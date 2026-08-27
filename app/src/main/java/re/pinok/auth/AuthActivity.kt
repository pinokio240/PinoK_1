package re.pinok.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import re.pinok.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import re.pinok.SovaApp
import re.pinok.BuildConfig
import re.pinok.auth.exchange.AuthErrorKind
import re.pinok.auth.exchange.AuthDomainsConfig
import re.pinok.auth.exchange.AuthState
import re.pinok.auth.exchange.ExternalBrowserAuth
import re.pinok.auth.exchange.RemixsidCapturer
import re.pinok.auth.exchange.SilentTokenExchanger
import re.pinok.auth.exchange.ValidationType
import re.pinok.auth.exchange.WebTokenAuth
import re.pinok.ui.components.DraggableLogFab
import re.pinok.ui.components.LogDialogState
import re.pinok.ui.components.LogViewerDialog
import re.pinok.ui.theme.SOVATheme
import re.pinok.util.AppLog

/**
 * Auth activity — WebView m.vk.ru (primary) + Direct Auth (deep fallback).
 *
 * **Основной flow:** WebView грузит `m.vk.ru` — пользователь логинится
 * через стандартную форму VK (включая 2FA через VK ID). CookieManager получает
 * `remixsid`. JavaScript m.vk.ru автоматически обменивает remixsid на токен
 * через login.vk.com и сохраняет его в localStorage.
 * Затем [WebTokenAuth.fullAuthFlow] читает токен из localStorage через
 * evaluateJavascript() — никаких прямых HTTP-запросов к login.vk.com.
 *
 * **Deep fallback:** Direct Auth — phone+password через POST
 * oauth.vk.com/access_token с grant_type=password, client_id=2274003 (VK Android).
 * Возвращает access_token + user_secret → sig. Подвержен flood_control.
 * Доступен через "Вход по паролю" на landing screen.
 *
 * См. [WebTokenAuth] (localStorage token reading),
 *     [AuthViewModel.submitWebToken] (обработка результата),
 *     [ExchangeAuthRepository.signIn] (Direct Auth deep fallback),
 *     [OAuthWebViewActivity] (устаревший OAuth flow).
 */
class AuthActivity : ComponentActivity() {

