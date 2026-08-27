package re.pinok.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.R
import re.pinok.api.VKApiClient
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * §42.2 #PUSH-ENHANCED — системные уведомления о VK-событиях с группировкой,
 * приватностью превью, аватарами, quiet hours, авто-скрытием и кнопками действий.
 *
 * ## Архитектура группировки
 *
 * Раньше [showNotification] вызывался per-item → N отдельных уведомлений.
 * При 25 лайках за раз — 25 записей в шторке (см. скриншот 20260802_221731).
 *
 * Теперь [showBatch] принимает все новые items за один poll-цикл и:
 *   1. Фильтрует (quiet hours, per-user mute, category toggles).
 *   2. Группирует по mode (none/category/community/user).
 *   3. Для групп >= threshold: показывает summary (InboxStyle, сворачиваемый).
 *      Для групп < threshold: индивидуальные уведомления с setGroup + summary.
 *
 * Android 7+ сворачивает уведомления с одним group key в стопку «N new»,
 * которая разворачивается при тапе. Без summary — не сворачиваются (был баг).
 *
 * ## Режимы превью (pushPreviewMode)
 *
 * - "full": «Иван: текст поста...» (отправитель + превью текста).
 * - "sender_only": «Иван» (только имя, без текста — приватность).
 * - "hidden": «Новое уведомление» (полностью скрыто — только заголовок).
 *
 * ## Quiet hours (pushQuietHoursEnabled)
 *
 * Окно [start, end) в минутах от полуночи. Если текущее время в окне —
 * уведомления не показываются (молча пропускаются). Поддерживает переход
 * через полночь (start > end, например 22:00→08:00).
 *
 * ## Аватар (pushShowAvatar)
 *
 * Загружает photo100 feedback profile как Bitmap (через OkHttpClient на IO
 * потоке) → setLargeIcon. Best-effort: при ошибке загрузки — без аватара.
 *
 * ## BigPicture (pushShowBigPicture)
 *
 * §45 #PUSH-LOOK-AND-FEEL: для ВСЕХ типов с parentPhotoUrl или parentVideoThumb —
 * загружает картинку как BigPictureStyle (посты сообществ, фото, видео-превью).
 * Раньше только like_photo / comment_photo — теперь wall/post/mention/copy/
 * comment_post тоже показывают BigPicture (соответствует референсу 2026-08-03).
 *
 * ## Авто-скрытие (pushAutoDismissMs)
 *
 * setTimeoutAfter(ms) на builder. 0 = никогда (висит до ручного смахивания).
 *
 * ## Кнопки действий (pushActionButtons)
 *
 * «Прочитать» — PendingIntent с ACTION_MARK_READ → NotificationActionReceiver
 * вызывает notifications.markAsRead(date) + cancel notification.
 */
object VkNotificationsNotifier {

    private const val TAG = "VkNotificationsNotifier"
    private const val NOTIFICATION_ID_BASE = 5000  // 5000+ для VK-уведомлений

    // Per-category channels
    const val CHANNEL_LIKES = "vk_likes"
    const val CHANNEL_COMMENTS = "vk_comments"
    const val CHANNEL_REPLIES = "vk_replies"
    const val CHANNEL_FOLLOWS = "vk_follows"
    const val CHANNEL_MENTIONS = "vk_mentions"
    const val CHANNEL_REPOSTS = "vk_reposts"
    const val CHANNEL_WALL = "vk_wall"
    const val CHANNEL_GIFTS = "vk_gifts"
    const val CHANNEL_OTHER = "vk_other"
    // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): подозрительные входы.
    // IMPORTANCE_HIGH — heads-up баннер (поверх других уведомлений) + звук.
    // Critical: пользователь должен ВИДЕТЬ сразу, что кто-то вошёл с нового устройства.
    const val CHANNEL_SECURITY_ALERTS = "vk_security_alerts"

    private val nextId = AtomicInteger(NOTIFICATION_ID_BASE)

    /**
     * §42.5 #PUSH-GROUP-EXPAND — scope для асинхронной загрузки аватаров/BigPicture.
     *
     * showSingle() постит notification немедленно (text + intent + actions),
     * затем в этом scope грузит битмапы и обновляет notification тем же ID.
     * Это позволяет всем children группы появиться в шторке мгновенно, а не ждать
     * по 5-10с на каждый (avatar + BigPicture timeout) — иначе пользователь
     * разворачивает группу и видит только summary (один «пост»), а дети ещё не
     * успели загрузиться.
     */
    private val notifyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // OkHttpClient для загрузки аватаров/BigPicture (lazy — создаётся при первом use).
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Создаёт все notification channels. Вызывается из SovaApp.onCreate.
     *
     * IMPORTANCE_DEFAULT (не HIGH) — чтобы:
     *  - был звук уведомления (по умолчанию)
     *  - heads-up баннер появлялся только если уведомление новое
     *  - не раздражать частыми всплывашками (лайки могут идти потоком)
     *
     * Пользователь может настроить каждый канал отдельно в системных
     * настройках Android (звук/вибрация/важность).
     */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        if (nm == null) {
            AppLog.w(TAG, "init: NotificationManager null")
            return
        }
        val manager = nm as NotificationManager
        val channels = listOf(
            ChannelSpec(CHANNEL_LIKES, "Лайки", "Лайки ваших постов, комментариев, фото и видео"),
            ChannelSpec(CHANNEL_COMMENTS, "Комментарии", "Новые комментарии к вашим записям"),
            ChannelSpec(CHANNEL_REPLIES, "Ответы", "Ответы на ваши комментарии"),
            ChannelSpec(CHANNEL_FOLLOWS, "Новые подписчики", "Новые подписчики и принятые заявки в друзья"),
            ChannelSpec(CHANNEL_MENTIONS, "Упоминания", "Упоминания вас в постах и комментариях"),
            ChannelSpec(CHANNEL_REPOSTS, "Репосты", "Кто поделился вашими записями"),
            ChannelSpec(CHANNEL_WALL, "Записи на стене", "Новые записи на вашей стене"),
            ChannelSpec(CHANNEL_GIFTS, "Подарки", "Полученные подарки"),
            ChannelSpec(CHANNEL_OTHER, "Прочее", "Прочие уведомления (приглашения в группы, приложения и т.д.)"),
        )
        for (spec in channels) {
            if (manager.getNotificationChannel(spec.id) != null) continue
            val channel = NotificationChannel(
                spec.id,
                spec.name,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = spec.description
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
        // §49.5.1 #SAFETY-NET-ALERTS: отдельный канал с IMPORTANCE_HIGH
        // (отличается от обычных vk_* каналов с IMPORTANCE_DEFAULT).
        // Security alerts должны всплывать heads-up + со звуком — это критично.
        if (manager.getNotificationChannel(CHANNEL_SECURITY_ALERTS) == null) {
            val secChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Безопасность аккаунта",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о подозрительных входах (новое устройство, город). " +
                    "Источник: accountPersonal.getSecurityAlerts."
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                // §49.5.1: bypass Do Not Disturb для security alerts.
                setBypassDnd(true)
            }
            manager.createNotificationChannel(secChannel)
            AppLog.i(TAG, "Security alerts channel created (IMPORTANCE_HIGH, bypassDnd)")
        }
        AppLog.i(TAG, "Notification channels created (${channels.size} categories + 1 security)")
    }

