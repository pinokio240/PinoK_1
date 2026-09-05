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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import re.pinok.feature.calls.CallHistoryEntry
import re.pinok.feature.calls.CallOutcome
import re.pinok.util.toDurationString

/**
 * #CALLS-SNAP (2026-09-05): Этап А4 плана «звонки.перенос.план.md» — общий
 * ВИД строки-кластера истории/пропущенных (матрица §1.3, вид — Этап А;
 * полную кластеризацию последовательных звонков и действия строки
 * «Действия/Убрать из списка/redial-кнопки» добавляет Этап Б).
 *
 * Статус-строка: «Завершённый · 8:14 · вчера» / «Пропущенный · вчера» /
 * «Отменённый · сегодня» / «Групповой · 12:03 · 26 авг» + иконка направления
 * (входящий — зелёная, исходящий — синяя, пропущенный — красная).
 * Аватар: фото профиля (обогащение — репозиторий А2) либо инициалы.
 * Строка кликабельна → redial аудио (существующий CallStarter-путь хоста,
 * SovaNavHost onNavigateToCall); video-redial — Этап Б.
 */
@Composable
fun CallsClusterRow(
    entry: CallHistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    entry.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = directionIcon(entry)
                    Icon(icon, null, Modifier.size(14.dp), tint = tint)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusLine(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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
