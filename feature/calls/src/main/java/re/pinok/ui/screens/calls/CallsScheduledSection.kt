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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

private data class ScheduledCallItem(
    val callId: String,
    val name: String,
    val dateLabel: String,
)

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А3 — секция «Запланированные» оживлена:
 * реальный список messages.getScheduledCalls через репозиторий раздела
 * (CallsSectionRepository.scheduled). Действия карточки «перенести/удалить/
 * начать сейчас» (messages.editCall/deleteScheduledCall/forceCallFinish) —
 * Этап Г по плану; здесь — список + честный empty-state с «Повторить».
 */
@Composable
fun CallsScheduledSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.scheduled.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsScheduledSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.SCHEDULED, force = false)
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
                        ScheduledCallCard(item = item, modifier = Modifier.testTag("scheduled_item"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledCallCard(
    item: ScheduledCallItem,
    modifier: Modifier = Modifier,
) {
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

        ScheduledCallItem(callId, name, dateLabel)
    } catch (e: Exception) {
        AppLog.e("CallsScheduledSection", "parseScheduledCall: запись пропущена", e)
        null
    }
}
