package re.pinok.feature.calls

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.StateFlow

/**
 * #CALLS-SNAP (2026-09-05): Этап А2 плана «звонки.перенос.план.md» —
 * capability «репозиторий раздела Звонки» (канон контейнеров §3.2: интерфейс
 * объявлен в контейнере :feature:calls, реализация — в хосте :app
 * (re.pinok.calls.CallsSectionRepositoryImpl), публикация — CompositionLocal
 * [LocalCallsSectionRepository] в MainActivity.setContent рядом с
 * LocalCallsDeps, по образцу CallsDependencies/PhotosDependencies).
 *
 * Единый источник правды списков раздела «Звонки» (план §3.2): секции читают
 * StateFlow вместо собственных remember{}-фечей — меньше повторных запросов,
 * кэш переживает переключение табов, «Повторить» реально перезапускает fetch
 * (известный баг: retry менял только флаги, LaunchedEffect(Unit) не
 * перезапускался). Весь сетевой I/O — в реализации, на Dispatchers.Default
 * (#ANR-MAIN-IO); интерфейс — только состояние и команды.
 *
 * ТИПОВАЯ ПОЛИТИКА: JsonObject (gson) / корутины — библиотечные типы,
 * CallHistoryEntry объявлен здесь — ноль :app-типов (канон §3.2 Этап Б).
 * Источники — существующие члены фасада [CallsApi] (Этап А1, 48 членов):
 *  - HISTORY    → callsGetHistory(count, offset, filter:"all", marker=null)
 *  - MISSED     → callsGetHistory(count, offset, filter:"missed", marker=null)
 *  - RECORDINGS → messagesGetCallRecordings(count)
 *  - TRANSCRIPTS→ callsGetAsrTranscriptions(count)
 *  - SCHEDULED  → messagesGetScheduledCalls(count)
 *  - ACTIVE     → messagesGetCurrentCalls()
 */
interface CallsSectionRepository {

    /** История звонков (обогащена профилями: имя/фото через usersGetByIds). */
    val history: StateFlow<CallsSectionState<CallHistoryEntry>>

    /** Пропущенные звонки (тот же тип записи, все outcome=MISSED). */
    val missed: StateFlow<CallsSectionState<CallHistoryEntry>>

    /** Записи звонков (сырые items messages.getCallRecordings — плеер, Этап В). */
    val recordings: StateFlow<CallsSectionState<JsonObject>>

    /** Расшифровки ASR (сырые items calls.getAsrTranscriptions — правка, Этап В). */
    val transcripts: StateFlow<CallsSectionState<JsonObject>>

    /** Запланированные звонки (сырые items messages.getScheduledCalls — действия, Этап Г). */
    val scheduled: StateFlow<CallsSectionState<JsonObject>>

    /** Активные звонки (сырые items messages.getCurrentCalls). */
    val active: StateFlow<CallsSectionState<JsonObject>>

    /**
     * Запросить обновление списка. force=false — кэш-семантика: CONTENT не
     * перезапрашивается (переключение табов не штормит API), LOADING/ERROR —
     * грузим. force=true — безусловный fetch (кнопка «Повторить», pull).
     * Повторный вызов для уже грузящегося ключа — no-op (dedupe in-flight).
     */
    fun refresh(key: CallsSectionKey, force: Boolean)

    /**
     * #CALLS-SNAP: событийная инвалидация по завершении звонка — точка
     * расширения. Сейчас вызывается из CallsActiveSection после hangup и из
     * хоста (LP/queue-событие о завершении, когда будет подключено); позже
     * сюда же добавят подписку на события звонков вместо ручных вызовов.
     * Обновляет списки, затрагиваемые завершением: активные, историю,
     * пропущенные (записи/расшифровки появляются позже — серверная обработка).
     */
    fun invalidateOnCallFinished()
}

/** Ключ списка раздела (для refresh/setState — по одному StateFlow на ключ). */
enum class CallsSectionKey { HISTORY, MISSED, RECORDINGS, TRANSCRIPTS, SCHEDULED, ACTIVE }

/** Жизненный цикл списка: грузится → контент/пусто/ошибка. */
enum class CallsSectionStatus { LOADING, CONTENT, EMPTY, ERROR }

/**
 * Состояние одного списка. status=LOADING — первый фетч (спиннер);
 * ERROR — показать ошибку + «Повторить»; EMPTY — честный empty-state
 * (API ответил пустым списком); CONTENT — данные для рендера.
 */
data class CallsSectionState<T>(
    val status: CallsSectionStatus = CallsSectionStatus.LOADING,
    val items: List<T> = emptyList(),
    val errorMessage: String? = null,
)

/** Исход звонка — статус-строка общего вида строки-кластера (Этап А4). */
enum class CallOutcome { FINISHED, MISSED, CANCELED }

/**
 * Типизированная запись истории/пропущенных (парсинг двух форм ответа
 * calls.getHistory: web {call_id, peer{id}, is_inbound, is_missed,
 * started_at, finished_at} и desktop {peer_id, name, photo, direction,
 * date, duration}). name/photo обогащаются профилем (usersGetByIds) в
 * реализации репозитория.
 */
data class CallHistoryEntry(
    val callId: String,
    val peerId: Long,
    val name: String,
    val photo: String?,
    val isInbound: Boolean,
    val isMissed: Boolean,
    val isGroup: Boolean,
    val outcome: CallOutcome,
    /** Начало звонка, unix-секунды (для «сегодня/вчера/дата»). */
    val timestampSec: Long,
    /** Длительность в секундах (0 — не состоялся/пропущен). */
    val durationSec: Int,
)

/**
 * Fail-fast провайдер репозитория раздела (инвариант И5 канона §3.5, тот же
 * замысел, что у LocalCallsDeps): отсутствие провайдера падает при композиции.
 */
val LocalCallsSectionRepository = staticCompositionLocalOf<CallsSectionRepository> {
    error("CallsSectionRepository not provided")
}
