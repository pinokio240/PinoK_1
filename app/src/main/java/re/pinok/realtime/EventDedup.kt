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
 */
object EventDedup {

    private const val WINDOW_MS = 5_000L

    private data class Key(val peerId: Long, val msgId: Long)
    private val recent = ConcurrentHashMap<Key, Long>()

    /**
     * Помечает ключ как увиденный и возвращает true, если он уже встречался
     * в течение [WINDOW_MS] (т.е. это дубликат). Контракт: вызывать ОДИН раз
     * на событие, перед обработкой.
     */
    fun isDuplicate(peerId: Long, msgId: Long): Boolean {
        val now = System.currentTimeMillis()
        val key = Key(peerId, msgId)
        val prev = recent.put(key, now)
        return prev != null && now - prev < WINDOW_MS
    }
}