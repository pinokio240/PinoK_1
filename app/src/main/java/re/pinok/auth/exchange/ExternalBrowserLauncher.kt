package re.pinok.auth.exchange

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import re.pinok.BuildConfig
import re.pinok.util.AppLog

/**
 * Fix #187: запуск авторизации во ВНЕШНЕМ браузере (Chrome, Яндекс, Samsung Internet).
 *
 * Fix #188: silent sign-in + явный выбор браузера через системный chooser.
 *
 * ─────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ ПРЕДЫДУЩАЯ ВЕРСИЯ НЕ РАБОТАЛА У ПОЛЬЗОВАТЕЛЯ
 * ─────────────────────────────────────────────────────────────────────
 *   1. tryLaunchCustomTabs() использовал CustomTabsIntent.launchUrl() —
 *      он открывает браузер ПО УМОЛЧАНИЮ. Если у пользователя default =
 *      Chrome, а VK залогинен в Яндекс — открывался Chrome без сессии
 *      VK → форма логина → «авторизация не подхватывается».
 *
 *   2. buildOAuthUrl() содержал &revoke=1 — VK ПРИНУДИТЕЛЬНО показывал
 *      экран «Разрешить доступ» даже если пользователь залогинен. Это
 *      НЕ silent sign-in: пользователь видит форму и думает «у меня же
 *      уже залогинено, почему снова спрашивает?».
 *
 * ─────────────────────────────────────────────────────────────────────
 * ЧТО ИЗМЕНИЛОСЬ В Fix #188
 * ─────────────────────────────────────────────────────────────────────
 *   1. Главный метод launch() теперь сначала вызывает tryLaunchChooser() —
 *      системный Intent.createChooser() показывает список ВСЕХ установленных
 *      браузеров. Пользователь САМ выбирает тот, где залогинен в VK.
 *
 *   2. &revoke=1 убран по умолчанию. Теперь:
 *      - Если залогинен в выбранном браузере И уже давал разрешения этому
 *        client_id (6287487) → VK сразу делает redirect на
 *        sova2://oauth#access_token=... (silent sign-in, 0 кликов).
 *      - Если залогинен но НЕ давал разрешения → VK покажет экран
 *        «Разрешить доступ» (1 клик).
 *      - Если НЕ залогинен → форма логина в браузере.
 *
 *   3. Custom Tabs и ACTION_VIEW остались как fallback (если chooser
 *      недоступен или пользователь отменил выбор).
 *
 * ─────────────────────────────────────────────────────────────────────
 * Fix #190: ПЕРЕКЛЮЧЕНИЕ НА blank.html + ВСТАВКА ТОКЕНА ВРУЧНУЮ
 * ─────────────────────────────────────────────────────────────────────
 * Fix #187/#188 использовали redirect_uri=sova2://oauth (custom scheme).
 * Но client_id=6287487 — это официальный web-client ВК (vk.com десктоп),
 * и ВК разрешил для него ТОЛЬКО redirect_uri=https://oauth.vk.com/blank.html.
 * Custom scheme sova2://oauth НЕ зарегистрирован в настройках этого
 * приложения на стороне ВК → ВК отклоняет его с ошибкой:
 *   {"error":"invalid_request",
 *    "error_description":"redirect_uri is incorrect, check application
 *    redirect uri in the settings page"}
 *
 * Custom scheme redirect работает ТОЛЬКО для client_id, которые мы сами
 * зарегистрировали бы на dev.vk.com. Но мы используем чужой публичный
 * client_id (6287487) — для него это не работало и не будет.
 *
 * Fix #190: используем redirect_uri=https://oauth.vk.com/blank.html
 * (ВК принимает его для 6287487). После авторизации браузер показывает
 * пустую страницу, в адресной строке — URL вида:
 *   https://oauth.vk.com/blank.html#access_token=vk1.a.XXX&user_id=...&expires_in=...
 * Пользователь копирует этот URL из адресной строки и вставляет в
 * приложение (поле «Вставьте ссылку» на LandingScreen). Приложение
 * парсит access_token + user_id и сохраняет.
 *
 * ─────────────────────────────────────────────────────────────────────
 * OAuth FLOW (Fix #190)
 * ─────────────────────────────────────────────────────────────────────
 *   1. launch() → tryLaunchChooser(activity, url)
 *      → системный диалог «Открыть в…» со списком браузеров
 *   2. Пользователь выбирает Яндекс/Chrome/...
 *   3. Браузер открывает https://oauth.vk.com/authorize?client_id=6287487
 *      &redirect_uri=https://oauth.vk.com/blank.html
 *      &display=page&response_type=token
 *   4. Если в этом браузере уже залогинен VK → VK (без revoke=1) сразу
 *      редиректит на https://oauth.vk.com/blank.html#access_token=...
 *   5. Браузер показывает пустую страницу; access_token виден в URL
 *      адресной строки.
 *   6. Пользователь копирует URL (долгий тап на адресной строке → Копировать),
 *      возвращается в PinoK, вставляет в поле «Вставьте ссылку».
 *   7. AuthActivity.parseExternalBrowserToken() извлекает access_token +
 *      user_id из вставленной строки и вызывает viewModel.submitOAuthToken().
 *
 * ─────────────────────────────────────────────────────────────────────
 * БЕЗОПАСНОСТЬ
 * ─────────────────────────────────────────────────────────────────────
 *   - access_token в URL fragment (#), не в query — не логируется
 *     прокси/серверами при redirect.
 *   - Пользователь ЯВНО нажимает кнопку «Войти через Яндекс/Chrome» и
 *     ЯВНО выбирает браузер в chooser — это осознанное действие.
 *   - Вставка токена — тоже осознанное действие пользователя.
 *   - Custom scheme sova2://oauth и intent-filter в MainActivity ОСТАЮТСЯ
 *     для будущего использования (если зарегистрируем собственный client_id).
 */