    @Suppress("DEPRECATION")
    private val viewModel: AuthViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = SovaApp.get(this@AuthActivity)
                AuthViewModel(app.exchangeAuthRepository)
            }
        }
    }

    /** ActivityResult контракт для запуска OAuthWebViewActivity.
     *  Возвращает access_token + user_id (success) или error message (cancel). */
    private val oauthWebViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val token = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_ACCESS_TOKEN)
            val userId = result.data?.getLongExtra(OAuthWebViewActivity.EXTRA_USER_ID, 0L) ?: 0L
            // #REMIXSID-CAPTURE / #SESSION-COOKIES (§41.22): session cookies из
            // CookieManager, захваченные в OAuthWebViewActivity после web-логина.
            // null если не найдены — AuthViewModel запустит RemixsidCapturer как
            // best-effort fallback.
            //
            // #SESSION-COOKIES: p + remixnsid критичны для cross-IP silent refresh.
            // §55 #SSO-FULL-COOKIE-SET / §57 #COOKIE-CAPTURE-UNIFY: 6 доп. кук
            // (httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvk-fp) —
            // VK login.vk.ru/?act=web_token валидирует полный cookie-set, без них
            // silent refresh часто падает (SSO loop §54).
            val remixsid = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIXSID)
            val pCookie = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_P_COOKIE)
            val remixnsid = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIXNSID)
            val httoken = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_HTTP_TOKEN)
            val remixnttpid = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIX_NTTPID)
            val remixuacck = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIX_UACCK)
            val remixuas = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIX_UAS)
            val remixdmgr = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIX_DMGR)
            val remixmvkFp = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_REMIX_MVK_FP)
            if (!token.isNullOrBlank() && userId > 0L) {
                AppLog.i(TAG, "OAuth WebView returned token for user_id=$userId, " +
                    "remixsid=${if (remixsid != null) "yes" else "no"}, " +
                    "p=${if (pCookie != null) "yes" else "no"}, " +
                    "remixnsid=${if (remixnsid != null) "yes" else "no"}, " +
                    "httoken=${if (httoken != null) "yes" else "no"}, " +
                    "nttpid=${if (remixnttpid != null) "yes" else "no"}, " +
                    "uacck=${if (remixuacck != null) "yes" else "no"}, " +
                    "uas=${if (remixuas != null) "yes" else "no"}, " +
                    "dmgr=${if (remixdmgr != null) "yes" else "no"}, " +
                    "mvkfp=${if (remixmvkFp != null) "yes" else "no"}")
                viewModel.submitOAuthToken(
                    token, userId, remixsid, pCookie, remixnsid,
                    httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvkFp,
                )
            } else {
                AppLog.w(TAG, "OAuth WebView RESULT_OK but no token/user_id in extras")
                viewModel.setOAuthError("Вход через WebView завершился без токена. Попробуйте ещё раз.")
            }
        } else {
            val err = result.data?.getStringExtra(OAuthWebViewActivity.EXTRA_ERROR)
            if (!err.isNullOrBlank()) {
                AppLog.w(TAG, "OAuth WebView cancelled/error: $err")
                viewModel.setOAuthError("WebView: $err")
            } else {
                AppLog.d(TAG, "OAuth WebView cancelled by user")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fix #339: в silent mode применяем transparent theme ДО super.onCreate и
        // enableEdgeToEdge — иначе windowBackground из манифеста успеет нарисоваться.
        // windowIsTranslucent=true + прозрачный фон → под Activity виден предыдущий
        // кадр MainActivity, а WebView работает в невидимом слое.
        val silentMode = intent?.getBooleanExtra(EXTRA_SILENT_MODE, false) ?: false
        if (silentMode) {
            setTheme(R.style.Theme_PinoK_Silent)
            AppLog.i(TAG, "onCreate — SILENT mode (transparent theme, Fix #339)")
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i(TAG, "onCreate — WebView m.vk.ru (primary) + Direct Auth (deep fallback)")
        // #P2-8: VkAuthWebViewFactoryState.reset() удалён вместе с мёртвым V1.

        // Fix #176 УДАЛЕН (2026-08-06, #WEBVIEW-FIRST-LOAD-HANG):
        // evictAll() основан на ЛОЖНОЙ посылке «WebView использует OkHttp client».
        // На самом деле WebView использует собственный Chromium network stack
        // (нет shouldInterceptRequest → OkHttp не перехватывает запросы WebView).
        // evictAll бесполезен для загрузки m.vk.ru, но нагружает startup
        // (Dispatchers.IO + lifecycleScope) и может ломать in-flight запросы
        // ExchangeAuthRepository (который ДЕЙСТВИТЕЛЬНО использует OkHttp).
        // Реальный фикс первого зависания — reload safety-net в polling loop
        // (см. #WEBVIEW-FIRST-LOAD-HANG ниже): если onPageStarted не сработал
        // за 6 сек (cr_ChildProcessConn failed), вызываем reload().

        // Fix #197: защита от авто-входа после logout через stale clipboard.
        // Сценарий: юзер вошёл через внешний браузер → в буфере остался URL
        // с access_token → юзер жмёт «Выйти» → AuthActivity запускается →
        // onWindowFocusChanged (Fix #195b) находит токен в буфере → входит
        // обратно. MainActivity пытается очистить буфер при logout, но если
        // это не сработало (permission, старый Android) — запасной вариант:
        // помечаем текущий буфер как «already processed» при onCreate, чтобы
        // onWindowFocusChanged его не обработал. Юзер должен САМ скопировать
        // новый токен (или Share → PinoK) для входа — старый не подхватится.
        try {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            if (cm != null && cm.hasPrimaryClip()) {
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                if (text != null && containsOAuthToken(text)) {
                    lastProcessedClipFingerprint = text.hashCode()
                    AppLog.i(TAG, "onCreate: clipboard already contains OAuth token — marked as processed (Fix #197). User must copy fresh token or Share to login.")
                }
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "onCreate: clipboard pre-check failed: ${e.message}")
        }

        setContent {
            SOVATheme {
                // Fix #107: silent re-login режим — запускается MainActivity при
                // tokenInvalidationTick, если в storage есть remixsid.
                val silentMode = intent?.getBooleanExtra(EXTRA_SILENT_MODE, false) ?: false
                if (silentMode) {
                    AppLog.i(TAG, "Silent re-login mode (Fix #107) — auto WebView, no landing")
                }
                // Fix #237: читаем showLogFab из SovaPrefs (для показа FAB логов).
                val snap by SovaApp.get(this).prefs.data.collectAsState(initial = null)
                // Локальный захват: snap — delegated property (by collectAsState),
                // smart cast невозможен. Захватываем в обычную val для null-проверки.
                val snapLocal = snap
                val showLogFab = if (snapLocal != null) snapLocal.showLogFab else BuildConfig.DEBUG
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AuthScreen(
                            viewModel = viewModel,
                            silentMode = silentMode,
                            onSuccess = {
                                AppLog.i(TAG, "Auth success — finishing with RESULT_OK")
                                PendingAuthResult.clear()
                                setResult(RESULT_OK)
                                finish()
                            },
                            onCancel = {
                                AppLog.i(TAG, "Auth cancelled")
                                PendingAuthResult.clear()
                                setResult(RESULT_CANCELED)
                                finish()
                            },
                            onOfflineMode = {
                                // #34: Пользователь выбрал «Офлайн-режим» — завершаем
                                // AuthActivity с кастомным результатом. MainActivity
                                // покажет OfflineManagerScreen в guest-режиме (без токена).
                                AppLog.i(TAG, "Offline mode requested — finishing with RESULT_OFFLINE_MODE")
                                PendingAuthResult.clear()
                                setResult(RESULT_OFFLINE_MODE)
                                finish()
                            },
                            onLaunchWebView = {
                                AppLog.i(TAG, "Launching OAuth WebView (vk.com web client_id=6287487)")
                                oauthWebViewLauncher.launch(
                                    Intent(this@AuthActivity, OAuthWebViewActivity::class.java)
                                )
                            },
                            // Fix #187/#190: запуск внешнего браузера (Chrome/Яндекс) для OAuth.
                            // Fix #190: redirect_uri=blank.html (sova2://oauth НЕ работает с
                            // client_id=6287487). После входа юзер копирует URL из адресной
                            // строки браузера и вставляет в поле на LandingScreen.
                            onLaunchExternalBrowser = {
                                AppLog.i(TAG, "Launching external browser for OAuth (blank.html redirect, Fix #190)")
                                re.pinok.auth.exchange.ExternalBrowserLauncher.launch(this@AuthActivity)
                            },
                        )
                    }
                    // Floating log button — управляется настройкой showLogFab.
                    if (showLogFab) {
                        DraggableLogFab(onClick = { LogDialogState.show() })
                    }
                }
                // Global log viewer dialog — overlays the auth screen.
                LogViewerDialog()
            }
        }
    }

    /**
     * Fix #191: onResume — проверка токена (scenario 1).
     *
     * Сценарий 1: пользователь нажал «Войти через Яндекс/Chrome», выбрал
     * браузер, в браузере нажал «Поделиться» → PinoK. MainActivity получил
     * share intent, сохранил токен. AuthActivity была в фоне. При возврате
     * (Recent → PinoK) — onResume видит токен → finish(RESULT_OK).
     *
     * Сценарий 2 (clipboard auto-detection) перенесён в onWindowFocusChanged
     * (Fix #195b) — там app точно foreground и ClipboardService разрешит чтение.
     *
     * Сценарий 3 (fallback): ручная вставка через поле на LandingScreen.
     */
    override fun onResume() {
        super.onResume()
        val app = SovaApp.get(this)

        // Сценарий 1: токен уже сохранён (через share intent в MainActivity).
        if (app.tokenStorage.hasValidToken()) {
            AppLog.i(TAG, "onResume: token already present (likely from share intent) — finishing with RESULT_OK")
            PendingAuthResult.clear()
            setResult(RESULT_OK)
            finish()
            return
        }

        // #VK-SSO-PENDING-RESULT: токен мог быть получен через shouldOverrideUrlLoading
        // пока AuthActivity была уничтожена (старый viewModel cleared → submitOAuthToken
        // не выполнился). Проверяем singleton — если есть токен, обрабатываем на НОВОМ viewModel.
        PendingAuthResult.consume()?.let { result ->
            val token = result.first
            val uid = result.second
            AppLog.i(TAG, "onResume: PendingAuthResult found — processing token (user_id=$uid) on fresh viewModel")
            viewModel.submitOAuthToken(token, uid)
            return
        }
    }

    /**
     * Safety-net очистка PendingAuthResult при finish().
     * (PendingAuthResult хранит токен SSO-входа — silent_token / direct access_token
     * из shouldOverrideUrlLoading; чистим чтобы не было авто-входа после logout.)
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AppLog.i(TAG, "onDestroy(isFinishing=true) — clearing PendingAuthResult")
            PendingAuthResult.clear()
        }
    }

    /**
     * Fix #195b: clipboard auto-detection в onWindowFocusChanged.
     *
     * Раньше (Fix #191) чтение буфера было в onResume — но Android 10+
     * блокирует чтение буфера для приложений НЕ в foreground: «ClipboardService:
     * Denying clipboard access to re.pinok.debug, application is not in focus».
     * onResume вызывается ДО того как окно получает focus → отказ.
     *
     * onWindowFocusChanged(hasFocus=true) срабатывает когда окно РЕАЛЬНО
     * получило focus — app foreground, ClipboardService разрешит чтение.
     * На Android 12+ покажется тост «App pasted from clipboard» — это
     * нормальное поведение, не ошибка.
     *
     * На Android 10+ нет permission READ_CLIPBOARD для сторонних приложений —
     * foreground-статус это и есть «разрешение» (implicit, системное).
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return

        val app = SovaApp.get(this)

        // Сценарий 1: токен уже сохранён (через share intent в MainActivity).
        if (app.tokenStorage.hasValidToken()) {
            AppLog.i(TAG, "onWindowFocusChanged: token already present — finishing with RESULT_OK")
            setResult(RESULT_OK)
            finish()
            return
        }

        // Сценарий 2: clipboard auto-detection.
        try {
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.coerceToText(this)?.toString()
                    if (text != null && containsOAuthToken(text)) {
                        // Проверяем что этот буфер мы ещё не обрабатывали.
                        val fingerprint = text.hashCode()
                        if (lastProcessedClipFingerprint != fingerprint) {
                            lastProcessedClipFingerprint = fingerprint
                            AppLog.i(TAG, "onWindowFocusChanged: OAuth token found in clipboard — auto-saving (Fix #191/#195b)")
                            val payload = extractOAuthPayload(text)
                            if (payload != null) {
                                val parsed = parsePayloadToToken(payload)
                                if (parsed != null) {
                                    AppLog.i(TAG, "onWindowFocusChanged: clipboard token parsed — user_id=${parsed.first}, at=${parsed.second.take(12)}...")
                                    viewModel.submitOAuthToken(parsed.second, parsed.first)
                                    return
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "onWindowFocusChanged: clipboard check failed: ${e.message}")
        }
    }

    /**
     * Запоминаем fingerprint последнего обработанного clipboard-токена,
     * чтобы не входить повторно при следующем onWindowFocusChanged с тем же
     * буфером. Без этого каждый return в app (смена фокуса) вызывал бы вход.
     */
    private var lastProcessedClipFingerprint: Int = 0

    private fun containsOAuthToken(text: String): Boolean {
        return text.contains("access_token=", ignoreCase = true) &&
            text.contains("user_id=", ignoreCase = true)
    }

    private fun extractOAuthPayload(text: String): String? {
        val trimmed = text.trim()
        val hashIdx = trimmed.lastIndexOf('#')
        if (hashIdx >= 0 && hashIdx < trimmed.length - 1) {
            return trimmed.substring(hashIdx + 1)
        }
        val qIdx = trimmed.indexOf('?')
        if (qIdx >= 0 && qIdx < trimmed.length - 1) {
            return trimmed.substring(qIdx + 1)
        }
        if (trimmed.contains("access_token=")) return trimmed
        return null
    }

    /** Возвращает (userId, accessToken) или null. */
    private fun parsePayloadToToken(payload: String): Pair<Long, String>? {
        try {
            val params = payload.split("&").mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            val at = params["access_token"] ?: return null
            val uid = params["user_id"]?.toLongOrNull() ?: return null
            if (at.isBlank() || at.length < 10) return null
            return uid to at
        } catch (_: Exception) {
            return null
        }
    }

    companion object {
        const val TAG = "AuthActivity"

        /**
         * #34: Кастомный результат AuthActivity — пользователь выбрал
         * «Офлайн-режим» на LandingScreen. MainActivity получает этот код
         * через authLauncher и показывает OfflineManagerScreen без авторизации
         * (guest-режим: просмотр/воспроизведение уже скачанных аудио/видео).
         *
         * Значение 2 — первое доступное user-defined resultCode
         * (RESULT_OK=-1, RESULT_CANCELED=0, RESULT_FIRST_USER=1).
         */
        const val RESULT_OFFLINE_MODE = 2

        /**
         * Fix #107: Extra для silent re-login режима.
         *
         * Когда MainActivity ловит tokenInvalidationTick (error 5/1117),
         * и в storage есть сохранённый remixsid (Fix #106 сохранил его) —
         * AuthActivity запускается с этим extra=true.
         *
         * В silent mode:
         *   - LANDING screen пропускается, сразу WEBVIEW
         *   - VkAuthWebViewScreen автоматически запускает submitWebToken
         *     при обнаружении remixsid в CookieManager
         *   - Пользователь НЕ видит форму логина — всё происходит в фоне
         *
         * Если silent re-login не удаётся (remixsid тоже устарел) —
         * AuthActivity возвращается в LANDING для ручного входа.
         */
        const val EXTRA_SILENT_MODE = "silent_mode"

        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, AuthActivity::class.java))
        }
    }
}

