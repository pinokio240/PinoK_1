package re.pinok.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Многоуровневый логгер с расширенной диагностикой (v2 — detailed diagnostics).
 *
 * Возможности:
 *  - 5 уровней: V / D / I / W / E (цвета в logcat + в UI)
 *  - Авто-место вызова: `file:line#method` (через проход стека, пропуская фреймы AppLog)
 *  - Имя потока на каждой записи (main / OkHttp… / DefaultDispatcher…)
 *  - Структурированный контекст: key-value пары (ownerId, postId, apiMethod…)
 *  - Полная цепочка Throwable: cause chain + suppressed exceptions
 *  - In-memory ring buffer на [BUFFER_CAPACITY] записей ([LogEntry])
 *  - File persistence с rotation (max [PERSIST_MAX_BYTES])
 *  - Таймирование блоков: [time]
 *  - Логирование VK API: [api] (с маскированием токенов/секретов)
 *  - Детальный экспорт: [exportDetailed] — полный дамп со всеми полями
 *
 * Потокобезопасность: все операции с buffer/persistFile синхронизированы.
 *
 * Обратная совместимость: [snapshot] возвращает строки в прежнем формате
 * `<ts> <LVL>/<PREFIX>/<tag>: <msg>` — просмотрщики логов не требуют изменений.
 * Для расширенного рендеринга использовать [snapshotEntries].
 */
object AppLog {

    private const val PREFIX = "PinoK"

    /** In-memory буфер (most-recent first). Увеличен с 2000 до 4000 для deep debugging. */
    private const val BUFFER_CAPACITY = 4000

    /** Максимум размера файла логов перед rotation (2 MB — было 512 KB). */
    private const val PERSIST_MAX_BYTES = 2 * 1024 * 1024L

    /** Имя файла персистентного лога в cacheDir/logs/. */
    private const val PERSIST_FILE = "persistent.log"

    /** Ключи параметров, значения которых маскируются при логировании API. */
    private val SENSITIVE_KEYS = setOf(
        "access_token", "token", "secret", "sig", "password",
        "captcha_key", "user_secret", "api_key", "client_secret",
    )

    /**
     * Структурированная запись лога. Хранится в in-memory буфере;
     * [snapshot] и [exportDetailed] форматируют её по-разному.
     */
    data class LogEntry(
        val timestamp: Long,
        val level: Int,
        val tag: String,
        val message: String,
        val threadName: String,
        /** `file:line#method` места вызова (например `FeedScreen.kt:142#loadFeed`). */
        val callerLocation: String?,
        /** Опциональный key-value контекст (ownerId, postId, apiMethod…). */
        val context: Map<String, String>?,
        val throwable: Throwable?,
    )

    private val buffer = ArrayDeque<LogEntry>()
    private val bufferLock = Any()

    @Volatile
    private var persistFile: File? = null

    @Volatile
    private var persistWriter: OutputStreamWriter? = null

    private val persistLock = Any()

    /**
     * #LOGCAT-NOISE-FIX (2026-08-03): gate verbose logcat output.
     *
     * В release-сборке (debugBuild == false) DEBUG и VERBOSE логи НЕ
     * пишутся в `adb logcat` — они засоряли logcat «мусором» (per-segment
     * download progress, per-op URL unmask, per-recompose getLocalFile и т.д.).
     * Но ВСЕ записи (включая DEBUG/VERBOSE) по-прежнему попадают в in-memory
     * buffer + persistent.log файл — in-app LogViewer и export работают полноценно.
     *
     * INFO/WARN/ERROR всегда идут в logcat (важные события).
     *
     * Runtime-переключатель: пользователь может включить verbose logcat в
     * Настройки -> Логирование -> «Подробный лог в logcat» (для глубокой
     * отладки через adb). Default = режим сборки хоста (debug-сборка = verbose,
     * release = тихий) — передаётся через [setAppBuildInfo].
     *
     * #ARCH-CONTAINERS (Этап 1.2-а): AppLog живёт в :core:common и больше НЕ читает
     * BuildConfig хоста напрямую — хост (:app, SovaApp.onCreate) передаёт
     * идентификацию сборки вызовом [setAppBuildInfo] ДО первого лог-вызова.
     * До инициализации — безопасные release-дефолты (Application.onCreate
     * стартует раньше любого нашего кода, окно с дефолтами не наблюдаемо).
     */
    @Volatile
    var verboseToLogcat: Boolean = false

    /** Идентификация приложения для заголовков экспорта/persistent.log ("# App:", "# App ID:"). */
    @Volatile
    var appId: String = "re.pinok"

    /** Имя версии для заголовков экспорта/persistent.log ("# Version:"). */
    @Volatile
    var versionName: String = "unknown"

