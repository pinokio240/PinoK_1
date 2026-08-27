# AUTH_FULL_AUDIT.md

> **Дата:** 2026-08-06 session 5
> **Триггер:** пользователь жалуется «снова https://m.vk.ru не открывается при нажатии на кнопку "Войти через ВК"».
> **Объект аудита:** все файлы в `app/src/main/java/re/pinok/auth/` + `auth/exchange/` + `MainActivity.kt` (auth bootstrap) + `SovaApp.kt` (auth-related init) + `service/PlayerService.kt` + `media/PlayerConnection.kt` + `res/xml/network_security_config.xml`.
> **Цель:** исчерпывающий аудит — типы, классы, подклассы, вызовы, методы, скобки, комментарии, модели, модули, связанность, версии, зависимости, задержки, отсутствие задержек.

---

## 0. Исполнительная сводка (TL;DR для разработчика)

**Симптом:** при тапе «Войти через VK» на Landing → `phase = AuthPhase.WEBVIEW` → `VkAuthWebViewScreenV2` → `AndroidView { FixedInputWebView(ctx).apply { ... loadUrl(mWebUrl) } }` → страница m.vk.ru либо не грузится (белый экран), либо грузится, но ничего не происходит.

**Root causes (ранжированы по вероятности):**

| # | Причина | Где | Тип |
|---|---|---|---|
| **P0-1** | **`PlayerConnection.init(this)` вызывается в `SovaApp.onCreate:919` ВСЕГДА** — в т.ч. в auth flow. `init()` → `connectController()` → IPC bind к `PlayerService` → `PlayerService.onCreate()` блокирует main thread (ExoPlayer + MediaSession + AudioEffects, ~150–300мс). В это время Chromium пытается поднять рендерер → `cr_ChildProcessConn: Failed to establish the service connection` → `onPageStarted` NEVER fires → m.vk.ru не грузится. Fix #AUTH-WEBVIEW-STARVATION (MainActivity.kt:1272) skip'ит только `notifyResumed`, но НЕ `init`. | `SovaApp.kt:919` + `PlayerConnection.kt:215` | архитектурный |
| **P0-2** | **`WebChromeClient` НЕ установлен в `VkAuthWebViewScreenV2`** → `onProgressChanged` не работает, `onJsAlert/Confirm/Prompt` молча подавляются (VK ID SDK использует prompt/confirm), `onCreateWindow` не работает (`window.open` молча fail). Без `onProgressChanged` UI не получает прогресса загрузки и не может показать fallback. | `VkAuthWebViewScreenV2.kt:267-285` (factory) | бага |
| **P0-3** | **Нет safety-net для `onPageStarted`-не-сработал**. Variant A (commit `89a71efe5`) удалил `pageStartedRef`/`recreateAttemptedRef`/`webviewRecreateKey`/`recreate safety-net` (~50 строк). Если Chromium рендерер не подключается с первого раза — нет recovery, loading остаётся `true` бесконечно, пользователь видит прогресс-бар. | `VkAuthWebViewScreenV2.kt:200-260` (был safety-net, удалён) | бага |
| **P1-4** | **`CookieManager` НЕ очищается перед `loadUrl`**. Если в CookieManager остались stale cookies от прошлой сессии (logout не вычистил), polling мгновенно находит старый remixsid → `onTokenExchange` → `submitWebToken` → silent refresh fails (remixsid невалидный) → `AuthState.Error` → `phase = AuthPhase.LANDING`. Но если `silentMode` и `phase == AuthPhase.WEBVIEW` — Error → LANDING. Пользователь не видит «m.vk.ru», видит Landing снова. | `VkAuthWebViewScreenV2.kt:264-265` (только `setAccept*`, без `removeAllCookies`) | бага |
| **P1-5** | **`setLayerType(View.LAYER_TYPE_SOFTWARE, null)`** — software rendering для input fix, но на сложном SPA m.vk.ru может тормозить/зависать. На некоторых устройствах Chromium вообще не рендерит в software mode. | `VkAuthWebViewScreenV2.kt:255` | риск |
| **P1-6** | **Static UA `"Mozilla/5.0 (Linux; Android 13; HOTWAV Cyber 15) ... Chrome/131.0.0.0 Mobile"`** — `build.gradle.kts:46-50` явно предупреждает: «Статический хардкод здесь убран: некорректное содержимое приводило к error 15». Здесь он снова хардкожен, и на другом устройстве VK может вести себя странно. | `VkAuthWebViewScreenV2.kt:241-243` | риск |
| **P1-7** | **`network_security_config.xml` не содержит `.ru` домены** (`vk.ru`, `m.vk.ru`, `id.vk.ru`, `login.vk.ru`, `oauth.vk.ru`, `api.vk.ru`). `base-config` с trust-anchors system+user применяется ко всем, так что НЕ блокирует загрузку, но подозрительно (VK мигрирует на .ru, дефолтный `mobileWebHost = m.vk.ru`). | `res/xml/network_security_config.xml:9-22` | риск |
| **P2-8** | **Дублирование `VkAuthWebViewScreen`**: в `AuthActivity.kt:1944` остался СТАРЫЙ private composable `VkAuthWebViewScreen` (~1200 строк), который **НЕ вызывается** (сейчас вызывается V2 из `AuthActivity.kt:717`). Мёртвый код, спутывает ревью. | `AuthActivity.kt:1944-2800` | техдолг |
| **P2-9** | **Cookie polling каждые 1 сек** в `Dispatchers.Default` — стабильный CPU wakeup, раз в секунду, до 5 мин (300с). На Android Doze может мешать. | `VkAuthWebViewScreenV2.kt:184` | техдолг |
| **P2-10** | **`tryLaunchIntentUrl`/`tryLaunchCustomScheme`/`tryLaunchMarketUrl` дублированы** между `AuthActivity.kt` (private) и `VkAuthWebViewScreenV2.kt` (private, через `re.pinok.SovaApp.get()`). TODO в коде: «вынести tryLaunch* в отдельный object». | `VkAuthWebViewScreenV2.kt:627-660` | техдолг |

---

## 1. Карта файлов auth-слоя (24 файла, 14 955 строк)

### 1.1. `auth/` — UI-слой (6 файлов, 6 046 строк)

