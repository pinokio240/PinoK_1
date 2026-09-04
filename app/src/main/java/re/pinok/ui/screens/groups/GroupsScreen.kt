package re.pinok.ui.screens.groups

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Group
import re.pinok.ui.components.ErrorView
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onGroupClick: (Long) -> Unit = {}) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // Fix #256: showSearch — поиск в глобальном TopAppBar.
    var showSearch by remember { mutableStateOf(false) }
    // Fix #80: пагинация списка сообществ + pull-to-refresh.
    val pageSize = 50
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val list = app.apiClient.groupsGet(count = pageSize)
                // Fix #53: защитная дедупликация.
                groups = list.distinctBy { it.id }
                if (list.size < pageSize) endReached = true
                AppLog.i("GroupsScreen", "Loaded ${list.size} groups")
                if (list.isEmpty()) {
                    errorText = app.apiClient.lastApiError ?: "Вы не состоите в сообществах"
                }
            } catch (e: Exception) {
                AppLog.e("GroupsScreen", "Failed to load groups", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #80: pull-to-refresh — перезагрузка первой страницы.
    fun refreshGroups() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.groupsGet(count = pageSize)
                groups = list.distinctBy { it.id }
                endReached = (list.size < pageSize)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("GroupsScreen", "refreshGroups failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #80: пагинация — подгрузка следующих сообществ через offset.
    fun loadMoreGroups() {
        if (loadingMore || endReached || groups.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = groups.size
                val page = app.apiClient.groupsGet(count = pageSize, offset = offset)
                    .filter { np -> groups.none { it.id == np.id } }
                if (page.isNotEmpty()) {
                    groups = (groups + page).distinctBy { it.id }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("GroupsScreen", "loadMoreGroups failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #80: бесконечная пагинация — триггер при скролле к концу.
    LaunchedEffect(listState, groups.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 3 && layoutInfo.totalItemsCount > 0
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreGroups() }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val filtered = groups.filter { g ->
        query.isBlank() || g.name.contains(query, ignoreCase = true)
    }

    // Fix #256: регистрируем search в глобальном TopAppBar.
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
                        placeholder = { Text("Поиск сообществ…", style = MaterialTheme.typography.bodySmall) },
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
        if (filtered.isEmpty()) {
            ErrorView(
                message = if (errorText != null && query.isBlank()) errorText else "Ничего не найдено",
                onRetry = { scope.launch { loading = true; errorText = null; 
                    try { groups = app.apiClient.groupsGet(count = pageSize).distinctBy { it.id }; endReached = groups.size < pageSize; if (groups.isEmpty()) errorText = app.apiClient.lastApiError ?: "Вы не состоите в сообществах" } catch (e: Exception) { errorText = "Ошибка: ${e.message}" } finally { loading = false } }
                },
            )
            return
        }
        // Fix #80: PullToRefreshBox — pull-to-refresh списка сообществ.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshGroups() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                items(filtered, key = { it.id }) { group ->
                    GroupRow(group = group, onGroupClick = onGroupClick)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .padding(horizontal = 76.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                }
                // Fix #80: футер пагинации.
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
                                text = "Это все сообщества",
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
private fun GroupRow(group: Group, onGroupClick: (Long) -> Unit = {}) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isMember by remember { mutableStateOf(group.isMemberBool) }
    // Fix #350: блокируем кнопку на время запроса, чтобы предотвратить
    // двойные нажатия и визуально показать ход операции.
    var pending by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onGroupClick(group.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group.photo100 != null) {
            AsyncImage(
                model = group.photo100,
                contentDescription = group.name,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = group.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
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
                text = buildString {
                    append(group.typeLabel)
                    append(" • ")
                    append(formatMembers(group.membersCount))
                    group.activity?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // #ARCH-CONTAINERS 3.7-1: статус в :core:data — захват ДО проверки.
            val groupStatus = group.status
            if (!groupStatus.isNullOrBlank()) {
                Text(
                    text = groupStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalButton(
            onClick = {
                if (pending) return@FilledTonalButton
                scope.launch {
                    pending = true
                    val wasMember = isMember
                    try {
                        val ok = if (wasMember) app.apiClient.groupsLeave(group.id)
                                 else app.apiClient.groupsJoin(group.id)
                        if (ok) {
                            // Оптимистичное обновление UI: меняем состояние сразу
                            // и показываем уведомление о результате.
                            isMember = !wasMember
                            val msg = if (wasMember) {
                                "Вы отписались от «${group.name}»"
                            } else {
                                "Вы подписались на «${group.name}»"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            AppLog.i("GroupsScreen",
                                "membership toggled: groupId=${group.id} wasMember=$wasMember -> ${!wasMember}")
                            // Fix #52-B (mirror CommunityScreen): после изменения
                            // подписки состав историй мог измениться — markDirty
                            // триггерит перезагрузку StoriesRow на Feed.
                            re.pinok.ui.navigation.StoriesHolder.markDirty()
                        } else {
                            val err = app.apiClient.lastApiError
                            Toast.makeText(
                                context,
                                if (err.isNullOrBlank()) "Не удалось изменить подписку" else "Ошибка: $err",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        AppLog.w("GroupsScreen", "toggle membership failed: ${e.message}")
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        pending = false
                    }
                }
            },
            enabled = !pending,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isMember) Icons.Default.GroupRemove else Icons.Default.GroupAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            // Fix #350: подписи кнопок переписаны в знакомых VK-терминах
            // (Подписаться / Отписаться). Старая пара «Вышел / Вступить» была
            // несимметричной (past-tense vs imperative) и сбивала пользователей.
            Text(if (isMember) "Отписаться" else "Подписаться", fontSize = 12.sp)
        }
    }
}

private fun formatMembers(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(",0", "")
    n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(",0", "")
    else -> n.toString()
} + " уч."
