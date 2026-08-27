package re.pinok.api

import re.pinok.BuildConfig

/**
 * VK API endpoint URLs and helpers.
 *
 * VK OAuth uses the implicit flow (token in URL fragment) — see
 * https://dev.vk.com/api/access-token/getting-started
 *
 * ## Two API gateways (Task #Web-API)
 *
 * VK exposes two different REST gateways for `/method/<METHOD>`:
 *
 * 1. **`api.vk.com`** — Android client gateway (default).
 *    - Expects `VKAndroidApp/...` User-Agent + `X-VK-Android-Client: new` header.
 *    - Requires `sig` (user_secret) for `messages.*` / `audio.*` / `execute`
 *      when using a non-web access token.
 *    - Default VK API version `5.269` (see build.gradle.kts).
 *
 * 2. **`web.api.vk.ru`** — Mobile-web gateway (m.vk.ru SPA).
 *    - Discovered in `мессенджер.zip` static config: `"apiDomain":"web.api.vk.ru"`.
 *    - URL pattern in `b-226df83bda86a954.bc377a3ddd52ea9b.js`:
 *      `https://${apiDomain}/method/${method}?${params}` (when `useAPIGateWay:true`).
 *    - Expects a browser-like User-Agent — sending `X-VK-Android-Client: new`
 *      here would expose the request as a non-browser client and break
 *      `get_anonym_token` / `messages.*` (401 invalid_request).
 *    - Default VK API version on m.vk.ru: `5.205` (with per-namespace overrides:
 *      `5.255` audio, `5.279` market). We still send our `5.269` — VK accepts
 *      it on web gateway (it's a recent enough version).
 *    - Works WITHOUT `sig` when using a `vk1.a.*` web access token (see
 *      `VkSigner.isWebToken`), which is exactly what our WebTokenAuth flow
 *      produces with `VK_WEB_CLIENT_ID` / `VK_WEB_MOBILE_CLIENT_ID`.
 *
 * Gateway selection is controlled by `SovaPrefs.netUseWebApiGateway`.
 * Default: `false` (api.vk.com) — existing messenger behaviour is preserved.
 */
object VKEndpoints {

    val API_HOST   = BuildConfig.VK_API_HOST      // https://api.vk.com
    val OAUTH_HOST = BuildConfig.VK_OAUTH_HOST    // https://oauth.vk.com
    val API_VERSION = BuildConfig.VK_API_VERSION  // 5.269

    /**
     * Mobile-web API gateway — used by m.vk.ru SPA.
     *
     * Source: research of saved m.vk.ru pages (мессенджер.zip),
     * `b-226df83bda86a954.bc377a3ddd52ea9b.js` + static config block
     * `"apiDomain":"web.api.vk.ru"`.
     *
     * Activated by `SovaPrefs.netUseWebApiGateway = true`.
     * See [method] overload with `useWebGateway` parameter.
     */
    const val WEB_API_HOST = "https://web.api.vk.ru"

    /** Hostname without scheme — used by SSL pinning & header logic in SovaApp. */
    const val WEB_API_HOSTNAME = "web.api.vk.ru"

    /**
     * Fix #154 docs upload 405: VK WEB API (web.api.vk.ru) возвращает для
     * `docs.getMessagesUploadServer` upload_url на сервер `kittenx` (web-upload
     * frontend nginx). Этот сервер на уровне nginx отклоняет POST-запросы без
     * браузерных заголовков Origin/Referer — возвращает HTTP 405 Not Allowed
     * с HTML-телом. Photos upload работает потому что `photos.*` upload-сервер
     * не имеет этой проверки.
     *
     * Эти заголовки должны добавляться к multipart POST на upload_url для
     * docs.* (и audio_message) — чтобы kittenx воспринял запрос как браузерный.
     * User-Agent тоже переопределяется на Chrome — мобильный `VKAndroidApp/...`
     * может дополнительно триггерить anti-bot фильтр на web-upload серверах.
     */
    const val WEB_ORIGIN = "https://vk.com"
    const val WEB_REFERER = "https://vk.com/"
    const val WEB_BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    fun authorizeUrl(clientId: String, redirectUri: String, scope: String, state: String = "sova2"): String {
        return "$OAUTH_HOST/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&response_type=token" +
            "&scope=$scope" +
            "&state=$state" +
            "&revoke=1" +
            "&display=mobile"
    }

    fun blankRedirectUrl(): String = "$OAUTH_HOST/blank.html"

    fun deepLinkRedirectUrl(): String = "sova2://oauth"

    /**
     * VK API method URL — default gateway (api.vk.com).
     *
     * e.g. `method("users.get")` -> `https://api.vk.com/method/users.get`
     */
    fun method(name: String): String = "$API_HOST/method/$name"

    /**
     * VK API method URL — explicit gateway selection.
     *
     * @param name       VK method, e.g. `"users.get"` or `"messages.send"`.
     * @param useWebGateway `true` → `https://web.api.vk.ru/method/<name>` (mobile-web),
     *                      `false` → `https://api.vk.com/method/<name>` (Android, default).
     */
    fun method(name: String, useWebGateway: Boolean): String {
        val host = if (useWebGateway) WEB_API_HOST else currentApiHost()
        return "$host/method/$name"
    }

    /**
     * #DOMAIN-CONFIG-API (2026-08-15): текущий API gateway из настроек доменов.
     *
     * Раньше API-вызовы хардкодили `api.vk.com` (BuildConfig.VK_API_HOST) и
     * игнорировали поле «API host» в шестерёнке доменов (AuthDomainsSettingsSheet).
     * Теперь default-gateway читается из AuthDomainsConfig.current.apiHost —
     * пользователь может переключить на api.vk.ru (миграция VK 2026) без
     * пересборки приложения. Web-gateway (web.api.vk.ru) по-прежнему отдельный
     * тумблер netUseWebApiGateway.
     */
    private fun currentApiHost(): String {
        val h = re.pinok.auth.exchange.AuthDomainsConfig.current.apiHost
        return if (h.startsWith("http")) h else "https://$h"
    }
}
