package re.pinok.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Панель выбора реакции (как в ВК).
 * Показывается как popup/popover поверх кнопки лайка.
 */
data class ReactionEntry(
    val emoji: String,
    val label: String,
    val reactionId: Int,  // VK API reaction_id
)

/** Стандартный набор реакций ВК. */
val VK_REACTIONS = listOf(
    ReactionEntry("\uD83D\uDC4D", "Класс", 1),
    ReactionEntry("\u2764\uFE0F", "Круто", 2),
    ReactionEntry("\uD83D\uDD25", "Огонь", 3),
    ReactionEntry("\uD83D\uDE02", "Смешно", 4),
    ReactionEntry("\uD83D\uDE2E", "Ух ты", 5),
    ReactionEntry("\uD83D\uDE22", "Жаль", 6),
    ReactionEntry("\uD83D\uDE21", "Злюсь", 7),
    ReactionEntry("\uD83D\uDE4F", "Спасибо", 8),
)

/**
 * ReactionPicker — всплывающая панель эмодзи-реакций.
 * [onSelect] — колбэк с выбранной реакцией.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPicker(
    onDismiss: () -> Unit,
    onSelect: (ReactionEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.Center,
            maxItemsInEachRow = 4,
        ) {
            VK_REACTIONS.forEach { reaction ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelect(reaction)
                            onDismiss()
                        }
                        .padding(6.dp)
                        .width(56.dp),
                ) {
                    Text(
                        text = reaction.emoji,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(40.dp).padding(4.dp),
                    )
                    Text(
                        text = reaction.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}