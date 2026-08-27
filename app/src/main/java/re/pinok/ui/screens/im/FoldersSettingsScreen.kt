package re.pinok.ui.screens.im

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Chat
import re.pinok.data.model.ChatFolder
import re.pinok.util.AppLog

/**
 * P3.3: FoldersSettingsScreen — экран управления папками диалогов.
 *
 * Аналог m.vk.ru: «Папки с чатами» (File 2 из архива).
 * Структура:
 *   - FieldGroup «Папки»: «Добавить папку» + существующие папки (tap → edit, delete icon).
 *   - FieldGroup «Рекомендации»: «Бизнес» с кнопкой «Добавить».
 *
 * Папки хранятся в FoldersRepository (SovaPrefs.msgFoldersData, JSON).
 * VK API messages.getChatFolders — best-effort (недокументирован).
 *
 * @param onBack Колбэк возврата (nav.popBackStack).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersSettingsScreen(
    onBack: () -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    var folders by remember { mutableStateOf<List<ChatFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Диалог создания/редактирования. null = закрыт. id=0 = создание.
    var editingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var chatsForPicker by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var chatsLoading by remember { mutableStateOf(false) }

    // Загрузка папок при открытии экрана.
    fun loadFolders() {
        scope.launch {
            loading = true
            try {
                folders = app.foldersRepository.loadFolders()
            } catch (e: Exception) {
                AppLog.e("FoldersSettings", "loadFolders error", e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFolders() }

    // Загрузка списка чатов для picker (лениво — только при открытии диалога).
    LaunchedEffect(editingFolder) {
        if (editingFolder != null && chatsForPicker.isEmpty()) {
            chatsLoading = true
            try {
                chatsForPicker = app.apiClient.messagesGetConversations(count = 80)
                    .distinctBy { it.peer.id }
            } catch (e: Exception) {
                AppLog.e("FoldersSettings", "load chats error", e)
            } finally {
                chatsLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Папки с чатами") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Section: Папки
            item {
                Text(
                    text = "Папки",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            // «Добавить папку»
            item {
                FolderRow(
                    icon = Icons.Filled.CreateNewFolder,
                    title = "Добавить папку",
                    subtitle = "Сгруппируйте чаты по темам",
                    onClick = {
                        editingFolder = ChatFolder(id = 0, title = "", peerIds = emptySet())
                    },
                )
            }
            // Существующие папки
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    icon = Icons.Filled.Folder,
                    title = folder.title,
                    subtitle = "${folder.peerIds.size} чатов",
                    onClick = {
                        // Тап → редактирование.
                        editingFolder = folder
                    },
                    trailing = {
                        IconButton(onClick = {
                            scope.launch {
                                app.foldersRepository.deleteFolder(folder.id)
                                folders = app.foldersRepository.loadFolders()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить папку",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
            // Section: Рекомендации
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Рекомендации",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item {
                RecommendationRow(
                    title = "Бизнес",
                    subtitle = "Рабочие чаты и коллеги",
                    onAdd = {
                        scope.launch {
                            // Создаём папку «Бизнес» с пустым списком — пользователь
                            // добавит чаты через редактирование.
                            app.foldersRepository.addFolder("Бизнес", emptySet())
                            folders = app.foldersRepository.loadFolders()
                        }
                    },
                )
            }
            item {
                RecommendationRow(
                    title = "Друзья",
                    subtitle = "Близкие переписки",
                    onAdd = {
                        scope.launch {
                            app.foldersRepository.addFolder("Друзья", emptySet())
                            folders = app.foldersRepository.loadFolders()
                        }
                    },
                )
            }
        }
    }

    // Диалог создания/редактирования папки.
    val dlg = editingFolder
    if (dlg != null) {
        FolderEditDialog(
            folder = dlg,
            chats = chatsForPicker,
            chatsLoading = chatsLoading,
            onDismiss = { editingFolder = null },
            onSave = { title, peerIds ->
                scope.launch {
                    if (dlg.id == 0L) {
                        app.foldersRepository.addFolder(title, peerIds)
                    } else {
                        app.foldersRepository.editFolder(dlg.id, title, peerIds)
                    }
                    folders = app.foldersRepository.loadFolders()
                    editingFolder = null
                }
            },
        )
    }
}

/**
 * Строка папки: иконка + title + subtitle + опциональный trailing.
 */
@Composable
private fun FolderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing()
    }
}

/**
 * Строка рекомендации с кнопкой «Добавить».
 */
@Composable
private fun RecommendationRow(
    title: String,
    subtitle: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) {
            Text("Добавить")
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

/**
 * Диалог создания/редактирования папки: поле названия + выбор чатов (checkboxes).
 */
@Composable
private fun FolderEditDialog(
    folder: ChatFolder,
    chats: List<Chat>,
    chatsLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, peerIds: Set<Long>) -> Unit,
) {
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    var selected by remember(folder.id) { mutableStateOf(folder.peerIds) }
    val isNew = folder.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новая папка" else "Редактировать папку") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Чаты в папке (${selected.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (chatsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (chats.isEmpty()) {
                    Text(
                        text = "Нет доступных чатов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    // Список чатов с чекбоксами. max height для скролла.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    ) {
                        items(chats, key = { it.peer.id }) { chat ->
                            val isChecked = chat.peer.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isChecked) {
                                            selected - chat.peer.id
                                        } else {
                                            selected + chat.peer.id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Чекбокс.
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                // Аватар.
                                AsyncImage(
                                    model = chat.peer.photo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = chat.peer.title ?: "Чат ${chat.peer.id}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), selected) },
                enabled = title.isNotBlank(),
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
