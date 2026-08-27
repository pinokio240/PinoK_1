// File: media/ImageSaver.kt
package re.pinok.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.util.AppLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сохранение картинок/фото из любого места VK в галерею.
 *
 * Единый helper для кнопки «Сохранить» в [re.pinok.ui.components.PhotoViewer]
 * (значок в правом верхнем углу полноэкранного просмотра). Качает байты через
 * OkHttp и кладёт в галерею:
 *  - API 29+: `MediaStore.Images` + `RELATIVE_PATH=Pictures/PinoK` (без разрешений);
 *  - API 24–28: публичный `Pictures/PinoK`, при отсутствии `WRITE_EXTERNAL_STORAGE` —
 *    fallback на внутреннее хранилище приложения (файл не теряется).
 */
object ImageSaver {

    private const val TAG = "ImageSaver"
    private const val ALBUM = "PinoK"

    sealed class Result {
        data class Ok(val displayPath: String) : Result()
        data class Err(val message: String) : Result()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Скачать и сохранить изображение в галерею. Вызывать из корутины (IO).
     *
     * @param url URL изображения (желательно максимальный размер — см. [PhotoSizes]).
     * @return [Result.Ok] с отображаемым путём или [Result.Err].
     */
    suspend fun save(context: Context, url: String): Result = withContext(Dispatchers.IO) {
        try {
            val bytes = download(url)
            val name = buildFileName(url)
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, name)
            } else {
                saveLegacy(context, bytes, name)
            }
            AppLog.i(TAG, "saved '$url' → '$savedPath' (${bytes.size} B)")
            Result.Ok(savedPath)
        } catch (e: Exception) {
            AppLog.w(TAG, "save failed for '$url': ${e.message}")
            Result.Err(e.message ?: "Ошибка сохранения")
        }
    }

    /** Страховка «оригинал»: апгрейд легаси-суффикса VK (_s/_m/_x/_y/_z → _w). */
    fun toMaxSize(url: String): String {
        val q = url.indexOf('?')
        val path = if (q >= 0) url.substring(0, q) else url
        val query = if (q >= 0) url.substring(q) else ""
        val upgraded = path.replace(VK_SIZE_SUFFIX) { "_w.${it.groupValues[2]}" }
        return upgraded + query
    }

    private val VK_SIZE_SUFFIX = Regex("_(s|m|x|y|z)\\.([A-Za-z0-9]+)$")

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            return response.body?.bytes() ?: throw RuntimeException("Пустой ответ")
        }
    }

    private fun buildFileName(url: String): String {
        val raw = url.substringBefore('?').substringAfterLast('/').ifBlank { null }
        val clean = raw?.take(80)?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (clean.isNullOrBlank()) {
            "PinoK_${System.currentTimeMillis()}.jpg"
        } else if (clean.endsWith(".jpg", true) || clean.endsWith(".png", true) ||
            clean.endsWith(".webp", true) || clean.endsWith(".gif", true)
        ) {
            "PinoK_${System.currentTimeMillis()}_$clean"
        } else {
            "PinoK_${System.currentTimeMillis()}_$clean.jpg"
        }
    }

    private fun saveViaMediaStore(context: Context, bytes: ByteArray, name: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw RuntimeException("openOutputStream failed")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "${Environment.DIRECTORY_PICTURES}/$ALBUM/$name"
    }

    private fun saveLegacy(context: Context, bytes: ByteArray, name: String): String {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        val writable = try {
            publicDir.mkdirs()
            publicDir.canWrite()
        } catch (_: Exception) {
            false
        }
        val dir = if (writable) publicDir else File(context.filesDir, "Pictures/$ALBUM").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return file.absolutePath
    }
}
