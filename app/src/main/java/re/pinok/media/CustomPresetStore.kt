// File: media/CustomPresetStore.kt
package re.pinok.media

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import re.pinok.SovaApp
import re.pinok.util.AppLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// ══════════════════════════════════════════════════════════════════════
//  CustomPresetStore — Этап 4 EQUALIZER_INTEGRATION_PLAN.md
//
//  Хранилище пользовательских пресетов эквалайзера. В плане предполагался
//  Room, но в PinoK Room не настроен (нет ksp/kapt плагина). Чтобы не
//  тянуть новую инфраструктуру, используем JSON-файл в filesDir/equalizer/
//  — тот же паттерн что у VideoDownloadManager/StoryVideoDownloadManager
//  (sidecar .meta + main файл) и TrackDownloadManager.
//
//  Снимок полного состояния AudioEffectsEngine:
//    - Equalizer bands (9 значений в mB)
//    - BassBoost (enabled + strength 0..1000)
//    - Virtualizer (enabled + strength 0..1000)
//    - LoudnessEnhancer (enabled + gain mB)
//    - PresetReverb (enabled + preset 0..6)
//
//  Потокобезопасность: все读写 synchronized на lock. AtomicBoolean dirty
//  для lazy-flush при изменениях.
//
//  Lifecycle: singleton через SovaApp.get(). CustomPresetStore.load() в
//  Application.onCreate, flush() при изменениях. UI читает list() синхронно
//  (быстро — JSON ~1KB на 10 пресетов).
// ══════════════════════════════════════════════════════════════════════

/**
 * Один пользовательский пресет — полный снимок состояния AudioEffectsEngine.
 *
 * @param id уникальный ID (monotonic). 0 = не сохранён.
 * @param name отображаемое имя («Мой пресет 1»).
 * @param eqBands значения полос EQ в mB (миллибелах), short array.
 * @param eqEnabled был ли EQ включён при сохранении.
 * @param bassEnabled, bassStrength состояние BassBoost.
 * @param virtEnabled, virtStrength состояние Virtualizer.
 * @param loudEnabled, loudGainmB состояние LoudnessEnhancer.
 * @param reverbEnabled, reverbPreset состояние PresetReverb.
 * @param createdAt timestamp создания (для сортировки).
 */
@Immutable
data class CustomPreset(
    val id: Long,
    val name: String,
    val eqBands: List<Short>,
    val eqEnabled: Boolean,
    val bassEnabled: Boolean,
    val bassStrength: Int,
    val virtEnabled: Boolean,
    val virtStrength: Int,
    val loudEnabled: Boolean,
    val loudGainmB: Int,
    val reverbEnabled: Boolean,
    val reverbPreset: Int,
    val createdAt: Long,
)

/**
 * Потокобезопасное JSON-хранилище пользовательских пресетов эквалайзера.
 *
 * Файл: `filesDir/equalizer/custom_presets.json`
 * Формат: `[{...CustomPreset}, ...]`
 */
object CustomPresetStore {

    private const val TAG = "CustomPresetStore"
    private const val DIR_NAME = "equalizer"
    private const val FILE_NAME = "custom_presets.json"

    private val lock = Any()
    private val dirty = AtomicBoolean(false)

    @Volatile
    private var presets: List<CustomPreset> = emptyList()

    @Volatile
    private var nextId: Long = 1L

    @Volatile
    private var loaded: Boolean = false

    private val gson = Gson()
    private val typeToken = object : TypeToken<List<CustomPreset>>() {}.type

