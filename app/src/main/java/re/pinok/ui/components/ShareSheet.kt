package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.Group
import re.pinok.data.model.Post
import re.pinok.util.AppLog

/**
 * ShareSheet — расширенный диалог «Поделиться» (по аналогии с m.vk.com).
 *
 * Три вкладки:
 *   - «Диалоги» — отправить пост в чат как wall-attachment
 *   - «Сообщества» — репост на стену группы
 *
 * Плюс быстрые действия над полем ввода:
 *   - «На своей стене» — wall.repost (существующий метод)
 *   - «В избранное» — fave.add
 *
 * На основе анализа лента.7z: ShareModal имеет SharePanel с вкладками
 * ShareHeaderConversationsPanel / ShareHeaderGroupsPanel, кнопку
 * SharePanel-headerRight__externalShare, опции «Избранное», «На своей стене».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    post: Post,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {},
) {
    val app = SovaApp.get()
    val snap by app.prefs.data.collectAsState(initial = null)
    val s = snap ?: return
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    // Списки
    var conversations by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loadingList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Выбранный элемент
    var selectedPeerId by remember { mutableStateOf<Long?>(null) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }

    val loadConversations: () -> Unit = {
        scope.launch {
            loadingList = true
            try {
                val result = app.apiClient.messagesGetConversations(count = 50)
                conversations = result.filter { it.peer.id != 0L }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "loadConversations error", e)
            }
            loadingList = false
        }
    }

    val loadGroups: () -> Unit = {
        scope.launch {
            loadingList = true
            try {
                val result = app.apiClient.groupsGet(userId = null, count = 50)
                groups = result
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "loadGroups error", e)
            }
            loadingList = false
        }
    }

    // Загрузка при переключении вкладки
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        selectedPeerId = null
        selectedGroupId = null
        if (selectedTab == 0 && conversations.isEmpty()) loadConversations()
        if (selectedTab == 1 && groups.isEmpty()) loadGroups()
    }

    // Действие отправки
    val doShare: () -> Unit = {
        val targetPeerId = selectedPeerId
        val targetGroupId = selectedGroupId

        when {
            targetPeerId != null -> {
                scope.launch {
                    sending = true
                    statusMsg = "Отправка в диалог…"
                    try {
                        val msgId = app.apiClient.sendPostToChat(
                            peerId = targetPeerId,
                            ownerId = post.ownerId,
                            postId = post.id,
                            message = comment.trim(),
                        )
                        if (msgId > 0) {
                            AppLog.i("ShareSheet", "Sent post to chat $targetPeerId, msgId=$msgId")
                            onSuccess()
                            onDismiss()
                        } else {
                            statusMsg = "Не удалось отправить"
                        }
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "sendPostToChat exception", e)
                        statusMsg = "Ошибка: ${e.message}"
                    } finally {
                        sending = false
                    }
                }
            }
            targetGroupId != null -> {
                scope.launch {
                    sending = true
                    statusMsg = "Публикация в сообществе…"
                    try {
                        val newPostId = app.apiClient.repostToGroup(
                            groupId = targetGroupId,
                            sourceOwnerId = post.ownerId,
                            sourcePostId = post.id,
                            message = comment.trim(),
                        )
                        if (newPostId > 0) {
                            AppLog.i("ShareSheet", "Reposted to group $targetGroupId, postId=$newPostId")
                            onSuccess()
                            onDismiss()
                        } else {
                            statusMsg = "Не удалось опубликовать"
                        }
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "repostToGroup exception", e)
                        statusMsg = "Ошибка: ${e.message}"
                    } finally {
                        sending = false
                    }
                }
            }
        }
    }

    // Быстрые действия
    val doRepostToWall: () -> Unit = {
        scope.launch {
            sending = true
            statusMsg = "Публикация на стене…"
            try {
                val obj = "wall${post.ownerId}_${post.id}"
                val (newPostId, _) = app.apiClient.wallRepost(obj, comment.trim())
                if (newPostId > 0) {
                    AppLog.i("ShareSheet", "Reposted to wall: $newPostId")
                    onSuccess()
                    onDismiss()
                } else {
                    statusMsg = "Не удалось сделать репост"
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "wallRepost exception", e)
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    val doBookmark: () -> Unit = {
        // #FAVE-SELF-CHAT: «В избранное» = пересылка поста в self-chat
        // (peer_id = myUserId) как wall-вложение. Раньше был fave.add("post")
        // который у web-токена даёт error 3 (Unknown method passed).
        scope.launch {
            sending = true
            statusMsg = "Отправка в избранное…"
            try {
                val target = app.exchangeAuthRepository.userId()
                if (target <= 0L) {
                    statusMsg = "Не удалось определить аккаунт"
                } else {
                    val msgId = app.apiClient.sendPostToChat(
                        peerId = target,
                        ownerId = post.ownerId,
                        postId = post.id,
                        message = comment.trim(),
                    )
                    if (msgId > 0) {
                        AppLog.i("ShareSheet", "Sent post to favorites (self-chat) msgId=$msgId")
                        onSuccess()
                        onDismiss()
                    } else {
                        statusMsg = "Не удалось отправить в избранное"
                    }
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "favorites send exception", e)
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // #SHARE-IME-FIX: ModalBottomSheet НЕ применяет imePadding к контенту
        // автоматически. Без этого мягкая клавиатура перекрывает поле
        // «Комментарий…» и кнопку «Отправить» (поле ввода в самом низу шторки,
        // под 300dp-списком получателей). Паттерн как в ChatDetailScreen.kt
        // (windowInsetsPadding(navigationBars).imePadding()), плюс
        // verticalScroll — чтобы при фокусе на поле Compose сам прокрутил
        // контент (bringIntoView) и поле оказалось видно над клавиатурой.
        // navigationBarsPadding — отступ под gesture-nav bar когда клавиатура
        // скрыта. imePadding — сдвигает контент вверх при открытой клавиатуре
        // (без двойного учёта: nav bar уже consumed, ime добивает сверху).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Поделиться",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (selectedPeerId != null || selectedGroupId != null) {
                    Text(
                        text = if (selectedPeerId != null) "1 получатель"
                               else "1 сообщество",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(onClick = { if (!sending) onDismiss() }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }

            // ── Quick actions row ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAction(
                    icon = Icons.Outlined.PostAdd,
                    label = "На своей\nстене",
                    onClick = doRepostToWall,
                    enabled = !sending,
                )
                QuickAction(
                    icon = Icons.Outlined.BookmarkAdd,
                    label = "В\nизбранное",
                    onClick = doBookmark,
                    enabled = !sending,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Tabs ─────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Диалоги") },
                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Сообщества") },
                    icon = { Icon(Icons.Outlined.Group, contentDescription = null) },
                )
            }

            // ── Search ───────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Поиск…") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            // ── List ─────────────────────────────────────────────
            when (selectedTab) {
                0 -> {
                    if (loadingList) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        val filtered = if (searchQuery.isBlank()) conversations
                            else conversations.filter {
                                it.peer.title?.contains(searchQuery, ignoreCase = true) == true
                            }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            // #FAVE-SELF-CHAT: «Избранное» (self-chat) pinned в начале.
                            val myUserId = app.exchangeAuthRepository.userId()
                            if (s.msgShowFavorites && myUserId > 0L && searchQuery.isBlank()) {
                                item(key = "favorites_$myUserId") {
                                    val isSelected = selectedPeerId == myUserId
                                    ShareListItem(
                                        name = "Избранное",
                                        photoUrl = "",
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedPeerId = if (isSelected) null else myUserId
                                            selectedGroupId = null
                                        },
                                    )
                                }
                            }
                            items(filtered, key = { it.peer.id }) { conv ->
                                val peer = conv.peer
                                val isSelected = selectedPeerId == peer.id
                                ShareListItem(
                                    name = peer.title ?: "Диалог ${peer.id}",
                                    photoUrl = peer.photo ?: "",
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedPeerId = if (isSelected) null else peer.id
                                        selectedGroupId = null
                                    },
                                )
                            }
                            if (filtered.isEmpty() && !loadingList) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Нет диалогов",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (loadingList) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        val filtered = if (searchQuery.isBlank()) groups
                            else groups.filter {
                                it.name.contains(searchQuery, ignoreCase = true)
                            }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            items(filtered, key = { it.id }) { group ->
                                val isSelected = selectedGroupId == group.id
                                ShareListItem(
                                    name = group.name,
                                    photoUrl = group.photo100 ?: "",
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedGroupId = if (isSelected) null else group.id
                                        selectedPeerId = null
                                    },
                                )
                            }
                            if (filtered.isEmpty() && !loadingList) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Нет сообществ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Comment field ────────────────────────────────────
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Комментарий…") },
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
            )

            // ── Status / Send button ─────────────────────────────
            statusMsg?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = doShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enabled = !sending && (selectedPeerId != null || selectedGroupId != null),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Отправка…")
                } else {
                    Text("Отправить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            lineHeight = 14.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun ShareListItem(
    name: String,
    photoUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                Icons.Outlined.PostAdd,
                contentDescription = "Выбрано",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}