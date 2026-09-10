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

    /** Манифест версий: raw-файл в корне ветки PinoK репозитория PinoK_1. */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/pinokio240/PinoK_1/PinoK/version.json"

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

    /** Проверка обновления: манифест → max(versionCode) vs BuildConfig.VERSION_CODE. */
    fun checkForUpdate() {
        val current = _state.value
        // Guard: не вмешиваемся в активную проверку/загрузку — состояние нельзя
        // перезаписывать Checking'ом, иначе прогресс скачивания молча исчезнет.
        if (current is UpdaterUiState.Checking) return
        if (current is UpdaterUiState.Downloading) return
        managerScope.launch {
            _state.value = UpdaterUiState.Checking
            when (val result = fetchManifest()) {
                is UpdateCheckResult.Success -> {
                    _manifest.value = result.manifest
                    val versions = result.manifest.versions
                    val latest = versions.maxByOrNull { it.versionCode }
                    if (latest == null) {
                        _state.value = UpdaterUiState.Error("Манифест не содержит ни одной версии")
                    } else {
                        val currentCode: Long = BuildConfig.VERSION_CODE.toLong()
                        if (latest.versionCode.toLong() > currentCode) {
                            _state.value = UpdaterUiState.Available(latest, result.manifest)
                        } else {
                            _state.value = UpdaterUiState.UpToDate(BuildConfig.VERSION_NAME)
                        }
                    }
                }
                is UpdateCheckResult.Offline -> _state.value = UpdaterUiState.Error(
                    "Сеть недоступна — проверьте подключение и повторите",
                )
                is UpdateCheckResult.HttpError -> {
                    // Честная диагностика 404: raw.githubusercontent отдаёт 404,
                    // когда репозиторий приватный или version.json ещё не запушен.
                    if (result.code == 404) {
                        _state.value = UpdaterUiState.Error(
                            "Манифест недоступен: репозиторий приватный или version.json отсутствует",
                        )
                    } else {
                        _state.value = UpdaterUiState.Error(
                            "Сервер ответил HTTP " + result.code + " — повторите позже",
                        )
                    }
                }
                is UpdateCheckResult.ParseError -> _state.value = UpdaterUiState.Error(
                    "Ошибка разбора version.json: " + result.message,
                )
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

    /** GET манифеста → sealed-результат (сеть/HTTP/парсинг разделены для честных сообщений). */
    private fun fetchManifest(): UpdateCheckResult {
        val request = Request.Builder().url(MANIFEST_URL).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
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
                    UpdateCheckResult.Success(parsed)
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

/** Результат fetchManifest — разделён, чтобы UI показывал ПРИЧИНУ, а не общий failure. */
sealed class UpdateCheckResult {
    data class Success(val manifest: UpdateManifest) : UpdateCheckResult()
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
    data class UpToDate(val currentName: String) : UpdaterUiState()
    data class Available(val latest: UpdateInfo, val manifest: UpdateManifest) : UpdaterUiState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdaterUiState()
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdaterUiState()
    data class Error(val message: String) : UpdaterUiState()
}