    /** DEBUG-сборка хоста (до Этапа 1.2-а читалось напрямую из BuildConfig.DEBUG в :app). */
    @Volatile
    var debugBuild: Boolean = false

    /**
     * #ARCH-CONTAINERS (Этап 1.2-а): хост (:app) передаёт идентификацию сборки
     * ДО первого лог-вызова (SovaApp.onCreate — раньше эти поля читались
     * напрямую из BuildConfig хоста). Вызывается один раз при старте процесса,
     * до любых вызовов AppLog — поэтому безусловно выставляет и
     * [verboseToLogcat] (прежний default = BuildConfig.DEBUG хоста).
     */
    fun setAppBuildInfo(appId: String, versionName: String, debuggable: Boolean) {
        this.appId = appId
        this.versionName = versionName
        this.debugBuild = debuggable
        verboseToLogcat = debuggable
    }

    /**
     * Runtime-переключатель verbose logcat (вызывается из SettingsScreen LoggingTab).
     *
     * Имя функции НЕ может быть `setVerboseToLogcat` — это сгенерирует JVM-метод
     * `setVerboseToLogcat(Z)V`, который clash'ит с synthetic setter'ом свойства
     * [verboseToLogcat] (Kotlin генерирует `setVerboseToLogcat(Z)V` для `var`).
     * Поэтому функция названа `setVerboseLogcatEnabled` — уникальная JVM-сигнатура.
     */
    fun setVerboseLogcatEnabled(enabled: Boolean) {
        if (verboseToLogcat != enabled) {
            verboseToLogcat = enabled
            // Логируем смену режима на INFO — всегда видно в logcat.
            Log.i("$PREFIX/AppLog", "verboseToLogcat = $enabled " +
                "(DEBUG/VERBOSE logcat output ${if (enabled) "enabled" else "suppressed"})")
        }
    }

    // ─── #LOG-CATEGORIES (2026-08-04): per-category gating ──────────────
    //
    // Пользователь просил в Настройки → Лог добавить тумблеры для выборочного
    // логирования по разделам приложения (Музыка, Сообщения, Лента и т.д.),
    // чтобы убрать «шум» в логе.
    //
    // Архитектура:
    //  - 11 категорий покрывают все 80+ тегов в кодовой базе.
    //  - Mapping tag → category через [categoryForTag] (when по known tags).
    //  - [enabledCategories] — mutable set, default = CRITICAL_CATEGORIES
    //    (#LOG-CATEGORIES-DEFAULT-CRITICAL: AUTH+SYSTEM+NETWORK, остальные ВЫКЛ).
    //  - В [log()]: если категория отключена — пропускаем запись ВООБЩЕ
    //    (buffer + file + logcat). Исключение: WARN/ERROR всегда пишутся
    //    (критичные события нельзя терять при диагностике).
    //  - SovaPrefs.persistLogCategoriesDisabled() хранит множество отключенных
    //    категорий (как JSON array строк) — загружается в SovaApp.onCreate.

    /**
     * Категории логов для per-category gating в Settings → Log.
     *
     * Порядок объявления = порядок отображения в UI (SettingsScreen LoggingTab).
     * Каждая категория покрывает группу связанных тегов — см. [categoryForTag].
     */
    enum class LogCategory(val title: String, val description: String) {
        AUDIO("Музыка и звук",
            "Воспроизведение аудио, загрузка треков, плейлисты, эквалайзер, визуализатор"),
        MESSAGES("Сообщения",
            "Чаты, диалоги, отправка/получение сообщений, вложения, пересылка"),
        FEED("Лента",
            "Посты, комментарии, лайки, репосты, вложения в ленте"),
        AUTH("Авторизация",
            "Вход, OAuth, токены, silent refresh, WebView auth"),
        NETWORK("Сеть и API",
            "VK API запросы, Retrofit, OkHttp, смена сети, NetworkObserver"),
        REALTIME("LongPoll (реалтайм)",
            "LongPoll цикл, push-уведомления в реальном времени, keep-alive сервис"),
        NOTIFICATIONS("Уведомления",
            "NotificationsPoller, VkNotificationsNotifier, RemoteInput, настройки пушей"),
        DOWNLOADS("Загрузки",
            "Video/Audio DownloadManager, DocumentFile SD-card, кэш сегментов"),
        STORIES("Истории и клипы",
            "Stories viewer, Clips feed, StoryOfflinePlayer, жесты"),
        UI("Интерфейс",
            "Навигация, активити, настройки, разрешения, FAB, темы"),
        SYSTEM("Система",
            "SovaApp lifecycle, BootReceiver, Linkify, прочее"),
        CALLS("Звонки",
            "WebRTC, Queuev4, сигналинг, микрофон, динамик"),
    }

