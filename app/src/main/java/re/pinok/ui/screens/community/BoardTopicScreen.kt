package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.BoardComment
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

/**
 * Шаг 4 (#32d) + #B-D: Экран темы обсуждения сообщества.
 *
 * #B-D (SCOUT-BOARD-SNAP): доработка по снапшоту VK web SPA —
 *  - composer внизу (OutlinedTextField + Send → boardCreateComment);
 *  - лайки на комментариях (Favorite/FavoriteBorder → boardLikeComment, optimistic);
 *  - имена/аватары авторов (из BoardCommentsResult.authors — profiles[]);
 *  - 3-dot меню (Ответить / Редактировать (canEdit) / Удалить (canDelete));
 *  - сортировка asc/desc (toggle в TopAppBar, перезагрузка);
 *  - заголовок темы + всего комментариев в первом item LazyColumn.
 *
 * Использует [boardGetCommentsFull] (вместо старого boardGetComments) — он возвращает
 * comments + authors (Map<from_id, UserProfile>) + totalCount за один запрос
 * (extended=1, need_likes=1).
 *
 * @param groupId  ID группы (положительный, как в groups[].id)
 * @param topicId  ID темы
 * @param title    Заголовок темы (для TopAppBar + header)
 * @param onBack   Callback возврата
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTopicScreen(
    groupId: Long,
    topicId: Long,
    title: String,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var comments by remember { mutableStateOf<List<BoardComment>>(emptyList()) }
    var authors by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }
    var totalCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // #B-D: сортировка asc/desc (как «Сначала старые/новые» в VK web).
    var sort by remember { mutableStateOf("asc") }
    // #B-D: триггер перезагрузки после отправки комментария.
    var reloadTrigger by remember { mutableStateOf(0) }

    // #B-D: composer state.
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // #B-D: диалог редактирования комментария (3-dot → Редактировать).
    var editingComment by remember { mutableStateOf<BoardComment?>(null) }
    var editText by remember { mutableStateOf("") }

    // B-FIX-4: «Редактировать тему» (board.editTopic). Локальный заголовок
    // темы — обновляется после успешного editTopic (параметр `title` у экрана
    // immutable, без локального state-а в TopAppBar остался бы старый текст).
    var displayTitle by remember { mutableStateOf(title) }
    var showEditTopicDialog by remember { mutableStateOf(false) }
    var editTopicTitle by remember { mutableStateOf(title) }
    var editTopicText by remember { mutableStateOf("") }

    // Первая загрузка (+ перезагрузка при смене sort / после отправки комментария).
    LaunchedEffect(groupId, topicId, sort, reloadTrigger) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val result = app.apiClient.boardGetCommentsFull(
                    groupId = groupId, topicId = topicId, count = 30, offset = 0, sort = sort,
                )
                comments = result.comments
                authors = result.authors
                totalCount = result.totalCount
                endReached = comments.size >= result.totalCount || comments.size < 30
                AppLog.i("BoardTopicScreen", "Loaded ${comments.size}/${result.totalCount} comments for topic $topicId (sort=$sort)")
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "boardGetCommentsFull failed", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Пагинация.
    fun loadMore() {
        if (loadingMore || endReached || comments.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val more = app.apiClient.boardGetCommentsFull(
                    groupId = groupId, topicId = topicId, count = 30, offset = comments.size, sort = sort,
                )
                val newOnes = more.comments.filter { nc -> comments.none { it.id == nc.id } }
                if (newOnes.isEmpty()) {
                    endReached = true
                } else {
                    comments = (comments + newOnes).distinctBy { it.id }
                    authors = authors + more.authors
                }
                if (more.comments.size < 30) endReached = true
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "loadMore failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Триггер пагинации — за 3 элемента до конца.
    LaunchedEffect(listState, comments.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                // #B-D: toggle сортировки asc/desc (как в VK web «Сначала старые/новые»).
                // ↑ = asc (старые сверху), ↓ = desc (новые сверху). Смена sort перезагружает
                // ленту через LaunchedEffect(groupId, topicId, sort, reloadTrigger).
                actions = {
                    // B-FIX-4: «Редактировать тему» (board.editTopic). Гейт упрощён —
                    // показываем всегда: VK сам отклонит (err=15/7) если у юзера нет
                    // прав (не автор темы / не админ группы). canEditTopic можно
                    // сузить позже: groupInfo?.isManager==true ИЛИ первый комментарий
                    // created_by == currentUserId (app.exchangeAuthRepository.userId()).
                    IconButton(onClick = {
                        editTopicTitle = displayTitle
                        editTopicText = ""
                        showEditTopicDialog = true
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Редактировать тему")
                    }
                    IconButton(onClick = {
                        sort = if (sort == "asc") "desc" else "asc"
                    }) {
                        Text(
                            text = if (sort == "asc") "↑" else "↓",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )
        },
        // #B-D: composer внизу (по снапшоту VK web — фиксированная панель ввода:
        // OutlinedTextField + IconButton(Send) → boardCreateComment).
        // imePadding поднимает панель над клавиатурой, navigationBarsPadding — над nav bar
        // (оба consume inset-ы → вложенность безопасна, двойного паддинга нет).
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Написать комментарий…") },
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isBlank() || sending) return@IconButton
                        sending = true
                        val text = commentText
                        scope.launch {
                            try {
                                // boardCreateComment(ownerId, topicId, message): ownerId для
                                // группы = -groupId (отрицательный). Метод внутри берёт abs.
                                val ok = app.apiClient.boardCreateComment(-groupId, topicId, text)
                                if (ok) {
                                    commentText = ""
                                    reloadTrigger++  // перезагрузить комментарии с нулевого offset.
                                }
                                Toast.makeText(
                                    ctx,
                                    if (ok) "Отправлено" else "Не удалось",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                sending = false
                            }
                        }
                    },
                    enabled = commentText.isNotBlank() && !sending,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorText != null && comments.isEmpty() -> {
                val msg = errorText
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            comments.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В теме нет комментариев",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            else -> {
                // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх»
                // над лентой комментариев темы (пагинация board.getComments).
                // innerPadding перенесён с LazyColumn на Box — FAB тоже внутри отступа.
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // #B-D: заголовок темы + всего комментариев (по снапшоту VK web
                    // — заголовок темы и счётчик в начале ленты).
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (totalCount > 0) {
                                Text(
                                    // B-FIX-3: pluralComments теперь возвращает всю строку «N комментарий»
                                    text = pluralComments(totalCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    items(comments, key = { it.id }) { comment ->
                        BoardCommentCard(
                            comment = comment,
                            authors = authors,
                            onLike = { c ->
                                val wasLiked = c.isLiked
                                // Optimistic: сразу обновляем UI, откатим при ошибке API.
                                comments = comments.map {
                                    if (it.id == c.id) it.copy(
                                        likes = Post.Likes(
                                            count = (it.likes?.count ?: 0) + (if (wasLiked) -1 else 1),
                                            userLikes = if (wasLiked) 0 else 1,
                                            canLike = it.likes?.canLike ?: 1,
                                        )
                                    ) else it
                                }
                                scope.launch {
                                    val ok = app.apiClient.boardLikeComment(groupId, c.id, like = !wasLiked)
                                    if (!ok) {
                                        // Откат optimistic-обновления.
                                        comments = comments.map { if (it.id == c.id) c else it }
                                        Toast.makeText(ctx, "Не удалось поставить лайк", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onReply = { c ->
                                // Вставить имя автора в composer (упрощённый «ответ» —
                                // VK web открывает инлайн-форму ответа, для MVP подставляем имя).
                                val author = authors[c.creatorId]
                                val name = author?.firstName?.takeIf { it.isNotBlank() } ?: ""
                                if (name.isNotBlank()) commentText = "$name, "
                            },
                            onDelete = { c ->
                                scope.launch {
                                    val ok = app.apiClient.boardDeleteComment(groupId, topicId, c.id)
                                    if (ok) {
                                        comments = comments.filterNot { it.id == c.id }
                                        if (totalCount > 0) totalCount -= 1
                                        Toast.makeText(ctx, "Удалено", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, "Не удалось удалить", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onEdit = { c ->
                                editingComment = c
                                editText = c.text
                            },
                        )
                    }
                    // Футер пагинации.
                    item {
                        when {
                            loadingMore -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            endReached -> {
                                Text(
                                    text = "Это все комментарии",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }

                // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх»
                // над комментариями темы (board.getComments offset-пагинация).
                ScrollToTopFab(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
                }
            }
        }
    }

    // #B-D: диалог редактирования комментария (3-dot → Редактировать).
    val editing = editingComment
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text("Редактирование комментария") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Текст комментария") },
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = editing
                    val newText = editText
                    editingComment = null
                    // c уже non-null (editing smart-cast в if (editing != null) выше).
                    // WARN-FIX: убрана избыточная проверка c != null (always true).
                    if (newText.isNotBlank()) {
                        scope.launch {
                            val ok = app.apiClient.boardEditComment(groupId, topicId, c.id, newText)
                            if (ok) {
                                comments = comments.map {
                                    if (it.id == c.id) it.copy(text = newText) else it
                                }
                                Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    // B-FIX-4: диалог редактирования темы (board.editTopic). Поля: Заголовок
    // (max 255) + Текст (max 4096). На «Сохранить» вызываем boardEditTopic —
    // при успехе обновляем displayTitle (TopAppBar + header) и закрываем диалог.
    if (showEditTopicDialog) {
        AlertDialog(
            onDismissRequest = { showEditTopicDialog = false },
            title = { Text("Редактирование темы") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTopicTitle,
                        onValueChange = { if (it.length <= 255) editTopicTitle = it },
                        label = { Text("Заголовок") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editTopicText,
                        onValueChange = { if (it.length <= 4096) editTopicText = it },
                        label = { Text("Текст") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Заголовок обязателен — VK требует title (пустой → err).
                    if (editTopicTitle.isBlank()) return@TextButton
                    val newTitle = editTopicTitle
                    val newText = editTopicText.ifBlank { null }
                    scope.launch {
                        val ok = app.apiClient.boardEditTopic(
                            groupId = groupId,
                            topicId = topicId,
                            title = newTitle,
                            text = newText,
                        )
                        if (ok) {
                            displayTitle = newTitle
                            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
                            showEditTopicDialog = false
                        } else {
                            // VK отклонил (err=15/7 — нет прав, иное). lastApiError
                            // содержит код+сообщение из response.error.
                            Toast.makeText(
                                ctx,
                                "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTopicDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/**
 * Склонение «комментарий/комментария/комментариев» по числу (RU plural rules).
 * B-FIX-3: internal — переиспользуется в CommunityScreen.BoardTopicRow.
 * Возвращает всю строку «N комментарий» (число уже включено).
 */
