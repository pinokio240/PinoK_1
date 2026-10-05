// File: media/EqualizerHelper.kt
package re.pinok.media

import re.pinok.SovaApp
import re.pinok.data.model.EqualizerPreset
import re.pinok.util.AppLog

/**
 * @deprecated Используйте [AudioEffectsEngine] — единый движок 6 эффектов
 * (Equalizer + BassBoost + Virtualizer + PresetReverb + LoudnessEnhancer
 * + DynamicsProcessing). Этот класс оставлен как тонкий facade для
 * обратной совместимости с UI (`AudioPlayerScreen`) — все методы
 * делегируют в единственный shared [engine].
 *
 * Миграция: `PlayerService` создаёт `engine` (один экземпляр на
 * audio session). `EqualizerHelper` обращается к нему через [engine].
 * Новые эффекты (bass/virt/loud/reverb) — только через [engine] напрямую.
 */
object EqualizerHelper {

    private const val TAG = "EqualizerHelper"

    /**
     * Shared engine — singleton. Создаётся в [PlayerService] при первом
     * `attachOnce(sessionId)`. До этого null — все методы facade
     * логируют warning и no-op.
     */
    @Volatile
    private var engine: AudioEffectsEngine? = null

    /**
     * #REVERB-AUX: мост UI → сервис. PresetReverb — AUX-эффект: кроме
     * включения в engine его надо привязать к плееру через
     * ExoPlayer.setAuxEffectInfo (на Player/MediaController метода нет).
     * PlayerService ставит сюда лямбду привязки при создании сессии;
     * UI-слой (PlayerConnection) вызывает после каждого изменения Reverb.
     * null (сервис не создан/уничтожен) — привязка выполнится при attach.
     */
    @Volatile
    var auxBindingHook: (() -> Unit)? = null

    /** Текущий пресет (читается из engine). null = пользовательский. */
    val currentPresetName: String?
        get() = engine?.currentPresetName

    private fun prefs() = try {
        SovaApp.get().getSharedPreferences("equalizer", 0)
    } catch (e: Exception) { null }

    // ─── Engine lifecycle (вызывает PlayerService) ───────────────

    // P3.4 (2026-10): attachOnce() и release() оба @Synchronized на этом
    // singleton-объекте → запись engine и его обнуление атомарны относительно
    // друг друга. engine() getter ниже также @Synchronized → UI никогда не
    // увидит "engine == released instance" в окне между current.release()
    // и engine = null/new. @Volatile оставлен для memory-visibility на случай
    // не-синхронизированных внутренних читателей (engine? в saveEnabled и т.д.),
    // но публичный доступ идёт через synchronized-геттер.

    /**
     * Создаёт или заменяет shared engine. Вызывается из [PlayerService]
     * при `onAudioSessionIdChanged` / после `player.build()`.
     *
     * Если sessionId совпадает с текущим engine — engine.attachOnce()
     * будет no-op (идемпотентно).
     */
    @Synchronized
    fun attachOnce(sessionId: Int) {
        if (sessionId == 0) {
            AppLog.w(TAG, "attachOnce: sessionId == 0 — skip")
            return
        }
        val current = engine
        if (current != null && current.attachedSessionId == sessionId && current.isAttached()) {
            AppLog.d(TAG, "attachOnce: engine already attached to sessionId=$sessionId — no-op")
            return
        }
        // Если sessionId сменился — release старый engine и создай новый.
        if (current != null && current.attachedSessionId != sessionId) {
            AppLog.i(TAG, "attachOnce: sessionId changed ${current.attachedSessionId}→$sessionId — recreating engine")
            current.release()
            engine = null
        }
        val e = engine ?: AudioEffectsEngine(sessionId).also { engine = it }
        e.attachOnce()
    }

