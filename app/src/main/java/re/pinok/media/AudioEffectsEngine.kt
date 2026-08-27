// File: media/AudioEffectsEngine.kt
package re.pinok.media

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import re.pinok.SovaApp
import re.pinok.data.model.EqualizerPreset
import re.pinok.util.AppLog

/**
 * Полный движок audio-эффектов для PinoK — паттерн из декомпиляции
 * `Equalizer v6.3.5.7` (см. `reference/equalizer/`).
 *
 * Поддерживает **6 эффектов одновременно** на одну audio session:
 *  - [Equalizer] (legacy, API 9+) — частотные полосы ±dB
 *  - [BassBoost] (API 9+) — усиление низких (0..1000)
 *  - [Virtualizer] (API 9+) — пространственный эффект (0..1000)
 *  - [PresetReverb] (API 9+) — реверберация (6 пресетов)
 *  - [LoudnessEnhancer] (API 19+) — нормализация громкости (0..1500 mB)
 *  - DynamicsProcessing (API 28+, advanced) — TODO Этап 2: pre-EQ + post-EQ + limiter
 *
 * **Двойная обработка** (legacy + DynamicsProcessing параллельно) для
 * совместимости со старыми устройствами — как в Equalizer v6.3.5.7.
 * На API <28 DP недоступен → legacy берёт всё на себя.
 *
 * **Thread-safety.** Все методы synchronized на [lock]. attach/release
 * идемпотентны — повторный вызов с тем же sessionId пропускается (без
 * release+recreate). Это критично: `Player.Listener.onAudioSessionIdChanged`
 * в media3 1.8.0 может вызываться на каждом треке, а пересоздание эффектов
 * даёт audible gap (Fix #50 → #334 в EqualizerHelper).
 *
 * **Null-safe.** Все сеттеры/геттеры тихо пропускают операцию если эффект
 * не создан (устройство не поддерживает, или не attach'ен) — логируют
 * warning. Это позволяет UI работать даже если часть эффектов недоступна.
 *
 * **Persistence.** Настройки сохраняются в `SharedPreferences("equalizer")`
 * (тот же файл что у [EqualizerHelper] — обратная совместимость). При
 * attach автоматически восстанавливаются все сохранённые значения.
 *
 * @param sessionId audio session ID от ExoPlayer (`player.audioSessionId`)
 */
@Suppress("DEPRECATION")
// Virtualizer (class + constructor + setStrength(Short)) deprecated в API 33
// БЕЗ замены — Google в AOSP так и пишет: "no migration path, use as-is".
// audiofx.* по-прежнему единственный способ применить эффект к ExoPlayer.
// Подавляем предупреждение систематически, на уровне класса — это чище чем
// 4 разрозненных @Suppress на каждое использование Virtualizer.
class AudioEffectsEngine(private val sessionId: Int) {

    private val lock = Any()

    @Volatile private var equalizer: Equalizer? = null
    @Volatile private var bassBoost: BassBoost? = null
    @Volatile private var virtualizer: Virtualizer? = null
    @Volatile private var reverb: PresetReverb? = null
    @Volatile private var loudness: LoudnessEnhancer? = null

    @Volatile private var attached: Boolean = false

    /** sessionId к которому привязаны эффекты. 0 = не привязан. */
    @Volatile var attachedSessionId: Int = 0
        private set

    // ─── Constants (как в Equalizer v6.3.5.7 ye/m0.smali) ──────────

    /** Reverb presets: 0=None, 1=LargeRoom, 2=MediumRoom, 3=SmallRoom, 4=LargeHall, 5=MediumHall, 6=Plate. */
    val reverbPresetNames = listOf(
        "Без реверба", "Большая комната", "Средняя комната", "Малая комната",
        "Большой зал", "Средний зал", "Пластина",
    )

    /** Множитель перевода dB → миллибелы (1 dB = 100 mB). */
    private val DB_TO_MILLIBEL: Int = 100

    // ─── Persistence ──────────────────────────────────────────────

    private fun prefs(): SharedPreferences? = try {
        SovaApp.get().getSharedPreferences("equalizer", 0)
    } catch (e: Exception) {
        AppLog.w(TAG, "prefs: SovaApp not ready — ${e.message}")
        null
    }

