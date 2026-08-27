// File: data/model/VideoQuality.kt
package re.pinok.data.model

/**
 * Единый шаблон порядка и меток качества видео (§52.2.4 + «общие паттерны»).
 *
 * Раньше порядок качеств дублировался в двух местах:
 *  - `VideoPlayerScreen.QUALITY_ORDER` (map mp4_* → label),
 *  - inline-список `order` в `Video.playUrlForQuality`.
 * Теперь источник правды — один: [ORDER]/[KEYS].
 */
object VideoQuality {

    /** Порядок качеств high→low: key → человекочитаемая метка. */
    val ORDER: List<Pair<String, String>> = listOf(
        "mp4_2160" to "4K",
        "mp4_1440" to "1440p",
        "mp4_1080" to "1080p",
        "mp4_720"  to "720p",
        "mp4_480"  to "480p",
        "mp4_360"  to "360p",
        "mp4_240"  to "240p",
        "mp4_144"  to "144p",
    )

    /** Ключи качеств high→low ("mp4_2160" ... "mp4_144"). */
    val KEYS: List<String> = ORDER.map { it.first }

    /** Метка качества ("mp4_1080" → "1080p"). Неизвестный ключ → сам ключ. */
    fun label(key: String): String = ORDER.firstOrNull { it.first == key }?.second ?: key

    /**
     * Fix #334: начальный индекс качества из предпочтения пользователя.
     *
     * [availableKeys] отсортированы high→low (index 0 = максимум):
     *  - "auto"/пусто → 0 (максимальное доступное);
     *  - точное совпадение (pref="1080" → key="mp4_1080") → его индекс;
     *  - иначе первое качество ≤ preferred; если все доступные выше — lastIndex
     *    (минимальное из высоких, чтобы не гонять 4K при pref=144p).
     */
    fun selectIndex(availableKeys: List<String>, preferredQuality: String): Int {
        if (availableKeys.isEmpty()) return 0
        if (preferredQuality == "auto" || preferredQuality.isBlank()) return 0
        val exact = availableKeys.indexOfFirst { it.endsWith("_$preferredQuality") }
        if (exact >= 0) return exact
        val prefInt = preferredQuality.toIntOrNull() ?: return 0
        val leIndex = availableKeys.indexOfFirst { key ->
            key.substringAfter("_").toIntOrNull()?.let { it <= prefInt } ?: false
        }
        return if (leIndex >= 0) leIndex else availableKeys.lastIndex
    }
}
