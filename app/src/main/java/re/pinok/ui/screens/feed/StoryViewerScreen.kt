package re.pinok.ui.screens.feed

import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
// Fix #140 (2026-08-03): navigationBarsPadding — нижние оверлеи не перекрываются
// navigation bar в edge-to-edge.
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.Story
import re.pinok.data.model.StoryGroup
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent
import re.pinok.media.StoryVideoDownloadManager
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
            val g = groups.getOrNull(groupIndex)
            if (g == null) { onBack(); return@launch }
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
    LaunchedEffect(groupIndex, storyIndex) {
        progress = 0f
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
    // Fix #109: state для ручной кнопки скачивания истории (mirror VideoDownloadManager.downloads).
    val downloads by StoryVideoDownloadManager.downloads.collectAsState()
    val exoPlayer = remember(videoUrl) {
        if (videoUrl == null) return@remember null
        try {
            val vkUa = VkUserAgent.get(context.applicationContext as android.app.Application)
            val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(vkUa)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(groupIndex, storyIndex) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 3f) {
                            goToPrev()
                        } else {
                            goToNext()
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    .padding(end = 16.dp, bottom = 56.dp),
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

/** In-memory holder для передачи данных в StoryViewerScreen (аналог PostHolder). */
object StoryHolder {
    /** Все группы историй для просмотра. */
    @Volatile
    var groups: List<StoryGroup> = emptyList()
    /** Индекс начальной группы. */
    @Volatile
    var startGroupIndex: Int = 0
}