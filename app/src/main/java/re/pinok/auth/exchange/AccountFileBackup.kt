package re.pinok.auth.exchange

import android.content.Context
import org.json.JSONObject
import re.pinok.util.AppLog
import java.io.File

/**
 * Файловый бэкап аккаунта — VTosters pattern #3 (account.json).
 *
 * `EncryptedSharedPreferences` — единственное хранилище токенов в PinoK, и
 * оно МОЖЕТ повредиться. Известные причины:
 *  - AndroidX Security `MasterKey` теряет ключ после factory reset на
 *    некоторых устройствах Samsung/Xiaomi (Keystore corruption);
 *  - Tink key rotation failure при переходе между версиями AndroidX Security;
 *  - backup/restore через `adb backup` / Google Auto Backup тащит шифртекст
 *    без ключа → prefs навсегда нечитаемы;
 *  - `apply()` flush window: процесс убит до записи на диск → данные
 *    потеряны (PinoK now uses `commit()` для критических writes, но
 *    файловый бэкап остаётся страховкой).
 *
 * Этот модуль пишет plaintext JSON со всеми токенами в:
 *   `<filesDir>/account.json`
 *
 * Файл используется как fallback: если prefs пусты/повреждены,
 * [ExchangeTokenStorage.restoreFromFileBackup] читает этот файл и
 * восстанавливает сессию без полного re-login.
 *
 * **Безопасность.** Файл лежит в `context.filesDir` (app-private, mode 0700,
 * не доступен другим приложениям без root). Уровень защиты идентичен
 * `SharedPreferences` MODE_PRIVATE. `EncryptedSharedPreferences` даёт
 * реальную защиту ТОЛЬКО на rooted устройствах; на нерутованных — избыточна.
 * Платой за надёжность восстановления является хранение токенов в открытом
 * виде в app-private каталоге — приемлемый компромисс, как и в VTosters.
 *
 * **Атомарность.** Пишем в `account.json.tmp` → `renameTo(account.json)`.
 * `rename(2)` на той же файловой системе атомарен POSIX-гарантией →
 * никогда не получим полу-записанный файл даже при kill процессе.
 *
 * **Потокобезопасность.** Все методы synchronized на `lock`. PinoK
 * вызывает `dumpToFile()` из `ExchangeTokenStorage` (который сам не
 * синхронизирован, но вызывается из `refreshMutex.withLock` в refresh-пути
 * и из главного потока в save-пути). Синхронизация здесь — защита от
 * параллельных dump/load.
 */
class AccountFileBackup(private val context: Context) {

    private val lock = Any()

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val tmpFile: File get() = File(context.filesDir, "$FILE_NAME.tmp")

    /**
     * Записать JSON-снимок аккаунта в файл. Атомарно (tmp + rename).
     * Best-effort: логирует warning, но не бросает — сбой бэкапа НЕ должен
     * ломать основной flow записи токена.
     */
    fun save(json: JSONObject) {
        synchronized(lock) {
            try {
                val dir = context.filesDir
                if (!dir.exists() && !dir.mkdirs()) {
                    AppLog.w(TAG, "save: cannot create filesDir=${dir.absolutePath}")
                    return
                }
                tmpFile.writeText(json.toString())
                // rename(2) атомарен на той же ФС. Если renameTo упал
                // (крайне редко — например FS permission), fallback: copy.
                if (!tmpFile.renameTo(file)) {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                }
                AppLog.d(TAG, "save: account snapshot written (${file.length()} bytes)")
            } catch (e: Exception) {
                AppLog.w(TAG, "save failed: ${e.message}")
                // Не бросаем — бэкап вспомогательный
            }
        }
    }

    /**
     * Прочитать JSON-снимок аккаунта. Возвращает null если файла нет,
     * пустой, или JSON невалиден. Best-effort.
     */
    fun load(): JSONObject? {
        synchronized(lock) {
            return try {
                if (!file.exists()) return null
                val text = file.readText()
                if (text.isBlank()) return null
                JSONObject(text)
            } catch (e: Exception) {
                AppLog.w(TAG, "load failed: ${e.message}")
                null
            }
        }
    }

    /** Удалить файл бэкапа (при logout). Best-effort. */
    fun clear() {
        synchronized(lock) {
            try {
                file.delete()
                tmpFile.delete()
            } catch (e: Exception) {
                AppLog.w(TAG, "clear failed: ${e.message}")
            }
        }
    }

    /** Существует ли файл бэкапа. */
    fun exists(): Boolean = synchronized(lock) { file.exists() }

    private companion object {
        private const val TAG = "AccountFileBackup"
        private const val FILE_NAME = "account.json"
    }
}
