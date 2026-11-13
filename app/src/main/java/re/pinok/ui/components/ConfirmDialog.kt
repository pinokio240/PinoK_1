package re.pinok.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Волна 2 (2026-11-13): универсальный диалог подтверждения «Вы уверены?».
 *
 * Заменяет ~30 инлайн-AlertDialog в проекте (удалить/забанить/покинуть/
 * очистить/пожаловаться и т.д.).
 *
 * - [title] — заголовок диалога.
 * - [text] — текстовое описание (может быть null).
 * - [confirmLabel] — текст кнопки подтверждения (по умолчанию «Удалить»).
 * - [dismissLabel] — текст кнопки отмены (по умолчанию «Отмена»).
 * - [danger] — true = красный цвет кнопки подтверждения (деструктивное действие).
 * - [onConfirm] — действие при подтверждении.
 * - [onDismiss] — действие при отмене.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String? = null,
    confirmLabel: String = "Удалить",
    dismissLabel: String = "Отмена",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}