// File: ui/screens/offline/ClipOfflinePlayerScreen.kt
package re.pinok.ui.screens.offline

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
// Fix #140: navigationBarsPadding — чтобы нижний оверлей не перекрывался
// navigation bar в edge-to-edge.
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import re.pinok.media.ClipVideoDownloadManager
import re.pinok.util.AppLog

/**
 * §37.12 #330: Экран офлайн-просмотра скачанного клипа.
 *
 * Открывается из OfflineManager → «Клипы» tab при тапе на скачанный clip.
 * Читает локальный .mp4 файл через [ClipVideoDownloadManager.getLocalFile]
 * и meta (title, authorName, authorAvatar, thumbUrl, duration, downloadedAt)
 * через [ClipVideoDownloadManager.getClipMeta].
 *
 * Воспроизведение через ExoPlayer с file:// URI — полностью офлайн, без сети.
 *
 * ## Отличия от [StoryOfflinePlayerScreen]
 *  - Параметр: `videoId: Long` (вместо `storyId: Int` — clips используют
 *    Long videoId как обычные VK видео).
 *  - Источник данных: [ClipVideoDownloadManager] (вместо StoryVideoDownloadManager).
 *  - ResizeMode = `RESIZE_MODE_ZOOM` (TikTok-стиль — видео заполняет экран
 *    полностью, края могут обрезаться). Stories используют Fit (letterbox).
 *  - Thumbnail `ContentScale.Crop` (вместо Fit) — preview тоже заполняет экран.
 *  - Header показывает `meta.title` (у stories — `meta.ownerName`).
 *  - Sub-header показывает «скачано N д назад» через `meta.downloadedAt`
 *    (у stories — `meta.storyDate`).
 *
 * UI (TikTok / VK Clips стиль):
 *  - Полноэкранный чёрный фон + PlayerView (видео в ZOOM-resize, 9:16 fills screen).
 *  - Photo placeholder пока видео буферизируется (shutter transparent).
 *  - Header: аватар автора + имя автора + заголовок + кнопка «Назад».
 *  - Центральная кнопка Play/Pause (тап по экрану = toggle).
 *  - Прогресс-бар внизу (position / duration).
 *  - Бейдж «ОФЛАЙН» в правом верхнем углу.
 *
 * @param ownerId  owner_id клипа (может быть отрицательным для групп).
 * @param videoId  id клипа (Long, как у Video.id).
 * @param onBack   колбэк закрытия (обычно nav.popBackStack()).
 */
@Composable
fun ClipOfflinePlayerScreen(
    ownerId: Long,
    videoId: Long,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val meta = remember(ownerId, videoId) {
        ClipVideoDownloadManager.getClipMeta(ownerId, videoId)
    }
    val localFile = remember(ownerId, videoId) {
        ClipVideoDownloadManager.getLocalFile(ownerId, videoId)
    }

    // §37.12 #330: если файл удалён/истёк TTL — закрываем экран (mirror StoryOfflinePlayer).
    if (localFile == null || !localFile.exists()) {
        AppLog.w(TAG, "file not found for ownerId=$ownerId videoId=$videoId — closing")
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val fileUri = "file://${localFile.absolutePath}"
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    val exoPlayer = remember(fileUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(fileUri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY && duration == 0L) {
                        durationMs = duration.coerceAtLeast(1L)
                    }
                }
            })
        }
    }

    // §37.12 #330: тик позиции воспроизведения каждые 200ms (mirror StoryOfflinePlayer).
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying || isPlaying) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (durationMs == 0L && exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
            }
            kotlinx.coroutines.delay(200L)
        }
    }

    // §37.12 #330: release ExoPlayer на выходе.
    DisposableEffect(exoPlayer) {
        onDispose {
            AppLog.d(TAG, "release ExoPlayer for clip ownerId=$ownerId videoId=$videoId")
            exoPlayer.release()
        }
    }

    // Системный back button.
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // §37.12 #330: тап по экрану = play/pause toggle (TikTok-style).
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
    ) {
        // §37.12 #330: thumbnail-превью под PlayerView — пока видео буферизируется.
        // ContentScale.Crop (вместо Fit у stories) — заполняет экран как TikTok preview.
        val thumb = meta?.thumbUrl
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // §37.12 #330: PlayerView поверх превью — RESIZE_MODE_ZOOM для TikTok-стиля
        // (видео 9:16 fills экран полностью, края могут обрезаться).
        // Stories используют RESIZE_MODE_FIT (letterbox), но clips — vertical full-bleed.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    player = exoPlayer
                }
            },
            update = { pv -> pv.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        // ─── Верхний градиент для читаемости хедера ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                    ),
                )
                .align(Alignment.TopCenter),
        )

        // ─── Header: back + аватар автора + имя автора + заголовок ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                )
            }
            val avatarUrl = meta?.authorAvatar
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
                        text = (meta?.authorName ?: "?").take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta?.authorName ?: "Клип",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val title = meta?.title?.ifBlank { null }
                if (title != null) {
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (meta != null && meta.downloadedAt > 0) {
                    Text(
                        text = "скачан ${formatDownloadedAtShort(meta.downloadedAt)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
            }
            // §37.12 #330: бейдж «ОФЛАЙН» (как у StoryOfflinePlayer).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "ОФЛАЙН",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ─── Центральная кнопка Play/Pause (полупрозрачная) ───
        if (!isPlaying && !isBuffering) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // ─── Buffering spinner ───
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center),
            )
        }

        // ─── Нижний градиент + прогресс-бар ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    ),
                )
                .align(Alignment.BottomCenter),
        )

        if (durationMs > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    // Fix #140: navigationBarsPadding — чтобы контролы не
                    // перекрывались navigation bar в edge-to-edge.
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatMs(positionMs),
                    color = Color.White,
                    fontSize = 11.sp,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    val pct = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White),
                    )
                }
                Text(
                    text = formatMs(durationMs),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private const val TAG = "ClipOfflinePlayer"

/** Форматирование миллисекунд в M:SS (mirror StoryOfflinePlayerScreen.formatMs). */
private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}

/**
 * §37.12 #330: короткий human-readable «N д/ч/мин назад» для даты скачивания clip'а.
 * Mirror StoryOfflinePlayerScreen.formatStoryOfflineDate.
 */
private fun formatDownloadedAtShort(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = (now - timestampMs) / 1000
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        else -> "${diff / 86400} д назад"
    }
}
