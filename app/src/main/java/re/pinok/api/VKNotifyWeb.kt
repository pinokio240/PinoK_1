package re.pinok.api

import re.pinok.data.model.SettingsParam
import re.pinok.data.model.SettingsSection

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import re.pinok.util.AppLog

/**
 * VKNotifyWeb — рабочая реализация НАСТРОЕК УВЕДОМЛЕНИЙ VK через ВЕБ-ФОРМЫ
 * m.vk.ru (волна #NOTIFY-WEB-REWRITE, 2026-09-30).
 *
 * ЗАЧЕМ: прежний путь (settingsGeneral.getNotifySettings / setNotifySettings /
 * toggleNotify через api.vk.com) на реальном клиенте даёт error 3
 * «Unknown method passed» (проверено HAR api.vk.com_29.har) — метода в VK API
 * НЕТ. Реальный механизм мобильного веба m.vk.ru — POST-ФОРМЫ:
 *   - act=notify_group&group=<g>              (состояние группы)
 *   - act=notify_group_save (group,value,hash)  value = "all"/"none"
 *   - act=notify_push_group_save (group,push_switcher,hash)
 *   - act=notify_push_message_save (push_switcher,push_no_text,hash)
 *   - act=notify_push_chat_save    (push_switcher,push_no_text,hash)
 *   - act=notify_push_save / notify_push_silent_save (push_switcher,hash)
 *   - act=save_email_notify (*_notify,email_period,hash)
 *
 * Каждый save требует hash (CSRF) со страницы https://m.vk.ru/settings?act=notify.
 * Cookies подставляет VkCookieJar (живой CookieManager) — отдельно их не шлём.
 *
 * ФАЙЛ САМОСТОЯТЕЛЬНЫЙ: не трогает VKApiClient; принимает OkHttpClient
 * параметром (тот же клиент, что у VKApiClient, с VkCookieJar).
 *
 * NULL-ЯВНО: без !!, ?., ?: — только if + локальный val (стиль проекта).
 */
object VKNotifyWeb {

    private const val TAG = "VKNotifyWeb"

    /** База мобильного веба. */
    const val BASE = "https://m.vk.ru"

    /** Страница настроек уведомлений (источник hash). */
    const val PAGE_URL = "https://m.vk.ru/settings?act=notify"

    /** UA как у Chrome/m.vk.ru (анти-бот). */
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * РЕАЛЬНЫЕ 30 групп веб-API VK (из HAR уведомления_290926.har,
     * act=notify_group / notify_group_save).
     */
    val GROUP_KEYS: List<String> = listOf(
        "likes",
        "copies",
        "comments",
        "comment_commented",
        "wall_posts",
        "related_events",
        "story_reply",
        "story_question",
        "voting",
        "clips_duet",
        "clips_from_video",
        "co_ownership",
        "mentions",
        "friends_requests",
        "friends_found",
        "clips",
        "event_soon",
        "new_posts",
        "private_group_post",
        "gifts",
        "video_playlists",
        "video_continue_watch",
        "video_subscription",
        "content_achievements",
        "service_recommend",
        "bookmarks",
        "market",
        "lovina",
        "stickers_bonus_expiration",
        "stickers_bonus_discounts_expiration",
    )

    /** Состояние страницы настроек. */
    data class Snapshot(
        /** hash для act=notify_group_save (совпадает с notify_push_group_save). */
        val hashGroup: String?,
        /** hash для act=notify_push_message_save / notify_push_chat_save. */
        val hashPush: String?,
        /** hash для act=notify / notify_push_save (общий). */
        val hashGeneral: String?,
        /** Мастер: получать push-уведомления (push_switcher). */
        val pushSwitcher: Boolean,
        /** Мастер: без текста (push_no_text). */
        val pushNoText: Boolean,
        /** Состояние групп: key -> "all"/"none". Отсутствие = неизвестно. */
        val groups: Map<String, String>,
    )

    // ────────────────────────────────────────────────────────────────────────
    // 1. ЧТЕНИЕ СТРАНИЦЫ
    // ────────────────────────────────────────────────────────────────────────