    /**
     * Mapping tag → category. Теги не из списка попадают в [LogCategory.SYSTEM].
     *
     * Поддерживается автоматически: новые теги добавлять сюда по мере роста
     * кодовой базы. Если тег не найден — логируется в SYSTEM (видно всегда,
     * если SYSTEM включён).
     */
    private fun categoryForTag(tag: String): LogCategory = when (tag) {
        // AUDIO
        "AudioPlayer", "AudioPlayerScreen", "AudioPicker", "MusicHomeTab",
        "MusicScreen", "MyMusicMenuList", "PlaylistsDialog", "PlaylistAttachment",
        "SpectrumVisualizer", "VoicePlayback", "AudioUrlUnmasker", "SirenTranscoder",
        "EqualizerManager" -> LogCategory.AUDIO

        // MESSAGES
        "ChatDetailScreen", "ChatInfoScreen", "MessagesScreen", "ForwardDialog",
        "PinnedConvsRepo", "ShareSheet", "ShareToChat", "ConversationsRepo",
        "MessagesRepo" -> LogCategory.MESSAGES

        // FEED
        "FeedScreen", "PostDetail", "PostDetailScreen", "CommentsBottomSheet",
        "RepostDialog", "FeedRepository", "WallRepository" -> LogCategory.FEED

        // AUTH
        "AuthActivity", "ExchangeTokenStorage", "VkAuthWebView", "ExchangeAuthRepo",
        "WebTokenAuth", "ExternalBrowserAuth", "AuthViewModel", "VkUrlDeepLinker" -> LogCategory.AUTH

        // NETWORK
        "NetworkObserver", "NetworkSwitchPopup", "VKApiClient", "VKApi",
        "OkHttpInterceptor", "LayerAnonymTokenHandler", "AuthDomainsConfig" -> LogCategory.NETWORK

        // REALTIME
        "LongPollClient", "LongPollKeepAliveService", "RealtimeHub",
        "WebSocketClient" -> LogCategory.REALTIME

        // NOTIFICATIONS
        "NotificationsScreen", "NotificationsTab", "NotificationSettings",
        "NotificationsPoller", "VkNotificationsNotifier", "NotificationActionReceiver",
        "ReplyResultNotifier" -> LogCategory.NOTIFICATIONS

        // DOWNLOADS
        "VideoDownloadsCard", "TrackDownloadManager", "VideoDownloadManager",
        "DocumentFileStorage", "DownloadManager", "AudioCacheManager",
        "VideoCacheManager", "StoryVideoCache" -> LogCategory.DOWNLOADS

        // STORIES
        "StoriesRow", "StoryOfflinePlayer", "StoryViewer", "ClipsRepository",
        "ClipsViewModel", "StoryCache" -> LogCategory.STORIES

        // UI
        "SovaNavHost", "Settings", "LogScreen", "LogExport", "PermissionManager",
        "DraggableLogFab", "BugReport", "ThemeManager", "BottomNav" -> LogCategory.UI

        // CALLS
        // #CALLS-ACK-REOFFER (2026-08-29): "CallSignaling" добавлен (раньше падал в
        // else → SYSTEM). Категория CALLS принудительно включается в SovaApp.startCallSignaling.
        "WebRtcEngine", "Queuev4Client", "CallScreen", "CallsHistory", "CallSignaling" -> LogCategory.CALLS

        // SYSTEM
        "SovaApp", "MainActivity", "BootReceiver", "Linkify", "AppLog",
        "CrashHandler", "WorkManager", "PrefsMigration" -> LogCategory.SYSTEM

        else -> LogCategory.SYSTEM
    }

    /**
     * #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): критичные категории логов,
     * которые включены по умолчанию. Остальные выключены (молчаливый дефолт).
     *
     * Пользователь: «по умолчанию в логирование надо включить только критические
     * источники, остальные выключить».
     *
     * Критичные = AUTH (авторизация/токены), SYSTEM (lifecycle/crash),
     * NETWORK (VK API/смена сети) — минимум для диагностики проблем входа и
     * сети. UI/FEED/AUDIO/MESSAGES/STORIES/DOWNLOADS/REALTIME/NOTIFICATIONS
     * выключены по умолчанию (шум), пользователь может включить вручную в
     * Настройки→Лог.
     *
     * Тот же набор что в SettingsScreen.kt кнопка «Только критичные».
     */
    val CRITICAL_CATEGORIES: Set<LogCategory> = setOf(
        LogCategory.AUTH,
        LogCategory.SYSTEM,
        LogCategory.NETWORK,
    )

    /**
     * Имена НЕ-критичных категорий (для SovaPrefs default logCategoriesDisabled).
     * SovaPrefs не ссылается на LogCategory напрямую чтобы не тащить зависимость
     * от enum — использует строковые имена.
     */
    val NON_CRITICAL_CATEGORY_NAMES: Set<String> =
        (LogCategory.values().toSet() - CRITICAL_CATEGORIES).map { it.name }.toSet()

