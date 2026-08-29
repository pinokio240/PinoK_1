package re.pinok

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import re.pinok.api.VKApiClient
import re.pinok.auth.exchange.AccountFileBackup
import re.pinok.auth.exchange.CookieRefreshWorker
import re.pinok.auth.exchange.ExchangeAuthApi
import re.pinok.auth.exchange.ExchangeAuthRepository
import re.pinok.auth.exchange.KeepAliveResult
import re.pinok.auth.exchange.RemixsidCapturer
import re.pinok.auth.exchange.ExchangeTokenStorage
import re.pinok.auth.exchange.ExternalBrowserAuth
import re.pinok.data.local.SovaPrefs
import re.pinok.data.local.TokenStorage
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.media.VideoDownloadManager
import re.pinok.media.StoryVideoDownloadManager
import re.pinok.media.ClipVideoDownloadManager
import re.pinok.captcha.UiCaptchaHandler
import re.pinok.mods.network.NetworkInterceptors
import re.pinok.realtime.LongPollClient
import re.pinok.realtime.Queuev4Client
import re.pinok.util.AppLog
import re.pinok.util.NetworkObserver
import re.pinok.util.NetworkSwitchState
import re.pinok.util.VkUserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * SOVA 2.0 — Application entry point.
 *
 * Inspired by VK's `com.vkontakte.android.VKApplication` (single global state holder),
 * but with SOVA mod features layered at the Compose level instead of JNI hooks.
 *
 * Responsibilities:
 *  - initialize [EncryptedSharedPreferences] for token storage
 *  - bootstrap [SovaPrefs] (DataStore) for settings
 *  - create [OkHttpClient] + [VKApiClient] singletons
 *  - configure Coil with the same OkHttp client (single connection pool)
 *  - install as [SingletonImageLoader.Factory] for Coil 3
 */
class SovaApp : Application(), SingletonImageLoader.Factory {

    lateinit var prefs: SovaPrefs
        private set

    /**
     * Fix #336: cached snapshot of [prefs] for synchronous O(1) reads from UI
     * (e.g. VideoPlayerScreen needs videoPreferredQuality BEFORE ExoPlayer is
     * created — async produceState caused the player to start at the wrong
     * quality). Updated by [prefs.data].collect below; seeded synchronously from
     * initialSnap in onCreate. Null only during the brief cold-start window
     * before runBlocking { prefs.data.first() } completes (practically never).
     */
    @Volatile
    var prefsSnapshot: SovaPrefs.Snapshot? = null
        private set

    lateinit var tokenStorage: TokenStorage
        private set

    /** Canonical auth storage — access_token + exchange_token + device_id + LP creds. */
    lateinit var exchangeStorage: ExchangeTokenStorage
        private set

    /** Exchange-token auth flow orchestrator (phone+password+2FA, refresh, LongPoll). */
    lateinit var exchangeAuthRepository: ExchangeAuthRepository
        private set

    /**
     * #SESSION-COOKIES-BG-REFRESH: helper для проверки инициализации lateinit
     * [exchangeAuthRepository]. Используется CookieRefreshWorker (WorkManager
     * может стартовать до завершения onCreate).
     */
    fun isExchangeAuthRepositoryInitialized(): Boolean =
        ::exchangeAuthRepository.isInitialized

    lateinit var httpClient: OkHttpClient
        private set

    lateinit var apiClient: VKApiClient
    /** #CALLS: сигналинг звонков через queuev4.vk.ru. */
    lateinit var queuev4Client: Queuev4Client
        private set
    /**
     * #CALLS: вторая queuev4-подписка на events_queue<uid> — сюда приходят полные
     * conversation params входящего звонка (payload LP 115). В messages LongPoll
     * payload = "-1" (без данных). Из localStorage: queue_connection_events_queue<uid>.
     */
    lateinit var eventsQueuev4Client: Queuev4Client
        private set

    /**
     * #CALLS: pending входящий звонок — payload LP 115 + caller.
     * Устанавливается в startCallNotifier при входящем звонке; MainActivity
     * читает и открывает CallScreen(incoming=true, payload). После навигации
     * сбрасывается (consumeIncomingCall).
     */
    @Volatile
    var pendingIncomingCallPayload: String? = null
        private set
    @Volatile
    var pendingIncomingCallPeerId: Long = 0L
        private set
    @Volatile
    var pendingIncomingCallTitle: String = ""
        private set
    @Volatile
    var pendingIncomingCallPhoto: String? = null
        private set

    fun consumeIncomingCall() {
        pendingIncomingCallPayload = null
        pendingIncomingCallPeerId = 0L
        pendingIncomingCallTitle = ""
        pendingIncomingCallPhoto = null
    }

    /**
     * P3.3: FoldersRepository — клиентское хранилище папок диалогов.
     * JSON в SovaPrefs.msgFoldersData (source of truth для UI).
     */
    lateinit var foldersRepository: re.pinok.data.local.FoldersRepository
        private set

    /**
     * Fix #276: PinnedConversationsRepository — локальное хранилище
     * закреплённых диалогов (JSON массив peer_id в SovaPrefs.pinnedConvsData).
     * Source of truth для UI списка диалогов (т.к. VK API
     * messages.markAsImportantConversation недоступен нашему web-token).
     */
    lateinit var pinnedConvsRepository: re.pinok.data.local.PinnedConversationsRepository
        private set

    /**
     * Sprint 1, P0-1: Real-time LongPoll-цикл для сообщений.
     *
     * Запускается из [re.pinok.ui.MainActivity] при `tokenStorage.hasValidToken()`,
     * останавливается при logout. UI подписывается на [LongPollClient.events].
     */
    lateinit var longPollClient: LongPollClient
        private set

    /**
     * §42 #PUSH-NOTIFICATIONS: периодический опрос notifications.getRedesign
     * + показ system notifications для лайков/комментариев/репостов/ответов/
     * подписок/упоминаний/подарков/записей на стене.
     *
     * Запускается из onCreate. Подписан на LongPoll code 114
     * (NotificationsCountChanged) → triggerImmediatePoll().
     *
     * Не делает запросов если pushEnabled=false или нет валидного токена
     * (poller сам проверяет в pollOnce).
     */
    @Volatile
    var notificationsPoller: re.pinok.realtime.NotificationsPoller? = null
        private set

    /**
     * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): poller для подозрительных
     * входов (accountPersonal.getSecurityAlerts). Запускается из onCreate
     * если pushSafetyNetAlerts=true && token валиден.
     */
    @Volatile
    var securityAlertsPoller: re.pinok.realtime.SecurityAlertsPoller? = null
        private set

    /**
     * Sprint 1, P0-3 (#76): обработчик VK Captcha (error 14).
     *
     * UI подписывается на [UiCaptchaHandler.challenge] (StateFlow) и показывает
     * диалог когда challenge != null. VKApiClient.callInternal при error 14
     * вызывает [UiCaptchaHandler.solve] — запрос блокируется пока пользователь
     * не введёт код или отменит.
     */
    lateinit var captchaHandler: UiCaptchaHandler
        private set

    /** Реактивный мониторинг сети — NetworkCallback + StateFlow. */
    lateinit var networkObserver: NetworkObserver
        private set

    /**
     * Fix #50-A: Реактивное уведомление об инвалидации токена.
     *
     * Когда [re.pinok.api.VKApiClient.callInternal] вызывает `tokenStorage.clear()`
     * (refresh failed или повторный error 5), счётчик инкрементируется через
     * [notifyTokenInvalidated]. MainActivity собирает этот Flow и перезапускает
     * AuthActivity — иначе приложение остаётся в MainActivity с пустым токеном,
     * все API вызовы возвращают null → пустая лента → БЕЛЫЙ ЭКРАН.
     *
     * StateFlow выбран вместо MutableIntState, т.к. обновление приходит из
     * фонового потока VKApiClient'а, а Compose подписывается через collectAsState.
     */
    val tokenInvalidationTicks: MutableStateFlow<Int> = MutableStateFlow(0)

    /**
     * Fix #176-auth-loop: throttle для notifyTokenInvalidated.
     *
     * Сценарий из лога 2026-08-04 12:36:43–12:37:10: после первого error 5
     * VKApiClient.clearAccessToken() → notifyTokenInvalidated (tick 1) →
     * AuthActivity SILENT запущена. Дальше LongPollKeepAlive / keepAlive /
     * другие API-вызовы тоже получают error 5 (токен же очищен) → каждый
     * инкрементит tick (2, 3, 4, 5, 6…) каждые ~4 сек. MainActivity фильтрует
     * дубликаты (authActivityShowing guard), но:
     *   - лог засерается tick-штормом
     *   - LongPoll pause 4000ms каждый tick
     *   - silent refresh в фоне крутится без backoff
     *
     * Фикс: не инкрементировать tick если предыдущий был < [NOTIFY_THROTTLE_MS]
     * назад. Первый tick проходит мгновенно (реакция на реальную инвалидацию),
     * последующие подавляются на 15 сек — за это время silent refresh Path 1.5/
     * 2.5/3 либо успеет, либо AuthActivity SILENT завершится и следующий tick
     * запустит FULL режим. Throttle покрывает и ситуацию «токен реально мёртв,
     * refresh не помогает» — вместо 15 ticks/мин получаем 4 ticks/мин, что
     * достаточно для реакции и не засоряет лог.
     */
    @Volatile
    private var lastNotifyTokenInvalidatedMs: Long = 0L

    /** Минимальный интервал между notifyTokenInvalidated tick'ами (мс). См. [notifyTokenInvalidated]. */
    private val NOTIFY_THROTTLE_MS = 15_000L

    /** Увеличивает счётчик инвалидаций токена — триггерит recompose в MainActivity. */
    fun notifyTokenInvalidated() {
        val now = System.currentTimeMillis()
        val sinceLast = now - lastNotifyTokenInvalidatedMs
        if (lastNotifyTokenInvalidatedMs > 0L && sinceLast < NOTIFY_THROTTLE_MS) {
            // Fix #176-auth-loop: throttle — подавляем tick-шторм. Логируем на D
            // чтобы не засорять W-уровень, но оставить след для диагностики.
            AppLog.d("SovaApp", "notifyTokenInvalidated throttled — last tick ${sinceLast}ms ago " +
                "(need ${NOTIFY_THROTTLE_MS - sinceLast}ms more) — tick NOT incremented (Fix #176-auth-loop)")
            return
        }
        lastNotifyTokenInvalidatedMs = now
        val prev = tokenInvalidationTicks.value
        tokenInvalidationTicks.value = prev + 1
        AppLog.w("SovaApp", "notifyTokenInvalidated: tick ${prev + 1} — MainActivity should relaunch AuthActivity")
    }

    // ─── #NET-SWITCH-POPUP (2026-08-03) ───────────────────────────────────
    /**
     * Реактивное состояние переключения сети для popup-overlay.
     *
     * Производители: [NetworkObserver] (физическая смена route / offline) и
     * [re.pinok.api.VKApiClient] (err=5/1117 → silent refresh → success/fail).
     * Потребитель: [re.pinok.ui.components.NetworkSwitchPopup] — собирает через
     * collectAsState, показывает AlertDialog когда state != Idle.
     *
     * Видимость popup дополнительно gated на [SovaPrefs.Snapshot.netSwitchPopupEnabled]
     * (тумблер в Настройки → Интерфейс → «Сеть»). Когда выключен — popup скрыт,
     * но логика переключения/refresh продолжает работать в фоне.
     */
    val networkSwitchState: MutableStateFlow<NetworkSwitchState> = MutableStateFlow(NetworkSwitchState.Idle)

    /**
     * Обновить состояние переключения сети (thread-safe, MutableStateFlow).
     * Логирует переход на INFO (видно в logcat всегда — важные события).
     * Дедуп: если новое состояние равно текущему — no-op (без лога).
     */
    fun setNetworkSwitchState(state: NetworkSwitchState) {
        val prev = networkSwitchState.value
        if (prev == state) return
        networkSwitchState.value = state
        AppLog.i("SovaApp", "networkSwitchState: $prev → $state")
    }

