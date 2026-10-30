// File: ui/screens/community/CreateBoardTopicScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.AttachmentPickerTab

/**
 * B-E (#SCOUT-BOARD-SNAP форма создания темы): экран «Новая тема» обсуждения.
 *
 * Соответствие снапшоту VK web SPA (HTML4 — создать тему):
 *   • FormItem «Заголовок» + counter «0 / 255» → input[maxlength=255]
 *   • FormItem «Текст»    + counter «0 / 4096» → textarea[rows=7, maxlength=4096]
 *   • Submit «Создать тему» (modePrimary accent) → board.addTopic
 *
 * After create: навигация к [BoardTopicScreen] (route Screen.BoardTopic) с
 * popUpTo текущего маршрута (inclusive=true) — back из открытой темы не
 * возвращает пользователя обратно к пустой форме.
 *
 * Source API (коммит af0fdb3 + 3a85d79):
 *   suspend fun boardAddTopic(groupId, title, text, fromGroup=0, attachments=null): Long?
 *   — возвращает topic_id (response.topic_id ?: response.id) или null.
 *   attachments — CSV-строка VK-вложений ("photo{oid}_{id},video{oid}_{id}").
 *
 * ATTACH-PATTERN-2: прикрепление вложений (паттерн заимствован из
 * [PostDetailScreen] composer). Без заглушек:
 *   • Фото с устройства   → PickVisualMedia(ImageOnly) + uploadPhotoForComment
 *   • Файл с устройства   → OpenDocument(*) + uploadDocForComment
 *   • Видео / Аудио / Док из VK → AttachmentPickerSheet → attachment-строка
 *   • Опрос   → toast «Опросы в разработке» (VK polls.add — сложный flow)
 *   • Эмодзи  → toast «Эмодзи скоро» (минимум — не молчаливо)
 * boardAddTopic вызывается с attachments = vkAttachmentString.
 *
 * B-FIX-A (TRACER-2 gap): Switch «От имени сообщества» (from_group).
 * В VK web SPA toggle НЕ рендерится (только в legacy /al_board.php modal
 * box через bnt_from_group checkbox), но параметр from_group принимается
 * API напрямую — access_token, БЕЗ hash. Гейта isManager/canManage в этом
 * экране нет → показываем всегда; VK отклонит err=15 если нет прав.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBoardTopicScreen(
    groupId: Long,
    onBack: () -> Unit,
    onCreated: (topicId: Long, title: String) -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as? SovaApp
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // B-FIX-A: from_group state — 1=от имени сообщества, 0=от себя.
    var fromGroup by remember { mutableStateOf(0) }

    // ATTACH-PATTERN-2: state вложений темы (паттерн из PostDetailScreen).
    // Видео/аудио/фото/док из VK → AttachmentPickerSheet → attachment-строка.
    // Фото/файл с устройства → лаунчер → uploadPhotoForComment/uploadDocForComment.
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableStateOf(AttachmentPickerTab.Music) }
    var vkAttachmentString by remember { mutableStateOf<String?>(null) }
    var vkAttachmentLabel by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // Фото с устройства: PickVisualMedia(ImageOnly) → temp-файл → uploadPhotoForComment
    // → "photo{ownerId}_{id}" attachment-строка (паттерн из PostDetailScreen L1167).
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val a = app ?: run {
                Toast.makeText(ctx, "Приложение не готово", Toast.LENGTH_SHORT).show()
                return@launch
            }
            uploading = true
            var inFile: File? = null
            try {
                val f = kotlin.io.path.createTempFile(
                    prefix = "btp_photo_",
                    suffix = ".jpg",
                    directory = ctx.cacheDir.toPath(),
                ).toFile()
                inFile = f
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    f.outputStream().use { out -> ins.copyTo(out) }
                } ?: return@launch
                val att = a.apiClient.uploadPhotoForComment(f, "image/*")
                if (att != null) {
                    vkAttachmentString = att
                    vkAttachmentLabel = "Фото"
                    Toast.makeText(ctx, "Фото прикреплено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                inFile?.delete()
                uploading = false
            }
        }
    }

    // Файл с устройства: OpenDocument(*) → temp-файл → uploadDocForComment
    // → "doc{ownerId}_{id}" attachment-строка (паттерн из PostDetailScreen L1186).
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val a = app ?: run {
                Toast.makeText(ctx, "Приложение не готово", Toast.LENGTH_SHORT).show()
                return@launch
            }
            uploading = true
            var tempFile: File? = null
            try {
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
                val displayName = rawName.replace(Regex("[^a-zA-Z0-9._\\- а-яА-ЯёЁ()\\[\\]]"), "_")
                    .ifBlank { "file_${System.currentTimeMillis()}" }
                val f = File(ctx.cacheDir, "btp_doc_${System.currentTimeMillis()}_$displayName")
                tempFile = f
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    f.outputStream().use { out -> ins.copyTo(out) }
                } ?: return@launch
                val att = a.apiClient.uploadDocForComment(f)
                if (att != null) {
                    vkAttachmentString = att
                    vkAttachmentLabel = "Документ: $displayName"
                    Toast.makeText(ctx, "Файл прикреплён", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Не удалось загрузить файл", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                tempFile?.delete()
                uploading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая тема", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Заголовок (max 255) + счётчик 0/255 ─────────────────────
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Заголовок", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${title.length} / 255",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (title.length > 255) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 255) title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Введите заголовок") },
                    singleLine = true,
                )
            }

            // ── Текст (max 4096) + счётчик 0/4096 ───────────────────────
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Текст", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${text.length} / 4096",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (text.length > 4096) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 4096) text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("Введите текст темы") },
                )
            }

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ATTACH-PATTERN-2 (#SCOUT-BOARD-SNAP): ряд attach-кнопок 24×24 +
            // emoji, как в форме создания темы VK web. Слева 6 кнопок attach,
            // справа emoji-smile. Видео/аудио/док-из-VK → AttachmentPickerSheet;
            // фото-с-устройства → PickVisualMedia + uploadPhotoForComment;
            // файл-с-устройства → OpenDocument + uploadDocForComment;
            // опрос → toast (VK polls.add — сложный flow); emoji → toast.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // С устройства → OpenDocument(*) + uploadDocForComment.
                IconButton(
                    onClick = { docPickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !uploading,
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "С устройства",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Фото → PickVisualMedia(ImageOnly) + uploadPhotoForComment.
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !uploading,
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = "Фото",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Видео → AttachmentPickerSheet(Video).
                IconButton(
                    onClick = {
                        attachmentPickerTab = AttachmentPickerTab.Video
                        showAttachmentPicker = true
                    },
                    enabled = !uploading,
                ) {
                    Icon(
                        Icons.Outlined.VideoCameraBack,
                        contentDescription = "Видео",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Аудио → AttachmentPickerSheet(Music).
                IconButton(
                    onClick = {
                        attachmentPickerTab = AttachmentPickerTab.Music
                        showAttachmentPicker = true
                    },
                    enabled = !uploading,
                ) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = "Аудио",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Файл/документ из VK → AttachmentPickerSheet(Docs).
                IconButton(
                    onClick = {
                        attachmentPickerTab = AttachmentPickerTab.Docs
                        showAttachmentPicker = true
                    },
                    enabled = !uploading,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = "Файл",
                        modifier = Modifier.size(24.dp),
                    )
                }
                // Опрос → toast (VK API polls.add — сложный flow, вне скоупа).
                IconButton(onClick = {
                    Toast.makeText(ctx, "Опросы в разработке", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Outlined.Poll,
                        contentDescription = "Опрос",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Эмодзи → toast (минимум — не молчаливо).
                IconButton(onClick = {
                    Toast.makeText(ctx, "Эмодзи скоро", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = "Эмодзи",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // ── Switch «От имени сообщества» (B-FIX-A, TRACER-2 gap) ────
            // from_group=1 → тема публикуется подписью группы. В VK web SPA
            // toggle НЕ рендерится, но параметр принимается API напрямую.
            // Гейта isManager нет → показываем всегда (VK err=15 если нет прав).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("От имени сообщества", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = fromGroup == 1,
                    onCheckedChange = { fromGroup = if (it) 1 else 0 },
                )
            }

            // ATTACH-PATTERN-2: индикатор выбранного вложения (над Submit).
            if (vkAttachmentLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vkAttachmentLabel!!,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        vkAttachmentString = null
                        vkAttachmentLabel = null
                    }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Убрать",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (uploading) {
                Text(
                    "Загрузка вложения…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // ── Кнопка «Создать тему» ──────────────────────────────────
            Button(
                onClick = {
                    if (title.isBlank() || sending || uploading) return@Button
                    sending = true
                    error = null
                    val t = title
                    val txt = text
                    val atts = vkAttachmentString
                    scope.launch {
                        val a = app
                        if (a == null) {
                            error = "Приложение не готово"
                            sending = false
                            return@launch
                        }
                        try {
                            val topicId = a.apiClient.boardAddTopic(
                                groupId, t, txt,
                                fromGroup = fromGroup,
                                attachments = atts,
                            )
                            if (topicId != null && topicId > 0) {
                                Toast.makeText(ctx, "Тема создана", Toast.LENGTH_SHORT).show()
                                vkAttachmentString = null
                                vkAttachmentLabel = null
                                onCreated(topicId, t)
                            } else {
                                error = "Не удалось: ${a.apiClient.lastApiError ?: "ошибка"}"
                            }
                        } catch (e: Exception) {
                            error = "Ошибка: ${e.message}"
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = title.isNotBlank() && !sending && !uploading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Создать тему")
                }
            }
        }
    }

    // ATTACH-PATTERN-2: AttachmentPickerSheet для видео/аудио/фото/док из VK
    // (паттерн из PostDetailScreen L1524). Фото-с-устройства и файл-с-устройства
    // идут через лаунчеры выше; здесь — только выбор готовых VK-вложений.
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
            showGiftTab = false,
            showPhotoTab = true,
            showDocsTab = true,
            onPickAudio = { track ->
                vkAttachmentString = "audio${track.ownerId}_${track.id}"
                vkAttachmentLabel = "Музыка: ${track.title}"
                showAttachmentPicker = false
            },
            onPickVideo = { video ->
                vkAttachmentString = "video${video.ownerId}_${video.id}"
                vkAttachmentLabel = "Видео: ${video.title.ifBlank { "видео" }}"
                showAttachmentPicker = false
            },
            onPickPhotoAttachment = { att, _ ->
                // _ = thumb-превью (не нужен — только attachment-строка).
                vkAttachmentString = att
                vkAttachmentLabel = "Фото из VK"
                showAttachmentPicker = false
            },
            onPickDocAttachment = { att, title ->
                vkAttachmentString = att
                vkAttachmentLabel = "Документ: $title"
                showAttachmentPicker = false
            },
        )
    }
}