    // Equalizer (legacy)
    private fun saveEqEnabled(v: Boolean) = prefs()?.edit()?.putBoolean(PREF_EQ_ENABLED, v)?.apply()
    private fun loadEqEnabled(): Boolean = prefs()?.getBoolean(PREF_EQ_ENABLED, false) ?: false
    private fun saveEqPreset(name: String?) = prefs()?.edit()?.putString(PREF_EQ_PRESET, name)?.apply()
    private fun loadEqPreset(): String? = prefs()?.getString(PREF_EQ_PRESET, null)
    private fun saveEqBands(bands: List<Short>) =
        prefs()?.edit()?.putString(PREF_EQ_BANDS, bands.joinToString(","))?.apply()
    private fun loadEqBands(): List<Short> =
        prefs()?.getString(PREF_EQ_BANDS, null)?.split(",")
            ?.mapNotNull { it.toShortOrNull() } ?: emptyList()

    // BassBoost
    private fun saveBassEnabled(v: Boolean) = prefs()?.edit()?.putBoolean(PREF_BASS_ENABLED, v)?.apply()
    private fun loadBassEnabled(): Boolean = prefs()?.getBoolean(PREF_BASS_ENABLED, false) ?: false
    private fun saveBassStrength(v: Int) = prefs()?.edit()?.putInt(PREF_BASS_STRENGTH, v)?.apply()
    private fun loadBassStrength(): Int = prefs()?.getInt(PREF_BASS_STRENGTH, 0) ?: 0

    // Virtualizer
    private fun saveVirtEnabled(v: Boolean) = prefs()?.edit()?.putBoolean(PREF_VIRT_ENABLED, v)?.apply()
    private fun loadVirtEnabled(): Boolean = prefs()?.getBoolean(PREF_VIRT_ENABLED, false) ?: false
    private fun saveVirtStrength(v: Int) = prefs()?.edit()?.putInt(PREF_VIRT_STRENGTH, v)?.apply()
    private fun loadVirtStrength(): Int = prefs()?.getInt(PREF_VIRT_STRENGTH, 0) ?: 0

    // LoudnessEnhancer
    private fun saveLoudEnabled(v: Boolean) = prefs()?.edit()?.putBoolean(PREF_LOUD_ENABLED, v)?.apply()
    private fun loadLoudEnabled(): Boolean = prefs()?.getBoolean(PREF_LOUD_ENABLED, false) ?: false
    private fun saveLoudGain(v: Int) = prefs()?.edit()?.putInt(PREF_LOUD_GAIN, v)?.apply()
    private fun loadLoudGain(): Int = prefs()?.getInt(PREF_LOUD_GAIN, 0) ?: 0

    // PresetReverb
    private fun saveReverbEnabled(v: Boolean) = prefs()?.edit()?.putBoolean(PREF_REVERB_ENABLED, v)?.apply()
    private fun loadReverbEnabled(): Boolean = prefs()?.getBoolean(PREF_REVERB_ENABLED, false) ?: false
    private fun saveReverbPreset(v: Int) = prefs()?.edit()?.putInt(PREF_REVERB_PRESET, v)?.apply()
    private fun loadReverbPreset(): Int = prefs()?.getInt(PREF_REVERB_PRESET, 0) ?: 0

    // ─── Lifecycle ───────────────────────────────────────────────

    /**
     * Создаёт все эффекты на [sessionId] и восстанавливает сохранённые
     * настройки. Идемпотентен: повторный вызов с тем же sessionId — no-op.
     *
     * Если один из эффектов не создаётся (device не поддерживает) —
     * логирует warning, но НЕ валит остальные. Это позволяет UI работать
     * даже если, например, PresetReverb недоступен на конкретном ROM.
     */
    fun attachOnce() {
        synchronized(lock) {
            if (sessionId == 0) {
                AppLog.w(TAG, "attachOnce: sessionId == 0 — skip")
                return
            }
            if (attached && attachedSessionId == sessionId) {
                AppLog.d(TAG, "attachOnce: already attached to sessionId=$sessionId — no-op")
                return
            }
            AppLog.i(TAG, "attachOnce: attaching to sessionId=$sessionId (was=$attachedSessionId)")
            releaseInternal()
            createEffects()
            restoreSettings()
            attached = true
            attachedSessionId = sessionId
        }
    }

    /**
     * Принудительно пересоздаёт все эффекты (полный release + create).
     * Используется при смене audio output route (BT/проводная гарнитура) —
     * эффекты могут «отвалиться» от нового output, хотя объект жив.
     *
     * **Внимание:** вызывает кратковременный audio gap (~5мс) без эффектов.
     * Для lightweight re-bind без gap см. [reattachLightweight].
     */
    fun reattachFull() {
        synchronized(lock) {
            if (sessionId == 0) return
            AppLog.i(TAG, "reattachFull: full release+recreate on sessionId=$sessionId")
            releaseInternal()
            createEffects()
            restoreSettings()
            attached = true
            attachedSessionId = sessionId
        }
    }

