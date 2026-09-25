package re.pinok.auth.exchange

import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import re.pinok.util.AppLog

/**
 * #SESSION-WEB-MECHANISM (2026-09-24, СЕССИЯ-ВЕБ-ПОРТ.md): единственный путь
 * silent-refresh сессии — «перепосещение» в скрытом WebView. Порт механизма
 * веб-версии VK 1:1.
 *
 * ## Как удерживает сессию веб-версия
 *
 *   Источник истины — cookie jar, НЕ токены:
 *     - `p` (.login.vk.ru, 1 год, HttpOnly) — persistent login-token: по нему
 *       VK узнаёт пользователя при любом визите, вход без пароля/СМС;
 *     - `remixsid` (.vk.ru, 1 год, ротейтится при security events);
 *     - `remixstid`/`remixstlid` — анонимная идентичность (anonym_id).
 *
 *   Токены — 24-часовые производные: web_token лежит в localStorage
 *   (`<app_id>:web_token:login:auth`) и обновляется простым «перепосещением»
 *   страницы — cookies уходят сами, VK молча выдаёт свежий токен.
 *   Пароли нигде не хранятся. Сессия умерла → страница логина.
 *
 * ## Как это работает в PinoK
 *
 *   CookieManager (WebView cookie store, persist) = единственный источник
 *   сессии. Refresh = скрытый (offscreen 0x0) WebView загружает `https://m.vk.ru/`,
 *   живые cookies уходят автоматически, VK ID SDK молча обменивает их на
 *   web_token (silent_token из window.init ИЛИ localStorage — вся логика в
 *   [WebTokenAuth.fullAuthFlow], переиспользуется как есть, со всеми фиксами
 *   §49/§51/§41.14). Результат сохраняется через
 *   [ExchangeAuthRepository.saveWebTokenResult].
 *
 * ## Что заменил
 *
 *   - Path 1.5 `silentRefreshViaRemixsid` — 7 HTTP-стратегий по СТЕЙЛ-копии
 *     cookies из storage (корень багов «смена сети → просит логин»);
 *   - Path 2.5 trusted_hash re-login по хранённому паролю (плюс: пароль больше
 *     НЕ хранится на устройстве);
 *   - Path 3/5 exchange_token / connect_exchange_token в репозитории;
 *   - CookieRefreshWorker + синк копий cookies (CookieManager → storage) —
 *     копий больше нет, источник живой.
 *
 * ## Почему надёжнее
 *
 *   Копия cookie в storage всегда рискует протухнуть (VK ротейтит remixsid).
 *   Живой CookieManager обновляется при любой web-навигации, персистит между
 *   запусками и работает с любого IP — ровно как браузерная сессия.
 */
object HiddenSessionRefresher {
    private const val TAG = "HiddenSessionRefresh"

    /** Стартовый URL: m.vk.ru при живых cookies молча логинит; VK ID SDK кладёт web_token. */
    private const val START_URL = "https://m.vk.ru/"

    /** Общий бюджет попытки. Внутри [WebTokenAuth.fullAuthFlow] свои таймауты (~25-40 с). */
    private const val TOTAL_TIMEOUT_MS = 60_000L

    /**
     * Кулдаун после провала — не спамить WebView-перезапусками
     * (перенесён из #SILENT-REFRESH-COOLDOWN Fix #177/#178, 90 секунд).
     */
    private const val FAIL_COOLDOWN_MS = 90_000L

    /**
     * Reentrancy-guard: true пока идёт скрытый refresh.
     *
     * КРИТИЧНО: [ExchangeAuthRepository.ensureFreshToken] обязан проверять этот
     * флаг ДО `refreshMutex.withLock` — иначе вложенный вызов
     * (WebTokenAuth.trySsoHttpRefreshViaRemixsid → ensureFreshToken → скрытый
     * refresh → fullAuthFlow → ...) повиснет НАВСЕГДА на non-reentrant Mutex.
     */
    @Volatile
    var inProgress: Boolean = false
        private set

