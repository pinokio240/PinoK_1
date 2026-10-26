package re.pinok.mods.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import okio.InflaterSource
import okio.buffer
import okio.use
import re.pinok.util.AppLog
import java.util.zip.Inflater

/**
 * #WEB-ACCEPT-ENCODING-FIX (2026-10-26, ROOT CAUSE err=3 audio-WRITE):
 *
 * ФАКТЫ (HAR #53 — SPA audio.createPlaylist 200 OK, vs device-лог err=3):
 *   • SPA шлёт Accept-Encoding: "gzip, deflate, br, zstd"
 *   • PinoK через okhttp3.brotli.BrotliInterceptor шлёт "br,gzip" (strings-дамп .class)
 *   • VK отвечает Content-Encoding: zstd для 200 OK (HAR #53 response headers)
 *   • Различие "br,gzip" vs "gzip, deflate, br, zstd" = BOT FINGERPRINT:
 *     - нет deflate (все браузеры шлют)
 *     - нет zstd (Chrome 124+ шлёт)
 *     - нет пробелов (браузер шлёт с пробелами)
 *     - другой порядок
 *   • WAF режет audio-WRITE (err=3 "Unknown method" decoy), READ пропускает
 *
 * ФИКС: перехватчик ВСЕГДА ставит Accept-Encoding = "gzip, deflate, br"
 * (zstd НЕ включаем — нет декодера в OkHttp; VK ответит gzip/br вместо zstd
 * благодаря Vary: Accept-Encoding). ВСЕГДА декодирует gzip/deflate/br в ответе
 * (независимо от того, кто поставил Accept-Encoding — в отличие от стандартного
 * BrotliInterceptor, который скипает если header уже есть).
 *
 * Заменяет okhttp3.brotli.BrotliInterceptor в SovaApp.kt.
 */
object WebAcceptEncodingInterceptor : Interceptor {

    // Эталон = HAR #53 (SPA): "gzip, deflate, br, zstd".
    // zstd убран — OkHttp не умеет декодировать. VK ответит gzip или br
    // (Vary: Accept-Encoding в response headers).
    private const val ACCEPT_ENCODING = "gzip, deflate, br"

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()

        // ВСЕГДА overwrite Accept-Encoding — даже если VKApiClient уже поставил
        // свой (в web-gateway header builder мы НЕ ставим, но BridgeInterceptor
        // мог). Это гарантирует точное совпадение с SPA.
        val newReq = req.newBuilder()
            .header("Accept-Encoding", ACCEPT_ENCODING)
            .build()

        val resp = chain.proceed(newReq)

        val body = resp.body ?: return resp
        val encoding = resp.header("Content-Encoding")?.lowercase()?.trim()
            ?: return resp

        // Диагностика: для web.api.vk.ru логируем что шлём и что получаем
        if (req.url.host == "web.api.vk.ru") {
            AppLog.d("WebAcceptEncoding",
                "${req.url.encodedPath.substringAfterLast('/')}: " +
                    "sent AE=\"$ACCEPT_ENCODING\", recv CE=\"$encoding\", code=${resp.code}")
        }

        val decodedBytes: ByteArray = try {
            when (encoding) {
                "gzip" -> {
                    GzipSource(body.source()).use { src ->
                        Buffer().apply { writeAll(src) }.readByteArray()
                    }
                }
                "br" -> {
                    // org.brotli.dec.BrotliInputStream (from okhttp-brotli dependency)
                    val bis = org.brotli.dec.BrotliInputStream(body.byteStream())
                    bis.use { it.readBytes() }
                }
                "deflate" -> {
                    // okio InflaterSource (raw deflate, zlib-wrapped)
                    InflaterSource(body.source(), Inflater(true)).use { src ->
                        Buffer().apply { writeAll(src) }.readByteArray()
                    }
                }
                "identity" -> {
                    return resp // no encoding
                }
                else -> {
                    // zstd или неизвестное — не можем декодировать, возвращаем как есть
                    // (вызовущий получит бинарный мусор → JsonSyntaxException, но это
                    // лучше чем silent drop). Логируем для диагностики.
                    AppLog.w(
                        "WebAcceptEncoding",
                        "Unknown Content-Encoding '$encoding' on ${req.url} — cannot decode, passing raw"
                    )
                    return resp
                }
            }
        } catch (e: Exception) {
            AppLog.e("WebAcceptEncoding", "decode '$encoding' failed on ${req.url}", e)
            return resp // возвращаем нераспакованное — пусть вызывающий увидит ошибку
        }

        val newBody = decodedBytes.toResponseBody(body.contentType())
        return resp.newBuilder()
            .body(newBody)
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length") // длина изменилась после распаковки
            .build()
    }
}
