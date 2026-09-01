package re.pinok.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import re.pinok.util.AppLog

/**
 * #CALLS-VIDEO-TEXVIEW (2026-08-31, лог ciber.txt 22:58, звонок по мобильной сети):
 * TextureView-рендерер удалённого видео на базе org.webrtc.EglRenderer.
 *
 * ХРОНИКА проблемы «экран чёрный при декодирующемся видео»:
 *  - лог 21:22 (LTE): кадры декодируются (framesDecoded растёт), экран чёрный.
 *    Гипотеза №1 — непрозрачный containerColor Scaffold поверх punch-through
 *    поверхности (#CALLS-VIDEO-BG) — не помогло.
 *  - лог 22:28 (LTE, фикс #CALLS-VIDEO-ZORDER): setZOrderMediaOverlay(true) +
 *    раскладка «видео сверху / панель снизу». В логе 22:58: videoFrames 0→2094,
 *    renderActive=true, «ПЕРВЫЙ КАДР отрисован на поверхности ✓» (RendererEvents
 *    proof) — поверхность РЕАЛЬНО рисовалась, а на экране/скриншоте всё равно чёрный.
 *  ВЫВОД: SurfaceView (отдельная аппаратная поверхность ЗА/НАД окном) на данном
 *  устройстве (HOTWAV Cyber 15, MediaTek, Android 13) не попадает в финальную
 *  композицию: hardware-overlay слой не пробивается сквозь иерархию
 *  (NavHost+вложенные Scaffold'ы+нижняя навигация) и/или не попадает в скриншот.
 *
 *  РЕШЕНИЕ: TextureView — ОБЫЧНЫЙ view-узел: композитится через GPU вместе с
 *  остальным окном, не имеет punch-through/z-order проблем, попадает в скриншоты.
 *  В артефакте stream-webrtc-android 1.3.10 TextureViewRenderer ОТСУТСТВУЕТ
 *  (сверено по classes.jar 2026-08-31), НО есть публичный EglRenderer:
 *    - ctor (String) и (String, VideoFrameDrawer)
 *    - init(EglBase.Context, int[] drawerAttributes, GlDrawer)  [GlDrawer=null —
 *      ровно то, что передаёт SurfaceViewRenderer.init(ctx, events) — отрисовано]
 *    - createEglSurface(Surface) / releaseEglSurface(Runnable)
 *    - onFrame(VideoFrame) — VideoSink
 *    - setLayoutAspectRatio(float), setFpsReduction(float), release()
 *
 *  Потоки: onFrame() приходит с декодерного потока — EglRenderer.onFrame
 *  потокобезопасен (внутренний render thread). SurfaceTextureListener вызывается
 *  на main. RendererEvents-эмуляция (первый кадр/смена резолюции) — сами, т.к.
 *  EglRenderer.init без RendererEvents (этот оверлоад есть только у
 *  SurfaceViewRenderer/SurfaceEglRenderer).
 */
