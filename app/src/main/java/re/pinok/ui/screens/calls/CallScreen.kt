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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    // #CALLS-NAME-FIX (2026-08-29): имя/аватар — state, а не val: они могут подтянуться
    // ПОСЛЕ навигации (refreshIncomingCaller в SovaApp — async, навигация срабатывает
    // мгновенно по payload). Если пришли пустыми/заглушкой — сам подтягиваем через
    // messagesGetCurrentCalls → usersGetByIds.
    var peerName by remember { mutableStateOf(title) }
    var peerPhoto by remember { mutableStateOf<String?>(photo) }
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
    // #CALLS-IN-FIX (2026-08-29, лог 20:54): входящий звонок завис в «Соединение…»:
    // vchat.getConversationParams висел 45с (readTimeout long-poll), а кнопка
    // «Принять» сработала сразу — accept-call ушёл в никуда (сигналинг не был
    // поднят), offer не пришёл. Теперь «Принять» ждёт готовности params через
    // этот deferred, поднимает сигналинг и шлёт accept только при открытом WS.
    val incomingParamsDeferred = remember {
        kotlinx.coroutines.CompletableDeferred<re.pinok.media.ConversationParamsDecoder.Params?>()
    }
    // #CALLS: participantId собеседника (из offer/candidate в сигналинге).
    // Нужен для отправки answer SDP и ICE candidates обратно.
    val remoteParticipantId = remember { mutableStateOf<String?>(null) }
    // #CALLS-IN-OFFER (2026-08-29, лог 21:26): флаги «offer получен» / «answer отправлен» —
    // для watchdog'а входящего и экранной диагностики. Раньше offer кэшировался в
    // pendingOffer и читался ТОЛЬКО кнопкой «Принять»: offer, прилетевший в момент
    // нажатия, затирался (pendingOffer.value = null) — answer не создавался вовсе,
    // звонящий повисал и сбрасывал звонок (remote-hangup через ~37с).
    val offerReceived = remember { mutableStateOf(false) }
    val answerSent = remember { mutableStateOf(false) }
    // #CALLS-ICE-REANSWER (2026-08-29, лог 22:29): флаг «ICE подключён» — блокирует
    // ретрансмит answer после установления связи (дубликат answer после stable
    // может уронить setRemoteDescription у собеседника).
    val iceConnected = remember { mutableStateOf(false) }
    // #CALLS-ICE-REANSWER: счётчик повторных answer + время последней отправки
    // (анти-спам: не чаще раза в 3с, максимум 4 повторов за звонок).
    var reanswerCount by remember { mutableStateOf(0) }
    var lastAnswerSentAt by remember { mutableStateOf(0L) }
    // #CALLS-ACK-REOFFER (2026-08-29): diag-строка ретраев answer ("answer×N")
    var diagAnswer by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    // #CALLS-ACK-REOFFER (2026-08-29): надёжная отправка answer. Раньше answerSent
    // ставился ДО отправки, а send() при закрытом WS молча отбрасывал команду —
    // answer терялся навсегда, звонящий ждал до сброса. Ретраим до 15с;
    // answerSent=true ТОЛЬКО на успешной отправке.
    val sendAnswerReliably: (String, org.webrtc.SessionDescription) -> Unit = { pid, sdp ->
        uiScope.launch {
            for (attempt in 1..30) {
                val ok = signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
                if (ok) {
                    answerSent.value = true
                    lastAnswerSentAt = System.currentTimeMillis()
                    if (attempt > 1) diagAnswer = "answer×$attempt"
                    AppLog.i("CallScreen", "ANSWER отправлен (участник=$pid, попытка $attempt)")
                    return@launch
                }
                AppLog.w("CallScreen", "ANSWER не ушёл (попытка $attempt, WS=${signaling.wsState()}) — ретрай 500мс")
                kotlinx.coroutines.delay(500)
            }
            AppLog.e("CallScreen", "ANSWER не отправлен за 15с — WS мёртв")
            failText = "Не удалось отправить ответ (сеть)"
            phase = CallPhase.FAILED
        }
    }
    // #CALLS-IN-OFFER: параметры последнего signaling.start — для nudge-перерегистрации
    // WS (входящий: если offer не пришёл, перерегистрация заставит сервер снова
    // разослать registered-peer → звонящий переотправит offer — семантика §8.3 звонки.md).
    // Аргумент reAccept (#CALLS-ACK-REOFFER): после перерегистрации ЗАНОВО отправить
    // accept-call — новый WS-peer может считаться сервером «не принявшим», и его
    // transmit-data не будет ретранслироваться, пока он не подтвердит участие.
    var sigRestart: ((Boolean) -> Unit)? by remember { mutableStateOf(null) }
    // #CALLS-ACK-REOFFER: сигналинг поднялся (триггер RINGING-nudge)
    var sigStarted by remember { mutableStateOf(false) }
    // #CALLS-OUTGOING: кэш нашего offer/candidates до получения participantId.
    val pendingLocalSdp = remember { mutableStateOf<org.webrtc.SessionDescription?>(null) }
    val pendingLocalCandidates = remember { mutableStateOf<MutableList<org.webrtc.IceCandidate>>(mutableListOf()) }
    // #CALLS-REOFFER (2026-08-29): флаг «answer уже получен» + ПОЛНЫЙ кэш локальных
    // ICE-кандидатов за звонок. Offer/кандидаты, отправленные ДО registered-peer
    // собеседника, сервер ВЫБРАСЫВАЕТ (у него ещё нет активного WS-peer'а) —
    // поэтому на registered-peer переотправляем offer + все кандидаты заново.
    val answerReceived = remember { mutableStateOf(false) }
    val allLocalCandidates = remember { mutableStateOf(mutableListOf<org.webrtc.IceCandidate>()) }
    var reofferCount by remember { mutableStateOf(0) }
    var diagReoffer by remember { mutableStateOf("") }
    // #CALLS: call_id активного звонка — для кнопки «Ссылка» (vchat.createJoinLink).
    val activeCallId = remember { mutableStateOf<String?>(null) }
    // #CALLS-DIAG (2026-08-29): экранная диагностика — состояние WS/PC/ICE и последнее
    // событие сигналинга. Видна при CONNECTING/FAILED/ENDED: пользователь шлёт скриншот
    // вместо logcat — сразу видно, где затык (WS не открылся / PC не создан / ICE FAILED).
    var diagWs by remember { mutableStateOf("—") }
    var diagEvent by remember { mutableStateOf("—") }
    var diagIce by remember { mutableStateOf("—") }
    var diagPc by remember { mutableStateOf("PC нет") }
    var diagPid by remember { mutableStateOf("участник —") }
    // #CALLS-ICE-STATS-UI (2026-08-30): снимок ICE (типы кандидатов + агрегат
    // candidate-pair reqS/resR/reqR) — обновляется поллингом из iceUiSnapshot().
    var diagStats by remember { mutableStateOf("") }
    // #CALLS-RX-DEBUG (2026-08-30, скриншот 00:17): скриншот показал пар=0/reqS=0
    // при 10 локальных кандидатах — кандидаты собеседника НЕ ДОЕХАЛИ. Но на экране
    // не было видно, какие команды сигналинга мы вообще получали. Теперь считаем
    // все входящие команды по именам: строка «Принято: candidate×3, connection×2…»
    // покажет, приходил ли «candidate» и что ещё приходит от сервера/собеседника.
    val rxCommands = remember { mutableStateMapOf<String, Int>() }
    // #CALLS-ZOMBIE (2026-08-29, скриншот 23:45): счётчик ПОДРЯД идущих ошибок сервера
    // (type:"error" на наши transmit-data). Сервер отвергает данные, когда разговор
    // уже мёртв (собеседник вышел / conversation закрыта). Экран обязан это заметить.
    var srvErrCount by remember { mutableStateOf(0) }

    val engine = remember {
        WebRtcEngine(
            context = context,
            onCallPhaseChanged = {
                phase = it
                // #CALLS-ICE-REANSWER: ICE CONNECTED — дальше answer не повторяем.
                if (it == CallPhase.ACTIVE) iceConnected.value = true
                // #CALLS-ICE-WATCHDOG (2026-08-29): ICE FAILED без установленного failText
                // показывал безликое «Ошибка соединения», а звонящий с запущенным таймером
                // оставался висеть навсегда (никто не клал трубку). Теперь: причина на экране.
                if (it == CallPhase.FAILED && failText == null) {
                    failText = if (incoming) "Медиа-соединение не установлено (ICE)" else "Не удалось установить соединение (ICE)"
                }
            },
            onLocalSdpReady = { sdp ->
                // #CALLS: отправляем наш SDP (offer для исходящего, answer для входящего).
                // Если participantId ещё неизвестен (исходящий: приходит из connection
                // ПОСЛЕ createOffer) — кэшируем и отправим при получении participantId.
                // #CALLS-ACK-REOFFER: answer отправляется надёжно (ретраи), offer —
                // обычным путём (его надёжность обеспечивает reoffer-механизм).
                val pid = remoteParticipantId.value
                if (pid != null) {
                    AppLog.i("CallScreen", "sending local SDP type=${sdp.type} to participant=$pid")
                    AppLog.i("CallScreen", "FULL_LOCAL_${sdp.type} SDP:\n${sdp.description}")
                    if (sdp.type == SessionDescription.Type.ANSWER) {
                        sendAnswerReliably(pid, sdp)
                    } else {
                        signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
                    }
                } else {
                    pendingLocalSdp.value = sdp
                    AppLog.w("CallScreen", "local SDP готов (${sdp.type}), participantId неизвестен — кэшируем")
                }
            },
            onIceCandidateReady = { candidate ->
                // #CALLS: отправляем ICE candidate собеседнику.
                // #CALLS-REOFFER: каждый локальный кандидат копится в allLocalCandidates
                // за весь звонок — пригодится при повторной отправке offer (registered-peer
                // / watchdog): ранее отправленные кандидаты сервер мог выбросить вместе
                // с первым offer'ом.
                allLocalCandidates.value.add(candidate)
                val pid = remoteParticipantId.value
                if (pid != null) {
                    signaling.sendCandidate(pid, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                } else {
                    pendingLocalCandidates.value.add(candidate)
                }
            },
            onIceStateChanged = { diagIce = it },
        )
    }

    // #CALLS-ACK-REOFFER (2026-08-29): флаш кэша, когда participantId стал известен
    // ПОЗЖЕ готовности SDP/кандидатов (offer пришёл без participantId, pid вернули
    // последующие candidate/connection). Раньше кэшированный answer оставался в кэше
    // навсегда — answer не уходил, звонящий сбрасывал звонок.
    val flushPendingLocal: (String) -> Unit = { pid ->
        val sdp = pendingLocalSdp.value
        if (sdp != null) {
            pendingLocalSdp.value = null
            if (sdp.type == SessionDescription.Type.ANSWER) {
                sendAnswerReliably(pid, sdp)
            } else {
                signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
            }
            AppLog.i("CallScreen", "кэшированный ${sdp.type} отправлен (участник=$pid)")
        }
        val cachedCnt = pendingLocalCandidates.value.size
        if (cachedCnt > 0) {
            pendingLocalCandidates.value.forEach { c ->
                signaling.sendCandidate(pid, c.sdpMid, c.sdpMLineIndex, c.sdp)
            }
            pendingLocalCandidates.value.clear()
            AppLog.i("CallScreen", "кэшированные ICE ($cachedCnt) отправлены (участник=$pid)")
        }
    }

    // #CALLS-REOFFER (2026-08-29): повторная отправка offer + всех локальных ICE-кандидатов.
    // Лог 20:31: offer ушёл в 24.720, REGISTERED_PEER собеседника пришёл только в 25.316 —
    // сервер выбросил offer (некому доставлять), answer не пришёл вовсе, звонок висел
    // в «Соединение…». Эталон Chrome (calls-sdk, звонки.md §13) переотправляет offer
    // на каждый registered-peer, пока не получит answer — делаем то же самое.
    val doReoffer: (String) -> Unit = { reason ->
        if (direction != CallDirection.OUTGOING) {
            AppLog.i("CallScreen", "REOFFER пропущен ($reason): не исходящий")
        } else if (answerReceived.value) {
            AppLog.i("CallScreen", "REOFFER пропущен ($reason): answer уже получен")
        } else {
            val pid = remoteParticipantId.value
            val sdp = pendingLocalSdp.value ?: engine.lastLocalSdp()
            if (pid.isNullOrBlank() || sdp == null) {
                AppLog.w("CallScreen", "REOFFER невозможен ($reason): pid=$pid, sdp=${sdp != null}")
            } else {
                pendingLocalSdp.value = null
                signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
                allLocalCandidates.value.forEach { c ->
                    signaling.sendCandidate(pid, c.sdpMid, c.sdpMLineIndex, c.sdp)
                }
                reofferCount++
                diagReoffer = "offer×$reofferCount"
                AppLog.i("CallScreen", "REOFFER #$reofferCount ($reason): offer + ${allLocalCandidates.value.size} кандидатов → $pid")
            }
        }
    }

    // #CALLS-ICE-REANSWER (2026-08-29, лог 22:29): РЕТРАНСМИТ ANSWER для ВХОДЯЩЕГО.
    // Хронология лога 22:29: сигналинг полный (offer ✓, answer ✓ ack сервера, 12
    // кандидатов ✓), TURN-аллокация успешна (4 relay), но ICE 16с в CHECKING → FAILED
    // без единой пары; topology-changed → SERVER (offerTo:[]) через 10с; звонящий
    // (VK Desktop, WEB_TRANSPORT) сбросил через 40с. Раз relay↔relay не связался —
    // агент звонящего не шлёт проверки: первая копия answer им НЕ ПРИМЕНЕНА
    // (потеряна/просрочена: race с accepted-call, сброс состояния при SERVER-topology).
    // Для исходящих такой механизм есть (doReoffer), для входящих — НЕТ: answer
    // отправлялся ровно один раз. Повторяем answer + ВСЕ локальные кандидаты на
    // ключевые события, пока ICE не подключился.
    val doReanswer: (String) -> Unit = { reason ->
        if (direction != CallDirection.INCOMING) {
            AppLog.i("CallScreen", "REANSWER пропущен ($reason): не входящий")
        } else if (!answerSent.value) {
            AppLog.i("CallScreen", "REANSWER пропущен ($reason): answer ещё не отправлялся")
        } else if (iceConnected.value) {
            AppLog.i("CallScreen", "REANSWER пропущен ($reason): ICE уже подключён")
        } else if (reanswerCount >= 4) {
            AppLog.w("CallScreen", "REANSWER пропущен ($reason): лимит 4 повторов")
        } else {
            val pid = remoteParticipantId.value
            val sdp = engine.lastLocalSdp()
            if (pid.isNullOrBlank() || sdp == null || sdp.type != SessionDescription.Type.ANSWER) {
                AppLog.w("CallScreen", "REANSWER невозможен ($reason): pid=$pid, sdp=${sdp != null}")
            } else {
                val now = System.currentTimeMillis()
                if (now - lastAnswerSentAt < 3000L) {
                    AppLog.i("CallScreen", "REANSWER пропущен ($reason): <3с с последней отправки")
                } else {
                    lastAnswerSentAt = now
                    signaling.sendSdp(pid, sdp.description, sdp.type.name.lowercase())
                    allLocalCandidates.value.forEach { c ->
                        signaling.sendCandidate(pid, c.sdpMid, c.sdpMLineIndex, c.sdp)
                    }
                    reanswerCount++
                    diagAnswer = "ans×$reanswerCount"
                    AppLog.i("CallScreen", "REANSWER #$reanswerCount ($reason): answer + ${allLocalCandidates.value.size} кандидатов → $pid")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // #CALLS-LOG-MARK (2026-08-30): маркер начала звонка — по нему в экспортированном
        // логе мгновенно находится сегмент звонка (когда экранные флаги не видны/
        // не успевают — пользователь присылает лог вместо скриншота).
        AppLog.i(
            "CallScreen",
            "════════ CALL START: ${if (incoming) "входящий" else "исходящий"} peer=$peerId name=$peerName payload=${incomingPayload?.length ?: 0} ════════"
        )
        engine.initialize()
        // #CALLS-MIC-GUARD: запрашиваем микрофон до установки соединения.
        if (!micGranted) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        if (incoming) {
            // #CALLS: входящий звонок. Если есть payload — декодируем conversation
            // params и подключаемся к WebSocket-сигналингу (accept/decline).
            AppLog.i("CallScreen", "Incoming call, payload.len=${incomingPayload?.length ?: 0}")
            var params = incomingParams
            var callConvId: String? = null
            // #CALLS-IN-OFFER (2026-08-29): conversationId (call_id) нужен ВСЕГДА —
            // он идёт в WS URL (conversationId=), vchat.joinConversation и hangup-fallback.
            // Раньше call_id доставали только когда payload="-1": если payload содержал
            // params (events_queue), WS уходил с ПУСТЫМ conversationId — сервер мог не
            // считать нас полноценным peer'ом conversation → registered-peer звонящему
            // не уходил, его offer до нас не доходил, answer отправлять было не на что.
            try {
                val calls = app.apiClient.messagesGetCurrentCalls()
                AppLog.i("CallScreen", "messagesGetCurrentCalls: items=${calls.size}")
                calls.firstOrNull()?.let { call ->
                    val convId = call.get("conversation_id")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: call.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: call.get("call_id")?.takeIf { it.isJsonPrimitive }?.asString
                    callConvId = convId
                    AppLog.i("CallScreen", "current call: convId=$convId")
                }
            } catch (e: Exception) {
                AppLog.w("CallScreen", "getCurrentCalls error: ${e.message}")
            }
            if (params == null) {
                // payload = "-1" — полные conversation params нужно получить через
                // vchat API по conversationId (vchat.getConversationParams).
                AppLog.w("CallScreen", "payload не содержит conversation params — пробуем vchat API")
                try {
                    val conv = callConvId
                    if (!conv.isNullOrBlank()) {
                        // Полностью автоматическая цепочка (как браузер):
                        // session_key из prefs → vchat.getConversationParams,
                        // при 102 (session expired) — авто-получение свежего
                        // через get_anonym_token → auth.anonymLogin → повтор.
                        // См. SovaApp.getCallConversationParams.
                        val (sessionKey, vchatResp) = app.getCallConversationParams(conv)
                        AppLog.i("CallScreen", "getCallParams: sessionKey=${sessionKey?.take(12) ?: "null"}… vchat=${if (vchatResp != null) "OK" else "null"}")
                        if (vchatResp != null) {
                            params = re.pinok.media.ConversationParamsDecoder.decodeParamsJson(vchatResp)
                            if (params != null) {
                                AppLog.i("CallScreen",
                                    "vchat params: endpoint=${params.endpoint.take(40)}… token=${params.token.take(8)}…")
                            }
                        }
                    } else {
                        AppLog.w("CallScreen", "call_id не получен — vchat fallback невозможен")
                    }
                } catch (e: Exception) {
                    AppLog.e("CallScreen", "vchat fallback error", e)
                }
            }
            val resolvedParams = params
            if (resolvedParams == null) {
                AppLog.w("CallScreen", "Не удалось получить conversation params — звонок принять нельзя")
                // #CALLS-IN-FIX: сообщаем ошибку и ждущему «Принять» (иначе он ждал бы deferred вечно).
                failText = "Не удалось получить параметры звонка"
                incomingParamsDeferred.complete(null)
                if (phase == CallPhase.RINGING) phase = CallPhase.FAILED
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
                // #CALLS-ACK-REOFFER (2026-08-29): сохраняем параметры для nudge-перерегистрации WS
                // (watchdog входящего: offer не пришёл → переподключаем WS; reAccept=true —
                // после перерегистрации заново отправить accept-call).
                sigRestart = { reAccept ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        signaling.stop()
                        signaling.start(userId = uid, conversationId = convId, params = resolvedParams, peerId = peerId)
                        var waited = 0
                        while (!signaling.isWsReady() && waited < 10_000) {
                            kotlinx.coroutines.delay(250)
                            waited += 250
                        }
                        if (reAccept && signaling.isWsReady()) {
                            val ok = signaling.acceptCall(isVideo = false)
                            AppLog.i("CallScreen", "nudge: WS перерегистрирован, повторный accept-call ok=$ok")
                        }
                    }
                }
                sigStarted = true
                AppLog.i("CallScreen", "Signaling started — ждём accept/decline от пользователя")
                // #CALLS-IN-FIX: «Принять» ждёт эти params — сообщаем в самом конце,
                // когда activeCallId/ICE-серверы/сигналинг уже готовы.
                incomingParamsDeferred.complete(resolvedParams)
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
                    // #CALLS-OUT-DIAG (30.08, «звонок с пинок на официальный вк не проходит»):
                    // сверяем, что сервер ЗАРЕГИСТРИРОВАЛ звонок — если getCurrentCalls пуст,
                    // официальный клиент не получит push, не зазвонит и registered-peer
                    // не придёт никогда (45с → CANCELED «no answer» — это будет ВИДНО из лога).
                    try {
                        val calls = app.apiClient.messagesGetCurrentCalls()
                        AppLog.i("CallScreen", "OUTGOING-SETUP: callId=$callId, getCurrentCalls=${calls.size} шт.")
                    } catch (e: Exception) {
                        AppLog.w("CallScreen", "OUTGOING-SETUP: getCurrentCalls error: ${e.message}")
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
                        // #CALLS-OUT-DIAG: одна итоговая строка всей цепочки исходящего —
                        // callId (сообщения.startCall) / conv (startConversation→WS) / uid
                        // (okcdn) / turn — по ней в логе мгновенно видно, какое звено пропало.
                        AppLog.i("CallScreen", "OUTGOING-SETUP OK: callId=$callId conv=$wsConversationId uid=$uid peerId=$peerId turn=${params.turnServer?.urls?.firstOrNull() ?: "нет"}")
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
            // #CALLS-IN-OFFER: для входящего этот collect — ловушка: queuev4Client остаётся
            // подписан на очередь «calls» после любого исходящего звонка и при входящем
            // получает то же событие LP 115 — фаза прыгала RINGING→CONNECTING без нажатия
            // «Принять». Состояния входящего меняет только WS-сигналинг.
            // #CALLS-OUT-QUEUE-FIX (30.08, «звонок с пинок на официальный вк не проходит»):
            // для ИСХОДЯЩЕГО фазу из queuev4 больше НЕ меняем вовсе. Прежнее условие
            // (code == 115L || queueId == "calls") ловило и СОБСТВЕННОЕ событие созданного
            // нами звонка — нашу же очередь «calls» сервер доставляет и звонящему. Фаза
            // прыгала RINGING→CONNECTING в момент НАБОРА: экран показывал «Соединение…»
            // вместо «Звоним…», и запускались watchdog'и CONNECTING — 60с → FAILED
            // «Не удалось установить соединение» ДО того, как собеседник вообще успевал
            // взять трубку (у эталона между registered-peer и accepted-call бывает 7с+,
            // а взять трубку — ещё дольше). Авторитетный сигнал принятия у исходящего —
            // notification accepted-call из сигналинга (доказан логом 20:31:
            // registered-peer 25.316 → accepted-call 32.487) — переключает фазу он
            // (#CALLS-OUT-ACCEPTED-PHASE).
            if (direction == CallDirection.OUTGOING) {
                AppLog.i("CallScreen",
                    "queuev4: событие при исходящем (code=$code queue=${ev.queueId}) — фазу не меняем, ждём accepted-call")
            }
        }
    }

    // #CALLS-DIAG: раз в секунду обновляем тех-строку (WS/PC/участник).
    LaunchedEffect(Unit) {
        while (true) {
            diagWs = signaling.wsState()
            diagPc = if (engine.hasPeerConnection()) "PC есть" else "PC нет"
            diagPid = remoteParticipantId.value?.let { "участник $it" } ?: "участник —"
            kotlinx.coroutines.delay(1_000)
        }
    }

    // #CALLS: слушаем WebSocket-сигналинг — SDP/ICE для WebRTC.
    LaunchedEffect(Unit) {
        signaling.messages.collect { msg ->
            AppLog.i("CallScreen", "signaling: command=${msg.command} sdp=${msg.sdp?.take(40)} cand=${msg.candidate?.take(40)}")
            diagEvent = msg.command
            // #CALLS-RX-DEBUG: считаем входящие команды для экранной строки «Принято:».
            rxCommands[msg.command] = (rxCommands[msg.command] ?: 0) + 1
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
                        // #CALLS-REOFFER: повторный answer (собеседник ответил вторым
                        // устройством — у него может быть WEB+ANDROID peer) игнорируем:
                        // PC уже в stable, второй answer уронит setRemoteDescription.
                        // Первый answer выигрывает.
                        if (!isOffer && answerReceived.value) {
                            AppLog.w("CallScreen", "повторный answer проигнорирован (уже применён)")
                            return@collect
                        }
                        // #CALLS-IN-OFFER/#CALLS-ACK-REOFFER: повторный offer ПОСЛЕ отправки/получения
                        // answer игнорируем (звонящий мог не увидеть наш answer и переотправить offer;
                        // повторный setRemoteDescription(offer) в have-local-offer упал бы).
                        if (isOffer && (answerReceived.value || answerSent.value)) {
                            AppLog.w("CallScreen", "повторный offer проигнорирован (answer уже отправлен/получен)")
                            return@collect
                        }
                        if (isOffer) offerReceived.value = true
                        // #CALLS-IN-OFFER: решение «применить сейчас или буферизовать до
                        // accept» принял на себя движок (setRemoteSdp: PC нет → буфер,
                        // применится в acceptCall; PC есть → сразу). Гонки с кнопкой
                        // «Принять» больше нет.
                        AppLog.i("CallScreen", "remote ${type.name.lowercase()} → engine (участник=${remoteParticipantId.value}, len=${sdp.length})")
                        engine.setRemoteSdp(sdp, type)
                        if (!isOffer) {
                            answerReceived.value = true
                            phase = CallPhase.CONNECTING
                        }
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_CANDIDATE -> {
                    val pidBefore = remoteParticipantId.value
                    msg.participantId?.let { remoteParticipantId.value = it }
                    // #CALLS-ACK-REOFFER: pid стал известен только сейчас (offer пришёл без
                    // participantId, pid вернули кандидаты) — флашим кэшированные SDP/ICE,
                    // иначе кэшированный answer остаётся в кэше навсегда.
                    if (pidBefore == null && remoteParticipantId.value != null) {
                        flushPendingLocal(remoteParticipantId.value!!)
                    }
                    msg.candidate?.let { c ->
                        // #CALLS-IN-OFFER: движок сам буферизует кандидата, если
                        // remoteDescription ещё не установлен (pendingRemoteIce → drain
                        // после setRemoteSdp) — кэш «до accept» в UI больше не нужен.
                        engine.addRemoteIceCandidate(msg.candidateSdpMid, msg.candidateSdpMLineIndex ?: 0, c)
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_CALL_ERROR -> {
                    AppLog.w("CallScreen", "Signaling error: ${msg.json}")
                    // #CALLS-DIAG: показываем причину прямо на экране, не только в логе.
                    val errText = msg.json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: msg.json.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    failText = if (errText != null) "Ошибка сигналинга: $errText" else "Ошибка сигналинга"
                    phase = CallPhase.FAILED
                }
                "response", "error" -> {
                    // #CALLS-ACK-REOFFER (2026-08-29): ack сервера на наши команды.
                    // participantIds — кому реально доставлен transmit-data; error — сервер
                    // отверг команду (раньше всё это молча игнорировалось).
                    val resp = msg.json.get("response")?.takeIf { it.isJsonPrimitive }?.asString
                    val pids = msg.json.get("participantIds")?.takeIf { it.isJsonArray }?.asJsonArray?.toString()
                    AppLog.i("CallScreen", "сервер ack: ${msg.command} response=$resp participantIds=$pids")
                    diagEvent = if (msg.command == "error") "ошибка сервера" else "ack:$resp"
                    // #CALLS-ZOMBIE (2026-08-29, скриншот 23:45): «ошибка сервера» при
                    // входящем с отправленным answer и без ICE = разговор мёртв
                    // (собеседник вышел — его participant больше нет; скриншот: ans×4,
                    // «на обратной стороне трубку повесили», а экран висел «Соединение…»).
                    // 2 ошибки подряд → терминалим звонок сами, а не спамим в никуда.
                    if (msg.command == "error") {
                        srvErrCount++
                        AppLog.w("CallScreen", "сервер отверг команду (подряд: $srvErrCount): ${msg.json}")
                        if (incoming && answerSent.value && !iceConnected.value &&
                            phase == CallPhase.CONNECTING && srvErrCount >= 2
                        ) {
                            AppLog.w("CallScreen", "ZOMBIE: 2+ ошибки сервера подряд в CONNECTING без ICE — разговор мёртв, терминалим")
                            failText = "Собеседник завершил вызов (данные не доставляются)"
                            signaling.hangup("timeout")
                            engine.endCall()
                            signaling.stop()
                            phase = CallPhase.FAILED
                        }
                    } else {
                        srvErrCount = 0
                    }
                }
                re.pinok.realtime.CallSignalingClient.CMD_ACCEPTED_CALL,
                re.pinok.realtime.CallSignalingClient.CMD_ACCEPTED_OUTGOING -> {
                    // #CALLS-ACK-REOFFER (2026-08-29): собеседник ПРИНЯЛ звонок. Единственный
                    // гарантированный момент, когда вызываемый готов принимать transmit-data
                    // (лог 20:31: registered-peer 25.316 → accepted-call 32.487). Если сервер
                    // ретранслирует transmit-data только «принявшим» peer'ам, reoffer на
                    // registered-peer тоже выбрасывался — переотправляем offer ещё раз.
                    // Для входящего это эхо нашего собственного accept — doReoffer пропустит.
                    AppLog.i("CallScreen", "accepted-call: собеседник принял звонок — reoffer")
                    // #CALLS-OUT-ACCEPTED-PHASE: для исходящего это ЕДИНСТВЕННЫЙ
                    // авторитетный момент «собеседник взял трубку» — только здесь
                    // RINGING→CONNECTING (queuev4 больше фазу не меняет,
                    // #CALLS-OUT-QUEUE-FIX). Для входящего это эхо НАШЕГО accept —
                    // фаза уже CONNECTING, не трогаем.
                    if (direction == CallDirection.OUTGOING && phase == CallPhase.RINGING) {
                        phase = CallPhase.CONNECTING
                    }
                    if (remoteParticipantId.value == null) {
                        msg.participantId?.let { remoteParticipantId.value = it }
                    }
                    doReoffer("accepted-call")
                    // #CALLS-ICE-REANSWER: для входящего это может быть эхо НАШЕГО accept,
                    // но также — уведомление, что звонящий (пере)подтвердил участие
                    // (напр. после nudge-перерегистрации). Если его агент пропустил первую
                    // копию answer — повторяем.
                    doReanswer("accepted-call")
                }
                re.pinok.realtime.CallSignalingClient.CMD_REGISTERED_PEER -> {
                    // #CALLS-OUTGOING (2026-08-24, подтверждено реальным WS):
                    // сервер шлёт { notification:"registered-peer", participantId:<id> }
                    // когда собеседник зарегистрировался в conversation. participantId
                    // здесь — ID участника, НА КОТОРЫЙ слать offer/ICE (не наш WS peerId).
                    val pid = msg.participantId
                    if (!pid.isNullOrBlank()) {
                        if (remoteParticipantId.value == null) {
                            remoteParticipantId.value = pid
                            AppLog.i("CallScreen", "registered-peer: participantId=$pid")
                        }
                        // #CALLS-REOFFER (2026-08-29): переотправляем offer+кандидаты
                        // ВСЕГДА, а не только когда participantId узнали впервые.
                        // Раньше условие «remoteParticipantId == null» почти всегда ложно
                        // (pid заполняется из connection.participants за секунду ДО
                        // registered-peer) — переотправка не срабатывала никогда.
                        doReoffer("registered-peer")
                        // #CALLS-ICE-REANSWER: звонящий (пере)зарегистрировался — возможно,
                        // его клиент перезапустился/сбросил состояние: повторяем answer.
                        doReanswer("registered-peer")
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
                                    // #CALLS-ACK-REOFFER: флаш кэша (offer/answer + кандидаты)
                                    flushPendingLocal(pId.toString())
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
                    // #CALLS-ICE-REANSWER (2026-08-29, лог 22:29): topology-changed → SERVER
                    // приходит через ~10с после answer, если сервер не видит медиа-ноги
                    // (все три лога входящих: 21:26, 21:44, 22:29). offerTo пуст — но звонящий
                    // при смене topology может сбросить своё SDP-состояние: повторяем answer
                    // + кандидатов, чтобы он мог пересобрать соединение.
                    if (topology == "SERVER") {
                        doReanswer("topology-SERVER")
                        if (direction == CallDirection.OUTGOING && (offerTo == null || offerTo.size() == 0)) {
                            // #CALLS-FIX (2026-08-24): сервер ждёт повторную отправку offer
                            // в новом режиме даже без offerTo (звонки.md §11).
                            doReoffer("topology-SERVER")
                        }
                    }
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

    // #CALLS-LOG-MARK (2026-08-30): время старта экрана — для длительности в CALL END.
    val startTime = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        onDispose {
            // #CALLS-LOG-MARK: маркер конца звонка — сегмент в логе = между START и END.
            AppLog.i(
                "CallScreen",
                "════════ CALL END: phase=$phase dur=${(System.currentTimeMillis() - startTime) / 1000}с srvErr=$srvErrCount offer=${offerReceived.value} answer=${answerSent.value} ice=${iceConnected.value} ════════"
            )
            engine.release()
            signaling.stop()
        }
    }

    // #CALLS-NAME-FIX (2026-08-29): самостоятельная подтяжка имени/аватара звонящего.
    // Лог 22:29: экран открылся с заглушкой «Входящий звонок» — refreshIncomingCaller
    // не успел (гонка с навигацией) либо молча не нашёл caller_id. Дублируем логику
    // на экране и ЛОГИРУЕМ результат (раньше отказ был невидим).
    LaunchedEffect(incoming) {
        if (!incoming) return@LaunchedEffect
        val placeholder = peerName.isBlank() || peerName == "Входящий звонок" || peerName == "Звонок"
        if (!placeholder && !peerPhoto.isNullOrBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(300) // даём шанс refreshIncomingCaller опередить
        try {
            val fetched = withContext(Dispatchers.IO) {
                val cur = app.apiClient.messagesGetCurrentCalls().firstOrNull()
                val cid = cur?.get("caller_id")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asLong ?: 0L
                if (cid <= 0L) null else cid to app.apiClient.usersGetByIds(listOf(cid))[cid]
            }
            if (fetched == null) {
                AppLog.w("CallScreen", "CALLER_INFO: не получен (нет активного звонка/caller_id=0)")
            } else {
                val (cid, profile) = fetched
                AppLog.i("CallScreen", "CALLER_INFO: id=$cid profile=${if (profile != null) "OK" else "нет"}")
                if (profile != null) {
                    val nm = (profile.firstName + " " + profile.lastName).trim()
                    if (nm.isNotBlank()) peerName = nm
                    val ph = profile.photo100
                    if (!ph.isNullOrBlank()) peerPhoto = ph
                }
            }
        } catch (e: Exception) {
            AppLog.w("CallScreen", "CALLER_INFO error: ${e.message}")
        }
    }

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

    // #CALLS-REOFFER (2026-08-29): watchdog CONNECTING для исходящего. Раньше при
    // переходе в CONNECTING (собеседник принял) таймаутов не оставалось вовсе —
    // если answer потерялся, экран висел «Соединение…» вечно. Теперь: 20с без answer
    // → последний повторный offer; 60с → рвём с понятной ошибкой.
    LaunchedEffect(phase) {
        if (phase == CallPhase.CONNECTING && !incoming) {
            kotlinx.coroutines.delay(20_000L)
            if (phase == CallPhase.CONNECTING && !incoming && !answerReceived.value) {
                AppLog.i("CallScreen", "Watchdog: 20с в CONNECTING без answer — повторный offer")
                doReoffer("watchdog-20s")
            }
            kotlinx.coroutines.delay(40_000L)
            if (phase == CallPhase.CONNECTING && !incoming && !answerReceived.value) {
                AppLog.w("CallScreen", "Watchdog: 60с в CONNECTING без answer — обрываем звонок")
                failText = "Не удалось установить соединение"
                signaling.hangup("timeout")
                engine.endCall()
                signaling.stop()
                phase = CallPhase.FAILED
            }
        }
    }

    // #CALLS-IN-OFFER (2026-08-29, лог 21:26): watchdog CONNECTING для ВХОДЯЩЕГО.
    // Раньше при потере offer (сервер выбросил transmit-data до нашей регистрации /
    // регистрация не дошла до звонящего) мы молча висели «Соединение…», пока звонящий
    // сам не сбрасывал звонок (~37с, remote-hangup). Теперь:
    //  8с без offer → nudge: перерегистрация WS (сервер снова разошлёт registered-peer,
    //                  звонящий переотправит offer — семантика §8.3 звонки.md);
    // 20с без offer → warn в лог;
    // 45с без answer → обрываем сами с понятной ошибкой (не ждём remote-hangup).
    var inNudgeDone by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase == CallPhase.CONNECTING && incoming) {
            kotlinx.coroutines.delay(8_000L)
            if (phase == CallPhase.CONNECTING && incoming && !engine.hasRemoteDescription() && !inNudgeDone) {
                inNudgeDone = true
                diagReoffer = "nudge WS"
                AppLog.w("CallScreen", "IN-Watchdog: 8с без offer — перерегистрация WS + повторный accept (nudge)")
                sigRestart?.invoke(true)
            }
            kotlinx.coroutines.delay(12_000L)
            if (phase == CallPhase.CONNECTING && incoming && !engine.hasRemoteDescription()) {
                AppLog.w("CallScreen", "IN-Watchdog: 20с — offer так и не получен")
            }
            kotlinx.coroutines.delay(25_000L)
            if (phase == CallPhase.CONNECTING && incoming && !answerSent.value) {
                AppLog.w("CallScreen", "IN-Watchdog: 45с без answer — обрываем звонок")
                failText = when {
                    !engine.hasRemoteDescription() -> "Данные звонка не получены (offer не пришёл)"
                    else -> "Не удалось отправить ответ (сеть)"
                }
                signaling.hangup("timeout")
                engine.endCall()
                signaling.stop()
                phase = CallPhase.FAILED
            }
        }
    }

    // #CALLS-ACK-REOFFER (2026-08-29): nudge ещё на этапе RINGING (до «Принять»).
    // offer звонящего, отправленный до нашей регистрации, сервер выбрасывает; если
    // звонящий сам не переотправляет offer — перерегистрация WS порождает
    // registered-peer → звонящий переотправит offer, и он буферизуется в движке
    // ещё ДО нажатия «Принять» (к моменту accept ответ создаётся мгновенно).
    var inRingNudgeDone by remember { mutableStateOf(false) }
    LaunchedEffect(sigStarted) {
        if (!sigStarted || !incoming) return@LaunchedEffect
        kotlinx.coroutines.delay(7_000L)
        if (incoming && phase == CallPhase.RINGING && !engine.hasRemoteDescription() && !inRingNudgeDone) {
            inRingNudgeDone = true
            diagReoffer = "nudge WS (ring)"
            AppLog.w("CallScreen", "IN-Watchdog: 7с в RINGING без offer — перерегистрация WS (nudge)")
            sigRestart?.invoke(false)
        }
    }

    // #CALLS-ICE-WATCHDOG (2026-08-29, симптом «у звонящего таймер идёт, а PinoK не
    // поднимает трубку»): accept/join уходят (сервер отмечает звонок отвечённым —
    // у звонящего стартует таймер), но МЕДИА (ICE) на нашей стороне не подключается —
    // и для состояния «answer отправлен, ICE не подключился» НЕ БЫЛО никакого
    // таймаута вовсе (старый IN-Watchdog на 45с срабатывал только при
    // !answerSent). Звонок висел «Соединение…» вечно, звонящий — со своим таймером.
    // Теперь: +15с → снимок ICE stats + doReanswer; +35с → ещё doReanswer;
    // +60с → сами кладём трубку (и у звонящего таймер остановится).
    LaunchedEffect(phase, answerSent.value) {
        if (!incoming || !answerSent.value || phase != CallPhase.CONNECTING) return@LaunchedEffect
        kotlinx.coroutines.delay(15_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "ICE-Watchdog: 15с после answer без ICE — stats + doReanswer")
            engine.dumpIceStatsNow()
            doReanswer("watchdog-ans-15s")
        }
        kotlinx.coroutines.delay(20_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "ICE-Watchdog: 35с после answer без ICE — повторный doReanswer")
            engine.dumpIceStatsNow()
            doReanswer("watchdog-ans-35s")
        }
        kotlinx.coroutines.delay(25_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "ICE-Watchdog: 60с после answer без ICE — обрываем звонок")
            engine.dumpIceStatsNow()
            failText = "Медиа-соединение не установлено (ICE)"
            signaling.hangup("timeout")
            engine.endCall()
            signaling.stop()
            phase = CallPhase.FAILED
        }
    }

    // #CALLS-ICE-WATCHDOG: ICE FAILED при входящем с уже отправленным answer —
    // до 2 попыток восстановления (reanswer заставляет собеседника пересобрать
    // соединение — его агент начинает проверки, наши получают ответы), затем
    // hangup, чтобы звонящий не висел с идущим таймером вечно.
    var iceFailRetries by remember { mutableStateOf(0) }
    LaunchedEffect(phase) {
        if (phase != CallPhase.FAILED || !incoming || !answerSent.value || iceConnected.value) return@LaunchedEffect
        // #CALLS-ZOMBIE: при 2+ ошибках сервера подряд разговор мёртв — ретраить нельзя
        // (иначе FAILED-retry «оживит» зомби обратно в CONNECTING и цикл повторится).
        if (iceFailRetries < 2 && srvErrCount < 2) {
            iceFailRetries++
            AppLog.w("CallScreen", "ICE FAILED (входящий): попытка восстановления #$iceFailRetries — doReanswer + stats")
            engine.dumpIceStatsNow()
            doReanswer("ice-failed")
            failText = null
            phase = CallPhase.CONNECTING
        } else {
            AppLog.w("CallScreen", "ICE FAILED (входящий): ретраи исчерпаны — hangup")
            signaling.hangup("timeout")
            engine.endCall()
            signaling.stop()
        }
    }

    // #CALLS-ICE-STATS-UI (2026-08-30, скриншот «ICE FAILED • ans×2 — трубка не
    // поднимается с обоих сторон»): симметричный watchdog для ИСХОДЯЩЕГО звонка.
    // Раньше у звонящей стороны не было НИКАКИХ таймаутов на «offer ушёл, ICE не
    // подключился» — если собеседник молчит по медиа, исходящий висел вечно.
    // Ретраить offer со звонящей стороны опасно (внезапный re-offer может сломать
    // собеседника) — поэтому только снимки статистики (15с/35с) и терминация на 45с.
    LaunchedEffect(phase) {
        if (incoming || phase != CallPhase.CONNECTING) return@LaunchedEffect
        kotlinx.coroutines.delay(15_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "OUT-Watchdog: 15с после offer без ICE — снимок stats")
            engine.dumpIceStatsNow()
        }
        kotlinx.coroutines.delay(20_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "OUT-Watchdog: 35с без ICE — повторный снимок stats")
            engine.dumpIceStatsNow()
        }
        kotlinx.coroutines.delay(10_000L)
        if (phase == CallPhase.CONNECTING && !iceConnected.value) {
            AppLog.w("CallScreen", "OUT-Watchdog: 45с без ICE — обрываем звонок")
            engine.dumpIceStatsNow()
            failText = "Не удалось установить соединение"
            signaling.hangup("timeout")
            engine.endCall()
            signaling.stop()
            phase = CallPhase.FAILED
        }
    }

    // #CALLS-ICE-STATS-UI: поллинг снимка ICE раз в секунду, пока звонок не активен.
    // (RINGING тоже обновляем — diag-блок с 00:17 виден и там; собранных кандидатов
    // до accept не будет, но «прин:0 sdpR:-» сразу покажет, что PC ещё не создан.)
    // dumpIceStatsNow() на watchdog'ах обновляет агрегат асинхронно (getStats-callback) —
    // поллинг гарантирует появление статистики на экране без доп. recompose-крючков.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            if (phase == CallPhase.ACTIVE) continue
            diagStats = engine.iceUiSnapshot()
        }
    }

    // #CALLS-ZOMBIE (2026-08-29, скриншот 23:45): единый поллинг-сторож зомби-состояний
    // входящего. Скриншот показал немыслимое: фаза «Соединение…» при «PC нет • ICE
    // CLOSED» и «ошибка сервера» — движок давно мёртв, собеседник вышел, а экран висел
    // в CONNECTING вечно. Причины: (1) движок закрыт (endCall из любой ветки), а фазу
    // никто не терминализировал; (2) FAILED↔CONNECTING-ретраи перезапускали 60с-watchdog
    // (потенциально бесконечно). Теперь — два железных предохранителя:
    //  • PC закрыт в CONNECTING ≥3с → терминалим (звонок уже физически мёртв);
    //  • CONNECTING с отправленным answer без ICE ≥90с ПОДРЯД (независимо от ретраев)
    //    → терминалим с внятной причиной.
    var zombieSince by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            if (!incoming) continue
            if (phase == CallPhase.CONNECTING && answerSent.value && !iceConnected.value) {
                val now = System.currentTimeMillis()
                if (zombieSince == 0L) zombieSince = now
                val heldMs = now - zombieSince
                val pcGone = !engine.hasPeerConnection()
                when {
                    pcGone && heldMs >= 3_000L -> {
                        AppLog.w("CallScreen", "ZOMBIE: CONNECTING ${heldMs / 1000}с без ICE при закрытом PC — терминалим")
                        failText = "Звонок оборван (соединение потеряно)"
                        signaling.hangup("timeout")
                        engine.endCall()
                        signaling.stop()
                        phase = CallPhase.FAILED
                        zombieSince = 0L
                    }
                    heldMs >= 90_000L -> {
                        AppLog.w("CallScreen", "ZOMBIE: абсолютный дедлайн 90с в CONNECTING без ICE — терминалим")
                        failText = "Не удалось установить соединение"
                        signaling.hangup("timeout")
                        engine.endCall()
                        signaling.stop()
                        phase = CallPhase.FAILED
                        zombieSince = 0L
                    }
                }
            } else {
                zombieSince = 0L
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerName, color = Color.White) },
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
            // #CALLS-UI-SHIFT (2026-08-30): контент поднят на 5% высоты экрана выше центра
            // (запрос пользователя после теста 30.08): аватарка выше, внизу больше места
            // для блока диагностики, который при «Соединение…» обрезался нижней навигацией.
            val shiftUp = (LocalConfiguration.current.screenHeightDp * 0.10f).dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp).padding(bottom = shiftUp),
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    // #CALLS-NAME-FIX: локальная копия — делегированное свойство
                    // не смарт-кастится после null-проверки.
                    val ph = peerPhoto
                    if (ph != null) {
                        AsyncImage(
                            model = ph,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(peerName.take(1).uppercase(), fontSize = 40.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Имя звонящего/собеседника — крупно (как в VK)
                Text(
                    text = peerName,
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
                                        // #CALLS-IN-FIX (2026-08-29): если сигналинг не поднят /
                                        // WS не открыт — declineCall молча отбрасывается в send()
                                        // и звонок продолжает звонить на других устройствах.
                                        // Fallback — HTTP vchat.hangupConversation(reason=declined).
                                        if (signaling.isWsReady()) {
                                            signaling.declineCall()
                                        } else {
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                val sk = app.ensureCallsSessionKey()
                                                val cid = activeCallId.value
                                                if (sk != null && !cid.isNullOrBlank()) {
                                                    withContext(Dispatchers.IO) {
                                                        app.apiClient.vchatHangupConversation(cid, sk, reason = "declined")
                                                    }
                                                } else {
                                                    AppLog.w("CallScreen", "Отклонить: нет WS и нет convId/sessionKey — decline не отправлен")
                                                }
                                            }
                                        }
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
                                            // #CALLS-IN-FIX (2026-08-29, лог 20:54): раньше accept
                                            // выполнялся мгновенно — если params ещё резолвились
                                            // (vchat при сбое висит до 45с), сигналинг не был поднят:
                                            // accept-call и answer уходили в никуда, «Соединение…» висело
                                            // вечно. Теперь: ждём params → поднимаем сигналинг (если ещё
                                            // не поднят) → ждём открытия WS → и только потом accept.
                                            phase = CallPhase.CONNECTING
                                            val resolved = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                                                incomingParamsDeferred.await()
                                            }
                                            if (resolved == null) {
                                                AppLog.w("CallScreen", "Принять: params не получены (таймаут 20с/ошибка) — отмена")
                                                failText = "Не удалось получить параметры звонка"
                                                phase = CallPhase.FAILED
                                                return@launch
                                            }
                                            if (!signaling.isRunning()) {
                                                // LaunchedEffect не успел/не смог — поднимаем сигналинг сами.
                                                val snap0 = app.prefs.data.first()
                                                val okUid0 = snap0.callsSessionUid
                                                val uid0 = if (okUid0 > 0L) okUid0 else app.exchangeAuthRepository.userId()
                                                engine.setIceServers(resolved)
                                                AppLog.i("CallScreen", "Принять: сигналинг не был поднят — стартуем (convId=${activeCallId.value})")
                                                signaling.start(
                                                    userId = uid0,
                                                    conversationId = activeCallId.value ?: "",
                                                    params = resolved,
                                                    peerId = peerId,
                                                )
                                                sigStarted = true
                                            }
                                            // Ждём открытия WS (до 10с) — иначе accept-call будет отброшен.
                                            var wsWaited = 0
                                            while (!signaling.isWsReady() && wsWaited < 10_000) {
                                                kotlinx.coroutines.delay(250)
                                                wsWaited += 250
                                            }
                                            if (!signaling.isWsReady()) {
                                                AppLog.w("CallScreen", "Принять: WS сигналинга не открылся за 10с — отмена")
                                                failText = "Нет связи с сервером звонков"
                                                phase = CallPhase.FAILED
                                                return@launch
                                            }
                                            AppLog.i("CallScreen", "Принять: params готовы, ws готов — accept")
                                            val sk = app.ensureCallsSessionKey()
                                            if (sk != null) {
                                                withContext(Dispatchers.IO) {
                                                    app.apiClient.vchatJoinConversation(
                                                        activeCallId.value ?: "", sk, isVideo = false
                                                    )
                                                }
                                            }
                                            // #CALLS-ACK-REOFFER (2026-08-29): accept-call ДО создания PC/answer —
                                            // сервер ретранслирует transmit-data участникам, подтвердившим участие;
                                            // answer, ушедший раньше accept, мог выбрасываться. Плюс это
                                            // детерминированный порядок (engine.acceptCall — асинхронный post).
                                            val acceptOk = signaling.acceptCall(isVideo = false)
                                            AppLog.i("CallScreen", "Принять: accept-call ${if (acceptOk) "отправлен" else "ОТБРОШЕН (WS закрыт!)"}")
                                            // #CALLS-IN-OFFER: PC создаётся здесь; offer, буферизованный
                                            // движком (пришёл до «Принять»), применяется САМ в acceptCall
                                            // (setRemoteDescription → createAnswer → answer уйдёт).
                                            engine.acceptCall(call)
                                            AppLog.i("CallScreen", "Принять: PC создаётся, offerReceived=${offerReceived.value}")
                                            phase = CallPhase.CONNECTING
                                        }
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
                                    // #CALLS-HANGUP-STATE-ENUM: эталон Conversation.hangup —
                                    // ACTIVE → HUNGUP, иное состояние (CONNECTING и т.п.) → CANCELED.
                                    signaling.hangup(if (phase == CallPhase.ACTIVE) "hungup" else "cancel")
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

                // #CALLS-DIAG (2026-08-29): тех-строки диагностики — видны пока звонок
                // НЕ активен (в т.ч. в RINGING — там важен nudge/отсутствие offer).
                // Скриншот экрана заменяет logcat.
                if (phase != CallPhase.ACTIVE) {
                    Spacer(Modifier.height(20.dp))
                    // #CALLS-DIAG-FIT (2026-08-30): блок диагностики ограничен 120dp и
                    // прокручивается — при «Соединение…» все строки (Диагностика/Сигналинг/
                    // Принято/Медиа) гарантированно видимы и не обрезаются нижней навигацией.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Диагностика: WS $diagWs • $diagPc • ICE $diagIce",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Сигналинг: $diagEvent • $diagPid • conv ${if (activeCallId.value.isNullOrBlank()) "—" else "✓"} • offer ${if (offerReceived.value) "✓" else "—"} • answer ${if (answerSent.value) "✓" else "—"}${if (diagReoffer.isBlank()) "" else " • $diagReoffer"}${if (diagAnswer.isBlank()) "" else " • $diagAnswer"}${if (srvErrCount > 0) " • err×$srvErrCount" else ""}",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        // #CALLS-RX-DEBUG (2026-08-30): какие команды сигналинга ПРИХОДИЛИ
                        // (топ-6 по частоте). «candidate×N» отсутствует при N локальных
                        // кандидатах = собеседник кандидаты не шлёт/не доходят — его сторона.
                        if (rxCommands.isNotEmpty()) {
                            val rxLine = rxCommands.entries
                                .sortedByDescending { it.value }
                                .take(6)
                                .joinToString(", ") { "${it.key}×${it.value}" }
                            Text(
                                text = "Принято: $rxLine",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        // #CALLS-ICE-STATS-UI (2026-08-30): решающая строка медиа-диагностики —
                        // типы собранных кандидатов (relay=0 при TURN = аллокация не удалась) и
                        // агрегат candidate-pair (reqS>0/resR=0/reqR=0 = собеседник не отвечает
                        // на проверки; reqR>0 = его проверки доходят, проблема в наших ответах).
                        if (diagStats.isNotBlank()) {
                            Text(
                                text = "Медиа: $diagStats",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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