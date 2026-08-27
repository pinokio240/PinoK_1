package re.pinok.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Post
import re.pinok.util.AppLog

/**
 * Sprint 2, P1-3 (#90): Диалог подтверждения репоста.
 *
 * Показывается при тапе на кнопку репоста (Repeat icon) в action bar поста.
 * Позволяет добавить комментарий к репосту (необязательно) и подтверждает
 * действие перед вызовом `wall.repost`.
 *
 * VK `wall.repost` принимает `object` в формате `wall{owner_id}_{post_id}`.
 * Например:
 *  — пост пользователя: `wall12345_678`
 *  — пост группы:      `wall-12345_678` (owner_id отрицательный)
 *
 * @param post       Репостимый пост. Берётся `post.ownerId` и `post.id`.
 * @param onDismiss  Закрытие диалога (cancel или после успеха).
 * @param onSuccess  Вызывается после успешного репоста с `(newPostId, repostsCount)`.
 */
@Composable
fun RepostDialog(
    post: Post,
    onDismiss: () -> Unit,
    onSuccess: (newPostId: Long, repostsCount: Int) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Поделиться записью") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Запись будет опубликована на вашей стене" +
                        (if (post.text.isNotBlank()) " с вашим комментарием." else "."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ваш комментарий (необязательно)") },
                    minLines = 2,
                    maxLines = 5,
                )
                error?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (sending) return@TextButton
                    scope.launch {
                        sending = true
                        error = null
                        try {
                            // object = "wall{ownerId}_{postId}"
                            val obj = "wall${post.ownerId}_${post.id}"
                            val (newPostId, repostsCount) = app.apiClient.wallRepost(obj, message.trim())
                            if (newPostId > 0) {
                                AppLog.i("RepostDialog", "Reposted: newPostId=$newPostId, reposts=$repostsCount")
                                onSuccess(newPostId, repostsCount)
                                onDismiss()
                            } else {
                                error = "Не удалось сделать репост. Попробуйте позже."
                                AppLog.w("RepostDialog", "wallRepost failed for $obj")
                            }
                        } catch (e: Exception) {
                            AppLog.e("RepostDialog", "wallRepost exception", e)
                            error = "Ошибка: ${e.message}"
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = !sending,
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Поделиться")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !sending,
            ) { Text("Отмена") }
        },
    )
}
