package re.pinok.ui.screens.calls

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import re.pinok.util.AppLog

/**
 * #CALLS-ZH (2026-09-06, Этап Ж/Ж1, Task 5-b): панель «Участники» активного звонка
 * (web: calls_call_footer_button_participants, label calls_participants; список —
 * calls_participant_list_*).
 *
 * ИСТОЧНИК ДАННЫХ — VK API calls.getParticipants { call_id, offset, count, fields }
 * (член фасада CallsApi.callsGetParticipants; KDoc фасада: response =
 * {count, secret, profiles, anonyms, groups}). Сеть — только через
 * withContext(Dispatchers.IO), парсинг — Dispatchers.Default (#ANR-MAIN-IO).
 *
 * Пагинация — OFFSET-пагинация по двум признакам (паттерн Этапа Б1): страница
 * < PAGE_SIZE (=25, count веб-формы) ИЛИ загружено >= count, если сервер дал
 * count. Догрузка — по скроллу к последним 3 элементам LazyColumn; дедуп по uid
 * (сдвиги offset-пагинации при параллельных изменениях — урок Б1); ошибка
 * догрузки НЕ затирает видимый список.
 *
 * ЧЕСТНОЕ ОГРАНИЧЕНИЕ (no-stub): WS-пагинация get-participant-list-chunk
 * (Ж0 §1.1#34, §6.7) НЕ используется — CallSignalingClient не ведёт
 * request/response-корреляцию по sequence, добавлять её в 5-b нечестно
 * минимальным диффом; HTTP-offset покрывает тот же список (по Ж0 §6.7
 * get-participants/chunk — дубли списка). Зафиксировано в отчёте 5-b.
 *
 * Статусы микро/камеры — ТОЛЕРАНТНО: точная форма HTTP-ответа {profiles} в
 * снапшотах не сохранилась — рендерится ТОЛЬКО то, что реально пришло:
 * mediaSettings{isAudioEnabled,isVideoEnabled} (форма §9.1 Ж0) либо плоские
 * is_muted/muted/is_video. Нет поля — нет иконки (никаких догадок).
 *
 * Реакции участников в панели НЕТ: per-call реакции приходят только WS-уведомлением
 * feedback (Ж0 §3.1), HTTP-списка реакций по звонку не существует; calls.getReactions —
 * каталог ключей для отправителя (использован в CallReactionsPanel; Ж0 §1.3).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */
private const val PARTICIPANTS_PAGE_SIZE = 25
private const val PARTICIPANTS_TAG = "CallParticipantsPanel"

/** Строка участника (tolerant-парсинг; неизвестное — null, не рендерится). */
private data class CallParticipantRow(
    val uid: Long,
    val name: String,
    val photo: String?,
    val statusText: String?,
    val isMuted: Boolean?,
    val isCamOff: Boolean?,
    val roleText: String?,
)

