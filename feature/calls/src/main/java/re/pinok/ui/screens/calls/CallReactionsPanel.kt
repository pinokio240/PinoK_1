package re.pinok.ui.screens.calls

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-ZH (2026-09-06, Этап Ж/Ж2, Task 5-b): панель реакций и поднятой руки
 * (web: calls_reactions_*, calls_tooltip_raise_hand/_lower_hand — REV-UI §11.9).
 *
 * ОТПРАВКА (wire — Ж0-протокол, через CallSignalingClient #CALLS-ZH-методы):
 *  - реакция: WS `feedback {key}` (Ж0 §3.1, 16131@309999) — key СЕРВЕРНЫЙ,
 *    берётся ИЗ КАТАЛОГА calls.getReactions (Ж0 §1.3: {items:[{key, description,
 *    images:[{width,url}]}]}; §13.5: в снапшотах литералов ключей нет —
 *    самодельный набор эмодзи был бы фикцией, поэтому каталог — единственный
 *    честный источник);
 *  - рука: WS `change-participant-state {participantState:{state:{hand:"1"|"0"}}}`
 *    (Ж0 §3.2, 16131@306127; значения — ParticipantStateDataValue 16131@480035,
 *    живая проверка — Этап И §13.1). Toggle с локальным состоянием isHandRaised
 *    (живёт в CallScreen); серверный синк своей руки — participant-state-changed
 *    в CallScreen (по myParticipantId из connection.participants).
 *
 * ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
 *  - каталог недоступен/пуст → в панели текст причины; кнопка РУКИ при этом
 *    работает (она от каталога не зависит);
 *  - показ ЧУЖИХ реакций (плашка по WS `feedback`-уведомлению) — вне скоупа
 *    5-b: уведомление попадает в messages-flow и считается в diag-строке
 *    «Принято: feedback×N» (CallScreen), UI-плашка — Ж6+ (следующая волна).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 * #ANR-MAIN-IO: callsGetReactions — Dispatchers.IO, парсинг — Dispatchers.Default.
 */
private const val CALL_REACTIONS_TAG = "CallReactionsPanel"

/** Элемент каталога реакций (calls.getReactions → items). */
private data class CallReactionItem(
    val key: String,
    val label: String,
    val imageUrl: String?,
)

@Composable
internal fun CallReactionsPanel(
    callId: String?,
    signaling: CallSignalingClient,
    isHandRaised: Boolean,
    onHandChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current

    var items by remember { mutableStateOf<List<CallReactionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Каталог реакций — calls.getReactions (член фасада; параметры {call_id}).
    LaunchedEffect(callId) {
        loading = true
        loadError = null
        items = emptyList()
        val cid = callId
        if (cid == null || cid.isBlank()) {
            loadError = "Звонок ещё не создан — каталог реакций недоступен"
            loading = false
            return@LaunchedEffect
        }
        try {
            val resp = withContext(Dispatchers.IO) { deps.apiClient.callsGetReactions(cid) }
            if (resp == null) {
                val apiErr = deps.apiClient.lastApiError
                if (apiErr != null && apiErr.isNotBlank()) {
                    loadError = "Каталог реакций недоступен: " + apiErr
                } else {
                    loadError = "Каталог реакций недоступен (сеть/оффлайн)"
                }
            } else {
                val parsed = withContext(Dispatchers.Default) { parseReactionCatalog(resp) }
                if (parsed.isEmpty()) {
                    loadError = "Сервер вернул пустой каталог реакций"
                } else {
                    items = parsed
                    AppLog.i(CALL_REACTIONS_TAG, "каталог реакций: " + parsed.size + " ключей")
                }
            }
        } catch (e: Exception) {
            AppLog.w(CALL_REACTIONS_TAG, "callsGetReactions failed: " + e.toString())
            loadError = "Каталог реакций недоступен"
        } finally {
            loading = false
        }
    }

    // Рука: toggle; при неудаче отправки состояние НЕ меняется (честно).
    val toggleHand: () -> Unit = {
        val next = !isHandRaised
        val ok = signaling.changeParticipantState("hand", if (next) "1" else "0")
        if (ok) {
            onHandChanged(next)
            AppLog.i(CALL_REACTIONS_TAG, "hand → " + (if (next) "1 (поднята)" else "0 (опущена)"))
        } else {
            Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
        }
    }

    // Реакция: feedback {key}; при неудаче — честный Toast, панель остаётся.
    val sendReaction: (CallReactionItem) -> Unit = { item ->
        val ok = signaling.sendFeedback(item.key)
        if (ok) {
            AppLog.i(CALL_REACTIONS_TAG, "feedback key=" + item.key + " отправлен")
        } else {
            Toast.makeText(context, "Реакция не отправлена: сигналинг закрыт", Toast.LENGTH_SHORT).show()
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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Реакции",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Поднятая рука (toggle) — работает независимо от каталога.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHandRaised) Color(0xFF43A047) else Color(0xFF37474F),
                    modifier = Modifier.fillMaxWidth().clickable { toggleHand() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isHandRaised) "Опустить руку" else "Поднять руку",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (isHandRaised) {
                            Text(
                                text = "рука поднята",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    val err = loadError
                    if (err != null) {
                        Text(
                            text = err,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    } else {
                        // Сетка каталога: строки по 4 (без экспериментального FlowRow).
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().height(220.dp).verticalScroll(rememberScrollState()),
                        ) {
                            for (chunk in items.chunked(4)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (item in chunk) {
                                        CallReactionCell(
                                            item = item,
                                            onClick = { sendReaction(item) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // Добивка пустыми ячейками, чтобы сетка была ровной.
                                    val pad = 4 - chunk.size
                                    if (pad > 0) {
                                        Spacer(Modifier.weight(pad.toFloat()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallReactionCell(
    item: CallReactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF37474F))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        val img = item.imageUrl
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = item.label,
                modifier = Modifier.size(40.dp),
            )
        } else {
            // CDN-картинки нет — подпись (description) вместо фиктивной иконки.
            Text(
                text = item.label.take(8),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Tolerant-парсинг каталога: {items:[{key, description, images:[{width,url}]}]}
 * (Ж0 §1.3, bridge@851655-852111: нормализация в карты {24|28|…|192: {key:url},
 * descriptions:{key:text}}). Выбор картинки: ближайшая к 48px (приоритет снизу).
 */
private fun parseReactionCatalog(resp: JsonObject): List<CallReactionItem> {
    val out = ArrayList<CallReactionItem>()
    val itemsEl = resp.get("items")
    if (itemsEl == null || !itemsEl.isJsonArray) return out
    for (el in itemsEl.asJsonArray) {
        if (!el.isJsonObject) continue
        val it = el.asJsonObject
        val keyEl = it.get("key")
        if (keyEl == null || !keyEl.isJsonPrimitive) continue
        val key = keyEl.asString
        if (key.isBlank()) continue
        val descEl = it.get("description")
        val label = if (descEl != null && descEl.isJsonPrimitive) descEl.asString else key
        var url: String? = null
        var best = Int.MIN_VALUE
        val imgEl = it.get("images")
        if (imgEl != null && imgEl.isJsonArray) {
            for (im in imgEl.asJsonArray) {
                if (!im.isJsonObject) continue
                val imo = im.asJsonObject
                val uEl = imo.get("url")
                if (uEl == null || !uEl.isJsonPrimitive) continue
                val u = uEl.asString
                if (u.isBlank()) continue
                val wEl = imo.get("width")
                val w = if (wEl != null && wEl.isJsonPrimitive) wEl.asInt else 0
                val score = if (w <= 48) 1000 - (48 - w) else w - 48
                if (url == null || score > best) {
                    url = u
                    best = score
                }
            }
        }
        out.add(CallReactionItem(key, label, url))
    }
    return out
}
