// File: ui/screens/video/VideoScreen.kt
package re.pinok.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Video
import re.pinok.media.VideoDownloadManager
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран списка видео — нативный интерфейс VK Video mobile.
 *
 * Тёмная тема, single-column список (как в ВК мобильном приложении):
 *  — Шапка с логотипом + вкладками (Мои видео / Альбомы / Каталоги)
 *  — Поле поиска
 *  — Лента видео-карточек: 16:9 превью + длительность + бейдж "Офлайн"
 *    + заголовок + просмотры/дата + кнопки скачать/меню
 *
 * @param onVideoClick Callback при тапе на карточку — открывает VideoPlayerScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@OptIn(kotlinx.coroutines.FlowPreview::class) // #VIDEO-SEARCH: debounce — FlowPreview API
fun VideoScreen(
    onVideoClick: (Video) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var apiErrorMessage by remember { mutableStateOf<String?>(null) }
    // Fix #81: пагинация видео + pull-to-refresh.
    val pageSize = 30
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Fix #258: состояние поиска — переносим в глобальный TopAppBar.
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    // #VIDEO-SEARCH: результаты поиска (отдельный список, не клиентский фильтр).
    var searchResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // #VIDEO-PORT: вкладки «Мои видео»(0) / «Альбомы»(1) / «Каталоги»(2).
    var selectedTab by remember { mutableStateOf(0) }
    var albums by remember { mutableStateOf<List<re.pinok.data.model.VideoAlbum>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var catalogSections by remember { mutableStateOf<List<re.pinok.data.model.VideoCatalogSection>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf<re.pinok.data.model.VideoCatalogSection?>(null) }
    var discoverVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var discoverLoading by remember { mutableStateOf(false) }
    // Выбранный альбом (null = список альбомов).
    var selectedAlbum by remember { mutableStateOf<re.pinok.data.model.VideoAlbum?>(null) }
    var albumVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var albumVideosLoading by remember { mutableStateOf(false) }

    // #VIDEO-PORT: загрузка альбомов при переключении на вкладку «Альбомы».
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && albums.isEmpty() && !albumsLoading) {
            albumsLoading = true
            try {
                val (_, list) = app.apiClient.videoGetAlbums(count = 50)
                albums = list
                AppLog.i("VideoScreen", "Loaded ${list.size} albums")
            } catch (e: Exception) {
                AppLog.w("VideoScreen", "videoGetAlbums failed: ${e.message}")
            } finally {
                albumsLoading = false
            }
        }
        if (selectedTab == 2 && catalogSections.isEmpty() && !catalogLoading) {
            catalogLoading = true
            try {
                val list = app.apiClient.videoGetCatalogSections()
                catalogSections = list
                AppLog.i("VideoScreen", "Loaded ${list.size} catalog sections")
            } catch (e: Exception) {
                AppLog.w("VideoScreen", "videoGetCatalogSections failed: ${e.message}")
            } finally {
                catalogLoading = false
            }
        }
    }

    // #VIDEO-PORT: загрузка discovery-видео выбранного раздела каталога.
    LaunchedEffect(selectedSection) {
        val section = selectedSection ?: return@LaunchedEffect
        discoverLoading = true
        discoverVideos = emptyList()
        try {
            val list = app.apiClient.videoGetDiscover(sectionId = section.id, sectionName = section.name, count = 30)
            discoverVideos = list
            AppLog.i("VideoScreen", "Loaded ${list.size} discover videos for ${section.name}")
        } catch (e: Exception) {
            AppLog.w("VideoScreen", "videoGetDiscover failed: ${e.message}")
        } finally {
            discoverLoading = false
        }
    }

    // #VIDEO-PORT: загрузка видео выбранного альбома.
    LaunchedEffect(selectedAlbum) {
        val album = selectedAlbum ?: return@LaunchedEffect
        albumVideosLoading = true
        albumVideos = emptyList()
        try {
            val list = app.apiClient.videoGet(ownerId = album.ownerId, count = 50, albumId = album.id)
                .filter { it.id > 0 && it.ownerId != 0L }
                .distinctBy { "${it.ownerId}_${it.id}" }
            albumVideos = list
            AppLog.i("VideoScreen", "Loaded ${list.size} videos in album ${album.title}")
        } catch (e: Exception) {
            AppLog.w("VideoScreen", "videoGet(album) failed: ${e.message}")
        } finally {
            albumVideosLoading = false
        }
    }

    val downloads by VideoDownloadManager.downloads.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            endReached = false
            apiErrorMessage = null
            try {
                val list = app.apiClient.videoGet(count = pageSize)
                    // Fix #53: защитная дедупликация.
                    // Audit #40: порядок ownerId_id (как в остальных экранах).
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                videos = list
                if (list.size < pageSize) endReached = true
                if (videos.isEmpty()) {
                    val errCode = app.apiClient.lastApiErrorCode
                    val errStr = app.apiClient.lastApiError
                    apiErrorMessage = when (errCode) {
                        5 -> "Токен недействителен или истёк (error 5)."
                        15 -> "Доступ к видео ограничен VK (error 15)."
                        else -> if (errStr != null) "Ошибка: $errStr" else "Нет видео"
                    }
                }
            } catch (e: Exception) {
                AppLog.e("VideoScreen", "Failed to load videos", e)
            } finally {
                loading = false
            }
        }
    }

    // Fix #81: pull-to-refresh — перезагрузка первой страницы.
    fun refreshVideos() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.videoGet(count = pageSize)
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.id}_${it.ownerId}" }
                videos = list
                endReached = (list.size < pageSize)
                apiErrorMessage = null
            } catch (e: Exception) {
                AppLog.w("VideoScreen", "refreshVideos failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #81: пагинация — подгрузка следующих видео через offset.
    fun loadMoreVideos() {
        if (loadingMore || endReached || videos.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = videos.size
                val page = app.apiClient.videoGet(count = pageSize, offset = offset)
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .filter { np -> videos.none { it.id == np.id && it.ownerId == np.ownerId } }
                if (page.isNotEmpty()) {
                    videos = (videos + page).distinctBy { "${it.id}_${it.ownerId}" }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("VideoScreen", "loadMoreVideos failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #81: бесконечная пагинация — триггер при скролле к концу.
    LaunchedEffect(listState, videos.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // +1 компенсирует item-футер пагинации в конце LazyColumn.
            lastVisible >= videos.size - 3 && videos.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreVideos() }
    }

    // Тёмная тема как в нативном ВК Video — фон почти чёрный.
    val vkBlack = Color(0xFF0F0F10)
    val vkCard = Color(0xFF1C1C1E)
    val vkSurface = Color(0xFF242426)
    val vkTextPrimary = Color(0xFFFFFFFF)
    val vkTextSecondary = Color(0xFFA8A8AA)
    val vkAccent = Color(0xFF3D8BFF)

    // Fix #258: фильтрованный список по поисковому запросу.
    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos
        else videos.filter { v ->
            v.title.contains(searchQuery, ignoreCase = true) ||
                v.description?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    // #VIDEO-SEARCH: дебаунс поиска через video.search (API).
    LaunchedEffect(searchQuery) {
        snapshotFlow { searchQuery }
            .debounce(500)
            .collect { q ->
                if (q.isBlank()) {
                    searchResults = emptyList()
                    searchError = null
                    return@collect
                }
                searchLoading = true
                searchError = null
                try {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        app.apiClient.videoSearch(q, count = 50)
                    }
                    searchResults = result
                    if (result.isEmpty()) searchError = "Ничего не найдено"
                } catch (e: Exception) {
                    AppLog.e("VideoScreen", "videoSearch error", e)
                    searchError = "Ошибка поиска: ${e.message}"
                } finally {
                    searchLoading = false
                }
            }
    }

    // Fix #258: регистрируем search в глобальном TopAppBar.
    // Убираем дублирующий заголовок «Видео» из VideoHeader — глобальный
    // TopAppBar уже показывает «Видео». Поиск переносим в TopAppBar.
    // Fix #260: showSearch в ключе DisposableEffect — иначе configure()
    // вызывается один раз с showSearch=false → titleOverride=null навсегда.
    DisposableEffect(showSearch) {
        val token = ScreenTopBar.configure(
            actions = {
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) searchQuery = ""
                }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        tint = if (showSearch) vkAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            titleOverride = if (showSearch) {
                {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск видео…", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = vkAccent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Очистить",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            } else null,
        )
        onDispose { ScreenTopBar.clear(token) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vkBlack),
    ) {
        // ─── Шапка: только вкладки (заголовок «Видео» убран — он в TopAppBar) ───
        VideoTabsBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            textColor = vkTextPrimary,
            secondaryColor = vkTextSecondary,
        )

        // ─── Контент по вкладке (#VIDEO-PORT) ───
        when (selectedTab) {
            1 -> {
                // Альбомы: список альбомов или видео выбранного альбома.
                if (selectedAlbum == null) {
                    if (albumsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = vkAccent)
                        }
                    } else if (albums.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Нет альбомов", color = vkTextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(albums, key = { "${it.ownerId}_${it.id}" }) { album ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAlbum = album }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(vkCard),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val cover = album.coverUrl
                                        if (cover != null) {
                                            AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Outlined.VideoLibrary, null, tint = vkAccent, modifier = Modifier.size(26.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(album.title, color = vkTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (album.count > 0) "${album.count} видео" else "0 видео",
                                            color = vkTextSecondary, fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Видео выбранного альбома.
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAlbum = null; albumVideos = emptyList() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("← Альбомы", color = vkAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        if (albumVideosLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = vkAccent)
                            }
                        } else if (albumVideos.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Альбом пуст", color = vkTextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                            ) {
                                items(albumVideos, key = { "${it.ownerId}_${it.id}" }) { video ->
                                    VKVideoCard(
                                        video = video,
                                        downloadState = downloads[VideoDownloadManager.videoKey(video.ownerId, video.id)],
                                        cardColor = vkCard,
                                        textColor = vkTextPrimary,
                                        secondaryColor = vkTextSecondary,
                                        accentColor = vkAccent,
                                        onClick = { onVideoClick(video) },
                                        onDownloadClick = {
                                            val ds = downloads[VideoDownloadManager.videoKey(video.ownerId, video.id)]
                                            if (ds != null && ds.status != DownloadStatus.FAILED) {
                                                VideoDownloadManager.removeDownload(video.ownerId, video.id)
                                            } else {
                                                VideoDownloadManager.enqueueDownload(video)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Каталоги: список разделов → discovery-видео раздела.
                if (selectedSection == null) {
                    if (catalogLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = vkAccent)
                        }
                    } else if (catalogSections.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Каталог недоступен для вашего аккаунта",
                                color = vkTextSecondary, fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(catalogSections, key = { it.id }) { section ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSection = section }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        section.name,
                                        color = vkTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("›", color = vkTextSecondary, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSection = null; discoverVideos = emptyList() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("← Каталоги", color = vkAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        if (discoverLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = vkAccent)
                            }
                        } else if (discoverVideos.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Нет видео", color = vkTextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                            ) {
                                items(discoverVideos, key = { "${it.ownerId}_${it.id}" }) { video ->
                                    VKVideoCard(
                                        video = video,
                                        downloadState = downloads[VideoDownloadManager.videoKey(video.ownerId, video.id)],
                                        cardColor = vkCard,
                                        textColor = vkTextPrimary,
                                        secondaryColor = vkTextSecondary,
                                        accentColor = vkAccent,
                                        onClick = { onVideoClick(video) },
                                        onDownloadClick = {
                                            val ds = downloads[VideoDownloadManager.videoKey(video.ownerId, video.id)]
                                            if (ds != null && ds.status != DownloadStatus.FAILED) {
                                                VideoDownloadManager.removeDownload(video.ownerId, video.id)
                                            } else {
                                                VideoDownloadManager.enqueueDownload(video)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
        // ─── Лента видео ───
        // Fix #81: PullToRefreshBox — pull-to-refresh видео.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshVideos() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = vkAccent)
                        }
                    }
                }

                // #VIDEO-SEARCH: активный поиск → показываем API-результаты.
                if (searchQuery.isNotBlank()) {
                    if (searchLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = vkAccent)
                            }
                        }
                    } else if (searchError != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(searchError!!, color = vkTextSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    } else if (searchResults.isNotEmpty()) {
                        item { SectionHeader("РЕЗУЛЬТАТЫ ПОИСКА", vkTextPrimary, vkTextSecondary) }
                        items(searchResults, key = { "s_${it.ownerId}_${it.id}" }) { video ->
                            val key = VideoDownloadManager.videoKey(video.ownerId, video.id)
                            val ds = downloads[key]
                            VKVideoCard(
                                video = video, downloadState = ds, cardColor = vkCard,
                                textColor = vkTextPrimary, secondaryColor = vkTextSecondary, accentColor = vkAccent,
                                onClick = { onVideoClick(video) },
                                onDownloadClick = {
                                    if (ds != null && ds.status != DownloadStatus.FAILED) VideoDownloadManager.removeDownload(video.ownerId, video.id)
                                    else VideoDownloadManager.enqueueDownload(video)
                                },
                            )
                        }
                    }
                } else {
                    val errMsg = apiErrorMessage
                if (errMsg != null && filteredVideos.isEmpty() && !loading && searchQuery.isBlank()) {
                    item {
                        Text(
                            text = errMsg,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                        )
                    }
                }

                if (filteredVideos.isNotEmpty()) {
                    item { SectionHeader("МОИ ВИДЕО", vkTextPrimary, vkTextSecondary) }
                }

                items(filteredVideos, key = { "${it.ownerId}_${it.id}" }) { video ->
                    val key = VideoDownloadManager.videoKey(video.ownerId, video.id)
                    val downloadState = downloads[key]

                    VKVideoCard(
                        video = video,
                        downloadState = downloadState,
                        cardColor = vkCard,
                        textColor = vkTextPrimary,
                        secondaryColor = vkTextSecondary,
                        accentColor = vkAccent,
                        onClick = { onVideoClick(video) },
                        onDownloadClick = {
                            if (downloadState != null && downloadState.status != DownloadStatus.FAILED) {
                                VideoDownloadManager.removeDownload(video.ownerId, video.id)
                            } else {
                                VideoDownloadManager.enqueueDownload(video)
                            }
                        },
                    )
                }

                // Fix #81: футер пагинации.
                item {
                    when {
                        loadingMore -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = vkAccent,
                                )
                            }
                        }
                        endReached && filteredVideos.isNotEmpty() -> {
                            Text(
                                text = "Это все видео",
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                color = vkTextSecondary,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                } // end else (searchQuery blank → обычный список)
            }
        }
            } // end else (selectedTab == 0)
        } // end when (selectedTab)
    }
}

// ─── Контекстное меню видео (#VIDEO-MORE-MENU) ────────────────────────────
@Composable
private fun VideoMoreMenu(
    video: Video,
    expanded: Boolean,
    onDismiss: () -> Unit,
    accentColor: Color,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("В избранное") },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = accentColor) },
            onClick = {
                onDismiss()
                scope.launch {
                    try {
                        val ok = app.apiClient.faveAdd("video", video.ownerId, video.id)
                        val msg = if (ok) "Добавлено в избранное" else "Не удалось добавить"
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(ctx, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Копировать ссылку") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = accentColor) },
            onClick = {
                onDismiss()
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("video", "https://vk.com/video${video.ownerId}_${video.id}"))
                android.widget.Toast.makeText(ctx, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
}

// ─── Header ────────────────────────────────────────────────────────────────
// Fix #258: VideoHeader → VideoTabsBar. Заголовок «Видео» с иконкой убран —
// он дублировал глобальный TopAppBar. Поиск «Поиск видео» (нерабочий Text)
// убран — теперь функциональный поиск в TopAppBar. Остались только вкладки.

@Composable
private fun VideoTabsBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    textColor: Color,
    secondaryColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F10))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Вкладки (#VIDEO-PORT: кликабельны — Мои видео / Альбомы / Каталоги).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            VideoTab(
                "Мои видео", selectedTab == 0, textColor, secondaryColor,
                onClick = { onTabSelected(0) },
            )
            VideoTab(
                "Альбомы", selectedTab == 1, textColor, secondaryColor,
                onClick = { onTabSelected(1) },
            )
            VideoTab(
                "Каталоги", selectedTab == 2, textColor, secondaryColor,
                onClick = { onTabSelected(2) },
            )
        }
    }
}

@Composable
private fun VideoTab(
    name: String,
    isActive: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = name,
            color = if (isActive) textColor else secondaryColor,
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 24.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) Color(0xFF3D8BFF) else Color.Transparent),
        )
    }
}

// ─── Section Header ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    textColor: Color,
    secondaryColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Показать все",
            color = secondaryColor,
            fontSize = 13.sp,
        )
    }
}

