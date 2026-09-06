package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import re.pinok.contracts.CallStarter
import re.pinok.contracts.ContainerRegistry
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-06): Этап Г/Г1 плана «звонки.перенос.план.md» — «Создать
 * звонок» с выбором адресата (REV-UI §6.2, CallByNameModal: поиск
 * calls_call_by_name_search, список items с фото 40 + имя, футер
 * «Аудиозвонок»/«Видеозвонок» calls_call_by_name_audiocall/videocall).
 *
 * Заменяет прежний CreateCallDialog CallsMainScreen (ручной ввод сырого peerId —
 * no-stub): адресат ищется реальным messagesSearchForCallTargets(query, 20)
 * (VK API messages.search{q, count:20, extended:1, fields}).
 *
 * Старт — через CallStarter хоста (ContainerRegistry.find<CallStarter>(),
 * паттерн CallsClusterRow/CallsHistorySection): video доносится до
 * messagesStartCall(peerId, video) через #CALLS-VIDEO-ROUTE (SovaNavHost →
 * Screen.Call → CallScreen). Стартера нет → честный отказ (Toast+лог), звонок
 * не имитируется.
 *
 * Рендерятся ТОЛЬКО обеспеченные фасадом контролы: поиск, список результатов,
 * аудио/видео-кнопки. Контролы реверса §6.2 БЕЗ API в фасаде НЕ рендерятся:
 * «от имени» (PROFILE/GROUP/ANONYM-select, getGroupsForCall), «вводимое имя»
 * с calls.checkParticipantName — для звонка АДРЕСАТУ они web-специфика
 * анонимного звонка по ссылке (см. CallsJoinByLinkDialog).
 */

/** Результат поиска адресата: peerId + имя + аватар + подтип (диалог/контакт). */
internal data class CallTargetItem(
    val peerId: Long,
    val name: String,
    val photo: String?,
    val kindLabel: String,
)

/**
 * Tolerant-парсинг items[] messages.search (extended=1): форма — смесь
 * {type, profile{…}|chat{…}} и плоских {peer_id|id|chat_id, title|first_name…}.
 * Подтип — «Контакт»/«Чат»/«Группа» (по type/знаку id).
 */
internal fun parseCallTarget(raw: JsonObject): CallTargetItem? {
    return try {
        val profileEl = raw.get("profile")
        val profile = if (profileEl != null && profileEl.isJsonObject) profileEl.asJsonObject else null
        val chatEl = raw.get("chat")
        val chat = if (chatEl != null && chatEl.isJsonObject) chatEl.asJsonObject else null
        val conversationEl = raw.get("conversation")
        val conversation = if (conversationEl != null && conversationEl.isJsonObject) conversationEl.asJsonObject else null

        fun primLong(obj: JsonObject?, key: String): Long {
            if (obj == null) return 0L
            val el = obj.get(key)
            if (el == null) return 0L
            if (!el.isJsonPrimitive) return 0L
            val v = el.asString.toLongOrNull()
            if (v == null) return 0L
            return v
        }

        fun primStr(obj: JsonObject?, key: String): String {
            if (obj == null) return ""
            val el = obj.get(key)
            if (el == null) return ""
            if (!el.isJsonPrimitive) return ""
            return el.asString
        }

        // peerId: profile.id → chat.id(+2000000000) → peer_id → conversation.peer{id|val} → id → user_id.
        var peerId = primLong(profile, "id")
        var isChat = false
        if (peerId == 0L && chat != null) {
            val chatId = primLong(chat, "id")
            if (chatId > 0L) {
                peerId = 2000000000L + chatId
                isChat = true
            }
        }
        if (peerId == 0L) {
            val p = primLong(raw, "peer_id")
            if (p != 0L) {
                peerId = p
                isChat = p > 2000000000L
            }
        }
        if (peerId == 0L && conversation != null) {
            val peerEl = conversation.get("peer")
            if (peerEl != null && peerEl.isJsonObject) {
                val pid = primLong(peerEl.asJsonObject, "id")
                if (pid != 0L) {
                    peerId = pid
                    isChat = pid > 2000000000L
                }
            } else if (peerEl != null && peerEl.isJsonPrimitive) {
                val pid = peerEl.asString.toLongOrNull()
                if (pid != null && pid != 0L) {
                    peerId = pid
                    isChat = pid > 2000000000L
                }
            }
        }
        if (peerId == 0L) {
            val p = primLong(raw, "id")
            if (p != 0L) {
                peerId = p
                isChat = p > 2000000000L
            }
        }
        if (peerId == 0L) peerId = primLong(raw, "user_id")
        if (peerId == 0L) return null

        // Имя: title (чат) → first_name+last_name → name.
        var name = primStr(chat, "title")
        if (name.isBlank()) {
            val fn = primStr(profile, "first_name").ifBlank { primStr(raw, "first_name") }
            val ln = primStr(profile, "last_name").ifBlank { primStr(raw, "last_name") }
            name = (fn + " " + ln).trim()
        }
        if (name.isBlank()) name = primStr(raw, "name")
        if (name.isBlank()) return null

        // Аватар: photo_200 → photo_100 → photo_50.
        val photo = primStr(chat, "photo_200")
            .ifBlank { primStr(chat, "photo_100") }
            .ifBlank { primStr(profile, "photo_100") }
            .ifBlank { primStr(raw, "photo_200") }
            .ifBlank { primStr(raw, "photo_100") }
            .ifBlank { primStr(raw, "photo_50") }

        // Подтип: type из ответа, иначе по форме id (peer-диапазон чатов VK).
        var kind = primStr(raw, "type")
        if (kind.isBlank()) kind = if (isChat || peerId > 2000000000L) "chat" else "user"
        val kindLabel = when (kind) {
            "user" -> "Контакт"
            "chat" -> "Чат"
            "group" -> "Группа"
            "email" -> "Email"
            else -> if (peerId > 2000000000L) "Чат" else "Контакт"
        }
        CallTargetItem(peerId, name, if (photo.isBlank()) null else photo, kindLabel)
    } catch (e: Exception) {
        AppLog.w("CallsCreateCall", "parseCallTarget: элемент пропущен (${e.message})")
        null
    }
}

