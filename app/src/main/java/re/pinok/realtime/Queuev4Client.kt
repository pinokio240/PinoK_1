package re.pinok.realtime

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

class Queuev4Client(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
) {
    companion object {
        private const val TAG = "Queuev4Client"
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var credential: QueueCredential? = null

    private val _events = MutableSharedFlow<QueueEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<QueueEvent> = _events.asSharedFlow()

    fun setCredential(cred: QueueCredential) { credential = cred }

    fun start() {
        if (pollJob?.isActive == true) return
        if (credential == null) {
            AppLog.w(TAG, "No queue credential — call setCredential() first")
            return
        }
        pollJob = scope.launch { pollLoop(credential!!) }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        credential = null
    }

    private suspend fun pollLoop(cred: QueueCredential) {
        var ts = cred.ts
        var backoff = MIN_BACKOFF_MS
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                val url = "${cred.url}?act=a_check&key=${cred.key}&ts=$ts&wait=25&mode=2&version=10"
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
                val json = JsonParser.parseString(body).asJsonObject
                ts = json.get("ts")?.asLong ?: ts
                val updates = json.getAsJsonArray("updates") ?: continue
                for (el in updates) {
                    if (!el.isJsonObject) continue
                    val obj = el.asJsonObject
                    val queueId = obj.get("queue_id")?.asString ?: continue
                    val data = obj.getAsJsonObject("data") ?: continue
                    val payload = mutableMapOf<String, Any?>()
                    for ((k, v) in data.entrySet()) {
                        payload[k] = when {
                            v.isJsonPrimitive -> {
                                val p = v.asJsonPrimitive
                                when { p.isString -> p.asString; p.isNumber -> p.asLong; p.isBoolean -> p.asBoolean; else -> v.toString() }
                            }
                            else -> v.toString()
                        }
                    }
                    _events.emit(QueueEvent(queueId = queueId, ts = ts, payload = payload))
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