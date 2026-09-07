// File: ui/screens/feed/FeedHiddenSourcesScreen.kt
package re.pinok.ui.screens.feed

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════
// IMP-FEED-2: Экран «Скрытые источники» — менеджер мьютов ленты
// (лента.снапшоты.парсинг.полный.md §3.1 «Редактировать» / §3.4
// «Мьют-менеджер» / §4 п.7; web: правое меню ленты → «Редактировать»,
// legacy al_settings.php?act=a_edit_owners_list):
//   - список:      newsfeedGetBanned (VK: newsfeed.getBanned extended=1 —
//                  response.profiles[] + response.groups[]);
//   - возврат:     newsfeedUnban (VK: newsfeed.unban) ТОЧЕЧНО для ОДНОГО
//                  источника: userIds=[id] / groupIds=[id]. Выбор метода:
//                  newsfeed.unban — документированный VK-метод «вернуть
//                  источник в ленту» (пара к newsfeed.addBan для мьют-менеджера
//                  ленты); newsfeed.deleteBan в VKA (#PROFILE-P0-2) — легаси-пара
//                  web-бандла ПРОФИЛЯ («Вернуть записи автора» после «Скрыть из
//                  ленты» в посте), для возврата из черного списка ленты
//                  семантически каноничен unban.
//   - optimistic:  строка исчезает сразу, при ошибке — возврат на прежнюю
//                  позицию + тост lastApiError (no-stub: без имитации успеха);
//   - disabled в полёте: mutableStateMapOf<String, Boolean> in-flight
//                  (ключ "user_<id>"/"group_<id>").
//   Паттерн UI — BlacklistScreen (П-4): Scaffold+TopAppBar, локальный
//   фильтр по имени (bl-filter), pull-to-refresh, empty/error состояния,
//   тосты; тап по источнику — профиль/сообщество (паттерн П-7-AB
//   FollowersSubscriptionsScreen: onUserClick/onGroupClick callbacks).
//   ЧЕСТНОЕ ОГРАНИЧЕНИЕ: newsfeedGetBanned «глотает» ошибки в пустые списки
//   (parse error / offline → пустой NewsfeedBannedResult) — различаем по
//   lastApiError (как FollowersSubscriptionsScreen).
// ═══════════════════════════════════════════════════════════

/**
 * Элемент списка скрытых источников: пользователь (isGroup=false, тап →
 * Screen.UserProfile) или сообщество (isGroup=true, тап → Screen.Community).
 * id — ПОЛОЖИТЕЛЬНЫЙ (как в profiles[]/groups[] ответа getBanned).
 */
private data class HiddenSourceEntry(
    val id: Long,
    val isGroup: Boolean,
    val title: String,
    val photo: String?,
) {
    /** Ключ in-flight карты (mutableStateMapOf). */
    val inFlightKey: String get() = if (isGroup) "group_$id" else "user_$id"
}

/** Первое непустое строковое поле из кандидатов-ключей (гварды null/primitive). */
private fun hiddenJsonStr(o: JsonObject, vararg keys: String): String? {
    for (key in keys) {
        val el = o.get(key) ?: continue
        if (el.isJsonPrimitive && el.asString.isNotBlank()) return el.asString
    }
    return null
}

/** Пользователь из response.profiles[] → элемент списка; битое — null (no-stub). */
private fun hiddenEntryFromProfileJson(o: JsonObject): HiddenSourceEntry? {
    val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    if (id <= 0L) return null
    val firstName = hiddenJsonStr(o, "first_name").orEmpty()
    val lastName = hiddenJsonStr(o, "last_name").orEmpty()
    val title = "$firstName $lastName".trim()
    if (title.isBlank()) return null
    return HiddenSourceEntry(
        id = id,
        isGroup = false,
        title = title,
        photo = hiddenJsonStr(o, "photo_200", "photo_100", "photo_50"),
    )
}

/** Сообщество из response.groups[] → элемент списка; битое — null (no-stub). */
private fun hiddenEntryFromGroupJson(o: JsonObject): HiddenSourceEntry? {
    val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    if (id <= 0L) return null
    val title = hiddenJsonStr(o, "name").orEmpty()
    if (title.isBlank()) return null
    return HiddenSourceEntry(
        id = id,
        isGroup = true,
        title = title,
        photo = hiddenJsonStr(o, "photo_200", "photo_100", "photo_50"),
    )
}