    /**
     * Активные категории. Default = только критичные (#LOG-CATEGORIES-DEFAULT-CRITICAL).
     * SovaApp.onCreate перезапишет из prefs (applyDisabledCategories).
     * Изменяется через [setCategoryEnabled] из SettingsScreen.
     */
    @Volatile
    private var enabledCategories: Set<LogCategory> = CRITICAL_CATEGORIES

    /**
     * Включить/выключить категорию логов.
     * Вызывается из SettingsScreen LoggingTab. Потокобезопасно (@Volatile + copy set).
     */
    fun setCategoryEnabled(category: LogCategory, enabled: Boolean) {
        val current = enabledCategories
        val newSet = if (enabled) current + category else current - category
        if (newSet != current) {
            enabledCategories = newSet
            Log.i("$PREFIX/AppLog", "LogCategory.$category = $enabled " +
                "(now ${newSet.size}/${LogCategory.values().size} categories enabled)")
        }
    }

    /** Проверить, включена ли категория. Для UI (SettingsScreen). */
    fun isCategoryEnabled(category: LogCategory): Boolean = category in enabledCategories

    /**
     * Загрузить состояние категорий из SovaPrefs при старте приложения.
     * Вызывается из SovaApp.onCreate после инициализации prefs.
     *
     * @param disabledCategories множество имён категорий (LogCategory.name),
     *        которые пользователь отключил. Default в SovaPrefs =
     *        NON_CRITICAL_CATEGORY_NAMES (только критичные включены).
     */
    fun applyDisabledCategories(disabledCategories: Set<String>) {
        val allCats = LogCategory.values().toSet()
        val disabled = disabledCategories.mapNotNull { name ->
            runCatching { LogCategory.valueOf(name) }.getOrNull()
        }.toSet()
        enabledCategories = allCats - disabled
        Log.i("$PREFIX/AppLog", "applyDisabledCategories: ${disabled.size} disabled " +
            "(${disabled.joinToString(",") { it.name }}) — " +
            "${enabledCategories.size}/${allCats.size} categories enabled")
    }

    private val isoFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    // ─── Public API: базовые уровни логирования ──────────────────────────

