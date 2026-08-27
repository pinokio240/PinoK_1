package re.pinok.realtime

import re.pinok.api.VKApiClient
import re.pinok.util.AppLog
import java.util.regex.Pattern

/**
 * §42 #PUSH-NOTIFICATIONS — маппинг VK NotificationItem → deep-link action.
 *
 * Когда пользователь тапает по push-уведомлению (лайк/комментарий/репост/ответ/
 * подписка/упоминание/подарок/запись на стене), приложение должно открыть
 * «место события»: пост, фото, видео, профиль пользователя, сообщество.
 *
 * [VkNotificationsNotifier] строит PendingIntent с action + extras из
 * [DeepLinkAction]. [re.pinok.ui.MainActivity] ловит intent и навигирует
 * на нужный Screen через SovaNavHost.
 *
 * §42.4 #PUSH-DEEPLINK — точная навигация к источнику события:
 *
 * Раньше deep-link строился ТОЛЬКО по parentType + parentOwnerId + parentItemId.
 * Это работало для постов/фото/видео, но ломалось в трёх случаях:
 *
 *  1. ВИДЕО: SovaNavHost делал nav.navigate(Screen.VideoPlayer...) — но маршрут
 *     video_player/{ownerId}/{videoId} УДАЛЁН из NavHost (#90, теперь overlay).
 *     Тап по видео-уведомлению → ничего не открывалось. Теперь SovaNavHost
 *     вызывает VideoHolder.open(Video(...)) — overlay-плеер.
 *
 *  2. ФОТО: открывалось в InternalBrowser (WebView, vk.com/photo...) вместо
 *     нативного PhotoViewer. Теперь передаём photoUrl и открываем PhotoHolder
 *     overlay (pinch-zoom, swipe — как в ленте).
 *
 *  3. ОТВЕТ НА КОММЕНТАРИЙ: открывался пост, но без скролла к комментарию.
 *     Теперь [OpenPost] несёт commentId, а PostDetailScreen скроллит к нему.
 *     commentId берётся из [NotificationItem.parentCommentId] (id комментария
 *     в parent) ИЛИ парсится из [NotificationItem.parentUrl] (?reply=...).
 *
 * ## Стратегия разрешения deep-link
 *
 *  1. PRIMARY: [NotificationItem.parentUrl] (canonical VK permalink из redesign
 *     action.entity.url). Парсится [parseVkUrl] → (kind, ownerId, itemId,
 *     commentId). URL однозначно кодирует всё, включая ?reply= для комментариев.
 *
 *  2. FALLBACK: type + parentType + parentOwnerId + parentItemId + parentCommentId
 *     (старая логика, дополненная commentId).
 *
 *  3. LAST RESORT: OpenNotifications (вкладка «Уведомления»).
 *
 * Mapping type → DeepLink (fallback):
 *   like_post / comment_post / reply_comment / mention / copy / wall
 *     → OpenPost(parentOwnerId, parentItemId, parentCommentId)
 *   like_photo / comment_photo
 *     → OpenPhoto(parentOwnerId, parentItemId, photoUrl)
 *   like_video / comment_video
 *     → OpenVideo(parentOwnerId, parentItemId)
 *   follow / friend_accepted
 *     → OpenUser(feedbackIds.first())
 *   invite_group
 *     → OpenCommunity(-(feedbackIds.first()))  (group IDs отрицательные в VK API)
 *   gift / другие
 *     → OpenNotifications  (fallback — открыть вкладку Уведомления)
 */
object VkUrlDeepLinker {

    private const val TAG = "VkUrlDeepLinker"

    // Intent actions для MainActivity.handleDeepLinkIntent
    const val ACTION_OPEN_POST = "re.pinok.action.OPEN_POST"
    const val ACTION_OPEN_PHOTO = "re.pinok.action.OPEN_PHOTO"
    const val ACTION_OPEN_VIDEO = "re.pinok.action.OPEN_VIDEO"
    const val ACTION_OPEN_USER = "re.pinok.action.OPEN_USER"
    const val ACTION_OPEN_COMMUNITY = "re.pinok.action.OPEN_COMMUNITY"
    const val ACTION_OPEN_NOTIFICATIONS = "re.pinok.action.OPEN_NOTIFICATIONS"
    /** §49.6 Sprint VK-ID-1.6: deep-link из security-alert notification → DevicesScreen. */
    const val ACTION_OPEN_DEVICES = "re.pinok.action.OPEN_DEVICES"

