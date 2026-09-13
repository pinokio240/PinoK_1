package re.pinok.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import re.pinok.auth.exchange.ExchangeTokenStorage
import re.pinok.data.local.SovaPrefs

/**
 * Волна 45 #SETTINGS-EXPORT (переработан в волне 45-б): экспорт/импорт ВСЕХ
 * настроек «контейнерного» приложения в JSON-файл и обратно.
 *
 * ЗАЧЕМ (сценарии):
 *  - откат версии (#UPDATER-ROLLBACK): Android требует удалить приложение,
 *    чтобы поставить более старую, — ВСЁ хранилище в app-контейнере стирается
 *    (это и значит «контейнерное приложение»: данные живут в /data/data/re.pinok.*
 *    и умирают вместе с пакетом); экспорт через SAF в пользовательскую папку —
 *    единственный способ вернуть настройки и сессию после переустановки;
 *  - перенос на новое устройство / второе устройство;
 *  - бэкап перед рискованными экспериментами с настройками.
 *
 * ЧТО ВХОДИТ (карта хранилищ контейнера — всё, что несёт НАСТРОЙКИ или СЕССИЮ):
 *  1. DataStore «sova_settings» (core/data, SovaPrefs) — ВСЕ ключи generic-ом
 *     (rawSnapshot): интерфейс, музыка, звонки, уведомления, логирование,
 *     кэш updater'а, локальные закладки треков и папок (ключи вне Snapshot
 *     тоже попадают — снимок сырой, полный);
 *  2. legacy SharedPreferences «equalizer» — настройки медиа-контейнера
 *     (AudioEffectsEngine/EqualizerHelper/EqualizerFeatureFlags: EQ, bass,
 *     virtualizer, loudness, reverb) — раньше выпадали из экспорта;
 *  3. сессия (EncryptedSharedPreferences через ExchangeTokenStorage) —
 *     access/exchange/webview/silent-токены, remixsid, vk_* куки, trusted_hash,
 *     last_phone/last_password — без этого «вход в аккаунт» после
 *     переустановки не восстановить. Прецедент plaintext-файла с токенами —
 *     account.json (File backup, VTosters pattern #3); в экспорт-файле секреты
 *     защищаются только выбором места пользователем — UI предупреждает ДО
 *     экспорта (красная строка во вкладке «Данные»).
 *
 * ЧТО НЕ ВХОДИТ (честно, пересоздаётся само или непереносимо by design):
 *  - SharedPreferences «security_alerts_cache» (кэш поллера алертов);
 *  - SharedPreferences «app_meta» (штамп last_version_code для миграции);
 *  - медиа-кэш и офлайн-загрузки (отдельные файлы — только перескачивание);
 *  - WorkManager-джобы (перепланируются при старте).
 *
 * ФОРМАТ (format=1, Gson; секции sp/session добавлены в волне 45-б, парсер
 * принимает и старый файл с одним DataStore-массивом):
 *   {"format":1,"app":"PinoK","appVersion":"2.0.1 (versionCode 2)",
 *    "exportedAt":1234567890,"skippedUnsupported":0,
 *    "keys":[{"name":..,"type":"string|boolean|int|long|stringSet","value":..}],
 *    "sp":[{"file":"equalizer","keys":[{"name":..,"type":"...|float","value":..}]}],
 *    "session":[{"name":..,"type":"...","value":..}]}
 *
 * ⚠ ВОЛНА 45-б, ПРИЧИНА ФИКСА СБОРКИ: в datastore 1.1.x у библиотеки СВОЙ
 * infix `to` (Preferences.Key.to(value) → Preferences.Pair) — выражение
 * `stringPreferencesKey(..) to v` резолвится именно в него, а не в generic
 * kotlin.to, и полученный Preferences.Pair не влезает в
 * Pair<Preferences.Key<*>, Any> (5 compile-ошибок «Argument type mismatch»
 * у тестера). Поэтому в этом файле инфиксный `to` для datastore-ключей
 * НЕ используется ВООБЩЕ: все пары строятся явным конструктором Pair(.., ..).
 *
 * Общие (generic) принципы: тип значения определяется Key-объектом DataStore
 * при экспорте и полем "type" при импорте — НОВЫЕ ключи любого хранилища
 * попадают в экспорт автоматически. Кривое значение конкретного ключа
 * пропускает КЛЮЧ, а не весь импорт; файл из будущей версии формата
 * отклоняется целиком (молчаливый частичный импорт опаснее честного отказа).
 */
object SovaPrefsBackup {

    /** Версия формата: при несовместимых изменениях — инкремент (парсер честно отклонит чужие версии). */
    const val FORMAT_VERSION = 1

    /** MIME экспорт-файла для SAF-контракта CreateDocument. */
    const val MIME_JSON = "application/json"

