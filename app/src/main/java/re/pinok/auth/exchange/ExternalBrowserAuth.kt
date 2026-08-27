package re.pinok.auth.exchange

import android.webkit.CookieManager
import android.webkit.ValueCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import re.pinok.util.AppLog

/**
 * Попытка найти авторизацию VK во внешнем браузере (Chrome, Samsung Internet, etc.).
 *
 * **Принцип:**
 * Android [CookieManager] — это SINGLETON, который разделяется между WebView
 * во ВСЕХ приложениях и системным браузером (Chrome). Если пользователь залогинен
 * в VK через Chrome (m.vk.ru / vk.ru), то CookieManager уже содержит remixsid
 * cookie — нам не нужно заставлять пользователя логиниться заново.
 *
 * **Почему это работает:**
 *   - CookieManager.getInstance() возвращает один и тот же экземпляр для всех WebView
 *   - Chrome на Android использует WebView engine (Chromium) для рендеринга
 *   - Cookies для домена m.vk.ru доступны через getCookie("https://m.vk.ru")
 *   - remixsid — главный cookie авторизации VK, устанавливается после полного логина
 *
 * **Flow:**
 *   1. Пользователь открывает AuthActivity
 *   2. LandingScreen автоматически вызывает [tryFindExistingAuth]()
 *   3. Мы проверяем CookieManager на remixsid для VK доменов
 *   4. Если remixsid найден:
 *      a. Создаём скрытый WebView, загружаем m.vk.ru
 *      b. Ждём пока m.vk.ru JS обменяет remixsid на web_token
 *      c. Читаем web_token из localStorage через WebTokenAuth
 *   5. Если remixsid НЕ найден — показываем обычный WebView для логина
 *
 * **Ограничения:**
 *   - CookieManager синхронизируется асинхронно — первый вызов getCookie()
 *     после холодного старта может вернуть null даже если Chrome авторизован.
 *     Поэтому рекомендуется вызывать [warmUpCookieManager] при старте приложения.
 *   - На некоторых устройствах (особенно с Custom Tabs) cookies Chrome
 *     могут быть изолированы от CookieManager. В этом случае метод не сработает
 *     и пользователь увидит обычный WebView логин.
 *   - WebView должен загрузить m.vk.ru чтобы JS получил web_token — нельзя
 *     просто использовать remixsid напрямую (нужен access_token vk1.a.*).
 */
object ExternalBrowserAuth {

    private const val TAG = "ExtBrowserAuth"

    /**
     * VK домены для проверки cookies.
     *
     * Fix #189: список доменов динамический — берётся из AuthDomainsConfig,
     * который учитывает пользовательские настройки (.com / .ru). Также
     * включает зеркальные домены (если юзер на .ru, проверяем и .com).
     *
     * Вычисляется при каждом вызове tryFindExistingAuth() / clearAllVkCookies()
     * — это дёшево (List из ~16 строк, distinct).
     */
    private val VK_COOKIE_URLS: List<String>
        get() = AuthDomainsConfig.vkCookieUrls()

    /**
     * Результат поиска авторизации во внешнем браузере.
     *
     * #SESSION-COOKIES (2026-08-04): теперь захватываем ТРИ cookie, не только
     * remixsid. `p` (persistent login, .login.vk.ru) критичен для cross-IP
     * silent refresh — без него VK отвергает silentRefreshViaRemixsid после
     * смены сети. `remixnsid` (vk1.a.*, vk.ru) — новая VK ID сессия.
     *
     * @param found true если хотя бы remixsid найден (минимум для Path 1.5)
     * @param remixsid значение remixsid cookie (если найден)
     * @param pCookie значение p cookie с .login.vk.ru (если найден)
     * @param remixnsid значение remixnsid cookie (если найден)
     * @param source домен, на котором был найден remixsid (для логов)
     */
    data class ExistingAuthResult(
        val found: Boolean,
        val remixsid: String? = null,
        val pCookie: String? = null,
        val remixnsid: String? = null,
        val source: String? = null,
        // §55 #SSO-FULL-COOKIE-SET: 6 кук браузерного набора (httoken, nttpid,
        // uacck, uas, dmgr, mvkfp) — VK требует полный cookie-set для
        // login.vk.ru/?act=web_token, без них silent refresh падает (SSO loop §54).
        val httoken: String? = null,
        val remixnttpid: String? = null,
        val remixuacck: String? = null,
        val remixuas: String? = null,
        val remixdmgr: String? = null,
        val remixmvkFp: String? = null,
        // #CALLS-ANTIFRAUD (2026-08-23): антифрод-токены.
        val remixstid: String? = null,
        val remixstlid: String? = null,
    )

