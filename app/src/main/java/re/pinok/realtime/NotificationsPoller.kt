package re.pinok.realtime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

/**
 * §42 #PUSH-NOTIFICATIONS — периодический опрос notifications.getRedesign
 * + показ system notifications для новых элементов (лайки/комментарии/
 * репосты/ответы/подписки/упоминания/подарки/записи на стене).
 *
 * Архитектура (без FCM, через LongPoll + периодический poll):
 *
 *   1. LongPollClient получает code 114 ($new_unread_count) → emit
 *      LongPollEvent.NotificationsCountChanged → SovaApp вызывает
 *      poller.triggerImmediatePoll().
 *   2. triggerImmediatePoll() → pollOnce() (вне зависимости от таймера).
 *   3. Fallback: периодический таймер (pushPollingIntervalSec, default 120с)
 *      на случай если LongPoll не активен или code 114 не пришёл.
 *   4. pollOnce():
 *      a) notificationsGetRedesign(count=30).
 *      b) Diff с pushLastSeenKeys (CSV в SovaPrefs).
 *      c) Для каждого нового item: проверка per-category toggle →
 *         VkNotificationsNotifier.showBatch() (группировка + summary).
 *      d) Обновление pushLastSeenKeys (max 100 последних uniqueKey).
 *
 * Потокобезопасность:
 *  - pollMutex гарантирует что pollOnce не вызывается параллельно
 *    (triggerImmediatePoll + периодический таймер могут конкурировать).
 *  - supervisorJob гарантирует что ошибка в одном poll не убивает весь poller.
 *
 * Жизненный цикл:
 *  - start() вызывается из SovaApp.onCreate (если pushEnabled && hasValidToken).
 *  - stop() вызывается при logout (tokenStorage.clear).
 *  - triggerImmediatePoll() — из SovaApp при LongPoll code 114.
 */
