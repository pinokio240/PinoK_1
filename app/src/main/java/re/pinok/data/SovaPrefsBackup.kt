package re.pinok.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import re.pinok.auth.exchange.ExchangeTokenStorage
import re.pinok.data.local.SovaPrefs

/**
 * Волна 45 #SETTINGS-EXPORT (переработан в волне 45-б: покрытие всего
 * «контейнерного» хранилища; механизм восстановления перепроверен в волне
 * 45-в #RESTORE-SAFETY по запросу «не хочу ничего терять»): экспорт/импорт
 * ВСЕХ настроек «контейнерного» приложения в JSON-файл и обратно.
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
 * ЧТО ВХОДИТ (карта хранилищ контейнера — свип 47-a подтвердил полноту):
 *  1. DataStore «sova_settings» (core/data, SovaPrefs; ЕДИНСТВЕННЫЙ DataStore
 *     в репо) — ВСЕ ключи generic-ом (rawSnapshot): интерфейс, музыка, звонки,
 *     уведомления, логирование, кэш updater'а, локальные закладки треков и
 *     папок (ключи вне Snapshot тоже попадают — снимок сырой, полный);
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
 * ВОЛНА 45-в #RESTORE-SAFETY — итог перепроверки механизма восстановления:
 *  - СТРАХОВОЧНАЯ КОПИЯ: перед любым импортом apply() сначала экспортирует
 *    ТЕКУЩЕЕ состояние целиком в приватный файл (SAFETY_FILE_NAME). Неудача
 *    копии = отказ импорта ДО единой записи: восстановление без сети
 *    безопасности не выполняется принципиально. Копия читается вкладкой
 *    «Данные» (readSafetyCopy) и восстанавливается одной кнопкой; при
 *    восстановлении ИЗ копии она сама не перезаписывается (иначе хороший
 *    снимок затирается уже испорченным текущим состоянием);
 *  - GUARD КОНФЛИКТА ТИПОВ: Preferences.Key равен ТОЛЬКО по имени (тип —
 *    дженерик-параметр без рантайм-роли), поэтому запись string-значения под
 *    живой int-ключ молча порождала бы ClassCastException при каждом чтении
 *    настройки (краш-луп без возврата во вкладку импорта). restoreRaw теперь
 *    сверяет тип нового значения с текущим и ПРОПУСКАЕТ конфликтные ключи
 *    с честным счётчиком (SovaPrefs.RawRestore);
 *  - ЧЕСТНЫЙ РЕЗУЛЬТАТ (AppliedResult): commit() у SharedPreferences
 *    проверяется (false = файл не записан, попадает в spFailed), сессия
 *    отделяется от «в файле не было access_token», счётчик skipped из parse
 *    доходит до тоста; UI рекомендует перезапуск (часть кода держит значения
 *    в памяти);
 *  - ДЕДУП В PARSE: повтор имени в секции — последнее значение выигрывает,
 *    заменённые записи попадают в skipped (молчаливое «последний неявно
 *    победил» хуже честного счётчика);
 *  - FORWARD-COMPAT ТИПОВ: добавлены float/double/bytes(base64) — полный
 *    набор типов DataStore, чтобы будущие ключи переносились уже сейчас
 *    (сейчас в SovaPrefs ровно 5 типов — свип 47-a).
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
     * Волна 45-в #RESTORE-SAFETY: имя файла страховочной копии в приватном
     * хранилище приложения (context.filesDir). Один файл, перезаписывается
     * перед каждым импортом — всегда хранит состояние «до последнего импорта».
     * Файл приватный (как account.json — прецедент plaintext с токенами),
     * наружу не читается ничем, кроме самого приложения.
     */
    const val SAFETY_FILE_NAME = "sova_prefs_safety.json"

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

    /**
     * План импорта: ok=false → error содержит честную причину отказа.
     * Волна 45-в: + skipped (нераспознанные/дублирующиеся записи файла),
     * exportedAt/appVersion (штампы для диалога и карточки страховки).
     */
    data class ImportPlan(
        val ok: Boolean,
        val error: String = "",
        val entries: List<Pair<Preferences.Key<*>, Any>> = emptyList(),
        val sp: List<SpEntry> = emptyList(),
        val session: List<Pair<String, Any>> = emptyList(),
        val total: Int = 0,
        val skipped: Int = 0,
        val exportedAt: Long = 0,
        val appVersion: String = "",
    )

    /**
     * Волна 45-в #RESTORE-SAFETY: честный результат применения плана — то,
     * из чего UI собирает тост без лжи.
     *  - ok=false: импорт прерван ДО ЛЮБЫХ записей (не создалась страховка)
     *    или DataStore-транзакция упала (она атомарна — состояние прежнее;
     *    страховочная копия к этому моменту уже лежит в SAFETY_FILE_NAME);
     *  - dataStoreSkipped: ключи, отклонённые guard'ом конфликта типов;
     *  - spFailed: SP-файлы, где commit() вернул false (данные НЕ записаны);
     *  - session/sessionExpected: фактически восстановленные сессионные
     *    записи из числа бывших в файле (sessionExpected==0 — секции сессии
     *    в файле не было вовсе).
     */
    data class AppliedResult(
        val ok: Boolean,
        val error: String = "",
        val dataStore: Int = 0,
        val dataStoreSkipped: Int = 0,
        val sp: Int = 0,
        val spFailed: List<String> = emptyList(),
        val session: Int = 0,
        val sessionExpected: Int = 0,
        val safetyPath: String? = null,
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
     *
     * Волна 45-в #RESTORE-SAFETY: повтор имени внутри секции — last-wins
     * (заменённая запись честно попадает в skipped); без дедупа две записи
     * с одним именем писались бы обе, а счётчик «Применено» врал.
     *
     * Волна 45-г: JSON читается как JsonElement с сужением as? JsonObject —
     * «null»-документ и корректный JSON не-объекта честно дают отказ «Файл
     * не содержит JSON-объекта». Раньше эта ветка была МЁРТВОЙ: fromJson —
     * Java API с platform-типом результата (JsonObject!), Kotlin вставлял
     * неявный checkNotNull при присваивании в non-null val → NPE в обход
     * catch + warning «Condition is always 'false'» на guard'е (266:13).
     */
    fun parse(text: String): ImportPlan {
        if (text.isBlank()) return ImportPlan(false, "Файл пустой")
        val root: JsonObject? = try {
            // JsonElement::class — точное совпадение типа → встроенный
            // JSON_ELEMENT-адаптер Gson (nullSafe, стабилен во всех версиях);
            // «[1]»/«123» — валидный JSON, но не объект → as? даёт null →
            // тот же отказ, что и для документа-литерала «null».
            Gson().fromJson(text, JsonElement::class.java) as? JsonObject
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
        val exportedAt = root.get("exportedAt")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        val fileAppVersion = root.get("appVersion")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        var skipped = 0

        // Дедуп по имени (last-wins): LinkedHashMap сохраняет порядок файла.
        val byName = LinkedHashMap<String, Pair<Preferences.Key<*>, Any>>()
        for (element in keysEl.asJsonArray) {
            val entry = element as? JsonObject
            if (entry == null) {
                skipped++
                continue
            }
            val decoded = decodeEntry(entry)
            if (decoded == null) {
                skipped++
                continue
            }
            val key = dataStoreKey(decoded.first, decoded.second)
            if (key == null) {
                skipped++
                continue
            }
            // Только явный конструктор Pair(..) — НЕ infix `to` (см. KDoc класса:
            // у datastore 1.1.x свой infix to → Preferences.Pair, ломает сборку).
            if (byName.put(decoded.first, Pair(key, decoded.second)) != null) skipped++
        }
        val entries = ArrayList(byName.values)

        val bySp = LinkedHashMap<Pair<String, String>, SpEntry>()
        val spEl = root.get("sp")
        if (spEl != null && spEl.isJsonArray) {
            for (groupEl in spEl.asJsonArray) {
                val group = groupEl as? JsonObject
                if (group == null) {
                    skipped++
                    continue
                }
                val file = group.get("file")?.takeIf { it.isJsonPrimitive }?.asString
                if (file == null) {
                    skipped++
                    continue
                }
                val arr = group.get("keys") as? JsonArray
                if (arr == null) {
                    skipped++
                    continue
                }
                for (element in arr) {
                    val entry = element as? JsonObject
                    if (entry == null) {
                        skipped++
                        continue
                    }
                    val decoded = decodeEntry(entry)
                    if (decoded == null) {
                        skipped++
                        continue
                    }
                    if (bySp.put(Pair(file, decoded.first), SpEntry(file, decoded.first, decoded.second)) != null) {
                        skipped++
                    }
                }
            }
        }
        val sp = ArrayList(bySp.values)

        val bySession = LinkedHashMap<String, Pair<String, Any>>()
        val sessionEl = root.get("session")
        if (sessionEl != null && sessionEl.isJsonArray) {
            for (element in sessionEl.asJsonArray) {
                val entry = element as? JsonObject
                if (entry == null) {
                    skipped++
                    continue
                }
                val decoded = decodeEntry(entry)
                if (decoded == null) {
                    skipped++
                    continue
                }
                if (bySession.put(decoded.first, Pair(decoded.first, decoded.second)) != null) skipped++
            }
        }
        val session = ArrayList(bySession.values)

        val total = entries.size + sp.size + session.size
        if (total == 0) {
            return ImportPlan(false, "Не нашёл ни одного корректного ключа — файл повреждён или из другой программы")
        }
        return ImportPlan(true, "", entries, sp, session, total, skipped, exportedAt, fileAppVersion)
    }

    /**
     * Применение плана. Волна 45-в #RESTORE-SAFETY:
     *  - СНАЧАЛА страховочная копия: полный export() текущего состояния в
     *    filesDir/SAFETY_FILE_NAME. Любая неудача здесь = отказ импорта,
     *    ни одна запись не тронута (ok=false, error объясняет). Это ядро
     *    гарантии «не потеряю ничего»: у пользователя всегда есть снимок
     *    состояния ДО последнего импорта;
     *  - DataStore — ОДНА транзакция (restoreRaw): либо все ключи, либо ни
     *    один; исключение откатывает транзакцию целиком, страховка уже лежит;
     *  - legacy SharedPreferences — один пакетный edit на файл, РЕЗУЛЬТАТ
     *    commit() проверяется (false/исключение → файл в spFailed, честный
     *    отчёт вместо вравшего счётчика);
     *  - сессия — ExchangeTokenStorage.applyExportedSession (commit + лучший
     *    effort account.json); возвращается фактическое число записей, чтобы
     *    UI отличил «в файле не было access_token» от «запись не удалась».
     *
     * @param appVersion    штамп для страховочной копии (виден в её карточке)
     * @param saveSafetyCopy false при восстановлении ИЗ самой копии: иначе
     *                       хороший снимок затёрся бы текущим (испорченным)
     *                       состоянием, и сети безопасности не осталось бы.
     */
    suspend fun apply(
        context: Context,
        prefs: SovaPrefs,
        exchangeStorage: ExchangeTokenStorage,
        plan: ImportPlan,
        appVersion: String = "",
        saveSafetyCopy: Boolean = true,
    ): AppliedResult {
        var safetyPath: String? = null
        if (saveSafetyCopy) {
            safetyPath = try {
                val snapshot = export(context, prefs, exchangeStorage, appVersion.ifEmpty { "PinoK (авто)" })
                val file = File(context.filesDir, SAFETY_FILE_NAME)
                file.writeText(snapshot.json, Charsets.UTF_8)
                file.absolutePath
            } catch (t: Throwable) {
                return AppliedResult(
                    ok = false,
                    error = "не удалось создать страховочную копию (" + t.javaClass.simpleName +
                        ") — текущие настройки НЕ тронуты",
                )
            }
        }

        val dsRes = try {
            prefs.restoreRaw(plan.entries)
        } catch (t: Throwable) {
            return AppliedResult(
                ok = false,
                error = "не удалось записать DataStore (" + t.javaClass.simpleName +
                    ") — транзакция атомарна, настройки прежние; состояние до импорта лежит в страховочной копии",
                safetyPath = safetyPath,
            )
        }

        var spWritten = 0
        val spFailed = ArrayList<String>()
        for ((file, entriesForFile) in plan.sp.groupBy { it.file }) {
            val sp = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val ed = sp.edit()
            var fileWritten = 0
            for (e in entriesForFile) {
                when (val v = e.value) {
                    is String -> {
                        ed.putString(e.key, v)
                        fileWritten++
                    }
                    is Boolean -> {
                        ed.putBoolean(e.key, v)
                        fileWritten++
                    }
                    is Int -> {
                        ed.putInt(e.key, v)
                        fileWritten++
                    }
                    is Long -> {
                        ed.putLong(e.key, v)
                        fileWritten++
                    }
                    is Float -> {
                        ed.putFloat(e.key, v)
                        fileWritten++
                    }
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        ed.putStringSet(e.key, v as Set<String>)
                        fileWritten++
                    }
                    // Прочих типов в SharedPreferences не бывает; значение из
                    // правленого файла молча НЕ считается записанным.
                }
            }
            val committed = try {
                ed.commit()
            } catch (t: Throwable) {
                false
            }
            if (committed) spWritten += fileWritten else spFailed.add(file)
        }

        val sessionMap = HashMap<String, Any>()
        for (pair in plan.session) sessionMap[pair.first] = pair.second
        val sessionWritten = try {
            exchangeStorage.applyExportedSession(sessionMap)
        } catch (t: Throwable) {
            // applyExportedSession глотает сам, но не полагаемся на чужие гарантии.
            0
        }

        return AppliedResult(
            ok = true,
            dataStore = dsRes.written,
            dataStoreSkipped = dsRes.skipped,
            sp = spWritten,
            spFailed = spFailed,
            session = sessionWritten,
            sessionExpected = plan.session.size,
            safetyPath = safetyPath,
        )
    }

    /**
     * Волна 45-в #RESTORE-SAFETY: чтение страховочной копии для карточки
     * «Восстановить из копии». Проверяется ТЕМ ЖЕ parse() (та же валидация,
     * что у импорта) — битая/устаревшая копия честно не предлагается (null).
     * Блокирующее чтение файла: вызывать с Dispatchers.IO.
     */
    fun readSafetyCopy(context: Context): ImportPlan? {
        val file = File(context.filesDir, SAFETY_FILE_NAME)
        if (!file.exists()) return null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            return null
        }
        val plan = parse(text)
        if (!plan.ok) return null
        return plan
    }

    // ─── Общая кодировка записей {name, type, value} для всех трёх секций ───

    /**
     * Упаковка значения в JSON-запись по его РЕАЛЬНОМУ типу. Возвращает null
     * для типов вне набора (честный пропуск со счётчиком у вызывающего).
     * Набор покрывает 100% типов SharedPreferences (string/boolean/int/long/
     * float/stringSet) и полный набор типов ключей DataStore (плюс
     * forward-compat double/bytes — свип 47-a: сейчас в SovaPrefs их нет).
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
            is Double -> {
                entry.addProperty("type", "double")
                entry.addProperty("value", value)
            }
            is ByteArray -> {
                entry.addProperty("type", "bytes")
                entry.addProperty("value", Base64.encodeToString(value, Base64.NO_WRAP))
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
            "double" -> valueEl.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.let { Pair(name, it) }
            "bytes" -> {
                val s = valueEl.takeIf { it.isJsonPrimitive }?.asString ?: return null
                try {
                    Pair(name, Base64.decode(s, Base64.NO_WRAP))
                } catch (t: Throwable) {
                    // Кривой base64 (ручная правка) — ключ пропускается честно.
                    null
                }
            }
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

    /**
     * Построение DataStore Key по типу распакованного значения. Волна 45-в:
     * покрыт ПОЛНЫЙ набор типов ключей DataStore (float/double/bytes —
     * forward-compat; сейчас в SovaPrefs их нет — свип 47-a).
     */
    private fun dataStoreKey(name: String, value: Any): Preferences.Key<*>? = when (value) {
        is String -> stringPreferencesKey(name)
        is Boolean -> booleanPreferencesKey(name)
        is Int -> intPreferencesKey(name)
        is Long -> longPreferencesKey(name)
        is Float -> floatPreferencesKey(name)
        is Double -> doublePreferencesKey(name)
        is ByteArray -> byteArrayPreferencesKey(name)
        is Set<*> -> stringSetPreferencesKey(name)
        else -> null
    }
}
