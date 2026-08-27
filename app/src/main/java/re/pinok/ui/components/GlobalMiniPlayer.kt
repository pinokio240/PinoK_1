package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.media.PlayerConnection

/**
 * Глобальный компактный мини-плеер (48dp) над NavigationBar.
 *
 * Layout: [▶ Play] ─── название — артист (marquee) ─── [⏭ Next]
 * Тап по полоске → onOpenPlayer. Прогресс 2dp сверху.
 */
@Composable
fun GlobalMiniPlayer(
    onOpenPlayer: () -> Unit,
) {
    val playerState by PlayerConnection.playerState.collectAsState()
    val track = playerState.currentTrack ?: return

    val cardColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary

    val effectiveDuration = if (playerState.durationMs > 0) playerState.durationMs else track.duration * 1000L
    val progressFraction = if (effectiveDuration > 0) {
        (playerState.positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LinearProgressIndicator(
        progress = { progressFraction },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = accentColor,
        trackColor = cardColor.copy(alpha = 0.5f),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor)
            .height(48.dp)
            .clickable(onClick = onOpenPlayer)
            .padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ▶ Play/Pause (слева)
        IconButton(
            onClick = { PlayerConnection.togglePlayPause() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playerState.isPlaying) "Пауза" else "Играть",
                tint = textColor,
                modifier = Modifier.size(24.dp),
            )
        }

        // Название трека — basicMarquee для длинных названий.
        Text(
            text = "${track.title} — ${track.artist}",
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 30.dp, // ~30dp/с как было раньше
                ),
        )

        // ⏭ Next (справа)
        IconButton(
            onClick = { PlayerConnection.next() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Следующий",
                tint = textColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}