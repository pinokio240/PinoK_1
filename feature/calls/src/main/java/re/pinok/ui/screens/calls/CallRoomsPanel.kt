package re.pinok.ui.screens.calls

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-ZH2 (2026-09-06, Этап Ж-2, Task 6-a) Ж9 ЗАЛЫ / брейкаут (Ж0 §8):
 * список залов / создание / переключение.
 *
 * WIRE (все signaling-методы добавлены АДДИТИВНО в CallSignalingClient, Task 6-a):
 *  - список: get-rooms {withParticipants} → type:"response" {rooms:{roomId, rooms:[Room]}}
 *    (Room-схема Ж0 §8: id/name/active/participantIds/...; парсинг TOLERANT —
 *    точная схема ответа не снапшотнута, Ж0 §13.3-подобная честность);
 *  - создание: update-rooms {rooms:[{name:"Зал N", participantCount:0}], assignRandomly:false}
 *    (bridge@819377; имя по умолчанию эталона calls_room_control_create_default_room_name);
 *  - переход: switch-room {toRoomId} / {toRoomId:null} — в основной зал (16131@307553);
 *  - открыть/закрыть (админ): activate-rooms {roomIds, deactivate} (bridge@830343/394008);
 *  - удалить (админ): remove-rooms {roomIds} (bridge@830722);
 *  - события: room-updated/rooms-updated/room-participants-updated → ре-запрос get-rooms
 *    (простая и честная синхронизация вместо парсинга диффов).
 *
 * НЕ РЕАЛИЗОВАНО (честные отклонения — no-stub):
 *  - перемещение ДРУГОГО участника update-rooms {id, addParticipantIds/removeParticipantIds} —
 *    админ-UI мультивыбора участников; без него панель покрывает prescribed scope
 *    «список/создание/переключение» (задание Task 6-a);
 *  - «попросить помощи» change-participant-state {drat:"1"} (bridge@809949) и объявление
 *    во все залы chat-message (bridge@828466) — нужны контролы статусов в залах;
 *  - таймер залов countdownSec — только передача значения в wire отсутствует за ненадобностью.
 *
 * ОЖИДАНИЕ ДАННЫХ: ответ correlation — по имени команды в type:"response"
 * ({response:"get-rooms"}); correlation по sequence невозможна (send() не возвращает seq —
 * существующий транспорт не трогали). Нет ответа за 5с → честная надпись «нет данных».
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */
private const val CALL_ROOMS_TAG = "CallRoomsPanel"

/** Зал для UI: [id]=null — не удалось распарсить id (команды по нему недоступны). */
internal data class CallRoomUi(
    val id: Long?,
    val name: String?,
    val active: Boolean?,
    val participantCount: Int,
)

// ─── #CALLS-ZH2: Tolerant-хелперы Gson (общие с CallWaitingHallPanel) ───
// #NULL-EXPLICIT: захват в val + if, БЕЗ ?./?: (в новом коде).

internal fun callJsonStr(o: JsonObject, key: String): String? {
    val el = o.get(key)
    if (el == null || !el.isJsonPrimitive) return null
    return el.asString
}

internal fun callJsonLong(o: JsonObject, key: String): Long? {
    val el = o.get(key)
    if (el == null || !el.isJsonPrimitive) return null
    return runCatching { el.asLong }.getOrNull()
}

internal fun callJsonBool(o: JsonObject, key: String): Boolean? {
    val el = o.get(key)
    if (el == null || !el.isJsonPrimitive) return null
    return runCatching { el.asBoolean }.getOrNull()
}

internal fun callJsonObj(o: JsonObject, key: String): JsonObject? {
    val el = o.get(key)
    if (el == null || !el.isJsonObject) return null
    return el.asJsonObject
}

internal fun callJsonArr(o: JsonObject, key: String): JsonArray? {
    val el = o.get(key)
    if (el == null || !el.isJsonArray) return null
    return el.asJsonArray
}

