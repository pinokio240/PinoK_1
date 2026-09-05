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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toRelativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CallsTranscripts"

/** Граничное значение «unix-миллисекунды» (правило CallsSectionRepositoryImpl). */
private const val MS_EPOCH_THRESHOLD = 100_000_000_000L

/** Поля-кандидаты файла расшифровки (факт бандла: doc_url, срез §5/§9.2). */
private val TRANSCRIPT_DOC_URL_FIELDS = listOf("doc_url", "url", "file_url", "link")

/** Поля-кандидаты размера файла (факт бандла: doc_size — Ns-подпись). */
private val TRANSCRIPT_DOC_SIZE_FIELDS = listOf("doc_size", "size")

/** Поля-кандидаты текста расшифровки (точное имя полем calls.getAsrTranscriptions не подтверждено). */
private val TRANSCRIPT_TEXT_FIELDS = listOf(
    "text", "transcription", "transcript", "content", "body", "result",
)

/**
 * Элемент списка расшифровок (дизайн по факту бандла 2308, chunk 97907:
 * Ys-компонент + Ns-подпись — title=name либо «удалённая расшифровка»,
 * subtitle=[«в HH:MM» при doc_url, размер, chat.title].join(" · ")).
 * Элемент списка в DOM-снапшоте 2608 отсутствует (пустое состояние) —
 * состав восстановлен по коду бандла, см. срез §5.
 */
internal data class TranscriptItem(
    val id: Long,
    val title: String,
    /** Файл расшифровки (doc_url). Нет файла → веб помечает «удалённая». */
    val docUrl: String?,
    /** «в 14:05» — по Ns строится только при наличии doc_url. */
    val timeLabel: String?,
    /** Размер файла «2,3 МБ» (по Ns: KB/MB/GB). */
    val sizeLabel: String?,
    val chatTitle: String?,
    /** Текст расшифровки (если возвращается в данных списка). */
    val text: String?,
    val durationSec: Int,
    val timestampSec: Long,
    /** doc_url отсутствует → веб рендерит «удалённую расшифровку». */
    val isDeleted: Boolean,
)

/**
 * #CALLS-SNAP (2026-09-05): Этап В2 плана «звонки.перенос.план.md» (§4-В,
 * матрица §1.5) — секция «Расшифровки звонков» доведена до полного функционала:
 *  - список calls.getAsrTranscriptions (репозиторий TRANSCRIPTS), элемент:
 *    иконка + название (+ «Удалённая расшифровка» без doc_url, вторичный цвет
 *    — как в вебе) + подпись «в HH:MM · размер · чат» (Ns-формат бандла) +
 *    «время назад» + превью текста (если текст есть в данных);
 *  - клик «Открыть» → просмотр текста (CallsTranscriptViewerDialog,
 *    полноэкранный Dialog внутри секции; маршруты не расширяются);
 *  - «Правка» → редактирование текста → callsEditAsrTranscription
 *    (фасад callsEditAsrTranscription{transcriptionId, text});
 *  - «Удалить» → callsDeleteAsrTranscriptions (фасад
 *    callsDeleteAsrTranscriptions{transcriptionIds}) с подтверждением
 *    (веб-бандл шлёт doc_ids — расхождение имени параметра реализации
 *    фасада зафиксировано в отчёте Этапа В, файл VKApiClient чужой);
 *  - «Скачать файл» → Downloads (DownloadManager), «Открыть» → браузер
 *    (реальные действия меню расшифровки: calls_decoding_page_menu_download/
 *    delete — из бандла; правки в веб-меню нет — добавлена по плану §1.5);
 *  - пустое состояние «У вас нет расшифровок» (срез §5) — scaffold А4.
 * После правки/удаления список обновляется СУЩЕСТВУЮЩИМ публичным
 * repo.refresh(TRANSCRIPTS, force=true).
 * #NULL-EXPLICIT: без non-null assertion, safe-call и elvis операторов.
 */
