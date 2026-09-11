package re.pinok.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.util.toDurationString

/**
 * #30 (audio attachments): список аудио-вложений в посте.
 *
 * Используется в FeedScreen, ProfileScreen, UserProfileScreen, CommunityScreen,
 * а с #AUDIO-COMMENTS (волна 37) — и в CommentAttachmentsView (аудио-вложения
 * комментариев; раньше там была статичная строка без click-обработчика).
 * Раньше audio-вложения рендерились только в FeedScreen и PostDetailScreen —
 * на стене профиля и сообщества они отсутствовали.
 *
 * Тап по треку: если уже играет — пауза, иначе — воспроизвести весь список
 * через PlayerConnection.playTrackList с startIndex.
 *
 * #AUDIO-COMMENTS (волна 37): трек без URL больше не тупик. VK в аудио-вложениях
 * (особенно в комментариях wall.getComments) часто НЕ отдаёт url — такой трек
 * раньше отсекался фильтром playTrackList («Нет воспроизводимых треков») или
 * менял очередь на соседний трек. Теперь перед запуском очередь тапнутый трек
 * без URL резолвится через VKApiClient.audioGetById (audio.getById с quality=hq
 * + extractAudioUrl #AUDIO-UNMASK + al_audio.php web-fallback §42.12 — тот же
 * путь, что у загрузок #DL-DISPATCH-RESTORE); при неудаче — честный Toast.
 */
@Composable
fun AudioAttachmentList(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    // #AUDIO-COMMENTS (волна 37): резолв URL трека без url (audioGetById + al_audio).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val playerState = PlayerConnection.playerState.collectAsState().value
        tracks.forEachIndexed { index, track ->
            val isCurrent = playerState.currentTrack?.id == track.id
            val isCurrentPlaying = isCurrent && playerState.isPlaying
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrentPlaying)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .clickable {
                        if (isCurrentPlaying) {
                            PlayerConnection.togglePlayPause()
                        } else {
                            // #AUDIO-COMMENTS (волна 37): тапнутый трек без URL
                            // сначала резолвится через audioGetById (API + al_audio
                            // web-fallback) — тот же путь, что у загрузок
                            // (#DL-DISPATCH-RESTORE). Иначе playTrackList либо
                            // отфильтровал бы трек, либо стартовал соседний.
                            // #ARCH-CONTAINERS 3.7-1: Track.url в :core:data —
                            // захват в val до проверки (смарт-каст чужого модуля).
                            val tapped = tracks[index]
                            val tappedUrl = tapped.url
                            if (!tappedUrl.isNullOrBlank()) {
                                PlayerConnection.playTrackList(tracks, startIndex = index)
                            } else {
                                scope.launch {
                                    // Dispatchers.IO: audioGetById — сетевой I/O
                                    // (внутри OkHttp); runCatching — любой сбой
                                    // (офлайн/капча/удалённый трек) = честный Toast.
                                    val resolved = withContext(Dispatchers.IO) {
                                        runCatching {
                                            SovaApp.get().apiClient.audioGetById(tapped)
                                        }.getOrNull()
                                    }
                                    // NULL-ЯВНО: audioGetById опционален по контракту.
                                    val resolvedUrl = resolved?.url
                                    if (resolved != null && !resolvedUrl.isNullOrBlank()) {
                                        // NULL-ЯВНО: треки без URL в хвосте очереди
                                        // отфильтрует playTrackList (Fix #59) —
                                        // startIndex ремапится по id.
                                        PlayerConnection.playTrackList(
                                            tracks.mapIndexed { i, t -> if (i == index) resolved else t },
                                            startIndex = index,
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Не удалось получить ссылку на трек — нет доступа к аудио",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isCurrentPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = track.duration.toDurationString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

