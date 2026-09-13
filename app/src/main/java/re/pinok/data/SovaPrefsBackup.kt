package re.pinok.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import re.pinok.data.local.SovaPrefs

/**
 * Волна 45 #SETTINGS-EXPORT: экспорт/импорт ВСЕХ настроек приложения —
 * полный перенос хранилища DataStore (SovaPrefs) в JSON-файл и обратно.
 *
 * ЗАЧЕМ (сценарии):
 *  - откат версии (#UPDATER-ROLLBACK): Android требует удалить приложение,
 *    чтобы поставить более старую, — данные приложения стираются; экспорт —
 *    единственный способ вернуть настройки, локальные закладки треков и
 *    сессию (вход в аккаунт) после переустановки;
 *  - перенос на новое устройство / второе устройство;
 *  - бэкап перед рискованными экспериментами с настройками.
 *
 * ЧТО ВХОДИТ: ВСЕ ключи DataStore (не только те, что в Snapshot) —
 * интерфейс, музыка, звонки, уведомления, логирование, кэш updater'а,
 * локальные закладки треков (track_bookmarks_data), сессия/куки. ЧТО НЕ
 * ВХОДИТ: медиа-кэш и офлайн-загрузки (отдельные файлы, переносятся
 * только перескачиванием) — честно написано в UI вкладки «Данные».
 *
 * ФОРМАТ (format=1, Gson):
 *   {"format":1,"app":"PinoK","appVersion":"2.0.1 (versionCode 2)",
 *    "exportedAt":1234567890,"skippedUnsupported":0,
 *    "keys":[{"name":"имя_ключа","type":"string|boolean|int|long|stringSet",
 *             "value": <скаляр или массив строк>}]}
 *
 * Общие (generic) принципы: тип каждого значения определяется Key-объектом
 * DataStore при экспорте и полем "type" при импорте — НОВЫЕ ключи SovaPrefs
 * попадают в экспорт автоматически, без правок этого объекта (Snapshot —
 * подмножество ключей и НЕ используется ни тут, ни при восстановлении).
 * Неизвестные типы из будущих версий формата честно ПРОПУСКАЮТСЯ (не роняют
 * импорт целиком) — счётчик skippedUnsupported в файле это фиксирует.
 *
 * БЕЗОПАСНОСТЬ: в файле лежат куки/токены сессии — это осознанная фича
 * (иначе перенос входа невозможен), UI обязан предупреждать о секретности
 * файла ДО экспорта. Файл создаётся через SAF — место выбирает юзер.
 */
object SovaPrefsBackup {

    /** Версия формата: при несовместимых изменениях — инкремент (парсер честно отклонит чужие версии). */
    const val FORMAT_VERSION = 1

    /** MIME экспорт-файла для SAF-контракта CreateDocument. */
    const val MIME_JSON = "application/json"

    /** Результат экспорта: готовый JSON + число ключей (для честного тоста). */
    data class Exported(val json: String, val keyCount: Int)

    /**
     * Полный дамп хранилища SovaPrefs в JSON (см. формат в KDoc класса).
     * Значение сериализуется по его РЕАЛЬНОМУ типу в DataStore
     * (string/boolean/int/long/stringSet — других типов Keys не использует);
     * типы вне этого набора честно пропускаются со счётчиком.
     */
    suspend fun export(prefs: SovaPrefs, appVersion: String): Exported {
        val raw = prefs.rawSnapshot()
        val keysArray = JsonArray()
        var skipped = 0
        for ((key, value) in raw) {
            val entry = JsonObject()
            entry.addProperty("name", key.name)
            when (value) {
                is String -> {
                    entry.addProperty("type", "string")
                    entry.addProperty("value", value)
                }
                is Boolean -> {
                    entry.addProperty("type", "boolean")
                    entry.addProperty("value", value)
                }
                is Int -> {
                    entry.addProperty("type", "int")
                    entry.addProperty("value", value)
                }
                is Long -> {
                    entry.addProperty("type", "long")
                    entry.addProperty("value", value)
                }
                is Set<*> -> {
                    entry.addProperty("type", "stringSet")
                    val arr = JsonArray()
                    for (item in value) {
                        val s = item as? String
                        if (s != null) arr.add(s)
                    }
                    entry.add("value", arr)
                }
                else -> {
                    // Типов Keys ровно пять (см. SovaPrefs.Keys); ветка для
                    // устойчивости к будущим типам — не роняем экспорт целиком.
                    skipped++
                    continue
                }
            }
            keysArray.add(entry)
        }
        val root = JsonObject()
        root.addProperty("format", FORMAT_VERSION)
        root.addProperty("app", "PinoK")
        root.addProperty("appVersion", appVersion)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.addProperty("skippedUnsupported", skipped)
        root.add("keys", keysArray)
        return Exported(Gson().toJson(root), raw.size)
    }

