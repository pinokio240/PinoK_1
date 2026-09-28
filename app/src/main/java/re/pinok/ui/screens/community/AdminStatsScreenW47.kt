package re.pinok.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FilterChip
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

    // #STATS-PERIOD: выбор периода (сегодня/вчера/7 дней/30 дней).
    // VK выравнивает sdate/edate по ЛОКАЛЬНОЙ зоне на границы суток.
    var periodIdx by remember { mutableIntStateOf(2) }
    val periodOpts = listOf(
        Triple("Сегодня", "today", 0),
        Triple("Вчера", "yesterday", 1),
        Triple("7 дней", "last7Days", 7),
        Triple("30 дней", "last30Days", 30),
    )
    // Диапазон считается НА МОМЕНТ вызова (а не при рекомпозиции) — иначе первый
    // клик по чипу использует старые даты (нужно 2 нажатия). #STATS-PERIOD-FIX
    fun periodRange(idx: Int): Triple<String, Long, Long> {
        val now = System.currentTimeMillis()
        val todayStart = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000
        return when (idx) {
            0 -> Triple("today", todayStart, now)
            1 -> Triple("yesterday", todayStart - dayMs, todayStart - 1)
            3 -> Triple("last30Days", todayStart - 30L * dayMs, now)
            else -> Triple("last7Days", todayStart - 7L * dayMs, now)
        }
    }

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
        val (curPeriod, sdateMs, edateMs) = periodRange(periodIdx)
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
                    period = curPeriod,
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
                        period = curPeriod,
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
                    periodIdx = defaultPeriodIdx(sections.firstOrNull()?.id ?: "")
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
                                periodIdx = defaultPeriodIdx(s.id)
                                loadLayout()
                            },
                            text = { Text(statsCardTitle(s.id, s.name)) },
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
                                text = { Text(statsCardTitle(sub.id, sub.name)) },
                            )
                        }
                    }
                }
                // #STATS-PERIOD: чипы выбора периода.
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    periodOpts.forEachIndexed { i, opt ->
                        FilterChip(
                            selected = periodIdx == i,
                            onClick = { periodIdx = i; loadLayout() },
                            label = { Text(opt.first, style = MaterialTheme.typography.bodySmall) },
                        )
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
private fun statsCardTitle(id: String, fallback: String): String {
    // Приоритет — родной заголовок VK из layout (fallback). Маппинг id — только
    // когда заголовок пуст (иначе выдуманные имена перебивали VK).
    if (fallback.isNotBlank()) return fallback
    return when (id) {
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
    "community_description_action_click" -> "Клики по описанию"
    "reach_and_views_tabs" -> "Охват и просмотры"
    "interaction_tabs" -> "Вовлечённость"
    "message_tabs" -> "Сообщения"
    "views" -> "Просмотры"
    "visitors" -> "Посетители"
    "stat_board_general" -> "Общее"
    "stat_board_post_details" -> "Записи"
    "messages" -> "Сообщения"
    "message_tabs/messages" -> "Сообщения"
    "all_likes" -> "Лайки"
    "likes" -> "Лайки"
    "interaction_tabs/likes" -> "Лайки"
    "post_reach" -> "Охват записей"
    "reach_timeline" -> "Охват"
    "subscribers_timeline" -> "Подписчики"
    "unsubscribers" -> "Отписки"
    "gender_age" -> "Пол и возраст"
    "countries" -> "Страны"
    "datalist_countries" -> "Страны"
    "datalist_cities" -> "Города"
    "datalist_sex" -> "Пол"
    "datalist_age" -> "Возраст"
    "reach_and_views_tabs/visitors_views" -> "Посетители и просмотры"
    "reach_and_views_tabs/post_views" -> "Просмотры записей"
    "reach_and_views_tabs/posts_interaction" -> "Вовлечённость записей"
    "reach_and_views_tabs/sections_views" -> "Просмотры разделов"
    "reach_and_views_tabs/subscribers_daily" -> "Подписчики по дням"
    "interaction_tabs/post_views" -> "Просмотры записей"
    "interaction_tabs/posts_interaction" -> "Вовлечённость записей"
    "interaction_tabs/all_likes" -> "Лайки"
    "interaction_tabs/comments" -> "Комментарии"
    "interaction_tabs/reposts" -> "Репосты"
    "comments" -> "Комментарии"
    "reposts" -> "Репосты"
    "bookmarks" -> "Закладки"
    "hidden" -> "Скрытия"
    "subscribed" -> "Подписки"
    "unsubscribed" -> "Отписки"
    else -> prettifyId(id)
    }
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

                is StatsChunkParser.DataCard.AdvancedList -> {
                    for (r in data.rows) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(r.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${r.value.toInt()}  ${String.format(Locale.US, "%.1f%%", r.percent)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

                is StatsChunkParser.DataCard.BarChart -> { BarChartView(data.series) }
                is StatsChunkParser.DataCard.Doughnut -> {
                    DoughnutChart(data.segments)
                }
                is StatsChunkParser.DataCard.Timeline -> {
                    if (data.points.isEmpty()) {
                        Text(
                            text = "Нет данных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TimelineChart(data.points)
                    }
                }
            }
        }
    }
}


/** #STATS-CHARTS: круговая (doughnut) диаграмма. */
@Composable
private fun DoughnutChart(segments: List<StatsChunkParser.Segment>) {
    if (segments.isEmpty()) {
        Text("Нет данных", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = listOf(Color(0xFF2688EB), Color(0xFFE03FAB), Color(0xFFA3ADB8),
        Color(0xFF4BB34B), Color(0xFFFFA000), Color(0xFF9C27B0))
    val total = segments.sumOf { it.value }.coerceAtLeast(1.0)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val d = size.minDimension
            val stroke = d * 0.32f
            var startAngle = -90f
            segments.forEachIndexed { i, s ->
                val sweep = (360f * (s.value / total)).toFloat()
                drawArc(
                    color = palette[i % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(d - stroke, d - stroke),
                )
                startAngle += sweep
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            segments.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(palette[i % palette.size], RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${s.value.toInt()} (${String.format(Locale.US, "%.0f%%", s.percent)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** #STATS-CHARTS: линейный график по точкам. */
@Composable
private fun TimelineChart(points: List<StatsChunkParser.TimePoint>) {
    val pts = points.takeLast(30)
    val maxY = (pts.maxOfOrNull { it.y } ?: 0.0).coerceAtLeast(1.0)
    val minY = (pts.minOfOrNull { it.y } ?: 0.0)
    val range = (maxY - minY).coerceAtLeast(1.0)
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp)) {
        if (pts.size < 2) return@Canvas
        val dx = size.width / (pts.size - 1)
        val path = Path()
        pts.forEachIndexed { i, pt ->
            val x = i * dx
            val norm = ((pt.y - minY) / range).toFloat()
            val y = size.height * (1f - norm)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmtXAxis(pts.firstOrNull()?.x), style = MaterialTheme.typography.bodySmall)
        Text("макс ${maxY.toInt()}", style = MaterialTheme.typography.bodySmall)
        Text(fmtXAxis(pts.lastOrNull()?.x), style = MaterialTheme.typography.bodySmall)
    }
}

/** #STATS-CHARTS: вертикальные бары (bar_chart). */
@Composable
private fun BarChartView(series: List<StatsChunkParser.BarSeries>) {
    if (series.isEmpty() || series.all { it.points.isEmpty() }) {
        Text("Нет данных", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = listOf(Color(0xFF2688EB), Color(0xFFE03FAB), Color(0xFF4BB34B), Color(0xFFFFA000))
    val labels = series.firstOrNull()?.points?.map { it.x } ?: emptyList()
    val maxY = (series.flatMap { it.points }.maxOfOrNull { it.y } ?: 1.0).coerceAtLeast(1.0)
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        val n = labels.size
        if (n == 0) return@Canvas
        val groupW = size.width / n
        val barW = (groupW * 0.7f) / series.size
        series.forEachIndexed { si, s ->
            s.points.forEachIndexed { i, p ->
                val h = (size.height * (p.y / maxY)).toFloat()
                val x = i * groupW + groupW * 0.15f + si * barW
                drawRoundRect(
                    color = palette[si % palette.size],
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW * 0.9f, h),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(fmtXAxis(it), style = MaterialTheme.typography.bodySmall) }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        series.forEachIndexed { si, s ->
            Box(modifier = Modifier.padding(4.dp).size(10.dp).background(palette[si % palette.size], RoundedCornerShape(2.dp)))
            Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
        }
    }
}


private fun fmtXAxis(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val v = raw.toLongOrNull() ?: return raw
    return try {
        val ms = if (v < 100000000000L) v * 1000L else v
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        val hasTime = cal.get(java.util.Calendar.HOUR_OF_DAY) != 0 || cal.get(java.util.Calendar.MINUTE) != 0
        val fmt = if (hasTime) "HH:mm" else "dd.MM"
        java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault()).format(cal.time)
    } catch (_: Exception) { raw }
}

/** #STATS-PERIOD: дефолтный период по секции (как в VK: Общее=30д, остальное=вчера). */
private fun defaultPeriodIdx(sectionId: String): Int = when (sectionId) {
    "top_community" -> 3   // 30 дней
    else -> 1              // вчера
}

/** #STATS-TITLES-FALLBACK: сырой id -> читаемый текст (snake_case -> "Слова"). */
private fun prettifyId(id: String): String {
    val tail = id.substringAfterLast('/')
    if (tail.isBlank()) return id
    return tail.split('_').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
