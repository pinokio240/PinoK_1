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
import androidx.compose.material.icons.filled.Videocam
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
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.data.model.UserProfile
import re.pinok.util.AppLog

@Composable
fun CallsFriendsSection(onNavigateToCall: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = false
    }

    LaunchedEffect(Unit) {
        try {
            val deps = LocalCallsDeps.current
            val result = deps.apiClient.friendsGetOnline(userId = null)
            items = result
            AppLog.i("CallsFriendsSection", "loaded ${items.size} online friends")
        } catch (e: Exception) {
            AppLog.e("CallsFriendsSection", "load error", e)
            error = true
        } finally {
            loading = false
        }
    }

    when {
        loading -> {
            Box(Modifier.fillMaxSize().testTag("friends_loading"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error -> {
            Box(Modifier.fillMaxSize().testTag("friends_error"), contentAlignment = Alignment.Center) {
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
            Box(Modifier.fillMaxSize().testTag("friends_empty"), contentAlignment = Alignment.Center) {
                Text(
                    "Нет друзей онлайн",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.testTag("friends_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { friend ->
                    FriendOnlineCard(
                        friend = friend,
                        onAudioCall = { onNavigateToCall(friend.id) },
                        onVideoCall = { onNavigateToCall(friend.id) },
                        modifier = Modifier.testTag("friend_item"),
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendOnlineCard(
    friend: UserProfile,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onAudioCall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (friend.photo100 != null) {
                        AsyncImage(
                            model = friend.photo100,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(friend.firstName.take(1), fontWeight = FontWeight.Bold)
                    }
                }
                if (friend.isOnline) {
                    Box(
                        modifier = Modifier.size(14.dp).clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friend.fullName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "В сети",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                )
            }
            IconButton(onClick = onAudioCall) {
                Icon(Icons.Filled.Call, contentDescription = "Аудиозвонок", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Filled.Videocam, contentDescription = "Видеозвонок", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}