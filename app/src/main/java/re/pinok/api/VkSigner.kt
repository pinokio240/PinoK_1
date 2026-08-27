package re.pinok.api

import java.security.MessageDigest

/**
 * Подпись запросов к VK API по схеме оригинального VK Android client.
 *
 * Извлечено из декомпилята VK 8.178 (ветка SOVA):
 *  - AndroidManifest.xml: `<meta-data android:name="api_secret" android:value="hHbZxrka2uZ6jB1inYsH"/>`
 *  - AndroidManifest.xml: `<meta-data android:name="api_id" android:value="2274003"/>`
 *  - `xsna.tzs.f()` — формирует sig = md5("/method/NAME?params&access_token&v&https=1" + secret)
 *  - `xsna.xn30.a.a(str)` — MD5 hex (RFC lowercase).
 *  - `com.vk.auth.api.models.AuthResult.c` — поле `secret` (user_secret), возвращается
 *    VK ТОЛЬКО при Direct Auth (oauth.vk.com/access_token grant_type=password).
 *    OAuth WebView (response_type=token) НЕ возвращает secret — sig невозможен.
 *
 * ⚠️ КРИТИЧНО (#28): для подписи пользовательских запросов используется
 * `user_secret` (AuthResult.secret), а НЕ `app_secret` (hHbZxrka2uZ6jB1inYsH).
 * `app_secret` используется ТОЛЬКО сервером VK при token exchange
 * (oauth.vk.com/access_token, id.vk.com/auth_by_exchange_token) — клиент
 * его не использует для подписи API-запросов.
 *
 * Формула (строго из декомпилята tzs.f()):
 *   1. query = urlEncode(allParams)   (включая access_token, v, https, lang, device)
 *   2. sigSource = "/method/" + METHOD + "?" + query + user_secret
 *   3. sig = md5_hex(sigSource)
 *   4. final POST body = query + "&sig=" + sig
 *
 * VK принимает `sig` только для client_id=2274003 (VK Android) и только
 * для методов помеченных `needs sig` (messages.*, audio.*, execute и др.).
 * Без sig эти методы возвращают error 15 (access denied).
 *
 * Reference: com.vk.api.sdk.okhttp.b#f() → tzs.e() → tzs.f() → xn30.a.a()
 */
object VkSigner {

    /**
     * App credentials оригинального VK Android client.
     * Хранятся в AndroidManifest как meta-data (см. com.vk.api.base.b#g()).
     *
     * ⚠️ APP_SECRET НЕ используется для подписи пользовательских запросов —
     * только сервером VK при token exchange. Для sig нужен user_secret
     * (см. [sign]). Оставлено как reference.
     */
    const val APP_ID: Int = 2274003
    const val APP_SECRET: String = "hHbZxrka2uZ6jB1inYsH"

    /**
     * Методы VK API, для которых VK сервер **требует** sig (иначе error 15).
     * Получено из анализа логов (#19): messages.* дают Access denied без sig.
     *
     * Публичные методы (users.get, newsfeed.get, wall.get, friends.get, groups.get,
     * photos.get, video.get, etc.) работают с обычным access_token без sig —
     * их сюда добавлять НЕ нужно.
     */
    private val SIGNED_METHODS: Set<String> = setOf(
        // Messaging
        "messages.getConversations",
        "messages.getConversationsById",
        "messages.getHistory",
        "messages.getById",
        "messages.send",
        "messages.getLongPollServer",
        "messages.getLongPollHistory",
        "messages.markAsRead",
        "messages.markAsAnswered",
        "messages.setTyping",
        "messages.delete",
        "messages.deleteConversation",
        "messages.search",
        "messages.getDialogs",
        "messages.createChat",
        "messages.editChat",
        "messages.addChatUser",
        "messages.removeChatUser",
        "messages.getChat",
        "messages.getChatUsers",
        "messages.editMessage",
        "messages.react",
        // Audio (требует sig даже если бы метод жил — для execute-based обходов)
        "audio.get",
        "audio.getById",
        "audio.getCatalog",
        "audio.getRecommendations",
        "audio.search",
        "audio.getPopular",
        "audio.getAudioById",
        // Execute (server-side code runner)
        "execute",
    )

    /** Минимальное совпадение по префиксу для редко вызываемых вариантов. */
    private val SIGNED_PREFIXES: Set<String> = setOf(
        "messages.",
        "audio.",
    )

