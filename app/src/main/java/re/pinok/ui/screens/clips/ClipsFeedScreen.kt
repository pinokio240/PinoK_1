package re.pinok.ui.screens.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
// Fix #140 (2026-08-03): navigationBarsPadding — чтобы нижний блок (автор +
// описание + музыка) не перекрывался системной navigation bar в edge-to-edge.
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.snapshotFlow
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.ClipVideoDownloadManager
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent

private const val TAG = "ClipsFeedScreen"

/**
 * §37.12 Phase 3: ClipsFeedScreen — главный экран clips.
 *
 * Архитектура:
 *  - VerticalPager (по 1 clip на страницу, swipe up/down для смены)
 *  - каждая страница: ClipPlayerItem (ExoPlayer в AndroidView + overlay с кнопками)
 *  - при смене страницы → vm.setCurrentIndex(i) → VM трекает просмотр предыдущего
 *  - auto-load next page при приближении к концу (в VM)
 *  - pull-to-refresh через TopAppBar
 *
 * @param vm ClipsViewModel
 * @param onAuthorClick(ownerId) — открыть профиль автора
 * @param onBack — закрыть экран
 * @param onShareClip(clip) — открыть share-sheet
 * @param onCommentClip(clip) — открыть комментарии
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ClipsFeedScreen(
    vm: ClipsViewModel,
    onAuthorClick: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onShareClip: (Video) -> Unit = {},
    onCommentClip: (Video) -> Unit = {},
    onMoreActions: (Video) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onCreateClip: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Первая загрузка при входе.
    LaunchedEffect(Unit) {
        if (state.clips.isEmpty() && !state.loading) {
            vm.loadFirst()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (state.loading && state.clips.isEmpty()) {
            // Полный загрузочный экран.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Загрузка клипов…",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                    )
                }
            }
        } else if (state.clips.isEmpty() && state.error != null) {
            // Экран ошибки.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Не удалось загрузить клипы",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.refresh() }) { Text("Повторить") }
                }
            }
        } else if (state.clips.isEmpty()) {
            // Пусто.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Клипов пока нет",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                )
            }
        } else {
            // VerticalPager с clip'ами.
            val pagerState = rememberPagerState(
                initialPage = state.currentIndex.coerceIn(0, state.clips.lastIndex),
                pageCount = { state.clips.size },
            )

            // Реагируем на смену страницы пользователем.
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        vm.setCurrentIndex(page)
                    }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                pageSpacing = 0.dp,
            ) { pageIndex ->
                val clip = state.clips[pageIndex]
                ClipPlayerItem(
                    clip = clip,
                    profiles = state.profiles,
                    groups = state.groups,
                    isCurrent = pageIndex == pagerState.currentPage,
                    isLiking = clip.id in state.likingClipIds,
                    isSubscribing = clip.id in state.subscribingClipIds,
                    onLike = { vm.toggleLike(clip) },
                    onSubscribe = { vm.toggleSubscribe(clip) },
                    onAuthorClick = { onAuthorClick(clip.ownerId) },
                    onShare = { onShareClip(clip) },
                    onComment = { onCommentClip(clip) },
                    onMore = { onMoreActions(clip) },
                    onHashtagClick = onHashtagClick,
                    onFetchDetails = { vm.fetchClipDetails(clip) },
                )
            }

            // Индикатор подгрузки снизу (если loadingMore).
            if (state.loadingMore) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        // Кнопка "назад" сверху-слева (поверх плеера).
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        // §37.12 Phase 5: FAB "создать клип" сверху-справа.
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onCreateClip() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Создать клип",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Один clip в VerticalPager: ExoPlayer + overlay (правая колонка кнопок + нижний блок автора).
 *
 * Игровой процесс:
 *  - Видео зацикливается (repeat=true)
 *  - Автопроигрывание только когда страница текущая
 *  - Тап по центру → pause/play
 *  - Mute-кнопка сверху-справа
 *  - Правая колонка: like, comment, share, more, music-icon
 *  - Нижний блок: author avatar+name + subscribe-btn + description + hashtags + music-info
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClipPlayerItem(
    clip: Video,
    profiles: Map<Long, UserProfile>,
    groups: Map<Long, VKApiClient.GroupInfo>,
    isCurrent: Boolean,
    isLiking: Boolean,
    isSubscribing: Boolean,
    onLike: () -> Unit,
    onSubscribe: () -> Unit,
    onAuthorClick: () -> Unit,
    onShare: () -> Unit,
    onComment: () -> Unit,
    onMore: () -> Unit,
    onHashtagClick: (String) -> Unit = {},
    onFetchDetails: () -> Unit = {},
) {
    val context = LocalContext.current
    // Fix #336: читаем preferredQuality СИНХРОННО из кэша SovaApp.prefsSnapshot
    // (раньше был async produceState — первый клип мог стартовать на "auto"=max
    // до загрузки pref). remember(clip.id) перечитывает pref при смене клипа.
    val preferredQuality = remember(clip.id) {
        re.pinok.SovaApp.get().prefsSnapshot?.videoPreferredQuality ?: "auto"
    }
    val playUrl = remember(clip.id, clip.ownerId, clip.bestPlayUrl, preferredQuality) {
        clip.playUrlForQuality(preferredQuality)
    }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember(clip.id) { mutableStateOf(clip.isMuted) }

    // §37.12 #329: состояние скачивания клипа. Подписываемся на ClipVideoDownloadManager.
    val clipDownloads by ClipVideoDownloadManager.downloads.collectAsState()
    val clipKey = "c_${clip.ownerId}_${clip.id}"
    val downloadState = clipDownloads[clipKey]
    val isDownloading = downloadState != null &&
        downloadState.status != re.pinok.data.model.DownloadStatus.COMPLETED &&
        downloadState.status != re.pinok.data.model.DownloadStatus.FAILED
    val isDownloaded = downloadState?.status == re.pinok.data.model.DownloadStatus.COMPLETED
    // §37.12 #329: progress — Int 0..100 (DownloadState.progress), не Float 0..1.
    val downloadProgress = downloadState?.progress ?: 0

    // §37.12 Phase 3.1 (#322, #324): lazy-fetch полных данных клипа (files[])
    // через video.get ТОЛЬКО когда bestPlayUrl==null. После перехода на
    // shortVideo.getRecom (canonical VK web endpoint) clips приходят с inline
    // files[] → bestPlayUrl сразу не-null → fetch не запускается. Fallback через
    // video.get нужен только для редких случаев (приватные clips, age-restricted).
    // accessKey НЕ обязателен для публичных clips (VK web передаёт bare videoId
    // в likes.add), поэтому не триггерим fetch только из-за null accessKey.
    LaunchedEffect(clip.id, clip.ownerId, clip.bestPlayUrl) {
        if (clip.id > 0 && clip.ownerId != 0L && clip.bestPlayUrl == null) {
            onFetchDetails()
        }
    }

    // ExoPlayer: создаётся один на clip, освобождается при уходе со страницы.
    val player = remember(clip.id, clip.ownerId, playUrl) {
        if (playUrl == null) return@remember null
        try {
            val vkUa = VkUserAgent.get(context.applicationContext as android.app.Application)
            val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(vkUa)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            val mediaItemBuilder = MediaItem.Builder().setUri(playUrl)
            if (playUrl.contains("m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().apply {
                    setMediaItem(mediaItemBuilder.build())
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = if (isMuted) 0f else 1f
                    playWhenReady = isCurrent && isPlaying
                    prepare()
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "ExoPlayer create error for ${clip.ownerId}_${clip.id}", e)
            null
        }
    }

    // Pause/play при смене current-страницы (для экономии батареи).
    LaunchedEffect(isCurrent, isPlaying) {
        player?.let {
            it.playWhenReady = isCurrent && isPlaying
            if (isCurrent && isPlaying) it.play() else it.pause()
        }
    }

    // Освобождаем player при уходе со страницы.
    DisposableEffect(clip.id, clip.ownerId) {
        onDispose {
            try { player?.release() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Видео (на весь экран, contain для вертикальных клипов).
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = player
                },
            )
        } else {
            // Fallback: постер + индикатор загрузки (пока fetchClipDetails подтягивает files[]).
            // #FIRST-FRAME: вертикальный постер (clipPosterUrl) вместо горизонтального covers
            // (thumbUrl) — для vertical клипа даёт правильный crop.
            clip.clipPosterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (clip.id > 0 && clip.ownerId != 0L) "Загрузка видео…" else "Видео недоступно",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Тап-зона: pause/play.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    isPlaying = !isPlaying
                    player?.let { if (isPlaying) it.play() else it.pause() }
                },
        )

        // Центральная ▶ при паузе.
        if (!isPlaying && player != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        // Правая колонка действий (как TikTok). §37.12 #332: mute-кнопка теперь
        // ПЕРВЫЙ элемент этой же колонки (раньше была отдельным TopEnd-виджетом),
        // поэтому интервал mute → аватар → like → ... → music СТРОГО одинаковый —
        // задаётся единым spacedBy. Раньше mute→аватар зависел от высоты экрана и
        // визуально слеплялся с аватаром, хотя spacedBy внутри колонки был равен.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .statusBarsPadding()
                .padding(end = 10.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1) Mute — звук вкл/выкл. Первый элемент колонки.
            IconButton(
                onClick = {
                    isMuted = !isMuted
                    player?.volume = if (isMuted) 0f else 1f
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 2) Аватар автора (тап → профиль).
            val authorAvatar = if (clip.ownerId > 0) {
                profiles[clip.ownerId]?.photo100
            } else {
                groups[-clip.ownerId]?.photo100
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable { onAuthorClick() },
            ) {
                if (authorAvatar != null) {
                    AsyncImage(
                        model = authorAvatar,
                        contentDescription = "Автор",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                // Маленький + внизу аватара если не подписан.
                if (!clip.isSubscribedToAuthor) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = "Подписаться",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // 3) Like.
            ActionButton(
                icon = if (clip.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = formatCount(clip.likesCount),
                tint = if (clip.isLiked) MaterialTheme.colorScheme.primary else Color.White,
                loading = isLiking,
                onClick = onLike,
            )

            // Comment.
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Comment,
                label = formatCount(clip.commentsCount),
                tint = Color.White,
                onClick = onComment,
            )

            // Share.
            ActionButton(
                icon = Icons.Filled.Share,
                label = formatCount(clip.repostsCount),
                tint = Color.White,
                onClick = onShare,
            )

            // More (context menu).
            ActionButton(
                icon = Icons.Filled.MoreVert,
                label = "Ещё",
                tint = Color.White,
                onClick = onMore,
            )

            // §37.12 #329: Скачать клип (для офлайн-просмотра).
            // Иконка меняется: Download (не скачано) → прогресс (качается) → CheckCircle (скачано).
            val downloadIcon = when {
                isDownloaded -> Icons.Filled.CheckCircle
                isDownloading -> Icons.Filled.Download
                else -> Icons.Filled.Download
            }
            val downloadLabel = when {
                isDownloaded -> "Скачано"
                isDownloading -> "$downloadProgress%"
                else -> "Скачать"
            }
            val downloadTint = when {
                isDownloaded -> MaterialTheme.colorScheme.primary
                else -> Color.White
            }
            ActionButton(
                icon = downloadIcon,
                label = downloadLabel,
                tint = downloadTint,
                loading = isDownloading,
                onClick = {
                    if (isDownloaded) {
                        // Уже скачано — показываем Toast (или можно открыть офлайн-плеер).
                        android.widget.Toast.makeText(
                            context, "Клип уже скачан — откройте в Офлайн-менеджере",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else if (!isDownloading) {
                        // Запускаем скачивание.
                        if (clip.bestPlayUrl.isNullOrBlank()) {
                            android.widget.Toast.makeText(
                                context, "Видео ещё загружается, подождите…",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@ActionButton
                        }
                        val authorName = if (clip.ownerId > 0) {
                            profiles[clip.ownerId]?.fullName
                        } else {
                            groups[-clip.ownerId]?.name
                        }
                        val authorAvatar = if (clip.ownerId > 0) {
                            profiles[clip.ownerId]?.photo100
                        } else {
                            groups[-clip.ownerId]?.photo100
                        }
                        ClipVideoDownloadManager.enqueueDownload(
                            video = clip,
                            authorName = authorName,
                            authorAvatar = authorAvatar,
                        )
                        android.widget.Toast.makeText(
                            context, "Скачивание начато",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )

            // Music info (вращающийся диск-иконка).
            if (clip.musicInfo != null) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Музыка",
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.6f))
                        .padding(8.dp),
                )
            }
        }

        // Нижний блок: автор + подписка + описание + музыка.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                // Fix #140: navigationBarsPadding — чтобы нижний блок не
                // перекрывался navigation bar в edge-to-edge (MainActivity
                // вызывает enableEdgeToEdge, и без этого padding'а нижние
                // 48dp контента уходят под nav bar).
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 80.dp, bottom = 24.dp),
        ) {
            // Имя автора (тап → профиль).
            val authorName = if (clip.ownerId > 0) {
                val p = profiles[clip.ownerId]
                p?.let { "${it.firstName} ${it.lastName}".trim() } ?: "id${clip.ownerId}"
            } else if (clip.ownerId < 0) {
                groups[-clip.ownerId]?.name ?: "Сообщество"
            } else "Автор"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = authorName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onAuthorClick() },
                )
                if (clip.ownerId != 0L && !clip.isSubscribedToAuthor) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onSubscribe,
                        enabled = !isSubscribing,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        if (isSubscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Подписаться", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Описание клипа (с кликабельными хештегами §37.10).
            if (!clip.description.isNullOrBlank()) {
                val desc = clip.description
                val hashtagRegex = Regex("#[\\wА-Яа-яЁё\\-_]+")
                val annotated = remember(desc) {
                    androidx.compose.ui.text.AnnotatedString.Builder(desc).apply {
                        var idx = 0
                        hashtagRegex.findAll(desc).forEach { m ->
                            // Текст до хештега.
                            if (m.range.first > idx) {
                                append(desc.substring(idx, m.range.first))
                            }
                            val tag = m.value
                            pushStringAnnotation(tag = "hashtag", annotation = tag)
                            pushStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    color = androidx.compose.ui.graphics.Color(0xFFBBDEFB),
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            append(tag)
                            pop()
                            pop()
                            idx = m.range.last + 1
                        }
                        if (idx < desc.length) append(desc.substring(idx))
                    }.toAnnotatedString()
                }
                Text(
                    text = annotated,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        // Найти ближайший хештег к клику не получится без offset,
                        // поэтому для простоты — клик по всему описанию открывает поиск первого тега.
                        val firstTag = hashtagRegex.find(desc)?.value
                        if (firstTag != null) onHashtagClick(firstTag)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }

            // Музыка.
            if (clip.musicInfo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${clip.musicInfo.artist} — ${clip.musicInfo.title}".trimStart('—', ' '),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Просмотров + дата.
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatCount(clip.views)} просмотров",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Одна кнопка действия в правой колонке (icon + count). */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Форматирование числа: 1234 → "1.2K", 1500000 → "1.5M". */
private fun formatCount(n: Int): String {
    if (n < 1000) return n.toString()
    if (n < 1_000_000) {
        val k = n / 1000.0
        return if (k >= 100) "${k.toInt()}K" else String.format("%.1fK", k)
    }
    val m = n / 1_000_000.0
    return if (m >= 100) "${m.toInt()}M" else String.format("%.1fM", m)
}

/**
 * Фабрика ViewModel для ClipsFeedScreen.
 * Использование:
 *   val vm: ClipsViewModel = viewModel(factory = clipsViewModelFactory())
 */
@Composable
fun clipsViewModelFactory(): androidx.lifecycle.ViewModelProvider.Factory {
    val context = LocalContext.current
    return viewModelFactory {
        initializer {
            val app = SovaApp.get(context)
            val repo = ClipsRepository(app.apiClient)
            ClipsViewModel(repo)
        }
    }
}
