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
    private const val CHANNEL_ID = "messages"
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
     * Инициализация notification channel. Вызывается из [re.pinok.SovaApp.onCreate].
     */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,  // sound + heads-up
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                enableLights(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
            AppLog.i(TAG, "Notification channel '$CHANNEL_ID' created")
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
     */
    fun showNotification(
        context: Context,
        peerId: Long,
        title: String,
        text: String,
        unreadCount: Int = 1,
        muted: Boolean = false,
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

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
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
