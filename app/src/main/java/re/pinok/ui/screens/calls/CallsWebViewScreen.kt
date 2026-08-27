package re.pinok.ui.screens.calls

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import re.pinok.SovaApp
import re.pinok.auth.exchange.RemixsidCapturer
import re.pinok.util.AppLog

private const val TAG = "CallsWebView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CallsWebViewScreen(
    onBack: () -> Unit,
    url: String = "https://vk.ru/calls",
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        try {
            val app = SovaApp.get(context)
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            if (webView != null) { try { cm.setAcceptThirdPartyCookies(webView, true) } catch (_: Exception) {} }
            val domains = listOf(".vk.ru", "vk.ru", ".calls.vk.ru", "calls.vk.ru",
                ".m.vk.ru", "m.vk.ru", ".login.vk.com", "login.vk.com",
                ".id.vk.com", "id.vk.com", ".vk.com", "vk.com")
            val cookies = mutableMapOf<String, String>()
            // 1) session cookies через RemixsidCapturer (из CookieManager)
            val header = RemixsidCapturer.buildVkCookieHeader()
            if (header.isNotBlank()) {
                for (pair in header.split(";")) {
                    val parts = pair.trim().split("=", limit = 2)
                    if (parts.size == 2) cookies[parts[0].trim()] = parts[1].trim()
                }
            }
            // 2) remixsid из storage (если нет в CookieManager)
            if (!cookies.containsKey("remixsid")) {
                val rid = app.exchangeAuthRepository?.remixsid()
                if (!rid.isNullOrBlank()) cookies["remixsid"] = rid
            }
            // 3) статические куки
            if (!cookies.containsKey("remixlang")) cookies["remixlang"] = "3"
            if (!cookies.containsKey("remixmdevice")) cookies["remixmdevice"] = "2560/1440/1/!!-!!!!!!!!/1249"
            if (!cookies.containsKey("remixff")) cookies["remixff"] = "10111111111111"
            cookies["remixdark_color_scheme"] = "1"
            cookies["remixcolor_scheme_mode"] = "auto"
            AppLog.i(TAG, "syncing ${cookies.size} cookies (remixsid=${cookies.containsKey("remixsid")} remixnsid=${cookies.containsKey("remixnsid")})")
            for ((name, value) in cookies) {
                for (domain in domains) {
                    cm.setCookie(domain, "$name=$value; domain=$domain; path=/")
                }
            }
            cm.flush()
            AppLog.i(TAG, "cookies synced to ${domains.size} domains")
        } catch (e: Exception) {
            AppLog.e(TAG, "cookie sync error: ${e.message}")
        }
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                                AppLog.i(TAG, "page loaded: $url")
                                if (url.contains("login") || url.contains("auth")) {
                                    AppLog.w(TAG, "redirected to login — cookies may be invalid")
                                }
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                AppLog.d(TAG, "nav: ${request.url}")
                                return false
                            }
                            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                                AppLog.e(TAG, "error $errorCode: $description url=$failingUrl")
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (isLoading && progress == 0) {
                Text(text = "Загрузка…", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}