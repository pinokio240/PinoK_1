package re.pinok.ui.components

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.Group
import re.pinok.data.model.Post
import re.pinok.util.AppLog

/**
 * ShareSheet — расширенный диалог «Поделиться» (полный паттерн SharePanel
 * vk.com, спека 18-β «план.волна-18.шеринг-каналы-карусель.2026-09-08.md»).
 *
 * Разделы (как в VK web SharePanel):
 *  - «На своей стене»  — пост: `wall.repost object=wall{owner}_{id}` + message;
 *                        сырое вложение (фото/видео/док/аудио): `wall.post`
 *                        owner_id=свой + attachments прямой строкой.
 *  - «В закладки»      — VKApiClient.bookmarksAdd (bookmarks.add type=…,
 *                        web-fallback fave.addPost/fave.addVideo — см. KDoc);
 *                        реальная ошибка API показывается без маскировки.
 *  - «Избранное»       — self-chat (peer_id = мой userId, #FAVE-SELF-CHAT):
 *                        messages.send с wall-attachment (пост) либо прямой
 *                        attachment-строкой (файлы).
 *  - «Копировать ссылку» — каноническая ссылка объекта в буфер обмена.
 *  - Вкладка «Диалоги»   — messages.send: пост → attachment=wall{owner}_{id};
 *                        файлы → прямые attachment-строки (без upload).
 *  - Вкладка «Сообщества» — группы, где могу публиковать (groups.get
 *                        filter=admin,editor,moder + мои сообщества, честный
 *                        фильтр can_post==1); wall.post owner_id=-gid.
 *
 * Контент: пост ([post]) ИЛИ готовые attachment-строки ([attachments] —
 * фото/видео/док/аудио/клип из поверхностей-вызывателей). Внешние файлы
 * (uri с устройства) шеркатся через ShareToChatSheet (upload-контур) —
 * здесь они не нужны.
 *
 * @param post        пост для шеринга (null при шеринге сырых вложений)
 * @param attachments готовые VK attachment-строки (photo{owner}_{id}[_key], …)
 * @param onDismiss   закрытие шторки
 * @param onSuccess   успешная отправка (обновление UI вызывающего экрана)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    post: Post? = null,
    attachments: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {},
) {
    val app = SovaApp.get()
    val snap by app.prefs.data.collectAsState(initial = null)
    val s = snap ?: return
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // ── #SHARE-18B: контент шеринга ─────────────────────────────────────
    // Локальный захват nullable-параметра в val — smart-cast работает во
    // всех ветках ниже (NULL-EXPLICIT, паттерн 3 из CODING_STYLE.md).
    val p = post
    // Пост отсутствует — шеркатся готовые attachment-строки (файлы).
    val attachmentString: String = if (p != null) {
        "wall${p.ownerId}_${p.id}"
    } else {
        attachments.joinToString(",")
    }
    val hasContent: Boolean = p != null || attachments.isNotEmpty()

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
    var selectedGroup by remember { mutableStateOf<Group?>(null) }

    // #SHARE-18B: честный текст последней ошибки API для UI (без маскировки).
    // lastApiError приходит из VKApiClient в формате "<method>: <message>",
    // поэтому пользователь видит какой вызов и почему не прошёл. code==0 —
    // вызов не дошёл до API (сеть/офлайн — до вызова проверяется isOffline).
    fun apiErrorText(action: String): String {
        val code = app.apiClient.lastApiErrorCode
        val msg = app.apiClient.lastApiError
        if (code == 0) return "$action: нет ответа API (сеть/офлайн)"
        if (msg != null) return "Ошибка API $code: $msg"
        return "Ошибка API $code"
    }

    val loadConversations: () -> Unit = {
        scope.launch {
            loadingList = true
            try {
                val result = app.apiClient.messagesGetConversations(count = 50)
                // #SHARE-18B: каналы (can_write.allowed=false) исключаются —
                // messages.send туда невозможен, выбор был бы ложным.
                // canWrite==null → права не пришли, не ограничиваем.
                conversations = result.filter { c ->
                    val cw = c.canWrite
                    c.peer.id != 0L && (cw == null || cw.allowed)
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "loadConversations error", e)
                statusMsg = "Не удалось загрузить диалоги: ${e.message}"
            }
            loadingList = false
        }
    }

    val loadGroups: () -> Unit = {
        scope.launch {
            loadingList = true
            try {
                // #SHARE-18B «Подписчикам сообщества»: два реальных вызова
                // groups.get параллельно — (a) filter=admin,editor,moder
                // (управляемые сообщества), (b) мои сообщества (открытая стена,
                // где участник может постить). Право публикации проверяем
                // ЧЕСТНО по can_post==1 из ответа API — группы без права
                // не показываются вовсе (wall.post без права даёт error 15).
                val managedDeferred = async {
                    try {
                        app.apiClient.groupsGet(count = 100, filter = "admin,editor,moder")
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "loadGroups managed error", e)
                        emptyList<Group>()
                    }
                }
                val memberDeferred = async {
                    try {
                        app.apiClient.groupsGet(userId = null, count = 100)
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "loadGroups member error", e)
                        emptyList<Group>()
                    }
                }
                val merged = LinkedHashMap<Long, Group>()
                for (g in managedDeferred.await()) {
                    merged[g.id] = g
                }
                for (g in memberDeferred.await()) {
                    if (!merged.containsKey(g.id)) merged[g.id] = g
                }
                groups = merged.values.filter { it.canPost == 1 }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "loadGroups error", e)
                statusMsg = "Не удалось загрузить сообщества: ${e.message}"
            }
            loadingList = false
        }
    }

    // Загрузка при переключении вкладки
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        selectedPeerId = null
        selectedGroup = null
        if (selectedTab == 0 && conversations.isEmpty()) loadConversations()
        if (selectedTab == 1 && groups.isEmpty()) loadGroups()
    }

    // Действие отправки (вкладки «Диалоги» / «Сообщества»)
    val doShare: () -> Unit = {
        val targetPeerId = selectedPeerId
        val targetGroup = selectedGroup

        when {
            targetPeerId != null -> {
                scope.launch {
                    sending = true
                    statusMsg = "Отправка в диалог…"
                    try {
                        if (app.apiClient.isOffline()) {
                            statusMsg = "Нет сети — офлайн-режим"
                            sending = false
                            return@launch
                        }
                        // #SHARE-18B «В сообщении»: пост → attachment=wall{owner}_{id}
                        // (sendPostToChat); файлы → прямые attachment-строки
                        // (sendWithAttachment), upload не нужен.
                        val msgId = if (p != null) {
                            app.apiClient.sendPostToChat(
                                peerId = targetPeerId,
                                ownerId = p.ownerId,
                                postId = p.id,
                                message = comment.trim(),
                            )
                        } else {
                            app.apiClient.sendWithAttachment(
                                peerId = targetPeerId,
                                attachment = attachmentString,
                                message = comment.trim(),
                            )
                        }
                        if (msgId > 0) {
                            AppLog.i("ShareSheet", "Sent to chat $targetPeerId, msgId=$msgId")
                            Toast.makeText(context, "Отправлено", Toast.LENGTH_SHORT).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            statusMsg = apiErrorText("Не удалось отправить")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "send exception", e)
                        statusMsg = "Ошибка: ${e.message}"
                    } finally {
                        sending = false
                    }
                }
            }
            targetGroup != null -> {
                scope.launch {
                    sending = true
                    statusMsg = "Публикация в сообществе…"
                    try {
                        // Честная проверка права (defense in depth): список
                        // уже отфильтрован по can_post==1, но состояние могло
                        // измениться между загрузкой и отправкой.
                        if (targetGroup.canPost != 1 && targetGroup.adminLevel < 2) {
                            statusMsg = "Нет права публикации в «${targetGroup.name}»"
                            sending = false
                            return@launch
                        }
                        if (app.apiClient.isOffline()) {
                            statusMsg = "Нет сети — офлайн-режим"
                            sending = false
                            return@launch
                        }
                        // #SHARE-18B «Подписчикам сообщества»: wall.post
                        // owner_id=-gid. Пост → attachments=wall{owner}_{id}
                        // (repostToGroup); файлы → прямые attachment-строки.
                        val newPostId = if (p != null) {
                            app.apiClient.repostToGroup(
                                groupId = targetGroup.id,
                                sourceOwnerId = p.ownerId,
                                sourcePostId = p.id,
                                message = comment.trim(),
                            )
                        } else {
                            app.apiClient.wallPostWithAttachments(
                                message = comment.trim(),
                                attachments = attachmentString,
                                ownerId = -targetGroup.id,
                            )
                        }
                        if (newPostId > 0) {
                            AppLog.i("ShareSheet", "Published to group ${targetGroup.id}, postId=$newPostId")
                            Toast.makeText(context, "Опубликовано в «${targetGroup.name}»", Toast.LENGTH_SHORT).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            statusMsg = apiErrorText("Не удалось опубликовать")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ShareSheet", "group share exception", e)
                        statusMsg = "Ошибка: ${e.message}"
                    } finally {
                        sending = false
                    }
                }
            }
        }
    }

    // Быстрые действия ─ «На своей стене»
    val doRepostToWall: () -> Unit = {
        scope.launch {
            sending = true
            statusMsg = "Публикация на стене…"
            try {
                if (app.apiClient.isOffline()) {
                    statusMsg = "Нет сети — офлайн-режим"
                    sending = false
                    return@launch
                }
                // #SHARE-18B: пост → wall.repost object=wall{owner}_{id}+message;
                // сырое вложение → wall.post owner_id=свой + attachments строкой
                // (wall.repost принимает только wall-объекты).
                val newPostId: Long = if (p != null) {
                    val obj = "wall${p.ownerId}_${p.id}"
                    val (postId, _) = app.apiClient.wallRepost(obj, comment.trim())
                    postId
                } else {
                    app.apiClient.wallPostWithAttachments(
                        message = comment.trim(),
                        attachments = attachmentString,
                    )
                }
                if (newPostId > 0) {
                    AppLog.i("ShareSheet", "Published on own wall: $newPostId")
                    Toast.makeText(context, "Опубликовано на стене", Toast.LENGTH_SHORT).show()
                    onSuccess()
                    onDismiss()
                } else {
                    statusMsg = apiErrorText("Не удалось опубликовать")
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "wall publish exception", e)
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    // Быстрые действия ─ «В закладки» (bookmarks.add / web-fallback)
    val doBookmark: () -> Unit = {
        scope.launch {
            sending = true
            statusMsg = "Добавление в закладки…"
            try {
                if (app.apiClient.isOffline()) {
                    statusMsg = "Нет сети — офлайн-режим"
                    sending = false
                    return@launch
                }
                // #SHARE-18B: пост → type=post owner_id/item_id из поста
                // (+access_key приватного поста); файлы → type из префикса
                // первой attachment-строки. Ошибка API (в т.ч. «метод не
                // поддерживается») показывается честно — apiErrorText.
                val ok: Boolean = if (p != null) {
                    app.apiClient.bookmarksAdd(
                        type = "post",
                        ownerId = p.ownerId,
                        itemId = p.id,
                        accessKey = p.accessKey,
                    )
                } else {
                    val ref = parseAttachmentRef(attachments.firstOrNull())
                    if (ref == null) {
                        statusMsg = "Некорректная attachment-строка: ${attachments.firstOrNull()}"
                        sending = false
                        return@launch
                    }
                    app.apiClient.bookmarksAdd(
                        // wall-вложение = пост (bookmarks.add type=post);
                        // clip → type=clip; photo/video/doc/audio как есть.
                        type = when (ref.type) {
                            "wall" -> "post"
                            else -> ref.type
                        },
                        ownerId = ref.ownerId,
                        itemId = ref.id,
                        accessKey = ref.accessKey,
                    )
                }
                if (ok) {
                    AppLog.i("ShareSheet", "Bookmarked")
                    Toast.makeText(context, "Добавлено в закладки", Toast.LENGTH_SHORT).show()
                    onSuccess()
                    onDismiss()
                } else {
                    statusMsg = apiErrorText("Не удалось добавить в закладки")
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "bookmark exception", e)
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    // Быстрые действия ─ «Избранное» (self-chat, #FAVE-SELF-CHAT)
    val doFavorites: () -> Unit = {
        scope.launch {
            sending = true
            statusMsg = "Отправка в избранное…"
            try {
                // Источник своего peer_id — сохранённый userId сессии
                // (ExchangeTokenStorage.KEY_USER_ID), тот же, что и pinned
                // «Избранное» в MessagesScreen/ForwardDialog.
                val target = app.exchangeAuthRepository.userId()
                if (target <= 0L) {
                    statusMsg = "Не удалось определить аккаунт"
                    sending = false
                    return@launch
                }
                if (app.apiClient.isOffline()) {
                    statusMsg = "Нет сети — офлайн-режим"
                    sending = false
                    return@launch
                }
                val msgId = if (p != null) {
                    app.apiClient.sendPostToChat(
                        peerId = target,
                        ownerId = p.ownerId,
                        postId = p.id,
                        message = comment.trim(),
                    )
                } else {
                    app.apiClient.sendWithAttachment(
                        peerId = target,
                        attachment = attachmentString,
                        message = comment.trim(),
                    )
                }
                if (msgId > 0) {
                    AppLog.i("ShareSheet", "Sent to favorites (self-chat) msgId=$msgId")
                    Toast.makeText(context, "Отправлено в «Избранное»", Toast.LENGTH_SHORT).show()
                    onSuccess()
                    onDismiss()
                } else {
                    statusMsg = apiErrorText("Не удалось отправить в избранное")
                }
            } catch (e: Exception) {
                AppLog.e("ShareSheet", "favorites send exception", e)
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    // Быстрые действия ─ «Копировать ссылку»
    val doCopyLink: () -> Unit = {
        // Канонические ссылки VK web: wall{owner}_{id} → vk.com/wall…,
        // photo/video/doc/audio → vk.com/<type>{owner}_{id}. Для типов без
        // публичной страницы (poll, sticker…) — честное сообщение.
        val link: String? = if (p != null) {
            "https://vk.com/wall${p.ownerId}_${p.id}"
        } else {
            val ref = parseAttachmentRef(attachments.firstOrNull())
            if (ref == null) {
                null
            } else {
                when (ref.type) {
                    "wall" -> "https://vk.com/wall${ref.ownerId}_${ref.id}"
                    "photo" -> "https://vk.com/photo${ref.ownerId}_${ref.id}"
                    "video" -> "https://vk.com/video${ref.ownerId}_${ref.id}"
                    "clip" -> "https://vk.com/clip${ref.ownerId}_${ref.id}"
                    "doc" -> "https://vk.com/doc${ref.ownerId}_${ref.id}"
                    "audio" -> "https://vk.com/audio${ref.ownerId}_${ref.id}"
                    else -> null
                }
            }
        }
        val l = link
        if (l == null) {
            statusMsg = "Ссылка для этого объекта недоступна"
        } else {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            if (cm == null) {
                statusMsg = "Не удалось получить буфер обмена"
            } else {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("VK", l))
                AppLog.i("ShareSheet", "Link copied: $l")
                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
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
                if (selectedPeerId != null || selectedGroup != null) {
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
            // #SHARE-18B: 4 действия VK web SharePanel — стена / закладки /
            // избранное (self-chat) / ссылка. weight(1f) — равные ширины,
            // чтобы 4 ячейки влезали на узких экранах.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAction(
                    icon = Icons.Outlined.PostAdd,
                    label = "На своей\nстене",
                    onClick = doRepostToWall,
                    enabled = !sending && hasContent,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Outlined.BookmarkAdd,
                    label = "В\nзакладки",
                    onClick = doBookmark,
                    enabled = !sending && hasContent,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Outlined.Bookmark,
                    label = "Избранное",
                    onClick = doFavorites,
                    enabled = !sending && hasContent,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Outlined.Link,
                    label = "Копировать\nссылку",
                    onClick = doCopyLink,
                    enabled = !sending && hasContent,
                    modifier = Modifier.weight(1f),
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
                                            selectedGroup = null
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
                                        selectedGroup = null
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
                                // NULL-EXPLICIT: захват var-делегата в val —
                                // сравнение выделения без ?. (smart-cast в val).
                                val sel = selectedGroup
                                val isSelected = sel != null && sel.id == group.id
                                ShareListItem(
                                    name = group.name,
                                    photoUrl = group.photo100 ?: "",
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedGroup = if (isSelected) null else group
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
                enabled = !sending && hasContent && (selectedPeerId != null || selectedGroup != null),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

/**
 * #SHARE-18B: разобранная VK attachment-строка — «photo12345_678»,
 * «video-100_42_abc», «wall123_456» и т.п.
 */
