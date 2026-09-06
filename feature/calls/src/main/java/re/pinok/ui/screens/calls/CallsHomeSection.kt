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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import android.widget.Toast
import re.pinok.data.model.UserProfile
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.CallsSectionStatus
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-05): Этап А3 — секция «Главная» оживлена (была мёртвой
 * с пустыми кнопками): друзья (существующий friends-метод фасада
 * friendsGetOnline) + последние звонки из репозитория раздела (А2).
 * Кнопки «Создать звонок»/«Присоединиться» открывают существующие диалоги
 * шапки (callback'и CallsMainScreen); «Показать всех» → таб «Позвонить
 * друзьям». Промо-баннеры «залы»/«vmoji» — прежние (§1.2).
 *
 * #CALLS-SNAP (2026-09-05): Этап Д — FCPanel (панель быстрых звонковых
 * чатов, REV-UI §1.5) над «Последними звонками»: плитки последних звонковых
 * чатов из HISTORY-состояния репозитория (CallChatPanel.kt); тап по плитке —
 * CallChatScreen (полноэкранный Dialog, callsGetConversationByCall, маршруты
 * не добавляются — прецедент CallsRecordingPlayer/CallsTranscriptViewer);
 * крестик плитки «Убрать чат из списка» — repo.removeFromHistory
 * (callsDeleteHistoryRecords / callsDeleteGroupHistoryRecords при groupId>0
 * + форс-обновление списков репозиторием) + честный Toast результата.
 * Прежние элементы секции не тронуты — панель ДОБАВЛЕНА, не заменяет.
 */
private const val HOME_PREVIEW_COUNT = 5

@Composable
fun CallsHomeSection(
    onNavigateToCall: (Long) -> Unit,
    onCreateCall: () -> Unit,
    onJoin: () -> Unit,
    onOpenFriends: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyState by repo.history.collectAsState()

    LaunchedEffect(Unit) {
        repo.refresh(CallsSectionKey.HISTORY, force = false) // кэш-семантика репозитория
    }

    // Друзья — собственный феч (friendsGetOnline); retryKey делает «Повторить»
    // рабочим (LaunchedEffect перезапускается — известный баг старых секций).
    var friends by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var friendsLoading by remember { mutableStateOf(true) }
    var friendsError by remember { mutableStateOf(false) }
    var friendsRetryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(friendsRetryKey) {
        friendsLoading = true
        friendsError = false
        try {
            friends = deps.apiClient.friendsGetOnline(userId = null)
            AppLog.i("CallsHomeSection", "loaded ${friends.size} online friends")
        } catch (e: Exception) {
            AppLog.e("CallsHomeSection", "friends load error", e)
            friendsError = true
        } finally {
            friendsLoading = false
        }
    }

    // Этап Д: открытый чат плитки FCPanel (callId записи + данные шапки плитки).
    var openChatCallId by remember { mutableStateOf<String?>(null) }
    var openChatTitle by remember { mutableStateOf<String?>(null) }
    var openChatPhoto by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_section")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Звонки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Звоните друзьям и создавайте видеоконференции",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCreateCall,
                modifier = Modifier.weight(1f).testTag("home_create_call"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Создать звонок")
            }
            OutlinedButton(
                onClick = onJoin,
                modifier = Modifier.weight(1f).testTag("home_join"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Присоединиться")
            }
        }

        // ─── FCPanel: панель быстрых звонковых чатов (REV-UI §1.5, Этап Д) ───
        val hs = historyState
        CallsFastChatsPanel(
            tiles = remember(hs.items) { buildFastChatTiles(hs.items) },
            onOpenChat = { tile ->
                openChatCallId = tile.callId
                openChatTitle = tile.name
                openChatPhoto = tile.photo
            },
            onRemoveChat = { tile ->
                scope.launch {
                    // repo.removeFromHistory: callsDeleteHistoryRecords(ids) либо
                    // callsDeleteGroupHistoryRecords(ids, groupId) при groupId>0,
                    // после успеха — форс-обновление HISTORY+MISSED (impl репозитория).
                    val ids = ArrayList<Long>()
                    if (tile.recordId > 0L) ids.add(tile.recordId)
                    val ok = repo.removeFromHistory(ids, tile.groupId)
                    if (ok) {
                        Toast.makeText(context, "Чат убран из списка", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не удалось убрать чат из списка", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )

        // ─── Последние звонки (репозиторий А2, общий вид строки А4) ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Последние звонки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        when (hs.status) {
            CallsSectionStatus.LOADING -> Box(
                Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
            CallsSectionStatus.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "История недоступна",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { repo.refresh(CallsSectionKey.HISTORY, force = true) }) {
                    Text("Повторить")
                }
            }
            CallsSectionStatus.EMPTY -> Text(
                "Пока нет звонков",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CallsSectionStatus.CONTENT -> {
                val preview = hs.items.take(HOME_PREVIEW_COUNT)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (entry in preview) {
                        CallsClusterRow(
                            entry = entry,
                            onClick = { onNavigateToCall(entry.peerId) },
                            modifier = Modifier.testTag("home_history_item"),
                        )
                    }
                }
            }
        }

        // ─── Друзья онлайн (friendsGetOnline — существующий метод фасада) ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Друзья онлайн",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenFriends, modifier = Modifier.testTag("home_show_all_friends")) {
                Text("Показать всех")
            }
        }
        when {
            friendsLoading -> Box(
                Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
            friendsError -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Не удалось загрузить друзей",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { friendsRetryKey++ },
                    modifier = Modifier.testTag("home_friends_retry"),
                ) { Text("Повторить") }
            }
            friends.isEmpty() -> Text(
                "Нет друзей онлайн",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (friend in friends.take(HOME_PREVIEW_COUNT)) {
                        HomeFriendRow(
                            friend = friend,
                            onAudioCall = { onNavigateToCall(friend.id) },
                            onVideoCall = { onNavigateToCall(friend.id) },
                        )
                    }
                }
            }
        }

        // ─── Промо-баннеры (§1.2 «залы»/«vmoji») — прежний контент ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Создавайте залы на встречи",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Приглашайте участников по ссылке",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Поделиться звонком через vmoji",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Отправьте ссылку на звонок с анимацией",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    // Этап Д: чат звонка — полноэкранный Dialog внутри секции (маршруты
    // не добавляются; hall_id в записях истории отсутствует — вызов без зала).
    val openCallId = openChatCallId
    if (openCallId != null) {
        CallChatScreen(
            callId = openCallId,
            hallId = null,
            title = openChatTitle,
            photoUrl = openChatPhoto,
            onDismiss = {
                openChatCallId = null
                openChatTitle = null
                openChatPhoto = null
            },
        )
    }
}

/** Компактная строка друга для «Главной»: аватар/инициалы + имя + кнопки звонка. */
@Composable
private fun HomeFriendRow(
    friend: UserProfile,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAudioCall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
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
        Spacer(Modifier.width(12.dp))
        Text(
            friend.fullName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAudioCall, modifier = Modifier.size(36.dp).testTag("home_friend_audio")) {
            Icon(Icons.Filled.Call, contentDescription = "Аудиозвонок", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onVideoCall, modifier = Modifier.size(36.dp).testTag("home_friend_video")) {
            Icon(Icons.Filled.Videocam, contentDescription = "Видеозвонок", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
