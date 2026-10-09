package re.pinok.media

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import re.pinok.util.AppLog

/**
 * #CLIP-BYTES-DIAGNOSTIC (2026-10-09): диагностика okcdn `&bytes=` для DASH-клипов.
 *
 * Web-плеер VK для on-demand DASH-сегментов okcdn добавляет query-параметр
 * `&bytes=start-end` в URL (сервер отвечает 200, не 206). Стандартный ExoPlayer
 * шлёт HTTP `Range: bytes=` в заголовке. Эта обёртка преобразует ЗАКРЫТЫЙ
 * диапазон (DataSpec.position + length) в `&bytes=start-end` ТОЛЬКО для
 * сегментов okcdn, и логирует каждый такой запрос для диагностики.
 */
class OkCdnQueryRangeDataSource(
    private val base: DataSource,
    private val logTag: String = "ClipBytes",
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        base.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uriStr = dataSpec.uri.toString()
        val host = dataSpec.uri.host ?: ""
        val q = dataSpecQuery(uriStr)
        val ct = q?.let { Regex("(?:^|&)ct=(\\d+)").find(it)?.groupValues?.get(1) }
        val hasScl = q?.contains("scl=") ?: false
        val path = dataSpec.uri.path ?: ""
        val segment = isOkCdnSegment(host, uriStr)
        val closedRange = dataSpec.length > 0

        AppLog.d(logTag,
            "open: host=$host seg=$segment ct=$ct scl=$hasScl path=$path closed=$closedRange " +
                "pos=${dataSpec.position} len=${dataSpec.length} u=${shortUri(uriStr)}")

        if (segment && closedRange) {
            val start = dataSpec.position
            val end = dataSpec.position + dataSpec.length - 1
            val newUri = appendBytesParam(uriStr, start, end)
            val spec = dataSpec.buildUpon()
                .setUri(Uri.parse(newUri))
                .setPosition(0)
                .build()
            AppLog.d(logTag, "  → bytes=$start-$end (closed) for u=${shortUri(newUri)}")
            return base.open(spec)
        }

        AppLog.d(logTag, "  → passthrough (seg=$segment closed=$closedRange)")
        return base.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        base.read(buffer, offset, length)

    override fun getUri(): Uri = base.uri ?: Uri.EMPTY

    override fun close() = base.close()

    /** true если это on-demand DASH-сегмент okcdn (не превью/не манифест). */
    private fun isOkCdnSegment(host: String, uriStr: String): Boolean {
        val okCdnHost = host.endsWith("okcdn.ru") || host.endsWith("vkuser.net")
        if (!okCdnHost) return false
        val q = dataSpecQuery(uriStr) ?: return false
        if (q.contains("scl=")) return false
        val ct = Regex("(?:^|&)ct=(\\d+)").find(q)?.groupValues?.get(1) ?: return false
        return ct in setOf("11", "12", "22", "32")
    }

    private fun dataSpecQuery(uriStr: String): String? = Uri.parse(uriStr).query

    /** Дописывает &bytes=start-end в конец query (после sig; sig не покрывает bytes). */
    private fun appendBytesParam(uriStr: String, start: Long, end: Long): String {
        val sep = if (uriStr.contains('?')) "&" else "?"
        return "$uriStr${sep}bytes=$start-$end"
    }

    /** Укорачивает URL для лога (host + первые 80 символов query). */
    private fun shortUri(uriStr: String): String {
        val u = Uri.parse(uriStr)
        val q = u.query ?: ""
        return "${u.host}/?${q.take(80)}…"
    }
}