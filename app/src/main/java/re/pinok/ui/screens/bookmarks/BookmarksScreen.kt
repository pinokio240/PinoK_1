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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Bookmark
import re.pinok.data.model.FaveTag
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Уникальный ключ закладки для дедупликации и LazyColumn keys. */
private fun bookmarkKey(bm: Bookmark): String =
    "${bm.type}_${bm.user?.id ?: bm.group?.id ?: bm.post?.id ?: bm.photo?.id ?: bm.video?.id ?: bm.link?.url}"

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

    // S6-2: теги закладок — фильтрация.
    var tags by remember { mutableStateOf<List<FaveTag>>(emptyList()) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }

    // S6-3: long-press удаление — AlertDialog state.
    var removeTarget by remember { mutableStateOf<Bookmark?>(null) }

    // Загрузка тегов (один раз).
    LaunchedEffect(Unit) {
        try {
            tags = app.apiClient.faveGetTagList()
        } catch (_: Exception) {}
    }

    // Загрузка закладок — перезапускается при смене тега.
    LaunchedEffect(selectedTagId) {
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
                    errorText = app.apiClient.lastApiError ?: "Нет закладок"
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
    LaunchedEffect(listState, bookmarks.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= bookmarks.size - 3 && bookmarks.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreBookmarks() }
    }

    // S6-3: обработка удаления из закладок.
    fun removeBookmark(bm: Bookmark) {
        scope.launch {
            try {
                val type = bm.type
                val ownerId: Long
                val itemId: Long
                when (type) {
                    // #ARCH-CONTAINERS 3.7-1: elvis на свойстве чужого модуля не смарт-кастит
                    // его для следующих строк — захватываем объект целиком.
                    "post" -> {
                        val post = bm.post ?: return@launch
                        ownerId = post.ownerId
                        itemId = post.id
                    }
                    "user", "profile" -> {
                        val user = bm.user ?: return@launch
                        ownerId = user.id
                        itemId = user.id
                    }
                    "group" -> {
                        val group = bm.group ?: return@launch
                        ownerId = -(group.id)
                        itemId = group.id
                    }
                    "photo" -> {
                        val photo = bm.photo ?: return@launch
                        ownerId = photo.ownerId
                        itemId = photo.id
                    }
                    "video" -> {
                        val video = bm.video ?: return@launch
                        ownerId = video.ownerId
                        itemId = video.id
                    }
                    "link" -> {
                        // Для ссылок используем ownerId=0 и id=0 — VK API для ссылок требует
                        // только type=link и id=URL, но faveRemove работает через ownerId+id.
                        // Пропускаем удаление ссылок через стандартный механизм.
                        Toast.makeText(context, "Удаление ссылок из закладок пока не поддерживается", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    else -> {
                        Toast.makeText(context, "Удаление типа «$type» не поддерживается", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }
                val ok = app.apiClient.faveRemove(type = type, ownerId = ownerId, itemId = itemId)
                if (ok) {
                    bookmarks = bookmarks.filter { bookmarkKey(it) != bookmarkKey(bm) }
                    AppLog.i("BookmarksScreen", "Removed bookmark: type=$type owner=$ownerId item=$itemId")
                } else {
                    Toast.makeText(context, "Не удалось удалить из закладок", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e("BookmarksScreen", "faveRemove failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // S6-3: AlertDialog для подтверждения удаления.
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

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = errorText ?: "Нет закладок",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        // Fix #83: PullToRefreshBox — pull-to-refresh закладок.
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
                                    Toast.makeText(context, "Тип ${bm.type} — в разработке", Toast.LENGTH_SHORT).show()
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bm: Bookmark,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val icon = iconForType(bm.type)
    val thumb = bm.thumbUrl
    val context = LocalContext.current
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
        Icon(Icons.Outlined.Favorite, contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
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
    else -> type
}

private fun formatDate(epochSec: Long): String {
    if (epochSec == 0L) return ""
    return SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(epochSec * 1000))
}