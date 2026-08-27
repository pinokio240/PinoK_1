// File: realtime/ChannelWebSocketClient.kt
package re.pinok.realtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import re.pinok.util.AppLog

/**
 * P4.3: WebSocket transport для VK каналов — STUB / RESEARCH.
 *
 * ⚠️ СТАТУС: ЗАГОТОВКА. НЕ ИНТЕГРИРОВАНО В ОСНОВНОЙ FLOW.
 *
 * ## Контекст
 * VK переводит каналы (peer.type == "channel", broadcast-сообщества) с LongPoll на
 * WebSocket transport (feature-flag в JS: `frontend.vkm_new_channels_ws_engine:1`).
 * Пока VK не форсирует отказ от LongPoll для каналов — мы оставляем LongPoll как
 * основной transport (см. [LongPollClient]). Этот класс — заготовка для будущего
 * перехода, когда VK отключит LP для каналов.
 *
 * ## Что известно о протоколе (из реверс-инжиниринга JS-бандлов)
 * Источник: `vendors~vk.8159f6ce85741948.js` (web.vk.ru frontend bundle).
 *
 * 1. **Endpoint**: `wss://api.vk.com/` (или `wss://imp.vk.com/` — FIXME: уточнить)
 * 2. **Auth**: в первом frame после handshake — JSON с `{"key": "<access_token>"}`.
 *    Альтернативно — query param `?access_token=<token>&v=5.243&device_id=...`.
 * 3. **Subscribe**: после auth — `{"action": "subscribe", "channel": "im_<peer_id>"}`.
 *    peer_id для каналов = `(<group_id> + 2_000_000_000)` (как у обычных чатов).
 * 4. **Events**: server push'ит JSON-сообщения вида:
 *    ```json
 *    { "type": "new_message", "peer_id": 2000000123, "message": { ... } }
 *    { "type": "edit_message", ... }
 *    { "type": "read_inbox", "peer_id": ..., "up_to": ... }
 *    { "type": "typing", "peer_id": ..., "user_id": ... }
 *    ```
 * 5. **Heartbeat**: каждые 25с — client шлёт `{"action": "ping"}`, server отвечает
 *    `{"action": "pong"}`. Если 3 пропусков подряд — reconnect.
 * 6. **Reconnect**: exponential backoff 1с → 2с → 4с → ... → 30с (как у LongPoll).
 *
 * ## Что НЕ известно (FIXME при активации)
 * - Точный WS endpoint (нужно сниффить трафик web.vk.com при открытии канала).
 * - Формат `message` в events — полный или укороченный (как preview_length в LP).
 * - Список всех `type` значений (new_message/edit/read/typing — предположительно).
 * - Какие `action` поддержаны (subscribe/unsubscribe/ping — предположительно).
 * - Передаются ли attachments в full или только id (нужен второй API-вызов?).
 *
 * ## Что нужно сделать при активации
 * 1. Сниффнуть WS traffic web.vk.com при открытии канала (DevTools → Network → WS).
 * 2. Зафиксировать endpoint, auth flow, event types в VK_IMPORT_API.MD §24.
 * 3. Доделать [onMessage] парсер (сейчас TODO).
 * 4. В [SovaApp] добавить опциональный старт `ChannelWebSocketClient` если
 *    `prefs.msgWsChannels == true` и пользователь открыл канал.
 * 5. В [LongPollClient.handleNewMessage] — skip events для channel peer_id если
 *    WS активен (чтобы не дублировать).
 *
 * ## Использование (когда будет активировано)
 * ```kotlin
 * val wsClient = ChannelWebSocketClient(httpClient, apiClient, prefs)
 * wsClient.start()  // handshake + auth + subscribe
 * wsClient.events.collect { event ->
 *     // LongPollEvent.NewMessage(...) ...
 * }
 * wsClient.subscribe(peerId)
 * wsClient.unsubscribe(peerId)
 * wsClient.stop()
 * ```
 *
 * ## Feature-flag
 * `SovaPrefs.msgWsChannels` (default: `false`). При `true` — [SovaApp] может
 * запускать этот client дополнительно к [LongPollClient]. В UI — тумблер в Settings.
 *
 * ## Риски
 * - VK может изменить WS protocol без notice (в отличие от REST API с versioning).
 * - WS держит connection открытым → больше battery drain чем LongPoll (но меньше latency).
 * - Auth flow может требовать `device_id` и других полей (FIXME: уточнить).
 */
class ChannelWebSocketClient(
    private val httpClient: OkHttpClient,
    @Suppress("UNUSED_PARAMETER") apiClient: re.pinok.api.VKApiClient,
) {
    private val _events = MutableSharedFlow<LongPollEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LongPollEvent> = _events.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var running = false

    /**
     * Подключиться к VK WS endpoint и начать слушать events.
     * STUB: реальный endpoint/auth flow нужно зафиксировать из сниффа трафика.
     */
    fun start() {
        if (running) return
        running = true
        AppLog.ws(stage = "stub-start")
        // TODO: реализовать при активации P4.3:
        //  1. val req = Request.Builder().url("wss://api.vk.com/?access_token=...&v=5.243").build()
        //  2. webSocket = httpClient.newWebSocket(req, WsListener())
        //  3. Отправить auth frame: {"key": "<token>"}
        //  4. Стартовать heartbeat корутину (ping каждые 25с)
    }

    /** Отключиться от WS. Idempotent. */
    fun stop() {
        running = false
        try {
            webSocket?.close(1000, "client stop")
        } catch (_: Exception) {}
        webSocket = null
        AppLog.ws(stage = "stub-stop")
    }

    /**
     * Подписаться на events канала [peerId].
     * STUB: реальный action format нужно зафиксировать.
     */
    fun subscribe(peerId: Long) {
        AppLog.ws(stage = "stub-subscribe", peerId = peerId)
        // TODO: webSocket?.send("{\"action\":\"subscribe\",\"channel\":\"im_$peerId\"}")
    }

    /**
     * Отписаться от events канала [peerId].
     * STUB: реальный action format нужно зафиксировать.
     */
    fun unsubscribe(peerId: Long) {
        AppLog.ws(stage = "stub-unsubscribe", peerId = peerId)
        // TODO: webSocket?.send("{\"action\":\"unsubscribe\",\"channel\":\"im_$peerId\"}")
    }

    /**
     * WebSocket listener — обрабатывает входящие frames.
     * STUB: парсер event types нужно доделать при активации.
     */
    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            AppLog.ws(stage = "ws-open", code = response.code)
            // TODO: отправить auth frame
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            AppLog.ws(stage = "ws-message", bytes = text.length)
            // TODO: распарсить JSON, определить type, эмитить LongPollEvent
            //  when (type) {
            //      "new_message" -> _events.tryEmit(LongPollEvent.NewMessage(...))
            //      "typing" -> _events.tryEmit(LongPollEvent.Typing(...))
            //      ...
            //  }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            AppLog.ws(stage = "ws-closed", code = code)
            if (running) {
                // TODO: reconnect с backoff
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            AppLog.ws(stage = "ws-failure", code = response?.code, error = t)
            if (running) {
                // TODO: reconnect с backoff
            }
        }
    }

    companion object {
        /**
         * Минимальный readTimeout для WS-клиента. LongPoll использует 45с для
         * wait=25, но WS держит connection постоянно — нужен ping/pong вместо timeout.
         */
        const val WS_TIMEOUT_SECONDS = 0L // 0 = без timeout (heartbeat управляет connection)
    }
}
