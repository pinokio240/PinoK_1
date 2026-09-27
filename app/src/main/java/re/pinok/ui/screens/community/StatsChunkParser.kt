package re.pinok.ui.screens.community

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * #ADMIN-STATS-W47 (2026-09-27): парсер vboardcard-чанков Mini App 51912452.
 *
 * Два вида чанка (из статистика.har):
 *  - LAYOUT: {"items":[{type,id,title,isLazy,tabs?}]} — структура карточек;
 *  - DATA:   {"item":{...},"range":{...}} — числа конкретной карточки.
 *
 * Типы DATA-item: summary, advanced_timeline_chart, doughnut_chart,
 * bar_chart, data_list.
 */
object StatsChunkParser {

    /** Карточка layout-чанка. */
    data class LayoutCard(
        val id: String,
        val type: String,
        val title: String,
        val tabs: List<LayoutTab> = emptyList(),
    )

    data class LayoutTab(
        val id: String,
        val title: String,
        val cards: List<LayoutCard> = emptyList(),
    )

    /** Распарсенный layout-чанк. */
    fun parseLayout(chunk: String): List<LayoutCard> {
        return try {
            val root = JsonParser.parseString(chunk).asJsonObject
            val items = root.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el -> if (el.isJsonObject) parseCard(el.asJsonObject) else null }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCard(o: JsonObject): LayoutCard {
        val tabsArr = o.getAsJsonArray("tabs")
        val tabs = tabsArr?.mapNotNull { t ->
            if (!t.isJsonObject) return@mapNotNull null
            val to = t.asJsonObject
            val inner = to.getAsJsonArray("layout")?.mapNotNull { c ->
                if (c.isJsonObject) parseCard(c.asJsonObject) else null
            } ?: emptyList()
            LayoutTab(
                id = to.str("id"),
                title = to.str("title"),
                cards = inner,
            )
        } ?: emptyList()
        return LayoutCard(
            id = o.str("id"),
            type = o.str("type"),
            title = o.str("title"),
            tabs = tabs,
        )
    }

    // ─── DATA ────────────────────────────────────────────────────────────

    /** Строка summary (label + число + процент). */
    data class SummaryRow(val label: String, val value: String, val relative: String)

    /** Пункт списка (cities, traffic_sources, platforms, devices). */
    data class ListRow(val label: String, val value: Double, val percent: Double)

    /** Сегмент круговой диаграммы (пол/возраст). */
    data class Segment(val label: String, val value: Double, val percent: Double)

    /** Точка графика (день → значение). */
    data class TimePoint(val x: String, val y: Double)

    data class TimelineChart(val title: String, val points: List<TimePoint>)

    /** Разобранный data-item. */
    sealed interface DataCard {
        data class Summary(val id: String, val rows: List<SummaryRow>) : DataCard
        data class DataList(val id: String, val title: String, val rows: List<ListRow>) : DataCard
        data class Doughnut(val id: String, val title: String, val segments: List<Segment>) : DataCard
        data class Timeline(val id: String, val title: String, val points: List<TimePoint>) : DataCard
    }

    fun parseData(chunk: String): DataCard? {
        return try {
            val root = JsonParser.parseString(chunk).asJsonObject
            val item = root.getAsJsonObject("item") ?: return null
            when (item.str("type")) {
                "summary" -> parseSummary(item)
                "data_list" -> parseDataList(item)
                "doughnut_chart" -> parseDoughnut(item)
                "advanced_timeline_chart" -> parseTimeline(item)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSummary(item: JsonObject): DataCard.Summary {
        val rows = item.getAsJsonArray("summary")?.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val label = o.str("label")
            val vals = o.getAsJsonArray("values") ?: return@mapNotNull null
            var main = ""; var rel = ""
            for (v in vals) {
                if (!v.isJsonObject) continue
                val vo = v.asJsonObject
                when (vo.str("type")) {
                    "type_main" -> main = fmtNum(vo.get("value"))
                    "type_relative" -> rel = fmtRelative(vo.get("value"), vo.str("format"))
                }
            }
            SummaryRow(label = label, value = main, relative = rel)
        } ?: emptyList()
        return DataCard.Summary(id = item.str("id"), rows = rows)
    }

    private fun parseDataList(item: JsonObject): DataCard.DataList {
        val rows = item.getAsJsonArray("items")?.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            ListRow(
                label = o.str("label"),
                value = o.num("value"),
                percent = o.num("percentage_value"),
            )
        } ?: emptyList()
        return DataCard.DataList(id = item.str("id"), title = item.str("title"), rows = rows)
    }

    private fun parseDoughnut(item: JsonObject): DataCard.Doughnut {
        val g = item.getAsJsonArray("graph")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val ds = g?.getAsJsonObject("datasets")
        val labels = ds?.getAsJsonArray("labels")?.map { it.asString } ?: emptyList()
        val values = ds?.getAsJsonArray("values")?.map { it.asDouble } ?: emptyList()
        val percents = ds?.getAsJsonArray("percents")?.map { it.asDouble } ?: emptyList()
        val segs = labels.indices.map { i ->
            Segment(
                label = labels[i],
                value = values.getOrElse(i) { 0.0 },
                percent = percents.getOrElse(i) { 0.0 },
            )
        }
        return DataCard.Doughnut(id = item.str("id"), title = item.str("title"), segments = segs)
    }

    private fun parseTimeline(item: JsonObject): DataCard.Timeline {
        val g = item.getAsJsonArray("graph")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val points = mutableListOf<TimePoint>()
        val datasets = g?.getAsJsonArray("datasets")
        if (datasets != null && datasets.size() > 0) {
            val first = datasets[0].takeIf { it.isJsonObject }?.asJsonObject
            val data = first?.getAsJsonArray("data")
            if (data != null) {
                for (p in data) {
                    if (!p.isJsonObject) continue
                    val po = p.asJsonObject
                    points.add(TimePoint(x = po.str("x"), y = po.num("y")))
                }
            }
        }
        return DataCard.Timeline(id = item.str("id"), title = item.str("title"), points = points)
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun JsonObject.str(k: String): String =
        get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

    private fun JsonObject.num(k: String): Double =
        get(k)?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0

    private fun fmtNum(el: com.google.gson.JsonElement?): String {
        if (el == null || !el.isJsonPrimitive) return "0"
        return try {
            val d = el.asDouble
            if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
        } catch (_: Exception) { el.asString }
    }

    private fun fmtRelative(el: com.google.gson.JsonElement?, format: String): String {
        if (el == null) return ""
        if (!el.isJsonPrimitive) return ""
        val raw = el.asString
        if (format == "format_percentage") {
            // value в сотых долях процента (5350 -> 53.5%) ИЛИ строка "—"
            return try {
                val d = el.asDouble
                if (d == d.toLong().toDouble() && raw.length <= 3 && raw != "0") "$raw%"
                else String.format("%.1f%%", d / 100.0)
            } catch (_: Exception) { "$raw%" }
        }
        return raw
    }
}