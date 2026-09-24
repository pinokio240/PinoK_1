package re.pinok.auth.exchange

import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import re.pinok.util.AppLog

/**
 * #SESSION-WEB-EXPORT (2026-09-24): снапшот/восстановление cookie jar VK для
 * переносимых бэкапов (экспорт настроек SovaPrefsBackup + account.json).
 *
 * ## Зачем
 *
 * #SESSION-WEB-MECHANISM сделал CookieManager ЕДИНСТВЕННЫМ источником web-
 * сессии (storage-копии стёрты, синк удалён). Оба бэкапа до этого фикса
 * переносили только storage-ключи (access_token, exchange_token, lp_*) —
 * cookie jar при переустановке/KeyStore-коррупции терялся, и восстановленная
 * «сессия» умирала при первом refresh: HiddenSessionRefresher грузил m.vk.ru
 * с ПУСТЫМ cookie jar → VK не узнаёт пользователя (remixsid/p нет) →
 * definitivelyDead → полный ре-логин. Экспорт был обещанием «вход в аккаунт
 * после переустановки», которое нововведение сломало.
 *
 * ## Модель переноса
 *
 * getCookie(url) отдаёт только пары "name=value" БЕЗ атрибутов — домен
 * принадлежности восстановить из ответа нельзя. Поэтому домен выводится
 * по ПРАВИЛАМ VK-доменной модели (см. СЕССИЯ-ВЕБ-ПОРТ.md §1):
 *   - `p`            → .login.<base>   (persistent login, только login-хосты);
 *   - `remix*`, `httoken` → .<base>    (все sub-домены vk.ru / vk.com);
 *   - прочие имена   → host-only для URL захвата (консервативно).
 * где base = базовый домен URL захвата ("m.vk.ru" → "vk.ru"). На импорте
 * атрибут Domain восстанавливается ЯВНО в setCookie (формат Set-Cookie),
 * так что cookie попадает ровно в ту зону видимости, из которой снят.
 *
 * URL захвата — [AuthDomainsConfig.vkCookieUrls] (единый список с рантаймом:
 * m/base/login/id/oauth/web.api, обе зоны .ru и .com). Дедуп по (домен, имя):
 * первый по порядку списка выигрывает (более специфичный URL раньше).
 *
 * ## Безопасность
 *
 * Cookies сессии — отзываемый секрет (ре-логин инвалидирует), тот же класс,
 * что access_token: plaintext-файл допустим наравне с токенами (прецеденты —
 * account.json, волна 45-б). Секция соблюдает includeSession чекбокса
 * экспорта («Экспортировать без сессии» убирает и cookies).
 *
 * ## Потоки
 *
 * CookieManager singleton прогревается на Main при старте приложения
 * ([ExternalBrowserAuth.warmUpCookieManager] из SovaApp.onCreate), после чего
 * getCookie/setCookie/flush потокобезопасны с любого потока. Все вызовы
 * обёрнуты в runCatching — сбой бэкапа не должен ломать основной flow.
 */
object CookieJarBackup {

    private const val TAG = "CookieJarBackup"

    /** Имя поля cookies в account.json (ExchangeTokenStorage.dumpToFile). */
    const val BACKUP_FIELD = "web_cookies"

    /** Одна переносимая cookie: имя, значение, зона видимости (".vk.ru" — domain-cookie, "vk.ru" — host-only). */
    data class VkCookie(val name: String, val value: String, val domain: String)

    /**
     * Снимок VK-куков из живого CookieManager по всем доменам [AuthDomainsConfig.vkCookieUrls].
     * Пустой список = не залогинены в веб-сессию (или CookieManager недоступен).
     */
    fun snapshot(): List<VkCookie> = try {
        val cm = CookieManager.getInstance()
        val out = LinkedHashMap<Pair<String, String>, VkCookie>()
        for (url in AuthDomainsConfig.vkCookieUrls()) {
            val raw = try { cm.getCookie(url) } catch (e: Exception) { null } ?: continue
            val host = Uri.parse(url).host?.lowercase() ?: continue
            val base = baseDomainOf(host)
            for (pair in raw.split(";")) {
                val parts = pair.trim().split("=", limit = 2)
                if (parts.size != 2) continue
                val n = parts[0].trim()
                val v = parts[1].trim()
                if (n.isBlank() || v.isBlank()) continue
                val key = domainFor(n, base, host) to n
                if (!out.containsKey(key)) out[key] = VkCookie(n, v, key.first)
            }
        }
        out.values.toList()
    } catch (e: Throwable) {
        AppLog.w(TAG, "snapshot failed: ${e.message}")
        emptyList()
    }

    /**
     * Залить cookies обратно в CookieManager (setCookie с явным Domain/Path) +
     * один flush на диск. @return число фактически установленных кук.
     */
    fun restore(cookies: List<VkCookie>): Int {
        if (cookies.isEmpty()) return 0
        var written = 0
        try {
            val cm = CookieManager.getInstance()
            runCatching { cm.setAcceptCookie(true) }
            for (c in cookies) {
                if (c.name.isBlank() || c.value.isBlank() || c.domain.isBlank()) continue
                val isDomainCookie = c.domain.startsWith(".")
                val url = "https://" + c.domain.removePrefix(".")
                val str = buildString {
                    append(c.name).append('=').append(c.value)
                    if (isDomainCookie) append("; Domain=").append(c.domain)
                    append("; Path=/")
                }
                val ok = try { cm.setCookie(url, str) } catch (e: Exception) { false }
                if (ok) written++
            }
            // Fix #377 #DOZE-COOKIE-FLUSH: без flush восстановление переживает
            // только до смерти WebView-процесса — сбрасываем на диск сразу.
            runCatching { cm.flush() }
        } catch (e: Throwable) {
            AppLog.w(TAG, "restore failed: ${e.message}")
        }
        return written
    }

    /** JSON-снапшот для account.json (org.json, формат dumpToFile). */
    fun snapshotJson(): JSONArray = JSONArray().apply {
        for (c in snapshot()) {
            put(JSONObject().put("name", c.name).put("value", c.value).put("domain", c.domain))
        }
    }

    /** Восстановление из account.json. @return число установленных кук. */
    fun restoreJsonArray(arr: JSONArray?): Int {
        if (arr == null) return 0
        val list = ArrayList<VkCookie>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(VkCookie(o.optString("name"), o.optString("value"), o.optString("domain")))
        }
        return restore(list)
    }

    /**
     * Домен принадлежности по имени cookie (см. KDoc объекта: правила
     * VK-доменной модели веб-сессии).
     */
    private fun domainFor(name: String, base: String, host: String): String = when {
        name == "p" -> ".login.$base"                       // persistent login — только login-хосты
        name.startsWith("remix") || name == "httoken" -> ".$base"  // сессия/антифрод — весь base-домен
        else -> host                                        // неизвестное имя — консервативно host-only
    }

    /** "m.vk.ru" / "web.api.vk.ru" / "login.vk.com" → "vk.ru" / "vk.com" (последние 2 метки). */
    private fun baseDomainOf(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }
}
