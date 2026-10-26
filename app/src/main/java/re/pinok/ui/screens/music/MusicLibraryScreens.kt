// File: ui/screens/music/MusicLibraryScreens.kt
package re.pinok.ui.screens.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.AudioArtist
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.CatalogBlock
import re.pinok.data.model.CatalogPlaylist
import re.pinok.data.model.CatalogViewType
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.util.AppLog

// #MUSIC-PORT: общие цвета (как в MusicScreen/AudioQueueScreen).
private val VK_BLACK = androidx.compose.ui.graphics.Color(0xFF0F0F10)
private val VK_CARD = androidx.compose.ui.graphics.Color(0xFF1C1C1E)
private val VK_TEXT_PRIMARY = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val VK_TEXT_SECONDARY = androidx.compose.ui.graphics.Color(0xFFA8A8AA)
private val VK_ACCENT = androidx.compose.ui.graphics.Color(0xFF3D8BFF)

/** #MUSIC-PORT: общая шапка вложенного экрана (стрелка назад + заголовок). */
@Composable
private fun LibraryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VK_BLACK)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = VK_TEXT_PRIMARY)
        }
        Text(
            text = title,
            color = VK_TEXT_PRIMARY,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  Плейлисты (мои) — audio.getPlaylists
// ════════════════════════════════════════════════════════════════════

@Composable
fun MusicPlaylistsScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (ownerId: Long, playlistId: Long, accessKey: String?) -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    var playlists by remember { mutableStateOf<List<AudioPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // #AUDIO-PAGING-ALL (волна 38): была одна страница на 50 плейлистов —
    // при библиотеке больше 50 до конца списка было не доскроллить.
    var serverOffset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        scope.launch {
            loadingMore = true
            try {
                val (_, page) = app.apiClient.audioGetPlaylists(count = 50, offset = serverOffset)
                serverOffset += page.size
                val fresh = page.filter { nv -> playlists.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                playlists = (playlists + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец списка
                // (VK может отдать короткую страницу в середине листинга).
                hasMore = page.isNotEmpty()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("MusicPlaylistsScreen", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        errorText = null
        try {
            val (total, list) = app.apiClient.audioGetPlaylists(count = 50, offset = 0)
            playlists = list
            serverOffset = list.size
            // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец списка;
            // сбой (total=-1) — сохраняем hasMore для повтора.
            hasMore = list.isNotEmpty() || total < 0
            if (list.isEmpty()) {
                // #AUDIO-PAGING-HOLE: сбой вызова ≠ «нет плейлистов» — честная причина.
                errorText = if (total < 0) {
                    "Не удалось загрузить плейлисты: ${app.apiClient.lastApiErrorHuman() ?: "неизвестная причина"}"
                } else {
                    "Нет плейлистов"
                }
            }
            AppLog.i("MusicPlaylistsScreen", "Loaded ${list.size} playlists (total=$total hasMore=$hasMore)")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicPlaylistsScreen", "load failed: ${e.message}")
            errorText = "Ошибка: ${e.message}"
        } finally {
            loading = false
        }
    }

    // #AUDIO-PAGING-ALL: автодогрузка при доскролле до конца.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 4) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar("Плейлисты", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VK_ACCENT)
            }
            errorText != null && playlists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorText ?: "", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(playlists, key = { "${it.ownerId}_${it.id}" }) { pl ->
                    PlaylistRow(
                        title = pl.title,
                        coverUrl = pl.coverUrl,
                        subtitle = if (pl.count > 0) "${pl.count} треков" else null,
                        onClick = { onOpenPlaylist(pl.ownerId, pl.id, pl.accessKey) },
                    )
                }
                // #AUDIO-PAGING-ALL: футер догрузки.
                if (hasMore) {
                    item(key = "playlists_more") {
                        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VK_ACCENT)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Детали плейлиста — audio.getPlaylistById
// ════════════════════════════════════════════════════════════════════

@Composable
fun PlaylistDetailScreen(
    ownerId: Long,
    playlistId: Long,
    accessKey: String?,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<AudioPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // #MUSIC-PLAYLIST-FULL (Fix #283): прогресс пагинации для плейлистов 100+.
    var loadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // #SAVE-COPY: состояние операции «Сохранить копию» (audio.savePlaylistAsCopy).
    // Показывается только для ЧУЖИХ плейлистов (ownerId != myId).
    var savingCopy by remember { mutableStateOf(false) }
    val myUserId = remember { app.exchangeAuthRepository.userId() }
    // #PLAYLIST-ACTIONS: состояние операций follow/unfollow/edit/delete.
    var followPending by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var actionPending by remember { mutableStateOf(false) }

    LaunchedEffect(ownerId, playlistId, accessKey) {
        loading = true
        loadProgress = null
        try {
            val (pl, plTracks) = app.apiClient.audioGetPlaylistById(
                playlistId = playlistId,
                ownerId = ownerId,
                accessKey = accessKey,
                count = 100,
                onProgress = { loaded, total -> loadProgress = loaded to total },
            )
            playlist = pl
            tracks = plTracks.filter { it.id > 0L && !it.url.isNullOrBlank() }
            AppLog.i("PlaylistDetailScreen", "Loaded playlist ${plTracks.size} tracks")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("PlaylistDetailScreen", "load failed: ${e.message}")
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar(playlist?.title ?: "Плейлист", onBack)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VK_ACCENT)
                    loadProgress?.let { (loaded, total) ->
                        if (total > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Загружено $loaded из $total…",
                                color = VK_TEXT_SECONDARY,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VK_CARD),
                            contentAlignment = Alignment.Center,
                        ) {
                            val cover = playlist?.coverUrl
                            if (cover != null) {
                                AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.MusicNote, null, tint = VK_ACCENT, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        playlist?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(desc, color = VK_TEXT_SECONDARY, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(VK_ACCENT)
                                    .clickable {
                                        if (tracks.isNotEmpty()) {
                                            PlayerConnection.playTrackList(tracks, 0)
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = VK_TEXT_PRIMARY, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Играть", color = VK_TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(VK_CARD)
                                    .clickable {
                                        scope.launch {
                                            TrackDownloadManager.enqueuePlaylistDownload(
                                                playlistTitle = playlist?.title ?: "Плейлист",
                                                coverUrl = playlist?.coverUrl,
                                                tracks = tracks,
                                            )
                                            android.widget.Toast.makeText(
                                                app.applicationContext,
                                                "Скачивание плейлиста: ${tracks.size} треков",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Download, null, tint = VK_ACCENT, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Скачать", color = VK_TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            // #SAVE-COPY: «Сохранить копию» — для ЧУЖИХ плейлистов (ownerId != myId).
                            // audio.savePlaylistAsCopy: 1 запрос, копирует плейлист целиком с cover/
                            // description/followers, новый id, title с датой (как в VK веб —
                            // «Для вас DD.MM.YYYY»). Только для чужих (свой нельзя скопировать).
                            // #SAVECOPY-GATE-FIX (аудит 5-UI-AUDIT C.1): КОРНЕВАЯ ПРИЧИНА жалобы
                            // «не сохраняется плейлист». Раньше проверяли `ownerId != myUserId` —
                            // но HAR доказывает: Daily Playlist (id=-21, type=generated) имеет
                            // owner_id=171093180==myUserId И permissions.save_as_copy=true.
                            // VK веб показывает кнопку Action_CopyToMyMusic для Daily Playlist
                            // (Главная1.html: testid Action_CopyToMyMusic, icon add_24).
                            // Условие должно быть ТОЛЬКО по permissions.save_as_copy,
                            // НЕ по owner_id (свой ugc имеет save_as_copy=false → кнопка скрыта).
                            // permissions==null → fallback: показываем (старые ответы без permissions).
                            if (myUserId != 0L && playlist != null &&
                                (playlist?.canSaveAsCopy == true || playlist?.permissions == null)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(VK_CARD)
                                        .clickable {
                                            if (savingCopy) return@clickable
                                            savingCopy = true
                                            val pl = playlist ?: return@clickable
                                            scope.launch {
                                                try {
                                                    // #SAVE-COPY-CREATE (HAR2 #53): audio.savePlaylistAsCopy
                                                    // на web-шлюзе возвращает err=3 для exchange-токенов
                                                    // (VK маскирует ошибку прав). Решение — 2 шага:
                                                    // 1) tracks уже загружены в tracks (audioGetPlaylistById при открытии экрана)
                                                    // 2) audio.createPlaylist(ownerId, title="orig DD.MM.YYYY", audioIds=треки)
                                                    //    — создаёт копию одним запросом (как VK веб делает для нового плейлиста).
                                                    val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                                        .format(java.util.Date())
                                                    val copyTitle = "${pl.title} $dateStr"
                                                    val audioIds = tracks
                                                        .filter { it.id > 0L && it.ownerId != 0L }
                                                        .map { "${it.ownerId}_${it.id}" }
                                                    if (audioIds.isEmpty()) {
                                                        android.widget.Toast.makeText(
                                                            app.applicationContext,
                                                            "Плейлист пуст — нечего копировать",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@launch
                                                    }
                                                    val newId = app.apiClient.audioCreatePlaylist(
                                                        ownerId = pl.ownerId,
                                                        title = copyTitle,
                                                        description = pl.description ?: "",
                                                        audioIds = audioIds,
                                                    )
                                                    if (newId > 0L) {
                                                        android.widget.Toast.makeText(
                                                            app.applicationContext,
                                                            "Плейлист сохранён: «$copyTitle» (${audioIds.size} треков)",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        val err = app.apiClient.lastApiError
                                                        android.widget.Toast.makeText(
                                                            app.applicationContext,
                                                            "Не удалось сохранить: ${err ?: "ошибка"}",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("PlaylistDetailScreen", "saveCopy failed", e)
                                                    android.widget.Toast.makeText(
                                                        app.applicationContext,
                                                        "Ошибка: ${e.message}",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                } finally {
                                                    savingCopy = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (savingCopy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = VK_ACCENT,
                                            )
                                        } else {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.PlaylistAdd,
                                                null,
                                                tint = VK_ACCENT,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (savingCopy) "Копирование…" else "Сохранить копию",
                                            color = VK_TEXT_PRIMARY,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                        // #PLAYLIST-ACTIONS: вторая Row — follow/edit/delete на основе permissions.
                        // Подписаться (чужие, canFollow) / Редактировать (свои, canEdit) /
                        // Удалить (свои, canDelete). Все с pending-индикатором.
                        val pl = playlist
                        if (pl != null && myUserId != 0L) {
                            val isOwn = pl.isOwnedBy(myUserId)
                            val showFollow = !isOwn && (pl.canFollow || pl.permissions == null)
                            val showEdit = isOwn && pl.canEdit
                            val showDelete = isOwn && pl.canDelete
                            if (showFollow || showEdit || showDelete) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Подписаться / Отписаться (чужие).
                                    if (showFollow) {
                                        val isFollowingNow = pl.isFollowing == true
                                        PlaylistActionButton(
                                            label = when {
                                                followPending -> "…"
                                                isFollowingNow -> "Вы подписаны"
                                                else -> "Подписаться"
                                            },
                                            icon = Icons.Outlined.BookmarkBorder,
                                            tint = if (isFollowingNow) VK_TEXT_SECONDARY else VK_ACCENT,
                                            enabled = !followPending,
                                            onClick = {
                                                if (followPending) return@PlaylistActionButton
                                                followPending = true
                                                scope.launch {
                                                    try {
                                                        val ok = app.apiClient.audioFollowPlaylist(
                                                            ownerId = pl.ownerId,
                                                            playlistId = pl.id,
                                                        )
                                                        val msg = if (ok && !isFollowingNow) {
                                                            "Вы подписаны на плейлист"
                                                        } else if (ok && isFollowingNow) {
                                                            "Подписка отменена"
                                                        } else {
                                                            "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}"
                                                        }
                                                        android.widget.Toast.makeText(
                                                            app.applicationContext, msg,
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                        // Обновляем isFollowing локально (without reload).
                                                        playlist = pl.copy(isFollowing = !isFollowingNow)
                                                    } catch (e: Exception) {
                                                        AppLog.e("PlaylistDetailScreen", "follow failed", e)
                                                    } finally {
                                                        followPending = false
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    // Редактировать (свои, canEdit).
                                    if (showEdit) {
                                        PlaylistActionButton(
                                            label = "Изменить",
                                            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                            tint = VK_ACCENT,
                                            enabled = !actionPending,
                                            onClick = {
                                                editTitle = pl.title
                                                editDescription = pl.description ?: ""
                                                showEditDialog = true
                                            },
                                        )
                                    }
                                    // Удалить (свои, canDelete).
                                    if (showDelete) {
                                        PlaylistActionButton(
                                            label = "Удалить",
                                            icon = Icons.Filled.Delete,
                                            tint = androidx.compose.ui.graphics.Color(0xFFFF5252),
                                            enabled = !actionPending,
                                            onClick = { showDeleteDialog = true },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Плейлист пуст", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
                        }
                    }
                } else {
                    item {
                        AudioAttachmentList(tracks = tracks)
                    }
                }
            }
        }
    }

    // #PLAYLIST-DELETE: диалог подтверждения удаления плейлиста (audio.deletePlaylist).
    if (showDeleteDialog) {
        val pl = playlist
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!actionPending) showDeleteDialog = false },
            title = { Text("Удалить плейлист?") },
            text = {
                Text(
                    "Плейлист «${pl?.title ?: ""}» будет удалён без возможности восстановления.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !actionPending,
                    onClick = {
                        if (pl == null || actionPending) return@TextButton
                        actionPending = true
                        scope.launch {
                            try {
                                val ok = app.apiClient.audioDeletePlaylist(
                                    ownerId = pl.ownerId,
                                    playlistId = pl.id,
                                )
                                if (ok) {
                                    android.widget.Toast.makeText(
                                        app.applicationContext,
                                        "Плейлист удалён",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    onBack()
                                } else {
                                    android.widget.Toast.makeText(
                                        app.applicationContext,
                                        "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e("PlaylistDetailScreen", "delete failed", e)
                            } finally {
                                actionPending = false
                                showDeleteDialog = false
                            }
                        }
                    },
                ) {
                    Text("Удалить", color = androidx.compose.ui.graphics.Color(0xFFFF5252))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !actionPending,
                    onClick = { showDeleteDialog = false },
                ) { Text("Отмена") }
            },
        )
    }

    // #PLAYLIST-EDIT: диалог редактирования title/description (audio.editPlaylist).
    if (showEditDialog) {
        val pl = playlist
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!actionPending) showEditDialog = false },
            title = { Text("Изменить плейлист") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Название") },
                        singleLine = true,
                        enabled = !actionPending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Описание") },
                        enabled = !actionPending,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !actionPending && editTitle.isNotBlank(),
                    onClick = {
                        if (pl == null || actionPending) return@TextButton
                        actionPending = true
                        scope.launch {
                            try {
                                val ok = app.apiClient.audioEditPlaylist(
                                    ownerId = pl.ownerId,
                                    playlistId = pl.id,
                                    title = editTitle.trim(),
                                    description = editDescription.trim().ifBlank { null },
                                )
                                if (ok) {
                                    playlist = pl.copy(
                                        title = editTitle.trim(),
                                        description = editDescription.trim().ifBlank { null },
                                    )
                                    android.widget.Toast.makeText(
                                        app.applicationContext,
                                        "Сохранено",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        app.applicationContext,
                                        "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e("PlaylistDetailScreen", "edit failed", e)
                            } finally {
                                actionPending = false
                                showEditDialog = false
                            }
                        }
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !actionPending,
                    onClick = { showEditDialog = false },
                ) { Text("Отмена") }
            },
        )
    }
}

/**
 * #PLAYLIST-ACTIONS: вспомогательная кнопка-чип для follow/edit/delete.
 * IconButton + Text, VK-стиль (закруглённая, VK_CARD фон).
 */
@Composable
private fun PlaylistActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VK_CARD)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabled) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  Альбомы — поиск альбомов (audio.searchAlbums)
// ════════════════════════════════════════════════════════════════════

@Composable
fun MusicAlbumsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (ownerId: Long, albumId: Long, accessKey: String?) -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    var query by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<AudioPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    // #AUDIO-PAGING-ALL (волна 38): страничный поиск альбомов — догрузка по
    // скроллу до конца выдачи (раньше — одна страница на 30 результатов).
    var albumsNextFrom by remember { mutableStateOf<String?>(null) }
    var albumsLoadingMore by remember { mutableStateOf(false) }
    val albumsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMoreAlbums() {
        if (albumsLoadingMore || loading) return
        val cursor = albumsNextFrom ?: return
        val q = query
        if (q.isBlank()) return
        scope.launch {
            albumsLoadingMore = true
            try {
                val (nf, page) = app.apiClient.audioSearchAlbumsPage(query = q, count = 30, startFrom = cursor)
                albumsNextFrom = nf
                albums = (albums + page).distinctBy { "${it.ownerId}_${it.id}" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("MusicAlbumsScreen", "load more failed: ${e.message}")
            } finally {
                albumsLoadingMore = false
            }
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            albums = emptyList(); searched = false; albumsNextFrom = null; return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        loading = true
        try {
            val (nf, page) = app.apiClient.audioSearchAlbumsPage(query = query, count = 30)
            albums = page
            albumsNextFrom = nf
            searched = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicAlbumsScreen", "search failed: ${e.message}")
        } finally {
            loading = false
        }
    }

    // #AUDIO-PAGING-ALL: автодогрузка результатов поиска альбомов.
    LaunchedEffect(albumsListState) {
        snapshotFlow {
            val info = albumsListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 4) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMoreAlbums() }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar("Альбомы", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск альбомов…", color = VK_TEXT_SECONDARY, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = VK_TEXT_SECONDARY) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VK_CARD,
                unfocusedContainerColor = VK_CARD,
                focusedBorderColor = VK_ACCENT,
                unfocusedBorderColor = VK_CARD,
                cursorColor = VK_ACCENT,
                focusedTextColor = VK_TEXT_PRIMARY,
                unfocusedTextColor = VK_TEXT_PRIMARY,
            ),
        )
        when {
            query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Введите запрос для поиска альбомов", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VK_ACCENT)
            }
            searched && albums.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = albumsListState) {
                // Fix #MUSIC-CRASH-5: ключ с индексом — дубли ключей в LazyColumn
                // недопустимы (IllegalArgumentException → краш). Данные уже
                // дедуплицированы в audioSearchAlbumsPage, индекс — страховка.
                itemsIndexed(albums, key = { i, al -> "album_${i}_${al.ownerId}_${al.id}" }) { _, al ->
                    PlaylistRow(
                        title = al.title,
                        coverUrl = al.coverUrl,
                        subtitle = if (al.count > 0) "${al.count} треков" else null,
                        onClick = { onOpenAlbum(al.ownerId, al.id, al.accessKey) },
                    )
                }
                // #AUDIO-PAGING-ALL: футер догрузки альбомов.
                if (albumsLoadingMore) {
                    item(key = "albums_more") {
                        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VK_ACCENT)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Артисты и кураторы — поиск артистов (audio.searchArtists)
// ════════════════════════════════════════════════════════════════════

@Composable
fun MusicArtistsScreen(
    onBack: () -> Unit,
    onOpenArtist: (slug: String, name: String) -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    var query by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf<List<AudioArtist>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    // #AUDIO-PAGING-ALL (волна 38): страничный поиск артистов — догрузка по
    // скроллу до конца выдачи (раньше — одна страница на 30 результатов).
    var artistsNextFrom by remember { mutableStateOf<String?>(null) }
    var artistsLoadingMore by remember { mutableStateOf(false) }
    val artistsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMoreArtists() {
        if (artistsLoadingMore || loading) return
        val cursor = artistsNextFrom ?: return
        val q = query
        if (q.isBlank()) return
        scope.launch {
            artistsLoadingMore = true
            try {
                val (nf, page) = app.apiClient.audioSearchArtistsPage(query = q, count = 30, startFrom = cursor)
                artistsNextFrom = nf
                // Артисты из links[] приходят с id=0 — дедуп по имени/domain (Fix #281).
                artists = (artists + page).distinctBy { a -> a.domain?.takeIf { it.isNotBlank() } ?: a.name.lowercase() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("MusicArtistsScreen", "load more failed: ${e.message}")
            } finally {
                artistsLoadingMore = false
            }
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            artists = emptyList(); searched = false; artistsNextFrom = null; return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        loading = true
        try {
            val (nf, page) = app.apiClient.audioSearchArtistsPage(query = query, count = 30)
            artists = page
            artistsNextFrom = nf
            searched = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicArtistsScreen", "search failed: ${e.message}")
        } finally {
            loading = false
        }
    }

    // #AUDIO-PAGING-ALL: автодогрузка результатов поиска артистов.
    LaunchedEffect(artistsListState) {
        snapshotFlow {
            val info = artistsListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 4) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMoreArtists() }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar("Артисты и кураторы", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск артистов…", color = VK_TEXT_SECONDARY, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = VK_TEXT_SECONDARY) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VK_CARD,
                unfocusedContainerColor = VK_CARD,
                focusedBorderColor = VK_ACCENT,
                unfocusedBorderColor = VK_CARD,
                cursorColor = VK_ACCENT,
                focusedTextColor = VK_TEXT_PRIMARY,
                unfocusedTextColor = VK_TEXT_PRIMARY,
            ),
        )
        when {
            query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Введите запрос для поиска артистов", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VK_ACCENT)
            }
            searched && artists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = artistsListState) {
                // Fix #MUSIC-CRASH-2 (UI-сторона): ключ = domain ?: id → при ≥2
                // артистах без domain все ключи «0» → IllegalArgumentException → краш
                // («при поиске закрывается приложение»). Индекс гарантирует уникальность.
                itemsIndexed(artists, key = { i, a -> "artist_${i}_${a.domain ?: a.name}" }) { _, artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(artist.domain ?: "", artist.name) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(VK_CARD),
                            contentAlignment = Alignment.Center,
                        ) {
                            val cover = artist.coverUrl
                            if (cover != null) {
                                AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.MusicNote, null, tint = VK_ACCENT, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, color = VK_TEXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (artist.followers > 0) {
                                Text("${artist.followers} подписчиков", color = VK_TEXT_SECONDARY, fontSize = 12.sp)
                            }
                        }
                    }
                }
                // #AUDIO-PAGING-ALL: футер догрузки артистов.
                if (artistsLoadingMore) {
                    item(key = "artists_more") {
                        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VK_ACCENT)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Артист — треки + related (audio.getAudiosByArtist)
// ════════════════════════════════════════════════════════════════════

@Composable
fun ArtistDetailScreen(
    slug: String,
    name: String,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    var artist by remember { mutableStateOf<AudioArtist?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // #AUDIO-PAGING-ALL (волна 38): страничная догрузка треков артиста по
    // курсору next_from (раньше — одна страница на 100 треков, дальше — конец).
    var artistNextFrom by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loadingMore || loading) return
        val cursor = artistNextFrom ?: return
        scope.launch {
            loadingMore = true
            try {
                val (nf, page) = app.apiClient.audioGetAudiosByArtist(
                    slug = slug, name = name, count = 100, startFrom = cursor)
                artistNextFrom = nf
                val fresh = page
                    .filter { it.id > 0L && !it.url.isNullOrBlank() }
                    .filter { nv -> tracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                tracks = (tracks + fresh).distinctBy { "${it.ownerId}_${it.id}" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArtistDetailScreen", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(slug, name) {
        loading = true
        artistNextFrom = null
        try {
            // #MUSIC-PORT-FIX: artistId — slug артиста, name — имя для поиска.
            // Треки ищем через catalog.getAudioSearch (по имени), фильтруем по main_artists.
            // #AUDIO-PAGING-ALL: теперь метод возвращает курсор догрузки.
            val (nf, list) = app.apiClient.audioGetAudiosByArtist(slug = slug, name = name, count = 100)
            artistNextFrom = nf
            tracks = list.filter { it.id > 0L && !it.url.isNullOrBlank() }
            artist = AudioArtist(id = 0L, name = name.ifBlank { slug.trimStart('_').replace('_', ' ').ifBlank { "Артист" } })
            AppLog.i("ArtistDetailScreen", "Loaded ${tracks.size} tracks for artist $slug (nextFrom=${if (nf.isNullOrBlank()) "нет" else "есть"})")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ArtistDetailScreen", "load failed: ${e.message}")
        } finally {
            loading = false
        }
    }

    // #AUDIO-PAGING-ALL: автодогрузка при доскролле до конца.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 3) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar(artist?.name ?: "Артист", onBack)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VK_ACCENT)
            }
        } else if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Треки не найдены", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item {
                    AudioAttachmentList(tracks = tracks)
                }
                // #AUDIO-PAGING-ALL: футер догрузки треков артиста.
                if (loadingMore) {
                    item(key = "artist_tracks_more") {
                        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VK_ACCENT)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Общая строка плейлиста/альбома
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistRow(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(VK_CARD),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = VK_ACCENT, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = VK_TEXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = VK_TEXT_SECONDARY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  «Показать все» — полный список блока каталога
// ════════════════════════════════════════════════════════════════════

/**
 * #MUSIC-CATALOG-SHOW-ALL: экран полного списка блока каталога.
 * Грузит catalog.getSection(sectionId) и показывает все треки/плейлисты блока.
 */
@Composable
fun CatalogSectionScreen(
    sectionId: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlaylist: (ownerId: Long, playlistId: Long, accessKey: String?) -> Unit,
) {
    BackHandler { onBack() }
    val app = SovaApp.get()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<CatalogPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Волна 40 #SECTIONS-HONEST: повтор загрузки + честные причины пустоты
    // (офлайн/ошибка VK/нерендерящиеся блоки) вместо молчаливой пустоты.
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(sectionId, reloadTick) {
        loading = true
        errorText = null
        try {
            // #AUDIO-PAGING-ALL (волна 38): был catalogGetSectionById — только
            // ПЕРВАЯ страница секции («Показать все» обрезался). Теперь метод
            // догружает ВСЕ страницы секции по курсору start_from (кап 10 страниц
            // — страховка от вечного цикла).
            val blocks = app.apiClient.catalogGetSectionAllBlocks(sectionId)
            val allTracks = mutableListOf<Track>()
            val allPlaylists = mutableListOf<CatalogPlaylist>()
            blocks.forEach { b ->
                allTracks.addAll(b.tracks)
                allPlaylists.addAll(b.playlists)
            }
            tracks = allTracks
            playlists = allPlaylists
            AppLog.i("CatalogSectionScreen", "Loaded ${allTracks.size} tracks, ${allPlaylists.size} playlists")
            // #SECTIONS-HONEST (волна 40): секция, где после парсинга не осталось
            // ни треков, ни плейлистов (артисты/жанры/радио-блоки пока не
            // рендерятся, офлайн, ошибка API) — честная причина вместо пустоты.
            if (allTracks.isEmpty() && allPlaylists.isEmpty()) {
                errorText = if (app.apiClient.isOffline()) "Нет сети — раздел доступен онлайн"
                else (app.apiClient.lastApiErrorHuman() ?: "Раздел пуст или содержит элементы, которые пока не отображаются")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("CatalogSectionScreen", "load failed: ${e.message}")
            // #SECTIONS-HONEST: причина различается (офлайн vs ошибка).
            errorText = if (app.apiClient.isOffline()) "Нет сети — раздел доступен онлайн"
            else "Ошибка: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(VK_BLACK)) {
        LibraryTopBar(title, onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VK_ACCENT)
            }
            errorText != null && tracks.isEmpty() && playlists.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Волна 40 #SECTIONS-HONEST: причина + «Повторить» (раньше —
                    // только текст без действия).
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            errorText ?: "",
                            color = VK_TEXT_SECONDARY,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { reloadTick++ }) { Text("Повторить") }
                    }
                }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                if (tracks.isNotEmpty()) {
                    item { AudioAttachmentList(tracks = tracks) }
                }
                if (playlists.isNotEmpty()) {
                    items(playlists, key = { "${it.ownerId}_${it.id}" }) { pl ->
                        PlaylistRow(
                            title = pl.title,
                            coverUrl = pl.coverUrl,
                            subtitle = when {
                                pl.matchPercent != null -> "${pl.matchPercent}% совпадение"
                                pl.count > 0 -> "${pl.count} треков"
                                else -> null
                            },
                            onClick = { onOpenPlaylist(pl.ownerId, pl.id, pl.accessKey) },
                        )
                    }
                }
            }
        }
    }
}
