package re.pinok.media

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import re.pinok.util.AppLog
import java.nio.ByteBuffer

/**
 * #CALLS-REAL-CAMERA (2026-10-15): захват камеры через CameraX (ImageAnalysis) вместо
 * Camera2Enumerator+SurfaceTextureHelper. Кадры ImageProxy (YUV_420_888) КОПИРУЮТСЯ в
 * org.webrtc.VideoFrame и отправляются в videoSource.capturerObserver.onFrameCaptured.
 *
 * Почему CameraX: прежний Camera2-капчер на некоторых устройствах давал «чёрный экран
 * при растущем счётчике кадров» (кадры текли в onFrameCaptured, но в приёмник уходили
 * пустые/нулевые буферы — известная деградация Camera2-капчера у stream-webrtc-android).
 * CameraX сам корректно ведёт Camera2-сессию (поворот учтён через rotationDegrees, буферы
 * ровные) — остаётся скопировать YUV_420_888 в VideoFrame и передать в VideoSource.
 *
 * Зеркалирование фронтальной камеры: TODO (первая итерация) — пиксели НЕ отзеркалены,
 * горизонтального отражения строк при копировании нет. Поворот задан в поле
 * VideoFrame.rotationDegrees, буфер остаётся w×h.
 *
 * #FIX-20161015 (баг «кадр не отправлен: newPosition > limit: (614400 > 614399)»):
 * Кадр НЕ является 1280×720. ResolutionStrategy(Size(1280,720),
 * FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER) на устройстве выбирает БЛИЖАЙШИЙ ВЫШЕ размер
 * (напр. 1920×1280 или 960×640 — ровно 614400 байт плоскостей), поэтому proxy.width/height,
 * rowStride и pixelStride не совпадают с заложенной в цикл геометрией 720p.
 * Прежний deinterleave при pixelStride>1 делал src.position(src.position()+(pixelStride-1)),
 * и на конце строки/кадра этот сдвиг выходил за src.limit() → IllegalArgumentException
 * "newPosition > limit: (<pos> > <limit>)" (пойман в pushFrame как "кадр не отправлен").
 * Новая конвертация скан/запись только АБСОЛЮТНЫМИ индексами (get(index)/put(index,byte)),
 * не мутирует position и никогда не выходит за пределы ни src, ни dst — размер кадра больше
 * не важен. Геометрия кадра логируется на первом кадре и при ошибке.
 */
