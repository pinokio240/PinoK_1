package re.pinok.ui.components

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.util.AppLog

/**
 * Fix #154 full forwarding: пикер чата для контента, полученного через
 * системный share (ACTION_SEND).
 *
 * Сценарий: пользователь поделился текстом/ссылкой/файлом/картинкой из
 * другого приложения и выбрал PinoK в share-меню. Этот bottom-sheet:
 *   1. Показывает превью полученного контента (текст / имя файла / картинка).
 *   2. Загружает список недавних диалогов (messages.getConversations).
 *   3. Даёт поиск по имени.
 *   4. Кнопка «Отправить» → один из трёх пайплайнов:
 *      - Картинка → [re.pinok.api.VKApiClient.uploadAndSendPhoto]
 *        (photos.getMessagesUploadServer → multipart → photos.save → messages.send).
 *      - Файл (текстовый, бинарный, любой MIME) → [re.pinok.api.VKApiClient.uploadAndSendDoc]
 *        (docs.getMessagesUploadServer(type=doc) → multipart → docs.save →
 *        messages.send с attachment doc{ownerId}_{id}). Документы VK — до
 *        200 МБ, без ограничения на «длину текста». Fix #159: раньше .txt
 *        логи читались как текст и упирались в лимит messages.send ~4096
 *        символов (error 914); теперь уходят как doc-attachment.
 *      - Текст без файла (EXTRA_TEXT только) → messages.send напрямую.
 *      Если к файлу/картинке приложен EXTRA_TEXT, он отправляется как
 *      caption (обрезается до 4000 символов).
 *
 * @param sharedText      текст из EXTRA_TEXT (может быть null если только файл)
 * @param sharedStreamUri URI из EXTRA_STREAM (файл/картинка, может быть null)
 * @param sharedMimeType  MIME type из intent.getType() (для категории)
 * @param onDismiss       закрытие sheet (cancel)
 * @param onSuccess       успешная отправка — закрывает sheet + чистит state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToChatSheet(
    sharedText: String?,
    sharedStreamUri: Uri?,
    sharedMimeType: String?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val app = SovaApp.get()
    val snap by app.prefs.data.collectAsState(initial = null)
    val s = snap ?: return
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var conversations by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loadingList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPeerId by remember { mutableStateOf<Long?>(null) }

    // #SHARE-FAVE: «Избранное» — отправка в self-chat (peer_id=myUserId).
    val myUserId = remember { app.exchangeAuthRepository.userId() }

    var sending by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Категоризируем что именно шерем.
    // isImage — только MIME-проверка; URI null-чек делается в when-ветке,
    // иначе компилятор считает `sharedStreamUri != null` всегда true
    // (т.к. isImage уже включал бы его) и выдаёт warning.
    val isImage = sharedMimeType?.startsWith("image/") == true
    val hasText = !sharedText.isNullOrBlank()

    // Загрузка диалогов при открытии.
    LaunchedEffect(Unit) {
        loadingList = true
        try {
            val result = app.apiClient.messagesGetConversations(count = 50)
            conversations = result.filter { it.peer.id != 0L && it.canWrite?.allowed != false }
            AppLog.i("ShareToChat", "Loaded ${conversations.size} conversations for share picker")
        } catch (e: Exception) {
            AppLog.e("ShareToChat", "loadConversations error", e)
            errorMsg = "Не удалось загрузить диалоги: ${e.message ?: "unknown"}"
        }
        loadingList = false
    }

    val doSend: () -> Unit = {
        val targetPeerId = selectedPeerId
        if (targetPeerId != null) scope.launch {
            sending = true
            errorMsg = null
            statusMsg = "Отправка…"
            try {
                val ok: Boolean = when {
                    // Картинка → photos.* pipeline (photos.getMessagesUploadServer →
                    // multipart → photos.save → messages.send).
                    isImage && sharedStreamUri != null -> {
                        statusMsg = "Загрузка фото…"
                        AppLog.i("ShareToChat", "Sending image to peer=$targetPeerId uri=$sharedStreamUri")
                        app.apiClient.uploadAndSendPhoto(targetPeerId, sharedStreamUri)
                    }
                    // Файл любого типа (text/plain, application/json, application/octet-stream,
                    // и т.д.) → docs.* pipeline. Fix #159: ранее .txt логи читались как
                    // текст и били error 914 на лимите 4096 символов. Теперь файл
                    // целиком уходит как VK-документ (doc attachment, до 200 МБ) —
                    // ограничения на длину нет. Если к файлу приложен EXTRA_TEXT,
                    // он идёт как caption (обрезается до 4000 символов в apiClient).
                    // Явный `sharedStreamUri != null` нужен для smart-cast в вызове ниже.
                    sharedStreamUri != null && !isImage -> {
                        statusMsg = "Загрузка файла как документа…"
                        AppLog.i("ShareToChat", "Sending file as doc to peer=$targetPeerId uri=$sharedStreamUri mime=$sharedMimeType")
                        val caption = sharedText ?: ""
                        app.apiClient.uploadAndSendDoc(
                            peerId = targetPeerId,
                            uri = sharedStreamUri,
                            mimeType = sharedMimeType,
                            message = caption,
                        )
                    }
                    // Только текст (EXTRA_TEXT без файла) → messages.send напрямую.
                    hasText -> {
                        // Smart cast: hasText = !sharedText.isNullOrBlank() —
                        // компилятор знает что sharedText != null в этой ветке.
                        val msg = sharedText
                        statusMsg = "Отправка текста…"
                        AppLog.i("ShareToChat", "Sending text (${msg.length} chars) to peer=$targetPeerId")
                        val msgId = app.apiClient.messagesSend(
                            peerId = targetPeerId,
                            message = msg,
                        )
                        msgId > 0L
                    }
                    else -> {
                        errorMsg = "Нет контента для отправки"
                        sending = false
                        statusMsg = null
                        false
                    }
                }
                if (ok) {
                    AppLog.i("ShareToChat", "Share sent successfully to peer=$targetPeerId")
                    statusMsg = "Отправлено"
                    onSuccess()
                } else if (errorMsg == null) {
                    errorMsg = "Не удалось отправить. Проверьте логи."
                    statusMsg = null
                }
            } catch (e: Exception) {
                AppLog.e("ShareToChat", "Send failed", e)
                errorMsg = "Ошибка: ${e.message ?: "unknown"}"
                statusMsg = null
            }
            sending = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header: title + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Отправить в PinoK",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, enabled = !sending) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                }
            }

            // Превью контента
            ShareContentPreview(
                text = sharedText,
                uri = sharedStreamUri,
                isImage = isImage,
                mimeType = sharedMimeType,
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Поиск
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск диалога") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                enabled = !sending,
            )

            Spacer(Modifier.height(8.dp))

            // Список диалогов
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
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
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // #SHARE-FAVE: «Избранное» — pinned entry в начале списка.
                        // Отправка в self-chat (peer_id = myUserId), аналог
                        // «Сохранить себе» в VK.
                        if (s.msgShowFavorites && myUserId > 0L && searchQuery.isBlank()) {
                            item(key = "favorites") {
                                val isSelected = selectedPeerId == myUserId
                                ShareChatRow(
                                    name = "Избранное",
                                    photoUrl = "",
                                    isSelected = isSelected,
                                    enabled = !sending,
                                    leadingIcon = Icons.Outlined.Bookmark,
                                    onClick = {
                                        selectedPeerId = if (isSelected) null else myUserId
                                    },
                                )
                            }
                        }
                        items(filtered, key = { it.peer.id }) { conv ->
                            val peer = conv.peer
                            val isSelected = selectedPeerId == peer.id
                            ShareChatRow(
                                name = peer.title ?: "Диалог ${peer.id}",
                                photoUrl = peer.photo ?: "",
                                isSelected = isSelected,
                                enabled = !sending,
                                onClick = {
                                    selectedPeerId = if (isSelected) null else peer.id
                                },
                            )
                        }
                        if (filtered.isEmpty()) {
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

            // Статус / ошибка
            // #NULL-EXPLICIT: захват var-делегатов в val — smart-cast по
            // делегированному свойству невозможен; null здесь возможен только
            // до установки, ветка просто не рисует текст (как и раньше).
            Spacer(Modifier.height(8.dp))
            val statusText = statusMsg
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            val errorText = errorMsg
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Кнопка отправки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    enabled = !sending,
                ) { Text("Отмена") }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = doSend,
                    enabled = !sending && selectedPeerId != null,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Отправить")
                }
            }
        }
    }
}

/**
 * Превью полученного контента — показывает что именно будет отправлено.
 */
@Composable
private fun ShareContentPreview(
    text: String?,
    uri: Uri?,
    isImage: Boolean,
    mimeType: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isImage && uri != null -> {
                AsyncImage(
                    model = uri,
                    contentDescription = "Изображение",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Изображение",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Будет загружено и отправлено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uri != null -> {
                // Файл — показываем имя из URI если можно.
                val fileName = runCatching {
                    java.net.URLDecoder.decode(uri.lastPathSegment ?: uri.toString(), "UTF-8")
                }.getOrDefault(uri.lastPathSegment ?: uri.toString())
                Icon(
                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Файл → документ VK",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Будет загружен как документ (до 200 МБ)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    if (mimeType != null) {
                        Text(
                            text = mimeType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            text != null -> {
                Icon(
                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Текст (${text.length} симв.)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = text.take(120) + if (text.length > 120) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Строка диалога в списке выбора.
 */
@Composable
private fun ShareChatRow(
    name: String,
    photoUrl: String,
    isSelected: Boolean,
    enabled: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            } else if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
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
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        if (isSelected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Выбрано",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
