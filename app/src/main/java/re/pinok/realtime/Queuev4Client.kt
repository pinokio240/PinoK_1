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
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.api.VKApiClient
import re.pinok.data.model.QueueCredential
import re.pinok.data.model.QueueEvent
import re.pinok.util.AppLog
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
class Queuev4Client(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
) {
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

    private val _events = MutableSharedFlow<QueueEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<QueueEvent> = _events.asSharedFlow()

    fun setCredential(cred: QueueCredential) { credential = cred }

    /** True если long-poll цикл активен. */
    fun isRunning(): Boolean = pollJob?.isActive == true

    fun start() {
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

    private suspend fun pollLoop(cred: QueueCredential) {
        var ts = cred.ts
        var backoff = MIN_BACKOFF_MS
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                // Формат a_check (mobile QueueManager): act/ts/key/id/wait — БЕЗ mode/version.
                val url = "${cred.url}?act=a_check&key=${cred.key}&ts=$ts&id=${cred.userId}&wait=25"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isBlank()) {
                    delay(backoff)
                    backoff = min(backoff * 2, MAX_BACKOFF_MS)
                    continue
                }
                backoff = MIN_BACKOFF_MS

                val (newTs, rawEvents) = parseResponse(body, ts)
                ts = newTs

                if (rawEvents.isEmpty()) continue

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
