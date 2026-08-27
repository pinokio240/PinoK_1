// File: media/TrackDownloadManager.kt
package re.pinok.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.SovaApp
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.FailReason
import re.pinok.data.model.Track
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Singleton-обёртка для офлайн-скачивания треков.
 *
 * Аналог `OfflineMusicDownloadService` + обвязки из SOVA V RE.
 * Использует OkHttp вместо Media3 DownloadManager (который был удалён в Media3 1.8.0).
 *
 * Что делает:
 *  — Держит каталог `downloads/music/` во internal storage.
 *  — Хранит таблицу [DownloadState] по trackId и отдаёт её как Flow.
 *  — Принимает команды: [enqueueDownload], [removeDownload], [getDownloadState].
 *  — Загружает через OkHttp в фоне с прогрессом.
 *
 * Fix #73: VK audio — 100% HLS (.m3u8?siren=1). Раньше скачивался
 * текстовый m3u8-плейлист вместо реального аудио.
 * Теперь: парсит m3u8, скачивает все .ts-сегменты, склеивает в один файл.
 * Для прямых MP3-URL — скачивает как раньше (backward compat).
 */
object TrackDownloadManager {

    private const val TAG = "TrackDownloadManager"
    private const val DOWNLOAD_DIR = "downloads/music"

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context
    private lateinit var downloadDir: File
    private lateinit var httpClient: OkHttpClient

    // #DOCFILE-SD (P2): SAF tree URI для SD-карты (non-primary volume).
    // Когда user выбирает SD-карту через OpenDocumentTree, путь в prefs —
    // content://com.android.externalstorage.documents/tree/XXXX-XXXX%3AMusic
    // (без "primary:" prefix). Этот URI нельзя конвертировать в File path —
    // нужен DocumentFile API. downloadDir остаётся internal (рабочая директория
    // для .tmp/segments), а финальный файл КОПИРУЕТСЯ на SD-карту после загрузки.
    // См. [DocumentFileStorage.copyFileToTree].
    @Volatile
    private var documentFileTreeUri: android.net.Uri? = null
    private val activeJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Fix #265: Download Queue (sequential, one track at a time) ───────────
    // КОНТЕКСТ: Раньше enqueueDownload сразу запускал scope.launch → при
    // нескольких вызовах (auto-cache + manual download + precache) стартовало
    // N параллельных HLS-загрузок. Каждая HLS-загрузка внутри себя ещё запускает
    // до 4 параллельных segment-загрузок → 4×N коннектов + 4×N AES-decrypt +
    // 4×N file-write одновременно → "ломает загруженные файлы":
    //   - перекрёстная запись в один .ts.tmp (Race Condition)
    //   - ENOSPC при больших плейлистах
    //   - AES-decrypt на main thread → ANR
    //   - повреждённые сегменты (неполные .ts файлы)
    //
    // РЕШЕНИЕ: настоящая FIFO-очередь на Channel + единственный worker.
    //   enqueueDownload() → кладёт Track в pendingQueue + signal
    //   queueWorker (один!) → берёт следующий Track → downloadTrack() →
    //   → по завершении (success/fail/cancel) берёт следующий.
    // Гарантия: в любой момент времени активна НЕ БОЛЕЕ ОДНОЙ загрузки трека.
    //
    // #VK-MUSIC-SAVER-PORT: элемент очереди — DownloadRequest, а не голый Track.
    // Для скачивания плейлиста к треку прикладываются subDir (папка плейлиста
    // внутри downloads/music/), index (номер трека, 1-based) и total (размер
    // плейлиста) — чтобы файлы легли в папку с именами "01. Artist - Title.m4a".
    private data class DownloadRequest(
        val track: Track,
        val subDir: String? = null,
        val index: Int? = null,
        val total: Int? = null,
    )

    private val pendingQueue = ConcurrentLinkedQueue<DownloadRequest>()
    private val queueSignal = Channel<Unit>(Channel.CONFLATED)  // неблокирующий signal
    @Volatile
    private var queueWorkerStarted = false
    private val queueLock = Any()  // guard для startQueueWorkerIfNeeded()

    // Fix #249: debounce-Handler для отложенного stopService().
    // Без этого получаем ForegroundServiceDidNotStartInTimeException:
    // enqueueDownload → startForegroundService() → корутина мгновенно
    // завершается (трек уже в кэше / быстрая ошибка) → finally блок
    // вызывает maybeStopForegroundService() → stopService() — всё это
    // за 4мс. Система видит «fast start-stop» и кидает ANR-исключение
    // даже если startForeground() БЫЛ вызван в onCreate. Отложив
    // stopService на 1500мс, даём сервису время закрепиться в
    // foreground state; если за это время появилась новая загрузка —
    // отменяем stopService вообще (см. startForegroundService()).
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopServiceRunnable = Runnable {
        try {
            MusicDownloadService.stop(appContext)
        } catch (e: Exception) {
            AppLog.w(TAG, "debounced stopService failed: ${e.message}")
        }
    }

    private val _downloads = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Long, DownloadState>> = _downloads.asStateFlow()

    // Fix #147: path mismatch detection.
    // Если сохранённый в prefs musicDownloadPath не может быть применён
    // (директория не существует / не writable / SD card unmounted / SAF
    // permission revoked) — reconfigurePath молча fallback'ит на internal
    // storage. Пользователь не знает, что файлы пишутся не туда куда он
    // выбрал. Этот flow=true когда есть mismatch — UI показывает banner.
    data class PathMismatchInfo(
        val savedPath: String,        // что пользователь выбрал в prefs
        val effectivePath: String,    // куда реально пишем (fallback)
        val reason: String,           // почему не смогли применить savedPath
    )
    private val _pathMismatch = MutableStateFlow<PathMismatchInfo?>(null)
    val pathMismatch: StateFlow<PathMismatchInfo?> = _pathMismatch.asStateFlow()

    /** Текущая эффективная директория загрузок (для UI). */
    val effectiveDownloadDir: File
        get() = if (initialized) downloadDir else File(appContext.filesDir, DOWNLOAD_DIR)

    /**
     * Fix #142: throttle для updateProgress. Timestamp последнего обновления
     * notification (ms). updateState проверяет что прошло ≥ NOTIFY_THROTTLE_MS
     * с последнего вызова, иначе пропускает nm.notify. Финальные события
     * (isCompleted / activeCount==0) проходят без throttle.
     */
    @Volatile
    private var lastNotifyTs: Long = 0L

    // Fix #147 (build error): `companion object` is not allowed inside a
    // standalone `object` declaration in Kotlin. Moved the constant to the
    // top level of the object body — constants are permitted directly.
    private const val NOTIFY_THROTTLE_MS = 500L

    /**
     * #75: Сменить директорию скачивания. Переносит файлы в новое место.
     *
     * Fix #78 (ENOENT): проверка `canWrite()` после `mkdirs()`. На Scoped Storage
     * (Android 11+) путь вида `/Music/PinoK` молча НЕ создаётся — `mkdirs()` возвращает
     * false, и все последующие `FileOutputStream` падают с ENOENT на каждом сегменте.
     * Если целевая директория недоступна для записи — откатываемся на internal storage
     * и пишем ошибку в лог.
     *
     * Fix #119: Нормализация пути. Дефолтный путь в prefs — `/Music/PinoK/` (относительный
     * к external storage). Раньше `File("/Music/PinoK")` интерпретировался как путь в
     * **root filesystem** (`/Music/PinoK`), который недоступен для записи → всегда fallback
     * на internal. Теперь относительные пути вида `/Music/...`, `/Movies/...` и т.п.
     * строятся от `Environment.getExternalStorageDirectory()` → `/storage/emulated/0/Music/PinoK`.
     * Также проверяем `Environment.isExternalStorageManager()` на Android 11+: если
     * MANAGE_EXTERNAL_STORAGE не предоставлено — external-путь недоступен для записи.
     */
    fun reconfigurePath(newPath: String) {
        if (!initialized) return

        // #DOCFILE-SD (P2): если путь — SAF tree URI для non-primary volume
        // (SD-карта), НЕ пытаемся конвертировать в File. downloadDir остаётся
        // internal (рабочая директория), а финальный файл копируется на SD-карту
        // через DocumentFile API после завершения загрузки.
        if (DocumentFileStorage.isNonPrimarySafUri(newPath)) {
            val treeUri = DocumentFileStorage.parseTreeUri(newPath)
            if (treeUri != null && DocumentFileStorage.isTreeAccessible(appContext, treeUri)) {
                documentFileTreeUri = treeUri
                // downloadDir остаётся internal (рабочая директория для .tmp/segments).
                // Если текущий downloadDir не internal — возвращаем на internal
                // (SD-карта использует DocumentFile API, File API только для temp).
                val internalWorkDir = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
                if (downloadDir.absolutePath != internalWorkDir.absolutePath) {
                    AppLog.i(TAG, "reconfigurePath: SD-карта detected — switching work dir to internal " +
                        "(final files will be copied to SD card via DocumentFile)")
                    // Переносим существующие файлы на internal (они будут скопированы
                    // на SD-карту при следующем reconfigurePath или при новой загрузке).
                    runCatching {
                        downloadDir.listFiles()?.forEach { file ->
                            file.copyTo(File(internalWorkDir, file.name), overwrite = true)
                        }
                    }
                    downloadDir = internalWorkDir
                }
                _pathMismatch.value = null
                AppLog.i(TAG, "reconfigurePath: SD-карта activated — treeUri=$treeUri, " +
                    "workDir=${downloadDir.absolutePath}")
                refreshFromDisk()
                return
            } else {
                AppLog.w(TAG, "reconfigurePath: SD-карта URI not accessible — $treeUri, falling back to internal")
                documentFileTreeUri = null
                val fallback = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
                downloadDir = fallback
                _pathMismatch.value = PathMismatchInfo(
                    savedPath = newPath,
                    effectivePath = downloadDir.absolutePath,
                    reason = "SD-карта недоступна. Файлы сохраняются во внутреннюю память.",
                )
                refreshFromDisk()
                return
            }
        }

        // Обычный File-based путь — сбрасываем SD-карту.
        documentFileTreeUri = null

        val newDir = resolveDownloadDir(newPath)
        newDir.mkdirs()
        // Fix #123: Scoped Storage guard. canWrite() ненадёжён на Android 11+
        // (возвращает true для /storage/emulated/0/Music, но реальная запись EPERM).
        // Делаем probe-запись: создаём временный файл и удаляем. Только потом переносим.
        if (!newDir.exists() || !newDir.canWrite() || !probeWritable(newDir)) {
            AppLog.e(TAG, "reconfigurePath: target dir not writable: ${newDir.absolutePath} — falling back to internal")
            val fallback = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
            if (fallback.canWrite()) {
                downloadDir = fallback
                AppLog.i(TAG, "reconfigurePath: using fallback internal dir=${fallback.absolutePath}")
            }
            // Fix #147: устанавливаем mismatch — UI покажет banner.
            _pathMismatch.value = PathMismatchInfo(
                savedPath = newPath.ifBlank { "(default)" },
                effectivePath = downloadDir.absolutePath,
                reason = "Папка недоступна или нет прав на запись",
            )
            refreshFromDisk()
            return
        }
        // Fix #147: путь применился успешно — чистим mismatch.
        _pathMismatch.value = null
        if (newDir.absolutePath == downloadDir.absolutePath) return
        // Fix #123: Перенос файлов в try/catch. Любой сбой записи (EPERM, ENOSPC, EACCES)
        // НЕ должен ронять приложение — оставляем файлы в текущей директории и логируем.
        val previousDir = downloadDir
        try {
            downloadDir.listFiles()?.forEach { file ->
                file.copyTo(File(newDir, file.name), overwrite = true)
                file.delete()
            }
            downloadDir = newDir
            AppLog.i(TAG, "reconfigurePath: downloadDir=${downloadDir.absolutePath}")
        } catch (e: Exception) {
            // Откат: оставляем downloadDir прежним, файлы не потеряны.
            AppLog.e(TAG, "reconfigurePath: copy failed (${e.javaClass.simpleName}: ${e.message}) — keeping previous dir=${previousDir.absolutePath}", e)
            downloadDir = previousDir
            // Fix #147: copy failed — устанавливаем mismatch с причиной.
            _pathMismatch.value = PathMismatchInfo(
                savedPath = newPath,
                effectivePath = previousDir.absolutePath,
                reason = "Не удалось переместить файлы: ${e.message ?: e.javaClass.simpleName}",
            )
            // Частично скопированные файлы в newDir — удаляем мусор.
            runCatching {
                downloadDir.listFiles()?.forEach { src ->
                    val dst = File(newDir, src.name)
                    if (dst.exists() && !src.exists()) {
                        // уже удалён из src, но dst мог быть скопирован до сбоя — оставляем
                    } else if (dst.exists() && src.exists()) {
                        dst.delete()
                    }
                }
            }
        }
        refreshFromDisk()
    }

    /**
     * Fix #147: Проверить соответствие сохранённого prefs-пути текущему downloadDir.
     * Вызывается из OfflineManagerScreen при открытии — если путь в prefs не пустой,
     * но effectiveDownloadDir отличается от resolveDownloadDir(savedPath) —
     * показываем banner. Полезно когда SD card отмонтирована или SAF permission
     * revoked после перезапуска приложения.
     */
    fun checkPathMismatch(savedPath: String) {
        if (!initialized) return
        if (savedPath.isBlank()) {
            _pathMismatch.value = null
            return
        }
        val expected = resolveDownloadDir(savedPath)
        if (expected.absolutePath == downloadDir.absolutePath) {
            _pathMismatch.value = null
            return
        }
        // Проверим writable — если да, можно применить.
        if (expected.exists() && expected.canWrite() && probeWritable(expected)) {
            // Путь валиден, но не применён — применим.
            AppLog.i(TAG, "checkPathMismatch: saved path is valid, applying ${expected.absolutePath}")
            reconfigurePath(savedPath)
            return
        }
        // Mismatch — UI покажет banner.
        _pathMismatch.value = PathMismatchInfo(
            savedPath = savedPath,
            effectivePath = downloadDir.absolutePath,
            reason = "Сохранённая папка недоступна (SD карта отмонтирована или права отозваны)",
        )
    }

    /**
     * Fix #123: Надёжная проверка записи в директорию на Scoped Storage.
     * canWrite() проверяет только filesystem permissions, но Android 11+ блокирует
     * запись в /storage/emulated/0/<PublicDirs> через SELinux даже если canWrite()==true.
     * Probe: создаём .pinok_probe файл, пишем байт, удаляем. Если хоть шаг падает — dir не writable.
     */
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
     * Fix #119: Нормализует путь загрузки музыки.
     *
     * Поддерживаемые форматы пути из prefs:
     * 1. `""` или blank → internal storage (`filesDir/downloads/music`).
     * 2. `/Music/PinoK/` → relative к external storage. На Android 10+ external
     *    storage это `/storage/emulated/0`, итог: `/storage/emulated/0/Music/PinoK`.
     *    Соответствует стандартной public-папке Music.
     * 3. `/storage/emulated/0/Music/PinoK` (абсолютный external-путь) → как есть.
     * 4. `/data/user/0/.../files/...` (абсолютный internal-путь) → как есть.
     *
     * Для external-путей проверяем `Environment.isExternalStorageManager()` на
     * Android 11+ (API 30+): если MANAGE_EXTERNAL_STORAGE не предоставлено,
     * external public-папки недоступны для записи через File API.
     */
    private fun resolveDownloadDir(rawPath: String): File {
        if (rawPath.isBlank()) return File(appContext.filesDir, DOWNLOAD_DIR)
        val path = rawPath.trim().trimEnd('/')

        // Абсолютный путь к external/internal — используем как есть.
        if (path.startsWith("/storage/") || path.startsWith("/data/") ||
            path.startsWith("/sdcard/") || path.startsWith("/mnt/")) {
            return File(path)
        }

        // Fix #119: SAF tree URI. OpenDocumentTree возвращает полный content:// URI:
        //   content://com.android.externalstorage.documents/tree/primary%3AMusic%2FPinoK
        // #SAF-PERSIST: раньше сохраняли только uri.path (/tree/primary:Music/PinoK),
        // теперь сохраняем полный URI (content://...). Парсим оба формата.
        // primary: → primary external storage (/storage/emulated/0).
        // Другие volume (SD card XXXX-XXXX:...) не поддерживаем через File API —
        // нужен DocumentFile (P2); fallback на internal.
        val treePart: String? = when {
            path.startsWith("content://") -> {
                // Полный SAF URI: извлекаем часть после /tree/, URL-decode.
                val treeIdx = path.indexOf("/tree/")
                if (treeIdx < 0) null
                else {
                    val raw = path.substring(treeIdx + 6)  // после "/tree/"
                    val decoded = runCatching {
                        android.net.Uri.decode(raw)
                    }.getOrDefault(raw)
                    decoded
                }
            }
            path.startsWith("/tree/") -> path.removePrefix("/tree/")  // старый формат
            else -> null
        }
        if (treePart != null && treePart.startsWith("primary:")) {
            val sub = treePart.removePrefix("primary:").removePrefix("/")
            val candidate = if (sub.isBlank()) Environment.getExternalStorageDirectory()
                            else File(Environment.getExternalStorageDirectory(), sub)
            AppLog.i(TAG, "resolveDownloadDir: SAF tree URI → ${candidate.absolutePath}")
            return candidate
        }
        // content:// URI без /tree/primary: (SD-карта, другой volume) — не конвертируется
        // в File, fallback на internal. Пользователь видит предупреждение в Settings.
        if (path.startsWith("content://")) {
            AppLog.w(TAG, "resolveDownloadDir: content:// URI (non-primary volume) cannot be used as File path — falling back to internal")
            return File(appContext.filesDir, DOWNLOAD_DIR)
        }

        // Relative-путь вида /Music/PinoK или Music/PinoK → от external storage.
        // Это фикс дефолтного "/Music/PinoK/" из SovaPrefs, который раньше
        // интерпретировался как root-filesystem path.
        val relPath = path.removePrefix("/")
        val externalRoot = Environment.getExternalStorageDirectory()  // /storage/emulated/0
        val candidate = File(externalRoot, relPath)

        // На Android 11+ (API 30+) проверяем MANAGE_EXTERNAL_STORAGE.
        // Без него external public-папки недоступны для записи через File API.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()) {
            AppLog.w(TAG, "resolveDownloadDir: MANAGE_EXTERNAL_STORAGE not granted — " +
                "external path ${candidate.absolutePath} will likely fail, " +
                "request permission in Settings or use internal storage")
        }