/**
 * Модалка «Создать звонок»: поиск адресата + футер «Аудиозвонок»/«Видеозвонок».
 * Дебаунс поиска 400 мс; пустой запрос — подсказка, без результатов —
 * calls_call_by_name_not_found («Никого не нашли»).
 */
@Composable
fun CallsCreateCallDialog(onDismiss: () -> Unit) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CallTargetItem>>(emptyList()) }
    var selected by remember { mutableStateOf<CallTargetItem?>(null) }
    var searchError by remember { mutableStateOf("") }
    val callStarter = remember { ContainerRegistry.find<CallStarter>().firstOrNull() }

    // Дебаунс-поиск: 400 мс после остановки ввода, минимум 2 символа.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searchError = ""
            searching = false
            return@LaunchedEffect
        }
        searching = true
        searchError = ""
        delay(400)
        try {
            val items = withContext(Dispatchers.Default) {
                deps.apiClient.messagesSearchForCallTargets(q, 20)
            }
            results = items.mapNotNull { parseCallTarget(it) }.distinctBy { it.peerId }
            AppLog.i("CallsCreateCall", "search '$q': raw=${items.size} parsed=${results.size}")
        } catch (e: Exception) {
            AppLog.e("CallsCreateCall", "search error", e)
            results = emptyList()
            searchError = "Ошибка поиска: ${e.message}"
        } finally {
            searching = false
        }
    }

    fun startCall(video: Boolean) {
        val target = selected
        if (target == null) {
            AppLog.w("CallsCreateCall", "startCall: адресат не выбран")
            return
        }
        val starter = callStarter
        if (starter == null) {
            AppLog.w("CallsCreateCall", "startCall: CallStarter недоступен — звонок невозможен (peerId=${target.peerId})")
            Toast.makeText(context, "Звонки сейчас недоступны", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = starter.startCall(target.peerId, video)
        AppLog.i("CallsCreateCall", "startCall peerId=${target.peerId} video=$video -> $ok")
        if (ok) {
            onDismiss()
        } else {
            Toast.makeText(context, "Не удалось начать звонок", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать звонок") },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по имени (calls_call_by_name_search)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_call_by_name_search"),
                )
                Spacer(Modifier.height(8.dp))
                if (searchError.isNotBlank()) {
                    Text(
                        searchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                when {
                    query.trim().length < 2 -> {
                        Text(
                            "Введите имя или номер — минимум 2 символа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    searching -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    results.isEmpty() -> {
                        // calls_call_by_name_not_found (REV-UI §6.2).
                        Text(
                            "Никого не нашли",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("calls_call_by_name_not_found"),
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.height(280.dp)) {
                            items(results, key = { it.peerId }) { target ->
                                // #NULL-EXPLICIT: захват делегата в локальный val для сравнения.
                                val sel = selected
                                CallTargetRow(
                                    target = target,
                                    isSelected = sel != null && sel.peerId == target.peerId,
                                    onClick = { selected = target },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { startCall(video = false) },
                modifier = Modifier.testTag("calls_call_by_name_audiocall"),
            ) { Text("Аудиозвонок") }
        },
        dismissButton = {
            TextButton(
                enabled = selected != null,
                onClick = { startCall(video = true) },
                modifier = Modifier.testTag("calls_call_by_name_videocall"),
            ) { Text("Видеозвонок") }
        },
    )
}

/** Строка адресата: фото 40 + имя + подтип (диалог/контакт) + выделение. */
@Composable
private fun CallTargetRow(
    target: CallTargetItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("calls_call_by_name_item_${target.peerId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            val photo = target.photo
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Text(
                    target.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                target.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                target.kindLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
