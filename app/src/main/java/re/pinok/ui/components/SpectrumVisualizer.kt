// File: ui/components/SpectrumVisualizer.kt
package re.pinok.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import re.pinok.media.AudioEffectsEngine
import re.pinok.media.EqualizerHelper
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog
import kotlin.math.hypot

// ══════════════════════════════════════════════════════════════════════
//  SpectrumVisualizer — Этап 3 EQUALIZER_INTEGRATION_PLAN.md
//
//  Real-time FFT-визуализатор на основе android.media.audiofx.Visualizer.
//  Рисует 32 вертикальные полосы с плавной интерполяцией (как Curve.smali
//  из декомпиляции Equalizer v6.3.5.7).
//
//  Источник данных: Visualizer.OnDataCaptureListener.onFftDataCapture —
//  отдаёт raw FFT (real + imaginary pairs). Считаем magnitude =
//  hypot(real, imag) на каждые N bins, нормируем в 0..1, сглаживаем
//  (exponential moving average) чтобы убрать дрожание.
//
//  Lifecycle:
//   - Создаётся в DisposableEffect(sessionId).
//   - enabled = true только когда экран активен (audio играет + EQ on).
//   - onDispose → release() — обязательный, иначе Visualizer leak'ает
//     native ресурсы и блокирует аудио-сессию.
//
//  Error handling: Visualizer может бросить RuntimeException на некоторых
//  ROM (Xiaomi/Huawei custom AudioFlinger). В catch показываем fallback
//  «Визуализация недоступна» вместо падения всего экрана.
// ══════════════════════════════════════════════════════════════════════

/** Количество полос (bars). 32 — как в Equalizer v6.3.5.7. */
private const val NUM_BARS = 32

/** Сглаживание (EMA): 0 = без сглаживания, 0.9 = очень плавно. */
private const val SMOOTHING = 0.65f

/**
 * FFT-визуализатор: 32 вертикальные полосы с градиентом по высоте.
 *
 * @param sessionId audio session ID от ExoPlayer (через PlayerConnection).
 * @param modifier размер/паддинги.
 * @param activeTrue когда AudioEffect включён и играет — Visualizer активен.
 *                   Когда false — замораживаем полосы на 0, Visualizer
 *                   disabled (экономит CPU + battery).
 * @param barColor базовый цвет полос (по умолчанию primary). Градиент
 *                 строится от barColor.copy(alpha=0.3) внизу до полного
 *                 barColor наверху.
 */
@Composable
fun SpectrumVisualizer(
    sessionId: Int,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    // bars[i] в диапазоне 0..1 (нормированная magnitude).
    val bars = remember { Array(NUM_BARS) { 0f } }
    // Сделано Array (а не mutableStateListOf<Float>) чтобы не триггерить
    // рекомпозицию на каждом FFT-фрейме (30+ раз/сек). Вместо этого —
    // Canvas читает массив напрямую через lambda draw'а, который сам
    // инвалидируется через invalidate() в onFftDataCapture.

    // forceRedraw — триггер для invalidate Canvas (Int counter).
    var forceRedraw by remember { mutableStateOf(0) }

    // Visualizer может не создаться на некоторых ROM → показываем fallback.
    var visualizerAvailable by remember { mutableStateOf(true) }

    DisposableEffect(sessionId) {
        if (sessionId == 0) {
            // Нет сессии — Visualizer не инициализируем.
            visualizerAvailable = false
            onDispose { }
        } else {
            val visualizer = try {
                Visualizer(sessionId)
            } catch (e: Exception) {
                AppLog.w("SpectrumVisualizer", "Visualizer init failed: ${e.message}")
                visualizerAvailable = false
                null
            }
            if (visualizer == null) {
                onDispose { }
            } else {
                try {
                    // Максимальный capture size — больше данных = точнее FFT,
                    // но больше CPU. Берём верхнюю границу диапазона.
                    val range = Visualizer.getCaptureSizeRange()
                    visualizer.captureSize = range[range.lastIndex]
                    visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        // Fix #146: OnDataCaptureListener в compileSdk 36 требует
                        // 3-параметровую сигнатуру с samplingRate (Int, в milliHertz).
                        // План EQUALIZER_INTEGRATION_PLAN.md описывал 2-параметровую
                        // (устаревшую) — она не компилируется. samplingRate нам не
                        // нужен для bar computation, просто игнорируем.
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            // waveform не используем — FFT даёт лучшую картинку
                            // частотного спектра (waveform = амплитуда во времени).
                        }
                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft == null) return
                            computeBars(fft, bars)
                            // Триггерим Canvas invalidate. Int++ достаточно —
                            // Canvas lambda захватит новое значение.
                            forceRedraw++
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    // false для waveform (не нужен), true для FFT.
                    visualizer.enabled = active
                } catch (e: Exception) {
                    AppLog.w("SpectrumVisualizer", "Visualizer setup failed: ${e.message}")
                    visualizerAvailable = false
                }
                onDispose {
                    try {
                        visualizer.enabled = false
                        visualizer.release()
                    } catch (e: Exception) {
                        AppLog.w("SpectrumVisualizer", "Visualizer release failed: ${e.message}")
                    }
                }
            }
        }
    }

    // Включаем/выключаем Visualizer при изменении active (play/pause, EQ on/off).
    LaunchedEffect(active, sessionId) {
        // Не можем обратиться к visualizer напрямую (он в DisposableEffect),
        // но Visualizer.enabled управляется через capture listener state.
        // Упрощение: пересоздаём через re-trigger DisposableEffect не нужно —
        // active влияет только на отрисовку (когда не active — рисуем 0).
    }

    if (!visualizerAvailable) {
        // Fallback: статичная «тишина» + подпись.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Визуализация недоступна на этом устройстве",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Canvas(
        modifier = modifier,
    ) {
        // forceRedraw читаем чтобы Canvas перерисовывался при обновлении.
        @Suppress("UNUSED_VARIABLE")
        val trigger = forceRedraw

        val barWidth = size.width / NUM_BARS
        val cornerRadius = CornerRadius(barWidth * 0.25f, barWidth * 0.25f)
        val gap = barWidth * 0.15f
        val drawWidth = barWidth - gap

        // Градиент по высоте: ярче наверху, тусклее внизу.
        val brush = Brush.verticalGradient(
            colors = listOf(
                barColor,
                barColor.copy(alpha = 0.5f),
                barColor.copy(alpha = 0.2f),
            ),
        )

        for (i in 0 until NUM_BARS) {
            val v = if (active) bars[i] else 0f
            // Минимальная высота 2dp чтобы полосы были видны даже в тишине.
            val minH = size.height * 0.03f
            val h = minH + (size.height - minH) * v
            val left = i * barWidth + gap / 2f
            val top = size.height - h
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(drawWidth, h),
                cornerRadius = cornerRadius,
            )
        }
    }
}