private enum class AuthPhase { LANDING, WEBVIEW, TWO_FA, SUCCESS }

/** Результат проверки внешнего браузера — определяется один раз при показе LandingScreen. */
private data class BrowserAuthCheck(
    val found: Boolean,
    val remixsid: String?,
    val source: String?,
)

// WebView m.vk.ru — PRIMARY auth flow.
// Пользователь логинится в WebView → CookieManager получает remixsid →
// m.vk.ru JS автоматически получает web_token через login.vk.com и
// сохраняет его в localStorage → WebTokenAuth.fullAuthFlow() читает
// токен из localStorage → токен работает со всеми VK API methods без sig.
//
// Direct Auth (phone+password → oauth.vk.com/access_token, client_id=2274003) —
// DEEP FALLBACK, доступен через "Вход по паролю" на landing screen.

@Composable
private fun AuthScreen(
    viewModel: AuthViewModel,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onOfflineMode: () -> Unit,
    onLaunchWebView: () -> Unit,
    silentMode: Boolean = false,
    // Fix #187: запуск внешнего браузера (Chrome/Яндекс) для OAuth.
    // AuthActivity передаёт сюда лямбду, которая вызывает ExternalBrowserLauncher.
    onLaunchExternalBrowser: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    // Fix #190: показываем поле «Вставьте ссылку» после запуска внешнего
    // браузера. Пользователь копирует URL из адресной строки браузера
    // (https://oauth.vk.com/blank.html#access_token=...) и вставляет сюда.
    var showTokenPaste by rememberSaveable { mutableStateOf(false) }
    var tokenPasteError by rememberSaveable { mutableStateOf<String?>(null) }
    // Fix #191: ручная вставка — свёрнутая секция (fallback, если Share не сработал).
    var showManualPaste by rememberSaveable { mutableStateOf(false) }

    // Fix #107: в silent mode пропускаем LANDING — сразу WEBVIEW.
    // MainActivity запускает AuthActivity с EXTRA_SILENT_MODE=true когда
    // tokenInvalidationTick сработал и есть сохранённый remixsid (Fix #106).
    var phase by rememberSaveable(silentMode) {
        mutableStateOf(if (silentMode) AuthPhase.WEBVIEW else AuthPhase.LANDING)
    }
    var lastValidation by remember { mutableStateOf<AuthState.NeedValidation?>(null) }
    val scope = rememberCoroutineScope()

    // Auto-check CookieManager на remixsid — если пользователь ранее логинился
    // в WebView внутри PinoK, сессия найдётся → авто-переход в WEBVIEW для обмена.
    // На Android 7+ почти всегда false (cookies изолированы от настоящих браузеров).
    var browserAuth by remember {
        mutableStateOf<BrowserAuthCheck?>(BrowserAuthCheck(found = false, remixsid = null, source = null))
    }
    LaunchedEffect(Unit) {
        // Fix #107: в silent mode пропускаем — phase уже WEBVIEW, remixsid в storage.
        if (silentMode) {
            AppLog.d("AuthActivity", "Silent mode — skipping browser auth check (already in WEBVIEW phase)")
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            val ext = ExternalBrowserAuth.tryFindExistingAuth()
            BrowserAuthCheck(ext.found, ext.remixsid, ext.source)
        }
        browserAuth = result
        if (result.found) {
            AppLog.i("AuthActivity", "Сессия VK найдена в CookieManager (${result.source}) — автоматически входим через WebView")
            phase = AuthPhase.WEBVIEW
        } else {
            AppLog.d("AuthActivity", "CookieManager не содержит remixsid — это нормально на Android 7+ (cookies изолированы).")
        }
    }


    LaunchedEffect(state) {
        when (state) {
            is AuthState.NeedValidation -> {
                lastValidation = state as AuthState.NeedValidation
                phase = AuthPhase.TWO_FA
            }
            is AuthState.Success -> {
                phase = AuthPhase.SUCCESS
                onSuccess()
            }
            is AuthState.Idle -> {
                // User tapped "Back" from 2FA -> return to landing.
                if (phase == AuthPhase.TWO_FA) phase = AuthPhase.LANDING
            }
            is AuthState.Error -> {
                // Fix #107: silent re-login не удался (remixsid тоже устарел,
                // либо m.vk.ru не отдал свежий токен). Показываем LANDING —
                // пользователь введёт логин/пароль вручную.
                if (silentMode && phase == AuthPhase.WEBVIEW) {
                    val errKind = (state as AuthState.Error).kind
                    AppLog.w("AuthActivity", "Silent re-login failed ($errKind) — fallback to LANDING for manual login")
                    phase = AuthPhase.LANDING
                }
            }
            is AuthState.OfflineWithCache -> {
                // #NETWORK-RESILIENCE (2026-08-04): offline-first вход.
                // Токен протух, но сеть недоступна → silent refresh невозможен.
                // НЕ показываем LANDING (бесполезно — нет сети для логина).
                // Завершаем AuthActivity с RESULT_OK в offline-режиме —
                // MainActivity откроется, покажет кэшированную ленту + баннер.
                // При появлении сети — AuthActivity будет перезапущена (см.
                // NetworkObserver listener в MainActivity) и silent refresh
                // обновит токен без участия пользователя.
                val offline = state as AuthState.OfflineWithCache
                AppLog.i("AuthActivity", "OfflineWithCache — entering offline mode " +
                    "(user=${offline.cachedUserId}, tokenExpiredAt=${offline.tokenExpiredAt}). " +
                    "AuthActivity finishing with RESULT_OK; MainActivity will show cached data.")
                phase = AuthPhase.SUCCESS
                onSuccess()
            }
            else -> { /* Loading: keep current phase */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        when (phase) {
            // Fix #199: systemBarsPadding — иначе контент LandingScreen (и
            // шестерёнка в TopEnd) «ныряет» под status bar при edge-to-edge,
            // и тап по шестерёнке перехватывается системной панелью.
            AuthPhase.LANDING -> Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                LandingScreen(
                    state = state,
                    // #VKAUTH-V2 Primary CTA: переход к WebView m.vk.ru.
                    onStartWebView = { phase = AuthPhase.WEBVIEW },
                    // Fix #190: при запуске внешнего браузера показываем подсказку
                    // «Поделиться → PinoK» (Fix #191 — основной способ).
                    onLaunchExternalBrowser = {
                        onLaunchExternalBrowser()
                        showTokenPaste = true
                        tokenPasteError = null
                        showManualPaste = false
                    },
                    // Fix #190/#191: подсказка Share + свёрнутая ручная вставка.
                    showTokenPaste = showTokenPaste,
                    tokenPasteError = tokenPasteError,
                    onPasteToken = { pasted ->
                        val parsed = parseExternalBrowserToken(pasted)
                        if (parsed == null) {
                            tokenPasteError = "Не удалось найти access_token и user_id в вставленном тексте.\n" +
                                "Скопируйте полный URL из адресной строки браузера " +
                                "(начинается с https://oauth.vk.com/blank.html#...)."
                        } else {
                            tokenPasteError = null
                            AppLog.i("AuthActivity", "External browser token parsed: user_id=${parsed.userId}, token=${parsed.accessToken.take(12)}...")
                            viewModel.submitOAuthToken(parsed.accessToken, parsed.userId)
                        }
                    },
                    onCancelTokenPaste = {
                        showTokenPaste = false
                        tokenPasteError = null
                        showManualPaste = false
                    },
                    showManualPaste = showManualPaste,
                    onShowManualPaste = { showManualPaste = true },
                    onOfflineMode = onOfflineMode,
                    modifier = Modifier.fillMaxSize(),
                )
                // Fix #189: шестерёнка с настройками VK доменов — доступна
                // ДО авторизации. Размещена в правом верхнем углу LandingScreen.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 4.dp),
                ) {
                    re.pinok.auth.exchange.AuthDomainsSettingsIcon()
                }
            }
            AuthPhase.WEBVIEW -> VkAuthWebViewScreenV2(
                isLoading = state is AuthState.Loading,
                // #VKID-ONLY (vk.id.md F-apply): кнопка «Войти через VK» → старт
                // WebView сразу на VK ID entry (m.vk.ru/login?app_id=6287487).
                // VK ID SDK делает silent exchange если есть remixsid, иначе показывает
                // форму входа VK ID. Авторизация ТОЛЬКО через VK ID.
                startUrl = AuthDomainsConfig.vkIdLoginUrl(),
                onTokenExchange = { remixsid, cookies, webView ->
                    // #VKAUTH-V2: сохраняем полный cookie-set (9 remix-ключей) в storage
                    // ДО вызова submitWebToken. Path 1.5 (silentRefreshViaRemixsid)
                    // получит полный браузерный cookie-set → удержание сессии при смене IP.
                    if (remixsid.isNotBlank()) {
                        try {
                            val app = re.pinok.SovaApp.get()
                            val captured = RemixsidCapturer.CapturedCookies(
                                remixsid = remixsid,
                                pCookie = cookies.p,
                                remixnsid = cookies.remixnsid,
                                httoken = cookies.httoken,
                                remixnttpid = cookies.remixnttpid,
                                remixuacck = cookies.remixuacck,
                                remixuas = cookies.remixuas,
                                remixdmgr = cookies.remixdmgr,
                                remixmvkFp = cookies.remixmvkFp,
                            )
                            app.exchangeAuthRepository.saveRemixsid(captured)
                            AppLog.i("AuthActivity",
                                "#VKAUTH-V2: cookie-set сохранён в storage " +
                                "(remixsid len=${remixsid.length}, " +
                                "p=${if (cookies.p != null) "yes" else "no"}, " +
                                "remixnsid=${if (cookies.remixnsid != null) "yes" else "no"}, " +
                                "httoken=${if (cookies.httoken != null) "yes" else "no"}, " +
                                "nttpid=${if (cookies.remixnttpid != null) "yes" else "no"}, " +
                                "uacck=${if (cookies.remixuacck != null) "yes" else "no"}, " +
                                "uas=${if (cookies.remixuas != null) "yes" else "no"}, " +
                                "dmgr=${if (cookies.remixdmgr != null) "yes" else "no"}, " +
                                "mvkfp=${if (cookies.remixmvkFp != null) "yes" else "no"}) — " +
                                "Path 1.5 silentRefreshViaRemixsid получит полный cookie-set")
                        } catch (e: Exception) {
                            AppLog.w("AuthActivity",
                                "#VKAUTH-V2: не удалось сохранить cookie-set: ${e.message}")
                        }
                    }
                    // remixsid найден → m.vk.ru JS получил токен. Читаем web_token.
                    viewModel.submitWebToken(remixsid, webView)
                },
                onBack = {
                    phase = AuthPhase.LANDING
                },
                onCancel = {
                    viewModel.cancel()
                    phase = AuthPhase.LANDING
                },
                onOfflineMode = onOfflineMode,
                onSilentTokenExchanged = { accessToken, userId ->
                    AppLog.i("AuthActivity", "silent_token exchange УСПЕШЕН → user_id=$userId, сохраняем OAuth token")
                    // #SESSION-HOLD: silent_token (VK ID/SSO) даёт токен БЕЗ remixsid.
                    // Пытаемся захватить cookie-set из CookieManager — если WebView уже
                    // установил сессию (remixsid), передаём в submitOAuthToken, чтобы
                    // Path 1.5 (silentRefreshViaRemixsid) работала при смене сети.
                    val captured = RemixsidCapturer.snapshotCookies()
                    if (captured != null) {
                        AppLog.i("AuthActivity",
                            "silent_token: cookie-set захвачен (remixsid len=${captured.remixsid.length}, " +
                                "p=${if (captured.pCookie != null) "yes" else "no"}) — Path 1.5 enabled")
                        viewModel.submitOAuthToken(
                            accessToken, userId,
                            remixsid = captured.remixsid,
                            pCookie = captured.pCookie,
                            remixnsid = captured.remixnsid,
                            httoken = captured.httoken,
                            remixnttpid = captured.remixnttpid,
                            remixuacck = captured.remixuacck,
                            remixuas = captured.remixuas,
                            remixdmgr = captured.remixdmgr,
                            remixmvkFp = captured.remixmvkFp,
                        )
                    } else {
                        AppLog.w("AuthActivity",
                            "silent_token: cookie-set НЕ найден в CookieManager — " +
                                "Path 1.5 недоступна (нужен in-app WebView login для silent network switch)")
                        viewModel.submitOAuthToken(accessToken, userId)
                    }
                },
                silentMode = silentMode,
                modifier = Modifier.fillMaxSize(),
            )
            AuthPhase.TWO_FA -> ValidationCodeForm(
                validation = lastValidation,
                state = state,
                onSubmit = { code -> viewModel.submit2FaCode(code) },
                onResend = { via -> viewModel.resendCode(via) },
                onWithoutPassword = { viewModel.submitWithoutPassword() },
                onBack = { viewModel.cancel() },
                modifier = Modifier.fillMaxSize(),
            )
            AuthPhase.SUCCESS -> {
                // Brief splash before the activity finishes — gives the user feedback.
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Вход выполнен", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
        // DraggableLogFab рендерится снаружи в setContent, поверх всего.
    }
}


