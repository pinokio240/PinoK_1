package re.pinok.mods.network

import android.net.Uri
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

/**
 * Network-level mods (formerly implemented as OkHttp interceptors in SOVA V RE).
 *
 * SOVA_2.0 ships these as Compose-level preferences that the [VKApiClient]
 * consults before each call. The methods below return "what would the user
 * want for this request?" so the API client can decide accordingly.
 *
 * Original SOVA V RE mods covered here:
 *  - offline mode toggle
 *  - SSL pinning toggle (handled in [SovaApp.httpClient] configuration)
 *  - away.php bypass
 *  - ad blocking
 *  - custom User-Agent / device masking (handled in [SovaApp.httpClient])
 */
class NetworkMods {

    private val tag = "NetworkMods"

    /** True if offline mode is forced via user preference (callers pass a fresh snapshot). */
    fun isOfflineForced(snapshot: SovaPrefs.Snapshot): Boolean {
        val forced = snapshot.privacyOfflineMode
        if (forced) AppLog.d(tag, "Offline mode forced — API call will be short-circuited")
        return forced
    }

    /**
     * Returns true if the given URL is an ad domain that should be blocked.
     * audit Medium #21: используем host-сравнение вместо substring match
     * (старый contains давал false positives типа notad.mail.ru).
     */
    fun isAdDomain(url: String): Boolean {
        val host = try { Uri.parse(url).host?.lowercase() } catch (_: Exception) { null }
        if (host == null) return false
        val blocked = AD_DOMAINS.any { adHost ->
            host == adHost || host.endsWith(".$adHost")
        }
        if (blocked) AppLog.d(tag, "Ad domain blocked: $host")
        return blocked
    }

    /**
     * Returns true if the URL is an away.php tracking redirect.
     * audit Medium #21: проверяем path, а не substring.
     */
    fun isAwayRedirect(url: String): Boolean {
        val path = try { Uri.parse(url).path } catch (_: Exception) { null }
        return path?.contains("away.php") == true
    }

    /**
     * Extracts the real URL from an away.php redirect link.
     * audit Medium #21: используем Uri.getQueryParameter("to") вместо
     * наивного indexOf("to=") — последнее матчило подстроку в path.
     */
    fun unwrapAway(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val target = uri.getQueryParameter("to")
            if (target.isNullOrBlank()) {
                AppLog.w(tag, "unwrapAway: no 'to' query param in $url")
                url
            } else {
                java.net.URLDecoder.decode(target, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            AppLog.e(tag, "unwrapAway failed for $url", e)
            url
        }
    }

    private companion object {
        // Домены рекламных серверов. ВАЖНО: сюда НЕ входит голый «vk.com» —
        // иначе canBeBlocked() может зарезать api.vk.com/oauth.vk.com.
        // Реклама на самом vk.com отсекается отдельно через AD_PATHS.
        //
        // #62: убран "vk.cc" — это официальный shortener VK, используется
        // не только для рекламы, но и для обычных ссылок в постах/сообщениях.
        // Блокировка vk.cc ломала открытие коротких ссылок из ленты и чатов.
        val AD_DOMAINS = listOf(
            "ad.mail.ru",
            "rs.mail.ru",
            "ad.vk.com",
            "targ.mail.ru",
            "ads.vk.com",
        )

        /** Path patterns for ad URLs on VK domains. */
        @Suppress("unused")
        val AD_PATHS = listOf(
            "/ads",
            "/ads_create",
        )
    }
}
