package re.pinok.realtime

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import re.pinok.api.VKApiClient
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

/**
 * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04) — периодический опрос
 * `accountPersonal.getSecurityAlerts` + показ system notifications
 * при обнаружении подозрительных входов (новое устройство/город/IP).
 *
 * Архитектура (аналог NotificationsPoller §42, но для security alerts):
 *
 *   1. Периодический таймер (safetyNetPollIntervalMin, default 10 мин).
 *   2. pollOnce():
 *      a) api.accountGetSecurityAlerts(hash=logout_hash).
 *      b) Diff с seenAlertIds (CSV в SovaPrefs — `safetyNetSeenAlerts`).
 *      c) Для каждого нового alert: SecurityAlertNotifier.showAlert().
 *      d) Обновление seenAlertIds (max 50 последних).
 *
 * Жизненный цикл:
 *  - start() вызывается из SovaApp.onCreate (если pushSafetyNetAlerts && token).
 *  - stop() вызывается при logout.
 *
 * Источник: анализ архива VK ID_веб.zip, см. VK_IMPORT_API.MD §49.5.1.
 *
 * ВАЖНО: этот poller НЕ зависит от LongPoll — accountPersonal.getSecurityAlerts
 * это REST API, не push. Polling раз в 10 мин — компромисс между promptness
 * и battery/traffic. После login (success auth) SovaApp вызывает
 * triggerImmediatePoll() один раз — не ждём 10 мин для первого alert'а.
 */
class SecurityAlertsPoller(
    private val context: Context,
    private val api: VKApiClient,
    private val prefs: SovaPrefs,
) {

    private val TAG = "SecurityAlertsPoller"

    private val supervisorJob = SupervisorJob()
    private val pollScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var periodicJob: Job? = null

    private val pollMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Запускает периодический опрос. Безопасно вызывать многократно.
     *
     * Не запускает poll если:
     *  - pushSafetyNetAlerts=false (в SovaPrefs)
     *  - нет валидного токена
     */
    fun start() {
        if (_isRunning.value) {
            AppLog.d(TAG, "start: already running — skip")
            return
        }
        _isRunning.value = true
        periodicJob = pollScope.launch {
            AppLog.i(TAG, "start: security alerts poller started")
            // Сразу один poll при старте (не ждём 10 мин).
            pollOnce()
            while (isActive) {
                val intervalMin = runCatching {
                    prefs.data.first().safetyNetPollIntervalMin
                }.getOrDefault(10)
                val intervalMs = intervalMin.coerceIn(1, 1440) * 60_000L
                delay(intervalMs)
                if (!isActive) break
                pollOnce()
            }
        }
    }

    fun stop() {
        if (!_isRunning.value) return
        _isRunning.value = false
        val j = periodicJob
        if (j != null) j.cancel()
        periodicJob = null
        AppLog.i(TAG, "stop: security alerts poller stopped")
    }

    /**
     * Принудительный poll — вызывается из SovaApp после успешного login
     * (не ждём 10 мин для первого alert'а) или из UI (refresh button).
     */
    fun triggerImmediatePoll() {
        pollScope.launch {
            pollOnce()
        }
    }

    private suspend fun pollOnce() = pollMutex.withLock {
        if (!isEnabled()) {
            AppLog.d(TAG, "pollOnce: disabled — skip")
            return@withLock
        }
        try {
            val hash = getLogoutHash()
            val alerts = api.accountGetSecurityAlerts(hash)
            if (alerts == null) {
                AppLog.d(TAG, "pollOnce: no alerts (null response)")
                return@withLock
            }
            if (alerts.size() == 0) {
                AppLog.d(TAG, "pollOnce: 0 alerts")
                return@withLock
            }

            AppLog.i(TAG, "pollOnce: got ${alerts.size()} alerts")

            // Diff с seen alert IDs.
            val seenIds = getSeenAlertIds().toMutableSet()
            val newAlerts = alerts.filter { alert ->
                val id = alertId(alert)
                if (id.isEmpty()) return@filter false
                !seenIds.contains(id)
            }

            if (newAlerts.isEmpty()) {
                AppLog.d(TAG, "pollOnce: 0 NEW alerts (all seen)")
                return@withLock
            }

            AppLog.i(TAG, "pollOnce: ${newAlerts.size} NEW alerts — showing notifications")
            for (alert in newAlerts) {
                if (!alert.isJsonObject) continue
                val obj = alert.asJsonObject
                SecurityAlertNotifier.showAlert(context, obj)
                // Mark as seen.
                val id = alertIdOfObj(obj)
                if (id.isEmpty()) continue
                seenIds.add(id)
            }

            // Save seen IDs (max 50).
            val toSave = seenIds.toList().takeLast(50)
            saveSeenAlertIds(toSave)
        } catch (e: Exception) {
            AppLog.e(TAG, "pollOnce error", e)
        }
    }

    private suspend fun isEnabled(): Boolean {
        return runCatching {
            val snap = prefs.data.first()
            snap.pushSafetyNetAlerts && snap.pushEnabled
        }.getOrDefault(false)
    }

    private fun getLogoutHash(): String? {
        return runCatching {
            re.pinok.SovaApp.get().tokenStorage.logoutHash()
        }.getOrNull()
    }

    private fun getSeenAlertIds(): Set<String> {
        // Храним в SharedPreferences (отдельный от SovaPrefs — это runtime cache).
        val sp = context.getSharedPreferences("security_alerts_cache", Context.MODE_PRIVATE)
        // SharedPreferences.getString с непустым default фактически не возвращает null,
        // но Kotlin этого не знает — проверяем явно через isNullOrBlank.
        val csv = sp.getString("seen_alert_ids", "")
        return if (csv.isNullOrBlank()) emptySet() else csv.split(",").toSet()
    }

    private fun saveSeenAlertIds(ids: List<String>) {
        val sp = context.getSharedPreferences("security_alerts_cache", Context.MODE_PRIVATE)
        sp.edit().putString("seen_alert_ids", ids.joinToString(",")).apply()
    }

    /** ID alert'а из JsonElement. Пусто = не распознан (нет asJsonObject). */
    private fun alertId(el: com.google.gson.JsonElement): String {
        if (!el.isJsonObject) return ""
        return alertIdOfObj(el.asJsonObject)
    }

    /** ID alert'а из JsonObject: alert_id → device_id → ts. Пусто = нет ни одного. */
    private fun alertIdOfObj(obj: JsonObject): String {
        val a = VKApiClient.safeString(obj.get("alert_id"))
        if (a != null) return a
        val d = VKApiClient.safeString(obj.get("device_id"))
        if (d != null) return d
        val t = VKApiClient.safeString(obj.get("ts"))
        if (t != null) return t
        return ""
    }
}
