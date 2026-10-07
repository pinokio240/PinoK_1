// File: music2/Music2TrackRow.kt
// P0.32 #MUSIC2 (2026-10): Переиспользуемая строка трека для Music2.
//
// Новые фичи по сравнению со старым MusicScreen:
// - Частотный визуализатор (MusicTrackCell_FrequencyBars) — анимированные полоски
//   при проигрывании трека, как в VK web.
// - Mini progress bar в строке (MusicTrackRow_PlayerProgressBar) — тонкая полоса
//   прогресса внизу строки трека, как в VK web.
// - Битрейт трека (vmsaudioRow__bitrate) — отображение качества аудио.
// - Volume slider per track (MusicTrackRow_VolumeSlider) — TODO (будет в следующей итерации).
//
// Переиспользует PlayerConnection для состояния плеера.

package re.pinok.ui.screens.music2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.data.model.Track

// P0.32: VK accent color для музыкального раздела.
private val VkAccent = Color(0xFF0077FF)

@Composable
fun Music2TrackRow(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // P0.32: Play/Pause иконка ИЛИ частотный визуализатор.
            // VK web: MusicTrackRow_PlaybackControls + MusicTrackCell_FrequencyBars.
            // Когда трек играет — показываем анимированные полоски визуализатора.
            // Когда не играет — PlayArrow.
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    FrequencyBars(
                        modifier = Modifier.size(24.dp),
                        color = VkAccent,
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Воспроизвести",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // P0.32: информация о треке — title + artist + bitrate.
            // VK web: MusicTrackRow_Title + MusicTrackRow_Authors + vmsaudioRow__bitrate.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) VkAccent else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // P0.32: HQ badge — высокое качество трека.
                    // VK web: vmsaudioRow__bitrate. PinoK: используем isHq флаг
                    // (VK API отдаёт is_hq=true для HQ треков). Поле bitrate пока
                    // не парсится — TODO: добавить в Track модель + VKApiClient.
                    if (track.isHq) {
                        Text(
                            text = "HQ",
                            style = MaterialTheme.typography.labelSmall,
                            color = VkAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // P0.32: длительность трека.
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // P0.32: контекстное меню (3 точки).
            // VK web: MusicAudio_MenuButton.
            // TODO: реализовать dropdown — дизлайк/own/lyrics/share/snippet.
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Меню",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // P0.32: Mini progress bar в строке трека.
        // VK web: MusicTrackRow_PlayerProgressBar — тонкая полоса прогресса внизу строки.
        // Показывается только когда трек играющий (isPlaying) и progress > 0.
        if (isPlaying && progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(VkAccent),
                )
            }
        }
    }
}

// P0.32: Частотный визуализатор — анимированные полоски.
// VK web: MusicTrackCell_FrequencyBars — 4 вертикальные полоски разной высоты,
// анимированные когда трек играет.
@Composable
private fun FrequencyBars(
    modifier: Modifier = Modifier,
    color: Color = VkAccent,
) {
    // P0.32: 4 полоски с разной высотой, анимированные через infiniteTransition.
    // Каждая полоска пульсирует с разной задержкой и скоростью.
    val transition = rememberInfiniteTransition(label = "freq_bars")
    val bars = listOf(
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar1",
        ),
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar2",
        ),
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar3",
        ),
        transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar4",
        ),
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { bar ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp * bar.value)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

// P0.32: форматирование длительности — секунды → "M:SS".
private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
