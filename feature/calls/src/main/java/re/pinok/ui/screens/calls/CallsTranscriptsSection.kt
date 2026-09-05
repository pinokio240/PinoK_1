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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import re.pinok.util.toRelativeTime

private data class TranscriptItem(
    val id: Long,
    val title: String,
    val date: String,
    val preview: String,
)

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А3 — секция «Расшифровки звонков»
 * оживлена: реальный список calls.getAsrTranscriptions (план §2.2, §1.2 —
 * «Расшифровки звонков»; прежний феч был messages.getCallTranscriptions и
 * секция была мёртвой) через репозиторий раздела. Просмотр/правка/удаление
 * текста расшифровки — Этап В2 по плану; «Открыть» — прежний честный лог.
 */
@Composable
fun CallsTranscriptsSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.transcripts.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsTranscriptsSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.TRANSCRIPTS, force = false)
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "У вас нет расшифровок",
        onRetry = { repo.refresh(CallsSectionKey.TRANSCRIPTS, force = true) },
        modifier = Modifier.testTag("transcripts_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { it.parseTranscriptItem() } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "У вас нет расшифровок",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Расшифровки звонков",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        items.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("transcripts_list"),
                ) {
                    items(items, key = { it.id }) { item ->
                        TranscriptItemCard(
                            item = item,
                            onClick = { onNavigateToCall(item.id) },
                            modifier = Modifier.testTag("transcripts_item"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptItemCard(
    item: TranscriptItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    item.preview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onClick,
                modifier = Modifier.testTag("transcripts_open_button"),
            ) {
                Text("Открыть")
            }
        }
    }
}

private fun JsonObject.parseTranscriptItem(): TranscriptItem? {
    val id = get("id")?.asLong ?: return null
    val title = get("title")?.takeIf { !it.isJsonNull }?.asString ?: "Расшифровка звонка"
    val timestamp = get("date")?.asLong ?: (System.currentTimeMillis() / 1000)
    val date = timestamp.toRelativeTime()
    val preview = get("preview")?.takeIf { !it.isJsonNull }?.asString ?: ""
    return TranscriptItem(id, title, date, preview)
}
