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
import androidx.compose.material.icons.filled.Group
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
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-ZH2 (2026-09-06, Этап Ж-2, Task 6-a) Ж8 ЗАЛ ОЖИДАНИЯ (Ж0 §7):
 * панель ожидающих — админ-функции впуска/отказа.
 *
 * WIRE (signaling-методы добавлены АДДИТИВНО в CallSignalingClient, Task 6-a):
 *  - список: get-waiting-hall {count, backward} → type:"response"
 *    {totalCount, participants:[…]} (16131@309933 getWaitingHall). Схема элемента
 *    восстановлена по использованию эталона (.peerId/.name — Ж0 §13.3) — парсинг
 *    TOLERANT: participantId → id → peerId.id → peerId(строка); имя → name → peerId.name;
 *  - впустить: promote-participant {participantId} (16131@309678);
 *  - отказ: remove-participant {participantId, ban:false} (Ж0 §6.3/§7, kickType
 *    WAITING_HALL — чисто UI-различие);
 *  - уведомление promote-participant {demote?} → ре-запрос списка (кто-то впущен/выгнан).
 *
 * ЧЕСТНЫЕ ОГРАНИЧЕНИЯ:
 *  - профили ожидающих эталон догружает messages.getCallParticipants-стиль
 *    (bridge@320074-323150) — схема догрузки не восстановлена; показываем то, что
 *    есть в ответе (name/peerId), аватары НЕ грузим (no-stub);
 *  - пагинация fromId — одна пачка (count=50) + кнопка «Обновить»; курсорная догрузка
 *    не реализована (формат курсора не снапшотнут);
 *  - прав админа локально не проверяем — сервер отклонит не-админу (type:"error"
 *    показывается текстом в панели).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */
private const val CALL_WAITING_TAG = "CallWaitingHallPanel"

/** Ожидающий для UI: [participantId] — строка-провайдер promote/remove-participant. */
internal data class CallWaitingUi(
    val participantId: String,
    val name: String?,
)

