package re.pinok.realtime

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
import re.pinok.api.VKApiClient
import re.pinok.data.model.QueueCredential
import re.pinok.util.AppLog
import kotlin.math.min

/**
 * #ADMIN-STATS-W47 (2026-09-27): long-poll клиент очереди статистики
 * сообщества (Mini App 51912452, vboardcard).
 *
 * Отличия от [Queuev4Client]:
 *  - события vboardcard приходят JSON-ОБЪЕКТАМИ ({entity_type, data}),
 *    а не LP-массивами → Queuev4Client.collectEvents их игнорирует;
 *  - эмитит готовые [VKApiClient.StatsBoardEvent] (task_id + chunk JSON-строка),
 *    а не LP-коды;
 *  - queue_id = vboardcard_<uid>_<gid>_1 (см. VKEndpoints.statsBoardQueueId).
 *
 * Механика из C:/Users/Pinokio240/Desktop/Ссылки/админка/статистика.har:
 *   1. queue.subscribe -> {base_url, key, timestamp}
 *   2. GET base_url?act=a_check&key&ts&id&wait=45
 *      -> events[{entity_type:"vboardcard", data:{task_id, task_result:{chunk}}}]
 *
 * Использование:
 *   val poller = StatsQueuePoller(httpClient, apiClient)
 *   if (poller.start(uid, gid)) {
 *     scope.launch { poller.chunks.collect { ev -> handle(ev.taskId, ev.chunk) } }
 *   }
 *   poller.stop()
 */
class StatsQueuePoller(
    private val httpClient: OkHttpClient,
    private val apiClient: VKApiClient,
) {
    companion object {
        private const val TAG = "StatsQueuePoller"
        private const val MIN_BACKOFF_MS = 1_500L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val WAIT_SEC = 45
    }

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var subscribedUid: Long = 0L
    private var subscribedGid: Long = 0L

    private val _chunks = MutableSharedFlow<VKApiClient.StatsBoardEvent>(
        replay = 32,
        extraBufferCapacity = 64,
    )
    val chunks: SharedFlow<VKApiClient.StatsBoardEvent> = _chunks.asSharedFlow()

    fun isRunning(): Boolean = pollJob?.isActive == true

    /**
     * Подписаться на очередь статистики и запустить long-poll цикл.
     * @return true если queue.subscribe вернул credential.
     */
    suspend fun start(userId: Long, groupId: Long): Boolean {
        if (pollJob?.isActive == true) return true
        AppLog.i(TAG, "PINOK_STATS_MARKER_W47_20260928 start uid=$userId gid=$groupId")
        val cred = apiClient.statsBoardQueueSubscribe(userId, groupId) ?: run {
            AppLog.w(TAG, "statsBoardQueueSubscribe failed (uid=$userId gid=$groupId)")
            return false
        }
        subscribedUid = cred.userId
        subscribedGid = groupId
        AppLog.i(TAG, "stats queue subscribed: url=${cred.url} ts=${cred.ts}")
        pollJob = scope.launch { pollLoop(cred) }
        return true
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollLoop(initial: QueueCredential) {
        var cred = initial
        var backoff = MIN_BACKOFF_MS
        while (scope.coroutineContext.isActive) {
            val result: Pair<Long, List<VKApiClient.StatsBoardEvent>>? = try {
                apiClient.statsBoardQueuePoll(cred, waitSec = WAIT_SEC)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.e(TAG, "poll error", e)
                null
            }

            if (result == null) {
                delay(backoff)
                backoff = min(backoff * 2, MAX_BACKOFF_MS)
                // Переподписаться при устойчивых ошибках (credential мог протухнуть)
                if (backoff >= MAX_BACKOFF_MS) {
                    val fresh = try {
                        apiClient.statsBoardQueueSubscribe(subscribedUid, subscribedGid)
                    } catch (_: Exception) { null }
                    // (groupId здесь не знаем — оставляем старый cred; владелец при желании
                    //  пересоздаст poller. НЕ ломаем цикл.)
                    if (fresh != null) cred = fresh
                }
                continue
            }

            backoff = MIN_BACKOFF_MS
            val (newTs, events) = result
            if (newTs != cred.ts) cred = cred.copy(ts = newTs)
            for (ev in events) {
                AppLog.i(TAG, "chunk task_id=" + ev.taskId + " head=" + ev.chunk.take(2500))
                _chunks.tryEmit(ev)
            }
        }
    }
}