package re.pinok.auth.exchange

import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

/**
 * Fix #189: AuthDomainsConfig — настраиваемые VK домены для auth flow.
 *
 * ─────────────────────────────────────────────────────────────────────
 * КОНТЕКСТ
 * ─────────────────────────────────────────────────────────────────────
 * VK мигрирует с .com на .ru домены (2025-2026):
 *   - vk.com → vk.ru
 *   - m.vk.com → m.vk.ru
 *   - id.vk.com → id.vk.ru
 *   - login.vk.com → login.vk.ru
 *   - oauth.vk.com → oauth.vk.ru
 *   - api.vk.com → api.vk.ru (в процессе)
 *
 * Раньше все эти домены были зашиты хардкодом в ExchangeAuthApi,
 * OAuthWebViewActivity, ExternalBrowserLauncher, WebTokenAuth, VKEndpoints.
 * Если VK полностью переключается на .ru, пользователю пришлось бы ждать
 * обновления приложения. Теперь он сам может переключить домены в
 * шестерёнке на экране входа (ДО авторизации).
 *
 * ─────────────────────────────────────────────────────────────────────
 * АРХИТЕКТУРА
 * ─────────────────────────────────────────────────────────────────────
 * AuthDomainsConfig — это object (singleton) с volatile-полем [snapshot].
 *
 *   SovaPrefs (DataStore) → Flow<Snapshot> → SovaApp.collect →
 *     AuthDomainsConfig.snapshot = snapshot  (volatile, O(1) read)
 *
 * Все auth flows читают [current] синхронно — без suspend, без IO.
 * Это работает потому что:
 *   1. SovaApp.onCreate() подписывается на prefs.data и обновляет snapshot.
 *   2. AuthActivity показывается ПОСЛЕ SovaApp.onCreate() → snapshot уже есть.
 *   3. На холодном старте (snapshot ещё null) — используется [Defaults].
 *
 * UI шестерёнки на LandingScreen пишет в SovaPrefs через setAuth*Host().
 * SovaPrefs → DataStore → Flow → SovaApp.collect → snapshot обновляется.
 * Это занимает ~10-50мс, но пользователь не замечает (он ещё в UI).
 *
 * ─────────────────────────────────────────────────────────────────────
 * КАК ИСПОЛЬЗОВАТЬ
 * ─────────────────────────────────────────────────────────────────────
 * Вместо хардкода:
 *   val url = "https://oauth.vk.com/authorize?..."
 * Писать:
 *   val cfg = AuthDomainsConfig.current
 *   val url = "https://${cfg.oauthHost}/authorize?..."
 *
 * Или через хелперы:
 *   AuthDomainsConfig.oauthAuthorizeUrl(clientId, scope, redirectUri)
 *   AuthDomainsConfig.idExchangeTokenUrl()
 *   AuthDomainsConfig.oauthAccessTokenUrl()
 *   AuthDomainsConfig.mobileWebUrl()
 *   AuthDomainsConfig.apiMethodUrl(methodName)
 *
 * ─────────────────────────────────────────────────────────────────────
 * БЕЗОПАСНОСТЬ
 * ─────────────────────────────────────────────────────────────────────
 * - Домены вводит САМ пользователь через UI (осознанное действие).
 * - Validate() убирает scheme если пользователь ввёл "https://oauth.vk.ru"
 *   (мы храним только host, scheme всегда https).
 * - Пустые/blank значения заменяются на defaults.
 * - Custom scheme sova2://oauth НЕ настраивается — это фиксированный
 *   app-specific scheme (intent-filter в AndroidManifest).
 */
object AuthDomainsConfig {

    private const val TAG = "AuthDomainsConfig"

