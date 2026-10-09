package re.pinok.realtime

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import re.pinok.api.VKApiClient
import re.pinok.util.AppLog

/**
 * #EVENTHUB: WebSocket-клиент мгновенной доставки событий мессенджера
 * (wss://eh.vk.ru) — быстрый канал поверх LongPoll (wait=25с).
 *
 * Протокол (эталон VK web, HAR):
 *  - URL: wss://eh.vk.ru/?v=1.002&format=json&app_id={client_id}&payload={base64}
 *    payload = base64({"api_version":"5.289","user_agent":"<UA>"})
 *  - Заголовок Sec-WebSocket-Protocol: "ehsp2, {token}" (token — eventHub.getToken).
 *  - Приём: сырые строки/бинарные кадры JSON-событий — пока не парсятся
 *    (главное — доставка), эмитятся в [events] как [EventHubEvent].
 *
 * Образец структуры — [re.pinok.realtime.CallSignalingClient] (тот же
 * connect-loop с экспоненциальным backoff 1с→30с и OkHttp WebSocketListener).
 *
 * LongPoll при этом продолжает работать как fallback: сценарии, когда у
 * EventHub нет token/соединения, не ломают доставку сообщений.
 */
class EventHubWebSocketClient(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
    private val userAgent: String,
) {

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null

    @Volatile
    private var running = false
    @Volatile
    private var wsOpen = false
    @Volatile
    private var lastWsError: String? = null

    /** Сколько ждём открытия WS, прежде чем рвать попытку (как CallSignalingClient). */
    private val CONNECT_TIMEOUT_MS = 10_000L

    private val _events = MutableSharedFlow<EventHubEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<EventHubEvent> = _events.asSharedFlow()

    /** Запустить EventHub-цикл (идемпотентно). */
    fun start() {
        if (running) return
        running = true
        AppLog.i(TAG, "start: запускаю EventHub WebSocket")
        connectJob = scope.launch { connectLoop() }
    }

    /** Остановить EventHub-цикл и закрыть соединение. */
    fun stop() {
        if (!running) return
        running = false
        connectJob?.cancel()
        connectJob = null
        val ws = webSocket
        webSocket = null
        wsOpen = false
        if (ws != null) {
            try { ws.close(1000, "client stop") } catch (_: Exception) {}
        }
        AppLog.i(TAG, "stop: EventHub WebSocket остановлен")
    }

    /** true — цикл активен (пытается поддерживать соединение). */
    fun isRunning(): Boolean = running

    /** true — WebSocket реально открыт. */
    fun isConnected(): Boolean = wsOpen

    /** Человекочитаемое состояние для диагностики. */
    fun state(): String = when {
        !running -> "выкл"
        wsOpen -> "подключён"
        lastWsError != null -> "ошибка: $lastWsError"
        else -> "подключение…"
    }

    private suspend fun connectLoop() {
        var backoff = 1_000L
        while (running) {
            try {
                // Токен получаем КАЖДЫЙ цикл — он короткоживущий; если не получен —
                // backoff и повтор (не падаем, LongPoll остаётся fallback).
                val token = apiClient.eventHubGetToken()
                if (token.isNullOrBlank()) {
                    AppLog.w(TAG, "connectLoop: нет token — retry через ${backoff}мс")
                    delay(backoff)
                    backoff = minOf(backoff * 2, 30_000L)
                    continue
                }
                if (!running) break
                val url = buildUrl(token)
                wsOpen = false
                lastWsError = null
                val req = Request.Builder()
                    .url(url)
                    .header("Sec-WebSocket-Protocol", "ehsp2, $token")
                    .build()
                webSocket = httpClient.newWebSocket(req, WsListener())
                // Ждём открытия не дольше лимита; выходим раньше при явной ошибке.
                var waited = 0L
                while (running && !wsOpen && waited < CONNECT_TIMEOUT_MS) {
                    delay(100)
                    waited += 100
                }
                if (!wsOpen) {
                    val ws = webSocket
                    if (ws != null) { try { ws.cancel() } catch (_: Exception) {} }
                    if (running) {
                        val why = lastWsError ?: "таймаут ${CONNECT_TIMEOUT_MS}мс"
                        lastWsError = why
                        AppLog.w(TAG, "connectLoop: WS не открыт ($why) — retry через ${backoff}мс")
                        delay(backoff)
                        backoff = minOf(backoff * 2, 30_000L)
                    }
                    continue
                }
                // WS открыт — держим соединение, закрытие слушает listener.
                backoff = 1_000L
                AppLog.i(TAG, "connectLoop: EventHub подключён")
                while (running && wsOpen) {
                    delay(2_000)
                }
                if (running) {
                    AppLog.w(TAG, "connectLoop: WS закрыт (${lastWsError ?: "без ошибки"}) — reconnect через ${backoff}мс")
                    delay(backoff)
                    backoff = minOf(backoff * 2, 30_000L)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "connectLoop error: ${e.message}")
                lastWsError = e.message ?: "ошибка соединения"
                if (running) {
                    delay(backoff)
                    backoff = minOf(backoff * 2, 30_000L)
                }
            }
        }
        AppLog.i(TAG, "connectLoop: цикл завершён")
    }

    private fun buildUrl(token: String): String {
        val payload = buildPayload(userAgent)
        return "wss://eh.vk.ru/?v=1.002&format=json&app_id=${re.pinok.BuildConfig.VK_WEB_CLIENT_ID}&payload=$payload"
    }

    /** payload = base64({"api_version":"5.289","user_agent":"<UA>"}), без переносов. */
    private fun buildPayload(userAgentValue: String): String {
        val json = "{\"api_version\":\"5.289\",\"user_agent\":\"$userAgentValue\"}"
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            wsOpen = true
            lastWsError = null
            AppLog.i(TAG, "onOpen: EventHub connected (code=${response.code})")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text)
        }

        /** Бинарные кадры декодируем как UTF-8 (как CallSignalingClient). */
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val text = try { bytes.utf8() } catch (_: Exception) { null }
            if (text != null) {
                handleFrame(text)
            } else {
                AppLog.w(TAG, "onMessage: бинарный кадр ${bytes.size}Б не-UTF8 — пропущен")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            wsOpen = false
            lastWsError = "closed code=$code"
            AppLog.i(TAG, "onClosed: $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            wsOpen = false
            lastWsError = t.message ?: "failure"
            AppLog.w(TAG, "onFailure: ${t.message}")
        }

        private fun handleFrame(text: String) {
            // Серверный ping-кадр — отвечаем pong (общий паттерн VK WS).
            if (text == "ping") {
                val ws = webSocket
                if (ws != null) { try { ws.send("pong") } catch (_: Exception) {} }
                AppLog.d(TAG, "ping → pong")
                return
            }
            if (text.isBlank()) return
            _events.tryEmit(EventHubEvent(text))
            AppLog.d(TAG, "event: ${text.take(160)}")
        }
    }

    private companion object {
        const val TAG = "EventHub"
    }
}

/**
 * #EVENTHUB: сырое событие EventHub. Пока содержит только payload (строку) —
 * парсинг в LongPollEvent-совместимые структуры — отдельная задача,
 * здесь главное — установить и поддержать канал доставки.
 */
data class EventHubEvent(
    val payload: String,
)