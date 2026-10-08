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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog

// P0.32: вкладки каталога — как в VK web (5 табов).
// P0.38: «Обновления» удалён (VK возвращает только placeholder-заглушки).
// data-testid: AudioCatalog_Tabs_Tab_all / general / explore / radiostations / friends
private val MUSIC2_TABS = listOf("Моя музыка", "Главная", "Обзор", "Радио", "Друзья")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Music2Screen(
    onBack: () -> Unit = {},
    // P0.37 #MUSIC2-FRIENDS: режим просмотра музыки друга (ownerId != null).
    ownerId: Long? = null,
    ownerName: String? = null,
    onOpenFriend: (Long, String) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // P0.37: режим друга — скрываем табы/каталог/upload, грузим audio.get с чужим ownerId.
    val isFriendMode = ownerId != null

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
    // 3=Радио (radio), 4=Друзья (friends).
    // P0.38: «Обновления» удалён — VK возвращает только placeholder.
    val CATALOG_SECTIONS = listOf("my", "general", "explore", "radio", "friends")

    // P0.32: загрузка треков (audio.get — для «Моя музыка» или музыки друга).
    // P0.38-PAGINATION: добавлен tracksEndReached — флаг что больше нет треков.
    var tracksEndReached by remember { mutableStateOf(false) }
    // P0.38-FIX: отдельный флаг isLoadingMore — guard от параллельных пагинаций.
    // НЕ используем `loading` для guard — оно true изначально (для спиннера первичной
    // загрузки), что блокировало бы первый loadTracks(). Теперь guard только для
    // подгрузки следующих страниц, не для первичной.
    var isLoadingMore by remember { mutableStateOf(false) }
    fun loadTracks(refresh: Boolean = false) {
        if (isLoadingMore && !refresh) return  // P0.38: защита от параллельных пагинаций.
        if (refresh) {
            refreshing = true
        } else {
            isLoadingMore = true
            if (tracks.isEmpty()) loading = true  // первичная загрузка — показываем спиннер
        }
        scope.launch {
            errorText = null
            try {
                val result = app.apiClient.audioGet(count = 50, offset = if (refresh) 0 else tracks.size, ownerId = ownerId)
                if (refresh) {
                    tracks = result
                    tracksEndReached = result.size < 50
                } else {
                    tracks = (tracks + result).distinctBy { "${it.ownerId}_${it.id}" }
                    // P0.38: если вернулось меньше запрошенного — больше треков нет.
                    if (result.size < 50) tracksEndReached = true
                }
            } catch (e: Exception) {
                AppLog.w("Music2", "loadTracks error: ${e.message}")
                errorText = "Ошибка загрузки: ${e.message}"
            } finally {
                loading = false
                refreshing = false
                isLoadingMore = false
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

    // P0.34: загрузка каталога (catalog.getSection — для вкладок 1-5).
    // P0.38-FIX: loadedSection объявлен ДО loadCatalog (Kotlin local function
    // не видит переменные, объявленные позже). Трекинг нужен чтобы при смене
    // вкладки перезагружать каталог, а не показывать блоки от предыдущей вкладки.
    var loadedSection by remember { mutableStateOf<String?>(null) }
    fun loadCatalog(section: String, refresh: Boolean = false) {
        // P0.38-FIX: очищаем блоки СИНХРОННО (до launch) — иначе между тапом по
        // вкладке и стартом корутины UI рендерит старые блоки от предыдущей секции.
        // Пользователь видел контент «Главная» на вкладке «Друзья» = «не свой контент».
        if (loadedSection != section && !refresh) {
            catalogBlocks = emptyList()
            catalogLoading = true
        }
        scope.launch {
            if (refresh) refreshing = true else catalogLoading = true
            catalogError = null
            try {
                val blocks = app.apiClient.catalogGetAudio(section = section, count = 10)
                catalogBlocks = blocks
                loadedSection = section
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
            // P0.38-FIX: перезагружаем если (1) пусто, (2) запрошен refresh,
            // ИЛИ (3) сменилась секция (general→explore→radio→updates).
            val section = CATALOG_SECTIONS[selectedTab]
            if (catalogBlocks.isEmpty() || refreshNeeded || loadedSection != section) {
                loadCatalog(section, refresh = false)
            }
        }
    }

    // P0.32: начальная загрузка.
    LaunchedEffect(Unit) {
        loadTracks()
    }

    // P0.38 #MUSIC2-PAGINATION: бесконечный скролл — подгрузка следующих треков
    // когда пользователь прокручивает к концу списка. Работает для «Моя музыка»
    // и для музыки друга (audio.get с offset).
    val endReached by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            // Триггерим когда виден предпоследний элемент (с учётом header+footer).
            total > 0 && lastVisible >= total - 3 && !loading && tracks.isNotEmpty()
        }
    }
    LaunchedEffect(endReached) {
        if (endReached && !loading && tracks.isNotEmpty() && !tracksEndReached) {
            loadTracks(refresh = false)
        }
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
        // P0.37: в режиме друга — показываем заголовок с именем друга вместо табов.
        if (isFriendMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ownerName ?: "Музыка",
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
        } else {
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
        }

        // P0.32+P0.34: PullToRefreshBox — pull-to-refresh.
        // Вкладка 0 (Моя музыка): обновляет audio.get.
        // Вкладки 1-4 (Каталог): обновляет catalog.getSection.
        // В режиме друга — обновляет audio.get(ownerId=friend).
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (isFriendMode || selectedTab == 0) {
                    loadTracks(refresh = true)
                } else {
                    val section = CATALOG_SECTIONS[selectedTab]
                    loadCatalog(section, refresh = true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isFriendMode || selectedTab == 0) {
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
                    showControls = !isFriendMode,
                    headerTitle = if (isFriendMode) ownerName else null,
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
                    onOpenFriend = onOpenFriend,
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
    // P0.37: в режиме друга скрываем upload/volume controls.
    showControls: Boolean = true,
    headerTitle: String? = null,
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
                    Text(headerTitle ?: "Моя музыка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    // P0.36 #MUSIC2-UPLOAD: кнопка загрузки аудио.
                    // VK web: UploadAudio_SelectFileButton.
                    if (showControls) {
                        IconButton(onClick = onUploadClick, enabled = !uploading, modifier = Modifier.size(32.dp)) {
                            if (uploading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Upload, contentDescription = "Загрузить аудио", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Text("${tracks.size} треков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // P0.35 #MUSIC2-VOLUME: volume slider. VK web: AudioPlayerBlock_VolumeSlider.
            // Скрывается в режиме друга (нет смысла менять громкость плеера из чужой музыки).
            if (showControls) {
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
    // P0.37: клик по карточке друга → открытие его музыки.
    onOpenFriend: (Long, String) -> Unit = { _, _ -> },
) {
    if (catalogLoading && catalogBlocks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // P0.37-REWRITE: рендеринг по образцу старого MusicScreen.MusicHomeTab.
        // Каждый viewType обрабатывается отдельно — слайдеры треков/плейлистов/друзей
        // рендерятся как горизонтальные LazyRow, а не вертикальный список.
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            catalogBlocks.forEachIndexed { blockIndex, block ->
                val blockKey = "blk_${blockIndex}_${block.blockId ?: block.title ?: block.hashCode()}"
                when (block.viewType) {
                    // HEADER/HEADER_EXTENDED: title уже дублируется в контент-блоке,
                    // отдельный рендер давал бы двойной заголовок. Пропускаем.
                    re.pinok.data.model.CatalogViewType.HEADER,
                    re.pinok.data.model.CatalogViewType.HEADER_EXTENDED -> Unit
                    // SEPARATOR: тонкая разделительная линия.
                    re.pinok.data.model.CatalogViewType.SEPARATOR -> {
                        item(key = "sep_$blockKey") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    // TRIPLE_STACKED_SLIDER: горизонтальная карусель треков.
                    re.pinok.data.model.CatalogViewType.TRIPLE_STACKED_SLIDER -> {
                        if (block.tracks.isNotEmpty()) {
                            item(key = "hdr_$blockKey") {
                                Music2SectionHeader(block.title)
                            }
                            item(key = "ts_$blockKey") {
                                Music2TrackSliderRow(
                                    tracks = block.tracks,
                                    isCurrentTrack = isCurrentTrack,
                                    isPlaying = isPlaying,
                                    onPlayTrack = { idx ->
                                        PlayerConnection.playTrackList(block.tracks, idx)
                                    },
                                )
                            }
                        }
                    }
                    // RECOMMS_SLIDER / LARGE_SLIDER: горизонтальная карусель плейлистов.
                    re.pinok.data.model.CatalogViewType.RECOMMS_SLIDER,
                    re.pinok.data.model.CatalogViewType.LARGE_SLIDER -> {
                        if (block.playlists.isNotEmpty()) {
                            item(key = "hdr_$blockKey") {
                                Music2SectionHeader(block.title)
                            }
                            item(key = "pl_$blockKey") {
                                Music2PlaylistSliderRow(playlists = block.playlists)
                            }
                        }
                    }
                    // P0.38 #MUSIC2-FRIENDS: SLIDER → GRID (вертикальная сетка друзей).
                    // VK web: data-testid="grid" --grid-columns=6. На мобиле 3 колонки.
                    re.pinok.data.model.CatalogViewType.SLIDER -> {
                        if (block.friends.isNotEmpty()) {
                            item(key = "hdr_$blockKey") {
                                Music2SectionHeader(block.title)
                            }
                            item(key = "fr_$blockKey") {
                                Music2FriendsSliderRow(
                                    friends = block.friends,
                                    onOpenFriend = onOpenFriend,
                                )
                            }
                        }
                    }
                    // P0.38 #MUSIC2-RADIO: RADIO_LIST — grid 2 колонки (VK web flex-wrap).
                    re.pinok.data.model.CatalogViewType.RADIO_LIST -> {
                        if (block.radioStations.isNotEmpty()) {
                            item(key = "hdr_$blockKey") {
                                Music2SectionHeader(block.title)
                            }
                            // P0.38: рендерим парами (по 2 станции в ряд).
                            val stations = block.radioStations
                            val pairs = stations.chunked(2)
                            itemsIndexed(pairs, key = { i, _ -> "rsp_${blockIndex}_$i" }) { _, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pair.forEach { station ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            Music2RadioStationRow(station = station)
                                        }
                                    }
                                    // если нечётное количество — добиваем пустым spacer.
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    // LIST / UNKNOWN / BANNER — пропускаем.
                    // P0.38: BANNER — это placeholder-заглушки (VK возвращает их когда
                    // реального контента нет). Не рендерим — пустое место.
                    // LARGE_LIST — обрабатывается через SLIDER (links → friends).
                    else -> Unit
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

// P0.37-REWRITE: заголовок секции каталога (как SectionHeader в старом MusicScreen).
@Composable
private fun Music2SectionHeader(title: String?) {
    if (title.isNullOrBlank()) return
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// P0.37-REWRITE: горизонтальная карусель треков (по образцу TrackSliderRow).
@Composable
private fun Music2TrackSliderRow(
    tracks: List<Track>,
    isCurrentTrack: (Track) -> Boolean,
    isPlaying: Boolean,
    onPlayTrack: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
            val isCurrent = isCurrentTrack(track)
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable {
                        val idx = tracks.indexOf(track)
                        if (isCurrent) PlayerConnection.togglePlayPause()
                        else onPlayTrack(idx)
                    },
            ) {
                // Обложка с оверлеем play.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (track.albumThumb != null) {
                        AsyncImage(
                            model = track.albumThumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    }
                    // Оверлей play/pause.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent && isPlaying) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Название трека.
                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                // Артист.
                Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// P0.37-REWRITE: горизонтальная карусель плейлистов (по образцу PlaylistSliderRow).
@Composable
private fun Music2PlaylistSliderRow(
    playlists: List<re.pinok.data.model.CatalogPlaylist>,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playlists, key = { "${it.ownerId}_${it.id}" }) { pl ->
            Column(modifier = Modifier.width(150.dp)) {
                // Обложка плейлиста.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pl.coverUrl != null) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Название плейлиста.
                Text(pl.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                // Совпадение вкусов или кол-во треков.
                val subtitle = when {
                    pl.matchPercent != null -> "${pl.matchPercent}% совпадение"
                    pl.count > 0 -> "${pl.count} треков"
                    else -> null
                }
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// P0.38 #MUSIC2-FRIENDS-GRID: вертикальная сетка друзей (как VK web grid).
// VK web: data-testid="grid" --grid-columns=6, card 147px, avatar 135x135.
// На мобиле 3 колонки, card ~108dp, avatar 108dp.
@Composable
private fun Music2FriendsSliderRow(
    friends: List<re.pinok.data.model.CatalogFriend>,
    onOpenFriend: (Long, String) -> Unit,
) {
    // P0.38: 3 колонки на мобиле. VK web 6 на десктопе, но экран уже.
    val columns = 3
    val rows = (friends.size + columns - 1) / columns
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (colIndex in 0 until columns) {
                    val idx = rowIndex * columns + colIndex
                    if (idx < friends.size) {
                        val friend = friends[idx]
                        Music2FriendCard(friend = friend, onOpenFriend = onOpenFriend)
                    } else {
                        // пустой placeholder чтобы выравнивание не съезжало.
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (rowIndex < rows - 1) Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// P0.38: одна карточка друга (grid-item).
@Composable
private fun androidx.compose.foundation.layout.RowScope.Music2FriendCard(
    friend: re.pinok.data.model.CatalogFriend,
    onOpenFriend: (Long, String) -> Unit,
) {
    val friendOwnerId = friend.ownerId
    Column(
        modifier = Modifier
            .weight(1f)
            .then(
                if (friendOwnerId != null)
                    Modifier.clickable { onOpenFriend(friendOwnerId, friend.name) }
                else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Аватар 108dp (VK web 135px, на мобиле чуть меньше).
        if (friend.avatarUrl != null) {
            AsyncImage(
                model = friend.avatarUrl,
                contentDescription = friend.name,
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(friend.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Имя друга.
        Text(friend.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // Количество треков.
        Text(friend.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// P0.38 #MUSIC2-RADIO: карточка радиостанции (grid-item как VK web).
// VK web: AudioCatalog_BlockRadioStationCell, flex-basis=166px,
// logo 72x72, --radio_cell_gradient_color (#D6424D), 2 кнопки: ToggleFollowing + TogglePlaying.
// На мобиле 2 колонки (VK web flex-wrap на десктопе ~6 колонок).
@Composable
private fun Music2RadioStationRow(station: re.pinok.data.model.CatalogRadioStation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // TODO: запуск стрима через PlayerConnection (stream_url HLS).
                AppLog.i("Music2", "radio click: ${station.name} stream=${station.streamUrl != null}")
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Лого с gradient-фоном (VK web: --radio_cell_gradient_color).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    station.backgroundColor?.let { parseHexColor(it) }
                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (station.logoUrl != null) {
                AsyncImage(
                    model = station.logoUrl,
                    contentDescription = station.name,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            // Кнопка play (TogglePlaying) — справа внизу.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, "Слушать", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Индикатор подписки (ToggleFollowing) — слева сверху.
            if (station.isFollowed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, "В моей музыке", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Название станции (2 строки max — VK web: --textclamp-lines=2).
        Text(
            station.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// P0.38: парсит HEX цвет (#D6424D) в Compose Color.
// НЕ @Composable — fallback на Color.Gray (не MaterialTheme, т.к. функция чистая).
private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}
