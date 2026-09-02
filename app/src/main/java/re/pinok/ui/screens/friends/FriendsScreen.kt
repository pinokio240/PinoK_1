package re.pinok.ui.screens.friends

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Friend
import re.pinok.ui.components.ErrorView
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    // Sprint 1, P0-2 (#74): тап на друга → экран чужого профиля.
    onUserClick: (Long) -> Unit = {},
    // #CALLS: кнопка звонка на карточке друга.
    // #ARCH-CONTAINERS (Этап 1.4): nullable — хост передаёт колбэк ТОЛЬКО если
    // в реестре есть CallStarter. null → кнопка НЕ рендерится (условие композиции).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var requests by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filterOnline by remember { mutableStateOf(false) }
    // Fix #256: showSearch — поиск в глобальном TopAppBar (title → TextField).
    var showSearch by remember { mutableStateOf(false) }
    // 0 = Все, 1 = Онлайн, 2 = Заявки (#43)
    var tab by remember { mutableStateOf(0) }
    // Fix #79: пагинация списка друзей + pull-to-refresh.
    val pageSize = 50
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Загрузка списка друзей (первая страница).
    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val list = app.apiClient.friendsGet(count = pageSize)
                // Fix #53: защитная дедупликация.
                friends = list.distinctBy { it.id }
                if (list.size < pageSize) endReached = true
                AppLog.i("FriendsScreen", "Loaded ${list.size} friends")
                if (list.isEmpty()) {
                    errorText = app.apiClient.lastApiError ?: "Список друзей пуст"
                }
            } catch (e: Exception) {
                AppLog.e("FriendsScreen", "Failed to load friends", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #79: pull-to-refresh — перезагрузка первой страницы.
    fun refreshFriends() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.friendsGet(count = pageSize)
                friends = list.distinctBy { it.id }
                endReached = (list.size < pageSize)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("FriendsScreen", "refreshFriends failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #79: пагинация — подгрузка следующих друзей через offset.
    fun loadMoreFriends() {
        if (loadingMore || endReached || friends.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = friends.size
                val page = app.apiClient.friendsGet(count = pageSize, offset = offset)
                    .filter { np -> friends.none { it.id == np.id } }
                if (page.isNotEmpty()) {
                    friends = (friends + page).distinctBy { it.id }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("FriendsScreen", "loadMoreFriends failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #79: бесконечная пагинация — триггер при скролле к концу.
    // Только на вкладках «Все»/«Онлайн» (на «Заявки» своя логика).
    LaunchedEffect(listState, friends.size, tab) {
        if (tab == 2) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 3 && layoutInfo.totalItemsCount > 0
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreFriends() }
    }

    // Загрузка заявок в друзья — отдельный LaunchedEffect, срабатывает при
    // переключении на вкладку «Заявки» (#43). Загружаем один раз и кэшируем.
    LaunchedEffect(tab) {
        if (tab == 2 && requests.isEmpty()) {
            scope.launch {
                try {
                    val list = app.apiClient.friendsGetRequests(count = 100)
                    // Fix #53: защитная дедупликация.
                    requests = list.distinctBy { it.id }
                    AppLog.i("FriendsScreen", "Loaded ${list.size} friend requests")
                } catch (e: Exception) {
                    AppLog.e("FriendsScreen", "Failed to load requests", e)
                }
            }
        }
    }

    if (loading && tab == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val filtered = friends.filter { f ->
        (query.isBlank() || f.fullName.contains(query, ignoreCase = true)) &&
            (!filterOnline || f.isOnline)
    }

    // Fix #256: регистрируем search в глобальном TopAppBar.
    // Раньше поиск был inline в контенте — теперь в TopAppBar для единообразия
    // с Notifications и другими экранами.
    // Fix #260: showSearch в ключе DisposableEffect — иначе configure()
    // вызывается один раз с showSearch=false → titleOverride=null навсегда.
    DisposableEffect(showSearch) {
        val token = ScreenTopBar.configure(
            actions = {
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) query = ""
                }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            titleOverride = if (showSearch) {
                {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск друзей…", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                    )
                }
            } else null,
        )
        onDispose { ScreenTopBar.clear(token) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fix #256: inline search убран — теперь в TopAppBar.
        // Фильтр-чипы (Все / Онлайн / Заявки) остаются в контенте.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = "Все (${friends.size})",
                selected = tab == 0,
                onClick = { tab = 0; filterOnline = false },
            )
            FilterChip(
                label = "Онлайн (${friends.count { it.isOnline }})",
                selected = tab == 1,
                onClick = { tab = 1; filterOnline = true },
            )
            FilterChip(
                label = "Заявки (${requests.size})",
                selected = tab == 2,
                onClick = { tab = 2 },
            )
        }

        if (tab == 2) {
            // Вкладка заявок в друзья (#43).
            if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нет заявок в друзья",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(requests, key = { it.id }) { request ->
                        RequestRow(
                            request = request,
                            onAccept = { uid ->
                                scope.launch {
                                    try {
                                        val r = app.apiClient.friendsAdd(uid)
                                        if (r >= 0) {
                                            requests = requests.filterNot { it.id == uid }
                                            AppLog.i("FriendsScreen", "Request accepted: $uid")
                                        }
                                    } catch (e: Exception) {
                                        AppLog.w("FriendsScreen", "accept failed: ${e.message}")
                                    }
                                }
                            },
                            onDecline = { uid ->
                                scope.launch {
                                    try {
                                        val ok = app.apiClient.friendsDelete(uid)
                                        if (ok) {
                                            requests = requests.filterNot { it.id == uid }
                                            AppLog.i("FriendsScreen", "Request declined: $uid")
                                        }
                                    } catch (e: Exception) {
                                        AppLog.w("FriendsScreen", "decline failed: ${e.message}")
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
                }
            }
            return
        }

        if (filtered.isEmpty()) {
            ErrorView(
                message = if (errorText != null && query.isBlank()) errorText else "Ничего не найдено",
                onRetry = { refreshFriends() },
            )
            return
        }

        // Fix #79: PullToRefreshBox — pull-to-refresh списка друзей.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshFriends() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                items(filtered, key = { it.id }) { friend ->
                    FriendRow(friend = friend, onUserClick = onUserClick, onCallClick = onCallClick)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .padding(horizontal = 76.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                }
                // Fix #79: футер пагинации.
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
                                text = "Это все друзья",
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

@Composable
private fun FriendRow(
    friend: Friend,
    // Sprint 1, P0-2 (#74): тап по строке друга → экран профиля.
    onUserClick: (Long) -> Unit = {},
    // #CALLS: кнопка звонка.
    // #ARCH-CONTAINERS (Этап 1.4): nullable — см. KDoc FriendsScreen.onCallClick.
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var isFriend by remember { mutableStateOf(true) }
    // Audit #40: удалён неиспользуемый val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            // Sprint 1, P0-2 (#74): открываем экран чужого профиля вместо Toast.
            onUserClick(friend.id)
        }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            if (friend.photo100 != null) {
                AsyncImage(
                    model = friend.photo100,
                    contentDescription = friend.fullName,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = friend.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (friend.isOnline) {
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend.verified == 1) {
                    Spacer(Modifier.width(4.dp))
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
            val sub = buildString {
                if (friend.isOnline) append("в сети")
                else friend.lastSeen?.time?.let { append("был в сети ${formatLastSeen(it)}") }
                friend.city?.title?.let { if (isNotEmpty()) append(" • "); append(it) }
                if (isBlank()) friend.status?.take(40)?.let { append(it) }
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
        // #CALLS: кнопка звонка (data-testid="friends_call_button").
        // #ARCH-CONTAINERS (Этап 1.4): рисуем только при живом CallStarter.
        if (onCallClick != null) {
            IconButton(
                onClick = { onCallClick(friend.id, friend.fullName, friend.photo100 ?: friend.photo200) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Позвонить", modifier = Modifier.size(20.dp))
            }
        }
        FilledTonalIconButton(
            onClick = {
                scope.launch {
                    try {
                        if (isFriend) {
                            val ok = app.apiClient.friendsDelete(friend.id)
                            if (ok) isFriend = false
                        } else {
                            val r = app.apiClient.friendsAdd(friend.id)
                            if (r >= 0) isFriend = true
                        }
                    } catch (e: Exception) {
                        AppLog.w("FriendsScreen", "toggle friend failed: ${e.message}")
                    }
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (isFriend) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                contentDescription = if (isFriend) "Удалить" else "Добавить",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Строка заявки в друзья — аватар + имя + кнопки «Принять»/«Отклонить» (#43).
 * Accept = friends.add (подтверждаем заявку), Decline = friends.delete (отклоняем).
 */
@Composable
private fun RequestRow(
    request: Friend,
    onAccept: (Long) -> Unit,
    onDecline: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (request.photo100 != null) {
            AsyncImage(
                model = request.photo100,
                contentDescription = request.fullName,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = request.firstName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.fullName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (request.verified == 1) {
                Text("\u2713 подтверждён", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            val sub = buildString {
                request.city?.title?.let { append(it) }
                if (isBlank()) request.sexLabel.takeIf { it.isNotBlank() }?.let { append(it) }
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Кнопка «Принять» — зелёная галочка.
        FilledTonalIconButton(
            onClick = { onAccept(request.id) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Принять",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        // Кнопка «Отклонить» — красный крестик.
        FilledTonalIconButton(
            onClick = { onDecline(request.id) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Отклонить",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatLastSeen(epochSec: Long): String {
    val diff = System.currentTimeMillis() / 1000 - epochSec
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        diff < 604800 -> "${diff / 86400} дн назад"
        else -> SimpleDateFormat("d MMM", Locale.forLanguageTag("ru")).format(Date(epochSec * 1000))
    }
}
