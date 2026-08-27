// File: api/OkVideoRepository.kt
package re.pinok.api

import android.util.LruCache
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.SovaApp
import re.pinok.util.AppLog
import re.pinok.util.HevcSupport
import re.pinok.util.VkUserAgent
import java.util.concurrent.TimeUnit

/**
 * OK-IMPL-1 (Stage 3): Репозиторий метаданных OK.ru видео.
 *
 * **Назначение:** извлекает прямые URL'ы (mp4/HLS) для OK-видео, кросс-постнутых
 * в VK-ленту (iframe `https://ok.ru/videoembed/<movieId>`). Без этого репозитория
 * OK-видео воспроизводятся только через [re.pinok.ui.screens.videoplayer.OkWebViewPlayer]
 * (iframe + JS ad-blocking). С ним — нативным ExoPlayer, без рекламы by design
 * (Adman — JS-only SDK, ExoPlayer его не грузит).
 *
 * **Две стратегии извлечения метаданных** (см. OK_PLAYER_REVERSE.md §3142-3148):
 *
 * 1. **HTML parsing** (preferred): `GET https://ok.ru/videoembed/<movieId>`
 *    → парсим `data-options="..."` атрибут на `div.vid-card_cnt`, HTML-decode,
 *    JSON-parse. Внутри `flashvars.metadata` — все прямые URL'ы (videos[],
 *    hlsManifestUrl, metadataUrl/DASH, poster, title, duration, showAd).
 *    Работает без авторизации — OK embed-страница отдаётся anonym'но.
 *
 * 2. **mycdn.me API** (fallback): `POST https://api.mycdn.me/dk?cmd=videoPlayerMetadata`
 *    body `movieId=<id>`. Требует `TKN` header (OK anti-CSRF) для приватных видео,
 *    для публичных TKN не нужен. Если 403 — возвращаем null, caller падает в WebView.
 *
 * **Кеш:** in-memory [LruCache] (maxSize=20) с TTL 30 минут. URL'ы OK CDN
 * подписаны `sig` + `expires` + `srcIp` — после `expires` они 410 Gone,
 * нужно re-fetch'ить.
 *
 * **HEVC filter (Fix #341):** OK's `full` (1080p), `quad` (1440p), `ultra` (2160p)
 * — обычно HEVC. [filterHevcUnsupported] убирает их если устройство HEVC
 * не поддерживает (так же как VK's `mp4_1080/1440/2160`).
 *
 * **Потокобезопасность:** кеш — [LruCache] (внутренне synchronized), HTTP-клиент
 * — OkHttpClient (thread-safe). [fetchMetadata] можно вызывать из любого потока.
 *
 * См. OK_PLAYER_REVERSE.md §3150-3160 (metadata schema), §3168-3212 (quality enum).
 * См. OK_VIDEO_PLAN.md §Этап 3.
 */
object OkVideoRepository {

    private const val TAG = "OkVideoRepository"

    /** OK embed URL — публичный, без авторизации. */
    private const val EMBED_URL_TEMPLATE = "https://ok.ru/videoembed/%s"

    /** Fallback API для приватных / embed-недоступных видео. */
    private const val MYCDN_METADATA_URL = "https://api.mycdn.me/dk?cmd=videoPlayerMetadata"

    /** TTL кеша (30 мин). URL'ы OK CDN подписаны `expires` (обычно 1-6 часов). */
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    /** Максимальный размер кеша (20 видео). */
    private const val CACHE_MAX_SIZE = 20

