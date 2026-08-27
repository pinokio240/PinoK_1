package re.pinok.data.local

import re.pinok.auth.exchange.ExchangeTokenStorage

/**
 * Legacy access-token storage facade.
 *
 * Historically SOVA 2.0 stored only `access_token` + `user_id` here. With the
 * exchange_token flow introduced in [#15] the canonical store is
 * [ExchangeTokenStorage], which keeps the access_token alongside the
 * exchange_token, device_id, secret and LongPoll credentials.
 *
 * This class is retained as a thin facade so existing call sites in
 * [re.pinok.api.VKApiClient], [re.pinok.ui.MainActivity] and
 * [re.pinok.SovaApp] don't need a rewrite — they continue to read/write
 * through [ExchangeTokenStorage] via this shim.
 */
class TokenStorage(private val exchange: ExchangeTokenStorage) {

    /** Save a token obtained externally (e.g. legacy OAuth deep-link fallback). */
    fun save(token: Token) {
        val expiresInSec = if (token.expiresAt == 0L) 0L
                           else (token.expiresAt - System.currentTimeMillis()) / 1000
        exchange.updateAccessToken(
            accessToken = token.accessToken,
            expiresIn = expiresInSec,
            scope = token.scope,
            exchangeToken = null,
        )
        exchange.setUserId(token.userId)
    }

    fun load(): Token? {
        val at = exchange.accessToken() ?: return null
        val uid = exchange.userId()
        if (uid == 0L) return null
        return Token(
            accessToken = at,
            userId = uid,
            expiresAt = exchange.expiresAt(),
            scope = exchange.scope(),
        )
    }

    /**
     * User secret для sig-подписи (messages.*, audio.*).
     * Возвращается VK ТОЛЬКО при Direct Auth (grant_type=password).
     * OAuth WebView не возвращает secret — sig невозможен, error 15.
     *
     * См. [re.pinok.api.VkSigner.sign].
     */
    fun secret(): String? = exchange.secret()

    /** device_id — стабильный per-install UUID, отправляется на каждый запрос. */
    fun deviceId(): String = exchange.deviceId()

    /**
     * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): logout_hash из web_token JSON.
     * Используется в SecurityAlertsPoller для вызова accountPersonal.getSecurityAlerts
     * и в Path 5 (connect_exchange_token) для token refresh.
     */
    fun logoutHash(): String? = exchange.logoutHash()

    fun clear() = exchange.clear()

    /**
     * Fix #106: очистить только access_token, сохраняя remixsid/sat_token/exchange_token.
     *
     * Делегирует в [ExchangeTokenStorage.clearAccessToken]. Используется при
     * API error 5/1117, чтобы не убить возможность silent re-login через
     * сохранённый remixsid (Fix #107).
     */
    fun clearAccessToken() = exchange.clearAccessToken()

    fun hasValidToken(): Boolean = exchange.hasValidAccessToken()

    data class Token(
        val accessToken: String,
        val userId: Long,
        val expiresAt: Long,   // epoch millis; 0 = no expiry
        val scope: String? = null,
    )
}
