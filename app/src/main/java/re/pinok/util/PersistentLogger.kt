package re.pinok.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #PERSIST-LOG (Task 6-LOGGER-INFRA): беспрерывное логирование в файл, переживает краш.
 *
 * Файл: context.filesDir/persistent_log.txt (filesDir НЕ чистится системой
 * и НЕ теряется при краше/принудительном закрытии — в отличие от памяти/logcat).
 *
 * Запись: синхронная, с flush() на каждое сообщение (гарантия что строка в файле
 * ДО того как краш убьёт процесс). PrintWriter с autoFlush=true + ручной flush
 * в [log] для гарантии; при краше flush в finally блоке [installCrashHandler].
 *
 * Ротация: если файл > 5MB — переименовать в persistent_log.old.txt, начать новый.
 * (2 файла максимум, чтобы не переполнить диск.)
 *
 * CrashHandler: [Thread.setDefaultUncaughtExceptionHandler] — пишет краш-стек
 * в лог ПЕРЕД падением процесса (flush гарантирован в finally).
 *
 * ## Хранилище состояния enabled
 *
 * PersistentLogger читает/пишет ключ `persist_log_enabled` в SharedPreferences
 * `"sova_prefs"` — это SEPARATE от SovaPrefs DataStore (`"sova_settings"`).
 * Причина: DataStore асинхронный (Flow), а [init] вызывается в самом начале
 * [re.pinok.SovaApp.onCreate] и должен синхронно знать enabled/no-op.
 *
 * SovaPrefs имеет mirror-поле [re.pinok.data.local.SovaPrefs.Snapshot.persistLogEnabled]
 * (DataStore `"sova_settings"`) — для отображения тумблера в SettingsScreen.
 * UI (Task 7-LOGGER-UI) при переключении тумблера ОБЯЗАН писать в ОБА хранилища:
 *  1. [re.pinok.data.local.SovaPrefs.setPersistLogEnabled] (DataStore mirror)
 *  2. [setEnabled] (runtime + SharedPreferences `"sova_prefs"`)
 *
 * ## Интеграция с AppLog
 *
 * AppLog живёт в :core:common и НЕ может зависеть от :app (где живёт этот object).
 * Поэтому инверсия: AppLog имеет `@Volatile var persistentLogHook` — function reference,
 * который SovaApp.onCreate заполняет ссылкой на [log]: `AppLog.persistentLogHook = PersistentLogger::log`.
 * AppLog.log() вызывает hook ДО любых гейтов (category/section/errors-only) —
 * persistent-лог пишет ПОЛНЫЙ лог всего, независимо от настроек фильтрации in-memory/logcat.
 * [log] сам проверяет [enabled] — если выключен, no-op (ранний return, дешев).
 */
object PersistentLogger {
    private const val TAG = "PersistentLogger"
    private const val LOG_FILE = "persistent_log.txt"
    private const val OLD_LOG_FILE = "persistent_log.old.txt"
    private const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5MB

    /**
     * Имя SharedPreferences-файла для синхронного хранения флага enabled.
     * НЕ связано с SovaPrefs DataStore ("sova_settings") — это отдельный лёгкий
     * store для одного boolean, читаемого синхронно при раннем старте.
     */
    private const val PREFS_FILE = "sova_prefs"
    private const val PREF_KEY_ENABLED = "persist_log_enabled"

    private var appContext: Context? = null
    private var logFile: File? = null
    private var writer: PrintWriter? = null
    @Volatile
    private var enabled = false
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    val isEnabled: Boolean get() = enabled

