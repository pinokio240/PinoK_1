// File: ui/screens/community/PostActionsMenu.kt
// #ADMIN-POST-ACTIONS: меню действий администратора над постом сообщества.
// Источник: HAR «сообщество действи с постами».
// 7 действий: Редактировать, Удалить, Восстановить,
// Открыть/Закрыть комментарии, Закрепить/Убрать из «Главное».
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog

@Composable
fun PostActionsMenu(
    groupId: Long,
    postId: Long,
    isCommentsClosed: Boolean = false,
    isPinnedInMain: Boolean = false,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onSoftDelete: (() -> Unit)? = null,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun run(label: String, okMsg: String, failMsg: String, block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = block()
                Toast.makeText(context, if (ok) okMsg else failMsg, Toast.LENGTH_SHORT).show()
                if (ok) onChanged()
            } catch (e: Exception) {
                AppLog.e("PostActionsMenu", "$label failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = { Text("Действия с записью") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (onEdit != null) {
                    MenuRow("Редактировать") { onDismiss(); onEdit() }
                    HorizontalDivider()
                }
                MenuRow("Удалить") {
                    if (onSoftDelete != null) {
                        onSoftDelete()
                    } else {
                        run("wallDelete", "Запись удалена", "Не удалось удалить") {
                            app.apiClient.wallDelete(-groupId, postId)
                        }
                    }
                }
                HorizontalDivider()
                if (isCommentsClosed) {
                    MenuRow("Открыть комментарии") {
                        run("wallOpenComments", "Комментарии открыты", "Не удалось открыть") {
                            app.apiClient.wallOpenComments(groupId, postId)
                        }
                    }
                } else {
                    MenuRow("Закрыть комментарии") {
                        run("wallCloseComments", "Комментарии закрыты", "Не удалось закрыть") {
                            app.apiClient.wallCloseComments(groupId, postId)
                        }
                    }
                }
                HorizontalDivider()
                if (isPinnedInMain) {
                    MenuRow("Убрать из «Главное»") {
                        run("ownersUnpinFromMainTab", "Убрано из «Главное»", "Не удалось убрать") {
                            app.apiClient.ownersRemoveFromMainTab(groupId, postId)
                        }
                    }
                } else {
                    MenuRow("Закрепить в «Главное»") {
                        run("ownersPinToMainTab", "Закреплено в «Главное»", "Не удалось закрепить") {
                            app.apiClient.ownersPinToMainTab(groupId, postId)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
