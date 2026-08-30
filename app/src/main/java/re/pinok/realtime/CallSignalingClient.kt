package re.pinok.realtime

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import re.pinok.media.ConversationParamsDecoder
import re.pinok.util.AppLog
import java.util.concurrent.atomic.AtomicLong

/**
 * #CALLS: WebSocket-клиент сигналинга звонков VK.
 *
 * Протокол расшифрован из calls SDK (vendors~calls-sdk):
 *  - WS URL = wssBase + query:
 *      userId, entityType(USER), deviceIdx(0), conversationId, token(wssToken)
 *      + platform, appVersion, version, device, capabilities, clientType, peerId
 *  - Исходящие команды: { command: "accept-call"|"hangup"|"transmit-data"|...,
 *                          sequence: <int>, ...params }
 *      accept-call → mediaSettings: { isAudioEnabled, isVideoEnabled, ... }
 *      hangup      → reason
 *      transmit-data → { participantId, data: { sdp | candidate } }
 *  - Входящие: JSON { command: ... } (accept-call/offer/answer/candidate/hangup)
 *
 * Используется для ответа на входящий звонок (accept/decline) и исходящих.
 */
class CallSignalingClient(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "CallSignaling"
        private const val ENTITY_TYPE_USER = "USER"
        private const val PLATFORM = "android"
        private const val APP_VERSION = "2.0.0"
        private const val PROTOCOL_VERSION = "1"
        private const val DEVICE = "HOTWAV Cyber 15"
        private const val CAPABILITIES = "0"

        /** Команды (enum or из calls SDK). */
        const val CMD_ACCEPT_CALL = "accept-call"
        const val CMD_HANGUP = "hangup"
        const val CMD_TRANSMIT_DATA = "transmit-data"
        const val CMD_CHANGE_MEDIA_SETTINGS = "change-media-settings"
        const val CMD_ADD_PARTICIPANT = "add-participant"
        const val CMD_GET_PARTICIPANTS = "get-participants"

        /** Входящие команды/события. */
        const val CMD_OFFER = "offer"
        const val CMD_ANSWER = "answer"
        const val CMD_CANDIDATE = "candidate"
        const val CMD_ACCEPTED_OUTGOING = "callAcceptedOutgoing"
        const val CMD_CALL_ERROR = "callError"
        /** #CALLS-OUTGOING: собеседник зарегистрировался в conversation.
         *  Сервер шлёт { notification:"registered-peer", participantId:<id>,
         *  participantType:"USER", peerId:{id,type} } — participantId здесь —
         *  ID участника, НА КОТОРЫЙ нужно слать offer/ICE (исходящий). */
        const val CMD_REGISTERED_PEER = "registered-peer"
        /** #CALLS-ACK-REOFFER (2026-08-29): собеседник ПРИНЯЛ звонок.
         *  Сервер шлёт { notification:"accepted-call" } вызывающему. Это ЕДИНСТВЕННЫЙ
         *  гарантированный момент, когда вызываемый готов принимать transmit-data
         *  (лог 20:31: registered-peer в 25.316, accepted-call в 32.487 — 7с между
         *  ними): если сервер ретранслирует transmit-data только «принявшим» peer'ам,
         *  offer, отправленный на registered-peer, тоже выбрасывался. */
        const val CMD_ACCEPTED_CALL = "accepted-call"
        /** Ack от сервера: { type:"response", sequence:N, response:"transmit-data"|"accept-call" }. */
        const val CMD_RESPONSE = "response"
        /** Собеседник завершил звонок / участник покинул conversation. */
        const val CMD_REMOTE_HANGUP = "remote-hangup"
        const val CMD_PARTICIPANT_LEFT = "participant-left"
    }

    /** Входящее событие сигналинга. */
    data class SignalingMessage(
        val command: String,
        val json: JsonObject,
        val participantId: String? = null,
        val sdp: String? = null,
        val sdpType: String? = null,
        val candidate: String? = null,
        val candidateSdpMid: String? = null,
        val candidateSdpMLineIndex: Int? = null,
    )

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null
    private var running = false
    private val sequence = AtomicLong(0)
    @Volatile
    private var conversationId: String = ""

    /** #CALLS-DIAG (2026-08-29): сколько ждём открытия WS, прежде чем рвать попытку. */
    private val CONNECT_TIMEOUT_MS = 10_000L

    private val _messages = MutableSharedFlow<SignalingMessage>(replay = 0, extraBufferCapacity = 64)
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    /** Подключиться к signaling. [params] — декодированные conversation params. */
    fun start(
        userId: Long,
        conversationId: String,
        params: ConversationParamsDecoder.Params,
        peerId: Long? = null,
    ) {
        if (running) return
        running = true
        this.conversationId = conversationId
        if (params.endpoint.isBlank()) {
            AppLog.w(TAG, "start: wssBase пустой — не могу подключиться")
            running = false
            return
        }
        connectJob = scope.launch { connectLoop(userId, conversationId, params, peerId) }
    }

    fun stop() {
        running = false
        connectJob?.cancel()
        connectJob = null
        try { webSocket?.close(1000, "client stop") } catch (_: Exception) {}
        webSocket = null
    }

    fun isRunning(): Boolean = running

    /** #CALLS-IN-FIX (2026-08-29): WS сигналинга открыт — команды уходят, а не отбрасываются. */
    fun isWsReady(): Boolean = running && wsOpen

    /**
     * #CALLS-DIAG (2026-08-29): человекочитаемое состояние WS для экранной
     * диагностики звонка (CallScreen показывает её при CONNECTING/FAILED/ENDED).
     */
    fun wsState(): String = when {
        !running -> "выкл"
        wsOpen -> "подключён"
        lastWsError != null -> "ошибка: $lastWsError"
        else -> "подключение…"
    }

    // ─── Команды ────────────────────────────────────────────────

    /** Принять входящий звонок (audio). @return true — команда реально ушла в WS. */
    fun acceptCall(isVideo: Boolean = false): Boolean {
        val media = JsonObject().apply {
            addProperty("isAudioEnabled", true)
            addProperty("isVideoEnabled", isVideo)
            addProperty("isScreenSharingEnabled", false)
            addProperty("isFastScreenSharingEnabled", false)
            addProperty("isAudioSharingEnabled", false)
            addProperty("isAnimojiEnabled", false)
        }
        val params = mutableMapOf<String, Any>("mediaSettings" to media)
        if (conversationId.isNotBlank()) params["conversationId"] = conversationId
        return send(CMD_ACCEPT_CALL, params)
    }

    /** Отклонить входящий звонок. @return true — команда реально ушла в WS. */
    fun declineCall(): Boolean {
        val params = mutableMapOf<String, Any>("reason" to "declined")
        if (conversationId.isNotBlank()) params["conversationId"] = conversationId
        return send(CMD_HANGUP, params)
    }

    /** Завершить звонок. @return true — команда реально ушла в WS. */
    fun hangup(reason: String = "hungup"): Boolean {
        val params = mutableMapOf<String, Any>("reason" to reason)
        if (conversationId.isNotBlank()) params["conversationId"] = conversationId
        return send(CMD_HANGUP, params)
    }

    /** Отправить SDP (answer для входящего, offer для исходящего).
     *  @return true — команда реально ушла в WS (#CALLS-ACK-REOFFER: false = потеряна,
     *  вызывающему ответ нужен ретрай — answer терять нельзя). */
    fun sendSdp(participantId: String, sdp: String, type: String): Boolean {
        val data = JsonObject().apply {
            addProperty("sdp", sdp)
            addProperty("type", type)
        }
        return send(CMD_TRANSMIT_DATA, mapOf("participantId" to participantId, "data" to data))
    }

    /** Отправить ICE candidate. @return true — команда реально ушла в WS. */
    fun sendCandidate(participantId: String, sdpMid: String?, sdpMLineIndex: Int?, candidate: String): Boolean {
        val c = JsonObject().apply {
            addProperty("candidate", candidate)
            sdpMid?.let { addProperty("sdpMid", it) }
            sdpMLineIndex?.let { addProperty("sdpMLineIndex", it) }
        }
        val data = JsonObject().apply { add("candidate", c) }
        return send(CMD_TRANSMIT_DATA, mapOf("participantId" to participantId, "data" to data))
    }

    // ─── Внутреннее ─────────────────────────────────────────────

    private suspend fun connectLoop(
        userId: Long,
        conversationId: String,
        params: ConversationParamsDecoder.Params,
        peerId: Long?,
    ) {
        var backoff = 1_000L
        while (running) {
            try {
                val url = buildUrl(userId, conversationId, params, peerId)
                AppLog.i(TAG, "connectLoop: connecting to signaling...")
                val req = Request.Builder().url(url).build()
                wsOpen = false
                wsFailed = false
                lastWsError = null
                webSocket = httpClient.newWebSocket(req, WsListener())
                // #CALLS-DIAG (2026-08-29): ждём открытия НЕ дольше CONNECT_TIMEOUT_MS
                // и выходим раньше при явной ошибке (onFailure). Раньше при неудачном
                // первом коннекте цикл «while (!isWsOpen())» крутился вечно —
                // реконнект не срабатывал вовсе, звонок молча висел «Соединение…».
                var waited = 0L
                while (running && !isWsOpen() && !wsFailed && waited < CONNECT_TIMEOUT_MS) {
                    delay(250)
                    waited += 250
                }
                if (!isWsOpen()) {
                    try { webSocket?.cancel() } catch (_: Exception) {}
                    if (running) {
                        val why = lastWsError ?: "таймаут ${CONNECT_TIMEOUT_MS}мс"
                        lastWsError = why
                        AppLog.w(TAG, "connectLoop: WS не открыт ($why) — retry через ${backoff}мс")
                        delay(backoff)
                        backoff = minOf(backoff * 2, 30_000L)
                    }
                    continue
                }
                // WS открыт — держим соединение. Если закроется — listener сбросит wsOpen.
                backoff = 1_000L
                while (running && isWsOpen()) {
                    delay(2_000)
                }
                if (running) {
                    AppLog.w(TAG, "connectLoop: WS закрыт (${lastWsError ?: "без ошибки"}), reconnect через ${backoff}ms")
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
    }

    @Volatile
    private var wsOpen = false
    /** #CALLS-DIAG: попытка провалилась (onFailure/onClosed до открытия). */
    @Volatile
    private var wsFailed = false
    /** #CALLS-DIAG: последняя причина ошибки/закрытия WS. */
    @Volatile
    private var lastWsError: String? = null
    private fun isWsOpen() = wsOpen

    private fun buildUrl(
        userId: Long,
        conversationId: String,
        params: ConversationParamsDecoder.Params,
        peerId: Long?,
    ): String {
        // wssBase из conversation params — может содержать полный WS URL или голый base.
        var base = params.endpoint
        if (base.isBlank()) base = "wss://calls.okcdn.ru"
        val query = StringBuilder()
        query.append("userId=").append(userId)
        query.append("&entityType=").append(ENTITY_TYPE_USER)
        query.append("&deviceIdx=0")
        query.append("&conversationId=").append(conversationId)
        if (params.token.isNotBlank()) query.append("&token=").append(params.token)
        // Параметры из _buildUrl calls SDK
        query.append("&platform=").append(PLATFORM)
        query.append("&appVersion=").append(APP_VERSION)
        query.append("&version=").append(PROTOCOL_VERSION)
        query.append("&device=").append(DEVICE)
        query.append("&capabilities=").append(CAPABILITIES)
        query.append("&clientType=").append(ENTITY_TYPE_USER)
        peerId?.let { query.append("&peerId=").append(it) }
        return if (base.contains("?")) base + "&" + query else base + "?" + query
    }

    /** #CALLS-ACK-REOFFER (2026-08-29): send возвращает Boolean — раньше команда при
     *  закрытом WS молча отбрасывалась (answer терялось навсегда, флаг answerSent
     *  при этом уже стоял true). Теперь вызывающий код видит неудачу и ретраит. */
    private fun send(command: String, params: Map<String, Any>): Boolean {
        val ws = webSocket
        if (ws == null || !wsOpen) {
            AppLog.w(TAG, "send: WS не открыт, команда '$command' отброшена")
            return false
        }
        try {
            val seq = sequence.incrementAndGet()
            val payload = JsonObject().apply {
                addProperty("command", command)
                addProperty("sequence", seq)
                params.forEach { (k, v) ->
                    when (v) {
                        is String -> addProperty(k, v)
                        is Int -> addProperty(k, v)
                        is Long -> addProperty(k, v)
                        is Boolean -> addProperty(k, v)
                        is JsonObject -> add(k, v)
                    }
                }
            }
            val text = payload.toString()
            val ok = ws.send(text)
            // INFO (не DEBUG): факт отправки/потери команд — главный диагностический след.
            // #CALLS-BINARY-FRAME (2026-08-30): + размер кадра — тест 13:06 показал, что
            // сервер не доставляет БОЛЬШИЕ transmit-data (offer ~4.1 КБ), доставляя мелкие
            // (кандидаты ~464 Б) из тех же батчей; размер — главный подозреваемый.
            AppLog.i(TAG, "send: command=$command seq=$seq ok=$ok size=${text.toByteArray(Charsets.UTF_8).size}Б")
            AppLog.d(TAG, "send payload: $payload")
            return ok
        } catch (e: Exception) {
            AppLog.w(TAG, "send error: ${e.message}")
            return false
        }
    }

    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            wsOpen = true
            wsFailed = false
            lastWsError = null
            AppLog.i(TAG, "onOpen: signaling connected (code=${response.code})")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleTextFrame(text)
        }

        /** #CALLS-BINARY-FRAME (2026-08-30, тест 13:06): OkHttp молча игнорирует бинарные
         *  кадры, если не переопределить этот вариант onMessage. Если сервер пересылает
         *  БОЛЬШИЕ данные (наш offer ~4.1 КБ) бинарём, а мелкие — текстом, то offer
         *  терялся БЕЗ ЕДИНОГО СЛЕДА в логе. Теперь бинарный кадр логируется и
         *  декодируется как UTF-8 в общий JSON-обработчик. */
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val text = try { bytes.utf8() } catch (_: Exception) { null }
            if (text != null) {
                AppLog.w(TAG, "⚠ BINARY кадр ${bytes.size}Б (раньше молча терялся) — обрабатываю как UTF-8")
                handleTextFrame(text)
            } else {
                AppLog.w(TAG, "⚠ BINARY кадр ${bytes.size}Б — не UTF-8, НЕ распарсить (первый раз за звонок)")
            }
        }

        private fun handleTextFrame(text: String) {
            try {
                val json = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject ?: run {
                    // #CALLS-BINARY-FRAME: не-JSON кадр раньше исчезал молча — логируем.
                    AppLog.w(TAG, "onMessage: не-JSON кадр ${text.toByteArray(Charsets.UTF_8).size}Б: ${text.take(120)}")
                    return
                }
                // #CALLS-ACK-REOFFER (2026-08-29): ack'и/ошибки сервера — {type:"response"|"error",
                // sequence:N, response:"transmit-data"|"accept-call", participantIds:[…]}. Раньше
                // молча игнорировались — успех/отказ доставки наших offer/answer/accept был невиден.
                val topType = json.get("type")?.takeIf { it.isJsonPrimitive }?.asString
                if (topType == "response" || topType == "error") {
                    AppLog.i(TAG, "SERVER_${topType.uppercase()}: $text")
                    _messages.tryEmit(SignalingMessage(topType, json))
                    return
                }
                val command = json.get("command")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: json.get("notification")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: detectCommand(json)
                val effective = when (command) {
                    "transmitted-data", "transmit-data", "data", "event" -> detectCommand(json)
                    else -> command
                }
                // #CALLS-FIX (2026-08-24): завершение звонка собеседником — сервер шлёт
                // notification "hangup" / "call-ended" / "participant-left" / "conversation-ended".
                // Маппим их в единый CMD_REMOTE_HANGUP, чтобы CallScreen перевёл экран в ENDED.
                // #CALLS-ZOMBIE (2026-08-29, скриншот 23:45): звонящий повесил трубку, а экран
                // остался в «Соединение…» — какое-то из уведомлений о завершении не сматчилось.
                // Добавлены алиасы; нераспознанные уведомления логируются на INFO (см. ниже),
                // чтобы следующий лог показал точное имя.
                val effective2 = when (effective) {
                    "hangup", "hungup", "call-ended", "conversation-ended", "closed-conversation",
                    "conversation-closed", "call-closed", "closed", "ended", "cancelled", "canceled",
                    "call-cancelled", "call-canceled", "participant-left", "participant-removed",
                    "participant-leaved", "left", "call-rejected", "rejected", "declined", "decline" -> CMD_REMOTE_HANGUP
                    else -> effective
                }
                if (effective2 == "connection") {
                    AppLog.i(TAG, "FULL_CONNECTION: $text")
                } else if (effective2 == CMD_REGISTERED_PEER) {
                    AppLog.i(TAG, "REGISTERED_PEER: $text")
                } else if (effective2 == CMD_REMOTE_HANGUP) {
                    AppLog.i(TAG, "REMOTE_HANGUP: $text")
                } else if (effective2 == CMD_ACCEPTED_CALL || effective2 == CMD_ACCEPTED_OUTGOING) {
                    AppLog.i(TAG, "ACCEPTED_CALL: $text")
                } else {
                    // #CALLS-ZOMBIE: INFO, а не DEBUG — нераспознанные уведомления (напр.
                    // незнакомое имя hangup) обязаны попадать в лог пользователя.
                    // #CALLS-BINARY-FRAME: + размер кадра — смотрите комментарий в send().
                    AppLog.i(TAG, "onMessage: command=$command eff=$effective size=${text.toByteArray(Charsets.UTF_8).size}Б body=${text.take(300)}")
                }
                if (effective2 == CMD_OFFER || effective2 == CMD_ANSWER) {
                    val sdp = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("sdp")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("sdp")?.takeIf { it.isJsonPrimitive }?.asString
                    if (sdp != null) AppLog.i(TAG, "FULL_$effective2 SDP:\n$sdp")
                }
                val msg = when (effective2) {
                    CMD_OFFER, CMD_ANSWER -> parseSdp(json, effective2)
                    CMD_CANDIDATE -> parseCandidate(json)
                    CMD_REGISTERED_PEER -> parseRegisteredPeer(json)
                    "connection" -> SignalingMessage("connection", json)
                    else -> SignalingMessage(effective2, json)
                }
                _messages.tryEmit(msg)
            } catch (e: Exception) {
                AppLog.w(TAG, "onMessage error: ${e.message}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            wsOpen = false
            wsFailed = true
            lastWsError = "закрыт сервером (code=$code)"
            AppLog.i(TAG, "onClosed: code=$code reason=$reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            wsOpen = false
            wsFailed = true
            lastWsError = t.message ?: "сбой соединения"
            AppLog.w(TAG, "onFailure: ${t.message}")
        }
    }

    /** #CALLS-ACK-REOFFER (2026-08-29): распознаём и примитивный sdp — envelope вида
     *  {data:{sdp:"v=0…", type:"offer"}} (строка, а не объект). Раньше такой offer
     *  классифицировался как «event» и молча терялся. */
    private fun detectCommand(json: JsonObject): String {
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val sdpObj = data?.get("sdp")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: json.get("sdp")?.takeIf { it.isJsonObject }?.asJsonObject
        val sdpPrim = data?.get("sdp")?.takeIf { it.isJsonPrimitive } != null ||
            json.get("sdp")?.takeIf { it.isJsonPrimitive } != null
        val cand = data?.get("candidate")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: json.get("candidate")?.takeIf { it.isJsonObject }?.asJsonObject
        val sdpType = sdpObj?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            ?: data?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            ?: json.get("type")?.takeIf { it.isJsonPrimitive }?.asString
        return when {
            (sdpObj != null || sdpPrim) && sdpType == "offer" -> CMD_OFFER
            (sdpObj != null || sdpPrim) && sdpType == "answer" -> CMD_ANSWER
            cand != null -> CMD_CANDIDATE
            json.get("notification")?.takeIf { it.isJsonPrimitive }?.asString == "connection" -> "connection"
            json.has("endpoint") -> "connection"
            else -> "event"
        }
    }

    private fun parseSdp(json: JsonObject, cmd: String): SignalingMessage {
        val peerId = json.get("peerId")?.takeIf { it.isJsonObject }?.asJsonObject
        val participantId = json.get("participantId")?.takeIf { it.isJsonPrimitive }?.asString
            ?: peerId?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val sdp = data?.get("sdp")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: json.get("sdp")?.takeIf { it.isJsonObject }?.asJsonObject
        val sdpStr = sdp?.get("sdp")?.takeIf { it.isJsonPrimitive }?.asString
            ?: data?.get("sdp")?.takeIf { it.isJsonPrimitive }?.asString
        val type = sdp?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            ?: data?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            ?: json.get("type")?.takeIf { it.isJsonPrimitive }?.asString
        return SignalingMessage(cmd, json, participantId, sdpStr, type)
    }

    private fun parseCandidate(json: JsonObject): SignalingMessage {
        val peerId = json.get("peerId")?.takeIf { it.isJsonObject }?.asJsonObject
        val participantId = json.get("participantId")?.takeIf { it.isJsonPrimitive }?.asString
            ?: peerId?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val cand = data?.get("candidate")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: json.get("candidate")?.takeIf { it.isJsonObject }?.asJsonObject
        val candidate = cand?.get("candidate")?.takeIf { it.isJsonPrimitive }?.asString
        val sdpMid = cand?.get("sdpMid")?.takeIf { it.isJsonPrimitive }?.asString
        val sdpMLineIndex = cand?.get("sdpMLineIndex")?.takeIf { it.isJsonPrimitive }?.asInt
        return SignalingMessage(
            command = CMD_CANDIDATE,
            json = json,
            participantId = participantId,
            candidate = candidate,
            candidateSdpMid = sdpMid,
            candidateSdpMLineIndex = sdpMLineIndex,
        )
    }

    /**
     * #CALLS-OUTGOING: парсинг `registered-peer` — собеседник зарегистрировался.
     * `participantId` в этом событии — ID участника (НА КОТОРОГО слать offer/ICE),
     * в отличие от `peerId` (наш WS-транспорт).
     */
    private fun parseRegisteredPeer(json: JsonObject): SignalingMessage {
        val participantId = json.get("participantId")?.takeIf { it.isJsonPrimitive }?.asString
        return SignalingMessage(
            command = CMD_REGISTERED_PEER,
            json = json,
            participantId = participantId,
        )
    }
}
