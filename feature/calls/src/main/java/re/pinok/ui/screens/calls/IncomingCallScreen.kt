package re.pinok.ui.screens.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.CallsDependencies
import re.pinok.media.ConversationParamsDecoder
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-INCOMING-UI (Этап Е плана «звонки.перенос.план.md» §4-Е; реверс UI-срезов §8.1):
 * полноэкранный экран входящего звонка — показывается ПЕРЕД входом в CallScreen.
 *
 * Реверс-соответствия (§8.1, webCallsBridge):
 *  - h1 calls_incoming_call_title = имя звонящего; desc calls_incoming_audiocall/_videocall =
 *    «Аудиозвонок/Видеозвонок». ВИДЕО-флаг входящего до offer НЕИЗВЕСТЕН (LP-115 payload —
 *    conversation params без media-флагов; видеостатус появляется только с offer —
 *    CallScreen:1003). Поэтому подзаголовок честно нейтральный («Входящий звонок»);
 *    «Принять с видео» (calls_incoming_call_reply_video) НЕ рендерится — no-stub:
 *    камера не перенесена (план §1.7: «❌ камера — Этап 2.2 старого плана», in-call — Этап Ж).
 *  - calls_incoming_call_collapse → кнопка «Свернуть» (правый верхний угол) → свёрнутый
 *    баннер [IncomingCallBanner]; системный Back — тоже свёртывание (web закрывает
 *    входящий с подтверждением SHOW_CAUTION_BOX; «закрыть» входящий без решения нельзя,
 *    свёртывание — честный Android-эквивалент).
 *  - decline → WS hangup REJECTED (эталон Conversation.js DECLINE_INCOMING);
 *    «Занят» → WS hangup BUSY — см. [performIncomingDecline].
 *  - Отмена звонящим: web eventBus incoming_call_canceled (§8.5). До accept своей
 *    сигналинг-сессии нет → факт отмены добирается опросом messagesGetCurrentCalls
 *    (дважды пусто подряд = звонок снят сервером). Отдельного локального таймаута
 *    «неотвеченный» НЕТ (в реверсе факт не зафиксирован) — сервер снимает сам.
 *
 * Аватар/имя: хост (SovaApp.refreshIncomingCaller) подтягивает их через
 * messagesGetCurrentCalls → usersGetByIds; если к моменту показа пусто — экран
 * добирает профиль сам (usersGetByIds по peer) тем же API.
 */
