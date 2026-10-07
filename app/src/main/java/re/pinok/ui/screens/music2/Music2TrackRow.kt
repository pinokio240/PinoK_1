// File: music2/Music2TrackRow.kt
// P0.32 #MUSIC2 (2026-10): Переиспользуемая строка трека для Music2.
//
// Новые фичи по сравнению со старым MusicScreen:
// - Частотный визуализатор (MusicTrackCell_FrequencyBars) — анимированные полоски
//   при проигрывании трека, как в VK web.
// - Mini progress bar в строке (MusicTrackRow_PlayerProgressBar) — тонкая полоса
//   прогресса внизу строки трека, как в VK web.
// - HQ badge (vmsaudioRow__bitrate) — отображение качества аудио.
// - Контекстное меню (MusicAudio_MenuButton) — dropdown с:
//   Текст песни (MusicAudio_OpenLyrics) / Добавить/Убрать (MusicAudio_ToggleOwning)
//   / Поделиться (MusicAudio_Share) / Редактировать (MusicAudio_OpenEditing).
//
// Переиспользует PlayerConnection для состояния плеера.

package re.pinok.ui.screens.music2

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Track

// P0.32: VK accent color для музыкального раздела.
private val VkAccent = Color(0xFF0077FF)

@Composable
fun Music2TrackRow(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = SovaApp.get()

    // P0.33 #TRACK-MENU: состояние dropdown меню.
    var menuExpanded by remember { mutableStateOf(false) }
    // P0.33: состояние own (трек в моей музыке?). Упрощённо: ownerId == myId → own.
    val myUserId = remember { app.exchangeAuthRepository.userId() }
    val isOwn = track.ownerId == myUserId
    // P0.33: состояние lyrics loading.
    var lyricsLoading by remember(track.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            // P0.32: Play/Pause иконка ИЛИ частотный визуализатор.
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
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

            // P0.32: информация о треке — title + artist + HQ badge + duration.
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                    // P0.32: HQ badge.
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

            // P0.33 #TRACK-MENU: контекстное меню (3 точки) с dropdown.
            // VK web: MusicAudio_MenuButton → dropdown с:
            //   MusicAudio_OpenLyrics (Текст песни)
            //   MusicAudio_ToggleOwning (Добавить/Убрать из моей музыки)
            //   MusicAudio_Share (Поделиться)
            //   MusicAudio_OpenEditing (Редактировать)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Меню",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    // P0.33: Текст песни (MusicAudio_OpenLyrics).
                    // Показываем только если у трека есть lyricsId.
                    if (track.hasLyrics) {
                        DropdownMenuItem(
                            text = { Text("Текст песни") },
                            leadingIcon = { Icon(Icons.Outlined.Lyrics, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                menuExpanded = false
                                lyricsLoading = true
                                scope.launch {
                                    try {
                                        val lyrics = app.apiClient.audioGetLyrics(track.lyricsId!!)
                                        if (lyrics != null) {
                                            // P0.33: показываем lyrics в Toast (временно —
                                            // TODO: отдельный LyricsSheet как в старом MusicScreen).
                                            val lines = lyrics.lines()
                                            val preview = lines.take(5).joinToString("\n")
                                            val suffix = if (lines.size > 5) "\n…" else ""
                                            Toast.makeText(context, preview + suffix, Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Текст недоступен", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        lyricsLoading = false
                                    }
                                }
                            },
                        )
                    }

                    // P0.33: Добавить/Убрать из моей музыки (MusicAudio_ToggleOwning).
                    DropdownMenuItem(
                        text = { Text(if (isOwn) "Убрать из моей музыки" else "Добавить в мою музыку") },
                        leadingIcon = {
                            Icon(
                                if (isOwn) Icons.Outlined.MusicOff else Icons.Outlined.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            scope.launch {
                                try {
                                    val ok = if (isOwn) {
                                        app.apiClient.audioDelete(track.id, track.ownerId)
                                    } else {
                                        app.apiClient.audioAdd(track.id, track.ownerId)
                                    }
                                    Toast.makeText(
                                        context,
                                        if (ok) (if (isOwn) "Удалено" else "Добавлено") else "Ошибка",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )

                    // P0.33: Поделиться (MusicAudio_Share).
                    DropdownMenuItem(
                        text = { Text("Поделиться") },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            val shareText = "${track.artist} — ${track.title}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться треком"))
                        },
                    )
                }
            }
        }

        // P0.32: Mini progress bar в строке трека.
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
