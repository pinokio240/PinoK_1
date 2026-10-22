package re.pinok.realtime

import java.util.concurrent.ConcurrentHashMap

/**
 * #EVENTHUB: дедупликация событий между двумя realtime-каналами
 * ([LongPollClient] и [EventHubWebSocketClient]).
 *
 * Когда оба канала работают, одно и то же «новое сообщение» может прийти
 * дважды: по LongPoll (wait=25с) И по EventHub (мгновенно). Чтобы счётчик
 * непрочитанных и уведомления не срабатывали дважды, обработчики перед
 * обработкой проверяют ключ (peerId, messageId) через [isDuplicate] —
 * повтор в пределах [WINDOW_MS] пропускается.
 *
 * Лучше-effort (несовершенен): при одновременном приходе дубликатов в разных
 * корутинах возможна гонка, дающая редкий пропуск — для счётчика/уведомления
 * это некритично (LongPoll всё равно пересчитывает счётчик).
 *
 * ## P2.2: cleanup стратегия (unbounded growth fix)
 *
 * Ранее `recent` рос без ограничений — при долгой uptime каждое уникальное
 * `(peerId, msgId)` оставалось в памяти навсегда (~10к ключей/день ≈ <1MB,
 * но течёт). Теперь:
 *
 * 1. **Inline cleanup** в [isDuplicate]: если `size > CLEANUP_THRESHOLD`,
 *    удаляем все записи старше `WINDOW_MS × 3` (15с). `ConcurrentHashMap.size`
 *    это O(n), но вызывается редко (только при превышении порога) и на 5к
 *    записей это <1ms — не блокирует realtime-поток.
 * 2. **Public [cleanup]**: для future use (если кто-то захочет cleanup по
 *    расписанию из LongPollKeepAliveService / RealtimeWakeReceiver).
 *
 * Thread safety: `ConcurrentHashMap.entries.iterator()` — weakly consistent,
 * может пропустить concurrent добавления (это OK для cleanup — такие записи
 * свежие и не подлежат удалению). `iter.remove()` — safe.
 */
object EventDedup {

    private const val WINDOW_MS = 5_000L

    /**
     * P2.2: max ключей в [recent] до запуска inline cleanup. При 10к сообщений/день
     * порог 5к срабатывает ~раз в 12ч — cleanup O(n) на 5к записей <1ms.
     */
    private const val CLEANUP_THRESHOLD = 5_000

    /**
     * P2.2: множитель для cutoff inline-cleanup (15с = 3 × WINDOW_MS).
     * Записи старше этого возраста точно не нужны (даже второй дубль за
     * пределами 5с окна уже не считается дубликатом).
     */
    private const val CLEANUP_AGE_MULTIPLIER = 3

    private data class Key(val peerId: Long, val msgId: Long)
    private val recent = ConcurrentHashMap<Key, Long>()

    /**
     * Помечает ключ как увиденный и возвращает true, если он уже встречался
     * в течение [WINDOW_MS] (т.е. это дубликат). Контракт: вызывать ОДИН раз
     * на событие, перед обработкой.
     *
     * P2.2: при превышении [CLEANUP_THRESHOLD] запускает inline cleanup —
     * удаляет записи старше `WINDOW_MS × [CLEANUP_AGE_MULTIPLIER]`.
     * Thread-safe, не блокирует (weakly consistent iterator).
     */
    fun isDuplicate(peerId: Long, msgId: Long): Boolean {
        val now = System.currentTimeMillis()
        val key = Key(peerId, msgId)
        val prev = recent.put(key, now)
        // P2.2: periodic cleanup — удаляем записи старше WINDOW_MS×3 (15с)
        // когда map разрастается. ConcurrentHashMap.size O(n), но редко.
        if (recent.size > CLEANUP_THRESHOLD) {
            val cutoff = now - WINDOW_MS * CLEANUP_AGE_MULTIPLIER
            val iter = recent.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (entry.value < cutoff) iter.remove()
            }
        }
        return prev != null && now - prev < WINDOW_MS
    }

    /**
     * P2.2: Удаляет все записи старше [maxAgeMs]. Безопасно вызывать из любого
     * потока (weakly consistent iterator). По умолчанию удаляет всё старше
     * `WINDOW_MS × [CLEANUP_AGE_MULTIPLIER]` (15с) — то же правило, что inline
     * cleanup в [isDuplicate].
     *
     * Для future use: можно дёргать из LongPollKeepAliveService / RealtimeWakeReceiver
     * по расписанию (alarm/foreground tick), чтобы cleanup не зависел от трафика.
     */
    fun cleanup(maxAgeMs: Long = WINDOW_MS * CLEANUP_AGE_MULTIPLIER) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val iter = recent.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value < cutoff) iter.remove()
        }
    }
}