@Composable
fun IncomingCallScreen(
    peerId: Long,
    title: String,
    photo: String?,
    payload: String?,
    deps: CallsDependencies,
    onAccept: () -> Unit,
    onDone: () -> Unit,
    onCollapse: () -> Unit,
) {
    var peerName by remember { mutableStateOf(title) }
    var peerPhoto by remember { mutableStateOf(photo) }
    var declineBusy by remember { mutableStateOf(false) }
    var declineError by remember { mutableStateOf<String?>(null) }
    var remoteCanceled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Back = свернуть в баннер (не «закрыть» — входящий требует решения).
    BackHandler(enabled = true) { onCollapse() }

    // #CALLS-NAME-FIX-Е: имя/аватар могли не успеть (refreshIncomingCaller — async).
    // Добираем usersGetByIds по peer (тот же API, что хост).
    LaunchedEffect(peerId, title) {
        if (title.isBlank() && peerId > 0L) {
            val profiles = withContext(Dispatchers.IO) {
                runCatching { deps.apiClient.usersGetByIds(listOf(peerId)) }.getOrNull()
            }
            if (profiles != null) {
                val profile = profiles[peerId]
                if (profile != null) {
                    val name = (profile.firstName + " " + profile.lastName).trim()
                    if (name.isNotBlank()) peerName = name.take(40)
                    val p = profile.photo100
                    if (p != null && peerPhoto == null) peerPhoto = p
                }
            }
        }
    }

    // Отмена звонящим (web: incoming_call_canceled): messagesGetCurrentCalls пуст
    // ДВАЖДЫ подряд (старт через 6с + шаг 5с — защита от гонки на старте звонка и
    // от сетевых ошибок; ошибка опроса не считается отменой).
    LaunchedEffect(peerId) {
        delay(6000)
        var emptyStreak = 0
        while (emptyStreak < 2) {
            val items = withContext(Dispatchers.IO) {
                runCatching { deps.apiClient.messagesGetCurrentCalls() }.getOrNull()
            }
            if (items == null) {
                emptyStreak = 0
            } else {
                if (items.isEmpty()) emptyStreak += 1 else emptyStreak = 0
            }
            if (emptyStreak >= 2) break
            delay(5000)
        }
        if (emptyStreak >= 2) {
            AppLog.i("IncomingCall", "Входящий снят сервером (getCurrentCalls пуст) — «Звонок отменён»")
            remoteCanceled = true
        }
    }

    // Показ «Звонок отменён» 2с → хост сбрасывает pending (оверлей уходит).
    LaunchedEffect(remoteCanceled) {
        if (remoteCanceled) {
            delay(2000)
            onDone()
        }
    }

    fun decline(reason: String) {
        if (declineBusy) return
        declineBusy = true
        declineError = null
        scope.launch {
            AppLog.i("IncomingCall", "Отклонение входящего: reason=$reason")
            val ok = performIncomingDecline(deps, payload, reason)
            if (ok) {
                AppLog.i("IncomingCall", "Отклонение доставлено (reason=$reason)")
                onDone()
            } else {
                AppLog.w("IncomingCall", "Отклонение НЕ подтверждено (reason=$reason)")
                declineBusy = false
                declineError = "Не удалось отклонить — проверьте сеть и попробуйте снова"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10131A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Верх: «Свернуть» (calls_collapse, §8.1).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = Color(0xFFB0B8C8),
                    )
                }
            }
            // Центр: аватар + имя + статус.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val photoVal = peerPhoto
                if (photoVal != null) {
                    AsyncImage(
                        model = photoVal,
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2F3E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color(0xFF8A93A6),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    peerName.ifBlank { "Входящий звонок" },
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                val subtitle = if (remoteCanceled) "Звонок отменён" else "Входящий звонок"
                Text(subtitle, color = Color(0xFF9AA3B5), fontSize = 15.sp)
                val err = declineError
                if (err != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(err, color = Color(0xFFEF9A9A), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            // Низ: действия (§8.1 footer; раскладка как в CallScreen RINGING).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (remoteCanceled) {
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A2F3E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Закрыть")
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Отклонить — красная (calls_incoming_call_decline, NEGATIVE).
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935))
                                    .clickable { decline("REJECTED") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.CallEnd,
                                    contentDescription = "Отклонить",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Отклонить", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Принять — зелёная (calls_reply): навигация на CallScreen —
                            // существующий путь (LaunchedEffect pendingIncomingCallPayload
                            // + incomingCallAccepted в SovaNavHost).
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF43A047))
                                    .clickable {
                                        if (!declineBusy) onAccept()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Call,
                                    contentDescription = "Принять",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Принять", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    // «Занят» — вариант отклонения с причиной BUSY (Ж0 §2.6 HangupType;
                    // wire hangup{reason} §Ж0-583). Отдельной кнопки в web-входящем нет
                    // (§8.1) — см. KDoc [performIncomingDecline].
                    TextButton(
                        onClick = { decline("BUSY") },
                        enabled = !declineBusy,
                    ) {
                        Text("Занят", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * #CALLS-INCOMING-UI (Этап Е): отклонение входящего звонка ДО accept.
 *
 * Wire-решение (Ж0-протокол §2.6/§2.7, реверс UI-срезов §8.1/§8.4):
 *  1. WS-путь (эталон): Conversation.js DECLINE_INCOMING → signaling.hangup(hangupType.REJECTED)
 *     — команда {"command":"hangup","reason":"REJECTED"}; формат подтверждён тестами 2/4/6
 *     (только {reason}, UPPERCASE enum hangupType; conversationId в hangup сервер отвергает).
 *     Поднимаем CallSignalingClient тем же способом, что CallScreen для входящего
 *     (params из payload → ConversationParamsDecoder.decode, conversationId и uid —
 *     как CallScreen:683-747), и шлём hangup(reason). «Занят» — тот же wire-формат
 *     со значением enum BUSY (Ж0 §2.6; §8.4: hangupType BUSY → calls_user_busy).
 *  2. HTTP-фолбэк: vchat.hangupConversation{conversationId, session_key, reason}
 *     (Ж0 §2.7 — reason из того же enum; прецедент CallScreen:1906-1915 — отклонение
 *     при неготовом WS тем же методом, но с reason="declined"; здесь шлём UPPERCASE
 *     enum по Ж0, расхождение строк помечено на live-проверку Этапом И).
 *  3. Верификация обоих путей — messagesGetCurrentCalls: звонок исчез из текущих =
 *     доставлено (в т.ч. если звонок уже снят сервером/принят на другом устройстве —
 *     web-события incoming_call_canceled / incoming_call_accepted_on_another_device).
 *
 * @param payload LP-115/events_queue payload входящего (conversation params) — может
 *                быть "-1"/null: тогда WS-путь недоступен, работает HTTP-фолбэк.
 * @param reason  "REJECTED" (Отклонить) или "BUSY" (Занят) — enum HangupType (Ж0 §2.6).
 * @return true — отклонение доставлено (или звонка уже нет); false — не удалось.
 */
suspend fun performIncomingDecline(
    deps: CallsDependencies,
    payload: String?,
    reason: String,
): Boolean {
    // 1) Текущий звонок сервера: conversationId (tolerant, как CallScreen:683-694).
    val calls = withContext(Dispatchers.IO) {
        runCatching { deps.apiClient.messagesGetCurrentCalls() }.getOrNull()
    }
    if (calls == null) return false
    if (calls.isEmpty()) return true
    var convId: String? = null
    val first = calls.first()
    val v1 = first.get("conversation_id")
    val v2 = first.get("id")
    val v3 = first.get("call_id")
    if (v1 != null && v1.isJsonPrimitive) {
        convId = v1.asString
    } else if (v2 != null && v2.isJsonPrimitive) {
        convId = v2.asString
    } else if (v3 != null && v3.isJsonPrimitive) {
        convId = v3.asString
    }
    val conv = convId
    if (conv == null || conv.isBlank()) {
        AppLog.w("IncomingCall", "decline: conversationId не получен — отклонить нельзя")
        return false
    }

    // 2) WS-путь: params из payload (декодирование LZ4 — CPU, не main).
    var params: ConversationParamsDecoder.Params? = null
    val src = payload
    if (!src.isNullOrBlank()) {
        params = withContext(Dispatchers.Default) {
            runCatching { ConversationParamsDecoder.decode(src) }.getOrNull()
        }
    }
    val wsParams = params
    if (wsParams != null) {
        // uid — как CallScreen:736-737: okcdn uid (callsSessionUid), при отсутствии — VK uid.
        val snap = deps.prefs.data.first()
        val okUid = snap.callsSessionUid
        val uid = if (okUid > 0L) okUid else deps.exchangeAuthRepository.userId()
        val signaling = CallSignalingClient(deps.httpClient)
        signaling.start(userId = uid, conversationId = conv, params = wsParams)
        var waited = 0
        while (!signaling.isWsReady() && waited < 6000) {
            delay(200)
            waited += 200
        }
        val sent = signaling.hangup(reason)
        AppLog.i("IncomingCall", "decline WS: sent=$sent wsReady=${signaling.isWsReady()}")
        if (sent) {
            delay(1200)
        }
        signaling.stop()
        if (sent) {
            val after = withContext(Dispatchers.IO) {
                runCatching { deps.apiClient.messagesGetCurrentCalls() }.getOrNull()
            }
            if (after != null && after.isEmpty()) return true
        }
    }

    // 3) HTTP-фолбэк: vchat.hangupConversation (Ж0 §2.7; прецедент CallScreen:1906-1915).
    val sk = runCatching { deps.ensureCallsSessionKey(force = false) }.getOrNull()
    if (sk == null || sk.isBlank()) {
        AppLog.w("IncomingCall", "decline: sessionKey не получен — HTTP-фолбэк невозможен")
        return false
    }
    val httpOk = withContext(Dispatchers.IO) {
        runCatching { deps.apiClient.vchatHangupConversation(conv, sk, reason) }.getOrDefault(false)
    }
    AppLog.i("IncomingCall", "decline HTTP: ok=$httpOk reason=$reason conv=${conv.take(12)}…")
    if (!httpOk) return false
    delay(1200)
    val after = withContext(Dispatchers.IO) {
        runCatching { deps.apiClient.messagesGetCurrentCalls() }.getOrNull()
    }
    if (after != null && after.isEmpty()) return true
    // Сервер мог не успеть снять звонок — вторая проверка через 2с.
    delay(2000)
    val after2 = withContext(Dispatchers.IO) {
        runCatching { deps.apiClient.messagesGetCurrentCalls() }.getOrNull()
    }
    return after2 != null && after2.isEmpty()
}
