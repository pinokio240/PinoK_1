package re.pinok.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.BuildConfig
import re.pinok.BuildStamp
import re.pinok.SovaApp
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fix #391 #IN-APP-UPDATER (волна 29-i): in-app updater поверх git-манифеста.
 *
 * ЗАЧЕМ ТАК: приложение распространяется вне сторов (APK из репозитория), поэтому
 * Google Play update-механика недоступна. Единственный постоянный, публичный и
 * версионированный источник правды — сам git-репозиторий: файл version.json в
 * корне ветки PinoK читается через raw.githubusercontent.com. Публикация новой
 * версии = git push (см. docs/UPDATER.md) — никакого бэкенда не нужно.
 *
 * МЕХАНИКА:
 *  1. checkForUpdate() → GET version.json (таймаут 10с) → Gson-парсинг →
 *     max(versionCode) сравнивается с BuildConfig.VERSION_CODE →
 *     UpToDate / Available / Error (честные состояния, без догадок).
 *  2. downloadApk(info) → OkHttp-стрим в getExternalFilesDir(null)/updates/
 *     (tmp + rename — паттерн TrackDownloadManager, Fix #39/#147: обрезанный
 *     файл не маскируется под успешный). Прогресс 0..100 по content-length.
 *     sha256 из манифеста (если задан) сверяется ПОСЛЕ скачивания — несовпадение
 *     = файл удалён + Error (защита от битой/подменённой сборки).
 *  3. Кэш: если APK этой версии уже скачан и sha256 сходится (или не задан) —
 *     состояние Downloaded сразу, без повторного трафика.
 *  4. installApk(file) → FileProvider (authority re.pinok.fileprovider, путь
 *     external-files-path — уже объявлен в манифесте для лог-экспорта) →
 *     ACTION_VIEW application/vnd.android.package-archive. Если установка из
 *     неизвестных источников не разрешена (API 26+) → честно открываем
 *     ACTION_MANAGE_UNKNOWN_APP_SOURCES, НЕ крашимся.
 *
 * ВОЛНА 43 (docs/UPDATER-PLAN.md, волна A):
 *  - #UPDATER-SOURCE: источник манифеста ПЕРЕОПРЕДЕЛЯЕТСЯ в настройках
 *    (SovaPrefs.update_manifest_url, пусто = DEFAULT_MANIFEST_URL) + опциональный
 *    Bearer-токен приватного репозитория (уходит ТОЛЬКО по https).
 *  - #UPDATER-ETAG: условный GET (If-None-Match → 304 = ноль тела и парсинга).
 *  - #UPDATER-AUTOCHECK: автопроверка при запуске (тумблер, default ВЫКЛ —
 *    философия «ноль фонового трафика без ведома юзера» сохранена) и при первом
 *    входе во вкладку «Обновления» за сессию; троттлинг 6ч + бэкофф при сбоях;
 *    ручная кнопка всегда форсирует.
 *  - «Пропустить эту версию» (баннер её скрывает) + testManifestUrl для кнопки
 *    «Проверить ссылку» (состояние updater'а не трогает).
 *
 * ОТКАТ (даунгрейд): версии с versionCode < текущей скачиваются и ставятся той
 * же цепочкой. Android НЕ ставит более старую версию поверх новой
 * (INSTALL_FAILED_VERSION_DOWNGRADE) без удаления приложения — UI предупреждает
 * об этом честно (AlertDialog в UpdateTab), данные приложения (сессия, кэши)
 * при удалении теряются. Это ограничение ОС, updater обойти его не может.
 *
 * NULL-ЯВНО: поля data-классов nullable (Gson через Unsafe-allocation кладёт null
 * в non-null поля при отсутствии ключа в JSON) — потребители обязаны orEmpty()/if.
 * В файле запрещены !!, ?., ?: — только явные if + захват val.
 */
object UpdaterManager {

    private const val TAG = "UpdaterManager"

    /** Дефолтный манифест версий: raw-файл в корне ветки PinoK репозитория PinoK_1.
     *  #UPDATER-SOURCE (волна 43): переопределяется настройкой update_manifest_url —
     *  см. currentManifestUrl() (пустая настройка = этот дефолт, без миграций). */
    const val DEFAULT_MANIFEST_URL =
        "https://raw.githubusercontent.com/pinokio240/PinoK_1/PinoK/version.json"

    /** #UPDATER-AUTOCHECK: автопроверка (запуск приложения) не чаще раза в 6 часов. */
    private const val AUTO_MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L

    /** Подкаталог external-files (совпадает с FileProvider external-files-path). */
    private const val UPDATES_DIR = "updates"

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Собственный scope: state-машина живёт дольше любого compose-экрана (Pager dispose). */
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    /** #UPDATER-AUTOCHECK: счётчик неудач автопроверок подряд (бэкофф, in-memory). */
    @Volatile
    private var autoFailsInRow = 0

    /** #UPDATER-AUTOCHECK M2: авто-проверка при входе во вкладку — раз за процесс. */
    @Volatile
    private var tabAutoCheckDone = false

    private val _manifest = MutableStateFlow<UpdateManifest?>(null)

    /** Последний успешно загруженный манифест (для списка версий и статусов). */
    val manifest: StateFlow<UpdateManifest?> = _manifest

    private val _state = MutableStateFlow<UpdaterUiState>(UpdaterUiState.Idle)

    /** Жизненный цикл: проверка → доступность → скачивание → готовность/ошибка. */
    val state: StateFlow<UpdaterUiState> = _state

    /**
     * Ленивая инициализация контекстом (UpdateTab вызывает при композиции;
     * повторные вызовы безобидны — перезаписывают тот же applicationContext).
     */
    fun ensureInit(context: Context): UpdaterManager {
        appContext = context.applicationContext
        return this
    }

    /** Ручная проверка (кнопка во вкладке): всегда форсирует сетевой запрос. */
    fun checkForUpdate() {
        runCheck(manual = true)
    }

    /**
     * #UPDATER-AUTOCHECK M2 (волна 43): первое открытие вкладки «Обновления»
     * за сессию процесса — тихая авто-проверка (юзер сам пришёл — намерение
     * очевидно). Троттлинга по prefs НЕ требует: раз за процесс + условный
     * GET (ETag) = дёшево. Ручная кнопка при этом остаётся всегда доступной.
     */
    fun maybeCheckOnTabOpen() {
        if (tabAutoCheckDone) return
        tabAutoCheckDone = true
        val snap = prefsSnapshotOrNull()
        if (snap != null && snap.updateLastCheckMs > 0L) {
            val sinceMs = System.currentTimeMillis() - snap.updateLastCheckMs
            // Свежая проверка (< 15 мин) — не дёргаем raw повторно: манифест
            // меняется только git push'ем, чаще проверять бессмысленно.
            if (sinceMs < 15L * 60L * 1000L) {
                AppLog.i(TAG, "tabOpen: проверка была " + (sinceMs / 60000L) + " мин назад — пропускаю")
                return
            }
        }
        runCheck(manual = false)
    }

    /**
     * #UPDATER-AUTOCHECK M3 (волна 43): автопроверка при запуске приложения.
     * Вызывается из MainActivity через ~12 с после старта. Три гейта:
     * тумблер (default ВЫКЛ), троттлинг 6ч по prefs + бэкофф при сбоях,
     * офлайн/занятость — тихий выход. Ни тостов, ни уведомлений при сбое:
     * результат увидит только баннер (Available) или вкладка.
     */
    fun maybeAutoCheckOnStart() {
        val app = SovaApp.getOrNull()
        if (app == null) {
            AppLog.i(TAG, "autoCheck: SovaApp ещё не создан — skip")
            return
        }
        val snap = prefsSnapshotOrNull()
        if (snap == null) {
            AppLog.i(TAG, "autoCheck: снапшот prefs ещё не готов — skip")
            return
        }
        if (!snap.updateAutostartCheck) {
            AppLog.i(TAG, "autoCheck: выключен в настройках — skip")
            return
        }
        val backoffMult = autoFailsInRow.toLong().coerceAtMost(4L)
        val sinceMs = System.currentTimeMillis() - snap.updateLastCheckMs
        if (snap.updateLastCheckMs > 0L && sinceMs < AUTO_MIN_INTERVAL_MS * backoffMult) {
            AppLog.i(TAG, "autoCheck: недавно проверялись (" + (sinceMs / 60000L) + " мин назад, fails=" + autoFailsInRow + ") — skip")
            return
        }
        if (app.networkObserver.isOffline()) {
            AppLog.i(TAG, "autoCheck: офлайн — skip (проверю при следующем запуске)")
            return
        }
        AppLog.i(TAG, "autoCheck: запускаю тихую проверку (fails=" + autoFailsInRow + ")")
        runCheck(manual = false)
    }

    /**
     * Общий ход проверки. manual=true — кнопка (ошибки показываются как есть);
     * manual=false — тихая (ошибки тоже честно пишутся в состояние — их увидит
     * только открывший вкладку, НО авто-счётчик неудач растёт → бэкофф).
     */
    private fun runCheck(manual: Boolean) {
        val current = _state.value
        // Guard: не вмешиваемся в активную проверку/загрузку — состояние нельзя
        // перезаписывать Checking'ом, иначе прогресс скачивания молча исчезнет.
        if (current is UpdaterUiState.Checking) return
        if (current is UpdaterUiState.Downloading) return
        // ETag-условный GET имеет смысл только когда манифест уже в памяти:
        // после перезапуска процесса 304 без тела оставил бы вкладку пустой.
        val etag = if (_manifest.value == null) "" else savedEtag()
        managerScope.launch {
            _state.value = UpdaterUiState.Checking
            when (val result = fetchManifest(currentManifestUrl(), currentToken(), etag)) {
                is UpdateCheckResult.Success -> {
                    _manifest.value = result.manifest
                    persistCheckState(result.etag)
                    autoFailsInRow = 0
                    decideLatest(result.manifest)
                }
                is UpdateCheckResult.NotModified -> {
                    // 304: сервер подтвердил «не менялся» — ноль тела/парсинга.
                    persistCheckState(savedEtag())
                    autoFailsInRow = 0
                    val cached = _manifest.value
                    if (cached == null) {
                        _state.value = UpdaterUiState.Idle
                    } else {
                        decideLatest(cached)
                    }
                }
                is UpdateCheckResult.Offline -> {
                    if (manual) {
                        _state.value = UpdaterUiState.Error(
                            "Сеть недоступна — проверьте подключение и повторите",
                        )
                    } else {
                        autoFailsInRow++
                        _state.value = UpdaterUiState.Idle
                        AppLog.i(TAG, "autoCheck: офлайн (" + autoFailsInRow + " подряд) — тихо, состояние сброшено")
                    }
                }
                is UpdateCheckResult.HttpError -> {
                    autoFailsInRow++
                    // Честная диагностика 404: raw.githubusercontent отдаёт 404,
                    // когда репозиторий приватный или version.json ещё не запушен.
                    if (manual) {
                        if (result.code == 404) {
                            _state.value = UpdaterUiState.Error(
                                "Манифест недоступен: репозиторий приватный или version.json отсутствует",
                            )
                        } else {
                            _state.value = UpdaterUiState.Error(
                                "Сервер ответил HTTP " + result.code + " — повторите позже",
                            )
                        }
                    } else {
                        _state.value = UpdaterUiState.Idle
                        AppLog.i(TAG, "autoCheck: HTTP " + result.code + " (" + autoFailsInRow + " подряд) — тихо")
                    }
                }
                is UpdateCheckResult.ParseError -> {
                    autoFailsInRow++
                    if (manual) {
                        _state.value = UpdaterUiState.Error(
                            "Ошибка разбора version.json: " + result.message,
                        )
                    } else {
                        _state.value = UpdaterUiState.Idle
                        AppLog.i(TAG, "autoCheck: не разобрал манифест (" + autoFailsInRow + " подряд) — тихо")
                    }
                }
            }
        }
    }

    /**
     * Сравнение манифеста с установленной сборкой (общее для 200 и 304):
     * max(versionCode) без зависимости от порядка записей.
     *  - новее → Available (баннер/вкладка);
     *  - та же версия, штамп сборки в манифесте отличается → UpToDate с пометкой
     *    (пересборка того же versionCode — не ошибка);
     *  - на источнике нет сборок новее установленной → UpToDate с честной пометкой
     *    (кастомный источник/форк отстаёт — это НЕ сбой).
     */
    private fun decideLatest(manifest: UpdateManifest) {
        val versions = manifest.versions
        val latest = versions.maxByOrNull { it.versionCode }
        if (latest == null) {
            _state.value = UpdaterUiState.Error("Манифест не содержит ни одной версии")
            return
        }
        val currentCode: Long = BuildConfig.VERSION_CODE.toLong()
        if (latest.versionCode.toLong() > currentCode) {
            _state.value = UpdaterUiState.Available(latest, manifest)
            return
        }
        if (latest.versionCode.toLong() == currentCode) {
            val manifestStamp = latest.stamp.orEmpty().trim()
            val buildStamp = BuildStamp.STAMP
            if (manifestStamp.isNotEmpty() && buildStamp.isNotEmpty() && manifestStamp != buildStamp) {
                _state.value = UpdaterUiState.UpToDate(
                    BuildConfig.VERSION_NAME,
                    "Версия та же, но штамп сборки в манифесте отличается (манифест: " +
                        manifestStamp + ", сборка: " + buildStamp + ") — вероятно, пересборка того же versionCode.",
                )
                return
            }
            _state.value = UpdaterUiState.UpToDate(BuildConfig.VERSION_NAME)
            return
        }
        // Источник старее установленного (кастомный URL/форк с отставшим манифестом).
        _state.value = UpdaterUiState.UpToDate(
            BuildConfig.VERSION_NAME,
            "На источнике нет сборок новее установленной: новейшая запись versionCode " +
                latest.versionCode + " < текущего " + BuildConfig.VERSION_CODE +
                ". Если это кастомный источник — манифест отстаёт от вашей сборки.",
        )
    }

    // ── #UPDATER-SOURCE: конфигурация из настроек ────────────────────────

    /** Снапшот prefs (может быть null до первого composition — Fix #336 кэш). */
    private fun prefsSnapshotOrNull(): SovaPrefs.Snapshot? {
        val app = SovaApp.getOrNull()
        if (app == null) return null
        val snap = app.prefsSnapshot
        if (snap == null) return null
        return snap
    }

    /** Активный URL манифеста: настройка, а пусто/не готово — встроенный дефолт. */
    fun currentManifestUrl(): String {
        val snap = prefsSnapshotOrNull()
        if (snap == null) return DEFAULT_MANIFEST_URL
        val custom = snap.updateManifestUrl.trim()
        if (custom.isEmpty()) return DEFAULT_MANIFEST_URL
        return custom
    }

    /** Активен ли НЕдефолтный источник (для баннера-предупреждения во вкладке). */
    fun isCustomSource(): Boolean {
        return currentManifestUrl() != DEFAULT_MANIFEST_URL
    }

    private fun currentToken(): String {
        val snap = prefsSnapshotOrNull()
        if (snap == null) return ""
        return snap.updateToken.trim()
    }

    private fun savedEtag(): String {
        val snap = prefsSnapshotOrNull()
        if (snap == null) return ""
        return snap.updateEtag
    }

    /** Персист успешной проверки: время (троттлинг) + ETag условного GET. */
    private suspend fun persistCheckState(etag: String) {
        val app = SovaApp.getOrNull()
        if (app == null) return
        app.prefs.setUpdateLastCheckMs(System.currentTimeMillis())
        if (etag.isNotEmpty()) app.prefs.setUpdateEtag(etag)
    }

    // ── «Пропустить эту версию» + пробная загрузка ссылки ────────────────

    /** «Пропустить эту версию»: баннер её больше не покажет (вкладка — покажет). */
    suspend fun skipVersion(code: Int) {
        val app = SovaApp.getOrNull()
        if (app == null) return
        app.prefs.setUpdateSkippedCode(code)
        AppLog.i(TAG, "skipVersion: " + code)
    }

    /** Отмена «пропустить» (кнопка во вкладке). */
    suspend fun clearSkippedVersion() {
        val app = SovaApp.getOrNull()
        if (app == null) return
        app.prefs.setUpdateSkippedCode(0)
    }

    /** versionCode, скрытый из баннера (0 = ничего не пропущено). */
    fun skippedCode(): Int {
        val snap = prefsSnapshotOrNull()
        if (snap == null) return 0
        return snap.updateSkippedCode
    }

    /**
     * Пробная загрузка ссылки для кнопки «Проверить ссылку» в настройках
     * источника: состояние updater'а НЕ трогает, вердикт — строкой в callback.
     */
    fun testManifestUrl(url: String, token: String, onResult: (String) -> Unit) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) {
            onResult("Ссылка должна начинаться с https:// — незащищённые источники приложение не открывает (cleartext запрещён глобально)")
            return
        }
        managerScope.launch {
            when (val r = fetchManifest(trimmed, token.trim(), "")) {
                is UpdateCheckResult.Success -> {
                    val versions = r.manifest.versions
                    val latest = versions.maxByOrNull { it.versionCode }
                    if (latest == null) {
                        onResult("Манифест загружен, но не содержит ни одной версии")
                    } else {
                        onResult(
                            "OK: версий " + versions.size + ", новейшая " +
                                latest.versionName.orEmpty() + " (versionCode " + latest.versionCode + ")",
                        )
                    }
                }
                is UpdateCheckResult.NotModified ->
                    onResult("OK: источник отвечает «не менялся с прошлой проверки»")
                is UpdateCheckResult.Offline ->
                    onResult("Сеть недоступна — проверьте подключение")
                is UpdateCheckResult.HttpError -> {
                    if (r.code == 404) {
                        onResult("HTTP 404: файл не найден — приватный репозиторий (нужен токен) или опечатка в ссылке")
                    } else {
                        onResult("Сервер ответил HTTP " + r.code)
                    }
                }
                is UpdateCheckResult.ParseError ->
                    onResult("Это не манифест версий: " + r.message)
            }
        }
    }

    /**
     * Скачивание APK версии в updates/. Повторный вызов для той же версии
     * при валидном кэше мгновенно даёт Downloaded (без трафика).
     */
    fun downloadApk(info: UpdateInfo) {
        val current = _state.value
        if (current is UpdaterUiState.Downloading) return
        val url = info.apkUrl.orEmpty()
        if (url.isEmpty()) {
            // Честно: манифест есть, но APK ещё не опубликован — не имитируем загрузку.
            _state.value = UpdaterUiState.Error(
                "APK для версии " + info.versionName.orEmpty() + " ещё не опубликован (apkUrl пуст)",
            )
            return
        }
        managerScope.launch {
            val target = targetFile(info)
            val parent = target.parentFile
            if (parent == null) {
                // Не должно случиться (targetFile всегда кладёт файл в каталог),
                // но честный ранний выход вместо NPE — правило NULL-ЯВНО.
                _state.value = UpdaterUiState.Error("Не удалось определить каталог загрузки APK")
                return@launch
            }
            val tmp = File(parent, target.name + ".tmp")
            try {
                // Кэш-хит: файл уже скачан и целостен → Downloaded сразу.
                if (target.exists()) {
                    val expectedCache = info.sha256.orEmpty().replace(" ", "").lowercase()
                    // || обязателен (не or): он НЕ вычисляет sha256Hex, если хэш не задан —
                    // иначе кэш-хит перемалывает 100+ МБ APK зря.
                    val cachedOk = expectedCache.isEmpty() || (sha256Hex(target) == expectedCache)
                    if (cachedOk) {
                        _state.value = UpdaterUiState.Downloaded(info, target)
                        return@launch
                    }
                    // Кэш битый (обрыв прошлой загрузки до sha-проверки) — удаляем.
                    target.delete()
                }
                // HTTP-стрим (паттерн downloadDirectWithResume TrackDownloadManager:
                // tmp-файл + content-length integrity check, но без Range — APK качаем
                // целиком, Range-resume для установочного пакета не критичен).
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful.not()) {
                        throw IOException("HTTP " + response.code + ": " + response.message)
                    }
                    val body = response.body
                    if (body == null) throw IOException("Пустой ответ сервера")
                    val totalBytes = body.contentLength()
                    var bytesRead = 0L
                    var lastPct = -1
                    body.byteStream().use { input ->
                        FileOutputStream(tmp, false).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (totalBytes > 0) {
                                    val pct = ((bytesRead * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        _state.value = UpdaterUiState.Downloading(info, pct)
                                    }
                                }
                            }
                        }
                    }
                    // Integrity (Fix #147-паттерн): Content-Length обещал больше, чем пришло.
                    if (totalBytes > 0 && bytesRead < totalBytes) {
                        throw IOException(
                            "Обрезанная загрузка: получено " + bytesRead + " из " + totalBytes + " байт",
                        )
                    }
                }
                // sha256 из манифеста — единственная защита от битого/подменённого APK.
                val expected = info.sha256.orEmpty().replace(" ", "").lowercase()
                if (expected.isNotEmpty()) {
                    val actual = sha256Hex(tmp)
                    if (actual != expected) {
                        tmp.delete()
                        throw IOException(
                            "SHA-256 не совпал (ожидалось " + expected.take(12) +
                                "…, получено " + actual.take(12) + "…) — файл удалён",
                        )
                    }
                }
                // Атомарная публикация: tmp → final (rename, fallback copy — как в кодовой базе).
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                _state.value = UpdaterUiState.Downloaded(info, target)
                AppLog.i(TAG, "downloadApk: OK " + target.name + " (" + target.length() + " bytes)")
            } catch (t: Throwable) {
                tmp.delete()
                _state.value = UpdaterUiState.Error("Загрузка не удалась: " + t.message.orEmpty())
                AppLog.w(TAG, "downloadApk failed: " + t.message.orEmpty(), t)
            }
        }
    }

    /**
     * Установка скачанного APK через системный установщик. Синхронный вызов
     * (startActivity), ошибки ловим честно в Error — никаких крэшей без лаунчера.
     */
    fun installApk(file: File) {
        val ctx = appContext
        if (ctx == null) {
            _state.value = UpdaterUiState.Error("Updater не инициализирован контекстом")
            return
        }
        // API 26+: установка из неизвестных источников — per-app permission.
        // Без неё установщик молча отклонит пакет; направляем юзера в настройки
        // и НЕ крашимся (после выдачи разрешения юзер повторит «Установить APK»).
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            AppLog.i(TAG, "installApk: canRequestPackageInstalls=false → открываю настройки unknown-sources")
            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            settingsIntent.data = Uri.parse("package:" + ctx.packageName)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(settingsIntent)
                _state.value = UpdaterUiState.Error(
                    "Разрешите установку из этого источника (экран «Установка неизвестных приложений») и повторите «Установить APK»",
                )
            } catch (t: Throwable) {
                _state.value = UpdaterUiState.Error(
                    "Не удалось открыть настройки установки: " + t.message.orEmpty(),
                )
                AppLog.w(TAG, "installApk: settings intent failed: " + t.message.orEmpty(), t)
            }
            return
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
            AppLog.i(TAG, "installApk: installer launched for " + file.name)
        } catch (t: Throwable) {
            // Нет package-installer (кастомная прошивка) — честное состояние, не крэш.
            _state.value = UpdaterUiState.Error(
                "Системный установщик не найден: " + t.message.orEmpty(),
            )
            AppLog.w(TAG, "installApk: no installer: " + t.message.orEmpty(), t)
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    /**
     * GET манифеста → sealed-результат (сеть/HTTP/парсинг разделены для честных сообщений).
     * Волна 43: параметризован (URL/токен/ETag — #UPDATER-SOURCE/#UPDATER-ETAG);
     * токен уходит ТОЛЬКО по https Bearer-заголовком; непустой etag даёт условный
     * GET (If-None-Match → 304 = NotModified без тела).
     */
    private fun fetchManifest(url: String, token: String, etag: String): UpdateCheckResult {
        val builder = Request.Builder().url(url)
        if (etag.isNotEmpty()) builder.header("If-None-Match", etag)
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer " + token)
        val request = builder.build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                // 304 Not Modified: тело НЕТ — это успех, а не ошибка.
                if (response.code == 304) {
                    return UpdateCheckResult.NotModified
                }
                if (!response.isSuccessful) {
                    return UpdateCheckResult.HttpError(response.code)
                }
                val body = response.body
                if (body == null) return UpdateCheckResult.ParseError("пустой ответ сервера")
                val text = body.string()
                val parsed = gson.fromJson(text, UpdateManifest::class.java)
                if (parsed == null) {
                    UpdateCheckResult.ParseError("JSON не распознан как манифест версий")
                } else {
                    val responseEtag = response.header("ETag")
                    UpdateCheckResult.Success(parsed, responseEtag.orEmpty())
                }
            }
        } catch (e: IOException) {
            // Только сетевые/таймаутные — HTTP-коды обрабатываются выше.
            UpdateCheckResult.Offline
        } catch (e: Exception) {
            // Gson (JsonSyntaxException) и прочие ошибки разбора.
            UpdateCheckResult.ParseError(e.message.orEmpty())
        }
    }

    /** Целевой файл APK: updates/PinoK_<versionName>_<versionCode>.apk. */
    private fun targetFile(info: UpdateInfo): File {
        val ctx = appContext
        val base: File = if (ctx == null) {
            File("/data/local/tmp")
        } else {
            val ext = ctx.getExternalFilesDir(null)
            if (ext == null) ctx.filesDir else File(ext, UPDATES_DIR)
        }
        if (!base.exists()) base.mkdirs()
        val safeName = info.versionName.orEmpty().replace("/", "_")
        return File(base, "PinoK_" + safeName + "_" + info.versionCode + ".apk")
    }

    /** SHA-256 файла → hex (lowercase). Используется и для кэш-валидации, и для проверки скачанного. */
    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest()
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v shr 4, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }
}

