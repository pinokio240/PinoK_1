package re.pinok.ui.screens.im

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.Friend
import re.pinok.util.AppLog

/**
 * P3.1: ChatInfoScreen — отдельный экран информации о чате.
 *
 * Fix #267 (Plan §36.12):
 *  - P0-CHAT-1: VK-style "More" dropdown с ACL-gated пунктами (вместо flat Column).
 *  - P0-CHAT-4: AddMemberDialog — добавление участников через friends.get + messages.addChatUser.
 *  - P1-CHAT-4: 7 табов (Участники + Фото + Видео + Музыка + Сервисы + Файлы + Ссылки).
 *  - P2-CHAT-2: Role icons (owner=star gold, admin=shield blue) в members list.
 *
 * Секции:
 *  - Шапка: аватар + имя + статус (online/last-seen для DM, members count для group)
 *  - Действия: mute toggle (отдельная ячейка) + "More" IconButton → DropdownMenu (ACL-gated)
 *  - Табы (7): Участники / Фото / Видео / Музыка / Сервисы / Файлы / Ссылки
 *
 * Все данные грузятся через messages.getConversationsById + getConversationMembers +
 * getHistoryAttachments + getLastActivity. ACL — из chat.acl (chat_settings.acl).
 * Feature flags — из app.featureFlags (account.getTogglesExternal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    peerId: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isGroupChat = peerId >= 2_000_000_000L
    val localChatId = if (isGroupChat) peerId - 2_000_000_000L else 0L
    // 1-1 диалог: peerId в диапазоне пользователей (1 .. 1_999_999_999).
    // Используется для показа пункта "Заблокировать пользователя".
    val isUserDialog = peerId > 0 && peerId < 2_000_000_000L

    // Chat metadata (title/photo/acl) — грузится через getConversationsById.
    var chat by remember { mutableStateOf<Chat?>(null) }
    var loadingMeta by remember { mutableStateOf(true) }
    // DM last activity (online/last-seen).
    var lastActivity by remember { mutableStateOf<re.pinok.api.VKApiClient.LastActivity?>(null) }
    // Mute state (из pushSettings + optimistic toggle).
    var muted by remember { mutableStateOf(false) }
    // Members (group only).
    var members by remember { mutableStateOf<List<re.pinok.api.VKApiClient.ChatMember>>(emptyList()) }
    var loadingMembers by remember { mutableStateOf(false) }
    // Fix #267 P1-CHAT-4: расширение с 3 до 7 табов.
    // 0=Members (без media API), 1=photo, 2=video, 3=audio, 4=mini_app, 5=doc, 6=link.
    var selectedTab by remember { mutableIntStateOf(0) }
    var mediaItems by remember { mutableStateOf<List<re.pinok.api.VKApiClient.HistoryAttachment>>(emptyList()) }
    var loadingMedia by remember { mutableStateOf(false) }
    // Pending action confirmation (block/report/clear/leave).
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }

    // Fix #267 P0-CHAT-1: состояние показа выпадающего меню действий.
    var showActionsMenu by remember { mutableStateOf(false) }
    // Fix #267 P0-CHAT-2: диалоги переименования/изменения описания.
    var showRenameDialog by remember { mutableStateOf(false) }
    var showChangeDescriptionDialog by remember { mutableStateOf(false) }
    // Fix #267 P0-CHAT-4: диалог добавления участников.
    var showAddMemberDialog by remember { mutableStateOf(false) }
    // Fix #267 P1-CHAT-3: диалог передачи прав создателя.
    var showTransferOwnerDialog by remember { mutableStateOf(false) }

    // Feature-flag for mute toggle (reuse P3.2 flag — mute доступен и здесь).
    val muteEnabled by app.prefs.data
        .map { it.msgMute }
        .collectAsState(initial = true)

    // Fix #267 P1-CHAT-5: feature flags для ACL-gating пунктов меню.
    // Пустая map = флаги не загружены → UI показывает пункты по умолчанию (fallback).
    val featureFlags by app.featureFlags.collectAsState()

    // Загрузка метаданных + mute state + last activity (DM) + members (group).
    LaunchedEffect(peerId) {
        loadingMeta = true
        try {
            val chats = app.apiClient.messagesGetConversationsById(listOf(peerId))
            var c = chats.firstOrNull()
            // Fix #272: fallback через messages.getChat если getConversationsById
            // не вернул title или photo для group chat. Бывает у VK, особенно для
            // чатов без аватара или с истекшим URL photo. messages.getChat отдаёт
            // поля в корне ответа (без chat_settings обёртки) — надёжнее.
            if (isGroupChat && c != null) {
                val titleMissing = c.peer.title.isNullOrBlank() || c.peer.title == "Диалог"
                val photoMissing = c.peer.photo.isNullOrBlank()
                if (titleMissing || photoMissing) {
                    try {
                        val (fbTitle, fbPhoto, fbCount) = app.apiClient.messagesGetChat(peerId)
                            ?: Triple(null, null, null)
                        c = c.copy(
                            peer = c.peer.copy(
                                title = if (titleMissing) (fbTitle ?: c.peer.title) else c.peer.title,
                                photo = if (photoMissing) (fbPhoto ?: c.peer.photo) else c.peer.photo,
                            ),
                        )
                        if (fbCount != null && fbCount > 0) {
                            // Не перезатираем members.size (он точнее), но логируем.
                            AppLog.d("ChatInfoScreen", "messagesGetChat: title=${fbTitle?.take(20)} photo=${fbPhoto != null} count=$fbCount")
                        }
                    } catch (e: Exception) {
                        AppLog.w("ChatInfoScreen", "messagesGetChat fallback failed: ${e.message}")
                    }
                }
            }
            chat = c
            // Fix #122: используем единый isMuted() helper (учитывает no_sound,
            // disabled_forever, disabled_until).
            muted = c?.pushSettings?.isMuted() == true
            // DM: last activity.
            if (!isGroupChat && isUserDialog) {
                try {
                    lastActivity = app.apiClient.messagesGetLastActivity(peerId)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            AppLog.e("ChatInfoScreen", "load meta error", e)
        } finally {
            loadingMeta = false
        }
        // Group: members.
        if (isGroupChat) {
            loadingMembers = true
            try {
                var loaded = app.apiClient.messagesGetConversationMembers(peerId)
                // Fix #272: fallback резолв имён/аватарок для участников, у которых
                // first_name пустой. Это community-участники (memberId < 0) — VK НЕ
                // отдаёт для них name/photo в items[], только в response.groups[].
                // Парсер messagesGetConversationMembers уже пробует groups[], но
                // иногда VK вообще не возвращает groups[] в ответе → добиваем через
                // groups.getById (batch). Аналогично для пользователей без first_name.
                val needGroupResolve = loaded.filter { it.memberId < 0 && it.firstName.isBlank() }
                val needUserResolve = loaded.filter { it.memberId > 0 && it.firstName.isBlank() }
                if (needGroupResolve.isNotEmpty() || needUserResolve.isNotEmpty()) {
                    try {
                        if (needGroupResolve.isNotEmpty()) {
                            val gids = needGroupResolve.map { -it.memberId }.distinct()
                            val groupsMap = app.apiClient.groupsGetById(gids)
                                .associateBy { it.id }
                            loaded = loaded.map { m ->
                                if (m.memberId < 0 && m.firstName.isBlank()) {
                                    groupsMap[-m.memberId]?.let { g ->
                                        m.copy(
                                            firstName = g.name.ifBlank { "id${m.memberId}" },
                                            photo100 = g.photo100 ?: g.photo200 ?: m.photo100,
                                        )
                                    } ?: m
                                } else m
                            }
                        }
                        if (needUserResolve.isNotEmpty()) {
                            val uids = needUserResolve.map { it.userId }.distinct()
                            val usersMap = app.apiClient.usersGetByIds(uids)
                            loaded = loaded.map { m ->
                                if (m.memberId > 0 && m.firstName.isBlank()) {
                                    usersMap[m.userId]?.let { u ->
                                        m.copy(
                                            firstName = u.firstName.ifBlank { "" },
                                            lastName = u.lastName.ifBlank { "" },
                                            photo100 = u.photo100 ?: u.photo200 ?: m.photo100,
                                        )
                                    } ?: m
                                } else m
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.w("ChatInfoScreen", "members resolve fallback failed: ${e.message}")
                    }
                }
                members = loaded
            } catch (e: Exception) {
                AppLog.e("ChatInfoScreen", "load members error", e)
            } finally {
                loadingMembers = false
            }
        }
    }

    // Fix #267 P1-CHAT-4: загрузка shared media при смене таба (только для tabs 1..6).
    // Tab 0 (Members) — не делает API-запрос (members уже загружены в LaunchedEffect выше).
    LaunchedEffect(selectedTab) {
        val mediaType = when (selectedTab) {
            1 -> "photo"
            2 -> "video"
            3 -> "audio"
            4 -> "mini_app"
            5 -> "doc"
            6 -> "link"
            else -> null  // tab 0 (members) — нет запроса
        }
        if (mediaType == null) {
            mediaItems = emptyList()
            return@LaunchedEffect
        }
        loadingMedia = true
        try {
            mediaItems = app.apiClient.messagesGetHistoryAttachments(peerId, mediaType, count = 30)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #270: LeftCompositionCancellationException — это НОРМАЛЬНАЯ отмена
            // корутины при быстром переключении табов (пользователь свайпнул дальше,
            // пока шёл запрос). Не логируем как error — это не баг, а стандартное
            // поведение Compose LaunchedEffect. Раньше спамило E в лог (видео/аудио).
            // Важно: НЕ проглатываем CancellationException для structured concurrency —
            // пробрасываем, чтобы родительский scope знал об отмене.
            mediaItems = emptyList()
            throw e
        } catch (e: Exception) {
            AppLog.e("ChatInfoScreen", "load media ($mediaType) error", e)
            mediaItems = emptyList()
        } finally {
            loadingMedia = false
        }
    }

    val title = chat?.peer?.title ?: if (isGroupChat) "Групповой чат" else "Диалог"
    val photo = chat?.peer?.photo
    val acl = chat?.acl  // ChatAcl? — null для DM и каналов (ACL неприменим).

    // Fix #267 P0-CHAT-1: ACL-gating переменные для пунктов меню.
    // canChangeInfo → пункты "Изменить название/фото/описание".
    val canChangeInfo = acl?.canChangeInfo == true
    // canChangeOwner → двойной gating: ACL + feature flag vkm_convo_owner_right_transfer.
    val canChangeOwner = acl?.canChangeOwner == true
        && featureFlags["vkm_convo_owner_right_transfer"] == true
    // canDeleteChat → пункт "Покинуть беседу". Feature flag vkm_delete_chat, либо
    // fallback (featureFlags пуст = флаги ещё не загружены → показываем).
    val canDeleteChat = featureFlags.isEmpty() || featureFlags["vkm_delete_chat"] == true
    // canInvite → показ кнопки "Добавить участников" в tab Members.
    val canInvite = acl?.canInvite == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (loadingMeta && chat == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // ---- Шапка ----
                item(key = "header") {
                    ChatInfoHeader(
                        title = title,
                        photo = photo,
                        isGroupChat = isGroupChat,
                        membersCount = members.size,
                        lastActivity = lastActivity,
                        onUserClick = onUserClick,
                        peerId = peerId,
                    )
                }

                // ---- Действия: mute toggle (отдельная ячейка) + More button с DropdownMenu ----
                item(key = "actions_header") { SectionHeader("Действия") }
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Mute toggle — отдельная prominent ячейка (НЕ в дропдауне).
                        if (muteEnabled) {
                            ActionToggleRow(
                                title = if (muted) "Уведомления выключены" else "Уведомления включены",
                                icon = if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                                checked = muted,
                                onToggle = { newVal ->
                                    muted = newVal
                                    scope.launch {
                                        try {
                                            val result = app.apiClient.messagesSetConversationPushSettings(peerId, disabled = newVal)
                                            if (result != null) {
                                                // API успех — обновляем state из ответа сервера.
                                                muted = result.isMuted()
                                                AppLog.i("ChatInfoScreen", "mute toggled: server-confirmed muted=$muted")
                                            } else {
                                                muted = !newVal
                                                AppLog.w("ChatInfoScreen", "mute failed (api returned null)")
                                            }
                                        } catch (ce: kotlinx.coroutines.CancellationException) {
                                            throw ce
                                        } catch (e: Exception) {
                                            muted = !newVal
                                            AppLog.e("ChatInfoScreen", "mute error", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        // Fix #267 P0-CHAT-1: "More" IconButton → DropdownMenu с ACL-gated пунктами.
                        Box {
                            IconButton(onClick = { showActionsMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                            }
                            ChatActionsDropdown(
                                expanded = showActionsMenu,
                                onDismiss = { showActionsMenu = false },
                                isGroupChat = isGroupChat,
                                isUserDialog = isUserDialog,
                                canChangeInfo = canChangeInfo,
                                canChangeOwner = canChangeOwner,
                                canDeleteChat = canDeleteChat,
                                onRename = {
                                    showActionsMenu = false
                                    showRenameDialog = true
                                },
                                onChangePhoto = {
                                    showActionsMenu = false
                                    // TODO Fix #267 P0-CHAT-2: интеграция с photo picker
                                    // (требует ActivityResultLauncher). VKApiClient.messagesSetChatPhoto
                                    // уже реализован — нужен только UI выбора файла.
                                    AppLog.i("ChatInfoScreen", "TODO: change chat photo (picker not integrated)")
                                },
                                onChangeDescription = {
                                    showActionsMenu = false
                                    showChangeDescriptionDialog = true
                                },
                                onSearchInChat = {
                                    showActionsMenu = false
                                    // TODO Fix #267 P1-CHAT-2: переход в ChatDetailScreen с открытой
                                    // поиск-строкой. Пока no-op callback.
                                    AppLog.i("ChatInfoScreen", "TODO: search in chat (no-op callback)")
                                },
                                onClearHistory = {
                                    showActionsMenu = false
                                    pendingAction = PendingAction.CLEAR_HISTORY
                                },
                                onBlock = {
                                    showActionsMenu = false
                                    pendingAction = PendingAction.BLOCK
                                },
                                onReport = {
                                    showActionsMenu = false
                                    pendingAction = PendingAction.REPORT
                                },
                                onLeave = {
                                    showActionsMenu = false
                                    pendingAction = PendingAction.LEAVE
                                },
                                onTransferOwner = {
                                    showActionsMenu = false
                                    showTransferOwnerDialog = true
                                },
                            )
                        }
                    }
                }

                // ---- Общие медиа / Участники (7 табов) ----
                item(key = "media_header") { SectionHeader("Общие медиа") }
                item(key = "media_tabs") {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text(if (isGroupChat) "Участники (${members.size})" else "Участники") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("Фото") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                            text = { Text("Видео") })
                        Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                            text = { Text("Музыка") })
                        Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                            text = { Text("Сервисы") })
                        Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                            text = { Text("Файлы") })
                        Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 },
                            text = { Text("Ссылки") })
                    }
                }
                // Fix #272: рендер участников ВНЕ одного item — раньше 828 MemberRow
                // внутри одного LazyColumn item не помещались в экран (Compose не
                // ленит Column внутри item) → пользователь видел только 1 карточку
                // и большую пустоту снизу (хотя счётчик показывал «828 участников»).
                // Теперь таб 0 (Участники) рендерит каждый MemberRow как отдельный
                // item(key) главного LazyColumn — ленивый скролл работает корректно.
                if (selectedTab == 0) {
                    // Кнопка «Добавить участников» + placeholder'ы — отдельный item.
                    item(key = "members_header") {
                        MembersTabHeader(
                            isGroupChat = isGroupChat,
                            members = members,
                            loadingMembers = loadingMembers,
                            canInvite = canInvite,
                            onAddMember = { showAddMemberDialog = true },
                        )
                    }
                    // Сами участники — каждый как отдельный ленивый item.
                    items(members, key = { it.memberId }) { m ->
                        MemberRow(
                            member = m,
                            onKick = { memberId ->
                                scope.launch {
                                    try {
                                        app.apiClient.messagesRemoveChatUser(localChatId, memberId)
                                        members = members.filter { it.memberId != memberId }
                                        AppLog.i("ChatInfoScreen", "kicked member $memberId")
                                    } catch (e: Exception) {
                                        AppLog.e("ChatInfoScreen", "kick error", e)
                                    }
                                }
                            },
                            onUserClick = onUserClick,
                        )
                    }
                } else {
                    // Tabs 1..6 — медиа (оставлены в одном item, т.к. count ≤ 30).
                    item(key = "media_content") {
                        when (selectedTab) {
                            // Tab 1 (Фото) — grid 3-in-row.
                            1 -> SharedMediaGrid(items = mediaItems, loading = loadingMedia)
                            // Tabs 2..6 — список с иконкой + title (video/audio/mini_app/doc/link).
                            else -> SharedMediaList(
                                items = mediaItems,
                                loading = loadingMedia,
                                mediaType = when (selectedTab) {
                                    2 -> "video"
                                    3 -> "audio"
                                    4 -> "mini_app"
                                    5 -> "doc"
                                    6 -> "link"
                                    else -> "doc"
                                },
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // ---- Confirmation dialogs (существующие actions) ----
    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { if (!actionInProgress) pendingAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    enabled = !actionInProgress,
                    onClick = {
                        actionInProgress = true
                        scope.launch {
                            try {
                                when (action) {
                                    PendingAction.CLEAR_HISTORY -> {
                                        app.apiClient.messagesDeleteConversation(peerId)
                                        AppLog.i("ChatInfoScreen", "history cleared")
                                    }
                                    PendingAction.BLOCK -> {
                                        if (peerId > 0) app.apiClient.accountBan(peerId)
                                        AppLog.i("ChatInfoScreen", "user blocked")
                                    }
                                    PendingAction.REPORT -> {
                                        // Пожаловаться на последнее сообщение (если есть).
                                        val lastMsgId = chat?.lastMessage?.id ?: 0L
                                        if (lastMsgId > 0) {
                                            app.apiClient.messagesMarkAsSpam(listOf(lastMsgId), peerId)
                                        }
                                        AppLog.i("ChatInfoScreen", "reported spam")
                                    }
                                    PendingAction.LEAVE -> {
                                        if (localChatId > 0) app.apiClient.messagesRemoveChatUser(localChatId)
                                        AppLog.i("ChatInfoScreen", "left chat")
                                    }
                                }
                                pendingAction = null
                                if (action == PendingAction.LEAVE || action == PendingAction.BLOCK) {
                                    onBack()
                                }
                            } catch (e: Exception) {
                                AppLog.e("ChatInfoScreen", "action error: $action", e)
                            } finally {
                                actionInProgress = false
                            }
                        }
                    },
                ) {
                    Text(action.confirmText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }, enabled = !actionInProgress) {
                    Text("Отмена")
                }
            },
        )
    }

    // Fix #267 P0-CHAT-2: диалог переименования чата (ACL: can_change_info).
    if (showRenameDialog) {
        RenameChatDialog(
            currentTitle = title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                showRenameDialog = false
                scope.launch {
                    try {
                        val ok = app.apiClient.messagesEditChat(localChatId, title = newTitle)
                        if (ok) {
                            // Optimistic update: обновляем локальный title из ответа.
                            chat = chat?.let { it.copy(peer = it.peer.copy(title = newTitle)) }
                            AppLog.i("ChatInfoScreen", "chat renamed to '$newTitle'")
                        } else {
                            AppLog.w("ChatInfoScreen", "rename failed (api returned false)")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatInfoScreen", "rename error", e)
                    }
                }
            },
        )
    }

    // Fix #267 P0-CHAT-2: диалог изменения описания чата (ACL: can_change_info).
    // Fix #269: pre-fill из chat.description (раньше хардкод "" → пользователь
    // не видел текущее описание). Также показываем Toast при ошибке/успехе,
    // вместо тихого логирования — пользователь жаловался «приложение падает в ошибку»
    // (на самом деле VK API error 7/15 не показывался, теперь видно в Toast).
    if (showChangeDescriptionDialog) {
        ChangeDescriptionDialog(
            currentDescription = chat?.description ?: "",
            onDismiss = { showChangeDescriptionDialog = false },
            onConfirm = { newDescription ->
                showChangeDescriptionDialog = false
                scope.launch {
                    try {
                        val ok = app.apiClient.messagesEditChat(localChatId, description = newDescription)
                        if (ok) {
                            AppLog.i("ChatInfoScreen", "chat description changed: ok=$ok")
                            // Оптимистично обновляем локальный state.
                            chat = chat?.copy(description = newDescription)
                            Toast.makeText(context, "Описание обновлено", Toast.LENGTH_SHORT).show()
                        } else {
                            // messagesEditChat вернул false — VK API error (нет прав,
                            // слишком длинное описание, и т.д.). lastApiError содержит код.
                            val err = app.apiClient.lastApiError ?: "Не удалось изменить описание"
                            AppLog.w("ChatInfoScreen", "chat description change failed: $err")
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatInfoScreen", "change description error", e)
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    // Fix #267 P0-CHAT-4: диалог добавления участника (ACL: can_invite).
    if (showAddMemberDialog) {
        AddMemberDialog(
            chatId = localChatId,
            existingMemberIds = members.map { it.userId }.toSet(),
            onDismiss = { showAddMemberDialog = false },
            onAdded = {
                // После успешного добавления — перезагружаем members.
                scope.launch {
                    try {
                        members = app.apiClient.messagesGetConversationMembers(peerId)
                    } catch (e: Exception) {
                        AppLog.e("ChatInfoScreen", "reload members after add error", e)
                    }
                }
            },
        )
    }

    // Fix #267 P1-CHAT-3: диалог передачи прав создателя чата
    // (ACL: can_change_owner + feature flag vkm_convo_owner_right_transfer).
    if (showTransferOwnerDialog) {
        TransferOwnerDialog(
            members = members,
            onDismiss = { showTransferOwnerDialog = false },
            onTransfer = { newOwnerId ->
                showTransferOwnerDialog = false
                scope.launch {
                    try {
                        val ok = app.apiClient.messagesTransferChatOwnership(localChatId, newOwnerId)
                        AppLog.i("ChatInfoScreen", "transfer ownership to $newOwnerId: ok=$ok")
                        if (ok) {
                            // Перезагружаем members — роли могли измениться (owner перешёл).
                            members = app.apiClient.messagesGetConversationMembers(peerId)
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatInfoScreen", "transfer ownership error", e)
                    }
                }
            },
        )
    }
}

/** Pending action descriptor for confirmation dialog. */
private enum class PendingAction(val title: String, val message: String, val confirmText: String) {
    CLEAR_HISTORY("Очистить историю?", "Вся история сообщений будет удалена без возможности восстановления.", "Очистить"),
    BLOCK("Заблокировать пользователя?", "Пользователь будет добавлен в чёрный список.", "Заблокировать"),
    REPORT("Пожаловаться на спам?", "Последнее сообщение будет помечено как спам.", "Пожаловаться"),
    LEAVE("Выйти из чата?", "Вы покинете этот групповой чат.", "Выйти"),
}

