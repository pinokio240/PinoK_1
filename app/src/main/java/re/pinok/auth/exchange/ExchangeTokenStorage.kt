package re.pinok.auth.exchange

import android.content.SharedPreferences
import org.json.JSONObject
import re.pinok.util.AppLog
import java.util.UUID

/**
 * Encrypted storage — fully cloned from decompiled VK 8.178.
 *
 * Source: com.vk.api.sdk.internal.TokenManager (EncryptedSharedPreferences singleton)
 *         + com.vk.auth.api.models.AuthResult field persistence
 *
 * VK stores EVERYTHING needed to restore / refresh a session:
 *  - `access_token` + `user_id` + `expires_at` + `scope` — API calls
 *  - `exchange_token` — refresh access_token via auth_by_exchange_token
 *  - `device_id` — stable per-install UUID, sent on every request
 *  - `secret` — user secret for sig= signing
 *  - `trusted_hash` — for passwordless re-login (grant_type=trusted_hash)
 *  - `last_phone` + `last_password` — for VkAuthCredentials re-login
 *  - `webview_access_token` + `webview_refresh_token` — web methods
 *  - LongPoll `lp_key`, `lp_server`, `lp_ts`, `lp_pts`
 *  - `utility_tokens` — serialized JSON of UtilityTokens
 *
 * Backed by [EncryptedSharedPreferences] (see [re.pinok.SovaApp]).
 *
 * **Файловый бэкап (VTosters pattern #3).** Дополнительно к prefs, все
 * токены дублируются в plaintext JSON `<filesDir>/account.json` через
 * [fileBackup]. Если prefs повреждаются (KeyStore corruption, Tink key
 * rotation failure, factory reset), [restoreFromFileBackup] читает файл
 * и восстанавливает сессию без полного re-login. См. [AccountFileBackup].
 *
 * **Синхронные записи.** `saveAuthResult` и `updateAccessToken` (login
 * и refresh — самые критичные writes) используют `.commit()` вместо
 * `.apply()`, чтобы пережить kill процесса между refresh и flush.
 * Прошлый audit (Critical #1) уже отмечал риск async flush; теперь он
 * закрыт для критических путей. Остальные writes остаются async — они
 * либо менее критичны (LongPoll creds, deviceId), либо часты (keepAlive).
 */
