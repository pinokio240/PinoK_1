package re.pinok.ui.screens.calls

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.util.AppLog
import re.pinok.util.toRelativeTime
import re.pinok.util.toDurationString

enum class CallDir { INCOMING, OUTGOING, MISSED_INCOMING, MISSED_OUTGOING }

data class CallHistoryItem(
    val callId: String,
    val peerId: Long,
    val name: String,
    val photo: String?,
    val direction: CallDir,
    val timestamp: Long,
    val duration: Int,
)

@Composable
fun CallsHistorySection(onNavigateToCall: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<CallHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = false
    }

LaunchedEffect(Unit) {
        try {
            val deps = LocalCallsDeps.current
            val raw = deps.apiClient.callsGetHistory(30, offset = 0)
            var parsed = raw.mapNotNull { it.parseCallItem() }
            val uniquePeers = parsed.map { it.peerId }.distinct()
            if (uniquePeers.isNotEmpty()) {
                val profiles = try { deps.apiClient.usersGetByIds(uniquePeers) } catch (_: Exception) { emptyMap() }
                parsed = parsed.map { item ->
                    val p = profiles[item.peerId]
                    if (p != null) item.copy(name = "${p.firstName} ${p.lastName}".trim(), photo = p.photo100)
                    else item
                }
            }
            items = parsed
            AppLog.i("CallsHistorySection", "loaded ${items.size} items from calls.getHistory")
        } catch (e: Exception) {
            AppLog.e("CallsHistorySection", "load error", e)
            error = true
        } finally {
            loading = false
}
    }

    when {
        loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ошибка загрузки",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { load() }) {
                        Text("Повторить")
                    }
                }
            }
        }
        items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.callId }) { item ->
                    CallHistoryItemCard(
                        item = item,
                        onClick = { onNavigateToCall(item.peerId) },
                        modifier = Modifier.testTag("call_history_item"),
                    )
                }
            }
        }
    }
}

@Composable
fun CallHistoryItemCard(
    item: CallHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.photo != null) {
                    AsyncImage(
                        model = item.photo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(item.name.take(1), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (item.direction) {
                        CallDir.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF1976D2)
                        CallDir.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF4CAF50)
                        CallDir.MISSED_OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFFE53935)
                        CallDir.MISSED_INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFFE53935)
                    }
                    Icon(icon, null, Modifier.size(14.dp), tint = tint)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        item.timestamp.toRelativeTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.duration > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.duration.toDurationString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onClick() },
                    modifier = Modifier.testTag("call_history_audiocall"),
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Audio call", modifier = Modifier.size(24.dp))
                }
                IconButton(
                    onClick = { AppLog.i("CallHistoryItemCard", "video call clicked for ${item.peerId}") },
                    modifier = Modifier.testTag("call_history_videocall"),
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video call", modifier = Modifier.size(24.dp))
                }
                IconButton(
                    onClick = { AppLog.i("CallHistoryItemCard", "menu clicked for ${item.peerId}") },
                    modifier = Modifier.testTag("call_history_item_menu_button"),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

fun JsonObject.parseCallItem(): CallHistoryItem? {
    val callId = get("call_id")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
    val peerObj = get("peer")?.takeIf { it.isJsonObject }?.asJsonObject
    val peerId = peerObj?.get("id")?.asLong ?: return null
    val isInbound = get("is_inbound")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
    val isMissed = get("is_missed")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
    val dir = when {
        isMissed && isInbound -> CallDir.MISSED_INCOMING
        isMissed && !isInbound -> CallDir.MISSED_OUTGOING
        isInbound -> CallDir.INCOMING
        else -> CallDir.OUTGOING
    }
    val started = get("started_at")?.asLong ?: (System.currentTimeMillis() / 1000)
    val finished = get("finished_at")?.asLong ?: 0L
    val duration = if (finished > started) ((finished - started) / 60).toInt() else 0
    return CallHistoryItem(callId, peerId, "Пользователь", null, dir, started, duration)
}