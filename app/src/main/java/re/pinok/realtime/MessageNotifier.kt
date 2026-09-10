package re.pinok.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import re.pinok.ui.MainActivity
import re.pinok.R
import re.pinok.util.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * #32: Системные уведомления о новых сообщениях.
 *
 * Подписывается на [LongPollClient.events] и показывает системное notification
 * при входящем сообщении (LongPollEvent.NewMessage).
 *
 * Группирует уведомления по peerId — новое сообщение в существующем диалоге
 * обновляет существующее notification (не создаёт новое).
 *
 * Требует POST_NOTIFICATIONS permission (Android 13+) — запрашивается в
 * [re.pinok.util.PermissionManager.RequestAllPermissionsEffect].
 */
object MessageNotifier {

    private const val TAG = "MessageNotifier"

    // Fix #390 #NOTIFY-MODES (волна 29): константы каналов стали public —
    // вызывающий (SovaApp.startMessageNotifier) выбирает канал ПО режиму
    // уведомлений и типу отправителя (юзер/сообщество), нотифаер prefs не знает.
    const val CHANNEL_MESSAGES = "messages"
    // Тихий канал сообщений (Fix #390 «Тихий режим»): heads-up ЕСТЬ (HIGH),
    // но звук null и вибрация отключены.
    const val CHANNEL_MESSAGES_SILENT = "messages_silent"
    // Каналы сообществ (Fix #390): обычный / без вибрации / полностью тихий.
    const val CHANNEL_COMMUNITIES = "communities"
    const val CHANNEL_COMMUNITIES_NOVIB = "communities_novib"
    const val CHANNEL_COMMUNITIES_SILENT = "communities_silent"

    private const val CHANNEL_NAME = "Сообщения"
    private const val NOTIFICATION_ID_BASE = 1000

    // Fix #208: Intent action + extras для открытия чата из push-уведомления.
    const val ACTION_OPEN_CHAT = "re.pinok.action.OPEN_CHAT"
    const val EXTRA_PEER_ID = "peer_id"
    const val EXTRA_TITLE = "title"
    // Fix #135b: Intent action для swipe-dismiss notification. SetDeleteIntent
    // в NotificationCompat срабатывает когда пользователь смахивает уведомление
    // или делает "Clear all". NotificationActionReceiver ловит этот action и
    // чистит activeNotifications кеш, чтобы следующее сообщение в этом диалоге
    // стартовало с unreadCount=1 (а не инкрементировало устаревший счётчик).
    const val ACTION_DISMISS = "re.pinok.action.DISMISS"

    /** peerId → (title, lastMessage, unreadCount) — для обновления существующих уведомлений. */
    private val activeNotifications = ConcurrentHashMap<Long, NotificationData>()

    // Audit #S5: internal — иначе public getActiveNotification() экспонирует
    // private-тип и компилятор падает: «'public' function exposes its 'private-in-class'
    // return type 'NotificationData'». internal = виден в модуле app, чего достаточно
    // для SovaApp (другой пакет, тот же модуль).
    internal data class NotificationData(
        val title: String,
        val text: String,
        val unreadCount: Int,
        // Fix #285: cached mute-стейт диалога. true → уведомления для этого
        // peer подавляются (showNotification не вызывает nm.notify). Поле
        // обновляется через [setMuted] при toggle в MessagesScreen, чтобы кеш
        // не рассинхронизировался после un-mute.
        val muted: Boolean = false,
    )

    /**
     * Инициализация notification channels. Вызывается из [re.pinok.SovaApp.onCreate].
     *
     * Fix #390 #NOTIFY-MODES: РАНЬШЕ был один канал "messages" (HIGH + звук +
     * вибрация). Теперь 5 каналов: базовый "messages", тихий "messages_silent"
     * («Тихий режим» — heads-up есть, но без звука/вибрации) и три канала для
     * сообществ ("communities" — как "messages", "communities_novib" — без
     * вибрации, "communities_silent" — без звука и вибрации; отключение
     * звука/вибрации сообществ — по требованию юзера). Выбор канала — на стороне
     * вызывающего (SovaApp), здесь создаются ВСЕ — создание канала идемпотентно.
     */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Fix #390 #NOTIFY-MODES + NULL-ЯВНО: РАНЬШЕ было `val nm = ... as?
            // NotificationManager` + `nm?.createNotificationChannel(...)`; при
            // 5 каналах это 5 одинаковых ?. — по правилу NULL-ЯВНО заменено на
            // явную проверку + смарт-каст (прецедент — VkNotificationsNotifier.init).
            val nmService = context.getSystemService(Context.NOTIFICATION_SERVICE)
            if (nmService == null) {
                AppLog.w(TAG, "init: NotificationManager is null — channels skipped")
                return
            }
            val nm = nmService as NotificationManager

            // Базовый канал сообщений: sound + heads-up + вибрация + light.
            val channel = NotificationChannel(
                CHANNEL_MESSAGES,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,  // sound + heads-up
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                enableLights(true)
            }
            nm.createNotificationChannel(channel)

