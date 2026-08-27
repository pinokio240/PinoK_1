// File: media/PlayerConnection.kt
package re.pinok.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import re.pinok.data.model.EqualizerPreset
import re.pinok.data.model.PlayerState
import re.pinok.data.model.Track
import re.pinok.service.PlayerService
import re.pinok.util.AppLog

/**
 * Singleton-обёртка над [MediaController] из Media3.
 *
 * Аналог обвязки MediaButtonReceiver + MediaSessionHelper из SOVA V RE,
 * но на современном Media3 API.
 *
 * Зона ответственности:
 *  — Коннектится к [PlayerService] через [SessionToken] один раз (lazy).
 *  — Держит актуальный [PlayerState] как StateFlow. UI (MusicScreen, MiniPlayer)
 *    читает его напрямую.
 *  — Проксирует команды UI → MediaController → PlayerService:
 *      [playTrackList] — задать плейлист и начать воспроизведение указанного индекса.
 *      [togglePlayPause], [next], [prev], [seekTo].
 *
 * UI НЕ должен трогать MediaController напрямую — все команды через этот класс.
 *
 * Потокобезопасность: все публичные методы можно вызывать с главного потока.
 */
object PlayerConnection {

    private const val TAG = "PlayerConnection"
    private const val PROGRESS_TICK_MS = 500L

    @Volatile
    private var initialized = false

    /**
     * #AUTH-WEBVIEW-STARVATION-V2: публичный read-only доступ к флагу initialized.
     * SovaApp.onCreate skip'ит init в auth flow (нет токена), MainActivity.onResume
     * проверяет isInitialized() после успешного auth и лениво вызывает init.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Fix #169: timestamp последнего вызова [init] / [reconnectController].
     * Защита от спама — если UI вызывает notifyResumed несколько раз подряд
     * (onResume + восстановление сети), не запускаем параллельные реконнекты.
     * Окно: 2 секунды — достаточно для типичных bursts.
     */
    @Volatile
    private var lastReconnectTs: Long = 0L

    /**
     * Fix #169: app context сохраняем при init() для возможности переподключения
     * controller'а из notifyResumed() (когда MainActivity.onResume ловит stale
     * controller после Doze и нужно пересоздать MediaController).
     */
    @Volatile
    private var appContext: Context? = null

    /**
     * Fix #169: timestamp последнего onResume — для логирования таймингов
     * «разблокировка → controller готов → состояние плеера опубликовано».
     * Пользователь сможет по logcat видеть сколько занимает каждый этап.
     */
    @Volatile
    private var lastResumeTs: Long = 0L

    /**
     * Fix #172: флаг «switch сети произошёл пока controller был null».
     *
     * Сценарий: телефон заблокирован (Doze убил PlayerService → controller
     * стал null) → Wi-Fi подключился → onNetworkChanged(true) вызывается,
     * но controller ?: return → prepare() НЕ вызывается → команда потеряна.
     * Потом пользователь разблокирует → notifyResumed() → reconnectController()
     * строит новый controller, но НЕ знает о switch → service's ExoPlayer
     * остаётся висеть на мёртвом mobile-интерфейсе.
     *
     * Решение: onNetworkChanged при null controller ставит флаг. Когда
     * connectController достраивает новый controller — проверяет флаг и
     * вызывает prepare() чтобы service перестроил MediaSource на новом
     * (Wi-Fi) интерфейсе с сохранением позиции.
     */
    @Volatile
    private var pendingNetworkReprepare: Boolean = false

    /**
     * Fix #110: gate для auto-cache аудио. Обновляется из [re.pinok.SovaApp]
     * подпиской на prefs.autoCacheAudio. Когда false — enqueueDownload(silent=true)
     * пропускается (пользователь отключил «Авто Кеш Аудио» в настройках).
     * Ручные загрузки (через UI кнопку) НЕ гейтятся — только auto-cache.
     *
     * Fix #AUTOCACHE-AUDIO-OFF (2026-08-05): initial = false (синхронно с
     * SovaPrefs default). SovaApp.onCreate применит snap.autoCacheAudio в
     * течение ~50-200мс, но до этого race-окно НЕ должно качать аудио.
     */
    @Volatile
    var autoCacheAudio: Boolean = false
        internal set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null

    /** Снапшот текущего трека (для сопоставления MediaItem ↔ Track). */
    @Volatile
    private var currentTrack: Track? = null

    /** Снапшот плейлиста (для prev/next по доменной модели). */
    @Volatile
    private var playlist: List<Track> = emptyList()

    /**
     * Fix #135: публичный доступ к текущему треку для PlayerService
     * (custom command "Скачать" на lock screen). PlayerService не имеет
     * доступа к playlist/currentTrack напрямую — вызывает этот метод.
     * Возвращает snapshot (val), потокобезопасно через @Volatile.
     */
    fun currentTrackForDownload(): Track? = currentTrack

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null
    private var consecutivePlayerErrors = 0
    /** REPEAT_MODE_TWO: how many times the current track has played (0=not yet, 1=once, 2=done). */
    @Volatile
    private var repeatTwoCount = 0
    // Fix #233 (P0): throttle локального fallback при onPlayerError —
    // не ретраиваем тот же трек из кэша чаще раза в 10с (защита от цикла
    // если локальный кэш тоже повреждён).
    @Volatile
    private var lastErrorTrackId: Long = -1L
    @Volatile
    private var lastErrorTrackTimeMs: Long = 0L

    // Fix #51-C: pendingRestoreTrackId УБРАН — больше не нужен.
    // Раньше помечал трек для restore позиции после STATE_READY, но это
    // восстанавливало позицию при next/prev (явный переход) → баг "трек
    // начинается с середины". Сохранение позиции в onMediaItemTransition
    // осталось (для потенциального будущего cold-start restore).
    // #39 C2: timestamp последнего save позиции (debounce 5с).
    @Volatile
    private var lastPositionSaveTs: Long = 0L

    /**
     * Fix #178: STATE_BUFFERING watchdog — автоматически reprepare'ит плеер
     * если он завис в BUFFERING дольше 30 секунд.
     *
     * КОНТЕКСТ БАГА: При switch mobile↔Wi-Fi NetworkObserver.onAvailable
     * (default callback) должен сработать → onNetworkChanged(true, forceReprepare=true)
     * → ctrl.prepare() + seekTo(pos). Но если switch не задетектирован (edge case:
     * VPN, captive portal, DHCP renewal без switch интерфейса) или if onAvailable
     * не вызвался — ExoPlayer остаётся в STATE_BUFFERING бесконечно ( HLS-стрим
     * на мёртвом сокете, OkHttp retry exhausted).
     *
     * ExoPlayer issue #911 (2015): "if connection has changed (3G changed to WiFi)
     * player freezes on STATE_BUFFERING and do not anything until player is
     * reinitialized. Call pause and than play, in this case, not work." —
     * подтверждает симптом. Автор issue рекомендовал 30s таймер → реинициализация.
     *
     * РЕШЕНИЕ: при входе в STATE_BUFFERING запускаем 30s watchdog. Если через 30s
     * плеер всё ещё BUFFERING (проверяем controller.playbackState) — делаем
     * ctrl.prepare() + seekTo(pos) аналогично onNetworkChanged.
     * Отменяем watchdog при любом другом state (READY/IDLE/ENDED).
     */
    private var bufferingWatchdogJob: Job? = null

    /**
     * Fix #174: Coroutine job для отслеживания завершения загрузки ТЕКУЩЕГО
     * трека → запуск precacheNext когда CURRENT уже скачан.
     *
     * КОНТЕКСТ БАГА: Раньше в onMediaItemTransition вызывался precacheNextTrack()
     * сразу при смене трека → начинал качать СЛЕДУЮЩИЙ трек. Затем в
     * onPlaybackStateChanged(STATE_READY) качать ТЕКУЩИЙ — но sequential mode
     * (Fix #170) блокировал (hasActiveDownload==true). В итоге кэшировался
     * следующий трек, а текущий — никогда (см. лог 21:22:44.651-.657:
     * onMediaItemTransition → precacheNext START track NEXT, потом READY →
     * auto-cache[READY] SKIP track CURRENT: another download active).
     *
     * Решение: precacheNext вызывается только когда CURRENT трек уже скачан
     * (или не нужен для кэша — например уже скачан ранее). Этот job
     * перезапускается при каждой смене currentTrack.
     */
    private var precacheAfterCurrentJob: Job? = null

