package re.pinok.auth.exchange

import com.google.gson.annotations.SerializedName

/**
 * Data models — fully cloned from decompiled VK 8.178.
 *
 * Source classes:
 *   - com.vk.auth.api.models.AuthResult (20 fields, Parcelable)
 *   - com.vk.superapp.api.states.VkAuthState (grant_type builder)
 *   - com.vk.superapp.api.internal.oauthrequests.AuthByExchangeToken$Initiator (6 values)
 *   - com.vk.superapp.api.dto.auth.VkAuthCredentials (username + password)
 *   - com.vk.superapp.core.api.models.BanInfo
 *   - com.vk.superapp.core.api.models.ValidationType
 *   - com.vk.superapp.core.api.models.ValidateInfo
 *   - com.vk.superapp.core.api.models.SendOtpInfo
 *   - com.vk.api.sdk.auth.UtilityTokens / UtilityToken
 *
 * ⚠️ Два эндпоинта (HISTORY.md #9):
 *   1. oauth.vk.com/access_token — password, 2FA, trusted_hash, without_password,
 *      vk_external_auth. Требует client_secret.
 *   2. id.vk.com/auth_by_exchange_token — ТОЛЬКО exchange_token refresh.
 *      НЕ поддерживает grant_type=password.
 *
 * Дополнительно: Web Token авторизация (vk1.a.*) через VK web flow,
 * минуя oauth.vk.com/password и flood control.
 */
sealed interface AuthState {
    /** Idle / nothing attempted yet. */
    data object Idle : AuthState

    /** Currently performing network request. */
    data object Loading : AuthState

    /** Server demands 2FA via SMS / push / email / IVR. */
    data class NeedValidation(
        val validationType: ValidationType,
        val validationSid: String,
        val phoneMask: String?,
        val nextStepHint: String? = null,
        /** If server returned a list of allowed re-send methods. */
        val allowedWays: List<ValidationType> = emptyList(),
        /** OTP resend info — e.g. when the next SMS can be sent. */
        val sendOtpInfo: SendOtpInfo? = null,
    ) : AuthState

    /** User is banned / deactivated / needs sign up. */
    data class Error(val kind: AuthErrorKind, val message: String) : AuthState

    /** Success — access_token + exchange_token + user_id acquired. */
    data class Success(val result: AuthResult) : AuthState

    /**
     * #NETWORK-RESILIENCE (2026-08-04): Offline-first режим с кэшированной сессией.
     *
     * Срабатывает в [re.pinok.auth.AuthViewModel.tryAutoLogin] когда:
     *  - Сохранённый access_token протух (`expires_at < now`).
     *  - [re.pinok.util.NetworkObserver] сообщает `isOnline() == false`.
     *  - Silent refresh невозможен (нет сети → retry исчерпан).
     *
     * В этом состоянии пользователь НЕ выкидывается в AuthActivity. Вместо этого
     * приложение открывает главный экран с кэшированными данными (лента/чаты из
     * локальной БД) и баннером «Нет подключения к сети. Данные могут быть
     * устаревшими». Как только сеть появляется — фоновая задача [`ensureFreshToken`]
     * обновляет токен и UI автоматически переходит в [Success].
     *
     * @param cachedUserId ID пользователя из последней успешной сессии (для
     *     открытия главного экрана в «гостевом» режиме — лента, чаты, профиль).
     * @param lastSeenMs timestamp последнего успешного API-запроса (для
     *     отображения «Последнее обновление: 5 мин назад» в баннере).
     * @param tokenExpiredAt когда протух access_token (для приоритизации
     *     refresh при появлении сети — чем раньше протух, тем выше приоритет).
     */
    data class OfflineWithCache(
        val cachedUserId: Long,
        val lastSeenMs: Long,
        val tokenExpiredAt: Long,
    ) : AuthState
}

// ============================================================================
// Enums
// ============================================================================

/** Mirrors com.vk.superapp.core.api.models.ValidationType */
enum class ValidationType {
    @SerializedName("sms")        SMS,
    @SerializedName("push")       PUSH,
    @SerializedName("email")      EMAIL,
    @SerializedName("ivr")        IVR,
    @SerializedName("callreset")  CALL_RESET,
    @SerializedName("libverify")  LIBVERIFY,
    @SerializedName("sms_inbox")  SMS_INBOX,
    @SerializedName("tg")         TELEGRAM,
    @SerializedName("passkey")    PASSKEY,
    @SerializedName("messenger")  MESSENGER,
    @SerializedName("unknown")    UNKNOWN;
}