    /**
     * Defaults — те же значения что были зашиты хардкодом до Fix #189.
     * Дублируют SovaPrefs.Companion.AUTH_*_DEFAULT для удобства импорта.
     */
    object Defaults {
        const val OAUTH_HOST      = SovaPrefs.AUTH_OAUTH_HOST_DEFAULT      // oauth.vk.com
        const val ID_HOST         = SovaPrefs.AUTH_ID_HOST_DEFAULT         // id.vk.com
        const val LOGIN_HOST      = SovaPrefs.AUTH_LOGIN_HOST_DEFAULT      // login.vk.com
        const val MOBILE_WEB_HOST = SovaPrefs.AUTH_MOBILE_WEB_HOST_DEFAULT // m.vk.ru
        const val API_HOST        = SovaPrefs.AUTH_API_HOST_DEFAULT        // api.vk.com
        const val WEB_CLIENT_ID   = SovaPrefs.AUTH_WEB_CLIENT_ID_DEFAULT   // 6287487
        const val FORCE_REVOKE    = false
    }

    /**
     * Immutable snapshot of auth domain settings.
     * Все хосты без scheme — scheme всегда "https" (кроме custom sova2://).
     */
    data class Snapshot(
        val oauthHost: String,        // oauth.vk.com / oauth.vk.ru
        val idHost: String,           // id.vk.com / id.vk.ru
        val loginHost: String,        // login.vk.com / login.vk.ru
        val mobileWebHost: String,    // m.vk.ru / m.vk.com
        val apiHost: String,          // api.vk.com / api.vk.ru
        val webClientId: String,      // 6287487
        val forceRevoke: Boolean,     // false = silent sign-in (без revoke=1)
    )

    /**
     * Volatile snapshot — обновляется из SovaPrefs Flow в SovaApp.onCreate().
     * На холодном старте (null) используется [Defaults].
     */
    @Volatile
    private var snapshot: Snapshot? = null

    /**
     * Текущий snapshot. Если ещё не загружен из prefs — возвращает Defaults.
     * Синхронное чтение, O(1), thread-safe.
     */
    val current: Snapshot
        get() = snapshot ?: Snapshot(
            oauthHost      = Defaults.OAUTH_HOST,
            idHost         = Defaults.ID_HOST,
            loginHost      = Defaults.LOGIN_HOST,
            mobileWebHost  = Defaults.MOBILE_WEB_HOST,
            apiHost        = Defaults.API_HOST,
            webClientId    = Defaults.WEB_CLIENT_ID,
            forceRevoke    = Defaults.FORCE_REVOKE,
        )

    /**
     * Обновляет snapshot из SovaPrefs. Вызывается из SovaApp.onCreate()
     * и при каждом изменении prefs (collect на Flow).
     */
    fun update(snap: SovaPrefs.Snapshot) {
        val validated = Snapshot(
            oauthHost      = validateHost(snap.authOauthHost,      Defaults.OAUTH_HOST),
            idHost         = validateHost(snap.authIdHost,         Defaults.ID_HOST),
            loginHost      = validateHost(snap.authLoginHost,      Defaults.LOGIN_HOST),
            mobileWebHost  = validateHost(snap.authMobileWebHost,  Defaults.MOBILE_WEB_HOST),
            apiHost        = validateHost(snap.authApiHost,        Defaults.API_HOST),
            webClientId    = validateClientId(snap.authWebClientId, Defaults.WEB_CLIENT_ID),
            forceRevoke    = snap.authForceRevoke,
        )
        val old = snapshot
        snapshot = validated
        if (old == null) {
            AppLog.i(TAG, "Snapshot initialized: oauth=${validated.oauthHost}, id=${validated.idHost}, login=${validated.loginHost}, mweb=${validated.mobileWebHost}, api=${validated.apiHost}, clientId=${validated.webClientId}, forceRevoke=${validated.forceRevoke}")
        } else if (old != validated) {
            AppLog.i(TAG, "Snapshot updated: oauth=${validated.oauthHost} (was ${old.oauthHost}), id=${validated.idHost} (was ${old.idHost}), login=${validated.loginHost} (was ${old.loginHost}), mweb=${validated.mobileWebHost} (was ${old.mobileWebHost}), api=${validated.apiHost} (was ${old.apiHost}), clientId=${validated.webClientId} (was ${old.webClientId}), forceRevoke=${validated.forceRevoke} (was ${old.forceRevoke})")
        }
    }

