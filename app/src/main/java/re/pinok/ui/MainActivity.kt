package re.pinok.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.photos.LocalPhotosDeps
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.auth.AuthActivity
import re.pinok.BuildConfig
import re.pinok.locker.LockerActivity
import re.pinok.ui.components.DraggableLogFab
import re.pinok.ui.components.LogDialogState
import re.pinok.ui.components.LogViewerDialog
import re.pinok.ui.components.ShareToChatSheet
import re.pinok.ui.navigation.SovaNavHost
import re.pinok.ui.navigation.VideoHolder
import re.pinok.ui.screens.offline.OfflineAudioPlayerScreen
import re.pinok.ui.screens.offline.OfflineManagerScreen
import re.pinok.ui.screens.offline.StoryOfflinePlayerScreen
import re.pinok.ui.screens.videoplayer.VideoPlatformRouter
import re.pinok.data.model.Video
import re.pinok.ui.theme.SOVATheme
import re.pinok.util.AppLog
import re.pinok.util.RequestAllPermissionsEffect

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * #SSO-RECREATE-GUARD: timestamp последнего запуска AuthActivity.
         *
         * Хранится в companion object (static) — переживает recreate
         * MainActivity, который система делает при low-memory kill во
         * время VK-app SSO (юзер в VK app подтверждает вход → система
         * убивает AuthActivity + MainActivity → юзер возвращается →
         * MainActivity onCreate → isOnlineFlow=true → без этой защиты
         * network-restored-no-token запускает НОВЫЙ AuthActivity с
         * НОВЫМ QR-токеном, ломая match с подтверждением юзера → loop).
         *
         * Instance-поле lastAuthActivityLaunchMs (Fix #230) НЕ переживает
         * recreate — сбрасывается в 0L. Это поле — единственный источник
         * правды о том, что AuthActivity был запущен недавно.
         *
         * Guard в launchAuth блокирует перезапуск в течение 90 сек
         * (SSO_TIMEOUT_MS) — больше чем QR TTL VK (~60 сек) + время
         * на возврат из VK app. После 90 сек guard истекает, давая
         * чистый retry без loop.
         *
         * @Volatile: все обращения — на main thread (Activity callbacks,
         * LaunchedEffect), но @Volatile добавлен для safety (например
         * если LongPollKeepAliveService вызовет через handler).
         */
        @Volatile
        private var lastAuthActivityLaunchedAt: Long = 0L

        /** Окно SSO-защиты: 90 сек с момента запуска AuthActivity. */
        private const val SSO_GUARD_WINDOW_MS = 90_000L
    }

    /**
     * Запускает AuthActivity (OAuth WebView как primary + Phone+Password как fallback).
     * Возвращает RESULT_OK если любой из способов авторизации успешен —
     * токен уже сохранён в ExchangeTokenStorage к этому моменту.
     *
     * #34: RESULT_OFFLINE_MODE (2) — пользователь выбрал «Офлайн-режим» на
     * LandingScreen. MainActivity переключается в guest-режим: показывает
     * OfflineManagerScreen без авторизации. Выйти из guest-режима можно
     * кнопкой «Войти» в TopAppBar офлайн-экрана.
     */
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLog.i("MainActivity", "AuthActivity result: ${result.resultCode}" +
            (if (lastLaunchWasSilent) " [was SILENT]" else " [was FULL]"))
        // Fix #112: AuthActivity закрылась — снимаем флаг, чтобы boot LaunchedEffect
        // мог перезапустить её если токен всё ещё невалиден.
        authActivityShowing = false
        // #SSO-GUARD-RESET: AuthActivity ВЕРНУЛСЯ (SSO-попытка завершена — успех/
        // отмена/провал). Сбрасываем timestamp #SSO-RECREATE-GUARD, чтобы следующий
        // launchAuth (boot-no-token / network-restored-no-token) НЕ блокировался на
        // 90 сек и пользователь не висел на бесконечном сплэше после возврата из
        // VK app. Гуард нужен только ПОКА пользователь подтверждает в VK app;
        // после return'а блокировка больше не нужна.
        lastAuthActivityLaunchedAt = 0L
        if (result.resultCode == AuthActivity.RESULT_OFFLINE_MODE) {
            // #34: guest-режим — показываем офлайн-менеджер без токена.
            isOfflineMode = true
        }
        // Fix #49 #SILENT-LOOP-BREAK: считаем SILENT-провалы. Если SILENT-запуск
        // завершился БЕЗ токена (не RESULT_OK) — инкрементим. При успехе — сброс.
        // Это заставит следующий launchAuth пойти в FULL режиме (см. 3 места
        // ниже где выставляется EXTRA_SILENT_MODE — все проверяют silentFailCount).
        if (result.resultCode == RESULT_OK) {
            if (silentFailCount > 0) {
                AppLog.i("MainActivity", "SILENT loop broken — silentFailCount $silentFailCount → 0 (Fix #49)")
            }
            silentFailCount = 0
            // Fix #176-auth-loop: успешный логин — сбрасываем флаг форсированного
            // FULL re-login. Если в будущем снова потребуется форсировать (новый
            // апдейт), SovaApp.onCreate выставит его заново.
            val appCtx = SovaApp.get(this@MainActivity)
            if (appCtx.forceFullReloginOnNextLaunch) {
                appCtx.forceFullReloginOnNextLaunch = false
                AppLog.i("MainActivity", "forceFullReloginOnNextLaunch reset after successful login (Fix #176-auth-loop)")
            }
        } else if (lastLaunchWasSilent && result.resultCode != AuthActivity.RESULT_OFFLINE_MODE) {
            silentFailCount++
            AppLog.w("MainActivity", "SILENT auth failed (${result.resultCode}) — " +
                "silentFailCount=$silentFailCount/$MAX_SILENT_FAILURES (Fix #49)" +
                (if (silentFailCount >= MAX_SILENT_FAILURES)
                    " → next launch will be FULL (visible)" else ""))
        }
        // Триггерим recompose независимо от результата —
        // если пользователь отменил, но токен уже был сохранён, покажем главный экран.
        authVersion++
    }

    /**
     * Счётчик версий авторизации. Любое изменение (login, logout, deep-link token)
     * инкрементирует это значение и заставляет Compose перечитать tokenStorage.
     * Без этого hasValidToken() не observable и UI не обновляется после OAuth.
     */
    private var authVersion by mutableIntStateOf(0)

    /**
     * #34: Guest-режим — пользователь нажал «Офлайн-режим» на экране авторизации.
     * Если true — показываем OfflineManagerScreen без проверки токена.
     * Сбрасывается в false кнопкой «Войти» в TopAppBar офлайн-экрана →
     * triggers AuthActivity relaunch.
     */
    private var isOfflineMode by mutableStateOf(false)

    /**
     * Fix #183: Оверлеи для guest-режима (офлайн без авторизации).
     *
     * В guest-режиме OfflineManagerScreen показывается напрямую (без SovaNavHost),
     * поэтому навигация к VideoPlayerScreen / StoryOfflinePlayerScreen /
     * OfflineAudioPlayerScreen невозможна через NavController. Эти переменные
     * хранят state оверлеев и обрабатываются в compose-дереве guest-режима.
     *
     * - [guestVideoOverlay]: видео для оверлей-плеера (VideoHolder используется
     *   для совместимости с authorized-режимом — но в guest-режиме читаем его
     *   через VideoHolder.active.collectAsState()).
     * - [guestStoryOverlay]: пара (ownerId, storyId) для StoryOfflinePlayerScreen.
     * - [guestAudioPlayerOpen]: флаг открытия OfflineAudioPlayerScreen.
     */
    private var guestStoryOverlay by mutableStateOf<Pair<Long, Int>?>(null)
    private var guestAudioPlayerOpen by mutableStateOf(false)

    /**
     * Fix #112: флаг «AuthActivity сейчас показывается».
     *
     * Предотвращает двойной запуск AuthActivity из boot LaunchedEffect.
     * Сценарий: AuthActivity запущена для silent re-login, boot LaunchedEffect
     * перезапускается (authVersion++ или токен стал невалиден) → без этого флага
     * boot запустил бы ВТОРУЮ AuthActivity поверх первой.
     *
     * Сбрасывается в false в authLauncher result callback (когда AuthActivity закрылась).
     */
    private var authActivityShowing by mutableStateOf(false)

    /**
     * Fix #230: timestamp последнего запуска AuthActivity (для троттлинга).
     * Если notifyTokenInvalidated срабатывает чаще раза в 20с — это признак
     * цикла (мертвый токен → AuthActivity → clipboard auto-save → мёртвый
     * токен → ...). Троттлинг разрывает цикл, давая юзеру шанс увидеть
     * стабильный UI вместо мелькания авторизации.
     *
     * Fix #250: 60с → 20с. 60с слишком долго для случая «токен только что
     * получен и сразу 1117» — юзер смотрит на зависшее приложение.
     */
    private var lastAuthActivityLaunchMs: Long = 0L

    /**
     * Fix #49 #SILENT-LOOP-BREAK: счётчик последовательных SILENT-провалов.
     *
     * Сценарий из логкэта 2026-08-03: мёртвый remixsid → AuthActivity запускается
     * в SILENT режиме (transparent theme) → WebView dispodee через ~118ms →
     * WebTokenAuth cancelled → RESULT_CANCELED → notifyTokenInvalidated tick →
     * снова SILENT (remixsid же есть!) → бесконечный цикл без видимого UI.
     *
     * Решение: считаем сколько раз подряд SILENT AuthActivity завершилась БЕЗ
     * токена. После [MAX_SILENT_FAILURES] (2) — форсируем FULL режим: НЕ
     * ставим EXTRA_SILENT_MODE даже если remixsid есть. Юзер увидит экран
     * входа и сможет перелогиниться вручную. Сбрасывается в 0 при успехе
     * (RESULT_OK) или при FULL-запуске (visible режим сам по себе разрывает
     * цикл). Не persisted — рестарт процесса = чистый старт, что нормально
     * (Fix #3 к тому времени уже почистил мёртвый remixsid).
     */
    private var silentFailCount: Int = 0
    private var lastLaunchWasSilent: Boolean = false

    /** Fix #49: после 2 SILENT-провалов подряд — FULL режим. */
    private val MAX_SILENT_FAILURES = 2

    /**
     * Fix #DOUBLE-FLICKER (§41.23): fingerprint последнего clipboard-токена,
     * сохранённого через [trySaveOAuthTokenFromClipboard]. Защита от двойного
     * сохранения одного и того же токена (network flicker → isOnlineFlow
     * эмитит повторно → collect блок → trySaveOAuthTokenFromClipboard снова).
     *
     * Также защищает от сценария «logout + clearPrimaryClip failed»: если
     * очистка буфера не сработала (permission/old Android), и network restore
     * fires после logout — без этой защиты мы бы пересохраннили stale token.
     * В logout handler (line ~660) мы устанавливаем fingerprint текущего буфера
     * ДО вызова clearPrimaryClip, чтобы даже при неудачной очистке повторное
     * сохранение было заблокировано.
     */
    private var lastSavedClipFingerprint: Int = 0

    /**
     * Fix #233 (P1): единая точка запуска AuthActivity. Обновляет
     * lastAuthActivityLaunchMs ВСЕГДА — чтобы throttle (Fix #230) работал
     * во всех 4 путях: boot, tokenInvalidation, logout, offline→login.
     * Раньше обновление было только в пути tokenInvalidation → auth loop
     * мог вернуться через logout/offline→login/boot.
     *
     * @param intent готовый Intent (с EXTRA_SILENT_MODE и др.)
     * @param reason метка для логов
     */
    private fun launchAuth(intent: Intent, reason: String) {
        val now = System.currentTimeMillis()
        // Fix #230 throttle: не запускаем AuthActivity чаще раза в 20с.
        // Кроме ручного logout — там пользователь явно нажал «Выйти»,
        // throttle тут только разозлит (он хочет немедленно войти обратно).
        //
        // Fix #250: уменьшили 60с → 20с. 60с слишком долго для случая
        // «токен только что получен и сразу 1117» — юзер смотрит на
        // зависшее приложение целую минуту. 20с достаточно чтобы разорвать
        // auth-loop (AuthActivity → 1117 → AuthActivity → ...) и при этом
        // не мучить юзера при реальном edge-case.
        // isManualAction: явное действие пользователя — throttle/guard отключены.
        //   - "logout": юзер нажал «Выйти», хочет немедленно войти обратно.
        //   - "offline-back-to-login": юзер нажал «Назад» в offline guest-режиме,
        //     ожидает возврат на экран авторизации. БЕЗ этого исключения SSO guard
        //     (90с) блокирует launchAuth → AuthActivity не запускается →
        //     hasValidToken()=false + isOfflineMode=false → ни одна ветка when{}
        //     не матчит → бесконечный StartupLoadingScreen (#OFFLINE-BACK-SPLASH).
        val isManualAction = reason == "logout" || reason == "offline-back-to-login"
        if (!isManualAction && lastAuthActivityLaunchMs > 0L && now - lastAuthActivityLaunchMs < 20_000L) {
            val waitMs = 20_000L - (now - lastAuthActivityLaunchMs)
            AppLog.w("MainActivity", "launchAuth($reason) throttled — last launch ${now - lastAuthActivityLaunchMs}ms ago (need ${waitMs}ms more). Skipping (Fix #230).")
            return
        }
        // #SSO-RECREATE-GUARD: если AuthActivity был запущен недавно (< 90 сек),
        // блокируем перезапуск. Защищает от loop после low-memory kill во время
        // VK-app SSO: система убивает AuthActivity+MainActivity → юзер возвращается
        // → MainActivity onCreate → isOnlineFlow/boot/token-invalidation триггерят
        // launchAuth → БЕЗ guard запустился бы НОВЫЙ AuthActivity с НОВЫМ QR,
        // ломая match с подтверждением юзера в VK app → бесконечный loop.
        //
        // Instance-throttle (Fix #230, 20с) НЕ помогает — lastAuthActivityLaunchMs
        // сбрасывается в 0L при recreate. Этот guard использует companion-object
        // timestamp, который переживает recreate.
        //
        // 90 сек > QR TTL VK (~60 сек) + время возврата из VK app. После истечения
        // guard пропускает один retry (если токен всё ещё невалиден).
        //
        // Manual logout исключён — юзер явно хочет перелогиниться немедленно.
        if (!isManualAction && lastAuthActivityLaunchedAt > 0L) {
            val sinceMs = now - lastAuthActivityLaunchedAt
            if (sinceMs < SSO_GUARD_WINDOW_MS) {
                AppLog.w("MainActivity", "launchAuth($reason) blocked — AuthActivity launched ${sinceMs / 1000}s ago (SSO in progress?, #SSO-RECREATE-GUARD). Skipping.")
                return
            }
        }
        lastAuthActivityLaunchMs = now
        lastAuthActivityLaunchedAt = now
        authActivityShowing = true
        // Fix #49: запоминаем был ли этот запуск SILENT — result-callback
        // инкрементит silentFailCount только если SILENT закончился неудачей.
        lastLaunchWasSilent = intent.getBooleanExtra(AuthActivity.EXTRA_SILENT_MODE, false)
        AppLog.i("MainActivity", "launchAuth($reason) — launching AuthActivity" +
            (if (lastLaunchWasSilent) " [SILENT]" else " [FULL]"))
        authLauncher.launch(intent)
    }

    /**
     * Fix #154 full forwarding: контент полученный через системный share
     * (ACTION_SEND). Когда не null — показываем ShareToChatSheet с выбором
     * диалога для пересылки. Очищается после отправки или отмены.
     *
     * Хранится как Compose state чтобы show/dismiss триггерил recompose.
     */
    private var pendingShareText by mutableStateOf<String?>(null)
    private var pendingShareUri by mutableStateOf<Uri?>(null)
    private var pendingShareMime by mutableStateOf<String?>(null)

    // Fix #208: peer_id диалога, который нужно открыть из push-уведомления.
    // Устанавливается в handleOpenChatIntent (onCreate или onNewIntent), а
    // SovaNavHost читает через LaunchedEffect и навигирует на ChatDetailScreen.
    private var pendingOpenChatPeerId by mutableStateOf<Long?>(null)
    private var pendingOpenChatTitle by mutableStateOf<String?>(null)

    /**
     * §42 #PUSH-NOTIFICATIONS: pending deep-link от tap на VK-уведомление
     * (лайк/комментарий/репост/ответ/подписка/упоминание/подарок/запись на стене).
     *
     * Устанавливается в [handleDeepLinkIntent] (onCreate или onNewIntent),
     * SovaNavHost читает через LaunchedEffect и навигирует на нужный Screen:
     *   OpenPost      → Screen.PostDetail
     *   OpenVideo     → Screen.VideoPlayer
     *   OpenUser      → Screen.UserProfile
     *   OpenCommunity  → Screen.Community
     *   OpenPhoto     → Screen.InternalBrowser (vk.com/photo URL)
     *   OpenNotifications → Screen.Messages (вкладка Уведомления)
     *
     * После навигации сбрасывается через [consumeDeepLink].
     */
    private var pendingDeepLink by mutableStateOf<re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction?>(null)

    /**
     * #29 (закрытие хвостов): флаг lockerOnBackground — был ли активити свёрнут.
     *
     * Логика:
     *  - onStop() → isBackgrounded = true (активити ушло в фон)
     *  - onResume() → если isBackgrounded && snap.lockerOnBackground && snap.lockerEnabled
     *    → запускаем LockerActivity (требование PIN при возврате из фона)
     *
     * Без этого флага LockerActivity показывался только при холодном старте приложения.
     */
    private var isBackgrounded = false

    /**
     * Fix #169: кэш последнего SovaPrefs Snapshot, обновляемый из Compose-подписки
     * (collectAsState в setContent). Используется в onResume() для мгновенной
     * проверки lockerOnBackground без блокирующего runBlocking { prefs.data.first() }
     * на main thread.
     *
     * Раньше onResume читал DataStore синхронно через runBlocking:
     *   kotlinx.coroutines.runBlocking { app.prefs.data.first() }
     * После длительного Doze (телефон заблокирован 1+ минут) in-memory cache
     * DataStore мог быть вытеснен → чтение с диска = 30-200мс блокировки UI.
     * Пользователь воспринимал это как «приложение подвисает при разблокировке».
     *
     * Теперь: в onResume мгновенно читаем [lastPrefsSnapshot] (volatile, O(1)).
     * Snapshot обновляется из setContent каждый раз когда Compose перечитывает
     * prefs (а это происходит на каждом изменении DataStore). Поскольку locker
     * prefs меняются крайне редко (только через настройки), snapshot практически
     * всегда актуален. В крайнем случае (snapshot ещё null — холодный старт) —
     * fallback на runBlocking, но это происходит один раз за сессию.
     */
    @Volatile
    private var lastPrefsSnapshot: re.pinok.data.local.SovaPrefs.Snapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i("MainActivity", "onCreate")
        handleOAuthIntent(intent)
        handleShareIntent(intent)
        handleOpenChatIntent(intent)
        handleDeepLinkIntent(intent)

        setContent {
            val app = SovaApp.get(this)
            val scope = rememberCoroutineScope()
            val snapshot by app.prefs.data.collectAsState(initial = null)
            val snap = snapshot
            var bootLocal by rememberSaveable { mutableStateOf(false) }

            // Fix #169: кэшируем snapshot для мгновенного доступа из onResume.
            // SideEffect вызывается после каждого успешного recomposition —
            // гарантирует что lastPrefsSnapshot актуален для следующего onResume.
            androidx.compose.runtime.SideEffect {
                if (snap != null) lastPrefsSnapshot = snap
            }

            // Читаем authVersion в composable scope — Compose подписывается на
            // MutableIntState и рекомпозирует при каждом authVersion++.
            val currentAuthVersion = authVersion

            // Fix #50-A: подписка на инвалидацию токена. VKApiClient.callInternal
            // инкрементирует tokenInvalidationTicks после tokenStorage.clear().
            // Без этого MainActivity остаётся на главном экране с пустым токеном
            // → все API вызовы возвращают null → пустая лента → БЕЛЫЙ ЭКРАН.
            val tokenInvalidationTick by app.tokenInvalidationTicks.collectAsState()
            // lastHandledTick хранит последний обработанный tick — чтобы не
            // запускать AuthActivity повторно для того же события. Инициализируем
            // текущим значением tick (обычно 0) — на первом composition нет реакции.
            var lastHandledTick by rememberSaveable { mutableIntStateOf(0) }

            SOVATheme(
                darkTheme = snap?.themeDark ?: true,
                dynamicColor = snap?.themeDynamic ?: false,
                monetHybrid = snap?.themeMonetHybrid ?: true,
                accentIndex = snap?.themeAccentIndex ?: 6,
                fontScale = snap?.fontScale ?: 100,
            ) {
                // LaunchedEffect'ы ниже срабатывают только когда snap уже загружен.
                // На этапе snap==null они пропускаются (return) — boot/LongPoll/auth
                // логика не имеет смысла пока prefs неизвестны.

                // Boot logic: запуск AuthActivity / LockerActivity при первом показе.
                // Срабатывает один раз (bootLocal guard). Рестартит при смене
                // authVersion (login/logout) и isOfflineMode.
                //
                // Fix #112 (сессия после долгого фона): guard теперь проверяет
                // ТОЖЕ валидность токена. Раньше `if (bootLocal) return` скипал
                // boot всегда после первого показа. Но bootLocal переживает смерть
                // процесса (rememberSaveable → SavedStateHandle). Сценарий бага:
                //   1. App в фоне долго → Android убивает процесс
                //   2. Пользователь возвращается → процесс пересоздаётся
                //   3. bootLocal=true (восстановлен) → boot скипается
                //   4. Токен истёк за время фона → hasValidToken()=false
                //   5. AuthActivity НЕ запускается → вечный loading screen
                //   6. Помогал только force-stop (очищает SavedStateHandle)
                //
                // Теперь: boot скипается ТОЛЬКО если bootLocal=true AND токен валиден.
                // Если токен невалиден (истёк/очищен) — boot выполняется заново,
                // запускает AuthActivity для silent re-login через remixsid (Fix #107).
                if (snap != null) {
                    LaunchedEffect(snap.lockerEnabled, snap.lockerPinHash, bootLocal, currentAuthVersion, isOfflineMode) {
                        // Скип только если уже загрузились AND токен жив.
                        // Если токен умер — boot должен перезапуститься.
                        if (bootLocal && app.tokenStorage.hasValidToken()) return@LaunchedEffect
                        // Предотвращаем двойной запуск AuthActivity (authLauncher уже активен).
                        if (authActivityShowing) return@LaunchedEffect
                        bootLocal = true

                        // #34: guest-режим — не запускаем AuthActivity, пользователь
                        // явно выбрал «Офлайн-режим».
                        if (isOfflineMode) {
                            AppLog.i("MainActivity", "Offline (guest) mode — skipping auth")
                            return@LaunchedEffect
                        }
                        if (!app.tokenStorage.hasValidToken()) {
                            // Fix #339: web_token истекает каждые ~15 мин. При холодном
                            // старте после простоя boot видел истёкший токен и запускал
                            // AuthActivity в ОБЫЧНОМ режиме → юзер видел WebView flash
                            // («часто приходится переходить в аккаунт»).
                            // Теперь: если есть сохранённый remixsid → передаём
                            // EXTRA_SILENT_MODE=true (как в token-invalidation пути).
                            // AuthActivity применит Theme.PinoK.Silent (transparent) и
                            // сделает silent re-login через CookieManager — юзер увидит
                            // предыдущий кадр MainActivity, а не WebView.
                            val hasRemixsid = !app.exchangeAuthRepository.remixsid().isNullOrBlank()
                            // Fix #49: после MAX_SILENT_FAILURES подряд — FULL режим,
                            // иначе мёртвый remixsid зацикливает SILENT-запуски.
                            // Fix #176-auth-loop: при форсированном FULL re-login
                            // (апдейт + протухший токен) — тоже FULL, иначе SILENT
                            // loop на мёртвом remixsid после установки поверх.
                            val useSilent = hasRemixsid && silentFailCount < MAX_SILENT_FAILURES
                                && !app.forceFullReloginOnNextLaunch
                            val intent = Intent(this@MainActivity, AuthActivity::class.java).apply {
                                if (useSilent) {
                                    putExtra(AuthActivity.EXTRA_SILENT_MODE, true)
                                    AppLog.i("MainActivity", "No token (expired after process restore) — silent re-login via remixsid (Fix #339)")
                                } else if (hasRemixsid) {
                                    AppLog.w("MainActivity", "No token — forcing FULL mode: silentFailCount=$silentFailCount/$MAX_SILENT_FAILURES" +
                                        (if (app.forceFullReloginOnNextLaunch) ", forceFullRelogin=true (Fix #176-auth-loop)" else "") +
                                        " (Fix #49)")
                                } else {
                                    AppLog.i("MainActivity", "No token (expired or cleared after process restore), launching AuthActivity")
                                }
                            }
                            launchAuth(intent, reason = "boot-no-token")
                            return@LaunchedEffect
                        }
                        if (snap.lockerEnabled && snap.lockerPinHash.isNotBlank()) {
                            AppLog.i("MainActivity", "Locker enabled, launching LockerActivity")
                            LockerActivity.launch(this@MainActivity)
                        }
                    }
                }

                // Sprint 1, P0-1: запуск/остановка LongPoll-цикла.
                // currentAuthVersion меняется при login/logout/deep-link-token →
                // этот LaunchedEffect перезапускается и синхронизирует состояние
                // LongPollClient с актуальным токеном.
                LaunchedEffect(currentAuthVersion) {
                    if (app.tokenStorage.hasValidToken()) {
                        AppLog.i("MainActivity", "Token valid — starting LongPoll")
                        app.longPollClient.start()
                        // #CALLS: автоподключение queuev4 для входящих звонков (LP 115).
                        app.startCallSignaling()
                        // Fix #340: поднимаем foreground-сервис чтобы удержать процесс
                        // живым в фоне — иначе Android Doze/kill убьёт LongPoll и
                        // push-уведомления перестанут приходить.
                        re.pinok.realtime.LongPollKeepAliveService.start(this@MainActivity)
                    } else {
                        AppLog.i("MainActivity", "No token — stopping LongPoll")
                        app.longPollClient.stop()
                        app.queuev4Client.stop()
                        re.pinok.realtime.LongPollKeepAliveService.stop(this@MainActivity)
                    }
                }

                // Fix #50-A: реактивная инвалидация токена. Если VKApiClient
                // очистил токен (refresh failed или error 5/1117 — Fix #96) —
                // перезапускаем AuthActivity, иначе пользователь видит пустую
                // ленту (белый экран) пока вручную не перезапустит приложение.
                // lastHandledTick защищает от повторного запуска для того же tick.
                LaunchedEffect(tokenInvalidationTick) {
                    if (tokenInvalidationTick <= lastHandledTick) return@LaunchedEffect
                    lastHandledTick = tokenInvalidationTick
                    if (tokenInvalidationTick == 0) return@LaunchedEffect
                    if (isOfflineMode) {
                        AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) but offline mode — skip AuthActivity relaunch")
                        return@LaunchedEffect
                    }
                    if (app.tokenStorage.hasValidToken()) {
                        AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) but token still valid — skip relaunch")
                        return@LaunchedEffect
                    }
                    // Fix #112: если AuthActivity уже показывается (например, из boot
                    // logic после process restore) — не запускаем вторую.
                    if (authActivityShowing) {
                        AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) but AuthActivity already showing — skip")
                        return@LaunchedEffect
                    }
                    // Fix #137: if ChatDetailScreen set suppressNextAuthRelaunch during
                    // a photo upload, skip the global AuthActivity launch — the chat will
                    // handle the error inline (showing a "session expired" dialog with
                    // "Перезайти"/"Остаться" buttons instead of hiding the chat).
                    //
                    // Single-shot: consumed here (reset to false) so a subsequent
                    // unrelated token invalidation (e.g. from LongPoll) still launches
                    // AuthActivity normally.
                    if (app.suppressNextAuthRelaunch) {
                        app.suppressNextAuthRelaunch = false
                        AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) but suppressNextAuthRelaunch=true — ChatDetailScreen will handle inline (Fix #137)")
                        return@LaunchedEffect
                    }
                    // Fix #217 (P1.2): time-based suppress AuthActivity relaunch.
                    // Если ChatDetailScreen (или другой экран) установил окно suppress
                    // — игнорируем tick. Это покрывает все API-вызовы (LongPoll, messages,
                    // photo upload) на время окна, пока silent refresh Path 1.5/2.5
                    // работает в фоне. Окно авто-протухает по времени — не нужно
                    // сбрасывать вручную.
                    val nowMs = System.currentTimeMillis()
                    if (app.suppressAuthRelaunchUntilMs > 0L && nowMs < app.suppressAuthRelaunchUntilMs) {
                        val remainingMs = app.suppressAuthRelaunchUntilMs - nowMs
                        AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) but " +
                            "suppressAuthRelaunchUntilMs active (${remainingMs}ms remaining) — inline handling (Fix #217)")
                        return@LaunchedEffect
                    }
                    // Fix #107: silent re-login — если в storage есть сохранённый
                    // remixsid (Fix #106 не удалил его при clearAccessToken),
                    // запускаем AuthActivity в silent mode. Пользователь НЕ увидит
                    // экран логина — AuthActivity сразу откроет WebView и попробует
                    // переобменять remixsid на свежий web_token в фоне.
                    //
                    // Это решает исходную проблему (A): «авторизация пропадает через
                    // ~час» — теперь вместо экрана логина происходит автоматический
                    // re-login через WebView (занимает 2-5 секунд).
                    //
                    // Если remixsid тоже устарел — AuthActivity покажет LANDING
                    // для ручного входа (fallback в AuthScreen.kt).
                    val hasRemixsid = !app.exchangeAuthRepository.remixsid().isNullOrBlank()
                    // Fix #230: чистим буфер обмена если там OAuth-ссылка.
                    // AuthActivity.onWindowFocusChanged (Fix #195b) автоматически
                    // сохраняет токен из буфера при получении фокуса. Если там лежит
                    // тот же мёртвый токен, который мы только что инвалидировали —
                    // новый instance AuthActivity тут же его пересохранит → цикл.
                    try {
                        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                        if (clipboard != null && clipboard.hasPrimaryClip()) {
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
                                if (text != null && text.contains("access_token=", ignoreCase = true)
                                    && text.contains("user_id=", ignoreCase = true)) {
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                                    AppLog.i("MainActivity", "Cleared clipboard (contained OAuth token) on auto-invalidation — breaks auth loop (Fix #230)")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.w("MainActivity", "Clipboard clear on invalidation failed: ${e.message}")
                    }
                    val intent = Intent(this@MainActivity, AuthActivity::class.java).apply {
                        // Fix #49: после MAX_SILENT_FAILURES подряд — FULL режим,
                        // иначе мёртвый remixsid зацикливает SILENT-запуски.
                        // Fix #176-auth-loop: при форсированном FULL re-login
                        // (апдейт + протухший токен) — тоже FULL, иначе SILENT
                        // loop на мёртвом remixsid после установки поверх.
                        val useSilent = hasRemixsid && silentFailCount < MAX_SILENT_FAILURES
                            && !app.forceFullReloginOnNextLaunch
                        if (useSilent) {
                            putExtra(AuthActivity.EXTRA_SILENT_MODE, true)
                            AppLog.i("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) — silent re-login via remixsid (Fix #107)")
                        } else if (hasRemixsid) {
                            AppLog.w("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) — forcing FULL mode: silentFailCount=$silentFailCount/$MAX_SILENT_FAILURES" +
                                (if (app.forceFullReloginOnNextLaunch) ", forceFullRelogin=true (Fix #176-auth-loop)" else "") +
                                " (Fix #49)")
                        } else {
                            AppLog.w("MainActivity", "Token invalidated (tick=$tokenInvalidationTick) — no remixsid, full re-login required")
                        }
                    }
                    // Fix #112 + Fix #233 (P1): launchAuth единая точка запуска —
                    // обновляет lastAuthActivityLaunchMs + проверяет throttle (Fix #230).
                    // Throttle теперь централизован в launchAuth, не дублируется тут.
                    launchAuth(intent, reason = "token-invalidation")
                }

                // #NET-RESTORE-AUTH-RETRY (Fix #341):
                // Сценарий: пользователь запустил приложение БЕЗ интернета.
                //   1. boot: hasValidToken()=false (токен истёк за время offline)
                //      → launchAuth(silent mode via remixsid)
                //   2. AuthActivity → VkAuthWebViewScreen → m.vk.ru не грузится
                //      (нет сети) → 30с таймаут → onBack → AuthActivity finish
                //   3. authActivityShowing=false, но hasValidToken() всё ещё false
                //      → MainActivity показывает StartupLoadingScreen()
                //   4. LaunchedEffect(boot...) НЕ перезапускается (ключи те же)
                //   5. Пользователь подключает Wi-Fi → NetworkObserver._isOnline=true
                //   6. БЕЗ этого фикса — НИКТО не перезапускает AuthActivity →
                //      приложение висит на StartupLoadingScreen навсегда.
                //
                // Фикс: подписываемся на isOnlineFlow. Когда online=true И
                // токена нет И AuthActivity не показывается И не offline-mode —
                // перезапускаем AuthActivity (silent mode если есть remixsid).
                // Throttle (20с) уже есть в launchAuth, дополнительно debounce
                // 3с чтобы не дёргать на каждом мелькании сети.
                LaunchedEffect(Unit) {
                    var lastRetryMs = 0L
                    app.networkObserver.isOnlineFlow.collect { online ->
                        if (!online) return@collect
                        // debounce: не retry чаще раза в 3 сек
                        val now = System.currentTimeMillis()
                        if (now - lastRetryMs < 3_000L) return@collect
                        // только если токена реально нет
                        if (app.tokenStorage.hasValidToken()) return@collect
                        // не мешаем офлайн-режиму
                        if (isOfflineMode) {
                            AppLog.i("MainActivity", "Network restored but offline mode — skip auth retry (#341)")
                            return@collect
                        }
                        // не запускаем второй AuthActivity
                        if (authActivityShowing) {
                            AppLog.i("MainActivity", "Network restored but AuthActivity already showing — skip (#341)")
                            return@collect
                        }
                        // Fix #DOUBLE-FLICKER (§41.23): если в буфере обмена УЖЕ есть
                        // OAuth token (юзер только что вернулся из Chrome, скопировав
                        // URL с access_token из редиректа blank.html) — сохраняем
                        // токен напрямую, БЕЗ запуска AuthActivity.
                        //
                        // Сценарий без фикса:
                        //   1. AuthActivity #1 (boot) → юзер жмёт «Войти через Chrome»
                        //   2. Chrome foreground, AuthActivity #1 onStop
                        //   3. Система убивает MainActivity (memory pressure) ИЛИ
                        //      «Don't keep activities» — authActivityShowing сбрасывается
                        //   4. Юзер логинится в Chrome, копирует token URL, возвращается
                        //   5. MainActivity onCreate (recreated) → isOnlineFlow=true
                        //      → все проверки проходят → launchAuth → AuthActivity #2
                        //   6. AuthActivity #2 моргает WebView 0.5с прежде чем
                        //      onWindowFocusChanged найдёт токен в буфере → UX-баг
                        //      «моргание авторизации дважды».
                        //
                        // С фиксом: на шаге 5 проверяем буфер — если там OAuth token,
                        // вызываем saveOAuthTokenFromPayload напрямую. AuthActivity #2
                        // не запускается, моргания нет. saveOAuthTokenFromPayload
                        // триггерит authVersion++ → Compose перерисует главный экран.
                        if (trySaveOAuthTokenFromClipboard()) {
                            AppLog.i("MainActivity", "Clipboard has OAuth token — saved directly, skip AuthActivity launch (#DOUBLE-FLICKER)")
                            return@collect
                        }
                        lastRetryMs = now
                        val hasRemixsid = !app.exchangeAuthRepository.remixsid().isNullOrBlank()
                        // Fix #49: после MAX_SILENT_FAILURES подряд — FULL режим.
                        // Fix #176-auth-loop: при форсированном FULL re-login — тоже FULL.
                        val useSilent = hasRemixsid && silentFailCount < MAX_SILENT_FAILURES
                            && !app.forceFullReloginOnNextLaunch
                        AppLog.i("MainActivity", "Network restored + no token — retry auth via ${if (useSilent) "silent remixsid" else "full login"} (#341)" +
                            (if (hasRemixsid && !useSilent) " [forced FULL: silentFailCount=$silentFailCount/$MAX_SILENT_FAILURES" +
                                (if (app.forceFullReloginOnNextLaunch) ", forceFullRelogin=true (Fix #176-auth-loop)" else "") +
                                ", Fix #49]" else ""))
                        val retryIntent = Intent(this@MainActivity, AuthActivity::class.java).apply {
                            if (useSilent) putExtra(AuthActivity.EXTRA_SILENT_MODE, true)
                        }
                        launchAuth(retryIntent, reason = "network-restored-no-token")
                    }
                }

                // Fix #96: Box-обёртка верхнего уровня — DraggableLogFab всегда
                // поверх ЛЮБОГО контента (loading / нет токена / главный экран /
                // offline). Раньше FAB показывался только при hasValidToken() →
                // на белом экране при холодном старте (snap==null) или во время
                // запуска AuthActivity пользователь не мог отправить логи.
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        // Холодный старт: DataStore ещё не загрузил prefs.
                        // Показываем loading вместо пустого белого экрана.
                        snap == null -> StartupLoadingScreen()

                        // Авторизованный режим — главный навигационный граф.
                        app.tokenStorage.hasValidToken() -> {
                            // Запрос ВСЕХ необходимых runtime-разрешений.
                            // На Android 13+ без POST_NOTIFICATIONS не работают уведомления
                            // foreground-сервисов (загрузка музыки/видео, плеер).
                            RequestAllPermissionsEffect()
                            // Task 20: CompositionLocal DI-контракта экранов звонков —
                            // провайдер SovaApp (реализует CallsDependencies).
                            // Этап 3.7-1: рядом провайдер контейнера фото — ЕДИНАЯ
                            // точка сборки (канон §3.2 Этап В).
                            CompositionLocalProvider(
                                LocalCallsDeps provides app,
                                LocalPhotosDeps provides app,
                            ) {
                            SovaNavHost(
                                initialRoute = snap.lastRoute,
                                pendingOpenChatPeerId = pendingOpenChatPeerId,
                                pendingOpenChatTitle = pendingOpenChatTitle,
                                onOpenChatConsumed = { consumeOpenChat() },
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { consumeDeepLink() },
                                onLogout = {
                                    scope.launch {
                                        // Fix #182: полный logout — очищаем ВСЕ хранилища
                                        // авторизации, не только SharedPreferences.
                                        //
                                        // Раньше: signOut() чистил только storage (access_token,
                                        // remixsid, exchange_token), но CookieManager (WebView
                                        // cookies) сохранял remixsid → AuthActivity auto-relogin
                                        // через ExternalBrowserAuth.tryFindExistingAuth() →
                                        // пользователь нажимал «Выйти» и через секунду снова
                                        // был залогинен.
                                        //
                                        // Теперь: перед signOut() останавливаем LongPoll +
                                        // плеер, после signOut() CookieManager + storage.clear()
                                        // полностью очищают сессию, затем запускаем AuthActivity.

                                        // 1. Останавливаем LongPoll — иначе он держит сессию
                                        //    активной HTTP-запросами и может пересоздать
                                        //    access_token через exchange.
                                        try {
                                            app.longPollClient.stop()
                                            AppLog.i("MainActivity", "Logout: LongPollClient stopped")
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "Logout: LongPollClient.stop failed: ${e.message}")
                                        }
                                        // Fix #340: останавливаем keep-alive foreground-сервис —
                                        // после logout нет смысла удерживать процесс живым.
                                        try {
                                            re.pinok.realtime.LongPollKeepAliveService.stop(this@MainActivity)
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "Logout: LongPollKeepAliveService.stop failed: ${e.message}")
                                        }

                                        // 2. Останавливаем воспроизведение музыки — не нужно
                                        //    держать foreground-сервис активным после logout.
                                        //    НЕ вызываем release() — PlayerConnection.init()
                                        //    вызывается только в SovaApp.onCreate(), и повторный
                                        //    init после re-login не произойдёт. Просто pause.
                                        try {
                                            re.pinok.media.PlayerConnection.pauseIfPlaying()
                                            AppLog.i("MainActivity", "Logout: PlayerConnection paused")
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "Logout: PlayerConnection.pauseIfPlaying failed: ${e.message}")
                                        }

                                        // 3. Полный signOut: VK server logout (fire-and-forget)
                                        //    + clearAllVkCookies (CookieManager) + storage.clear().
                                        app.exchangeAuthRepository.signOut()

                                        // #LOGOUT-SINGLETON-CLEAR: очистка auth-UI singleton'ов.
                                        // signOut() чистит cookies+storage, но НЕ трогает
                                        // in-memory state. PendingAuthResult хранит токен SSO-входа
                                        // (silent_token / direct access_token из shouldOverrideUrlLoading).
                                        try {
                                            re.pinok.auth.PendingAuthResult.clear()
                                            AppLog.i("MainActivity", "Logout: cleared PendingAuthResult singleton")
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "Logout: PendingAuthResult clear failed: ${e.message}")
                                        }

                                        // 4. WebViewDatabase.clearFormData() убран: deprecated с
                                        //    API 18 и no-op на современных устройствах. Form data
                                        //    в WebView больше не хранится — autocomplete обрабатывается
                                        //    системным Android Autofill framework. CookieManager и
                                        //    storage.clear() в signOut() полностью очищают сессию.

                                        // Fix #197: очистить буфер обмена если там OAuth-ссылка.
                                        // После external browser auth в буфере остаётся URL вида
                                        // https://oauth.vk.com/blank.html#access_token=...&user_id=...
                                        // Fix #195b (onWindowFocusChanged clipboard auto-detect)
                                        // при запуске AuthActivity после logout находит этот URL и
                                        // автоматически входит обратно → «выйти невозможно».
                                        // Решение: перед запуском AuthActivity проверить буфер и
                                        // очистить если содержит access_token + user_id.
                                        try {
                                            val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                                                as android.content.ClipboardManager
                                            if (cm.hasPrimaryClip()) {
                                                val clip = cm.primaryClip
                                                val text = clip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
                                                if (text != null &&
                                                    text.contains("access_token=", ignoreCase = true) &&
                                                    text.contains("user_id=", ignoreCase = true)) {
                                                    // Fix #DOUBLE-FLICKER (§41.23): запоминаем fingerprint
                                                    // ДО clearPrimaryClip — если очистка не сработает
                                                    // (permission/old Android), trySaveOAuthTokenFromClipboard
                                                    // всё равно пропустит этот токен по fingerprint match.
                                                    lastSavedClipFingerprint = text.hashCode()
                                                    // API 28+: clearPrimaryClip — заменяет буфер на пустой ClipData.
                                                    cm.clearPrimaryClip()
                                                    AppLog.i("MainActivity", "Logout: cleared clipboard (contained OAuth token URL) — Fix #197 + fingerprint set (#DOUBLE-FLICKER)")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            AppLog.w("MainActivity", "Logout: clipboard clear failed: ${e.message}")
                                        }

                                        bootLocal = false
                                        authVersion++
                                        // Fix #233 (P1): launchAuth с reason=logout —
                                        // throttle ОТКЛЮЧЕН для ручного logout (пользователь
                                        // явно хочет войти обратно, не ждём 60с).
                                        launchAuth(Intent(this@MainActivity, AuthActivity::class.java), reason = "logout")
                                    }
                                },
                                // #247: выход из приложения целиком (с сохранением
                                // авторизации). finishAffinity() закрывает все Activity
                                // в стеке — Android завершает процесс. При следующем
                                // запуске SovaApp.onCreate() находит сохранённый
                                // access_token в tokenStorage → пользователь сразу
                                // попадает в ленту без повторного логина.
                                // НЕ вызываем onLogout / signOut — токены и cookies
                                // остаются в хранилище.
                                onExitApp = {
                                    // Сначала останавливаем плеер и LongPoll —
                                    // иначе foreground-сервис может пережить
                                    // finishAffinity() и держать процесс активным.
                                    try {
                                        re.pinok.media.PlayerConnection.pauseIfPlaying()
                                    } catch (_: Exception) { }
                                    try {
                                        app.longPollClient.stop()
                                    } catch (_: Exception) { }
                                    // Fix #340: останавливаем keep-alive сервис при полном выходе.
                                    try {
                                        re.pinok.realtime.LongPollKeepAliveService.stop(this@MainActivity)
                                    } catch (_: Exception) { }
                                    finishAffinity()
                                },
                            )
                            } // CompositionLocalProvider(LocalCallsDeps, LocalPhotosDeps)
                        }

                        // #34: Guest-режим — пользователь нажал «Офлайн-режим» на
                        // экране авторизации. Показываем OfflineManagerScreen без
                        // токена: доступны просмотр и воспроизведение уже скачанных
                        // аудио/видео (PlayerConnection инициализирован в SovaApp).
                        // Кнопка «Назад» → выход из guest-режима → relaunch AuthActivity
                        // (возврат на экран авторизации). Кнопка «Войти» в TopAppBar
                        // удалена — её функцию выполняет «Назад».
                        //
                        // Fix #183: раньше колбэки onPlayVideo/onPlayStory/onOpenPlayer
                        // не передавались (default null/{}) → тапы по видео/историям/
                        // кнопке плеера игнорировались. Теперь передаём колбэчи, которые
                        // открывают оверлеи ниже (overlayVideo, StoryOfflinePlayer,
                        // OfflineAudioPlayer). Это позволяет воспроизводить кэш без
                        // авторизации.
                        isOfflineMode -> {
                            // Fix #183: подписываемся на VideoHolder.active чтобы
                            // оверлей VideoPlayerScreen показывался в guest-режиме.
                            val overlayVideo by VideoHolder.active.collectAsState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    OfflineManagerScreen(
                                        onBack = {
                                            AppLog.i("MainActivity", "Back from offline guest mode → auth screen")
                                            isOfflineMode = false
                                            bootLocal = false  // позволить LaunchedEffect перезапуститься
                                            authVersion++
                                            // Fix #233 (P1): launchAuth единая точка запуска.
                                            launchAuth(Intent(this@MainActivity, AuthActivity::class.java), reason = "offline-back-to-login")
                                        },
                                        // Fix #183: открываем оверлей VideoPlayerScreen.
                                        // VideoHolder — singleton object, общий с authorized-режимом.
                                        onPlayVideo = { ownerId, videoId, title ->
                                            AppLog.i("MainActivity", "Guest: play video owner=$ownerId id=$videoId")
                                            VideoHolder.open(Video(
                                                id = videoId,
                                                ownerId = ownerId,
                                                title = title,
                                                description = null,
                                                duration = 0,
                                                date = 0,
                                            ))
                                        },
                                        // Fix #183: открываем StoryOfflinePlayerScreen как
                                        // fullscreen overlay. Читает локальный .mp4 через
                                        // StoryVideoDownloadManager.getLocalFile — без сети.
                                        onPlayStory = { ownerId, storyId ->
                                            AppLog.i("MainActivity", "Guest: play story owner=$ownerId id=$storyId")
                                            guestStoryOverlay = ownerId to storyId
                                        },
                                        // Fix #183: открываем OfflineAudioPlayerScreen как
                                        // fullscreen overlay. Читает скачанные треки из
                                        // TrackDownloadManager и играет через PlayerConnection
                                        // (инициализирован в SovaApp, не требует auth token).
                                        onOpenPlayer = {
                                            AppLog.i("MainActivity", "Guest: open offline audio player")
                                            guestAudioPlayerOpen = true
                                        },
                                    )
                                }

                                // Fix #183: оверлей VideoPlayerScreen — показывается
                                // поверх OfflineManagerScreen когда VideoHolder.active != null.
                                // VideoPlayerScreen сам проверяет VideoDownloadManager.getLocalFile
                                // и играет локальный файл без сети (LaunchedEffect return'ит
                                // раньше если localFile != null). Требует SovaApp (для app.apiClient),
                                // но apiClient не вызывается если видео скачано.
                                overlayVideo?.let { video ->
                                    BackHandler(enabled = true) {
                                        VideoHolder.close()
                                    }
                                    VideoPlatformRouter(
                                        video = video,
                                        onBack = { VideoHolder.close() },
                                    )
                                }

                                // Fix #183: оверлей StoryOfflinePlayerScreen — fullscreen.
                                // Полностью офлайн: читает StoryVideoDownloadManager.
                                guestStoryOverlay?.let { (ownerId, storyId) ->
                                    BackHandler(enabled = true) {
                                        guestStoryOverlay = null
                                    }
                                    StoryOfflinePlayerScreen(
                                        ownerId = ownerId,
                                        storyId = storyId,
                                        onBack = { guestStoryOverlay = null },
                                    )
                                }

                                // Fix #183: оверлей OfflineAudioPlayerScreen — fullscreen.
                                // PlayerConnection + TrackDownloadManager работают без auth token.
                                if (guestAudioPlayerOpen) {
                                    BackHandler(enabled = true) {
                                        guestAudioPlayerOpen = false
                                    }
                                    OfflineAudioPlayerScreen(
                                        onBack = { guestAudioPlayerOpen = false },
                                    )
                                }
                            }
                        }

                        // Нет токена, не offline — AuthActivity запускается в
                        // LaunchedEffect выше. Пока она не показалась — loading,
                        // чтобы не было белого экрана без FAB.
                        else -> StartupLoadingScreen()
                    }

                    // FAB логов — управляется настройкой showLogFab (SovaPrefs).
                    // По умолчанию виден в debug-сборке, скрыт в release.
                    // Явная null-проверка snap вместо Elvis — см. стиль проекта.
                    val showLogFab = if (snap != null) snap.showLogFab else BuildConfig.DEBUG
                    if (showLogFab) {
                        DraggableLogFab(onClick = { LogDialogState.show() })
                    }
                }
                LogViewerDialog()

                // Fix #154 full forwarding: показываем пикер чата если есть
                // pending share контент. После выбора диалога и отправки (или
                // отмены) — очищаем state, sheet закрывается.
                val shareText = pendingShareText
                val shareUri = pendingShareUri
                val shareMime = pendingShareMime
                if (shareText != null || shareUri != null) {
                    ShareToChatSheet(
                        sharedText = shareText,
                        sharedStreamUri = shareUri,
                        sharedMimeType = shareMime,
                        onDismiss = {
                            pendingShareText = null
                            pendingShareUri = null
                            pendingShareMime = null
                        },
                        onSuccess = {
                            pendingShareText = null
                            pendingShareUri = null
                            pendingShareMime = null
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri: Uri? = intent?.data ?: return
        AppLog.i("MainActivity", "Deep link received: $uri")
        // Fix #187: поддерживаем OAuth redirect от внешнего браузера (sova2://oauth).
        // VK implicit flow кладёт access_token в URL fragment (#access_token=...).
        // Также поддерживаем error-redirects (sova2://oauth#error=...).
        val fragment = uri?.fragment
        val query = uri?.query
        // Проверяем fragment сначала (success case), потом query (error case).
        val payload = when {
            fragment?.contains("access_token=") == true -> fragment
            fragment?.contains("error=") == true -> fragment
            query?.contains("access_token=") == true -> query
            query?.contains("error=") == true -> query
            else -> return
        }
        saveOAuthTokenFromPayload(payload, source = "deep-link")
    }

    /**
     * Fix #191: сохраняет OAuth-токен из строки с параметрами.
     *
     * Переиспользуется тремя путями:
     *  1. Deep link sova2://oauth#access_token=... (handleOAuthIntent)
     *  2. Share intent: браузер → «Поделиться» → PinoK (handleShareIntent)
     *  3. Clipboard auto-detect в AuthActivity (onResume проверяет буфер)
     *
     * @param payload строка вида "access_token=vk1.a.XXX&user_id=123&expires_in=0"
     *   (fragment после # или query после ?)
     * @param source описание источника для логов
     * @return true если токен сохранён, false если ошибка/невалидный payload
     */
    private fun saveOAuthTokenFromPayload(payload: String, source: String): Boolean {
        try {
            val params = payload.split("&").mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()

            // Обработка ошибки от VK (user denied, invalid client, etc.)
            val error = params["error"]
            val errorDesc = params["error_description"]
            if (error != null) {
                AppLog.w("MainActivity", "OAuth error ($source): $error — $errorDesc")
                return false
            }

            val at = params["access_token"] ?: return false
            val uid = params["user_id"]?.toLongOrNull() ?: return false
            val expIn = params["expires_in"]?.toLongOrNull() ?: 0L
            val scope = params["scope"]
            val expiresAt = if (expIn == 0L) 0L else System.currentTimeMillis() + expIn * 1000L

            val app = SovaApp.get(this)
            app.tokenStorage.save(
                re.pinok.data.local.TokenStorage.Token(
                    accessToken = at,
                    userId = uid,
                    expiresAt = expiresAt,
                    scope = scope,
                ),
            )
            AppLog.i("MainActivity", "OAuth token saved ($source) for user $uid")
            authVersion++  // триггерим recompose

            // В фоне получаем exchange_token для refresh-поддержки.
            app.appScope.launch {
                try {
                    val state = app.exchangeAuthRepository.saveOAuthToken(at, uid)
                    if (state is re.pinok.auth.exchange.AuthState.Success) {
                        AppLog.i("MainActivity", "OAuth ($source): exchange_token obtained & saved for user $uid")
                    } else if (state is re.pinok.auth.exchange.AuthState.Error) {
                        AppLog.w("MainActivity", "OAuth ($source): getExchangeToken failed (token still works, just no refresh): ${state.message}")
                    }
                } catch (e: Exception) {
                    AppLog.w("MainActivity", "OAuth ($source): exchange_token background fetch failed: ${e.message} (access_token still works)")
                }
            }
            return true
        } catch (e: Exception) {
            AppLog.e("MainActivity", "Failed to parse token ($source)", e)
            return false
        }
    }

    /**
     * Fix #DOUBLE-FLICKER (§41.23): проверяет буфер обмена на наличие OAuth
     * token URL (format: `...#access_token=vk1.a.XXX&user_id=123&expires_in=0`
     * или `access_token=...&user_id=...`). Если найден — сохраняет токен через
     * [saveOAuthTokenFromPayload] и возвращает true.
     *
     * Используется в network-restore LaunchedEffect: если юзер только что
     * вернулся из Chrome со скопированным token URL, сохраняем напрямую БЕЗ
     * запуска AuthActivity (которое моргает WebView 0.5с прежде чем
     * onWindowFocusChanged найдёт токен — UX-баг «моргание дважды»).
     *
     * Возвращает false если: буфер пуст, не содержит OAuth token, парсинг
     * неудачен, или saveOAuthTokenFromPayload вернул false.
     *
     * НЕ вызывает clearClipboard() — это делает отдельный механизм в
     * AuthActivity после успешного входа. Здесь только read+parse+save.
     */
    private fun trySaveOAuthTokenFromClipboard(): Boolean {
        try {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            if (cm == null) return false
            if (!cm.hasPrimaryClip()) return false
            val clip = cm.primaryClip
            if (clip == null) return false
            if (clip.itemCount == 0) return false
            val item = clip.getItemAt(0)
            if (item == null) return false
            val text = item.coerceToText(this).toString()
            if (text.isBlank()) return false
            // Быстрая проверка без парсинга — avoids substring allocation.
            if (!text.contains("access_token=", ignoreCase = true)) return false
            if (!text.contains("user_id=", ignoreCase = true)) return false
            // Fix #DOUBLE-FLICKER: fingerprint-защита от повторного сохранения
            // того же токена (network flicker / logout + clearPrimaryClip failed).
            val fingerprint = text.hashCode()
            if (fingerprint == lastSavedClipFingerprint) {
                AppLog.d("MainActivity", "Clipboard OAuth token already saved (fingerprint match) — skip (#DOUBLE-FLICKER)")
                return false
            }
            // Извлекаем payload: после '#' или '?' или вся строка если raw params.
            val trimmed = text.trim()
            val hashIdx = trimmed.lastIndexOf('#')
            val payload: String = if (hashIdx >= 0 && hashIdx < trimmed.length - 1) {
                trimmed.substring(hashIdx + 1)
            } else {
                val qIdx = trimmed.indexOf('?')
                if (qIdx >= 0 && qIdx < trimmed.length - 1) {
                    trimmed.substring(qIdx + 1)
                } else {
                    trimmed
                }
            }
            AppLog.i("MainActivity", "Clipboard OAuth token detected — saving directly (#DOUBLE-FLICKER)")
            val saved = saveOAuthTokenFromPayload(payload, source = "clipboard-pre-check-#DOUBLE-FLICKER")
            if (saved) {
                lastSavedClipFingerprint = fingerprint
            }
            return saved
        } catch (e: Exception) {
            AppLog.w("MainActivity", "trySaveOAuthTokenFromClipboard failed: ${e.message}")
            return false
        }
    }

    override fun onResume() {
        super.onResume()
        val app = SovaApp.get(this)
        // Пробуждаем LongPoll при возврате на передний план —
        // сбрасываем backoff, очищаем stale TCP, прерываем текущий wait
        // и отменяем in-flight poll call (Fix #112).
        app.longPollClient.notifyResumed()

        // Fix #112: проактивная проверка — истёк ли токен по времени пока
        // приложение было в фоне. Если да — немедленно сообщаем MainActivity
        // (notifyTokenInvalidated → boot LaunchedEffect запустит AuthActivity
        // для silent re-login через remixsid). Без этого мы ждём пока LongPoll
        // сделает следующий API запрос и VK вернёт error 5 — а это до 45с
        // если LongPoll был в doRequest.
        if (isBackgrounded && !isOfflineMode) {
            app.checkTokenValidity()
        }

        // #BG-AUTH-LOOP-FIX (2026-08-05): LongPollKeepAliveService при истёкшем
        // токене в фоне делает bringMainActivityToForeground (вместо прямого
        // запуска AuthActivity из background — который ломался из-за singleTask
        // + потери EXTRA_SILENT_MODE + отсутствия window focus). MainActivity
        // поднимается, но LaunchedEffect(tokenInvalidationTick) НЕ сработает
        // повторно для уже обработанного tick (rememberSaveable lastHandledTick
        // сохраняет значение across process foreground/background).
        //
        // Поэтому здесь: если токен невалиден И AuthActivity не показывается —
        // запускаем launchAuth() вручную. Это foreground launch, AuthActivity
        // получит window focus, WebView factory вызовется, loadUrl загрузит
        // m.vk.ru, tryReadWebToken прочитает localStorage → auth завершится.
        //
        // Throttle (20с) уже есть в launchAuth (Fix #230) — не будет zацикливаться.
        if (isBackgrounded && !isOfflineMode && !authActivityShowing) {
            if (!app.tokenStorage.hasValidToken()) {
                val hasRemixsid = !app.exchangeAuthRepository.remixsid().isNullOrBlank()
                AppLog.i("MainActivity", "onResume (#BG-AUTH-LOOP-FIX): token invalid after background — launching AuthActivity (${if (hasRemixsid) "SILENT" else "FULL"})")
                val intent = Intent(this, AuthActivity::class.java).apply {
                    if (hasRemixsid) {
                        putExtra(AuthActivity.EXTRA_SILENT_MODE, true)
                    }
                }
                launchAuth(intent, reason = "bg-auth-loop-resume")
            }
        }

        // #29 (закрытие хвостов): lockerOnBackground — если активити вернулось
        // из фона (isBackgrounded=true) и включена блокировка при уходе в фон
        // (lockerOnBackground=true) И включен PIN (lockerEnabled=true),
        // показываем LockerActivity. Без этого — LockerActivity показывался
        // только при холодном старте приложения.
        //
        // Fix #169: читаем prefs из [lastPrefsSnapshot] (volatile, O(1)) вместо
        // блокирующего runBlocking { prefs.data.first() }. После длительного Doze
        // DataStore in-memory cache мог быть вытеснен → чтение с диска 30-200мс
        // на main thread = пользователь видит «подвисание» при разблокировке.
        // Snapshot обновляется из setContent SideEffect каждый recomposition —
        // для locker prefs (меняются только через настройки) практически всегда
        // актуален. Если null (холодный старт, ещё не было recomposition) —
        // fallback на runBlocking, но это раз в сессию, не на каждом resume.
        if (isBackgrounded) {
            isBackgrounded = false
            val t0 = System.currentTimeMillis()
            val cached = lastPrefsSnapshot
            if (cached != null) {
                if (cached.lockerEnabled && cached.lockerOnBackground && cached.lockerPinHash.isNotBlank()) {
                    AppLog.i("MainActivity", "Locker on background (cached snapshot, ${System.currentTimeMillis() - t0}ms): launching LockerActivity")
                    re.pinok.locker.LockerActivity.launch(this)
                } else {
                    AppLog.d("MainActivity", "onResume locker check (cached, ${System.currentTimeMillis() - t0}ms): no locker needed")
                }
            } else {
                // Холодный старт: snapshot ещё не пришёл из DataStore. Редкий случай.
                // runBlocking здесь приемлем — это НЕ путь «разблокировки экрана»
                // (на разблокировке snapshot уже есть с предыдущей сессии).
                kotlinx.coroutines.runBlocking {
                    val snap = app.prefs.data.first()
                    lastPrefsSnapshot = snap
                    if (snap.lockerEnabled && snap.lockerOnBackground && snap.lockerPinHash.isNotBlank()) {
                        AppLog.i("MainActivity", "Locker on background (cold-start fallback, ${System.currentTimeMillis() - t0}ms): launching LockerActivity")
                        re.pinok.locker.LockerActivity.launch(this@MainActivity)
                    }
                }
            }
        }

        // Fix #169: плеер — мгновенный wake-up. Если PlayerService был убит
        // системой во время Doze (low memory), controller в PlayerConnection
        // остаётся stale. notifyResumed() проверит контроллер и при необходимости
        // запустит переподключение в фоне — UI не ждёт.
        //
        // #AUTH-WEBVIEW-STARVATION: НО если токена нет (auth flow) — notifyResumed
        // запускает PlayerService recreate (~150-300мс main-thread block:
        // ExoPlayer.Builder + MediaSession.Builder + AudioEffectsEngine.attachOnce
        // создает 5 AudioEffect на main thread). Chromium WebView рендерер в это
        // время не может подключиться → "cr_ChildProcessConn: Failed to establish
        // the service connection" → m.vk.ru не грузится → 2FA страница пустая.
        // Auth плеер не нужен — пропускаем будильник PlayerService.
        //
        // #AUTH-WEBVIEW-STARVATION-V2: SovaApp.onCreate skip'ит PlayerConnection.init
        // в auth flow (нет токена → нет init). После успешного auth токен появляется,
        // но PlayerConnection ещё не initialized. notifyResumed проверяет initialized
        // и skip'ает если false → музыка не работает после первого входа.
        // Решение: если токен есть НО PlayerConnection не initialized → вызываем init
        // (идемпотентен, безопасен). Затем notifyResumed.
        if (app.tokenStorage.hasValidToken()) {
            try {
                // #AUTH-WEBVIEW-STARVATION-V2: lazy init если SovaApp.onCreate skip'ул
                if (!re.pinok.media.PlayerConnection.isInitialized()) {
                    AppLog.i("MainActivity", "PlayerConnection не инициализирован (auth flow закончился) — init сейчас, #AUTH-WEBVIEW-STARVATION-V2")
                    re.pinok.media.PlayerConnection.init(this)
                }
                re.pinok.media.PlayerConnection.notifyResumed()
            } catch (e: Exception) {
                AppLog.w("MainActivity", "PlayerConnection.notifyResumed failed: ${e.message}")
            }
        } else {
            AppLog.d("MainActivity", "skip PlayerConnection.notifyResumed — no token (auth flow), #AUTH-WEBVIEW-STARVATION")
        }
    }

    override fun onStop() {
        super.onStop()
        // Фиксируем что активити ушло в фон — onResume проверит этот флаг.
        isBackgrounded = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLog.i("MainActivity", "onNewIntent: $intent")
        handleOAuthIntent(intent)
        handleShareIntent(intent)
        handleOpenChatIntent(intent)
        handleDeepLinkIntent(intent)
        setIntent(intent)
    }

    /**
     * Fix #154: обработка ACTION_SEND — контент, пошаренный из других приложений.
     *
     * PinoK зарегистрирован как share target (text/plain, image) в манифесте.
     * Когда пользователь делится текстом/ссылкой/картинкой из браузера/галереи/
     * другого приложения и выбирает PinoK в share-меню → вызывается этот метод.
     *
     * Текущая реализация: логирует полученный контент + показывает toast.
     * TODO: full forwarding — открыть экран выбора чата и отправить через
     * messages.send / uploadAndSendPhoto. Пока просто принимаем и логируем,
     * чтобы PinoK появлялся в share-меню наравне с SOVA V RE / Telegram / VK.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type ?: ""
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        AppLog.i("MainActivity", "Share received: type=$type text=${text?.take(200)} streamUri=$streamUri")

        // Fix #191: если shared text — это OAuth redirect от внешнего браузера
        // (содержит access_token= и user_id=), обрабатываем как вход, а не как
        // share-to-chat. Пользователь в браузере: «Поделиться» → выбрал PinoK →
        // мы получили URL вида https://oauth.vk.com/blank.html#access_token=...
        if (text != null && containsOAuthToken(text)) {
            val payload = extractOAuthPayload(text)
            if (payload != null) {
                AppLog.i("MainActivity", "Share: OAuth token detected — saving (Fix #191)")
                val saved = saveOAuthTokenFromPayload(payload, source = "share")
                if (saved) {
                    Toast.makeText(this, "PinoK: вход выполнен ✓", Toast.LENGTH_LONG).show()
                    return
                }
            }
        }

        // Fix #154 full forwarding: сохраняем контент в state → setContent
        // покажет ShareToChatSheet с выбором диалога и отправкой через
        // messages.send (текст) или uploadAndSendPhoto (картинка).
        // Если контент пустой — показываем Toast с ошибкой.
        if (text.isNullOrBlank() && streamUri == null) {
            Toast.makeText(this, "PinoK: пустой контент для шаринга", Toast.LENGTH_LONG).show()
            return
        }
        pendingShareText = text
        pendingShareUri = streamUri
        pendingShareMime = type.ifBlank { null }
        AppLog.i("MainActivity", "Share queued for picker: text=${text?.length ?: 0} chars, uri=$streamUri, mime=$type")
    }

    /**
     * Fix #191: проверяет, содержит ли строка OAuth-токен.
     * Ищет "access_token=" И "user_id=" в любом месте строки
     * (в fragment после #, в query после ?, или просто как параметры).
     */
    private fun containsOAuthToken(text: String): Boolean {
        return text.contains("access_token=", ignoreCase = true) &&
            text.contains("user_id=", ignoreCase = true)
    }

    /**
     * Fix #208: обработка ACTION_OPEN_CHAT из push-уведомления.
     *
     * MessageNotifier создаёт PendingIntent с action=re.pinok.action.OPEN_CHAT
     * и extras peer_id + title. Когда пользователь тапает по уведомлению:
     *  - Если приложение было свёрнуто → onNewIntent (Activity уже жива)
     *  - Если приложение было убито → onCreate (новая Activity)
     *
     * Метод читает peer_id из extras и сохраняет в pendingOpenChatPeerId/state.
     * SovaNavHost через LaunchedEffect подхватывает это и навигирует на
     * ChatDetailScreen. После навигации SovaNavHost вызывает onOpenChatConsumed
     * → state сбрасывается в null.
     */
    private fun handleOpenChatIntent(intent: Intent?) {
        if (intent?.action != re.pinok.realtime.MessageNotifier.ACTION_OPEN_CHAT) return
        val peerId = intent.getLongExtra(re.pinok.realtime.MessageNotifier.EXTRA_PEER_ID, -1L)
        if (peerId <= 0) {
            AppLog.w("MainActivity", "OPEN_CHAT intent: invalid peerId=$peerId")
            return
        }
        val title = intent.getStringExtra(re.pinok.realtime.MessageNotifier.EXTRA_TITLE) ?: ""
        AppLog.i("MainActivity", "OPEN_CHAT intent: peerId=$peerId title='$title'")
        pendingOpenChatPeerId = peerId
        pendingOpenChatTitle = title
    }

    /** Fix #208: сброс pending-chat после того, как SovaNavHost навигировал. */
    fun consumeOpenChat() {
        pendingOpenChatPeerId = null
        pendingOpenChatTitle = null
    }

    /**
     * §42 #PUSH-NOTIFICATIONS: обработка deep-link intent от tap на VK-уведомление.
     *
     * VkNotificationsNotifier строит PendingIntent с action из [re.pinok.realtime.VkUrlDeepLinker]:
     *   ACTION_OPEN_POST / ACTION_OPEN_PHOTO / ACTION_OPEN_VIDEO /
     *   ACTION_OPEN_USER / ACTION_OPEN_COMMUNITY / ACTION_OPEN_NOTIFICATIONS.
     *
     * §47 #URL-INTENT-FILTER: также обрабатывает системный ACTION_VIEW с VK URL
     * (https://vk.com/wall-123_456) — пользователь тапает ссылку в браузере/
     * Telegram, Android предлагает PinoK, URL парсится через
     * VkUrlDeepLinker.deepLinkFromUrl().
     *
     * Метод читает action + extras (или action + data URL) и сохраняет в
     * [pendingDeepLink]. SovaNavHost через LaunchedEffect подхватывает и
     * навигирует. После навигации SovaNavHost вызывает [consumeDeepLink] →
     * state сбрасывается.
     *
     * Если action не распознан — no-op (не логируем, т.к. onNewIntent получает
     * ВСЕ intents включая MAIN/LAUNCHER, и спам логов был бы бесполезным).
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val deepLink: re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction
        when (action) {
            // §47 #URL-INTENT-FILTER: системный ACTION_VIEW с VK URL data.
            // Пользователь тапнул на vk.com/wall-123_456 в браузере/Telegram →
            // Android предложил PinoK (intent-filter в manifest) → открыли app.
            // Парсим URL через VkUrlDeepLinker.deepLinkFromUrl().
            android.content.Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                if (url.isNullOrBlank()) {
                    AppLog.w("MainActivity", "ACTION_VIEW: no data URL — skip")
                    return
                }
                val parsed = re.pinok.realtime.VkUrlDeepLinker.deepLinkFromUrl(url)
                if (parsed == null) {
                    AppLog.w("MainActivity", "ACTION_VIEW: URL not recognized as VK permalink: $url")
                    return
                }
                AppLog.i("MainActivity", "ACTION_VIEW: url='$url' → $parsed")
                deepLink = parsed
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_POST -> {
                val ownerId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_OWNER_ID, 0L)
                val postId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_ITEM_ID, 0L)
                if (ownerId == 0L || postId == 0L) {
                    AppLog.w("MainActivity", "OPEN_POST intent: invalid ownerId=$ownerId postId=$postId")
                    return
                }
                // §42.4 #PUSH-DEEPLINK: ID комментария для скролла (reply/comment).
                val commentId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_COMMENT_ID, 0L)
                AppLog.i("MainActivity", "OPEN_POST intent: ownerId=$ownerId postId=$postId commentId=$commentId")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenPost(ownerId, postId, commentId)
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_PHOTO -> {
                val ownerId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_OWNER_ID, 0L)
                val photoId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_ITEM_ID, 0L)
                if (ownerId == 0L || photoId == 0L) {
                    AppLog.w("MainActivity", "OPEN_PHOTO intent: invalid ownerId=$ownerId photoId=$photoId")
                    return
                }
                // §42.4 #PUSH-DEEPLINK: URL фото для нативного PhotoViewer.
                val photoUrl = intent.getStringExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_PHOTO_URL)
                val accessKey = intent.getStringExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_ACCESS_KEY)
                AppLog.i("MainActivity", "OPEN_PHOTO intent: ownerId=$ownerId photoId=$photoId hasUrl=${!photoUrl.isNullOrBlank()}")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenPhoto(ownerId, photoId, photoUrl, accessKey)
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_VIDEO -> {
                val ownerId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_OWNER_ID, 0L)
                val videoId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_ITEM_ID, 0L)
                if (ownerId == 0L || videoId == 0L) {
                    AppLog.w("MainActivity", "OPEN_VIDEO intent: invalid ownerId=$ownerId videoId=$videoId")
                    return
                }
                AppLog.i("MainActivity", "OPEN_VIDEO intent: ownerId=$ownerId videoId=$videoId")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenVideo(ownerId, videoId)
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_USER -> {
                val userId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_USER_ID, 0L)
                if (userId <= 0) {
                    AppLog.w("MainActivity", "OPEN_USER intent: invalid userId=$userId")
                    return
                }
                AppLog.i("MainActivity", "OPEN_USER intent: userId=$userId")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenUser(userId)
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_COMMUNITY -> {
                val groupId = intent.getLongExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_GROUP_ID, 0L)
                if (groupId >= 0) {
                    AppLog.w("MainActivity", "OPEN_COMMUNITY intent: invalid groupId=$groupId (must be negative)")
                    return
                }
                AppLog.i("MainActivity", "OPEN_COMMUNITY intent: groupId=$groupId")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenCommunity(groupId)
            }
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_NOTIFICATIONS -> {
                AppLog.i("MainActivity", "OPEN_NOTIFICATIONS intent")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenNotifications
            }
            // §49.6 Sprint VK-ID-1.6: deep-link из security-alert notification.
            re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_DEVICES -> {
                AppLog.i("MainActivity", "OPEN_DEVICES intent")
                deepLink = re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenDevices
            }
            else -> return  // не deep-link intent — пропускаем тихо
        }
        pendingDeepLink = deepLink
    }

    /** §42: сброс pending-deep-link после того, как SovaNavHost навигировал. */
    fun consumeDeepLink() {
        pendingDeepLink = null
    }

    /**
     * Fix #191: извлекает OAuth payload (параметры после # или ?) из строки.
     *
     * Принимает полный URL, фрагмент, или просто параметры.
     * Возвращает строку вида "access_token=...&user_id=...&expires_in=...".
     */
    private fun extractOAuthPayload(text: String): String? {
        val trimmed = text.trim()
        // Ищем "#" (fragment) — OAuth success redirect.
        val hashIdx = trimmed.lastIndexOf('#')
        if (hashIdx >= 0 && hashIdx < trimmed.length - 1) {
            return trimmed.substring(hashIdx + 1)
        }
        // Ищем "?" (query) — на случай error-redirect.
        val qIdx = trimmed.indexOf('?')
        if (qIdx >= 0 && qIdx < trimmed.length - 1) {
            return trimmed.substring(qIdx + 1)
        }
        // Нет ни # ни ? — возможно юзер вставил только "access_token=...&user_id=...".
        if (trimmed.contains("access_token=")) {
            return trimmed
        }
        return null
    }

}

/**
 * Fix #96: Loading-экран для холодного старта и для окна между запуском
 * AuthActivity и её первым кадром.
 *
 * Заменяет пустой белый экран, который раньше показывался пока DataStore
 * загружает prefs (snap == null). Без этого пользователь видел белый экран
 * без кнопки логирования при каждом запуске приложения.
 *
 * Не блокирует UI-поток — только статичный лого + spinner. DataStore грузится
 * в своём Dispatchers.IO, Compose подписывается через collectAsState.
 */
@Composable
private fun StartupLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "PinoK",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
