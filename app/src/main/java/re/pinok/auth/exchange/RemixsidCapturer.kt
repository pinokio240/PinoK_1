package re.pinok.auth.exchange

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import re.pinok.BuildConfig
import re.pinok.auth.OAuthWebViewActivity
import re.pinok.util.AppLog

// §57 #COOKIE-CAPTURE-UNIFY: RemixsidCapturer — единственная точка чтения
// CookieManager во всём приложении. OAuthWebViewActivity и
// ExternalBrowserAuth.tryFindExistingAuth делегируют сюда через snapshotCookies().
// Это устраняет 3 копии логики парсинга "Cookie: k=v; k=v" + дедупликацию доменов.

/**
 * #REMIXSID-CAPTURE (Option B, §41.22): Best-effort захват remixsid через
 * скрытый WebView после external browser OAuth.
 *
 * ## КОНТЕКСТ ПРОБЛЕМЫ
 *
 * После external browser OAuth (Chrome → blank.html → clipboard) у нас есть
 * access_token, но НЕТ remixsid. Chrome изолирует cookies от app CookieManager
 * на большинстве устройств (Android 7+). Без remixsid:
 *   - `hasSilentReloginMeans()` = false
 *   - Path 1.5 (`silentRefreshViaRemixsid`) не работает
 *   - При переключении WiFi↔Mobile → AuthActivity launch (§41.21 #RELOGIN-FORCE)
 *     вместо silent refresh
 *
 * ## ДВА ПУТИ РЕШЕНИЯ
 *
 * **Path A (надёжный)** — [OAuthWebViewActivity] захватывает remixsid из
 * CookieManager после in-app WebView OAuth. Пользователь логинится внутри
 * приложения → VK ставит remixsid cookie → мы его читаем и передаём через
 * `EXTRA_REMIXSID` в result Intent. 100% работает, но требует от пользователя
 * использовать in-app WebView flow (а не external browser).
 *
 * **Path B (best-effort, этот файл)** — после external browser OAuth, если
 * remixsid всё ещё отсутствует, открываем СКРЫТЫЙ WebView на OAuth URL
 * (silent sign-in, `revoke=0`). Если CookieManager УЖЕ имеет VK session
 * cookies (от предыдущей in-app WebView сессии), VK молча редиректит на
 * blank.html и обновляет remixsid. Если cookies нет — VK показывает страницу
 * логина, мы таймаутим через 10с и не мешаем пользователю.
 *
 * ## ОГРАНИЧЕНИЯ
 *
 * - `access_token` **НЕЛЬЗЯ** конвертировать в remixsid без веб-логина.
 *   VK не имеет endpoint для этого (см. исследование SAK лога: `auth.exchange`
 *   помечен на удаление через `core_rm_auth_exchange_exec`).
 * - Этот capturer работает ТОЛЬКО если CookieManager уже имеет VK session
 *   от предыдущей in-app WebView сессии (или от устройств, где CookieManager
 *   разделяется с Chrome — Samsung/Xiaomi).
 * - Для ПЕРВОГО входа через external browser remixsid НЕ будет захвачен.
 *   Пользователь должен использовать in-app WebView хотя бы один раз, чтобы
 *   этот capther работал в будущем.
 *
 * ## THREADING
 *
 * WebView должен создаваться и управляться на Main thread.
 * `withContext(Dispatchers.Main)` обеспечивается вызывающим кодом.
 */
object RemixsidCapturer {
    private const val TAG = "RemixsidCapturer"