    /**
     * Lazy-инициализация. Безопасно вызывать много раз — реальная работа
     * выполняется один раз. Должна вызываться из SovaApp.onCreate.
     *
     * #AUTH-WEBVIEW-STARVATION-V2: SovaApp.onCreate skip'ит init в auth flow
     * (нет токена → нет init → PlayerService НЕ блокирует main thread →
     * Chromium может поднять рендерер для m.vk.ru). После успешного auth
     * MainActivity.onResume вызывает init лениво через [isInitialized] check.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            appContext = context.applicationContext
            // #INIT-RECONNECT-GUARD: отмечаем lastReconnectTs чтобы первый
            // notifyResumed() сразу после init НЕ запускал второй connectController
            // параллельно (race → 2 MediaController future в полёте → leak
            // первого). Без этого guard init+notifyResumed за <2с создавали
            // дубликат контроллера (в логе: "MediaController подключён" дважды
            // за 9мс, "Controller (re)connected 913ms/921ms после onResume").
            lastReconnectTs = System.currentTimeMillis()
            AppLog.i(TAG, "init: подключаемся к PlayerService")
            connectController(context.applicationContext)
        }
    }

    /**
     * Fix #169: создаёт MediaController и подключает к PlayerService.
     * Вынесено из [init] чтобы переиспользовать в [reconnectController]
     * (когда старый controller устарел после убийства сервиса системой).
     *
     * Не синхронизировано — вызывается только из [init] (под synchronized)
     * или из [reconnectController] (под guard lastReconnectTs).
     */
    private fun connectController(ctx: Context) {
        val sessionToken = SessionToken(
            ctx,
            ComponentName(ctx, PlayerService::class.java)
        )
        val future = MediaController.Builder(ctx, sessionToken).buildAsync()
        future.addListener(
            {
                val ctrl = future.get()
                if (ctrl == null) {
                    AppLog.e(TAG, "MediaController build failed")
                    return@addListener
                }
                controller = ctrl
                ctrl.addListener(ControllerListener())
                AppLog.i(TAG, "MediaController подключён")
                startProgressTicker()
                // Fix #169: если controller (пере)создан в ответ на onResume —
                // логируем тайминг от lastResumeTs для диагностики «подвисания».
                if (lastResumeTs > 0) {
                    val dt = System.currentTimeMillis() - lastResumeTs
                    AppLog.i(TAG, "Controller (re)connected ${dt}ms после onResume")
                    // Публикуем состояние сразу — UI увидит трек/позицию без
                    // ожидания следующего progress ticker'а (500мс).
                    publishStateImmediate()
                }
                // Fix #172: если switch сети произошёл пока controller был null
                // (Doze убил service, Wi-Fi подключился при locked screen) —
                // onNetworkChanged поставил pendingNetworkReprepare. Сейчас
                // controller готов → выполняем отложенный prepare чтобы service's
                // ExoPlayer перестроил MediaSource на новом интерфейсе.
                if (pendingNetworkReprepare) {
                    pendingNetworkReprepare = false
                    val track = currentTrack
                    if (track != null && ctrl.mediaItemCount > 0) {
                        try {
                            val pos = ctrl.currentPosition.coerceAtLeast(0L)
                            ctrl.prepare()
                            if (pos > 1000L) {
                                try { ctrl.seekTo(pos) } catch (_: Exception) {}
                            }
                            AppLog.i(TAG, "connectController: pending network reprepare applied (track=#${track.id}, pos=${pos}ms)")
                        } catch (e: Exception) {
                            AppLog.w(TAG, "connectController: pending reprepare failed: ${e.message}")
                        }
                    } else {
                        AppLog.d(TAG, "connectController: pending reprepare skipped (no track / empty playlist — service was killed, nothing to reprepare)")
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Fix #169: переподключение MediaController после того как система убила
     * PlayerService (Doze > 1 мин, low memory). Без этого controller остаётся
     * stale, withController ждёт 3×300мс = 900мс и логирует «controller так и
     * не подключился» — UI команда теряется, плеер «не отвечает» при разблокировке.
     *
     * Guard: [lastReconnectTs] — не чаще чем раз в 2 секунды (burst protection).
     * Вызывается из [notifyResumed] если controller == null.
     *
     * Synchronized: [lastReconnectTs] @Volatile но check-then-act не атомарна.
     * notifyResumed (main thread) и withController (main thread scope) могут
     * одновременно пройти guard и создать 2 MediaController → leak первого.
     * synchronized(this) гарантирует что только один reconnect выполняется.
     */
    private fun reconnectController() {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (now - lastReconnectTs < 2000L) {
                AppLog.d(TAG, "reconnectController: SKIP (recent reconnect ${now - lastReconnectTs}ms ago)")
                return
            }
            val ctx = appContext
            if (ctx == null) {
                AppLog.w(TAG, "reconnectController: SKIP (appContext null — init never called)")
                return
            }
            lastReconnectTs = now
            AppLog.i(TAG, "reconnectController: пересоздаём MediaController (старый устарел после Doze)")
            // Старый controller мог остаться non-null но мёртвым — освобождаем.
            try { controller?.release() } catch (_: Exception) {}
            controller = null
            connectController(ctx)
        }
    }

    /**
     * Fix #169: вызывается из MainActivity.onResume() для мгновенного wake-up
     * плеера после разблокировки экрана. Без этого UI ждёт пока network callback
     * (onAvailable) сработает — а он может задерживаться на 1-3с в зависимости
     * от Doze duration и radio state.
     *
     * Действия:
     *  1. Запоминаем timestamp для логирования таймингов resume.
     *  2. Если controller живой — publishStateImmediate() обновляет UI сразу
     *     (без ожидания progress ticker 500мс). Это убирает «приложение
     *     подвисает» восприятие — UI видит актуальный трек/позицию мгновенно.
     *  3. Если controller null (сервис убит) — reconnectController() запускает
     *     переподключение в фоне. UI не блокируется.
     *
     * Безопасен для вызова когда плеер не инициализирован (no-op).
     */
    fun notifyResumed() {
        lastResumeTs = System.currentTimeMillis()
        val ctrl = controller
        if (ctrl != null) {
            // Controller жив — публикуем состояние мгновенно.
            publishStateImmediate()
            AppLog.d(TAG, "notifyResumed: controller жив, состояние опубликовано (0мс)")
        } else if (initialized) {
            // init уже был, но controller null → сервис убит системой → реконнект.
            AppLog.w(TAG, "notifyResumed: controller null (сервис убит?) — запуск reconnectController")
            reconnectController()
        } else {
            AppLog.d(TAG, "notifyResumed: init ещё не было — skip")
        }
    }

    // ─── Публичный API ─────────────────────────────────────────────

    /**
     * Задать плейлист и начать воспроизведение с заданного индекса.
     *
     * Fix #59: треки без источника (нет локального файла И нет URL) фильтруются —
     * иначе они попадали в плейлист с URI "about:blank", и ExoPlayer падал с
     * ERROR_CODE_INPUT_INVALID при попытке их воспроизведения. StartIndex
     * ремапится на отфильтрованный плейлист по ID выбранного трека.
     */
    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            AppLog.w(TAG, "playTrackList: пустой плейлист")
            return
        }
        // Fix #164: убраны File.exists() вызовы с main thread.
        // Раньше: tracks.map { getLocalFile(t.id) } = N вызовов File.exists() +
        // isValidAudioFile (читает 4 байта) на main thread. Для 824 треков =
        // 824 stat-сисвызова → ANR → ForegroundServiceDidNotStartInTimeException.
        // Теперь: используем in-memory isDownloaded() (StateFlow lookup, O(1))
        // для фильтрации, и getLocalFile() ТОЛЬКО для скачанных треков
        // (обычно 57 из 824). Ускорение в ~14 раз.
        val localCache: Map<Long, java.io.File?> = buildMap {
            tracks.forEach { t ->
                if (TrackDownloadManager.isDownloaded(t.id)) {
                    put(t.id, TrackDownloadManager.getLocalFile(t.id))
                }
            }
        }
        val localCount = localCache.count { it.value != null }
        val onlineCount = tracks.count { !it.url.isNullOrBlank() && !localCache.containsKey(it.id) }
        // Fix #165: логируем онлайн-режим — определяет ONLINE vs OFFLINE playback.
        val isOnline = re.pinok.SovaApp.getOrNull()?.networkObserver?.isOnline() ?: false
        AppLog.i(TAG, "playTrackList: total=${tracks.size} local=$localCount online=$onlineCount startIdx=$startIndex isOnline=$isOnline (Fix #165: hybrid mode)")
        // Логирование каждого трека вынесено на Dispatchers.Default (не блокирует UI).
        if (tracks.size <= 50) {
            tracks.forEachIndexed { i, t ->
                val local = localCache[t.id]
                if (local != null) {
                    AppLog.d(TAG, "  [$i] track=#${t.id} LOCAL ext=${local.extension} size=${local.length()}B — ${t.artist} - ${t.title}")
                } else if (!t.url.isNullOrBlank()) {
                    AppLog.d(TAG, "  [$i] track=#${t.id} ONLINE url=${t.url.orEmpty().take(60)} — ${t.artist} - ${t.title}")
                } else {
                    AppLog.w(TAG, "  [$i] track=#${t.id} SKIP (no url, no local) — ${t.artist} - ${t.title}")
                }
            }
        } else {
            AppLog.i(TAG, "  (skipped per-track log, ${tracks.size} items > 50)")
        }
        val startTrackId = tracks.getOrNull(startIndex.coerceIn(0, tracks.lastIndex))?.id
        val playable = tracks.filter { t ->
            !t.url.isNullOrBlank() || localCache[t.id] != null
        }
        if (playable.isEmpty()) {
            AppLog.w(TAG, "playTrackList: нет воспроизводимых треков (все без URL)")
            _playerState.value = _playerState.value.copy(
                error = "Нет воспроизводимых треков — у всех отсутствует URL",
            )
            return
        }
        playlist = playable
        val safeIndex = if (startTrackId != null) {
            playable.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        } else {
            startIndex.coerceIn(0, playable.lastIndex)
        }
        // Fix #164: передаём кэшированный localFile в toMediaItem чтобы избежать
        // повторных File.exists() вызовов (по одному на трек).
        val mediaItems = playable.map { it.toMediaItem(localCache[it.id]) }
        withController { ctrl ->
            ctrl.setMediaItems(mediaItems, safeIndex, 0L)
            // Fix #51-C: убран restore сохранённой позиции в playTrackList.
            // Раньше здесь был seekTo(safeIndex, savedPos) если savedPos > 3000ms.
            // Это вызывало баг: трек A играет 30сек → пользователь нажимает next →
            // onMediaItemTransition сохраняет позицию A (30сек) в PlaybackPositionStore →
            // пользователь кликает на A в списке → playTrackList([A,...], 0) →
            // restored 30сек → трек A начинается с середины вместо начала.
            // playTrackList = ВСЕГДА явный выбор пользователя (клик в списке,
            // аудио-вложение, плейлист) → должен стартовать с начала.
            // Все 13 UI-вызовов playTrackList (AudioAttachmentList, MusicScreen,
            // OfflineManager, OfflineAudioPlayer, PostDetail, PlaylistAttachmentCard)
            // = явный выбор. Resume после паузы идёт через togglePlayPause/play(),
            // не через playTrackList. Cold-start resume не реализован через playTrackList.
            val startTrack = playable[safeIndex]
            ctrl.prepare()
            ctrl.playWhenReady = true
            currentTrack = startTrack
            publishStateImmediate()
            AppLog.i(TAG, "playTrackList: ${playable.size}/${tracks.size} треков, start=$safeIndex")

            // Fix #177: кэшируем ТЕКУЩИЙ трек (startTrack), а НЕ firstTrack.
            //
            // КОНТЕКСТ БАГА: Раньше здесь кэшировался firstTrack (index 0) когда
            // safeIndex > 0. Идея была «onPlaybackStateChanged(READY) кэширует только
            // воспроизводимый трек, поэтому firstTrack никогда не попадёт в кеш».
            // Но Fix #170 (SEQUENTIAL mode) + Fix #174 (schedulePrecacheAfterCurrent)
            // сделали это логику ВРЕДНОЙ:
            //   1. playTrackList → enqueueDownload(firstTrack) — sequential mode занят
            //   2. onPlaybackStateChanged(READY) для startTrack → auto-cache[READY]
            //      SKIP: "another download active (sequential mode)"
            //   3. startTrack (ТЕКУЩИЙ играющий) НИКОГДА не кэшируется
            //   4. schedulePrecacheAfterCurrent ждёт COMPLETED для startTrack, но
            //      startTrack не качается → precacheNext никогда не запустится
            // Пользователь видел в кеше firstTrack (следующий по порядку), а текущий
            // трек — нет. Это ровно баг «в кеш загружается следующий трек, а не тот
            // что играет».
            //
            // РЕШЕНИЕ: кэшируем startTrack (currentTrack) сразу при playTrackList,
            // не дожидаясь STATE_READY. Тогда:
            //   - startTrack качается первым (правильный приоритет)
            //   - auto-cache[READY] SKIP'нет startTrack (inProgress=true) — ОК
            //   - schedulePrecacheAfterCurrent ждёт COMPLETED → precacheNext
            //   - firstTrack (index 0) кэшируется через precacheNext когда очередь
            //     дойдёт до него (repeat mode ALL) — или не кэшируется вообще
            //     (repeat mode OFF) — это нормально, пользователь выбрал safeIndex.
            //
            // Fix #110: gate через autoCacheAudio pref (default false, #AUTOCACHE-AUDIO-OFF).
            // Fix #140: diagnostic logging для всех веток решения (START/SKIP + reason).
            if (autoCacheAudio) {
                val cur = startTrack
                if (!cur.url.isNullOrBlank()) {
                    val url = cur.url
                    val isHls = url.contains("m3u8", ignoreCase = true) || url.contains("vkuseraudio.net")
                    val isCached = TrackDownloadManager.isDownloaded(cur.id)
                    val inProgress = TrackDownloadManager.getDownloadState(cur.id)?.isInProgress == true
                    // Fix #280: hasActiveDownload() gate убран — TrackDownloadManager
                    // теперь имеет настоящую FIFO-очередь (Fix #265): enqueueDownload
                    // НЕ запускает параллельный download, а кладёт трек в очередь.
                    // Раньше этот gate SKIPал текущий (играющий) трек, если качался
                    // другой → текущий НИКОГДА не кэшировался (см. баг-контекст выше).
                    when {
                        isCached -> AppLog.d(TAG, "auto-cache[playList] SKIP current #${cur.id}: already cached")
                        inProgress -> AppLog.d(TAG, "auto-cache[playList] SKIP current #${cur.id}: download in progress")
                        !isHls -> AppLog.w(TAG, "auto-cache[playList] SKIP current #${cur.id}: not HLS (${url.take(60)})")
                        else -> {
                            AppLog.i(TAG, "auto-cache[playList] START current #${cur.id} (HLS, silent)")
                            TrackDownloadManager.enqueueDownload(cur, silent = true)
                        }
                    }
                }
            }
        }
    }

    /** Play/Pause toggle — мгновенный, без задержек. */
    fun togglePlayPause() {
        withController { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    /**
     * Поставить аудиоплеер на паузу (например, при открытии видео).
     * Возвращает true, если плеер играл и был поставлен на паузу.
     */
    fun pauseIfPlaying(): Boolean {
        var wasPlaying = false
        withController { ctrl ->
            if (ctrl.isPlaying) {
                wasPlaying = true
                ctrl.pause()
            }
        }
        return wasPlaying
    }

    /**
     * Возобновить воспроизведение, если оно было остановлено pauseIfPlaying().
     * Вызывается при закрытии видео.
     */
    fun resumeIfWasPlaying() {
        withController { ctrl ->
            if (!ctrl.isPlaying && ctrl.mediaItemCount > 0) {
                ctrl.play()
            }
        }
    }

    /** Следующий трек. */
    fun next() {
        withController { ctrl ->
            if (ctrl.mediaItemCount == 0) return@withController
            ctrl.seekToNextMediaItem()
            ctrl.playWhenReady = true
        }
    }

    /** Предыдущий трек. Если прошли >3сек — перематывает в начало. */
    fun prev() {
        withController { ctrl ->
            if (ctrl.mediaItemCount == 0) return@withController
            if (ctrl.currentPosition > 3_000L) {
                ctrl.seekTo(0L)
            } else {
                ctrl.seekToPreviousMediaItem()
                ctrl.playWhenReady = true
            }
        }
    }

    /** Перемотка к указанной позиции в миллисекундах. */
    fun seekTo(positionMs: Long) {
        withController { ctrl -> ctrl.seekTo(positionMs) }
    }

    /** Fix #62: включить/выключить перемешивание плейлиста. */
    fun setShuffleModeEnabled(enabled: Boolean) {
        withController { ctrl ->
            ctrl.shuffleModeEnabled = enabled
            _playerState.value = _playerState.value.copy(shuffleModeEnabled = enabled)
            AppLog.i(TAG, "shuffleMode → $enabled")
        }
    }

    /**
     * Циклическое переключение режима повтора: OFF → ALL → ONE → TWO → OFF.
     * TWO = повтор одного трека 2 раза, затем переход к следующему.
     * Внутри ExoPlayer остаётся в REPEAT_MODE_OFF — перехват делаем в
     * onMediaItemTransition(AUTO) чтобы после 2-го проигрывания advance случился.
     */
    fun cycleRepeatMode() {
        withController { ctrl ->
            val current = _playerState.value.repeatMode
            val next = when (current) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> PlayerState.REPEAT_MODE_TWO
                else -> Player.REPEAT_MODE_OFF  // TWO → OFF
            }
            repeatTwoCount = 0
            // ExoPlayer не знает про TWO — внутри держим OFF, логику перехвата
            // делаем сами в onMediaItemTransition.
            val exoMode = if (next == PlayerState.REPEAT_MODE_TWO) Player.REPEAT_MODE_OFF else next
            ctrl.repeatMode = exoMode
            _playerState.value = _playerState.value.copy(repeatMode = next)
            AppLog.i(TAG, "repeatMode → $next (exo=$exoMode, count reset)")
        }
    }

    /** Установить скорость воспроизведения (0.25x – 3.0x). */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        withController { ctrl ->
            val params = androidx.media3.common.PlaybackParameters(clamped)
            ctrl.playbackParameters = params
            _playerState.value = _playerState.value.copy(speed = clamped)
            AppLog.i(TAG, "speed → $clamped")
        }
    }

    // ─── Эквалайзер (прокси → EqualizerHelper) ───────────────────

    /** Применить пресет эквалайзера. */
    fun setEqualizerPreset(preset: EqualizerPreset) {
        EqualizerHelper.applyPreset(preset)
    }

    /** Включить/выключить эквалайзер. */
    fun setEqualizerEnabled(enabled: Boolean) {
        EqualizerHelper.setEnabled(enabled)
    }

    /** Включён ли эквалайзер. */
    fun isEqualizerEnabled(): Boolean = EqualizerHelper.isEnabled()

    /** Текущие уровни полос в миллибелах. */
    fun getEqualizerBands(): List<Short> = EqualizerHelper.getBands()

    /** Установить усиление отдельной полосы. */
    fun setEqualizerBand(bandIndex: Int, gainMB: Short) {
        EqualizerHelper.setBand(bandIndex, gainMB)
    }

    /** Fix #62: перемешать текущий плейлист и начать воспроизведение с первого трека. */
    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        // Перемешиваем плейлист и обновляем внутреннее состояние.
        playlist = shuffled
        // Fix #164: передаём кэшированный localFile в toMediaItem (in-memory filter).
        val localCache: Map<Long, java.io.File?> = buildMap {
            shuffled.forEach { t ->
                if (TrackDownloadManager.isDownloaded(t.id)) {
                    put(t.id, TrackDownloadManager.getLocalFile(t.id))
                }
            }
        }
        val mediaItems = shuffled.map { it.toMediaItem(localCache[it.id]) }
        withController { ctrl ->
            ctrl.setMediaItems(mediaItems, 0, 0L)
            ctrl.shuffleModeEnabled = true
            ctrl.prepare()
            ctrl.playWhenReady = true
            currentTrack = shuffled.firstOrNull()
            _playerState.value = _playerState.value.copy(shuffleModeEnabled = true)
            publishStateImmediate()
            AppLog.i(TAG, "shuffleAll: ${shuffled.size} треков перемешано")
        }
    }

    /**
     * Запустить воспроизведение трека по его ID в текущем плейлисте.
     * Если плейлист ещё не задан — вызывающая сторона должна передать fallback.
     */
    fun playTrackById(trackId: Long, fallbackTracks: List<Track>) {
        if (playlist.isEmpty()) {
            val idx = fallbackTracks.indexOfFirst { it.id == trackId }
            playTrackList(fallbackTracks, idx.coerceAtLeast(0))
            return
        }
        val idx = playlist.indexOfFirst { it.id == trackId }
        if (idx < 0) {
            // Fix #162: трека нет в текущем плейлисте. Раньше просто return —
            // тап ничего не делал. Если трек есть в fallbackTracks — переключаемся
            // на новый плейлист через playTrackList (как при playlist.isEmpty()).
            val fbIdx = fallbackTracks.indexOfFirst { it.id == trackId }
            if (fbIdx >= 0 && fallbackTracks.isNotEmpty()) {
                AppLog.i(TAG, "playTrackById: track #$trackId not in current playlist — switching to fallback list")
                playTrackList(fallbackTracks, fbIdx)
            } else {
                AppLog.w(TAG, "playTrackById: track #$trackId not in playlist nor fallback — doing nothing")
            }
            return
        }
        withController { ctrl ->
            ctrl.seekToDefaultPosition(idx)
            ctrl.playWhenReady = true
        }
    }

    /**
     * Освобождение (например, при выходе из приложения). Обычно не требуется —
     * service продолжит жить сам.
     */
    fun release() {
        scope.cancel()
        progressJob?.cancel()
        progressJob = null
        // Fix #174: отменяем ожидающий precache-wait job.
        precacheAfterCurrentJob?.cancel()
        precacheAfterCurrentJob = null
        // Fix #178: отменяем BUFFERING watchdog.
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        controller?.release()
        controller = null
        initialized = false
        // Fix #169: сбрасываем поля реконнекта — после release() notifyResumed()
        // должен быть no-op (init ещё не было).
        appContext = null
        lastReconnectTs = 0L
        lastResumeTs = 0L
        // Fix #172: сбрасываем pending-флаг — после release() новый controller
        // не будет построен, флаг не должен срабатывать при повторном init.
        pendingNetworkReprepare = false
    }

    /**
     * Fix #45: Уведомление плеера о смене сети (WiFi↔Mobile).
     *
     * При переключении сети текущий HLS-стрим может зависнуть на dead connection
     * (TCP keep-alive не всегда успевает детектить обрыв за разумное время).
     *
     * - online=true: вызываем [MediaController.prepare] — пересоздаёт MediaSource
     *   на новом интерфейсе, сохраняя позицию воспроизведения. Плеер на секунду
     *   буферизуется заново, затем продолжает с того же места.
     * - online=false: ничего не делаем — плеер доиграет буфер и остановится,
     *   при восстановлении сети online=true снова его перезапустит.
     *
     * Fix #171: параметр [forceReprepare] — отличает «восстановление после
     * offline» (flow: false→true) от «switch mobile↔Wi-Fi без потери связи»
     * (default-network-changed listener). Семантически поведение одинаковое
     * (online=true всегда делает prepare+seekTo), но в логе видно какой сценарий
     * сработал — удобно для отладки зависаний плеера.
     *
     * Вызывается из [re.pinok.SovaApp.registerGlobalNetworkWatcher].
     */
    fun onNetworkChanged(online: Boolean, forceReprepare: Boolean = false) {
        val ctrl = controller
        val track = currentTrack
        if (online && track != null) {
            if (ctrl == null) {
                // Fix #172: controller null (Doze убил service или идёт reconnect) —
                // prepare сейчас невозможен. Ставим флаг: когда connectController
                // достроит новый controller, он применит отложенный prepare.
                // Без этого switch сети «теряется» и service's ExoPlayer висит
                // на мёртвом интерфейсе после разблокировки.
                pendingNetworkReprepare = true
                val reason = if (forceReprepare) "default-network-switch" else "offline-restored"
                AppLog.i(TAG, "Network restored ($reason) but controller null — pendingNetworkReprepare=true (will apply on reconnect)")
                return
            }
            val reason = if (forceReprepare) "default-network-switch" else "offline-restored"
            AppLog.i(TAG, "Network restored ($reason) — re-preparing player for track #${track.id}")
            try {
                // Сохраняем позицию — prepare() сбрасывает в 0 без неё.
                val pos = ctrl.currentPosition
                ctrl.prepare()
                // Fix #169: убран фиксированный delay(300L) перед seekTo.
                // Раньше: scope.launch { delay(300L); seekTo(pos) } — 300мс
                // искусственной задержки на каждом восстановлении сети = при
                // разблокировке экрана пользователь слышал «тишину» 300мс прежде
                // чем трек возобновлялся. Это воспринималось как «подвисание».
                //
                // Теперь: seekTo вызывается сразу после prepare(). ExoPlayer
                // ставит seek в очередь — когда MediaSource перестроится,
                // позиция уже будет искомой. seekTo на ExoPlayer безопасен
                // в любом состоянии (IDLE/BUFFERING/READY) — это задокументировано
                // в media3 docs: «Can be called from any state».
                if (pos > 1000L) {
                    try { ctrl.seekTo(pos) } catch (_: Exception) {}
                    AppLog.d(TAG, "onNetworkChanged: seekTo($pos) applied immediately (no delay, reason=$reason)")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "onNetworkChanged prepare failed: ${e.message}")
            }
        }
    }

    // ─── Внутренний API ────────────────────────────────────────────

    /**
     * Fix #134: Pre-cache следующего трека в очереди.
     *
     * Берёт следующий MediaItem из плеера, находит соответствующий Track
     * в playlist, и если трек ещё не скачан и не качается — запускает
     * silent-загрузку. Вызывается из onPlaybackStateChanged(STATE_READY)
     * и onMediaItemTransition — чтобы следующий трек был готов ДО того,
     * как пользователь на него перейдёт.
     *
     * Возвращает true если был запущен pre-cache, false если следующий
     * трек уже скачан / качается / отсутствует / autoCacheAudio выключен.
     */
    private fun precacheNextTrack(): Boolean {
        // Fix #140: diagnostic logging для каждой точки skip — пользователь
        // сможет по logcat проверить, почему pre-cache следующего трека
        // не запустился (disabled / no controller / no next track / not HLS /
        // already cached / in progress). SUCCESS-лог остался прежним (Fix #134).
        if (!autoCacheAudio) {
            AppLog.d(TAG, "precacheNext: SKIP (autoCacheAudio disabled)")
            return false
        }
        // Fix #280: TrackDownloadManager теперь имеет FIFO-очередь (Fix #265) —
        // enqueueDownload НЕ запускает параллельный download, а кладёт трек в
        // очередь. Раньше (Fix #170) здесь был hard SKIP при любом активном
        // download, чтобы избежать 2 параллельных HLS → ANR. Теперь это не
        // нужно: следующий трек спокойно встаёт в очередь и качается после
        // текущего. Но чтобы очередь не разрасталась при быстром скиппании —
        // ограничиваем precache лимитом pending (≤2). Текущий (активный)
        // download в этот лимит не входит (getQueueSize = только pending).
        if (TrackDownloadManager.getQueueSize() >= 3) {
            AppLog.d(TAG, "precacheNext: SKIP (queue full: ${TrackDownloadManager.getQueueSize()} pending)")
            return false
        }
        val ctrl = controller
        if (ctrl == null) {
            AppLog.d(TAG, "precacheNext: SKIP (controller null)")
            return false
        }
        // currentMediaItemIndex + 1 = следующий трек в очереди.
        // Если currentMediaItemIndex == last (конец очереди) — nextIndex
        // выходит за границы; hasNextMediaItem() проверяет это с учётом
        // repeatMode (REPEAT_MODE_ALL → next после последнего = первый).
        if (!ctrl.hasNextMediaItem()) {
            AppLog.d(TAG, "precacheNext: SKIP (no next media item)")
            return false
        }
        val nextIndex = ctrl.currentMediaItemIndex + 1
        if (nextIndex < 0 || nextIndex >= ctrl.mediaItemCount) {
            AppLog.d(TAG, "precacheNext: SKIP (nextIndex out of bounds: $nextIndex, count=${ctrl.mediaItemCount})")
            return false
        }
        val nextTrackId = ctrl.getMediaItemAt(nextIndex).mediaId.toLongOrNull()
        if (nextTrackId == null) {
            AppLog.d(TAG, "precacheNext: SKIP (mediaId not Long: ${ctrl.getMediaItemAt(nextIndex).mediaId})")
            return false
        }
        val nextTrack = playlist.firstOrNull { it.id == nextTrackId }
        if (nextTrack == null) {
            AppLog.d(TAG, "precacheNext: SKIP (track #$nextTrackId not in playlist)")
            return false
        }
        if (nextTrack.url.isNullOrBlank()) {
            AppLog.d(TAG, "precacheNext: SKIP track #${nextTrack.id}: URL is null/blank")
            return false
        }
        // Не кешируем прямые (не-HLS) URL — они и так играются быстро.
        val isHls = nextTrack.url.contains("m3u8", ignoreCase = true) ||
            nextTrack.url.contains("vkuseraudio.net")
        if (!isHls) {
            AppLog.w(TAG, "precacheNext: SKIP track #${nextTrack.id}: not HLS (${nextTrack.url.take(60)})")
            return false
        }
        // Fix #170: isDownloaded() вместо getLocalFile() — O(1) без I/O.
        if (TrackDownloadManager.isDownloaded(nextTrack.id)) {
            AppLog.d(TAG, "precacheNext: SKIP track #${nextTrack.id}: already cached")
            return false
        }
        if (TrackDownloadManager.getDownloadState(nextTrack.id)?.isInProgress == true) {
            AppLog.d(TAG, "precacheNext: SKIP track #${nextTrack.id}: download in progress")
            return false
        }
        AppLog.i(TAG, "precacheNext: START track #${nextTrack.id} (${nextTrack.artist} — ${nextTrack.title}, HLS, silent)")
        return try {
            TrackDownloadManager.enqueueDownload(nextTrack, silent = true)
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "precacheNextTrack: enqueueDownload failed: ${e.message}")
            false
        }
    }

