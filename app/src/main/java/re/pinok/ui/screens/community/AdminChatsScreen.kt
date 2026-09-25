// File: ui/screens/community/AdminChatsScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Chat
import re.pinok.data.model.ChatPermissions
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════════
// C6 (#ADMIN-CHATS, W41): «Чаты сообщества» — паритет секции web-админки
// (HAR vk.ru_чат_2, 2026-09-25). Форматы сняты с реального веба:
//   • список    — messages.searchConversations {q:" ", extended, group_id}
//   • детали    — messages.getConversationsById {peer_ids, group_id}
//   • rename    — messages.editChat {chat_id, title, group_id}
//   • права     — messages.editChat {chat_id, permissions, group_id}
//   • ссылка    — messages.getInviteLink {peer_id, reset:0, group_id}
//   • удалить   — messages.dropChatForAll {chat_id, group_id}
// Всё — web-шлюз (forceWebGateway), тот же токен.
// ═══════════════════════════════════════════════════════════════════════════

/** Подписи ключей permissions (порядок — как в веб-форме HAR). */
private val CHAT_PERMISSION_KEYS = listOf(
    "invite" to "Приглашение участников",
    "change_info" to "Изменение информации",
    "change_pin" to "Закрепление сообщений",
    "use_mass_mentions" to "Массовые упоминания",
    "see_invite_link" to "Просмотр ссылки-приглашения",
    "call" to "Звонки",
    "change_style" to "Смена стиля чата",
    "change_admins" to "Назначение администраторов",
)