    /**
     * #SESSION-COOKIES (2026-08-04): результат захвата — ТРИ cookie, не один.
     *
     * - [remixsid] — классическая сессия (1_xxx, .vk.ru)
     * - [pCookie] — persistent login (vk1.a.xxx, .login.vk.ru). КРИТИЧЕН для
     *   cross-IP silent refresh: VK отвергает login.vk.ru/?act=web_token без p.
     * - [remixnsid] — новая VK ID сессия (vk1.a.xxx, vk.ru)
     *
     * Любое поле может быть null если cookie не найден. Минимум для Path 1.5 —
     * remixsid; p/remixnsid резко повышают шанс успеха silent refresh.
     *
     * §55 #SSO-FULL-COOKIE-SET (2026-08-05): расширено 6 куками из реального
     * дампа браузерной сессии VK.ru. VK login.vk.ru/?act=web_token валидирует
     * НЕ только remixsid — без httoken/nttpid/uacck/uas/dmgr/mvkfp VK часто
     * отвергает silent refresh (root cause SSO loop §54). Все 6 опциональны
     * (могут отсутствовать на некоторых аккаунтах/устройствах), но если есть —
     * отправляются в Cookie header silentRefreshViaRemixsid.
     */
    data class CapturedCookies(
        val remixsid: String,
        val pCookie: String? = null,
        val remixnsid: String? = null,
        // §55 #SSO-FULL-COOKIE-SET: 6 кук браузерного набора.
        val httoken: String? = null,        // anti-CSRF (.vk.ru + .web.api.vk.ru)
        val remixnttpid: String? = null,    // новая VK ID сессия (vk1.a.*, .vk.ru)
        val remixuacck: String? = null,     // user access check key (.vk.ru)
        val remixuas: String? = null,       // user auth signature, base64 (.vk.ru)
        val remixdmgr: String? = null,      // device manager hash, anti-fraud (.vk.ru)
        val remixmvkFp: String? = null,     // mobile VK fingerprint (.vk.ru)
        // §57 #COOKIE-CAPTURE-UNIFY: домен, на котором найден remixsid —
        // для логов (раньше было только в ExternalBrowserAuth.source).
        val source: String? = null,
    )

    /** Таймаут для hidden WebView capture. 10 секунд — достаточно для
     *  silent OAuth redirect (обычно 1-3с), но не слишком долго чтобы
     *  не задерживать post-auth инициализацию. */
    private const val CAPTURE_TIMEOUT_MS = 10_000L

    /** Интервал опроса CookieManager. 500мс — баланс между отзывчивостью
     *  и overhead (20 опросов за 10с, каждый ~1мс). */
    private const val POLL_INTERVAL_MS = 500L

    /** VK домены для проверки cookies в CookieManager.
     *
     *  §57 #COOKIE-CAPTURE-UNIFY: список динамический — берётся из
     *  [AuthDomainsConfig.vkCookieUrls], который учитывает пользовательские
     *  настройки (.com / .ru) и включает зеркальные домены + web.api.vk.ru/.com
     *  (там живёт второй httoken, anti-CSRF). Раньше был хардкод — если
     *  пользователь менял домен, RemixsidCapturer не подхватывал.
     *
     *  Вычисляется при каждом вызове [readAllCookiesFromCookieManager] —
     *  это дёшево (List из ~16 строк, distinct). */
    private val COOKIE_URLS: List<String>
        get() = AuthDomainsConfig.vkCookieUrls()

