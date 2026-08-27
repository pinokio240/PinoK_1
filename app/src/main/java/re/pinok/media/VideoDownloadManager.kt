// File: media/VideoDownloadManager.kt
package re.pinok.media

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * Singleton for offline video downloads.
 *
 * Uses OkHttp for downloading (Media3 DownloadManager/CacheDataSink/DataSpec
 * were removed or had breaking API changes in Media3 1.8.0).
 *
 * Videos are saved as .mp4 files in `video_downloads/` directory.
 * VideoPlayerScreen plays local files via Uri.fromFile() when available.
 */
object VideoDownloadManager {

    private const val TAG = "VideoDownloadManager"
    private const val DOWNLOAD_DIR = "video_downloads"

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context
    private lateinit var downloadDir: File
    private lateinit var httpClient: OkHttpClient
    private val activeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    // #DOCFILE-SD (P2): SAF tree URI для SD-карты (non-primary volume).
    // Когда user выбирает SD-карту через OpenDocumentTree, путь в prefs —
    // content://com.android.externalstorage.documents/tree/XXXX-XXXX%3AMovies
    // (без "primary:" prefix). Этот URI нельзя конвертировать в File path —
    // нужен DocumentFile API. downloadDir остаётся internal (рабочая директория
    // для .tmp), а финальный .mp4 КОПИРУЕТСЯ на SD-карту после загрузки.
    // См. [DocumentFileStorage.copyFileToTree]. Аналогично TrackDownloadManager.
    @Volatile
    private var documentFileTreeUri: android.net.Uri? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    /**
     * Fix #60: строковый ключ видеоскачивания — БЕЗ переполнения.
     *
     * Раньше: `ownerId * 1_000_000_000L + videoId` (Long). VK video IDs достигают
     * 2.1*10^9, поэтому при videoId >= 10^9 разные (ownerId, videoId) пары давали
     * ОДИНАКОВЫЙ packedKey → `getLocalFile` возвращал файл ДРУГОГО видео →
     * «кэш не воспроизводится» / неиграющее видео.
     *
     * Теперь: строка "ownerId_videoId" — гарантированно уникальна.
     */
    fun videoKey(ownerId: Long, videoId: Long): String = "${ownerId}_${videoId}"

