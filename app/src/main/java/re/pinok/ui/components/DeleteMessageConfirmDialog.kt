// File: ui/components/DeleteMessageConfirmDialog.kt
// DELETE-MSG-CONFIRM-DIALOG: reusable composable диалога подтверждения
// удаления сообщения(й) в чатах PinoK. Используется в ChatDetailScreen
// (одиночное и массовое удаление). Эталон: VK-стиль (скриншот пользователя).
package re.pinok.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * DELETE-MSG-CONFIRM-DIALOG: модальный диалог подтверждения удаления
 * сообщений (одиночного или массового). Стиль — VK-эталон: белый фон с
 * закруглёнными углами, заголовок по левому краю, опц. чекбокс «Удалить
 * для всех», кнопки «НЕТ»/«ДА» внизу справа (ДА — error-color для акцента
 * деструктивного действия).
 *
 * @param count 1 = одиночное удаление («Вы действительно хотите удалить?»),
 *              N > 1 = массовое («Удалить N сообщений?»).
 * @param initialDeleteForAll начальное состояние чекбокса.
 * @param hideForAllOption true = скрыть чекбокс (для чужого сообщения или ЛС
 *                         без прав на delete-for-all).
 * @param onDismiss колбэк закрытия диалога без подтверждения (тап по scrim,
 *                  системный Back, кнопка «НЕТ»).
 * @param onConfirm колбэк подтверждения. Параметр = состояние чекбокса
 *                  «Удалить для всех» на момент тапа по «ДА».
 */
@Composable
fun DeleteMessageConfirmDialog(
    /** 1 = одиночное удаление, N = массовое. Влияет на текст: «N сообщений?» */
    count: Int = 1,
    /** Начальное состояние чекбокса «Удалить для всех» */
    initialDeleteForAll: Boolean = false,
    /** true = скрыть чекбокс (для чужого сообщения / ЛС без прав) */
    hideForAllOption: Boolean = false,
    onDismiss: () -> Unit,
    /** Подтверждение. deleteForAll = состояние чекбокса на момент «ДА» */
    onConfirm: (deleteForAll: Boolean) -> Unit,
) {
    var deleteForAll by remember { mutableStateOf(initialDeleteForAll) }

    val titleText = if (count <= 1) {
        "Вы действительно хотите удалить?"
    } else {
        "Удалить $count сообщений?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titleText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
        },
        text = {
            if (!hideForAllOption) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deleteForAll,
                        onCheckedChange = { deleteForAll = it },
                    )
                    Text(
                        text = "Удалить для всех",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteForAll) }) {
                Text(
                    text = "ДА",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "НЕТ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
