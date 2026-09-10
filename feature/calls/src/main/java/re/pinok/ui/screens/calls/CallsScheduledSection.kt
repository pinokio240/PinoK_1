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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
// W33-b: LocalClipboardManager deprecated в Compose 1.8+ — пишем в буфер через
// платформенный ClipboardManager (тот же подход, что Fix #193 в LandingScreen).
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

/** Айтем списка запланированных (wire REV-DEEP-2, см. parseScheduledCall). */
private data class ScheduledCallItem(
    val callId: String,
    val name: String,
    /** schedule.time — unix СЕКУНДЫ (прежних кандидатов scheduled_date/date/start_date в wire НЕТ). */
    val ts: Long,
    /** schedule.duration — СЕКУНДЫ. */
    val durationSec: Long,
    /** schedule.recurrence_rule (never|daily|weekly|weekdays|weekend|monthly|yearly). */
    val recurrenceRule: String,
    /** Ссылка-приглашение (join-флоу + «Копировать ссылку-приглашение»). */
    val vkJoinLink: String,
    /** group.name — для desc-строки айтема (calls_scheduled_calls_item_desc). */
    val groupName: String,
    /** schedule.marker_time — маркер messages.editCall при правке/переносе. */
    val markerTime: Long,
    /** Сырой JsonObject — editItem модалки CallsScheduleCallDialog (полный prefill). */
    val raw: JsonObject,
)

/** Блок группировки по дням: «Сегодня»/«Завтра»/дата (локальная TZ). */
private class ScheduledDayBlock(val title: String, val isToday: Boolean, val items: List<ScheduledCallItem>)

/**
 * #CALLS-SNAP (2026-09-06, РЕВИЗИЯ-2 по REV-DEEP-2): секция «Запланированные»
 * — messages.getScheduledCalls через репозиторий раздела (paged-форма
 * {grouped:1, count:50, start_from}; next_from-пагинация — loadMore(SCHEDULED)),
 * ГРУППИРОВКА ПО ДНЯМ (подзаголовки блоков «Сегодня»/«Завтра»/дата по локальной
 * TZ — testid calls_main_page_scheduled_calls_block_title), wire-парсинг
 * schedule{time,duration,recurrence_rule,marker_time} + vk_join_link + group.
 *
 * Действия айтема (web-меню ds@97907 — подмножество, обеспеченное фасадом):
 *  - «Присоединиться» → join по vk_join_link (web: showJoinPopup, отдельного
 *    API «начать сейчас» НЕ существует; messagesForceCallFinish web'ом НЕ
 *    используется — прежний пункт УДАЛЁН из карточки): authed-вход
 *    performJoinByLink(deps, parseCallJoinLink(vkJoinLink), пароль из ссылки,
 *    anonymName="", isVideo=false) → при успехе CallJoinByLinkHolder.stash —
 *    SovaNavHost сам открывает CallScreen (join-режим). isVideo=false — фикс:
 *    тоггл не делаем (mute_video айтема мог бы подсказать, web-поведение
 *    превью не эмулируем); анонимную ветку секция НЕ использует (имя пустое).
 *  - «Редактировать/перенести» → CallsScheduleCallDialog(editItem=raw):
 *    web правит (BM) и переносит (Uk) одним messages.editCall — один пункт.
 *  - «Копировать ссылку-приглашение» → vk_join_link в буфер обмена.
 *  - «Удалить» → messages.deleteScheduledCall с confirm-диалогом.
 * У сегодняшних айтемов — прямая кнопка «Присоединиться» на карточке
 * (calls_scheduled_calls_item_join).
 *
 * НЕ РЕНДЕРЯТСЯ (честные отклонения, обеспеченность фасада/UI):
 *  - goto_chat (переход в chat.peer_id) — навигация в :app-мессенджер из
 *    :feature:calls недоступна (модульные границы);
 *  - share_qr_code — QR-генератора в проекте нет;
 *  - copy_short_link/copy_broadcast_link — short_credentials показываются в
 *    пост-модалке создания; основная ссылка айтема — vk_join_link;
 *  - «Присоединиться» не рендерится, если vk_join_link пуст (честно).
 *
 * Пагинация (паттерн Этапа Б1, CallsHistorySection): scroll-to-end LazyColumn
 * → repo.loadMore(CallsSectionKey.SCHEDULED); hasMore/loadingMore — поля
 * CallsSectionState, футер-спиннер при дозагрузке. После мутаций —
 * repo.refresh(SCHEDULED, force=true) + честный Toast (lastApiError).
 */
