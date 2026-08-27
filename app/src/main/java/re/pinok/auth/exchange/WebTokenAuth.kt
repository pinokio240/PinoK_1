package re.pinok.auth.exchange

import android.net.Uri
import android.webkit.WebView
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import re.pinok.BuildConfig
import re.pinok.util.AppLog
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/**
 * VK Web Token Authentication — чтение токена из m.vk.ru localStorage.
 *
 * **Стратегия** (замена предыдущего подхода через login.vk.com HTTP-запросы):
 *   1. WebView загружает m.vk.ru -> пользователь логинится через VK ID
 *   2. JavaScript m.vk.ru автоматически обменивает remixsid на токен
 *      через внутренние запросы к login.vk.com
 *   3. Токен сохраняется в localStorage: `{app_id}:web_token:login:auth`
 *   4. Мы читаем токен из localStorage через evaluateJavascript()
 *
 * **Доказательство** — дамп ВК.txt (m.vk.com localStorage):
 *   7879029:web_token:login:auth -> {"access_token":"vk1.a.P4tc8s8CLC...",
 *       "expires":1784309646,"user_id":171093180,"logout_hash":"b749bef3e56b2d0742"}
 *
 * **Преимущества** перед прямыми HTTP-запросами к login.vk.com:
 *   - НЕ зависит от Sec-Fetch-* заголовков (проблема "wrong origin" #94)
 *   - НЕ зависит от TLS fingerprint
 *   - НЕ требует OkHttp — используется тот же flow, что и реальный браузер
 *   - Проще, надёжнее, меньше кода
 *
 * **Почему m.vk.ru**:
 *   - VK мигрирует на .ru домены (2025-2026)
 *   - m.vk.ru — актуальный мобильный домен VK
 *   - m.vk.com может редиректить на m.vk.ru
 *
 * **Flow в приложении**:
 *   1. AuthActivity показывает WebView с m.vk.ru
 *   2. Пользователь логинится (включая 2FA через VK ID)
 *   3. CookieManager получает remixsid
 *   4. m.vk.ru JS автоматически получает web_token и сохраняет в localStorage
 *   5. [fullAuthFlow] ждёт появления токена в localStorage и читает его
 *   6. Возвращает [WebTokenResult] с access_token + user_id + sat_token
 */
object WebTokenAuth {
    private const val TAG = "WebTokenAuth"

    // m.vk.ru app_id (VK Mobile web) — из дампа ВК.txt.
    // JavaScript m.vk.ru использует этот app_id для получения web_token.
    // localStorage key формат: "{app_id}:web_token:login:auth".
    //
    // §49 #WEB-TOKEN-CONTRACT (Fix auth-loop 2026-08-04):
    // VK ID web SDK (bundle.js module 8742, function `oe`/`iD`) использует
    // app_id из своей конфигурации, НЕ только m.vk.ru. JS может сохранять
    // токен под разными app_id (6287487=PinoK, 7879029=m.vk.ru, 7934655=VK ID QR).
    // Поэтому при чтении перечисляем ВСЕ ключи localStorage, заканчивающиеся
    // на ":web_token:login:auth", и берём первый валидный (is_active=true если массив).
    private const val WEB_APP_ID = "7879029"
    private const val LS_SAT_TOKEN_KEY = "${WEB_APP_ID}:sign_in_sat:login:auth"

    // Настройки polling'а localStorage
    private const val LS_POLL_INTERVAL_MS = 1000L  // 1 секунда между проверками
    // §49 #CONNECT-EXCHANGE-PRE-POLL (Fix auth-loop 2026-08-04):
    // 25 сек вместо 90 — ДО polling пытаемся connect_exchange_token (1-2 сек).
    // Если VK принимает истёкший web_token — свежий токен получаем мгновенно,
    // без ожидания m.vk.ru JS. Polling — только fallback если exchange неудачен.
    private const val LS_POLL_TIMEOUT_MS = 25_000L

    // #EVALJS-HANG-FIX (Fix #178): таймаут на один вызов evaluateJavascript.
    // Если WebView в навигации (redirect), callback может не прийти вообще.
    // 5 сек — достаточно для normal page, достаточно коротко чтобы polling
    // продолжился если callback потерян.
    private const val EVALJS_TIMEOUT_MS = 5_000L

    // §49 #WEB-TOKEN-RELOAD (Fix auth-loop 2026-08-04):
    // После удаления истёкшего web_token из localStorage, JS m.vk.ru НЕ
    // переинициализируется и не запрашивает свежий токен (кэширует состояние
    // «пользователь авторизован»). Поэтому нужно явно reload'нуть страницу —
    // это заставит JS переинициализировать VK ID SDK и сделать запрос
    // к login.vk.com/?act=web_token заново.
    //
    // Доказательство из лога 2026-08-04 20:20:50:
    //   20:20:50.568  localStorage содержит истёкший web_token — удаляем ключ 7879029:web_token:login:auth
    //   20:21:01.573  tryReadWebToken: localStorage.getItem returned null/blank
    //   ... 47 секунд ожидания, JS так и не положил новый токен ...
    //   20:21:38.565  WebTokenAuth failed: Токен не появился в localStorage за 60 сек
    //
    // reload на 3-й попытке (через 3 сек после старта polling) — достаточно
    // рано чтобы успеть получить новый токен за оставшиеся 22 сек.
    // §49: таймаут уменьшен с 90с до 25с — connect_exchange_token теперь
    // пытается ДО polling (1-2 сек), polling — только fallback.
    private const val RELOAD_AT_ATTEMPT = 3

    // §51 #VK-SSO-RETURN-WAIT (Fix SSO auth-loop 2026-08-05):
    // После возврата из VK app (intent://qr.vk.ru/ca?q=…) AuthActivity
    // пересоздаётся, retention переиспользует WebView на id.vk.ru/auth?…uuid=… .
    // Cookie polling тут же находит remixsid (VK app его установил) и запускает
    // fullAuthFlow. НО id.vk.ru/auth ещё не успел опросить сервер и сделать
    // редирект на m.vk.ru/login?code=… (JS polling возобновляется только когда
    // WebView снова активен). Если fullAuthFlow сразу вызовет
    // ensureSdkInitialized → loadUrl(/login?app_id=…), навигация ПРОРЫВАЕТ
    // pending JS-polling id.vk.ru/auth, и редирект с auth-кодом теряется.
    // Дальше VK видит remixsid и редиректит /login → /feed, SDK не успевает
    // инициализироваться → токен не появляется 25 сек → fail.
    //
    // РЕШЕНИЕ: detect SSO-return (URL = id.vk.ru/auth?…uuid=…), ждать до
    // SSO_RETURN_WAIT_MS пока страница сама не редиректнёт на m.vk.ru/… .
    // Если не редиректнуло — reload id.vk.ru/auth (сервер уже помечает
    // QR-сессию как подтверждённую, reload вызовет повторный polling и
    // редирект на m.vk.ru/login?code=… → SDK init → web_token в localStorage).
    private const val SSO_RETURN_WAIT_MS = 15_000L
    private const val SSO_RETURN_POLL_INTERVAL_MS = 1000L
    // §51 #VK-SSO-POST-REDIRECT-POLL: после SSO redirect на m.vk.ru даём
    // JS время положить web_token в localStorage (fresh или expired для
    // connect_exchange). Из логкэта: onPageFinished → token в LS через ~7 сек.
    // 10 сек polling покрывает это с запасом, без 25-сек LS_POLL_TIMEOUT_MS.
    private const val SSO_POST_REDIRECT_POLL_MS = 10_000L

    // §51 #VK-SSO-PATH-A (Fix VK app SSO auth-loop 2026-08-XX):
    //
    // ПРОБЛЕМА: после VK app SSO (intent://qr.vk.ru/ca?q=…) redirect chain
    // заканчивается на m.vk.ru/feed:
    //   restore_cookies → m.vk.ru/login → m.vk.ru/ → m.vk.ru/feed
    //
    // На /feed VK ID SDK JS НЕ загружается (нет app_id в URL → SDK не init).
    // remixsid cookie есть, но web_token не получается → polling 25 сек → fail.
    // Доказательство: логкэт.txt 23:27:51 remixsid found len=88, затем
    // 23:28:16 WebTokenAuth failed attempt 1/2 — токен не появился.
    //
    // ФИКС (Path A): навигируем WebView на /login?app_id=6287487 — эта
    // страница грузит VK ID SDK, который при наличии remixsid cookie делает
    // silent exchange remixsid → web_token и кладёт его в localStorage под
    // ключом "{app_id}:web_token:login:auth". tryReadWebToken читает ВСЕ
    // app_id (§49 #WEB-TOKEN-CONTRACT), поэтому найдёт токен.
    //
    // app_id=VK_WEB_CLIENT_ID (6287487) — PinoK OAuth web client. Можно
    // использовать и 7879029 (m.vk.ru own), но 6287487 выдаёт токен сразу
    // под нужным приложением. Оба работают — tryReadWebToken читает любые.
    //
    // БЕЗОПАСНОСТЬ: fullAuthFlow вызывается ТОЛЬКО после обнаружения remixsid
    // (CookieManager polling) — значит пользователь УЖЕ залогинился.
    // /login?app_id=… с валидным remixsid НЕ показывает форму входа, а сразу
    // делает silent exchange. Если consent на app_id уже дан (повторный вход) —
    // полностью тихо. Если первый вход — VK может показать consent screen,
    // но VK app SSO уже дал consent, так что тоже тихо.
    private val SDK_INIT_LOGIN_URL: String =
        "https://m.vk.ru/login?app_id=${BuildConfig.VK_WEB_CLIENT_ID}"

