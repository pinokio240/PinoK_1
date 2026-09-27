// File: ui/screens/community/AdminEventsScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog

// ADMIN-EVENTS: «События» сообщества — панель уведомлений.
// READ  notifications.getRedesign {group_id, category}
// WRITE notifications.markAsViewed {group_id}

private data class EventCategory(val id: String, val label: String)

private val EVENT_CATEGORIES = listOf(
    EventCategory("comments", "Комментарии"),
    EventCategory("mentions", "Упоминания"),
    EventCategory("followers", "Подписчики"),
    EventCategory("suggested_posts", "Предложки"),
    EventCategory("from_vk", "От VK"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEventsScreen(groupId: Long, onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tabIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<VKApiClient.NotificationItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        val cat = EVENT_CATEGORIES[tabIndex].id
        scope.launch {
            loading = true
            error = null
            try {
                val pair = app.apiClient.notificationsGetRedesign(
                    count = 30,
                    startFrom = null,
                    groupId = groupId,
                    category = cat,
                )
                items = pair.first
                val err = app.apiClient.lastApiError
                error = if (items.isEmpty() && !err.isNullOrBlank()) err else null
            } catch (e: Exception) {
                AppLog.e("AdminEvents", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    fun markViewed() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = app.apiClient.notificationsMarkAsViewed(groupId)
                Toast.makeText(context, if (ok) "Отмечено просмотренным" else "Не удалось отметить", Toast.LENGTH_SHORT).show()
                if (ok) load()
            } catch (e: Exception) {
                AppLog.e("AdminEvents", "markViewed failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(groupId, tabIndex) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("События") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Button(onClick = { markViewed() }, enabled = !busy) {
                        Text("Прочитано")
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
                EVENT_CATEGORIES.forEachIndexed { idx, cat ->
                    Tab(
                        selected = tabIndex == idx,
                        onClick = { tabIndex = idx },
                        text = { Text(cat.label) },
                    )
                }
            }
            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> ErrorView(message = error, onRetry = { load() }, modifier = Modifier.padding(16.dp))
                items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Событий нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.uniqueKey }) { n ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(n.text.ifBlank { n.type }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (n.parentText.isNotBlank()) {
                                Text(n.parentText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
