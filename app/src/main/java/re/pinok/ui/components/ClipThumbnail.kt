package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import re.pinok.data.model.Video

/**
 * POSTCARD-REFACTOR-PLAN (этап 2): общая вертикальная карточка клипа
 * (короткого вертикального видео) как вложения поста.
 *
 * Вынесена из CommunityScreen (была private) в общий `ui/components`, чтобы
 * [PostAttachmentRenderer] мог её переиспользовать. Логика сохранена 1-в-1:
 *
 * - вертикальный постер 9:16 (first_frames → clipPosterUrl, #GROUP-CLIPS);
 * - play-круг по центру;
 * - название — полупрозрачной плашкой снизу (как в clips-сетке VK).
 *
 * Тап по карточке → [onClick].
 */
@Composable
fun ClipThumbnail(video: Video, onClick: (Video) -> Unit, compact: Boolean = false) {
    // #GROUP-CLIPS: вертикальный постер (first_frames → clipPosterUrl), как в веб-VK.
    val thumbUrl = video.clipPosterUrl ?: video.thumbUrl
    if (compact) {
        // COMPACT-CLIP-GRID: compact режим для сетки клипов в сообществе —
        // только превью + play button, без Card/padding/названия.
        // weight(1f) обязателен — иначе в Row fillMaxWidth занимает всю ширину.
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick(video) },
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
                modifier = Modifier.size(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
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
            // Название снизу (как в clips-сетке VK).
            if (video.title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}