    // Extras keys
    const val EXTRA_OWNER_ID = "owner_id"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_USER_ID = "user_id"
    const val EXTRA_GROUP_ID = "group_id"
    const val EXTRA_NOTIFICATION_TYPE = "notif_type"
    const val EXTRA_NOTIFICATION_TITLE = "notif_title"
    // §42.4 #PUSH-DEEPLINK: extras для точной навигации.
    /** ID комментария, к которому скроллить в PostDetailScreen (0 = не скроллить). */
    const val EXTRA_COMMENT_ID = "comment_id"
    /** Лучший доступный URL фото для нативного PhotoViewer (null → fallback на InternalBrowser). */
    const val EXTRA_PHOTO_URL = "photo_url"
    /** access_key для приватных фото/видео (пробрасывается до viewer'а при необходимости). */
    const val EXTRA_ACCESS_KEY = "access_key"

    /**
     * Deep-link action — что открыть при тапе на уведомление.
     *
     * MainActivity читает action + extras и навигирует через SovaNavHost.
     */
    sealed class DeepLinkAction {
        /**
         * @param commentId ID комментария, к которому скроллить после открытия поста
         *                  (для reply_comment / comment). 0 = просто открыть пост.
         */
        data class OpenPost(
            val ownerId: Long,
            val postId: Long,
            val commentId: Long = 0L,
        ) : DeepLinkAction()

        /**
         * @param photoUrl Лучший URL фото (из parentPhotoUrl/attachments) для
         *                 нативного PhotoViewer. null → fallback на InternalBrowser.
         * @param accessKey access_key для приватных фото.
         */
        data class OpenPhoto(
            val ownerId: Long,
            val photoId: Long,
            val photoUrl: String? = null,
            val accessKey: String? = null,
        ) : DeepLinkAction()

        data class OpenVideo(val ownerId: Long, val videoId: Long) : DeepLinkAction()
        data class OpenUser(val userId: Long) : DeepLinkAction()
        data class OpenCommunity(val groupId: Long) : DeepLinkAction()
        object OpenNotifications : DeepLinkAction()
        /** §49.6 Sprint VK-ID-1.6: открыть экран «Устройства и сессии». */
        object OpenDevices : DeepLinkAction()
    }

    /**
     * Распарсенный VK permalink — результат [parseVkUrl].
     *
     * @param kind "post" | "photo" | "video" | "topic" | "user" | "club"
     * @param ownerId Владелец (отрицательный для групп — как в VK API).
     * @param itemId ID поста/фото/видео/топика.
     * @param commentId ID комментария (reply=... для wall, post=... для topic). 0 если нет.
     */
    data class ParsedVkUrl(
        val kind: String,
        val ownerId: Long,
        val itemId: Long,
        val commentId: Long = 0L,
    )

    // Регэкспы для разбора VK permalink'ов. Поддерживают опциональный домен
    // (vk.com / m.vk.com) и опциональный query (?reply=N / ?post=N / ?z=...).
    // wall{owner}_{post} — owner может быть отрицательным (группа) или положительным (юзер).
    private val WALL_RE: Pattern = Pattern.compile(
        "wall(-?\\d+)_(\\d+)(?:[?&]reply=(\\d+))?"
    )
    private val PHOTO_RE: Pattern = Pattern.compile("photo(-?\\d+)_(\\d+)")
    private val VIDEO_RE: Pattern = Pattern.compile("video(-?\\d+)_(\\d+)")
    // topic-{groupId}_{topicId}?post={commentId} — топики ВСЕГДА используют дефис.
    private val TOPIC_RE: Pattern = Pattern.compile("topic(-\\d+)_(\\d+)(?:[?&]post=(\\d+))?")
    private val USER_RE: Pattern = Pattern.compile("id(\\d+)")
    private val CLUB_RE: Pattern = Pattern.compile("club(\\d+)")

