// File: media/FilenameBuilder.kt
package re.pinok.media

import re.pinok.data.model.Track
import re.pinok.util.AppLog

/**
 * §42.12 P1 #5: умное имя файла + sanitization + номер трека.
 *
 * Раньше файлы сохранялись как `$trackId.m4a` (numeric ID). Это надёжно
 * (нет коллизий, нет проблем с кодировками), но неудобно пользователю:
 * в файловом менеджере видны `456249594.m4a` без артиста/названия.
 *
 * Теперь (если numTracksInPlaylist=true) файл называется:
 *   `NN. Artist - Title.m4a`
 * где NN — номер трека в плейлисте (если есть), Artist/Title — sanitized.
 *
 * Sanitization (вдохновлён VKNext `sanitizeFilename`):
 *   Windows-reserved: / \ : * ? " < > | → заменяем на '_' или ':'
 *   Control chars (0x00-0x1F) → удаляем
 *   Leading/trailing dots и spaces → удаляем (Windows не любит)
 *   Максимальная длина: 200 символов (NTFS limit ~255, FAT32 ~255 bytes)
 *
 * Если sanitize даёт пустую строку (трек без artist/title) — fallback на
 * numeric ID. Коллизии (два трека с одинаковым именем) не случаются, потому
 * что в плейлисте треки уникальны по (ownerId, id) — но имя может совпасть,
 * поэтому добавляем суффикс ` (2)`, ` (3)` при конфликте.
 *
 * Поскольку сменить схему именования для СУЩЕСТВУЮЩИХ кэшей нельзя (файлы
 * уже на диске как `456249594.m4a`), FilenameBuilder используется ТОЛЬКО
 * для новых скачиваний. Старые кэши остаются с numeric именами и работают
 * как раньше — getLocalFile() ищет по trackId в обоих форматах.
 */
object FilenameBuilder {

    private const val TAG = "FilenameBuilder"
    private const val MAX_LEN = 200
    private const val WINDOWS_RESERVED = "/\\:*?\"<>|"

    /**
     * Построить безопасное имя файла для трека.
     *
     * @param track      трек (берём artist, title).
     * @param ext        расширение без точки ("m4a", "ts", "mp3").
     * @param index      номер трека в плейлисте (1-based), null если вне плейлиста.
     * @param total      всего треков в плейлисте (для форматирования NN из N цифр).
     * @param useTrackNumber добавлять ли "NN. " префикс (настройка numTracksInPlaylist).
     * @return имя файла (например "01. Artist - Title.m4a") или null если sanitize пуст.
     */
    fun buildFilename(
        track: Track,
        ext: String,
        index: Int? = null,
        total: Int? = null,
        useTrackNumber: Boolean = true,
    ): String? {
        val artist = sanitize(track.artist)
        val title = sanitize(track.title)

        // Если оба пустые — numeric fallback.
        if (artist.isBlank() && title.isBlank()) {
            AppLog.d(TAG, "buildFilename: empty artist+title for #${track.id} — numeric fallback")
            return null
        }

        val sb = StringBuilder()
        // Префикс номера трека.
        if (useTrackNumber && index != null && index > 0) {
            val pad = if (total != null && total > 0) total.toString().length else 2
            sb.append(index.toString().padStart(pad, '0')).append(". ")
        }
        // Artist - Title (или просто Title если нет artist).
        if (artist.isNotBlank()) {
            sb.append(artist).append(" - ")
        }
        sb.append(title)

        // #VK-MUSIC-SAVER-PORT: subtitle (remix/feat подпись) в скобках — как VKnext
        // шаблон "{artist} - {title} {subtitle}". Пропускаем если пустой или
        // дублирует title.
        val subtitle = sanitize(track.subtitle ?: "")
        if (subtitle.isNotBlank() && !subtitle.equals(title, ignoreCase = true)) {
            sb.append(" (").append(subtitle).append(')')
        }

        // Обрезаем до MAX_LEN (оставляя место для расширения и суффикса).
        val baseLen = sb.length
        val maxBaseLen = MAX_LEN - ext.length - 5 // -1 для точки, -4 для " (99)"
        if (baseLen > maxBaseLen) {
            sb.setLength(maxBaseLen)
        }

        sb.append('.').append(ext)
        return sb.toString()
    }

    /**
     * Sanitize строки для использования в имени файла.
     *
     * Заменяет Windows-reserved символы на '_', убирает control chars,
     * обрезает leading/trailing dots и spaces.
     */
    fun sanitize(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch.code < 0x20 -> { /* control char — skip */ }
                ch in WINDOWS_RESERVED -> sb.append('_')
                else -> sb.append(ch)
            }
        }
        // Trim leading/trailing dots и spaces (Windows не любит).
        var result = sb.toString().trim().trim('.', ' ')
        // Сжимаем множественные пробелы.
        while (result.contains("  ")) {
            result = result.replace("  ", " ")
        }
        return result
    }

    /**
     * Разрешить коллизию имён: если файл существует, добавить " (2)", " (3)" и т.д.
     *
     * @param dir      директория файла.
     * @param filename исходное имя (например "Artist - Title.m4a").
     * @return уникальное имя (например "Artist - Title (2).m4a").
     */
    fun resolveCollision(dir: java.io.File, filename: String): String {
        if (!java.io.File(dir, filename).exists()) return filename
        val dotIdx = filename.lastIndexOf('.')
        if (dotIdx < 0) {
            // Нет расширения — добавляем суффикс в конец.
            var i = 2
            while (java.io.File(dir, "$filename ($i)").exists()) i++
            return "$filename ($i)"
        }
        val base = filename.substring(0, dotIdx)
        val ext = filename.substring(dotIdx + 1)
        var i = 2
        while (java.io.File(dir, "$base ($i).$ext").exists()) i++
        return "$base ($i).$ext"
    }
}