    /**
     * Валидирует host: убирает scheme если есть, убирает trailing slash,
     * заменяет пустое на default. НЕ добавляет scheme — она добавляется
     * при формировании URL.
     *
     * Примеры:
     *   "oauth.vk.ru"          → "oauth.vk.ru"
     *   "https://oauth.vk.ru"  → "oauth.vk.ru"
     *   "oauth.vk.ru/"         → "oauth.vk.ru"
     *   ""                     → default
     *   "  "                   → default
     */
    private fun validateHost(input: String, default: String): String {
        val trimmed = input.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return default
        // Убираем scheme если пользователь ввёл с ним.
        val withoutScheme = when {
            trimmed.startsWith("https://") -> trimmed.removePrefix("https://")
            trimmed.startsWith("http://")  -> trimmed.removePrefix("http://")
            else -> trimmed
        }
        // Убираем path если вдруг ввели (оставляем только host[:port]).
        val hostOnly = withoutScheme.substringBefore("/")
        return if (hostOnly.isEmpty()) default else hostOnly
    }

    /**
     * Валидирует client_id: только цифры, непустой.
     */
    private fun validateClientId(input: String, default: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return default
        if (trimmed.all { it.isDigit() }) return trimmed
        // Если не все цифры — возможно пользователь ввёл что-то странное.
        // Возвращаем default (безопаснее чем сломанный auth).
        AppLog.w(TAG, "authWebClientId contains non-digit chars: '$trimmed' → using default $default")
        return default
    }

    // ═══════════════════════════════════════════════════════════════
    // URL BUILDERS — хелперы для auth flows
    // ═══════════════════════════════════════════════════════════════

    /**
     * OAuth authorize URL для implicit flow.
     * https://<oauthHost>/authorize?client_id=...&redirect_uri=...&...
     *
     * @param redirectUri например "sova2://oauth" или "https://oauth.vk.com/blank.html"
     * @param scope список scopes через запятую
     * @param apiVersion VK API version (например "5.269")
     * @param silent true = без revoke=1 (silent sign-in), false = с revoke=1
     */
    fun oauthAuthorizeUrl(
        clientId: String,
        scope: String,
        redirectUri: String,
        apiVersion: String,
        silent: Boolean,
    ): String {
        val cfg = current
        val revokeParam = if (silent && !cfg.forceRevoke) "" else "&revoke=1"
        // Fix #190: redirect_uri передаём БЕЗ URL-encoding. Незакодированный
        // вариант "https://oauth.vk.com/blank.html" работал для OAuthWebViewActivity
        // (primary auth flow), и "sova2://oauth" тоже принимался VK (до проверки
        // регистрации). VK OAuth толерирует незакодированные ":" и "/" в query
        // value (они в pchar по RFC 3986). Encoding мог бы сломать рабочий flow.
        return "https://${cfg.oauthHost}/authorize?" +
            "client_id=$clientId" +
            "&scope=$scope" +
            "&redirect_uri=$redirectUri" +
            "&display=page" +
            "&response_type=token" +
            "&v=$apiVersion" +
            revokeParam
    }

    /**
     * Fix #195: OAuth authorize URL с хардкодом oauth.vk.com для external browser.
     *
     * VK НЕ зеркалирует blank.html на .ru (Fix #194): если authorize на .ru,
     * VK сам решает на какой домен редиректить blank — и редиректит на .ru,
     * где заглушки нет → 405. redirect_uri parameter VK игнорирует для blank.html.
     *
     * Решение: для external browser flow ВЕСЬ путь идёт через oauth.vk.com —
     * и authorize, и redirect. client_id=6287487 это официальный VK desktop
     * web-client, зарегистрированный под .com. .ru домен для него не работает
     * полностью (authorize зеркалируется, но blank.html — нет).
     *
     * oauthHost из AuthDomainsConfig остаётся для других flows (WebView, direct).
     *
     * @param redirectUri должен быть https://oauth.vk.com/blank.html (см. oauthBlankRedirectUrl)
     * @param scope список scopes через запятую
     * @param apiVersion VK API version (например "5.269")
     * @param silent true = без revoke=1 (silent sign-in), false = с revoke=1
     */
    fun oauthAuthorizeUrlCom(
        clientId: String,
        scope: String,
        redirectUri: String,
        apiVersion: String,
        silent: Boolean,
    ): String {
        val cfg = current
        val revokeParam = if (silent && !cfg.forceRevoke) "" else "&revoke=1"
        return "https://oauth.vk.com/authorize?" +
            "client_id=$clientId" +
            "&scope=$scope" +
            "&redirect_uri=$redirectUri" +
            "&display=page" +
            "&response_type=token" +
            "&v=$apiVersion" +
            revokeParam
    }

