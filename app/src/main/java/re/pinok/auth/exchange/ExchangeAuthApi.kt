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
import re.pinok.util.ExponentialBackoff
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Low-level HTTP wrapper — cloned from decompiled VK 8.178.
 *
 * Source classes:
 *   - com.vk.superapp.api.internal.oauthrequests.AuthByExchangeToken
 *   - com.vk.superapp.api.states.VkAuthState (builders a/b/d/e)
 *   - com.vk.superapp.api.analytics.RegistrationStatParamsFactory
 *
 * ⚠️ Два эндпоинта (HISTORY.md #18, #7):
 *   1. oauth.vk.com/access_token — password, 2FA, trusted_hash, without_password,
 *      vk_external_auth. Требует client_secret. Официально документирован:
 *      https://dev.vk.com/api/direct-auth
 *   2. id.vk.com/auth_by_exchange_token — ТОЛЬКО exchange_token refresh.
 *      НЕ поддерживает grant_type=password (возвращает 404, см. HISTORY.md #5,#18).
 *
 * Parameters (from VkAuthState builders):
 *   Always:  client_id, device_id, scope=all
 *
 *   Password login (VkAuthState.b, sid=null):
 *     grant_type=password, username, password, 2fa_supported=1, supported_ways=push,email
 *
 *   2FA code (VkAuthState.b, sid!=null; or VkAuthState.d, z=false):
 *     grant_type=phone_confirmation_sid, sid, username, code, 2fa_supported=1, supported_ways=push,email
 *
 *   2FA without password / push-approved (VkAuthState.d, z=true):
 *     grant_type=without_password, sid, username, password="", 2fa_supported=1, supported_ways=push,email
 *
 *   Trusted hash re-login (VkAuthState.e):
 *     grant_type=trusted_hash, sid, username, password=""
 *
 *   External service auth (VkAuthState.a):
 *     grant_type=vk_external_auth, vk_service, vk_external_code, vk_external_client_id,
 *     vk_external_redirect_uri, code_verifier, nonce, 2fa_supported=1
 *
 *   Exchange token refresh (AuthByExchangeToken constructor):
 *     grant_type=exchange_token, exchange_token, scope=all, initiator=expired_token|...,
 *     validate_session, silent_auth_by_login
 */
class ExchangeAuthApi(
    private val httpClient: OkHttpClient,
) {

    // =====================================================================
    // Password auth — VkAuthState.b(sid=null)
    // =====================================================================

    /**
     * Step 1 — password auth.
     * POST oauth.vk.com/access_token with grant_type=password.
     * Requires client_secret. Endpoint id.vk.com/auth_by_exchange_token
     * does NOT support grant_type=password (returns 404).
     */
    suspend fun authByPassword(
        phone: String,
        password: String,
        deviceId: String,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "password")
            add("username", phone)
            add("password", password)
            add("2fa_supported", "1")
            add("supported_ways", "push,email")
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // 2FA code — VkAuthState.b(sid!=null) / VkAuthState.d(z=false)
    // =====================================================================

    /**
     * Step 2 — submit 2FA code.
     * POST oauth.vk.com/access_token with grant_type=phone_confirmation_sid.
     */
    suspend fun authBy2FaCode(
        phone: String,
        sid: String,
        code: String,
        deviceId: String,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "phone_confirmation_sid")
            add("sid", sid)
            add("username", phone)
            add("code", code)
            add("2fa_supported", "1")
            add("supported_ways", "push,email")
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // Without password (push-approved) — VkAuthState.d(z=true)
    // =====================================================================

    /**
     * 2FA without password — push-approved login.
     * POST oauth.vk.com/access_token with grant_type=without_password.
     */
    suspend fun authWithoutPassword(
        phone: String,
        sid: String,
        deviceId: String,
        additionalSignUpAgreementShowed: Boolean = false,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "without_password")
            add("sid", sid)
            add("username", phone)
            add("password", "")
            add("2fa_supported", "1")
            add("supported_ways", "push,email")
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
            if (additionalSignUpAgreementShowed) {
                add("additional_sign_up_agreement_showed", "1")
            }
            // addRegistrationStatParams — requires VK superapp SDK, skip for now
        },
    )

    // =====================================================================
    // Trusted hash re-login — VkAuthState.e(sid, username)
    // =====================================================================

    /**
     * Re-login via trusted device hash.
     * POST oauth.vk.com/access_token with grant_type=trusted_hash.
     */
    suspend fun authByTrustedHash(
        phone: String,
        trustedHash: String,
        deviceId: String,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "trusted_hash")
            add("password", "")
            add("username", phone)
            add("sid", trustedHash)
            add("2fa_supported", "1")
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // External service auth — VkAuthState.a(...)
    // =====================================================================

    /**
     * External service auth (VK ID, Google, Mail.ru, etc.).
     * POST oauth.vk.com/access_token with grant_type=vk_external_auth.
     */
    suspend fun authByExternalService(
        vkService: String,
        externalCode: String,
        externalClientId: String,
        externalRedirectUri: String,
        deviceId: String,
        codeVerifier: String? = null,
        nonce: String? = null,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "vk_external_auth")
            add("vk_service", vkService)
            add("vk_external_code", externalCode)
            add("vk_external_client_id", externalClientId)
            add("vk_external_redirect_uri", externalRedirectUri)
            if (!codeVerifier.isNullOrBlank()) add("code_verifier", codeVerifier)
            if (!nonce.isNullOrBlank()) add("nonce", nonce)
            add("2fa_supported", "1")
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // Exchange token refresh — AuthByExchangeToken constructor
    // =====================================================================

    /**
     * Get fresh exchange_token.
     * Cloned from AuthGetExchangeItemsCommand.g():
     *   return { exchange: API.auth.getExchangeToken({v:'...'}) };
     */
    suspend fun getExchangeToken(accessToken: String): String? {
        return when (val r = getExchangeTokenDetailed(accessToken)) {
            is ExchangeTokenResult.Success -> r.exchangeToken
            else -> null
        }
    }

    /**
     * Fix #230: то же что [getExchangeToken], но возвращает sealed result,
     * различающий «токен мёртв» (err=5) от «метод недоступен» (err=3/network).
     *
     * Вызывающий код (saveOAuthToken) использует это чтобы НЕ сохранять
     * невалидный токен при err=5 subcode 1130 (IP mismatch — типично при
     * подключении к Bluetooth-магнитоле с tethering, или cell handover).
     */
    suspend fun getExchangeTokenDetailed(accessToken: String): ExchangeTokenResult {
        val code = """
            return {
                exchange: API.auth.getExchangeToken({v:"${BuildConfig.VK_API_VERSION}"})
            };
        """.trimIndent()

        val json = try {
            // #NETWORK-RESILIENCE: exponential backoff на transient network errors.
            // execute endpoint лёгкий (< 200мс), поэтому 4 попытки с 0.5/1/2/4 сек.
            // Non-transient (JsonSyntaxException, err=5/3) — пробрасывается без retry.
            ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.API_LIGHT,
                tag = "getExchangeToken",
            ) {
                postForm(
                    url = "${BuildConfig.VK_API_HOST}/method/execute",
                    form = formBody {
                        add("code", code)
                        add("access_token", accessToken)
                        add("v", BuildConfig.VK_API_VERSION)
                    },
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "getExchangeTokenDetailed network error (all retries exhausted): ${e.message}")
            return ExchangeTokenResult.Unavailable
        }
        // retryOnTransient вернёт null только если все попытки провалились (IOException
        // на каждой). В этом случае токен возможно валиден, просто exchange_token
        // временно недоступен — Unavailable (не TokenInvalid).
        ?: run {
            AppLog.w(TAG, "getExchangeTokenDetailed: all retry attempts returned null — Unavailable")
            return ExchangeTokenResult.Unavailable
        }

        return try {
            val errObj = json.getAsJsonObject("error")
            if (errObj != null) {
                val errCode = errObj.get("error_code")?.asInt ?: -1
                val errSubcode = errObj.get("error_subcode")?.asInt ?: 0
                val errMsg = errObj.get("error_msg")?.asString ?: "unknown"
                AppLog.w(TAG, "getExchangeTokenDetailed API error $errCode/$errSubcode: $errMsg")
                // Fix #230: err=5 = access_token невалиден (expired/revoked/IP mismatch).
                // subcode 1130 = "given to another ip address" — типично при смене IP.
                // #EXCHANGE-IP-MISMATCH (Issue A): subcode 1130 НЕ означает, что
                // токен мёртв. auth.getExchangeToken строже проверяет IP, чем
                // обычные API-методы (newsfeed.get, messages.getLongPollServer
                // работают с того же IP с тем же токеном). Лог 2026-08-01 11:11:30
                // это доказывает: error 5/1130 на exchange, но через 6 сек
                // messages.getLongPollServer → HTTP 200. Возвращаем Unavailable
                // (метод недоступен), а не TokenInvalid — иначе saveWebTokenResult
                // откатывает сохранение (storage.clearAccessToken) → бесполезный
                // retry → 4 сек задержки + риск logout если retry упадёт.
                //
                // Применимо к web-flow токенам (app_id=7879029 / 7934655): VK
                // выдаёт exchange_token только official VK apps (client_id 2274003).
                // Web-токены принципиально не могут получить exchange_token —
                // error 15 "Invalid app" (subcode 0) на втором вызове это подтверждает.
                // Свежий web_token ВСЕГДА валиден для API; refresh идёт через
                // silentRefreshViaRemixsid (Path 1.5 в ensureFreshToken).
                if (errCode == 5 && errSubcode != 1130) {
                    return ExchangeTokenResult.TokenInvalid
                }
                // err=5/1130 (IP mismatch), err=3 (Unknown method),
                // err=15 (Invalid app) и прочие = exchange_token недоступен,
                // но access_token работает.
                return ExchangeTokenResult.Unavailable
            }
            val responseObj = json.getAsJsonObject("response")
                ?: return ExchangeTokenResult.Unavailable
            val exchangeElem = responseObj.get("exchange")
            if (exchangeElem == null || !exchangeElem.isJsonObject)
                return ExchangeTokenResult.Unavailable
            val tokensArr = exchangeElem.asJsonObject
                .getAsJsonArray("users_exchange_tokens")
                ?: return ExchangeTokenResult.Unavailable
            if (tokensArr.isEmpty) return ExchangeTokenResult.Unavailable
            val token = tokensArr[0].asJsonObject
                .get("exchange_token")?.takeIf { !it.isJsonNull }?.asString
                ?: return ExchangeTokenResult.Unavailable
            ExchangeTokenResult.Success(token)
        } catch (e: Exception) {
            AppLog.e(TAG, "getExchangeTokenDetailed parse error", e)
            ExchangeTokenResult.Unavailable
        }
    }

    /**
     * Exchange exchange_token for fresh access_token.
     * Cloned from AuthByExchangeToken constructor (initiator=EXPIRED_TOKEN):
     *   grant_type=exchange_token, exchange_token, scope=all, initiator=expired_token,
     *   validate_session, silent_auth_by_login
     */
    suspend fun authByExchangeToken(
        exchangeToken: String,
        deviceId: String,
        initiator: Initiator,
        validateSession: String? = null,
        silentAuthByLogin: Boolean = false,
    ): JsonObject = postExchangeTokenEndpoint(
        form = formBody {
            add("grant_type", "exchange_token")
            add("exchange_token", exchangeToken)
            add("scope", "all")
            initiator.value?.let { add("initiator", it) }
            if (!validateSession.isNullOrBlank()) add("validate_session", validateSession)
            if (silentAuthByLogin) add("silent_auth_by_login", "1")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // Web Token auth — VK web flow
    // =====================================================================
    // NOTE (audit Medium #2): getAnonymToken() и getWebToken() удалены как
    // мёртвый код — рабочий web-token flow реализован в WebTokenAuth.kt
    // (login.vk.com/?act=get_anonym_token + web_token с remixsid cookie).
    // Эти методы (api.vk.com/method/auth.*) никто не вызывал —signInByAnonymFlow
    // в Repository тоже удалён. Остаётся только validateWebToken() для
    // ручного ввода токена пользователем.

    /**
     * Step 2 alt — direct web_token exchange with a pre-existing token.
     * Use this when the user provides a web_token directly (e.g. from browser).
     * Validates the token by calling a lightweight API method.
     */
    suspend fun validateWebToken(webToken: String): JsonObject = postForm(
        url = "${BuildConfig.VK_API_HOST}/method/account.getProfileInfo",
        form = formBody {
            add("access_token", webToken)
            add("v", BuildConfig.VK_API_VERSION)
        },
    )

    // =====================================================================
    // LongPoll
    // =====================================================================

    /**
     * Fetch LongPoll credentials via messages.getLongPollServer.
     * (Not part of AuthByExchangeToken, required for messenger.)
     */
    suspend fun getLongPollServer(accessToken: String): JsonObject = postForm(
        url = "${BuildConfig.VK_API_HOST}/method/messages.getLongPollServer",
        form = formBody {
            add("need_pts", "1")
            add("lp_version", "4")
            add("access_token", accessToken)
            add("v", BuildConfig.VK_API_VERSION)
        },
    )

    // =====================================================================
    // Resend 2FA code
    // =====================================================================

    /**
     * Request a new 2FA code via a specific method.
     *
     * Mirrors decompiled VkAuthState.b(username, password, sid, isPhoneConfirmationSid=false):
     *   grant_type=password (replays the auth with same credentials),
     *   sid included so VK reuses the active validation session and can switch channel.
     *
     * supported_ways lists channels the client can receive through.
     * For SMS we add force_sms=true (VK API switch).
     *
     * Before audit #18 we used grant_type=phone_confirmation_sid (which is for SUBMITTING
     * the code, not resending) and VK silently fell back to SMS.
     */
    suspend fun resendValidationCode(
        phone: String,
        password: String,
        sid: String,
        validationType: ValidationType,
        deviceId: String,
    ): JsonObject = postLegacyAuthEndpoint(
        form = formBody {
            add("grant_type", "password")
            add("username", phone)
            add("password", password)
            add("sid", sid)
            add("2fa_supported", "1")
            // supported_ways mirrors VK client: push + email (sms via force_sms flag).
            add("supported_ways", "push,email")
            // force_sms is the official VK switch to request SMS specifically.
            if (validationType == ValidationType.SMS) {
                add("force_sms", "true")
            }
            add("scope", "all")
            add("client_id", BuildConfig.VK_CLIENT_ID)
            add("client_secret", BuildConfig.VK_CLIENT_SECRET)
            add("device_id", deviceId)
        },
    )

    // =====================================================================
    // Internal
    // =====================================================================

    /** Password / 2FA / trusted_hash / external auth → oauth.vk.com/access_token */
    private suspend fun postLegacyAuthEndpoint(form: FormBody): JsonObject =
        postForm(url = LEGACY_AUTH_ENDPOINT, form = form)

    /** Exchange token refresh → id.vk.com/auth_by_exchange_token */
    private suspend fun postExchangeTokenEndpoint(form: FormBody): JsonObject =
        postForm(url = EXCHANGE_TOKEN_ENDPOINT, form = form)

    private suspend fun postForm(url: String, form: FormBody): JsonObject {
        val req = Request.Builder().url(url).post(form).build()

        // Логируем запрос (без пароля в логах для безопасности).
        val grantType = formValue(form, "grant_type")
        val username = formValue(form, "username")
        AppLog.i(TAG, "POST $url  grant_type=$grantType  username=$username")

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
                            // Логируем ответ (обрезаем токены для безопасности).
                            val safeBody = body
                                .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]+\""), "\"access_token\":\"***\"")
                                .replace(Regex("\"exchange_token\"\\s*:\\s*\"[^\"]+\""), "\"exchange_token\":\"***\"")
                                .take(500)
                            val httpCode = response.code
                            AppLog.i(TAG, "POST $url → HTTP $httpCode  body=$safeBody")
                            cont.resume(body to httpCode)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                })
            }

            val (body, code) = rawBody

            val parsed = try {
                JsonParser.parseString(body).asJsonObject
            } catch (e: Exception) {
                val preview = body.take(80).replace('\n', ' ').replace('\r', ' ')
                AppLog.e(TAG, "Non-JSON from $url (HTTP $code): $preview")
                throw IOException(
                    when {
                        code == 404 -> "VK endpoint not found (404). Проверьте client_id/secret."
                        code in 500..599 -> "Сервер VK недоступен ($code). Попробуйте позже."
                        body.isBlank() -> "Пустой ответ от сервера VK (HTTP $code)."
                        body.contains("<html", ignoreCase = true) ->
                            "Сервер VK вернул HTML вместо JSON (HTTP $code)."
                        else -> "Не удалось связаться с VK (HTTP $code)."
                    }
                )
            }

            parsed
        }
    }

    private inline fun formBody(builder: FormBody.Builder.() -> Unit): FormBody =
        FormBody.Builder().apply(builder).build()

    /**
     * Mirrors AuthByExchangeToken$Initiator from decompiled VK 8.178.
     * NO_INITIATOR has value=null (VK sends no initiator param for initial auth).
     */
    enum class Initiator(val value: String?) {
        NO_INITIATOR(null),
        EXPIRED_TOKEN("expired_token"),
        ADD_EDU_PROFILE("add_edu_profile"),
        AUTHORIZATION("authorization"),
        SILENT_AUTHORIZATION("silent_authorization"),
        WEB_HANDLER_AUTHORIZATION("web_handler_authorization"),
        ;

        companion object {
            fun fromString(s: String?): Initiator =
                entries.firstOrNull { it.value == s } ?: NO_INITIATOR
        }
    }

    /** Extract a form field value by name from OkHttp FormBody (which only has index-based access). */
    private fun formValue(form: FormBody, name: String): String {
        for (i in 0 until form.size) {
            if (form.name(i) == name) return form.value(i)
        }
        return "?"
    }

    private companion object {
        const val TAG = "ExchangeAuthApi"

        /**
         * Legacy VK OAuth endpoint — handles password auth, 2FA, trusted_hash,
         * without_password, vk_external_auth.
         * Requires client_secret.
         *
         * Fix #189: домен берётся из AuthDomainsConfig (oauth.vk.com / oauth.vk.ru).
         * Раньше был const val — теперь это computed property, читающая snapshot.
         */
        val LEGACY_AUTH_ENDPOINT: String
            get() = AuthDomainsConfig.oauthAccessTokenUrl()

        /**
         * VK ID exchange token endpoint — ONLY for exchange_token refresh.
         * From decompiled AuthByExchangeToken.kt constructor:
         *   this.f = z400.a("https://", str, "/auth_by_exchange_token")
         * Does NOT support grant_type=password (returns 404).
         *
         * Fix #189: домен берётся из AuthDomainsConfig (id.vk.com / id.vk.ru).
         */
        val EXCHANGE_TOKEN_ENDPOINT: String
            get() = AuthDomainsConfig.idExchangeTokenUrl()
    }
}