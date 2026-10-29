// File: ui/screens/community/CreateBoardTopicScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
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
import kotlinx.coroutines.launch
import re.pinok.SovaApp

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
 * Source API (коммит af0fdb3):
 *   suspend fun boardAddTopic(groupId, title, text, fromGroup=0): Long?
 *   — возвращает topic_id (response.topic_id ?: response.id) или null.
 *
 * НЕ включает (TODO-заглушки B-FIX-4): реальный attach-payload для
 * 6 кнопок (device/photo/video/audio/file/poll) и emoji-picker — кнопки
 * видны как в снапшоте VK web (HTML4), но onClick — пустые TODO. Сам
 * board.addTopic не принимает attachments — нужен отдельный upload-flow.
 * «Опубликовать от имени группы» (fromGroup=0 зафиксирован — юзер обычный
 * участник). Это добавится позже отдельными задачами.
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

            // B-FIX-4 (#SCOUT-BOARD-SNAP HTML4): ряд attach-кнопок 24×24 + emoji,
            // как в форме создания темы VK web. Слева 6 кнопок attach, справа
            // emoji-smile. onClick — TODO-заглушки (attach-payload не реализован).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { /* TODO upload from device */ }) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "С устройства",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* TODO photo */ }) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = "Фото",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* TODO video */ }) {
                    Icon(
                        Icons.Outlined.VideoCameraBack,
                        contentDescription = "Видео",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* TODO audio */ }) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = "Аудио",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* TODO file/doc */ }) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = "Файл",
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* TODO poll */ }) {
                    Icon(
                        Icons.Outlined.Poll,
                        contentDescription = "Опрос",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* TODO emoji */ }) {
                    Icon(
                        Icons.Outlined.EmojiEmotions,
                        contentDescription = "Эмодзи",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // ── Кнопка «Создать тему» ──────────────────────────────────
            Button(
                onClick = {
                    if (title.isBlank() || sending) return@Button
                    sending = true
                    error = null
                    val t = title
                    val txt = text
                    scope.launch {
                        val a = app
                        if (a == null) {
                            error = "Приложение не готово"
                            sending = false
                            return@launch
                        }
                        try {
                            val topicId = a.apiClient.boardAddTopic(groupId, t, txt, fromGroup = 0)
                            if (topicId != null && topicId > 0) {
                                Toast.makeText(ctx, "Тема создана", Toast.LENGTH_SHORT).show()
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
                enabled = title.isNotBlank() && !sending,
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
}