    fun v(tag: String, msg: String) = log(Log.VERBOSE, tag, msg, null, null)
    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg, null, null)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg, null, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = log(Log.WARN, tag, msg, t, null)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(Log.ERROR, tag, msg, t, null)

    // ─── Перегрузки со структурированным контекстом ───────────────────────

    /** Debug-лог со структурированным контекстом (key-value). */
    fun d(tag: String, msg: String, context: Map<String, String>) =
        log(Log.DEBUG, tag, msg, null, context)

    /** Info-лог со структурированным контекстом (key-value). */
    fun i(tag: String, msg: String, context: Map<String, String>) =
        log(Log.INFO, tag, msg, null, context)

    /** Warn-лог со структурированным контекстом (key-value). */
    fun w(tag: String, msg: String, context: Map<String, String>, t: Throwable? = null) =
        log(Log.WARN, tag, msg, t, context)

    /** Error-лог со структурированным контекстом (key-value). */
    fun e(tag: String, msg: String, context: Map<String, String>, t: Throwable? = null) =
        log(Log.ERROR, tag, msg, t, context)

    // ─── Специализированные хелперы ───────────────────────────────────────

    /**
     * Замеряет длительность блока [block] и логирует результат (DEBUG).
     * При исключении внутри блока — логирует ERROR с длителькой и пробрасывает исключение.
     *
     * Пример:
     * ```
     * val posts = AppLog.time("FeedScreen", "loadFeed") { api.wallGet(...) }
     * ```
     */
    inline fun <T> time(tag: String, label: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            val result = block()
            val ms = (System.nanoTime() - start) / 1_000_000.0
            d(tag, "⏱ $label done in ${"%.2f".format(ms)}ms")
            result
        } catch (t: Throwable) {
            val ms = (System.nanoTime() - start) / 1_000_000.0
            e(tag, "⏱ $label FAILED in ${"%.2f".format(ms)}ms", t)
            throw t
        }
    }

    /**
     * Логирование VK API запроса с маскированием чувствительных параметров.
     *
     * Используется из [re.pinok.api.VKApiClient.callInternal] для единообразной
     * трассировки всех запросов: метод, параметры, длительность, статус, ошибки.
     *
     * Параметры [params] маскируются через [maskParams] (token, secret, sig…).
     */
    fun api(
        tag: String = "VKApi",
        method: String,
        params: Map<String, String> = emptyMap(),
        direction: ApiDirection,
        durationMs: Long? = null,
        httpStatus: Int? = null,
        apiCode: Int? = null,
        bodySize: Int? = null,
        error: Throwable? = null,
    ) {
        val masked = maskParams(params)
        val ctx = buildMap {
            put("method", method)
            if (params.isNotEmpty()) put("params", masked)
            durationMs?.let { put("durationMs", it.toString()) }
            apiCode?.let { put("apiCode", it.toString()) }
            httpStatus?.let { put("http", it.toString()) }
            bodySize?.let { put("bytes", it.toString()) }
        }
        val arrow = when (direction) {
            ApiDirection.REQUEST -> "→"
            ApiDirection.RESPONSE_OK -> "←"
            ApiDirection.RESPONSE_ERR -> "✗"
            ApiDirection.NETWORK_FAIL -> "✗NET"
        }
        val msg = buildString {
            append("$arrow $method")
            if (params.isNotEmpty()) append(" $masked")
            durationMs?.let { append(" ${it}ms") }
            httpStatus?.let { append(" HTTP $it") }
            bodySize?.let { append(" ${it}B") }
            apiCode?.let { append(" err=$it") }
        }
        when (direction) {
            ApiDirection.REQUEST -> d(tag, msg, ctx)
            ApiDirection.RESPONSE_OK -> d(tag, msg, ctx)
            ApiDirection.RESPONSE_ERR -> e(tag, msg, ctx, error)
            ApiDirection.NETWORK_FAIL -> e(tag, msg, ctx, error)
        }
    }

    /** Направление VK API лог-события. */
    enum class ApiDirection { REQUEST, RESPONSE_OK, RESPONSE_ERR, NETWORK_FAIL }

    // ─── P4.1/P4.2: LongPoll-specific helpers ───────────────────────────

    /**
     * P4.1/P4.2: Логирование LongPoll-цикла с structured context.
     *
     * Используется из [re.pinok.realtime.LongPollClient] для единообразной
     * трассировки: версия LP (3/14), ts, pts, mode, failed code, кол-во events,
     * длительность wait'а.
     *
     * @param tag лог-тег (обычно "LongPollClient")
     * @param phase фаза цикла: "server-fetch" / "poll-start" / "poll-response" /
     *        "backfill-start" / "backfill-replay" / "backfill-done" / "failed" / "reconnect"
     * @param level V/D/I/W/E (по умолчанию I)
     * @param fields key-value контекст (ts, pts, version, mode, failed, events, ms, delta...)
     */
    fun lp(
        tag: String = "LongPollClient",
        phase: String,
        level: Int = Log.INFO,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        val ctx = fields.entries
            .filter { it.value != null }
            .associate { it.key to it.value.toString() }
        val msg = buildString {
            append("[LP:$phase]")
            fields.forEach { (k, v) ->
                if (v != null) {
                    val display = when (v) {
                        is Float, is Double -> "%.2f".format(v)
                        is Long, is Int -> v.toString()
                        else -> v.toString()
                    }
                    append(" $k=$display")
                }
            }
        }
        log(level, tag, msg, throwable, ctx)
    }

    /**
     * P4.2: Логирование backfill-операции (восстановление пропущенных событий).
     *
     * @param stage "start" / "skip-no-flag" / "skip-no-state" / "skip-up-to-date" /
     *        "fetch" / "fetch-failed" / "replay" / "replay-event" / "persist" / "done" /
     *        "failed1-start" / "failed1-done"
     * @param savedPts pts сохранённый в prefs (точка отсчёта backfill)
     * @param currentPts актуальный pts от VK
     * @param eventsCount кол-во восстановленных событий
     * @param messagesCount кол-во сообщений в ответе
     * @param durationMs длительность операции (для perf tracking)
     * @param error опциональная ошибка
     */
    fun backfill(
        tag: String = "LongPollClient",
        stage: String,
        savedPts: Long? = null,
        currentPts: Long? = null,
        eventsCount: Int? = null,
        messagesCount: Int? = null,
        conversationsCount: Int? = null,
        durationMs: Long? = null,
        error: Throwable? = null,
    ) {
        val fields = buildMap {
            put("stage", stage)
            savedPts?.let { put("savedPts", it.toString()) }
            currentPts?.let { put("currentPts", it.toString()) }
            eventsCount?.let { put("events", it.toString()) }
            messagesCount?.let { put("msgs", it.toString()) }
            conversationsCount?.let { put("convs", it.toString()) }
            durationMs?.let { put("ms", it.toString()) }
            savedPts?.let { sp ->
                currentPts?.let { cp ->
                    if (cp > sp) put("delta", (cp - sp).toString())
                }
            }
        }
        val level = when {
            error != null -> Log.WARN
            stage.contains("failed") || stage.contains("skip") -> Log.DEBUG
            stage == "done" || stage == "replay" -> Log.INFO
            else -> Log.INFO
        }
        val msg = buildString {
            append("[BACKFILL:$stage]")
            fields.forEach { (k, v) -> append(" $k=$v") }
        }
        log(level, tag, msg, error, fields)
    }

    /**
     * P4.4: Логирование execute (VKScript) вызова.
     *
     * @param stage "request" / "response-ok" / "response-partial" / "response-err" /
     *        "script-empty"
     * @param scriptLength длина VKScript (символов) — сам скрипт НЕ логируем
     *        целиком (может содержать PII), только длину и первые 80 символов
     * @param scriptPreview первые 80 символов скрипта (для debugging)
     * @param executeErrorsCount кол-во execute_errors (частичный fail)
     * @param durationMs длительность запроса
     * @param bodySize размер ответа в байтах
     */
    fun execute(
        tag: String = "VKApiClient",
        stage: String,
        scriptLength: Int? = null,
        scriptPreview: String? = null,
        executeErrorsCount: Int? = null,
        durationMs: Long? = null,
        bodySize: Int? = null,
        error: Throwable? = null,
    ) {
        val ctx = buildMap {
            put("stage", stage)
            scriptLength?.let { put("scriptLen", it.toString()) }
            executeErrorsCount?.let { put("execErrors", it.toString()) }
            durationMs?.let { put("ms", it.toString()) }
            bodySize?.let { put("bytes", it.toString()) }
        }
        val level = when {
            error != null -> Log.ERROR
            executeErrorsCount != null && executeErrorsCount > 0 -> Log.WARN
            stage == "script-empty" -> Log.WARN
            else -> Log.DEBUG
        }
        val msg = buildString {
            append("[EXEC:$stage]")
            scriptLength?.let { append(" scriptLen=$it") }
            scriptPreview?.let {
                val safe = it.take(80).replace("\n", " ")
                append(" preview=\"$safe\"")
            }
            executeErrorsCount?.let { if (it > 0) append(" execErrors=$it") }
            durationMs?.let { append(" ${it}ms") }
            bodySize?.let { append(" ${it}B") }
        }
        log(level, tag, msg, error, ctx)
    }

    /**
     * P4.3: Логирование WebSocket-событий (для ChannelWebSocketClient).
     *
     * @param stage "stub-start" / "stub-stop" / "stub-subscribe" / "stub-unsubscribe" /
     *        "ws-open" / "ws-message" / "ws-closed" / "ws-failure" / "ws-ping" / "ws-pong"
     * @param peerId опциональный peer (для subscribe/unsubscribe)
     * @param code WebSocket close code (1000/1001/...)
     * @param bytes размер message (без содержимого — privacy)
     */
    fun ws(
        tag: String = "ChannelWSClient",
        stage: String,
        peerId: Long? = null,
        code: Int? = null,
        bytes: Int? = null,
        error: Throwable? = null,
    ) {
        val ctx = buildMap {
            put("stage", stage)
            peerId?.let { put("peerId", it.toString()) }
            code?.let { put("code", it.toString()) }
            bytes?.let { put("bytes", it.toString()) }
        }
        val level = when {
            error != null -> Log.ERROR
            stage.startsWith("ws-closed") || stage.startsWith("ws-failure") -> Log.WARN
            stage.startsWith("stub") -> Log.DEBUG
            else -> Log.INFO
        }
        val msg = buildString {
            append("[WS:$stage]")
            peerId?.let { append(" peerId=$it") }
            code?.let { append(" code=$it") }
            bytes?.let { append(" ${it}B") }
        }
        log(level, tag, msg, error, ctx)
    }

    /**
     * Маскирует чувствительные значения параметров (token, secret, sig…)
     * и обрезает слишком длинные значения. Возвращает строку вида `{k=v, k=v}`.
     */
    fun maskParams(params: Map<String, String>): String {
        if (params.isEmpty()) return "{}"
        return params.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") { (k, v) ->
            val value = when {
                k.lowercase() in SENSITIVE_KEYS || k.contains("token", ignoreCase = true) -> {
                    if (v.length > 8) "${v.take(4)}…${v.takeLast(3)}" else "***"
                }
                v.length > 120 -> "${v.take(120)}…(${v.length})"
                else -> v
            }
            "$k=$value"
        }
    }

    // ─── Внутренняя реализация ────────────────────────────────────────────

    private fun log(
        level: Int,
        tag: String,
        msg: String,
        t: Throwable?,
        context: Map<String, String>?,
    ) {
        // #LOG-CATEGORIES (2026-08-04): per-category gating.
        // Если категория этого тега отключена пользователем в Settings → Log —
        // пропускаем запись ВООБЩЕ (buffer + file + logcat), чтобы убрать «шум».
        // Исключение: WARN/ERROR всегда пишутся — критичные события (краши,
        // ошибки сети, инвалидация токена) нельзя терять при диагностике.
        val category = categoryForTag(tag)
        val categoryEnabled = category in enabledCategories
        if (!categoryEnabled && level != Log.WARN && level != Log.ERROR) {
            return
        }

        val fullTag = "$PREFIX/$tag"
        val levelStr = levelChar(level)
        val ts = System.currentTimeMillis()
        val thread = Thread.currentThread().name
        val caller = callerLocation()
        val entry = LogEntry(ts, level, tag, msg, thread, caller, context, t)

        // In-memory buffer (most-recent first)
        synchronized(bufferLock) {
            buffer.addFirst(entry)
            while (buffer.size > BUFFER_CAPACITY) buffer.removeLast()
        }

        // File persistence (chronological order, с расширенными полями)
        appendToFile(entry, levelStr)

        // Logcat — msg enriched с thread+caller+ctx для удобства в adb logcat
        val logcatMsg = buildString {
            append(msg)
            if (caller != null) {
                append("  [").append(thread).append(" @ ").append(caller).append("]")
            }
            if (context != null && context.isNotEmpty()) {
                append("  ").append(context.entries.joinToString(",", "{", "}") { "${it.key}=${it.value}" })
            }
        }
        // #LOGCAT-NOISE-FIX: DEBUG/VERBOSE пишутся в logcat ТОЛЬКО если
        // verboseToLogcat==true (default = debugBuild хоста). В release-сборке
        // logcat чистый — но buffer + persistent.log содержат всё (для LogViewer).
        // INFO/WARN/ERROR всегда в logcat.
        when (level) {
            Log.VERBOSE -> if (verboseToLogcat) Log.v(fullTag, logcatMsg, t)
            Log.DEBUG -> if (verboseToLogcat) Log.d(fullTag, logcatMsg, t)
            Log.INFO -> Log.i(fullTag, logcatMsg, t)
            Log.WARN -> Log.w(fullTag, logcatMsg, t)
            Log.ERROR -> Log.e(fullTag, logcatMsg, t)
        }
    }

    /**
     * Определяет место вызова лога (`file:line#method`), проходя стек и пропуская
     * все фреймы внутри [AppLog] (включая inline-обёртки time/api/d/i/w/e).
     * Возвращает null, если определить не удалось.
     */
    private fun callerLocation(): String? {
        val stack = Throwable().stackTrace
        for (i in 1 until stack.size) {
            val el = stack[i]
            if (el.className.startsWith("re.pinok.util.AppLog")) continue
            val file = el.fileName ?: "?"
            return "$file:${el.lineNumber}#${el.methodName}"
        }
        return null
    }

    private fun appendToFile(entry: LogEntry, levelStr: String) {
        val writer = persistWriter ?: return
        synchronized(persistLock) {
            try {
                val dateStr = isoFormat.get()?.format(Date(entry.timestamp)) ?: return
                val threadStr = "  [${entry.threadName}]"
                val callerStr = entry.callerLocation?.let { "  @ $it" } ?: ""
                val ctxStr = entry.context?.takeIf { it.isNotEmpty() }?.let {
                    "  " + it.entries.joinToString(",", "{", "}") { (k, v) -> "$k=$v" }
                } ?: ""
                val traceStr = entry.throwable?.let {
                    "\n" + formatThrowable(it).prependIndent("    ")
                } ?: ""
                val line = "$dateStr $levelStr/$PREFIX/${entry.tag}: " +
                    "${entry.message}$threadStr$callerStr$ctxStr$traceStr\n"
                writer.write(line)
                writer.flush()
                // Rotation check
                val file = persistFile
                if (file != null && file.length() > PERSIST_MAX_BYTES) {
                    writer.close()
                    val old = File(file.parentFile, "$PERSIST_FILE.old")
                    old.delete()
                    file.renameTo(old)
                    val newFile = File(file.parentFile, PERSIST_FILE)
                    persistFile = newFile
                    persistWriter = OutputStreamWriter(
                        java.io.FileOutputStream(newFile, true),
                        Charsets.UTF_8,
                    )
                }
            } catch (_: Exception) {
                // Не падаем если файл недоступен — in-memory буфер всё ещё работает
            }
        }
    }

    /**
     * Полное форматирование Throwable: stack trace + cause chain + suppressed.
     * Аналог `Log.getStackTraceString`, но с явным перебором cause и suppressed.
     */
    private fun formatThrowable(t: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println(t.toString())
            t.stackTrace.forEach { pw.println("    at $it") }
            // Cause chain
            var cause: Throwable? = t.cause
            while (cause != null) {
                pw.println("Caused by: $cause")
                cause.stackTrace.forEach { pw.println("    at $it") }
                cause = cause.cause
            }
            // Suppressed exceptions (try-with-resources / addSuppressed)
            if (t.suppressed.isNotEmpty()) {
                pw.println("Suppressed:")
                t.suppressed.forEach { s ->
                    pw.println("  $s")
                    s.stackTrace.forEach { pw.println("    at $it") }
                }
            }
        }
        return sw.toString().trim()
    }

    // ─── Snapshot / Export ────────────────────────────────────────────────

    /**
     * Snapshot in-memory буфера как отформатированные строки (most-recent first).
     * Формат совместим со старым просмотрщиком: `<ts> <LVL>/<PREFIX>/<tag>: <msg>`.
     */
    fun snapshot(): List<String> = synchronized(bufferLock) {
        buffer.map { formatEntrySimple(it) }
    }

    /** Структурированные записи (most-recent first) для расширенного рендеринга. */
    fun snapshotEntries(): List<LogEntry> = synchronized(bufferLock) { buffer.toList() }

    private fun formatEntrySimple(e: LogEntry): String =
        "${e.timestamp} ${levelChar(e.level)}/$PREFIX/${e.tag}: ${e.message}"

    /**
     * Полный детальный экспорт логов (в хронологическом порядке) со ВСЕМИ полями:
     * timestamp, level, thread, caller, message, context, stack trace (с cause chain).
     *
     * Это формат, который отправляется разработчику для глубокой диагностики —
     * подключён к кнопкам экспорта в [LogViewerDialogContent] и [LogScreen].
     */
    fun exportDetailed(): String {
        val entries = synchronized(bufferLock) { buffer.toList() }
        val sb = StringBuilder()
        sb.append("# PinoK detailed log dump — ${Date()}\n")
        sb.append("# App: $appId v$versionName (debug=$debugBuild)\n")
        sb.append("# Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} ")
        sb.append("(Android API ${android.os.Build.VERSION.SDK_INT}, release ${android.os.Build.VERSION.RELEASE})\n")
        sb.append("# Entries: ${entries.size} (chronological order below)\n")
        sb.append("# Fields: timestamp | level/tag | thread | caller | message | context | throwable\n")
        sb.append("\n")
        // buffer хранит most-recent first → реверсим для хронологического порядка
        for (e in entries.asReversed()) {
            val dateStr = isoFormat.get()?.format(Date(e.timestamp)) ?: e.timestamp.toString()
            val lvl = levelChar(e.level)
            sb.append("$dateStr $lvl/${e.tag}")
            sb.append("  [${e.threadName}]")
            e.callerLocation?.let { sb.append("  @ $it") }
            sb.append("\n    ${e.message}")
            e.context?.takeIf { it.isNotEmpty() }?.let {
                sb.append("\n    ctx: ")
                sb.append(it.entries.joinToString(", ") { (k, v) -> "$k=$v" })
            }
            e.throwable?.let {
                sb.append("\n    throwable: ")
                sb.append(formatThrowable(it).replace("\n", "\n    "))
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun levelChar(level: Int): String = when (level) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "?"
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Инициализация файлового лога. Вызывается из [re.pinok.SovaApp.onCreate].
     * Создаёт cacheDir/logs/persistent.log, добавляет заголовок сессии с версией
     * и устройством.
     */
    fun init(context: Context) {
        synchronized(persistLock) {
            if (persistFile != null) return  // уже инициализирован
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            var file = File(dir, PERSIST_FILE)

            // Rotation: если файл больше лимита — переименовываем в .old и начинаем новый.
            if (file.exists() && file.length() > PERSIST_MAX_BYTES) {
                val old = File(dir, "$PERSIST_FILE.old")
                old.delete()
                file.renameTo(old)
                file = File(dir, PERSIST_FILE)  // пересоздаём File object после rename
            }

            persistFile = file
            persistWriter = OutputStreamWriter(
                java.io.FileOutputStream(file, true),
                Charsets.UTF_8,
            )
            // Заголовок сессии
            persistWriter?.apply {
                write("\n# === PinoK session ${Date()} ===\n")
                write("# Version: $versionName (debug=$debugBuild)\n")
                write("# App ID: $appId\n")
                write("# Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                    "(API ${android.os.Build.VERSION.SDK_INT})\n")
                write("# Encoding: UTF-8\n\n")
                flush()
            }
        }
        i("AppLog", "Persistent log initialized: ${persistFile?.absolutePath}")
    }

    /**
     * Returns the persisted log file (chronological order, includes previous sessions).
     * Может быть null если [init] не вызывался.
     */
    fun persistFile(): File? = persistFile

    fun clear() {
        synchronized(bufferLock) { buffer.clear() }
        synchronized(persistLock) {
            try {
                persistWriter?.close()
                persistFile?.delete()
                persistFile?.let { f ->
                    persistWriter = OutputStreamWriter(java.io.FileOutputStream(f, true), Charsets.UTF_8)
                    persistWriter?.write("# Log cleared ${Date()}\n")
                    persistWriter?.flush()
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
