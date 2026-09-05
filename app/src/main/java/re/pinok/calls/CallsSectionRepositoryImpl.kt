package re.pinok.calls

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.data.model.UserProfile
import re.pinok.feature.calls.CallHistoryEntry
import re.pinok.feature.calls.CallOutcome
import re.pinok.feature.calls.CallsDependencies
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.CallsSectionRepository
import re.pinok.feature.calls.CallsSectionState
import re.pinok.feature.calls.CallsSectionStatus
import re.pinok.util.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * #CALLS-SNAP (2026-09-05): Этап А2 плана «звонки.перенос.план.md» —
 * реализация capability [CallsSectionRepository] в хосте (:app; канон
 * контейнеров §3.2 Этап В — интерфейс в :feature:calls, хост-реализация,
 * публикация CompositionLocal в MainActivity). Создаётся SovaApp
 * (лениво, scope = appScope); deps — сам SovaApp (: CallsDependencies).
 *
 * Потоки: refresh() из UI-композиции — неблокирующий запуск корутины в
 * scope; сам fetch+парсинг целиком в withContext(Dispatchers.Default)
 * (#ANR-MAIN-IO: план §3.2 — «I/O — только на Dispatchers.Default»).
 * Кэш-семантика: refresh(force=false) не перезапрашивает CONTENT; in-flight
 * dedupe по ключу (спам по «Повторить» не порождает параллельных фечей).
 * #NULL-EXPLICIT: без non-null assertion; safe-call и elvis операторы
 * не используются — явные if-проверки с захватом в локальный val.
 */
class CallsSectionRepositoryImpl(
    private val deps: CallsDependencies,
    private val scope: CoroutineScope,
) : CallsSectionRepository {

    private val _history = MutableStateFlow(CallsSectionState<CallHistoryEntry>())
    override val history: StateFlow<CallsSectionState<CallHistoryEntry>> = _history.asStateFlow()

    private val _missed = MutableStateFlow(CallsSectionState<CallHistoryEntry>())
    override val missed: StateFlow<CallsSectionState<CallHistoryEntry>> = _missed.asStateFlow()

    private val _recordings = MutableStateFlow(CallsSectionState<JsonObject>())
    override val recordings: StateFlow<CallsSectionState<JsonObject>> = _recordings.asStateFlow()

    private val _transcripts = MutableStateFlow(CallsSectionState<JsonObject>())
    override val transcripts: StateFlow<CallsSectionState<JsonObject>> = _transcripts.asStateFlow()

    private val _scheduled = MutableStateFlow(CallsSectionState<JsonObject>())
    override val scheduled: StateFlow<CallsSectionState<JsonObject>> = _scheduled.asStateFlow()

    private val _active = MutableStateFlow(CallsSectionState<JsonObject>())
    override val active: StateFlow<CallsSectionState<JsonObject>> = _active.asStateFlow()

    /** Ключи, чей fetch прямо сейчас выполняется (dedupe параллельных фечей). */
    private val inFlight: MutableSet<CallsSectionKey> = ConcurrentHashMap.newKeySet()

    override fun refresh(key: CallsSectionKey, force: Boolean) {
        if (!force) {
            val current = stateFor(key)
            if (current.status == CallsSectionStatus.CONTENT) return // кэш жив
        }
        if (!inFlight.add(key)) return // уже грузится — повторный вызов no-op
        scope.launch {
            setTransient(key, CallsSectionStatus.LOADING, null)
            try {
                withContext(Dispatchers.Default) {
                    fetch(key)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "refresh($key) failed", e)
                setTransient(key, CallsSectionStatus.ERROR, e.message)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    override fun invalidateOnCallFinished() {
        // #CALLS-SNAP: точка расширения — см. KDoc интерфейса. Позже здесь
        // появится подписка на LP/queue-события завершения звонка; сейчас
        // вызывают секции (hangup из «Активных») и будущие источники событий.
        AppLog.i(TAG, "invalidateOnCallFinished: refreshing active/history/missed")
        refresh(CallsSectionKey.ACTIVE, force = true)
        refresh(CallsSectionKey.HISTORY, force = true)
        refresh(CallsSectionKey.MISSED, force = true)
    }

    // ─── fetch: один suspend-fetch на ключ; выполняется на Dispatchers.Default ───

    private suspend fun fetch(key: CallsSectionKey) {
        when (key) {
            CallsSectionKey.HISTORY -> _history.value = loadHistory(FILTER_ALL, missedContext = false)
            CallsSectionKey.MISSED -> _missed.value = loadHistory(FILTER_MISSED, missedContext = true)
            CallsSectionKey.RECORDINGS ->
                _recordings.value = loadRaw { deps.apiClient.messagesGetCallRecordings(PAGE_SIZE) }
            CallsSectionKey.TRANSCRIPTS ->
                _transcripts.value = loadRaw { deps.apiClient.callsGetAsrTranscriptions(PAGE_SIZE) }
            CallsSectionKey.SCHEDULED ->
                _scheduled.value = loadRaw { deps.apiClient.messagesGetScheduledCalls(PAGE_SIZE) }
            CallsSectionKey.ACTIVE ->
                _active.value = loadRaw { deps.apiClient.messagesGetCurrentCalls() }
        }
    }

    /** Fetch сырого списка (recordings/transcripts/scheduled/active). */
    private suspend fun loadRaw(fetch: suspend () -> List<JsonObject>): CallsSectionState<JsonObject> {
        val raw = fetch()
        if (raw.isEmpty()) return CallsSectionState(status = CallsSectionStatus.EMPTY)
        return CallsSectionState(status = CallsSectionStatus.CONTENT, items = raw)
    }

    /**
     * История/пропущенные: calls.getHistory{filter} + обогащение профилями
     * (usersGetByIds одним запросом). isOffline() фасадного клиента вернёт
     * пустой список — честный EMPTY; «Повторить» подтянет, когда сеть вернётся.
     */
    private suspend fun loadHistory(filter: String, missedContext: Boolean): CallsSectionState<CallHistoryEntry> {
        val raw = deps.apiClient.callsGetHistory(PAGE_SIZE, 0, filter, null)
        if (raw.isEmpty()) return CallsSectionState(status = CallsSectionStatus.EMPTY)
        val entries = parseHistoryPage(raw, missedContext)
        if (entries.isEmpty()) {
            // Сырые данные есть, но формат не распознан — НЕ врём про «нет звонков»,
            // а честная ошибка с «Повторить» (данные не потеряны, формат донастроится).
            AppLog.w(TAG, "history(filter=$filter): raw=" + raw.size + ", parsed=0 — формат не распознан")
            return CallsSectionState(
                status = CallsSectionStatus.ERROR,
                errorMessage = "Формат истории не распознан",
            )
        }
        return CallsSectionState(status = CallsSectionStatus.CONTENT, items = entries)
    }

    // ─── парсинг истории/пропущенных (новый код — #NULL-EXPLICIT: без
    // non-null assertion, safe-call и elvis операторов) ───

    private suspend fun parseHistoryPage(
        raw: List<JsonObject>,
        missedContext: Boolean,
    ): List<CallHistoryEntry> {
        val peerIds = LinkedHashSet<Long>()
        for (o in raw) {
            val pid = extractPeerId(o)
            if (pid > 0L) peerIds.add(pid)
        }
        var profiles: Map<Long, UserProfile> = emptyMap()
        if (peerIds.isNotEmpty()) {
            profiles = try {
                deps.apiClient.usersGetByIds(peerIds.toList())
            } catch (e: Exception) {
                AppLog.e(TAG, "usersGetByIds failed — строки без имён", e)
                emptyMap()
            }
        }
        val out = ArrayList<CallHistoryEntry>(raw.size)
        for (o in raw) {
            val entry = parseHistoryEntry(o, profiles, missedContext)
            if (entry != null) out.add(entry)
        }
        return out
    }

    private fun parseHistoryEntry(
        o: JsonObject,
        profiles: Map<Long, UserProfile>,
        missedContext: Boolean,
    ): CallHistoryEntry? {
        return try {
            val callId = extractCallId(o)
            if (callId == null) {
                null
            } else {
                val peerId = extractPeerId(o)
                val profile = profiles[peerId]

                var name = stringField(o, "name")
                if (name == null && profile != null) {
                    name = "${profile.firstName} ${profile.lastName}".trim()
                }
                if (name == null || name.isBlank()) name = "Пользователь"

                var photo = stringField(o, "photo")
                if (photo == null && profile != null) photo = profile.photo100

                val isInbound = extractIsInbound(o)
                // NULL-ЯВНО: фолбэк — флаг «is_missed» отсутствует в ответе
                // (фильтр missed гарантирует пропущенность контекстом списка)
                val isMissedFlag = boolField(o, "is_missed")
                val isMissed = if (isMissedFlag != null) isMissedFlag else missedContext

                val groupFlag = boolField(o, "is_group_call")
                val isGroup = if (groupFlag != null) groupFlag else peerId >= GROUP_PEER_MIN

                val durationSec = extractDuration(o)
                val outcome = if (isMissed) {
                    CallOutcome.MISSED
                } else if (durationSec > 0) {
                    CallOutcome.FINISHED
                } else {
                    CallOutcome.CANCELED
                }

                CallHistoryEntry(
                    callId = callId,
                    peerId = peerId,
                    name = name,
                    photo = photo,
                    isInbound = isInbound,
                    isMissed = isMissed,
                    isGroup = isGroup,
                    outcome = outcome,
                    timestampSec = extractTimestamp(o),
                    durationSec = durationSec,
                )
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "parseHistoryEntry: запись пропущена", e)
            null
        }
    }

    private fun extractCallId(o: JsonObject): String? {
        val el = o.get("call_id")
        if (el != null && el.isJsonPrimitive) return el.asString
        // Desktop-формат (peer_id/name/photo, KDoc VKApiClient) без call_id —
        // синтетический стабильный id (ключ LazyColumn), запись не теряется.
        val peerId = extractPeerId(o)
        if (peerId == 0L) return null
        val ts = extractTimestamp(o)
        return "p" + peerId + "_" + ts
    }

    private fun extractPeerId(o: JsonObject): Long {
        val peerObjEl = o.get("peer")
        if (peerObjEl != null && peerObjEl.isJsonObject) {
            val idEl = peerObjEl.asJsonObject.get("id")
            if (idEl != null && idEl.isJsonPrimitive) return idEl.asLong
        }
        val peerIdEl = o.get("peer_id")
        if (peerIdEl != null && peerIdEl.isJsonPrimitive) return peerIdEl.asLong
        return 0L
    }

    /** is_inbound-флаг web-формата; desktop direction: in/incoming/inbound → входящий. */
    private fun extractIsInbound(o: JsonObject): Boolean {
        val flag = boolField(o, "is_inbound")
        if (flag != null) return flag
        val direction = stringField(o, "direction")
        if (direction != null) {
            val d = direction.trim().lowercase()
            if (d == "in" || d == "incoming" || d == "inbound") return true
            if (d == "out" || d == "outgoing" || d == "outbound") return false
        }
        return true // недоступно — считаем входящим (нейтральный вид строки)
    }

    /** started_at|date (unix-сек); защита от миллисекунд; fallback — сейчас. */
    private fun extractTimestamp(o: JsonObject): Long {
        var ts = longField(o, "started_at")
        if (ts == null) ts = longField(o, "date")
        if (ts == null || ts <= 0L) ts = System.currentTimeMillis() / 1000L
        if (ts > MS_EPOCH_THRESHOLD) ts = ts / 1000L
        return ts
    }

    /**
     * duration-поле (сек) либо finished_at-started_at. Оба таймстемпа
     * нормализуются к секундам ПО ОТДЕЛЬНОСТИ (правило extractTimestamp:
     * > MS_EPOCH_THRESHOLD — миллисекунды): разность секундного web-формата
     * иначе делилась бы на 1000 и любой звонок выглядел «нулевым»
     * (Отменённый вместо Завершённого, без длительности).
     */
    private fun extractDuration(o: JsonObject): Int {
        val dur = intField(o, "duration")
        if (dur != null) return if (dur < 0) 0 else dur
        val started = longField(o, "started_at")
        val finished = longField(o, "finished_at")
        if (started != null && finished != null && finished > started) {
            val s = if (started > MS_EPOCH_THRESHOLD) started / 1000L else started
            val f = if (finished > MS_EPOCH_THRESHOLD) finished / 1000L else finished
            if (f > s) return (f - s).toInt()
        }
        return 0
    }

    private fun stringField(o: JsonObject, key: String): String? {
        val el = o.get(key)
        if (el != null && el.isJsonPrimitive) return el.asString
        return null
    }

    private fun longField(o: JsonObject, key: String): Long? {
        val el = o.get(key)
        if (el != null && el.isJsonPrimitive) return el.asLong
        return null
    }

    private fun intField(o: JsonObject, key: String): Int? {
        val el = o.get(key)
        if (el != null && el.isJsonPrimitive) return el.asInt
        return null
    }

    private fun boolField(o: JsonObject, key: String): Boolean? {
        val el = o.get(key)
        if (el != null && el.isJsonPrimitive) return el.asBoolean
        return null
    }

    // ─── доступ к состояниям по ключу ───

    private fun stateFor(key: CallsSectionKey): CallsSectionState<*> {
        return when (key) {
            CallsSectionKey.HISTORY -> _history.value
            CallsSectionKey.MISSED -> _missed.value
            CallsSectionKey.RECORDINGS -> _recordings.value
            CallsSectionKey.TRANSCRIPTS -> _transcripts.value
            CallsSectionKey.SCHEDULED -> _scheduled.value
            CallsSectionKey.ACTIVE -> _active.value
        }
    }

    /**
     * Промежуточное состояние (LOADING/ERROR) — без items: подмена значения
     * конкретного флоу по ключу. Контентные состояния пишет сам fetch()
     * (типизированно, без кастов).
     */
    private fun setTransient(key: CallsSectionKey, status: CallsSectionStatus, errorMessage: String?) {
        when (key) {
            CallsSectionKey.HISTORY -> _history.value = CallsSectionState(status = status, errorMessage = errorMessage)
            CallsSectionKey.MISSED -> _missed.value = CallsSectionState(status = status, errorMessage = errorMessage)
            CallsSectionKey.RECORDINGS -> _recordings.value = CallsSectionState(status = status, errorMessage = errorMessage)
            CallsSectionKey.TRANSCRIPTS -> _transcripts.value = CallsSectionState(status = status, errorMessage = errorMessage)
            CallsSectionKey.SCHEDULED -> _scheduled.value = CallsSectionState(status = status, errorMessage = errorMessage)
            CallsSectionKey.ACTIVE -> _active.value = CallsSectionState(status = status, errorMessage = errorMessage)
        }
    }

    companion object {
        private const val TAG = "CallsSectionRepo"
        /** Страница списков — как у прежних фечей секций (30). */
        private const val PAGE_SIZE = 30
        private const val FILTER_ALL = "all"
        private const val FILTER_MISSED = "missed"
        /** Граничное значение «unix-миллисекунды» (~3366 год в секундах). */
        private const val MS_EPOCH_THRESHOLD = 100_000_000_000L
        /** peer_id групповых чатов/звонков VK — от 2e9. */
        private const val GROUP_PEER_MIN = 2_000_000_000L
    }
}