    /**
     * #75: Сменить директорию скачивания видео. Переносит файлы.
     *
     * Fix #119: Нормализация пути + canWrite fallback (как в TrackDownloadManager).
     * Раньше путь `/Movies/PinoK/` интерпретировался как root-filesystem — mkdirs
     * молча fail, файлы писались в недоступное место. Теперь relative-пути
     * строятся от external storage, SAF tree URI декодируется, а при неудаче —
     * fallback на internal.
     */
    fun reconfigurePath(newPath: String) {
        if (!initialized) return

        // #DOCFILE-SD (P2): если путь — SAF tree URI для non-primary volume
        // (SD-карта XXXX-XXXX:..., USB OTG, другой volume) — НЕ пытаемся
        // конвертировать в File. downloadDir остаётся internal (рабочая
        // директория для .tmp), а финальный .mp4 копируется на SD-карту
        // через DocumentFile API после завершения загрузки.
        // См. [DocumentFileStorage] и аналогичную логику в TrackDownloadManager.
        if (DocumentFileStorage.isNonPrimarySafUri(newPath)) {
            val treeUri = DocumentFileStorage.parseTreeUri(newPath)
            if (treeUri != null && DocumentFileStorage.isTreeAccessible(appContext, treeUri)) {
                documentFileTreeUri = treeUri
                // downloadDir остаётся internal (рабочая директория для .tmp).
                // Если текущий downloadDir не internal — возвращаем на internal
                // (SD-карта использует DocumentFile API, File API только для temp).
                val internalWorkDir = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
                if (downloadDir.absolutePath != internalWorkDir.absolutePath) {
                    AppLog.i(TAG, "reconfigurePath: SD-карта detected — switching work dir to internal " +
                        "(final .mp4 files will be copied to SD card via DocumentFile)")
                    // Переносим существующие файлы на internal (они будут скопированы
                    // на SD-карту при следующем reconfigurePath или при новой загрузке).
                    runCatching {
                        downloadDir.listFiles()?.forEach { file ->
                            file.copyTo(File(internalWorkDir, file.name), overwrite = true)
                        }
                    }
                    downloadDir = internalWorkDir
                }
                AppLog.i(TAG, "reconfigurePath: SD-карта activated — treeUri=$treeUri, " +
                    "workDir=${downloadDir.absolutePath}")
                refreshFromDisk()
                return
            } else {
                AppLog.w(TAG, "reconfigurePath: SD-карта URI not accessible — $treeUri, falling back to internal")
                documentFileTreeUri = null
                val fallback = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
                downloadDir = fallback
                refreshFromDisk()
                return
            }
        }

        // Обычный File-based путь — сбрасываем SD-карту.
        documentFileTreeUri = null

        val newDir = resolveVideoDir(newPath)
        newDir.mkdirs()
        // Fix #123: probe-write — canWrite() ненадёжён на Scoped Storage.
        if (!newDir.exists() || !newDir.canWrite() || !probeWritable(newDir)) {
            AppLog.e(TAG, "reconfigurePath: target dir not writable: ${newDir.absolutePath} — falling back to internal")
            val fallback = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
            if (fallback.canWrite()) downloadDir = fallback
            refreshFromDisk()
            return
        }
        if (newDir.absolutePath == downloadDir.absolutePath) return
        // Fix #123: перенос в try/catch — сбой не роняет приложение.
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
     * Fix #119: Нормализует путь загрузки видео (см. TrackDownloadManager.resolveDownloadDir).
     */
    private fun resolveVideoDir(rawPath: String): File {
        if (rawPath.isBlank()) return File(appContext.filesDir, DOWNLOAD_DIR)
        val path = rawPath.trim().trimEnd('/')
        if (path.startsWith("/storage/") || path.startsWith("/data/") ||
            path.startsWith("/sdcard/") || path.startsWith("/mnt/")) {
            return File(path)
        }
        // #SAF-PERSIST: парсим полный content:// URI (как в TrackDownloadManager).
        // OpenDocumentTree возвращает content://com.android.externalstorage.documents/tree/primary%3AMovies%2FPinoK
        // primary: → primary external storage (/storage/emulated/0).
        val treePart: String? = when {
            path.startsWith("content://") -> {
                val treeIdx = path.indexOf("/tree/")
                if (treeIdx < 0) null
                else {
                    val raw = path.substring(treeIdx + 6)
                    runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
                }
            }
            path.startsWith("/tree/") -> path.removePrefix("/tree/")
            else -> null
        }
        if (treePart != null && treePart.startsWith("primary:")) {
            val sub = treePart.removePrefix("primary:").removePrefix("/")
            val candidate = if (sub.isBlank()) Environment.getExternalStorageDirectory()
                            else File(Environment.getExternalStorageDirectory(), sub)
            AppLog.i(TAG, "resolveVideoDir: SAF tree URI → ${candidate.absolutePath}")
            return candidate
        }
        if (path.startsWith("content://")) {
            AppLog.w(TAG, "resolveVideoDir: content:// URI (non-primary volume) cannot be used as File path — falling back")
            return File(appContext.filesDir, DOWNLOAD_DIR)
        }
        val relPath = path.removePrefix("/")
        val candidate = File(Environment.getExternalStorageDirectory(), relPath)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()) {
            AppLog.w(TAG, "resolveVideoDir: MANAGE_EXTERNAL_STORAGE not granted — " +
                "external path ${candidate.absolutePath} will likely fail")
        }
        return candidate
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
        }
    }

    fun enqueueDownload(video: Video) {
        ensureInitialized()

        // VK API: files содержит прямые .mp4 URL, player — HTML-страницу плеера.
        // Для скачивания нужен только прямой URL (files), иначе скачается HTML.
        val files = video.files
        val url = if (files != null) {
            listOf("mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240")
                .firstNotNullOfOrNull { key -> files[key] }
        } else {
            null
        }

        if (url == null) {
            AppLog.w(TAG, "enqueueDownload: video #${video.id} has no URL")
            return
        }

        val key = videoKey(video.ownerId, video.id)
        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueDownload: video #${video.id} already ${existing.status}")
                return
            }

            AppLog.i(TAG, "enqueueDownload: ${video.title} ($url)")
            // #34: сохраняем ownerId + title в DownloadState — нужно для
            // OfflineManagerScreen (удаление + воспроизведение + реальный заголовок).
            updateState(key, DownloadState(
                trackId = video.id,
                status = DownloadStatus.QUEUED,
                progress = 0,
                title = video.title,
                ownerId = video.ownerId,
            ))
            // #39 C1: сразу пишем .meta sidecar — title/duration/thumb
            // переживают рестарт приложения даже если загрузка прервана.
            saveMetadata(video)
        }
        startForegroundService()

        val job = scope.launch {
            try {
                downloadFile(key, video, url)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e(TAG, "downloadFile failed for key=$key: ${t.message}", t)
                val cur = _downloads.value[key]
                if (cur != null && cur.isInProgress) {
                    updateState(key, DownloadState(
                        trackId = video.id,
                        status = DownloadStatus.FAILED,
                        progress = 0,
                        reason = t.message,
                        title = video.title,
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

    /**
     * Fix #141 (2026-08-03): Скачать видео по ПРЯМОМУ URL (без video.files).
     *
     * Применяется в [re.pinok.ui.screens.videoplayer.OkWebViewPlayer] для
     * YouTube / EXTERNAL_IFRAME / OK-fallback — где video.files пустой, а
     * прямой URL извлекается через JS-инъекцию из <video>.currentSrc в WebView.
     *
     * Ключ скачивания — тот же формат "ownerId_videoId" (для совместимости
     * с OfflineManagerScreen и getLocalFile). Если у видео нет реального
     * VK ownerId/videoId (внешний источник), используем synthetic ключ:
     *   ownerId = -2_000_000_000 (маркер «внешний источник»)
     *   videoId = abs(url.hashCode()).toLong()
     *
     * Это гарантирует что разные URL → разные файлы, а один и тот же URL
     * → один файл (идемпотентность).
     *
     * ВНИМАНИЕ: прямой URL из WebView может быть blob: или data: —
     * такие не качаем (blob требует postMessage extraction, data слишком мал).
     * Возвращаем false в этом случае — UI показывает ошибку.
     *
     * @return true если задача поставлена в очередь, false если URL невалиден.
     */
    fun enqueueUrlDownload(video: Video, directUrl: String): Boolean {
        ensureInitialized()

        val trimmed = directUrl.trim()
        if (trimmed.isEmpty()) {
            AppLog.w(TAG, "enqueueUrlDownload: empty url for video #${video.id}")
            return false
        }
        // blob:/data: нельзя скачать простым HTTP-запросом.
        if (trimmed.startsWith("blob:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)) {
            AppLog.w(TAG, "enqueueUrlDownload: unsupported scheme blob:/data: — abort (video #${video.id})")
            return false
        }

        // Synthetic ключ для внешних видео без VK ownerId/videoId.
        // Если video.id > 0 и video.ownerId != 0 — используем штатный ключ
        // (это OK-видео с реальным externalId). Иначе — synthetic.
        val useSynthetic = video.id <= 0L || video.ownerId == 0L
        val (syntheticOwner, syntheticId) = if (useSynthetic) {
            val hash = Math.abs(trimmed.hashCode().toLong())
            Pair(-2_000_000_000L, hash)
        } else {
            Pair(video.ownerId, video.id)
        }
        val key = videoKey(syntheticOwner, syntheticId)

        synchronized(this) {
            val existing = _downloads.value[key]
            if (existing != null && (existing.isCompleted || existing.isInProgress)) {
                AppLog.d(TAG, "enqueueUrlDownload: key=$key already ${existing.status}")
                return true
            }
            AppLog.i(TAG, "enqueueUrlDownload: key=$key url=$trimmed title=${video.title}")
            updateState(key, DownloadState(
                trackId = syntheticId,
                status = DownloadStatus.QUEUED,
                progress = 0,
                title = video.title.ifBlank { "Видео из WebView" },
                ownerId = syntheticOwner,
            ))
            // Сохраняем .meta с оригинальным Video (если есть thumb/duration).
            saveMetadata(video.copy(ownerId = syntheticOwner, id = syntheticId))
        }
        startForegroundService()

        val job = scope.launch {
            try {
                downloadUrl(key, syntheticId, syntheticOwner, video.title, trimmed)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e(TAG, "enqueueUrlDownload failed for key=$key: ${t.message}", t)
                val cur = _downloads.value[key]
                if (cur != null && cur.isInProgress) {
                    updateState(key, DownloadState(
                        trackId = syntheticId,
                        status = DownloadStatus.FAILED,
                        progress = 0,
                        reason = t.message,
                        title = video.title,
                        ownerId = syntheticOwner,
                    ))
                }
            } finally {
                activeJobs.remove(key)
                maybeStopForegroundService()
            }
        }
        activeJobs[key] = job
        return true
    }

    /**
     * Fix #141: проверка, скачано ли видео по прямому URL (используя тот же
     * synthetic-ключ что и enqueueUrlDownload). Нужно чтобы кнопка «Скачать»
     * в OkWebViewPlayer сразу показывала «Скачано» при перезагрузке экрана.
     */
    fun isUrlDownloaded(video: Video, directUrl: String): Boolean {
        ensureInitialized()
        val useSynthetic = video.id <= 0L || video.ownerId == 0L
        val (syntheticOwner, syntheticId) = if (useSynthetic) {
            val hash = Math.abs(directUrl.hashCode().toLong())
            Pair(-2_000_000_000L, hash)
        } else {
            Pair(video.ownerId, video.id)
        }
        val key = videoKey(syntheticOwner, syntheticId)
        val state = _downloads.value[key]
        return state != null && state.isCompleted
    }

    /**
     * Fix #141: получить состояние скачивания для прямого URL (для UI-кнопки).
     */
    fun getUrlDownloadState(video: Video, directUrl: String): DownloadState? {
        ensureInitialized()
        val useSynthetic = video.id <= 0L || video.ownerId == 0L
        val (syntheticOwner, syntheticId) = if (useSynthetic) {
            val hash = Math.abs(directUrl.hashCode().toLong())
            Pair(-2_000_000_000L, hash)
        } else {
            Pair(video.ownerId, video.id)
        }
        val key = videoKey(syntheticOwner, syntheticId)
        return _downloads.value[key]
    }

    fun removeDownload(ownerId: Long, videoId: Long) {
        ensureInitialized()
        val key = videoKey(ownerId, videoId)
        AppLog.i(TAG, "removeDownload: owner=$ownerId id=$videoId (key=$key)")

        activeJobs[key]?.cancel()
        activeJobs.remove(key)

        // #39 C1+C3: удаляем .mp4, .mp4.tmp (partial) и .meta sidecar.
        File(downloadDir, "$key.mp4").delete()
        File(downloadDir, "$key.mp4.tmp").delete()
        deleteMetadata(key)

        // #DOCFILE-SD: удалить .mp4 с SD-карты если активирован DocumentFile tree.
        // У видео только numeric name ($key.mp4) — красивого имени нет (в отличие
        // от треков, где .meta хранит pretty filename). .meta sidecar на SD-карту
        // НЕ копируется (он только для internal playback pipeline).
        documentFileTreeUri?.let { treeUri ->
            runCatching {
                val name = "$key.mp4"
                if (DocumentFileStorage.deleteFileFromTree(appContext, treeUri, name)) {
                    AppLog.i(TAG, "removeDownload: deleted '$name' from SD card")
                }
            }.onFailure { e ->
                AppLog.w(TAG, "removeDownload: SD card cleanup failed for key=$key — ${e.message}")
            }
        }

        removeState(key)
        maybeStopForegroundService()
    }

    /**
     * #DOCFILE-SD (P2): Очистить ВСЕ скачанные видео (internal + SD-карта).
     *
     * Используется в SettingsScreen.VideoDownloadsCard → «Удалить все».
     * Раньше UI напрямую дёргал `dir.listFiles()?.forEach { it.delete() }` —
     * это чистило только internal, а SD-карта (если активирована) оставалась
     * с «зомби»-файлами. Теперь чистим через менеджер: и internal, и DocumentFile tree.
     *
     * @return количество удалённых файлов (internal + SD), или -1 при ошибке.
     */
    fun clearAllDownloads(): Int {
        ensureInitialized()
        AppLog.i(TAG, "clearAllDownloads: starting (workDir=${downloadDir.absolutePath})")
        var deletedInternal = 0
        // 1. Internal: удаляем .mp4, .mp4.tmp, .meta (всё что связано с загрузками).
        runCatching {
            downloadDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".mp4") ||
                        file.name.endsWith(".mp4.tmp") || file.name.endsWith(".meta"))) {
                    if (file.delete()) deletedInternal++
                }
            }
        }.onFailure { e ->
            AppLog.w(TAG, "clearAllDownloads: internal cleanup failed — ${e.message}")
        }
        AppLog.i(TAG, "clearAllDownloads: deleted $deletedInternal files in ${downloadDir.absolutePath}")

        // 2. SD-карта: очищаем DocumentFile tree если активирован.
        var deletedSd = 0
        documentFileTreeUri?.let { treeUri ->
            runCatching {
                deletedSd = DocumentFileStorage.clearTree(appContext, treeUri)
                AppLog.i(TAG, "clearAllDownloads: deleted $deletedSd files from SD card (DocumentFile tree)")
            }.onFailure { e ->
                AppLog.w(TAG, "clearAllDownloads: SD card cleanup failed — ${e.message}")
            }
        }

        // 3. Сбросить состояние загрузок (один bulk-апдейт → одна recomposition).
        _downloads.update { emptyMap() }

        // 4. Остановить foreground-сервис если больше нет активных загрузок.
        maybeStopForegroundService()

        val total = deletedInternal + deletedSd
        AppLog.i(TAG, "clearAllDownloads: DONE — total $total files deleted (internal=$deletedInternal, SD=$deletedSd)")
        return total
    }

    fun getDownloadState(ownerId: Long, videoId: Long): DownloadState? {
        val key = videoKey(ownerId, videoId)
        return _downloads.value[key]
    }

    fun isDownloaded(ownerId: Long, videoId: Long): Boolean {
        ensureInitialized()
        val key = videoKey(ownerId, videoId)
        return _downloads.value[key]?.isCompleted == true
    }

    fun getLocalFile(ownerId: Long, videoId: Long): File? {
        ensureInitialized()
        val key = videoKey(ownerId, videoId)
        val file = File(downloadDir, "$key.mp4")
        return if (file.exists()) file else null
    }

    /**
     * #VIDEO-PATH: возвращает текущую директорию скачивания видео.
     * Используется в SettingsScreen.VideoDownloadsCard для отображения
     * реального пути (а не захардкоженного filesDir/video_downloads).
     */
    fun getDownloadDir(): File {
        ensureInitialized()
        return downloadDir
    }

    /**
     * #VIDEO-PATH: суммарный размер всех скачанных видео-файлов + количество.
     * Возвращает Pair(bytes, count). Используется в VideoDownloadsCard.
     *
     * #DOCFILE-SD: считает ТОЛЬКО internal копии. SD-карта (если активирована)
     * считается отдельно через [getSdCardStats]. В UI показываем обе строки.
     */
    fun getStorageStats(): Pair<Long, Int> {
        ensureInitialized()
        val dir = downloadDir
        if (!dir.exists()) return Pair(0L, 0)
        val files = dir.listFiles() ?: emptyArray()
        var total = 0L
        var count = 0
        for (f in files) {
            if (f.isFile && f.name.endsWith(".mp4")) {
                total += f.length()
                count++
            }
        }
        return Pair(total, count)
    }

    /**
     * #DOCFILE-SD (P2): активирована ли SD-карта (DocumentFile tree URI).
     * Используется в SettingsScreen.VideoDownloadsCard для показа отдельной
     * строки со статистикой SD-карты.
     */
    fun isSdCardActive(): Boolean = documentFileTreeUri != null

    /**
     * #DOCFILE-SD (P2): статистика файлов на SD-карте (bytes, count).
     * Возвращает Pair(0L, 0) если SD-карта не активирована или недоступна.
     */
    fun getSdCardStats(): Pair<Long, Int> {
        val treeUri = documentFileTreeUri ?: return Pair(0L, 0)
        return try {
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(appContext, treeUri)
                ?: return Pair(0L, 0)
            var total = 0L
            var count = 0
            for (f in tree.listFiles()) {
                if (f.isFile && f.name?.endsWith(".mp4") == true) {
                    total += f.length()
                    count++
                }
            }
            Pair(total, count)
        } catch (e: Exception) {
            AppLog.w(TAG, "getSdCardStats: failed — ${e.message}")
            Pair(0L, 0)
        }
    }

    // --- Internal ---

    private fun ensureInitialized() {
        check(initialized) { "VideoDownloadManager not initialized. Call init(context) in SovaApp.onCreate." }
    }

    // #LOGCAT-NOISE-FIX: throttle для chunk-level progress (downloadVideoDirect).
    @Volatile
    private var lastProgressTs: Long = 0L

    private fun updateState(key: String, state: DownloadState) {
        // #RACE-FIX (2026-08-03): atomic update.
        _downloads.update { current -> current + (key to state) }
    }

    private fun removeState(key: String) {
        // #RACE-FIX: atomic update.
        _downloads.update { current -> current - key }
    }

    // ─── Foreground Service ─────────────────────────────────────────

    /**
     * Запускает VideoDownloadService как foreground — обязательно на Android 8+,
     * иначе система убивает фоновый процесс загрузки.
     */
    private fun startForegroundService() {
        try {
            VideoDownloadService.start(appContext)
        } catch (e: Exception) {
            AppLog.w(TAG, "startForegroundService failed", e)
        }
    }

    /**
     * Если активных загрузок нет — останавливаем foreground-сервис.
     */
    private fun maybeStopForegroundService() {
        val active = _downloads.value.values.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        if (active == 0) {
            try {
                VideoDownloadService.stop(appContext)
            } catch (e: Exception) {
                AppLog.w(TAG, "maybeStopForegroundService failed", e)
            }
        }
    }

    /**
     * #39 C3: Скачать видео с retry (3 попытки, backoff 1s/3s/9s) + Range-resume.
     *
     * VK CDN на мобильных сетях часто обрывает соединение на больших файлах.
     * Раньше обрыв → FAILED + ручной перезапуск. Теперь:
     *  — tempFile НЕ удаляется между попытками → Range: bytes=<size>- возобновляет.
     *  — Сервер без Range-поддержки (200 вместо 206) → стартуем с нуля.
     *  — tempFile сохраняется даже при финальной неудаче → resume при следующем enqueue.
     */
    private suspend fun downloadFile(
        key: String,
        video: Video,
        url: String,
    ) {
        downloadUrl(key, video.id, video.ownerId, video.title, url)
        // #39 C1: обновляем .meta после успешной загрузки (на случай,
        // если title/duration изменились между enqueue и завершением).
        saveMetadata(video)
    }

    /**
     * Fix #141 (2026-08-03): Обобщённая загрузка видео по прямому URL.
     *
     * Используется как [downloadFile] (для VK-видео с known ownerId/videoId),
     * так и [enqueueUrlDownload] (для внешних видео из WebView — synthetic ключ).
     *
     * Реализация идентична старому downloadFile: retry×3 + Range-resume +
     * atomic tempFile→targetFile rename. Разница только в параметрах: вместо
     * целого Video передаём распакованные (videoId, ownerId, title, url).
     */
    private suspend fun downloadUrl(
        key: String,
        videoId: Long,
        ownerId: Long,
        title: String,
        url: String,
    ) {
        updateState(key, DownloadState(
            trackId = videoId,
            status = DownloadStatus.DOWNLOADING,
            progress = 0,
            title = title,
            ownerId = ownerId,
        ))

        val tempFile = File(downloadDir, "$key.mp4.tmp")
        val targetFile = File(downloadDir, "$key.mp4")

        val maxRetries = 3
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadWithResume(url, tempFile, key, videoId, ownerId, title)
                // Атомарная замена (renameTo может молча провалиться на разных
                // файловых системах — используем copyTo + delete как fallback).
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                // #DOCFILE-SD (P2): копируем финальный .mp4 на SD-карту если
                // активирован DocumentFile tree URI (non-primary volume).
                // Internal копия остаётся для быстрого воспроизведения
                // (VideoPlayerScreen → Uri.fromFile, без latency на copy-from-SD).
                copyToSdCardIfNeeded(key, targetFile)
                updateState(key, DownloadState(
                    trackId = videoId,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = title,
                    ownerId = ownerId,
                ))
                AppLog.i(TAG, "Video key=$key downloaded (attempt $attempt)")
                return
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                // НЕ удаляем tempFile — он позволит Range-resume на следующей попытке.
                if (attempt >= maxRetries) {
                    AppLog.e(TAG, "downloadUrl: $attempt attempts exhausted for key=$key: ${e.message}", e)
                    throw e
                }
                val backoffMs = (1000L * Math.pow(3.0, (attempt - 1).toDouble())).toLong()
                AppLog.w(TAG, "downloadUrl attempt $attempt failed: ${e.message}, retry in ${backoffMs}ms")
                delay(backoffMs)
            }
        }
    }

    /**
     * #39 C3: HTTP-загрузка с поддержкой Range-resume.
     *
     * Если tempFile уже существует (частичная загрузка), отправляем
     * `Range: bytes=<size>-` и дописываем (HTTP 206 Partial Content).
     * Если сервер вернул 200 (Range не поддерживается) — стартуем с нуля.
     */
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
        val request = requestBuilder.build()  // UA добавляется interceptor'ом в init()

        httpClient.newCall(request).execute().use { response ->
            val isPartial = response.code == 206
            if (!response.isSuccessful && response.code != 206) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            // Сервер проигнорировал Range (200 OK) — начинаем с нуля.
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
                            // #LOGCAT-NOISE-FIX + #RECOMPOSE-FIX (2026-08-03):
                            // Раньше updateState вызывался на КАЖДОМ 64KB chunk →
                            // ~hundreds of updates/sec → logcat spam + recompose storm.
                            // Теперь: throttle — обновляем StateFlow не чаще чем
                            // каждые 500ms ИЛИ при изменении прогресса на >=2%.
                            val nowMs = System.currentTimeMillis()
                            val lastProg = _downloads.value[key]?.progress ?: -1
                            val delta = progress - lastProg
                            val enoughTime = nowMs - lastProgressTs >= 500L
                            // Throttle: update StateFlow only if progress changed by
                            // >=2% OR 500ms passed since last update. Avoids logcat
                            // spam + recompose storm from per-64KB-chunk updates.
                            if (progress != lastProg && (delta >= 2 || delta <= -2 || enoughTime)) {
                                lastProgressTs = nowMs
                                updateState(key, DownloadState(
                                    trackId = videoId,
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = progress,
                                    title = title,
                                    ownerId = ownerId,
                                ))
                            }
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
                // Fix #60: новый формат "ownerId_videoId.mp4".
                // Старые файлы (чистое число) не имеют '_' — пропускаем как orphaned.
                val name = file.nameWithoutExtension
                val underscore = name.indexOf('_')
                if (underscore <= 0) return@forEach
                val ownerIdStr = name.substring(0, underscore)
                val videoIdStr = name.substring(underscore + 1)
                val ownerId = ownerIdStr.toLongOrNull() ?: return@forEach
                val videoId = videoIdStr.toLongOrNull() ?: return@forEach
                // #39 C1: загружаем title из sidecar .meta файла. Раньше после
                // рестарта все видео показывались как «Видео #ID» — metadata
                // терялась. Теперь .meta пишется в enqueueDownload и после
                // успешной загрузки (см. saveMetadata).
                val meta = loadMetadata(name)
                map[name] = DownloadState(
                    trackId = videoId,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = meta?.title ?: "",
                    ownerId = ownerId,
                )
            }
        }
        _downloads.value = map
        AppLog.i(TAG, "Loaded ${map.size} existing video downloads from disk")
    }

    // ─── Metadata persistence (#39 C1) ──────────────────────────────

    /** Sidecar-метаданные видео, переживают рестарт приложения. */
    private data class VideoMeta(
        val title: String,
        val ownerId: Long,
        val videoId: Long,
        val duration: Int = 0,
        val thumbUrl: String? = null,
    )

    /** Записать .meta sidecar (JSON). Вызывается в enqueueDownload + после COMPLETED. */
    private fun saveMetadata(video: Video) {
        try {
            val key = videoKey(video.ownerId, video.id)
            val metaFile = File(downloadDir, "$key.meta")
            val json = com.google.gson.Gson().toJson(
                VideoMeta(
                    title = video.title,
                    ownerId = video.ownerId,
                    videoId = video.id,
                    duration = video.duration,
                    thumbUrl = video.thumbUrl,
                )
            )
            metaFile.writeText(json)
        } catch (e: Exception) {
            AppLog.w(TAG, "saveMetadata failed for #${video.id}: ${e.message}")
        }
    }

    /** Прочитать .meta sidecar. null если файл отсутствует/повреждён. */
    private fun loadMetadata(key: String): VideoMeta? {
        return try {
            val metaFile = File(downloadDir, "$key.meta")
            if (!metaFile.exists()) return null
            val json = metaFile.readText()
            com.google.gson.Gson().fromJson(json, VideoMeta::class.java)
        } catch (_: Exception) { null }
    }

    /** Удалить .meta sidecar. */
    private fun deleteMetadata(key: String) {
        try {
            val metaFile = File(downloadDir, "$key.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (_: Exception) {}
    }

    /**
     * #DOCFILE-SD (P2): Копирует финальный .mp4 на SD-карту через DocumentFile API.
     *
     * Вызывается после завершения загрузки (downloadUrl, после rename tempFile →
     * targetFile). Если [documentFileTreeUri] не установлен (обычный File-based
     * path) — no-op.
     *
     * Имя файла на SD-карте = `targetFile.name` (= "$key.mp4"). У видео нет
     * красивого имени (как у треков с pretty filename из .meta) — numeric key
     * используется и для internal, и для SD-карты.
     *
     * @param key строковый ключ "ownerId_videoId" (для логирования)
     * @param sourceFile internal File (downloadDir/$key.mp4)
     */
    private fun copyToSdCardIfNeeded(key: String, sourceFile: java.io.File) {
        val treeUri = documentFileTreeUri ?: return  // SD-карта не активирована — no-op
        val targetName = sourceFile.name  // "$key.mp4"
        runCatching {
            val ok = DocumentFileStorage.copyFileToTree(appContext, treeUri, sourceFile, targetName)
            if (ok) {
                AppLog.i(TAG, "copyToSdCardIfNeeded: key=$key → SD card '$targetName' " +
                    "(${sourceFile.length() / 1024} KB) OK")
            } else {
                AppLog.w(TAG, "copyToSdCardIfNeeded: key=$key → SD card '$targetName' FAILED — " +
                    "internal copy remains usable")
            }
        }.onFailure { e ->
            AppLog.e(TAG, "copyToSdCardIfNeeded: key=$key SD card copy failed — ${e.message}", e)
            // НЕ бросаем — internal копия остаётся, пользователь может смотреть.
        }
    }
}