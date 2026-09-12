package re.pinok.ui.screens.bookmarks

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.data.model.Bookmark
import re.pinok.data.model.FaveTag
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.util.AppLog
import re.pinok.util.toDurationString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Закладки (fave.get) + локальные закладки треков.
 *
 * Волна 40 #BOOKMARKS-TRACKS (обратная связь тестера 2026-09-12):
 *  1. ТРЕКИ: у fave.* НЕТ аудио-раздела (#FAVE-AUDIO) — серверная закладка
 *     трека невозможна, поэтому треки закладываются ЛОКАЛЬНО
 *     (TrackBookmarksRepository → SovaPrefs) и показываются в отдельном
 *     разделе «Треки»: тап = воспроизведение очередью, корзина = удаление.
 *     Меню трека «В закладки» (MusicScreen/AudioPlayerScreen) пишет сюда.
 *  2. УДАЛЕНИЕ: кнопка-корзина ВИДИМА на каждой строке (раньше удаление было
 *     только скрытым long-press'ом — тестер о нём не знал: «Нет возможности
 *     удалить из закладок»), серверное удаление — через VKApiClient.bookmarkRemove:
 *     видео с access_key, ссылки по link_id, статьи/товары через
 *     fave.removeArticle/removeProduct (раньше — заглушки), посты/люди/группы/фото.
 */

/** Уникальный ключ закладки для дедупликации и LazyColumn keys. */
private fun bookmarkKey(bm: Bookmark): String =
    "${bm.type}_${bm.user?.id ?: bm.group?.id ?: bm.post?.id ?: bm.photo?.id ?: bm.video?.id ?: bm.linkId ?: bm.objectId ?: bm.link?.url ?: "id"}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onUserClick: ((userId: Long) -> Unit)? = null,
    onGroupClick: ((groupId: Long) -> Unit)? = null,
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onVideoClick: ((ownerId: Long, videoId: Long) -> Unit)? = null,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Fix #83: пагинация закладок + pull-to-refresh.
    val pageSize = 30
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Волна 40 #BOOKMARKS-TRACKS: раздел экрана — 0 = закладки VK (fave.get),
    // 1 = локальные закладки треков (TrackBookmarksRepository).
    var section by remember { mutableStateOf(0) }
    val bookmarkTracks by app.trackBookmarksRepository.tracks.collectAsState()

    // S6-2: теги закладок — фильтрация.
    var tags by remember { mutableStateOf<List<FaveTag>>(emptyList()) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }

    // S6-3 + волна 40: цель удаления — серверная закладка ИЛИ локальный трек.
    var removeTarget by remember { mutableStateOf<Bookmark?>(null) }
    var removeTrackTarget by remember { mutableStateOf<Track?>(null) }

    // Загрузка тегов (один раз).
    LaunchedEffect(Unit) {
        try {
            tags = app.apiClient.faveGetTagList()
        } catch (_: Exception) {}
    }

    // Загрузка закладок — перезапускается при смене тега; только в разделе «Закладки VK».
    LaunchedEffect(selectedTagId, section) {
        if (section != 0) return@LaunchedEffect
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val list = app.apiClient.faveGet(count = pageSize, tagId = selectedTagId)
                // Fix #53: защитная дедупликация — LazyColumn keys должны быть уникальны.
                bookmarks = list.distinctBy { bookmarkKey(it) }
                if (list.size < pageSize) endReached = true
                AppLog.i("BookmarksScreen", "Loaded ${list.size} bookmarks (tag=$selectedTagId)")
                if (list.isEmpty()) {
                    // #BOOKMARKS-FIX (2026-09-12): вместо сырой английской VK-ошибки
                    // («Access denied…» — «пишет какую-то ошибку на англ») — понятная
                    // русская причина с кодом; сырое значение остаётся в AppLog.
                    val human = app.apiClient.lastApiErrorHuman()
                    errorText = if (human != null) "Закладки не загрузились: $human" else "Нет закладок"
                }
            } catch (e: Exception) {
                AppLog.e("BookmarksScreen", "Failed to load bookmarks", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #83: pull-to-refresh — перезагрузка первой страницы.
    fun refreshBookmarks() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.faveGet(count = pageSize, tagId = selectedTagId)
                bookmarks = list.distinctBy { bookmarkKey(it) }
                endReached = (list.size < pageSize)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("BookmarksScreen", "refreshBookmarks failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #83: пагинация — подгрузка следующих закладок через offset.
    fun loadMoreBookmarks() {
        if (loadingMore || endReached || bookmarks.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = bookmarks.size
                val page = app.apiClient.faveGet(count = pageSize, offset = offset, tagId = selectedTagId)
                    .filter { np ->
                        val npKey = bookmarkKey(np)
                        bookmarks.none { bookmarkKey(it) == npKey }
                    }
                if (page.isNotEmpty()) {
                    bookmarks = (bookmarks + page).distinctBy { bookmarkKey(it) }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("BookmarksScreen", "loadMoreBookmarks failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #83: бесконечная пагинация — триггер при скролле к концу.
    LaunchedEffect(listState, bookmarks.size, section) {
        if (section != 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= bookmarks.size - 3 && bookmarks.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreBookmarks() }
    }

    // S6-3 + волна 40 #BOOKMARKS-REMOVE-ALL: удаление серверной закладки через
    // bookmarkRemove (видео с access_key, ссылки по link_id, статьи/товары —
    // раньше всё это были заглушки или падали с VK-ошибкой).
    fun removeBookmark(bm: Bookmark) {
        scope.launch {
            val (ok, err) = app.apiClient.bookmarkRemove(bm)
            if (ok) {
                bookmarks = bookmarks.filter { bookmarkKey(it) != bookmarkKey(bm) }
                AppLog.i("BookmarksScreen", "Removed bookmark: type=${bm.type}")
                Toast.makeText(context, "Удалено из закладок", Toast.LENGTH_SHORT).show()
            } else {
                // NULL-ЯВНО: err nullable — явный if вместо ?:.
                val shown = if (err != null) ": $err" else ""
                Toast.makeText(context, "Не удалось удалить из закладок$shown", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Волна 40 #BOOKMARKS-TRACKS: удаление локальной закладки трека.
    fun removeBookmarkedTrack(track: Track) {
        scope.launch {
            val removed = try {
                app.trackBookmarksRepository.remove(track)
            } catch (e: Exception) {
                AppLog.e("BookmarksScreen", "track bookmark remove failed", e)
                false
            }
            val msg = if (removed) "Трек удалён из закладок" else "Не удалось удалить трек из закладок"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Волна 40 #BOOKMARKS-TRACKS: воспроизведение локальных закладок очередью.
    // Трек без URL (стейл-ссылка VK истекла) резолвится перед стартом через
    // audioGetById (API + al_audio web-fallback) — паттерн #AUDIO-COMMENTS
    // (AudioAttachmentList, волна 37). Хвост очереди без URL отфильтрует
    // playTrackList (Fix #59), startIndex ремапится по id.
    fun playBookmarkedTracks(list: List<Track>, index: Int) {
        scope.launch {
            val tapped = list[index]
            val tappedUrl = tapped.url
            if (!tappedUrl.isNullOrBlank()) {
                PlayerConnection.playTrackList(list, startIndex = index)
            } else {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { app.apiClient.audioGetById(tapped) }.getOrNull()
                }
                // NULL-ЯВНО: audioGetById опционален по контракту.
                val resolvedUrl = resolved?.url
                if (resolved != null && !resolvedUrl.isNullOrBlank()) {
                    PlayerConnection.playTrackList(
                        list.mapIndexed { i, t -> if (i == index) resolved else t },
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

    // S6-3: AlertDialog подтверждения удаления серверной закладки.
    removeTarget?.let { bm ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Удалить из закладок?") },
            text = {
                Text(
                    text = bm.title.ifBlank { typeLabel(bm.type) },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    removeBookmark(bm)
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    // Волна 40: AlertDialog подтверждения удаления локальной закладки трека.
    removeTrackTarget?.let { tr ->
        AlertDialog(
            onDismissRequest = { removeTrackTarget = null },
            title = { Text("Удалить трек из закладок?") },
            text = {
                Text(
                    text = "${tr.title} — ${tr.artist}",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeTrackTarget = null
                    removeBookmarkedTrack(tr)
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTrackTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Волна 40 #BOOKMARKS-TRACKS: переключатель разделов — «Закладки VK» /
        // «Треки». Треки — локальные (у fave.* нет аудио-раздела), поэтому
        // отдельный раздел вместо подмешивания в серверный список.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionChip(
                label = "Закладки VK",
                selected = section == 0,
                onClick = { section = 0 },
                modifier = Modifier.weight(1f),
            )
            SectionChip(
                label = "Треки",
                selected = section == 1,
                onClick = { section = 1 },
                modifier = Modifier.weight(1f),
            )
        }

        if (section == 0) {
            // ─── Раздел «Закладки VK» (fave.get) ───────────────────────────
            // S6-2: горизонтальная строка фильтров по тегам.
            if (tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        TagFilterChip(
                            label = "Все",
                            selected = selectedTagId == null,
                            onClick = { selectedTagId = null },
                        )
                    }
                    items(tags, key = { it.id }) { tag ->
                        TagFilterChip(
                            label = tag.name,
                            selected = selectedTagId == tag.id,
                            onClick = { selectedTagId = tag.id },
                        )
                    }
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorText ?: "Нет закладок",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Fix #83: PullToRefreshBox — pull-to-refresh закладок.
                // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх».
                Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { refreshBookmarks() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(bookmarks, key = { "${bookmarkKey(it)}_${it.addedDate}" }) { bm ->
                            BookmarkRow(
                                bm = bm,
                                onLongClick = { removeTarget = bm },
                                onRemoveClick = { removeTarget = bm },
                                onClick = {
                                    when (bm.type) {
                                        "post" -> bm.post?.let { p ->
                                            onPostClick?.invoke(p.ownerId, p.id)
                                        }
                                        "user", "profile" -> bm.user?.let { u ->
                                            onUserClick?.invoke(u.id)
                                        }
                                        "group" -> bm.group?.let { g ->
                                            onGroupClick?.invoke(g.id)
                                        }
                                        "video" -> bm.video?.let { v ->
                                            onVideoClick?.invoke(v.ownerId, v.id)
                                        }
                                        "link" -> {
                                            val url = bm.link?.url
                                            if (!url.isNullOrBlank()) {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(url),
                                                )
                                                context.startActivity(intent)
                                            }
                                        }
                                        else -> {
                                            Toast.makeText(context, "Тип ${bm.type} — просмотр в разработке, удаление доступно по корзине", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth().height(1.dp)
                                    .padding(horizontal = 76.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            )
                        }
                        // Fix #83: футер пагинации.
                        item {
                            when {
                                loadingMore -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                                endReached -> {
                                    Text(
                                        text = "Это все закладки",
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх» над закладками
                // (пагинация fave.get offset — loadMoreBookmarks).
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
                }
            }
        } else {
            // ─── Раздел «Треки» (локальные закладки, TrackBookmarksRepository) ──
            if (bookmarkTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Нет треков в закладках",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Откройте меню трека в Музыке и выберите «В закладки»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(bookmarkTracks, key = { _, t -> "track_${t.ownerId}_${t.id}" }) { index, tr ->
                        BookmarkedTrackRow(
                            track = tr,
                            onClick = { playBookmarkedTracks(bookmarkTracks, index) },
                            onRemoveClick = { removeTrackTarget = tr },
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().height(1.dp)
                                .padding(horizontal = 76.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    item {
                        Text(
                            text = "Локальные закладки треков — хранятся в приложении",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bm: Bookmark,
    onLongClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val icon = iconForType(bm.type)
    val thumb = bm.thumbUrl
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = bm.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bm.title.ifBlank { typeLabel(bm.type) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${typeLabel(bm.type)} • ${formatDate(bm.addedDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // Волна 40 #BOOKMARKS-REMOVE-ALL: ВИДИМАЯ кнопка удаления (раньше было
        // только скрытым long-press'ом — тестер о нём не знал).
        IconButton(onClick = onRemoveClick) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Удалить из закладок",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Волна 40 #BOOKMARKS-TRACKS: строка локальной закладки-трека (тап = играет). */
@Composable
private fun BookmarkedTrackRow(
    track: Track,
    onClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = track.albumThumb
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} • ${track.duration.toDurationString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemoveClick) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Удалить трек из закладок",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Волна 40: чип переключателя разделов («Закладки VK» / «Треки»). */
@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** S6-2: чип фильтра тегов закладок. */
@Composable
private fun TagFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private fun iconForType(type: String): ImageVector = when (type) {
    "user", "profile" -> Icons.Outlined.Person
    "group" -> Icons.Outlined.Group
    "photo" -> Icons.Outlined.InsertPhoto
    "video" -> Icons.Outlined.PlayCircle
    "link" -> Icons.Outlined.Link
    "article" -> Icons.AutoMirrored.Outlined.Article
    "product" -> Icons.Outlined.ShoppingBag
    else -> Icons.Outlined.Favorite
}

private fun typeLabel(type: String): String = when (type) {
    "user", "profile" -> "Пользователь"
    "group" -> "Сообщество"
    "post" -> "Запись"
    "photo" -> "Фото"
    "video" -> "Видео"
    "link" -> "Ссылка"
    "article" -> "Статья"
    "product" -> "Товар"
    "page" -> "Страница"
    else -> type
}

private fun formatDate(epochSec: Long): String {
    if (epochSec == 0L) return ""
    return SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(epochSec * 1000))
}