internal fun pluralComments(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return "$n " + when {
        mod10 == 1 && mod100 != 11 -> "комментарий"
        mod10 in 2..4 && mod100 !in 12..14 -> "комментария"
        else -> "комментариев"
    }
}

/**
 * Карточка комментария темы обсуждения.
 *
 * #B-D (SCOUT-BOARD-SNAP): по снапшоту VK web —
 *  - аватар 28dp + имя автора (из [authors] — profiles[]). Фото если есть, иначе буква;
 *  - 3-dot меню (Действия): Ответить / Редактировать (canEdit==1) / Удалить (canDelete==1);
 *  - лайк (Favorite/FavoriteBorder) с optimistic-счётчиком (likesCount).
 */
@Composable
private fun BoardCommentCard(
    comment: BoardComment,
    authors: Map<Long, UserProfile>,
    onLike: (BoardComment) -> Unit,
    onReply: (BoardComment) -> Unit,
    onDelete: (BoardComment) -> Unit,
    onEdit: (BoardComment) -> Unit,
) {
    val author = authors[comment.creatorId]
    val authorName = author?.fullName?.takeIf { it.isNotBlank() }
        ?: if (comment.creatorId > 0) "Пользователь #${comment.creatorId}"
           else "Сообщество #${-comment.creatorId}"
    val authorPhoto = author?.photo100
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Аватар 28dp (как в снапшоте VK web). Фото если есть, иначе буква имени.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (authorPhoto != null) {
                    AsyncImage(
                        model = authorPhoto,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = authorName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Имя автора (реальное, из profiles[]) — weight(1f) чтобы не вытесняло дату/меню.
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = comment.created.toAbsoluteTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    // #B-D: 3-dot меню (Действия) — Ответить / Редактировать / Удалить.
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Действия")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ответить") },
                                onClick = {
                                    showMenu = false
                                    onReply(comment)
                                },
                            )
                            if (comment.canEdit == 1) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    onClick = {
                                        showMenu = false
                                        onEdit(comment)
                                    },
                                )
                            }
                            if (comment.canDelete == 1) {
                                DropdownMenuItem(
                                    text = { Text("Удалить") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete(comment)
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // #B-D: лайк на комментарии (Favorite/FavoriteBorder, optimistic).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onLike(comment) }) {
                        Icon(
                            imageVector = if (comment.isLiked) Icons.Filled.Favorite
                                          else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Лайк",
                            tint = if (comment.isLiked) Color(0xFFE91E63)
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (comment.likesCount > 0) {
                        Text(
                            text = "${comment.likesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
