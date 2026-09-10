package re.pinok.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog
import java.io.File

/** Файл, ожидающий отправки в пост (выбран через OpenMultipleDocuments). */
private data class PendingPostFile(
    val id: Long,
    val file: File,
    val displayName: String,
)

/** Генератор уникальных id для [PendingPostFile]. */
private val postFileIdCounter = java.util.concurrent.atomic.AtomicLong(0)
private fun nextPendingPostFileId(): Long = postFileIdCounter.incrementAndGet()

/**
 * Диалог создания нового поста — текстовое поле + вложения + галочка «только для друзей».
 *
 * Вынесен из FeedScreen.kt в общий компонент, чтобы переиспользовать
 * в ProfileScreen.kt (по запросу пользователя 2026-07-12: кнопка
 * «Создать пост» перенесена из ленты в профиль, как в оригинальном VK).
 *
 * #ATTACH-UNIFY (спека «вложения.веб-изучение.унификация.md» §3/§4, п.3+6+7):
 * пост-композер приведён к VK web семантике —
 *  • Фото: мульти-выбор с устройства (до 10, PickMultipleVisualMedia);
 *  • Фото/Музыка/Видео/Файл «Из VK»: AttachmentPickerSheet (attach без upload);
 *  • Видео с устройства: video.save(isClips=false) + videoUploadFile (P2.6);
 *  • Видео по ссылке: video.save(link_url=…) — VK скачивает сам, upload-шаг
 *    не нужен (P2.6, только пост — как в VK web);
 *  • Опрос: конструктор (вопрос + 2-10 вариантов + анонимность) → polls.add
 *    → attachment "poll{owner}_{id}" (P2.7, только пост).
 *
 * Публикация:
 *  • без внутренних вложений — легаси-путь бит-в-бит: [onSubmit], фото/пост
 *    грузит вызывающая сторона (uploadPhotoAndPost/wallPost);
 *  • с внутренними вложениями — если передан [onSubmitWithAttachments],
 *    вызывается он; ИНАЧЕ диалог публикует сам через wallPostWithAttachments
 *    (wall.post без owner_id = своя стена — диалог используется только там).
 *    ВАЖНО (честное отклонение #ATTACH-UNIFY): при самостоятельной публикации
 *    стена профиля НЕ авто-обновляется — канал reloadWallTrigger принадлежит
 *    ProfileScreen (вне зоны волны); новый пост появится после pull-to-refresh
 *    или повторного входа. Лимит VK «до 10 вложений» проверяется честным
 *    тостом, без молчаливого усечения.
 *
 * Легаси-параметры [selectedPhotoUri]/[onPickPhoto]/[onRemovePhoto] сохранены
 * для совместимости существующего вызова (ProfileScreen): превью и
 * легаси-путь публикации работают как прежде. Пункт меню «Фото» теперь
 * открывает внутренний мульти-пикер диалога (требование multi-фото), поэтому
 * легаси-колбэк onPickPhoto остаётся API-совместимым, но меню его не вызывает.
 *
 * VK API: `wall.post` (message, attachments, friends_only, owner_id, publish_date).
 * Фото-флоу: `photos.getWallUploadServer` → upload → `photos.saveWallPhoto` → attachments.
 * См. VK_IMPORT_API.MD §1.1.
 *
 * W36 #COMMUNITY-COMPOSER (C0 из плана волны 35, сверка «Группа_админ» §3.4/§3.5):
 * режим публикации НА СТЕНУ СООБЩЕСТВА — [targetGroupId] (ПОЛОЖИТЕЛЬНЫЙ id).
 *  • Переключатель автора «От моего имени / От имени сообщества» — виден только
 *    при [canPostAsGroup] (admin_level>=2 — редактор/администратор, гейт
 *    wallPost-fromGroup из волны 35); дефолт автора для руководителя —
 *    «сообщество» (аналог cur.defaultPostSettings.official веба, сверка §3.5).
 *  • «Подпись автора» ([signed] → wall.post signed=1) — видна только при
 *    постинге от имени сообщества (веб: signed meaningless без official).
 *  • friends_only скрывается (не применим к стене сообщества).
 *  • Фото на стену сообщества: photos.getWallUploadServer(group_id) +
 *    photos.saveWallPhoto(group_id) — VK требует group_id (положительный),
 *    иначе фото уйдёт на стену пользователя.
 *  • Публикация без вложений: [onSubmitGroup] (стена обновляет вызывающий
 *    экран); с вложениями: [onSubmitGroupWithAttachments], при null —
 *    самопубликация через wallPostWithAttachments(owner_id=-gid).
 *
 * @param onDismiss Вызывается при закрытии диалога без публикации.
 * @param onSubmit Легаси-колбэк (message, friendsOnly) — путь без внутренних вложений.
 * @param selectedPhotoUri Легаси: URI одиночного фото от вызывающей стороны.
 * @param onPickPhoto Легаси: запуск фото-пикера вызывающей стороны (меню не вызывает).
 * @param onRemovePhoto Легаси: удаление легаси-фото.
 * @param onSubmitWithAttachments Опциональный колбэк публикации с готовыми
 *        attachment-строками; null → диалог публикует сам (см. выше).
 * @param targetGroupId W36: положительный id сообщества — режим стены сообщества
 *        (owner_id=-gid); null → легаси-режим своей/чужой стены.
 * @param canPostAsGroup W36: право постить ОТ ИМЕНИ сообщества (admin_level>=2,
 *        isAuthor из волны 35) — включает переключатель автора.
 * @param onSubmitGroup W36: публикация без вложений на стену сообщества
 *        (message, fromGroup, signed) — обновление стены на вызывающей стороне.
 * @param onSubmitGroupWithAttachments W36: публикация с вложениями на стену
 *        сообщества (message, fromGroup, signed, attachments); null → самопубликация.
 */
