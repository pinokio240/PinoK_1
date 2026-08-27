package re.pinok.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import re.pinok.BuildConfig
import re.pinok.SovaApp
import re.pinok.auth.exchange.RemixsidCapturer
import kotlinx.coroutines.launch
import re.pinok.ui.components.DraggableLogFab
import re.pinok.ui.components.LogDialogState
import re.pinok.ui.components.LogViewerDialog
import re.pinok.util.AppLog

/**
 * OAuth WebView авторизация — Web Token Exchange flow.
 *
 * Открывает oauth.vk.com/authorize в WebView.
 * Пользователь вводит логин/пароль на сервере VK (не в нашем коде!).
 * VK редиректит на blank.html#access_token=vk1.a.XXX&user_id=...
 * Мы перехватываем redirect и извлекаем токен.
 *
 * ⚠️ ПОЧЕМУ client_id=6287487 (vk.com web — официальный):
 *   - 6287487 (vk.com desktop web) — официальный веб-клиент VK.
 *     Возвращает vk1.a.XXX токен с web-scope (биты 52+53+54) — VK API
 *     trustит ему как официальному → все methods работают БЕЗ sig и
 *     БЕЗ user_secret.
 *   - Не подпадает под парольный flood_control (логин в VK-форме на VK-домене).
 *   - Никаких сторонних app_id НЕ используется — только first-party VK.
 *
 *   Источник: дамп localStorage VK-веб-клиента (docs/references/
 *   ВК_веб_токены_референс.txt на main ветке).
 *
 * Flow (из ВК.txt, client_id=6287487):
 *   GET https://oauth.vk.com/authorize?
 *     client_id=6287487                   (vk.com desktop web)
 *     &scope=notify,friends,photos,audio,video,stories,pages,links,
 *            status,notes,messages,wall,ads,offline,docs,groups,
 *            notifications,stats,email,market
 *     &redirect_uri=https://oauth.vk.com/blank.html
 *     &display=page                       (веб-формат, не android)
 *     &response_type=token
 *     &v=5.243                            (актуальная web API версия)
 *     &revoke=1
 *
 *   → VK показывает форму логина/пароля (веб-страница)
 *   → После входа VK редиректит:
 *     https://oauth.vk.com/blank.html#access_token=vk1.a.XXX&user_id=...
 *   → Мы перехватываем URL и извлекаем access_token + user_id
 *   → vk1.a.XXX работает напрямую в VK API БЕЗ sig
 */
class OAuthWebViewActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate — OAuth WebView flow")

        val oauthUrl = buildOAuthUrl()

        setContent {
            MaterialTheme {
                // Fix #237: читаем showLogFab из SovaPrefs (для показа FAB логов).
                val snap by SovaApp.get(this).prefs.data.collectAsState(initial = null)
                // Локальный захват: snap — delegated property (by collectAsState),
                // smart cast невозможен. Захватываем в обычную val для null-проверки.
                val snapLocal = snap
                val showLogFab = if (snapLocal != null) snapLocal.showLogFab else BuildConfig.DEBUG
                Box(modifier = Modifier.fillMaxSize()) {
                    OAuthWebViewScreen(
                        url = oauthUrl,
                        onTokenReceived = { token, userId ->
                            AppLog.i(TAG, "Token received, user_id=$userId")
                            // #REMIXSID-CAPTURE / #SESSION-COOKIES / §57
                            // #COOKIE-CAPTURE-UNIFY: После OAuth WebView flow
                            // пользователь залогинился на VK web-странице внутри
                            // этого WebView → VK поставил session cookies в
                            // CookieManager. Захватываем ВЕСЬ браузерный cookie-set
                            // (9 кук) через единую точку чтения —
                            // [RemixsidCapturer.snapshotCookies]. Раньше тут была
                            // 3-я копия логики парсинга (устаревшая, 3 куки).
                            //
                            // §55 #SSO-FULL-COOKIE-SET: p cookie критичен для
                            // cross-IP silent refresh. Без него VK отвергает
                            // login.vk.ru/?act=web_token после смены сети →
                            // AuthActivity (полный re-login). httoken/nttpid/uacck/
                            // uas/dmgr/mvkfp — anti-CSRF/anti-fraud, VK часто
                            // отвергает silent refresh без полного набора (SSO loop §54).
                            val captured = RemixsidCapturer.snapshotCookies()
                            if (captured != null) {
                                AppLog.i(TAG, "session cookies captured from CookieManager " +
                                    "(source=${captured.source}, " +
                                    "remixsid len=${captured.remixsid.length}, " +
                                    "p=${if (captured.pCookie != null) "yes" else "no"}, " +
                                    "remixnsid=${if (captured.remixnsid != null) "yes" else "no"}, " +
                                    "httoken=${if (captured.httoken != null) "yes" else "no"}, " +
                                    "nttpid=${if (captured.remixnttpid != null) "yes" else "no"}, " +
                                    "uacck=${if (captured.remixuacck != null) "yes" else "no"}, " +
                                    "uas=${if (captured.remixuas != null) "yes" else "no"}, " +
                                    "dmgr=${if (captured.remixdmgr != null) "yes" else "no"}, " +
                                    "mvkfp=${if (captured.remixmvkFp != null) "yes" else "no"}) — " +
                                    "Path 1.5 enabled (cross-IP silent refresh, full cookie-set)")
                            } else {
                                AppLog.w(TAG, "remixsid NOT found in CookieManager after OAuth — " +
                                    "Path 1.5 will rely on backfill/RemixsidCapturer")
                            }
                            // #CALLS (2026-08-24): получаем $-токен (calls_token_with_url)
                            // сразу после логина, как m.vk.ru SPA — когда VK доверяет
                            // свежей сессии и cookies уже в CookieManager (капча не нужна).
                            // Сохраняем в prefs callsCallToken для auth.anonymLogin (version=3).
                            val cookieHeader = RemixsidCapturer.buildVkCookieHeader()
                            if (cookieHeader.isNotBlank()) {
                                val app = SovaApp.get(this@OAuthWebViewActivity)
                                app.appScope.launch {
                                    val callToken = app.apiClient.getCallToken(token, cookieHeader)
                                    if (!callToken.isNullOrBlank()) {
                                        app.prefs.setCallsCallToken(callToken)
                                        AppLog.i(TAG, "getCallToken OK, \$-токен сохранён (len=${callToken.length})")
                                    } else {
                                        AppLog.w(TAG, "getCallToken вернул null (авторизация/капча?)")
                                    }
                                }
                            } else {
                                AppLog.w(TAG, "getCallToken: нет cookies в CookieManager — \$-токен не получен")
                            }
                            setResult(RESULT_OK, Intent().apply {
                                putExtra(EXTRA_ACCESS_TOKEN, token)
                                putExtra(EXTRA_USER_ID, userId)
                                // §57: передаём весь cookie-set (9 кук) для
                                // cross-IP silent refresh. null = не передаём
                                // (AuthActivity.saveOAuthToken patch'ит только переданное).
                                if (captured != null) {
                                    putExtra(EXTRA_REMIXSID, captured.remixsid)
                                    captured.pCookie?.let { putExtra(EXTRA_P_COOKIE, it) }
                                    captured.remixnsid?.let { putExtra(EXTRA_REMIXNSID, it) }
                                    // §55 #SSO-FULL-COOKIE-SET: 6 доп. кук.
                                    captured.httoken?.let { putExtra(EXTRA_HTTP_TOKEN, it) }
                                    captured.remixnttpid?.let { putExtra(EXTRA_REMIX_NTTPID, it) }
                                    captured.remixuacck?.let { putExtra(EXTRA_REMIX_UACCK, it) }
                                    captured.remixuas?.let { putExtra(EXTRA_REMIX_UAS, it) }
                                    captured.remixdmgr?.let { putExtra(EXTRA_REMIX_DMGR, it) }
                                    captured.remixmvkFp?.let { putExtra(EXTRA_REMIX_MVK_FP, it) }
                                }
                            })
                            finish()
                        },
                        onError = { error ->
                            AppLog.e(TAG, "OAuth error: $error")
                            setResult(RESULT_CANCELED, Intent().apply {
                                putExtra(EXTRA_ERROR, error)
                            })
                            finish()
                        },
                        onBack = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                    )
                    // FAB поверх WebView — управляется настройкой showLogFab.
                    if (showLogFab) {
                        DraggableLogFab(onClick = { LogDialogState.show() })
                    }
                }
                LogViewerDialog()
            }
        }
    }

    private fun buildOAuthUrl(): String {
        // #33: Web Token Exchange flow — client_id из AuthDomainsConfig
        // (по умолчанию 6287487, vk.com desktop web). Возвращает vk1.a.XXX
        // токен, который VK API trustит без sig.
        // display=page (веб-формат).
        // Audit #40: v=BuildConfig.VK_API_VERSION (5.269).
        //
        // Fix #189: домен oauthHost и client_id из AuthDomainsConfig.
        // Fix #188: silent sign-in если forceRevoke=false (по умолчанию).
        val clientId = re.pinok.auth.exchange.AuthDomainsConfig.webClientId()
        val silent = !re.pinok.auth.exchange.AuthDomainsConfig.current.forceRevoke
        return re.pinok.auth.exchange.AuthDomainsConfig.oauthAuthorizeUrl(
            clientId = clientId,
            scope = OAUTH_SCOPE,
            redirectUri = REDIRECT_URI,
            apiVersion = BuildConfig.VK_API_VERSION,
            silent = silent,
        )
    }

    companion object {
        const val TAG = "OAuthWebView"

        /** vk.com desktop web client_id — из BuildConfig.VK_WEB_CLIENT_ID (audit Medium #1).
         *
         *  #33: Используем официальный веб-клиент VK (только first-party app_id).
         *
         *  6287487 — официальный веб-клиент vk.com. Возвращает vk1.a.XXX токен
         *  с web-scope (биты 52+53+54 в anonym JWT). VK API trustит этому
         *  токену как официальному → все methods работают БЕЗ sig и БЕЗ
         *  user_secret.
         *
         *  Источник: docs/references/ВК_веб_токены_референс.txt (main ветка).
         *
         *  Не подпадает под парольный flood_control — логин в VK-форме
         *  на VK-домене, а не через grant_type=password.
         *
         *  audit Medium #1: унифицирован с WebTokenAuth.WEB_APP_ID — оба читают
         *  из единого BuildConfig.VK_WEB_CLIENT_ID (ранее дублировался хардкод
         *  "6287487" тут и в WebTokenAuth.kt). */
        val OAUTH_CLIENT_ID: String get() = BuildConfig.VK_WEB_CLIENT_ID

        /** Scopes — полный web-scope как у vk.com (из ВК.txt).
         *  Включает все permissions: messages, audio, video, docs, wall,
         *  groups, stats, email, market, notifications, stories, pages, etc.
         *  offline — для долгоживущих токенов. */
        const val OAUTH_SCOPE = "notify,friends,photos,audio,video,stories," +
            "pages,links,status,notes,messages,wall,ads,offline,docs," +
            "groups,notifications,stats,email,market"

        /**
         * Fix #189: redirect URI — домен из AuthDomainsConfig (oauth.vk.com / oauth.vk.ru).
         * Computed property — обновляется при смене домена в шестерёнке.
         */
        val REDIRECT_URI: String
            get() = re.pinok.auth.exchange.AuthDomainsConfig.oauthBlankRedirectUrl()

        /** #35: Chrome browser User-Agent для OAuth WebView.
         *
         *  VK редиректит oauth.vk.com/authorize → id.vk.com/auth (VK ID flow).
         *  Если UA = "VKAndroidApp/..." (как у VkUserAgent.get()), VK ID фронтенд
         *  пытается вызвать Android-specific auth-методы, которых нет для
         *  client_id=6287487 (web-клиент) → error 3 "Unknown method passed"
         *  прямо на странице входа.
         *
         *  Chrome UA → VK ID использует стандартный web-flow (форма телефона/пароля),
         *  который работает для любого client_id.
         *
         *  VkUserAgent (VKAndroidApp) нужен ТОЛЬКО для API-вызовов api.vk.com/method
         *  в VKApiClient — там он предотвращает error 15 на messages и wall.
         *  Для веб-страниц (OAuth, id.vk.com) он ВРЕДЕН. */
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; HOTWAV Cyber 15) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        const val EXTRA_ACCESS_TOKEN = "access_token"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_ERROR = "error"

        /** #REMIXSID-CAPTURE (§41.22): remixsid, захваченный из CookieManager
         *  после OAuth WebView flow. Передаётся в AuthActivity, затем в
         *  AuthViewModel.submitOAuthToken → ExchangeAuthRepository.saveOAuthToken.
         *
         *  null если remixsid не найден (VK не поставил cookie, или
         *  CookieManager изолирован). В этом случае AuthViewModel запустит
         *  RemixsidCapturer как best-effort fallback. */
        const val EXTRA_REMIXSID = "remixsid"

        /** #SESSION-COOKIES (2026-08-04): p cookie — persistent login (.login.vk.ru).
         *  Критичен для cross-IP silent refresh: без него VK отвергает
         *  silentRefreshViaRemixsid после смены сети. */
        const val EXTRA_P_COOKIE = "p_cookie"

        /** #SESSION-COOKIES (2026-08-04): remixnsid — новая VK ID сессия (vk1.a.*). */
        const val EXTRA_REMIXNSID = "remixnsid"

        // §55 #SSO-FULL-COOKIE-SET / §57 #COOKIE-CAPTURE-UNIFY: 6 доп. кук
        // браузерного набора. VK login.vk.ru/?act=web_token валидирует полный
        // cookie-set — без них silent refresh падает (SSO loop §54).
        /** httoken — anti-CSRF (.vk.ru + .web.api.vk.ru). */
        const val EXTRA_HTTP_TOKEN = "httoken"
        /** remixnttpid — новая VK ID сессия (vk1.a.*, .vk.ru). */
        const val EXTRA_REMIX_NTTPID = "remixnttpid"
        /** remixuacck — user access check key (.vk.ru). */
        const val EXTRA_REMIX_UACCK = "remixuacck"
        /** remixuas — user auth signature, base64 (.vk.ru). */
        const val EXTRA_REMIX_UAS = "remixuas"
        /** remixdmgr — device manager hash, anti-fraud (.vk.ru). */
        const val EXTRA_REMIX_DMGR = "remixdmgr"
        /** remixmvk-fp — mobile VK fingerprint (.vk.ru). */
        const val EXTRA_REMIX_MVK_FP = "remixmvkfp"
    }

    // §57 #COOKIE-CAPTURE-UNIFY: captureSessionCookiesFromCookieManager() удалена.
    // Единственная точка чтения CookieManager — RemixsidCapturer.snapshotCookies().
    // OAuthWebViewActivity теперь вызывает её напрямую (см. onTokenReceived выше),
    // что устраняет 3-ю копию логики парсинга (устаревшую, 3 куки/7 доменов).
    // Backwards-compat: old captures can\u2019t happen — код вызывается только в
    // onCreate текущего процесса.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OAuthWebViewScreen(
    url: String,
    onTokenReceived: (token: String, userId: Long) -> Unit,
    onError: (String) -> Unit,
    onBack: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("Вход в VK") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // #35: Browser UA (Chrome), НЕ VKAndroidApp!
                        // Раньше использовался VkUserAgent.get() → VKAndroidApp/2.0.0-debug-1...
                        // VK редиректит oauth.vk.com/authorize → id.vk.com/auth (новый VK ID flow).
                        // VK ID фронтенд видит "VKAndroidApp" UA → пытается вызвать Android-specific
                        // auth-методы (auth.validatePhone и т.д.), которых НЕТ для client_id=6287487
                        // (это web-клиент, не Android) → error 3 "Unknown method passed" на странице входа.
                        //
                        // Браузерный UA заставляет VK ID использовать стандартный web-flow (форма ввода
                        // телефона/пароля), который работает для любого client_id.
                        //
                        // VkUserAgent (VKAndroidApp) нужен ТОЛЬКО для прямых API-вызовов (api.vk.com/method)
                        // через OkHttp в VKApiClient — там он предотвращает error 15.
                        settings.userAgentString = OAuthWebViewActivity.CHROME_UA
                        // Fix #184: явная UTF-8 кодировка (см. комментарий в AuthActivity).
                        @Suppress("DEPRECATION")
                        settings.defaultTextEncodingName = "UTF-8"

                        webViewClient = object : WebViewClient() {

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val reqUrl = request.url.toString()
                                AppLog.d(OAuthWebViewActivity.TAG, "navigate: $reqUrl")

                                // Проверяем redirect на blank.html — это значит токен получен.
                                if (reqUrl.startsWith(OAuthWebViewActivity.REDIRECT_URI)) {
                                    parseTokenFromUrl(reqUrl, onTokenReceived, onError)
                                    return true
                                }

                                // Проверяем error redirect.
                                if (reqUrl.contains("error=")) {
                                    val error = request.url.getQueryParameter("error")
                                        ?: "Unknown error"
                                    val desc = request.url.getQueryParameter("error_description")
                                    onError("$error: $desc")
                                    return true
                                }

                                return false
                            }

                            override fun onPageStarted(view: WebView, urlParam: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, urlParam, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, urlParam: String?) {
                                super.onPageFinished(view, urlParam)
                                isLoading = false
                                progress = 100
                                title = view.title ?: "Вход в VK"
                                // #CALLS-FIX: если redirect на blank.html прошёл через onPageFinished
                                // (а не shouldOverrideUrlLoading) — токен в urlParam, обрабатываем.
                                if (urlParam?.startsWith(OAuthWebViewActivity.REDIRECT_URI) == true) {
                                    AppLog.i(OAuthWebViewActivity.TAG, "onPageFinished: токен в URL — обрабатываем")
                                    parseTokenFromUrl(urlParam, onTokenReceived, onError)
                                    return
                                }
                                // #CALLS-AUTOCLICK (временный): на id.vk.ru/auth автоматически
                                // нажимаем кнопку подтверждения входа, чтобы flow дошёл до
                                // onTokenReceived → getCallToken (получение $-токена).
                                if (urlParam?.contains("id.vk.ru/auth") == true) {
                                    try {
                                        val autoClickJs = """
                                            (function(){
                                              var out=[];
                                              var btns = document.querySelectorAll('button, [role="button"], a');
                                              for (var i=0;i<btns.length;i++){
                                                var t=(btns[i].textContent||'').trim();
                                                if(t.length<60&&t.length>0) out.push(t);
                                              }
                                              var targets = ['Продолжить','Войти','Войти в VK','Продолжить вход','Войти в аккаунт','Разрешить','Подтвердить'];
                                              for (var i=0;i<btns.length;i++){
                                                var t=(btns[i].textContent||'').trim();
                                                for (var j=0;j<targets.length;j++){
                                                  if (t===targets[j]||t.indexOf(targets[j])===0){
                                                    btns[i].click();
                                                    return 'clicked:'+t;
                                                  }
                                                }
                                              }
                                              var body = document.body? document.body.innerText.replace(/\s+/g,' ') : '';
                                              var loc = window.location.href || '';
                                              return 'no-btn:'+out.join('|')+' || page:'+body+' || loc:'+loc;
                                            })();
                                        """.trimIndent()
                                        view.evaluateJavascript(autoClickJs) { r ->
                                            android.util.Log.i("OAuthWebViewActivity", "autoclick result: $r")
                                            val tok = extractAccessTokenFromJs(r)
                                            if (tok != null) {
                                                AppLog.i(OAuthWebViewActivity.TAG, "токен извлечён (len=${tok.length})")
                                                val uid = try {
                                                    SovaApp.get(view.context).exchangeAuthRepository.userId()
                                                } catch (e: Exception) { 0L }
                                                onTokenReceived(tok, uid)
                                            } else {
                                                view.postDelayed({
                                                    view.evaluateJavascript(autoClickJs) { r2 ->
                                                        android.util.Log.i("OAuthWebViewActivity", "autoclick retry2: $r2")
                                                        val tok2 = extractAccessTokenFromJs(r2)
                                                        if (tok2 != null) {
                                                            val uid = try {
                                                                SovaApp.get(view.context).exchangeAuthRepository.userId()
                                                            } catch (e: Exception) { 0L }
                                                            onTokenReceived(tok2, uid)
                                                        } else {
                                                            view.postDelayed({
                                                                view.evaluateJavascript(autoClickJs) { r3 ->
                                                                    android.util.Log.i("OAuthWebViewActivity", "autoclick retry3: $r3")
                                                                    val tok3 = extractAccessTokenFromJs(r3)
                                                                    if (tok3 != null) {
                                                                        val uid = try {
                                                                            SovaApp.get(view.context).exchangeAuthRepository.userId()
                                                                        } catch (e: Exception) { 0L }
                                                                        onTokenReceived(tok3, uid)
                                                                    }
                                                                }
                                                            }, 4000)
                                                        }
                                                    }
                                                }, 3000)
                                            }
                                        }
                                        android.util.Log.i("OAuthWebViewActivity", "autoclick injected on id.vk.ru/auth")
                                    } catch (e: Exception) {
                                        android.util.Log.w("OAuthWebViewActivity", "autoclick failed: ${e.message}")
                                    }
                                }
                            }
                        }

                        setWebChromeClient(object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) isLoading = false
                            }
                        })

                        loadUrl(url)
                    }
                },
                // ВАЖНО: без onRelease WebView утекает — держит потоки и контекст Activity.
                // audit High #3: добавлен destroy в onRelease.
                onRelease = { web ->
                    web.apply {
                        stopLoading()
                        removeJavascriptInterface("AccessibilityBridge")
                        removeJavascriptInterface("clipboard")
                        destroy()
                    }
                    AppLog.d(OAuthWebViewActivity.TAG, "WebView released")
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Loading overlay.
            if (isLoading && progress < 20) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Парсит access_token и user_id из URL redirect.
 * VKoffline делает то же самое через regex на hash:
 *   token = hash.match(/access_token=([a-z0-9_]+)/)
 *   userId = hash.match(/user_id=([\d]+)/)
 */
private fun parseTokenFromUrl(
    url: String,
    onTokenReceived: (token: String, userId: Long) -> Unit,
    onError: (String) -> Unit,
) {
    // URL формата: https://oauth.vk.com/blank.html#access_token=...&user_id=...&expires_in=...
    val fragment = url.substringAfter('#', "")
    if (fragment.isBlank()) {
        // Возможно error в query string.
        val error = android.net.Uri.parse(url).getQueryParameter("error")
        if (error != null) {
            onError(error)
        } else {
            onError("Пустой ответ от VK OAuth")
        }
        return
    }

    val params = android.net.Uri.parse("?$fragment") // префикс ? чтобы парсер работал с #params

    val token = params.getQueryParameter("access_token")
    val userIdStr = params.getQueryParameter("user_id")
    val error = params.getQueryParameter("error")
    val errorDesc = params.getQueryParameter("error_description")

    if (error != null) {
        onError("$error: $errorDesc")
        return
    }

    if (token.isNullOrBlank() || userIdStr.isNullOrBlank()) {
        onError("Нет токена или user_id в ответе VK")
        return
    }

    val userId = userIdStr.toLongOrNull()
    if (userId == null) {
        onError("Неверный формат user_id: $userIdStr")
        return
    }

    onTokenReceived(token, userId)
}

/**
 * #CALLS (2026-08-24): извлекает access_token из результата JS evaluateJavascript
 * (строка вида `...page:Webpage not available ... blank.html#access_token=vk1.a.XXX ...`).
 * Устойчив к HTML-сущностям и обрезкам.
 */
private fun extractAccessTokenFromJs(result: String?): String? {
    if (result == null) return null
    android.util.Log.i("OAuthWebViewActivity", "extract input len=${result.length}: ${result.take(120)}")
    // vk1.a.* — надёжный маркер web-токена; устойчив к HTML-сущностям.
    val m = Regex("vk1\\.a\\.[A-Za-z0-9_.\\-]{40,}").find(result)
    if (m != null) {
        val t = m.value.trim().trim('"')
        android.util.Log.i("OAuthWebViewActivity", "extract found vk1.a len=${t.length}")
        return t.takeIf { it.length > 20 }
    }
    // fallback: access_token=XXX
    val m2 = Regex("access_token=([A-Za-z0-9_.\\-]+)").find(result)
    val r2 = m2?.groupValues?.get(1)
    android.util.Log.i("OAuthWebViewActivity", "extract fallback: ${if (r2 != null) "found len=${r2.length}" else "null"}")
    return r2?.takeIf { it.length > 20 }
}