    /**
     * #NET-SWITCH-POPUP: запустить silent refresh токена вручную (кнопка «Повторить»
     * в NetworkSwitchPopup). Вызывает exchangeAuthRepository.ensureFreshToken(force=true)
     * в appScope. Результат обновляет networkSwitchState (Idle при успехе, Failed при неудаче).
     */
    fun retryNetworkSwitchRefresh() {
        // exchangeAuthRepository — lateinit, поэтому проверяем isInitialized.
        // Простой `repo == null` давал warning "Condition is always 'false'",
        // потому что компилятор знает что lateinit-ссылка либо инициализирована,
        // либо кидает UninitializedPropertyAccessException — null там быть не может.
        if (!::exchangeAuthRepository.isInitialized) {
            AppLog.w("SovaApp", "retryNetworkSwitchRefresh: exchangeAuthRepository not initialized")
            setNetworkSwitchState(NetworkSwitchState.Failed("auth repo not ready", canRetry = false))
            return
        }
        val repo = exchangeAuthRepository
        setNetworkSwitchState(NetworkSwitchState.Refreshing(attempt = 1))
        appScope.launch {
            try {
                val token = repo.ensureFreshToken(force = true)
                if (token != null) {
                    AppLog.i("SovaApp", "retryNetworkSwitchRefresh: OK — token refreshed")
                    setNetworkSwitchState(NetworkSwitchState.Idle)
                } else {
                    val canRetry = try { repo.hasSilentReloginMeans() } catch (_: Exception) { false }
                    AppLog.w("SovaApp", "retryNetworkSwitchRefresh: no token (canRetry=$canRetry)")
                    setNetworkSwitchState(NetworkSwitchState.Failed("silent refresh вернул null", canRetry = canRetry))
                }
            } catch (e: Exception) {
                AppLog.e("SovaApp", "retryNetworkSwitchRefresh failed", e)
                setNetworkSwitchState(NetworkSwitchState.Failed(e.message ?: "exception", canRetry = true))
            }
        }
    }

