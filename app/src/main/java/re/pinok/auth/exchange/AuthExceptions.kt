package re.pinok.auth.exchange

/**
 * Auth exception hierarchy — fully cloned from decompiled VK 8.178.
 *
 * Source: com.vk.superapp.api.exceptions
 *   - AuthException (base, sealed)
 *     - BannedUserException    — user account is banned (BanInfo attached)
 *     - DeactivatedUserException — user account is deactivated
 *     - ExchangeTokenException — exchange_token is invalid/expired
 *     - NeedSilentAuthException — silent auth required
 *     - UnknownException      — catch-all
 *
 * In VK, AuthByExchangeToken.d() and .f() throw these based on:
 *   - VKWebAuthException.o() → ExchangeTokenException
 *   - VKWebAuthException.m() → DeactivatedUserException or parsed error
 *   - VKApiExecutionException with BanInfo → BannedUserException
 *   - Other → UnknownException
 *
 * Our ExchangeAuthRepository catches these and translates to AuthState.Error
 * for the UI, but they can also bubble up for programmatic use.
 */

sealed class VKAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * User account is banned.
 * Mirrors AuthException$BannedUserException.
 * VK throws this when VKApiExecutionException contains a BanInfo payload.
 */
class BannedUserException(
    val banInfo: BanInfo,
    message: String = "Аккаунт заблокирован",
    cause: Throwable? = null,
) : VKAuthException(message, cause)

/**
 * User account is deactivated.
 * Mirrors AuthException$DeactivatedUserException.
 * VK throws this when VKWebAuthException.m() is true.
 */
class DeactivatedUserException(
    val deactivatedAccessToken: String? = null,
    val utilityTokens: UtilityTokens? = null,
    message: String = "Аккаунт деактивирован",
    cause: Throwable? = null,
) : VKAuthException(message, cause)

/**
 * Exchange token is invalid or expired.
 * Mirrors AuthException$ExchangeTokenException.
 * VK throws this when VKWebAuthException.o() is true.
 * The app must re-authenticate from scratch (phone + password).
 */
class ExchangeTokenException(
    val authState: AuthStateInfo? = null,
    message: String = "Exchange token недействителен",
    cause: Throwable? = null,
) : VKAuthException(message, cause)

/**
 * Silent auth is required — the user must re-authorize silently.
 * Mirrors AuthException$NeedSilentAuthException.
 */
class NeedSilentAuthException(
    message: String = "Требуется тихая авторизация",
    cause: Throwable? = null,
) : VKAuthException(message, cause)

/**
 * Catch-all auth exception.
 * Mirrors AuthException$UnknownException.
 */
class AuthUnknownException(
    message: String = "Неизвестная ошибка авторизации",
    cause: Throwable? = null,
) : VKAuthException(message, cause)

/**
 * Auth state info — mirrors com.vk.superapp.core.api.models.a
 * (the decompiled inner model that holds the intermediate auth state
 * from the error response).
 * Used by ExchangeTokenException to carry the failed auth state for retry.
 */
data class AuthStateInfo(
    val accessToken: String? = null,
    val exchangeToken: String? = null,
    val userId: Long? = null,
    val expiresIn: Int = 0,
    val httpsRequired: Boolean = false,
    val trustedHash: String? = null,
    val validationType: ValidationType? = null,
    val validationSid: String? = null,
    val banInfo: BanInfo? = null,
)