/**
 * Fix #267 P0-CHAT-1: VK-style "More" dropdown с ACL-gated пунктами.
 *
 * Пункты меню (в порядке):
 *  1. Изменить название       — group + ACL.canChangeInfo
 *  2. Изменить фото           — group + ACL.canChangeInfo (TODO: photo picker)
 *  3. Изменить описание       — group + ACL.canChangeInfo
 *  4. Поиск по чату           — всегда
 *  --- divider ---
 *  5. Очистить историю        — всегда
 *  6. Заблокировать           — только 1-1 диалог (isUserDialog)
 *  --- divider ---
 *  7. Пожаловаться            — всегда
 *  8. Покинуть беседу         — group + canDeleteChat (feature flag vkm_delete_chat или fallback)
 *  9. Передать права создателя — group + canChangeOwner (ACL + flag vkm_convo_owner_right_transfer)
 *
 * Mute toggle не входит в дропдаун — он показан отдельной prominent ячейкой слева.
 */
@Composable
private fun ChatActionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isGroupChat: Boolean,
    isUserDialog: Boolean,
    canChangeInfo: Boolean,
    canChangeOwner: Boolean,
    canDeleteChat: Boolean,
    onRename: () -> Unit,
    onChangePhoto: () -> Unit,
    onChangeDescription: () -> Unit,
    onSearchInChat: () -> Unit,
    onClearHistory: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onLeave: () -> Unit,
    onTransferOwner: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Пункты "Изменить название/фото/описание" — только для group chats,
        // т.к. ACL.canChangeInfo = true только в group chats (для DM acl = null).
        if (isGroupChat) {
            DropdownMenuItem(
                text = { Text("Изменить название") },
                enabled = canChangeInfo,
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onRename,
            )
            DropdownMenuItem(
                text = { Text("Изменить фото") },
                enabled = canChangeInfo,
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onChangePhoto,
            )
            DropdownMenuItem(
                text = { Text("Изменить описание") },
                enabled = canChangeInfo,
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onChangeDescription,
            )
        }
        // Поиск по чату — доступен всегда (для DM и group).
        DropdownMenuItem(
            text = { Text("Поиск по чату") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = onSearchInChat,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Очистить историю") },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = onClearHistory,
        )
        // Заблокировать — только для 1-1 диалогов (peerId в диапазоне пользователей).
        if (isUserDialog) {
            DropdownMenuItem(
                text = { Text("Заблокировать пользователя") },
                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onBlock,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Пожаловаться") },
            leadingIcon = { Icon(Icons.Outlined.Report, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = onReport,
        )
        // Покинуть беседу — только для group chats. Feature flag vkm_delete_chat, либо
        // fallback (featureFlags ещё не загружены → показываем как enabled).
        if (isGroupChat) {
            DropdownMenuItem(
                text = { Text("Покинуть беседу") },
                enabled = canDeleteChat,
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onLeave,
            )
        }
        // Передать права создателя — двойной gating:
        //  1) ACL.can_change_owner (есть права на передачу в этом чате)
        //  2) feature flag vkm_convo_owner_right_transfer (функция включена глобально)
        if (isGroupChat) {
            DropdownMenuItem(
                text = { Text("Передать права создателя") },
                enabled = canChangeOwner,
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = onTransferOwner,
            )
        }
    }
}

/** Шапка: аватар + имя + статус. */
@Composable
private fun ChatInfoHeader(
    title: String,
    photo: String?,
    isGroupChat: Boolean,
    membersCount: Int,
    lastActivity: re.pinok.api.VKApiClient.LastActivity?,
    onUserClick: (Long) -> Unit,
    peerId: Long,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .then(
                    if (peerId in 1..1_999_999_999L) Modifier.clickable { onUserClick(peerId) }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = title,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (isGroupChat) Icons.Outlined.Group else Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Статус.
        val statusText = when {
            isGroupChat -> "$membersCount участников"
            lastActivity?.online == 1 -> "онлайн"
            lastActivity != null && lastActivity.lastSeen > 0 -> {
                val diff = (System.currentTimeMillis() / 1000 - lastActivity.lastSeen)
                when {
                    diff < 60 -> "был(а) только что"
                    diff < 3600 -> "был(а) ${diff / 60} мин назад"
                    diff < 86400 -> "был(а) ${diff / 3600} ч назад"
                    else -> "был(а) ${diff / 86400} д назад"
                }
            }
            else -> ""
        }
        if (statusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (lastActivity?.online == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Fix #272: шапка таба 0 "Участники" — кнопка "Добавить" + placeholder'ы
 * (загрузка / пусто / не групповой). Сам список участников рендерится
 * отдельными items() в главном LazyColumn (см. ChatInfoScreen).
 *
 * Раньше здесь был MembersTabContent с Column { forEach { MemberRow } } внутри
 * одного LazyColumn item — для 828 участников это не помещалось в экран и
 * Compose не ленил такие блоки → пользователь видел 1 карточку и пустоту.
 */
@Composable
private fun MembersTabHeader(
    isGroupChat: Boolean,
    members: List<re.pinok.api.VKApiClient.ChatMember>,
    loadingMembers: Boolean,
    canInvite: Boolean,
    onAddMember: () -> Unit,
) {
    if (!isGroupChat) {
        Text(
            text = "Участники доступны только в групповых чатах",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Fix #267 P0-CHAT-4: кнопка добавления участника (ACL-gated: can_invite).
        // Показывается только если у текущего пользователя есть право приглашать.
        if (canInvite) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(onClick = onAddMember),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Добавить участников",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (loadingMembers && members.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
            return
        }
        if (members.isEmpty()) {
            Text(
                text = "Нет участников",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            return
        }
        // Сами участники рендерятся снаружи (items() в LazyColumn) — здесь ничего не кладём.
    }
}

/**
 * Строка участника (group chat): аватар + имя + role icon + role text + kick.
 *
 * Fix #267 P2-CHAT-2: role icons:
 *  - Owner (isOwner) → заполненная звезда (Icons.Filled.Star), amber/gold цвет.
 *  - Admin (isAdmin) → щит (Icons.Outlined.Shield), primary (blue) цвет.
 *  - Regular — без иконки.
 * Иконка ставится справа от имени (внутри Row). Role text ниже оставлен для контекста.
 */
@Composable
private fun MemberRow(
    member: re.pinok.api.VKApiClient.ChatMember,
    onKick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { if (member.userId > 0) onUserClick(member.userId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (member.photo100 != null) {
                    AsyncImage(
                        model = member.photo100,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = member.firstName.take(1).ifBlank { "?" }.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${member.firstName} ${member.lastName}".trim().ifBlank { "id${member.userId}" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Fix #267 P2-CHAT-2: role icon справа от имени.
                    if (member.isOwner) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Владелец",
                            tint = Color(0xFFFFC107),  // amber/gold (Material Amber 500)
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (member.isAdmin) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = "Администратор",
                            tint = MaterialTheme.colorScheme.primary,  // blue (theme primary)
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val role = when {
                    member.isOwner -> "Владелец"
                    member.isAdmin -> "Администратор"
                    else -> "Участник"
                }
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = { onKick(member.memberId) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Исключить",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Fix #267 P1-CHAT-4: список медиа для tabs 2..6 (video/audio/mini_app/doc/link).
 *
 * Каждый элемент — Row с type-specific иконкой + title. Audio показывает
 * "artist — title" (уже отдаётся парсером HistoryAttachment). Mini app и link
 * могут возвращать пустой title — fallback на mediaType.
 */
@Composable
private fun SharedMediaList(
    items: List<re.pinok.api.VKApiClient.HistoryAttachment>,
    loading: Boolean,
    mediaType: String,
) {
    if (loading && items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
        return
    }
    if (items.isEmpty()) {
        Text(
            text = "Нет вложений",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (mediaType) {
                        "video" -> Icons.Outlined.PlayCircle
                        "audio" -> Icons.Outlined.MusicNote
                        "mini_app" -> Icons.Outlined.Apps
                        "doc" -> Icons.Outlined.Description
                        "link" -> Icons.Outlined.Link
                        else -> Icons.Outlined.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.title.ifBlank { mediaType },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Сетка общих медиа (для фото — grid 3-in-row). Используется только для tab 1 (Фото).
 * Для других tabs — см. [SharedMediaList].
 */
@Composable
private fun SharedMediaGrid(
    items: List<re.pinok.api.VKApiClient.HistoryAttachment>,
    loading: Boolean,
) {
    if (loading && items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
        return
    }
    if (items.isEmpty()) {
        Text(
            text = "Нет медиа",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        return
    }
    // Grid 3-in-row (chunked — безопасно внутри LazyColumn item).
    val rows = items.chunked(3)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (item.url != null) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                // Заполняем пустые ячейки последнего ряда.
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/**
 * Fix #267 P0-CHAT-4: диалог добавления участника в чат.
 *
 * Загружает friends.get (count=100), показывает LazyColumn с поиском и кнопкой
 * добавления для каждого друга. На клик "Добавить" → messages.addChatUser.
 * ACL-gating: кнопка "Добавить участников" в ChatInfoScreen показывается только
 * если acl.canInvite == true (см. [MembersTabContent]).
 *
 * @param chatId локальный ID чата (без 2_000_000_000)
 * @param existingMemberIds ID пользователей, уже состоящих в чате (для "Уже в чате")
 * @param onAdded вызывается после успешного добавления (перезагрузка members)
 */
@Composable
private fun AddMemberDialog(
    chatId: Long,
    existingMemberIds: Set<Long>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var addingUserId by remember { mutableStateOf<Long?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Загрузка списка друзей при первом показе диалога.
    LaunchedEffect(Unit) {
        loading = true
        try {
            friends = app.apiClient.friendsGet(count = 100)
        } catch (e: Exception) {
            AppLog.e("AddMemberDialog", "friendsGet error", e)
        } finally {
            loading = false
        }
    }

    val filtered = remember(friends, query) {
        if (query.isBlank()) friends
        else friends.filter { it.fullName.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = { if (addingUserId == null) onDismiss() },
        title = { Text("Добавить участников") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск друзей") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    when {
                        loading && friends.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }

                        filtered.isEmpty() -> Text(
                            text = "Ничего не найдено",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )

                        else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filtered, key = { it.id }) { f ->
                                val already = f.id in existingMemberIds
                                AddMemberRow(
                                    friend = f,
                                    alreadyAdded = already,
                                    isAdding = addingUserId == f.id,
                                    onAdd = {
                                        // Защита от двойного клика: если уже добавляем кого-то — игнор.
                                        if (addingUserId == null) {
                                            addingUserId = f.id
                                            scope.launch {
                                                try {
                                                    val ok = app.apiClient.messagesAddChatUser(chatId, f.id)
                                                    if (ok) {
                                                        AppLog.i("AddMemberDialog", "added user ${f.id} to chat $chatId")
                                                        onAdded()
                                                        onDismiss()
                                                    } else {
                                                        errorMsg = "Не удалось добавить участника"
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("AddMemberDialog", "addChatUser error", e)
                                                    errorMsg = e.message ?: "Ошибка"
                                                } finally {
                                                    addingUserId = null
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                errorMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = addingUserId == null) { Text("Закрыть") }
        },
    )
}

/** Строка друга в [AddMemberDialog]: аватар + имя + add/progress. */
@Composable
private fun AddMemberRow(
    friend: Friend,
    alreadyAdded: Boolean,
    isAdding: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (friend.photo100 != null) {
                AsyncImage(
                    model = friend.photo100,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = friend.firstName.take(1).ifBlank { "?" }.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.fullName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alreadyAdded) {
                Text(
                    text = "Уже в чате",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (isAdding) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            IconButton(onClick = onAdd, enabled = !alreadyAdded) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Добавить",
                    tint = if (alreadyAdded) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Fix #267 P1-CHAT-3: диалог передачи прав создателя чата.
 *
 * Показывает список участников (без текущего owner) с RadioButton для выбора.
 * На "Передать" → messages.editChat({chat_id, owner_id: newOwnerId}).
 * ACL-gating: показывается только если canChangeOwner (acl.canChangeOwner + flag).
 *
 * @param members текущий список участников (для выбора нового owner)
 * @param onTransfer принимает userId нового владельца
 */
@Composable
private fun TransferOwnerDialog(
    members: List<re.pinok.api.VKApiClient.ChatMember>,
    onDismiss: () -> Unit,
    onTransfer: (Long) -> Unit,
) {
    // Исключаем текущего owner (нельзя передать себе).
    val candidates = remember(members) { members.filter { !it.isOwner } }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Передать права создателя") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Выберите нового создателя. Вы потеряете все права администратора.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (candidates.isEmpty()) {
                    Text(
                        text = "Нет доступных участников",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(candidates, key = { it.memberId }) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedId = m.userId }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedId == m.userId,
                                        onClick = { selectedId = m.userId },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${m.firstName} ${m.lastName}".trim().ifBlank { "id${m.userId}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedId != null,
                onClick = { selectedId?.let(onTransfer) },
            ) { Text("Передать", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Fix #267 P0-CHAT-2: диалог переименования чата (ACL: can_change_info).
 * OutlinedTextField с предзаполненным текущим title. Кнопка "Сохранить"
 * disabled пока title пуст или не отличается от текущего.
 */
@Composable
private fun RenameChatDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newTitle by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить название") },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = newTitle.isNotBlank() && newTitle != currentTitle,
                onClick = { onConfirm(newTitle.trim()) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Fix #267 P0-CHAT-2: диалог изменения описания чата (ACL: can_change_info).
 * Multiline OutlinedTextField (3-5 строк). Сохранение через messages.editChat(description).
 */
@Composable
private fun ChangeDescriptionDialog(
    currentDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newDesc by remember { mutableStateOf(currentDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить описание") },
        text = {
            OutlinedTextField(
                value = newDesc,
                onValueChange = { newDesc = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newDesc.trim()) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Заголовок секции. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Строка действия с Switch (toggle) — для mute.
 * Fix #267 P0-CHAT-1: добавлен [modifier] параметр — caller может передать
 * `Modifier.weight(1f)` чтобы кнопка "More" стояла справа в той же Row.
 */
@Composable
private fun ActionToggleRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
