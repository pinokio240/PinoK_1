package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import re.pinok.feature.calls.CallHistoryEntry
import re.pinok.feature.calls.CallOutcome
import re.pinok.util.toDurationString

/**
 * Кластер строк истории/пропущенных (Этап Б, REV-UI §2.5): последовательные
 * записи одного собеседника схлопываются в одну строку с заголовком
 * «Имя Фамилия (N)» (наблюдаемый максимум в снапшоте — (10)); статус-строка —
 * ПОСЛЕДНЕГО (самого свежего) звонка кластера. Групповые записи не
 * кластеризуются (веб: «Групповой звонок 26.08.2026» — всегда отдельная
 * строка без стрелки направления).
 *
 * recordIds — числовые id записей кластера для calls.deleteHistoryRecords /
 * calls.deleteGroupHistoryRecords; canRemove=false — хотя бы одна запись без
 * распознанного числового id (пункт «Убрать из списка» честно отключается).
 */
internal data class CallCluster(
    val key: String,
    val peerId: Long,
    val name: String,
    val photo: String?,
    val isGroup: Boolean,
    val groupId: Long,
    val recordIds: List<Long>,
    val canRemove: Boolean,
    val count: Int,
    /** Самая свежая запись кластера — источник статус-строки (REV-UI §2.5). */
    val last: CallHistoryEntry,
)

/**
 * Кластеризация ПОСЛЕДОВАТЕЛЬНЫХ записей одного собеседника (entries идут
 * от свежих к старым). Правило веба (REV-UI §2.5/§3.2): соседние записи с
 * одним peerId схлопываются; групповые записи и записи без peer — отдельно.
 * Граница страниц пагинации кластер не разрывает искусственно — соседние
 * записи одного peer из разных страниц тоже сольются (визуальный аналог веба).
 */
internal fun clusterHistoryEntries(entries: List<CallHistoryEntry>): List<CallCluster> {
    if (entries.isEmpty()) return emptyList()
    val out = ArrayList<CallCluster>()
    var bucket = ArrayList<CallHistoryEntry>()

    fun flush() {
        if (bucket.isEmpty()) return
        val head = bucket[0]
        val ids = ArrayList<Long>(bucket.size)
        var allValid = true
        for (e in bucket) {
            if (e.recordId > 0L) ids.add(e.recordId) else allValid = false
        }
        out.add(
            CallCluster(
                key = head.callId,
                peerId = head.peerId,
                name = head.name,
                photo = head.photo,
                isGroup = head.isGroup,
                groupId = head.groupId,
                recordIds = ids,
                canRemove = allValid,
                count = bucket.size,
                last = head,
            )
        )
        bucket = ArrayList()
    }

    for (e in entries) {
        val prev = bucket.lastOrNull()
        val merge = prev != null && !e.isGroup && !prev.isGroup &&
            e.peerId > 0L && e.peerId == prev.peerId
        if (merge) {
            bucket.add(e)
        } else {
            flush()
            bucket.add(e)
        }
    }
    flush()
    return out
}

/**
 * #CALLS-SNAP (2026-09-05): Этап А4 + Этап Б плана «звонки.перенос.план.md» —
 * строка-кластер истории/пропущенных (матрица §1.3).
 *
 * Режим А4 (cluster=null, прежние вызовы — «Главная»): имя + статус-строка
 * «Завершённый/Пропущенный/Отменённый/Групповой · длительность ·
 * сегодня/вчера/дата» + иконка направления (входящий — зелёная, исходящий —
 * синяя, пропущенный — красная); клик по строке — redial аудио через хост.
 *
 * Режим Б (cluster != null, история/пропущенные):
 *  - заголовок «Имя (N)» / «Групповой звонок dd.MM.yyyy» (REV-UI §2.3/§2.5);
 *  - redial-кнопки «Аудиозвонок X»/«Видеозвонок X» (testid веба
 *    calls_history_list_audiocall/videocall) — старт через CallStarter
 *    хоста (video=true доходит до SovaApp.pendingOutgoingCallVideo);
 *  - меню «Действия» (calls_history_list_item_menu_button):
 *    «Убрать из списка» → deleteHistoryRecords/deleteGroupHistoryRecords,
 *    «Очистить историю» → clearHistory/clearGroupHistory (с подтверждением —
 *    диалог на уровне секции через onClear);
 *  - у групповых строк стрелки направления нет (REV-UI §2.4).
 */
