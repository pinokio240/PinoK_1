// File: media/VideoPlayerConfig.kt
package re.pinok.media

import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * #VIDEO-NET (2026-10-02): единый конфиг буферизации для ВСЕХ видео-ExoPlayer
 * (полноэкранный плеер, клипы, «кружки», истории-видео, PiP, фоновое видео
 * через VideoPlaybackBus).
 *
 * По образцу аудио PlayerService (DefaultLoadControl 30s/120s/2.5s/5s), но с
 * учётом специфики видео:
 *  - видео-потоки тяжелее аудио → буфер считаем по ВРЕМЕНИ, а не по байтам
 *    (setPrioritizeTimeOverSizeThresholds(true) на слабой сети);
 *  - back buffer для перемотки назад при скачках (retainBackBufferFromKeyframe=false);
 *  - на мобильной сети (не Wi-Fi) — более щадящий буфер: реже обрывы и быстрее
 *    возобновление после ребуферинга.
 *
 * НЕ трогаем аудио PlayerService/PlayerConnection — там свои значения буфера.
 */
object VideoPlayerConfig {

    // ── Wi-Fi / Ethernet (стабильная сеть) ──
    private const val BUFFER_MIN_MS_WIFI = 8_000
    private const val BUFFER_MAX_MS_WIFI = 40_000

    // ── Мобильная / оффлайн / нестабильная сеть (более щадящий) ──
    private const val BUFFER_MIN_MS_MOBILE = 12_000
    private const val BUFFER_MAX_MS_MOBILE = 60_000

    // Общие для всех: скорость возобновления после паузы и после ребуферинга.
    private const val BUFFER_FOR_PLAYBACK_MS = 2_500
    private const val BUFFER_FOR_REBUFFER_MS = 5_000

    // Back buffer: сколько млс хранить ПЕРЕД текущей позицией (для перемотки
    // назад при скачках на слабой сети). retainBackBufferFromKeyframe=false —
    // держим ровно [BACK_BUFFER_MS], не до ближайшего keyframe.
    private const val BACK_BUFFER_MS = 30_000

    // Wake mode для ФОНОВОГО воспроизведения сетевого видео: NETWORK держит CPU
    // активным для докачки, чтобы плеер не засыпал при погашенном экране.
    const val WAKE_MODE_NETWORK: Int = C.WAKE_MODE_NETWORK

    /** True, если текущая сеть стабильная (Wi-Fi/Ethernet). Паттерн как в
     *  PlayerConnection.isOnWifiForPrecache(). */
    fun isOnWifi(): Boolean {
        return try {
            val type = re.pinok.SovaApp.getOrNull()?.networkObserver?.connectionType() ?: return false
            type == "Wi-Fi" || type == "Ethernet"
        } catch (_: Exception) { false }
    }

    /**
     * DefaultLoadControl для видео-ExoPlayer. Выбирает буфер под текущую сеть:
     * на Wi-Fi — 8s/40s, на мобильной — 12s/60s (реже обрывы, быстрее
     * возобновление после ребуферинга).
     */
    fun defaultLoadControl(isOnWifi: Boolean): DefaultLoadControl {
        return if (isOnWifi) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    BUFFER_MIN_MS_WIFI, BUFFER_MAX_MS_WIFI,
                    BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_REBUFFER_MS,
                )
                .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ false)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    BUFFER_MIN_MS_MOBILE, BUFFER_MAX_MS_MOBILE,
                    BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_REBUFFER_MS,
                )
                .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ false)
                // На слабой сети приоритет ВРЕМЕНИ над размером: продолжаем
                // заполнять буфер, даже если byte-порог достигнут, пока не
                // набрано время — меньше вероятность уйти в ребуферинг.
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    /** Шорткат: буфер под ТЕКУЩУЮ сеть (определяется автоматически). */
    fun defaultLoadControl(): DefaultLoadControl = defaultLoadControl(isOnWifi = isOnWifi())
}