    /**
     * Парсит VK permalink (или путь) в [ParsedVkUrl].
     *
     * Принимает как полный URL («https://vk.com/wall-123_456?reply=789»),
     * так и короткий путь («wall-123_456»). Извлекает ownerId (с сохранением
     * знака: отрицательный для групп), itemId и commentId (если есть ?reply=
     * или ?post=).
     *
     * @return [ParsedVkUrl] или null если строка не похожа на VK-ссылку.
     */
    fun parseVkUrl(rawUrl: String?): ParsedVkUrl? {
        if (rawUrl.isNullOrBlank()) return null
        val url = rawUrl.trim()
        try {
            WALL_RE.matcher(url).let { m ->
                if (m.find()) {
                    val owner = m.group(1)?.toLongOrNull() ?: return null
                    val item = m.group(2)?.toLongOrNull() ?: return null
                    val reply = m.group(3)?.toLongOrNull() ?: 0L
                    return ParsedVkUrl("post", owner, item, reply)
                }
            }
            PHOTO_RE.matcher(url).let { m ->
                if (m.find()) {
                    val owner = m.group(1)?.toLongOrNull() ?: return null
                    val item = m.group(2)?.toLongOrNull() ?: return null
                    return ParsedVkUrl("photo", owner, item, 0L)
                }
            }
            VIDEO_RE.matcher(url).let { m ->
                if (m.find()) {
                    val owner = m.group(1)?.toLongOrNull() ?: return null
                    val item = m.group(2)?.toLongOrNull() ?: return null
                    return ParsedVkUrl("video", owner, item, 0L)
                }
            }
            TOPIC_RE.matcher(url).let { m ->
                if (m.find()) {
                    val owner = m.group(1)?.toLongOrNull() ?: return null
                    val item = m.group(2)?.toLongOrNull() ?: return null
                    val post = m.group(3)?.toLongOrNull() ?: 0L
                    // topic в навигации открываем как пост (BoardTopic — отдельный
                    // маршрут, но для push-тапа достаточно открыть пост-владелец).
                    return ParsedVkUrl("topic", owner, item, post)
                }
            }
            USER_RE.matcher(url).let { m ->
                if (m.find()) {
                    val uid = m.group(1)?.toLongOrNull() ?: return null
                    return ParsedVkUrl("user", uid, 0L, 0L)
                }
            }
            CLUB_RE.matcher(url).let { m ->
                if (m.find()) {
                    val gid = m.group(1)?.toLongOrNull() ?: return null
                    return ParsedVkUrl("club", -gid, 0L, 0L)
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "parseVkUrl failed for '$rawUrl': ${e.message}")
        }
        return null
    }

    /**
     * Возвращает DeepLinkAction для уведомления.
     *
     * Стратегия (см. KDoc класса):
     *  1. Если есть [NotificationItem.parentUrl] — парсим его через [parseVkUrl]
     *     и строим action по kind. Для post/topic с commentId → OpenPost(.,.,commentId).
     *     Это покрывает reply_comment (url = wall{owner}_{post}?reply={comment}).
     *  2. Иначе fallback по type + parentType:
     *     - follow/friend_accepted → OpenUser(feedbackIds.first())
     *     - invite_group → OpenCommunity(-(feedbackIds.first()))
     *     - gift → OpenNotifications
     *     - parentType post/comment → OpenPost(ownerId, itemId, parentCommentId)
     *     - parentType photo → OpenPhoto(ownerId, itemId, parentPhotoUrl)
     *     - parentType video → OpenVideo(ownerId, itemId)
     *     - иначе → OpenNotifications
     *
     * Для OpenPhoto photoUrl берётся из parentPhotoUrl, а если пусто — из первого
     * photo-вложения attachments (лучший доступный URL).
     */
    fun deepLinkFor(item: VKApiClient.NotificationItem): DeepLinkAction {
        try {
            val type = item.type

            // --- PRIMARY: parentUrl (canonical VK permalink) ---
            // Самый надёжный источник: URL однозначно кодирует тип/owner/item/comment.
            val parsed = parseVkUrl(item.parentUrl)
            if (parsed != null) {
                val action = when (parsed.kind) {
                    "post", "topic" -> DeepLinkAction.OpenPost(
                        ownerId = parsed.ownerId,
                        postId = parsed.itemId,
                        // reply= (wall) или post= (topic) — комментарий для скролла.
                        // Если URL не дал commentId, берём parentCommentId (если есть).
                        commentId = parsed.commentId.takeIf { it != 0L }
                            ?: item.parentCommentId,
                    )
                    "photo" -> DeepLinkAction.OpenPhoto(
                        ownerId = parsed.ownerId,
                        photoId = parsed.itemId,
                        photoUrl = bestPhotoUrl(item),
                        accessKey = firstPhotoAccessKey(item),
                    )
                    "video" -> DeepLinkAction.OpenVideo(parsed.ownerId, parsed.itemId)
                    "user" -> {
                        if (parsed.ownerId > 0) DeepLinkAction.OpenUser(parsed.ownerId)
                        else fallbackDeepLink(item)
                    }
                    "club" -> DeepLinkAction.OpenCommunity(parsed.ownerId)
                    else -> fallbackDeepLink(item)
                }
                AppLog.d(TAG, "deepLinkFor: parsed parentUrl='${item.parentUrl}' → $action")
                return action
            }

            // --- FALLBACK: type + parentType ---
            return fallbackDeepLink(item)
        } catch (e: Exception) {
            AppLog.w(TAG, "deepLinkFor failed: ${e.message}")
            return DeepLinkAction.OpenNotifications
        }
    }

    /**
     * §47 #URL-INTENT-FILTER: Конвертирует VK URL (из ACTION_VIEW intent) в
     * [DeepLinkAction] для навигации внутри PinoK.
     *
     * Вызывается из [re.pinok.ui.MainActivity.handleDeepLinkIntent] когда
     * пользователь тапает на `https://vk.com/wall-123_456` в браузере/Telegram/
     * мессенджере и Android предлагает открыть в PinoK (intent-filter).
     *
     * В отличие от [deepLinkFor] (который принимает NotificationItem), этот
     * метод работает только со строкой URL — не нужен весь item. Но без item
     * недоступны: photoUrl (для OpenPhoto) и accessKey — они будут null,
     * PhotoViewer fallback на InternalBrowser.
     *
     * @param rawUrl VK permalink («https://vk.com/wall-123_456?reply=789»,
     *               «https://m.vk.ru/photo123_456», «wall-123_456», и т.д.).
     * @return [DeepLinkAction] или null если URL не распознан как VK-ссылка.
     */
    fun deepLinkFromUrl(rawUrl: String?): DeepLinkAction? {
        val parsed = parseVkUrl(rawUrl) ?: return null
        val action = when (parsed.kind) {
            "post", "topic" -> DeepLinkAction.OpenPost(
                ownerId = parsed.ownerId,
                postId = parsed.itemId,
                commentId = parsed.commentId,
            )
            "photo" -> DeepLinkAction.OpenPhoto(
                ownerId = parsed.ownerId,
                photoId = parsed.itemId,
                // §47: без NotificationItem нет parentPhotoUrl/accessKey —
                // PhotoViewer fallback на InternalBrowser (откроет VK URL в WebView).
                photoUrl = null,
                accessKey = null,
            )
            "video" -> DeepLinkAction.OpenVideo(parsed.ownerId, parsed.itemId)
            "user" -> {
                if (parsed.ownerId > 0) DeepLinkAction.OpenUser(parsed.ownerId)
                else null
            }
            "club" -> DeepLinkAction.OpenCommunity(parsed.ownerId)
            else -> null
        }
        AppLog.d(TAG, "deepLinkFromUrl: '$rawUrl' → $action")
        return action
    }

    /**
     * Fallback-логика построения deep-link по type + parentType (без URL).
     * Вынесена отдельно чтобы не дублировать между primary-path и catch.
     */
    private fun fallbackDeepLink(item: VKApiClient.NotificationItem): DeepLinkAction {
        val type = item.type

        // Follow / friend_accepted → профиль пользователя
        if (type == "follow" || type == "friend_accepted" || type == "friend_requested") {
            val uid = item.feedbackIds.firstOrNull()
            if (uid != null && uid > 0) {
                return DeepLinkAction.OpenUser(uid)
            }
            return DeepLinkAction.OpenNotifications
        }

        // Invite group → сообщество
        if (type == "invite_group") {
            val uid = item.feedbackIds.firstOrNull()
            if (uid != null && uid > 0) {
                // Group IDs в VK API — положительные, но в PinoK навигации
                // используем отрицательные (как peerId для сообществ).
                return DeepLinkAction.OpenCommunity(-uid)
            }
            return DeepLinkAction.OpenNotifications
        }

        // Gift → нет конкретного URL, открываем вкладку Уведомления
        if (type == "gift") {
            return DeepLinkAction.OpenNotifications
        }

        // §43 #LOG-NOISE: new_posts — это «в сообществе появились новые записи»,
        // без конкретного parent post (parentOwnerId=0, parentItemId=0). Это
        // ожидаемое поведение VK API, не ошибка. Раньше логировали на WARN каждый
        // раз → засоряло logcat. Теперь тихо возвращаем OpenNotifications.
        if (type == "new_posts") {
            return DeepLinkAction.OpenNotifications
        }

        // Все остальные типы (like_*, comment_*, reply_*, mention, copy, wall,
        // photo, video, clip, topic, ...)
        // → используем parentType для определения что открыть.
        val parentType = item.parentType
        val ownerId = item.parentOwnerId
        val itemId = item.parentItemId

        if (ownerId == 0L || itemId == 0L) {
            // §43 #LOG-NOISE: downgrade WARN → DEBUG. Некоторые типы уведомлений
            // (new_posts, некоторые mention) не несут parentOwnerId/ItemId — это
            // ожидаемое поведение VK API. UI открывает вкладку Уведомлений.
            AppLog.d(TAG, "fallback: invalid parent ownerId=$ownerId itemId=$itemId type=$type parentUrl=${item.parentUrl}")
            return DeepLinkAction.OpenNotifications
        }

        return when (parentType.lowercase()) {
            "post", "wall" -> DeepLinkAction.OpenPost(ownerId, itemId, item.parentCommentId)
            "comment" -> DeepLinkAction.OpenPost(ownerId, itemId, item.parentCommentId)
            "photo" -> DeepLinkAction.OpenPhoto(
                ownerId = ownerId,
                photoId = itemId,
                photoUrl = bestPhotoUrl(item),
                accessKey = firstPhotoAccessKey(item),
            )
            "video" -> DeepLinkAction.OpenVideo(ownerId, itemId)
            "clip" -> DeepLinkAction.OpenVideo(ownerId, itemId)
            "topic" -> DeepLinkAction.OpenPost(ownerId, itemId, item.parentCommentId)
            else -> {
                AppLog.d(TAG, "fallback: unknown parentType='$parentType' type=$type → OpenNotifications")
                DeepLinkAction.OpenNotifications
            }
        }
    }

    /**
     * Лучший доступный URL фото для нативного PhotoViewer.
     * Приоритет: parentPhotoUrl (photo_600/130/75 из legacy-парсера) →
     * первый photo-attachment.thumbUrl → null.
     */
    private fun bestPhotoUrl(item: VKApiClient.NotificationItem): String? {
        item.parentPhotoUrl?.let { if (it.isNotBlank()) return it }
        return item.attachments
            .firstOrNull { it.type == "photo" && !it.thumbUrl.isNullOrBlank() }
            ?.thumbUrl
    }

    /** access_key первого photo-вложения (для приватных фото). */
    private fun firstPhotoAccessKey(item: VKApiClient.NotificationItem): String? {
        return item.attachments
            .firstOrNull { it.type == "photo" }
            ?.accessKey
    }

    /**
     * Возвращает VK web URL для deep-link (для InternalBrowser fallback).
     * Например: wall-12345_678 → https://vk.com/wall-12345_678
     *
     * §42.4: ownerId уже хранится со знаком (отрицательный для групп — как в
     * VK API). Поэтому URL строится прямой конкатенацией БЕЗ манипуляций со
     * знаком (раньше был баг: для user-владельца добавлялся лишний дефис).
     */
    fun webUrlFor(action: DeepLinkAction): String {
        return when (action) {
            is DeepLinkAction.OpenPost -> {
                val base = "https://vk.com/wall${action.ownerId}_${action.postId}"
                if (action.commentId != 0L) "$base?reply=${action.commentId}" else base
            }
            is DeepLinkAction.OpenPhoto -> "https://vk.com/photo${action.ownerId}_${action.photoId}"
            is DeepLinkAction.OpenVideo -> "https://vk.com/video${action.ownerId}_${action.videoId}"
            is DeepLinkAction.OpenUser -> "https://vk.com/id${action.userId}"
            is DeepLinkAction.OpenCommunity -> "https://vk.com/club${-action.groupId}"
            DeepLinkAction.OpenNotifications -> "https://vk.com/notifications"
            DeepLinkAction.OpenDevices -> "https://vk.com/settings?act=devices"
        }
    }
}
