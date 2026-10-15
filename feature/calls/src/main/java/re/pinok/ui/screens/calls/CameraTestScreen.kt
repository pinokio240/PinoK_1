package re.pinok.ui.screens.calls

// #CAMERA-TEST (2026-10-15): ОТЛАДОЧНЫЙ экран self-превью камеры ВНЕ звонка.
//
// Цель: доказать, что камера реально захватывает кадры через Camera2Enumerator +
// CameraVideoCapturer (тот же путь, что WebRtcEngine.tryStartCamera), и показать
// живой self-preview через org.webrtc.SurfaceViewRenderer.
//
// Дизайн: НЕ используем WebRtcEngine целиком (он тянет PeerConnection/сигналинг).
// Здесь минимальный стек: PeerConnectionFactory (нужен ТОЛЬКО чтобы произвести
// VideoSource/VideoTrack) + Camera2Enumerator + SurfaceViewRenderer + счётчик кадров
// через отдельный VideoSink. Никакого PeerConnection не создаётся.
//
// Потоки (важно, сверено с WebRTC-движком и реальным логом):
//  - все операции over native webrtc (factory.createVideoSource, startCapture,
//    SurfaceTextureHelper.create) выполняются на ОТДЕЛЬНОМ HandlerThread — создание
//    native-объектов с main-потока даёт нативный SIGABRT ("front() called on an
//    empty vector"). Комментарий класса WebRtcEngine (стр. 36-39).
//  - SurfaceViewRenderer.init + addSink/removeSink — на main-поток (CallScreen делает
//    так же: factory AndroidView рисует поверхность на main).
//  - Счётчик кадров: VideoSink.onFrame приходит с капчурера; делаем его
//    потокобезопасным (AtomicLong), UI читает значение из LaunchedEffect-поллинга.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import re.pinok.util.AppLog
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** #CAMERA-TEST: контроллер камеры для тестового экрана. Создаёт и держит все webrtc-объекты. */
class CameraTestController(private val context: Context) {

    companion object {
        private const val TAG = "CameraTest"
        private const val W = 1280
        private const val H = 720
        private const val FPS = 30
    }

    /** #CAMERA-TEST: число кадров, доставленных в sink (счётчик по VideoSink.onFrame). */
    val frameCount = AtomicLong(0)

    /** #CAMERA-TEST: текущий статус для текстовой диагностики на экране. */
    val status = AtomicReference("idle")

    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var source: org.webrtc.VideoSource? = null
    private var track: VideoTrack? = null
    private var helper: SurfaceTextureHelper? = null
    private var capturer: CameraVideoCapturer? = null
    private var renderer: SurfaceViewRenderer? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false
    @Volatile
    private var disposed = false
    // #CAMERA-TEST: какая камера сейчас активна. Сначала выбирается фронтальная.
    @Volatile
    private var currentIsFront = true

    // #CAMERA-TEST: отдельный sink-счётчик на том же треке, что и рендерер.
    private val countingSink = object : VideoSink {
        override fun onFrame(frame: VideoFrame) {
            frameCount.incrementAndGet()
        }
    }

    /** #CAMERA-TEST: подтверждение (растёт), что захват реально идёт кадрами. */
    fun hasStarted(): Boolean = started
    fun isCapturing(): Boolean = started && capturer != null

    /** #CAMERA-TEST: активная камера (фронтальная = true / тыловая = false). */
    fun isFrontCamera(): Boolean = currentIsFront

    /**
     * #CAMERA-TEST: смена камеры на лету. Используем switchCamera у того же capturer'а
     * (Camera2-реализация переключает камеру внутри, не пересоздавая объект/стек).
     * Северено с webrtc 1.3.10: switchCamera(CameraSwitchHandler) существует.
     */
    fun switchCamera() {
        if (!started || disposed) return
        val cam = capturer ?: return
        status.set("переключение камеры…")
        AppLog.i(TAG, "#CAMERA-TEST: switchCamera вызывается")
        mainHandler.post {
            cam.switchCamera(object : CameraSwitchHandler {
                override fun onCameraSwitchDone(isFront: Boolean) {
                    currentIsFront = isFront
                    status.set(if (isFront) "камера: ФРОНТ" else "камера: ТЫЛ")
                    AppLog.i(TAG, "#CAMERA-TEST: onCameraSwitchDone, isFrontCamera=$isFront")
                }

                override fun onCameraSwitchError(errorDescription: String) {
                    status.set("ошибка переключения: $errorDescription")
                    AppLog.w(TAG, "#CAMERA-TEST: onCameraSwitchError $errorDescription")
                }
            })
        }
    }

