package re.pinok.auth.exchange

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.data.local.SovaPrefs
import re.pinok.feature.calls.CallsAuth
import re.pinok.util.AppLog
import re.pinok.util.ExponentialBackoff
import re.pinok.util.NetworkObserver
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Auth flow orchestrator — cloned from decompiled VK 8.178.
 *
 * Source: com.vk.superapp.api.internal.oauthrequests.AuthByExchangeToken
 *         + com.vk.auth.main.AuthModel (interface)
 *         + xsna.ev4 (AuthResult parser: ev4.a for success, ev4.b for error)
 *         + com.vk.superapp.api.states.VkAuthState (grant_type builders)
 *
 * ⚠️ ENDPOINT MAP (HISTORY.md #5, #7, #18):
 *   Password / 2FA / trusted_hash / external auth →
 *       POST https://oauth.vk.com/access_token  (требует client_secret)
 *   Exchange token refresh (initiator=expired_token/...) →
 *       POST https://id.vk.com/auth_by_exchange_token  (БЕЗ client_secret)
 *
 *   Эндпоинт id.vk.com/auth_by_exchange_token НЕ поддерживает grant_type=password
 *   (возвращает 404). Это задокументировано в HISTORY.md строки 2231, 2487.
 *
 * The flow mirrors the real VK client:
 *   1. POST oauth.vk.com/access_token  grant_type=password
 *        → AuthResult (success) OR need_validation (2FA) OR error
 *   2a. (2FA with code) POST oauth.vk.com/access_token  grant_type=phone_confirmation_sid
 *        → AuthResult
 *   2b. (push-approved) POST oauth.vk.com/access_token  grant_type=without_password
 *        → AuthResult
 *   2c. (re-send code) POST oauth.vk.com/access_token  grant_type=phone_confirmation_sid + resend=type
 *        → new code sent
 *   3. (trusted_hash re-login) POST oauth.vk.com/access_token  grant_type=trusted_hash
 *        → AuthResult (no password needed)
 *   4. (refresh) POST api.vk.com/method/execute → auth.getExchangeToken
 *        → fresh exchange_token
 *   5. (refresh) POST id.vk.com/auth_by_exchange_token  initiator=expired_token
 *        → new AuthResult
 *   6. (external) POST oauth.vk.com/access_token  grant_type=vk_external_auth
 *        → AuthResult
 */
// Task 21: реализует CallsAuth (фасад :feature:calls) — userId()/remixsid() для
// CallsWebViewScreen/CallScreen. Файл возвращён в :app: пакет re.pinok.auth.exchange —
// кластер из 14 файлов (AuthState/AuthModels/AuthResponseParser/ExchangeTokenStorage/
// CookieRefreshWorker/ExternalBrowserAuth...), same-package ссылки импорта не требуют
// (330 ошибок лога 2026-09-03 — только этот файл). Рантайм-объект тот же.
class ExchangeAuthRepository(
    private val api: ExchangeAuthApi,
    private val storage: ExchangeTokenStorage,
    private val httpClient: OkHttpClient? = null,
    private val prefs: SovaPrefs? = null,
) : CallsAuth {

    private val refreshMutex = Mutex()

    /**
     * #NETWORK-RESILIENCE (2026-08-04): ссылка на [NetworkObserver] для
     * offline-detection. Attache через [attachNetworkObserver] после создания
     * (NetworkObserver создаётся ПОСЛЕ repo в [re.pinok.SovaApp.onCreate],
     * т.к. repo нужен для VKApiClient, а VKApiClient тоже зависит от observer).
     *
     * @Volatile — читается из [offlineWithCacheState] (может быть на UI потоке
     * из [re.pinok.auth.AuthViewModel.tryAutoLogin]) и из [ensureFreshToken]
     * (IO dispatcher). Без @Volatile возможен stale read на multi-core.
     */
    @Volatile
    private var networkObserver: NetworkObserver? = null

    /**
     * Подключает [NetworkObserver] после создания репозитория.
     * Idempotent — повторные вызовы перезаписывают ссылку (безопасно для тестов).
     * Вызывается из [re.pinok.SovaApp.onCreate] сразу после `networkObserver.register()`.
     */
    fun attachNetworkObserver(observer: NetworkObserver) {
        networkObserver = observer
        AppLog.i(TAG, "NetworkObserver attached to ExchangeAuthRepository")
    }

    /**
     * #SESSION-WEB-MECHANISM: application context для скрытого WebView
     * ([HiddenSessionRefresher]). Устанавливается из [re.pinok.SovaApp.onCreate].
     */
    @Volatile
    private var appContext: android.content.Context? = null

    /** Подключает application context (idempotent). Вызывается из SovaApp.onCreate. */
    fun attachAppContext(ctx: android.content.Context) {
        appContext = ctx.applicationContext
    }

    /** True если сеть сейчас недоступна (по [NetworkObserver.isOnline]). */    /** True если сеть сейчас недоступна (по [NetworkObserver.isOnline]). */
    fun isOffline(): Boolean = networkObserver?.isOnline() == false

    /**
     * #NETWORK-RESILIENCE: возвращает [AuthState.OfflineWithCache] если выполнены
     * все условия для offline-first входа:
     *  1. NetworkObserver подключён И сообщает `isOnline() == false`.
     *  2. В storage есть сохранённый `user_id` (> 0) — значит, ранее был успешный вход.
     *  3. `access_token` протух (`expires_at` в прошлом или равен 0 — токен потерян).
     *
     * Если любая из условий не выполнена — возвращает null (caller продолжает
     * обычный flow: trusted_hash login → AuthActivity).
     *
     * UX: пользователь открывает приложение в метро, видит главный экран с
     * кэшированной лентой + баннер «Нет сети», а не AuthActivity «Войдите снова».
     * Как только сеть появляется — [ensureFreshToken] в фоне обновляет токен,
     * [re.pinok.auth.AuthViewModel] переходит в [AuthState.Success].
     */
    fun offlineWithCacheState(): AuthState.OfflineWithCache? {
        val observer = networkObserver ?: return null
        if (observer.isOnline()) return null
        val userId = storage.userId()
        if (userId <= 0L) return null
        val expiresAt = storage.expiresAt()
        // expiresAt == 0 — токен никогда не был сохранён (или storage повреждён).
        // expiresAt > now — токен ещё валиден, offline-state не нужен (Success и так сработает).
        if (expiresAt == 0L || expiresAt > System.currentTimeMillis()) return null
        return AuthState.OfflineWithCache(
            cachedUserId = userId,
            lastSeenMs = expiresAt,  // последнее подтверждённое присутствие = выдача токена
            tokenExpiredAt = expiresAt,
        )
    }

    /**
     * #VKID-SESSION-WIPE-GUARD (переосмыслено в #SESSION-WEB-MECHANISM, 2026-09-24):
     * true = последняя попытка скрытого refresh видела web-сессию ДОКАЗАННО мёртвой
     * (в CookieManager нет ни remixsid, ни p — VK физически не может узнать
     * пользователя). Только тогда caller'у разрешено чистить сессию
     * (clearDeadSessionForRetry). Сетевые/контрактные сбои дают false —
     * сессия может быть жива, чистить cookies НЕЛЬЗЯ.
     * Источник истины: [HiddenSessionRefresher.lastAttemptDefinitivelyDead].
     */
    fun wasLastSilentRefreshDefinitivelyDead(): Boolean =
        HiddenSessionRefresher.lastAttemptDefinitivelyDead


    // =====================================================================
    // Sign-in flows (used by AuthViewModel)
    // =====================================================================

    /** Step 1 — submit phone + password. Mirrors VkAuthState.b(sid=null). */
    suspend fun signIn(phone: String, password: String): AuthState {
        val deviceId = storage.deviceId()
        // #SESSION-WEB-MECHANISM (2026-09-24): пароль НЕ сохраняется на устройстве
        // (в веб-версии пароли не хранятся). ViewModel держит lastPassword в памяти
        // для re-send кода 2FA в течение активной логин-сессии.

        return try {
            // #NETWORK-RESILIENCE: backoff на password login (3 попытки, 1/2/4 сек).
            // Один SocketTimeout при логине больше НЕ прерывает вход — пользователь
            // вводит пароль один раз, backoff даёт VK шанс ответить.
            val json = ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.AUTH_DEFAULT,
                tag = "authByPassword",
            ) {
                api.authByPassword(phone, password, deviceId)
            } ?: return AuthState.Error(
                AuthErrorKind.NETWORK,
                "Нет связи с VK. Проверьте подключение и попробуйте снова.",
            )
            val parsed = parseAuthResponse(json)
            // Fix #331: post-login валидация Direct Auth токена.
            //
            // Сценарий: VK отдаёт через Direct Auth (grant_type=password,
            // client_id=2274003) только web-токен vk1.a.* БЕЗ secret, БЕЗ
            // exchange_token, БЕЗ trusted_hash. Этот токен формально валиден
            // (parseAuthResponse возвращает Success), но VK API отвергает
            // его на чувствительных методах (messages.getLongPollServer,
            // newsfeed.get) с err=1117 "Access token has expired" —
            // misleading сообщение, на самом деле VK distinguishing
            // «настоящий VK-клиент» (по APK signing hash) от third-party.
            //
            // Без этой проверки токен сохраняется, юзер видит «успех», но
            // первый же API-запрос падает с 1117 → notifyTokenInvalidated
            // → AuthActivity loop. Пользователь застревает.
            //
            // Решение: после parseAuthResponse Success, если токен
            // vk1.a.* И exchange_token отсутствует (значит VK его не выдал
            // для этого клиента) — проверяем токен через
            // getExchangeTokenDetailed. Если err=5 — возвращаем понятную
            // ошибку, НЕ сохраняя токен.
            if (parsed is AuthState.Success) {
                val token = parsed.result.accessToken
                val hasExchange = !parsed.result.exchangeToken.isNullOrBlank()
                val isWebToken = re.pinok.api.VkSigner.isWebToken(token)
                if (isWebToken && !hasExchange) {
                    AppLog.w(TAG, "signIn: web-token without exchange_token detected — " +
                        "validating via getExchangeTokenDetailed (Fix #331)")
                    val verify = try {
                        api.getExchangeTokenDetailed(token)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "signIn: validation network error: ${e.message}")
                        ExchangeTokenResult.Unavailable
                    }
                    if (verify is ExchangeTokenResult.TokenInvalid) {
                        AppLog.e(TAG, "signIn: Direct Auth token rejected by VK (err=5) — " +
                            "VK disabled password grant for third-party clients. " +
                            "NOT saving. User must use WebView/external browser.")
                        // НЕ сохраняем мёртвый токен.
                        storage.clearAccessToken()
                        return AuthState.Error(
                            AuthErrorKind.EXPIRED,
                            "VK отключил парольный вход для сторонних приложений. " +
                                "Войдите через кнопку «Войти через Яндекс / Chrome» выше — " +
                                "это официальный и поддерживаемый способ.",
                        )
                    }
                    // Если Unavailable (err=3/network) — возможно токен
                    // всё-таки рабочий. Сохраняем как есть, юзер увидит
                    // результат на первом API-запросе. Это безопаснее чем
                    // блокировать вход в случае кратковременной ошибки сети.
                    AppLog.i(TAG, "signIn: validation inconclusive ($verify) — " +
                        "saving token as-is, will be verified on first API call")
                }
            }
            parsed
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "signIn auth error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "signIn network error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    /** Step 2a — submit the 2FA code. Mirrors VkAuthState.b(sid!=null). */
    suspend fun submit2FaCode(phone: String, sid: String, code: String): AuthState {
        val deviceId = storage.deviceId()
        return try {
            // #NETWORK-RESILIENCE: backoff на 2FA code submit.
            // SMS код действует ~60 сек — backoff (3 попытки за 7 сек) не превысит lifetime.
            val json = ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.AUTH_DEFAULT,
                tag = "authBy2FaCode",
            ) {
                api.authBy2FaCode(phone, sid, code, deviceId)
            } ?: return AuthState.Error(
                AuthErrorKind.NETWORK,
                "Нет связи с VK. Код действителен — попробуйте ещё раз.",
            )
            parseAuthResponse(json)
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "submit2FaCode auth error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "submit2FaCode network error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    /**
     * Step 2b — push-approved login (no password).
     * Mirrors VkAuthState.d(z=true).
     * VK sends this when the user already approved via push notification
     * and the password isn't needed.
     */
    suspend fun authWithoutPassword(phone: String, sid: String): AuthState {
        val deviceId = storage.deviceId()
        return try {
            // #NETWORK-RESILIENCE: backoff на push-approved login.
            val json = ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.AUTH_DEFAULT,
                tag = "authWithoutPassword",
            ) {
                api.authWithoutPassword(phone, sid, deviceId)
            } ?: return AuthState.Error(
                AuthErrorKind.NETWORK,
                "Нет связи с VK. Push-одобрение действительно — попробуйте ещё раз.",
            )
            parseAuthResponse(json)
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "authWithoutPassword error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "authWithoutPassword error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    /**
     * Step 3 — re-login via trusted hash (no password needed).
     * Mirrors VkAuthState.e(sid=trustedHash, username=phone).
     *
     * After the first successful login, VK returns a trusted_hash.
     * On subsequent app launches, the app can use this hash to authenticate
     * without prompting for the password again.
     */
    // #SESSION-WEB-MECHANISM (2026-09-24): signInByTrustedHash удалён (Path 2.5).
    // Silent re-login теперь ТОЛЬКО через скрытый WebView («перепосещение») —
    // как в веб-версии. Пароль/trusted_hash на устройстве больше не хранятся.

    /**
     * External service auth (VKID, Google, Mail.ru, etc.).
     * Mirrors VkAuthState.a(...).
     */
    suspend fun signInByExternalService(
        vkService: String,
        externalCode: String,
        externalClientId: String,
        externalRedirectUri: String,
        codeVerifier: String? = null,
        nonce: String? = null,
    ): AuthState {
        val deviceId = storage.deviceId()
        return try {
            // #NETWORK-RESILIENCE: backoff на external service auth (VKID/Google/Mail.ru).
            val json = ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.AUTH_DEFAULT,
                tag = "authByExternalService",
            ) {
                api.authByExternalService(
                    vkService = vkService,
                    externalCode = externalCode,
                    externalClientId = externalClientId,
                    externalRedirectUri = externalRedirectUri,
                    deviceId = deviceId,
                    codeVerifier = codeVerifier,
                    nonce = nonce,
                )
            } ?: return AuthState.Error(
                AuthErrorKind.NETWORK,
                "Нет связи с VK. Внешняя авторизация не завершена — попробуйте снова.",
            )
            parseAuthResponse(json)
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "signInByExternalService error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "signInByExternalService error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    // =====================================================================
    // OAuth WebView token (from oauth.vk.com/authorize, like VKoffline)
    // =====================================================================

    /**
     * Save an OAuth token obtained via WebView (Implicit Grant flow).
     * The user logged in on VK's own page, so there's no flood control risk.
     * We try to get exchange_token via auth.getExchangeToken for refresh support.
     *
     * Fix #215 (P0.4): backfill remixsid через CookieManager.
     * External browser flow (Chrome/Яндекс) и OAuth WebView не сохраняют
     * remixsid в storage — только access_token. Это ломает Path 1.5 в
     * ensureFreshToken (silent refresh через remixsid HTTP). Решение: после
     * успешного логина проверяем CookieManager на remixsid (внешний браузер
     * мог оставить cookie если CookieManager shared с Chrome на некоторых
     * устройствах Samsung/Xiaomi). Если найден — сохраняем в storage.
     * Это бесплатно, без UI, без дополнительного сетевого запроса.
     */
    suspend fun saveOAuthToken(
        accessToken: String,
        userId: Long,
        remixsid: String? = null,
        pCookie: String? = null,
        remixnsid: String? = null,
        // §55 #SSO-FULL-COOKIE-SET / §57 #COOKIE-CAPTURE-UNIFY: 6 доп. кук.
        httoken: String? = null,
        remixnttpid: String? = null,
        remixuacck: String? = null,
        remixuas: String? = null,
        remixdmgr: String? = null,
        remixmvkFp: String? = null,
    ): AuthState {
        return try {
            // Fix #230: getExchangeTokenDetailed различает «токен мёртв» (err=5,
            // включая subcode 1130 IP mismatch) от «метод недоступен» (err=3/network).
            // Раньше saveOAuthToken вызывал getExchangeToken который возвращал null
            // на ЛЮБУЮ ошибку → токен сохранялся ВСЕГДА, даже когда VK его уже
            // отклонил → вечный цикл err=5 → AuthActivity → clipboard auto-save
            // → сохранение того же мёртвого токена → повтор.
            val exchangeResult = try {
                api.getExchangeTokenDetailed(accessToken)
            } catch (e: Exception) {
                AppLog.w(TAG, "getExchangeTokenDetailed from OAuth token failed: ${e.message}")
                ExchangeTokenResult.Unavailable
            }

            if (exchangeResult is ExchangeTokenResult.TokenInvalid) {
                // VK уже отклонил access_token (err=5). Сохранять его бессмысленно —
                // каждый последующий API-вызов вернёт err=5 → notifyTokenInvalidated
                // → AuthActivity → clipboard loop. Возвращаем Error, НЕ сохраняем.
                AppLog.e(TAG, "saveOAuthToken: access_token rejected by VK (err=5) — NOT saving. User must re-login from scratch.")
                return AuthState.Error(
                    AuthErrorKind.EXPIRED,
                    "Токен отклонён ВКонтакте (смена IP). Войдите заново.",
                )
            }

            val exchangeToken = (exchangeResult as? ExchangeTokenResult.Success)?.exchangeToken

            val result = AuthResult(
                accessToken = accessToken,
                exchangeToken = exchangeToken,
                userId = userId,
                expiresIn = 0L,
                scope = "friends,messages,offline,photos,audio,video,docs,wall,groups",
            )
            storage.saveAuthResult(result, storage.deviceId())

            // #SESSION-WEB-MECHANISM (2026-09-24): session cookies живут в
            // CookieManager (единственный источник сессии) — storage-копии больше
            // не храним, backfillRemixsidFromCookieManager удалён.

            // Fix #211: сбрасываем auto-offline флаг при успешной авторизации.
            // privacyOfflineMode мог быть включён авто-офлайном (#38) в прошлой
            // сессии после сетевых ошибок и сохраниться в DataStore. Без сброса
            // все API-вызовы шорт-сиркитятся → «приложение не грузится» после
            // re-login. Успешный логин = сеть работает → offline не нужен.
            runCatching {
                prefs?.setPrivacyOfflineMode(false)
                AppLog.i(TAG, "OAuth auth success — privacyOfflineMode reset to false (was auto-enabled by #38)")
            }
            AppLog.i(TAG, "OAuth WebView auth success — user_id=$userId, exchange_token=${if (exchangeToken != null) "yes" else "no"}")
            AuthState.Success(result)
        } catch (e: Exception) {
            AppLog.e(TAG, "saveOAuthToken error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    // #SESSION-WEB-MECHANISM: hasRemixsid() удалён — storage-копий remixsid больше нет.
    // Silent-средства проверяются по живому CookieManager: [hasSilentReloginMeans].

    /**
     * §51 #WEB-TOKEN-DEAD-SESSION-CLEAR (Fix auth-loop 2026-08-05):
     * Сценарий: WebTokenAuth.fullAuthFlow упал с НЕ-EXPIRED ошибкой
     * (токен не появился в localStorage за 25 сек). Это значит remixsid
     * cookie есть, но VK не обменивает его на access_token (сессия мертва
     * по IP, или login.vk.com отвергает). Без этой очистки MainActivity
     * перезапустит AuthActivity → cookie polling снова найдёт мёртвый
     * remixsid → цикл бесконечный (пользователь никогда не увидит форму
     * логина с 2FA).
     *
     * Чистим:
     *   1. CookieManager — все VK cookies (remixsid, p, remixnsid, …)
     *      через ExternalBrowserAuth.clearAllVkCookies() (suspend, sync + flush).
     *   2. Storage — только remixsid/p/remixnsid (storage.clearRemixsid()),
     *      НЕ трогая access_token/exchange_token/trusted_hash — они могут
     *      ещё дать Path 2.5/3 silent re-login без полного ручного ввода.
     *
     * Безопасно для всех auth flows:
     *   - WebView m.vk.ru: чистый старт → VK покажет форму логина → 2FA.
     *   - External Browser Auth: тоже начнёт с нуля (cookies невалидны).
     *   - OAuth WebView: чистый старт, юзер вводит phone+pass → 2FA.
     *   - Direct Auth (password + 2FA через OkHttp): НЕ зависит от cookies,
     *     не пострадает.
     *   - VK App SSO (intent): НЕ зависит от cookies, не пострадает.
     *   - silentRefreshViaRemixsid: НЕ зависит от CookieManager, но ЧИТАЕТ
     *     storage.remixsid — поэтому чистим и storage (иначе silent refresh
     *     возьмёт мёртвый remixsid и снова упадёт с 5/1130).
     *   - Path 2.5 (trusted_hash): не зависит от remixsid, не пострадает.
     */
    suspend fun clearDeadSessionForRetry() {
        AppLog.i(TAG, "clearDeadSessionForRetry: WebTokenAuth failed — clearing dead remixsid " +
            "(CookieManager + storage) to break auto-relogin loop and force fresh login with 2FA")
        try {
            ExternalBrowserAuth.clearAllVkCookies()
        } catch (e: Exception) {
            AppLog.w(TAG, "clearDeadSessionForRetry: clearAllVkCookies failed: ${e.message}")
        }
        try {
            storage.clearRemixsid()
        } catch (e: Exception) {
            AppLog.w(TAG, "clearDeadSessionForRetry: storage.clearRemixsid failed: ${e.message}")
        }
    }

    /**
     * #SESSION-COOKIES-BG-REFRESH: делегат к [ExchangeTokenStorage.hasValidAccessToken].
     * Используется CookieRefreshWorker для проверки «пользователь залогинен?» —
     * нет смысла sync'ить cookies если access_token отсутствует/истёк.
     */
    fun hasValidAccessToken(): Boolean = storage.hasValidAccessToken()

    /**
     * #CALLS: SAT-токен для queuev4 (queue.subscribe / long-poll).
     *
     * VK выдаёт sat_token вместе с web_token (см. WebTokenAuth.kt). Он нужен
     * для подписки на очереди queuev4.vk.ru (queue.subscribe) и для других
     * LongPoll-подписок. Хранится в ExchangeTokenStorage.
     *
     * @return sat_token или null если не сохранён.
     */
    fun satToken(): String? = storage.satToken()

    /**
     * #CALLS: стабильный per-install device_id (UUID).
     * Используется для oauth.vk.ru/get_anonym_token (звонки vchat API).
     */
    fun deviceId(): String = storage.deviceId()

    // #SESSION-WEB-MECHANISM (2026-09-24): CookieRefreshResult,
    // refreshSessionCookiesFromCookieManager, saveRemixsid(...) и
    // backfillRemixsidFromCookieManager(...) удалены — cookies живут в
    // CookieManager, копии в storage не ведутся, синхронизировать нечего.

    /**
     * #41: Сохраняет результат WebTokenAuth.fullAuthFlow() в storage.
     *
     * Вызывается из AuthViewModel.submitWebToken после успешного обмена
     * через login.vk.com. Не делает HTTP-запросов — только persist.
     *
     * Возвращает [AuthState.Success] с минимальным AuthResult для UI.
     */
    suspend fun saveWebTokenResult(
        accessToken: String,
        userId: Long,
        expiresAt: Long,
        satToken: String?,
        logoutHash: String?,
        remixsid: String?,
    ): AuthState {
        // Fix #105: НЕ сохраняем истёкший web_token. WebTokenAuth.fullAuthFlow
        // (Fix #103) уже отбраковывает истёкшие, но это последняя линия обороны —
        // если где-то в цепочке вызовов (AuthViewModel, deep-link) токен протёк
        // без проверки, здесь мы его остановим.
        //
        // expiresAt — абсолютный unix-timestamp в ms (0 = offline scope, без истечения).
        // Раньше сохранялся любой токен, даже мёртвый → hasValidToken()=false →
        // белый экран / зависание на splash (лог 2026-07-18 21:32:54).
        if (expiresAt != 0L && expiresAt <= System.currentTimeMillis()) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val expiresHuman = sdf.format(java.util.Date(expiresAt))
            val nowHuman = sdf.format(java.util.Date(System.currentTimeMillis()))
            AppLog.w(
                TAG,
                "saveWebTokenResult: REJECTED expired web_token " +
                    "(expires=$expiresHuman UTC, now=$nowHuman UTC, user_id=$userId) — " +
                    "токен НЕ сохранён, требуем re-login"
            )
            return AuthState.Error(
                AuthErrorKind.EXPIRED,
                "web_token истёк (expires=$expiresHuman UTC). Требуется повторный вход."
            )
        }
        return try {
            storage.saveWebTokenResult(
                accessToken = accessToken,
                userId = userId,
                expiresAt = expiresAt,
                satToken = satToken,
                logoutHash = logoutHash,
                remixsid = remixsid,
            )

            // Fix #117 + Fix #233 (P1): Получаем exchange_token сразу после web-логина.
            // Fix #230 sealed result — различаем «токен мёртв» (err=5) от «метод
            // недоступен» (err=3/network). Раньше getExchangeToken возвращал null
            // на любую ошибку → токен сохранялся ВСЕГДА → auth loop того же класса
            // что Fix #230 фиксил для saveOAuthToken.
            val exchangeResult = try {
                api.getExchangeTokenDetailed(accessToken)
            } catch (e: Exception) {
                AppLog.w(TAG, "getExchangeTokenDetailed from web_token failed: ${e.message}")
                ExchangeTokenResult.Unavailable
            }
            if (exchangeResult is ExchangeTokenResult.TokenInvalid) {
                // VK уже отклонил access_token (err=5). Web_token реально мёртв,
                // хотя expiresAt ещё в будущем (VK мог отозвать сессию на стороне
                // сервера). storage.saveWebTokenResult уже отработал выше — откатываем.
                AppLog.e(TAG, "saveWebTokenResult: access_token rejected by VK (err=5) — rolling back save. User must re-login.")
                runCatching { storage.clearAccessToken() }
                return AuthState.Error(
                    AuthErrorKind.EXPIRED,
                    "Сессия отозвана ВКонтакте. Войдите заново.",
                )
            }
            val exchangeToken = (exchangeResult as? ExchangeTokenResult.Success)?.exchangeToken
            if (exchangeToken != null) {
                val expiresIn = if (expiresAt == 0L) 0L
                                else (expiresAt - System.currentTimeMillis()) / 1000
                storage.updateAccessToken(
                    accessToken = accessToken,
                    expiresIn = expiresIn,
                    scope = "all",
                    exchangeToken = exchangeToken,
                )
                AppLog.i(TAG, "WebToken: obtained exchange_token for silent refresh")
            }

            // Fix #211: сбрасываем auto-offline флаг при успешной авторизации.
            // privacyOfflineMode мог быть включён авто-офлайном (#38) в прошлой
            // сессии после сетевых ошибок и сохраниться в DataStore. Без сброса
            // все API-вызовы шорт-сиркитятся → «приложение не грузится» после
            // re-login (лог 2026-07-24: весь лог забит «Offline mode forced»
            // даже после Auth success). Успешный логин = сеть работает → offline не нужен.
            runCatching {
                prefs?.setPrivacyOfflineMode(false)
                AppLog.i(TAG, "WebToken saved — privacyOfflineMode reset to false (was auto-enabled by #38)")
            }

            // #SESSION-WEB-MECHANISM: cookies уже в CookieManager (их поставила
            // страница, выдавшая web_token) — storage-копии не ведём.
            AppLog.i(
                TAG,
                "WebToken saved — user_id=$userId, sat=${if (satToken != null) "yes" else "no"}, " +
                    "exchange=${if (exchangeToken != null) "yes" else "no"}"
            )
            AuthState.Success(
                AuthResult(
                    accessToken = accessToken,
                    exchangeToken = exchangeToken,
                    userId = userId,
                    expiresIn = 0L,
                    scope = "all",
                )
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "saveWebTokenResult error", e)
            AuthState.Error(AuthErrorKind.UNKNOWN, e.message ?: "save error")
        }
    }

    /**
     * Sign in using a pre-existing web_token (vk1.a.*) from VK web.
     *
     * The web_token is a full access_token obtained via VK web auth.
     * It bypasses oauth.vk.com/password flow entirely, so no flood control.
     *
     * Flow:
     *   1. User pastes a web_token from m.vk.com / vk.com browser.
     *   2. We validate it via account.getProfileInfo.
     *   3. On success, store as access_token and try to get exchange_token
     *      via auth.getExchangeToken so we can refresh later.
     *
     * @param webToken The vk1.a.* token string from VK web.
     * @return AuthState.Success if the token is valid, Error otherwise.
     */
    suspend fun signInByWebToken(webToken: String): AuthState {
        return try {
            // Step 1: Validate the token and get user info.
            val profileJson = api.validateWebToken(webToken)
            val err = profileJson.getAsJsonObject("error")
            if (err != null) {
                val errCode = err.get("error_code")?.asInt ?: -1
                val errMsg = err.get("error_msg")?.asString ?: "Unknown error"
                AppLog.e(TAG, "Web token validation failed: error_code=$errCode msg=$errMsg")
                return when (errCode) {
                    5 -> AuthState.Error(AuthErrorKind.INVALID_CREDENTIALS, "Токен недействителен или истёк")
                    else -> AuthState.Error(AuthErrorKind.UNKNOWN, errMsg)
                }
            }

            val resp = profileJson.getAsJsonObject("response") ?: return AuthState.Error(
                AuthErrorKind.PARSE, "No response in web token validation"
            )
            val userId = resp.get("id")?.asLong ?: return AuthState.Error(
                AuthErrorKind.PARSE, "No user id in profile response"
            )

            AppLog.i(TAG, "Web token valid for user_id=$userId")

            // Step 2: Try to get exchange_token via execute.
            // Fix #233 (P1): sealed result — если VK отклонил токен (err=5),
            // НЕ сохраняем его. Раньше null на любую ошибку → мёртвый токен
            // сохранялся → auth loop.
            val exchangeResult = try {
                api.getExchangeTokenDetailed(webToken)
            } catch (e: Exception) {
                AppLog.w(TAG, "getExchangeTokenDetailed from web_token failed: ${e.message}")
                ExchangeTokenResult.Unavailable
            }
            if (exchangeResult is ExchangeTokenResult.TokenInvalid) {
                // VK уже отклонил web_token (err=5). Хотя validateWebToken выше
                // прошёл (profile info вернулась), getExchangeToken строже —
                // возможно IP-mismatch (subcode 1130). Не сохраняем токен.
                AppLog.e(TAG, "signInByWebToken: web_token rejected by VK (err=5) — NOT saving. User must re-login.")
                return AuthState.Error(
                    AuthErrorKind.EXPIRED,
                    "Токен отклонён ВКонтакте (смена IP). Войдите заново.",
                )
            }
            val exchangeToken = (exchangeResult as? ExchangeTokenResult.Success)?.exchangeToken

            // Step 3: Save everything.
            val result = AuthResult(
                accessToken = webToken,
                exchangeToken = exchangeToken,
                userId = userId,
                expiresIn = 0L,  // web_token uses absolute "expires", not relative
                scope = "all",
            )
            storage.saveAuthResult(result, storage.deviceId())
            AppLog.i(TAG, "Web token auth success — user_id=$userId, exchange_token=${if (exchangeToken != null) "yes" else "no"}")
            AuthState.Success(result)
        } catch (e: Exception) {
            AppLog.e(TAG, "signInByWebToken error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    // NOTE (audit Medium #2): signInByAnonymFlow() удалён как мёртвый код.
    // Рабочий web-token flow реализован в WebTokenAuth.kt (login.vk.com с
    // remixsid cookie). Этот метод (api.vk.com/method/auth.* без cookie)
    // никогда не вызывался — getAnonymToken/getWebToken в ExchangeAuthApi
    // тоже удалены.

    /**
     * Resend 2FA code via a different method.
     *
     * VK API contract (per decompiled VkAuthState.b):
     *   - grant_type=password (NOT phone_confirmation_sid — that's for code SUBMISSION)
     *   - username + password required
     *   - sid included to re-use the active validation session
     *   - supported_ways tells VK which channels the client can receive
     *   - force_sms=true forces SMS specifically
     *
     * Before this fix, we sent grant_type=phone_confirmation_sid without password,
     * which VK silently ignored and defaulted to SMS (audit #18).
     */
    suspend fun resendValidationCode(
        phone: String,
        password: String,
        sid: String,
        validationType: ValidationType,
    ): AuthState {
        val deviceId = storage.deviceId()
        return try {
            val json = api.resendValidationCode(phone, password, sid, validationType, deviceId)
            parseAuthResponse(json)
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "resendValidationCode error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "resendValidationCode error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    // =====================================================================
    // Token refresh (used by VKApiClient on error 5)
    // =====================================================================

    /**
     * Ensures a non-expired access_token is in storage.
     *
     * Strategy (6 paths, in order — все silent, без UI):
     *   0. **File backup recovery (VTosters pattern #3)**: если access_token
     *      потерян из prefs, но `<filesDir>/account.json` существует —
     *      восстанавливаем из файла без сети. Защита от Keystore corruption.
     *   1. If current access_token is still valid → return immediately.
     *   1.5. **Fix #212 (P0.1): Silent refresh via remixsid** (HTTP, no WebView):
     *        GET login.vk.ru/?act=web_token&app_id=7879029 with Cookie remixsid.
     *        Это тот же endpoint, что использует m.vk.ru JS. Работает за 200мс
     *        без UI. Решает ~90% случаев "выбивает из диалога при отправке фото".
     *   2. **Web token refresh**: requires WebView (reads from m.vk.ru localStorage).
     *      Not available in background refresh — user must re-login via WebView.
     *   2.5. **Fix #214 (P0.3): trusted_hash re-login** (HTTP, no password):
     *        POST oauth.vk.com/access_token grant_type=trusted_hash.
     *        Требует trusted_hash + last_phone. VK выдаёт на ~1 год.
     *   3. Exchange token refresh (fallback for Direct Auth / OAuth):
     *      Try auth.getExchangeToken → POST id.vk.com/auth_by_exchange_token.
     *
     * ## §51 #AUTH-PATH-REORDER (2026-08-05): динамический порядок Path 1.5 / Path 5
     *
     * После §50 #TOKEN-LIFECYCLE-FIX (clearAccessToken НЕ удаляет токен, а ставит
     * invalidated flag) порядок Path 1.5 и Path 5 зависит от состояния токена:
     *
     *  - **invalidated=true** (VK отверг токен err 5/1117 → clearAccessToken):
     *    Path 5 (connect_exchange_token) выполняется ПЕРВЫМ — logout_hash переживает
     *    token invalidation (VK проверяет сессию, не сам токен), один HTTP-вызов
     *    ~200мс. Path 1.5 (7 remixsid стратегий) — fallback если Path 5 вернул
     *    empty array. Сокращает recovery «токен умирает» с 3-7с до ~200мс.
     *
     *  - **invalidated=false** (просто timestamp-expired, normal refresh):
     *    Path 1.5 первым (remixsid скорее жив, классический silent refresh).
     *    Path 5 — fallback если Path 1.5 упал по contract failure.
     *
     * См. [ExchangeTokenStorage.isAccessTokenInvalidated].
     *
     * ## #FORCE-REFRESH (2026-08-02): параметр `force`
     *
     * **Проблема:** при смене сети (Wi-Fi↔Mobile) VK инвалидирует токен по IP
     * (err=5/1130 «access_token was given to another ip address»). При этом
     * `hasValidAccessToken()` возвращает `true` — токен ещё валиден по timestamp
     * (истекает через часы/дни). `ensureFreshToken()` short-circuits на этой
     * проверке и возвращает СТАРЫЙ IP-bound токен БЕЗ реального refresh.
     *
     * **Симптом (из logcat 2026-08-02 19:02-19:04):**
     * ```
     * 19:02:39.481  Default network SWITCHED (WiFi↔Mobile)
     * 19:02:41.002  getLongPollServer err=5/1130 (IP mismatch)
     * 19:02:41.003  grace period — delay 5с + ensureFreshToken()
     * 19:02:46.008  "silent refresh during grace period OK" ← ЛОЖЬ! ensureFreshToken
     *               вернул СТАРЫЙ токен (hasValidAccessToken=true → short-circuit).
     *               Path 1.5 (silentRefreshViaRemixsid) НЕ вызван!
     * 19:02:46.412  retry → err=5/1130 again (старый токен IP-bound)
     * ...loop continues for 5+ minutes, app stuck in "no data"...
     * ```
     *
     * **Фикс:** параметр `force=true` bypasses `hasValidAccessToken()` short-circuit
     * и переходит сразу к Path 0/1.5/2.5/3. Это позволяет `silentRefreshViaRemixsid`
     * получить НОВЫЙ токен для НОВОГО IP, даже если старый ещё «валиден» по timestamp.
     *
     * **Кто вызывает с `force=true`:**
     * - `VKApiClient.callInternal` на err=5/1130 (IP mismatch / token invalid) —
     *   VK отверг токен, значит он НЕ валиден независимо от timestamp.
     * - `VKApiClient.call` когда `token()` вернул null (force безвреден —
     *   `hasValidAccessToken()` и так false).
     *
     * **Кто вызывает с `force=false` (default):**
     * - `keepAlive()` — proactive check каждые 60с. НЕ должен force (иначе
     *   каждый раз будет network request на login.vk.ru).
     * - `silentAuth()` — proactive check для LongPoll.
     * - `LongPollKeepAliveService` — headless refresh observer.
     *
     * @param force если `true` — пропускает `hasValidAccessToken()` short-circuit
     *              и сразу пытается refresh через Path 0/1.5/2.5/3. Использовать
     *              когда VK отверг токен (err=5/1130) или после смены сети.
     */
    /**
     * #SESSION-WEB-MECHANISM (2026-09-24, СЕССИЯ-ВЕБ-ПОРТ.md): refresh сессии —
     * веб-механизм, ЕДИНСТВЕННЫЙ путь (порт веб-версии VK 1:1).
     *
     * ## Порядок
     *   1. Reentrancy-guard: если скрытый refresh УЖЕ идёт — возвращаем null
     *      ДО refreshMutex (иначе non-reentrant Mutex повиснет навсегда:
     *      WebTokenAuth.trySsoHttpRefreshViaRemixsid вызывает ensureFreshToken
     *      внутри fullAuthFlow, который запущен из HiddenSessionRefresher).
     *   2. Short-circuit: !force && hasValidAccessToken → текущий токен.
     *   3. Offline-guard (#NETWORK-RESILIENCE, без изменений).
     *   4. WEB-MECHANISM: скрытый WebView «перепосещение» m.vk.ru → живые
     *      cookies (CookieManager) уезжают сами → VK молча выдаёт web_token
     *      ([HiddenSessionRefresher.refresh]) → persist через saveWebTokenResult.
     *
     * ## Что удалено
     *   Path 1.5 (silentRefreshViaRemixsid, HTTP-стратегии по стейл-копии
     *   cookies), Path 2.5 (trusted_hash), Path 5 (connect_exchange_token).
     *   Причина: копии cookies в storage протухают (VK ротейтит remixsid) —
     *   все баги класса «смена сети → просит логин». Живой CookieManager
     *   работает с любого IP, ровно как браузерная сессия.
     *
     * ## #FORCE-REFRESH (смысл сохранён)
     *   force=true (err=5/1130, смена сети) пропускает short-circuit и делает
     *   реальный refresh. Раньше — Path 1.5; теперь — скрытый WebView:
     *   cookies уезжают на новый IP сами, VK молча выдаёт свежий web_token.
     */
    suspend fun ensureFreshToken(force: Boolean = false): String? {
        if (HiddenSessionRefresher.inProgress) {
            AppLog.d(TAG, "ensureFreshToken: reentrant call during hidden web refresh — " +
                "returning null (web-mechanism доведёт сам, #SESSION-WEB-MECHANISM)")
            return null
        }
        return refreshMutex.withLock {
            // #FORCE-REFRESH: force=true (err=5/1130) — токен отвергнут VK,
            // hasValidAccessToken() лжёт (проверяет только timestamp, не IP-binding).
            if (!force && storage.hasValidAccessToken()) {
                return@withLock storage.accessToken()
            }
            if (force) {
                AppLog.i(TAG, "ensureFreshToken: FORCE refresh — bypassing hasValidAccessToken " +
                    "(err=5/1130 or network switch — token rejected by VK, WEB-MECHANISM refresh)")
            }

            // #NETWORK-RESILIENCE: offline-guard (без force — экономим батарею
            // в метро/лифте; с force — даём шанс: NetworkObserver мог не поймать switch).
            if (!force && isOffline()) {
                AppLog.i(TAG, "ensureFreshToken: OFFLINE — skipping refresh " +
                    "(caller should show OfflineWithCache)")
                return@withLock null
            }

            // ── WEB-MECHANISM: скрытый WebView «перепосещение» (единственный refresh-путь) ──
            val ctx = appContext
                ?: runCatching { re.pinok.SovaApp.get() as android.content.Context }.getOrNull()
            if (ctx == null) {
                AppLog.w(TAG, "ensureFreshToken: no appContext — WEB-MECHANISM refresh impossible")
                return@withLock null
            }
            val web = HiddenSessionRefresher.refresh(ctx)
            if (web == null) {
                AppLog.w(TAG, "ensureFreshToken: WEB-MECHANISM refresh failed " +
                    "(definitivelyDead=${HiddenSessionRefresher.lastAttemptDefinitivelyDead}) — " +
                    "re-login required")
                return@withLock null
            }

            // Persist через battle-tested saveWebTokenResult (expired-отбраковка,
            // exchange_token best-effort, privacyOfflineMode reset).
            // remixsid = null: cookies живут в CookieManager — storage-копии не ведём.
            val state = saveWebTokenResult(
                accessToken = web.accessToken,
                userId = web.userId,
                expiresAt = web.expiresAt,
                satToken = web.satToken,
                logoutHash = web.logoutHash,
                remixsid = null,
            )
            if (state is AuthState.Success) {
                state.result.accessToken
            } else {
                AppLog.w(TAG, "ensureFreshToken: WEB-MECHANISM token rejected on save — $state")
                null
            }
        }
    }

    // =====================================================================
    // S7-4: Session keep-alive    // =====================================================================
    // S7-4: Session keep-alive
    // =====================================================================

    /**
     * S7-4: Proactive keep-alive — refreshes the access token before it expires.
     *
     * Unlike [ensureFreshToken] (which is reactive — only refreshes when
     * [ExchangeTokenStorage.hasValidAccessToken] returns false), this method
     * proactively checks token expiry and refreshes early.
     *
     * Uses [silentAuth] internally because it bypasses the validity check
     * and goes straight to exchange-token refresh with SILENT_AUTHORIZATION
     * initiator — exactly what VK uses for background token renewal.
     *
     * Should be called periodically (e.g., every 60 seconds) while the app
     * is in the foreground to prevent session drops during idle.
     *
     * Fix #216 (P1.1): окно preemptive refresh увеличено с 60с до 300с.
     * 60с — 0.07% от 24-часового TTL web_token: если в эту минуту нет
     * сети (например, метро), токен умрёт. 300с даёт 5 минут на
     * восстановление сети. Это особенно важно для Path 1.5 (silent
     * refresh через remixsid) — он работает только пока remixsid жив,
     * а remixsid живёт столько же сколько web_token (~24ч).
     *
     * Также [keepAlive] можно вызывать вручную при открытии чата
     * (см. ChatDetailScreen LaunchedEffect) — чтобы обновить токен
     * ДО того как пользователь начнёт отправлять сообщения.
     *
     * @return true if token was refreshed, false if not needed or failed
     */
    suspend fun keepAlive(): KeepAliveResult {
        val exp = storage.expiresAt()
        // 0 = no expiry (offline scope) — nothing to do
        if (exp <= 0L) return KeepAliveResult.NOT_NEEDED

        val now = System.currentTimeMillis()
        val timeUntilExpiryMs = exp - now

        // Fix #216 (P1.1): preemptive refresh window = 300с (5 минут).
        // Раньше было 60с — слишком узкое окно, токен умирал при кратковременной
        // потере сети. 300с даёт время на восстановление соединения.
        val refreshWindowMs = 300_000L
        if (timeUntilExpiryMs > refreshWindowMs) {
            AppLog.d(TAG, "keepAlive: token valid for ${timeUntilExpiryMs / 1000}s, no refresh needed")
            return KeepAliveResult.NOT_NEEDED
        }

        AppLog.i(TAG, "keepAlive: token expires in ${timeUntilExpiryMs / 1000}s " +
            "(within ${refreshWindowMs / 1000}s window), refreshing proactively")
        // #KEEPALIVE-ENSURE-FRESH (2026-08-01): вызываем ensureFreshToken()
        // вместо silentAuth(). silentAuth() знает ТОЛЬКО про exchange_token
        // (Path 3) — если его нет, возвращает null, и токен умирает. Это
        // приводило к лишнему открытию AuthActivity SILENT mode при каждом
        // истечении web_token без exchange_token (web-сессии не имеют
        // exchange_token — getExchangeToken работает только для official app_id).
        //
        // ensureFreshToken() содержит ВСЕ пути восстановления:
        //   Path 0:   file backup recovery
        //   Path 1.5: silentRefreshViaRemixsid (HTTP, ~200мс) ← фикс
        //              #SILENT-REFRESH-ORIGIN (Origin header) ЖИЛ здесь, но
        //              был мёртвым кодом — keepAlive его не вызывал.
        //   Path 2:   web token refresh (WebView)
        //   Path 2.5: trusted_hash re-login
        //   Path 3:   exchange_token refresh
        //
        // Лог до фикса (process killed after failed refresh):
        //   12:24:50.444  keepAlive: token expires in -36s
        //   12:24:50.452  silentAuth: no exchange_token  ← STOP, no Path 1.5!
        //   12:24:51.141  MainActivity onCreate (process recreated)
        //   12:24:51.556  No token — stopping LongPoll
        //   12:24:51.706  AuthActivity SILENT mode  ← лишний re-login через WebView
        //   12:24:51.998  remixsid найден! длина=88  ← Path 1.5 сработал бы!
        //
        // После фикса: ensureFreshToken → Path 1.5 (silentRefreshViaRemixsid)
        // обновит токен за ~200мс через HTTP, без process kill и без AuthActivity.
        //
        // §44 #KEEPALIVE-FORCE (2026-08-03): force=true обязателен! Лог показал
        //   keepAlive: token expires in 274s (within 300s window), refreshing proactively
        //   Keep-alive: token refreshed proactively
        // …но токен НЕ обновился. Причина: ensureFreshToken(force=false) short-circuits
        // на hasValidAccessToken() (line 699) — токен ещё валиден по timestamp (274с
        // до истечения), поэтому refresh НЕ запускается, возвращается СТАРЫЙ токен.
        // Caller (SovaApp.startKeepAlive) видит non-null и логирует "refreshed" —
        // вводит в заблуждение. Когда токен реально истекал (-25s), каскад
        // notifyTokenInvalidated запускался уже ПОСЛЕ факта.
        // force=true bypasses short-circuit → Path 1.5 (silentRefreshViaRemixsid)
        // запускается в pre-expiry окне, обновляя токен ДО истечения.
        // P0 #KEEPALIVE-BACKOFF: возвращаем три-состояние, чтобы SovaApp.startKeepAlive
        // отличал «обновили» от «нужно обновить, но не вышло» и ретраил с бэк-оффом.
        val refreshed = ensureFreshToken(force = true) != null
        return if (refreshed) KeepAliveResult.REFRESHED else KeepAliveResult.FAILED
    }

    // =====================================================================
    // LongPoll
    // =====================================================================

    suspend fun fetchLongPoll(): LongPollCredentials? {
        val token = ensureFreshToken() ?: run {
            AppLog.w(TAG, "fetchLongPoll: no valid access_token")
            return null
        }
        return try {
            val json = api.getLongPollServer(token)
            val err = json.getAsJsonObject("error")
            if (err != null) {
                AppLog.e(TAG, "fetchLongPoll API error: ${err.get("error_msg")}")
                return null
            }
            val resp = json.getAsJsonObject("response") ?: return null
            val creds = LongPollCredentials(
                key = resp.get("key")?.asString ?: return null,
                server = resp.get("server")?.asString ?: return null,
                ts = resp.get("ts")?.asLong ?: return null,
                pts = resp.get("pts")?.takeIf { !it.isJsonNull }?.asLong,
            )
            storage.saveLongPoll(creds)
            AppLog.i(TAG, "LongPoll saved (ts=${creds.ts})")
            creds
        } catch (e: Exception) {
            AppLog.e(TAG, "fetchLongPoll error", e)
            null
        }
    }

    // =====================================================================
    // Convenience accessors
    // =====================================================================

    fun longPoll(): LongPollCredentials? = storage.longPoll()
    fun accessToken(): String? = storage.accessToken()
    override fun userId(): Long = storage.userId()

    /**
     * #AUTH-LOOP-FIX (2026-08-07): сохраняет userId в storage.
     *
     * Используется из [re.pinok.auth.AuthViewModel.submitWebToken] ПЕРЕД вызовом
     * Path 1.5 fallback (ensureFreshToken force=true) если storage.userId()==0.
     *
     * Сценарий: после m.vk.ru login remixsid валиден, но WebTokenAuth.fullAuthFlow
     * таймаутит (m.vk.ru/feed редиректит, VK ID SDK не инициализируется → web_token
     * не появляется за 25 сек). Path 1.5 (silentRefreshViaRemixsid) требует userId
     * для `remixsid_user=<userId>` cookie header. Без userId Path 1.5 падает →
     * clearDeadSessionForRetry → loop.
     *
     * Фикс: AuthViewModel извлекает userId из cookie `remixsid_user` через
     * [re.pinok.auth.getUserIdFromCookieManager], вызывает setUserId, и THEN
     * Path 1.5 получает корректный userId → успешный silent refresh.
     */
    fun setUserId(userId: Long) {
        if (userId <= 0L) return
        val current = storage.userId()
        if (current != userId) {
            storage.setUserId(userId)
            AppLog.i(TAG, "setUserId: saved user_id=$userId (was $current) — Path 1.5 will use it for remixsid_user cookie")
        }
    }
    // §58 #2FA-SESSION-WIPE-FIX: accessors для создания AuthResult после Path 1.5.
    fun exchangeToken(): String? = storage.exchangeToken()
    fun expiresAt(): Long = storage.expiresAt()
    fun scope(): String? = storage.scope()
    fun isSignedIn(): Boolean = storage.hasValidAccessToken()

    /**
     * Fix #107 / #SESSION-WEB-MECHANISM (2026-09-24): remixsid accessor.
     * Источник истины — ЖИВОЙ CookieManager (как в веб-версии: cookies есть →
     * silent-вход возможен). storage-копия — legacy fallback (миграция стирает).
     */
    override fun remixsid(): String? {
        runCatching { RemixsidCapturer.snapshotCookies() }.getOrNull()
            ?.remixsid?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return storage.remixsid()
    }

    /**
     * Task 22 (2026-09-03): override CallsAuth (:feature:calls). Делегирует в
     * RemixsidCapturer (same-package, :app) — CallsWebViewScreen синхронизирует
     * куки через фасад deps.exchangeAuthRepository, рантайм не менялся.
     */
    override fun buildVkCookieHeader(): String = RemixsidCapturer.buildVkCookieHeader()

    /**
     * #SESSION-WEB-MECHANISM (2026-09-24): silent-средства = живые cookies
     * web-сессии (remixsid ИЛИ p) в CookieManager. Как в веб-версии: cookies
     * есть → VK молча узнает пользователя; cookies нет → страница логина.
     * storage-копий больше нет (#AUTH-SIMPLIFY-2).
     */
    fun hasSilentReloginMeans(): Boolean {
        val cookies = runCatching { RemixsidCapturer.snapshotCookies() }.getOrNull()
        return cookies != null &&
            (!cookies.remixsid.isNullOrBlank() || !cookies.pCookie.isNullOrBlank())
    }

    // #SESSION-WEB-MECHANISM (2026-09-24): canTrustedHashLogin / tryTrustedHashLogin*
    // удалены (Path 2.5). Авто-вход при старте = скрытый WebView refresh
    // (AuthViewModel.tryAutoLogin → ensureFreshToken(force=true)).

    /**
     * Fix #182: Полный logout — очищает ВСЕ хранилища авторизации.
     *
     * Раньше `signOut()` вызывал только `storage.clear()` (SharedPreferences:
     * access_token, remixsid, exchange_token, logout_hash). Но `CookieManager`
     * (Android WebView cookie store, singleton) НЕ очищался → `remixsid` cookie
     * оставался → AuthActivity при следующем запуске находил его через
     * `ExternalBrowserAuth.tryFindExistingAuth()` → silent auto-relogin.
     * Пользователь нажимал «Выйти» → через секунду снова залогинен.
     *
     * Теперь signOut делает 3 вещи:
     *   1. Fire-and-forget VK server logout через `logout_hash` (best-effort,
     *      в background-потоке — не блокирует UI). Endpoint:
     *      `https://login.vk.com/?act=logout&logout_hash=...`. Это инвалидидирует
     *      сессию на стороне VK — даже если локальные cookies сохранятся, VK
     *      потребует повторный логин.
     *   2. `ExternalBrowserAuth.clearAllVkCookies()` — suspend очистка CookieManager
     *      (setCookie Max-Age=0 + removeAllCookies СИНХРОННО + flush). Гарантирует что
     *      `tryFindExistingAuth()` вернёт found=false.
     *      #LOGOUT-WEBVIEW-HANG: removeAllCookies теперь ждёт callback (2 сек
     *      timeout) — раньше async fire-and-forget вызывал race с WebView load
     *      m.vk.ru → onPageFinished never fired → «не грузится с первого раза».
     *   3. `storage.clear()` — SharedPreferences (access_token, remixsid,
     *      exchange_token, logout_hash, sat_token, trusted_hash, device_id
     *      сохраняется).
     *
     * ВАЖНО: logout_hash читается ДО `storage.clear()` (после очистки его уже
     * не получить). Server logout запускается в отдельном потоке — даже если
     * он не успеет выполниться до `storage.clear()`, httpClient держит свою
     * копию запроса.
     *
     * @param onServerLogout callback (optional) — вызывается после попытки
     *        server logout (success/failure/timeout). Вызывается в background
     *        потоке. Полезно для логирования.
     */
    suspend fun signOut(onServerLogout: ((Boolean) -> Unit)? = null) {
        // 1. Читаем logout_hash ДО очистки storage.
        val logoutHash = storage.logoutHash()
        val accessToken = storage.accessToken()
        AppLog.i(TAG, "signOut: starting comprehensive logout (logout_hash=${if (logoutHash != null) "present" else "null"}, access_token=${if (accessToken != null) "present" else "null"})")

        // 2. Fire-and-forget VK server logout (background thread, best-effort).
        //    Даже если не сработает — локальная очистка cookies + storage
        //    достаточна для разрыва сессии с точки зрения пользователя.
        if (!logoutHash.isNullOrBlank()) {
            Thread({
                try {
                    val success = callVkServerLogout(logoutHash)
                    AppLog.i(TAG, "signOut: VK server logout ${if (success) "succeeded" else "failed"}")
                    onServerLogout?.invoke(success)
                } catch (e: Exception) {
                    AppLog.w(TAG, "signOut: VK server logout exception: ${e.message}")
                    onServerLogout?.invoke(false)
                }
            }, "vk-server-logout").apply { isDaemon = true }.start()
        } else {
            AppLog.d(TAG, "signOut: no logout_hash — skipping VK server logout (local cleanup only)")
            onServerLogout?.invoke(false)
        }

        // 3. Очищаем CookieManager (WebView cookies) — КРИТИЧНО для разрыва
        //    auto-relogin через ExternalBrowserAuth.tryFindExistingAuth().
        try {
            ExternalBrowserAuth.clearAllVkCookies()
        } catch (e: Exception) {
            AppLog.w(TAG, "signOut: clearAllVkCookies failed: ${e.message}")
        }

        // 4. Очищаем SharedPreferences (access_token, remixsid, exchange_token,
        //    logout_hash, sat_token, trusted_hash). device_id сохраняется.
        storage.clear()

        AppLog.i(TAG, "Signed out (storage cleared + cookies cleared + server logout dispatched)")
    }

    /**
     * Fix #182: Вызывает VK server logout endpoint.
     *
     * Endpoint: `https://login.vk.com/?act=logout&logout_hash=...`
     *
     * Это инвалидидирует сессию на стороне VK. После этого `remixsid` (даже если
     * он где-то остался) становится недействительным, и VK потребует повторный
     * логин при следующем обращении.
     *
     * Best-effort: если запрос не удался (нет сети, timeout, VK изменил endpoint),
     * не падаем — локальная очистка cookies + storage достаточна для logout
     * с точки зрения пользователя.
     *
     * @param logoutHash значение `logout_hash` из web_token response
     * @return true если HTTP-запрос завершился успешно (2xx), false иначе
     */
    private fun callVkServerLogout(logoutHash: String): Boolean {
        val client = httpClient ?: return false
        // VK logout endpoint. act=logout + logout_hash инвалидирует сессию.
        // Также передаём access_token для явного отзыва (VK auth.revoke).
        val logoutUrl = "https://login.vk.com/?act=logout&logout_hash=${java.net.URLEncoder.encode(logoutHash, "UTF-8")}"
        return try {
            val req = okhttp3.Request.Builder()
                .url(logoutUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://m.vk.com/")
                .build()
            AppLog.d(TAG, "callVkServerLogout: GET $logoutUrl")
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string().orEmpty().take(200)
                AppLog.d(TAG, "callVkServerLogout: HTTP $code body=$body")
                code in 200..299
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "callVkServerLogout failed: ${e.message}")
            false
        }
    }

    // #SESSION-WEB-MECHANISM (2026-09-24): tryConnectExchangeToken (Path 5)
    // удалён из репозитория. connect_exchange_token внутри web-SDK flow
    // остаётся в WebTokenAuth (§49 pre-poll) — это часть веб-механизма.

    // =====================================================================
    // Parsing — mirrors ev4.a() (success) / ev4.b() (error)
    // =====================================================================

    /**
     * Parse the auth response.
     *
     * Two response formats:
     *   1. oauth.vk.com/access_token (flat):
     *      {"access_token":"...","user_id":123,"expires_in":86400}   (success)
     *      {"error":"invalid_grant","error_description":"...","error_code":9}  (error)
     *   2. id.vk.com/auth_by_exchange_token (nested):
     *      {"response":{"access_token":"...",...}}  (success)
     *      {"error":{"error":"need_validation","sid":"...","validation_type":"sms",...}}  (error)
     *
     * For format 1, error_code and error_description are at ROOT level,
     * not inside the "error" string. We merge them into the err object.
     */
    private fun parseAuthResponse(json: JsonObject): AuthState {
        val result = AuthResponseParser.parseAuthResultFromJson(json)
        if (result != null) {
            storage.saveAuthResult(result, storage.deviceId())
            // Fix #250: детальное логирование ответа VK — без этого невозможно
            // диагностировать случаи «токен получен, но сразу 1117 expired».
            // Логируем: тип токена (web vk1.a.* / kate-style), наличие secret,
            // scope, exchange_token, trusted_hash, utility_tokens count.
            val tokenType = if (re.pinok.api.VkSigner.isWebToken(result.accessToken)) "web(vk1.a.*)" else "kate-style"
            val tokenPreview = result.accessToken.take(8) + "…" + result.accessToken.takeLast(4)
            val hasSecret = if (result.secret.isNullOrBlank()) "NO" else "YES"
            val hasExchange = if (result.exchangeToken.isNullOrBlank()) "NO" else "YES"
            val hasTrustedHash = if (result.trustedHash.isNullOrBlank()) "NO" else "YES"
            val utilityCount = result.utilityTokens?.tokens?.size ?: 0
            AppLog.i(TAG, "Auth success — user_id=${result.userId}, scope=${result.scope}, " +
                "token=$tokenType[$tokenPreview], secret=$hasSecret, exchange_token=$hasExchange, " +
                "trusted_hash=$hasTrustedHash, utility_tokens=$utilityCount, expires_in=${result.expiresIn}")
            return AuthState.Success(result)
        }

        // Build error object — merge root-level error_description/error_code
        // (oauth.vk.com format) into the err object.
        val err = json.get("error")?.let { elem ->
            if (elem.isJsonObject) elem.asJsonObject
            else if (elem.isJsonPrimitive) JsonObject().apply { addProperty("error", elem.asString) }
            else null
        } ?: return AuthState.Error(AuthErrorKind.PARSE, "Unexpected response: ${json.toString().take(200)}")

        // oauth.vk.com puts error_description and error_code at root level.
        if (!err.has("error_description")) {
            json.get("error_description")?.takeIf { !it.isJsonNull }?.let {
                err.add("error_description", it)
            }
        }
        if (!err.has("error_code")) {
            json.get("error_code")?.takeIf { !it.isJsonNull }?.let {
                err.add("error_code", it)
            }
        }

        return AuthResponseParser.parseErrorState(err)
    }


    // #SESSION-WEB-MECHANISM (2026-09-24): silentRefreshViaRemixsid + RemixsidRefreshResult +
    // OriginStrategy + doSilentRefreshRequest + doSingleSilentRefreshHttp удалены.
    // HTTP-обмен по стейл-копии cookies из storage — корень багов «смена сети →
    // просит логин» (#SESSION-COOKIES-BG-REFRESH, #NET-SWITCH-AUTH-FIX — все
    // костыли этого класса больше не нужны). Вместо него — скрытый WebView
    // ([HiddenSessionRefresher]): живой cookie jar, как в веб-версии.

    private companion object {
        const val TAG = "ExchangeAuthRepo"
        // #SESSION-WEB-MECHANISM: SILENT_REFRESH_COOLDOWN_MS переехал в
        // HiddenSessionRefresher.FAIL_COOLDOWN_MS (кулдаун скрытого WebView-refresh).
    }
}

/**
 * P0 #KEEPALIVE-BACKOFF: результат проактивной проверки сессии ([ExchangeAuthRepository.keepAlive]).
 *
 * Позволяет [re.pinok.SovaApp.startKeepAlive] различать три случая и адаптивно
 * управлять интервалом опроса:
 *  - NOT_NEEDED — токен не в окне истечения, ничего не делали;
 *  - REFRESHED  — silent refresh успешно обновил токен;
 *  - FAILED     — токен в окне истечения, но все silent-пути провалились.
 *                 Нужен повтор с бэк-оффом (а не ожидание полного 60с цикла).
 */
enum class KeepAliveResult { NOT_NEEDED, REFRESHED, FAILED }