    /**
     * Пытается захватить session cookies (remixsid + p + remixnsid) из
     * CookieManager через скрытый WebView.
     *
     * ## Flow:
     * 1. Быстрая проверка CookieManager (cookies могут уже быть от
     *    предыдущей сессии — тогда WebView не нужен).
     * 2. Открывает скрытый WebView на OAuth URL (silent sign-in, revoke=0).
     * 3. Опрашивает CookieManager каждые [POLL_INTERVAL_MS] в течение
     *    [CAPTURE_TIMEOUT_MS].
     * 4. Если remixsid найден — возвращает CapturedCookies (с p/remixnsid
     *    если они тоже нашлись). Если timeout — null.
     *
     * #SESSION-COOKIES: возвращаем все три cookie. p/remixnsid находятся на
     * других доменах (.login.vk.ru, vk.ru) — читаем их отдельно после того
     * как remixsid подтверждён.
     *
     * @param context Application context (для создания WebView)
     * @return [CapturedCookies] если remixsid найден, null если не найден за timeout
     */
    suspend fun capture(context: Context): CapturedCookies? {
        return withContext(Dispatchers.Main) {
            // 1. Быстрая проверка — remixsid может уже быть в CookieManager
            val existing = readAllCookiesFromCookieManager()
            if (existing != null) {
                AppLog.i(TAG, "session cookies already in CookieManager " +
                    "(remixsid len=${existing.remixsid.length}, " +
                    "p=${if (existing.pCookie != null) "yes" else "no"}, " +
                    "remixnsid=${if (existing.remixnsid != null) "yes" else "no"}, " +
                    // §55: полный cookie-set
                    "httoken=${if (existing.httoken != null) "yes" else "no"}, " +
                    "nttpid=${if (existing.remixnttpid != null) "yes" else "no"}, " +
                    "uacck=${if (existing.remixuacck != null) "yes" else "no"}, " +
                    "uas=${if (existing.remixuas != null) "yes" else "no"}, " +
                    "dmgr=${if (existing.remixdmgr != null) "yes" else "no"}, " +
                    "mvkfp=${if (existing.remixmvkFp != null) "yes" else "no"}) — no WebView needed")
                return@withContext existing
            }

            AppLog.i(TAG, "capture: opening hidden WebView for silent OAuth sign-in...")

            val webView = createHiddenWebView(context)
            try {
                val oauthUrl = buildSilentOAuthUrl()
                AppLog.d(TAG, "capture: loading $oauthUrl")
                webView.loadUrl(oauthUrl)

                // Опрашиваем CookieManager пока remixsid не появится или timeout.
                val found = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    var result: CapturedCookies? = null
                    while (result == null) {
                        delay(POLL_INTERVAL_MS)
                        result = readAllCookiesFromCookieManager()
                    }
                    result
                }

                if (found != null) {
                    AppLog.i(TAG, "capture: SUCCESS — remixsid found (len=${found.remixsid.length}), " +
                        "p=${if (found.pCookie != null) "yes" else "no"}, " +
                        "remixnsid=${if (found.remixnsid != null) "yes" else "no"}, " +
                        // §55: полный cookie-set
                        "httoken=${if (found.httoken != null) "yes" else "no"}, " +
                        "nttpid=${if (found.remixnttpid != null) "yes" else "no"}, " +
                        "uacck=${if (found.remixuacck != null) "yes" else "no"}, " +
                        "uas=${if (found.remixuas != null) "yes" else "no"}, " +
                        "dmgr=${if (found.remixdmgr != null) "yes" else "no"}, " +
                        "mvkfp=${if (found.remixmvkFp != null) "yes" else "no"}")
                } else {
                    AppLog.w(TAG, "capture: TIMEOUT after ${CAPTURE_TIMEOUT_MS}ms — " +
                        "VK likely showed login page (no existing session in CookieManager). " +
                        "Path 1.5 unavailable — user should try in-app WebView login once.")
                }

                found
            } finally {
                destroyWebView(webView)
            }
        }
    }

    /**
     * Создаёт скрытый WebView с теми же settings, что и OAuthWebViewActivity:
     * - JavaScript enabled (VK OAuth требует JS для redirects)
     * - Chrome User-Agent (VK ID flow работает только с browser UA)
     * - WebViewClient для логирования navigation и остановки на blank.html
     *
     * WebView НЕ прикрепляется к View hierarchy — он работает headless,
     * только для HTTP requests и cookie management.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createHiddenWebView(context: Context): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Chrome UA — тот же, что в OAuthWebViewActivity.
            // VK ID frontend ломается с VKAndroidApp UA (error 3).
            settings.userAgentString = OAuthWebViewActivity.CHROME_UA
            @Suppress("DEPRECATION")
            settings.defaultTextEncodingName = "UTF-8"

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    AppLog.d(TAG, "navigate: $url")
                    // Если редирект на blank.html — silent sign-in завершён.
                    // Останавливаем навигацию (access_token нам не нужен —
                    // он уже сохранён, мы ищем только remixsid).
                    if (url.startsWith(OAuthWebViewActivity.REDIRECT_URI)) {
                        AppLog.d(TAG, "blank.html redirect — silent sign-in succeeded, checking cookies")
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    AppLog.d(TAG, "page finished: $url")
                }
            }
        }
    }

    /**
     * Строит OAuth URL с silent=true (revoke=0).
     *
     * `revoke=0` → VK пытается silent sign-in: если в CookieManager уже
     * есть валидная VK сессия (remixsid), VK НЕ показывает форму логина,
     * а сразу редиректит на blank.html#access_token=... и обновляет remixsid.
     *
     * Тот же URL, что и [re.pinok.auth.exchange.ExternalBrowserLauncher.buildOAuthUrl] /
     * [re.pinok.auth.OAuthWebViewActivity.buildOAuthUrl], но всегда silent.
     */
    private fun buildSilentOAuthUrl(): String {
        val clientId = AuthDomainsConfig.webClientId()
        val scope = OAuthWebViewActivity.OAUTH_SCOPE
        val redirectUri = OAuthWebViewActivity.REDIRECT_URI
        val apiVersion = BuildConfig.VK_API_VERSION
        return AuthDomainsConfig.oauthAuthorizeUrlCom(
            clientId = clientId,
            scope = scope,
            redirectUri = redirectUri,
            apiVersion = apiVersion,
            silent = true,
        )
    }

    /**
     * Читает session cookies (remixsid + p + remixnsid + 6 доп. кук) из
     * CookieManager для всех VK доменов.
     *
     * CookieManager — singleton, разделяемый между всеми WebView в приложении.
     * `getCookie(url)` возвращает все cookies для данного URL в виде
     * `"key1=val1; key2=val2; ..."`. Мы ищем:
     *   - `remixsid` (валидный: len >= 20, не "deleted", не пустой)
     *   - `p` (валидный: len >= 50, формат vk1.a.*)
     *   - `remixnsid` (валидный: len >= 50, формат vk1.a.*)
     *
     * §55 #SSO-FULL-COOKIE-SET: дополнительно захватываем 6 кук:
     *   - `httoken` (anti-CSRF, .vk.ru + .web.api.vk.ru, len >= 20)
     *   - `remixnttpid` (vk1.a.*, .vk.ru, len >= 50)
     *   - `remixuacck` (user access check key, .vk.ru, len >= 10)
     *   - `remixuas` (user auth signature, base64, .vk.ru, len >= 20)
     *   - `remixdmgr` (device manager hash, .vk.ru, len >= 32)
     *   - `remixmvk-fp` (mobile VK fingerprint, .vk.ru, len >= 20)
     *
     * #SESSION-COOKIES: p cookie находится на .login.vk.ru, поэтому проверяем
     * и login-домены. remixnsid на vk.ru (без leading dot). Обходим все URL
     * и собираем каждое cookie с первого домена, где найдено.
     *
     * @return [CapturedCookies] если remixsid найден (минимум для Path 1.5),
     *         null если remixsid не найден (p/remixnsid без remixsid бесполезны)
     */
    private fun readAllCookiesFromCookieManager(): CapturedCookies? {
        val cm = CookieManager.getInstance()
        var remixsid: String? = null
        var remixsidSource: String? = null
        var pCookie: String? = null
        var remixnsid: String? = null
        // §55 #SSO-FULL-COOKIE-SET
        var httoken: String? = null
        var remixnttpid: String? = null
        var remixuacck: String? = null
        var remixuas: String? = null
        var remixdmgr: String? = null
        var remixmvkFp: String? = null

        for (url in COOKIE_URLS) {
            try {
                val rawCookie = cm.getCookie(url) ?: continue
                val cookies = rawCookie.split(";").map { it.trim() }
                for (cookie in cookies) {
                    val parts = cookie.split("=", limit = 2)
                    if (parts.size != 2) continue
                    val name = parts[0].trim()
                    val value = parts[1].trim()
                    if (value.isEmpty() || value == "deleted") continue

                    when (name) {
                        "remixsid" -> {
                            if (remixsid == null && value.length >= 20) {
                                remixsid = value
                                remixsidSource = url
                            }
                        }
                        "p" -> {
                            // persistent login cookie (.login.vk.ru), vk1.a.* формат
                            if (pCookie == null && value.length >= 50) {
                                pCookie = value
                            }
                        }
                        "remixnsid" -> {
                            // новая VK ID сессия (vk1.a.*), vk.ru домен
                            if (remixnsid == null && value.length >= 50) {
                                remixnsid = value
                            }
                        }
                        // §55 #SSO-FULL-COOKIE-SET: 6 кук браузерного набора.
                        "httoken" -> {
                            // anti-CSRF (.vk.ru + .web.api.vk.ru). Берём первое
                            // ненулевое значение — VK ставит одинаковый httoken на
                            // оба домена, но .vk.ru приоритетнее (встречается первым).
                            if (httoken == null && value.length >= 20) {
                                httoken = value
                            }
                        }
                        "remixnttpid" -> {
                            // новая VK ID сессия (vk1.a.*, .vk.ru)
                            if (remixnttpid == null && value.length >= 50) {
                                remixnttpid = value
                            }
                        }
                        "remixuacck" -> {
                            // user access check key (.vk.ru), короткий hex
                            if (remixuacck == null && value.length >= 10) {
                                remixuacck = value
                            }
                        }
                        "remixuas" -> {
                            // user auth signature, base64 (.vk.ru)
                            if (remixuas == null && value.length >= 20) {
                                remixuas = value
                            }
                        }
                        "remixdmgr" -> {
                            // device manager hash, 64-hex (.vk.ru)
                            if (remixdmgr == null && value.length >= 32) {
                                remixdmgr = value
                            }
                        }
                        "remixmvk-fp" -> {
                            // mobile VK fingerprint, 32-hex (.vk.ru)
                            if (remixmvkFp == null && value.length >= 20) {
                                remixmvkFp = value
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // remixsid — обязательный минимум. p/remixnsid без remixsid бесполезны
        // (Path 1.5 не запустится без remixsid).
        return remixsid?.let {
            CapturedCookies(
                remixsid = it,
                pCookie = pCookie,
                remixnsid = remixnsid,
                httoken = httoken,
                remixnttpid = remixnttpid,
                remixuacck = remixuacck,
                remixuas = remixuas,
                remixdmgr = remixdmgr,
                remixmvkFp = remixmvkFp,
                source = remixsidSource,
            )
        }
    }

    /**
     * §55 #SSO-FULL-COOKIE-SET: синхронный (без WebView) снимок ВСЕХ session
     * cookies из CookieManager. Для вызова из AuthActivity onTokenExchange —
     * там SSO уже прошёл, куки в CookieManager, нужно лишь их прочитать и
     * сохранить в storage ДО вызова submitWebToken.
     *
     * В отличие от [capture] (который открывает скрытый WebView на 10с),
     * этот метод только читает CookieManager — мгновенно (~1мс).
     *
     * @return [CapturedCookies] если remixsid найден в CookieManager, иначе null.
     *         Если null — caller должен сам сохранить хотя бы remixsid через
     *         [ExchangeAuthRepository.saveRemixsid] (string overload).
     */
    fun snapshotCookies(): CapturedCookies? = readAllCookiesFromCookieManager()

    /**
     * Безопасно уничтожает WebView: stopLoading + removeJavascriptInterface +
     * destroy. Вызывается в finally блоке чтобы избежать leak.
     */
    private fun destroyWebView(webView: WebView) {
        try {
            webView.stopLoading()
            webView.removeJavascriptInterface("AccessibilityBridge")
            webView.removeJavascriptInterface("clipboard")
            webView.destroy()
            AppLog.d(TAG, "hidden WebView destroyed")
        } catch (e: Exception) {
            AppLog.w(TAG, "WebView destroy error: ${e.message}")
        }
    }
}
