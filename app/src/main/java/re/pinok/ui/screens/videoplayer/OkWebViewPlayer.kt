// File: ui/screens/videoplayer/OkWebViewPlayer.kt
package re.pinok.ui.screens.videoplayer

// Fix #140 (2026-08-03): WindowInsetsControllerCompat для скрытия system bars
// в fullscreen-режиме WebView-плеера (YouTube/EXTERNAL_IFRAME). РАНЬШЕ
// onShowCustomView только переключал Compose state, но НЕ скрывал status bar
// и navigation bar — плеер оставался под системными панелями.
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.webkit.WebViewCompat
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Video
import re.pinok.data.model.VideoPlatform
import re.pinok.media.VideoDownloadManager
import re.pinok.media.VideoPipController
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import java.io.ByteArrayInputStream

/**
 * OK-IMPL-1 (Stage 2) + Fix #142 (2026-08-03): WebView-based видео-плеер для
 * OK.ru / YouTube / Instagram / external iframe.
 *
 * Применяется в [VideoPlatformRouter] для платформ `YOUTUBE`, `INSTAGRAM`,
 * и `EXTERNAL_IFRAME`, а также как fallback для `OK` когда нативный
 * [OkVideoRepository] не смог извлечь метаданные (403 / изменённая подпись
 * URL / TKN требуется).
 *
 * ## Fix #142: Instagram embed:
 *
 * Instagram блокирует generic iframe-встраивание (X-Frame-Options: SAMEORIGIN).
 * Если просто загрузить `instagram.com/reel/<shortcode>/` в WebView — получим
 * пустой экран (Instagram видит чужой referer и отдаёт 200 OK с empty body).
 *
 * Решение: используем публичный `/embed` endpoint:
 *   `https://www.instagram.com/reel/<shortcode>/embed/`
 *
 * Этот endpoint специально для встраивания (используется самим Instagram на
 * вебе для preview-карточек). НЕ блокирует iframe, показывает видео-плеер с
 * стандартными контролами. Поддерживает autoplay через URL-параметр (но
 * Instagram часто игнорирует autoplay из-за политики user-gesture).
 *
 * Shortcode извлекается в [Video.extractExternalId] через INSTAGRAM_ID_REGEX.
 *
 * ## Ad-blocking — все 7 методов из OK_PLAYER_REVERSE.md §"WAYS TO DISABLE ADS":
 *
 * 1. **Network-перехват** ([WebViewClient.shouldInterceptRequest]): блокируем
 *    загрузку Adman SDK, трекеров Yandex/Mail.ru/TNS, gtmpx ad-tag injector.
 *    Возвращаем пустой `text/plain` response → AdmanHTML не загружается →
 *    `onAdmanLoadingError()` → main video plays без рекламы.
 *
 * 2. **JS stub** ([WebViewClient.onPageFinished]): переопределяем `window.AdmanHTML`
 *    классом-заглушкой. AdmanHTML init'ится успешно, но `start("preroll")`
 *    немедленно вызывает `onCompleted` → `switchToVideo()` → main video plays.
 *    Самый чистый JS-side fix.
 *
 * 3. **advForce flag**: `localStorage["@vpl-flags"] = {advForce:false}` —
 *    belt-and-suspenders (advForce单向 force-ON, но false безвредно).
 *
 * 4. **flashvars override**: best-effort `Object.defineProperty` чтобы выставить
 *    `flashvars.isAdvertismentsSwitchOffForced = "1"` (самый сильный server-side
 *    флаг отключения рекламы). Обёрнуто в try/catch — flashvars может быть
 *    readonly или вообще отсутствовать на момент инъекции.
 *
 * 5. **_vp_lastDayAdvShown cap** (FEED-FIX-3 #348): `localStorage._vp_lastDayAdvShown = 999`
 *    — OK player хранит счётчик показов рекламы за день. Высокое значение →
 *    player считает что дневной лимит достигнут → пропускает рекламу.
 *
 * 6. **Quality force** (FEED-FIX-3 #348): `localStorage._vp_lastVideoQualityName = "hd"`
 *    — форсирует HD-качество. Значения: mobile/lowest/low/sd/hd/full/quad/ultra.
 *
 * 7. **Privacy: device ID wipe** (FEED-FIX-3 #348): `localStorage.deviceId = ""` и
 *    `localStorage["tracer-device-id"] = ""` — обнуляет OK tracking IDs.
 *    Telemetry по-прежнему работает (на уровне network), но device-bound
 *    профилирование затрудняется.
 *
 * ## Ограничения:
 *  - Управление воспроизведением (play/pause/seek/quality) — внутри WebView.
 *    Compose-оверлеи НЕ управляют плеером, только навигация + индикатор «Без рекламы».
 *  - YouTube: реклама YouTube (AdSense) этим методом НЕ блокируется полностью —
 *    рекомендуется открывать в браузере или использовать Invidious/Piped.
 *
 * См. OK_PLAYER_REVERSE.md §3213-3305 (Ad-SDK analysis), §3308-3312 (cross-platform).
 * См. OK_VIDEO_PLAN.md §Этап 2.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OkWebViewPlayer(
    video: Video,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // ── State ───────────────────────────────────────────────────────────
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(video) { mutableStateOf(true) }
    var loadError by remember(video) { mutableStateOf<String?>(null) }
    // Retry trigger: WebView пересоздаётся по изменению этого ключа.
    var retryTrigger by remember(video) { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Fix #141 (2026-08-03): состояние скачивания для кнопки «Скачать».
    // downloadStateFlow — Map<key, DownloadState>. Ключ вычисляется по тому же
    // правилу что и в VideoDownloadManager.enqueueUrlDownload: если у video
    // есть VK ownerId/videoId — используем их; иначе synthetic -2e9/hashCode(url).
    // т.к. прямой URL неизвестен до JS-экстракта, используем «worst case» —
    // показываем кнопку как idle, а после первого тапа обновляем ключ.
    val downloadsFlow = VideoDownloadManager.downloads.collectAsState()
    // Если video.id > 0 и ownerId != 0 — это OK-видео, используем штатный ключ.
    // Иначе synthetic (показываем idle пока URL не извлечён).
    val downloadState = remember(video, downloadsFlow.value) {
        if (video.id > 0L && video.ownerId != 0L) {
            val key = VideoDownloadManager.videoKey(video.ownerId, video.id)
            downloadsFlow.value[key]
        } else {
            null
        }
    }
    // Стейт для ошибок скачивания (blob:/data: URL, пустой extrakt и т.д.)
    var downloadError by remember(video) { mutableStateOf<String?>(null) }

    /**
     * Fix #141: JS-инъекция для извлечения прямого URL видео из WebView.
     *
     * Стратегия (по приоритету):
     *  1. `document.querySelector('video').currentSrc` — текущий play URL
     *     (учитывает <source> элементы и adaptive streaming).
     *  2. `video.src` — fallback.
     *  3. Первый `<source>` элемент внутри <video>.
     *
     * Результат приходит в callback как JSON-quoted string (с outer кавычками)
     * или "null" если видео нет. Парсим через [parseJsStringResult].
     */
    fun extractVideoUrlAndDownload() {
        val wv = webViewRef
        if (wv == null) {
            downloadError = "Плеер ещё загружается"
            AppLog.w(TAG, "extractVideoUrlAndDownload: webViewRef is null")
            return
        }
        downloadError = null
        AppLog.i(TAG, "extractVideoUrlAndDownload: injecting JS to get <video>.currentSrc")
        val js = """
            (function(){
                var v = document.querySelector('video');
                if (!v) return '';
                var url = v.currentSrc || v.src || '';
                if (!url) {
                    var sources = v.querySelectorAll('source');
                    for (var i = 0; i < sources.length; i++) {
                        if (sources[i].src) { url = sources[i].src; break; }
                    }
                }
                return url;
            })();
        """.trimIndent()
        wv.evaluateJavascript(js) { result ->
            // result — JSON-quoted string: "\"https://...\"" или "null" или "\"\"".
            val url = parseJsStringResult(result)
            if (url.isEmpty()) {
                downloadError = "Видео не найдено на странице"
                AppLog.w(TAG, "extractVideoUrlAndDownload: JS returned empty url (result=$result)")
                return@evaluateJavascript
            }
            AppLog.i(TAG, "extractVideoUrlAndDownload: extracted url=${url.take(120)}")
            // Ставим в очередь скачивания. enqueueUrlDownload сам проверит
            // blob:/data: и вернёт false если схема не поддерживается.
            val enqueued = VideoDownloadManager.enqueueUrlDownload(video, url)
            if (!enqueued) {
                downloadError = "URL не поддерживается (blob:/data:)"
                AppLog.w(TAG, "extractVideoUrlAndDownload: enqueueUrlDownload rejected url=$url")
            }
        }
    }

    // Fix #141: обработчик тапа по кнопке «Скачать» в WebView-плеере.
    // Если видео уже скачано — тап = удалить. Если идёт загрузка — игнор.
    // Иначе — извлекаем URL из WebView и ставим в очередь.
    fun onDownloadClick() {
        // Если уже скачано — тап = удалить (поведение как в VKVideoDownloadButton).
        if (downloadState != null && downloadState.isCompleted) {
            if (video.id > 0L && video.ownerId != 0L) {
                VideoDownloadManager.removeDownload(video.ownerId, video.id)
                AppLog.i(TAG, "onDownloadClick: removed completed download (owner=${video.ownerId} id=${video.id})")
            }
            return
        }
        // Если идёт загрузка — тап игнорируем (нельзя отменить mid-download
        // без подтверждения; пользователь подождёт или удалит после завершения).
        if (downloadState != null && downloadState.isInProgress) {
            AppLog.d(TAG, "onDownloadClick: download in progress, ignoring tap")
            return
        }
        // Иначе — извлекаем URL из WebView.
        extractVideoUrlAndDownload()
    }

    // ── URL resolution ──────────────────────────────────────────────────
    // OK: строим embed URL из movieId (externalId). YouTube/EXTERNAL_IFRAME:
    // используем video.player напрямую (он уже embed URL).
    // Fix #142: Instagram — строим /embed URL из shortcode (externalId).
    val embedUrl = remember(video, retryTrigger) {
        when (video.videoPlatform) {
            VideoPlatform.OK -> {
                val movieId = video.externalId
                if (movieId != null) {
                    "https://ok.ru/videoembed/$movieId?autoplay=1&__ref=vk.mvk"
                } else {
                    // OK, но externalId не извлечён — fallback на player URL.
                    video.player
                }
            }
            // Fix #142: Instagram embed endpoint.
            // instagram.com/reel/<shortcode>/embed — публичный, не блокирует iframe.
            // Если externalId (shortcode) не извлечён — fallback на player URL
            // (неработающий, но лучше чем пустой экран — пользователь увидит ошибку).
            VideoPlatform.INSTAGRAM -> {
                val shortcode = video.externalId
                if (shortcode != null) {
                    "https://www.instagram.com/reel/$shortcode/embed/"
                } else {
                    video.player
                }
            }
            VideoPlatform.YOUTUBE,
            VideoPlatform.EXTERNAL_IFRAME -> video.player
            else -> video.player
        }
    }

    // VK mobile UA — OK CDN и iframe ожидает VK-related referer/UA.
    val vkUa = remember { VkUserAgent.get(context.applicationContext as android.app.Application) }

    // ── Load URL into WebView ───────────────────────────────────────────
    LaunchedEffect(embedUrl, retryTrigger) {
        if (embedUrl.isNullOrBlank()) {
            loadError = "Нет URL для встраивания видео"
            isLoading = false
            return@LaunchedEffect
        }
        AppLog.i(TAG, "Loading embed URL: $embedUrl (platform=${video.videoPlatform}, movieId=${video.externalId})")
    }

    // ── Back handler: выход из fullscreen优先, потом onBack ─────────────
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    // ── Lifecycle: pause WebView media on background, resume on foreground ─
    LifecycleStartEffect(webViewRef) {
        // NULLSAFE-1: replaced webViewRef?.let { wv -> ... } with explicit null check
        val wvStart = webViewRef
        if (wvStart != null) {
            // onResume / onPause у WebView корректно приостанавливают media.
            wvStart.onResume()
            AppLog.d(TAG, "WebView onResume (foreground)")
        }
        onStopOrDispose {
            val wvStop = webViewRef
            if (wvStop != null) {
                wvStop.onPause()
                AppLog.d(TAG, "WebView onPause (background)")
            }
        }
    }

    // ── Destroy WebView on dispose ──────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef
            if (wv != null) {
                wv.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    // NULLSAFE-1: replaced (parent as? ViewGroup)?.removeView(this) with smart cast
                    val parentGroup = parent as? ViewGroup
                    if (parentGroup != null) parentGroup.removeView(this)
                    destroy()
                }
                AppLog.i(TAG, "WebView destroyed")
            }
            webViewRef = null
        }
    }

    /**
     * Fix #142: JS-инъекция play/pause для WebView-видео (для PiP RemoteAction).
     *
     * Ищет первый <video> элемент и вызывает .play() или .pause() в зависимости
     * от текущего состояния. Безъязыковой JS — работает для любого плеера
     * (OK/YouTube/Instagram/embed) т.к. Web-стандарт HTMLMediaElement единый.
     */
    fun toggleWebViewPlayback() {
        val wv = webViewRef
        if (wv == null) {
            AppLog.d(TAG, "toggleWebViewPlayback: webViewRef is null")
            return
        }
        val js = """
            (function(){
                var v = document.querySelector('video');
                if (!v) return;
                if (v.paused) { v.play(); } else { v.pause(); }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
        AppLog.d(TAG, "toggleWebViewPlayback: JS injected")
    }

    // Fix #142 (2026-08-03): PiP-регистрация для WebView-плеера.
    // VideoPlayerScreen (нативный ExoPlayer) уже имеет PiP через VideoPipController.
    // OkWebViewPlayer — НЕ имел. Теперь имеет: при входе на экран включаем PiP,
    // при выходе — выключаем. togglePlayPause дергает WebView JS (play/pause video).
    //
    // ВАЖНО: PiP для WebView работает только если внутри WebView есть активный
    // <video> элемент в playing состоянии. Система определяет это через
    // MediaSession/WebChromeClient. Без активной media session PiP-окно будет
    // чёрным. Это известное ограничение Android WebView + PiP.
    //
    // Fix #PIP-VIDEO-ONLY (2026-08-04): добавлен JS-polling для отслеживания
    // isPlaying. VideoPipController.enterPipIfEnabled теперь проверяет isPlaying
    // — если видео на паузе/не загружено, auto-PiP при сворачивании НЕ сработает
    // (раньше показывался чёрный PiP). Polling раз в 1 сек читает
    // document.querySelector('video').paused через evaluateJavascript.
    DisposableEffect(video) {
        VideoPipController.setPipEnabled(true)
        VideoPipController.setIsPlaying(false) // сброс до опроса
        VideoPipController.setTogglePlayPause { toggleWebViewPlayback() }

        // JS-polling: каждые 1 сек проверяем playing-состояние <video> элемента.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val pollRunnable = object : Runnable {
            override fun run() {
                val wv = webViewRef
                if (wv != null) {
                    val js = "(function(){var v=document.querySelector('video');" +
                        "if(!v)return 'none';return v.paused?'paused':'playing';})()"
                    wv.evaluateJavascript(js) { result ->
                        // result = "paused" / "playing" / "none" (или null если JS fail)
                        val playing = "playing" == (result?.trim()?.trim('"') ?: "")
                        VideoPipController.setIsPlaying(playing)
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
        handler.postDelayed(pollRunnable, 1000L)

        onDispose {
            handler.removeCallbacks(pollRunnable)
            VideoPipController.setPipEnabled(false)
            VideoPipController.setIsPlaying(false)
            VideoPipController.setTogglePlayPause(null)
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (embedUrl.isNullOrBlank()) {
            // Нет URL — показываем ошибку и кнопку «Открыть в браузере».
            OkWebViewErrorPlaceholder(
                message = loadError ?: "Видео недоступно для встраивания",
                onRetry = { retryTrigger++ },
                onOpenInBrowser = {
                    // NULLSAFE-1: replaced video.player?.let { url -> ... } with explicit null check
                    val url = video.player
                    if (url != null) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "No browser available: ${e.message}")
                        }
                    }
                },
                canOpenInBrowser = !video.player.isNullOrBlank(),
            )
            return@Box
        }

        // embedUrl — это `val embedUrl = remember(...)` (plain val, не delegated),
        // поэтому smart cast работает: после `if (embedUrl.isNullOrBlank()) return@Box`
        // выше, компилятор smart-cast'ит embedUrl к String (non-null) для всего
        // оставшегося scope. Локальный val embed больше не нужен (NULLSAFE-1 cleanup).

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // ── WebView config ────────────────────────────────────
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString = vkUa
                    // Рекомендуем OK/vk видео не масштабировать — у него свой viewport.
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    // #AD-DOCUMENT-START: инжектим AdmanHTML-stub + флаги на document
                    // start — ДО выполнения скриптов страницы. Раньше stub вставлялся
                    // в onPageFinished (слишком поздно): плеер успевал создать AdmanHTML
                    // из заблокированного SDK и зависал в ожидании рекламного колбэка →
                    // чёрный экран ~12с до таймаута. Теперь колбэк onCompleted/onClosed
                    // срабатывает сразу → видео стартует без паузы.
                    WebViewCompat.addDocumentStartJavaScript(this, ADMAN_DOCUMENT_START_JS, setOf("*"))

                    // ── WebViewClient: ad-blocking + error handling ──────
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            // NULLSAFE-1: replaced request?.url?.toString() ?: return null
                            val req = request ?: return null
                            val url = req.url.toString()
                            // Ad-blocking method #1: network-level block.
                            if (isAdOrTrackerUrl(url)) {
                                AppLog.d(TAG, "Blocked ad/tracker: ${url.take(120)}")
                                return emptyResponse()
                            }
                            return null
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            loadError = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            // Ad-blocking methods #2/#3/#4: inject JS stubs.
                            injectAdmanStub(view)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            // Игнорируем ошибки под-ресурсов (картинок, трекеров) —
                            // важна только ошибка главной страницы.
                            // NULLSAFE-1: replaced request?.isForMainFrame == true + error?.description?.toString()
                            if (request != null && request.isForMainFrame) {
                                val err = error
                                val desc = if (err != null) {
                                    val d = err.description
                                    if (d != null) d.toString() else "unknown"
                                } else "unknown"
                                val codeStr = if (err != null) err.errorCode.toString() else "null"
                                AppLog.e(TAG, "WebView main-frame error: $desc ($codeStr)")
                                loadError = "Не удалось загрузить видео ($desc)"
                            }
                        }
                    }

                    // ── WebChromeClient: fullscreen video handling ───────
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            // OK/YouTube плеер запросил fullscreen (например, при
                            // тапе на кнопку fullscreen внутри iframe) — переключаем
                            // Compose-состояние, скрываем оверлеи.
                            isFullscreen = true
                            AppLog.d(TAG, "WebChrome onShowCustomView — fullscreen")
                            // Fix #140: скрываем status bar + navigation bar через
                            // WindowInsetsControllerCompat. РАНЬШЕ не скрывались →
                            // плеер оставался под системными панелями.
                            val act = context as? android.app.Activity
                            if (act != null) {
                                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            }
                        }

                        override fun onHideCustomView() {
                            isFullscreen = false
                            AppLog.d(TAG, "WebChrome onHideCustomView — exit fullscreen")
                            // Fix #140: показываем system bars обратно
                            val act = context as? android.app.Activity
                            if (act != null) {
                                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                    }

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    // Стартуем загрузку. embedUrl уже smart-cast к non-null String
                    // (после isNullOrBlank() + return@Box выше).
                    loadUrl(embedUrl)
                }.also { wv ->
                    webViewRef = wv
                }
            },
            update = { wv ->
                // На каждой recomposition синхронизируем URL только если он
                // изменился И это не about:blank (иначе зациклим перезагрузку).
                // embedUrl уже smart-cast к non-null String (после isNullOrBlank()
                // + return@Box выше) — `!= null` проверка не нужна (compiler warning).
                val current = wv.url
                if (embedUrl.isNotBlank() && current != embedUrl && current != "about:blank") {
                    wv.loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Loading spinner overlay ─────────────────────────────────────
        if (isLoading && loadError == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Загрузка видео…", color = Color.White, fontSize = 13.sp)
            }
        }

        // ── Error overlay (main-frame failed) ───────────────────────────
        if (loadError != null) {
            OkWebViewErrorPlaceholder(
                message = loadError ?: "Не удалось загрузить видео",
                onRetry = { retryTrigger++ },
                onOpenInBrowser = {
                    // NULLSAFE-1: replaced video.player?.let { url -> ... } with explicit null check
                    val url = video.player
                    if (url != null) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "No browser available: ${e.message}")
                        }
                    }
                },
                canOpenInBrowser = !video.player.isNullOrBlank(),
            )
        }

        // ── Top overlays: back button + ad-free badge + download (hidden in fullscreen) ──
        if (!isFullscreen) {
            // Back button (top-left, semi-transparent)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp)
                    .background(Color(0x80000000), RoundedCornerShape(8.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
            }

            // Top-right cluster: download button + ad-free badge.
            // Fix #141: кнопка «Скачать» рядом с бейджем «Без рекламы».
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WebViewDownloadButton(
                    state = downloadState,
                    onError = downloadError,
                    onClick = { onDownloadClick() },
                )
                AdFreeBadge()
            }
        }
    }
}

/**
 * Fix #141 (2026-08-03): Кнопка «Скачать» для OkWebViewPlayer.
 *
 * Состояния (зеркалирует VKVideoDownloadButton из VideoScreen.kt):
 *  - state == null: иконка Download (idle), тап → извлечь URL из WebView.
 *  - state.isInProgress: CircularProgressIndicator с процентом.
 *  - state.isCompleted: иконка DownloadDone (зелёная), тап → удалить.
 *  - state.status == FAILED: иконка Download (красная), тап → retry.
 *
 * Если onError не null — показываем маленький красный бейдж с описанием ошибки
 * (например «blob: не поддерживается»).
 */
@Composable
private fun WebViewDownloadButton(
    state: DownloadState?,
    onError: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(0x80000000), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (state != null && state.isInProgress) {
            CircularProgressIndicator(
                progress = { if (state.progress >= 0) state.progress / 100f else 0f },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        } else if (state != null && state.isCompleted) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Скачано (тап чтобы удалить)",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(22.dp),
            )
        } else if (state != null && state.status == DownloadStatus.FAILED) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Ошибка скачивания (тап чтобы повторить)",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Скачать",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    // Если есть ошибка — логируем (UI не загромождаем, кнопка уже красная).
    if (onError != null && (state == null || state.status == DownloadStatus.FAILED)) {
        AppLog.d(TAG, "Download button error visible: $onError")
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Ad-free badge (Stage 7)
// ══════════════════════════════════════════════════════════════════════

/**
 * OK-IMPL-1 (Stage 7): зелёный бейдж «Без рекламы» в правом верхнем углу
 * WebView-плеера. Показывает пользователю, что ad-blocking применён.
 */
@Composable
private fun AdFreeBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xCC1B5E20), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.VerifiedUser,
            contentDescription = "Без рекламы",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(14.dp),
        )
        Text(
            "Без рекламы",
            color = Color(0xFFA5D6A7),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Error placeholder
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun OkWebViewErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    canOpenInBrowser: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(32.dp)
            .background(Color(0xCC000000), RoundedCornerShape(12.dp))
            .padding(24.dp),
    ) {
        Text(
            message,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, "Повторить", tint = Color.White)
            }
            if (canOpenInBrowser) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2196F3), RoundedCornerShape(8.dp))
                        .clickable { onOpenInBrowser() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Открыть в браузере", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Ad-blocking helpers
// ══════════════════════════════════════════════════════════════════════

/**
 * Список подстрок-маркеров ad/tracker-запросов. URL считаются как contains-any
 * (case-insensitive). Источник: OK_PLAYER_REVERSE.md §3215-3218 (Adman SDK),
 * §3319-3324 (Yandex/Mail.ru/TNS stat pixels).
 *
 * Включает:
 *  - Adman SDK (Mail.ru/VK Ads) — главный рекламный движок OK player.
 *  - gtmpx — ad-tag injector (внешний ad-server Mail.ru).
 *  - Mail.ru top counter (top-fwz1) — трекер показов.
 *  - Yandex Metrika — трекер (mc.yandex.ru/metrika/).
 *  - TNS counter — трекер (tns-counter.ru).
 *  - ad.mail.ru — общая доменная的广告 выдача.
 */
private val AD_TRACKER_PATTERNS: List<String> = listOf(
    "ad.mail.ru/static/admanhtml/",
    "ad.mail.ru/static/admanhtml",
    "//ad.mail.ru/",
    "admanhtml/rbadman-html5.min.js",
    "gtmpx.com/",
    "top-fwz1.mail.ru/",
    "mc.yandex.ru/metrika/",
    "tns-counter.ru/",
)

/**
 * true если URL соответствует ad/tracker-запросу и должен быть заблокирован.
 * Чувствителен к регистру (URL'ы обычно lowercase в path-части, но хост может быть mixed).
 */
private fun isAdOrTrackerUrl(url: String): Boolean {
    val lower = url.lowercase()
    return AD_TRACKER_PATTERNS.any { pat -> lower.contains(pat.lowercase()) }
}

/**
 * Пустой HTTP-ответ для подмены заблокированных ad/tracker-запросов.
 * 200 OK + пустое тело + text/plain → AdmanHTML не загружается.
 */
private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/**
 * #AD-DOCUMENT-START: JS, инжектируемый через [WebViewCompat.addDocumentStartJavaScript]
 * ДО выполнения скриптов страницы. Устраняет гонку: раньше stub появлялся в
 * onPageFinished, а плеер уже успевал создать AdmanHTML из заблокированного SDK →
 * зависал в ожидании рекламного колбэка (чёрный экран ~12с).
 *
 * Stub `start()`/`init()`/`skip()` сразу триггерят `onCompleted`+`onClosed` →
 * плеер переходит к видео без паузы. Конструктор принимает callbacks как объект
 * (на случай `new AdmanHTML({onCompleted:...})`) и как присвоение после создания.
 */
private const val ADMAN_DOCUMENT_START_JS = """
(function() {
    try {
        window.AdmanHTML = function(opts) {
            var self = this;
            var o = (opts && typeof opts === 'object') ? opts : {};
            this.onReady = o.onReady || function() {};
            this.onStarted = o.onStarted || function() {};
            this.onPlayed = o.onPlayed || function() {};
            this.onPaused = o.onPaused || function() {};
            this.onCompleted = o.onCompleted || function() {};
            this.onClosed = o.onClosed || function() {};
            this.onSkipped = o.onSkipped || function() {};
            this.onClicked = o.onClicked || function() {};
            this.onTimeRemained = o.onTimeRemained || function() {};
            this.onError = o.onError || function() {};
            this.setDebug = function() {};
            this.adMidrollPoint = function(cb) { if (cb) cb(); };
            this.init = function() { self._done(); };
            this.start = function() { self._done(); };
            this.pause = function() {};
            this.resume = function() {};
            this.skip = function() { self._done(); };
            this.setVolume = function() {};
            this.setFullscreen = function() {};
            this.setPosition = function() {};
            this._done = function() {
                try { self.onCompleted(); } catch (e) {}
                try { self.onClosed(); } catch (e) {}
            };
        };
    } catch (e) { /* ignore */ }
    try {
        var fv = window.flashvars;
        if (!fv) { fv = {}; window.flashvars = fv; }
        fv.isAdvertismentsSwitchOffForced = '1';
        fv.showAd = '0';
        fv.showRec = '0';
    } catch (e) { /* ignore */ }
    try {
        localStorage.setItem('@vpl-flags', JSON.stringify({advForce: false}));
        localStorage.setItem('_vp_lastDayAdvShown', '999');
        localStorage.setItem('_vp_lastDayVideoShown', '999');
        localStorage.setItem('_vp_lastVideoQualityName', 'hd');
        localStorage.setItem('deviceId', '');
        localStorage.setItem('tracer-device-id', '');
    } catch (e) { /* ignore */ }
})();
"""

/**
 * Ad-blocking methods #2/#3/#4 + FEED-FIX-3 (#348) #5/#6/#7: JS-инъекция после загрузки страницы.
 *
 * Method #2: переопределяем `window.AdmanHTML` классом-заглушкой.
 *  Все методы — no-op, кроме `start()`/`skip()` которые вызывают `onCompleted`.
 *  AdmanHTML init'ится успешно, но `startPreroll()` немедленно триггерит
 *  `switchToVideo()` → main video plays.
 *
 * Method #3: `localStorage["@vpl-flags"] = {advForce:false}` — флаг force-ON
 *  рекламы (односторонний), устанавливаем false на всякий случай.
 *
 * Method #4: best-effort `Object.defineProperty` для `flashvars.isAdvertismentsSwitchOffForced = "1"`
 *  — самый сильный server-side флаг отключения. Обёрнуто в try/catch (flashvars
 *  может быть readonly или ещё не существовать на момент инъекции).
 *
 * Method #5 (FEED-FIX-3 #348): `localStorage._vp_lastDayAdvShown = 999` —
 *  OK player хранит счётчик показов рекламы за день в localStorage.
 *  Высокое значение → player считает что дневной лимит достигнут →
 *  пропускает рекламу. Источник: localStorage dump от пользователя.
 *
 * Method #6 (FEED-FIX-3 #348): `localStorage._vp_lastVideoQualityName = "hd"` —
 *  форсирует HD-качество (вместо "sd" default). Значения:
 *  mobile/lowest/low/sd/hd/full/quad/ultra (см. quality map Ds в
 *  OK_PLAYER_REVERSE.md §3309).
 *
 * Method #7 (FEED-FIX-3 #348): `localStorage.deviceId = ""` + `tracer-device-id = ""`
 *  — обнуляет OK tracking IDs. Telemetry по-прежнему работает на network
 *  уровне, но device-bound профилирование затрудняется.
 *
 * Источник: OK_PLAYER_REVERSE.md §3294-3305 (WAYS TO DISABLE ADS),
 * §localStorage state dump (FEED-FIX-3 #348).
 */
private fun injectAdmanStub(view: WebView?) {
    if (view == null) return
    val js = """
        (function() {
            try {
                // Method #2: AdmanHTML stub class.
                window.AdmanHTML = class {
                    setDebug() {}
                    onReady() {}
                    onStarted() {}
                    onPlayed() {}
                    onPaused() {}
                    adMidrollPoint(cb) { if (cb) cb(); }
                    onClosed() {}
                    onSkipped() {}
                    onClicked() {}
                    onTimeRemained() {}
                    onCompleted() { if (this.onCompleted) this.onCompleted(); }
                    onError() {}
                    init() {}
                    start(section) { if (this.onCompleted) this.onCompleted(); }
                    pause() {}
                    resume() {}
                    skip() { if (this.onCompleted) this.onCompleted(); }
                    setVolume() {}
                    setFullscreen() {}
                    setPosition() {}
                };
            } catch (e) { /* ignore */ }

            try {
                // Method #3: advForce=false flag (one-directional force-ON, but false is harmless).
                localStorage.setItem('@vpl-flags', JSON.stringify({advForce: false}));
            } catch (e) { /* ignore */ }

            try {
                // Method #4: flashvars.isAdvertismentsSwitchOffForced = "1" (strongest disable flag).
                if (typeof flashvars === 'undefined' || flashvars === null) {
                    window.flashvars = {};
                }
                try {
                    flashvars.isAdvertismentsSwitchOffForced = '1';
                } catch (e) {
                    Object.defineProperty(flashvars, 'isAdvertismentsSwitchOffForced', {
                        value: '1', writable: true, configurable: true, enumerable: true
                    });
                }
                flashvars.showAd = '0';
                flashvars.showRec = '0';
            } catch (e) { /* ignore */ }

            // FEED-FIX-3 (#348): localStorage state hacks (from user dump).
            try {
                // Method #5: _vp_lastDayAdvShown cap — fake daily ad limit reached.
                localStorage.setItem('_vp_lastDayAdvShown', '999');
                localStorage.setItem('_vp_lastDayVideoShown', '999');
            } catch (e) { /* ignore */ }

            try {
                // Method #6: force HD quality (instead of "sd" default).
                localStorage.setItem('_vp_lastVideoQualityName', 'hd');
            } catch (e) { /* ignore */ }

            try {
                // Method #7: privacy — wipe OK tracking device IDs.
                localStorage.setItem('deviceId', '');
                localStorage.setItem('tracer-device-id', '');
            } catch (e) { /* ignore */ }
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
    AppLog.d(TAG, "Injected AdmanHTML stub + advForce=false + flashvars override + _vp_lastDayAdvShown=999 + quality=hd + deviceId wipe")
}

/**
 * Fix #141 (2026-08-03): Парсит результат evaluateJavascript для строкового значения.
 *
 * WebView.evaluateJavascript(js) { result -> ... } возвращает:
 *  - "\"https://example.com/video.mp4\"" — если JS вернул строку (JSON-quoted).
 *  - "null" — если JS вернул null/undefined.
 *  - "\"\"" — если JS вернул пустую строку.
 *
 * Эта функция распаковывает JSON-quoting и возвращает «сырую» строку:
 *  - "null" → ""
 *  - "\"https://...\"" → "https://..."
 *  - "\"\"" → ""
 *  - null (если callback дал null) → ""
 *
 * Обработка escape-последовательностей (\n, \", \\, \uXXXX) — через минимальный
 * JSON-парсер (org.json не подходит, т.к. строка может содержать некорректный JSON
 * при некоторых краевых случаях в WebView). Используем ручной unescape.
 */
private fun parseJsStringResult(raw: String?): String {
    if (raw == null) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "null") return ""
    // Если строка не обёрнута в кавычки — возвращаем как есть (число, boolean).
    if (trimmed.length < 2) return trimmed
    if (trimmed[0] != '"' || trimmed[trimmed.length - 1] != '"') return trimmed
    // Снимаем outer quotes и unescape.
    val inner = trimmed.substring(1, trimmed.length - 1)
    val sb = StringBuilder(inner.length)
    var i = 0
    while (i < inner.length) {
        val c = inner[i]
        if (c == '\\' && i + 1 < inner.length) {
            val next = inner[i + 1]
            when (next) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'u' -> {
                    if (i + 5 < inner.length) {
                        val hex = inner.substring(i + 2, i + 6)
                        val codePoint = hex.toIntOrNull(16)
                        if (codePoint != null) {
                            sb.appendCodePoint(codePoint)
                            i += 6
                            continue
                        }
                    }
                    sb.append(next)
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private const val TAG = "OkWebViewPlayer"
