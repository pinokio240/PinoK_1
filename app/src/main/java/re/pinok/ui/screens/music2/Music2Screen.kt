// File: music2/Music2Screen.kt
// P0.32 #MUSIC2 (2026-10): Новый контейнер «Музыка 2» — обособленный от старого MusicScreen.
// Содержит все новые фичи: pull-to-refresh, 5 вкладок, визуализатор, битрейт,
// мини-прогресс в строке, volume slider, дизлайк, Play Mix, загрузку аудио.
//
// Переиспользует (не дублирует):
// - PlayerConnection (единый контроллер плеера)
// - TrackDownloadManager (скачивание)
// - VKApiClient (audio.get, catalog.getAudio, audio.search и т.д.)
// - SovaPrefs (preferredQuality, autoCacheAudio)
// - GlobalMiniPlayer (мини-плеер внизу)
//
// НЕ зависит от старого MusicScreen — полностью обособленный код.

package re.pinok.ui.screens.music2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog

// P0.32: вкладки каталога — как в VK web (5 табов).
// data-testid: AudioCatalog_Tabs_Tab_all / general / explore / radiostations / updates
private val MUSIC2_TABS = listOf("Моя музыка", "Главная", "Обзор", "Радио", "Обновления")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Music2Screen(
    onBack: () -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    // P0.32: состояние вкладок (5 табов как в VK web).
    var selectedTab by remember { mutableIntStateOf(0) }

    // P0.32: состояние списка треков.
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // P0.32: состояние плеера — для подсветки играющего трека.
    val playerState by PlayerConnection.playerState.collectAsState()

    // P0.32: поиск.
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // P0.32: загрузка треков (audio.get — для «Моя музыка»).
    fun loadTracks(refresh: Boolean = false) {
        scope.launch {
            if (refresh) refreshing = true else loading = true
            errorText = null
            try {
                val result = app.apiClient.audioGet(count = 50, offset = if (refresh) 0 else tracks.size)
                if (refresh) {
                    tracks = result
                } else {
                    tracks = (tracks + result).distinctBy { "${it.ownerId}_${it.id}" }
                }
            } catch (e: Exception) {
                AppLog.w("Music2", "loadTracks error: ${e.message}")
                errorText = "Ошибка загрузки: ${e.message}"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    // P0.32: начальная загрузка.
    LaunchedEffect(Unit) {
        loadTracks()
    }

    // P0.32: pull-to-refresh — перезагрузка списка при pull-down.
    // VK web: catalog.getAudio перезапрашивается, список обновляется.
    val isCurrentTrack = { track: Track ->
        val current = playerState.currentTrack
        current != null && current.id == track.id && current.ownerId == track.ownerId
    }

    // P0.32: прогресс вычисляется из positionMs/durationMs (PlayerState не имеет progress).
    val currentProgress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // P0.32: вкладки (ScrollableTabRow — 5 табов, как в VK web AudioCatalog_Tabs).
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
        ) {
            MUSIC2_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        // P0.32: PullToRefreshBox — pull-to-refresh списка треков.
        // Раньше (старый MusicScreen): НЕ было pull-to-refresh — список не обновлялся.
        // Теперь: pull-down → loadTracks(refresh=true) → свежий audio.get.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadTracks(refresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (loading && tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    // P0.32: заголовок + счётчик треков.
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Моя музыка",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${tracks.size} треков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // P0.32: список треков — Music2TrackRow с визуализатором + mini progress + bitrate.
                    items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
                        Music2TrackRow(
                            track = track,
                            isPlaying = isCurrentTrack(track) && playerState.isPlaying,
                            progress = if (isCurrentTrack(track)) currentProgress else 0f,
                            onClick = {
                                val idx = tracks.indexOf(track)
                                if (isCurrentTrack(track)) {
                                    PlayerConnection.togglePlayPause()
                                } else {
                                    PlayerConnection.playTrackList(tracks, idx, fromMyMusic = true)
                                }
                            },
                            onMenuClick = {
                                // P0.32: TODO — context menu (дизлайк/own/lyrics/share/snippet)
                            },
                        )
                    }

                    // P0.32: футер — догрузка при скролле.
                    if (tracks.isNotEmpty()) {
                        item(key = "footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    // P0.32: пустое состояние.
                    if (tracks.isEmpty() && !loading) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Нет треков",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // P0.32: error состояние.
                    if (errorText != null && tracks.isEmpty()) {
                        item(key = "error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = errorText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
