// File: ui/screens/offline/OfflineAudioPlayerScreen.kt
package re.pinok.ui.screens.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.data.model.PlayerState
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.util.AppLog
import re.pinok.util.toDurationString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OfflineAudioPlayer"

/**
 * Fix #50: Собственный аудиоплеер для офлайн режима.
 *
 * Отделяется от AudioPlayerScreen (online), потому что:
 * - НЕ делает сетевых запросов (обложки, лайки, текст песни)
 * - Показывает только скачанные треки из TrackDownloadManager
 * - Минималистичный UI: только прогресс, controls, очередь
 * - Авто-переход к следующему скачанному треку (уже работает через PlayerConnection)
 *
 * Источник данных: [TrackDownloadManager.downloads] StateFlow.
 * Воспроизведение: через существующий [PlayerConnection] singleton — без дублирования
 * логики плеера. Авто-продвижение и shuffle/repeat делегируются ExoPlayer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAudioPlayerScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    AppLog.i(TAG, "screen opened")

    // 1. Collect downloaded tracks и player state
    val downloads by TrackDownloadManager.downloads.collectAsState()
    val playerState by PlayerConnection.playerState.collectAsState()

    // 2. Build track list (only COMPLETED downloads) — newest first (по trackId desc).
    val offlineTracks = remember(downloads) {
        downloads.values
            .filter { it.isCompleted }
            .sortedByDescending { it.trackId }
            .map { ds ->
                Track(
                    id = ds.trackId,
                    ownerId = ds.ownerId,
                    artist = ds.artist.ifBlank { "Неизвестный исполнитель" },
                    title = ds.title.ifBlank { "Трек #${ds.trackId}" },
                    duration = 0,
                    url = null, // URL не нужен — PlayerConnection сам возьмёт локальный файл
                )
            }
    }

    // 3. Current track из playerState
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.isPlaying

    // #50: логируем авто-переход к новому треку (вкл. auto-advance ExoPlayer'а).
    LaunchedEffect(currentTrack?.id) {
        val t = currentTrack ?: return@LaunchedEffect
        AppLog.i(TAG, "track play: ${t.artist} - ${t.title} (id=${t.id})")
    }

    // 4. UI: Scaffold + TopBar + Column
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Офлайн плеер (${offlineTracks.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Current track card — занимает верхнюю часть экрана.
            CurrentTrackCard(
                track = currentTrack,
                playerState = playerState,
                onPlayPause = { PlayerConnection.togglePlayPause() },
                onPrev = { PlayerConnection.prev() },
                onNext = { PlayerConnection.next() },
                onSeek = { PlayerConnection.seekTo(it) },
            )

            HorizontalDivider()

            // Queue header — shuffle + repeat toggles
            QueueHeaderRow(
                count = offlineTracks.size,
                shuffleEnabled = playerState.shuffleModeEnabled,
                repeatMode = playerState.repeatMode,
                onToggleShuffle = { PlayerConnection.setShuffleModeEnabled(!playerState.shuffleModeEnabled) },
                onCycleRepeat = { PlayerConnection.cycleRepeatMode() },
            )

            HorizontalDivider()

            // Queue list (остаток экрана, scrollable)
            if (offlineTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Нет скачанных трекей",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Скачайте треки в офлайн-менеджере",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(offlineTracks, key = { it.id }) { track ->
                        val isCurrent = currentTrack?.id == track.id
                        OfflineTrackRow(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onClick = {
                                val index = offlineTracks.indexOf(track)
                                if (index >= 0) {
                                    AppLog.i(TAG, "play offline: ${track.artist} - ${track.title} (index=$index)")
                                    PlayerConnection.playTrackList(offlineTracks, index)
                                }
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка текущего трека — крупная иконка MusicNote (без album art),
 * title/artist, метаданные файла (размер/формат/дата), seek bar, controls.
 *
 * В офлайн-режиме нет обложки альбома — вместо неё круглый плейсхолдер
 * с цветной заливкой из primaryContainer (как в MusicScreen empty state).
 */
@Composable
private fun CurrentTrackCard(
    track: Track?,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (track == null) {
            // Empty state — нет воспроизводимого трека.
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Выберите трек из очереди",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        // Крупная иконка трека (без album art в офлайн режиме)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Title + artist
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Метаданные файла: размер + формат + дата скачивания
        val fileInfo = remember(track.id) { formatFileInfo(track.id) }
        if (fileInfo != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = fileInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Seek bar — drag with deferred seek (как в AudioPlayerScreen)
        val effectiveDuration = if (playerState.durationMs > 0) {
            playerState.durationMs
        } else {
            (track.duration.toLong()) * 1000L
        }
        var sliderDragging by remember { mutableStateOf(false) }
        var sliderPos by remember { mutableStateOf(playerState.positionMs.toFloat()) }

        LaunchedEffect(playerState.positionMs) {
            if (!sliderDragging) sliderPos = playerState.positionMs.toFloat()
        }

        val displayValue = if (sliderDragging) sliderPos else playerState.positionMs.toFloat()
        val rangeEnd = effectiveDuration.coerceAtLeast(1L).toFloat()
        Slider(
            value = displayValue.coerceIn(0f, rangeEnd),
            onValueChange = {
                sliderDragging = true
                sliderPos = it
            },
            onValueChangeFinished = {
                sliderDragging = false
                AppLog.d(TAG, "seek to ${sliderPos.toLong()}ms")
                onSeek(sliderPos.toLong())
            },
            valueRange = 0f..rangeEnd,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = playerState.positionMs.toDurationString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val remaining = (effectiveDuration - playerState.positionMs).coerceAtLeast(0L)
            Text(
                text = "-" + remaining.toDurationString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Controls: prev / play-pause / next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Назад",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Вперёд",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Заголовок очереди с тогглами shuffle/repeat.
 * - Shuffle: ON/OFF (фиксированный tint primary когда включён).
 * - Repeat: цикл OFF → ALL → ONE → OFF (3-state с разными иконками).
 */
@Composable
private fun QueueHeaderRow(
    count: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Очередь ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Перемешать",
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val repeatIcon = when (repeatMode) {
                PlayerState.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                else -> Icons.Filled.Repeat
            }
            val repeatTint = if (repeatMode != PlayerState.REPEAT_MODE_OFF) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Повтор",
                    tint = repeatTint,
                )
            }
        }
    }
}

/**
 * Элемент очереди. Подсвечивает текущий трек (primaryContainer с alpha),
 * показывает иконку VolumeUp если сейчас играет.
 */
@Composable
private fun OfflineTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Text(
                text = if (isPlaying) "▶" else "II",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
            )
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────


/** Форматирует байты → "1.2 МБ" / "512 КБ" / "0 Б". */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes ${units[0]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

/**
 * Возвращает строку метаданных о файле трека: "1.2 МБ • TS • 2025-01-15"
 * или null если локальный файл не найден.
 */
private fun formatFileInfo(trackId: Long): String? {
    val file: File = TrackDownloadManager.getLocalFile(trackId) ?: return null
    val sizeStr = formatBytes(file.length())
    val ext = file.extension.uppercase(Locale.ROOT).ifBlank { "?" }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    val dateStr = dateFmt.format(Date(file.lastModified()))
    return "$sizeStr • $ext • $dateStr"
}
