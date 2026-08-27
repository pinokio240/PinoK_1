// File: media/ZipExporter.kt
package re.pinok.media

import re.pinok.data.model.Track
import re.pinok.util.AppLog
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * §42.12 P3 #10: экспорт скачанных треков в ZIP-архив.
 *
 * VKNext использует JSZip в браузере для упаковки плейлистов. На Android
 * эквивалент — `java.util.zip.ZipOutputStream` (стандартная JVM-библиотека,
 * не требует зависимостей).
 *
 * Поток:
 *  1. [exportToZip] — точка входа. Принимает список Track и output .zip файл.
 *  2. Для каждого трека: getLocalFile(trackId) → если есть, добавляем в zip.
 *  3. Имя в архиве: FilenameBuilder.buildFilename или fallback "$trackId.m4a".
 *  4. OnProgress callback: (current, total) для UI-прогресса.
 *
 * Стримингово: пишем треки по одному в ZipOutputStream, не загружая все
 * в память. Каждый трек читаем через streaming InputStream.copyTo.
 *
 * Безопасность:
 *  — Если trackId не скачан (getLocalFile вернул null) — skip, логируем.
 *  — Если output .zip существует — перезаписываем.
 *  — В случае ошибки — удаляем partial .zip (atomic-ish).
 *  — ZipEntry имена: UTF-8, sanitization через FilenameBuilder.
 */
object ZipExporter {

    private const val TAG = "ZipExporter"

    /**
     * Экспортировать список треков в ZIP-архив.
     *
     * @param tracks    список треков (берём id для getLocalFile).
     * @param outputZip целевой .zip файл (будет перезаписан).
     * @param onProgress callback (current, total) — вызывается после каждого трека.
     * @return Pair(exportedCount, skippedCount) или null при критической ошибке.
     */
    suspend fun exportToZip(
        tracks: List<Track>,
        outputZip: File,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Pair<Int, Int>? {
        if (tracks.isEmpty()) {
            AppLog.w(TAG, "exportToZip: empty track list")
            return 0 to 0
        }

        // Удаляем старый архив если был.
        outputZip.delete()
        val parent = outputZip.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        var exported = 0
        var skipped = 0
        val total = tracks.size

        return try {
            ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
                zos.setLevel(java.util.zip.Deflater.BEST_SPEED) // быстрое сжатие (треки уже сжаты)
                val usedNames = HashSet<String>()

                for ((index, track) in tracks.withIndex()) {
                    val localFile = re.pinok.media.TrackDownloadManager.getLocalFile(track.id)
                    if (localFile == null || !localFile.exists()) {
                        AppLog.w(TAG, "exportToZip: #${track.id} not downloaded — skip")
                        skipped++
                        onProgress?.invoke(index + 1, total)
                        continue
                    }

                    // Имя в архиве: красивое если можно построить, иначе numeric.
                    val entryName = buildEntryName(track, localFile, index + 1, total, usedNames)
                    usedNames.add(entryName)

                    try {
                        val entry = ZipEntry(entryName)
                        entry.size = localFile.length()
                        entry.time = localFile.lastModified()
                        zos.putNextEntry(entry)
                        localFile.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                        exported++
                    } catch (e: Exception) {
                        AppLog.w(TAG, "exportToZip: #${track.id} write failed: ${e.message} — skip")
                        skipped++
                    }
                    onProgress?.invoke(index + 1, total)
                }
            }
            AppLog.i(TAG, "exportToZip: DONE — $exported exported, $skipped skipped, " +
                "${outputZip.length() / 1024} KB → ${outputZip.name}")
            exported to skipped
        } catch (e: Exception) {
            AppLog.e(TAG, "exportToZip: critical failure: ${e.message}")
            outputZip.delete() // partial zip — удаляем
            null
        }
    }

    /**
     * Построить уникальное имя для ZipEntry.
     * Использует FilenameBuilder если возможно, иначе numeric fallback.
     * Коллизии внутри архива решаем суффиксом " (2)".
     */
    private fun buildEntryName(
        track: Track,
        localFile: File,
        index: Int,
        total: Int,
        usedNames: Set<String>,
    ): String {
        val ext = localFile.extension.ifBlank { "m4a" }
        val built = FilenameBuilder.buildFilename(
            track = track,
            ext = ext,
            index = index,
            total = total,
            useTrackNumber = true,
        )
        var name = built ?: "${track.id}.$ext"
        // Разрешаем коллизии (два трека с одинаковым artist+title).
        if (usedNames.contains(name)) {
            val dotIdx = name.lastIndexOf('.')
            val base = if (dotIdx > 0) name.substring(0, dotIdx) else name
            val extPart = if (dotIdx > 0) name.substring(dotIdx + 1) else ""
            var i = 2
            var candidate = if (extPart.isEmpty()) "$base ($i)" else "$base ($i).$extPart"
            while (usedNames.contains(candidate)) {
                i++
                candidate = if (extPart.isEmpty()) "$base ($i)" else "$base ($i).$extPart"
            }
            name = candidate
        }
        return name
    }
}