    /**
     * Результат последней попытки: true = web-сессия ДОКАЗАННО мертва
     * (в CookieManager нет ни remixsid, ни p — VK физически не может узнать
     * пользователя; как в вебе: сессия умерла → страница логина).
     * false при провале = контракт/сеть/SDK-сбой — сессия может быть жива,
     * чистить cookies НЕ нужно (#VKID-SESSION-WIPE-GUARD).
     */
    @Volatile
    var lastAttemptDefinitivelyDead: Boolean = false
        private set

    @Volatile
    private var lastFailMs: Long = 0L

    /**
     * Скрытый refresh сессии через web-механизм.
     *
     * @param context application context (WebView создаётся offscreen на Main).
     * @return [WebTokenAuth.WebTokenResult] при успехе, null при провале
     *         (кулдаун, reentrancy, сеть, мёртвая сессия).
     */
    suspend fun refresh(context: Context): WebTokenAuth.WebTokenResult? {
        if (inProgress) {
            AppLog.w(TAG, "refresh: already in progress — skip (reentrancy guard)")
            return null
        }
        val now = System.currentTimeMillis()
        if (lastFailMs != 0L && now - lastFailMs < FAIL_COOLDOWN_MS) {
            AppLog.i(
                TAG,
                "refresh: cooldown active (${FAIL_COOLDOWN_MS - (now - lastFailMs)}ms left) — skip"
            )
            return null
        }
        inProgress = true
        lastAttemptDefinitivelyDead = false
        try {
            return withTimeoutOrNull(TOTAL_TIMEOUT_MS) { doRefresh(context) }
        } catch (e: Exception) {
            AppLog.w(TAG, "refresh: exception — ${e.message}")
            return null
        } finally {
            inProgress = false
        }
    }

    private suspend fun doRefresh(context: Context): WebTokenAuth.WebTokenResult? {
        // Cookies должны быть flush'нуты — WebView-процесс мог умереть в Doze
        // (#DOZE-COOKIE-FLUSH), незаflush'енные ротации пропали бы.
        withContext(Dispatchers.Main) {
            runCatching { CookieManager.getInstance().flush() }
                .onFailure { AppLog.w(TAG, "flush before refresh failed: ${it.message}") }
        }

        val outcome = withContext(Dispatchers.Main) {
            var webView: WebView? = null
            try {
                webView = makeHiddenWebView(context)
                AppLog.i(TAG, "═══ WEB-MECHANISM refresh: скрытый WebView → $START_URL ═══")
                webView.loadUrl(START_URL)
                // fullAuthFlow сам дождётся страницы, прочитает silent_token из
                // window.init (§41.14), сделает ensureSdkInitialized (§51 Path A)
                // и опросит localStorage web_token (§49). Переиспользуем как есть.
                WebTokenAuth.fullAuthFlow(webView).getOrNull()
            } catch (e: Exception) {
                AppLog.w(TAG, "doRefresh: ${e.message}")
                null
            } finally {
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
            }
        }

        if (outcome != null) {
            lastFailMs = 0L
            AppLog.i(
                TAG,
                "WEB-MECHANISM refresh: SUCCESS (user=${outcome.userId}, " +
                    "expires=${outcome.expiresAt})"
            )
            return outcome
        }

        // Провал: классифицируем по ЖИВЫМ cookies (не по парсингу ответов, как раньше).
        val cookies = runCatching { RemixsidCapturer.snapshotCookies() }.getOrNull()
        val noSessionCookies = cookies == null ||
            (cookies.remixsid.isNullOrBlank() && cookies.pCookie.isNullOrBlank())
        lastAttemptDefinitivelyDead = noSessionCookies
        lastFailMs = System.currentTimeMillis()
        AppLog.w(
            TAG,
            "WEB-MECHANISM refresh: FAILED (definitivelyDead=$noSessionCookies, " +
                "remixsid=${cookies?.remixsid?.length ?: "null"}, " +
                "p=${if (cookies?.pCookie != null) "yes" else "no"})"
        )
        return null
    }

    /**
     * Offscreen WebView: 0x0, НЕ аттачится к окну — JS/DOM/localStorage работают.
     * Application context допустим: WebView не попадает в иерархию view.
     */
    private fun makeHiddenWebView(context: Context): WebView {
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(0, 0)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true      // localStorage — источник web_token
        s.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        s.blockNetworkLoads = false
        CookieManager.getInstance().setAcceptCookie(true)
        return wv
    }
}