    /** Lightweight re-bind без audio gap (Fix #334). */
    fun reattach() {
        val e = engine
        if (e == null) {
            // P3.4: после release() engine == null — UI-вызов бесполезен,
            // логируем (раньше был silent no-op).
            AppLog.w(TAG, "reattach: engine == null (released or not attached) — no-op")
            return
        }
        e.reattachLightweight()
    }

    /** Полный release+recreate (если lightweight не помог). */
    fun reattachFull() {
        val e = engine
        if (e == null) {
            // P3.4: после release() engine == null — UI-вызов бесполезен,
            // логируем (раньше был silent no-op).
            AppLog.w(TAG, "reattachFull: engine == null (released or not attached) — no-op")
            return
        }
        e.reattachFull()
    }

    // P3.4 (2026-10): теперь зануляет engine = null после release().
    // Раньше engine оставался non-null чтобы UI мог читать saved-state
    // через engine?.getSavedPresetName() и т.д. — но AudioEffectsEngine
    // после releaseInternal() всё равно читает saved-state из prefs
    // (loadEqEnabled/loadEqPreset/loadEqBands и т.д. не требуют живых
    // эффектов), поэтому зануление безопасно. EqualizerHelper же имеет
    // prefs-fallback в getSavedPresetName()/getSavedBands()/isSavedEnabled()
    // — UI продолжит получать корректные сохранённые значения из prefs
    // даже с engine == null. Бонус: UI-сеттеры (saveEnabled/setEnabled/
    // applyPreset) будут явно логировать warning вместо silent no-op
    // на released-синглтоне, что упрощает отладку «почему ползунок
    // не применён».
    @Synchronized
    fun release() {
        val current = engine ?: return
        current.release()
        engine = null
    }

    // ─── Equalizer (legacy) API — delegate to engine ─────────────

    // P3.4: saveEnabled/setEnabled/applyPreset — UI-сеттеры, которые раньше
    // silent no-op'или при engine == null (после release() / до attachOnce).
    // Теперь логируем warning, чтобы в logcat было видно «сеттер вызван без
    // живого engine». Сам вызов остаётся no-op (нечего применять) — но без
    // engine?.setEqEnabled() настройки НЕ пишутся в prefs намеренно: эти
    // три метода — чисто «применить к устройству сейчас», без persist.
    // Persist включения делается внутри AudioEffectsEngine.setEqEnabled
    // (saveEqEnabled). Для persist-без-engine есть loadEnabled/saveEnabled
    // prefs-fallback в EqualizerHelper.loadEnabled(), но НЕ в saveEnabled().

    fun saveEnabled(enabled: Boolean) {
        val e = engine
        if (e == null) {
            AppLog.w(TAG, "saveEnabled($enabled): engine == null — no-op (call after release or before attach)")
            return
        }
        e.setEqEnabled(enabled)
    }

    fun loadEnabled(): Boolean = engine?.isEqSavedEnabled() ?: run {
        try { SovaApp.get().getSharedPreferences("equalizer", 0).getBoolean("eq_enabled", false) }
        catch (_: Exception) { false }
    }

    fun savePreset(name: String?) {
        // Сохраняем даже если engine null (SovaApp может быть не готов).
        engine?.savePreset(name) ?: run {
            try { SovaApp.get().getSharedPreferences("equalizer", 0)
                .edit().putString("eq_preset", name).apply() } catch (_: Exception) {}
        }
    }

    fun loadPreset(): String? = engine?.getSavedPresetName() ?: run {
        try { SovaApp.get().getSharedPreferences("equalizer", 0).getString("eq_preset", null) }
        catch (_: Exception) { null }
    }

    fun saveBands(bands: List<Short>) {
        engine?.saveBands(bands) ?: run {
            try { SovaApp.get().getSharedPreferences("equalizer", 0)
                .edit().putString("eq_bands", bands.joinToString(",")).apply() } catch (_: Exception) {}
        }
    }

