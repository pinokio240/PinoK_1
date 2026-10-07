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

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
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

    // P0.36 #MUSIC2-UPLOAD: состояние загрузки аудио файла.
    var uploading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // P0.36: загрузка файла на upload URL (multipart POST, как VK web HAR).
    suspend fun uploadAudioFile(uploadUrl: String, file: java.io.File): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("file", file.name,
                        file.asRequestBody("audio/mpeg".toMediaType()))
                    .build()
                val req = okhttp3.Request.Builder()
                    .url(uploadUrl)
                    .header("Origin", re.pinok.api.VKEndpoints.WEB_ORIGIN)
                    .header("Referer", re.pinok.api.VKEndpoints.WEB_REFERER)
                    .header("User-Agent", re.pinok.util.VkUserAgent.get(app))
                    .post(requestBody)
                    .build()
                app.httpClient.newCall(req).execute().use { resp ->
                    resp.body?.string()
                }
            } catch (e: Exception) {
                AppLog.e("Music2", "uploadAudioFile error: ${e.message}", e)
                null
            }
        }
    }

    // P0.36: file picker — объявлен ПОСЛЕ loadTracks (нужна ссылка на loadTracks).
    // Объявляется позже (после loadTracks). См. ниже.

    // P0.32: состояние вкладок (5 табов как в VK web).
    var selectedTab by remember { mutableIntStateOf(0) }

    // P0.32: состояние списка треков (для вкладки «Моя музыка»).
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // P0.34 #MUSIC2-CATALOG: состояние каталога (для вкладок Главная/Обзор/Радио/Обновления).
    var catalogBlocks by remember { mutableStateOf<List<re.pinok.data.model.CatalogBlock>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    // P0.32: состояние плеера — для подсветки играющего трека.
    val playerState by PlayerConnection.playerState.collectAsState()

    // P0.32: поиск.
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // P0.32: маппинг вкладок → API sections.
    // 0=Моя музыка (audio.get), 1=Главная (general), 2=Обзор (explore),
    // 3=Радио (radio), 4=Обновления (updates).
    val CATALOG_SECTIONS = listOf("my", "general", "explore", "radio", "updates")

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

    // P0.36: file picker — объявлен ПОСЛЕ loadTracks (нужна ссылка на loadTracks).
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val tempFile = java.io.File(context.cacheDir, "upload_audio_${System.currentTimeMillis()}.mp3")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val uploadUrl = app.apiClient.audioGetUploadServer()
                if (uploadUrl.isNullOrBlank()) {
                    Toast.makeText(context, "Не удалось получить URL загрузки", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val uploadResponse = uploadAudioFile(uploadUrl, tempFile)
                if (uploadResponse.isNullOrBlank()) {
                    Toast.makeText(context, "Загрузка файла не удалась", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val savedTrack = app.apiClient.audioSave(uploadResponse)
                if (savedTrack != null) {
                    Toast.makeText(context, "Загружено: ${savedTrack.artist} — ${savedTrack.title}", Toast.LENGTH_LONG).show()
                    loadTracks(refresh = true)
                } else {
                    Toast.makeText(context, "Не удалось сохранить трек", Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            } catch (e: Exception) {
                AppLog.e("Music2", "upload error: ${e.message}", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                uploading = false
            }
        }
    }

    // P0.34: загрузка каталога (catalog.getSection — для вкладок 1-4).
    fun loadCatalog(section: String, refresh: Boolean = false) {
        scope.launch {
            if (refresh) refreshing = true else catalogLoading = true
            catalogError = null
            try {
                val blocks = app.apiClient.catalogGetAudio(section = section, count = 10)
                catalogBlocks = blocks
                AppLog.i("Music2", "loadCatalog($section): ${blocks.size} blocks")
            } catch (e: Exception) {
                AppLog.w("Music2", "loadCatalog error: ${e.message}")
                catalogError = "Ошибка загрузки каталога: ${e.message}"
            } finally {
                catalogLoading = false
                refreshing = false
            }
        }
    }

    // P0.34: загрузка при смене вкладки.
    // P0.34: флаг refresh для каталога при возврате на вкладку.
    var refreshNeeded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            // «Моя музыка» — audio.get.
            if (tracks.isEmpty()) loadTracks()
        } else {
            // Каталог — catalog.getSection.
            val section = CATALOG_SECTIONS[selectedTab]
            if (catalogBlocks.isEmpty() || refreshNeeded) loadCatalog(section, refresh = false)
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

        // P0.32+P0.34: PullToRefreshBox — pull-to-refresh.
        // Вкладка 0 (Моя музыка): обновляет audio.get.
        // Вкладки 1-4 (Каталог): обновляет catalog.getSection.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (selectedTab == 0) {
                    loadTracks(refresh = true)
                } else {
                    val section = CATALOG_SECTIONS[selectedTab]
                    loadCatalog(section, refresh = true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selectedTab == 0) {
                Music2MyTracksContent(
                    tracks = tracks,
                    loading = loading,
                    errorText = errorText,
                    listState = listState,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = playerState.isPlaying,
                    currentProgress = currentProgress,
                    volume = playerState.volume,
                    uploading = uploading,
                    onUploadClick = { filePicker.launch("audio/*") },
                )
            } else {
                Music2CatalogContent(
                    catalogBlocks = catalogBlocks,
                    catalogLoading = catalogLoading,
                    catalogError = catalogError,
                    listState = listState,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = playerState.isPlaying,
                    currentProgress = currentProgress,
                )
            }
        }
    }
}

// P0.34: контент вкладки «Моя музыка» — список треков.
@Composable
private fun Music2MyTracksContent(
    tracks: List<Track>,
    loading: Boolean,
    errorText: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isCurrentTrack: (Track) -> Boolean,
    isPlaying: Boolean,
    currentProgress: Float,
    volume: Float = 1.0f,
    uploading: Boolean = false,
    onUploadClick: () -> Unit = {},
) {
    if (loading && tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Моя музыка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    // P0.36 #MUSIC2-UPLOAD: кнопка загрузки аудио.
                    // VK web: UploadAudio_SelectFileButton.
                    IconButton(onClick = onUploadClick, enabled = !uploading, modifier = Modifier.size(32.dp)) {
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Upload, contentDescription = "Загрузить аудио", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text("${tracks.size} треков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // P0.35 #MUSIC2-VOLUME: volume slider. VK web: AudioPlayerBlock_VolumeSlider.
            item(key = "volume_slider") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = "Громкость",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = volume,
                        onValueChange = { PlayerConnection.setVolume(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
            items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
                Music2TrackRow(
                    track = track,
                    isPlaying = isCurrentTrack(track) && isPlaying,
                    progress = if (isCurrentTrack(track)) currentProgress else 0f,
                    onClick = {
                        val idx = tracks.indexOf(track)
                        if (isCurrentTrack(track)) PlayerConnection.togglePlayPause()
                        else PlayerConnection.playTrackList(tracks, idx, fromMyMusic = true)
                    },
                )
            }
            if (tracks.isNotEmpty()) {
                item(key = "footer") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (tracks.isEmpty() && !loading) {
                item(key = "empty") {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Нет треков", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (errorText != null && tracks.isEmpty()) {
                item(key = "error") {
                    Text(errorText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}

// P0.34: контент каталога (вкладки 1-4) — блоки с треками.
@Composable
private fun Music2CatalogContent(
    catalogBlocks: List<re.pinok.data.model.CatalogBlock>,
    catalogLoading: Boolean,
    catalogError: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isCurrentTrack: (Track) -> Boolean,
    isPlaying: Boolean,
    currentProgress: Float,
) {
    if (catalogLoading && catalogBlocks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            catalogBlocks.forEach { block ->
                item(key = "block_${block.blockId ?: block.title ?: block.hashCode()}") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        block.title?.let { title ->
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        block.subtitle?.let { sub ->
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                        }
                    }
                }
                items(block.tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
                    Music2TrackRow(
                        track = track,
                        isPlaying = isCurrentTrack(track) && isPlaying,
                        progress = if (isCurrentTrack(track)) currentProgress else 0f,
                        onClick = {
                            val idx = block.tracks.indexOf(track)
                            if (isCurrentTrack(track)) PlayerConnection.togglePlayPause()
                            else PlayerConnection.playTrackList(block.tracks, idx)
                        },
                    )
                }
                // P0.37 #MUSIC2-FRIENDS: горизонтальный слайдер друзей.
                // VK web: links-slider-block / links-cell / links-cell-avatar.
                if (block.friends.isNotEmpty()) {
                    item(key = "friends_${block.blockId ?: block.title}") {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(block.friends, key = { it.id }) { friend ->
                                Column(
                                    modifier = Modifier.width(72.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Аватар друга.
                                    if (friend.avatarUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = friend.avatarUrl,
                                            contentDescription = friend.name,
                                            modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(friend.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Имя друга.
                                    Text(friend.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    // Количество треков.
                                    Text(friend.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (catalogBlocks.isEmpty() && !catalogLoading) {
                item(key = "catalog_empty") {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Каталог недоступен", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (catalogError != null && catalogBlocks.isEmpty()) {
                item(key = "catalog_error") {
                    Text(catalogError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}