private data class AttachmentRef(
    val type: String,
    val ownerId: Long,
    val id: Long,
    val accessKey: String?,
)

/**
 * #SHARE-18B: парсер attachment-строки формата VK
 * `<type><ownerId>_<id>[_<accessKey>]` (общий хелпер строк — buildVkAttachment
 * в UnifiedAttachMenu, #ATTACH-UNIFY; здесь обратная задача — разбор).
 *
 * Нужен для действий над сырыми вложениями (закладки/ссылка): type определяет
 * bookmarks.add type и каноническую ссылку vk.com/<type>….
 *
 * @return null — строка не распознана (вызывающая сторона показывает
 *         честное сообщение, не молча пропускает).
 */
private fun parseAttachmentRef(raw: String?): AttachmentRef? {
    if (raw == null) return null
    val trimmed = raw.trim()
    val firstUnderscore = trimmed.indexOf('_')
    if (firstUnderscore <= 0) return null
    val typePart = trimmed.substring(0, firstUnderscore)
    // type = буквы префикса; ownerId = остаток (может быть отрицательным:
    // «video-100_42» → type=video, ownerId=-100).
    val type = typePart.filter { it.isLetter() }
    val ownerIdPart = typePart.filter { it.isDigit() || it == '-' }
    if (type.isEmpty()) return null
    if (ownerIdPart.isEmpty()) return null
    val ownerId = ownerIdPart.toLongOrNull()
    if (ownerId == null) return null
    val rest = trimmed.substring(firstUnderscore + 1)
    val parts = rest.split('_')
    if (parts.isEmpty()) return null
    val id = parts[0].toLongOrNull()
    if (id == null) return null
    val accessKey = if (parts.size > 1) {
        val key = parts[1]
        if (key.isNotBlank()) key else null
    } else {
        null
    }
    return AttachmentRef(type = type, ownerId = ownerId, id = id, accessKey = accessKey)
}