class ExchangeTokenStorage(
    private val prefs: SharedPreferences,
    private val fileBackup: AccountFileBackup? = null,
) {

    // =====================================================================
    // Auth result persistence
    // =====================================================================

    /** Persist a successful AuthResult + the device_id used. */
    fun saveAuthResult(result: AuthResult, deviceId: String) {
        // .commit() (sync) — критическая запись (login). .apply() может
        // потеряться при kill процесса до flush, и пользователь потеряет
        // только что полученный токен. См. audit Critical #1.
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, result.accessToken)
            .putLong(KEY_USER_ID, result.userId)
            .putLong(KEY_EXPIRES_AT, expiresAt(result.expiresIn))
            .putString(KEY_SCOPE, result.scope)
            .putString(KEY_EXCHANGE_TOKEN, result.exchangeToken)
            .putString(KEY_SECRET, result.secret)
            .putString(KEY_TRUSTED_HASH, result.trustedHash)
            .putString(KEY_DEVICE_ID, deviceId)
            // Webview tokens — from AuthResult.i / AuthResult.j
            .putString(KEY_WEBVIEW_ACCESS_TOKEN, result.webviewAccessToken)
            .putString(KEY_WEBVIEW_REFRESH_TOKEN, result.webviewRefreshToken)
            .putInt(KEY_WEBVIEW_EXPIRES_IN, result.webviewExpiresIn)
            // Utility tokens — serialized JSON
            .putString(KEY_UTILITY_TOKENS, result.utilityTokens?.let { serializeUtilityTokens(it) })
            // Fix #213 (P0.2): silent_token + silent_token_uuid — 4-й fallback уровень.
            // Сохраняем только если пришли (nullable), иначе не затираем существующие.
            .apply {
                if (result.silentToken != null) putString(KEY_SILENT_TOKEN, result.silentToken)
                if (result.silentTokenUuid != null) putString(KEY_SILENT_TOKEN_UUID, result.silentTokenUuid)
                // §50 #TOKEN-LIFECYCLE-FIX: успешный логин — снимаем флаг
                // invalidated (если был от предыдущей сессии).
                clearInvalidatedFlag(this)
            }
            .commit()   // sync — login не должен потеряться
        dumpToFile()
    }

    /**
     * Update only access_token + expiry (+ optionally scope / exchangeToken).
     *
     * Семантика nullable-полей как у patch: null = «не трогать», значение = «обновить».
     * Ранее putString(KEY_SCOPE, null) молча удалял ключ — Path 5 (connect_exchange_token)
     * и Path 4 (oauth host 302) стёрли scope при refresh. Теперь null оставляет старое значение.
     * Чтобы явно очистить scope, вызывай storage.clearScope() (если когда-нибудь понадобится).
     */
    fun updateAccessToken(accessToken: String, expiresIn: Long, scope: String?, exchangeToken: String? = null) {
        // .commit() (sync) — refresh критичен. .apply() может потеряться
        // при kill процесса между refresh и flush → пользователь потеряет
        // свежий токен и снова получит error 5/1117 на следующем запросе.
        // audit Critical #1: ранее .apply() отсутствовал вовсе; теперь sync.
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putLong(KEY_EXPIRES_AT, expiresAt(expiresIn))
            if (scope != null) putString(KEY_SCOPE, scope)        // null = оставить текущий
            exchangeToken?.let { putString(KEY_EXCHANGE_TOKEN, it) } // null = оставить текущий
            // §50 #TOKEN-LIFECYCLE-FIX: свежий токен получен — снимаем флаг
            // invalidated (если был). Иначе hasValidAccessToken() продолжал бы
            // возвращать false несмотря на новый валидный токен в prefs.
            clearInvalidatedFlag(this)
        }.commit()  // sync — refresh не должен потеряться
        dumpToFile()
    }

    // =====================================================================
    // Credentials (for re-login via trusted_hash or password)
    // =====================================================================

    /**
     * Save username + password — mirrors VkAuthCredentials stored by VK.
     * VK stores these to enable grant_type=trusted_hash re-login.
     */
    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_LAST_PHONE, username)
            .putString(KEY_LAST_PASSWORD, password)
            .apply()
        dumpToFile()
    }

    fun credentials(): VkAuthCredentials? {
        val u = prefs.getString(KEY_LAST_PHONE, null) ?: return null
        val p = prefs.getString(KEY_LAST_PASSWORD, null) ?: return null
        if (u.isBlank() || p.isBlank()) return null
        return VkAuthCredentials(username = u, password = p)
    }

    // =====================================================================
    // Phone
    // =====================================================================

    fun saveLastPhone(phone: String) {
        prefs.edit().putString(KEY_LAST_PHONE, phone).apply()
        dumpToFile()
    }

    fun lastPhone(): String? = prefs.getString(KEY_LAST_PHONE, null)

    // =====================================================================
    // Device ID
    // =====================================================================

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    // =====================================================================
    // Token accessors
    // =====================================================================

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun userId(): Long = prefs.getLong(KEY_USER_ID, 0L)
    fun exchangeToken(): String? = prefs.getString(KEY_EXCHANGE_TOKEN, null)
    fun secret(): String? = prefs.getString(KEY_SECRET, null)
    fun scope(): String? = prefs.getString(KEY_SCOPE, null)
    fun trustedHash(): String? = prefs.getString(KEY_TRUSTED_HASH, null)?.takeIf { it.isNotBlank() }
    fun expiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    // Webview tokens
    fun webviewAccessToken(): String? = prefs.getString(KEY_WEBVIEW_ACCESS_TOKEN, null)
    fun webviewRefreshToken(): String? = prefs.getString(KEY_WEBVIEW_REFRESH_TOKEN, null)
    fun webviewExpiresIn(): Int = prefs.getInt(KEY_WEBVIEW_EXPIRES_IN, 0)

    // Utility tokens
    fun utilityTokens(): UtilityTokens? {
        val raw = prefs.getString(KEY_UTILITY_TOKENS, null) ?: return null
        return deserializeUtilityTokens(raw)
    }

    /**
     * Fix #213 (P0.2): silent_token — VKID SDK token для 4-го fallback уровня.
     * Сохраняется при auth_by_exchange_token / Direct Auth (если VK вернул).
     * Используется в ensureFreshToken Path 4 когда remixsid+exchange_token невалидны.
     */
    fun silentToken(): String? = prefs.getString(KEY_SILENT_TOKEN, null)?.takeIf { it.isNotBlank() }

    /**
     * Fix #213 (P0.2): silent_token_uuid — связан с silent_token.
     * Передаётся вместе с silent_token в VKID SDK endpoint для fresh access_token.
     */
    fun silentTokenUuid(): String? = prefs.getString(KEY_SILENT_TOKEN_UUID, null)?.takeIf { it.isNotBlank() }

    /**
     * Fix #213 (P0.2): сохранить silent_token + silent_token_uuid отдельно.
     * Используется когда silent_token получен не через saveAuthResult (например,
     * из VKID SDK auth.js ответа, или из web flow).
     */
    fun saveSilentToken(silentToken: String?, silentTokenUuid: String?) {
        prefs.edit().apply {
            if (silentToken != null) putString(KEY_SILENT_TOKEN, silentToken)
            if (silentTokenUuid != null) putString(KEY_SILENT_TOKEN_UUID, silentTokenUuid)
        }.apply()
        dumpToFile()
    }

    /**
     * Fix #215 (P0.4) / #SESSION-COOKIES (2026-08-04): сохранить ТОЛЬКО session
     * cookies (remixsid + p + remixnsid), не трогая остальные поля.
     *
     * Используется в backfillSessionCookiesFromCookieManager после external browser
     * / OAuth WebView логина. CookieManager сохраняет ВСЕ session cookies VK —
     * мы читаем три ключевых:
     *   - remixsid (1_xxx, .vk.ru) — классическая сессия
     *   - remixnsid (vk1.a.xxx, vk.ru) — новая VK ID сессия
     *   - p (vk1.a.xxx, .login.vk.ru) — persistent login, восстанавливает сессию
     *     после смены IP. Без него silentRefreshViaRemixsid падает на network switch.
     *
     * Любой из параметров может быть null — сохраняются только ненулевые
     * (patch-семантика, как в updateAccessToken).
     *
     * НЕ перезаписывает access_token, exchange_token, expires_at, trusted_hash —
     * только добавляет session cookies. Это безопасно: даже если cookies невалидны,
     * ensureFreshToken Path 1.5 просто упадёт и перейдёт к Path 2.5/3.
     */
    fun saveSessionCookiesOnly(
        remixsid: String? = null,
        p: String? = null,
        remixnsid: String? = null,
        // §55 #SSO-FULL-COOKIE-SET: 6 дополнительных кук из браузерного дампа.
        httoken: String? = null,
        remixnttpid: String? = null,
        remixuacck: String? = null,
        remixuas: String? = null,
        remixdmgr: String? = null,
        remixmvkFp: String? = null,
        // #CALLS-ANTIFRAUD (2026-08-23): антифрод-токены.
        remixstid: String? = null,
        remixstlid: String? = null,
    ) {
        val hasAny = !remixsid.isNullOrBlank() || !p.isNullOrBlank() || !remixnsid.isNullOrBlank() ||
            !httoken.isNullOrBlank() || !remixnttpid.isNullOrBlank() ||
            !remixuacck.isNullOrBlank() || !remixuas.isNullOrBlank() ||
            !remixdmgr.isNullOrBlank() || !remixmvkFp.isNullOrBlank() ||
            !remixstid.isNullOrBlank() || !remixstlid.isNullOrBlank()
        if (!hasAny) return
        prefs.edit().apply {
            if (!remixsid.isNullOrBlank()) putString(KEY_REMIXSID, remixsid)
            if (!p.isNullOrBlank()) putString(KEY_P_COOKIE, p)
            if (!remixnsid.isNullOrBlank()) putString(KEY_REMIXNSID, remixnsid)
            // §55: полный cookie-set для silentRefreshViaRemixsid.
            if (!httoken.isNullOrBlank()) putString(KEY_HTTP_TOKEN, httoken)
            if (!remixnttpid.isNullOrBlank()) putString(KEY_REMIX_NTTPID, remixnttpid)
            if (!remixuacck.isNullOrBlank()) putString(KEY_REMIX_UACCK, remixuacck)
            if (!remixuas.isNullOrBlank()) putString(KEY_REMIX_UAS, remixuas)
            if (!remixdmgr.isNullOrBlank()) putString(KEY_REMIX_DMGR, remixdmgr)
            if (!remixmvkFp.isNullOrBlank()) putString(KEY_REMIX_MVK_FP, remixmvkFp)
            // #CALLS-ANTIFRAUD: антифрод-токены.
            if (!remixstid.isNullOrBlank()) putString(KEY_REMIX_STID, remixstid)
            if (!remixstlid.isNullOrBlank()) putString(KEY_REMIX_STLID, remixstlid)
        }.apply()
        dumpToFile()
    }

    /** Backwards-compat: сохраняет только remixsid. */
    fun saveRemixsidOnly(remixsid: String) {
        if (remixsid.isBlank()) return
        prefs.edit().putString(KEY_REMIXSID, remixsid).apply()
        dumpToFile()
    }

    /**
     * Fix #49 #DEAD-REMIXSID / #SESSION-COOKIES: очистить ВСЕ session cookies
     * (remixsid + p + remixnsid), не трогая access_token, exchange_token,
     * trusted_hash, sat_token.
     *
     * Вызывается из [ExchangeAuthRepository.ensureFreshToken] когда
     * silentRefreshViaRemixsid доказал что VK отверг куки на ВСЕХ стратегиях
     * Origin (wrong origin / unauthorized). Мёртвые cookies не дают
     * silent-режиму AuthActivity перелогиниться — приложение застревает в
     * бесконечном SILENT-цикле. Чистка заставляет следующий запуск AuthActivity
     * пойти в FULL (видимом) режиме.
     *
     * Чистим все 3 cookies вместе: если remixsid мёртв, то p/remixnsid от той же
     * сессии тоже невалидны (VK инвалидирует session целиком).
     *
     * В отличие от [clear] / [clearAccessToken], НЕ трогает другие поля —
     * exchange_token/trusted_hash могут ещё жить и дать silent re-login
     * через Path 2.5/3 без полного ручного ввода.
     */
    fun clearRemixsid() {
        var changed = false
        prefs.edit().apply {
            if (prefs.contains(KEY_REMIXSID)) { remove(KEY_REMIXSID); changed = true }
            if (prefs.contains(KEY_P_COOKIE)) { remove(KEY_P_COOKIE); changed = true }
            if (prefs.contains(KEY_REMIXNSID)) { remove(KEY_REMIXNSID); changed = true }
            // §55 #SSO-FULL-COOKIE-SET: чистим весь cookie-set вместе (VK
            // инвалидирует сессию целиком — если remixsid мёртв, остальные тоже).
            if (prefs.contains(KEY_HTTP_TOKEN)) { remove(KEY_HTTP_TOKEN); changed = true }
            if (prefs.contains(KEY_REMIX_NTTPID)) { remove(KEY_REMIX_NTTPID); changed = true }
            if (prefs.contains(KEY_REMIX_UACCK)) { remove(KEY_REMIX_UACCK); changed = true }
            if (prefs.contains(KEY_REMIX_UAS)) { remove(KEY_REMIX_UAS); changed = true }
            if (prefs.contains(KEY_REMIX_DMGR)) { remove(KEY_REMIX_DMGR); changed = true }
            if (prefs.contains(KEY_REMIX_MVK_FP)) { remove(KEY_REMIX_MVK_FP); changed = true }
        }.apply()
        if (changed) dumpToFile()
    }

    /** Patch user_id without touching the rest of the auth state. */
    fun setUserId(userId: Long) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply()
        dumpToFile()
    }

    fun hasValidAccessToken(): Boolean {
        // §50 #TOKEN-LIFECYCLE-FIX: проверяем флаг invalidated ПЕРВЫМ.
        // clearAccessToken() больше не удаляет access_token физически (чтобы
        // Path 5 connect_exchange_token мог его использовать), а ставит флаг.
        // Без этой проверки hasValidAccessToken() возвращал бы true даже после
        // clearAccessToken() — access_token же в prefs есть (просто протухший).
        if (prefs.getBoolean(KEY_ACCESS_TOKEN_INVALIDATED, false)) {
            return false
        }
        val at = accessToken() ?: return false
        if (at.isBlank()) return false
        val exp = expiresAt()
        return exp == 0L || exp > System.currentTimeMillis()
    }

    /**
     * §50 #TOKEN-LIFECYCLE-FIX: публичный accessor для флага invalidated.
     *
     * Возвращает true если access_token был явно отвергнут VK (err 5/1117) и
     * помечен через [clearAccessToken]. Токен при этом ФИЗИЧЕСКИ сохранён в
     * prefs (и в account.json бэкапе) — для Path 5 (connect_exchange_token).
     *
     * Используется [ExchangeAuthRepository.ensureFreshToken] для динамического
     * переупорядочивания путей refresh (§51 #AUTH-PATH-REORDER):
     *  - invalidated=true  → Path 5 ПЕРВЫМ (logout_hash переживает invalidation,
     *                        один быстрый HTTP-вызов ~200мс)
     *  - invalidated=false → Path 1.5 ПЕРВЫМ (remixsid скорее жив, классический
     *                        silent refresh через 7 origin-стратегий)
     *
     * Различие с [hasValidAccessToken]: эта функция НЕ проверяет timestamp/
     * presence — только сам флаг. Нужно чтобы отличить «токен протух по времени»
     * (invalidated=false, Path 1.5 приоритетнее) от «токен отвергнут VK»
     * (invalidated=true, Path 5 приоритетнее).
     */
    fun isAccessTokenInvalidated(): Boolean =
        prefs.getBoolean(KEY_ACCESS_TOKEN_INVALIDATED, false)

    // =====================================================================
    // LongPoll credentials
    // =====================================================================

    fun saveLongPoll(creds: LongPollCredentials) {
        prefs.edit()
            .putString(KEY_LP_KEY, creds.key)
            .putString(KEY_LP_SERVER, creds.server)
            .putLong(KEY_LP_TS, creds.ts)
            .putLong(KEY_LP_PTS, creds.pts ?: -1L)
            .apply()
        dumpToFile()
    }

    fun longPoll(): LongPollCredentials? {
        val k = prefs.getString(KEY_LP_KEY, null) ?: return null
        val s = prefs.getString(KEY_LP_SERVER, null) ?: return null
        val t = prefs.getLong(KEY_LP_TS, 0L)
        if (k.isBlank() || s.isBlank() || t == 0L) return null
        val pts = prefs.getLong(KEY_LP_PTS, -1L).takeIf { it >= 0 }
        return LongPollCredentials(key = k, server = s, ts = t, pts = pts)
    }

    fun clearLongPoll() {
        prefs.edit()
            .remove(KEY_LP_KEY)
            .remove(KEY_LP_SERVER)
            .remove(KEY_LP_TS)
            .remove(KEY_LP_PTS)
            .apply()
    }

    // =====================================================================
    // Web Token Exchange (WebTokenAuth) — login.vk.com flow
    // =====================================================================

    /**
     * #41: Сохраняет результат WebTokenAuth.fullAuthFlow().
     *
     * - access_token + user_id + expires_at — через стандартные поля.
     * - sat_token — для LongPoll/Queue (queuev4.vk.com).
     * - logout_hash — для future logout.
     * - remixsid — для синхронизации WebView-экранов (m.vk.com, audio, video).
     *
     * НЕ затирает exchange_token если он уже есть (от Direct Auth).
     * scope = "all" — web_token имеет полный web-scope (биты 52+53+54).
     */
    fun saveWebTokenResult(
        accessToken: String,
        userId: Long,
        expiresAt: Long,
        satToken: String?,
        logoutHash: String?,
        remixsid: String?,
        pCookie: String? = null,
        remixnsid: String? = null,
        // §55 #SSO-FULL-COOKIE-SET: 6 кук браузерного набора (опционально —
        // WebTokenAuth.fullAuthFlow может их не иметь, если пришёл только web_token).
        httoken: String? = null,
        remixnttpid: String? = null,
        remixuacck: String? = null,
        remixuas: String? = null,
        remixdmgr: String? = null,
        remixmvkFp: String? = null,
    ) {
        // .commit() (sync) — это login-эквивалентная запись (web_token flow).
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_SCOPE, "all")
            .apply {
                if (satToken != null) putString(KEY_SAT_TOKEN, satToken)
                if (logoutHash != null) putString(KEY_LOGOUT_HASH, logoutHash)
                if (remixsid != null) putString(KEY_REMIXSID, remixsid)
                // #SESSION-COOKIES: p + remixnsid для cross-IP silent refresh.
                if (pCookie != null) putString(KEY_P_COOKIE, pCookie)
                if (remixnsid != null) putString(KEY_REMIXNSID, remixnsid)
                // §55 #SSO-FULL-COOKIE-SET: полный cookie-set как браузер.
                if (httoken != null) putString(KEY_HTTP_TOKEN, httoken)
                if (remixnttpid != null) putString(KEY_REMIX_NTTPID, remixnttpid)
                if (remixuacck != null) putString(KEY_REMIX_UACCK, remixuacck)
                if (remixuas != null) putString(KEY_REMIX_UAS, remixuas)
                if (remixdmgr != null) putString(KEY_REMIX_DMGR, remixdmgr)
                if (remixmvkFp != null) putString(KEY_REMIX_MVK_FP, remixmvkFp)
                // §50 #TOKEN-LIFECYCLE-FIX: успешный web_token логин — снимаем
                // флаг invalidated (если был от предыдущей сессии).
                clearInvalidatedFlag(this)
            }
            .commit()   // sync — web_token login не должен потеряться
        dumpToFile()
    }

    /** SAT токен для LongPoll/Queue (из WebTokenAuth step 3). */
    fun satToken(): String? = prefs.getString(KEY_SAT_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** remixsid cookie — для синхронизации WebView-экранов. */
    fun remixsid(): String? = prefs.getString(KEY_REMIXSID, null)?.takeIf { it.isNotBlank() }

    /** #SESSION-COOKIES: remixnsid cookie — новая VK ID сессия (vk1.a.*). */
    fun remixnsid(): String? = prefs.getString(KEY_REMIXNSID, null)?.takeIf { it.isNotBlank() }

    /** #SESSION-COOKIES: p cookie — persistent login (.login.vk.ru), восстанавливает
     *  сессию после смены IP. Без него silentRefreshViaRemixsid падает на network switch. */
    fun pCookie(): String? = prefs.getString(KEY_P_COOKIE, null)?.takeIf { it.isNotBlank() }

    // §55 #SSO-FULL-COOKIE-SET: getters для 6 дополнительных кук браузерного набора.
    /** httoken — anti-CSRF (.vk.ru + .web.api.vk.ru). VK требует для state-changing запросов. */
    fun httoken(): String? = prefs.getString(KEY_HTTP_TOKEN, null)?.takeIf { it.isNotBlank() }
    /** remixnttpid — новая VK ID сессия (vk1.a.*, .vk.ru). */
    fun remixnttpid(): String? = prefs.getString(KEY_REMIX_NTTPID, null)?.takeIf { it.isNotBlank() }
    /** remixuacck — user access check key (.vk.ru). */
    fun remixuacck(): String? = prefs.getString(KEY_REMIX_UACCK, null)?.takeIf { it.isNotBlank() }
    /** remixuas — user auth signature, base64 (.vk.ru). */
    fun remixuas(): String? = prefs.getString(KEY_REMIX_UAS, null)?.takeIf { it.isNotBlank() }
    /** remixdmgr — device manager hash, anti-fraud (.vk.ru). */
    fun remixdmgr(): String? = prefs.getString(KEY_REMIX_DMGR, null)?.takeIf { it.isNotBlank() }
    /** remixmvk-fp — mobile VK fingerprint (.vk.ru). */
    fun remixmvkFp(): String? = prefs.getString(KEY_REMIX_MVK_FP, null)?.takeIf { it.isNotBlank() }
    // #CALLS-ANTIFRAUD (2026-08-23): антифрод-токены из браузерного cookie-set.
    /** remixstid — сессионный антифрод-токен (.vk.ru). */
    fun remixstid(): String? = prefs.getString(KEY_REMIX_STID, null)?.takeIf { it.isNotBlank() }
    /** remixstlid — long-lived антифрод-токен (.vk.ru). */
    fun remixstlid(): String? = prefs.getString(KEY_REMIX_STLID, null)?.takeIf { it.isNotBlank() }

    /** logout_hash из web_token response. */
    fun logoutHash(): String? = prefs.getString(KEY_LOGOUT_HASH, null)

    /** Clear all auth-related data (logout). */
    fun clear() {
        val did = prefs.getString(KEY_DEVICE_ID, null)
        prefs.edit().clear().commit()  // sync — logout должен зафиксироваться
        if (did != null) {
            prefs.edit().putString(KEY_DEVICE_ID, did).apply()
        }
        fileBackup?.clear()
    }

    /**
     * Fix #106: очистить ТОЛЬКО access_token + expires_at + scope.
     *
     * Используется при API error 5 (token invalid) / 1117 (token expired),
     * когда refresh через exchange_token не помог. Старый `clear()` удалял
     * ВСЁ (включая remixsid, sat_token, exchange_token), что убивало
     * возможность авто-восстановления через WebView re-login — приложение
     * могло только показать экран логина, даже если сессия VK жива.
     *
     * Fix #106: remixsid/sat_token/exchange_token СОХРАНЯЕМ — AuthActivity сможет
     * автоматически переобменять remixsid на свежий web_token без ручного
     * ввода пароля (Fix #107 — silent re-login).
     *
     * §50 #TOKEN-LIFECYCLE-FIX (Fix "токен умирает навсегда", 2026-08-05):
     * Раньше clearAccessToken() делал remove(KEY_ACCESS_TOKEN) + dumpToFile() —
     * бэкап account.json сразу перезаписывался БЕЗ access_token. При следующем
     * старте Path 5 (tryConnectExchangeToken) не мог работать: VK требует
     * access_token (даже протухший) + logout_hash для connect_exchange_token.
     * Без access_token → Path 5 возвращает null → silent refresh падает →
     * AuthActivity SILENT loop → "токен умирает" пользовательский симптом.
     *
     * ФИКС: НЕ удаляем access_token физически. Ставим флаг
     * KEY_ACCESS_TOKEN_INVALIDATED=true + expires_at=0 (текущее время минус 1с).
     * hasValidAccessToken() проверяет флаг → false → AuthActivity запускается.
     * accessToken() ВСЁ ЕЩЁ возвращает токен → Path 5 получает протухший
     * access_token + logout_hash → connect_exchange_token → свежий токен
     * за 1-2 сек. Бэкап account.json СОХРАНЯЕТ протухший токен — при
     * следующем старте Path 5 снова работает.
     *
     * exchange_token оставляем — refresh через него уже не сработал
     * (иначе мы бы не дошли до clearAccessToken), но он может пригодиться
     * для будущего retry, либо VK может его освежить при re-login.
     */
    fun clearAccessToken() {
        val currentToken = accessToken()
        prefs.edit().apply {
            if (currentToken != null) {
                // §50 #TOKEN-LIFECYCLE-FIX: НЕ remove(KEY_ACCESS_TOKEN) —
                // сохраняем протухший токен для Path 5 connect_exchange_token.
                // VK принимает истёкший access_token если logout_hash валиден.
                putBoolean(KEY_ACCESS_TOKEN_INVALIDATED, true)
                // expires_at = now-1000 — гарантированно в прошлом, чтобы
                // hasValidAccessToken() возвращал false даже без проверки флага
                // (defensive — флаг и так это делает, но expires_at в прошлом
                // делает логику self-contained и читаемой в дампах).
                putLong(KEY_EXPIRES_AT, System.currentTimeMillis() - 1000L)
            } else {
                // Нет токена вообще — чистим как раньше (нечего сохранять).
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_EXPIRES_AT)
                remove(KEY_ACCESS_TOKEN_INVALIDATED)
            }
            remove(KEY_SCOPE)
        }.apply()
        // Обновляем файловый бэкап: access_token + invalidated flag сохранены
        // (для Path 5), exchange_token/remixsid/sat_token остаются.
        dumpToFile()
    }

    /**
     * §50 #TOKEN-LIFECYCLE-FIX: снять флаг invalidated после успешного refresh.
     *
     * Вызывается из [updateAccessToken] / [saveAuthResult] / [saveWebTokenResult]
     * — после получения свежего токена флаг invalidated сбрасывается, иначе
     * hasValidAccessToken() продолжал бы возвращать false несмотря на новый токен.
     */
    private fun clearInvalidatedFlag(editor: SharedPreferences.Editor) {
        if (prefs.contains(KEY_ACCESS_TOKEN_INVALIDATED)) {
            editor.remove(KEY_ACCESS_TOKEN_INVALIDATED)
        }
    }

    // =====================================================================
    // Utility token serialization
    // =====================================================================

    private fun serializeUtilityTokens(ut: UtilityTokens): String {
        return ut.tokens.joinToString(";") { "${it.targetKey}=${it.token}" }
    }

    private fun deserializeUtilityTokens(raw: String): UtilityTokens? {
        if (raw.isBlank()) return null
        val tokens = raw.split(";").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val token = pair.substring(idx + 1)
            if (key.isBlank() || token.isBlank()) return@mapNotNull null
            UtilityToken(targetKey = key, token = token)
        }
        return if (tokens.isEmpty()) null else UtilityTokens(tokens)
    }

    // =====================================================================
    // File backup (VTosters pattern #3 — account.json)
    // =====================================================================

    /**
     * Сериализовать все поля аккаунта в JSON и записать в
     * `<filesDir>/account.json`. Вызывается после каждого успешного write
     * (save*, update*, set*). Best-effort: сбой НЕ ломает основной flow.
     *
     * Формат — плоский JSON object с ключами = prefs keys. Совпадает с
     * форматом VTosters `account.toJSONObject().toString()`.
     */
    private fun dumpToFile() {
        val backup = fileBackup ?: return
        try {
            val json = JSONObject().apply {
                // Core auth
                put(KEY_ACCESS_TOKEN, accessToken())
                // §50 #TOKEN-LIFECYCLE-FIX: сохраняем флаг invalidated в бэкап.
                // При restoreFromFileBackup он снова применится — hasValidAccessToken()
                // вернёт false, но accessToken() вернёт протухший токен для Path 5.
                put(KEY_ACCESS_TOKEN_INVALIDATED, prefs.getBoolean(KEY_ACCESS_TOKEN_INVALIDATED, false))
                put(KEY_USER_ID, userId())
                put(KEY_EXPIRES_AT, expiresAt())
                putOpt(KEY_SCOPE, scope())
                putOpt(KEY_EXCHANGE_TOKEN, exchangeToken())
                putOpt(KEY_SECRET, secret())
                putOpt(KEY_TRUSTED_HASH, trustedHash())
                putOpt(KEY_DEVICE_ID, prefs.getString(KEY_DEVICE_ID, null))

                // Credentials
                putOpt(KEY_LAST_PHONE, prefs.getString(KEY_LAST_PHONE, null))
                putOpt(KEY_LAST_PASSWORD, prefs.getString(KEY_LAST_PASSWORD, null))

                // Webview tokens
                putOpt(KEY_WEBVIEW_ACCESS_TOKEN, prefs.getString(KEY_WEBVIEW_ACCESS_TOKEN, null))
                putOpt(KEY_WEBVIEW_REFRESH_TOKEN, prefs.getString(KEY_WEBVIEW_REFRESH_TOKEN, null))
                put(KEY_WEBVIEW_EXPIRES_IN, prefs.getInt(KEY_WEBVIEW_EXPIRES_IN, 0))

                // Utility tokens (raw serialized string)
                putOpt(KEY_UTILITY_TOKENS, prefs.getString(KEY_UTILITY_TOKENS, null))

                // silent_token (VKID SDK)
                putOpt(KEY_SILENT_TOKEN, prefs.getString(KEY_SILENT_TOKEN, null))
                putOpt(KEY_SILENT_TOKEN_UUID, prefs.getString(KEY_SILENT_TOKEN_UUID, null))

                // LongPoll
                putOpt(KEY_LP_KEY, prefs.getString(KEY_LP_KEY, null))
                putOpt(KEY_LP_SERVER, prefs.getString(KEY_LP_SERVER, null))
                put(KEY_LP_TS, prefs.getLong(KEY_LP_TS, 0L))
                put(KEY_LP_PTS, prefs.getLong(KEY_LP_PTS, -1L))

                // Web Token Exchange
                putOpt(KEY_SAT_TOKEN, prefs.getString(KEY_SAT_TOKEN, null))
                putOpt(KEY_REMIXSID, prefs.getString(KEY_REMIXSID, null))
                putOpt(KEY_REMIXNSID, prefs.getString(KEY_REMIXNSID, null))
                putOpt(KEY_P_COOKIE, prefs.getString(KEY_P_COOKIE, null))
                putOpt(KEY_LOGOUT_HASH, prefs.getString(KEY_LOGOUT_HASH, null))
                // §55 #SSO-FULL-COOKIE-SET: 6 кук браузерного набора в бэкапе.
                putOpt(KEY_HTTP_TOKEN, prefs.getString(KEY_HTTP_TOKEN, null))
                putOpt(KEY_REMIX_NTTPID, prefs.getString(KEY_REMIX_NTTPID, null))
                putOpt(KEY_REMIX_UACCK, prefs.getString(KEY_REMIX_UACCK, null))
                putOpt(KEY_REMIX_UAS, prefs.getString(KEY_REMIX_UAS, null))
                putOpt(KEY_REMIX_DMGR, prefs.getString(KEY_REMIX_DMGR, null))
                putOpt(KEY_REMIX_MVK_FP, prefs.getString(KEY_REMIX_MVK_FP, null))
                // #CALLS-ANTIFRAUD: антифрод-токены в бэкапе.
                putOpt(KEY_REMIX_STID, prefs.getString(KEY_REMIX_STID, null))
                putOpt(KEY_REMIX_STLID, prefs.getString(KEY_REMIX_STLID, null))

                // Метка восстановления для диагностики
                put("__backup_at", System.currentTimeMillis())
                put("__backup_version", 1)
            }
            backup.save(json)
        } catch (e: Exception) {
            AppLog.w("ExchangeTokenStorage", "dumpToFile failed: ${e.message}")
        }
    }

    /**
     * Восстановить аккаунт из файлового бэкапа `account.json`.
     *
     * Вызывается из [re.pinok.SovaApp.onCreate] после инициализации
     * `EncryptedSharedPreferences`, если `accessToken()` вернул null
     * (т.е. prefs пусты или повреждены). Если файл существует и содержит
     * access_token, поля заливаются обратно в prefs (sync `.commit()`).
     *
     * @return `true` если восстановление прошло и access_token валиден.
     */
    fun restoreFromFileBackup(): Boolean {
        // Fix #177+#178 #RESTORE-ALL-FIELDS (2026-08-04, лог 17:33:09.271):
        // Раньше если access_token в бэкапе пустой — ранний `return false`
        // пропускал ВСЕ остальные поля (remixsid, exchange_token, trusted_hash,
        // last_phone, device_id, webview tokens). Сценарий: securePrefs пусты,
        // в account.json нет access_token (dumped когда токен уже был очищен),
        // но есть remixsid=88 → restoreFromFileBackup=false → silentRefresh
        // читает remixsid из CookieManager вместо storage → выглядит как
        // "remixsid найден! длина=88", но exchange_token/trusted_hash УТрачены
        // → Path 2.5 (trusted_hash) skipped → AuthActivity SILENT loop 60 сек.
        //
        // Фикс: разделить чтение бэкапа на два шага. Сначала восстанавливаем
        // ВСЕ re-login credentials (независимо от access_token), потом если
        // access_token валиден — заливаем и его. Возвращаем true ТОЛЬКО если
        // access_token восстановлен и не протух (hasValidAccessToken=true),
        // но поля credentials в любом случае залиты — silent paths получают
        // шанс отработать без полного re-login.
        val backup = fileBackup ?: run {
            AppLog.w("ExchangeTokenStorage", "restoreFromFileBackup: no fileBackup configured — skip")
            return false
        }
        val json = backup.load() ?: run {
            AppLog.w("ExchangeTokenStorage", "restoreFromFileBackup: backup.load() returned null — file missing or unreadable")
            return false
        }
        return try {
            val at = json.optString(KEY_ACCESS_TOKEN, "").takeIf { it.isNotBlank() }
            if (at == null) {
                AppLog.w("ExchangeTokenStorage",
                    "restoreFromFileBackup: access_token absent in backup — restoring re-login " +
                    "credentials only (remixsid present=${!json.optString(KEY_REMIXSID, "").isBlank()}, " +
                    "exchange_token present=${!json.optString(KEY_EXCHANGE_TOKEN, "").isBlank()}, " +
                    "trusted_hash present=${!json.optString(KEY_TRUSTED_HASH, "").isBlank()})")
            }

            // Fix #176-auth-loop: НЕ восстанавливаем протухший access_token из бэкапа.
            // Сценарий из лога 2026-08-04 12:35:49: процесс стартовал → keepAlive
            // видит expires_at = now-12s → запускает refresh → Path 0 читает бэкап,
            // восстанавливает ТОТ ЖЕ протухший токен в prefs → hasValidAccessToken()
            // возвращает false → падает в Path 1.5/2.5/3 → все silent paths фейлятся
            // (remixsid contract failure / no exchange_token) → re-login required →
            // AuthActivity SILENT loop по tick 1,2,3,4,5...
            //
            // Фикс: проверяем expires_at из бэкапа ДО записи в prefs. Если протух —
            // восстанавливаем ТОЛЬКО re-login credentials (remixsid, trusted_hash,
            // last_phone, exchange_token, device_id, webview tokens, sat_token),
            // но НЕ access_token. Это даёт Path 1.5/2.5/3 шанс работать, без засорения
            // prefs мёртвым токеном. Возврат false корректен — hasValidAccessToken()
            // после restore будет false (access_token не восстановлен).
            val backupExpiresAt = if (json.has(KEY_EXPIRES_AT)) json.getLong(KEY_EXPIRES_AT) else 0L
            val now = System.currentTimeMillis()
            val tokenExpired = backupExpiresAt != 0L && backupExpiresAt <= now
            if (tokenExpired) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                // §49 #KEEP-EXPIRED-FOR-CONNECT-EXCHANGE (Fix auth-loop 2026-08-04):
                // НЕ удаляем протухший access_token из prefs! Path 5
                // (tryConnectExchangeToken в ExchangeAuthRepository) принимает
                // истёкший токен — VK проверяет сессию по logout_hash.
                // Раньше удаление протухшего токена → Path 5 skip → fall-through
                // к full re-login (25 сек WebView). Теперь Path 5 получает
                // истёкший токен + logout_hash → connect_exchange_token →
                // свежий токен за 1-2 сек. hasValidAccessToken() всё равно вернёт
                // false (проверяет expiry) → Path 0 корректно fall-through к Path 5.
                AppLog.w("ExchangeTokenStorage",
                    "restoreFromFileBackup: backup access_token EXPIRED " +
                    "(expires_at=${sdf.format(java.util.Date(backupExpiresAt))} UTC, " +
                    "now=${sdf.format(java.util.Date(now))} UTC) — keeping expired token " +
                    "for Path 5 connect_exchange_token (Fix #176-auth-loop). " +
                    "hasValidAccessToken() will return false → Path 5 tries exchange.")
            }

            val editor = prefs.edit()
            // §49 #KEEP-EXPIRED-FOR-CONNECT-EXCHANGE (Fix auth-loop 2026-08-04):
            // Сохраняем access_token ДАЖЕ ЕСЛИ ПРОТУХ. Path 5 (tryConnectExchangeToken)
            // в ExchangeAuthRepository принимает истёкший токен — VK проверяет
            // валидность сессии по logout_hash, а не по access_token expiry.
            // hasValidAccessToken() проверяет expiry → вернёт false для протухшего →
            // Path 0 fall-through к Path 5 → connect_exchange_token с истёкшим
            // токеном → свежий токен за 1-2 сек. БЕЗ auth-loop.
            // Fix #177+#178: at может быть null (отсутствует в бэкапе).
            if (at != null) {
                editor.putString(KEY_ACCESS_TOKEN, at)
            } else {
                editor.remove(KEY_ACCESS_TOKEN)
            }
            // §50 #TOKEN-LIFECYCLE-FIX: восстанавливаем флаг invalidated из бэкапа.
            // Если в бэкапе access_token помечен невалидным (clearAccessToken звался
            // перед процесс-киллом) — сохраняем флаг. hasValidAccessToken() вернёт
            // false → AuthActivity запустится, но accessToken() вернёт токен для
            // Path 5 connect_exchange_token.
            if (json.optBoolean(KEY_ACCESS_TOKEN_INVALIDATED, false)) {
                editor.putBoolean(KEY_ACCESS_TOKEN_INVALIDATED, true)
            } else {
                editor.remove(KEY_ACCESS_TOKEN_INVALIDATED)
            }
            if (json.has(KEY_USER_ID)) editor.putLong(KEY_USER_ID, json.getLong(KEY_USER_ID))
            // expires_at пишем всегда (даже если протух) — hasValidAccessToken()
            // использует его для short-circuit, и протухшее значение корректно
            // триггерит refresh при следующем вызове.
            if (json.has(KEY_EXPIRES_AT)) editor.putLong(KEY_EXPIRES_AT, json.getLong(KEY_EXPIRES_AT))
            // Локальный helper: optString возвращает non-null String (Java),
            // поэтому пишем через takeIf — избегаем передачи null в Java-метод
            // (иначе Kotlin infer Nothing? + unnecessary safe call warnings).
            fun putOptStr(key: String) {
                val v = json.optString(key, "")
                if (v.isNotBlank()) editor.putString(key, v)
            }
            putOptStr(KEY_SCOPE)
            putOptStr(KEY_EXCHANGE_TOKEN)
            putOptStr(KEY_SECRET)
            putOptStr(KEY_TRUSTED_HASH)
            putOptStr(KEY_DEVICE_ID)
            putOptStr(KEY_LAST_PHONE)
            putOptStr(KEY_LAST_PASSWORD)
            putOptStr(KEY_WEBVIEW_ACCESS_TOKEN)
            putOptStr(KEY_WEBVIEW_REFRESH_TOKEN)
            if (json.has(KEY_WEBVIEW_EXPIRES_IN)) editor.putInt(KEY_WEBVIEW_EXPIRES_IN, json.getInt(KEY_WEBVIEW_EXPIRES_IN))
            putOptStr(KEY_UTILITY_TOKENS)
            putOptStr(KEY_SILENT_TOKEN)
            putOptStr(KEY_SILENT_TOKEN_UUID)
            putOptStr(KEY_LP_KEY)
            putOptStr(KEY_LP_SERVER)
            if (json.has(KEY_LP_TS)) editor.putLong(KEY_LP_TS, json.getLong(KEY_LP_TS))
            if (json.has(KEY_LP_PTS)) editor.putLong(KEY_LP_PTS, json.getLong(KEY_LP_PTS))
            putOptStr(KEY_SAT_TOKEN)
            putOptStr(KEY_REMIXSID)
            putOptStr(KEY_REMIXNSID)
            putOptStr(KEY_P_COOKIE)
            putOptStr(KEY_LOGOUT_HASH)
            // §55 #SSO-FULL-COOKIE-SET: восстанавливаем 6 кук браузерного набора.
            putOptStr(KEY_HTTP_TOKEN)
            putOptStr(KEY_REMIX_NTTPID)
            putOptStr(KEY_REMIX_UACCK)
            putOptStr(KEY_REMIX_UAS)
            putOptStr(KEY_REMIX_DMGR)
            putOptStr(KEY_REMIX_MVK_FP)
            // #CALLS-ANTIFRAUD: восстанавливаем антифрод-токены.
            putOptStr(KEY_REMIX_STID)
            putOptStr(KEY_REMIX_STLID)
            // sync commit — восстановление должно зафиксироваться до того,
            // как любой другой код попытается читать prefs.
            editor.commit()

            // Fix #176-auth-loop / #177+#178: сообщение зависит от того, восстановлен
            // ли access_token или только re-login credentials. Лог "OK — access_token
            // restored" был misleading когда токен протух или отсутствует — теперь
            // явно указываем причину PARTIAL.
            // §49 #KEEP-EXPIRED-FOR-CONNECT-EXCHANGE (Fix auth-loop 2026-08-04):
            // В else-ветке at гарантированно null (if (at != null) выше) —
            // "when { at == null -> ... }" давал warning "Condition is always 'true'".
            // Убрали when, пишем причину напрямую: единственный случай здесь —
            // access_token отсутствует в бэкапе (tokenExpired бессмысленен без at).
            if (at != null) {
                AppLog.i("ExchangeTokenStorage",
                    "restoreFromFileBackup: OK — access_token restored " +
                    "(user_id=${userId()}, expires_at=${expiresAt()}, " +
                    "expired=${tokenExpired}) — Path 5 will try connect_exchange_token")
            } else {
                AppLog.i("ExchangeTokenStorage",
                    "restoreFromFileBackup: PARTIAL — re-login credentials restored " +
                    "(user_id=${userId()}, access_token skipped: absent in backup, " +
                    "remixsid present=${!remixsid().isNullOrBlank()}, " +
                    "exchange_token present=${!exchangeToken().isNullOrBlank()})")
            }
            hasValidAccessToken()
        } catch (e: Exception) {
            AppLog.w("ExchangeTokenStorage", "restoreFromFileBackup failed: ${e.message}")
            false
        }
    }

    /** Существует ли файловый бэкап (для диагностики). */
    fun hasFileBackup(): Boolean = fileBackup?.exists() == true

    // =====================================================================
    // Internal
    // =====================================================================

    private fun expiresAt(expiresIn: Long): Long {
        if (expiresIn <= 0L) return 0L  // 0 = no expiry (offline scope)
        return System.currentTimeMillis() + expiresIn * 1000L
    }

    private companion object {
        // Core auth
        const val KEY_ACCESS_TOKEN        = "access_token"
        // §50 #TOKEN-LIFECYCLE-FIX: флаг "токент инвалидируется в runtime, но
        // физически сохранён в prefs для Path 5 connect_exchange_token".
        // clearAccessToken() ставит true, updateAccessToken/save* снимают.
        // hasValidAccessToken() проверяет первым — false если флаг=true.
        const val KEY_ACCESS_TOKEN_INVALIDATED = "access_token_invalidated"
        const val KEY_EXCHANGE_TOKEN      = "exchange_token"
        const val KEY_USER_ID             = "user_id"
        const val KEY_EXPIRES_AT          = "expires_at"
        const val KEY_SCOPE               = "scope"
        const val KEY_SECRET              = "secret"
        const val KEY_TRUSTED_HASH        = "trusted_hash"
        const val KEY_DEVICE_ID           = "device_id"

        // Credentials
        const val KEY_LAST_PHONE          = "last_phone"
        const val KEY_LAST_PASSWORD       = "last_password"

        // Webview tokens
        const val KEY_WEBVIEW_ACCESS_TOKEN  = "webview_access_token"
        const val KEY_WEBVIEW_REFRESH_TOKEN = "webview_refresh_token"
        const val KEY_WEBVIEW_EXPIRES_IN    = "webview_expires_in"

        // Utility tokens
        const val KEY_UTILITY_TOKENS      = "utility_tokens"

        // Fix #213 (P0.2): VKID SDK silent_token — 4-й fallback уровень
        const val KEY_SILENT_TOKEN        = "silent_token"
        const val KEY_SILENT_TOKEN_UUID   = "silent_token_uuid"

        // LongPoll
        const val KEY_LP_KEY              = "lp_key"
        const val KEY_LP_SERVER           = "lp_server"
        const val KEY_LP_TS               = "lp_ts"
        const val KEY_LP_PTS              = "lp_pts"

        // Web Token Exchange (#41) — login.vk.com flow
        const val KEY_SAT_TOKEN           = "sat_token"
        const val KEY_REMIXSID            = "remixsid"
        const val KEY_REMIXNSID           = "remixnsid"   // #SESSION-COOKIES: новая VK ID сессия (vk1.a.*)
        const val KEY_P_COOKIE            = "vk_p_cookie" // #SESSION-COOKIES: persistent login (.login.vk.ru)
        const val KEY_LOGOUT_HASH         = "logout_hash"
        // §55 #SSO-FULL-COOKIE-SET (2026-08-05): полный cookie-set как браузер.
        // VK login.vk.ru/?act=web_token валидирует НЕ только remixsid — без этих
        // 6 кук VK отвергает silent refresh (root cause SSO loop §54). Куки взяты
        // из реального дампа браузерной сессии VK.ru (httoken ×2, remixnttpid,
        // remixuacck, remixuas, remixdmgr, remixmvk-fp — все на .vk.ru, HttpOnly+Secure).
        const val KEY_HTTP_TOKEN          = "vk_httoken"        // anti-CSRF (.vk.ru + .web.api.vk.ru)
        const val KEY_REMIX_NTTPID        = "vk_remixnttpid"    // новая VK ID сессия (vk1.a.*, .vk.ru)
        const val KEY_REMIX_UACCK         = "vk_remixuacck"     // user access check key
        const val KEY_REMIX_UAS           = "vk_remixuas"       // user auth signature (base64)
        const val KEY_REMIX_DMGR          = "vk_remixdmgr"      // device manager hash (anti-fraud)
        const val KEY_REMIX_MVK_FP        = "vk_remixmvkfp"     // mobile VK fingerprint
        // #CALLS-ANTIFRAUD (2026-08-23): remixstid/remixstlid — антифрод-токены
        // (сессионный и long-lived), присутствуют в браузерной сессии VK.ru.
        // Нужны для чувствительных к антифроду эндпоинтов (get_anonym_token,
        // auth.anonymLogin на oauth.vk.ru / calls.okcdn.ru).
        const val KEY_REMIX_STID          = "vk_remixstid"      // session anti-fraud token
        const val KEY_REMIX_STLID         = "vk_remixstlid"     // long-lived anti-fraud token
    }
}