package re.pinok.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun initialize() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        AppLog.i(TAG, "WebRTC initialized")
    }

    fun startCall(call: VkCall, isInitiator: Boolean) {
        ensureInitialized()
        createPeerConnection()
        createLocalAudioTrack()
        if (isInitiator) createOffer()
    }

    fun acceptCall(call: VkCall) {
        ensureInitialized()
        createPeerConnection()
        createLocalAudioTrack()
        onCallPhaseChanged(CallPhase.CONNECTING)
    }

    fun endCall() {
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

    fun setRemoteSdp(sdp: String, type: SessionDescription.Type) {
        val sessionDesc = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(SdpObserverAdapter(
            onSuccess = { drainPendingIceCandidates() },
            onError = { err -> AppLog.e(TAG, "setRemoteSdp error: $err") }
        ), sessionDesc)
        if (type == SessionDescription.Type.OFFER) createAnswer()
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        if (peerConnection?.remoteDescription != null) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingRemoteIce.getOrPut("remote") { mutableListOf() }.add(candidate)
        }
    }

    fun setMuted(muted: Boolean) { localAudioTrack?.setEnabled(!muted) }
    fun setSpeakerOn(speakerOn: Boolean) { /* TODO: AudioManager routing */ }

    fun release() { endCall(); factory?.dispose(); factory = null }

    private fun ensureInitialized() { if (factory == null) initialize() }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_URL).createIceServer(),
            PeerConnection.IceServer.builder(TURN_URL)
                .setUsername("vk").setPassword("vk").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
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
        peerConnection = factory?.createPeerConnection(rtcConfig, observer)
    }

    private fun createLocalAudioTrack() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioSource = factory?.createAudioSource(constraints)
        audioSource?.let { src ->
            localAudioTrack = factory?.createAudioTrack("audio0", src)
            localAudioTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("stream0"))
                AppLog.i(TAG, "Local audio track created")
            }
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        peerConnection?.createOffer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                    onLocalSdpReady(it)
                }
                onCallPhaseChanged(CallPhase.CONNECTING)
            },
            onError = { err -> AppLog.e(TAG, "createOffer error: $err") }
        ), constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        peerConnection?.createAnswer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                    onLocalSdpReady(it)
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