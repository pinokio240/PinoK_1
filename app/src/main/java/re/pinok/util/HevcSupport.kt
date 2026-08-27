package re.pinok.util

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.annotation.VisibleForTesting
import re.pinok.SovaApp

/**
 * Fix #341: Session-level HEVC (H.265) detection.
 *
 * Проблема: VK отдаёт mp4_2160 / mp4_1440 / mp4_1080 почти всегда в HEVC.
 * На устройствах без HEVC-декодера (MediaTek MT67xx, старые Snapdragon)
 * ExoPlayer падает с `ERROR_CODE_DECODING_FAILED` → Fix #338 fallback
 * переключает на AVC/HLS. Это работает, но:
 *  - каждый раз при открытии длинного видео — 1-2 сек «чёрный экран» до fallback;
 *  - user видит «Кодек не поддерживается. Пробую другой формат…».
 *
 * Решение: заранее проверить поддержку HEVC через [MediaCodecList] и
 * отфильтровать HEVC-качества в [re.pinok.ui.screens.videoplayer.VideoPlayerScreen]
 * ДО создания ExoPlayer. Fallback #338 остаётся как страховка (на случай если
 * VK поменяет кодек для конкретного качества).
 *
 * Результат кешируется в [cached] — `MediaCodecList` читается один раз за
 * сессию (O(n) по кодекам устройства, ~10-20 шт, <1мс на modern device).
 *
 * Эвристика «какие ключи VK — HEVC»: [HEVC_LIKELY_KEYS]. VK не помечает явно
 * кодек в `files`, но исторически 2160p/1440p/1080p — HEVC (экономия трафика
 * для больших разрешений), 720p и ниже — AVC. Это совпадает с поведением
 * официального VK app.
 */
object HevcSupport {

    private const val TAG = "HevcSupport"

    /**
     * VK mp4-ключи, которые почти всегда кодированы в HEVC.
     * Используется [VideoPlayerScreen] для фильтрации на устройствах без HEVC.
     *
     * 720p и ниже НЕ включены — VK обычно отдаёт их в AVC (H.264), который
     * поддерживается на всех Android-устройствах (baseline с API 1).
     */
    val HEVC_LIKELY_KEYS: Set<String> = setOf("mp4_2160", "mp4_1440", "mp4_1080")

    @Volatile
    private var cached: Boolean? = null

    /**
     * true если устройство имеет hardware ИЛИ software HEVC-декодер.
     *
     * Проверяет [MediaCodecList.REGULAR_CODECS] (только декларированные в
     * system, без vendor temporary/passthrough). На API 31+ можно дополнительно
     * проверить `isSoftwareOnly` / `isHardwareAccelerated`, но для gate'инга
     * достаточно наличия любого декодера — software HEVC тоже воспроизводится
     * (медленнее, но не падает).
     *
     * Thread-safe. Кеширует результат в [cached].
     */
    fun isSupported(): Boolean {
        cached?.let { return it }
        val result = try {
            checkHevcSupport()
        } catch (e: Exception) {
            AppLog.w(TAG, "HEVC check failed, assuming supported: ${e.message}")
            // Fail-open: если проверка упала, НЕ отфильтровываем — пусть #338
            // fallback сработает. Хуже отфильтровать рабочее HEVC, чем дать
            // DECODING_FAILED на нерабочем.
            true
        }
        cached = result
        AppLog.i(TAG, "HEVC support: $result (cached for session)")
        return result
    }

    /** Convenience overload — берёт context из [SovaApp.getOrNull]. */
    fun isSupported(context: Context): Boolean = isSupported()

    /**
     * Сбрасывает кеш. Только для testing — в production HEVC-поддержка устройства
     * не меняется за сессию.
     */
    @VisibleForTesting
    fun resetCache() {
        cached = null
    }

    private fun checkHevcSupport(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            // Только декодеры (не энкодеры).
            if (info.isEncoder) continue
            if (isHevcDecoder(info)) {
                AppLog.d(TAG, "Found HEVC decoder: ${info.name} (hw=${!info.isSoftwareOnly})")
                return true
            }
        }
        return false
    }

    private fun isHevcDecoder(info: MediaCodecInfo): Boolean {
        // getCapabilitiesForType(video/hevc) работает на всех API уровнях (с 21)
        // и бросает IllegalArgumentException если MIME не поддерживается кодеком.
        // Это достаточная проверка — не нужен getSupportedMimeTypes() (который
        // местами unresolved на некоторых compileSdk).
        return try {
            info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            true
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: Exception) {
            // На некоторых OEM-прошивках getCapabilitiesForType может бросить
            // RuntimeException вместо IllegalArgumentException — трактуем как "не HEVC".
            false
        }
    }

    /**
     * Фильтрует список quality-ключей, убирая HEVC-likely если устройство
     * HEVC не поддерживает. Возвращает исходный список если HEVC поддерживается.
     *
     * Используется [VideoPlayerScreen.qualityOptions] для построения списка
     * доступных качеств ДО создания ExoPlayer.
     *
     * @param keys исходные mp4-ключи (например ["mp4_2160","mp4_1080","mp4_720"])
     * @return отфильтрованный список без HEVC если не поддерживается
     */
    fun filterKeys(keys: List<String>): List<String> {
        if (isSupported()) return keys
        val filtered = keys.filter { it !in HEVC_LIKELY_KEYS }
        if (filtered.size != keys.size) {
            val removed = keys.filter { it in HEVC_LIKELY_KEYS }
            AppLog.i(TAG, "Filtered HEVC qualities (device has no HEVC): removed=$removed, kept=$filtered")
        }
        return filtered
    }
}