    private data class ChannelSpec(
        val id: String,
        val name: String,
        val description: String,
    )

    /**
     * Возвращает ID канала для типа уведомления.
     */
    fun channelForType(type: String): String {
        return when {
            type.startsWith("like_") -> CHANNEL_LIKES
            type.startsWith("comment_") -> CHANNEL_COMMENTS
            type == "reply_comment" || type == "reply_to_comment" -> CHANNEL_REPLIES
            type == "follow" || type == "friend_accepted" -> CHANNEL_FOLLOWS
            type.startsWith("mention") -> CHANNEL_MENTIONS
            type == "copy" -> CHANNEL_REPOSTS
            type == "wall" -> CHANNEL_WALL
            type == "gift" -> CHANNEL_GIFTS
            else -> CHANNEL_OTHER
        }
    }

    /**
     * Возвращает человекочитаемое название типа для заголовка уведомления.
     */
    fun titleForType(type: String, count: Int): String {
        val singular = when {
            type.startsWith("like_") -> "Новый лайк"
            type.startsWith("comment_") -> "Новый комментарий"
            type == "reply_comment" || type == "reply_to_comment" -> "Новый ответ"
            type == "follow" -> "Новый подписчик"
            type == "friend_accepted" -> "Заявка принята"
            type.startsWith("mention") -> "Вас упомянули"
            type == "copy" -> "Новый репост"
            type == "wall" -> "Новая запись на стене"
            type == "gift" -> "Новый подарок"
            type == "invite_group" -> "Приглашение в сообщество"
            else -> "Новое уведомление"
        }
        return if (count > 1) "$singular ($count)" else singular
    }

    /**
     * Возвращает множественное название для group summary (например «5 новых лайков»).
     */
    private fun pluralTitle(type: String, count: Int): String {
        val word = when {
            type.startsWith("like_") -> pluralize(count, "лайк", "лайка", "лайков")
            type.startsWith("comment_") -> pluralize(count, "комментарий", "комментария", "комментариев")
            type == "reply_comment" || type == "reply_to_comment" -> pluralize(count, "ответ", "ответа", "ответов")
            type == "follow" -> pluralize(count, "подписчик", "подписчика", "подписчиков")
            type == "friend_accepted" -> pluralize(count, "заявка", "заявки", "заявок")
            type.startsWith("mention") -> pluralize(count, "упоминание", "упоминания", "упоминаний")
            type == "copy" -> pluralize(count, "репост", "репоста", "репостов")
            type == "wall" -> pluralize(count, "запись", "записи", "записей")
            type == "gift" -> pluralize(count, "подарок", "подарка", "подарков")
            type == "invite_group" -> pluralize(count, "приглашение", "приглашения", "приглашений")
            else -> pluralize(count, "уведомление", "уведомления", "уведомлений")
        }
        return "$count новых $word"
    }