    /**
     * GET https://m.vk.ru/settings?act=notify, парсинг hash + состояния.
     * Возвращает null при сетевой ошибке/оффлайне.
     */
    suspend fun fetch(client: OkHttpClient): Snapshot? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(PAGE_URL)
            .get()
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .build()
        val html: String? = try {
            client.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                    if (resp.code != 200) {
                        AppLog.w(TAG, "fetch: HTTP ${resp.code}")
                        null
                    } else {
                        resp.body?.string()
                    }
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "fetch: error", e)
            null
        }
        if (html == null) return@withContext null
        parseSnapshot(html)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. ПАРСИНГ HTML (без внешних библиотек)
    // ────────────────────────────────────────────────────────────────────────

    /** Парсит HTML → Snapshot. Публичный для юнит-тестов. */
    fun parseSnapshot(html: String): Snapshot {
        // hash-и: VK кладёт их в action форм (?...&hash=<18hex>) и/или в JSON.
        val allHashes = findAllHashes(html)
        // push_switcher / push_no_text: input checkbox hidden (checked = включено).
        val pushSwitcher = isChecked(html, "push_switcher")
        val pushNoText = isChecked(html, "push_no_text")
        // группы: action notify_group_save&group=likes ... — или data-атрибуты.
        val groups = parseGroupStates(html)
        return Snapshot(
            hashGroup = pickHashFor(allHashes, html, "notify_group_save"),
            hashPush = pickHashFor(allHashes, html, "notify_push_message_save"),
            hashGeneral = pickHashFor(allHashes, html, "notify_push_save"),
            pushSwitcher = pushSwitcher,
            pushNoText = pushNoText,
            groups = groups,
        )
    }

    private val HASH_RE = Regex("[a-f0-9]{18}")

    private fun findAllHashes(html: String): Set<String> {
        val out = LinkedHashSet<String>()
        val m = HASH_RE.findAll(html)
        for (x in m) {
            // исключаем очевидно не-hash длинные токены — 18 знаков = наш hash.
            if (x.value.length == 18) out.add(x.value)
        }
        return out
    }

    /**
     * Ищет hash рядом с конкретным action (notify_group_save, notify_push_save …).
     * Если не нашли — берём hash, встречающийся в HTML с этим action наиболее часто.
     */
    private fun pickHashFor(hashes: Set<String>, html: String, action: String): String? {
        // Ищем окрестность 400 символов после вхождения action.
        val idx = html.indexOf(action)
        if (idx >= 0) {
            val start = idx
            val end = minOf(html.length, idx + 400)
            val chunk = html.substring(start, end)
            val m = HASH_RE.find(chunk)
            if (m != null) return m.value
        }
        // Fallback: первый найденный hash вообще.
        val it = hashes.iterator()
        if (it.hasNext()) return it.next()
        return null
    }

    /** checked ли input[name=<name>] (по ближайшему checked). */
    private fun isChecked(html: String, name: String): Boolean {
        val needle = "name=\"" + name + "\""
        val idx = html.indexOf(needle)
        if (idx < 0) return false
        val end = minOf(html.length, idx + 250)
        val chunk = html.substring(idx, end)
        // значение value="1" и/или checked неподалёку.
        val hasChecked = chunk.contains("checked")
        val v1 = chunk.contains("value=\"1\"")
        return hasChecked || v1
    }

    /**
     * Достаёт состояния групп: VK отдаёт value у скрытых input'ов/селектов
     * рядом с group=<key>. Ищем name="<key>" или data-group=<key> … value="all|none".
     */
    private fun parseGroupStates(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (key in GROUP_KEYS) {
            val idxName = html.indexOf("name=\"" + key + "\"")
            val idxData = html.indexOf("data-group=\"" + key + "\"")
            val idx = if (idxName >= 0) idxName else idxData
            if (idx < 0) continue
            val end = minOf(html.length, idx + 300)
            val chunk = html.substring(idx, end)
            if (chunk.contains("value=\"all\"")) out[key] = "all"
            else if (chunk.contains("value=\"none\"")) out[key] = "none"
        }
        return out
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. ЗАПИСЬ
    // ────────────────────────────────────────────────────────────────────────

    /** act=notify_group_save: group=<key>, value="all"/"none", hash. */
    suspend fun saveGroup(
        client: OkHttpClient,
        hash: String,
        group: String,
        enabled: Boolean,
    ): Boolean {
        if (hash.isBlank()) return false
        if (!GROUP_KEYS.contains(group)) {
            AppLog.w(TAG, "saveGroup: unknown group '$group' — skip")
            return false
        }
        return postForm(
            client,
            path = "/settings?act=notify_group_save&group=" + group + "&hash=" + hash,
            fields = mapOf(
                "value" to (if (enabled) "all" else "none"),
                "hash" to hash,
            ),
        )
    }

    /** act=notify_push_group_save: group=<key>, push_switcher=1, hash. */
    suspend fun savePushGroup(
        client: OkHttpClient,
        hash: String,
        group: String,
        enabled: Boolean,
    ): Boolean {
        if (hash.isBlank()) return false
        return postForm(
            client,
            path = "/settings?act=notify_push_group_save&group=" + group + "&hash=" + hash,
            fields = mapOf(
                "push_switcher" to (if (enabled) "1" else "0"),
                "hash" to hash,
            ),
        )
    }

    /** act=notify_push_message_save: push_switcher, push_no_text, hash. */
    suspend fun savePushMessage(
        client: OkHttpClient,
        hash: String,
        pushSwitcher: Boolean?,
        pushNoText: Boolean?,
    ): Boolean {
        if (hash.isBlank()) return false
        val f = HashMap<String, String>()
        if (pushSwitcher != null) f["push_switcher"] = if (pushSwitcher) "1" else "0"
        if (pushNoText != null) f["push_no_text"] = if (pushNoText) "1" else "0"
        f["hash"] = hash
        if (f.size <= 1) return false
        return postForm(client, "/settings?act=notify_push_message_save&hash=" + hash, f)
    }

    /** act=notify_push_chat_save: push_switcher, push_no_text, hash. */
    suspend fun savePushChat(
        client: OkHttpClient,
        hash: String,
        pushSwitcher: Boolean?,
        pushNoText: Boolean?,
    ): Boolean {
        if (hash.isBlank()) return false
        val f = HashMap<String, String>()
        if (pushSwitcher != null) f["push_switcher"] = if (pushSwitcher) "1" else "0"
        if (pushNoText != null) f["push_no_text"] = if (pushNoText) "1" else "0"
        f["hash"] = hash
        if (f.size <= 1) return false
        return postForm(client, "/settings?act=notify_push_chat_save&hash=" + hash, f)
    }

    /** act=notify_push_save (мастер): push_switcher, hash. */
    suspend fun savePushMaster(
        client: OkHttpClient,
        hash: String,
        enabled: Boolean,
    ): Boolean {
        if (hash.isBlank()) return false
        return postForm(
            client,
            path = "/settings?act=notify_push_save&hash=" + hash,
            fields = mapOf(
                "push_switcher" to (if (enabled) "1" else "0"),
                "hash" to hash,
            ),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. ОБЩИЙ POST
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun postForm(
        client: OkHttpClient,
        path: String,
        fields: Map<String, String>,
    ): Boolean = withContext(Dispatchers.IO) {
        val fb = FormBody.Builder()
        for ((k, v) in fields) fb.add(k, v)
        val req = Request.Builder()
            .url(BASE + path)
            .post(fb.build())
            .header("User-Agent", UA)
            .header("Origin", "https://m.vk.ru")
            .header("Referer", PAGE_URL)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        try {
            client.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                    if (resp.code == 200) {
                        true
                    } else {
                        AppLog.w(TAG, "post $path: HTTP ${resp.code}")
                        false
                    }
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "post $path: error", e)
            false
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. МАППИНГ PinoK sn_* → веб-группа (для UI/адаптера VKApiClient)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает имя ВЕБ-группы для PinoK-ключа sn_<x>, либо null если
     * такого ключа в веб-API нет (тогда слать НЕЛЬЗЯ — фиктивный тумблер).
     *
     * Ключи PinoK (SettingsScreen.kt NOTIFY_*_TOGGLES) → веб:
     *   sn_likes          → likes
     *   sn_comments       → comments
     *   sn_replies        → comment_commented  (в PinoK «Ответы на комментарии»)
     *   sn_reposts        → copies             («Поделились»)
     *   sn_mentions       → mentions
     *   sn_mass_mentions  → mentions           (веб-группы нет отдельной)
     *   sn_friend_requests→ friends_requests
     *   sn_friend_found   → friends_found
     *   sn_polls          → voting
     *   sn_wall_posts     → wall_posts
     *   sn_related_events → related_events
     *   sn_story_reply    → story_reply
     *   sn_story_question → story_question
     *   sn_clips_duet     → clips_duet
     *   sn_clips_from_video → clips_from_video
     *   sn_co_ownership   → co_ownership
     *   sn_clips          → clips
     *   sn_event_soon     → event_soon
     *   sn_new_posts      → new_posts
     *   sn_private_group_post → private_group_post
     *   sn_gifts          → gifts
     *   sn_video_playlists→ video_playlists
     *   sn_content_achievements → content_achievements
     *   sn_service_recommend → service_recommend
     *   sn_bookmarks      → bookmarks
     *   sn_market         → market
     *   sn_lovina         → lovina
     *   sn_stickers_bonus_expiration → stickers_bonus_expiration
     *   sn_stickers_bonus_discounts_expiration → stickers_bonus_discounts_expiration
     *
     * НЕ мапятся (веб-группы нет): sn_messages, sn_chats, sn_groups,
     *   sn_group_invites, sn_group_actions, sn_friend_accepted,
     *   sn_birthdays, sn_stories, sn_photo_tags, sn_friends_follow,
     *   sn_interest, sn_group_recommendation, sn_feed_promo, sn_app_invites,
     *   sn_events, sn_market_orders, sn_lives, sn_video_groups_publish,
     *   sn_message_requests, sn_message_requests. Для них возвращаем null.
     */
    fun webGroupForPinoKKey(snKey: String): String? {
        val k = if (snKey.startsWith("sn_")) snKey.substring(3) else snKey
        return when (k) {
            "likes" -> "likes"
            "comments" -> "comments"
            "replies" -> "comment_commented"
            "reposts" -> "copies"
            "mentions" -> "mentions"
            "mass_mentions" -> "mentions"
            "friend_requests" -> "friends_requests"
            "friend_found" -> "friends_found"
            "polls" -> "voting"
            "wall_posts" -> "wall_posts"
            "related_events" -> "related_events"
            "story_reply" -> "story_reply"
            "story_question" -> "story_question"
            "clips_duet" -> "clips_duet"
            "clips_from_video" -> "clips_from_video"
            "co_ownership" -> "co_ownership"
            "clips" -> "clips"
            "event_soon" -> "event_soon"
            "new_posts" -> "new_posts"
            "private_group_post" -> "private_group_post"
            "gifts" -> "gifts"
            "video_playlists" -> "video_playlists"
            "content_achievements" -> "content_achievements"
            "service_recommend" -> "service_recommend"
            "bookmarks" -> "bookmarks"
            "market" -> "market"
            "lovina" -> "lovina"
            "stickers_bonus_expiration" -> "stickers_bonus_expiration"
            "stickers_bonus_discounts_expiration" -> "stickers_bonus_discounts_expiration"
            else -> null
        }
    }

    /**
     * Cтроит sections-дерево из Snapshot для UI.
     */
    fun buildSections(snap: Snapshot): List<SettingsSection> {
        val masterParams = listOf(
            SettingsParam(
                key = "sn_push_send",
                type = "toggle",
                title = "Получать push-уведомления",
                isChecked = snap.pushSwitcher,
            ),
        )
        val master = SettingsSection(
            id = "notify_master",
            title = "Основное",
            params = masterParams,
        )
        val groupParams = ArrayList<SettingsParam>()
        for (sn in PinoKOrder) {
            val wg = webGroupForPinoKKey(sn)
            if (wg == null) continue
            val v = snap.groups[wg]
            val checked = v == "all"
            groupParams.add(
                SettingsParam(
                    key = sn,
                    type = "toggle",
                    title = PinoKTitles[sn],
                    isChecked = checked,
                )
            )
        }
        val groupSec = SettingsSection(
            id = "notify_groups",
            title = "Уведомления по типам",
            params = groupParams,
        )
        return listOf(master, groupSec)
    }

    private val PinoKOrder: List<String> = listOf(
        "sn_likes", "sn_comments", "sn_replies", "sn_reposts",
        "sn_mentions", "sn_mass_mentions",
        "sn_friend_requests", "sn_friend_found",
        "sn_wall_posts", "sn_related_events",
        "sn_story_reply", "sn_story_question",
        "sn_clips", "sn_clips_duet", "sn_clips_from_video",
        "sn_co_ownership", "sn_new_posts",
        "sn_event_soon", "sn_polls", "sn_private_group_post",
        "sn_gifts", "sn_video_playlists",
        "sn_content_achievements", "sn_service_recommend",
        "sn_bookmarks", "sn_market", "sn_lovina",
        "sn_stickers_bonus_expiration",
        "sn_stickers_bonus_discounts_expiration",
    )

    private val PinoKTitles: Map<String, String> = mapOf(
        "sn_likes" to "Отметки «Нравится»",
        "sn_comments" to "Комментарии",
        "sn_replies" to "Ответы на комментарии",
        "sn_reposts" to "Поделились",
        "sn_mentions" to "Упоминания",
        "sn_mass_mentions" to "Массовые упоминания",
        "sn_friend_requests" to "Заявки в друзья",
        "sn_friend_found" to "Возможные друзья",
        "sn_wall_posts" to "Посты на стене",
        "sn_related_events" to "Связано с вами",
        "sn_story_reply" to "Ответы на истории",
        "sn_story_question" to "Вопросы и мнения",
        "sn_clips" to "Интересные клипы",
        "sn_clips_duet" to "Дуэты с клипами",
        "sn_clips_from_video" to "Клипы с видео",
        "sn_co_ownership" to "Соавторство",
        "sn_new_posts" to "Новые записи друзей",
        "sn_event_soon" to "Ближайшие мероприятия",
        "sn_polls" to "Опросы",
        "sn_private_group_post" to "Посты в закрытых сообществах",
        "sn_gifts" to "Подарки",
        "sn_video_playlists" to "Обновление плейлиста",
        "sn_content_achievements" to "Достижения",
        "sn_service_recommend" to "Рекомендации сервисов",
        "sn_bookmarks" to "Закладки",
        "sn_market" to "Магазин",
        "sn_lovina" to "Знакомства",
        "sn_stickers_bonus_expiration" to "Энергия скоро сгорит",
        "sn_stickers_bonus_discounts_expiration" to "Скидки на стикеры",
    )
}
