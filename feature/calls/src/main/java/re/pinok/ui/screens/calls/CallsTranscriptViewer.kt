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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import re.pinok.util.toAbsoluteTime
import re.pinok.util.toDurationString

/**
 * #CALLS-SNAP (2026-09-05): Этап В2 плана «звонки.перенос.план.md» (§4-В,
 * матрица §1.5) — просмотр текста расшифровки + правка + удаление.
 *
 * Элемент списка расшифровок в DOM-снапшоте 2608 отсутствовал (пустое
 * состояние, срез §5) — дизайн по аналогии с записями (план §4-В2) и по
 * факту бандла 2308 (Ns-подпись, меню download/delete). Экран —
 * полноэкранный Dialog ВНУТРИ секции (маршруты не расширяются):
 *  - текст расшифровки, выделяемый (SelectionContainer); если текст не
 *    пришёл в данных — честная надпись вместо имитации;
 *  - метаданные: чат · «в HH:MM» · размер · длительность · абсолютная дата;
 *  - «Правка» → OutlinedTextField → «Сохранить» → onSave (секция вызывает
 *    callsEditAsrTranscription и обновляет список; кнопка блокируется на
 *    время запроса);
 *  - «Удалить» → подтверждение (AlertDialog) → onDelete;
 *  - «Скачать файл»/«Открыть» — реальные действия файла расшифровки.
 * #NULL-EXPLICIT: без non-null assertion, safe-call и elvis операторов.
 */
@Composable
internal fun CallsTranscriptViewerDialog(
    item: TranscriptItem,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDownloadFile: () -> Unit,
    onOpenFile: () -> Unit,
    onClose: () -> Unit,
) {
    val initialText = item.text
    var editing by remember(item.id) { mutableStateOf(false) }
    var draft by remember(item.id) { mutableStateOf(if (initialText != null) initialText else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = transcriptMetaLine(item)
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("transcript_viewer_close"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                            .testTag("transcript_viewer_editor"),
                    )
                } else {
                    SelectionContainer(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("transcript_viewer_text"),
                    ) {
                        val t = item.text
                        if (t == null) {
                            Text(
                                "Текст расшифровки отсутствует в данных API. " +
                                    "Файл расшифровки можно скачать или открыть ниже.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                t,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val docUrl = item.docUrl
                    if (docUrl != null) {
                        TextButton(
                            onClick = onDownloadFile,
                            modifier = Modifier.testTag("transcript_viewer_download"),
                        ) {
                            Text("Скачать")
                        }
                        TextButton(
                            onClick = onOpenFile,
                            modifier = Modifier.testTag("transcript_viewer_open"),
                        ) {
                            Text("Открыть")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (item.text != null) {
                        if (editing) {
                            TextButton(onClick = { editing = false }, enabled = !saving) {
                                Text("Отмена")
                            }
                            TextButton(
                                onClick = { onSave(draft) },
                                enabled = !saving && draft.isNotBlank(),
                                modifier = Modifier.testTag("transcript_viewer_save"),
                            ) {
                                Text(if (saving) "Сохранение…" else "Сохранить")
                            }
                        } else {
                            TextButton(
                                onClick = { editing = true },
                                modifier = Modifier.testTag("transcript_viewer_edit"),
                            ) {
                                Text("Правка")
                            }
                        }
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.testTag("transcript_viewer_delete"),
                    ) {
                        Text("Удалить")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить расшифровку?") },
            text = { Text("Расшифровка будет удалена безвозвратно (calls.deleteAsrTranscriptions).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/** Метаданные шапки: чат · «в HH:MM» · размер · длительность · дата. */
private fun transcriptMetaLine(item: TranscriptItem): String {
    val parts = ArrayList<String>(5)
    val chat = item.chatTitle
    if (chat != null && chat.isNotBlank()) parts.add(chat)
    if (item.docUrl != null) {
        val tl = item.timeLabel
        if (tl != null) parts.add(tl)
    }
    val size = item.sizeLabel
    if (size != null) parts.add(size)
    if (item.durationSec > 0) parts.add(item.durationSec.toDurationString())
    val absolute = item.timestampSec.toAbsoluteTime()
    if (absolute.isNotBlank()) parts.add(absolute)
    return parts.joinToString(" · ")
}
