package re.pinok.media

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import re.pinok.data.model.CallPhase
import re.pinok.data.model.VkCall
import re.pinok.util.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * #CALLS: WebRTC-движок для голосовых звонков VK.
 *
 * Использует org.webrtc.* (stream-webrtc-android 1.3.10).
 * STUN: videostun.okcdn.ru:19302. TURN: calls.okcdn.ru.
 *
 * ВАЖНО: все операции с PeerConnection/audio source выполняются на
 * отдельном HandlerThread (signaling thread). Вызов createPeerConnection /
 * createAudioSource с main-потока вызывает нативный крэш libjingle:
 * "front() called on an empty vector" (SIGABRT).
 */
class WebRtcEngine(
    private val context: Context,
    private val onCallPhaseChanged: (CallPhase) -> Unit,
    private val onLocalSdpReady: (SessionDescription) -> Unit,
    private val onIceCandidateReady: (IceCandidate) -> Unit,
    /** #CALLS-DIAG (2026-08-29): сырое имя ICE-состояния для экранной диагностики. */
    private val onIceStateChanged: ((String) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "WebRtcEngine"
        private val STUN_URL = "stun:videostun.okcdn.ru:19302"
        private val TURN_URL = "turn:calls.okcdn.ru:3478?transport=udp"
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private val pendingRemoteIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    // #CALLS-FIX (2026-08-24): последний отправленный локальный SDP (offer/answer).
    // При topology-changed сервер просит отправить offer заново — берём его отсюда
    // (в CallScreen кэш pendingLocalSdp очищается после первой отправки).
    @Volatile
    private var lastLocalSdp: SessionDescription? = null
    fun lastLocalSdp(): SessionDescription? = lastLocalSdp
    // #CALLS-FIX: реальные ICE-серверы из conversation params (turn_server/stun_server).
    // Хардкод videostun.okcdn.ru/turn:calls.okcdn.ru не даёт ICE CONNECTED.
    @Volatile
    private var iceServers: List<PeerConnection.IceServer> = emptyList()

    // #CALLS-OUT-FIX (2026-08-27): флаг «PeerConnection создан». Читается с main-потока
    // (CallScreen решает: применять answer/offer сразу или кэшировать до accept),
    // пишется на signaling thread — поэтому @Volatile.
    @Volatile
    private var pcCreated = false

    /** true, если PeerConnection жив (создан и не закрыт). */
    fun hasPeerConnection(): Boolean = pcCreated

    // #CALLS-IN-OFFER (2026-08-29): буфер удалённого SDP, пришедшего ДО создания PC
    // (входящий: offer звонящего прилетает, пока экран «Входящий звонок…» — PC ещё нет).
    // Применяется сразу после создания PC в acceptCall/startCall. Раньше кэш жил в
    // CallScreen (pendingOffer) и читался кнопкой «Принять» — offer, прилетевший МЕЖДУ
    // чтением кэша и созданием PC, затирался (pendingOffer.value = null) и терялся:
    // answer не создавался, звонящий висел и сбрасывал звонок (лог 21:26, remote-hangup).
    @Volatile
    private var pendingRemoteSdp: SessionDescription? = null

    // #CALLS-DROP-GRACE (2026-08-29): DISCONNECTED часто транзиентен (смена Wi-Fi↔LTE,
    // краткая потеря пакетов) — ICE восстанавливается сам. Раньше первый же
    // DISCONNECTED мгновенно переводил экран в ENDED — живые разговоры обрывались.
    // Даём 10с на восстановление: CONNECTED/повторный DISCONNECTED инвалидируют таймер
    // (генерационный счётчик), переживший таймер закрывает звонок.
    private val iceStateGen = java.util.concurrent.atomic.AtomicLong(0)
    private val iceRecoveryTimeoutMs = 10_000L

    // #CALLS-ICE-STATS-UI (2026-08-30, скриншот «ICE FAILED • ans×2, трубка не
    // поднимается с обоих сторон»): пользователь диагностирует звонки СКРИНШОТАМИ,
    // а решающая статистика уходила только в logcat. Теперь движок копит:
    //  1) типы собранных ЛОКАЛЬНЫХ кандидатов (host/srflx/prflx/relay) — relay=0
    //     при настроенном TURN мгновенно означает «аллокация TURN не удалась»;
    //  2) агрегат candidate-pair из dumpIceStats (reqS/resR/reqR) — отвечает на
    //     вопрос «проверки уходят без ответа» vs «проверки собеседника не доходят».
    // Всё это попадает в экранную строку «Медиа:» (CallScreen, поллинг iceUiSnapshot).
    // Не сбрасываем в endCall — снимок должен пережить hangup для скриншота FAILED-экрана;
    // очистка только при создании НОВОГО PeerConnection.
    private val candTypeCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val candTcpCount = java.util.concurrent.atomic.AtomicInteger(0)

    // #CALLS-RX-DEBUG (2026-08-30, скриншот 00:17): пар=0/reqS=0 при 10 локальных
    // кандидатах (host=5 srflx=1 relay=4 tcp=2) = пары НЕ ИЗ ЧЕГО строить —
    // удалённые кандидаты до нас не доехали/не применились. Счётчик принятых
    // удалённых кандидатов попадает в снапшот («прин:N») — скриншот покажет,
    // приходил ли от собеседника хоть один кандидат.
    private val remoteCandCount = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var lastPairStats: String? = null

    /** Экранный снимок ICE: «канд: host=1 srflx=1 relay=0 • прин:3 sdpR:+ • пар=3 • reqS=12 resR=0 reqR=0». */
    fun iceUiSnapshot(): String {
        val order = listOf("host", "srflx", "prflx", "relay")
        val known = order.mapNotNull { t -> candTypeCounts[t]?.let { "$t=$it" } }
        val extra = candTypeCounts.keys.filter { it !in order }.map { "$it=${candTypeCounts[it]}" }
        val candPart = if (known.isEmpty() && extra.isEmpty()) "канд: 0" else "канд: ${(known + extra).joinToString(" ")}" + (if (candTcpCount.get() > 0) " tcp=${candTcpCount.get()}" else "")
        // #CALLS-RX-DEBUG: принятые удалённые кандидаты + применён ли удалённый SDP.
        val rx = "прин:${remoteCandCount.get()} sdpR:${if (hasRemoteDescription()) "+" else "-"}"
        val pairs = lastPairStats
        return listOfNotNull(candPart, rx, pairs).joinToString(" • ")
    }

    /** Установлен ли remote description (или он ждёт в буфере до создания PC). */
    fun hasRemoteDescription(): Boolean =
        peerConnection?.remoteDescription != null || pendingRemoteSdp != null

    // #CALLS-FIX: отдельный signaling thread для всех операций PeerConnection.
    // Создание PC/audio source с main-потока → нативный SIGABRT в libjingle
    // ("front() called on an empty vector").
    private var signalingThread: HandlerThread? = null
    private var signalingHandler: Handler? = null

    private fun post(runnable: () -> Unit) {
        val h = signalingHandler
        if (h != null && h.looper.thread != Thread.currentThread()) {
            h.post(runnable)
        } else {
            runnable()
        }
    }

    fun initialize() {
        if (factory != null) return
        if (signalingThread == null) {
            signalingThread = HandlerThread("webrtc-signaling").also { it.start() }
            signalingHandler = Handler(signalingThread!!.looper)
        }
        // #CALLS-FIX: factory создаётся на нашем signaling thread — тогда
        // libjingle считает этот поток signaling thread, и все операции
        // PeerConnection (createPeerConnection/audio source) выполняются на нём.
        post {
            if (factory != null) return@post
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    // #CALLS: field trials как в VK (PeerConnectionClient.getFieldTrials):
                    // RED-кодирование Opus + ранний старт playout/записи.
                    .setFieldTrials("WebRTC-Audio-Red-For-Opus/Enabled-2/CallsSDK-Audio-EarlyStartPlayout/Recording/")
                    .createInitializationOptions()
            )
            // #CALLS-FIX: как в ru.ok.android.webrtc.SharedPeerConnectionFactory —
            // audio device module создаётся ЯВНО и передаётся в factory.
            // Без него createAudioSource даёт нативный крэш libjingle
            // "front() called on an empty vector".
            val audioDeviceModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
            val eglBase = org.webrtc.EglBase.create()
            val eglContext = eglBase.eglBaseContext
            factory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(org.webrtc.DefaultVideoEncoderFactory(
                    eglContext, true /* enableIntelVp8Encoder */, true /* enableH264HighProfile */
                ))
                .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(eglContext))
                .createPeerConnectionFactory()
            eglBase.release()
            AppLog.i(TAG, "WebRTC initialized")
        }
    }

    fun startCall(call: VkCall, isInitiator: Boolean) {
        ensureInitialized()
        post {
            setCommunicationMode()
            createLocalAudioTrack()
            createPeerConnection()
            localAudioTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("stream0"))
            }
            applyBufferedRemoteSdp()
            if (isInitiator) createOffer()
        }
    }

    fun acceptCall(call: VkCall) {
        ensureInitialized()
        post {
            // #CALLS-FIX: порядок как в офиц. примере WebRTC: audio source →
            // audio track → peer connection → addTrack. Создание PC до audio
            // source даёт нативный крэш "front() called on an empty vector".
            setCommunicationMode()
            createLocalAudioTrack()
            createPeerConnection()
            localAudioTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("stream0"))
            }
            // #CALLS-IN-OFFER: offer звонящего, буферизованный до «Принять»,
            // применяем здесь же (на signaling thread, в порядке очереди) —
            // setRemoteDescription → onSetSuccess → createAnswer → answer уйдёт.
            applyBufferedRemoteSdp()
            onCallPhaseChanged(CallPhase.CONNECTING)
        }
    }

    /** #CALLS-IN-OFFER: применить буферизованный remote SDP (вызов на signaling thread). */
    private fun applyBufferedRemoteSdp() {
        val buffered = pendingRemoteSdp
        if (buffered != null) {
            pendingRemoteSdp = null
            AppLog.i(TAG, "применяю буферизованный ${buffered.type} (PC готов, len=${buffered.description.length})")
            applyRemoteSdp(buffered)
        }
    }

    fun endCall() {
        // инвалидирует возможный DISCONNECTED-таймер
        iceStateGen.incrementAndGet()
        post {
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
            pcCreated = false
            pendingRemoteSdp = null
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            pendingRemoteIce.clear()
            onCallPhaseChanged(CallPhase.ENDED)
            AppLog.i(TAG, "Call ended")
        }
    }

    /**
     * #CALLS-IN-OFFER (2026-08-29): единая точка приёма удалённого SDP.
     * Если PC ещё НЕ создан (входящий: offer прилетел до «Принять») — SDP
     * буферизуется и будет применён сразу после создания PC в acceptCall/startCall.
     * Если PC уже создан — применяется немедленно (offer → createAnswer в onSetSuccess).
     * Так исчезает гонка CallScreen «кэш pendingOffer vs кнопка Принять».
     */
    fun setRemoteSdp(sdp: String, type: SessionDescription.Type) {
        post {
            if (peerConnection == null) {
                pendingRemoteSdp = SessionDescription(type, sdp)
                AppLog.i(TAG, "setRemoteSdp: PC ещё нет — ${type.name.lowercase()} буферизован (len=${sdp.length})")
            } else {
                applyRemoteSdp(SessionDescription(type, sdp))
            }
        }
    }

    /** Применение remote SDP (только на signaling thread, PC уже создан). */
    private fun applyRemoteSdp(sessionDesc: SessionDescription) {
        AppLog.i(TAG, "setRemoteSdp: type=${sessionDesc.type} sdpLen=${sessionDesc.description.length}")
        peerConnection?.setRemoteDescription(SdpObserverAdapter(
            onSetSuccess = {
                AppLog.i(TAG, "setRemoteSdp SUCCESS, pending=${pendingRemoteIce["remote"]?.size ?: 0}")
                drainPendingIceCandidates()
                // #CALLS-FIX: как в VK — createAnswer ТОЛЬКО после успешной
                // установки remote SDP (onSetSuccess), не сразу.
                if (sessionDesc.type == SessionDescription.Type.OFFER) createAnswer()
            },
            onError = { err -> AppLog.e(TAG, "setRemoteSdp error: $err") }
        ), sessionDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        post {
            // #CALLS-RX-DEBUG: считаем ВСЕ входящие кандидаты (и добавленные, и
            // буферизованные до remote description) — для экранного «прин:N».
            remoteCandCount.incrementAndGet()
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
            if (peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(candidate)
                AppLog.d(TAG, "addRemoteIceCandidate OK: mid=$sdpMid idx=$sdpMLineIndex ${candidate.sdp.take(40)}")
            } else {
                pendingRemoteIce.getOrPut("remote") { mutableListOf() }.add(candidate)
                AppLog.d(TAG, "addRemoteIceCandidate PENDING (no remoteDesc): ${candidate.sdp.take(40)}")
            }
        }
    }

    fun setMuted(muted: Boolean) { post { localAudioTrack?.setEnabled(!muted) } }
    fun setSpeakerOn(speakerOn: Boolean) {
        // #CALLS: переключение динамик/наушник (как CallsAudioManagerV3Impl).
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        post {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = speakerOn
        }
    }

    /** #CALLS: выставить режим разговора + audio focus (вызывается при accept/start). */
    private fun setCommunicationMode() {
        try {
            val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            // API 26+: requestAudioFocus(AudioFocusRequest); старый метод deprecated.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val afr = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                am.requestAudioFocus(afr)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN)
            }
            AppLog.i(TAG, "Communication mode + audio focus set")
        } catch (e: Exception) {
            AppLog.w(TAG, "setCommunicationMode error: ${e.message}")
        }
    }

    /**
     * #CALLS: задать ICE-серверы из conversation params (STUN/TURN с credentials).
     * Вызывается до acceptCall/startCall.
     */
    fun setIceServers(params: re.pinok.media.ConversationParamsDecoder.Params) {
        val servers = mutableListOf<PeerConnection.IceServer>()
        params.stunServer?.urls?.forEach { url ->
            runCatching { servers.add(PeerConnection.IceServer.builder(url).createIceServer()) }
        }
        params.turnServer?.urls?.forEach { url ->
            val turn = params.turnServer
            runCatching {
                servers.add(
                    PeerConnection.IceServer.builder(url)
                        .setUsername(turn.username ?: "")
                        .setPassword(turn.credential ?: "")
                        .createIceServer()
                )
                // #CALLS-FIX: как в VK (PeerConnectionClient.m109a) — добавляем
                // TURN ?transport=tcp вариант (tcpCandidatePolicy=ENABLED).
                // #CALLS-OUT-FIX (2026-08-27): НЕ склеиваем "$url?transport=tcp" вслепую —
                // для URL, уже содержащего transport=, это давало ДВА transport-параметра
                // (?transport=udp?transport=tcp), и libjingle такой сервер молча отбрасывал.
                // #CALLS-ICE-WATCHDOG (2026-08-29): для ?transport=udp добавляем TCP-вариант
                // ТОГО ЖЕ сервера (замена transport=udp → transport=tcp) — как в Chrome
                // (udp+tcp пара на 3478). Раньше URL с transport= пропускался — TCP-fallback
                // не добавлялся вовсе, и на LTE/за жёстким NAT (UDP заблокирован) ICE умирал
                // в CHECKING→FAILED при успешной аллокации по UDP-ветке не бывало.
                val tcpUrl = when {
                    url.contains("transport=udp") -> url.replace("transport=udp", "transport=tcp")
                    url.contains("transport=") -> null
                    url.contains('?') -> "$url&transport=tcp"
                    else -> "$url?transport=tcp"
                }
                tcpUrl?.let { tcp ->
                    servers.add(
                        PeerConnection.IceServer.builder(tcp)
                            .setUsername(turn.username ?: "")
                            .setPassword(turn.credential ?: "")
                            .createIceServer()
                    )
                }
            }
        }
        // fallback — если params пустые
        if (servers.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(STUN_URL).createIceServer())
        }
        iceServers = servers
        // #CALLS-ICE-STATS-UI: пустые креденшелы TURN = аллокация 401 = relay-кандидатов
        // не будет (на экране «канд: … relay=0») — логируем сразу, не дожидаясь stats.
        val turn = params.turnServer
        val credsOk = !turn?.username.isNullOrBlank() && !turn?.credential.isNullOrBlank()
        AppLog.i(TAG, "setIceServers: ${servers.size} servers (stun=${params.stunServer?.urls}, turn=${turn?.urls}, creds=${if (credsOk) "есть" else "НЕТ/пустые"})")
    }

    fun release() {
        post {
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
            pcCreated = false
            pendingRemoteSdp = null
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            pendingRemoteIce.clear()
            factory?.dispose()
            factory = null
        }
        signalingThread?.quitSafely()
        signalingThread = null
        signalingHandler = null
    }

    private fun ensureInitialized() { if (factory == null) initialize() }

    private fun createPeerConnection() {
        // #CALLS-ICE-STATS-UI: новый звонок — новая статистика (см. комментарий к полям).
        candTypeCounts.clear()
        candTcpCount.set(0)
        remoteCandCount.set(0)
        lastPairStats = null
        val iceServers = this.iceServers.ifEmpty {
            listOf(
                PeerConnection.IceServer.builder(STUN_URL).createIceServer(),
                PeerConnection.IceServer.builder(TURN_URL)
                    .setUsername("vk").setPassword("vk").createIceServer(),
            )
        }
        // #CALLS-FIX: настройки как в ru.ok.android.webrtc.PeerConnectionClient
        // (m109a): MAXBUNDLE, REQUIRE rtcp-mux, ECDSA, TCP candidates ENABLED.
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            keyType = PeerConnection.KeyType.ECDSA
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // #CALLS-ICE-STATS-UI: классифицируем локального кандидата.
                // Формат sdp: "candidate:<foundation> <component> <proto> <prio> <ip> <port> typ <type> ..."
                val tokens = candidate.sdp.split(" ")
                val typ = tokens.getOrNull(7) ?: "?"
                val proto = tokens.getOrNull(2) ?: "?"
                if (typ != "?") candTypeCounts.merge(typ, 1, Int::plus)
                if (proto == "tcp") candTcpCount.incrementAndGet()
                AppLog.d(TAG, "локальный кандидат: $typ/$proto ${candidate.sdp.take(48)}")
                onIceCandidateReady(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                // #CALLS-DROP-GRACE: INFO вместо DEBUG — ICE-переходы должны быть видны
                // в любом логе (раньше тонули в DEBUG-фильтре).
                AppLog.i(TAG, "ICE: $state")
                onIceStateChanged?.invoke(state.name)
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        // инвалидирует висящий DISCONNECTED-таймер (генерация изменилась)
                        iceStateGen.incrementAndGet()
                        onCallPhaseChanged(CallPhase.ACTIVE)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        onCallPhaseChanged(CallPhase.FAILED)
                        // #CALLS-ICE-REANSWER (2026-08-29, лог 22:29): при FAILED снимаем
                        // getStats — candidate-pair статистика показывает, УХОДИЛИ ЛИ наши
                        // STUN-проверки (requestsSent) и ПРИХОДИЛИ ЛИ ответы (responsesReceived).
                        // Это решающий диагноз «обоюдной тишины»: если requestsSent>0 при
                        // responsesReceived=0 на ВСЕХ парах — собеседник не отвечает (его
                        // агент не запущен/answer до него не дошёл), а не сеть.
                        dumpIceStats()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        val gen = iceStateGen.incrementAndGet()
                        signalingHandler?.postDelayed({
                            if (gen == iceStateGen.get() && peerConnection != null) {
                                AppLog.w(TAG, "ICE DISCONNECTED держится >${iceRecoveryTimeoutMs / 1000}с — завершаем звонок")
                                onCallPhaseChanged(CallPhase.ENDED)
                            }
                        }, iceRecoveryTimeoutMs)
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                // #CALLS-ICE-STATS-UI: сбор должен доходить до COMPLETE; застревание в
                // GATHERING = висящие TURN-аллокации (креденшелы/недоступность сервера).
                AppLog.i(TAG, "ICE gathering: $state (${iceUiSnapshot()})")
            }
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {
                if (receiver.track() is AudioTrack) AppLog.i(TAG, "Remote audio track")
            }
        }
        // #CALLS-FIX: как в VK (PeerConnectionClient.b): DtlsSrtpKeyAgreement
        // обязательно — без него DTLS-handshake не активируется.
        val pcConstraints = MediaConstraints()
        pcConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        @Suppress("DEPRECATION")
        peerConnection = factory?.createPeerConnection(rtcConfig, pcConstraints, observer)
        pcCreated = peerConnection != null
    }

    private fun createLocalAudioTrack() {
        // #CALLS: AEC/NS/AGC как в VK (PeerConnectionAudioConstraints).
        // Крэш "front() on empty vector" был из-за отсутствия AudioDeviceModule —
        // он уже исправлен, AEC/NS можно включать.
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
        audioSource = factory?.createAudioSource(constraints)
        audioSource?.let { src ->
            localAudioTrack = factory?.createAudioTrack("audio0", src)
            AppLog.i(TAG, "Local audio track created")
        }
    }

    private fun createOffer() {
        // #CALLS-FIX: как в VK (PeerConnectionClient.b): OfferToReceiveVideo=true.
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        peerConnection?.createOffer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let {
                    // #CALLS-FIX: как в VK — SDP отправляем ТОЛЬКО после установки
                    // localDescription (onSetSuccess), иначе ICE gathering не стартует.
                    peerConnection?.setLocalDescription(SdpObserverAdapter(
                        onSetSuccess = {
                            AppLog.i(TAG, "setLocalDescription(offer) SUCCESS")
                            lastLocalSdp = it
                            onLocalSdpReady(it)
                        },
                        onError = { err -> AppLog.e(TAG, "setLocalDescription error: $err") }
                    ), it)
                }
                onCallPhaseChanged(CallPhase.CONNECTING)
            },
            onError = { err -> AppLog.e(TAG, "createOffer error: $err") }
        ), constraints)
    }

    private fun createAnswer() {
        // #CALLS-FIX: как в VK (PeerConnectionClient.b): OfferToReceiveVideo=true.
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        peerConnection?.createAnswer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let {
                    // #CALLS-FIX: как в VK — answer отправляем ТОЛЬКО после установки
                    // localDescription (onSetSuccess).
                    peerConnection?.setLocalDescription(SdpObserverAdapter(
                        onSetSuccess = {
                            AppLog.i(TAG, "setLocalDescription(answer) SUCCESS")
                            lastLocalSdp = it
                            onLocalSdpReady(it)
                        },
                        onError = { err -> AppLog.e(TAG, "setLocalDescription error: $err") }
                    ), it)
                }
            },
            onError = { err -> AppLog.e(TAG, "createAnswer error: $err") }
        ), constraints)
    }

    private fun drainPendingIceCandidates() {
        pendingRemoteIce.remove("remote")?.forEach { candidate ->
            peerConnection?.addIceCandidate(candidate)
        }
    }

    /**
     * #CALLS-ICE-WATCHDOG (2026-08-29): публичный снимок candidate-pair статистики.
     * Вызывается из CallScreen на watchdog'ах «answer ушёл, ICE не подключился» и
     * при ICE FAILED — решающий диагноз «наши проверки уходят, ответы не приходят»
     * (requestsSent/responsesReceived) vs «проверки собеседника к нам не доходят»
     * (requestsReceived=0 на всех парах).
     */
    fun dumpIceStatsNow() = dumpIceStats()

    /**
     * #CALLS-ICE-REANSWER (2026-08-29): снимок candidate-pair статистики при ICE FAILED.
     * Для каждой пары логируем: типы/адреса локального и удалённого кандидата, state,
     * nominated, requestsSent/responsesReceived (наши проверки) и requestsReceived/
     * responsesSent (чужие проверки к нам). API: org.webrtc.RTCStatsCollectorCallback
     * (stream-webrtc-android 1.3.10, libwebrtc M114).
     */
    private fun dumpIceStats() {
        val pc = peerConnection ?: return
        runCatching {
            pc.getStats { report ->
                try {
                    val candInfo = HashMap<String, String>()
                    for (stat in report.statsMap.values) {
                        if (stat.type == "local-candidate" || stat.type == "remote-candidate") {
                            val ip = stat.members["ip"] ?: stat.members["address"] ?: "?"
                            val port = stat.members["port"] ?: "?"
                            val ctype = stat.members["candidateType"] ?: "?"
                            candInfo[stat.id] = "$ctype $ip:$port"
                        }
                    }
                    val sb = StringBuilder()
                    var pairs = 0
                    var sumReqS = 0L
                    var sumResR = 0L
                    var sumReqR = 0L
                    for (stat in report.statsMap.values) {
                        if (stat.type == "candidate-pair") {
                            pairs++
                            sumReqS += (stat.members["requestsSent"]?.toString()?.toLongOrNull() ?: 0L)
                            sumResR += (stat.members["responsesReceived"]?.toString()?.toLongOrNull() ?: 0L)
                            sumReqR += (stat.members["requestsReceived"]?.toString()?.toLongOrNull() ?: 0L)
                            val localId = stat.members["localCandidateId"]?.toString()
                            val remoteId = stat.members["remoteCandidateId"]?.toString()
                            sb.append("[")
                                .append(candInfo[localId] ?: localId ?: "?")
                                .append(" <-> ")
                                .append(candInfo[remoteId] ?: remoteId ?: "?")
                                .append("] state=").append(stat.members["state"])
                                .append(" nom=").append(stat.members["nominated"])
                                .append(" reqS=").append(stat.members["requestsSent"])
                                .append(" resR=").append(stat.members["responsesReceived"])
                                .append(" reqR=").append(stat.members["requestsReceived"])
                                .append(" resS=").append(stat.members["responsesSent"])
                                .append(" | ")
                        }
                    }
                    val s = sb.toString()
                    // #CALLS-ICE-STATS-UI: агрегат для экранной строки «Медиа:» — читается
                    // CallScreen'ом через iceUiSnapshot() (скриншот вместо logcat).
                    lastPairStats = "пар=$pairs • reqS=$sumReqS resR=$sumResR reqR=$sumReqR"
                    AppLog.w(TAG, if (s.isEmpty()) "ICE stats: нет candidate-pair в отчёте (${lastPairStats})" else "ICE stats: $s (${lastPairStats})")
                } catch (e: Exception) {
                    AppLog.w(TAG, "ICE stats parse error: ${e.message}")
                }
            }
        }.onFailure { AppLog.w(TAG, "ICE stats API error: ${it.message}") }
    }
}

/** Adapter: SdpObserver → Kotlin lambdas. */
private class SdpObserverAdapter(
    private val onSuccess: ((SessionDescription?) -> Unit)? = null,
    private val onSetSuccess: (() -> Unit)? = null,
    private val onError: ((String?) -> Unit)? = null,
) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) { onSuccess?.invoke(sdp) }
    override fun onSetSuccess() { onSetSuccess?.invoke() }
    override fun onCreateFailure(err: String?) { onError?.invoke(err) }
    override fun onSetFailure(err: String?) { onError?.invoke(err) }
}