package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toDurationString
import re.pinok.util.toRelativeTime

private const val TAG = "CallsRecordings"

/** Граничное значение «unix-миллисекунды» (правило CallsSectionRepositoryImpl). */
private const val MS_EPOCH_THRESHOLD = 100_000_000_000L

/** Поля-кандидаты прямой ссылки записи в ответе messages.getCallRecordings. */
private val RECORDING_URL_FIELDS = listOf(
    "url", "file_url", "video_url", "doc_url", "download_url", "link", "player_url", "page_url",
)

internal data class RecordingItem(
    val id: Long,
    val title: String,
    val durationSec: Int,
    val views: Int,
    val timestampSec: Long,
    /** Лучший URL из данных: прямой медиа-поток либо страница vkvideo. */
    val url: String?,
    /** Собранная ссылка vkvideo-страницы (формат thumb среза §4), если id известны. */
    val pageUrl: String?,
    val isPrivate: Boolean,
)

/**
 * #CALLS-SNAP (2026-09-05): Этап В1 плана «звонки.перенос.план.md» (§4-В,
 * матрица §1.4) — секция «Записи звонков» доведена до полного функционала:
 *  - плеер записи: полноэкранный нативный плеер (CallsRecordingPlayerDialog,
 *    Media3/ExoPlayer — нативный эквивалент vkvideo-плеера среза §4) по клику
 *    на превью/play; URL — из данных messagesGetCallRecordings (репозиторий
 *    RECORDINGS, парсинг tolerant: url/file_url/video_url/doc_url/... либо
 *    собранная vkvideo-ссылка owner_id+video_id);
 *  - контролы карточки (testid-набор video_card_* среза §4) маплены по
 *    фактическим возможностям API, честно:
 *      play_36/thumb          → нативный плеер;
 *      download_outline_16    → скачивание в Downloads (DownloadManager);
 *      check_outline_16       → режим мультивыбора (реальная функция
 *                               чекбокса в вебе) с пакетным удалением;
 *      cancel_16              → удаление calls.deleteHistoryRecords
 *                               (фасад callsDeleteHistoryRecords) с
 *                               подтверждением;
 *      more_horizontal_24     → меню: Открыть в VK Видео / Скопировать
 *                               ссылку / Скачать / Удалить;
 *      pen_outline_16         → НЕ рендерится: API переименования записи в
 *                               фасаде CallsApi нет (чужой файл не
 *                               расширяется), имитация запрещена no-stub;
 *      lock_16                → индикатор приватности (is_private).
 *  - «N просмотров · время назад» — фактический формат среза §4.2
 *    (русская плюрализация «просмотр/просмотра/просмотров»); инкремент
 *    просмотров не имитируется — мутатора в фасаде нет (отмечено в отчёте).
 *  - после удаления список обновляется СУЩЕСТВУЮЩИМ публичным
 *    repo.refresh(RECORDINGS, force=true) — StateFlow перерисует сетку.
 *
 * Живые фичи А2-А4 (загрузка через репозиторий, loading/error/empty
 * scaffold, честный empty, счётчик-индикатор шапки) сохранены.
 * #NULL-EXPLICIT: без non-null assertion, safe-call и elvis операторов.
 */