    // Задержка после навигации на /login?app_id=… — даём VK ID SDK время
    // загрузиться (bundle.js ~500 KB) и сделать POST login.vk.com/?act=web_token.
    // 2 сек достаточно для mobile сети; polling loop всё равно продолжает
    // проверять каждую секунду, так что даже если 2 сек мало — подхватит позже.
    private const val SDK_INIT_DELAY_MS = 2_000L

    // §49 #CONNECT-EXCHANGE-PRE-POLL (Fix auth-loop 2026-08-04):
    // OkHttpClient для прямого POST к login.vk.com/?act=connect_exchange_token.
    // НЕ используем SovaApp.httpClient — у него interceptor'ы (X-VK-Android-Client
    // и др.) которые мешают web SDK flow. Для one-off POST нужен чистый клиент.
    private val connectExchangeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // ═══════════════════════════════════════════════════════════════
    // Data classes
    // ═══════════════════════════════════════════════════════════════

    /** Полный результат auth flow — всё, что нужно для работы приложения. */
    data class WebTokenResult(
        /** `vk1.a.XXX` access_token — работает со всеми VK API methods без sig. */
        val accessToken: String,
        /** ID пользователя (из web_token response). */
        val userId: Long,
        /** Абсолютный unix-timestamp истечения в ms (0 = бессрочный при offline scope). */
        val expiresAt: Long,
        /** SAT токен для LongPoll/Queue (может быть null если не найден в localStorage). */
        val satToken: String?,
        /** logout_hash из web_token response (для future logout). */
        val logoutHash: String?,
    )

    /** Ответ из localStorage web_token. */
    data class WebTokenResponse(
        val accessToken: String,
        val userId: Long,
        val expires: Long,        // абсолютный unix timestamp в секундах
        val logoutHash: String?,
    )

    /** §49 Истёкший web_token из localStorage — для connect_exchange_token. */
    private data class ExpiredWebTokenData(
        val accessToken: String,
        val logoutHash: String?,
        val appIdKey: String?,
        val userId: Long,
        val expires: Long,
    )

    /** SAT токен ответ. */
    data class SatTokenResponse(
        val token: String,
    )

    // ═══════════════════════════════════════════════════════════════
    // Полный AUTH FLOW
    // ═══════════════════════════════════════════════════════════════

