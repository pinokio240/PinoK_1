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

    // P0.5b (2026-10-22): persistent disable flag — accountPersonal.getSecurityAlerts
    // НЕ существует ни на api.vk.com, ни на web.api.vk.ru (подтверждено logcat:
    // err=3 "Unknown method passed" на обоих шлюзах). Это private VK ID API,
    // недоступный через обычный access_token. Graceful backoff (P0.5) просто
    // откладывал шум на 1 час, потом poller перезапускался на следующем app launch.
    // Теперь: после MAX_CONSECUTIVE err=3 → persist flag в SharedPreferences →
    // start() проверяет флаг и НЕ запускает poller. Флаг можно сбросить только
    // вручную (clear app data) или если VK добавит метод (проверить через triggerImmediatePoll).
    private val disablePrefs = context.getSharedPreferences("pinok_security_alerts", Context.MODE_PRIVATE)

    private val supervisorJob = SupervisorJob()
    private val pollScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var periodicJob: Job? = null

    private val pollMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * P0.5-fix (2026-10-23): счётчик последовательных "method unavailable"
     * (VK API err=3 "Unknown method passed") ответов от accountPersonal.getSecurityAlerts.
     *
     * Поведение:
     *  - err=3 на poll'е → counter++, poller НЕ останавливается (раньше stop()
     *    убивал poller навсегда, и SecurityAlertNotifier НИКОГДА не вызывался).
     *  - Каждый следующий poll происходит через safetyNetPollIntervalMin (default 10 мин) —
     *    это естественный backoff (метод может появиться на стороне VK).
     *  - После [MAX_CONSECUTIVE_METHOD_UNAVAILABLE] (6 = 1 час при default interval)
     *    последовательных err=3 — graceful-disable с INFO-логом "method unavailable
     *    after N polls — disabling poller until next app start".
     *  - Любой успешный poll (alerts != null) → сброс counter в 0.
     *
     * Почему НЕ permanent-stop на первом err=3:
     *  - VK может выкатить метод позже (web-bundle обновляется).
     *  - При переключении auth-флоу (web_token ↔ legacy) может временно пропадать.
     *  - SovaApp.start() вызывается один раз в onCreate — если stop() сработал,
     *    poller больше никогда не запустится до полного перезапуска приложения.
     */
    private var consecutiveMethodUnavailable: Int = 0

    private companion object {
        /**
         * Порог graceful-disable: 6 polls × 10 мин (default interval) = 1 час.
         * Если за час VK ни разу не ответил успешно — отключаем poller
         * (он не нужен: метод не существует на текущем шлюзе VK).
         * P0.5b: теперь также persist'им флаг в SharedPreferences чтобы
         * poller НЕ перезапускался на следующем app launch (метод стабильно
         * отсутствует — logcat 2026-10-22 подтверждает err=3 на api.vk.com).
         */
        const val MAX_CONSECUTIVE_METHOD_UNAVAILABLE = 6
        const val KEY_METHOD_UNAVAILABLE = "method_unavailable_permanent"
    }

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
        // P0.5b: check persistent disable flag — если метод стабильно возвращал
        // err=3 в прошлых сессиях, НЕ запускаем poller (экономия батареи/трафика
        // + чистый logcat без err=3 каждые 10 мин).
        if (disablePrefs.getBoolean(KEY_METHOD_UNAVAILABLE, false)) {
            AppLog.i(TAG, "start: permanently disabled — accountPersonal.getSecurityAlerts " +
                "method unavailable (err=3 persisted from previous session). " +
                "Clear app data to retry if VK adds the method.")
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
                // P0.5-fix (2026-10-23): #SAFETY-NET-ALERTS-NO-PERM-STOP.
                // accountPersonal.getSecurityAlerts возвращает err=3 "Unknown
                // method passed" (DIAG-A2 §2, DIAG-B2 §10). Раньше здесь был
                // stop() — poller умирал НАВСЕГДА, SecurityAlertNotifier НИКОГДА
                // не вызывался. Теперь: counter++ и ждём следующий poll
                // (safetyNetPollIntervalMin = default 10 мин — естественный
                // backoff). После MAX_CONSECUTIVE_METHOD_UNAVAILABLE (6 = 1 час)
                // — graceful-disable с INFO (метод стабильно отсутствует).
                val err = api.lastApiError
                val isMethodUnavailable = err != null && (
                    err.contains("Unknown method", ignoreCase = true) ||
                    err.contains("API error 3", ignoreCase = true)
                )
                if (isMethodUnavailable) {
                    consecutiveMethodUnavailable++
                    if (consecutiveMethodUnavailable >= MAX_CONSECUTIVE_METHOD_UNAVAILABLE) {
                        // P0.5b: permanent disable — persist flag в SharedPreferences.
                        // Метод стабильно отсутствует (err=3 на api.vk.com AND web.api.vk.ru,
                        // подтверждено logcat 2026-10-22). Poller НЕ перезапустится на
                        // следующем app launch — start() проверит флаг и выйдет с INFO логом.
                        // Сбросить можно только clear app data (или если VK добавит метод
                        // и пользователь manually триггернет triggerImmediatePoll после сброса).
                        disablePrefs.edit().putBoolean(KEY_METHOD_UNAVAILABLE, true).apply()
                        AppLog.i(TAG, "pollOnce: accountPersonal.getSecurityAlerts " +
                            "unavailable for $consecutiveMethodUnavailable consecutive polls " +
                            "(err=$err) — PERMANENT disable (method not exposed by VK on any " +
                            "gateway, persisting flag — poller will not restart on next app launch)")
                        stop()
                    } else {
                        AppLog.w(TAG, "pollOnce: method unavailable " +
                            "($consecutiveMethodUnavailable/$MAX_CONSECUTIVE_METHOD_UNAVAILABLE, " +
                            "err=$err) — will retry on next interval " +
                            "(api.vk.com / web gateway may be misrouted; backoff via poll interval)")
                    }
                } else {
                    AppLog.d(TAG, "pollOnce: no alerts (null response, err=$err)")
                }
                return@withLock
            }
            // Успешный poll (хоть и 0 alerts) — сбрасываем счётчик err=3:
            // метод доступен, SecurityAlertNotifier chain жив.
            if (consecutiveMethodUnavailable > 0) {
                AppLog.i(TAG, "pollOnce: method available again after " +
                    "$consecutiveMethodUnavailable unavailable polls — counter reset")
                consecutiveMethodUnavailable = 0
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