    /**
     * OAuth blank redirect URL — https://oauth.vk.com/blank.html
     *
     * Fix #194: ВЕРХНЕУРОВНЕВЫЙ хардкод на oauth.vk.com, НЕ строится из
     * oauthHost. Причина: client_id=6287487 (официальный VK desktop web-client)
     * зарегистрирован на dev.vk.com с redirect_uri=https://oauth.vk.com/blank.html.
     * Заглушка /blank.html физически существует ТОЛЬКО на oauth.vk.com.
     *
     * Если юзер переключил oauthHost на oauth.vk.ru (Fix #189), authorize
     * endpoint работает на .ru (VK ID зеркалируется), но редирект на
     * oauth.vk.ru/blank.html отдаёт HTTP 405 — заглушки там нет. При этом
     * access_token УЖЕ в URL fragment (#access_token=...), но юзер видит
     * «405 Method Not Allowed» вместо пустой страницы и пугается.
     *
     * redirect_uri проверяется VK по зарегистрированному списку для client_id,
     * а не по домену authorize endpoint → https://oauth.vk.com/blank.html
     * валиден для 6287487 независимо от того, на каком домене проходит
     * authorize (oauth.vk.com или oauth.vk.ru).
     *
     * Используется:
     *   - ExternalBrowserLauncher.buildOAuthUrl() — redirect для внешнего браузера
     *   - OAuthWebViewActivity (WebView flow)
     */
    fun oauthBlankRedirectUrl(): String = "https://oauth.vk.com/blank.html"

    /**
     * Legacy auth endpoint — POST для password/2FA/trusted_hash.
     * https://<oauthHost>/access_token
     */
    fun oauthAccessTokenUrl(): String = "https://${current.oauthHost}/access_token"

    /**
     * VK ID exchange token endpoint — POST для exchange_token refresh.
     * https://<idHost>/auth_by_exchange_token
     */
    fun idExchangeTokenUrl(): String = "https://${current.idHost}/auth_by_exchange_token"

    /**
     * Mobile web URL — для WebView (WebTokenAuth flow).
     * https://<mobileWebHost>
     */
    fun mobileWebUrl(): String = "https://${current.mobileWebHost}"

    /**
     * #VKID-ONLY (vk.id.md F-apply): VK ID entry URL для кнопки «Войти через VK».
     *
     * `https://<mobileWebHost>/login?app_id=<webClientId>` — страница грузит VK ID SDK
     * (bundle.js), который использует `response_type=silent_token`:
     *  • Если есть валидный remixsid cookie → silent exchange remixsid→web_token,
     *    форма входа НЕ показывается (бесшовный повторный вход).
     *  • Если сессии нет → VK ID показывает современную форму входа
     *    (телефон/email + 2FA через VK ID), НЕ legacy m.vk.ru form.
     *
     * `app_id=webClientId` (6287487) — PinoK OAuth web client. Выдаёт токен сразу
     * под нужным приложением. Совпадает с `WebTokenAuth.SDK_INIT_LOGIN_URL` —
     * это ОДИН И ТОТ ЖЕ VK ID flow, просто теперь мы стартуем с него, а не
     * переходим на него после загрузки root m.vk.ru.
     *
     * Требование: авторизация по кнопке «Войти через VK» — ТОЛЬКО через VK ID.
     * Внешний браузер (Яндекс/Chrome) и «Импорт сессии» не затрагиваются.
     */
    fun vkIdLoginUrl(): String =
        "https://${current.mobileWebHost}/login?app_id=${current.webClientId}"

