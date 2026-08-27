package re.pinok.ui.screens.im

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.widget.Toast
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.ChatFolder
import re.pinok.realtime.LongPollEvent
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog
import re.pinok.util.toMsgTime
import re.pinok.ui.components.SkeletonChatList
import re.pinok.ui.components.ErrorView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onChatClick: (Chat) -> Unit = {},
    onFoldersSettings: () -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val snap by app.prefs.data.collectAsState(initial = null)
    val s = snap ?: return
    // #FAVE-SELF-CHAT: «Избранное» — постоянный self-чат (peer_id = myUserId).
    // VK не возвращает пустой self-chat в messages.getConversations, поэтому
    // показываем его как pinned-entry в начале списка «Диалоги».
    // #MSG-FAVORITES-TOGGLE: скрывается настройкой msgShowFavorites.
    val myUserId = remember { app.exchangeAuthRepository.userId() }
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Fix #127: было 40 — VK API отдаёт только первые 40 диалогов/каналов.
    // Если у пользователя >40 подписок (каналы + ЛС + чаты) — остальные НЕ видны.
    // VK API максимум count=200. Грузим сразу 200 — один запрос, без пагинации.
    val pageSize = 200
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Fix #127: infinite scroll — если даже 200 не хватило, подгружаем дальше.
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    // §52.5 Sprint A (P0): список диалогов грузится legacy getConversations
    // (offset-пагинация надёжна). Modern Sync (getDiff) влияет только на
    // LongPoll-credentials в LongPollClient; курсор getItems неразгадан без
    // нестандартного примера start_from (см. #GETITEMS-CURSOR).
    // Fix #129: используем listState.layoutInfo.totalItemsCount вместо
    // filteredChats.size — filteredChats объявлен ниже (после tabs/folders),
    // и Kotlin не позволяет forward-reference на локальный val. Свойство
    // totalItemsCount в рантайме равно количеству элементов в LazyColumn
    // (== filteredChats.size, т.к. список рендерит filteredChats), поэтому
    // семантика триггера пагинации не меняется. Guard > 0 предотвращает
    // срабатывание на пустом списке (0 - 5 = -5 → всегда true).
    val reachedEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    // P1.4: search + tabs feature-flag.
    val searchEnabled by app.prefs.data
        .map { it.msgSearch }
        .collectAsState(initial = true)

    // P3.3: folders system feature-flag (default false — opt-in).
    val foldersEnabled by app.prefs.data
        .map { it.msgFolders }
        .collectAsState(initial = false)

    // §44 #DNR-MARK-READ-UX: DNR (Do Not Read / «не читалка») state.
    // Если включён — messages.markAsRead подавляется в VKApiClient, НО раньше
    // UI оптимистично чистил бейдж непрочитанных (chat.copy(unreadCount=0))
    // ПЕРЕД API call → бейдж исчезал, потом возвращался при refresh.
    // Пользователь: «при попытке отметить что прочитано, ничего не происходит».
    // Теперь: при DNR=on НЕ чистим бейдж + показываем Toast с объяснением.
    val msgDnr by app.prefs.data
        .map { it.msgDnr }
        .collectAsState(initial = false)

    // P3.3: пользовательские папки диалогов (из FoldersRepository).
    var folders by remember { mutableStateOf<List<ChatFolder>>(emptyList()) }
    LaunchedEffect(foldersEnabled) {
        if (foldersEnabled) {
            try {
                folders = app.foldersRepository.loadFolders()
            } catch (e: Exception) {
                AppLog.w("MessagesScreen", "loadFolders: ${e.message}")
            }
        } else {
            folders = emptyList()
        }
    }

    // P1.4: search query + active tab.
    var searchQuery by remember { mutableStateOf("") }
    // Fix #258: поиск перенесён в глобальный TopAppBar через ScreenTopBar.
    var showSearch by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Все, 1=Каналы, 2=Непрочитанные
    // P3.3: safety-clamp — если активная вкладка указывает на удалённую папку,
    // сбрасываем на «Все» (0). max valid = folders.size + 1 (Непрочитанные).
    LaunchedEffect(folders.size, foldersEnabled) {
        if (foldersEnabled && activeTab > folders.size + 1) {
            activeTab = 0
        }
    }

    // Fix #274 + Fix #276: локальный override порядка закреплённых диалогов.
    // VK API messages.markAsImportantConversation требует special-scope user
    // token (выдаётся только по запросу в support) ИЛИ community token. Наш
    // web-token (vk1.a.*) отвергается с err=8 "method available only for
    // group messages". Поэтому:
    //  - localPinnedOrder — source of truth для UI (какие чаты закреплены
    //    и в каком порядке). Персистится в PinnedConversationsRepository
    //    (SovaPrefs.pinnedConvsData, JSON array of peer_id).
    //  - API-вызов делается best-effort в фоне: если когда-нибудь VK разрешит
    //    нашему токену — сервер тоже подхватит; если нет (текущий случай) —
    //    локальное состояние всё равно сохранится между сессиями.
    //  - При сортировке: сначала идут pinned из localPinnedOrder (в их порядке),
    //    потом остальные pinned (по VK major_id DESC), потом unpinned.
    var localPinnedOrder by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Первичная загрузка диалогов.
    LaunchedEffect(Unit) {
        // FIX: используем корутину LaunchedEffect напрямую вместо scope.launch,
        // чтобы избежать ForgottenCoroutineScopeException при пересоздании Activity.
        // Fix #276: сначала грузим локально закреплённые peer_id (source of truth).
        // Это нужно ДО отображения чатов, чтобы сортировка сразу учла pinned.
        try {
            localPinnedOrder = app.pinnedConvsRepository.load()
            if (localPinnedOrder.isNotEmpty()) {
                AppLog.d("MessagesScreen", "Loaded ${localPinnedOrder.size} locally pinned: $localPinnedOrder")
            }
        } catch (e: Exception) {
            AppLog.w("MessagesScreen", "Failed to load local pinned: ${e.message}")
        }
        loading = true
        errorText = null
        hasMore = true  // Fix #127: сброс пагинации при первичной загрузке.
        // Fix #339: retry на transient IOException ("Socket is closed" — connection
        // pool evicted / network switch посреди запроса). Без retry юзер видел
        // «Не удалось загрузить диалоги: Socket is closed» вместо диалогов.
        // 3 попытки с backoff 500мс / 1.5с / 3с — покрывает evictAll + network switch.
        var attempt = 0
        var list: List<re.pinok.data.model.Chat> = emptyList()
        var lastException: Exception? = null
        while (attempt < 3) {
            try {
                list = app.apiClient.messagesGetConversations(count = pageSize)
                    // Fix #53: защитная дедупликация — LazyColumn keys должны быть уникальны.
                    .distinctBy { it.peer.id }
                lastException = null
                break
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastException = e
                // err=5 (токен истёк) — не transient, retry не поможет. VKApiClient
                // уже сделал retry + grace period. Прерываем цикл, ниже обработаем.
                if (app.apiClient.lastApiErrorCode == 5 || app.apiClient.lastApiErrorCode == 1117) break
                attempt++
                if (attempt < 3) {
                    AppLog.w("MessagesScreen", "loadChats attempt $attempt failed: ${e.message} — retry")
                    kotlinx.coroutines.delay(if (attempt == 1) 500L else if (attempt == 2) 1500L else 3000L)
                }
            }
        }
        try {
            // §44 #MSG-REQUESTS: догружаем папку «Запросы» (сообщения от не-друзей).
            // VK возвращает их отдельным filter=message_request — default getConversations
            // их исключает. Мерджим в общий список, дедуплицируем по peer.id.
            // Запросы могут быть пустыми (нет запросов) или вернуть err (фильтр не
            // поддерживается) — в обоих случаях не ломаем основной список.
            if (list.isNotEmpty()) {
                try {
                    val requests = app.apiClient.messagesGetConversationRequests(count = 50)
                    if (requests.isNotEmpty()) {
                        val existingIds = list.map { it.peer.id }.toHashSet()
                        val newRequests = requests.filter { it.peer.id !in existingIds }
                        if (newRequests.isNotEmpty()) {
                            list = (list + newRequests)
                                .sortedByDescending { it.lastMessage?.date ?: 0L }
                            AppLog.i("MessagesScreen",
                                "loadChats: merged ${newRequests.size} message_request(s) " +
                                    "from non-friends into conversation list")
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    // Не ломаем основной список если requests-fetch упал.
                    AppLog.w("MessagesScreen", "loadChats: message_request fetch failed (non-fatal): ${e.message}")
                }
            }
            chats = list
            // Fix #127: если VK вернул меньше запрошенного — больше страниц нет.
            if (list.size < pageSize) hasMore = false
            // #MODERN-SYNC-CURSOR: legacy getConversations отдаёт не все каналы
            // (2 из 10) — догружаем полный список через messages.getItems.
            // ВАЖНО: вызываем ПОСЛЕ цикла пагинации, а не внутри — иначе
            // следующий while-цикл перезапишет chats = list и каналы пропадут.
            try {
                val allChannels = app.apiClient.messagesGetAllChannels()
                if (allChannels.isNotEmpty()) {
                    val existingIds = chats.map { it.peer.id }.toHashSet()
                    val missing = allChannels.filter { it.peer.id !in existingIds }
                    if (missing.isNotEmpty()) {
                        chats = (chats + missing).distinctBy { it.peer.id }
                        AppLog.i("MessagesScreen", "loadChats: merged ${missing.size} missing channels via getItems")
                    }
                }
            } catch (e: Exception) {
                AppLog.w("MessagesScreen", "loadChats: getAllChannels failed (non-fatal): ${e.message}")
            }
            if (list.isEmpty()) {
                // Fix #339: приоритет — lastException (transient IOException после 3 retry).
                // Иначе VK API errCode может быть 0/stale → покажем «Нет диалогов» вместо
                // реальной ошибки сети.
                if (lastException != null) {
                    errorText = "Не удалось загрузить диалоги: ${lastException.message}"
                    AppLog.e("MessagesScreen", "loadChats failed after $attempt retries", lastException)
                } else {
                    val err = app.apiClient.lastApiError
                    val errCode = app.apiClient.lastApiErrorCode
                    errorText = when (errCode) {
                        15 -> "VK API error 15: доступ к сообщениям запрещён.\n\n" +
                            "Если вы вошли через браузер VK (Kate mobile) — это ограничение VK " +
                            "для сторонних приложений. Попробуйте перезайти через Direct Auth " +
                            "(телефон + пароль) на экране входа, если он не заблокирован flood_control."
                        // Fix #339: при err=5 НЕ показываем «Авторизуйтесь заново» — silent
                        // re-login уже идёт в фоне (AuthActivity в silent mode). Показываем
                        // null → UI остаётся в loading, после re-login recomposition перезапустит
                        // LaunchedEffect через currentAuthVersion и данные подгрузятся.
                        5, 1117 -> null
                        else -> if (err != null) "Ошибка: $err" else "Нет диалогов"
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    // Pull-to-refresh — перезагрузка списка диалогов.
    fun refreshChats() {
        scope.launch {
            isRefreshing = true
            hasMore = true  // Fix #127: сброс пагинации при refresh.
            try {
                val list = app.apiClient.messagesGetConversations(count = pageSize)
                    .distinctBy { it.peer.id }
                chats = list
                if (list.size < pageSize) hasMore = false
                errorText = null
            } catch (e: Exception) {
                AppLog.w("MessagesScreen", "refreshChats failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Real-time обновление списка диалогов через LongPoll.
    LaunchedEffect(Unit) {
        app.longPollClient.events.collect { ev ->
            val relevant = when (ev) {
                is LongPollEvent.NewMessage -> true
                is LongPollEvent.DialogUpdate -> true
                is LongPollEvent.EditMessage -> true
                LongPollEvent.UnreadCountersChanged -> true
                LongPollEvent.Reset -> true
                else -> false
            }
            if (!relevant) return@collect
            if (loading || isRefreshing) return@collect
            scope.launch {
                try {
                    val targetCount = maxOf(chats.size, pageSize)
                    val fresh = app.apiClient.messagesGetConversations(count = targetCount)
                        .distinctBy { it.peer.id }
                    if (fresh.isNotEmpty()) {
                        chats = fresh
                        errorText = null
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Fix #149: rememberCoroutineScope отменяется при уходе экрана /
                    // рекомпозиции — это нормальный lifecycle, не ошибка. Раньше
                    // логировалось как "LongPoll re-fetch error: rememberCoroutineScope
                    // left the composition" и засоряло лог. Пробрасываем отмену дальше.
                    throw e
                } catch (e: Exception) {
                    AppLog.w("MessagesScreen", "LongPoll re-fetch error: ${e.message}")
                }
            }
        }
    }

    // Fix #127: infinite scroll — подгрузка следующих страниц при достижении конца списка.
    // Если chats.size < pageSize — значит VK вернул меньше запрошенного → больше нет.
    // Иначе при reachedEnd грузим offset=chats.size, count=pageSize, добавляем к списку.
    LaunchedEffect(reachedEnd, hasMore) {
        if (!reachedEnd || !hasMore || isLoadingMore || loading || isRefreshing) return@LaunchedEffect
        if (chats.isEmpty()) return@LaunchedEffect
        isLoadingMore = true
        scope.launch {
            try {
                val offset = chats.size
                AppLog.d("MessagesScreen", "loadMore: offset=$offset, count=$pageSize")
                val more = app.apiClient.messagesGetConversations(count = pageSize, offset = offset)
                    .distinctBy { it.peer.id }
                if (more.isEmpty()) {
                    hasMore = false
                    AppLog.d("MessagesScreen", "loadMore: no more chats (empty response)")
                } else {
                    // Дедупликация с существующими + добавление.
                    val existingIds = chats.map { it.peer.id }.toHashSet()
                    val newOnes = more.filter { it.peer.id !in existingIds }
                    if (newOnes.isEmpty()) {
                        hasMore = false
                    } else {
                        chats = (chats + newOnes)
                        AppLog.d("MessagesScreen", "loadMore: +${newOnes.size} chats (total now ${chats.size})")
                        // Если VK вернул меньше запрошенного — это последняя страница.
                        if (more.size < pageSize) hasMore = false
                    }
                }
            } catch (e: Exception) {
                AppLog.w("MessagesScreen", "loadMore error: ${e.message}")
            } finally {
                isLoadingMore = false
            }
        }
    }

    if (loading) {
        SkeletonChatList(count = 8)
        return
    }
    if (errorText != null && chats.isEmpty()) {
        ErrorView(
            message = errorText,
            onRetry = { refreshChats() },
        )
        return
    }

    // P1.4 + P3.3: filtered chats — search query + tab filter.
    // Fix #274: + сортировка pinned наверх + локальный reorder.
    // Вычисляется через derivedStateOf для эффективности (пересчёт только при
    // изменении chats, searchQuery, activeTab, folders, foldersEnabled, localPinnedOrder).
    val filteredChats by remember(chats, searchQuery, activeTab, folders, foldersEnabled, localPinnedOrder) {
        derivedStateOf {
            var result = chats
            if (foldersEnabled) {
                // P3.3: динамические табы — 0=Все, 1..N=папки, N+1=Непрочитанные.
                val unreadIdx = folders.size + 1
                when {
                    activeTab == 0 -> {} // Все
                    activeTab == unreadIdx -> result = result.filter { it.unreadCount > 0 }
                    activeTab in 1..folders.size -> {
                        val folder = folders[activeTab - 1]
                        result = result.filter { it.peer.id in folder.peerIds }
                    }
                }
            } else {
                // Legacy 3-tab mode: 0=Диалоги, 1=Каналы, 2=Непрочитанные.
                // #DIALOGS-TAB: «Диалоги» — всё КРОМЕ каналов (broadcast-сообщества);
                // «Каналы» — только каналы (isChannel = group && can_write.allowed=false).
                when (activeTab) {
                    0 -> result = result.filter { !it.isChannel }
                    1 -> result = result.filter { it.isChannel }
                    2 -> result = result.filter { it.unreadCount > 0 }
                    else -> {}
                }
            }
            // Search filter (client-side, case-insensitive)
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim()
                result = result.filter { chat ->
                    chat.peer.title?.contains(q, ignoreCase = true) == true
                }
            }
            // Fix #274 + Fix #276: сортировка — закреплённые (pinned) вверху, обычные внизу.
            //  - Pinned = peer.id в localPinnedOrder (локальное закрепление,
            //    source of truth) ИЛИ sortId.majorId > 0 ИЛИ important == true
            //    (серверное закрепление, если когда-нибудь VK разрешит API).
            //  - Внутри pinned: сначала те, что в localPinnedOrder (в их порядке),
            //    потом остальные pinned по VK majorId DESC.
            //  - Внутри unpinned: по timestamp последнего сообщения DESC (как в VK).
            val pinned = result.filter {
                it.peer.id in localPinnedOrder || it.sortId?.isPinned() == true || it.important == true
            }
            val unpinned = result.filterNot {
                it.peer.id in localPinnedOrder || it.sortId?.isPinned() == true || it.important == true
            }
            val pinnedSorted = buildList {
                // Сначала pinned из localPinnedOrder (в порядке, заданном пользователем).
                localPinnedOrder.forEach { peerId ->
                    pinned.firstOrNull { it.peer.id == peerId }?.let { add(it) }
                }
                // Потом остальные pinned (не в localPinnedOrder) по majorId DESC.
                val remaining = pinned.filterNot { it.peer.id in localPinnedOrder }
                remaining.sortedByDescending { it.sortId?.majorId ?: 0L }.forEach { add(it) }
            }
            val unpinnedSorted = unpinned.sortedByDescending { it.lastMessage?.date ?: 0L }
            pinnedSorted + unpinnedSorted
        }
    }

    // P1.4: badge counts for tabs.
    // FIX (P5.2): бейджи на вкладках показывают НЕПРОЧИТАННОЕ, а не total count чатов.
    // Раньше «Все» = chats.size (40) и «Каналы» = total channels (2) — эти числа
    // никогда не уменьшались при чтении (чаты никуда не исчезают), из-за чего
    // пользователь видел «залипшие» счётчики. Теперь все три бейджа отражают
    // непрочитанное и сбрасываются при просмотре:
    //  - dialogsUnreadSum  = сумма unreadCount по ДИАЛОГАМ (не каналам) — бейдж «Диалоги»
    //  - totalUnreadSum    = сумма unreadCount по ВСЕМ чатам (для папок-режима «Все»)
    //  - channelUnreadSum  = сумма unreadCount только по каналам — бейдж «Каналы»
    //  - unreadCount       = сколько ДИАЛОГОВ имеют непрочитанные (для вкладки «Непрочитанные»)
    val dialogsUnreadSum by remember(chats) {
        derivedStateOf { chats.filter { !it.isChannel }.sumOf { it.unreadCount } }
    }
    val totalUnreadSum by remember(chats) {
        derivedStateOf { chats.sumOf { it.unreadCount } }
    }
    val channelUnreadSum by remember(chats) {
        derivedStateOf {
            chats.filter { it.isChannel }
                .sumOf { it.unreadCount }
        }
    }
    val unreadCount by remember(chats) {
        derivedStateOf { chats.count { it.unreadCount > 0 } }
    }
    // P3.3: сумма непрочитанных по каждой папке (параллельно списку folders).
    // Нужно для бейджей на чипах папок в FolderTabRow.
    val folderUnreadSums by remember(chats, folders) {
        derivedStateOf {
            folders.map { folder ->
                chats.filter { it.peer.id in folder.peerIds }.sumOf { it.unreadCount }
            }
        }
    }

    // Fix #258: регистрируем search в глобальном TopAppBar через ScreenTopBar.
    // Раньше поиск был inline под TopAppBar — теперь в TopAppBar для единообразия
    // с Notifications/Video/Friends/Groups. searchEnabled (feature-flag) теперь
    // просто определяет, показывать ли иконку поиска.
    // Fix #260: showSearch в ключе DisposableEffect — иначе configure()
    // вызывается один раз с showSearch=false → titleOverride=null навсегда
    // и TextField поиска не появляется при тапе на иконку.
    DisposableEffect(searchEnabled, showSearch) {
        if (searchEnabled) {
            val token = ScreenTopBar.configure(
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Поиск",
                            tint = if (showSearch) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                titleOverride = if (showSearch) {
                    {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск по чатам…", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                        )
                    }
                } else null,
            )
            onDispose { ScreenTopBar.clear(token) }
        } else {
            ScreenTopBar.clear()
            onDispose { }
        }
    }

    // Fix #78: PullToRefreshBox — pull-to-refresh списка диалогов.
    // #SCROLL-TO-TOP: обёрнуто в Box для overlay-кнопки «Наверх» (длинная
    // пагинация диалогов — кнопка появляется при прокрутке вниз).
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshChats() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fix #258: inline поиск убран — теперь в TopAppBar.
            // Табы (вкладки) остаются в контенте.
            if (searchEnabled) {
                if (foldersEnabled) {
                    // P3.3: динамические табы — «Все» + папки + «Непрочитанные» + gear.
                    // Scrollable Row т.к. папок может быть много.
                    // FIX (P5.2): бейджи показывают непрочитанное (сбрасывается при чтении).
                    FolderTabRow(
                        folders = folders,
                        activeTab = activeTab,
                        totalUnreadSum = totalUnreadSum,
                        folderUnreadSums = folderUnreadSums,
                        unreadCount = unreadCount,
                        onTabSelect = { activeTab = it },
                        onFoldersSettings = onFoldersSettings,
                    )
                } else {
                    // P1.4: TabRow с 3 табами: Все / Каналы / Непрочитанные.
                    // FIX (P5.2): бейджи показывают непрочитанное (сбрасывается при чтении),
                    // а не total count чатов.
                    PrimaryTabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = {
                                BadgedBox(badge = {
                                    if (dialogsUnreadSum > 0) {
                                        Badge { Text(if (dialogsUnreadSum > 99) "99+" else dialogsUnreadSum.toString()) }
                                    }
                                }) { Text("Диалоги") }
                            },
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = {
                                BadgedBox(badge = {
                                    if (channelUnreadSum > 0) {
                                        Badge { Text(if (channelUnreadSum > 99) "99+" else channelUnreadSum.toString()) }
                                    }
                                }) { Text("Каналы") }
                            },
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = {
                                BadgedBox(badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                                    }
                                }) { Text("Непрочитанные") }
                            },
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                if (filteredChats.isEmpty() && searchQuery.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // #FAVE-SELF-CHAT: «Избранное» (self-chat) — всегда в начале
                    // вкладки «Диалоги» (activeTab == 0), когда нет поиска.
                    val favoritesChat: Chat? = if (myUserId > 0L && activeTab == 0 && searchQuery.isBlank() && s.msgShowFavorites) {
                        Chat(
                            peer = Chat.Peer(
                                id = myUserId,
                                type = "user",
                                localId = myUserId,
                                title = "Избранное",
                                photo = null,
                            ),
                        )
                    } else null

                    // Fix #274 + Fix #276: helper для определения pinned-статуса чата.
                    // peer.id в localPinnedOrder (локальное закрепление, source of truth)
                    // ИЛИ серверное закрепление (sortId.majorId > 0 / important).
                    val isPinnedChat: (Chat) -> Boolean = { c ->
                        c.peer.id in localPinnedOrder ||
                            c.sortId?.isPinned() == true ||
                            c.important == true
                    }
                    // Fix #274: для каждого чата ищем его индекс среди pinned.
                    // Нужно для drag&drop swap (меняем местами в localPinnedOrder).
                    val pinnedPeerIdsInOrder: List<Long> = filteredChats
                        .filter { isPinnedChat(it) }
                        .map { it.peer.id }
                    // #FAVE-SELF-CHAT: pinned строка «Избранное» (не из filteredChats).
                    if (favoritesChat != null) {
                        item(key = "favorites_$myUserId") {
                            val ctx = LocalContext.current
                            ChatCard(
                                chat = favoritesChat,
                                isPinned = false,
                                onClick = { onChatClick(favoritesChat) },
                                onMarkAsRead = { _, _ -> },
                                onToggleMute = { _, _ -> },
                                onTogglePin = { _, _ -> },
                                onToggleUnread = { _, _ -> },
                                onDeleteConversation = { _ -> },
                                onDragSwap = { _, _ -> },
                                pinnedPeerIdsInOrder = emptyList(),
                                pinnedIndex = -1,
                            )
                        }
                    }
                    items(filteredChats, key = { it.peer.id }) { chat ->
                        val ctx = LocalContext.current
                        val isPinned = isPinnedChat(chat)
                        // Fix #274: ищем позицию этого чата среди pinned (для drag-swap).
                        val pinnedIndex = if (isPinned) pinnedPeerIdsInOrder.indexOf(chat.peer.id) else -1
                        ChatCard(
                            chat = chat,
                            isPinned = isPinned,
                            onClick = { onChatClick(chat) },
                            onMarkAsRead = { peerId, lastMsgId ->
                                // §44 #DNR-MARK-READ-UX: при включённом DNR («не читалка»)
                                // messages.markAsRead подавляется на уровне VKApiClient —
                                // отправлять API call бессмысленно. Раньше UI всё равно
                                // оптимистично чистил бейдж (unreadCount=0), но при refresh
                                // VK возвращал реальный unread_count → бейдж возвращался,
                                // и юзер видел «ничего не происходит». Теперь при DNR:
                                //   1. НЕ чистим бейдж (он отражает реальный unread на сервере)
                                //   2. Показываем Toast с понятным объяснением
                                if (msgDnr) {
                                    Toast.makeText(
                                        ctx,
                                        "DNR («не читалка») включён — read receipt не отправляется серверу. " +
                                            "Отключите DNR в настройках, чтобы отмечать прочитанным.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    AppLog.d("MessagesScreen",
                                        "onMarkAsRead(peer=$peerId): suppressed by DNR — toast shown, badge kept")
                                    return@ChatCard  // ← не чистим бейдж, не зовём API
                                }
                                // Оптимистично убираем бейдж непрочитанных сразу.
                                chats = chats.map { c ->
                                    if (c.peer.id == peerId) c.copy(unreadCount = 0) else c
                                }
                                // В фоне вызываем VK API. DNR (Do Not Read) проверяется внутри.
                                scope.launch {
                                    val ok = app.apiClient.messagesMarkAsRead(peerId, lastMsgId)
                                    AppLog.d("MessagesScreen",
                                        "messagesMarkAsRead(peer=$peerId, upTo=$lastMsgId): $ok")
                                }
                            },
                            // Fix #122: long-press → mute/unmute из списка диалогов.
                            // Оптимистично обновляем pushSettings, вызываем API, при
                            // ошибке откатываем + Toast.
                            onToggleMute = { peerId, mute ->
                                val oldSettings = chat.pushSettings
                                val newSettings = re.pinok.data.model.Chat.PushSettings(
                                    disabledForever = if (mute) true else null,
                                    disabledUntil = if (mute) -1L else 0L,
                                )
                                chats = chats.map { c ->
                                    if (c.peer.id == peerId) c.copy(pushSettings = newSettings) else c
                                }
                                scope.launch {
                                    try {
                                        val result = app.apiClient.messagesSetConversationPushSettings(peerId, disabled = mute)
                                        if (result != null) {
                                            // API вернул точные настройки — обновляем из ответа.
                                            chats = chats.map { c ->
                                                if (c.peer.id == peerId) c.copy(pushSettings = result) else c
                                            }
                                            // Fix #285: синхронизируем cached mute-стейт в MessageNotifier,
                                            // иначе после un-mute следующее сообщение всё ещё считалось
                                            // бы заглушённым из cached.muted=true.
                                            re.pinok.realtime.MessageNotifier.setMuted(peerId, result.isMuted())
                                            Toast.makeText(
                                                ctx,
                                                if (mute) "Уведомления выключены" else "Уведомления включены",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            AppLog.i("MessagesScreen", "mute toggled from list: peer=$peerId mute=$mute")
                                        } else {
                                            // Ошибка — откатываем.
                                            chats = chats.map { c ->
                                                if (c.peer.id == peerId) c.copy(pushSettings = oldSettings) else c
                                            }
                                            re.pinok.realtime.MessageNotifier.setMuted(peerId, oldSettings?.isMuted() == true)
                                            Toast.makeText(
                                                ctx,
                                                if (mute) "Не удалось выключить уведомления" else "Не удалось включить уведомления",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            AppLog.w("MessagesScreen", "mute toggle failed: peer=$peerId mute=$mute")
                                        }
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        chats = chats.map { c ->
                                            if (c.peer.id == peerId) c.copy(pushSettings = oldSettings) else c
                                        }
                                        re.pinok.realtime.MessageNotifier.setMuted(peerId, oldSettings?.isMuted() == true)
                                        AppLog.e("MessagesScreen", "mute toggle error", e)
                                        Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            // Fix #274 + Fix #276: long-press → закрепить/открепить диалог.
                            // Оптимистично меняем localPinnedOrder + chats (UI обновится
                            // мгновенно). Персистим в локальное хранилище (source of truth).
                            // API-вызов делаем best-effort в фоне: для web-token он вернёт
                            // err=8 (метод требует special-scope token), но локальное
                            // состояние всё равно сохранится между сессиями. НЕ откатываем.
                            onTogglePin = { peerId, pin ->
                                val newSortId = if (pin) {
                                    // Закрепляем: ставим majorId = max существующих + 1
                                    // (для совместимости с серверным sortId, если когда-нибудь
                                    // API заработает). Локально source of truth = localPinnedOrder.
                                    val maxMajor = chats.maxOfOrNull { it.sortId?.majorId ?: 0L } ?: 0L
                                    Chat.SortId(majorId = maxMajor + 1L, minorId = chat.lastMessage?.date ?: 0L)
                                } else {
                                    Chat.SortId(majorId = 0L, minorId = chat.lastMessage?.date ?: 0L)
                                }
                                chats = chats.map { c ->
                                    if (c.peer.id == peerId) c.copy(
                                        sortId = newSortId,
                                        important = if (pin) true else null,
                                    ) else c
                                }
                                // Обновляем localPinnedOrder (source of truth для UI).
                                if (pin) {
                                    localPinnedOrder = (listOf(peerId) + localPinnedOrder.filter { it != peerId }).distinct()
                                } else {
                                    localPinnedOrder = localPinnedOrder.filterNot { it == peerId }
                                }
                                // Fix #276: персистим в локальное хранилище.
                                val newOrder = localPinnedOrder
                                scope.launch {
                                    try {
                                        app.pinnedConvsRepository.setOrder(newOrder)
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen", "Failed to persist pin($peerId, $pin): ${e.message}")
                                    }
                                }
                                // Best-effort VK API sync (вернёт err=8 для web-token,
                                // но локальное состояние уже сохранено — НЕ откатываем).
                                scope.launch {
                                    try {
                                        val ok = app.apiClient.messagesMarkAsImportantConversation(peerId, pin)
                                        if (ok) {
                                            AppLog.i("MessagesScreen", "pin API sync ok: peer=$peerId pin=$pin")
                                        } else {
                                            AppLog.d("MessagesScreen",
                                                "pin API sync returned false (expected for web-token, local state preserved): peer=$peerId pin=$pin")
                                        }
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen",
                                            "pin API sync error (local state preserved): ${e.message}")
                                    }
                                }
                                Toast.makeText(
                                    ctx,
                                    if (pin) "Закреплено" else "Откреплено",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                AppLog.i("MessagesScreen", "pin toggled locally: peer=$peerId pin=$pin")
                            },
                            // Fix #274: отметить непрочитанным / прочитанным.
                            onToggleUnread = { peerId, unread ->
                                val oldUnread = chat.unreadCount
                                val lastMsgId = chat.lastMessage?.id ?: 0L
                                chats = chats.map { c ->
                                    if (c.peer.id == peerId) c.copy(
                                        // VK semantic: markAsUnreadConversation(unread=true)
                                        // не ставит числовой бейдж, а помечает чат «непрочитанным»
                                        // (жирный шрифт без числа). Эмулируем через unreadCount=1.
                                        unreadCount = if (unread) maxOf(1, c.unreadCount) else 0,
                                    ) else c
                                }
                                scope.launch {
                                    try {
                                        val ok = app.apiClient.messagesMarkAsUnreadConversation(peerId, unread)
                                        // #MARK-READ-REVERT: markAsUnreadConversation(unread=0)
                                        // снимает только «метку непрочитанного», но НЕ чистит
                                        // unread_count на сервере — бейдж возвращался при refresh.
                                        // Реально чистит счётчик messages.markAsRead(start_message_id).
                                        if (!unread && lastMsgId > 0L) {
                                            val cleared = app.apiClient.messagesMarkAsRead(peerId, lastMsgId, force = true)
                                            AppLog.i("MessagesScreen",
                                                "markAsRead (force) after unread-toggle: peer=$peerId upTo=$lastMsgId ok=$cleared")
                                        }
                                        if (ok) {
                                            Toast.makeText(
                                                ctx,
                                                if (unread) "Отмечено непрочитанным" else "Отмечено прочитанным",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            AppLog.i("MessagesScreen", "unread toggled: peer=$peerId unread=$unread")
                                        } else {
                                            // Откат.
                                            chats = chats.map { c ->
                                                if (c.peer.id == peerId) c.copy(unreadCount = oldUnread) else c
                                            }
                                            Toast.makeText(
                                                ctx,
                                                if (unread) "Не удалось отметить непрочитанным" else "Не удалось отметить прочитанным",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        AppLog.e("MessagesScreen", "unread toggle error", e)
                                    }
                                }
                            },
                            // Fix #281: удалить диалог (messages.deleteConversation).
                            // Оптимистично убираем из списка, на ошибке — возвращаем.
                            // Если чат был закреплён — убираем и из pinned-порядка.
                            onDeleteConversation = { peerId ->
                                val removedChat = chat
                                val wasPinned = peerId in pinnedPeerIdsInOrder
                                chats = chats.filter { it.peer.id != peerId }
                                if (wasPinned) {
                                    val newOrder = pinnedPeerIdsInOrder.filter { it != peerId }
                                    localPinnedOrder = newOrder
                                }
                                scope.launch {
                                    try {
                                        val ok = app.apiClient.messagesDeleteConversation(peerId)
                                        if (ok) {
                                            // Убираем из локального pinned-хранилища если был закреплён.
                                            if (wasPinned) {
                                                try {
                                                    app.pinnedConvsRepository.unpin(peerId)
                                                } catch (e: Exception) {
                                                    AppLog.w("MessagesScreen", "pinned unpin after delete: ${e.message}")
                                                }
                                            }
                                            Toast.makeText(ctx, "Диалог удалён", Toast.LENGTH_SHORT).show()
                                            AppLog.i("MessagesScreen", "conversation deleted: peer=$peerId")
                                        } else {
                                            // Откат: возвращаем чат в список.
                                            chats = chats.toMutableList().apply { add(removedChat) }
                                            if (wasPinned) localPinnedOrder = pinnedPeerIdsInOrder
                                            Toast.makeText(ctx, "Не удалось удалить диалог", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        AppLog.e("MessagesScreen", "delete conversation error", e)
                                        chats = chats.toMutableList().apply { add(removedChat) }
                                        if (wasPinned) localPinnedOrder = pinnedPeerIdsInOrder
                                        Toast.makeText(ctx, "Не удалось удалить диалог", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            // Fix #274 + Fix #276: drag&drop — swap с соседним pinned чатом.
                            // Вызывается из ChatCard при detectDragGesturesAfterLongPress,
                            // когда drag пересекает порог соседнего элемента.
                            // Новый порядок персистим в локальное хранилище (source of truth).
                            onDragSwap = { fromIdx, toIdx ->
                                if (fromIdx < 0 || toIdx < 0) return@ChatCard
                                if (fromIdx == toIdx) return@ChatCard
                                if (fromIdx !in pinnedPeerIdsInOrder.indices) return@ChatCard
                                if (toIdx !in pinnedPeerIdsInOrder.indices) return@ChatCard
                                val fromId = pinnedPeerIdsInOrder[fromIdx]
                                val toId = pinnedPeerIdsInOrder[toIdx]
                                // Перестраиваем localPinnedOrder: swap fromIdx ↔ toIdx.
                                val newOrder = pinnedPeerIdsInOrder.toMutableList()
                                val tmp = newOrder[fromIdx]
                                newOrder[fromIdx] = newOrder[toIdx]
                                newOrder[toIdx] = tmp
                                localPinnedOrder = newOrder
                                // Fix #276: персистим новый порядок в локальное хранилище.
                                scope.launch {
                                    try {
                                        app.pinnedConvsRepository.setOrder(newOrder)
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen", "Failed to persist drag reorder: ${e.message}")
                                    }
                                }
                                AppLog.d("MessagesScreen", "drag swap: $fromId ↔ $toId (idx $fromIdx ↔ $toIdx)")
                            },
                            // Fix #274: список всех pinned peerId в текущем порядке
                            // (передаём в ChatCard для расчёта порогов swap).
                            pinnedPeerIdsInOrder = pinnedPeerIdsInOrder,
                            pinnedIndex = pinnedIndex,
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    }
                }
                // Fix #127: footer-indicator при подгрузке следующих страниц.
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        }  // PullToRefreshBox

        // #SCROLL-TO-TOP: кнопка «Наверх» — видна когда список прокручен вниз
        // (firstVisibleItemIndex > 0 или offset > 200). Тап → анимация к item 0.
        val showScrollToTopFab by remember {
            derivedStateOf {
                chats.isNotEmpty() &&
                    (listState.firstVisibleItemIndex > 0 ||
                        listState.firstVisibleItemScrollOffset > 200)
            }
        }
        if (showScrollToTopFab) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
            }
        }
    }  // Box
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatCard(
    chat: Chat,
    isPinned: Boolean = false,
    onClick: () -> Unit = {},
    onMarkAsRead: (peerId: Long, lastMessageId: Long) -> Unit = { _, _ -> },
    onToggleMute: (peerId: Long, mute: Boolean) -> Unit = { _, _ -> },
    // Fix #274: новые callback-и для закрепления, отметки непрочитанным, drag&drop.
    onTogglePin: (peerId: Long, pin: Boolean) -> Unit = { _, _ -> },
    onToggleUnread: (peerId: Long, unread: Boolean) -> Unit = { _, _ -> },
    // Fix #281: удалить диалог (messages.deleteConversation) — с confirm-диалогом.
    onDeleteConversation: (peerId: Long) -> Unit = { _ -> },
    onDragSwap: (fromIdx: Int, toIdx: Int) -> Unit = { _, _ -> },
    pinnedPeerIdsInOrder: List<Long> = emptyList(),
    pinnedIndex: Int = -1,
) {
    val title = chat.peer.title ?: "Диалог ${chat.peer.localId}"
    val photo = chat.peer.photo
    val lastMsgId = chat.lastMessage?.id ?: 0L
    val hasUnread = chat.unreadCount > 0
    // Fix #282: preview последнего сообщения — «Вы: » префикс для исходящих,
    // label типа вложения когда текст пуст, action-текст для service-сообщений.
    val lastMessage = chat.lastMessage
    val preview = if (lastMessage == null) {
        "…"
    } else if (lastMessage.isAction) {
        // Service-сообщение (chat_create, user_joined, …) — actionText содержит
        // человекочитаемый текст («Иван создал чат»). Если его нет — «…».
        lastMessage.actionText ?: "…"
    } else {
        val prefix = if (lastMessage.isOut) "Вы: " else ""
        val body = when {
            lastMessage.text.isNotBlank() -> lastMessage.text
            !lastMessage.attachments.isNullOrEmpty() ->
                lastMessage.attachments.firstOrNull()?.let { attachmentPreviewLabel(it) } ?: "…"
            else -> "…"
        }
        prefix + body
    }
    // Fix #282: read checkmarks для исходящего последнего сообщения.
    // outRead = ID последнего ИСХОДЯЩЕГО сообщения, прочитанного собеседником.
    // lastMessage.id <= outRead → прочитано (✓✓ primary), иначе отправлено (✓ outline).
    val showOutCheckmarks = lastMessage != null && lastMessage.isOut && !lastMessage.isAction
    // Fix warn: null-проверка должна идти первой, чтобы (а) не быть избыточной
    // (showOutCheckmarks уже гарантирует lastMessage != null) и (б) включить
    // smart-cast lastMessage к non-null для доступа к .id в том же &&-цепочке.
    val lastOutRead = lastMessage != null && showOutCheckmarks && lastMessage.id <= chat.outRead
    // P3.2 + Fix #122: mute indicator — используем единый isMuted() helper
    // из PushSettings (учитывает disabled_forever, disabled_until, no_sound).
    val isMuted = chat.pushSettings?.isMuted() == true
    // Fix #122: long-press context menu для mute/unmute прямо из списка диалогов
    // (как в нативном VK). DropdownMenu с пунктами: закрепить, заглушить, непрочитанным.
    var showContextMenu by remember { mutableStateOf(false) }
    // Fix #281: confirm-диалог для удаления диалога (деструктивное действие).
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // Fix #274: высота карточки ~72dp (avatar 48 + padding 12*2). В px для порога swap.
    val cardHeightPx = with(density) { 72.dp.toPx() }

    // Fix #274: локальный drag state. Хранится в ChatCard, не в parent —
    // иначе onDrag callback захватывал бы stale значение dragOffsetY из
    // предыдущей recomposition. Локальный state обновляется синхронно.
    var isDragging by remember { mutableStateOf(false) }
    var localDragOffsetY by remember { mutableStateOf(0f) }

    // Fix #274: rememberUpdatedState — чтобы pointerInput (который keyed на
    // chat.peer.id и НЕ пересоздаётся при swap) всегда видел свежий pinnedIndex.
    // Без этого после swap gesture продолжал бы использовать старый индекс.
    val currentPinnedIndex by androidx.compose.runtime.rememberUpdatedState(pinnedIndex)
    val currentPinnedListSize by androidx.compose.runtime.rememberUpdatedState(pinnedPeerIdsInOrder.size)

    // Fix #274: анимация elevation + scale для dragging-карточки.
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 12f else 0f,
        label = "drag-elevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "drag-scale",
    )
    // Fix #275: переименован в dragAlpha — иначе локальный val затенял
    // GraphicsLayerScope.alpha внутри блока graphicsLayer, и строка
    // `alpha = alpha` падала с "'val' cannot be reassigned".
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.92f else 1f,
        label = "drag-alpha",
    )

    Card(modifier = Modifier
        .fillMaxWidth()
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (isDragging) localDragOffsetY else 0f
            scaleX = scale
            scaleY = scale
            alpha = dragAlpha
        }
        .shadow(elevation = elevation.dp, shape = RoundedCornerShape(0.dp))
        .combinedClickable(
            onClick = {
                // Тап по чату → открываем экран диалога (#43).
                // Если есть непрочитанные — заодно помечаем прочитанными (DNR мод
                // проверяется внутри messagesMarkAsRead).
                if (hasUnread) onMarkAsRead(chat.peer.id, lastMsgId)
                onClick()
            },
            onLongClick = { showContextMenu = true },
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                hasUnread -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isPinned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Box {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Fix #274: drag handle виден только у закреплённых чатов.
                // Long-press на handle начинает перетаскивание (не конфликтует с
                // combinedClickable на Card, т.к. handle — отдельный элемент).
                if (isPinned && pinnedIndex >= 0) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "Перетащить",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .pointerInput(chat.peer.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isDragging = true
                                        localDragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        localDragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        localDragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        localDragOffsetY += dragAmount.y
                                        // Fix #274: определяем целевой индекс для swap.
                                        // Если |localDragOffsetY| > threshold, вызываем
                                        // onDragSwap и корректируем offset (компенсируем
                                        // высоту карточки, чтобы продолжить drag плавно).
                                        val threshold = cardHeightPx * 0.6f
                                        if (localDragOffsetY <= -threshold && currentPinnedIndex > 0) {
                                            // Тащим вверх → swap с предыдущим.
                                            onDragSwap(currentPinnedIndex, currentPinnedIndex - 1)
                                            // После swap позиция карточки в списке сместилась на 1 вверх,
                                            // поэтому добавляем cardHeightPx к offset (компенсация).
                                            localDragOffsetY += cardHeightPx
                                        } else if (localDragOffsetY >= threshold &&
                                            currentPinnedIndex < currentPinnedListSize - 1) {
                                            // Тащим вниз → swap со следующим.
                                            onDragSwap(currentPinnedIndex, currentPinnedIndex + 1)
                                            localDragOffsetY -= cardHeightPx
                                        }
                                    },
                                )
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                // Fix #283: avatar обёрнут в Box(BottomEnd) чтобы поверх угла
                // показать online-индикатор (зелёная точка) для 1-1 диалогов.
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (photo != null) {
                        AsyncImage(model = photo, contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text(text = title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    // Fix #283: online-индикатор — зелёная точка 10dp с surface-кольцом
                    // (2dp) поверх нижнего-правого угла аватара. Только для type="user"
                    // и chat.peer.online == true. VK-зелёный #4CAF50.
                    // Fix #286: defensive guard — точка рисуется ТОЛЬКО для 1-1 диалогов
                    // с пользователем (type=="user" && id>0). Группы (type="group",
                    // id<0), чаты (type="chat", id>=2e9) и каналы — никогда не получают
                    // точку, даже если online-флаг как-то утёк в Peer через merge/default.
                    if (chat.peer.type == "user" && chat.peer.id > 0 && chat.peer.online == true) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = title, style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // Fix #274: pin-иконка рядом с названием (для закреплённых).
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Закреплён",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (isMuted) {
                            Icon(
                                Icons.Outlined.NotificationsOff,
                                contentDescription = "Заглушено",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        chat.lastMessage?.let { lm ->
                            Text(text = lm.date.toMsgTime(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = preview, style = MaterialTheme.typography.bodySmall,
                            color = if (hasUnread)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        // Fix #282: read checkmarks для исходящего последнего сообщения.
                        // ✓ (Done) — отправлено, но собеседник не прочитал (id > outRead).
                        // ✓✓ (DoneAll, primary) — прочитано (id <= outRead).
                        // Checkmarks и unread-badge взаимно исключают друг друга:
                        // unread_count считает ВХОДЯЩИЕ непрочитанные → если он > 0,
                        // последнее сообщение входящее (isOut=false) → checkmarks не рисуются.
                        if (showOutCheckmarks) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (lastOutRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = if (lastOutRead) "Прочитано" else "Отправлено",
                                modifier = Modifier.size(16.dp),
                                tint = if (lastOutRead) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (hasUnread) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(text = chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Fix #274: расширенное DropdownMenu — Закрепить/Открепить, Заглушить,
            // Отметить непрочитанным/прочитанным (как в нативном VK).
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                // Fix #274: Закрепить/Открепить.
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onTogglePin(chat.peer.id, !isPinned)
                    },
                )
                // Fix #122: Заглушить/Включить уведомления.
                DropdownMenuItem(
                    text = { Text(if (isMuted) "Включить уведомления" else "Заглушить") },
                    leadingIcon = {
                        Icon(
                            if (isMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onToggleMute(chat.peer.id, !isMuted)
                    },
                )
                // Fix #274: Отметить непрочитанным / прочитанным.
                DropdownMenuItem(
                    text = { Text(if (hasUnread) "Отметить прочитанным" else "Отметить непрочитанным") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.MarkChatUnread,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onToggleUnread(chat.peer.id, !hasUnread)
                    },
                )
                // Fix #281: Удалить диалог (messages.deleteConversation).
                // Деструктивное действие — после tap открывается confirm-диалог,
                // API вызывается только после подтверждения пользователя.
                DropdownMenuItem(
                    text = { Text("Удалить диалог", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        showDeleteConfirm = true
                    },
                )
            }
            // Fix #281: confirm-диалог удаления диалога.
            // VK API messages.deleteConversation удаляет ВСЮ переписку
            // без возможности восстановления — поэтому всегда спрашиваем.
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Удалить диалог?") },
                    text = {
                        Text(
                            "Вся переписка с «$title» будет удалена без возможности восстановления.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteConversation(chat.peer.id)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Удалить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Отмена")
                        }
                    },
                )
            }
        }
    }
}

/**
 * P3.3: FolderTabRow — скролляемый ряд динамических табов для папок диалогов.
 *
 * Табы: «Все» (0) + папки (1..N) + «Непрочитанные» (N+1) + gear (→ FoldersSettings).
 * Аналог m.vk.ru: `OrganiserViewHorizontal` с `me_folder_tab_filters__all` +
 * `me_folder_tab_folders__*` + `me_folders_settings_gear`.
 *
 * Использует horizontalScroll т.к. папок может быть много (PrimaryTabRow не скроллится).
 */
@Composable
private fun FolderTabRow(
    folders: List<ChatFolder>,
    activeTab: Int,
    totalUnreadSum: Int,
    folderUnreadSums: List<Int>,
    unreadCount: Int,
    onTabSelect: (Int) -> Unit,
    onFoldersSettings: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val unreadIdx = folders.size + 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // «Все» — FIX (P5.2): сумма непрочитанных сообщений (сбрасывается при чтении).
            FolderTabChip(
                text = "Все",
                selected = activeTab == 0,
                badge = totalUnreadSum.takeIf { it > 0 },
                onClick = { onTabSelect(0) },
            )
            // Папки — FIX (P5.2): сумма непрочитанных для чатов в этой папке.
            folders.forEachIndexed { index, folder ->
                val folderUnread = folderUnreadSums.getOrNull(index) ?: 0
                FolderTabChip(
                    text = folder.title,
                    selected = activeTab == index + 1,
                    badge = folderUnread.takeIf { it > 0 },
                    onClick = { onTabSelect(index + 1) },
                )
            }
            // «Непрочитанные» — сколько диалогов имеют непрочитанные.
            FolderTabChip(
                text = "Непрочитанные",
                selected = activeTab == unreadIdx,
                badge = unreadCount.takeIf { it > 0 },
                onClick = { onTabSelect(unreadIdx) },
            )
            // Gear → FoldersSettings
            IconButton(onClick = onFoldersSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Настройки папок",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * P3.3: Таб-чип для FolderTabRow. Стиль: pill с selected-состоянием.
 */
@Composable
private fun FolderTabChip(
    text: String,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Fix #282: текстовая метка типа вложения для preview последнего сообщения в
 * списке диалогов. Когда [Message.text] пуст, но есть attachments — показываем
 * label вместо «…», чтобы пользователь видел чем закончился диалог (фото,
 * голосовое, стикер и т.д.) не открывая чат. Стилистика как в нативном VK.
 */
private fun attachmentPreviewLabel(att: re.pinok.data.model.Attachment): String = when (att.type) {
    "photo" -> "Фотография"
    "video" -> "Видеозапись"
    "audio" -> "Аудиозапись"
    "audio_message" -> "Голосовое сообщение"
    "doc" -> "Документ"
    "sticker" -> "Стикер"
    "wall" -> "Запись на стене"
    "link" -> "Ссылка"
    "poll" -> "Опрос"
    "audio_playlist" -> "Плейлист"
    "gift" -> "Подарок"
    "market" -> "Товар"
    "story" -> "История"
    "call" -> "Звонок"
    else -> "Вложение"
}
