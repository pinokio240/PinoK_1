package re.pinok.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.FileProvider
import re.pinok.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Lvl(val label: String, val char: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    // Fix #112: явный BackHandler — перехватывает системный back press
    // (predictive back gesture на Android 13+) и вызывает onClose.
    // Раньше работала только UI-кнопка в TopAppBar, но системный back
    // мог не сработать если drawer/overlay перехватывал событие.
    //
    // Fix #114: параметр переименован onBack → onClose, иконка заменена
    // со стрелки «назад» на крестик «закрыть». Причина: если Logs был
    // сохранён как lastRoute (старый баг), он становился startDestination
    // NavHost → nav.popBackStack() возвращал false и ничего не происходило.
    // Теперь onClose вызвает fallback на Feed (см. SovaNavHost).
    BackHandler(onBack = onClose)

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
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        kotlinx.coroutines.delay(2000L)
        logLines = AppLog.snapshot()
        refreshKey++
    }

    val filtered = remember(logLines, enabledLevels) {
        logLines.filter { line ->
            val firstSpace = line.indexOf(' ')
            if (firstSpace < 0) return@filter false
            val afterTs = line.substring(firstSpace + 1)
            val lvlChar = afterTs.firstOrNull() ?: '?'
            lvlChar.toString() in enabledLevels
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Логи (${filtered.size}/${logLines.size})") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        logLines = AppLog.snapshot()
                        refreshKey++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = {
                        AppLog.clear()
                        logLines = emptyList()
                        refreshKey++
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить")
                    }
                    IconButton(onClick = {
                        // #SHARE-LOG-AS-FILE (2026-08-01): отправляем лог как ФАЙЛ,
                        // а не как plain text. Раньше EXTRA_TEXT с большим логом
                        // (50k+ chars) не доходил до VK-чата — VK имеет лимит на
                        // длину сообщения, и chooser показывал «сообщение слишком
                        // длинное». Файл .txt прикрепляется как документ — VK
                        // принимает без лимита, получатель может открыть в любом
                        // текстовом редакторе.
                        //
                        // Файл пишется в cacheDir (доступен через FileProvider,
                        // зарегистрирован в AndroidManifest.xml: cache-path).
                        // Имя: pinok_logs_<timestamp>.txt — удобно для сортировки.
                        val text = AppLog.exportDetailed()
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val logFile = File(context.cacheDir, "pinok_logs_$timestamp.txt")
                        try {
                            logFile.writeText(text)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                logFile,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "PinoK logs $timestamp")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить логи (${logFile.length() / 1024} KB)"))
                        } catch (e: Exception) {
                            // Fallback: если FileProvider/запись упали —
                            // возвращаемся к старому plain-text способу.
                            AppLog.e("LogScreen", "share as file failed: ${e.message} — fallback to text", e)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra(Intent.EXTRA_SUBJECT, "PinoK detailed logs")
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить логи (текст)"))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                levels.forEach { lvl ->
                    val selected = lvl.char in enabledLevels
                    FilterChip(
                        selected = selected,
                        onClick = {
                            enabledLevels = if (selected) {
                                enabledLevels - lvl.char
                            } else {
                                enabledLevels + lvl.char
                            }
                        },
                        label = { Text(lvl.label, fontWeight = FontWeight.Bold) },
                        leadingIcon = if (selected) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(lvl.color),
                                )
                            }
                        } else null,
                    )
                }
                FilterChip(
                    selected = enabledLevels.size == levels.size,
                    onClick = { enabledLevels = levels.map { it.char }.toSet() },
                    label = { Text("Все") },
                )
            }

            Spacer(Modifier.height(4.dp))

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
                    // audit Low: key по индексу — дубликаты строк не дают warning.
                    itemsIndexed(filtered, key = { idx, _ -> idx }) { _, line ->
                        LogLineRow(line = line, levels = levels)
                    }
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