package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import re.pinok.SovaApp
import re.pinok.util.AppLog

@Composable
fun CallsMissedSection(onNavigateToCall: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<CallHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val raw = SovaApp.get().apiClient.callsGetHistory(30)
            var parsed = raw.mapNotNull { it.parseCallItem() }.filter { it.direction.name.startsWith("MISSED") }
            val uniqueIds = parsed.map { it.peerId }.distinct()
            if (uniqueIds.isNotEmpty()) {
                val names = SovaApp.get().apiClient.usersGetByIds(uniqueIds)
                parsed = parsed.map { item ->
                    val user = names[item.peerId]
                    if (user != null) item.copy(name = "${user.firstName} ${user.lastName}".trim())
                    else item
                }
            }
            items = parsed
            AppLog.i("CallsMissedSection", "loaded ${items.size} missed items")
        } catch (e: Exception) {
            AppLog.e("CallsMissedSection", "load error", e)
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
                    Text("Ошибка загрузки", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { loading = true; error = false }) { Text("Повторить") }
                }
            }
        }
        items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет пропущенных звонков", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.callId }) { item ->
                    CallHistoryItemCard(
                        item = item,
                        onClick = { onNavigateToCall(item.peerId) },
                        modifier = Modifier.testTag("missed_call_item"),
                    )
                }
            }
        }
    }
}