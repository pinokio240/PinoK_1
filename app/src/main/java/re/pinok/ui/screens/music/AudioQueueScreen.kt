// File: ui/screens/music/AudioQueueScreen.kt
package re.pinok.ui.screens.music

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.data.model.PlayerState
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import kotlin.math.abs

/**
 * Fix #62: Экран очереди воспроизведения (список «Далее»).
 *
 * Моделирован по скриншоту SOVA reference (Screenshot_20260628_185136.png):
 *  — Заголовок «Далее» + кнопка «Сохранить как плейлист» (TODO)
 *  — Список треков из очереди (PlayerState.queue), начиная с currentIndex
 *  — Текущий трек выделен иконкой play/pause
 *  — Тап по треку → переключение на него (seekToDefaultPosition)
 *  — Нижний ряд контролов: shuffle / list / repeat
 */
@Composable
fun AudioQueueScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val playerState by PlayerConnection.playerState.collectAsState()

    val vkBlack = Color(0xFF0F0F10)
    val vkCard = Color(0xFF1C1C1E)
    val vkTextPrimary = Color(0xFFFFFFFF)
    val vkTextSecondary = Color(0xFFA8A8AA)
    val vkAccent = Color(0xFF3D8BFF)

    val queue = playerState.queue
    val currentIndex = playerState.currentIndex
    // Показываем треки начиная с текущего (как в SOVA — «Далее»)
    val upcoming = if (currentIndex >= 0 && currentIndex < queue.size) {
        queue.subList(currentIndex, queue.size)
    } else {
        queue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vkBlack)
            .statusBarsPadding(),
    ) {
        // ─── Top bar: back + «Далее» + «Сохранить как плейлист» ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = vkTextPrimary)
            }
            Text(
                text = "Далее",
                color = vkTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            // #QUEUE-COUNT (2026-08-01): счётчик треков в очереди. Раньше его не
            // было — пользователь жаловался «не отображается количество треков».
            // Показываем «N треков» (с правильным склонением) рядом с заголовком.
            if (upcoming.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                val n = upcoming.size
                val countLabel = when {
                    n % 100 in 11..14 -> "$n треков"
                    n % 10 == 1 -> "$n трек"
                    n % 10 in 2..4 -> "$n трека"
                    else -> "$n треков"
                }
                Text(
                    text = countLabel,
                    color = vkTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = "Сохранить как плейлист",
                color = vkAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { /* TODO: сохранение в плейлист */ }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        // ─── Список очереди ────────────────────────────────────────────
        // #QUEUE-LAYOUT (2026-08-01): contentPadding уменьшен с 80dp до 16dp.
        // Раньше 80dp резервировали место под нижнюю панель контролов — но
        // панель теперь в Column-flow (не overlay), список её не перекрывает.
        // 80dp давали лишний пустой хвост при скролле.
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (upcoming.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.MusicNote, null,
                                tint = vkTextSecondary, modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Очередь пуста", color = vkTextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Выберите треки в разделе «Музыка»", color = vkTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(upcoming, key = { "${it.ownerId}_${it.id}" }) { track ->
                    val isCurrent = track.id == playerState.currentTrack?.id &&
                        track.ownerId == playerState.currentTrack?.ownerId
                    QueueTrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        cardColor = vkCard,
                        textColor = vkTextPrimary,
                        secondaryColor = vkTextSecondary,
                        accentColor = vkAccent,
                        onClick = {
                            if (!isCurrent) {
                                PlayerConnection.playTrackById(track.id, upcoming)
                            } else {
                                PlayerConnection.togglePlayPause()
                            }
                        },
                    )
                }
            }
        }

        // ─── Нижний ряд: shuffle / list / repeat ───────────────────────
        // Fix #258: панель узкая (wrapContentWidth) и центрированная,
        // а не fillMaxWidth + SpaceEvenly (растягивалось на весь экран).
        // #QUEUE-LAYOUT (2026-08-01): УБРАН windowInsetsPadding(navigationBars).
        // Этот экран хостится внутри Scaffold, чей bottomBar-слот (GlobalMiniPlayer
        // + NavigationBar) САМ резервирует navigationBars inset (SovaNavHost:806).
        // Дублирование inset здесь создавало ~48dp пустоты между панелью контролов
        // и мини-плеером (жёлтая зона на Screenshot_20260801_194131.png).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(vkCard)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(vkBlack.copy(alpha = 0.5f))
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    PlayerConnection.setShuffleModeEnabled(!playerState.shuffleModeEnabled)
                }) {
                    Icon(
                        Icons.Filled.Shuffle, "Перемешать",
                        tint = if (playerState.shuffleModeEnabled) vkAccent else vkTextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Иконка «список» (текущий экран)
                Icon(
                    Icons.Filled.MusicNote, "Список",
                    tint = vkAccent, modifier = Modifier.size(24.dp),
                )
                IconButton(onClick = { PlayerConnection.cycleRepeatMode() }) {
                    val repeatIcon = when (playerState.repeatMode) {
                        PlayerState.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }
                    val repeatTint = if (playerState.repeatMode != PlayerState.REPEAT_MODE_OFF) vkAccent else vkTextSecondary
                    Icon(repeatIcon, "Повтор", tint = repeatTint, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) cardColor.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Обложка / иконка play
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.hsl((abs(track.artist.hashCode()) % 360).toFloat(), 0.55f, 0.45f),
                            Color.hsl(((abs(track.artist.hashCode() * 31)) % 360).toFloat(), 0.45f, 0.55f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val albumThumb = track.albumThumb
            if (!albumThumb.isNullOrBlank()) {
                AsyncImage(
                    model = albumThumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) accentColor else textColor,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                color = secondaryColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { /* TODO: меню трека */ }) {
            Icon(Icons.Filled.MoreVert, "Ещё", tint = secondaryColor, modifier = Modifier.size(20.dp))
        }
    }
}
