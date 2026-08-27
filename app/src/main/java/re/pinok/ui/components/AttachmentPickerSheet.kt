package re.pinok.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.GiftItem
import re.pinok.data.model.Track
import re.pinok.data.model.Video
import re.pinok.util.AppLog
import re.pinok.util.toDurationString

/**
 * P5.3: Bottom sheet для выбора вложений из библиотеки VK — Музыка / Видео / Подарки.
 *
 * Заменяет простое меню «Фото/Файл» на расширенное, как в m.vk.ru:
 * пользователь выбирает существующий контент из своей библиотеки VK и
 * отправляет его как attachment в диалог.
 *
 * @param initialTab 0=Музыка, 1=Видео, 2=Подарки
 * @param onPickAudio callback при выборе трека
 * @param onPickVideo callback при выборе видео
 * @param onPickGift  callback при выборе подарка
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    onPickAudio: (Track) -> Unit = {},
    onPickVideo: (Video) -> Unit = {},
    onPickGift: (GiftItem) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = SovaApp.get()
    var activeTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.85f)
        ) {
            Text(
                text = "Прикрепить",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            PrimaryTabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Музыка") },
                    icon = { Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Видео") },
                    icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Подарки") },
                    icon = { Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }

            when (activeTab) {
                0 -> AudioPickerTab(app = app, onPick = { onPickAudio(it); onDismiss() })
                1 -> VideoPickerTab(app = app, onPick = { onPickVideo(it); onDismiss() })
                2 -> GiftPickerTab(app = app, onPick = { onPickGift(it); onDismiss() })
            }
        }
    }
}

// ============================================================================
//  Tab: Музыка — список треков из audio.get библиотеки пользователя.
// ============================================================================

@Composable
private fun AudioPickerTab(app: SovaApp, onPick: (Track) -> Unit) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            tracks = app.apiClient.audioGet(count = 50)
        } catch (e: Exception) {
            AppLog.e("AudioPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Библиотека пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
                AudioTrackRow(track = track, onClick = { onPick(track) })
            }
        }
    }
}

@Composable
private fun AudioTrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail (album cover) или иконка-заглушка
        if (track.albumThumb != null) {
            AsyncImage(
                model = track.albumThumb,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = track.duration.toDurationString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ============================================================================
//  Tab: Видео — список видео из video.get библиотеки пользователя.
// ============================================================================

@Composable
private fun VideoPickerTab(app: SovaApp, onPick: (Video) -> Unit) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            videos = app.apiClient.videoGet(count = 50)
        } catch (e: Exception) {
            AppLog.e("VideoPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(videos, key = { "${it.ownerId}_${it.id}" }) { video ->
                VideoRow(video = video, onClick = { onPick(video) })
            }
        }
    }
}

@Composable
private fun VideoRow(video: Video, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(96.dp, 54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (video.thumbUrl != null) {
                AsyncImage(
                    model = video.thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Icon(Icons.Filled.PlayCircle, contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title.ifBlank { "Видео" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${video.duration.toDurationString()} • ${video.views} просм.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================================================================
//  Tab: Подарки — сетка подарков из gifts.getCatalog.
// ============================================================================

@Composable
private fun GiftPickerTab(app: SovaApp, onPick: (GiftItem) -> Unit) {
    var gifts by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            gifts = app.apiClient.giftsGetCatalog()
        } catch (e: Exception) {
            AppLog.e("GiftPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        gifts.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет доступных подарков", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(gifts, key = { it.id }) { gift ->
                GiftCell(gift = gift, onClick = { onPick(gift) })
            }
        }
    }
}

@Composable
private fun GiftCell(gift: GiftItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (gift.thumbUrl != null) {
            AsyncImage(
                model = gift.thumbUrl,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = gift.priceText,
            style = MaterialTheme.typography.labelSmall,
            color = if (gift.isFree) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
