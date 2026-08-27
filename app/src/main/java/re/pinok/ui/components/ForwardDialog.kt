package re.pinok.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.Group
import re.pinok.util.AppLog

/**
 * Диалог выбора чата для пересылки сообщений (включая файлы/вложения).
 *
 * Показывает bottom-sheet со списком диалогов (из messages.getConversations),
 * поиском и кнопкой «Переслать». Сам выполняет API-вызов [VKApiClient.messagesForward]
 * с cmid-based `forward` JSON (VK API 5.221+), затем вызывает [onForward].
 *
 * Fix #295: раньше принимал `messageIds: List<Long>` (legacy message_id) и
 * дёргал устаревший `forward_messages` — вложения терялись. Теперь принимает
 * `cmids` (conversation_message_id) + `sourcePeerId` — сервер VK переносит
 * сообщение целиком (текст + фото + голосовые + файлы + документы).
 *
 * Fix #144 (2026-08-04): жалоба «При отправке сообщений, постов, файлов не
 * возможно отправить в избранное (то есть себе) и опубликовать в сообществе
 * в котом я администратор».
 *
 * Теперь в начале списка:
 *  1. «Избранное» (peer_id = myUserId) — чат с самим собой, всегда виден
 *     даже если пользователь ещё ни разу не писал себе (VK API не возвращает
 *     пустой self-chat в messages.getConversations).
 *  2. «Мои сообщества» — группы где пользователь admin/editor (через
 *     groups.get?filter=editor). Загружаются параллельно с диалогами.
 *     Пользователь может переслать сообщение в ЛС сообщества (не на стену!).
 *
 * ВАЖНО: пересылка в сообщество = пересылка в ЛС сообщества (peer_id=-gid),
 * НЕ публикация на стене. Для публикации поста на стену сообщества —
 * отдельный флоу в CreatePostDialog.
 *
 * @param currentPeerId peer_id текущего чата (исключается из списка выбора).
 * @param sourcePeerId  peer_id исходного диалога (куда принадлежат cmids).
 * @param cmids         conversation_message_id пересылаемых сообщений.
 * @param onDismiss     Закрыть диалог.
 * @param onForward     Успешная пересылка — callback с targetPeerId.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardDialog(
    currentPeerId: Long,
    sourcePeerId: Long,
    cmids: List<Long>,
    onDismiss: () -> Unit,
    onForward: (targetPeerId: Long) -> Unit,
) {
    val app = SovaApp.get()
    val snap by app.prefs.data.collectAsState(initial = null)
    val s = snap ?: return
    val apiClient = app.apiClient
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Fix #144: myUserId — для «Избранного» (peer_id = myUserId).
    val myUserId = remember { app.exchangeAuthRepository.userId() }

    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    // Fix #144: adminGroups — сообщества где пользователь admin/editor.
    var adminGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPeerId by remember { mutableStateOf<Long?>(null) }
    var forwarding by remember { mutableStateOf(false) }

    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Fix #144: параллельная загрузка диалогов + admin-сообществ.
    // Оба async стартуют concurrently — await() по очереди даёт то же
    // общее время = max(диалоги, группы), не сумма. Если groups.get
    // упадёт — диалоги всё равно загрузятся (catch в async возвращает
    // emptyList с правильным типом).
    // Fix #145: убран awaitAll + cast "as List<Chat>" — он порождал
    // unchecked cast warnings (List<Any> → List<Chat>). Прямой .await()
    // на Deferred<List<Chat>> сохраняет тип без каста.
    LaunchedEffect(Unit) {
        val deferredChats = async {
            try {
                apiClient.messagesGetConversations(count = 50)
                    .filter { it.peer.id != currentPeerId }
            } catch (e: Exception) {
                AppLog.e("ForwardDialog", "load chats error", e)
                emptyList<Chat>()
            }
        }
        val deferredGroups = async {
            try {
                // filter=editor возвращает сообщества где admin_level >= 2
                // (editor + administrator). VK API groups.get.
                apiClient.groupsGet(count = 100, filter = "editor")
                    // peer_id для сообщества = -groupId (VK convention).
                    // Исключаем currentPeerId (нельзя переслать в текущий чат).
                    .filter { -it.id != currentPeerId }
            } catch (e: Exception) {
                AppLog.e("ForwardDialog", "load admin groups error", e)
                emptyList<Group>()
            }
        }
        // Оба уже стартовали concurrently — await() по очереди не добавляет
        // последовательности, просто забирает готовые результаты.
        chats = deferredChats.await()
        adminGroups = deferredGroups.await()
        loading = false
    }

    // Фильтрация по поиску — применяется и к чатам, и к группам.
    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter {
            it.peer.title.orEmpty().contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredGroups = remember(adminGroups, searchQuery) {
        if (searchQuery.isBlank()) adminGroups
        else adminGroups.filter { g ->
            // NULLSAFE-1: явная проверка screenName вместо ?.
            val sn = g.screenName
            g.name.contains(searchQuery, ignoreCase = true) ||
                (sn != null && sn.contains(searchQuery, ignoreCase = true))
        }
    }
    // Fix #144: показывать «Избранное» всегда (не фильтруется поиском
    // по title, но если пользователь ищет «избран» — показываем).
    val showFavorites = remember(searchQuery) {
        // NULLSAFE-1: searchQuery.isBlank() + явная проверка substring.
        if (searchQuery.isBlank()) true
        else "избранное".contains(searchQuery, ignoreCase = true) ||
             "favorites".contains(searchQuery, ignoreCase = true) ||
             "saved".contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Заголовок.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Переслать сообщение",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Поиск.
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск чата…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Список: Избранное → Мои сообщества → Диалоги.
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filteredChats.isEmpty() && filteredGroups.isEmpty() && (!showFavorites || !s.msgShowFavorites) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Нет диалогов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Fix #144: «Избранное» — pinned entry, всегда в начале списка.
                        // peer_id = myUserId (VK convention: chat with self = my user id).
                        // Не показываем если myUserId == 0 (not logged in) или
                        // myUserId == currentPeerId (уже в Избранном).
                        if (s.msgShowFavorites && showFavorites && myUserId > 0L && myUserId != currentPeerId) {
                            item(key = "favorites_$myUserId") {
                                val isSelected = selectedPeerId == myUserId
                                ForwardTargetRow(
                                    title = "Избранное",
                                    subtitle = "Сохранить себе",
                                    photo = null,
                                    isSelected = isSelected,
                                    leadingIcon = Icons.Outlined.Bookmark,
                                    onClick = { selectedPeerId = myUserId },
                                )
                            }
                        }

                        // Fix #144: «Мои сообщества» — секция с admin/editor сообществами.
                        if (filteredGroups.isNotEmpty()) {
                            item(key = "section_admin_groups") {
                                SectionHeader(text = "Мои сообщества (${filteredGroups.size})")
                            }
                            items(filteredGroups, key = { "group_${it.id}" }) { group ->
                                val peerId = -group.id  // VK convention: group peerId = -groupId
                                val isSelected = selectedPeerId == peerId
                                ForwardTargetRow(
                                    title = group.name,
                                    subtitle = group.typeLabel,
                                    photo = group.photo100,
                                    isSelected = isSelected,
                                    leadingIcon = Icons.Outlined.Group,
                                    onClick = { selectedPeerId = peerId },
                                )
                            }
                        }

                        // «Диалоги» — существующий список.
                        if (filteredChats.isNotEmpty()) {
                            item(key = "section_chats") {
                                SectionHeader(text = "Диалоги (${filteredChats.size})")
                            }
                            items(filteredChats, key = { "chat_${it.peer.id}" }) { chat ->
                                val isSelected = selectedPeerId == chat.peer.id
                                val bgColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                                val fgColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor)
                                        .clickable { selectedPeerId = chat.peer.id }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val photo = chat.peer.photo
                                    if (photo != null) {
                                        AsyncImage(
                                            model = photo,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val initial = chat.peer.title.orEmpty().take(1).uppercase()
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        // NULLSAFE-1: явная проверка вместо ?:
                                        val peerTitle = chat.peer.title
                                        val title = if (peerTitle.isNullOrBlank()) "Диалог" else peerTitle
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = fgColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        // NULLSAFE-1: явная проверка вместо ?.
                                        val lastMsg = chat.lastMessage
                                        val lastText = if (lastMsg != null) lastMsg.text else null
                                        if (!lastText.isNullOrBlank()) {
                                            Text(
                                                text = lastText.take(40),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопка «Переслать».
            Button(
                onClick = {
                    val target = selectedPeerId
                    if (target == null) return@Button
                    if (cmids.isEmpty()) {
                        Toast.makeText(ctx, "Не удалось определить сообщения для пересылки", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        forwarding = true
                        try {
                            val msgId = apiClient.messagesForward(target, sourcePeerId, cmids)
                            if (msgId > 0) {
                                Toast.makeText(ctx, "Переслано", Toast.LENGTH_SHORT).show()
                                onForward(target)
                            } else {
                                AppLog.w("ForwardDialog", "forward returned $msgId (target=$target source=$sourcePeerId cmids=$cmids)")
                                Toast.makeText(ctx, "Не удалось переслать (код $msgId)", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            AppLog.e("ForwardDialog", "forward error", e)
                            Toast.makeText(ctx, "Ошибка пересылки: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            forwarding = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPeerId != null && !forwarding && cmids.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (forwarding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                val btnText = if (forwarding) "Пересылка…" else "Переслать"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!forwarding) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = btnText)
                }
            }
        }
    }
}

/**
 * Fix #144: секция-заголовок в списке целей пересылки.
 * Серый текст без фона, отделяет «Мои сообщества» от «Диалоги».
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Fix #144: строка-цель пересылки с иконкой-заглушкой если нет фото.
 * Используется для «Избранное» (Bookmark) и «Мои сообщества» (Group icon).
 * В отличие от диалогов, у Избранного/сообществ может не быть аватара —
 * показываем leading icon вместо буквы-инициала.
 */
@Composable
private fun ForwardTargetRow(
    title: String,
    subtitle: String,
    photo: String?,
    isSelected: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface
    val fgColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // NULLSAFE-1: явная проверка вместо ?. — photo smart-cast к String
        // в true-ветке (Kotlin @Contract на isNullOrBlank()).
        val photoStr = photo
        if (photoStr != null && !photoStr.isNullOrBlank()) {
            AsyncImage(
                model = photoStr,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
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