enum class AuthErrorKind {
    INVALID_CREDENTIALS,
    TOO_MANY_ATTEMPTS,
    TOO_MANY_REQUESTS,
    NEED_SIGNUP,
    BANNED,
    DEACTIVATED,
    NEED_RESTORE,
    EXCHANGE_TOKEN_INVALID,
    NETWORK,
    PARSE,
    UNKNOWN,
    /**
     * Fix #105: web_token из localStorage m.vk.ru истёк.
     *
     * Срабатывает в [ExchangeAuthRepository.saveWebTokenResult] как
     * последняя линия обороны перед сохранением мёртвого токена.
     * UI (AuthActivity) видит EXPIRED → НЕ завершается с RESULT_OK →
     * пользователь видит нормальный экран логина вместо зависания.
     */
    EXPIRED,
}

/**
 * Fix #230: результат getExchangeTokenDetailed.
 *
 * Различает 3 исхода:
 *  - [Success]: exchange_token получен. access_token валиден.
 *  - [TokenInvalid]: VK вернул error_code=5 (access_token invalid/expired,
 *    включая subcode=1130 "given to another ip address"). access_token мёртв,
 *    его НЕ нужно сохранять в storage — иначе получим вечный цикл err=5.
 *  - [Unavailable]: error_code=3 (Unknown method — для некоторых токенов),
 *    network error, parse error. access_token возможно работает, просто
 *    exchange_token недоступен. Токен можно сохранить.
 */
sealed interface ExchangeTokenResult {
    data class Success(val exchangeToken: String) : ExchangeTokenResult
    data object TokenInvalid : ExchangeTokenResult
    data object Unavailable : ExchangeTokenResult
}

// ============================================================================
// Auth result
// ============================================================================

/**
 * Mirrors com.vk.auth.api.models.AuthResult (20-field Parcelable).
 *
 * Fields from VK (obfuscated names → real names from toString()):
 *   b  = accessToken,      c  = secret,
 *   d  = userId (UserId),  e  = httpsRequired,
 *   f  = expiresIn,        g  = trustedHash,
 *   h  = authCredentials,  i  = webviewAccessToken,
 *   j  = webviewRefreshToken,
 *   k  = webviewExpired,   l  = authCookies,
 *   m  = webviewRefreshTokenExpired,
 *   n  = authPayload,      o  = authTarget,
 *   p  = personalData,     q  = createdMs,
 *   r  = metadata,         s  = utilityTokens,
 *   t  = phoneToActualize, u  = email,
 *   v  = silentToken,      w  = silentTokenUuid
 */
data class AuthResult(
    @SerializedName("access_token")   val accessToken: String,
    @SerializedName("exchange_token") val exchangeToken: String?,
    @SerializedName("user_id")        val userId: Long,
    @SerializedName("expires_in")     val expiresIn: Long,
    @SerializedName("scope")          val scope: String? = null,
    @SerializedName("secret")         val secret: String? = null,
    @SerializedName("trusted_hash")   val trustedHash: String? = null,
    val phone: String? = null,
    val email: String? = null,
    /** VK webview access_token (for web methods). */
    val webviewAccessToken: String? = null,
    /** VK webview refresh_token. */
    val webviewRefreshToken: String? = null,
    /** Webview token expiry timestamp. */
    val webviewExpiresIn: Int = 0,
    /** Utility tokens — exchange_token and silent_token for specific targets. */
    val utilityTokens: UtilityTokens? = null,
    /** Auth cookies for web requests. */
    val authCookies: List<String>? = null,
    /** Whether HTTPS is required for API calls. */
    val httpsRequired: Boolean = false,
    /** Phone that needs actualization after auth. */
    val phoneToActualize: String? = null,
    /**
     * Fix #213 (P0.2): silent_token — VKID SDK token для silent auth.
     *
     * 4-й уровень fallback в официальном VK клиенте (после access_token,
     * exchange_token, remixsid). Используется когда access_token истёк,
     * exchange_token мёртв, и remixsid невалиден — silent_token может
     * ещё работать (VKID выдаёт на ~1 год).
     *
     * Поле v в decompiled AuthResult. В auth_by_exchange_token ответе
     * приходит как "silent_token". В VKID SDK auth.js — как "silent_token"
     * в response_type=silent_token flow.
     */
    @SerializedName("silent_token")
    val silentToken: String? = null,
    /**
     * Fix #213 (P0.2): silent_token_uuid — UUID для silent auth через VKID SDK.
     *
     * Связан с silent_token — передаётся вместе с ним в VKID SDK endpoint
     * для получения свежего access_token. Поле w в decompiled AuthResult.
     */
    @SerializedName("silent_token_uuid")
    val silentTokenUuid: String? = null,
)

