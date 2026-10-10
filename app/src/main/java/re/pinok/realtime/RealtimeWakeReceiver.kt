package re.pinok.realtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * #DOZE-RELIABILITY (2026-10-10): периодическое пробуждение realtime-канала
 * через AlarmManager.setExactAndAllowWhileIdle.
 *
 * ПРОБЛЕМА: FCM нет → доставка сообщений зависит от LongPoll/EventHub, которые
 * живут в процессе SovaApp. Android Doze может усыпить сеть/CPU фонового процесса
 * даже при живом foreground-сервисе (LongPollKeepAliveService держит процесс, но
 * НЕ гарантирует сеть/CPU). Тогда LongPoll «замерзает» и сообщения не приходят,
 * пока пользователь не откроет приложение.
 *
 * РЕШЕНИЕ (мера C): этот receiver вызывается системой по будильнику
 * setExactAndAllowWhileIdle — он срабатывает ДАЖЕ В ГЛУБОКОМ DOZE (в отличие от
 * обычных будильников). На каждом срабатывании:
 *   1. если есть валидный токен — будим realtime: longPollClient.notifyResumed()
 *      (сброс backoff + эвикт застоевших TCP + отмена in-flight poll) и
 *      eventHubClient.start() (idempotent);
 *   2. перепланируем следующий будильник (иначе цепочка оборвётся).
 *
 * ВАЖНО: setExactAndAllowWhileIdle в Doze реально срабатывает не чаще ~раз в
 * 9 минут (системное ограничение) — интервал [INTERVAL_MS] = 15 мин безопасен.
 *
 * Тикеты: BootReceiver (после загрузки/обновления), LongPollKeepAliveService
 * (start/onStartCommand) планируют будильник; MainActivity (logout/выход) — cancel.
 */
class RealtimeWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLog.i(TAG, "#DOZE-RELIABILITY wake-receiver: action=$action")
        try {
            val app = SovaApp.getOrNull()
            if (app == null) {
                AppLog.d(TAG, "#DOZE-RELIABILITY app not ready — skip wake (still re-scheduling)")
            } else if (app.tokenStorage.hasValidToken()) {
                // Пробуждаем LongPoll (сброс backoff + эвикт соединений + отмена in-flight).
                app.longPollClient.notifyResumed()
                // EventHub стартуем идемпотентно (no-op если уже работает).
                app.eventHubClient.start()
                AppLog.i(TAG, "#DOZE-RELIABILITY realtime woken (LongPoll.notifyResumed + EventHub.start)")
            } else {
                AppLog.d(TAG, "#DOZE-RELIABILITY no valid token — not waking realtime")
            }
        } catch (e: Exception) {
            // Receiver не должен падать — система может убить процесс при исключении.
            AppLog.w(TAG, "#DOZE-RELIABILITY wake failed: ${e.message}")
        }
        // Перепланируем следующий будильник — цепочка должна продолжаться.
        schedule(context)
    }

    companion object {
        const val TAG = "RealtimeWake"
        const val ACTION_WAKE = "re.pinok.action.REALTIME_WAKE"
        const val WAKE_REQUEST_CODE = 0x5A5A
        /** Интервал пробуждения. В Doze setExactAndAllowWhileIdle не чаще ~9 мин. */
        const val INTERVAL_MS = 15 * 60_000L

        /**
         * Запланировать следующий будильник пробуждения. Точный
         * (setExactAndAllowWhileIdle) если разрешён SCHEDULE_EXACT_ALARM,
         * иначе — inexact setAndAllowWhileIdle (всё равно будит в Doze).
         * Идемпотентно: FLAG_UPDATE_CURRENT перезапишет существующий.
         */
        fun schedule(context: Context) {
            try {
                val nmService = context.getSystemService(Context.ALARM_SERVICE)
                if (nmService == null) {
                    AppLog.w(TAG, "#DOZE-RELIABILITY schedule: AlarmManager is null — skip")
                    return
                }
                val alarmManager = nmService as AlarmManager
                val intent = Intent(context, RealtimeWakeReceiver::class.java).setAction(ACTION_WAKE)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    WAKE_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val triggerAt = System.currentTimeMillis() + INTERVAL_MS
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    AppLog.i(TAG, "#DOZE-RELIABILITY alarm scheduled (exact, +${INTERVAL_MS / 60_000}min)")
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    AppLog.i(TAG, "#DOZE-RELIABILITY alarm scheduled (inexact, no exact-alarm permission, +${INTERVAL_MS / 60_000}min)")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "#DOZE-RELIABILITY schedule failed: ${e.message}")
            }
        }

        /** Отменить будильник (logout/выход). Идемпотентно. */
        fun cancel(context: Context) {
            try {
                val nmService = context.getSystemService(Context.ALARM_SERVICE)
                if (nmService == null) {
                    AppLog.w(TAG, "#DOZE-RELIABILITY cancel: AlarmManager is null — skip")
                    return
                }
                val alarmManager = nmService as AlarmManager
                val intent = Intent(context, RealtimeWakeReceiver::class.java).setAction(ACTION_WAKE)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    WAKE_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    AppLog.i(TAG, "#DOZE-RELIABILITY alarm cancelled")
                } else {
                    AppLog.d(TAG, "#DOZE-RELIABILITY cancel: no pending alarm")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "#DOZE-RELIABILITY cancel failed: ${e.message}")
            }
        }
    }
}