    /**
     * API method URL — https://<apiHost>/method/<name>
     * Заменяет VKEndpoints.method() когда нужна настраиваемость.
     */
    fun apiMethodUrl(methodName: String): String = "https://${current.apiHost}/method/$methodName"

    /**
     * Web client_id — из текущего snapshot (может быть переопределён пользователем).
     */
    fun webClientId(): String = current.webClientId

    /**
     * Fix #212 (P0.1): login host URL — https://<loginHost>/?act=web_token
     *
     * Это тот же endpoint, который использует m.vk.ru JS для silent refresh
     * access_token через remixsid cookie (см. RESEARCH-VK-WEB-SDK Q2 в worklog).
     *
     * Endpoint: GET https://login.vk.ru/?act=web_token&app_id=7879029&version=1
     *   Headers: Cookie: remixsid=...; remixsid_user=<userId>
     *   Response: {access_token, expires (unix sec), user_id, logout_hash}
     *
     * НЕ требует X-VK-Android-Client header — это web-flow host
     * (SovaApp.httpClient interceptor: isWebFlowHost=true → header опускается).
     *
     * app_id=7879029 — VK Mobile Web (тот же что использует m.vk.ru JS,
     * см. WebTokenAuth.WEB_APP_ID). Это НЕ тот же client_id что webClientId()
     * (=6287487, OAuth web client) — они разные, и для silent refresh через
     * remixsid нужен именно 7879029 (как делает m.vk.ru).
     */
    fun loginWebTokenUrl(): String {
        // m.vk.ru JS использует app_id=7879029. hardcoded — это не настраиваемый
        // пользователем client_id, а идентификатор VK Mobile Web на стороне VK.
        return "https://${current.loginHost}/?act=web_token&app_id=7879029&version=1"
    }

    /**
     * VK cookie URLs для ExternalBrowserAuth.tryFindExistingAuth() и
     * clearAllVkCookies(). Включает .ru и .com варианты для обоих доменов
     * (mobile web + main + login + id + oauth).
     *
     * Важно: проверяем cookies на ВСЕх вариантах доменов, потому что
     * CookieManager может хранить cookies для m.vk.ru и m.vk.com одновременно
     * (если пользователь логинился через оба). Это безопасно — getCookie()
     * для несуществующего домена просто вернёт null.
     */
    fun vkCookieUrls(): List<String> {
        val cfg = current
        // Извлекаем базовый домен из mobileWebHost (m.vk.ru → vk.ru).
        // Если mobileWebHost = "m.vk.com" → base = "vk.com".
        val baseDomain = cfg.mobileWebHost.removePrefix("m.")
        return listOf(
            "https://${cfg.mobileWebHost}",       // m.vk.ru или m.vk.com
            "https://$baseDomain",                // vk.ru или vk.com
            "https://m.$baseDomain",              // m.vk.ru или m.vk.com (зеркально)
            "https://${cfg.loginHost}",           // login.vk.com или login.vk.ru
            "https://${cfg.idHost}",              // id.vk.com или id.vk.ru
            "https://${cfg.oauthHost}",           // oauth.vk.com или oauth.vk.ru
            // Также проверяем альтернативный домен (если юзер на .ru, проверяем .com и наоборот).
            "https://m.vk.ru",
            "https://vk.ru",
            "https://m.vk.com",
            "https://vk.com",
            "https://login.vk.com",
            "https://login.vk.ru",
            "https://id.vk.com",
            "https://id.vk.ru",
            "https://oauth.vk.com",
            "https://oauth.vk.ru",
            // §55 #SSO-FULL-COOKIE-SET: web.api.vk.ru/.com — домен httoken
            // (anti-CSRF). VK ставит httoken на .web.api.vk.ru отдельно от .vk.ru.
            // Без проверки этого домена httoken не захватывается → silent refresh
            // падает на неполном cookie-set (root cause SSO loop §54).
            "https://web.api.vk.ru",
            "https://web.api.vk.com",
        ).distinct()
    }
}