class CameraXVideoCapture(
    private val appContext: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val TAG = "CameraXVideoCapture"
    private var videoSource: VideoSource? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var provider: ProcessCameraProvider? = null
    @Volatile
    private var capturing = false
    @Volatile
    private var geometryLogged = false

    /** #CAMERA-TOGGLE-FIX: реально ли идёт захват камеры (используется WebRtcEngine). */
    fun isCapturing(): Boolean = capturing

    /**
     * Запустить захват и направить кадры в videoSource.
     * Может вызываться с signaling-потока; bind выполняется на главном (main) потоке
     * (требование ProcessCameraProvider.bindToLifecycle).
     * @return true — bind инициирован (кадры полетят асинхронно после bindToLifecycle);
     *         false — bind не удалось запустить (нет прав/камеры/ошибка).
     */
    fun start(videoSource: VideoSource, frontCamera: Boolean = true): Boolean {
        this.videoSource = videoSource
        return try {
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val selector = if (frontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(appContext)) { proxy ->
                            pushFrame(proxy)
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
                        imageAnalysis = analysis
                        capturing = true
                        AppLog.i(TAG, "CameraX: камера запущена (front=$frontCamera, target=1280x720)")
                    } catch (e: Exception) {
                        AppLog.w(TAG, "CameraX: bindToLifecycle не удался: ${e.message}")
                        capturing = false
                    }
                },
                ContextCompat.getMainExecutor(appContext),
            )
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "CameraX: не удалось получить ProcessCameraProvider: ${e.message}")
            this.videoSource = null
            false
        }
    }

    fun stop() {
        capturing = false
        videoSource = null
        val a = imageAnalysis
        imageAnalysis = null
        val p = provider
        provider = null
        try {
            a?.clearAnalyzer()
        } catch (_: Exception) {
        }
        if (p != null) {
            // unbindAll — только на главном потоке (требование CameraX).
            try {
                ContextCompat.getMainExecutor(appContext).execute { runCatching { p.unbindAll() } }
            } catch (_: Exception) {
            }
        }
    }

    /** Приём кадра ImageAnalysis → конвертация в VideoFrame → push в source.capturerObserver. */
    private fun pushFrame(proxy: ImageProxy) {
        try {
            val src = videoSource ?: return
            logGeometryOnce(proxy)
            val frame = convertToVideoFrame(proxy) ?: return
            try {
                src.capturerObserver.onFrameCaptured(frame)
            } finally {
                frame.release()
            }
        } catch (e: Exception) {
            AppLog.w(
                TAG,
                "CameraX: кадр не отправлен: ${e.message} | " +
                    "proxy ${proxy.width}x${proxy.height} | " +
                    "planes ${planeGeometry(proxy)}"
            )
        } finally {
            proxy.close()
        }
    }

    /** Логирует реальную геометрию кадра один раз (для диагностики реального размера камеры). */
    private fun logGeometryOnce(proxy: ImageProxy) {
        if (geometryLogged) return
        geometryLogged = true
        AppLog.i(TAG, "CameraX: реальная геометрия кадра ${proxy.width}x${proxy.height} | planes:")
        proxy.planes.forEachIndexed { i, p ->
            AppLog.i(
                TAG,
                "  plane[$i] rowStride=${p.rowStride} pixelStride=${p.pixelStride} " +
                    "pos=${p.buffer.position()} limit=${p.buffer.limit()}"
            )
        }
    }

    /** Строит строку геометрии плоскостей кадра (для лога ошибки). */
    private fun planeGeometry(proxy: ImageProxy): String {
        return proxy.planes.joinToString("; ") { p ->
            "s=${p.rowStride}/px=${p.pixelStride}/l=${p.buffer.limit()}"
        }
    }

    /**
     * Копирующий конвертер ImageProxy (YUV_420_888) → VideoFrame(I420).
     * ВАЖНО: копируем ВСЕГДА (ImageProxy принадлежит CameraX, VideoFrame — WebRTC;
     * оба требуют собственный релиз — без копии будет двойное освобождение / невалидный буфер).
     * Поворот НЕ применяем к пикселям — кладём в VideoFrame.rotationDegrees; буфер остаётся w×h,
     * энкодер учтёт поворот.
     * Буферы выделяем по реальным proxy.width/height; копирование — через copyPlaneSafely,
     * которое опирается на rowStride/pixelStride плоскостей и НЕ переполняет ни src, ни dst.
     */
    private fun convertToVideoFrame(proxy: ImageProxy): VideoFrame? {
        val image = proxy.image ?: return null
        val w = proxy.width
        val h = proxy.height
        if (w <= 0 || h <= 0) return null

        val buffer = JavaI420Buffer.allocate(w, h)
        val dstY = buffer.dataY
        val dstU = buffer.dataU
        val dstV = buffer.dataV

        // Y — full-resolution plane, копируем построчно с учётом rowStride/pixelStride источника.
        if (!copyPlaneSafely(proxy.planes[0], dstY, w, w, h)) {
            AppLog.w(TAG, "CameraX: copyPlaneSafely(Y) обрезан: ${planeGeometry(proxy)}")
        }

        // Хрома — полуразрешение по обеим осям (4:2:0). pixelStride>1 (semi-planar NV12/NV21)
        // обрабатывается смещением col*pixelStride внутри copyPlaneSafely.
        val chromaW = (w + 1) / 2
        val chromaH = (h + 1) / 2

        if (!copyPlaneSafely(proxy.planes[1], dstU, chromaW, chromaW, chromaH)) {
            AppLog.w(TAG, "CameraX: copyPlaneSafely(U) обрезан: ${planeGeometry(proxy)}")
        }
        if (!copyPlaneSafely(proxy.planes[2], dstV, chromaW, chromaW, chromaH)) {
            AppLog.w(TAG, "CameraX: copyPlaneSafely(V) обрезан: ${planeGeometry(proxy)}")
        }

        return VideoFrame(buffer, proxy.imageInfo.rotationDegrees, System.nanoTime())
    }

    /**
     * Построчное копирование плоскости Y/U/V в плотный (packed) I420-буфер, устойчивое к любой
     * геометрии кадра (pixelStride 1 или 2, rowStride с выравниванием) и к любому реальному
     * размеру (720p / больше / меньше, поворот и т.п.).
     *
     * ВАЖНО против бага "newPosition > limit": работаем ТОЛЬКО абсолютными индексами
     * src.get(absIndex)/dst.put(absIndex), позиция буферов НЕ мутируется, поэтому:
     *   - никогда не выбрасывается "newPosition > limit" от ByteBuffer.position(int);
     *   - выход за пределы src (srcLimit) или dst (dstLimit) приводит к раннему обрыву с
     *     возвратом false, НЕ к исключению.
     *
     * @param plane плоскость ImageProxy (buffer/rowStride/pixelStride).
     * @param dst целевой плотный буфер (dataY/dataU/dataV).
     * @param dstStride шаг строки в dst (при копировании хромы = chromaW, памяти ровно столько же).
     * @param width ширина копируемой области (для Y = w, для хромы = chromaW).
     * @param height высота копируемой области (для Y = h, для хромы = chromaH).
     * @return true — все height строк записаны полностью; false — запись обрезана (слаболее
     *         данных в src или не хватило места в dst), исключение НЕ выбрасывается.
     */
    private fun copyPlaneSafely(
        plane: ImageProxy.PlaneProxy,
        dst: ByteBuffer,
        dstStride: Int,
        width: Int,
        height: Int,
    ): Boolean {
        val src = plane.buffer
        val srcStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val srcLimit = src.limit()
        val dstLimit = dst.limit()
        val srcBase = src.position()
        var complete = true
        for (row in 0 until height) {
            val srcRow = srcBase + row * srcStride
            val dstRow = row * dstStride
            // Места в dst на строку — обязательно хватает; иначе обрыв.
            if (dstRow + width > dstLimit) {
                complete = false
                break
            }
            for (col in 0 until width) {
                val si = srcRow + col * pixelStride
                // Защита от чтения за пределы src (источник реально меньше, чем следовало бы).
                if (si >= srcLimit) {
                    complete = false
                    break
                }
                dst.put(dstRow + col, src.get(si))
            }
            if (!complete) break
        }
        return complete
    }
}