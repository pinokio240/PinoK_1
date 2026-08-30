package re.pinok.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import re.pinok.util.AppLog
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Lvl(val label: String, val char: String, val color: Color)

// #LOG-CALLS-FILTER (2026-08-30): теги звонковой цепочки для фильтра «Звонки» и
// копирования сегмента звонка. Пользователь присылает лог вместо скриншотов
// (файлы в чат не доходят — только текст), поэтому в фильтр включены ВСЕ
// смежные теги: сам звонковый стек + SovaApp (session_key/anonymLogin) +
// VKApiClient (vchat-HTTP, ошибки транспорта).
private val CALL_TAGS = listOf(
    "/CallScreen:", "/CallSignaling:", "/WebRtcEngine:", "/Queuev4Client:",
    "/SovaApp:", "/VKApiClient:",
)

private fun isCallLine(line: String): Boolean = CALL_TAGS.any { line.contains(it) }

/**
 * Full-screen dialog with the in-app log viewer + UTF-8 export action.
 *
 * Triggered via [LogDialogState.show] — host renders [LogViewerDialog] at the
 * Activity root, which inflates this content when visible.
 *
 * Export path: writes `sova_logs_<timestamp>.txt` (UTF-8, BOM-less) to
 * `cacheDir/logs/` then fires an `Intent.ACTION_SEND` with a `FileProvider`
 * URI so the user can share / save via any app (Telegram, email, Files, etc.).
 *
 * Uses `Dialog` (not `AlertDialog`) with `usePlatformDefaultWidth = false` so
 * it covers the whole window — logcat-style full-screen experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerDialogContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val levels = remember(isDark) {
        listOf(
            Lvl("Verbose", "V", if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161)),
            Lvl("Debug",   "D", if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)),
            Lvl("Info",    "I", if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)),
            Lvl("Warn",    "W", if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)),
            Lvl("Error",   "E", if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)),
        )
    }
    val defaultLevels: Set<String> = setOf("V", "D", "I", "W", "E")
    var enabledLevels by remember { mutableStateOf(defaultLevels) }

    var logLines by remember { mutableStateOf(AppLog.snapshot()) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // #LOG-CALLS-FILTER: чип «Звонки» — только теги звонковой цепочки.
    var callsOnly by remember { mutableStateOf(false) }

    // Auto-refresh every 2s while open.
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(2000L)
            logLines = AppLog.snapshot()
        }
    }

    val filtered = remember(logLines, enabledLevels, searchQuery, callsOnly) {
        val query = searchQuery.trim()
        logLines.filter { line ->
            val firstSpace = line.indexOf(' ')
            if (firstSpace < 0) return@filter false
            val afterTs = line.substring(firstSpace + 1)
            val lvlChar = afterTs.firstOrNull() ?: '?'
            val levelOk = lvlChar.toString() in enabledLevels
            if (!levelOk) return@filter false
            // #LOG-CALLS-FILTER (2026-08-30): чип «Звонки» — только звонковая цепочка
            if (callsOnly && !isCallLine(line)) return@filter false
            // Поиск по tag/text (case-insensitive)
            if (query.isNotEmpty()) {
                line.contains(query, ignoreCase = true)
            } else {
                true
            }
        }
    }

    val listState = rememberLazyListState()

    // Контрастные цвета для top bar — явно, чтобы не зависеть от наследования
    // LocalContentColor (которое в Dialog может быть тусклым на 0xFF0A0A0A surface).
    val topBarBg = MaterialTheme.colorScheme.surface
    val iconTint = MaterialTheme.colorScheme.onSurface
    val iconTintDanger = MaterialTheme.colorScheme.error

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Top bar — обёрнут в Surface с явными container/content цветами,
            // чтобы иконки гарантированно контрастировали с фоном в любой теме.
            Surface(
                color = topBarBg,
                contentColor = iconTint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Логи (${filtered.size}/${logLines.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            logLines = AppLog.snapshot()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = iconTint,
                        ),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить",
                            tint = iconTint)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                // #33: Экспорт детального дампа логов (вместо plain snapshot).
                                // dump включает: timestamp, level, thread, caller (file:line#method),
                                // message, context map, полную цепочку Throwable (cause + suppressed).
                                // Это формат, который разработчик получает для глубокой диагностики.
                                logLines = AppLog.snapshot()
                                val dump = AppLog.exportDetailed()
                                val msg = exportDetailedToCache(context, dump)
                                exportStatus = msg
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = iconTint,
                        ),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Экспорт логов",
                            tint = iconTint)
                    }
                    IconButton(
                        onClick = {
                            // #LOG-CALLS-FILTER (2026-08-30): копирование отфильтрованных строк
                            // в буфер — пользователь вставляет лог прямо в чат ТЕКСТОМ
                            // (txt-файлы через шлюз не доходят). С чипом «Звонки» копируется
                            // компактный сегмент звонка вместо мегабайтного полного дампа.
                            scope.launch {
                                logLines = AppLog.snapshot()
                                val text = filtered.joinToString("\n")
                                if (text.isBlank()) {
                                    exportStatus = "Нечего копировать — лог пуст"
                                } else {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                    if (cm != null) {
                                        cm.setPrimaryClip(ClipData.newPlainText("PinoK logs", text))
                                        exportStatus = "Скопировано ${filtered.size} строк — вставь в чат"
                                    } else {
                                        exportStatus = "Clipboard недоступен"
                                    }
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = iconTint,
                        ),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать логи",
                            tint = iconTint)
                    }
                    IconButton(
                        onClick = {
                            AppLog.clear()
                            logLines = emptyList()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = iconTintDanger,
                        ),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить",
                            tint = iconTintDanger)
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = iconTint,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть",
                            tint = iconTint)
                    }
                }
            }

            // Level filter chips — с явными цветами для контраста
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                levels.forEach { lvl ->
                    val selected = lvl.char in enabledLevels
                    FilterChip(
                        selected = selected,
                        onClick = {
                            enabledLevels = if (selected) enabledLevels - lvl.char
                                            else enabledLevels + lvl.char
                        },
                        label = {
                            Text(
                                lvl.label,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(lvl.color),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
                FilterChip(
                    selected = enabledLevels.size == levels.size,
                    onClick = { enabledLevels = levels.map { it.char }.toSet() },
                    label = {
                        Text(
                            "Все",
                            color = if (enabledLevels.size == levels.size)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                // #LOG-CALLS-FILTER (2026-08-30): чип «Звонки» — только теги звонковой
                // цепочки (CallScreen/CallSignaling/WebRtcEngine/Queuev4Client/SovaApp/
                // VKApiClient). Включает и W/E из этих тегов — они всегда в буфере.
                FilterChip(
                    selected = callsOnly,
                    onClick = { callsOnly = !callsOnly },
                    label = {
                        Text(
                            "Звонки",
                            fontWeight = FontWeight.Bold,
                            color = if (callsOnly) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            // Search field — с явными цветами для контраста в тёмной теме
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Поиск по tag или тексту…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = OutlinedTextFieldDefaults.shape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            // Разделитель между контролями и списком логов
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )

            Spacer(Modifier.height(2.dp))

            // Log list
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Нет записей",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // audit Low: key по индексу, а не по всей строке —
                    // иначе дубликаты строк дают warning о дублирующихся ключах.
                    itemsIndexed(filtered, key = { idx, _ -> idx }) { _, line ->
                        LogLineRow(line = line, levels = levels)
                    }
                }
            }

            // Export status toast
            exportStatus?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(3000L)
                    exportStatus = null
                }
            }
        }
    }
}

@Composable
private fun LogLineRow(line: String, levels: List<Lvl>) {
    val firstSpace = line.indexOf(' ')
    if (firstSpace < 0) return
    val timestamp = line.substring(0, firstSpace)
    val rest = line.substring(firstSpace + 1)

    val slashIdx = rest.indexOf('/')
    if (slashIdx < 0) return
    val lvlChar = rest.substring(0, slashIdx)
    val tagAndMsg = rest.substring(slashIdx + 1)

    val colonIdx = tagAndMsg.indexOf(": ")
    val tag = if (colonIdx >= 0) tagAndMsg.substring(0, colonIdx) else tagAndMsg
    val msg = if (colonIdx >= 0) tagAndMsg.substring(colonIdx + 2) else ""

    val formattedTs = try {
        val ms = timestamp.toLong()
        val totalSec = ms / 1000
        val h = (totalSec / 3600) % 24
        val m = (totalSec / 60) % 60
        val s = totalSec % 60
        val millis = ms % 1000
        "%02d:%02d:%02d.%03d".format(h, m, s, millis)
    } catch (_: NumberFormatException) { timestamp }

    val lvlColor = levels.firstOrNull { it.char == lvlChar }?.color
        ?: MaterialTheme.colorScheme.onSurface

    val bgForLevel = when (lvlChar) {
        "E" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        "W" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)) {
                append("$formattedTs  ")
            }
            withStyle(SpanStyle(color = lvlColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)) {
                append("[$lvlChar] ")
            }
            withStyle(SpanStyle(color = lvlColor.copy(alpha = 0.7f), fontSize = 10.sp)) {
                append("$tag: ")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)) {
                append(msg)
            }
        },
        fontFamily = FontFamily.Monospace,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(bgForLevel)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/**
 * Writes the detailed log dump (produced by [AppLog.exportDetailed]) to a UTF-8
 * text file in `cacheDir/logs/` and returns a share-Intent-friendly status message.
 *
 * #33: Формат дампа включает все диагностические поля — timestamp, level, thread,
 * caller (file:line#method), message, context map и полную цепочку Throwable.
 * Это позволяет разработчику точно определить источник проблемы по присланному логу.
 *
 * UTF-8 is forced explicitly via `OutputStreamWriter(out, Charsets.UTF_8)`.
 * No BOM — Android / Linux tools handle BOM-less UTF-8 correctly everywhere.
 *
 * After writing, fires `Intent.ACTION_SEND` with `type=text/plain` and a
 * `FileProvider` URI so the user can share via Telegram / email / Files.
 *
 * @return human-readable status message for the in-app toast.
 */
