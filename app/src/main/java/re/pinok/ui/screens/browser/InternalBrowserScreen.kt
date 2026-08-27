package re.pinok.ui.screens.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import re.pinok.auth.OAuthWebViewActivity
import re.pinok.util.AppLog

/**
 * P5.1: Встроенный браузер (WebView) для открытия ссылок из чата.
 *
 * Альтернатива внешнему браузеру (ACTION_VIEW). Настройка
 * [re.pinok.data.local.SovaPrefs.Snapshot.openLinksInInternalBrowser]
 * определяет, какой способ используется при клике по URL в ChatDetailScreen.
 *
 * Настройки WebView:
 *  - JavaScript enabled (многие сайты без JS не работают).
 *  - DOM storage enabled (Web-приложения: VK, React/Vue apps).
 *  - Chrome UA (см. [OAuthWebViewActivity.CHROME_UA]) — VK ID и большинство
 *    сайтов отдают мобильную web-версию только при браузерном UA;
 *    VKAndroidApp UA ломает id.vk.com (см. комментарий в OAuthWebViewScreen).
 *  - shouldOverrideUrlLoading: http/https грузятся внутри; прочие схемы
 *    (mailto:, tel:, intent:, market:) — наружу через ACTION_VIEW.
 *
 * Back-навигация: сначала WebView.goBack() (история внутри страницы),
 * затем onBack() (выход из экрана). Реализовано через [BackHandler].
 *
 * Lifecycle: WebView уничтожается в onRelease (audit High #3 — иначе утечка
 * потоков/контекста, та же правка что в OAuthWebViewScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("SetJavaScriptEnabled")
fun InternalBrowserScreen(
    url: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf(url) }
    var currentUrl by remember { mutableStateOf(url) }
    var showMenu by remember { mutableStateOf(false) }
    // WebView reference для reload / canGoBack / goBack из TopAppBar buttons.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Сначала WebView.goBack() (история внутри страницы), потом onBack().
    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title.ifBlank { currentUrl },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = {
                    webViewRef?.reload()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Открыть во внешнем браузере") },
                        onClick = {
                            showMenu = false
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                AppLog.e(TAG, "external browser failed: ${e.message}")
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться ссылкой") },
                        onClick = {
                            showMenu = false
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "Поделиться").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (e: Exception) {
                                AppLog.e(TAG, "share failed: ${e.message}")
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Копировать ссылку") },
                        onClick = {
                            showMenu = false
                            try {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", currentUrl))
                            } catch (e: Exception) {
                                AppLog.e(TAG, "copy failed: ${e.message}")
                            }
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (isLoading) {
            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Браузерный UA (НЕ VKAndroidApp) — см. комментарий в OAuthWebViewScreen:
                        // VK ID и большинство сайтов отдают корректную web-версию только при Chrome UA.
                        settings.userAgentString = OAuthWebViewActivity.CHROME_UA
                        // Поддержка viewport + zoom под мобильный экран.
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // Fix #184: явная UTF-8 кодировка (см. комментарий в AuthActivity).
                        @Suppress("DEPRECATION")
                        settings.defaultTextEncodingName = "UTF-8"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val reqUrl = request.url
                                val scheme = reqUrl.scheme?.lowercase()
                                // http/https грузим внутри WebView.
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }
                                // Прочие схемы (mailto:, tel:, intent:, market:, tg:…) — наружу.
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, reqUrl)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "no handler for $reqUrl: ${e.message}")
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView, urlParam: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, urlParam, favicon)
                                isLoading = true
                                if (urlParam != null) currentUrl = urlParam
                            }

                            override fun onPageFinished(view: WebView, urlParam: String?) {
                                super.onPageFinished(view, urlParam)
                                isLoading = false
                                progress = 100
                                title = view.title ?: urlParam ?: currentUrl
                                if (urlParam != null) currentUrl = urlParam
                            }
                        }

                        setWebChromeClient(object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) isLoading = false
                            }
                        })

                        loadUrl(url)
                        webViewRef = this
                    }
                },
                // audit High #3: WebView утекает без destroy() в onRelease.
                onRelease = { web ->
                    web.apply {
                        stopLoading()
                        removeJavascriptInterface("AccessibilityBridge")
                        removeJavascriptInterface("clipboard")
                        destroy()
                    }
                    webViewRef = null
                    AppLog.d(TAG, "WebView released")
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Loading overlay — показывается только на самом старте (progress < 20),
            // потом достаточно тонкого LinearProgressIndicator сверху.
            if (isLoading && progress < 20) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val TAG = "InternalBrowser"
