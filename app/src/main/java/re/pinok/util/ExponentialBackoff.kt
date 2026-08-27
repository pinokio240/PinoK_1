package re.pinok.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.random.Random

/**
 * #NETWORK-RESILIENCE (2026-08-04): Exponential backoff для auth-запросов.
 *
 * Проблема, которую решает этот класс:
 *  При нестабильной сети (метро, лифт, edge cell, WiFi handover) OkHttp `onFailure`
 *  срабатывает на transient errors (SocketTimeoutException, ConnectionReset,
 *  UnknownHostException). Без retry одна такая ошибка прерывает весь auth-flow:
 *  `silentRefreshViaRemixsid` abort'ит multi-strategy loop → `ensureFreshToken`
 *  → null → `notifyTokenInvalidated` → 30s LongPoll pause → AuthActivity cascade.
 *  Пользователь видит «выбивает из диалога» при малейшем дрожании сети.
 *
 * Решение (RFC 6298 + AWS exponential backoff с full jitter):
 *  - На каждую transient failure ждём `delay = min(base * 2^(attempt-1), cap) * (1 ± jitter)`.
 *  - Retry только для network/transient errors (см. [isTransient]).
 *  - 4xx/5xx HTTP ответы НЕ retry (контрактные/авторизационные ошибки).
 *  - Корутина cancellable: между попытками проверяется `coroutineContext.ensureActive()`,
 *    при cancel из AuthViewModel (`currentJob?.cancel()`) retry-loop мгновенно выходит.
 *
 * Параметры (defaults = OkHttp/Android best practices):
 *  - `maxAttempts = 3` (1 исходный + 2 retry) — баланс между UX и нагрузкой на VK.
 *     Больше 3 — пользователь ждёт слишком долго (1+2+4 = 7 сек в худшем случае).
 *  - `initialDelayMs = 1000` — первый retry через 1 сек (не мгновенно, чтобы дать
 *     сети «переварить» handover; RFC 6298 рекомендует 1 сек RTO).
 *  - `maxDelayMs = 8000` — ceiling (равно 2 retry от initial). Больше нет смысла:
 *     если за 8 сек сеть не восстановилась — это не transient, а реальный offline.
 *  - `jitterPct = 0.2` (±20%) — AWS «full jitter» pattern предотвращает thundering
 *     herd если несколько запросов упали одновременно (типично при восстановлении
 *     сети: LongPoll + NotificationsPoller + keepalive стартуют разом).
 *
 * Использование:
 *  ```
 *  val token = ExponentialBackoff.retryOnTransient {
 *      doSilentRefreshRequest(client, url, cookie, strat)
 *  }  // вернёт null если все 3 попытки провалились
 *  ```
 *
 * НЕ использовать для:
 *  - API-вызовов с side-effects (messages.send — может задвоиться).
 *  - Запросов > 5 сек (используйте простой retry без backoff).
 *  - LongPoll (там своя reconnect-логика с задержкой 1/2/4/8/16/32 сек).
 *
 * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">AWS Exponential Backoff And Jitter</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6298">RFC 6298 (TCP congestion control)</a>
 */
object ExponentialBackoff {

    private const val TAG = "ExpBackoff"

    /**
     * Дефолтная стратегия для auth-запросов.
     *
     * §50 #TOKEN-LIFECYCLE-FIX (2026-08-05): 3 попытки → 2 попытки.
     * Пользовательский симптом "токен умирает" усиливался долгими retry:
     * silent refresh на мёртвом remixsid делал 3 попытки (1с + 2с = 3с
     * delay между ними) → 7 сек висящего UI → AuthActivity SILENT loop.
     * 2 попытки (1с delay) = 3 сек worst case. Если VK не ответил за 3с —
     * NetworkObserver уже выставил Offline, popup показан, дольше ждать
     * бессмысленно. Path 5 (connect_exchange_token) срабатывает за 1-2с,
     * ему retry вообще не нужны.
     */
    val AUTH_DEFAULT: Strategy = Strategy(
        maxAttempts = 2,
        initialDelayMs = 1_000L,
        maxDelayMs = 8_000L,
        jitterPct = 0.2,
    )

    /**
     * Стратегия для лёгких API-вызовов (getExchangeToken, validateWebToken):
     * 4 попытки, 0.5/1/2/4 сек. Эти запросы быстрые (< 200мс), поэтому можем
     * позволить больше попыток с меньшей initial задержкой.
     */
    val API_LIGHT: Strategy = Strategy(
        maxAttempts = 4,
        initialDelayMs = 500L,
        maxDelayMs = 4_000L,
        jitterPct = 0.25,
    )

