package re.pinok.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import re.pinok.util.AppLog
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
 *
 * #SAVE-PLAYLIST-FROM-POST: кнопка «Сохранить как плейлист» в шапке блока.
 * Создаёт новый плейлист (audio.createPlaylist) и добавляет ВСЕ треки из поста
 * одним запросом (audio.addToPlaylist). Жалоба: «не сохраняется плейлист.
 * в ленте был пост с треками» — раньше можно было сохранить только ОДИН трек
 * через AudioPlayerScreen → «Добавить в плейлист» (currentTrack only), а не
 * весь список. Теперь — одной кнопкой.
 */
@Composable
fun AudioAttachmentList(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    // #AUDIO-COMMENTS (волна 37): резолв URL трека без url (audioGetById + al_audio).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // #SAVE-PLAYLIST-FROM-POST: state для диалога создания плейлиста.
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // #SAVE-PLAYLIST-FROM-POST: шапка блока с кнопкой «Сохранить как плейлист».
        // Показываем всегда (даже для 1 трека) — пользователь может захотеть
        // сохранить и один трек как плейлист.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable { showSavePlaylistDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "В плейлист",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

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

    // #SAVE-PLAYLIST-FROM-POST: диалог создания плейлиста из треков поста.
    if (showSavePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) showSavePlaylistDialog = false
            },
            title = { Text("Сохранить как плейлист") },
            text = {
                Column {
                    Text(
                        "Будет создан новый плейлист с ${tracks.size} " +
                            pluralTracks(tracks.size) + " из этого поста.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        placeholder = { Text("Название плейлиста") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (saving) {
                        Spacer(Modifier.size(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Сохранение…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = playlistTitle.trim().ifEmpty { "Треки из поста" }
                        if (saving) return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                val app = SovaApp.get()
                                val myId = app.exchangeAuthRepository.userId()
                                if (myId <= 0L) {
                                    Toast.makeText(
                                        context,
                                        "Не удалось определить аккаунт",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                                // audioIds формат: "ownerId_audioId" (VK convention).
                                // Фильтруем треки без id (некоторые вложения могут
                                // быть невалидными —VK иногда шлёт мусор).
                                val audioIds = tracks
                                    .filter { it.id > 0L && it.ownerId != 0L }
                                    .map { "${it.ownerId}_${it.id}" }
                                if (audioIds.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Нет валидных треков для сохранения",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                                // #CREATE-WITH-TRACKS (HAR #53): audio.createPlaylist принимает
                                // audio_ids CSV — создаёт плейлист И добавляет треки ОДНИМ
                                // запросом (как VK веб: audioCreatePlaylist({audio_ids:d.Audios})).
                                // Раньше было 2 запроса (create + addToPlaylist). Теперь 1.
                                val newPlaylistId = app.apiClient.audioCreatePlaylist(
                                    ownerId = myId,
                                    title = title,
                                    description = "",
                                    audioIds = audioIds,
                                )
                                if (newPlaylistId > 0L) {
                                    Toast.makeText(
                                        context,
                                        "Сохранено: «$title» — ${audioIds.size} треков",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    val err = app.apiClient.lastApiError
                                    Toast.makeText(
                                        context,
                                        "Не удалось создать: ${err ?: "ошибка"}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e("AudioAttachmentList", "savePlaylistFromPost failed", e)
                                Toast.makeText(
                                    context,
                                    "Ошибка: ${e.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                saving = false
                                showSavePlaylistDialog = false
                                playlistTitle = ""
                            }
                        }
                    },
                    enabled = !saving,
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!saving) {
                            showSavePlaylistDialog = false
                            playlistTitle = ""
                        }
                    },
                    enabled = !saving,
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

/** Вспомогательное: правильное склонение слова «трек» для диалога. */
private fun pluralTracks(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "треком"
    n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20) -> "треками"
    else -> "треками"
}