    /**
     * Fix #174: Запускает precacheNext КОГДА текущий трек уже скачан (или не
     * нуждается в кэше). Решает баг «кэшируется СЛЕДУЮЩИЙ трек, а не текущий»:
     *
     * Сценарий БЕЗ этого метода (старый код):
     *   1. onMediaItemTransition → precacheNextTrack() → NEXT трек начинает качаться
     *   2. onPlaybackStateChanged(READY) → auto-cache[READY] → SKIP (sequential mode)
     *   3. CURRENT трек НИКОГДА не кэшируется (пока NEXT качается)
     *
     * Сценарий с этим методом:
     *   1. onMediaItemTransition → schedulePrecacheAfterCurrent(current)
     *   2. Если current уже скачан (isDownloaded) → precacheNext СРАЗУ
     *   3. Если current качается сейчас → ждём COMPLETED в Flow → потом precacheNext
     *   4. Если current не качается и не скачан → STATE_READY запустит auto-cache
     *      для current, этот job будет ждать COMPLETED → потом precacheNext
     *
     * Subscription на [TrackDownloadManager.downloads] фильтрует только
     * COMPLETED transition для текущего trackId (debounce через distinctUntilChanged).
     */
    private fun schedulePrecacheAfterCurrent(current: Track?) {
        // Отменяем предыдущий wait-job (если был — для предыдущего трека).
        precacheAfterCurrentJob?.cancel()
        precacheAfterCurrentJob = null
        if (current == null) return
        if (!autoCacheAudio) return
        val currentId = current.id
        // Case 1: CURRENT уже скачан → precacheNext СРАЗУ.
        if (TrackDownloadManager.isDownloaded(currentId)) {
            AppLog.d(TAG, "schedulePrecacheAfterCurrent: track #$currentId already cached → precacheNext immediately")
            precacheNextTrack()
            return
        }
        // Case 2: CURRENT сейчас качается или будет качаться (auto-cache[READY]) —
        // ждём COMPLETED. Подписываемся на downloads flow и фильтруем только
        // transition в COMPLETED для currentId.
        precacheAfterCurrentJob = scope.launch {
            // distinctUntilChanged по статусу currentId — ждём перехода в COMPLETED.
            // map { it[currentId]?.status } → distinctUntilChanged → filter COMPLETED.
            // Используем conflate чтобы не накапливать промежуточные состояния.
            flow {
                TrackDownloadManager.downloads.collect { states ->
                    val status = states[currentId]?.status
                    emit(status)
                }
            }
                .distinctUntilChanged()
                .filter { it == re.pinok.data.model.DownloadStatus.COMPLETED }
                .take(1)  // срабатывает один раз → корутина завершается
                .collect {
                    AppLog.i(TAG, "schedulePrecacheAfterCurrent: track #$currentId COMPLETED → precacheNext now")
                    precacheNextTrack()
                }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null) {
            block(ctrl)
        } else {
            AppLog.w(TAG, "Controller ещё не готов — повтор через 300мс")
            // Fix #169: если controller отсутствует после init — значит сервис
            // убит системой (Doze). Запускаем переподключение немедленно, чтобы
            // к моменту 1-й retry-итерации (300мс) controller уже мог появиться.
            // Без этого 3 retry по 300мс = 900мс ожидания впустую, команда теряется.
            if (initialized) reconnectController()
            scope.launch {
                var done = false
                repeat(3) {
                    // audit Medium #3: return@launch (НЕ return@repeat) —
                    // return@repeat лишь переходил на следующую итерацию, не прерывая
                    // цикл. return@launch корректно выходит из корутины сразу после
                    // успеха, пропуская и лишние итерации, и лог ошибки ниже.
                    if (done) return@launch
                    delay(300L)
                    val c = controller
                    if (c != null) {
                        done = true
                        block(c)
                    }
                }
                if (!done) {
                    AppLog.e(TAG, "withController: controller так и не подключился (сервис убит? реконнект запущен ранее)")
                }
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(PROGRESS_TICK_MS)
                publishProgressIfPlaying()
            }
        }
    }

