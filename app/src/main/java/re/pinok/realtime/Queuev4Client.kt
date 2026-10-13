package re.pinok.realtime

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.api.VKApiClient
import re.pinok.feature.calls.CallsQueue
import re.pinok.data.model.QueueCredential
import re.pinok.data.model.QueueEvent
import re.pinok.util.AppLog
import java.net.URLEncoder
import kotlin.math.min
import kotlin.random.Random

/**
 * #CALLS: long-poll клиент для queuev4.vk.ru (сигналинг звонков и события im).
 *
 * Формат запроса (расшифрован из m.vk.ru бандлов notifier/QueueManager):
 *   GET <url>?act=a_check&key=<key>&ts=<ts>&id=<uid>&wait=25
 *
 * Формат ответа — JSON **массив** [main, add1, ...] ИЛИ объект {failed, ts, ...}:
 *   - массив: main = { ts, ... } — обновляет timestamp; каждый add = { failed?, events: [...] }
 *   - объект: main_response сам по себе; события в `events` или `updates` (массив массивов кодов)
 *
 * События queuev4 — массивы [код, ...аргументы]. LP-коды:
 *   - 115 → INCOMING_CALL, payload = строка conversation-params звонка
 *   - 70  → VIDEO_CALL, args = [userId, callId]
 *   - -1  → history lost (нужен ресинк / новый credential)
 *   - -2  → key expired (нужен новый credential)
 *
 * Используется для приёма входящих звонков (LP 115).
 */
/**
 * P0.42-CALLS-HAR: событие звонка из queuev4 (новый формат).
 * HAR: {"data":{"value":{...},"type":"incoming_call"},"entity_type":"calls"}
 */
data class CallEvent(
    val type: String,          // "incoming_call", "call_accepted", "current_calls_update", "call_history_updated"
    val value: JsonObject?,    // payload события (call_id, caller_info, conversation_params, ...)
    val entityType: String,     // "calls"
)