            // Fix #390 #NOTIFY-MODES: тихий канал сообщений — IMPORTANCE_HIGH
            // сохраняет heads-up (всплывающий баннер), но звук/вибрация выключены.
            val silentChannel = NotificationChannel(
                CHANNEL_MESSAGES_SILENT,
                "Сообщения (без звука)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Всплывающие уведомления о сообщениях без звука и вибрации (Тихий режим)"
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
            }
            nm.createNotificationChannel(silentChannel)

            // Fix #390 #NOTIFY-MODES: каналы сообществ. Отдельные от "messages",
            // чтобы юзер мог отключить ТОЛЬКО звук/вибрацию от сообществ, не трогая
            // сообщения (параметры notifyCommunitiesSound/Vibration выбирают канал).
            val communitiesChannel = NotificationChannel(
                CHANNEL_COMMUNITIES,
                "Сообщества",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Всплывающие уведомления от сообществ (звук + вибрация)"
                enableVibration(true)
                enableLights(true)
            }
            nm.createNotificationChannel(communitiesChannel)

            val communitiesNovibChannel = NotificationChannel(
                CHANNEL_COMMUNITIES_NOVIB,
                "Сообщества (без вибрации)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Всплывающие уведомления от сообществ со звуком, без вибрации"
                enableVibration(false)
                enableLights(true)
            }
            nm.createNotificationChannel(communitiesNovibChannel)

            val communitiesSilentChannel = NotificationChannel(
                CHANNEL_COMMUNITIES_SILENT,
                "Сообщества (без звука)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Всплывающие уведомления от сообществ без звука и вибрации"
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
            }
            nm.createNotificationChannel(communitiesSilentChannel)