// ─── VK-style Video Card (single column, native mobile) ────────────────────

@Composable
private fun VKVideoCard(
    video: Video,
    downloadState: DownloadState?,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // Превью 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF1A1A1C)),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = video.thumbUrl
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Placeholder — цветной градиент если превью нет
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.hsl((video.id % 360).toFloat(), 0.4f, 0.3f),
                                    Color.hsl(((video.id * 31) % 360).toFloat(), 0.3f, 0.4f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // Центральная кнопка Play (полупрозрачная)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            // Длительность в правом нижнем углу
            if (video.duration > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatVideoDuration(video.duration),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }

            // Бейдж "Офлайн" в левом верхнем углу
            if (downloadState?.isCompleted == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.95f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Офлайн",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Прогресс-бар скачивания под превью
        if (downloadState != null && downloadState.isInProgress) {
            LinearProgressIndicator(
                progress = {
                    if (downloadState.progress >= 0) downloadState.progress / 100f else 0f
                },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = accentColor,
                trackColor = Color.Transparent,
            )
        }

        // Информация о видео: заголовок + просмотры/дата + кнопки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (video.views > 0) {
                            append(formatViews(video.views))
                            append(" просмотров")
                        }
                        if (video.commentsCount > 0) {
                            if (isNotEmpty()) append(" • ")
                            append(formatViews(video.commentsCount))
                            append(" комментариев")
                        }
                        if (video.date > 0) {
                            if (isNotEmpty()) append(" • ")
                            append(formatDate(video.date))
                        }
                    },
                    color = secondaryColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Кнопка скачивания
            VKVideoDownloadButton(state = downloadState, onClick = onDownloadClick, accentColor = accentColor, secondaryColor = secondaryColor)

            // Троеточие-меню
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Ещё",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
        // #VIDEO-MORE-MENU
        VideoMoreMenu(
            video = video,
            expanded = showMenu,
            onDismiss = { showMenu = false },
            accentColor = accentColor,
        )
    } // end Box
}

@Composable
private fun VKVideoDownloadButton(
    state: DownloadState?,
    onClick: () -> Unit,
    accentColor: Color,
    secondaryColor: Color,
) {
    when {
        state == null || state.status == DownloadStatus.FAILED -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Скачать",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        state.isInProgress -> {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { if (state.progress >= 0) state.progress / 100f else 0f },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accentColor,
                    trackColor = Color.Transparent,
                )
            }
        }
        state.isCompleted -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "Скачано (тап чтобы удалить)",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        state.status == DownloadStatus.REMOVING -> {
            IconButton(onClick = {}, modifier = Modifier.size(36.dp), enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = secondaryColor,
                )
            }
        }
        else -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Скачивание",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────

private fun formatVideoDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatViews(views: Int): String {
    return when {
        views >= 1_000_000 -> "%.1fM".format(views / 1_000_000.0)
        views >= 1_000 -> "%.1fK".format(views / 1_000.0)
        else -> views.toString()
    }
}

private fun formatDate(epochSeconds: Long): String {
    return try {
        val sdf = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
        sdf.format(Date(epochSeconds * 1000))
    } catch (e: Exception) {
        ""
    }
}