@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onSubmit: (message: String, friendsOnly: Boolean) -> Unit,
    // Sprint 2, P1-4 (#91): photo picker integration (легаси-путь).
    selectedPhotoUri: Uri? = null,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    // #ATTACH-UNIFY: публикация с внутренними вложениями (опционально).
    onSubmitWithAttachments: ((message: String, friendsOnly: Boolean, attachments: List<String>) -> Unit)? = null,
    // W36 #COMMUNITY-COMPOSER: режим стены сообщества (см. KDoc выше).
    targetGroupId: Long? = null,
    canPostAsGroup: Boolean = false,
    onSubmitGroup: ((message: String, fromGroup: Boolean, signed: Boolean) -> Unit)? = null,
    onSubmitGroupWithAttachments: ((message: String, fromGroup: Boolean, signed: Boolean, attachments: List<String>) -> Unit)? = null,
) {
    val app = SovaApp.get()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var friendsOnly by remember { mutableStateOf(false) }
    // W36 #COMMUNITY-COMPOSER: автор записи + подпись. Дефолт автора для
    // руководителя — «сообщество» (сверка §3.5: defaultPostSettings.official).
    var postAsGroup by remember { mutableStateOf(canPostAsGroup) }
    var signPost by remember { mutableStateOf(false) }
    // Эффективные флаги wall.post: signed без from_group не отправляем
    // (веб: signed meaningless без official).
    val effFromGroup = postAsGroup && canPostAsGroup
    val effSigned = effFromGroup && signPost
    // Единое меню «Прикрепить» — тот же UI, что в чате и комментариях.
    var showAttachMenu by remember { mutableStateOf(false) }
    // #ATTACH-UNIFY: внутренние вложения пост-композера.
    var pendingPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFiles by remember { mutableStateOf<List<PendingPostFile>>(emptyList()) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    // Библиотека VK: пары (attachment-строка, подпись для превью).
    var vkAttachments by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var pollAttachment by remember { mutableStateOf<String?>(null) }
    var pollLabel by remember { mutableStateOf<String?>(null) }
    var videoLinkAttachment by remember { mutableStateOf<String?>(null) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableStateOf(AttachmentPickerTab.Music) }
    var showPollDialog by remember { mutableStateOf(false) }
    var pollCreating by remember { mutableStateOf(false) }
    var showVideoLinkDialog by remember { mutableStateOf(false) }
    var videoLinkLoading by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }

    val hasInternalAttachments = pendingPhotos.isNotEmpty() || pendingFiles.isNotEmpty() ||
        pendingVideoUri != null || videoLinkAttachment != null || pollAttachment != null ||
        vkAttachments.isNotEmpty()

    fun toastLocal(message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    // #ATTACH-UNIFY: мульти-фото с устройства — до 10 за раз (паттерн чата, Fix #234).
    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPhotos = (pendingPhotos + uris).take(10)
        }
    }
    // #ATTACH-UNIFY: видео с устройства (PickVisualMedia.VideoOnly).
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingVideoUri = uri
    }
    // #ATTACH-UNIFY: файлы с устройства — до 10 (OpenMultipleDocuments, паттерн
    // комментария PostDetail Fix #237: URI → temp-файл → upload при публикации).
    val multiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newFiles = mutableListOf<PendingPostFile>()
        for (uri in uris) {
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: continue
                var nameFromResolver: String? = null
                try {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            nameFromResolver = cursor.getString(nameIdx)
                        }
                    }
                } catch (_: Exception) { }
                val rawName = nameFromResolver
                    ?: uri.lastPathSegment?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: "file"
                val safeTempName = "post_${System.currentTimeMillis()}_${newFiles.size}_$rawName"
                val tempFile = File(ctx.cacheDir, safeTempName)
                tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                newFiles += PendingPostFile(
                    id = nextPendingPostFileId(),
                    file = tempFile,
                    displayName = rawName,
                )
            } catch (e: Exception) {
                AppLog.e("CreatePostDialog", "fileLauncher: copy uri→file error for $uri", e)
            }
        }
        if (newFiles.isNotEmpty()) {
            pendingFiles = (pendingFiles + newFiles).take(10)
        }
    }

    // #ATTACH-UNIFY: самостоятельная публикация с внутренними вложениями.
    suspend fun postWithAttachments(message: String, onlyFriends: Boolean) {
        // Лимит VK: до 10 вложений на пост — честный отказ, не молчаливое усечение.
        val totalCount = pendingPhotos.size + pendingFiles.size + vkAttachments.size +
            (if (pendingVideoUri != null) 1 else 0) +
            (if (videoLinkAttachment != null) 1 else 0) +
            (if (pollAttachment != null) 1 else 0) +
            (if (selectedPhotoUri != null) 1 else 0)
        if (totalCount > 10) {
            toastLocal("VK: не более 10 вложений на пост")
            return
        }
        val attachments = mutableListOf<String>()
        // Легаси-фото вызывающей стороны (если передано) + фото с устройства:
        // wall-photo pipeline из uploadPhotoAndPost, по фото на цепочку.
        val photoUris = buildList {
            selectedPhotoUri?.let { add(it) }
            addAll(pendingPhotos)
        }
        for (uri in photoUris) {
            // W36 #COMMUNITY-COMPOSER: upload URL на стену сообщества — group_id
            // (ПОЛОЖИТЕЛЬНЫЙ, конвенция волны 35); null → своя стена (легаси).
            val uploadUrl = app.apiClient.photosGetWallUploadServer(groupId = targetGroupId)
            if (uploadUrl.isNullOrBlank()) {
                toastLocal("Ошибка подготовки загрузки фото")
                return
            }
            val uploaded = app.apiClient.photosUploadWallPhoto(uploadUrl, uri)
            if (uploaded == null) {
                toastLocal("Ошибка загрузки фото")
                return
            }
            val (photoId, photoOwnerId) = app.apiClient.photosSaveWallPhoto(
                server = uploaded.server,
                photo = uploaded.photo,
                hash = uploaded.hash,
                groupId = targetGroupId,
            )
            if (photoId <= 0L || photoOwnerId <= 0L) {
                toastLocal("Ошибка сохранения фото")
                return
            }
            attachments += buildVkAttachment("photo", photoOwnerId, photoId)
        }
        // Файлы с устройства — docs.getWallUploadServer pipeline (uploadDocForComment).
        for (pf in pendingFiles) {
            val att = app.apiClient.uploadDocForComment(pf.file)
            if (att == null) {
                toastLocal("Ошибка загрузки файла: ${pf.displayName}")
                return
            }
            attachments += att
        }
        // Видео с устройства: video.save(isClips=false) → videoUploadFile → attach.
        val videoUri = pendingVideoUri
        if (videoUri != null) {
            val ticket = app.apiClient.videoSave(name = "Видео", isClips = false)
            if (ticket == null) {
                toastLocal("Ошибка подготовки видео")
                return
            }
            val uploaded = app.apiClient.videoUploadFile(ticket.uploadUrl, videoUri)
            if (!uploaded) {
                toastLocal("Ошибка загрузки видео")
                return
            }
            attachments += buildVkAttachment("video", ticket.ownerId, ticket.videoId)
        }
        // Видео по ссылке (тикет уже получен в диалоге ссылки) / опрос / библиотека VK.
        videoLinkAttachment?.let { attachments += it }
        pollAttachment?.let { attachments += it }
        vkAttachments.forEach { attachments += it.first }
        if (attachments.isEmpty()) {
            toastLocal("Не удалось загрузить вложения")
            return
        }
        val delegate = onSubmitWithAttachments
        val gid = targetGroupId
        if (gid != null) {
            // W36 #COMMUNITY-COMPOSER: публикация на стену сообщества
            // (owner_id = -gid; from_group/signed — эффективные флаги).
            val groupDelegate = onSubmitGroupWithAttachments
            if (groupDelegate != null) {
                // Вызывающая сторона сама публикует и обновляет стену.
                groupDelegate(message, effFromGroup, effSigned, attachments)
            } else {
                // Фолбэк: самопубликация — wall.post owner_id=-gid.
                // Честное отклонение: авто-обновление стены вне зоны (#ATTACH-UNIFY).
                val postId = app.apiClient.wallPostWithAttachments(
                    message = message,
                    attachments = attachments.joinToString(","),
                    ownerId = -gid,
                    fromGroup = effFromGroup,
                    signed = effSigned,
                )
                if (postId > 0) {
                    AppLog.i("CreatePostDialog", "group post created: id=$postId, groupId=$gid, fromGroup=$effFromGroup")
                    toastLocal("Опубликовано")
                } else {
                    AppLog.w("CreatePostDialog", "wallPostWithAttachments(group) failed")
                    toastLocal("Не удалось опубликовать пост")
                }
                onDismiss()
            }
        } else if (delegate != null) {
            // Вызывающая сторона сама публикует (аналог легаси-контракта).
            delegate(message, onlyFriends, attachments)
        } else {
            // Делегат не передан — публикуем сами: wall.post без owner_id = своя
            // стена (диалог используется только из своего профиля). Честное
            // отклонение: авто-обновление стены профиля вне зоны (#ATTACH-UNIFY).
            val postId = app.apiClient.wallPostWithAttachments(
                message = message,
                attachments = attachments.joinToString(","),
                friendsOnly = onlyFriends,
            )
            if (postId > 0) {
                AppLog.i("CreatePostDialog", "post created: id=$postId, attachments=${attachments.size}")
                toastLocal("Опубликовано")
            } else {
                AppLog.w("CreatePostDialog", "wallPostWithAttachments failed")
                toastLocal("Не удалось опубликовать пост")
            }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!posting) onDismiss() },
        title = { Text(if (targetGroupId != null) "Новая запись" else "Новый пост") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = {
                        Text(if (targetGroupId != null) "Что нового в сообществе?" else "Что у вас нового?")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Легаси-превью одиночного фото от вызывающей стороны.
                if (selectedPhotoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Выбранное фото",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // Remove button — top-right corner.
                        IconButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // #ATTACH-UNIFY: превью мульти-фото с устройства.
                if (pendingPhotos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // itemsIndexed — Lazy-скоуп (LazyRow), правило Fix #284 соблюдено.
                        itemsIndexed(pendingPhotos) { _, uri ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Фото для поста",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                IconButton(
                                    onClick = { pendingPhotos = pendingPhotos - uri },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Box(
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "✕",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // #ATTACH-UNIFY: строки-превью прочих вложений (видео/ссылка/опрос/
                // файлы/библиотека VK) с удалением.
                pendingVideoUri?.let {
                    AttachmentLabelRow(
                        icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
                        label = "Видео с устройства",
                        onRemove = { pendingVideoUri = null },
                    )
                }
                videoLinkAttachment?.let {
                    AttachmentLabelRow(
                        icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        label = "Видео по ссылке",
                        onRemove = { videoLinkAttachment = null },
                    )
                }
                pollAttachment?.let {
                    AttachmentLabelRow(
                        icon = { Icon(Icons.Outlined.Poll, contentDescription = null) },
                        label = pollLabel ?: "Опрос",
                        onRemove = { pollAttachment = null; pollLabel = null },
                    )
                }
                pendingFiles.forEach { pf ->
                    AttachmentLabelRow(
                        icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                        label = pf.displayName,
                        onRemove = {
                            pendingFiles = pendingFiles.filterNot { it.id == pf.id }
                            pf.file.delete()
                        },
                    )
                }
                vkAttachments.forEach { (att, label) ->
                    AttachmentLabelRow(
                        icon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                        label = label,
                        onRemove = { vkAttachments = vkAttachments.filterNot { it.first == att } },
                    )
                }
                // Строка «Прикрепить» + единое меню (UnifiedAttachMenu).
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable(enabled = !posting) { showAttachMenu = true }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Прикрепить",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    UnifiedAttachMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        // #ATTACH-UNIFY: мульти-фото с устройства (внутренний пикер).
                        onPhoto = {
                            multiPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        // Камера в пост-композере VK web недоступна — пункт скрыт.
                        showCamera = false,
                        showPhotoFromVk = true,
                        onPhotoFromVk = {
                            pickerTab = AttachmentPickerTab.Photos
                            showAttachmentPicker = true
                        },
                        showVideo = true,
                        onVideo = {
                            pickerTab = AttachmentPickerTab.Video
                            showAttachmentPicker = true
                        },
                        showVideoFromDevice = true,
                        onVideoFromDevice = {
                            videoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        showVideoLink = true,
                        onVideoLink = { showVideoLinkDialog = true },
                        showMusic = true,
                        onAudio = {
                            pickerTab = AttachmentPickerTab.Music
                            showAttachmentPicker = true
                        },
                        showFile = true,
                        onFile = { multiFileLauncher.launch(arrayOf("*/*")) },
                        showFileFromVk = true,
                        onFileFromVk = {
                            pickerTab = AttachmentPickerTab.Docs
                            showAttachmentPicker = true
                        },
                        // Подарков в постах нет (VK web) — пункт скрыт.
                        showGift = false,
                        // #ATTACH-UNIFY: опрос — только пост-композер (P2.7).
                        showPoll = true,
                        onPoll = { showPollDialog = true },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // W36 #COMMUNITY-COMPOSER: переключатель автора записи
                // (виден только в групповом режиме; «сообщество» — только при
                // canPostAsGroup=admin_level>=2, гейт волны 35).
                if (targetGroupId != null) {
                    Text(
                        text = "Автор записи",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !effFromGroup,
                            onClick = { postAsGroup = false },
                            label = { Text("От моего имени") },
                        )
                        if (canPostAsGroup) {
                            FilterChip(
                                selected = effFromGroup,
                                onClick = { postAsGroup = true },
                                label = { Text("От имени сообщества") },
                            )
                        }
                    }
                    if (effFromGroup) {
                        // Веб: подпись автора имеет смысл только при official.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = signPost,
                                onCheckedChange = { signPost = it },
                            )
                            Text(
                                text = "Подпись автора (ваше имя под записью)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // friends_only — только для своей стены (к стене сообщества
                // не применим; веб-композер сообщества этот флаг не показывает).
                if (targetGroupId == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = friendsOnly,
                            onCheckedChange = { friendsOnly = it },
                        )
                        Text(
                            text = "Только для друзей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (posting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(
                    onClick = {
                        val message = text.trim()
                        // Можно опубликовать с вложениями даже без текста, или текст без вложений.
                        if (!hasInternalAttachments && message.isBlank() && selectedPhotoUri == null) {
                            return@TextButton
                        }
                        val gid = targetGroupId
                        if (gid != null) {
                            // W36 #COMMUNITY-COMPOSER: групповой режим.
                            if (!hasInternalAttachments) {
                                val groupDelegate = onSubmitGroup
                                if (groupDelegate != null) {
                                    // Основной путь: вызывающий экран публикует и обновляет стену.
                                    groupDelegate(message, effFromGroup, effSigned)
                                } else {
                                    // Defensive-фолбэк: самопубликация wall.post
                                    // owner_id=-gid (обновление стены — вне зоны).
                                    scope.launch {
                                        posting = true
                                        try {
                                            val postId = app.apiClient.wallPost(
                                                message,
                                                ownerId = -gid,
                                                fromGroup = effFromGroup,
                                                signed = effSigned,
                                            )
                                            if (postId > 0) {
                                                AppLog.i("CreatePostDialog", "group post created: id=$postId, groupId=$gid")
                                                toastLocal("Опубликовано")
                                                onDismiss()
                                            } else {
                                                AppLog.w("CreatePostDialog", "wallPost(group) failed")
                                                toastLocal("Не удалось опубликовать пост")
                                            }
                                        } catch (e: Exception) {
                                            AppLog.e("CreatePostDialog", "wallPost(group) error", e)
                                            toastLocal("Ошибка публикации")
                                        } finally {
                                            posting = false
                                        }
                                    }
                                }
                            } else {
                                scope.launch {
                                    posting = true
                                    try {
                                        postWithAttachments(message, friendsOnly)
                                    } catch (e: Exception) {
                                        AppLog.e("CreatePostDialog", "postWithAttachments(group) error", e)
                                        toastLocal("Ошибка публикации")
                                    } finally {
                                        posting = false
                                    }
                                }
                            }
                        } else if (!hasInternalAttachments) {
                            // Легаси-путь бит-в-бит: публикация на вызывающей стороне.
                            onSubmit(message, friendsOnly)
                        } else {
                            scope.launch {
                                posting = true
                                try {
                                    postWithAttachments(message, friendsOnly)
                                } catch (e: Exception) {
                                    AppLog.e("CreatePostDialog", "postWithAttachments error", e)
                                    toastLocal("Ошибка публикации")
                                } finally {
                                    posting = false
                                }
                            }
                        }
                    },
                    enabled = text.isNotBlank() || selectedPhotoUri != null || hasInternalAttachments,
                ) { Text("Опубликовать") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!posting) onDismiss() }) { Text("Отмена") }
        },
    )

    // #ATTACH-UNIFY: пикер библиотеки VK (Фото/Музыка/Видео/Документы).
    // Подарки в постах недоступны — таб скрыт (no-stub).
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = pickerTab,
            showGiftTab = false,
            showPhotoTab = true,
            showDocsTab = true,
            onPickAudio = { track ->
                vkAttachments = vkAttachments + Pair(
                    buildVkAttachment("audio", track.ownerId, track.id, track.accessKey),
                    "Музыка: ${track.title}",
                )
            },
            onPickVideo = { video ->
                vkAttachments = vkAttachments + Pair(
                    buildVkAttachment("video", video.ownerId, video.id, video.accessKey),
                    "Видео: ${video.title.ifBlank { "видео" }}",
                )
            },
            onPickPhotoAttachment = { att ->
                vkAttachments = vkAttachments + Pair(att, "Фото из VK")
            },
            onPickDocAttachment = { att, title ->
                vkAttachments = vkAttachments + Pair(att, "Документ: $title")
            },
        )
    }

    // #ATTACH-UNIFY (P2.7): конструктор опроса → polls.add → attachment poll{}.
    if (showPollDialog) {
        PollCreateDialog(
            creating = pollCreating,
            onDismiss = { if (!pollCreating) showPollDialog = false },
            onCreate = { question, answers, isAnonymous ->
                scope.launch {
                    pollCreating = true
                    try {
                        val poll = app.apiClient.pollsAdd(
                            question = question,
                            answers = answers,
                            isAnonymous = isAnonymous,
                        )
                        if (poll == null) {
                            // Гвард VKA (<2 непустых ответа / offline / ошибка) —
                            // диалог НЕ закрываем, даём исправить варианты.
                            Toast.makeText(ctx, "Минимум 2 варианта", Toast.LENGTH_SHORT).show()
                        } else {
                            pollAttachment = buildVkAttachment("poll", poll.ownerId, poll.id)
                            pollLabel = "Опрос: $question"
                            showPollDialog = false
                        }
                    } catch (e: Exception) {
                        AppLog.e("CreatePostDialog", "pollsAdd error", e)
                        Toast.makeText(ctx, "Не удалось создать опрос", Toast.LENGTH_SHORT).show()
                    } finally {
                        pollCreating = false
                    }
                }
            },
        )
    }

    // #ATTACH-UNIFY (P2.6): видео по ссылке → video.save(link_url) → тикет →
    // attachment "video{owner}_{id}" (upload-шаг не нужен — VK скачивает сам).
    if (showVideoLinkDialog) {
        VideoLinkDialog(
            loading = videoLinkLoading,
            onDismiss = { if (!videoLinkLoading) showVideoLinkDialog = false },
            onAttach = { link ->
                scope.launch {
                    videoLinkLoading = true
                    try {
                        val ticket = app.apiClient.videoSave(
                            name = "Видео по ссылке",
                            isClips = false,
                            linkUrl = link,
                        )
                        if (ticket == null) {
                            Toast.makeText(ctx, "Не удалось добавить видео по ссылке", Toast.LENGTH_SHORT).show()
                        } else {
                            videoLinkAttachment = buildVkAttachment("video", ticket.ownerId, ticket.videoId)
                            showVideoLinkDialog = false
                        }
                    } catch (e: Exception) {
                        AppLog.e("CreatePostDialog", "videoSave(link) error", e)
                        Toast.makeText(ctx, "Не удалось добавить видео по ссылке", Toast.LENGTH_SHORT).show()
                    } finally {
                        videoLinkLoading = false
                    }
                }
            },
        )
    }
}

/**
 * #ATTACH-UNIFY: строка-превью вложения (иконка + подпись + кнопка удаления).
 * IconButton — дефолтный (48dp тач-таргет, правило 44dp соблюдено).
 */
@Composable
private fun AttachmentLabelRow(
    icon: @Composable () -> Unit,
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Удалить вложение",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * #ATTACH-UNIFY (P2.7): конструктор опроса — вопрос + 2-10 вариантов
 * (добавление/удаление полей) + тумблер «Анонимный». Создание выполняет
 * вызывающая сторона (pollsAdd); при ошибке/гварде диалог остаётся открытым.
 */
@Composable
private fun PollCreateDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (question: String, answers: List<String>, isAnonymous: Boolean) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    // Стартуем с двух пустых полей — VK требует минимум 2 варианта.
    var answers by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(false) }
    val validAnswers = answers.map { it.trim() }.filter { it.isNotEmpty() }
    val canCreate = question.isNotBlank() && validAnswers.size >= 2 && !creating
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый опрос") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Вопрос") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !creating,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // forEach — обычный Column, НЕ Lazy-скоуп (правило Fix #284).
                answers.forEachIndexed { index, answer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { newValue ->
                                answers = answers.toMutableList().also { it[index] = newValue }
                            },
                            label = { Text("Вариант ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !creating,
                        )
                        // Удаление поля (минимум 2 поля остаётся — VK требует ≥2).
                        IconButton(
                            onClick = { answers = answers.filterIndexed { i, _ -> i != index } },
                            enabled = answers.size > 2 && !creating,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Удалить вариант",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { answers = answers + "" },
                    enabled = answers.size < 10 && !creating,
                ) {
                    Text("Добавить вариант")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        enabled = !creating,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Анонимный опрос",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(question.trim(), validAnswers, isAnonymous) },
                enabled = canCreate,
            ) {
                Text(if (creating) "Создание…" else "Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Отмена") }
        },
    )
}

/**
 * #ATTACH-UNIFY (P2.6): диалог «Видео по ссылке» — поле URL → video.save(link_url).
 * Только пост-композер (VK web даёт ссылку-видео только там, спека §5.3).
 */
@Composable
private fun VideoLinkDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onAttach: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Видео по ссылке") },
        text = {
            Column {
                Text(
                    text = "VK скачает видео с внешнего ресурса. " +
                        "Оно появится в посте после серверной обработки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на видео") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !loading,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Без схемы — добавляем https:// (паттерн normalizeUrl в ленте).
                    val link = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url.trim()
                    } else {
                        "https://${url.trim()}"
                    }
                    onAttach(link)
                },
                enabled = url.isNotBlank() && !loading,
            ) {
                Text(if (loading) "Загрузка…" else "Прикрепить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Отмена") }
        },
    )
}
