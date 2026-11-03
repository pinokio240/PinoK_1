package re.pinok.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI

/**
 * Утилиты для скачивания HLS/DASH контента в MP4/TS.
 * Порт техник из VK-Video-Downloader-4.js (эталон: userScript v4.1).
 * Используется VideoDownloadManager / StoryVideoDownloadManager / ClipVideoDownloadManager.
 *
 * Чистый Kotlin + OkHttp: никакого android-специфичного кода.
 * OkHttpClient передаётся параметром — менеджеры отдают свой инстанс (с VK User-Agent).
 */
object HlsDownloadSupport {

    /** Один сегмент media-плейлиста. [offset]=-1 — «продолжить после предыдущего» (BYTERANGE без @offset). */
    data class Segment(val url: String, val offset: Long, val length: Long)

    /** Вариант качества из мастер-плейлиста (EXT-X-STREAM-INF). */
    data class Variant(val url: String, val width: Int, val height: Int, val bandwidth: Long)

    /** Результат разбора m3u8: либо мастер-плейлист ([variants]), либо media-плейлист ([segments]). */
    data class HlsPlaylist(
        val variants: List<Variant>,      // мастер-плейлист (EXT-X-STREAM-INF)
        val segments: List<Segment>,      // media-плейлист
        val initSegment: Segment?,        // EXT-X-MAP (fMP4 init)
        val isSingleFile: Boolean,        // все сегменты — куски одного URL (fMP4 одним файлом)
    )

    private val RESOLUTION_RE = Regex("RESOLUTION=(\\d+)x(\\d+)")
    private val BANDWIDTH_RE = Regex("BANDWIDTH=(\\d+)")
    private val MAP_URI_RE = Regex("URI=\"([^\"]+)\"")
    private val MAP_BYTERANGE_RE = Regex("BYTERANGE=\"(\\d+)@(\\d+)\"")
    private val BYTERANGE_RE = Regex("^#EXT-X-BYTERANGE:(\\d+)(?:@(\\d+))?")
    private val ABS_URL_RE = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val BYTES_PARAM_RE = Regex("([?&])bytes=")
    private val NEWLINE_RE = Regex("\r?\n")

    /**
     * Парсинг m3u8: мастер → variants; media → segments (+BYTERANGE, +EXT-X-MAP).
     *
     * Порт VK-Video-Downloader-4.js:L554-598 (parseM3U8):
     * - `#EXT-X-STREAM-INF:` → RESOLUTION=WxH + BANDWIDTH, следующая не-# строка = URL варианта;
     * - `#EXT-X-MAP:URI="..."` (+опц. BYTERANGE="len@off") → initSegment;
     * - `#EXT-X-BYTERANGE:len[@off]` → pending для следующего сегмента (off=-1 — продолжить
     *   после предыдущего);
     * - склейка последовательных BYTERANGE без @offset через prevEnd-трекинг (L591-596);
     * - относительные URL резолвятся через [baseUrl] (аналог JS `new URL(u, base)`).
     *
     * @return null только для пустого текста; иначе плейлист (возможно с пустыми списками).
     */
    fun parseM3U8(text: String, baseUrl: String): HlsPlaylist? {
        if (text.isBlank()) return null
        val variants = ArrayList<Variant>()
        val segments = ArrayList<Segment>()
        var initSegment: Segment? = null

        // pending BYTERANGE (порт L557, L574-578): length, offset; offset=-1 → после предыдущего
        var pendingLen = 0L
        var pendingOff = -1L
        var hasPendingRange = false
        // pending вариант из #EXT-X-STREAM-INF (порт L558, L562-566); ширина -1 = «нет pending»
        var pendingW = -1
        var pendingH = 0
        var pendingBw = 0L

        for (raw in text.split(NEWLINE_RE)) {
            val ln = raw.trim()
            if (ln.isEmpty()) continue

            if (ln.startsWith("#EXT-X-STREAM-INF:")) {
                val res = RESOLUTION_RE.find(ln)
                val bw = BANDWIDTH_RE.find(ln)
                pendingW = res?.groupValues?.get(1)?.toIntOrNull() ?: 0
                pendingH = res?.groupValues?.get(2)?.toIntOrNull() ?: 0
                pendingBw = bw?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                continue
            }
            if (ln.startsWith("#EXT-X-MAP:")) {
                val uri = MAP_URI_RE.find(ln)?.groupValues?.get(1)
                if (uri != null) {
                    val br = MAP_BYTERANGE_RE.find(ln)
                    initSegment = Segment(
                        url = resolveUrl(uri, baseUrl) ?: uri,
                        offset = br?.groupValues?.get(2)?.toLongOrNull() ?: 0L,
                        length = br?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                    )
                }
                continue
            }
            if (ln.startsWith("#EXT-X-BYTERANGE:")) {
                val m = BYTERANGE_RE.find(ln)
                if (m != null) {
                    pendingLen = m.groupValues[1].toLongOrNull() ?: 0L
                    pendingOff = m.groupValues.getOrNull(2)?.toLongOrNull() ?: -1L
                    hasPendingRange = true
                } else {
                    hasPendingRange = false
                }
                continue
            }
            if (ln.startsWith("#")) continue

            val u = resolveUrl(ln, baseUrl) ?: continue
            if (pendingW >= 0) {
                // следующий за STREAM-INF не-# строкой идёт URL варианта (порт L582)
                variants.add(Variant(url = u, width = pendingW, height = pendingH, bandwidth = pendingBw))
                pendingW = -1
                continue
            }
            if (hasPendingRange) {
                // offset=-1 — временная метка «продолжить после предыдущего», резолвится ниже (порт L583-589)
                segments.add(Segment(url = u, offset = pendingOff, length = pendingLen))
                hasPendingRange = false
            } else {
                segments.add(Segment(url = u, offset = 0L, length = 0L))
            }
        }

        // Склейка последовательных BYTERANGE без @offset (порт VK-Video-Downloader-4.js:L591-596)
        var prevEnd = 0L
        for (i in segments.indices) {
            val sg = segments[i]
            if (sg.length > 0L && sg.offset < 0L) segments[i] = sg.copy(offset = prevEnd)
            if (segments[i].length > 0L) prevEnd = segments[i].offset + segments[i].length
        }

        val isSingleFile = initSegment != null &&
            segments.isNotEmpty() &&
            segments.all { it.url == segments[0].url }
        return HlsPlaylist(variants = variants, segments = segments, initSegment = initSegment, isSingleFile = isSingleFile)
    }

