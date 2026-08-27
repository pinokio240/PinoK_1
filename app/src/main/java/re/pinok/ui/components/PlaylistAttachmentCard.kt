package re.pinok.ui.components

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
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog

/**
 * #30 (playlists): карточка плейлиста как вложения поста.
 *
 * VK API возвращает `audio_playlist` вложения с полями id, owner_id, title,
 * count, photo_*, access_key. Раньше они не парсились и не отображались.
 *
 * UI: обложка 56×56 + название + кол-во треков + play-иконка.
 * Тап: загружает треки плейлиста через audio.get?album_id=... и запускает
 * воспроизведение через PlayerConnection.playTrackList.
 */
@Composable
fun PlaylistAttachmentCard(playlist: AudioPlaylist) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var loadedTracks by remember { mutableStateOf<List<Track>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                if (loading) return@clickable
                if (loadedTracks != null) {
                    // Уже загружено — воспроизводим
                    loadedTracks?.let { PlayerConnection.playTrackList(it, startIndex = 0) }
                    return@clickable
                }
                scope.launch {
                    loading = true
                    error = null
                    try {
                        val app = SovaApp.get()
                        val (count, tracks) = app.apiClient.audioGetPlaylistTracks(
                            playlistId = playlist.id,
                            ownerId = playlist.ownerId,
                            accessKey = playlist.accessKey,
                            count = 100,
                        )
                        if (tracks.isEmpty()) {
                            error = "Плейлист пуст или недоступен"
                            AppLog.w("PlaylistAttachment", "playlist ${playlist.id}: no tracks")
                        } else {
                            loadedTracks = tracks
                            PlayerConnection.playTrackList(tracks, startIndex = 0)
                            AppLog.i("PlaylistAttachment", "playlist ${playlist.id}: ${tracks.size} tracks loaded")
                        }
                    } catch (e: Exception) {
                        error = "Ошибка: ${e.message}"
                        AppLog.e("PlaylistAttachment", "load failed", e)
                    } finally {
                        loading = false
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Обложка 56×56
        val cover = playlist.coverUrl
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.title,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Название + кол-во треков
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            // Локальный захват var-делегатов, чтобы smart-cast работал
            // в when-ветках (без !! и ?:).
            val err = error
            val tracks = loadedTracks
            Text(
                text = when {
                    err != null -> err
                    tracks != null -> "${tracks.size} треков"
                    playlist.count > 0 -> "Плейлист • ${playlist.count} треков"
                    else -> "Плейлист"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (err != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Play-иконка / индикатор загрузки
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LibraryMusic,
                contentDescription = "Воспроизвести плейлист",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
