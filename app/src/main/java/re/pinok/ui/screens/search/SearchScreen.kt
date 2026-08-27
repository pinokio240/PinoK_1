package re.pinok.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Group
import re.pinok.data.model.Post
import re.pinok.data.model.SearchHint
import re.pinok.data.model.UserProfile
import re.pinok.util.AppLog

private enum class SearchTab(val label: String) {
    HINTS("Подсказки"),
    PEOPLE("Люди"),
    GROUPS("Сообщества"),
    NEWSFEED("Новости"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onUserClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(SearchTab.HINTS) }
    var hints by remember { mutableStateOf<List<SearchHint>>(emptyList()) }
    var people by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var newsfeedPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var searchedQuery by remember { mutableStateOf("") }
    // Fix #87: пагинация по вкладкам People/Groups (Hints не поддерживает offset).
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Debounced search — 500ms after last keystroke.
    LaunchedEffect(query, activeTab) {
        if (query.isBlank()) {
            hints = emptyList(); people = emptyList(); groups = emptyList(); newsfeedPosts = emptyList()
            errorText = null; loading = false; endReached = false
            return@LaunchedEffect
        }
        delay(500)
        loading = true
        errorText = null
        endReached = false
        scope.launch {
            try {
                when (activeTab) {
                    SearchTab.HINTS -> {
                        val r = app.apiClient.searchGetHints(query, count = 30)
                        // Fix #53: защитная дедупликация.
                        hints = r.distinctBy { "${it.type}_${it.user?.id ?: it.group?.id}" }
                        if (r.isEmpty()) errorText = app.apiClient.lastApiError ?: "Ничего не найдено"
                    }
                    SearchTab.PEOPLE -> {
                        val r = app.apiClient.usersSearch(query, count = 30)
                        people = r.distinctBy { it.id }
                        endReached = r.size < 30
                        if (r.isEmpty()) errorText = app.apiClient.lastApiError ?: "Никого не найдено"
                    }
                    SearchTab.GROUPS -> {
                        val r = app.apiClient.groupsSearch(query, count = 30)
                        groups = r.distinctBy { it.id }
                        endReached = r.size < 30
                        if (r.isEmpty()) errorText = app.apiClient.lastApiError ?: "Ничего не найдено"
                    }
                    SearchTab.NEWSFEED -> {
                        val (totalCount, r) = app.apiClient.newsfeedSearch(query, count = 20)
                        newsfeedPosts = r.distinctBy { "${it.ownerId}_${it.id}" }
                        endReached = r.size < 20
                        if (r.isEmpty()) errorText = app.apiClient.lastApiError ?: "Ничего не найдено"
                    }
                }
                searchedQuery = query
                val resultCount = when (activeTab) {
                    SearchTab.HINTS -> hints.size
                    SearchTab.PEOPLE -> people.size
                    SearchTab.GROUPS -> groups.size
                    SearchTab.NEWSFEED -> newsfeedPosts.size
                }
                AppLog.i("SearchScreen", "search '$query' tab=$activeTab results=$resultCount")
            } catch (e: Exception) {
                AppLog.e("SearchScreen", "search failed", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #87: pull-to-refresh — повторный поиск по текущей вкладке.
    fun refreshSearch() {
        if (isRefreshing || query.isBlank()) return
        scope.launch {
            isRefreshing = true
            try {
                when (activeTab) {
                    SearchTab.HINTS -> {
                        val r = app.apiClient.searchGetHints(query, count = 30)
                        hints = r.distinctBy { "${it.type}_${it.user?.id ?: it.group?.id}" }
                    }
                    SearchTab.PEOPLE -> {
                        val r = app.apiClient.usersSearch(query, count = 30)
                        people = r.distinctBy { it.id }
                        endReached = r.size < 30
                    }
                    SearchTab.GROUPS -> {
                        val r = app.apiClient.groupsSearch(query, count = 30)
                        groups = r.distinctBy { it.id }
                        endReached = r.size < 30
                    }
                    SearchTab.NEWSFEED -> {
                        val (totalCount, r) = app.apiClient.newsfeedSearch(query, count = 20)
                        newsfeedPosts = r.distinctBy { "${it.ownerId}_${it.id}" }
                        endReached = r.size < 20
                    }
                }
            } catch (e: Exception) {
                AppLog.e("SearchScreen", "refreshSearch failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #87: infinite scroll для People/Groups (Hints — без пагинации).
    fun loadMore() {
        if (loadingMore || endReached || query.isBlank()) return
        if (activeTab == SearchTab.HINTS) return
        scope.launch {
            loadingMore = true
            try {
                when (activeTab) {
                    SearchTab.PEOPLE -> {
                        val r = app.apiClient.usersSearch(query, count = 30, offset = people.size)
                        val newItems = r.filter { np -> people.none { it.id == np.id } }.distinctBy { it.id }
                        if (newItems.isEmpty()) endReached = true
                        else {
                            people = (people + newItems).distinctBy { it.id }
                            if (r.size < 30) endReached = true
                        }
                    }
                    SearchTab.GROUPS -> {
                        val r = app.apiClient.groupsSearch(query, count = 30, offset = groups.size)
                        val newItems = r.filter { ng -> groups.none { it.id == ng.id } }.distinctBy { it.id }
                        if (newItems.isEmpty()) endReached = true
                        else {
                            groups = (groups + newItems).distinctBy { it.id }
                            if (r.size < 30) endReached = true
                        }
                    }
                    SearchTab.NEWSFEED -> {
                        val r = app.apiClient.newsfeedSearch(query, count = 20, offset = newsfeedPosts.size).second
                        val newItems = r.filter { np -> newsfeedPosts.none { "${it.ownerId}_${it.id}" == "${np.ownerId}_${np.id}" } }
                        if (newItems.isEmpty()) endReached = true
                        else {
                            newsfeedPosts = (newsfeedPosts + newItems).distinctBy { "${it.ownerId}_${it.id}" }
                            if (r.size < 20) endReached = true
                        }
                    }
                    SearchTab.HINTS -> {}
                }
            } catch (e: Exception) {
                AppLog.e("SearchScreen", "loadMore failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #87: триггер пагинации (только для People/Groups/Newsfeed).
    LaunchedEffect(listState, activeTab, people.size, groups.size, newsfeedPosts.size) {
        if (activeTab == SearchTab.HINTS) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Поиск людей и сообществ…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchTab.entries.forEach { tab ->
                TabChip(
                    label = tab.label,
                    selected = activeTab == tab,
                    onClick = {
                        activeTab = tab
                        // Fix #87: activeTab в ключах LaunchedEffect — переключение
                        // вкладки само перезапускает поиск, хак с query больше не нужен.
                    },
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Введите запрос для поиска",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        val isEmpty = when (activeTab) {
            SearchTab.HINTS -> hints.isEmpty()
            SearchTab.PEOPLE -> people.isEmpty()
            SearchTab.GROUPS -> groups.isEmpty()
            SearchTab.NEWSFEED -> newsfeedPosts.isEmpty()
        }
        if (isEmpty) {
            // Fix #87: PullToRefreshBox даже на empty state — можно обновить.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshSearch() },
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorText ?: "Ничего не найдено по запросу «$searchedQuery»",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        // Fix #87: PullToRefreshBox вокруг результатов.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshSearch() },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            when (activeTab) {
                SearchTab.HINTS -> items(hints, key = { "${it.type}_${it.user?.id ?: it.group?.id ?: it.appId ?: it.description ?: it.section ?: it.hashCode()}" }) { hint ->
                    HintRow(hint = hint, onUserClick = onUserClick, onGroupClick = onGroupClick)
                    Divider()
                }
                SearchTab.PEOPLE -> items(people, key = { it.id }) { user ->
                    PersonRow(user = user, onUserClick = onUserClick)
                    Divider()
                }
                SearchTab.GROUPS -> items(groups, key = { it.id }) { group ->
                    GroupRow(group = group, onGroupClick = onGroupClick)
                    Divider()
                }
                SearchTab.NEWSFEED -> items(newsfeedPosts, key = { "${it.ownerId}_${it.id}" }) { post ->
                    NewsfeedPostRow(post = post)
                    Divider()
                }
            }
            // Fix #87: футер пагинации (только People/Groups/Newsfeed).
            if (activeTab != SearchTab.HINTS) {
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
                                text = "Это все результаты",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox (Fix #87)
    }
}

@Composable
private fun HintRow(hint: SearchHint, onUserClick: (Long) -> Unit, onGroupClick: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            // Fix #87: навигация на профиль/сообщество вместо Toast.
            when (hint.type) {
                "profile" -> hint.user?.id?.let(onUserClick)
                "group" -> hint.group?.id?.let(onGroupClick)
            }
        }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarOrPlaceholder(url = hint.thumbUrl, label = hint.title, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hint.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (hint.type) {
                    "profile" -> "Пользователь"
                    "group" -> "Сообщество"
                    else -> hint.type
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hint.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonRow(user: UserProfile, onUserClick: (Long) -> Unit) {
    // #FRIEND-COLOR: визуальное отличие друзей от не-друзей.
    //
    // VK friend_status: 0=не друг, 1=заявка отправлена, 2=есть входящая
    // заявка, 3=друг. Подсвечиваем только status==3 (полноценный друг)
    // мягким зелёным фоном. Заявки (1, 2) — оставляем обычным цветом,
    // т.к. пользователь ещё не "друг" в полном смысле.
    //
    // Используем containerColors с alpha — не чистый зелёный, чтобы
    // сохранить читаемость текста и вписаться в любую тему (light/dark).
    val isFriend = user.friendStatus == 3
    val rowBg = if (isFriend) {
        // Мягкий зелёный: primary (обычно зелёный в VK-подобной теме) с alpha 0.08.
        // Если primary не зелёный — берём literals Color(0xFF4CAF50).copy(alpha=0.10).
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        // Не друг — фон как был (прозрачный, базовый surface).
        androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(rowBg)
            .clickable {
                // Fix #87: навигация на профиль вместо Toast.
                onUserClick(user.id)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            AvatarOrPlaceholder(url = user.photo100, label = user.fullName, size = 48)
            if (user.isOnline) {
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            // #FRIEND-COLOR: зелёная точка-индикатор "друг" в углу аватара.
            if (isFriend) {
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isFriend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.verified == 1) {
                    Spacer(Modifier.width(4.dp))
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                // #FRIEND-COLOR: текстовый бейдж "друг" после имени.
                if (isFriend) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "друг",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            val sub = buildString {
                user.city?.title?.let { append(it) }
                if (user.isOnline) { if (isNotEmpty()) append(" • "); append("в сети") }
                if (isBlank()) user.status?.take(40)?.let { append(it) }
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(group: Group, onGroupClick: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            // Fix #87: навигация на сообщество вместо Toast.
            onGroupClick(group.id)
        }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarOrPlaceholder(url = group.photo100, label = group.name, size = 48, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.verified == 1) {
                    Spacer(Modifier.width(4.dp))
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
            Text(
                text = "${group.typeLabel} • ${group.membersCount} уч.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!group.activity.isNullOrBlank()) {
                Text(
                    text = group.activity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AvatarOrPlaceholder(
    url: String?,
    label: String,
    size: Int = 48,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = label,
            modifier = Modifier.size(size.dp).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NewsfeedPostRow(post: Post) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarOrPlaceholder(url = null, label = "П", size = 32)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "id${post.fromId}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (post.date > 0) {
                Text(
                    text = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.forLanguageTag("ru")).format(java.util.Date(post.date * 1000)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .padding(horizontal = 76.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
