// File: screens/feed/PostDetailScreen.kt
package re.pinok.ui.screens.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.Comment
import re.pinok.data.model.Post
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.PlayerConnection
import re.pinok.ui.navigation.PostHolder
import re.pinok.ui.navigation.PostDetailScrollHolder
import re.pinok.ui.navigation.PostDetailTarget
import re.pinok.ui.navigation.ScrollPosition
import re.pinok.ui.navigation.StoriesHolder
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.util.AppLog
import re.pinok.ui.components.PendingPhotosBar
import re.pinok.ui.components.PendingPhoto
import re.pinok.ui.components.nextPendingPhotoId
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fix #71: Экран детального просмотра поста.
 *
 * Экран показывает:
 *  — Полный текст поста (без обрезки)
 *  — Все вложения (фото/видео/ссылки)
 *  — Action bar (лайк/комментарий/репост/просмотры)
 *  — Полный список комментариев (с пагинацией)
 *  — Поле ввода нового комментария
 *
 * Пост передаётся через in-memory holder [PostHolder] (как VideoHolder).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostDetailScreen(
    ownerId: Long,
    postId: Long,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // Fix #233 (Q&A Bug B): post может быть «stub» (text="", нет attachments)
    // когда переход из Ответов/Уведомлений — SovaNavHost создаёт минимальный
    // Post(ownerId, id, text="") и кладёт в PostHolder. holderPost — то что в
    // holder'е; fetchedPost — дозагруженный через wallGetById. val post —
    // computed (fetchedPost ?: holderPost), smart-cast работает после null-check.
    val holderPost = PostHolder.last?.takeIf { it.ownerId == ownerId && it.id == postId }
    var fetchedPost by remember { mutableStateOf<re.pinok.data.model.Post?>(null) }
    val post: re.pinok.data.model.Post? = fetchedPost ?: holderPost
    // Fix #99: берём группы из holder'а для отображения имени сообщества.
    var groups by remember { mutableStateOf(PostHolder.lastGroups ?: emptyMap()) }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var profiles by remember { mutableStateOf(emptyMap<Long, UserProfile>()) }
    var loadingComments by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var localComments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var isLiked by remember { mutableStateOf(post?.likes?.userLikes == 1) }
    var likeCount by remember { mutableStateOf(post?.likes?.count ?: 0) }
    // §37.12 #328: развёртывание веток ответов в комментариях.
    // expandedReplies — множество ID комментариев, чья ветка развёрнута.
    // threadReplies — дополнительно загруженные ответы (когда preview из
    // comment.thread.items не покрывает весь thread.count). Ключ — comment.id.
    var expandedReplies by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var threadReplies by remember { mutableStateOf<Map<Long, List<Comment>>>(emptyMap()) }
    var loadingThreadFor by remember { mutableStateOf<Long?>(null) }
    // Вложения в комментариях.
    var uploading by remember { mutableStateOf(false) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachmentString by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    // Fix #209: reply на комментарий (threaded replies через wall.createComment
    // с reply_to_comment). replyingToComment — комментарий, на который отвечаем.
    var replyingToComment by remember { mutableStateOf<Comment?>(null) }
    // Fix #234 (multi-photo preview): фото в комментарии. Юзер выбирает одно
    // или несколько фото → миниатюры в PendingPhotosBar над полем ввода →
    // Send отправляет batch через uploadPhotoForComment для каждого.
    // Fix #235: обёрнуты в PendingPhoto(id, uri) — уникальный id для ключа LazyRow.
    var pendingCommentPhotos by remember { mutableStateOf<List<PendingPhoto>>(emptyList()) }
    var previewPhotoIndex by remember { mutableStateOf<Int?>(null) }
    // Fix #237 (multi-file в комментариях): список выбранных файлов (до 10),
    // как в чате. Каждый файл — PendingCommentFile(id, file, name, size, mime).
    var pendingCommentFiles by remember { mutableStateOf<List<PendingCommentFile>>(emptyList()) }
    // Fix #237 (emoji-панель): вставка эмодзи в текст комментария.
    var showEmojiPanel by remember { mutableStateOf(false) }
    // Расширенный пикер (Музыка/Видео) — общий с чатом компонент AttachmentPickerSheet.
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableStateOf(0) } // 0=Музыка, 1=Видео
    val ctx = LocalContext.current

    // Лаунчеры для вложений в комментариях.
    // Fix #234 (multi-photo preview): single photo picker → добавляем в
    // pendingCommentPhotos для предпросмотра (не отправляем сразу).
    val commentPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pendingCommentPhotos = (pendingCommentPhotos + PendingPhoto(nextPendingPhotoId(), uri)).take(10)
    }
    // Fix #234: multi-photo picker — до 10 фото за раз для комментария.
    val commentMultiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val combined = (pendingCommentPhotos + uris.map { PendingPhoto(nextPendingPhotoId(), it) }).take(10)
        pendingCommentPhotos = combined
    }
    // Fix #237 (multi-file): выбор НЕСКОЛЬКИХ файлов за раз (до 10), как в чате.
    // Каждый URI копируется в temp-файл, оборачивается в PendingCommentFile и
    // добавляется в pendingCommentFiles. Загрузка на сервер — в doSend (batch).
    // Раньше был одиночный OpenDocument + немедленная загрузка одного файла в
    // attachmentString — нельзя было прикрепить 2+ файла к комментарию.
    val commentMultiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newFiles = mutableListOf<PendingCommentFile>()
        for (uri in uris) {
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: run {
                    AppLog.e("PostDetail", "comment filePicker: cannot open input stream for $uri")
                    continue
                }
                var nameFromResolver: String? = null
                try {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            nameFromResolver = cursor.getString(nameIdx)
                        }
                    }
                } catch (_: Exception) { }
                val mimeFromResolver = ctx.contentResolver.getType(uri)
                val rawName = nameFromResolver
                    ?: uri.lastPathSegment?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: "file"
                val displayName = rawName.replace(Regex("[^a-zA-Z0-9._\\- а-яА-ЯёЁ()\\[\\]]"), "_")
                    .ifBlank { "file_${System.currentTimeMillis()}" }
                val isImage = mimeFromResolver?.startsWith("image/") == true
                val safeTempName = "cmt_${System.currentTimeMillis()}_${newFiles.size}_$displayName"
                val tempFile = File(ctx.cacheDir, safeTempName)
                tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                newFiles += PendingCommentFile(
                    id = nextPendingCommentFileId(),
                    file = tempFile,
                    displayName = displayName,
                    sizeBytes = tempFile.length(),
                    mime = mimeFromResolver,
                    isImage = isImage,
                )
            } catch (e: Exception) {
                AppLog.e("PostDetail", "comment filePicker: copy uri→file error for $uri", e)
            }
        }
        if (newFiles.isNotEmpty()) {
            // VK wall.createComment принимает до 10 attachments. Лимит — 10 суммарно.
            pendingCommentFiles = (pendingCommentFiles + newFiles).take(10)
            AppLog.i("PostDetail", "comment filePicker: added ${newFiles.size} files, total=${pendingCommentFiles.size}")
        }
    }
    // Pull-to-refresh + пагинация комментариев.
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    // #POST-DETAIL-SCROLL: rememberSaveable + LazyListState.Saver — позиция
    // скролла переживает навигацию к Community и возврат. По образцу FeedScreen
    // (Fix #51-B). Дополнительно PostDetailScrollHolder — backup-механизм
    // (singleton, переживает пересоздание composition).
    var postDetailReloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberSaveable(postDetailReloadKey, saver = LazyListState.Saver) {
        LazyListState()
    }
    var scrollRestored by remember { mutableStateOf(false) }

    // #POST-DETAIL-SCROLL: сохранение позиции ПЕРЕД уходом с экрана.
    fun saveScrollPosition() {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        PostDetailScrollHolder.position = if (first != null) {
            ScrollPosition(first.index, first.offset)
        } else {
            ScrollPosition(0, 0)
        }
    }
    val onVideoClickSavePos: (Video) -> Unit = { v -> saveScrollPosition(); onVideoClick(v) }
    val onGroupClickSavePos: (Long) -> Unit = { g -> saveScrollPosition(); onGroupClick(g) }
    // Полноэкранный просмотр фото из поста.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Диалог репоста.
    var showRepostDialog by remember { mutableStateOf(false) }

    // Fix #233 (Q&A Bug B): дозагрузка поста + комментарии в одном эффекте.
    // Если post — stub (text пустой и нет attachments) ИЛИ null — сначала
    // wallGetById подтягивает полный пост + группы, затем грузим комментарии.
    // Ранее stub post (из Ответов/Уведомлений) показывался пустым: нет хедера,
    // нет тела, нет лайков — только список комментариев под ним.
    LaunchedEffect(ownerId, postId) {
        val current = post
        val isStub = current == null ||
            (current.text.isBlank() && current.attachments.isNullOrEmpty())
        var p = current
        if (isStub) {
            try {
                val result = app.apiClient.wallGetById(listOf(ownerId to postId))
                val fetched = result.posts.firstOrNull { it.ownerId == ownerId && it.id == postId }
                if (fetched != null) {
                    fetchedPost = fetched
                    PostHolder.last = fetched
                    if (result.groups.isNotEmpty()) {
                        groups = result.groups
                        PostHolder.lastGroups = result.groups
                    }
                    isLiked = fetched.likes?.userLikes == 1
                    likeCount = fetched.likes?.count ?: 0
                    p = fetched
                }
            } catch (e: Exception) {
                AppLog.e("PostDetail", "fetch post via wallGetById error", e)
            }
        }
        if (p == null) return@LaunchedEffect
        scope.launch {
            loadingComments = true
            try {
                val result = app.apiClient.wallGetComments(p.ownerId, p.id, count = 50)
                comments = result.comments
                profiles = result.profiles
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Fix #252: корректная отмена (пользователь ушёл со экрана)
                throw e
            } catch (e: Exception) {
                AppLog.e("PostDetail", "load comments error", e)
            } finally {
                loadingComments = false
            }
        }
    }

    // §42.4 #PUSH-DEEPLINK: скролл к целевому комментарию после загрузки.
    //
    // Сценарий: пользователь тапнул по push-уведомлению «ответ на комментарий»
    // или «новый комментарий на посте». SovaNavHost перед nav.navigate(PostDetail)
    // положил commentId в PostDetailTarget. Здесь мы:
    //   1. Ждём пока comments загрузятся (loadingComments=false && comments не пуст).
    //   2. Ищем commentId среди top-level комментариев (allComments).
    //   3. Если не нашли — ищем в thread.items каждого top-level комментария
    //      (ответ на ответ) → разворачиваем ветку (expandedReplies) и скроллим
    //      к родительскому top-level комментарию.
    //   4. Сбрасываем PostDetailTarget.commentId (одноразовое действие).
    //
    // Индекс в LazyColumn = 2 (пост-хедер + «Комментарии» заголовок) + индекс
    // в allComments. См. структуру LazyColumn выше (item пост, item заголовок,
    // items(comments)).
    LaunchedEffect(comments, localComments, loadingComments) {
        val targetCommentId = PostDetailTarget.commentId ?: return@LaunchedEffect
        if (targetCommentId == 0L) return@LaunchedEffect
        // Ждём загрузки комментариев.
        if (loadingComments && comments.isEmpty() && localComments.isEmpty()) return@LaunchedEffect

        // Та же дедупликация что и в LazyColumn (см. allComments выше).
        val seenIds = HashSet<Long>()
        val allComments = (localComments + comments).filter { seenIds.add(it.id) }

        // 1. Ищем среди top-level.
        val topLevelIdx = allComments.indexOfFirst { it.id == targetCommentId }
        if (topLevelIdx >= 0) {
            // 2 (пост + заголовок «Комментарии») + индекс комментария.
            val lazyIdx = 2 + topLevelIdx
            AppLog.i("PostDetail", "DEEPLINK scroll to comment $targetCommentId (top-level idx=$topLevelIdx, lazyIdx=$lazyIdx)")
            listState.animateScrollToItem(lazyIdx)
            PostDetailTarget.commentId = null
            return@LaunchedEffect
        }

        // 2. Ищем в thread.items (ответ на ответ) — разворачиваем ветку.
        for (top in allComments) {
            val thread = top.thread?.items ?: continue
            if (thread.any { it.id == targetCommentId }) {
                // Разворачиваем ветку родителя, чтобы ответ стал виден.
                if (top.id !in expandedReplies) {
                    expandedReplies = expandedReplies + top.id
                }
                val parentIdx = allComments.indexOf(top)
                val lazyIdx = 2 + parentIdx
                AppLog.i("PostDetail", "DEEPLINK scroll to thread reply $targetCommentId (parent top-level idx=$parentIdx, lazyIdx=$lazyIdx)")
                // Небольшая задержка чтобы развёрнутая ветка отрисовалась.
                kotlinx.coroutines.delay(150)
                listState.animateScrollToItem(lazyIdx)
                PostDetailTarget.commentId = null
                return@LaunchedEffect
            }
        }

        // 3. Не нашли (комментарий за пределами первой страницы или удалён) —
        // оставляем target чтобы пагинация могла подтянуть. Логируем для диагностики.
        AppLog.w("PostDetail", "DEEPLINK: target comment $targetCommentId not found in ${allComments.size} comments (maybe paginated out)")
    }

    // Пагинация: подгружаем ещё при скролле вниз.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@snapshotFlow false
            val lastVisible = layoutInfo.visibleItemsInfo.last().index
            val totalItems = layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (post == null || endReached || loadingMore) return@collect
                scope.launch {
                    loadingMore = true
                    try {
                        // Fix #233 (P1-7): pagination offset = только comments.size,
                        // БЕЗ localComments.size. localComments — это optimistic
                        // комментарии, которые уже отображены, но НЕ учтены на
                        // сервере. Сервер возвращает комментарии по offset от
                        // начала списка — localComments не смещают server offset.
                        // Раньше offset = comments.size + localComments.size →
                        // пропускали N реальных комментариев после optimistic send.
                        val offset = comments.size
                        val result = app.apiClient.wallGetComments(
                            post.ownerId, post.id,
                            count = 30, offset = offset,
                        )
                        if (result.comments.isEmpty()) {
                            endReached = true
                        } else {
                            comments = comments + result.comments
                            profiles += result.profiles
                            // Fix #233 (P1-7): если сервер вернул комментарии,
                            // которые уже есть в localComments (по server ID после
                            // замены optimisticId → serverCommentId) — удаляем
                            // их из localComments чтобы избежать дублирования.
                            val serverIds = result.comments.map { it.id }.toHashSet()
                            if (localComments.any { it.id in serverIds }) {
                                localComments = localComments.filterNot { it.id in serverIds }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Fix #252: корректная отмена (пользователь ушёл со экрана)
                        throw e
                    } catch (e: Exception) {
                        AppLog.e("PostDetail", "load more comments error", e)
                    } finally {
                        loadingMore = false
                    }
                }
            }
    }

    // #POST-DETAIL-SCROLL: continuous save — сохраняем позицию в holder при
    // каждом изменении скролла (после восстановления). По образцу FeedScreen.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            if (first != null) first.index to first.offset else null
        }
            .distinctUntilChanged()
            .collect { pair ->
                if (pair != null && scrollRestored) {
                    PostDetailScrollHolder.position = ScrollPosition(pair.first, pair.second)
                }
            }
    }

    // #POST-DETAIL-SCROLL: restore — при возврате на PostDetail (trigger =
    // StoriesHolder.dirtyKey, SovaNavHost вызывает markDirty на возврате).
    val restoreKey by StoriesHolder.dirtyKey.collectAsState()
    LaunchedEffect(restoreKey) {
        if (PostDetailScrollHolder.position.index > 0) {
            scrollRestored = false
        }
    }
    LaunchedEffect(postDetailReloadKey, comments.isNotEmpty() || post != null, restoreKey) {
        if (PostDetailScrollHolder.position.index > 0) {
            // #FEED-SCROLL-OFFSET-SIGN: visibleItemsInfo.offset и scrollToItem
            // scrollOffset имеют противоположные знаки — negate при restore.
            // См. подробный комментарий в FeedScreen.kt.
            listState.scrollToItem(
                PostDetailScrollHolder.position.index,
                -PostDetailScrollHolder.position.offset,
            )
            AppLog.d("PostDetail", "Scroll restored to index=${PostDetailScrollHolder.position.index} " +
                "offset=${PostDetailScrollHolder.position.offset} (restoreKey=$restoreKey)")
        }
        scrollRestored = true
    }

    // Лайки на постах.
    fun toggleLike() {
        if (post == null) return
        scope.launch {
            try {
                if (isLiked) {
                    app.apiClient.likesDelete(
                        type = "post",
                        ownerId = post.ownerId,
                        itemId = post.id,
                    )
                    isLiked = false
                    likeCount = maxOf(0, likeCount - 1)
                } else {
                    app.apiClient.likesAdd(
                        type = "post",
                        ownerId = post.ownerId,
                        itemId = post.id,
                    )
                    isLiked = true
                    likeCount += 1
                }
            } catch (e: Exception) {
                AppLog.e("PostDetail", "toggle like error", e)
            }
        }
    }

    // Pull-to-refresh.
    fun doRefresh() {
        if (post == null) return
        scope.launch {
            isRefreshing = true
            try {
                val result = app.apiClient.wallGetComments(post.ownerId, post.id, count = 50)
                comments = result.comments
                profiles = result.profiles
            } catch (e: Exception) {
                AppLog.e("PostDetail", "refresh error", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    if (post == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пост не найден")
        }
        return
    }

    // Полноэкранный просмотр фото.
    val state = photoViewerState.value
    if (state != null) {
        val (photos, startIndex) = state
        PhotoViewer(
            photos = photos,
            initial = startIndex,
            onDismiss = { photoViewerState.value = null },
        )
        return
    }

    // ShareSheet: расширенный диалог «Поделиться».
    if (showRepostDialog) {
        ShareSheet(
            post = post,
            onDismiss = { showRepostDialog = false },
            onSuccess = {
                showRepostDialog = false
                doRefresh()
            },
        )
        return
    }

    // Определяем фото автора поста из профилей/групп.
    val authorAvatarUrl: String? = if (post.fromId > 0) {
        profiles[post.fromId]?.photo100
    } else {
        groups[-post.fromId]?.photo100
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пост") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
        content = { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = ::doRefresh,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = listState,
                ) {
                    // --- Пост ---
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            // Автор поста.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    // #POST-DETAIL-SCROLL: тап по header'у автора →
                                    // экран сообщества (для fromId < 0). По образцу
                                    // FeedScreen Fix #67. saveScrollPosition() перед
                                    // уходом — позиция комментариев восстановится при
                                    // возврате. Для fromId > 0 (пользователь) клика
                                    // нет — PostDetailScreen не имеет onUserClick.
                                    .then(
                                        if (post.fromId < 0) Modifier.clickable {
                                            onGroupClickSavePos(-post.fromId)
                                        } else Modifier
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = authorAvatarUrl,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentDescription = "Аватар",
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                val authorName = if (post.fromId > 0) {
                                    val p = profiles[post.fromId]
                                    p?.fullName ?: ""
                                } else {
                                    // Fix #99: показываем имя сообщества.
                                    val g = groups[-post.fromId]
                                    g?.name ?: "Сообщество"
                                }
                                val nameColor = if (post.fromId < 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                                Text(
                                    text = authorName,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = nameColor,
                                )
                            }

                            // Текст поста.
                            if (post.text.isNotBlank()) {
                                // Fix #204: парсим VK inline-ссылки [#alias|display|url] + обычные URL.
                                Text(
                                    text = re.pinok.util.linkifyVkText(
                                        text = post.text,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        onUrlClick = { url -> re.pinok.util.openUrlExternal(ctx, url) },
                                    ),
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }

                            // Вложения.
                            val attachments = post.attachments ?: emptyList()
                            for (attachment in attachments) {
                                when (attachment.type) {
                                    "photo" -> {
                                        val photoSizes = attachment.photo?.sizes
                                        val photoUrl = PhotoSizes.bestUrl(photoSizes)
                                        if (photoUrl != null) {
                                            AsyncImage(
                                                model = photoUrl,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        val allPhotos = attachments
                                                            .filter { it.type == "photo" && it.photo?.sizes != null }
                                                            .mapNotNull { PhotoSizes.bestUrl(it.photo?.sizes) }
                                                        val idx = allPhotos.indexOf(photoUrl)
                                                        photoViewerState.value = allPhotos to maxOf(0, idx)
                                                    },
                                                contentDescription = "Фото",
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                    }
                                    "video" -> {
                                        val video = attachment.video
                                        if (video != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black)
                                                    .clickable { onVideoClickSavePos(video) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Outlined.PlayArrow,
                                                    contentDescription = "Видео",
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    }
                                    "audio" -> {
                                        val track = attachment.audio
                                        if (track != null) {
                                            PostDetailAudioRow(track = track)
                                        }
                                    }
                                    // #30 (playlists): audio_playlist вложение.
                                    "audio_playlist" -> {
                                        val playlist = attachment.audioPlaylist
                                        if (playlist != null) {
                                            re.pinok.ui.components.PlaylistAttachmentCard(playlist = playlist)
                                        }
                                    }
                                    else -> {}
                                }
                            }

                            // Action bar (лайк/коммент/репост/просмотры).
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // Лайк.
                                IconButton(onClick = ::toggleLike) {
                                    Icon(
                                        if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Лайк",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text("$likeCount")

                                // Комментарий.
                                IconButton(onClick = { /* scroll to comment input */ }) {
                                    Icon(
                                        Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Комментарий",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text("${post.comments?.count ?: 0}")

                                // Репост.
                                IconButton(onClick = { showRepostDialog = true }) {
                                    Icon(
                                        Icons.Outlined.Repeat,
                                        contentDescription = "Репост",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text("${post.reposts?.count ?: 0}")

                                // Просмотры.
                                Row(
                                    modifier = Modifier.padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Visibility,
                                        contentDescription = "Просмотры",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text("${post.views?.count ?: 0}")
                                }
                            }
                        }
                    }

                    // --- Комментарии ---
                    item {
                        Text(
                            text = "Комментарии (${post.comments?.count ?: 0})",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    // Список комментариев.
                    if (comments.isEmpty() && localComments.isEmpty() && !loadingComments) {
                        item {
                            Text(
                                "Пост ещё не прокомментирован.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Fix #231: дедупликация по ID. wall.getComments может вернуть
                    // один и тот же комментарий дважды (thread-ответ дублирует
                    // родителя, или пагинация вернула пересекающиеся окна).
                    // localComments (оптимистично добавленные) тоже могут совпасть
                    // с freshly-fetched comments после reload. Без дедупликации
                    // LazyColumn крашит: "Key N was already used".
                    // NB: plain Kotlin (без remember) — мы внутри LazyListScope,
                    // это не @Composable-контекст, remember тут нельзя.
                    val seenIds = HashSet<Long>()
                    val allComments = (localComments + comments).filter { seenIds.add(it.id) }
                    // Fix #237: индекс по id — чтобы в CommentItem показать
                    // цитату-родителя (кого комментируют), а не просто «→ Имя».
                    val commentsById: Map<Long, Comment> = allComments.associateBy { it.id }

                    items(allComments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            profiles = profiles,
                            // Fix #237: родительский комментарий (на который ответили),
                            // если он есть в загруженной выборке. null — VK не вернул
                            // родителя (например, он за пределами страницы) → покажем
                            // только имя автора.
                            parentComment = comment.replyToComment?.let { commentsById[it] },
                            // Fix #209: кнопка «Ответить» внутри комментария.
                            onReply = {
                                replyingToComment = comment
                                // Фокус на поле ввода — пользователь сразу печатает ответ.
                            },
                            // Fix #237: клик по видео/фото в комментарии открывает
                            // просмотрщик. Переиспользуем photoViewerState поста.
                            // #POST-DETAIL-SCROLL: сохраняем позицию перед уходом.
                            onVideoClick = onVideoClickSavePos,
                            onPhotoClick = { urls, idx ->
                                photoViewerState.value = urls to idx
                            },
                            // Fix #237: ownerId стены — для likes.add/delete type=comment.
                            postOwnerId = post.ownerId,
                            // §37.12 #328: развёртывание ветки ответов.
                            isExpanded = comment.id in expandedReplies,
                            isLoadingReplies = loadingThreadFor == comment.id,
                            extraReplies = threadReplies[comment.id] ?: emptyList(),
                            onExpandThread = {
                                if (comment.id in expandedReplies) {
                                    // Сворачиваем.
                                    expandedReplies = expandedReplies - comment.id
                                } else {
                                    // Разворачиваем.
                                    expandedReplies = expandedReplies + comment.id
                                    // Если preview из thread.items не покрывает весь count —
                                    // догружаем через wall.getComments(comment_id=...).
                                    val previewCount = comment.thread?.items?.size ?: 0
                                    val totalCount = comment.thread?.count ?: 0
                                    if (previewCount < totalCount) {
                                        loadingThreadFor = comment.id
                                        scope.launch {
                                            try {
                                                val r = app.apiClient.wallGetComments(
                                                    ownerId = post.ownerId,
                                                    postId = post.id,
                                                    count = 100,
                                                    commentId = comment.id,
                                                )
                                                // Мержим профили авторов ответов.
                                                profiles = profiles + r.profiles
                                                threadReplies = threadReplies + (comment.id to r.comments)
                                            } catch (e: Exception) {
                                                AppLog.e("PostDetailScreen", "fetch thread replies error", e)
                                            } finally {
                                                loadingThreadFor = null
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }

                    // Индикатор загрузки.
                    if (loadingComments || loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // Небольшой отступ перед полем ввода (bottomBar).
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Превью прикреплённого файла.
                if (attachedFileName != null) {
                    val fileName = attachedFileName
                    if (fileName != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AttachFile, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = fileName, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { attachedFileName = null; attachmentString = null },
                                modifier = Modifier.size(20.dp)) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
                // Fix #209: превью комментария, на который отвечаем (reply-режим).
                // Показывает имя автора + первые 60 символов текста + кнопку ✕ для отмены.
                val replyTarget = replyingToComment
                if (replyTarget != null) {
                    val replyAuthor = profiles[replyTarget.fromId]
                    val replyAuthorName = replyAuthor?.fullName ?: "Неизвестный"
                    val replyAuthorAvatar = replyAuthor?.photo100
                    val replyPreviewText = replyTarget.text.take(60).ifBlank { "(вложение)" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Fix #237: аватар автора комментария, на который отвечаем —
                        // нагляднее, чем просто иконка Reply. Пользователь сразу видит
                        // ЧЕЙ это комментарий.
                        AsyncImage(
                            model = replyAuthorAvatar,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ответ $replyAuthorName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = replyPreviewText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { replyingToComment = null },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Text(
                                "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            // Fix #237: бары вложений (фото/файлы) и emoji-панель — ВЫШЕ поля
            // ввода, как в чате. Раньше PendingPhotosBar был внутри Row с
            // fillMaxWidth() → OutlinedTextField получал 0 ширины при выборе фото.
            if (pendingCommentPhotos.isNotEmpty()) {
                PendingPhotosBar(
                    photos = pendingCommentPhotos,
                    onRemove = { idx ->
                        pendingCommentPhotos = pendingCommentPhotos.toMutableList().also { it.removeAt(idx) }
                    },
                    onPreview = { idx -> previewPhotoIndex = idx },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Fix #237 (multi-file): бар выбранных файлов (до 10).
            if (pendingCommentFiles.isNotEmpty()) {
                CommentFilesBar(
                    files = pendingCommentFiles,
                    onRemove = { idx ->
                        pendingCommentFiles = pendingCommentFiles.toMutableList().also { it.removeAt(idx) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Fix #237 (emoji): панель эмодзи над полем ввода.
            if (showEmojiPanel) {
                EmojiGridPanel(
                    onEmojiClick = { emoji -> inputText += emoji },
                    onBackspace = {
                        if (inputText.isNotEmpty()) {
                            // Удаляем последний code point (корректно для эмодзи-суррогатов).
                            inputText = inputText.dropLast(
                                if (inputText.last().isHighSurrogate() && inputText.length >= 2) 2 else 1
                            )
                        }
                    },
                    onDismiss = { showEmojiPanel = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Поле ввода комментария.
            // #COMMENT-IME-FIX: добавлены .windowInsetsPadding(navigationBars) + .imePadding()
            // чтобы клавиатура не перекрывала поле ввода. Тот же паттерн что в
            // ChatDetailScreen.kt:2499-2506. Без этого при enableEdgeToEdge()
            // система не сдвигает контент вверх → клавиатура закрывает текстовое поле.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Кнопка вложения.
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !sending && !uploading,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AttachFile, contentDescription = "Прикрепить")
                        }
                    }
                    // Единое меню «Прикрепить» — тот же компонент, что в чате
                    // и при создании поста. Подарки недоступны в комментариях
                    // (gifts.send работает только для личных сообщений).
                    UnifiedAttachMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        onPhoto = {
                            // Fix #234 (multi-photo preview): мульти-выбор до 10 фото.
                            commentMultiPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onVideo = {
                            attachmentPickerTab = 1
                            showAttachmentPicker = true
                        },
                        onAudio = {
                            attachmentPickerTab = 0
                            showAttachmentPicker = true
                        },
                        onFile = {
                            commentMultiFileLauncher.launch(arrayOf("*/*"))
                        },
                        // Подарки в комментариях не поддерживаются VK API.
                        showGift = false,
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Комментарий...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                // Fix #237 (emoji): кнопка переключения emoji-панели.
                IconButton(onClick = { showEmojiPanel = !showEmojiPanel }) {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = "Эмодзи",
                        tint = if (showEmojiPanel) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val textToSend = inputText.trim()
                    val photosToSend = pendingCommentPhotos
                    val filesToSend = pendingCommentFiles
                    val hasContent = textToSend.isNotBlank() || attachedFileName != null ||
                        photosToSend.isNotEmpty() || filesToSend.isNotEmpty()
                    if (!hasContent || sending || uploading) return@IconButton
                    // Фиксируем attachmentToSend ДО очистки attachmentString.
                    val attachmentToSend = attachmentString
                    val replyToCommentId = replyingToComment?.id
                    val replyToUserId = replyingToComment?.fromId
                    // Прячем UI сразу — оптимистичный UX.
                    inputText = ""
                    attachedFileName = null
                    attachmentString = null
                    replyingToComment = null
                    pendingCommentPhotos = emptyList()
                    pendingCommentFiles = emptyList()
                    showEmojiPanel = false
                    scope.launch {
                        sending = true
                        try {
                            // Fix #234 (multi-photo preview): загружаем все выбранные
                            // фото через wall-photo pipeline (photos.getWallUploadServer
                            // → upload → saveWallPhoto). Каждое фото → "photo{ownerId}_{id}"
                            // строка, собираем через запятую.
                            val photoAttachments = mutableListOf<String>()
                            if (photosToSend.isNotEmpty()) {
                                uploading = true
                                for (p in photosToSend) {
                                    val uri = p.uri
                                    val inFile = kotlin.io.path.createTempFile(
                                        prefix = "pdc_photo_",
                                        suffix = ".jpg",
                                        directory = ctx.cacheDir.toPath(),
                                    ).toFile()
                                    try {
                                        ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                            inFile.outputStream().use { out -> ins.copyTo(out) }
                                        } ?: continue
                                        val att = app.apiClient.uploadPhotoForComment(inFile, "image/*")
                                        if (att != null) {
                                            photoAttachments += att
                                            AppLog.i("PostDetail", "comment photo uploaded: $att")
                                        }
                                    } finally {
                                        inFile.delete()
                                    }
                                }
                                uploading = false
                            }
                            // Fix #237 (multi-file): загружаем все выбранные файлы
                            // через docs.upload (uploadDocForComment). Каждый файл →
                            // "doc{ownerId}_{id}" строка. temp-файлы удаляются после.
                            val fileAttachments = mutableListOf<String>()
                            if (filesToSend.isNotEmpty()) {
                                uploading = true
                                for (pf in filesToSend) {
                                    try {
                                        val att = app.apiClient.uploadDocForComment(pf.file)
                                        if (att != null) {
                                            fileAttachments += att
                                            AppLog.i("PostDetail", "comment file uploaded: $att")
                                        } else {
                                            AppLog.w("PostDetail", "comment file upload returned null: ${pf.displayName}")
                                        }
                                    } catch (fe: Exception) {
                                        AppLog.e("PostDetail", "comment file upload error: ${pf.displayName}", fe)
                                    } finally {
                                        pf.file.delete()
                                    }
                                }
                                uploading = false
                            }
                            // Совмещаем photo + file attachments с attachmentString
                            // (аудио/видео, выбранное через AttachmentPickerSheet).
                            val allAttachments = listOfNotNull(attachmentToSend) +
                                photoAttachments + fileAttachments
                            val attachmentsStr = allAttachments
                                .filter { it.isNotBlank() }
                                .joinToString(",")
                                .ifBlank { null }
                            // Fix #233 (P1-7): используем server-returned comment_id.
                            val currentUserId = app.tokenStorage.load()?.userId ?: 0L
                            val optimisticId = -System.currentTimeMillis()
                            localComments = localComments + Comment(
                                id = optimisticId,
                                fromId = currentUserId,
                                date = System.currentTimeMillis() / 1000,
                                text = when {
                                    textToSend.isNotBlank() -> textToSend
                                    photosToSend.isNotEmpty() -> "📷 ${photosToSend.size} фото"
                                    filesToSend.isNotEmpty() -> "📎 ${filesToSend.size} файл(ов)"
                                    else -> "📎 вложение"
                                },
                                // Fix #209: показываем reply-контекст в оптимистичном комментарии.
                                replyToUser = replyToUserId,
                                replyToComment = replyToCommentId,
                            )
                            val serverCommentId = app.apiClient.wallCreateComment(
                                ownerId = post.ownerId,
                                postId = post.id,
                                message = textToSend,
                                attachments = attachmentsStr,
                                replyToComment = replyToCommentId,
                            )
                            if (serverCommentId > 0L) {
                                // Заменяем optimistic ID на server ID — теперь
                                // дедупликация (Fix #231) корректно смёржет с
                                // freshly-fetched comments при reload.
                                localComments = localComments.map { c ->
                                    if (c.id == optimisticId) c.copy(id = serverCommentId) else c
                                }
                            } else {
                                // Fix #233 (P1-7): откатываем optimistic при ошибке.
                                localComments = localComments.filterNot { it.id == optimisticId }
                                AppLog.w("PostDetail", "wallCreateComment failed (serverCommentId=$serverCommentId) — optimistic comment rolled back (Fix #233)")
                                // Fix #234/#236: возвращаем фото/файлы в превью, чтобы юзер не терял выбор.
                                if (photosToSend.isNotEmpty() || filesToSend.isNotEmpty()) {
                                    pendingCommentPhotos = photosToSend
                                    pendingCommentFiles = filesToSend
                                    inputText = textToSend
                                }
                            }
                        } catch (e: Exception) {
                            AppLog.e("PostDetail", "send comment error", e)
                            // Fix #233 (P1-7): откатываем optimistic и при exception.
                            localComments = localComments.filterNot { it.id < 0L }
                            // Fix #234/#236: возвращаем фото/файлы в превью при exception.
                            if (photosToSend.isNotEmpty() || filesToSend.isNotEmpty()) {
                                pendingCommentPhotos = photosToSend
                                pendingCommentFiles = filesToSend
                                inputText = textToSend
                            }
                        } finally {
                            sending = false
                        }
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            }
        },
    )

    // Индикатор отправки.
    if (sending) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    // Единый пикер «Музыка/Видео» из библиотеки VK — открывается при выборе
    // соответствующего пункта в UnifiedAttachMenu. Видео/аудио прикрепляются
    // к комментарию как "video{ownerId}_{id}" / "audio{ownerId}_{id}".
    // Fix #234 (multi-photo preview): полноэкранный просмотрщик фото.
    val pvi = previewPhotoIndex
    if (pvi != null && pendingCommentPhotos.isNotEmpty()) {
        PhotoViewer(
            photos = pendingCommentPhotos.map { it.uri.toString() },
            initial = pvi.coerceIn(0, pendingCommentPhotos.lastIndex),
            onDismiss = { previewPhotoIndex = null },
        )
    }
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
            onPickAudio = { track ->
                // Формируем attachment-string для wall.createComment.
                val att = if (track.accessKey != null) {
                    "audio${track.ownerId}_${track.id}_${track.accessKey}"
                } else {
                    "audio${track.ownerId}_${track.id}"
                }
                attachmentString = att
                attachedFileName = "Музыка: ${track.title}"
                showAttachmentPicker = false
            },
            onPickVideo = { video ->
                val att = if (video.accessKey != null) {
                    "video${video.ownerId}_${video.id}_${video.accessKey}"
                } else {
                    "video${video.ownerId}_${video.id}"
                }
                attachmentString = att
                attachedFileName = "Видео: ${video.title.ifBlank { "видео" }}"
                showAttachmentPicker = false
            },
        )
    }
}

/**
 * Компонент для отображения отдельного комментария.
 * Fix #237: теперь рендерит вложения комментария (фото/видео/аудио/документ/
 * ссылка/голосовое) — раньше показывался только текст, вложения терялись.
 * Также добавлен лайк-счётчик и кнопка лайка (likes.add/delete type=comment),
 * чтобы комментарий имел те же функции, что и сообщение.
 */
@Composable
private fun CommentItem(
    comment: Comment,
    profiles: Map<Long, UserProfile>,
    // Fix #209: callback кнопки «Ответить».
    onReply: () -> Unit = {},
    // Fix #237: колбэки для вложений.
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // Fix #237: ownerId стены (для likes.add/delete type=comment).
    postOwnerId: Long = 0L,
    // Fix #237: родительский комментарий (на который ответили), если он
    // есть в загруженной выборке. Нужен для компактной цитаты над ответом,
    // чтобы было видно «кто кому отвечает», а не просто «→ Имя».
    parentComment: Comment? = null,
    // §37.12 #328: развёртывание ветки ответов под этим комментарием.
    isExpanded: Boolean = false,
    isLoadingReplies: Boolean = false,
    extraReplies: List<Comment> = emptyList(),
    onExpandThread: () -> Unit = {},
) {
    val author = profiles[comment.fromId]
    val authorName = author?.fullName ?: "Неизвестный"
    val authorAvatar = author?.photo100
    val commentDate = Date(comment.date * 1000)
    val commentDateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(commentDate)
    // Fix #237: автор комментария-родителя (на который ответили).
    // reply_to_user — это uid автора родителя; parentComment — сам объект,
    // если он попал в выборку. Приоритет у parentComment.fromId, fallback на replyToUser.
    val parentAuthorId = parentComment?.fromId ?: comment.replyToUser
    val parentAuthorName = parentAuthorId?.let { profiles[it]?.fullName }
    // Fix #237: текст-превью родительского комментария (1 строка).
    val parentPreviewText = parentComment?.text?.take(80)
    // Fix #237: есть ли родитель вообще (reply на комментарий)?
    val hasReplyContext = comment.replyToComment != null || comment.replyToUser != null
    // Fix #237: счётчик ответов ВЕТКОЙ под этим комментарием (VK thread).
    val threadCount = comment.thread?.count ?: 0
    // Fix #237: локальный стейт лайка комментария.
    val scope = rememberCoroutineScope()
    val app = SovaApp.get()
    val ctx = LocalContext.current
    var isLiked by remember(comment.id) { mutableStateOf(comment.likes?.userLikes == 1) }
    var likeCount by remember(comment.id) { mutableStateOf(comment.likes?.count ?: 0) }
    var likeBusy by remember(comment.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fix #237: отступ слева для reply-комментариев — визуальная
            // вложенность, как в Telegram/VK. Плюс тонкая вертикальная
            // линия-connector слева, чтобы было видно «это ответ в ветке».
            .padding(
                start = if (hasReplyContext) 20.dp else 8.dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // Fix #237: вертикальная линия-connector слева от аватара для
        // reply-комментариев — показывает принадлежность к ветке.
        if (hasReplyContext) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
        }
        // Аватар.
        AsyncImage(
            model = authorAvatar,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentDescription = "Аватар",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = authorName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    text = commentDateStr,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Fix #237: контекст ответа — компактная quote-bar «Ответ для [Имя]:
            // превью текста», если это reply на другой комментарий. Раньше было
            // просто «→ Имя» — было не понятно, что это значит и на какой
            // комментарий отвечают. Теперь: иконка Reply + «Ответ для Имя» +
            // (если родитель в выборке) превью его текста одной строкой.
            if (hasReplyContext && parentAuthorName != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        buildAnnotatedString {
                            append("Ответ для ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append(parentAuthorName)
                            }
                            if (!parentPreviewText.isNullOrBlank()) {
                                append(": $parentPreviewText")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (hasReplyContext && parentAuthorName == null) {
                // Fix #237: родителя нет в выборке (за пределами страницы),
                // но reply_to_user указывает на uid — покажем хотя бы имя,
                // если профиль загружен; иначе нейтральное «В ответ на комментарий».
                val fallbackName = comment.replyToUser?.let { profiles[it]?.fullName }
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (fallbackName != null) "Ответ для $fallbackName" else "В ответ на комментарий",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Текст комментария (с парсингом VK inline-ссылок, как у поста).
            if (comment.text.isNotBlank()) {
                Text(
                    text = re.pinok.util.linkifyVkText(
                        text = comment.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onUrlClick = { url -> re.pinok.util.openUrlExternal(ctx, url) },
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Fix #237: вложения комментария (фото/видео/аудио/документ/ссылка/голосовое).
            // Раньше рендерился только текст — вложения терялись.
            CommentAttachments(
                attachments = comment.attachments ?: emptyList(),
                onVideoClick = onVideoClick,
                onPhotoClick = onPhotoClick,
            )

            // Fix #237: action-row: «Ответить» + лайк (как у сообщений/постов).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onReply,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ответить",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Лайк комментария (likes.add/delete, type=comment).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (likeBusy || postOwnerId == 0L) return@IconButton
                            scope.launch {
                                likeBusy = true
                                val wasLiked = isLiked
                                // Оптимистично переключаем.
                                isLiked = !wasLiked
                                likeCount = if (wasLiked) maxOf(0, likeCount - 1) else likeCount + 1
                                try {
                                    if (wasLiked) {
                                        app.apiClient.likesDelete(
                                            type = "comment",
                                            ownerId = postOwnerId,
                                            itemId = comment.id,
                                        )
                                    } else {
                                        app.apiClient.likesAdd(
                                            type = "comment",
                                            ownerId = postOwnerId,
                                            itemId = comment.id,
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Откатываем оптимистичное изменение.
                                    isLiked = wasLiked
                                    likeCount = if (wasLiked) likeCount + 1 else maxOf(0, likeCount - 1)
                                    AppLog.e("PostDetail", "comment like error", e)
                                } finally {
                                    likeBusy = false
                                }
                            }
                        },
                        enabled = !likeBusy && postOwnerId != 0L,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Outlined.Favorite
                                          else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Лайк комментария",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (likeCount > 0) {
                        Text(
                            text = "$likeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Fix #237 / §37.12 #328: счётчик ответов веткой под этим комментарием.
            // VK возвращает thread.count + thread.items (превью до 10 ответов).
            // По тапу разворачиваем ветку: показываем thread.items + догружаем
            // остальные через wall.getComments(comment_id=...), если preview < count.
            if (threadCount > 0) {
                val word = when {
                    threadCount % 100 in 11..14 -> "ответов"
                    threadCount % 10 == 1 -> "ответ"
                    threadCount % 10 in 2..4 -> "ответа"
                    else -> "ответов"
                }
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onExpandThread() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLoadingReplies) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isExpanded) "↑ свернуть" else "↓ $threadCount $word",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // §37.12 #328: развёрнутая ветка ответов.
            // Сначала рендерим preview из comment.thread.items (до 10, уже в памяти),
            // затем extraReplies — догруженные через wall.getComments(comment_id).
            if (isExpanded && threadCount > 0) {
                val previewReplies = comment.thread?.items ?: emptyList()
                val allReplies = previewReplies + extraReplies
                // Дедупликация по id (preview и extra могут пересекаться).
                val seenIds = HashSet<Long>()
                val dedupReplies = allReplies.filter { seenIds.add(it.id) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
                ) {
                    dedupReplies.forEach { reply ->
                        ReplyItem(
                            reply = reply,
                            profiles = profiles,
                            parentComment = comment,
                            onReply = onReply,
                            onVideoClick = onVideoClick,
                            onPhotoClick = onPhotoClick,
                            postOwnerId = postOwnerId,
                        )
                    }
                    if (isLoadingReplies) {
                        Text(
                            text = "Загрузка ответов…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * §37.12 #328: компактный рендер одного ответа в развёрнутой ветке.
 * Похоже на CommentItem, но без аватара (только имя) и с меньшим отступом —
 * чтобы ветка визуально отличалась от корневых комментариев.
 */
@Composable
private fun ReplyItem(
    reply: Comment,
    profiles: Map<Long, UserProfile>,
    parentComment: Comment?,
    onReply: () -> Unit = {},
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    postOwnerId: Long = 0L,
) {
    val author = profiles[reply.fromId]
    val authorName = author?.fullName ?: "Неизвестный"
    val authorAvatar = author?.photo100
    val replyDate = Date(reply.date * 1000)
    val dateStr = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(replyDate)
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var isLiked by remember(reply.id) { mutableStateOf(reply.likes?.userLikes == 1) }
    var likeCount by remember(reply.id) { mutableStateOf(reply.likes?.count ?: 0) }
    var likeBusy by remember(reply.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // Маленький аватар (28dp вместо 36dp).
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (authorAvatar != null) {
                AsyncImage(
                    model = authorAvatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reply.text.isNotBlank()) {
                Text(
                    text = reply.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Вложения ответа (если есть).
            // §37.12 #328 fix: attachments nullable — smart-cast через локальную val
            // + isNullOrEmpty() (без !!/?. — пользователь явно просил избегать).
            val replyAttachments = reply.attachments
            if (!replyAttachments.isNullOrEmpty()) {
                CommentAttachments(
                    attachments = replyAttachments,
                    onVideoClick = onVideoClick,
                    onPhotoClick = onPhotoClick,
                )
            }
            // Действия: Ответить · Лайк.
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Ответить",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onReply() },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (likeBusy || postOwnerId == 0L) return@IconButton
                            likeBusy = true
                            val wasLiked = isLiked
                            isLiked = !isLiked
                            likeCount = if (isLiked) likeCount + 1 else maxOf(0, likeCount - 1)
                            scope.launch {
                                try {
                                    if (wasLiked) {
                                        app.apiClient.likesDelete("comment", postOwnerId, reply.id)
                                    } else {
                                        app.apiClient.likesAdd("comment", postOwnerId, reply.id)
                                    }
                                } catch (e: Exception) {
                                    isLiked = wasLiked
                                    likeCount = if (wasLiked) likeCount + 1 else maxOf(0, likeCount - 1)
                                } finally {
                                    likeBusy = false
                                }
                            }
                        },
                        enabled = !likeBusy && postOwnerId != 0L,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Лайк ответа",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (likeCount > 0) {
                        Text(
                            text = "$likeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fix #237: рендер вложений комментария. Переиспользует паттерны из рендера
 * поста (фото-сетка, видео-превью, аудио-строка), но в компактном виде для
 * комментария. Поддерживаемые VK типы вложений в wall.createComment / getComments:
 * photo, video, audio, doc, link, audio_message (голосовое), graffiti, poll.
 */
@Composable
private fun CommentAttachments(
    attachments: List<Attachment>,
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    if (attachments.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Фото: собираем все фото-вложения в единую сетку (как в посте, но компактнее).
        val photoUrls = attachments
            .filter { it.type == "photo" && it.photo != null }
            .mapNotNull { it.photo?.largestUrl }
        if (photoUrls.isNotEmpty()) {
            CommentPhotoGrid(urls = photoUrls, onClick = { idx -> onPhotoClick(photoUrls, idx) })
        }

        // Остальные вложения по порядку (видео/аудио/документ/ссылка/голосовое).
        for (att in attachments) {
            when (att.type) {
                "photo" -> { /* уже в сетке выше */ }
                "video" -> att.video?.let { v ->
                    CommentVideoThumb(video = v, onClick = { onVideoClick(v) })
                }
                "audio" -> att.audio?.let { track ->
                    PostDetailAudioRow(track = track)
                }
                "doc" -> att.doc?.let { doc ->
                    if (!doc.isVoiceMessage) CommentDocChip(doc = doc)
                    else CommentVoiceChip(duration = doc.audioMsg?.duration ?: 0)
                }
                "audio_message" -> att.audioMessage?.let { am ->
                    CommentVoiceChip(duration = am.duration)
                }
                "link" -> att.link?.let { link -> CommentLinkCard(link = link) }
                else -> { /* poll / graffiti / gift — пока пропускаем */ }
            }
        }
    }
}

/** Fix #237: компактная сетка фото комментария (1 — крупно, 2 — в ряд,
 *  3-4 — 2×2, 5+ — первая крупно + остальные сеткой). */
@Composable
private fun CommentPhotoGrid(urls: List<String>, onClick: (Int) -> Unit) {
    val cols = when { urls.size == 1 -> 1; urls.size == 2 -> 2; else -> 3 }
    val rows = (urls.size + cols - 1) / cols
    val cellSize = if (cols == 1) 200.dp else 96.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx >= urls.size) {
                        // Заполнитель для выравнивания последнего ряда.
                        Spacer(modifier = Modifier.size(cellSize))
                    } else {
                        AsyncImage(
                            model = urls[idx],
                            contentDescription = "Фото комментария",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onClick(idx) },
                        )
                    }
                }
            }
        }
    }
}

/** Fix #237: превью видео в комментарии — миниатюра с play-иконкой. */
@Composable
private fun CommentVideoThumb(video: Video, onClick: () -> Unit) {
    val thumb = video.thumbUrl
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = "Видео комментария",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Воспроизвести",
            tint = Color.White,
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .padding(4.dp),
        )
    }
}

/** Fix #237: чип документа в комментарии (иконка + имя + расширение + размер). */
@Composable
private fun CommentDocChip(doc: Attachment.Doc) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                // Открываем URL документа во внешнем браузере (VK doc url — прямой).
                if (doc.url.isNotBlank()) re.pinok.util.openUrlExternal(ctx, doc.url)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title.ifBlank { "document.${doc.ext}" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} · ${formatFileSize(doc.size)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fix #237: карточка ссылки в комментарии. */
@Composable
private fun CommentLinkCard(link: Attachment.Link) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { re.pinok.util.openUrlExternal(ctx, link.url) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        link.photo?.largestUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
        } ?: run {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title ?: link.url,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!link.description.isNullOrBlank()) {
                Text(
                    text = link.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Fix #237: чип голосового сообщения в комментарии. */
@Composable
private fun CommentVoiceChip(duration: Int) {
    val m = duration / 60
    val s = duration % 60
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Голосовое",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "🎤 ${m}:${"%02d".format(s)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Fix #237: форматирование размера файла для чипа документа в комментарии. */
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    if (bytes < 1024 * 1024) return DecimalFormat("#.#").format(bytes / 1024.0) + " КБ"
    return DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0)) + " МБ"
}

/** Аудио-строка в детальном просмотре поста. */
@Composable
private fun PostDetailAudioRow(track: Track) {
    val isCurrentPlaying = with(PlayerConnection.playerState.collectAsState().value) {
        currentTrack?.id == track.id && isPlaying
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrentPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable {
                if (isCurrentPlaying) {
                    PlayerConnection.togglePlayPause()
                } else {
                    PlayerConnection.playTrackList(listOf(track))
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isCurrentPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        val m = track.duration / 60
        val s = track.duration % 60
        Text(
            text = "${m}:${"%02d".format(s)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// Fix #237: multi-file + emoji для композера комментария (как в чате).
// ══════════════════════════════════════════════════════════════════════

/**
 * Fix #237 (multi-file): данные о выбранном файле, ожидающем отправки в
 * комментарий. Аналог [PendingPhoto] для фото, но для произвольных файлов.
 * id — уникальный стабильный ключ для LazyRow (см. Fix #235).
 */
data class PendingCommentFile(
    val id: Long,
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val mime: String?,
    val isImage: Boolean,
)

/** Генератор уникальных id для [PendingCommentFile]. */
private val commentFileIdCounter = java.util.concurrent.atomic.AtomicLong(0)
fun nextPendingCommentFileId(): Long = commentFileIdCounter.incrementAndGet()

/**
 * Fix #237 (multi-file): бар выбранных файлов над полем ввода комментария.
 * Горизонтальный LazyRow: для каждого файла — чип (миниатюра для картинок или
 * иконка-закрепка, имя, размер, кнопка ×). Счётчик «N файл(ов)» слева.
 */
@Composable
private fun CommentFilesBar(
    files: List<PendingCommentFile>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📎 ${files.size} файл(ов)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "× — убрать",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        ) {
            items(
                items = files,
                key = { it.id },
            ) { pf ->
                val index = files.indexOf(pf)
                CommentFileChip(pf = pf, onRemove = { onRemove(index) })
            }
        }
    }
}

/** Fix #237: чип одного файла в [CommentFilesBar]. */
@Composable
private fun CommentFileChip(pf: PendingCommentFile, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pf.isImage) {
            AsyncImage(
                model = pf.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pf.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(pf.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp),
        ) {
            Text(
                "✕",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Fix #237 (emoji): панель эмодзи для вставки в текст комментария.
 * Прокручиваемая сетка 8×N популярных эмодзи + кнопка ⌫ (backspace).
 * Стикеры НЕ поддерживаются VK wall.createComment — поэтому только эмодзи.
 */
@Composable
private fun EmojiGridPanel(
    onEmojiClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Кураторская подборка ~128 эмодзи (смайлы + жесты + сердца + символы).
    val emojis = listOf(
        "😀","😃","😄","😁","😆","😅","😂","🤣",
        "🙂","😉","😊","😇","🥰","😍","🤩","😘",
        "😋","😛","😜","🤪","😝","🤑","🤗","🤭",
        "🤫","🤔","🤐","🤨","😐","😑","😶","😏",
        "😒","🙄","😬","🤥","😌","😔","😪","🤤",
        "😴","😷","🤒","🤕","🤢","🤮","🥵","🥶",
        "😵","🤯","🤠","🥳","😎","🤓","🧐","😕",
        "😟","🙁","😮","😯","😲","😳","🥺","😦",
        "😧","😨","😰","😥","😢","😭","😱","😖",
        "😣","😞","😓","😩","😫","🥱","😤","😡",
        "😠","🤬","😈","👿","💀","💩","🤡","👻",
        "👍","👎","👌","✌️","🤞","🤟","🤘","🤙",
        "👈","👉","👆","👇","☝️","✋","🤚","🖐",
        "👋","🤝","🙏","💪","🦾","🤳","👀","🧠",
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍",
        "🤎","💔","❣️","💕","💞","💓","💗","💖",
        "🎉","🎊","🎁","🎂","🌹","🌸","🌺","🌻",
        "🔥","⭐","🌟","✨","⚡","💯","✅","❌",
    )
    Column(
        modifier = modifier
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Эмодзи",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // Backspace — удаляет последний символ (с учётом суррогатов).
            IconButton(
                onClick = onBackspace,
                modifier = Modifier.size(36.dp),
            ) {
                Text("⌫", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
            ) {
                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        val cols = 8
        val rows = (emojis.size + cols - 1) / cols
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx < emojis.size) {
                        Text(
                            text = emojis[idx],
                            fontSize = 24.sp,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onEmojiClick(emojis[idx]) }
                                .padding(2.dp),
                        )
                    } else {
                        Spacer(Modifier.size(36.dp))
                    }
                }
            }
        }
    }
} 