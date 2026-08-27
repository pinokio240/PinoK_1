package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.data.model.CallDirection
import re.pinok.data.model.CallMediaType
import re.pinok.data.model.CallPhase
import re.pinok.data.model.CallParticipant
import re.pinok.data.model.VkCall
import re.pinok.media.WebRtcEngine
import re.pinok.util.AppLog
import org.webrtc.SessionDescription

/**
 * #CALLS: экран активного звонка — входящий, исходящий, разговор.
 *
 * Состояния:
 *  - RINGING: анимация звонка + кнопки «Принять»/«Отклонить»
 *  - CONNECTING: спиннер + «Соединение…»
 *  - ACTIVE: разговор — кнопки mute/speaker/end
 *  - ENDED/FAILED: результат + кнопка «Закрыть»
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    peerId: Long,
    title: String,
    photo: String?,
    incoming: Boolean,
    onNavigateBack: () -> Unit,
    /**
     * #CALLS: payload LP 115 (conversation params "len:base64") для входящего звонка.
     * Декодируется в STUN/TURN/token/endpoint и используется для WebSocket-сигналинга
     * (accept/decline). null для исходящих.
     */
    incomingPayload: String? = null,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val direction = if (incoming) CallDirection.INCOMING else CallDirection.OUTGOING
    var phase by remember { mutableStateOf(if (incoming) CallPhase.RINGING else CallPhase.CONNECTING) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0L) }
    // #CALLS-MIC-GUARD (2026-08-27): без RECORD_AUDIO трек создаётся, но захват
    // не идёт — собеседник слышит тишину. Запрашиваем разрешение до начала звонка.
    var micGranted by remember { mutableStateOf(re.pinok.util.PermissionManager.hasRecordAudio(context)) }
    var failText by remember { mutableStateOf<String?>(null) }
    var noAnswer by remember { mutableStateOf(false) }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) {
            AppLog.w("CallScreen", "RECORD_AUDIO denied — звонок невозможен")
            failText = "Нет доступа к микрофону"
            phase = CallPhase.FAILED
        }
    }

    val peer = CallParticipant(peerId = peerId, name = title, photo100 = photo)
    val call = VkCall(
        callId = "",
        peer = peer,
        direction = direction,
        mediaType = CallMediaType.AUDIO,
        phase = phase,
        isMuted = isMuted,
        isSpeakerOn = isSpeakerOn,
    )

    // #CALLS: WebSocket-сигналинг для ответа на входящий/исходящий звонок.
    val signaling = remember { re.pinok.realtime.CallSignalingClient(app.httpClient) }
    // conversation params для входящего (декодированные)
    val incomingParams = remember(incomingPayload) {
        incomingPayload?.let { re.pinok.media.ConversationParamsDecoder.decode(it) }
    }
    // #CALLS: participantId собеседника (из offer/candidate в сигналинге).
    // Нужен для отправки answer SDP и ICE candidates обратно.
    val remoteParticipantId = remember { mutableStateOf<String?>(null) }
    // #CALLS: кэш offer + ICE candidates — приходят сразу после подключения WS,
    // ДО нажатия «Принять». PeerConnection создаётся только при accept, поэтому
    // offer/candidates применяем после создания PC.
    val pendingOffer = remember { mutableStateOf<Pair<String, String>?>(null) } // (sdp, type)
    val pendingCandidates = remember { mutableStateOf<MutableList<Triple<String?, Int, String>>>(mutableListOf()) }
    // #CALLS-OUTGOING: кэш нашего offer/candidates до получения participantId.
    val pendingLocalSdp = remember { mutableStateOf<org.webrtc.SessionDescription?>(null) }
    val pendingLocalCandidates = remember { mutableStateOf<MutableList<org.webrtc.IceCandidate>>(mutableListOf()) }
    // #CALLS: call_id активного звонка — для кнопки «Ссылка» (vchat.createJoinLink).
    val activeCallId = remember { mutableStateOf<String?>(null) }

    val engine = remember {
        WebRtcEngine(
            context = context,
            onCallPhaseChanged = { phase = it },
            onLocalSdpReady = { sdp ->
                // #CALLS: отправляем наш SDP (offer для исходящего, answer для входящего).
                // Если participantId ещё неизвестен (исходящий: приходит из connection
                // ПОСЛЕ createOffer) — кэшируем и отправим при получении participantId.
                val pid = remoteParticipantId.value
                if (pid != null) {
                    AppLog.i("CallScreen", "sending local SDP type=${sdp.type} to participant=$pid")
                AppLog.i("CallScreen", "FULL_LOCAL_${sdp.type} SDP:\n${sdp.description}")
                    signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
                } else {
                    pendingLocalSdp.value = sdp
                    AppLog.w("CallScreen", "local SDP готов, participantId неизвестен — кэшируем")
                }
            },
            onIceCandidateReady = { candidate ->
                // #CALLS: отправляем ICE candidate собеседнику.
                val pid = remoteParticipantId.value
                if (pid != null) {
                    signaling.sendCandidate(pid, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                } else {
                    pendingLocalCandidates.value.add(candidate)
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        engine.initialize()
        // #CALLS-MIC-GUARD: запрашиваем микрофон до установки соединения.
        if (!micGranted) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        if (incoming) {
            // #CALLS: входящий звонок. Если есть payload — декодируем conversation
            // params и подключаемся к WebSocket-сигналингу (accept/decline).
            AppLog.i("CallScreen", "Incoming call, payload.len=${incomingPayload?.length ?: 0}")
            var params = incomingParams
            var callConvId: String? = null
            if (params == null) {
                // payload = "-1" — полные conversation params нужно получить через
                // vchat API по conversationId (vchat.getConversationParams).
                // conversationId берём из messages.getCurrentCalls (активный звонок).
                AppLog.w("CallScreen", "payload не содержит conversation params — пробуем vchat API")
                try {
                    val calls = app.apiClient.messagesGetCurrentCalls()
                    AppLog.i("CallScreen", "messagesGetCurrentCalls: items=${calls.size}")
                    calls.firstOrNull()?.let { call ->
                        AppLog.i("CallScreen", "current call json=${call}")
                        // ищем conversation_id / id в объекте звонка
                        val convId = call.get("conversation_id")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: call.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: call.get("call_id")?.takeIf { it.isJsonPrimitive }?.asString
                        callConvId = convId
                        if (!convId.isNullOrBlank()) {
                            // Полностью автоматическая цепочка (как браузер):
                            // session_key из prefs → vchat.getConversationParams,
                            // при 102 (session expired) — авто-получение свежего
                            // через get_anonym_token → auth.anonymLogin → повтор.
                            // См. SovaApp.getCallConversationParams.
                            val (sessionKey, vchatResp) = app.getCallConversationParams(convId)
                            AppLog.i("CallScreen", "getCallParams: sessionKey=${sessionKey?.take(12) ?: "null"}… vchat=${if (vchatResp != null) "OK" else "null"}")
                            if (vchatResp != null) {
                                params = re.pinok.media.ConversationParamsDecoder.decodeParamsJson(vchatResp)
                                if (params != null) {
                                    AppLog.i("CallScreen",
                                        "vchat params: endpoint=${params.endpoint.take(40)}… token=${params.token.take(8)}…")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e("CallScreen", "vchat fallback error", e)
                }
            }
            val resolvedParams = params
            if (resolvedParams == null) {
                AppLog.w("CallScreen", "Не удалось получить conversation params — звонок принять нельзя")
                phase = CallPhase.FAILED
            } else {
                // #CALLS-FIX: userId в WS URL — это okcdn uid из _okcls_anonymLogin
                // (напр. 584520805550), НЕ VK user_id (171093180). Токен из
                // vchat.getConversationParams привязан к okcdn uid — с VK id
                // сервер отвечает invalid-token.
                val snap = app.prefs.data.first()
                val okUid = snap.callsSessionUid
                val uid = if (okUid > 0L) okUid else app.exchangeAuthRepository.userId()
                val convId = callConvId ?: ""
                activeCallId.value = convId
                // #CALLS-FIX: реальные STUN/TURN из conversation params — без них ICE FAILED.
                engine.setIceServers(resolvedParams)
                AppLog.i("CallScreen", "Signaling start: conversationId=$convId userId=$uid (okUid=$okUid)")
                signaling.start(userId = uid, conversationId = convId, params = resolvedParams, peerId = peerId)
                AppLog.i("CallScreen", "Signaling started — ждём accept/decline от пользователя")
            }
        } else {
            AppLog.i("CallScreen", "Starting call to peerId=$peerId")
            try {
                val callId = app.apiClient.messagesStartCall(peerId)
                AppLog.i("CallScreen", "messagesStartCall returned: $callId")
                if (callId == null) {
                    val err = app.apiClient.lastApiError
                    val errCode = app.apiClient.lastApiErrorCode
                    AppLog.e("CallScreen", "startCall failed: err=$err code=$errCode")
                    android.widget.Toast.makeText(
                        context,
                        "Звонки не поддерживаются для web-токена (err=$errCode)",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    phase = CallPhase.FAILED
                } else {
                    AppLog.i("CallScreen", "Call started: callId=$callId")
                    activeCallId.value = callId
                    // #CALLS: получаем queue-credential через queue.subscribe (SAT-токен)
                    // и запускаем long-poll — ловим LP 115 (собеседник принял/звонит).
                    val cred = app.apiClient.queueSubscribe()
                    if (cred != null) {
                        app.queuev4Client.setCredential(cred)
                        app.queuev4Client.start()
                        AppLog.i("CallScreen", "queuev4 started (key=${cred.key.take(8)}… ts=${cred.ts})")
                    } else {
                        AppLog.w("CallScreen", "queueSubscribe returned null — входящий звонок не будет обработан")
                    }
                    // #CALLS-OUTGOING (2026-08-24): продолжаем исходящий звонок —
                    // получаем conversation params (vchat) и запускаем WebRTC-сигналинг
                    // как initiator (createOffer → отправка offer через WS).
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        // #CALLS-FIX: форсируем свежие credentials — получаем okcdn uid
                        // (584520805550) из auth.anonymLogin, нужен для WS userId.
                        val sk2 = app.ensureCallsSessionKey(force = true)
                        // #CALLS-FIX (2026-08-24): для ИСХОДЯЩЕГО нужна активная conversation —
                        // vchat.startConversation (иначе сервер сразу conversation-ended).
                        // ВАЖНО (эталон Chrome 2026-08-24 + CALLS_MAP §6): conversationId для
                        // vchat/WS — СВОЙ UUID, который генерирует клиент (ConversationFactory),
                        // НЕ call_id из messages.startCall! Если передать call_id — сервер
                        // закрывает conversation: conversation-ended (INITIALLY_CLOSED).
                        val outgoingConvId = java.util.UUID.randomUUID().toString()
                        var wsConversationId = outgoingConvId
                        if (!sk2.isNullOrBlank()) {
                            // #CALLS-FIX (2026-08-24): эталон Chrome desktop вызывает
                            // system.getInfo ПЕРЕД startConversation.
                            val sysResp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                app.apiClient.vchatSystemGetInfo(sk2)
                            }
                            AppLog.i("CallScreen", "vchat.system.getInfo: ${if (sysResp != null) "OK (${sysResp.keySet().size} полей)" else "null"}")
                            AppLog.i("CallScreen", "startConversation: conversationId=$outgoingConvId (свой UUID, не call_id=$callId)")
                            val scResp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                app.apiClient.vchatStartConversation(outgoingConvId, sk2, peerId)
                            }
                            if (scResp != null) {
                                AppLog.i("CallScreen", "vchat.startConversation OK (${scResp.keySet().size} полей)")
                                // #CALLS-FIX: response содержит id conversation (НЕ call_id!) —
                                // WS подключается именно с ним, иначе conversation-ended.
                                val convId = scResp.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                                if (!convId.isNullOrBlank()) {
                                    wsConversationId = convId
                                    AppLog.i("CallScreen", "conversation id из startConversation: $convId")
                                }
                            } else {
                                AppLog.w("CallScreen", "vchat.startConversation вернул null")
                            }
                        }
                        val (sk, vchatResp) = app.getCallConversationParams(wsConversationId)
                        val params = vchatResp?.let { re.pinok.media.ConversationParamsDecoder.decodeParamsJson(it) }
                        if (params == null) {
                            AppLog.w("CallScreen", "Исходящий: не удалось получить conversation params")
                            phase = CallPhase.FAILED
                            return@launch
                        }
                        val snap = app.prefs.data.first()
                        val okUid = snap.callsSessionUid
                        val vkUid = app.exchangeAuthRepository.userId()
                        // #CALLS-FIX: userId в WS URL — okcdn uid (584520805550), НЕ VK user_id.
                        // Если в prefs лежит VK id (старый баг) — берём okcdn uid из
                        // последнего auth.anonymLogin (lastAnonymUid).
                        val uid = when {
                            okUid > 0L && okUid != vkUid -> okUid
                            app.apiClient.lastAnonymUid() > 0L -> app.apiClient.lastAnonymUid()
                            else -> vkUid
                        }
                        engine.setIceServers(params)
                        AppLog.i("CallScreen", "Outgoing signaling start: conversationId=$wsConversationId userId=$uid")
                        signaling.start(userId = uid, conversationId = wsConversationId, params = params, peerId = peerId)
                        // Создаём PeerConnection + offer (isInitiator=true) →
                        // onLocalSdpReady отправит offer через signaling.sendSdp.
                        val call = re.pinok.data.model.VkCall(
                            callId = callId,
                            peer = re.pinok.data.model.CallParticipant(peerId, "", null),
                            direction = re.pinok.data.model.CallDirection.OUTGOING,
                            mediaType = re.pinok.data.model.CallMediaType.AUDIO,
                            phase = re.pinok.data.model.CallPhase.RINGING,
                        )
                        engine.startCall(call, isInitiator = true)
                        phase = CallPhase.RINGING
                    }
                }
            } catch (e: Exception) {
                AppLog.e("CallScreen", "startCall exception", e)
                android.widget.Toast.makeText(
                    context,
                    "Ошибка: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                phase = CallPhase.FAILED
            }
        }
    }

    // #CALLS: слушаем события queuev4 — LP 115 (входящий звонок) и прочие.
    LaunchedEffect(Unit) {
        app.queuev4Client.events.collect { ev ->
            AppLog.i("CallScreen", "queuev4 event: queue=${ev.queueId} payload=${ev.payload}")
            val code = ev.payload["code"] as? Long
            if (code == 115L || ev.queueId == "calls") {
                // Входящий/ответ собеседника — переводим в CONNECTING.
                // SDP/ICE обмен пока не реализован (WebRTC signaling через WebSocket — TODO).
                if (phase == CallPhase.RINGING || phase == CallPhase.CONNECTING) {
                    phase = CallPhase.CONNECTING
                }
            }
        }
    }

    // #CALLS: слушаем WebSocket-сигналинг — SDP/ICE для WebRTC.
    LaunchedEffect(Unit) {
        signaling.messages.collect { msg ->
            AppLog.i("CallScreen", "signaling: command=${msg.command} sdp=${msg.sdp?.take(40)} cand=${msg.candidate?.take(40)}")
            when (msg.command) {
                re.pinok.realtime.CallSignalingClient.CMD_ANSWER,
                re.pinok.realtime.CallSignalingClient.CMD_OFFER -> {
                    // #CALLS: запоминаем participantId собеседника для ответа.
                    msg.participantId?.let { remoteParticipantId.value = it }
                    msg.sdp?.let { sdp ->
                        val isOffer = msg.command == re.pinok.realtime.CallSignalingClient.CMD_OFFER ||
                            msg.sdpType == "offer"
                        val type = if (isOffer) org.webrtc.SessionDescription.Type.OFFER
                        else org.webrtc.SessionDescription.Type.ANSWER
                        if (engine.hasPeerConnection()) {
                            // #CALLS-OUT-FIX (2026-08-27): PC уже создан — answer/offer
                            // применяем СРАЗУ. Раньше answer лишь кэшировался в
                            // pendingOffer, который читается ТОЛЬКО в кнопке «Принять»
                            // входящего звонка → в исходящем answer не применялся вовсе,
                            // звонок вечно висел «Звоним…».
                            AppLog.i("CallScreen", "applying remote ${type.name.lowercase()} immediately (PC ready)")
                            engine.setRemoteSdp(sdp, type)
                            if (!isOffer) phase = CallPhase.CONNECTING
                        } else {
                            // Входящий до «Принять»: PC ещё нет — кэшируем; применит accept.
                            pendingOffer.value = sdp to if (isOffer) "offer" else "answer"
                            AppLog.i("CallScreen", "remote ${msg.sdpType} кэширован (${sdp.take(30)}…) participant=$remoteParticipantId")
                        }
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_CANDIDATE -> {
                    msg.participantId?.let { remoteParticipantId.value = it }
                    msg.candidate?.let { c ->
                        // #CALLS-FIX: если звонок уже принят (PC создан) — применяем
                        // кандидата сразу, иначе кэшируем до accept.
                        if (phase == CallPhase.RINGING) {
                            pendingCandidates.value.add(Triple(msg.candidateSdpMid, msg.candidateSdpMLineIndex ?: 0, c))
                        } else {
                            engine.addRemoteIceCandidate(msg.candidateSdpMid, msg.candidateSdpMLineIndex ?: 0, c)
                        }
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_CALL_ERROR -> {
                    AppLog.w("CallScreen", "Signaling error: ${msg.json}")
                    phase = CallPhase.FAILED
                }
                re.pinok.realtime.CallSignalingClient.CMD_REGISTERED_PEER -> {
                    // #CALLS-OUTGOING (2026-08-24, подтверждено реальным WS):
                    // сервер шлёт { notification:"registered-peer", participantId:<id> }
                    // когда собеседник зарегистрировался в conversation. participantId
                    // здесь — ID участника, НА КОТОРЫЙ слать offer/ICE (не наш WS peerId).
                    val pid = msg.participantId
                    if (!pid.isNullOrBlank() && remoteParticipantId.value == null) {
                        remoteParticipantId.value = pid
                        AppLog.i("CallScreen", "registered-peer: participantId=$pid")
                        // Отправляем кэшированные offer + ICE candidates собеседнику.
                        val cached = pendingLocalSdp.value
                        if (cached != null) {
                            pendingLocalSdp.value = null
                            signaling.sendSdp(pid, cached.description, cached.type.name.lowercase())
                            AppLog.i("CallScreen", "кэшированный offer отправлен ($pid)")
                        }
                        pendingLocalCandidates.value.forEach { c ->
                            signaling.sendCandidate(pid, c.sdpMid, c.sdpMLineIndex, c.sdp)
                        }
                        pendingLocalCandidates.value.clear()
                    }
                }
                "connection" -> {
                    // #CALLS-FIX (логика Chrome): сервер присылает в событии connection
                    // СВОИ conversationParams с TURN-credentials — Chrome использует именно их
                    // (в pcap виден username из WS, а не из vchat API). Обновляем ICE-серверы.
                    try {
                        val cp = msg.json.get("conversationParams")
                            ?.takeIf { it.isJsonObject }?.asJsonObject
                        if (cp != null) {
                            val wsParams = re.pinok.media.ConversationParamsDecoder.decodeParamsJson(cp)
                            if (wsParams?.turnServer != null || wsParams?.stunServer != null) {
                                engine.setIceServers(wsParams)
                                AppLog.i("CallScreen",
                                    "ICE обновлены из WS connection: turn=${wsParams.turnServer?.urls} " +
                                    "user=${wsParams.turnServer?.username?.take(12)}… " +
                                    "cred=${wsParams.turnServer?.credential?.take(8)}…")
                            } else {
                                AppLog.w("CallScreen", "WS connection без TURN/STUN в conversationParams")
                            }
                        }
                        // #CALLS-OUTGOING (2026-08-24, подтверждено реальным логом):
                        // в connection приходит conversation.participants[] — полный список
                        // участников. Собеседник — тот, чей externalId != наш VK user_id
                        // (и id != наш okcdn uid 584520805550). Его `id` — participantId,
                        // НА КОТОРЫЙ слать offer/ICE (у нас было 595859469344).
                        // Раньше ждали registered-peer, но он может и не прийти —
                        // participants[] в connection содержит всё.
                        val convObj = msg.json.get("conversation")
                            ?.takeIf { it.isJsonObject }?.asJsonObject
                        val participantsArr = convObj?.get("participants")
                            ?.takeIf { it.isJsonArray }?.asJsonArray
                        if (participantsArr != null && remoteParticipantId.value == null) {
                            val myVkUid = app.exchangeAuthRepository.userId()
                            // #CALLS-OUT-FIX (2026-08-27): наш okcdn uid берём из prefs
                            // (callsSessionUid — заполняет ensureCallsSessionKey). Хардкод
                            // 584520805550 был верен только на устройстве разработчика —
                            // на любом другом «я» не распознавалось, и offer уходил
                            // самому себе → звонок вечно «Звоним…».
                            val myOkUid = runCatching { app.prefs.data.first().callsSessionUid }.getOrDefault(0L)
                            for (el in participantsArr) {
                                if (!el.isJsonObject) continue
                                val p = el.asJsonObject
                                val extId = p.get("externalId")?.takeIf { it.isJsonObject }?.asJsonObject
                                val extVkId = extId?.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
                                val pId = p.get("id")?.takeIf { it.isJsonPrimitive }?.asLong
                                val state = p.get("state")?.takeIf { it.isJsonPrimitive }?.asString
                                // Собеседник: другой VK id / не наш okcdn uid.
                                val isMe = extVkId == myVkUid || (myOkUid > 0L && pId == myOkUid)
                                if (!isMe && pId != null && pId > 0L) {
                                    remoteParticipantId.value = pId.toString()
                                    AppLog.i("CallScreen", "participantId собеседника из connection.participants: $pId (extId=$extVkId state=$state)")
                                    val cached = pendingLocalSdp.value
                                    if (cached != null) {
                                        pendingLocalSdp.value = null
                                        signaling.sendSdp(pId.toString(), cached.description, cached.type.name.lowercase())
                                        AppLog.i("CallScreen", "кэшированный offer отправлен ($pId)")
                                    }
                                    pendingLocalCandidates.value.forEach { c ->
                                        signaling.sendCandidate(pId.toString(), c.sdpMid, c.sdpMLineIndex, c.sdp)
                                    }
                                    pendingLocalCandidates.value.clear()
                                    break
                                }
                            }
                        }
                        // #CALLS-OUTGOING: также слушаем topology-changed — сервер может
                        // переключить topology и указать offerTo (кому слать offer).
                        // Обрабатывается в when-ветке ниже.
                        // #CALLS-OUTGOING-FIX (2026-08-24): после connection инициатор
                        // ТОЖЕ отправляет accept-call (подтверждает участие) — иначе сервер
                        // не ретранслирует answer от собеседника, и звонок висит в CONNECTING.
                        if (direction == re.pinok.data.model.CallDirection.OUTGOING) {
                            AppLog.i("CallScreen", "Исходящий: отправляю accept-call (подтверждение участия)")
                            signaling.acceptCall(isVideo = false)
                        }
                    } catch (e: Exception) {
                        AppLog.w("CallScreen", "WS connection TURN parse error: ${e.message}")
                    }
                }
                "topology-changed" -> {
                    // #CALLS-OUTGOING: сервер указал offerTo=[participantId] — кому слать offer.
                    // #CALLS-FIX (2026-08-24): после переключения topology (напр. DIRECT→SERVER)
                    // сервер ждёт ПОВТОРНУЮ отправку offer в новом режиме — даже если
                    // participantId уже установлен (иначе answer не ретранслируется).
                    val topology = msg.json.get("topology")?.takeIf { it.isJsonPrimitive }?.asString
                    val offerTo = msg.json.get("offerTo")
                        ?.takeIf { it.isJsonArray }?.asJsonArray
                    AppLog.i("CallScreen", "topology-changed: topology=$topology offerTo=$offerTo")
                    if (offerTo != null && offerTo.size() > 0) {
                        val pid = offerTo[0].takeIf { it.isJsonPrimitive }?.asLong
                        if (pid != null && pid > 0L) {
                            if (remoteParticipantId.value == null) {
                                remoteParticipantId.value = pid.toString()
                            }
                            val pidStr = pid.toString()
                            // Повторно отправляем offer (кэшированный SDP или из engine) + кандидаты.
                            var sdpToSend: org.webrtc.SessionDescription? = pendingLocalSdp.value
                            if (sdpToSend != null) {
                                pendingLocalSdp.value = null
                            } else {
                                // Кэш пуст (offer уже ушёл ранее) — берём последний из engine.
                                sdpToSend = engine.lastLocalSdp()
                                AppLog.w("CallScreen", "topology-changed: кэш пуст — беру offer из engine (${sdpToSend?.type})")
                            }
                            if (sdpToSend != null) {
                                signaling.sendSdp(pidStr, sdpToSend.description, sdpToSend.type.name.lowercase())
                                AppLog.i("CallScreen", "offer отправлен повторно (topology=$topology, $pidStr)")
                            }
                            pendingLocalCandidates.value.forEach { c ->
                                signaling.sendCandidate(pidStr, c.sdpMid, c.sdpMLineIndex, c.sdp)
                            }
                            pendingLocalCandidates.value.clear()
                        }
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_REMOTE_HANGUP -> {
                    // #CALLS-FIX (2026-08-24): собеседник завершил звонок (повесил трубку).
                    // Сервер прислал hangup/call-ended/participant-left — переводим в ENDED.
                    AppLog.i("CallScreen", "Собеседник завершил звонок: ${msg.json}")
                    engine.endCall()
                    signaling.stop()
                    phase = CallPhase.ENDED
                }
                else -> { /* прочие события игнорируем */ }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.release()
            signaling.stop()
        }
    }

    val startTime = remember { System.currentTimeMillis() }
    LaunchedEffect(phase) {
        if (phase == CallPhase.ACTIVE) {
            kotlinx.coroutines.delay(1000)
            callDuration = (System.currentTimeMillis() - startTime) / 1000
        }
    }

    // #CALLS-OUT-FIX (2026-08-27): таймаут дозвона. Если за 45с answer не пришёл
    // (абонент офлайн/не отвечает) — рвём звонок, а не висим в «Звоним…» навсегда.
    LaunchedEffect(phase) {
        if (phase == CallPhase.RINGING && !incoming) {
            kotlinx.coroutines.delay(45_000L)
            if (phase == CallPhase.RINGING && !incoming) {
                AppLog.i("CallScreen", "Исходящий: 45с без ответа — завершаем (no answer)")
                noAnswer = true
                signaling.hangup("timeout")
                engine.endCall()
                signaling.stop()
                phase = CallPhase.ENDED
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF1A1A2E),
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(title.take(1).uppercase(), fontSize = 40.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Имя звонящего/собеседника — крупно (как в VK)
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Phase text (статус)
                Text(
                    text = when (phase) {
                        CallPhase.RINGING -> if (incoming) "Входящий звонок…" else "Звоним…"
                        CallPhase.CONNECTING -> "Соединение…"
                        CallPhase.ACTIVE -> formatDuration(callDuration)
                        CallPhase.ENDED -> if (noAnswer) "Абонент не отвечает" else "Звонок завершён"
                        CallPhase.FAILED -> failText ?: "Ошибка соединения"
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                if (phase == CallPhase.CONNECTING) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }

                Spacer(Modifier.height(48.dp))

                // Controls
                when (phase) {
                    CallPhase.RINGING -> {
                        // Входящий: [Отклонить] [Принять] — как в VK (vkuiButton modeTertiary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Отклонить (cancel_24, appearanceNegative)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        signaling.declineCall()
                                        signaling.stop()
                                        phase = CallPhase.ENDED
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935)),
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Отклонить", modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Отклонить", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            // Принять (phone_24, appearancePositive)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            val sk = app.ensureCallsSessionKey()
                                            if (sk != null) {
                                                withContext(Dispatchers.IO) {
                                                    app.apiClient.vchatJoinConversation(
                                                        activeCallId.value ?: "", sk, isVideo = false
                                                    )
                                                }
                                            }
                                        }
                                        engine.acceptCall(call)
                                        val offer = pendingOffer.value
                                        if (offer != null) {
                                            val type = if (offer.second == "offer") org.webrtc.SessionDescription.Type.OFFER
                                            else org.webrtc.SessionDescription.Type.ANSWER
                                            engine.setRemoteSdp(offer.first, type)
                                        }
                                        pendingCandidates.value.forEach { (mid, idx, cand) ->
                                            engine.addRemoteIceCandidate(mid, idx, cand)
                                        }
                                        pendingCandidates.value.clear()
                                        signaling.acceptCall(isVideo = false)
                                        phase = CallPhase.CONNECTING
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF43A047)),
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Принять", modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Принять", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                    }
                    CallPhase.ACTIVE, CallPhase.CONNECTING -> {
                        // Footer как в VK (mvk_calls_call_footer_*): микрофон, динамик, ссылка, завершить
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Вкл. микрофон" else "Микрофон",
                                color = if (isMuted) Color(0xFF616161) else Color(0xFF37474F),
                                onClick = { isMuted = !isMuted; engine.setMuted(isMuted) },
                            )
                            CallControlButton(
                                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                label = if (isSpeakerOn) "Динамик" else "Динамик",
                                color = if (isSpeakerOn) Color(0xFF43A047) else Color(0xFF37474F),
                                onClick = { isSpeakerOn = !isSpeakerOn; engine.setSpeakerOn(isSpeakerOn) },
                            )
                            CallControlButton(
                                icon = Icons.Default.Link,
                                label = "Ссылка",
                                color = Color(0xFF37474F),
                                onClick = {
                                    val cid = activeCallId.value
                                    if (cid.isNullOrBlank()) {
                                        android.widget.Toast.makeText(context, "Звонок ещё не создан", android.widget.Toast.LENGTH_SHORT).show()
                                        return@CallControlButton
                                    }
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        try {
                                            val snap = app.prefs.data.first()
                                            val sk = snap.callsSessionKey
                                            val link = if (sk.isNotBlank()) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    app.apiClient.vchatCreateJoinLink(cid, sk)
                                                }
                                            } else null
                                            val full = if (!link.isNullOrBlank()) "https://vk.ru/call/join/$link" else null
                                            if (full != null) {
                                                val clip = android.content.ClipData.newPlainText("join_link", full)
                                                context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Не удалось создать ссылку", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                            CallControlButton(
                                icon = Icons.Default.CallEnd,
                                label = "Завершить",
                                color = Color(0xFFE53935),
                                onClick = {
                                    signaling.hangup()
                                    engine.endCall()
                                    signaling.stop()
                                    phase = CallPhase.ENDED
                                    onNavigateBack()
                                },
                            )
                        }
                    }
                    CallPhase.ENDED, CallPhase.FAILED -> {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF333333)),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}