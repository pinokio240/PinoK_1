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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
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
import androidx.compose.material3.FilterChip
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
import kotlinx.coroutines.delay
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Chat
import re.pinok.data.model.ChatFolder
import re.pinok.realtime.LongPollEvent
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog
import re.pinok.util.toChatDate
import re.pinok.util.toMsgTime
import re.pinok.ui.components.SkeletonChatList
import re.pinok.ui.components.ErrorView

// #TYPING-FIX: таймаут-гашение индикатора «печатает…» в списке диалогов.
// VK web паттерн: VK шлёт typing-событие каждые ~4с пока пользователь печатает;
// если новое событие не пришло за ~5с — индикатор гаснет. Тот же интервал,
// что и в ChatDetailScreen (TYPING_TIMEOUT_MS = 5_000L).
private const val TYPING_TIMEOUT_MS = 5_000L

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

    // #TYPING-FIX: приёмный контур typing в списке диалогов.
    // Раньше Typing-события потреблял только ChatDetailScreen (шапка чата),
    // а список диалогов их не показывал вообще (rg Typing = 0 вхождений).
    val typingEnabled by app.prefs.data
        .map { it.msgTypingIndicator }
        .collectAsState(initial = true)
    // peerId → (userId печатающего, timestamp последнего события, мс).
    var typingPeers by remember { mutableStateOf<Map<Long, Pair<Long, Long>>>(emptyMap()) }
    // Кеш имён для typing в беседах (код 62): userId → «Имя Фамилия».
    // ЛС (код 61) — имя не нужно (там «печатает…» без имени, peer и есть юзер).
    var typingNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LaunchedEffect(typingEnabled) {
        if (!typingEnabled) {
            typingPeers = emptyMap()
            return@LaunchedEffect
        }
        app.longPollClient.events.collect { ev ->
            if (ev !is LongPollEvent.Typing) return@collect
            // Не показываем свой typing (события о себе не приходят, но guard как в чате).
            if (ev.userId == myUserId) return@collect
            typingPeers = typingPeers + (ev.peerId to Pair(ev.userId, System.currentTimeMillis()))
            AppLog.d("MessagesScreen",
                "#TYPING-FIX: dialog-list typing peer=${ev.peerId} user=${ev.userId} isChat=${ev.isChat}")
            // В беседах (код 62) нужно имя печатающего: резолвим один раз на userId
            // (точечный users.get — только при новом typing в беседе, не по таймеру).
            if (ev.isChat && !typingNames.containsKey(ev.userId)) {
                scope.launch {
                    try {
                        val profiles = app.apiClient.usersGetByIds(listOf(ev.userId))
                        val profile = profiles[ev.userId]
                        if (profile != null) {
                            val name = "${profile.firstName} ${profile.lastName}".trim()
                            if (name.isNotBlank()) {
                                typingNames = typingNames + (ev.userId to name)
                            }
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLog.w("MessagesScreen", "#TYPING-FIX: name resolve failed: ${e.message}")
                    }
                }
            }
        }
    }

    // #TYPING-FIX: таймаут-гашение. Эффект keyed на typingPeers: любое обновление
    // карты (новое событие — то же или от другого пира) перезапускает таймер,
    // через TYPING_TIMEOUT_MS без продления запись удаляется (см. разбор бага
    // залипания в ChatDetailScreen — здесь сразу правильная схема).
    LaunchedEffect(typingPeers) {
        val current = typingPeers
        if (current.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(TYPING_TIMEOUT_MS)
        val now = System.currentTimeMillis()
        val fresh = current.filterValues { entry -> now - entry.second < TYPING_TIMEOUT_MS }
        if (fresh.size != current.size) {
            typingPeers = fresh
            AppLog.d("MessagesScreen", "#TYPING-FIX: dialog-list typing expired, remaining=${fresh.size}")
        }
    }

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

    // ══ Fix #355 #MSG-SEARCH-SERVER: серверный поиск «по чатам и сообщениям» ══
    // При q ≥ 2 символов — серверный режим (m.vk.ru ищет так же): секция «Чаты»
    // (messages.searchConversations — НОВЫЙ VKA-метод) + секция «Сообщения»
    // (messages.search с peerId=null — глобальный поиск). Debounce 400мс:
    // LaunchedEffect(searchQuery) отменяется при каждом изменении запроса,
    // API-вызов стартует только после паузы в наборе.
    // Client-фильтр (ниже в filteredChats) остаётся для q == 1 (одна буква —
    // серверный поиск не имеет смысла) и как фолбэк при пустом серверном ответе.
    var searchChats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var searchMessages by remember { mutableStateOf<List<VKApiClient.MessageSearchResult>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    // Ошибка серверного поиска (err=15/8 у web-токена возможны) — показываем
    // честный текст в пустом стейте; client-совпадения при этом всё равно видны.
    var searchErrorText by remember { mutableStateOf<String?>(null) }
    val serverSearchActive = searchQuery.trim().length >= 2
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchChats = emptyList()
            searchMessages = emptyList()
            searchErrorText = null
            searchLoading = false
            return@LaunchedEffect
        }
        searchLoading = true
        delay(400) // debounce: отменится при продолжении набора (LaunchedEffect key)
        try {
            searchChats = app.apiClient.messagesSearchConversations(q, count = 20)
            searchMessages = app.apiClient.messagesSearch(q, peerId = null, count = 20)
            if (searchChats.isEmpty() && searchMessages.isEmpty()) {
                val err = app.apiClient.lastApiError
                if (err != null && err.isNotBlank() && app.apiClient.lastApiErrorCode != 0) {
                    searchErrorText = "Ошибка поиска: $err — показаны только локальные совпадения"
                } else {
                    searchErrorText = null
                }
            } else {
                searchErrorText = null
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            searchErrorText = "Ошибка поиска: ${e.message}"
            AppLog.w("MessagesScreen", "#MSG-SEARCH-SERVER: search failed: ${e.message}")
        }
        searchLoading = false
    }

    // ══ Fix #356 #MSG-ARCHIVE: раздел «Архив» («Убрать чат из списка») ══
    // localArchivedIds — source of truth UI (ArchivedConversationsRepository,
    // SovaPrefs.archivedConvsData; паттерн pin Fix #274/#276). API
    // messages.archiveConversation/unarchiveConversation — best-effort.
    var localArchivedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showArchived by remember { mutableStateOf(false) }
    // Серверный архив (messages.getConversations filter=archived) — если токену
    // доступен (например диалог заархивирован с другого клиента) — мерджим с
    // локальным списком. Пустой/ошибочный ответ не фатален (локальный список
    // самодостаточен).
    var archivedServerChats by remember { mutableStateOf<List<Chat>>(emptyList()) }

    // Fix #392 #IM-LOCAL-PIN: локальный закреп диалога. Source of truth —
    // SovaPrefs-ключ im_pinned_dialogs (JSON array of peer_id В ПОРЯДКЕ
    // ЗАКРЕПЛЕНИЯ), читается РЕАКТИВНО через снапшот (s.imPinnedDialogs):
    // закреп/откреп/drag пишут setImPinnedDialogs() → DataStore emit →
    // recomposition — порядок обновляется мгновенно и без локального
    // override-стейта. (Прежний механизм Fix #274/#276 — remember-список +
    // PinnedConversationsRepository — заменён: один источник истины, порядок
    // переживает перезапуск процесса и синхронен между всеми подписчиками.)
    // VK API messages.markAsImportantConversation по-прежнему требует
    // special-scope token (web-token → err=8), поэтому серверный sync —
    // best-effort и source of truth не является. Одноразовая миграция legacy
    // pinned_convs_data — migrateLegacyPinnedDialogsIfNeeded() в LaunchedEffect
    // первичной загрузки ниже. Закреп — фича вкладки «Диалоги»: каналы
    // (isChannel) не закрепляются (пункт меню скрыт, pinned-сортировка их
    // игнорирует).
    val localPinnedOrder: List<Long> = s.imPinnedDialogs

    // #CHANNEL-NET: каналы/«Запросы», дозагруженные ПОСЛЕ основного getConversations
    // (legacy getConversations отдаёт не все каналы — #MODERN-SYNC-CURSOR; «Запросы»
    // идут отдельным filter=message_request). Сохраняются между LP re-fetch, чтобы
    // дозагруженные записи не исчезали из списка при каждом входящем сообщении.
    // Обновляются ТОЛЬКО полными загрузками (первичная / pull-to-refresh) —
    // в LP re-fetch entries живут в своей последней версии до следующей полной.
    var mergedExtras by remember { mutableStateOf<List<Chat>>(emptyList()) }

    // #CHANNEL-NET: полный контур загрузки списка диалогов — getConversations +
    // merge «Запросы» (filter=message_request, §44) + merge каналов
    // (messagesGetAllChannels, #MODERN-SYNC-CURSOR). ЕДИНАЯ точка для первичной
    // загрузки и pull-to-refresh: раньше refreshChats() перезаписывал `chats`
    // голым ответом getConversations — дозагруженные каналы и запросы исчезали
    // при каждом обновлении (жалоба: «сброс списка при обновлении», каналы
    // пропадали из вкладки «Каналы»).
    suspend fun fetchConversationsMerged(): List<Chat> {
        var list = app.apiClient.messagesGetConversations(count = pageSize)
            .distinctBy { it.peer.id }
        val extras = ArrayList<Chat>()
        // §44 #MSG-REQUESTS: merge запросов от не-друзей (non-fatal — пустые
        // запросы или ошибка фильтра не ломают основной список).
        if (list.isNotEmpty()) {
            try {
                val requests = app.apiClient.messagesGetConversationRequests(count = 50)
                if (requests.isNotEmpty()) {
                    val existingIds = list.map { it.peer.id }.toHashSet()
                    val newRequests = requests.filter { it.peer.id !in existingIds }
                    if (newRequests.isNotEmpty()) {
                        list = (list + newRequests)
                            // NULL-ЯВНО: сортировочный ключ, null-ветка тривиальна
                            // (нет даты последнего сообщения = 0, дефолт для UI).
                            .sortedByDescending { it.lastMessage?.date ?: 0L }
                        extras.addAll(newRequests)
                        AppLog.i("MessagesScreen",
                            "#CHANNEL-NET: merged ${newRequests.size} message_request(s) into conversation list")
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w("MessagesScreen",
                    "#CHANNEL-NET: message_request merge failed (non-fatal): ${e.message}")
            }
        }
        // #MODERN-SYNC-CURSOR: merge каналов, которых нет в legacy getConversations
        // (non-fatal). ВАЖНО: после основного списка, не внутри retry-цикла —
        // иначе следующий while-цикл перезапишет список и каналы пропадут.
        try {
            val allChannels = app.apiClient.messagesGetAllChannels()
            if (allChannels.isNotEmpty()) {
                val existingIds = list.map { it.peer.id }.toHashSet()
                val missing = allChannels.filter { it.peer.id !in existingIds }
                if (missing.isNotEmpty()) {
                    list = (list + missing).distinctBy { it.peer.id }
                    extras.addAll(missing)
                    AppLog.i("MessagesScreen",
                        "#CHANNEL-NET: merged ${missing.size} missing channels via getItems")
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppLog.w("MessagesScreen", "#CHANNEL-NET: getAllChannels failed (non-fatal): ${e.message}")
        }
        mergedExtras = extras
        return list
    }

    // Первичная загрузка диалогов.
    LaunchedEffect(Unit) {
        // FIX: используем корутину LaunchedEffect напрямую вместо scope.launch,
        // чтобы избежать ForgottenCoroutineScopeException при пересоздании Activity.
        // Fix #392 #IM-LOCAL-PIN: одноразовая миграция legacy pinned_convs_data
        // (Fix #274/#276) → im_pinned_dialogs. Явная загрузка больше не нужна:
        // снапшот (s.imPinnedDialogs) доставляет список реактивно — сортировка
        // сразу учитывает закреплённые (и любые последующие изменения порядка).
        try {
            app.prefs.migrateLegacyPinnedDialogsIfNeeded()
        } catch (e: Exception) {
            AppLog.w("MessagesScreen", "#IM-LOCAL-PIN: legacy pinned migration failed: ${e.message}")
        }
        // Fix #356 #MSG-ARCHIVE: грузим локальные архивные peer_id (source of
        // truth) — до отображения чатов, чтобы основной список сразу исключил их.
        try {
            localArchivedIds = app.archivedConvsRepository.load()
            if (localArchivedIds.isNotEmpty()) {
                AppLog.d("MessagesScreen", "#MSG-ARCHIVE: loaded ${localArchivedIds.size} archived: $localArchivedIds")
            }
        } catch (e: Exception) {
            AppLog.w("MessagesScreen", "#MSG-ARCHIVE: failed to load local archived: ${e.message}")
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
                // #CHANNEL-NET: полный контур (getConversations + «Запросы» + каналы)
                // с защитной дедупликацией по peer.id (Fix #53 — внутри).
                list = fetchConversationsMerged()
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
            // #CHANNEL-NET: merge «Запросов» и каналов теперь внутри
            // fetchConversationsMerged() (см. выше) — тот же порядок и non-fatal
            // семантика, что и раньше, но контур переиспользуется pull-to-refresh'ом.
            chats = list
            // Fix #127: если VK вернул меньше запрошенного — больше страниц нет.
            if (list.size < pageSize) hasMore = false
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
                // #CHANNEL-NET: полный контур (getConversations + «Запросы» + каналы).
                // Раньше здесь был голый getConversations — при каждом обновлении
                // списка дозагруженные каналы и «Запросы» исчезали (сброс списка).
                val list = fetchConversationsMerged()
                chats = list
                if (list.size < pageSize) hasMore = false
                errorText = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("MessagesScreen", "refreshChats failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Real-time обновление списка диалогов через LongPoll.
    // #CHANNEL-NET: single-flight — один активный re-fetch; новое событие ОТМЕНЯЕТ
    // предыдущий запрос (раньше каждый event запускал СВОЙ getConversations: во
    // время активности параллельные запросы шли друг за другом, старший ответ мог
    // перезаписать свежий — гонка + повторные запросы + риск rate-limit).
    var lpRefetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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
            // NULL-EXPLICIT: захват var-делегата в val — явная проверка вместо ?.
            val refetchJob = lpRefetchJob
            if (refetchJob != null) refetchJob.cancel()
            lpRefetchJob = scope.launch {
                try {
                    val targetCount = maxOf(chats.size, pageSize)
                    val fresh = app.apiClient.messagesGetConversations(count = targetCount)
                        .distinctBy { it.peer.id }
                    if (fresh.isNotEmpty()) {
                        // #CHANNEL-NET: сохраняем дозагруженные каналы/«Запросы»
                        // (mergedExtras), которых нет в свежем ответе — иначе каналы
                        // (те, что legacy getConversations не отдаёт) и запросы
                        // исчезали из списка при КАЖДОМ входящем сообщении.
                        val freshIds = fresh.map { it.peer.id }.toHashSet()
                        val preserved = mergedExtras.filter { it.peer.id !in freshIds }
                        chats = if (preserved.isEmpty()) fresh else (fresh + preserved)
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
    val filteredChats by remember(chats, searchQuery, activeTab, folders, foldersEnabled, localPinnedOrder, localArchivedIds) {
        derivedStateOf {
            var result = chats
            // Fix #356 #MSG-ARCHIVE: архивные диалоги исключены из основного
            // списка (смотрятся в разделе «Архив» — showArchived ниже).
            result = result.filterNot { it.peer.id in localArchivedIds }
            if (foldersEnabled) {
                // P3.3: динамические табы — 0=Все, 1..N=папки, N+1=Непрочитанные.
                // #COUNTER-CHANNELS: «Непрочитанные» — только диалоги, без каналов
                // (каналы читаются в своей вкладке/списке, их непрочитанное не
                // входит в «непрочитанные» по требованию пользователя).
                val unreadIdx = folders.size + 1
                when {
                    activeTab == 0 -> {} // Все
                    activeTab == unreadIdx -> result = result.filter { it.unreadCount > 0 && !it.isChannel }
                    activeTab in 1..folders.size -> {
                        val folder = folders[activeTab - 1]
                        result = result.filter { it.peer.id in folder.peerIds }
                    }
                }
            } else {
                // Legacy 3-tab mode: 0=Диалоги, 1=Каналы, 2=Непрочитанные.
                // #DIALOGS-TAB: «Диалоги» — всё КРОМЕ каналов (broadcast-сообщества);
                // «Каналы» — только каналы (isChannel = group && can_write.allowed=false).
                // #COUNTER-CHANNELS: «Непрочитанные» — только диалоги, без каналов
                // (бейдж и содержимое консистентны: оба без каналов).
                when (activeTab) {
                    0 -> result = result.filter { !it.isChannel }
                    1 -> result = result.filter { it.isChannel }
                    2 -> result = result.filter { it.unreadCount > 0 && !it.isChannel }
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
            // Fix #392 #IM-LOCAL-PIN: закреплённые наверху — ТОЛЬКО на вкладке 0
            // («Диалоги» в legacy-режиме / «Все» в режиме папок). На вкладках
            // «Каналы»/«Непрочитанные» и в папках порядок прежний (дата последнего
            // сообщения DESC, стабильная сортировка): закреплённые там не всплывают.
            //  - Pinned = peer.id в localPinnedOrder (локальный закреп, source of
            //    truth — s.imPinnedDialogs) ИЛИ серверное закрепление
            //    (sortId.majorId > 0 / important), НО только для не-каналов
            //    (закреп — фича вкладки «Диалоги», каналы не закрепляются).
            //  - Внутри pinned: В ПОРЯДКЕ ЗАКРЕПЛЕНИЯ (localPinnedOrder) — позиция
            //    фиксирована порядком в prefs, новые сообщения её НЕ двигают;
            //    остальные серверно-pinned (не в localPinnedOrder) — по majorId DESC.
            //  - Дальше — все остальные в прежнем порядке (дата DESC).
            val pinnedFirst = activeTab == 0
            val isServerPinned: (Chat) -> Boolean = { c ->
                val sid = c.sortId
                (sid != null && sid.isPinned()) || c.important == true
            }
            if (pinnedFirst) {
                val pinned = result.filter {
                    !it.isChannel && (it.peer.id in localPinnedOrder || isServerPinned(it))
                }
                val unpinned = result.filterNot {
                    !it.isChannel && (it.peer.id in localPinnedOrder || isServerPinned(it))
                }
                val pinnedSorted = buildList {
                    // Закреплённые — В ПОРЯДКЕ ЗАКРЕПЛЕНИЯ (порядок из prefs).
                    localPinnedOrder.forEach { peerId ->
                        val match = pinned.firstOrNull { candidate -> candidate.peer.id == peerId }
                        if (match != null) add(match)
                    }
                    // Серверно-pinned вне локального списка (если появятся) — majorId DESC.
                    val remaining = pinned.filterNot { it.peer.id in localPinnedOrder }
                    remaining.sortedByDescending { c ->
                        val sid = c.sortId
                        if (sid != null) sid.majorId else 0L
                    }.forEach { add(it) }
                }
                val unpinnedSorted = unpinned.sortedByDescending { c ->
                    val lm = c.lastMessage
                    if (lm != null) lm.date else 0L
                }
                pinnedSorted + unpinnedSorted
            } else {
                // Fix #392 #IM-LOCAL-PIN: вне вкладки «Диалоги» — прежний порядок
                // (дата DESC, stable), без pinned-пересортировки.
                result.sortedByDescending { c ->
                    val lm = c.lastMessage
                    if (lm != null) lm.date else 0L
                }
            }
        }
    }

    // Fix #356 #MSG-ARCHIVE: объединённый список раздела «Архив».
    // Источники: (1) chats, локально заархивированные (peer.id in localArchivedIds —
    // сервер наш web-token archiveConversation скорее всего отклонил, чат живёт
    // в обычном ответе getConversations); (2) archivedServerChats — серверный
    // filter=archived (если диалог заархивирован с другого клиента/сессии).
    // Dedup по peer.id, сортировка по дате последнего сообщения DESC.
    val archivedChats by remember(chats, localArchivedIds, archivedServerChats) {
        derivedStateOf {
            val seen = mutableSetOf<Long>()
            val merged = buildList {
                chats.filter { it.peer.id in localArchivedIds }.forEach { c ->
                    if (seen.add(c.peer.id)) add(c)
                }
                archivedServerChats.forEach { c ->
                    if (seen.add(c.peer.id)) add(c)
                }
            }
            merged.sortedByDescending { it.lastMessage?.date ?: 0L }  // NULL-ЯВНО
        }
    }

    // Fix #356 #MSG-ARCHIVE: обработчики архивации. Паттерн pin (Fix #274/#276):
    // оптимистичный локальный список (source of truth, персистится в
    // ArchivedConversationsRepository → SovaPrefs.archivedConvsData) + best-effort
    // API-вызов в фоне (для web-токена вероятен err=8/15 — НЕ откатываем,
    // честный AppLog; прецедент — onTogglePin ниже).
    // Из `chats` архивный чат НЕ удаляется — он скрывается фильтром filteredChats
    // (peer.id in localArchivedIds) и мгновенно возвращается при разархивации
    // без перезагрузки списка.
    val screenContext = LocalContext.current
    fun onArchiveConversationLocal(peerId: Long) {
        scope.launch {
            try {
                localArchivedIds = app.archivedConvsRepository.archive(peerId)
                AppLog.i("MessagesScreen",
                    "#MSG-ARCHIVE: archived locally: peer=$peerId (${localArchivedIds.size} total)")
            } catch (e: Exception) {
                AppLog.w("MessagesScreen",
                    "#MSG-ARCHIVE: failed to persist archive($peerId): ${e.message}")
            }
        }
        scope.launch {
            try {
                val ok = app.apiClient.messagesArchiveConversation(peerId)
                if (ok) {
                    AppLog.i("MessagesScreen", "#MSG-ARCHIVE: archive API sync ok: peer=$peerId")
                } else {
                    AppLog.d("MessagesScreen",
                        "#MSG-ARCHIVE: archive API sync false (expected for web-token, local state preserved): peer=$peerId")
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w("MessagesScreen",
                    "#MSG-ARCHIVE: archive API sync error (local state preserved): ${e.message}")
            }
        }
        Toast.makeText(screenContext, "Убрано в архив", Toast.LENGTH_SHORT).show()
    }

    fun onUnarchiveConversationLocal(peerId: Long) {
        scope.launch {
            try {
                localArchivedIds = app.archivedConvsRepository.unarchive(peerId)
                AppLog.i("MessagesScreen",
                    "#MSG-ARCHIVE: unarchived locally: peer=$peerId (${localArchivedIds.size} left)")
            } catch (e: Exception) {
                AppLog.w("MessagesScreen",
                    "#MSG-ARCHIVE: failed to persist unarchive($peerId): ${e.message}")
            }
        }
        scope.launch {
            try {
                val ok = app.apiClient.messagesUnarchiveConversation(peerId)
                if (ok) {
                    AppLog.i("MessagesScreen", "#MSG-ARCHIVE: unarchive API sync ok: peer=$peerId")
                } else {
                    AppLog.d("MessagesScreen",
                        "#MSG-ARCHIVE: unarchive API sync false (expected for web-token, local state preserved): peer=$peerId")
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w("MessagesScreen",
                    "#MSG-ARCHIVE: unarchive API sync error (local state preserved): ${e.message}")
            }
        }
        Toast.makeText(screenContext, "Возвращено из архива", Toast.LENGTH_SHORT).show()
    }

    // P1.4: badge counts for tabs.
    // FIX (P5.2): бейджи на вкладках показывают НЕПРОЧИТАННОЕ, а не total count чатов.
    // Раньше «Все» = chats.size (40) и «Каналы» = total channels (2) — эти числа
    // никогда не уменьшались при чтении (чаты никуда не исчезают), из-за чего
    // пользователь видел «залипшие» счётчики. Теперь все три бейджа отражают
    // непрочитанное и сбрасываются при просмотре:
    //  - dialogsUnreadSum  = сумма unreadCount по ДИАЛОГАМ (не каналам) — бейдж «Диалоги»
    //  - totalUnreadSum    = сумма unreadCount по ДИАЛОГАМ — бейдж «Все» в папках-режиме
    //  - channelUnreadSum  = сумма unreadCount только по каналам — бейдж «Каналы»
    //  - unreadCount       = сколько ДИАЛОГОВ имеют непрочитанные (для вкладки «Непрочитанные»)
    // #COUNTER-CHANNELS (требование пользователя: «счетчик количество сообщений
    // каналов не должны входить в счетчик сообщений диалогов или непрочитанные»):
    // totalUnreadSum и unreadCount ИСКЛЮЧАЮТ каналы (isChannel = group &&
    // can_write.allowed=false, #DIALOGS-TAB). Бейджи самих каналов в списке
    // (chat.unreadCount в ChatCard) и бейдж вкладки «Каналы» НЕ тронуты.
    val dialogsUnreadSum by remember(chats) {
        derivedStateOf { chats.filter { !it.isChannel }.sumOf { it.unreadCount } }
    }
    val totalUnreadSum by remember(chats) {
        derivedStateOf { chats.filter { !it.isChannel }.sumOf { it.unreadCount } }
    }
    val channelUnreadSum by remember(chats) {
        derivedStateOf {
            chats.filter { it.isChannel }
                .sumOf { it.unreadCount }
        }
    }
    val unreadCount by remember(chats) {
        derivedStateOf { chats.count { it.unreadCount > 0 && !it.isChannel } }
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

            // Fix #356 #MSG-ARCHIVE: чип «Архив» — вход в раздел архивных диалогов
            // (аналог кнопки «Архив» в шапке m.vk.ru). Скрывается в режиме
            // серверного поиска (q ≥ 2 — список занят результатами поиска).
            // Открытие раздела догружает серверный архив (filter=archived) —
            // для web-токена может быть пуст/ошибка; локальный список самодостаточен.
            if (!serverSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = showArchived,
                        onClick = {
                            showArchived = !showArchived
                            if (showArchived && archivedServerChats.isEmpty()) {
                                scope.launch {
                                    try {
                                        archivedServerChats = app.apiClient.messagesGetConversations(
                                            count = 50,
                                            filter = "archived",
                                        )
                                        AppLog.d("MessagesScreen",
                                            "#MSG-ARCHIVE: server archived loaded: ${archivedServerChats.size}")
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen",
                                            "#MSG-ARCHIVE: server archived load failed (non-fatal): ${e.message}")
                                    }
                                }
                            }
                        },
                        label = {
                            Text(
                                if (showArchived) "Архив (${archivedChats.size}) — открыть список диалогов"
                                else "Архив (${archivedChats.size})",
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                if (serverSearchActive) {
                    // ══ Fix #355 #MSG-SEARCH-SERVER: серверный поиск ══
                    // Секция «Чаты»: локальные совпадения (client-фильтр) + серверные
                    // (messages.searchConversations) без дублей по peer.id (ключи
                    // items() должны быть уникальны).
                    val q = searchQuery.trim()
                    val clientMatches = chats.filter { it.peer.title?.contains(q, ignoreCase = true) == true }  // NULL-ЯВНО
                    val clientIds = clientMatches.map { it.peer.id }.toSet()
                    val serverNew = searchChats.filterNot { it.peer.id in clientIds }
                    val searchTotal = clientMatches.size + serverNew.size + searchMessages.size
                    if (searchTotal == 0 && !searchLoading) {
                        item(key = "search_empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Ничего не найдено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (searchErrorText != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = searchErrorText ?: "",  // NULL-ЯВНО
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    if (clientMatches.isNotEmpty() || serverNew.isNotEmpty()) {
                        item(key = "search_hdr_chats") {
                            Text(
                                text = "Чаты",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(clientMatches, key = { "c_${it.peer.id}" }) { chat ->
                            ChatCard(
                                chat = chat,
                                // Fix #355: из результатов поиска архив/меню-действия
                                // недоступны (результат — ссылка на диалог).
                                showArchiveAction = false,
                                showListActions = false,
                                onClick = { onChatClick(chat) },
                            )
                        }
                        items(serverNew, key = { "c_${it.peer.id}" }) { chat ->
                            ChatCard(
                                chat = chat,
                                showArchiveAction = false,
                                showListActions = false,
                                onClick = { onChatClick(chat) },
                            )
                        }
                    }
                    if (searchMessages.isNotEmpty()) {
                        item(key = "search_hdr_msgs") {
                            Text(
                                text = "Сообщения",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(searchMessages, key = { "m_${it.messageId}_${it.peerId}_${it.date}" }) { result ->
                            MessageSearchResultRow(
                                result = result,
                                onClick = {
                                    // Тап — переход в диалог. ChatDetail сам резолвит
                                    // титул/аватар по peerId (messagesGetConversationsById,
                                    // прецедент Fix #348).
                                    onChatClick(
                                        Chat(
                                            peer = Chat.Peer(
                                                id = result.peerId,
                                                type = if (result.peerId > 0) "user" else "group",
                                                localId = if (result.peerId > 0) result.peerId else -result.peerId,
                                                title = result.peerTitle ?: "Диалог",  // NULL-ЯВНО
                                                photo = null,
                                            ),
                                        )
                                    )
                                },
                            )
                        }
                    }
                    if (searchLoading) {
                        item(key = "search_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                } else if (showArchived) {
                    // ══ Fix #356 #MSG-ARCHIVE: раздел «Архив» ══
                    if (archivedChats.isEmpty()) {
                        item(key = "arch_empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Архив пуст",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(archivedChats, key = { it.peer.id }) { chat ->
                            ChatCard(
                                chat = chat,
                                isArchived = true,
                                // Fix #356: вне основного списка — только «Вернуть из архива».
                                showListActions = false,
                                onArchiveConversation = { peerId -> onUnarchiveConversationLocal(peerId) },
                                // В архиве drag&drop/pin-логика не имеет смысла.
                                onDragSwap = { _, _ -> },
                                pinnedPeerIdsInOrder = emptyList(),
                                pinnedIndex = -1,
                                onClick = { onChatClick(chat) },
                            )
                        }
                    }
                } else if (filteredChats.isEmpty() && searchQuery.isNotBlank()) {
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

                    // Fix #392 #IM-LOCAL-PIN: pinned-статус карточки — локальный закреп
                    // (s.imPinnedDialogs) или серверное закрепление, НО только для
                    // не-каналов (закреп — фича вкладки «Диалоги»: в «Каналах» нет
                    // пункта меню и не рисуется pinned-визуал).
                    val isPinnedChat: (Chat) -> Boolean = { c ->
                        if (c.isChannel) {
                            false
                        } else {
                            val sid = c.sortId
                            c.peer.id in localPinnedOrder ||
                                (sid != null && sid.isPinned()) ||
                                c.important == true
                        }
                    }
                    // Fix #274 + Fix #392: порядок закреплённых для drag&drop — В ПОРЯДКЕ
                    // ЗАКРЕПЛЕНИЯ (localPinnedOrder), ограниченный чатами, реально
                    // присутствующими в текущем списке. (Прежний вариант брал порядок
                    // из filteredChats — на вкладках без pinned-сортировки индексы
                    // drag&drop не совпадали с порядком закрепления.)
                    val visibleDialogIds = filteredChats
                        .filter { candidate -> !candidate.isChannel }
                        .map { it.peer.id }
                        .toHashSet()
                    val pinnedPeerIdsInOrder: List<Long> = localPinnedOrder.filter { it in visibleDialogIds }
                    // #FAVE-SELF-CHAT: pinned строка «Избранное» (не из filteredChats).
                    if (favoritesChat != null) {
                        item(key = "favorites_$myUserId") {
                            val ctx = LocalContext.current
                            ChatCard(
                                chat = favoritesChat,
                                isPinned = false,
                                // Fix #356: «Избранное» — виртуальный self-chat,
                                // архивировать его нельзя → пункт меню скрыт.
                                showArchiveAction = false,
                                // Fix #392 #IM-LOCAL-PIN: виртуальный self-chat не участвует
                                // в закрепе/заглушении/непрочитанном/удалении (обработчики
                                // ниже — no-op) → долгий тап не открывает меню мёртвых
                                // пунктов (no-stub UI); закрепить «Избранное» невозможно.
                                showListActions = false,
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
                        // Fix #392 #IM-LOCAL-PIN: drag-handle только на вкладке 0 — там,
                        // где действует pinned-сортировка; на прочих вкладках список
                        // показан в порядке даты, и перестановка закрепа была бы невидима.
                        val pinnedIndex = if (isPinned && activeTab == 0) {
                            pinnedPeerIdsInOrder.indexOf(chat.peer.id)
                        } else {
                            -1
                        }
                        // #TYPING-FIX: «печатает…» вместо сниппета при живом typing пира.
                        // ЛС (код 61) — «печатает…»; беседа (код 62, peer >= 2e9) —
                        // «Имя печатает…» (имя из typingNames, резолвится через users.get).
                        val typingEntry = typingPeers[chat.peer.id]
                        val typingText: String? = if (typingEntry == null) {
                            null
                        } else {
                            val typingUserId = typingEntry.first
                            if (chat.peer.id >= 2_000_000_000L) {
                                val name = typingNames[typingUserId]
                                if (name != null) "$name печатает…" else "печатает…"
                            } else {
                                "печатает…"
                            }
                        }
                        ChatCard(
                            chat = chat,
                            isPinned = isPinned,
                            typingText = typingText,
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
                                    try {
                                        val ok = app.apiClient.messagesMarkAsRead(peerId, lastMsgId)
                                        AppLog.d("MessagesScreen",
                                            "messagesMarkAsRead(peer=$peerId, upTo=$lastMsgId): $ok")
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        // #IM-CHANNEL-OPEN: callInternal пробрасывает сетевые
                                        // IOException (VKApiClient: NETWORK_FAIL → throw) —
                                        // раньше launch был без catch, непойманное исключение
                                        // в rememberCoroutineScope крэшило приложение РОВНО
                                        // в момент тапа по чату с непрочитанным (в т.ч. по
                                        // каналу) — субъективно «диалог не открывается».
                                        AppLog.w("MessagesScreen",
                                            "messagesMarkAsRead(peer=$peerId) network error: ${e.message}")
                                    }
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
                                // Fix #392 #IM-LOCAL-PIN: персистим НОВЫЙ порядок закрепления
                                // в SovaPrefs (source of truth — im_pinned_dialogs). UI
                                // обновится реактивно через снапшот (DataStore emit →
                                // s.imPinnedDialogs → recomposition): закреплённый диалог
                                // встаёт на место мгновенно и дальше НЕ двигается при новых
                                // сообщениях — позицию фиксирует порядок в prefs.
                                val newOrder: List<Long> = if (pin) {
                                    (listOf(peerId) + localPinnedOrder.filter { it != peerId }).distinct()
                                } else {
                                    localPinnedOrder.filterNot { it == peerId }
                                }
                                scope.launch {
                                    try {
                                        app.prefs.setImPinnedDialogs(newOrder)
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen", "#IM-LOCAL-PIN: failed to persist pin($peerId, $pin): ${e.message}")
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
                                // Fix #392 #IM-LOCAL-PIN: pinned-порядок живёт в prefs и не
                                // меняется оптимистично — из im_pinned_dialogs запись убирается
                                // только при УСПЕШНОМ удалении (ветка ok ниже), при откате
                                // персистить нечего.
                                scope.launch {
                                    try {
                                        val ok = app.apiClient.messagesDeleteConversation(peerId)
                                        if (ok) {
                                            // Fix #392 #IM-LOCAL-PIN: убираем из im_pinned_dialogs,
                                            // если диалог был закреплён (source of truth — prefs).
                                            if (wasPinned) {
                                                try {
                                                    app.prefs.setImPinnedDialogs(
                                                        pinnedPeerIdsInOrder.filterNot { it == peerId },
                                                    )
                                                } catch (e: Exception) {
                                                    AppLog.w("MessagesScreen", "#IM-LOCAL-PIN: unpin after delete: ${e.message}")
                                                }
                                            }
                                            Toast.makeText(ctx, "Диалог удалён", Toast.LENGTH_SHORT).show()
                                            AppLog.i("MessagesScreen", "conversation deleted: peer=$peerId")
                                        } else {
                                            // Откат: возвращаем чат в список.
                                            chats = chats.toMutableList().apply { add(removedChat) }
                                            Toast.makeText(ctx, "Не удалось удалить диалог", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (ce: kotlinx.coroutines.CancellationException) {
                                        throw ce
                                    } catch (e: Exception) {
                                        AppLog.e("MessagesScreen", "delete conversation error", e)
                                        chats = chats.toMutableList().apply { add(removedChat) }
                                        Toast.makeText(ctx, "Не удалось удалить диалог", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            // Fix #356 #MSG-ARCHIVE: «Убрать чат из списка» — локальный
                            // архив (source of truth) + best-effort API. Паттерн pin.
                            onArchiveConversation = { peerId ->
                                onArchiveConversationLocal(peerId)
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
                                // Fix #392 #IM-LOCAL-PIN: новый порядок закрепления пишется
                                // в SovaPrefs (source of truth) — снапшот реактивно
                                // переставит карточки, порядок переживает перезапуск.
                                scope.launch {
                                    try {
                                        app.prefs.setImPinnedDialogs(newOrder)
                                    } catch (e: Exception) {
                                        AppLog.w("MessagesScreen", "#IM-LOCAL-PIN: failed to persist drag reorder: ${e.message}")
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
    // #TYPING-FIX: живой typing для этого пира — заменяет сниппет последнего
    // сообщения («печатает…» / «Имя печатает…»); null = typing нет. Гаснет
    // по таймауту на стороне списка (см. LaunchedEffect(typingPeers)).
    typingText: String? = null,
    onClick: () -> Unit = {},
    onMarkAsRead: (peerId: Long, lastMessageId: Long) -> Unit = { _, _ -> },
    onToggleMute: (peerId: Long, mute: Boolean) -> Unit = { _, _ -> },
    // Fix #274: новые callback-и для закрепления, отметки непрочитанным, drag&drop.
    onTogglePin: (peerId: Long, pin: Boolean) -> Unit = { _, _ -> },
    onToggleUnread: (peerId: Long, unread: Boolean) -> Unit = { _, _ -> },
    // Fix #281: удалить диалог (messages.deleteConversation) — с confirm-диалогом.
    onDeleteConversation: (peerId: Long) -> Unit = { _ -> },
    // Fix #356 #MSG-ARCHIVE: «Убрать чат из списка»/«Вернуть из архива».
    // isArchived — карточка рендерится в разделе «Архив» (меняет пункт меню);
    // showArchiveAction=false скрывает пункт (виртуальный self-chat «Избранное»,
    // результаты серверного поиска — Fix #355).
    // showListActions=false — карточка вне основного списка (архив/поиск):
    // пункты закрепить/заглушить/непрочитанным/удалить СКРЫТЫ (их обработчики
    // не подключены — иначе были бы мёртвые кнопки, no-stub/honest UI).
    //   архив: showListActions=false + isArchived=true → меню только «Вернуть из архива»;
    //   поиск: showListActions=false + isArchived=false → контекст-меню отключено целиком.
    isArchived: Boolean = false,
    showArchiveAction: Boolean = true,
    showListActions: Boolean = true,
    onArchiveConversation: (peerId: Long) -> Unit = { _ -> },
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
        // #ARCH-CONTAINERS 3.7-1: attachments в :core:data — захват ДО проверки.
        val lastAttachments = lastMessage.attachments
        val body = when {
            lastMessage.text.isNotBlank() -> lastMessage.text
            !lastAttachments.isNullOrEmpty() ->
                lastAttachments.firstOrNull()?.let { attachmentPreviewLabel(it) } ?: "…"
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
            onLongClick = {
                // Fix #355/356: в результатах поиска меню нет (действия не
                // подключены) — long-press не открывает пустой DropdownMenu.
                if (showListActions || isArchived) showContextMenu = true
            },
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
                        // #TYPING-FIX: при живом typing пира показываем «печатает…»
                        // (в беседах — имя печатающего) ВМЕСТО сниппета последнего
                        // сообщения — как в VK web. Гашение по таймауту — в списке.
                        // Не ломает подстановку превью: typingText == null → прежний
                        // preview (текст/метка вложения/action-текст/«Вы: » префикс).
                        Text(
                            text = if (typingText != null) typingText else preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (typingText != null)
                                MaterialTheme.colorScheme.primary
                            else if (hasUnread)
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
                // Fix #274 + Fix #392 #IM-LOCAL-PIN: Закрепить/Открепить диалог.
                // Скрывается вне основного списка (архив/поиск — showListActions=false,
                // см. KDoc параметра) и для каналов (закреп — фича вкладки «Диалоги»:
                // isChannel → пункта нет, долгий тап не закрепляет).
                if (showListActions && !chat.isChannel) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Открепить диалог" else "Закрепить диалог") },
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
                }
                // Fix #122: Заглушить/Включить уведомления (см. showListActions).
                if (showListActions) {
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
                }
                // Fix #274: Отметить непрочитанным / прочитанным (см. showListActions).
                if (showListActions) {
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
                }
                // Fix #356 #MSG-ARCHIVE: «Убрать чат из списка» (в VK web — архив)
                // / «Вернуть из архива» для карточек раздела «Архив». Скрывается
                // для виртуального self-chat и результатов поиска (showArchiveAction).
                if (showArchiveAction) {
                    DropdownMenuItem(
                        text = { Text(if (isArchived) "Вернуть из архива" else "Убрать чат из списка") },
                        leadingIcon = {
                            Icon(
                                if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showContextMenu = false
                            onArchiveConversation(chat.peer.id)
                        },
                    )
                }
                // Fix #281: Удалить диалог (messages.deleteConversation).
                // Деструктивное действие — после tap открывается confirm-диалог,
                // API вызывается только после подтверждения пользователя.
                // Скрывается вне основного списка (showListActions=false).
                if (showListActions) {
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

/**
 * Fix #355 #MSG-SEARCH-SERVER: строка результата поиска ПО СООБЩЕНИЯМ
 * (секция «Сообщения» серверного поиска в списке диалогов — m.vk.ru ищет
 * «по чатам и сообщениям»). Титул — из extended-ответа messages.search
 * ([VKApiClient.MessageSearchResult.peerTitle]); дата — человекочитаемая
 * (toChatDate: «Сегодня»/«Вчера»/«12 июля»). Тап — переход в диалог
 * (ChatDetail сам резолвит титул/аватар по peerId, прецедент Fix #348).
 */
@Composable
private fun MessageSearchResultRow(
    result: VKApiClient.MessageSearchResult,
    onClick: () -> Unit,
) {
    val title = result.peerTitle ?: "Диалог ${if (result.peerId < 0) -result.peerId else result.peerId}"  // NULL-ЯВНО
    val dateText = if (result.date > 0L) result.date.toChatDate() else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (dateText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = result.text.ifBlank { "Вложение" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