@Composable
internal fun CallParticipantsPanel(
    callId: String?,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current

    var rows by remember { mutableStateOf<List<CallParticipantRow>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Первая страница. Нет call_id (звонок ещё не создан) — честное сообщение,
    // панель не притворяется, что список пуст «сам собой».
    LaunchedEffect(callId) {
        val cid = callId
        if (cid == null || cid.isBlank()) {
            errorText = "Звонок ещё не создан — участников нет"
            hasMore = false
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        try {
            val resp = withContext(Dispatchers.IO) {
                deps.apiClient.callsGetParticipants(cid, 0, PARTICIPANTS_PAGE_SIZE, null)
            }
            if (resp == null) {
                val apiErr = deps.apiClient.lastApiError
                if (apiErr != null && apiErr.isNotBlank()) {
                    errorText = "Сервер не вернул участников: " + apiErr
                } else {
                    errorText = "Сервер не вернул участников (сеть/оффлайн)"
                }
                hasMore = false
            } else {
                val parsed = withContext(Dispatchers.Default) { parseCallParticipantsPage(resp) }
                val loaded = parsed.first
                rows = loaded
                total = parsed.second
                hasMore = if (total > 0) loaded.size < total else loaded.size >= PARTICIPANTS_PAGE_SIZE
                AppLog.i(PARTICIPANTS_TAG, "страница 1: loaded=" + loaded.size + " total=" + total + " hasMore=" + hasMore)
            }
        } catch (e: Exception) {
            AppLog.w(PARTICIPANTS_TAG, "load error: " + e.toString())
            errorText = "Не удалось загрузить участников"
            hasMore = false
        } finally {
            loading = false
        }
    }

    // Догрузка по скроллу (порог: 3 элемента до конца — паттерн Этапа Б1).
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (info != null) info.index else -1
        }
            .collect { lastIdx ->
                if (lastIdx < 0) return@collect
                if (!hasMore || loadingMore || loading || rows.isEmpty()) return@collect
                if (lastIdx < rows.size - 3) return@collect
                val cid = callId
                if (cid == null || cid.isBlank()) return@collect
                loadingMore = true
                try {
                    val resp = withContext(Dispatchers.IO) {
                        deps.apiClient.callsGetParticipants(cid, rows.size, PARTICIPANTS_PAGE_SIZE, null)
                    }
                    if (resp == null) {
                        hasMore = false
                    } else {
                        val parsed = withContext(Dispatchers.Default) { parseCallParticipantsPage(resp) }
                        val loaded = parsed.first
                        if (loaded.isEmpty()) {
                            hasMore = false
                        } else {
                            val known = HashSet<Long>()
                            for (r in rows) {
                                if (r.uid > 0L) known.add(r.uid)
                            }
                            val fresh = ArrayList<CallParticipantRow>()
                            for (r in loaded) {
                                if (r.uid <= 0L || !known.contains(r.uid)) fresh.add(r)
                            }
                            rows = rows + fresh
                            val t = parsed.second
                            hasMore = if (t > 0) rows.size < t else loaded.size >= PARTICIPANTS_PAGE_SIZE
                            AppLog.i(PARTICIPANTS_TAG, "догрузка: +" + fresh.size + " всего=" + rows.size)
                        }
                    }
                } catch (e: Exception) {
                    // Ошибка догрузки НЕ затирает видимый список (урок Б1):
                    // hasMore остаётся — повтор по следующему scroll-to-end.
                    AppLog.w(PARTICIPANTS_TAG, "loadMore error: " + e.toString())
                    Toast.makeText(context, "Догрузка не удалась — потяните список ещё раз", Toast.LENGTH_SHORT).show()
                } finally {
                    loadingMore = false
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
            Column(modifier = Modifier.padding(16.dp)) {
                // Шапка: заголовок + счётчик + закрыть (web: calls_participants).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (total > 0) "Участники (" + total + ")" else "Участники",
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

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    // NULL-ЯВНО: захват в val — smart-cast делегированного свойства невозможен.
                    val err0 = errorText
                    if (err0 != null && rows.isEmpty()) {
                        Text(
                            text = err0,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(rows.size) { i ->
                                val r = rows[i]
                                CallParticipantRowView(r)
                            }
                            if (loadingMore) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                            if (!hasMore && rows.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Все участники загружены",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                        // Ошибка при уже загруженном списке — строкой под списком
                        // (список не затираем).
                        val err = errorText
                        if (err != null && rows.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = err,
                                color = Color(0xFFEF9A9A),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallParticipantRowView(r: CallParticipantRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        // Аватар: фото либо инициал (как IncomingCallScreen/CallScreen).
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val ph = r.photo
            if (ph != null) {
                AsyncImage(
                    model = ph,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = r.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = r.name,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val role = r.roleText
            val status = r.statusText
            if (role != null || status != null) {
                Text(
                    text = listOfNotNull(role, status).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Статусы — только если сервер их реально прислал (tolerant, KDoc).
        val muted = r.isMuted
        if (muted != null) {
            Icon(
                imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (muted) "Микрофон выключен" else "Микрофон включён",
                tint = if (muted) Color(0xFFEF9A9A) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
        val camOff = r.isCamOff
        if (camOff == true) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = "Камера выключена",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Tolerant-парсинг страницы {count, profiles, anonyms, groups} (KDoc фасада;
 * Ж0 §1.3). Поля микро/камеры/статуса/роли — только фактически пришедшие.
 */
private fun parseCallParticipantsPage(resp: JsonObject): Pair<List<CallParticipantRow>, Int> {
    val out = ArrayList<CallParticipantRow>()
    val countEl = resp.get("count")
    val total = if (countEl != null && countEl.isJsonPrimitive) countEl.asInt else 0
    val buckets = listOf("profiles", "anonyms", "groups")
    for (bucket in buckets) {
        val arrEl = resp.get(bucket)
        if (arrEl == null || !arrEl.isJsonArray) continue
        for (el in arrEl.asJsonArray) {
            if (!el.isJsonObject) continue
            val p = el.asJsonObject
            val idEl = p.get("id")
            val uid = if (idEl != null && idEl.isJsonPrimitive) idEl.asLong else 0L
            val firstEl = p.get("first_name")
            val lastEl = p.get("last_name")
            val nameEl = p.get("name")
            val first = if (firstEl != null && firstEl.isJsonPrimitive) firstEl.asString else ""
            val last = if (lastEl != null && lastEl.isJsonPrimitive) lastEl.asString else ""
            val nm = if (nameEl != null && nameEl.isJsonPrimitive) nameEl.asString else ""
            val composed = (first + " " + last).trim()
            val finalName = when {
                composed.isNotBlank() -> composed
                nm.isNotBlank() -> nm
                bucket == "anonyms" -> "Гость"
                bucket == "groups" -> "Сообщество"
                else -> "Участник"
            }
            val photo200El = p.get("photo_200")
            val photo100El = p.get("photo_100")
            val photo = when {
                photo200El != null && photo200El.isJsonPrimitive && !photo200El.asString.isBlank() -> photo200El.asString
                photo100El != null && photo100El.isJsonPrimitive && !photo100El.asString.isBlank() -> photo100El.asString
                else -> null
            }
            // Статусы — tolerant: mediaSettings §9.1 либо плоские ключи (KDoc).
            var muted: Boolean? = null
            var camOff: Boolean? = null
            val msEl = p.get("mediaSettings")
            if (msEl != null && msEl.isJsonObject) {
                val ms = msEl.asJsonObject
                val aEl = ms.get("isAudioEnabled")
                // isBoolean живёт на JsonPrimitive, НЕ на JsonElement (ошибка компиляции
                // сборки юзера) — сужаем через asJsonPrimitive под guard isJsonPrimitive.
                if (aEl != null && aEl.isJsonPrimitive && aEl.asJsonPrimitive.isBoolean) muted = !aEl.asBoolean
                val vEl = ms.get("isVideoEnabled")
                if (vEl != null && vEl.isJsonPrimitive && vEl.asJsonPrimitive.isBoolean) camOff = !vEl.asBoolean
            }
            val mutedEl = p.get("is_muted")
            if (muted == null && mutedEl != null && mutedEl.isJsonPrimitive && mutedEl.asJsonPrimitive.isBoolean) muted = mutedEl.asBoolean
            val muted2El = p.get("muted")
            if (muted == null && muted2El != null && muted2El.isJsonPrimitive && muted2El.asJsonPrimitive.isBoolean) muted = muted2El.asBoolean
            val videoEl = p.get("is_video")
            if (camOff == null && videoEl != null && videoEl.isJsonPrimitive && videoEl.asJsonPrimitive.isBoolean) camOff = !videoEl.asBoolean
            // Статус/роль — сырые серверные значения (ParticipantStatus/UserRole —
            // Ж0 §2.6; без самодельного словаря, честно как пришло).
            val stEl = p.get("state")
            val status = if (stEl != null && stEl.isJsonPrimitive) stEl.asString else null
            val rolesEl = p.get("roles")
            var role: String? = null
            if (rolesEl != null && rolesEl.isJsonArray) {
                val names = ArrayList<String>()
                for (r in rolesEl.asJsonArray) {
                    if (r.isJsonPrimitive) names.add(r.asString)
                }
                if (names.isNotEmpty()) role = names.joinToString(", ")
            }
            out.add(CallParticipantRow(uid, finalName, photo, status, muted, camOff, role))
        }
    }
    return Pair(out, total)
}