object ExternalBrowserLauncher {

    private const val TAG = "ExtBrowserLauncher"

    /**
     * Custom scheme для OAuth redirect (должен совпадать с intent-filter в
     * AndroidManifest.xml: <data android:scheme="sova2" android:host="oauth" />).
     */
    const val REDIRECT_SCHEME = "sova2"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    /**
     * Известные пакеты браузеров для приоритетного отображения в chooser.
     * Используется только для логирования (какие браузеры найдены) —
     * выбор делает пользователь в системном chooser.
     */
    private val KNOWN_BROWSER_PACKAGES = listOf(
        "com.yandex.browser",                    // Яндекс Браузер
        "com.android.chrome",                    // Chrome (stable)
        "com.chrome.beta",                       // Chrome Beta
        "com.chrome.dev",                        // Chrome Dev
        "com.sec.android.app.sbrowser",          // Samsung Internet
        "org.mozilla.firefox",                   // Firefox
        "org.mozilla.firefox_beta",              // Firefox Beta
        "com.opera.browser",                     // Opera
        "com.microsoft.emmx",                    // Edge
        "com.brave.browser",                     // Brave
        "com.duckduckgo.mobile.android",         // DuckDuckGo
        "com.vivaldi.browser",                   // Vivaldi
    )

    /**
     * Запускает OAuth авторизацию во внешнем браузере.
     *
     * Fix #188: показывает системный chooser для явного выбора браузера.
     * Это решает проблему «у меня VK в Яндексе, а открывается Chrome» —
     * пользователь сам выбирает нужный браузер.
     *
     * #AUTH-SIMPLIFY (2026-08-15): прямой запуск VK app (VkAppDirectLauncher)
     * удалён — QR-SSO не работает в WebView и был источником циклов.
     * «Войти через Яндекс/Chrome» теперь всегда идёт через browser chooser.
     *
     * @param activity — для startActivity.
     * @return true если браузер запущен, false если ни один браузер не найден.
     */
    fun launch(activity: Activity): Boolean {
        // Fix #189: URL строится через AuthDomainsConfig — учитывает
        // пользовательские домены (oauth.vk.com / oauth.vk.ru) и forceRevoke.
        val url = buildOAuthUrl()
        AppLog.i(TAG, "Запуск внешнего браузера для OAuth: $url")

        // Логируем какие браузеры установлены — помогает пользователю
        // понять, что chooser покажет именно его браузеры.
        logInstalledBrowsers(activity)

        // Способ 1 (Fix #188, MAIN): системный chooser — пользователь сам
        // выбирает браузер. Это гарантирует, что откроется ИМЕННО тот
        // браузер, где залогинен VK (а не default browser).
        val chooserLaunched = tryLaunchChooser(activity, url)
        if (chooserLaunched) {
            AppLog.i(TAG, "Browser chooser запущен успешно (Fix #188)")
            return true
        }

        // Способ 2 (fallback): Custom Tabs — открывает вкладку в браузере
        // по умолчанию. Используется если chooser недоступен (редкость).
        val customTabsLaunched = tryLaunchCustomTabs(activity, url)
        if (customTabsLaunched) {
            AppLog.i(TAG, "Custom Tabs запущен (fallback)")
            return true
        }

        // Способ 3 (deep fallback): ACTION_VIEW без chooser.
        val actionViewLaunched = tryLaunchActionView(activity, url)
        if (actionViewLaunched) {
            AppLog.i(TAG, "ACTION_VIEW запущен (deep fallback)")
            return true
        }

        AppLog.e(TAG, "Ни один браузер не установлен на устройстве — OAuth невозможен", null)
        return false
    }

