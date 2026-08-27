package re.pinok.auth.exchange

import com.google.gson.JsonObject
import re.pinok.util.AppLog

/**
 * P2-11 #AUTH-AUDIT: pure-функции парсинга auth response, вынесенные из
 * ExchangeAuthRepository.kt (god-class 3058 строк).
 *
 * Эти функции НЕ имеют побочных эффектов и НЕ зависят от состояния репозитория
 * (storage / httpClient / refreshMutex / app.apiClient). Они работают только
 * с входными параметрами (JsonObject / String / VKAuthException) и возвращают
 * значения (AuthState / AuthResult / ValidationType / Boolean / String?).
 *
 * Это безопасная часть декомпозиции: полная декомпозиция на UseCase классы
 * (TokenRefreshUseCase, SilentAuthUseCase, SaveOAuthTokenUseCase, etc.)
 * отложена из-за высокого риска регрессии — методы тесно связаны через
 * storage и refreshMutex.
 *
 * Используется из [ExchangeAuthRepository]:
 *   - parseAuthResponse (остался в репозитории, использует storage.saveAuthResult)
 *   - signIn / submit2FaCode / authWithoutPassword / signInByTrustedHash / etc.
 *   - silentRefreshViaRemixsid / doSilentRefreshRequest (используют isWrongOriginResponse)
 */
object AuthResponseParser {

    private const val TAG = "AuthResponseParser"

    /**
     * Parse error response — mirrors ev4.b() and AuthByExchangeToken.f().
     *
     * VK error hierarchy:
     *   - error.error == "need_validation" → 2FA required
     *   - error.error == "invalid_grant" / "invalid_credentials" → bad password
     *   - error.error_code == 5 + ban_info → BannedUserException
     *   - VKWebAuthException.o() → ExchangeTokenException
     *   - VKWebAuthException.m() → DeactivatedUserException
     *   - Otherwise → UnknownException
     */
    internal fun parseErrorState(err: JsonObject): AuthState {
        val errorStr = err.get("error")?.asString
            ?: err.get("error_code")?.asString
            ?: "unknown"

        val errCode = err.get("error_code")?.asInt ?: 0
        val errDesc = err.get("error_description")?.asString
            ?: err.get("error_msg")?.asString

        // Check for ban info — VK includes this when error_code=5 + ban payload.
        val banInfo = parseBanInfo(err)

        return when {
            // 2FA validation required — the most common flow after password.
            errorStr.equals("need_validation", ignoreCase = true) -> {
                val vt = err.get("validation_type")?.asString
                    ?: err.get("validation_method")?.asString ?: "sms"
                val sid = err.get("validation_sid")?.asString
                    ?: err.get("sid")?.asString
                    ?: return AuthState.Error(AuthErrorKind.UNKNOWN, "need_validation without sid")
                val phone = err.get("phone")?.takeIf { !it.isJsonNull }?.asString
                val hint = err.get("next_step")?.takeIf { !it.isJsonNull }?.asString
                val supportedWays = err.get("supported_ways")?.takeIf { !it.isJsonNull }?.asString
                    ?.split(",")
                    ?.mapNotNull { parseValidationType(it.trim()).takeIf { it != ValidationType.UNKNOWN } }
                    ?: emptyList()
                AuthState.NeedValidation(
                    validationType = parseValidationType(vt),
                    validationSid = sid,
                    phoneMask = phone,
                    nextStepHint = hint,
                    allowedWays = supportedWays,
                )
            }

            // Exchange token invalid — must re-login.
            errorStr.equals("invalid_exchange_token", ignoreCase = true) ||
            errorStr.equals("exchange_token_expired", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.EXCHANGE_TOKEN_INVALID, "Токен обмена недействителен. Войдите заново.")

            // Bad credentials.
            errorStr.equals("invalid_grant", ignoreCase = true) ||
            errorStr.equals("invalid_credentials", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.INVALID_CREDENTIALS, "Неверный логин или пароль")

            // Captcha.
            errorStr.equals("need_captcha", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.TOO_MANY_ATTEMPTS, "Требуется капча")

            // Rate limiting.
            errorStr.equals("too_many_attempts", ignoreCase = true) ||
            errorStr.equals("rate_limit", ignoreCase = true) ||
            errCode == 9 ->
                AuthState.Error(AuthErrorKind.TOO_MANY_ATTEMPTS,
                    errDesc ?: "Слишком много попыток. Подождите 10-15 минут.")

            errorStr.equals("too_many_requests", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.TOO_MANY_REQUESTS, "Слишком много запросов")

            // Banned — with ban info from error response.
            errorStr.equals("banned", ignoreCase = true) || banInfo != null -> {
                val reason = banInfo?.banDescription
                    ?: banInfo?.banReason
                    ?: errDesc
                    ?: "Аккаунт заблокирован"
                AuthState.Error(AuthErrorKind.BANNED, reason)
            }

            // Deactivated.
            errorStr.equals("deactivated", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.DEACTIVATED, "Аккаунт деактивирован")

            // Need signup.
            errorStr.equals("need_signup", ignoreCase = true) ||
            errorStr.equals("need_sign_up", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.NEED_SIGNUP, "Требуется регистрация")

            // Need restore.
            errorStr.equals("need_restore", ignoreCase = true) ||
            errorStr.equals("need_restore_password", ignoreCase = true) ->
                AuthState.Error(AuthErrorKind.NEED_RESTORE, "Требуется восстановление пароля")

            // Generic error with description.
            else -> {
                val msg = errDesc ?: errorStr
                AuthState.Error(AuthErrorKind.UNKNOWN, msg)
            }
        }
    }