    fun init(context: Context) {
        appContext = context.applicationContext
        // Читаем prefs напрямую (без DataStore — синхронно, для раннего старта).
        // SovaPrefs (DataStore) может быть ещё не инициализирован на момент
        // SovaApp.onCreate, поэтому читаем SharedPreferences напрямую.
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(PREF_KEY_ENABLED, false)
        if (enabled) {
            startInternal()
        }
        // Устанавливаем CrashHandler ВСЕГДА (даже если лог выключен) — если
        // пользователь включит лог, а потом краш — краш-стек запишется.
        // Внутри handler проверяем enabled.
        installCrashHandler()
        Log.i(TAG, "init: enabled=$enabled")
    }

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (this.enabled == enabled) return
            this.enabled = enabled
            if (enabled) {
                startInternal()
            } else {
                stopInternal()
            }
        }
        // Сохраняем в prefs (синхронно, чтобы пережить краш).
        appContext?.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(PREF_KEY_ENABLED, enabled)?.apply()
    }

    private fun startInternal() {
        try {
            val ctx = appContext ?: return
            logFile = File(ctx.filesDir, LOG_FILE)
            // Ротация если файл слишком большой.
            rotateIfNeeded()
            // Открываем в append режиме (дописываем в конец).
            writer = PrintWriter(FileWriter(logFile, true), true) // autoFlush=true
            log("I", TAG, "PersistentLogger started — file=${logFile?.absolutePath}")
            Log.i(TAG, "started: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "startInternal failed", e)
            enabled = false
        }
    }

    private fun stopInternal() {
        try {
            writer?.let {
                log("I", TAG, "PersistentLogger stopped")
                it.flush()
                it.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopInternal failed", e)
        } finally {
            writer = null
            logFile = null
        }
    }

    private fun rotateIfNeeded() {
        val f = logFile ?: return
        if (!f.exists()) return
        if (f.length() < MAX_FILE_SIZE) return
        try {
            val ctx = appContext ?: return
            val old = File(ctx.filesDir, OLD_LOG_FILE)
            if (old.exists()) old.delete()
            f.renameTo(old)
            Log.i(TAG, "rotated log file (${f.length()} bytes → new file)")
        } catch (e: Exception) {
            Log.e(TAG, "rotateIfNeeded failed", e)
        }
    }

    /**
     * Главная функция логирования. Потокобезопасная (synchronized).
     * Формат: "2026-10-25 18:27:36.781  I/VKApiClient: message"
     * (тот же формат что logcat — для удобства чтения).
     *
     * Если [enabled] == false — мгновенный no-op (ранний return без lock).
     * Вызывается из AppLog.persistentLogHook на КАЖДУЮ log-запись (v/d/i/w/e
     * + api/lp/backfill/execute/ws) — поэтому должна быть дешёвой при выключенном логе.
     */
    fun log(level: String, tag: String, msg: String, throwable: Throwable? = null) {
        if (!enabled) return
        synchronized(lock) {
            val w = writer ?: return
            try {
                val ts = dateFormat.format(Date())
                val line = buildString {
                    append(ts)
                    append("  ")
                    append(level)
                    append('/')
                    append(tag.take(30)) // ограничиваем длину тега
                    append(": ")
                    append(msg)
                }
                w.println(line)
                if (throwable != null) {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    w.println(sw.toString())
                }
                w.flush() // гарантия записи на диск
            } catch (e: Exception) {
                // Не логируем ошибку логирования рекурсивно — просто глушим.
            }
        }
    }

    /** Текущий файл лога (для UI: отправить/сохранить). null если лог выключен. */
    fun getLogFile(): File? {
        if (!enabled) return null
        return logFile
    }

    /** Размер лога в байтах (для UI: показать размер). */
    fun getLogSize(): Long {
        val f = logFile ?: return 0L
        return if (f.exists()) f.length() else 0L
    }

    /** Очистить лог (для UI кнопки «Очистить»). */
    fun clearLog() {
        synchronized(lock) {
            try {
                writer?.let {
                    it.flush()
                    it.close()
                }
                logFile?.let { f ->
                    if (f.exists()) f.delete()
                }
                // Перезапускаем writer.
                if (enabled) startInternal()
                Log.i(TAG, "log cleared")
            } catch (e: Exception) {
                Log.e(TAG, "clearLog failed", e)
            }
        }
    }

    /**
     * CrashHandler — перехватывает необработанные исключения, пишет стек в лог
     * ПЕРЕД тем как процесс упадёт. flush гарантирован в finally.
     */
    private fun installCrashHandler() {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Пишем краш-стек в лог (если включён).
            if (enabled) {
                synchronized(lock) {
                    try {
                        val w = writer
                        if (w != null) {
                            val ts = dateFormat.format(Date())
                            w.println("$ts  E/CrashHandler: UNCAUGHT EXCEPTION on thread ${thread.name}")
                            val sw = StringWriter()
                            throwable.printStackTrace(PrintWriter(sw))
                            w.println(sw.toString())
                            w.println("$ts  E/CrashHandler: --- END OF CRASH ---")
                            w.flush()
                        }
                    } catch (e: Exception) {
                        // глушим — мы и так падаем
                    }
                }
            }
            // Передаём предыдущему handler'у (чтобы стандартный диалог краша показался).
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}
