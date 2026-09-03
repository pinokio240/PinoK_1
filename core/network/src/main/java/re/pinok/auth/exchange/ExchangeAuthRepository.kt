package re.pinok.auth.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog
import re.pinok.util.ExponentialBackoff
import re.pinok.util.NetworkObserver
import java.io.IOException
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
class ExchangeAuthRepository(
    private val api: ExchangeAuthApi,
    private val storage: ExchangeTokenStorage,
    private val httpClient: OkHttpClient? = null,
    private val prefs: SovaPrefs? = null,
) {

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

    /** True если сеть сейчас недоступна (по [NetworkObserver.isOnline]). */
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
     * Fix #49 #DEAD-REMIXSID: выставляется в true внутри silentRefreshViaRemixsid
     * когда VK отверг куки на ВСЕХ стратегиях Origin (wrong origin/unauthorized)
     * или вернул явный VK error. ensureFreshToken читает флаг после провала и
     * чистит remixsid через storage.clearRemixsid() — иначе AuthActivity зацикливается
     * в SILENT-режиме (мёртвый remixsid → silent refresh fail → notifyTokenInvalidated
     * → снова SILENT, бесконечно). Сетевые ошибки НЕ выставляют флаг — только
     * definitive VK-side rejection. @Volatile т.к. читается из ensureFreshToken
     * (может быть на другом dispatcher'е, но внутри refreshMutex — safe.
     *
     * Fix #144 (2026-08-04): Жалоба «Почему долгий вход». Логкат показал что
     * silentRefreshViaRemixsid ВСЕ 7 стратегий возвращают «wrong origin» или
     * «unauthorized» — это CONTRACT FAILURE (VK изменил endpoint contract),
     * НЕ dead remixsid. WebView cookie polling находит свежий remixsid len=88.
     * Но ensureFreshToken ЧИСТИТ remixsid (Fix #49), из-за чего последующие
     * retry-итерации падают с «no remixsid/userId stored» → полный re-login
     * через external browser OAuth вместо silent.
     *
     * РЕШЕНИЕ: разделить «definitively dead» (явный auth-rejection) от
     * «contract failure» (wrong origin/unauthorized/no token). lastRemixsidContractFailure
     * НЕ чистит remixsid — даём шанс WebView найти свежий remixsid и пере-сделать
     * silent refresh с ним.
     */
    @Volatile
    private var lastRemixsidDefinitivelyDead: Boolean = false

    /**
     * Fix #144: CONTRACT FAILURE — VK endpoint отверг запрос на уровне контракта
     * (wrong origin / unauthorized / no access_token), НЕ на уровне credentials.
     * В отличие от lastRemixsidDefinitivelyDead, этот флаг НЕ триггерит чистку
     * remixsid — remixsid может быть валиден (WebView cookie polling находит его),
     * просто silent refresh через HTTP не работает пока VK не починят endpoint.
     *
     * Влияние на ensureFreshToken:
     *  - lastRemixsidDefinitivelyDead=true → clearRemixsid (старое поведение Fix #49)
     *  - lastRemixsidContractFailure=true  → НЕ чистим, falling through to WebView
     */
    @Volatile
    private var lastRemixsidContractFailure: Boolean = false

    /**
     * #VKID-SESSION-WIPE-GUARD (логкэт 2026-08-07 16:48:31): persistent результат
     * последнего silentRefreshViaRemixsid — был ли remixsid ОДНОЗНАЧНО мёртв
     * (VK вернул явный {"error":...} JSON с auth rejection).
     *
     * В отличие от [lastRemixsidDefinitivelyDead] (который сбрасывается после
     * использования внутри ensureFreshToken), этот флаг НЕ сбрасывается
     * автоматически — живёт до следующего вызова ensureFreshToken. Это позволяет
     * caller'у (AuthViewModel.submitWebToken) различить:
     *
     *  - wasLastSilentRefreshDefinitivelyDead()=true → remixsid точно мёртв,
     *    можно вызывать clearDeadSessionForRetry (старое поведение).
     *  - wasLastSilentRefreshDefinitivelyDead()=false → silentRefresh упал по
     *    CONTRACT failure (wrong origin / unauthorized / parsing bug / network),
     *    remixsid может быть валиден → НЕ чистить сессию, иначе уничтожим
     *    рабочую 2FA-сессию и заставим пользователя перелогиниваться.
     *
     * Баг-сценарий который это фиксит: VK login.vk.com вернул
     * {"type":"okay","data":{"access_token":...}} но код не распарсил (читал из
     * корня) → "no access_token (contract failure)" → все 7 strategies failed →
     * submitWebToken: "Path 1.5 тоже упал (remixsid мёртв)" → clearDeadSessionForRetry
     * → УНИЧТОЖИЛ валидную сессию. С этим флагом clearDeadSessionForRetry НЕ
     * вызывается при contract failure.
     */
    @Volatile
    private var lastSilentRefreshDefinitivelyDeadResult: Boolean = false

    /** Публичный геттер для [lastSilentRefreshDefinitivelyDeadResult]. */
    fun wasLastSilentRefreshDefinitivelyDead(): Boolean = lastSilentRefreshDefinitivelyDeadResult

    /**
     * Fix #177+#178 #SILENT-REFRESH-COOLDOWN (2026-08-04, лог 17:33:09–17:35:15):
     * Если silentRefreshViaRemixsid отработал все 7 стратегий и все упали с
     * wrong origin / unauthorized — повторять его каждую минуту бессмысленно:
     * VK endpoint не поменяется за 5 минут, а батарея и трафик тратятся
     * (3 цикла × 7 стратегий = 21 HTTP запрос за 2 минуты в логе).
     *
     * Кулдаун: после первого провала silent refresh не запускается
     * SILENT_REFRESH_COOLDOWN_MS (5 минут). Каждый новый ensureFreshToken
     * в этот период сразу fallback к WebView flow.
     *
     * Сбрасывается при успешном refresh (через другой path) или при
     * регенерации remixsid (login flow).
     */
    @Volatile
    private var lastSilentRefreshFailMs: Long = 0L

    // =====================================================================
    // Sign-in flows (used by AuthViewModel)
    // =====================================================================

    /** Step 1 — submit phone + password. Mirrors VkAuthState.b(sid=null). */
    suspend fun signIn(phone: String, password: String): AuthState {
        val deviceId = storage.deviceId()
        storage.saveLastPhone(phone)
        storage.saveCredentials(phone, password)

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
    suspend fun signInByTrustedHash(phone: String, trustedHash: String): AuthState {
        val deviceId = storage.deviceId()
        return try {
            // #NETWORK-RESILIENCE: backoff на trusted_hash re-login (auto-login при старте).
            // Один IOException при холодном старте в метро больше НЕ прерывает auto-login.
            val json = ExponentialBackoff.retryOnTransient(
                strategy = ExponentialBackoff.AUTH_DEFAULT,
                tag = "authByTrustedHash",
            ) {
                api.authByTrustedHash(phone, trustedHash, deviceId)
            } ?: return AuthState.Error(
                AuthErrorKind.NETWORK,
                "Нет связи с VK. Попробуйте позже.",
            )
            parseAuthResponse(json)
        } catch (e: VKAuthException) {
            AppLog.e(TAG, "signInByTrustedHash error: ${e.javaClass.simpleName}", e)
            AuthResponseParser.authStateException(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "signInByTrustedHash error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

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

            // #REMIXSID-CAPTURE / #SESSION-COOKIES (§41.22): Если session cookies
            // переданы напрямую (из OAuthWebViewActivity, где они захвачены из
            // CookieManager после in-app web-логина), сохраняем их ДО вызова backfill.
            // backfillRemixsidFromCookieManager проверит storage.remixsid()
            // и пропустит если уже сохранён.
            //
            // §55 #SSO-FULL-COOKIE-SET / §57 #COOKIE-CAPTURE-UNIFY: сохраняем
            // ВЕСЬ браузерный cookie-set (9 кук). VK login.vk.ru/?act=web_token
            // валидирует полный набор — без httoken/nttpid/uacck/uas/dmgr/mvkfp
            // silent refresh часто падает (SSO loop §54). Все 6 доп. кук опциональны:
            // saveSessionCookiesOnly использует patch-семантику (null = не трогать).
            if (remixsid != null && remixsid.isNotBlank()) {
                storage.saveSessionCookiesOnly(
                    remixsid = remixsid,
                    p = pCookie,
                    remixnsid = remixnsid,
                    // §55: полный cookie-set.
                    httoken = httoken,
                    remixnttpid = remixnttpid,
                    remixuacck = remixuacck,
                    remixuas = remixuas,
                    remixdmgr = remixdmgr,
                    remixmvkFp = remixmvkFp,
                )
                AppLog.i(TAG, "saveOAuthToken: session cookies saved from OAuthWebView " +
                    "(remixsid len=${remixsid.length}, " +
                    "p=${if (pCookie != null) "yes" else "no"}, " +
                    "remixnsid=${if (remixnsid != null) "yes" else "no"}, " +
                    "httoken=${if (httoken != null) "yes" else "no"}, " +
                    "nttpid=${if (remixnttpid != null) "yes" else "no"}, " +
                    "uacck=${if (remixuacck != null) "yes" else "no"}, " +
                    "uas=${if (remixuas != null) "yes" else "no"}, " +
                    "dmgr=${if (remixdmgr != null) "yes" else "no"}, " +
                    "mvkfp=${if (remixmvkFp != null) "yes" else "no"}) — " +
                    "Path 1.5 enabled (cross-IP silent refresh, full cookie-set)")
            }

            // Fix #215 (P0.4): backfill remixsid из CookieManager.
            // Это позволяет Path 1.5 в ensureFreshToken работать для
            // external browser / OAuth WebView flow (где remixsid не
            // сохраняется автоматически как в web flow).
            // Если remixsid уже сохранён (выше), backfill пропустит.
            backfillRemixsidFromCookieManager(userId)

            // Fix #211: сбрасываем auto-offline флаг при успешной авторизации.
            // privacyOfflineMode мог быть включён авто-офлайном (#38) в прошлой
            // сессии после сетевых ошибок и сохраниться в DataStore. Без сброса
            // все API-вызовы шорт-сиркитятся → «приложение не грузится» после
            // re-login. Успешный логин = сеть работает → offline не нужен.
            runCatching {
                prefs?.setPrivacyOfflineMode(false)
                AppLog.i(TAG, "OAuth auth success — privacyOfflineMode reset to false (was auto-enabled by #38)")
            }
            AppLog.i(TAG, "OAuth WebView auth success — user_id=$userId, exchange_token=${if (exchangeToken != null) "yes" else "no"}, " +
                "remixsid=${if (storage.remixsid() != null) "yes" else "no"}, " +
                "p=${if (storage.pCookie() != null) "yes" else "no"}, " +
                "remixnsid=${if (storage.remixnsid() != null) "yes" else "no"}")
            AuthState.Success(result)
        } catch (e: Exception) {
            AppLog.e(TAG, "saveOAuthToken error", e)
            AuthState.Error(AuthErrorKind.NETWORK, e.message ?: "network error")
        }
    }

    /**
     * #REMIXSID-CAPTURE (§41.22): Проверяет, сохранён ли remixsid в storage.
     *
     * Используется AuthViewModel.submitOAuthToken чтобы решить, нужно ли
     * запускать RemixsidCapturer (best-effort hidden WebView) после
     * saveOAuthToken.
     *
     * @return true если remixsid сохранён (Path 1.5 доступна)
     */
    fun hasRemixsid(): Boolean {
        val remixsid = storage.remixsid()
        return remixsid != null && remixsid.isNotBlank()
    }

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

    /**
     * #SESSION-COOKIES-BG-REFRESH: результат фонового обновления cookies.
     *
     * @param remixsidChanged true если remixsid в CookieManager отличается от
     *   сохранённого в storage (VK ротейтит remixsid при security events).
     * @param pChanged true если p cookie изменился (persistent login ротейт).
     * @param remixnsidChanged true если remixnsid изменился (VK ID сессия).
     * @param anyChanged true если хоть один из трёх обновлён.
     * @param hadAllThree true если после refresh в storage есть все 3 cookie
     *   (Path 1.5 silent cross-IP refresh полностью готова).
     */
    data class CookieRefreshResult(
        val remixsidChanged: Boolean,
        val pChanged: Boolean,
        val remixnsidChanged: Boolean,
        val hadAllThree: Boolean,
        // §55 #SSO-FULL-COOKIE-SET: 6 доп. кук + флаг полного набора.
        val httokenChanged: Boolean = false,
        val nttpidChanged: Boolean = false,
        val uacckChanged: Boolean = false,
        val uasChanged: Boolean = false,
        val dmgrChanged: Boolean = false,
        val mvkfpChanged: Boolean = false,
        val hadAllCookies: Boolean = false,
    ) {
        val anyChanged: Boolean get() = remixsidChanged || pChanged || remixnsidChanged ||
            httokenChanged || nttpidChanged || uacckChanged ||
            uasChanged || dmgrChanged || mvkfpChanged
    }

    /**
     * #SESSION-COOKIES-BG-REFRESH: синхронизирует session cookies (remixsid +
     * p + remixnsid) из CookieManager → storage.
     *
     * ПРОБЛЕМА: `backfillRemixsidFromCookieManager` вызывается ТОЛЬКО в момент
     * логина (saveOAuthToken / saveWebTokenResult). После логина cookies в
     * storage НИКОГДА не обновляются. Но VK ротейтит cookies:
     *   - remixsid: на security events, при web-навигации через m.vk.ru
     *   - p (persistent login): периодически (~раз в пару недель)
     *   - remixnsid (VK ID): при пере-входе через ID SDK
     * CookieManager (WebView) обновляется автоматически при любой web-навигации
     * внутри app (m.vk.ru, stories browser, и т.д.). storage — нет.
     *
     * Через несколько дней/недель storage содержит СТЕЙЛОВЫЕ cookies → при
     * смене сети silentRefreshViaRemixsid шлёт устаревший Cookie header → VK
     * отбрасывает → полный re-login через AuthActivity. Это тот же класс
     * бага, что #SESSION-COOKIES, но отложенный во времени.
     *
     * РЕШЕНИЕ: sync CookieManager → storage по трём триггерам:
     *   1. После успешного silentRefreshViaRemixsid (hooks в этом файле).
     *   2. На app foreground (ProcessLifecycleOwner ON_RESUME в SovaApp).
     *   3. Периодически каждые 6 часов (CookieRefreshWorker, WorkManager).
     *
     * Patch-семантика: сохраняются ТОЛЬКО изменившиеся cookies. Если все три
     * уже совпадают с CookieManager — no-op (ранний return, без write).
     *
     * Thread-safety: CookieManager потокобезопасен, storage.apply() атомарен.
     * Двойной вызов безопасен — patch сохраняет только diff.
     *
     * @param forceLogOnNoop если true — логировать даже когда ничего не изменилось
     *   (для foreground/worker триггеров, чтобы видеть что sync отработал).
     * @return [CookieRefreshResult] с описанием что обновилось.
     */
    suspend fun refreshSessionCookiesFromCookieManager(
        forceLogOnNoop: Boolean = false,
    ): CookieRefreshResult = withContext(Dispatchers.IO) {
        try {
            val found = ExternalBrowserAuth.tryFindExistingAuth()
            if (!found.found || found.remixsid.isNullOrBlank()) {
                if (forceLogOnNoop) {
                    AppLog.d(TAG, "refreshSessionCookies: no remixsid in CookieManager — " +
                        "skip (storage untouched). Path 1.5 will use existing stored cookies.")
                }
                return@withContext CookieRefreshResult(
                    remixsidChanged = false,
                    pChanged = false,
                    remixnsidChanged = false,
                    hadAllThree = !storage.remixsid().isNullOrBlank() &&
                                  !storage.pCookie().isNullOrBlank() &&
                                  !storage.remixnsid().isNullOrBlank(),
                )
            }

            val curRemixsid = storage.remixsid()
            val curP = storage.pCookie()
            val curNsid = storage.remixnsid()
            // §55: текущие значения 6 доп. кук.
            val curHttoken = storage.httoken()
            val curNttpid = storage.remixnttpid()
            val curUacck = storage.remixuacck()
            val curUas = storage.remixuas()
            val curDmgr = storage.remixdmgr()
            val curMvkfp = storage.remixmvkFp()

            // Diff: сохраняем только те, что изменились (или были null).
            val newRemixsid = if (found.remixsid != curRemixsid) found.remixsid else null
            val newP = if (found.pCookie != null && found.pCookie != curP) found.pCookie else null
            val newNsid = if (found.remixnsid != null && found.remixnsid != curNsid) found.remixnsid else null
            // §55: diff 6 доп. кук.
            val newHttoken = if (found.httoken != null && found.httoken != curHttoken) found.httoken else null
            val newNttpid = if (found.remixnttpid != null && found.remixnttpid != curNttpid) found.remixnttpid else null
            val newUacck = if (found.remixuacck != null && found.remixuacck != curUacck) found.remixuacck else null
            val newUas = if (found.remixuas != null && found.remixuas != curUas) found.remixuas else null
            val newDmgr = if (found.remixdmgr != null && found.remixdmgr != curDmgr) found.remixdmgr else null
            val newMvkfp = if (found.remixmvkFp != null && found.remixmvkFp != curMvkfp) found.remixmvkFp else null

            val remixsidChanged = newRemixsid != null
            val pChanged = newP != null
            val nsidChanged = newNsid != null
            // §55
            val httokenChanged = newHttoken != null
            val nttpidChanged = newNttpid != null
            val uacckChanged = newUacck != null
            val uasChanged = newUas != null
            val dmgrChanged = newDmgr != null
            val mvkfpChanged = newMvkfp != null

            if (remixsidChanged || pChanged || nsidChanged ||
                httokenChanged || nttpidChanged || uacckChanged ||
                uasChanged || dmgrChanged || mvkfpChanged) {
                storage.saveSessionCookiesOnly(
                    remixsid = newRemixsid,
                    p = newP,
                    remixnsid = newNsid,
                    // §55: 6 доп. кук.
                    httoken = newHttoken,
                    remixnttpid = newNttpid,
                    remixuacck = newUacck,
                    remixuas = newUas,
                    remixdmgr = newDmgr,
                    remixmvkFp = newMvkfp,
                )
                AppLog.i(TAG, "refreshSessionCookies: UPDATED — " +
                    "remixsid=${if (remixsidChanged) "rotated(len=${found.remixsid.length})" else "same"}, " +
                    // PinoK style: smart-cast через pChanged / nsidChanged (= newP/newNsid != null, line 717-718).
                    // Без non-null assertion и без избыточной null-проверки — компилятор сам выводит non-null из булева val.
                    "p=${if (pChanged) "rotated(len=${newP.length})" else "same"}, " +
                    "remixnsid=${if (nsidChanged) "rotated(len=${newNsid.length})" else "same"}, " +
                    // §55
                    "httoken=${if (httokenChanged) "rotated" else "same"}, " +
                    "nttpid=${if (nttpidChanged) "rotated" else "same"}, " +
                    "uacck=${if (uacckChanged) "rotated" else "same"}, " +
                    "uas=${if (uasChanged) "rotated" else "same"}, " +
                    "dmgr=${if (dmgrChanged) "rotated" else "same"}, " +
                    "mvkfp=${if (mvkfpChanged) "rotated" else "same"} — " +
                    "source=${found.source}")
            } else if (forceLogOnNoop) {
                AppLog.d(TAG, "refreshSessionCookies: no changes — all cookies match CookieManager " +
                    "(remixsid len=${curRemixsid?.length ?: 0}, p=${if (curP != null) "yes" else "no"}, " +
                    "remixnsid=${if (curNsid != null) "yes" else "no"}, " +
                    "httoken=${if (curHttoken != null) "yes" else "no"}, " +
                    "nttpid=${if (curNttpid != null) "yes" else "no"}, " +
                    "uacck=${if (curUacck != null) "yes" else "no"}, " +
                    "uas=${if (curUas != null) "yes" else "no"}, " +
                    "dmgr=${if (curDmgr != null) "yes" else "no"}, " +
                    "mvkfp=${if (curMvkfp != null) "yes" else "no"})")
            }

            CookieRefreshResult(
                remixsidChanged = remixsidChanged,
                pChanged = pChanged,
                remixnsidChanged = nsidChanged,
                hadAllThree = !storage.remixsid().isNullOrBlank() &&
                              !storage.pCookie().isNullOrBlank() &&
                              !storage.remixnsid().isNullOrBlank(),
                // §55
                httokenChanged = httokenChanged,
                nttpidChanged = nttpidChanged,
                uacckChanged = uacckChanged,
                uasChanged = uasChanged,
                dmgrChanged = dmgrChanged,
                mvkfpChanged = mvkfpChanged,
                hadAllCookies = !storage.remixsid().isNullOrBlank() &&
                    !storage.pCookie().isNullOrBlank() &&
                    !storage.remixnsid().isNullOrBlank() &&
                    !storage.httoken().isNullOrBlank() &&
                    !storage.remixnttpid().isNullOrBlank() &&
                    !storage.remixuacck().isNullOrBlank() &&
                    !storage.remixuas().isNullOrBlank() &&
                    !storage.remixdmgr().isNullOrBlank() &&
                    !storage.remixmvkFp().isNullOrBlank(),
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "refreshSessionCookies error: ${e.message}")
            CookieRefreshResult(
                remixsidChanged = false,
                pChanged = false,
                remixnsidChanged = false,
                hadAllThree = !storage.remixsid().isNullOrBlank() &&
                              !storage.pCookie().isNullOrBlank() &&
                              !storage.remixnsid().isNullOrBlank(),
            )
        }
    }

    /**
     * #REMIXSID-CAPTURE (§41.22): Сохраняет remixsid, захваченный
     * RemixsidCapturer'ом через скрытый WebView.
     *
     * Вызывается из AuthViewModel после успешного capture. Не перезаписывает
     * access_token/exchange_token — только remixsid (через saveRemixsidOnly).
     *
     * @param remixsid Значение remixsid cookie (len >= 20)
     */
    /**
     * #SESSION-COOKIES: сохранить session cookies (remixsid + p + remixnsid).
     *
     * Принимает [RemixsidCapturer.CapturedCookies] — все три cookie, захваченные
     * из CookieManager. p/remixnsid могут быть null если не найдены.
     *
     * Вызывается из AuthViewModel после RemixsidCapturer.capture().
     */
    fun saveRemixsid(captured: RemixsidCapturer.CapturedCookies) {
        if (captured.remixsid.isBlank()) return
        storage.saveSessionCookiesOnly(
            remixsid = captured.remixsid,
            p = captured.pCookie,
            remixnsid = captured.remixnsid,
            // §55 #SSO-FULL-COOKIE-SET: полный cookie-set как браузер.
            httoken = captured.httoken,
            remixnttpid = captured.remixnttpid,
            remixuacck = captured.remixuacck,
            remixuas = captured.remixuas,
            remixdmgr = captured.remixdmgr,
            remixmvkFp = captured.remixmvkFp,
        )
        AppLog.i(TAG, "saveRemixsid: saved — remixsid len=${captured.remixsid.length}, " +
            "p=${if (captured.pCookie != null) "yes" else "no"}, " +
            "remixnsid=${if (captured.remixnsid != null) "yes" else "no"}, " +
            // §55: полный cookie-set
            "httoken=${if (captured.httoken != null) "yes" else "no"}, " +
            "nttpid=${if (captured.remixnttpid != null) "yes" else "no"}, " +
            "uacck=${if (captured.remixuacck != null) "yes" else "no"}, " +
            "uas=${if (captured.remixuas != null) "yes" else "no"}, " +
            "dmgr=${if (captured.remixdmgr != null) "yes" else "no"}, " +
            "mvkfp=${if (captured.remixmvkFp != null) "yes" else "no"} — " +
            "Path 1.5 enabled (full browser cookie-set) for future network switches")
    }

    /** Backwards-compat: сохраняет только remixsid (для старых call sites). */
    fun saveRemixsid(remixsid: String) {
        if (remixsid.isBlank()) return
        storage.saveRemixsidOnly(remixsid)
        AppLog.i(TAG, "saveRemixsid: saved (len=${remixsid.length}) — Path 1.5 enabled for future network switches")
    }

    /**
     * Fix #215 (P0.4) / #SESSION-COOKIES (2026-08-04): Backfill session cookies
     * (remixsid + p + remixnsid) из CookieManager в storage.
     *
     * External browser flow (Chrome/Яндекс через chooser) и внутренний
     * OAuth WebView НЕ сохраняют session cookies в storage автоматически (только
     * web flow через m.vk.ru их сохраняет). Без remixsid Path 1.5 в
     * ensureFreshToken (silent refresh через login.vk.ru) не работает →
     * при истечении access_token запускается AuthActivity overlay.
     *
     * #SESSION-COOKIES: теперь захватываем ТРИ cookie, не только remixsid:
     *   - remixsid (1_xxx, .vk.ru) — классическая сессия
     *   - p (vk1.a.xxx, .login.vk.ru) — persistent login, критичен для cross-IP
     *   - remixnsid (vk1.a.xxx, vk.ru) — новая VK ID сессия
     *
     * Без p cookie silentRefreshViaRemixsid падает после смены сети (VK отвергает
     * login.vk.ru/?act=web_token без persistent login cookie). p — это "remember
     * me" token, браузер использует его для восстановления сессии после IP-смены.
     *
     * @param userId ID пользователя для проверки соответствия cookie
     */
    private fun backfillRemixsidFromCookieManager(userId: Long) {
        try {
            // #SESSION-COOKIES: backfill не только remixsid, но и p + remixnsid.
            // Раньше early-return если remixsid уже сохранён → p/remixnsid никогда
            // не захватывались для web_token flow (где remixsid передаётся напрямую
            // через submitWebToken). Теперь backfill'им любые недостающие cookies.
            //
            // §55 #SSO-FULL-COOKIE-SET: расширено до всех 9 кук. Если хотя бы одна
            // отсутствует в storage — перечитываем CookieManager и patch'им недостающие.
            val existingRemixsid = storage.remixsid()
            val existingP = storage.pCookie()
            val existingNsid = storage.remixnsid()
            val existingHttoken = storage.httoken()
            val existingNttpid = storage.remixnttpid()
            val existingUacck = storage.remixuacck()
            val existingUas = storage.remixuas()
            val existingDmgr = storage.remixdmgr()
            val existingMvkfp = storage.remixmvkFp()
            // #CALLS-ANTIFRAUD (2026-08-23)
            val existingStid = storage.remixstid()
            val existingStlid = storage.remixstlid()
            val allPresent = !existingRemixsid.isNullOrBlank() && !existingP.isNullOrBlank() &&
                !existingNsid.isNullOrBlank() && !existingHttoken.isNullOrBlank() &&
                !existingNttpid.isNullOrBlank() && !existingUacck.isNullOrBlank() &&
                !existingUas.isNullOrBlank() && !existingDmgr.isNullOrBlank() &&
                !existingMvkfp.isNullOrBlank()
            if (allPresent) {
                AppLog.d(TAG, "backfillRemixsid: all session cookies already in storage — skip")
                return
            }
            val found = ExternalBrowserAuth.tryFindExistingAuth()
            if (found.found && !found.remixsid.isNullOrBlank()) {
                // #SESSION-COOKIES: сохраняем только недостающие cookies (patch-семантика).
                storage.saveSessionCookiesOnly(
                    remixsid = found.remixsid.takeIf { existingRemixsid.isNullOrBlank() },
                    p = found.pCookie.takeIf { existingP.isNullOrBlank() },
                    remixnsid = found.remixnsid.takeIf { existingNsid.isNullOrBlank() },
                    // §55: полный cookie-set — патчим недостающие.
                    httoken = found.httoken.takeIf { existingHttoken.isNullOrBlank() },
                    remixnttpid = found.remixnttpid.takeIf { existingNttpid.isNullOrBlank() },
                    remixuacck = found.remixuacck.takeIf { existingUacck.isNullOrBlank() },
                    remixuas = found.remixuas.takeIf { existingUas.isNullOrBlank() },
                    remixdmgr = found.remixdmgr.takeIf { existingDmgr.isNullOrBlank() },
                    remixmvkFp = found.remixmvkFp.takeIf { existingMvkfp.isNullOrBlank() },
                    // #CALLS-ANTIFRAUD
                    remixstid = found.remixstid.takeIf { existingStid.isNullOrBlank() },
                    remixstlid = found.remixstlid.takeIf { existingStlid.isNullOrBlank() },
                )
                AppLog.i(TAG, "backfillRemixsid: SUCCESS — session cookies saved from CookieManager " +
                    "(source=${found.source}, remixsid len=${found.remixsid.length}, " +
                    "p=${if (found.pCookie != null && existingP.isNullOrBlank()) "yes (new)" else if (!existingP.isNullOrBlank()) "already" else "no"}, " +
                    "remixnsid=${if (found.remixnsid != null && existingNsid.isNullOrBlank()) "yes (new)" else if (!existingNsid.isNullOrBlank()) "already" else "no"}, " +
                    // §55
                    "httoken=${if (found.httoken != null && existingHttoken.isNullOrBlank()) "yes (new)" else if (!existingHttoken.isNullOrBlank()) "already" else "no"}, " +
                    "nttpid=${if (found.remixnttpid != null && existingNttpid.isNullOrBlank()) "yes (new)" else if (!existingNttpid.isNullOrBlank()) "already" else "no"}, " +
                    "uacck=${if (found.remixuacck != null && existingUacck.isNullOrBlank()) "yes (new)" else if (!existingUacck.isNullOrBlank()) "already" else "no"}, " +
                    "uas=${if (found.remixuas != null && existingUas.isNullOrBlank()) "yes (new)" else if (!existingUas.isNullOrBlank()) "already" else "no"}, " +
                    "dmgr=${if (found.remixdmgr != null && existingDmgr.isNullOrBlank()) "yes (new)" else if (!existingDmgr.isNullOrBlank()) "already" else "no"}, " +
                    "mvkfp=${if (found.remixmvkFp != null && existingMvkfp.isNullOrBlank()) "yes (new)" else if (!existingMvkfp.isNullOrBlank()) "already" else "no"}). " +
                    "Path 1.5 silent refresh (cross-IP, full cookie-set) now available for user $userId.")
            } else {
                AppLog.d(TAG, "backfillRemixsid: no remixsid in CookieManager — " +
                    "Path 1.5 will not work for this session (device isolates cookies). " +
                    "Fallbacks: trusted_hash (Path 2.5) + exchange_token (Path 3).")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "backfillRemixsid error: ${e.message}")
        }
    }

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

            // #SESSION-COOKIES: backfill session cookies (p + remixnsid) из CookieManager.
            // web_token flow передаёт только remixsid (из AuthActivity), а p/remixnsid
            // остаются в CookieManager. Без них silentRefreshViaRemixsid падает после
            // смены сети. Backfill читает все три cookie и сохраняет недостающие.
            // Безопасно: если все три уже сохранены — early return.
            backfillRemixsidFromCookieManager(userId)

            AppLog.i(
                TAG,
                "WebToken saved — user_id=$userId, sat=${if (satToken != null) "yes" else "no"}, " +
                    "remixsid=${remixsid?.take(8) ?: "none"}..., " +
                    "exchange=${if (exchangeToken != null) "yes" else "no"}, " +
                    "p=${if (storage.pCookie() != null) "yes" else "no"}, " +
                    "remixnsid=${if (storage.remixnsid() != null) "yes" else "no"}"
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
    suspend fun ensureFreshToken(force: Boolean = false): String? = refreshMutex.withLock {
        // #VKID-SESSION-WIPE-GUARD: сбрасываем persistent флаг в начале каждого
        // ensureFreshToken — caller (submitWebToken) читает его ПОСЛЕ возврата,
        // должен получить результат ИМЕННО этого вызова, а не предыдущего.
        lastSilentRefreshDefinitivelyDeadResult = false

        // #FORCE-REFRESH (2026-08-02): при force=true пропускаем short-circuit.
        // err=5/1130 означает VK отверг токен — hasValidAccessToken() лжёт
        // (проверяет только timestamp, не IP binding). Нужен реальный refresh
        // через Path 1.5 (silentRefreshViaRemixsid) для получения токена на новый IP.
        if (!force && storage.hasValidAccessToken()) {
            return@withLock storage.accessToken()
        }
        if (force) {
            AppLog.i(TAG, "ensureFreshToken: FORCE refresh — bypassing hasValidAccessToken " +
                "(err=5/1130 or network switch — token rejected by VK, refreshing via Path 1.5/5)")
        }

        // #NETWORK-RESILIENCE (2026-08-04): offline-guard.
        // Если сеть точно недоступна (NetworkObserver.isOnline == false) — НЕ
        // запускаем Path 0/1.5/2.5/3 (они всё равно упадут по IOException, а
        // ExponentialBackoff будет 7 секунд ретраить в пустую). Сразу возвращаем
        // null — caller (VKApiClient error handler / AuthViewModel) перейдёт в
        // OfflineWithCache вместо каскада AuthActivity.
        //
        // ИСКЛЮЧЕНИЕ: force=true остаётся (если VK явно отверг токен err=5/1130,
        // возможно сеть "есть" но с новым IP — NetworkObserver мог ещё не
        // поймать switch, дадим silent refresh шанс). Без force — экономим
        // батарею и время пользователя в метро/лифте.
        //
        // File backup recovery (Path 0) тоже пропускаем — он работает только если
        // в файле есть валидный токен, а hasValidAccessToken() уже это проверил.
        if (!force && isOffline()) {
            AppLog.i(TAG, "ensureFreshToken: OFFLINE — skipping all refresh paths " +
                "(NetworkObserver.isOnline=false). Returning null, caller should show OfflineWithCache.")
            return@withLock null
        }

        // ── Path 1.5: Silent refresh via remixsid (Fix #212, P0.1) ──
        // Тот же трюк, что использует m.vk.ru JS: прямой HTTP GET к
        // login.vk.ru/?act=web_token с Cookie header. НЕ требует WebView,
        // работает за 200мс. Основной silent-refresh путь: remixsid захватывается
        // при входе через WebView VK ID и переживает истечение access_token + смену IP.
        //
        // #SSO-NO-USERID-GATE: remixsid_user cookie опционален — login.vk.ru сам
        // возвращает user_id в ответе, поэтому Path 1.5 работает даже с userId=0.
        val remixsid = storage.remixsid()
        if (!remixsid.isNullOrBlank()) {
            val userId = storage.userId()
            val refreshed = try {
                silentRefreshViaRemixsid(remixsid, userId)
            } catch (e: Exception) {
                AppLog.w(TAG, "silentRefreshViaRemixsid error: ${e.message}")
                null
            }
            if (refreshed != null) {
                AppLog.i(TAG, "ensureFreshToken: silent refresh via remixsid OK — " +
                    "user_id=${refreshed.userId}, expires_at=${refreshed.expiresAt}")
                return@withLock refreshed.accessToken
            }
            // Fix #49 #DEAD-REMIXSID: если VK явно отверг remixsid ({"error":...} JSON) —
            // чистим его, чтобы следующий launchAuth пошёл в FULL (видимый вход).
            // Контрактные ошибки (wrong origin/unauthorized/parsing) НЕ триггерят чистку.
            if (lastRemixsidDefinitivelyDead) {
                AppLog.w(TAG, "ensureFreshToken: remixsid definitively dead " +
                    "(VK explicit auth rejection) — clearing to break SILENT loop (Fix #49)")
                runCatching { storage.clearRemixsid() }
                    .onFailure { AppLog.w(TAG, "clearRemixsid failed: ${it.message}") }
                lastRemixsidDefinitivelyDead = false
                lastSilentRefreshDefinitivelyDeadResult = true
            } else if (lastRemixsidContractFailure) {
                AppLog.w(TAG, "ensureFreshToken: remixsid contract failure (VK changed " +
                    "endpoint) — KEEPING remixsid for retry (Fix #144)")
                lastRemixsidContractFailure = false
                lastSilentRefreshDefinitivelyDeadResult = false
            }
        } else {
            AppLog.d(TAG, "ensureFreshToken: no remixsid stored, skipping Path 1.5")
        }

        // ── Path 5: connect_exchange_token (VK ID web SDK flow) ──
        // POST login.vk.com/?act=connect_exchange_token с {token, hash}.
        // Работает даже когда remixsid мёртв: VK проверяет сессию по logout_hash,
        // который переживает инвалидацию access_token. Быстрый HTTP (~200мс).
        val connectToken = tryConnectExchangeToken()
        if (connectToken != null) {
            AppLog.i(TAG, "ensureFreshToken: connect_exchange_token OK (Path 5)")
            return@withLock connectToken
        }

        AppLog.w(TAG, "ensureFreshToken: Path 1.5 + Path 5 failed — re-login required")
        return@withLock null
    }

    // =====================================================================
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
    fun userId(): Long = storage.userId()

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
     * Fix #107: remixsid accessor для silent re-login проверки.
     *
     * MainActivity.tokenInvalidationTick вызывает это чтобы решить,
     * запускать AuthActivity в silent mode (есть remixsid → авто re-login)
     * или в обычном режиме (нет remixsid → полный ручной вход).
     */
    fun remixsid(): String? = storage.remixsid()

    /**
     * #NO-SILENT-MEANS (2026-08-02): public accessor для VKApiClient.
     * Проверяет, есть ли у нас способ silent re-login (Path 1.5 — remixsid).
     *
     * #AUTH-SIMPLIFY (2026-08-15): после удаления Path 2.5/3/4 silent refresh
     * работает ТОЛЬКО через remixsid (Path 1.5). p cookie живёт 1 год на
     * .login.vk.ru и НЕ привязан к IP — даже если remixsid потерян, p позволяет
     * silentRefreshViaRemixsid получить новый web_token.
     *
     * Если ни того, ни другого нет (типично для external browser auth, где
     * cookies изолированы) — VKApiClient делает grace + single retry (#IP-BINDING-RETRY),
     * затем полный re-login.
     */
    fun hasSilentReloginMeans(): Boolean {
        val hasRemixsid = !storage.remixsid().isNullOrBlank()
        val hasPCookie = !storage.pCookie().isNullOrBlank()
        return hasRemixsid || hasPCookie
    }

    /** Check if we have a trusted_hash for passwordless re-login. */
    fun canTrustedHashLogin(): Boolean {
        val hash = storage.trustedHash()
        val phone = storage.lastPhone()
        return !hash.isNullOrBlank() && !phone.isNullOrBlank()
    }

    /**
     * Attempt silent trusted-hash login on app start.
     * Returns the full AuthState (Success with all fields persisted) or non-Success.
     * The caller should fall back to showing the login form on non-Success.
     */
    suspend fun tryTrustedHashLoginFullState(): AuthState {
        val hash = storage.trustedHash() ?: return AuthState.Error(AuthErrorKind.UNKNOWN, "No trusted_hash")
        val phone = storage.lastPhone() ?: return AuthState.Error(AuthErrorKind.UNKNOWN, "No last_phone")
        val state = signInByTrustedHash(phone, hash)
        if (state is AuthState.Success) {
            AppLog.i(TAG, "Trusted hash login success (user ${state.result.userId})")
        } else {
            AppLog.w(TAG, "Trusted hash login failed: $state")
        }
        return state
    }

    /**
     * Attempt silent trusted-hash login — returns access_token string only.
     * Convenience for callers that only need the token.
     */
    suspend fun tryTrustedHashLogin(): String? {
        val state = tryTrustedHashLoginFullState()
        return if (state is AuthState.Success) state.result.accessToken else null
    }

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

    /**
     * Path 5: connect_exchange_token — VK ID web SDK token exchange.
     *
     * POST `https://login.vk.com/?act=connect_exchange_token` с телом
     * `token=<access_token>&hash=<logout_hash>`. Возвращает JSON-массив
     * токенов для связанных app_id (или одиночный объект):
     *
     * ```json
     * [{"access_token":"vk1.a.X","user_id":171093180,"expires":1785855515,
     *   "logout_hash":"...","is_active":true}, ...]
     * ```
     *
     * Источник: анализ архива VK ID_веб.zip (2026-08-04),
     * см. VK_IMPORT_API.MD §49.5.3 и §49.6 Sprint VK-ID-6.
     *
     * Это позволяет exchange токен одного app_id (например VK ID QR 7934655)
     * на токен другого (PinoK 6287487) БЕЗ повторного логина. Особенно
     * полезно когда:
     *  - юзер логинился через VK ID QR (app_id 7934655) → у нас есть
     *    `7934655:web_token:login:auth` в storage;
     *  - нам нужен токен для PinoK (app_id 6287487);
     *  - connect_exchange_token({token: <7934655 token>, hash: <logout_hash>})
     *    → массив токенов включая `6287487:web_token:login:auth`.
     *
     * Cookie-based: credentials:"include" + remixsid cookie в CookieManager.
     * Но мы делаем form-urlencoded POST с явными token+hash (как web SDK).
     *
     * @return access_token если exchange успешен, null иначе
     */
    private fun tryConnectExchangeToken(): String? {
        val client = httpClient ?: return null
        val accessToken = storage.accessToken()
        val logoutHash = storage.logoutHash()
        // §50 #TOKEN-LIFECYCLE-FIX: diagnostic — показываем состояние токена.
        // hasValid=false значит либо истёк по timestamp, либо invalidated flag
        // (clearAccessToken). access_token ВСЁ ЕЩЁ в storage (новое поведение
        // §50) — VK примет его если logout_hash валиден.
        val tokenValid = storage.hasValidAccessToken()
        val expiresAt = storage.expiresAt()
        val nowMs = System.currentTimeMillis()
        val expiredStr = if (expiresAt == 0L) "no-expiry" else "${(expiresAt - nowMs) / 1000}s"

        // Условия: оба значения должны быть непустыми.
        // access_token может быть протухшим (VK иногда принимает),
        // logout_hash переживает истечение access_token.
        if (accessToken.isNullOrBlank() || logoutHash.isNullOrBlank()) {
            AppLog.d(TAG, "tryConnectExchangeToken: skip — accessToken=${if (accessToken.isNullOrBlank()) "null" else "present"}, logoutHash=${if (logoutHash.isNullOrBlank()) "null" else "present"}")
            return null
        }
        AppLog.i(TAG, "tryConnectExchangeToken: attempting with access_token=${accessToken.take(8)}… " +
            "(valid=$tokenValid, expires=$expiredStr) + logout_hash=${logoutHash.take(6)}… — " +
            "VK проверяет сессию по logout_hash, не по access_token expiry (§50 #TOKEN-LIFECYCLE-FIX)")

        val url = "https://login.vk.com/?act=connect_exchange_token"
        val formBody = okhttp3.FormBody.Builder()
            .add("token", accessToken)
            .add("hash", logoutHash)
            .build()

        return try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://id.vk.ru/")
                .header("Origin", "https://id.vk.ru")
                .build()

            AppLog.d(TAG, "tryConnectExchangeToken: POST $url (token=${accessToken.take(8)}…, hash=${logoutHash.take(6)}…)")
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string().orEmpty()
                if (code !in 200..299) {
                    AppLog.w(TAG, "tryConnectExchangeToken: HTTP $code body=${body.take(300)}")
                    return null
                }

                // Парсим ответ. Может быть:
                //  1. JSON-массив токенов: [{"access_token":"...","is_active":true}, ...]
                //  2. Одиночный объект: {"access_token":"...","user_id":...}
                //  3. Ошибка: {"error":"...","error_description":"..."}
                //  4. Обёртка VK ID: {"type":"okay","data":{...}} (как в act=web_token,
                //     #VKID-RESPONSE-WRAP) или {"type":"error","error_info":"..."}.
                val rootElem = try {
                    com.google.gson.JsonParser.parseString(body)
                } catch (e: Exception) {
                    AppLog.w(TAG, "tryConnectExchangeToken: non-JSON response — ${body.take(200)}")
                    return null
                }

                // Top-level может быть массивом [ {...} ] или объектом {...}.
                val rootJson: com.google.gson.JsonObject = when {
                    rootElem.isJsonArray -> {
                        val arr = rootElem.asJsonArray
                        if (arr.isEmpty) {
                            AppLog.w(TAG, "tryConnectExchangeToken: empty response array — no linked app_id tokens")
                            return null
                        }
                        val first = arr.firstOrNull { it.isJsonObject }
                        if (first == null) {
                            AppLog.w(TAG, "tryConnectExchangeToken: array element is not object — ${body.take(200)}")
                            return null
                        }
                        first.asJsonObject
                    }
                    rootElem.isJsonObject -> rootElem.asJsonObject
                    else -> {
                        AppLog.w(TAG, "tryConnectExchangeToken: unexpected response shape — ${body.take(200)}")
                        return null
                    }
                }

                // #VKID-RESPONSE-WRAP: явный отказ {"type":"error","error_info":"..."}.
                val rootType = rootJson.get("type")
                if (rootType != null && rootType.isJsonPrimitive && rootType.asString == "error") {
                    val errInfoElem = rootJson.get("error_info")
                    val errInfo = if (errInfoElem != null && !errInfoElem.isJsonNull) errInfoElem.asString else "unknown"
                    AppLog.w(TAG, "tryConnectExchangeToken: VK type=error — error_info=\"$errInfo\"")
                    return null
                }
                // #VKID-RESPONSE-WRAP: обёртка {"type":"okay","data":{...}}.
                val dataElem = rootJson.get("data")
                val json = if (dataElem != null && dataElem.isJsonObject) dataElem.asJsonObject else rootJson

                val errorObj = json.getAsJsonObject("error")
                if (errorObj != null) {
                    val errCodeElem = errorObj.get("error_code")
                    val errCode = if (errCodeElem != null) errCodeElem.asInt else -1
                    val errMsgElem = errorObj.get("error_msg")
                    val errDescElem = errorObj.get("error_description")
                    val errMsg = when {
                        errMsgElem != null -> errMsgElem.asString
                        errDescElem != null -> errDescElem.asString
                        else -> ""
                    }
                    AppLog.w(TAG, "tryConnectExchangeToken: VK error — code=$errCode msg=$errMsg")
                    return null
                }

                // Внутри "response" может быть массив или объект.
                val responseRaw = json.get("response")
                val responseElem = if (responseRaw != null) responseRaw else json

                // Извлекаем access_token + expires из выбранного элемента ответа.
                val token: String
                val expiresSec: Long
                if (responseElem.isJsonArray) {
                    val arr = responseElem.asJsonArray
                    if (arr.isEmpty) {
                        AppLog.w(TAG, "tryConnectExchangeToken: empty response array — no linked app_id tokens")
                        return null
                    }
                    // Предпочитаем is_active=true, иначе первый.
                    var active: com.google.gson.JsonElement? = null
                    for (el in arr) {
                        if (el.isJsonObject) {
                            val isActiveElem = el.asJsonObject.get("is_active")
                            if (isActiveElem != null && isActiveElem.isJsonPrimitive && isActiveElem.asBoolean) {
                                active = el
                                break
                            }
                        }
                    }
                    val chosen = if (active != null) active else arr.first()
                    if (!chosen.isJsonObject) {
                        AppLog.w(TAG, "tryConnectExchangeToken: response array element is not object")
                        return null
                    }
                    val chosenObj = chosen.asJsonObject
                    val t = chosenObj.get("access_token")
                    token = if (t != null && !t.isJsonNull) t.asString else ""
                    val exp = chosenObj.get("expires")
                    expiresSec = if (exp != null && !exp.isJsonNull) exp.asLong else 0L
                } else if (responseElem.isJsonObject) {
                    val obj = responseElem.asJsonObject
                    val t = obj.get("access_token")
                    token = if (t != null && !t.isJsonNull) t.asString else ""
                    val exp = obj.get("expires")
                    expiresSec = if (exp != null && !exp.isJsonNull) exp.asLong else 0L
                } else {
                    AppLog.w(TAG, "tryConnectExchangeToken: unexpected response shape — ${body.take(200)}")
                    return null
                }

                if (token.isBlank()) {
                    AppLog.w(TAG, "tryConnectExchangeToken: no access_token in response body=${body.take(300)}")
                    return null
                }

                // Сохраняем полученный токен (обновляем access_token в storage).
                // expires — unix seconds, пересчитываем в relative expiresIn.
                val expiresIn = if (expiresSec > 0) {
                    val nowSec = System.currentTimeMillis() / 1000
                    (expiresSec - nowSec).coerceAtLeast(0L)
                } else 0L
                storage.updateAccessToken(
                    accessToken = token,
                    expiresIn = expiresIn,
                    scope = null,
                    exchangeToken = null, // connect_exchange_token не возвращает новый exchange_token
                )
                AppLog.i(TAG, "tryConnectExchangeToken: OK — got token ${token.take(8)}…${token.takeLast(4)} (expires_in=${expiresIn}s)")
                token
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "tryConnectExchangeToken failed: ${e.message}")
            null
        }
    }

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


    // =====================================================================
    // Fix #212 (P0.1): Silent refresh via remixsid (HTTP, no WebView)
    // =====================================================================
    //
    // Тот же endpoint, что использует m.vk.ru JS:
    //   GET https://login.vk.ru/?act=web_token&app_id=7879029&version=1
    //   Headers: Cookie: remixsid=<...>; remixsid_user=<userId>
    //   Response: {"access_token":"vk1.a.XXX","expires":<unix_sec>,
    //              "user_id":<id>,"logout_hash":"<hex>"}
    //
    // Это решает ~90% случаев "выбивает из диалога при отправке фото":
    // access_token обновляется за 200мс без UI, без WebView, без AuthActivity.
    // remixsid уже сохраняется в storage (Fix #106 при saveWebTokenResult),
    // переживает очистку access_token (Fix #106 clearAccessToken).
    //
    // НЕ требует X-VK-Android-Client header — SovaApp.httpClient interceptor
    // проверяет isWebFlowHost (login.vk.com/login.vk.ru) и опускает его.
    //
    // Источник: RESEARCH-VK-WEB-SDK Q2 в worklog.md (строки ~3233-3252).

    /** Результат silent refresh через remixsid. */
    private data class RemixsidRefreshResult(
        val accessToken: String,
        val userId: Long,
        val expiresAt: Long,        // абсолютный ms (0 = offline scope, без истечения)
        val logoutHash: String?,
    )

    /**
     * Silent refresh access_token через remixsid cookie.
     *
     * @param remixsid значение remixsid cookie из storage
     * @param userId id пользователя (для remixsid_user cookie)
     * @return [RemixsidRefreshResult] или null если refresh не удался
     *         (remixsid истёк, network error, не JSON, нет access_token в ответе)
     */
    private suspend fun silentRefreshViaRemixsid(
        remixsid: String,
        userId: Long,
    ): RemixsidRefreshResult? {
        val client = httpClient ?: run {
            AppLog.w(TAG, "silentRefreshViaRemixsid: no httpClient in repository — skip")
            return null
        }
        if (remixsid.isBlank()) return null

        // Fix #177+#178 #SILENT-REFRESH-COOLDOWN (2026-08-04, лог 17:33:09–17:35:15):
        // Если silent refresh уже падал в течение последних 5 минут — НЕ повторяем
        // 7 HTTP запросов (VK endpoint за это время не поменяется, а трафик и
        // батарея тратятся: 3 цикла × 7 стратегий = 21 запрос в логе за 2 мин).
        // Сразу возвращаем null — ensureFreshToken fallback к WebView flow.
        // Кулдаун сбрасывается при успешном refresh в saveToken()/save() ниже.
        val nowMs = System.currentTimeMillis()
        if (lastSilentRefreshFailMs != 0L && nowMs - lastSilentRefreshFailMs < SILENT_REFRESH_COOLDOWN_MS) {
            val remainMs = SILENT_REFRESH_COOLDOWN_MS - (nowMs - lastSilentRefreshFailMs)
            AppLog.i(TAG, "silentRefreshViaRemixsid: SKIPPED — cooldown active " +
                "(last fail ${nowMs - lastSilentRefreshFailMs}ms ago, ${remainMs}ms remaining) — " +
                "falling through to WebView flow (Fix #177+#178 #SILENT-REFRESH-COOLDOWN)")
            return null
        }

        // #SESSION-COOKIES (2026-08-04): Cookie header точно как браузер —
        // отправляем ВСЕ session cookies, не только remixsid.
        //
        // Раньше отправляли только `remixsid=...; remixsid_user=...; remixlang=0`.
        // Но браузер при запросе к login.vk.ru/?act=web_token отправляет ещё:
        //   - p (persistent login, .login.vk.ru) — "remember me" token, без него
        //     VK отвергает silent refresh после смены IP (wrong origin / unauthorized)
        //   - remixnsid (vk1.a.*, vk.ru) — новая VK ID сессия
        //
        // Корень бага "при смене сети поднятие сессии не происходит":
        //   1. Network switch → IP меняется → VK инвалидирует access_token
        //   2. ensureFreshToken → Path 1.5 (silentRefreshViaRemixsid)
        //   3. HTTP-запрос на login.vk.ru/?act=web_token с Cookie: remixsid=...
        //      БЕЗ p cookie → VK видит неполный cookie-set → отвергает
        //   4. Все 7 Origin стратегий падают → ensureFreshToken → null → AuthActivity
        //
        // Фикс: добавляем p и remixnsid в Cookie header (если они сохранены в storage).
        // p cookie — это "remember me" token, валиден ~1 год. VK использует его
        // для silent re-login даже после IP-смены (как браузер).
        //
        // #AUTH-SIMPLIFY (2026-08-15): минимальный cookie-set — 4 ключевых куки.
        // Браузер при запросе к login.vk.ru/?act=web_token отправляет remixsid +
        // p + remixnsid + httoken. Эти куки VK реально валидирует для silent refresh.
        // 6 кук «полного набора» (§55) были добавлены для SSO-loop отладки — теперь
        // не нужны. Все опциональны: если кука не захвачена, не отправляем.
        val pCookieValue = storage.pCookie()
        val remixnsidValue = storage.remixnsid()
        val httokenValue = storage.httoken()
        val cookieHeader = buildString {
            append("remixsid=").append(remixsid)
            // #SSO-NO-USERID-GATE: remixsid_user опционален. После SSO userId=0 —
            // не шлём пустой cookie. login.vk.ru/?act=web_token вернёт user_id сам.
            if (userId > 0L) {
                append("; remixsid_user=").append(userId)
            }
            // p cookie — persistent login (.login.vk.ru), КРИТИЧЕН для cross-IP refresh.
            if (!pCookieValue.isNullOrBlank()) {
                append("; p=").append(pCookieValue)
            }
            // remixnsid — новая VK ID сессия (vk1.a.*).
            if (!remixnsidValue.isNullOrBlank()) {
                append("; remixnsid=").append(remixnsidValue)
            }
            // httoken — anti-CSRF (.vk.ru).
            if (!httokenValue.isNullOrBlank()) {
                append("; httoken=").append(httokenValue)
            }
            // remixlang=0 — русский (нейтральное значение, как в браузере по умолчанию).
            append("; remixlang=0")
        }

        // #AUTH-SIMPLIFY (2026-08-15): 2 origin-стратегии вместо 7.
        // Логи показали: strategy 2/7 [id(id.vk.com)] SUCCEEDED — рабочий Origin
        // это https://id.vk.com на endpoint login.vk.com. m.vk.ru-Origin VK отвергает
        // ('wrong origin'). Вторая стратегия — .ru миграция (login.vk.ru + id.vk.ru).
        val cfg = AuthDomainsConfig.current
        val baseUrl = AuthDomainsConfig.loginWebTokenUrl()

        val strategies = buildList {
            // Стратегия 1 — рабочая (подтверждена логами): Origin id.vk.com + endpoint login.vk.com.
            add(OriginStrategy(origin = "https://${cfg.idHost}", referer = "https://${cfg.idHost}/", originQueryParam = null, label = "id(${cfg.idHost})"))
            // Стратегия 2 — .ru миграция: Origin id.vk.ru + endpoint login.vk.ru.
            add(OriginStrategy(origin = "https://id.vk.ru", referer = "https://id.vk.ru/", originQueryParam = null, label = "alt-endpoint(login.vk.ru)+id(id.vk.ru)", urlOverride = "https://login.vk.ru/?act=web_token&app_id=7879029&version=1"))
        }

        // Fix #49: сбрасываем флаг «remixsid доказанно мёртв» в начале попытки.
        // Если все стратегии упадут с wrong origin/unauthorized — выставим true,
        // и ensureFreshToken почистит remixsid чтобы разорвать SILENT-цикл.
        //
        // Fix #144: сбрасываем также lastRemixsidContractFailure. Если в этом
        // запуске хоть одна стратегия вернёт «wrong origin»/«unauthorized» —
        // выставим contract failure (НЕ definitive dead).
        lastRemixsidDefinitivelyDead = false
        lastRemixsidContractFailure = false

        AppLog.i(TAG, "silentRefreshViaRemixsid: trying ${strategies.size} origin strategies " +
            "(remixsid len=${remixsid.length}, user=$userId)")

        return withContext(Dispatchers.IO) {
            for ((idx, strat) in strategies.withIndex()) {
                val tryUrl = strat.urlOverride ?: if (strat.originQueryParam != null) {
                    // Добавляем &origin=... к URL (URL уже содержит ?act=...&app_id=...&version=1).
                    "$baseUrl&origin=${java.net.URLEncoder.encode(strat.originQueryParam, "UTF-8")}"
                } else baseUrl

                AppLog.d(TAG, "silentRefreshViaRemixsid: strategy ${idx + 1}/${strategies.size} " +
                    "[${strat.label}] Origin=${strat.origin ?: "<none>"}")

                val rawBody = doSilentRefreshRequest(client, tryUrl, cookieHeader, strat)
                if (rawBody == null) {
                    // network failure / non-JSON — не пробуем дальше (remixsid может быть мёртв).
                    AppLog.w(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "network/parse failure — aborting multi-strategy")
                    return@withContext null
                }

                // §44: детектим формат {"type":"error","error_info":"wrong origin"}.
                // Старый парсер искал "error" object/string, но VK web_token endpoint
                // использует "type":"error" + "error_info" — другая схема. Без этой
                // детекции лог показывал "no access_token" вместо понятного "wrong origin".
                val wrongOrigin = AuthResponseParser.isWrongOriginResponse(rawBody)
                if (wrongOrigin) {
                    // Fix #176-auth-loop: 'wrong origin' = CONTRACT failure (VK изменил
                    // endpoint contract). Это ожидаемая ситуация пока VK не вернёт
                    // контракт, либо пока мы не поднимем новый парсер. Логируем на D
                    // (было W) — 7 W-логов на каждый refresh attempt засоряли лог и
                    // выглядели как actionable warning, хотя не требуют действия.
                    AppLog.d(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "→ 'wrong origin' (VK contract failure) — trying next strategy")
                    // Fix #144: wrong origin = CONTRACT failure (VK изменил endpoint
                    // contract), НЕ dead remixsid. Не чистим remixsid.
                    lastRemixsidContractFailure = true
                    continue  // ← следующая стратегия
                }
                // Fix #144: детектим {"type":"error","error_info":"unauthorized"}.
                // VK принимает Origin, но отвергает remixsid как неавторизованный
                // для этого контекста. Это тоже CONTRACT failure (VK требует
                // дополнительные cookies/headers), НЕ dead remixsid — не чистим.
                if (AuthResponseParser.isUnauthorizedResponse(rawBody)) {
                    // Fix #176-auth-loop: 'unauthorized' тоже contract failure.
                    // Логируем на D (было W) — см. комментарий в wrongOrigin выше.
                    AppLog.d(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "→ 'unauthorized' (cookie rejected for this Origin) — trying next strategy")
                    lastRemixsidContractFailure = true
                    continue
                }

                // Парсим JSON: {"access_token":"vk1.a.XXX","expires":<unix_sec>,
                //                "user_id":<id>,"logout_hash":"<hex>"}
                val json = try {
                    JsonParser.parseString(rawBody).asJsonObject
                } catch (e: Exception) {
                    AppLog.w(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "non-JSON response — ${rawBody.take(80)} — aborting")
                    return@withContext null
                }

                // #VKID-RESPONSE-WRAP (логкэт 2026-08-07 16:48:30): VK login.vk.com
                // возвращает обёрнутый формат:
                //   {"type":"okay","data":{"access_token":"vk1.a.*","expires":<sec>,
                //                           "user_id":<id>,"logout_hash":"<hex>"}}
                // или
                //   {"type":"error","error_code":"","error_info":"wrong origin"}
                //
                // БАГ: раньше код читал access_token из КОРНЯ json → для нового формата
                // всегда получал null → "no access_token (contract failure)" →
                // все 7 strategies помечались failed → ensureFreshToken падал →
                // submitWebToken вызывал clearDeadSessionForRetry → УНИЧТОЖАЛ валидную
                // сессию (remixsid + cookies), хотя VK реально вернул токен.
                //
                // ФИКС: если есть поле "data" (объект) — используем его как payload.
                // Иначе — корень (обратная совместимость со старым форматом
                // m.vk.ru localStorage: {"access_token":...} без обёртки).
                val payload = json.getAsJsonObject("data")?.takeIf { it.isJsonObject } ?: json

                // VK может вернуть {"error":"...","error_description":"..."} если remixsid невалиден.
                val errObj = json.getAsJsonObject("error")
                if (errObj != null) {
                    val errCode = errObj.get("error")?.takeIf { !it.isJsonNull }?.asString ?: "unknown"
                    val errMsg = errObj.get("error_description")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    AppLog.w(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "VK error — $errCode: $errMsg — aborting (remixsid likely dead)")
                    // Fix #49: VK явно сказал error — remixsid мёртв.
                    lastRemixsidDefinitivelyDead = true
                    return@withContext null
                }
                val errStr = json.get("error")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
                if (!errStr.isNullOrBlank() && errStr != "0") {
                    AppLog.w(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "VK error string — $errStr — aborting")
                    // Fix #49: VK явно сказал error — remixsid мёртв.
                    lastRemixsidDefinitivelyDead = true
                    return@withContext null
                }
                // #VKID-RESPONSE-WRAP: новый формат {"type":"error","error_info":"..."}
                // (не "wrong origin" и не "unauthorized" — они уже обработаны выше через
                // isWrongOriginResponse/isUnauthorizedResponse). Логируем error_info и
                // continue к следующей strategy — НЕ чистим remixsid (это contract failure,
                // не dead remixsid).
                val typeStr = json.get("type")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
                if (typeStr == "error") {
                    val errInfo = json.get("error_info")?.takeIf { !it.isJsonNull }?.asString ?: "unknown"
                    AppLog.d(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "VK type=error — error_info=\"$errInfo\" — trying next strategy")
                    lastRemixsidContractFailure = true
                    continue
                }

                val accessToken = payload.get("access_token")?.takeIf { !it.isJsonNull }?.asString
                if (accessToken.isNullOrBlank()) {
                    // Fix #176-auth-loop: no access_token без явного error = contract
                    // failure. Логируем на D (было W) — см. комментарий в wrongOrigin.
                    AppLog.d(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] " +
                        "no access_token in response (contract failure) — trying next strategy")
                    // Fix #144: no access_token без явного error = contract failure
                    // (VK endpoint ответил 200 OK, но без токена — контракт изменился).
                    // НЕ dead remixsid — не чистим.
                    lastRemixsidContractFailure = true
                    continue
                }

                AppLog.i(TAG, "silentRefreshViaRemixsid: strategy [${strat.label}] SUCCEEDED " +
                    "(attempt ${idx + 1}/${strategies.size}) — token obtained")

                // expires — unix timestamp в СЕКУНДАХ (как в m.vk.ru localStorage).
                // 0 = бессрочный (offline scope).
                val expiresSec = payload.get("expires")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val expiresAt = if (expiresSec <= 0L) 0L
                                else expiresSec * 1000L  // → ms

                // Fix #233 (P1-4): проверка expiry — последняя линия обороны.
                if (expiresAt != 0L && expiresAt <= System.currentTimeMillis()) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    AppLog.w(TAG, "silentRefreshViaRemixsid: REJECTED expired token " +
                        "(expires=${sdf.format(java.util.Date(expiresAt))} UTC, " +
                        "now=${sdf.format(java.util.Date(System.currentTimeMillis()))} UTC) — не сохраняем")
                    return@withContext null
                }

                // user_id из ответа (может отличаться от storage.userId если мультиакк).
                val responseUserId = payload.get("user_id")?.takeIf { !it.isJsonNull }?.asLong
                                     ?: userId

                val logoutHash = payload.get("logout_hash")?.takeIf { !it.isJsonNull }?.asString

                // Сохраняем обновлённый access_token в storage.
                // scope = "all" — web_token от login.vk.ru имеет полный web-scope.
                // expiresAt — абсолютный ms, переводим в относительный expiresIn.
                val expiresIn = if (expiresAt == 0L) 0L
                                else (expiresAt - System.currentTimeMillis()) / 1000
                storage.saveWebTokenResult(
                    accessToken = accessToken,
                    userId = responseUserId,
                    expiresAt = expiresAt,
                    satToken = null,             // sat_token не возвращается web_token endpoint
                    logoutHash = logoutHash,     // обновляем если VK прислал новый
                    remixsid = remixsid,         // remixsid не меняется, сохраняем тот же
                )

                // Fix #233 (P1-4): сбрасываем privacyOfflineMode после успешного silent
                // refresh — иначе app остаётся офлайн после re-login.
                runCatching {
                    prefs?.setPrivacyOfflineMode(false)
                    AppLog.i(TAG, "silentRefreshViaRemixsid: privacyOfflineMode reset to false")
                }

                AppLog.i(TAG, "silentRefreshViaRemixsid: SUCCESS — user=$responseUserId, " +
                    "expires_in=${if (expiresIn == 0L) "offline" else "${expiresIn}s"}, " +
                    "logout_hash=${if (logoutHash != null) "yes" else "no"}")

                // Fix #177+#178 #SILENT-REFRESH-COOLDOWN: сбрасываем кулдаун
                // при успехе — если VK починил endpoint, следующие silent refresh
                // снова будут пытаться сразу (без 5-мин ожидания).
                lastSilentRefreshFailMs = 0L

                // #SESSION-COOKIES-BG-REFRESH (Hook #1): после успешного silent
                // refresh синхронизируем storage ← CookieManager. AuthActivity
                // SILENT WebView cookie-polling (и сам web_token endpoint через
                // Set-Cookie, если WebView его проксирует) мог обновить cookies
                // в CookieManager. Без этого sync'а следующий silent refresh
                // (через часы/дни) будет слать СТЕЙЛОВЫЙ remixsid/p/remixnsid.
                //
                // Best-effort: ошибка sync'а НЕ роняет успешный refresh —
                // access_token уже сохранён, Path 1.5 уже отработала.
                runCatching {
                    val syncResult = refreshSessionCookiesFromCookieManager()
                    if (syncResult.anyChanged) {
                        AppLog.i(TAG, "silentRefreshViaRemixsid: session cookies synced " +
                            "from CookieManager after success — " +
                            "remixsid=${if (syncResult.remixsidChanged) "rotated" else "same"}, " +
                            "p=${if (syncResult.pChanged) "rotated" else "same"}, " +
                            "remixnsid=${if (syncResult.remixnsidChanged) "rotated" else "same"}")
                    }
                }.onFailure { e ->
                    AppLog.w(TAG, "silentRefreshViaRemixsid: cookie sync after success failed " +
                        "(non-fatal — access_token already saved): ${e.message}")
                }

                return@withContext RemixsidRefreshResult(
                    accessToken = accessToken,
                    userId = responseUserId,
                    expiresAt = expiresAt,
                    logoutHash = logoutHash,
                )
            }  // end for strategies

            // §44: все стратегии вернули 'wrong origin' или empty — refresh провален.
            AppLog.w(TAG, "silentRefreshViaRemixsid: ALL ${strategies.size} origin strategies failed " +
                "(all returned 'wrong origin' or no access_token) — remixsid may be dead or " +
                "VK changed web_token endpoint contract")
            // Fix #177+#178 #SILENT-REFRESH-COOLDOWN: взводим кулдаун чтобы
            // следующие ensureFreshToken в течение 5 минут НЕ повторяли 7 запросов.
            lastSilentRefreshFailMs = System.currentTimeMillis()
            // Fix #144: разделение причин провала.
            //  - lastRemixsidDefinitivelyDead (взведён внутри цикла через явный
            //    {"error":...} JSON) → remixsid точно мёртв, чистим (Fix #49).
            //  - lastRemixsidContractFailure (взведён внутри цикла через
            //    wrong origin/unauthorized/no token) → контракт изменён, но
            //    remixsid может быть валиден. НЕ чистим — даём шанс WebView.
            //
            // Если НЕ было ни одного definitive-dead (явного error JSON), но
            // все стратегии упали с contract failure — НЕ выставляем dead.
            // Это разрывает старый SILENT-loop без уничтожения валидного
            // remixsid. ensureFreshToken falling through to WebView/AuthActivity.
            if (!lastRemixsidDefinitivelyDead && lastRemixsidContractFailure) {
                AppLog.w(TAG, "silentRefreshViaRemixsid: contract failure only (no explicit " +
                    "auth rejection) — remixsid NOT cleared, falling through to WebView flow")
                // Возвращаем null — ensureFreshToken пойдёт в Path 2/2.5/3,
                // но НЕ почистит remixsid (storage.remixsid() остаётся валиден
                // для последующих retry после WebView cookie polling).
                return@withContext null
            }
            // Если был явный auth rejection (definitive dead) — старое поведение.
            // lastRemixsidDefinitivelyDead уже true (взведён внутри цикла).
            // ensureFreshToken почистит remixsid чтобы разорвать SILENT-цикл.
            return@withContext null
        }
    }

    /** §44 #SILENT-REFRESH-ORIGIN-MULTI: стратегия Origin/Referer для silent refresh. */
    private data class OriginStrategy(
        val origin: String?,          // null = не ставить Origin header
        val referer: String,
        val originQueryParam: String?, // null = не добавлять &origin= в URL
        val label: String,             // для логов
        // Fix #49: переопределение endpoint URL для alt-TLD стратегий
        // (login.vk.ru вместо login.vk.com). null = использовать baseUrl.
        val urlOverride: String? = null,
    )


    /**
     * §44 + #NETWORK-RESILIENCE: выполняет HTTP-запрос silent refresh с заданной
     * стратегией Origin, с exponential backoff на transient network failures.
     *
     * Возвращает raw body (String) или null если все retry-попытки провалились
     * (network failure / все 3 attempts исчерпаны). Null НЕ возвращается для
     * HTTP 200 ответов (даже "wrong origin") — только для реальных IOException.
     *
     * Раньше один [IOException] (SocketTimeout, ConnectionReset при handover)
     * прерывал весь multi-strategy loop → `ensureFreshToken` → null →
     * `notifyTokenInvalidated` → AuthActivity cascade. Пользователь жаловался:
     * «выбивает из диалога при малейшем дрожании сети».
     *
     * Теперь: на transient IOException делается до 3 попыток (1s/2s/4s с jitter ±20%).
     * Non-transient ответы (HTTP 200 с body, даже "wrong origin") — НЕ retry
     * (контрактная ошибка, retry бесполезен). См. [ExponentialBackoff].
     *
     * Cancellation-safe: при `currentJob?.cancel()` в AuthViewModel retry-loop
     * мгновенно выходит через CancellationException (не ждёт завершения delay).
     */
    private suspend fun doSilentRefreshRequest(
        client: OkHttpClient,
        url: String,
        cookieHeader: String,
        strat: OriginStrategy,
    ): String? = withContext(Dispatchers.IO) {
        ExponentialBackoff.retryOnTransient(
            strategy = ExponentialBackoff.AUTH_DEFAULT,
            tag = "silentRefresh[${strat.label}]",
        ) { attempt ->
            if (attempt > 1) {
                AppLog.d(TAG, "silentRefreshViaRemixsid [${strat.label}] retry attempt " +
                    "$attempt/${ExponentialBackoff.AUTH_DEFAULT.maxAttempts}")
            }
            doSingleSilentRefreshHttp(client, url, cookieHeader, strat)
        }
    }

    /**
     * Одиночный HTTP-запрос к VK web_token endpoint (без retry).
     *
     * ВАЖНО: в отличие от старой версии, при [onFailure] бросает [IOException]
     * (а не возвращает null) — чтобы [ExponentialBackoff.retryOnTransient] мог
     * отличить transient network failure от non-transient HTTP 200 ответа.
     *
     * При [onResponse] возвращает body как есть (может быть "wrong origin" —
     * это НЕ network error, retry не нужен, парсер выше разберётся).
     *
     * При ошибке чтения body — оборачиваем в [IOException] чтобы retry
     * сработал (транзакция не завершена, тело не прочитано).
     */
    private suspend fun doSingleSilentRefreshHttp(
        client: OkHttpClient,
        url: String,
        cookieHeader: String,
        strat: OriginStrategy,
    ): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
        // §44: Origin — только если стратегия его задаёт (null = не ставим).
        if (strat.origin != null) {
            reqBuilder.header("Origin", strat.origin)
        }
        reqBuilder
            .header("Sec-Fetch-Site", "same-site")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("Referer", strat.referer)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36")
            .get()
        val req = reqBuilder.build()

        suspendCancellableCoroutine<String> { cont ->
            val call = client.newCall(req)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    AppLog.w(TAG, "silentRefreshViaRemixsid [${strat.label}] HTTP failed: ${e.message}")
                    // #NETWORK-RESILIENCE: бросаем IOException вместо resume(null) —
                    // чтобы ExponentialBackoff отличил network failure от HTTP 200.
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val body = response.body?.string()
                        val httpCode = response.code
                        val safeBody = (body ?: "")
                            .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]+\""), "\"access_token\":\"***\"")
                            .take(400)
                        AppLog.i(TAG, "silentRefreshViaRemixsid [${strat.label}] → HTTP $httpCode  body=$safeBody")
                        // Body может быть null если response закрыт раньше времени —
                        // это transient, кидаем IOException для retry.
                        if (cont.isActive) {
                            if (body != null) cont.resume(body)
                            else cont.resumeWithException(IOException("empty response body (HTTP $httpCode)"))
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "silentRefreshViaRemixsid [${strat.label}] read body error: ${e.message}")
                        // Оборачиваем в IOException чтобы ExponentialBackoff среагировал.
                        if (cont.isActive) {
                            cont.resumeWithException(IOException("read body failed: ${e.message}", e))
                        }
                    }
                }
            })
        }
    }


    private companion object {
        const val TAG = "ExchangeAuthRepo"
        /**
         * Fix #177+#178: кулдаун после провала silent refresh (все 7 стратегий).
         *
         * §50 #TOKEN-LIFECYCLE-FIX (2026-08-05): 5 минут → 90 секунд.
         * Пользовательский симптом "токен умирает" часто связан с тем, что
         * silent refresh один раз упал (мёртвый remixsid на 1 цикл), поставил
         * кулдаун 5 минут, и следующие 5 минут любое API-действие падает в
         * AuthActivity SILENT loop. 90 секунд достаточно чтобы:
         *  - не спамить VK если remixsid реально мёртв (нужен ручной re-login)
         *  - но дать шанс Path 5 (connect_exchange_token с сохранённым протухшим
         *    токеном — теперь clearAccessToken его не удаляет) сработать быстрее
         *  - AuthActivity SILENT loop сокращается с 5 мин до 90с
         */
        const val SILENT_REFRESH_COOLDOWN_MS = 90L * 1000L // 90 секунд (было 5 минут)
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
