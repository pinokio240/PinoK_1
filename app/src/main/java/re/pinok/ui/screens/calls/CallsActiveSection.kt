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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import re.pinok.SovaApp
import re.pinok.util.AppLog

private data class ActiveCallItem(
    val callId: String,
    val peerId: Long,
    val userIds: List<Long>,
    val startTime: Long,
    val state: String,
    val name: String,
    val photo: String?,
)

@Composable
fun CallsActiveSection(onNavigateToCall: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<ActiveCallItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = false
    }

    LaunchedEffect(Unit) {
        try {
            val raw = SovaApp.get().apiClient.messagesGetCurrentCalls()
            items = raw.mapNotNull { it.parseActiveCallItem() }
            AppLog.i("CallsActiveSection", "loaded ${items.size} items")
        } catch (e: Exception) {
            AppLog.e("CallsActiveSection", "load error", e)
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
                    "Нет активных звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.callId }) { item ->
                    ActiveCallItemCard(
                        item = item,
                        onClick = { onNavigateToCall(item.peerId) },
                        modifier = Modifier.testTag("active_call_item"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCallItemCard(
    item: ActiveCallItem,
    onClick: () -> Unit,
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
                    val statusColor = when (item.state) {
                        "active" -> Color(0xFF4CAF50)
                        "calling" -> Color(0xFF1976D2)
                        "holding" -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val statusText = when (item.state) {
                        "active" -> "Разговор"
                        "calling" -> "Вызов..."
                        "holding" -> "Ожидание"
                        else -> item.state
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Filled.CallEnd,
                    contentDescription = "Завершить",
                    tint = Color(0xFFE53935),
                )
            }
        }
    }
}

private fun JsonObject.parseActiveCallItem(): ActiveCallItem? {
    val callIdEl = get("call_id")
    val callId = if (callIdEl != null && !callIdEl.isJsonNull) callIdEl.asString else null
    if (callId == null) return null

    val peerIdEl = get("peer_id")
    val peerId = if (peerIdEl != null && !peerIdEl.isJsonNull) peerIdEl.asLong else 0L

    val userIdsEl = get("user_ids")
    val userIds = if (userIdsEl != null && !userIdsEl.isJsonNull) {
        val arr = userIdsEl.asJsonArray
        val result = mutableListOf<Long>()
        for (i in 0 until arr.size()) {
            val el = arr[i]
            if (el != null && el.isJsonPrimitive) {
                result.add(el.asLong)
            }
        }
        result
    } else {
        emptyList()
    }

    val startTimeEl = get("start_time")
    val startTime = if (startTimeEl != null && !startTimeEl.isJsonNull) startTimeEl.asLong else 0L

    val stateEl = get("state")
    val state = if (stateEl != null && !stateEl.isJsonNull) stateEl.asString else ""

    val nameEl = get("name")
    val name = if (nameEl != null && !nameEl.isJsonNull) nameEl.asString else "Пользователь"

    val photoEl = get("photo")
    val photo = if (photoEl != null && !photoEl.isJsonNull) photoEl.asString else null

    return ActiveCallItem(callId, peerId, userIds, startTime, state, name, photo)
}