    private fun file(): File? = try {
        val ctx = SovaApp.get()
        val dir = File(ctx.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        File(dir, FILE_NAME)
    } catch (e: Exception) {
        AppLog.w(TAG, "file: SovaApp not ready — ${e.message}")
        null
    }

    /**
     * Загрузить пресеты с диска. Безопасно вызывать несколько раз —
     * повторные вызовы no-op (использует in-memory cache).
     * SovaApp.onCreate вызывает этот метод.
     */
    fun load() {
        synchronized(lock) {
            if (loaded) return
            val f = file()
            if (f == null || !f.exists()) {
                loaded = true
                return
            }
            try {
                val json = f.readText()
                if (json.isBlank()) {
                    loaded = true
                    return
                }
                val parsed = gson.fromJson<List<CustomPreset>>(json, typeToken) ?: emptyList()
                // Фильтруем невалидные (null name / empty bands).
                presets = parsed.filter { it.name.isNotBlank() && it.eqBands.isNotEmpty() }
                nextId = (presets.maxOfOrNull { it.id } ?: 0L) + 1L
                loaded = true
                AppLog.i(TAG, "load: ${presets.size} custom presets loaded, nextId=$nextId")
            } catch (e: Exception) {
                AppLog.e(TAG, "load: failed to parse $FILE_NAME — starting fresh", e)
                presets = emptyList()
                nextId = 1L
                loaded = true
            }
        }
    }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    /**
     * Список всех пользовательских пресетов (копия). Сортировка: createdAt ASC.
     */
    fun list(): List<CustomPreset> {
        synchronized(lock) {
            ensureLoaded()
            return presets.sortedBy { it.createdAt }
        }
    }

    /**
     * Найти пресет по ID.
     */
    fun getById(id: Long): CustomPreset? {
        synchronized(lock) {
            ensureLoaded()
            return presets.find { it.id == id }
        }
    }

    /**
     * Сохранить новый пресет (или обновить существующий если id != 0).
     * Возвращает id сохранённого пресета (новый или существующий).
     */
    fun upsert(
        name: String,
        eqBands: List<Short>,
        eqEnabled: Boolean,
        bassEnabled: Boolean,
        bassStrength: Int,
        virtEnabled: Boolean,
        virtStrength: Int,
        loudEnabled: Boolean,
        loudGainmB: Int,
        reverbEnabled: Boolean,
        reverbPreset: Int,
        existingId: Long = 0L,
    ): Long {
        synchronized(lock) {
            ensureLoaded()
            val now = System.currentTimeMillis()
            val id = if (existingId != 0L) existingId else nextId++
            val preset = CustomPreset(
                id = id,
                name = name.trim().ifBlank { "Пресет $id" },
                eqBands = eqBands,
                eqEnabled = eqEnabled,
                bassEnabled = bassEnabled,
                bassStrength = bassStrength.coerceIn(0, 1000),
                virtEnabled = virtEnabled,
                virtStrength = virtStrength.coerceIn(0, 1000),
                loudEnabled = loudEnabled,
                loudGainmB = loudGainmB.coerceIn(0, 1500),
                reverbEnabled = reverbEnabled,
                reverbPreset = reverbPreset.coerceIn(0, 6),
                createdAt = now,
            )
            // Заменяем существующий или добавляем новый.
            val mutable = presets.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                mutable[existingIndex] = preset
            } else {
                mutable.add(preset)
            }
            presets = mutable
            dirty.set(true)
            flush()
            AppLog.i(TAG, "upsert: preset '${preset.name}' (id=$id) saved")
            return id
        }
    }

    /**
     * Удалить пресет по ID. Возвращает true если был удалён.
     */
    fun delete(id: Long): Boolean {
        synchronized(lock) {
            ensureLoaded()
            val mutable = presets.toMutableList()
            val removed = mutable.removeAll { it.id == id }
            if (removed) {
                presets = mutable
                dirty.set(true)
                flush()
                AppLog.i(TAG, "delete: preset id=$id removed")
            }
            return removed
        }
    }

    /**
     * Сбросить ВСЕ пользовательские пресеты. Только для «Сброс настроек».
     */
    fun clearAll() {
        synchronized(lock) {
            presets = emptyList()
            nextId = 1L
            dirty.set(true)
            flush()
            AppLog.i(TAG, "clearAll: all custom presets removed")
        }
    }

    /**
     * Записать in-memory кэш на диск. Дешёвая операция (~1KB JSON).
     */
    private fun flush() {
        if (!dirty.compareAndSet(true, false)) return
        val f = file() ?: return
        try {
            val json = gson.toJson(presets)
            f.writeText(json)
        } catch (e: Exception) {
            AppLog.e(TAG, "flush: failed to write $FILE_NAME", e)
            // Не сбрасываем dirty флаг обратно — при следующем изменении
            // попытаемся снова. Иначе потерянные данные накапливаются.
            dirty.set(true)
        }
    }

    /** Только для тестов/диагностики: количество пресетов в памяти. */
    fun count(): Int {
        synchronized(lock) {
            ensureLoaded()
            return presets.size
        }
    }
}
