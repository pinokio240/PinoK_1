package re.pinok.media

import com.google.gson.JsonParser
import java.util.Base64

/**
 * #CALLS: декодер conversation params входящего звонка VK.
 *
 * Payload LP 115 (входящий звонок) — строка формата `"<original_len>:<base64>"`.
 * Декодирование (по calls SDK vendors~calls-sdk `_decodeExternalConversationParams`):
 *   1. split(":") → [original_len, base64]
 *   2. base64 → байты
 *   3. LZ4-декомпрессия (block format) → JSON-строка
 *   4. JSON → { srcp, stne, tkn, trne, trnp, trnu, wse, wte }
 *   5. → { token: tkn, endpoint: wse, wt_endpoint: wte,
 *           turn_server: {urls: trne.split(","), username: trnu, credential: trnp},
 *           stun_server: {urls: stne.split(",")}, client_type: srcp }
 */
object ConversationParamsDecoder {

    data class IceServer(
        val urls: List<String>,
        val username: String? = null,
        val credential: String? = null,
    )

    data class Params(
        val token: String,
        val endpoint: String,       // wse — wssBase (WebSocket signaling)
        val wtEndpoint: String?,    // wte — WebTransport fallback
        val turnServer: IceServer?,
        val stunServer: IceServer?,
        val clientType: String,     // srcp
    )

    /**
     * LZ4 block format декомпрессия (соответствует JS из calls SDK).
     * @param src сжатые байты (base64-декодированные)
     * @param expectedLen оригинальная длина (число перед ':')
     */
    fun lz4Decompress(src: ByteArray, expectedLen: Int): ByteArray {
        val out = ByteArray(expectedLen)
        var si = 0
        var di = 0
        while (si < src.size && di < expectedLen) {
            val token = src[si++].toInt() and 0xFF
            var litLen = token ushr 4
            if (litLen == 15) {
                var b: Int
                do {
                    b = src[si++].toInt() and 0xFF
                    litLen += b
                } while (b == 255)
            }
            for (i in 0 until litLen) {
                if (di >= expectedLen) return out
                out[di++] = src[si++]
            }
            if (si >= src.size || di >= expectedLen) break
            val offset = (src[si].toInt() and 0xFF) or ((src[si + 1].toInt() and 0xFF) shl 8)
            si += 2
            var matchLen = token and 0x0F
            if (matchLen == 15) {
                var b: Int
                do {
                    b = src[si++].toInt() and 0xFF
                    matchLen += b
                } while (b == 255)
            }
            matchLen += 4
            val matchStart = di - offset
            for (i in 0 until matchLen) {
                if (di >= expectedLen) return out
                out[di++] = out[matchStart + i]
            }
        }
        return out
    }

    /**
     * Декодирует conversation params из payload LP 115.
     * @return Params или null при ошибке.
     */
    fun decode(payload: String): Params? {
        return try {
            val colon = payload.indexOf(':')
            if (colon <= 0) return null
            val len = payload.substring(0, colon).toIntOrNull() ?: return null
            val b64 = payload.substring(colon + 1)
            val raw = Base64.getDecoder().decode(b64)
            val decompressed = lz4Decompress(raw, len)
            val json = String(decompressed, Charsets.UTF_8)
            parseJson(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJson(json: String): Params? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val srcp = str(obj, "srcp")
            val stne = str(obj, "stne")
            val tkn = str(obj, "tkn")
            val trne = str(obj, "trne")
            val trnp = str(obj, "trnp")
            val trnu = str(obj, "trnu")
            val wse = str(obj, "wse")
            val wte = str(obj, "wte")
            Params(
                token = tkn ?: "",
                endpoint = wse ?: "",
                wtEndpoint = wte,
                turnServer = if (!trne.isNullOrBlank()) IceServer(
                    urls = trne.split(","),
                    username = trnu,
                    credential = trnp,
                ) else null,
                stunServer = if (!stne.isNullOrBlank()) IceServer(urls = stne.split(",")) else null,
                clientType = srcp ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Парсит Params из JSON-ответа vchat.getConversationParams.
     * Формат response (calls SDK):
     *   { id, endpoint, wtEndpoint, stun: {urls}, turn: {urls,username,credential},
     *     token, clientType, isConcurrent, device_idx }
     */
    fun decodeParamsJson(obj: com.google.gson.JsonObject): Params? {
        return try {
            val id = str(obj, "id")
            val endpoint = str(obj, "endpoint") ?: str(obj, "wssBase") ?: ""
            val wt = str(obj, "wtEndpoint")
            val token = str(obj, "token") ?: ""
            val clientType = str(obj, "clientType") ?: ""
            val stunObj = obj.get("stun")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: obj.get("stun_server")?.takeIf { it.isJsonObject }?.asJsonObject
            val turnObj = obj.get("turn")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: obj.get("turn_server")?.takeIf { it.isJsonObject }?.asJsonObject
            val stun = stunObj?.get("urls")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }
            val turnUrls = turnObj?.get("urls")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }
            Params(
                token = token,
                endpoint = endpoint,
                wtEndpoint = wt,
                turnServer = if (!turnUrls.isNullOrEmpty()) IceServer(
                    urls = turnUrls,
                    username = str(turnObj, "username"),
                    credential = str(turnObj, "credential"),
                ) else null,
                stunServer = if (!stun.isNullOrEmpty()) IceServer(urls = stun) else null,
                clientType = clientType,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun str(o: com.google.gson.JsonObject, name: String): String? =
        o.get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
}
