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

    /** Текущий пресет (читается из engine). null = пользовательский. */
    val currentPresetName: String?
        get() = engine?.currentPresetName

    // ─── Engine lifecycle (вызывает PlayerService) ───────────────

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
        engine?.reattachLightweight()
    }

    /** Полный release+recreate (если lightweight не помог). */
    fun reattachFull() {
        engine?.reattachFull()
    }

    /** Освобождает shared engine. Безопасно вызывать несколько раз. */
    fun release() {
        engine?.release()
        // НЕ зануляем engine — он ещё может пригодиться для чтения saved state.
        // PlayerService пересоздаст при следующем attachOnce.
    }

    // ─── Equalizer (legacy) API — delegate to engine ─────────────

    fun saveEnabled(enabled: Boolean) = engine?.setEqEnabled(enabled) ?: Unit
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
        engine?.applyPreset(preset)
    }

    fun setBand(bandIndex: Int, gainMilliBels: Short) {
        engine?.setBand(bandIndex, gainMilliBels)
    }

    fun getBands(): List<Short> = engine?.getBands() ?: emptyList()

    fun setEnabled(enabled: Boolean) = engine?.setEqEnabled(enabled) ?: Unit

    fun isEnabled(): Boolean = engine?.isEqEnabled() ?: false

    fun isSavedEnabled(): Boolean = engine?.isEqSavedEnabled() ?: loadEnabled()

    fun getSavedPresetName(): String? = engine?.getSavedPresetName() ?: loadPreset()

    fun getSavedBands(): List<Short> = engine?.getSavedBands() ?: loadBands()

    /** Доступ к shared engine для UI (новые эффекты: bass/virt/loud/reverb). */
    fun engine(): AudioEffectsEngine? = engine

    // ─── Backward-compat: старый API Equalizer-объекта ───────────
    // AudioPlayerScreen/PlayerConnection могут звать эти методы.
    // Делегируем в engine, при null — no-op с логом.

    /** @deprecated используйте [engine] напрямую. */
    fun numberOfBands(): Short = engine?.getNumberOfBands() ?: 0

    /** @deprecated используйте [engine] напрямую. */
    fun bandLevelRange(): ShortArray = engine?.getBandLevelRange() ?: shortArrayOf(-1500, 1500)
}