    /**
     * VK CDN без Range отдаёт HTML-заглушку — добавляем `bytes=0-999999999`
     * (порт VK-Video-Downloader-4.js:L185-188, withBytes; так же запрашивает сам плеер VK).
     * Если параметр `bytes=` уже есть — URL возвращается как есть.
     */
    fun withBytes(url: String): String {
        if (url.isEmpty() || BYTES_PARAM_RE.containsMatchIn(url)) return url
        return url + (if (url.contains('?')) "&" else "?") + "bytes=0-999999999"
    }

    /**
     * Точный размер файла: GET с `Range: bytes=0-0` → `Content-Range: bytes 0-0/TOTAL`.
     * Порт VK-Video-Downloader-4.js:L329-334 (probeSize) + L287-288 (wantTotal).
     * Fallback — Content-Length (если сервер не отдал Content-Range).
     *
     * @return TOTAL в байтах; 0 при любой ошибке сети/HTTP (кроме 200/206).
     */
    suspend fun probeSize(url: String, client: OkHttpClient): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(withBytes(url))
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code != 200 && resp.code != 206) return@use 0L
                parseRangeTotal(resp)
            }
        } catch (e: Exception) {
            0L
        }
    }

    /** `Content-Range: bytes 0-0/TOTAL` → TOTAL; fallback Content-Length (порт VK-Video-Downloader-4.js:L287-288). */
    private fun parseRangeTotal(resp: Response): Long {
        val cr = resp.header("Content-Range")
        val fromCr = cr?.substringAfterLast('/')?.trim()?.toLongOrNull()
        if (fromCr != null && fromCr > 0L) return fromCr
        return resp.header("Content-Length")?.toLongOrNull() ?: 0L
    }

    /**
     * Детект контейнера по первым байтам ответа.
     * Порт VK-Video-Downloader-4.js:L931-935:
     * - head[0] == 0x47 (sync byte MPEG-TS) → "ts";
     * - head[4..7] == "ftyp" → "mp4";
     * - head[4..7] == "styp" (segmented fMP4) → "ts";
     * - иначе "unknown".
     */
    fun detectContainer(head: ByteArray): String {
        if (head.isEmpty()) return "unknown"
        if (head[0] == 0x47.toByte()) return "ts"
        if (head.size >= 8) {
            when (String(head, 4, 4, Charsets.ISO_8859_1)) {
                "ftyp" -> return "mp4"
                "styp" -> return "ts"
            }
        }
        return "unknown"
    }

    /**
     * Есть ли звук в init-сегменте fMP4: ищем атомы soun|mp4a в первых 4096 байтах
     * (порт VK-Video-Downloader-4.js:L968-972, findAtom — аналог String.fromCharCode).
     * Используется для метки «единый MP4 (без звука — так отдаёт VK)» (L887-888).
     */
    fun hasAudioAtom(initBytes: ByteArray): Boolean {
        if (initBytes.isEmpty()) return false
        val n = minOf(4096, initBytes.size)
        val head = String(initBytes, 0, n, Charsets.ISO_8859_1)
        return head.contains("soun") || head.contains("mp4a")
    }

    /**
     * Выбор варианта из мастер-плейлиста: максимальная высота, при равенстве — максимальный bandwidth.
     * Ранжирование height*1e6+bandwidth (порт VK-Video-Downloader-4.js:L608-612 — rank по q/bw).
     */
    fun pickBestVariant(playlist: HlsPlaylist): Variant? =
        playlist.variants.maxByOrNull { it.height * 1_000_000L + it.bandwidth }

    /**
     * Резолв относительного URL через базу (аналог JS `new URL(u, base)`, порт resolveUrl L190-197).
     * Абсолютные http(s) URL возвращаются как есть; при ошибке разбора — null (строка пропускается).
     */
    private fun resolveUrl(url: String, baseUrl: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        if (ABS_URL_RE.containsMatchIn(trimmed)) return trimmed
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (e: Exception) {
            null
        }
    }
}