// Task 20: реализует CallsQueue (фасад :feature:calls) — события/креденшелы queue.
class Queuev4Client(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
) : CallsQueue {
    companion object {
        private const val TAG = "Queuev4Client"
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        /** LP-коды queuev4. */
        const val LP_INCOMING_CALL = 115
        const val LP_VIDEO_CALL = 70
        const val LP_HISTORY_LOST = -1
        const val LP_KEY_EXPIRED = -2

        /** Имя calls-очереди (из localStorage queue_credential_calls_cache_<uid>_<app_id>). */
        const val QUEUE_CALLS = "calls"
    }

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var credential: QueueCredential? = null

    // P3.8: ссылка на in-flight OkHttp-call — чтобы stop() мог отменить poll,
    // не дожидаясь истечения wait=25с. @Volatile: stop() вызывается из другого
    // потока, чем pollLoop (Dispatchers.IO). Зануляем в finally pollLoop'а.
    @Volatile
    private var currentCall: Call? = null

    private val _events = MutableSharedFlow<QueueEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<QueueEvent> = _events.asSharedFlow()

    override fun setCredential(cred: QueueCredential) { credential = cred }

    /** True если long-poll цикл активен. */
    fun isRunning(): Boolean = pollJob?.isActive == true

    override fun start() {
        if (pollJob?.isActive == true) return
        // #NULL-EXPLICIT: credential — var-свойство класса, smart-cast невозможен —
        // захватываем в локальный val (снимок на момент start()). Инвариант: владелец
        // обязан вызвать setCredential() до start() — иначе ранний возврат с логом.
        val cred = credential
        if (cred == null) {
            AppLog.w(TAG, "No queue credential — call setCredential() first")
            return
        }
        pollJob = scope.launch { pollLoop(cred) }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        // P3.8: отменяем in-flight OkHttp-call — иначе execute() будет
        // блокировать поток до истечения wait=25с даже после stop().
        currentCall?.cancel()
        currentCall = null
        credential = null
    }

    /**
     * Разбирает ответ queuev4 (массив [main, add...] или объект) в список событий.
     * Возвращает Pair(новый ts, список событий-массивов).
     */
    internal fun parseResponse(body: String, prevTs: Long): Pair<Long, List<JsonArray>> {
        val events = mutableListOf<JsonArray>()
        var newTs = prevTs
        val parsed = JsonParser.parseString(body)
        when {
            parsed.isJsonArray -> {
                val arr = parsed.asJsonArray
                if (arr.size() > 0) {
                    val main = arr[0]
                    if (main.isJsonObject) {
                        val m = main.asJsonObject
                        newTs = m.get("ts")?.takeIf { it.isJsonPrimitive }?.asLong ?: newTs
                        collectEvents(m, events)
                    }
                }
                // add-очереди: каждый элемент после main — { failed?, events: [...] }
                for (i in 1 until arr.size()) {
                    val add = arr[i]
                    if (add.isJsonObject) {
                        val a = add.asJsonObject
                        val failed = a.get("failed")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                        if (failed != 0) {
                            AppLog.d(TAG, "add-queue failed: $failed err=${a.get("err")}")
                            continue
                        }
                        collectEvents(a, events)
                    }
                }
            }
            parsed.isJsonObject -> {
                val obj = parsed.asJsonObject
                newTs = obj.get("ts")?.takeIf { it.isJsonPrimitive }?.asLong ?: newTs
                collectEvents(obj, events)
            }
        }
        return newTs to events
    }

    private fun collectEvents(o: JsonObject, out: MutableList<JsonArray>) {
        o.get("events")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
            if (el.isJsonArray) out.add(el.asJsonArray)
        }
        o.get("updates")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
            if (el.isJsonArray) out.add(el.asJsonArray)
        }
    }

    // P0.42-CALLS-HAR: парсинг НОВОГО формата queuev4 — события как JSON-объекты.
    // Реальная структура (HAR сигналинг.zip):
    //   { "ts":"1227312320","events":[{
    //     "data": {
    //       "value": { "call_id":"...","caller_info":{...},
    //                  "conversation_params":"...","from_id":152094335 },
    //       "type": "incoming_call"          ← type ВНУТРИ data
    //     },
    //     "entity_type": "calls",            ← entity_type НА ВЕРХНЕМ УРОВНЕ events[i]
    //     "entity_id": 0
    //   }]}
    // Для call_finished value — строка (call_id), для call_history_updated — отсутствует.
    private fun collectCallEvents(o: JsonObject): List<CallEvent> {
        val events = mutableListOf<CallEvent>()
        o.get("events")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val ev = el.asJsonObject
            // entity_type НА ВЕРХНЕМ УРОВНЕ events[i] (рядом с data)
            val entityType = ev.get("entity_type")?.takeIf { it.isJsonPrimitive }?.asString
            if (entityType == null || entityType != "calls") return@forEach
            val data = ev.getAsJsonObject("data") ?: return@forEach
            // type ВНУТРИ data (для всех событий calls)
            val type = data.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
            // value может быть: объектом (incoming_call), строкой (call_finished),
            // или отсутствовать (call_history_updated, current_calls_update).
            val valueEl = data.get("value")
            val value: JsonObject? = if (valueEl != null && valueEl.isJsonObject) valueEl.asJsonObject else null
            events.add(CallEvent(type = type, value = value, entityType = entityType))
        }
        return events
    }

    // P0.42-CALLS-HAR: парсинг call-событий из ответа queuev4.
    private fun parseCallEvents(body: String): List<CallEvent> {
        return try {
            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonObject) {
                AppLog.d(TAG, "parseCallEvents: not a JSON object (type=${parsed.javaClass.simpleName})")
                return emptyList()
            }
            val obj = parsed.asJsonObject
            val result = collectCallEvents(obj)
            // P0.42-CALLS-DIAG: логируем результат парсинга — видим что нашли.
            if (result.isNotEmpty()) {
                AppLog.i(TAG, "parseCallEvents: parsed ${result.size} call events: types=${result.map { it.type }}")
            }
            result
        } catch (e: Exception) {
            AppLog.w(TAG, "parseCallEvents error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun pollLoop(cred: QueueCredential) {
        var ts = cred.ts
        var backoff = MIN_BACKOFF_MS
        // P0.42-CALLS-DIAG: логируем старт pollLoop — если этой строки нет, pollLoop не запустился.
        AppLog.i(TAG, "pollLoop started: url=${cred.url} ts=$ts userId=${cred.userId} key=${cred.key.take(8)}…")
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                val encodedKey = URLEncoder.encode(cred.key, "UTF-8")
                val url = "${cred.url}?act=a_check&key=$encodedKey&ts=$ts&id=${cred.userId}&wait=25"
                val request = Request.Builder().url(url).get().build()
                // P3.8: сохраняем Call в currentCall, чтобы stop() мог cancel()'нуть
                // in-flight poll. execute() блокирует до ответа VK (до wait=25с);
                // без cancel() stop() не прерывает активный poll.
                val call = httpClient.newCall(request)
                currentCall = call
                val response = try {
                    call.execute()
                } finally {
                    currentCall = null
                }
                val body = response.body?.string() ?: ""
                val httpCode = response.code
                response.close()
                // P0.42-CALLS-DIAG: логируем каждый poll-цикл — иначе pollLoop "молчит"
                // и непонятно: ждёт ответа / получил пусто / не распарсил формат.
                // Тримим body до 200 символов — длинные conversation_params не засоряют лог.
                val bodyPreview = if (body.length > 200) body.substring(0, 200) + "…(${body.length})" else body
                AppLog.d(TAG, "poll: code=$httpCode body.len=${body.length} key=${cred.key.take(8)}… ts=$ts body=$bodyPreview")
                if (body.isBlank()) {
                    AppLog.w(TAG, "poll: blank body (code=$httpCode) — backoff=${backoff}ms")
                    delay(backoff)
                    backoff = min(backoff * 2, MAX_BACKOFF_MS)
                    continue
                }
                backoff = MIN_BACKOFF_MS

                val (newTs, rawEvents) = parseResponse(body, ts)
                ts = newTs

                // P0.42-CALLS-HAR: парсим новый формат (events как JSON-объекты с entity_type).
                val callEvents = parseCallEvents(body)

                if (rawEvents.isEmpty() && callEvents.isEmpty()) continue

                // P0.42-CALLS-HAR: сначала обрабатываем новый формат (calls-события).
                for (ce in callEvents) {
                    when (ce.type) {
                        "incoming_call" -> {
                            val callId = ce.value?.get("call_id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val fromId = ce.value?.get("from_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                            val convParams = ce.value?.get("conversation_params")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val callerInfo = ce.value?.getAsJsonObject("caller_info")
                            val payload = ce.value?.get("payload")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            AppLog.i(TAG, "INCOMING_CALL (queuev4 calls): callId=$callId fromId=$fromId convParams.len=${convParams.length}")
                            _events.emit(QueueEvent(
                                queueId = QUEUE_CALLS,
                                ts = ts,
                                payload = mapOf(
                                    "type" to "incoming_call",
                                    "call_id" to callId,
                                    "from_id" to fromId,
                                    "conversation_params" to convParams,
                                    "caller_info" to (callerInfo ?: JsonObject()),
                                    "payload" to payload,
                                ),
                            ))
                        }
                        "call_accepted" -> {
                            val callId = ce.value?.takeIf { it.isJsonPrimitive }?.asString
                                ?: ce.value?.get("value")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                            AppLog.i(TAG, "CALL_ACCEPTED (queuev4 calls): callId=$callId")
                            _events.emit(QueueEvent(queueId = QUEUE_CALLS, ts = ts,
                                payload = mapOf("type" to "call_accepted", "call_id" to callId)))
                        }
                        "current_calls_update" -> {
                            AppLog.d(TAG, "CURRENT_CALLS_UPDATE (queuev4 calls)")
                            _events.emit(QueueEvent(queueId = QUEUE_CALLS, ts = ts,
                                payload = mapOf("type" to "current_calls_update")))
                        }
                        "call_history_updated" -> {
                            AppLog.d(TAG, "CALL_HISTORY_UPDATED (queuev4 calls)")
                            _events.emit(QueueEvent(queueId = QUEUE_CALLS, ts = ts,
                                payload = mapOf("type" to "call_history_updated")))
                        }
                        else -> {
                            AppLog.d(TAG, "CALL_EVENT unknown type=${ce.type}")
                        }
                    }
                }

                // Legacy формат (массивы [code, payload]) — оставляем для совместимости.
                for (ev in rawEvents) {
                    if (ev.size() == 0) continue
                    val code = ev[0]?.takeIf { it.isJsonPrimitive }?.asInt ?: continue
                    when (code) {
                        LP_HISTORY_LOST, LP_KEY_EXPIRED -> {
                            AppLog.w(TAG, "LP code $code — credential expired, re-request")
                            // Останавливаемся — нужен новый credential (ресинк).
                            // Не ставим ts=0 бесконечно; положим паузу и попробуем заново.
                            _events.emit(QueueEvent(queueId = "lp", ts = ts, payload = mapOf("code" to code.toLong())))
                        }
                        LP_INCOMING_CALL -> {
                            // [115, payload] — payload = строка conversation params
                            val payload = ev[1]?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                            AppLog.i(TAG, "INCOMING_CALL (LP 115), payload.len=${payload.length}")
                            _events.emit(QueueEvent(queueId = QUEUE_CALLS, ts = ts, payload = mapOf("payload" to payload)))
                        }
                        LP_VIDEO_CALL -> {
                            val userId = ev[1]?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                            val callId = ev[2]?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                            _events.emit(QueueEvent(queueId = QUEUE_CALLS, ts = ts, payload = mapOf("userId" to userId, "callId" to callId)))
                        }
                        else -> {
                            // Прочие события — отдаём как есть (payload = остаток массива).
                            _events.emit(QueueEvent(queueId = "lp:$code", ts = ts, payload = mapOf("code" to code.toLong())))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "pollLoop error: ${e.message}")
                delay(backoff + Random.nextLong(0, 1000))
                backoff = min(backoff * 2, MAX_BACKOFF_MS)
            }
        }
    }
}