@Composable
fun CallsRecordingsSection(onNavigateToCall: (Long) -> Unit) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repo.recordings.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i(TAG, "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.RECORDINGS, force = false)
    }

    var playing by remember { mutableStateOf<RecordingItem?>(null) }
    var confirmDeleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var deleting by remember { mutableStateOf(false) }

    fun performDelete(ids: List<Long>) {
        if (deleting) return
        scope.launch {
            deleting = true
            var ok = false
            try {
                withContext(Dispatchers.Default) {
                    ok = deps.apiClient.callsDeleteHistoryRecords(ids)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "callsDeleteHistoryRecords failed", e)
            }
            deleting = false
            if (ok) {
                AppLog.i(TAG, "deleted recordings: $ids")
                // Список обновляется существующим публичным refresh репозитория
                repo.refresh(CallsSectionKey.RECORDINGS, force = true)
                selected.clear()
                selectionMode = false
            } else {
                Toast.makeText(context, "Не удалось удалить запись", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет записей звонков",
        onRetry = { repo.refresh(CallsSectionKey.RECORDINGS, force = true) },
        modifier = Modifier.testTag("recordings_section"),
    ) { raw ->
        val items = remember(raw) { raw.mapNotNull { parseRecordingItem(it) } }
        if (items.isEmpty()) {
            // Сырой список не пуст, но строки не распарсились — честный empty.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет записей звонков",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Выбрано: " + selected.size,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (selected.isNotEmpty()) confirmDeleteIds = selected.toList()
                            },
                            enabled = !deleting,
                            modifier = Modifier.testTag("recordings_delete_selected"),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Удалить выбранные",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = {
                            selectionMode = false
                            selected.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Записи звонков",
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
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("recordings_grid"),
                ) {
                    items(items, key = { it.id }) { item ->
                        RecordingItemCard(
                            item = item,
                            selectionMode = selectionMode,
                            isSelected = selected.contains(item.id),
                            onPlay = {
                                val u = item.url
                                if (u == null) {
                                    AppLog.w(TAG, "запись " + item.id + ": URL недоступен в данных API")
                                    Toast.makeText(
                                        context,
                                        "Ссылка на запись недоступна в данных API",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    playing = item
                                }
                            },
                            onToggleSelect = {
                                if (selected.contains(item.id)) {
                                    selected.remove(item.id)
                                } else {
                                    selected.add(item.id)
                                }
                            },
                            onEnterSelection = {
                                selectionMode = true
                                selected.add(item.id)
                            },
                            onDownload = {
                                val u = item.url
                                if (u == null) {
                                    Toast.makeText(
                                        context,
                                        "Прямая ссылка на файл недоступна — откройте запись в VK Видео",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    downloadToDownloads(context, u, item.title, TAG)
                                }
                            },
                            onOpen = {
                                var link = item.pageUrl
                                if (link == null) link = item.url
                                if (link == null) {
                                    Toast.makeText(
                                        context,
                                        "Ссылка на запись недоступна в данных API",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    openUrlExternally(context, link, TAG)
                                }
                            },
                            onCopyLink = {
                                var link = item.pageUrl
                                if (link == null) link = item.url
                                if (link == null) {
                                    Toast.makeText(
                                        context,
                                        "Ссылка на запись недоступна в данных API",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    copyToClipboard(context, link, "Ссылка скопирована", TAG)
                                }
                            },
                            onDelete = { confirmDeleteIds = listOf(item.id) },
                            modifier = Modifier.testTag("recordings_item"),
                        )
                    }
                }
            }
        }
    }

    val p = playing
    if (p != null) {
        val u = p.url
        if (u != null) {
            CallsRecordingPlayerDialog(
                title = p.title,
                url = u,
                onDismiss = { playing = null },
            )
        }
    }

    val delIds = confirmDeleteIds
    if (delIds != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteIds = null },
            title = { Text("Удалить запись звонка?") },
            text = { Text("Запись будет убрана из раздела «Записи звонков» (calls.deleteHistoryRecords).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteIds = null
                        performDelete(delIds)
                    },
                    enabled = !deleting,
                    modifier = Modifier.testTag("recordings_delete_confirm"),
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIds = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun RecordingItemCard(
    item: RecordingItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cardClick: () -> Unit = if (selectionMode) onToggleSelect else onPlay
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .clickable(onClick = cardClick),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isPrivate) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Приватная запись",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Смотреть запись",
                    modifier = Modifier.size(36.dp).testTag("recordings_play_button"),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (isSelected) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    )
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Выбрана",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp).testTag("recordings_duration_badge"),
                ) {
                    Text(
                        item.durationSec.toDurationString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(28.dp).testTag("recordings_more_button"),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Действия с записью",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Открыть в VK Видео") },
                                leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpen()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Скопировать ссылку") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onCopyLink()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Скачать") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDownload()
                                },
                            )
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
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Формат среза §4.2: «0 просмотров · 6 часов назад»
                Text(
                    viewsLabel(item.views) + " · " + item.timestampSec.toRelativeTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectionMode) onToggleSelect() else onEnterSelection()
                        },
                        modifier = Modifier.size(28.dp).testTag("recordings_select_button"),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Выбрать",
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(28.dp).testTag("recordings_download"),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Скачать", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("recordings_delete"),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Удалить",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Русская плюрализация «N просмотр/просмотра/просмотров» (формат среза §4.2). */
private fun viewsLabel(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "просмотр"
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "просмотра"
        else -> "просмотров"
    }
    return "$n $word"
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

private fun JsonObject.boolFieldOrNull(key: String): Boolean? {
    val el = get(key)
    if (el != null && el.isJsonPrimitive) return el.asBoolean
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

internal fun parseRecordingItem(o: JsonObject): RecordingItem? {
    val id = o.longFieldOrNull("id")
    if (id == null) return null
    var title = o.strFieldOrNull("title")
    if (title == null || title.isBlank()) title = "Запись звонка"
    val duration = o.intFieldOrNull("duration")
    val views = o.intFieldOrNull("views")
    var ts = o.longFieldOrNull("date")
    if (ts == null || ts <= 0L) ts = System.currentTimeMillis() / 1000L
    if (ts > MS_EPOCH_THRESHOLD) ts = ts / 1000L
    val mediaUrl = o.firstStrOrNull(RECORDING_URL_FIELDS)
    // Страница vkvideo — формат thumb среза §4: vk.ru/video<owner>_<video>.
    var pageUrl: String? = null
    val owner = o.longFieldOrNull("owner_id")
    val video = o.longFieldOrNull("video_id")
    if (owner != null && video != null && owner != 0L && video != 0L) {
        pageUrl = "https://vkvideo.ru/video" + owner + "_" + video
    }
    if (pageUrl == null) {
        val vidStr = o.strFieldOrNull("video_id")
        if (vidStr != null && vidStr.contains("_")) {
            pageUrl = "https://vkvideo.ru/video" + vidStr
        }
    }
    var url: String? = mediaUrl
    if (url == null) url = pageUrl
    val priv = o.boolFieldOrNull("is_private")
    val isPrivate = if (priv != null) priv else o.boolFieldOrNull("private") != null
    return RecordingItem(
        id = id,
        title = title,
        durationSec = if (duration != null && duration > 0) duration else 0,
        views = if (views != null && views > 0) views else 0,
        timestampSec = ts,
        url = url,
        pageUrl = pageUrl,
        isPrivate = isPrivate,
    )
}