    fun loadBands(): List<Short> = engine?.getSavedBands() ?: run {
        try {
            val str = SovaApp.get().getSharedPreferences("equalizer", 0).getString("eq_bands", null)
            str?.split(",")?.mapNotNull { it.toShortOrNull() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun applyPreset(preset: EqualizerPreset) {
        val e = engine
        if (e == null) {
            // P3.4: без engine — silent no-op был раньше; теперь логируем.
            // Persist пресета доступен через EqualizerHelper.savePreset() /
            // applyCustomPresetPersist() — они пишут в prefs без движка.
            AppLog.w(TAG, "applyPreset(${preset.name}): engine == null — no-op (call after release or before attach)")
            return
        }
        e.applyPreset(preset)
    }

    /**
     * #EQ-SAVE-NULL-ENGINE: снимок текущего состояния эффектов БЕЗ требования
     * живого engine (engine появляется только после первой сыгранной дорожки).
     * Читает saved-значения из prefs теми же ключами, что AudioEffectsEngine.
     * Полосы: saved → live (engine) → полосы активного встроенного пресета →
     * 9 нулей. Пустой eqBands недопустим: CustomPresetStore.load() фильтрует
     * такие пресеты как невалидные → «исчезновение» после перезапуска.
     */
    fun snapshotCustomPreset(name: String): CustomPreset {
        val bands = loadBands().ifEmpty { engine?.getBands().orEmpty() }.ifEmpty {
            val presetName = getSavedPresetName()
            EqualizerPreset.ALL.find { it.name == presetName }
                ?.bands?.map { (it * 100).toInt().toShort() }
                ?: List(9) { 0.toShort() }
        }
        val p = prefs()
        fun b(key: String): Boolean = p?.getBoolean(key, false) ?: false
        fun i(key: String): Int = p?.getInt(key, 0) ?: 0
        return CustomPreset(
            id = 0L,
            name = name,
            eqBands = bands,
            eqEnabled = b(AudioEffectsEngine.

            PREF_EQ_ENABLED),
            bassEnabled = b(AudioEffectsEngine.PREF_BASS_ENABLED),
            bassStrength = i(AudioEffectsEngine.PREF_BASS_STRENGTH),
            virtEnabled = b(AudioEffectsEngine.PREF_VIRT_ENABLED),
            virtStrength = i(AudioEffectsEngine.PREF_VIRT_STRENGTH),
            loudEnabled = b(AudioEffectsEngine.PREF_LOUD_ENABLED),
            loudGainmB = i(AudioEffectsEngine.PREF_LOUD_GAIN),
            reverbEnabled = b(AudioEffectsEngine.PREF_REVERB_ENABLED),
            reverbPreset = i(AudioEffectsEngine.PREF_REVERB_PRESET),
            createdAt = System.currentTimeMillis(),
        )
    }

    // #EQ-SAVE-NULL-ENGINE: применить custom-пресет без живого engine —
    // все значения пишутся в prefs, restoreSettings подхватит при attach.
    //
    // P3.4 (2026-10): сохранение eq_enabled тоже перенесено в единый
    // prefs-editor chain (раньше звало saveEnabled(), который без engine
    // silent-no-op'ил → eq_enabled не персистился в null-engine path).
    fun applyCustomPresetPersist(preset: CustomPreset) {
        val e = engine
        if (e != null) {
            e.applyCustomPreset(preset)
            return
        }
        // engine == null: пишем всё в prefs напрямую (restoreSettings
        // в AudioEffectsEngine.attachOnce() подхватит при первом attach).
        saveBands(preset.eqBands)
        savePreset(preset.name)
        prefs()?.edit()
            ?.putBoolean(AudioEffectsEngine.PREF_EQ_ENABLED, preset.eqEnabled)
            ?.putBoolean(AudioEffectsEngine.PREF_BASS_ENABLED, preset.bassEnabled)
            ?.putInt(AudioEffectsEngine.PREF_BASS_STRENGTH, preset.bassStrength)
            ?.putBoolean(AudioEffectsEngine.PREF_VIRT_ENABLED, preset.virtEnabled)
            ?.putInt(AudioEffectsEngine.PREF_VIRT_STRENGTH, preset.virtStrength)
            ?.putBoolean(AudioEffectsEngine.PREF_LOUD_ENABLED, preset.loudEnabled)
            ?.putInt(AudioEffectsEngine.PREF_LOUD_GAIN, preset.loudGainmB)
            ?.putBoolean(AudioEffectsEngine.PREF_REVERB_ENABLED, preset.reverbEnabled)
            ?.putInt(AudioEffectsEngine.PREF_REVERB_PRESET, preset.reverbPreset)
            ?.apply()
        AppLog.i("EqualizerHelper", "applyCustomPresetPersist: '${preset.name}' в prefs (engine null)")
    }

    fun setBand(bandIndex: Int, gainMilliBels: Short) {
        val e = engine
        if (e != null) {
            e.setBand(bandIndex, gainMilliBels)
            return
        }
        // #EQ-SAVE-NULL-ENGINE: без движка (музыка в этом процессе ещё не
        // играла) раньше был тихий no-op — ползунки и «Сохранить пресет»
        // не работали. Пишем в prefs напрямую: restoreSettings подхватит.
        if (bandIndex < 0) return
        saveBands(
            loadBands().toMutableList().apply {
                while (size <= bandIndex) add(0.toShort())
                this[bandIndex] = gainMilliBels
            }
        )
    }

    fun getBands(): List<Short> = engine?.getBands() ?: emptyList()

    fun setEnabled(enabled: Boolean) {
        // P3.4: UI-сеттер. Без engine — silent no-op был раньше; теперь логируем
        // warning. Persist-флаг в AudioEffectsEngine.setEqEnabled не пишется
        // без engine — но UI обычно вызывает setEnabled для мгновенного эффекта,
        // а persist включения делает EqualizerHelper.saveEnabled()/applyCustomPresetPersist().
        val e = engine
        if (e == null) {
            AppLog.w(TAG, "setEnabled($enabled): engine == null — no-op (call after release or before attach)")
            return
        }
        e.setEqEnabled(enabled)
    }

    fun isEnabled(): Boolean = engine?.isEqEnabled() ?: false

    fun isSavedEnabled(): Boolean = engine?.isEqSavedEnabled() ?: loadEnabled()

    fun getSavedPresetName(): String? = engine?.getSavedPresetName() ?: loadPreset()

    fun getSavedBands(): List<Short> = engine?.getSavedBands() ?: loadBands()

    // P3.4 (2026-10): getter теперь @Synchronized. attachOnce()/release()
    // тоже @Synchronized → UI никогда не получит ссылку на released-инстанс
    // в окне между current.release() и engine = null/new. UI-код должен
    // использовать safe-call `engine()?.setVirtualizerEnabled(on) ?: ...`
    // — даже после synchronized-чтения engine может быть null (ещё не
    // attach'ен / уже released), и это нормальная ситуация.
    @Synchronized
    fun engine(): AudioEffectsEngine? = engine

    // ─── Backward-compat: старый API Equalizer-объекта ───────────
    // AudioPlayerScreen/PlayerConnection могут звать эти методы.
    // Делегируем в engine, при null — no-op с логом.

    /** @deprecated используйте [engine] напрямую. */
    fun numberOfBands(): Short {
        val e = engine
        if (e == null) {
            AppLog.w(TAG, "numberOfBands: engine == null — return 0")
            return 0
        }
        return e.getNumberOfBands()
    }

    /** @deprecated используйте [engine] напрямую. */
    fun bandLevelRange(): ShortArray {
        val e = engine
        if (e == null) {
            AppLog.w(TAG, "bandLevelRange: engine == null — return default [-1500, 1500]")
            return shortArrayOf(-1500, 1500)
        }
        return e.getBandLevelRange()
    }
}
