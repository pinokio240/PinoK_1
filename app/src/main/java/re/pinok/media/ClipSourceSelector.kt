// File: media/ClipSourceSelector.kt
package re.pinok.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import androidx.media3.common.MimeTypes
import re.pinok.util.AppLog

/**
 * Выбор источника клипа по правилам VK web (DASH-first) + проверка AV1-декодера.
 *
 * В отличие от [re.pinok.data.model.Video.bestPlayUrl] (mp4-first), web-плеер
 * клипов VK использует DASH-приоритет: dash_webm_av1 → dash_webm → dash_sep →
 * dash_ondemand → hls_fmp4 → hls → hls_ondemand → mp4_*.
 *
 * Результат выбора — `(key, url, mime)`; MIME по ключу (dash→MPD, hls→M3U8,
 * mp4→пустая строка), чтобы ExoPlayer корректно определил контейнер, а не пытался
 * играть DASH-манифест как прогрессивный mp4 (HTTP 400).
 *
 * NULL-политика: без `?.`/`?:`/`!!`, только явные guard и локальные val.
 */
object ClipSourceSelector {

    private const val TAG = "ClipSourceSelector"

/**
     * Приоритет форматов для клипов (по web-плееру VK, DASH-first).
     *
     * Разбор HAR (клипы.web): web играет клипы ТОЛЬКО через DASH — запросов к
     * `*.m3u8` в сессии листания клипов 0. DASH-сегменты (dash_sep/dash_webm_av1)
     * качаются с okcdn/vkuser через `&bytes=start-end` в query (не HTTP Range),
     * статус 200. HLS_fmp4/HLS в HAR web-сессии не используются.
     *
     * Поэтому порядок:
     *   dash_webm_av1 → dash_webm → dash_sep → dash_ondemand →
     *   hls_fmp4 → hls → hls_ondemand → mp4_1080…144.
     * (AV1 при отсутствии декодера пропускается в pick().)
     */
    val WEB_PRIORITY: List<String> = listOf(
        "dash_webm_av1",
        "dash_webm",
        "dash_sep",
        "dash_ondemand",
        "hls_fmp4",
        "hls",
        "hls_ondemand",
        "mp4_1080",
        "mp4_720",
        "mp4_480",
        "mp4_360",
        "mp4_240",
        "mp4_144",
    )

    private const val AV1_MIME: String = "video/av01"

    @Volatile
    private var av1Cached: Boolean? = null

    /**
     * true если устройство имеет AV1-декодер (video/av01).
     *
     * Кешируется на сессию — декодеры не меняются в runtime. Fail-open: если
     * проверка падает, считаем AV1 недоступным (безопаснее — dash_webm_av1 будет
     * пропущен, а не провален с ERROR_CODE_DECODING_FAILED).
     */
    fun hasAv1Decoder(context: Context): Boolean {
        val cached = av1Cached
        if (cached != null) return cached
        val result = try {
            checkAv1Decoder()
        } catch (e: Exception) {
            AppLog.w(TAG, "AV1 check failed, assuming unsupported: ${e.message}")
            false
        }
        av1Cached = result
        AppLog.i(TAG, "AV1 decoder support: $result (cached for session)")
        return result
    }

    private fun checkAv1Decoder(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val infos = codecList.codecInfos
        for (info in infos) {
            if (info.isEncoder) continue
            if (isAv1Decoder(info)) {
                AppLog.d(TAG, "Found AV1 decoder: ${info.name} (hw=${!info.isSoftwareOnly})")
                return true
            }
        }
        return false
    }

    private fun isAv1Decoder(info: MediaCodecInfo): Boolean {
        return try {
            info.getCapabilitiesForType(AV1_MIME)
            true
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Выбирает лучший доступный источник клипа по web-приоритету.
     *
     * @param files карта files[] клипа (key → url)
     * @param av1Supported подерживает ли устройство AV1 (передаётся наружу,
     *   чтобы дешево кэшировать решение в UI)
     * @return `Triple(key, url, mime)` по правилам [pickMime], или null если
     *   нет ни одного подходящего url.
     */
    fun pick(files: Map<String, String>?, av1Supported: Boolean): Triple<String, String, String>? {
        if (files == null) return null
        if (files.isEmpty()) return null
        val priority = WEB_PRIORITY
        for (key in priority) {
            val url = files[key]
            if (url == null) continue
            if (url.isBlank()) continue
            if (key == "dash_webm_av1" && !av1Supported) {
                AppLog.d(TAG, "pick: skip dash_webm_av1 (no AV1 decoder)")
                continue
            }
            // #CLIP-SCL-FIX (2026-10-09): URL с 'scl=' в query — это превью (низкое качество),
            // а не полноценный поток. Web-плеер их не использует для воспроизведения;
            // такой dash_webm_av1/dash_sep даёт чёрный экран/зависание. Пропускаем.
            if (url.contains("scl=")) {
                AppLog.d(TAG, "pick: skip '$key' — url is preview (scl=): ${url.take(100)}")
                continue
            }
            val mime = pickMime(key)
            if (mime == null) {
                return Triple(key, url, "")
            }
            return Triple(key, url, mime)
        }
        AppLog.w(TAG, "pick: подходящий источник не найден (files keys=${files.keys})")
        return null
    }

    /**
     * MIME по ключу files (общий для переиспользования).
     * dash* → APPLICATION_MPD, hls* → APPLICATION_M3U8, mp4_* → null (прогрессивный).
     */
    fun pickMime(key: String?): String? {
        if (key == null) return null
        return when {
            key.startsWith("dash") -> MimeTypes.APPLICATION_MPD
            key.startsWith("hls") -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }

    /** mimeForKey — алиас [pickMime] для сигнатуры из задания. */
    fun mimeForKey(key: String?): String? = pickMime(key)

    /**
     * Заменяет hostname URL на failover_host (при недоступности основного CDN).
     * Если failover_host пуст/невалиден или URL не имеет host — возвращает исходный URL.
     */
    fun withFailoverHost(url: String?, failoverHost: String?): String? {
        if (url == null) return null
        if (url.isBlank()) return url
        if (failoverHost == null) return url
        val fh = failoverHost
        if (fh.isBlank()) return url
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host
            if (host == null) return url
            val rebuilt = uri.buildUpon().authority(fh).build().toString()
            if (rebuilt.isBlank()) url else rebuilt
        } catch (e: Exception) {
            AppLog.w(TAG, "withFailoverHost failed, keep original: ${e.message}")
            url
        }
    }
}