    /**
     * Lightweight re-bind: переключает `enabled` off→on на СУЩЕСТВУЮЩИХ
     * эффектах без release+recreate. Заставляет AudioFlinger перепривязать
     * эффекты к текущему output route **без разрыва** обработки → нет
     * всплеска громкости (Fix #334 в EqualizerHelper).
     *
     * Fallback: если toggle бросает исключение → [reattachFull].
     */
    fun reattachLightweight() {
        synchronized(lock) {
            if (!attached || attachedSessionId == 0) {
                AppLog.d(TAG, "reattachLightweight: not attached — skip")
                return
            }
            try {
                // Переключаем каждый эффект off→on на его текущем enabled-state.
                // Если эффект не создан (null) — пропускаем.
                toggleEnabled(equalizer, "Equalizer")
                toggleEnabled(bassBoost, "BassBoost")
                toggleEnabled(virtualizer, "Virtualizer")
                toggleEnabled(reverb, "PresetReverb")
                toggleEnabled(loudness, "LoudnessEnhancer")
                AppLog.i(TAG, "reattachLightweight: toggle OK — no audio gap")
            } catch (e: Exception) {
                AppLog.w(TAG, "reattachLightweight: toggle failed — falling back to full reattach", e)
                reattachFull()
            }
        }
    }

    private fun toggleEnabled(effect: android.media.audiofx.AudioEffect?, name: String) {
        val e = effect ?: return
        val was = try { e.enabled } catch (_: Exception) { return }
        e.enabled = false
        e.enabled = was
    }

