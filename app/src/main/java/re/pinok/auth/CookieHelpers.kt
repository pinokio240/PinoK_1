package re.pinok.auth

import android.webkit.CookieManager
import re.pinok.auth.exchange.RemixsidCapturer

/**
 * Cookie helpers — извлечение remixsid и userId из CookieManager WebView.
 *
 * P2-12 #AUTH-AUDIT: вынесено из AuthActivity.kt в отдельный файл.
 * Используется в [re.pinok.auth.VkAuthWebViewScreenV2] (cookie polling)
 * и в [re.pinok.auth.AuthViewModel.submitWebToken] (Path 1.5 fallback).
 */

/** URL-ы для проверки remixsid (пробиваем все VK домены). */
internal val COOKIE_CHECK_URLS = listOf(
    "https://vk.com",
    "https://m.vk.com",
    "https://vk.ru",
    "https://m.vk.ru",
    "https://login.vk.com",
    "https://login.vk.ru",
    // #IP-MISMATCH-REMIXSID (2026-10-18): VK ID flow авторизует на id.vk.ru —
    // session cookie (remixsid/remixnsid) может ставиться на id.vk.ru/id.vk.com,
    // которых раньше в списке не было → вход завершался без remixsid → IP-mismatch.
    "https://id.vk.ru",
    "https://id.vk.com",
)

/**
 * Ищет remixsid в CookieManager WebView.
 * remixsid появляется ТОЛЬКО после полной авторизации (включая 2FA).
 *
 * Единый источник — [RemixsidCapturer.snapshotCookies] (понимает id.vk.ru,
 * remixnsid/new VK ID session и p cookie). Если его нет — fallback на прямое
 * сканирование [COOKIE_CHECK_URLS] (как раньше).
 *
 * Возвращает первый найденный не-empty и не-«deleted» value.
 */
internal fun getRemixSidFromCookieManager(): String? {
    // #IP-MISMATCH-REMIXSID: сначала пробуем единый источник (id.vk.ru/remixnsid).
    val unified = runCatching { RemixsidCapturer.snapshotCookies() }.getOrNull()
    if (unified != null) {
        if (!unified.remixsid.isNullOrBlank()) return unified.remixsid
        // VK ID ставит remixnsid вместо remixsid (похожий по роли) — тоже годится
        // как silent-средство (RemixsidCapturer собирает его в cookie-set).
        if (!unified.remixnsid.isNullOrBlank()) return unified.remixnsid
        if (!unified.pCookie.isNullOrBlank()) return unified.pCookie
    }
    // Fallback: прямое сканирование.
    val cm = CookieManager.getInstance()
    for (url in COOKIE_CHECK_URLS) {
        try {
            val rawCookie = cm.getCookie(url) ?: continue
            val cookies = rawCookie.split(";").map { it.trim() }
            for (cookie in cookies) {
                val parts = cookie.split("=", limit = 2)
                if (parts.size == 2 && (parts[0].trim() == "remixsid" || parts[0].trim() == "remixnsid")) {
                    val value = parts[1].trim()
                    if (value.isNotEmpty() && value != "deleted") {
                        return value
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return null
}

/**
 * #AUTH-LOOP-FIX (2026-08-07): извлекает userId из CookieManager.
 *
 * VK ставит cookie `remixsid_user=<userId>` после успешной авторизации.
 * Это тот же userId что VK возвращает в AuthResult.userId.
 *
 * Зачем: после m.vk.ru login remixsid валиден (88 символов), но WebTokenAuth
 * не успевает обменять его на web_token за 25 сек (m.vk.ru/feed редиректит
 * и VK ID SDK не инициализируется). Path 1.5 (silentRefreshViaRemixsid)
 * требует userId для `remixsid_user=<userId>` cookie header, но userId в
 * storage = 0 (токен ещё не сохранён). Без этого Path 1.5 падает →
 * clearDeadSessionForRetry → loop (пользователь видит форму логина снова).
 *
 * Фикс: извлекаем userId из CookieManager и сохраняем в storage ДО вызова
 * Path 1.5. Тогда silentRefreshViaRemixsid получает корректный userId и
 * может обменять remixsid на access_token через login.vk.ru/?act=web_token.
 *
 * @return userId если найден в cookie remixsid_user, иначе 0
 */
internal fun getUserIdFromCookieManager(): Long {
    val cm = CookieManager.getInstance()
    for (url in COOKIE_CHECK_URLS) {
        try {
            val rawCookie = cm.getCookie(url) ?: continue
            val cookies = rawCookie.split(";").map { it.trim() }
            for (cookie in cookies) {
                val parts = cookie.split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == "remixsid_user") {
                    val value = parts[1].trim()
                    val userId = value.toLongOrNull()
                    if (userId != null && userId > 0L) {
                        return userId
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return 0L
}

