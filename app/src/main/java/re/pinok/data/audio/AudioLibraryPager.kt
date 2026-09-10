// File: data/audio/AudioLibraryPager.kt
package re.pinok.data.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog

/**
 * #AUDIO-BG-PAGER (волна 31): app-level фоновый пейджер библиотеки «Моя музыка».
 *
 * ПРОБЛЕМА (до волны 31): полный пейджинг жил ВНУТРИ композиции MusicScreen
 * (LaunchedEffect(selectedTab)) и был ограничен maxPreloadPages=10 (~500 треков,
 * Fix #173) — уход с экрана убивал загрузку, прогресс сбрасывался. «Добавить в
 * мою музыку» обновляло только сервер — кэш списка не знал о треке до перезапуска
 * приложения (#AUDIO-ADD-INSTANT).
 *
 * РЕШЕНИЕ: единый процесс-живущий синглтон — источник истины списка «Мои треки»
 * (замена приватного MusicTracksCache из MusicScreen.kt):
 *  - Старт: SovaApp.onCreate при валидной сессии, либо лениво при первом входе
 *    на MusicScreen (ensureStarted идемпотентен).
 *  - Последовательные страницы audioGetWithCount(count=50): offset растёт по
 *    СТРАНИЦАМ (серверный курсор), а не по размеру отфильтрованного списка —
 *    исключён класс стагнации «dedupe съел страницу → offset не сдвинулся →
 *    та же страница по кругу» (root-cause стопа ~1600, #AUDIO-PAGING-UNLIMITED).
 *  - Без искусственного капа страниц: идём, пока сервер отдаёт полную страницу
 *    (полная страница → есть ещё данные; частичная/пустая → конец). VK total
 *    используется для UI, но НЕ как стоп-условие (защита от заниженного count).
 *  - Fix #173 сохранён: пауза 1500мс между страницами, стоп при offline с
 *    авто-возобновлением по возврату сети (networkObserver.isOnlineFlow),
 *    одна in-flight страница (строгая последовательность цикла).
 *  - Retry при сбое: backoff 3с/6с/12с; 3 неудачи подряд → пауза 60с →
 *    продолжение (не бесконечный спам). kick() (скролл/«Повторить»)
 *    сокращает паузы.
 *  - Персист чекпоинта в SovaPrefs: my_music_paged_offset / my_music_total
 *    после каждой успешной страницы. При перезапуске процесса чекпоинт НЕ
 *    сбрасывается: пишется в лог resume и служит floor-целью. Список при этом
 *    восстанавливается последовательной догрузкой с offset=0 — треки живут
 *    только в памяти (URL протухают, JSON-персист тысяч треков вне скоупа),
 *    а «прыжок» курсора на сохранённый offset оставил бы дыру 50..checkpoint
 *    в списке и в очереди воспроизведения. Интерпретация «не скидываться, а
 *    продолжаться если замрёт»: пейджер на уровне приложения + персист
 *    прогресса + resume после смерти/сбоя без остановок раньше чекпоинта.
 *  - #AUDIO-ADD-INSTANT: addTrackFront/removeTrack/replaceTrack обновляют
 *    список немедленно из ЛЮБОГО места (MusicScreen, AudioPlayerScreen) —
 *    dedupe по ownerId_id, новая добавка в начале (порядок VK: новейшие
 *    первыми), серверный курсор сдвигается на ±1 чтобы не было пропуска
 *    одной дорожки на границе окна догрузки.
 *  - #AUDIO-QUEUE-PLAYLIST: каждая догруженная страница отдаётся в
 *    PlayerConnection — если живая очередь построена из «Моей музыки»,
 *    треки append'ятся в очередь без сброса воспроизведения.
 *
 * Логирование: AppLog.i по каждой странице — offset, получено N, fresh M,
 * total, hasMore, длительность (юзер может прислать логкат при повторе стопа).
 */
class AudioLibraryPager private constructor() {

    /** UI-состояние пейджера. MusicScreen подписывается на [state]. */
    data class PagerState(
        val tracks: List<Track> = emptyList(),
        /** VK response.count; -1 = неизвестен (web-fallback без count). */
        val total: Int = -1,
        /** Первая страница ещё не загружена (круговой индикатор списка). */
        val initialLoading: Boolean = false,
        /** Идёт загрузка очередной страницы (футер «Загрузка…»). */
        val fetchingPage: Boolean = false,
        /** Есть ли смысл ждать ещё страницы (честный «Это все треки»). */
        val hasMore: Boolean = true,
        /** Ошибка загрузки (показывается когда список пуст; null = нет). */
        val error: String? = null,
        /** Серверный курсор (сколько позиций библиотеки уже запрошено). */
        val serverOffset: Int = 0,
        /** Сколько страниц загружено за жизнь процесса (для итогового лога). */
        val pagesLoaded: Int = 0,
    )