    /**
     * #SESSION-COOKIES-BG-REFRESH: регистрирует фоновые триггеры sync'а session
     * cookies (remixsid + p + remixnsid) из CookieManager → storage.
     *
     * Вызывается из [onCreate] после инициализации exchangeAuthRepository.
     *
     * Триггеры:
     *   - Hook #2: [ProcessLifecycleOwner] ON_RESUME — app выходит на foreground.
     *     Debounce 30с (вместо sync на каждый onResume — activity transitions
     *     могут генерировать множественные ON_RESUME за секунду).
     *   - Hook #3: [CookieRefreshWorker] periodic 6ч через WorkManager —
     *     ловит ротэйты пока app в фоне (LongPoll/push держат session живой).
     *
     * Hook #1 (после успешного silentRefreshViaRemixsid) — внутри ExchangeAuthRepository,
     * здесь не регистрируется.
     *
     * Sync best-effort: ошибки НЕ роняют app. [ExchangeAuthRepository.refreshSessionCookiesFromCookieManager]
     * использует patch-семантику (null = не трогать) — сохраняет только изменившиеся
     * cookies. Нет access_token → no-op (пользователь не залогинен).
     */
    private fun setupCookieBackgroundRefresh() {
        try {
            // Hook #3: periodic WorkManager (каждые 6ч, network=CONNECTED).
            // KEEP policy — не перезаписывает существующий график при re-onCreate.
            CookieRefreshWorker.schedule(this)

            // Hook #2: ProcessLifecycleOwner — ON_RESUME sync.
            // lifecycleScope привязан к process (не к конкретной activity) —
            // корутину отменяет только полный kill процесса.
            val app = this
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : androidx.lifecycle.DefaultLifecycleObserver {
                    private var lastForegroundSyncMs = 0L
                    private val foregroundSyncMutex = kotlinx.coroutines.sync.Mutex()

                    override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                        val now = System.currentTimeMillis()
                        // Debounce 30с: быстрые activity transitions не должны
                        // вызывать множественные CookieManager reads.
                        if (now - lastForegroundSyncMs < 30_000L) {
                            AppLog.d("SovaApp", "cookieSync: foreground sync debounced " +
                                "(${(now - lastForegroundSyncMs) / 1000}s ago) — skip")
                            return
                        }
                        lastForegroundSyncMs = now
                        // appScope = Dispatchers.IO + SupervisorJob() — живёт пока
                        // процесс жив, ошибки в одном launch не роняют остальные.
                        app.appScope.launch {
                            // Mutex предотвращает параллельные sync'и если несколько
                            // ON_RESUME пришли почти одновременно (race).
                            if (!foregroundSyncMutex.tryLock()) {
                                AppLog.d("SovaApp", "cookieSync: foreground sync already in progress — skip")
                                return@launch
                            }
                            try {
                                if (!app.isExchangeAuthRepositoryInitialized()) {
                                    AppLog.d("SovaApp", "cookieSync: repo not initialized — skip")
                                    return@launch
                                }
                                val repo = app.exchangeAuthRepository
                                if (!repo.hasValidAccessToken()) {
                                    AppLog.d("SovaApp", "cookieSync: no valid access_token — skip foreground sync")
                                    return@launch
                                }
                                val result = repo.refreshSessionCookiesFromCookieManager()
                                if (result.anyChanged) {
                                    AppLog.i("SovaApp", "cookieSync: foreground sync UPDATED cookies — " +
                                        "remixsid=${if (result.remixsidChanged) "rotated" else "same"}, " +
                                        "p=${if (result.pChanged) "rotated" else "same"}, " +
                                        "remixnsid=${if (result.remixnsidChanged) "rotated" else "same"}")
                                } else {
                                    AppLog.d("SovaApp", "cookieSync: foreground sync — no changes (all 3 match CookieManager)")
                                }
                            } catch (e: Exception) {
                                AppLog.w("SovaApp", "cookieSync: foreground sync failed: ${e.message}")
                            } finally {
                                foregroundSyncMutex.unlock()
                            }
                        }
                    }
                }
            )
            AppLog.i("SovaApp", "setupCookieBackgroundRefresh: hooks registered " +
                "(ProcessLifecycleOwner ON_RESUME + WorkManager periodic 6h)")
        } catch (e: Exception) {
            AppLog.w("SovaApp", "setupCookieBackgroundRefresh failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Fix #267 (Plan §36.12 P1-CHAT-5): Feature flags от VK (account.getTogglesExternal).
     *
     * In-memory кэш фича-флагов вида "vkm_convo_owner_right_transfer" → enabled.
     * Загружается при логине через [loadFeatureFlags]. UI использует для ACL-gating
     * (например, «Передать права создателя» показывается только если
     * featureFlags["vkm_convo_owner_right_transfer"] == true).
     *
     * StateFlow<Map> — immutable, при update создаётся новая map → Compose
     * собирает через collectAsState и автоматически рекомпосит.
     *
     * Null-значения трактовуются как false (флаг выключен/не загружен).
     * Пустая map (default) = флаги не загружены — UI показывает пункты
     * по умолчанию (canChangeOwner из ACL решает).
     */
    val featureFlags: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    /**
     * Fix #176-auth-loop: форсировать FULL re-login при следующем запуске AuthActivity.
     *
     * Сценарий: пользователь обновил приложение поверх старого → access_token в
     * бэкапе протух, exchange_token отсутствует (старая версия не сохранила),
     * remixsid есть но VK сменил контракт web_token endpoint → SILENT AuthActivity
     * бесконечно loop'ится (transparent WebView, пользователь не видит экран входа).
     *
     * Флаг выставляется в [onCreate] при детекте смены versionCode + протухшего
     * токена/отсутствующего exchange_token. Читается в MainActivity.LaunchedEffect
     * (boot + tokenInvalidationTick) — если true, silentFailCount приравнивается
     * к MAX_SILENT_FAILURES → useSilent=false → AuthActivity в FULL режиме →
     * юзер видит нормальный экран входа.
     *
     * Сбрасывается в false после успешного логина (saveAuthResult).
     */
    @Volatile
    var forceFullReloginOnNextLaunch: Boolean = false

    /**
     * Загружает feature flags через account.getTogglesExternal и кэширует в
     * [featureFlags]. Вызывается из onCreate после успешного логина.
     * Errors логируются но НЕ пробрасываются — UI работает с empty map.
     */
    suspend fun loadFeatureFlags() {
        try {
            val flags = apiClient.accountGetTogglesExternal()
            if (flags.isNotEmpty()) {
                featureFlags.value = flags
                AppLog.i("SovaApp", "Loaded ${flags.size} feature flags")
            }
        } catch (e: Exception) {
            AppLog.w("SovaApp", "loadFeatureFlags failed: ${e.message}")
        }
    }

    /**
     * Fix #137: When true, the next tokenInvalidation tick will NOT trigger
     * AuthActivity relaunch in MainActivity. Used by ChatDetailScreen to
     * handle photo-upload auth failures inline (showing a banner instead of
     * hiding the chat behind AuthActivity).
     *
     * Set to true BEFORE calling uploadAndSendPhoto, reset to false in finally.
     * If token was invalidated during upload, ChatDetailScreen detects the
     * tick change and shows an inline "session expired" dialog.
     *
     * Single-shot: MainActivity consumes it (sets to false) when it fires.
     * Race-condition-prone (see worklog Fix #137) but acceptable for the
     * photo-upload use case — concurrent uploads are unlikely in UI.
     */
    @Volatile
    var suppressNextAuthRelaunch: Boolean = false

    /**
     * Fix #217 (P1.2): Time-based suppress AuthActivity relaunch.
     *
     * Fix #137 (single-shot suppressNextAuthRelaunch) работает только для
     * photo upload и только для ОДНОГО tick. Если error 5/1117 приходит
     * от LongPoll или другого API-вызова — AuthActivity запускается сразу,
     * перекрывая чат.
     *
     * Time-based подход: suppressAuthRelaunchUntilMs = System.currentTimeMillis()
     * + windowMs. Любой tokenInvalidation tick в этом окне НЕ запускает
     * AuthActivity — вместо этого ChatDetailScreen (или другой экран)
     * показывает inline toast "Сессия истекла, обновляем...".
     *
     * Это покрывает все API-вызовы на 30-60 секунд (хватит чтобы пользователь
     * закончил действие, а silent refresh Path 1.5/2.5 восстановил токен).
     *
     * 0 = suppress отключён (по умолчанию). MainActivity проверяет:
     *   if (now < suppressAuthRelaunchUntilMs) { skip AuthActivity; show inline }
     *
     * Сценарий: пользователь отправляет фото → error 5 → silent refresh
     * запустился (Path 1.5) → параллельно LongPoll тоже получил error 5 →
     * без suppress, LongPoll tick запустил бы AuthActivity поверх чата.
     * С suppress: LongPoll tick игнорируется, silent refresh завершается,
     * пользователь видит чат без overlay.
     */
    @Volatile
    var suppressAuthRelaunchUntilMs: Long = 0L

    /**
     * Fix #217 (P1.2): Установить окно suppress AuthActivity relaunch.
     *
     * @param windowMs длительность окна в миллисекундах (по умолчанию 30 сек).
     * Любой tokenInvalidation tick в этом окне НЕ запускает AuthActivity.
     *
     * Используется ChatDetailScreen перед потенциально долгой операцией
     * (отправка фото, голосового) чтобы silent refresh Path 1.5/2.5 успел
     * завершиться в фоне без перекрытия чата AuthActivity overlay.
     *
     * Безопасно: окно авто-протухает по времени. Если silent refresh
     * не успел за 30 сек — следующий tick запустит AuthActivity нормально.
     */
    fun suppressAuthRelaunchFor(windowMs: Long = 30_000L) {
        suppressAuthRelaunchUntilMs = System.currentTimeMillis() + windowMs
        AppLog.i("SovaApp", "suppressAuthRelaunchFor(${windowMs}ms) — " +
            "AuthActivity relaunch suppressed until ${suppressAuthRelaunchUntilMs}")
    }

    /**
     * Fix #217 (P1.2): Сбросить окно suppress (например после успешного
     * silent refresh — больше не нужно подавлять).
     */
    fun clearSuppressAuthRelaunch() {
        if (suppressAuthRelaunchUntilMs != 0L) {
            suppressAuthRelaunchUntilMs = 0L
            AppLog.d("SovaApp", "clearSuppressAuthRelaunch — suppress window cleared")
        }
    }

    /**
     * Fix #112: проактивная проверка валидности токена при возврате из фона.
     *
     * `hasValidAccessToken()` проверяет только локальное `expires_at` — если токен
     * истёк по времени (не сервер-сайд), VK ещё не знает об этом (не было API
     * вызова). Без этой проверки мы ждём, пока LongPoll сделает следующий запрос
     * к `messagesGetLongPollServer()` → VK вернёт error 5/1117 → callInternal
     * вызовет `notifyTokenInvalidated()`. Но LongPoll может быть в 45-секундном
     * `doRequest` (Doze заблокировал сеть) → задержка до 45с.
     *
     * Вызов из `MainActivity.onResume()` немедленно проверяет истёк ли токен по
     * времени и если да — инкрементирует tick → boot LaunchedEffect (с Fix #112
     * guard) запускает AuthActivity для silent re-login через remixsid.
     *
     * Безопасен для `expires_at == 0` (offline scope, без истечения) — в этом
     * случае hasValidToken()=true и notifyTokenInvalidated НЕ вызывается.
     */
    fun checkTokenValidity() {
        if (!tokenStorage.hasValidToken()) {
            AppLog.w("SovaApp", "checkTokenValidity: token locally expired — notifying MainActivity")
            notifyTokenInvalidated()
        }
    }

    // S7-4: Session keep-alive scope — runs while the app process is alive.
    private val keepAliveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Процесс-живущий coroutine scope (SupervisorJob + IO dispatcher).
     *
     * Используется для запуска долгих upload-операций (голосовые сообщения,
     * фото, файлы), которые НЕ должны отменяться, когда пользователь уходит
     * с экрана чата — иначе `rememberCoroutineScope()` отменяет загрузку
     * (`LeftCompositionCancellationException`) и вложение теряется на полпути.
     *
     * UI-обновления (reloadMessages, scroll) всё равно делаются через
     * композиционный [androidx.compose.runtime.rememberCoroutineScope].
     */
    val appScope: CoroutineScope get() = keepAliveScope

    /**
     * Fix #340: счётчик активных (started) Activity. > 0 значит хотя бы одна
     * Activity на переднем плане. Используется [LongPollKeepAliveService] для
     * решения: делать headless silent re-login (нет UI) или отдать обработку
     * MainActivity (UI виден).
     */
    @Volatile
    private var startedActivities: Int = 0

    /** true если хотя бы одна Activity в started state (видна пользователю). */
    fun isAnyActivityForeground(): Boolean = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.i("SovaApp", "onCreate: PinoK starting (version=${BuildConfig.VERSION_NAME})")

        // 0. Инициализация файлового лога (персистентный, с rotation)
        AppLog.init(this)

        // §42.12 P0 #2: проверка доступности Siren-декодера в ffmpeg-kit.
        // Логируем один раз при старте — если декодер недоступен, siren-треки
        // будут кэшироваться как .ts (codec=siren, Wi-Fi бейдж, онлайн-only).
        // Проверка идёт на Dispatchers.IO, не блокирует UI.
        appScope.launch {
            val available = try {
                re.pinok.media.SirenTranscoder.checkSirenDecoderAvailable()
            } catch (e: Throwable) {
                AppLog.e("SovaApp", "SirenTranscoder.checkSirenDecoderAvailable failed: ${e.message}")
                false
            }
            AppLog.i("SovaApp", "Siren decoder available=$available " +
                "(if false, siren tracks cache as .ts with Wi-Fi badge)")
        }

        // Fix #340: трекинг foreground-состояния через ActivityLifecycleCallbacks.
        //_startedActivities инкрементится в onActivityStarted, декрементится в
        // onActivityStopped. isAnyActivityForeground() использует сервис для
        // решения: headless silent re-login (нет UI) или отдать обработку MainActivity.
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                AppLog.d("SovaApp", "Activity started (${activity.javaClass.simpleName}), foreground count=$startedActivities")
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                AppLog.d("SovaApp", "Activity stopped (${activity.javaClass.simpleName}), foreground count=$startedActivities")
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        // 1. Encrypted shared prefs for token + master key
        @Suppress("DEPRECATION")
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        @Suppress("DEPRECATION")
        val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
            this,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        // Order matters: ExchangeTokenStorage wraps securePrefs directly,
        // then TokenStorage wraps ExchangeTokenStorage as a legacy facade.
        //
        // VTosters pattern #3: файловый бэкап account.json как fallback
        // при повреждении EncryptedSharedPreferences (KeyStore corruption,
        // Tink key rotation failure, factory reset). ExchangeTokenStorage
        // дампит все поля в JSON после каждого write, а при старте, если
        // prefs пусты, пытается восстановиться из файла.
        val accountFileBackup = AccountFileBackup(this)
        exchangeStorage = ExchangeTokenStorage(securePrefs, accountFileBackup)

        // Восстановление из файлового бэкапа: если prefs пусты/повреждены,
        // но account.json существует — заливаем токены обратно ДО того, как
        // любой другой код попытается их читать. Это спасает от полной
        // потери сессии при Keystore corruption на Samsung/Xiaomi.
        //
        // Fix #177+#178 #FORCE-FULL-ON-FAILED-RESTORE (2026-08-04, лог 17:33:09):
        // Раньше restoreFromFileBackup FAILED молча логировался, но forceFull flag
        // НЕ выставлялся (он триггерился только при смене versionCode). В debug-
        // сборках versionCode=1 всегда → "App version unchanged" → миграция НЕ
        // запускалась → forceFull=false → AuthActivity SILENT loop 60 сек, потом
        // fallback to LANDING. Юзер ждал минуту чтобы увидеть экран входа.
        //
        // Теперь: если securePrefs пусты И restoreFromFileBackup не восстановил
        // access_token (restored=false) — выставляем forceFullReloginOnNextLaunch
        // НЕЗАВИСИМО от versionCode. Условие versionChanged осталось для случая
        // когда prefs не пусты (миграция поверх живой сессии), а здесь — для
        // случая когда prefs пусты (чистый старт / Keystore corruption).
        var backupRestoreFailed = false
        if (exchangeStorage.accessToken() == null && accountFileBackup.exists()) {
            AppLog.w("SovaApp", "securePrefs пусты, но найден account.json — " +
                "попытка восстановления сессии из файлового бэкапа")
            val restored = exchangeStorage.restoreFromFileBackup()
            AppLog.i("SovaApp", "restoreFromFileBackup: ${if (restored) "OK" else "FAILED"}")
            // restored=false значит access_token не восстановлен (либо отсутствует
            // в бэкапе, либо протух). Все re-login credentials уже залиты (Fix A),
            // но silentRefresh всё равно скорее всего упадёт (VK web_token contract
            // change), поэтому форсируем FULL mode чтобы юзер сразу увидел экран
            // входа вместо 60-сек SILENT loop с последующим fallback.
            if (!restored) {
                backupRestoreFailed = true
            }
        }

        // Fix #176-auth-loop: миграция при апдейте приложения (установка поверх старого).
        // Сценарий из лога 2026-08-04: пользователь установил новый APK поверх старого
        // → access_token в бэкапе протух (expires_at = now-12s на момент старта),
        // exchange_token отсутствует (старая версия не сохраняла или сохранила под
        // другим ключом), remixsid присутствует но VK сменил контракт web_token
        // endpoint → все silent paths фейлятся → AuthActivity SILENT loop по tick.
        //
        // Фикс: при детекте смены versionCode — если access_token протух ИЛИ отсутствует
        // exchange_token — помечаем needFullRelogin. Это не чистит remixsid (он может
        // быть ещё валиден для WebView silent re-auth), но гарантирует что MainActivity
        // запустит AuthActivity в FULL режиме (а не SILENT), и юзер увидит экран входа
        // вместо бесконечного прозрачного WebView loop'а.
        //
        // Флаг читается в MainActivity.LaunchedEffect(tokenInvalidationTick) и в boot
        // LaunchedEffect — если true, silentFailCount устанавливается в MAX_SILENT_FAILURES
        // чтобы useSilent=false → FULL режим.
        runCatching {
            val pm = packageManager
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val currentVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            val metaPrefs = getSharedPreferences("app_meta", Context.MODE_PRIVATE)
            val storedVersion = metaPrefs.getLong("last_version_code", 0L)
            if (storedVersion != 0L && storedVersion != currentVersion) {
                AppLog.i("SovaApp", "App upgraded: $storedVersion → $currentVersion — checking token state for auth-loop prevention (Fix #176-auth-loop)")
                val tokenExpired = !exchangeStorage.hasValidAccessToken()
                val noExchangeToken = exchangeStorage.exchangeToken().isNullOrBlank()
                if (tokenExpired || noExchangeToken) {
                    AppLog.w("SovaApp", "App upgraded AND (token expired=$tokenExpired OR no exchange_token=$noExchangeToken) — forcing FULL re-login on next AuthActivity launch (Fix #176-auth-loop)")
                    forceFullReloginOnNextLaunch = true
                }
            } else if (storedVersion == 0L) {
                AppLog.i("SovaApp", "First install (version=$currentVersion) — no migration needed")
            } else {
                AppLog.d("SovaApp", "App version unchanged ($currentVersion) — no migration needed")
            }
            // Fix #177+#178 #FORCE-FULL-ON-FAILED-RESTORE: независимая от versionCode
            // проверка. Срабатывает когда securePrefs пусты + restoreFromFileBackup
            // не восстановил access_token (account.json без токена или с протухшим).
            // В debug-сборках versionCode=1 → ветка versionChanged никогда не идёт,
            // без этого блока юзер ждёт 60 сек SILENT loop перед экраном входа.
            if (backupRestoreFailed) {
                AppLog.w("SovaApp", "backupRestoreFailed=true — forcing FULL re-login on next AuthActivity launch (Fix #177+#178, versionCode-independent)")
                forceFullReloginOnNextLaunch = true
            }
            metaPrefs.edit().putLong("last_version_code", currentVersion).apply()
        }.onFailure { e ->
            AppLog.w("SovaApp", "Version migration check failed: ${e.message} — non-fatal, continuing")
        }

        tokenStorage = TokenStorage(exchangeStorage)
        prefs = SovaPrefs(this)

        // #LOG-CATEGORIES (2026-08-04): загрузка отключенных категорий логов
        // из DataStore в AppLog. Делаем ОДИН синхронный read в runBlocking на
        // старте — до того, как любой другой код начнёт логировать. После этого
        // все логи фильтруются по категориям автоматически (AppLog.log проверяет
        // enabledCategories перед записью в buffer/file/logcat).
        //
        // UI (SettingsScreen LoggingTab) обновляет множество через
        // prefs.setLogCategoriesDisabled() + AppLog.setCategoryEnabled() —
        // изменения применяются мгновенно (без перезапуска приложения).
        runBlocking {
            runCatching {
                val disabled = prefs.data.first().logCategoriesDisabled
                AppLog.applyDisabledCategories(disabled)
            }.onFailure { e ->
                android.util.Log.w("PinoK/SovaApp",
                    "loadLogCategories failed: ${e.message} — default (critical only: AUTH+SYSTEM+NETWORK) used")
            }
        }

        // 1b. Прогрев CookieManager для обнаружения сессии VK из внешнего браузера.
        //     CookieManager лениво загружает cookies из хранилища — без прогрева
        //     первый getCookie() в ExternalBrowserAuth.tryFindExistingAuth()
        //     может вернуть null даже если Chrome авторизован в VK.
        ExternalBrowserAuth.warmUpCookieManager()

        // 2. HTTP client — VK Android User-Agent (dynamic, SOVA RE format).
        //    VK API отбрасывает не-официальные клиенты по UA (error 15 на messages.*).
        //    Хардкод заменён на VkUserAgent.get() — формат идентичен VK.app.
        //
        //    #44: Interceptor уважает UA, уже заданный запросом — WebTokenAuth
        //    стучится на login.vk.com (WEB-flow endpoint) с Chrome UA, и этот UA
        //    НЕ должен перетираться VKAndroidApp/... иначе VK отдаёт 401 invalid_request
        //    (layer: LayerAnonymTokenHandler). Аналогично X-VK-Android-Client ставится
        //    только для api.vk.com — web-эндпоинты его не ждут.
        //
        //    #29 (закрытие хвостов): SSL pinning, AdBlock, away.php bypass —
        //    читаем prefs ОДИН раз синхронно через runBlocking для initial config,
        //    interceptors будут перечитывать prefs на каждый запрос.
        val ua = VkUserAgent.get(this)
        AppLog.i("SovaApp", "User-Agent: $ua")
        // #BOTTOM-DEFAULT-4: миграция дефолта нижней панели (5→4 кнопки).
        // Применяется только если пользователь не настраивал панель сам.
        runBlocking {
            val migrated = prefs.migratePanelDefaultsV2()
            if (migrated) AppLog.i("SovaApp", "Panel defaults v2 migration: applied (4-button bottom bar)")
            // §42.6 #PUSH-NO-GROUP-DEFAULT: сброс pushGroupingMode "category"→"none".
            // Старый default сворачивал пуш-группы — пользователь не видел отдельные
            // посты без pinch-out. Новый default = каждое уведомление отдельно.
            val pushMigrated = prefs.migratePushGroupingDefault()
            if (pushMigrated) AppLog.i("SovaApp", "Push grouping migration: reset 'category'→'none' (individual notifications)")
        }
        val initialSnap = runBlocking { prefs.data.first() }
        prefsSnapshot = initialSnap   // Fix #336: seed synchronous cache
        // Fix #189: инициализируем AuthDomainsConfig из prefs ДО того, как
        // любой auth flow (AuthActivity, ExternalBrowserLauncher, WebTokenAuth)
        // попытается прочитать домены. initialSnap уже загружен синхронно выше.
        re.pinok.auth.exchange.AuthDomainsConfig.update(initialSnap)
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)   // LongPoll wait=25 + запас на latency/Doze
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)   // TLS keep-alive ping каждые 15с — предотвращает NAT timeout
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            // #CALLS-ANTIFRAUD (2026-08-23): OkHttp CookieJar — подставляет полный
            // браузерный cookie-set VK (remixsid/remixnsid/remixstid/remixstlid/httoken…)
            // в исходящие запросы. Без него get_anonym_token / auth.anonymLogin /
            // login.vk.ru web_token отклоняются по антифроду (401 AUTH_LOGIN).
            // exchangeStorage создан выше (строка ~662) — доступен здесь.
            .cookieJar(re.pinok.mods.network.VkCookieJar(exchangeStorage))
            // #CALLS: OkHttp/Java InetAddress не резолвит calls.okcdn.ru (IPv6-проблема),
            // хотя системный WebView резолвит. Для okcdn-хостов используем захардкоженный
            // IPv4 (из ping: 155.212.204.12). Для остальных — стандартный Dns.SYSTEM.
            .dns(object : okhttp3.Dns {
                private val pinned = mapOf(
                    "calls.okcdn.ru" to "155.212.204.12",
                    "calls-test.okcdn.ru" to "155.212.204.12",
                    "api.mycdn.me" to "155.212.204.12",
                )
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val ip = pinned[hostname.lowercase()]
                    if (ip != null) {
                        return listOf(java.net.InetAddress.getByName(ip))
                    }
                    return okhttp3.Dns.SYSTEM.lookup(hostname)
                }
            })


        // SSL Pinning — применяется на старте. Для смены требуется restart приложения
        // (OkHttp CertificatePinner immutable после build).
        if (initialSnap.netSslPinning) {
            val pinnerBuilder = CertificatePinner.Builder()
            listOf(
                "api.vk.com", "oauth.vk.com", "id.vk.com",
                "login.vk.com", "m.vk.com", "vk.com", "vk.ru",
                // Task #Web-API: mobile-web API gateway (m.vk.ru SPA uses this).
                // See VKEndpoints.WEB_API_HOSTNAME. Activated by netUseWebApiGateway.
                re.pinok.api.VKEndpoints.WEB_API_HOSTNAME,
                "psv4.vkuseraudio.net", "psv4.userapi.com",
            ).forEach { host ->
                NetworkInterceptors.SslPins.forHost(host).forEach { pin ->
                    pinnerBuilder.add(host, pin)
                }
            }
            builder.certificatePinner(pinnerBuilder.build())
            AppLog.i("SovaApp", "SSL Pinning ENABLED — ${10} hosts pinned")
        } else {
            AppLog.i("SovaApp", "SSL Pinning disabled (netSslPinning=false)")
        }

        httpClient = builder
            .addInterceptor { chain ->
                val original = chain.request()
                val builder2 = original.newBuilder()
                    .header("Accept-Language", "ru")
                // VK Android UA — только если запрос явно не задал свой
                // (WebTokenAuth задаёт Chrome UA для login.vk.com web-flow).
                if (original.header("User-Agent") == null) {
                    builder2.header("User-Agent", ua)
                }
                // #44: X-VK-Android-Client — для VK Android API/auth endpoints
                // (api.vk.com, oauth.vk.com, id.vk.com). На web-flow endpoints
                // (login.vk.com, m.vk.com) этот header выдаёт non-browser клиент
                // и ломает get_anonym_token (401 invalid_request, LayerAnonymTokenHandler).
                //
                // Task #Web-API: web.api.vk.ru — это мобильный WEB-шлюз m.vk.ru.
                // X-VK-Android-Client там тоже не нужен (VK может классифицировать
                // запрос как non-browser и отказать). Header опускается автоматически.
                val host = original.url.host
                val isWebFlowHost = host == "login.vk.com" ||
                    host == "m.vk.com" ||
                    host == "new.vk.com" ||
                    host == re.pinok.api.VKEndpoints.WEB_API_HOSTNAME
                if (!isWebFlowHost &&
                    original.header("X-VK-Android-Client") == null
                ) {
                    builder2.header("X-VK-Android-Client", "new")
                }
                chain.proceed(builder2.build())
            }
            // #29: 3 сетевые настройки теперь реально работают (раньше были UI-only).
            .addInterceptor(NetworkInterceptors.AwayBypassInterceptor(prefs))
            .addInterceptor(NetworkInterceptors.AdBlockInterceptor(prefs))
            // Fix #176: StaleConnectionInterceptor — добавляет Connection: close
            // в течение 10 сек после switch'а сети. Должен быть ДО NetworkRetry
            // чтобы retry'и тоже шли с Connection: close (не переиспользовали
            // stale connections из pool'а).
            .addInterceptor(NetworkInterceptors.StaleConnectionInterceptor())
            // Fix #45: retry на IOException при переключении WiFi↔Mobile.
            // Должен быть ПОСЛЕДНИМ application interceptor — работает с финальным
            // request после away-bypass/ad-block преобразований.
            .addInterceptor(NetworkInterceptors.NetworkRetryInterceptor())
            .build()

        // 3. Exchange-token auth repository — phone+password+2FA flow, refresh, LongPoll.
        //    Initialized BEFORE VKApiClient so the API client can refresh tokens on error 5.
        exchangeAuthRepository = ExchangeAuthRepository(
            api = ExchangeAuthApi(httpClient = httpClient),
            storage = exchangeStorage,
            httpClient = httpClient,  // Web token refresh через remixsid (ensureFreshToken)
            prefs = prefs,  // Fix #211: сброс privacyOfflineMode при успешной авторизации
        )

        // 3b. VK API client (offline-aware) — wires to the auth repository for auto-refresh.
        //    NetworkObserver initialized first so VKApiClient can reuse it (C13).
        networkObserver = NetworkObserver(this)
        networkObserver.register()

        // #NETWORK-RESILIENCE (2026-08-04): подключаем NetworkObserver к auth-repo
        // для offline-first входа (AuthState.OfflineWithCache) и offline-guard в
        // ensureFreshToken — не тратим retry-попытки когда сеть точно недоступна.
        exchangeAuthRepository.attachNetworkObserver(networkObserver)

        // #SESSION-COOKIES-BG-REFRESH (Hook #2 + Hook #3): фоновый sync session
        // cookies (remixsid + p + remixnsid) из CookieManager → storage.
        //
        // Проблема: backfillRemixsidFromCookieManager вызывается ТОЛЬКО в момент
        // логина. После логина VK ротейтит cookies (security events, web-навигация),
        // CookieManager обновляется, storage — нет. Через дни/недели storage содержит
        // стейловые cookies → при смене сети silentRefreshViaRemixsid шлёт устаревший
        // Cookie header → VK отбрасывает → полный re-login.
        //
        // Два триггера sync'а:
        //   Hook #2 — ProcessLifecycleOwner ON_RESUME: ловит ротэйты пока пользователь
        //            пользовался app (m.vk.ru WebView, stories browser обновляют
        //            CookieManager). Debounce 30с чтобы не дёргать на каждом onResume.
        //   Hook #3 — WorkManager periodic 6ч: ловит ротэйты пока app в фоне
        //            (push notifications, LongPoll держат session живой).
        // Hook #1 (после успешного silentRefreshViaRemixsid) — внутри ExchangeAuthRepository.
        setupCookieBackgroundRefresh()

        apiClient = VKApiClient(
            context = this,
            httpClient = httpClient,
            tokenStorage = tokenStorage,
            prefs = prefs,
            exchangeAuthRepository = exchangeAuthRepository,
            networkObserver = networkObserver,
        )
        // P3.3: FoldersRepository — клиентские папки диалогов (JSON в SovaPrefs).
        foldersRepository = re.pinok.data.local.FoldersRepository(prefs)
        // Fix #276: PinnedConversationsRepository — локальные закреплённые диалоги.
        pinnedConvsRepository = re.pinok.data.local.PinnedConversationsRepository(prefs)

        // #CALLS: Queuev4Client — сигналинг звонков через queuev4.vk.ru.
        queuev4Client = Queuev4Client(httpClient = httpClient, apiClient = apiClient)
        // #CALLS: вторая подписка на events_queue<uid> — полные conversation params.
        eventsQueuev4Client = Queuev4Client(httpClient = httpClient, apiClient = apiClient)

        // 4. Media3 — downloaders + PlayerService.
        //    #39 C7: AudioStreamCache (zombie) удалён — стриминг-кэш не использовался
        //    (Fix #76 убрал CacheDataSource из-за короткоживущих AES-128 ключей HLS).
        //    Реальный офлайн-кэш = полные .ts/.mp4 файлы через TrackDownloadManager +
        //    VideoDownloadManager (auto-download при первом прослушивании/просмотре).
        TrackDownloadManager.init(this)
        VideoDownloadManager.init(this)
        // Fix #100: офлайн-кэш для ВИДЕО-историй (stories). Отдельный менеджер —
        // см. STORY_VIDEO_CACHE_PLAN.md. Init после VideoDownloadManager, т.к.
        // использует тот же VkUserAgent/OkHttp pattern.
        StoryVideoDownloadManager.init(this)
        // §37.12 #329: офлайн-кэш для VK Clips (коротких вертикальных видео).
        // Смоделирован по StoryVideoDownloadManager, но использует Video model
        // (Long videoId), TTL 7 дней, URL-refresh через videoGetClipById.
        // Init после StoryVideoDownloadManager — тот же pattern.
        ClipVideoDownloadManager.init(this)
        // #39 C2: persistent playback position (audio + video) — до PlayerConnection,
        // т.к. PlayerConnection.init использует его для restore/seek при playTrackList.
        re.pinok.media.PlaybackPositionStore.init(this)
        // #AUTH-WEBVIEW-STARVATION-V2: НЕ запускаем PlayerConnection в auth flow.
        // PlayerConnection.init() → connectController() → IPC bind к PlayerService →
        // PlayerService.onCreate() блокирует main thread на ~150-300мс (ExoPlayer +
        // MediaSession + AudioEffects). В это время Chromium в VkAuthWebViewScreenV2
        // пытается поднять рендерер → IPC handshake cr_ChildProcessConn таймаутится →
        // onPageStarted NEVER fires → m.vk.ru не грузится (белый экран).
        //
        // Fix #AUTH-WEBVIEW-STARVATION (MainActivity.kt:1272) skip'ит только
        // notifyResumed, но НЕ init. init вызывается ЗДЕСЬ ВСЕГДА — даже в auth flow.
        // Этот guard закрывает дыру: если токена нет (auth flow), PlayerService НЕ
        // запускается, main thread свободен для Chromium.
        //
        // После успешного auth (token сохранён) — MainActivity.onResume вызовет
        // PlayerConnection.init(this) через notifyResumed→init path (init идемпотентен,
        // повторный вызов безопасен). См. PlayerConnection.init: `if (initialized) return`.
        if (tokenStorage.hasValidToken()) {
            PlayerConnection.init(this)
        } else {
            AppLog.i("SovaApp", "skip PlayerConnection.init — no token (auth flow), #AUTH-WEBVIEW-STARVATION-V2")
        }
        // Этап 4 EQUALIZER_INTEGRATION_PLAN.md: загрузка кастомных пресетов
        // эквалайзера из JSON-файла (filesDir/equalizer/custom_presets.json).
        // Singleton store, lazy-load через ensureLoaded(), но вызываем явно в
        // onCreate чтобы первый open EqualizerScreen не тормозил на чтение файла.
        re.pinok.media.CustomPresetStore.load()

        // 4b. Async: применим пользовательский путь загрузки музыки из prefs.
        //     DataStore загружается асинхронно — не можем ждать в onCreate().
        //     #39 C7: cacheSizeMb/cacheCustomPath больше не реконфигурируют кэш
        //     (AudioStreamCache удалён). Prefs остаются для обратной совместимости,
        //     но не используются. Путь загрузки музыки (musicDownloadPath) пока тоже
        //     не применяется — TrackDownloadManager пишет в filesDir/downloads/music.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snap = prefs.data.first()
                AppLog.i("SovaApp", "Prefs loaded: musicPath=${snap.musicDownloadPath}, adBlock=${snap.netAdBlock}")
                // Fix #100: применяем лимит кэша stories из prefs (default 200 МБ).
                // evictExpired уже отработал в init, здесь — LRU по размеру.
                StoryVideoDownloadManager.enforceCacheLimit(snap.storyCacheLimitMb)
                // §37.12 #329: применяем лимит кэша clips из prefs. Пока отдельного
                // prefs-ключа clipCacheLimitMb нет — переиспользуем storyCacheLimitMb
                // (RESEARCH-1 рекомендует отдельный pref=300 МБ в будущей итерации).
                ClipVideoDownloadManager.enforceCacheLimit(snap.storyCacheLimitMb)
                // Fix #110: применяем autoCacheAudio к PlayerConnection (initial).
                PlayerConnection.autoCacheAudio = snap.autoCacheAudio
            } catch (e: Exception) {
                AppLog.w("SovaApp", "Failed to load prefs", e)
            }
        }

        // Fix #110: runtime-подписка на autoCacheAudio — обновляется немедленно
        // при переключении в настройках, без перезапуска приложения.
        // Fix #189: та же подписка обновляет AuthDomainsConfig.snapshot —
        // когда пользователь меняет домены в шестерёнке на LandingScreen,
        // snapshot обновляется за ~10-50мс, и следующий auth flow использует
        // новые домены.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs.data.collect { snap ->
                PlayerConnection.autoCacheAudio = snap.autoCacheAudio
                re.pinok.auth.exchange.AuthDomainsConfig.update(snap)
                prefsSnapshot = snap   // Fix #336: keep synchronous cache fresh
            }
        }

        // 4c. LongPoll — real-time messages.
        //     LongPoll слушает isOnlineFlow и прерывает poll при потере,
        //     немедленно переподключается при восстановлении.
        //     Также очищает connectionPool при onLost (stale TCP на мёртвом интерфейсе).
        //     §43 #NET-SWITCH-DELAY: передаём tokenInvalidationTicks + isTokenValid —
        //     LongPollClient делает паузу при инвалидации токена (error 5/1117 после
        //     switch Wi-Fi↔Mobile), проверяет isTokenValid каждые 2с для раннего
        //     выхода когда AuthActivity завершит silent re-login.
        longPollClient = LongPollClient(
            httpClient = httpClient,
            apiClient = apiClient,
            networkObserver = networkObserver,
            prefs = prefs,
            tokenInvalidationTicks = tokenInvalidationTicks,
            isTokenValid = { tokenStorage.hasValidToken() },
        )

        // #32: MessageNotifier — системные уведомления о новых сообщениях.
        //    Подписывается на longPollClient.events в отдельной корутине,
        //    показывает Notification при LongPollEvent.NewMessage.
        re.pinok.realtime.MessageNotifier.init(this)
        startMessageNotifier()

        // §42 #PUSH-NOTIFICATIONS: VkNotificationsNotifier + NotificationsPoller.
        //    Poller подписывается на LongPoll code 114 (NotificationsCountChanged)
        //    и периодически опрашивает notifications.getRedesign.
        //    Показывает system notifications для лайков/комментариев/репостов/
        //    ответов/подписок/упоминаний/подарков/записей на стене.
        //    Deep-link → MainActivity → SovaNavHost навигация на место события.
        re.pinok.realtime.VkNotificationsNotifier.init(this)
        notificationsPoller = re.pinok.realtime.NotificationsPoller(this, apiClient, prefs)
        startNotificationsPoller()

        // #CALLS: глобальный слушатель queuev4 — LP 115 (входящий звонок).
        //    Подписывается на queuev4Client.events; при LP 115/INCOMING_CALL
        //    логирует payload (conversation params) и показывает уведомление.
        //    Полный ответ на входящий (WebSocket signaling + vchat API) — TODO.
        createCallNotificationChannel()
        startCallNotifier()

        // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): SecurityAlertsPoller.
        //    Poller каждые 10 мин (safetyNetPollIntervalMin) опрашивает
        //    accountPersonal.getSecurityAlerts и показывает heads-up
        //    notification при подозрительном входе (новое устройство/город).
        //    Channel: vk_security_alerts (IMPORTANCE_HIGH, bypassDnd).
        //    Deep-link → MainActivity → (TODO: DevicesScreen, Sprint VK-ID-1).
        securityAlertsPoller = re.pinok.realtime.SecurityAlertsPoller(this, apiClient, prefs)
        startSecurityAlertsPoller()

        // #32: UnreadMessagesCounter — счётчик непрочитанных для badge на иконке.
        re.pinok.realtime.UnreadMessagesCounter.init(this)

        // §37.12 Phase 7: ClipsCounter — счётчик новых clips от подписок.
        // badge на пункте «Клипы» в боковой панели. Polling каждые 5 минут.
        re.pinok.realtime.ClipsCounter.start(this)

        // Fix #45: Единый network watcher — обрабатывает переключение WiFi↔Mobile
        // для ВСЕХ компонентов, а не только LongPoll (как было раньше).
        registerGlobalNetworkWatcher()

        // 6. Sprint 1, P0-3 (#76): Captcha handler. UI-диалог подписывается на
        //    captchaHandler.challenge, VKApiClient.callInternal вызывает solve()
        //    при error 14 — запрос блокируется до ввода пользователя.
        captchaHandler = UiCaptchaHandler()
        apiClient.captchaHandler = captchaHandler

        AppLog.i("SovaApp", "SovaApp initialized: okHttp=$httpClient, api=${apiClient.javaClass.simpleName}")

        // S7-4: Start proactive session keep-alive
        startKeepAlive()
    }

    /**
     * #32: Запускает корутину которая подписывается на LongPoll events и
     * показывает системное уведомление при входящем сообщении.
     *
     * P0.2: резолвит имя чата через [VKApiClient.messagesGetConversationsById]
     * (с кешированием в [MessageNotifier] activeNotifications) и инкрементирует
     * unreadCount для каждого нового сообщения в уже уведомлённом диалоге.
     */
    private fun startMessageNotifier() {
        keepAliveScope.launch {
            longPollClient.events.collect { event ->
                if (event is re.pinok.realtime.LongPollEvent.NewMessage) {
                    try {
                        // Пропускаем исходящие сообщения (флаг 2 = outbox в VK LongPoll)
                        if (event.flags and 2 != 0) return@collect

                        // Fix #135 (2026-XX): РАНЬШЕ было:
                        //   val text = if (event.text.isBlank()) "Вложение" else event.text
                        // Это означало: для сообщения со стикером/фото/голосовым
                        // (где LongPoll отдаёт пустой text) пуш показывал «Вложение» —
                        // без указания КАКОЕ именно. Пользователь жаловался: «в пушах
                        // содержимое не отображается».
                        //
                        // Теперь LongPollClient.handleNewMessage парсит extra JSON
                        // (Fix #134) и отдаёт attachType + fwdCount + replyMessagePreview
                        // + action/actionText. По ним строим осмысленное описание.
                        val text = describeMessageForPush(event)

                        // P0.2: резолвим имя чата. Сначала пробуем кеш activeNotifications,
                        // потом — async lookup через messagesGetConversationsById.
                        // Если lookup упал — fallback на «Новое сообщение».
                        val cached = re.pinok.realtime.MessageNotifier
                            .getActiveNotification(event.peerId)
                        val title: String
                        val unread: Int
                        // Fix #285: mute-стейт диалога. Если уже есть cached-запись —
                        // берём cached.muted (обновляется через MessageNotifier.setMuted
                        // при toggle в MessagesScreen). Иначе — lookup через
                        // messagesGetConversationsById → chat.pushSettings.isMuted().
                        val muted: Boolean
                        if (cached != null) {
                            title = cached.title
                            unread = cached.unreadCount + 1
                            muted = cached.muted
                        } else {
                            // Async lookup — может занять 200-500мс, но это OK для notification.
                            val chats = try {
                                apiClient.messagesGetConversationsById(listOf(event.peerId))
                            } catch (e: Exception) {
                                AppLog.w("SovaApp", "messagesGetConversationsById failed for notification: ${e.message}")
                                emptyList()
                            }
                            val chat = chats.firstOrNull()
                            title = chat?.peer?.title?.takeIf { it.isNotBlank() }
                                ?: "Новое сообщение"
                            // Используем server-side unread_count если доступен, иначе 1.
                            unread = chat?.unreadCount?.takeIf { it > 0 } ?: 1
                            muted = chat?.pushSettings?.isMuted() == true
                        }

                        // Fix #137: для чатов (peerId >= 2_000_000_000) пытаемся
                        // добавить имя отправителя в title, если оно доступно из
                        // event.fromId. Это делает пуш более информативным:
                        // «Иван Иванов: Стикер» вместо «Название чата: Стикер».
                        // Для 1-1 диалогов (peerId < 2e9) имя отправителя = title,
                        // ничего не меняем.
                        val displayTitle = if (event.peerId >= 2_000_000_000L && event.fromId != null && event.fromId > 0) {
                            val senderName = try {
                                apiClient.usersGetByIds(listOf(event.fromId))[event.fromId]
                                    ?.let { "${it.firstName} ${it.lastName}".trim() }
                            } catch (e: Exception) { null }
                            if (!senderName.isNullOrBlank() && title != senderName) {
                                "$senderName → $title"
                            } else {
                                title
                            }
                        } else {
                            title
                        }

                        re.pinok.realtime.MessageNotifier.showNotification(
                            context = this@SovaApp,
                            peerId = event.peerId,
                            title = displayTitle,
                            text = text,
                            unreadCount = unread,
                            muted = muted,
                        )
                    } catch (e: Exception) {
                        AppLog.w("SovaApp", "MessageNotifier event handling failed: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * #CALLS: слушатель queuev4 — входящие звонки (LP 115).
     *
     * Подписывается на [Queuev4Client.events]. При LP 115 (INCOMING_CALL)
     * payload = строка conversation-params звонка (см. CallModels.QueueCredential).
     * Сейчас только логируем + показываем системное уведомление — полный
     * ответ на входящий (декомпрессия conversation params → WebSocket
     * signaling → WebRTC) реализуется отдельной задачей.
     */
    private fun startCallNotifier() {
        // #CALLS: входящие звонки приходят через messages LongPoll code 115
        // (подтверждено: LongPollClient «unknown event type=115 ev=[115,-1,-1]»).
        // Это основной канал. queuev4-канал оставлен как fallback.
        keepAliveScope.launch {
            longPollClient.events.collect { event ->
                if (event is re.pinok.realtime.LongPollEvent.IncomingCall) {
                    try {
                        AppLog.i("SovaApp", "INCOMING_CALL (LP 115): payload.len=${event.payload?.length ?: 0}")
                        // Сохраняем pending — MainActivity откроет CallScreen по тапу на уведомление.
                        pendingIncomingCallPayload = event.payload
                        refreshIncomingCaller()
                        showIncomingCallNotification()
                    } catch (e: Exception) {
                        AppLog.w("SovaApp", "startCallNotifier: ${e.message}")
                    }
                }
            }
        }
        keepAliveScope.launch {
            queuev4Client.events.collect { ev ->
                try {
                    if (ev.queueId == re.pinok.realtime.Queuev4Client.QUEUE_CALLS) {
                        val payload = ev.payload["payload"] as? String
                        AppLog.i("SovaApp", "INCOMING_CALL (queuev4): payload.len=${payload?.length ?: 0}")
                        pendingIncomingCallPayload = payload
                        refreshIncomingCaller()
                        showIncomingCallNotification()
                    }
                } catch (e: Exception) {
                    AppLog.w("SovaApp", "startCallNotifier: ${e.message}")
                }
            }
        }
        // #CALLS: events_queue<uid> — полный payload входящего звонка (conversation params).
        keepAliveScope.launch {
            eventsQueuev4Client.events.collect { ev ->
                try {
                    val payload = ev.payload["payload"] as? String
                    if (ev.queueId == re.pinok.realtime.Queuev4Client.QUEUE_CALLS || !payload.isNullOrBlank()) {
                        AppLog.i("SovaApp", "INCOMING_CALL (events_queue): queue=${ev.queueId} payload.len=${payload?.length ?: 0}")
                        pendingIncomingCallPayload = payload
                        refreshIncomingCaller()
                        showIncomingCallNotification()
                    }
                } catch (e: Exception) {
                    AppLog.w("SovaApp", "eventsQueuev4 notifier: ${e.message}")
                }
            }
        }
    }

    /**
     * #CALLS: подтягиваем информацию о звонящем (caller_id → имя + фото).
     * Вызывается из startCallNotifier при входящем звонке (LP 115).
     * messagesGetCurrentCalls().first() → caller_id → usersGetByIds → title/photo.
     * При ошибке оставляем заглушку «Входящий звонок».
     */
    private fun refreshIncomingCaller() {
        keepAliveScope.launch {
            try {
                val callerId = withContext(Dispatchers.IO) {
                    apiClient.messagesGetCurrentCalls().firstOrNull()?.get("caller_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                }
                if (callerId <= 0L) {
                    pendingIncomingCallPeerId = callerId
                    return@launch
                }
                pendingIncomingCallPeerId = callerId
                val profile = withContext(Dispatchers.IO) {
                    apiClient.usersGetByIds(listOf(callerId))[callerId]
                }
                if (profile != null) {
                    val name = (profile.firstName + " " + profile.lastName).trim()
                    pendingIncomingCallTitle = name.take(40).ifBlank { "Входящий звонок" }
                    pendingIncomingCallPhoto = profile.photo100
                    // #CALLS-FIX (2026-08-27): имя подтянулось — обновляем уведомление,
                    // чтобы в шторке было «Иван Иванов», а не «Кто-то звонит вам в VK».
                    showIncomingCallNotification()
                }
            } catch (e: Exception) {
                AppLog.w("SovaApp", "refreshIncomingCaller: ${e.message}")
            }
        }
    }

    /**
     * #CALLS: автоподключение queuev4 (входящие звонки).
     *
     * Вызывается при старте приложения (когда есть валидный токен) —
     * параллельно с LongPoll. Получает queue-credential через
     * [VKApiClient.queueSubscribe] (queue.subscribe с SAT/web-токеном) и
     * запускает long-poll на queuev4.vk.ru — ловим LP 115 (входящий звонок).
     *
     * Если credential уже задан вручную (SovaPrefs callsQueueKey/Ts) — используем его.
     */
    fun startCallSignaling() {
        if (queuev4Client.isRunning()) return
        // #CALLS-DEBUG: включаем CALLS-логи (иначе Queuev4Client INFO/DEBUG скрыты).
        runCatching { re.pinok.util.AppLog.setCategoryEnabled(re.pinok.util.AppLog.LogCategory.CALLS, true) }
        keepAliveScope.launch {
            try {
                // 0) #CALLS-AUTO: получаем session_key (vchat) автоматически,
                //    если его ещё нет. Как браузер: get_anonym_token → auth.anonymLogin.
                ensureCallsSessionKey()
                // 1) Сначала — автоматический queue.subscribe (свежий credential).
                val cred = apiClient.queueSubscribe()
                if (cred != null) {
                    AppLog.i("SovaApp", "queue.subscribe OK (key=${cred.key.take(8)}… ts=${cred.ts})")
                    queuev4Client.setCredential(cred)
                    queuev4Client.start()
                }
                // 1b) events_queue<uid> / nccts<uid> — сюда приходят полные conversation
                //     params звонка (payload с tkn для WebSocket-сигналинга).
                //     Имя очереди из im_m_comms_key localStorage: "nccts<uid>".
                //     events_queue<uid> — из queue_connection_events_queue<uid>.
                try {
                    val uid = exchangeAuthRepository.userId()
                    var eventsCred = apiClient.queueSubscribe(queueIdSuffix = "nccts$uid")
                    if (eventsCred == null) {
                        eventsCred = apiClient.queueSubscribe(queueIdSuffix = "events_queue$uid")
                    }
                    if (eventsCred != null) {
                        AppLog.i("SovaApp", "events_queue.subscribe OK (key=${eventsCred.key.take(8)}… ts=${eventsCred.ts})")
                        eventsQueuev4Client.setCredential(eventsCred)
                        eventsQueuev4Client.start()
                    } else {
                        AppLog.w("SovaApp", "events_queue subscribe вернул null")
                    }
                } catch (e: Exception) {
                    AppLog.w("SovaApp", "events_queue subscribe error: ${e.message}")
                }
                // 2) Fallback: ручной credential из SovaPrefs.
                val snap = prefs.data.first()
                if (snap.callsQueueKey.isNotBlank() && snap.callsQueueTs > 0L) {
                    val uid = exchangeAuthRepository.userId()
                    queuev4Client.setCredential(
                        re.pinok.data.model.QueueCredential(
                            key = snap.callsQueueKey,
                            ts = snap.callsQueueTs,
                            url = "https://queuev4.vk.ru/im1180",
                            userId = uid,
                        )
                    )
                    queuev4Client.start()
                    AppLog.i("SovaApp", "queuev4 started with manual credential")
                } else {
                    AppLog.w("SovaApp", "queue.subscribe failed и нет manual credential — входящие звонки недоступны")
                }
            } catch (e: Exception) {
                AppLog.e("SovaApp", "startCallSignaling failed", e)
            }
        }
    }

    /**
     * #CALLS-AUTO (2026-08-23): автоматическое получение session_key (vchat)
     * и okcdn uid — как браузер. Цепочка:
     *   get_anonym_token (oauth.vk.ru, с CookieJar антифрод-кук)
     *     → auth.anonymLogin (calls.okcdn.ru) → { session_key, session_secret_key, uid }
     *
     * Результат сохраняется в SovaPrefs (callsSessionKey / callsSessionUid) —
     * используется CallScreen/vchat без ручного ввода.
     *
     * @return session_key (не пустой) или null.
     */
    suspend fun ensureCallsSessionKey(force: Boolean = false): String? {
        return try {
            // 1) Уже есть в prefs — используем (если не force).
            val snap = prefs.data.first()
            if (!force && snap.callsSessionKey.isNotBlank()) {
                AppLog.d("SovaApp", "ensureCallsSessionKey: уже есть (len=${snap.callsSessionKey.length})")
                return snap.callsSessionKey
            }
            // 2) Получаем через get_anonym_token → auth.anonymLogin.
            //    Для vchat нужен session_key ПРАВИЛЬНОГО формата (-w-fl..., 156), который
            //    даёт auth.anonymLogin С auth_token = $Ksd-токен (version=3). Без auth_token
            //    сервер даёт ключ -w-vF... (134), который vchat не принимает.
            var callToken = snap.callsCallToken.takeIf { it.isNotBlank() && it.startsWith("$") }
            if (callToken.isNullOrBlank()) {
                // #CALLS-FIX (2026-08-24): если $Ksd нет в prefs — НЕ используем getAnonymToken
                // (он даёт anonym.eyJ..., который сервер НЕ принимает: AUTH_LOGIN).
                // Вместо этого получаем $Ksd через messages.getCallToken — как браузер.
                // Для этого нужен VK access_token + cookieHeader (remixsid/httoken).
                val token = tokenStorage.load()
                val accessToken = token?.accessToken
                val cookieHeader = try {
                    re.pinok.auth.exchange.RemixsidCapturer.buildVkCookieHeader()
                } catch (_: Exception) { null }
                if (!accessToken.isNullOrBlank() && !cookieHeader.isNullOrBlank()) {
                    val ct: String? = withContext(Dispatchers.IO) {
                        apiClient.getCallToken(accessToken, cookieHeader)
                    }
                    if (!ct.isNullOrBlank()) {
                        callToken = ct
                        prefs.setCallsCallToken(ct)
                        AppLog.i("SovaApp", "ensureCallsSessionKey: получен \$Ksd через getCallToken (len=${ct.length})")
                    }
                }
            }
            if (callToken.isNullOrBlank()) {
                // Fallback: getAnonymToken (может не сработать, но попробуем)
                callToken = withContext(Dispatchers.IO) { apiClient.getAnonymToken() }
                if (!callToken.isNullOrBlank()) {
                    AppLog.w("SovaApp", "ensureCallsSessionKey: fallback getAnonymToken (может не сработать: ${callToken.take(12)}…)")
                }
            }
            if (callToken.isNullOrBlank()) {
                AppLog.w("SovaApp", "ensureCallsSessionKey: callToken пуст (нет $-токена)")
                return null
            }
            AppLog.i("SovaApp", "ensureCallsSessionKey: callToken=${callToken.take(12)}…")
            val deviceId = exchangeAuthRepository.deviceId()
            var sessionKey: String? = null
            for (apiKey in listOf("CGMMEJLGDIHBABABA", "7793118", "android_web", "0")) {
                sessionKey = withContext(Dispatchers.IO) {
                    apiClient.vchatAnonymLogin(callToken, apiKey, deviceId)
                }
                if (!sessionKey.isNullOrBlank()) break
            }
            if (sessionKey.isNullOrBlank()) {
                AppLog.w("SovaApp", "ensureCallsSessionKey: auth.anonymLogin не вернул session_key")
                return null
            }
            // 3) Сохраняем в prefs. okcdn uid (584520805550) — из auth.anonymLogin
            //    (lastAnonymUid), НЕ VK user_id. Он нужен для userId в WS URL сигналинга.
            prefs.setCallsSessionKey(sessionKey)
            val okUid = apiClient.lastAnonymUid()
            val uid = if (okUid > 0L) okUid else exchangeAuthRepository.userId()
            if (uid > 0L) prefs.setCallsSessionUid(uid)
            AppLog.i("SovaApp", "ensureCallsSessionKey: OK (len=${sessionKey.length}, uid=$uid)")
            sessionKey
        } catch (e: Exception) {
            AppLog.w("SovaApp", "ensureCallsSessionKey error: ${e.message}")
            null
        }
    }

    /**
     * #CALLS-AUTO (2026-08-24): ПОЛНАЯ автоматическая цепочка получения
     * conversation params для звонка — как в браузере, без ручного ввода:
     *
     *   1) session_key из prefs (если есть)
     *   2) vchat.getConversationParams(call_id, session_key)
     *   3) если vchat вернул 102 PARAM_SESSION_EXPIRED (session_key протух) —
     *      автоматически получаем СВЕЖИЙ через get_anonym_token → auth.anonymLogin,
     *      сохраняем в prefs и повторяем vchat.
     *
     * Возвращает пару (sessionKey, vchatResponse). vchatResponse может быть null —
     * вызывающий сам решает (FAILED). sessionKey может быть null если не удалось
     * получить вообще (нет сети/аномалогин не прошёл).
     *
     * @param conversationId call_id из messages.getCurrentCalls
     * @return (sessionKey, vchatResponse)
     */
    suspend fun getCallConversationParams(
        conversationId: String,
    ): Pair<String?, com.google.gson.JsonObject?> {
        return withContext(Dispatchers.IO) {
            var sessionKey: String? = null
            var vchatResp: com.google.gson.JsonObject? = null
            try {
                // 1) session_key из prefs.
                val snap = prefs.data.first()
                val rawSk = snap.callsSessionKey
                if (!rawSk.isNullOrBlank()) {
                    sessionKey = rawSk
                    AppLog.i("SovaApp", "getCallParams: session_key из prefs (${rawSk.take(12)}…)")
                }

                // 2) vchat.getConversationParams (до 2 попыток: если 102 — обновляем ключ).
                for (attempt in 1..2) {
                    vchatResp = apiClient.vchatGetConversationParams(conversationId, sessionKey)
                    if (vchatResp != null) {
                        AppLog.i("SovaApp", "getCallParams: vchat OK (attempt $attempt)")
                        break
                    }
                    // #CALLS-IN-FIX (2026-08-29): сбой должен быть ВИДЕН в логе —
                    // раньше attempt с null проходил молча, и по логу было не понять,
                    // где именно завис входящий звонок.
                    AppLog.w("SovaApp", "getCallParams: vchat null (attempt $attempt, convId=$conversationId)")
                    // 3) Не получилось — проверяем, была ли ошибка именно 102 (session expired)
                    //    и обновляем session_key. Даже если причина другая — пробуем свежий ключ.
                    if (sessionKey.isNullOrBlank() || attempt == 1) {
                        val fresh = ensureCallsSessionKey(force = true)
                        if (!fresh.isNullOrBlank() && fresh != sessionKey) {
                            sessionKey = fresh
                            AppLog.i("SovaApp", "getCallParams: получен свежий session_key (${fresh.take(12)}…)")
                            continue
                        }
                    }
                    break
                }
            } catch (e: Exception) {
                AppLog.e("SovaApp", "getCallParams error: ${e.message}")
            }
            sessionKey to vchatResp
        }
    }

    /**
     * #CALLS: системное уведомление «Входящий звонок».
     * Пока входящие звонки не могут быть приняты (нет WebSocket-сигналинга),
     * показываем информационное уведомление, чтобы пользователь знал о звонке.
     * Звук/вибрация задаются каналом vk_calls (создаётся в onCreate — иначе
     * Android 8+ создаст канал с дефолтными настройками без звука).
     */
    private fun createCallNotificationChannel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                val ch = android.app.NotificationChannel(
                    "vk_calls", "Звонки",
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                )
                ch.description = "Входящие звонки VK"
                // Звук: системный рингтон звонка. Вибрация: стандартный паттерн.
                ch.setSound(
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_RINGTONE
                    ),
                    android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT,
                )
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 500, 400, 500, 400, 500)
                ch.enableLights(true)
                nm.createNotificationChannel(ch)
            }
        } catch (e: Exception) {
            AppLog.w("SovaApp", "createCallNotificationChannel failed: ${e.message}")
        }
    }

    private fun showIncomingCallNotification() {
        try {
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            // Гарантируем канал со звуком/вибрацией (создаётся при первом звонке,
            // если onCreate ещё не вызывал — см. createCallNotificationChannel).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val cur = nm.getNotificationChannel("vk_calls")
                if (cur == null) createCallNotificationChannel()
            }
            val intent = Intent(this, re.pinok.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                this, 0xB17, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = @Suppress("DEPRECATION")
            android.app.Notification.Builder(this, "vk_calls")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Входящий звонок")
                // #CALLS-FIX (2026-08-27): показываем имя звонящего (если уже подтянулось),
                // а не безличное «Кто-то звонит вам в VK».
                .setContentText(
                    if (pendingIncomingCallTitle.isNotBlank()) pendingIncomingCallTitle
                    else "Кто-то звонит вам в VK"
                )
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setOngoing(true)
            // FullScreenIntent: экран поверх блокировки/другого приложения.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setFullScreenIntent(pi, true)
            } else {
                builder.setFullScreenIntent(pi, true)
            }
            nm.notify(0xB17, builder.build())
        } catch (e: Exception) {
            AppLog.w("SovaApp", "showIncomingCallNotification failed: ${e.message}")
        }
    }

    /**
     * Fix #135: строит осмысленное описание сообщения для системного пуша.
     *
     * Раньше возвращала «Вложение» для всех сообщений с пустым text —
     * теперь строит описание по типу вложения (Стикер/Фото/Голосовое
     * сообщение/Видео/...) и добавляет признаки reply/forward если есть.
     *
     * Логика приоритета:
     *  1. action (сервисное сообщение) → actionText или красивое описание action
     *  2. text непустой → text (приоритет тексту, даже если есть вложение)
     *  3. attachType → «Стикер»/«Фото»/«Голосовое сообщение»/...
     *  4. fwdCount > 0 → «Пересланное сообщение (N)»
     *  5. replyMessagePreview != null → «Ответ: <превью>»
     *  6. fallback → «Вложение»
     *
     * Fix #138c (URGENT 2026-08-03): если text состоит ТОЛЬКО из цифр и длина
     * 9-13 символов — это подозрительно на Unix timestamp / random_id, не на
     * реальный текст сообщения. Такие сообщения показывались в шторке как
     * «1785781370» (просто число) — пользователь жаловался на «непонятные
     * уведомления». Если text выглядит как timestamp — пропускаем его и
     * строим описание из вложений/forward/reply, либо fallback «Новое сообщение».
     */
    private fun describeMessageForPush(event: re.pinok.realtime.LongPollEvent.NewMessage): String {
        // 1. Сервисные сообщения (chat_create, chat_kick_user, chat_pin_message, ...).
        if (!event.action.isNullOrBlank()) {
            return describeActionForPush(event.action, event.actionText)
        }
        // Fix #138c: проверка text на «timestamp pattern» — только цифры, 9-13 символов.
        // Типичные Unix timestamps: 1785781370 (10 цифр, секунды), 1785781370123 (13, миллис).
        // Если text такой — игнорируем его, идём к описанию вложений.
        val realText = if (looksLikeTimestamp(event.text)) "" else event.text

        // 2. Текст сообщения — приоритет, если есть (даже с вложением — VK
        //    обычно отдаёт caption в text, а photo в attachType).
        if (realText.isNotBlank()) {
            val base = realText.take(200).replace("\n", " ").trim()
            // Если есть reply — добавляем контекст «↩ Ответ: <превью>».
            return if (event.replyMessagePreview != null && event.replyMessagePreview != "<вложение>") {
                "↩ Ответ: $base"
            } else {
                base
            }
        }
        // 3. Вложение по типу.
        val attachDesc = if (event.attachType != null) {
            describeAttachTypeForPush(event.attachType)
        } else if (event.fwdCount > 0) {
            // 4. Только пересланные сообщения, без своего вложения.
            if (event.fwdCount == 1) "Пересланное сообщение" else "Пересланные сообщения (${event.fwdCount})"
        } else if (event.replyMessagePreview != null) {
            // 5. Только reply, без своего текста/вложения (редкий случай).
            "↩ Ответ"
        } else if (realText.isBlank() && event.text.isBlank()) {
            // Fix #139-P0.6 (2026-08-03): РАНЬШЕ возвращала «Вложение» даже
            // когда вложения нет (если text пустой). Это вводило пользователя
            // в заблуждение — пуш показывал «Вложение» для служебных/meta-events
            // которые прошли фильтр Fix #139, но не имели реального контента.
            // Теперь показываем нейтральное «Новое сообщение».
            // (event.attachType здесь всегда null — если бы был не null,
            // зашли бы в ветку 3 выше.)
            "Новое сообщение"
        } else {
            // Fix #138c: text был, но мы его отбросили как timestamp-pattern.
            // Не показываем «Вложение» (это введёт в заблуждение — вложения нет),
            // показываем нейтральное «Новое сообщение».
            "Новое сообщение"
        }
        // Доп. маркеры: если есть reply или forward одновременно с вложением.
        val extras = mutableListOf<String>()
        if (event.replyMessagePreview != null) extras.add("↩")
        if (event.fwdCount > 0) extras.add("↪${event.fwdCount}")
        return if (extras.isEmpty()) attachDesc else "$attachDesc ${extras.joinToString(" ")}"
    }

    /**
     * Fix #138c: проверка, выглядит ли строка как Unix timestamp / random_id.
     *
     * Признаки:
     *  - Состоит ТОЛЬКО из цифр (после trim).
     *  - Длина 9-13 символов (10 = seconds, 13 = millis).
     *  - Не empty.
     *
     * Используется в [describeMessageForPush] чтобы отбрасывать подозрительные
     * «числовые» text'ы, которые на самом деле являются random_id или msg_date
     * из VK LP v14 echo-events (см. Fix #138 в LongPollClient.handleNewMessage).
     *
     * ВНИМАНИЕ: это эвристика. Реальный текст сообщения МОЖЕТ состоять только
     * из цифр (например, пользователь отправил «1234567890» как код). Но:
     *  - Такие сообщения редки.
     *  - Если это реальный текст, пользователь всё равно откроет чат и увидит его.
     *  - Лучше показать «Новое сообщение» чем «1785781370» — это менее запутывает.
     *
     * Альтернатива: проверять что число попадает в диапазон реальных Unix
     * timestamps (1_000_000_000..2_000_000_000 для секунд, или
     * 1_000_000_000_000..2_000_000_000_000 для миллисекунд). Но VK random_id
     * может быть любым — и он тоже часто похож на timestamp. Поэтому оставляем
     * простую эвристику по длине.
     */
    private fun looksLikeTimestamp(s: String): Boolean {
        if (s.length !in 9..13) return false
        if (s.isBlank()) return false
        // Только цифры (без пробелов, точек, дефисов).
        return s.all { it.isDigit() }
    }

    /** Fix #135: красивое описание типа вложения для пуша.
     *  Fix #139-P0.7 (2026-08-03): добавлены 12 недостающих типов из анализа
     *  архивов m.vk.ru и VK API docs. */
    private fun describeAttachTypeForPush(type: String): String = when (type) {
        "sticker"       -> "Стикер"
        "photo"         -> "Фото"
        "video"         -> "Видео"
        "audio"         -> "Аудиозапись"
        "audio_message" -> "Голосовое сообщение"
        "doc"           -> "Документ"
        "wall"          -> "Запись на стене"
        "gift"          -> "Подарок"
        "link"          -> "Ссылка"
        "poll"          -> "Опрос"
        "story"         -> "История"
        "market"        -> "Товар"
        "call"          -> "Звонок"
        "money_request" -> "Запрос денег"
        "article"       -> "Статья"
        "podcast"       -> "Подкаст"
        "narrative"     -> "Сюжет"
        "event"         -> "Событие"
        // Fix #139-P0.7: недостающие типы
        "graffiti"              -> "Граффити"
        "audio_playlist"        -> "Плейлист"
        "money_transfer"        -> "Денежный перевод"
        "widget"                -> "Виджет"
        "podcast_episode"       -> "Эпизод подкаста"
        "story_reply"           -> "Ответ на историю"
        "call_missed"           -> "Пропущенный звонок"
        "market_album"          -> "Подборка товаров"
        "narrative_reply"       -> "Ответ на сюжет"
        "curator"               -> "Куратор"
        "video_message"         -> "Видеосообщение"
        "currency"              -> "Валюта"
        "app_action"            -> "Действие в приложении"
        else            -> "Вложение"
    }

    /** Fix #135: описание сервисных action-сообщений (chat_create, kick, pin, ...). */
    private fun describeActionForPush(action: String, actionText: String?): String {
        if (!actionText.isNullOrBlank()) return actionText
        return when (action) {
            "chat_create"            -> "Создал(а) беседу"
            "chat_title_update"      -> "Изменил(а) название беседы"
            "chat_photo_update"      -> "Обновил(а) фото беседы"
            "chat_photo_remove"      -> "Удалил(а) фото беседы"
            "chat_invite_user"       -> "Пригласил(а) пользователя"
            "chat_kick_user"         -> "Исключил(а) пользователя"
            "chat_pin_message"       -> "Закрепил(а) сообщение"
            "chat_unpin_message"     -> "Открепил(а) сообщение"
            "chat_invite_user_by_link" -> "Присоединился(ась) по ссылке"
            "accepted_message_request" -> "Принял(а) запрос на сообщение"
            else                     -> "Сервисное сообщение"
        }
    }

    /**
     * §42 #PUSH-NOTIFICATIONS: подписка на LongPoll code 114
     * (NotificationsCountChanged) + запуск периодического poller'а.
     *
     * - LongPoll code 114 → notificationsPoller.triggerImmediatePoll()
     *   → notifications.getRedesign → diff → system notifications.
     * - Периодический poll (pushPollingIntervalSec, default 120с) — fallback
     *   если code 114 не пришёл (LongPoll отключён или VK не шлёт).
     *
     * Poller сам проверяет pushEnabled и hasValidToken перед каждым poll.
     */
    private fun startNotificationsPoller() {
        val poller = notificationsPoller
        if (poller == null) {
            AppLog.w("SovaApp", "startNotificationsPoller: poller is null — skip")
            return
        }
        // Подписка на LongPoll code 114 → немедленный poll.
        keepAliveScope.launch {
            longPollClient.events.collect { event ->
                if (event is re.pinok.realtime.LongPollEvent.NotificationsCountChanged) {
                    AppLog.d("SovaApp", "LongPoll code 114 → triggerImmediatePoll")
                    poller.triggerImmediatePoll()
                }
            }
        }
        // Запуск периодического poller'а.
        poller.start()
        AppLog.i("SovaApp", "NotificationsPoller started (push notifications)")
    }

    /**
     * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): запуск SecurityAlertsPoller.
     *
     * Poller каждые safetyNetPollIntervalMin минут (default 10) опрашивает
     * `accountPersonal.getSecurityAlerts`. При новом alert'е — heads-up
     * notification (channel `vk_security_alerts`, IMPORTANCE_HIGH, bypassDnd).
     *
     * НЕ зависит от LongPoll — это REST API polling.
     *
     * Poller сам проверяет pushSafetyNetAlerts и hasValidToken перед каждым poll.
     */
    private fun startSecurityAlertsPoller() {
        val poller = securityAlertsPoller
        if (poller == null) {
            AppLog.w("SovaApp", "startSecurityAlertsPoller: poller is null — skip")
            return
        }
        poller.start()
        AppLog.i("SovaApp", "SecurityAlertsPoller started (§49.5.1 safety-net alerts)")
    }

    /**
     * Fix #45: Единый обработчик переключения сети (WiFi↔Mobile).
     * Fix #171: разделены ПОЛНАЯ потеря сети (onLostListeners) и СМЕНА default
     * network (onDefaultNetworkChangedListener). Раньше onLost срабатывал на
     * любую потерю сети → cancelAll() убивал HLS-стрим ExoPlayer при switch
     * mobile→Wi-Fi даже когда сеть оставалась онлайн.
     */
    private fun registerGlobalNetworkWatcher() {
        // Полная потеря сети — жёсткий reset (evictAll + cancelAll).
        // Срабатывает ТОЛЬКО когда checkOnline() == false (см. NetworkObserver.onLost).
        networkObserver.addOnNetworkLostListener {
            AppLog.w("SovaApp", "Network FULLY lost — evicting OkHttp pool + cancelling in-flight calls (global)")
            try { httpClient.connectionPool.evictAll() } catch (_: Exception) {}
            try { httpClient.dispatcher.cancelAll() } catch (_: Exception) {}
            try { re.pinok.media.PlayerConnection.onNetworkChanged(online = false) } catch (_: Exception) {}
            // #NET-SWITCH-POPUP: полная потеря сети → Offline state.
            setNetworkSwitchState(NetworkSwitchState.Offline)
        }

        // Fix #171: СМЕНА default network (mobile→Wi-Fi или Wi-Fi→mobile), сеть
        // осталась онлайн. Мягкий reset: только evictAll (закрыть keep-alive на
        // старом мёртвом интерфейсе), БЕЗ cancelAll (не рвать HLS-стрим).
        // + переподготовка плеера чтобы ExoPlayer перестроил MediaSource на новом
        // интерфейсе с сохранением позиции.
        networkObserver.addOnDefaultNetworkChangedListener {
            val ctype = try { networkObserver.connectionType() } catch (_: Exception) { "network" }
            AppLog.i("SovaApp", "Default network SWITCHED to $ctype — soft reset (evictAll, NO cancelAll) + reprepare player")
            try { httpClient.connectionPool.evictAll() } catch (_: Exception) {}
            try { apiClient.resetNetworkErrorCounter() } catch (_: Exception) {}
            try { re.pinok.media.PlayerConnection.onNetworkChanged(online = true, forceReprepare = true) } catch (_: Exception) {}

            // #VKID-SEAMLESS (vk.id.md P0-5): PROACTIVE silent refresh на смене сети.
            // Раньше refresh запускался ТОЛЬКО реактивно — когда следующий API-вызов
            // падал с err=5/1117 (VK инвалидировал token из-за IP change) и VKApiClient
            // err-handler вызывал ensureFreshToken(force=true). Это значит пользователь
            // успевал увидеть ошибку / NetworkSwitchPopup c spinner.
            //
            // Теперь: СРАЗУ на onAvailable(default route switched) запускаем
            // ensureFreshToken(force=true) в фоне. Path 1.5 silentRefreshViaRemixsid
            // использует p cookie (1 год, НЕ IP-bound) + remixsid + полный cookie-set
            // → получает новый web_token на новый IP ДО того, как UI сделает API-запрос.
            // Бесшовно: пользователь не видит ни popup, ни AuthActivity.
            //
            // Guard hasSilentReloginMeans() (теперь включает pCookie — см. P0-2):
            // если совсем нет credentials (пользователь не залогинен) — пропускаем,
            // не делаем холостых HTTP-вызовов. refreshMutex внутри ensureFreshToken
            // сериализует concurrent вызовы (keepAlive / reactive err-handler / этот).
            if (::exchangeAuthRepository.isInitialized) {
                val canSilent = try { exchangeAuthRepository.hasSilentReloginMeans() } catch (_: Exception) { false }
                if (canSilent) {
                    keepAliveScope.launch {
                        try {
                            val refreshed = exchangeAuthRepository.ensureFreshToken(force = true)
                            if (refreshed != null) {
                                AppLog.i("SovaApp", "Proactive silent refresh after network switch: OK — token valid on new IP")
                                setNetworkSwitchState(NetworkSwitchState.Idle)
                            } else {
                                AppLog.w("SovaApp", "Proactive silent refresh after network switch: null " +
                                    "(reactive err-handler path will retry on next API call)")
                            }
                        } catch (e: Exception) {
                            AppLog.w("SovaApp", "Proactive silent refresh after network switch failed: ${e.message}")
                        }
                    }
                } else {
                    AppLog.i("SovaApp", "Proactive silent refresh skipped — no silent relogin means (user not logged in)")
                    // #SESSION-HOLD: нет silent-средств (remixsid/p/trusted_hash/exchange_token),
                    // но web-токен может быть валиден (типично для VK-app SSO — токен без
                    // remixsid). Best-effort: пытаемся захватить remixsid из CookieManager
                    // через скрытый WebView. Если app WebView имеет VK-сессию — Path 1.5
                    // станет доступной, и следующие смены сети будут тихими. Дешёво
                    // (≤10с в фоне), не блокирует UI.
                    val signedIn = try { exchangeAuthRepository.isSignedIn() } catch (_: Exception) { false }
                    if (signedIn) {
                        keepAliveScope.launch {
                            try {
                                val captured = RemixsidCapturer.capture(this@SovaApp)
                                if (captured != null) {
                                    exchangeAuthRepository.saveRemixsid(captured)
                                    AppLog.i("SovaApp", "#SESSION-HOLD: remixsid захвачен после смены сети " +
                                        "(len=${captured.remixsid.length}) — Path 1.5 enabled")
                                }
                            } catch (e: Exception) {
                                AppLog.w("SovaApp", "#SESSION-HOLD: remixsid capture failed: ${e.message}")
                            }
                        }
                    }
                }
            }

            // #NET-SWITCH-POPUP: смена default route → Switching state.
            // Popup покажет спиннер + «Отмена»/«Закрыть». При успехе proactive refresh
            // выше переведёт в Idle; при неудаче VKApiClient err-handler переведёт в
            // Refreshing, затем Failed. Если VK не вернёт err=5 (IP binding не изменился)
            // — Switching auto-очистится в Idle через короткий таймаут в NetworkSwitchPopup.
            setNetworkSwitchState(NetworkSwitchState.Switching(
                sinceMs = System.currentTimeMillis(),
                reason = "Смена сети → $ctype",
            ))
        }

        keepAliveScope.launch {
            var wasOnline = networkObserver.isOnline()
            networkObserver.isOnlineFlow.collect { online ->
                if (!wasOnline && online) {
                    AppLog.i("SovaApp", "Network restored (offline→online) — resetting API error counter")
                    try { apiClient.resetNetworkErrorCounter() } catch (_: Exception) {}
                    try { re.pinok.media.PlayerConnection.onNetworkChanged(online = true) } catch (_: Exception) {}
                    // #NET-SWITCH-POPUP: сеть восстановилась → Idle (скрыть popup).
                    setNetworkSwitchState(NetworkSwitchState.Idle)
                }
                wasOnline = online
            }
        }
    }

    /**
     * S7-4: Session keep-alive — proactively refreshes the access token every 60s
     * to prevent session drops during idle. Uses [ExchangeAuthRepository.keepAlive]
     * which only triggers a network call when the token is within 300 seconds of
     * expiry (Fix #216, P1.1 — было 60с, стало 300с).
     *
     * 60-секундный polling интервал остаётся — это частота проверки, а не окно
     * refresh. Окно refresh (когда silentAuth вызывается) теперь 300с.
     */
    private fun startKeepAlive() {
        keepAliveScope.launch {
            var failStreak = 0
            while (true) {
                delay(keepAliveDelayMs(failStreak))
                try {
                    if (!::exchangeAuthRepository.isInitialized) {
                        failStreak = 0
                        continue
                    }
                    when (exchangeAuthRepository.keepAlive()) {
                        KeepAliveResult.REFRESHED -> {
                            failStreak = 0
                            AppLog.i("SovaApp", "Keep-alive: token refreshed proactively")
                        }
                        KeepAliveResult.NOT_NEEDED -> {
                            failStreak = 0
                        }
                        KeepAliveResult.FAILED -> {
                            failStreak++
                            AppLog.w("SovaApp", "Keep-alive: refresh needed but failed " +
                                "(streak=$failStreak) — retry with backoff (#KEEPALIVE-BACKOFF)")
                        }
                    }
                } catch (e: Exception) {
                    failStreak++
                    AppLog.w("SovaApp", "Keep-alive failed: ${e.message}")
                }
            }
        }
        // #SETTINGS-FIX: периодический account.setOnline — посылает VK ping
        // каждые ~5 минут, обновляя last_seen. Пропускается если
        // privacyHideLastSeen=true (тогда accountSetOnline() → no-op).
        keepAliveScope.launch {
            while (true) {
                try { apiClient.accountSetOnline() } catch (_: Exception) {}
                delay(300_000L) // 5 минут (как в оригинальном VK Android)
            }
        }
    }

    /**
     * P0 #KEEPALIVE-BACKOFF: интервал опроса keepAlive зависит от числа подряд
     * идущих провалов. Если токен в окне истечения, а silent refresh падает —
     * ретраим быстрее (15с/30с/45с), а не ждём полные 60с. Успех/not-needed
     * сбрасывает интервал на базовые 60с.
     */
    private fun keepAliveDelayMs(failStreak: Int): Long = when (failStreak) {
        0 -> 60_000L
        1 -> 15_000L
        2 -> 30_000L
        else -> 45_000L
    }

    /**
     * Coil 3 single-image-loader factory — reuses our OkHttp client
     * so images go through the same User-Agent / connection pool as API calls.
     *
     * Fix #229: анимированные стикеры (GIF + animated WebP).
     * В Coil 3.3.0 API поменялся относительно Coil 2:
     *  - GifDecoder / AnimatedWebPDecoder / AnimatedImageDecoder — это Decoder,
     *    а НЕ Decoder.Factory → add() их НЕ принимает.
     *  - Регистрируем через вложенный класс Factory: AnimatedImageDecoder.Factory()
     *    (один декодер для GIF + animated WebP + animated HEIF, на android.graphics.ImageDecoder).
     *  - AnimatedImageDecoder требует API 28+. На API 24-27 фолбэк на GifDecoder.Factory()
     *    (GIF только, animated WebP НЕ поддерживается — стикеры покажутся статичными).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = httpClient))
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil3.gif.AnimatedImageDecoder.Factory())
                } else {
                    add(coil3.gif.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    companion object {
        private const val SECURE_PREFS_FILE = "sova_secure_prefs.xml"

        @Volatile
        private var instance: SovaApp? = null

        fun get(): SovaApp = instance ?: error("SovaApp not initialized")

        fun getOrNull(): SovaApp? = instance

        fun get(context: Context): SovaApp =
            context.applicationContext as? SovaApp ?: get()
    }
}