        return candidate
    }

    /**
     * Инициализация. Идемпотентна. Должна вызываться из SovaApp.onCreate.
     *
     * Fix #78 (ANR): убран `runBlocking` на main-thread — он блокировал UI на 5+ секунд
     * при старте приложения (DataStore читается с диска). Теперь:
     *   1. Синхронно ставим дефолтный internal-path (быстро, без I/O).
     *   2. Чтение custom path из prefs + refreshFromDisk уезжают в фон на Dispatchers.IO.
     *   3. Если custom path валиден — reconfigurePath асинхронно перекинет каталог.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext

            // Дефолтный путь сразу (без I/O) — main не блокируется.
            downloadDir = File(appContext.filesDir, DOWNLOAD_DIR).apply { mkdirs() }

            val ua = VkUserAgent.get(context.applicationContext as android.app.Application)
            httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", ua)
                        .header("Accept-Language", "ru")
                        .build()
                    chain.proceed(req)
                }
                .build()

            initialized = true
            // Fix #265: запускаем единственный queue-worker ПОСЛЕ initialized=true,
            // чтобы worker видел полностью инициализированное состояние (httpClient,
            // downloadDir, appContext). Worker живёт всё время жизни singleton'а,
            // крутится в Dispatchers.IO, блокируется на queueSignal.receive().
            startQueueWorkerIfNeeded()
            AppLog.i(TAG, "Инициализирован (default path). downloadDir=${downloadDir.absolutePath}")

            // Чтение custom-path + refreshFromDisk — в фоне.
            // Fix #123: всё тело launch в try/catch — никакая ошибка фоновой
            // инициализации (IO, prefs, EPERM) не должна ронять приложение.
            scope.launch(Dispatchers.IO) {
                try {
                    val customPath = try {
                        SovaApp.get().prefs.data.first().musicDownloadPath
                    } catch (_: Exception) { "" }
                    if (customPath.isNotBlank()) {
                        AppLog.i(TAG, "init: applying custom path from prefs → $customPath")
                        reconfigurePath(customPath)
                    } else {
                        refreshFromDisk()
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    AppLog.e(TAG, "init: background init failed — app stays on internal dir", e)
                    runCatching { refreshFromDisk() }
                }
            }
        }
    }

    // ─── API для UI ────────────────────────────────────────────────

    /**
     * Поставить трек в очередь на скачивание. Если трек уже скачан или в очереди — ничего не делает.
     *
     * Fix #265: Теперь кладёт трек в pendingQueue вместо прямого scope.launch.
     * Единственный queueWorker берёт треки по одному (sequential), гарантируя
     * что в любой момент активна максимум одна HLS-загрузка. Это устраняет
     * "поломку файлов" при параллельной загрузке нескольких треков (race
     * condition на .ts.tmp, ENOSPC, AES-decrypt ANR).
     *
     * @param silent если true — автокэширование (fix #76/77): НЕ запускает
     *   foreground-сервис уведомлений. Используется PlayerConnection для
     *   фонового предзагрузки следующего трека.
     */
    fun enqueueDownload(track: Track, silent: Boolean = false) {
        enqueueRequest(DownloadRequest(track), silent)
    }

    /**
     * #VK-MUSIC-SAVER-PORT: скачать весь плейлист в отдельную папку.
     *
     * Создаёт `downloads/music/<Playlist>/`, кладёт туда `tracklist.txt`
     * (список треков), `cover.jpg` (обложка) и все треки с именами
     * `NN. Artist - Title.m4a` (нумерация по порядку в плейлисте).
     *
     * @param playlistTitle название плейлиста (sanitize → имя папки).
     * @param coverUrl      обложка плейлиста (может быть null).
     * @param tracks        треки плейлиста (уже с url, отфильтрованные).
     */
    fun enqueuePlaylistDownload(playlistTitle: String, coverUrl: String?, tracks: List<Track>) {
        ensureInitialized()
        if (tracks.isEmpty()) {
            AppLog.w(TAG, "enqueuePlaylistDownload: empty playlist '$playlistTitle' — skip")
            return
        }
        val subDirName = FilenameBuilder.sanitize(playlistTitle).ifBlank { "playlist" }
        val subDir = File(downloadDir, subDirName)
        if (!subDir.exists() && !subDir.mkdirs()) {
            AppLog.w(TAG, "enqueuePlaylistDownload: cannot create folder ${subDir.absolutePath} — fallback to flat downloadDir")
            tracks.forEach { enqueueRequest(DownloadRequest(it), silent = false) }
            return
        }
        writeTracklist(subDir, tracks)
        downloadPlaylistCover(subDir, coverUrl)
        tracks.forEachIndexed { i, track ->
            enqueueRequest(DownloadRequest(track, subDirName, i + 1, tracks.size), silent = false)
        }
        AppLog.i(TAG, "enqueuePlaylistDownload: '$playlistTitle' → ${tracks.size} tracks into '$subDirName/'")
    }

    private fun enqueueRequest(request: DownloadRequest, silent: Boolean = false) {
        ensureInitialized()
        val track = request.track
        val url = track.url ?: run {
            AppLog.w(TAG, "enqueueDownload: track #${track.id} has no URL — skip")
            return
        }
        if (silent) {
            AppLog.i(TAG, "enqueueDownload [silent] trackId=${track.id}: ${track.artist} — ${track.title} (${url.take(80)})")
        } else {
            AppLog.i(TAG, "enqueueDownload trackId=${track.id}: ${track.artist} — ${track.title} (${url.take(80)})")
        }

        // Не запускать повторно если уже качается, в очереди или скачано
        val existing = _downloads.value[track.id]
        if (existing != null && (existing.isCompleted || existing.isInProgress)) {
            AppLog.v(TAG, "enqueueDownload: track #${track.id} already ${existing.status} — skip")
            return
        }
        // Fix #265: проверяем очередь — может уже ждёт
        if (pendingQueue.any { it.track.id == track.id }) {
            AppLog.v(TAG, "enqueueDownload: track #${track.id} already in queue — skip")
            return
        }

        updateState(track.id, DownloadState(
            trackId = track.id,
            status = DownloadStatus.QUEUED,
            progress = 0,
            title = track.title,
            artist = track.artist,
            ownerId = track.ownerId,
        ))
        // Fix #134: ВСЕ загрузки (включая silent авто-кеш) запускают foreground
        // service. Раньше silent=true НЕ запускал MusicDownloadService → на
        // Android 8+ система убивала фоновый процесс загрузки через 1-2 минуты
        // после ухода приложения в фон. Теперь даже авто-кеш держит foreground
        // notification, пока загрузка активна. maybeStopForegroundService() в
        // finally блоке queueWorker остановит сервис когда всё завершится.
        startForegroundService()

        // Fix #265: кладём в FIFO-очередь. Worker возьмёт следующий трек когда
        // закончит текущий. Сигнал — неблокирующий (CONFLATED), если worker
        // уже ждёт — получит сигнал; если занят — сигнал останется в буфере
        // и будет обработан в следующей итерации цикла.
        pendingQueue.add(request)
        queueSignal.trySend(Unit)
        AppLog.i(TAG, "queue: enqueued #${track.id}, queueSize=${pendingQueue.size}")
    }

    /** #VK-MUSIC-SAVER-PORT: записать tracklist.txt в папку плейлиста. */
    private fun writeTracklist(subDir: File, tracks: List<Track>) {
        try {
            val sb = StringBuilder()
            tracks.forEachIndexed { i, track ->
                val artist = track.artist.ifBlank { "Unknown" }
                val title = track.title.ifBlank { track.id.toString() }
                val subtitle = track.subtitle?.takeIf { it.isNotBlank() }
                    ?.let { " ($it)" } ?: ""
                sb.append("${(i + 1).toString().padStart(2, '0')}. ")
                sb.append(artist).append(" - ").append(title).append(subtitle)
                sb.append("\n")
            }
            File(subDir, "tracklist.txt").writeText(sb.toString())
            AppLog.i(TAG, "writeTracklist: ${tracks.size} tracks → ${subDir.name}/tracklist.txt")
        } catch (e: Exception) {
            AppLog.w(TAG, "writeTracklist failed: ${e.message}")
        }
    }

    /** #VK-MUSIC-SAVER-PORT: скачать обложку плейлиста в cover.jpg. */
    private fun downloadPlaylistCover(subDir: File, coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) return
        try {
            val bytes = fetchBytes(coverUrl)
            if (bytes.isNotEmpty()) {
                File(subDir, "cover.jpg").writeBytes(bytes)
                AppLog.i(TAG, "downloadPlaylistCover: ${bytes.size}B → ${subDir.name}/cover.jpg")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "downloadPlaylistCover failed: ${e.message}")
        }
    }

    /**
     * Fix #265: Запускает единственный queue-worker, если ещё не запущен.
     * Idempotent — потокобезопасный через queueLock. Worker живёт всё время
     * жизни singleton'а и крутится в Dispatchers.IO.
     */
    private fun startQueueWorkerIfNeeded() {
        if (queueWorkerStarted) return
        synchronized(queueLock) {
            if (queueWorkerStarted) return
            queueWorkerStarted = true
            scope.launch(Dispatchers.IO) {
                AppLog.i(TAG, "queueWorker: started")
                while (true) {
                    // Ждём сигнал что в очереди что-то появилось.
                    // trySend() в enqueueDownload триггерит receive().
                    // Если очередь уже пуста — блокируемся здесь до следующего enqueue.
                    queueSignal.receive()
                    // Обрабатываем все накопленные треки последовательно.
                    while (true) {
                        val request = pendingQueue.poll() ?: break
                        processTrackFromQueue(request)
                    }
                }
            }
        }
    }

    /**
     * Fix #265: Обработка одного трека из очереди.
     * Запускается ТОЛЬКО из queueWorker — гарантирует sequential execution.
     * activeJobs[track.id] хранит Job для возможности cancel через removeDownload.
     */
    private suspend fun processTrackFromQueue(request: DownloadRequest) {
        val track = request.track
        val url = track.url ?: run {
            AppLog.w(TAG, "queue: track #${track.id} has no URL — skip")
            removeState(track.id)
            maybeStopForegroundService()
            return
        }
        // Проверяем — может уже скачали или удалили пока ждал в очереди
        val existing = _downloads.value[track.id]
        if (existing != null && existing.isCompleted) {
            AppLog.v(TAG, "queue: track #${track.id} already completed — skip")
            maybeStopForegroundService()
            return
        }

        AppLog.i(TAG, "queue: start #${track.id} (${track.artist} — ${track.title}), queueRemaining=${pendingQueue.size}")
        val job = scope.launch(Dispatchers.IO) {
            try {
                downloadTrack(track.id, url, track, request.subDir, request.index, request.total)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce  // CancellationException НЕ ловим — coroutine cancellation
            } catch (t: Throwable) {
                AppLog.e(TAG, "downloadTrack failed for track #${track.id}: ${t.message}", t)
                if (_downloads.value[track.id]?.isInProgress == true) {
                    // #OFFLINE-STATUS-1: категоризуем причину — DEAD_URL vs NETWORK
                    // vs CODEC vs DISK. UI показывает дохлые треки отдельно, retry
                    // имеет смысл только для NETWORK/DISK.
                    val fr = classifyFailReason(t)
                    // #DEAD-RECHECK: запоминаем когда трек «умер» — авто-recheck
                    // через 1ч перепроверит URL через audioGetById (VK мог пере-выдать).
                    val deadTs = if (fr == re.pinok.data.model.FailReason.DEAD_URL)
                        System.currentTimeMillis() else null
                    updateState(track.id, DownloadState(track.id, DownloadStatus.FAILED, 0,
                        reason = t.message, failReason = fr, deadSinceMs = deadTs))
                }
            } finally {
                activeJobs.remove(track.id)
                maybeStopForegroundService()
            }
        }
        activeJobs[track.id] = job
        // Ждём завершения этого трека прежде чем брать следующий из очереди.
        // Это и есть sequential guarantee — одновременно активен только один track.
        job.join()
        AppLog.i(TAG, "queue: done #${track.id}, queueRemaining=${pendingQueue.size}")
    }

    /**
     * #OFFLINE-STATUS-1: классификация причины сбоя загрузки в [FailReason].
     *
     * Разделяет «трек умер» (DEAD_URL — URL протух/удалён, повтор бесполезен)
     * от сетевых сбоев (NETWORK — повтор может помочь) и проблем кодека (CODEC —
     * нужен транскодер). Это позволяет UI показывать дохлые треки отдельным
     * списком и не предлагать бесполезный retry.
     *
     * Источники сообщений: fetchText/fetchBytes кидают RuntimeException с
     * "HTTP {code} при загрузке ...", downloadHlsTrack — IOException при I/O,
     * MediaExtractor/MediaMuxer — свои исключения.
     */
    private fun classifyFailReason(t: Throwable): FailReason {
        val rawMsg = t.message
        val msg = if (rawMsg != null) rawMsg.lowercase() else ""
        when {
            msg.contains("http 403") || msg.contains("http 404") ||
                msg.contains("http 410") || msg.contains("http 451") ||
                msg.contains("expired") || msg.contains("unavailable") ||
                msg.contains("forbidden") || msg.contains("not found") -> return FailReason.DEAD_URL
            msg.contains("http 5") || msg.contains("http 500") ||
                msg.contains("http 502") || msg.contains("http 503") ||
                msg.contains("http 504") -> return FailReason.NETWORK
            msg.contains("siren") || msg.contains("codec") ||
                msg.contains("mediacodec") || msg.contains("mediamuxer") ||
                msg.contains("mediaextractor") -> return FailReason.CODEC
        }
        return when (t) {
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is javax.net.ssl.SSLException -> FailReason.NETWORK
            is java.io.IOException -> FailReason.DISK
            else -> FailReason.UNKNOWN
        }
    }

    /**
     * Удалить скачанный файл трека (или отменить активную/ожидающую загрузку).
     *
     * Fix #265: теперь также убирает трек из pendingQueue, если он ещё ждёт.
     * Без этого трек оставался бы в очереди и worker бы его скачал даже после
     * того как пользователь нажал «отменить».
     */
    fun removeDownload(trackId: Long) {
        ensureInitialized()
        AppLog.i(TAG, "removeDownload: track #$trackId")

        // Fix #265: убрать из очереди если ещё ждёт
        val wasQueued = pendingQueue.removeAll { it.track.id == trackId }
        if (wasQueued) {
            AppLog.i(TAG, "removeDownload: track #$trackId removed from queue (queueSize=${pendingQueue.size})")
        }

        // Отменить активную загрузку
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)

        // #VK-MUSIC-SAVER-PORT: читаем красивое/относительное имя ДО удаления
        // .meta — иначе подпапка плейлиста останется невычищенной.
        val metaFilename = readFilenameFromMeta(trackId)

        // Удалить файлы (m4a, mp3, ts, tmp)
        for (ext in listOf("m4a", "mp3", "ts", "tmp")) {
            val file = File(downloadDir, "$trackId.$ext")
            if (file.exists()) {
                file.delete()
            }
        }
        // Удалить временный HLS-файл
        for (ext in listOf("m4a.tmp", "ts.tmp", "mp3.tmp")) {
            val file = File(downloadDir, "$trackId.$ext")
            if (file.exists()) {
                file.delete()
            }
        }
        // #VK-MUSIC-SAVER-PORT: удалить файл в подпапке плейлиста
        // (относительный путь из .meta, например "Playlist/01. Artist - Title.m4a").
        if (metaFilename != null) {
            val subFile = File(downloadDir, metaFilename)
            if (subFile.exists() && subFile.isFile) {
                subFile.delete()
                AppLog.i(TAG, "removeDownload: deleted subfolder file '$metaFilename'")
            }
        }
        // #30: удалить sidecar .meta файл
        deleteMetadata(trackId)
        // Fix #166: удалить sidecar .sha256 файл
        deleteSha256(trackId)
        // Fix #167: удалить sidecar .m3u8info файл
        deleteM3u8Info(trackId)
        // Удалить временную директорию сегментов
        val segDir = File(downloadDir, "$trackId.segments")
        if (segDir.exists()) {
            segDir.listFiles()?.forEach { it.delete() }
            segDir.delete()
        }
        // #DOCFILE-SD: удалить файл с SD-карты если активирован DocumentFile tree.
        // Проверяем все возможные имена: numeric ($trackId.m4a/mp3/ts) и красивое
        // (из .meta, только имя файла без подпапки — SD-копия плоская).
        documentFileTreeUri?.let { treeUri ->
            runCatching {
                val namesToDelete = mutableListOf<String>()
                for (ext in listOf("m4a", "mp3", "ts")) {
                    namesToDelete.add("$trackId.$ext")
                }
                if (metaFilename != null) namesToDelete.add(metaFilename.substringAfterLast('/'))
                for (name in namesToDelete) {
                    if (DocumentFileStorage.deleteFileFromTree(appContext, treeUri, name)) {
                        AppLog.i(TAG, "removeDownload: deleted '$name' from SD card")
                    }
                }
            }.onFailure { e ->
                AppLog.w(TAG, "removeDownload: SD card cleanup failed for #$trackId — ${e.message}")
            }
        }

        updateState(trackId, DownloadState(trackId, DownloadStatus.REMOVING, 0))
        removeState(trackId)
        maybeStopForegroundService()
    }

    /**
     * Получить текущее состояние скачивания конкретного трека.
     */
    fun getDownloadState(trackId: Long): DownloadState? = _downloads.value[trackId]

    /**
     * True, если трек уже скачан и доступен офлайн.
     */
    fun isDownloaded(trackId: Long): Boolean =
        _downloads.value[trackId]?.isCompleted == true

    /**
     * Fix #170: True, если ЛЮБОЙ download сейчас в процессе (QUEUED или DOWNLOADING)
     * ИЛИ в очереди есть ожидающие треки.
     *
     * Используется в PlayerConnection.precacheNextTrack и auto-cache[playList]
     * чтобы гарантировать SEQUENTIAL загрузку — только один трек качается за раз.
     * Раньше pre-cache следующего + auto-cache первого запускали 2 параллельных
     * HLS download'а → 2 HTTP connection'а + 2 AES decrypt + 2 file write
     * одновременно → ANR на main thread (Skipped 246 frames, 4097ms MotionEvent).
     *
     * Fix #265: теперь также учитывает pendingQueue — если очередь не пуста,
     * hasActiveDownload() вернёт true (чтобы caller'ы не добавляли новые задания
     * пока текущие не отработают).
     *
     * O(N) где N = размер _downloads map (обычно 50-100), но values.filter
     * на StateFlow snapshot быстро (< 1мс).
     */
    fun hasActiveDownload(): Boolean =
        _downloads.value.values.any { it.isInProgress } || pendingQueue.isNotEmpty()

    /**
     * Fix #265: Размер очереди ожидающих загрузок (не включая активный трек).
     * Для UI badge "В очереди: N".
     */
    fun getQueueSize(): Int = pendingQueue.size

    /**
     * Fix #265: Позиция трека в очереди (1-based), или 0 если не в очереди.
     * Для UI "В очереди: 3" на карточке трека.
     */
    fun getQueuePosition(trackId: Long): Int {
        var pos = 0
        for (t in pendingQueue) {
            pos++
            if (t.track.id == trackId) return pos
        }
        return 0
    }

    /**
     * Fix #265: True, если трек стоит в очереди (ещё не начал скачиваться).
     * Удобный helper для UI.
     */
    fun isQueued(trackId: Long): Boolean =
        pendingQueue.any { it.track.id == trackId }

    /**
     * Получить локальный файл трека (если скачан). Read-only операция.
     *
     * Fix #76b: валидация magic bytes при каждом обращении.
     * Fix #53-B: НЕ удаляем файл при провале валидации — getLocalFile
     * вызывается из toMediaItem/OfflineManager/getTotalDownloadedBytes
     * (часто, из UI). Удаление здесь приводило к потере кэша. Очистка
     * невалидных файлов — только в refreshFromDisk (startup).
     */
    fun getLocalFile(trackId: Long): File? {
        // #BT-37SEC-FIX: .m4a (новые скачивания, MediaMuxer)优先, .ts (fallback/Siren), .mp3 (прямые)
        for (ext in listOf("m4a", "ts", "mp3")) {
            val file = File(downloadDir, "$trackId.$ext")
            if (file.exists()) {
                if (isValidAudioFile(file, ext)) {
                    AppLog.v(TAG, "getLocalFile: #$trackId.$ext OK (${file.length()}B, ${file.name})")
                    return file
                } else {
                    // Fix #53-B: getLocalFile — read-only операция, НЕ удаляем файл.
                    AppLog.w(TAG, "getLocalFile: #$trackId.$ext не прошёл валидацию (size=${file.length()}B) — возвращаем null без удаления")
                    return null
                }
            }
        }
        // §42.12 P1 #5: если numeric файла нет — ищем по красивому имени из .meta.
        // Новые скачивания (с включённым numTracksInPlaylist) называют файлы
        // "NN. Artist - Title.m4a" вместо "456249594.m4a". .meta хранит это имя.
        val metaFilename = readFilenameFromMeta(trackId)
        if (metaFilename != null) {
            val metaFile = File(downloadDir, metaFilename)
            if (metaFile.exists()) {
                val ext = metaFilename.substringAfterLast('.', "").lowercase()
                if (ext.isNotEmpty() && isValidAudioFile(metaFile, ext)) {
                    AppLog.v(TAG, "getLocalFile: #$trackId via .meta filename='${metaFilename}' (${metaFile.length()}B)")
                    return metaFile
                }
            }
        }
        AppLog.v(TAG, "getLocalFile: #$trackId — файл не найден локально")
        return null
    }

    /** Общий размер всех скачанных файлов в байтах. */
    fun getTotalDownloadedBytes(): Long {
        if (!initialized) return 0L
        return _downloads.value.values
            .filter { it.isCompleted }
            .sumOf { state ->
                getLocalFile(state.trackId)?.length() ?: 0L
            }
    }

    /** Количество скачанных треков. */
    fun getDownloadedCount(): Int = _downloads.value.values.count { it.isCompleted }

    /**
     * #OFFLINE-TAB: Удалить ВСЕ скачанные треки + отменить всю очередь.
     *
     * Вызывается из Настройки → Офлайн → «Очистить всё» (после confirm dialog).
     *
     * Порядок:
     *  1. Очистить pendingQueue (треки, ждущие sequential-worker).
     *  2. Cancel все активные Job (activeJobs).
     *  3. Удалить все файлы в downloadDir: .m4a/.mp3/.ts/.tmp + sidecar
     *     .meta/.sha256/.m3u8info + директории .segments/.
     *  4. Сбросить _downloads в emptyMap().
     *  5. maybeStopForegroundService() — погасить notification.
     *
     * Не использует removeDownload(trackId) по одному — это O(N) updateState/
     * removeState вызовов с recomposition на каждый. Здесь один bulk-сброс.
     */
    fun clearAllDownloads() {
        ensureInitialized()
        AppLog.i(TAG, "clearAllDownloads: started")

        // 1. Очистить очередь ожидающих.
        val queueCleared = pendingQueue.size
        pendingQueue.clear()
        if (queueCleared > 0) {
            AppLog.i(TAG, "clearAllDownloads: cleared pendingQueue ($queueCleared items)")
        }

        // 2. Cancel все активные загрузки.
        val activeIds = activeJobs.keys.toList()
        for (id in activeIds) {
            val job = activeJobs.remove(id)
            if (job != null) {
                job.cancel()
            }
        }
        if (activeIds.isNotEmpty()) {
            AppLog.i(TAG, "clearAllDownloads: cancelled ${activeIds.size} active jobs")
        }

        // 3. Удалить все файлы и поддиректории в downloadDir.
        var deletedFiles = 0
        var deletedDirs = 0
        val files = downloadDir.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    // .segments/ — очистить содержимое, затем саму директорию.
                    val children = f.listFiles()
                    if (children != null) {
                        for (c in children) {
                            if (c.delete()) deletedFiles++
                        }
                    }
                    if (f.delete()) deletedDirs++
                } else {
                    if (f.delete()) deletedFiles++
                }
            }
        }
        AppLog.i(TAG, "clearAllDownloads: deleted $deletedFiles files, $deletedDirs segment dirs in ${downloadDir.absolutePath}")

        // #DOCFILE-SD: очистить SD-карту если активирован DocumentFile tree.
        documentFileTreeUri?.let { treeUri ->
            runCatching {
                val sdDeleted = DocumentFileStorage.clearTree(appContext, treeUri)
                AppLog.i(TAG, "clearAllDownloads: deleted $sdDeleted files from SD card (DocumentFile tree)")
            }.onFailure { e ->
                AppLog.w(TAG, "clearAllDownloads: SD card cleanup failed — ${e.message}")
            }
        }

        // 4. Сбросить состояние загрузок (один bulk-апдейт → одна recomposition).
        // #RACE-FIX: atomic update.
        _downloads.update { emptyMap() }

        // 5. Погасить foreground-service если был.
        maybeStopForegroundService()
        AppLog.i(TAG, "clearAllDownloads: done")
    }

    /**
     * #OFFLINE-TAB: Поставить в очередь список треков (для «Загрузить всё»).
     *
     * Фильтрует уже скачанные / уже в очереди / в процессе, остальные ставит в
     * pendingQueue. Sequential queue-worker (Fix #265) обрабатывает их по одному
     * — в любой момент активна максимум одна HLS-загрузка.
     *
     * Треки с пустым URL пропускаются (VK иногда отдаёт track без url —
     * нужно audioUnmaskSource, но если URL отсутствует физически — нечего качать).
     *
     * @return количество треков, фактически добавленных в очередь
     *   (excludes already-downloaded / already-queued / in-progress / no-url).
     */
    fun enqueueAll(tracks: List<Track>): Int {
        ensureInitialized()
        if (tracks.isEmpty()) {
            AppLog.i(TAG, "enqueueAll: empty list — nothing to do")
            return 0
        }

        // Собираем ID уже скачанных / в очереди / в процессе — чтобы не дублировать.
        val skipIds = HashSet<Long>()
        for (entry in _downloads.value) {
            val st = entry.value
            if (st.isCompleted || st.isInProgress) {
                skipIds.add(entry.key)
            }
        }
        for (t in pendingQueue) {
            skipIds.add(t.track.id)
        }

        var enqueued = 0
        var skippedNoUrl = 0
        var skippedExisting = 0
        for (t in tracks) {
            val url = t.url
            if (url.isNullOrBlank()) {
                skippedNoUrl++
                continue
            }
            if (skipIds.contains(t.id)) {
                skippedExisting++
                continue
            }
            updateState(t.id, DownloadState(
                trackId = t.id,
                status = DownloadStatus.QUEUED,
                progress = 0,
                title = t.title,
                artist = t.artist,
                ownerId = t.ownerId,
            ))
            pendingQueue.add(DownloadRequest(t))
            skipIds.add(t.id)
            enqueued++
        }

        if (enqueued > 0) {
            startForegroundService()
            queueSignal.trySend(Unit)
        }
        AppLog.i(TAG, "enqueueAll: enqueued=$enqueued, skippedNoUrl=$skippedNoUrl, " +
            "skippedExisting=$skippedExisting (input=${tracks.size}, alreadyKnown=${skipIds.size - enqueued})")
        return enqueued
    }

    /**
     * #DEAD-RECHECK: список «дохлых» треков (DEAD_URL) для авто-recheck.
     *
     * Возвращает пары (trackId, ownerId, title, artist, deadSinceMs) — вызывающий
     * код (SettingsScreen) вызывает audioGetById для каждого и если VK вернул
     * новый URL — enqueueDownload. URL мог протухнуть временно (VK пере-выдал
     * ссылку, трек разблокировали, etc.).
     *
     * @param minDeadAgeMs минимальный возраст dead-статуса для recheck (default 1ч).
     *                     Треки dead <1ч не перепроверяются (слишком свежие — VK
     *                     ещё не мог пере-выдать URL, лишний API-запрос).
     */
    fun getDeadTracksForRecheck(minDeadAgeMs: Long = 3_600_000L): List<DownloadState> {
        ensureInitialized()
        val now = System.currentTimeMillis()
        val result = ArrayList<DownloadState>()
        for (st in _downloads.value.values) {
            if (!st.isDead) continue
            val since = st.deadSinceMs
            if (since == null) {
                // Нет timestamp (старая запись до #DEAD-RECHECK) — перепроверяем.
                result.add(st)
            } else if (now - since >= minDeadAgeMs) {
                result.add(st)
            }
        }
        return result
    }

    /**
     * #DEAD-RECHECK: сброс dead-статуса (вызывается после успешного re-enqueue
     * или когда пользователь нажал «Повторить»). Очищает deadSinceMs и ставит
     * QUEUED чтобы UI показал прогресс вместо «недоступен».
     */
    fun resetDeadStatus(trackId: Long) {
        ensureInitialized()
        val prev = _downloads.value[trackId]
        if (prev != null && prev.isDead) {
            updateState(trackId, prev.copy(
                status = DownloadStatus.QUEUED,
                progress = 0,
                reason = null,
                failReason = null,
                deadSinceMs = null,
            ))
            AppLog.i(TAG, "resetDeadStatus: track #$trackId — dead status cleared, now QUEUED")
        }
    }

    // ─── Internal ──────────────────────────────────────────────────

    private fun ensureInitialized() {
        check(initialized) { "TrackDownloadManager не инициализирован. Вызови init(context) в SovaApp.onCreate." }
    }

    private fun updateState(trackId: Long, state: DownloadState) {
        // #RACE-FIX (2026-08-03): atomic update via _downloads.update{}.
        // Раньше `_downloads.value = _downloads.value + (id to state)` —
        // non-atomic read-modify-write: concurrent enqueueDownload + auto-cache +
        // segment-progress updates теряли записи. update{} атомарен.
        // Сохраняем метаданные (title, artist, ownerId) из предыдущего состояния
        var merged = state
        _downloads.update { current ->
            val prev = current[trackId]
            merged = if (prev != null && (state.title.isEmpty() || state.artist.isEmpty())) {
                state.copy(title = if (state.title.isEmpty()) prev.title else state.title,
                           artist = if (state.artist.isEmpty()) prev.artist else prev.artist,
                           ownerId = if (state.ownerId == 0L) prev.ownerId else state.ownerId)
            } else state
            current + (trackId to merged)
        }

        // Fix #142: throttling notification updates.
        // Раньше updateProgress вызывался на каждом HLS-сегменте (10+ параллельно) →
        // rate > 6/sec → Android NotificationService shed'ит обновления:
        //   "Package enqueue rate is 6.92. Shedding 0|re.pinok.debug|1001"
        // В результате прогресс-бар не двигался, а на lock screen мог "пропасть"
        // медиа-плеер (MediaSession notification страдала от общей перегрузки
        // NotificationManager).
        // Теперь: updateProgress не чаще 500ms между вызовами. Финальный вызов
        // (isCompleted) всегда проходит без throttle — пользователь видит 100%.
        try {
            val snapshot = _downloads.value
            val active = snapshot.values.filter { it.isInProgress }
            val activeCount = active.size
            val avgProgress = when {
                activeCount > 0 -> active.map { it.progress }
                    .ifEmpty { listOf(0) }
                    .average().toInt().coerceIn(0, 100)
                merged.isCompleted -> 100
                else -> 0
            }
            val now = System.currentTimeMillis()
            val isFinal = merged.isCompleted || activeCount == 0
            if (isFinal || now - lastNotifyTs >= NOTIFY_THROTTLE_MS) {
                lastNotifyTs = now
                MusicDownloadService.updateProgress(appContext, activeCount, avgProgress)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "updateState: updateProgress notification failed: ${e.message}")
        }
    }

    private fun removeState(trackId: Long) {
        // #RACE-FIX: atomic update.
        _downloads.update { current -> current - trackId }
    }

    // ─── Foreground Service ─────────────────────────────────────────

    /**
     * Запускает MusicDownloadService как foreground — обязательно на Android 8+,
     * иначе система убивает фоновый процесс загрузки.
     *
     * Fix #249: отменяем отложенный stopService (если был запланирован в
     * maybeStopForegroundService) — новая загрузка продлевает жизнь сервиса.
     */
    private fun startForegroundService() {
        mainHandler.removeCallbacks(stopServiceRunnable)
        try {
            MusicDownloadService.start(appContext)
        } catch (e: Exception) {
            AppLog.w(TAG, "startForegroundService failed", e)
        }
    }

    /**
     * Если активных загрузок нет — останавливаем foreground-сервис.
     *
     * Fix #249: НЕ вызываем stopService() мгновенно — откладываем на
     * 1500мс. Если за это время появится новая загрузка,
     * startForegroundService() отменит pending-stop (см. выше).
     * Без этого дебаунса получаем ForegroundServiceDidNotStartInTimeException
     * на fast start-stop сценарии (трек уже в кэше → корутина завершается
     * за 4мс → stopService вызывается сразу после startForegroundService).
     */
    private fun maybeStopForegroundService() {
        val active = _downloads.value.values.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        if (active == 0) {
            mainHandler.removeCallbacks(stopServiceRunnable)
            mainHandler.postDelayed(stopServiceRunnable, 1500L)
        }
    }

    // ─── Fix #73: HLS-aware download ───────────────────────────────

    /**
     * Точка входа: определяет тип URL (HLS или прямой) и скачивает.
     *
     * Fix #76b: та же нормализация URL, что и в PlayerConnection.toMediaItem().
     * VK audio CDN отдаёт базовый URL без /index.m3u8.
     * Без этого downloadDirectTrack() скачивал m3u8-текст как .mp3 →
     * невалидный файл → ExoPlayer skip при повторном воспроизведении.
     */
    private suspend fun downloadTrack(
        trackId: Long,
        url: String,
        track: Track,
        subDir: String? = null,
        index: Int? = null,
        total: Int? = null,
    ) {
        val cleaned = url.trim()
        val https = if (cleaned.startsWith("http://")) {
            "https://" + cleaned.substring("http://".length)
        } else cleaned

        // Fix #76b: нормализация как в PlayerConnection.toMediaItem()
        val normalizedUrl = if (https.contains("vkuseraudio.net") &&
            !https.contains("m3u8", ignoreCase = true)) {
            "${https.trimEnd('/')}/index.m3u8"
        } else https

        if (normalizedUrl.contains("m3u8", ignoreCase = true)) {
            downloadHlsTrack(trackId, normalizedUrl, track, subDir, index, total)
        } else {
            downloadDirectTrack(trackId, normalizedUrl, track, subDir, index, total)
        }
    }

    /**
     * #39 C3: Скачивание прямого MP3 (non-HLS) с retry + Range-resume.
     *
     * Retry: 3 попытки, backoff 1s/3s/9s. Range-resume: если .tmp файл
     * уже существует (частичная загрузка), отправляем Range: bytes=<size>-.
     */
    private suspend fun downloadDirectTrack(
        trackId: Long,
        url: String,
        track: Track,
        subDir: String? = null,
        index: Int? = null,
        total: Int? = null,
    ) {
        updateState(trackId, DownloadState(trackId, DownloadStatus.DOWNLOADING, 0))

        val tempFile = File(downloadDir, "$trackId.mp3.tmp")
        val targetFile = File(downloadDir, "$trackId.mp3")
        // #VK-MUSIC-SAVER-PORT: финальный файл может лечь в подпапку плейлиста.
        val renameDir = if (subDir != null) File(downloadDir, subDir).apply { mkdirs() } else downloadDir

        val maxRetries = 3
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadDirectWithResume(trackId, url, tempFile)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                // #VK-MUSIC-SAVER-PORT: переименовать numeric → красивое имя
                // (в подпапку плейлиста если скачиваем плейлист).
                var finalFile = targetFile
                var finalFilename: String? = null
                try {
                    val built = FilenameBuilder.buildFilename(
                        track = track,
                        ext = "mp3",
                        index = index,
                        total = total,
                        useTrackNumber = subDir != null,
                    )
                    if (built != null) {
                        val uniqueName = FilenameBuilder.resolveCollision(renameDir, built)
                        val renamed = File(renameDir, uniqueName)
                        if (targetFile.renameTo(renamed)) {
                            finalFile = renamed
                            finalFilename = if (subDir != null) "$subDir/$uniqueName" else uniqueName
                            AppLog.i(TAG, "direct #$trackId: renamed '${targetFile.name}' → '${finalFilename}'")
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "direct #$trackId: FilenameBuilder failed: ${e.message} — numeric name kept")
                }
                // #30: сохраняем метаданные в sidecar .meta файл
                saveMetadata(track, finalFilename)
                // Fix #166: SHA-256 integrity sidecar
                saveSha256(trackId, finalFile)
                // #DOCFILE-SD: копируем финальный файл на SD-карту если активирован
                // DocumentFile tree URI (non-primary volume). Internal копия остаётся
                // для быстрого воспроизведения (ExoPlayer → Uri.fromFile).
                copyToSdCardIfNeeded(trackId, finalFile, finalFilename?.substringAfterLast('/'))
                updateState(trackId, DownloadState(trackId, DownloadStatus.COMPLETED, 100,
                    title = track.title, artist = track.artist, ownerId = track.ownerId, codec = "mp3"))
                AppLog.i(TAG, "Трек #$trackId скачан (direct, attempt $attempt, codec=mp3)")
                return
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                // НЕ удаляем tempFile — он позволит Range-resume на следующей попытке.
                if (attempt >= maxRetries) {
                    AppLog.e(TAG, "downloadDirectTrack: $attempt attempts exhausted for #$trackId: ${e.message}", e)
                    throw e
                }
                val backoffMs = (1000L * Math.pow(3.0, (attempt - 1).toDouble())).toLong()
                AppLog.w(TAG, "downloadDirectTrack attempt $attempt failed: ${e.message}, retry in ${backoffMs}ms")
                delay(backoffMs)
            }
        }
    }

    /** #39 C3: HTTP-загрузка MP3 с поддержкой Range-resume. */
    private fun downloadDirectWithResume(trackId: Long, url: String, tempFile: File) {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            AppLog.i(TAG, "downloadDirectWithResume: resuming #$trackId from $existingBytes bytes")
        }
        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            val isPartial = response.code == 206
            if (!response.isSuccessful && response.code != 206) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}")
            }
            val append = isPartial && existingBytes > 0
            if (!append && tempFile.exists()) tempFile.delete()

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
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = ((bytesRead.toFloat() / totalBytes.toFloat()) * 100f)
                                .toInt().coerceIn(0, 100)
                            updateState(trackId, DownloadState(trackId, DownloadStatus.DOWNLOADING, progress))
                        }
                    }
                }
            }
            // Fix #147: Content-Length vs received bytes integrity check.
            // Если сервер отдал Content-Length, но мы прочитали меньше —
            // файл обрезан (stream закрылся раньше времени, сеть упала).
            // Без этой проверки обрезанный MP3 сохранялся как «успешно скачанный»
            // и падал только при воспроизведении (или вообще играл с артефактами).
            // Бросаем IOException → retry loop в downloadTrack подхватит.
            if (totalBytes > 0 && bytesRead < totalBytes) {
                throw java.io.IOException(
                    "Truncated download #$trackId: got $bytesRead of $totalBytes bytes " +
                    "(${(bytesRead.toFloat() / totalBytes.toFloat() * 100f).toInt()}%)"
                )
            }
        }
    }

    /**
     * Данные о шифровании HLS-плейлиста.
     */
    private data class HlsEncryption(
        val method: String,       // "AES-128"
        val keyUrl: String,       // URL ключа (16 байт)
        val iv: ByteArray?,       // null = использовать порядковый номер сегмента как IV
    ) {
        override fun equals(other: Any?): Boolean =
            other is HlsEncryption && method == other.method && keyUrl == other.keyUrl && iv.contentEquals(other.iv)

        override fun hashCode(): Int = 31 * (31 * method.hashCode() + keyUrl.hashCode()) + (iv?.contentHashCode() ?: 0)
    }

    /**
     * Результат парсинга HLS-плейлиста.
     */
    private data class HlsPlaylist(
        val segments: List<String>,
        val encryption: HlsEncryption?,
    )

    /**
     * Fix #73: Скачивание HLS-трека.
     *
     * 1. Скачивает m3u8-плейлист.
     * 2. Парсит .ts-сегменты + параметры шифрования.
     * 3. Если зашифровано (AES-128) — скачивает ключ.
     * 4. Скачивает сегменты параллельно (до 4 одновременных).
     * 5. Расшифровывает сегменты (если нужно) и склеивает в один .ts файл.
     */
    private suspend fun downloadHlsTrack(
        trackId: Long,
        playlistUrl: String,
        track: Track,
        subDir: String? = null,
        index: Int? = null,
        total: Int? = null,
    ) {
        // Шаг 1: скачиваем m3u8-плейлист
        updateState(trackId, DownloadState(trackId, DownloadStatus.DOWNLOADING, 0))
        AppLog.i(TAG, "HLS track #$trackId: fetching playlist from ${playlistUrl.take(80)}")

        val playlistContent = fetchText(playlistUrl)
        val playlist = parseHlsPlaylist(playlistContent, playlistUrl)
        AppLog.i(TAG, "HLS track #$trackId: playlist parsed — ${playlist.segments.size} segments, encrypted=${playlist.encryption != null}")

        if (playlist.segments.isEmpty()) {
            AppLog.w(TAG, "HLS track #$trackId: no segments (master playlist), falling back to direct download")
            return downloadDirectTrack(trackId, playlistUrl, track, subDir, index, total)
        }

        // Шаг 2: если зашифровано — скачиваем ключ
        val keyBytes: ByteArray? = if (playlist.encryption != null) {
            val enc = playlist.encryption
            if (enc.method != "AES-128") {
                AppLog.w(TAG, "HLS track #$trackId: unsupported encryption method ${enc.method}")
                throw RuntimeException("Неподдерживаемый метод шифрования HLS: ${enc.method}")
            }
            AppLog.i(TAG, "HLS track #$trackId: AES-128 encrypted, fetching key from ${enc.keyUrl.take(80)}")
            fetchBytes(enc.keyUrl).also { key ->
                if (key.size != 16) {
                    AppLog.e(TAG, "HLS track #$trackId: AES-128 key has invalid size ${key.size}B (expected 16)")
                    throw RuntimeException("AES-128 key должен быть 16 байт, получено ${key.size}")
                }
                AppLog.v(TAG, "HLS track #$trackId: AES key fetched OK (16B)")
            }
        } else null

        // Шаг 3: скачиваем сегменты параллельно
        val segDir = File(downloadDir, "$trackId.segments").apply { mkdirs() }
        // Fix #78 (ENOENT): guard перед циклом скачивания. Если segDir не создан
        // (например, Scoped Storage не дал создать /Music/PinoK/...) — нет смысла
        // запускать N параллельных корутин, каждая из которых упадёт с ENOENT.
        if (!segDir.exists() || !segDir.canWrite()) {
            throw RuntimeException(
                "Cannot create segment directory: ${segDir.absolutePath} " +
                "(exists=${segDir.exists()}, canWrite=${if (segDir.exists()) segDir.canWrite() else false}). " +
                "Check download path in Settings → may need to pick a writable location."
            )
        }
        val totalSegments = playlist.segments.size
        val completedSegments = java.util.concurrent.atomic.AtomicInteger(0)

        AppLog.i(TAG, "HLS track #$trackId: starting download of $totalSegments segments (encrypted=${keyBytes != null})")

        try {
        val downloadJobs = playlist.segments.mapIndexed { index, segUrl ->
            scope.async(Dispatchers.IO) {
                val segFile = File(segDir, "%04d.ts".format(index))
                downloadSegment(segUrl, segFile, segmentIndex = index, maxRetries = 4)
                // Расшифровка AES-128-CBC
                if (keyBytes != null) {
                    val iv = playlist.encryption?.iv
                        ?: java.math.BigInteger.valueOf(index.toLong()).toByteArray().let { pad16(it) }
                    decryptSegment(segFile, keyBytes, iv)
                    AppLog.v(TAG, "HLS track #$trackId: seg #$index decrypted OK (${segFile.length()}B)")
                }
                completedSegments.incrementAndGet()
                val progress = ((completedSegments.get().toFloat() / totalSegments.toFloat()) * 100f)
                    .toInt().coerceIn(0, 99)
                updateState(trackId, DownloadState(trackId, DownloadStatus.DOWNLOADING, progress))
                segFile.length()
            }
        }

        // Ждём все сегменты (awaitAll бросает исключение на первом провалившемся)
        val sizes = try {
            downloadJobs.awaitAll()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // CancellationException НЕ ловим — coroutine cancellation
            throw ce
        } catch (e: Exception) {
            // Один из сегментов провалился после всех retry. Логируем сколько успело скачаться.
            val succeeded = (0 until totalSegments).count { i ->
                val f = File(segDir, "%04d.ts".format(i))
                f.exists() && f.length() > 0L
            }
            AppLog.e(TAG, "HLS track #$trackId: awaitAll failed after $succeeded/$totalSegments segments downloaded: ${e.message}", e)
            throw e
        }
        val totalSize = sizes.sum()

        // Fix #50-B: Проверка целостности — все сегменты должны быть на диске и непустые
        val missingSegments = (0 until totalSegments).filter { i ->
            val f = File(segDir, "%04d.ts".format(i))
            !f.exists() || f.length() == 0L
        }
        if (missingSegments.isNotEmpty()) {
            val preview = missingSegments.take(10).joinToString(",")
            val suffix = if (missingSegments.size > 10) ",..." else ""
            AppLog.e(TAG, "HLS track #$trackId: ${missingSegments.size}/$totalSegments segments missing or empty: [$preview$suffix]")
            throw RuntimeException("Track assembly failed: ${missingSegments.size}/$totalSegments segments missing")
        }

        AppLog.i(TAG, "HLS track #$trackId: all $totalSegments segments downloaded (${totalSize / 1024} KB total), merging...")

        // #BT-37SEC-FIX (2026-08-01): Раньше сегменты склеивались raw-byte
        // copyTo → на границе 2-го/3-го сегмента (~37с при типичной длине
        // VK-сегмента 10-15с) ExoPlayer терял MPEG-TS sync byte, попадал в
        // бесконечный BUFFERING→READY→BUFFERING цикл. Под Bluetooth A2DP
        // (строгий буферизатор) баг проявлялся стабильнее, чем через динамик.
        // Лог 2026-08-01 17:24:31: track #456249768, pos=37447ms, циклится.
        //
        // Фикс: используем MediaExtractor + MediaMuxer для извлечения AAC
        // аудио-треков из каждого сегмента и сборки их в валидный MP4 (.m4a).
        // ExoPlayer играет .m4a идеально (это стандартный формат для музыки).
        // Если MediaExtractor не находит аудио-трек (VK Siren codec) —
        // fallback на raw .ts concat (как раньше).
        //
        // #OFFLINE-TAB: читаем выбранный пользователем формат. MP3 пока НЕ
        // подключён (нужен ffmpeg-kit + Siren-транскодер, P0 #2 из §42) —
        // логируем предупреждение и сохраняем как M4A. Когда транскодер будет
        // готов, здесь появится ветка transcodeToMp3() вместо mergeSegmentsToM4a().
        val prefFormat: re.pinok.data.local.AudioFormat = try {
            re.pinok.SovaApp.get().prefs.data.first().audioFormat
        } catch (e: Exception) {
            AppLog.w(TAG, "HLS track #$trackId: audioFormat prefs read failed (${e.message}) — defaulting to M4A")
            re.pinok.data.local.AudioFormat.M4A
        }
        if (prefFormat == re.pinok.data.local.AudioFormat.MP3) {
            AppLog.w(TAG, "HLS track #$trackId: MP3 format requested but encoder not yet integrated " +
                "(P0 #2 Siren транскодер/ffmpeg-kit pending) — saving as M4A. Setting persists.")
        }
        val targetM4a = File(downloadDir, "$trackId.m4a")
        val targetTs = File(downloadDir, "$trackId.ts")
        // Удаляем старые файлы если были (re-download после удаления)
        targetM4a.delete()
        targetTs.delete()

        val m4aOk = try {
            mergeSegmentsToM4a(segDir, totalSegments, targetM4a, trackId)
        } catch (e: Exception) {
            AppLog.w(TAG, "HLS track #$trackId: MediaMuxer→.m4a failed: ${e.message} — will try Siren transcoder")
            targetM4a.delete()
            false
        }

        // §42.12 P0 #2: если MediaExtractor не нашёл аудио (VK Siren, magic 0x25) —
        // раньше падали в raw .ts concat с пометкой codec=siren (Wi-Fi бейдж).
        // Теперь: склеиваем .ts → запускаем SirenTranscoder (ffmpeg-kit) → .m4a.
        // Если транскод успешен — трек играет офлайн (codec=aac). Если нет —
        // старая механика (.ts, codec=siren, стрим онлайн).
        val targetFile = if (m4aOk) targetM4a else {
            AppLog.i(TAG, "HLS track #$trackId: MediaExtractor found no audio (likely Siren) — concatenating .ts for transcoder")
            // Fix #SIREN-PLAYBACK: двухпроходный concat.
            //
            // ПРОВЛЕМА: VK HLS-сегменты приходят в двух форматах:
            //   1. MPEG-TS container (magic 0x47) — содержит Siren audio внутри TS
            //   2. Raw Siren data (magic 0x25) — без TS-контейнера
            // Склейка ВСЕХ сегментов в один файл ломает ffmpeg TS-демуксер:
            // при попадании на raw Siren (0x25) внутри TS-потока → потеря sync →
            // M4A с 88200Hz, чередующийся mono/stereo, rapid AudioTrack cycling.
            //
            // РЕШЕНИЕ: два прохода.
            // Проход 1: подсчитываем TS-valid (0x47) vs raw Siren (0x25).
            // Если есть TS-valid — склеиваем ТОЛЬКО их для ffmpeg транскода.
            // Если ВСЕ raw Siren — склеиваем ВСЕ для .ts fallback (codec=siren).
            //
            // Проход 1: классификация сегментов
            val tsSegments = mutableListOf<Int>()
            val sirenSegments = mutableListOf<Int>()
            for (i in playlist.segments.indices) {
                val segFile = File(segDir, "%04d.ts".format(i))
                if (!segFile.exists()) continue
                val firstByte = segFile.inputStream().use { it.read() }
                if (firstByte == 0x47) {
                    tsSegments.add(i)
                } else {
                    sirenSegments.add(i)
                }
            }
            AppLog.i(TAG, "HLS track #$trackId: segment classification — TS=${tsSegments.size}, raw Siren=${sirenSegments.size}")

            val useTranscoder = tsSegments.isNotEmpty()
            val segmentsToConcat = if (useTranscoder) tsSegments else (tsSegments + sirenSegments)

            // Проход 2: склейка выбранных сегментов
            val tempTs = File(downloadDir, "$trackId.ts.tmp")
            tempTs.delete()
            FileOutputStream(tempTs).use { output ->
                for (i in segmentsToConcat) {
                    val segFile = File(segDir, "%04d.ts".format(i))
                    if (segFile.exists()) {
                        segFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                        segFile.delete()
                    }
                }
            }
            AppLog.i(TAG, "HLS track #$trackId: concat done — ${segmentsToConcat.size}/${totalSegments} segments" +
                if (useTranscoder) " (TS-only for transcoder)" else " (all segments, .ts fallback)")

            if (!tempTs.renameTo(targetTs)) {
                tempTs.copyTo(targetTs, overwrite = true)
                tempTs.delete()
            }

            // Удаляем оставшиеся сегменты (не попавшие в concat)
            segDir.listFiles()?.forEach { it.delete() }

            // Шаг 2: Siren→AAC транскод через ffmpeg-kit.
            // §42.12 P3 #11: если audioConvertMethod="hls_native" — пропускаем
            // транскод, оставляем .ts (codec=siren, Wi-Fi бейдж). Это для слабых
            // устройств или если пользователь не хочет +15 MB ffmpeg-kit в APK.
            // Fix #SIREN-PLAYBACK: также пропускаем транскод если нет TS-valid
            // сегментов (useTranscoder=false) — ffmpeg нечего декодировать.
            val convertMethod = try {
                re.pinok.SovaApp.get().prefs.data.first().audioConvertMethod
            } catch (e: Exception) {
                "siren_transcoder" // default
            }
            val skipTranscodeReason = when {
                convertMethod == "hls_native" -> "hls_native"
                !useTranscoder -> "no TS segments (all raw Siren)"
                else -> null
            }
            val transcoded = if (skipTranscodeReason != null) {
                AppLog.i(TAG, "HLS track #$trackId: transcode skipped ($skipTranscodeReason) — keep .ts")
                false
            } else try {
                SirenTranscoder.transcodeToM4a(targetTs, targetM4a)
            } catch (e: Exception) {
                AppLog.e(TAG, "HLS track #$trackId: SirenTranscoder threw: ${e.message}")
                false
            }

            if (transcoded && targetM4a.exists() && targetM4a.length() > 10_000L) {
                // Транскод успешен — удаляем .ts, оставляем .m4a.
                AppLog.i(TAG, "HLS track #$trackId: Siren→AAC transcode OK (${targetM4a.length() / 1024} KB) — removing .ts")
                targetTs.delete()
                targetM4a
            } else {
                // Транскод не удался — оставляем .ts (старая механика, Wi-Fi бейдж).
                // Это НЕ ошибка — трек всё ещё доступен онлайн через HLS.
                AppLog.w(TAG, "HLS track #$trackId: Siren transcode failed — keeping .ts (codec=siren, online-only)")
                targetM4a.delete()
                targetTs
            }
        }

        // Удаляем временную директорию сегментов
        segDir.listFiles()?.forEach { it.delete() }
        segDir.delete()

        // Fix #50-B: Валидация итогового файла — минимальный размер.
        val finalSize = targetFile.length()
        if (finalSize < 10_000L) {
            AppLog.e(TAG, "HLS track #$trackId: merged file too small (${finalSize}B, expected ≥10KB) — discarding")
            targetFile.delete()
            deleteMetadata(trackId)
            throw RuntimeException("Merged file too small: ${finalSize}B (expected ≥10KB)")
        }
        // #BT-37SEC-FIX: для .m4a — полная структурная проверка MP4 (ftyp+moov+
        // mdat), для .ts — sync byte. Здесь только логирование — реальная
        // валидация (с fallback на .ts) сделана ВНУТРИ mergeSegmentsToM4a перед
        // rename. Если мы тут видим .m4a — он уже прошёл isValidMp4Box.
        val ext = targetFile.extension.lowercase()
        // #SIREN-TRANSCODER (P0 #2): определяем codec кэша.
        // После внедрения SirenTranscoder siren-треки транскодируются в .m4a (aac)
        // и играют офлайн. Siren-кэш (.ts с magic != 0x47) остаётся только как
        // FALLBACK — если ffmpeg-kit не смог транскодировать (декодер недоступен,
        // битый вход, timeout). В этом случае codec=siren, трейл стримится онлайн
        // через HLS-стек (где siren умеет), Wi-Fi бейдж в UI показывает «онлайн-only».
        // Это НЕ ошибка — пользователь видит реальное состояние и может удалить кеш.
        val codec: String = if (ext == "m4a") {
            try {
                val header = ByteArray(12)
                java.io.DataInputStream(targetFile.inputStream()).use { it.readFully(header) }
                val ftyp = String(header, 4, 4, Charsets.US_ASCII)
                val brand = String(header, 8, 4, Charsets.US_ASCII)
                if (ftyp == "ftyp") {
                    AppLog.i(TAG, "HLS track #$trackId: M4A OK — ftyp brand=$brand (${finalSize / 1024} KB)")
                } else {
                    AppLog.w(TAG, "HLS track #$trackId: M4A missing 'ftyp' box (got '$ftyp') — file may be corrupt after rename")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "HLS track #$trackId: M4A header read failed: ${e.message}")
            }
            "aac"
        } else {
            // .ts fallback path — проверяем magic byte.
            val magicByte = targetFile.inputStream().use { it.read() }
            if (magicByte == 0x47) {
                AppLog.v(TAG, "HLS track #$trackId: TS sync byte OK (0x47) — real MPEG-TS, playable offline")
                "mpegts"
            } else {
                // Siren (non-encrypted VK codec, magic != 0x47, наблюдается 0x25).
                // КЭШИРУЕМ как .ts, статус COMPLETED, codec=siren.
                AppLog.w(TAG, "HLS track #$trackId: merged .ts is SIREN (magic=0x${magicByte.toString(16)}) — " +
                    "cached as COMPLETED (codec=siren). Offline play needs transcoder (P0 #2); streaming online via HLS.")
                "siren"
            }
        }

        // Fix #166: SHA-256 integrity sidecar (для HLS — после merge, хеш от финального .ts)
        // §42.12 P1 #5: saveMetadata вызывается ПОЗЖЕ (после rename) чтобы сохранить
        // красивое имя файла. Здесь только SHA + m3u8 info.
        saveSha256(trackId, targetFile)
        // Fix #167: m3u8 info sidecar — для проверки unchanged (server-side change detection)
        saveM3u8Info(trackId, playlistUrl, playlist.segments, playlist.encryption)

        // §42.12 P1 #3: пишем MP4 metadata теги (©nam/©ART/©alb/©too/cmt/©lyr).
        // Только для .m4a (не .ts — там нет MP4-контейнера). Tags записываются
        // ПОСЛЕ sha256 (хеш от аудио-данных без тегов, чтобы проверка не валилась).
        // Genius lyrics (P2 #8) и промо-комментарий (P2 #9) — опционально из prefs.
        if (codec == "aac" && targetFile.extension.equals("m4a", ignoreCase = true)) {
            try {
                val snap = re.pinok.SovaApp.get().prefs.data.first()
                if (snap.writeId3Tags) {
                    val lyrics = if (snap.writeGeniusLyrics) {
                        GeniusLyricsFetcher.fetchLyrics(track.artist, track.title)
                    } else null
                    val comment = if (snap.writePromoComment) {
                        "Downloaded by PinoK v${re.pinok.BuildConfig.VERSION_NAME} — https://github.com/pin24/VK_X_mod"
                    } else null
                    Mp4TagWriter.writeTags(
                        m4aFile = targetFile,
                        track = track,
                        lyrics = lyrics,
                        comment = comment,
                        coverUrl = track.albumThumb,  // #VK-MUSIC-SAVER-PORT: обложка в covr-атом
                    )
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "HLS track #$trackId: Mp4TagWriter failed: ${e.message} — tags skipped (file still valid)")
            }
        }

        // §42.12 P1 #5: переименование numeric файла в красивое имя.
        // "456249594.m4a" → "Artist - Title.m4a" (или "01. Artist - Title.m4a"
        // если index передан). Только для .m4a (.ts fallback остаётся numeric —
        // siren-кеш временный). Если sanitize даёт пустую строку — оставляем numeric.
        // #VK-MUSIC-SAVER-PORT: при скачивании плейлиста файл ложится в подпапку
        // `downloads/music/<Playlist>/` с нумерацией "NN. ".
        var finalFilename: String? = null
        var finalFile: File = targetFile
        if (codec == "aac" && targetFile.extension.equals("m4a", ignoreCase = true)) {
            try {
                val renameDir = if (subDir != null) File(downloadDir, subDir).apply { mkdirs() } else downloadDir
                val built = FilenameBuilder.buildFilename(
                    track = track,
                    ext = "m4a",
                    index = index,
                    total = total,
                    useTrackNumber = subDir != null,
                )
                if (built != null) {
                    val uniqueName = FilenameBuilder.resolveCollision(renameDir, built)
                    val renamed = File(renameDir, uniqueName)
                    if (targetFile.renameTo(renamed)) {
                        finalFilename = if (subDir != null) "$subDir/$uniqueName" else uniqueName
                        finalFile = renamed
                        AppLog.i(TAG, "HLS track #$trackId: renamed '${targetFile.name}' → '$finalFilename'")
                        // saveSha256 уже посчитан от numeric, после rename хеш НЕ пересчитываем —
                        // он использовался только для integrity check при refresh, rename не меняет bytes.
                    } else {
                        AppLog.w(TAG, "HLS track #$trackId: rename to '$uniqueName' failed — keeping numeric name")
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "HLS track #$trackId: FilenameBuilder failed: ${e.message} — numeric name kept")
            }
        }

        // #30: сохраняем метаданные в sidecar .meta файл (+ filename для P1 #5).
        saveMetadata(track, finalFilename)
        // #DOCFILE-SD: копируем финальный HLS файл на SD-карту если активирован
        // DocumentFile tree URI. Используем finalFile (реальный файл после rename).
        // Для плейлистов берём только имя файла (без подпапки) — SD-копия плоская.
        val sdCardFileName = finalFilename?.substringAfterLast('/') ?: finalFile.name
        copyToSdCardIfNeeded(trackId, finalFile, sdCardFileName)
        // #HLS-COMPLETED-STATE: раньше здесь НЕ было updateState(COMPLETED) —
        // только AppLog. StateFlow оставался DOWNLOADING → UI не показывал
        // завершённую загрузку (кнопка «скачано», список «Скачанная музыка»,
        // isDownloaded=false до рестарта). Теперь фиксируем COMPLETED.
        updateState(trackId, DownloadState(trackId, DownloadStatus.COMPLETED, 100,
            title = track.title, artist = track.artist, ownerId = track.ownerId, codec = codec))
        AppLog.i(TAG, "HLS track #$trackId: COMPLETED — $totalSegments segments, ${finalSize / 1024} KB, encrypted=${keyBytes != null}, codec=$codec, path=${finalFile.name}")
        } finally {
            // #BT-37SEC-FIX: всегда чистим segDir в finally — если merge уже
            // удалил сегменты, это no-op; если упали до merge — чистим мусор.
            // Раньше проверка !targetFile.exists() была эвристикой «успел ли
            // merge», но теперь targetFile может быть .m4a или .ts — проще
            // всегда чистить.
            segDir.listFiles()?.forEach { it.delete() }
            segDir.delete()
        }
    }

    /**
     * Скачать текст по URL.
     */
    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} при загрузке m3u8: ${response.message}")
            }
            return response.body?.string()
                ?: throw RuntimeException("Пустой ответ при загрузке m3u8")
        }
    }

    /**
     * Скачать бинарные данные по URL.
     */
    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} при загрузке ключа: ${response.message}")
            }
            return response.body?.bytes()
                ?: throw RuntimeException("Пустой ответ при загрузке ключа")
        }
    }

    /**
     * Расшифровать файл сегмента AES-128-CBC in-place.
     *
     * #30 (audio cache fix): HLS AES-128 сегменты НЕ имеют PKCS5 padding —
     * они выровнены по 16 байт (блок AES). Раньше пробовали PKCS5Padding
     * first → BadPaddingException на каждом сегменте → double-decrypt fallback.
     * Теперь NoPadding напрямую — быстрее и надёжнее.
     *
     * Fix #144: VK CDN сегменты часто НЕ выровнены по 16 байт (827576 % 16 = 8
     * в логе). Раньше код ОБРЕЗАЛ trailing bytes → теряли до 15 байт реального
     * шифротекста → последний AES-блок расшифровывался как мусор → иногда
     * повреждённый TS-пакет → ExoPlayer PARSING_CONTAINER_UNSUPPORTED.
     *
     * Теперь: ПАДДИМ нулями до 16 байт вместо обрезки. AES/CBC/NoPadding
     * расшифрует добавленный блок как мусор в конце — TS-декодер остановится
     * на последнем валидном TS-пакете и проигнорирует trailing мусор.
     * Это безопаснее: не теряем реальные данные, добавляем только мусор в конец.
     *
     * Альтернатива: обрезать trailing bytes если они HTTP artifacts (CRLF от
     * chunked encoding). Но различить artifacts от реального шифротекста
     * ненадёжно — паддинг нулями универсально безопасен.
     */
    private fun decryptSegment(file: File, key: ByteArray, iv: ByteArray) {
        val raw = file.readBytes()
        if (raw.isEmpty()) return
        // AES-128-CBC требует данные кратные 16 байтам. VK CDN сегменты часто
        // имеют trailing bytes (8 байт в логе — вероятно HTTP artifacts или
        // неполный последний блок). Паддим нулями до 16 — не теряем данные.
        val encrypted = if (raw.size % 16 != 0) {
            val padLen = 16 - (raw.size % 16)
            val padded = ByteArray(raw.size + padLen) // zero-filled by default
            System.arraycopy(raw, 0, padded, 0, raw.size)
            AppLog.w(TAG, "decryptSegment: размер ${raw.size} не кратен 16 — паддим нулями до ${padded.size} (Fix #144)")
            padded
        } else {
            raw
        }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encrypted)
        file.writeBytes(decrypted)
    }

    /**
     * Дополнить байтовый массив нулями слева до 16 байт (для IV из sequence number).
     */
    private fun pad16(bytes: ByteArray): ByteArray {
        if (bytes.size >= 16) return bytes.copyOfRange(bytes.size - 16, bytes.size)
        val padded = ByteArray(16)
        System.arraycopy(bytes, 0, padded, 16 - bytes.size, bytes.size)
        return padded
    }

    /**
     * #BT-37SEC-FIX (2026-08-01): Сборка HLS-сегментов в валидный MP4 (.m4a).
     *
     * Проблема: raw-byte concat .ts сегментов давал ExoPlayer'у поток, в
     * котором на границе сегментов ломался MPEG-TS sync byte (0x47). Демуксер
     * не мог найти следующий пакет → вечный BUFFERING→READY→BUFFERING цикл
     * на ~37-й секунде воспроизведения. Под Bluetooth A2DP баг воспроизводился
     * стабильнее из-за более строгого буферизатора.
     *
     * Решение: MediaExtractor умеет читать отдельный .ts-сегмент и доставать
     * из него AAC-сэмплы с правильными PTS. MediaMuxer пишет эти сэмплы в
     * валидный MP4-контейнер (.m4a) с монотонно возрастающими PTS — ExoPlayer
     * играет результат идеально, без провалов на границах сегментов.
     *
     * Алгоритм:
     *   1. Первый проход: открываем MediaExtractor для каждого сегмента,
     *      находим аудио-трек, запоминаем его MediaFormat (из первого сегмента).
     *      Если ни в одном сегменте нет аудио-трека (VK Siren) → return false,
     *      вызывающий код делает fallback на raw .ts concat.
     *   2. Создаём MediaMuxer на temp-файле, addTrack(format из п.1), start().
     *   3. Второй проход: для каждого сегмента читаем сэмплы через MediaExtractor,
     *      пишем в MediaMuxer с PTS = accumulatedTimeUs + sampleRelativePts.
     *      accumulatedTimeUs += max PTS сегмента + 1 (чтобы не было коллизий).
     *   4. stop(), release(). Атомарно переименовываем temp → target.
     *
     * @return true если .m4a успешно создан, false если MediaExtractor не нашёл
     *         аудио-трек (вызывающий код должен сделать fallback на .ts)
     */
    private fun mergeSegmentsToM4a(segDir: File, totalSegments: Int, targetFile: File, trackId: Long): Boolean {
        val tempFile = File(targetFile.parentFile, "${trackId}.m4a.tmp")
        tempFile.delete()

        val extractors = mutableListOf<MediaExtractor>()
        var muxer: MediaMuxer? = null

        try {
            // ── Первый проход: открываем extractors, находим audio-формат ──
            var audioFormat: MediaFormat? = null
            for (i in 0 until totalSegments) {
                val segFile = File(segDir, "%04d.ts".format(i))
                if (!segFile.exists() || segFile.length() == 0L) {
                    AppLog.w(TAG, "mergeSegmentsToM4a: seg #$i missing/empty — skip")
                    continue
                }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segFile.absolutePath)
                } catch (e: Exception) {
                    AppLog.w(TAG, "mergeSegmentsToM4a: seg #$i setDataSource failed: ${e.message} — skip")
                    runCatching { extractor.release() }
                    continue
                }
                // Ищем аудио-трек в этом сегменте
                var foundAudio = false
                for (trackIdx in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(trackIdx)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        extractor.selectTrack(trackIdx)
                        if (audioFormat == null) {
                            audioFormat = format
                            // getInteger бросает NPE если ключа нет — для AAC
                            // KEY_SAMPLE_RATE/KEY_CHANNEL_COUNT всегда есть, но
                            // для безопасности логируем через runCatching.
                            val sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(0)
                            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(0)
                            val duration = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
                            AppLog.i(TAG, "mergeSegmentsToM4a: seg #$i audio track found — mime=$mime, " +
                                "sampleRate=$sampleRate, channels=$channels, duration=${duration}us")
                        }
                        foundAudio = true
                        break
                    }
                }
                if (!foundAudio) {
                    AppLog.w(TAG, "mergeSegmentsToM4a: seg #$i has no audio track (VK Siren codec?) — skip extractor")
                    runCatching { extractor.release() }
                    // Продолжаем — может в других сегментах есть аудио
                } else {
                    extractors.add(extractor)
                }
            }

            if (audioFormat == null || extractors.isEmpty()) {
                AppLog.w(TAG, "mergeSegmentsToM4a: track #$trackId — no audio track in ANY segment " +
                    "(extractors=${extractors.size}, totalSegs=$totalSegments) — fallback to .ts concat")
                return false
            }

            // ── Создаём MediaMuxer ──
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            AppLog.i(TAG, "mergeSegmentsToM4a: track #$trackId — MediaMuxer started, " +
                "outTrack=$outTrackIndex, segments=${extractors.size}")

            // ── Второй проход: пишем сэмплы с непрерывными PTS ──
            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            var accumulatedPtsUs = 0L
            var totalSamples = 0

            for (extractor in extractors) {
                var segMaxPtsUs = 0L
                var segSampleCount = 0
                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val samplePts = extractor.sampleTime
                    if (samplePts > segMaxPtsUs) segMaxPtsUs = samplePts
                    val bufferInfo = MediaCodec.BufferInfo()
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = accumulatedPtsUs + samplePts
                    bufferInfo.flags = extractor.sampleFlags
                    buffer.position(0)
                    buffer.limit(sampleSize)
                    muxer.writeSampleData(outTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                    segSampleCount++
                    totalSamples++
                }
                AppLog.v(TAG, "mergeSegmentsToM4a: segment done — $segSampleCount samples, maxPts=${segMaxPtsUs / 1000}ms")
                // Сдвигаем PTS для следующего сегмента, чтобы они шли непрерывно
                accumulatedPtsUs += segMaxPtsUs + 1000L // +1ms gap между сегментами
            }

            muxer.stop()
            AppLog.i(TAG, "mergeSegmentsToM4a: track #$trackId — DONE, $totalSamples samples, " +
                "${tempFile.length() / 1024} KB, duration≈${accumulatedPtsUs / 1000}ms")

            // #M4A-VALIDATE (2026-08-01): проверка целостности .m4a перед rename.
            // MediaMuxer мог записать partial/пустой файл (например если все
            // writeSampleData упали с исключением, но stop() прошёл). Проверяем
            // структуру MP4: должны быть ftyp + moov + mdat boxes. Если хоть
            // одного нет — .m4a битый, return false → вызывающий код сделает
            // fallback на raw .ts concat (сегменты ещё на месте в segDir).
            if (!isValidMp4Box(tempFile)) {
                AppLog.e(TAG, "mergeSegmentsToM4a: track #$trackId — temp .m4a invalid " +
                    "(missing ftyp/moov/mdat, size=${tempFile.length()}B) — fallback to .ts concat")
                muxer.release()
                muxer = null
                tempFile.delete()
                return false
            }
            AppLog.i(TAG, "mergeSegmentsToM4a: track #$trackId — MP4 structure OK (ftyp+moov+mdat)")

            // Атомарная замена
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            muxer.release()
            muxer = null
            return true
        } catch (e: Exception) {
            AppLog.e(TAG, "mergeSegmentsToM4a: track #$trackId failed: ${e.message}", e)
            runCatching { muxer?.release() }
            muxer = null
            tempFile.delete()
            return false
        } finally {
            extractors.forEach { runCatching { it.release() } }
        }
    }

    /**
     * Парсинг HLS-плейлиста — извлечение URL сегментов и параметров шифрования.
     *
     * Обрабатывает:
     *  — Абсолютные URL (https://...)
     *  — Относительные пути (./segment0.ts, segment0.ts)
     *  — #EXT-X-KEY:METHOD=AES-128,URI="...",IV=0x...
     *  — Пропускает #EXT-X-MAP (initialization sections)
     *
     * @return [HlsPlaylist] с сегментами и информацией о шифровании.
     */
    private fun parseHlsPlaylist(playlist: String, playlistUrl: String): HlsPlaylist {
        val lines = playlist.lines().map { it.trim() }
        val baseUrl = playlistUrl.substringBeforeLast("/") + "/"

        // Если есть master-плейлист (#EXT-X-STREAM-INF) — unsupported
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            AppLog.w(TAG, "parseHlsPlaylist: master playlist detected, falling back to direct download")
            return HlsPlaylist(emptyList(), null)
        }

        var encryption: HlsEncryption? = null
        val segments = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (line.startsWith("#EXT-X-KEY")) {
                if (line.contains("METHOD=NONE")) {
                    encryption = null
                } else {
                    // Парсим параметры: METHOD=AES-128,URI="...",IV=0x...
                    val method = extractAttr(line, "METHOD")
                    val uriRaw = extractAttr(line, "URI")
                    if (method == "AES-128" && uriRaw != null) {
                        val keyUrl = uriRaw.removeSurrounding("\"")
                        val resolvedKeyUrl = if (keyUrl.startsWith("http://") || keyUrl.startsWith("https://")) {
                            keyUrl
                        } else {
                            baseUrl + keyUrl.removePrefix("./")
                        }
                        // IV: может отсутствовать (тогда используется номер сегмента)
                        val iv: ByteArray? = extractAttr(line, "IV")?.let { ivStr ->
                            if (ivStr.startsWith("0x") || ivStr.startsWith("0X")) {
                                val hex = ivStr.substring(2)
                                if (hex.length == 32) {
                                    hexToBytes(hex)
                                } else null
                            } else null
                        }
                        encryption = HlsEncryption(method = "AES-128", keyUrl = resolvedKeyUrl, iv = iv)
                        AppLog.i(TAG, "parseHlsPlaylist: AES-128 encrypted, keyUrl=${resolvedKeyUrl.take(80)}")
                    } else {
                        AppLog.w(TAG, "parseHlsPlaylist: unsupported encryption method=$method")
                    }
                }
            } else if (line.startsWith("#")) {
                // Другие теги — пропускаем
            } else if (line.isNotBlank()) {
                // URL сегмента
                val resolvedUrl = if (line.startsWith("http://") || line.startsWith("https://")) {
                    line
                } else {
                    baseUrl + line.removePrefix("./")
                }
                segments.add(resolvedUrl)
            }
            i++
        }

        return HlsPlaylist(segments, encryption)
    }

    /**
     * Извлечь значение атрибута из строки тега HLS.
     * Пример: 'METHOD=AES-128,URI="key.bin",IV=0x...' → extractAttr(s, "URI") = '"key.bin"'
     */
    private fun extractAttr(tag: String, attrName: String): String? {
        // Ищем ATTRNAME= , но не внутри другого слова
        val patterns = listOf("$attrName=", ",$attrName=")
        for (pattern in patterns) {
            val idx = tag.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) {
                val start = idx + pattern.length
                if (start >= tag.length) continue
                // Значение может быть в кавычках или без
                return if (tag[start] == '"') {
                    val end = tag.indexOf('"', start + 1)
                    if (end > start) tag.substring(start, end + 1) else null
                } else {
                    // Без кавычек — до запятой или конца строки
                    val end = tag.indexOf(',', start)
                    if (end > start) tag.substring(start, end) else tag.substring(start)
                }
            }
        }
        return null
    }

    /**
     * Конвертировать hex-строку (32 символа) в ByteArray (16 байт).
     */
    private fun hexToBytes(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = ((hex[i * 2].digitToInt(16) shl 4) + hex[i * 2 + 1].digitToInt(16)).toByte()
        }
        return bytes
    }

    /**
     * #39 C3 / Fix #50-B: Скачать один HLS-сегмент с retry (4 попытки) +
     * exponential backoff + jitter.
     *
     * Сегменты маленькие (2-10 MB), поэтому Range-resume не имеет смысла —
     * при неудаче просто перекачиваем с нуля. Retry покрывает transient
     * сбои (мобильная сеть, DNS hiccup, CDN 503).
     *
     * Fix #50-B: увеличено с 3 до 4 попыток, backoff изменён с фиксированного
     * 1s/3s/9s на exponential 500ms/1s/2s/4s + random jitter 0..200ms для
     * избежания thundering-herd при массовых retry (полезно когда параллельно
     * скачиваются 4 сегмента и все упали одновременно).
     */
    private suspend fun downloadSegment(
        url: String,
        targetFile: File,
        segmentIndex: Int = -1,
        maxRetries: Int = 4,
    ) {
        val segTag = if (segmentIndex >= 0) "seg #$segmentIndex" else "seg"
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RuntimeException("HTTP ${response.code} при загрузке $segTag: ${url.take(60)}")
                    }
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(16384)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: throw RuntimeException("Пустой body для $segTag: ${url.take(60)}")
                }
                if (targetFile.length() == 0L) {
                    throw RuntimeException("downloaded $segTag is 0 bytes")
                }
                if (segmentIndex >= 0) {
                    AppLog.v(TAG, "HLS $segTag attempt $attempt/$maxRetries OK: ${targetFile.length()}B — ${url.take(60)}")
                }
                return  // success
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastError = e
                targetFile.delete()  // cleanup partial segment
                if (attempt >= maxRetries) {
                    AppLog.e(TAG, "downloadSegment: $segTag $attempt/$maxRetries attempts exhausted for ${url.take(60)}: ${e.message}", e)
                    throw e
                }
                // Exponential backoff: 500ms * 2^(attempt-1) + random jitter 0..200ms
                val backoffMs = (500L * (1L shl (attempt - 1))) + (0..200L).random()
                AppLog.w(TAG, "HLS $segTag attempt $attempt/$maxRetries failed: ${e.message}, retry in ${backoffMs}ms")
                delay(backoffMs)
            }
        }
        throw lastError ?: RuntimeException("downloadSegment: $segTag failed after $maxRetries retries")
    }

    /**
     * Сканировать каталог и восстановить StateFlow.
     * VK audio segments с non-standard magic bytes (0x25 raw siren, 0x47
     * decrypted MPEG-TS) сохраняются (Fix #53-B / Fix #186).
     *
     * Fix #186 (audio cache loss after restart): ранее невалидные файлы
     * УДАЛЯЛИСЬ автоматически. Это приводило к потере кэша, если валидация
     * оказывалась слишком строгой (см. isValidMpegTs — до Fix #186 требовала
     * строгий 0x47, что убивало siren-segments). Теперь файлы НЕ удаляются
     * автоматически — только логируется warning. Удаление возможно только
     * через явный вызов removeDownload() из UI или isCacheValid()=false.
     *
     * #30 (audio cache fix): загружаем метаданные (title, artist, ownerId) из
     * sidecar .meta файлов. Раньше после рестарта все треки показывались как
     * "Unknown — Unknown" — metadata терялась.
     */
    private fun refreshFromDisk() {
        val map = mutableMapOf<Long, DownloadState>()
        downloadDir.listFiles()?.forEach { file ->
            val ext = file.extension
            // #BT-37SEC-FIX: добавлен .m4a (MediaMuxer output)
            if (ext == "mp3" || ext == "ts" || ext == "m4a") {
                val id = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
                if (file.length() < 1024) {
                    // Fix #186: НЕ удаляем — только логируем. Файл может быть
                    // валидным коротким сегментом или partial-write, который
                    // PlayerConnection ещё не дозаписал. Удаление — только явно.
                    AppLog.w(TAG, "refreshFromDisk: файл #$id подозрительно мал (${file.length()} B) — пропускаем (не удаляем, Fix #186)")
                    return@forEach
                }
                // Fix #53-B / #186: валидация отвергает только m3u8-текст ('#')
                // и HTML ('<'). VK audio segments (0x47, 0x25, 0xFF и др.) валидны.
                if (!isValidAudioFile(file, ext)) {
                    // Fix #186: НЕ удаляем — только логируем. Пользователь может
                    // удалить вручную через UI (removeDownload). Авто-удаление
                    // приводило к потере кэша при ложных срабатываниях валидации.
                    AppLog.w(TAG, "refreshFromDisk: файл #$id.$ext невалидный (m3u8-text или HTML) — пропускаем (не удаляем, Fix #186)")
                    return@forEach
                }
                val meta = loadMetadata(id)
                // #OFFLINE-STATUS-1: восстанавливаем codec после рестарта.
                // .m4a → "aac", .ts → проверяем magic byte (0x47=mpegts, иначе siren).
                // mp3 → "mp3". Нужно для UI-бейджа «онлайн-кеш» (codec=siren).
                val codecFromDisk: String? = when (ext) {
                    "m4a" -> "aac"
                    "mp3" -> "mp3"
                    "ts" -> {
                        val magic = runCatching { file.inputStream().use { it.read() } }.getOrDefault(-1)
                        if (magic == 0x47) "mpegts" else "siren"
                    }
                    else -> null
                }
                map[id] = DownloadState(
                    trackId = id,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    title = meta?.first ?: "",
                    artist = meta?.second ?: "",
                    ownerId = meta?.third ?: 0L,
                    codec = codecFromDisk,
                )
            }
        }
        // #VK-MUSIC-SAVER-PORT: восстановить треки, скачанные в подпапки плейлистов.
        // Для них numeric файла нет (файл лежит в downloads/music/<Playlist>/NN. Artist.m4a),
        // единственный источник правды — sidecar .meta (хранит относительный путь).
        downloadDir.listFiles()?.forEach { metaFile ->
            if (!metaFile.isFile || metaFile.extension != "meta") return@forEach
            val id = metaFile.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (map.containsKey(id)) return@forEach
            val rel = readFilenameFromMeta(id) ?: return@forEach
            val subFile = File(downloadDir, rel)
            if (!subFile.exists() || !subFile.isFile) return@forEach
            val subExt = subFile.extension.lowercase()
            if (subExt != "m4a" && subExt != "mp3" && subExt != "ts") return@forEach
            if (!isValidAudioFile(subFile, subExt)) return@forEach
            val meta = loadMetadata(id)
            val codecFromSub: String? = when (subExt) {
                "m4a" -> "aac"
                "mp3" -> "mp3"
                "ts" -> {
                    val magic = runCatching { subFile.inputStream().use { it.read() } }.getOrDefault(-1)
                    if (magic == 0x47) "mpegts" else "siren"
                }
                else -> null
            }
            map[id] = DownloadState(
                trackId = id,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                title = meta?.first ?: "",
                artist = meta?.second ?: "",
                ownerId = meta?.third ?: 0L,
                codec = codecFromSub,
            )
        }
        // #RACE-FIX (2026-08-03): MERGE with in-progress downloads instead of
        // overwriting. Раньше `_downloads.value = map` убивал активные IN_PROGRESS
        // записи если refreshFromDisk бежал пока загрузка ещё шла (init race).
        // Теперь: COMPLETED-с диска мёрджатся поверх текущего состояния; треки
        // которые уже в очереди/качаются (QUEUED/IN_PROGRESS) НЕ перетираются.
        _downloads.update { current ->
            val merged = current.toMutableMap()
            for ((id, diskState) in map) {
                val existing = current[id]
                // Только если трека нет в текущем состоянии ИЛИ он не активен
                // (не QUEUED/IN_PROGRESS) — ставим disk-состояние (COMPLETED).
                // isInProgress covers QUEUED + DOWNLOADING — don't overwrite active.
                if (existing == null || !existing.isInProgress) {
                    merged[id] = diskState
                }
            }
            merged.toMap()
        }
        AppLog.i(TAG, "Загружено ${map.size} существующих треков с диска (merged с in-progress)")
    }

    // ─── Metadata persistence (#30) ──────────────────────────────────

    /**
     * Сохранить метаданные трека в sidecar .meta файл (JSON).
     * Вызывается после успешного скачивания.
     */
    private fun saveMetadata(track: Track, filename: String? = null) {
        try {
            val metaFile = File(downloadDir, "${track.id}.meta")
            val map = mutableMapOf<String, Any>(
                "title" to track.title,
                "artist" to track.artist,
                "ownerId" to track.ownerId,
            )
            // §42.12 P1 #5: сохраняем красивое имя файла, если оно построено.
            // getLocalFile использует это для lookup, когда numeric файла нет.
            if (filename != null) map["filename"] = filename
            val json = com.google.gson.Gson().toJson(map)
            metaFile.writeText(json)
        } catch (e: Exception) {
            AppLog.w(TAG, "saveMetadata failed for #${track.id}: ${e.message}")
        }
    }

    /**
     * §42.12 P1 #5: прочитать сохранённое имя файла из .meta sidecar.
     * Возвращает null если .meta нет или поле filename отсутствует (старые кэши).
     */
    private fun readFilenameFromMeta(trackId: Long): String? {
        return try {
            val metaFile = File(downloadDir, "$trackId.meta")
            if (!metaFile.exists()) return null
            val json = com.google.gson.Gson().fromJson(
                metaFile.readText(),
                com.google.gson.JsonObject::class.java,
            )
            json.get("filename")?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            null
        }
    }

    /** Удалить .meta файл трека. */
    private fun deleteMetadata(trackId: Long) {
        try {
            val metaFile = File(downloadDir, "$trackId.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (_: Exception) {}
    }

    // ─── Fix #166: SHA-256 integrity sidecar ──────────────────────
    /**
     * Fix #166: Сохранить SHA-256 хеш скачанного файла в sidecar `.sha256`.
     *
     * Пишется сразу после успешного скачивания (HLS merge или direct MP3).
     * Хеш считается от ФИНАЛЬНОГО файла (после AES-decrypt + merge для HLS,
     * или от raw MP3 байтов для direct). При последующих проверках
     * verifyCacheIntegrity() перещитывает хеш и сравнивает — если файл
     * повредился на диске (BAD sector, partial write, user edit) — хеш
     * не совпадёт, кэш помечается CORRUPTED.
     *
     * Примечание: SHA-256 не детектит повреждения от неправильной
     * AES-дешифровки (тогда файл "цел" но содержит мусор). Для этого
     * есть структурная валидация: для .ts — isValidMpegTs (Fix #165, sync
     * bytes 0x47), для .m4a — isValidMp4Box (#M4A-VALIDATE, ftyp+moov+mdat).
     * SHA-256 детектит ИЗМЕНЕНИЯ файла после скачивания — что тоже важно.
     */

    /**
     * #DOCFILE-SD (P2): Копирует финальный файл на SD-карту через DocumentFile API.
     *
     * Вызывается после завершения загрузки (direct + HLS). Если [documentFileTreeUri]
     * не установлен (обычный File-based path) — no-op.
     *
     * @param trackId для логирования
     * @param sourceFile internal File (downloadDir/$trackId.ext или красивое имя)
     * @param targetNameOverride имя файла на SD-карте (если null — используется sourceFile.name)
     */
    private fun copyToSdCardIfNeeded(trackId: Long, sourceFile: java.io.File, targetNameOverride: String? = null) {
        val treeUri = documentFileTreeUri ?: return  // SD-карта не активирована — no-op
        val targetName = targetNameOverride ?: sourceFile.name
        runCatching {
            val ok = DocumentFileStorage.copyFileToTree(appContext, treeUri, sourceFile, targetName)
            if (ok) {
                AppLog.i(TAG, "copyToSdCardIfNeeded: #$trackId → SD card '$targetName' (${sourceFile.length() / 1024} KB) OK")
            } else {
                AppLog.w(TAG, "copyToSdCardIfNeeded: #$trackId → SD card '$targetName' FAILED — internal copy remains usable")
            }
        }.onFailure { e ->
            AppLog.e(TAG, "copyToSdCardIfNeeded: #$trackId SD card copy failed — ${e.message}", e)
            // НЕ бросаем — internal копия остаётся, пользователь может слушать.
        }
    }

    private fun saveSha256(trackId: Long, file: java.io.File) {
        try {
            val startTime = System.currentTimeMillis()
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val shaFile = java.io.File(downloadDir, "$trackId.sha256")
            shaFile.writeText(hash)
            val elapsed = System.currentTimeMillis() - startTime
            AppLog.i(TAG, "saveSha256: #$trackId hash=${hash.take(16)}... size=${file.length()}B elapsed=${elapsed}ms — saved to ${shaFile.name}")
        } catch (e: Exception) {
            AppLog.w(TAG, "saveSha256 failed for #$trackId: ${e.message}")
        }
    }

    /** Удалить .sha256 sidecar (при removeDownload). */
    private fun deleteSha256(trackId: Long) {
        try {
            val shaFile = java.io.File(downloadDir, "$trackId.sha256")
            if (shaFile.exists()) shaFile.delete()
        } catch (_: Exception) {}
    }

    /**
     * Fix #166: Проверить целостность кэша по SHA-256.
     *
     * @return CacheIntegrity enum:
     *   - VALID     — файл скачан, хеш совпадает (или .sha256 отсутствует —
     *                 backward compat для старых скачиваний; в этом случае
     *                 структурная валидация Fix #165 всё равно применяется)
     *   - CORRUPTED — файл скачан, .sha256 есть, но хеш НЕ совпадает
     *                 (файл изменился после скачивания)
     *   - NO_HASH   — файл скачан, .sha256 отсутствует (старая загрузка)
     *   - NOT_FOUND — файл не скачан или отсутствует на диске
     */
    enum class CacheIntegrity { VALID, CORRUPTED, NO_HASH, NOT_FOUND }

    fun verifyCacheIntegrity(trackId: Long): CacheIntegrity {
        if (!isDownloaded(trackId)) return CacheIntegrity.NOT_FOUND
        val file = getLocalFile(trackId) ?: return CacheIntegrity.NOT_FOUND
        val shaFile = java.io.File(downloadDir, "$trackId.sha256")
        if (!shaFile.exists()) {
            AppLog.i(TAG, "verifyCacheIntegrity: #$trackId NO_HASH (старая загрузка, нет .sha256 sidecar)")
            return CacheIntegrity.NO_HASH
        }
        return try {
            val expected = shaFile.readText().trim().lowercase()
            val startTime = System.currentTimeMillis()
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val elapsed = System.currentTimeMillis() - startTime
            if (actual == expected) {
                AppLog.i(TAG, "verifyCacheIntegrity: #$trackId VALID — hash=${actual.take(16)}... matches, size=${file.length()}B elapsed=${elapsed}ms")
                CacheIntegrity.VALID
            } else {
                AppLog.w(TAG, "verifyCacheIntegrity: #$trackId CORRUPTED — expected=${expected.take(16)}... actual=${actual.take(16)}... size=${file.length()}B — файл изменился после скачивания")
                CacheIntegrity.CORRUPTED
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "verifyCacheIntegrity: #$trackId exception: ${e.message}")
            CacheIntegrity.NOT_FOUND
        }
    }

    // ─── Fix #167: m3u8 unchanged check + deep rescan ─────────────
    /**
     * Fix #167: Сохранить информацию о m3u8-плейлисте в sidecar `.m3u8info`.
     *
     * Хранит: URL плейлиста + список URL сегментов + метод шифрования.
     * При checkM3u8Unchanged() перезапрашивает m3u8 и сравнивает segment URLs.
     * Если URLs изменились — трек перезалит на сервере, кэш устарел.
     *
     * Формат: JSON {playlistUrl, segmentUrls:[...], encryptionMethod}
     */
    private fun saveM3u8Info(
        trackId: Long,
        playlistUrl: String,
        segments: List<String>,
        encryption: HlsEncryption?,
    ) {
        try {
            val info = com.google.gson.Gson().toJson(mapOf(
                "playlistUrl" to playlistUrl,
                "segmentUrls" to segments,
                "encryptionMethod" to (encryption?.method ?: "NONE"),
                "segmentCount" to segments.size,
                "savedAt" to System.currentTimeMillis(),
            ))
            val infoFile = java.io.File(downloadDir, "$trackId.m3u8info")
            infoFile.writeText(info)
            AppLog.i(TAG, "saveM3u8Info: #$trackId playlistUrl=${playlistUrl.take(60)}... segments=${segments.size} enc=${encryption?.method ?: "NONE"}")
        } catch (e: Exception) {
            AppLog.w(TAG, "saveM3u8Info failed for #$trackId: ${e.message}")
        }
    }

    /** Удалить .m3u8info sidecar (при removeDownload). */
    private fun deleteM3u8Info(trackId: Long) {
        try {
            val infoFile = java.io.File(downloadDir, "$trackId.m3u8info")
            if (infoFile.exists()) infoFile.delete()
        } catch (_: Exception) {}
    }

    /**
     * Результат проверки m3u8 unchanged.
     */
    enum class M3u8CheckResult {
        UNCHANGED,       // m3u8 на сервере идентичен сохранённому
        CHANGED,         // URLs сегментов изменились → кэш устарел
        NO_INFO,         // нет .m3u8info sidecar (старая загрузка или direct MP3)
        FETCH_FAILED,    // не удалось запросить m3u8 (нет сети / 404 / timeout)
        NOT_HLS,         // файл не HLS (direct MP3 — m3u8 check не применим)
    }

    /**
     * Fix #167: Проверить, не изменился ли m3u8 на сервере.
     *
     * Запрашивает актуальный m3u8 по сохранённому URL, парсит segment URLs,
     * сравнивает с сохранёнными в .m3u8info sidecar.
     *
     * СЕТЬ: 1 HTTP-запрос на трек (только m3u8, без сегментов).
     * Ловит: трек перезалит, сегменты заменены, плейлист сокращён/расширен.
     * НЕ ловит: содержимое сегмента изменилось при том же URL (редко).
     *
     * @return M3u8CheckResult
     */
    suspend fun checkM3u8Unchanged(trackId: Long): M3u8CheckResult {
        val infoFile = java.io.File(downloadDir, "$trackId.m3u8info")
        if (!infoFile.exists()) {
            // Для direct MP3 нет m3u8 — проверка не применима
            // #BT-37SEC-FIX: проверяем .m4a и .ts (новый и старый форматы HLS-merge)
            val m4aFile = java.io.File(downloadDir, "$trackId.m4a")
            val tsFile = java.io.File(downloadDir, "$trackId.ts")
            return if (m4aFile.exists() || tsFile.exists()) M3u8CheckResult.NO_INFO else M3u8CheckResult.NOT_HLS
        }
        return try {
            val info = com.google.gson.Gson().fromJson(
                infoFile.readText(),
                com.google.gson.JsonObject::class.java,
            )
            val savedUrl = info.get("playlistUrl")?.asString ?: return M3u8CheckResult.NO_INFO
            val savedSegments = info.getAsJsonArray("segmentUrls")
                ?.map { it.asString } ?: emptyList()

            AppLog.i(TAG, "checkM3u8Unchanged: #$trackId fetching m3u8 from ${savedUrl.take(60)}...")
            val playlistContent = try {
                fetchText(savedUrl)
            } catch (e: Exception) {
                AppLog.w(TAG, "checkM3u8Unchanged: #$trackId fetch failed: ${e.message}")
                return M3u8CheckResult.FETCH_FAILED
            }
            val playlist = parseHlsPlaylist(playlistContent, savedUrl)
            val currentSegments = playlist.segments

            val unchanged = currentSegments == savedSegments
            val result = if (unchanged) M3u8CheckResult.UNCHANGED else M3u8CheckResult.CHANGED
            AppLog.i(TAG, "checkM3u8Unchanged: #$trackId $result — saved=${savedSegments.size} segs, current=${currentSegments.size} segs")
            if (!unchanged) {
                // Логируем различия для диагностики
                val added = currentSegments - savedSegments.toSet()
                val removed = savedSegments - currentSegments.toSet()
                if (added.isNotEmpty()) AppLog.w(TAG, "  added segments: ${added.size} (e.g. ${added.first().take(60)})")
                if (removed.isNotEmpty()) AppLog.w(TAG, "  removed segments: ${removed.size} (e.g. ${removed.first().take(60)})")
            }
            result
        } catch (e: Exception) {
            AppLog.w(TAG, "checkM3u8Unchanged: #$trackId exception: ${e.message}")
            M3u8CheckResult.FETCH_FAILED
        }
    }

    /**
     * Fix #167: Глубокая проверка одного трека — полный рескан.
     *
     * 1. Структурная валидация (Fix #165) — мгновенно
     * 2. SHA-256 целостность (Fix #166) — ~50мс
     * 3. m3u8 unchanged (Fix #167) — 1 HTTP-запрос
     *
     * Если m3u8 CHANGED или файл CORRUPTED — рекомендует перезагрузку.
     * Если всё OK — кэш на 100% валиден (насколько это проверяемо).
     *
     * @return DeepScanResult с деталями
     */
    data class DeepScanResult(
        val trackId: Long,
        val structuralValid: Boolean,    // Fix #165: MPEG-TS sync bytes
        val sha256Integrity: CacheIntegrity,  // Fix #166
        val m3u8Check: M3u8CheckResult,   // Fix #167
        val overallValid: Boolean,        // true = кэш полностью валиден
        val recommendation: String,       // человекочитаемая рекомендация
    )

    suspend fun deepScanTrack(trackId: Long): DeepScanResult {
        AppLog.i(TAG, "deepScanTrack: #$trackId starting deep scan")
        // 1. Структурная
        val structuralValid = isCacheValid(trackId) &&
            verifyCacheIntegrity(trackId) != CacheIntegrity.CORRUPTED
        // 2. SHA-256
        val shaResult = verifyCacheIntegrity(trackId)
        // 3. m3u8
        val m3u8Result = checkM3u8Unchanged(trackId)

        val overall = structuralValid &&
            shaResult != CacheIntegrity.CORRUPTED &&
            (m3u8Result == M3u8CheckResult.UNCHANGED || m3u8Result == M3u8CheckResult.NO_INFO)

        val recommendation = when {
            !structuralValid -> "Файл повреждён (MPEG-TS sync bytes неверные). Удалите и скачайте заново."
            shaResult == CacheIntegrity.CORRUPTED -> "SHA-256 не совпадает — файл изменился после скачивания. Удалите и скачайте заново."
            m3u8Result == M3u8CheckResult.CHANGED -> "m3u8 на сервере изменился — трек перезалит. Рекомендуется перезагрузка."
            m3u8Result == M3u8CheckResult.FETCH_FAILED -> "Не удалось проверить m3u8 (нет сети?). Локальная целостность OK."
            overall -> "Кэш полностью валиден. Можно слушать офлайн."
            else -> "Неизвестная проблема. Попробуйте перезагрузить трек."
        }

        AppLog.i(TAG, "deepScanTrack: #$trackId DONE — structural=$structuralValid sha=$shaResult m3u8=$m3u8Result overall=$overall")
        AppLog.i(TAG, "  recommendation: $recommendation")
        return DeepScanResult(trackId, structuralValid, shaResult, m3u8Result, overall, recommendation)
    }

    /**
     * Fix #167: Проверить все скачанные треки (для UI кнопки «Проверить кэш»).
     *
     * Лёгкая проверка: структурная + SHA-256 (без сети, мгновенно).
     * Для глубокой проверки (с m3u8) используйте deepScanTrack() по одному.
     *
     * @return map trackId → CacheIntegrity
     */
    fun scanAllCachedLight(): Map<Long, CacheIntegrity> {
        val result = mutableMapOf<Long, CacheIntegrity>()
        val cached = _downloads.value.values.filter { it.isCompleted }
        AppLog.i(TAG, "scanAllCachedLight: checking ${cached.size} cached tracks (structural + SHA-256, no network)")
        var valid = 0
        var corrupted = 0
        var noHash = 0
        for (state in cached) {
            val integrity = verifyCacheIntegrity(state.trackId)
            result[state.trackId] = integrity
            when (integrity) {
                CacheIntegrity.VALID -> valid++
                CacheIntegrity.CORRUPTED -> corrupted++
                CacheIntegrity.NO_HASH -> noHash++
                CacheIntegrity.NOT_FOUND -> corrupted++
            }
        }
        AppLog.i(TAG, "scanAllCachedLight: DONE — valid=$valid corrupted=$corrupted noHash=$noHash total=${cached.size}")
        return result
    }

    /**
     * Загрузить метаданные трека из .meta файла.
     * @return Triple(title, artist, ownerId) или null если файл отсутствует.
     */
    private fun loadMetadata(trackId: Long): Triple<String, String, Long>? {
        return try {
            val metaFile = File(downloadDir, "$trackId.meta")
            if (!metaFile.exists()) return null
            val json = metaFile.readText()
            val o = com.google.gson.JsonParser.parseString(json).asJsonObject
            Triple(
                o.get("title")?.asString ?: "",
                o.get("artist")?.asString ?: "",
                o.get("ownerId")?.asLong ?: 0L,
            )
        } catch (_: Exception) { null }
    }

    /**
     * Проверяет, что файл является аудио (а не m3u8-текстом, сохранённым
     * как .mp3/.ts — старый баг Fix #76b).
     *
     * Fix #53-B: Раньше требовались строгие magic bytes — .ts → 0x47
     * (MPEG-TS sync), .mp3 → 0xFF/ID3. Но VK audio HLS segments НЕ всегда
     * начинаются с 0x47: non-encrypted tracks (siren=1) отдаются как raw
     * bytes (наблюдались файлы начинающиеся с 0x25). AES-128 decrypted
     * tracks начинаются с 0x47. Прямые MP3 — с 0xFF или "ID3".
     *
     * Строгая валидация .ts (требование 0x47) приводила к тому, что валидные
     * non-encrypted файлы (0x25) признавались «невалидными» и удалялись в
     * getLocalFile/refreshFromDisk → кэш пропадал после первого обращения.
     *
     * Теперь: отвергаем только m3u8-текст (начинается с '#' = 0x23) и
     * HTML-страницы ошибок (начинаются с '<' = 0x3C). Всё остальное
     * считается валидным аудио. Размер проверяется отдельно (>= 1024B в
     * refreshFromDisk, >= 10000B в downloadHlsTrack).
     *
     * Fix #165: для .ts файлов добавлена СТРУКТУРНАЯ валидация MPEG-TS.
     * Раньше принимался любой первый байт кроме 0x23/0x3C — даже полностью
     * повреждённые AES-decryption-мусором файлы (magic 25 78 11 5b) проходили
     * валидацию → ExoPlayer падал с UnrecognizedInputFormatException →
     * «перепрыгивание» треков. Теперь для .ts проверяем:
     *   - первый байт == 0x47 (MPEG-TS sync byte), И
     *   - байт по смещению 188 == 0x47 (второй пакет), И
     *   - байт по смещению 376 == 0x47 (третий пакет)
     * Это гарантирует что файл реально MPEG-TS, а не мусор. Для mp3/siren
     * старая логика сохранена (принимаем 0x47/0x25/0xFF, отвергаем 0x23/0x3C).
     */
    private fun isValidAudioFile(file: File, ext: String): Boolean {
        return try {
            // #BT-37SEC-FIX / #M4A-VALIDATE: .m4a (MP4 container) — полная
            // структурная проверка: ftyp + moov + mdat boxes. Раньше проверяли
            // только ftyp (4 байта) — этого недостаточно: partial-файл после
            // упавшего MediaMuxer мог иметь ftyp, но без moov/mdat → ExoPlayer
            // не мог играть. Теперь сканируем все top-level boxes.
            if (ext.equals("m4a", ignoreCase = true)) {
                return isValidMp4Box(file)
            }
            // Fix #165: строгая MPEG-TS валидация для .ts
            if (ext.equals("ts", ignoreCase = true)) {
                return isValidMpegTs(file)
            }
            // Прочие форматы (mp3, siren) — старая логика
            val header = ByteArray(4)
            // #30 (build fix): readFully есть только на DataInputStream.
            java.io.DataInputStream(file.inputStream()).use { stream ->
                stream.readFully(header)
            }
            val first = header[0]
            // Отвергаем m3u8-текст ('#' = 0x23) и HTML ('<' = 0x3C).
            // VK audio segments могут начинаться с 0x47 (decrypted MPEG-TS),
            // 0x25 (raw siren), 0xFF (MP3 sync) — все валидны.
            first != 0x23.toByte() && first != 0x3C.toByte()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fix #165: Структурная валидация MPEG-TS файла.
     *
     * MPEG-TS packet = 188 байт, первый байт каждого = 0x47 (sync byte).
     * Проверяем sync byte на смещениях 0, 188, 376 (первые 3 пакета).
     * Этого достаточно для детекции повреждённых файлов:
     *   - AES-мусор (25 78 11 5b ...) → нет 0x47 на смещении 0 → INVALID
     *   - HTML/JSON-ошибки → нет 0x47 → INVALID
     *   - Обрезанные файлы (< 376 байт) → проверяем сколько есть пакетов
     *
     * Fix #186 (audio cache loss after restart): ранее эта функция требовала
     * строгий 0x47 на смещении 0. Но VK отдаёт HLS-сегменты в двух форматах:
     *   1. AES-128 encrypted (дешифрованные) → начинаются с 0x47 (MPEG-TS sync)
     *   2. Non-encrypted siren=1 → raw bytes, начинаются с 0x25 (наблюдается)
     * По mobile-сети VK чаще отдаёт siren (вариант 2), по Wi-Fi — encrypted.
     * Поэтому треки, скачанные по мобильной сети, при restart'е приложения
     * признавались «невалидными» и УДАЛЯЛИСЬ refreshFromDisk — кэш пропадал.
     *
     * Новая логика:
     *   - Если первый байт == 0x47 → строгая проверка MPEG-TS (sync на 188/376)
     *   - Если первый байт != 0x47 И != '#' (0x23, m3u8-text) И != '<' (0x3C, HTML)
     *     → считаем валидным siren-segment'ом (Fix #53-B наконец-то применён)
     *   - Размер всё ещё должен быть ≥ 188 байт (иначе точно мусор)
     *
     * @return true если файл валидный, false если повреждён
     */
    private fun isValidMpegTs(file: File): Boolean {
        return try {
            val size = file.length()
            if (size < 188) {
                AppLog.w(TAG, "isValidMpegTs: #${file.nameWithoutExtension} size=${size}B < 188 (минимум 1 пакет) → INVALID")
                return false
            }
            // Читаем первые 3 sync-байта (смещения 0, 188, 376).
            val bytesNeeded = if (size >= 377) 377 else 188
            val buf = ByteArray(bytesNeeded)
            java.io.DataInputStream(file.inputStream()).use { stream ->
                stream.readFully(buf)
            }
            val firstByte = buf[0]

            // #OFFLINE-STATUS-1: откат #SIREN-FIX со стороны валидации. Siren
            // (.ts с firstByte != 0x47) снова считается ВАЛИДНЫМ кэшем (Fix #186) —
            // файл сохраняется, codec=siren, статус COMPLETED. Офлайн-игра невозможна,
            // но toMediaItem/onPlayerError видят magic!=0x47 и стримят онлайн.
            // Отвергаем только настоящий мусор: m3u8-text '#' (0x23) и HTML '<' (0x3C).
            if (firstByte != 0x47.toByte()) {
                if (firstByte == 0x23.toByte() || firstByte == 0x3C.toByte()) {
                    val reason = if (firstByte == 0x23.toByte()) "m3u8-text" else "HTML"
                    AppLog.w(TAG, "isValidMpegTs: #${file.nameWithoutExtension} first byte=0x%02X ($reason) size=${size}B → INVALID".format(firstByte))
                    return false
                }
                AppLog.v(TAG, "isValidMpegTs: #${file.nameWithoutExtension} first byte=0x%02X (siren) size=${size}B → VALID (siren cache, codec=siren)".format(firstByte))
                return true
            }

            // Строгая проверка MPEG-TS: sync bytes на смещениях 188, 376.
            val sync1Ok = bytesNeeded >= 189 && buf[188] == 0x47.toByte()
            val sync2Ok = bytesNeeded >= 377 && buf[376] == 0x47.toByte()
            val result = (bytesNeeded < 189 || sync1Ok) && (bytesNeeded < 377 || sync2Ok)
            if (!result) {
                val b0 = "%02x".format(buf[0])
                val b1 = if (bytesNeeded >= 189) "%02x".format(buf[188]) else "—"
                val b2 = if (bytesNeeded >= 377) "%02x".format(buf[376]) else "—"
                AppLog.w(TAG, "isValidMpegTs: #${file.nameWithoutExtension} sync bytes = $b0/$b1/$b2 (нужно 47/47/47) size=${size}B → INVALID (повреждён AES-мусором?)")
            } else {
                AppLog.v(TAG, "isValidMpegTs: #${file.nameWithoutExtension} sync 0x47/0x47/0x47 OK size=${size}B → VALID")
            }
            result
        } catch (e: Exception) {
            AppLog.w(TAG, "isValidMpegTs: #${file.nameWithoutExtension} exception: ${e.message}")
            false
        }
    }

    /**
     * #M4A-VALIDATE (2026-08-01): Полная структурная проверка MP4-контейнера.
     *
     * MP4 = последовательность top-level boxes. Каждый box:
     *   bytes 0-3: size (big-endian uint32)
     *   bytes 4-7: type (4 ASCII chars)
     *   bytes 8..: data (size-8 bytes)
     * Особые случаи size:
     *   0  → box extends to end of file (последний)
     *   1  → extended size (64-bit) в bytes 8-15
     *
     * Валидный MP4 audio должен содержать минимум:
     *   - ftyp (file type) — всегда первый
     *   - moov (movie metadata — track info, sample table)
     *   - mdat (media data — actual audio samples)
     *
     * MediaMuxer OutputFormat.MUXER_OUTPUT_MPEG_4 пишет: ftyp → mdat → moov
     * (moov в конце). Если merge упал на полпути — moov может отсутствовать
     * (MediaMuxer.stop() не вызван или упал). ExoPlayer не сможет играть
     * файл без moov (нет sample table → невозможно найти сэмплы).
     *
     * Алгоритм: итерируем top-level boxes через RandomAccessFile.seek,
     * ищем ftyp/moov/mdat. Ограничение 50 boxes (защита от битых size →
     * бесконечный цикл). Сами data bytes не читаем, только headers → быстро.
     *
     * @return true если найдены все три обязательных box (ftyp+moov+mdat)
     */
    private fun isValidMp4Box(file: File): Boolean {
        return try {
            val fileSize = file.length()
            if (fileSize < 16) {
                AppLog.w(TAG, "isValidMp4Box: #${file.nameWithoutExtension} size=${fileSize}B < 16 → INVALID")
                return false
            }
            var hasFtyp = false
            var hasMoov = false
            var hasMdat = false
            val header = ByteArray(16)
            java.io.RandomAccessFile(file, "r").use { raf ->
                var pos = 0L
                var boxes = 0
                while (pos + 8 <= fileSize && boxes < 50) {
                    raf.seek(pos)
                    raf.readFully(header, 0, 8)
                    val size = ((header[0].toLong() and 0xFF) shl 24) or
                               ((header[1].toLong() and 0xFF) shl 16) or
                               ((header[2].toLong() and 0xFF) shl 8) or
                               (header[3].toLong() and 0xFF)
                    val type = String(header, 4, 4, Charsets.US_ASCII)
                    when (type) {
                        "ftyp" -> hasFtyp = true
                        "moov" -> hasMoov = true
                        "mdat" -> hasMdat = true
                    }
                    if (hasFtyp && hasMoov && hasMdat) break  // все найдены, дальше не нужно
                    // size == 0 → box extends to EOF, больше boxes нет.
                    if (size == 0L) break
                    // size == 1 → extended 64-bit size в bytes 8-15.
                    if (size == 1L) {
                        if (pos + 16 > fileSize) break
                        raf.readFully(header, 0, 8)
                        val extSize =
                            ((header[0].toLong() and 0xFF) shl 56) or
                            ((header[1].toLong() and 0xFF) shl 48) or
                            ((header[2].toLong() and 0xFF) shl 40) or
                            ((header[3].toLong() and 0xFF) shl 32) or
                            ((header[4].toLong() and 0xFF) shl 24) or
                            ((header[5].toLong() and 0xFF) shl 16) or
                            ((header[6].toLong() and 0xFF) shl 8) or
                            (header[7].toLong() and 0xFF)
                        if (extSize < 16L) break
                        pos += extSize
                    } else {
                        if (size < 8L) break
                        pos += size
                    }
                    boxes++
                }
            }
            val ok = hasFtyp && hasMoov && hasMdat
            if (ok) {
                AppLog.v(TAG, "isValidMp4Box: #${file.nameWithoutExtension} OK — ftyp+moov+mdat present (${fileSize / 1024} KB)")
            } else {
                AppLog.w(TAG, "isValidMp4Box: #${file.nameWithoutExtension} INVALID — " +
                    "ftyp=$hasFtyp moov=$hasMoov mdat=$hasMdat (${fileSize / 1024} KB)")
            }
            ok
        } catch (e: Exception) {
            AppLog.w(TAG, "isValidMp4Box: #${file.nameWithoutExtension} exception: ${e.message}")
            false
        }
    }

    /**
     * Fix #165: Публичный метод проверки валидности кэша (для UI / PlayerConnection).
     * Возвращает true если файл скачан И прошёл структурную валидацию.
     * Не делает File.exists() если state не COMPLETED (O(1) StateFlow lookup).
     *
     * Fix #166: если есть .sha256 sidecar и хеш НЕ совпадает — тоже невалиден.
     * Двухуровневая проверка:
     *   1. Структурная (MPEG-TS sync bytes) — Fix #165
     *   2. SHA-256 целостность — Fix #166 (если sidecar есть)
     *   3. Fix #SIREN-PLAYBACK: для .m4a — проверка аудио-параметров через MediaExtractor
     *      (sample rate 44100, stereo, duration > 0). Битые Siren-транскоды
     *      отбрасываются — they have valid MP4 structure but garbage audio.
     */
    fun isCacheValid(trackId: Long): Boolean {
        if (!isDownloaded(trackId)) return false
        val file = getLocalFile(trackId) ?: return false
        val ext = file.extension.lowercase()
        if (!isValidAudioFile(file, ext)) return false
        // Fix #SIREN-PLAYBACK: для .m4a — проверяем аудио-параметры.
        // Битые Siren-транскоды имеют ftyp+moov+mdat (проходят isValidMp4Box)
        // но sample-rate=88200 вместо 44100 → ExoPlayer produce garbage.
        if (ext == "m4a" && !isM4aAudioParamsValid(file)) {
            AppLog.w(TAG, "isCacheValid: #$trackId — M4A has invalid audio params (Siren transcode artifact)")
            return false
        }
        // Fix #166: дополнительная проверка SHA-256 если sidecar есть.
        // NO_HASH (старая загрузка без sidecar) — считаем валидным
        // (структурная проверка уже прошла). CORRUPTED — невалиден.
        val integrity = verifyCacheIntegrity(trackId)
        return integrity != CacheIntegrity.CORRUPTED
    }

    /**
     * Fix #SIREN-PLAYBACK: проверка аудио-параметров M4A файла.
     * Вызывается только из isCacheValid (не на hot path воспроизведения).
     * Возвращает false если sample-rate != 44100 ±100 или channel-count != 2
     * или duration <= 0 (признаки битого Siren-транскода).
     */
    private fun isM4aAudioParamsValid(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    val sr = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { -1 }
                    val ch = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { -1 }
                    val dur = try { format.getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { -1L }
                    val ok = sr in 44000..44200 && ch == 2 && dur > 0
                    if (!ok) {
                        AppLog.w(TAG, "isM4aAudioParamsValid: ${file.name} — sr=$sr ch=$ch dur=$dur → INVALID")
                    }
                    return ok
                }
            }
            // Нет audio track — невалидный M4A
            AppLog.w(TAG, "isM4aAudioParamsValid: ${file.name} — no audio track found")
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "isM4aAudioParamsValid: ${file.name} — exception: ${e.message}")
            false
        } finally {
            extractor.release()
        }
    }
}
