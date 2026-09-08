package re.pinok.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Bookmark
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.util.toCountString

/**
 * Состояние загрузки секции правого меню (#FEED-RIGHTPANEL).
 * LOADING — запрос в полёте (spinner); READY — данные получены (в т.ч. пустые —
 * секция честно пишет «Пусто»); ERROR — реальная ошибка API (текст + «Повторить»).
 */
private enum class RightPanelSectionState {
    LOADING,
    READY,
    ERROR,
}

/** Размер страницы «Возможные друзья» (friends.getSuggestions). #FEED-RIGHTPANEL */
private const val SUGGESTIONS_PAGE_SIZE = 10
/** Размер страницы «Рекомендуемые сообщества» (groups.getCatalog). #FEED-RIGHTPANEL */
private const val CATALOG_PAGE_SIZE = 10
/** Размер страницы «Мои закладки» (fave.get) — VK web сводка ~5 позиций. #FEED-RIGHTPANEL */
private const val BOOKMARKS_PAGE_SIZE = 5

/**
 * #FEED-RIGHTPANEL (19-A, волна 18-ε): правое боковое меню ленты — аналог
 * правой колонки vk.com/feed (друзья онлайн, возможные друзья, рекомендуемые
 * сообщества, мои закладки). Все секции — РЕАЛЬНЫЕ вызовы VKA без заглушек.
 *
 * Реализация оверлея: ModalNavigationDrawer Material3 привязывает drawer
 * ТОЛЬКО к левому краю (end-anchor отсутствует, layoutDirection-хак менял бы
 * поведение всего контекста) → по ТЗ выбран самодельный AnimatedVisibility-
 * оверлей: Scrim на весь контент + панель, выезжающая справа (slideIn/
 * slideOutHorizontally), поверх контента FeedScreen. Панель всегда в
 * композиции (visible-флаг) — анимируются и вход, и выход. BackHandler при
 * открытой панели закрывает её, а не экран ленты.
 *
 * Дни рождения: секция НЕ реализована — friends.get с fields=bdate отсутствует
 * в VKA (файл заморожен), friendsGetOnline bdate не отдаёт; добавление метода —
 * будущая волна. Честное отклонение по ТЗ, не заглушка.
 *
 * «Рекомендуемые сообщества» без кнопки «Ещё»: groups.getCatalog в VKA-фасаде
 * не имеет offset-пагинации (сигнатура count-only), каталог сам подбирает
 * рекомендации — решение по ТЗ задокументировано здесь.
 *
 * Честные ошибки: VKA-методы списков при ошибке/офлайне возвращают emptyList,
 * записывая ошибку в lastApiError в форме «<метод>: <текст>» (callInternal).
 * Отличаем «пусто» от «ошибка» по префиксу метода в lastApiError (одиночный
 * последовательный загрузочный корутина-поток — lastApiError не
 * перемеживается между секциями). Секция с ошибкой показывает реальный текст
 * API + кнопку «Повторить» (реальный повторный вызов).
 *
 * @param visible   панель открыта (управляет анимацией и загрузкой секций)
 * @param onDismiss закрыть панель
 * @param onUserClick переход в профиль (друзья онлайн / возможные друзья /
 *                    закладка-пользователь); из FeedScreen пробрасывается
 *                    реальный колбэк — тапы не глушатся молча
 * @param onGroupClick переход в сообщество (рекомендации / закладка-группа)
 * @param onPostClick переход в детальный экран поста (закладка-пост)
 * @param onVideoClick переход в плеер видео (закладка-видео)
 * @param onPhotoClick просмотр фото (закладка-фото; отдаётся список из 1 URL)
 */
