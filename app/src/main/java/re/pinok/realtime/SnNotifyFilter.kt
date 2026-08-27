package re.pinok.realtime

import re.pinok.api.VKApiClient
import re.pinok.util.AppLog

/**
 * §42.3 #PUSH-SOURCE-FILTER — client-side применение sn_* настроек уведомлений.
 *
 * ## Проблема
 *
 * sn_* toggles (sn_likes, sn_comments, sn_reposts, sn_replies, sn_wall_posts,
 * sn_groups, ...) — это SERVER-SIDE настройки VK (управляют что VK шлёт через
 * FCM). Они сохраняются через `settingsGeneral.toggleNotify(key, value)` на
 * сервер VK.
 *
 * Но PinoK НЕ использует FCM — он опрашивает `notifications.getRedesign`.
 * VK возвращает ВСЕ уведомления в getRedesign независимо от sn_* настроек
 * (sn_* влияет только на FCM push). Поэтому toggles «не работали» —
 * пользователь выключал sn_likes, а лайки всё равно показывались.
 *
 * ## Решение
 *
 * Применяем sn_* states CLIENT-SIDE в [VkNotificationsNotifier.showBatch]:
 * парсим notifyCacheJson (кэш sn_* states) → маппим каждый sn_* key на
 * predicate над NotificationItem → фильтруем.
 *
 * ## Маппинг sn_* key → notification type
 *
 * Основные ключи (покрывают ~90% реальных уведомлений из getRedesign):
 *
 *   sn_likes         → type.startsWith("like_")        (default: false в VK!)
 *   sn_comments      → type.startsWith("comment_")     (default: true)
 *   sn_reposts       → type == "copy"                  (default: false!)
 *   sn_replies       → type in {reply_comment, reply_to_comment}  (default: true)
 *   sn_copies        → type == "copy"  (alias sn_reposts)         (default: false!)
 *   sn_wall_posts    → type == "wall"                  (default: true)
 *   sn_friend_accepted → type == "friend_accepted"     (default: true)
 *   sn_friends_follow  → type == "follow"              (default: true)
 *   sn_friend_requests → type == "friend_request"      (default: true)
 *   sn_mentions      → type.startsWith("mention")      (default: true)
 *   sn_mass_mentions → type == "mention_mass"          (default: true)
 *   sn_gifts         → type == "gift"                  (default: true)
 *   sn_group_invites → type == "invite_group"          (default: true)
 *   sn_app_invites   → type == "invite_app"            (default: false!)
 *   sn_groups        → parentOwnerId < 0 (сообщество)  (default: true)
 *   sn_new_posts     → type == "wall" && parentOwnerId > 0  (default: false!)
 *   sn_stories       → type.startsWith("story")        (default: true)
 *   sn_photo_tags    → type == "tag_photo"             (default: true)
 *   sn_polls         → type.startsWith("poll_")        (default: true)
 *
 * Если ключ отсутствует в states — используется default из VK (см. выше).
 *
 * ## Приоритет фильтров
 *
 * showBatch применяет фильтры в порядке:
 *   1. Quiet hours (глобально — пропускаем всё).
 *   2. Per-category toggles (pushLikes/pushComments/...).
 *   3. Source filter (pushFromCommunities/pushFromUsers).
 *   4. sn_* client-side filter (этот класс).
 *   5. Per-user mute (pushPerUserMuted).
 *
 * Шаги 2+3+4+5 комбинируются через AND: уведомление показывается если ВСЕ
 * фильтры пропустили его.
 */
object SnNotifyFilter {

    private const val TAG = "SnNotifyFilter"

    /**
     * Default values для sn_* ключей (как в VK web settings).
     * Если ключ отсутствует в states — используем эти.
     *
     * ВАЖНО: некоторые defaults = false (sn_likes, sn_reposts, sn_copies,
     * sn_new_posts, sn_app_invites) — VK по умолчанию НЕ шлёт push для них.
     * Но т.к. PinoK опрашивает getRedesign (не FCM), мы применяем эти
     * defaults client-side: если пользователь не менял — лайки/репосты
     * фильтруются. Пользователь может включить их в Настройках.
     */
    private val SN_DEFAULTS: Map<String, Boolean> = mapOf(
        // Master
        "sn_push_send" to true,
        // Messages (не фильтруем — для сообщений отдельный MessageNotifier)
        "sn_messages" to true,
        "sn_chats" to true,
        "sn_mentions" to true,
        "sn_mass_mentions" to true,
        "sn_message_requests" to true,
        // Groups
        "sn_groups" to true,
        "sn_group_invites" to true,
        "sn_group_actions" to false,
        // Friends
        "sn_friend_accepted" to true,
        "sn_friend_requests" to true,
        "sn_friend_found" to false,
        "sn_birthdays" to true,
        // Reactions
        "sn_likes" to false,       // VK default: OFF
        "sn_comments" to true,
        "sn_reposts" to false,     // VK default: OFF
        "sn_replies" to true,
        // Feedback
        "sn_copies" to false,      // VK default: OFF (alias sn_reposts)
        "sn_wall_posts" to true,
        "sn_related_events" to true,
        "sn_story_reply" to true,
        "sn_story_question" to false,
        "sn_clips_duet" to false,
        "sn_clips_from_video" to false,
        "sn_co_ownership" to false,
        // Content
        "sn_new_posts" to false,   // VK default: OFF
        "sn_stories" to true,
        "sn_photo_tags" to true,
        // Events
        "sn_friends_follow" to true,
        "sn_event_soon" to false,
        "sn_interest" to false,
        "sn_group_recommendation" to false,
        "sn_clips" to false,
        "sn_feed_promo" to false,
        // Other
        "sn_app_invites" to false,  // VK default: OFF
        "sn_events" to true,
        "sn_polls" to true,
        "sn_market_orders" to true,
        // Extra
        "sn_private_group_post" to false,
        "sn_gifts" to true,
        "sn_lives" to false,
        "sn_video_playlists" to false,
        "sn_video_groups_publish" to true,
        "sn_content_achievements" to false,
        "sn_service_recommend" to false,
        "sn_bookmarks" to false,
        "sn_market" to false,
        "sn_lovina" to false,
        "sn_stickers_bonus_expiration" to false,
        "sn_stickers_bonus_discounts_expiration" to false,
    )

