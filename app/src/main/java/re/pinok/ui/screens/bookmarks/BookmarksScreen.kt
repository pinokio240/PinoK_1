package re.pinok.ui.screens.bookmarks

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import re.pinok.ui.components.PostCard as UnifiedPostCard
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
// P0.45-TRACER B6: prefix 'u'/'g' чтобы избежать коллизии user_id vs group_id
// (оба могут быть 12345 — user 12345 и group 12345 в разных namespaces).
private fun bookmarkKey(bm: Bookmark): String =
    "${bm.type}_${bm.user?.let { "u${it.id}" } ?: bm.group?.let { "g${it.id}" } ?: bm.post?.id ?: bm.photo?.id ?: bm.video?.id ?: bm.linkId ?: bm.objectId ?: bm.link?.url ?: "id"}"

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

    // #BOOKMARKS-TYPE-TABS (2026-10-19): вкладки-фильтры по типу закладки.
    // Значение — ключ вкладки: "all" / "post" / "video" / "people" / "groups" / "clip".
    var typeFilter by remember { mutableStateOf("all") }

    // #BOOKMARKS-PEOPLE-GROUPS (2026-10-19): люди и группы НЕ приходят в общем
    // списке fave.get (там только post/video/link/clip — см. закладки.har). VK API
    // отдаёт их ТОЛЬКО при item_type="page", причём ОДНИМ потоком: человек (id>0) и
    // сообщество (id<0) различаются только ЗНАКОМ id (эталон веб-Закладки_люди/группы,
    // закладки.har). Поэтому держим ЕДИНЫЙ поток страниц pageBookmarks
    // (item_type=page, пагинация по offset), из которого вкладки «Люди»/«Сообщества»
    // фильтруются по знаку, а общий bookmarks обслуживает all/post/video/clip.
    // «Все закладки» = общий список + поток страниц (люди и группы видны и там).
    var pageBookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var pageLoading by remember { mutableStateOf(false) }
    var pageErrorText by remember { mutableStateOf<String?>(null) }
    var pageEndReached by remember { mutableStateOf(false) }

    // Текущий отображаемый список и его состояния зависят от активной вкладки.
    // people — пользователи (страницы с id>0), groups — сообщества (страницы с
    // id<0); post/video/clip — локальная фильтрация общего списка; all — общий
    // список + весь поток страниц.
    val currentList: List<Bookmark> = when (typeFilter) {
        "people" -> pageBookmarks.filter { it.user != null }
        "groups" -> pageBookmarks.filter { it.group != null }
        "post" -> bookmarks.filter { it.type == "post" }
        "video" -> bookmarks.filter { it.type == "video" }
        "clip" -> bookmarks.filter { it.type == "clip" }
        else -> (bookmarks + pageBookmarks).distinctBy { bookmarkKey(it) }
    }
    val currentLoading: Boolean = when (typeFilter) {
        "people", "groups" -> pageLoading
        else -> loading
    }
    val currentErrorText: String? = when (typeFilter) {
        "people", "groups" -> pageErrorText
        else -> errorText
    }
    val currentEndReached: Boolean = when (typeFilter) {
        "people", "groups" -> pageEndReached
        else -> endReached
    }

    // Загрузка тегов (один раз).
    LaunchedEffect(Unit) {
        try {
            tags = app.apiClient.faveGetTagList()
        } catch (_: Exception) {}
    }

    suspend fun loadBookmarksFirstPage(tagId: Long?) {
        loading = true
        endReached = false
        errorText = null
        try {
            val list = app.apiClient.faveGet(count = pageSize, tagId = tagId)
            bookmarks = list.distinctBy { bookmarkKey(it) }
            if (list.size < pageSize) endReached = true
            AppLog.i("BookmarksScreen", "Loaded ${list.size} bookmarks (tag=$tagId)")
            if (list.isEmpty()) {
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

    // #BOOKMARKS-PEOPLE-GROUPS: серверная загрузка потока страниц (item_type=page).
    // VK API не разделяет людей/сообщества фильтром — они приходят одним потоком и
    // разделяются знаком id (id>0 человек, id<0 группа). Вкладки «Люди»/«Сообщества»
    // фильтруют этот поток; «Все закладки» добавляют его к общему списку.
    // P0.45-bookmarks-legacy: fave.get(item_type=page) возвращает 0 items —
    // users/groups хранятся в legacy al_bookmarks.php. Загружаем ОБА типа
    // (user + group) и объединяем.
    suspend fun loadLegacyPages(offset: Int): Pair<List<Bookmark>, Boolean> {
        val (users, usersHasMore) = app.apiClient.bookmarksGetLegacyPages("user", offset = offset)
        val (groups, groupsHasMore) = app.apiClient.bookmarksGetLegacyPages("group", offset = offset)
        val combined = (users + groups).distinctBy { bookmarkKey(it) }
        val hasMore = usersHasMore || groupsHasMore
        return combined to hasMore
    }

    suspend fun loadPageFirstPage(tagId: Long?) {
        pageLoading = true
        pageEndReached = false
        pageErrorText = null
        try {
            val (list, hasMore) = loadLegacyPages(offset = 0)
            pageBookmarks = list
            pageEndReached = !hasMore
            AppLog.i("BookmarksScreen", "Loaded ${list.size} pages via legacy (people/groups, tag=$tagId)")
            if (list.isEmpty()) {
                pageErrorText = "Нет закладок людей и сообществ"
            }
        } catch (e: Exception) {
            AppLog.e("BookmarksScreen", "Failed to load pages", e)
            pageErrorText = "Ошибка: ${e.message}"
        } finally {
            pageLoading = false
        }
    }

    // Загрузка закладок — перезапускается при смене тега или вкладки; только
    // в разделе «Закладки VK». Стоит ПОСЛЕ load*FirstPage (локальные функции
    // не поддерживают forward reference в теле composable).
    LaunchedEffect(selectedTagId, section, typeFilter) {
        if (section != 0) return@LaunchedEffect
        // Загружаем секцию только если она ещё пуста (не перезагружаем уже
        // загруженный список при повторном переключении вкладки).
        when (typeFilter) {
            // people/groups — один поток страниц; all — общий список + поток страниц,
            // поэтому грузим обе секции, если они ещё пусты.
            "people", "groups" -> if (pageBookmarks.isEmpty()) loadPageFirstPage(selectedTagId)
            else -> {
                if (bookmarks.isEmpty()) loadBookmarksFirstPage(selectedTagId)
                if (pageBookmarks.isEmpty()) loadPageFirstPage(selectedTagId)
            }
        }
    }

    // Fix #83: pull-to-refresh — перезагрузка первой страницы текущей вкладки.
    // Для «Все закладки» обновляем и общий список, и поток страниц (люди/группы).
    fun refreshBookmarks() {
        scope.launch {
            isRefreshing = true
            when (typeFilter) {
                "people", "groups" -> {
                    try {
                        // P0.45-bookmarks-legacy: use al_bookmarks.php instead of fave.get(item_type=page)
                        val (list, hasMore) = loadLegacyPages(offset = 0)
                        pageBookmarks = list
                        pageEndReached = !hasMore
                        pageErrorText = null
                    } catch (e: Exception) {
                        AppLog.w("BookmarksScreen", "refreshPages failed: ${e.message}")
                    }
                }
                "all" -> {
                    try {
                        val list = app.apiClient.faveGet(count = pageSize, tagId = selectedTagId)
                        bookmarks = list.distinctBy { bookmarkKey(it) }
                        endReached = (list.size < pageSize)
                        errorText = null
                    } catch (e: Exception) {
                        AppLog.w("BookmarksScreen", "refreshBookmarks failed: ${e.message}")
                    }
                    try {
                        // P0.45-bookmarks-legacy: use al_bookmarks.php instead of fave.get(item_type=page)
                        val (pages, hasMore) = loadLegacyPages(offset = 0)
                        pageBookmarks = pages
                        pageEndReached = !hasMore
                        pageErrorText = null
                    } catch (e: Exception) {
                        AppLog.w("BookmarksScreen", "refreshPages failed: ${e.message}")
                    }
                }
                else -> {
                    try {
                        val list = app.apiClient.faveGet(count = pageSize, tagId = selectedTagId)
                        bookmarks = list.distinctBy { bookmarkKey(it) }
                        endReached = (list.size < pageSize)
                        errorText = null
                    } catch (e: Exception) {
                        AppLog.w("BookmarksScreen", "refreshBookmarks failed: ${e.message}")
                    }
                }
            }
            isRefreshing = false
        }
    }

    // Fix #83: пагинация — подгрузка следующих закладок общего списка.
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

    // #BOOKMARKS-PEOPLE-GROUPS: пагинация потока страниц (legacy al_bookmarks.php).
    fun loadMorePages() {
        if (loadingMore || pageEndReached || pageBookmarks.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                // P0.45-bookmarks-legacy: use al_bookmarks.php for pagination.
                // Offset = count of users and groups separately (legacy pagination is per-type).
                val userOffset = pageBookmarks.count { it.user != null }
                val groupOffset = pageBookmarks.count { it.group != null }
                val (page, hasMore) = loadLegacyPages(offset = maxOf(userOffset, groupOffset))
                val newPages = page.filter { np ->
                    val npKey = bookmarkKey(np)
                    pageBookmarks.none { bookmarkKey(it) == npKey }
                }
                if (newPages.isNotEmpty()) {
                    pageBookmarks = (pageBookmarks + newPages).distinctBy { bookmarkKey(it) }
                }
                pageEndReached = !hasMore
            } catch (e: Exception) {
                AppLog.w("BookmarksScreen", "loadMorePages failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // #BOOKMARKS-PEOPLE-GROUPS: «Все закладки» — пагинация ОБОИХ потоков
    // (общий список + страницы), чтобы люди и группы подгружались и в «Все».
    fun loadMoreAll() {
        if (loadingMore) return
        if (endReached && pageEndReached) return
        scope.launch {
            loadingMore = true
            try {
                if (!endReached && bookmarks.isNotEmpty()) {
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
                }
                if (!pageEndReached && pageBookmarks.isNotEmpty()) {
                    // P0.45-bookmarks-legacy: use al_bookmarks.php for pagination.
                    val userOffset = pageBookmarks.count { it.user != null }
                    val groupOffset = pageBookmarks.count { it.group != null }
                    val offset = maxOf(userOffset, groupOffset)
                    val (page, hasMore) = loadLegacyPages(offset = offset)
                    val newPages = page.filter { np ->
                        val npKey = bookmarkKey(np)
                        pageBookmarks.none { bookmarkKey(it) == npKey }
                    }
                    if (newPages.isNotEmpty()) {
                        pageBookmarks = (pageBookmarks + newPages).distinctBy { bookmarkKey(it) }
                    }
                    pageEndReached = !hasMore
                }
            } catch (e: Exception) {
                AppLog.w("BookmarksScreen", "loadMoreAll failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Текущая стратегия подгрузки следующей страницы зависит от активной вкладки.
    // Объявлена ПОСЛЕ loadMore* — локальные функции вызываются только после
    // объявления (forward reference в теле composable не работает).
    fun currentMore() = when (typeFilter) {
        "all" -> loadMoreAll()
        "people", "groups" -> loadMorePages()
        else -> loadMoreBookmarks()
    }

    // Fix #83: бесконечная пагинация — триггер при скролле к концу текущей вкладки.
    LaunchedEffect(listState, currentList.size, section, typeFilter) {
        if (section != 0) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= currentList.size - 3 && currentList.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { currentMore() }
    }

    // S6-3 + волна 40 #BOOKMARKS-REMOVE-ALL: удаление серверной закладки через
    // bookmarkRemove (видео с access_key, ссылки по link_id, статьи/товары —
    // раньше всё это были заглушки или падали с VK-ошибкой).
    fun removeBookmark(bm: Bookmark) {
        scope.launch {
            val (ok, err) = app.apiClient.bookmarkRemove(bm)
            if (ok) {
                // Удаляем из всех списков (общий + поток страниц) по ключу.
                bookmarks = bookmarks.filter { bookmarkKey(it) != bookmarkKey(bm) }
                pageBookmarks = pageBookmarks.filter { bookmarkKey(it) != bookmarkKey(bm) }
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
        // #BOOKMARKS-TYPE-TABS (2026-10-19): единый ряд вкладок закладок —
        // «Треки» (раздел локальных треков, section=1) и фильтры по типу
        // серверных закладок (Все закладки/Посты/Видео/Люди/Сообщества,
        // section=0; эталон веб-версии fave.get). Заменяет прежний
        // переключатель «Закладки VK»/«Треки».
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TagFilterChip(
                    label = "Все закладки",
                    selected = section == 0 && typeFilter == "all",
                    onClick = { section = 0; typeFilter = "all" },
                )
            }
            item {
                TagFilterChip(
                    label = "Посты",
                    selected = section == 0 && typeFilter == "post",
                    onClick = { section = 0; typeFilter = "post" },
                )
            }
            item {
                TagFilterChip(
                    label = "Видео",
                    selected = section == 0 && typeFilter == "video",
                    onClick = { section = 0; typeFilter = "video" },
                )
            }
            item {
                TagFilterChip(
                    label = "Люди",
                    selected = section == 0 && typeFilter == "people",
                    onClick = { section = 0; typeFilter = "people" },
                )
            }
            item {
                TagFilterChip(
                    label = "Сообщества",
                    selected = section == 0 && typeFilter == "groups",
                    onClick = { section = 0; typeFilter = "groups" },
                )
            }
            item {
                TagFilterChip(
                    label = "Клипы",
                    selected = section == 0 && typeFilter == "clip",
                    onClick = { section = 0; typeFilter = "clip" },
                )
            }
            item {
                TagFilterChip(
                    label = "Треки",
                    selected = section == 1,
                    onClick = { section = 1 },
                )
            }
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

            if (currentLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = currentErrorText ?: "Нет закладок",
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
                        items(currentList, key = { "${bookmarkKey(it)}_${it.addedDate}" }) { bm ->
                            BookmarkRow(
                                bm = bm,
                                onLongClick = { removeTarget = bm },
                                onRemoveClick = { removeTarget = bm },
                                onPostContextClick = bm.post?.let { p ->
                                    {
                                        if (p.ownerId > 0) onUserClick?.invoke(p.ownerId)
                                        else onGroupClick?.invoke(-p.ownerId)
                                    }
                                },
                                onDeletePost = bm.post?.let { p ->
                                    {
                                        scope.launch {
                                            try {
                                                val ok = app.apiClient.wallDelete(p.ownerId, p.id)
                                                if (ok) {
                                                    removeBookmark(bm)
                                                    Toast.makeText(context, "Пост удалён", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Не удалось удалить пост", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    when (bm.type) {
                                        "post" -> bm.post?.let { p ->
                                            onPostClick?.invoke(p.ownerId, p.id)
                                        }
                                        "user", "profile", "page" -> bm.user?.let { u ->
                                            onUserClick?.invoke(u.id)
                                        } ?: bm.group?.let { g ->
                                            onGroupClick?.invoke(g.id)
                                        }
                                        "group" -> bm.group?.let { g ->
                                            onGroupClick?.invoke(g.id)
                                        }
                                        "video", "clip" -> bm.video?.let { v ->
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
                                currentEndReached -> {
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

/**
 * Карточка закладки: диспетчер по типу. Для «богатых» типов (видео/пост) —
 * типизированные карточки, для остальных — плоская строка [BookmarkGenericRow].
 * Сигнатура вызова не менялась (bm, onLongClick, onRemoveClick, onClick).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bm: Bookmark,
    onLongClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {},
    onClick: () -> Unit = {},
    // AUTHOR-STRIP: полоска «Перейти к посту». Передаётся только в BookmarkPostCard
    // (для остальных типов закладок игнорируется). null → полоска не рисуется.
    onPostContextClick: (() -> Unit)? = null,
    // WALL-DELETE: удаление поста (wall.delete) — только для постов с canDelete=true.
    onDeletePost: (() -> Unit)? = null,
) {
    // #BOOKMARKS-SMARTCAST: свойства Bookmark (video/post/user/group/link) — public
    // из core/data, поэтому smart-cast невозможен; берём в локальные val (nullable).
    val bmV = bm.video
    val bmP = bm.post
    val bmU = bm.user
    val bmG = bm.group
    val bmL = bm.link
    when (bm.type) {
        "video", "clip" -> if (bmV != null) {
            BookmarkVideoCard(
                bm = bm,
                video = bmV,
                onLongClick = onLongClick,
                onRemoveClick = onRemoveClick,
                onClick = onClick,
            )
        } else {
            BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        "post" -> if (bmP != null) {
            BookmarkPostCard(
                bm = bm,
                post = bmP,
                onLongClick = onLongClick,
                onRemoveClick = onRemoveClick,
                onClick = onClick,
                onPostContextClick = onPostContextClick,
                onDeletePost = onDeletePost,
            )
        } else {
            BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        "user", "profile" -> if (bmU != null) {
            BookmarkUserRow(bm, bmU, onLongClick, onRemoveClick, onClick)
        } else {
            BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        "group" -> if (bmG != null) {
            BookmarkGroupRow(bm, bmG, onLongClick, onRemoveClick, onClick)
        } else {
            BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        // #BOOKMARKS-CLIPS_TABS: type="page" — авторы клипов (fave.addPage),
        // распознаются эвристикой парсера в user/group. Рендерим как
        // пользователя/сообщество, иначе они показывались как generic-строки.
        "page" -> when {
            bmU != null -> BookmarkUserRow(bm, bmU, onLongClick, onRemoveClick, onClick)
            bmG != null -> BookmarkGroupRow(bm, bmG, onLongClick, onRemoveClick, onClick)
            else -> BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        "link" -> if (bmL != null) {
            BookmarkLinkRow(bm, bmL, onLongClick, onRemoveClick, onClick)
        } else {
            BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
        }
        else -> BookmarkGenericRow(bm, onLongClick, onRemoveClick, onClick)
    }
}

/**
 * Богатая видео-карточка (эталон VK web Закладки_видео): обложка 16:9,
 * бейдж длительности, название, просмотры, дата добавления.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkVideoCard(
    bm: Bookmark,
    video: re.pinok.data.model.Video,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
) {
    val cover = bm.thumbUrl
    Column(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Обложка 16:9 с бейджем-длительностью в правом нижнем углу.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Бейдж длительности «2:42» в углу обложки (эталон video_thumb_label_duration).
            val durationText = if (video.duration > 0) video.duration.toDurationString() else ""
            if (durationText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title.ifBlank { typeLabel(bm.type) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // «19 415 просмотров» — video.views; затем дата добавления.
                val viewsText = if (video.views > 0) "${formatCount(video.views)} просмотров" else ""
                val meta = listOf(viewsText, formatDate(bm.addedDate))
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
}

/**
 * Полная пост-карточка (эталон VK web Закладки_посты): аватар+автор+время,
 * текст, счётчики (лайки/репосты/комментарии/просмотры), строка репоста.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkPostCard(
    bm: Bookmark,
    post: re.pinok.data.model.Post,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
    // AUTHOR-STRIP: полоска «Перейти к посту» — клик открывает стену (ownerId).
    // null → полоска не рисуется (вызывающий может скрыть).
    onPostContextClick: (() -> Unit)? = null,
    // WALL-DELETE: удаление поста (wall.delete) — только если canDelete=true.
    onDeletePost: (() -> Unit)? = null,
) {
    // NULL-ЯВНО: автор поста в закладке — из postAuthor/postGroup (fill через
    // fave.get extended profiles/groups), fallback на bm.user (легаси).
    val postAuthor = bm.postAuthor
    val postGroup = bm.postGroup
    val fallbackUser = bm.user
    val author = if (postAuthor != null) postAuthor else fallbackUser
    // Имя автора: группа-источник → имя группы; пользователь → fullName; else title.
    val authorIsGroup = postGroup != null || post.fromId < 0
    val authorName = when {
        postGroup != null -> postGroup.name
        postAuthor != null -> postAuthor.fullName
        author != null -> author.fullName
        bm.title.isNotBlank() -> bm.title
        else -> typeLabel(bm.type)
    }
    val authorPhoto = if (author != null) author.photo100 else postGroup?.photo100
    // POSTCARD-REFACTOR-PLAN (этап 3f): BookmarkPostCard — обёртка-делегат
    // единого PostCard. Закладка на карточке (isBookmarked=true) снимает её
    // (onToggleBookmark = onRemoveClick). Открытие поста/вложений — onClick.
    UnifiedPostCard(
        post = post,
        authorName = authorName,
        authorPhoto = authorPhoto,
        authorIsGroup = authorIsGroup,
        signerName = null,
        // Полная дата (закладка показывает абсолютное время поста).
        showFullDate = true,
        // Сама запись — уже закладка: видимая иконка-закладка снимает её.
        isBookmarked = true,
        onToggleBookmark = onRemoveClick,
        verified = false,
        // В закладках клик по автору тоже ведёт в пост (как остальное тело).
        onAuthorClick = onClick,
        onPostClick = { onClick() },
        // AUTHOR-STRIP: проброс клика полоски «Перейти к посту» в единый PostCard.
        onPostContextClick = onPostContextClick,
        onVideoClick = { onClick() },
        onPhotoClick = { _, _ -> onClick() },
        onLikeToggle = {},
        onCommentClick = {},
        onRepostClick = {},
        onReaction = null,
        likesOverride = null,
        reactionOverride = null,
        // BOOKMARK-MENU: «⋮»-меню — Удалить из закладок, Скопировать ссылку, Удалить
        menuItems = run {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            buildList {
                add(re.pinok.ui.components.PostMenuItem(
                    label = "Удалить из закладок",
                    icon = androidx.compose.material.icons.Icons.Outlined.BookmarkBorder,
                    onClick = onRemoveClick,
                    destructive = true,
                ))
                add(re.pinok.ui.components.PostMenuItem(
                    label = "Скопировать ссылку",
                    icon = androidx.compose.material.icons.Icons.Outlined.Link,
                    onClick = {
                        val url = "https://vk.com/wall${post.ownerId}_${post.id}"
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ссылка", url))
                        Toast.makeText(ctx, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    },
                ))
                // «Удалить» — только если пост от текущего пользователя или админ сообщества
                val canDelete = post.canDelete == true
                if (canDelete && onDeletePost != null) {
                    add(re.pinok.ui.components.PostMenuItem(
                        label = "Удалить",
                        icon = androidx.compose.material.icons.Icons.Outlined.Delete,
                        onClick = { onDeletePost() },
                        destructive = true,
                    ))
                }
            }
        },
        showPinnedBadge = false,
        showAdBadge = false,
        showSubscribeButton = false,
        textMaxLines = 6,
        carouselEnabled = true,
        showViews = (post.views?.count ?: 0) > 0,
        myUserId = 0L,
    )
}

/** Строка закладки-пользователя: аватар 48dp, имя, точка-онлайн. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkUserRow(
    bm: Bookmark,
    user: re.pinok.data.model.UserProfile,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            val photo = user.photo100
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = user.fullName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.fullName.ifBlank { typeLabel(bm.type) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.isOnline) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = formatDate(bm.addedDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Outlined.Delete, contentDescription = "Удалить из закладок",
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Строка закладки-сообщества: аватар 48dp, имя, число подписчиков. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGroupRow(
    bm: Bookmark,
    group: re.pinok.data.model.Group,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            val photo = group.photo100
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = group.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name.ifBlank { typeLabel(bm.type) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val followers = if (group.membersCount > 0) "${formatCount(group.membersCount)} подписчиков" else ""
            val meta = listOf(followers, formatDate(bm.addedDate)).filter { it.isNotEmpty() }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Outlined.Delete, contentDescription = "Удалить из закладок",
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Строка закладки-ссылки: favicon-иконка, URL, title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkLinkRow(
    bm: Bookmark,
    link: re.pinok.data.model.Attachment.Link,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // NULL-ЯВНО: у ссылки фавиконка-превью; fallback на превью закладки.
            val linkPhoto = link.photo
            val thumb = if (linkPhoto != null) linkPhoto.largestUrl else bm.thumbUrl
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = bm.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(24.dp),
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
            if (link.url.isNotBlank()) {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Outlined.Delete, contentDescription = "Удалить из закладок",
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Запасная плоская строка для прочих типов (article/product/photo/...):
 * прежнее поведение — иконка + title + дата + корзина.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkGenericRow(
    bm: Bookmark,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit,
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

/**
 * Форматирование счётчика с разделением тысяч пробелом: 19415 → "19 415".
 * null → пустая строка (нет данных), 0 → "0".
 */
private fun formatCount(n: Int?): String {
    if (n == null) return ""
    if (n == 0) return "0"
    val digits = n.toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
        val fromRight = digits.length - i
        if (i > 0 && fromRight % 3 == 0) sb.append(' ')
        sb.append(digits[i])
    }
    return sb.toString()
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
    // P0.44-audit I4: "page" — fave.get(item_type=page) MIX users+groups.
    // Иконка выбирается по заполненной сущности (user → Person, group → Group).
    // Но здесь type приходит как строка, поэтому fallback на Person
    // (большинство page-bookmarks — это users; для групп тоже приемлемо).
    "user", "profile", "page" -> Icons.Outlined.Person
    "group" -> Icons.Outlined.Group
    "photo" -> Icons.Outlined.InsertPhoto
    "video", "clip" -> Icons.Outlined.PlayCircle
    "link" -> Icons.Outlined.Link
    "article" -> Icons.AutoMirrored.Outlined.Article
    "product" -> Icons.Outlined.ShoppingBag
    else -> Icons.Outlined.Favorite
}

private fun typeLabel(type: String): String = when (type) {
    "user", "profile" -> "Пользователь"
    // P0.44-audit I4: "page" — label для MIX users+groups.
    "page" -> "Страница"
    "group" -> "Сообщество"
    "post" -> "Запись"
    "photo" -> "Фото"
    "video" -> "Видео"
    "clip" -> "Клип"
    "link" -> "Ссылка"
    "article" -> "Статья"
    "product" -> "Товар"
    else -> type
}

private fun formatDate(epochSec: Long): String {
    if (epochSec == 0L) return ""
    return SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(epochSec * 1000))
}
