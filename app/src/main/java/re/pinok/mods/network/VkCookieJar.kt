package re.pinok.mods.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import re.pinok.auth.exchange.ExchangeTokenStorage
import re.pinok.util.AppLog

/**
 * #CALLS-ANTIFRAUD (2026-08-23): OkHttp CookieJar, который подставляет
 * полный браузерный cookie-set VK из [ExchangeTokenStorage] в исходящие
 * HTTP-запросы — как это делает браузер.
 *
 * Зачем: PinoK раньше вообще не отправлял cookies (только Origin/Referer/UA).
 * Обычные API-запросы (access_token в query) работают без кук, но
 * чувствительные к антифроду эндпоинты — `get_anonym_token` (oauth.vk.ru),
 * `auth.anonymLogin` (calls.okcdn.ru), login.vk.ru/?act=web_token —
 * валидируют полный cookie-set (remixsid, remixstid, remixstlid, httoken…).
 * Без них VK отклоняет запрос (401 AUTH_LOGIN / 403), и мы не можем
 * автоматически получить session_key/callToken как браузер.
 *
 * Доменное маппирование (как у браузера):
 *   - `.vk.ru` / `.vk.com` cookies → любые vk.ru/vk.com/m.vk.ru/web.api.vk.ru
 *   - httoken есть на `.api.vk.ru` и `.web.api.vk.ru` — шлём на API-домены
 *   - p (persistent login) — на login.vk.com (для web_token flow)
 *
 * saveFromResponse: Set-Cookie от VK сохраняем в storage (patch-семантика) —
 * чтобы следующая сессия имела обновлённые remix-куки.
 */
class VkCookieJar(
    private val storage: ExchangeTokenStorage,
) : CookieJar {

    private companion object {
        const val TAG = "VkCookieJar"
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return try {
            val host = url.host.lowercase()
            val cookies = mutableListOf<Cookie>()

            fun add(name: String, value: String, domain: String, path: String = "/") {
                if (value.isBlank()) return
                // OkHttp требует домен БЕЗ ведущей точки ("vk.ru", не ".vk.ru").
                val d = domain.removePrefix(".")
                cookies.add(
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(d)
                        .path(path)
                        .build()
                )
            }

            // vk.ru/vk.com и все их поддомены (m.vk.ru, web.api.vk.ru, id.vk.com…)
            if (host.endsWith("vk.ru") || host.endsWith("vk.com") ||
                host == "api.vk.ru" || host == "web.api.vk.ru" || host == "m.vk.ru" ||
                host == "id.vk.com" || host == "login.vk.com"
            ) {
                // Session + VK ID
                storage.remixsid()?.let { add("remixsid", it, ".vk.ru") }
                storage.remixnsid()?.let { add("remixnsid", it, "vk.ru") }
                // Anti-fraud
                storage.remixstid()?.let { add("remixstid", it, ".vk.ru") }
                storage.remixstlid()?.let { add("remixstlid", it, ".vk.ru") }
                storage.remixdmgr()?.let { add("remixdmgr", it, ".vk.ru") }
                storage.remixuacck()?.let { add("remixuacck", it, ".vk.ru") }
                storage.remixuas()?.let { add("remixuas", it, ".vk.ru") }
                storage.remixmvkFp()?.let { add("remixmvk-fp", it, ".vk.ru") }
                // httoken — anti-CSRF (шлём на все vk-домены; VK ставит его на .api.vk.ru)
                storage.httoken()?.let { add("httoken", it, ".api.vk.ru") }
            }

            // login.vk.com — для web_token flow нужен persistent login p
            if (host == "login.vk.com" || host == "login.vk.ru" || host.endsWith("login.vk.com")) {
                storage.pCookie()?.let { add("p", it, ".login.vk.com") }
            }

            // calls.okcdn.ru / api.mycdn.me — session_key не в cookie, но
            // API_SESSION_ID (если захвачена) можно отправить тоже.
            // (calls аутентифицируется через session_key/callToken, не куки —
            //  этот блок оставлен на случай, если VK начнёт проверять.)

            cookies
        } catch (e: Exception) {
            AppLog.w(TAG, "loadForRequest error: ${e.message}")
            emptyList()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Сохраняем обновлённые remix-куки в storage (patch-семантика).
        // Основной захват кук идёт через RemixsidCapturer (CookieManager),
        // этот метод — дополнительный источник для OkHttp-потоков.
        try {
            var remixsid: String? = null
            var remixnsid: String? = null
            var p: String? = null
            var httoken: String? = null
            var stid: String? = null
            var stlid: String? = null
            var changed = false
            for (c in cookies) {
                when (c.name) {
                    "remixsid" -> { if (c.value.length >= 20) { remixsid = c.value; changed = true } }
                    "remixnsid" -> { if (c.value.length >= 50) { remixnsid = c.value; changed = true } }
                    "p" -> { if (c.value.length >= 50) { p = c.value; changed = true } }
                    "httoken" -> { if (c.value.length >= 20) { httoken = c.value; changed = true } }
                    "remixstid" -> { if (c.value.length >= 20) { stid = c.value; changed = true } }
                    "remixstlid" -> { if (c.value.length >= 20) { stlid = c.value; changed = true } }
                }
            }
            if (changed) {
                storage.saveSessionCookiesOnly(
                    remixsid = remixsid,
                    remixnsid = remixnsid,
                    p = p,
                    httoken = httoken,
                    remixstid = stid,
                    remixstlid = stlid,
                )
                AppLog.d(TAG, "saveFromResponse: updated ${url.host} cookies " +
                    "(remixsid=${if (remixsid != null) "yes" else "no"}, " +
                    "stid=${if (stid != null) "yes" else "no"}, " +
                    "stlid=${if (stlid != null) "yes" else "no"})")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "saveFromResponse error: ${e.message}")
        }
    }
}