@Composable
fun FeedRightPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    // ── Секция «Друзья онлайн» (friends.getOnline) ──
    var friendsOnlineState by remember { mutableStateOf(RightPanelSectionState.LOADING) }
    var friendsOnlineItems by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var friendsOnlineError by remember { mutableStateOf("") }

    // ── Секция «Возможные друзья» (friends.getSuggestions, offset-пагинация) ──
    var suggestionsState by remember { mutableStateOf(RightPanelSectionState.LOADING) }
    var suggestionsItems by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var suggestionsError by remember { mutableStateOf("") }
    var suggestionsLoadingMore by remember { mutableStateOf(false) }
    var suggestionsEndReached by remember { mutableStateOf(false) }

    // ── Секция «Рекомендуемые сообщества» (groups.getCatalog) ──
    var catalogState by remember { mutableStateOf(RightPanelSectionState.LOADING) }
    var catalogItems by remember { mutableStateOf<List<VKApiClient.GroupInfo>>(emptyList()) }
    var catalogError by remember { mutableStateOf("") }

    // ── Секция «Мои закладки» (fave.get) ──
    var bookmarksState by remember { mutableStateOf(RightPanelSectionState.LOADING) }
    var bookmarksItems by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var bookmarksError by remember { mutableStateOf("") }

    // ── Загрузчики: suspend — вызываются ПОСЛЕДОВАТЕЛЬНО (одна корутина),
    // чтобы lastApiError каждой секции читался детерминированно. ──

    /** «Друзья онлайн»: friends.getOnline(userId = null) — реальный вызов. */
    suspend fun loadFriendsOnline() {
        friendsOnlineState = RightPanelSectionState.LOADING
        val errBefore = app.apiClient.lastApiError
        val list = app.apiClient.friendsGetOnline(userId = null)
        if (list.isNotEmpty()) {
            friendsOnlineItems = list
            friendsOnlineError = ""
            friendsOnlineState = RightPanelSectionState.READY
            return
        }
        // Пустой ответ: отличаем честное «пусто» от ошибки API.
        // NULL-ЯВНО: Gson/VKA-фасад не сигнализирует ошибку из списка —
        // различаем по стейту lastApiError (конвенция BookmarksScreen:122,
        // уточнено префиксом метода — см. KDoc класса).
        val errAfter = app.apiClient.lastApiError
        if (errAfter != null && errAfter.startsWith("friends.getOnline:")) {
            friendsOnlineError = errAfter
            friendsOnlineState = RightPanelSectionState.ERROR
            return
        }
        if (errAfter != null && errAfter != errBefore) {
            // Свежая непрефиксованная ошибка, записанная этим вызовом.
            friendsOnlineError = errAfter
            friendsOnlineState = RightPanelSectionState.ERROR
            return
        }
        friendsOnlineItems = emptyList()
        friendsOnlineError = ""
        friendsOnlineState = RightPanelSectionState.READY
    }

    /**
     * «Возможные друзья»: friends.getSuggestions(count, offset). При
     * reset=true — первая страница; иначе дозагрузка с offset = items.size
     * (реальная offset-пагинация по ТЗ).
     */
    suspend fun loadSuggestions(reset: Boolean) {
        if (reset) {
            suggestionsState = RightPanelSectionState.LOADING
            suggestionsEndReached = false
        } else {
            suggestionsLoadingMore = true
        }
        val errBefore = app.apiClient.lastApiError
        val offset = if (reset) 0 else suggestionsItems.size
        val page = app.apiClient.friendsGetSuggestions(count = SUGGESTIONS_PAGE_SIZE, offset = offset)
        suggestionsLoadingMore = false
        if (page.isNotEmpty()) {
            suggestionsItems = if (reset) page else suggestionsItems + page
            // Короткая страница — VK выдал меньше запрошенного: конец списка.
            suggestionsEndReached = page.size < SUGGESTIONS_PAGE_SIZE
            suggestionsError = ""
            suggestionsState = RightPanelSectionState.READY
            return
        }
        val errAfter = app.apiClient.lastApiError
        if (errAfter != null && (errAfter.startsWith("friends.getSuggestions:") || errAfter != errBefore)) {
            if (reset) {
                suggestionsError = errAfter
                suggestionsState = RightPanelSectionState.ERROR
            }
            // Дозагрузка при ошибке: предыдущие данные остаются, «Показать ещё»
            // остаётся доступной — повторный тап = реальная повторная попытка.
            return
        }
        // Честное «пусто»: первая страница пуста → READY с пустым списком;
        // дозагрузка пуста → конец списка.
        if (reset) {
            suggestionsItems = emptyList()
            suggestionsError = ""
            suggestionsState = RightPanelSectionState.READY
        }
        suggestionsEndReached = true
    }

    /** «Рекомендуемые сообщества»: groups.getCatalog(count = 10). */
    suspend fun loadCatalog() {
        catalogState = RightPanelSectionState.LOADING
        val errBefore = app.apiClient.lastApiError
        val list = app.apiClient.groupsGetCatalog(count = CATALOG_PAGE_SIZE)
        if (list.isNotEmpty()) {
            catalogItems = list
            catalogError = ""
            catalogState = RightPanelSectionState.READY
            return
        }
        val errAfter = app.apiClient.lastApiError
        if (errAfter != null && errAfter.startsWith("groups.getCatalog:")) {
            catalogError = errAfter
            catalogState = RightPanelSectionState.ERROR
            return
        }
        if (errAfter != null && errAfter != errBefore) {
            catalogError = errAfter
            catalogState = RightPanelSectionState.ERROR
            return
        }
        catalogItems = emptyList()
        catalogError = ""
        catalogState = RightPanelSectionState.READY
    }

    /** «Мои закладки»: faveGet(count = 5) — сводка последних закладок. */
    suspend fun loadBookmarks() {
        bookmarksState = RightPanelSectionState.LOADING
        val errBefore = app.apiClient.lastApiError
        val list = app.apiClient.faveGet(count = BOOKMARKS_PAGE_SIZE)
        if (list.isNotEmpty()) {
            bookmarksItems = list
            bookmarksError = ""
            bookmarksState = RightPanelSectionState.READY
            return
        }
        val errAfter = app.apiClient.lastApiError
        if (errAfter != null && errAfter.startsWith("fave.get:")) {
            bookmarksError = errAfter
            bookmarksState = RightPanelSectionState.ERROR
            return
        }
        if (errAfter != null && errAfter != errBefore) {
            bookmarksError = errAfter
            bookmarksState = RightPanelSectionState.ERROR
            return
        }
        bookmarksItems = emptyList()
        bookmarksError = ""
        bookmarksState = RightPanelSectionState.READY
    }

    // Открытие панели → последовательная загрузка всех секций (каждая
    // независимо держит loading/error/empty — сбой одной не рушит другие).
    LaunchedEffect(visible) {
        if (visible) {
            loadFriendsOnline()
            loadSuggestions(reset = true)
            loadCatalog()
            loadBookmarks()
        }
    }

    // Back при открытой панели закрывает панель (не экран ленты).
    BackHandler(enabled = visible) { onDismiss() }

    // ── Оверлей: Scrim + панель. Дети Box: сначала Scrim, потом панель —
    // панель выше по z, клики по ней не доходят до Scrim. ──
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Scrim: тап вне панели закрывает её (индикация клика отключена —
            // паттерн VideoPlayerScreen:1899: remember { MutableInteractionSource() }).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Шапка панели: заголовок + крестик закрытия.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Меню ленты",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть меню",
                            )
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                    ) {
                        // ── 1. Друзья онлайн ──
                        PanelSectionHeader("Друзья онлайн")
                        when (friendsOnlineState) {
                            RightPanelSectionState.LOADING -> PanelSectionLoading()
                            RightPanelSectionState.ERROR -> PanelSectionError(
                                message = friendsOnlineError,
                                onRetry = { scope.launch { loadFriendsOnline() } },
                            )
                            RightPanelSectionState.READY -> {
                                if (friendsOnlineItems.isEmpty()) {
                                    PanelSectionEmpty("Нет друзей онлайн")
                                } else {
                                    friendsOnlineItems.forEach { friend ->
                                        FriendRow(friend = friend, onClick = { onUserClick(friend.id) })
                                    }
                                }
                            }
                        }
                        HorizontalDivider()

                        // ── 2. Возможные друзья ──
                        PanelSectionHeader("Возможные друзья")
                        when (suggestionsState) {
                            RightPanelSectionState.LOADING -> PanelSectionLoading()
                            RightPanelSectionState.ERROR -> PanelSectionError(
                                message = suggestionsError,
                                onRetry = { scope.launch { loadSuggestions(reset = true) } },
                            )
                            RightPanelSectionState.READY -> {
                                if (suggestionsItems.isEmpty()) {
                                    PanelSectionEmpty("Пока нет рекомендаций друзей")
                                } else {
                                    suggestionsItems.forEach { friend ->
                                        FriendRow(friend = friend, onClick = { onUserClick(friend.id) })
                                    }
                                    // Offset-пагинация: «Показать ещё» = friendsGetSuggestions
                                    // со смещением suggestionsItems.size (реальный offset).
                                    if (!suggestionsEndReached) {
                                        TextButton(
                                            onClick = { scope.launch { loadSuggestions(reset = false) } },
                                            enabled = !suggestionsLoadingMore,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        ) {
                                            if (suggestionsLoadingMore) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text("Показать ещё")
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()

                        // ── 3. Рекомендуемые сообщества ──
                        PanelSectionHeader("Рекомендуемые сообщества")
                        when (catalogState) {
                            RightPanelSectionState.LOADING -> PanelSectionLoading()
                            RightPanelSectionState.ERROR -> PanelSectionError(
                                message = catalogError,
                                onRetry = { scope.launch { loadCatalog() } },
                            )
                            RightPanelSectionState.READY -> {
                                if (catalogItems.isEmpty()) {
                                    PanelSectionEmpty("Пока нет рекомендаций сообществ")
                                } else {
                                    catalogItems.forEach { group ->
                                        GroupRow(group = group, onClick = { onGroupClick(group.id) })
                                    }
                                }
                            }
                        }
                        HorizontalDivider()

                        // ── 4. Мои закладки ──
                        PanelSectionHeader("Мои закладки")
                        when (bookmarksState) {
                            RightPanelSectionState.LOADING -> PanelSectionLoading()
                            RightPanelSectionState.ERROR -> PanelSectionError(
                                message = bookmarksError,
                                onRetry = { scope.launch { loadBookmarks() } },
                            )
                            RightPanelSectionState.READY -> {
                                if (bookmarksItems.isEmpty()) {
                                    PanelSectionEmpty("Закладок пока нет")
                                } else {
                                    bookmarksItems.forEach { bm ->
                                        BookmarkRow(
                                            bookmark = bm,
                                            onUserClick = onUserClick,
                                            onGroupClick = onGroupClick,
                                            onPostClick = onPostClick,
                                            onVideoClick = onVideoClick,
                                            onPhotoClick = onPhotoClick,
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
}

/**
 * Заголовок секции (#FEED-RIGHTPANEL) — стиль VK web: небольшой жирный
 * заголовок блока правой колонки.
 */
@Composable
private fun PanelSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * Состояние загрузки секции (#FEED-RIGHTPANEL) — CircularProgressIndicator
 * по центру фиксированной высоты (прецедент: LazyColumn-загрузчики FeedScreen).
 */
@Composable
private fun PanelSectionLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/**
 * Честное «пусто» секции (#FEED-RIGHTPANEL): реальный пустой ответ API.
 */
@Composable
private fun PanelSectionEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Ошибка секции (#FEED-RIGHTPANEL): реальный текст ошибки API + «Повторить»
 * (реальный повторный вызов того же метода).
 */
@Composable
private fun PanelSectionError(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Не удалось загрузить",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

/**
 * Аватар с фолбэком-буквой (#FEED-RIGHTPANEL). Без URL — честный глиф первой
 * буквы имени на нейтральном фоне (не картинка-заглушка из сети).
 */
@Composable
private fun PanelAvatar(url: String?, fallback: String, size: Dp) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallback.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Строка друга (секции «Друзья онлайн» / «Возможные друзья», #FEED-RIGHTPANEL):
 * аватар + имя + зелёный индикатор online (VK web). Тап — реальный переход
 * в профиль через onUserClick.
 */
@Composable
private fun FriendRow(friend: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            PanelAvatar(
                url = friend.photo100,
                fallback = "${friend.firstName} ${friend.lastName}",
                size = 40.dp,
            )
            if (friend.online == 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${friend.firstName} ${friend.lastName}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Строка рекомендуемого сообщества (#FEED-RIGHTPANEL): аватар + имя +
 * число участников (toCountString). Тап — реальный переход в сообщество.
 */
@Composable
private fun GroupRow(group: VKApiClient.GroupInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelAvatar(url = group.photo200, fallback = group.name, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.membersCount > 0) {
                Text(
                    text = "${group.membersCount.toCountString()} участников",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Честная строка закладки (#FEED-RIGHTPANEL): тип объекта + заголовок/сниппет
 * + миниатюра (Bookmark.thumbUrl — реальное фото если есть). Переход по типу:
 * пост → детальный экран, видео → плеер, фото → просмотрщик, юзер/группа →
 * профиль/сообщество; ссылка и неизвестные типы — рендер БЕЗ навигации
 * (честно: экрана-перехода для типа нет, фейковый переход запрещён ТЗ).
 */
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onUserClick: (Long) -> Unit,
    onGroupClick: (Long) -> Unit,
    onPostClick: (Post) -> Unit,
    onVideoClick: (Video) -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    // Сущности захватываются в локальные val до null-проверок
    // (смарт-каст на свойства чужого модуля ненадёжен — #ARCH-CONTAINERS 3.7-1).
    val post = bookmark.post
    val video = bookmark.video
    val photo = bookmark.photo
    val user = bookmark.user
    val group = bookmark.group
    val link = bookmark.link

    val canNavigate: Boolean = when {
        post != null && post.id > 0L -> true
        video != null -> true
        photo != null && photo.largestUrl != null -> true
        user != null -> true
        group != null -> true
        else -> false // link/неизвестный тип/битая сущность — без навигации
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                enabled = canNavigate,
                onClick = {
                    if (post != null && post.id > 0L) {
                        onPostClick(post)
                        return@clickable
                    }
                    if (video != null) {
                        onVideoClick(video)
                        return@clickable
                    }
                    if (photo != null) {
                        val url = photo.largestUrl
                        if (url != null) {
                            onPhotoClick(listOf(url), 0)
                        }
                        return@clickable
                    }
                    if (user != null) {
                        onUserClick(user.id)
                        return@clickable
                    }
                    if (group != null) {
                        onGroupClick(group.id)
                        // return@clickable не нужен — последний if.
                    }
                    // Иначе: без перехода (link/неизвестный тип — честный рендер).
                },
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = bookmark.thumbUrl
        if (thumb != null) {
            PanelAvatar(url = thumb, fallback = bookmark.title, size = 40.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bookmarkTypeLabel(bookmark.type).take(1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmarkTypeLabel(bookmark.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bookmark.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (link != null && link.url.isNotBlank()) {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Русская подпись типа закладки (#FEED-RIGHTPANEL). Неизвестный тип выводится
 * как пришёл от API — честно, без маскировки.
 */
private fun bookmarkTypeLabel(type: String): String = when (type) {
    "user" -> "Пользователь"
    "group" -> "Сообщество"
    "post" -> "Запись"
    "photo" -> "Фото"
    "video" -> "Видео"
    "link" -> "Ссылка"
    "article" -> "Статья"
    "product" -> "Товар"
    "clip" -> "Клип"
    else -> type
}
