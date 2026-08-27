package re.pinok.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import re.pinok.R
import re.pinok.SovaApp
import re.pinok.auth.AuthActivity
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog

/**
 * Fix #340: Foreground-сервис, удерживающий процесс приложения живым в фоне.
 *
 * Проблема: FCM/Firebase в проекте нет → LongPoll — единственный realtime-канал
 * доставки сообщений. LongPoll живёт в [re.pinok.SovaApp] (Application) и работает
 * пока жив процесс. Android Doze / memory pressure убивают фоновый процесс через
 * несколько минут после ухода приложения в фон → LongPoll умирает → push-уведомления
 * перестают приходить, пока пользователь сам не откроет приложение. BootReceiver
 * также не перезапускал LongPoll после перезагрузки устройства.
 *
 * Решение: foreground-сервис с `type=remoteMessaging` удерживает процесс от
 * убийства системой. Сервис сам НЕ выполняет LongPoll-работу — [LongPollClient]
 * уже запущен в `SovaApp.onCreate()`. Сервис просто держит процесс живым, чтобы
 * LongPoll мог продолжать опрос VK LongPoll server в фоне.
 *
 * Дополнительно: если в фоне токен истёк (web_token ~15 мин), а ни одна Activity
 * не на переднем плане — сервис делает headless silent re-login через
 * [re.pinok.auth.exchange.ExchangeAuthRepository.ensureFreshToken] (Path 1.5:
 * remixsid HTTP). Без этого при cold-boot с истёкшим токеном LongPoll получал
 * error 5 и простаивал до первого ручного открытия приложения.
 *
 * Жизненный цикл:
 *  - start: [re.pinok.ui.MainActivity] при валидном токене (рядом с
 *    `LongPollClient.start()`), либо [re.pinok.locker.BootReceiver] после
 *    загрузки устройства (если есть token/remixsid).
 *  - stop: `MainActivity` при logout (рядом с `LongPollClient.stop()`).
 *
 * Уведомление: IMPORTANCE_LOW (без звука / heads-up), отдельный канал
 * «Фоновая работа». Тап → открывает `MainActivity`.
 *
 * Примечание: тип `remoteMessaging` НЕ подпадает под 6-часовой Android 14+
 * timeout на foreground-сервисы (в отличие от `dataSync`/`mediaProcessing`) —
 * это позволяет держать LongPoll активным постоянно, как у мессенджеров без FCM.
 */
