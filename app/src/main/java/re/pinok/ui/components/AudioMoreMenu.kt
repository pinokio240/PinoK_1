// File: ui/components/AudioMoreMenu.kt
package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.DownloadState
import re.pinok.data.model.Track
import re.pinok.util.AppLog
import re.pinok.util.toDurationString

/**
 * Fix #84: AudioMoreMenu — контекстное меню трека (8 пунктов).
 * Аналог React-компонента AudioMoreMenu из m.vk.com/audio.
 *
 * Действия (i18n-ключи из аудио-архива):
 * - audio_add_to_audio / audio_delete_audio / audio_restore_audio
 * - audio_share_audio / audio_copy_audio_link
 * - audio_edit_track (только для своих)
 * - audio_show_lyrics (если есть lyrics_id)
 * - audio_show_recommendations
 * - audio_open_album (если есть album_id)
 * - audio_set_next_audio
 * - audio_action_dislike
 *
 * Подписки/реклама исключены (требование пользователя).
 */
@Composable
fun AudioMoreMenu(
    track: Track,
    expanded: Boolean,
    onDismiss: () -> Unit,
    isOwn: Boolean = false,
    onAdd: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRestore: () -> Unit = {},
    onShare: () -> Unit = {},
    // #FAVE-AUDIO (2026-08-03): "В закладки" — fave.add(type="audio").
    onBookmark: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onEdit: () -> Unit = {},
    onShowLyrics: () -> Unit = {},
    onShowRecommendations: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onSetNext: () -> Unit = {},
    onDislike: () -> Unit = {},
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (isOwn) {
            DropdownMenuItem(text = { Text("Редактировать трек") }, onClick = { onEdit(); onDismiss() })
            DropdownMenuItem(text = { Text("Удалить аудиозапись") }, onClick = { onDelete(); onDismiss() })
        } else {
            DropdownMenuItem(text = { Text("Добавить в мою музыку") }, onClick = { onAdd(); onDismiss() })
            DropdownMenuItem(text = { Text("Воспроизвести следующей") }, onClick = { onSetNext(); onDismiss() })
        }
        if (track.hasLyrics) {
            DropdownMenuItem(text = { Text("Показать текст") }, onClick = { onShowLyrics(); onDismiss() })
        }
        DropdownMenuItem(text = { Text("Показать похожие") }, onClick = { onShowRecommendations(); onDismiss() })
        if (track.albumId != null && track.albumId != 0L) {
            DropdownMenuItem(text = { Text("Открыть альбом") }, onClick = { onOpenAlbum(); onDismiss() })
        }
        DropdownMenuItem(text = { Text("Не нравится") }, onClick = { onDislike(); onDismiss() })
        // #FAVE-AUDIO: добавить в закладки (fave.add type="audio").
        DropdownMenuItem(text = { Text("В закладки") }, onClick = { onBookmark(); onDismiss() })
        DropdownMenuItem(text = { Text("Поделиться") }, onClick = { onShare(); onDismiss() })
        DropdownMenuItem(text = { Text("Скопировать ссылку") }, onClick = { onCopyLink(); onDismiss() })
        if (isOwn) {
            DropdownMenuItem(text = { Text("Восстановить") }, onClick = { onRestore(); onDismiss() })
        }
    }
}

/**
 * Fix #84: LyricsSheet — экран лирики песни с karaoke-mode.
 * Текст тапабелен — тап по строке перематывает к этому моменту (если есть timings).
 * Аналог компонента audio_lyrics_seek_to_karaoke_line_aria_label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    lyricsId: Long,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var lyrics by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lyricsId) {
        scope.launch {
            loading = true
            error = null
            try {
                val text = app.apiClient.audioGetLyrics(lyricsId)
                lyrics = text
                if (text.isNullOrBlank()) error = "Текст песни недоступен"
            } catch (e: Exception) {
                error = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Текст песни",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            // Локальный захват, чтобы smart-cast работал в when-ветках
            // (var-делегат mutableStateOf не smart-cast'ится напрямую).
            val err = error
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3D8BFF))
                    }
                }
                err != null -> {
                    Text(err, color = Color(0xFFA8A8AA), fontSize = 14.sp)
                }
                else -> {
                    val lines = lyrics.orEmpty().split("\n")
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line.ifBlank { "♪" },
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { /* karaoke seek — TODO: timings */ },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