/** Подписи значений permissions (web HAR: all / owner_and_admins / owner). */
private val CHAT_PERMISSION_VALUES = listOf(
    "all" to "Все участники",
    "owner_and_admins" to "Владелец и админы",
    "owner" to "Только владелец",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminChatsScreen(
    groupId: Long,
    onBack: () -> Unit,
    onOpenChat: (peerId: Long, title: String, photo: String?) -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }

    // Действие над выбранным чатом: null = ничего, иначе строка-меню.
    var actionChat by remember { mutableStateOf<Chat?>(null) }
    var renameChat by remember { mutableStateOf<Chat?>(null) }
    var permsChat by remember { mutableStateOf<Chat?>(null) }
    var linkChat by remember { mutableStateOf<Chat?>(null) }
    var dropChat by remember { mutableStateOf<Chat?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val list = app.apiClient.messagesGetGroupChats(groupId)
                if (list.isEmpty() && !app.apiClient.lastApiError.isNullOrBlank()) {
                    error = app.apiClient.lastApiError
                }
                chats = list
            } catch (e: Exception) {
                AppLog.e("AdminChats", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    fun rename(chat: Chat, title: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = app.apiClient.messagesEditChat(
                    chatId = chat.peer.localId, title = title, groupId = groupId,
                )
                Toast.makeText(
                    context,
                    if (ok) "Сохранено" else "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}",
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) {
                    renameChat = null
                    load() // перечитываем фактическое состояние сервера
                }
            } catch (e: Exception) {
                AppLog.e("AdminChats", "rename failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun savePermissions(chat: Chat, p: ChatPermissions) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = app.apiClient.messagesEditChat(
                    chatId = chat.peer.localId, groupId = groupId, permissions = p,
                )
                Toast.makeText(
                    context,
                    if (ok) "Сохранено" else "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}",
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) {
                    permsChat = null
                    load()
                }
            } catch (e: Exception) {
                AppLog.e("AdminChats", "permissions failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun dropForAll(chat: Chat) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = app.apiClient.messagesDropChatForAll(chat.peer.localId, groupId)
                Toast.makeText(
                    context,
                    if (ok) "Беседа удалена для всех" else "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}",
                    Toast.LENGTH_SHORT,
                ).show()
                dropChat = null
                if (ok) load()
            } catch (e: Exception) {
                AppLog.e("AdminChats", "dropForAll failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты сообщества") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> ErrorView(
                message = error,
                onRetry = { load() },
                modifier = Modifier.padding(pad),
            )

            chats.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text(
                        "Бесед сообщества нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
                items(chats, key = { it.peer.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actionChat = chat }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = chat.peer.photo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(46.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chat.peer.title.orEmpty().ifBlank { "Беседа" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val preview = chat.lastMessage?.text
                            if (!preview.isNullOrBlank()) {
                                Text(
                                    preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Действия",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    actionChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { actionChat = null },
            title = { Text(chat.peer.title.orEmpty().ifBlank { "Беседа" }) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionChat = null
                        onOpenChat(chat.peer.id, chat.peer.title.orEmpty().ifBlank { "Беседа" }, chat.peer.photo)
                    }) { Text("Открыть чат") }
                    TextButton(onClick = { actionChat = null; renameChat = chat }) { Text("Переименовать") }
                    TextButton(onClick = { actionChat = null; permsChat = chat }) { Text("Управление") }
                    TextButton(onClick = { actionChat = null; linkChat = chat }) { Text("Ссылка-приглашение") }
                    TextButton(onClick = { actionChat = null; dropChat = chat }) { Text("Удалить для всех") }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionChat = null }) { Text("Закрыть") }
            },
        )
    }

    renameChat?.let { chat ->
        var title by remember(chat.peer.id) { mutableStateOf(chat.peer.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { if (!busy) renameChat = null },
            title = { Text("Переименовать") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Название") },
                    enabled = !busy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && title.isNotBlank(),
                    onClick = { rename(chat, title.trim()) },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { renameChat = null }) { Text("Отмена") }
            },
        )
    }

    permsChat?.let { chat ->
        ChatPermissionsDialog(
            chat = chat,
            groupId = groupId,
            busy = busy,
            onDismiss = { if (!busy) permsChat = null },
            onSave = { p -> savePermissions(chat, p) },
        )
    }

    linkChat?.let { chat ->
        var link by remember(chat.peer.id) { mutableStateOf<String?>(null) }
        var linkErr by remember(chat.peer.id) { mutableStateOf(false) }
        LaunchedEffect(chat.peer.id) {
            link = app.apiClient.messagesGetGroupChatInviteLink(chat.peer.id, groupId)
            linkErr = link.isNullOrBlank()
        }
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { linkChat = null },
            title = { Text("Ссылка-приглашение") },
            text = {
                when {
                    link == null && !linkErr -> Text("Загрузка…")
                    linkErr -> Text("Не удалось получить ссылку")
                    else -> Text(link.orEmpty())
                }
            },
            confirmButton = {
                if (!linkErr && !link.isNullOrBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(link.orEmpty()))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }) { Text("Копировать") }
                }
            },
            dismissButton = {
                TextButton(onClick = { linkChat = null }) { Text("Закрыть") }
            },
        )
    }

    dropChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { if (!busy) dropChat = null },
            title = { Text("Удалить беседу?") },
            text = {
                Text(
                    "«${chat.peer.title.orEmpty().ifBlank { "Беседа" }}» будет удалена для всех участников. " +
                        "Историю сообщений восстановить нельзя.",
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { dropForAll(chat) }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { dropChat = null }) { Text("Отмена") }
            },
        )
    }
}

/**
 * Диалог «Управление» — права участников беседы сообщества. Prefill —
 * из chat_settings.permissions (messages.getConversationsById с group_id,
 * как в web HAR). Если права сервер не отдал — честный отказ, без дефолтов.
 */
