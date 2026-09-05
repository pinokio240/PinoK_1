package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toDurationString
import re.pinok.util.toRelativeTime

private data class RecordingItem(
    val id: Long,
    val title: String,
    val duration: String,
    val views: Int,
    val timeAgo: String,
)

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А3 — секция «Записи звонков» оживлена:
 * реальный список messages.getCallRecordings через репозиторий раздела
 * (CallsSectionRepository.recordings) вместо мёртвого remember{}-феча.
 * Плеер записи/инкремент просмотров/действия (скачать/копировать/удалить) —
 * Этап В по плану; кнопки карточки — прежние (лог), честно не имитируют
 * воспроизведение.
 */
@Composable
fun CallsRecordingsSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.recordings.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsRecordingsSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.RECORDINGS, force = false)
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет записей звонков",
        onRetry = { repo.refresh(CallsSectionKey.RECORDINGS, force = true) },
        modifier = Modifier.testTag("recordings_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { it.parseRecordingItem() } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет записей звонков",
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
                        "Записи звонков",
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("recordings_grid"),
                ) {
                    items(items, key = { it.id }) { item ->
                        RecordingItemCard(
                            item = item,
                            modifier = Modifier.testTag("recordings_item"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingItemCard(
    item: RecordingItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(36.dp).testTag("recordings_play_button"),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp).testTag("recordings_duration_badge"),
                ) {
                    Text(
                        item.duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { AppLog.i("RecordingItemCard", "edit ${item.id}") },
                        modifier = Modifier.size(28.dp).testTag("recordings_edit"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { AppLog.i("RecordingItemCard", "download ${item.id}") },
                        modifier = Modifier.size(28.dp).testTag("recordings_download"),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { AppLog.i("RecordingItemCard", "delete ${item.id}") },
                        modifier = Modifier.size(28.dp).testTag("recordings_delete"),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        item.views.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun JsonObject.parseRecordingItem(): RecordingItem? {
    val id = get("id")?.asLong ?: return null
    val title = get("title")?.takeIf { !it.isJsonNull }?.asString ?: "Запись звонка"
    val durationSec = get("duration")?.asInt ?: 0
    val duration = durationSec.toDurationString()
    val views = get("views")?.asInt ?: 0
    val timestamp = get("date")?.asLong ?: (System.currentTimeMillis() / 1000)
    val timeAgo = timestamp.toRelativeTime()
    return RecordingItem(id, title, duration, views, timeAgo)
}