    /**
     * Запуск захвата. Вызывается с main-потока, реальная работа — на внутреннем
     * HandlerThread (native webrtc нельзя трогать с main). После готовности EGL —
     * инициализируем renderer на main и вешаем sink'и на трек.
     */
    fun start(renderer: SurfaceViewRenderer) {
        if (started || disposed) return
        this.renderer = renderer
        started = true
        status.set("запуск…")
        if (thread == null) {
            thread = HandlerThread("camera-test-thread").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        handler?.post { doStart() }
    }

    /** #CAMERA-TEST: native-инициализация и старт захвата (на HandlerThread). */
    private fun doStart() {
        if (disposed) return
        try {
            status.set("инициализация PeerConnectionFactory…")
            // #CAMERA-TEST: ПеерConnection НЕ создаём — factory нужна только для
            // createVideoSource/createVideoTrack. initialize обязателен до builder.
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    // field trials как в движке (безопасно, RED-opus нам не нужен, но
                    // формат идентичен рабочему WebRTC-движку).
                    .setFieldTrials("WebRTC-Audio-Red-For-Opus/Enabled-2/")
                    .createInitializationOptions()
            )
            // #CAMERA-TEST: AudioDeviceModule создаём так же, как WebRtcEngine.initialize —
            // без него createVideoSource может дать нативный краш libjingle
            // "front() called on an empty vector".
            val audioDeviceModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
            val egl = EglBase.create()
            eglBase = egl
            factory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(audioDeviceModule)
                // #CAMERA-TEST: encoder/decoder factory НЕ ставим — кодировать/декодировать
                // не нужно, камера пушит сырые кадры прямо в VideoSource.
                .createPeerConnectionFactory()
            val src = factory!!.createVideoSource(false)
            source = src
            track = factory!!.createVideoTrack("camera-test-video", src)

            status.set("поиск камеры…")
            val enumerator = Camera2Enumerator(context)
            val names = enumerator.deviceNames
            if (names.isEmpty()) {
                status.set("камеры не найдены")
                return
            }
            val device = names.firstOrNull { it.contains("front", ignoreCase = true) } ?: names[0]
            val eglCtx = egl.eglBaseContext
            val sth = SurfaceTextureHelper.create("camera-test", eglCtx)
            helper = sth
            val cam = enumerator.createCapturer(device, cameraEventsHandler)
            capturer = cam
            // #CAMERA-TEST: в качестве капчурер-обзервера переём source.capturerObserver —
            // VideoSource сам делает copy/release кадра (как в WebRtcEngine.tryStartCamera).
            cam.initialize(sth, context, src.capturerObserver)
            cam.startCapture(W, H, FPS)
            status.set("камера \"$device\" открыта, захват ${W}x$H@$FPS")

            // Инициализируем поверхность и вешаем sink'и на main.
            mainHandler.post {
                if (disposed) return@post
                val r = this.renderer
                val t = track
                if (r != null && t != null) {
                    runCatching {
                        val me = r
                        me.init(eglCtx, object : RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() {
                                AppLog.i(TAG, "#CAMERA-TEST: первый кадр отрисован на поверхности ✓")
                            }
                            override fun onFrameResolutionChanged(w: Int, h: Int, rotation: Int) {
                                AppLog.d(TAG, "#CAMERA-TEST: кадр ${w}x$h rot=$rotation")
                            }
                        })
                        t.addSink(me)
                        t.addSink(countingSink)
                    }.onFailure { e ->
                        status.set("renderer init error: ${e.message}")
                        AppLog.e(TAG, "#CAMERA-TEST: renderer: ${e.message}")
                    }
                    status.set("renderer подключён — превью идёт")
                }
            }
        } catch (e: Exception) {
            status.set("ошибка: ${e.message}")
            AppLog.w(TAG, "#CAMERA-TEST: doStart: ${e.message}")
        }
    }

    /** #CAMERA-TEST: общий CameraEventsHandler (лог + статус в UI). */
    private val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(errorDescription: String) {
            status.set("camera error: $errorDescription")
            AppLog.w(TAG, "#CAMERA-TEST: onCameraError $errorDescription")
        }
        override fun onCameraDisconnected() {
            AppLog.w(TAG, "#CAMERA-TEST: onCameraDisconnected")
        }
        override fun onCameraFreezed(errorDescription: String) {
            status.set("camera freezed: $errorDescription")
            AppLog.w(TAG, "#CAMERA-TEST: onCameraFreezed $errorDescription")
        }
        override fun onCameraOpening(cameraName: String) {
            status.set("камера \"$cameraName\" открывается…")
            AppLog.i(TAG, "#CAMERA-TEST: onCameraOpening $cameraName")
        }
        override fun onFirstFrameAvailable() {
            AppLog.i(TAG, "#CAMERA-TEST: onFirstFrameAvailable")
        }
        override fun onCameraClosed() {
            AppLog.i(TAG, "#CAMERA-TEST: onCameraClosed")
        }
    }

    /**
     * #CAMERA-TEST: полный teardown. Идемпотентен, безопасен при повторном вызове.
     * Снимает sink'и и релизит рендерер на main; остальное — на HandlerThread.
     */
    fun release() {
        disposed = true
        started = false
        val h = handler
        val post = {
            runCatching { capturer?.stopCapture() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: stopCapture: ${it.message}") }
            runCatching { capturer?.dispose() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: capturer.dispose: ${it.message}") }
            capturer = null
            runCatching { helper?.dispose() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: helper.dispose: ${it.message}") }
            helper = null
            val src = source
            source = null
            runCatching { src?.dispose() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: source.dispose: ${it.message}") }
            track = null
            runCatching { factory?.dispose() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: factory.dispose: ${it.message}") }
            factory = null
            runCatching { eglBase?.release() }.onFailure { AppLog.w(TAG, "#CAMERA-TEST: egl.release: ${it.message}") }
            eglBase = null
        }
        if (h != null) {
            h.post { post() }
            thread?.quitSafely()
            // #CAMERA-TEST-FIX (2026-10-15): обнуляем handler/thread ПОСЛЕ завершения
            // потока. Иначе повторный release() увидит не-null handler, указывающий на
            // МЁРТВЫЙ поток, и h.post {} упадёт: «Handler sending message to a Handler
            // on a dead thread» (краш в логе 17:03:52 — после закрытия теста камеры).
            thread = null
            handler = null
        } else {
            post()
        }
        // Renderer снимаем/релизим на main (это view).
        val r = renderer
        val t = track
        renderer = null
        if (r != null && t != null) {
            mainHandler.post {
                runCatching { t.removeSink(r) }
                runCatching { t.removeSink(countingSink) }
                runCatching { r.release() } // #CAMERA-TEST: release() идемпотентен у SurfaceViewRenderer
            }
        }
        status.set("остановлен")
    }
}

/** #CAMERA-TEST: ОТЛАДОЧНЫЙ экран self-превью камеры (вне звонка). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraTestScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var statusText by remember { mutableStateOf("Нажмите «Запросить камеру»") }
    var frames by remember { mutableStateOf(0L) }
    var capturing by remember { mutableStateOf(false) }
    var startRequested by remember { mutableStateOf(false) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    // #CAMERA-TEST: какая камера сейчас активна (для кнопки переключения).
    var frontCamera by remember { mutableStateOf(true) }

    val controller = remember { CameraTestController(appContext) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        statusText = if (granted) {
            "Разрешение получено. Нажмите «Запустить захват»"
        } else {
            "Нет разрешения CAMERA"
        }
    }

    // #CAMERA-TEST: UI читает потокобезопасные счётчики/статус через поллинг.
    LaunchedEffect(Unit) {
        while (true) {
            frames = controller.frameCount.get()
            statusText = controller.status.get()
            capturing = controller.isCapturing()
            frontCamera = controller.isFrontCamera()
            delay(180)
        }
    }

    // #CAMERA-TEST: старт захвата только когда запрошено И разрешение есть И renderer готов.
    LaunchedEffect(startRequested, hasPermission, renderer) {
        if (startRequested && hasPermission) {
            val r = renderer
            if (r != null && !controller.hasStarted()) {
                controller.start(r)
            }
        }
    }

    // #CAMERA-TEST: полный teardown при уходе с экрана.
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Camera Test (#CAMERA-TEST)") }, colors = TopAppBarDefaults.topAppBarColors()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Запросить разрешение камеры")
                }
            } else {
                Button(
                    onClick = { startRequested = true },
                    enabled = !controller.hasStarted()
                ) {
                    Text(if (controller.hasStarted()) "Захват запущен…" else "Запустить захват камеры")
                }
            }
            Button(
                onClick = { controller.switchCamera() },
                enabled = capturing
            ) {
                Text("Камера: ${if (frontCamera) "ФРОНТ → ТЫЛ" else "ТЫЛ → ФРОНТ"}")
            }
            Button(onClick = { controller.release() }) {
                Text("Остановить")
            }

            Text("Разрешение: ${if (hasPermission) "ЕСТЬ" else "НЕТ"}")
            Text("Статус: $statusText")
            Text("Захвачено кадров (sink.onFrame): $frames")
            Text(
                "Захват идёт: ${if (capturing) "ДА ✓" else "нет"}",
                color = if (capturing) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )

            Spacer(Modifier.height(8.dp))

            // #CAMERA-TEST: SurfaceViewRenderer во всю ширину; init происходит в
            // controller.start из LaunchedEffect (когда все условия выполнены).
            if (startRequested && hasPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                // #CAMERA-TEST: поверхность ПОВЕРХ окна — не перекрывается
                                // фоном NavHost (паттерн из CallScreen #CALLS-SURFACE-ZTOP).
                                setZOrderOnTop(true)
                            }.also { renderer = it }
                        },
                        update = { renderer = it },
                    )
                }
            } else {
                Text("Превью появится после запуска захвата.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}