    /**
     * Файлы legacy SharedPreferences, несущие НАСТРОЙКИ (попадают в экспорт).
     * «security_alerts_cache» (кэш поллера) и «app_meta» (штамп версии для
     * миграции) сознательно НЕ включены — это не настройки, они пересоздаются
     * сами; включать их = тащить мусор в переносимый файл.
     */
    private val SETTINGS_SP_FILES = listOf("equalizer")

    /** Результат экспорта: готовый JSON + счётчики по секциям (для честного тоста). */
    data class Exported(val json: String, val keyCount: Int, val spCount: Int, val sessionCount: Int) {
        val totalCount: Int get() = keyCount + spCount + sessionCount
    }

    /** Одна запись legacy SharedPreferences: файл → ключ → типизированное значение. */
    data class SpEntry(val file: String, val key: String, val value: Any)

    /** План импорта: ok=false → error содержит честную причину отказа. */
    data class ImportPlan(
        val ok: Boolean,
        val error: String = "",
        val entries: List<Pair<Preferences.Key<*>, Any>> = emptyList(),
        val sp: List<SpEntry> = emptyList(),
        val session: List<Pair<String, Any>> = emptyList(),
        val total: Int = 0,
    )

    /**
     * Полный дамп хранилищ настроек в JSON (см. формат в KDoc класса).
     * DataStore — rawSnapshot (полный сырой снимок); equalizer — чтение
     * SharedPreferences; сессия — расшифрованный снапшот EncryptedSharedPreferences
     * (в процессе мы владеем ключом Keystore, расшифрование валидно).
     */
    suspend fun export(
        context: Context,
        prefs: SovaPrefs,
        exchangeStorage: ExchangeTokenStorage,
        appVersion: String,
    ): Exported {
        // 1) DataStore — полный сырой дамп всех ключей (Snapshot НЕ используется:
        //    он подмножество, часть ключей живёт вне него).
        val raw = prefs.rawSnapshot()
        val keysArray = JsonArray()
        var skipped = 0
        for ((key, value) in raw) {
            val entry = encodeEntry(key.name, value)
            if (entry == null) {
                // Типов Keys ровно пять (см. SovaPrefs.Keys); ветка для
                // устойчивости к будущим типам — не роняем экспорт целиком.
                skipped++
                continue
            }
            keysArray.add(entry)
        }

        // 2) Legacy SharedPreferences с настройками (equalizer медиа-контейнера).
        val spArray = JsonArray()
        var spCount = 0
        for (fileName in SETTINGS_SP_FILES) {
            val sp = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val group = JsonObject()
            group.addProperty("file", fileName)
            val arr = JsonArray()
            for ((k, v) in sp.all) {
                val entry = if (v != null) encodeEntry(k, v) else null
                if (entry == null) continue
                arr.add(entry)
                spCount++
            }
            group.add("keys", arr)
            spArray.add(group)
        }

        // 3) Сессия — расшифрованный снапшот EncryptedSharedPreferences
        //    (токены/куки входа). Пустой, если в аккаунт не входили.
        val sessionArray = JsonArray()
        var sessionCount = 0
        for ((k, v) in exchangeStorage.exportSessionSnapshot()) {
            val entry = encodeEntry(k, v)
            if (entry == null) continue
            sessionArray.add(entry)
            sessionCount++
        }

        val root = JsonObject()
        root.addProperty("format", FORMAT_VERSION)
        root.addProperty("app", "PinoK")
        root.addProperty("appVersion", appVersion)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.addProperty("skippedUnsupported", skipped)
        root.add("keys", keysArray)
        root.add("sp", spArray)
        root.add("session", sessionArray)
        return Exported(Gson().toJson(root), raw.size, spCount, sessionCount)
    }