class LongPollKeepAliveService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var refreshJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.i(TAG, "onCreate: starting foreground + headless refresh observer")
        startForegroundCompat()
        startHeadlessRefreshObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Убеждаемся, что foreground поднят (на случай re-delivery после kill).
        startForegroundCompat()
        // Covers boot case: процесс поднят сервисом (BootReceiver), MainActivity
        // не запускалась → LongPollClient.start() из LaunchedEffect не отработал.
        try {
            val app = SovaApp.get(this)
            if (app.tokenStorage.hasValidToken()) {
                app.longPollClient.start()
                AppLog.i(TAG, "onStartCommand: LongPollClient ensured running")
            } else {
                AppLog.i(TAG, "onStartCommand: token not valid — LongPoll not started, awaiting refresh")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "onStartCommand: ensure LongPoll failed: ${e.message}")
        }
        // START_STICKY — Android перезапустит сервис если процесс был убит.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        refreshJob = null
        AppLog.i(TAG, "onDestroy: keep-alive service stopped")
        super.onDestroy()
    }

    /**
     * Headless silent re-login: наблюдает за [SovaApp.tokenInvalidationTicks].
     *
     * Если ни одна Activity не на переднем плане → вызываем
     * [re.pinok.auth.exchange.ExchangeAuthRepository.ensureFreshToken] (Path 1.5:
     * remixsid HTTP). При успехе — будим LongPoll (`notifyResumed` сбрасывает
     * backoff и прерывает текущий wait → следующий loop iteration возьмёт
     * свежий токен из storage). При неудаче — bring MainActivity to foreground,
     * а MainActivity при resume поймает `tokenInvalidationTick` и запустит
     * AuthActivity через `launchAuth()` в **foreground** с корректным
     * `EXTRA_SILENT_MODE`.
     *
     * #BG-AUTH-LOOP-FIX (2026-08-05, лог 10:17:41-10:23:21):
     * РАНЬШЕ сервис вызывал `startActivity(AuthActivity, SILENT_MODE=true)` из
     * background. На Android 10+ background activity launch из foreground
     * service разрешён, НО:
     *   1. `launchMode="singleTask"` у AuthActivity + `FLAG_ACTIVITY_NEW_TASK`
     *      из Service → система **переиспользует** существующий экземпляр
     *      (если он в задаче MainActivity) и вызывает `onNewIntent` (который
     *      НЕ был реализован) → `EXTRA_SILENT_MODE` **теряется** →
     *      `silentMode=false` в onCreate → AuthScreen начинает с
     *      `AuthPhase.LANDING` → WebView factory НЕ вызывается →
     *      `tryReadWebToken` не запускается → auth-loop.
     *   2. Даже если silentMode доходит — AuthActivity не получает window
     *      focus (background launch) → Compose не рисует → `AndroidView.factory`
     *      не вызывается → WebView не создаётся → `onStop` через 100мс →
     *      `ensureFreshToken` успевает провалиться по всем paths, но WebView
     *      даже не загрузился.
     *
     * Логката (PID 27041, 22 цикла за 5 мин 40 сек): каждый цикл — onCreate
     * AuthActivity → 122мс → onStop. `factory INVOKED` и `loadUrl:` НИ РАЗУ
     * не вызваны. `onCreate — SILENT mode (transparent theme)` НИ РАЗУ не
     * написано (silentMode=false в onCreate несмотря на putExtra(true)).
     *
     * ФИКС: сервис НЕ запускает AuthActivity напрямую. Сервис bring'ит
     * **MainActivity** to foreground (через startActivity с `CLEAR_TOP |
     * SINGLE_TOP`). MainActivity при resume видит `tokenInvalidationTick >
     * lastHandledTick` и запускает AuthActivity через `launchAuth()` — это
     * **foreground** launch (MainActivity в foreground), `EXTRA_SILENT_MODE`
     * доходит корректно, AuthActivity получает focus, WebView factory
     * вызывается, `loadUrl` грузит m.vk.ru, `tryReadWebToken` читает
     * localStorage → auth завершается.
     *
     * Если Activity на переднем плане — отдаём обработку `MainActivity`
     * (его `LaunchedEffect(tokenInvalidationTick)` запустит AuthActivity).
     */
    private fun startHeadlessRefreshObserver() {
        refreshJob = scope.launch {
            val app = SovaApp.get(this@LongPollKeepAliveService)
            var lastHandled = app.tokenInvalidationTicks.value
            app.tokenInvalidationTicks.collectLatest { tick ->
                if (tick <= lastHandled) return@collectLatest
                lastHandled = tick
                if (tick == 0) return@collectLatest
                AppLog.i(TAG, "Token invalidated (tick=$tick) — checking foreground state")
                if (app.isAnyActivityForeground()) {
                    AppLog.i(TAG, "Activity is foreground — letting MainActivity handle re-login")
                    return@collectLatest
                }
                AppLog.i(TAG, "No foreground activity — attempting headless silent re-login")
                try {
                    val token = app.exchangeAuthRepository.ensureFreshToken()
                    if (token != null) {
                        AppLog.i(TAG, "Headless silent re-login OK — waking LongPoll")
                        app.longPollClient.notifyResumed()
                    } else {
                        AppLog.w(TAG, "Headless silent re-login returned null — bringing MainActivity to foreground for AuthActivity launch (#BG-AUTH-LOOP-FIX)")
                        bringMainActivityToForeground()
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Headless silent re-login exception: ${e.message} — bringing MainActivity to foreground (#BG-AUTH-LOOP-FIX)")
                    bringMainActivityToForeground()
                }
            }
        }
    }

    /**
     * #BG-AUTH-LOOP-FIX: bring MainActivity to foreground.
     *
     * MainActivity при resume поймает `tokenInvalidationTick` (через
     * `LaunchedEffect(tokenInvalidationTick)`) и запустит AuthActivity через
     * `launchAuth()` — это foreground launch, `EXTRA_SILENT_MODE` дойдёт
     * корректно, AuthActivity получит window focus.
     *
     * `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`:
     *   - `NEW_TASK` — обязательный для startActivity из Service.
     *   - `CLEAR_TOP` — если MainActivity уже в задаче, очищает всё сверху
     *     неё и доставляет `onNewIntent` (MainActivity реализует onNewIntent).
     *   - `SINGLE_TOP` — если MainActivity уже на вершине, не создаёт новый
     *     экземпляр, вызывает `onNewIntent` (быстрее, без recreate).
     *
     * На Android 10+ background activity launch из foreground service
     * (тип `remoteMessaging`) разрешён — MainActivity поднимется даже если
     * экран выключен (в зависимости от lock screen политики).
     *
     * Throttle (15с): защита от loop если MainActivity не может подняться
     * (например экран заблокирован и lock-screen policy запрещает bg launch).
     * 15с = SovaApp.notifyTokenInvalidated throttle — не чаще чем ticks.
     * MainActivity.launchAuth сам имеет throttle 20с (Fix #230) — двойная
     * защита от zацикливания.
     */
    @Volatile
    private var lastBringToFrontMs: Long = 0L

    private fun bringMainActivityToForeground() {
        val now = System.currentTimeMillis()
        if (now - lastBringToFrontMs < 15_000L) {
            AppLog.w(TAG, "bringMainActivityToForeground throttled — last attempt ${now - lastBringToFrontMs}ms ago (need 15s). Tick will be re-handled by MainActivity on resume.")
            return
        }
        lastBringToFrontMs = now
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            AppLog.i(TAG, "bringMainActivityToForeground: startActivity(MainActivity, CLEAR_TOP|SINGLE_TOP) — MainActivity will launchAuth() on resume (#BG-AUTH-LOOP-FIX)")
        } catch (e: Exception) {
            AppLog.w(TAG, "bringMainActivityToForeground failed: ${e::class.java.simpleName}: ${e.message}")
        }
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PinoK")
            .setContentText("Получение сообщений в фоне")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(buildContentIntent())
            .build()
        // Android 14+ (API 34+): foreground service должен стартовать с указанием
        // типа. remoteMessaging не подпадает под 6-часовой timeout.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, type)
        } catch (e: Exception) {
            AppLog.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Фоновое получение сообщений"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "LongPollKeepAlive"
        private const val CHANNEL_ID = "bg_keepalive"
        private const val CHANNEL_NAME = "Фоновая работа"
        private const val NOTIFICATION_ID = 7777

        /**
         * Запустить сервис (idempotent — повторный `startForegroundService` просто
         * обновит `onStartCommand`). Безопасно вызывать из foreground (MainActivity)
         * и из `BOOT_COMPLETED` receiver (exempt от background-start restrictions
         * на Android 12+).
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, LongPollKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                AppLog.i(TAG, "start: keep-alive service requested")
            } catch (e: Exception) {
                AppLog.w(TAG, "start failed: ${e.message}")
            }
        }

        /** Остановить сервис (idempotent). */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LongPollKeepAliveService::class.java))
                AppLog.i(TAG, "stop: keep-alive service stopped")
            } catch (e: Exception) {
                AppLog.w(TAG, "stop failed: ${e.message}")
            }
        }
    }
}