    private fun pluralize(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> one
            mod10 in 2..4 && (mod100 < 10 || mod100 >= 20) -> few
            else -> many
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  BATCH ENTRY POINT (§42.2 #PUSH-ENHANCED)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Показать batch уведомлений за один poll-цикл. Применяет фильтры,
     * группировку, и показывает summary + индивидуальные уведомления.
     *
     * Фильтры применяются в порядке (AND — все должны пропустить):
     *   1. Quiet hours (глобально — пропускаем всё).
     *   2. Per-category toggles (pushLikes/pushComments/...).
     *   3. Source filter (pushFromCommunities/pushFromUsers) — §42.3.
     *   4. sn_* client-side filter (SnNotifyFilter) — §42.3.
     *   5. Per-user mute (pushPerUserMuted).
     *
     * @param context любой контекст
     * @param items все НОВЫЕ items (уже отфильтрованные по seenKeys в poller)
     * @param snap текущий snapshot настроек
     * @param categoryFilter функция-предикат: true если категория включена
     *        (poller передаёт isCategoryEnabled для проверки per-category toggles)
     * @param snStates кэш sn_* states (из notifyCacheJson) — §42.3 client-side filter
     */
    fun showBatch(
        context: Context,
        items: List<VKApiClient.NotificationItem>,
        snap: SovaPrefs.Snapshot,
        categoryFilter: (String) -> Boolean,
        snStates: Map<String, Boolean> = emptyMap(),
    ) {
        if (items.isEmpty()) return

        val ctx = context.applicationContext

        // 1. Проверка permission на Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                AppLog.w(TAG, "showBatch: notifications not permitted — skip all")
                return
            }
        }

        // 2. Quiet hours — если активно, не показываем ничего.
        if (snap.pushQuietHoursEnabled && isInQuietHours(snap)) {
            AppLog.d(TAG, "showBatch: in quiet hours — skip all (${items.size} items)")
            return
        }

        // 3. Фильтр: per-category + source + sn_* + per-user mute.
        val mutedUsers = parseMutedUsers(snap.pushPerUserMuted)
        val filtered = items.filter { item ->
            // 3a. Per-category toggle (pushLikes/pushComments/...).
            val channel = channelForType(item.type)
            if (!categoryFilter(channel)) {
                AppLog.d(TAG, "showBatch: category '$channel' disabled — skip type=${item.type}")
                return@filter false
            }
            // 3b. Source filter (§42.3): сообщества vs пользователи.
            // parentOwnerId < 0 = сообщество, > 0 = пользователь, 0 = неизвестно.
            val owner = item.parentOwnerId
            if (owner < 0 && !snap.pushFromCommunities) {
                AppLog.d(TAG, "showBatch: fromCommunities=false — skip owner=$owner type=${item.type}")
                return@filter false
            }
            if (owner > 0 && !snap.pushFromUsers) {
                AppLog.d(TAG, "showBatch: fromUsers=false — skip owner=$owner type=${item.type}")
                return@filter false
            }
            // 3c. sn_* client-side filter (§42.3): применяем BFF-настройки локально.
            if (!SnNotifyFilter.passes(item, snStates)) {
                return@filter false
            }
            // 3d. Per-user mute: если первый feedbackId в списке muted — skip.
            val actorId = item.feedbackIds.firstOrNull()
            if (actorId != null && actorId in mutedUsers) {
                AppLog.d(TAG, "showBatch: user $actorId muted — skip")
                return@filter false
            }
            true
        }

        if (filtered.isEmpty()) {
            AppLog.d(TAG, "showBatch: all ${items.size} items filtered out")
            return
        }

        AppLog.i(TAG, "showBatch: ${items.size} items → ${filtered.size} after filter, grouping=${snap.pushGroupingMode}")

        // 4. Группировка.
        val groups = groupItems(filtered, snap)

        // 5. Для каждой группы: показываем summary + индивидуальные.
        for ((groupKey, groupItems) in groups) {
            showGroup(ctx, groupKey, groupItems, snap)
        }
    }

    /**
     * Группирует items по выбранному mode. Возвращает list of (groupKey, items).
     *
     * - "none": каждый item в своей группе (key = uniqueKey), но без setGroup.
     * - "category": по channelForType(type).
     * - "community": по parentOwnerId (владелец поста/фото/видео).
     * - "user": по feedbackIds.first() (кто совершил действие).
     */
    private fun groupItems(
        items: List<VKApiClient.NotificationItem>,
        snap: SovaPrefs.Snapshot,
    ): List<Pair<String, List<VKApiClient.NotificationItem>>> {
        val mode = snap.pushGroupingMode
        return when (mode) {
            "none" -> {
                // Каждый item отдельно. groupKey = "none" (не используется для setGroup).
                items.map { it.uniqueKey to listOf(it) }
            }
            "community" -> {
                items.groupBy { "owner_${it.parentOwnerId}" }
                    .toList()
            }
            "user" -> {
                items.groupBy { "user_${it.feedbackIds.firstOrNull() ?: 0L}" }
                    .toList()
            }
            else -> {
                // "category" (default) — группировка по channelForType.
                items.groupBy { channelForType(it.type) }
                    .toList()
            }
        }
    }

    /**
     * Показывает одну группу: либо summary (если >= threshold), либо индивидуальные.
     *
     * При grouping mode != "none":
     *   - Индивидуальные уведомления получают setGroup(groupKey) — Android
     *     сворачивает их в стопку.
     *   - Summary с setGroupSummary(true) обязателен для сворачивания.
     *
     * При grouping mode == "none":
     *   - Индивидуальные без setGroup — каждое отдельно (старое поведение).
     */
    private fun showGroup(
        ctx: Context,
        groupKey: String,
        items: List<VKApiClient.NotificationItem>,
        snap: SovaPrefs.Snapshot,
    ) {
        val useGrouping = snap.pushGroupingMode != "none"
        val threshold = snap.pushGroupThreshold.coerceIn(1, 20)

        if (items.size >= threshold && useGrouping) {
            // §42.5 #PUSH-GROUP-EXPAND: children FIRST, summary LAST.
            // Android требует post summary последним — иначе система не собирает
            // children в стек, и summary показывается standalone с InboxStyle
            // (выглядит как «один пост»), а children не видны по отдельности.
            // Теперь все children сначала постятся (быстро, text-only),
            // битмапы догружаются async (notifyScope в showSingle), и лишь
            // затем summary — корректное сворачивание в expandable-стек.
            for (item in items) {
                showSingle(ctx, item, items.size, snap, groupKey)
            }
            showGroupSummary(ctx, groupKey, items, snap)
        } else {
            // Мало уведомлений — показываем индивидуально.
            for (item in items) {
                showSingle(ctx, item, items.size, snap, if (useGrouping) groupKey else null)
            }
            // Если grouping active и есть хотя бы 1 item — нужен summary
            // для корректного сворачивания (Android требует summary).
            if (useGrouping && items.isNotEmpty()) {
                showGroupSummary(ctx, groupKey, items, snap)
            }
        }
    }

