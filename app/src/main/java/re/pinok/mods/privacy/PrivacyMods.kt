package re.pinok.mods.privacy

import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

/**
 * Privacy mods (formerly implemented as VK API request modifiers in SOVA V RE).
 *
 * SOVA_2.0 ships these as flags the API client reads before making calls.
 *
 * Original SOVA V RE mods covered:
 *  - offline mode (force last_seen=offline)
 *  - device masking (replace device model, OS version, build fields)
 *  - anti-telemetry (drop stat requests)
 *  - hide last_seen (don't send online status pings)
 *
 * Методы логируют каждое применение для трейсинга приватности.
 */
class PrivacyMods {

    private val tag = "PrivacyMods"

    /**
     * Returns true if online status pings should be suppressed.
     * Read from current prefs snapshot.
     *
     * When true:
     *  - [re.pinok.api.VKApiClient.accountSetOnline] becomes no-op (no `account.setOnline` calls)
     *  - `online,last_seen,online_info` are stripped from `users.get` fields
     *  - VK cannot update our own last_seen because we never ping
     */
    fun shouldHideLastSeen(snapshot: SovaPrefs.Snapshot): Boolean {
        val hide = snapshot.privacyHideLastSeen
        if (hide) AppLog.d(tag, "last_seen hidden — online ping suppressed")
        return hide
    }

    /**
     * Strips privacy-sensitive fields from a `users.get` fields list when
     * `privacyHideLastSeen` is enabled. Returns the filtered comma-separated
     * string. When the flag is off, returns [fields] unchanged.
     *
     * Removed tokens: `online`, `online_info`, `last_seen` — these are the
     * fields VK uses to record that WE queried someone's online status,
     * and also reveal our own online state via reverse-ping.
     */
    fun filterUsersFields(snapshot: SovaPrefs.Snapshot, fields: String): String {
        if (!snapshot.privacyHideLastSeen) return fields
        val removed = fields.split(",")
            .filter { token ->
                val t = token.trim()
                t != "online" && t != "online_info" && t != "last_seen"
            }
            .joinToString(",")
        AppLog.d(tag, "users.get fields filtered: ${fields.length}→${removed.length} chars")
        return removed
    }

    /** True if telemetry / stats endpoints should be dropped. */
    fun shouldDropTelemetry(snapshot: SovaPrefs.Snapshot): Boolean {
        val drop = snapshot.privacyAntiTelemetry
        if (drop) AppLog.d(tag, "telemetry dropped")
        return drop
    }

    /** True if the device should be masqueraded (model, OS, etc.). */
    fun shouldMaskDevice(snapshot: SovaPrefs.Snapshot): Boolean {
        val mask = snapshot.privacyDeviceMask
        if (mask) AppLog.d(tag, "device masked: ${maskedDeviceFields()["device_model"]}")
        return mask
    }

    /** Masked device fields sent in API "device" parameter. */
    fun maskedDeviceFields(): Map<String, String> = mapOf(
        "device_model"  to "Pixel 9 Pro",
        "platform"      to "android",
        "os_version"    to "14",
        "build"         to "AP3A.241105.007",
        "manufacturer"  to "Google",
    )

    /** True if the offline mode should be forced (no network at all). */
    fun shouldForceOffline(snapshot: SovaPrefs.Snapshot): Boolean {
        val force = snapshot.privacyOfflineMode
        if (force) AppLog.d(tag, "offline mode forced — all network suppressed")
        return force
    }
}