private suspend fun exportDetailedToCache(context: Context, dump: String): String =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "pinok_logs_$ts.txt")

            // Write UTF-8 explicitly, BOM-less — Android / Linux tools handle
            // BOM-less UTF-8 correctly everywhere.
            file.outputStream().use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { w -> w.write(dump) }
            }

            val lineCount = dump.count { it == '\n' }

            // Share via FileProvider.
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: IllegalArgumentException) {
                // FileProvider not configured — fall back to a plain share with text content.
                AppLog.w("LogExport", "FileProvider not configured ($authority), sharing text only")
                shareTextOnly(context, dump)
                return@withContext "Экспортировано $lineCount строк (как текст)"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                // Чистый "text/plain" — без "; charset=utf-8".
                // Fix #156: Android share sheet (ChooserActivity) на некоторых
                // версиях/оболочках не матчит "text/plain; charset=utf-8" с
                // intent-filter "text/plain" в манифесте → PinoK не появляется
                // в списке целей шеринга. Файл уже записан как UTF-8 без BOM,
                // charset в MIME type не нужен.
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PinoK detailed logs ($ts)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Fix #153: clipData обязателен для Intent.createChooser на Android 11+.
                // Системная ChooserActivity (uid 1000) читает URI через FileProvider
                // чтобы показать preview thumbnail + filename. Без clipData
                // FLAG_GRANT_READ_URI_PERMISSION НЕ распространяется на ChooserActivity
                // → SecurityException: "Permission Denial: reading FileProvider uri
                // ... requires the provider be exported, or grantUriPermission()".
                // ClipData + FLAG_GRANT_READ_URI_PERMISSION автоматически грантит
                // read access ВСЕМ компонентам которые обработают intent, включая
                // системный ChooserActivity и финального получателя (Telegram/VK/etc).
                clipData = ClipData.newRawUri("PinoK logs", uri)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт логов (UTF-8)"))

            "Экспортировано $lineCount строк → ${file.name}"
        } catch (e: Exception) {
            AppLog.e("LogExport", "Export failed", e)
            "Ошибка экспорта: ${e.message ?: "unknown"}"
        }
    }

private fun shareTextOnly(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "PinoK detailed logs")
    }
    context.startActivity(Intent.createChooser(intent, "Отправить логи"))
}
