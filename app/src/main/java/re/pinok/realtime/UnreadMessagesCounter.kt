package re.pinok.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * #32: Глобальный счётчик непрочитанных сообщений (бейдж «Сообщения» в доке).
 *
 * Подписывается на [LongPollClient.events] и обновляется при:
 *  - [LongPollEvent.NewMessage] → +1 (если не outbox)
 *  - [LongPollEvent.ReadInbox] → перезагрузка счётчика
 *  - [LongPollEvent.UnreadCountersChanged] → перезагрузка
 *  - [LongPollEvent.Reset] → перезагрузка
 *
 * UI использует [unreadCount] для badge на иконке Messages в bottom nav
 * (SovaNavHost: NavigationBar + боковая панель).
 *
 * #COUNTER-CHANNELS (волна 18-α, требование пользователя: «счетчик количество
 * сообщений каналов не должны входить в счетчик сообщений диалогов или
 * непрочитанные»):
 *
 * БЫЛО: unread_count из messages.getConversations?count=0 — СЕРВЕРНАЯ агрегация,
 * включающая непрочитанное КАНАЛОВ (broadcast-сообществ). Пользователь видел
 * бейдж дока, раздутый постами каналов, которые он не читает.
 *
 * ТЕПЕРЬ: счётчик считается КЛИЕНТСКИ как сумма unread_count только по
 * ДИАЛОГАМ (isChannel = false, т.е. peer не канал: isChannel = group &&
 * can_write.allowed=false — та же классификация, что и вкладки #DIALOGS-TAB).
 * Клиентский подсчёт выбран сознательно: он корректен ПО ПОСТРОЕНИЮ
 * (считает ровно то, что требует пользователь) и не зависит от
 * не доказуемой по коду серверной семантики unread_count по отношению к
 * каналам. Это тот же VK web паттерн: бейдж мессенджера считает только
 * ЛС/беседы, каналы — отдельно.
 *
 * Граница честности: пагинация getConversations ограничена
 * [COUNTER_MAX_PAGES] страницами по [COUNTER_PAGE_SIZE]; диалоги глубже
 * новейших ~600 в бейдж не попадают (для бейджа это несущественно — непрочитанное
 * старше 600 свежих диалогов почти не встречается; точный серверный aggregate
 * исключить каналы не позволяет).
 */
object UnreadMessagesCounter {

    private const val TAG = "UnreadCounter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    @Volatile
    private var initialized = false

    // #COUNTER-CHANNELS: параметры клиентского подсчёта.
    // 200 — максимум count у messages.getConversations за 1 вызов.
    private const val COUNTER_PAGE_SIZE = 200
    private const val COUNTER_MAX_PAGES = 3

    // Single-flight: параллельные refresh сливаются в один; события, пришедшие
    // во время подсчёта, дают ровно ОДИН повтор после текущей итерации.
    private val refreshLock = Mutex()

    @Volatile
    private var refreshQueued = false

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
                            // #COUNTER-CHANNELS: слепой +1 — только для пиров,
                            // которые гарантированно НЕ каналы: пользователи
                            // (peerId > 0) и беседы (peerId >= 2e9). Для peerId < 0
                            // по событию невозможно отличить канал (не входит в
                            // счётчик) от сообщества-диалога (входит) — запускаем
                            // точный пересчёт: getConversations знает can_write
                            // каждого пира.
                            if (event.peerId > 0L) {
                                _unreadCount.value = _unreadCount.value + 1
                                AppLog.d(TAG, "NewMessage: unreadCount → ${_unreadCount.value}")
                            } else {
                                AppLog.d(TAG,
                                    "#COUNTER-CHANNELS: NewMessage peer=${event.peerId} — recompute (community/channel)")
                                refreshCount(app)
                            }
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
     * Запросить актуальный счётчик.
     *
     * #COUNTER-CHANNELS: раньше — messages.getConversations?count=0 (серверный
     * unread_count, включал каналы). Теперь — клиентский подсчёт по страницам
     * getConversations (см. [computeUnread]).
     *
     * Single-flight + coalescing: если подсчёт уже идёт, новый вызов НЕ запускает
     * параллельную работу, а помечает очередь — активная корутина после текущей
     * итерации сделает ровно один повтор (с троттлингом против шторма LP 1/2/3/80,
     * которые прилетают на каждое изменение флагов сообщений).
     */
    fun refreshCount(app: SovaApp) {
        scope.launch {
            if (!refreshLock.tryLock()) {
                refreshQueued = true
                return@launch
            }
            try {
                do {
                    refreshQueued = false
                    computeUnread(app)
                    if (refreshQueued) delay(1500)
                } while (refreshQueued)
            } finally {
                refreshLock.unlock()
            }
        }
    }

    /**
     * #COUNTER-CHANNELS: клиентский подсчёт непрочитанных ДИАЛОГОВ (без каналов).
     *
     * Пагинация: getConversations по [COUNTER_PAGE_SIZE], до [COUNTER_MAX_PAGES]
     * страниц. Каналы (chat.isChannel) суммируются отдельно — только для лога,
     * в бейдж не попадают.
     */
    private suspend fun computeUnread(app: SovaApp) {
        try {
            var dialogsUnread = 0
            var channelsUnread = 0
            var offset = 0
            var pages = 0
            while (pages < COUNTER_MAX_PAGES) {
                val page = app.apiClient.messagesGetConversations(
                    count = COUNTER_PAGE_SIZE,
                    offset = offset,
                )
                if (page.isEmpty()) break
                for (chat in page) {
                    if (chat.isChannel) {
                        channelsUnread += chat.unreadCount
                    } else {
                        dialogsUnread += chat.unreadCount
                    }
                }
                offset += page.size
                pages++
                if (page.size < COUNTER_PAGE_SIZE) break
            }
            _unreadCount.value = dialogsUnread
            AppLog.d(TAG,
                "#COUNTER-CHANNELS: refreshCount: dialogsUnread=$dialogsUnread " +
                    "channelsExcluded=$channelsUnread pages=$pages")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "#COUNTER-CHANNELS: refreshCount failed: ${e.message}")
        }
    }

    /** Сброс счётчика (когда пользователь открыл вкладку Сообщения). */
    fun reset() {
        _unreadCount.value = 0
    }
}
