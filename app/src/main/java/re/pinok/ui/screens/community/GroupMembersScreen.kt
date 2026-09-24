// File: ui/screens/community/GroupMembersScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════
// #OPVK-EXTRACT (Task 3-c): экран «Участники сообщества» — список членов
// группы. Идея-референс: OpenVK Legacy GroupMembersActivity (UsersListActivity:
// постраничный getMembers + список «аватар/имя», тап → профиль) — ТОЛЬКО
// семантика раздела, НЕ код (AGPL-3.0 репозиторий, копирование запрещено,
// чистая комната: код ниже — Kotlin/Compose по паттерну PinoK).
//
//   Источник данных: groupsGetMembers (VKA:4106 — заморожен, НЕ расширялся):
//     suspend fun groupsGetMembers(groupId: Long, count: Int = 50, offset: Int = 0):
//         List<UserProfile>
//     — профили приходят СРАЗУ (fields photo_100,photo_200,online,last_seen,
//     status,verified; парсинг parseUserProfileMini) → обогащение
//     usersGetByIds НЕ требуется.
//   Пагинация «Загрузить ещё» по [GROUP_MEMBERS_PAGE_SIZE]; total API-метод
//   не отдаёт (возврат — только список) → hasMore = «страница полная» —
//   тот же честный паттерн, что у followers-режима FollowersSubscriptionsScreen.
//
//   ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
//     1) groupsGetMembers «глотает» ошибку в emptyList → ошибка отличима от
//        пустого списка по lastApiError (честный error-стейт, не «нет участников»).
//     2) Счётчик участников (GroupInfo.membersCount) в маршрут не тащится:
//        экрану он не нужен (total=-1 → «Загрузить ещё» без числа), а
//        синхронизировать его с реальным размером списка было бы имитацией.
//     3) Закрытое сообщество/нет прав — сервер вернёт пусто или ошибку;
//        экран показывает честное empty/error состояние.
// ═══════════════════════════════════════════════════════════

/** Размер страницы groups.getMembers (эталон FollowersSubscriptionsScreen: 50). */
private const val GROUP_MEMBERS_PAGE_SIZE = 50

/**
 * W38 (C8): причины бана groups.banUser reason 0..4 (офиц. доки VK API).
 * План §4 C8: «Бан из контекст-меню участника — groups.banUser (метод уже
 * добавлен в 35-b)». Диалог по long-press на строке участника.
 */
private val BAN_REASONS = listOf(
    "Другое", "Спам", "Оскорбление участников", "Нецензурные выражения", "Угрозы",
)

/** W38 (C8): сроки бана в секундах (0 = навсегда, end_date=0). */
private val BAN_DURATION_SECONDS = listOf(0L, 86400L, 604800L, 2592000L)
private val BAN_DURATION_LABELS = listOf("Навсегда", "1 день", "1 неделя", "1 месяц")

