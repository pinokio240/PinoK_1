package re.pinok.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Fix #234 (multi-photo preview): переиспользуемый горизонтальный бар
 * миниатюр выбранных фото над полем ввода.
 *
 * Fix #235 (crash + multi-file): ранее в itemsIndexed использовался
 * key = { i, u -> "$i-$u" }, но из-за экранирования доллара при записи
 * файла (Write tool) в файле оказалось "$i-$u" буквально — ВСЕ элементы
 * получали ОДИНАКОВЫЙ ключ → IllegalArgumentException: Key "$i-$u" was
 * already used → FATAL на recomposition после выбора фото. Та же беда с
 * счётчиком "📷 ${photos.size} фото" — рендерился буквально "${photos.size}".
 *
 * Решение: обернуть Uri в [PendingPhoto] со стабильным уникальным id
 * (autoincrement). Это даёт уникальные ключи даже когда один и тот же
 * Uri добавлен дважды (повторный выбор того же фото), и сохраняет
 * identity элемента при удалении из середины (Compose переиспользует
 * композабл-ы корректно).
 *
 * UX:
 *  — появляется анимированно (expand+fade), когда список не пуст
 *  — каждая миниатюра 72dp, скруглённая 8dp
 *  — кнопка «×» в правом верхнем углу каждой миниатюры
 *  — клик по миниатюре → onPreview(index) → полноэкранный просмотр
 *  — кнопка «×» → onRemove(index)
 *  — счётчик «N фото» слева для ясности
 *
 * Используется в:
 *  — ChatDetailScreen: над полем ввода сообщения
 *  — PostDetailScreen: над полем ввода комментария
 */
data class PendingPhoto(
    val id: Long,
    val uri: Uri,
)

/**
 * Генератор уникальных id для [PendingPhoto]. Каждый вызов возвращает
 * большее число — гарантированно уникальное в рамках сессии.
 */
private val photoIdCounter = java.util.concurrent.atomic.AtomicLong(0)
fun nextPendingPhotoId(): Long = photoIdCounter.incrementAndGet()

@Composable
fun PendingPhotosBar(
    photos: List<PendingPhoto>,
    onRemove: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = photos.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📷 ${photos.size} фото",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Тап — увеличить, × — убрать",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    items(
                        items = photos,
                        // Fix #235: стабильный уникальный ключ — id, не Uri.
                        // Раньше тут было "$i-$u" с экранированным долларом → crash.
                        key = { it.id },
                    ) { photo ->
                        val index = photos.indexOf(photo)
                        PendingPhotoThumb(
                            uri = photo.uri,
                            onRemove = { onRemove(index) },
                            onPreview = { onPreview(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPhotoThumb(
    uri: Uri,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    Box(
        modifier = Modifier.size(72.dp),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Выбранное фото",
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPreview),
            contentScale = ContentScale.Crop,
        )
        // Полупрозрачный × в правом верхнем углу миниатюры.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Убрать фото",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
