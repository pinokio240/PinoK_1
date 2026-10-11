package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import re.pinok.data.model.Post
import re.pinok.data.model.Video
import re.pinok.util.linkifyVkText
import re.pinok.util.openUrlExternal
import coil3.compose.AsyncImage

// Fix #70: RepostBlock — отображение copy_history[0] как вложенной карточки.
// Показывает заголовок «Запись <ownerId>» + текст + вложения оригинального поста
// (фото/видео/аудио/плейлисты — #PROFILE-REPOST-ATTACH).
// Sprint 2, P1-1 (#88): фото кликабельны → PhotoViewer.
@Composable
fun RepostBlock(
    repost: Post,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    onVideoClick: (Video) -> Unit = {},
    // Fix #373 (#PROFILE-REPOST-OPEN): тап по карточке репоста / по развёрнутому
    // или короткому тексту → открытие поста-оригинала (PostDetail через onPostClick
    // вызывающей карточки; колбэк пробрасывается из WallPostCard, дефолт {} —
    // как у соседей, прежние вызовы совместимы).
    onPostClick: (Post) -> Unit = {},
    // 22-B: карусель фото (общий SovaPrefs.feedCarouselEnabled, пробрасывается
    // из WallPostCard). Дефолт true — легаси-вызовы совместимы (паттерн 19-A).
    carouselEnabled: Boolean = true,
    // Группа-источник репоста (repost.ownerId<0). Если переданы groupName —
    // рендерим её аватар/имя/клик в группу ПОВЕРХ «Запись клуба <ownerId>»
    // (или вместо него). null — прежний вид (заголовок «Запись клуба <id>»).
    groupName: String? = null,
    groupPhoto: String? = null,
    onGroupClick: (() -> Unit)? = null,
) {
    // Fix #373: контекст для linkify-кликов в тексте репоста (openUrlExternal).
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            // Fix #373: карточка репоста ЦЕЛИКОМ открывает оригинальный пост.
            // clickable ПОСЛЕ clip — риппл по скруглению; вложенные клики
            // (PostPhotoGrid/видео/аудио/ссылки ниже) перехватываются раньше —
            // открывается просмотр, а не пост (потомок кликабельнее родителя).
            .clickable { onPostClick(repost) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                val srcGroupName = groupName
                if (srcGroupName != null) {
                    // Группа-источник репоста: аватар 20dp (AsyncImage или
                    // буква-фоллбэк из groupName.take(1)) + имя (клик → onGroupClick).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape),
                        ) {
                            val photo = groupPhoto
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = srcGroupName,
                                    modifier = Modifier.size(20.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = srcGroupName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = srcGroupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (onGroupClick != null) {
                                TextDecoration.Underline
                            } else {
                                TextDecoration.None
                            },
                            modifier = if (onGroupClick != null) {
                                Modifier.clickable { onGroupClick?.invoke() }
                            } else {
                                Modifier
                            },
                        )
                    }
                } else {
                    Text(
                        // Audit #40: для user-posts (ownerId>0) не показываем "клуба" —
                        // иначе получается двойной пробел "Запись  123". buildString аккуратнее.
                        text = buildString {
                            append("Запись")
                            if (repost.ownerId < 0) append(" клуба")
                            append(' ')
                            append(repost.ownerId)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (repost.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                // Fix #373 (#PROFILE-REPOST-EXPAND): текст репоста — паттерн Fix #345
                // (CommunityScreen:1337-1365 / FeedScreen:2571-2609) + linkify: «Показать
                // ещё» только при реальном переполнении (hasVisualOverflow); тап по
                // свёрнутому тексту = развернуть, по развёрнутому/короткому = открыть
                // пост целиком (onPostClick).
                var repostTextExpanded by remember { mutableStateOf(false) }
                var repostTextOverflowed by remember { mutableStateOf(false) }
                Text(
                    text = linkifyVkText(
                        text = repost.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onUrlClick = { url -> openUrlExternal(ctx, url) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (repostTextOverflowed && !repostTextExpanded) {
                            repostTextExpanded = true
                        } else {
                            onPostClick(repost)
                        }
                    },
                    onTextLayout = { result ->
                        if (!repostTextExpanded && result.hasVisualOverflow) repostTextOverflowed = true
                    },
                    maxLines = if (repostTextExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repostTextOverflowed && !repostTextExpanded) {
                    Text(
                        text = "Показать ещё",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clickable { repostTextExpanded = true },
                    )
                }
            }
            // Фото из репоста
            val repostPhotos = repost.attachments
                ?.filter { it.type == "photo" && it.photo != null }
                .orEmpty()
            if (repostPhotos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                // 22-B: общий PostPhotoGrid (см. WallPostCard выше).
                PostPhotoGrid(
                    photos = repostPhotos.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                    carouselEnabled = carouselEnabled,
                )
            }
            // #PROFILE-REPOST-ATTACH: видео в репосте (как в WallPostCard).
            val repostVideos = repost.attachments
                ?.filter { it.type == "video" && it.video != null }
                .orEmpty()
            // Fix #373: несколько видео в репосте и включённая карусель —
            // общий PostVideoCarousel (как в посте выше и в ленте, Fix #366);
            // иначе прежний вертикальный стопк VideoThumbnail (одно видео).
            if (carouselEnabled && repostVideos.size > 1) {
                Spacer(Modifier.height(6.dp))
                PostVideoCarousel(
                    videos = repostVideos.mapNotNull { it.video },
                    carouselEnabled = carouselEnabled,
                    onVideoClick = onVideoClick,
                )
            } else {
                repostVideos.forEach { att ->
                    val v = att.video
                    if (v != null) {
                        Spacer(Modifier.height(6.dp))
                        VideoThumbnail(video = v, onClick = onVideoClick, showTitle = false)
                    }
                }
            }
            // #PROFILE-REPOST-ATTACH: аудио в репосте.
            val repostAudios = repost.attachments
                ?.filter { it.type == "audio" && it.audio != null }
                .orEmpty()
            if (repostAudios.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AudioAttachmentList(tracks = repostAudios.mapNotNull { it.audio })
            }
            // #PROFILE-REPOST-ATTACH: плейлисты в репосте.
            repost.attachments
                ?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }
                ?.forEach { att ->
                    att.audioPlaylist?.let {
                        Spacer(Modifier.height(6.dp))
                        PlaylistAttachmentCard(playlist = it)
                    }
                }
        }
    }
}