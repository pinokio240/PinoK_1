package re.pinok.realtime

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import re.pinok.api.VKApiClient
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog

/**
 * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04) — показ системного уведомления
 * при обнаружении подозрительного входа (новое устройство/город/IP).
 *
 * Канал: [VkNotificationsNotifier.CHANNEL_SECURITY_ALERTS] (IMPORTANCE_HIGH,
 * bypassDnd). Heads-up баннер + звук + вибрация.
 *
 * Структура уведомления:
 *  - smallIcon: ic_notification (white-on-transparent)
 *  - contentTitle: «⚠ Подозрительный вход» или «Новый вход в аккаунт»
 *  - contentText: «{device} · {location} · {ip}»
 *  - BigTextStyle: детали (device, app, ip, location, last_activity)
 *  - priority: HIGH (heads-up)
 *  - category: CATEGORY_MESSAGE (lock-screen messaging)
 *  - autoCancel: true (исчезает по тапу)
 *  - contentIntent: MainActivity + EXTRA_OPEN_DEVICES (deep-link к списку сессий)
 *  - P2.4: action button «Завершить сессию» — PendingIntent на SecurityAlertActionReceiver,
 *    который вызывает accountPersonal.resetSessions({device_id, hash}). Показывается ТОЛЬКО
 *    если в alert'е есть `device_id` (иначе action button не добавляем — нет цели для сброса).
 *
 * Источник: анализ архива VK ID_веб.zip, см. VK_IMPORT_API.MD §49.5.1.
 */
object SecurityAlertNotifier {

    private const val TAG = "SecurityAlertNotifier"
    private const val NOTIFICATION_ID = 7000  // 7000+ для security alerts (отдельный диапазон)
    // P2.4: requestCode для action-button PendingIntent. NOTIFICATION_ID + 1 чтобы
    // не конфликтовать с contentIntent (requestCode=0) и с другими alert'ами
    // (deviceId.hashCode() добавляется ниже для уникальности per-device).
    private const val NOTIF_ACTION_REQUEST_BASE = 7001

