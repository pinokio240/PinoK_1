package re.pinok.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import re.pinok.util.AppLog

/**
 * #VK-SSO-PENDING-RESULT: хранит access_token если он получен пока AuthActivity
 * была уничтожена (Don't keep activities).
 *
 * **Сценарий:** WebView жива (процесс не убит), JS-polling продолжается в фоне.
 * VK app подтверждает QR → страница редиректит на access_token →
 * shouldOverrideUrlLoading срабатывает на СТАРОМ WebViewClient (с closures
 * на старый viewModel). Старый viewModel уже cleared (viewModelScope cancelled)
 * → submitOAuthToken не выполняется. Токен теряется.
 *
 * **Решение:** При перехвате токена сохраняем его в этот singleton. Новый
 * AuthActivity в onResume проверяет PendingAuthResult.consume() — если есть
 * токен, вызывает viewModel.submitOAuthToken на НОВОМ viewModel.
 *
 * TTL = 10 минут.
 *
 * P2-12 #AUTH-AUDIT: вынесен из AuthActivity.kt в отдельный файл.
 */
object PendingAuthResult {
    private const val TAG = "PendingAuthResult"
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var userId: Long = 0L
    @Volatile
    private var savedAt: Long = 0L

    /** Сохранить токен (вызывается из shouldOverrideUrlLoading, может быть старый WebViewClient). */
    fun save(token: String, uid: Long) {
        accessToken = token
        userId = uid
        savedAt = System.currentTimeMillis()
        AppLog.i(TAG, "saved: access_token (user_id=$uid) — waiting for AuthActivity.onResume to pick up")
    }

    /** Возвращает (token, userId) если валиден, и очищает. Иначе null. */
    fun consume(): Pair<String, Long>? {
        val token = accessToken ?: return null
        val age = System.currentTimeMillis() - savedAt
        if (age > MAX_AGE_MS) {
            AppLog.w(TAG, "expired (age=${age}ms) — discarding token")
            clear()
            return null
        }
        AppLog.i(TAG, "consume: returning access_token (user_id=$userId, age=${age}ms)")
        clear()
        return token to userId
    }

    fun clear() {
        accessToken = null
        userId = 0L
        savedAt = 0L
    }
}

/**
 * #SSO-EXCHANGE-GLOBAL-SCOPE — глобальный CoroutineScope для silent_token exchange.
 *
 * ПРОБЛЕМА: shouldOverrideUrlLoading ловит silent_token из редиректа VK ID и
 * запускает exchange (HTTP POST auth.getAuthData). РАНЬШЕ exchange запускался
 * в `coroutineScope` (rememberCoroutineScope VkAuthWebViewScreen). Но при
 * retention WebView (#VK-SSO-WEBVIEW-RETAIN / #2FA-RETAIN-ON-BACKGROUND) и
 * пересоздании AuthActivity системой — старый composable disposed → его
 * coroutineScope CANCELLED → exchange отменяется на полпути → токен теряется.
 *
 * РЕШЕНИЕ: exchange запускается в этом глобальном scope, который переживает
 * lifecycle Activity/composable. Exchange — короткий HTTP-запрос (1-3 сек),
 * результат сохраняется в PendingAuthResult (singleton), новый AuthActivity
 * onResume / polling LaunchedEffect подбирает токен.
 *
 * SupervisorJob — чтобы одна неудачная exchange не отменяла другие.
 * Dispatchers.IO — network-bound.
 *
 * P2-12 #AUTH-AUDIT: вынесен из AuthActivity.kt в отдельный файл.
 */
object SsoExchangeScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
}
