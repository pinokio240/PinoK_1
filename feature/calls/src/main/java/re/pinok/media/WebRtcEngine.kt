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
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
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
    /** #CALLS-VIDEO-RX: удалённый VideoTrack появился (или null — звонок завершён). */
    private val onRemoteVideoTrack: ((VideoTrack?) -> Unit)? = null,
) {
companion object {
        private const val TAG = "WebRtcEngine"
        private val STUN_URL = "stun:videostun.okcdn.ru:19302"
        private val TURN_URL = "turn:calls.okcdn.ru:3478?transport=udp"
        /** #CALLS-INLINE-ICE: официальный VK-клиент зашивает кандидаты ВНУТРЬ SDP.
         *  Без inline-кандидатов входящий звонок не работает (пар=0, reqS=0).
         *  Ждём сбора кандидатов до 1.5с, затем отправляем SDP с зашитыми a=candidate. */
        private const val INLINE_ICE_WAIT_MS = 1500L
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private val pendingRemoteIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    @Volatile
    private var lastLocalSdp: SessionDescription? = null
    fun lastLocalSdp(): SessionDescription? = lastLocalSdp

    // #CALLS-INLINE-ICE: кандидаты, собранные с момента планирования отправки SDP.
    private val gatheredCandidates = ArrayList<IceCandidate>(16)
    @Volatile
    private var inlineIceSent = false
    private val sdpSendGen = java.util.concurrent.atomic.AtomicLong(0)

    // #CALLS-ANSWER-FIRST (2026-08-30, тест 4 16:03–16:05): ПОРЯДОК отправки решает всё.
    // Прежний #CALLS-NON-TRICKLE (ждать gathering ≤3с, зашивать кандидатов в текст SDP)
    // опровергнут тестом 4: trickle-кандидаты через сервер ДОСТАВЛЯЮТСЯ (4 trickle от
    // официального звонящего дошли и применились, eff=candidate ×4), а гипотеза «trickle
    // не проходят» из теста 15:38 была ошибкой интерпретации — там собеседник их просто
    // не отправлял. ФАТАЛЬНЫЙ дефект старой схемы: у входящего звонка кандидаты уходили
    // на ~3с РАНЬШЕ answer (тест 4: кандидаты 16:03:38.433–38.620, answer 16:03:41.413).
    // У эталонного клиента (OK/videochat/transport/DirectTransport) транспорт звонящего
    // к этому моменту уже открыт, remote description ещё пуст — addIceCandidate ДО
    // setRemoteDescription отклоняется, и обработчик ошибки у эталона закрывает ВЕСЬ
    // транспорт: _addIceCandidate(...)["catch"](this.close.bind(this)). Наш answer,
    // пришедший на 3с позже, применять уже некому → 0 STUN-проверок от собеседника →
    // CHECKING→FAILED (тесты 2 и 4, обе роли). Эталонный callee шлёт answer СРАЗУ после
    // setLocal (case "have-remote-offer" → _createAnswer().then(sendSdp)), кандидаты —
    // trickle'ом ПОСЛЕ. Повторяем этот порядок: sendLocalSdpNow() без задержек.
    //
    // #CALLS-INLINE-ICE (2026-08-31, лог 21:20–21:26 — финальная эволюция схемы):
    // входящий по Wi-Fi: reqS=307 resR=0 reqR=0 — обоюдная тишина STUN, включая
    // relay↔relay через TURN VK; при этом LTE↔LTE тем же кодом — CONNECTED за 0.7с.
    // Разбор: answer уходил первым, но 10 trickle-кандидатов уезжали залпом в первые
    // 200мс — у эталонного клиента (OK/videochat DirectTransport) addIceCandidate,
    // пришедший ДО применения answer (async setRemoteDescription ещё не отработал),
    // роняет ВЕСЬ транспорт: catch(this.close.bind(this)). Пир так и не получил наши
    // кандидаты → на его TURN-аллокации не создались permissions для наших relay-IP →
    // наши relay-проверки глохли у его сервера, его проверок не было вовсе (reqR=0).
    // Wi-Fi (строгий NAT) умирает полностью (0/307), LTE спасает peer-reflexive.
    // РЕШЕНИЕ: кандидаты зашиваются ВНУТРЬ SDP (одна посылка — гонка невозможна),
    // trickle остаётся только для кандидатов, собранных ПОСЛЕ отправки. Ретраи
    // (REANSWER/REOFFER в CallScreen) уезжают с кандидатами автоматически —
    // УСТАРЕЛО (2026-09-03, #CALLS-NON-TRICKLE-2): inline-ICE отменён, как в VK web —
    // SDP уходит сразу и чистым, кандидаты только trickle'ом (onIceCandidateReady).
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

    // #CALLS-PC-RESTART (2026-08-31, лог ciber.txt 22:55 Wi-Fi): последний применённый
    // удалённый OFFER. При ICE FAILED входящего (пар=0/reqS=0 — пары вообще не
    // формируются) повторные REANSWER бессмысленны: тот же PC, те же ufrag/pwd, те же
    // мёртвые порты. Единственный доступный отвечающему рестарт — пересоздать PC и
    // ответить на ЭТОТ ЖЕ offer НОВЫМ answer: новые ice-ufrag/pwd в answer — по RFC
    // это сигнал ICE-restart, агент собеседника обязан пересобрать check-list и
    // кандидатов. Оффер хранится до первого createPeerConnection следующего звонка.
    @Volatile
    private var lastRemoteOffer: SessionDescription? = null

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

    // #CALLS-VIDEO-RX (Этап 1, CALLS_MAP §11.2): приём видео собеседника.
    // OFF (false) — старое поведение #CALLS-VIDEO-INACTIVE: video-транссиверы → INACTIVE,
    // видео не согласуется (a=inactive), декодер не стартует.
    // RECEIVE (true) — video-транссиверы → RECV_ONLY (принимаем, НЕ отправляем: mid=1
    // живёт, BUNDLE/ICE кандидаты с sdpMid=1 не отваливаются) + H265 вырезается из
    // answer (стек краша 21:50 не снят, виновник не доказан — H265 первый кодек
    // официального; после strip первым выжившим становится H264).
    @Volatile
    private var videoRxEnabled: Boolean = false

    fun setVideoRxEnabled(enabled: Boolean) { videoRxEnabled = enabled }

    // #CALLS-SYMMETRIC (01.09): отправлять наружу чёрную видеозаглушку (Этап 2-заготовка,
    // БЕЗ камеры). answer m=video становится sendrecv — звонок симметричен. Гипотеза
    // пользователя: официальный клиент в Wi-Fi same-NAT не начинает ICE-проверки
    // против recvonly-ответа (лог 01.09 09:50: наш answer доставлен и ACKнут, 24 пары,
    // reqS=103 — а от пира 0 проверок и 0 ответов; на LTE тот же код — CONNECTED за 2с).
    @Volatile
    private var videoTxEnabled: Boolean = false

    fun setVideoTxEnabled(enabled: Boolean) { videoTxEnabled = enabled }

    // #CALLS-SYMMETRIC: фиктивный источник видео (чёрные кадры 320×180@10fps).
    // Живёт на signaling thread; создается один раз, переиспользуется при рестартах PC.
    @Volatile
    private var dummyVideoRunning = false
    private var dummyVideoSource: org.webrtc.VideoSource? = null
    private var localVideoTrack: org.webrtc.VideoTrack? = null

    // #CALLS-VIDEO-RX: EglBase живёт столько же, сколько factory — видео-декодер и
    // рендерер CallScreen (SurfaceViewRenderer) делят этот EGL-контекст. Раньше
    // eglBase создавался локально и релизился сразу после создания factory — для
    // аудио это не мешало (кодеки видео не использовались), но декодер видео с
    // терминированным контекстом не работает.
    private var eglBase: EglBase? = null

    fun eglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    // #CALLS-VIDEO-RX: последний полученный удалённый VideoTrack (onAddTrack).
    @Volatile
    private var remoteVideoTrackRef: VideoTrack? = null

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
        // #CALLS-NATIVE-LOG (2026-08-31, лог ciber.txt 22:55 Wi-Fi): в Wi-Fi-сценарии
        // «оба за одним роутером» наш агент имел кандидатов с обеих сторон, дошёл до
        // CHECKING и 16с МОЛЧАЛ: пар=0, reqS=0 (ни одной проверки) — тогда как LTE тем же
        // кодом подключался за 2с. Kotlin-логи не видят НИЖЕ JNI: не видно формирование
        // пар (P2PTransportChannel::CreateConnections), привязку портов к сети, pruning.
        // Включаем НАТИВНОЕ логирование libwebrtc в logcat — следующий Wi-Fi-лог покажет,
        // агент считает пары и от чего они отмирает. Тег в logcat — "logging" (не
        // org.webrtc.*): в фильтр захвата добавить logging:I. Сверено по classes.jar
        // 1.3.10: Logging.enableLogToDebugOutput(Severity)V, Severity.LS_INFO есть.
        runCatching {
            org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_INFO)
        }.onFailure { AppLog.w(TAG, "native log enable: ${it.message}") }
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
            // #CALLS-VIDEO-RX: сохраняем EglBase в поле (см. комментарий к полю) —
            // контекст нужен и factory (кодеки), и рендереру CallScreen.
            this@WebRtcEngine.eglBase?.release()
            val eglBase = org.webrtc.EglBase.create()
            this@WebRtcEngine.eglBase = eglBase
            val eglContext = eglBase.eglBaseContext
            // #CALLS-HW-DECODER (2026-09-01): ВСЕГДА DefaultVideoDecoderFactory.
            // SoftwareVideoDecoderFactory НЕ поддерживает H264 в переговорах — answer
            // уходит только с VP8/VP9, что на MTK даёт чёрный экран (TextureView не
            // композитится, а VP8/VP9 через SW-декодер не дают аппаратного слоя).
            // stripH265 вырезает H265 — H264 остаётся первым HW-кодеком, стабильным
            // на подавляющем большинстве Android-устройств.
            factory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(org.webrtc.DefaultVideoEncoderFactory(
                    eglContext, true /* enableIntelVp8Encoder */, true /* enableH264HighProfile */
                ))
                .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(eglContext))
                .createPeerConnectionFactory()
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
            // #CALLS-SYMMETRIC: видеозаглушка наружу (sendrecv) — до применения offer.
            startDummyVideoIfNeeded()
            localVideoTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("stream0"))
            }
            // #CALLS-OUT-SENDRECV (2026-09-01, лог 11:29 исходящий): m=video уходил
            // a=sendonly — addTrack создаёт транссивер с SEND_ONLY, а
            // prepareVideoTransceivers() вызывался только во ВХОДЯЩЕМ пути (перед
            // createAnswer после setRemoteDescription). Готовим транссиверы ЯВНО и
            // здесь: оффер m=video = sendrecv при videoRx=ON, иначе собеседник отвечает
            // recvonly и не пришлёт видео даже при живом ICE.
            prepareVideoTransceivers()
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
            // #CALLS-SYMMETRIC: видеозаглушка наружу — до применения buffered offer.
            startDummyVideoIfNeeded()
            localVideoTrack?.let { track ->
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
            // #CALLS-VIDEO-RX: сбрасываем ссылку на удалённое видео ДО close() —
            // UI (CallScreen) снимет sink и скроет рендерер.
            if (remoteVideoTrackRef != null) {
                remoteVideoTrackRef = null
                onRemoteVideoTrack?.invoke(null)
            }
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
            pcCreated = false
            pendingRemoteSdp = null
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            // #CALLS-SYMMETRIC: глушим помпу заглушки после close() PC.
            stopDummyVideo()
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
        // #CALLS-PC-RESTART: сохраняем offer для возможного рестарта (см. recreateAndReanswer).
        if (sessionDesc.type == SessionDescription.Type.OFFER) lastRemoteOffer = sessionDesc
        peerConnection?.setRemoteDescription(SdpObserverAdapter(
            onSetSuccess = {
                AppLog.i(TAG, "setRemoteSdp SUCCESS, pending=${pendingRemoteIce["remote"]?.size ?: 0}")
                drainPendingIceCandidates()
                // #CALLS-FIX: как в VK — createAnswer ТОЛЬКО после успешной
                // установки remote SDP (onSetSuccess), не сразу.
                if (sessionDesc.type == SessionDescription.Type.OFFER) {
                    // #CALLS-VIDEO-INACTIVE (2026-08-30, краш 21:50) → #CALLS-VIDEO-RX:
                    // направление видео задаётся ДО createAnswer. videoRx=RECEIVE →
                    // RECV_ONLY (принимаем, не отправляем), videoRx=OFF → INACTIVE
                    // (старое поведение: видео не согласуется, декодер не стартует).
                    prepareVideoTransceivers()
                    createAnswer()
                }
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
            // #NULL-ЯВНО (#NULL-EXPLICIT): явная проверка вместо прямого разыменования.
            // Формально ветка недостижима — urls перебираются из params.turnServer
            // (условие forEach выше), но компилятор не smart-cast'ит свойство чужого
            // модуля внутри лямбды. Ранний выход с логом вместо !!.
            if (turn == null) {
                AppLog.w(TAG, "setIceServers: turnServer == null при обходе urls — пропускаем TURN")
                return@forEach
            }
            runCatching {
                servers.add(
                    PeerConnection.IceServer.builder(url)
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
        // #CALLS-ICE-STATS-UI: пустые креденшелы TURN = аллокация 401 = relay-кандидатов
        // не будет (на экране «канд: … relay=0») — логируем сразу, не дожидаясь stats.
        val turn = params.turnServer
        // Явный null-check (не turn?.credential): после проверки username компилятор
        // smart-cast'ит turn в non-null и ругался «Unnecessary safe call» на credential.
        val credsOk = turn != null && !turn.username.isNullOrBlank() && !turn.credential.isNullOrBlank()
        AppLog.i(TAG, "setIceServers: ${servers.size} servers (stun=${params.stunServer?.urls}, turn=${turn?.urls}, creds=${if (credsOk) "есть" else "НЕТ/пустые"})")
    }

    fun release() {
        post {
            // #CALLS-VIDEO-RX: полный teardown — видео-трек, затем (после factory) EGL.
            if (remoteVideoTrackRef != null) {
                remoteVideoTrackRef = null
                onRemoteVideoTrack?.invoke(null)
            }
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
            pcCreated = false
            pendingRemoteSdp = null
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
            // #CALLS-SYMMETRIC: полный teardown — глушим помпу заглушки.
            stopDummyVideo()
            pendingRemoteIce.clear()
            factory?.dispose()
            factory = null
            // #CALLS-VIDEO-RX: EGL-контекст освобождаем ПОСЛЕ factory (см. поле eglBase).
            eglBase?.release()
            eglBase = null
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
        // #CALLS-PC-RESTART: оффер прошлого звонка не должен утечь в новый
        lastRemoteOffer = null
        // #CALLS-INLINE-ICE: новый PC — буфер кандидатов и режим отправки заново
        gatheredCandidates.clear()
        inlineIceSent = false
        sdpSendGen.incrementAndGet()
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
                // #CALLS-CAND-FILTER-2 (2026-09-01, лог 11:29): сбор тянет мусор —
                // 127.0.0.1/::1 (loopback), 0.0.0.0/:: (any-address), tcp. Inline-фильтр
                // вырезал их из SDP, но TRICKLE-ветка ушла бы КАК ЕСТЬ — мусорный
                // кандидат в addIceCandidate пира способен обрушить его ICE-агент.
                // Фильтруем ДО обеих веток: мусор не покидает процесс ни в каком виде.
                val addr = tokens.getOrNull(4) ?: ""
                if (proto == "tcp" || addr == "127.0.0.1" || addr == "::1" ||
                    addr == "0.0.0.0" || addr == "::"
                ) {
                    AppLog.d(TAG, "локальный кандидат ОТФИЛЬТРОВАН (tcp/loopback/any): $typ/$proto ${candidate.sdp.take(40)}")
                    return
                }
                // #CALLS-INLINE-ICE: официальный VK-клиент зашивает кандидаты ВНУТРЬ SDP.
                // Доказательство: лог 20:25 исходящий — answer от оф.клиента содержал
                // a=candidate:468136283... прямо в SDP. Trickle-кандидаты не добавляются.
                // До отправки SDP кандидаты копятся в буфере; после — trickle.
                if (inlineIceSent) {
                    AppLog.d(TAG, "локальный кандидат (trickle): $typ/$proto ${candidate.sdp.take(48)}")
                    onIceCandidateReady(candidate)
                } else {
                    gatheredCandidates.add(candidate)
                    AppLog.d(TAG, "локальный кандидат (буфер→inline): $typ/$proto ${candidate.sdp.take(48)}")
                }
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
                    PeerConnection.IceConnectionState.CHECKING -> {
                        // #CALLS-ICE-EARLYSTATS (2026-08-31, лог ciber.txt 22:55 Wi-Fi):
                        // 16с тишины между CHECKING и FAILED, пар=0 — неясно, пары вообще
                        // ФОРМИРОВАЛИСЬ (и умерли) или НЕ СОЗДАВАЛИСЬ вовсе. Снимок stats на
                        // 5-й секунде CHECKING отвечает на это в СЛЕДУЮЩЕМ логе: пар>0 при
                        // reqS>0 — проверки уходят и гибнут (сетевой уровень); пар=0 —
                        // агент не создаёт пар (уровень libwebrtc/кандидатов).
                        val gen = iceStateGen.get()
                        signalingHandler?.postDelayed({
                            if (gen == iceStateGen.get() && peerConnection != null) {
                                AppLog.w(TAG, "#CALLS-ICE-EARLYSTATS: 5с в CHECKING — снимок пар")
                                dumpIceStats()
                            }
                        }, 5_000L)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        // инвалидирует висящий EARLYSTATS-таймер (генерация изменилась)
                        iceStateGen.incrementAndGet()
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
                val track = receiver.track()
                if (track is AudioTrack) {
                    AppLog.i(TAG, "Remote audio track")
                } else if (track is VideoTrack) {
                    // #CALLS-VIDEO-RX (§11.2.3): удалённое видео появилось — отдаём UI
                    // (CallScreen подключит SurfaceViewRenderer через addSink и покажет).
                    remoteVideoTrackRef = track
                    AppLog.i(TAG, "Remote video track (id=${track.id()}, state=${track.state()})")
                    onRemoteVideoTrack?.invoke(track)
                }
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
        // #CALLS-AUDIO-OFFER (2026-08-30, тест 13:06): размер offer держим < ~4 КБ
        // (порог доставки сигналинг-канала). ЛЕГАСИ-констрейнты OfferToReceive*
        // УДАЛЕНЫ (#CALLS-OUT-SENDRECV-2, 2026-09-01, лог 17:02): OfferToReceiveVideo=false
        // в UnifiedPlan НЕ «игнорируется», а ВЫРЕЗАЕТ recv-половину у транссиверов с
        // локальным сендером: транссивер был SEND_RECV (лог 17:02:32.991), а offer
        // ушёл a=sendonly (лог 17:02:34) → официальный клиент отвечал без отдачи видео
        // → в ИСХОДЯЩИХ звонках (Wi-Fi 16:58 и LTE 17:02) у PinoK нет remote video track
        // вовсе (videoFrames не пишутся, onAddTrack только audio). Направление теперь
        // задаЁт ТОЛЬКО prepareVideoTransceivers() (вызов до createOffer в startCall).
        // Дополнительную recvonly m-линию UnifiedPlan не добавит: видео-транссивер
        // всегда существует (заглушка addTrack) — размер offer под контролем.
        val constraints = MediaConstraints()
        peerConnection?.createOffer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let { orig ->
                    // #CALLS-OFFER-STRIPH265: убран — offer всегда отправляется как есть
                    // (VK-совместимость, см. VK Web: мунгинг только в answer).
                    val offer = orig
                    // #CALLS-FIX: как в VK — SDP отправляем ТОЛЬКО после установки
                    // localDescription (onSetSuccess), иначе ICE gathering не стартует.
                    peerConnection?.setLocalDescription(SdpObserverAdapter(
                        onSetSuccess = {
                            AppLog.i(TAG, "setLocalDescription(offer) SUCCESS")
                            sendLocalSdpNow(offer)
                        },
                        onError = { err -> AppLog.e(TAG, "setLocalDescription error: $err") }
                    ), offer)
                }
                onCallPhaseChanged(CallPhase.CONNECTING)
            },
            onError = { err -> AppLog.e(TAG, "createOffer error: $err") }
        ), constraints)
    }

    /**
     * #CALLS-TOPOLOGY-RESTART (2026-08-30, лог 20:49, тест исходящего): перезапуск
     * ICE-агента и ПОЛНЫЙ новый offer-цикл: createOffer → setLocal → onLocalSdpReady.
     *
     * Зачем: topology-changed{SERVER} приходит, когда P2P-нога НЕ СОБРАЛАСЬ у
     * собеседника (тест 20:49: его answer содержал только 2 host-кандидата — ни
     * srflx, ни relay; наш агент послал 253 проверки — 0 ответов). Повторная отправка
     * СТАРОГО offer не создаёт новый транспорт: у него те же ice-ufrag/pwd и те же
     * мёртвые кандидаты. restartIce() даёт новые ufrag/pwd и свежую сборку кандидатов
     * (включая relay) — это последний шанс поднять медиа до того, как собеседник
     * сдастся (в тесте 20:49 он сбросил через 11с после topology-changed).
     *
     * @return true — restart запущен, свежий offer уйдёт через onLocalSdpReady.
     */
    fun restartIce(): Boolean {
        val pc = peerConnection ?: run {
            AppLog.w(TAG, "restartIce: PeerConnection нет — некому рестартовать")
            return false
        }
        return try {
            AppLog.i(TAG, "ICE RESTART: новый ufrag/pwd + свежая сборка кандидатов")
            pc.restartIce()
            // createOffer при pending negotiation-needed даст offer с новыми
            // ice-credentials; setLocal + sendLocalSdpNow отправят его собеседнику.
            createOffer()
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "restartIce error: ${e.message}")
            false
        }
    }

    /**
     * #CALLS-VIDEO-INACTIVE (2026-08-30, краш на входящем ВИДЕО-звонке, лог 21:50):
     * PinoK аудио-only, но в UnifiedPlan при setRemoteDescription(offer с m=video)
     * libwebrtc АВТОМАТИЧЕСКИ создаёт recvonly video-транссивер — и answer уходил
     * с АКТИВНЫМ m=video (a=recvonly), хотя OfferToReceiveVideo=false (эта
     * констрейнта работает только в Plan B и здесь игнорируется).
     * Итог краша: официальное приложение отправляло offer с 3 m-линиями
     * (audio+video+data), PinoK отвечал recvonly video; когда собеседник включал
     * камеру (media-settings-changed isVideoEnabled=true), видео-пакеты шли в
     * декодер (H265 — первый кодек в offer официального клиента) и процесс умирал
     * НАТИВНО — в логе ни одной Kotlin-строки, только «beginning of crash».
     * Фикс: перед createAnswer задать video-транссиверам направление (см.
     * prepareVideoTransceivers): videoRx=OFF → INACTIVE (видео не согласуется,
     * mid=1 остаётся — не мешает BUNDLE и ICE), videoRx=RECEIVE → RECV_ONLY
     * (Этап 1 приёма видео, CALLS_MAP §11.2).
     * stop() не используем — отклонённая m-линия (port 0) может отвалить
     * кандидаты, пришедшие с sdpMid=1.
     */
    private fun prepareVideoTransceivers() {
        // ВАЖНО: в Java-API org.webrtc константа называется RECV_ONLY (через подчёркивание),
        // НЕ RECVONLY (kRecvOnly — это нативный C++; в Kotlin-биндинге 'RECVONLY' не существует).
        // #CALLS-SYMMETRIC (01.09): с видеозаглушкой (videoTx) m=video отвечает SEND_RECV —
        // звонок симметричен, как у офиц. клиента (см. гипотезу в комментарии к полю).
        val canTx = videoTxEnabled && localVideoTrack != null
        val target = when {
            canTx && videoRxEnabled -> RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            canTx                   -> RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
            videoRxEnabled          -> RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
            else                    -> RtpTransceiver.RtpTransceiverDirection.INACTIVE
        }
        peerConnection?.transceivers?.forEach { tr ->
            if (tr.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                try {
                    tr.direction = target
                    AppLog.i(TAG, "video-транссивер → $target (videoTx=${if (canTx) "ON" else "OFF"}, videoRx=${if (videoRxEnabled) "RECEIVE" else "OFF"})")
                } catch (e: Exception) {
                    AppLog.w(TAG, "не удалось задать направление video-транссиверу: ${e.message}")
                }
            }
        }
    }

    // ══════════ #CALLS-SYMMETRIC: видеозаглушка наружу ══════════

    /**
     * #CALLS-SYMMETRIC (2026-09-01, лог 09:50 Wi-Fi): создать фиктивный источник видео
     * и помпу чёрных кадров (320×180@10fps, YUV-инъекция БЕЗ камеры и без разрешения
     * CAMERA). answer m=video → sendrecv: звонок становится симметричным, как у
     * официального клиента. Решает гипотезу пользователя: офиц. клиент мог не начинать
     * ICE-проверки против асимметричного recvonly-ответа в same-NAT Wi-Fi (наш answer
     * доставлен и ACKнут, пар=24/reqS=103 — от пира 0 проверок и 0 ответов).
     * Одновременно — заготовка Этапа 2: при включении камеры источник заменяется.
     * API сверено по classes.jar 1.3.10: PCF.createVideoSource(Z)V,
     * PCF.createVideoTrack(String,VideoSource), VideoSource.getCapturerObserver(),
     * CapturerObserver.onFrameCaptured(VideoFrame), JavaI420Buffer.allocate(II),
     * VideoFrame(Buffer,int,long).
     */
    private fun startDummyVideoIfNeeded() {
        if (!videoTxEnabled) {
            AppLog.i(TAG, "#CALLS-SYMMETRIC: выключено в настройках (callsVideoTx=false) — m=video ответит ${if (videoRxEnabled) "recvonly" else "inactive"}")
            return
        }
        if (dummyVideoSource != null) return
        val f = factory ?: return
        dummyVideoRunning = true
        val src = f.createVideoSource(false)
        dummyVideoSource = src
        localVideoTrack = f.createVideoTrack("video0", src)
        AppLog.i(TAG, "#CALLS-SYMMETRIC: видеозаглушка создана (video0: 320x180@10fps, чёрные кадры — БЕЗ камеры)")
        pumpDummyFrame()
    }

    /** Помпа чёрных кадров: 10fps на signaling thread (кадр ~86КБ — копейки). */
    private fun pumpDummyFrame() {
        if (!dummyVideoRunning) return
        try {
            val buffer = org.webrtc.JavaI420Buffer.allocate(320, 180)
            fillPlane(buffer.dataY, 16)   // чёрный в limited-range
            fillPlane(buffer.dataU, 128)
            fillPlane(buffer.dataV, 128)
            dummyVideoSource?.capturerObserver?.onFrameCaptured(
                org.webrtc.VideoFrame(buffer, 0, System.nanoTime())
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "#CALLS-SYMMETRIC: кадр заглушки не отправлен: ${e.message}")
        }
        signalingHandler?.postDelayed({ pumpDummyFrame() }, 100L)
    }

    /**
     * Заполнение плоскости YUV константой. Значение задаётся в unsigned-диапазоне
     * 0..255 и переводится в signed Byte внутри (128.toByte() == -128, те же биты
     * 0x80 — нейтральная хрома).
     * ВАЖНО: параметр НЕ Byte — литерал 128 не влезает в signed -128..127, и Kotlin
     * НЕ конвертирует его автоматически (ошибка «actual Int, expected Byte»);
     * 16 (Y-чёрный) влезает и компилируется молча — рассинхрон типов.
     */
    private fun fillPlane(plane: java.nio.ByteBuffer, value: Int) {
        plane.rewind()
        while (plane.hasRemaining()) plane.put(value.toByte())
    }

    private fun stopDummyVideo() {
        val wasRunning = dummyVideoRunning
        dummyVideoRunning = false
        localVideoTrack = null
        val src = dummyVideoSource
        dummyVideoSource = null
        if (src != null) {
            runCatching { src.dispose() }
                .onFailure { AppLog.w(TAG, "#CALLS-SYMMETRIC: dispose источника: ${it.message}") }
        }
        if (wasRunning) AppLog.i(TAG, "#CALLS-SYMMETRIC: видеозаглушка остановлена")
    }

    private fun createAnswer() {
        // #CALLS-AUDIO-OFFER (2026-08-30): симметрично createOffer — аудио-only answer,
        // без recvonly video m-line (см. комментарий в createOffer).
        // #CALLS-VIDEO-RX: реальное управление видео — в prepareVideoTransceivers()
        // (вызывается перед этим методом). #CALLS-OUT-SENDRECV-2 (01.09): легаси
        // OfferToReceive* убраны и здесь — они избыточны (направление задаЁт
        // транссивер), а в offer-пути тот же легаси активно ВРЕДИЛ (см. createOffer).
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(SdpObserverAdapter(
            onSuccess = { sdp ->
                sdp?.let { desc ->
                    // #CALLS-VIDEO-RX (§11.2.2): H265 (+ его rtx) вырезаем из answer —
                    // ТОЛЬКО в режиме RECEIVE (видео реально согласуется и пойдёт в
                    // декодер). В режиме OFF видео не согласуется (a=inactive), декодер
                    // не стартует — strip не нужен, и answer остаётся БИТ-В-БИТ как в
                    // работавшей серии 21:49–21:51 (#CALLS-VIDEO-INACTIVE). Так
                    // kill-switch callsVideoRx=OFF = точный откат на вчера-рабочий
                    // wire-формат одним тумблером, без пересборки.
                    // После strip первым выжившим становится H264 (HW-декодер почти
                    // везде), VP8/VP9 остаются SW-фолбэком.
                    var fixedDesc = if (videoRxEnabled) stripH265(desc.description)
                                    else desc.description
                    // #CALLS-VIDEO-INACTIVE (страховка, только videoRx=OFF): если
                    // транссивер по какой-то причине остался recvonly (setDirection
                    // не сработал/не применился), вырезаем активное видео на уровне
                    // SDP: a=recvonly → a=inactive ТОЛЬКО в секции m=video (аудио у
                    // PinoK всегда sendrecv — не заденем). В режиме RECEIVE demote
                    // НЕ применяем — ответ обязан остаться a=recvonly.
                    if (!videoRxEnabled && !videoTxEnabled) fixedDesc = demoteVideoRecvOnly(fixedDesc)
                    val answer = if (fixedDesc != desc.description) {
                        AppLog.w(TAG, "#CALLS-VIDEO-RX: answer изменён (${if (videoRxEnabled) "strip H265 (videoRx)" else "a=inactive (strip не применён)"}) — len ${desc.description.length}→${fixedDesc.length}")
                        SessionDescription(desc.type, fixedDesc)
                    } else desc
                    // #CALLS-FIX: как в VK — answer отправляем ТОЛЬКО после установки
                    // localDescription (onSetSuccess).
                    peerConnection?.setLocalDescription(SdpObserverAdapter(
                        onSetSuccess = {
                            AppLog.i(TAG, "setLocalDescription(answer) SUCCESS")
                            sendLocalSdpNow(answer)
                        },
                        onError = { err -> AppLog.e(TAG, "setLocalDescription error: $err") }
                    ), answer)
                }
            },
            onError = { err -> AppLog.e(TAG, "createAnswer error: $err") }
        ), constraints)
    }

    /**
     * #CALLS-VIDEO-INACTIVE: a=recvonly → a=inactive в секциях m=video.
     * Секции режем по границам "m=" (lookahead, разделители сохраняются).
     */
    private fun demoteVideoRecvOnly(sdp: String): String {
        if (!sdp.contains("a=recvonly")) return sdp
        return sdp.split("(?=m=)".toRegex())
            .joinToString("") { sec ->
                if (sec.startsWith("m=video")) sec.replace("a=recvonly", "a=inactive") else sec
            }
    }

    /**
     * #CALLS-VIDEO-RX (§11.2.2): удалить H265 (и его rtx) из секций m=video.
     * Payload-типы H265 ищем по a=rtpmap:<pt> H265/<rate>, rtx-потомки — по
     * a=fmtp:<rtx-pt> … apt=<h265-pt>. Удаляем их из списка m=video и вычищаем
     * все a=rtpmap/a=fmtp/a=rtcp-fb строки, ссылающиеся на эти payload'ы.
     * Остальные кодеки (H264/VP8/VP9/red/ulpfec и их rtx) не трогаем.
     * Секции режем по границам "m=" (lookahead, разделители сохраняются).
     */
    private fun stripH265(sdp: String): String {
        if (!sdp.contains("m=video")) return sdp
        val reH265Rtpmap = Regex("a=rtpmap:(\\d+) H265/")
        val reRtxApt = Regex("a=fmtp:(\\d+) .*apt=(\\d+)")
        return sdp.split("(?=m=)".toRegex()).joinToString("") { sec ->
            if (!sec.startsWith("m=video")) return@joinToString sec
            val pts = LinkedHashSet<String>()
            reH265Rtpmap.findAll(sec).forEach { pts.add(it.groupValues[1]) }
            reRtxApt.findAll(sec).forEach { m -> if (m.groupValues[2] in pts) pts.add(m.groupValues[1]) }
            if (pts.isEmpty()) return@joinToString sec
            val sep = if (sec.contains("\r\n")) "\r\n" else "\n"
            val endsWithSep = sec.endsWith(sep)
            val body = if (endsWithSep) sec.removeSuffix(sep) else sec
            // payload из строки a=rtpmap:NN / a=fmtp:NN / a=rtcp-fb:NN — без якоря '$'
            // (одиночный '$' в Kotlin-строке — ошибка компиляции), строка и так одна.
            val ptPrefixRe = Regex("^a=(?:rtpmap|fmtp|rtcp-fb):(\\d+)")
            val kept = ArrayList<String>(body.split(sep).size)
            for (line in body.split(sep)) {
                val pt = ptPrefixRe.find(line)?.groupValues?.get(1)
                when {
                    line.startsWith("m=video ") -> {
                        val parts = line.split(" ")
                        if (parts.size > 3) {
                            val keptPts = parts.drop(3).filter { it !in pts }
                            if (keptPts.isEmpty()) {
                                // аномалия: после удаления не осталось кодеков — секцию
                                // не трогаем (у официального всегда есть H264/VP8/VP9)
                                AppLog.w(TAG, "stripH265: в m=video после удаления H265 не осталось кодеков — секция не изменена")
                                kept.add(line)
                            } else {
                                kept.add((parts.take(3) + keptPts).joinToString(" "))
                            }
                        } else kept.add(line)
                    }
                    pt != null && pt in pts -> { /* строка H265/rtx — удаляем */ }
                    else -> kept.add(line)
                }
            }
            kept.joinToString(sep) + if (endsWithSep) sep else ""
        }
    }

    /**
     * #CALLS-UPDATE-ICE-SERVERS: обновить TURN/STUN credentials на текущем PC.
     * Вызов при новом `connection` (WS переподключение / topology→SERVER).
     * VK web: каждый connection несёт свежие credentials, adapter.js фильтрует —
     * оставляет 1 TURN UDP, STUN отсекает.
     */
    fun updateIceServers(turnUrls: List<String>, stunUrls: List<String>, username: String, credential: String) {
        val servers = mutableListOf<PeerConnection.IceServer>()
        stunUrls.forEach { url ->
            runCatching { servers.add(PeerConnection.IceServer.builder(url).createIceServer()) }
        }
        turnUrls.forEach { url ->
            runCatching {
                servers.add(
                    PeerConnection.IceServer.builder(url)
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )
            }
        }
        if (servers.isEmpty()) return
        iceServers = servers
        // VK web: adapter.js фильтрует — оставляет 1 TURN UDP, STUN отсекает.
        // Здесь просто заменяем конфиг — следующий PC (если пересоздаётся)
        // подхватит свежие серверы.
        AppLog.i(TAG, "updateIceServers: ${servers.size} серверов обновлено (turn=${turnUrls.size} stun=${stunUrls.size})")
    }

    /**
     * #CALLS-TOPOLOGY-RESTART (2026-08-30, лог 20:49, тест исходящего): перезапуск
     * ICE-агента и ПОЛНЫЙ новый offer-цикл: createOffer → setLocal → onLocalSdpReady.
     *
     * Зачем: topology-changed{SERVER} приходит, когда P2P-нога НЕ СОБРАЛАСЬ у
     * собеседника (тест 20:49: его answer содержал только 2 host-кандидата — ни
     * srflx, ни relay; наш агент послал 253 проверки — 0 ответов). Повторная отправка
     * СТАРОГО offer не создаёт новый транспорт: у него те же ice-ufrag/pwd и те же
     * мёртвые кандидаты. restartIce() даёт новые ufrag/pwd и свежую сборку кандидатов
     * (включая relay) — это последний шанс поднять медиа до того, как собеседник
     * сдастся (в тесте 20:49 он сбросил через 11с после topology-changed).
     *
     * #CALLS-VK-TOPOLOGY (2026-09-03, VK web pattern): topology-changed{SERVER}.
     * 5s wait → restartIce() → 20s timeout → если не помогло — вызывает onBounce.
     * @param onBounce колбэк для сигналинг-bounce (WS переподключение), если restart не помог. */
    fun handleTopologyServer(onBounce: () -> Unit) {
        val gen = iceStateGen.incrementAndGet()
        val genStr = "t$gen"
        AppLog.w(TAG, "TOPOLOGY→SERVER: запущен таймер 5с → restartIce() [${genStr}]")
        signalingHandler?.postDelayed({
            if (gen != iceStateGen.get() || peerConnection == null) return@postDelayed
            AppLog.w(TAG, "TOPOLOGY→SERVER: 5s wait done — restartIce() [${genStr}]")
            restartIce()
            val gen2 = iceStateGen.get()
            signalingHandler?.postDelayed({
                if (gen2 != iceStateGen.get() || peerConnection == null) return@postDelayed
                AppLog.w(TAG, "TOPOLOGY→SERVER: 20s после restart — ICE не помогло, bounce [${genStr}]")
                onBounce()
            }, 20_000L)
        }, 5_000L)
    }

    /**
     * #CALLS-ICE-DRAIN-LOG (2026-08-31, лог 22:25 Wi-Fi): сброс буферизованных удалённых
     * кандидатов после setRemoteDescription. addIceCandidate возвращает Boolean —
     * раньше результат ИГНОРИРОВАЛСЯ: при сбое кандидат молча терялся, ICE агент
     * оставался без пар и после таймаута падал в FAILED с «пар=0 • reqS=0»
     * (точно такой отчёт в логе 22:25:59). Теперь каждый сброс логируется.
     */
    private fun drainPendingIceCandidates() {
        pendingRemoteIce.remove("remote")?.forEach { candidate ->
            val ok = peerConnection?.addIceCandidate(candidate) ?: false
            if (ok) {
                AppLog.i(TAG, "drain: remote candidate добавлен (${candidate.sdp.take(48)})")
            } else {
                AppLog.w(TAG, "drain: addIceCandidate вернул FALSE — кандидат НЕ добавлен: ${candidate.sdp.take(48)}")
            }
        }
    }

    /**
     * #CALLS-INLINE-ICE: SDP отправляется с задержкой [INLINE_ICE_WAIT_MS] для сбора
     * кандидатов. Кандидаты зашиваются ВНУТРЬ SDP (a=candidate), как делает официальный
     * VK-клиент. Доказательство: лог 20:25 — answer оф.клиента содержал inline-кандидаты.
     * Trickle-кандидаты после отправки SDP — только для свежесобранных.
     */
    private fun sendLocalSdpNow(sdp: SessionDescription) {
        val gen = sdpSendGen.incrementAndGet()
        val scheduledAt = System.currentTimeMillis()
        gatheredCandidates.clear()
        inlineIceSent = false
        AppLog.i(TAG, "${sdp.type}: отправка через ${INLINE_ICE_WAIT_MS}мс — кандидаты уйдут ВНУТРИ SDP (#CALLS-INLINE-ICE)")
        signalingHandler?.postDelayed({
            if (gen != sdpSendGen.get()) return@postDelayed
            if (peerConnection == null) return@postDelayed
            val (finalSdp, inlined, skipped) = buildSdpWithCandidates(sdp.description, gatheredCandidates)
            gatheredCandidates.clear()
            inlineIceSent = true
            AppLog.i(TAG, "${sdp.type} → отправка (inline кандидатов: $inlined, отфильтровано loopback/tcp: $skipped; сбор ${System.currentTimeMillis() - scheduledAt}мс)")
            val toSend = SessionDescription(sdp.type, finalSdp)
            lastLocalSdp = toSend
            onLocalSdpReady(toSend)
        }, INLINE_ICE_WAIT_MS)
    }

    /**
     * #CALLS-INLINE-ICE: зашить ICE-кандидатов в текст SDP по своим m-секциям.
     */
    private fun buildSdpWithCandidates(sdp: String, candidates: List<IceCandidate>): Triple<String, Int, Int> {
        if (candidates.isEmpty()) return Triple(sdp, 0, 0)
        val sep = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.split(sep).toMutableList()
        val mIdx = mutableListOf<Int>()
        lines.forEachIndexed { i, l -> if (l.startsWith("m=")) mIdx.add(i) }
        if (mIdx.isEmpty()) return Triple(sdp, 0, 0)
        val useful = candidates.filterNot { c ->
            val isTcp = c.sdp.split(" ").getOrNull(2) == "tcp"
            val isLoopback = c.sdp.contains(" 127.0.0.1 ") || c.sdp.contains(" ::1 ")
            isTcp || isLoopback
        }
        val skipped = candidates.size - useful.size
        if (useful.isEmpty()) return Triple(sdp, 0, skipped)
        val bySection = useful.groupBy { c ->
            if (c.sdpMLineIndex in mIdx.indices) c.sdpMLineIndex else mIdx.size - 1
        }
        var inserted = 0
        for (sec in mIdx.indices.reversed()) {
            val list = bySection[sec] ?: continue
            val insertAtRaw = if (sec + 1 < mIdx.size) mIdx[sec + 1] else lines.size
            val insertAt = if (insertAtRaw > 0 && lines[insertAtRaw - 1].isEmpty()) insertAtRaw - 1 else insertAtRaw
            val candLines = list.map { "a=" + it.sdp }
            lines.addAll(insertAt, candLines)
            inserted += candLines.size
        }
        return Triple(lines.joinToString(sep), inserted, skipped)
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
     * #CALLS-VIDEO-RX (§11.2.4): framesDecoded по inbound-rtp видео — для UI-стража
     * «камера собеседника включена, а кадров нет» (плейсхолдер) и диагностики.
     * Колбэк приходит с потока getStats (signaling) — присваивание Compose-состоянию
     * из CallScreen потокобезопасно. best=-1: PC нет или видео inbound-rtp нет вообще
     * (пакеты не ходят); 0+: суммарно декодированные кадры.
     */
    fun pollVideoFramesDecoded(callback: (Int) -> Unit) {
        val pc = peerConnection ?: run { callback(-1); return }
        runCatching {
            pc.getStats { report ->
                try {
                    var best = -1
                    for (stat in report.statsMap.values) {
                        if (stat.type != "inbound-rtp") continue
                        val kind = stat.members["kind"]?.toString()
                            ?: stat.members["mediaType"]?.toString()
                        if (kind != "video") continue
                        val frames = stat.members["framesDecoded"]?.toString()?.toIntOrNull() ?: 0
                        if (frames > best) best = frames
                    }
                    callback(best)
                } catch (e: Exception) {
                    AppLog.w(TAG, "video stats parse error: ${e.message}")
                    callback(-1)
                }
            }
        }.onFailure { AppLog.w(TAG, "video stats API error: ${it.message}"); callback(-1) }
    }

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
                        } else if (stat.type == "inbound-rtp") {
                            // #CALLS-SWDECODE (01.09): decoderImplementation покажет,
                            // какой декодер реально пашет (HW MTK vs OpenH264) —
                            // соотнести с чёрным экраном/тумблером SW-декодера.
                            val k = stat.members["kind"]?.toString()
                                ?: stat.members["mediaType"]?.toString()
                            if (k == "video") {
                                AppLog.i(TAG, "video RX stats: decoder=${stat.members["decoderImplementation"]} framesDecoded=${stat.members["framesDecoded"]} framesReceived=${stat.members["framesReceived"]} keyFrames=${stat.members["keyFramesDecoded"]}")
                            }
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