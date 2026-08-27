package re.pinok.auth

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.auth.exchange.AuthErrorKind
import re.pinok.auth.exchange.AuthResult
import re.pinok.auth.exchange.AuthState
import re.pinok.auth.exchange.ExchangeAuthRepository
import re.pinok.auth.exchange.LongPollCredentials
import re.pinok.auth.exchange.RemixsidCapturer
import re.pinok.auth.exchange.ValidationType
import re.pinok.auth.exchange.WebTokenAuth
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * UI state + intent handler for [AuthActivity].
 *
 * Fully mirrors decompiled VK 8.178 auth flow:
 *   - AuthModel (interface) → coordinates auth state transitions
 *   - VkAuthState builders → grant_type selection
 *   - AuthByExchangeToken → actual HTTP calls
 *
 * State machine:
 *   Idle → Loading → Success | NeedValidation | Error
 *   NeedValidation → Loading → Success | Error | NeedValidation (re-send)
 *
 * On app start, tries trusted_hash re-login (mirrors VK's auto-login).
 * After Success, prefetches LongPoll credentials for messenger.
 */
class AuthViewModel(
    private val repo: ExchangeAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Phone captured from step 1 — needed for 2FA and re-send. */
    private var lastPhone: String = ""

    /** Password captured from step 1 — needed for re-send (VK requires grant_type=password
     *  with sid to switch validation channel, see decompiled VkAuthState.b). */
    private var lastPassword: String = ""

    /** sid captured from need_validation — needed for submit2FaCode. */
    private var pendingSid: String = ""

    /** Current validation type — for re-send selection. */
    private var pendingValidationType: ValidationType = ValidationType.SMS

    /** Whether we attempted trusted_hash login already (avoid infinite loop). */
    private var trustedHashAttempted: Boolean = false

    /**
     * §51 #WEB-TOKEN-DEAD-SESSION-CLEAR (2026-08-05):
     * Флаг: чистили ли уже dead session (cookies+storage) в этом submitWebToken
     * вызове. Гарантирует только ОДНА чистка — иначе при каждой неудаче
     * (например ручной логин который занимает >25 сек) чистили бы снова.
     * Сбрасывается в false при новом submitWebToken вызове (новый AuthActivity).
     */
    private var cookiesClearedForRetry: Boolean = false

    /** Fix #113: Текущая асинхронная операция (submitWebToken / submitCredentials / …).
     *  Сохраняется чтобы [cancel] мог прервать реально идущий network-запрос, а не
     *  только сбросить state. Без этого submitWebToken продолжает работать в фоне
     *  даже после cancel() → state возвращается в Loading/Success и UI «подвисает»
     *  — кнопка офлайн-режима остаётся disabled, пользователь не может войти. */
    private var currentJob: kotlinx.coroutines.Job? = null

    // =====================================================================
    // Auto-login (trusted hash)
    // =====================================================================

    /**
     * Attempt silent login via trusted_hash on app start.
     * Mirrors VK's VkAutoLoginComponent / silent auth flow.
     * If this succeeds, the user never sees the login form.
     *
     * Uses tryTrustedHashLoginFullState() which returns the complete AuthState
     * with all fields properly parsed and persisted (exchange_token, scope, etc.).
     */
    fun tryAutoLogin() {
        if (trustedHashAttempted) return
        if (repo.isSignedIn()) {
            AppLog.i(TAG, "Already signed in, skipping auto-login")
            return
        }

        // #NETWORK-RESILIENCE (2026-08-04): OfflineWithCache — offline-first вход.
        // Если сеть недоступна И есть сохранённый протухший токен с user_id —
        // НЕ пытаемся trusted_hash login (он упадёт по IOException после 3 retry
        // = 7 сек задержки). Сразу показываем OfflineWithCache → пользователь
        // попадает в главный экран с кэшированными данными + баннер «Нет сети».
        // tryAutoLogin будет вызван повторно когда NetworkObserver сообщит о
        // появлении сети (см. AuthActivity.onResume / networkObserver listener).
        val offlineState = repo.offlineWithCacheState()
        if (offlineState != null) {
            AppLog.i(TAG, "tryAutoLogin: OFFLINE + cached session " +
                "(user=${offlineState.cachedUserId}, token expired ${offlineState.tokenExpiredAt}) " +
                "→ OfflineWithCache (skipping trusted_hash — would fail anyway after 7s retry)")
            _state.value = offlineState
            // НЕ выставляем trustedHashAttempted=true — при появлении сети
            // tryAutoLogin должен снова сработать и перейти в Success.
            return
        }

        if (!repo.canTrustedHashLogin()) {
            AppLog.d(TAG, "No trusted_hash for auto-login")
            return
        }
        trustedHashAttempted = true
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                repo.tryTrustedHashLoginFullState()
            }
            if (state is AuthState.Success) {
                _state.value = state
                prefetchLongPoll()
            } else {
                AppLog.i(TAG, "Trusted hash login failed, showing login form")
                _state.value = AuthState.Idle
            }
        }
    }

    // =====================================================================
    // Step 1 — phone + password
    // =====================================================================

    /** Submit phone + password. Mirrors VkAuthState.b(sid=null). */
    fun submitCredentials(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _state.value = AuthState.Error(AuthErrorKind.INVALID_CREDENTIALS, "Введите телефон и пароль")
            return
        }
        lastPhone = phone
        lastPassword = password
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.signIn(phone, password) }
            handleAuthResult(result)
        }
    }

    // =====================================================================
    // Step 2 — 2FA code
    // =====================================================================

    /** Submit 2FA code. Mirrors VkAuthState.b(sid!=null) or VkAuthState.d(z=false). */
    fun submit2FaCode(code: String) {
        if (code.isBlank() || pendingSid.isBlank()) {
            _state.value = AuthState.Error(AuthErrorKind.INVALID_CREDENTIALS, "Введите код")
            return
        }
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.submit2FaCode(lastPhone, pendingSid, code)
            }
            handleAuthResult(result)
        }
    }

    // =====================================================================
    // Push-approved login (no password)
    // =====================================================================

    /**
     * Login without password — push was already approved.
     * Mirrors VkAuthState.d(z=true) → grant_type=without_password.
     */
    fun submitWithoutPassword() {
        if (pendingSid.isBlank()) {
            _state.value = AuthState.Error(AuthErrorKind.UNKNOWN, "Нет sid для push-авторизации")
            return
        }
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.authWithoutPassword(lastPhone, pendingSid)
            }
            handleAuthResult(result)
        }
    }

    // =====================================================================
    // OAuth WebView token (from OAuthWebViewActivity, bypasses flood control)
    // =====================================================================

    /**
     * Submit token obtained via OAuth WebView (oauth.vk.com/authorize).
     * The token comes from VK's own login page, so no flood control.
     * We try to get exchange_token via auth.getExchangeToken for refresh support.
     *
     * #REMIXSID-CAPTURE (§41.22): [remixsid] — опциональный remixsid из
     * CookieManager, захваченный в [re.pinok.auth.OAuthWebViewActivity]
     * после in-app web-логина. Если передан (non-null) → сохраняется
     * напрямую, Path 1.5 (silentRefreshViaRemixsid) включается.
     *
     * Если null (external browser flow, или WebView не нашёл cookie) →
     * после сохранения токена запускается [RemixsidCapturer.capture] как
     * best-effort: скрытый WebView пытается silent OAuth sign-in и
     * захватить remixsid. Работает только если CookieManager уже имеет
     * VK session от предыдущей in-app WebView сессии.
     *
     * §55 #SSO-FULL-COOKIE-SET / §57 #COOKIE-CAPTURE-UNIFY: расширено до
     * 9 кук браузерного набора. p/remixnsid критичны для cross-IP silent
     * refresh, httoken/remixnttpid/remixuacck/remixuas/remixdmgr/remixmvk-fp —
     * anti-CSRF/anti-fraud, VK часто отвергает silent refresh без них (SSO loop §54).
     * Все 6 доп. кук опциональны (null = не переданы, storage не патчит).
     */
    fun submitOAuthToken(
        accessToken: String,
        userId: Long,
        remixsid: String? = null,
        pCookie: String? = null,
        remixnsid: String? = null,
        // §55 #SSO-FULL-COOKIE-SET: 6 доп. кук браузерного набора.
        httoken: String? = null,
        remixnttpid: String? = null,
        remixuacck: String? = null,
        remixuas: String? = null,
        remixdmgr: String? = null,
        remixmvkFp: String? = null,
    ) {
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.saveOAuthToken(
                    accessToken, userId, remixsid, pCookie, remixnsid,
                    httoken, remixnttpid, remixuacck, remixuas, remixdmgr, remixmvkFp,
                )
            }
            handleAuthResult(result)

            // #REMIXSID-CAPTURE (§41.22): Если после saveOAuthToken remixsid
            // всё ещё нет — запускаем best-effort capture через скрытый WebView.
            // Это НЕ блокирует UI (запуск в отдельной coroutine) и не влияет
            // на auth result (токен уже сохранён, app работает).
            //
            // Зачем: Path 1.5 (silentRefreshViaRemixsid) позволяет silent refresh
            // access_token при переключении WiFi↔Mobile без перезапуска
            // AuthActivity. Без remixsid → #RELOGIN-FORCE (§41.21) → ручной
            // re-login. С remixsid → silent refresh ~200ms → нет прерывания.
            if (result is AuthState.Success && !repo.hasRemixsid()) {
                viewModelScope.launch {
                    val captured = RemixsidCapturer.capture(SovaApp.get())
                    if (captured != null) {
                        withContext(Dispatchers.IO) { repo.saveRemixsid(captured) }
                        AppLog.i(TAG, "RemixsidCapturer: SUCCESS — remixsid saved " +
                            "(len=${captured.remixsid.length}, " +
                            "p=${if (captured.pCookie != null) "yes" else "no"}, " +
                            "remixnsid=${if (captured.remixnsid != null) "yes" else "no"}), " +
                            "Path 1.5 enabled (cross-IP silent refresh)")
                    } else {
                        AppLog.w(TAG, "RemixsidCapturer: no remixsid captured — " +
                            "Path 1.5 unavailable (use in-app WebView login once for silent network switching)")
                    }
                }
            }
        }
    }

    /**
     * PRIMARY auth method — чтение web_token из m.vk.ru localStorage.
     *
     * Запускается после того, как AuthActivity обнаружил remixsid в CookieManager.
     * m.vk.ru JS автоматически обменял remixsid на токен через login.vk.com
     * и сохранил его в localStorage. Мы читаем оттуда через evaluateJavascript().
     *
     * @param remixsid Значение remixsid (для сохранения в storage и refresh)
     * @param webView  WebView instance на m.vk.ru
     */
    fun submitWebToken(remixsid: String, webView: WebView) {
        _state.value = AuthState.Loading
        // §51 #WEB-TOKEN-DEAD-SESSION-CLEAR: сбрасываем флаг на новом вызове
        // (новый AuthActivity lifecycle — даём шанс ещё одной чистке если понадобится).
        cookiesClearedForRetry = false
        currentJob = viewModelScope.launch {
            // Fix #104: retry-цикл для истёкших токенов.
            //
            // WebTokenAuth.fullAuthFlow (Fix #103) уже отбраковывает истёкший
            // токен и удаляет его из localStorage, после чего m.vk.ru JS должен
            // получить свежий. Но JS может не сработать с первого раза (нужен
            // re-load страницы, либо сессия login.vk.com тоже устарела).
            //
            // Стратегия:
            //   1. Пытаемся fullAuthFlow. Если Success — сохраняем и выходим.
            //   2. Если saveWebTokenResult вернул EXPIRED (Fix #105) —
            //      перезагружаем m.vk.ru и пробуем снова.
            //   3. Максимум MAX_EXPIRED_RETRIES попыток, затем показываем
            //      пользователю экран логина (не зависаем на splash).
            //
            // §51 #WEB-TOKEN-DEAD-SESSION-CLEAR (2026-08-05):
            //   4. Если fullAuthFlow упал с НЕ-EXPIRED ошибкой (токен не появился
            //      за 25 сек — remixsid есть, но VK не обменивает) — чистим
            //      dead remixsid из CookieManager+storage (clearDeadSessionForRetry).
            //      Это разрывает auto-relogin loop: без чистки MainActivity
            //      перезапустит AuthActivity → снова найдёт мёртвый remixsid →
            //      цикл, пользователь никогда не увидит форму логина с 2FA.
            //      С чисткой следующий запуск AuthActivity покажет форму логина
            //      (remixsid нет) → пользователь введёт phone+pass → 2FA →
            //      новый валидный токен.
            //      НЕ делаем retry внутри submitWebToken — после очистки WebView
            //      показывает форму логина, и пользователю нужно >25 сек на
            //      ручной ввод (таймаут fullAuthFlow). Просто возвращаем Error,
            //      AuthActivity/MainActivity перезапустится с чистой сессией.
            //      Флаг cookiesClearedForRetry гарантирует только ОДНА чистка
            //      (иначе чистили бы при каждой неудаче, в т.ч. при нормальном
            //      ручном логине который занимает время).
            val maxRetries = 2
            var result: AuthState
            var attempt = 0
            do {
                attempt++
                result = withContext(Dispatchers.Main) {
                    val webResult = WebTokenAuth.fullAuthFlow(webView)
                    if (webResult.isFailure) {
                        val err = webResult.exceptionOrNull()?.message ?: "unknown error"
                        AppLog.e(TAG, "WebTokenAuth failed (attempt $attempt/$maxRetries): $err")
                        // Если ошибка содержит "истёк" — это EXPIRED, пробуем ещё раз.
                        // Иначе — другая ошибка (network, JS error), отдаём наверх.
                        val isExpired = err.contains("истёк", ignoreCase = true) ||
                            err.contains("expired", ignoreCase = true)
                        return@withContext if (isExpired && attempt < maxRetries) {
                            AuthState.Error(AuthErrorKind.EXPIRED, err)
                        } else {
                            AuthState.Error(
                                AuthErrorKind.UNKNOWN,
                                "Не удалось получить токен: $err"
                            )
                        }
                    }
                    val token = webResult.getOrThrow()
                    AppLog.i(TAG, "WebTokenAuth success — saving token for user_id=${token.userId}")

                    // Fix #WEB-TOKEN-FALLBACK (2026-08-04): remixsid может быть
                    // пустым если web_token найден через localStorage polling
                    // (CookieManager не видит remixsid на Android 7+). В этом
                    // случае передаём null чтобы НЕ перезаписать существующий
                    // remixsid в storage (если он был сохранён ранее).
                    val remixsidToSave = remixsid.takeIf { it.isNotBlank() }
                    repo.saveWebTokenResult(
                        accessToken = token.accessToken,
                        userId = token.userId,
                        expiresAt = token.expiresAt,
                        satToken = token.satToken,
                        logoutHash = token.logoutHash,
                        remixsid = remixsidToSave,
                    )
                }

                // Если токен истёк — reload m.vk.ru и retry.
                // saveWebTokenResult вернёт AuthState.Error(EXPIRED, ...) (Fix #105).
                val isExpired = result is AuthState.Error &&
                    result.kind == AuthErrorKind.EXPIRED
                if (isExpired && attempt < maxRetries) {
                    AppLog.w(TAG, "submitWebToken: токен истёк (attempt $attempt/$maxRetries) — " +
                        "reload m.vk.ru и retry")
                    withContext(Dispatchers.Main) {
                        // Очищаем localStorage чтобы m.vk.ru JS точно получил свежий токен
                        webView.evaluateJavascript(
                            "localStorage.removeItem('7879029:web_token:login:auth')",
                            null
                        )
                        // Перезагружаем страницу — это заставит m.vk.ru JS
                        // переинициализироваться и запросить новый токен.
                        webView.loadUrl("https://m.vk.ru")
                    }
                    // Даём m.vk.ru время на load + JS-обмен.
                    kotlinx.coroutines.delay(3000L)
                }

                // §51 #WEB-TOKEN-DEAD-SESSION-CLEAR: не-EXPIRED failure на
                // последней попытке — чистим dead session (CookieManager+storage),
                // чтобы следующий запуск AuthActivity показал форму логина вместо
                // auto-relogin с мёртвым remixsid. НЕ retry внутри submitWebToken —
                // после очистки пользователю нужно >25 сек на ручной ввод.
                //
                // §58 #2FA-SESSION-WIPE-FIX (2026-08-05, лог 19:47-19:48):
                // ПЕРЕД чисткой кук попробуем Path 1.5 (silentRefreshViaRemixsid).
                // КОРНЕВАЯ ПРИЧИНА сброса 2FA: после VK app SSO remixsid ВАЛИДЕН
                // (88 символов, только что получен), но m.vk.ru/feed НЕ запускает
                // VK ID SDK → web_token не появляется в localStorage → 25 сек
                // таймаут → clearDeadSessionForRetry → УДАЛЯЕТ ВСЕ КУКИ (вкл.
                // валидный remixsid) → 2FA сбрасывается, пользователь вводит заново.
                //
                // ФИКС: Path 1.5 делает HTTP POST на login.vk.ru/?act=web_token с
                // Cookie: remixsid=... → VK вернёт access_token за 1-2 сек, БЕЗ
                // сброса 2FA сессии. ensureFreshToken(force=true) сохраняет токен
                // в storage. Если Path 1.5 успешен — возвращаем Success, куки НЕ
                // трогаем. Если Path 1.5 тоже упал (remixsid действительно мёртв) —
                // тогда чистим dead session как раньше.
                val isUnknownError = result is AuthState.Error &&
                    result.kind == AuthErrorKind.UNKNOWN
                if (isUnknownError && !cookiesClearedForRetry) {
                    // #AUTH-LOOP-FIX (2026-08-07): перед Path 1.5 fallback,
                    // извлекаем userId из cookie remixsid_user и сохраняем в storage.
                    // Path 1.5 (silentRefreshViaRemixsid) требует userId для
                    // `remixsid_user=<userId>` cookie header. Без userId Path 1.5
                    // падает → clearDeadSessionForRetry → loop (пользователь видит
                    // форму логина снова и снова).
                    //
                    // Сценарий: m.vk.ru login → remixsid валиден, но WebTokenAuth
                    // таймаутит (m.vk.ru/feed редиректит, VK ID SDK не init).
                    // remixsid_user cookie УЖЕ есть в CookieManager (VK ставит его
                    // при логине) — извлекаем и сохраняем в storage.
                    if (repo.userId() == 0L) {
                        val cookieUserId = withContext(Dispatchers.Main) {
                            re.pinok.auth.getUserIdFromCookieManager()
                        }
                        var extractedUserId = cookieUserId
                        // #SSO-USERID-EXTRACT: remixsid_user cookie нет в SSO-flow.
                        // Извлекаем userId из window.init VK ID SDK через JS.
                        if (extractedUserId <= 0L) {
                            extractedUserId = try {
                                re.pinok.auth.exchange.WebTokenAuth.readUserIdFromWindowInit(webView)
                            } catch (e: Exception) {
                                AppLog.w(TAG, "submitWebToken: readUserIdFromWindowInit failed: ${e.message}")
                                0L
                            }
                        }
                        if (extractedUserId > 0L) {
                            AppLog.i(TAG, "submitWebToken: #AUTH-LOOP-FIX — извлёк userId=$extractedUserId " +
                                "(${if (cookieUserId > 0L) "cookie" else "window.init"}), сохраняю в storage перед Path 1.5")
                            withContext(Dispatchers.IO) {
                                repo.setUserId(extractedUserId)
                            }
                        } else {
                            AppLog.w(TAG, "submitWebToken: #AUTH-LOOP-FIX — userId=0 в storage и " +
                                "ни в cookie remixsid_user, ни в window.init. Path 1.5 скорее " +
                                "всего упадёт (нет userId для remixsid_user cookie header).")
                        }
                    }
                    // §58: Path 1.5 fallback ПЕРЕД чисткой кук.
                    val path15Token = withContext(Dispatchers.IO) {
                        try {
                            repo.ensureFreshToken(force = true)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "submitWebToken: §58 Path 1.5 fallback exception: ${e.message}")
                            null
                        }
                    }
                    if (path15Token != null) {
                        // Path 1.5 УСПЕШЕН — remixsid обменян на access_token.
                        // ensureFreshToken уже сохранил токен в storage.
                        // Возвращаем Success, куки НЕ трогаем (2FA сессия сохранена).
                        val p15UserId = repo.userId()
                        val p15ExpiresAt = repo.expiresAt()
                        AppLog.i(TAG, "submitWebToken: §58 #2FA-SESSION-WIPE-FIX — Path 1.5 " +
                            "silentRefreshViaRemixsid SUCCESS — remixsid обменян на " +
                            "access_token через HTTP (user_id=$p15UserId), куки НЕ очищены " +
                            "(2FA сессия сохранена, повторный ввод не требуется)")
                        result = AuthState.Success(
                            AuthResult(
                                accessToken = path15Token,
                                exchangeToken = repo.exchangeToken(),
                                userId = p15UserId,
                                expiresIn = if (p15ExpiresAt > 0L)
                                    (p15ExpiresAt - System.currentTimeMillis()) / 1000 else 0L,
                                scope = repo.scope(),
                            )
                        )
                    } else {
                        // Path 1.5 тоже упал. Различаем две причины:
                        //
                        // #VKID-SESSION-WIPE-GUARD (логкэт 2026-08-07 16:48:31):
                        //   - wasLastSilentRefreshDefinitivelyDead()=true → remixsid
                        //     ОДНОЗНАЧНО мёртв (VK вернул явный auth rejection).
                        //     Чистим dead session — единственный шанс = re-login с 2FA.
                        //   - wasLastSilentRefreshDefinitivelyDead()=false → CONTRACT
                        //     failure (wrong origin / unauthorized / parsing bug / network).
                        //     remixsid может быть ВАЛИДЕН (VK реально вернул токен, но мы
                        //     не распарсили). НЕ чистим сессию — иначе уничтожим рабочую
                        //     2FA-сессию и заставим пользователя перелогиниваться без нужды.
                        //     Возвращаем Error — пользователь может нажать "повторить".
                        val definitivelyDead = withContext(Dispatchers.IO) {
                            try { repo.wasLastSilentRefreshDefinitivelyDead() } catch (_: Exception) { false }
                        }
                        if (definitivelyDead) {
                            AppLog.w(TAG, "submitWebToken: §58 Path 1.5 упал — remixsid " +
                                "definitively dead (VK explicit rejection) — clearing dead " +
                                "remixsid (CookieManager+storage). Next AuthActivity launch " +
                                "will show login form → 2FA.")
                            cookiesClearedForRetry = true
                            withContext(Dispatchers.IO) {
                                repo.clearDeadSessionForRetry()
                            }
                            // Чистим localStorage WebView от старых web_token ключей,
                            // чтобы следующий запуск не подобрал истёкший токен.
                            withContext(Dispatchers.Main) {
                                webView.evaluateJavascript(
                                    "(function(){Object.keys(localStorage)" +
                                        ".filter(function(k){return k.indexOf(':web_token:login:auth')>-1})" +
                                        ".forEach(function(k){localStorage.removeItem(k)})})();",
                                    null
                                )
                                webView.clearCache(true)
                            }
                        } else {
                            AppLog.w(TAG, "submitWebToken: §58 Path 1.5 упал по CONTRACT failure " +
                                "(wrong origin / unauthorized / parsing / network) — remixsid " +
                                "МОЖЕТ быть валиден. НЕ чистим сессию (#VKID-SESSION-WIPE-GUARD). " +
                                "Возвращаем Error — пользователь может повторить.")
                            // НЕ вызываем clearDeadSessionForRetry — сессия сохранена.
                            // НЕ чистим localStorage — web_token может появиться при retry.
                            // Возвращаем Error с понятным сообщением.
                            result = AuthState.Error(
                                AuthErrorKind.UNKNOWN,
                                "Не удалось получить токен (контракт VK изменился). " +
                                "Сессия сохранена — попробуйте ещё раз."
                            )
                        }
                    }
                }
            } while (isExpired && attempt < maxRetries)

            handleAuthResult(result)
        }
    }

    /** Show OAuth WebView error in the UI. */
    fun setOAuthError(message: String) {
        _state.value = AuthState.Error(AuthErrorKind.UNKNOWN, message)
    }

    // =====================================================================
    // Re-send 2FA code
    // =====================================================================

    /**
     * Re-send 2FA code via a different method.
     * VK allows switching between SMS, push, email, IVR.
     */
    fun resendCode(via: ValidationType) {
        if (pendingSid.isBlank()) return
        _state.value = AuthState.Loading
        currentJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.resendValidationCode(lastPhone, lastPassword, pendingSid, via)
            }
            // After resend, VK returns a new NeedValidation with updated sid.
            if (result is AuthState.NeedValidation) {
                pendingSid = result.validationSid
                pendingValidationType = result.validationType
            }
            _state.value = result
        }
    }

    // =====================================================================
    // Cancel
    // =====================================================================

    /** Reset back to Idle (e.g. user taps "Back" from 2FA screen или «Отмена»).
     *  Fix #113: отменяем реально идущий network-запрос, иначе coroutine
     *  продолжит работу и перезапишет state после cancel() — UI снова уйдёт
     *  в Loading и все кнопки (включая офлайн-режим) заблокируются. */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = AuthState.Idle
        pendingSid = ""
        lastPassword = ""
    }

    // =====================================================================
    // Internal
    // =====================================================================

    private fun handleAuthResult(result: AuthState) {
        if (result is AuthState.NeedValidation) {
            pendingSid = result.validationSid
            pendingValidationType = result.validationType
        }
        _state.value = result
        if (result is AuthState.Success) {
            prefetchLongPoll()
        }
    }

    /**
     * Pre-fetch LongPoll credentials in the background so the messenger
     * screen has them ready. Mirrors VK's post-auth LongPoll init.
     */
    private fun prefetchLongPoll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val creds: LongPollCredentials? = repo.fetchLongPoll()
                if (creds != null) {
                    AppLog.i(TAG, "LongPoll prefetched (ts=${creds.ts})")
                } else {
                    AppLog.w(TAG, "LongPoll prefetch returned null — IM will retry on demand")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "LongPoll prefetch error: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}