    /** Возвращает true если метод требует sig-подписи. */
    fun requiresSig(method: String): Boolean {
        if (method in SIGNED_METHODS) return true
        // Точное имя без аргументов: "messages.getConversations" → проверяем префикс
        val baseName = method.substringBefore('?').trim()
        if (baseName in SIGNED_METHODS) return true
        // Не подписываем execute.*, только execute (он один)
        if (baseName == "execute") return true
        // Проверяем префиксы для редко вызываемых вариантов
        if (SIGNED_PREFIXES.any { baseName.startsWith(it) }) return true
        return false
    }

    /**
     * #33: Проверяет, является ли access_token веб-токеном (vk1.a.XXX).
     *
     * Веб-токены выдаются oauth.vk.com при client_id=6287487 (vk.com desktop web).
     * VK API trustит им как официальному веб-клиенту — все methods работают
     * БЕЗ sig и БЕЗ user_secret, даже из SIGNED_METHODS (messages.*, audio.*).
     *
     * Источник: docs/references/ВК_веб_токены_референс.txt — веб-клиент
     * vk.com использует web_token напрямую в API без подписи.
     *
     * @param accessToken токен из TokenStorage
     * @return true если токен вебовый (vk1.a.*) → sig НЕ нужен
     */
    fun isWebToken(accessToken: String?): Boolean {
        if (accessToken.isNullOrBlank()) return false
        // Веб-токены имеют формат vk1.a.XXX (новая версия токенов VK).
        // Старые токены (Direct Auth 2274003 — официальный VK Android) — без префикса vk1.
        return accessToken.startsWith("vk1.a.")
    }

    /**
     * #33: Решает, нужен ли sig для конкретного метода + токена.
     *
     * - Веб-токены (vk1.a.*) → НИКОГДА не подписываем (VK trustит)
     * - Обычные токены + метод из SIGNED_METHODS → подписываем если есть user_secret
     * - Публичные методы → не подписываем
     */
    fun shouldSign(method: String, accessToken: String?, userSecret: String?): Boolean {
        // Веб-токены никогда не требуют sig
        if (isWebToken(accessToken)) return false
        // Без user_secret подписывать бессмысленно
        if (userSecret.isNullOrBlank()) return false
        // Проверяем, требует ли метод sig
        return requiresSig(method)
    }

    /**
     * Вычисляет sig для запроса.
     *
     * ⚠️ Подпись считается по ПОЛНОМУ набору параметров запроса (включая
     * access_token, v, https, lang, device) — ровно в том порядке, в каком
     * они будут отправлены в FormBody. Иначе сервер VK recomputes sig из
     * пришедших params и получит другое значение → error 15.
     *
     * @param method     имя метода, напр. "messages.getConversations"
     * @param allParams  ВСЕ параметры запроса в порядке отправки (args + v + https
     *                   + access_token + lang + device). БЕЗ sig.
     * @param userSecret user_secret из AuthResult.secret (Direct Auth only).
     *                   НЕ app_secret (hHbZxrka2uZ6jB1inYsH) — он серверный.
     * @return           32-char hex MD5 sig
     */
    fun sign(method: String, allParams: Map<String, String>, userSecret: String): String {
        // 1. Формируем querystring как делает Uri.Builder (URLEncoder RFC 3986).
        val query = buildQueryString(allParams)

        // 2. sigSource = "/method/NAME?query" + user_secret  (НЕ app_secret!)
        val sigSource = "/method/$method?$query$userSecret"

        // 3. MD5 hex lowercase
        return md5Hex(sigSource)
    }

    /**
     * Кодирует параметры в querystring как Android Uri.Builder:
     * `key=value&key2=value2` где value = URLEncoder.encode(value, "UTF-8")
     * заменяет '+' на '%20', НЕ экранирует '~','*','-','_','.'.
     */
    private fun buildQueryString(params: Map<String, String>): String {
        val sb = StringBuilder(params.size * 24)
        var first = true
        for ((k, v) in params) {
            if (!first) sb.append('&')
            first = false
            sb.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8.name()))
            sb.append('=')
            sb.append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20"))
        }
        return sb.toString()
    }

    /** MD5 → 32 hex lowercase (как xn30.a.a()). */
    private fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        val hex = "0123456789abcdef".toCharArray()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(hex[v ushr 4])
            sb.append(hex[v and 0x0F])
        }
        return sb.toString()
    }
}