// ============================================================================
// VK Credentials (for re-login via trusted_hash)
// ============================================================================

/**
 * Mirrors com.vk.superapp.api.dto.auth.VkAuthCredentials.
 * Stored so the app can re-login via grant_type=trusted_hash without
 * re-prompting for username/password.
 */
data class VkAuthCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "VkAuthCredentials(username='$username', password='***')"
}

// ============================================================================
// BanInfo — from decompiled VK AuthException$BannedUserException
// ============================================================================

/**
 * Mirrors com.vk.superapp.core.api.models.BanInfo.
 * Parsed from the error response when VK returns a ban.
 */
data class BanInfo(
    @SerializedName("ban_reason")       val banReason: String? = null,
    @SerializedName("ban_description")  val banDescription: String? = null,
    @SerializedName("ban_info")         val banInfo: String? = null,
    @SerializedName("restore_available") val restoreAvailable: Boolean = false,
    @SerializedName("member_id")        val memberId: Long? = null,
    @SerializedName("ban_type")         val banType: Int? = null,
)

// ============================================================================
// ValidateInfo — from decompiled VK
// ============================================================================

/**
 * Mirrors com.vk.superapp.core.api.models.ValidateInfo.
 * Additional info returned when 2FA validation is required.
 */
data class ValidateInfo(
    @SerializedName("validation_type") val validationType: String? = null,
    @SerializedName("validation_sid")  val validationSid: String? = null,
    @SerializedName("phone_mask")       val phoneMask: String? = null,
    @SerializedName("next_step")        val nextStep: String? = null,
    /** Allowed validation methods for re-send. */
    @SerializedName("supported_ways")  val supportedWays: List<String>? = null,
)

// ============================================================================
// SendOtpInfo — from decompiled VK
// ============================================================================

/**
 * Mirrors com.vk.superapp.core.api.models.SendOtpInfo.
 * Info about when the next OTP can be sent (rate limiting).
 */
data class SendOtpInfo(
    @SerializedName("next_otp_at")    val nextOtpAt: Long? = null,
    @SerializedName("retry_after")    val retryAfter: Int? = null,
    @SerializedName("code_length")    val codeLength: Int? = null,
)

// ============================================================================
// UtilityTokens — from decompiled VK com.vk.api.sdk.auth
// ============================================================================

/**
 * Mirrors com.vk.api.sdk.auth.UtilityTokens.
 * Contains additional tokens for specific VK services (e.g. exchange_token
 * for a particular mini-app or superapp target).
 */
data class UtilityTokens(
    val tokens: List<UtilityToken> = emptyList(),
) {
    /** Find an exchange_token by target key. */
    fun exchangeTokenFor(targetKey: String): String? =
        tokens.firstOrNull { it.targetKey == targetKey }?.token

    /** All exchange tokens as a map. */
    fun toMap(): Map<String, String> =
        tokens.associate { it.targetKey to it.token }

    companion object {
        val EMPTY = UtilityTokens()
    }
}

/**
 * Mirrors com.vk.api.sdk.auth.UtilityToken.
 * A key-value pair: targetKey (e.g. "vkbridge") → token string.
 */
data class UtilityToken(
    /** The target service key (e.g. "vkbridge", "miniapp_12345"). */
    val targetKey: String,
    /** The token value. */
    val token: String,
)

// ============================================================================
// LongPoll credentials
// ============================================================================

/**
 * LongPoll credentials — obtained from `messages.getLongPollServer`
 * once we have a valid access_token with `messages` scope (scope=all guarantees this).
 */
data class LongPollCredentials(
    val key: String,
    val server: String,
    val ts: Long,
    val pts: Long? = null,
)