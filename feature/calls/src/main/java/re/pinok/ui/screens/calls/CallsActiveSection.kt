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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
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

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А3 — секция «Активные» оживлена: данные
 * из репозитория раздела (CallsSectionRepository.active →
 * messagesGetCurrentCalls), кнопки карточки РЕАЛЬНЫЕ:
 *  - «Вернуться» — переход в звонок через существующий CallStarter-паттерн
 *    хоста (SovaNavHost onNavigateToCall → starter.startCall(peerId, video));
 *  - «Завершить» — vchat.hangupConversation{reason:"hungup"} тем же
 *    fallback-путём, что «Отклонить» в CallScreen (ensureCallsSessionKey →
 *    HTTP hangup, I/O на Dispatchers.Default), затем invalidateOnCallFinished()
 *    (#CALLS-SNAP) — активные/история/пропущенные перечитываются.
 */
@Composable
fun CallsActiveSection(onNavigateToCall: (Long) -> Unit) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val scope = rememberCoroutineScope()
    val state by repo.active.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsActiveSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.ACTIVE, force = false)
    }

    fun finishCall(item: ActiveCallItem) {
        scope.launch {
            val sk = deps.ensureCallsSessionKey(force = false)
            if (sk == null) {
                AppLog.w("CallsActiveSection", "Завершить: нет sessionKey — hangup не отправлен (${item.callId})")
            } else {
                try {
                    withContext(Dispatchers.Default) {
                        deps.apiClient.vchatHangupConversation(item.callId, sk, "hungup")
                    }
                    AppLog.i("CallsActiveSection", "hangup отправлен: ${item.callId}")
                } catch (e: Exception) {
                    AppLog.e("CallsActiveSection", "hangup failed: ${item.callId}", e)
                }
            }
            // #CALLS-SNAP: событийная инвалидация по завершении звонка
            repo.invalidateOnCallFinished()
        }
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет активных звонков",
        onRetry = { repo.refresh(CallsSectionKey.ACTIVE, force = true) },
        modifier = Modifier.testTag("active_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { it.parseActiveCallItem() } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет активных звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
            ) {
                items(items, key = { it.callId }) { item ->
                    ActiveCallItemCard(
                        item = item,
                        onReturn = { onNavigateToCall(item.peerId) },
                        onFinish = { finishCall(item) },
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
    onReturn: () -> Unit,
    onFinish: () -> Unit,
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
            // «Вернуться» — CallStarter-путь хоста (переход в звонок)
            IconButton(onClick = onReturn, modifier = Modifier.testTag("active_call_return")) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Вернуться в звонок",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // «Завершить» — vchat.hangupConversation(reason="hungup")
            IconButton(onClick = onFinish, modifier = Modifier.testTag("active_call_finish")) {
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
