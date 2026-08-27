// File: media/StoryVideoDownloadManager.kt
package re.pinok.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * - Файлы: `${key}.mp4` (видео) + `${key}.meta` (JSON sidecar [StoryVideoMeta]).
 *
 * ## TTL eviction
 * Stories живут 24h на стороне VK. При [refreshFromDisk] и периодически через
 * [evictExpired] удаляем файлы, где `expiresAt < now`.
 *
 * ## URL-refresh on 403
 * CDN URL stories короткоживущие. При 403 во время загрузки вызываем
 * [refreshStoryUrl] — ре-феч `storiesGet()` для owner и поиск story по id.
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

        val url = pickBestMp4(storyVideo.files)
        if (url == null) {
            AppLog.w(TAG, "enqueueDownload: story #${story.id} has no mp4 URL")
            return
        }

        val key = storyKey(story.ownerId, story.id)
        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueDownload: story #${story.id} already ${existing.status}")
                return
            }

            AppLog.i(TAG, "enqueueDownload: story #${story.id} (silent=$silent, $url)")
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
                downloadFile(key, story, ownerName, ownerPhoto100, url)
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
        val key = storyKey(ownerId, storyId)
        val file = File(downloadDir, "$key.mp4")
        return if (file.exists()) file else null
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
            val file = File(downloadDir, "$key.mp4")
            if (!file.exists()) continue
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
     * Выбрать лучший MP4 URL из files map.
     * Приоритет: mp4_720 → mp4_480 → mp4_360 → mp4_240 → mp4_144 → hls.
     * Story videos обычно не имеют 1080 (в отличие от catalog videos).
     */
    private fun pickBestMp4(files: Map<String, String>?): String? {
        if (files == null) return null
        return listOf("mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144", "hls")
            .firstNotNullOfOrNull { files[it]?.takeIf(String::isNotBlank) }
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
        val targetFile = File(downloadDir, "$key.mp4")

        var currentUrl = initialUrl
        val maxRetries = 3
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadWithResume(currentUrl, tempFile, key, storyId, ownerId, ownerName)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
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
                AppLog.i(TAG, "Story video key=$key downloaded (attempt $attempt)")
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
                        currentUrl = refreshed
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
     * Возвращает свежий mp4 URL или null (если story уже удалена/истекла на стороне VK).
     */
    private suspend fun refreshStoryUrl(ownerId: Long, storyId: Int): String? {
        return try {
            val app = SovaApp.get()
            val groups = app.apiClient.storiesGet(count = 50)
            val story = groups.find { it.ownerId == ownerId }
                ?.stories?.find { it.id == storyId }
            if (story != null) {
                val url = pickBestMp4(story.video?.files)
                AppLog.i(TAG, "refreshStoryUrl: got fresh URL for story $ownerId/$storyId: $url")
                url
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
        val requestBuilder = Request.Builder().url(url)
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

    private fun refreshFromDisk() {
        val map = mutableMapOf<String, DownloadState>()
        downloadDir.listFiles()?.forEach { file ->
            if (file.extension == "mp4") {
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
            val srcUrl = pickBestMp4(story.video?.files) ?: ""
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
                fileSize = File(downloadDir, "$key.mp4").takeIf { it.exists() }?.length() ?: 0L,
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