    /**
     * Разбор текста экспорт-файла → типизированный план восстановления.
     * Значения читаются через примитивное asString (Gson отдаёт строковое
     * представление любого примитива) и конвертируются явно. Секции "sp" и
     * "session" опциональны (совместимость с файлами волны 45).
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
            val decoded = decodeEntry(entry) ?: continue
            val key = dataStoreKey(decoded.first, decoded.second) ?: continue
            // Только явный конструктор Pair(..) — НЕ infix `to` (см. KDoc класса:
            // у datastore 1.1.x свой infix to → Preferences.Pair, ломает сборку).
            entries.add(Pair(key, decoded.second))
        }

        val sp = ArrayList<SpEntry>()
        val spEl = root.get("sp")
        if (spEl != null && spEl.isJsonArray) {
            for (groupEl in spEl.asJsonArray) {
                val group = groupEl as? JsonObject ?: continue
                val file = group.get("file")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val arr = group.get("keys") as? JsonArray ?: continue
                for (element in arr) {
                    val entry = element as? JsonObject ?: continue
                    val decoded = decodeEntry(entry) ?: continue
                    sp.add(SpEntry(file, decoded.first, decoded.second))
                }
            }
        }

        val session = ArrayList<Pair<String, Any>>()
        val sessionEl = root.get("session")
        if (sessionEl != null && sessionEl.isJsonArray) {
            for (element in sessionEl.asJsonArray) {
                val entry = element as? JsonObject ?: continue
                val decoded = decodeEntry(entry) ?: continue
                session.add(Pair(decoded.first, decoded.second))
            }
        }

        val total = entries.size + sp.size + session.size
        if (total == 0) {
            return ImportPlan(false, "Не нашёл ни одного корректного ключа — файл повреждён или из другой программы")
        }
        return ImportPlan(true, "", entries, sp, session, total)
    }

    /**
     * Применение плана. Атомарность честная, по хранилищам:
     *  - DataStore — ОДНА транзакция (restoreRaw): либо все ключи, либо ни один;
     *  - legacy SharedPreferences — один пакетный edit на файл (commit —
     *    синхронно, чтобы тост «Применено N» не врал);
     *  - сессия — ExchangeTokenStorage.applyExportedSession (commit + обновление
     *    account.json). Возвращает число фактически записанных записей.
     */
    suspend fun apply(
        context: Context,
        prefs: SovaPrefs,
        exchangeStorage: ExchangeTokenStorage,
        plan: ImportPlan,
    ): Int {
        var written = prefs.restoreRaw(plan.entries)
        for ((file, entriesForFile) in plan.sp.groupBy { it.file }) {
            val sp = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val ed = sp.edit()
            for (e in entriesForFile) {
                when (val v = e.value) {
                    is String -> ed.putString(e.key, v)
                    is Boolean -> ed.putBoolean(e.key, v)
                    is Int -> ed.putInt(e.key, v)
                    is Long -> ed.putLong(e.key, v)
                    is Float -> ed.putFloat(e.key, v)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        ed.putStringSet(e.key, v as Set<String>)
                    }
                }
            }
            ed.commit()
            written += entriesForFile.size
        }
        val sessionMap = HashMap<String, Any>()
        for (pair in plan.session) sessionMap[pair.first] = pair.second
        written += exchangeStorage.applyExportedSession(sessionMap)
        return written
    }

    // ─── Общая кодировка записей {name, type, value} для всех трёх секций ───

    /**
     * Упаковка значения в JSON-запись по его РЕАЛЬНОМУ типу. Возвращает null
     * для типов вне набора (честный пропуск со счётчиком у вызывающего).
     * Набор покрывает 100% типов SharedPreferences (string/boolean/int/long/
     * float/stringSet) и все типы ключей SovaPrefs (всё, кроме float).
     */
    private fun encodeEntry(name: String, value: Any): JsonObject? {
        val entry = JsonObject()
        entry.addProperty("name", name)
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
            is Float -> {
                entry.addProperty("type", "float")
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
            else -> return null
        }
        return entry
    }

    /** Распаковка записи: (имя, типизированное значение) или null, если ключ битый. */
    private fun decodeEntry(entry: JsonObject): Pair<String, Any>? {
        val name = entry.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (name.isEmpty()) return null
        val type = entry.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val valueEl = entry.get("value") ?: return null
        return when (type) {
            "string" -> valueEl.takeIf { it.isJsonPrimitive }?.asString?.let { Pair(name, it) }
            "boolean" -> {
                val s = valueEl.takeIf { it.isJsonPrimitive }?.asString
                when (s) {
                    "true" -> Pair(name, true)
                    "false" -> Pair(name, false)
                    else -> null
                }
            }
            "int" -> valueEl.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()?.let { Pair(name, it) }
            "long" -> valueEl.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()?.let { Pair(name, it) }
            "float" -> valueEl.takeIf { it.isJsonPrimitive }?.asString?.toFloatOrNull()?.let { Pair(name, it) }
            "stringSet" -> {
                if (!valueEl.isJsonArray) return null
                val set = LinkedHashSet<String>()
                for (item in valueEl.asJsonArray) {
                    val s = item.takeIf { it.isJsonPrimitive }?.asString
                    if (s != null) set.add(s)
                }
                Pair(name, set)
            }
            // Неизвестный тип (файл из более новой версии) — ключ пропускается.
            else -> null
        }
    }

    /** Построение DataStore Key по типу распакованного значения (float для DataStore невозможен). */
    private fun dataStoreKey(name: String, value: Any): Preferences.Key<*>? = when (value) {
        is String -> stringPreferencesKey(name)
        is Boolean -> booleanPreferencesKey(name)
        is Int -> intPreferencesKey(name)
        is Long -> longPreferencesKey(name)
        is Set<*> -> stringSetPreferencesKey(name)
        else -> null
    }
}