    /**
     * Полный auth flow — читает web_token из localStorage m.vk.ru.
     *
     * Вызывается после того, как AuthActivity обнаружил remixsid в CookieManager.
     * К этому моменту m.vk.ru JS уже получил (или получает) токен через
     * внутренние запросы к login.vk.com — мы просто ждём его появления
     * в localStorage и читаем через evaluateJavascript().
     *
     * @param webView WebView instance (должен быть на m.vk.ru / m.vk.com)
     * @return [Result] с [WebTokenResult] или исключением
     */
    suspend fun fullAuthFlow(webView: WebView): Result<WebTokenResult> = withContext(Dispatchers.Main) {
        runCatching {
            AppLog.i(TAG, "═══ Запуск m.vk.ru localStorage token flow ═══")
            val currentUrl = webView.url ?: "unknown"
            AppLog.i(TAG, "WebView URL: $currentUrl")

            // §41.14 #SILENT-TOKEN-FROM-WINDOW-INIT (2026-08-07):
            // m.vk.ru использует response_type=silent_token (НЕ token!).
            // VK ID SDK кладёт silent_token + silent_token_uuid в window.init.data
            // (или window.__VK_ID__ / window.vkid) ПОСЛЕ успешного логина.
            // silent_token нужно обменять на access_token через auth.getAuthData.
            //
            // Без этого шага мы ждём web_token в localStorage 25 сек, но его
            // НЕТ — VK ID отдаёт silent_token, а не access_token. m.vk.ru JS
            // сам обменивает silent_token → web_token, но если JS не отрабатывает
            // (медленная сеть, /feed редирект без VK ID SDK init) — мы таймаутим.
            //
            // Фикс: читаем silent_token из window.init через JS injection,
            // обмениваем через SilentTokenExchanger → access_token. Это
            // официальный flow VK ID для m.vk.ru (см. VK_IMPORT_API.MD §41.13-14).
            val silentTokenResult = tryReadSilentTokenFromWindowInit(webView)
            if (silentTokenResult != null) {
                AppLog.i(TAG, "Step 0: silent_token найден в window.init — " +
                    "обмен через SilentTokenExchanger (provider_app_id=${silentTokenResult.providerAppId})")
                val exchanged = exchangeSilentToken(silentTokenResult)
                if (exchanged != null) {
                    AppLog.i(TAG, "Step 0: silent_token exchange УСПЕШЕН — " +
                        "user_id=${exchanged.userId}, access_token=${exchanged.accessToken.take(12)}...")
                    return@runCatching exchanged
                }
                AppLog.w(TAG, "Step 0: silent_token exchange failed — " +
                    "fallback к localStorage web_token polling")
            } else {
                AppLog.d(TAG, "Step 0: silent_token не найден в window.init — " +
                    "fallback к localStorage web_token polling")
            }

            // Step 1: Ждём web_token в localStorage (m.vk.ru JS кладёт его туда
            // после обмена remixsid через login.vk.com).
            //
            // Fix #103: waitForWebToken теперь возвращает ТОЛЬКО свежий токен
            // (expires > now). Если в localStorage лежит истёкший токен (баг
            // m.vk.ru — JS иногда не обновляет его при silent re-login) —
            // waitForWebToken удалит его через localStorage.removeItem и
            // продолжит polling, пока m.vk.ru JS не положит свежий.
            val webTokenResp = waitForWebToken(webView)
                .getOrElse { throw it }

            // Fix #103: форматируем expires в UTC (а не в device TZ).
            // Раньше подпись была "UTC", но SimpleDateFormat без timeZone
            // использовал device default (MSK) → лог вводил в заблуждение
            // и мешал диагностике истёкших токенов.
            val expiresHuman = formatExpiresUtc(webTokenResp.expires)
            AppLog.i(
                TAG,
                "Step 1: web_token из localStorage -> OK " +
                    "(user_id=${webTokenResp.userId}, expires=$expiresHuman)"
            )

            // Fix #103: финальная проверка (defensive) — не возвращаем истёкший
            // токен наверх. waitForWebToken уже это делает, но если что-то
            // пошло не так — лучше упасть здесь с понятным сообщением, чем
            // сохранить мёртвый токен и получить белый экран.
            if (isExpired(webTokenResp.expires)) {
                throw IllegalStateException(
                    "web_token истёк (expires=$expiresHuman). " +
                        "m.vk.ru JS не обновил токен за ${LS_POLL_TIMEOUT_MS / 1000} сек. " +
                        "Возможно, сессия VK устарела — попробуйте выйти и войти заново."
                )
            }

            // Step 2: Пробуем прочитать SAT токен (не критично для работы мессенджера)
            val satToken = try {
                tryReadSatToken(webView).also {
                    if (it != null) AppLog.i(TAG, "Step 2: SAT token из localStorage -> OK")
                    else AppLog.d(TAG, "Step 2: SAT token не найден в localStorage (не критично)")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Step 2: SAT token -> ошибка (не критично): ${e.message}")
                null
            }

            // expires — абсолютный Unix timestamp в секундах -> конвертируем в ms
            // Доказательство — дамп ВК.txt: expires=1784309646 = 2026-03-16 (абсолютное время)
            val expiresAt = if (webTokenResp.expires <= 0L) 0L
            else webTokenResp.expires * 1000L

            AppLog.i(TAG, "═══ m.vk.ru token flow завершён ═══")

            WebTokenResult(
                accessToken = webTokenResp.accessToken,
                userId = webTokenResp.userId,
                expiresAt = expiresAt,
                satToken = satToken,
                logoutHash = webTokenResp.logoutHash,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §41.14 #SILENT-TOKEN-FROM-WINDOW-INIT — чтение silent_token из window.init
    // ═══════════════════════════════════════════════════════════════

    /**
     * Данные silent_token, извлечённые из window.init VK ID SDK.
     *
     * @param silentToken short-lived token для обмена через auth.getAuthData
     * @param silentTokenUuid UUID парный с silent_token
     * @param anonymousToken anonymous_token из init (для exchange, опционален)
     * @param providerAppId app_id провайдера (51421844/8223270/6287487)
     */
    private data class SilentTokenData(
        val silentToken: String,
        val silentTokenUuid: String,
        val anonymousToken: String?,
        val providerAppId: String,
    )

    /**
     * Читает silent_token + silent_token_uuid из window.init VK ID SDK.
     *
     * VK ID SDK после успешного логина кладёт в `window.init.data` (или
     * `window.__VK_ID__`, `window.vkid`) структуру с полями:
     *   - silentToken
     *   - silentTokenUUID
     *   - anonymousToken (init_parsed.json: auth.anonymous_token)
     *   - hostAppId / providerAppId
     *
     * Этот метод пробует несколько путей доступа (VK ID SDK меняет имена
     * между версиями) и возвращает первый найденный не-empty silent_token.
     *
     * @return [SilentTokenData] если найден, иначе null
     */
    private suspend fun tryReadSilentTokenFromWindowInit(webView: WebView): SilentTokenData? =
        withContext(Dispatchers.Main) {
            // JS скрипт читает window.init / __VK_ID__ / vkid и возвращает JSON
            // с silent_token, silent_token_uuid, anonymous_token, provider_app_id.
            // Если ничего не найдено — возвращает пустую строку.
            val js = """
                (function() {
                    function extract(obj, depth) {
                        if (!obj || depth > 5) return null;
                        try {
                            // Прямые поля
                            var st = obj.silentToken || obj.silent_token;
                            var uuid = obj.silentTokenUUID || obj.silent_token_uuid || obj.silentTokenUuid;
                            if (st && uuid) {
                                return {
                                    silentToken: st,
                                    silentTokenUuid: uuid,
                                    anonymousToken: obj.anonymousToken || obj.anonymous_token || null,
                                    providerAppId: obj.providerAppId || obj.hostAppId || obj.app_id || obj.appId || '6287487'
                                };
                            }
                            // Рекурсивный поиск по вложенным объектам
                            for (var key in obj) {
                                if (!obj.hasOwnProperty(key)) continue;
                                var val = obj[key];
                                if (val && typeof val === 'object' && !Array.isArray(val)) {
                                    var result = extract(val, depth + 1);
                                    if (result) return result;
                                }
                            }
                        } catch(e) {}
                        return null;
                    }
                    // Пробуем несколько известных имён VK ID SDK
                    var sources = [
                        window.init,
                        window.__VK_ID__,
                        window.vkid,
                        window.VKID,
                        window.vk && window.vk.id
                    ];
                    for (var i = 0; i < sources.length; i++) {
                        var src = sources[i];
                        if (src) {
                            var result = extract(src, 0);
                            if (result) return JSON.stringify(result);
                        }
                    }
                    return '';
                })();
            """.trimIndent()

            try {
                val raw = suspendCancellableCoroutine<String?> { cont ->
                    webView.evaluateJavascript(js) { result ->
                        // evaluateJavascript возвращает JS-значение в "кавычках" если строка,
                        // или null если undefined. Декодируем.
                        val decoded = when {
                            result == null -> null
                            result == "null" -> null
                            result == "undefined" -> null
                            result.startsWith("\"") && result.endsWith("\"") -> {
                                // JSON string — удаляем outer quotes и декодируем escapes
                                try {
                                    com.google.gson.JsonParser.parseString(result).asString
                                } catch (_: Exception) {
                                    result.substring(1, result.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                }
                            }
                            else -> result
                        }
                        cont.resume(decoded)
                    }
                }

                if (raw.isNullOrBlank()) {
                    return@withContext null
                }

                AppLog.d(TAG, "window.init silent_token raw: ${raw.take(200)}")

                // Парсим JSON ответ от JS
                val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
                val silentToken = json.get("silentToken")?.takeIf { !it.isJsonNull }?.asString
                val silentTokenUuid = json.get("silentTokenUuid")?.takeIf { !it.isJsonNull }?.asString
                if (silentToken.isNullOrBlank() || silentTokenUuid.isNullOrBlank()) {
                    AppLog.d(TAG, "tryReadSilentTokenFromWindowInit: silent_token или uuid пустой")
                    return@withContext null
                }

                val anonymousToken = json.get("anonymousToken")?.takeIf { !it.isJsonNull }?.asString
                val providerAppId = json.get("providerAppId")?.takeIf { !it.isJsonNull }?.asString
                    ?: re.pinok.BuildConfig.VK_WEB_CLIENT_ID

                AppLog.i(TAG, "tryReadSilentTokenFromWindowInit: OK " +
                    "(silent_token=${silentToken.take(12)}... uuid=$silentTokenUuid " +
                    "anon=${if (anonymousToken != null) "yes" else "no"} provider=$providerAppId)")

                SilentTokenData(
                    silentToken = silentToken,
                    silentTokenUuid = silentTokenUuid,
                    anonymousToken = anonymousToken,
                    providerAppId = providerAppId,
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "tryReadSilentTokenFromWindowInit failed: ${e.message}")
                null
            }
        }

    /**
     * #SSO-USERID-EXTRACT: извлекает userId из window.init VK ID SDK.
     *
     * После SSO «войти через приложение» remixsid захватывается, но userId в
     * storage = 0 (web_token ещё не обменян). Path 1.5 (silentRefreshViaRemixsid)
     * требует userId для `remixsid_user=<userId>` cookie header → падает.
     *
     * VK ID SDK кладёт в window.init.data.user.id актуальный userId. Читаем его
     * через JS injection (рекурсивный поиск по window.init/__VK_ID__/vkid).
     *
     * @return userId если найден (и > 0), иначе 0
     */
    suspend fun readUserIdFromWindowInit(webView: WebView): Long =
        withContext(Dispatchers.Main) {
            val js = """
                (function() {
                    function findUid(obj, depth) {
                        if (!obj || depth > 6) return 0;
                        try {
                            var uid = obj.userId || obj.user_id || obj.uid;
                            if (typeof uid === 'number' && uid > 0) return uid;
                            if (typeof uid === 'string' && /^[0-9]+$/.test(uid)) return parseInt(uid, 10);
                            var u = obj.user;
                            if (u && typeof u === 'object') {
                                var id = u.id || u.userId || u.user_id;
                                if (typeof id === 'number' && id > 0) return id;
                                if (typeof id === 'string' && /^[0-9]+$/.test(id)) return parseInt(id, 10);
                            }
                            for (var k in obj) {
                                if (!obj.hasOwnProperty(k)) continue;
                                var v = obj[k];
                                if (v && typeof v === 'object' && !Array.isArray(v)) {
                                    var r = findUid(v, depth + 1);
                                    if (r > 0) return r;
                                }
                            }
                        } catch (e) {}
                        return 0;
                    }
                    var sources = [window.init, window.__VK_ID__, window.vkid, window.VKID];
                    for (var i = 0; i < sources.length; i++) {
                        var s = sources[i];
                        if (s) {
                            var r = findUid(s, 0);
                            if (r > 0) return String(r);
                        }
                    }
                    return '0';
                })();
            """.trimIndent()
            try {
                val raw = suspendCancellableCoroutine<String?> { cont ->
                    webView.evaluateJavascript(js) { result ->
                        val decoded = when {
                            result == null || result == "null" || result == "undefined" -> null
                            result.startsWith("\"") && result.endsWith("\"") ->
                                try { com.google.gson.JsonParser.parseString(result).asString }
                                catch (_: Exception) { result.substring(1, result.length - 1) }
                            else -> result
                        }
                        cont.resume(decoded)
                    }
                }
                val uid = raw?.trim()?.toLongOrNull() ?: 0L
                if (uid > 0L) {
                    AppLog.i(TAG, "readUserIdFromWindowInit: userId=$uid")
                } else {
                    AppLog.d(TAG, "readUserIdFromWindowInit: userId не найден в window.init")
                }
                uid
            } catch (e: Exception) {
                AppLog.w(TAG, "readUserIdFromWindowInit failed: ${e.message}")
                0L
            }
        }

    /**
     * Обменивает silent_token на access_token через [SilentTokenExchanger].
     *
     * @return [WebTokenResult] если обмен успешен, иначе null
     */
    private suspend fun exchangeSilentToken(data: SilentTokenData): WebTokenResult? {
        return try {
            val app = re.pinok.SovaApp.get()
            val exchanger = SilentTokenExchanger(app.httpClient)
            val deviceId = app.exchangeStorage.deviceId()
            val result = exchanger.exchange(
                silentToken = data.silentToken,
                silentTokenUuid = data.silentTokenUuid,
                anonymousToken = data.anonymousToken,
                providerAppId = data.providerAppId,
                deviceId = deviceId,
            )
            when (result) {
                is SilentTokenExchanger.Result.Success -> {
                    val expiresAt = if (result.expiresIn <= 0L) 0L
                    else System.currentTimeMillis() + result.expiresIn * 1000L
                    WebTokenResult(
                        accessToken = result.accessToken,
                        userId = result.userId,
                        expiresAt = expiresAt,
                        satToken = null,  // SAT токен не возвращается silent_token exchange
                        logoutHash = null,  // logout_hash не возвращается (только в web_token flow)
                    )
                }
                is SilentTokenExchanger.Result.TokenInvalid -> {
                    AppLog.w(TAG, "exchangeSilentToken: TokenInvalid — ${result.message}")
                    null
                }
                is SilentTokenExchanger.Result.Unavailable -> {
                    AppLog.w(TAG, "exchangeSilentToken: Unavailable — ${result.message}")
                    null
                }
                is SilentTokenExchanger.Result.AllEndpointsFailed -> {
                    AppLog.w(TAG, "exchangeSilentToken: AllEndpointsFailed — ${result.errors}")
                    null
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "exchangeSilentToken exception: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Чтение токенов из localStorage
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ждёт появления web_token в localStorage.
     *
     * После логина m.vk.ru JS делает запросы к login.vk.com и сохраняет
     * результат в localStorage. Мы опрашиваем каждую секунду.
     *
     * Если WebView ещё на id.vk.ru (редирект после VK ID не завершён) —
     * localStorage этого домена не содержит web_token, polling продолжится
     * после автоматического редиректа на m.vk.ru.
     *
     * §49 #WEB-TOKEN-RELOAD (Fix auth-loop 2026-08-04):
     * Если в localStorage лежит истёкший токен — после его удаления JS m.vk.ru
     * НЕ запрашивает свежий автоматически. Делаем reload страницы на 3-й попытке,
     * чтобы JS переинициализировал VK ID SDK и сделал POST login.vk.com/?act=web_token.
     */
    private suspend fun waitForWebToken(webView: WebView): Result<WebTokenResponse> {
        // §51 #VK-SSO-RETURN-RACE-FIX (Fix SSO auth-loop 2026-08-05):
        // SSO-return проверка ДОЛЖНА идти ПЕРВОЙ, до любого evaluateJavascript.
        //
        // Раньше tryReadWebToken (→ evaluateJsSafely) вызывался ДО этой проверки.
        // При возврате из VK app WebView стоит на id.vk.ru/auth?uuid=…, и через
        // ~150мс стартует redirect chain: id.vk.ru → login.vk.com → m.vk.ru/login
        // → m.vk.ru/ → m.vk.ru/feed. evaluateJavascript на id.vk.ru СБРАСЫВАЕТСЯ
        // навигацией (WebView отменяет pending JS callback), evaluateJsSafely
        // ждёт полный EVALJS_TIMEOUT_MS (5 сек). За эти 5 сек URL уже m.vk.ru/feed
        // → isOnSsoReturnPage возвращает false → SSO-return блок ПРОПУЩЕН →
        // ensureSdkInitialized навигирует на /login?app_id=… → /feed redirect →
        // SDK не обменивает remixsid → 25 сек timeout → clearDeadSessionForRetry
        // чистит ВАЛИДНЫЙ remixsid → пользователь снова видит 2FA страницу.
        //
        // ФИКС: проверяем isOnSsoReturnPage (чистый URL-parsing, без JS) САМЫМ
        // ПЕРВЫМ. Если SSO-return — ждём редирект на m.vk.ru, и ТОЛЬКО ПОТОМ
        // запускаем tryReadWebToken / connect_exchange (они прочитают m.vk.ru
        // localStorage — правильный origin).
        if (isOnSsoReturnPage(webView)) {
            AppLog.i(TAG, "waitForWebToken: SSO-return detected (id.vk.ru/auth?uuid=…) — " +
                "ждём ${SSO_RETURN_WAIT_MS}ms пока JS polling увидит confirmed и " +
                "редиректнёт на m.vk.ru/login?code=… (НЕ навигируем ensureSdkInitialized — " +
                "это прорвёт pending redirect)")
            val redirected = waitForSsoReturnRedirect(webView)
            if (!redirected) {
                // Таймаут — reload id.vk.ru/auth, сервер помнит confirmation.
                reloadSsoReturnPage(webView)
                // После reload снова ждём редирект (короче — 8 сек, если и сейчас
                // не вышло, fall through к ensureSdkInitialized как fallback).
                AppLog.i(TAG, "waitForWebToken: после reload id.vk.ru/auth — " +
                    "повторное ожидание редиректа (8 сек)")
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 8_000L) {
                    val h = runCatching { Uri.parse(webView.url ?: "").host }.getOrNull() ?: ""
                    if (h == "m.vk.ru" || h == "m.vk.com") {
                        AppLog.i(TAG, "waitForWebToken: id.vk.ru/auth → $h после reload — " +
                            "продолжаем normal flow")
                        break
                    }
                    delay(SSO_RETURN_POLL_INTERVAL_MS)
                }
            }
            // §51 #VK-SSO-POST-REDIRECT-POLL: после SSO redirect на m.vk.ru
            // делаем КОРОТКИЙ polling (10 сек) для fresh token ИЛИ expired token
            // + connect_exchange. НЕ вызываем ensureSdkInitialized (навигация на
            // /login?app_id=… редиректит обратно на /feed и сбрасывает pending JS).
            //
            // #SSO-HTTP-REFRESH-FIRST (2026-08-15): ПЕРЕД polling'ом сразу пробуем
            // HTTP silentRefreshViaRemixsid (Path 1.5). На m.vk.ru/feed VK ID SDK
            // НЕ кладёт web_token (нет app_id в URL → SDK не init), поэтому polling
            // всегда отвисал полные 10 сек впустую — задержка повторного SSO-входа.
            // remixsid уже сохранён (AuthActivity.onTokenExchange → saveRemixsid),
            // Path 1.5 обменивает его за ~0.5-1с → вход мгновенно. Polling остаётся
            // fallback'ом (для случаев когда HTTP exchange отклонён).
            val httpRefreshToken = trySsoHttpRefreshViaRemixsid()
            if (httpRefreshToken != null) {
                return Result.success(httpRefreshToken)
            }

            // Почему polling а не single-shot: из логкэта (2026-08-05 09:42:54)
            // onPageFinished m.vk.ru/feed в 09:42:53.895, но expired web_token
            // появляется в localStorage только к 09:43:01 (~7 сек позже) —
            // m.vk.ru JS размещает его асинхронно после load. Single-shot проверка
            // сразу после redirect → null → fall through → ensureSdkInitialized
            // → навигация → сбой. Polling даёт JS время положить токен.
            AppLog.i(TAG, "waitForWebToken: SSO post-redirect — короткий polling " +
                "(${SSO_POST_REDIRECT_POLL_MS}ms) ждём пока m.vk.ru JS положит " +
                "web_token в localStorage (fresh или expired для connect_exchange)")
            val ssoPostStart = System.currentTimeMillis()
            var ssoPostAttempt = 0
            while (System.currentTimeMillis() - ssoPostStart < SSO_POST_REDIRECT_POLL_MS) {
                ssoPostAttempt++
                // 1) Свежий токен (SDK сам обменял remixsid → web_token)
                tryReadWebToken(webView)?.let {
                    AppLog.i(TAG, "waitForWebToken: SSO post-redirect — fresh web_token " +
                        "найден (попытка $ssoPostAttempt) — SDK обменял сам")
                    return Result.success(it)
                }
                // 2) Истёкший токен + connect_exchange_token (HTTP, 1-2 сек)
                //    remixsid свежий после VK app SSO → exchange валиден.
                val ssoExpired = readExpiredWebTokenForConnectExchange(webView)
                if (ssoExpired != null) {
                    AppLog.i(TAG, "waitForWebToken: SSO post-redirect (попытка $ssoPostAttempt) — " +
                        "found expired web_token (key=${ssoExpired.appIdKey}) — " +
                        "trying connect_exchange_token (remixsid свежий после VK app SSO)")
                    val exchanged = tryConnectExchangeViaHttp(ssoExpired)
                    if (exchanged != null) {
                        AppLog.i(TAG, "waitForWebToken: SSO connect_exchange_token OK — " +
                            "fresh token (user_id=${exchanged.userId}), " +
                            "skipping polling + ensureSdkInitialized entirely")
                        return Result.success(exchanged)
                    }
                    AppLog.w(TAG, "waitForWebToken: SSO connect_exchange_token failed " +
                        "(попытка $ssoPostAttempt, возможно logout_hash сменился после SSO) — " +
                        "продолжаем polling (возможно JS положит fresh token)")
                }
                delay(SSO_RETURN_POLL_INTERVAL_MS)
            }
            AppLog.w(TAG, "waitForWebToken: SSO post-redirect polling таймаут " +
                "(${SSO_POST_REDIRECT_POLL_MS}ms) — ни fresh, ни expired token не найден. " +
                "Falling back к ensureSdkInitialized + general polling (последний шанс)")
            // §54 #SSO-HTTP-REFRESH (2026-08-05, фикс SSO auth-loop):
            // Повторная попытка HTTP refresh (первая была ДО polling) — на случай
            // если remixsid в storage появился только что. Если тоже падает —
            // продолжаем normal flow (ensureSdkInitialized).
            val httpRefreshRetry = trySsoHttpRefreshViaRemixsid()
            if (httpRefreshRetry != null) {
                return Result.success(httpRefreshRetry)
            }
            // Если за 10 сек ничего не нашли — продолжаем normal flow
            // (ensureSdkInitialized теперь безопасен: мы на m.vk.ru).
        }

        // Сначала проверяем — возможно свежий токен уже есть.
        // tryReadWebToken возвращает null и для "нет токена", и для "истёкший",
        // поэтому одной проверки достаточно для обоих случаев.
        // (Если SSO-return блок выше уже отработал и не нашёл токен — этот
        // вызов redundant, но безобиден: пере-проверит на случай что токен
        // появился за последние миллисекунды.)
        tryReadWebToken(webView)?.let { return Result.success(it) }

        // §49 #CONNECT-EXCHANGE-PRE-POLL (Fix auth-loop 2026-08-04):
        // Прежде чем ждать 25 сек пока m.vk.ru JS переинициализируется после
        // reload — пробуем обменять истёкший web_token через HTTP.
        // login.vk.com/?act=connect_exchange_token принимает истёкший
        // access_token если logout_hash валиден. Это 1-2 сек вместо 25 сек.
        val expired = readExpiredWebTokenForConnectExchange(webView)
        if (expired != null) {
            AppLog.i(TAG, "waitForWebToken: found expired web_token (key=${expired.appIdKey}) — " +
                "trying connect_exchange_token BEFORE polling (saves ~${LS_POLL_TIMEOUT_MS / 1000}s)")
            val exchanged = tryConnectExchangeViaHttp(expired)
            if (exchanged != null) {
                AppLog.i(TAG, "waitForWebToken: connect_exchange_token OK — fresh token " +
                    "(user_id=${exchanged.userId}), skipping polling entirely")
                return Result.success(exchanged)
            }
            AppLog.w(TAG, "waitForWebToken: connect_exchange_token failed — " +
                "falling back to clear+reload+poll")
        }

        // §51 #VK-SSO-PATH-A (Fix VK app SSO auth-loop):
        // Если ни свежего, ни истёкшего web_token нет в localStorage — значит
        // мы в сценарии VK app SSO: remixsid cookie получен, но VK ID SDK JS
        // не загружен (WebView стоит на /feed или /). Навигируем на
        // /login?app_id=6287487 чтобы SDK инициализировался и обменял remixsid
        // на web_token. Без этого polling будет ждать 25 сек впустую.
        //
        // ensureSdkInitialized безопасен: если уже на /login с app_id —
        // возвращает false (не навигирует), SDK уже работает.
        val sdkNavigated = ensureSdkInitialized(webView)
        if (sdkNavigated) {
            // Даём VK ID SDK время загрузиться и сделать silent exchange.
            // Polling loop ниже продолжит проверять каждую секунду, но
            // начальная пауза предотвращает ложный reload на 3-й попытке
            // (RELOAD_AT_ATTEMPT) пока страница ещё грузится.
            AppLog.i(TAG, "waitForWebToken: ждём ${SDK_INIT_DELAY_MS}ms пока VK ID SDK " +
                "инициализируется на /login?app_id=… и обменяет remixsid → web_token")
            delay(SDK_INIT_DELAY_MS)
        }

        AppLog.i(TAG, "web_token ещё нет в localStorage (или истёк), начинаем polling (интервал ${LS_POLL_INTERVAL_MS}ms)...")

        val token = withTimeoutOrNull(LS_POLL_TIMEOUT_MS) {
            var attempt = 0
            var expiredCleared = false
            // §51 #VK-SSO-PATH-A: если ensureSdkInitialized только что навигировал
            // на /login?app_id=…, считаем что первый "reload" уже выполнен —
            // иначе ensureSdkInitialized на attempt 3 прервал бы идущий silent exchange
            // SDK (POST login.vk.com/?act=web_token был бы отменён новой
            // навигацией). Повторный reload каждые 20 сек остаётся как fallback.
            var reloadedAfterClear = sdkNavigated
            while (true) {
                delay(LS_POLL_INTERVAL_MS)
                attempt++
                val currentUrl = webView.url ?: "unknown"
                tryReadWebToken(webView)?.let { found ->
                    AppLog.i(TAG, "web_token найден в localStorage (попытка $attempt, URL=$currentUrl)")
                    return@withTimeoutOrNull found
                }

                // Fix #103: если в localStorage лежит истёкший токен — удаляем его
                // ОДИН раз, чтобы m.vk.ru JS положил свежий. JS проверяет наличие
                // ключа и не делает повторный запрос к login.vk.com, если ключ есть.
                // Поэтому без removeItem polling может ждать вечно.
                if (!expiredCleared && attempt == 2) {
                    AppLog.w(TAG, "waitForWebToken: localStorage содержит истёкший web_token — " +
                        "удаляем все ключи *:web_token:login:auth, чтобы m.vk.ru JS получил свежий")
                    clearAllWebTokenKeys(webView)
                    expiredCleared = true
                }

                // §49 #WEB-TOKEN-RELOAD (Fix auth-loop 2026-08-04):
                // После удаления истёкшего ключа JS m.vk.ru НЕ переинициализируется
                // сам (кэширует состояние «пользователь авторизован»). Делаем reload
                // страницы — это заставит JS заново инициализировать VK ID SDK и
                // сделать POST login.vk.com/?act=web_token с credentials:"include".
                //
                // reload ждём 1 сек после clear (attempt 3 = 3-я секунда polling'а),
                // чтобы removeItem точно применился. Если reload не помог — повторяем
                // каждые 20 сек (максимум 4 reload за 90 сек polling).
                if (expiredCleared && !reloadedAfterClear && attempt >= RELOAD_AT_ATTEMPT) {
                    AppLog.i(TAG, "waitForWebToken: reload m.vk.ru (attempt=$attempt) — " +
                        "JS не переинициализировался после удаления ключа, перезагружаем страницу")
                    ensureSdkInitialized(webView)
                    reloadedAfterClear = true
                } else if (expiredCleared && reloadedAfterClear && attempt % 20 == 0 && attempt < LS_POLL_TIMEOUT_MS / 1000) {
                    // Повторный reload каждые 20 сек если JS так и не положил токен.
                    AppLog.w(TAG, "waitForWebToken: повторный reload m.vk.ru (attempt=$attempt) — " +
                        "токен всё ещё не получен")
                    ensureSdkInitialized(webView)
                }

                if (attempt % 5 == 0) {
                    AppLog.d(TAG, "Ожидание web_token... ($attempt сек, URL=$currentUrl)")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        return if (token != null) {
            Result.success(token)
        } else {
            val finalUrl = webView.url ?: "unknown"
            Result.failure(
                IllegalStateException(
                    "Токен не появился в localStorage за ${LS_POLL_TIMEOUT_MS / 1000} сек. " +
                        "Финальный URL: $finalUrl. " +
                        "Возможно, m.vk.ru не завершил авторизацию или JS не получил токен от login.vk.com. " +
                        "Попробуйте войти заново."
                )
            )
        }
    }

    /**
     * #SSO-HTTP-REFRESH-FIRST (2026-08-15): HTTP silentRefreshViaRemixsid после SSO.
     *
     * remixsid уже сохранён в storage (AuthActivity.onTokenExchange → saveRemixsid).
     * Path 1.5 (silentRefreshViaRemixsid) обменивает его на access_token через
     * HTTP за ~0.5-1с БЕЗ ожидания localStorage polling (который на m.vk.ru/feed
     * всё равно бесполезен — SDK не init, 10 сек впустую).
     *
     * @return [WebTokenResponse] если обмен успешен, иначе null (remixsid мёртв
     *         или все 7 Origin стратегий отвергнуты).
     */
    private suspend fun trySsoHttpRefreshViaRemixsid(): WebTokenResponse? {
        return try {
            val app = re.pinok.SovaApp.get()
            if (!app.isExchangeAuthRepositoryInitialized()) {
                return null
            }
            AppLog.i(TAG, "waitForWebToken: SSO HTTP refresh — ensureFreshToken(force=true) " +
                "(§54 #SSO-HTTP-REFRESH) — remixsid уже сохранён, Path 1.5 должен сработать")
            val refreshedToken = app.exchangeAuthRepository.ensureFreshToken(force = true)
            if (refreshedToken != null) {
                val refreshedUserId = app.exchangeStorage.userId()
                val refreshedExpires = app.exchangeStorage.expiresAt()
                AppLog.i(TAG, "waitForWebToken: §54 #SSO-HTTP-REFRESH OK — " +
                    "Path 1.5 silentRefreshViaRemixsid обменял remixsid → access_token " +
                    "(user_id=$refreshedUserId) — skipping ensureSdkInitialized + polling")
                // expiresAt хранит АБСОЛЮТНЫЙ unix-timestamp в МС, WebTokenResponse.expires — в СЕКУНДАХ.
                val expiresSec = if (refreshedExpires > 0L) refreshedExpires / 1000L else 0L
                WebTokenResponse(
                    accessToken = refreshedToken,
                    userId = refreshedUserId,
                    expires = expiresSec,
                    logoutHash = app.exchangeStorage.logoutHash(),
                )
            } else {
                AppLog.w(TAG, "waitForWebToken: §54 #SSO-HTTP-REFRESH failed — " +
                    "ensureFreshToken(force=true) вернул null (Path 1.5 remixsid тоже мёртв " +
                    "или все 7 Origin стратегий отвергнуты)")
                null
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "waitForWebToken: §54 #SSO-HTTP-REFRESH exception: ${e.message} — continuing")
            null
        }
    }

    /**
     * Пытается прочитать web_token из localStorage m.vk.ru.
     *
     * §49 #WEB-TOKEN-CONTRACT (Fix auth-loop 2026-08-04):
     * VK ID web SDK (bundle.js module 8742) может сохранять web_token под
     * разными app_id (6287487=PinoK, 7879029=m.vk.ru, 7934655=VK ID QR).
     * Перечисляем ВСЕ ключи localStorage через Object.keys, фильтруем по
     * суффиксу ":web_token:login:auth", берём первый валидный.
     *
     * Также поддерживает мульти-аккаунтный формат — значение может быть
     * JSON-массивом токенов с полем `is_active`. В этом случае берём
     * `arr.find(e => e.is_active) || arr[0]` (как делает сам VK SDK).
     *
     * @return WebTokenResponse если токен найден и валиден, null если нет
     *         (токен ещё не получен JS, или мы на неверном origin)
     *
     * Fix #WEB-TOKEN-FALLBACK (2026-08-04): метод public — вызывается из
     * AuthActivity VkAuthWebViewScreen (cookie polling) для проверки
     * web_token в localStorage когда CookieManager не видит remixsid
     * (Android 7+ cookie isolation).
     */
    suspend fun tryReadWebToken(webView: WebView): WebTokenResponse? =
        withContext(Dispatchers.Main) {
            try {
                val raw = readRawWebTokenJson(webView)
                if (raw == null) {
                    // #EVALJS-HANG-FIX: логируем каждый null чтобы видеть в логе
                    // что tryReadWebToken НЕ завис (раньше из-за зависшего evaluateJs
                    // вообще не было логов между "Запуск flow" и "Job was cancelled").
                    val url = webView.url ?: "unknown"
                    AppLog.d(TAG, "tryReadWebToken: localStorage.getItem returned null/blank (url=$url) — token not yet in localStorage or callback lost")
                    return@withContext null
                }

                AppLog.d(TAG, "localStorage raw: ${raw.jsonStr.take(200)}")

                // Fix #103: НЕ возвращаем истёкший токен. Раньше WebTokenAuth
                // сохранял мёртвый токен → hasValidToken()=false → белый экран.
                // expires=0 означает "offline scope, без истечения" — пропускаем проверку.
                if (isExpired(raw.expires)) {
                    AppLog.w(TAG, "localStorage web_token: ИСТЁК " +
                        "(key=${raw.appIdKey}, expires=${formatExpiresUtc(raw.expires)}, " +
                        "now=${formatExpiresUtc(System.currentTimeMillis() / 1000L)}) — " +
                        "токен проигнорирован, ждём свежий от m.vk.ru JS")
                    return@withContext null
                }

                AppLog.i(TAG, "tryReadWebToken: OK (key=${raw.appIdKey}, user_id=${raw.userId}, " +
                    "expires=${if (raw.expires <= 0L) "offline" else raw.expires}s)")

                WebTokenResponse(
                    accessToken = raw.accessToken,
                    userId = raw.userId,
                    expires = raw.expires,
                    logoutHash = raw.logoutHash,
                )
            } catch (e: Exception) {
                // JSON parse error или JS error — нормально при polling'е
                AppLog.d(TAG, "tryReadWebToken: ${e.message}")
                null
            }
        }

    /**
     * §49 #CONNECT-EXCHANGE-PRE-POLL (Fix auth-loop 2026-08-04):
     * Читает ИСТЁКШИЙ web_token из localStorage — для connect_exchange_token.
     *
     * В отличие от [tryReadWebToken], НЕ проверяет expiry. connect_exchange_token
     * принимает истёкший access_token если logout_hash валиден (VK проверяет
     * сессию по logout_hash, а не по access_token expiry).
     *
     * @return ExpiredWebTokenData если в localStorage есть web_token с access_token,
     *         null если localStorage пуст или токен невалидный (не vk1.a.*)
     */
    private suspend fun readExpiredWebTokenForConnectExchange(webView: WebView): ExpiredWebTokenData? =
        withContext(Dispatchers.Main) {
            try {
                val raw = readRawWebTokenJson(webView) ?: return@withContext null

                AppLog.d(TAG, "readExpiredWebToken: found (key=${raw.appIdKey}, user_id=${raw.userId}, " +
                    "expires=${if (raw.expires <= 0L) "offline" else raw.expires}s)")

                ExpiredWebTokenData(
                    accessToken = raw.accessToken,
                    logoutHash = raw.logoutHash,
                    appIdKey = raw.appIdKey,
                    userId = raw.userId,
                    expires = raw.expires,
                )
            } catch (e: Exception) {
                AppLog.d(TAG, "readExpiredWebToken: ${e.message}")
                null
            }
        }

    /**
     * §49 #WEB-TOKEN-CONTRACT (Fix auth-loop 2026-08-04):
     * Базовое чтение web_token из localStorage. Один JS-запрос для всех
     * callers'ов — [tryReadWebToken] (с проверкой expiry) и
     * [readExpiredWebTokenForConnectExchange] (без проверки).
     *
     * VK ID web SDK (bundle.js module 8742) может сохранять web_token под
     * разными app_id (6287487=PinoK, 7879029=m.vk.ru, 7934655=VK ID QR).
     * Перечисляем ВСЕ ключи localStorage через Object.keys, фильтруем по
     * суффиксу ":web_token:login:auth", берём первый валидный.
     *
     * Поддерживает мульти-аккаунтный формат — значение может быть
     * JSON-массивом токенов с полем `is_active`. Берём
     * `arr.find(e => e.is_active) || arr[0]` (как делает сам VK SDK).
     *
     * @return RawWebTokenJson если в localStorage есть валидный vk1.a.* токен,
     *         null если localStorage пуст или токен невалидный
     */
    private data class RawWebTokenJson(
        val accessToken: String,
        val userId: Long,
        val expires: Long,        // абсолютный unix timestamp в секундах
        val logoutHash: String?,
        val appIdKey: String?,
        val jsonStr: String,      // сырой JSON (для лога)
    )

    private suspend fun readRawWebTokenJson(webView: WebView): RawWebTokenJson? =
        withContext(Dispatchers.Main) {
            try {
                val js = "(function(){var keys=Object.keys(localStorage);" +
                    "var matches=keys.filter(function(k){return k.indexOf(':web_token:login:auth')>-1 && k.length - ':web_token:login:auth'.length === k.lastIndexOf(':web_token:login:auth');});" +
                    "if(matches.length===0)return null;" +
                    "var result=null;" +
                    "for(var i=0;i<matches.length;i++){" +
                    "  var v=localStorage.getItem(matches[i]);" +
                    "  if(!v)continue;" +
                    "  try{var parsed=JSON.parse(v);" +
                    "    // Мульти-аккаунт: массив токенов — берём is_active=true или первый." +
                    "    if(Array.isArray(parsed)){" +
                    "      var active=parsed.find(function(e){return e&&e.is_active===true})||parsed[0];" +
                    "      if(active&&active.access_token){active.__app_id_key=matches[i];result=JSON.stringify(active);break;}" +
                    "    } else if(parsed&&parsed.access_token){" +
                    "      parsed.__app_id_key=matches[i];result=JSON.stringify(parsed);break;" +
                    "    }" +
                    "  }catch(e){}" +
                    "}" +
                    "return result;" +
                    "})()"
                val jsonStr = evaluateJsSafely(webView, js)
                if (jsonStr.isNullOrBlank() || jsonStr == "null") return@withContext null

                val parsed = JsonParser.parseString(jsonStr).asJsonObject

                val atEl = parsed.get("access_token")
                if (atEl == null || atEl.isJsonNull || !atEl.isJsonPrimitive) return@withContext null
                val accessToken = atEl.asString
                if (accessToken.isBlank() || !accessToken.startsWith("vk1.a.")) {
                    AppLog.d(TAG, "localStorage web_token: невалидный access_token")
                    return@withContext null
                }

                val uidEl = parsed.get("user_id")
                // user_id может отсутствовать в edge cases — возвращаем 0L,
                // tryConnectExchangeViaHttp использует expired.userId только как
                // fallback если response не содержит user_id.
                val userId = if (uidEl != null && !uidEl.isJsonNull && uidEl.isJsonPrimitive) uidEl.asLong else 0L

                val expEl = parsed.get("expires")
                val expires = if (expEl != null && !expEl.isJsonNull && expEl.isJsonPrimitive) expEl.asLong else 0L

                val lhEl = parsed.get("logout_hash")
                val logoutHash = if (lhEl != null && !lhEl.isJsonNull && lhEl.isJsonPrimitive) lhEl.asString else null

                val akEl = parsed.get("__app_id_key")
                val appIdKey = if (akEl != null && !akEl.isJsonNull && akEl.isJsonPrimitive) akEl.asString else null

                RawWebTokenJson(
                    accessToken = accessToken,
                    userId = userId,
                    expires = expires,
                    logoutHash = logoutHash,
                    appIdKey = appIdKey,
                    jsonStr = jsonStr,
                )
            } catch (e: Exception) {
                AppLog.d(TAG, "readRawWebTokenJson: ${e.message}")
                null
            }
        }

    /**
     * §49 #CONNECT-EXCHANGE-PRE-POLL (Fix auth-loop 2026-08-04):
     * Прямой HTTP POST к login.vk.com/?act=connect_exchange_token.
     *
     * Обменивает истёкший web_token (access_token + logout_hash) на свежий
     * access_token. VK проверяет валидность сессии по logout_hash, а не по
     * access_token expiry — поэтому истёкший токен принимается.
     *
     * Тот же endpoint что ExchangeAuthRepository.tryConnectExchangeToken(),
     * но вызывается из WebTokenAuth (object) ДО polling'а localStorage.
     *
     * @param expired истёкший web_token из localStorage
     * @return свежий WebTokenResponse если exchange успешен, null иначе
     */
    private suspend fun tryConnectExchangeViaHttp(expired: ExpiredWebTokenData): WebTokenResponse? =
        withContext(Dispatchers.IO) {
            val logoutHash = expired.logoutHash
            if (logoutHash == null || logoutHash.isBlank()) {
                AppLog.d(TAG, "tryConnectExchangeViaHttp: skip — logoutHash null/blank")
                return@withContext null
            }
            val url = "https://login.vk.com/?act=connect_exchange_token"
            val formBody = FormBody.Builder()
                .add("token", expired.accessToken)
                .add("hash", logoutHash)
                .build()
            val req = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://id.vk.ru/")
                .header("Origin", "https://id.vk.ru")
                .build()
            try {
                AppLog.d(TAG, "tryConnectExchangeViaHttp: POST $url " +
                    "(token=${expired.accessToken.take(8)}…, hash=${logoutHash.take(6)}…)")
                connectExchangeClient.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val respBody = resp.body
                    val body = if (respBody != null) respBody.string() else ""
                    if (code !in 200..299) {
                        AppLog.w(TAG, "tryConnectExchangeViaHttp: HTTP $code body=${body.take(300)}")
                        return@withContext null
                    }
                    val json = JsonParser.parseString(body).asJsonObject
                    val errorObj = json.getAsJsonObject("error")
                    if (errorObj != null) {
                        val errCodeEl = errorObj.get("error_code")
                        val errMsgEl = errorObj.get("error_msg")
                        val errCode = if (errCodeEl != null && !errCodeEl.isJsonNull) errCodeEl.asInt else null
                        val errMsg = if (errMsgEl != null && !errMsgEl.isJsonNull) errMsgEl.asString else null
                        AppLog.w(TAG, "tryConnectExchangeViaHttp: VK error — code=$errCode msg=$errMsg")
                        return@withContext null
                    }

                    val respElem = json.get("response")
                    val responseElem = if (respElem != null) respElem else json
                    var token = ""
                    var userId = expired.userId
                    var expires = 0L
                    var respLogoutHash = logoutHash

                    if (responseElem.isJsonArray) {
                        val arr = responseElem.asJsonArray
                        if (arr.isEmpty) {
                            AppLog.w(TAG, "tryConnectExchangeViaHttp: empty response array")
                            return@withContext null
                        }
                        var activeObj: com.google.gson.JsonObject? = null
                        for (el in arr) {
                            if (!el.isJsonObject) continue
                            val obj = el.asJsonObject
                            val isActiveEl = obj.get("is_active")
                            if (isActiveEl != null && !isActiveEl.isJsonNull && isActiveEl.asBoolean) {
                                activeObj = obj
                                break
                            }
                        }
                        if (activeObj == null) {
                            val firstEl = arr.first()
                            if (firstEl.isJsonObject) {
                                activeObj = firstEl.asJsonObject
                            }
                        }
                        if (activeObj == null) {
                            AppLog.w(TAG, "tryConnectExchangeViaHttp: array elements not objects")
                            return@withContext null
                        }
                        val tEl = activeObj.get("access_token")
                        if (tEl == null || tEl.isJsonNull || !tEl.isJsonPrimitive) {
                            AppLog.w(TAG, "tryConnectExchangeViaHttp: no access_token in array")
                            return@withContext null
                        }
                        token = tEl.asString
                        val uEl = activeObj.get("user_id")
                        if (uEl != null && !uEl.isJsonNull && uEl.isJsonPrimitive) userId = uEl.asLong
                        val eEl = activeObj.get("expires")
                        if (eEl != null && !eEl.isJsonNull && eEl.isJsonPrimitive) expires = eEl.asLong
                        val lhEl2 = activeObj.get("logout_hash")
                        if (lhEl2 != null && !lhEl2.isJsonNull && lhEl2.isJsonPrimitive) respLogoutHash = lhEl2.asString
                    } else if (responseElem.isJsonObject) {
                        val obj = responseElem.asJsonObject
                        val tEl = obj.get("access_token")
                        if (tEl == null || tEl.isJsonNull || !tEl.isJsonPrimitive) {
                            AppLog.w(TAG, "tryConnectExchangeViaHttp: no access_token in object")
                            return@withContext null
                        }
                        token = tEl.asString
                        val uEl = obj.get("user_id")
                        if (uEl != null && !uEl.isJsonNull && uEl.isJsonPrimitive) userId = uEl.asLong
                        val eEl = obj.get("expires")
                        if (eEl != null && !eEl.isJsonNull && eEl.isJsonPrimitive) expires = eEl.asLong
                        val lhEl2 = obj.get("logout_hash")
                        if (lhEl2 != null && !lhEl2.isJsonNull && lhEl2.isJsonPrimitive) respLogoutHash = lhEl2.asString
                    } else {
                        AppLog.w(TAG, "tryConnectExchangeViaHttp: unexpected response type")
                        return@withContext null
                    }

                    if (token.isBlank()) {
                        AppLog.w(TAG, "tryConnectExchangeViaHttp: empty access_token")
                        return@withContext null
                    }

                    AppLog.i(TAG, "tryConnectExchangeViaHttp: OK — fresh token " +
                        "${token.take(8)}…${token.takeLast(4)} (user_id=$userId, " +
                        "expires=${if (expires <= 0L) "offline" else expires}s)")

                    WebTokenResponse(
                        accessToken = token,
                        userId = userId,
                        expires = expires,
                        logoutHash = respLogoutHash,
                    )
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "tryConnectExchangeViaHttp failed: ${e.message}")
                null
            }
        }

    /**
     * §51 #VK-SSO-PATH-A / #VK-SSO-RETURN-WAIT:
     * Тип страницы авторизации в WebView. Объединяет логику
     * [isOnSdkLoginPage] и [isOnSsoReturnPage] в один when-friendly enum.
     */
    private enum class AuthPage {
        /** /login?app_id=… — VK ID SDK инициализирован. */
        SDK_LOGIN,
        /** id.vk.ru/auth?…uuid=… — QR/SSO polling после возврата из VK app. */
        SSO_RETURN,
        /** Любая другая страница (/feed, /, id.vk.ru/auth без uuid). */
        OTHER,
    }

    /**
     * Объединённая проверка типа страницы. Заменяет [isOnSdkLoginPage] и
     * [isOnSsoReturnPage] — одна точка парсинга URL для всех callers'ов.
     *
     * @return [AuthPage] для текущего URL WebView
     */
    private fun isOnPage(webView: WebView, page: AuthPage): Boolean {
        val currentUrl = webView.url ?: return false
        val host = runCatching { Uri.parse(currentUrl).host }.getOrNull() ?: return false
        return when (page) {
            AuthPage.SDK_LOGIN -> {
                // /login с app_id в query — SDK инициализирован.
                // Проверяем оба домена (m.vk.ru и m.vk.com) на случай редиректа.
                currentUrl.contains("/login") && currentUrl.contains("app_id=")
            }
            AuthPage.SSO_RETURN -> {
                // §51 #VK-SSO-RETURN-WAIT: detect SSO return page.
                // URL вида https://id.vk.ru/auth?...uuid=… (или id.vk.com) — страница
                // VK ID QR-polling, инициировавшая SSO через intent://.
                // uuid= параметр — признак QR/SSO страницы (обычный /auth без uuid
                // тоже может быть, но без SSO intent не запускается).
                (host == "id.vk.ru" || host == "id.vk.com") &&
                    currentUrl.contains("/auth") &&
                    currentUrl.contains("uuid=")
            }
            AuthPage.OTHER -> false
        }
    }

    /**
     * §49 #WEB-TOKEN-RELOAD (Fix auth-loop 2026-08-04):
     * Удаляет ВСЕ ключи localStorage, заканчивающиеся на ":web_token:login:auth".
     * Нужно перед reload m.vk.ru — иначе JS видит существующий (пусть и истёкший)
     * токен и не делает POST login.vk.com/?act=web_token.
     */
    private suspend fun clearAllWebTokenKeys(webView: WebView) {
        val js = "(function(){var keys=Object.keys(localStorage);" +
            "var removed=[];" +
            "keys.forEach(function(k){" +
            "  if(k.indexOf(':web_token:login:auth')>-1 && k.length - ':web_token:login:auth'.length === k.lastIndexOf(':web_token:login:auth')){" +
            "    localStorage.removeItem(k);removed.push(k);" +
            "  }" +
            "});" +
            "return removed.join(',');" +
            "})()"
        val removed = evaluateJsSafely(webView, js)
        AppLog.i(TAG, "clearAllWebTokenKeys: removed keys = ${removed ?: "(none)"}")
    }

    /**
     * §51 #VK-SSO-PATH-A (Fix VK app SSO auth-loop):
     * Проверяет, находится ли WebView на странице, где VK ID SDK JS
     * инициализирован (т.е. /login с параметром app_id).
     *
     * VK ID SDK грузится ТОЛЬКО на /login?app_id=… — на /feed и / его нет.
     * Без SDK remixsid cookie не обменивается на web_token, polling висит.
     *
     * @return true если WebView уже на /login с app_id (SDK должен работать),
     *         false в противном случае (нужна навигация).
     */
    private fun isOnSdkLoginPage(webView: WebView): Boolean =
        isOnPage(webView, AuthPage.SDK_LOGIN)

    /**
     * §51 #VK-SSO-RETURN-WAIT: detect SSO return page.
     * URL вида https://id.vk.ru/auth?...uuid=… (или id.vk.com) — страница
     * VK ID QR-polling, инициировавшая SSO через intent://.
     * На ней JS polling проверяет статус QR-сессии и редиректит на
     * redirect_uri после подтверждения в VK app.
     */
    private fun isOnSsoReturnPage(webView: WebView): Boolean =
        isOnPage(webView, AuthPage.SSO_RETURN)

    /**
     * §51 #VK-SSO-RETURN-WAIT: ждёт пока id.vk.ru/auth сделает редирект
     * на m.vk.ru/… (любой путь) после подтверждения SSO в VK app.
     *
     * ВАЖНО (Kotlin nested comments): внутри KDoc-блока нельзя писать
     * последовательность «slash + star» (два символа: косая черта и звёздочка)
     * — Kotlin поддерживает ВЛОЖЕННЫЕ блочные комментарии, поэтому такая
     * пара символов открывает новый уровень вложенности. Один закрывающий
     * «star + slash» в конце KDoc закрывает только внутренний уровень, и весь
     * последующий код до следующего закрывающего маркера становится
     * комментарием. Симптомы: «unresolved reference» на все методы ниже +
     * «Unclosed comment» в конце файла + «Missing }». Поэтому в комментариях
     * пишем «m.vk.ru/…» или «m.vk.ru/<path>» вместо glob-шаблона со звёздочкой.
     *
     * @return true если страница редиректнула на m.vk.ru/… (нормальный flow),
     *         false если таймаут — тогда нужен reload id.vk.ru/auth.
     */
    private suspend fun waitForSsoReturnRedirect(webView: WebView): Boolean {
        val start = System.currentTimeMillis()
        var waited = 0L
        while (waited < SSO_RETURN_WAIT_MS) {
            val url = webView.url ?: ""
            val host = runCatching { Uri.parse(url).host }.getOrNull() ?: ""
            // Как только ушли с id.vk.ru/auth на m.vk.ru/… — редирект случился.
            if (host == "m.vk.ru" || host == "m.vk.com") {
                AppLog.i(TAG, "waitForSsoReturnRedirect: id.vk.ru/auth → $host " +
                    "(редирект через ${waited}ms) — продолжаем normal flow")
                return true
            }
            // Если ушли на другой домен (login.vk.ru промежуточный) — тоже OK,
            // скоро приземлимся на m.vk.ru.
            if (host == "login.vk.ru" || host == "login.vk.com") {
                AppLog.d(TAG, "waitForSsoReturnRedirect: промежуточный редирект на $host, " +
                    "продолжаем ждать m.vk.ru (${waited}ms)")
            }
            delay(SSO_RETURN_POLL_INTERVAL_MS)
            waited = System.currentTimeMillis() - start
        }
        AppLog.w(TAG, "waitForSsoReturnRedirect: таймаут ${SSO_RETURN_WAIT_MS}ms — " +
            "id.vk.ru/auth не редиректнуло, пробуем reload страницы")
        return false
    }

    /**
     * §51 #VK-SSO-RETURN-WAIT: reload id.vk.ru/auth страницы.
     * Сервер уже помечает QR-сессию (uuid) как подтверждённую VK app'ом,
     * повторная загрузка страницы вызовет JS polling, который тут же
     * увидит "confirmed" и сделает редирект на m.vk.ru/login?code=… .
     */
    private fun reloadSsoReturnPage(webView: WebView) {
        val url = webView.url ?: return
        AppLog.i(TAG, "reloadSsoReturnPage: reload($url) — сервер должен сразу " +
            "вернуть confirmed и сделать редирект на m.vk.ru/login?code=…")
        try {
            webView.reload()
        } catch (e: Exception) {
            AppLog.w(TAG, "reloadSsoReturnPage: reload failed: ${e.message}")
        }
    }

    /**
     * §51 #VK-SSO-PATH-A (Fix VK app SSO auth-loop):
     * Навигирует WebView на /login?app_id=6287487, если текущая страница
     * НЕ является /login с app_id (т.е. VK ID SDK не загружен).
     *
     * Сценарий: после VK app SSO redirect chain заканчивается на /feed.
     * remixsid cookie есть, но /feed не грузит VK ID SDK → web_token не
     * появляется. Навигация на /login?app_id=… заставляет SDK инициализироваться
     * и сделать silent exchange remixsid → web_token.
     *
     * §49 #WEB-TOKEN-RELOAD: также используется как reload после clearAllWebTokenKeys —
     * /login?app_id=… единственная страница m.vk.ru, где VK ID SDK гарантированно
     * инициализируется (раньше грузили m.vk.ru/, но при наличии remixsid VK
     * редиректит / → /feed, где SDK не грузится).
     *
     * Идемпотентен: если уже на /login с app_id — не навигирует.
     *
     * @return true если навигация выполнена, false если уже на нужной странице
     *         или loadUrl упал с Exception
     */
    private fun ensureSdkInitialized(webView: WebView): Boolean {
        if (isOnPage(webView, AuthPage.SDK_LOGIN)) {
            AppLog.d(TAG, "ensureSdkInitialized: уже на /login с app_id — " +
                "VK ID SDK должен быть инициализирован (${webView.url})")
            return false
        }
        val currentUrl = webView.url ?: "(null)"
        AppLog.i(TAG, "ensureSdkInitialized: навигируем на SDK init page " +
            "(текущий URL: $currentUrl — VK ID SDK не загружен, web_token не будет получен)")
        return try {
            webView.loadUrl(SDK_INIT_LOGIN_URL)
            AppLog.i(TAG, "ensureSdkInitialized: loadUrl($SDK_INIT_LOGIN_URL) — " +
                "VK ID SDK должен инициализироваться и обменять remixsid → web_token")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "ensureSdkInitialized: loadUrl failed: ${e.message}")
            false
        }
    }

    /**
     * Пытается прочитать SAT токен из localStorage (non-blocking).
     *
     * SAT нужен для LongPoll v4 (queuev4.vk.com).
     * Если его нет в localStorage — мессенджер сделает retry
     * через messages.getLongPollServer с access_token.
     */
    private suspend fun tryReadSatToken(webView: WebView): String? =
        withContext(Dispatchers.Main) {
            try {
                val jsonStr = evaluateJsSafely(webView, "localStorage.getItem('$LS_SAT_TOKEN_KEY')")
                if (jsonStr.isNullOrBlank() || jsonStr == "null") return@withContext null

                val parsed = JsonParser.parseString(jsonStr).asJsonObject

                // Формат может быть {"token":"SAT_XXX"} или {"access_token":"SAT_XXX"}
                parsed.get("token")?.asString
                    ?: parsed.get("access_token")?.asString
            } catch (e: Exception) {
                null
            }
        }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные
    // ═══════════════════════════════════════════════════════════════

    /**
     * Безопасное выполнение JS в WebView с очисткой кавычек.
     *
     * evaluateJavascript оборачивает результат в кавычки:
     *   JS возвращает: {"access_token":"vk1.a..."}
     *   evaluateJavascript возвращает: "\"{\"access_token\":\"vk1.a...\"}\""
     *
     * Мы очищаем обёртку: trim('"') + unescape.
     *
     * #EVALJS-HANG-FIX (Fix #178): withTimeoutOrNull(5с) защищает от зависания.
     *
     * Проблема: `webView.evaluateJavascript(js) { value -> ... }` вызывает
     * callback ТОЛЬКО когда страница готова принять JS. Если WebView в момент
     * вызова находится в навигации (redirect login.vk.ru → m.vk.ru/feed),
     * callback может НЕ быть вызванным вообще — coroutine висит вечно.
     *
     * Доказательство из лога 2026-08-04 17:10:21–17:13:30:
     *   17:10:21.397 — WebTokenAuth: ═══ Запуск m.vk.ru localStorage token flow ═══
     *   17:10:21.398 — WebView URL: https://m.vk.ru/
     *   17:10:21.597 — navigate: https://m.vk.ru/feed (page load finished)
     *   ... 3 минуты тишины ...
     *   17:13:30.217 — tryReadWebToken: Job was cancelled (AuthActivity disposed)
     *
     * Первый `tryReadWebToken` вызывается на строке 202 (вне withTimeoutOrNull
     * polling loop). Если evaluateJavascript не fired'нул — весь fullAuthFlow
     * висит 3 минуты пока AuthActivity не dispose'нет.
     *
     * Фикс: withTimeoutOrNull(5_000L) вокруг suspendCancellableCoroutine.
     * Если callback не пришел за 5 сек — возвращаем null (как "токена нет"),
     * polling loop продолжит попытки.
     */
    private suspend fun evaluateJsSafely(webView: WebView, js: String): String? =
        withTimeoutOrNull(EVALJS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    webView.evaluateJavascript(js) { value ->
                        val cleaned = value
                            ?.trim('"')
                            ?.replace("\\\"", "\"")
                            ?.replace("\\\\", "\\")
                        cont.resume(cleaned)
                    }
                } catch (e: Exception) {
                    // WebView может быть уничтожен — не крашимся
                    AppLog.w(TAG, "evaluateJsSafely error: ${e.message}")
                    cont.resume(null)
                }
            }
        }

    // ═══════════════════════════════════════════════════════════════
    // Expiry-хелперы (единая точка проверки истечения токена)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Проверяет истёк ли токен. expires — абсолютный Unix timestamp в секундах.
     * expires <= 0 означает "offline scope, без истечения" — не считается истёкшим.
     *
     * Fix #103: единственная точка проверки expiry (раньше дублировалась в
     * tryReadWebToken и fullAuthFlow).
     */
    private fun isExpired(expiresSec: Long): Boolean =
        expiresSec > 0L && expiresSec * 1000L <= System.currentTimeMillis()

    /**
     * Форматирует expires (секунды) в человекочитаемую строку UTC.
     * Для expires <= 0 возвращает "never (offline scope)".
     *
     * Fix #103: форматируем в UTC (а не в device TZ). Раньше подпись была "UTC",
     * но SimpleDateFormat без timeZone использовал device default (MSK) →
     * лог вводил в заблуждение и мешал диагностике истёкших токенов.
     */
    private fun formatExpiresUtc(expiresSec: Long): String {
        if (expiresSec <= 0L) return "never (offline scope)"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return sdf.format(java.util.Date(expiresSec * 1000L)) + " UTC"
    }
}