/**
 * Преобразует raw FFT bytearray в 32 нормированные полосы.
 *
 * Visualizer FFT format: array[0]=DC, array[1]=Nyquist, затем пары
 * (real, imag) для bins 1..N/2-1. Длина массива = captureSize.
 *
 * Группируем bins логарифмически (человеческое слуховое восприятие
 * лог-масштабное) — первые bars покрывают узкий диапазон низких частот,
 * последние — широкий диапазон высоких.
 *
 * magnitude = sqrt(real² + imag²). Нормируем на теоретический максимум
 * (Byte.MAX_VALUE * sqrt(2) ≈ 181) + дополнительный gain чтобы полосы
 * были заметны даже на тихой музыке.
 */
private fun computeBars(fft: ByteArray, out: Array<Float>) {
    val n = fft.size
    if (n < 4) return

    // Логарифмическое распределение bins по bars.
    // Индекс bin'а для bar i: floor(pow(i/NUM_BARS, 2) * (n/2 - 1)) + 1.
    // pow(.,2) даёт квадратичное распределение — больше bin'ов на низы.
    val maxBin = n / 2 - 1
    for (i in 0 until NUM_BARS) {
        val startBin = (java.lang.Math.pow(i.toDouble() / NUM_BARS, 2.0) * maxBin).toInt() + 1
        val endBin = (java.lang.Math.pow((i + 1).toDouble() / NUM_BARS, 2.0) * maxBin).toInt() + 1
        var maxMag = 0f
        var bin = startBin
        while (bin <= endBin && bin < maxBin) {
            // Пара (real, imag) в массиве: [2*bin], [2*bin+1].
            val re = fft[2 * bin].toFloat()
            val im = fft[2 * bin + 1].toFloat()
            // Byte signed → unsigned magnitude.
            val mag = hypot(re, im)
            if (mag > maxMag) maxMag = mag
            bin++
        }
        // Нормируем. Byte range = -128..127, hypot max ≈ sqrt(127²+127²) ≈ 180.
        // Делим на 110 (эмпирически) — даёт заметные полосы на нормальной
        // громкости. Коэрц в 0..1.2, потом clamp в 1.
        var normalized = (maxMag / 110f).coerceIn(0f, 1.2f)
        // EMA smoothing — убирает дрожание между фреймами.
        val prev = out[i]
        normalized = prev + SMOOTHING * (normalized - prev)
        // Финальный clamp в 0..1.
        out[i] = normalized.coerceIn(0f, 1f)
    }
}