/** Одна запись манифеста версий. Поля nullable: Gson кладёт null в missing-ключи (см. KDoc класса). */
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String? = null,
    val stamp: String? = null,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val notes: String? = null,
)

/** Корень version.json: { "versions": [ ... ] }. */
data class UpdateManifest(
    val versions: List<UpdateInfo> = emptyList(),
)

/** Результат fetchManifest — разделён, чтобы UI показывал ПРИЧИНУ, а не общий failure.
 *  Волна 43: + NotModified (304 условного GET), Success несёт ETag ответа для персиста. */
sealed class UpdateCheckResult {
    data class Success(val manifest: UpdateManifest, val etag: String = "") : UpdateCheckResult()
    /** 304 Not Modified — манифест не менялся с последней проверки (ETag совпал). */
    object NotModified : UpdateCheckResult()
    object Offline : UpdateCheckResult()
    data class HttpError(val code: Int) : UpdateCheckResult()
    data class ParseError(val message: String) : UpdateCheckResult()
}

/**
 * UI-состояние вкладки «Обновления». Downloaded несёт info+file — UpdateTab
 * рисует «Установить APK» у конкретной версии (кэш-хит тоже проходит здесь).
 */
sealed class UpdaterUiState {
    object Idle : UpdaterUiState()
    object Checking : UpdaterUiState()
    /** Волна 43: [note] — честная пометка (штамп манифеста отличается / источник
     *  старее установленного — кастомный URL отстаёт); null = обычное «актуальна». */
    data class UpToDate(val currentName: String, val note: String? = null) : UpdaterUiState()
    data class Available(val latest: UpdateInfo, val manifest: UpdateManifest) : UpdaterUiState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdaterUiState()
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdaterUiState()
    data class Error(val message: String) : UpdaterUiState()
}

/**
 * Волна 43 #UPDATER-BANNER: UI-флаг «открыть вкладку Обновлений» (баннер →
 * настройки). Не compose-state — процессный флаг, который SettingsScreen
 * потребляет один раз при composition (consumeOpenRequest) для выбора
 * начальной вкладки. Отдельный объект, чтобы не трогать граф навигации.
 */
object UpdateDeepLink {
    @Volatile
    private var openUpdateTab: Boolean = false

    fun requestOpenUpdateTab() {
        openUpdateTab = true
    }

    /** Прочитать и СБРОСИТЬ запрос (одноразовый). */
    fun consumeOpenRequest(): Boolean {
        val value = openUpdateTab
        openUpdateTab = false
        return value
    }
}
