package re.pinok.ui.screens.community

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.BoardComment
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.ui.theme.BoardAccent
import re.pinok.util.AppLog
import re.pinok.util.buildBoardCommentText
import re.pinok.util.toBoardCommentDate

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
 * #B-FIX-5 (по снапшоту HTML3): 3 расхождения закрыты:
 *  - «Ответить» — отдельная TextButton в meta-row (между дата и лайк), НЕ в 3-dot меню;
 *  - onReply вставляет VK mention format `[id{userId}|{firstName}], ` в composer;
 *  - onUserClick wiring: имя автора → навигация на UserProfile (из SovaNavHost).
 *
 * Использует [re.pinok.api.VKApiClient.boardGetCommentsFull] (extended=1, need_likes=1) —
 * comments + authors (Map<from_id, UserProfile>) + totalCount за один запрос.
 *
 * @param groupId     ID группы (положительный, как в groups[].id)
 * @param topicId     ID темы
 * @param title       Заголовок темы (для TopAppBar + header)
 * @param onBack      Callback возврата
 * @param onUserClick Callback клика по имени автора комментария (id) → UserProfile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTopicScreen(
    groupId: Long,
    topicId: Long,
    title: String,
    onBack: () -> Unit,
    // #B-FIX-5: клик по имени автора комментария (comment-owner в снапшоте VK web)
    // → навигация на UserProfile. Передаётся из SovaNavHost.
    onUserClick: (Long) -> Unit = {},
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

    // COMPOSER-REUSE: attach/emoji/upload state, заимствован из PostDetailScreen composer.
    // (attachmentString опущен — board-композер не имеет AttachmentPickerSheet-флоу;
    //  photo/file идут напрямую через pendingPhotoUris/pendingFileUris → upload → CSV.)
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFileUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // COMPOSER-REUSE: multi-pickers (PickMultipleVisualMedia/OpenMultipleDocument),
    // как PostDetailScreen L242+. До 10 фото / 10 файлов за раз.
    val commentMultiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPhotoUris = (pendingPhotoUris + uris).take(10)
        }
    }
    val commentMultiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingFileUris = (pendingFileUris + uris).take(10)
        }
    }

    // Диалог редактирования комментария (3-dot → Редактировать).
    var editingComment by remember { mutableStateOf<BoardComment?>(null) }
    var editText by remember { mutableStateOf("") }

    // B-FIX-8: «Редактировать тему» (board.editTopic). Локальный заголовок темы —
    // обновляется после успешного editTopic (параметр `title` immutable, без локального
    // state-а TopAppBar/header остались бы старым текстом). Восстановлено из B-FIX-4
    // (было утеряно при rewrite BoardTopicScreen в B-FIX-2).
    var displayTitle by remember { mutableStateOf(title) }
    var showEditTopicDialog by remember { mutableStateOf(false) }
    var editTopicTitle by remember { mutableStateOf(title) }
    var editTopicText by remember { mutableStateOf("") }

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
                // #B-FIX-2: sort dropdown «Сначала старые/новые» (по снапшоту VK web —
                // TextButton + ArrowDropDown, НЕ glyph ↑/↓). Смена sort перезагружает
                // ленту через LaunchedEffect(groupId, topicId, sort, offset, reloadTrigger).
                actions = {
                    // B-FIX-8: «Редактировать тему» TextButton (modePrimary accent, как
                    // board-topic-edit-button в снапшоте VK web). Гейт упрощён — показываем
                    // всегда: VK отклонит (err=15/7) если нет прав (не автор/не админ).
                    // VK-STYLING-2 (AUDIT #29): board-topic-edit-button — filled accent
                    // button (vkuiButton sizeS modePrimary appearanceAccent), НЕ TextButton.
                    Button(
                        onClick = {
                            editTopicTitle = displayTitle
                            editTopicText = ""
                            showEditTopicDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BoardAccent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Редактировать тему", style = MaterialTheme.typography.labelMedium)
                    }
                    Box {
                        // B-FIX-7 (AUDIT #54): accent #447bba (board_comments_sorting —
                        // modeLink accent по снапшоту VK web).
                        TextButton(
                            onClick = { sortExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = BoardAccent),
                        ) {
                            Text(if (sort == "asc") "Сначала старые" else "Сначала новые")
                            // VK-STYLING-2 (AUDIT #30): dropdown_outline_16 (16dp, НЕ default 24).
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
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
        // PIN-COMPOSER: composer закреплён внизу (Scaffold.bottomBar), как в
        // ChatDetailScreen / PostDetailScreen. БЕЗ разделителя — Material3 сам
        // отделяет bottomBar от content. B-FIX-2 ошибочно перенёс composer в
        // LazyColumn item (уезжал со скроллом) — противоречит паттерну сообщений.
        bottomBar = {
            ComposerRow(
                text = commentText,
                onTextChange = { commentText = it },
                sending = sending,
                onSendingChange = { sending = it },
                uploading = uploading,
                onUploadingChange = { uploading = it },
                myPhoto = myPhoto,
                showAttachMenu = showAttachMenu,
                onShowAttachMenuChange = { showAttachMenu = it },
                showEmojiPanel = showEmojiPanel,
                onShowEmojiPanelChange = { showEmojiPanel = it },
                pendingPhotoUris = pendingPhotoUris,
                onPendingPhotoUrisChange = { pendingPhotoUris = it },
                pendingFileUris = pendingFileUris,
                onPendingFileUrisChange = { pendingFileUris = it },
                onLaunchPhotoPicker = {
                    commentMultiPhotoLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onLaunchFilePicker = {
                    commentMultiFileLauncher.launch(arrayOf("*/*"))
                },
                groupId = groupId,
                topicId = topicId,
                onSentSuccess = {
                    reloadTrigger++  // перезагрузить комментарии.
                    currentPage = 1
                    offset = 0
                },
                // B-FIX-B: показывать Switch «От имени сообщества». В BoardTopicScreen
                // нет флага isManager (маршрут без admin-флага), поэтому default true.
                // VK отклонит err=15 если у юзера нет прав — Switch просто не сработает.
                showFromGroup = true,
            )
        },
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
                                // VK-STYLING-2 (AUDIT #25): board-topic-title — title2
                                // (20px / 600). headlineSmall=24sp слишком крупно → 20sp.
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    // B-FIX-7 (AUDIT #54): accent #447bba (vkcom_light).
                                    color = BoardAccent,
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
                                                    // B-FIX-7 (AUDIT #54): accent #447bba.
                                                    contentColor = BoardAccent,
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
                                        // #B-FIX-5: VK mention format [id{userId}|{firstName}]
                                        // — VK распарсит и рендерит как ссылку, автор получит
                                        // уведомление об ответе (как в VK web SPA).
                                        val author = (topicBodyAuthors + authors)[c.creatorId]
                                        val firstName = author?.firstName?.takeIf { it.isNotBlank() }
                                        val userId = c.creatorId
                                        val mention = if (firstName != null && userId != 0L)
                                            "[id$userId|$firstName], " else ""
                                        commentText = if (commentText.isBlank()) mention
                                                      else mention + commentText
                                        // TODO: установить фокус на composer (focusRequester).
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
                                    onUserClick = onUserClick,  // #B-FIX-5: wiring → UserProfile
                                    groupId = groupId,
                                    topicId = topicId,
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
                                    // #B-FIX-5: VK mention format [id{userId}|{firstName}]
                                    // — VK распарсит и рендерит как ссылку, автор получит
                                    // уведомление об ответе (как в VK web SPA).
                                    val author = authors[c.creatorId]
                                    val firstName = author?.firstName?.takeIf { it.isNotBlank() }
                                    val userId = c.creatorId
                                    val mention = if (firstName != null && userId != 0L)
                                        "[id$userId|$firstName], " else ""
                                    commentText = if (commentText.isBlank()) mention
                                                  else mention + commentText
                                    // TODO: установить фокус на composer (focusRequester).
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
                                onUserClick = onUserClick,  // #B-FIX-5: wiring → UserProfile
                                groupId = groupId,
                                topicId = topicId,
                            )
                            // VK-STYLING (AUDIT #33): разделитель между комментариями
                            // (как <hr class="RCvVnl"> в снапшоте VK web, color #dce1e6).
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
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

    // B-FIX-8: диалог редактирования темы (board.editTopic). Поля: Заголовок
    // (max 255) + Текст (max 4096). На «Сохранить» вызываем boardEditTopic —
    // при успехе обновляем displayTitle (TopAppBar + header) и закрываем диалог.
    // Восстановлено из B-FIX-4 (утеряно при rewrite BoardTopicScreen в B-FIX-2).
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
 *  - лайк БЕЗ счётчика (только Favorite/FavoriteBorder, как like_outline_16 в VK web);
 *  - showmoretext для длинных комментариев (>200 символов, «Показать полностью»/«Свернуть»);
 *  - вложения: photo media-grid (80dp, RoundedCornerShape 8dp) + doc (40dp icon + title + ext).
 *
 * #B-FIX-5 (по снапшоту HTML3): meta-row = date + «Ответить» (отдельная TextButton,
 * accent/primary, НЕ в 3-dot меню) + spacer + like. 3-dot меню теперь только
 * Редактировать (canEdit==1) / Удалить (canDelete==1). Имя автора кликабельно →
 * [onUserClick] → навигация на UserProfile.
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
    // B-FIX-B (TRACER-2 gap): groupId/topicId для «Скопировать ссылку»
    // (VK topic URL: vk.com/topic-{groupId}_{topicId}?post={commentId}).
    groupId: Long = 0L,
    topicId: Long = 0L,
) {
    val author = authors[comment.creatorId]
    val authorName = author?.fullName?.takeIf { it.isNotBlank() }
        ?: if (comment.creatorId > 0) "Пользователь #${comment.creatorId}"
           else "Сообщество #${-comment.creatorId}"
    val authorPhoto = author?.photo100
    var showMenu by remember { mutableStateOf(false) }
    // #B-FIX-2: showmoretext — свёрнутый длинный текст комментария.
    var expanded by remember { mutableStateOf(false) }
    // B-FIX-B: ctx для Toast/Clipboard в меню «Скопировать ссылку».
    val ctx = LocalContext.current

    // VK-STYLING (AUDIT #1/#2/#4/#5): flat Surface (radius 8dp, БЕЗ elevation),
    // padding-block 8dp / padding-inline 0dp (как flat row в снапшоте VK web HTML3).
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Аватар 28dp (как в снапшоте VK web). Фото если есть, иначе буква имени.
            // B-FIX-8: online-индикатор 12dp зелёный (Color 0xFF4BB34B) на BottomEnd
            // аватара если author?.online==1 (как div.WjmGck в снапшоте VK web HTML3).
            Box(modifier = Modifier.size(28.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                if (author?.online == 1) {
                    // VK-STYLING (AUDIT #3/#4): online-dot 8dp БЕЗ border,
                    // offset right:3px bottom:3px, bg #4bb34b (как div.WjmGck в VK web).
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-3).dp, y = (-3).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4BB34B)),
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
                    // #B-FIX-5: дата перенесена в нижнюю meta-row (между «Ответить» и лайком,
                    // как comment-reply/comment-like в снапшоте VK web HTML3).
                    TextButton(
                        onClick = { onUserClick(comment.creatorId) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = authorName,
                            // VK-STYLING (AUDIT #10): bodyMedium ~14sp (было labelMedium 12sp).
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            // B-FIX-7 (AUDIT #54): accent #447bba (comment-owner link).
                            color = BoardAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 3-dot меню (Действия) — Редактировать / Удалить.
                    // #B-FIX-5: «Ответить» вынесен в отдельную TextButton в meta-row
                    // (по снапшоту VK web: comment-reply НЕ в 3-dot меню).
                    // VK-STYLING-SNAPSHOT: comment_action_edit — ОТДЕЛЬНАЯ видимая кнопка
                    // «Редактировать» (pen_outline_16), как в снапшоте VK web HTML3/тест2.
                    // Раньше была спрятана в 3-dot меню (расхождение). Теперь рядом с «Действия».
                    if (comment.canEdit == 1) {
                        IconButton(onClick = { onEdit(comment) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Редактировать",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // comment_action_more — dropdown «Действия» (more_horizontal_16).
                    // В снапшоте VK web: только «Удалить» (canDelete) + «Пожаловаться».
                    // «Редактировать» убрана отсюда — теперь отдельная кнопка выше.
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "Действия",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
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
                            // B-FIX-B (TRACER-2 gap): «Скопировать ссылку» — clipboard.
                            // VK topic URL: vk.com/topic-{groupId}_{topicId}?post={commentId}
                            DropdownMenuItem(
                                text = { Text("Скопировать ссылку") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Link, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    val url = "https://vk.com/topic${-groupId}_${topicId}?post=${comment.id}"
                                    val clipboard = ctx.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE,
                                    ) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Ссылка", url),
                                    )
                                    Toast.makeText(ctx, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                },
                            )
                            // B-FIX-B (TRACER-2 gap): «Пожаловаться» — TODO report API.
                            // VK не имеет board.reportComment; заглушка toast, дальше через
                            // wall.reportComment или общий VK report flow.
                            DropdownMenuItem(
                                text = {
                                    Text("Пожаловаться", color = MaterialTheme.colorScheme.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Report,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(ctx, "Жалоба отправляется...", Toast.LENGTH_SHORT).show()
                                    // TODO: реализовать через wall.reportComment или общий VK report API.
                                },
                            )
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
                // B-FIX-7 (AUDIT #26): парсинг VK mention'ов [id|Имя] / [club|Имя]
                // → кликабельные AnnotatedString spans (accent #447bba). Клик по упоминанию
                // → onUserClick(entityId) (id>0 — юзер, id<0 — сообщество, как VK from_id).
                // LinkAnnotation.Clickable (НЕ deprecated ClickableText) — рендерится Text.
                val annotatedText = buildBoardCommentText(
                    text = displayText,
                    mentionColor = BoardAccent,
                    onUserClick = onUserClick,
                )
                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
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
                // #B-FIX-5: meta-row по снапшоту VK web HTML3 — date + «Ответить»
                // (отдельная TextButton, accent/primary) + spacer + like (БЕЗ счётчика).
                // Порядок как в снапшоте: comment-text → date → comment-reply → comment-like.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // B-FIX-7 (AUDIT #28): «15 фев 2021» без времени для старых,
                        // «в HH:mm» сегодня, «вчера в HH:mm», «d MMM в HH:mm» этот год.
                        text = comment.created.toBoardCommentDate(),
                        // VK-STYLING (AUDIT #15): bodySmall ~13sp (было labelSmall 11sp).
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    TextButton(
                        onClick = { onReply(comment) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "Ответить",
                            // VK-STYLING (AUDIT #13): bodySmall ~13sp (было labelSmall 11sp).
                            style = MaterialTheme.typography.bodySmall,
                            // B-FIX-7 (AUDIT #54): accent #447bba (comment-reply).
                            color = BoardAccent,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Like БЕЗ счётчика (как like_outline_16 в VK web).
                    IconButton(onClick = { onLike(comment) }) {
                        // VK-STYLING (AUDIT #7): like_outline_16 (16dp), tint VK red #e64646 /
                        // secondary #99a2ad (было 24dp + Material pink #E91E63).
                        Icon(
                            imageVector = if (comment.isLiked) Icons.Filled.Favorite
                                          else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Лайк",
                            tint = if (comment.isLiked) Color(0xFFE64646)
                                   else Color(0xFF99A2AD),
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
 * #COMPOSER-REUSE: Composer обсуждений — паттерн заимствован из PostDetailScreen
 * composer (L1040-1180). Переиспользует UnifiedAttachMenu (public компонент) +
 * uploadPhotoForComment / uploadDocForComment → attachments CSV → boardCreateComment.
 *
 * Отличия от PostDetailScreen:
 *  - НЕ использует PendingPhoto/PendingCommentFile (они private в PostDetail) —
 *    простые List<Uri>, temp-файлы создаются на лету при отправке (как PostDetail L1153).
 *  - boardCreateComment принимает attachments напрямую (commit 5857e0a).
 *  - showGift=false, showPoll=false (board не поддерживает: gifts.send — только ЛС,
 *    polls.add — только посты).
 *
 * @param myPhoto URL аватара текущего юзера (photo_100 из users.get); null → fallback Person icon.
 * @param sending флаг отправки (показывает CircularProgressIndicator вместо Send).
 * @param uploading флаг загрузки вложений (показывает CircularProgressIndicator на attach).
 * @param onSentSuccess вызывается после успешной отправки (перезагрузка + page=1 + offset=0).
 */
@Composable
private fun ComposerRow(
    text: String,
    onTextChange: (String) -> Unit,
    sending: Boolean,
    onSendingChange: (Boolean) -> Unit,
    uploading: Boolean,
    onUploadingChange: (Boolean) -> Unit,
    myPhoto: String?,
    showAttachMenu: Boolean,
    onShowAttachMenuChange: (Boolean) -> Unit,
    showEmojiPanel: Boolean,
    onShowEmojiPanelChange: (Boolean) -> Unit,
    pendingPhotoUris: List<Uri>,
    onPendingPhotoUrisChange: (List<Uri>) -> Unit,
    pendingFileUris: List<Uri>,
    onPendingFileUrisChange: (List<Uri>) -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onLaunchFilePicker: () -> Unit,
    groupId: Long,
    topicId: Long,
    onSentSuccess: () -> Unit,
    // B-FIX-B (TRACER-2 gap): гейт Switch «От имени сообщества» (from_group).
    // Передаётся из call-site (bottomBar) как showFromGroup = isManager;
    // если флага прав нет — default false (VK вернёт err=15 если у юзера нет прав).
    showFromGroup: Boolean = false,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // B-FIX-B: from_group (1 = постить от имени сообщества). Гейт showFromGroup.
    var fromGroup by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // PIN-COMPOSER: windowInsetsPadding для bottomBar — IME поднимает composer,
            // navigationBars для жестовой панели. Паритет с PostDetailScreen composer.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        // COMPOSER-REUSE: photos bar — превью выбранных фото (как PostDetailScreen CommentPhotosBar).
        if (pendingPhotoUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pendingPhotoUris.forEachIndexed { idx, uri ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = {
                                onPendingPhotoUrisChange(
                                    pendingPhotoUris.toMutableList().also { it.removeAt(idx) }
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Удалить",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        // COMPOSER-REUSE: files bar — превью выбранных файлов (имя + иконка, клик = удалить).
        if (pendingFileUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingFileUris.forEachIndexed { idx, uri ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onPendingFileUrisChange(
                                    pendingFileUris.toMutableList().also { it.removeAt(idx) }
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = uri.lastPathSegment ?: "Файл",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // COMPOSER-REUSE: emoji panel — упрощённая сетка популярных эмодзи
        // (PostDetailScreen EmojiGridPanel здесь избыточен — board-композер компактнее).
        if (showEmojiPanel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("😀", "😂", "❤️", "👍", "🔥", "🎉", "😍", "😎").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier.clickable { onTextChange(text + emoji) },
                    )
                }
            }
        }
        // B-FIX-B (TRACER-2 gap): Switch «От имени сообщества» (from_group).
        // VK API board.createComment(from_group=1) постит от имени сообщества;
        // гейт showFromGroup (isManager). Отдельный compact row над composer-ом.
        if (showFromGroup) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = fromGroup == 1,
                    onCheckedChange = { fromGroup = if (it) 1 else 0 },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "От имени сообщества",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Composer row (avatar + input + attach + emoji + send), как PostDetailScreen L1040.
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            // VK-STYLING-2 (AUDIT #17, #18): borderless поле — как contenteditable div в
            // VK web (border:0, NO outline). Серый фон surfaceVariant, скругление 8dp.
            val containerColor = MaterialTheme.colorScheme.surfaceVariant
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Написать комментарий…", color = MaterialTheme.colorScheme.outline)
                },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor,
                ),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            // COMPOSER-REUSE: UnifiedAttachMenu (переиспользуемый public компонент, НЕ дублируем).
            Box {
                IconButton(
                    onClick = { onShowAttachMenuChange(true) },
                    enabled = !sending && !uploading,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        // VK-STYLING-2 (AUDIT #19): attach_20 (20dp, НЕ default 24).
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Прикрепить",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                UnifiedAttachMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { onShowAttachMenuChange(false) },
                    onPhoto = { onLaunchPhotoPicker() },
                    onCamera = { /* TODO: camera (TakePicture + FileProvider) */ },
                    onVideo = { /* TODO: video picker (AttachmentPickerSheet) */ },
                    onAudio = { /* TODO: audio picker (audioGet) */ },
                    onFile = { onLaunchFilePicker() },
                    // Подарки/опросы недоступны в board (VK API: gifts.send — только ЛС,
                    // polls.add — только посты).
                    showGift = false,
                    showPoll = false,
                )
            }
            // Emoji toggle (smile_outline_20 в VK web).
            IconButton(onClick = { onShowEmojiPanelChange(!showEmojiPanel) }) {
                // VK-STYLING-2 (AUDIT #19): smile_outline_20 (20dp, НЕ default 24).
                Icon(
                    Icons.Outlined.EmojiEmotions,
                    contentDescription = "Эмодзи",
                    modifier = Modifier.size(20.dp),
                    tint = if (showEmojiPanel) BoardAccent
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Send (send_24 в VK web, только иконка, БЕЗ текста «Сохранить»).
            // disabled до ввода (как VK web: <button disabled data-testid="send-comment">).
            IconButton(
                onClick = {
                    val textToSend = text.trim()
                    val hasContent = textToSend.isNotBlank() ||
                        pendingPhotoUris.isNotEmpty() || pendingFileUris.isNotEmpty()
                    if (!hasContent || sending || uploading) return@IconButton
                    // Снапшотим списки ДО очистки (оптимистичный UX, как PostDetailScreen).
                    val photosCopy = pendingPhotoUris.toList()
                    val filesCopy = pendingFileUris.toList()
                    onTextChange("")
                    onPendingPhotoUrisChange(emptyList())
                    onPendingFileUrisChange(emptyList())
                    onShowEmojiPanelChange(false)
                    onSendingChange(true)
                    scope.launch {
                        try {
                            // Upload photos → "photo{ownerId}_{id}" (uploadPhotoForComment,
                            // wall-photo pipeline: getWallUploadServer → upload → saveWallPhoto).
                            val photoAttachments = mutableListOf<String>()
                            if (photosCopy.isNotEmpty()) {
                                onUploadingChange(true)
                                for (uri in photosCopy) {
                                    val inFile = kotlin.io.path.createTempFile(
                                        prefix = "board_photo_",
                                        suffix = ".jpg",
                                        directory = ctx.cacheDir.toPath(),
                                    ).toFile()
                                    try {
                                        ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                            inFile.outputStream().use { out -> ins.copyTo(out) }
                                        } ?: continue
                                        val att = app.apiClient.uploadPhotoForComment(inFile, "image/*")
                                        if (att != null) photoAttachments += att
                                    } finally {
                                        inFile.delete()
                                    }
                                }
                                onUploadingChange(false)
                            }
                            // Upload files → "doc{ownerId}_{id}" (uploadDocForComment,
                            // docs.getWallUploadServer → upload).
                            val fileAttachments = mutableListOf<String>()
                            if (filesCopy.isNotEmpty()) {
                                onUploadingChange(true)
                                for (uri in filesCopy) {
                                    val inFile = kotlin.io.path.createTempFile(
                                        prefix = "board_doc_",
                                        suffix = ".bin",
                                        directory = ctx.cacheDir.toPath(),
                                    ).toFile()
                                    try {
                                        ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                            inFile.outputStream().use { out -> ins.copyTo(out) }
                                        } ?: continue
                                        val att = app.apiClient.uploadDocForComment(inFile)
                                        if (att != null) fileAttachments += att
                                    } finally {
                                        inFile.delete()
                                    }
                                }
                                onUploadingChange(false)
                            }
                            // CSV: photo{ownerId}_{id},doc{ownerId}_{id},…
                            val allAttachments = (photoAttachments + fileAttachments)
                                .joinToString(",")
                                .ifBlank { null }
                            // boardCreateComment: ownerId для группы = -groupId (метод берёт abs).
                            // B-FIX-B: from_group (0=от себя, 1=от сообщества) — Switch выше.
                            // Параметр fromGroup в VKApiClient.boardCreateComment добавлен
                            // агентом B-FIX-A (TRACER-2 gap, паритет с VK web from_group).
                            val ok = app.apiClient.boardCreateComment(
                                ownerId = -groupId,
                                topicId = topicId,
                                message = textToSend,
                                attachments = allAttachments,
                                fromGroup = fromGroup,
                            )
                            if (ok) {
                                onSentSuccess()
                                Toast.makeText(ctx, "Отправлено", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    ctx,
                                    "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            onSendingChange(false)
                        }
                    }
                },
                enabled = (text.isNotBlank() || pendingPhotoUris.isNotEmpty() ||
                    pendingFileUris.isNotEmpty()) && !sending && !uploading,
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Отправить",
                        // VK-STYLING-2 (AUDIT #21, #22): send-comment — accent #447bba
                        // (НЕ primary #0077FF), disabled — icon_tertiary #aeb7c2.
                        tint = if (text.isNotBlank() || pendingPhotoUris.isNotEmpty() ||
                                       pendingFileUris.isNotEmpty())
                               BoardAccent else Color(0xFFAEB7C2),
                    )
                }
            }
        }
    }
}