class NotificationsPoller(
    private val context: Context,
    private val api: VKApiClient,
    private val prefs: SovaPrefs,
) {

    private val TAG = "NotificationsPoller"

    private val supervisorJob = SupervisorJob()
    private val pollScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var periodicJob: Job? = null

    private val pollMutex = Mutex()

    /** true если poller активен (start вызван, stop не вызван). */
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Запускает периодический опрос. Безопасно вызывать многократно —
     * если уже запущен, no-op.
     *
     * Не запускает poll если:
     *  - pushEnabled=false (в SovaPrefs)
     *  - нет валидного токена
     */
    fun start() {
        if (_isRunning.value) {
            AppLog.d(TAG, "start: already running — skip")
            return
        }
        _isRunning.value = true
        periodicJob = pollScope.launch {
            AppLog.i(TAG, "Notifications poller started")
            // Первоначальный poll через 5с после старта (не блокируем boot).
            delay(5_000L)
            while (isActive) {
                try {
                    val snap = prefs.data.first()
                    if (!snap.pushEnabled) {
                        AppLog.d(TAG, "pushEnabled=false — pausing poller")
                        // Не выходим из цикла — продолжаем проверять, чтобы
                        // можно было включить push обратно без restart.
                        val intervalMs = (snap.pushPollingIntervalSec.coerceIn(60, 600) * 1000L)
                        delay(intervalMs)
                        continue
                    }
                    pollOnce()
                } catch (e: Exception) {
                    AppLog.w(TAG, "periodic poll failed: ${e.message}")
                }
                // Интервал из prefs (60-600с, default 120).
                val snap = prefs.data.first()
                val intervalMs = (snap.pushPollingIntervalSec.coerceIn(60, 600) * 1000L)
                delay(intervalMs)
            }
        }
    }

    /** Останавливает poller. Безопасно вызывать если не запущен. */
    fun stop() {
        if (!_isRunning.value) {
            return
        }
        _isRunning.value = false
        periodicJob?.cancel()
        periodicJob = null
        AppLog.i(TAG, "Notifications poller stopped")
    }

    /**
     * Триггерит немедленный poll (вне расписания). Безопасно вызывать
     * параллельно с периодическим poll'ом — pollMutex гарантирует что
     * только один pollOnce выполняется одновременно.
     *
     * Вызывается из SovaApp при получении LongPoll code 114.
     */
    fun triggerImmediatePoll() {
        pollScope.launch {
            try {
                val snap = prefs.data.first()
                if (!snap.pushEnabled) {
                    AppLog.d(TAG, "triggerImmediatePoll: pushEnabled=false — skip")
                    return@launch
                }
                if (!SovaApp.get(context).tokenStorage.hasValidToken()) {
                    AppLog.d(TAG, "triggerImmediatePoll: no valid token — skip")
                    return@launch
                }
                pollOnce()
            } catch (e: Exception) {
                AppLog.w(TAG, "triggerImmediatePoll failed: ${e.message}")
            }
        }
    }

    /**
     * Один цикл опроса: fetch → diff → show notifications → update lastSeen.
     *
     * Потокобезопасность: pollMutex гарантирует что только один pollOnce
     * выполняется одновременно (triggerImmediatePoll + periodic могут
     * конкурировать).
     */
    private suspend fun pollOnce() {
        pollMutex.withLock {
            try {
                val snap = prefs.data.first()
                if (!snap.pushEnabled) return@withLock

                AppLog.d(TAG, "pollOnce: fetching notifications.getRedesign...")
                val (items, _) = api.notificationsGetRedesign(count = 30)
                if (items.isEmpty()) {
                    AppLog.d(TAG, "pollOnce: no notifications returned")
                    return@withLock
                }

                // Diff с lastSeenKeys.
                val seenKeys = parseSeenKeys(snap.pushLastSeenKeys)
                val newItems = items.filter { it.uniqueKey !in seenKeys }

                if (newItems.isEmpty()) {
                    AppLog.d(TAG, "pollOnce: ${items.size} items, all already seen — no new notifications")
                    // Всё равно обновляем seenKeys (на случай если VK вернул
                    // элементы в другом порядке — сохраняем актуальный топ).
                    val updatedKeys = items.take(100).joinToString(",") { it.uniqueKey }
                    if (updatedKeys != snap.pushLastSeenKeys) {
                        prefs.setPushLastSeenKeys(updatedKeys)
                    }
                    return@withLock
                }

                AppLog.i(TAG, "pollOnce: ${items.size} total, ${newItems.size} NEW — showing notifications")

                // §42.3 #PUSH-SOURCE-FILTER: парсим sn_* states из notifyCacheJson.
                // Эти states — кэш BFF-настроек (settingsGeneral.getNotifySettings),
                // которые пользователь меняет в Настройках → Уведомления → sn_* toggles.
                // Раньше они не работали (server-side, для FCM) — теперь применяем
                // client-side в SnNotifyFilter.passes().
                val snStates = SnNotifyFilter.parseStates(snap.notifyCacheJson)

                // §42.2 #PUSH-ENHANCED: передаём все новые items в showBatch.
                // Notifier сам фильтрует (quiet hours, per-user mute, category,
                // source, sn_*) и группирует (none/category/community/user).
                VkNotificationsNotifier.showBatch(
                    context = context,
                    items = newItems,
                    snap = snap,
                    categoryFilter = { channel -> isCategoryEnabled(snap, channel) },
                    snStates = snStates,
                )

                // Обновляем seenKeys: новые + старые (top 100 uniqueKey).
                val mergedKeys = (newItems.asReversed() + items).map { it.uniqueKey }.distinct().take(100)
                prefs.setPushLastSeenKeys(mergedKeys.joinToString(","))
            } catch (e: Exception) {
                AppLog.w(TAG, "pollOnce failed: ${e.message}")
            }
        }
    }

    /**
     * Проверяет включён ли push для категории в данном snapshot.
     */
    private fun isCategoryEnabled(snap: SovaPrefs.Snapshot, channel: String): Boolean {
        return when (channel) {
            VkNotificationsNotifier.CHANNEL_LIKES -> snap.pushLikes
            VkNotificationsNotifier.CHANNEL_COMMENTS -> snap.pushComments
            VkNotificationsNotifier.CHANNEL_REPLIES -> snap.pushReplies
            VkNotificationsNotifier.CHANNEL_FOLLOWS -> snap.pushFollows
            VkNotificationsNotifier.CHANNEL_MENTIONS -> snap.pushMentions
            VkNotificationsNotifier.CHANNEL_REPOSTS -> snap.pushReposts
            VkNotificationsNotifier.CHANNEL_WALL -> snap.pushWall
            VkNotificationsNotifier.CHANNEL_GIFTS -> snap.pushGifts
            VkNotificationsNotifier.CHANNEL_OTHER -> snap.pushOther
            else -> true
        }
    }

    /**
     * Парсит CSV seenKeys из prefs в Set<String>.
     * Пустая строка → пустой set.
     */
    private fun parseSeenKeys(csv: String): Set<String> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",").filter { it.isNotBlank() }.toSet()
    }
}