    /**
     * Пытается найти session cookies (9 кук браузерного набора) в CookieManager.
     *
     * Вызывать на Main thread или любом — CookieManager потокобезопасен.
     *
     * §57 #COOKIE-CAPTURE-UNIFY: делегирует чтение в
     * [RemixsidCapturer.snapshotCookies] — единственную точку чтения
     * CookieManager во всём приложении. Раньше тут была 2-я копия логики
     * парсинга (дублировала RemixsidCapturer). Теперь [ExistingAuthResult] —
     * тонкий wrapper над [RemixsidCapturer.CapturedCookies], сохранённый для
     * обратной совместимости с call sites, которые читают поле `source`.
     *
     * #SESSION-COOKIES: обходим все VK домены, собираем ВСЕ 9 cookie за один
     * проход (remixsid на .vk.ru, p на .login.vk.ru, remixnsid на vk.ru,
     * httoken на .vk.ru + .web.api.vk.ru — каждый на своём домене).
     * found=true если найден ХОТЯ БЫ remixsid (минимальное требование Path 1.5);
     * остальные 8 кук дополняют для надёжности silent refresh.
     *
     * @return [ExistingAuthResult] с найденными cookies или пустой результат
     */
    fun tryFindExistingAuth(): ExistingAuthResult {
        val cm = CookieManager.getInstance()
        // Убедимся что CookieManager включён
        if (!cm.acceptCookie()) {
            cm.setAcceptCookie(true)
        }

        // §57 #COOKIE-CAPTURE-UNIFY: единая точка чтения.
        val captured = RemixsidCapturer.snapshotCookies()
        return if (captured != null) {
            AppLog.i(TAG, "Session cookies: remixsid=yes (source=${captured.source}), " +
                "p=${if (captured.pCookie != null) "yes" else "no"}, " +
                "remixnsid=${if (captured.remixnsid != null) "yes" else "no"}, " +
                "httoken=${if (captured.httoken != null) "yes" else "no"}, " +
                "nttpid=${if (captured.remixnttpid != null) "yes" else "no"}, " +
                "uacck=${if (captured.remixuacck != null) "yes" else "no"}, " +
                "uas=${if (captured.remixuas != null) "yes" else "no"}, " +
                "dmgr=${if (captured.remixdmgr != null) "yes" else "no"}, " +
                "mvkfp=${if (captured.remixmvkFp != null) "yes" else "no"}, " +
                "stid=${if (captured.remixstid != null) "yes" else "no"}, " +
                "stlid=${if (captured.remixstlid != null) "yes" else "no"}")
            ExistingAuthResult(
                found = true,
                remixsid = captured.remixsid,
                pCookie = captured.pCookie,
                remixnsid = captured.remixnsid,
                source = captured.source,
                httoken = captured.httoken,
                remixnttpid = captured.remixnttpid,
                remixuacck = captured.remixuacck,
                remixuas = captured.remixuas,
                remixdmgr = captured.remixdmgr,
                remixmvkFp = captured.remixmvkFp,
                remixstid = captured.remixstid,
                remixstlid = captured.remixstlid,
            )
        } else {
            AppLog.d(TAG, "remixsid НЕ найден в CookieManager — внешний браузер не авторизован")
            ExistingAuthResult(found = false)
        }
    }

