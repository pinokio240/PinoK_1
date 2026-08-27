package re.pinok.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

/**
 * Fix #297: [RequestBody] с прогресс-колбэком для отслеживания загрузки
 * больших файлов (видео) на сервер VK.
 *
 * Оборачивает любой RequestBody, считает байты записанные в sink, и
 * периодически (каждые ~100ms) вызывает [onProgress] с долей 0..1.
 *
 * Используется в [VKApiClient.uploadVideoFile] для实时ного прогресс-бара
 * при выгрузке видео с телефона.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long, fraction: Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = try { delegate.contentLength() } catch (_: Exception) { -1L }

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink).buffer()
        delegate.writeTo(countingSink)
        countingSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten = 0L
        private var totalBytes = contentLength()
        private var lastReportAt = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            if (totalBytes < 0) totalBytes = contentLength()
            // throttle reports to ~every 80ms to avoid UI thrash
            val now = System.currentTimeMillis()
            if (now - lastReportAt >= 80 || bytesWritten == totalBytes) {
                lastReportAt = now
                val frac = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                onProgress(bytesWritten, totalBytes, frac)
            }
        }
    }
}