    /**
     * Parse a successful AuthResult from JSON.
     * Mirrors ev4.a() from decompiled VK.
     *
     * Extracts all fields from com.vk.auth.api.models.AuthResult:
     *   access_token, exchange_token, user_id, expires_in, scope, secret,
     *   trusted_hash, utility_tokens, webview_access_token, etc.
     */
    internal fun parseAuthResultFromJson(json: JsonObject): AuthResult? {
        val root = json.getAsJsonObject("response") ?: json
        val at = root.get("access_token")?.takeIf { !it.isJsonNull }?.asString ?: return null
        if (at.isBlank()) return null
        val uid = root.get("user_id")?.asLong ?: root.get("mid")?.asLong ?: return null

        // Parse utility_tokens — mirrors UtilityTokens.a(json) from decompiled VK.
        val utilityTokens = parseUtilityTokens(root)

        return AuthResult(
            accessToken = at,
            exchangeToken = root.get("exchange_token")?.takeIf { !it.isJsonNull }?.asString,
            userId = uid,
            expiresIn = root.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            scope = root.get("scope")?.takeIf { !it.isJsonNull }?.asString ?: "all",
            secret = root.get("secret")?.takeIf { !it.isJsonNull }?.asString,
            trustedHash = root.get("trusted_hash")?.takeIf { !it.isJsonNull }?.asString,
            webviewAccessToken = root.get("webview_access_token")?.takeIf { !it.isJsonNull }?.asString,
            webviewRefreshToken = root.get("webview_refresh_token")?.takeIf { !it.isJsonNull }?.asString,
            webviewExpiresIn = root.get("webview_expires_in")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            utilityTokens = utilityTokens,
            email = root.get("email")?.takeIf { !it.isJsonNull }?.asString,
            // Fix #213 (P0.2): silent_token + silent_token_uuid — 4-й fallback уровень.
            // VK отдаёт их в auth_by_exchange_token response если включён VKID SDK flow.
            silentToken = root.get("silent_token")?.takeIf { !it.isJsonNull }?.asString,
            silentTokenUuid = root.get("silent_token_uuid")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    /**
     * Parse utility tokens from the auth response.
     * Mirrors UtilityTokens.a(json) from decompiled VK.
     * Expected format: { "utility_tokens": [ { "target_key": "...", "token": "..." }, ... ] }
     */
    internal fun parseUtilityTokens(root: JsonObject): UtilityTokens? {
        val utArray = root.getAsJsonArray("utility_tokens")
        if (utArray == null || utArray.isEmpty) return null
        val tokens = utArray.mapNotNull { elem ->
            val obj = elem.asJsonObject
            val key = obj.get("target_key")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            val token = obj.get("token")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            UtilityToken(targetKey = key, token = token)
        }
        return if (tokens.isEmpty()) null else UtilityTokens(tokens)
    }

    /**
     * Parse BanInfo from error response.
     * Mirrors BanInfo.a.a(JSONObject) from decompiled VK.
     */
    internal fun parseBanInfo(err: JsonObject): BanInfo? {
        val banInfo = err.getAsJsonObject("ban_info") ?: return null
        return BanInfo(
            banReason = banInfo.get("ban_reason")?.takeIf { !it.isJsonNull }?.asString,
            banDescription = banInfo.get("ban_description")?.takeIf { !it.isJsonNull }?.asString,
            banInfo = banInfo.get("ban_info")?.takeIf { !it.isJsonNull }?.asString,
            restoreAvailable = banInfo.get("restore_available")?.asBoolean ?: false,
            memberId = banInfo.get("member_id")?.takeIf { !it.isJsonNull }?.asLong,
            banType = banInfo.get("ban_type")?.takeIf { !it.isJsonNull }?.asInt,
        )
    }

    /**
     * Translate VKAuthException to AuthState.Error.
     * Mirrors AuthByExchangeToken.f() error classification.
     */
    internal fun authStateException(e: VKAuthException): AuthState.Error = when (e) {
        is BannedUserException -> AuthState.Error(AuthErrorKind.BANNED, e.message ?: "Аккаунт заблокирован")
        is DeactivatedUserException -> AuthState.Error(AuthErrorKind.DEACTIVATED, e.message ?: "Аккаунт деактивирован")
        is ExchangeTokenException -> AuthState.Error(AuthErrorKind.EXCHANGE_TOKEN_INVALID, e.message ?: "Токен обмена недействителен")
        is NeedSilentAuthException -> AuthState.Error(AuthErrorKind.UNKNOWN, e.message ?: "Требуется тихая авторизация")
        is AuthUnknownException -> AuthState.Error(AuthErrorKind.UNKNOWN, e.message ?: "Неизвестная ошибка авторизации")
    }

    /**
     * Parse ValidationType from string — handles VK's various casings.
     */
    internal fun parseValidationType(value: String): ValidationType = try {
        ValidationType.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        when (value.lowercase()) {
            "callreset", "call_reset" -> ValidationType.CALL_RESET
            "sms_inbox", "smsinbox" -> ValidationType.SMS_INBOX
            "tg", "telegram" -> ValidationType.TELEGRAM
            else -> ValidationType.UNKNOWN
        }
    }

    /**
     * Fix #144: детектит ответ VK web_token в формате
     *   {"type":"error","error_code":"","error_info":"wrong origin"}
     *
     * VK отвергает Origin header (мы шлём https://m.vk.ru, VK ожидает другой).
     * Это НЕ значит что remixsid мёртв — это значит что нужно попробовать
     * другой Origin (alternateTld: m.vk.ru → m.vk.com или наоборот).
     */
    internal fun isWrongOriginResponse(rawBody: String): Boolean {
        // Быстрая проверка без полного JSON-парса (для скорости в hot path).
        if (!rawBody.contains("wrong origin", ignoreCase = true)) return false
        // Дополнительная проверка: это именно error-ответ (а не вложенный текст).
        return rawBody.contains("\"type\"", ignoreCase = true) &&
               rawBody.contains("\"error_info\"", ignoreCase = true)
    }

    /**
     * Fix #144: детектит ответ VK web_token в формате
     *   {"type":"error","error_code":"","error_info":"unauthorized"}
     *
     * VK принимает Origin header, но отвергает remixsid cookie для этого
     * контекста. Это НЕ значит что remixsid мёртв — это значит что endpoint
     * требует дополнительные cookies/headers (например remixsid_stid), которых
     * у нас нет. Поэтому НЕ чистим remixsid (см. lastRemixsidContractFailure).
     */
    internal fun isUnauthorizedResponse(rawBody: String): Boolean {
        if (!rawBody.contains("unauthorized", ignoreCase = true)) return false
        return rawBody.contains("\"type\"", ignoreCase = true) &&
               rawBody.contains("\"error_info\"", ignoreCase = true)
    }

    /**
     * §44 + #NETWORK-RESILIENCE: alternate TLD для Origin header.
     * Если host заканчивается на .vk.com → возвращаем .vk.ru вариант (и наоборот).
     * Используется в silentRefreshViaRemixsid для multi-strategy Origin probing.
     *
     * Примеры:
     *   "m.vk.com" → "m.vk.ru"
     *   "m.vk.ru"  → "m.vk.com"
     *   "login.vk.com" → "login.vk.ru"
     *   "id.vk.ru" → "id.vk.com"
     *   "other.com" → null (не VK домен)
     */
    internal fun alternateTld(host: String): String? = when {
        host.endsWith(".vk.com") -> host.removeSuffix(".vk.com") + ".vk.ru"
        host.endsWith(".vk.ru")  -> host.removeSuffix(".vk.ru") + ".vk.com"
        else -> null
    }
}
