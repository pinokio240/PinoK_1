package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import coil3.compose.AsyncImage
import re.pinok.data.model.Video
import re.pinok.util.toCountString

/**
 * Общая карточка видео-вложения (16:9 превью с длительностью + play-кругом).
 *
 * Вынесена из FeedScreen/CommunityScreen/ProfileScreen на этапе 1 рефакторинга
 * POSTCARD-REFACTOR-PLAN в общий `ui/components`. Поведение исходных
 * реализаций сохранено через флаги:
 *
 * - `showTitle` — показывать ли блок «название + счётчики» под превью
 *   (ProfileScreen ранее не показывал его вовсе);
 * - `titleExpandable` — разворачивать ли длинное название по тапу
 *   (#POST-TEXT-EXPAND, исходное поведение CommunityScreen); при false —
 *   статичное название (FeedScreen).
 */
@Composable
fun VideoThumbnail(
    video: Video,
    onClick: (Video) -> Unit,
    showTitle: Boolean = true,
    titleExpandable: Boolean = false,
) {
    val thumbUrl = video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                if (video.duration > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            // #VIDEO-TITLE-COMMENTS: название + счётчики (просмотры/комментарии),
            // если доступны — как в веб-VK под превью видео-вложения.
            if (showTitle && (video.title.isNotBlank() || video.views > 0 || video.commentsCount > 0)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (video.title.isNotBlank()) {
                        if (titleExpandable) {
                            // #POST-TEXT-EXPAND (Fix #345): длинное название видео
                            // разворачивается тапом (перехватывает клик карточки;
                            // тап по превью открывает видео как раньше, а тап по
                            // развёрнутому названию — тоже открывает видео).
                            var titleExpanded by remember { mutableStateOf(false) }
                            var titleOverflowed by remember { mutableStateOf(false) }
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    if (titleExpanded) {
                                        onClick(video)
                                    } else {
                                        titleExpanded = true
                                    }
                                },
                                onTextLayout = { result ->
                                    if (!titleExpanded && result.hasVisualOverflow) titleOverflowed = true
                                },
                                maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (titleOverflowed && !titleExpanded) {
                                Text(
                                    text = "Показать ещё",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1976D2),
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { titleExpanded = true },
                                )
                            }
                        } else {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (video.views > 0 || video.commentsCount > 0) {
                        val meta = buildList {
                            if (video.views > 0) add("${video.views.toCountString()} просмотров")
                            if (video.commentsCount > 0) add("${video.commentsCount.toCountString()} комментариев")
                        }.joinToString(" • ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}