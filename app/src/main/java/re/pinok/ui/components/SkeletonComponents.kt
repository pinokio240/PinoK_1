package re.pinok.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * S7-3: Модификатор shimmer-эффекта для скелетон-загрузки.
 *
 * Использует анимированный линейный градиент, который «протекает» по элементу.
 */
private fun Modifier.shimmer(
    widthFraction: Float = 1f,
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor,
    )
    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(offset * widthFraction, 0f),
            end = Offset(offset * widthFraction + widthFraction, 0f),
        ),
    )
}

/** Селектор цвета для shimmer — поддерживает как light, так и dark тему. */
private val skeletonBase: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

// ─── Базовые примитивы ──────────────────────────────────────────────

/** Серый прямоугольник-заглушка с shimmer. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .shimmer(),
    )
}

/** Круглая заглушка-аватар с shimmer. */
@Composable
fun SkeletonCircle(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .shimmer(),
    )
}

/** Заглушка для текстовой строки с shimmer. */
@Composable
fun SkeletonText(
    widthFraction: Float = 0.7f,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
    )
}

// ─── Составные скелетоны для экранов ────────────────────────────────

/** S7-3: Скелетон для строки списка (Feed, Messages, Notifications). */
@Composable
fun SkeletonListRow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonText(widthFraction = 0.5f, height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonText(widthFraction = 0.8f, height = 12.dp)
            Spacer(modifier = Modifier.height(4.dp))
            SkeletonText(widthFraction = 0.4f, height = 10.dp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        SkeletonText(widthFraction = 0.15f, height = 10.dp)
    }
}

/** S7-3: Скелетон для поста в ленте. */
@Composable
fun SkeletonPostCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        // Header: avatar + name + date
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = 40.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonText(widthFraction = 0.4f, height = 14.dp)
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonText(widthFraction = 0.2f, height = 10.dp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Text placeholder
        SkeletonText(
            modifier = Modifier.padding(horizontal = 16.dp),
            widthFraction = 0.9f,
            height = 14.dp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        SkeletonText(
            modifier = Modifier.padding(horizontal = 16.dp),
            widthFraction = 0.6f,
            height = 14.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Image placeholder
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Bottom bar: likes, comments, etc
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SkeletonText(widthFraction = 0.12f, height = 12.dp)
            SkeletonText(widthFraction = 0.12f, height = 12.dp)
            SkeletonText(widthFraction = 0.12f, height = 12.dp)
        }
    }
}

/** S7-3: Скелетон для карточки трека (MusicScreen). */
@Composable
fun SkeletonTrackRow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art placeholder
        SkeletonBox(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonText(widthFraction = 0.6f, height = 15.dp)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonText(widthFraction = 0.4f, height = 13.dp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        SkeletonText(widthFraction = 0.1f, height = 12.dp)
    }
}

/** S7-3: Скелетон для строки диалога (MessagesScreen). */
@Composable
fun SkeletonChatRow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = 56.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonText(widthFraction = 0.35f, height = 15.dp)
                SkeletonText(widthFraction = 0.15f, height = 10.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonText(widthFraction = 0.7f, height = 13.dp)
        }
    }
}

// ─── Списки скелетонов для экранов ──────────────────────────────────

/** S7-3: Список из N скелетон-строк (Feed, Notifications, Friends). */
@Composable
fun SkeletonList(
    count: Int = 8,
    rowContent: @Composable () -> Unit = { SkeletonListRow() },
) {
    Column {
        repeat(count) {
            rowContent()
        }
    }
}

/** S7-3: Список из N скелетон-постов для ленты. */
@Composable
fun SkeletonFeedList(count: Int = 5) {
    SkeletonList(count = count) { SkeletonPostCard() }
}

/** S7-3: Список из N скелетон-треков для MusicScreen. */
@Composable
fun SkeletonTrackList(count: Int = 10) {
    SkeletonList(count = count) { SkeletonTrackRow() }
}

/** S7-3: Список из N скелетон-диалогов для MessagesScreen. */
@Composable
fun SkeletonChatList(count: Int = 8) {
    SkeletonList(count = count) { SkeletonChatRow() }
}