    /**
     * Показать уведомление о подозрительном входе.
     *
     * @param context
     * @param alert JSON-объект alert'а из accountPersonal.getSecurityAlerts.
     *   Ожидаемые поля:
     *   - `device_name` (String, опционально) — «Yandex Browser» / «iPhone 15»
     *   - `app_name` (String, опционально) — «PinoK» / «VK» / «VK ID»
     *   - `ip` (String, опционально) — «185.123.45.67»
     *   - `location` (String, опционально) — «Москва, Россия»
     *   - `last_activity` (Long, опционально) — unix timestamp
     *   - `is_suspicious` (Boolean, опционально) — true если VK считает вход подозрительным
     *   - `device_id` (String, опционально) — для action button «Завершить»
     */
    fun showAlert(context: Context, alert: JsonObject) {
        try {
            // §49.5.1: chain of safeString fallbacks (VK может отдавать разные ключи
            // в зависимости от источника alert'а). Без null-safe операторов — через локальные val.
            val dn1 = VKApiClient.safeString(alert.get("device_name"))
            val dn2 = VKApiClient.safeString(alert.get("device"))
            val deviceName = if (dn1 != null) dn1 else if (dn2 != null) dn2 else "Неизвестное устройство"
            val an1 = VKApiClient.safeString(alert.get("app_name"))
            val an2 = VKApiClient.safeString(alert.get("app"))
            val appName = if (an1 != null) an1 else an2
            val ip = VKApiClient.safeString(alert.get("ip"))
            val l1 = VKApiClient.safeString(alert.get("location"))
            val l2 = VKApiClient.safeString(alert.get("geo"))
            val location = if (l1 != null) l1 else l2
            val suspEl = alert.get("is_suspicious")
            val isSuspicious = suspEl != null && !suspEl.isJsonNull && suspEl.isJsonPrimitive && suspEl.asBoolean

            // P2.4: deviceId нужен для action button «Завершить сессию».
            // VK ID_веб alert содержит `device_id` (см. VK_IMPORT_API.MD §49.5.1,
            // VKApiClient.accountGetSecurityAlerts docstring line 17771).
            // Если поле отсутствует — action button НЕ показываем (нет цели для reset).
            val deviceId = VKApiClient.safeString(alert.get("device_id"))

            val title = if (isSuspicious) "⚠ Подозрительный вход" else "Новый вход в аккаунт"

            val subtitle = buildString {
                append(deviceName)
                if (!appName.isNullOrBlank()) append(" · ").append(appName)
                if (!location.isNullOrBlank()) append("\n📍 ").append(location)
                if (!ip.isNullOrBlank()) append("\n🌐 ").append(ip)
            }

            val builder = NotificationCompat.Builder(context, VkNotificationsNotifier.CHANNEL_SECURITY_ALERTS)
                .setSmallIcon(re.pinok.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(subtitle.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(buildContentIntent(context))

            // P2.4: action button «Завершить сессию» — только если есть deviceId.
            if (!deviceId.isNullOrBlank()) {
                builder.addAction(
                    re.pinok.R.drawable.ic_notification,
                    "Завершить сессию",
                    buildTerminateSessionIntent(context, deviceId)
                )
            } else {
                AppLog.w(TAG, "showAlert: no device_id in alert — action button «Завершить сессию» skipped")
            }

            val svc = context.getSystemService(Context.NOTIFICATION_SERVICE)
            if (svc !is NotificationManager) {
                AppLog.w(TAG, "showAlert: NotificationManager null")
                return
            }
            val nm = svc

            // Используем фиксированный ID — если пришло несколько alerts,
            // показываем только последний (для security alerts это ОК —
            // user не должен получить 5 всплывашек подряд).
            nm.notify(NOTIFICATION_ID, builder.build())
            AppLog.i(TAG, "showAlert: notified (device=$deviceName, loc=$location, suspicious=$isSuspicious)")
        } catch (e: Exception) {
            AppLog.e(TAG, "showAlert error", e)
        }
    }

    /**
     * Deep-link intent: открывает MainActivity → SovaNavHost → DevicesScreen.
     *
     * §49.6 Sprint VK-ID-1.6: теперь DevicesScreen реализован — тап по
     * уведомлению о подозрительном входе ведёт прямо к списку сессий,
     * где пользователь может завершить подозрительную одной кнопкой.
     */
    private fun buildContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_DEVICES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * P2.4: PendingIntent для action button «Завершить сессию».
     *
     * Отправляет broadcast на [SecurityAlertActionReceiver], который через
     * `goAsync()` вызывает `apiClient.accountResetSessions(deviceId, hash)`
     * (VK ID account API `accountPersonal.resetSessions`) и показывает Toast
     * с результатом. После успешного reset'а уведомление отменяется.
     *
     * FLAG_MUTABLE (Android S+) — обязателен, т.к. в intent есть extras,
     * которые receiver читает обратно. Для pre-S — только FLAG_UPDATE_CURRENT
     * (все PendingIntents на pre-S фактически mutable).
     */
    private fun buildTerminateSessionIntent(context: Context, deviceId: String): PendingIntent {
        val intent = Intent(context, SecurityAlertActionReceiver::class.java).apply {
            action = SecurityAlertActionReceiver.ACTION_TERMINATE_SESSION
            putExtra(SecurityAlertActionReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(SecurityAlertActionReceiver.EXTRA_NOTIF_ID, NOTIFICATION_ID)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        // requestCode = NOTIF_ACTION_REQUEST_BASE + deviceId.hashCode() для уникальности
        // per-device (если одновременно показаны 2 alert'а, кнопки не подменяют друг друга).
        val requestCode = NOTIF_ACTION_REQUEST_BASE + (deviceId.hashCode() and 0x00FFFFFF)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