@Composable
private fun ChatPermissionsDialog(
    chat: Chat,
    groupId: Long,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ChatPermissions) -> Unit,
) {
    val app = SovaApp.get()
    var perms by remember { mutableStateOf<ChatPermissions?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(chat.peer.id) {
        val details = app.apiClient.messagesGetConversationsById(listOf(chat.peer.id), groupId)
            .firstOrNull()
        val p = details?.permissions
        if (p == null) {
            loadFailed = true
        } else {
            perms = p
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Управление") },
        text = {
            when {
                loadFailed -> Text("Права беседы не получены от сервера")
                perms == null -> Text("Загрузка…")
                else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val p = perms ?: return@Column
                    CHAT_PERMISSION_KEYS.forEach { (key, label) ->
                        val current: String = when (key) {
                            "invite" -> p.invite
                            "change_info" -> p.changeInfo
                            "change_pin" -> p.changePin
                            "use_mass_mentions" -> p.useMassMentions
                            "see_invite_link" -> p.seeInviteLink
                            "call" -> p.call
                            "change_style" -> p.changeStyle
                            else -> p.changeAdmins
                        } ?: "owner_and_admins"
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                        CHAT_PERMISSION_VALUES.forEach { (value, valueLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        perms = when (key) {
                                            "invite" -> p.copy(invite = value)
                                            "change_info" -> p.copy(changeInfo = value)
                                            "change_pin" -> p.copy(changePin = value)
                                            "use_mass_mentions" -> p.copy(useMassMentions = value)
                                            "see_invite_link" -> p.copy(seeInviteLink = value)
                                            "call" -> p.copy(call = value)
                                            "change_style" -> p.copy(changeStyle = value)
                                            else -> p.copy(changeAdmins = value)
                                        }
                                    }
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = current == value, onClick = null)
                                Spacer(Modifier.width(6.dp))
                                Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && perms != null,
                onClick = { perms?.let(onSave) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// C6 (#ADMIN-MESSAGES, W41): раздел «Сообщения» веб-админки.
// READ  — groups.getGroupSettings {group_id, fields: messages_*}
// WRITE — groups.setGroupSettings (6 полей плоско, как в web HAR).
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMessagesScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf<VKApiClient.GroupMessagesSettings?>(null) }

    var enabled by remember { mutableStateOf(false) }
    var firstMessage by remember { mutableStateOf("") }
    var widgetEnabled by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val st = app.apiClient.groupsGetMessagesSettings(groupId)
                if (st == null) {
                    error = app.apiClient.lastApiError?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить настройки"
                } else {
                    settings = st
                    enabled = st.messagesEnabled
                    firstMessage = st.firstMessage
                    widgetEnabled = st.widgetEnabled
                }
            } catch (e: Exception) {
                AppLog.e("AdminMessages", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    fun save() {
        val base = settings ?: return
        if (saving) return
        saving = true
        scope.launch {
            try {
                // Round-trip: widget_info/offline_info/domains не редактируются
                // здесь — отправляем прочитанные значения как есть (web HAR шлёт
                // все 6 полей всегда).
                val ok = app.apiClient.groupsSetMessagesSettings(
                    groupId,
                    VKApiClient.GroupMessagesSettings(
                        messagesEnabled = enabled,
                        firstMessage = firstMessage.trim(),
                        widgetEnabled = widgetEnabled,
                        widgetInfo = base.widgetInfo,
                        widgetOfflineInfo = base.widgetOfflineInfo,
                        widgetDomains = base.widgetDomains,
                    ),
                )
                if (ok) {
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось сохранить" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminMessages", "save failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сообщения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> ErrorView(
                message = error,
                onRetry = { load() },
                modifier = Modifier.padding(pad),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сообщения сообщества", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Возможность написать сообществу",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !saving)
                }

                if (enabled) {
                    OutlinedTextField(
                        value = firstMessage,
                        onValueChange = { if (it.length <= 130) firstMessage = it },
                        label = { Text("Приветственное сообщение") },
                        supportingText = { Text("${firstMessage.length}/130") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        enabled = !saving,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Виджет сообщений", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Блок сообщества на сайте",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = widgetEnabled, onCheckedChange = { widgetEnabled = it }, enabled = !saving)
                    }
                }

                Spacer(Modifier.size(16.dp))
                Button(onClick = { save() }, enabled = !saving && settings != null) {
                    Text(if (saving) "Сохранение…" else "Сохранить")
                }
            }
        }
    }
}