@Composable
internal fun CallWaitingHallPanel(
    signaling: CallSignalingClient,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var waiting by remember { mutableStateOf<List<CallWaitingUi>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun requestWaiting() {
        val ok = signaling.getWaitingHall(count = 50)
        AppLog.i(CALL_WAITING_TAG, "get-waiting-hall: отправлено=" + ok)
        if (!ok) {
            errorText = "Сигналинг закрыт — список недоступен"
        }
    }

    // Tolerant-парсинг ответа get-waiting-hall (Ж0 §7/§13.3).
    fun parseWaiting(json: JsonObject) {
        val list = ArrayList<CallWaitingUi>()
        val arr = callJsonArr(json, "participants")
        if (arr != null) {
            for (el in arr) {
                if (!el.isJsonObject) continue
                val p = el.asJsonObject
                var pid = callJsonStr(p, "participantId")
                if (pid == null) pid = callJsonStr(p, "id")
                if (pid == null) {
                    // peerId может быть объектом {id, type} или строкой (Ж0 §13.3).
                    val peerEl = p.get("peerId")
                    if (peerEl != null && peerEl.isJsonObject) {
                        val peerObj = peerEl.asJsonObject
                        val peerIdNum = callJsonLong(peerObj, "id")
                        if (peerIdNum != null) {
                            pid = peerIdNum.toString()
                        } else {
                            pid = callJsonStr(peerObj, "id")
                        }
                    } else if (peerEl != null && peerEl.isJsonPrimitive) {
                        pid = peerEl.asString
                    }
                }
                if (pid == null) {
                    // tolerant: participantId может прийти числом
                    val pidNum = callJsonLong(p, "participantId")
                    if (pidNum != null) pid = pidNum.toString()
                }
                if (pid == null) {
                    AppLog.w(CALL_WAITING_TAG, "participantId не распознан в элементе зала ожидания — пропущен")
                    continue
                }
                var name = callJsonStr(p, "name")
                if (name == null) {
                    val peerEl2 = p.get("peerId")
                    if (peerEl2 != null && peerEl2.isJsonObject) {
                        name = callJsonStr(peerEl2.asJsonObject, "name")
                    }
                }
                list.add(CallWaitingUi(participantId = pid, name = name))
            }
        }
        waiting = list
        val tc = callJsonLong(json, "totalCount")
        if (tc != null) totalCount = tc.toInt()
        loaded = true
        AppLog.i(CALL_WAITING_TAG, "get-waiting-hall: totalCount=" + tc + " распознано=" + list.size)
    }

    LaunchedEffect(Unit) {
        requestWaiting()
        signaling.messages.collect { msg ->
            when (msg.command) {
                "response" -> {
                    val respName = callJsonStr(msg.json, "response")
                    if (respName == "get-waiting-hall") {
                        errorText = null
                        parseWaiting(msg.json)
                    }
                }
                "error" -> {
                    val errName = callJsonStr(msg.json, "error")
                    errorText = "Сервер отклонил команду" + (if (errName != null) ": " + errName else " (возможно, нужна роль админа)")
                    AppLog.w(CALL_WAITING_TAG, "error от сервера: " + msg.json)
                }
                // Ж0 §7: баннер админу о новых ждущих / demote-уведомления — ре-запрос.
                "promote-participant" -> {
                    AppLog.i(CALL_WAITING_TAG, "promote-participant уведомление — ре-запрос списка")
                    requestWaiting()
                }
                "session-state" -> {
                    // Ж0 §7: данные о ждущих приходят и через session-state (bridge@322850).
                    requestWaiting()
                }
                else -> {}
            }
        }
    }

    // Честное «нет данных» при молчании сервера.
    LaunchedEffect(Unit) {
        delay(5_000)
        if (!loaded && errorText == null) {
            errorText = "Сервер не ответил на get-waiting-hall (возможно, зал ожидания не включён для звонка)"
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
                        text = "Зал ожидания",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }
                Text(
                    text = "Ожидают: " + totalCount,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(8.dp))

                val err = errorText
                if (err != null) {
                    Text(text = err, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (waiting.isEmpty() && loaded && err == null) {
                    Text(
                        text = "Никого нет в зале ожидания",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
                for (w in waiting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(26.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // Имя может быть не в ответе (Ж0 §13.3) — честный fallback на id.
                            val label = w.name
                            if (label != null) {
                                Text(text = label, color = Color.White, fontSize = 15.sp)
                                Text(
                                    text = "id " + w.participantId,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                )
                            } else {
                                Text(text = "Участник " + w.participantId, color = Color.White, fontSize = 15.sp)
                            }
                        }
                        TextButton(onClick = {
                            val ok = signaling.promoteParticipant(participantId = w.participantId)
                            AppLog.i(CALL_WAITING_TAG, "promote-participant(" + w.participantId + "): отправлено=" + ok)
                            if (ok) {
                                scope.launch {
                                    delay(800)
                                    requestWaiting()
                                }
                            } else {
                                Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Впустить", color = Color(0xFF66BB6A)) }
                        TextButton(onClick = {
                            // Отказ = remove-participant {ban:false} (Ж0 §6.3/§7).
                            val ok = signaling.removeParticipant(participantId = w.participantId, ban = false)
                            AppLog.i(CALL_WAITING_TAG, "remove-participant(отказ, " + w.participantId + "): отправлено=" + ok)
                            if (ok) {
                                scope.launch {
                                    delay(800)
                                    requestWaiting()
                                }
                            } else {
                                Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Отклонить", color = Color(0xFFEF9A9A)) }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { requestWaiting() }) {
                    Text("Обновить", color = Color.White.copy(alpha = 0.8f))
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Догрузка профилей ожидающих и курсорная пагинация не перенесены (схема не снапшотнута — Ж0 §13.3; детали — KDoc панели).",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
