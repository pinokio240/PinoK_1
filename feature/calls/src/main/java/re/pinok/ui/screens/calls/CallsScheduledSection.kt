package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

private data class ScheduledCallItem(
    val callId: String,
    val name: String,
    val dateLabel: String,
    val ts: Long,
)

/**
 * #CALLS-SNAP (2026-09-06): Этап А2/А3 + Этап Г/Г3 плана «звонки.перенос.план.md»
 * — секция «Запланированные»: реальный список messages.getScheduledCalls через
 * репозиторий раздела (CallsSectionRepository.scheduled) + ДЕЙСТВИЯ карточки
 * (REV-UI §1.2 «перенести/удалить/начать сейчас»):
 *  - «Начать сейчас» → messagesForceCallFinish(callId) (механизм запуска по плану Г3);
 *  - «Редактировать» → модалка планирования (CallsScheduleCallDialog в режиме
 *    editCall с существующим call_id, Г2);
 *  - «Удалить» → messagesDeleteScheduledCall(callId) с AlertDialog-подтверждением.
 * После успеха — repo.refresh(SCHEDULED, force=true) + Toast; при провале —
 * Toast с РЕАЛЬНЫМ сообщением (lastApiError фасада). Парсинг полей — tolerant
 * кандидаты, согласованные с CallsSectionRepositoryImpl (SCHEDULED — сырые
 * JsonObject из messages.getScheduledCalls).
 */
@Composable
fun CallsScheduledSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repo.scheduled.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsScheduledSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.SCHEDULED, force = false)
    }

    var editItem by remember { mutableStateOf<ScheduledCallItem?>(null) }
    var deleteItem by remember { mutableStateOf<ScheduledCallItem?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Общий исполнитель действий карточки: API → refresh+Toast / Toast с реальной ошибкой.
    val runAction: (ScheduledCallItem, String, suspend (String) -> Boolean) -> Unit =
        { item, successText, apiCall ->
            if (busy) {
                AppLog.w("CallsScheduledSection", "действие пропущено: предыдущее ещё выполняется")
            } else {
                busy = true
                scope.launch {
                    val ok = try {
                        apiCall(item.callId)
                    } catch (e: Exception) {
                        AppLog.e("CallsScheduledSection", "action error (callId=${item.callId})", e)
                        false
                    }
                    busy = false
                    if (ok) {
                        AppLog.i("CallsScheduledSection", "action OK (callId=${item.callId})")
                        repo.refresh(CallsSectionKey.SCHEDULED, force = true)
                        Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    } else {
                        val apiErr = deps.apiClient.lastApiError
                        val msg = if (apiErr.isNullOrBlank()) {
                            "Действие не выполнено (сервер не подтвердил)"
                        } else {
                            "Ошибка: $apiErr"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет запланированных звонков",
        onRetry = { repo.refresh(CallsSectionKey.SCHEDULED, force = true) },
        modifier = Modifier.testTag("scheduled_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { it.parseScheduledCall() } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет запланированных звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Запланированные · " + items.size,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("scheduled_list"),
                ) {
                    items(items, key = { it.callId }) { item ->
                        ScheduledCallCard(
                            item = item,
                            busy = busy,
                            onStartNow = {
                                // Г3: «Начать сейчас» — forceCallFinish (план §1.2/Г3).
                                runAction(item, "Звонок запущен (подтверждено сервером)") { callId ->
                                    deps.apiClient.messagesForceCallFinish(callId)
                                }
                            },
                            onEdit = { editItem = item },
                            onDelete = { deleteItem = item },
                            modifier = Modifier.testTag("scheduled_item"),
                        )
                    }
                }
            }
        }
    }

    // «Редактировать» → модалка планирования в режиме правки (Г2, существующий call_id).
    val editing = editItem
    if (editing != null) {
        CallsScheduleCallDialog(
            editCallId = editing.callId,
            initialName = editing.name,
            initialDateSec = editing.ts,
            onDismiss = { editItem = null },
        )
    }

    // «Удалить» → подтверждение (необратимо) → deleteScheduledCall.
    val deleting = deleteItem
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Удалить запланированный звонок?") },
            text = { Text("«" + deleting.name + "» будет удалён. Действие необратимо.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val target = deleting
                        deleteItem = null
                        runAction(target, "Запланированный звонок удалён") { callId ->
                            deps.apiClient.messagesDeleteScheduledCall(callId)
                        }
                    },
                    modifier = Modifier.testTag("scheduled_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ScheduledCallCard(
    item: ScheduledCallItem,
    busy: Boolean,
    onStartNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.dateLabel.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Меню действий карточки (REV-UI §1.2): Начать сейчас / Редактировать / Удалить.
            Box {
                IconButton(
                    enabled = !busy,
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("scheduled_item_menu"),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Действия")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Начать сейчас") },
                        onClick = {
                            menuOpen = false
                            onStartNow()
                        },
                        modifier = Modifier.testTag("scheduled_item_start_now"),
                    )
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("scheduled_item_edit"),
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("scheduled_item_delete"),
                    )
                }
            }
        }
    }
}

/** Парсинг items[] messages.getScheduledCalls (новый код — #NULL-EXPLICIT). */
private fun JsonObject.parseScheduledCall(): ScheduledCallItem? {
    return try {
        val idEl = get("call_id")
        if (idEl == null || !idEl.isJsonPrimitive) return null
        val callId = idEl.asString

        var name = "Запланированный звонок"
        val nameEl = get("name")
        if (nameEl != null && nameEl.isJsonPrimitive) {
            val n = nameEl.asString
            if (n.isNotBlank()) name = n
        } else {
            val titleEl = get("title")
            if (titleEl != null && titleEl.isJsonPrimitive) {
                val t = titleEl.asString
                if (t.isNotBlank()) name = t
            }
        }

        var ts = 0L
        val scheduledEl = get("scheduled_date")
        if (scheduledEl != null && scheduledEl.isJsonPrimitive) ts = scheduledEl.asLong
        if (ts == 0L) {
            val dateEl = get("date")
            if (dateEl != null && dateEl.isJsonPrimitive) ts = dateEl.asLong
        }
        if (ts == 0L) {
            val startEl = get("start_date")
            if (startEl != null && startEl.isJsonPrimitive) ts = startEl.asLong
        }
        if (ts > 100_000_000_000L) ts = ts / 1000L // защита от миллисекунд
        val dateLabel = if (ts == 0L) "" else ts.toAbsoluteTime()

        ScheduledCallItem(callId, name, dateLabel, ts)
    } catch (e: Exception) {
        AppLog.e("CallsScheduledSection", "parseScheduledCall: запись пропущена", e)
        null
    }
}
