package re.pinok.auth.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import re.pinok.BuildConfig
import re.pinok.util.AppLog
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Top-level helper — строит FormBody из lambda.
 *
 * ВАЖНО: должен быть top-level (не членом класса), иначе вложенный
 * enum class ExchangeEndpoint не сможет его вызвать — enum не может
 * быть `inner` в Kotlin, и вызов члена внешнего класса из не-inner
 * вложенного класса даёт ошибку "Outer class of non-inner class
 * cannot be used as receiver".
 */
private inline fun formBody(builder: FormBody.Builder.() -> Unit): FormBody =
    FormBody.Builder().apply(builder).build()

/**
 * #VK-SILENT-TOKEN-EXCHANGE — обмен silent_token → access_token.
 *
 * ─────────────────────────────────────────────────────────────────────
 * КОНТЕКСТ (из анализа VK_autor.zip / auth.js, см. VK_IMPORT_API.MD §41.14)
 * ─────────────────────────────────────────────────────────────────────
 * VK ID `silent_token` flow (response_type=silent_token, используют VK Music
 * app_id=51421844 и VK Messenger app_id=8223270) возвращает НЕ access_token,
 * а пару { silent_token, silent_token_uuid }.
 *
 * В auth.js найдена структура ответа VK ID:
 *   { authToken, anonymousToken, silentToken, silentTokenUUID,
 *     providerAppId, flowType, nextStep, errorSubcode, ... }
 *
 * silent_token сам по себе — НЕ access_token. Это "тихий" токен, который
 * нужно обменять на полноценный access_token через VK API method
 * `auth.getAuthData` (найден в auth.js: `auth.getAuthData` через apiRequest).
 *
 * ─────────────────────────────────────────────────────────────────────
 * FLOW
 * ─────────────────────────────────────────────────────────────────────
 * 1. VK ID SDK (m.vk.ru) выдаёт silent_token + uuid после подтверждения входа.
 * 2. VK ID сервер редиректит на redirect_uri с silent_token + silent_token_uuid
 *    в ответе (fragment или query).
 * 3. Этот класс ловит silent_token+uuid и делает POST на
 *    {api_host}/method/auth.getAuthData с параметрами:
 *      silent_token, silent_token_uuid (или token_uuid), access_token (anonym),
 *      client_id, device_id, v
 * 4. В ответе — access_token (vk1.a.XXX) + user_id + expires_in.
 *
 * ─────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ ЭТО ВАЖНО
 * ─────────────────────────────────────────────────────────────────────
 * Без этого шага SSO через VK app "зависает" — VK app подтверждает вход,
 * но PinoK не получает access_token (только silent_token, который бесполезен
 * сам по себе). Это была вероятная причина #VK-2FA-SSO "app остаётся без
 * авторизации" — silent_token получен, но не обменян.
 *
 * ─────────────────────────────────────────────────────────────────────
 * АЛЬТЕРНАТИВНЫЕ EXCHANGE ENDPOINTS (попытки по очереди)
 * ─────────────────────────────────────────────────────────────────────
 * auth.js минифицирован, точный exchange-endpoint не виден напрямую.
 * Известные кандидаты (пробуем по очереди до успеха):
 *   1. {api_host}/method/auth.getAuthData — VK API method (найден в auth.js)
 *   2. {api_host}/method/auth.getAnonymToken — найден в auth.js (anonym+exchange)
 *   3. {id_host}/auth_by_silent_token — REST endpoint (по аналогии с
 *      auth_by_exchange_token, может существовать)
 *   4. {oauth_host}/access_token?grant_type=silent_token — OAuth2 extension
 *
 * Реальные параметры exchange-запроса (из auth.js структуры):
 *   silent_token, silent_token_uuid (или token_uuid), access_token (anonym),
 *   client_id, device_id, v, scope
 */
