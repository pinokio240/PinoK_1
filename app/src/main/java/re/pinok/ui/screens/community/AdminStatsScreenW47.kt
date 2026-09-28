package re.pinok.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.realtime.StatsQueuePoller
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog
import java.util.Locale

/**
 * #ADMIN-STATS-W47 (2026-09-27): статистика сообщества — async-пайплайн
 * Mini App 51912452 (замена legacy AdminStatsScreenLegacy).
 *
 * Поток: bootstrap -> sections -> queue.subscribe(poller) ->
 *        getOwnerStats(act=layout) -> chunk[layout] ->
 *        для каждой карточки getOwnerStats(act=data&card_id) -> chunk[data].
 * Числа приходят через StatsQueuePoller (long-poll queuev4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStatsScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    AppLog.i("AdminStats", "PINOK_STATS_MARKER_W47_20260928 screen open groupId=$groupId")
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ownerName by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf<List<VKApiClient.StatsSection>>(emptyList()) }
    var selectedSection by remember { mutableIntStateOf(0) }
    var selectedSub by remember { mutableIntStateOf(0) }
    var cards by remember { mutableStateOf<List<StatsChunkParser.LayoutCard>>(emptyList()) }
    val dataByTask = remember { mutableStateOf<Map<String, StatsChunkParser.DataCard>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }

    // task_id -> card_id (для сопоставления ответов poller'а с карточками)
    val taskToCard = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // #STATS-FIX3: чанки, пришедшие до заполнения taskToCard (иначе терялись).
    val pendingChunks = remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val poller = remember { StatsQueuePoller(app.httpClient, app.apiClient) }

    // Единый период — последние 7 дней (как Mini App по умолчанию).
    val edateMs = remember { System.currentTimeMillis() }
    val sdateMs = remember { edateMs - 7L * 24 * 60 * 60 * 1000 }

    // Сбор chunk'ов от poller'а: сопоставляем task_id -> card_id.
    LaunchedEffect(groupId) {
        poller.chunks.collect { ev ->
            val cardId = taskToCard.value[ev.taskId]
            if (cardId == null) {
                // #STATS-FIX3: буферизуем — соответствие появится позже.
                pendingChunks.value = pendingChunks.value + (ev.taskId to ev.chunk)
            } else {
                val parsed = StatsChunkParser.parseData(ev.chunk)
                if (parsed != null) {
                    dataByTask.value = dataByTask.value + (cardId to parsed)
                }
            }
        }
    }

    /** Загрузка layout текущей секции/подсекции + запуск data-задач по карточкам. */
    fun loadLayout() {
        val sec = sections.getOrNull(selectedSection) ?: return
        val sub = sec.subsections.getOrNull(selectedSub) ?: return
        scope.launch {
            busy = true
            error = null
            try {
                val handle = app.apiClient.statsDashboardGetOwnerStats(
                    ownerId = -groupId,
                    section = sec.id,
                    subSection = sub.id,
                    act = "layout",
                    sdateMs = sdateMs,
                    edateMs = edateMs,
                )
                if (handle == null) {
                    error = "Не удалось запросить статистику (getOwnerStats)"
                    return@launch
                }
                // Ждём layout-чанк через poller (по task_id).
                val layoutChunk = awaitChunk(poller, handle.taskId, timeoutMs = 25_000L)
                if (layoutChunk == null) {
                    error = "Layout статистики не пришёл (timeout)"
                    return@launch
                }
                val layout = StatsChunkParser.parseLayout(layoutChunk)
                cards = layout
                dataByTask.value = emptyMap()
                // Запускаем data-задачи по каждой leaf-карточке.
                val newMap = mutableMapOf<String, String>()
                for (card in flatten(layout)) {
                    // #STATS-RATE-FIX: пауза между data-запросами (VK err=6 при пачке).
                    kotlinx.coroutines.delay(220L)
                    val h = app.apiClient.statsDashboardGetOwnerStats(
                        ownerId = -groupId,
                        section = sec.id,
                        subSection = sub.id,
                        act = "data",
                        cardId = card.id,
                        sdateMs = sdateMs,
                        edateMs = edateMs,
                    )
                    if (h != null) newMap[h.taskId] = card.id
                }
                taskToCard.value = taskToCard.value + newMap
                // #STATS-FIX3: применить чанки, пришедшие до заполнения taskToCard.
                val pend = pendingChunks.value
                if (pend.isNotEmpty()) {
                    val add = mutableMapOf<String, StatsChunkParser.DataCard>()
                    for ((tid, cid) in newMap) {
                        val ch = pend[tid] ?: continue
                        val parsed = StatsChunkParser.parseData(ch) ?: continue
                        add[cid] = parsed
                    }
                    if (add.isNotEmpty()) dataByTask.value = dataByTask.value + add
                    pendingChunks.value = pend - newMap.keys
                }
            } catch (e: Exception) {
                AppLog.e("AdminStatsW47", "loadLayout failed", e)
                error = e.message ?: "Ошибка загрузки статистики"
            } finally {
                busy = false
            }
        }
    }

    // #STATS-FIX3: останавливаем long-poll при уходе с экрана.
    DisposableEffect(groupId) {
        onDispose { poller.stop() }
    }

    // Первичная загрузка: bootstrap + sections + poller, затем layout.
    LaunchedEffect(groupId) {
        loading = true
        error = null
        try {
            val boot = withContext(Dispatchers.IO) {
                app.apiClient.statsDashboardGetBootstrapData(ownerId = -groupId)
            }
            ownerName = boot?.owner?.name ?: ""
            val secs = withContext(Dispatchers.IO) {
                app.apiClient.statsDashboardGetDashboardSections(ownerId = -groupId)
            }
            sections = secs
            if (secs.isEmpty()) {
                error = "Секции статистики не получены"
            } else {
                // #STATS-FIX3: queue.subscribe обязателен ДО getOwnerStats,
                // иначе задача уходит в очередь без подписчика и чанк не приходит.
                val subOk = poller.start(userId = 0L, groupId = groupId)
                if (!subOk) {
                    error = "Не удалось подписаться на очередь статистики (queue.subscribe)"
                } else {
                    loadLayout()
                }
            }
        } catch (e: Exception) {
            AppLog.e("AdminStatsW47", "bootstrap failed", e)
            error = e.message ?: "Ошибка загрузки"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null && sections.isEmpty() -> ErrorView(
                message = error,
                onRetry = { /* перезапуск через рекомпозицию невозможен — просим вернуться */ },
                modifier = Modifier.padding(pad),
            )

            sections.isEmpty() -> ErrorView(
                message = "Статистика недоступна",
                onRetry = {},
                modifier = Modifier.padding(pad),
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                // Шапка: имя сообщества
                if (ownerName.isNotBlank()) {
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                // Табы секций
                ScrollableTabRow(selectedTabIndex = selectedSection) {
                    sections.forEachIndexed { i, s ->
                        Tab(
                            selected = selectedSection == i,
                            onClick = {
                                selectedSection = i
                                selectedSub = 0
                                loadLayout()
                            },
                            text = { Text(s.name) },
                        )
                    }
                }
                val sec = sections.getOrNull(selectedSection)
                if (sec != null && sec.subsections.size > 1) {
                    ScrollableTabRow(selectedTabIndex = selectedSub) {
                        sec.subsections.forEachIndexed { i, sub ->
                            Tab(
                                selected = selectedSub == i,
                                onClick = { selectedSub = i; loadLayout() },
                                text = { Text(sub.name) },
                            )
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    val flat = flatten(cards)
                    if (flat.isEmpty()) {
                        Text(
                            text = "Нет карточек для этого раздела",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (card in flat) {
                        val data = dataByTask.value[card.id]
                        StatsCardView(cardTitle = statsCardTitle(card.id, card.title), data = data)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Источник: Mini App 51912452 (statsDashboard.getOwnerStats + queuev4)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Раскрывает layout в плоский список leaf-карточек. */

/** #STATS-TITLES: человекочитаемые названия карточек вместо raw id. */
private fun statsCardTitle(id: String, fallback: String): String = when (id) {
    "overview" -> "Обзор"
    "reach" -> "Охват"
    "reach_and_views_tabs/reach" -> "Охват"
    "subscribers" -> "Подписчики"
    "subscribers_daily" -> "Подписчики по дням"
    "visitors_views" -> "Посетители и просмотры"
    "post_views" -> "Просмотры записей"
    "posts_interaction" -> "Вовлечённость записей"
    "post_content_list" -> "Посты"
    "sex" -> "Пол"
    "age" -> "Возраст"
    "cities" -> "Города"
    "datalist_platforms" -> "Платформы"
    "datalist_devices" -> "Устройства"
    "datalist_traffic_sources" -> "Источники трафика"
    "sections_views" -> "Просмотры разделов"
    "bell_subscribers" -> "Подписки на уведомления"
    "action_button" -> "Кнопка действия"
    "messages" -> "Сообщения"
    "message_tabs/messages" -> "Сообщения"
    "all_likes" -> "Лайки"
    "likes" -> "Лайки"
    "interaction_tabs/likes" -> "Лайки"
    else -> if (fallback.isNotBlank()) fallback else id
}
private fun flatten(cards: List<StatsChunkParser.LayoutCard>): List<StatsChunkParser.LayoutCard> {
    val out = mutableListOf<StatsChunkParser.LayoutCard>()
    for (c in cards) {
        if (c.tabs.isNotEmpty()) {
            for (t in c.tabs) out.addAll(flatten(t.cards))
        } else {
            out.add(c)
        }
    }
    return out
}
private suspend fun awaitChunk(
    poller: StatsQueuePoller,
    taskId: String,
    timeoutMs: Long,
): String? {
    return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        poller.chunks.first { it.taskId == taskId }.chunk
    }
}

/** Карточка статистики. */
@Composable
private fun StatsCardView(cardTitle: String, data: StatsChunkParser.DataCard?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = cardTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            when (data) {
                null -> Text(
                    text = "Загрузка…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is StatsChunkParser.DataCard.Summary -> {
                    for (r in data.rows) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = r.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (r.relative.isNotBlank()) {
                                Text(
                                    text = r.relative,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = r.value,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider()
                    }
                }

                is StatsChunkParser.DataCard.DataList -> {
                    for (r in data.rows) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = r.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${r.value.toInt()}  ${String.format(Locale.US, "%.1f%%", r.percent)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is StatsChunkParser.DataCard.Doughnut -> {
                    for (s in data.segments) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = s.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${s.value.toInt()}  ${String.format(Locale.US, "%.1f%%", s.percent)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is StatsChunkParser.DataCard.Timeline -> {
                    if (data.points.isEmpty()) {
                        Text(
                            text = "Нет данных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        for (p in data.points.takeLast(10)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    text = p.x,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = p.y.toInt().toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
