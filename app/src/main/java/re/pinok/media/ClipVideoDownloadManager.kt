// File: media/ClipVideoDownloadManager.kt
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
import re.pinok.data.model.Video
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ClipVideoDownloadManager — офлайн-кэш для VK Clips (коротких вертикальных
 * видео, TikTok-like feed).
 *
 * §37.12 #329: смоделирован по образцу [StoryVideoDownloadManager] (НЕ
 * [VideoDownloadManager]), т.к. clips имеют story-like характеристики:
 * короткая длительность (≤60s), короткоживущие CDN URL (нужен URL-refresh on
 * 403), auto-cache-on-play (silent=true), LRU eviction по размеру.
 *
 * ## Отличия от [StoryVideoDownloadManager]
 *  1. Модель — [Video] (`Long videoId`), а не [re.pinok.data.model.Story]
 *     (`Int storyId`). Clip'ы — это Video с `isClips=1` (см. Models.kt:355).
 *  2. TTL — 7 дней (clips не «исчезают» через 24h как stories, но CDN URL
 *     истекают; периодическая пере-валидция освобождает место).
 *  3. accessKey — сохраняется в sidecar (clips часто приватные/ограниченные,
 *     без accessKey нельзя ре-фетчнуть URL через `video.get`).
 *  4. URL-refresh — через `VKApiClient.videoGetClipById(ownerId, videoId,
 *     accessKey)` (НЕ `shortVideoGet`, т.к. тот не принимает accessKey).
 *
 * ## Хранение
 * - Директория: `filesDir/clip_downloads/`
 * - Ключ: `"c_${ownerId}_${videoId}"` (prefix `c_` = clip). Не коллизирует с
 *   catalog videos (`"${ownerId}_${videoId}"` в `video_downloads/`) и stories
 *   (`"s_${ownerId}_${storyId}"` в `story_video_downloads/`).
 * - Файлы: `${key}.mp4` (видео) + `${key}.mp4.tmp` (partial, Range-resume)
 *   + `${key}.meta` (JSON sidecar [ClipVideoMeta]).
 *
 * ## TTL eviction
 * Clips не имеют hard-expiry на стороне VK (как stories 24h), но мы держим
 * мягкий TTL 7 дней: старые скачивания вычищаются в [evictExpired] при init
 * и периодически. Освобождает место под свежие clips.
 *
 * ## LRU eviction
 * [enforceCacheLimit] удаляет самые старые clips (по `downloadedAt`) пока
 * суммарный размер не уйдёт под лимит. По умолчанию 300 МБ (см. RESEARCH-1).
 *
 * ## URL-refresh on 403
 * CDN URL clips короткоживущие (~часы). При 403/410 во время загрузки
 * вызываем [refreshClipUrl] — ре-фетч `video.get?videos=ownerId_videoId_accessKey`
 * для свежего `files[]`.
 *
 * ## Auto-cache-on-play
 * [enqueueDownload] с `silent=true` — для auto-cache при первом просмотре в
 * ClipsFeedScreen (mirror `StoryVideoDownloadManager.enqueueDownload(silent=true)`
 * в StoryViewerScreen).
 *
 * См. `STORY_VIDEO_CACHE_PLAN.md` (Fix #100), `FEED_RESEARCH.md`, RESEARCH-1
 * (worklog §1451+).
 */
object ClipVideoDownloadManager {

    private const val TAG = "ClipVideoDownloadMgr"
    private const val DOWNLOAD_DIR = "clip_downloads"
    // §37.12 #329: clips — мягкий TTL 7 дней (604800000 ms). Stories имеют 24h,
    // но clips живут дольше на стороне VK. Чистим для освобождения места.
    private const val CLIP_TTL_MS = 7L * 24 * 60 * 60 * 1000  // 7 days

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
     * Строковый ключ clip-видео: `"c_${ownerId}_${videoId}"`.
     * Prefix `c_` (clip) гарантирует отсутствие коллизии:
     *  - с catalog videos (`"${ownerId}_${videoId}"` в [VideoDownloadManager])
     *  - со stories (`"s_${ownerId}_${storyId}"` в [StoryVideoDownloadManager])
     */
    fun clipKey(ownerId: Long, videoId: Long): String = "c_${ownerId}_${videoId}"

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
            // §37.12 #329: при старте удаляем протухшие clips (TTL 7 дней).
            evictExpired()
        }
    }

    /**
     * Поставить clip в очередь загрузки.
     *
     * @param video        объект Video (isClips=1). Должен иметь `bestPlayUrl != null`
     *                     (mp4_720/mp4_480/.../hls или хотя бы player).
     * @param authorName   имя автора/группы (для отображения в OfflineManager + sidecar).
     * @param authorAvatar аватар автора (для UI).
     * @param silent       `true` = auto-cache (skip foreground notification).
     *                     Mirror `StoryVideoDownloadManager.enqueueDownload(silent=true)`.
     */
    fun enqueueDownload(
        video: Video,
        authorName: String? = null,
        authorAvatar: String? = null,
        silent: Boolean = false,
    ) {
        ensureInitialized()

        val url = video.bestPlayUrl
        if (url == null) {
            AppLog.w(TAG, "enqueueDownload: clip #${video.id} has no play URL (files=${video.files?.size ?: 0})")
            return
        }
        // §37.12 #329: defensive — `bestPlayUrl` falls back to `player` (HTML
        // страница плеера) когда files[] пустой. Скачивать HTML нет смысла.
        // В этом случае ждём, пока ClipsRepository подтянет свежий files[]
        // через shortVideo.get (см. refreshClipUrl на 403).
        if (video.files == null && url == video.player) {
            AppLog.w(TAG, "enqueueDownload: clip #${video.id} only has player HTML (no files[]) — skip")
            return
        }

        val key = clipKey(video.ownerId, video.id)
        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueDownload: clip #${video.id} already ${existing.status}")
                return
            }

            AppLog.i(TAG, "enqueueDownload: clip #${video.id} (silent=$silent, $url)")
            val title = video.title.ifBlank { authorName ?: "Клип ${video.ownerId}" }
            updateState(key, DownloadState(
                trackId = video.id,
                status = DownloadStatus.QUEUED,
                progress = 0,
                title = title,
                artist = authorName ?: "",
                ownerId = video.ownerId,
            ))
            saveMetadata(video, authorName, authorAvatar)
        }
        if (!silent) startForegroundService()

        val job = scope.launch {
            try {
                downloadFile(key, video, authorName, authorAvatar, url)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e(TAG, "downloadFile failed for key=$key: ${t.message}", t)
                if (_downloads.value[key]?.isInProgress == true) {
                    updateState(key, DownloadState(
                        trackId = video.id,
                        status = DownloadStatus.FAILED,
                        progress = 0,
                        reason = t.message,
                        title = video.title,
                        artist = authorName ?: "",
                        ownerId = video.ownerId,
                    ))
                }
            } finally {
                activeJobs.remove(key)
                maybeStopForegroundService()
            }
        }
        activeJobs[key] = job
    }

    fun removeDownload(ownerId: Long, videoId: Long) {
        ensureInitialized()
        val key = clipKey(ownerId, videoId)
        AppLog.i(TAG, "removeDownload: owner=$ownerId videoId=$videoId (key=$key)")

        activeJobs[key]?.cancel()
        activeJobs.remove(key)

        File(downloadDir, "$key.mp4").delete()
        File(downloadDir, "$key.mp4.tmp").delete()
        deleteMetadata(key)

        removeState(key)
        maybeStopForegroundService()
    }

    fun getDownloadState(ownerId: Long, videoId: Long): DownloadState? {
        val key = clipKey(ownerId, videoId)
        return _downloads.value[key]
    }

    fun isDownloaded(ownerId: Long, videoId: Long): Boolean {
        ensureInitialized()
        val key = clipKey(ownerId, videoId)
        return _downloads.value[key]?.isCompleted == true
    }

    fun getLocalFile(ownerId: Long, videoId: Long): File? {
        ensureInitialized()
        val key = clipKey(ownerId, videoId)
        val file = File(downloadDir, "$key.mp4")
        return if (file.exists()) file else null
    }

    /**
     * §37.12 #329: public accessor к .meta sidecar — нужен OfflineManagerScreen
     * для отображения title/description/thumbUrl/duration/authorName/authorAvatar/accessKey.
     * Возвращает null если файл отсутствует/повреждён.
     */
    fun getClipMeta(key: String): ClipVideoMeta? = loadMetadata(key)

    /** Перегрузка для удобства: по (ownerId, videoId). */
    fun getClipMeta(ownerId: Long, videoId: Long): ClipVideoMeta? =
        loadMetadata(clipKey(ownerId, videoId))

    /**
     * Удалить все clips с истёкшим TTL (7 дней с момента скачивания).
     * Вызывается в [init] и периодически (например, при открытии OfflineManager).
     */
    fun evictExpired(now: Long = System.currentTimeMillis()) {
        if (!initialized) return
        var evicted = 0
        val snapshot = _downloads.value.toMap()
        for ((key, _) in snapshot) {
            val meta = loadMetadata(key) ?: continue
            if (meta.downloadedAt + CLIP_TTL_MS < now) {
                AppLog.i(TAG, "evictExpired: $key expired (downloaded ${java.util.Date(meta.downloadedAt)})")
                // Парсим ownerId/videoId из ключа "c_${ownerId}_${videoId}".
                val parts = key.removePrefix("c_").split("_")
                if (parts.size == 2) {
                    val ownerId = parts[0].toLongOrNull() ?: continue
                    val videoId = parts[1].toLongOrNull() ?: continue
                    removeDownload(ownerId, videoId)
                    evicted++
                }
            }
        }
        if (evicted > 0) AppLog.i(TAG, "evictExpired: removed $evicted expired clips")
    }

    /**
     * §37.12 #329 Risk #4: LRU eviction по размеру кэша.
     * Удаляет самые старые clips (по `downloadedAt` из .meta) пока суммарный
     * размер файлов не уйдёт под `limitMb`. Вызывается после каждой успешной
     * загрузки и в [init].
     *
     * @param limitMb лимит в МБ (0 = без ограничений).
     */
    fun enforceCacheLimit(limitMb: Int) {
        if (!initialized || limitMb <= 0) return
        val limitBytes = limitMb.toLong() * 1024 * 1024

        // Собираем (key, downloadedAt, fileSize) для всех COMPLETED.
        data class Cached(val key: String, val ownerId: Long, val videoId: Long, val downloadedAt: Long, val size: Long)
        val cached = mutableListOf<Cached>()
        var totalSize = 0L
        for ((key, state) in _downloads.value) {
            if (state.status != DownloadStatus.COMPLETED) continue
            val parts = key.removePrefix("c_").split("_")
            if (parts.size != 2) continue
            val ownerId = parts[0].toLongOrNull() ?: continue
            val videoId = parts[1].toLongOrNull() ?: continue
            val file = File(downloadDir, "$key.mp4")
            if (!file.exists()) continue
            val meta = loadMetadata(key)
            cached += Cached(key, ownerId, videoId, meta?.downloadedAt ?: file.lastModified(), file.length())
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
            removeDownload(c.ownerId, c.videoId)
            totalSize -= c.size
            removed++
        }
        if (removed > 0) AppLog.i(TAG, "enforceCacheLimit: removed $removed LRU clips, total now ${totalSize / 1024}KB")
    }

    // --- Internal ---

    private fun ensureInitialized() {
        check(initialized) { "ClipVideoDownloadManager not initialized. Call init(context) in SovaApp.onCreate." }
    }

    private fun updateState(key: String, state: DownloadState) {
        _downloads.value = _downloads.value + (key to state)
    }

    private fun removeState(key: String) {
        _downloads.value = _downloads.value - key
    }

    // ─── Foreground Service ─────────────────────────────────────────

    private fun startForegroundService() {
        try {
            ClipDownloadService.start(appContext)
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
                ClipDownloadService.stop(appContext)
            } catch (e: Exception) {
                AppLog.w(TAG, "maybeStopForegroundService failed", e)
            }
        }
    }

    // ─── Download (range-resume + 3 retries + URL-refresh on 403) ───

    private suspend fun downloadFile(
        key: String,
        video: Video,
        authorName: String?,
        authorAvatar: String?,
        initialUrl: String,
    ) {
        val videoId = video.id
        val ownerId = video.ownerId
        val title = video.title.ifBlank { authorName ?: "Клип $ownerId" }

        updateState(key, DownloadState(
            trackId = videoId,
            status = DownloadStatus.DOWNLOADING,
            progress = 0,
            title = title,
            artist = authorName ?: "",
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
                downloadWithResume(currentUrl, tempFile, key, videoId, ownerId, title)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                saveMetadata(video, authorName, authorAvatar)
                updateState(key, DownloadState(
                    trackId = videoId,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = title,
                    artist = authorName ?: "",
                    ownerId = ownerId,
                ))
                AppLog.i(TAG, "Clip video key=$key downloaded (attempt $attempt)")
                // §37.12 #329 Risk #4: LRU eviction после каждой загрузки.
                // Используем storyCacheLimitMb как лимит по умолчанию (RESEARCH-1
                // рекомендует отдельный clipCacheLimitMb=300, но пока отдельного
                // prefs-ключа нет — берём storyCacheLimitMb). 0 = без ограничений.
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
                // §37.12 #329: на 403/410 (CDN URL истёк) — ре-феч clip и retry с новым URL.
                if (isExpiredUrlError(e) && attempt <= maxRetries) {
                    AppLog.w(TAG, "downloadFile: 403 on attempt $attempt, refreshing URL for key=$key")
                    val refreshed = refreshClipUrl(ownerId, videoId, video.accessKey)
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
     * §37.12 #329 Risk #5: ре-феч clip по (ownerId, videoId, accessKey).
     *
     * Использует `VKApiClient.videoGetClipById` (video.get с extended=1), а НЕ
     * `shortVideoGet` — т.к. `shortVideoGet` не принимает accessKey, а clips
     * часто приватные/ограниченные и требуют accessKey для повторного запроса
     * (см. RESEARCH-1 §1720 — Blocker).
     *
     * Возвращает свежий `bestPlayUrl` или null (если clip удалён/недоступен
     * на стороне VK).
     */
    private suspend fun refreshClipUrl(ownerId: Long, videoId: Long, accessKey: String?): String? {
        return try {
            val app = SovaApp.get()
            val video = app.apiClient.videoGetClipById(ownerId, videoId, accessKey)
            if (video != null) {
                val url = video.bestPlayUrl
                AppLog.i(TAG, "refreshClipUrl: got fresh URL for clip $ownerId/$videoId: $url " +
                    "(files=${video.files?.size ?: 0}, accessKey=${if (video.accessKey != null) "yes" else "no"})")
                url
            } else {
                AppLog.w(TAG, "refreshClipUrl: clip $ownerId/$videoId not found via videoGetClipById (deleted on VK?)")
                null
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "refreshClipUrl failed: ${e.message}", e)
            null
        }
    }

    private fun downloadWithResume(
        url: String,
        tempFile: File,
        key: String,
        videoId: Long,
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
                                trackId = videoId,
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
                // Ключ имеет формат "c_${ownerId}_${videoId}".
                val name = file.nameWithoutExtension
                if (!name.startsWith("c_")) return@forEach
                val core = name.removePrefix("c_")
                val underscore = core.indexOf('_')
                if (underscore <= 0) return@forEach
                val ownerIdStr = core.substring(0, underscore)
                val videoIdStr = core.substring(underscore + 1)
                val ownerId = ownerIdStr.toLongOrNull() ?: return@forEach
                val videoId = videoIdStr.toLongOrNull() ?: return@forEach
                val meta = loadMetadata(name)
                map[name] = DownloadState(
                    trackId = videoId,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = meta?.title ?: "",
                    artist = meta?.authorName ?: "",
                    ownerId = ownerId,
                )
            }
        }
        _downloads.value = map
        AppLog.i(TAG, "Loaded ${map.size} existing clip video downloads from disk")
    }

    // ─── Metadata persistence ───────────────────────────────────────

    /**
     * Sidecar-метаданные clip'а, переживают рестарт приложения.
     *
     * §37.12 #329: дополнительно к StoryVideoMeta полям сохраняем:
     *  - [description] — описание clip'а (для оффлайн-листа)
     *  - [accessKey]   — critical для URL re-fetch (см. RESEARCH-1 §1720).
     */
    data class ClipVideoMeta(
        val ownerId: Long,
        val videoId: Long,
        val title: String,
        val description: String? = null,
        val thumbUrl: String? = null,
        val duration: Int = 0,
        val authorName: String? = null,
        val authorAvatar: String? = null,
        val accessKey: String? = null,
        val downloadedAt: Long = 0,
        val fileSize: Long = 0,
    )

    private fun saveMetadata(video: Video, authorName: String?, authorAvatar: String?) {
        try {
            val key = clipKey(video.ownerId, video.id)
            val metaFile = File(downloadDir, "$key.meta")
            val now = System.currentTimeMillis()
            val meta = ClipVideoMeta(
                ownerId = video.ownerId,
                videoId = video.id,
                title = video.title,
                description = video.description,
                thumbUrl = video.thumbUrl,
                duration = video.duration,
                authorName = authorName,
                authorAvatar = authorAvatar,
                accessKey = video.accessKey,
                downloadedAt = now,
                fileSize = File(downloadDir, "$key.mp4").takeIf { it.exists() }?.length() ?: 0L,
            )
            val json = com.google.gson.Gson().toJson(meta)
            metaFile.writeText(json)
        } catch (e: Exception) {
            AppLog.w(TAG, "saveMetadata failed for clip #${video.id}: ${e.message}")
        }
    }

    private fun loadMetadata(key: String): ClipVideoMeta? {
        return try {
            val metaFile = File(downloadDir, "$key.meta")
            if (!metaFile.exists()) return null
            val json = metaFile.readText()
            com.google.gson.Gson().fromJson(json, ClipVideoMeta::class.java)
        } catch (_: Exception) { null }
    }

    private fun deleteMetadata(key: String) {
        try {
            val metaFile = File(downloadDir, "$key.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (_: Exception) {}
    }
}