    /**
     * Маппинг sn_* key → predicate над NotificationItem.
     *
     * Возвращает (key, predicate) pairs. Predicate = true если уведомление
     * СООТВЕТСТВУЕТ этому sn_* key (т.е. должно фильтроваться им).
     */
    private val SN_PREDICATES: List<Pair<String, (VKApiClient.NotificationItem) -> Boolean>> = listOf(
        // Реакции (лайки/комментарии/репосты/ответы)
        "sn_likes" to { it.type.startsWith("like_") },
        "sn_comments" to { it.type.startsWith("comment_") },
        "sn_reposts" to { it.type == "copy" },
        "sn_copies" to { it.type == "copy" },  // alias
        "sn_replies" to { it.type == "reply_comment" || it.type == "reply_to_comment" },
        // Стена
        "sn_wall_posts" to { it.type == "wall" },
        "sn_new_posts" to { it.type == "wall" && it.parentOwnerId > 0 },
        // Друзья
        "sn_friend_accepted" to { it.type == "friend_accepted" },
        "sn_friends_follow" to { it.type == "follow" },
        "sn_friend_requests" to { it.type == "friend_request" },
        // Упоминания
        "sn_mentions" to { it.type.startsWith("mention") && it.type != "mention_mass" },
        "sn_mass_mentions" to { it.type == "mention_mass" },
        // Подарки / приглашения
        "sn_gifts" to { it.type == "gift" },
        "sn_group_invites" to { it.type == "invite_group" },
        "sn_app_invites" to { it.type == "invite_app" },
        // Истории / фото / опросы
        "sn_stories" to { it.type.startsWith("story") },
        "sn_photo_tags" to { it.type == "tag_photo" },
        "sn_polls" to { it.type.startsWith("poll_") },
        // Источник: сообщества
        "sn_groups" to { it.parentOwnerId < 0 },
    )

    /**
     * Парсит JSON-кэш sn_* states в Map<String, Boolean>.
     * Формат: `{"sn_likes":false,"sn_comments":true,...}`
     * Толерантна к мусору — возвращает emptyMap при ошибке.
     *
     * (Дублирует логику parseNotifyCache из SettingsScreen, но без зависимости
     * на UI-слой — этот объект живёт в realtime package.)
     */
    fun parseStates(json: String): Map<String, Boolean> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val out = mutableMapOf<String, Boolean>()
            for ((key, value) in obj.entrySet()) {
                if (value.isJsonPrimitive) {
                    try {
                        out[key] = value.asBoolean
                    } catch (_: Exception) { /* skip non-boolean */ }
                }
            }
            out
        } catch (e: Exception) {
            AppLog.w(TAG, "parseStates failed: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Проверяет, проходит ли уведомление через sn_* фильтр.
     *
     * Логика:
     *   - Для каждого sn_* key с predicate: если item matching predicate,
     *     проверяем state. Если state == false → уведомление отфильтровано.
     *   - Если state отсутствует — используем SN_DEFAULTS[key] ?: true.
     *
     * @param item уведомление для проверки
     * @param states кэш sn_* states (из notifyCacheJson)
     * @return true если уведомление проходит фильтр (показывать), false если отфильтровано
     */
    fun passes(item: VKApiClient.NotificationItem, states: Map<String, Boolean>): Boolean {
        for ((key, predicate) in SN_PREDICATES) {
            if (!predicate(item)) continue
            // Item matching this sn_* key → check state.
            val enabled = states[key] ?: SN_DEFAULTS[key] ?: true
            if (!enabled) {
                AppLog.d(TAG, "filtered: type='${item.type}' sn_$key=false")
                return false
            }
        }
        return true
    }

    /**
     * Фильтрует список уведомлений, оставляя только те что проходят sn_* фильтр.
     */
    fun filter(items: List<VKApiClient.NotificationItem>, states: Map<String, Boolean>): List<VKApiClient.NotificationItem> {
        if (states.isEmpty()) return items  // нет кэша — нет фильтра (используем defaults ниже)
        return items.filter { passes(it, states) }
    }

    /**
     * Возвращает default state для sn_* key (для UI отображения).
     */
    fun defaultFor(key: String): Boolean = SN_DEFAULTS[key] ?: true
}
