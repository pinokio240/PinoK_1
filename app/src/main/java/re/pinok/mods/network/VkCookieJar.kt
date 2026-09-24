package re.pinok.mods.network

import android.webkit.CookieManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import re.pinok.auth.exchange.ExchangeTokenStorage
import re.pinok.util.AppLog

/**
 * #CALLS-ANTIFRAUD (2026-08-23) + #SESSION-WEB-MECHANISM (2026-09-24): OkHttp
 * CookieJar, который подставляет ЖИВОЙ cookie-set VK из [CookieManager]
 * (WebView cookie store) в исходящие HTTP-запросы — как это делает браузер.
 *
 * Зачем: PinoK раньше вообще не отправлял cookies (только Origin/Referer/UA).
 * Обычные API-запросы (access_token в query) работают без кук, но
 * чувствительные к антифроду эндпоинты — `get_anonym_token` (oauth.vk.ru),
 * `auth.anonymLogin` (calls.okcdn.ru), login.vk.ru/?act=web_token —
 * валидируют полный cookie-set (remixsid, remixstid, remixstlid, httoken…).
 * Без них VK отклоняет запрос (401 AUTH_LOGIN / 403), и мы не можем
 * автоматически получить session_key/callToken как браузер.
 *
 * ## ИСТОЧНИК (изменилось в #SESSION-WEB-MECHANISM)
 *
 * CookieManager — ЕДИНСТВЕННЫЙ источник web-сессии (как в веб-версии).
 * Раньше jar читал storage-копии [ExchangeTokenStorage] — но копии стёрты
 * wipe'ом и больше не синхронизируются (протухали при ротации remixsid —
 * корень багов «смена сети → просит логин»). Теперь:
 *   - loadForRequest → CookieManager.getCookie(url) — тот же jar, что у
 *     HiddenSessionRefresher/AuthActivity: ротации видны мгновенно, с любого IP;
 *   - saveFromResponse → зеркало Set-Cookie ОБРАТНО в CookieManager
 *     (setCookie с атрибутами Domain/Path/Expires из OkHttp Cookie) — ротации,
 *     пойманные OkHttp-потоками (антифрод-эндпоинты), не теряются для web-сессии;
 *   - ИСКЛЮЧЕНИЕ для anonym_id: remixstid/remixstlid дублируются в storage
 *     (Fix F-3 #CALLS-ANTIFRAUD, P0 «персистит вечно») — переживают очистку
 *     webview-данных; читаются как fallback, пишутся как раньше.
 *
 * flush() здесь НЕ зовётся: ON_RESUME flush (#DOZE-COOKIE-FLUSH) в SovaApp
 * уже покрывает персистентность, а disk-IO на OkHttp-потоках не нужен.
 */
class VkCookieJar(
    private val storage: ExchangeTokenStorage,
) : CookieJar {

    private companion object {
        const val TAG = "VkCookieJar"

        /** OkHttp sentinel для session-cookie (без Expires) — «31.12.9999». */
        const val OKHTTP_MAX_DATE = 253402300799999L

        /** Имена кук, которые зеркалим из Set-Cookie в CookieManager. */
        val MIRROR_NAMES = setOf(
            "remixsid", "remixnsid", "p", "httoken",
            "remixstid", "remixstlid", "remixdmgr",
            "remixuacck", "remixuas", "remixmvk-fp", "remixnttpid",
        )
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return try {
            val host = url.host.lowercase()
            if (!host.endsWith("vk.ru") && !host.endsWith("vk.com")) return emptyList()

            val raw = CookieManager.getInstance().getCookie(url.toString())
            val cookies = ArrayList<Cookie>()
            if (!raw.isNullOrBlank()) {
                for (pair in raw.split(";")) {
                    val parts = pair.trim().split("=", limit = 2)
                    if (parts.size != 2) continue
                    val n = parts[0].trim()
                    val v = parts[1].trim()
                    if (n.isBlank() || v.isBlank()) continue
                    // getCookie(url) уже отфильтровал по домену/пути — аттачим к хосту запроса.
                    cookies.add(
                        Cookie.Builder().name(n).value(v).domain(host).path("/").build()
                    )
                }
            }

            // Fallback anonym_id (#CALLS-ANTIFRAUD F-3): remixstid/remixstlid
            // персистят в storage и переживают очистку webview-данных. Только
            // эти два имени — сессионные куки fallback'а НЕ имеют (источник
            // истины — живой CookieManager).
            val names = cookies.mapTo(HashSet()) { it.name }
            if ("remixstid" !in names) {
                storage.remixstid()?.takeIf { it.isNotBlank() }?.let {
                    cookies.add(Cookie.Builder().name("remixstid").value(it).domain(host).path("/").build())
                }
            }
            if ("remixstlid" !in names) {
                storage.remixstlid()?.takeIf { it.isNotBlank() }?.let {
                    cookies.add(Cookie.Builder().name("remixstlid").value(it).domain(host).path("/").build())
                }
            }
            cookies
        } catch (e: Exception) {
            AppLog.w(TAG, "loadForRequest error: ${e.message}")
            emptyList()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        try {
            val host = url.host.lowercase()
            if (!host.endsWith("vk.ru") && !host.endsWith("vk.com")) return
            val cm = CookieManager.getInstance()
            var mirrored = 0
            var stid: String? = null
            var stlid: String? = null

            for (c in cookies) {
                when (c.name) {
                    "remixstid" -> if (c.value.length >= 20) stid = c.value
                    "remixstlid" -> if (c.value.length >= 20) stlid = c.value
                }
                if (c.name !in MIRROR_NAMES) continue
                // Зеркало Set-Cookie → CookieManager (формат Set-Cookie ответа):
                // Domain — для domain-cookie (okhttp хранит без ведущей точки),
                // Expires — только для персистентных (session-cookie терять
                // на рестарте процесса и так нечего зеркалить — они умрут вместе
                // с памятью OkHttp-ответа до всякой пользы).
                val str = buildString {
                    append(c.name).append('=').append(c.value)
                    if (!c.hostOnly) append("; Domain=").append(c.domain)
                    append("; Path=").append(c.path)
                    if (c.expiresAt in 1L until OKHTTP_MAX_DATE) {
                        append("; Expires=").append(httpDate(c.expiresAt))
                    }
                    if (c.secure) append("; Secure")
                }
                // Успех = нет исключения: НЕ завязываемся на тип результата
                // setCookie (Boolean в API 21+ / void в части тулчейнов —
                // getOrDefault(false) даёт Any и роняет компиляцию). Прецедент
                // игнорирования результата — CallsWebViewScreen/ExternalBrowserAuth.
                if (runCatching { cm.setCookie(url.toString(), str) }.isSuccess) mirrored++
            }

            // anonym_id → storage (персистентность F-3, patch-семантика как раньше).
            if (stid != null || stlid != null) {
                storage.saveSessionCookiesOnly(remixstid = stid, remixstlid = stlid)
            }
            if (mirrored > 0) {
                AppLog.d(TAG, "saveFromResponse: mirrored $mirrored cookies → CookieManager (${url.host})" +
                    (if (stid != null) " +stid" else "") + (if (stlid != null) " +stlid" else ""))
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "saveFromResponse error: ${e.message}")
        }
    }

    /** unix-ms → HTTP-date (RFC 7231) для атрибута Expires. */
    private fun httpDate(ms: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(ms))
}
