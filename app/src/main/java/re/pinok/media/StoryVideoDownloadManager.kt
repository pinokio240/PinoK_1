// File: media/StoryVideoDownloadManager.kt
package re.pinok.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.SovaApp
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Story
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * StoryVideoDownloadManager — офлайн-кэш для ВИДЕО-историй (stories).
 *
 * Отдельный класс от [VideoDownloadManager], потому что:
 *  1. [re.pinok.data.model.Video] имеет `Long videoId`, [Story] имеет `Int storyId` —
 *     разные ID-пространства.
 *  2. Stories имеют TTL 24h (нужен eviction), catalog videos — нет.
 *  3. CDN URL stories истекают (~часы) — нужен URL-refresh on 403.
 *  4. Чистое разделение = меньше риск поломать существующий video download flow.
 *
 * ## Хранение
 * - Директория: `filesDir/story_video_downloads/`
 * - Ключ: `"s_${ownerId}_${storyId}"` (prefix `s_` = story, чтобы избежать коллизии
 *   с catalog videos `"${ownerId}_${videoId}"` в `video_downloads/`).
 * - Файлы: `${key}.mp4` (видео) / `${key}.ts` (HLS→MPEG-TS, DL-C) + `${key}.meta` (JSON sidecar [StoryVideoMeta]).
 *
 * ## TTL eviction
 * Stories живут 24h на стороне VK. При [refreshFromDisk] и периодически через
 * [evictExpired] удаляем файлы, где `expiresAt < now`.
 *
 * ## URL-refresh on 403
 * CDN URL stories короткоживущие. При 403 во время загрузки вызываем
 * [refreshStoryUrl] — ре-феч `storiesGet()` для owner и поиск story по id.
 *
 * ## HLS/DASH-источники (DL-C, Wave 2)
 * [pickBestSource] возвращает (url, kind): mp4_* качается напрямую (с `bytes=`
 * параметром — [HlsDownloadSupport.withBytes]); hls/hls_fmp4 — манифест через
 * [downloadHls] (single-file fMP4 → один Range-запрос; иначе последовательная
 * сборка сегментов, 3 retry/сегмент; container-detect → .mp4/.ts); dash* —
 * явный FAILED с [DASH_FAIL_REASON] (не тихий skip). Прогресс HLS — по
 * сегментам (done/total).
 *
 * ## Auto-cache-on-play
 * [enqueueDownload] с `silent=true` — mirror паттерна `PlayerConnection`
 * (auto-cache audio). В [StoryViewerScreen] вызывается на `STATE_READY`.
 *
 * См. `STORY_VIDEO_CACHE_PLAN.md` (Fix #100), `FEED_RESEARCH.md` ЧАСТЬ B.
 */
object StoryVideoDownloadManager {

    private const val TAG = "StoryVideoDownloadMgr"
    private const val DOWNLOAD_DIR = "story_video_downloads"
    private const val STORY_TTL_MS = 24L * 60 * 60 * 1000  // 24h

    // DL-C (Wave 2): kind-таги источника из [pickBestSource] + причина FAIL для DASH-only.
    private const val KIND_MP4 = "mp4"
    private const val KIND_HLS = "hls"
    private const val KIND_DASH = "dash"
    private const val DASH_FAIL_REASON = "DASH-поток недоступен для скачивания в этой версии"

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context
    private lateinit var downloadDir: File
    private lateinit var httpClient: OkHttpClient
    private val activeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    /**
     * Строковый ключ story-видео: `"s_${ownerId}_${storyId}"`.
     * Prefix `s_` (story) гарантирует отсутствие коллизии с catalog videos
     * (ключ `"${ownerId}_${videoId}"` в [VideoDownloadManager]).
     */
    fun storyKey(ownerId: Long, storyId: Int): String = "s_${ownerId}_${storyId}"

    /** Сменить директорию скачивания (переносит файлы). Mirror [VideoDownloadManager.reconfigurePath]. */
    fun reconfigurePath(newPath: String) {
        if (!initialized) return
        val newDir = if (newPath.isNotBlank()) File(newPath) else File(appContext.filesDir, DOWNLOAD_DIR)
        newDir.mkdirs()
        // Fix #123: probe-write + try/catch на перенос (Scoped Storage EPERM guard).
        if (!newDir.exists() || !newDir.canWrite() || !probeWritable(newDir)) {
            AppLog.e(TAG, "reconfigurePath: target dir not writable: ${newDir.absolutePath} — falling back to internal")
            val fallback = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
            if (fallback.canWrite()) downloadDir = fallback
            refreshFromDisk()
            return
        }
        if (newDir.absolutePath == downloadDir.absolutePath) return
        val previousDir = downloadDir
        try {
            downloadDir.listFiles()?.forEach { file ->
                file.copyTo(File(newDir, file.name), overwrite = true)
                file.delete()
            }
            downloadDir = newDir
            AppLog.i(TAG, "reconfigurePath: dir=${downloadDir.absolutePath}")
        } catch (e: Exception) {
            AppLog.e(TAG, "reconfigurePath: copy failed (${e.javaClass.simpleName}: ${e.message}) — keeping previous dir=${previousDir.absolutePath}", e)
            downloadDir = previousDir
            runCatching {
                downloadDir.listFiles()?.forEach { src ->
                    val dst = File(newDir, src.name)
                    if (dst.exists() && src.exists()) dst.delete()
                }
            }
        }
        refreshFromDisk()
    }

    /** Fix #123: probe-write для надёжной проверки Scoped Storage. */
    private fun probeWritable(dir: File): Boolean {
        val probe = File(dir, ".pinok_probe_${System.currentTimeMillis()}")
        return try {
            java.io.FileOutputStream(probe).use { it.write(0) }
            probe.delete()
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "probeWritable: write test FAILED for ${dir.absolutePath}: ${e.javaClass.simpleName}: ${e.message}")
            probe.delete()
            false
        }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext
            downloadDir = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }

            val ua = VkUserAgent.get(context.applicationContext as android.app.Application)
            httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", ua)
                        .header("Accept-Language", "ru")
                        .build()
                    chain.proceed(req)
                }
                .build()

            initialized = true
            AppLog.i(TAG, "Initialized. dir=${downloadDir.absolutePath}")

            refreshFromDisk()
            // Fix #100: при старте удаляем истёкшие stories (TTL 24h).
            evictExpired()
        }
    }

    /**
     * Поставить story video в очередь загрузки.
     *
     * @param story      объект Story (должен иметь `video != null` с `files`).
     * @param ownerName  имя автора (для отображения в OfflineManager + sidecar).
     * @param ownerPhoto100 аватар автора (для UI).
     * @param silent     `true` = auto-cache (skip foreground notification).
     *                   Mirror `TrackDownloadManager.enqueueDownload(track, silent=true)`.
     */
    fun enqueueDownload(
        story: Story,
        ownerName: String,
        ownerPhoto100: String? = null,
        silent: Boolean = false,
    ) {
        ensureInitialized()

        val storyVideo = story.video
        if (storyVideo == null) {
            AppLog.w(TAG, "enqueueDownload: story #${story.id} has no video")
            return
        }

        // DL-C (Wave 2): kind-aware выбор источника — mp4 напрямую, hls через
        // [downloadHls], dash → явный FAIL (см. ниже). Раньше .m3u8 из `hls`
        // скачивался как файл — пользователь получал текст вместо видео.
        val src = pickBestSource(storyVideo.files)
        if (src == null) {
            AppLog.w(TAG, "enqueueDownload: story #${story.id} has no downloadable URL (files=${storyVideo.files?.keys})")
            return
        }
        val (url, kind) = src

        val key = storyKey(story.ownerId, story.id)
        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueDownload: story #${story.id} already ${existing.status}")
                return
            }

            // DL-C: DASH-манифест нельзя скачать как файл — явный FAIL, не тихий skip.
            if (kind == KIND_DASH) {
                AppLog.w(TAG, "enqueueDownload: story #${story.id} DASH-only — FAILED ($DASH_FAIL_REASON)")
                updateState(key, DownloadState(
                    trackId = story.id.toLong(),
                    status = DownloadStatus.FAILED,
                    progress = 0,
                    reason = DASH_FAIL_REASON,
                    title = ownerName.ifBlank { "История ${story.ownerId}" },
                    artist = ownerName,
                    ownerId = story.ownerId,
                ))
                return
            }

            AppLog.i(TAG, "enqueueDownload: story #${story.id} (silent=$silent, kind=$kind, $url)")
            val title = ownerName.ifBlank { "История ${story.ownerId}" }
            updateState(key, DownloadState(
                trackId = story.id.toLong(),
                status = DownloadStatus.QUEUED,
                progress = 0,
                title = title,
                artist = ownerName,
                ownerId = story.ownerId,
            ))
            saveMetadata(story, ownerName, ownerPhoto100)
        }
        if (!silent) startForegroundService()

        val job = scope.launch {
            try {
                downloadFile(key, story, ownerName, ownerPhoto100, url, kind)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e(TAG, "downloadFile failed for key=$key: ${t.message}", t)
                if (_downloads.value[key]?.isInProgress == true) {
                    updateState(key, DownloadState(
                        trackId = story.id.toLong(),
                        status = DownloadStatus.FAILED,
                        progress = 0,
                        reason = t.message,
                        title = ownerName,
                        artist = ownerName,
                        ownerId = story.ownerId,
                    ))
                }
            } finally {
                activeJobs.remove(key)
                maybeStopForegroundService()
            }
        }
        activeJobs[key] = job
    }

    fun removeDownload(ownerId: Long, storyId: Int) {
        ensureInitialized()
        val key = storyKey(ownerId, storyId)
        AppLog.i(TAG, "removeDownload: owner=$ownerId storyId=$storyId (key=$key)")

        activeJobs[key]?.cancel()
        activeJobs.remove(key)

        File(downloadDir, "$key.mp4").delete()
        File(downloadDir, "$key.mp4.tmp").delete()
        // DL-C: HLS-результат может быть .ts (+ temp сборки "$key.hls.tmp").
        File(downloadDir, "$key.ts").delete()
        File(downloadDir, "$key.ts.tmp").delete()
        File(downloadDir, "$key.hls.tmp").delete()
        deleteMetadata(key)

        removeState(key)
        maybeStopForegroundService()
    }

    fun getDownloadState(ownerId: Long, storyId: Int): DownloadState? {
        val key = storyKey(ownerId, storyId)
        return _downloads.value[key]
    }

    fun isDownloaded(ownerId: Long, storyId: Int): Boolean {
        ensureInitialized()
        val key = storyKey(ownerId, storyId)
        return _downloads.value[key]?.isCompleted == true
    }

    fun getLocalFile(ownerId: Long, storyId: Int): File? {
        ensureInitialized()
        // DL-C: .mp4 (прогрессивные) или .ts (HLS→MPEG-TS результат).
        return localCacheFile(storyKey(ownerId, storyId))
    }

    /**
     * Fix #100: public accessor к .meta sidecar — нужен OfflineManagerScreen
     * для отображения ownerName, ownerPhoto100, thumbUrl, expiresAt, duration.
     * Возвращает null если файл отсутствует/повреждён.
     */
    fun getStoryMeta(key: String): StoryVideoMeta? = loadMetadata(key)

    /** Перегрузка для удобства: по (ownerId, storyId). */
    fun getStoryMeta(ownerId: Long, storyId: Int): StoryVideoMeta? =
        loadMetadata(storyKey(ownerId, storyId))

    /**
     * Удалить все stories с истёкшим TTL (24h с момента создания story).
     * Вызывается в [init] и периодически (например, при открытии OfflineManager).
     */
    fun evictExpired(now: Long = System.currentTimeMillis()) {
        if (!initialized) return
        var evicted = 0
        val snapshot = _downloads.value.toMap()
        for ((key, _) in snapshot) {
            val meta = loadMetadata(key) ?: continue
            if (meta.expiresAt < now) {
                AppLog.i(TAG, "evictExpired: $key expired ${java.util.Date(meta.expiresAt)}")
                // Парсим ownerId/storyId из ключа "s_${ownerId}_${storyId}".
                val parts = key.removePrefix("s_").split("_")
                if (parts.size == 2) {
                    val ownerId = parts[0].toLongOrNull() ?: continue
                    val storyId = parts[1].toIntOrNull() ?: continue
                    removeDownload(ownerId, storyId)
                    evicted++
                }
            }
        }
        if (evicted > 0) AppLog.i(TAG, "evictExpired: removed $evicted expired stories")
    }

    /**
     * Fix #100 Risk #4: LRU eviction по размеру кэша.
     * Удаляет самые старые stories (по `downloadedAt` из .meta) пока суммарный
     * размер файлов не уйдёт под `limitMb`. Вызывается после каждой успешной
     * загрузки и в [init].
     *
     * @param limitMb лимит в МБ (0 = без ограничений).
     */
    fun enforceCacheLimit(limitMb: Int) {
        if (!initialized || limitMb <= 0) return
        val limitBytes = limitMb.toLong() * 1024 * 1024

        // Собираем (key, downloadedAt, fileSize) для всех COMPLETED.
        data class Cached(val key: String, val ownerId: Long, val storyId: Int, val downloadedAt: Long, val size: Long)
        val cached = mutableListOf<Cached>()
        var totalSize = 0L
        for ((key, state) in _downloads.value) {
            if (state.status != DownloadStatus.COMPLETED) continue
            val parts = key.removePrefix("s_").split("_")
            if (parts.size != 2) continue
            val ownerId = parts[0].toLongOrNull() ?: continue
            val storyId = parts[1].toIntOrNull() ?: continue
            // DL-C: файл может быть .ts (HLS-результат).
            val file = localCacheFile(key) ?: continue
            val meta = loadMetadata(key)
            cached += Cached(key, ownerId, storyId, meta?.downloadedAt ?: file.lastModified(), file.length())
            totalSize += file.length()
        }

        if (totalSize <= limitBytes) {
            AppLog.d(TAG, "enforceCacheLimit: total=${totalSize / 1024}KB ≤ limit=${limitMb}MB — no eviction")
            return
        }

        // Сортируем по downloadedAt ascending (самые старые — первые на удаление).
        cached.sortBy { it.downloadedAt }
        var removed = 0
        for (c in cached) {
            if (totalSize <= limitBytes) break
            AppLog.i(TAG, "enforceCacheLimit: evicting LRU ${c.key} (${c.size / 1024}KB, downloaded ${java.util.Date(c.downloadedAt)})")
            removeDownload(c.ownerId, c.storyId)
            totalSize -= c.size
            removed++
        }
        if (removed > 0) AppLog.i(TAG, "enforceCacheLimit: removed $removed LRU stories, total now ${totalSize / 1024}KB")
    }

    // --- Internal ---

    private fun ensureInitialized() {
        check(initialized) { "StoryVideoDownloadManager not initialized. Call init(context) in SovaApp.onCreate." }
    }

    private fun updateState(key: String, state: DownloadState) {
        _downloads.value = _downloads.value + (key to state)
    }

    private fun removeState(key: String) {
        _downloads.value = _downloads.value - key
    }

    /**
     * DL-C (Wave 2): выбрать источник скачивания из files map — (url, kind).
     * Приоритет: mp4_720 → mp4_480 → mp4_360 → mp4_240 → mp4_144 (kind="mp4") →
     * hls / hls_fmp4 (kind="hls") → dash* (kind="dash").
     * Story videos обычно не имеют 1080 (в отличие от catalog videos).
     *
     *  - "mp4"  — прогрессивный MP4: качается напрямую (Range-resume + `bytes=`
     *             параметр [HlsDownloadSupport.withBytes]);
     *  - "hls"  — манифест .m3u8: НЕЛЬЗЯ сохранять как файл — качается через
     *             [downloadHls] (раньше пользователь получал текст вместо видео);
     *  - "dash" — DASH-манифест: скачивание не поддерживается, [enqueueDownload]
     *             ставит FAILED с [DASH_FAIL_REASON];
     *  - null   — нет известного источника (caller: warn + skip, как раньше).
     */
    private fun pickBestSource(files: Map<String, String>?): Pair<String, String>? {
        if (files == null) return null
        fun firstOf(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { files[it]?.takeIf(String::isNotBlank) }
        firstOf("mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144")?.let { return it to KIND_MP4 }
        firstOf("hls", "hls_fmp4")?.let { return it to KIND_HLS }
        firstOf("dash", "dash_ondemand", "dash_sep", "dash_webm", "dash_webm_av1")?.let { return it to KIND_DASH }
        return null
    }

    // ─── Foreground Service ─────────────────────────────────────────

    private fun startForegroundService() {
        try {
            StoryVideoDownloadService.start(appContext)
        } catch (e: Exception) {
            AppLog.w(TAG, "startForegroundService failed", e)
        }
    }

    private fun maybeStopForegroundService() {
        val active = _downloads.value.values.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        if (active == 0) {
            try {
                StoryVideoDownloadService.stop(appContext)
            } catch (e: Exception) {
                AppLog.w(TAG, "maybeStopForegroundService failed", e)
            }
        }
    }

    // ─── Download (range-resume + 3 retries + URL-refresh on 403) ───

    private suspend fun downloadFile(
        key: String,
        story: Story,
        ownerName: String,
        ownerPhoto100: String?,
        initialUrl: String,
        initialKind: String,
    ) {
        val storyId = story.id
        val ownerId = story.ownerId

        updateState(key, DownloadState(
            trackId = storyId.toLong(),
            status = DownloadStatus.DOWNLOADING,
            progress = 0,
            title = ownerName,
            artist = ownerName,
            ownerId = ownerId,
        ))

        val tempFile = File(downloadDir, "$key.mp4.tmp")

        var currentUrl = initialUrl
        var currentKind = initialKind
        val maxRetries = 3
        var attempt = 0
        while (true) {
            attempt++
            try {
                // DL-C: HLS — сборка в отдельный temp + container-detect (.mp4/.ts);
                // mp4 — прежний путь с Range-resume.
                val ext: String
                if (currentKind == KIND_HLS) {
                    val hlsTemp = File(downloadDir, "$key.hls.tmp")
                    val detected = downloadHls(currentUrl, hlsTemp, key, storyId.toLong(), ownerId, ownerName)
                    ext = if (detected == "ts") "ts" else "mp4"
                    finalizeTempFile(hlsTemp, key, ext)
                } else {
                    downloadWithResume(currentUrl, tempFile, key, storyId, ownerId, ownerName)
                    ext = "mp4"
                    finalizeTempFile(tempFile, key, ext)
                }
                saveMetadata(story, ownerName, ownerPhoto100)
                updateState(key, DownloadState(
                    trackId = storyId.toLong(),
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = ownerName,
                    artist = ownerName,
                    ownerId = ownerId,
                ))
                AppLog.i(TAG, "Story video key=$key downloaded as .$ext (attempt $attempt)")
                // Fix #100 Risk #4: LRU eviction после каждой загрузки.
                // Читаем лимит из prefs (suspend). 0 = без ограничений.
                try {
                    val snap = SovaApp.get().prefs.data.first()
                    enforceCacheLimit(snap.storyCacheLimitMb)
                } catch (e: Exception) {
                    AppLog.w(TAG, "enforceCacheLimit skipped: ${e.message}")
                }
                return
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Fix #100: на 403 (CDN URL истёк) — ре-феч stories и retry с новым URL.
                if (isExpiredUrlError(e) && attempt <= maxRetries) {
                    AppLog.w(TAG, "downloadFile: 403 on attempt $attempt, refreshing URL for key=$key")
                    val refreshed = refreshStoryUrl(ownerId, storyId)
                    if (refreshed != null) {
                        currentUrl = refreshed.first
                        currentKind = refreshed.second
                        // Не считаем эту попытку потраченной — URL обновлён, retry.
                        attempt--
                        delay(500L)
                        continue
                    }
                }
                if (attempt >= maxRetries) {
                    AppLog.e(TAG, "downloadFile: $attempt attempts exhausted for key=$key: ${e.message}", e)
                    throw e
                }
                val backoffMs = (1000L * Math.pow(3.0, (attempt - 1).toDouble())).toLong()
                AppLog.w(TAG, "downloadFile attempt $attempt failed: ${e.message}, retry in ${backoffMs}ms")
                delay(backoffMs)
            }
        }
    }

    /** Признак ошибки истёкшего CDN URL (403/410). */
    private fun isExpiredUrlError(e: Throwable): Boolean {
        val msg = e.message ?: return false
        return msg.contains("HTTP 403") || msg.contains("HTTP 410")
    }

    /**
     * Fix #100 Risk #5: ре-феч stories для owner и поиск story по id.
     * DL-C: возвращает (url, kind) через [pickBestSource] (mp4 или hls) или null
     * (если story уже удалена/истекла на стороне VK).
     */
    private suspend fun refreshStoryUrl(ownerId: Long, storyId: Int): Pair<String, String>? {
        return try {
            val app = SovaApp.get()
            val groups = app.apiClient.storiesGet(count = 50)
            val story = groups.find { it.ownerId == ownerId }
                ?.stories?.find { it.id == storyId }
            if (story != null) {
                val src = pickBestSource(story.video?.files)
                AppLog.i(TAG, "refreshStoryUrl: got fresh source for story $ownerId/$storyId: $src")
                src
            } else {
                AppLog.w(TAG, "refreshStoryUrl: story $ownerId/$storyId not found in fresh storiesGet (expired on VK?)")
                null
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "refreshStoryUrl failed: ${e.message}", e)
            null
        }
    }

    private fun downloadWithResume(
        url: String,
        tempFile: File,
        key: String,
        storyId: Int,
        ownerId: Long,
        title: String,
    ) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        // DL-C: VK CDN без `bytes=` отдаёт HTML-заглушку — оборачиваем URL
        // (порт VK-Video-Downloader-4.js withBytes, эталон L185-188).
        val requestBuilder = Request.Builder().url(HlsDownloadSupport.withBytes(url))
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            AppLog.i(TAG, "downloadWithResume: resuming from $existingBytes bytes (key=$key)")
        }
        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            val isPartial = response.code == 206
            if (!response.isSuccessful && response.code != 206) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            val append = isPartial && existingBytes > 0
            if (!append && tempFile.exists()) {
                tempFile.delete()
            }

            val body = response.body ?: throw RuntimeException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = when {
                append && contentLength > 0 -> existingBytes + contentLength
                contentLength > 0 -> contentLength
                else -> -1L
            }
            var bytesRead = if (append) existingBytes else 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (totalBytes > 0) {
                            val progress = ((bytesRead.toFloat() / totalBytes.toFloat()) * 100f)
                                .toInt().coerceIn(0, 100)
                            updateState(key, DownloadState(
                                trackId = storyId.toLong(),
                                status = DownloadStatus.DOWNLOADING,
                                progress = progress,
                                title = title,
                                artist = title,
                                ownerId = ownerId,
                            ))
                        }
                    }
                }
            }
        }
    }

    // ─── DL-C (Wave 2): HLS download (.m3u8 → .mp4/.ts) ─────────────────────

    /** Файл кэша по ключу: ".mp4" (прогрессивные) или ".ts" (HLS→MPEG-TS). */
    private fun localCacheFile(key: String): File? {
        for (ext in listOf("mp4", "ts")) {
            val f = File(downloadDir, "$key.$ext")
            if (f.exists()) return f
        }
        return null
    }

    /** DL-C: temp → "$key.$ext"; stale-файл другого контейнера удаляется. */
    private fun finalizeTempFile(tempFile: File, key: String, ext: String) {
        val target = File(downloadDir, "$key.$ext")
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        File(downloadDir, "$key.${if (ext == "ts") "mp4" else "ts"}").delete()
    }

    /** DL-C: первые [n] байт файла (container-detect / hasAudioAtom); null если файл короче. */
    private fun readHeadBytes(file: File, n: Int): ByteArray? {
        return try {
            if (file.length() < n) return null
            java.io.RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(n)
                raf.readFully(buf)
                buf
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "readHeadBytes(${file.name}) failed: ${e.message}")
            null
        }
    }

    /**
     * DL-C: скачать HLS в [tempFile]. Порт техник VK-Video-Downloader-4.js
     * (эталон L875-892 — single-file fMP4 одним Range-запросом).
     *
     *  1. GET манифеста → [HlsDownloadSupport.parseM3U8].
     *  2. Мастер-плейлист (variants) → [HlsDownloadSupport.pickBestVariant] →
     *     variant-плейлист (до 3 уровней вложенности).
     *  3. Media-плейлист:
     *     - isSingleFile (fMP4 одним файлом, EXT-X-BYTERANGE) → ОДИН Range-запрос
     *       `bytes=initOff..lastEnd` → валидный .mp4 (hasAudioAtom — информационно);
     *     - иначе — последовательная закачка сегментов (EXT-X-MAP init первым)
     *       в temp, 3 retry/сегмент; прогресс = done/totalSegs.
     *  4. Container-detect по первым 8 байтам.
     *
     * @return контейнер из [HlsDownloadSupport.detectContainer]:
     *         "mp4" | "ts" | "unknown" (caller трактовывает unknown как "mp4").
     */
    private suspend fun downloadHls(
        playlistUrl: String,
        tempFile: File,
        key: String,
        trackId: Long,
        ownerId: Long,
        title: String,
    ): String {
        // 1-2. Мастер → variant (защита от бесконечной вложенности).
        var mediaUrl = playlistUrl
        var playlist: HlsDownloadSupport.HlsPlaylist? = null
        var hops = 0
        while (hops < 3) {
            val text = fetchPlaylistText(mediaUrl)
            val parsed = HlsDownloadSupport.parseM3U8(text, mediaUrl)
                ?: throw RuntimeException("Empty HLS playlist: $mediaUrl")
            if (parsed.variants.isNotEmpty()) {
                val best = HlsDownloadSupport.pickBestVariant(parsed)
                    ?: throw RuntimeException("HLS master playlist без вариантов: $mediaUrl")
                AppLog.i(TAG, "downloadHls: master → variant ${best.width}x${best.height} @${best.bandwidth}bps")
                mediaUrl = best.url
                hops++
                continue
            }
            playlist = parsed
            break
        }
        val pl = playlist
            ?: throw RuntimeException("HLS: слишком глубокая вложенность плейлистов: $playlistUrl")
        if (pl.segments.isEmpty()) throw RuntimeException("HLS плейлист без сегментов: $mediaUrl")

        // HLS не резюмится по байтам — сегменты ретраятся сами; начинаем с чистого temp.
        if (tempFile.exists()) tempFile.delete()

        if (pl.isSingleFile) {
            downloadHlsSingleFile(pl, tempFile, key, trackId, ownerId, title)
        } else {
            downloadHlsSegments(pl, tempFile, key, trackId, ownerId, title)
        }

        // 4. Container-detect по первым 8 байтам → корректное расширение (.ts если TS).
        val container = readHeadBytes(tempFile, 8)?.let(HlsDownloadSupport::detectContainer) ?: "unknown"
        AppLog.i(TAG, "downloadHls: key=$key done (${tempFile.length() / 1024} KB, container=$container)")
        return container
    }

    /** DL-C: GET текста манифеста (без withBytes — плейлист, не медиа). */
    private fun fetchPlaylistText(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${resp.message}")
            return resp.body?.string() ?: throw RuntimeException("Empty response body (playlist)")
        }
    }

    /**
     * DL-C: single-file fMP4 — все сегменты это BYTERANGE-куски ОДНОГО URL,
     * есть EXT-X-MAP. Порт VK-Video-Downloader-4.js:L875-892: ОДИН Range-запрос
     * `bytes=initOff..lastEnd` вместо покусегментной закачки → сразу валидный .mp4.
     */
    private fun downloadHlsSingleFile(
        pl: HlsDownloadSupport.HlsPlaylist,
        tempFile: File,
        key: String,
        trackId: Long,
        ownerId: Long,
        title: String,
    ) {
        val init = requireNotNull(pl.initSegment) { "single-file fMP4 без EXT-X-MAP" }
        val last = pl.segments.last()
        val lastEndExclusive = last.offset + last.length
        val rangeable = pl.segments.all { it.length > 0 } && lastEndExclusive > init.offset

        val builder: Request.Builder
        var totalBytes = -1L
        if (rangeable) {
            val rangeEndInclusive = lastEndExclusive - 1
            totalBytes = lastEndExclusive - init.offset
            builder = Request.Builder()
                .url(HlsDownloadSupport.withBytes(pl.segments.first().url))
                .header("Range", "bytes=${init.offset}-$rangeEndInclusive")
            AppLog.i(TAG, "downloadHlsSingleFile: key=$key один Range-запрос bytes=${init.offset}-$rangeEndInclusive ($totalBytes B, ${pl.segments.size} byteranges)")
        } else {
            // BYTERANGE не распарсились — качаем файл целиком (init + все данные в нём же).
            builder = Request.Builder().url(HlsDownloadSupport.withBytes(pl.segments.first().url))
            AppLog.i(TAG, "downloadHlsSingleFile: key=$key BYTERANGE недоступен — полный GET")
        }

        var bytesRead = 0L
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (resp.code != 200 && resp.code != 206) throw RuntimeException("HTTP ${resp.code}: ${resp.message}")
            val body = resp.body ?: throw RuntimeException("Empty response body")
            if (totalBytes <= 0) totalBytes = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(tempFile, false).use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = ((bytesRead.toFloat() / totalBytes.toFloat()) * 100f)
                                .toInt().coerceIn(0, 100)
                            updateState(key, DownloadState(
                                trackId = trackId,
                                status = DownloadStatus.DOWNLOADING,
                                progress = progress,
                                title = title,
                                artist = title,
                                ownerId = ownerId,
                            ))
                        }
                    }
                }
            }
        }
        // Информационно: есть ли аудио-трек в init (VK-эталон L887-888:
        // «единый MP4 без звука — так отдаёт VK»). На результат не влияет.
        readHeadBytes(tempFile, 4096)?.let { head ->
            val hasAudio = HlsDownloadSupport.hasAudioAtom(head)
            AppLog.i(TAG, "downloadHlsSingleFile: key=$key audio=${if (hasAudio) "yes" else "NO (единый fMP4 без звука — так отдаёт VK)"}")
        }
    }

    /**
     * DL-C: многосегментный HLS (TS или fMP4-сегменты) — последовательная
     * закачка в temp (EXT-X-MAP init первым), 3 retry/сегмент. Прогресс —
     * по сегментам: done/totalSegs. Сегмент буферизуется в памяти и пишется
     * целиком — retry не оставляет обрывков в temp.
     */
    private suspend fun downloadHlsSegments(
        pl: HlsDownloadSupport.HlsPlaylist,
        tempFile: File,
        key: String,
        trackId: Long,
        ownerId: Long,
        title: String,
    ) {
        val segments = ArrayList<HlsDownloadSupport.Segment>(pl.segments.size + 1)
        pl.initSegment?.let { segments.add(it) }
        segments.addAll(pl.segments)
        val totalSegs = segments.size
        AppLog.i(TAG, "downloadHlsSegments: key=$key сегментов: $totalSegs")

        FileOutputStream(tempFile, true).use { output ->
            for ((idx, seg) in segments.withIndex()) {
                currentCoroutineContext().ensureActive()
                output.write(fetchSegmentWithRetry(seg, idx, totalSegs))
                // Прогресс по сегментам: done/total.
                val progress = (((idx + 1).toFloat() / totalSegs.toFloat()) * 100f).toInt().coerceIn(0, 100)
                updateState(key, DownloadState(
                    trackId = trackId,
                    status = DownloadStatus.DOWNLOADING,
                    progress = progress,
                    title = title,
                    artist = title,
                    ownerId = ownerId,
                ))
            }
        }
    }

    /** DL-C: один сегмент с 3 retry; байты возвращаются целиком (пишет caller). */
    private suspend fun fetchSegmentWithRetry(
        seg: HlsDownloadSupport.Segment,
        idx: Int,
        totalSegs: Int,
    ): ByteArray {
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            try {
                val builder = Request.Builder().url(HlsDownloadSupport.withBytes(seg.url))
                if (seg.length > 0) {
                    builder.header("Range", "bytes=${seg.offset}-${seg.offset + seg.length - 1}")
                }
                httpClient.newCall(builder.build()).execute().use { resp ->
                    if (resp.code != 200 && resp.code != 206) throw RuntimeException("HTTP ${resp.code}: ${resp.message}")
                    val body = resp.body ?: throw RuntimeException("Empty response body (segment ${idx + 1})")
                    return body.bytes()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastErr = e
                AppLog.w(TAG, "downloadHlsSegments: сегмент ${idx + 1}/$totalSegs, попытка $attempt/3 failed: ${e.message}")
                if (attempt < 3) delay(500L * attempt)
            }
        }
        throw RuntimeException("HLS сегмент ${idx + 1}/$totalSegs failed после 3 retries: ${lastErr?.message}", lastErr)
    }

    private fun refreshFromDisk() {
        val map = mutableMapOf<String, DownloadState>()
        downloadDir.listFiles()?.forEach { file ->
            // DL-C: .mp4 (прогрессивные) или .ts (HLS→MPEG-TS результат).
            if (file.extension == "mp4" || file.extension == "ts") {
                // Ключ имеет формат "s_${ownerId}_${storyId}".
                val name = file.nameWithoutExtension
                if (!name.startsWith("s_")) return@forEach
                val core = name.removePrefix("s_")
                val underscore = core.indexOf('_')
                if (underscore <= 0) return@forEach
                val ownerIdStr = core.substring(0, underscore)
                val storyIdStr = core.substring(underscore + 1)
                val ownerId = ownerIdStr.toLongOrNull() ?: return@forEach
                val storyId = storyIdStr.toIntOrNull() ?: return@forEach
                val meta = loadMetadata(name)
                map[name] = DownloadState(
                    trackId = storyId.toLong(),
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = meta?.ownerName ?: "",
                    artist = meta?.ownerName ?: "",
                    ownerId = ownerId,
                )
            }
        }
        _downloads.value = map
        AppLog.i(TAG, "Loaded ${map.size} existing story video downloads from disk")
    }

    // ─── Metadata persistence ───────────────────────────────────────

    /** Sidecar-метаданные story video, переживают рестарт приложения. */
    data class StoryVideoMeta(
        val ownerId: Long,
        val storyId: Int,
        val ownerName: String,
        val ownerPhoto100: String? = null,
        val thumbUrl: String? = null,
        val duration: Int = 0,
        val storyDate: Long = 0,
        val downloadedAt: Long = 0,
        val expiresAt: Long = 0,
        val sourceUrl: String = "",
        val fileSize: Long = 0,
    )

    private fun saveMetadata(story: Story, ownerName: String, ownerPhoto100: String?) {
        try {
            val key = storyKey(story.ownerId, story.id)
            val metaFile = File(downloadDir, "$key.meta")
            val now = System.currentTimeMillis()
            val thumb = story.video?.preview?.sizes?.maxByOrNull { it.width * it.height }?.url
                ?: story.thumbUrl
            val srcUrl = pickBestSource(story.video?.files)?.first ?: ""
            val meta = StoryVideoMeta(
                ownerId = story.ownerId,
                storyId = story.id,
                ownerName = ownerName,
                ownerPhoto100 = ownerPhoto100,
                thumbUrl = thumb,
                duration = story.video?.duration ?: 0,
                storyDate = story.date * 1000L,
                downloadedAt = now,
                expiresAt = (story.date * 1000L) + STORY_TTL_MS,
                sourceUrl = srcUrl,
                fileSize = localCacheFile(key)?.length() ?: 0L,
            )
            val json = com.google.gson.Gson().toJson(meta)
            metaFile.writeText(json)
        } catch (e: Exception) {
            AppLog.w(TAG, "saveMetadata failed for story #${story.id}: ${e.message}")
        }
    }

    private fun loadMetadata(key: String): StoryVideoMeta? {
        return try {
            val metaFile = File(downloadDir, "$key.meta")
            if (!metaFile.exists()) return null
            val json = metaFile.readText()
            com.google.gson.Gson().fromJson(json, StoryVideoMeta::class.java)
        } catch (_: Exception) { null }
    }

    private fun deleteMetadata(key: String) {
        try {
            val metaFile = File(downloadDir, "$key.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (_: Exception) {}
    }
}