            AppLog.i(TAG, "Notification channels created: $CHANNEL_MESSAGES, $CHANNEL_MESSAGES_SILENT, $CHANNEL_COMMUNITIES, $CHANNEL_COMMUNITIES_NOVIB, $CHANNEL_COMMUNITIES_SILENT")
        }
    }

    /**
     * Показать или обновить уведомление о новом сообщении.
     *
     * @param context любой контекст (используется applicationContext)
     * @param peerId  ID диалога (для группировки)
     * @param title   имя отправителя / название чата
     * @param text    текст сообщения (или "Изображение", "Видео", etc.)
     * @param unreadCount сколько непрочитанных в этом диалоге
     * @param channelId канал уведомления (Fix #390 #NOTIFY-MODES): вызывающий
     *        (SovaApp.startMessageNotifier) выбирает по режиму уведомлений и
     *        типу отправителя — CHANNEL_MESSAGES / CHANNEL_MESSAGES_SILENT /
     *        CHANNEL_COMMUNITIES / CHANNEL_COMMUNITIES_NOVIB /
     *        CHANNEL_COMMUNITIES_SILENT. Default CHANNEL_MESSAGES — прежнее
     *        поведение для существующих вызовов.
     */
    fun showNotification(
        context: Context,
        peerId: Long,
        title: String,
        text: String,
        unreadCount: Int = 1,
        muted: Boolean = false,
        channelId: String = CHANNEL_MESSAGES,
    ) {
        try {
            val ctx = context.applicationContext
            activeNotifications[peerId] = NotificationData(title, text, unreadCount, muted)

            // Fix #285: заглушённый диалог — не показываем системное уведомление.
            // NotificationData всё равно кешируем (с muted=true), чтобы последующие
            // сообщения в этом диалоге сразу знали mute-стейт из cached.muted без
            // повторного messagesGetConversationsById lookup.
            if (muted) {
                AppLog.d(TAG, "Notification suppressed (muted): peer=$peerId title='$title' unread=$unreadCount")
                return
            }

            // Проверяем permission на Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (NotificationManagerCompat.from(ctx).areNotificationsEnabled().not()) {
                    AppLog.w(TAG, "Notifications not permitted — skipping")
                    return
                }
            }

            // Fix #208: Pending intent — открыть приложение на MainActivity и
            // автоматически перейти в диалог, которому принадлежит уведомление.
            // peer_id + title передаются как extras; MainActivity.handleOpenChatIntent()
            // читает их и навигирует на ChatDetailScreen.
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = ACTION_OPEN_CHAT
                putExtra(EXTRA_PEER_ID, peerId)
                putExtra(EXTRA_TITLE, title)
            }
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                peerId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // Fix #135b: Delete intent — срабатывает когда пользователь свайпает
            // уведомление (или делает "Clear all"). Без него activeNotifications
            // кеш продолжал считать диалог «активным» даже после dismiss → при
            // следующем входящем сообщении unreadCount инкрементировался от
            // старого значения (например 5 вместо 1). Теперь dismiss сбрасывает
            // кеш через ACTION_DISMISS, и следующее сообщение стартует с 1.
            val deleteIntent = Intent(ctx, NotificationActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_PEER_ID, peerId)
            }
            val deletePendingIntent = PendingIntent.getBroadcast(
                ctx,
                (peerId.toInt() xor 0x5E5E5E5E.toInt()),  // уникальный requestCode чтобы не конфликтовал с contentIntent
                deleteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // Fix #135a: РАНЬШЕ было "$text ($unreadCount непрочитанных)" — это
            // дублировало информацию (setNumber ниже уже показывает счётчик в
            // системном UI). Теперь text всегда чистый, а unreadCount передаётся
            // через setNumber — это правильный Android-way для messaging notifications.
            val displayText = text

            val notification = NotificationCompat.Builder(ctx, channelId)  // Fix #390 #NOTIFY-MODES: канал выбирает вызывающий
                .setSmallIcon(R.drawable.ic_notification)  // нужен простой white-on-transparent icon
                .setContentTitle(title)
                .setContentText(displayText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
                // Fix #135c: setNumber — показывает маленький badge с количеством
                // непрочитанных рядом с иконкой приложения в шторке. Стандартный
                // Android-way отобразить счётчик (вместо "$text (N непрочитанных)").
                .setNumber(unreadCount.coerceAtLeast(1))
                // Fix #135d: видимость на lockscreen — показываем полностью
                // (иначе на заблокированном экране только "Новое сообщение").
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                // #NOTIF-FEED-FILTER (19-B/19-E, волна 18-δ): диалоговые уведомления —
                // ВЫШЕ новостных в шторке. setSortKey сортирует внутри одного пакета
                // при равной важности: диалогам "0" (MessageNotifier), новостным "1"
                // (VkNotificationsNotifier showSingle/summary) — пара закрывает
                // порядок «диалоги → новости» системными средствами.
                .setSortKey("0")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                // Fix #135e: убран .setGroup("messages_$peerId") без summary.
                // РАНЬШЕ: setGroup без setGroupSummary(true) на Android 7+ (API 24+)
                // на некоторых OEM ROM (MIUI/OneUI/EMUI) мог вызвать collapse
                // notification, скрывая content text. Так как каждый peerId и
                // так получает ОДНО уведомление (NOTIFICATION_ID_BASE + peerId.hashCode
                // — обновляется на месте), grouping был не нужен и только вредил.
                .setColor(0xFF4A76A8.toInt())  // VK brand blue (для акцента в shade)
                .setColorized(false)
                .build()

            val nm = NotificationManagerCompat.from(ctx)
            @Suppress("DEPRECATION")
            nm.notify(NOTIFICATION_ID_BASE + peerId.hashCode(), notification)
            AppLog.d(TAG, "Notification shown: peer=$peerId title='$title' unread=$unreadCount text='$text'")
        } catch (e: Exception) {
            AppLog.e(TAG, "showNotification failed", e)
        }
    }

    /**
     * Отменить уведомление для диалога (когда пользователь открыл чат).
     */
    fun cancelNotification(context: Context, peerId: Long) {
        try {
            activeNotifications.remove(peerId)
            val nm = NotificationManagerCompat.from(context.applicationContext)
            nm.cancel(NOTIFICATION_ID_BASE + peerId.hashCode())
            AppLog.d(TAG, "Notification cancelled: peer=$peerId")
        } catch (e: Exception) {
            AppLog.w(TAG, "cancelNotification failed: ${e.message}")
        }
    }

    /**
     * P0.2: возвращает активное уведомление для диалога (или null).
     *
     * Используется [SovaApp.startMessageNotifier] для:
     * - получения cached title (имя чата) без повторного API-вызова
     * - инкремента unreadCount при следующих сообщениях в этом диалоге
     */
    // Audit #S5-fix2: internal — иначе public function экспонирует internal-тип
    // NotificationData (Kotlin forbid public member exposing less-visible type).
    // Единственный caller — SovaApp.kt в том же модуле :app → internal достаточно.
    internal fun getActiveNotification(peerId: Long): NotificationData? = activeNotifications[peerId]

    /**
     * Fix #285: обновляет cached mute-стейт для диалога. Вызывается из
     * MessagesScreen.onToggleMute после успешного API-вызова, чтобы кеш
     * MessageNotifier не рассинхронизировался (иначе после un-mute следующее
     * сообщение всё ещё считалось бы заглушённым из cached.muted=true).
     *
     * Если кеша для peerId ещё нет — ничего не делает: при первом сообщении
     * mute вычислится через messagesGetConversationsById lookup.
     */
    fun setMuted(peerId: Long, muted: Boolean) {
        val existing = activeNotifications[peerId]
        if (existing != null) {
            activeNotifications[peerId] = existing.copy(muted = muted)
        }
        AppLog.d(TAG, "setMuted: peer=$peerId muted=$muted (cached=${existing != null})")
    }

    /** Отменить все уведомления о сообщениях. */
    fun cancelAll(context: Context) {
        try {
            val nm = NotificationManagerCompat.from(context.applicationContext)
            activeNotifications.keys.forEach { peerId ->
                nm.cancel(NOTIFICATION_ID_BASE + peerId.hashCode())
            }
            activeNotifications.clear()
            AppLog.d(TAG, "All message notifications cancelled")
        } catch (e: Exception) {
            AppLog.w(TAG, "cancelAll failed: ${e.message}")
        }
    }
}
