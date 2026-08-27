package re.pinok.mods.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog
import java.io.IOException

/**
 * OkHttp interceptors, реализующие 3 сетевые настройки SOVA 2.0:
 *
 *  - [AdBlockInterceptor]      → `netAdBlock`    — блокирует запросы к рекламным доменам
 *  - [AwayBypassInterceptor]   → `netAwayBypass` — разворачивает away.php?to=... напрямую
 *  - [SslPinningInterceptor]   → `netSslPinning` — добавляет CertificatePinner для VK domains
 *
 * Все interceptors читают актуальный [SovaPrefs.Snapshot] на каждый запрос через
 * `runBlocking { prefs.data.first() }`. Это безопасно — OkHttp вызывает interceptors
 * на рабочем потоке, не на UI. runBlocking здесь предпочтительнее создания нового
 * OkHttpClient при каждом изменении prefs (что требовало бы пересоздавать apiClient
 * и все его зависимые сервисы).
 *
 * Pinned SPKI hashes для VK доменов взяты из публичных certificate transparency логов
 * (действительны на 2025-2026, обновляются при ротации ключей VK).
 */
object NetworkInterceptors {

    private const val TAG = "NetInterceptors"

    // ─────────────────────────────────────────────────────────────────────────
    //  Ad Block — блокировка рекламных доменов
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Блокирует запросы к доменам из [NetworkMods.AD_DOMAINS] когда `netAdBlock=true`.
     *
     * Возвращает HTTP 451 (Unavailable For Legal Reasons) с пустым телом —
     * ExoPlayer/Coil/VKApiClient интерпретируют это как сетевую ошибку и
     * переходят к следующему URL/fallback. Это лучше чем throw, т.к. не
     * засоряет логи stacktrace-ами и не рвёт соединение.
     */
    class AdBlockInterceptor(
        private val prefs: SovaPrefs,
        private val networkMods: NetworkMods = NetworkMods(),
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url.toString()
            val snap = runBlocking { prefs.data.first() }
            if (snap.netAdBlock && networkMods.isAdDomain(url)) {
                AppLog.d(TAG, "AdBlock: blocked $url")
                return Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(451)
                    .message("Blocked by PinoK AdBlock")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            return chain.proceed(chain.request())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Away Bypass — разворот away.php?to=... напрямую
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Когда `netAwayBypass=true` и URL — это `vk.com/away.php?to=ENC_URL`,
     * interceptor перестраивает запрос на развёрнутый URL. VK отдаёт через
     * away.php реальный адрес как query-параметр `to` (URL-encoded).
     *
     * Это пропускает tracking-редирект VK и экономит один round-trip.
     * Не разворачивает если URL не away.php или если параметр `to` пустой.
     */
    class AwayBypassInterceptor(
        private val prefs: SovaPrefs,
        private val networkMods: NetworkMods = NetworkMods(),
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val url = original.url.toString()
            val snap = runBlocking { prefs.data.first() }
            if (!snap.netAwayBypass) return chain.proceed(original)
            if (!networkMods.isAwayRedirect(url)) return chain.proceed(original)

            val unwrapped = networkMods.unwrapAway(url)
            if (unwrapped == url) return chain.proceed(original)

            AppLog.d(TAG, "AwayBypass: $url → $unwrapped")
            val newReq = original.newBuilder()
                .url(unwrapped)
                .build()
            return chain.proceed(newReq)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SSL Pinning — certificate pinner для VK доменов
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SPKI SHA-256 pins для VK доменов.
     *
     * ⚠️ Audit #40: ВНИМАНИЕ — pins ВРЕМЕННО ДЕАКТИВИРОВАНЫ.
     *
     * Раньше здесь были 3 константы, но 2 из них (`VK_COM` и `USERAUDIO_NET`)
     * имели значение "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=" —
     * это SHA-256 от ПУСТОЙ СТРОКИ (хорошо известная demo-заглушка).
     * Если бы пользователь включил `netSslPinning=true`, OkHttp CertificatePinner
     * отклонил бы ВСЕ VK HTTPS-соединения с `peerFailedPinning`.
     *
     * Для восстановления нужно получить актуальные SPKI pins из реальных
     * сертификатов VK:
     *   echo | openssl s_client -connect api.vk.com:443 -servername api.vk.com 2>/dev/null | \
     *     openssl x509 -pubkey -noout | \
     *     openssl pkey -pubin -outform der | \
     *     openssl dgst -sha256 -binary | \
     *     openssl enc -base64
     * И затем: "sha256/<результат>".
     *
     * Пока pins не восстановлены — `forHost()` всегда возвращает emptyList,
     * и CertificatePinner не активируется (поведение по умолчанию).
     */
    object SslPins {
        // Оригинальные значения сохранены как комментарий для истории:
        // const val VK_COM = "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="  ← fake (sha256(""))
        // const val VK_COM_ALT = "sha256/lCppFqbkrlJ3EcVFAkeip0+44VaoJUymbnOaEUk7tEU="
        // const val USERAUDIO_NET = "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="  ← fake

        /** Все pins для домена. Если их несколько — любой совпадает. */
        fun forHost(host: String): List<String> = when (host) {
            // Audit #40: временно отключено — см. комментарий выше.
            // "api.vk.com", "oauth.vk.com", "id.vk.com",
            // "login.vk.com", "m.vk.com", "vk.com", "vk.ru" -> listOf(VK_COM, VK_COM_ALT)
            // "psv4.vkuseraudio.net", "psv4.userapi.com" -> listOf(USERAUDIO_NET)
            else -> emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Network Retry — автоматический retry на IOException при смене сети
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fix #45: Application interceptor — retry на IOException с коротким backoff.
     *
     * Проблема: при переключении WiFi→Mobile активные запросы VKApiClient
     * падают с `SocketTimeoutException` / `ConnectionResetException` на dead
     * WiFi-соединениях. OkHttp `retryOnConnectionFailure(true)` ретраит только
     * если connection упала ДО отправки запроса — если request уже в полёте,
     * retry не срабатывает, и [VKApiClient.callInternal] пробрасывает исключение
     * в UI (красный баннер «Ошибка воспроизведения» вместо тихого retry).
     *
     * Этот interceptor делает до [MAX_RETRIES] дополнительных попыток с
     * экспоненциальным backoff (500мс → 1000мс) — достаточно для того, чтобы
     * NetworkObserver успел вызвать `evictAll()` и новый запрос пошёл через
     * свежий connection на новом интерфейсе.
     *
     * ВАЖНО: только для GET-запросов (VK API — всегда GET). POST/multipart
     * не ретраим — тело запроса могло быть уже отправлено, повтор опасен
     * (двойной like, двойной пост и т.д.).
     *
     * Не ретраим 4xx/5xx HTTP — это не сетевые ошибки, а валидные ответы VK.
     */
    class NetworkRetryInterceptor : Interceptor {
        private companion object {
            const val TAG = "NetRetry"
            const val MAX_RETRIES_DEFAULT = 2
            const val MAX_RETRIES_AUDIO = 4
            // Фиксированный backoff для VK API endpoints — короткий, т.к. вызовы
            // обычно массовые и лишний hammering нежелателен.
            val BACKOFF_MS_DEFAULT = longArrayOf(500L, 1000L)
            // Fix #50-B: Exponential backoff для audio CDN — сегменты маленькие,
            // CDN устойчив к нагрузке, поэтому можно дольше ретраить чтобы пережить
            // кратковременные обрывы на мобильной сети.
            val BACKOFF_MS_AUDIO = longArrayOf(500L, 1000L, 2000L, 4000L)

            /**
             * VK audio/video CDN домены. Для них используем расширенный retry.
             * psv4.vkuseraudio.net — основные audio-сегменты HLS.
             * psv4.vkvideo.net — video-сегменты.
             * *.userapi.com — fallback CDN (audio+video).
             */
            fun isAudioCdnHost(host: String): Boolean =
                host.endsWith("vkuseraudio.net") ||
                host.endsWith("vkvideo.net") ||
                host.endsWith("userapi.com")
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // Только idempotent GET-запросы — VK API всегда GET.
            val isGet = request.method == "GET"
            if (!isGet) return chain.proceed(request)

            val host = request.url.host
            val isAudio = isAudioCdnHost(host)
            val maxRetries = if (isAudio) MAX_RETRIES_AUDIO else MAX_RETRIES_DEFAULT
            val backoffSchedule = if (isAudio) BACKOFF_MS_AUDIO else BACKOFF_MS_DEFAULT

            // Fix #181: флаг — делали ли evictAll в этом вызове intercept.
            // evictAll() дорогой (закрывает ВСЕ keep-alive соединения в pool'е),
            // поэтому делаем его только один раз за запрос — при первой IOException.
            var evictedForSwitch = false

            var lastError: IOException? = null
            repeat(maxRetries + 1) { attempt ->
                try {
                    return chain.proceed(request)
                } catch (e: IOException) {
                    lastError = e
                    // Fix #181: если сеть недавно переключилась и это первая ошибка —
                    // принудительно evictAll OkHttp connection pool. StaleConnectionInterceptor
                    // добавляет Connection: close, но он срабатывает только если
                    // isRecentlySwitched(10s) == true. Если switch не задетектирован
                    // (edge case) или если 10 сек уже прошло, но pool ещё содержит
                    // stale connections — evictAll при IOException спасает.
                    if (!evictedForSwitch) {
                        val recentlySwitched = try {
                            re.pinok.SovaApp.getOrNull()?.networkObserver?.isRecentlySwitched(30_000L) == true
                        } catch (_: Exception) { false }
                        if (recentlySwitched) {
                            evictedForSwitch = true
                            try {
                                // Fix #181: доступ к connectionPool через SovaApp.httpClient.
                                // chain.call() возвращает okhttp3.Call (interface), у которого
                                // НЕТ метода client() — он есть только у internal RealCall.
                                // Поэтому берём httpClient из singleton SovaApp (тот же client,
                                // что используется для всех VK API/audio запросов).
                                val pool = re.pinok.SovaApp.getOrNull()?.httpClient?.connectionPool
                                if (pool != null) {
                                    AppLog.i(TAG, "IOException + recently switched → evictAll() for $host (attempt ${attempt + 1}/${maxRetries + 1})")
                                    pool.evictAll()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (attempt >= maxRetries) {
                        AppLog.w(TAG, "All $maxRetries retries exhausted for $host${request.url.encodedPath}: ${e.javaClass.simpleName}")
                        throw e
                    }
                    val backoff = backoffSchedule[attempt]
                    AppLog.d(TAG, "Retry ${attempt + 1}/$maxRetries for $host after ${backoff}ms (${e.javaClass.simpleName})")
                    try {
                        Thread.sleep(backoff)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
            // Unreachable, но компилятор требует return/throw.
            throw lastError ?: IOException("NetworkRetryInterceptor: unreachable")
        }
    }

    /**
     * Fix #176: StaleConnectionInterceptor — добавляет `Connection: close` header
     * к запросам если сеть недавно переключилась (mobile↔Wi-Fi).
     *
     * PROBLEM: После switch'а OkHttp connectionPool содержит keep-alive соединения
     * на мёртвом Wi-Fi интерфейсе. evictAll() закрывает только IDLE соединения,
     * но если соединение сейчас в pool'е и помечено как reusable — новый запрос
     * может быть отправлен по нему (OkHttp берёт connection из pool'а по key
     * host:port, не зная что нижележащий socket мёртв). Запрос таймаутит →
     * user видит «не удалось авторизоваться в течение 60 секунд».
     *
     * Дополнительно: JVM-level AddressCache (network-unaware, TTL 2 сек по
     * default) может возвращать старый DNS resolution. OkHttp issue #4789
     * подтверждает что это известная проблема при network switch.
     *
     * SOLUTION: В течение 10 секунд после switch'а добавляем `Connection: close`
     * ко всем запросам. Это заставляет OkHttp НЕ переиспользовать connections
     * из pool'а и открывать новые. Через 10 сек switch'а уже устоялся, можно
     * вернуть keep-alive для производительности.
     *
     * Безопасно: Connection: close — стандартный HTTP/1.1 header, серверы
     * корректно его обрабатывают. Единственный минус — каждый запрос открывает
     * новый TCP handshake (на 10 сек), что приемлемо.
     */
    class StaleConnectionInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val recentlySwitched = try {
                re.pinok.SovaApp.getOrNull()?.networkObserver?.isRecentlySwitched(10_000L) == true
            } catch (_: Exception) { false }
            if (recentlySwitched && request.header("Connection") == null) {
                val newRequest = request.newBuilder()
                    .header("Connection", "close")
                    .build()
                return chain.proceed(newRequest)
            }
            return chain.proceed(request)
        }
    }
}
