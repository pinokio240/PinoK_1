// File: ui/screens/profile/FollowersSubscriptionsScreen.kt
package re.pinok.ui.screens.profile

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.navigation.Screen
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════
// П-7-AB: Экран «Подписчики»/«Подписки» (остаток правой колонки веба —
// профиль.этап-П5.решение.md §3: «Правая колонка веба (друзья/подписчики/
// подписки списками) — счётчики есть, списков нет»).
//   Один экран с режимом (mode query-параметр маршрута Screen.FollowList):
//     - followers:      users.getFollowers (VKA возвращает List<UserProfile>);
//     - subscriptions:  users.getSubscriptions (extended=1, VKA возвращает СЫРОЙ
//                       JsonObject → терпеливый парсинг на UI-стороне, паттерн
//                       extractArticleInfo/jsonPrimitiveStr П-6b: isJsonPrimitive-
//                       гварды, битые элементы пропускаются).
//   ДЕФОЛТ count у users.getSubscriptions = 5 (веб-снапшот) — здесь страницы
//   запрашиваются по 50 ([FOLLOW_LIST_PAGE_SIZE]).
//   Тапы: пользователь → Screen.UserProfile.buildRoute(id); группа →
//   Screen.Community.buildRoute(id) — маршрут сообщества существует
//   (Screen.kt, Fix #67); объект Screen.Group в репо отсутствует.
//   Пагинация «Загрузить ещё» + PullToRefreshBox + локальный фильтр по имени —
//   по образцу BlacklistScreen (П-4).
//
//   ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
//     1) users.getFollowers не отдаёт total → hasMore = «последняя страница
//        полная» (страница < 50 — конец); ошибки VKA внутри VKA «глотаются» в
//        emptyList → различаем по lastApiError (честное состояние ошибки).
//     2) total подписок — response.count, при его отсутствии сумма buckets
//        users.count + groups.count (см. [followSubscriptionsTotal]); если
//        счётчик не распознан — hasMore по «полной странице».
//     3) Закрытый/скрытый профиль — сервер вернёт пусто или ошибку; экран
//        показывает честное empty/error состояние.
// ═══════════════════════════════════════════════════════════

/** Размер страницы users.getFollowers / users.getSubscriptions. */
private const val FOLLOW_LIST_PAGE_SIZE = 50

/**
 * Элемент списка подписчиков/подписок: пользователь (isGroup=false, id > 0,
 * тап → Screen.UserProfile) или сообщество (isGroup=true, id > 0 — ПОЛОЖИТЕЛЬНЫЙ
 * id группы, как в groups[].id; тап → Screen.Community.buildRoute(id),
 * минус-владелец CommunityScreen вычисляет сам).
 */
private data class FollowListEntry(
    val id: Long,
    val isGroup: Boolean,
    val title: String,
    val photo: String?,
    val subtitle: String?,
)

/** Страница users.getSubscriptions (extended=1) после терпеливого парсинга. */
private data class FollowSubscriptionsPage(
    val total: Int,
    val entries: List<FollowListEntry>,
)

/** Первое непустое строковое поле из кандидатов-ключей (гварды null/primitive). */
private fun followJsonStr(o: JsonObject, vararg keys: String): String? {
    for (key in keys) {
        val el = o.get(key) ?: continue
        if (el.isJsonPrimitive && el.asString.isNotBlank()) return el.asString
    }
    return null
}

/**
 * П-7-AB: total подписок из ответа usersGetSubscriptions (VKA возвращает
 * `response`). Формат (живой снапшот, KDoc VKA): {count?,
 * groups:{count,items[]}, users:{count,items[]}} — count верхнего уровня,
 * иначе сумма счётчиков buckets. null — счётчик не распознан (вызывающий
 * рисует строку «Подписки» без числа, счётчик не имитируется).
 * Shared для ProfileScreen/UserProfileScreen (строки «Подписки»).
 */
