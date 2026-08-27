package re.pinok.auth.exchange

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import re.pinok.SovaApp
import re.pinok.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * #SESSION-COOKIES-BG-REFRESH: WorkManager worker для периодического (раз в 6ч)
 * фонового sync'а session cookies из CookieManager → storage.
 *
 * ЗАЧЕМ: `backfillRemixsidFromCookieManager` вызывается только в момент логина.
 * После логина VK может ротейтить `remixsid`/`p`/`remixnsid` (security events,
 * server-side rotation). CookieManager (WebView) обновляется при любой web-навигации,
 * storage — нет. Через несколько дней storage содержит стейловые cookies → при
 * смене сети `silentRefreshViaRemixsid` шлёт устаревший Cookie header → VK
 * отбрасывает → полный re-login.
 *
 * Worker ловит ротэйты пока app в фоне (или в foreground, но пользователь не
 * переключал экраны). Дополняет два других триггера:
 *   - Hook #1: после успешного silentRefreshViaRemixsid (в ExchangeAuthRepository).
 *   - Hook #2: на app foreground ON_RESUME (ProcessLifecycleOwner в SovaApp).
 *
 * Worker запускается только если есть сеть (CONNECTED) — без сети CookieManager
 * не обновляется, sync бесполезен.
 *
 * `refreshSessionCookiesFromCookieManager` — patch-семантика (null = не трогать),
 * сохраняет только изменившиеся cookies. Если все 3 совпадают — no-op write.
 *
 * НЕ запускается до первого логина (storage.accessToken() == null) — нет смысла
 * sync'ить cookies если пользователь ещё не вошёл.
 */
class CookieRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SovaApp
            if (app == null) {
                AppLog.w(TAG, "CookieRefreshWorker: applicationContext is not SovaApp — skip")
                return Result.success()
            }
            // exchangeAuthRepository — lateinit. Если приложение только запускается
            // и worker стартовал раньше инициализации — пропускаем (следующий tick).
            if (!app.isExchangeAuthRepositoryInitialized()) {
                AppLog.d(TAG, "CookieRefreshWorker: exchangeAuthRepository not yet initialized — skip this tick")
                return Result.retry()
            }
            val repo = app.exchangeAuthRepository

            // Пропускаем если пользователь не залогинен — нет смысла sync'ить cookies.
            if (!repo.hasValidAccessToken()) {
                AppLog.d(TAG, "CookieRefreshWorker: no valid access_token — user not logged in, skip")
                return Result.success()
            }

            val result = repo.refreshSessionCookiesFromCookieManager(forceLogOnNoop = true)
            AppLog.i(TAG, "CookieRefreshWorker: done — " +
                "remixsid=${if (result.remixsidChanged) "rotated" else "same"}, " +
                "p=${if (result.pChanged) "rotated" else "same"}, " +
                "remixnsid=${if (result.remixnsidChanged) "rotated" else "same"}, " +
                "allThree=${result.hadAllThree}")
            Result.success()
        } catch (e: Exception) {
            AppLog.w(TAG, "CookieRefreshWorker failed: ${e.message}")
            // retry — возможно transient (CookieManager race, storage locked)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CookieRefreshWorker"
        private const val WORK_NAME = "pinok_cookie_refresh_periodic"

        /**
         * Планирует periodic worker: раз в 6 часов, только с сетью.
         *
         * Идемпотентен: повторный вызов с [ExistingPeriodicWorkPolicy.KEEP]
         * оставляет существующий график (не создаёт дубликаты).
         *
         * Интервал 6ч выбран как баланс:
         *   - VK ротейтит p/remixnsid ~раз в пару недель → 6ч более чем достаточно.
         *   - WorkManager гарантирует выполнение в течение ~15ч окне (Doze/Battery Saver).
         *   - Частота не садит батарею (~4 запуска/день, каждый < 50мс на CookieManager read).
         *
         * min interval WorkManager = 15 мин — 6ч в 24× больше, ОК.
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    // НЕ требуем battery not low — cookie sync лёгкий (1 CookieManager read,
                    // 0-1 storage write). Может работать и на низком заряде.
                    .build()

                val request = PeriodicWorkRequestBuilder<CookieRefreshWorker>(
                    6, TimeUnit.HOURS,
                    // flex period 30 мин: WorkManager запустит в окне [5h30m, 6h],
                    // давая системе батчить с другими work'ами (батареи эффективнее).
                    30, TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,  // не перезаписывать если уже запланирован
                    request,
                )
                AppLog.i(TAG, "schedule: periodic cookie-refresh work enqueued (every 6h, network=CONNECTED)")
            } catch (e: Exception) {
                AppLog.w(TAG, "schedule failed: ${e.message}")
            }
        }

        /**
         * Отменяет periodic worker (вызывается при logout/clearAll если нужно).
         * Обычно НЕ вызывается — worker безопасно no-op'ит если пользователь не залогинен.
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                AppLog.i(TAG, "cancel: periodic cookie-refresh work cancelled")
            } catch (e: Exception) {
                AppLog.w(TAG, "cancel failed: ${e.message}")
            }
        }
    }
}
