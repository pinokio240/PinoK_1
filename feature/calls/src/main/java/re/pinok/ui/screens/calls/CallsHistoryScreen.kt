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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.CallsDependencies
import re.pinok.util.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsHistoryScreen(
    onCallClick: (peerId: Long, title: String, photo: String?) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
) {
    val deps = LocalCallsDeps.current
    val scope = rememberCoroutineScope()
    var currentCalls by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var inboundCalls by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=текущие, 1=история

    LaunchedEffect(Unit) {
        try {
            val cur = app.apiClient.messagesGetCurrentCalls()
            val inb = app.apiClient.messagesGetInboundCalls(30)
            currentCalls = cur
            inboundCalls = inb
            AppLog.i("CallsHistory", "current=${cur.size} inbound=${inb.size}")
        } catch (e: Exception) {
            AppLog.e("CallsHistory", "load error", e)
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Звонки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (activeTab == 0) "Текущие звонки" else "История звонков",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clickable { activeTab = 0 },
                    )
                    Text(
                        if (activeTab == 1) "История звонков" else "Текущие",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { activeTab = 1 },
                    )
                }
                val items = if (activeTab == 0) currentCalls else inboundCalls
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhoneDisabled, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Нет звонков", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (activeTab == 0) "VK API не возвращает текущие звонки для web-токена"
                                else "История звонков доступна только через VK API",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items, key = { it.hashCode().toString() }) { item ->
                            val peerId = item.get("peer_id")?.asLong ?: 0L
                            val name = item.get("name")?.asString ?: "Пользователь"
                            val photo = item.get("photo")?.takeIf { !it.isJsonNull }?.asString
                            val status = item.get("status")?.asString ?: ""
                            val direction = if (item.get("incoming")?.asInt == 1) "входящий" else "исходящий"
                            CallHistoryCard(
                                name = name,
                                photo = photo,
                                time = status,
                                direction = direction,
                                onClick = { },
                                onCallClick = { onCallClick(peerId, name, photo) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryCard(
    name: String,
    photo: String?,
    time: String,
    direction: String,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                if (photo != null) {
                    AsyncImage(model = photo, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(name.take(1), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (direction == "входящий") Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                        null, Modifier.size(14.dp),
                        tint = if (direction == "входящий") Color(0xFF4CAF50) else Color(0xFF1976D2),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(time, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onCallClick) {
                Icon(Icons.Filled.Call, contentDescription = "Позвонить", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}