/**
 * #OPVK-EXTRACT: экран «Участники» сообщества [groupId] (положительный id
 * группы, как в Screen.Community). Строка (аватар + имя + статус) → чужой
 * профиль ([re.pinok.ui.navigation.Screen.UserProfile], проводит хост).
 * Локальный фильтр по имени + PullToRefresh + пагинация — паттерн
 * FollowersSubscriptionsScreen (П-7-AB).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
    groupId: Long,
    onBack: () -> Unit,
    onMemberClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    // W38 (C8): гейт прав — long-press «Забанить» виден только руководителю
    // (isManager = admin_level >= 1 || is_admin == 1, W35-a). Статус берётся
    // отдельным groupsGetById (админ-блок в fields с волны 35-a).
    var canBan by remember { mutableStateOf(false) }
    // W38 (C8): состояние диалога бана.
    var banTarget by remember { mutableStateOf<UserProfile?>(null) }
    var banBusy by remember { mutableStateOf(false) }
    var banReason by remember { mutableStateOf(0) }
    var banDurationIdx by remember { mutableStateOf(0) }
    var banComment by remember { mutableStateOf("") }
    // Fix #389 #SCROLL-TOP-PARITY: состояние списка для FAB «наверх»
    // (тот же экземпляр передаётся в LazyColumn.state ниже).
    val listState = rememberLazyListState()

    /** Страница участников по offset (профили приходят сразу — VKA:4106). */
    suspend fun fetchPage(offset: Int): List<UserProfile> =
        app.apiClient.groupsGetMembers(
            groupId = groupId,
            count = GROUP_MEMBERS_PAGE_SIZE,
            offset = offset,
        )

    fun loadFirst() {
        scope.launch {
            loading = true
            error = null
            try {
                val page = fetchPage(0)
                if (page.isEmpty() && app.apiClient.lastApiError != null) {
                    // groupsGetMembers «глотает» ошибку в emptyList — различаем
                    // по lastApiError, чтобы не показывать «нет участников»
                    // при сетевой/API-ошибке (no-stub; паттерн followers-режима).
                    error = "Не удалось загрузить: ${app.apiClient.lastApiError}"
                } else {
                    members = page.distinctBy { it.id }
                    endReached = page.size < GROUP_MEMBERS_PAGE_SIZE
                }
            } catch (e: Exception) {
                AppLog.e("GroupMembersScreen", "loadFirst error", e)
                error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (loadingMore || endReached || loading || error != null) return
        scope.launch {
            loadingMore = true
            try {
                val page = fetchPage(members.size)
                val fresh = page.filter { candidate -> members.none { it.id == candidate.id } }
                if (fresh.isEmpty()) {
                    // Пустая страница — дальше грузить нечего (защита от цикла).
                    endReached = true
                } else {
                    members = (members + fresh).distinctBy { it.id }
                    if (page.size < GROUP_MEMBERS_PAGE_SIZE) endReached = true
                }
            } catch (e: Exception) {
                AppLog.e("GroupMembersScreen", "loadMore error", e)
                Toast.makeText(
                    context,
                    "Не удалось загрузить: ${app.apiClient.lastApiError ?: e.message ?: "ошибка"}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                loadingMore = false
            }
        }
    }

    // W38 (C8): проверить права текущего пользователя на это сообщество.
    LaunchedEffect(groupId) {
        try {
            canBan = app.apiClient.groupsGetById(listOf(groupId)).firstOrNull()?.isManager == true
        } catch (e: Exception) {
            AppLog.e("GroupMembersScreen", "canBan check failed", e)
        }
    }
    LaunchedEffect(groupId) { loadFirst() }

    /** W38 (C8): применить бан (groups.banUser) и убрать участника из списка. */
    fun applyBan(target: UserProfile) {
        scope.launch {
            banBusy = true
            try {
                val seconds = BAN_DURATION_SECONDS[banDurationIdx]
                val endDate = if (seconds == 0L) 0L else System.currentTimeMillis() / 1000L + seconds
                val ok = app.apiClient.groupsBanUser(
                    groupId = groupId,
                    userId = target.id,
                    reason = banReason,
                    endDate = endDate,
                    comment = banComment.takeIf { it.isNotBlank() },
                )
                if (ok) {
                    Toast.makeText(context, "Участник забанен", Toast.LENGTH_SHORT).show()
                    members = members.filterNot { it.id == target.id }
                    banTarget = null
                    banComment = ""
                    banReason = 0
                    banDurationIdx = 0
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось забанить" else "Ошибка: $err",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("GroupMembersScreen", "ban failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                banBusy = false
            }
        }
    }

    val filtered = members.filter { member ->
        filter.isBlank() || member.fullName.lowercase().contains(filter.trim().lowercase())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Участники") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        val loadError = error
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            loadError,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.heightIn(min = 12.dp))
                        TextButton(onClick = { loadFirst() }) {
                            Text("Повторить")
                        }
                    }
                }
            }
            else -> {
                // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх»
                // над списком участников (пагинация groups.getMembers offset).
                Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                val page = fetchPage(0)
                                if (page.isNotEmpty() || app.apiClient.lastApiError == null) {
                                    members = page.distinctBy { it.id }
                                    endReached = page.size < GROUP_MEMBERS_PAGE_SIZE
                                }
                            } catch (e: Exception) {
                                AppLog.e("GroupMembersScreen", "refresh error", e)
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        // Локальный фильтр по имени (bl-filter-паттерн BlacklistScreen).
                        item(key = "filter") {
                            OutlinedTextField(
                                value = filter,
                                onValueChange = { filter = it },
                                singleLine = true,
                                placeholder = { Text("Фильтр по имени") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .heightIn(min = 44.dp),
                            )
                        }
                        if (canBan && filtered.isNotEmpty()) {
                            item(key = "ban_hint") {
                                Text(
                                    "Удерживайте участника, чтобы забанить",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (filtered.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (members.isEmpty()) "Участников нет"
                                    else "По фильтру ничего не найдено",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { member ->
                                GroupMemberRow(
                                    member = member,
                                    onClick = { onMemberClick(member.id) },
                                    // W38 (C8): long-press → диалог бана (только руководителю).
                                    onBanClick = if (canBan) ({ banTarget = member }) else null,
                                )
                            }
                        }
                        if (!endReached && members.isNotEmpty()) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp,
                                        )
                                    } else {
                                        TextButton(onClick = { loadMore() }) {
                                            // total метод не отдаёт — без числа
                                            // (hasMore = «страница полная»).
                                            Text("Загрузить ещё")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх»
                // над списком участников (groups.getMembers offset-пагинация).
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
                }
            }
        }
    }

    // W38 (C8): диалог бана участника — причина/срок/комментарий (groups.banUser).
    val target = banTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { if (!banBusy) banTarget = null },
            title = {
                Text(
                    "Забанить: ${target.fullName}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Причина", style = MaterialTheme.typography.labelLarge)
                    BAN_REASONS.forEachIndexed { idx, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { banReason = idx },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = banReason == idx, onClick = { banReason = idx })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.heightIn(min = 8.dp))
                    Text("Срок", style = MaterialTheme.typography.labelLarge)
                    BAN_DURATION_LABELS.forEachIndexed { idx, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { banDurationIdx = idx },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = banDurationIdx == idx, onClick = { banDurationIdx = idx })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.heightIn(min = 8.dp))
                    OutlinedTextField(
                        value = banComment,
                        onValueChange = { banComment = it },
                        label = { Text("Комментарий (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { applyBan(target) }, enabled = !banBusy) {
                    Text("Забанить")
                }
            },
            dismissButton = {
                TextButton(onClick = { banTarget = null }, enabled = !banBusy) { Text("Отмена") }
            },
        )
    }
}

/**
 * #OPVK-EXTRACT: строка участника — аватар (AsyncImage, фоллбэк — первая
 * буква) + имя + статус (подзаголовок); паттерн FollowListRow
 * FollowersSubscriptionsScreen. Тач-таргет ≥ 44dp (аватар 48dp + паддинги).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMemberRow(
    member: UserProfile,
    onClick: () -> Unit,
    onBanClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onBanClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = member.photo200 ?: member.photo100
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
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
                    text = member.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.fullName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = member.status?.takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
