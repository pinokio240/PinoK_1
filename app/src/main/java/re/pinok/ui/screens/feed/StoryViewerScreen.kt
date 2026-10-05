package re.pinok.ui.screens.feed

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
// Fix #140 (2026-08-03): navigationBarsPadding — нижние оверлеи не перекрываются
// navigation bar в edge-to-edge.
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.StickerItem
import re.pinok.data.model.StickerPack
import re.pinok.data.model.Story
import re.pinok.data.model.StoryGroup
import re.pinok.SovaApp
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import re.pinok.media.VideoPlayerConfig
import re.pinok.media.StoryVideoDownloadManager
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlin.math.min

/**
 * Полноэкранный просмотр Stories — аналог vkitStoriesGallery.
 *
 * Структура по vkcom-kit CSS:
 * - StoriesProgressBar (полоски прогресса сверху)
 * - StoriesViewerHeader (аватар + имя + закрыть)
 * - StoryContainer (фото/видео + текст)
 * - Тап левая/правая половина → предыдущая/следующая история
 */
@Composable
fun StoryViewerScreen(
    onBack: () -> Unit,
    onOpenUrlInternal: (String) -> Unit = {},
    // B3 (tap-zones): клик по стикерам сторис.
    onOpenAuthorClips: (Long) -> Unit = {},    // ownerId клип-стикера/сторис
    onOpenPost: (Long, Long) -> Unit = { _, _ -> },     // ownerId, postId
    onOpenAuthorPhotos: (Long) -> Unit = {},   // ownerId сторис/фото
    // P0.14 (Task 25): overlay-ссылка «Видео автора» из paused-overlay.
    onOpenAuthorVideos: (Long) -> Unit = {},
    // P0.15 (Task 25): тап по шапке (аватар + имя) → стена автора.
    //   ownerId > 0 → UserProfile; ownerId < 0 → Community (abs(ownerId)).
    onOpenAuthorProfile: (Long) -> Unit = {},
    // B5 (reply-author): открыть диалог (DM) с автором истории. peerId = ownerId
    // (группы уже задаются отрицательным owner_id — это же значение peer_id для
    // messages API; у VK web ответ на сторис — это именно DM автору, а не публичный
    // комментарий, и отдельного stories.reply-метода в проекте нет).
    onReplyToAuthor: (Long, String, String?) -> Unit = { _, _, _ -> },  // peerId, title, photo
) {
    val groups = StoryHolder.groups
    val startIndex = StoryHolder.startGroupIndex

    if (groups.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var groupIndex by remember { mutableIntStateOf(startIndex) }
    var storyIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    // B5 (reply-author): состояние поля ввода ответа автору сторис.
    var replyText by remember { mutableStateOf("") }
    var replyFocused by remember { mutableStateOf(false) }
    // «Идёт набор» — пока в поле есть текст или оно в фокусе, тап по фону НЕ
    // должен переключать историю (web-parity: focus в поле ввода).
    val isComposing = replyText.isNotBlank() || replyFocused
    val composingForTap by rememberUpdatedState(isComposing)
    val keyboardController = LocalSoftwareKeyboardController.current

    // P0.14 (Task 25): ручная пауза — тап по центру экрана без стикера toggles
    // pause. Отдельно от isComposing (набор ответа). Когда isPaused=true,
    // показывается StoryPausedOverlay со ссылками (Профиль/Клипы/Фото/Видео/Пост).
    var isPaused by remember { mutableStateOf(false) }

    // P0.16 (Task 25): состояние стикер-пикера (полная интеграция VK store.getStickerPacks).
    var showStickerPicker by remember { mutableStateOf(false) }
    var stickerPacks by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
    var stickerLoading by remember { mutableStateOf(false) }
    var selectedStickerPack by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    // Таймер 5 секунд на историю.
    val storyDuration = 5000L

    fun cancelTimer() {
        val prev = timerJob
        if (prev != null) {
            prev.cancel()
            timerJob = null
        }
    }

    // P0.13 (Task 25): логика «после завершения анимации → следующая история».
    // Вынесена в отдельную функцию, чтобы переиспользовать между startTimer
    // (с 0) и resumeTimer (с текущего progress). Раньше этот блок дублировался.
    fun advanceToNext() {
        val g = groups.getOrNull(groupIndex)
        if (g == null) { onBack(); return }
        if (storyIndex < g.stories.size - 1) {
            storyIndex++
            progress = 0f
            startTimer()
        } else if (groupIndex < groups.size - 1) {
            groupIndex++
            storyIndex = 0
            progress = 0f
            startTimer()
        } else {
            onBack()
        }
    }

    fun startTimer() {
        cancelTimer()
        // Fix #49-1: для видео-историй НЕ запускаем tween-таймер — длительность
        // определяет сам ExoPlayer ( Listener.onPlaybackStateChanged(STATE_ENDED) → goToNext ).
        val g0 = groups.getOrNull(groupIndex) ?: return
        val s0 = g0.stories.getOrNull(storyIndex) ?: return
        val v0 = s0.video
        if (v0 != null && (!v0.files.isNullOrEmpty() || !v0.player.isNullOrBlank())) {
            return
        }
        timerJob = scope.launch {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(storyDuration.toInt(), easing = LinearEasing),
            ) {
                progress = value
            }
            // Таймер истёк → следующая история.
            advanceToNext()
        }
    }

    // P0.13 (Task 25): возобновление таймера с текущего progress (НЕ с 0).
    // Вызывается когда isComposing/isPaused стали false — анимация продолжается
    // с того же progress, на котором остановилась. Время оставшейся анимации
    // пропорционально (1 - progress) * storyDuration.
    fun resumeTimer() {
        cancelTimer()
        // Для видео-историй таймер не нужен — паузу/возобновление обрабатывает
        // ExoPlayer (см. LaunchedEffect(isComposing, isPaused) ниже).
        val g0 = groups.getOrNull(groupIndex) ?: return
        val s0 = g0.stories.getOrNull(storyIndex) ?: return
        val v0 = s0.video
        if (v0 != null && (!v0.files.isNullOrEmpty() || !v0.player.isNullOrBlank())) {
            return
        }
        // Если прогресс уже 1f (история закончилась, но pause не сбросил progress),
        // просто переходим к следующей.
        if (progress >= 1f) {
            advanceToNext()
            return
        }
        timerJob = scope.launch {
            val startProgress = progress
            val anim = Animatable(startProgress)
            val remainingMs = ((1f - startProgress) * storyDuration)
                .toInt().coerceAtLeast(1)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(remainingMs, easing = LinearEasing),
            ) {
                progress = value
            }
            advanceToNext()
        }
    }

    fun goToNext() {
        val g = groups.getOrNull(groupIndex)
        if (g == null) { onBack(); return }
        if (storyIndex < g.stories.size - 1) {
            storyIndex++
            progress = 0f
            startTimer()
        } else if (groupIndex < groups.size - 1) {
            groupIndex++
            storyIndex = 0
            progress = 0f
            startTimer()
        }
    }

    fun goToPrev() {
        if (storyIndex > 0) {
            storyIndex--
            progress = 0f
            startTimer()
        } else if (groupIndex > 0) {
            groupIndex--
            val prevGroup = groups[groupIndex]
            storyIndex = (prevGroup.stories.size - 1).coerceAtLeast(0)
            progress = 0f
            startTimer()
        }
    }

    // Запуск таймера при смене истории.
    // P0.14 (Task 25): при смене истории сбрасываем ручную паузу — каждая новая
    // история должна играться с начала (без унаследованного pause от предыдущей).
    LaunchedEffect(groupIndex, storyIndex) {
        progress = 0f
        isPaused = false
        startTimer()
    }

    // Пауза при выходе.
    DisposableEffect(Unit) {
        onDispose { cancelTimer() }
    }

    // Отметить просмотренной через API.
    // Fix #49-2: VK API rejects access_key="story" (литерал, который VK возвращает
    // как access_key для каждой story) с кодом 3 (Unknown error). Реальный VK web
    // НЕ передаёт access_key в stories.view — только owner_id + story_id.
    LaunchedEffect(groupIndex, storyIndex) {
        val g = groups.getOrNull(groupIndex)
        if (g == null) return@LaunchedEffect
        val story = g.stories.getOrNull(storyIndex)
        if (story == null) return@LaunchedEffect
        if (story.isSeenBool) return@LaunchedEffect
        // Fix #100 Risk #7: skip stories.view если story playing from local cache.
        // Story уже была просмотрена ранее (иначе не попала бы в кэш) — повторный
        // API-вызов бесполезен и может 404'нуть если VK уже удалил story (24h TTL).
        if (StoryVideoDownloadManager.isDownloaded(story.ownerId, story.id)) {
            AppLog.d("StoryViewer", "skip stories.view — story #${story.id} cached")
            return@LaunchedEffect
        }
        try {
            re.pinok.SovaApp.get().apiClient.callPublic(
                "stories.view",
                mapOf(
                    "owner_id" to story.ownerId.toString(),
                    "story_id" to story.id.toString(),
                ),
            )
        } catch (e: Exception) {
            AppLog.w("StoryViewer", "stories.view failed: ${e.message}")
        }
    }

    // Читаем текущие данные внутри composition для реактивности.
    val currentGroup = groups.getOrNull(groupIndex)
    if (currentGroup == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val currentStory = currentGroup.stories.getOrNull(storyIndex)
    if (currentStory == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // ── Fix #49-1: видео-истории ───────────────────────────────────────
    // Определяем, доступен ли video URL. VK stories возвращают либо
    // files[mp4_*] (карта mp4 URL'ов разного качества), либо player URL.
    val storyVideo = currentStory.video
    // Fix #100: приоритет — локальный кэш (file://), потом CDN URL.
    // Snapshot ONCE per story (derivedStateOf) — НЕ подписываемся на live
    // download state, иначе remember(videoUrl) пересоздаст ExoPlayer при
    // завершении загрузки mid-playback → чёрный кадр (Risk #2).
    val videoUrl: String? = if (storyVideo != null) {
        val localFile = StoryVideoDownloadManager.getLocalFile(currentStory.ownerId, currentStory.id)
        if (localFile != null && localFile.exists()) {
            AppLog.d("StoryViewer", "story #${currentStory.id}: playing from cache ${localFile.name}")
            "file://${localFile.absolutePath}"
        } else {
            val f = storyVideo.files
            f?.get("mp4_720") ?: f?.get("mp4_480") ?: f?.get("mp4_360")
                ?: f?.get("mp4_240") ?: f?.get("mp4_144") ?: f?.get("hls")
                ?: storyVideo.player?.takeIf { it.isNotBlank() }
        }
    } else null
    val isVideoStory = videoUrl != null

    val context = LocalContext.current
    // Fix #100: читаем настройку autoCacheStories (default false, #AUTOCACHE-STORIES-OFF) — gate auto-cache.
    val app = re.pinok.SovaApp.get()
    val prefsSnap by app.prefs.data.collectAsState(initial = null)
    val autoCacheStories = prefsSnap?.autoCacheStories ?: false
    // Ссылка в сторис: настройка openLinksInInternalBrowser (как ChatDetailScreen:987).
    val openLinksInternal = prefsSnap?.openLinksInInternalBrowser ?: false
    val onUrlClick: (String) -> Unit = { url ->
        if (openLinksInternal) {
            onOpenUrlInternal(url)
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLog.e("StoryViewer", "open url failed: ${e.message}")
            }
        }
    }
    // Fix #109: state для ручной кнопки скачивания истории (mirror VideoDownloadManager.downloads).
    val downloads by StoryVideoDownloadManager.downloads.collectAsState()
    val exoPlayer = remember(videoUrl) {
        if (videoUrl == null) return@remember null
        try {
            val vkUa = VkUserAgent.get(context.applicationContext as android.app.Application)
            // P0.10 (Task 20): DefaultHttpDataSource НЕ отправляет cookies автоматически —
            // VK CDN для stories-видео (sun9-XX.userapi.com) требует remixsid/remixstid/
            // remixstlid (антифрод-куки), без них mp4-URL возвращает HTTP 400. Перешли на
            // OkHttpDataSource.Factory(SovaApp.httpClient) — OkHttpClient содержит
            // VkCookieJar, подставляющий живой VK cookie-set в исходящие запросы.
            // Образец: PlayerService.kt:371-400 (audio), VideoPlayerScreen.kt:802 (video).
            // Fallback на DefaultHttpDataSource если SovaApp ещё не инициализирован.
            // Referer https://m.vk.com/ — VK CDN игнорирует, но не вредит (OK CDN требует).
            val refererProps = mapOf("Referer" to "https://m.vk.com/")
            val appCtx = context.applicationContext as? SovaApp
            val httpFactory = if (appCtx != null) {
                try {
                    OkHttpDataSource.Factory(appCtx.httpClient)
                        .setUserAgent(vkUa)
                        .setDefaultRequestProperties(refererProps)
                } catch (e: Exception) {
                    AppLog.w("StoryViewer", "OkHttpDataSource setup failed, fallback to DefaultHttpDataSource: ${e.message}")
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(vkUa)
                        .setDefaultRequestProperties(refererProps)
                }
            } else {
                AppLog.w("StoryViewer", "SovaApp not initialized, fallback to DefaultHttpDataSource (no cookies)")
                DefaultHttpDataSource.Factory()
                    .setUserAgent(vkUa)
                    .setDefaultRequestProperties(refererProps)
            }
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                // #VIDEO-NET (2026-10-02): истории-видео играют часто на
                // мобильном — увеличенный буфер под сеть (реже обрывы).
                .setLoadControl(VideoPlayerConfig.defaultLoadControl())
                .build().apply {
                    val mi = if (videoUrl.contains("m3u8", ignoreCase = true)) {
                        MediaItem.Builder().setUri(videoUrl)
                            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                            .build()
                    } else {
                        MediaItem.fromUri(videoUrl)
                    }
                    setMediaItem(mi)
                    prepare()
                    playWhenReady = true
                    volume = 1f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                AppLog.d("StoryViewer", "video ended → advance to next story")
                                goToNext()
                            }
                            // Fix #100: auto-cache-on-play (mirror PlayerConnection
                            // pattern для audio). На STATE_READY — тихо ставим в очередь
                            // загрузки, если story ещё не в кэше. silent=true = без notif.
                            // Только для CDN URL (file:// уже в кэше).
                            // Gate: autoCacheStories pref (default false) — пользователь
                            // может отключить автокэш в настройках.
                            //
                            // Fix #108: убрана избыточная проверка `videoUrl != null` —
                            // весь блок remember(videoUrl) на строке 255 уже гарантирует
                            // non-null через early return на строке 256. Компилятор
                            // предупреждал «Condition is always 'true'».
                            if (autoCacheStories &&
                                state == Player.STATE_READY &&
                                !videoUrl.startsWith("file://") &&
                                !StoryVideoDownloadManager.isDownloaded(currentStory.ownerId, currentStory.id)
                            ) {
                                AppLog.d("StoryViewer", "auto-cache story #${currentStory.id} (silent)")
                                StoryVideoDownloadManager.enqueueDownload(
                                    story = currentStory,
                                    ownerName = currentGroup.name ?: "",
                                    ownerPhoto100 = currentGroup.photo100,
                                    silent = true,
                                )
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            AppLog.w("StoryViewer", "story video error: ${error.errorCodeName}")
                            // На ошибке видео — переключаемся на следующую историю
                            // (не блокируем пользователя чёрным экраном).
                            goToNext()
                        }
                    })
                }
        } catch (e: Exception) {
            AppLog.w("StoryViewer", "ExoPlayer init failed: ${e.message}")
            null
        }
    }

    // Релиз плеера при смене истории или выходе.
    DisposableEffect(exoPlayer) {
        onDispose {
            try { exoPlayer?.release() } catch (_: Exception) {}
        }
    }

    // P0.13 + P0.17 (Task 25): пауза/возобновление и tween-таймера, и ExoPlayer.
    // Когда isComposing (набор ответа) или isPaused (ручная пауза) — отменяем
    // tween-таймер (progress сохраняется, НЕ сбрасывается в 0) и ставим ExoPlayer
    // на паузу. Когда оба флага false — для фото-историй возобновляем таймер с
    // текущего progress (resumeTimer), для видео-историй — player.play().
    // safe-call `exoPlayer?.` — для фото-историй player == null (no-op).
    LaunchedEffect(isComposing, isPaused, exoPlayer, isVideoStory) {
        if (isComposing || isPaused) {
            cancelTimer()
            exoPlayer?.pause()
        } else {
            if (isVideoStory) {
                exoPlayer?.play()
            } else {
                resumeTimer()
            }
        }
    }

    // Для видео-историй синхронизируем progress-bar с позицией воспроизведения.
    // Капаем на 30 секунд max — на случай, если duration неизвестен или видео зависло.
    LaunchedEffect(exoPlayer, isVideoStory) {
        if (!isVideoStory || exoPlayer == null) return@LaunchedEffect
        val maxDurationMs = 30_000L
        var elapsedMs = 0L
        while (true) {
            val dur = exoPlayer.duration
            if (dur > 0) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                // Длительность ещё неизвестна — считаем по elapsed.
                elapsedMs += 50L
                progress = (elapsedMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
                if (elapsedMs >= maxDurationMs) {
                    // Защита от зависшего видео — переключаемся дальше.
                    goToNext()
                    break
                }
            }
            delay(50)
        }
    }

    // B3 (tap-zones): «клип-сторис» — либо type="clip", либо есть fullview clip-стикер.
    // Используется для тапа в центр без стикеров (переход в клипы автора).
    val isClipStory = currentStory.type == "clip" ||
        currentStory.stickers.any { it.type == "clip" && it.style == "fullview" }

    // P0.16 (Task 25): загрузка стикер-паков (mirror ChatDetailScreen:1183-1216).
    // Купленные (filters=purchased) + каталог (featured — обычно пустой: VK web-токен
    // возвращает err=100, см. storeGetStickerCatalog). Кэш в stickerPacks: повторные
    // открытия пикера — no-op. scope.launch корутинный — UI не блокируется.
    fun loadStickers() {
        if (stickerPacks.isNotEmpty() || stickerLoading) return
        stickerLoading = true
        scope.launch {
            try {
                val purchased = app.apiClient.storeGetStickerPacks()
                val purchasedIds = purchased.map { it.id }.toHashSet()
                val catalog = app.apiClient.storeGetStickerCatalog()
                val unpurchased = catalog.filter { it.id !in purchasedIds }
                stickerPacks = purchased + unpurchased
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("StoryViewer", "loadStickers failed: ${e.message}")
            } finally {
                stickerLoading = false
            }
        }
    }

    // P0.16 (Task 25): отправка стикера через messagesSendSticker (peerId=ownerId).
    // mirror ChatDetailScreen:1218-1270. purchased=false → Toast «не куплен», без
    // отправки. Иначе messagesSendSticker(fallbackImageUrl=displayUrl) — VKApiClient
    // сам перехватит err=100 "not available" и отправит как картинку (Fix #223).
    // Toast «Стикер отправлен» + закрытие пикера после успешной отправки.
    fun sendSticker(stickerId: Int, peerId: Long, packs: List<StickerPack>) {
        var foundPack: StickerPack? = null
        var foundSticker: StickerItem? = null
        for (pack in packs) {
            val s = pack.stickers?.firstOrNull { it.stickerId == stickerId }
            if (s != null) { foundPack = pack; foundSticker = s; break }
        }
        val isPurchased = foundPack?.purchased != false
        val fallbackUrl = foundSticker?.displayUrl
        if (!isPurchased) {
            Toast.makeText(context, "Стикер-пак не куплен", Toast.LENGTH_SHORT).show()
            return
        }
        showStickerPicker = false
        scope.launch {
            try {
                val msgId = app.apiClient.messagesSendSticker(
                    peerId, stickerId, fallbackImageUrl = fallbackUrl,
                )
                if (msgId > 0) {
                    Toast.makeText(context, "Стикер отправлен", Toast.LENGTH_SHORT).show()
                } else {
                    AppLog.w("StoryViewer", "sendSticker failed (msgId=$msgId) for stickerId=$stickerId")
                    Toast.makeText(context, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.w("StoryViewer", "sendSticker exception: ${e.message}")
                Toast.makeText(context, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // P0.12 #STORY-SWIPE (2026-10): paging историй — горизонтальным свайпом,
            // не тапом по левому/правому краю. Тап остаётся только для стикеров и центра
            // (открытие клипов автора / фото / поста / ссылки). Раньше тап в левую/правую
            // треть экрана переключал историю — конфликтовало со стикерами у края и ломала
            // UX (пользователь хотел тапнуть по стикеру, но промахивался → переходил на
            // следующую историю). Теперь: свайп вправо → prev, свайп влево → next.
            .pointerInput(groupIndex, storyIndex) {
                var totalDelta = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDelta = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDelta += dragAmount },
                    onDragEnd = {
                        // Порог: 8% ширины экрана (плотнее — случайные микро-дроги при
                        // тапе не должны переключать; свайп должен быть осознанным).
                        val threshold = size.width * 0.08f
                        when {
                            totalDelta < -threshold -> goToNext()
                            totalDelta > threshold -> goToPrev()
                        }
                    },
                )
            }
            .pointerInput(groupIndex, storyIndex) {
                detectTapGestures(
                    onTap = { offset ->
                        // B5 (reply-author): пока идёт набор ответа (текст или фокус
                        // в поле ввода) тап по фону НЕ переключает историю — только
                        // снимает фокус с поля.
                        if (composingForTap) {
                            // no-op: не переключаем историю, поле само потеряет
                            // фокус при тапе вне него.
                            return@detectTapGestures
                        }
                        val screenWidth = size.width
                        // Константы исходника сторис: VK возвращает clickable_area
                        // в координатах исходника (1080x1920). Маппим точку тапа (px)
                        // из размеров экрана в координаты исходника.
                        val origW = 1080f
                        val origH = 1920f
                        val scaleX = if (screenWidth > 0f) origW / screenWidth else 1f
                        val scaleY = if (size.height > 0f) origH / size.height else 1f
                        val origX = offset.x * scaleX
                        val origY = offset.y * scaleY

                        fun fallbackPaging() {
                            // P0.12: тап больше НЕ переключает историю (только свайп).
                            // Fallback для стикеров с невалидным target — no-op,
                            // чтобы случайный тап по пустому фону не уводил с истории.
                        }

                        fun handleNoStickerTap() {
                            // P0.14 (Task 25): тап в центр без стикера → toggle ручной
                            // паузы. Раньше (P0.12) тап в центр сразу открывал клипы/фото
                            // автора — это слишком агрессивно (промахнулся → ушёл с истории).
                            // Теперь тап в центр = пауза + overlay со ссылками (Профиль/Клипы/
                            // Фото/Видео/Пост). Пользователь сам решает, куда перейти.
                            // Левая/правая треть — no-op (пользователь хотел свайп, но палец
                            // не сдвинулся — считаем это «не жест»).
                            val third = screenWidth / 3f
                            when {
                                offset.x < third -> Unit  // no-op (раньше goToPrev)
                                offset.x > 2f * third -> Unit  // no-op (раньше goToNext)
                                else -> {
                                    // Тап в центр → toggle паузы. LaunchedEffect(isPaused)
                                    // сам отменит/возобновит таймер и ExoPlayer.
                                    isPaused = !isPaused
                                }
                            }
                        }

                        // Ищем первый стикер, чья кликабельная область содержит точку тапа.
                        val tappedSticker = currentStory.stickers.firstOrNull { s ->
                            s.area.size >= 4 && pointInPolygon(origX, origY, s.area)
                        }
                        if (tappedSticker != null) {
                            when (tappedSticker.type) {
                                "clip" -> onOpenAuthorClips(tappedSticker.clipOwnerId ?: currentStory.ownerId)
                                "post" -> {
                                    val pid = tappedSticker.postId ?: 0L
                                    val oid = tappedSticker.postOwnerId ?: 0L
                                    if (pid > 0L && oid != 0L) onOpenPost(oid, pid) else fallbackPaging()
                                }
                                "link" -> {
                                    val url = tappedSticker.linkUrl
                                    if (!url.isNullOrBlank()) onUrlClick(url) else fallbackPaging()
                                }
                                else -> fallbackPaging()
                            }
                        } else {
                            handleNoStickerTap()
                        }
                    },
                )
            },
    ) {
        // --- Фоновое изображение ---
        val photo = currentStory.photo
        var imageUrl: String? = null
        if (photo != null) {
            imageUrl = PhotoSizes.bestStory(photo.sizes)?.url
        }
        if (imageUrl == null) {
            imageUrl = currentStory.thumbUrl
        }

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // --- Fix #49-1: видео-истории (PlayerView поверх AsyncImage) ---
        // PlayerView рендерится ПОВЕРХ фото-превью. Пока видео буферизируется,
        // shutter прозрачный → пользователь видит фото-превью.
        if (isVideoStory && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        // Прозрачный shutter — пока первый кадр не отрисован,
                        // видно фото-превью под PlayerView.
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        player = exoPlayer
                    }
                },
                update = { pv -> pv.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Градиент-оверлей сверху для читаемости хедера.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .align(Alignment.TopCenter),
        )

        // --- Прогресс-бары (vkitStoriesProgressBar) ---
        StoryProgressBars(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            storyCount = currentGroup.stories.size,
            currentIndex = storyIndex,
            progress = progress,
        )

        // --- Header (vkitStoriesViewerHeader) ---
        // P0.15 (Task 25): тап по аватару/имени → стена автора. ownerId>0 →
        // UserProfile; ownerId<0 → Community (abs(ownerId)). Закрывающая кнопка
        // (IconButton) — отдельный child clickable, перехватывает свой тап, поэтому
        // тап по «крестику» НЕ открывает профиль (родительский clickable не срабатывает).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAuthorProfile(currentGroup.ownerId) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrl = currentGroup.photo100
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (currentGroup.name ?: "?").take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentGroup.name ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatStoryDate(currentStory.date),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                )
            }
        }

        // --- Нижний градиент ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                // Fix #140: navigationBarsPadding — чтобы градиент не уходил
                // под navigation bar в edge-to-edge.
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.3f))
                .align(Alignment.BottomCenter),
        )

        // --- Счётчик ---
        Text(
            text = "${groupIndex + 1}/${groups.size}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // Fix #140: navigationBarsPadding — счётчик не под nav bar
                .navigationBarsPadding()
                .padding(16.dp),
        )

        // --- Fix #109: ручная кнопка скачивания видео-истории ---
        // Авто-кэш (Fix #100) работает тихо на STATE_READY, но пользователь
        // должен видеть явную кнопку: скачать вручную / прогресс / «сохранено»
        // (тап = удалить из кэша). Только для видео-историй —
        // StoryVideoDownloadManager работает с video-only (photo stories нет CDN mp4).
        // clickable перехватывает тап у родительского pointerInput(detectTapGestures),
        // поэтому тап по кнопке НЕ переключает на следующую историю.
        //
        // B5 (reply-author): когда показано поле ответа, поднимаем кнопку выше,
        // чтобы она не наезжала на поле ввода (web имеет скачивание + DM-поле).
        val showReplyInput = currentStory.canReply || currentStory.canComment
        if (isVideoStory) {
            val key = StoryVideoDownloadManager.storyKey(currentStory.ownerId, currentStory.id)
            val dlState = downloads[key]
            StoryDownloadButton(
                state = dlState,
                onClick = {
                    if (dlState != null && dlState.status != DownloadStatus.FAILED) {
                        // Уже в очереди / скачивается / скачано → отменить/удалить.
                        StoryVideoDownloadManager.removeDownload(currentStory.ownerId, currentStory.id)
                    } else {
                        // Не скачано или FAILED → поставить в очередь (foreground notif).
                        StoryVideoDownloadManager.enqueueDownload(
                            story = currentStory,
                            ownerName = currentGroup.name ?: "",
                            ownerPhoto100 = currentGroup.photo100,
                            silent = false,
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Fix #140: navigationBarsPadding — кнопка скачать не под nav bar
                    .navigationBarsPadding()
                    .padding(
                        end = 16.dp,
                        // B5: при открытом поле ответа поднимаем кнопку над ним.
                        bottom = if (showReplyInput) 148.dp else 56.dp,
                    ),
            )
        }

        // Ссылка истории (story.link.url). Показываем компактной кликабельной
        // поверхностью внизу. clickable перехватывает тап у родительского
        // pointerInput(detectTapGestures) — переключение истории не срабатывает.
        //
        // B5 (reply-author): если разрешён ответ и стоит ссылка — ссылка
        // остаётся вверху нижнего блока, а поле ответа — ниже (web-parity:
        // ссылка над DM-полем). Если ссылки нет — только поле ответа.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Fix #140: navigationBarsPadding — блок не уходит под nav bar
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val storyUrl = currentStory.link?.url?.takeIf { it.isNotBlank() }
            if (storyUrl != null) {
                val linkText = currentStory.link?.text?.takeIf { it.isNotBlank() }
                    ?: "Перейти по ссылке"
                StoryLinkButton(
                    text = linkText,
                    onClick = { onUrlClick(storyUrl) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (showReplyInput) {
                Spacer(modifier = Modifier.height(4.dp))
                StoryReplyField(
                    text = replyText,
                    onTextChange = { replyText = it },
                    focused = replyFocused,
                    onFocusChange = { replyFocused = it },
                    onSubmit = {
                        keyboardController?.hide()
                        val title = currentGroup.name ?: ""
                        val photo = currentGroup.photo100
                        onReplyToAuthor(currentGroup.ownerId, title, photo)
                    },
                    // P0.16 (Task 25): кнопка стикеров в поле ответа. loadStickers
                    // кэширует packs (повторные клики — no-op), showStickerPicker=true
                    // открывает StickerPickerSheet (ModalBottomSheet) поверх истории.
                    onOpenStickerPicker = {
                        loadStickers()
                        showStickerPicker = true
                    },
                )
            }
        }

        // P0.14 (Task 25): StoryPausedOverlay — показывается когда isPaused && !isComposing.
        // Полупрозрачный круг с иконкой Pause + вертикальная колонка ссылок-чипов
        // (Профиль/Клипы/Фото/Видео/Пост). Тап по любой ссылке открывает соответствующий
        // экран. clickable каждого чипа перехватывает тап у родительского detectTapGestures,
        // поэтому тап по ссылке НЕ toggles паузу обратно.
        if (isPaused && !isComposing) {
            StoryPausedOverlay(
                story = currentStory,
                group = currentGroup,
                onOpenAuthorProfile = onOpenAuthorProfile,
                onOpenAuthorClips = onOpenAuthorClips,
                onOpenAuthorPhotos = onOpenAuthorPhotos,
                onOpenAuthorVideos = onOpenAuthorVideos,
                onOpenPost = onOpenPost,
                onResume = { isPaused = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // P0.16 (Task 25): стикер-пикер поверх истории (ModalBottomSheet).
        // Загружается через VK store.getProducts (purchased + catalog). Тап по
        // купленному стикеру → messagesSendSticker(ownerId, ...) → Toast → закрытие.
        // Locked-стикеры (purchased=false) — Toast «не куплен», без закрытия.
        if (showStickerPicker) {
            StickerPickerSheet(
                packs = stickerPacks,
                loading = stickerLoading,
                selectedPack = selectedStickerPack,
                onSelectPack = { selectedStickerPack = it },
                onStickerClick = { stickerId ->
                    sendSticker(stickerId, currentGroup.ownerId, stickerPacks)
                },
                onDismiss = { showStickerPicker = false },
            )
        }
    }
}

/**
 * Полоски прогресса для Stories — аналог vkitStoriesProgressBar.
 */
@Composable
private fun StoryProgressBars(
    modifier: Modifier = Modifier,
    storyCount: Int,
    currentIndex: Int,
    progress: Float,
) {
    if (storyCount == 0) return

    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until min(storyCount, 10)) {
            val barProgress = when {
                i < currentIndex -> 1f
                i == currentIndex -> progress
                else -> 0f
            }
            val barColor = animateColorAsState(
                targetValue = if (i <= currentIndex) Color.White else Color.White.copy(alpha = 0.3f),
                label = "barColor",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor.value),
                )
            }
        }
    }
}

/**
 * Кнопка ручного скачивания видео-истории (Fix #109).
 *
 * Зеркалирует поведение VKVideoDownloadButton (VideoScreen.kt), но адаптирована
 * под stories UI: полупрозрачный круг, белый icon, circular progress поверх
 * во время загрузки. Размещается в BottomEnd поверх нижнего градиента.
 *
 * Состояния:
 *  - null / FAILED       → иконка Download (тап = enqueue / retry). FAILED — красный tint.
 *  - QUEUED/DOWNLOADING  → circular progress с % в центре (тап = cancel+remove).
 *  - REMOVING            → spinner без действия (короткий переходный статус).
 *  - COMPLETED           → иконка DownloadDone (тап = удалить из кэша).
 *
 * clickable (не pointerInput) — даёт ripple + accessibility (TalkBack).
 * Child clickable потребляет тап раньше родительского detectTapGestures,
 * поэтому переключение истории не срабатывает при нажатии на кнопку.
 */
@Composable
private fun StoryDownloadButton(
    state: DownloadState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgAlpha by animateColorAsState(
        targetValue = if (state?.isInProgress == true) Color.Black.copy(alpha = 0.3f)
                      else Color.Black.copy(alpha = 0.45f),
        label = "storyDlBg",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgAlpha)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state == null || state.status == DownloadStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = if (state?.status == DownloadStatus.FAILED)
                        "Ошибка загрузки — повторить" else "Скачать историю",
                    tint = if (state?.status == DownloadStatus.FAILED)
                        Color(0xFFFF6B6B) else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            state.isInProgress -> {
                // Circular progress с процентом в центре (mirror VKVideoDownloadButton).
                val pct = if (state.progress >= 0) state.progress / 100f else 0f
                CircularProgressIndicator(
                    progress = { pct },
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = if (state.progress > 0) "${state.progress}" else "…",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            state.status == DownloadStatus.REMOVING -> {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
            state.isCompleted -> {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "Сохранено — удалить из кэша",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Компактная кликабельная кнопка-ссылка в сторис.
 *
 * Поверхность с текстом в нижней части экрана. clickable (не pointerInput) —
 * даёт ripple + accessibility и перехватывает тап у родительского
 * detectTapGestures, поэтому нажатие НЕ переключает историю.
 */
@Composable
private fun StoryLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * B5 (reply-author): компактное поле «Сообщение...» для ответа автору истории.
 *
 * В веб-VK ответ на сторис — это отправка личного сообщения (DM) автору, гейтится
 * флагом can_reply, а НЕ публичный комментарий. Отдельного stories.reply-метода в
 * VKApiClient нет, поэтому submit просто открывает диалог (DM) с автором через
 * [onSubmit] (в SovaNavHost это Screen.ChatDetail.buildRoute(peerId=ownerId, ...)).
 *
 * Стиль: Material3 OutlinedTextField, округлые (20.dp), полупрозрачный фон как у
 * StoryLinkButton, белый текст. Компактная высота (~48dp). Кнопка-стрелка Send —
 * справа (trailing icon не используют — отдельный IconButton в Row проще и точнее
 * по позиционированию). IME Send тоже отправляет. Тап по полю/кнопке не всплывает
 * к родительскому detectTapGestures (clickable + фокус-флаг перехватывают историю).
 */
@Composable
private fun StoryReplyField(
    text: String,
    onTextChange: (String) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    // P0.16 (Task 25): кнопка-стикер (EmojiEmotions) внутри поля ответа. Тап →
    // открывает StickerPickerSheet (ModalBottomSheet). leadingIcon Material3 —
    // иконка внутри OutlinedTextField слева от текста.
    onOpenStickerPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) },
            placeholder = {
                Text(
                    text = "Сообщение…",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            maxLines = 1,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            // P0.16 (Task 25): leadingIcon — кнопка стикеров. IconButton перехватывает
            // тап у родительского detectTapGestures (clickable внутри IconButton), поэтому
            // тап по стикер-кнопке НЕ toggles паузу истории и НЕ переключает на следующую.
            leadingIcon = {
                IconButton(onClick = onOpenStickerPicker) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = "Стикеры",
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
            },
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = onSubmit,
            enabled = focused || text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Написать автору",
                tint = Color.White.copy(alpha = if (focused || text.isNotBlank()) 1f else 0.4f),
            )
        }
    }
}

private fun formatStoryDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestamp
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        else -> "${diff / 86400} д назад"
    }
}

/**
 * B3 (tap-zones): point-in-polygon (ray casting) — попадает ли точка (в координатах
 * исходника 1080x1920) в кликабельную область стикера. Работает для выпуклых и
 * вогнутых полигонов любой формы; у VK clickable_area всегда 4 точки (прямоугольник).
 */
private fun pointInPolygon(x: Float, y: Float, area: List<Story.StoryClickableArea>): Boolean {
    var inside = false
    val n = area.size
    var j = n - 1
    for (i in 0 until n) {
        val xi = area[i].x
        val yi = area[i].y
        val xj = area[j].x
        val yj = area[j].y
        if ((yi > y) != (yj > y) &&
            x < (xj - xi) * (y - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// ═══════════════════════════════════════════════════════════════════
// P0.14 (Task 25): StoryPausedOverlay
// ═══════════════════════════════════════════════════════════════════
// Overlay показывается когда история на паузе (тап в центр) И пользователь
// НЕ набирает ответ. В центре — полупрозрачный круг с иконкой Pause, под ним —
// вертикальная колонка чипов-ссылок: Профиль/Клипы/Фото/Видео/Пост автора.
// Пользователь сам выбирает, куда перейти (раньше тап в центр сразу открывал
// клипы/фото — это было слишком агрессивно: промахнулся → ушёл с истории).
//
// Тап по любой ссылке — отдельный child clickable, перехватывает тап у
// родительского detectTapGestures → НЕ toggles паузу обратно. Ссылка открывает
// соответствующий экран; история остаётся на паузе (после возврата пользователь
// продолжит с того же прогресса — LaunchedEffect(isPaused) сам возобновит
// таймер/ExoPlayer когда пауза снимется).
//
// Ссылки показываются ВСЕГДА (пользователь одобрил «всегда показывать ссылки»):
// пусть пользователь сам решает, куда перейти. Раньше скрывали «Клипы» для
// не-clip-стикер-историй, но это путало — ссылка есть только у clip-историй.
// Теперь: Профиль — всегда; Клипы — всегда; Фото — всегда; Видео — всегда;
// Пост — только если в story.stickers есть type="post".
@Composable
private fun StoryPausedOverlay(
    story: Story,
    group: StoryGroup,
    onOpenAuthorProfile: (Long) -> Unit,
    onOpenAuthorClips: (Long) -> Unit,
    onOpenAuthorPhotos: (Long) -> Unit,
    onOpenAuthorVideos: (Long) -> Unit,
    onOpenPost: (Long, Long) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Тёмный полупрозрачный фон затемняет сам story-кадр, чтобы overlay был
        // визуально отделён. clickable на фоне — тап по пустой области = resume.
        // ВАЖНО: clickable НЕ здесь — иначе тап по чипам тоже закроет overlay
        // (child clickables всё равно перехватывают, но лишняя область ripple
        // некрасива). Фон НЕ кликабельный; resume только через чип «Продолжить»
        // или повторный тап в центр (через родительский detectTapGestures).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Иконка Pause в круге.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Пауза",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            // Ссылки-чипы.
            StoryOverlayChip(text = "Профиль автора") {
                onOpenAuthorProfile(group.ownerId)
            }
            StoryOverlayChip(text = "Клипы автора") {
                onOpenAuthorClips(story.ownerId)
            }
            StoryOverlayChip(text = "Фото автора") {
                onOpenAuthorPhotos(story.ownerId)
            }
            StoryOverlayChip(text = "Видео автора") {
                onOpenAuthorVideos(story.ownerId)
            }
            // Пост — только если в story.stickers есть type="post".
            val postSticker = story.stickers.firstOrNull { it.type == "post" }
            if (postSticker != null) {
                val pid = postSticker.postId ?: 0L
                val oid = postSticker.postOwnerId ?: 0L
                if (pid > 0L && oid != 0L) {
                    StoryOverlayChip(text = "Пост автора") {
                        onOpenPost(oid, pid)
                    }
                }
            }
            StoryOverlayChip(text = "Продолжить", primary = true, onClick = onResume)
        }
    }
}

// Чип-ссылка для StoryPausedOverlay. Полупрозрачный фон, белый текст, ripple.
// clickable (не pointerInput) — даёт accessibility + перехватывает тап у
// родительского detectTapGestures.
@Composable
private fun StoryOverlayChip(
    text: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (primary) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f)
    val fg = if (primary) Color.Black else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// P0.16 (Task 25): StickerPickerSheet
// ═══════════════════════════════════════════════════════════════════
// ModalBottomSheet для выбора стикера. Образец: ChatDetailScreen:9626-9851
// (EmojiStickerPanel). Здесь — упрощённая версия: только стикеры (без эмодзи-таб),
// pack-tabs сверху (горизонтальный скролл) + сетка стикеров 5 колонок.
// Locked-стикеры (purchased=false) — затемнённые + 🔒, тап → Toast «не куплен».
// Купленные — тап → onStickerClick(stickerId). ModalBottomSheet сам закроется
// колбэком onDismiss (вызывается в sendSticker после успешной отправки).
@Composable
private fun StickerPickerSheet(
    packs: List<StickerPack>,
    loading: Boolean,
    selectedPack: Int,
    onSelectPack: (Int) -> Unit,
    onStickerClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            // Шапка: заголовок + «Закрыть».
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Стикеры",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
            // Pack-tabs (горизонтальный скролл). Иконка пака или первая буква title.
            if (packs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    packs.forEachIndexed { idx, pack ->
                        val iconUrl = pack.icon?.url
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (idx == selectedPack)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { onSelectPack(idx) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (iconUrl != null) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = pack.title,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                Text(
                                    text = pack.title.take(1),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Сетка стикеров (5 колонок) или Loading/Empty.
            val currentStickers = packs.getOrNull(selectedPack)?.stickers ?: emptyList()
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                currentStickers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Нет стикеров",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val currentPack = packs.getOrNull(selectedPack)
                    val isPurchased = currentPack?.purchased != false
                    val isActive = currentPack?.active != false
                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(currentStickers, key = { it.stickerId }) { sticker ->
                            val url = sticker.displayUrl
                            // Fix #229: animatedDisplayUrl фильтрует .json/.tgs (Lottie),
                            // которые Coil не умеет декодировать — fallback на статичный url.
                            val renderUrl = sticker.animatedDisplayUrl ?: url
                            val dimAlpha = when {
                                !isPurchased -> 0.4f
                                !isActive -> 0.55f
                                else -> 1f
                            }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onStickerClick(sticker.stickerId) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (renderUrl != null) {
                                    AsyncImage(
                                        model = renderUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .graphicsLayer(alpha = dimAlpha),
                                    )
                                }
                                // Бейдж ▶ для анимированных стикеров (видно, что
                                // стикер заиграет в чате).
                                if (sticker.isAnimated && isPurchased && isActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(50),
                                            )
                                            .padding(2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Анимированный стикер",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                // 🔒 для не купленных, 📷 для деактивированных.
                                if (!isPurchased || !isActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 3.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = if (!isPurchased) "🔒" else "📷",
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** In-memory holder для передачи данных в StoryViewerScreen (аналог PostHolder). */
object StoryHolder {
    /** Все группы историй для просмотра. */
    @Volatile
    var groups: List<StoryGroup> = emptyList()
    /** Индекс начальной группы. */
    @Volatile
    var startGroupIndex: Int = 0
}