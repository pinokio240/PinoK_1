package re.pinok.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * P2.4 (DIAG-B2 §1.3, §9 / DIAG-SUMMARY P2 #21) — приёмник кнопки
 * «Завершить сессию» из security alert уведомления
 * (см. [SecurityAlertNotifier]).
 *
 * Когда пользователь нажимает «Завершить сессию» в шторке уведомлений,
 * NotificationCompat отправляет PendingIntent (созданный
 * [SecurityAlertNotifier.buildTerminateSessionIntent]) на этот BroadcastReceiver.
 * Мы:
 *   1. Извлекаем `device_id` из extras.
 *   2. Через `goAsync()` даём себе время на сетевой запрос (не блокируем
 *      onReceive дольше ~10с — это лимит BroadcastReceiver).
 *   3. Вызываем `apiClient.accountResetSessions(deviceId, logoutHash)`
 *      (VK ID account API: `accountPersonal.resetSessions`).
 *   4. При успехе — отменяем notification + Toast «Сессия завершена».
 *      При неудаче — Toast «Не удалось завершить сессию».
 *
 * Зарегистрирован в AndroidManifest.xml с intent-filter на
 * [ACTION_TERMINATE_SESSION] (exported=false — только системные PendingIntents
 * из этого же app могут запускать receiver).
 *
 * ВАЖНО: `validationToken` (cua.check*Code) НЕ передаётся. Для большинства
 * действий VK ID требует 2FA confirmation (через cua.* methods), но в
 * текущей реализации PinoK мы пробуем прямой reset — если VK ответит ошибкой
 * "validation required", пользователь увидит Toast и сможет завершить
 * сессию вручную через DevicesScreen (tap по notification).
 */
class SecurityAlertActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_TERMINATE_SESSION) return

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        if (deviceId.isNullOrBlank()) {
            AppLog.w(TAG, "onReceive: device_id missing in extras — skip")
            return
        }
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, DEFAULT_NOTIF_ID)
        AppLog.i(TAG, "onReceive: terminate session deviceId=$deviceId notifId=$notifId")

        // goAsync() — даёт до ~10с на завершение async-работы, не блокируя
        // главный thread BroadcastReceiver'а. finish() обязателен в конце.
        val pendingResult = goAsync()
        scope.launch {
            var ok = false
            try {
                val app = SovaApp.get(context.applicationContext)
                // logoutHash — auth hash из web_token JSON (нужен для VK ID
                // account API). Если null — VK может принять просто access_token.
                val hash = runCatching {
                    app.tokenStorage.logoutHash()
                }.getOrNull()
                ok = app.apiClient.accountResetSessions(
                    deviceId = deviceId,
                    hash = hash,
                    // validationToken=null — пробуем без 2FA; VK сам скажет,
                    // нужно ли confirmation (тогда покажем Toast-ошибку).
                    validationToken = null,
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "terminate session failed (deviceId=$deviceId)", e)
            } finally {
                // Toast обязательно на main looper (BroadcastReceiver onReceive
                // сам на main, но корутина на IO — нужен post).
                Handler(Looper.getMainLooper()).post {
                    val msg = if (ok) "Сессия завершена" else "Не удалось завершить сессию"
                    Toast.makeText(context.applicationContext, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                }
                if (ok) {
                    try {
                        NotificationManagerCompat.from(context.applicationContext).cancel(notifId)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "cancel notification failed: ${e.message}")
                    }
                }
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    AppLog.w(TAG, "pendingResult.finish failed: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SecurityAlertActionReceiver"
        private const val DEFAULT_NOTIF_ID = 7000  // = SecurityAlertNotifier.NOTIFICATION_ID

        /** Broadcast action: «Завершить сессию». Зарегистрирован в AndroidManifest.xml. */
        const val ACTION_TERMINATE_SESSION = "re.pinok.action.TERMINATE_SESSION"

        /** Extra: device_id (String) для accountPersonal.resetSessions. */
        const val EXTRA_DEVICE_ID = "extra_device_id"

        /** Extra: notification_id (Int) для cancel после успеха. */
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
