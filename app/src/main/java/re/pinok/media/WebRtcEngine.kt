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
            onCallPhaseChanged(CallPhase.CONNECTING)
        }
    }

    fun endCall() {
        post {
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            pendingRemoteIce.clear()
            onCallPhaseChanged(CallPhase.ENDED)
            AppLog.i(TAG, "Call ended")
        }
    }

    fun setRemoteSdp(sdp: String, type: SessionDescription.Type) {
        post {
            val sessionDesc = SessionDescription(type, sdp)
            AppLog.i(TAG, "setRemoteSdp: type=$type sdpLen=${sdp.length}")
            peerConnection?.setRemoteDescription(SdpObserverAdapter(
                onSetSuccess = {
                    AppLog.i(TAG, "setRemoteSdp SUCCESS, pending=${pendingRemoteIce["remote"]?.size ?: 0}")
                    drainPendingIceCandidates()
                    // #CALLS-FIX: как в VK — createAnswer ТОЛЬКО после успешной
                    // установки remote SDP (onSetSuccess), не сразу.
                    if (type == SessionDescription.Type.OFFER) createAnswer()
                },
                onError = { err -> AppLog.e(TAG, "setRemoteSdp error: $err") }
            ), sessionDesc)
        }
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        post {
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
                servers.add(
                    PeerConnection.IceServer.builder("$url?transport=tcp")
                        .setUsername(turn.username ?: "")
                        .setPassword(turn.credential ?: "")
                        .createIceServer()
                )
            }
        }
        // fallback — если params пустые
        if (servers.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(STUN_URL).createIceServer())
        }
        iceServers = servers
        AppLog.i(TAG, "setIceServers: ${servers.size} servers (stun=${params.stunServer?.urls}, turn=${params.turnServer?.urls})")
    }

    fun release() {
        post {
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
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
                onIceCandidateReady(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                AppLog.d(TAG, "ICE: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> onCallPhaseChanged(CallPhase.ACTIVE)
                    PeerConnection.IceConnectionState.FAILED -> onCallPhaseChanged(CallPhase.FAILED)
                    PeerConnection.IceConnectionState.DISCONNECTED -> onCallPhaseChanged(CallPhase.ENDED)
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
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