    private fun createEffects() {
        // Equalizer (legacy) — priority 0
        try {
            equalizer = Equalizer(0, sessionId)
            AppLog.i(TAG, "Equalizer created: bands=${equalizer?.numberOfBands}, " +
                "range=${equalizer?.bandLevelRange?.toList()}")
        } catch (e: Exception) {
            AppLog.w(TAG, "Equalizer init failed: ${e.message}")
            equalizer = null
        }
        // BassBoost — priority 0
        try {
            bassBoost = BassBoost(0, sessionId)
        } catch (e: Exception) {
            AppLog.w(TAG, "BassBoost init failed: ${e.message}")
            bassBoost = null
        }
        // Virtualizer — priority 0
        try {
            virtualizer = Virtualizer(0, sessionId)
        } catch (e: Exception) {
            AppLog.w(TAG, "Virtualizer init failed: ${e.message}")
            virtualizer = null
        }
        // PresetReverb — priority 0. На некоторых ROM ломает звук →
        // default off, switch в UI с предупреждением.
        try {
            reverb = PresetReverb(0, sessionId)
        } catch (e: Exception) {
            AppLog.w(TAG, "PresetReverb init failed: ${e.message}")
            reverb = null
        }
        // LoudnessEnhancer — API 19+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                loudness = LoudnessEnhancer(sessionId)
            } catch (e: Exception) {
                AppLog.w(TAG, "LoudnessEnhancer init failed: ${e.message}")
                loudness = null
            }
        }
        // DynamicsProcessing — API 28+. TODO Этап 2: advanced pre-EQ/post-EQ/limiter.
        // Пока не создаём — legacy Equalizer берёт всё на себя.
    }

    private fun restoreSettings() {
        // Equalizer
        val eqEnabled = loadEqEnabled()
        val savedPreset = loadEqPreset()
        val savedBands = loadEqBands()
        val eq = equalizer
        if (eq != null) {
            try {
                if (savedPreset != null) {
                    val preset = EqualizerPreset.ALL.find { it.name == savedPreset }
                    if (preset != null) {
                        applyPresetBands(eq, preset.bands)
                        currentPresetName = preset.name
                    } else if (savedBands.isNotEmpty()) {
                        applyBandsList(eq, savedBands)
                        currentPresetName = null
                    }
                } else if (savedBands.isNotEmpty()) {
                    applyBandsList(eq, savedBands)
                    currentPresetName = null
                } else {
                    currentPresetName = EqualizerPreset.DEFAULT.name
                }
                eq.enabled = eqEnabled
            } catch (e: Exception) {
                AppLog.w(TAG, "restoreSettings: Equalizer failed: ${e.message}")
            }
        }
        // BassBoost
        bassBoost?.let {
            try {
                it.setStrength(loadBassStrength().toShort())
                it.enabled = loadBassEnabled()
            } catch (e: Exception) { AppLog.w(TAG, "restoreSettings: BassBoost: ${e.message}") }
        }
        // Virtualizer
        virtualizer?.let {
            try {
                it.setStrength(loadVirtStrength().toShort())
                it.enabled = loadVirtEnabled()
            } catch (e: Exception) { AppLog.w(TAG, "restoreSettings: Virtualizer: ${e.message}") }
        }
        // PresetReverb
        reverb?.let {
            try {
                it.setPreset(loadReverbPreset().toShort())
                it.enabled = loadReverbEnabled()
            } catch (e: Exception) { AppLog.w(TAG, "restoreSettings: Reverb: ${e.message}") }
        }
        // LoudnessEnhancer
        loudness?.let {
            try {
                it.setTargetGain(loadLoudGain())
                it.enabled = loadLoudEnabled()
            } catch (e: Exception) { AppLog.w(TAG, "restoreSettings: Loudness: ${e.message}") }
        }
        AppLog.i(TAG, "restoreSettings: done (eq=$eqEnabled, preset=$currentPresetName, " +
            "bass=${loadBassEnabled()}/${loadBassStrength()}, " +
            "virt=${loadVirtEnabled()}/${loadVirtStrength()}, " +
            "loud=${loadLoudEnabled()}/${loadLoudGain()}mB, " +
            "reverb=${loadReverbEnabled()}/${reverbPresetNames.getOrNull(loadReverbPreset())})")
    }

    @Volatile
    var currentPresetName: String? = null
        private set

    private fun applyPresetBands(eq: Equalizer, bandsDb: List<Float>) {
        val upper = minOf(bandsDb.lastIndex, eq.numberOfBands.toInt() - 1)
        for (i in 0..upper) {
            eq.setBandLevel(i.toShort(), (bandsDb[i] * DB_TO_MILLIBEL).toInt().toShort())
        }
    }

    private fun applyBandsList(eq: Equalizer, bandsmB: List<Short>) {
        val upper = minOf(bandsmB.lastIndex, eq.numberOfBands.toInt() - 1)
        for (i in 0..upper) {
            eq.setBandLevel(i.toShort(), bandsmB[i])
        }
    }

    // ─── Equalizer (legacy) API ──────────────────────────────────

    fun setEqEnabled(on: Boolean) {
        synchronized(lock) {
            saveEqEnabled(on)
            val eq = equalizer ?: return
            try { eq.enabled = on } catch (e: Exception) { AppLog.w(TAG, "setEqEnabled: ${e.message}") }
        }
    }

    fun isEqEnabled(): Boolean = synchronized(lock) {
        val eq = equalizer ?: return false
        try { eq.enabled } catch (e: Exception) { false }
    }

    fun isEqSavedEnabled(): Boolean = loadEqEnabled()

    fun setBand(bandIndex: Int, gainMilliBels: Short) {
        if (bandIndex < 0) return
        synchronized(lock) {
            currentPresetName = null
            saveEqPreset(null)
            // #EQ-BANDS-PERSIST: всегда обновляем ПОЛНЫЙ сохранённый список полос
            // (9 UI-слотов). Раньше для полос в диапазоне устройства сохраняли
            // только numberOfBands значений с устройства → высокие слоты (5-8),
            // которых нет на 5-полосном устройстве, терялись при следующем сдвиге
            // низкой полосы, и ползунки «сбрасывались» при возврате на вкладку.
            val current = loadEqBands().toMutableList()
            while (current.size <= bandIndex) current.add(0.toShort())
            current[bandIndex] = gainMilliBels
            saveEqBands(current)
            // Применяем к устройству только если полоса в его диапазоне.
            val eq = equalizer
            if (eq != null) {
                try {
                    if (bandIndex in 0 until eq.numberOfBands.toInt()) {
                        eq.setBandLevel(bandIndex.toShort(), gainMilliBels)
                    }
                } catch (e: Exception) { AppLog.w(TAG, "setBand: ${e.message}") }
            }
        }
    }

    fun getBands(): List<Short> = synchronized(lock) {
        val eq = equalizer ?: return emptyList()
        try {
            (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()) }
        } catch (e: Exception) { emptyList() }
    }

    fun getNumberOfBands(): Short = synchronized(lock) {
        equalizer?.numberOfBands ?: 0
    }

    fun getBandLevelRange(): ShortArray = synchronized(lock) {
        try { equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500) }
        catch (e: Exception) { shortArrayOf(-1500, 1500) }
    }

    fun getCenterFreq(band: Short): Int = synchronized(lock) {
        try { equalizer?.getCenterFreq(band) ?: 0 } catch (e: Exception) { 0 }
    }

    fun applyPreset(preset: EqualizerPreset) {
        synchronized(lock) {
            val eq = equalizer
            if (eq != null) {
                try { applyPresetBands(eq, preset.bands) }
                catch (e: Exception) { AppLog.w(TAG, "applyPreset: ${e.message}") }
            }
            currentPresetName = preset.name
            saveEqPreset(preset.name)
            saveEqBands(preset.bands.map { (it * DB_TO_MILLIBEL).toInt().toShort() })
        }
    }

    fun getSavedPresetName(): String? = loadEqPreset()
    fun getSavedBands(): List<Short> = loadEqBands()
    fun savePreset(name: String?) = saveEqPreset(name)
    fun saveBands(bands: List<Short>) = saveEqBands(bands)

    // ─── BassBoost API ───────────────────────────────────────────
    // strength: 0..1000 (как в Equalizer v6.3.5.7 ye/m0$a.smali)

    fun setBassBoostEnabled(on: Boolean) {
        synchronized(lock) {
            saveBassEnabled(on)
            bassBoost?.let { try { it.enabled = on } catch (e: Exception) { AppLog.w(TAG, "setBassBoostEnabled: ${e.message}") } }
        }
    }

    fun isBassBoostEnabled(): Boolean = synchronized(lock) {
        bassBoost?.let { try { it.enabled } catch (e: Exception) { false } } ?: false
    }

    fun isBassBoostSavedEnabled(): Boolean = loadBassEnabled()

    /** strength: 0..1000 */
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        synchronized(lock) {
            saveBassStrength(clamped)
            bassBoost?.let { try { it.setStrength(clamped.toShort()) } catch (e: Exception) { AppLog.w(TAG, "setBassBoostStrength: ${e.message}") } }
        }
    }

    fun getBassBoostStrength(): Int = loadBassStrength()

    // ─── Virtualizer API ─────────────────────────────────────────
    // strength: 0..1000

    fun setVirtualizerEnabled(on: Boolean) {
        synchronized(lock) {
            saveVirtEnabled(on)
            // Если сейчас SCO-suspend, пользователь меняет желание →
            // обновляем saved-флаг, чтобы restoreAfterSco вернул новое.
            if (scoSuspended) savedVirtEnabledBeforeSco = on
            virtualizer?.let { try { it.enabled = on } catch (e: Exception) { AppLog.w(TAG, "setVirtualizerEnabled: ${e.message}") } }
        }
    }

    fun isVirtualizerEnabled(): Boolean = synchronized(lock) {
        virtualizer?.let { try { it.enabled } catch (e: Exception) { false } } ?: false
    }

    fun isVirtualizerSavedEnabled(): Boolean = loadVirtEnabled()

    /** strength: 0..1000 */
    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        synchronized(lock) {
            saveVirtStrength(clamped)
            virtualizer?.let { try { it.setStrength(clamped.toShort()) } catch (e: Exception) { AppLog.w(TAG, "setVirtualizerStrength: ${e.message}") } }
        }
    }

    fun getVirtualizerStrength(): Int = loadVirtStrength()

    // ─── PresetReverb API ────────────────────────────────────────
    // preset: 0..6 (см. reverbPresetNames)

    fun setReverbEnabled(on: Boolean) {
        synchronized(lock) {
            saveReverbEnabled(on)
            // Если сейчас SCO-suspend — обновляем saved-флаг (см. setVirtualizerEnabled).
            if (scoSuspended) savedReverbEnabledBeforeSco = on
            reverb?.let { try { it.enabled = on } catch (e: Exception) { AppLog.w(TAG, "setReverbEnabled: ${e.message}") } }
        }
    }

    fun isReverbEnabled(): Boolean = synchronized(lock) {
        reverb?.let { try { it.enabled } catch (e: Exception) { false } } ?: false
    }

    fun isReverbSavedEnabled(): Boolean = loadReverbEnabled()

    /** preset: 0..6 */
    fun setReverbPreset(preset: Int) {
        val clamped = preset.coerceIn(0, 6)
        synchronized(lock) {
            saveReverbPreset(clamped)
            reverb?.let { try { it.setPreset(clamped.toShort()) } catch (e: Exception) { AppLog.w(TAG, "setReverbPreset: ${e.message}") } }
        }
    }

    fun getReverbPreset(): Int = loadReverbPreset()

    // ─── LoudnessEnhancer API (API 19+) ──────────────────────────
    // gainmB: 0..1500 millibels (0..+15 dB)

    fun setLoudnessEnabled(on: Boolean) {
        synchronized(lock) {
            saveLoudEnabled(on)
            loudness?.let { try { it.enabled = on } catch (e: Exception) { AppLog.w(TAG, "setLoudnessEnabled: ${e.message}") } }
        }
    }

    fun isLoudnessEnabled(): Boolean = synchronized(lock) {
        loudness?.let { try { it.enabled } catch (e: Exception) { false } } ?: false
    }

    fun isLoudnessSavedEnabled(): Boolean = loadLoudEnabled()

    /** gainmB: 0..1500 millibels (0..+15 dB) */
    fun setLoudnessTargetGain(gainmB: Int) {
        val clamped = gainmB.coerceIn(0, 1500)
        synchronized(lock) {
            saveLoudGain(clamped)
            loudness?.let { try { it.setTargetGain(clamped) } catch (e: Exception) { AppLog.w(TAG, "setLoudnessTargetGain: ${e.message}") } }
        }
    }

    fun getLoudnessTargetGain(): Int = loadLoudGain()

    // ─── Custom presets (Этап 4 EQUALIZER_INTEGRATION_PLAN.md) ────
    // Применение/снимок полного состояния всех 5 эффектов одним вызовом.
    // CustomPresetStore хранит пресеты в JSON-файле (вместо Room —
    // Room не настроен в проекте, JSON-файл по паттерну проекта).

    /**
     * Применить пользовательский пресет — восстанавливает ВСЕ эффекты
     * (EQ bands + enabled flags + Bass/Virt/Loud/Reverb) одним вызовом.
     * Используется из [EqualizerScreen] при тапе на custom preset card.
     *
     * После применения currentPresetName устанавливается в имя пресета
     * (чтобы UI показывал активный). Если эффект не создан (null) —
     * значения всё равно сохраняются в prefs и восстановятся при
     * следующем attach (см. restoreSettings).
     */
    fun applyCustomPreset(preset: re.pinok.media.CustomPreset) {
        synchronized(lock) {
            // Equalizer bands + enabled.
            val eq = equalizer
            if (eq != null) {
                try {
                    val upper = minOf(preset.eqBands.lastIndex, eq.numberOfBands.toInt() - 1)
                    for (i in 0..upper) {
                        eq.setBandLevel(i.toShort(), preset.eqBands[i])
                    }
                    eq.enabled = preset.eqEnabled
                } catch (e: Exception) {
                    AppLog.w(TAG, "applyCustomPreset: EQ bands: ${e.message}")
                }
            }
            saveEqEnabled(preset.eqEnabled)
            saveEqPreset(preset.name)
            saveEqBands(preset.eqBands)
            currentPresetName = preset.name

            // BassBoost.
            saveBassEnabled(preset.bassEnabled)
            saveBassStrength(preset.bassStrength)
            val bb = bassBoost
            if (bb != null) {
                try {
                    bb.setStrength(preset.bassStrength.coerceIn(0, 1000).toShort())
                    bb.enabled = preset.bassEnabled
                } catch (e: Exception) {
                    AppLog.w(TAG, "applyCustomPreset: BassBoost: ${e.message}")
                }
            }

            // Virtualizer.
            saveVirtEnabled(preset.virtEnabled)
            saveVirtStrength(preset.virtStrength)
            val virt = virtualizer
            if (virt != null) {
                try {
                    virt.setStrength(preset.virtStrength.coerceIn(0, 1000).toShort())
                    virt.enabled = preset.virtEnabled
                } catch (e: Exception) {
                    AppLog.w(TAG, "applyCustomPreset: Virtualizer: ${e.message}")
                }
            }

            // LoudnessEnhancer.
            saveLoudEnabled(preset.loudEnabled)
            saveLoudGain(preset.loudGainmB)
            val loud = loudness
            if (loud != null) {
                try {
                    loud.setTargetGain(preset.loudGainmB.coerceIn(0, 1500))
                    loud.enabled = preset.loudEnabled
                } catch (e: Exception) {
                    AppLog.w(TAG, "applyCustomPreset: Loudness: ${e.message}")
                }
            }

            // PresetReverb.
            saveReverbEnabled(preset.reverbEnabled)
            saveReverbPreset(preset.reverbPreset)
            val rev = reverb
            if (rev != null) {
                try {
                    rev.setPreset(preset.reverbPreset.coerceIn(0, 6).toShort())
                    rev.enabled = preset.reverbEnabled
                } catch (e: Exception) {
                    AppLog.w(TAG, "applyCustomPreset: Reverb: ${e.message}")
                }
            }

            AppLog.i(TAG, "applyCustomPreset: '${preset.name}' applied " +
                "(eq=${preset.eqEnabled}, bass=${preset.bassEnabled}/${preset.bassStrength}, " +
                "virt=${preset.virtEnabled}/${preset.virtStrength}, " +
                "loud=${preset.loudEnabled}/${preset.loudGainmB}mB, " +
                "reverb=${preset.reverbEnabled}/${preset.reverbPreset})")
        }
    }

    /**
     * Снимок текущего состояния эффектов для сохранения как custom preset.
     * Читает SAVED значения из prefs (не живые — живые могут быть null
     * если engine не attached, а saved всегда актуальны).
     *
     * @param name имя нового пресета.
     * @param id существующий ID (для update) или 0 для нового.
     */
    fun snapshotCustomPreset(name: String, id: Long = 0L): CustomPreset {
        val bands = loadEqBands()
        val eqEnabled = loadEqEnabled()
        val bassEnabled = loadBassEnabled()
        val bassStrength = loadBassStrength()
        val virtEnabled = loadVirtEnabled()
        val virtStrength = loadVirtStrength()
        val loudEnabled = loadLoudEnabled()
        val loudGain = loadLoudGain()
        val reverbEnabled = loadReverbEnabled()
        val reverbPreset = loadReverbPreset()
        return CustomPreset(
            id = id,
            name = name,
            eqBands = bands,
            eqEnabled = eqEnabled,
            bassEnabled = bassEnabled,
            bassStrength = bassStrength,
            virtEnabled = virtEnabled,
            virtStrength = virtStrength,
            loudEnabled = loudEnabled,
            loudGainmB = loudGain,
            reverbEnabled = reverbEnabled,
            reverbPreset = reverbPreset,
            createdAt = System.currentTimeMillis(),
        )
    }

    // ─── SCO route handling (Bluetooth-звонковая гарнитура) ──────
    // SCO = 8kHz/16kHz моно, узкая полоса, для голоса (HFP profile).
    // Virtualizer на моно-SCO = фазовые артефакты, «подводный звук».
    // PresetReverb на SCO = эхо, мешает разговору. Оба эффекта надо
    // временно отключать при переходе на SCO, и восстанавливать при
    // возврате на A2DP/speaker/wired.
    //
    // A2DP — стерео музыкальный профиль (SBC/AAC/aptX/LDAC), на нём
    // эффекты работают нормально — suspend НЕ нужен.
    //
    // Вызывается из PlayerService при AudioDeviceCallback срабатывании
    // (см. AudioRouteLogger.isScoRoute()). Идемпотентен — повторный
    // вызов с тем же состоянием — no-op.

    @Volatile
    private var scoSuspended: Boolean = false

    /**
     * True если эффекты сейчас приостановлены из-за SCO-маршрута.
     * UI может показывать предупреждение «эффекты отключены (звонок)».
     */
    fun isScoSuspended(): Boolean = scoSuspended

    /**
     * Временно отключает Virtualizer + PresetReverb (но не Equalizer/Bass/
     * Loudness — они не вредны на моно). Запоминает их предыдущее состояние,
     * чтобы [restoreAfterSco] вернул его при возврате на нормальный output.
     *
     * Вызывается из PlayerService при детекции [AudioRouteLogger.isScoRoute].
     */
    fun suspendForSco() {
        synchronized(lock) {
            if (scoSuspended) {
                AppLog.d(TAG, "suspendForSco: already suspended — skip")
                return
            }
            scoSuspended = true
            AppLog.i(TAG, "suspendForSco: SCO route detected — disabling Virtualizer+Reverb " +
                "(mono 8kHz, effects harmful). Equalizer/Bass/Loudness stay on.")
            // Запоминаем сохранённое состояние (из prefs, не живое —
            // живое может быть уже false если пользователь выключил сам).
            savedVirtEnabledBeforeSco = loadVirtEnabled()
            savedReverbEnabledBeforeSco = loadReverbEnabled()
            // Отключаем живые эффекты (но НЕ записываем в prefs —
            // restoreAfterSco вернёт как было).
            virtualizer?.let {
                try { it.enabled = false } catch (e: Exception) { AppLog.w(TAG, "suspendForSco: virt: ${e.message}") }
            }
            reverb?.let {
                try { it.enabled = false } catch (e: Exception) { AppLog.w(TAG, "suspendForSco: reverb: ${e.message}") }
            }
        }
    }

    /**
     * Восстанавливает Virtualizer + PresetReverb после возврата с SCO
     * на нормальный output (A2DP/speaker/wired). Восстанавливает то
     * состояние, которое было сохранено в [suspendForSco].
     *
     * Если пользователь за время SCO сам выключил эффект в UI —
     * [setVirtualizerEnabled]/[setReverbEnabled] перезапишут prefs,
     * и restoreAfterSco вернёт новое (выключенное) состояние. Это
     * корректно: желание пользователя важнее auto-suspend'а.
     */
    fun restoreAfterSco() {
        synchronized(lock) {
            if (!scoSuspended) {
                AppLog.d(TAG, "restoreAfterSco: not suspended — skip")
                return
            }
            scoSuspended = false
            val virtOn = savedVirtEnabledBeforeSco
            val revOn = savedReverbEnabledBeforeSco
            AppLog.i(TAG, "restoreAfterSco: restoring Virtualizer=$virtOn, Reverb=$revOn")
            virtualizer?.let {
                try { it.enabled = virtOn } catch (e: Exception) { AppLog.w(TAG, "restoreAfterSco: virt: ${e.message}") }
            }
            reverb?.let {
                try { it.enabled = revOn } catch (e: Exception) { AppLog.w(TAG, "restoreAfterSco: reverb: ${e.message}") }
            }
            savedVirtEnabledBeforeSco = false
            savedReverbEnabledBeforeSco = false
        }
    }

    @Volatile private var savedVirtEnabledBeforeSco: Boolean = false
    @Volatile private var savedReverbEnabledBeforeSco: Boolean = false

    // ─── Lifecycle: release ──────────────────────────────────────

    /**
     * Освобождает все эффекты. Безопасно вызывать несколько раз.
     * НЕ сбрасывает [currentPresetName] и НЕ чистит prefs — настройки
     * сохраняются для следующего attach.
     */
    fun release() {
        synchronized(lock) { releaseInternal() }
    }

    private fun releaseInternal() {
        releaseEffect(equalizer, "Equalizer"); equalizer = null
        releaseEffect(bassBoost, "BassBoost"); bassBoost = null
        releaseEffect(virtualizer, "Virtualizer"); virtualizer = null
        releaseEffect(reverb, "PresetReverb"); reverb = null
        releaseEffect(loudness, "LoudnessEnhancer"); loudness = null
        attached = false
        attachedSessionId = 0
    }

    private fun releaseEffect(effect: android.media.audiofx.AudioEffect?, name: String) {
        val e = effect ?: return
        try {
            e.enabled = false
            e.release()
        } catch (ex: Exception) {
            AppLog.w(TAG, "release $name: ${ex.message}")
        }
    }

    /** True если хотя бы один эффект создан (attach прошёл успешно). */
    fun isAttached(): Boolean = synchronized(lock) { attached }

    private companion object {
        private const val TAG = "AudioEffectsEngine"
        // SharedPreferences keys — в том же файле "equalizer" что у EqualizerHelper
        // для обратной совместимости. Новые ключи с префиксами bb_/virt_/loud_/reverb_.
        private const val PREF_EQ_ENABLED = "eq_enabled"
        private const val PREF_EQ_PRESET = "eq_preset"
        private const val PREF_EQ_BANDS = "eq_bands"
        private const val PREF_BASS_ENABLED = "bb_switch"
        private const val PREF_BASS_STRENGTH = "bb_slider"
        private const val PREF_VIRT_ENABLED = "vir_switch"
        private const val PREF_VIRT_STRENGTH = "vir_slider"
        private const val PREF_LOUD_ENABLED = "loud_switch"
        private const val PREF_LOUD_GAIN = "loud_slider"
        private const val PREF_REVERB_ENABLED = "reverb_switch"
        private const val PREF_REVERB_PRESET = "reverb_preset"
    }
}