    /**
     * Регулярка для извлечения `data-options` JSON из HTML.
     *
     * OK HTML экранирует `"` внутри атрибута как `&quot;`, поэтому литеральные `"`
     * встречаются только как delimiter'ы атрибута. `([^"]*)` захватывает всё
     * между ними (включая `&quot;` entities), затем [htmlUnescape] декодирует
     * entities в реальные `"`.
     *
     * В Kotlin triple-quoted string `\s` это два символа `\` и `s`, что для
     * Regex значит `\s` (whitespace). Внутренние `"` экранируем через `${'"'}`,
     * чтобы не сломать triple-quote.
     */
    private val DATA_OPTIONS_REGEX = Regex(
        """data-options\s*=\s*${'"'}([^${'"'}]*)${'"'}""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** HTTP-клиент (15s таймауты — embed-страница ~50KB, metadata ~10KB). */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Кеш с TTL. Value = (metadata, timestamp). */
    private data class CacheEntry(val metadata: OkVideoMetadata, val ts: Long)

    private val cache = LruCache<String, CacheEntry>(CACHE_MAX_SIZE)

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * OK-видео метаданные (одна запись).
     *
     * @param movieId OK movieId (например "16108201904696").
     * @param title заголовок (из metadata.movie.title).
     * @param duration длительность в секундах (из metadata.movie.duration).
     * @param posterUrl URL постера (из metadata.movie.poster).
     * @param hlsManifestUrl URL HLS .m3u8 (адаптивное, "Авто" в UI).
     * @param videos список progressive MP4 qualities (mobile/low/sd/hd/full/quad/ultra).
     * @param collageUrl URL sprite-sheet для timeline hover-preview (опционально).
     * @param showAd true если OK метаданные объявили рекламу. Логируется для диагностики.
     *               Не влияет на воспроизведение — ExoPlayer не грузит Adman JS.
     */
    data class OkVideoMetadata(
        val movieId: String,
        val title: String?,
        val duration: Long,
        val posterUrl: String?,
        val hlsManifestUrl: String?,
        val videos: List<OkQuality>,
        val collageUrl: String?,
        val showAd: Boolean?,
    )

    /**
     * Качество progressive MP4 из OK metadata.videos[].
     *
     * @param key OK quality key: `mobile`/`lowest`/`low`/`sd`/`hd`/`full`/`quad`/`ultra`.
     * @param label Человекочитаемая метка: "144p"/"240p"/.../"2160p".
     * @param url Прямой URL .mp4 (подписан sig+expires+srcIp, обычно 1-6 часов живёт).
     * @param width Ширина в пикселях (если указана в metadata).
     * @param height Высота в пикселях.
     */
    data class OkQuality(
        val key: String,
        val label: String,
        val url: String,
        val width: Int = 0,
        val height: Int = 0,
    )

    /**
     * Получить метаданные OK-видео по movieId.
     *
     * Сначала проверяет кеш (если не истёк TTL). Если кеша нет — Strategy 1
     * (HTML parsing). Если Strategy 1 упала — Strategy 2 (mycdn.me API).
     * Если обе упали — null, caller падает в [OkWebViewPlayer] fallback.
     *
     * @param movieId OK movieId (строка из [re.pinok.data.model.Video.externalId]).
     * @return метаданные или null если обе стратегии не сработали.
     */
    suspend fun fetchMetadata(movieId: String): OkVideoMetadata? {
        if (movieId.isBlank()) return null

        // HTTP-вызовы выполняем на IO-диспетчере (OkHttp.newCall().execute() — blocking).
        return withContext(Dispatchers.IO) {
            fetchMetadataInternal(movieId)
        }
    }

    private fun fetchMetadataInternal(movieId: String): OkVideoMetadata? {
        // 1) Cache check.
        // NULLSAFE-1: replaced cache.get(movieId)?.let { entry -> ... } with explicit null check
        val cached = cache.get(movieId)
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.ts
            if (age < CACHE_TTL_MS) {
                AppLog.d(TAG, "fetchMetadata($movieId) → cache hit (age=${age}ms)")
                return cached.metadata
            } else {
                cache.remove(movieId)
                AppLog.d(TAG, "fetchMetadata($movieId) → cache expired (age=${age}ms)")
            }
        }

        // 2) Strategy 1: HTML parsing (preferred — без TKN).
        val fromHtml = runCatching { fetchMetadataFromHtml(movieId) }
            .onFailure { AppLog.w(TAG, "Strategy 1 (HTML) failed for $movieId: ${it.message}") }
            .getOrNull()
        if (fromHtml != null) {
            cache.put(movieId, CacheEntry(fromHtml, System.currentTimeMillis()))
            AppLog.i(TAG, "fetchMetadata($movieId) → Strategy 1 OK: ${fromHtml.videos.size} qualities, hls=${fromHtml.hlsManifestUrl != null}, showAd=${fromHtml.showAd}")
            return fromHtml
        }

        // 3) Strategy 2: mycdn.me API fallback.
        val fromApi = runCatching { fetchMetadataFromApi(movieId) }
            .onFailure { AppLog.w(TAG, "Strategy 2 (mycdn) failed for $movieId: ${it.message}") }
            .getOrNull()
        if (fromApi != null) {
            cache.put(movieId, CacheEntry(fromApi, System.currentTimeMillis()))
            AppLog.i(TAG, "fetchMetadata($movieId) → Strategy 2 OK: ${fromApi.videos.size} qualities")
            return fromApi
        }

        AppLog.w(TAG, "fetchMetadata($movieId) → all strategies failed, returning null")
        return null
    }

    /**
     * Очищает кеш. Использовать при logout / low-memory / явном refresh.
     */
    fun clearCache() {
        val size = cache.size()
        cache.evictAll()
        AppLog.i(TAG, "Cache cleared ($size entries)")
    }

    // ── Strategy 1: HTML parsing ───────────────────────────────────────

    /**
     * Strategy 1: GET embed page, parse `data-options` JSON from HTML.
     *
     * OK отдаёт embed-страницу анонимно (isAnonym=1, isEmbed=1) — TKN не нужен.
     * `data-options` HTML-attribute содержит JSON с полным `flashvars.metadata`:
     * videos[], hlsManifestUrl, metadataUrl (DASH), movie.poster/title/duration,
     * showAd, admanMetadata, collageInfo.
     *
     * HTTP-заголовки:
     *  - `User-Agent`: VK Android App (OK CDN/VK API отбрасывает не-VK UA).
     *  - `Referer: https://m.vk.com/` — OK проверяет referer для embed-видео.
     *  - `Accept-Language: ru` — иначе OK отдаёт английскую страницу.
     */
    private fun fetchMetadataFromHtml(movieId: String): OkVideoMetadata? {
        val app = SovaApp.getOrNull() ?: run {
            AppLog.w(TAG, "SovaApp not initialized — cannot build UA")
            return null
        }
        val ua = VkUserAgent.get(app)
        val url = EMBED_URL_TEMPLATE.format(movieId)

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Referer", "https://m.vk.com/")
            .header("Accept-Language", "ru")
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

        val html = httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                AppLog.w(TAG, "HTML fetch HTTP ${resp.code} for $movieId")
                return null
            }
            val body = resp.body
            if (body != null) body.string() else return null
        }

        // Извлекаем data-options JSON. OK HTML-экранирует кавычки как &quot;
        // внутри data-options атрибута.
        val rawJson = extractDataOptionsJson(html) ?: run {
            AppLog.w(TAG, "data-options attribute not found in HTML for $movieId (len=${html.length})")
            return null
        }

        // HTML-decode (unescapes &quot;, &amp;, &#39; etc).
        val decoded = htmlUnescape(rawJson)

        // JSON-parse и навигация к flashvars.metadata.
        val root = try {
            JsonParser.parseString(decoded).asJsonObject
        } catch (e: Exception) {
            AppLog.w(TAG, "data-options JSON parse failed for $movieId: ${e.message}")
            return null
        }

        // data-options имеет структуру {flashvars: {metadata: "{...}" or {...}}, ...}.
        // metadata может быть строкой (JSON-encoded) или уже объектом.
        val flashvars = root.getAsJsonObject("flashvars") ?: run {
            AppLog.w(TAG, "flashvars object not found in data-options")
            return null
        }
        val metadata = parseMetadataField(flashvars.get("metadata")) ?: run {
            AppLog.w(TAG, "flashvars.metadata not found or invalid")
            return null
        }

        return parseMetadata(metadata, movieId)
    }

    /**
     * Извлекает значение `data-options="..."` атрибута из HTML.
     * OK экранирует внутренние `"` как `&quot;`, поэтому литеральные `"` в HTML
     * встречаются только как delimiter'ы атрибута — простая regex `([^"]*)`
     * корректно захватывает весь attribute value. Возвращает сырое (HTML-encoded)
     * значение; caller должен вызвать [htmlUnescape] для декодирования entities.
     */
    private fun extractDataOptionsJson(html: String): String? {
        val match = DATA_OPTIONS_REGEX.find(html)
        return if (match != null) match.groupValues[1] else null
    }

    /**
     * `flashvars.metadata` может быть:
     *  - строкой (JSON-encoded) — VK/OK часто пакует metadata как string.
     *  - объектом (inline JSON) — реже.
     */
    private fun parseMetadataField(el: JsonElement?): JsonObject? {
        if (el == null || el.isJsonNull) return null
        return when {
            el.isJsonObject -> el.asJsonObject
            el.isJsonPrimitive && el.asString.isNotBlank() -> try {
                JsonParser.parseString(el.asString).asJsonObject
            } catch (e: Exception) {
                AppLog.w(TAG, "metadata string JSON parse failed: ${e.message}")
                null
            }
            else -> null
        }
    }

    /**
     * Парсит metadata-объект в [OkVideoMetadata].
     *
     * Структура (OK_PLAYER_REVERSE.md §3150-3157):
     * ```
     * metadata: {
     *   movie: { movieId, title, duration, poster, ... },
     *   videos: [ { name: "mobile"|"lowest"|...|"ultra", url: "https://..." } ],
     *   hlsManifestUrl: "https://...",
     *   metadataUrl: "https://...",  // DASH MPD
     *   collageInfo: { url: "https://..." },
     *   showAd: true|false,
     *   admanMetadata: {...}
     * }
     * ```
     */
    private fun parseMetadata(metadata: JsonObject, movieId: String): OkVideoMetadata? {
        val movie = metadata.getAsJsonObject("movie") ?: JsonObject()

        // videos[] — progressive MP4.
        val videosArray = metadata.getAsJsonArray("videos")
        val videos: List<OkQuality> = if (videosArray != null) {
            videosArray.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val vo = el.asJsonObject
                val name = vo.getStringOrNull("name") ?: return@mapNotNull null
                val url = vo.getStringOrNull("url") ?: return@mapNotNull null
                if (url.isBlank()) return@mapNotNull null
                val label = qualityLabel(name) ?: return@mapNotNull null
                OkQuality(
                    key = name,
                    label = label,
                    url = url,
                    width = vo.getIntOrNull("width") ?: 0,
                    height = vo.getIntOrNull("height") ?: 0,
                )
            }.filter { it.url.isNotBlank() }
        } else emptyList()

        val hlsRaw = metadata.getStringOrNull("hlsManifestUrl")
        val hlsUrl = if (hlsRaw != null && hlsRaw.isNotBlank() && hlsRaw.contains("m3u8", ignoreCase = true)) hlsRaw else null

        val poster = movie.getStringOrNull("poster")
        val title = movie.getStringOrNull("title")
        val duration = movie.getLongOrNull("duration") ?: 0L

        val ci = metadata.getAsJsonObject("collageInfo")
        val collageUrl = if (ci != null) ci.getStringOrNull("url") else null

        val showAdEl = metadata.get("showAd")
        val showAd = if (showAdEl != null && !showAdEl.isJsonNull) {
            try { showAdEl.asBoolean } catch (_: Exception) {
                try { showAdEl.asString.lowercase() == "true" } catch (_: Exception) { null }
            }
        } else null

        if (videos.isEmpty() && hlsUrl.isNullOrBlank()) {
            AppLog.w(TAG, "metadata for $movieId has no videos[] and no hlsManifestUrl — unusable")
            return null
        }

        return OkVideoMetadata(
            movieId = movieId,
            title = title,
            duration = duration,
            posterUrl = poster,
            hlsManifestUrl = hlsUrl,
            videos = videos,
            collageUrl = collageUrl,
            showAd = showAd,
        )
    }

    // ── Strategy 2: mycdn.me API fallback ──────────────────────────────

    /**
     * Strategy 2: POST mycdn.me/dk?cmd=videoPlayerMetadata.
     *
     * Без TKN работает только для публичных видео. Если OK требует TKN —
     * сервер вернёт 403, метод вернёт null, caller падает в WebView.
     *
     * Response structure похожа на metadata (см. [parseMetadata]).
     */
    private fun fetchMetadataFromApi(movieId: String): OkVideoMetadata? {
        val app = SovaApp.getOrNull() ?: return null
        val ua = VkUserAgent.get(app)

        val body = FormBody.Builder()
            .add("movieId", movieId)
            .build()

        val req = Request.Builder()
            .url(MYCDN_METADATA_URL)
            .header("User-Agent", ua)
            .header("Referer", "https://ok.ru/videoembed/$movieId")
            .header("Accept", "application/json")
            .post(body)
            .build()

        val jsonStr = httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 403) {
                AppLog.d(TAG, "mycdn.me returned 403 for $movieId (TKN required, public-only flow)")
                return null
            }
            if (!resp.isSuccessful) {
                AppLog.w(TAG, "mycdn.me returned HTTP ${resp.code} for $movieId")
                return null
            }
            val body = resp.body
            if (body != null) body.string() else return null
        }

        val metadata = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            AppLog.w(TAG, "mycdn.me JSON parse failed for $movieId: ${e.message}")
            return null
        }

        // mycdn.me ответ может быть обёрнут в {flashvars: {metadata: {...}}} или
        // сразу возвращать metadata-объект. Поддерживаем оба варианта.
        val realMetadata = parseMetadataField(metadata.get("metadata")) ?: metadata
        return parseMetadata(realMetadata, movieId)
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * OK quality key → человекочитаемая метка. Источник: OK_PLAYER_REVERSE.md
     * §3170-3177 (quality enum), §3172 (mapping).
     *
     * OK не отдаёт явно разрешение в metadata.videos[] — только ключ `name`.
     * Соответствие захардкожено (совпадает с официальным OK web-плеером):
     *   mobile→144p, lowest→240p, low→360p, sd|medium→480p, hd|high→720p,
     *   fullhd|full→1080p, quadhd|quad→1440p, ultrahd|ultra→2160p.
     */
    fun qualityLabel(okKey: String): String? {
        val k = okKey.lowercase().trim()
        return when (k) {
            "mobile" -> "144p"
            "lowest" -> "240p"
            "low" -> "360p"
            "sd", "medium" -> "480p"
            "hd", "high" -> "720p"
            "full", "fullhd" -> "1080p"
            "quad", "quadhd" -> "1440p"
            "ultra", "ultrahd" -> "2160p"
            else -> null
        }
    }

    /**
     * OK quality key → VK-style mp4-ключ (для переиспользования существующего
     * UI в [re.pinok.ui.screens.videoplayer.VideoPlayerScreen]).
     *
     * Mapping: mobile→mp4_144, lowest→mp4_240, low→mp4_360, sd→mp4_480,
     * hd→mp4_720, full→mp4_1080, quad→mp4_1440, ultra→mp4_2160.
     * Это позволяет не дублировать UI-код качества — работает тот же
     * [re.pinok.ui.screens.videoplayer.QUALITY_ORDER] и [computeInitialQualityIndex].
     */
    fun okKeyToVkKey(okKey: String): String? {
        val k = okKey.lowercase().trim()
        return when (k) {
            "mobile" -> "mp4_144"
            "lowest" -> "mp4_240"
            "low" -> "mp4_360"
            "sd", "medium" -> "mp4_480"
            "hd", "high" -> "mp4_720"
            "full", "fullhd" -> "mp4_1080"
            "quad", "quadhd" -> "mp4_1440"
            "ultra", "ultrahd" -> "mp4_2160"
            else -> null
        }
    }

    /**
     * Fix #341: фильтрует HEVC-likely качества (full/quad/ultra = 1080p/1440p/2160p)
     * если устройство HEVC не поддерживает. Аналог [HevcSupport.filterKeys] для VK.
     *
     * OK почти всегда кодирует 1080p/1440p/2160p в HEVC (экономия трафика для
     * больших разрешений), 720p и ниже — в AVC. Это совпадает с поведением VK.
     *
     * Если HEVC поддерживается — возвращает список без изменений.
     */
    fun List<OkQuality>.filterHevcUnsupported(): List<OkQuality> {
        if (HevcSupport.isSupported()) return this
        val hevcKeys = setOf("full", "fullhd", "quad", "quadhd", "ultra", "ultrahd")
        val filtered = filter { it.key.lowercase() !in hevcKeys }
        if (filtered.size != size) {
            val removed = map { it.key } - filtered.map { it.key }.toSet()
            AppLog.i(TAG, "Filtered HEVC OK qualities (device has no HEVC): removed=$removed, kept=${filtered.map { it.key }}")
        }
        return filtered
    }

    /**
     * HTML-unescape для `data-options` атрибута. OK экранирует кавычки и &.
     * Минимальный набор сущностей, которых достаточно для парсинга JSON.
     */
    private fun htmlUnescape(s: String): String {
        return s
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&") // Всегда последним, чтобы не разрушить &amp;quot; → &quot;
    }

    // NULLSAFE-1: helper'ы заменяют многословные цепочки `get("x")?.takeIf { !it.isJsonNull }?.asString`
    // на явный null-check. Если поле отсутствует или JSON-null — возвращается null.
    private fun JsonObject.getStringOrNull(key: String): String? {
        val el = get(key)
        return if (el != null && !el.isJsonNull) el.asString else null
    }

    private fun JsonObject.getIntOrNull(key: String): Int? {
        val el = get(key)
        return if (el != null && !el.isJsonNull) el.asInt else null
    }

    private fun JsonObject.getLongOrNull(key: String): Long? {
        val el = get(key)
        return if (el != null && !el.isJsonNull) el.asLong else null
    }
}
