package re.pinok.auth.auth2

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.gson.JsonParser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import re.pinok.util.AppLog

/**
 * #AUTH2-VKLITE-SCREEN (2026-10-17): автономный WebView-экран входа «Войти в аккаунт 2».
 *
 * Сделан ПО ОБРАЗЦУ vk-lite-auth WebVkActivity (docs/users/vk-lite-auth-0.12.0-source.zip):
 *   - грузит m.vk.ru/login?app_id=7879029 в WebView — VK сам принимает номер/пароль/2FA/капчу;
 *   - НЕ вмешивается в процесс входа (нет принудительной повторной инициализации SDK);
 *   - простой polling (раз в 750мс) читает web_token из localStorage ТОЛЬКО с trusted-origin
 *     страниц (m.vk.ru / m.vk.com) — когда токен появляется → завершает вход;
 *   - в отличие от VkAuthWebViewScreenV2, НЕ трогает общий WebTokenAuth/remixsid-механизм —
 *     потому он и не вызывает белый экран (VkAuthWebViewScreenV2 сам себе мешал,
 *     форсируя ensureSdkInitialized на m.vk.ru поверх активной формы входа id.vk.ru).
 *
 * @param onTokenReady вызывается с (access_token, user_id) когда web_token найден
 *   и проверен (user_id>0, not blank). Сохранение — на стороне вызывающего.
 * @param onDismiss пользователь нажал назад/закрыл (сейчас вызывается системным Back,
 *   см. BackHandler в AuthV2Activity при необходимости).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun VkLiteWebViewAuthScreen(
    startUrl: String,
    onTokenReady: (accessToken: String, userId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var completed by remember { mutableStateOf(false) }

    // Системная кнопка «назад»: назад в истории WebView, либо закрыть экран.
    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDismiss()
        }
    }

    fun handleToken(raw: String?) {
        if (completed) return
        if (raw.isNullOrBlank() || raw == "null") return
        try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val el = obj.get("access_token")
            if (el == null || el.isJsonNull || !el.isJsonPrimitive) return
            val token = el.asString
            if (token.isBlank() || !token.startsWith("vk1.a.")) return
            val uidObj = obj.get("user_id")
            val uid = if (uidObj != null && uidObj.isJsonPrimitive) uidObj.asLong else 0L
            if (uid <= 0L) return
            completed = true
            AppLog.i("VkLiteWebViewAuth", "web_token найден (user_id=$uid) — завершаем вход")
            onTokenReady(token, uid)
        } catch (e: Exception) {
            AppLog.d("VkLiteWebViewAuth", "handleToken parse error: ${e.message}")
        }
    }

    fun readWebTokenJs(): String = """
        (function(){
          if(location.protocol!=='https:' || ['m.vk.com','m.vk.ru','id.vk.com','id.vk.ru'].indexOf(location.hostname)<0) return null;
          var found=[];
          try {
            for(var i=0;i<localStorage.length && i<500;i++) {
              var key=localStorage.key(i);
              if(!key || key.indexOf(':web_token:login:auth')<0) continue;
              var value=JSON.parse(localStorage.getItem(key));
              var items=Array.isArray(value)?value:[value];
              items.forEach(function(x){if(x && typeof x.access_token==='string' && found.length<16)
                found.push(x);});
            }
          } catch(e) {}
          var picked=null;
          for(var j=0;j<found.length;j++){ if(found[j].is_active===true || found[j].is_active===undefined){ picked=found[j]; break; } }
          return picked ? JSON.stringify(picked) : null;
        })();
    """.trimIndent()

    // Polling: раз в 750мс читаем localStorage пока юзер не вошёл. НЕ грузим новых
    // страниц и НЕ перебиваем форму входа — только ждём появления токена.
    LaunchedEffect(Unit) {
        while (!completed) {
            val wv = webViewRef
            if (wv != null) {
                val url = wv.url
                if (url != null && url.startsWith("https://")) {
                    val host = runCatching { Uri.parse(url).host }.getOrNull()
                    if (host == "m.vk.ru" || host == "m.vk.com" || host == "id.vk.ru" || host == "id.vk.com") {
                        try {
                            // evaluateJavascript требует ValueCallback<String>; результат —
                            // JSON-строка объекта в кавычках либо "null". Снимаем кавычки.
                            val holder = arrayOfNulls<String>(1)
                            wv.evaluateJavascript(readWebTokenJs(), object : ValueCallback<String> {
                                override fun onReceiveValue(value: String?) { holder[0] = value }
                            })
                            val res = holder[0]
                            if (!res.isNullOrBlank() && res != "null" && res.length >= 2 &&
                                res.startsWith("\"") && res.endsWith("\"")) {
                                val inner = res.substring(1, res.length - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                handleToken(inner)
                            }
                        } catch (e: Exception) {
                            AppLog.d("VkLiteWebViewAuth", "poll read fail: ${e.message}")
                        }
                    }
                }
            }
            delay(750L)
        }
    }

    AndroidView(
        factory = { ctx ->
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }
            val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4))
            val web = WebView(ctx)
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    // Ограничиваем навигацию только доверенными VK-доменами.
                    val bypassed = url.startsWith("https://m.vk.ru/") || url.startsWith("https://m.vk.com/") ||
                        url.startsWith("https://id.vk.ru/") || url.startsWith("https://id.vk.com/") ||
                        url.startsWith("https://login.vk.ru/") || url.startsWith("https://login.vk.com/") ||
                        url.startsWith("https://oauth.vk.ru/") || url.startsWith("https://oauth.vk.com/") ||
                        url.startsWith("https://vk.ru/") || url.startsWith("https://vk.com/") ||
                        url.startsWith("https://static.vk.ru/") || url.startsWith("https://static.vk.com/")
                    return !bypassed
                }
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request != null && request.isForMainFrame) {
                        val desc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            error.description.toString()
                        } else {
                            "error ${error.errorCode}"
                        }
                        AppLog.e("VkLiteWebViewAuth", "onReceivedError: $desc code=${error.errorCode} url=${request.url}")
                    }
                }
            }
            web.loadUrl(startUrl)
            root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            webViewRef = web
            root
        },
        update = { },
        modifier = modifier,
    )
}