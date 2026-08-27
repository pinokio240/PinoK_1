package re.pinok.ui.screens.photos

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Album
import re.pinok.data.model.PhotoItem
import re.pinok.ui.components.ErrorView
import re.pinok.ui.components.PhotoViewer
import re.pinok.util.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen() {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loadingAlbums by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var openedAlbum by remember { mutableStateOf<Album?>(null) }
    // Fix #83: pull-to-refresh для списка альбомов.
    var isRefreshingAlbums by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        scope.launch {
            loadingAlbums = true
            errorText = null
            try {
                val list = app.apiClient.photosGetAlbums()
                // Fix #53: защитная дедупликация — LazyColumn keys должны быть уникальны.
                albums = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                AppLog.i("PhotosScreen", "Loaded ${list.size} albums")
                if (list.isEmpty()) {
                    errorText = app.apiClient.lastApiError ?: "Нет альбомов"
                }
            } catch (e: Exception) {
                AppLog.e("PhotosScreen", "Failed to load albums", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loadingAlbums = false
            }
        }
    }

    // Fix #83: pull-to-refresh — перезагрузка списка альбомов.
    fun refreshAlbums() {
        scope.launch {
            isRefreshingAlbums = true
            try {
                val list = app.apiClient.photosGetAlbums()
                albums = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                errorText = null
            } catch (e: Exception) {
                AppLog.w("PhotosScreen", "refreshAlbums failed: ${e.message}")
            } finally {
                isRefreshingAlbums = false
            }
        }
    }

    val current = openedAlbum
    if (current != null) {
        AlbumPhotosView(
            album = current,
            onBack = { openedAlbum = null },
        )
        return
    }

    if (loadingAlbums) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (albums.isEmpty()) {
        ErrorView(
            message = errorText ?: "Нет альбомов",
            onRetry = { refreshAlbums() },
        )
        return
    }

    // Fix #83: PullToRefreshBox — pull-to-refresh списка альбомов.
    // VK photos.getAlbums возвращает все альбомы разом (без offset-пагинации),
    // поэтому infinite scroll здесь не нужен — только refresh.
    PullToRefreshBox(
        isRefreshing = isRefreshingAlbums,
        onRefresh = { refreshAlbums() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), state = listState) {
            items(albums, key = { "${it.ownerId}_${it.id}" }) { album ->
                AlbumRow(album = album, onClick = { openedAlbum = album })
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = album.thumbSrc
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.size} фото",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!album.description.isNullOrBlank()) {
                Text(
                    text = album.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumPhotosView(album: Album, onBack: () -> Unit) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Fix #83: пагинация фото в альбоме + pull-to-refresh.
    val pageSize = 60
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    LaunchedEffect(album.id) {
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val list = app.apiClient.photosGet(
                    ownerId = album.ownerId,
                    albumId = album.id.toString(),
                    count = pageSize,
                )
                // Fix #53: защитная дедупликация — LazyColumn keys должны быть уникальны.
                photos = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                if (list.size < pageSize) endReached = true
                AppLog.i("PhotosScreen", "Loaded ${list.size} photos from album ${album.id}")
                if (list.isEmpty()) {
                    errorText = app.apiClient.lastApiError ?: "В альбоме нет фото"
                }
            } catch (e: Exception) {
                AppLog.e("PhotosScreen", "Failed to load photos", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #83: pull-to-refresh — перезагрузка первой страницы фото.
    fun refreshPhotos() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.photosGet(
                    ownerId = album.ownerId,
                    albumId = album.id.toString(),
                    count = pageSize,
                )
                photos = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                endReached = (list.size < pageSize)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("PhotosScreen", "refreshPhotos failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #83: пагинация — подгрузка следующих фото через offset.
    fun loadMorePhotos() {
        if (loadingMore || endReached || photos.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = photos.size
                val page = app.apiClient.photosGet(
                    ownerId = album.ownerId,
                    albumId = album.id.toString(),
                    count = pageSize,
                    offset = offset,
                ).filter { it.id > 0 && it.ownerId != 0L }
                 .filter { np -> photos.none { it.id == np.id && it.ownerId == np.ownerId } }
                if (page.isNotEmpty()) {
                    photos = (photos + page).distinctBy { "${it.ownerId}_${it.id}" }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("PhotosScreen", "loadMorePhotos failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #83: бесконечная пагинация — триггер при скролле к концу сетки.
    LaunchedEffect(gridState, photos.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= photos.size - 6 && photos.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMorePhotos() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${photos.size} / ${album.size} фото",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        if (photos.isEmpty()) {
            ErrorView(
                message = errorText ?: "Нет фото",
                onRetry = { refreshPhotos() },
            )
            return
        }

        // Fix #83: PullToRefreshBox — pull-to-refresh фото в альбоме.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshPhotos() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(photos, key = { _, photo -> "${photo.ownerId}_${photo.id}" }) { index, photo ->
                    PhotoThumb(
                        photo = photo,
                        onClick = {
                            // Sprint 2, P1-1 (#88): передаём largestUrl всех фото + индекс.
                            val urls = photos.mapNotNull { it.largestUrl }
                            if (urls.isNotEmpty()) {
                                photoViewerState.value = urls to index
                            }
                        },
                    )
                }
                // Fix #83: футер пагинации в grid (full-span item).
                if (loadingMore || (endReached && photos.isNotEmpty())) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = "Это все фото",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
        val viewer = photoViewerState.value
        if (viewer != null) {
            PhotoViewer(
                photos = viewer.first,
                initial = viewer.second,
                onDismiss = { photoViewerState.value = null },
            )
        }
    }
}

@Composable
private fun PhotoThumb(photo: PhotoItem, onClick: () -> Unit = {}) {
    val url = photo.mediumUrl ?: photo.largestUrl
    // Sprint 2, P1-2 (#89): локальное состояние лайка фото.
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var isLiked by remember(photo.id) { mutableStateOf(photo.likes?.userLikes == 1) }
    var likeCount by remember(photo.id) { mutableStateOf(photo.likes?.count ?: 0) }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = photo.text,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Sprint 2, P1-2 (#89): кликабельная кнопка лайка фото (type="photo").
        // Полупрозрачный фон-чип в правом нижнем углу, поверх thumbnail.
        // clickable перехватывает тап — PhotoViewer не откроется.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    if (photo.id <= 0 || photo.ownerId == 0L) return@clickable
                    val newLiked = !isLiked
                    isLiked = newLiked
                    likeCount = (likeCount + (if (newLiked) 1 else -1)).coerceAtLeast(0)
                    scope.launch {
                        val newCount = if (newLiked) {
                            app.apiClient.likesAdd("photo", photo.ownerId, photo.id)
                        } else {
                            app.apiClient.likesDelete("photo", photo.ownerId, photo.id)
                        }
                        if (newCount >= 0) {
                            likeCount = newCount
                        } else {
                            isLiked = !newLiked
                            likeCount = (likeCount + (if (newLiked) -1 else 1)).coerceAtLeast(0)
                        }
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Лайк",
                modifier = Modifier.size(12.dp),
                tint = if (isLiked) Color(0xFFE53935) else Color.White,
            )
            if (likeCount > 0) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = likeCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