    /**
     * Показывает group summary notification. Использует InboxStyle —
     * свёрнуто показывает «N новых X», развёрнуто — список строк (по одной на item).
     *
     * Tap → OpenNotifications (нельзя дать deep-link на конкретный item в summary).
     */
    private fun showGroupSummary(
        ctx: Context,
        groupKey: String,
        items: List<VKApiClient.NotificationItem>,
        snap: SovaPrefs.Snapshot,
    ) {
        try {
            val firstItem = items.first()
            val channelId = channelForType(firstItem.type)
            val count = items.size

            // Заголовок зависит от grouping mode.
            val title = when (snap.pushGroupingMode) {
                "community" -> {
                    val ownerName = resolveOwnerName(firstItem, items)
                    "$count уведомлений: $ownerName"
                }
                "user" -> {
                    val userName = firstItem.feedbackProfiles.firstOrNull()?.name
                        ?: firstItem.feedbackIds.firstOrNull()?.let { "id$it" }
                        ?: "VK"
                    "$count от $userName"
                }
                else -> {
                    // category
                    pluralTitle(firstItem.type, count)
                }
            }

            // InboxStyle: одна строка на item (до 8, потом «+N ещё»).
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(groupLabel(snap.pushGroupingMode))

            val maxLines = 8
            items.take(maxLines).forEach { item ->
                val line = buildShortLine(item, snap)
                inboxStyle.addLine(line)
            }
            if (items.size > maxLines) {
                inboxStyle.setSummaryText("+${items.size - maxLines} ещё")
            }

            val intent = android.content.Intent(ctx, re.pinok.ui.MainActivity::class.java).apply {
                action = VkUrlDeepLinker.ACTION_OPEN_NOTIFICATIONS
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                groupKey.hashCode() and 0x7FFFFFFF,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val builder = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("Нажмите, чтобы посмотреть")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setGroup(groupKey)
                .setGroupSummary(true)
                // §42.6: GROUP_ALERT_CHILDREN на детях + SUMMARY здесь = только дети
                // алертят (звук/heads-up), summary тихо обновляется. Стандартный
                // паттерн Android для grouped notifications.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)

            applyDisplayPrefs(builder, snap)

            // Summary ID = negative offset from base (чтобы не конфликтовать с индивидуальными).
            val summaryId = NOTIFICATION_ID_BASE - 1 - (groupKey.hashCode() and 0x0FFF)
            NotificationManagerCompat.from(ctx).notify(summaryId, builder.build())
            AppLog.d(TAG, "Group summary shown: key=$groupKey count=$count title='$title'")
        } catch (e: Exception) {
            AppLog.e(TAG, "showGroupSummary failed", e)
        }
    }

    /**
     * Показывает одно индивидуальное уведомление. При grouping active —
     * с setGroup(groupKey) для сворачивания в стопку.
     *
     * §42.5 #PUSH-GROUP-EXPAND: notification постится немедленно (text + intent +
     * actions + display prefs), БЕЗ ожидания загрузки битмапов. Аватар и BigPicture
     * догружаются асинхронно в [notifyScope] и обновляют notification тем же ID.
     *
     * Это критично для групп: раньше showSingle блокировал на 5-10с (avatar +
     * BigPicture timeout) на каждый item. При 5 children в группе = 50с, в течение
     * которых пользователь разворачивает группу и видит только summary (один
     * «пост»), а дети ещё не постятся. Теперь все дети появляются мгновенно.
     */
    private fun showSingle(
        ctx: Context,
        item: VKApiClient.NotificationItem,
        count: Int,
        snap: SovaPrefs.Snapshot,
        groupKey: String?,
    ) {
        try {
            val type = item.type
            val channelId = channelForType(type)

            // §45 #PUSH-LOOK-AND-FEEL: title = имя отправителя (сообщества/пользователя),
            // НЕ generic «Новая запись на стене». Соответствует референсу 2026-08-03:
            // bold title = «Телеканал 360», «Первый Тульский» и т.д.
            val senderName = resolveSenderName(item)
            val title = senderName

            // §45: body = глагол действия + превью текста («опубликовал(а) новый пост: …»).
            // Соответствует референсу: серая строка под заголовком = action verb,
            // далее превью текста поста.
            val actionVerb = buildActionVerb(type, count)
            val body = buildBodyWithAction(item, snap, actionVerb)

            // Deep-link action для тапа — каждый child имеет свой PendingIntent,
            // ведущий к конкретному посту/фото/видео/комментарию.
            val deepLink = VkUrlDeepLinker.deepLinkFor(item)
            val intent = buildIntent(ctx, deepLink, item)
            val requestCode = item.uniqueKey.hashCode() and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // §42.5: резервируем ID заранее — нужен и для ACTION_MARK_READ extra,
            // и для немедленного notify(), и для async-обновления битмапов.
            val notifId = nextId.incrementAndGet()

            val builder = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                // §45 #PUSH-LOOK-AND-FEEL: корректное время события (VK date в секундах →
                // Android ms). Соответствует референсу: справа вверху «2 ч назад».
                .setWhen(item.date * 1000L)
                .setShowWhen(true)

            // Группировка: setGroup для сворачивания в стек (summary posted separately).
            // §42.6: GROUP_ALERT_CHILDREN — каждое child-уведомление алертит отдельно
            // (звук/вибрация/heads-up), summary — тихо. Без этого при grouped-режиме
            // только summary алертит, дети появляются молча (пользователь не замечает).
            if (groupKey != null) {
                builder.setGroup(groupKey)
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            }

            // Кнопка «Прочитать» — mark as read + cancel.
            if (snap.pushActionButtons) {
                val markReadIntent = android.content.Intent(ctx, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_MARK_READ
                    putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIF_DATE, item.date)
                }
                val markReadPI = PendingIntent.getBroadcast(
                    ctx,
                    (item.uniqueKey.hashCode() and 0x7FFFFFFF) xor 0x1000,
                    markReadIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, "Прочитать", markReadPI)
            }

            // §46 #REMOTE-INPUT: кнопка «Ответить» с RemoteInput — прямой ответ
            // на комментарий/пост из шторки (без открытия приложения).
            // Показываем только для типов где ответ имеет смысл:
            //   - comment_post / comment_photo / comment_video — комментарий на пост/фото/видео
            //   - reply_comment / reply_to_comment — ответ на комментарий
            //   - mention (на посте/комментарии) — можно ответить
            //   - wall / copy — можно прокомментировать
            // Не показываем для: like_*, follow, friend_accepted, gift, invite_group
            // (там нет объекта для ответа).
            if (snap.pushReplyButton && canReplyToType(type)) {
                addReplyAction(builder, ctx, item, notifId)
            }

            applyDisplayPrefs(builder, snap)

            val nm = NotificationManagerCompat.from(ctx)
            // §42.5: немедленный пост text-only notification — все children группы
            // появляются в шторке мгновенно, пользователь может развернуть стек
            // и выбрать любой. Раньше тут блокировалось на loadBitmap (5-10с/шт).
            nm.notify(notifId, builder.build())
            AppLog.d(TAG, "Notification shown: type=$type title='$title' group=${groupKey ?: "none"} id=$notifId")

            // §42.5: async-обогащение битмапами (avatar + BigPicture).
            // Грузим в notifyScope (параллельно для разных children), обновляем
            // notification тем же notifId. Failures — best-effort (остаётся text).
            val wantAvatar = snap.pushShowAvatar
            // §45 #PUSH-LOOK-AND-FEEL: аватар = логотип сообщества для постов групп
            // (profilesMap[negativeOwnerId]), иначе feedback profile. Соответствует
            // референсу: аватар слева = лого канала/сообщества.
            val avatarUrl = if (wantAvatar) resolveAvatarUrl(item) else null
            // §45: BigPicture для ВСЕХ типов с фото или видео-превью (не только
            // like_photo/comment_photo). Соответствует референсу: картинка поста
            // показывается в теле уведомления для wall/post/mention/copy/...
            val wantBigPic = snap.pushShowBigPicture
            val photoUrl = when {
                !item.parentPhotoUrl.isNullOrBlank() -> item.parentPhotoUrl
                !item.parentVideoThumb.isNullOrBlank() -> item.parentVideoThumb
                else -> null
            }

            if (!avatarUrl.isNullOrBlank() || (wantBigPic && !photoUrl.isNullOrBlank())) {
                notifyScope.launch {
                    try {
                        var updated = false
                        if (!avatarUrl.isNullOrBlank()) {
                            // §45: аватар — 192px достаточно для largeIcon.
                            val bmp = loadBitmap(avatarUrl, targetPx = 192)
                            if (bmp != null) {
                                builder.setLargeIcon(bmp)
                                updated = true
                            }
                        }
                        if (wantBigPic && !photoUrl.isNullOrBlank()) {
                            // §45: BigPicture — 1024px для full-width картинки в шторке
                            // (раньше 192px → пиксельное/мыльное изображение).
                            val picBmp = loadBitmap(photoUrl, targetPx = 1024)
                            if (picBmp != null) {
                                // §45: BigPicture с senderName как bigContentTitle +
                                // actionVerb как summaryText (серая строка снизу).
                                builder.setStyle(
                                    NotificationCompat.BigPictureStyle()
                                        .bigPicture(picBmp)
                                        .setBigContentTitle(senderName)
                                        .setSummaryText(actionVerb)
                                )
                                updated = true
                            }
                        }
                        if (updated) {
                            nm.notify(notifId, builder.build())
                            AppLog.d(TAG, "Notification enriched: id=$notifId avatar=${!avatarUrl.isNullOrBlank()} bigPic=${wantBigPic && !photoUrl.isNullOrBlank()}")
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "async bitmap enrich failed: id=$notifId ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "showSingle failed", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  DISPLAY PREFERENCES
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Применяет общие настройки отображения к builder: auto-dismiss, sound, vibration, LED.
     */
    private fun applyDisplayPrefs(builder: NotificationCompat.Builder, snap: SovaPrefs.Snapshot) {
        // Авто-скрытие.
        if (snap.pushAutoDismissMs > 0) {
            builder.setTimeoutAfter(snap.pushAutoDismissMs)
        }
        // Звук.
        if (!snap.pushSoundEnabled) {
            builder.setSilent(true)
        }
        // Вибрация (только если звук тоже включён — иначе silent уже убрал и вибрацию;
        // для отдельного контроля вибрации нужен channel-level config, тут best-effort).
        if (!snap.pushVibrationEnabled) {
            builder.setVibrate(longArrayOf(0))
        }
        // LED цвет (только если channel его поддерживает — enableLights уже true).
        if (snap.pushLedColor != 0) {
            builder.setColor(snap.pushLedColor)
        }
    }

    // §45 #PUSH-LOOK-AND-FEEL: buildBody() заменён на buildBodyWithAction() —
    // теперь body = actionVerb + превью текста (а не «Имя: текст»).
    // Старый buildBody удалён как dead code.

    /**
     * Короткая строка для InboxStyle (одна строка на item в summary).
     */
    private fun buildShortLine(item: VKApiClient.NotificationItem, snap: SovaPrefs.Snapshot): String {
        val who = item.feedbackProfiles.firstOrNull()?.name?.take(25) ?: "VK"
        return when (snap.pushPreviewMode) {
            "hidden" -> "• $who"
            "sender_only" -> who
            else -> {
                val maxLen = (snap.pushPreviewLength.coerceIn(0, 200)).coerceAtMost(50)
                if (item.parentText.isNotBlank() && maxLen > 0) {
                    val preview = item.parentText.take(maxLen).replace("\n", " ")
                    "$who: $preview${if (item.parentText.length > maxLen) "…" else ""}"
                } else {
                    who
                }
            }
        }
    }

    /**
     * Лейбл для summaryText в InboxStyle (показывается развёрнуто внизу).
     */
    private fun groupLabel(mode: String): String = when (mode) {
        "community" -> "по сообществу"
        "user" -> "по пользователю"
        "category" -> "по категории"
        else -> ""
    }

    /**
     * Разрешает имя владельца (сообщества или пользователя) для group title
     * при grouping mode = "community".
     */
    private fun resolveOwnerName(
        firstItem: VKApiClient.NotificationItem,
        allItems: List<VKApiClient.NotificationItem>,
    ): String {
        // Ищем в profilesMap владельца (parentOwnerId может быть отрицательным = группа).
        val ownerId = firstItem.parentOwnerId
        if (ownerId != 0L) {
            val profile = firstItem.profilesMap[ownerId]
            if (profile != null) return profile.name
            // Группа: id положительный в profilesMap, но parentOwnerId отрицательный.
            if (ownerId < 0) {
                val groupProfile = firstItem.profilesMap[-ownerId]
                if (groupProfile != null) return groupProfile.name
            }
        }
        // Fallback: первый feedback profile.
        return firstItem.feedbackProfiles.firstOrNull()?.name ?: "VK"
    }

    // ──────────────────────────────────────────────────────────────────────
    //  §45 #PUSH-LOOK-AND-FEEL — helpers для референсного дизайна уведомлений
    // ──────────────────────────────────────────────────────────────────────

    /**
     * §45 #PUSH-LOOK-AND-FEEL: Разрешает имя отправителя для заголовка уведомления.
     *
     * В отличие от [titleForType] (который возвращает generic «Новая запись на стене»),
     * это возвращает КОНКРЕТНОЕ имя: сообщества (для постов групп) или пользователя.
     *
     * Соответствует референсу 2026-08-03: bold title = «Телеканал 360», «Первый Тульский».
     *
     * Порядок разрешения:
     *   1. profilesMap[parentOwnerId] — владелец поста/фото/видео (группы с negative ID).
     *   2. profilesMap[-parentOwnerId] — legacy fallback (группы с positive ID).
     *   3. feedbackProfiles.first() — кто совершил действие (лайк, комментарий).
     *   4. "VK" — последний fallback.
     */
    private fun resolveSenderName(item: VKApiClient.NotificationItem): String {
        val ownerId = item.parentOwnerId
        if (ownerId != 0L) {
            item.profilesMap[ownerId]?.name?.takeIf { it.isNotBlank() }?.let { return it }
            if (ownerId < 0) {
                item.profilesMap[-ownerId]?.name?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return item.feedbackProfiles.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "VK"
    }

    /**
     * §45 #PUSH-LOOK-AND-FEEL: Разрешает URL аватара отправителя.
     *
     * Для постов сообществ — аватар группы (из profilesMap[negativeOwnerId]),
     * для прочих — аватар первого feedback profile (кто совершил действие).
     *
     * Соответствует референсу: аватар слева = логотип канала/сообщества.
     * Prefer photo200 (чётче на больших экранах), fallback на photo100.
     */
    private fun resolveAvatarUrl(item: VKApiClient.NotificationItem): String? {
        val ownerId = item.parentOwnerId
        if (ownerId != 0L) {
            item.profilesMap[ownerId]?.let { p ->
                p.photo200.takeIf { it.isNotBlank() }?.let { return it }
                p.photo100.takeIf { it.isNotBlank() }?.let { return it }
            }
            if (ownerId < 0) {
                item.profilesMap[-ownerId]?.let { p ->
                    p.photo200.takeIf { it.isNotBlank() }?.let { return it }
                    p.photo100.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        val fb = item.feedbackProfiles.firstOrNull()
        return fb?.photo200?.takeIf { it.isNotBlank() }
            ?: fb?.photo100?.takeIf { it.isNotBlank() }
    }

    /**
     * §45 #PUSH-LOOK-AND-FEEL: Глагол действия для подзаголовка/body.
     *
     * Возвращает описание действия в зависимости от type:
     * «опубликовал(а) новый пост», «оценил(а)», «оставил(а) комментарий», и т.д.
     *
     * Соответствует референсу: серая строка под заголовком = action verb
     * (например «опубликовало 2 новых поста», «опубликовал новый пост»).
     *
     * @param count число действий в группе (для множественного числа).
     */
    private fun buildActionVerb(type: String, count: Int): String {
        val plural = count > 1
        return when {
            type.startsWith("like_") ->
                if (plural) "оценили ($count)" else "оценил(а)"
            type.startsWith("comment_") ->
                if (plural) "прокомментировали ($count)" else "оставил(а) комментарий"
            type == "reply_comment" || type == "reply_to_comment" ->
                if (plural) "ответили ($count)" else "ответил(а)"
            type == "follow" ->
                if (plural) "подписались ($count)" else "подписался(ась)"
            type == "friend_accepted" ->
                if (plural) "приняли заявки ($count)" else "принял(а) заявку"
            type.startsWith("mention") ->
                if (plural) "упомянули вас ($count)" else "упомянул(а) вас"
            type == "copy" ->
                if (plural) "поделились записью ($count)" else "поделился(ась) записью"
            type == "wall" ->
                if (plural) "опубликовали на стене ($count)" else "опубликовал(а) на стене"
            type == "gift" ->
                if (plural) "отправили подарки ($count)" else "отправил(а) подарок"
            type == "invite_group" ->
                if (plural) "пригласили в сообщества ($count)" else "пригласил(а) в сообщество"
            // §45: post / new_post — пост от сообщества/пользователя (референс).
            type == "post" || type == "new_post" ->
                if (plural) "опубликовали новые посты ($count)" else "опубликовал(а) новый пост"
            else ->
                if (plural) "новые действия ($count)" else "новое действие"
        }
    }

    /**
     * §45 #PUSH-LOOK-AND-FEEL: Строит body = action verb + превью текста поста.
     *
     * Формат (full): «опубликовал(а) новый пост: Школьники встретили президента…»
     * Соответствует референсу: content text = action verb + preview текста.
     *
     * Уважает pushPreviewMode:
     *   - "hidden": только actionVerb (без текста поста — приватность).
     *   - "sender_only": только actionVerb (без текста).
     *   - "full": actionVerb + ": " + preview текста (до pushPreviewLength символов).
     */
    private fun buildBodyWithAction(
        item: VKApiClient.NotificationItem,
        snap: SovaPrefs.Snapshot,
        actionVerb: String,
    ): String {
        return when (snap.pushPreviewMode) {
            "hidden" -> actionVerb
            "sender_only" -> actionVerb
            else -> {
                // "full"
                val maxLen = snap.pushPreviewLength.coerceIn(0, 200)
                if (item.parentText.isBlank() || maxLen == 0) {
                    actionVerb
                } else {
                    val preview = item.parentText.take(maxLen).replace("\n", " ")
                    val suffix = if (item.parentText.length > maxLen) "…" else ""
                    "$actionVerb: $preview$suffix"
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  §46 #REMOTE-INPUT — кнопка «Ответить» с RemoteInput
    // ──────────────────────────────────────────────────────────────────────

    /**
     * §46 #REMOTE-INPUT: определяет, для каких типов уведомлений имеет смысл
     * кнопка «Ответить» (прямой ответ из шторки).
     *
     * Ответ имеет смысл когда есть родительский объект (пост/фото/видео/комментарий),
     * на который можно оставить комментарий. Не имеет смысла для:
     *   - like_* — лайк, нечего отвечать
     *   - follow / friend_accepted — подписка/заявка, ответ не через комментарий
     *   - gift — подарок, ответ через другое API
     *   - invite_group — приглашение, ответ через другое API
     *
     * @return true если тип поддерживает ответ через wall.createComment /
     *         photos.createComment.
     */
    private fun canReplyToType(type: String): Boolean {
        return when {
            type.startsWith("like_") -> false
            type == "follow" || type == "friend_accepted" -> false
            type == "gift" -> false
            type == "invite_group" -> false
            // comment_*, reply_comment, reply_to_comment, mention*, copy, wall,
            // post, new_post — все имеют parent object для ответа.
            else -> true
        }
    }

    /**
     * §46 #REMOTE-INPUT: определяет target type для отправки комментария
     * на основе parentType уведомления.
     *
     * - parentType "photo" → photos.createComment
     * - parentType "video" → video.createComment (§48 #VIDEO-BOARD-COMMENT)
     * - parentType "topic" → board.createComment (§48 #VIDEO-BOARD-COMMENT)
     * - прочее (post, wall, comment) → wall.createComment
     *
     * @return "wall" | "photo" | "video" | "topic" | null (null = нельзя ответить).
     */
    private fun replyTargetType(item: VKApiClient.NotificationItem): String? {
        return when (item.parentType.lowercase()) {
            "photo" -> "photo"
            "video" -> "video"
            "topic" -> "topic"
            "post", "wall", "comment", "" -> "wall"
            else -> "wall"  // default — пробуем wall.createComment
        }
    }

    /**
     * §46 #REMOTE-INPUT: добавляет кнопку «Ответить» с RemoteInput в notification.
     *
     * Создаёт PendingIntent на [NotificationActionReceiver] с ACTION_REPLY,
     * упаковывает extras (ownerId, itemId, commentId, targetType) и RemoteInput
     * (ключ EXTRA_REPLY_TEXT). Система показывает текстовое поле прямо в шторке
     * при нажатии «Ответить» — без открытия приложения.
     *
     * @param builder notification builder для addAction
     * @param ctx context
     * @param item уведомление (для извлечения ownerId/itemId/commentId)
     * @param notifId ID notification (для обновления результата)
     */
    private fun addReplyAction(
        builder: NotificationCompat.Builder,
        ctx: Context,
        item: VKApiClient.NotificationItem,
        notifId: Int,
    ) {
        val targetType = replyTargetType(item) ?: return
        val ownerId = item.parentOwnerId
        val itemId = item.parentItemId
        // Если parentOwnerId или parentItemId = 0 — ответ невозможен (нет объекта).
        if (ownerId == 0L || itemId == 0L) return

        val replyIntent = android.content.Intent(ctx, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationActionReceiver.EXTRA_REPLY_TARGET_TYPE, targetType)
            putExtra(NotificationActionReceiver.EXTRA_REPLY_OWNER_ID, ownerId)
            putExtra(NotificationActionReceiver.EXTRA_REPLY_ITEM_ID, itemId)
            putExtra(NotificationActionReceiver.EXTRA_REPLY_COMMENT_ID, item.parentCommentId)
        }
        val replyPI = PendingIntent.getBroadcast(
            ctx,
            // Уникальный requestCode для reply (xor 0x2000, чтобы не конфликтовать с mark-read).
            (item.uniqueKey.hashCode() and 0x7FFFFFFF) xor 0x2000,
            replyIntent,
            // §46: FLAG_MUTABLE обязателен для RemoteInput — система встраивает
            // results в intent перед доставкой. MUTABLE + RemoteInput — безопасно
            // (intent не exported, только система может доставлять).
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // RemoteInput — текстовое поле в шторке.
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REPLY_TEXT)
            .setLabel("Ответить…")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            0,  // icon (0 = без иконки, только текст; иконка не видна в expanded mode)
            "Ответить",
            replyPI,
        )
            .addRemoteInput(remoteInput)
            // §46: ALLOW_GENERATED_REPLIES — разрешаем системе предлагать
            // сгенерированные ответы (smart replies) на основе контекста.
            .setAllowGeneratedReplies(true)
            // §46: SEMANTIC_ACTION_REPLY — помечаем как действие «ответить»,
            // система может оптимизировать отображение.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        builder.addAction(replyAction)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  QUIET HOURS
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Проверяет, находится ли текущее время в окне quiet hours.
     * Поддерживает переход через полночь (start > end, например 22:00→08:00).
     *
     * @param snap snapshot с pushQuietHours* настройками
     * @return true если сейчас quiet hours (не показывать уведомления)
     */
    private fun isInQuietHours(snap: SovaPrefs.Snapshot): Boolean {
        if (!snap.pushQuietHoursEnabled) return false
        val now = java.util.Calendar.getInstance()
        val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val start = snap.pushQuietHoursStart
        val end = snap.pushQuietHoursEnd
        return if (start <= end) {
            nowMin in start until end
        } else {
            // Переход через полночь: 22:00→08:00 → nowMin >= 22:00 OR nowMin < 08:00.
            nowMin >= start || nowMin < end
        }
    }

    /**
     * Парсит CSV muted user IDs в Set<Long>.
     */
    private fun parseMutedUsers(csv: String): Set<Long> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  BITMAP LOADING (avatars, BigPicture)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Загружает Bitmap из URL (best-effort, с timeout 5с).
     * Вызывается из IO потока (poller уже на IO). При ошибке — null.
     *
     * §45 #PUSH-LOOK-AND-FEEL: добавлен параметр targetPx — целевой max размер
     * по большей стороне. Для аватара (largeIcon) достаточно 192px, но для
     * BigPicture нужно 1024px (full-width image в шторке). Раньше всегда 192px —
     * BigPicture был пиксельным/мыльным. Теперь:
     *   - avatar: loadBitmap(url, targetPx = 192)
     *   - BigPicture: loadBitmap(url, targetPx = 1024)
     *
     * @param url URL картинки (photo_100, photo_200, parentPhotoUrl, ...)
     * @param targetPx целевой max размер по большей стороне (default 192 = аватар).
     */
    private fun loadBitmap(url: String, targetPx: Int = 192): Bitmap? {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "loadBitmap: HTTP ${response.code} for $url")
                    return null
                }
                val body = response.body ?: return null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return null
                // Декодируем с downsample до targetPx по большей стороне.
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, targetPx)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "loadBitmap failed for $url: ${e.message}")
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((width / sample) > target || (height / sample) > target) {
            sample *= 2
        }
        return sample
    }

    // ──────────────────────────────────────────────────────────────────────
    //  INTENT BUILDER (deep links)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Строит Intent для PendingIntent на основе DeepLinkAction.
     *
     * Intent → MainActivity.handleDeepLinkIntent → SovaNavHost навигация.
     */
    private fun buildIntent(
        ctx: Context,
        deepLink: VkUrlDeepLinker.DeepLinkAction,
        item: VKApiClient.NotificationItem,
    ): android.content.Intent {
        val intent = android.content.Intent(ctx, re.pinok.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, item.type)
            putExtra(EXTRA_NOTIFICATION_TITLE, titleForType(item.type, 1))
        }
        when (deepLink) {
            is VkUrlDeepLinker.DeepLinkAction.OpenPost -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_POST
                intent.putExtra(VkUrlDeepLinker.EXTRA_OWNER_ID, deepLink.ownerId)
                intent.putExtra(VkUrlDeepLinker.EXTRA_ITEM_ID, deepLink.postId)
                // §42.4 #PUSH-DEEPLINK: ID комментария для скролла в PostDetailScreen
                // (reply_comment / comment). 0 = просто открыть пост.
                if (deepLink.commentId != 0L) {
                    intent.putExtra(VkUrlDeepLinker.EXTRA_COMMENT_ID, deepLink.commentId)
                }
            }
            is VkUrlDeepLinker.DeepLinkAction.OpenPhoto -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_PHOTO
                intent.putExtra(VkUrlDeepLinker.EXTRA_OWNER_ID, deepLink.ownerId)
                intent.putExtra(VkUrlDeepLinker.EXTRA_ITEM_ID, deepLink.photoId)
                // §42.4 #PUSH-DEEPLINK: URL фото для нативного PhotoViewer.
                // null/пусто → SovaNavHost fallback на InternalBrowser.
                if (!deepLink.photoUrl.isNullOrBlank()) {
                    intent.putExtra(VkUrlDeepLinker.EXTRA_PHOTO_URL, deepLink.photoUrl)
                }
                if (!deepLink.accessKey.isNullOrBlank()) {
                    intent.putExtra(VkUrlDeepLinker.EXTRA_ACCESS_KEY, deepLink.accessKey)
                }
            }
            is VkUrlDeepLinker.DeepLinkAction.OpenVideo -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_VIDEO
                intent.putExtra(VkUrlDeepLinker.EXTRA_OWNER_ID, deepLink.ownerId)
                intent.putExtra(VkUrlDeepLinker.EXTRA_ITEM_ID, deepLink.videoId)
            }
            is VkUrlDeepLinker.DeepLinkAction.OpenUser -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_USER
                intent.putExtra(VkUrlDeepLinker.EXTRA_USER_ID, deepLink.userId)
            }
            is VkUrlDeepLinker.DeepLinkAction.OpenCommunity -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_COMMUNITY
                intent.putExtra(VkUrlDeepLinker.EXTRA_GROUP_ID, deepLink.groupId)
            }
            VkUrlDeepLinker.DeepLinkAction.OpenNotifications -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_NOTIFICATIONS
            }
            VkUrlDeepLinker.DeepLinkAction.OpenDevices -> {
                intent.action = VkUrlDeepLinker.ACTION_OPEN_DEVICES
            }
        }
        return intent
    }

    /** Отменить все VK-уведомления (вызывается при открытии NotificationsScreen). */
    fun cancelAll(context: Context) {
        try {
            val nm = NotificationManagerCompat.from(context.applicationContext)
            // Отменяем все уведомления в диапазоне NOTIFICATION_ID_BASE..(current nextId)
            val currentMax = nextId.get()
            for (id in NOTIFICATION_ID_BASE..currentMax) {
                nm.cancel(id)
            }
            // Также отменяем summary IDs (negative offset от base).
            for (id in (NOTIFICATION_ID_BASE - 1000)..(NOTIFICATION_ID_BASE - 1)) {
                nm.cancel(id)
            }
            AppLog.d(TAG, "All VK notifications cancelled")
        } catch (e: Exception) {
            AppLog.w(TAG, "cancelAll failed: ${e.message}")
        }
    }

    /** Extras keys для MainActivity.handleDeepLinkIntent. */
    const val EXTRA_NOTIFICATION_TYPE = "notif_type"
    const val EXTRA_NOTIFICATION_TITLE = "notif_title"
}
