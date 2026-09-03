// File: realtime/LongPollClient.kt
package re.pinok.realtime

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import re.pinok.api.VKApiClient
import re.pinok.feature.calls.CallsLongPoll
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog
import re.pinok.util.NetworkObserver
import java.net.URLEncoder
import kotlin.math.min
import kotlin.random.Random

/**
 * Real-time LongPoll-цикл для сообщений VK.
 *
 * Улучшения стабильности соединения:
 *  - Слушает [NetworkObserver.isOnlineFlow] — при потере сети прерывает poll,
 *    при восстановлении немедленно переподключается.
 *  - Exponential backoff с jitter при ошибках (2с → 4с → 8с → ... → 60с макс).
 *  - При потере сети вызывает [OkHttpClient.connectionPool.evictAll()] —
 *    очищает застоявшиеся TCP-соединения на мёртвом интерфейсе (WiFi → 4G).
 *  - readTimeout httpClient должен быть ≥ 45с (wait=25 + запас).
 */
// Task 20: реализует CallsLongPoll (фасад :feature:calls, член без вызовов экранов).
class LongPollClient(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
    private val networkObserver: NetworkObserver? = null,
    /**
     * P4.2: настройки для backfill'а пропущенных между сессиями событий.
     * Если null или [SovaPrefs.Snapshot.msgLpBackfill] == false — backfill отключён,
     * поведение идентично прежнему (fresh getLongPollServer каждый старт).
     */
    private val prefs: SovaPrefs? = null,
    /**
     * §43 #NET-SWITCH-DELAY: поток инвалидаций токена (SovaApp.tokenInvalidationTicks).
     *
     * Когда VKAPIView получает error 5/1117 (IP mismatch после switch Wi-Fi↔Mobile),
     * callInternal() вызывает SovaApp.notifyTokenInvalidated() → инкремент этого счётчика.
     *
     * Без этого параметра LongPollClient продолжает hammer messagesGetLongPollServer
     * каждые ~10с во время 30-секундного grace period'а — каждый вызов занимает ~5.5с
     * (grace delay 5с + retry) и возвращает null → пользователь видит 30+ секунд
     * «зависания» после переключения сети.
     *
     * Теперь loop() подписан на этот flow: при инкременте tick loop делает паузу
     * до [TOKEN_INVALIDATION_PAUSE_MS] (30с max) — даёт AuthActivity/ensureFreshToken
     * время завершить re-login. Пауза проверяет [isTokenValid] каждые 2с и выходит
     * раньше если токен восстановлен (AuthActivity success → новый токен в storage).
     *
     * null = функция отключена (старое поведение, для обратной совместимости).
     */
    private val tokenInvalidationTicks: kotlinx.coroutines.flow.SharedFlow<Int>? = null,
    /**
     * §43 #NET-SWITCH-DELAY: lambda-проверка валидности токена для раннего выхода
     * из token-invalidation pause. Возвращает true если в TokenStorage есть
     * валидный access_token (не истёкший по timestamp).
     *
     * Вызывается каждые 2с во время паузы — если токен восстановлен (AuthActivity
     * silent re-login через remixsid завершился успешно), пауза прерывается
     * немедленно вместо ожидания полных 30с.
     *
     * null = проверка недоступна, пауза ждёт полное [TOKEN_INVALIDATION_PAUSE_MS].
     */
    private val isTokenValid: (() -> Boolean)? = null,
) : CallsLongPoll {
    private val _events = MutableSharedFlow<LongPollEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LongPollEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var networkJob: Job? = null
    /**
     * §43 #NET-SWITCH-DELAY: job-подписка на [tokenInvalidationTicks] flow.
     * См. [startTokenInvalidationWatcher].
     */
    private var tokenInvalidationJob: Job? = null

    @Volatile
    private var running = false

    /** Флаг «прервать текущий wait» — ставится при resume/восстановлении сети. */
    @Volatile
    private var interruptWait = false

    /**
     * §43 #NET-SWITCH-DELAY: timestamp до которого loop() должен ждать прежде
     * чем продолжать poll (token invalidation pause).
     *
     * Когда [notifyTokenInvalidated] вызывается (из SovaApp.tokenInvalidationTicks
     * flow), поле устанавливается в `now + TOKEN_INVALIDATION_PAUSE_MS`. Loop()
     * в начале каждой итерации проверяет это поле — если `now < tokenPauseUntilMs`,
     * ждёт остаток времени (interruptible, чтобы resume/network-restore могли
     * прервать паузу).
     *
     * Это предотвращает hammering messagesGetLongPollServer каждые ~10с во время
     * 30-секундного grace period'а после switch сети. Каждый вызов занимает ~5.5с
     * (grace delay 5с + retry) и возвращает null → без паузы пользователь видит
     * 30+ секунд «зависания».
     */
    @Volatile
    private var tokenPauseUntilMs: Long = 0L

    /**
     * Fix #112: текущий in-flight HTTP call LongPoll-запроса.
     *
     * `doRequest` использует синхронный `call.execute()` — он блокирует корутину
     * до 45с (readTimeout). При возвращении приложения на передний план
     * (onResume → notifyResumed) раньше мы только evict'или connection pool,
     * но НЕ отменяли сам call → loop ждал до 45с прежде чем перефетчить
     * `messagesGetLongPollServer()` и обнаружить, что токен истёк.
     *
     * Теперь `notifyResumed()` вызывает `currentCall?.cancel()` — `execute()`
     * немедленно бросает IOException → loop ловит её → перефетчит server/key.
     */
    @Volatile
    private var currentCall: okhttp3.Call? = null

    /** Последний успешный ts — для диагностики и потенциального восстановления. */
    @Volatile
    var lastTs: Long = 0
        private set

    /**
     * P4.2: последний успешный pts (per-conversation-global позиция событий VK).
     * В отличие от [lastTs] (который меняется каждый wait), [lastPts] обновляется
     * только на реальных событиях (new message, edit, read и т.д.). Используется
     * [messagesGetLongPollHistory] для backfill'а.
     */
    @Volatile
    var lastPts: Long = 0
        private set

    /**
     * Вызвать при возвращении приложения на передний план.
     * Сбрасывает backoff, очищает connection pool от застоявшихся TCP,
     * прерывает текущий wait/delay чтобы сразу переподключиться.
     *
     * Fix #112: также отменяем in-flight LongPoll HTTP call (currentCall),
     * иначе синхронный `execute()` блокирует loop до 45с (readTimeout)
     * прежде чем loop сможет перефетчить messagesGetLongPollServer и
     * обнаружить истёкший токен.
     */
    fun notifyResumed() {
        AppLog.i(TAG, "notifyResumed: resetting backoff, evicting connections, cancelling in-flight poll")
        consecutiveErrors = 0
        interruptWait = true
        // §43 #NET-SWITCH-DELAY: сбрасываем token-pause — при resume пользователь
        // явно хочет немедленного переподключения (возврат в foreground), не ждём
        // истечения 15-секундной паузы token invalidation.
        tokenPauseUntilMs = 0L
        try { httpClient.connectionPool.evictAll() } catch (_: Exception) {}
        try { currentCall?.cancel() } catch (_: Exception) {}
    }

    /** Запуск LongPoll-цикла. Idempotent — повторный вызов ничего не делает. */
    fun start() {
        synchronized(this) {
            if (running) {
                AppLog.d(TAG, "start: already running")
                return
            }
            running = true
        }
        AppLog.i(TAG, "LongPoll started")
        job = scope.launch { loop() }
        startNetworkWatcher()
        // §43 #NET-SWITCH-DELAY: подписка на token invalidation flow.
        startTokenInvalidationWatcher()
    }

    /** Остановка. Idempotent. */
    fun stop() {
        running = false
        networkJob?.cancel()
        networkJob = null
        tokenInvalidationJob?.cancel()
        tokenInvalidationJob = null
        job?.cancel()
        job = null
        // Fix #233 (P1-6): снимаем listener с NetworkObserver, иначе он
        // накапливается при каждом start()/stop() цикле и держит ссылку на
        // мёртвый LongPollClient через захват httpClient в лямбде → memory leak.
        stopNetworkWatcher()
        AppLog.i(TAG, "LongPoll stopped")
    }

    /**
     * Подписываемся на изменения сети. При onLost — прерываем текущий poll
     * (через running=false→true hack, чтобы внутренний цикл вышел),
     * очищаем connection pool. При onAvailable — запускаем заново.
     *
     * Fix #233 (P1-6): listener сохраняем в поле lostListener чтобы можно
     * было снять в stopNetworkWatcher(). Раньше addOnNetworkLostListener
     // вызывался каждый start() без remove → leak.
     */
    @Volatile
    private var lostListener: (() -> Unit)? = null

    private fun startNetworkWatcher() {
        val observer = networkObserver ?: return
        // Fix #233 (P1-6): если предыдущий listener не снят (например, stop()
        // не вызывался) — снимаем, чтобы не плодить дубли.
        lostListener?.let { observer.removeOnNetworkLostListener(it) }
        val listener: () -> Unit = {
            AppLog.w(TAG, "Network lost — evicting pool + cancelling in-flight calls")
            try { httpClient.connectionPool.evictAll() } catch (_: Exception) {}
            try { httpClient.dispatcher.cancelAll() } catch (_: Exception) {}
        }
        lostListener = listener
        observer.addOnNetworkLostListener(listener)
        networkJob = scope.launch {
            observer.isOnlineFlow.collect { online ->
                if (!online && running) {
                    AppLog.w(TAG, "Network offline detected — pausing poll")
                    // Даём текущему doRequest() завершиться с ошибкой таймаута.
                    // Внутренний цикл увидит null/exception и выйдет на delay с backoff.
                    // После восстановления сети — продолжит.
                } else if (online && running) {
                    AppLog.i(TAG, "Network restored — resuming poll")
                    // Сбрасываем backoff и прерываем текущий wait.
                    consecutiveErrors = 0
                    interruptWait = true
                }
            }
        }
    }

    /**
     * Fix #233 (P1-6): снимаем listener с NetworkObserver — предотвращает
     * memory leak (listener держит ссылку на LongPollClient через httpClient).
     */
    private fun stopNetworkWatcher() {
        val observer = networkObserver ?: return
        lostListener?.let {
            observer.removeOnNetworkLostListener(it)
            lostListener = null
        }
    }

    /**
     * §43 #NET-SWITCH-DELAY: подписка на [tokenInvalidationTicks] flow.
     *
     * Когда VKApiClient.callInternal() получает error 5/1117 (IP mismatch после
     * switch Wi-Fi↔Mobile) и вызывает SovaApp.notifyTokenInvalidated() →
     * инкремент этого счётчика. Мы ловим инкремент и устанавливаем
     * [tokenPauseUntilMs] = now + [TOKEN_INVALIDATION_PAUSE_MS].
     *
     * Loop() в начале каждой итерации проверяет [tokenPauseUntilMs] — если
     * `now < tokenPauseUntilMs`, ждёт остаток времени (interruptible). Это
     * предотвращает hammering messagesGetLongPollServer каждые ~10с во время
     * grace period'а — каждый вызов занимает ~5.5с (grace delay 5с + retry) и
     * возвращает null → без паузы пользователь видит 30+ секунд «зависания».
     *
     * Также прерываем текущий poll (interruptWait=true) — если loop сейчас в
     * 25-секундном LongPoll wait, он выйдет и начнёт следующую итерацию, где
     * проверит tokenPauseUntilMs.
     *
     * Дополнительная защита: currentCall?.cancel() — если loop сейчас в
     * in-flight HTTP запросе к messagesGetLongPollServer, отменяем его (токен
     * всё равно невалиден, ответ будет null-or-error).
     */
    private fun startTokenInvalidationWatcher() {
        val ticks = tokenInvalidationTicks ?: return
        tokenInvalidationJob?.cancel()
        tokenInvalidationJob = scope.launch {
            var lastTick = 0
            ticks.collect { tick ->
                if (tick <= lastTick || tick == 0) return@collect
                lastTick = tick
                // §43: устанавливаем pause до которого loop() будет ждать.
                val pauseUntil = System.currentTimeMillis() + TOKEN_INVALIDATION_PAUSE_MS
                tokenPauseUntilMs = pauseUntil
                AppLog.w(TAG, "Token invalidated (tick=$tick) — pausing LongPoll for ${TOKEN_INVALIDATION_PAUSE_MS}ms " +
                    "to let AuthActivity/ensureFreshToken complete re-login (§43 #NET-SWITCH-DELAY)")
                // Прерываем текущий wait/poll — loop выйдет и проверит tokenPauseUntilMs.
                interruptWait = true
                // Отменяем in-flight HTTP call (messagesGetLongPollServer с невалидным токеном).
                try { currentCall?.cancel() } catch (_: Exception) {}
            }
        }
    }

    /** Счётчик последовательных ошибок для exponential backoff. */
    @Volatile
    private var consecutiveErrors = 0

    /** Backoff delay: 2с × 2^errors + jitter, макс 60с. */
    private fun backoffMs(): Long {
        val base = 2_000L * (1L shl min(consecutiveErrors, 5)) // 2^0..2^5
        val jitter = Random.nextLong(0, 1_000L)
        return min(base + jitter, 60_000L)
    }

    /** Interruptible delay — проверяет [interruptWait] каждые 500мс. */
    private suspend fun interruptibleDelay(ms: Long) {
        val step = 500L
        var remaining = ms
        while (remaining > 0 && running) {
            if (interruptWait) {
                interruptWait = false
                return
            }
            val d = min(step, remaining)
            delay(d)
            remaining -= d
        }
        interruptWait = false
    }

    private suspend fun loop() {
        while (running) {
            try {
                interruptWait = false
                // Если сети нет — ждём (с небольшим backoff) вместо бесполезных попыток.
                if (networkObserver?.isOffline() == true) {
                    AppLog.d(TAG, "offline, waiting...")
                    interruptibleDelay(3_000)
                    continue
                }

                // §43 #NET-SWITCH-DELAY: token invalidation pause.
                // Если недавно была инвалидация токена (error 5/1117 после switch
                // сети) — ждём остаток TOKEN_INVALIDATION_PAUSE_MS, чтобы дать
                // AuthActivity/ensureFreshToken время завершить re-login.
                // Без этой паузы loop hammer'it messagesGetLongPollServer каждые
                // ~10с во время 30-секундного grace period'а — каждый вызов
                // занимает ~5.5с (grace delay 5с + retry) и возвращает null →
                // пользователь видит 30+ секунд «зависания» после switch сети.
                //
                // Ранний выход: каждые 2с проверяем isTokenValid() — если токен
                // восстановлен (AuthActivity success), выходим из паузы немедленно.
                val pauseRemaining = tokenPauseUntilMs - System.currentTimeMillis()
                if (pauseRemaining > 0) {
                    AppLog.i(TAG, "token-invalidation pause active — waiting ${pauseRemaining}ms " +
                        "before next server-fetch (§43 #NET-SWITCH-DELAY)")
                    // Проверяем isTokenValid каждые 2с для раннего выхода.
                    // Также проверяем tokenPauseUntilMs — notifyResumed() сбрасывает
                    // его в 0 (interruptibleDelay очищает interruptWait внутри себя,
                    // поэтому проверяем tokenPauseUntilMs вместо interruptWait).
                    val checkStep = 2_000L
                    var waited = 0L
                    var tokenRestored = false
                    var interrupted = false
                    while (waited < pauseRemaining && running) {
                        // notifyResumed сбросил tokenPauseUntilMs → прерываем паузу.
                        if (tokenPauseUntilMs == 0L) {
                            interrupted = true
                            break
                        }
                        val chunk = min(checkStep, pauseRemaining - waited)
                        interruptibleDelay(chunk)
                        waited += chunk
                        // notifyResumed мог сбросить tokenPauseUntilMs во время delay.
                        if (tokenPauseUntilMs == 0L) {
                            interrupted = true
                            break
                        }
                        // Проверяем восстановление токена.
                        if (isTokenValid != null) {
                            try {
                                if (isTokenValid.invoke()) {
                                    tokenRestored = true
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (interrupted) {
                        AppLog.i(TAG, "token pause interrupted by notifyResumed (waited ${waited}ms) — resuming immediately (§43)")
                    }
                    if (tokenRestored) {
                        AppLog.i(TAG, "token restored during pause (waited ${waited}ms) — resuming poll immediately (§43)")
                        tokenPauseUntilMs = 0L
                    }
                    continue
                }

                // P4.1: читаем feature-flag lp v14 один раз на цикл переподключения.
                // Если prefs=null или msgLpV14=false — используем lp_version=3 (по умолчанию).
                val useV14 = prefs?.let { p ->
                    try { p.data.first().msgLpV14 } catch (e: Exception) { false }
                } ?: false
                // §52.5 Sprint A (P0): Modern Sync — messages.getDiff (lp_version=21).
                // Когда включён, credentials (key/ts/server_lp) берём из getDiff.
                val useModern = prefs?.let { p ->
                    try { p.data.first().msgModernSync } catch (e: Exception) { false }
                } ?: false
                val lpVersion = if (useModern) 21 else if (useV14) 14 else 3
                AppLog.lp(phase = "version-select", fields = mapOf(
                    "version" to lpVersion,
                    "useV14" to useV14,
                    "useModern" to useModern,
                    "prefsSet" to (prefs != null),
                ))

                // 1. Получаем server/key/ts: Modern Sync → messages.getDiff,
                //    иначе → messages.getLongPollServer.
                val serverFetchStart = System.nanoTime()
                var lp: VKApiClient.LongPollServer? = null
                var lpPts: Long = 0L
                var useModernServer = false
                if (useModern) {
                    // §52.5: getDiff даёт server_lp="api.vk.ru/ruim<uid>" — без pts.
                    val diff = apiClient.messagesGetDiff()
                    if (diff != null && diff.hasCredentials) {
                        lp = VKApiClient.LongPollServer(
                            server = diff.serverLp,
                            key = diff.key,
                            ts = diff.ts,
                            pts = 0L,
                        )
                        lpPts = 0L
                        useModernServer = true
                        AppLog.i(TAG, "Modern Sync: credentials from getDiff (server_lp=${diff.serverLp.take(40)}, " +
                            "server_version=${diff.serverVersion}, invalidate_all=${diff.invalidateAll}, " +
                            "counters=${diff.countersMessages}/${diff.countersUnreadUnmuted}, folders=${diff.folders.size})")
                        // §52.5: getConfig (v17) — часть триады, логируем версию конфига.
                        val cfg = apiClient.messagesGetConfig()
                        AppLog.i(TAG, "Modern Sync: messages.getConfig version=${cfg?.version ?: "null"}")
                    } else {
                        AppLog.w(TAG, "Modern Sync: getDiff failed/empty — falling back to getLongPollServer")
                    }
                }
                if (lp == null) {
                    lp = apiClient.messagesGetLongPollServer(lpVersion = lpVersion)
                    lpPts = lp?.pts ?: 0L
                }
                val serverFetchMs = (System.nanoTime() - serverFetchStart) / 1_000_000
                if (lp == null || lp.server.isBlank() || lp.key.isBlank()) {
                    // §43 #NET-SWITCH-DELAY: определяем причину null и выбираем
                    // правильную паузу.
                    //
                    // СЦЕНАРИЙ A — network switch grace period (isRecentlySwitched):
                    //   callInternal() получил err=5/1130 (IP mismatch), сделал grace
                    //   delay 5с + retry, вернул null. VK будет rejecting токен ещё
                    //   ~20-30с пока не обновит IP binding. НЕ hammer'им — ждём 10с
                    //   (interruptible), давая VK время обновиться. Раньше было 5с
                    //   delay на null → цикл ~10с × 3 = 30с «зависания».
                    //
                    // СЦЕНАРИЙ B — обычная сетевая ошибка (не switch):
                    //   callInternal() не делал grace delay, просто retry исчерпан.
                    //   Короткая пауза 1.5с и retry.
                    //
                    // tokenPauseUntilMs (от tokenInvalidation watcher) имеет приоритет —
                    // если установлен, interruptibleDelay прервётся немедленно когда
                    // watcher его сбросит.
                    val recentlySwitched = networkObserver?.isRecentlySwitched(30_000L) == true
                    val pauseMs = if (recentlySwitched) 10_000L else 1_500L
                    AppLog.lp(phase = "server-fetch", level = android.util.Log.WARN, fields = mapOf(
                        "version" to lpVersion,
                        "result" to "null-or-empty",
                        "ms" to serverFetchMs,
                        "recentlySwitched" to recentlySwitched,
                        "pauseMs" to pauseMs,
                    ))
                    interruptibleDelay(pauseMs)
                    continue
                }

                var server = lp.server
                var key = lp.key
                var ts = lp.ts
                lastTs = ts
                lastPts = lp.pts
                consecutiveErrors = 0 // Сброс при успешном получении сервера.
                AppLog.lp(phase = "server-fetch", fields = mapOf(
                    "version" to lpVersion,
                    "ts" to ts,
                    "pts" to lp.pts,
                    "mode" to if (lpVersion >= 14) 1226 else 2,
                    "ms" to serverFetchMs,
                    "server" to server.take(40),
                ))

                // P4.2: backfill пропущенных между сессиями событий.
                // Срабатывает только если:
                //  - prefs задан (конструктор получил SovaPrefs)
                //  - snapshot.msgLpBackfill == true (opt-in feature-flag)
                //  - prefs.lpLastPts > 0 (есть что восстанавливать)
                //  - prefs.lpLastPts < lp.pts (пропущены события)
                // Восстановленные события эмитятся через тот же [handleEvent],
                // что и обычные LP events → UI обновляется идентично.
                try {
                    performBackfillIfNeeded(lp)
                } catch (e: Exception) {
                    AppLog.backfill(stage = "error", currentPts = lp.pts, error = e)
                }

                // 2. Polling loop — держим те же credentials пока не выпадет failed.
                while (running) {
                    // Прерываем poll если сеть пропала.
                    if (networkObserver?.isOffline() == true) {
                        AppLog.d(TAG, "network lost mid-poll, breaking inner loop")
                        break
                    }

                    val url = buildPollUrl(server, key, ts, lpVersion)
                    val pollStart = System.nanoTime()
                    val resp = try {
                        doRequest(url, usePost = useModernServer)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // §43 #NET-SWITCH-DELAY: определяем, был ли Canceled
                        // (notifyResumed/currentCall.cancel — значит сеть
                        // переключилась или app вернулось в foreground). В этом
                        // случае НЕ retry с тем же key — он скорее всего устарел
                        // (VK инвалидирует key при смене IP). Break'аем inner loop
                        // → outer loop перефетчит messagesGetLongPollServer.
                        val isCanceled = e is java.io.IOException &&
                            (e.message?.contains("Canceled", ignoreCase = true) == true ||
                                e.javaClass.simpleName == "Canceled")
                        AppLog.lp(phase = "poll-error", level = android.util.Log.WARN, fields = mapOf(
                            "version" to lpVersion,
                            "ts" to ts,
                            "error" to (e.message ?: e.javaClass.simpleName),
                            "consecutiveErrors" to (consecutiveErrors + 1),
                            "canceled" to isCanceled,
                        ), throwable = e)
                        consecutiveErrors++
                        if (isCanceled) {
                            // §43: Canceled = notifyResumed отменил poll (network
                            // switch / app resume). Старый key скорее всего устарел
                            // → НЕ retry с ним. Break inner loop → outer loop
                            // перефетчит server/key. ВАЖНО: НЕ делаем backoff здесь —
                            // notifyResumed уже сбросил consecutiveErrors=0.
                            AppLog.i(TAG, "poll canceled by notifyResumed — breaking inner loop to re-fetch server (§43 #NET-SWITCH-DELAY)")
                            break
                        }
                        interruptibleDelay(backoffMs())
                        null
                    }

                    if (resp == null) {
                        // §43 #NET-SWITCH-DELAY: ранее здесь был ВТОРОЙ backoffMs()
                        // сразу после первого в catch — двойная задержка (4-8с)
                        // на каждую сетевую ошибку. catch уже сделал backoff, так
                        // что здесь используем короткий delay (500мс) — просто
                        // чтобы уступить CPU перед retry.
                        interruptibleDelay(500)
                        continue
                    }

                    // Успешный ответ — сбрасываем backoff.
                    consecutiveErrors = 0
                    val pollMs = (System.nanoTime() - pollStart) / 1_000_000

                    val failed = resp.getAsJsonPrimitive("failed")?.asInt ?: 0
                    when (failed) {
                        0 -> {
                            val newTs = resp.getAsJsonPrimitive("ts")?.asLong
                            if (newTs != null) {
                                ts = newTs
                                lastTs = ts
                            }
                            // P4.2: обновляем pts, если VK вернул его в ответе.
                            // VK LP v3 не всегда возвращает pts в каждом ответе,
                            // но если вернул — это актуальная позиция событий.
                            val newPts = resp.getAsJsonPrimitive("pts")?.asLong
                            if (newPts != null && newPts > 0) lastPts = newPts
                            val updates = resp.getAsJsonArray("updates")
                            val eventsCount = updates?.size() ?: 0
                            AppLog.lp(phase = "poll-response", level = if (eventsCount > 0) android.util.Log.INFO else android.util.Log.DEBUG, fields = mapOf(
                                "version" to lpVersion,
                                "ts" to ts,
                                "pts" to lastPts,
                                "events" to eventsCount,
                                "ms" to pollMs,
                                "failed" to 0,
                            ))
                            if (updates != null) {
                                for (i in 0 until updates.size()) {
                                    val ev = updates[i]
                                    if (ev.isJsonArray) {
                                        handleEvent(ev.asJsonArray)
                                    }
                                }
                                // P4.2: после обработки events — персистим ts/pts в prefs,
                                // чтобы следующий старт мог сделать backfill.
                                persistLpState()
                            }
                        }
                        1 -> {
                            val newTs = resp.getAsJsonPrimitive("ts")?.asLong
                            if (newTs != null) {
                                ts = newTs
                                AppLog.lp(phase = "failed", level = android.util.Log.WARN, fields = mapOf(
                                    "failedCode" to 1,
                                    "reason" to "history-outdated",
                                    "oldTs" to lastTs,
                                    "newTs" to ts,
                                    "ms" to pollMs,
                                ))
                                _events.emit(LongPollEvent.Reset)
                                // P4.2: на failed=1 history outdated — пытаемся восстановить
                                // пропущенные события через messages.getLongPollHistory
                                // (если feature-flag включён и у нас есть сохранённый pts).
                                // Это страхует от потери сообщений при кратковременном
                                // разрыве LongPoll (более 25с без ответа).
                                try {
                                    performBackfillOnFailed1(ts)
                                } catch (e: Exception) {
                                    AppLog.backfill(stage = "failed1-error", error = e)
                                }
                            } else {
                                AppLog.lp(phase = "failed", level = android.util.Log.WARN, fields = mapOf(
                                    "failedCode" to 1,
                                    "reason" to "no-new-ts",
                                    "action" to "break",
                                ))
                                break
                            }
                        }
                        2, 3, 4 -> {
                            AppLog.lp(phase = "failed", level = android.util.Log.WARN, fields = mapOf(
                                "failedCode" to failed,
                                "reason" to when (failed) {
                                    2 -> "key-outdated"
                                    3 -> "ts-outdated"
                                    4 -> "version-outdated"
                                    else -> "unknown"
                                },
                                "version" to lpVersion,
                                "action" to "re-fetch-server",
                            ))
                            break
                        }
                        else -> {
                            AppLog.lp(phase = "failed", level = android.util.Log.WARN, fields = mapOf(
                                "failedCode" to failed,
                                "reason" to "unknown",
                                "body" to resp.toString().take(200),
                            ))
                            interruptibleDelay(2000)
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                AppLog.i(TAG, "loop cancelled")
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "loop error", e)
                consecutiveErrors++
                interruptibleDelay(backoffMs())
            }
        }
    }

    private fun buildPollUrl(server: String, key: String, ts: Long, lpVersion: Int = 3): String {
        val base = if (server.startsWith("http")) server else "https://$server"
        // P4.1: mode=2 для v3 (базовый), mode=1226 для v14 (расширенный:
        // attachments + pts + message_id + peer_id + platform). version в URL
        // должен совпадать с lp_version в messagesGetLongPollServer — иначе failed=4.
        val mode = if (lpVersion >= 14) 1226 else 2
        return "$base?act=a_check&key=${urlEncode(key)}&ts=$ts&wait=25&mode=$mode&version=$lpVersion"
    }

    // ─── P4.2: backfill implementation ────────────────────────────────────────

    /**
     * P4.2: При старте цикла — восстанавливает пропущенные между сессиями события
     * через `messages.getLongPollHistory(pts, ts)`.
     *
     * Срабатывает только если:
     *  - [prefs] задан (конструктор получил SovaPrefs)
     *  - snapshot.msgLpBackfill == true (opt-in feature-flag)
     *  - prefs.lpLastPts > 0 (есть сохранённое состояние)
     *  - prefs.lpLastPts < lp.pts (между сессиями появились новые события)
     *
     * Восстановленные события эмитятся через [handleEvent] — идентично обычным
     * LP events. После backfill обновляем prefs актуальным pts.
     *
     * Не бросает исключения наружу (non-fatal — при ошибке просто пропускаем).
     */
    private suspend fun performBackfillIfNeeded(lp: VKApiClient.LongPollServer) {
        val p = prefs ?: return
        val snap = try {
            p.data.first()
        } catch (e: Exception) {
            AppLog.backfill(stage = "skip-prefs-error", error = e)
            return
        }
        if (!snap.msgLpBackfill) {
            AppLog.backfill(stage = "skip-no-flag")
            return
        }
        val savedPts = snap.lpLastPts
        val savedTs = snap.lpLastTs
        if (savedPts <= 0L || savedTs <= 0L) {
            AppLog.backfill(stage = "skip-no-state", savedPts = savedPts, currentPts = lp.pts)
            return
        }
        if (savedPts >= lp.pts) {
            AppLog.backfill(stage = "skip-up-to-date", savedPts = savedPts, currentPts = lp.pts)
            // Сохраним актуальный ts/pts — для следующего старта.
            persistLpState()
            return
        }
        AppLog.backfill(stage = "start", savedPts = savedPts, currentPts = lp.pts)
        val backfillStart = System.nanoTime()
        val history = apiClient.messagesGetLongPollHistory(pts = savedPts, ts = savedTs)
        val backfillMs = (System.nanoTime() - backfillStart) / 1_000_000
        if (history == null) {
            AppLog.backfill(stage = "fetch-failed", savedPts = savedPts, currentPts = lp.pts, durationMs = backfillMs)
            return
        }
        if (history.history.isEmpty()) {
            AppLog.backfill(stage = "done", savedPts = savedPts, currentPts = lp.pts,
                eventsCount = 0, messagesCount = history.messagesCount,
                conversationsCount = history.conversationsCount, durationMs = backfillMs)
        } else {
            AppLog.backfill(stage = "replay", savedPts = savedPts, currentPts = lp.pts,
                eventsCount = history.history.size, messagesCount = history.messagesCount,
                conversationsCount = history.conversationsCount, durationMs = backfillMs)
            for (ev in history.history) {
                if (!running) {
                    AppLog.backfill(stage = "replay-interrupted", eventsCount = history.history.size)
                    return
                }
                handleEvent(ev)
            }
        }
        // Обновляем актуальный pts после backfill'а — чтобы следующий цикл poll'а
        // продолжался с актуальной позиции, а следующий старт — не делал backfill повторно.
        if (history.newPts > 0) lastPts = history.newPts
        persistLpState()
        AppLog.backfill(stage = "done", savedPts = savedPts, currentPts = history.newPts,
            eventsCount = history.history.size, messagesCount = history.messagesCount,
            conversationsCount = history.conversationsCount, durationMs = backfillMs)
    }

    /**
     * P4.2: На failed=1 (history outdated) — пытаемся восстановить пропущенные
     * события через `messages.getLongPollHistory(lastPts, ts)`.
     *
     * В отличие от [performBackfillIfNeeded] (старт между сессиями), здесь
     * мы используем текущий [lastPts] (актуальный in-memory pts с последнего
     * event'а) + новый ts из ответа failed=1.
     *
     * Это страхует от потери сообщений при разрыве LongPoll более 25с (wait timeout)
     * — VK помечает соединение как устаревшее и даёт новый ts, но без backfill'а
     * пропущенные между старым ts и новым — теряются.
     */
    private suspend fun performBackfillOnFailed1(newTs: Long) {
        val p = prefs ?: return
        val snap = try {
            p.data.first()
        } catch (e: Exception) {
            AppLog.backfill(stage = "failed1-prefs-error", error = e)
            return
        }
        if (!snap.msgLpBackfill) {
            AppLog.backfill(stage = "failed1-skip-no-flag")
            return
        }
        if (lastPts <= 0L) {
            AppLog.backfill(stage = "failed1-skip-no-pts")
            return
        }
        AppLog.backfill(stage = "failed1-start", savedPts = lastPts, currentPts = null)
        val backfillStart = System.nanoTime()
        val history = apiClient.messagesGetLongPollHistory(pts = lastPts, ts = newTs)
        val backfillMs = (System.nanoTime() - backfillStart) / 1_000_000
        if (history == null) {
            AppLog.backfill(stage = "failed1-fetch-failed", savedPts = lastPts, durationMs = backfillMs)
            return
        }
        if (history.history.isNotEmpty()) {
            AppLog.backfill(stage = "failed1-replay", savedPts = lastPts,
                eventsCount = history.history.size, messagesCount = history.messagesCount,
                durationMs = backfillMs)
            for (ev in history.history) {
                if (!running) return
                handleEvent(ev)
            }
        } else {
            AppLog.backfill(stage = "failed1-no-events", savedPts = lastPts, durationMs = backfillMs)
        }
        if (history.newPts > 0) lastPts = history.newPts
        persistLpState()
        AppLog.backfill(stage = "failed1-done", savedPts = lastPts, currentPts = history.newPts,
            eventsCount = history.history.size, durationMs = backfillMs)
    }

    /**
     * P4.2: сохраняет текущие [lastTs] и [lastPts] в [SovaPrefs] — для backfill'а
     * при следующем старте. Non-blocking, non-fatal (ошибки DataStore только логируем).
     */
    private fun persistLpState() {
        val p = prefs ?: return
        val ts = lastTs
        val pts = lastPts
        if (ts <= 0L || pts <= 0L) return
        scope.launch {
            try {
                p.setLpLastTs(ts)
                p.setLpLastPts(pts)
            } catch (e: Exception) {
                AppLog.w(TAG, "persistLpState error: ${e.message}")
            }
        }
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun doRequest(url: String, usePost: Boolean = false): JsonObject? {
        // §52.5 Sprint A (P0): Modern Sync endpoint api.vk.ru/ruim<uid> требует POST
        // (VK_IMPORT_API.MD §35.3.4), legacy lp.vk.com — GET.
        val req = if (usePost) Request.Builder().url(url)
            .post(ByteArray(0).toRequestBody(null)).build()
                  else Request.Builder().url(url).get().build()
        // Fix #112: сохраняем Call в currentCall чтобы notifyResumed() мог его
        // отменить. Очищаем в finally — если call завершился сам (ответ/таймаут),
        // currentCall=null и notifyResumed не отменит уже мёртвый call.
        val call = httpClient.newCall(req)
        currentCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.w(TAG, "HTTP ${resp.code} on poll")
                    return null
                }
                val body = resp.body?.string() ?: return null
                return try {
                    val parsed = JsonParser.parseString(body)
                    if (parsed.isJsonObject) parsed.asJsonObject else {
                        AppLog.w(TAG, "non-object response: $body")
                        null
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "parse error: ${e.message}")
                    null
                }
            }
        } finally {
            currentCall = null
        }
    }

    private suspend fun handleEvent(ev: JsonArray) {
        try {
            if (ev.size() == 0) return
            val type = ev.intAt(0) ?: return
            when (type) {
                // Fix #148: коды 1/2/3 — изменение флагов сообщения.
                //   1: заменить флаги  [1, msgId, flags, peerId]
                //   2: установить флаги [2, msgId, mask, peerId]
                //   3: сбросить флаги   [3, msgId, mask, peerId]
                // Флаг 1 = UNREAD. Событие [3, msgId, 1, peerId] = сообщение прочитано.
                // Раньше эти коды попадали в ветку `else` → "unknown event type=3"
                // → UI не узнавал о прочтении в реальном времени, счётчик непрочитанных
                // обновлялся только при ручном refresh. Теперь эмитим DialogUpdate +
                // UnreadCountersChanged чтобы список и счётчик обновились.
                1, 2, 3 -> {
                    val peerId = ev.longAt(3) ?: return
                    _events.emit(LongPollEvent.DialogUpdate(peerId))
                    _events.emit(LongPollEvent.UnreadCountersChanged)
                }
                4 -> handleNewMessage(ev)
                5 -> {
                    val msgId = ev.longAt(1) ?: return
                    val peerId = ev.longAt(3) ?: return
                    _events.emit(LongPollEvent.EditMessage(peerId, msgId))
                }
                6 -> {
                    val peerId = ev.longAt(1) ?: return
                    val upTo = ev.longAt(2) ?: return
                    _events.emit(LongPollEvent.ReadInbox(peerId, upTo))
                }
                7 -> {
                    val peerId = ev.longAt(1) ?: return
                    val upTo = ev.longAt(2) ?: return
                    _events.emit(LongPollEvent.ReadOutbox(peerId, upTo))
                }
                8 -> {
                    val userId = ev.longAt(1) ?: return
                    _events.emit(LongPollEvent.UserOnline(userId))
                }
                9 -> {
                    val userId = ev.longAt(1) ?: return
                    _events.emit(LongPollEvent.UserOffline(userId))
                }
                12, 13 -> {
                    val peerId = ev.longAt(1) ?: return
                    _events.emit(LongPollEvent.DialogUpdate(peerId))
                }
                61 -> {
                    // P0.1: DM typing — peerId = userId (DM peer is the user themselves).
                    val userId = ev.longAt(1) ?: return
                    val flags = ev.longAt(2) ?: 0L
                    _events.emit(LongPollEvent.Typing(userId, flags, isChat = false, peerId = userId))
                }
                62 -> {
                    // P0.1: chat typing — peerId = chatId + 2_000_000_000 (VK chat peer namespace).
                    val userId = ev.longAt(1) ?: return
                    val chatId = ev.longAt(2) ?: 0L
                    val peerId = chatId + 2_000_000_000L
                    _events.emit(LongPollEvent.Typing(userId, chatId, isChat = true, peerId = peerId))
                }
                80 -> {
                    _events.emit(LongPollEvent.UnreadCountersChanged)
                }
                51, 52 -> {
                    _events.emit(LongPollEvent.UnreadCountersChanged)
                }
                114 -> {
                    // §42 #PUSH-NOTIFICATIONS: code 114 = $new_unread_count —
                    // VK сообщает что изменилось количество непрочитанных
                    // УВЕДОМЛЕНИЙ (не сообщений! — лайки/комментарии/подписки/
                    // упоминания/подарки/репосты/ответы/записи на стене).
                    //
                    // LongPoll отдаёт только счётчик, не сами уведомления.
                    // SovaApp подписывается на NotificationsCountChanged →
                    // вызывает NotificationsPoller.triggerImmediatePoll() →
                    // notifications.getRedesign → diff → system notifications.
                    //
                    // Формат ev: [114, {count: N, ...}] (объект с деталями) или
                    // [114, N] (просто число). count — только для лога, парсинг
                    // терпим к неудаче (null → -1).
                    val rawCount = ev.intAt(1) ?: -1
                    AppLog.lp(phase = "event-notif-count", level = android.util.Log.DEBUG, fields = mapOf(
                        "code" to 114,
                        "rawCount" to rawCount,
                        "rawEv" to ev.toString().take(120),
                    ))
                    _events.emit(LongPollEvent.NotificationsCountChanged)
                }
                115 -> {
                    // #CALLS: входящий звонок. ev=[115, payload] — payload =
                    // строка conversation-params (WebRTC). Формат подтверждён из
                    // m.vk.ru бандла: case 115: return {type:INCOMING_CALL, payload:e}
                    val payload = ev.stringAt(1)
                    AppLog.i(TAG, "INCOMING_CALL (LP 115): payload.len=${payload?.length ?: 0}")
                    _events.emit(LongPollEvent.IncomingCall(payload))
                }
                else -> {
                    AppLog.d(TAG, "unknown event type=$type ev=$ev")
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "handleEvent error: ${e.message} ev=$ev")
        }
    }

    private suspend fun handleNewMessage(ev: JsonArray) {
        val msgId = ev.longAt(1) ?: return
        val flags = ev.intAt(2) ?: 0
        val peerId = ev.longAt(3) ?: return
        val ts = ev.longAt(4) ?: 0L
        val text = ev.stringAt(5) ?: ""

        // Fix #138 (URGENT 2026-08-03): пользователь жалуется на «непонятные
        // уведомления» — в шторке показывает текст = число (например
        // "1785781370") вместо реального текста сообщения. Лог показал:
        //
        //   msgId=295315 peerId=295315 flags=2629633 ts=2000000071 textPreview=1785781370
        //
        // АНАЛИЗ:
        //  - msgId == peerId (295315 == 295315) — НЕВОЗМОЖНО для настоящего
        //    сообщения VK (msg_id это глобальный счётчик, peer_id это
        //    userId/dialogId, они не могут совпадать).
        //  - text = "1785781370" — это Unix timestamp (10 цифр). Такое
        //    невозможно как реальный текст от 7+ разных людей одновременно.
        //  - Для того же msgId=295316 приходит ДВА event'а: один с
        //    peerId=295316 (=msgId) и text="timestamp", второй с
        //    peerId=2000000070 (реальный чат) и text="" (пустой).
        //
        // ВЫВОД: VK LP v14 с mode=1226 отдаёт КАКОЙ-ТО МЕТА-EVENT (вероятно
        // echo/confirmation для cross-device sync или msg-status update),
        // где ev[1] и ev[3] содержат одинаковые значения (msgId=peerId) и
        // ev[5] = строковый timestamp (random_id или msg_date). Реальный
        // текст сообщения находится в ДРУГОМ event'е (с реальным peer_id
        // чата и пустым text).
        //
        // HOT-FIX: пропускаем event'ы где msgId == peerId как echo-дубликаты.
        // Это спасает от показа «1785781370» в уведомлении. Реальный event с
        // этим же msgId (но другим peerId) придёт следом и покажет нормальное
        // уведомление (с text="Вложение"/"Стикер"/"Голосовое" если text пустой,
        // или с реальным текстом если есть).
        //
        // FIX #138b: также добавляем RAW-лог ev.toString() (первые 300 символов)
        // для будущей диагностики — нужно понять реальный формат VK LP v14.
        if (msgId == peerId && msgId > 0) {
            AppLog.lp(phase = "event-new-message-SKIPPED-echo", level = android.util.Log.WARN, fields = mapOf(
                "msgId" to msgId,
                "peerId" to peerId,
                "flags" to flags,
                "ts" to ts,
                "textLen" to text.length,
                "textPreview" to text.take(40).replace("\n", " "),
                "rawEv" to ev.toString().take(300),
            ))
            return  // пропускаем echo/meta-event
        }

        // Fix #134 (2026-XX): РАНЬШЕ парсили ТОЛЬКО базовые поля [4, msg_id, flags,
        // peer_id, ts, text] — игнорируя индекс 6 (extra JSON с attachments,
        // fwd_messages, reply_message, action) и индекс 8 (conversation_message_id).
        //
        // Это приводило к ДВУМ видимым пользователю багам:
        //  1. «В пушах содержимое не отображается» — для сообщений со стикером/
        //     фото/голосовым LongPoll отдаёт пустой text, но в extra JSON на
        //     индексе 6 лежит attach1_type="sticker"/"photo"/"audio_message".
        //     Без парсинга extra мы это не знали → пуш показывал «Вложение».
        //  2. «Отсутствует ответ в чатах» — LongPoll НЕ парсил cmid (индекс 8).
        //     Когда пользователь в чате нажимал «Ответить» на свежее входящее
        //     сообщение (которое ещё не было re-fetch'ено через
        //     messagesGetHistory) — у него не было cmid → toast «Нельзя
        //     ответить на это сообщение» → пользователь думал, что reply не
        //     работает. Теперь cmid приходит сразу из LongPoll event.
        //
        // Формат VK LongPoll (v3/v14) для code 4 (new message):
        //   [4, msg_id, flags, peer_id, ts, text, extra_json, random_id, cmid,
        //    attach_type?, attach_id?, ...]
        // где extra_json — объект с ключами:
        //   - from_id (для чатов: кто отправил)
        //   - attach1_type, attach1 (тип и данные первого вложения)
        //   - fwd_messages (массив пересланных сообщений)
        //   - reply_message (объект сообщения-ответа)
        //   - action, action_text (для сервисных сообщений: chat_create и т.д.)
        //   - source (для сервисных action-сообщений)
        val extra = ev.jsonObjectAt(6)
        val randomId = ev.longAt(7)
        val cmid = ev.longAt(8)
        // Fallback для старого формата LP v3 (без extra JSON): тип вложения
        // может быть на индексе 9 как строка ("photo", "video", ...).
        val attachTypeFallback = ev.stringAt(9)

        // Извлекаем поля из extra JSON.
        val fromId = extra?.get("from_id")?.takeIf { !it.isJsonNull }?.let {
            try { it.asLong } catch (_: Exception) { null }
        }
        val action = extra?.get("action")?.takeIf { !it.isJsonNull }?.let {
            try { it.asString } catch (_: Exception) { null }
        }
        val actionText = extra?.get("action_text")?.takeIf { !it.isJsonNull }?.let {
            try { it.asString } catch (_: Exception) { null }
        }
        val fwdCount = extra?.getAsJsonArray("fwd_messages")?.size() ?: 0
        // Для reply_message берём текст (или признак что reply есть) —
        // для пуша этого достаточно; полный объект VK отдаст при re-fetch.
        val replyMessagePreview: String? = extra?.getAsJsonObject("reply_message")?.let { rm ->
            val rText = rm.get("text")?.takeIf { !it.isJsonNull }?.let {
                try { it.asString } catch (_: Exception) { null }
            } ?: ""
            if (rText.isNotBlank()) rText.take(60) else "<вложение>"
        }
        // attach_type из extra JSON (ключ "attach1_type" для первого вложения).
        val attachTypeExtra = extra?.get("attach1_type")?.takeIf { !it.isJsonNull }?.let {
            try { it.asString } catch (_: Exception) { null }
        }
        val attachType = attachTypeExtra ?: attachTypeFallback

        // Fix #139b (URGENT 2026-08-03): проверка служебных флагов сообщения.
        // VK LongPoll flags для code 4 (new message):
        //   bit 0 (1)     = UNREAD
        //   bit 1 (2)     = OUTBOX
        //   bit 2 (4)     = REPLIED
        //   bit 3 (8)     = IMPORTANT
        //   bit 4 (16)    = CHAT (peerId >= 2e9)
        //   bit 5 (32)    = FRIENDS_REQUEST
        //   bit 6 (64)    = MARKED_AS_SPAM
        //   bit 7 (128)   = DELETED
        //   bit 8 (256)   = FIXED (pinned)
        //   bit 9 (512)   = MEDIA
        //   bit 10 (1024) = HIDDEN (скрытое сообщение — не должно показываться)
        //   bit 11 (2048) = DELETE_FOR_ALL
        //   bit 12 (4096) = NOT_DELIVERED
        //   bit 13 (8192) = SILENT (без уведомления — например, бот)
        //
        // Если сообщение DELETED / SPAM / HIDDEN / NOT_DELIVERED / SILENT —
        // НЕ эмитим NewMessage. Эти events не должны вызывать пуш и не должны
        // добавляться в чат (они либо удалены, либо скрыты, либо служебные).
        // Раньше такие events проходили и порождали «пустые» пуши.
        val FLAG_SPAM = 64
        val FLAG_DELETED = 128
        val FLAG_HIDDEN = 1024
        val FLAG_NOT_DELIVERED = 4096
        val FLAG_SILENT = 8192
        if (flags and (FLAG_SPAM or FLAG_DELETED or FLAG_HIDDEN or FLAG_NOT_DELIVERED) != 0) {
            AppLog.lp(phase = "event-new-message-SKIPPED-flags", level = android.util.Log.WARN, fields = mapOf(
                "msgId" to msgId,
                "peerId" to peerId,
                "flags" to flags,
                "spam" to (flags and FLAG_SPAM != 0),
                "deleted" to (flags and FLAG_DELETED != 0),
                "hidden" to (flags and FLAG_HIDDEN != 0),
                "notDelivered" to (flags and FLAG_NOT_DELIVERED != 0),
            ))
            return  // служебное/удалённое сообщение — не пушим
        }

        // Fix #139 (URGENT 2026-08-03): строгий фильтр meta/echo-events.
        //
        // ПРОБЛЕМА: после §44.E #MSG-REQUESTS (загрузка папки «Запросы») и
        // Fix #134 (парсинг extra JSON) пользователь стал получать «множественные
        // пустые пуш уведомления». Анализ показал: VK LP v14 для ОДНОГО реального
        // сообщения часто шлёт НЕСКОЛЬКО code=4 events:
        //   - event #1: msgId==peerId, text==timestamp (echo) — ловится Fix #138
        //   - event #2: msgId!=peerId, text=="" (пустой), extra==null, cmid==null
        //     — это META-event (status update / cross-device sync / msg_request
        //     delivery confirmation). Раньше проходил фильтр Fix #138 (msgId!=peerId)
        //     и порождал пуш с fallback-текстом «Вложение» или «Новое сообщение».
        //
        // ПРИЗНАК meta/echo-event (ВСЕ одновременно):
        //   1. text пустой (нет текста сообщения)
        //   2. extra == null (нет JSON-объекта на индексе 6)
        //   3. attachType == null (нет вложения)
        //   4. fwdCount == 0 (нет пересланных)
        //   5. replyMessagePreview == null (нет ответа)
        //   6. action == null (не сервисное сообщение)
        //   7. cmid == null (нет conversation_message_id)
        //
        // Реальное сообщение ВСЕГДА имеет хотя бы одно из:
        //   - непустой text
        //   - extra JSON (с from_id для чатов, или с attach1_type)
        //   - cmid (если LP mode включает bit 1024)
        //   - action (для сервисных: chat_create и т.д.)
        //
        // Если ВСЕ 8 признаков пусты → это 100% meta/echo, пропускаем.
        // Это убирает «пустые» пуши, не трогая реальные сообщения.
        // Fix #139-P0.5 (2026-08-03): добавлен randomId==null в условие.
        // Реальное сообщение почти всегда имеет random_id (VK генерирует его
        // на сервере даже если клиент не передал). Meta/echo-events его не
        // имеют. Это ужесточает фильтр — меньше шанс случайно пропустить
        // реальное сообщение из-за того что extra==null (на некоторых VK
        // LP конфигурациях extra JSON может не приходить).
        val isMetaEchoEvent = text.isBlank()
            && extra == null
            && attachType == null
            && fwdCount == 0
            && replyMessagePreview == null
            && action == null
            && cmid == null
            && randomId == null
        if (isMetaEchoEvent) {
            AppLog.lp(phase = "event-new-message-SKIPPED-meta", level = android.util.Log.WARN, fields = mapOf(
                "msgId" to msgId,
                "peerId" to peerId,
                "flags" to flags,
                "ts" to ts,
                "textLen" to text.length,
                "rawEv" to ev.toString().take(300),
            ))
            return  // meta/echo-event без реального контента — не пушим
        }

        // Fix #139d: SILENT flag (bit 13) — сообщение без уведомления (например,
        // от бота или @silent). Пока только логируем для будущего использования
        // (нужно решить: показывать тихий пуш или вообще не показывать).
        val isSilent = flags and FLAG_SILENT != 0

        // P4.2: детальное логирование нового сообщения для отладки backfill'а
        // и realtime-доставки. text обрезаем до 40 символов (privacy + log size).
        // Fix #134: добавлены поля cmid/attachType/fwdCount/hasReply/action —
        // чтобы по логам было видно, что реально приходит из LongPoll.
        // Fix #138b: добавлен rawEv (первые 300 символов) для диагностики
        // формата VK LP v14 — пока не уверены в порядке полей.
        // Fix #139d: добавлен флаг silent для диагностики @silent-сообщений.
        AppLog.lp(phase = "event-new-message", level = android.util.Log.DEBUG, fields = mapOf(
            "msgId" to msgId,
            "peerId" to peerId,
            "flags" to flags,
            "ts" to ts,
            "out" to (flags and 2 != 0),  // бит 1 = OUTBOX (исходящее)
            "unread" to (flags and 1 != 0),  // бит 0 = UNREAD
            "silent" to isSilent,  // Fix #139d: бит 13 = SILENT
            "textLen" to text.length,
            "textPreview" to text.take(40).replace("\n", " "),
            "cmid" to (cmid ?: -1L),
            "randomId" to (randomId ?: -1L),
            "attachType" to (attachType ?: ""),
            "fwdCount" to fwdCount,
            "hasReply" to (replyMessagePreview != null),
            "action" to (action ?: ""),
            "fromId" to (fromId ?: -1L),
            "rawEv" to ev.toString().take(300),
        ))
        _events.emit(
            LongPollEvent.NewMessage(
                peerId = peerId,
                messageId = msgId,
                flags = flags,
                text = text,
                ts = ts,
                conversationMessageId = cmid,
                randomId = randomId,
                fromId = fromId,
                attachType = attachType,
                replyMessagePreview = replyMessagePreview,
                fwdCount = fwdCount,
                action = action,
                actionText = actionText,
            )
        )
    }

    companion object {
        private const val TAG = "LongPollClient"

        /**
         * §43 #NET-SWITCH-DELAY: максимальная пауза loop() после token invalidation.
         *
         * Когда VKApiClient получает error 5/1117 (IP mismatch после switch
         * Wi-Fi↔Mobile) и вызывает SovaApp.notifyTokenInvalidated() → LongPollClient
         * делает паузу до этого времени, чтобы дать AuthActivity/ensureFreshToken
         * завершить re-login. Без паузы loop hammer'ит messagesGetLongPollServer
         * каждые ~10с во время 30-секундного grace period'а — каждый вызов
         * занимает ~5.5с (grace delay 5с + retry) и возвращает null → 30+ секунд
         * «зависания» после switch сети.
         *
         * 30с — это MAX. Фактическая пауза обычно короче благодаря раннему выходу:
         *  - isTokenValid() проверяется каждые 2с — если AuthActivity silent re-login
         *    завершился успешно (2-5с), пауза прерывается немедленно.
         *  - notifyResumed() (MainActivity.onResume после AuthActivity finish)
         *    сбрасывает tokenPauseUntilMs=0 → interruptibleDelay прерывается.
         *
         * 30с покрывает worst case: AuthActivity silent mode WebView (10-15с) +
         * VK IP binding update (5-15с) + возможный fallback на full re-login (20с).
         */
        // §44 #NET-SWITCH-DELAY (2026-08-03): снижено с 30_000ms до 4_000ms.
        // Лог 2026-08-03 показал: silentRefreshViaRemixsid падал с 'wrong origin'
        // (31 раз) → 22 инвалидации токена → 22×30s пауз = ~11 минут мёртвого
        // LongPoll за 40 минут. После фикса §44 #SILENT-REFRESH-ORIGIN-MULTI
        // silent refresh работает (~200мс), и isTokenValid() проверяется каждые
        // 2с → пауза обычно выходит за 2-4с. 4_000ms max — достаточно для
        // крайнего случая (silent refresh + retry). Если за 4с токен не
        // восстановился, LongPoll возобновит poll и получит err=5 снова →
        // ещё одна 4с пауза (мягкий цикл, не 30s блок).
        private const val TOKEN_INVALIDATION_PAUSE_MS = 4_000L
    }
}

// ─── Gson JsonArray safe accessors ───────────────────────────────────────────

private fun JsonArray.longAt(index: Int): Long? {
    if (index < 0 || index >= size()) return null
    val el = get(index)
    if (el == null || el.isJsonNull) return null
    return try { el.asLong } catch (e: Exception) { null }
}

private fun JsonArray.intAt(index: Int): Int? {
    if (index < 0 || index >= size()) return null
    val el = get(index)
    if (el == null || el.isJsonNull) return null
    return try { el.asInt } catch (e: Exception) { null }
}

private fun JsonArray.stringAt(index: Int): String? {
    if (index < 0 || index >= size()) return null
    val el = get(index)
    if (el == null || el.isJsonNull) return null
    return try { el.asString } catch (e: Exception) { null }
}

// Fix #134: safe accessor для JsonObject (extra JSON в LongPoll new message).
private fun JsonArray.jsonObjectAt(index: Int): JsonObject? {
    if (index < 0 || index >= size()) return null
    val el = get(index)
    if (el == null || el.isJsonNull) return null
    return try { el.asJsonObject } catch (e: Exception) { null }
}

/**
 * Real-time события VK LongPoll (version=3, mode=2).
 */
sealed class LongPollEvent {
    data class NewMessage(
        val peerId: Long,
        val messageId: Long,
        val flags: Int,
        val text: String,
        val ts: Long,
        // Fix #134: расширенные поля, парсимые из LongPoll extra JSON (индекс 6)
        // и индексов 7/8 (random_id, conversation_message_id). Раньше LongPoll
        // отдавал только базовые поля — из-за этого пуш показывал «Вложение»
        // для стикеров/фото/голосовых (text был пустой, а тип вложения лежал
        // в extra JSON, который не парсился), и нельзя было «Ответить» на
        // свежее входящее сообщение (cmid не был известен до re-fetch).
        val conversationMessageId: Long? = null,
        val randomId: Long? = null,
        /** Для чатов: ID отправителя (from_id). Для 1-1 = peerId. */
        val fromId: Long? = null,
        /** Тип первого вложения: "sticker", "photo", "video", "audio",
         *  "audio_message", "doc", "wall", "gift", "link", "poll", "story",
         *  "market", "call". null = нет вложений (только текст). */
        val attachType: String? = null,
        /** Превью текста сообщения-ответа (для пуша). null = reply нет. */
        val replyMessagePreview: String? = null,
        /** Количество пересланных сообщений. 0 = нет forward. */
        val fwdCount: Int = 0,
        /** VK action type для сервисных сообщений ("chat_create", "chat_kick_user",
         *  "chat_pin_message" и т.д.). null = обычное сообщение. */
        val action: String? = null,
        val actionText: String? = null,
    ) : LongPollEvent()

    data class EditMessage(val peerId: Long, val messageId: Long) : LongPollEvent()
    data class ReadInbox(val peerId: Long, val upToMsgId: Long) : LongPollEvent()
    data class ReadOutbox(val peerId: Long, val upToMsgId: Long) : LongPollEvent()
    data class UserOnline(val userId: Long) : LongPollEvent()
    data class UserOffline(val userId: Long) : LongPollEvent()
    /**
     * User is typing in a chat.
     *
     * P0.1: added [peerId] field for easy UI filtering.
     * - DM (code 61): [peerId] = [userId] (DM peer is the user themselves).
     * - Chat (code 62): [peerId] = chatId + 2_000_000_000 (VK chat peer namespace).
     *
     * [flags]/[userId] for chat: [userId] = typing user, [flags] = chatId (legacy field name).
     */
    data class Typing(
        val userId: Long,
        val flags: Long,
        val isChat: Boolean,
        val peerId: Long,
    ) : LongPollEvent()
    data class DialogUpdate(val peerId: Long) : LongPollEvent()
    object UnreadCountersChanged : LongPollEvent()
    /**
     * §42 #PUSH-NOTIFICATIONS: VK LongPoll code 114 ($new_unread_count).
     * Изменилось количество непрочитанных УВЕДОМЛЕНИЙ (лайки/комментарии/
     * подписки/упоминания/подарки/репосты/ответы/записи на стене).
     *
     * SovaApp подписывается на это событие → NotificationsPoller.triggerImmediatePoll().
     */
    object NotificationsCountChanged : LongPollEvent()
    /**
     * #CALLS: VK LongPoll code 115 — входящий звонок (INCOMING_CALL).
     *
     * Формат ev: [115, payload] где payload = строка conversation-params звонка
     * (WebRTC signaling: STUN/TURN/token/endpoint). PinoK пока только уведомляет —
     * полный ответ на звонок (WebSocket signaling + vchat API) — отдельная задача.
     */
    data class IncomingCall(val payload: String?) : LongPollEvent()
    object Reset : LongPollEvent()
}