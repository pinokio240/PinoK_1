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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    LaunchedEffect(Unit) {
        loading = true
        errorText = null
        try {
            val (total, list) = app.apiClient.audioGetPlaylists(count = 50)
            playlists = list
            if (list.isEmpty()) {
                errorText = "Нет плейлистов"
            }
            AppLog.i("MusicPlaylistsScreen", "Loaded ${list.size} playlists (total=$total)")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicPlaylistsScreen", "load failed: ${e.message}")
            errorText = "Ошибка: ${e.message}"
        } finally {
            loading = false
        }
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
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { "${it.ownerId}_${it.id}" }) { pl ->
                    PlaylistRow(
                        title = pl.title,
                        coverUrl = pl.coverUrl,
                        subtitle = if (pl.count > 0) "${pl.count} треков" else null,
                        onClick = { onOpenPlaylist(pl.ownerId, pl.id, pl.accessKey) },
                    )
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

    LaunchedEffect(ownerId, playlistId, accessKey) {
        loading = true
        try {
            val (pl, plTracks) = app.apiClient.audioGetPlaylistById(
                playlistId = playlistId,
                ownerId = ownerId,
                accessKey = accessKey,
                count = 100,
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
                CircularProgressIndicator(color = VK_ACCENT)
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

    LaunchedEffect(query) {
        if (query.isBlank()) { albums = emptyList(); searched = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        loading = true
        try {
            albums = app.apiClient.audioSearchAlbums(query = query, count = 30)
            searched = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicAlbumsScreen", "search failed: ${e.message}")
        } finally {
            loading = false
        }
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
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(albums, key = { "${it.ownerId}_${it.id}" }) { al ->
                    PlaylistRow(
                        title = al.title,
                        coverUrl = al.coverUrl,
                        subtitle = if (al.count > 0) "${al.count} треков" else null,
                        onClick = { onOpenAlbum(al.ownerId, al.id, al.accessKey) },
                    )
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

    LaunchedEffect(query) {
        if (query.isBlank()) { artists = emptyList(); searched = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        loading = true
        try {
            artists = app.apiClient.audioSearchArtists(query = query, count = 30)
            searched = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicArtistsScreen", "search failed: ${e.message}")
        } finally {
            loading = false
        }
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
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(artists, key = { it.domain ?: it.id.toString() }) { artist ->
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

    LaunchedEffect(slug, name) {
        loading = true
        try {
            // #MUSIC-PORT-FIX: artistId — slug артиста, name — имя для поиска.
            // Треки ищем через catalog.getAudioSearch (по имени), фильтруем по main_artists.
            tracks = app.apiClient.audioGetAudiosByArtist(slug = slug, name = name, count = 100)
                .filter { it.id > 0L && !it.url.isNullOrBlank() }
            artist = AudioArtist(id = 0L, name = name.ifBlank { slug.trimStart('_').replace('_', ' ').ifBlank { "Артист" } })
            AppLog.i("ArtistDetailScreen", "Loaded ${tracks.size} tracks for artist $slug")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ArtistDetailScreen", "load failed: ${e.message}")
        } finally {
            loading = false
        }
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
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    AudioAttachmentList(tracks = tracks)
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

    LaunchedEffect(sectionId) {
        loading = true
        errorText = null
        try {
            val blocks = app.apiClient.catalogGetSectionById(sectionId)
            val allTracks = mutableListOf<Track>()
            val allPlaylists = mutableListOf<CatalogPlaylist>()
            blocks.forEach { b ->
                allTracks.addAll(b.tracks)
                allPlaylists.addAll(b.playlists)
            }
            tracks = allTracks
            playlists = allPlaylists
            AppLog.i("CatalogSectionScreen", "Loaded ${allTracks.size} tracks, ${allPlaylists.size} playlists")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("CatalogSectionScreen", "load failed: ${e.message}")
            errorText = "Ошибка: ${e.message}"
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
                    Text(errorText ?: "", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
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