@Composable
internal fun CallRoomsPanel(
    signaling: CallSignalingClient,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rooms by remember { mutableStateOf<List<CallRoomUi>>(emptyList()) }
    // Текущий зал: null = основной (Ж0 §8: switch-room {toRoomId:null} = основной).
    var currentRoomId by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun requestRooms() {
        busy = true
        val ok = signaling.getRooms(withParticipants = false)
        AppLog.i(CALL_ROOMS_TAG, "get-rooms: отправлено=" + ok)
        if (!ok) busy = false
    }

    // Парсинг ответа get-rooms: {rooms:{roomId, rooms:[Room]}} ИЛИ {rooms:[Room]} (tolerant).
    fun parseRooms(json: JsonObject) {
        var parsedCurrent: Long? = null
        val list = ArrayList<CallRoomUi>()
        val roomsEl = json.get("rooms")
        var arr: JsonArray? = null
        if (roomsEl != null && roomsEl.isJsonArray) {
            arr = roomsEl.asJsonArray
        } else if (roomsEl != null && roomsEl.isJsonObject) {
            val wrapper = roomsEl.asJsonObject
            parsedCurrent = callJsonLong(wrapper, "roomId")
            arr = callJsonArr(wrapper, "rooms")
        }
        val a = arr
        if (a != null) {
            for (el in a) {
                if (!el.isJsonObject) continue
                val r = el.asJsonObject
                var rid = callJsonLong(r, "id")
                if (rid == null) {
                    // tolerant: id может прийти строкой
                    val idStr = callJsonStr(r, "id")
                    if (idStr != null) rid = idStr.toLongOrNull()
                }
                var pCount = 0
                val pids = callJsonArr(r, "participantIds")
                val part = callJsonObj(r, "participants")
                if (pids != null) {
                    pCount = pids.size()
                } else if (part != null) {
                    val inner = callJsonArr(part, "participants")
                    if (inner != null) pCount = inner.size()
                }
                list.add(
                    CallRoomUi(
                        id = rid,
                        name = callJsonStr(r, "name"),
                        active = callJsonBool(r, "active"),
                        participantCount = pCount,
                    )
                )
            }
        }
        rooms = list
        currentRoomId = parsedCurrent
        loaded = true
        busy = false
        AppLog.i(CALL_ROOMS_TAG, "get-rooms: залов=" + list.size + " текущий=" + parsedCurrent)
    }

    LaunchedEffect(Unit) {
        requestRooms()
        signaling.messages.collect { msg ->
            handleRoomsMessage(
                msg = msg,
                parseRooms = { j -> parseRooms(j) },
                requestRooms = { requestRooms() },
                setError = { t ->
                    errorText = t
                    busy = false
                },
            )
        }
    }

    // Честное «нет данных»: ответ не пришёл за 5с (не админ/нет залов/сервер молчит).
    LaunchedEffect(Unit) {
        delay(5_000)
        if (!loaded) {
            errorText = "Сервер не ответил на get-rooms (возможно, залы недоступны для этой роли)"
        }
    }

    fun afterAction(refresh: Boolean) {
        if (refresh) {
            scope.launch {
                delay(800)
                requestRooms()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF20203A),
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Залы",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }
                // Текущий зал: имя из списка, иначе #id (без ?./?: — #NULL-EXPLICIT).
                var currentLabel = "основной"
                val curId = currentRoomId
                if (curId != null) {
                    var matched: String? = null
                    for (r in rooms) {
                        val rId = r.id
                        if (rId != null && rId == curId) {
                            val nm = r.name
                            if (nm != null) matched = nm
                        }
                    }
                    if (matched != null) currentLabel = matched else currentLabel = "#" + curId
                }
                Text(
                    text = "Текущий зал: " + currentLabel,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(8.dp))

                val err = errorText
                if (err != null) {
                    Text(text = err, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (rooms.isEmpty() && !busy) {
                    Text(
                        text = "Залов нет",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
                for (room in rooms) {
                    val rid = room.id
                    val isCurrent = rid != null && rid == currentRoomId
                    // Имя может отсутствовать — честный fallback на id (без ?: — #NULL-EXPLICIT).
                    val nm = room.name
                    val title = if (nm != null) nm else ("Зал #" + rid)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFF43A047) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(26.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, color = Color.White, fontSize = 15.sp)
                            val statusLine = StringBuilder()
                            if (isCurrent) statusLine.append("вы здесь")
                            if (room.active == false) {
                                if (statusLine.isNotEmpty()) statusLine.append(" · ")
                                statusLine.append("закрыт")
                            }
                            statusLine.append(" · участников: ").append(room.participantCount)
                            Text(
                                text = statusLine.toString(),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                        // Переход: только в НЕтекущий зал с известным id.
                        if (rid != null && !isCurrent) {
                            TextButton(onClick = {
                                val ok = signaling.switchRoom(toRoomId = rid)
                                if (ok) {
                                    currentRoomId = rid
                                    afterAction(true)
                                } else {
                                    Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Перейти", color = Color(0xFF66BB6A)) }
                        } else if (rid == null) {
                            Text(
                                text = "id не распознан",
                                color = Color(0xFFEF9A9A),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }

                Spacer(Modifier.height(10.dp))

                // Создание зала: имя по умолчанию эталона «Зал N» (calls_room_control_create_default_room_name).
                TextButton(onClick = {
                    val ok = signaling.updateRoomsCreate(roomNames = listOf("Зал " + (rooms.size + 1)))
                    AppLog.i(CALL_ROOMS_TAG, "update-rooms(создать): отправлено=" + ok)
                    if (ok) afterAction(true) else Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
                }) { Text("＋ Создать зал", color = Color(0xFF66BB6A)) }

                // Админ-операции над ПЕРВЫМ залом списка (мультивыбор честно не реализован — KDoc).
                val firstRoom = rooms.firstOrNull()
                if (firstRoom != null) {
                    val firstId = firstRoom.id
                    if (firstId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val ok = signaling.activateRooms(roomIds = listOf(firstId), deactivate = false)
                            AppLog.i(CALL_ROOMS_TAG, "activate-rooms(open): отправлено=" + ok)
                            if (ok) afterAction(true)
                        }) { Text("Открыть", color = Color.White.copy(alpha = 0.8f)) }
                        TextButton(onClick = {
                            val ok = signaling.activateRooms(roomIds = listOf(firstId), deactivate = true)
                            AppLog.i(CALL_ROOMS_TAG, "activate-rooms(close): отправлено=" + ok)
                            if (ok) afterAction(true)
                        }) { Text("Закрыть", color = Color.White.copy(alpha = 0.8f)) }
                        TextButton(onClick = {
                            val ok = signaling.removeRooms(roomIds = listOf(firstId))
                            AppLog.i(CALL_ROOMS_TAG, "remove-rooms: отправлено=" + ok)
                            if (ok) afterAction(true)
                        }) { Text("Удалить", color = Color(0xFFEF9A9A)) }
                    }
                    }
                }

                // Возврат в основной зал (switch-room {toRoomId:null}) — когда мы в боковом.
                if (currentRoomId != null) {
                    TextButton(onClick = {
                        val ok = signaling.switchRoom(toRoomId = null)
                        AppLog.i(CALL_ROOMS_TAG, "switch-room(null=основной): отправлено=" + ok)
                        if (ok) {
                            currentRoomId = null
                            afterAction(true)
                        }
                    }) { Text("Вернуться в основной зал", color = Color(0xFF66BB6A)) }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Перемещение других участников, «попросить помощи» и объявления в залы не перенесены (детали — KDoc панели).",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * #CALLS-ZH2: обработчик сообщения сигналинга для панели залов. Отдельная НЕ-Composable
 * функция — collect живёт в LaunchedEffect, логика парсинга вне композиции.
 */
private suspend fun handleRoomsMessage(
    msg: CallSignalingClient.SignalingMessage,
    parseRooms: (JsonObject) -> Unit,
    requestRooms: () -> Unit,
    setError: (String?) -> Unit,
) {
    when (msg.command) {
        "response" -> {
            val respName = callJsonStr(msg.json, "response")
            if (respName == "get-rooms") {
                setError(null)
                parseRooms(msg.json)
            }
        }
        "error" -> {
            // Ошибка на наш get-rooms/update-rooms/…: корреляции по sequence нет (KDoc),
            // но панель отправляет команды последовательно — показываем честно.
            val errName = callJsonStr(msg.json, "error")
            setError("Сервер отклонил команду" + (if (errName != null) ": " + errName else ""))
            AppLog.w(CALL_ROOMS_TAG, "error от сервера: " + msg.json)
        }
        // События залов (Ж0 §1.2): ре-запрос — простая честная синхронизация состояния.
        "room-updated", "rooms-updated", "room-participants-updated" -> {
            AppLog.i(CALL_ROOMS_TAG, "событие залов: " + msg.command + " — ре-запрос get-rooms")
            requestRooms()
        }
        else -> {}
    }
}
