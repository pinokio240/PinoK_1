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
 * §37.12 Phase 7: Глобальный счётчик новых clips от подписок.
 *
 * Источник: account.getCounters → поле 'clips' (VK API отдаёт кол-во новых
 * clips от авторов, на которых подписан пользователь).
 *
 * Обновляется:
 *  - при старте приложения (LaunchedEffect в SovaApp)
 *  - раз в N минут (fallback polling, т.к. LongPoll не присылает clips-счётчик)
 *  - после просмотра clips-экрана (пользователь «прочитал» все новые clips)
 *
 * UI: badge на иконке «Клипы» в боковой панели.
 *
 * Reset: setCurrent(0) при открытии ClipsFeedScreen.
 */
object ClipsCounter {
    private const val TAG = "ClipsCounter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 минут
    private var refreshJob: kotlinx.coroutines.Job? = null

    /**
     * Запустить периодическое обновление счётчика.
     * Вызывается из SovaApp.onCreate() после инициализации apiClient.
     */
    fun start(app: SovaApp) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            // Первое обновление сразу.
            refreshOnce(app)
            // Периодический polling.
            while (true) {
                kotlinx.coroutines.delay(REFRESH_INTERVAL_MS)
                refreshOnce(app)
            }
        }
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Одноразовое обновление — вызывается из clips-экрана при pull-to-refresh
     * или из SovaApp. Получаем account.getCounters, читаем поле 'clips'.
     */
    suspend fun refreshOnce(app: SovaApp) {
        try {
            val counters = app.apiClient.accountGetCounters()
            val rawClips = counters["clips"]
            val newCount = when (rawClips) {
                is Number -> rawClips.toInt()
                is String -> rawClips.toIntOrNull() ?: 0
                else -> 0
            }
            if (newCount != _count.value) {
                _count.value = newCount
                AppLog.d(TAG, "clips counter updated: $newCount")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "refreshOnce error: ${e.message}")
        }
    }

    /**
     * Сбросить счётчик при открытии clips-экрана.
     */
    fun reset() {
        if (_count.value != 0) {
            _count.value = 0
            AppLog.d(TAG, "clips counter reset to 0")
        }
    }
}
