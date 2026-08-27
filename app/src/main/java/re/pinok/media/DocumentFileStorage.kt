// File: media/DocumentFileStorage.kt
package re.pinok.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import re.pinok.util.AppLog
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * #DOCFILE-SD (P2): DocumentFile API для SD-карты и других non-primary volumes.
 *
 * Проблема: [TrackDownloadManager.resolveDownloadDir] конвертирует SAF tree URI
 * с `primary:` prefix в File path, но content:// URI БЕЗ `primary:` (SD-карта,
 * USB OTG, другие volume'ы) НЕ конвертируется в File — fallback на internal.
 * Пользователь выбирает SD-карту через OpenDocumentTree, но выбор игнорируется.
 *
 * Решение (write-through hybrid):
 *  - Все промежуточные файлы (.tmp, segments, probe) — на internal storage (File API).
 *  - Финальный файл (после завершения загрузки) — копируется на SD-карту через
 *    DocumentFile API. Internal копия остаётся для быстрого воспроизведения
 *    (ExoPlayer → Uri.fromFile(internalFile), без latency на copy-from-SD).
 *  - [getLocalFile] возвращает internal File — без изменений в playback pipeline.
 *  - Удаление: чистим и internal File, и DocumentFile на SD-карте.
 *
 * Storage cost: удвоение (internal + SD-карта). Acceptable для music files (3-10MB).
 * Пользователь может очистить internal cache через «Очистить кэш» без потери SD-копии.
 *
 * Persisted permissions: SAF tree URI должен быть сохранён через
 * `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION
 * | FLAG_GRANT_WRITE_URI_PERMISSION)` в ActivityResult callback. Без этого URI
 * недоступен после перезапуска приложения.
 */
object DocumentFileStorage {

    private const val TAG = "DocumentFileStorage"

    /**
     * Проверяет, является ли path SAF tree URI для non-primary volume (SD-карта).
     * Такие URI нельзя конвертировать в File path — нужен DocumentFile API.
     *
     * Примеры:
     *  - `content://com.android.externalstorage.documents/tree/primary%3AMusic` → false (primary, File API OK)
     *  - `content://com.android.externalstorage.documents/tree/1234-5678%3AMusic` → true (SD card, DocumentFile needed)
     */
    fun isNonPrimarySafUri(path: String): Boolean {
        if (!path.startsWith("content://")) return false
        val treeIdx = path.indexOf("/tree/")
        if (treeIdx < 0) return false
        val raw = path.substring(treeIdx + 6)
        val decoded = runCatching { Uri.decode(raw) }.getOrDefault(raw)
        // primary: → primary external storage (File API works).
        // Всё остальное (XXXX-XXXX:, home:, etc.) → non-primary, needs DocumentFile.
        return !decoded.startsWith("primary:")
    }

    /**
     * Парсит SAF tree URI из path. Возвращает null если path не SAF URI.
     */
    fun parseTreeUri(path: String): Uri? {
        if (!path.startsWith("content://")) return null
        return Uri.parse(path)
    }

    /**
     * Проверяет, доступен ли DocumentFile tree (существует + writable).
     * Используется в [TrackDownloadManager.probeWritable] и при init.
     */
    fun isTreeAccessible(context: Context, treeUri: Uri): Boolean {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            docFile.exists() && docFile.canWrite()
        } catch (e: Exception) {
            AppLog.w(TAG, "isTreeAccessible: failed for $treeUri — ${e.message}")
            false
        }
    }

    /**
     * Копирует файл (internal File) в DocumentFile tree на SD-карте.
     *
     * @param context app context (for contentResolver)
     * @param treeUri SAF tree URI (persisted permission required)
     * @param sourceFile internal File to copy from
     * @param targetName имя файла в DocumentFile tree (например "456249594.mp3")
     * @return true если успешно, false при ошибке
     */
    fun copyFileToTree(
        context: Context,
        treeUri: Uri,
        sourceFile: File,
        targetName: String,
    ): Boolean {
        if (!sourceFile.exists()) {
            AppLog.w(TAG, "copyFileToTree: source doesn't exist: ${sourceFile.absolutePath}")
            return false
        }
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            AppLog.w(TAG, "copyFileToTree: cannot resolve tree URI $treeUri")
            return false
        }
        if (!tree.exists() || !tree.canWrite()) {
            AppLog.w(TAG, "copyFileToTree: tree not writable: $treeUri")
            return false
        }
        // Удаляем существующий файл с тем же именем (overwrite).
        tree.findFile(targetName)?.delete()
        val target = tree.createFile("application/octet-stream", targetName) ?: run {
            AppLog.w(TAG, "copyFileToTree: createFile failed for '$targetName'")
            return false
        }
        return try {
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                sourceFile.inputStream().use { inp ->
                    copyStream(inp, out)
                }
            } ?: run {
                AppLog.w(TAG, "copyFileToTree: openOutputStream returned null")
                false
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "copyFileToTree: copy failed for '$targetName' — ${e.message}", e)
            // Частично записанный файл удаляем.
            runCatching { target.delete() }
            false
        }
    }

    /**
     * Удаляет файл из DocumentFile tree по имени.
     */
    fun deleteFileFromTree(context: Context, treeUri: Uri, fileName: String): Boolean {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val file = tree.findFile(fileName) ?: return true  // уже нет — OK
            file.delete()
        } catch (e: Exception) {
            AppLog.w(TAG, "deleteFileFromTree: failed for '$fileName' — ${e.message}")
            false
        }
    }

    /**
     * Проверяет существование файла в DocumentFile tree.
     */
    fun fileExistsInTree(context: Context, treeUri: Uri, fileName: String): Boolean {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            tree.findFile(fileName)?.exists() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Размер файла в DocumentFile tree (в байтах). -1L если не найден.
     */
    fun fileSizeInTree(context: Context, treeUri: Uri, fileName: String): Long {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return -1L
            val file = tree.findFile(fileName) ?: return -1L
            if (file.exists()) file.length() else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Удаляет ВСЕ файлы в DocumentFile tree (для clearAllDownloads).
     * Возвращает количество удалённых файлов.
     */
    fun clearTree(context: Context, treeUri: Uri): Int {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
            var count = 0
            for (file in tree.listFiles()) {
                if (file.isFile) {
                    if (file.delete()) count++
                }
            }
            count
        } catch (e: Exception) {
            AppLog.w(TAG, "clearTree: failed — ${e.message}")
            0
        }
    }

    /**
     * Список всех имён файлов в DocumentFile tree (для refreshFromDisk).
     */
    fun listFileNames(context: Context, treeUri: Uri): List<String> {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            tree.listFiles().filter { it.isFile }.mapNotNull { it.name }
        } catch (e: Exception) {
            AppLog.w(TAG, "listFileNames: failed — ${e.message}")
            emptyList()
        }
    }

    /**
     * Копирует поток (InputStream → OutputStream) с buffer 8KB.
     * Вынесено для тестирования и переиспользования.
     */
    private fun copyStream(inp: InputStream, out: OutputStream): Boolean {
        return try {
            val buffer = ByteArray(8192)
            while (true) {
                val read = inp.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.flush()
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "copyStream: failed — ${e.message}", e)
            false
        }
    }
}