@Composable
fun CallsClusterRow(
    entry: CallHistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cluster: CallCluster? = null,
    onAudioCall: ((Long) -> Unit)? = null,
    onVideoCall: ((Long) -> Unit)? = null,
    onRemove: ((CallCluster) -> Unit)? = null,
    onClear: ((CallCluster) -> Unit)? = null,
) {
    val c = cluster
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
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
                if (entry.photo != null) {
                    AsyncImage(
                        model = entry.photo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(entry.name.take(1), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rowTitle(entry, c),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Стрелка = НАПРАВЛЕНИЕ звонка (не статус); у «Групповой» стрелки нет.
                    if (!entry.isGroup) {
                        val (icon, tint) = directionIcon(entry)
                        Icon(icon, null, Modifier.size(14.dp), tint = tint)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        statusLine(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // ─── Правый блок веба: redial + «Действия» (только кластерный режим) ───
            if (c != null) {
                val audio = onAudioCall
                if (audio != null) {
                    IconButton(
                        onClick = { audio(c.peerId) },
                        modifier = Modifier.size(36.dp).testTag("calls_history_list_audiocall"),
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = "Аудиозвонок " + rowTitle(entry, c),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val video = onVideoCall
                if (video != null) {
                    IconButton(
                        onClick = { video(c.peerId) },
                        modifier = Modifier.size(36.dp).testTag("calls_history_list_videocall"),
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = "Видеозвонок " + rowTitle(entry, c),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val removeAction = onRemove
                val clearAction = onClear
                if (removeAction != null || clearAction != null) {
                    ClusterActionsMenu(
                        cluster = c,
                        onRemove = removeAction,
                        onClear = clearAction,
                    )
                }
            }
        }
    }
}

/** Меню «Действия» строки (Этап Б3): «Убрать из списка» / «Очистить историю». */
@Composable
private fun ClusterActionsMenu(
    cluster: CallCluster,
    onRemove: ((CallCluster) -> Unit)?,
    onClear: ((CallCluster) -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.size(36.dp).testTag("calls_history_list_item_menu_button"),
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Действия")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Убрать из списка") },
                enabled = cluster.canRemove && cluster.recordIds.isNotEmpty(),
                onClick = {
                    menuOpen = false
                    val remove = onRemove
                    if (remove != null) remove(cluster)
                },
                modifier = Modifier.testTag("calls_row_action_remove"),
            )
            DropdownMenuItem(
                text = { Text("Очистить историю") },
                // Групповая очистка возможна только при известном group_id.
                enabled = !cluster.isGroup || cluster.groupId > 0L,
                onClick = {
                    menuOpen = false
                    val clear = onClear
                    if (clear != null) clear(cluster)
                },
                modifier = Modifier.testTag("calls_row_action_clear"),
            )
        }
    }
}

/** Заголовок строки: «Имя (N)» / «Групповой звонок dd.MM.yyyy» / «Имя». */
private fun rowTitle(entry: CallHistoryEntry, cluster: CallCluster?): String {
    if (entry.isGroup) {
        return "Групповой звонок " + groupDayLabel(entry.timestampSec)
    }
    val c = cluster
    if (c != null && c.count > 1) {
        return entry.name + " (" + c.count + ")"
    }
    return entry.name
}

/** «26.08.2026» — формат заголовка групповой строки веба (REV-UI §2.3). */
private fun groupDayLabel(timestampSec: Long): String {
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru"))
    return format.format(Date(timestampSec * 1000L))
}

/** Иконка направления: пропущенный — красная стрелка, входящий/исходящий — своя. */
private fun directionIcon(entry: CallHistoryEntry): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    if (entry.isMissed) {
        return if (entry.isInbound) {
            Icons.AutoMirrored.Filled.CallReceived to Color(0xFFE53935)
        } else {
            Icons.AutoMirrored.Filled.CallMade to Color(0xFFE53935)
        }
    }
    return if (entry.isInbound) {
        Icons.AutoMirrored.Filled.CallReceived to Color(0xFF4CAF50)
    } else {
        Icons.AutoMirrored.Filled.CallMade to Color(0xFF1976D2)
    }
}

/**
 * Статус-строка вида «Групповой · 8:14 · вчера»: статус (Групповой перекрывает
 * исход, как в вебе) · длительность (если была) · день (сегодня/вчера/дата).
 */
private fun statusLine(entry: CallHistoryEntry): String {
    val sb = StringBuilder()
    if (entry.isGroup) {
        sb.append("Групповой")
    } else {
        when (entry.outcome) {
            CallOutcome.FINISHED -> sb.append("Завершённый")
            CallOutcome.MISSED -> sb.append("Пропущенный")
            CallOutcome.CANCELED -> sb.append("Отменённый")
        }
    }
    if (entry.durationSec > 0) {
        sb.append(" · ")
        sb.append(entry.durationSec.toDurationString())
    }
    sb.append(" · ")
    sb.append(callsDayLabel(entry.timestampSec))
    return sb.toString()
}

/**
 * «сегодня» / «вчера» / «26 авг» / «26 авг 2025» — по локальному календарю
 * (тот же принцип, что Long.toChatDate в :core:common, но в нижнем регистре
 * и с коротким месяцем — формат статус-строки веб-раздела).
 */
internal fun callsDayLabel(timestampSec: Long): String {
    val calNow = Calendar.getInstance()
    val calTs = Calendar.getInstance()
    calTs.timeInMillis = timestampSec * 1000L
    val yearNow = calNow.get(Calendar.YEAR)
    val yearTs = calTs.get(Calendar.YEAR)
    val dayNow = calNow.get(Calendar.DAY_OF_YEAR)
    val dayTs = calTs.get(Calendar.DAY_OF_YEAR)
    val ru = Locale.forLanguageTag("ru")
    return when {
        yearNow == yearTs && dayNow == dayTs -> "сегодня"
        yearNow == yearTs && dayNow - dayTs == 1 -> "вчера"
        yearNow == yearTs -> SimpleDateFormat("d MMM", ru).format(calTs.time)
        else -> SimpleDateFormat("d MMM yyyy", ru).format(calTs.time)
    }
}
