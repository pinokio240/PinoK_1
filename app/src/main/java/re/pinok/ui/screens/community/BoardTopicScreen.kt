package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.BoardComment
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

/** Размер страницы комментариев (VK web: count=20). */
private const val PAGE_SIZE = 20
/** Порог showmoretext: комментарии длиннее обрезаются до превью. */
private const val SHOWMORE_THRESHOLD = 200
/** Максимум кнопок пагинации в ряд (1..10), далее «…» + последняя страница. */
private const val MAX_PAGINATION_BUTTONS = 10

/**
 * #B-FIX-2 (AUDIT-BOARD-SNAP-VS-CODE): Экран темы обсуждения сообщества
 * по снапшоту VK web SPA.
 *
 * 11 HIGH-расхождений из аудита закрыты:
 *  - composer в потоке (НЕ fixed bottomBar) — последний item LazyColumn;
 *  - тело темы = первый comment-блок (BoardCommentCard, отдельный fetch count=1 offset=0);
 *  - пагинация кнопками 1/2/3/4/» (НЕ infinite scroll);
 *  - composer: avatar 36dp текущего юзера + attach + emoji + send;
 *  - вложения: media-grid (photos 80dp, radius 8dp) + doc (40dp icon + title + ext);
 *  - like без счётчика (только иконка, как like_outline_16 в VK web);
 *  - owner-name кликабельная ссылка (TextButton, primary);
 *  - showmoretext для длинных комментариев (>200 символов, «Показать полностью»);
 *  - sort dropdown «Сначала старые/новые» (TextButton + ArrowDropDown, НЕ glyph).
 *
 * Использует [re.pinok.api.VKApiClient.boardGetCommentsFull] (extended=1, need_likes=1) —
 * comments + authors (Map<from_id, UserProfile>) + totalCount за один запрос.
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
    var errorText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // #B-FIX-2: тело темы = первый комментарий (count=1 offset=0 sort=asc) —
    // отдельный запрос, т.к. на не-первой странице тело всё равно должно быть сверху
    // (по снапшоту VK web: дублирующий запрос board.getComments[count=1,offset=0]).
    var topicBody by remember { mutableStateOf<BoardComment?>(null) }
    var topicBodyAuthors by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }

    // #B-FIX-2: аватар текущего юзера 36dp для composer (photo_100 из users.get).
    var myPhoto by remember { mutableStateOf<String?>(null) }

    // #B-FIX-2: сортировка asc/desc + dropdown состояния.
    var sort by remember { mutableStateOf("asc") }
    var sortExpanded by remember { mutableStateOf(false) }

    // #B-FIX-2: пагинация кнопками (НЕ infinite scroll).
    var currentPage by remember { mutableStateOf(1) }
    var offset by remember { mutableStateOf(0) }

    // Триггер перезагрузки после отправки комментария.
    var reloadTrigger by remember { mutableStateOf(0) }

    // Composer state.
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // Диалог редактирования комментария (3-dot → Редактировать).
    var editingComment by remember { mutableStateOf<BoardComment?>(null) }
    var editText by remember { mutableStateOf("") }

    // Загрузка текущего юзера (аватар 36dp для composer).
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val me = app.apiClient.usersGet(null)
                if (me != null) myPhoto = me.photo100
            } catch (e: Exception) {
                AppLog.w("BoardTopicScreen", "usersGet failed for composer avatar", e)
            }
        }
    }

    // #B-FIX-2: тело темы = первый комментарий (всегда offset=0 sort=asc,
    // независимо от текущей страницы/сортировки списка).
    LaunchedEffect(groupId, topicId, reloadTrigger) {
        scope.launch {
            try {
                val body = app.apiClient.boardGetCommentsFull(
                    groupId = groupId, topicId = topicId,
                    count = 1, offset = 0, sort = "asc",
                )
                topicBody = body.comments.firstOrNull()
                topicBodyAuthors = if (topicBodyAuthors.isEmpty()) body.authors
                                   else topicBodyAuthors + body.authors
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "topic body fetch failed", e)
            }
        }
    }

    // Загрузка текущей страницы комментариев (offset = (currentPage-1)*PAGE_SIZE).
    LaunchedEffect(groupId, topicId, sort, offset, reloadTrigger) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val result = app.apiClient.boardGetCommentsFull(
                    groupId = groupId, topicId = topicId,
                    count = PAGE_SIZE, offset = offset, sort = sort,
                )
                comments = result.comments
                authors = result.authors
                totalCount = result.totalCount
                AppLog.i("BoardTopicScreen",
                    "Loaded ${comments.size}/${result.totalCount} comments " +
                    "(page=$currentPage, offset=$offset, sort=$sort)")
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "boardGetCommentsFull failed", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // #B-FIX-2: переход на страницу (перезагружает комментарии через LaunchedEffect[offset]).
    fun goToPage(p: Int) {
        val pages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE
        val target = p.coerceIn(1, if (pages > 0) pages else 1)
        if (target == currentPage) return
        currentPage = target
        offset = (target - 1) * PAGE_SIZE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                // #B-FIX-2: sort dropdown «Сначала старые/новые» (по снапшоту VK web —
                // TextButton + ArrowDropDown, НЕ glyph ↑/↓). Смена sort перезагружает
                // ленту через LaunchedEffect(groupId, topicId, sort, offset, reloadTrigger).
                actions = {
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
                            Text(if (sort == "asc") "Сначала старые" else "Сначала новые")
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Сначала старые") },
                                onClick = {
                                    sort = "asc"
                                    sortExpanded = false
                                    currentPage = 1
                                    offset = 0
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Сначала новые") },
                                onClick = {
                                    sort = "desc"
                                    sortExpanded = false
                                    currentPage = 1
                                    offset = 0
                                },
                            )
                        }
                    }
                },
            )
        },
        // #B-FIX-2: composer перенесён из bottomBar в конец LazyColumn (в потоке,
        // прокручивается с контентом — как в снапшоте VK web, НЕ fixed).
    ) { innerPadding ->
        when {
            loading && comments.isEmpty() -> {
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
            comments.isEmpty() && topicBody == null -> {
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
                // Box-обёртка — оверлей для FAB «наверх» над лентой комментариев темы.
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Заголовок темы + всего комментариев.
                        item(key = "topic_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (totalCount > 0) {
                                    Text(
                                        text = pluralComments(totalCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }

                        // #B-FIX-2: пагинация кнопками 1/2/3/4/» (по снапшоту VK web — TOP).
                        item(key = "pagination_top") {
                            val pages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE
                            if (pages > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val shown = minOf(pages, MAX_PAGINATION_BUTTONS)
                                    for (p in 1..shown) {
                                        TextButton(
                                            onClick = { goToPage(p) },
                                            colors = if (p == currentPage)
                                                ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary,
                                                )
                                            else ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        ) { Text("$p") }
                                    }
                                    if (pages > MAX_PAGINATION_BUTTONS) {
                                        Text(
                                            text = "…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                        TextButton(onClick = { goToPage(pages) }) { Text("$pages") }
                                    }
                                    // Кнопка «вперёд» (»).
                                    TextButton(
                                        onClick = { goToPage(currentPage + 1) },
                                        enabled = currentPage < pages,
                                    ) { Text("»") }
                                }
                            }
                        }

                        // #B-FIX-2: тело темы = первый comment-блок (по снапшоту VK web —
                        // первый комментарий отображается как тело темы тем же UI, что и
                        // обычные комментарии). Фетчится отдельно (count=1 offset=0 sort=asc).
                        val body = topicBody
                        if (body != null) {
                            item(key = "topic_body_${body.id}") {
                                BoardCommentCard(
                                    comment = body,
                                    authors = topicBodyAuthors + authors,
                                    onLike = { c ->
                                        // Optimistic + rollback для тела темы.
                                        val wasLiked = c.isLiked
                                        topicBody = c.copy(
                                            likes = Post.Likes(
                                                count = (c.likes?.count ?: 0) + (if (wasLiked) -1 else 1),
                                                userLikes = if (wasLiked) 0 else 1,
                                                canLike = c.likes?.canLike ?: 1,
                                            ),
                                        )
                                        scope.launch {
                                            val ok = app.apiClient.boardLikeComment(groupId, c.id, like = !wasLiked)
                                            if (!ok) {
                                                topicBody = c
                                                Toast.makeText(ctx, "Не удалось поставить лайк", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onReply = { c ->
                                        val author = (topicBodyAuthors + authors)[c.creatorId]
                                        val name = author?.firstName?.takeIf { it.isNotBlank() } ?: ""
                                        if (name.isNotBlank()) commentText = "$name, "
                                    },
                                    onDelete = { c ->
                                        scope.launch {
                                            val ok = app.apiClient.boardDeleteComment(groupId, topicId, c.id)
                                            if (ok) {
                                                topicBody = null
                                                if (totalCount > 0) totalCount -= 1
                                                reloadTrigger++
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
                                    onUserClick = { /* TODO: nav to user profile */ },
                                )
                            }
                        }

                        // Список комментариев текущей страницы. Пропускаем topicBody.id,
                        // чтобы не дублировать тело темы (если оно попало в текущую страницу).
                        val bodyId = body?.id ?: -1L
                        val pageComments = comments.filter { it.id != bodyId }
                        items(pageComments, key = { it.id }) { comment ->
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
                                onUserClick = { /* TODO: nav to user profile */ },
                            )
                        }

                        // #B-FIX-2: composer в потоке (НЕ fixed) — последний item LazyColumn.
                        // По снапшоту VK web: avatar 36dp + contenteditable + attach + emoji + send.
                        item(key = "composer") {
                            ComposerRow(
                                text = commentText,
                                onTextChange = { commentText = it },
                                onSend = {
                                    if (commentText.isBlank() || sending) return@ComposerRow
                                    sending = true
                                    val text = commentText
                                    scope.launch {
                                        try {
                                            // boardCreateComment(ownerId, topicId, message): ownerId для
                                            // группы = -groupId (отрицательный). Метод внутри берёт abs.
                                            val ok = app.apiClient.boardCreateComment(-groupId, topicId, text)
                                            if (ok) {
                                                commentText = ""
                                                reloadTrigger++  // перезагрузить комментарии.
                                                currentPage = 1
                                                offset = 0
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
                                sending = sending,
                                myPhoto = myPhoto,
                            )
                        }
                    }

                    // FAB «наверх» над комментариями темы.
                    ScrollToTopFab(
                        listState = listState,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    // Диалог редактирования комментария (3-dot → Редактировать).
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
                    if (newText.isNotBlank()) {
                        scope.launch {
                            val ok = app.apiClient.boardEditComment(groupId, topicId, c.id, newText)
                            if (ok) {
                                // Обновляем и в списке, и в теле темы (если редактируется тело).
                                comments = comments.map {
                                    if (it.id == c.id) it.copy(text = newText) else it
                                }
                                topicBody = topicBody?.let {
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
}

/**
 * Склонение «комментарий/комментария/комментариев» по числу (RU plural rules).
 */
internal fun pluralComments(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "комментарий"
        mod10 in 2..4 && mod100 !in 12..14 -> "комментария"
        else -> "комментариев"
    }
    return "$n $word"
}

/**
 * Карточка комментария темы обсуждения.
 *
 * #B-FIX-2 (AUDIT-BOARD-SNAP-VS-CODE): по снапшоту VK web —
 *  - аватар 28dp + имя автора (из [authors] — profiles[]), кликабельное (TextButton primary);
 *  - 3-dot меню (Действия): Ответить / Редактировать (canEdit==1) / Удалить (canDelete==1);
 *  - лайк БЕЗ счётчика (только Favorite/FavoriteBorder, как like_outline_16 в VK web);
 *  - showmoretext для длинных комментариев (>200 символов, «Показать полностью»/«Свернуть»);
 *  - вложения: photo media-grid (80dp, RoundedCornerShape 8dp) + doc (40dp icon + title + ext).
 *
 * @param onUserClick callback клика по имени автора (id автора комментария).
 */
@Composable
private fun BoardCommentCard(
    comment: BoardComment,
    authors: Map<Long, UserProfile>,
    onLike: (BoardComment) -> Unit,
    onReply: (BoardComment) -> Unit,
    onDelete: (BoardComment) -> Unit,
    onEdit: (BoardComment) -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val author = authors[comment.creatorId]
    val authorName = author?.fullName?.takeIf { it.isNotBlank() }
        ?: if (comment.creatorId > 0) "Пользователь #${comment.creatorId}"
           else "Сообщество #${-comment.creatorId}"
    val authorPhoto = author?.photo100
    var showMenu by remember { mutableStateOf(false) }
    // #B-FIX-2: showmoretext — свёрнутый длинный текст комментария.
    var expanded by remember { mutableStateOf(false) }

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
                    // #B-FIX-2: owner-name — кликабельная ссылка (TextButton, primary).
                    // По снапшоту VK web owner-link ведёт на профиль автора.
                    TextButton(
                        onClick = { onUserClick(comment.creatorId) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = comment.created.toAbsoluteTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    // 3-dot меню (Действия) — Ответить / Редактировать / Удалить.
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
                // #B-FIX-2: showmoretext — превью для длинных комментариев
                // (по снапшоту VK web: showmoretext-in-expanded с clamp + «Показать полностью»).
                val displayText = if (comment.text.length > SHOWMORE_THRESHOLD && !expanded) {
                    comment.text.take(SHOWMORE_THRESHOLD) + "…"
                } else {
                    comment.text
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (comment.text.length > SHOWMORE_THRESHOLD) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (expanded) "Свернуть" else "Показать полностью")
                    }
                }
                // #B-FIX-2: вложения — photo media-grid + doc secondaryattachment.
                CommentAttachments(comment.attachments)
                // #B-FIX-2: лайк БЕЗ счётчика (только иконка, как like_outline_16 в VK web).
                IconButton(onClick = { onLike(comment) }) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Filled.Favorite
                                      else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Лайк",
                        tint = if (comment.isLiked) Color(0xFFE91E63)
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * #B-FIX-2: вложения комментария — фото (media-grid, 80dp, radius 8dp) +
 * документы (40dp иконка + title + ext). По снапшоту VK web.
 */
@Composable
private fun CommentAttachments(attachments: List<Attachment>?) {
    if (attachments.isNullOrEmpty()) return
    val photos = attachments.filter { it.type == "photo" && it.photo != null }
    val docs = attachments.filter { it.type == "doc" && it.doc != null }
    if (photos.isEmpty() && docs.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Photos: media-grid (up to 3 в ряд, 80dp, RoundedCornerShape 8dp).
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                photos.take(3).forEach { att ->
                    val url = att.photo?.largestUrl ?: att.photo?.mediumUrl
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        // Docs: 40dp иконка + title + ext (secondaryattachment в VK web).
        docs.forEach { att ->
            val doc = att.doc ?: return@forEach
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = doc.ext.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * #B-FIX-2: Composer в потоке (НЕ fixed bottomBar) — последний item LazyColumn.
 * По снапшоту VK web: avatar 36dp текущего юзера + OutlinedTextField + attach + emoji + send.
 *
 * @param myPhoto URL аватара текущего юзера (photo_100 из users.get); null → fallback Person icon.
 * @param sending флаг отправки (показывает CircularProgressIndicator вместо Send).
 */
@Composable
private fun ComposerRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    myPhoto: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar 36dp текущего юзера (comment-author-avatar в VK web).
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (myPhoto != null) {
                AsyncImage(
                    model = myPhoto,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Написать комментарий…") },
            maxLines = 4,
        )
        Spacer(Modifier.width(4.dp))
        // Attach (show-attach-dropdown / attach_20 в VK web).
        IconButton(onClick = { /* TODO: attach */ }) {
            Icon(Icons.Outlined.AttachFile, contentDescription = "Прикрепить")
        }
        // Emoji (emoji-smile / smile_outline_20 в VK web).
        IconButton(onClick = { /* TODO: emoji */ }) {
            Icon(Icons.Outlined.EmojiEmotions, contentDescription = "Эмодзи")
        }
        // Send (send_24 в VK web, disabled до ввода).
        IconButton(onClick = onSend, enabled = text.isNotBlank() && !sending) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
            }
        }
    }
}