/**
 * Экран «Скрытые источники»: список замьюченных источников ленты
 * (newsfeed.getBanned) с возвратом по одному (newsfeed.unban, optimistic).
 * Тап по источнику — профиль/сообщество через [onUserClick]/[onGroupClick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedHiddenSourcesScreen(
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<HiddenSourceEntry>>(emptyList()) }
    var groups by remember { mutableStateOf<List<HiddenSourceEntry>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    // IMP-FEED-2: in-flight разбанов (disabled кнопки на время запроса).
    val unbanInFlight = remember { mutableStateMapOf<String, Boolean>() }

    /** Загрузка newsfeed.getBanned + терпеливый парсинг profiles/groups. */
    suspend fun fetchBanned(): Pair<List<HiddenSourceEntry>, List<HiddenSourceEntry>> {
        val result: VKApiClient.NewsfeedBannedResult = app.apiClient.newsfeedGetBanned()
        val parsedUsers = result.profiles.mapNotNull { hiddenEntryFromProfileJson(it) }
        val parsedGroups = result.groups.mapNotNull { hiddenEntryFromGroupJson(it) }
        return parsedUsers to parsedGroups
    }

    fun loadFirst() {
        scope.launch {
            loading = true
            error = null
            try {
                val (parsedUsers, parsedGroups) = fetchBanned()
                if (parsedUsers.isEmpty() && parsedGroups.isEmpty() &&
                    app.apiClient.lastApiError != null
                ) {
                    // getBanned «глотает» ошибку в пустые списки — различаем по
                    // lastApiError, чтобы не показывать «скрытых нет» при сетевой
                    // ошибке (no-stub, паттерн FollowersSubscriptionsScreen).
                    error = "Не удалось загрузить: ${app.apiClient.lastApiError}"
                } else {
                    users = parsedUsers
                    groups = parsedGroups
                }
            } catch (e: Exception) {
                AppLog.e("FeedHiddenSourcesScreen", "loadFirst error", e)
                error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFirst() }

    /**
     * Optimistic-возврат источника в ленту: строка исчезает сразу, при
     * ошибке — возврат на прежнюю позицию + тост lastApiError.
     */
    fun unbanSource(entry: HiddenSourceEntry) {
        if (unbanInFlight[entry.inFlightKey] == true) return
        val sourceList = if (entry.isGroup) groups else users
        val index = sourceList.indexOfFirst { it.id == entry.id }
        if (index < 0) return
        unbanInFlight[entry.inFlightKey] = true
        // Optimistic: удаляем строку сразу.
        if (entry.isGroup) {
            groups = groups.toMutableList().apply { removeAt(index) }
        } else {
            users = users.toMutableList().apply { removeAt(index) }
        }
        scope.launch {
            // newsfeed.unban точечно для ОДНОГО источника (документированная
            // семантика «вернуть в ленту»; см. шапку файла — почему не deleteBan).
            val ok = if (entry.isGroup) {
                app.apiClient.newsfeedUnban(groupIds = listOf(entry.id))
            } else {
                app.apiClient.newsfeedUnban(userIds = listOf(entry.id))
            }
            unbanInFlight.remove(entry.inFlightKey)
            if (ok) {
                Toast.makeText(
                    context,
                    if (entry.isGroup) {
                        "Сообщество «${entry.title}» возвращено в ленту"
                    } else {
                        "«${entry.title}» возвращён в ленту"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                // Rollback: вернуть строку на прежнюю позицию.
                if (entry.isGroup) {
                    val restored = groups.toMutableList()
                    restored.add(index.coerceAtMost(restored.size), entry)
                    groups = restored
                } else {
                    val restored = users.toMutableList()
                    restored.add(index.coerceAtMost(restored.size), entry)
                    users = restored
                }
                Toast.makeText(
                    context,
                    "Не удалось вернуть: ${app.apiClient.lastApiError ?: "нет ответа сервера"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val filteredUsers = users.filter { entry ->
        filter.isBlank() || entry.title.lowercase().contains(filter.trim().lowercase())
    }
    val filteredGroups = groups.filter { entry ->
        filter.isBlank() || entry.title.lowercase().contains(filter.trim().lowercase())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Скрытые источники") },
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
                        Button(
                            onClick = { loadFirst() },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
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
                                val (parsedUsers, parsedGroups) = fetchBanned()
                                if (parsedUsers.isEmpty() && parsedGroups.isEmpty() &&
                                    app.apiClient.lastApiError != null
                                ) {
                                    Toast.makeText(
                                        context,
                                        "Не удалось обновить: ${app.apiClient.lastApiError}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    users = parsedUsers
                                    groups = parsedGroups
                                    error = null
                                }
                            } catch (e: Exception) {
                                AppLog.e("FeedHiddenSourcesScreen", "refresh error", e)
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
                        // bl-filter-паттерн BlacklistScreen — локальная фильтрация по имени.
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
                        if (filteredUsers.isEmpty() && filteredGroups.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (users.isEmpty() && groups.isEmpty()) {
                                        "Скрытых источников нет"
                                    } else {
                                        "По фильтру ничего не найдено"
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            if (filteredUsers.isNotEmpty()) {
                                item(key = "section_users") {
                                    HiddenSectionHeader(
                                        title = "Пользователи",
                                        count = filteredUsers.size,
                                    )
                                }
                                items(filteredUsers, key = { "user_${it.id}" }) { entry ->
                                    HiddenSourceRow(
                                        entry = entry,
                                        inFlight = unbanInFlight[entry.inFlightKey] == true,
                                        onClick = {
                                            if (entry.isGroup) onGroupClick(entry.id) else onUserClick(entry.id)
                                        },
                                        onUnban = { unbanSource(entry) },
                                    )
                                }
                            }
                            if (filteredGroups.isNotEmpty()) {
                                item(key = "section_groups") {
                                    HiddenSectionHeader(
                                        title = "Сообщества",
                                        count = filteredGroups.size,
                                    )
                                }
                                items(filteredGroups, key = { "group_${it.id}" }) { entry ->
                                    HiddenSourceRow(
                                        entry = entry,
                                        inFlight = unbanInFlight[entry.inFlightKey] == true,
                                        onClick = {
                                            if (entry.isGroup) onGroupClick(entry.id) else onUserClick(entry.id)
                                        },
                                        onUnban = { unbanSource(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Заголовок секции списка (Пользователи / Сообщества) с числом видимых строк. */
@Composable
private fun HiddenSectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Строка скрытого источника: аватар (AsyncImage + фоллбэк — первая буква),
 * имя, кнопка «Вернуть в ленту» (disabled пока запрос в полёте). Тап по строке
 * — профиль/сообщество (паттерн FollowListRow П-7-AB).
 */
@Composable
private fun HiddenSourceRow(
    entry: HiddenSourceEntry,
    inFlight: Boolean,
    onClick: () -> Unit,
    onUnban: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = entry.photo
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.isGroup) {
                Text(
                    text = "Сообщество",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onUnban,
            enabled = !inFlight,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Вернуть в ленту")
        }
    }
}
