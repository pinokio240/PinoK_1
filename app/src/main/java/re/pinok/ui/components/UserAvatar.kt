package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Волна 2 (2026-11-13): единый компонент круглого аватара.
 *
 * Заменяет ~55 инлайн-реализаций AsyncImage + CircleShape + инициал по всему
 * проекту (ProfileScreen, ChatDetailScreen, MessagesScreen, FriendsScreen,
 * CommunityScreen и т.д.).
 *
 * - [url] — ссылка на фото (null → fallback).
 * - [fallbackText] — инициал/буква при отсутствии фото (берётся первая буква).
 * - [fallbackIcon] — иконка при отсутствии фото (приоритетнее, чем текст).
 * - [size] — размер в dp.
 * - [shape] — форма (по умолчанию круг).
 * - [onlineDot] — зелёная точка «онлайн» в правом нижнем углу (размер = size/4).
 */
@Composable
fun UserAvatar(
    url: String?,
    fallbackText: String = "?",
    fallbackIcon: ImageVector? = null,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    onlineDot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = fallbackText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (fallbackIcon != null) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallbackText.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.25f)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF4CAF50)),
            )
        }
    }
}