internal fun followSubscriptionsTotal(response: JsonObject?): Int? {
    if (response == null) return null
    return try {
        val explicit = response.get("count")?.takeIf { it.isJsonPrimitive }?.asInt
        if (explicit != null && explicit >= 0) {
            explicit
        } else {
            val usersCount =
                response.getAsJsonObject("users")?.get("count")?.takeIf { it.isJsonPrimitive }?.asInt
            val groupsCount =
                response.getAsJsonObject("groups")?.get("count")?.takeIf { it.isJsonPrimitive }?.asInt
            if (usersCount != null || groupsCount != null) {
                (usersCount ?: 0) + (groupsCount ?: 0)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        AppLog.e("FollowersSubscriptionsScreen", "followSubscriptionsTotal parse error", e)
        null
    }
}

/** Пользователь из profiles[]/users.items[] → элемент списка; битое — null. */
private fun followEntryFromUserJson(o: JsonObject): FollowListEntry? {
    val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    if (id <= 0L) return null
    val firstName = followJsonStr(o, "first_name").orEmpty()
    val lastName = followJsonStr(o, "last_name").orEmpty()
    val title = "$firstName $lastName".trim()
    if (title.isBlank()) return null // рисовать нечего — элемент пропускаем (no-stub)
    return FollowListEntry(
        id = id,
        isGroup = false,
        title = title,
        photo = followJsonStr(o, "photo_200", "photo_100", "photo_50"),
        subtitle = followJsonStr(o, "status")?.takeIf { it.isNotBlank() },
    )
}

/** Сообщество из groups.items[]/groups[] → элемент списка; битое — null. */
private fun followEntryFromGroupJson(o: JsonObject): FollowListEntry? {
    val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    if (id <= 0L) return null
    val title = followJsonStr(o, "name").orEmpty()
    if (title.isBlank()) return null
    return FollowListEntry(
        id = id,
        isGroup = true,
        title = title,
        photo = followJsonStr(o, "photo_200", "photo_100", "photo_50"),
        subtitle = followJsonStr(o, "description", "status")?.takeIf { it.isNotBlank() },
    )
}

/**
 * Терпеливый парсинг `response` users.getSubscriptions (extended=1).
 * Формат А (живой снапшот, KDoc VKA): {count?, users:{count,items[]},
 * groups:{count,items[]}}. Формат Б (дефенсив — задание П-7 описывает
 * response.profiles[]/groups[] массивами): распознаётся, только если
 * buckets-объекты формата А не встретились. Битые элементы пропускаются.
 */
private fun parseFollowSubscriptionsPage(response: JsonObject): FollowSubscriptionsPage {
    val entries = mutableListOf<FollowListEntry>()

    fun bucketEntries(bucket: JsonObject?, isGroup: Boolean) {
        val items = bucket?.getAsJsonArray("items") ?: return
        for (el in items) {
            if (!el.isJsonObject) continue
            val entry = if (isGroup) followEntryFromGroupJson(el.asJsonObject) else followEntryFromUserJson(el.asJsonObject)
            if (entry != null) entries.add(entry)
        }
    }

    val usersObj = response.getAsJsonObject("users")
    val groupsObj = response.getAsJsonObject("groups")
    bucketEntries(usersObj, isGroup = false)
    bucketEntries(groupsObj, isGroup = true)

    if (usersObj == null && groupsObj == null) {
        val profiles = response.getAsJsonArray("profiles")
        if (profiles != null) {
            for (el in profiles) {
                if (!el.isJsonObject) continue
                followEntryFromUserJson(el.asJsonObject)?.let { entries.add(it) }
            }
        }
        val groupArray = response.getAsJsonArray("groups")
        if (groupArray != null) {
            for (el in groupArray) {
                if (!el.isJsonObject) continue
                followEntryFromGroupJson(el.asJsonObject)?.let { entries.add(it) }
            }
        }
    }

    val explicit = response.get("count")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
    val total = if (explicit >= 0) {
        explicit
    } else {
        val usersCount = usersObj?.get("count")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
        val groupsCount = groupsObj?.get("count")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
        if (usersCount >= 0 || groupsCount >= 0) {
            usersCount.coerceAtLeast(0) + groupsCount.coerceAtLeast(0)
        } else {
            -1
        }
    }
    return FollowSubscriptionsPage(total = total, entries = entries)
}

/**
 * П-7-AB: экран «Подписчики» ([Screen.FollowList.MODE_FOLLOWERS]) или
 * «Подписки» ([Screen.FollowList.MODE_SUBSCRIPTIONS]) пользователя [userId]
 * (положительный id владельца списка; 0 — экран недоступен, честная ошибка).
 * Подписчики: строка (аватар + имя + статус) → чужой профиль. Подписки:
 * пользователи и сообщества (extended=1) — сообщества → Screen.Community.
 * Фильтр по имени локальный (bl-filter-паттерн BlacklistScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersSubscriptionsScreen(
    userId: Long,
    mode: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSubscriptionsMode = mode == Screen.FollowList.MODE_SUBSCRIPTIONS

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FollowListEntry>>(emptyList()) }
    var total by remember { mutableStateOf(-1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    // Fix #389 #SCROLL-TOP-PARITY: состояние списка для FAB «наверх»
    // (тот же экземпляр передаётся в LazyColumn.state ниже).
    val listState = rememberLazyListState()

    /** Страница по offset → (элементы, total; total=-1 если API его не отдаёт). */
    suspend fun fetchPage(offset: Int): Pair<List<FollowListEntry>, Int> {
        if (isSubscriptionsMode) {
            // ДЕФОЛТ VKA count=5 перекрыт: страницы по [FOLLOW_LIST_PAGE_SIZE].
            val response = app.apiClient.usersGetSubscriptions(
                userId = userId,
                count = FOLLOW_LIST_PAGE_SIZE,
                offset = offset,
            ) ?: throw IllegalStateException(app.apiClient.lastApiError ?: "нет ответа сервера")
            val page = parseFollowSubscriptionsPage(response)
            return page.entries to page.total
        }
        val users: List<UserProfile> =
            app.apiClient.usersGetFollowers(userId = userId, count = FOLLOW_LIST_PAGE_SIZE, offset = offset)
        return users.mapNotNull { u ->
            if (u.id <= 0L) return@mapNotNull null
            FollowListEntry(
                id = u.id,
                isGroup = false,
                title = u.fullName,
                photo = u.photo200 ?: u.photo100,
                subtitle = u.status?.takeIf { it.isNotBlank() },
            )
        } to -1
    }

    fun loadFirst() {
        scope.launch {
            loading = true
            error = null
            try {
                val (page, pageTotal) = fetchPage(0)
                if (page.isEmpty() && !isSubscriptionsMode && app.apiClient.lastApiError != null) {
                    // usersGetFollowers «глотает» ошибку в emptyList — различаем
                    // по lastApiError, чтобы не показывать «нет подписчиков»
                    // при сетевой ошибке (no-stub).
                    error = "Не удалось загрузить: ${app.apiClient.lastApiError}"
                } else {
                    entries = page.distinctBy { "${it.isGroup}_${it.id}" }
                    total = pageTotal
                    endReached =
                        if (pageTotal >= 0) entries.size >= pageTotal else page.size < FOLLOW_LIST_PAGE_SIZE
                }
            } catch (e: Exception) {
                AppLog.e("FollowersSubscriptionsScreen", "loadFirst error", e)
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
                val (page, pageTotal) = fetchPage(entries.size)
                val fresh = page.filter { candidate ->
                    entries.none { it.isGroup == candidate.isGroup && it.id == candidate.id }
                }
                if (fresh.isEmpty()) {
                    // Пустая страница — дальше грузить нечего (buckets ответа
                    // могут отдать 0 items при offset за границей — защита от цикла).
                    endReached = true
                } else {
                    entries = (entries + fresh).distinctBy { "${it.isGroup}_${it.id}" }
                    if (pageTotal >= 0) {
                        total = pageTotal
                        if (entries.size >= pageTotal) endReached = true
                    } else if (page.size < FOLLOW_LIST_PAGE_SIZE) {
                        endReached = true
                    }
                }
            } catch (e: Exception) {
                AppLog.e("FollowersSubscriptionsScreen", "loadMore error", e)
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

    LaunchedEffect(userId, mode) { loadFirst() }

    val filtered = entries.filter { entry ->
        filter.isBlank() || entry.title.lowercase().contains(filter.trim().lowercase())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSubscriptionsMode) "Подписки" else "Подписчики") },
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
                // над списком подписчиков/подписок (пагинация loadMore).
                Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                val (page, pageTotal) = fetchPage(0)
                                if (page.isNotEmpty() || isSubscriptionsMode || app.apiClient.lastApiError == null) {
                                    entries = page.distinctBy { "${it.isGroup}_${it.id}" }
                                    total = pageTotal
                                    endReached = if (pageTotal >= 0) {
                                        entries.size >= pageTotal
                                    } else {
                                        page.size < FOLLOW_LIST_PAGE_SIZE
                                    }
                                }
                            } catch (e: Exception) {
                                AppLog.e("FollowersSubscriptionsScreen", "refresh error", e)
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
                        if (filtered.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (entries.isEmpty()) {
                                        if (isSubscriptionsMode) "Подписок нет" else "Подписчиков нет"
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
                            items(filtered, key = { "entry_${it.isGroup}_${it.id}" }) { entry ->
                                FollowListRow(
                                    entry = entry,
                                    onClick = {
                                        if (entry.isGroup) onGroupClick(entry.id) else onUserClick(entry.id)
                                    },
                                )
                            }
                        }
                        if (!endReached && entries.isNotEmpty()) {
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
                                            Text(
                                                if (total >= 0) {
                                                    "Загрузить ещё (${total - entries.size})"
                                                } else {
                                                    "Загрузить ещё"
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх»
                // (подписчики/подписки — один общий LazyList экрана).
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
                }
            }
        }
    }
}

/**
 * П-7-AB: строка-вход «Подписки» под чипами-счётчиками профиля (свой и чужой).
 * Чип «Подписки» в [CountersRow] добавить НЕЛЬЗЯ: поля subscriptions в модели
 * UserProfile.Counters нет, а правки core-моделей запрещены правилами этапа —
 * доступ к экрану подписок даёт эта строка (веб: секция правой колонки).
 * [count] — из users.getSubscriptions через [followSubscriptionsTotal]
 * (параллельный добор в вызывающем экране); null → строка без числа
 * (счётчик не имитируется, no-stub). Паттерн строки — «Редактировать профиль»/
 * «Создать пост» в ProfileScreen.
 */
@Composable
internal fun SubscriptionsEntryRow(count: Int?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (count != null) "Подписки ($count)" else "Подписки",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Строка списка: аватар (AsyncImage, фоллбэк — первая буква) + имя + подзаголовок
 * (статус у пользователя; описание/статус у сообщества). Сообщества помечены
 * меткой «Сообщество» справа (тап ведёт в Screen.Community, не в профиль).
 */
@Composable
private fun FollowListRow(entry: FollowListEntry, onClick: () -> Unit) {
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
            val subtitle = entry.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.isGroup) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Сообщество",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