    companion object {
        private const val TAG = "AudioLibraryPager"
        const val PAGE_SIZE = 50
        /** Fix #173: пауза между страницами — не мешать другим API-запросам. */
        private const val PAUSE_BETWEEN_PAGES_MS = 1500L
        private const val MAX_CONSECUTIVE_FAILS = 3
        private const val FAIL_BACKOFF_MS = 3000L
        /** 3 неудачи подряд → длинная пауза → продолжение (не спам). */
        private const val LONG_FAIL_PAUSE_MS = 60_000L
        /** Защита от патологического «та же страница по кругу». */
        private const val MAX_DUP_PAGES_IN_ROW = 20

        @Volatile
        private var instance: AudioLibraryPager? = null

        fun get(): AudioLibraryPager {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val again = instance
                if (again != null) return again
                val created = AudioLibraryPager()
                instance = created
                return created
            }
        }
    }

    private val _state = MutableStateFlow(PagerState())
    val state: StateFlow<PagerState> = _state.asStateFlow()

    private var loopJob: Job? = null

    /** Скролл/«Повторить» сокращают паузы и бэкоффы (без отмены страниц). */
    @Volatile
    private var kickRequested = false

    /** Guard идемпотентного старта (сбрасывается если токена ещё нет). */
    @Volatile
    private var started = false

    /** Серверный курсор — поле объекта (а не локальная переменная цикла),
     *  чтобы add/delete могли выравнивать его на ±1 (#AUDIO-ADD-INSTANT). */
    @Volatile
    private var serverOffset = 0

    /** true после первой успешной страницы — до неё add/delete не двигают
     *  курсор (список пуст/частичен, выравнивание не имеет смысла). */
    @Volatile
    private var pagingStarted = false

    /**
     * Идемпотентный старт фонового цикла. Вызывается из SovaApp.onCreate
     * (валидная сессия) и лениво из MusicScreen. Без токена — no-op
     * (после входа следующий вызов стартует цикл).
     */
    fun ensureStarted() {
        if (started) {
            kickRequested = true
            return
        }
        synchronized(this) {
            if (started) {
                kickRequested = true
                return
            }
            val app = SovaApp.getOrNull()
            if (app == null) {
                AppLog.w(TAG, "ensureStarted: SovaApp ещё не создан — skip")
                return
            }
            if (!app.tokenStorage.hasValidToken()) {
                AppLog.i(TAG, "ensureStarted: нет валидного токена (auth flow) — старт отложен")
                return
            }
            started = true
            AppLog.i(TAG, "ensureStarted: запуск фонового пейджера «Моя музыка» (appScope)")
            loopJob = app.appScope.launch { pagingLoop() }
        }
    }

    /** UX-триггер (скролл-догрузка, «Повторить»): сокращает текущую паузу. */
    fun kick() {
        kickRequested = true
    }

    // ─── #AUDIO-ADD-INSTANT: мгновенные мутации списка из любого экрана ───

    /**
     * Успешный audioAddReliable → трек немедленно в начале списка «Моей
     * музыки» (порядок VK: новейшие первыми). Dedupe по ownerId_id. Работает
     * из MusicScreen и AudioPlayerScreen — общий holder.
     */
    fun addTrackFront(track: Track) {
        val key = trackKey(track)
        var inserted = false
        _state.update { s ->
            val already = s.tracks.any { trackKey(it) == key }
            if (already) {
                s
            } else {
                inserted = true
                s.copy(
                    tracks = listOf(track) + s.tracks,
                    total = if (s.total >= 0) s.total + 1 else s.total,
                )
            }
        }
        if (inserted) {
            // Добавление сдвигает серверные позиции библиотеки на +1 —
            // выравниваем курсор, чтобы окно догрузки не пере-запрашивало
            // лишний дубль (не влияет только в catalog-fallback режиме,
            // где offset>0 всё равно пуст).
            if (pagingStarted) serverOffset += 1
            AppLog.i(TAG, "#AUDIO-ADD-INSTANT: '${track.artist} — ${track.title}' вставлен в начало (list=${_state.value.tracks.size})")
        } else {
            AppLog.d(TAG, "#AUDIO-ADD-INSTANT: трек уже в списке — skip (${track.id})")
        }
    }

    /** Успешный audioDelete → трек немедленно удаляется из списка. */
    fun removeTrack(track: Track) {
        val key = trackKey(track)
        var removed = false
        _state.update { s ->
            val next = s.tracks.filter { trackKey(it) != key }
            if (next.size == s.tracks.size) {
                s
            } else {
                removed = true
                s.copy(
                    tracks = next,
                    total = if (s.total >= 0) (s.total - 1).coerceAtLeast(0) else s.total,
                )
            }
        }
        if (removed) {
            // Удаление сдвигает серверные позиции на −1 — возвращаем курсор
            // на границу, иначе следующая страница перепрыгнет одну дорожку.
            if (pagingStarted && serverOffset > 0) serverOffset -= 1
            AppLog.i(TAG, "#AUDIO-ADD-INSTANT: '${track.artist} — ${track.title}' удалён из списка (list=${_state.value.tracks.size})")
        }
    }

    /** audio.edit (Fix #362) → обновить поля трека на месте. */
    fun replaceTrack(updated: Track) {
        val key = trackKey(updated)
        _state.update { s ->
            s.copy(tracks = s.tracks.map { current ->
                if (trackKey(current) == key) updated else current
            })
        }
    }

    // ─── Фоновый цикл ─────────────────────────────────────────────────────

    private suspend fun pagingLoop() {
        val app = SovaApp.get()
        // Чекпоинт прошлого процесса: resume-лог + floor-цель hasMore
        // (защита от заниженного VK total: не останавливаться раньше
        // сохранённого прогресса, пока сервер отдаёт полные страницы).
        var checkpointOffset = 0
        var checkpointTotal = 0
        try {
            val snap = app.prefs.data.first()
            checkpointOffset = snap.myMusicPagedOffset
            checkpointTotal = snap.myMusicTotal
        } catch (e: Exception) {
            AppLog.w(TAG, "checkpoint read failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        if (checkpointOffset > 0) {
            AppLog.i(TAG, "#AUDIO-BG-PAGER resume: чекпоинт прошлого процесса offset=$checkpointOffset total=$checkpointTotal — продолжаю с него (список восстанавливается последовательной догрузкой, без дыр)")
        }

        var consecutiveFails = 0
        var dupPagesInRow = 0
        _state.update { s ->
            s.copy(initialLoading = s.tracks.isEmpty(), error = null)
        }

        while (true) {
            // Стоп при offline + авто-возобновление при возврате сети.
            if (app.networkObserver.isOffline()) {
                AppLog.i(TAG, "#AUDIO-BG-PAGER: offline — пауза (offset=$serverOffset, list=${_state.value.tracks.size}); авто-возобновление при возврате сети")
                if (_state.value.tracks.isEmpty()) {
                    _state.update { s ->
                        s.copy(
                            error = "Нет сети. Подключитесь к интернету — музыка загрузится автоматически.",
                            initialLoading = false,
                        )
                    }
                }
                app.networkObserver.isOnlineFlow.first { it }
                AppLog.i(TAG, "#AUDIO-BG-PAGER: сеть вернулась — продолжаю")
                _state.update { s -> s.copy(error = null) }
            }

            _state.update { s -> s.copy(fetchingPage = true, error = null) }
            val pageStartedMs = System.currentTimeMillis()
            val offsetForThisPage = serverOffset
            try {
                val (total, raw) = withContext(Dispatchers.IO) {
                    app.apiClient.audioGetWithCount(count = PAGE_SIZE, offset = offsetForThisPage)
                }
                val tookMs = System.currentTimeMillis() - pageStartedMs
                val pageGot = raw.size
                // Auto-offline (Fix #367): 3 сетевых сбоя подряд — гейт API
                // вернул пусто без исключения. Это пауза, а не конец библиотеки.
                if (pageGot == 0 && app.apiClient.isAutoOfflineActive()) {
                    _state.update { s -> s.copy(fetchingPage = false) }
                    if (_state.value.tracks.isEmpty()) {
                        _state.update { s ->
                            s.copy(error = "Музыка остановлена: 3 сетевых сбоя подряд → авто-офлайн. Нажмите «Повторить».")
                        }
                    }
                    AppLog.w(TAG, "#AUDIO-PAGING page: offset=$offsetForThisPage auto-offline active — пауза 60с, потом продолжение")
                    consecutiveFails++
                    waitCancellable(LONG_FAIL_PAUSE_MS)
                    continue
                }
                // Сеть пропала между гейтом и запросом: пустая страница — пауза,
                // а не «конец библиотеки».
                if (pageGot == 0 && app.networkObserver.isOffline()) {
                    _state.update { s -> s.copy(fetchingPage = false) }
                    continue
                }

                // Фильтр (тот же, что был в MusicScreen) + dedupe по ownerId_id.
                val existingKeys = HashSet<String>()
                for (t in _state.value.tracks) existingKeys.add(trackKey(t))
                val fresh = ArrayList<Track>()
                for (t in raw) {
                    if (t.id <= 0L || t.ownerId == 0L) continue
                    val url = t.url
                    if (url == null || url.isBlank()) continue
                    val key = trackKey(t)
                    if (existingKeys.contains(key)) continue
                    existingKeys.add(key)
                    fresh.add(t)
                }

                // hasMore: полная страница → сервер отдаёт данные → идём дальше.
                // Частичная/пустая страница → конец библиотеки. total — только
                // для UI (VK может занижать count — не доверяем как стоп-условию).
                val fullPage = pageGot >= PAGE_SIZE
                val hasMore = fullPage
                val knownTotal = if (total > 0) total else _state.value.total
                serverOffset = offsetForThisPage + PAGE_SIZE
                pagingStarted = true
                val pagesLoadedNow = _state.value.pagesLoaded + 1
                _state.update { s ->
                    s.copy(
                        tracks = s.tracks + fresh,
                        total = knownTotal,
                        hasMore = hasMore,
                        fetchingPage = false,
                        initialLoading = false,
                        error = null,
                        serverOffset = serverOffset,
                        pagesLoaded = pagesLoadedNow,
                    )
                }
                AppLog.i(TAG, "#AUDIO-PAGING page: offset=$offsetForThisPage got=$pageGot fresh=${fresh.size} list=${_state.value.tracks.size} total=$knownTotal hasMore=$hasMore took=${tookMs}ms pages=$pagesLoadedNow checkpoint=$checkpointOffset")
                consecutiveFails = 0
                dupPagesInRow = if (fresh.isEmpty()) dupPagesInRow + 1 else 0
                persistCheckpoint(serverOffset, knownTotal)
                if (fresh.isNotEmpty()) {
                    // #AUDIO-QUEUE-PLAYLIST: живая очередь из «Моей музыки» —
                    // append без сброса воспроизведения.
                    PlayerConnection.onMyMusicPageLoaded(_state.value.tracks)
                }
                if (!hasMore) {
                    AppLog.i(TAG, "#AUDIO-PAGING done: list=${_state.value.tracks.size} из total=$knownTotal, страниц=$pagesLoadedNow, чекпоинт=$serverOffset (resume был offset=$checkpointOffset, resumeTotal=$checkpointTotal)")
                    break
                }
                if (dupPagesInRow >= MAX_DUP_PAGES_IN_ROW) {
                    AppLog.w(TAG, "#AUDIO-PAGING stop: $dupPagesInRow страниц подряд без новых треков (offset=$serverOffset) — библиотека уже в списке либо total рассинхронизирован")
                    _state.update { s -> s.copy(hasMore = false) }
                    break
                }
                waitCancellable(PAUSE_BETWEEN_PAGES_MS)
            } catch (e: CancellationException) {
                // Прецедент Fix #151/#281: отмену (смерть процесса/скоупа)
                // пробрасываем — не глотаем.
                throw e
            } catch (e: Exception) {
                val tookMs = System.currentTimeMillis() - pageStartedMs
                consecutiveFails++
                val msg = e.message
                val failText = if (msg != null) msg else e.javaClass.simpleName
                val waitMs = if (consecutiveFails >= MAX_CONSECUTIVE_FAILS) {
                    LONG_FAIL_PAUSE_MS
                } else {
                    FAIL_BACKOFF_MS * consecutiveFails
                }
                AppLog.w(TAG, "#AUDIO-PAGING page failed: offset=$offsetForThisPage fails=$consecutiveFails took=${tookMs}ms err=$failText — retry через ${waitMs}мс")
                _state.update { s ->
                    s.copy(
                        fetchingPage = false,
                        initialLoading = false,
                        error = if (s.tracks.isEmpty()) "Ошибка загрузки: $failText" else s.error,
                    )
                }
                waitCancellable(waitMs)
            }
        }
    }

    /** Пауза с нарезкой по 200мс: kickRequested сокращает ожидание. */
    private suspend fun waitCancellable(totalMs: Long) {
        var waited = 0L
        while (waited < totalMs) {
            if (kickRequested) {
                if (waited > 0) {
                    AppLog.d(TAG, "kick: пауза сокращена (${waited}/${totalMs}мс)")
                }
                kickRequested = false
                return
            }
            delay(200)
            waited += 200
        }
        kickRequested = false
    }

    /** Персист чекпоинта в SovaPrefs (DataStore). Сбой логируется, не валит цикл. */
    private suspend fun persistCheckpoint(offset: Int, total: Int) {
        try {
            val app = SovaApp.get()
            app.prefs.setMyMusicPagedOffset(offset)
            if (total > 0) app.prefs.setMyMusicTotal(total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "checkpoint persist failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun trackKey(t: Track): String = "${t.ownerId}_${t.id}"
}