    private fun publishProgressIfPlaying() {
        val ctrl = controller ?: return
        val current = _playerState.value
        if (current.currentTrack == null) return
        val posMs = ctrl.currentPosition.coerceAtLeast(0L)
        val durMs = ctrl.duration.coerceAtLeast(0L)
        _playerState.value = current.copy(
            positionMs = posMs,
            durationMs = durMs,
        )
        // #39 C2: сохраняем позицию каждые 5с (debounce). Не сохраняем
        // если трек почти дослушан (>95% длительности) — clearPosition
        // вызовется в onMediaItemTransition(AUTO).
        val track = currentTrack
        if (track != null && posMs > 3000L) {
            if (durMs <= 0 || posMs < durMs * 0.95) {
                val now = System.currentTimeMillis()
                if (now - lastPositionSaveTs > 5000L) {
                    re.pinok.media.PlaybackPositionStore.savePosition(track.id.toString(), posMs)
                    lastPositionSaveTs = now
                }
            }
        }
    }

    private fun publishStateImmediate() {
        val ctrl = controller ?: return
        val track = currentTrack ?: return
        _playerState.value = PlayerState(
            currentTrack = track,
            isPlaying = ctrl.isPlaying,
            positionMs = ctrl.currentPosition.coerceAtLeast(0L),
            durationMs = ctrl.duration.coerceAtLeast(0L),
            queue = playlist,
            currentIndex = ctrl.currentMediaItemIndex,
            shuffleModeEnabled = ctrl.shuffleModeEnabled,
            repeatMode = ctrl.repeatMode,
        )
    }

