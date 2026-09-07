// File: ui/screens/community/GroupMembersScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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

    LaunchedEffect(groupId) { loadFirst() }

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
            }
        }
    }
}

/**
 * #OPVK-EXTRACT: строка участника — аватар (AsyncImage, фоллбэк — первая
 * буква) + имя + статус (подзаголовок); паттерн FollowListRow
 * FollowersSubscriptionsScreen. Тач-таргет ≥ 44dp (аватар 48dp + паддинги).
 */
@Composable
private fun GroupMemberRow(member: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
