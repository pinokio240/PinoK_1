package re.pinok.realtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import re.pinok.R
import re.pinok.util.AppLog

/**
 * §46 #REMOTE-INPUT — показывает результат отправки ответа из notification.
 *
 * После того как пользователь ввёл текст ответа в RemoteInput-диалоге и
 * [NotificationActionReceiver] отправил комментарий через VK API, нужно
 * дать визуальный фидбек:
 *
 * 1. **Success** — notification обновляется: «✓ Ответ отправлен» + превью текста.
 *    Через 3 секунды notification cancel'ится (auto-dismiss).
 *
 * 2. **Error** — notification обновляется: «✗ Не удалось отправить» + причина.
 *    Через 5 секунд cancel (даём время прочитать ошибку).
 *
 * Без этого фидбека пользователь не понимает, ушёл ли ответ — RemoteInput
 * закрывает диалог, но не показывает результат отправки.
 *
 * Использует тот же notifId, что и исходное уведомление — обновляет in-place
 * (не создаёт новую запись в шторке).
 */
object ReplyResultNotifier {

    private const val TAG = "ReplyResultNotifier"
    private const val CHANNEL_ID = VkNotificationsNotifier.CHANNEL_COMMENTS
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Показать успешный результат: «✓ Ответ отправлен» + превью текста.
     * Auto-cancel через 3 секунды.
     *
     * @param notifId ID исходного notification (обновляем in-place).
     * @param replyText текст ответа (для превью, обрезается до 80 символов).
     */
    fun showSuccess(context: Context, notifId: Int, replyText: String) {
        try {
            val preview = if (replyText.length > 80) {
                replyText.take(80) + "…"
            } else {
                replyText
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("✓ Ответ отправлен")
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                // §46: auto-dismiss через 3 секунды.
                .setTimeoutAfter(3000L)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            AppLog.d(TAG, "Success shown: notifId=$notifId preview='${preview.take(40)}'")

            // Fallback cancel через 3.5с (на случай если setTimeoutAfter не сработал).
            scheduleCancel(context, notifId, 3500L)
        } catch (e: Exception) {
            AppLog.w(TAG, "showSuccess failed: ${e.message}")
        }
    }

    /**
     * Показать ошибку: «✗ Не удалось отправить» + причина.
     * Auto-cancel через 5 секунд.
     *
     * @param notifId ID исходного notification.
     * @param error сообщение об ошибке (может быть null — покажем generic).
     */
    fun showError(context: Context, notifId: Int, error: String?) {
        try {
            val errorText = if (!error.isNullOrBlank()) {
                "Ошибка: $error"
            } else {
                "Проверьте подключение к сети и попробуйте снова"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("✗ Не удалось отправить ответ")
                .setContentText(errorText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(errorText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // §46: auto-dismiss через 5 секунд (даём прочитать ошибку).
                .setTimeoutAfter(5000L)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            AppLog.d(TAG, "Error shown: notifId=$notifId error='${error?.take(60)}'")

            // Fallback cancel через 5.5с.
            scheduleCancel(context, notifId, 5500L)
        } catch (e: Exception) {
            AppLog.w(TAG, "showError failed: ${e.message}")
        }
    }

    /**
     * Планирует cancel notification через [delayMs] (fallback для setTimeoutAfter).
     * Запускается на main handler (не блокирует IO).
     */
    private fun scheduleCancel(context: Context, notifId: Int, delayMs: Long) {
        handler.postDelayed({
            try {
                NotificationManagerCompat.from(context).cancel(notifId)
            } catch (e: Exception) {
                AppLog.d(TAG, "scheduleCancel: cancel failed for notifId=$notifId: ${e.message}")
            }
        }, delayMs)
    }
}