    private fun Track.toMediaItem(cachedLocalFile: java.io.File? = null): MediaItem {
        // Fix #170: УБРАН вызов getLocalFile(id) из toMediaItem.
        // Раньше: `cachedLocalFile ?: getLocalFile(id)` — для каждого трека в
        // плейлисте где localCache[id] == null (online-only треки, 1382 из 1439)
        // вызывался getLocalFile → 2× File.exists() + isValidMpegTs (открывает
        // файл, читает 377 байт). Для плейлиста из 1439 треков = ~2878 syscall'ов
        // на main thread → ANR (Skipped 246 frames, 4097ms MotionEvent, 4164ms Davey).
        //
        // Теперь: cachedLocalFile — единственный источник. playTrackList/shuffleAll
        // всегда передают cachedLocalFile (могет быть null если трек не скачан).
        // Для одиночных вызовов (playTrackById) — тоже null, но в гибридном режиме
        // (Fix #165) при наличии интернета кэш не используется → null безопасен.
        // При отсутствии интернета playTrackList фильтрует треки без валидного кэша
        // через localCache (см. playable filter), так что невалидные треки сюда
        // вообще не попадают.
        val localFile = cachedLocalFile
        // Fix #165: ГИБРИДНЫЙ PLAYBACK.
        // При наличии интернета — стримим HLS из сети (даже если есть кэш).
        // Это решает «перепрыгивание» треков: 18 из 30 кэшированных файлов
        // повреждены AES-мусором (magic 25 78 11 5b вместо 0x47), ExoPlayer
        // падает с UnrecognizedInputFormatException и прыгает дальше. При
        // онлайн-стриминге все треки идут через HLS — единый формат, стабильно.
        // При офлайне — играем из кэша (если валиден). Если кэш повреждён —
        // URI = about:blank, трек пропустится (см. playTrackList filter).
        val isOnline = re.pinok.SovaApp.getOrNull()?.networkObserver?.isOnline() ?: false
        val hasUrl = !this.url.isNullOrBlank()
        // #SIREN-FIX (2026-08-01): .ts-кэш с VK Siren codec (non-encrypted HLS,
        // первый байт != 0x47) НЕ проигрывается ExoPlayer'ом офлайн — TsExtractor
        // не умеет siren → UnrecognizedInputFormatException → цикл ошибок.
        // Лог 2026-08-01 19:49:04: track #456249594, magic 25 78 11 5b, падал.
        // Если локальный .ts — siren, НЕ используем его: при наличии URL стримим
        // онлайн HLS (там HLS-стек умеет siren), без URL — about:blank (skip).
        // Чтение 1 байта — дёшево; делаем только если реально рассматриваем local.
        // Note: localFile != null включён напрямую в useLocal (не через отдельный
        // val) — иначе Kotlin не смарт-кастит localFile в Uri.fromFile ниже.
        val considerLocal = !isOnline || !hasUrl
        var localIsSiren = false
        if (localFile != null && considerLocal && localFile.extension.lowercase() == "ts") {
            try {
                val firstByte = localFile.inputStream().use { it.read() }
                localIsSiren = firstByte != 0x47
            } catch (e: Exception) {
                AppLog.w(TAG, "toMediaItem: siren-check read failed for #${this.id}: ${e.message}")
            }
        }
        if (localIsSiren) {
            AppLog.w(TAG, "toMediaItem: track=#${this.id} local .ts is SIREN (magic!=0x47) — " +
                "unplayable offline, ${if (hasUrl && isOnline) "streaming online HLS" else "no URL → will skip"}")
        }
        val useLocal = localFile != null && considerLocal && !localIsSiren
        val isLocal = useLocal
        val uri = if (isLocal) {
            android.net.Uri.fromFile(localFile)
        } else if (hasUrl) {
            // Fix #62: VK audio.get/audio.getCatalog иногда отдают HTTP-ссылки
            // (http://cs1-50v4.vkuseraudio.net/...). network_security_config
            // запрещает cleartextTraffic → ExoPlayer падает с "Cleartext HTTP
            // traffic not permitted". VK audio CDN поддерживает HTTPS, поэтому
            // безопасно переписать схему. Также обрезаем лишние пробелы/CR-LF,
            // которые иногда просачиваются из VK-ответа.
            val cleaned = this.url.trim()
            val https = if (cleaned.startsWith("http://")) {
                "https://" + cleaned.substring("http://".length)
            } else cleaned
            // Fix: VK audio CDN отдаёт базовый URL без /index.m3u8.
            // Если URL с vkuseraudio.net и не содержит m3u8 — добавляем.
            val hlsUrl = if (https.contains("vkuseraudio.net") &&
                !https.contains("m3u8", ignoreCase = true)) {
                "${https.trimEnd('/')}/index.m3u8"
            } else https
            android.net.Uri.parse(hlsUrl)
        } else {
            android.net.Uri.parse("about:blank")
        }
        AppLog.d(TAG, "toMediaItem: track #${id} owner=${ownerId} url=${if (isLocal) "LOCAL" else uri.toString().take(80)}")
        // Fix #165: логирование гибридного решения ONLINE vs OFFLINE.
        // Видно в logcat какой путь выбран и почему:
        //   - ONLINE (HLS stream) — есть сеть + есть URL
        //   - OFFLINE (cache) — нет сети ИЛИ нет URL, но есть валидный кэш
        //   - SKIP — нет ни URL, ни валидного кэша (трек пропустится)
        if (isLocal) {
            AppLog.i(TAG, "OFFLINE toMediaItem: track=#$id ext=${localFile.extension} size=${localFile.length()}B path=${localFile.absolutePath} (reason=${if (!isOnline) "no-net" else "no-url"})")
            val header = ByteArray(4)
            try {
                java.io.DataInputStream(localFile.inputStream()).use { it.readFully(header) }
                AppLog.d(TAG, "OFFLINE magic bytes: ${header.joinToString(" ") { "%02x".format(it) }}")
            } catch (e: Exception) {
                AppLog.w(TAG, "OFFLINE header read failed: ${e.message}")
            }
        } else if (hasUrl && isOnline) {
            AppLog.i(TAG, "ONLINE toMediaItem: track=#$id url=${uri.toString().take(80)} (HLS stream, cache=${if (localFile != null) "ignored-online" else "absent"})")
        } else if (hasUrl && !isOnline) {
            // Этого не должно быть при isLocal=false — но если кэш невалиден,
            // useLocal=false, и при отсутствии сети мы сюда попадём.
            AppLog.w(TAG, "SKIP toMediaItem: track=#$id — offline + cache invalid/absent, url exists but unreachable (will skip)")
        }
        // Fix #68: VK audio URLs теперь 100% HLS (.m3u8?siren=1).
        // Без явного MIME-type ExoPlayer пытается парсить m3u8-плейлист как
        // сырой ProgressiveMediaSource → ERROR_CODE_PARSING_* или вечный BUFFERING.
        // Аналогично Fix #60 для видео (VideoPlayerScreen.kt:115-117).
        val builder = MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
        val rawUrl = this.url
        if (!isLocal && !rawUrl.isNullOrBlank() &&
            (rawUrl.contains("m3u8", ignoreCase = true) || rawUrl.contains("vkuseraudio.net"))) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            AppLog.d(TAG, "ONLINE mime=APPLICATION_M3U8 (HLS)")
        }
        // #71: для локальных .ts файлов (HLS-склейка) явно указываем MIME-тип
        // MPEG-TS. Без этого ExoPlayer пытается парсить как MP3 →
        // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
        // .mp3 файлы (прямые скачивания) — указываем AUDIO_MPEG.
        // #BT-37SEC-FIX: .m4a файлы (MediaMuxer output) — AUDIO_MP4.
        if (isLocal) {
            val ext = localFile.extension.lowercase()
            val mime = when (ext) {
                "m4a" -> {
                    AppLog.i(TAG, "OFFLINE mime=AUDIO_MP4 (M4A — MediaMuxer output, BT-safe)")
                    MimeTypes.AUDIO_MP4
                }
                "ts" -> {
                    AppLog.i(TAG, "OFFLINE mime=VIDEO_MP2T (MPEG-TS container)")
                    MimeTypes.VIDEO_MP2T
                }
                "mp3" -> {
                    AppLog.i(TAG, "OFFLINE mime=AUDIO_MPEG (MP3)")
                    MimeTypes.AUDIO_MPEG
                }
                else -> {
                    AppLog.w(TAG, "OFFLINE unknown ext=$ext — no MIME set, ExoPlayer will try auto-detect")
                    null
                }
            }
            if (mime != null) builder.setMimeType(mime)
        }
        return builder
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(null)
                    .build()
            )
            .build()
    }

    // ─── Controller listener ───────────────────────────────────────

    private class ControllerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            // Fix #170: используем isDownloaded() (O(1) StateFlow lookup) вместо
            // getLocalFile() (I/O: File.exists + isValidMpegTs). Раньше при каждом
            // событии play/pause вызывался getLocalFile на main thread.
            val track = currentTrack
            val local = if (track != null) TrackDownloadManager.isDownloaded(track.id) else false
            AppLog.i(TAG, "onIsPlayingChanged → $isPlaying | track=#${track?.id ?: -1} local=$local pos=${_playerState.value.positionMs}ms dur=${_playerState.value.durationMs}ms")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // #39 C2: сохраняем позицию ПРЕДЫДУЩЕГО трека перед переходом.
            // reason == AUTO = трек доиграл до конца → очищаем позицию.
            val oldTrack = currentTrack
            if (oldTrack != null) {
                val oldPos = _playerState.value.positionMs
                val oldDur = _playerState.value.durationMs
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    re.pinok.media.PlaybackPositionStore.clearPosition(oldTrack.id.toString())
                    AppLog.d(TAG, "onMediaItemTransition: cleared pos for finished track #${oldTrack.id}")
                } else {
                    if (oldPos > 3000L && (oldDur <= 0 || oldPos < oldDur * 0.95)) {
                        re.pinok.media.PlaybackPositionStore.savePosition(oldTrack.id.toString(), oldPos)
                    }
                    re.pinok.media.PlaybackPositionStore.flush()
                }
            }
            lastPositionSaveTs = 0L  // сброс debounce для нового трека

            // REPEAT_MODE_TWO: перехватываем AUTO-переход после 1-го проигрывания
            // и крутим трек 2 раза, на 3-й раз пропускаем к следующему.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                _playerState.value.repeatMode == PlayerState.REPEAT_MODE_TWO &&
                oldTrack != null) {
                repeatTwoCount++
                if (repeatTwoCount < 2) {
                    // 1-е проигрывание закончилось → перемотать на начало того же трека.
                    val oldIndex = playlist.indexOfFirst { it.id == oldTrack.id }
                    if (oldIndex >= 0) {
                        AppLog.i(TAG, "REPEAT_MODE_TWO: replay #${oldTrack.id} (play ${repeatTwoCount + 1}/2)")
                        controller?.seekTo(oldIndex, 0L)
                        controller?.play()
                        // НЕ обновляем currentTrack — остаётся oldTrack.
                        return
                    }
                } else {
                    AppLog.i(TAG, "REPEAT_MODE_TWO: #${oldTrack.id} отыграл 2 раза → next")
                    repeatTwoCount = 0
                }
            } else if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Явный next/prev пользователя — сбрасываем счётчик для нового трека.
                repeatTwoCount = 0
            }

            if (mediaItem == null) {
                currentTrack = null
                _playerState.value = _playerState.value.copy(
                    currentTrack = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    currentIndex = -1,
                )
                return
            }
            val trackId = mediaItem.mediaId.toLongOrNull()
            val track = playlist.firstOrNull { it.id == trackId }
            currentTrack = track
            // Fix #51-C: убран pendingRestoreTrackId — помечал трек для restore
            // позиции после STATE_READY. Это восстанавливало позицию при next/prev
            // (явный переход пользователя) — пользователь нажимает next на треке A,
            // потом prev чтобы вернуться к A → A начинался с сохранённой позиции
            // вместо начала. next/prev = явный выбор → не должен resume.
            // Fix #59: новый трек → сбрасываем ошибку (предыдущая могла быть
            // для трека, который не воспроизвёлся).
            _playerState.value = _playerState.value.copy(
                currentTrack = track,
                positionMs = 0L,
                durationMs = 0L,
                currentIndex = controller?.currentMediaItemIndex ?: -1,
                error = null,
            )
            AppLog.d(TAG, "onMediaItemTransition → track=${track?.title} reason=$reason local=${if (track != null) TrackDownloadManager.isDownloaded(track.id) else false}")
            // Fix #174: НЕ вызываем precacheNextTrack() здесь напрямую!
            // Раньше это вызывало баг «кэшируется СЛЕДУЮЩИЙ трек, а не текущий»:
            // onMediaItemTransition → START NEXT download → READY → auto-cache[READY]
            // SKIP CURRENT (sequential mode) → CURRENT никогда не кэшируется.
            // Теперь schedulePrecacheAfterCurrent решает когда запускать precacheNext:
            //   - если CURRENT уже скачан → сразу precacheNext
            //   - если CURRENT качается → ждём COMPLETED → потом precacheNext
            //   - если CURRENT будет качаться (auto-cache[READY]) → аналогично ждём
            if (track != null && autoCacheAudio) {
                schedulePrecacheAfterCurrent(track)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = controller ?: return
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            val track = currentTrack
            val local = if (track != null) TrackDownloadManager.isDownloaded(track.id) else false
            AppLog.i(TAG, "onPlaybackStateChanged → $stateName | track=#${track?.id ?: -1} local=$local pos=${ctrl.currentPosition}ms dur=${ctrl.duration}ms")
            // Fix #178: BUFFERING watchdog — запускаем при входе в BUFFERING,
            // отменяем при любом другом state. Срабатывает через 30s если плеер
            // всё ещё BUFFERING → force reprepare (см. bufferingWatchdogJob).
            bufferingWatchdogJob?.cancel()
            if (playbackState == Player.STATE_BUFFERING) {
                bufferingWatchdogJob = scope.launch {
                    delay(30_000L)
                    val c = controller ?: return@launch
                    if (c.playbackState == Player.STATE_BUFFERING) {
                        AppLog.w(TAG, "BUFFERING watchdog: 30s timeout — force reprepare (track=#${track?.id ?: -1})")
                        try {
                            val pos = c.currentPosition.coerceAtLeast(0L)
                            c.prepare()
                            if (pos > 1000L) {
                                try { c.seekTo(pos) } catch (_: Exception) {}
                            }
                            AppLog.i(TAG, "BUFFERING watchdog: reprepare applied (pos=${pos}ms)")
                        } catch (e: Exception) {
                            AppLog.e(TAG, "BUFFERING watchdog: reprepare failed: ${e.message}", e)
                        }
                    }
                }
            }
            if (playbackState == Player.STATE_READY) {
                // Fix #51-C: убран restore сохранённой позиции через pendingRestoreTrackId.
                // Раньше: onMediaItemTransition помечал трек → здесь восстанавливалась
                // позиция. Это работало не только для cold-start resume, но и для next/prev
                // (явный переход) → трек начинался с середины вместо начала.
                // Теперь restore не делается — playTrackList и next/prev стартуют с начала.
                // Сохранение позиции в onMediaItemTransition осталось (для потенциального
                // будущего cold-start restore через отдельный метод).
                val durMs = ctrl.duration.coerceAtLeast(0L)
                if (durMs > 0) {
                    // Fix #59: STATE_READY значит трек успешно загрузился — сбрасываем ошибку.
                    _playerState.value = _playerState.value.copy(
                        durationMs = durMs,
                        error = null,
                    )
                }
                // Успешное воспроизведение — сбрасываем счётчик последовательных ошибок.
                consecutivePlayerErrors = 0

                // Fix #76: авто-кеширование HLS-треков.
                // VK audio — 100% зашифрованный HLS (AES-128). SimpleCache
                // кэшировал зашифрованные .ts-сегменты, но ключ шифрования
                // истекает → PARSING_CONTAINER_UNSUPPORTED при повторе.
                // Новая стратегия: при первом успешном воспроизведении трека
                // Fix #76: auto-caching HLS track in background.
                // Fix #110: gate через autoCacheAudio pref (default false, #AUTOCACHE-AUDIO-OFF).
                // Fix #140: diagnostic logging для всех веток решения (START/SKIP + reason).
                val track = currentTrack
                if (track != null && !track.url.isNullOrBlank() && autoCacheAudio) {
                    val url = track.url
                    val isHls = url.contains("m3u8", ignoreCase = true) ||
                        url.contains("vkuseraudio.net")
                    // Fix #170: isDownloaded() вместо getLocalFile() — O(1) без I/O.
                    // Fix #280: hasActiveDownload() gate убран — FIFO-очередь (Fix #265)
                    // гарантирует sequential загрузку без параллелизма. Раньше gate
                    // SKIPал текущий трек, если качался другой → текущий не кэшировался.
                    val isCached = TrackDownloadManager.isDownloaded(track.id)
                    val inProgress = TrackDownloadManager.getDownloadState(track.id)?.isInProgress == true
                    when {
                        isCached -> AppLog.d(TAG, "auto-cache[READY] SKIP track #${track.id}: already cached")
                        inProgress -> AppLog.d(TAG, "auto-cache[READY] SKIP track #${track.id}: download in progress")
                        !isHls -> AppLog.w(TAG, "auto-cache[READY] SKIP track #${track.id}: not HLS (${url.take(60)})")
                        else -> {
                            AppLog.i(TAG, "auto-cache[READY] START track #${track.id} (HLS, silent)")
                            TrackDownloadManager.enqueueDownload(track, silent = true)
                        }
                    }
                    // Fix #174: precacheNext теперь запускается через
                    // schedulePrecacheAfterCurrent (уже вызван из
                    // onMediaItemTransition). Он дождётся COMPLETED этого
                    // auto-cache[READY] download'а и только потом запустит
                    // precacheNext. Старый вызов precacheNextTrack() здесь
                    // был бесполезен — sequential mode его SKIPал.
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            AppLog.e(TAG, "onPlayerError: ${error.errorCodeName} (code=${error.errorCode})", error)
            // Fix #170: isDownloaded() для quick check (O(1)), getLocalFile только
            // если isDownloaded true — для детального логирования (file name, size).
            // onPlayerError — редкое событие, один I/O вызов приемлем.
            val track = currentTrack
            val isCached = if (track != null) TrackDownloadManager.isDownloaded(track.id) else false
            val localFile = if (isCached && track != null) TrackDownloadManager.getLocalFile(track.id) else null
            if (localFile != null) {
                AppLog.e(TAG, "OFFLINE ERROR details: track=#${track?.id} file=${localFile.name} ext=${localFile.extension} size=${localFile.length()}B exists=${localFile.exists()}")
                AppLog.e(TAG, "OFFLINE ERROR uri=${localFile.toURI()}")
            } else if (track != null) {
                AppLog.e(TAG, "ONLINE ERROR details: track=#${track.id} url=${track.url?.take(80)}")
            }
            AppLog.e(TAG, "ERROR cause: ${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")

            // Fix #233 (P0): при ЛЮБОЙ ошибке воспроизведения (не только PARSING_*)
            // — если есть локальный кэш, переключаемся на него. Раньше при
            // HTTP 403 (протухший siren=1 URL через ~1ч) ExoPlayer падал, а
            // onPlayerError только для PARSING_* вызывал enqueueDownload тем же
            // протухшим URL → бесконечный 403. В гибридном режиме Fix #165
            // (prefer online over local) кэш игнорируется → протухший URL = глухая
            // ошибка даже на скачанном треке.
            //
            // Защита от цикла: флаг lastErrorTrackId+lastErrorTime — не ретраиваем
            // тот же трек из кэша чаще раза в 10с. Если локальный кэш тоже падает
            // (PARSING_CONTAINER_UNSUPPORTED от AES-мусора) — переходим к
            // существующей логике PARSING ниже (enqueueDownload + skip).
            if (track != null && localFile != null) {
                // #SIREN-FIX (2026-08-01): если локальный .ts — siren (magic!=0x47),
                // ретраить его бессмысленно — TsExtractor опять упадёт с
                // UnrecognizedInputFormatException. Пропускаем local-fallback и
                // переходим к логике PARSING ниже (auto-skip на следующий трек).
                val localIsSirenTs = localFile.extension.lowercase() == "ts" && runCatching {
                    java.io.DataInputStream(localFile.inputStream()).use { it.readByte() } != 0x47.toByte()
                }.getOrDefault(false)
                if (localIsSirenTs) {
                    AppLog.w(TAG, "onPlayerError: track #${track.id} local .ts is SIREN (0x25) — " +
                        "unplayable offline, skip local fallback (Fix #233), go to skip logic")
                } else {
                val now = System.currentTimeMillis()
                val sameTrack = lastErrorTrackId == track.id
                val recent = sameTrack && (now - lastErrorTrackTimeMs) < 10_000L
                if (!recent) {
                    lastErrorTrackId = track.id
                    lastErrorTrackTimeMs = now
                    AppLog.w(TAG, "onPlayerError: switching track #${track.id} to LOCAL cache fallback (Fix #233). " +
                        "Online URL likely expired (403) or unreachable. Local file: ${localFile.name}")
                    // Переключаем на локальный файл, сохраняя позицию.
                    val posMs = try { _playerState.value.positionMs } catch (_: Exception) { 0L }
                    val mediaItem = track.toMediaItem(cachedLocalFile = localFile)
                    scope.launch {
                        withController { ctrl ->
                            ctrl.setMediaItem(mediaItem, posMs)
                            ctrl.prepare()
                            ctrl.playWhenReady = true
                        }
                    }
                    // Пробрасываем ошибку — но более мягкое сообщение.
                    _playerState.value = _playerState.value.copy(
                        error = "Сетевой URL истёк — переключение на локальный кэш",
                    )
                    return
                } else {
                    AppLog.w(TAG, "onPlayerError: track #${track.id} already failed recently (${now - lastErrorTrackTimeMs}ms ago) — local fallback already tried, going to skip/cache logic")
                }
                } // end else (not siren)
            }

            // Fix #76: при PARSING_CONTAINER_UNSUPPORTED — раньше очищали кэш
            // и скипали трек. Теперь CacheDataSource убран из PlayerService,
            // но если в старом кэше остались данные — всё равно чистим.
            // Также: пытаемся скачать+расшифровать трек в фоне.
            if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
            ) {
                val trackUrl = currentTrack?.url
                val track = currentTrack
                // #39 C7: AudioStreamCache.removeForUrl убран — стриминг-кэш удалён
                // (zombie, Fix #76). Авто-кеширование через TrackDownloadManager
                // ниже в этом же блоке.
                if (!trackUrl.isNullOrBlank()) {
                    AppLog.i(TAG, "onPlayerError: track url=${trackUrl.take(60)}")
                }
                // Fix #76: попытка скачать трек в фоне, чтобы следующий
                // раз воспроизвести из локального файла.
                // Fix #110: gate через autoCacheAudio pref (default false, #AUTOCACHE-AUDIO-OFF).
                // Note: error-triggered cache тоже гейтится — если пользователь
                // отключил авто-кеш, он не хочет чтобы трек качался автоматически
                // даже при ошибке. Ручное скачивание через UI остаётся доступным.
                // Fix #140: diagnostic logging — нет isHls gate (error-triggered
                // cache срабатывает даже для прямых URL, т.к. PARSING_CONTAINER_UNSUPPORTED
                // означает что стриминг всё равно невозможен). Логируем START с url.
                // Fix #280: !hasActiveDownload() убран из условия — FIFO-очередь
                // (Fix #265) разруливает sequencing. Трек при ошибке встаёт в
                // очередь и качается после текущего.
                if (track != null && !track.url.isNullOrBlank() && autoCacheAudio &&
                    !TrackDownloadManager.isDownloaded(track.id) &&
                    TrackDownloadManager.getDownloadState(track.id)?.isInProgress != true
                ) {
                    AppLog.i(TAG, "auto-cache[ERROR] START track #${track.id} (url=${track.url.take(60)}, silent)")
                    TrackDownloadManager.enqueueDownload(track, silent = true)
                }
                consecutivePlayerErrors++
                // Авто-скип на следующий трек, но не более 5 ошибок подряд
                if (consecutivePlayerErrors <= 5) {
                    scope.launch {
                        delay(300L)
                        withController { ctrl ->
                            if (ctrl.hasNextMediaItem()) {
                                ctrl.seekToNextMediaItem()
                                ctrl.playWhenReady = true
                            }
                        }
                    }
                } else {
                    AppLog.w(TAG, "onPlayerError: $consecutivePlayerErrors consecutive errors — stopping auto-skip")
                }
            }
            // Пробрасываем ошибку в StateFlow — UI покажет баннер.
            val trackUrl2 = currentTrack?.url?.take(80) ?: "null"
            _playerState.value = _playerState.value.copy(
                error = "Ошибка воспроизведения: ${error.errorCodeName}\nURL: $trackUrl2",
            )
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.value = _playerState.value.copy(shuffleModeEnabled = shuffleModeEnabled)
            AppLog.d(TAG, "onShuffleModeEnabledChanged → $shuffleModeEnabled")
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.value = _playerState.value.copy(repeatMode = repeatMode)
            AppLog.d(TAG, "onRepeatModeChanged → $repeatMode")
        }
    }
}