| Файл | Строк | Назначение |
|------|-------|-----------|
| `AuthActivity.kt` | **2 953** | ComponentActivity, Compose UI, фаза-машина `AuthPhase` (LANDING→WEBVIEW→TWO_FA→SUCCESS). Включает `AuthScreen` composable, `LandingScreen`, `ValidationCodeForm`, **мёртвый** `VkAuthWebViewScreen` (private, ~1200 строк), `PendingAuthResult` object, `VkAuthWebViewFactoryState` object, `getRemixSidFromCookieManager()`, clipboard auto-detection, external browser flow. |
| `AuthViewModel.kt` | 545 | ViewModel для Direct Auth + submitWebToken/submitOAuthToken/submit2FaCode. Проксирует `ExchangeAuthRepository`. |
| `FixedInputWebView.kt` | 140 | Custom WebView с `SovaInputConnection` (InputConnectionWrapper) — фикс «зеркального ввода» в VK ID React controlled inputs (Fix #74/#75). |
| `Formatters.kt` | 112 | `PhoneFormatter`, `humanizeError()`. |
| `OAuthWebViewActivity.kt` | 481 | **Устаревший** OAuth WebView flow (oauth.vk.com/authorize). Вызывается из `AuthActivity.onLaunchWebView`, НО на LandingScreen кнопка `onLaunchWebView` больше НЕ отображается (только `onStartWebView` = V2). Имеет `WebChromeClient` (правильно), в отличие от V2. |
| `VkAuthWebViewScreenV2.kt` | 661 | **АКТИВНЫЙ** WebView flow (m.vk.ru). Создаёт `FixedInputWebView`, грузит `AuthDomainsConfig.mobileWebUrl()`, polling remixsid каждую 1 сек, при найденном → `onTokenExchange` → `submitWebToken`. **Без WebChromeClient, без safety-net, без CookieManager cleanup.** |

### 1.2. `auth/exchange/` — auth-logic (18 файлов, 8 909 строк)

| Файл | Строк | Назначение |
|------|-------|-----------|
| `ExchangeAuthRepository.kt` | **3 058** | God-class. ~30 методов: `signIn`, `submit2FaCode`, `authWithoutPassword`, `signInByTrustedHash`, `signInByExternalService`, `saveOAuthToken`, `saveWebTokenResult`, `signInByWebToken`, `resendValidationCode`, `ensureFreshToken(force)`, `silentAuth`, `keepAlive`, `fetchLongPoll`, `tryTrustedHashLogin`, `signOut`, `silentRefreshViaRemixsid` (Path 1.5), `saveRemixsid`, `connectExchangeToken`, и др. |
| `ExchangeTokenStorage.kt` | 889 | EncryptedSharedPreferences-backed storage для access_token + exchange_token + user_id + device_id + LP creds + 9 session cookies. |
| `WebTokenAuth.kt` | 1 199 | Чтение web_token из m.vk.ru localStorage через `evaluateJavascript()`. Polling с таймаутами (LS_POLL_TIMEOUT_MS, SSO_RETURN_WAIT_MS, SSO_POST_REDIRECT_POLL_MS, EVALJS_TIMEOUT_MS). `connectExchangeToken()` через `login.vk.ru/?act=connect_exchange_token`. |
| `ExchangeAuthApi.kt` | 555 | OkHttp POST к `oauth.vk.com/access_token` + `id.vk.com/auth_by_exchange_token`. Grant_types: password, phone_confirmation_sid, without_password, trusted_hash, vk_external_auth, exchange_token. `validateWebToken()`, `getLongPollServer()`, `getExchangeTokenDetailed()`. |
| `SilentTokenExchanger.kt` | 501 | Perебор 5 endpoints для silent_token exchange (`auth.getAuthData`, `auth.getAnonymToken`, `id.vk.com/auth_by_silent_token`, `oauth.vk.com/access_token`, `execute`). Research-mode код. |
| `RemixsidCapturer.kt` | 439 | `snapshotCookies()` — единая точка чтения CookieManager для 9 session cookies (remixsid, p, remixnsid, httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvk-fp). |
| `AuthDomainsConfig.kt` | 380 | Object singleton с volatile `Snapshot(oauthHost, idHost, loginHost, mobileWebHost, apiHost, webClientId, forceRevoke)`. URL builders. Defaults: oauth.vk.com, id.vk.com, login.vk.com, **m.vk.ru**, api.vk.com, 6287487. |
| `VkAppIntentInspector.kt` | 382 | Разведка intent-filters VK app (com.vkontakte.android) — поиск недокументированных auth-deeplink'ов. |
| `ExchangeTokenExchanger.kt` | 352 | Path 4: `oauth.vk.com/auth_by_exchange_token` с follow 302 redirect. |
| `ExternalBrowserLauncher.kt` | 341 | Запуск внешнего браузера (Chrome/Яндекс) для OAuth implicit flow. |
| `ExternalBrowserAuth.kt` | 312 | `tryFindExistingAuth()` — проверка CookieManager на наличие remixsid (для авто-входа). |
| `AuthDomainsSettingsSheet.kt` | 464 | UI шестерёнки настроек VK доменов. |
| `AuthModels.kt` | 322 | `AuthState` sealed (Idle/Loading/NeedValidation/Error/Success/OfflineWithCache), `ValidationType`, `AuthErrorKind`, `ExchangeTokenResult`, `AuthResult` (20+ полей), `VkAuthCredentials`, `BanInfo`, `ValidateInfo`, `SendOtpInfo`, `UtilityTokens`, `LongPollCredentials`. |
| `CookieRefreshWorker.kt` | 138 | WorkManager periodic 6ч — фоновый cookie sync. |
| `VkAppDirectLauncher.kt` | 282 | Прямой запуск VK app для SSO. |
| `SessionDumpParser.kt` | 237 | Парсинг дампа localStorage + cookies (для «Импорт сессии»). |
| `AccountFileBackup.kt` | 118 | Backup/restore `account.json` (VTosters-style). |
| `AuthExceptions.kt` | 94 | `BannedUserException`, `DeactivatedUserException`, `ExchangeTokenException`, `NeedSilentAuthException`, `UnknownException`. |

### 1.3. Сопутствующие файлы вне `auth/`

| Файл | Связь с auth |
|------|--------------|
| `ui/MainActivity.kt` (1585 строк) | `launchAuth(reason)` (throttle 20с + #SSO-RECREATE-GUARD 90с), `notifyResumed` (skip если `!hasValidToken()` — Fix #AUTH-WEBVIEW-STARVATION), `tokenInvalidationTicks` StateFlow, clipboard token capture (Share intent), `network-restored-no-token` callback. |
| `SovaApp.kt` (1400+ строк) | `onCreate`: EncryptedSharedPreferences, SovaPrefs, TokenStorage, ExchangeTokenStorage, ExchangeAuthRepository, VKApiClient, **`PlayerConnection.init(this)` (строка 919)**, LongPollClient, MessageNotifier, NotificationsPoller, SecurityAlertsPoller, AuthDomainsConfig.update(snap). |
| `service/PlayerService.kt` (400+ строк) | `MediaSessionService`. `onCreate` (строка 306): heavy main-thread work — ExoPlayer + MediaSession + AudioEffects. Запускается IPC bind из `PlayerConnection.connectController`. |
| `media/PlayerConnection.kt` (1000+ строк) | Singleton. `init(context)` — вызывает `connectController` (MediaController.Builder.buildAsync → IPC bind к PlayerService). `notifyResumed()` — reconnect если controller null. `lastReconnectTs` guard (2с). |
| `res/xml/network_security_config.xml` | `cleartextTrafficPermitted="false"` + VK `.com` domains (vk.com, id.vk.com, oauth.vk.com, userapi.com, mycdn.me, vk-cdn.net, vk.me, vkontakte.ru). **НЕ содержит `.ru` домены.** |

---

## 2. Типы, классы, подклассы

### 2.1. AuthState (sealed interface) — `auth/exchange/AuthModels.kt:28`
```kotlin
sealed interface AuthState {
    data object Idle : AuthState
    data class Loading(val message: String? = null) : AuthState
    data class NeedValidation(
        val phone: String,
        val sid: String?,
        val validationType: ValidationType,
        val validationTypeRaw: String?,
        val supportedWays: List<String>,
        val canResend: Boolean,
        val resendAfter: Int?,
        val nextStep: String?,
        val phoneMask: String?,
    ) : AuthState
    data class Error(val kind: AuthErrorKind, val message: String) : AuthState
    data class Success(val result: AuthResult) : AuthState
    data class OfflineWithCache(
        val cachedUserId: Long,
        val lastSeenMs: Long,
        val tokenExpiredAt: Long,
    ) : AuthState
}
```

### 2.2. ValidationType (enum) — `AuthModels.kt:86`
`SMS, PUSH, EMAIL, IVR, CALL_RESET, LIBVERIFY, SMS_INBOX, TELEGRAM, PASSKEY, MESSENGER, UNKNOWN` (11 значений).

### 2.3. AuthErrorKind (enum) — `AuthModels.kt:100`
`INVALID_CREDENTIALS, TOO_MANY_ATTEMPTS, TOO_MANY_REQUESTS, NEED_SIGNUP, BANNED, DEACTIVATED, NEED_RESTORE, EXCHANGE_TOKEN_INVALID, NETWORK, PARSE, UNKNOWN, EXPIRED` (12 значений).

### 2.4. ExchangeTokenResult (sealed) — `AuthModels.kt:135`
`Success(exchangeToken) | TokenInvalid | Unavailable`.

### 2.5. AuthResult — `AuthModels.kt:162`
`accessToken, exchangeToken?, userId, expiresIn, scope?, secret?, trustedHash?, phone?, email?, webviewAccessToken?, webviewRefreshToken?, webviewExpiresIn, utilityTokens?, authCookies?, httpsRequired, phoneToActualize?, silentToken?, silentTokenUuid?` (20 полей).

### 2.6. AuthPhase (private enum) — `AuthActivity.kt:510`
`LANDING, WEBVIEW, TWO_FA, SUCCESS` (4 значения, Compose state).

### 2.7. SessionCookies (data class) — `VkAuthWebViewScreenV2.kt:619`
`remixsid, p, remixnsid, httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvkFp` (9 nullable String).

### 2.8. PendingAuthResult (object) — `AuthActivity.kt:2662`
```kotlin
object PendingAuthResult {
    private const val TAG = "PendingAuthResult"
    @Volatile private var token: String? = null
    @Volatile private var userId: Long = 0L
    fun save(token: String, userId: Long)
    fun consume(): Pair<String, Long>?
    fun clear()
}
```

### 2.9. AuthDomainsConfig.Snapshot — `AuthDomainsConfig.kt:92`
`oauthHost, idHost, loginHost, mobileWebHost, apiHost, webClientId, forceRevoke` (6 String + 1 Boolean).

### 2.10. FixedInputWebView → SovaInputConnection (composition) — `FixedInputWebView.kt:43,77`
```kotlin
class FixedInputWebView(context, attrs, defStyleAttr) : WebView(context, attrs, defStyleAttr) {
    override fun onCreateInputConnection(outAttrs): InputConnection? =
        super.onCreateInputConnection(outAttrs)?.let { SovaInputConnection(it) }
}
private class SovaInputConnection(target: InputConnection) : InputConnectionWrapper(target, true) {
    private var composingLength = 0
    override fun setComposingText(text, newCursorPosition): Boolean  // deleteSurroundingText + commitText
    override fun finishComposingText(): Boolean  // no-op + reset
    override fun setComposingRegion(start, end): Boolean  // блокируем
    override fun deleteSurroundingText(before, after): Boolean  // сбрасываем composingLength
}
```

### 2.11. LongPollEvent (sealed) — `realtime/LongPollClient.kt:1240+`
`NewMessage, EditMessage, ReadInbox, ReadOutbox, UserOnline, UserOffline, Typing, DialogUpdate, UnreadCountersChanged (object), NotificationsCountChanged (object), Reset (object)`. Используется auth flow через `MessageNotifier` (для push notifications при залогиненной сессии).

### 2.12. ExistingAuthResult — `ExternalBrowserAuth.kt`
`found: Boolean, remixsid: String?, source: String?` (возвращается из `tryFindExistingAuth()`).

### 2.13. CapturedCookies — `RemixsidCapturer.kt`
`remixsid, pCookie, remixnsid, httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvkFp, source` (9 cookies + source).

### 2.14. CuaMethod / CuaValidationMethod / CuaCheckResult — `data/model/VkAccountModels.kt`
Для CUA (Confirm User Action) — используется в `CuaVerifySheet` (devices screen).

### 2.15. Initiator (enum) — `ExchangeAuthApi.kt:508`
`NO_INITIATOR, EXPIRED_TOKEN, ADD_EDU_PROFILE, AUTHORIZATION, SILENT_AUTHORIZATION, WEB_HANDLER_AUTHORIZATION` (6 значений, для `authByExchangeToken`).

### 2.16. ExchangeAuthRepository.Result / SilentTokenExchanger.Result — sealed
`Success | TokenInvalid | Unavailable | AllEndpointsFailed` (последнее только для SilentTokenExchanger).

---

## 3. Вызовы и методы (статическая карта)

### 3.1. Точка входа: `AuthActivity.onCreate` → `AuthScreen` → фаза-машина

```
MainActivity.launchAuth(reason)
  → Intent(AuthActivity, EXTRA_SILENT_MODE)
  → AuthActivity.onCreate
    → setTheme(Theme_PinoK_Silent if silentMode else Theme_PinoK)
    → super.onCreate + enableEdgeToEdge
    → VkAuthWebViewFactoryState.reset()
    → setContent { SOVATheme { AuthScreen(...) } }
       → AuthScreen(viewModel, silentMode, ...)
         → phase = if (silentMode) WEBVIEW else LANDING
         → LaunchedEffect(state) { when (state) { NeedValidation → TWO_FA; Success → SUCCESS+onSuccess; Error → LANDING (silentMode+WEBVIEW); OfflineWithCache → SUCCESS+onSuccess } }
         → LaunchedEffect(Unit) { ExternalBrowserAuth.tryFindExistingAuth() → если remixsid, phase=WEBVIEW }
         → when (phase) {
              LANDING → LandingScreen(onStartWebView={phase=WEBVIEW}, onLaunchExternalBrowser, onShowSessionDump, onOfflineMode, ...)
              WEBVIEW → VkAuthWebViewScreenV2(onTokenExchange, onBack={phase=LANDING}, onCancel, onOfflineMode, onSilentTokenExchanged, silentMode)
              TWO_FA → ValidationCodeForm(onSubmit, onResend, onWithoutPassword, onBack)
              SUCCESS → (пусто, onSuccess уже вызван)
            }
```

### 3.2. `VkAuthWebViewScreenV2` — внутренний flow

```
VkAuthWebViewScreenV2(isLoading, onTokenExchange, onBack, onCancel, onOfflineMode, onSilentTokenExchanged, silentMode)
  → remember { webViewRef, statusText="Открываем VK…", loading=true, isExchanging=false, userClosing, authSucceeded }
  → LaunchedEffect(Unit) { PendingAuthResult polling каждые 500мс, 120с timeout }
  → DisposableEffect(Unit) {
      coroutineScope.launch(Dispatchers.Default) {
        while (isActive) {
          remixsid = getRemixSidFromCookieManager()
          if (remixsid != null && webViewRef != null) {
            cookies = captureSessionCookies()
            withContext(Main) { isExchanging=true; onTokenExchange(remixsid, cookies, wv) }
            return@launch
          }
          if (timeoutMs exceeded) { handleClose(); return@launch }
          delay(1000L)
        }
      }
      onDispose { pollJob.cancel(); removeView+destroy WebView }
    }
  → Box {
      AndroidView(
        factory = { ctx →
          FixedInputWebView(ctx).apply {
            settings.javaScriptEnabled=true, domStorageEnabled=true, ...
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            CookieManager.setAcceptCookie(true), setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
              shouldOverrideUrlLoading → блокировка рекламы, intent://, custom schemes, silent_token перехват, direct access_token перехват, VK-домен allowlist
              onPageStarted → loading=true
              onPageFinished → loading=false, captureAndLogCookies
              onReceivedError / onReceivedHttpError / onReceivedSslError
            }
            // ❌ НЕТ setWebChromeClient
            loadUrl(AuthDomainsConfig.mobileWebUrl())  // https://m.vk.ru
            webViewRef = this
          }
        },
        update = { webView → webViewRef = webView },
        // ❌ НЕТ onRelease (cleanup в DisposableEffect.onDispose)
      )
      // TopBar: IconButton(Назад) → handleClose
      // statusText Surface (top-center)
      // CircularProgressIndicator если loading||isExchanging (bottom-center, padding 84dp)
      // Bottom action bar: OutlinedButton("Отмена") + OutlinedButton("Офлайн")
    }
```

### 3.3. `AuthActivity.onTokenExchange` callback (строки 719-756)

```
onTokenExchange = { remixsid, cookies, webView →
  if (remixsid.isNotBlank()) {
    val captured = RemixsidCapturer.CapturedCookies(remixsid, cookies.p, cookies.remixnsid, ...)
    app.exchangeAuthRepository.saveRemixsid(captured)
  }
  viewModel.submitWebToken(remixsid, webView)  // → WebTokenAuth.fullAuthFlow
}
```

### 3.4. `AuthViewModel.submitWebToken` — `AuthViewModel.kt:273`

```
fun submitWebToken(remixsid: String, webView: WebView) {
  viewModelScope.launch {
    // Вызывает WebTokenAuth.fullAuthFlow(webView) — polling localStorage каждые 500мс
    // Если токен истёк — reload m.vk.ru и retry
    // Если токен найден — submitOAuthToken(token, userId)
  }
}
```

### 3.5. `WebTokenAuth.fullAuthFlow` — `WebTokenAuth.kt:227`

```
suspend fun fullAuthFlow(webView: WebView): Result<WebTokenResult> {
  // Step 1: Ждём web_token в localStorage (LS_POLL_TIMEOUT_MS = 60с)
  //   evaluateJavascript("localStorage.getItem('web_token_6287487')") каждые 500мс
  //   Если null → reload m.vk.ru и retry
  // Step 2: Если токен истёк — удалить из localStorage, reload m.vk.ru, retry
  // Step 3: connectExchangeToken(remixsid, exchangeToken) — login.vk.ru/?act=connect_exchange_token
  //   Возвращает новый access_token (если remixsid невалидный → ошибка)
}
```

### 3.6. `ExchangeAuthRepository.saveOAuthToken` — сохраняет token + 9 cookies в `ExchangeTokenStorage` (EncryptedSharedPreferences).

### 3.7. `MainActivity.onResume` → `notifyResumed` (Fix #AUTH-WEBVIEW-STARVATION)

```
if (app.tokenStorage.hasValidToken()) {
  try { PlayerConnection.notifyResumed() } catch (e) { ... }
} else {
  AppLog.d("MainActivity", "skip PlayerConnection.notifyResumed — no token (auth flow), #AUTH-WEBVIEW-STARVATION")
}
```

### 3.8. `SovaApp.onCreate` → `PlayerConnection.init(this)` (строка 919) — **ВСЕГДА**

```
PlayerConnection.init(this)
  → if (initialized) return
  → synchronized(this) {
      initialized = true
      appContext = context.applicationContext
      lastReconnectTs = System.currentTimeMillis()  // #INIT-RECONNECT-GUARD
      connectController(context.applicationContext)  // ← IPC bind к PlayerService
    }
```

### 3.9. `PlayerConnection.connectController` — `PlayerConnection.kt:232`

```
private fun connectController(ctx: Context) {
  val sessionToken = SessionToken(ctx, ComponentName(ctx, PlayerService::class.java))
  val controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()
  controllerFuture.addListener({
    // controller готов — но PlayerService.onCreate уже выполнен к этому моменту
  }, ContextCompat.getMainExecutor(ctx))
}
```

`MediaController.Builder.buildAsync()` запускает IPC bind к `PlayerService`. Если сервис ещё не запущен — Android запускает его (`PlayerService.onCreate`), что блокирует main thread на ~150-300мс (ExoPlayer + MediaSession + AudioEffects).

---

## 4. Связанность (coupling map)

```
AuthActivity (auth/)
├── AuthViewModel (auth/)
│   └── ExchangeAuthRepository (auth/exchange/)  ← god-class
│       ├── ExchangeAuthApi (auth/exchange/)
│       ├── ExchangeTokenExchanger (auth/exchange/)
│       ├── SilentTokenExchanger (auth/exchange/)
│       ├── WebTokenAuth (auth/exchange/)
│       ├── ExchangeTokenStorage (auth/exchange/)
│       ├── RemixsidCapturer (auth/exchange/)
│       └── AccountFileBackup (auth/exchange/)
├── VkAuthWebViewScreenV2 (auth/)
│   ├── FixedInputWebView (auth/)
│   ├── AuthDomainsConfig (auth/exchange/)  ← singleton
│   ├── SilentTokenExchanger (auth/exchange/)
│   ├── PendingAuthResult (auth/, object singleton)
│   ├── RemixsidCapturer (auth/exchange/)
│   ├── SovaApp.get()  ← GLOBAL SINGLETON (для tryLaunch* через reflection)
│   └── AppLog (util/)
├── LandingScreen (auth/, inline)
│   ├── AuthDomainsSettingsIcon (auth/exchange/)
│   └── SessionDumpParser (auth/exchange/)
├── OAuthWebViewActivity (auth/)  ← launch через oauthWebViewLauncher (РЕДКО)
│   └── RemixsidCapturer (auth/exchange/)
├── ExternalBrowserLauncher (auth/exchange/)  ← launch через onLaunchExternalBrowser
├── ExternalBrowserAuth (auth/exchange/)  ← tryFindExistingAuth
├── SovaApp.get()  ← GLOBAL SINGLETON (для exchangeAuthRepository, tokenStorage)
└── AppLog (util/)

SovaApp (root Application)
├── PlayerConnection.init(this)  ← СТАРТИТ PlayerService IPC ВСЕГДА (P0-1)
├── ExchangeAuthRepository
├── VKApiClient
├── LongPollClient  ← только если hasValidToken (через lifecycle)
├── MessageNotifier / NotificationsPoller / SecurityAlertsPoller  ← только если hasValidToken
└── AuthDomainsConfig.update(snap)  ← coroutine, обновляет volatile snapshot

MainActivity (ui/)
├── launchAuth(reason)  ← throttled 20с + #SSO-RECREATE-GUARD 90с
├── notifyResumed()  ← SKIP если !hasValidToken (Fix #AUTH-WEBVIEW-STARVATION)
├── tokenInvalidationTicks: StateFlow<Int>  ← триггер для silent re-login
├── network-restored-no-token callback  ← auto-retry auth
└── clipboard token capture (Share intent)
```

**Связанность:** AuthActivity напрямую обращается к 12+ классам. ExchangeAuthRepository — god-class, ~30 методов, обращается к 8+ классам. SovaApp — глобальный singleton, доступ из всех точек через `SovaApp.get()`.

---

## 5. Версии и зависимости

### 5.1. BuildConfig (из `app/build.gradle.kts`)

| Ключ | Значение | Назначение |
|------|----------|-----------|
| `VK_CLIENT_ID` | `2274003` | Official VK Android client (для Direct Auth) |
| `VK_CLIENT_SECRET` | `hHbZxrka2uZ6jB1inYsH` | Хардкод app_secret (норма для VK Android) |
| `VK_API_VERSION` | `5.269` | VK API version |
| `VK_API_HOST` | `https://api.vk.com` | Default API host |
| `VK_OAUTH_HOST` | `https://oauth.vk.com` | OAuth endpoint (password/2FA/trusted_hash) |
| `VK_ID_HOST` | `https://id.vk.com` | VK ID endpoint (exchange_token refresh) |
| `VK_WEB_CLIENT_ID` | `6287487` | vk.com desktop web (vk1.a.* токен, без sig) |
| `VK_WEB_MOBILE_CLIENT_ID` | `7879029` | m.vk.com mobile web (без client_secret) |

### 5.2. SDK и библиотеки (из `libs.versions.toml` + `app/build.gradle.kts`)

- **Kotlin:** AGP 9.0+ built-in Kotlin (без `kotlin-android` plugin).
- **minSdk:** 24 (Android 7.0).
- **targetSdk:** 36 (Android 16).
- **compileSdk:** 36.
- **Java:** 21.
- **Compose:** BOM-managed (Material3).
- **Navigation:** `androidx.navigation.compose`.
- **DataStore:** `androidx.datastore.preferences`.
- **Security:** `androidx.security.crypto` (EncryptedSharedPreferences).
- **Biometric:** `androidx.biometric` (Locker).
- **WebKit:** `androidx.webkit`.
- **Browser:** `androidx.browser` (Chrome Custom Tabs).
- **OkHttp:** `okhttp` + `okhttp.logging`.
- **Coil 3:** `coil.compose` + `coil.network.okhttp` + `coil.gif`.
- **Gson:** `gson`.
- **Coroutines:** `kotlinx.coroutines.android`.
- **Media3:** `media3.exoplayer` + `media3.ui` + `media3.session` + `media3.common` + `media3.datasource` + `media3.datasource.okhttp` + `media3.database` + `media3.exoplayer.hls`.
- **ffmpeg-kit-audio:** Siren→AAC транскодер.
- **CameraX:** `camera.core` + `camera.camera2` + `camera.lifecycle` + `camera.view` + `camera.video`.
- **WorkManager:** `work.runtime.ktx` (CookieRefreshWorker).
- **Lifecycle:** `lifecycle.runtime.ktx` + `lifecycle.viewmodel.compose` + `lifecycle.runtime.compose` + `lifecycle.process`.
- **Splashscreen:** `core.splashscreen`.
- **DocumentFile:** `androidx.documentfile`.

### 5.3. Внешние сервисы / endpoints

| Endpoint | Метод | Назначение |
|----------|-------|-----------|
| `https://api.vk.com/method/<m>` | POST | Все VK API methods (Android gateway, X-VK-Android-Client UA, sig для messages.*/audio.*/execute) |
| `https://web.api.vk.ru/method/<m>` | POST | Mobile-web gateway (без sig, для vk1.a.* токенов) |
| `https://oauth.vk.com/authorize` | GET (WebView) | Implicit OAuth flow (6287487 → blank.html#access_token) |
| `https://oauth.vk.com/access_token` | POST | grant_type=password/phone_confirmation_sid/without_password/trusted_hash/vk_external_auth |
| `https://id.vk.com/auth_by_exchange_token` | POST | grant_type=exchange_token (refresh) |
| `https://oauth.vk.com/auth_by_exchange_token` | POST (follow 302) | Path 4 — exchange_token через legacy oauth |
| `https://api.vk.com/method/auth.getAuthData` | POST | silent_token exchange candidate #1 |
| `https://api.vk.com/method/auth.getAnonymToken` | POST | silent_token exchange candidate #2 |
| `https://id.vk.com/auth_by_silent_token` | POST | silent_token exchange candidate #3 |
| `https://login.vk.com/?act=connect_exchange_token` | POST | refresh expired web_token через remixsid |
| `https://login.vk.com/?act=web_token` | WebView JS | m.vk.ru JS сам дёргает |
| `https://vk.com/al_audio.php` | POST (remixsid cookie) | Web fallback для аудио URL |
| `https://m.vk.ru` | GET (WebView) | **PRIMARY auth URL** (mobileWebUrl) |
| `https://ok.ru/videoembed/<movieId>` | GET | OK video metadata |
| LongPoll: `https://<server>?act=a_check&key=&ts=&wait=25&mode=2&version=3` | GET (long-poll) | Real-time сообщения |

### 5.4. Android permissions (из `AndroidManifest.xml`)

Auth-связанные: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `USE_BIOMETRIC`, `USE_FINGERPRINT`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `ACCESS_NOTIFICATION_POLICY`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, `BLUETOOTH_CONNECT`, `READ/WRITE_EXTERNAL_STORAGE` (≤28/32), `READ_MEDIA_AUDIO/VIDEO/IMAGES` (33+), `MANAGE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`.

`<queries>`: `com.vkontakte.android` (VK app SSO), `com.vk.android.lite`, custom schemes `vkontakte://`, `vk://`, `vklink://`, `http/https` ACTION_VIEW.

---

## 6. Задержки (delays/timeouts) — полный реестр

| Константа | Значение | Где | Назначение |
|-----------|---------|-----|-----------|
| `launchAuth` throttle | 20 000 мс | `MainActivity.kt:launchAuth` | Анти-спам запуск AuthActivity (Fix #230) |
| `#SSO-RECREATE-GUARD` window | 90 000 мс | `MainActivity.kt:launchAuth` (static) | Блокирует auto-retry auth после low-memory kill во время VK-app SSO |
| `LS_POLL_TIMEOUT_MS` | 60 000 мс | `WebTokenAuth.kt` | Таймаут чтения web_token из localStorage |
| `SSO_RETURN_WAIT_MS` | ~25 000 мс | `WebTokenAuth.kt` | Ожидание редиректа SSO на m.vk.ru |
| `SSO_POST_REDIRECT_POLL_MS` | ~10 000 мс | `WebTokenAuth.kt` | Post-redirect poll (m.vk.ru JS placement) |
| `EVALJS_TIMEOUT_MS` | 5 000 мс | `WebTokenAuth.kt` | Таймаут `evaluateJavascript` |
| Cookie polling interval | 1 000 мс | `VkAuthWebViewScreenV2.kt:184` | Интервал проверки CookieManager на remixsid |
| Cookie polling timeout (silent) | 30 000 мс | `VkAuthWebViewScreenV2.kt:181` | Таймаут silent mode |
| Cookie polling timeout (normal) | 300 000 мс (5 мин) | `VkAuthWebViewScreenV2.kt:181` | Таймаут normal mode |
| PendingAuthResult polling interval | 500 мс | `VkAuthWebViewScreenV2.kt:153` | Интервал polling SSO callback |
| PendingAuthResult polling timeout | 120 000 мс (2 мин) | `VkAuthWebViewScreenV2.kt:151` | Таймаут SSO callback |
| `lastReconnectTs` guard (PlayerConnection) | 2 000 мс | `PlayerConnection.kt:62` | Анти-спам reconnect (Fix #169) |
| `delay(120L)` после `removeView` перед `webviewRecreateKey++` | 120 мс | `AuthActivity.kt` (Variant A удалил) | ~~Chromium cleanup~~ (удалено) |
| `CookieRefreshWorker` periodic | 6 часов | `CookieRefreshWorker.kt` | Фоновый cookie sync |
| `NetworkSwitchState` pause (`TOKEN_INVALIDATION_PAUSE_MS`) | 4 000 мс | `NetworkSwitchState.kt` (#NET-SWITCH-DELAY) | Пауза при Wi-Fi↔Mobile switch |
| `#ATTACH-SUPPRESS-WINDOW` | 120 000 мс | (в SovaApp) | Suppress auth re-launch после attach |
| `NotificationsPoller` interval | ~60 с | `realtime/NotificationsPoller.kt` | Polling notifications.getRedesign |
| `SecurityAlertsPoller` interval | 10 мин | `realtime/SecurityAlertsPoller.kt` | Polling accountPersonal.getSecurityAlerts |
| `ClipsCounter` interval | 5 мин | `realtime/ClipsCounter.kt` | Polling account.getCounters |

### 6.1. Задержки в `VkAuthWebViewScreenV2` — детально

| Локация | Что | Значение | Оценка |
|---------|-----|---------|--------|
| `:153` | PendingAuthResult poll interval | 500мс | ОК |
| `:151` | PendingAuthResult poll timeout | 120с | ОК (покрывает VK-app SSO) |
| `:181` | Cookie poll timeout (silent) | 30с | ОК |
| `:181` | Cookie poll timeout (normal) | 300с | ОК |
| `:184` | Cookie poll interval | 1000мс | ОК, но можно 500мс |
| `:241-243` | UA static `Chrome/131.0.0.0` | — | РИСК — нужно dynamic `WebSettings.getDefaultUserAgent(ctx)` |
| `:255` | `setLayerType(LAYER_TYPE_SOFTWARE)` | — | РИСК — тормозит Chromium на m.vk.ru SPA |
| `:264-265` | `setAcceptCookie(true)` + `setAcceptThirdPartyCookies(true)` | — | ОК, но НЕТ `removeAllCookies` перед `loadUrl` |
| `:267-285` | `webViewClient` | — | ОК |
| `:427` | `loadUrl(mWebUrl)` | — | ОК, но без `clearCache(true)` / `clearHistory()` |
| ❌ `:267-285` | **НЕТ `setWebChromeClient`** | — | БАГА — нет onProgressChanged/JS dialogs/window.open |
| ❌ `:200-260` | **НЕТ safety-net** (удалён Variant A) | — | БАГА — если onPageStarted не сработал, нет recovery |
| ❌ `:264` | **НЕТ `CookieManager.removeAllCookies`** | — | БАГА — stale cookies могут мешать |
| ❌ `:427` | **НЕТ `clearCache(true)` / `clearHistory()`** | — | РИСК — stale cache |

### 6.2. Отсутствие задержек — где они критичны

| Локация | Что отсутствует | Почему критично |
|---------|-----------------|-----------------|
| `VkAuthWebViewScreenV2.factory` | `delay` перед `loadUrl` (после `CookieManager.setAccept*`) | На некоторых устройствах `setAcceptCookie` асинхронен, и `loadUrl` стартует до применения настроек → cookies не сохраняются |
| `VkAuthWebViewScreenV2` | safety-net polling «onPageStarted не сработал за N сек» | Удалён в Variant A → нет recovery при chromium starvation |
| `VkAuthWebViewScreenV2` | `WebChromeClient.onProgressChanged` | Нет — UI не видит прогресс, нет fallback на «прогресс < 20% за N сек → reload» |
| `AuthActivity.onCreate` | `PlayerConnection.init` guard «if !hasValidToken» | `init` запускается ВСЕГДА в `SovaApp.onCreate:919`, IPC bind к PlayerService → main thread block → chromium starvation |

---

## 7. Скобки, комментарии, style audit

### 7.1. Скобки (точечная проверка)

| Файл | Локация | Проверка |
|------|---------|---------|
| `VkAuthWebViewScreenV2.kt:255` | `setLayerType(View.LAYER_TYPE_SOFTWARE, null)` | OK |
| `VkAuthWebViewScreenV2.kt:264` | `CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)` | OK |
| `VkAuthWebViewScreenV2.kt:267-285` | `webViewClient = object : WebViewClient() { ... }` | OK, braces сбалансированы |
| `VkAuthWebViewScreenV2.kt:427` | `loadUrl(mWebUrl)` | OK, no parens issue |
| `AuthActivity.kt:717` | `VkAuthWebViewScreenV2(...)` вызов | OK, 8 аргументов |
| `AuthActivity.kt:1190-1206` | `Button(onClick = onStartWebView, ...)` | OK |
| `AuthActivity.kt:641` | `onStartWebView = { phase = AuthPhase.WEBVIEW }` | OK |

Скобки в PinoK-style: `(parent as? ViewGroup)?.removeView(wv)` в V2 — соответствует `CODING_STYLE.md` (smart-cast через `if (parent is ViewGroup)` в onDispose). ✓

### 7.2. Комментарии — критичные несоответствия

| Локация | Комментарий | Реальность | Проблема |
|---------|-------------|-----------|----------|
| `VkAuthWebViewScreenV2.kt:71` | «кнопка «Войти через VK» на Landing» | ОК | — |
| `VkAuthWebViewScreenV2.kt:241-243` | UA `Chrome/131.0.0.0 Mobile` | `build.gradle.kts:46-50` явно говорит «Статический хардкод здесь убран: некорректное содержимое приводило к error 15» | Хардкод снова здесь, противоречит build.gradle warning |
| `AuthActivity.kt:218-228` | «Fix #176 УДАЛЕН: evictAll() основан на ЛОЖНОЙ посылке» | OK, fix removed | — |
| `AuthActivity.kt:1804` | «VkAuthWebViewScreen — WebView m.vk.ru + cookie polling + localStorage token» | Это СТАРЫЙ private composable, **НЕ вызывается** (сейчас V2) | Мёртвый код ~1200 строк, вводит в заблуждение |
| `AuthActivity.kt:1928-1931` | «Variant A: сломанная WebView-механика УДАЛЕНА — pageStartedRef/recreate safety-net» | Удалён в старом `VkAuthWebViewScreen`, но в V2 тоже отсутствует → нет safety-net нигде | Подтверждает P0-3 |
| `AuthActivity.kt:2082-2083` | «AuthActivity пока юзер в VK-app — новый AuthActivity грузит m.vk.ru» | ОК (stateless flow) | — |
| `AuthActivity.kt:1100` | «#VKAUTH-V2 Primary CTA: запуск встроенного WebView (m.vk.ru)» | ОК, переходит в `phase = AuthPhase.WEBVIEW` → V2 | — |
| `VkAuthWebViewScreenV2.kt:75` | «AuthDomainsConfig.mobileWebUrl()» | ОК, дефолт m.vk.ru | — |
| `VkAuthWebViewScreenV2.kt:267-285` | Нет комментария про отсутствие WebChromeClient | Нет `setWebChromeClient` | Бага без комментария |
| `network_security_config.xml` | Нет `.ru` доменов | VK мигрирует на .ru, но конфиг только .com | Подозрительно |

### 7.3. PinoK-style violations (`?.` / `?:` / `!!`)

| Файл | Локация | Нарушение |
|------|---------|-----------|
| `VkAuthWebViewScreenV2.kt:191` | `val wv = webViewRef` + `if (wv != null) { ... }` | OK — smart-cast через local val |
| `VkAuthWebViewScreenV2.kt:215-218` | `withContext(Dispatchers.Main) { isExchanging = true; ... }` | OK |
| `VkAuthWebViewScreenV2.kt:233-237` | `val parent = wv.parent; if (parent is ViewGroup) { parent.removeView(wv) }` | OK — smart-cast |
| `VkAuthWebViewScreenV2.kt:627-660` | `tryLaunchIntentUrl`/`tryLaunchCustomScheme`/`tryLaunchMarketUrl` — дублирование через `re.pinok.SovaApp.get()` | TODO: «вынести tryLaunch* в отдельный object» — техдолг |
| `AuthActivity.kt:244` | `cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()` | 4-level `?.` chain — нарушает CODING_STYLE.md (но на границе с Java API, допустимо) |
| `AuthActivity.kt:405` | `clip.getItemAt(0)?.coerceToText(this)?.toString()` | то же |
| `AuthActivity.kt:723-754` | `cookies.p != null` / `cookies.remixnsid != null` (smart-cast в строке) | OK |
| `AuthActivity.kt:755-756` | `viewModel.submitWebToken(remixsid, webView)` | OK |

**`!!` violations в auth/:** 0 (поиском не найдено).
**`?:` violations в auth/:** ~15 (большинство на границе с Java API — OK по CODING_STYLE.md).

---

## 8. Конкретные баги и рекомендации

### 🔴 P0-1: `PlayerConnection.init(this)` в `SovaApp.onCreate:919` блокирует main thread в auth flow

**Симптом:** При холодном старте приложения (токен невалиден, auth flow) `SovaApp.onCreate` вызывает `PlayerConnection.init(this)` → `connectController()` → IPC bind к `PlayerService` → `PlayerService.onCreate()` (heavy main-thread work: ExoPlayer + MediaSession + AudioEffects, ~150-300мс). В это время Chromium в `VkAuthWebViewScreenV2.factory` пытается поднять рендерер → IPC handshake `cr_ChildProcessConn` таймаутится → `onPageStarted` NEVER fires → m.vk.ru не грузится (белый экран).

**Fix #AUTH-WEBVIEW-STARVATION** (`MainActivity.kt:1272`) skip'ит только `notifyResumed`, но НЕ `init`. `init` вызывается в `SovaApp.onCreate` ВСЕГДА, даже в auth flow.

**Исправление:**

Вариант A (минимальный, рекомендуемый):
```kotlin
// SovaApp.kt:919
// #AUTH-WEBVIEW-STARVATION-V2: НЕ запускаем PlayerConnection в auth flow.
// PlayerService.onCreate блокирует main thread (ExoPlayer+MediaSession+AudioEffects),
// что мешает Chromium поднять рендерер для m.vk.ru.
if (tokenStorage.hasValidToken()) {
    PlayerConnection.init(this)
} else {
    AppLog.i("SovaApp", "skip PlayerConnection.init — no token (auth flow), #AUTH-WEBVIEW-STARVATION-V2")
}
```

Вариант B (более радикальный): отложить `PlayerConnection.init` до `MainActivity.onResume` (после auth success):
```kotlin
// SovaApp.kt:919 — убрать
// MainActivity.kt — добавить в onResume после hasValidToken check:
if (app.tokenStorage.hasValidToken() && !PlayerConnection.isInitialized()) {
    PlayerConnection.init(this)
}
```

Вариант C (вынести PlayerService.onCreate в фон): `PlayerService.onCreate` вынести в `serviceScope.launch(Dispatchers.Default)` с возвратом на main только для `MediaSession.setPlayer`. Это улучшит startup не только auth, но и обычного cold start (см. HISTORY.md session 3 «Нерешённое / next steps»).

**Оценка:** 0.5д (вариант A), 1д (B), 2д (C).

---

### 🔴 P0-2: Отсутствует `WebChromeClient` в `VkAuthWebViewScreenV2`

**Симптом:** Без `WebChromeClient`:
- `onProgressChanged` НЕ работает → UI не получает прогресс загрузки, нет fallback «прогресс < 20% за N сек → reload».
- `onJsAlert/Confirm/Prompt` молча подавляются → JS `alert()` теряется, `confirm()` возвращает `false` (default), `prompt()` возвращает `null`.
- `onCreateWindow` НЕ работает → `window.open()` молча fail.
- `onConsoleMessage` НЕ работает → JS console output не виден в logcat.
- `onGeolocationPermissionsShowPrompt` / `onPermissionRequest` НЕ работают.

VK ID SDK на m.vk.ru использует `window.open()` (для QR-логина), `prompt()` (для 2FA confirm), `console.log` (для отладки). Без `WebChromeClient` эти вызовы молча fail → flow может зависнуть или отмениться.

**Сравнение с OAuthWebViewActivity.kt:399-404** (там WebChromeClient есть):
```kotlin
setWebChromeClient(object : android.webkit.WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        progress = newProgress
        if (newProgress >= 100) isLoading = false
    }
})
```

**Исправление:** добавить `setWebChromeClient` в `VkAuthWebViewScreenV2.factory` (после `webViewClient = ...`, до `loadUrl`):

```kotlin
setWebChromeClient(object : android.webkit.WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        // Обновляем loading на основе прогресса — fallback если onPageFinished не сработал
        if (newProgress >= 100) loading = false
        // Safety-net: если прогресс 0 за 6 сек → reload
        // (см. P0-3)
    }
    override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
        AppLog.d(TAG, "JS alert: $message (url=$url)")
        // Показываем AlertDialog или просто result.confirm()
        result.confirm()
        return true
    }
    override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
        AppLog.d(TAG, "JS confirm: $message (url=$url)")
        result.confirm()  // auto-confirm (или показать AlertDialog)
        return true
    }
    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: android.webkit.JsPromptResult): Boolean {
        AppLog.d(TAG, "JS prompt: $message (url=$url)")
        result.confirm(defaultValue ?: "")
        return true
    }
    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
        AppLog.d(TAG, "JS console [${consoleMessage.messageLevel()}]: ${consoleMessage.message()} at ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}")
        return true
    }
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, result: android.webkit.WebChromeClient.ResultMessage<WebView>): Boolean {
        // VK ID SDK может открывать window.open для QR-логина
        // Создаём новый WebView в том же контексте
        val newWebView = FixedInputWebView(view.context)
        newWebView.webViewClient = view.webViewClient
        newWebView.settings.apply { /* те же настройки */ }
        result.send(newWebView)
        return true
    }
})
```

**Оценка:** 0.5д.

---

### 🔴 P0-3: Нет safety-net для `onPageStarted`-не-сработал

**Симптом:** Variant A (commit `89a71efe5`) удалил `pageStartedRef` (AtomicBoolean) + `recreateAttemptedRef` (AtomicInteger) + `webviewRecreateKey` (mutableStateOf) + `recreate safety-net` (~50 строк polling). Если Chromium рендерер не подключается с первого раза (`cr_ChildProcessConn: Failed to establish the service connection`), `onPageStarted` НЕ вызывается → `loading` остаётся `true` бесконечно → пользователь видит прогресс-бар без возможности recovery.

Старый safety-net thrash'ил (3 destroy+recreate за 3 сек), но его можно сделать правильно: **один** reload через 6 сек, без recreate.

**Исправление:** добавить safety-net в `VkAuthWebViewScreenV2`:

```kotlin
// Внутри DisposableEffect(Unit) { ... }, рядом с cookie polling:
val safetyNetJob = coroutineScope.launch(Dispatchers.Main) {
    delay(6_000L)  // 6 сек — больше чем нормальный onPageStarted (~500мс-2с)
    if (loading) {  // onPageStarted не сработал (loading остался true)
        AppLog.w(TAG, "#WEBVIEW-SAFETY-NET: onPageStarted не сработал за 6 сек — reload")
        val wv = webViewRef
        if (wv != null) {
            try { wv.reload() } catch (e: Exception) {
                AppLog.w(TAG, "#WEBVIEW-SAFETY-NET: reload failed: ${e.message}")
            }
        }
    }
}

// onDispose:
safetyNetJob.cancel()
```

Альтернатива: использовать `onProgressChanged` из `WebChromeClient` (P0-2) — если прогресс 0 за 6 сек → reload.

**Оценка:** 0.5д (вместе с P0-2).

---

### 🟠 P1-4: `CookieManager` НЕ очищается перед `loadUrl`

**Симптом:** Если в CookieManager остались stale cookies от прошлой сессии (logout не вычистил), polling мгновенно находит старый remixsid → `onTokenExchange` → `submitWebToken` → silent refresh fails (remixsid невалидный) → `AuthState.Error` → `phase = AuthPhase.LANDING`. Пользователь видит Landing снова вместо m.vk.ru.

**Исправление:** перед `loadUrl(mWebUrl)` в `VkAuthWebViewScreenV2.factory`, добавить очистку cookies:

```kotlin
// Перед setAcceptCookie:
if (!silentMode) {  // В silent mode НЕ очищаем — там как раз читаем существующий remixsid
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().removeSessionCookies(null)
    CookieManager.getInstance().flush()
    AppLog.i(TAG, "CookieManager очищен перед loadUrl (normal mode)")
}
```

Также проверить `MainActivity.logout()` — должно вызывать `CookieManager.removeAllCookies` (если ещё не делает).

**Оценка:** 0.5д.

---

### 🟠 P1-5: `setLayerType(View.LAYER_TYPE_SOFTWARE, null)` тормозит Chromium

**Симптом:** Software rendering для input fix (курсор в VK ID React), но на сложном SPA m.vk.ru может тормозить или зависать. На некоторых устройствах Chromium вообще не рендерит в software mode.

**Исправление:** убрать `setLayerType(LAYER_TYPE_SOFTWARE)` — `FixedInputWebView` + `SovaInputConnection` + JS cursor fix (`VK_2FA_CURSOR_FIX_JS` в AuthActivity) уже решают проблему курсора без software rendering.

Если на некоторых устройствах input всё ещё ломается — добавить `LAYER_TYPE_HARDWARE` (default) и проверить.

```kotlin
// Убрать:
// setLayerType(View.LAYER_TYPE_SOFTWARE, null)
// Оставить default (LAYER_TYPE_NONE / hardware-accelerated)
```

**Оценка:** 0.5д + тестирование на разных устройствах.

---

### 🟠 P1-6: Static UA `"HOTWAV Cyber 15"` противоречит `build.gradle.kts:46-50`

**Симптом:** `build.gradle.kts:46-50` явно предупреждает: «Статический хардкод здесь убран: некорректное содержимое (Android-Studio, smartphone вместо manufacturer/model/WxH) приводило к error 15 на messages.* (VK отбрасывает по UA). VkUserAgent.get() генерируется динамически».

Но в `VkAuthWebViewScreenV2.kt:241-243` UA снова хардкожен: `"Mozilla/5.0 (Linux; Android 13; HOTWAV Cyber 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"`.

Для OAuth WebView (`OAuthWebViewActivity.kt:252-254`) — то же хардкод. Там это работает (OAuth flow не требует корректного UA), но для m.vk.ru (mobile web SPA) VK может вести себя странно на устройствах, отличных от HOTWAV Cyber 15.

**Исправление:** использовать system default UA для WebView (Chrome Mobile на устройстве пользователя):

```kotlin
// Заменить:
// userAgentString = "Mozilla/5.0 (Linux; Android 13; HOTWAV Cyber 15) ..."
// На:
userAgentString = WebSettings.getDefaultUserAgent(ctx)
// Или вообще убрать настройку — WebView использует system default автоматически
```

**Альтернатива:** динамически построить UA на основе `Build.MANUFACTURER` + `Build.MODEL` + `Build.VERSION.RELEASE` + актуального Chrome version.

**Оценка:** 0.5д.

---

### 🟡 P1-7: `network_security_config.xml` не содержит `.ru` домены

**Симптом:** VK мигрирует с `.com` на `.ru` (2025-2026): `vk.com → vk.ru`, `m.vk.com → m.vk.ru`, `id.vk.com → id.vk.ru`, `login.vk.com → login.vk.ru`, `oauth.vk.com → oauth.vk.ru`, `api.vk.com → api.vk.ru` (в процессе). Дефолтный `mobileWebHost = m.vk.ru`.

`network_security_config.xml` содержит только `.com` домены: `vk.com`, `vkontakte.ru`, `userapi.com`, `vk.me`, `mycdn.me`, `vk-cdn.net`, `id.vk.com`, `oauth.vk.com`.

**Влияние:** НЕ блокирует загрузку m.vk.ru (`base-config` с `cleartextTrafficPermitted="false"` + trust-anchors system+user применяется ко всем доменам). Но:
- VK-специфичные domain-config правила (например, дополнительные trust-anchors) не применяются к `.ru` доменам.
- Подозрительно — будто конфиг не обновлялся с момента миграции VK на `.ru`.

**Исправление:** добавить `.ru` домены в `domain-config`:

```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">vk.com</domain>
    <domain includeSubdomains="true">vk.ru</domain>
    <domain includeSubdomains="true">vkontakte.ru</domain>
    <domain includeSubdomains="true">userapi.com</domain>
    <domain includeSubdomains="true">vk.me</domain>
    <domain includeSubdomains="true">mycdn.me</domain>
    <domain includeSubdomains="true">vk-cdn.net</domain>
    <domain includeSubdomains="true">id.vk.com</domain>
    <domain includeSubdomains="true">id.vk.ru</domain>
    <domain includeSubdomains="true">oauth.vk.com</domain>
    <domain includeSubdomains="true">oauth.vk.ru</domain>
    <domain includeSubdomains="true">login.vk.com</domain>
    <domain includeSubdomains="true">login.vk.ru</domain>
    <domain includeSubdomains="true">api.vk.com</domain>
    <domain includeSubdomains="true">api.vk.ru</domain>
    <domain includeSubdomains="true">web.api.vk.ru</domain>
    <domain includeSubdomains="true">vkvideo.ru</domain>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</domain-config>
```

**Оценка:** 0.2д.

---

### 🟡 P2-8: Мёртвый код — старый `VkAuthWebViewScreen` в `AuthActivity.kt:1944-2800`

**Симптом:** В `AuthActivity.kt` остался СТАРЫЙ private composable `VkAuthWebViewScreen` (~1200 строк), который **НЕ вызывается** (сейчас вызывается V2 из `AuthActivity.kt:717`). Включает:
- `VK_2FA_CURSOR_FIX_JS` (JS-инъекция для VK ID cursor fix)
- `VK_INPUT_HARDENING_JS` (JS-инъекция для ad-block)
- Старую реализацию с `LaunchedEffect(state)`, `DisposableEffect(Unit)` с cookie polling, `AndroidView { FixedInputWebView(ctx).apply { ... } }`.
- Этот код частично дублирует V2, но имеет ДРУГУЮ логику (например, `tryReadWebToken` polling, который удалён в V2).

**Влияние:**
- Вводит в заблуждение при ревью (какой из двух VkAuthWebViewScreen активный?).
- Увеличивает размер `AuthActivity.kt` до 2953 строк (большой файл = медленный IDE, сложный navigation).
- При поиске по коду (grep) находит оба варианта.

**Исправление:** удалить старый `VkAuthWebViewScreen` + `VK_2FA_CURSOR_FIX_JS` + `VK_INPUT_HARDENING_JS` из `AuthActivity.kt`. Если JS-инъекции нужны в V2 — перенести в `VkAuthWebViewScreenV2.kt`.

**Оценка:** 0.5д (включая проверку что ничего не сломается).

---

### 🟡 P2-9: Cookie polling каждые 1 сек в `Dispatchers.Default`

**Симптом:** `VkAuthWebViewScreenV2.kt:184` — polling CookieManager каждую 1 сек в `Dispatchers.Default`. До 5 мин (300с) в normal mode. Это стабильный CPU wakeup каждую секунду.

На Android Doze (когда экран выключен) polling может мешать (хотя в auth flow экран обычно включён).

**Исправление:** уменьшить интервал до 500мс (быстрее находит remixsid) ИЛИ адаптивный интервал: 500мс первые 30 сек, потом 2 сек. Также можно использовать `CookieManager.getInstance().getCookie(url)` только когда `onPageFinished` срабатывает (event-driven), а не polling.

**Альтернатива:** подписаться на `WebViewClient.onPageFinished` и проверять cookies только тогда.

**Оценка:** 0.5д.

---

### 🟡 P2-10: `tryLaunchIntentUrl`/`tryLaunchCustomScheme`/`tryLaunchMarketUrl` дублированы

**Симптом:** В `VkAuthWebViewScreenV2.kt:627-660` — private функции `tryLaunchIntentUrl`/`tryLaunchCustomScheme`/`tryLaunchMarketUrl` через `re.pinok.SovaApp.get()` (reflection-style доступ к context).

В `AuthActivity.kt` (старый `VkAuthWebViewScreen`) — те же функции как private top-level.

TODO в коде: «вынести tryLaunch* в отдельный object, чтобы переиспользовать без дублирования».

**Исправление:** создать `auth/IntentLauncher.kt`:

```kotlin
object IntentLauncher {
    fun launchIntentUrl(url: String): Boolean { ... }
    fun launchCustomScheme(url: String, scheme: String): Boolean = launchIntentUrl(url)
    fun launchMarketUrl(url: String): Boolean = launchIntentUrl(url)
}
```

Использовать из V2 и (если нужно) из старого `VkAuthWebViewScreen` (который удаляется в P2-8).

**Оценка:** 0.3д.

---

## 9. План внедрения (по приоритетам)

### Sprint AUTH-FIX-1 (1 день) — Critical fixes

| # | Задача | Файл | Оценка | Эффект |
|---|--------|------|--------|--------|
| 1 | **P0-1**: `PlayerConnection.init` guard — skip если `!hasValidToken()` | `SovaApp.kt:919` | 0.5д | m.vk.ru грузится с первого раза в auth flow |
| 2 | **P0-2**: добавить `setWebChromeClient` в `VkAuthWebViewScreenV2.factory` | `VkAuthWebViewScreenV2.kt:267` | 0.3д | JS dialogs/window.open/progress работают |
| 3 | **P0-3**: safety-net — reload через 6 сек если `onPageStarted` не сработал | `VkAuthWebViewScreenV2.kt` (DisposableEffect) | 0.2д | recovery при chromium starvation |
| 4 | **P1-4**: `CookieManager.removeAllCookies` перед `loadUrl` (только normal mode) | `VkAuthWebViewScreenV2.kt:264` | 0.2д | нет stale cookies → нет false onTokenExchange |

**Итого:** 1.2д (с тестированием — 1.5д). После этого «m.vk.ru не открывается» должно уйти.

### Sprint AUTH-FIX-2 (1 день) — Stability

| # | Задача | Файл | Оценка |
|---|--------|------|--------|
| 5 | **P1-5**: убрать `setLayerType(LAYER_TYPE_SOFTWARE)` (проверить input fix без него) | `VkAuthWebViewScreenV2.kt:255` | 0.3д + тест |
| 6 | **P1-6**: использовать `WebSettings.getDefaultUserAgent(ctx)` вместо хардкода | `VkAuthWebViewScreenV2.kt:241` | 0.3д |
| 7 | **P1-7**: добавить `.ru` домены в `network_security_config.xml` | `res/xml/network_security_config.xml` | 0.2д |

**Итого:** 0.8д.

### Sprint AUTH-FIX-3 (1 день) — Cleanup

| # | Задача | Файл | Оценка |
|---|--------|------|--------|
| 8 | **P2-8**: удалить мёртвый `VkAuthWebViewScreen` из `AuthActivity.kt:1944-2800` | `AuthActivity.kt` | 0.5д |
| 9 | **P2-9**: адаптивный cookie polling (500мс → 2 сек) | `VkAuthWebViewScreenV2.kt:184` | 0.3д |
| 10 | **P2-10**: вынести `tryLaunch*` в `IntentLauncher` object | новый `auth/IntentLauncher.kt` | 0.3д |

**Итого:** 1.1д.

### Sprint AUTH-FIX-4 (2 дня) — Architectural

| # | Задача | Файл | Оценка |
|---|--------|------|--------|
| 11 | Split `ExchangeAuthRepository.kt` (3058 строк) на `AuthOrchestrator` + `TokenRefreshUseCase` + `SilentAuthUseCase` + `SaveOAuthTokenUseCase` + `CookieStorageUseCase` | `auth/exchange/` (4 новых файла) | 1.5д |
| 12 | Split `AuthActivity.kt` (2953 строк) — вынести `LandingScreen` в `auth/LandingScreen.kt`, `ValidationCodeForm` в `auth/ValidationCodeForm.kt`, `PendingAuthResult` в `auth/PendingAuthResult.kt` | `auth/` (3 новых файла) | 0.5д |

**Итого:** 2д.

---

## 10. Резюме для разработчика

**Что сделать прямо сейчас (1.2д, fixes «m.vk.ru не открывается»):**

1. **P0-1:** В `SovaApp.kt:919` обернуть `PlayerConnection.init(this)` в `if (tokenStorage.hasValidToken())`. Это УБИРАЕТ root cause «m.vk.ru не грузится» — PlayerService больше НЕ блокирует main thread в auth flow.

2. **P0-2:** В `VkAuthWebViewScreenV2.kt` добавить `setWebChromeClient` (с `onProgressChanged`, `onJsAlert/Confirm/Prompt`, `onConsoleMessage`, `onCreateWindow`). Без этого VK ID SDK на m.vk.ru не может полноценно работать (window.open, JS dialogs).

3. **P0-3:** В `VkAuthWebViewScreenV2.kt` добавить safety-net — reload через 6 сек если `onPageStarted` не сработал (loading остался true). Это recovery для chromium starvation.

4. **P1-4:** В `VkAuthWebViewScreenV2.kt` перед `loadUrl` очистить `CookieManager.removeAllCookies` (только normal mode, НЕ silentMode). Это убирает false onTokenExchange от stale cookies.

**После этих 4 фиксов «m.vk.ru не открывается» должно уйти.** Если не уйдёт — включить `WebChromeClient.onConsoleMessage` и посмотреть JS console output из m.vk.ru — там будет видно что именно идёт не так (JS error, network error, и т.д.).

**Что сделать потом (1.9д, stability + cleanup):** P1-5 (убрать software rendering), P1-6 (dynamic UA), P1-7 (.ru домены в network_security_config), P2-8 (удалить мёртвый код), P2-9 (адаптивный polling), P2-10 (IntentLauncher object).

**Что сделать в следующем спринте (2д, архитектурный рефакторинг):** P2-11 (split ExchangeAuthRepository), P2-12 (split AuthActivity).
