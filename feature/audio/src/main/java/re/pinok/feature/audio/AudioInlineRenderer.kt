package re.pinok.feature.audio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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

/**
 * #ARCH-CONTAINERS (Этап 1.5-б): инлайн-рендер аудио-вложений чата — перенос
 * аудио-ветки из ChatDetailScreen (MessageBubble, компосабл AudioAttachmentRow
 * #59) в контейнер :feature:audio. Хост находит рендерер через реестр
 * (AttachmentRenderer, rendererKey "audio_inline") и вызывает этот компосабл,
 * прокидывая:
 *  - [title]/[artist]/[durationSec] — данные вложения ТОЛЬКО примитивами
 *    (у VK-аудио-вложений messages.get нет mime; модель re.pinok.data.model.Track
 *    остаётся в :app — хост распаковывает поля и передаёт числа/строки);
 *  - [textColor] — цвет текста бабла (исходящий/входящий/bubble-less решает хост);
 *  - [onPlay]  — тап по строке: хост решает — запуск трека в PlayerConnection
 *    (P2.2) или toggle selection (Fix #244); контейнер плеера и selection-
 *    состояния не знает;
 *  - [onLongPress] — long-press (selection/context-menu хоста).
 *
 * Поведение = прежний host-рендер 1:1: строка во всю ширину с фоном
 * textColor/8%, иконка ▶ 24dp, название (bodySmall, Medium), артист
 * (labelSmall, 60%), длительность «M:SS» (labelSmall, 50%); скругление 8dp.
 *
 * ОТЛИЧИЕ от прежнего host-кода (сознательное, по образцу PhotosInlineRenderer):
 * selection-логика Fix #244 (LocalAttachmentSelection) осталась в хосте —
 * хост-ветка "audio_inline" оборачивает onPlay/onLongPress. Контейнер
 * compose-CompositionLocal'ов хоста не знает.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioInlineRenderer(
    title: String,
    artist: String,
    durationSec: Int,
    textColor: Color,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Воспроизвести",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val min = durationSec / 60
        val sec = durationSec % 60
        Text("$min:${"%02d".format(sec)}", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
    }
}