    /**
     * Предварительный прогрев CookieManager.
     *
     * CookieManager лениво загружает cookies из хранилища. Первый вызов
     * getCookie() после холодного старта может вернуть null даже если
     * Chrome авторизован в VK. Этот метод заставляет CookieManager
     * загрузить cookies заранее.
     *
     * Вызывать в Application.onCreate() или при старте MainActivity.
     */
    fun warmUpCookieManager() {
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            // Fix #184: setAcceptThirdPartyCookies(null, true) вызывал NPE:
            // "Attempt to invoke virtual method 'android.webkit.WebSettings
            // android.webkit.WebView.getSettings()' on a null object reference"
            //
            // CookieManager.setAcceptThirdPartyCookies(WebView, boolean) требует
            // NON-NULL WebView — внутри вызывает webview.getSettings() для
            // установки флага. Передача null = NPE.
            //
            // setAcceptThirdPartyCookies — per-WebView setting, а НЕ global.
            // Глобально third-party cookies включаются через setAcceptCookie(true)
            // (уже сделано выше). Per-WebView third-party cookies настраиваются в
            // самом WebView (см. AuthActivity: CookieManager.getInstance()
            // .setAcceptThirdPartyCookies(webView, true)).
            //
            // Поэтому здесь убираем setAcceptThirdPartyCookies(null, true) —
            // он не нужен (global accept уже включён) и вызывал NPE.
            // Прогрев — вызываем getCookie для VK доменов чтобы CookieManager
            // загрузил cookies из хранилища в память.
            for (url in VK_COOKIE_URLS) {
                try {
                    cm.getCookie(url)
                } catch (_: Exception) {}
            }
            AppLog.d(TAG, "CookieManager warmed up")
        } catch (e: Exception) {
            AppLog.d(TAG, "CookieManager warmup error: ${e.message}")
        }
    }

    /**
     * Fix #182: Clears ALL VK cookies from [CookieManager].
     *
     * КОНТЕКСТ БАГА: кнопка «Выйти из аккаунта» в ProfileScreen вызывала только
     * `ExchangeTokenStorage.clear()` (чистит SharedPreferences: access_token,
     * remixsid, exchange_token, logout_hash). Но `CookieManager` — это ОТДЕЛЬНОЕ
     * хранилище (singleton WebView), и `remixsid` cookie оставался там.
     *
     * При следующем запуске AuthActivity → `tryFindExistingAuth()` находил
     * `remixsid` в CookieManager → автоматически перелогинивал пользователя
     * (silent re-login через WebView exchange). Пользователь нажимал «Выйти» →
     * видел экран логина → через секунду оказывался снова залогинен.
     *
     * РЕШЕНИЕ: при logout явно очищаем cookies для ВСЕХ VK доменов через
     * `setCookie(url, "remixsid=; Max-Age=0; Path=/")` (sync — обновляет
     * in-memory store) + `removeAllCookies` (belt-and-suspenders, СИНХРОННО
     * через suspendCancellableCoroutine) + `flush` (persist to disk).
     * После этого `tryFindExistingAuth()` вернёт found=false.
     *
     * #LOGOUT-WEBVIEW-HANG (2026-08-06, фикс бага «не грузится с первого раза
     * пока не остановишь приложение»):
     *
     * Раньше `removeAllCookies(null)` вызывался БЕЗ callback — fire-and-forget.
     * CookieManager.removeAllCookies ASYNC: возвращает immediately, фактическое
     * удаление происходит на background thread через неопределённое время
     * (10-500мс в зависимости от нагрузки). Сценарий бага:
     *   T=0:    signOut() → clearAllVkCookies() → setCookie Max-Age=0 (sync) +
     *           removeAllCookies(null) (ASYNC!) → flush() → return
     *   T=200мс: AuthActivity onCreate
     *   T=1800мс: WebView.loadUrl("https://m.vk.ru")
     *           → m.vk.ru сервер ставит cookies (remixlang, remixdt, remixscreen)
     *   T=???мс: removeAllCookies callback fires → ВЫТИРАЕТ cookies которые
     *           m.vk.ru ТОЛЬКО ЧТО поставил → JS m.vk.ru видит inconsistent
     *           state → redirect loop → onPageFinished NEVER fires →
     *           страница висит 60+ сек → пользователь видит белый экран
     *           → force-stop убивает процесс → при рестарте removeAllCookies
     *           уже завершён (persisted to disk) → m.vk.ru грузится нормально
     *
     * ФИКС: `clearAllVkCookies` теперь suspend. `removeAllCookies` вызывается
     * с callback, обёрнутым в `suspendCancellableCoroutine`. Вызывающий
     * (signOut / clearDeadSessionForRetry) ждёт завершения removeAllCookies
     * (с timeout 2 сек) ПЕРЕД тем как запустить AuthActivity.
     *
     * VK cookies для удаления (34 имени — полный набор из дампа браузерной
     * сессии VK.ru + §55 #SSO-FULL-COOKIE-SET):
     *   - Session: remixsid, remixsidst, remixstid, remixtmpsid, remixcookie_type
     *   - Persistent login: p, remixnsid, remixpasskey, remixuserkey, remixuserid
     *   - §55 anti-fraud: httoken, remixnttpid, remixuacck, remixuas, remixdmgr,
     *     remixmvk-fp
     *   - UI prefs: remixlang, remixdt, remixff, remixmdevice, remixcolor_scheme_mode,
     *     remixdark_color_scheme, remixsf, remixstlid, remixsuc, remixua, remixscreen,
     *     remixseenads, remixcontext, remixfilter_copy, remixrts_audio,
     *     remixcurrent_audio, remixgp
     *   - Other: audio_vol
     * Удаляем ВСЕ — это logout, не нужно сохранять ничего.
     */
    suspend fun clearAllVkCookies() {
        val cm = CookieManager.getInstance()
        // Fix #189: используем AuthDomainsConfig.vkCookieUrls() — он включает
        // все варианты (.com + .ru) и учитывает пользовательские настройки.
        val allVkUrls = VK_COOKIE_URLS
        // Все известные VK cookie-имена (34 — полный набор из дампа VK.ru
        // + §55 anti-fraud cookies). Max-Age=0 + expires в прошлом →
        // browser удаляет cookie немедленно. Path=/ покрывает все пути.
        val vkCookieNames = listOf(
            // Session
            "remixsid", "remixsidst", "remixstid", "remixtmpsid", "remixcookie_type",
            // Persistent login + VK ID
            "p", "remixnsid", "remixpasskey", "remixuserkey", "remixuserid",
            // §55 #SSO-FULL-COOKIE-SET: anti-CSRF + anti-fraud (без них VK
            // отвергает silent refresh, а после logout они не вытирались)
            "httoken", "remixnttpid", "remixuacck", "remixuas", "remixdmgr",
            "remixmvk-fp",
            // UI prefs + other
            "remixlang", "remixdt", "remixff", "remixmdevice",
            "remixcolor_scheme_mode", "remixdark_color_scheme", "remixsf",
            "remixstlid", "remixsuc", "remixua", "remixscreen", "remixseenads",
            "remixcontext", "remixfilter_copy", "remixrts_audio",
            "remixcurrent_audio", "remixgp", "audio_vol",
        )
        var clearedCount = 0
        for (url in allVkUrls) {
            try {
                for (name in vkCookieNames) {
                    // Max-Age=0 + Expires в прошлом → cookie удаляется.
                    // Domain не указываем — setCookie применит к URL'у.
                    cm.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                    clearedCount++
                }
            } catch (_: Exception) {}
        }
        // #LOGOUT-WEBVIEW-HANG: removeAllCookies СИНХРОННО через callback.
        // Раньше null callback → async fire-and-forget → race с WebView load
        // → m.vk.ru JS redirect loop → onPageFinished never fires →
        // «не грузится с первого раза пока не остановишь приложение».
        // Теперь ждём завершения (с timeout 2 сек — если callback не придёт,
        // setCookie Max-Age=0 уже вытер все перечисленные cookies).
        val removedAll = withTimeoutOrNull(REMOVE_ALL_COOKIES_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                cm.removeAllCookies(ValueCallback<Boolean> { result ->
                    if (cont.isActive) {
                        cont.resume(result)
                    }
                })
            }
        }
        // flush() — persist изменений на диск (sync).
        try {
            cm.flush()
        } catch (_: Exception) {}
        val waitStatus = if (removedAll != null) "sync" else "timeout(${REMOVE_ALL_COOKIES_TIMEOUT_MS}ms)"
        AppLog.i(TAG, "clearAllVkCookies: cleared $clearedCount cookie-slots across ${allVkUrls.size} domains + removeAllCookies($waitStatus) + flush")
    }

    /** Timeout для ожидания removeAllCookies callback. 2 сек — достаточно
     *  для завершения async cleanup на любом устройстве. Если callback
     *  не придёт — setCookie Max-Age=0 уже вытер все 34 cookie синхронно,
     *  так что timeout безопасен (просто без belt-and-suspenders). */
    private const val REMOVE_ALL_COOKIES_TIMEOUT_MS = 2_000L
}