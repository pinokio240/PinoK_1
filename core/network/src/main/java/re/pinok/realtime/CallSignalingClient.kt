package re.pinok.realtime

// #CALLS-ZH2 (Task 6-a): JsonArray/JsonPrimitive/JsonNull — команды Ж6-Ж9 с массивами
// и ЯВНЫМИ JSON null (record-start/update-rooms/activate-rooms/switch-room; Ж0 §4/§5/§8).
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
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
 *      hangup      → reason — ТОЛЬКО значение enum эталона hangupType, UPPERCASE:
 *                    CANCELED|REJECTED|REMOVED|HUNGUP|MISSED|BUSY|FAILED (#CALLS-HANGUP-REASON-ENUM)
 *  - Сервер пингует текстовым кадром "ping" каждые ~5 с — отвечаем текстовым "pong"
 *    (#CALLS-WS-PONG), как эталон.
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

        // ─── #CALLS-ZH (Этап Ж, Task 5-b): имена команд in-call ядра (wire — Ж0-протокол) ───
        // Только КОНСТАНТЫ имён + ДОБАВЛЯЮЩИЕ send-методы ниже; форматы существующих
        // сообщений (accept-call/hangup/transmit-data) не изменены (РЕГРЕСС недопустим).
        /** Рука/статусы участника (Ж0 §1.1#10, 16131@306127): {participantState:{state:{…≤5симв}}} */
        const val CMD_CHANGE_PARTICIPANT_STATE = "change-participant-state"
        /** Шумодав (Ж0 §1.1#31, 16131@308860): {mediaModifiers:{denoise,denoiseAnn}} */
        const val CMD_UPDATE_MEDIA_MODIFIERS = "update-media-modifiers"
        /** Реакция (Ж0 §1.1#48, §3.1, 16131@309999): {key} — key из каталога calls.getReactions */
        const val CMD_FEEDBACK = "feedback"

        // ─── #CALLS-ZH2 (Task 6-a): команды Ж6 запись / Ж7 ASR / Ж8 зал ожидания / Ж9 залы ───
        // Все имена — wire-литералы Ж0-протокола (§1.1, смещения эталона в KDoc методов).
        /** Ж6 запись (Ж0 §1.1#16, 16131@422393 startStream): {movieId:null,name,privacy,groupId:null,roomId:null,streamMovie:false} */
        const val CMD_RECORD_START = "record-start"
        /** Ж6 запись (Ж0 §1.1#17, 16131@422494 stopStream): {roomId:null, remove?} */
        const val CMD_RECORD_STOP = "record-stop"
        /** Ж7 расшифровка (Ж0 §1.1#49, 16131@310522 startAsr): {fileName≤128, roomId?} */
        const val CMD_ASR_START = "asr-start"
        /** Ж7 расшифровка (Ж0 §1.1#50, 16131@310539 stopAsr): {} (roomId только если есть) */
        const val CMD_ASR_STOP = "asr-stop"
        /** Ж9 залы (Ж0 §1.1#43, 16131@307641): {withParticipants:bool} → response {rooms:{roomId, rooms:[Room]}} */
        const val CMD_GET_ROOMS = "get-rooms"
        /** Ж9 залы (Ж0 §8, bridge@819377/830706): создание {rooms:[{name,participantCount}],assignRandomly} / перемещение {rooms:[{id,removeParticipantIds},{id,addParticipantIds}]} */
        const val CMD_UPDATE_ROOMS = "update-rooms"
        /** Ж9 залы (Ж0 §8, bridge@830343/394008): {roomIds:[…], deactivate:bool} */
        const val CMD_ACTIVATE_ROOMS = "activate-rooms"
        /** Ж9 залы (Ж0 §8, bridge@830722): {roomIds:[…]} */
        const val CMD_REMOVE_ROOMS = "remove-rooms"
        /** Ж9 залы (Ж0 §1.1#47, 16131@307553): {toRoomId?(null=основной), participantId?} */
        const val CMD_SWITCH_ROOM = "switch-room"
        /** Ж8 зал ожидания (Ж0 §1.1#33, 16131@309933 getWaitingHall): {fromId?,count?,backward?} → {totalCount,participants} */
        const val CMD_GET_WAITING_HALL = "get-waiting-hall"
        /** Ж8 зал ожидания (Ж0 §1.1#36, 16131@309678): {participantId, demote?:bool} — впустить/вернуть в зал */
        const val CMD_PROMOTE_PARTICIPANT = "promote-participant"
        /** Ж8 отказ в зале ожидания (Ж0 §6.3/§7): remove-participant {participantId, ban:false}.
         *  Сам send-метод в Ж1 (wave-5) НЕ вошёл — добавлен в Ж8-пакете для «Отклонить» (refuse). */
        const val CMD_REMOVE_PARTICIPANT = "remove-participant"

        /**
         * #CALLS-ZH2 ФИКС волны-5 (Task 6-a): имя УВЕДОМЛЕНИЯ participant-state-changed
         * (Ж0 §1.2, enum 16131@163588). CallScreen в ветке Ж2 (Task 5-b) уже сравнивает
         * msg.command с ЭТОЙ константой (CallScreen.kt, when-ветка синка руки), но сама
         * константа в companion объявлена НЕ БЫЛА — репозиторий после волны-5 не собирался.
         * Уведомление эмитится в messages-flow СУЩЕСТВУЮЩИМ обработчиком (effective2 =
         * имя notification) — здесь только объявление литерала. */
        const val CMD_PARTICIPANT_STATE_CHANGED = "participant-state-changed"

        /**
         * #CALLS-ZH2 Ж7: запрещённые символы fileName расшифровки (Ж0 §5.1,
         * валидация эталона bridge@849931): [#%&{}\/<>*?$!`"':@+|=].
         */
        private val ASR_NAME_FORBIDDEN = Regex("[#%&{}\\\\/<>*?\$!`\"':@+|=]")

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
        val ws = webSocket
        webSocket = null
        // #CALLS-HANGUP-GRACE (тест 6, 17:56:18): после hangup мы закрывали WS ЧЕРЕЗ 6 мс —
        // кадр сервер успевал получить (он отвечал ошибкой), но его ответ/ack мы уже
        // теряли (cancel connectLoop → close). 300 мс хватает, чтобы увидеть в логе
        // ответ сервера на hangup — главный признак «сервер завершил разговор».
        if (ws != null) {
            scope.launch {
                delay(300)
                try { ws.close(1000, "client stop") } catch (_: Exception) {}
            }
        }
    }

    fun isRunning(): Boolean = running

    /**
     * #CALLS-REVWEB-BOUNCE (2026-09-02, реверс открытых реализаций VK-звонков):
     * переподключить WS сигналинга, НЕ убивая состояние звонка (running/поток сообщений
     * живут). Эталон (whitelist-bypass vk_joiner.go, case "topology-changed"): при
     * topology != DIRECT закрывают транспорт ЦЕЛИКОМ и переподключаются — новый
     * `connection` несёт СВЕЖИЕ per-connection TURN-креды (vk-calls-tunnel README:
     * «VK TURN credentials are temporary and session-scoped») + перерегистрацию
     * участника. connectLoop сам переустановит соединение (backoff 1с после
     * успешного открытия). Все команды в окне без WS будут отброшены (send вернёт
     * false) — вызывать только когда медиа-нога всё равно мертва (SERVER, ICE нет).
     */
    fun bounce() {
        if (!running) return
        val ws = webSocket
        webSocket = null
        AppLog.w(TAG, "bounce: закрываю WS сигналинга — переподключение ради свежего connection (TURN-креды/перерегистрация)")
        try { ws?.close(1000, "bounce") } catch (_: Exception) {}
    }

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
        // #CALLS-HANGUP-FORMAT (тест 4, 16:04:54; тест 2, 14:43): hangup — ТОЛЬКО {reason}.
        // conversationId в hangup сервер отвергает целиком: SERVER_ERROR
        // "Invalid message format: <base64>" — base64 декодируется в префикс
        // conversationId (e1e6bf41… в тесте 2, db347444… в тесте 4). Эталон шлёт
        // this.send("hangup", {reason}) — без лишних полей (OK/videochat/Signaling.js).
        // #CALLS-HANGUP-REASON-ENUM (тест 6, 17:56): отказ от входящего у эталона —
        // hangup(REJECTED) (Conversation.js: DECLINE_INCOMING → signaling.hangup(hangupType.REJECTED)).
        return send(CMD_HANGUP, mapOf("reason" to "REJECTED"))
    }

    /** Завершить звонок. @return true — команда реально ушла в WS. */
    fun hangup(reason: String = "hungup"): Boolean {
        // #CALLS-HANGUP-FORMAT: только {reason} — см. комментарий в declineCall().
        // #CALLS-HANGUP-REASON-ENUM (тест 6, 17:56:18.172): сервер отверг наш hangup в ЛЮБОЙ
        // форме — reason "timeout" (тесты 2, 5), "hungup" (тест 6), "declined" — всегда
        // SERVER_ERROR "Invalid message format" (hangup не принят НИ РАЗУ за все тесты;
        // в тесте 5 шестой уникальный id b490f8b81230943007 — это он). Причина: у эталона
        // reason — ВЕРХНЕРЕГИСТРОВОЕ значение enum hangupType (Enums_gxwn5e2j.js):
        // CANCELED|REJECTED|REMOVED|HUNGUP|MISSED|BUSY|FAILED. Завершение активного
        // звонка = HUNGUP, отмена исходящего до ответа = CANCELED (Conversation.hangup).
        // Наши lowercase-строки не проходят схему → сервер не завершает разговор,
        // собеседник висит в звонке («огрехи при завершении»). Нормализуем здесь,
        // чтобы все вызовы (ZOMBIE «timeout», пользовательский hangup) уходили верно.
        // #CALLS-HANGUP-STATE-ENUM (30.08, уточнение §32): семантика эталона
        // (Conversation.hangup) — state ACTIVE → HUNGUP, ЛЮБОЕ другое состояние →
        // CANCELED. Все наши сторожа срабатывают ДО ACTIVE: 45с без ответа (RINGING),
        // 60с без answer (CONNECTING), ZOMBIE (CONNECTING) — их «timeout» по эталону
        // это CANCELED (сброс вызова), а не FAILED. FAILED остаётся для явных
        // технических ошибок ("failed"/"error").
        val wire = when (reason.lowercase()) {
            "timeout", "cancel", "canceled", "cancelled" -> "CANCELED"
            "failed", "error" -> "FAILED"
            "reject", "rejected", "decline", "declined" -> "REJECTED"
            else -> "HUNGUP"
        }
        return send(CMD_HANGUP, mapOf("reason" to wire))
    }

    /**
     * Адрес получателя transmit-data в НАШЕМ диалекте (ws2, peerId=0, WEB_SOCKET) —
     * ЧИСЛОВОЙ внутренний id участника ("595859469344").
     *
     * #CALLS-PARTICIPANT-U-REVERT (тест 5, 17:17:41–44): составной "u<id>" (composeId из
     * OK/videochat/Utils.js — это форма ВЕБ-диалекта WEB_TRANSPORT) в ws2 сервер
     * ОТВЕРГАЕТ ПОЛНОСТЬЮ: answer (3613Б, seq=2) и все 4 trickle-кандидата (seq=3–6) →
     * SERVER_ERROR "Invalid message format: <base64(id)>" через ~20 мс после каждой
     * отправки → ZOMBIE → hangup «timeout» через 190 мс после accept. В тесте 4
     * (16:03, сборка ДО #CALLS-PARTICIPANT-U) те же сообщения с ЧИСЛОВЫМ id сервер
     * принял без единой ошибки (10 кандидатов + answer; отвергнут был только hangup
     * со старым форматом — #CALLS-HANGUP-FORMAT). Составной id в конверте ДОСТАВКИ —
     * забота сервера (он сам собирает participant:{id,idType} для получателя);
     * в поле routing-адреса participantId ws2-схема ждёт число.
     */
    private fun composeParticipantId(raw: String): String = raw.trim()

    /** Отправить SDP (answer для входящего, offer для исходящего).
     *  @return true — команда реально ушла в WS (#CALLS-ACK-REOFFER: false = потеряна,
     *  вызывающему ответ нужен ретрай — answer терять нельзя). */
    fun sendSdp(participantId: String, sdp: String, type: String): Boolean {
        // #CALLS-SDP-OBJECT (тест 14:42–14:45): сервер ОК молча выбрасывает transmit-data,
        // где data.sdp — СТРОКА ({"sdp":"v=0…","type":"offer"}): 1353Б аудио-only offer не
        // дошёл ни разу (как и прежний 4.1КБ), при этом объектные candidate из тех же
        // батчей доходили всегда. Offer официального клиента (WEB_TRANSPORT-пир) пришёл
        // как data.sdp = {"type":"offer","sdp":"…"} — ОБЪЕКТ — и был доставлен и
        // распарсен (setRemoteSdp SUCCESS). Значит порог не в размере, а в ФОРМЕ:
        // сервер валидирует data.sdp как объект. Шлём объектной формой; наш приёмник
        // понимает обе (sdpObj ?: строка).
        val sdpObj = JsonObject().apply {
            addProperty("type", type)
            addProperty("sdp", sdp)
        }
        val data = JsonObject().apply { add("sdp", sdpObj) }
        return send(CMD_TRANSMIT_DATA, mapOf("participantId" to composeParticipantId(participantId), "data" to data))
    }

    /** Отправить ICE candidate. @return true — команда реально ушла в WS. */
    fun sendCandidate(participantId: String, sdpMid: String?, sdpMLineIndex: Int?, candidate: String): Boolean {
        val c = JsonObject().apply {
            addProperty("candidate", candidate)
            sdpMid?.let { addProperty("sdpMid", it) }
            sdpMLineIndex?.let { addProperty("sdpMLineIndex", it) }
        }
        val data = JsonObject().apply { add("candidate", c) }
        return send(CMD_TRANSMIT_DATA, mapOf("participantId" to composeParticipantId(participantId), "data" to data))
    }

    // ─── #CALLS-ZH (Этап Ж, Task 5-b): send-методы in-call ядра (ДОБАВЛЕНИЕ) ───
    // Конверт тот же {command, sequence, …} через существующий send(); ответы/уведомления
    // сервера (feedback, participant-state-changed, media-settings-changed) уходят в
    // messages-flow СУЩЕСТВУЮЩИМ обработчиком (else-ветка эмитит имя notification) —
    // CallScreen читает их без правок транспорта.

    /**
     * #CALLS-ZH Ж4: change-media-settings (Ж0 §1.1#9, 16131@306103; §9.1).
     * mediaSettings — ПОЛНЫЙ объект из 6 bool (НЕ диф), ретрай у эталона 10.
     * Mute/камера у эталона сопровождаются этой командой (media-settings-changed).
     * @return true — команда реально ушла в WS (false — WS закрыт, у эталона ретрай).
     */
    fun changeMediaSettings(
        isAudioEnabled: Boolean,
        isVideoEnabled: Boolean,
        isScreenSharingEnabled: Boolean = false,
        isFastScreenSharingEnabled: Boolean = false,
        isAudioSharingEnabled: Boolean = false,
        isAnimojiEnabled: Boolean = false,
    ): Boolean {
        val media = JsonObject().apply {
            addProperty("isAudioEnabled", isAudioEnabled)
            addProperty("isVideoEnabled", isVideoEnabled)
            addProperty("isScreenSharingEnabled", isScreenSharingEnabled)
            addProperty("isFastScreenSharingEnabled", isFastScreenSharingEnabled)
            addProperty("isAudioSharingEnabled", isAudioSharingEnabled)
            addProperty("isAnimojiEnabled", isAnimojiEnabled)
        }
        return send(CMD_CHANGE_MEDIA_SETTINGS, mapOf("mediaSettings" to media))
    }

    /**
     * #CALLS-ZH Ж4: update-media-modifiers — шумодав (Ж0 §1.1#31, 16131@308860;
     * маппинг режимов 9644@18175). Значения UI web: NEURAL/AUTO={denoise:true,
     * denoiseAnn:true}, SIMPLE={true,false}, NONE/CLIENT={false,false} — конвертация
     * у вызывающего (CallScreen/CallMediaSettingsPanel), здесь чистый wire.
     * @return true — команда реально ушла в WS.
     */
    fun updateMediaModifiers(denoise: Boolean, denoiseAnn: Boolean): Boolean {
        val m = JsonObject().apply {
            addProperty("denoise", denoise)
            addProperty("denoiseAnn", denoiseAnn)
        }
        return send(CMD_UPDATE_MEDIA_MODIFIERS, mapOf("mediaModifiers" to m))
    }

    /**
     * #CALLS-ZH Ж2: feedback — реакция (эмодзи-плашка) (Ж0 §3.1, 16131@309999).
     * wire: {"command":"feedback","sequence":N,"key":"<key>"}. У эталона _sendRaw —
     * БЕЗ декомпозиции participantId (не нужен). key — СЕРВЕРНОЕ имя из каталога
     * calls.getReactions (Ж0 §13.5: в снапшотах ключей нет — только каталог).
     * @return true — команда реально ушла в WS.
     */
    fun sendFeedback(key: String): Boolean = send(CMD_FEEDBACK, mapOf("key" to key))

    /**
     * #CALLS-ZH Ж2: change-participant-state — поднятая рука (Ж0 §3.2, 16131@306127).
     * wire: {"command":"change-participant-state","sequence":N,
     *        "participantState":{"state":{"hand":"1"|"0"}}}. КЛЮЧ/ЗНАЧЕНИЕ ≤5 символов
     * (валидация эталона 16131@402205); значения "1"/"0" — SDK-enum
     * ParticipantStateDataValue (16131@480035; живая проверка — Этап И, Ж0 §13.1).
     * Чужую руку (participantId) эталон позволяет только админу — здесь НЕ шлём.
     * @return true — команда реально ушла в WS.
     */
    fun changeParticipantState(key: String, value: String): Boolean {
        if (key.length > 5 || value.length > 5) {
            AppLog.w(TAG, "changeParticipantState: key/value >5 символов — сервер отбросит ($key=$value)")
            return false
        }
        val state = JsonObject().apply { addProperty(key, value) }
        val ps = JsonObject().apply { add("state", state) }
        return send(CMD_CHANGE_PARTICIPANT_STATE, mapOf("participantState" to ps))
    }

    // ─── #CALLS-ZH2 (Task 6-a): send-методы Ж6 запись / Ж7 ASR / Ж8 зал ожидания / Ж9 залы ───
    // АДДИТИВНО: существующие константы/методы/форматы (accept-call/hangup/transmit-data/
    // change-media-settings/update-media-modifiers/feedback/change-participant-state) НЕ тронуты.
    // Уведомления ответных событий (record-started/stopped, asr-started/stopped, promote-participant,
    // room-updated/rooms-updated/room-participants-updated) уходят в messages-flow СУЩЕСТВУЮЩИМ
    // обработчиком (else-ветка эмитит имя notification) — CallScreen/панели читают без правок транспорта.

    /**
     * #CALLS-ZH2: конверт для команд с JSON-массивами и ЯВНЫМИ null
     * (record-start/stop, asr-start/stop, get-rooms/update-rooms/activate-rooms/
     * remove-rooms/switch-room, get-waiting-hall/promote-participant/remove-participant).
     * Существующий send(Map<String,Any>) НЕ РАСШИРЯЛСЯ (нулевая правка форм сообщений
     * волн 1-5): JSON null в Map невыразим, а null-поля wire-схемы обязательны
     * (Ж0 §4: movieId/groupId/roomId=null в record-start). Конверт тот же
     * {command, sequence, …body} (Ж0 §2.2 _serializeJson, 16131@320580).
     * @return true — команда реально ушла в WS.
     */
    private fun sendJson(command: String, body: JsonObject): Boolean {
        val ws = webSocket
        if (ws == null || !wsOpen) {
            AppLog.w(TAG, "sendJson: WS не открыт, команда '$command' отброшена")
            return false
        }
        return try {
            val seq = sequence.incrementAndGet()
            val payload = JsonObject()
            payload.addProperty("command", command)
            payload.addProperty("sequence", seq)
            for (e in body.entrySet()) {
                payload.add(e.key, e.value)
            }
            val text = payload.toString()
            val ok = ws.send(text)
            AppLog.i(TAG, "send: command=$command seq=$seq ok=$ok size=${text.toByteArray(Charsets.UTF_8).size}Б")
            AppLog.d(TAG, "send payload: $payload")
            ok
        } catch (e: Exception) {
            AppLog.w(TAG, "send error: ${e.message}")
            false
        }
    }

    /**
     * #CALLS-ZH2 Ж6: record-start — запись звонка (Ж0 §4, 16131@422393 startStream).
     * wire: {"command":"record-start","sequence":N,"movieId":null,"name":"<название>",
     *        "privacy":"DIRECT_LINK","groupId":null,"roomId":null,"streamMovie":false}
     * (roomId=null — основной зал; movieId/groupId — ЯВНЫЕ JSON null). streamMovie=true —
     * только для трансляции (Ж11: video.startStreaming — фасадом не обеспечен, НЕ шлём).
     * Имя по умолчанию UI-эталона "<имя звонившего> <дата>", лимит 128 (bridge@850013) —
     * формирует вызывающий (CallMorePanel/CallScreen). Индикация: record-started/stopped.
     * @return true — команда реально ушла в WS.
     */
    fun startRecording(name: String, privacy: String = "DIRECT_LINK", streamMovie: Boolean = false): Boolean {
        val body = JsonObject()
        body.add("movieId", JsonNull.INSTANCE)
        body.addProperty("name", name)
        body.addProperty("privacy", privacy)
        body.add("groupId", JsonNull.INSTANCE)
        body.add("roomId", JsonNull.INSTANCE)
        body.addProperty("streamMovie", streamMovie)
        return sendJson(CMD_RECORD_START, body)
    }

    /**
     * #CALLS-ZH2 Ж6: record-stop — стоп записи (Ж0 §4, 16131@422494 stopStream(e={roomId:null})).
     * [remove]=true — удалить созданный video-стрим (только трансляция, Ж0 §4; здесь false —
     * поле опускается). Индикация: record-stopped.
     * @return true — команда реально ушла в WS.
     */
    fun stopRecording(remove: Boolean = false): Boolean {
        val body = JsonObject()
        body.add("roomId", JsonNull.INSTANCE)
        if (remove) body.addProperty("remove", true)
        return sendJson(CMD_RECORD_STOP, body)
    }

    /**
     * #CALLS-ZH2 Ж7: asr-start — старт расшифровки (Ж0 §5.1, 16131@310522 startAsr;
     * валидация эталона bridge@849931): fileName ≤128 и БЕЗ символов
     * [#%&{}\\/<>*?$!`"':@+|=]. Заголовок расшифровки = fileName; результат — «Расшифровки
     * звонков» (calls.getAsrTranscriptions, фасад есть). Индикация: asr-started {asrInfo}.
     * @return true — команда реально ушла в WS (false — невалидное имя/WS закрыт).
     */
    fun startAsr(fileName: String): Boolean {
        if (fileName.length > 128) {
            AppLog.w(TAG, "startAsr: fileName >128 символов — сервер отклонит (${fileName.length})")
            return false
        }
        if (ASR_NAME_FORBIDDEN.containsMatchIn(fileName)) {
            AppLog.w(TAG, "startAsr: fileName содержит запрещённые символы (Ж0 §5.1) — сервер отклонит")
            return false
        }
        val body = JsonObject()
        body.addProperty("fileName", fileName)
        return sendJson(CMD_ASR_START, body)
    }

    /**
     * #CALLS-ZH2 Ж7: asr-stop — стоп расшифровки (Ж0 §5.1, 16131@310539 stopAsr:
     * payload {} — roomId добавляется только если есть; у нас roomId всегда основной зал = null).
     * Индикация: asr-stopped {roomId}.
     * @return true — команда реально ушла в WS.
     */
    fun stopAsr(): Boolean = sendJson(CMD_ASR_STOP, JsonObject())

    /**
     * #CALLS-ZH2 Ж9: get-rooms — список залов (Ж0 §8, 16131@307641). withParticipants=true —
     * только админ (Ж0 §8). Ответ: type:"response" c {rooms:{roomId, rooms:[Room]}}
     * (Room-схема Ж0 §8); парсинг — CallRoomsPanel (tolerant).
     * @return true — команда реально ушла в WS.
     */
    fun getRooms(withParticipants: Boolean): Boolean {
        val body = JsonObject()
        body.addProperty("withParticipants", withParticipants)
        return sendJson(CMD_GET_ROOMS, body)
    }

    /**
     * #CALLS-ZH2 Ж9: update-rooms — СОЗДАНИЕ залов (Ж0 §8, bridge@819377):
     * {rooms:[{name:"Зал N", participantCount:0, countdownSec?}], assignRandomly:false}.
     * Имя по умолчанию эталона calls_room_control_create_default_room_name («Зал 1») —
     * генерирует вызывающий. [countdownSec] — таймер залов (опция, не шлём при null).
     * @return true — команда реально ушла в WS.
     */
    fun updateRoomsCreate(roomNames: List<String>, assignRandomly: Boolean = false, countdownSec: Long? = null): Boolean {
        if (roomNames.isEmpty()) {
            AppLog.w(TAG, "updateRoomsCreate: пустой список имён — команда не отправляется")
            return false
        }
        val arr = JsonArray()
        for (name in roomNames) {
            val room = JsonObject()
            room.addProperty("name", name)
            room.addProperty("participantCount", 0)
            val cd = countdownSec
            if (cd != null) room.addProperty("countdownSec", cd)
            arr.add(room)
        }
        val body = JsonObject()
        body.add("rooms", arr)
        body.addProperty("assignRandomly", assignRandomly)
        return sendJson(CMD_UPDATE_ROOMS, body)
    }

    /**
     * #CALLS-ZH2 Ж9: activate-rooms — открыть/закрыть залы (Ж0 §8, bridge@830343/394008):
     * {roomIds:[…], deactivate:bool}. Закрытие у эталона = activate-rooms{deactivate:true}
     * + затем remove-rooms (двухшагово — вызывающий).
     * @return true — команда реально ушла в WS.
     */
    fun activateRooms(roomIds: List<Long>, deactivate: Boolean): Boolean {
        if (roomIds.isEmpty()) {
            AppLog.w(TAG, "activateRooms: пустой список id — команда не отправляется")
            return false
        }
        val arr = JsonArray()
        for (id in roomIds) arr.add(JsonPrimitive(id))
        val body = JsonObject()
        body.add("roomIds", arr)
        body.addProperty("deactivate", deactivate)
        return sendJson(CMD_ACTIVATE_ROOMS, body)
    }

    /**
     * #CALLS-ZH2 Ж9: remove-rooms — удалить залы (Ж0 §8, bridge@830722): {roomIds:[…]}.
     * @return true — команда реально ушла в WS.
     */
    fun removeRooms(roomIds: List<Long>): Boolean {
        if (roomIds.isEmpty()) {
            AppLog.w(TAG, "removeRooms: пустой список id — команда не отправляется")
            return false
        }
        val arr = JsonArray()
        for (id in roomIds) arr.add(JsonPrimitive(id))
        val body = JsonObject()
        body.add("roomIds", arr)
        return sendJson(CMD_REMOVE_ROOMS, body)
    }

    /**
     * #CALLS-ZH2 Ж9: switch-room — переход в зал (Ж0 §1.1#47, 16131@307553):
     * {toRoomId?(null=ОСНОВНОЙ зал — ЯВНЫЙ JSON null), participantId?}. participantId —
     * перемещение ДРУГОГО участника (админ; §13.7 — семантика не доказана, не используем).
     * Форма participantId — СТРОКА (проверенный в этом клиенте формат transmit-data;
     * живая сверка — Этап И). Событие: room-participants-updated.
     * @return true — команда реально ушла в WS.
     */
    fun switchRoom(toRoomId: Long?, participantId: String? = null): Boolean {
        val body = JsonObject()
        val rid = toRoomId
        if (rid != null) body.add("toRoomId", JsonPrimitive(rid)) else body.add("toRoomId", JsonNull.INSTANCE)
        val pid = participantId
        if (pid != null) body.addProperty("participantId", pid)
        return sendJson(CMD_SWITCH_ROOM, body)
    }

    /**
     * #CALLS-ZH2 Ж8: get-waiting-hall — список ожидающих в зале ожидания (Ж0 §7,
     * 16131@309933 getWaitingHall): {fromId?, count?, backward?} → response
     * {totalCount, participants:[…]}. Пагинация: fromId — курсор (эталон догружает
     * пачками; наш UI — одна пачка [count] + повтор по кнопке «Обновить»).
     * @return true — команда реально ушла в WS.
     */
    fun getWaitingHall(count: Int, fromId: Long? = null, backward: Boolean = false): Boolean {
        val body = JsonObject()
        body.addProperty("count", count)
        body.addProperty("backward", backward)
        val f = fromId
        if (f != null) body.addProperty("fromId", f)
        return sendJson(CMD_GET_WAITING_HALL, body)
    }

    /**
     * #CALLS-ZH2 Ж8: promote-participant — ВПУСТИТЬ из зала ожидания (Ж0 §7, 16131@309678):
     * {participantId} (demote=true — вернуть в зал/выгнать из звонка, 16131@408802 —
     * админ-функция). Форма participantId — СТРОКА (проверенный формат; Этап И — живая сверка).
     * Отказ (forbid) — отдельная команда remove-participant {ban:false} ([removeParticipant]).
     * @return true — команда реально ушла в WS.
     */
    fun promoteParticipant(participantId: String, demote: Boolean = false): Boolean {
        val body = JsonObject()
        body.addProperty("participantId", participantId)
        if (demote) body.addProperty("demote", true)
        return sendJson(CMD_PROMOTE_PARTICIPANT, body)
    }

    /**
     * #CALLS-ZH2 Ж8: remove-participant — отказ ожидающему (Ж0 §6.3/§7):
     * {participantId, ban:false} — kickType WAITING_HALL у эталона (чисто UI-различие;
     * ban=true — обычный kick из Ж1, здесь НЕ используем).
     * @return true — команда реально ушла в WS.
     */
    fun removeParticipant(participantId: String, ban: Boolean = false): Boolean {
        val body = JsonObject()
        body.addProperty("participantId", participantId)
        body.addProperty("ban", ban)
        return sendJson(CMD_REMOVE_PARTICIPANT, body)
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
            // #CALLS-WS-PONG (тест 6, 17:56:00–15): сервер каждые ~5 с шлёт текстовый кадр
            // "ping"; эталон отвечает текстовым "pong" (Signaling._onMessage:
            // if(e.data==="ping"){this.socket.send("pong");return}). Мы молчали — при
            // длинных звонках сервер может счесть соединение неактивным
            // (activityTimeout=120000) и оборвать сигналинг.
            if (text == "ping") {
                try { webSocket?.send("pong") } catch (_: Exception) {}
                AppLog.d(TAG, "ping → pong")
                return
            }
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