class VideoTextureRenderer(context: Context) :
    TextureView(context), TextureView.SurfaceTextureListener, VideoSink {

    companion object {
        // ВАЖНО (лог 01.09 09:52): тег "VideoTextureRenderer" (>23 симв. с префиксом
        // PinoK/) НЕ попал в фильтр logcat пользователя — в дампе нет НИ ОДНОЙ строки
        // рендерера, хотя кадры доставлялись. Логируем через УЖЕ фильтруемый тег
        // "CallScreen" с префиксом [TexView] — теперь видимость гарантирована.
        private const val TAG = "CallScreen"
        private const val PREFIX = "[TexView] "
    }

    private val eglRenderer = EglRenderer("PinoKVideoRx")
    private var rendererEvents: org.webrtc.RendererCommon.RendererEvents? = null

    private var firstFrameReported = false
    private var reportedW = 0
    private var reportedH = 0
    private var reportedRot = 0
    private var frameCount = 0

    @Volatile
    private var released = false

    /**
     * Инициализация с общим EGL-контекстом движка (тот же контекст, что у
     * PeerConnectionFactory — декодер и рендерер делят текстуры).
     * ГЛАВНОЕ ОТЛИЧИЕ ОТ SurfaceViewRenderer: НИКАКИХ z-order вызовов —
     * TextureView рисуется в обычной иерархии окна.
     */
    fun init(eglContext: EglBase.Context, events: org.webrtc.RendererCommon.RendererEvents?) {
        rendererEvents = events
        surfaceTextureListener = this
        // GlDrawer=null: SurfaceViewRenderer.init(ctx, events) передаёт внутрь EglRenderer
        // ровно те же CONFIG_PLAIN + null-drawer (кодек/дровер по умолчанию) — путь отрисован.
        eglRenderer.init(eglContext, EglBase.CONFIG_PLAIN, null)
        AppLog.i(TAG, PREFIX + "init: EglRenderer готов (TextureView-путь, без z-order)")
    }

    /** VideoSink: трек (track.addSink) шлёт кадры сюда, дальше — в EglRenderer. */
    override fun onFrame(frame: VideoFrame) {
        // RendererEvents-эмуляция: как в SurfaceEglRenderer — первый кадр + смена
        // резолюции/поворота. Даёт решающий след в логе («кадры дошли до рендера»).
        val w = frame.rotatedWidth
        val h = frame.rotatedHeight
        val rot = frame.rotation
        frameCount++
        if (!firstFrameReported) {
            firstFrameReported = true
            AppLog.i(TAG, PREFIX + "ПЕРВЫЙ КАДР принят (${w}x$h rot=$rot)")
            rendererEvents?.onFirstFrameRendered()
        } else if (w != reportedW || h != reportedH || rot != reportedRot) {
            rendererEvents?.onFrameResolutionChanged(w, h, rot)
        } else if (frameCount % 150 == 0) {
            // Лог №150/№300/... — доказательство НЕПРЕРЫВНОЙ доставки в sink
            // (в логе 01.09 виден был только первый кадр).
            AppLog.i(TAG, PREFIX + "кадр #$frameCount принят (${w}x$h)")
        }
        reportedW = w
        reportedH = h
        reportedRot = rot
        eglRenderer.onFrame(frame)
    }

    // ══ TextureView.SurfaceTextureListener ══

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        eglRenderer.setLayoutAspectRatio(if (height == 0) 1f else width.toFloat() / height)
        // #CALLS-VIDEO-TEXVIEW-2 (01.09): ЯВНЫЙ размер буфера SurfaceTexture — без него
        // часть устройств даёт 1x1/некорректный буфер → GL рисует «в никуда» → чёрный
        // экран при доставляющихся кадрах. TextureView сам выставляет размер по вью
        // в draw(), но мы страхуемся ДО создания EGL-поверхности.
        st.setDefaultBufferSize(width.coerceAtLeast(2), height.coerceAtLeast(2))
        runCatching { eglRenderer.createEglSurface(Surface(st)) }
            .onFailure { AppLog.e(TAG, PREFIX + "createEglSurface: ${it.message}") }
        AppLog.i(TAG, PREFIX + "surface доступен ${width}x$height (буфер ${st.defaultBufferSize.width}x${st.defaultBufferSize.height}) → EGL-поверхность создана")
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        eglRenderer.setLayoutAspectRatio(if (height == 0) 1f else width.toFloat() / height)
        st.setDefaultBufferSize(width.coerceAtLeast(2), height.coerceAtLeast(2))
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        // SurfaceTexture освобождаем ТОЛЬКО после демонтажа EGL-поверхности
        // (иначе краш/ANR в нативном рендер-цикле). releaseEglSurface(run) —
        // официальный способ (сверено: дескриптор (Ljava/lang/Runnable;)V).
        runCatching { eglRenderer.releaseEglSurface { st.release() } }
            .onFailure {
                AppLog.w(TAG, PREFIX + "releaseEglSurface: ${it.message}")
                runCatching { st.release() }
            }
        return false // false = саму текстуру освобождаем мы (в колбэке выше), не TextureView
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    /**
     * Полный демонтаж. РОВНО ОДИН раз (повторный release у EglRenderer даёт
     * IllegalStateException — CallScreen вызывает это из onDispose).
     */
    fun releaseRenderer() {
        if (released) return
        released = true
        surfaceTextureListener = null
        runCatching { eglRenderer.release() }
            .onFailure { AppLog.w(TAG, PREFIX + "release: ${it.message}") }
        AppLog.i(TAG, PREFIX + "releaseRenderer: рендерер освобождён (кадров в sink: $frameCount)")
    }
}
