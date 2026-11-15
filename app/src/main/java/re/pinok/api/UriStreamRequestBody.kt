package re.pinok.api

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

/**
 * #UPLOAD-STREAM (по мотивам Fenrir dev.ragnarok.fenrir.api.util.ProgressRequestBody):
 * стриминговый [RequestBody] прямо из ContentResolver Uri.
 *
 * Файл НИКОГДА не материализуется в кэш и не читается в память целиком:
 * поток открывается лениво в [writeTo] (на каждую попытку OkHttp — ретраи
 * корректны), данные идут в сеть чанками. В память проекта это пришло
 * вместо паттерна «copy content:// → temp File → asRequestBody».
 *
 * @param sizeBytes размер файла (OpenableColumns.SIZE из пикера); -1 →
 *        chunked transfer (OkHttp не шлёт Content-Length).
 */
class UriStreamRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mime: MediaType?,
    private val sizeBytes: Long = -1L,
    private val onProgress: ((written: Long, total: Long) -> Unit)? = null,
) : RequestBody() {

    override fun contentType(): MediaType? = mime

    override fun contentLength(): Long = sizeBytes

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri)
            ?: throw IOException("cannot open input stream for $uri")
        input.use { ins ->
            val out = sink.outputStream()
            val buf = ByteArray(BUFFER_SIZE)
            var written = 0L
            while (true) {
                val r = ins.read(buf)
                if (r == -1) break
                out.write(buf, 0, r)
                written += r
                if (sizeBytes > 0) onProgress?.invoke(written, sizeBytes)
            }
            out.flush()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