    data class Strategy(
        val maxAttempts: Int,
        val initialDelayMs: Long,
        val maxDelayMs: Long,
        val jitterPct: Double,
    )

    /**
     * Выполняет [block] с exponential backoff при transient failures.
     *
     * Возвращает:
     *  - результат [block] если хотя бы одна попытка успешна (не throws, не null).
     *  - `null` если все попытки провалились ИЛИ [block] вернул null на последней.
     *
     * Контракт [block]:
     *  - Может бросать [IOException] / его подклассы — будет retry.
     *  - Может бросать другие Exception — будет проброшено без retry.
     *  - Может вернуть null — будет retry (трактуется как transient failure).
     *
     * Cancellation: между попытками проверяется `coroutineContext.ensureActive()`.
     * При отмене корутины (например, `currentJob?.cancel()` в AuthViewModel)
     * loop мгновенно выходит через `CancellationException`.
     */
    suspend fun <T : Any> retryOnTransient(
        strategy: Strategy = AUTH_DEFAULT,
        tag: String = TAG,
        block: suspend (attempt: Int) -> T?,
    ): T? {
        var lastError: Exception? = null
        repeat(strategy.maxAttempts) { attempt ->
            // Cancellation check — если корутина отменена, выходим немедленно.
            coroutineContext.ensureActive()
            try {
                val result = block(attempt + 1)  // attempt с 1 для логов
                if (result != null) {
                    if (attempt > 0) {
                        AppLog.i(tag, "retry succeeded on attempt ${attempt + 1}/${strategy.maxAttempts}")
                    }
                    return result
                }
                // null = transient failure (network error / parse failure).
                lastError = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation — не подавляем, пробрасываем наверх.
                throw e
            } catch (e: Exception) {
                if (!isTransient(e)) {
                    // Non-transient (e.g. IllegalArgumentException, JsonSyntaxException) —
                    // retry не поможет, пробрасываем.
                    AppLog.d(tag, "non-transient exception on attempt ${attempt + 1}: ${e.javaClass.simpleName} — rethrow")
                    throw e
                }
                lastError = e
            }
            // Если это не последняя попытка — ждём с jitter.
            if (attempt < strategy.maxAttempts - 1) {
                val base = strategy.initialDelayMs * (1L shl attempt)  // 2^attempt
                val capped = min(base, strategy.maxDelayMs)
                val jitter = if (capped > 0) {
                    val range = (capped * strategy.jitterPct).toLong()
                    if (range > 0) Random.nextLong(-range, range + 1) else 0L
                } else 0L
                val delayMs = (capped + jitter).coerceAtLeast(0L)
                AppLog.d(tag, "attempt ${attempt + 1}/${strategy.maxAttempts} failed " +
                    (lastError?.let { "(${it.javaClass.simpleName}: ${it.message}) " } ?: "(null result) ") +
                    "→ backing off ${delayMs}ms before retry")
                delay(delayMs)
            }
        }
        AppLog.w(tag, "all ${strategy.maxAttempts} attempts exhausted — giving up" +
            (lastError?.let { " (last: ${it.javaClass.simpleName}: ${it.message})" } ?: " (last: null result)"))
        return null
    }

    /**
     * Классификация transient vs non-transient ошибок.
     *
     * Transient (retry имеет смысл):
     *  - [java.io.IOException] и подклассы (SocketTimeoutException, ConnectException,
     *    UnknownHostException, ConnectionResetException, SSLException на handshake).
     *  - [javax.net.ssl.SSLException] — типично при TLS renegotiation на смене сети.
     *
     * Non-transient (retry бесполезен):
     *  - [IllegalArgumentException] / [NullPointerException] — bug в коде.
     *  - [com.google.gson.JsonParseException] / [JsonSyntaxException] — VK вернул
     *    не-JSON (HTML error page) — retry даст тот же результат.
     *  - [java.lang.SecurityException] — нет permission.
     */
    private fun isTransient(e: Throwable): Boolean {
        // Сначала проверяем cause — OkHttp часто оборачивает IOException в RuntimeException.
        val root = e.cause ?: e
        return when (root) {
            is java.io.IOException -> true
            is javax.net.ssl.SSLException -> true
            else -> {
                // JsonParseException и подклассы — non-transient.
                // IllegalArgumentException — non-transient.
                false
            }
        }
    }
}