    /** План импорта: ok=false → error содержит честную причину отказа. */
    data class ImportPlan(
        val ok: Boolean,
        val error: String = "",
        val entries: List<Pair<Preferences.Key<*>, Any>> = emptyList(),
        val total: Int = 0,
    )

    /**
     * Разбор текста экспорт-файла → типизированный план восстановления.
     * Значения читаются через примитивное asString (Gson отдаёт строковое
     * представление любого примитива) и конвертируются явно — кривое значение
     * конкретного ключа пропускает КЛЮЧ, а не весь импорт. Файл из будущей
     * версии формата отклоняется целиком (честная причина, молчаливый
     * частичный импорт опаснее отказа).
     */
    fun parse(text: String): ImportPlan {
        if (text.isBlank()) return ImportPlan(false, "Файл пустой")
        val root: JsonObject = try {
            Gson().fromJson(text, JsonObject::class.java)
        } catch (t: Throwable) {
            return ImportPlan(false, "Это не JSON-файл: " + t.javaClass.simpleName)
        }
        if (root == null) return ImportPlan(false, "Файл не содержит JSON-объекта")
        val format = root.get("format")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (format != FORMAT_VERSION) {
            return ImportPlan(false, "Неизвестная версия формата: " + format + " (ожидалась " + FORMAT_VERSION + ")")
        }
        val keysEl = root.get("keys")
        if (keysEl == null || !keysEl.isJsonArray) return ImportPlan(false, "В файле нет массива keys")
        val entries = ArrayList<Pair<Preferences.Key<*>, Any>>()
        for (element in keysEl.asJsonArray) {
            val entry = element as? JsonObject ?: continue
            val name = entry.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (name.isEmpty()) continue
            val type = entry.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val valueEl = entry.get("value")
            if (valueEl == null) continue
            when (type) {
                "string" -> {
                    val v = valueEl.takeIf { it.isJsonPrimitive }?.asString
                    if (v != null) entries.add(stringPreferencesKey(name) to v)
                }
                "boolean" -> {
                    val s = valueEl.takeIf { it.isJsonPrimitive }?.asString
                    val v = if (s == "true") true else if (s == "false") false else null
                    if (v != null) entries.add(booleanPreferencesKey(name) to v)
                }
                "int" -> {
                    val v = valueEl.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
                    if (v != null) entries.add(intPreferencesKey(name) to v)
                }
                "long" -> {
                    val v = valueEl.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
                    if (v != null) entries.add(longPreferencesKey(name) to v)
                }
                "stringSet" -> {
                    if (valueEl.isJsonArray) {
                        val set = LinkedHashSet<String>()
                        for (item in valueEl.asJsonArray) {
                            val s = item.takeIf { it.isJsonPrimitive }?.asString
                            if (s != null) set.add(s)
                        }
                        entries.add(stringSetPreferencesKey(name) to set)
                    }
                }
                // Неизвестный тип (файл из более новой версии) — ключ пропускается.
            }
        }
        if (entries.isEmpty()) {
            return ImportPlan(false, "Не нашёл ни одного корректного ключа — файл повреждён или из другой программы")
        }
        return ImportPlan(true, "", entries, entries.size)
    }

    /**
     * Применение плана: ОДНА атомарная транзакция DataStore (restoreRaw) —
     * либо все ключи, либо ни один. Возвращает число записанных ключей.
     */
    suspend fun apply(prefs: SovaPrefs, entries: List<Pair<Preferences.Key<*>, Any>>): Int {
        return prefs.restoreRaw(entries)
    }
}