    /**
     * Fix #188: Системный chooser — пользователь явно выбирает браузер.
     *
     * Intent.createChooser() показывает диалог «Открыть в…» со списком
     * ВСЕХ приложений, которые могут обработать ACTION_VIEW + http/https URL.
     * На Android это все установленные браузеры.
     *
     * Важно: chooser ВСЕГДА показывает диалог, даже если default browser
     * установлен. Это гарантирует выбор.
     *
     * На Android 11+ (API 30) нужно объявить <queries> в манифесте для
     * ACTION_VIEW http/https — это уже сделано (см. AndroidManifest.xml).
     */
    private fun tryLaunchChooser(activity: Activity, url: String): Boolean {
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val chooserTitle = "Выберите браузер для входа в VK"
            val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            AppLog.d(TAG, "Chooser: браузер не найден (${e.message})")
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "Chooser failed: ${e.message}")
            false
        }
    }

    /**
     * Логирует какие браузеры установлены — помогает диагностировать
     * «у меня VK в Яндексе, но chooser пустой». Также помогает понять
     * что chooser реально найдёт на устройстве.
     */
    private fun logInstalledBrowsers(activity: Activity) {
        try {
            val pm = activity.packageManager
            val found = mutableListOf<String>()
            for (pkg in KNOWN_BROWSER_PACKAGES) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    found.add(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    // не установлен — нормально
                }
            }
            AppLog.i(TAG, "Установленные браузеры: ${found.ifEmpty { "none from known list (будет показан системный chooser)" }}")
        } catch (e: Exception) {
            AppLog.d(TAG, "logInstalledBrowsers error: ${e.message}")
        }
    }

    /**
     * Fallback: Custom Tabs — открывает вкладку в браузере по умолчанию.
     * Не показывает chooser, использует default browser.
     */
    private fun tryLaunchCustomTabs(activity: Activity, url: String): Boolean {
        return try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(activity, Uri.parse(url))
            true
        } catch (e: ActivityNotFoundException) {
            AppLog.d(TAG, "Custom Tabs не доступен: ${e.message}")
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "Custom Tabs launch failed: ${e.message}")
            false
        }
    }

    /**
     * Deep fallback: ACTION_VIEW с scheme http/https. Системный chooser
     * может показать список браузеров (зависит от OEM).
     */
    private fun tryLaunchActionView(activity: Activity, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            AppLog.d(TAG, "ACTION_VIEW: браузер не найден (${e.message})")
            false
        } catch (e: Exception) {
            AppLog.w(TAG, "ACTION_VIEW failed: ${e.message}")
            false
        }
    }

    /**
     * Строит OAuth URL для VK implicit flow.
     *
     * Fix #190: используем redirect_uri=https://oauth.vk.com/blank.html
     * (через AuthDomainsConfig.oauthBlankRedirectUrl()). Это ЕДИНСТВЕННЫЙ
     * redirect_uri, который ВК принимает для client_id=6287487. Custom
     * scheme sova2://oauth НЕ работает (не зарегистрирован для 6287487).
     *
     * После авторизации браузер покажет пустую страницу; access_token
     * будет в URL адресной строки (#access_token=...&user_id=...).
     * Пользователь копирует URL и вставляет в приложение.
     *
     * Fix #189: домен oauthHost и client_id из AuthDomainsConfig.
     * Fix #188: silent sign-in по умолчанию (БЕЗ revoke=1), если
     * AuthDomainsConfig.forceRevoke = false.
     */
    private fun buildOAuthUrl(): String {
        val clientId = AuthDomainsConfig.webClientId()
        val scope = OAUTH_SCOPE
        val apiVersion = BuildConfig.VK_API_VERSION
        val silent = !AuthDomainsConfig.current.forceRevoke
        // Fix #194/#195: redirect_uri — всегда https://oauth.vk.com/blank.html
        // (заглушка существует только на .com; на .ru отдаёт 405).
        val redirectUri = AuthDomainsConfig.oauthBlankRedirectUrl()
        // Fix #195: authorize endpoint тоже хардкод на oauth.vk.com —
        // VK игнорирует redirect_uri parameter для blank.html и сам решает
        // на какой домен редиректить (по домену authorize). Если authorize
        // на .ru → redirect на .ru/blank.html → 405. Поэтому весь flow
        // external browser идёт через .com, минуя AuthDomainsConfig.oauthHost.
        return AuthDomainsConfig.oauthAuthorizeUrlCom(
            clientId = clientId,
            scope = scope,
            redirectUri = redirectUri,
            apiVersion = apiVersion,
            silent = silent,
        )
    }

    /**
     * Полный web-scope (как в OAuthWebViewActivity.OAUTH_SCOPE).
     * Включает все permissions: messages, audio, video, docs, wall, groups,
     * stats, email, market, notifications, stories, pages, offline.
     */
    private const val OAUTH_SCOPE = "notify,friends,photos,audio,video,stories," +
        "pages,links,status,notes,messages,wall,ads,offline,docs," +
        "groups,stats,email,market,notifications"
}
