package re.pinok.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Общие утилиты форматирования.
 * Вынесены из дублирующихся private-функций в FeedScreen, MusicScreen,
 * ProfileScreen, CommunityScreen, NotificationsScreen, ChatDetailScreen, etc.
 *
 * Аудит #90: объединено 5× formatDuration, 5× formatTime, 3× formatCount,
 * 2× formatMsgTime, 1× formatRecordingTime, 2× formatDurationMs.
 */

// ─── Длительность ─────────────────────────────────────────────

/** Форматирование длительности трека: 195 → "3:15" */
fun Int.toDurationString(): String {
    val m = this / 60
    val s = this % 60
    return "%d:%02d".format(m, s)
}

/** Форматирование длительности из миллисекунд: 195000 → "3:15" */
fun Long.toDurationString(): String {
    val totalSec = (this / 1000).toInt()
    return totalSec.toDurationString()
}

/** Форматирование времени записи голосового: 90 → "1:30", 45 → "0:45" */
fun Int.toRecordingTimeString(): String {
    val m = this / 60
    val s = this % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}

// ─── Время ────────────────────────────────────────────────────

private val RU_LOCALE = Locale.forLanguageTag("ru")

/** Относительное время: "только что", "5 мин назад", "вчера", "12 июн 2025" */
fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() / 1000 - this
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        diff < 172800 -> "вчера"
        else -> {
            val sdf = SimpleDateFormat("d MMM yyyy", RU_LOCALE)
            sdf.format(Date(this * 1000))
        }
    }
}

/** Абсолютное время: "12 июн 2025, 14:30" */
fun Long.toAbsoluteTime(): String {
    return try {
        val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", RU_LOCALE)
        sdf.format(Date(this * 1000))
    } catch (_: Exception) {
        ""
    }
}

/** Время сообщения: "14:30" */
fun Long.toMsgTime(): String {
    val sdf = SimpleDateFormat("HH:mm", RU_LOCALE)
    return sdf.format(Date(this * 1000))
}

// ─── Board (обсуждения) — B-FIX-7 / AUDIT #10, #28 ──────────────────────────

/**
 * B-FIX-7 (AUDIT #10): Дата превью last-comment в списке тем.
 *
 * Формат по снапшоту VK web: «Ответил 28 окт в 08:32».
 *  - Текущий год:  «d MMM в HH:mm»   → 28 окт в 08:32
 *  - Прошлый год:   «d MMM yyyy в HH:mm» → 28 окт 2024 в 08:32
 *
 * [this] — Unix timestamp в секундах (VK API board.getTopics `updated`).
 */
fun Long.toBoardLastCommentDate(): String {
    val tsMs = this * 1000L
    val calNow = java.util.Calendar.getInstance()
    val calTs = java.util.Calendar.getInstance().apply { timeInMillis = tsMs }
    val sameYear = calNow.get(java.util.Calendar.YEAR) == calTs.get(java.util.Calendar.YEAR)
    val pattern = if (sameYear) "d MMM 'в' HH:mm" else "d MMM yyyy 'в' HH:mm"
    return try {
        SimpleDateFormat(pattern, RU_LOCALE).format(Date(tsMs))
    } catch (_: Exception) {
        ""
    }
}

/**
 * B-FIX-7 (AUDIT #28): Дата комментария в теме (board.getComments `created`).
 *
 * Формат по снапшоту VK web (внизу комментария, между текстом и «Ответить»):
 *  - Сегодня:        «в HH:mm»            (15:30)
 *  - Вчера:          «вчера в HH:mm»
 *  - Этот год:       «d MMM в HH:mm»     (9 мар в 14:30)
 *  - Иначе (старый): «d MMM yyyy» БЕЗ времени (15 фев 2021) — как в снапшоте.
 *
 * [this] — Unix timestamp в секундах.
 */
fun Long.toBoardCommentDate(): String {
    val nowMs = System.currentTimeMillis()
    val tsMs = this * 1000L
    val calNow = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val calTs = java.util.Calendar.getInstance().apply { timeInMillis = tsMs }
    val dayNow = calNow.get(java.util.Calendar.DAY_OF_YEAR)
    val dayTs = calTs.get(java.util.Calendar.DAY_OF_YEAR)
    val yearNow = calNow.get(java.util.Calendar.YEAR)
    val yearTs = calTs.get(java.util.Calendar.YEAR)
    return try {
        when {
            yearNow == yearTs && dayNow == dayTs ->
                SimpleDateFormat("'в' HH:mm", RU_LOCALE).format(Date(tsMs))
            yearNow == yearTs && dayNow - dayTs == 1 ->
                SimpleDateFormat("'вчера в' HH:mm", RU_LOCALE).format(Date(tsMs))
            yearNow == yearTs ->
                SimpleDateFormat("d MMM 'в' HH:mm", RU_LOCALE).format(Date(tsMs))
            else ->
                SimpleDateFormat("d MMM yyyy", RU_LOCALE).format(Date(tsMs))
        }
    } catch (_: Exception) {
        ""
    }
}

/**
 * P1.1: Дата для date-separator'а в чате: «Сегодня», «Вчера», «12 июля»,
 * «12 июля 2024» (для старых лет).
 *
 * [timestampSec] — Unix timestamp в секундах (как VK API возвращает message.date).
 * Сравнение с «сегодня/вчера» по локальному календарю.
 */
fun Long.toChatDate(): String {
    val nowMs = System.currentTimeMillis()
    val tsMs = this * 1000L
    val calNow = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val calTs = java.util.Calendar.getInstance().apply { timeInMillis = tsMs }
    val dayNow = calNow.get(java.util.Calendar.DAY_OF_YEAR)
    val dayTs = calTs.get(java.util.Calendar.DAY_OF_YEAR)
    val yearNow = calNow.get(java.util.Calendar.YEAR)
    val yearTs = calTs.get(java.util.Calendar.YEAR)
    return when {
        yearNow == yearTs && dayNow == dayTs -> "Сегодня"
        yearNow == yearTs && dayNow - dayTs == 1 -> "Вчера"
        yearNow == yearTs -> SimpleDateFormat("d MMMM", RU_LOCALE).format(Date(tsMs))
        else -> SimpleDateFormat("d MMMM yyyy", RU_LOCALE).format(Date(tsMs))
    }
}

/**
 * P1.1: Возвращает «день» (год + dayOfYear) как стабильный ключ для группировки
 * сообщений по дате. Два сообщения с одним [dayKey] belong to one date-separator group.
 */
fun Long.toDayKey(): Int {
    // Audit #S5: `this` внутри apply{} ссылается на Calendar, а не на Long-приёмник
    // (shadowing) → `this * 1000L` = Calendar * Long = «None of the following candidates
    // is applicable». Выносим умножение наружу, в scope extension-функции.
    val tsMs = this * 1000L
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = tsMs }
    val year = cal.get(java.util.Calendar.YEAR)
    val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
    return year * 1000 + day
}

// ─── Счётчики ─────────────────────────────────────────────────

/** Форматирование счётчика: 999 → "999", 1500 → "1.5K", 2300000 → "2.3M" */
fun Int.toCountString(): String = when {
    this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
    this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1_000.0)
    else -> this.toString()
}