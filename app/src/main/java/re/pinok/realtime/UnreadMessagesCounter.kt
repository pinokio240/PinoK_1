package re.pinok.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * #32: Глобальный счётчик непрочитанных сообщений.
 *
 * Подписывается на [LongPollClient.events] и обновляется при:
 *  - [LongPollEvent.NewMessage] → +1 (если не outbox)
 *  - [LongPollEvent.ReadInbox] → перезагрузка счётчика
 *  - [LongPollEvent.UnreadCountersChanged] → перезагрузка
 *  - [LongPollEvent.Reset] → перезагрузка
 *
 * UI использует [unreadCount] для badge на иконке Messages в bottom nav.
 */
object UnreadMessagesCounter {

    private const val TAG = "UnreadCounter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    @Volatile
    private var initialized = false

    /**
     * Запускает подписку на LongPoll events. Вызывается из [re.pinok.SovaApp.onCreate].
     */
    fun init(app: SovaApp) {
        if (initialized) return
        initialized = true

        // Первичная загрузка счётчика
        refreshCount(app)

        // Подписка на LongPoll события
        scope.launch {
            app.longPollClient.events.collect { event ->
                when (event) {
                    is LongPollEvent.NewMessage -> {
                        // Флаг 2 = outbox (исходящее), не считаем
                        if (event.flags and 2 == 0) {
                            _unreadCount.value = _unreadCount.value + 1
                            AppLog.d(TAG, "NewMessage: unreadCount → ${_unreadCount.value}")
                        }
                    }
                    is LongPollEvent.ReadInbox,
                    is LongPollEvent.UnreadCountersChanged,
                    is LongPollEvent.Reset -> {
                        refreshCount(app)
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Запросить актуальный счётчик через messages.getConversations?count=0.
     * VK возвращает response.unread_count.
     */
    fun refreshCount(app: SovaApp) {
        scope.launch {
            try {
                val count = app.apiClient.messagesGetUnreadCount()
                _unreadCount.value = count
                AppLog.d(TAG, "refreshCount: unread=$count")
            } catch (e: Exception) {
                AppLog.w(TAG, "refreshCount failed: ${e.message}")
            }
        }
    }

    /** Сброс счётчика (когда пользователь открыл вкладку Сообщения). */
    fun reset() {
        _unreadCount.value = 0
    }
}