@Composable
fun CallsScheduledSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    // W33-b: замена deprecated LocalClipboardManager — см. импорт-блок выше.
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val scope = rememberCoroutineScope()
    val state by repo.scheduled.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsScheduledSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.SCHEDULED, force = false)
    }

    var editItem by remember { mutableStateOf<ScheduledCallItem?>(null) }
    var deleteItem by remember { mutableStateOf<ScheduledCallItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }

    // Общий исполнитель действий карточки: API → refresh+Toast / Toast с реальной ошибкой.
    val runAction: (ScheduledCallItem, String, suspend (String) -> Boolean) -> Unit =
        { item, successText, apiCall ->
            if (busy) {
                AppLog.w("CallsScheduledSection", "действие пропущено: предыдущее ещё выполняется")
            } else {
                busy = true
                scope.launch {
                    val ok = try {
                        apiCall(item.callId)
                    } catch (e: Exception) {
                        AppLog.e("CallsScheduledSection", "action error (callId=" + item.callId + ")", e)
                        false
                    }
                    busy = false
                    if (ok) {
                        AppLog.i("CallsScheduledSection", "action OK (callId=" + item.callId + ")")
                        repo.refresh(CallsSectionKey.SCHEDULED, force = true)
                        Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    } else {
                        val apiErr = deps.apiClient.lastApiError
                        val msg = if (apiErr.isNullOrBlank()) {
                            "Действие не выполнено (сервер не подтвердил)"
                        } else {
                            "Ошибка: $apiErr"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    // Join-флоу (REV-DEEP-2: web входит по vk_join_link — showJoinPopup):
    // authed-путь performJoinByLink, пароль — из самой ссылки (?p=), анонимная
    // ветка секцией не используется, isVideo=false (см. KDoc).
    val runJoin: (ScheduledCallItem) -> Unit = { item ->
        if (joining) {
            AppLog.w("CallsScheduledSection", "join пропущен: предыдущее присоединение ещё выполняется")
        } else {
            val parts = parseCallJoinLink(item.vkJoinLink)
            if (parts == null) {
                Toast.makeText(context, "Не удалось распознать ссылку-приглашение", Toast.LENGTH_LONG).show()
            } else {
                joining = true
                scope.launch {
                    val result = performJoinByLink(
                        deps = deps,
                        parts = parts,
                        password = parts.password,
                        anonymName = "",
                        isVideo = false,
                    )
                    joining = false
                    if (result.success) {
                        val session = result.session
                        if (session != null) {
                            AppLog.i("CallsScheduledSection", "join OK (callId=" + item.callId + ") — сессия в CallJoinByLinkHolder")
                            CallJoinByLinkHolder.stash(session)
                        } else {
                            Toast.makeText(context, "Сервер не вернул параметры звонка", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, result.errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val copyInvite: (String) -> Unit = { link ->
        clipboard.setPrimaryClip(ClipData.newPlainText("invite_link", link))
        Toast.makeText(context, "Ссылка-приглашение скопирована", Toast.LENGTH_SHORT).show()
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет запланированных звонков",
        onRetry = { repo.refresh(CallsSectionKey.SCHEDULED, force = true) },
        modifier = Modifier.testTag("scheduled_section"),
    ) { raw ->
        val parsed = remember(raw) { raw.mapNotNull { it.parseScheduledCall() } }
        if (parsed.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет запланированных звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val blocks = remember(parsed) { groupScheduledByDays(parsed) }
            val listState = rememberLazyListState()
            val flatCount = blocks.size + parsed.size

            // Пагинация: scroll-to-end → loadMore(SCHEDULED) (паттерн Этапа Б1).
            LaunchedEffect(flatCount, state.hasMore, state.loadingMore) {
                snapshotFlow {
                    val info = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    if (info != null) info.index else -1
                }.collect { lastVisible ->
                    if (lastVisible >= 0 &&
                        parsed.isNotEmpty() &&
                        lastVisible >= flatCount - LOAD_MORE_AHEAD &&
                        state.hasMore &&
                        !state.loadingMore
                    ) {
                        AppLog.i("CallsScheduledSection", "scroll-to-end → loadMore(SCHEDULED)")
                        repo.loadMore(CallsSectionKey.SCHEDULED)
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                Text(
                    "Запланированные · " + parsed.size,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("scheduled_list"),
                ) {
                    // Индекс в ключе заголовка: при «рваном» порядке айтемов два
                    // блока могут получить одинаковый заголовок — ключи LazyColumn
                    // обязаны быть уникальны.
                    blocks.forEachIndexed { blockIdx, block ->
                        item(key = "scheduled_day_" + blockIdx + "_" + block.title) {
                            Text(
                                block.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("calls_main_page_scheduled_calls_block_title"),
                            )
                        }
                        items(block.items, key = { it.callId }) { item ->
                            ScheduledCallCard(
                                item = item,
                                isToday = block.isToday,
                                busy = busy || joining,
                                onJoin = { runJoin(item) },
                                onEdit = { editItem = item },
                                onCopyInvite = { copyInvite(item.vkJoinLink) },
                                onDelete = { deleteItem = item },
                                modifier = Modifier.testTag("scheduled_item"),
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item(key = "scheduled_load_more_footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag("calls_scheduled_load_more"),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // «Редактировать/перенести» → модалка планирования с полным prefill
    // из сырого айтема (правка/перенос — один messages.editCall, REV-DEEP-2).
    val editing = editItem
    if (editing != null) {
        CallsScheduleCallDialog(
            editItem = editing.raw,
            onDismiss = { editItem = null },
        )
    }

    // «Удалить» → подтверждение (необратимо) → deleteScheduledCall.
    val deleting = deleteItem
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Удалить запланированный звонок") },
            text = { Text("«" + deleting.name + "» будет удалён. Действие необратимо.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val target = deleting
                        deleteItem = null
                        runAction(target, "Запланированный звонок удалён") { callId ->
                            deps.apiClient.messagesDeleteScheduledCall(callId)
                        }
                    },
                    modifier = Modifier.testTag("scheduled_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ScheduledCallCard(
    item: ScheduledCallItem,
    isToday: Boolean,
    busy: Boolean,
    onJoin: () -> Unit,
    onEdit: () -> Unit,
    onCopyInvite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Пункт «Присоединиться» честно не рендерится без vk_join_link.
    val hasJoinLink = item.vkJoinLink.isNotBlank()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val desc = scheduledDescOf(item)
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Прямая кнопка «Присоединиться» — у сегодняшних айтемов
                // (web: calls_scheduled_calls_item_join).
                if (isToday && hasJoinLink) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onJoin,
                        enabled = !busy,
                        modifier = Modifier.testTag("calls_scheduled_calls_item_join"),
                    ) {
                        Text("Присоединиться")
                    }
                }
            }
            // Меню действий карточки (подмножество web-меню ds@97907).
            Box {
                IconButton(
                    enabled = !busy,
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("scheduled_item_menu"),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Действия")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (hasJoinLink) {
                        DropdownMenuItem(
                            text = { Text("Присоединиться") },
                            onClick = {
                                menuOpen = false
                                onJoin()
                            },
                            modifier = Modifier.testTag("calls_scheduled_calls_item_menu_join"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Редактировать/перенести") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("calls_scheduled_calls_item_menu_edit"),
                    )
                    if (hasJoinLink) {
                        DropdownMenuItem(
                            text = { Text("Копировать ссылку-приглашение") },
                            onClick = {
                                menuOpen = false
                                onCopyInvite()
                            },
                            modifier = Modifier.testTag("calls_scheduled_calls_item_menu_copy_invite"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("calls_scheduled_calls_item_menu_delete"),
                    )
                }
            }
        }
    }
}

/** Desc-строка айтема (calls_scheduled_calls_item_desc): время + повтор + группа. */
private fun scheduledDescOf(item: ScheduledCallItem): String {
    var desc = ""
    if (item.ts > 0L) desc = item.ts.toAbsoluteTime()
    if (item.recurrenceRule.isNotBlank() && item.recurrenceRule != "never") {
        desc = desc + " · " + repeatLabelOf(item.recurrenceRule)
    }
    if (item.groupName.isNotBlank()) {
        desc = desc + " · " + item.groupName
    }
    return desc
}

/** Рус. подпись повтора (wire-енум модуля 912069; ланг-ключи не вырезаны). */
private fun repeatLabelOf(wire: String): String {
    when (wire) {
        "daily" -> return "Каждый день"
        "weekly" -> return "Каждую неделю"
        "weekdays" -> return "По будням"
        "weekend" -> return "По выходным"
        "monthly" -> return "Каждый месяц"
        "yearly" -> return "Каждый год"
    }
    return wire
}

/** Ключ дня (локальная TZ): год*1000 + dayOfYear — стабильный ключ группировки. */
private fun dayKeyOf(sec: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = sec * 1000L
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

/** Заголовок блока дня: Сегодня/Завтра/«d MMMM»/«d MMMM yyyy»; без даты — «Без даты». */
private fun dayTitleOf(sec: Long, key: Int, todayKey: Int, tomorrowKey: Int): String {
    if (sec <= 0L) return "Без даты"
    if (key == todayKey) return "Сегодня"
    if (key == tomorrowKey) return "Завтра"
    val cal = Calendar.getInstance()
    cal.timeInMillis = sec * 1000L
    val pattern = if (cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
        "d MMMM"
    } else {
        "d MMMM yyyy"
    }
    val sdf = SimpleDateFormat(pattern, Locale.forLanguageTag("ru"))
    return sdf.format(Date(sec * 1000L))
}

/**
 * Группировка по дням в порядке списка (порядок wire не меняем): соседние
 * айтемы одного дня попадают в один блок с подзаголовком Сегодня/Завтра/дата.
 */
private fun groupScheduledByDays(items: List<ScheduledCallItem>): List<ScheduledDayBlock> {
    val nowSec = System.currentTimeMillis() / 1000L
    val todayKey = dayKeyOf(nowSec)
    val tomorrowKey = dayKeyOf(nowSec + 86_400L)
    val blocks = ArrayList<ScheduledDayBlock>()
    var currentKey = Int.MIN_VALUE
    var currentTitle = ""
    var currentToday = false
    var currentList = ArrayList<ScheduledCallItem>()
    for (item in items) {
        val key = dayKeyOf(item.ts)
        if (key != currentKey) {
            if (currentList.isNotEmpty()) {
                blocks.add(ScheduledDayBlock(currentTitle, currentToday, currentList))
            }
            currentKey = key
            currentTitle = dayTitleOf(item.ts, key, todayKey, tomorrowKey)
            currentToday = key == todayKey
            currentList = ArrayList()
        }
        currentList.add(item)
    }
    if (currentList.isNotEmpty()) {
        blocks.add(ScheduledDayBlock(currentTitle, currentToday, currentList))
    }
    return blocks
}

/** Парсинг items[] messages.getScheduledCalls по wire (новый код — #NULL-EXPLICIT). */
private fun JsonObject.parseScheduledCall(): ScheduledCallItem? {
    return try {
        val idEl = get("call_id")
        if (idEl == null || !idEl.isJsonPrimitive) return null
        val callId = idEl.asString

        var name = "Запланированный звонок"
        val nameEl = get("name")
        if (nameEl != null && nameEl.isJsonPrimitive) {
            val n = nameEl.asString
            if (n.isNotBlank()) name = n
        }

        // Wire (REV-DEEP-2): schedule{time, duration, recurrence_rule,
        // recurrence_until_time, marker_time} — СЕКУНДЫ. Прежние кандидаты
        // scheduled_date/date/start_date в wire НЕ существуют — удалены.
        var ts = 0L
        var durationSec = 0L
        var recurrenceRule = ""
        var markerTime = 0L
        val schedEl = get("schedule")
        if (schedEl != null && schedEl.isJsonObject) {
            val sched = schedEl.asJsonObject
            val timeEl = sched.get("time")
            if (timeEl != null && timeEl.isJsonPrimitive) ts = timeEl.asLong
            val durEl = sched.get("duration")
            if (durEl != null && durEl.isJsonPrimitive) durationSec = durEl.asLong
            val rrEl = sched.get("recurrence_rule")
            if (rrEl != null && rrEl.isJsonPrimitive) recurrenceRule = rrEl.asString
            val markerEl = sched.get("marker_time")
            if (markerEl != null && markerEl.isJsonPrimitive) markerTime = markerEl.asLong
        }
        if (ts > 100_000_000_000L) ts = ts / 1000L // защита от миллисекунд

        var joinLink = ""
        val linkEl = get("vk_join_link")
        if (linkEl != null && linkEl.isJsonPrimitive) joinLink = linkEl.asString

        var groupName = ""
        val groupEl = get("group")
        if (groupEl != null && groupEl.isJsonObject) {
            val gnameEl = groupEl.asJsonObject.get("name")
            if (gnameEl != null && gnameEl.isJsonPrimitive) groupName = gnameEl.asString
        }

        ScheduledCallItem(
            callId = callId,
            name = name,
            ts = ts,
            durationSec = durationSec,
            recurrenceRule = recurrenceRule,
            vkJoinLink = joinLink,
            groupName = groupName,
            markerTime = markerTime,
            raw = this,
        )
    } catch (e: Exception) {
        AppLog.e("CallsScheduledSection", "parseScheduledCall: запись пропущена", e)
        null
    }
}