class SilentTokenExchanger(
    private val httpClient: OkHttpClient,
) {

    /**
     * Результат обмена silent_token → access_token.
     */
    sealed class Result {
        /** Успех: access_token + user_id + expires_in получены. */
        data class Success(
            val accessToken: String,
            val userId: Long,
            val expiresIn: Long,
            val raw: JsonObject,
        ) : Result()

        /** silent_token невалиден / истёк / отозван. */
        data class TokenInvalid(val message: String) : Result()

        /** Сеть недоступна / сервер вернул 5xx / parse error. */
        data class Unavailable(val message: String) : Result()

        /** Все endpoints вернули ошибку — exchange невозможен. */
        data class AllEndpointsFailed(val errors: List<String>) : Result()
    }

    /**
     * Обмен silent_token + uuid на access_token.
     *
     * @param silentToken silent_token из VK ID ответа (НЕ access_token!).
     * @param silentTokenUuid silent_token_uuid из того же ответа.
     * @param anonymousToken anonymous_token из VK ID init (может быть null —
     *        тогда exchange может не сработать, но пробуем).
     * @param providerAppId app_id провайдера (51421844 для VK Music,
     *        8223270 для VK Messenger, 6287487 для общего VK ID).
     * @param deviceId device_id из SovaPrefs (формат "87v-we10y1_697oTiTmI8").
     */
    suspend fun exchange(
        silentToken: String,
        silentTokenUuid: String,
        anonymousToken: String?,
        providerAppId: String,
        deviceId: String,
    ): Result {
        AppLog.i(TAG, "exchange: silent_token=${silentToken.take(12)}... uuid=$silentTokenUuid provider=$providerAppId")

        val errors = mutableListOf<String>()

        // Пытаемся по очереди 4 кандидата exchange-endpoint'а.
        for (endpoint in ExchangeEndpoint.values()) {
            val result = tryExchange(endpoint, silentToken, silentTokenUuid, anonymousToken, providerAppId, deviceId)
            when (result) {
                is Result.Success -> {
                    AppLog.i(TAG, "exchange: УСПЕХ через ${endpoint.name} → user_id=${result.userId}")
                    return result
                }
                is Result.TokenInvalid -> {
                    AppLog.w(TAG, "exchange: ${endpoint.name} → TokenInvalid: ${result.message}")
                    errors.add("${endpoint.name}: TokenInvalid(${result.message})")
                    // TokenInvalid = silent_token протух, нет смысла пробовать другие endpoints.
                    return result
                }
                is Result.Unavailable -> {
                    AppLog.w(TAG, "exchange: ${endpoint.name} → Unavailable: ${result.message}")
                    errors.add("${endpoint.name}: Unavailable(${result.message})")
                    // Пробуем следующий endpoint.
                }
                is Result.AllEndpointsFailed -> {
                    errors.addAll(result.errors)
                }
            }
        }

        return Result.AllEndpointsFailed(errors)
    }

    /**
     * Одна попытка exchange через конкретный endpoint.
     */
    private suspend fun tryExchange(
        endpoint: ExchangeEndpoint,
        silentToken: String,
        silentTokenUuid: String,
        anonymousToken: String?,
        providerAppId: String,
        deviceId: String,
    ): Result {
        val url = endpoint.url()
        val form = endpoint.buildForm(
            silentToken = silentToken,
            silentTokenUuid = silentTokenUuid,
            anonymousToken = anonymousToken,
            providerAppId = providerAppId,
            deviceId = deviceId,
        )

        val grantType = endpoint.grantType
        AppLog.i(TAG, "POST $url  grant_type=$grantType  provider=$providerAppId")

        return try {
            val json = postForm(url, form)
            parseExchangeResponse(json)
        } catch (e: IOException) {
            AppLog.w(TAG, "${endpoint.name} network error: ${e.message}")
            Result.Unavailable(e.message ?: "network error")
        } catch (e: Exception) {
            AppLog.e(TAG, "${endpoint.name} parse error", e)
            Result.Unavailable(e.message ?: "parse error")
        }
    }

    /**
     * Парсит ответ exchange-запроса.
     *
     * VK API format: { response: { access_token, user_id, expires_in, ... } }
     *   или { response: { user: { id, ... }, access_token, ... } }
     * OAuth2 error: { error: "invalid_grant", error_description: "..." }
     */
    private fun parseExchangeResponse(json: JsonObject): Result {
        // OAuth2 error format
        val errorObj = json.getAsJsonObject("error")
        if (errorObj != null) {
            val errorCode = errorObj.get("error_code")?.asInt
            val errorMsg = errorObj.get("error_msg")?.asString
                ?: errorObj.get("error")?.asString
                ?: "unknown error"
            AppLog.w(TAG, "exchange API error: code=$errorCode msg=$errorMsg")
            // err=5 = access_token невалиден (для anonym_token-based exchange).
            // invalid_grant = silent_token протух или невалиден.
            return if (errorCode == 5 || errorMsg.contains("invalid_grant", ignoreCase = true) ||
                errorMsg.contains("expired", ignoreCase = true)) {
                Result.TokenInvalid("$errorCode: $errorMsg")
            } else {
                Result.Unavailable("$errorCode: $errorMsg")
            }
        }

        // VK API success format: { response: { ... } }
        val responseObj = json.getAsJsonObject("response")
            ?: return Result.Unavailable("no 'response' field: ${json.toString().take(200)}")

        // access_token может быть на верхнем уровне response или внутри user/token объекта.
        val accessToken = responseObj.get("access_token")?.takeIf { !it.isJsonNull }?.asString
            ?: responseObj.getAsJsonObject("user")?.get("access_token")?.takeIf { !it.isJsonNull }?.asString
            ?: responseObj.getAsJsonObject("token")?.get("access_token")?.takeIf { !it.isJsonNull }?.asString
            ?: return Result.Unavailable("no access_token in response: ${responseObj.toString().take(200)}")

        val userId = responseObj.get("user_id")?.takeIf { !it.isJsonNull }?.asLong
            ?: responseObj.getAsJsonObject("user")?.get("id")?.takeIf { !it.isJsonNull }?.asLong
            ?: 0L

        val expiresIn = responseObj.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong
            ?: 0L

        return Result.Success(
            accessToken = accessToken,
            userId = userId,
            expiresIn = expiresIn,
            raw = responseObj,
        )
    }

    // =====================================================================
    // HTTP
    // =====================================================================

    private suspend fun postForm(url: String, form: FormBody): JsonObject {
        val req = Request.Builder().url(url).post(form).build()

        return withContext(Dispatchers.IO) {
            val rawBody = suspendCancellableCoroutine { cont ->
                val call = httpClient.newCall(req)
                cont.invokeOnCancellation { runCatching { call.cancel() } }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        AppLog.e(TAG, "POST $url FAILED: ${e.message}")
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val body = response.body?.string().orEmpty()
                            val safeBody = body
                                .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]+\""), "\"access_token\":\"***\"")
                                .replace(Regex("\"silent_token\"\\s*:\\s*\"[^\"]+\""), "\"silent_token\":\"***\"")
                                .take(500)
                            AppLog.i(TAG, "POST $url → HTTP ${response.code}  body=$safeBody")
                            cont.resume(body to response.code)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                })
            }

            val (body, code) = rawBody
            try {
                JsonParser.parseString(body).asJsonObject
            } catch (e: Exception) {
                val preview = body.take(80).replace('\n', ' ').replace('\r', ' ')
                AppLog.e(TAG, "Non-JSON from $url (HTTP $code): $preview")
                throw IOException(
                    when {
                        code == 404 -> "VK endpoint not found (404): $url"
                        code in 500..599 -> "Сервер VK недоступен ($code)"
                        body.isBlank() -> "Пустой ответ (HTTP $code)"
                        else -> "Не JSON (HTTP $code): $preview"
                    }
                )
            }
        }
    }

    /**
     * Кандидаты exchange-endpoint'а.
     *
     * auth.js минифицирован — точный endpoint не виден. Пробуем по очереди
     * известные VK API methods и REST paths. Порядок: от наиболее вероятного
     * к наименее.
     */
    enum class ExchangeEndpoint(val grantType: String) {
        // 1. VK API method auth.getAuthData — найден в auth.js.
        //    Вызывается как {api_host}/method/auth.getAuthData.
        AUTH_GET_AUTH_DATA("silent_token") {
            override fun url(): String =
                "https://${AuthDomainsConfig.current.apiHost}/method/auth.getAuthData"
            override fun buildForm(
                silentToken: String, silentTokenUuid: String,
                anonymousToken: String?, providerAppId: String, deviceId: String,
            ): FormBody = formBody {
                add("silent_token", silentToken)
                add("silent_token_uuid", silentTokenUuid)
                if (!anonymousToken.isNullOrBlank()) add("access_token", anonymousToken)
                add("client_id", providerAppId)
                add("device_id", deviceId)
                add("v", BuildConfig.VK_API_VERSION)
                add("scope", "all")
            }
        },

        // 2. VK API method auth.getAnonymToken — найден в auth.js.
        //    Принимает client_id + device_id, возможно принимает silent_token.
        AUTH_GET_ANONYM_TOKEN("silent_token") {
            override fun url(): String =
                "https://${AuthDomainsConfig.current.apiHost}/method/auth.getAnonymToken"
            override fun buildForm(
                silentToken: String, silentTokenUuid: String,
                anonymousToken: String?, providerAppId: String, deviceId: String,
            ): FormBody = formBody {
                add("silent_token", silentToken)
                add("silent_token_uuid", silentTokenUuid)
                add("client_id", providerAppId)
                add("device_id", deviceId)
                add("v", BuildConfig.VK_API_VERSION)
            }
        },

        // 3. REST endpoint auth_by_silent_token (по аналогии с auth_by_exchange_token).
        //    Может существовать на id.vk.com/.ru.
        ID_AUTH_BY_SILENT_TOKEN("silent_token") {
            override fun url(): String =
                "https://${AuthDomainsConfig.current.idHost}/auth_by_silent_token"
            override fun buildForm(
                silentToken: String, silentTokenUuid: String,
                anonymousToken: String?, providerAppId: String, deviceId: String,
            ): FormBody = formBody {
                add("grant_type", "silent_token")
                add("silent_token", silentToken)
                add("silent_token_uuid", silentTokenUuid)
                if (!anonymousToken.isNullOrBlank()) add("anonymous_token", anonymousToken)
                add("client_id", providerAppId)
                add("device_id", deviceId)
                add("v", BuildConfig.VK_API_VERSION)
                add("scope", "all")
            }
        },

        // 4. OAuth2 extension: grant_type=silent_token на legacy endpoint.
        OAUTH_SILENT_TOKEN("silent_token") {
            override fun url(): String =
                AuthDomainsConfig.oauthAccessTokenUrl()
            override fun buildForm(
                silentToken: String, silentTokenUuid: String,
                anonymousToken: String?, providerAppId: String, deviceId: String,
            ): FormBody = formBody {
                add("grant_type", "silent_token")
                add("silent_token", silentToken)
                add("silent_token_uuid", silentTokenUuid)
                if (!anonymousToken.isNullOrBlank()) add("anonymous_token", anonymousToken)
                add("client_id", providerAppId)
                add("device_id", deviceId)
                add("v", BuildConfig.VK_API_VERSION)
                add("scope", "all")
            }
        },

        // 5. VK API execute — universal method, может содержать exchange logic.
        EXECUTE_EXCHANGE("silent_token") {
            override fun url(): String =
                "https://${AuthDomainsConfig.current.apiHost}/method/execute"
            override fun buildForm(
                silentToken: String, silentTokenUuid: String,
                anonymousToken: String?, providerAppId: String, deviceId: String,
            ): FormBody = formBody {
                // VKScript: вызываем auth.getAuthData с silent_token.
                val code = """
                    return API.auth.getAuthData({
                        silent_token: "$silentToken",
                        silent_token_uuid: "$silentTokenUuid",
                        client_id: $providerAppId,
                        device_id: "$deviceId",
                        v: "${BuildConfig.VK_API_VERSION}"
                    });
                """.trimIndent()
                add("code", code)
                if (!anonymousToken.isNullOrBlank()) add("access_token", anonymousToken)
                add("v", BuildConfig.VK_API_VERSION)
            }
        };

        abstract fun url(): String
        abstract fun buildForm(
            silentToken: String,
            silentTokenUuid: String,
            anonymousToken: String?,
            providerAppId: String,
            deviceId: String,
        ): FormBody
    }

    companion object {
        private const val TAG = "SilentTokenExchanger"

        /**
         * Парсит silent_token и silent_token_uuid из URL/fragment/JSON ответа VK ID.
         *
         * VK ID возвращает silent_token в разных форматах:
         *   - URL fragment: #silent_token=xxx&silent_token_uuid=yyy
         *   - URL query: ?silent_token=xxx&uuid=yyy
         *   - JSON: { silentToken: "xxx", silentTokenUUID: "yyy" }
         *   - postMessage: { type: "VK_ID_AUTH", silent_token, silent_token_uuid }
         *
         * @return pair (silent_token, silent_token_uuid) или null если не найдено.
         */
        fun parseSilentToken(input: String): Pair<String, String>? {
            val st = extractValue(input, "silent_token") ?: extractValue(input, "silentToken")
                ?: return null
            val uuid = extractValue(input, "silent_token_uuid")
                ?: extractValue(input, "silentTokenUUID")
                ?: extractValue(input, "uuid")
                ?: return null
            return st to uuid
        }

        /**
         * Парсит ПРЯМОЙ access_token (не silent_token!) из URL редиректа VK ID.
         *
         * VK ID может вернуть сразу готовый access_token (vk1.a.XXX) в URL
         * fragment, БЕЗ необходимости обмена. Это происходит когда:
         *   - response_type=token (classic OAuth2 implicit grant)
         *   - SSO уже было подтверждено ранее и VK ID кэшировал согласие
         *   - QR-polling страница id.vk.ru/auth после подтверждения в VK app
         *     редиректит на redirect_uri#access_token=...&user_id=...&expires_in=...
         *
         * В отличие от [parseSilentToken], этот метод НЕ требует uuid и
         * возвращает готовый access_token, который можно сразу сохранять
         * (без exchange через auth.getAuthData).
         *
         * Формат ответа (OAuth2 implicit grant):
         *   https://redirect_uri#access_token=vk1.a.XXX&user_id=12345&expires_in=86400
         *   https://redirect_uri?access_token=vk1.a.XXX&user_id=12345
         *   JSON: { "access_token":"vk1.a.XXX", "user_id":12345, "expires_in":86400 }
         *
         * @return triple (access_token, user_id, expires_in) или null если
         *         access_token не найден. user_id/expires_in могут быть 0 если
         *         отсутствуют в ответе.
         */
        fun parseDirectAccessToken(input: String): Triple<String, Long, Long>? {
            // access_token должен начинаться с "vk1.a." (VK ID JWT format) или
            // быть достаточно длинным (старый формат). Защита от ложных срабатываний
            // на "access_token" в других контекстах (например, в JS-коде страницы).
            val at = extractValue(input, "access_token") ?: return null
            if (at.length < 20) return null  // слишком короткий — не токен
            if (!at.startsWith("vk1.") && !at.startsWith("vk2.") && at.length < 50) {
                return null  // не VK ID JWT и не длинный legacy token
            }
            val userId = extractValue(input, "user_id")?.toLongOrNull()
                ?: extractValue(input, "userId")?.toLongOrNull()
                ?: 0L
            val expiresIn = extractValue(input, "expires_in")?.toLongOrNull()
                ?: extractValue(input, "expiresIn")?.toLongOrNull()
                ?: 0L
            return Triple(at, userId, expiresIn)
        }

        /**
         * Извлекает значение параметра из URL-query, JSON, или postMessage-формата.
         */
        private fun extractValue(input: String, key: String): String? {
            // 1. URL query/fragment: key=value (URL-encoded)
            val urlPattern = Regex("""[?&#]${Regex.escape(key)}=([^&#\s]+)""")
            urlPattern.find(input)?.let {
                return try {
                    java.net.URLDecoder.decode(it.groupValues[1], "UTF-8")
                } catch (e: Exception) {
                    it.groupValues[1]
                }
            }
            // 2. JSON: "key":"value"
            val jsonPattern = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]+)"""")
            jsonPattern.find(input)?.let { return it.groupValues[1] }
            // 3. JSON single-quote: 'key':'value'
            val jsonPattern2 = Regex("""'${Regex.escape(key)}'\s*:\s*'([^']+)'""")
            jsonPattern2.find(input)?.let { return it.groupValues[1] }
            return null
        }
    }
}
