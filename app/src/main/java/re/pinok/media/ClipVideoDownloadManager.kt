// File: media/ClipVideoDownloadManager.kt
package re.pinok.media

import android.content.Context
import android.os.Environment
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
 * - Файлы: `${key}.mp4` (видео) / `${key}.ts` (HLS→MPEG-TS, DL-C) + `${key}.mp4.tmp` (partial, Range-resume)
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
 * ## HLS/DASH-источники (DL-C, Wave 2)
 * [pickClipSource] (НЕ `bestPlayUrl` — тот отдаёт .m3u8/.mpd как «URL файла»)
 * возвращает (url, kind): mp4_* качается напрямую (с `bytes=` параметром —
 * [HlsDownloadSupport.withBytes]); hls_ondemand/hls_fmp4/hls — манифест через
 * [downloadHls] (single-file fMP4 → один Range-запрос; иначе последовательная
 * сборка сегментов, 3 retry/сегмент; container-detect → .mp4/.ts); dash* —
 * явный FAILED с [DASH_FAIL_REASON] (не тихий skip). Прогресс HLS — по
 * сегментам (done/total).
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

    // DL-C (Wave 2): kind-таги источника из [pickClipSource] + причина FAIL для DASH-only.
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
     * Строковый ключ clip-видео: `"c_${ownerId}_${videoId}"`.
     * Prefix `c_` (clip) гарантирует отсутствие коллизии:
     *  - с catalog videos (`"${ownerId}_${videoId}"` в [VideoDownloadManager])
     *  - со stories (`"s_${ownerId}_${storyId}"` в [StoryVideoDownloadManager])
     */
    fun clipKey(ownerId: Long, videoId: Long): String = "c_${ownerId}_${videoId}"

    /**
     * DL-EXTERNAL-ONLY: Сменить директорию скачивания clips (переносит файлы).
     * Mirror [VideoDownloadManager.reconfigurePath] / [StoryVideoDownloadManager.reconfigurePath].
     *
     * Поддерживаемые форматы пути см. в [resolveClipDir]. Internal storage
     * больше НЕ используется как fallback — если целевая папка недоступна для
     * записи, оставляем текущий downloadDir (без отката на filesDir).
     */
    fun reconfigurePath(newPath: String) {
        if (!initialized) return
        val newDir = resolveClipDir(newPath)
        newDir.mkdirs()
        if (!newDir.exists() || !newDir.canWrite() || !probeWritable(newDir)) {
            AppLog.e(TAG, "reconfigurePath: target dir not writable: ${newDir.absolutePath} — keeping previous dir")
            // DL-EXTERNAL-ONLY: НЕ откатываемся на internal. Оставляем текущий downloadDir.
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
            FileOutputStream(probe).use { it.write(0) }
            probe.delete()
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "probeWritable: write test FAILED for ${dir.absolutePath}: ${e.javaClass.simpleName}: ${e.message}")
            probe.delete()
            false
        }
    }

    /**
     * DL-EXTERNAL-ONLY: Нормализует путь скачивания clips. Mirror
     * [VideoDownloadManager.resolveVideoDir] / [StoryVideoDownloadManager.resolveStoryDir].
     *
     * Поддерживаемые форматы:
     *  - "" (blank) → /storage/emulated/0/Movies/PinoK/Clips (default).
     *  - "/Movies/PinoK/Clips/" → relative к external storage.
     *  - "/storage/emulated/0/..." (абсолютный external) → как есть.
     *  - "content://..." SAF tree URI → fallback на default external.
     */
    private fun resolveClipDir(rawPath: String): File {
        if (rawPath.isBlank()) {
            return File(Environment.getExternalStorageDirectory(), "Movies/PinoK/Clips")
        }
        val path = rawPath.trim().trimEnd('/')
        if (path.startsWith("/storage/") || path.startsWith("/data/") ||
            path.startsWith("/sdcard/") || path.startsWith("/mnt/")) {
            return File(path)
        }
        if (path.startsWith("content://")) {
            AppLog.w(TAG, "resolveClipDir: SAF URI not supported for clips, using default")
            return File(Environment.getExternalStorageDirectory(), "Movies/PinoK/Clips")
        }
        val relPath = path.removePrefix("/")
        return File(Environment.getExternalStorageDirectory(), relPath)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext
            // DL-EXTERNAL-ONLY: дефолтный путь СРАЗУ в /Movies/PinoK/Clips/ (external).
            // Internal storage (filesDir/clip_downloads) больше НЕ используем.
            downloadDir = resolveClipDir("/Movies/PinoK/Clips/").apply { mkdirs() }

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
     * @param video        объект Video (isClips=1). Должен иметь files[] с mp4/hls
     *                     источником (см. [pickClipSource]); DASH-only → FAILED.
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
        val fileKey = pickBestKey(video.files)
        if (fileKey == null) {
            // DL-EXPLICIT: явный if вместо `video.files?.size ?: 0` в логе.
            val filesCount = if (video.files != null) video.files.size else 0
            AppLog.w(TAG, "enqueueDownload: clip #${video.id} has no downloadable URL (files=$filesCount)")
            return
        }
        enqueueDownload(video, fileKey, authorName, authorAvatar, silent)
    }

    /**
     * DL-QP: скачать clip с явно выбранным качеством (fileKey из шторки выбора).
     * Контракт зеркален [VideoDownloadManager.enqueueDownload].
     *
     * @param fileKey один из ключей Video.files: «mp4_1080»/…/«mp4_144»/«mp4_orig»,
     *   «hls»/«hls_fmp4»/«hls_ondemand» (→ сборка через [downloadHls]),
     *   «dash*» (→ явный FAILED с [DASH_FAIL_REASON]).
     */
    fun enqueueDownload(
        video: Video,
        fileKey: String,
        authorName: String? = null,
        authorAvatar: String? = null,
        silent: Boolean = false,
    ) {
        ensureInitialized()
        // DL-C: НЕ `bestPlayUrl` — тот отдаёт .m3u8/.mpd-манифесты как «URL файла»
        // (пользователь получал текст вместо видео) и падает на player-HTML.
        // Скачиванию нужен свой порядок + kind (см. [pickSourceByKey]).
        val src = pickSourceByKey(video.files, fileKey)
        if (src == null) {
            // DL-EXPLICIT: явный if вместо `video.files?.size ?: 0` в логе.
            val filesCount = if (video.files != null) video.files.size else 0
            AppLog.w(TAG, "enqueueDownload: clip #${video.id} fileKey=$fileKey не найден в files (size=$filesCount)")
            return
        }
        val (url, kind) = src
        enqueueDownloadInternal(video, url, kind, authorName, authorAvatar, silent)
    }

    private fun enqueueDownloadInternal(
        video: Video,
        url: String,
        kind: String,
        authorName: String?,
        authorAvatar: String?,
        silent: Boolean,
    ) {
        val key = clipKey(video.ownerId, video.id)
        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueDownload: clip #${video.id} already ${existing.status}")
                return
            }

            // DL-C: DASH-манифест нельзя скачать как файл — явный FAIL, не тихий skip.
            if (kind == KIND_DASH) {
                AppLog.w(TAG, "enqueueDownload: clip #${video.id} DASH-only — FAILED ($DASH_FAIL_REASON)")
                updateState(key, DownloadState(
                    trackId = video.id,
                    status = DownloadStatus.FAILED,
                    progress = 0,
                    reason = DASH_FAIL_REASON,
                    title = video.title.ifBlank { authorName ?: "Клип ${video.ownerId}" },
                    artist = authorName ?: "",
                    ownerId = video.ownerId,
                ))
                return
            }

            AppLog.i(TAG, "enqueueDownload: clip #${video.id} (silent=$silent, kind=$kind, $url)")
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
                downloadFile(key, video, authorName, authorAvatar, url, kind)
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
        // DL-C: HLS-результат может быть .ts (+ temp сборки "$key.hls.tmp").
        File(downloadDir, "$key.ts").delete()
        File(downloadDir, "$key.ts.tmp").delete()
        File(downloadDir, "$key.hls.tmp").delete()
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
        // DL-C: .mp4 (прогрессивные) или .ts (HLS→MPEG-TS результат).
        return localCacheFile(clipKey(ownerId, videoId))
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
            // DL-C: файл может быть .ts (HLS-результат).
            val file = localCacheFile(key) ?: continue
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

    /**
     * Лучший fileKey по приоритету (mp4_1080→…→mp4_144→mp4_orig → hls → dash) —
     * для auto-pick в legacy [enqueueDownload] (auto-cache-on-play) и для
     * [refreshClipUrl]. Возвращает null если files пуст/нет источников.
     *
     * DL-EXPLICIT: явный if-каскад вместо elvis-цепочки `?:`.
     */
    private fun pickBestKey(files: Map<String, String>?): String? {
        if (files == null) return null
        fun firstOf(vararg keys: String): String? {
            for (k in keys) {
                val v = files[k]
                if (v != null && v.isNotBlank()) return k
            }
            return null
        }
        // Приоритет 1: mp4 (прямые .mp4 URL — лучшее качество, без сборки).
        val mp4 = firstOf("mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144", "mp4_orig")
        if (mp4 != null) return mp4
        // Приоритет 2: HLS (требует сборки m3u8 в один файл).
        val hls = firstOf("hls_ondemand", "hls_fmp4", "hls")
        if (hls != null) return hls
        // Приоритет 3: DASH (приведёт к FAILED — DASH не качаем).
        val dash = firstOf("dash_ondemand", "dash", "dash_sep", "dash_webm", "dash_webm_av1")
        if (dash != null) return dash
        return null
    }

    /**
     * Map fileKey → (url, kind). null если ключа нет/blank/неизвестный kind.
     * kind: [KIND_MP4] (mp4_* и mp4_orig), [KIND_HLS] (hls/hls_fmp4/hls_ondemand),
     * [KIND_DASH] (dash*) — для FAILED-обработки в [enqueueDownloadInternal].
     *
     * DL-EXPLICIT: явная nullable-проверка вместо `?.takeIf(...) ?: return null`.
     */
    private fun pickSourceByKey(files: Map<String, String>?, fileKey: String): Pair<String, String>? {
        if (files == null) return null
        val rawUrl = files[fileKey]
        if (rawUrl == null || rawUrl.isBlank()) return null
        val kind = when {
            fileKey.startsWith("mp4_") -> KIND_MP4
            fileKey == "hls" || fileKey == "hls_fmp4" || fileKey == "hls_ondemand" -> KIND_HLS
            fileKey.startsWith("dash") -> KIND_DASH
            else -> return null
        }
        return rawUrl to kind
    }

    private fun pickClipSource(files: Map<String, String>?): Pair<String, String>? {
        val key = pickBestKey(files)
        if (key == null) return null
        return pickSourceByKey(files, key)
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
        initialKind: String,
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
                    val detected = downloadHls(currentUrl, hlsTemp, key, videoId, ownerId, title)
                    ext = if (detected == "ts") "ts" else "mp4"
                    finalizeTempFile(hlsTemp, key, ext)
                } else {
                    downloadWithResume(currentUrl, tempFile, key, videoId, ownerId, title)
                    ext = "mp4"
                    finalizeTempFile(tempFile, key, ext)
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
                AppLog.i(TAG, "Clip video key=$key downloaded as .$ext (attempt $attempt)")
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
     * §37.12 #329 Risk #5: ре-феч clip по (ownerId, videoId, accessKey).
     *
     * Использует `VKApiClient.videoGetClipById` (video.get с extended=1), а НЕ
     * `shortVideoGet` — т.к. `shortVideoGet` не принимает accessKey, а clips
     * часто приватные/ограниченные и требуют accessKey для повторного запроса
     * (см. RESEARCH-1 §1720 — Blocker).
     *
     * DL-C: возвращает (url, kind) через [pickClipSource] или null (если clip
     * удалён/недоступен на стороне VK).
     */
    private suspend fun refreshClipUrl(ownerId: Long, videoId: Long, accessKey: String?): Pair<String, String>? {
        return try {
            val app = SovaApp.get()
            val video = app.apiClient.videoGetClipById(ownerId, videoId, accessKey)
            if (video != null) {
                val src = pickClipSource(video.files)
                // DL-EXPLICIT: явный if вместо `video.files?.size ?: 0` в логе.
                val filesCount = if (video.files != null) video.files.size else 0
                val accessKeyStr = if (video.accessKey != null) "yes" else "no"
                AppLog.i(TAG, "refreshClipUrl: got fresh source for clip $ownerId/$videoId: $src " +
                    "(files=$filesCount, accessKey=$accessKeyStr)")
                src
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
                fileSize = localCacheFile(key)?.length() ?: 0L,
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
