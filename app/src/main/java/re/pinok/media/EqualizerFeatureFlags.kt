// File: media/EqualizerFeatureFlags.kt
package re.pinok.media

import android.content.SharedPreferences
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * Видимость отдельных эффектов эквалайзера в UI.
 *
 * **Назначение.** Пользователь может отключить эффекты, которые ему не
 * нужны (или которые плохо работают на его устройстве) — тогда
 * соответствующая вкладка в [re.pinok.ui.screens.music.EqualizerScreen]
 * скрывается, а toggle в аудиоплеере не показывается.
 *
 * Это **не** отключает сам AudioEffect-объект в [AudioEffectsEngine] —
 * эффект остаётся созданным (на случай если пользователь включит его
 * снова), но его `enabled = false` и UI не даёт к нему доступа.
 *
 * **Хранение.** Тот же файл `SharedPreferences("equalizer")` что и
 * [AudioEffectsEngine] — ключи с префиксом `feat_`. Значения по умолчанию:
 * все эффекты включены (кроме PresetReverb — он часто ломает звук на
 * custom ROM, см. риски в `EQUALIZER_INTEGRATION_PLAN.md`).
 *
 * **Thread-safety.** Чтение/запись через SharedPreferences.apply() —
 * потокобезопасно. [snapshot] читает атомарно все 5 флагов.
 */
object EqualizerFeatureFlags {

    private const val TAG = "EqualizerFeatureFlags"
    private const val PREFS_NAME = "equalizer"

    /** Ключи в SharedPreferences (префикс `feat_`). */
    private const val KEY_EQ = "feat_eq"
    private const val KEY_BASS = "feat_bass"
    private const val KEY_VIRT = "feat_virt"
    private const val KEY_REVERB = "feat_reverb"
    private const val KEY_LOUD = "feat_loud"

    /**
     * Immutable-снапшот всех 5 флагов. Читается из prefs атомарно.
     * Используется UI для решения какие вкладки/toggles показывать.
     */
    data class Snapshot(
        val eqEnabled: Boolean,
        val bassEnabled: Boolean,
        val virtualizerEnabled: Boolean,
        val reverbEnabled: Boolean,
        val loudnessEnabled: Boolean,
    )

    /**
     * Значения по умолчанию. PresetReverb = false — см. риски в плане:
     * на некоторых ROM (Xiaomi/Huawei) ломает звук. Остальные = true.
     */
    private val DEFAULTS = Snapshot(
        eqEnabled = true,
        bassEnabled = true,
        virtualizerEnabled = true,
        reverbEnabled = false,
        loudnessEnabled = true,
    )

    private fun prefs(): SharedPreferences? = try {
        SovaApp.get().getSharedPreferences(PREFS_NAME, 0)
    } catch (e: Exception) {
        AppLog.w(TAG, "prefs: SovaApp not ready — ${e.message}")
        null
    }

    /** Атомарно читает все 5 флагов. Если prefs недоступен — дефолты. */
    fun snapshot(): Snapshot {
        val p = prefs() ?: return DEFAULTS
        return Snapshot(
            eqEnabled = p.getBoolean(KEY_EQ, DEFAULTS.eqEnabled),
            bassEnabled = p.getBoolean(KEY_BASS, DEFAULTS.bassEnabled),
            virtualizerEnabled = p.getBoolean(KEY_VIRT, DEFAULTS.virtualizerEnabled),
            reverbEnabled = p.getBoolean(KEY_REVERB, DEFAULTS.reverbEnabled),
            loudnessEnabled = p.getBoolean(KEY_LOUD, DEFAULTS.loudnessEnabled),
        )
    }

    // ─── Individual setters (вызываются из SettingsScreen) ───────────

    fun setEqEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_EQ, on)?.apply()
        // Если эффект отключается в UI — выключаем и сам AudioEffect,
        // чтобы он не потреблял CPU (даже если оставался созданным).
        if (!on) EqualizerHelper.engine()?.setEqEnabled(false)
    }

    fun setBassEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_BASS, on)?.apply()
        if (!on) EqualizerHelper.engine()?.setBassBoostEnabled(false)
    }

    fun setVirtualizerEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_VIRT, on)?.apply()
        if (!on) EqualizerHelper.engine()?.setVirtualizerEnabled(false)
    }

    fun setReverbEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_REVERB, on)?.apply()
        if (!on) EqualizerHelper.engine()?.setReverbEnabled(false)
    }

    fun setLoudnessEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_LOUD, on)?.apply()
        if (!on) EqualizerHelper.engine()?.setLoudnessEnabled(false)
    }
}
