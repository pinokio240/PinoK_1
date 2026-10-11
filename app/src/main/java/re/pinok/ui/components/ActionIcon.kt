package re.pinok.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import re.pinok.util.toCountString

// П-8-REACT: ExperimentalFoundationApi — combinedClickable для long-press
// (в foundation 1.8 / BOM 2025.06 stable-перегрузка с named onClick/onLongClick
// opt-in не требует — аннотация страховочная, как в FeedScreen/ProfileScreen).
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIcon(
    icon: ImageVector,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    // П-8-REACT: long-press (пикер реакций на лайке); null — прежнее поведение.
    // Прочие вызовы (комментарий/репост) параметр не передают. Named-аргументы
    // combinedClickable; onLongClick == null деградирует до простого клика.
    onLongClick: (() -> Unit)? = null,
    // П-6a: disabled-состояние — во время API-полёта clickable не вешается.
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(
                if (onClick != null && enabled) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toCountString(), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}