@Composable
fun CallsTranscriptsSection(onNavigateToCall: (Long) -> Unit) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repo.transcripts.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i(TAG, "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.TRANSCRIPTS, force = false)
    }

    var viewerItem by remember { mutableStateOf<TranscriptItem?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun performDelete(id: Long) {
        scope.launch {
            var ok = false
            try {
                withContext(Dispatchers.Default) {
                    ok = deps.apiClient.callsDeleteAsrTranscriptions(listOf(id))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "callsDeleteAsrTranscriptions failed", e)
            }
            if (ok) {
                AppLog.i(TAG, "deleted transcription: $id")
                // Список обновляется существующим публичным refresh репозитория
                repo.refresh(CallsSectionKey.TRANSCRIPTS, force = true)
                viewerItem = null
                Toast.makeText(context, "Расшифровка удалена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не удалось удалить расшифровку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun performSave(item: TranscriptItem, newText: String) {
        if (saving) return
        scope.launch {
            saving = true
            var ok = false
            try {
                withContext(Dispatchers.Default) {
                    ok = deps.apiClient.callsEditAsrTranscription(item.id.toString(), newText)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "callsEditAsrTranscription failed", e)
            }
            saving = false
            if (ok) {
                AppLog.i(TAG, "transcription edited: " + item.id)
                // Список обновляется существующим публичным refresh репозитория
                repo.refresh(CallsSectionKey.TRANSCRIPTS, force = true)
                viewerItem = null
                Toast.makeText(context, "Правка сохранена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не удалось сохранить правку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "У вас нет расшифровок",
        onRetry = { repo.refresh(CallsSectionKey.TRANSCRIPTS, force = true) },
        modifier = Modifier.testTag("transcripts_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { parseTranscriptItem(it) } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "У вас нет расшифровок",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Расшифровки звонков",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        items.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("transcripts_list"),
                ) {
                    items(items, key = { it.id }) { item ->
                        TranscriptItemCard(
                            item = item,
                            onOpen = { viewerItem = item },
                            onDownload = {
                                val u = item.docUrl
                                if (u == null) {
                                    Toast.makeText(
                                        context,
                                        "Файл расшифровки недоступен (запись удалена)",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    downloadToDownloads(context, u, item.title, TAG)
                                }
                            },
                            onDelete = { confirmDeleteId = item.id },
                            modifier = Modifier.testTag("transcripts_item"),
                        )
                    }
                }
            }
        }
    }

    val v = viewerItem
    if (v != null) {
        val vDocUrl = v.docUrl
        CallsTranscriptViewerDialog(
            item = v,
            saving = saving,
            onSave = { newText -> performSave(v, newText) },
            onDelete = { performDelete(v.id) },
            onDownloadFile = {
                if (vDocUrl == null) {
                    Toast.makeText(
                        context,
                        "Файл расшифровки недоступен (запись удалена)",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    downloadToDownloads(context, vDocUrl, v.title, TAG)
                }
            },
            onOpenFile = {
                if (vDocUrl == null) {
                    Toast.makeText(
                        context,
                        "Файл расшифровки недоступен (запись удалена)",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    openUrlExternally(context, vDocUrl, TAG)
                }
            },
            onClose = { viewerItem = null },
        )
    }

    val delId = confirmDeleteId
    if (delId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Удалить расшифровку?") },
            text = { Text("Расшифровка будет удалена безвозвратно (calls.deleteAsrTranscriptions).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteId = null
                        performDelete(delId)
                    },
                    modifier = Modifier.testTag("transcripts_delete_confirm"),
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun TranscriptItemCard(
    item: TranscriptItem,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isDeleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                // Подпись Ns бандла: [«в HH:MM», размер, чат].join(" · ")
                val subtitle = transcriptSubtitle(item)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.timestampSec.toRelativeTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val t = item.text
                if (t != null && t.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpen,
                modifier = Modifier.testTag("transcripts_open_button"),
            ) {
                Text("Открыть")
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp).testTag("transcripts_menu_button"),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Действия с расшифровкой",
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (item.docUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Скачать файл") },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            },
                            modifier = Modifier.testTag("transcripts_download"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("transcripts_delete"),
                    )
                }
            }
        }
    }
}

/** Подпись по Ns бандла: [«в HH:MM» (только при doc_url), размер, чат].join(" · "). */
private fun transcriptSubtitle(item: TranscriptItem): String {
    val parts = ArrayList<String>(3)
    if (item.docUrl != null) {
        val tl = item.timeLabel
        if (tl != null) parts.add(tl)
    }
    val size = item.sizeLabel
    if (size != null) parts.add(size)
    val chat = item.chatTitle
    if (chat != null && chat.isNotBlank()) parts.add(chat)
    return parts.joinToString(" · ")
}

/** Размер файла по Ns бандла: ГБ (>=2^30), МБ (>=2^20), КБ (>=2^10, округление/1 знак). */
private fun docSizeLabel(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return ""
    val ru = Locale.forLanguageTag("ru")
    return when {
        sizeBytes >= 1073741824L -> String.format(ru, "%.1f ГБ", sizeBytes / 1073741824.0)
        sizeBytes >= 1048576L -> String.format(ru, "%.1f МБ", sizeBytes / 1048576.0)
        sizeBytes >= 1024L -> String.format(ru, "%d КБ", Math.round(sizeBytes / 1024.0))
        else -> String.format(ru, "%.1f КБ", sizeBytes / 1024.0)
    }
}

// ─── парсинг (новый код — #NULL-EXPLICIT: без non-null assertion,
// safe-call и elvis операторов) ───

private fun JsonObject.strFieldOrNull(key: String): String? {
    val el = get(key)
    if (el != null && el.isJsonPrimitive) return el.asString
    return null
}

private fun JsonObject.longFieldOrNull(key: String): Long? {
    val el = get(key)
    if (el != null && el.isJsonPrimitive) return el.asLong
    return null
}

private fun JsonObject.intFieldOrNull(key: String): Int? {
    val el = get(key)
    if (el != null && el.isJsonPrimitive) return el.asInt
    return null
}

private fun JsonObject.firstStrOrNull(keys: List<String>): String? {
    for (k in keys) {
        val el = get(k)
        if (el != null && el.isJsonPrimitive) {
            val v = el.asString
            if (v.isNotBlank()) return v
        }
    }
    return null
}

internal fun parseTranscriptItem(o: JsonObject): TranscriptItem? {
    // Факт бандла: элемент идентифицируется doc_id (deleteAsrTranscriptions{doc_ids}).
    var id = o.longFieldOrNull("doc_id")
    if (id == null) id = o.longFieldOrNull("id")
    if (id == null) return null
    var name = o.strFieldOrNull("name")
    if (name == null) name = o.strFieldOrNull("title")
    val docUrl = o.firstStrOrNull(TRANSCRIPT_DOC_URL_FIELDS)
    val baseTitle = if (name != null && name.isNotBlank()) name else "Расшифровка звонка"
    val title = if (docUrl == null) "Удалённая расшифровка" else baseTitle
    var ts = o.longFieldOrNull("date")
    if (ts == null || ts <= 0L) ts = o.longFieldOrNull("created")
    if (ts == null || ts <= 0L) ts = System.currentTimeMillis() / 1000L
    if (ts > MS_EPOCH_THRESHOLD) ts = ts / 1000L
    // «в 14:05» — Ns строит время только при наличии doc_url
    var timeLabel: String? = null
    if (docUrl != null) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeLabel = "в " + fmt.format(Date(ts * 1000L))
    }
    var sizeLabel: String? = null
    val size = o.longFieldOrNull("doc_size")
    if (size == null) {
        val alt = o.longFieldOrNull("size")
        if (alt != null) sizeLabel = docSizeLabel(alt)
    } else {
        sizeLabel = docSizeLabel(size)
    }
    var chatTitle: String? = null
    val chatEl = o.get("chat")
    if (chatEl != null && chatEl.isJsonObject) {
        val titleEl = chatEl.asJsonObject.get("title")
        if (titleEl != null && titleEl.isJsonPrimitive) chatTitle = titleEl.asString
    }
    val text = o.firstStrOrNull(TRANSCRIPT_TEXT_FIELDS)
    val duration = o.intFieldOrNull("duration")
    return TranscriptItem(
        id = id,
        title = title,
        docUrl = docUrl,
        timeLabel = timeLabel,
        sizeLabel = sizeLabel,
        chatTitle = chatTitle,
        text = text,
        durationSec = if (duration != null && duration > 0) duration else 0,
        timestampSec = ts,
        isDeleted = docUrl == null,
    )
}
