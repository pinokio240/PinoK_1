package re.pinok.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.DownloadState
import re.pinok.media.EqualizerHelper
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.util.AppLog

/**
 * Foreground media player service — wraps ExoPlayer + MediaSession.
 *
 * Mirrors the original SOVA V RE `PlayerService` which used a custom
 * foreground notification + AIDL. SOVA_2.0 uses Media3 `MediaSessionService`
 * which is the modern replacement and integrates with the system media router.
 *
 * Fix #62: ExoPlayer настраивается с OkHttpDataSource, переиспользующим
 * OkHttpClient приложения (VK User-Agent + X-VK-Android-Client interceptor).
 * Раньше ExoPlayer использовал DefaultHttpDataSource с UA "ExoPlayer" —
 * VK audio CDN (psv4.vkuseraudio.net) местами отдаёт 403/пустой ответ на
 * не-VK User-Agent. Теперь стрим идёт через тот же OkHttp, что и API-зовы.
 * http→https редиректы OkHttp обрабатывает нативно (followSslRedirects=true
 * в SovaApp.httpClient), cleartextTrafficPermitted=false в network_security_config.
 *
 * Fix #64: убран вызов setAllowCrossProtocolRedirects(true) — этого метода
 * НЕТ на OkHttpDataSource.Factory в media3 1.8.0 (он только на
 * DefaultHttpDataSource.Factory). Компиляция падала с Unresolved reference.
 *
 * Fix #73→#76: CacheDataSource УБРАН. VK audio — 100% зашифрованный HLS
 * (AES-128, #EXT-X-KEY с короткоживущими ключами). SimpleCache кэшировал
 * зашифрованные .ts-сегменты, но при повторном воспроизведении ключ уже
 * истекал → PARSING_CONTAINER_UNSUPPORTED.
 * Новая стратегия (Fix #76): при первом воспроизведении трека он
 * автоматически скачивается, расшифровывается и склеивается в один
 * локальный .ts-файл через TrackDownloadManager (в фоне). Повторное
 * воспроизведение — из локального файла (офлайн).
 *
 * Fix #138: добавлена кастомная кнопка «Скачать» в MediaSession custom layout.
 * Кнопка появляется в EXPANDED media notification (на lock screen и в шторке),
 * Android Auto, Wear OS. Compact notification (3 действия: prev/play/next)
 * систему не меняет — это стандартное поведение Media3.
 * Custom SessionCommand `ACTION_DOWNLOAD` обрабатывается в onCustomCommand:
 * берёт текущий трек из PlayerConnection.currentTrackForDownload() и
 * энqueue'ит его в TrackDownloadManager (silent=false → progress notification).
 * Состояние кнопки обновляется динамически через подписку на
 * TrackDownloadManager.downloads + PlayerConnection.playerState — пока
 * трек качается, кнопка disabled; после завершения снова enabled.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * Coroutine scope for the lock-screen download button state subscriber.
     * Cancelled in [onDestroy]. Uses Main.immediate because PlayerService
     * callbacks + MediaSession.setCustomLayout must be called from main thread.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** TrackId of the currently playing track (mirrors PlayerConnection.currentTrack). */
    @Volatile
    private var currentTrackId: Long? = null

    /** Mirrors whether the download button should be enabled (false while download is in progress). */
    @Volatile
    private var currentDownloadButtonEnabled: Boolean = true

    /**
     * Fix #287: детекция смены audio output route (подключение/отключение
     * Bluetooth A2DP, проводной гарнитуры) для rebind'а эквалайзера.
     * android.media.audiofx.Equalizer, привязанный к sessionId, на многих
     * устройствах перестаёт применяться к выводу после смены route — reattach
     * (release + new Equalizer на том же sessionId) принудительно перепривязывает
     * эффект. Коллбэк срабатывает в т.ч. на initial device enumeration при
     * регистрации — EqualizerHelper.reattach() в этом случае no-op (EQ ещё не
     * привязан).
     *
     * Fix #342: динамический debounce. Bluetooth A2DP connect даёт серию
     * device-added событий + codec negotiation (SBC/AAC/aptX) занимает ~1с.
     * 600мс debounce ловил reattach в середине negotiation → EQ привязывался
     * к нестабильному output → «приглушённый звук». Теперь: BT A2DP → 1200мс,
     * иначе 600мс. Также логируем типы устройств для диагностики.
     *
     * #EQ-BT (#Equalizer Stage 2.5): после debounce вызывается
     * [re.pinok.media.AudioRouteLogger.logActiveRoute] — логирует активный
     * output + кодек (SBC/AAC/aptX/LDAC) + имя BT-устройства + sample rate.
     * Вызов ПОСЛЕ debounce критичен: getCodecStatus() в середине negotiation
     * вернёт промежуточный кодек. Требует BLUETOOTH_CONNECT (API 31+) для
     * чтения кодека; без permission пишется `codec=perm_denied`, но сам
     * AudioDeviceCallback работает (ему permission не нужен).
     *
     * #EQ-SCO: при переходе на Bluetooth SCO (звонковая гарнитура, моно 8kHz)
     * [re.pinok.media.AudioEffectsEngine.suspendForSco] отключает Virtualizer
     * + PresetReverb (они дают фазовые артефакты/эхо на моно). Equalizer,
     * BassBoost, LoudnessEnhancer остаются (не вредны). При возврате на
     * A2DP/speaker/wired — [restoreAfterSco] возвращает сохранённое состояние.
     */
    private val audioRouteHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var pendingEqReattach = false
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val types = addedDevices?.map { deviceTypeName(it.type) } ?: emptyList()
            AppLog.i("PlayerService", "Audio devices added: $types")
            // #EQ-BT: isBluetooth смотрим по ВСЕМ активным sinks, не по
            // addedDevices. При подключении BT-гарнитуры сначала приходит
            // callback с [BT_SCO] (HFP profile), и только через ~500мс —
            // [BT_A2DP]. Если считать по addedDevices, первый callback
            // даст bt=false и выберет короткий debounce (600мс вместо 1200мс),
            // а второй вообще проигнорируется (pendingEqReattach=true).
            // По всем sinks — корректно bt=true с первого callback'а.
            val changedTo = addedDevices?.firstOrNull()?.type ?: -1
            scheduleEqReattach(
                isBluetooth = isAnyBluetoothOutputConnected(),
                changedToType = changedTo,
            )
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val types = removedDevices?.map { deviceTypeName(it.type) } ?: emptyList()
            AppLog.i("PlayerService", "Audio devices removed: $types")
            scheduleEqReattach(
                isBluetooth = isAnyBluetoothOutputConnected(),
            )
        }
    }

    /** true если среди активных output sinks есть любой BT-профиль (A2DP или SCO). */
    private fun isAnyBluetoothOutputConnected(): Boolean = try {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return false
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    } catch (e: Exception) {
        AppLog.w("PlayerService", "isAnyBluetoothOutputConnected: ${e.message}")
        false
    }
    private fun scheduleEqReattach(isBluetooth: Boolean = false, changedToType: Int = -1) {
        if (pendingEqReattach) return
        pendingEqReattach = true
        val delay = if (isBluetooth) EQ_REATTACH_DEBOUNCE_BT_MS else EQ_REATTACH_DEBOUNCE_MS
        audioRouteHandler.postDelayed({
            pendingEqReattach = false
            // #EQ-BT: логируем активный output + кодек (SBC/AAC/aptX/LDAC).
            // Делается ПОСЛЕ debounce — к этому моменту codec negotiation
            // завершён и getCodecStatus() вернёт финальный кодек.
            re.pinok.media.AudioRouteLogger.logActiveRoute(changedToType)
            // #EQ-SCO: если перешли на Bluetooth SCO (звонковая гарнитура,
            // моно 8kHz) — приостанавливаем Virtualizer+Reverb (они вредны
            // на моно). Если вернулись с SCO — восстанавливаем.
            val engine = re.pinok.media.EqualizerHelper.engine()
            val onSco = re.pinok.media.AudioRouteLogger.isScoRoute()
            if (onSco && engine != null && !engine.isScoSuspended()) {
                engine.suspendForSco()
            } else if (!onSco && engine != null && engine.isScoSuspended()) {
                engine.restoreAfterSco()
            }
            AppLog.i("PlayerService", "Audio route changed (bt=$isBluetooth, sco=$onSco) — reattaching Equalizer (Fix #287/#342)")
            EqualizerHelper.reattach()
        }, delay)
    }

    /** Возвращает читаемое имя типа audio device для логов. */
    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        else -> "type=$type"
    }

    /**
     * Custom session command for the «Скачать» button on the lock screen / notification.
     *
     * Fix #138-buildfix: в Media3 1.4.0 НЕТ константы COMMAND_VERSION_1 (ни на SessionCommand,
     * ни на CommandButton) и НЕТ конструктора SessionCommand(String, Int). Единственный
     * конструктор для custom action — `SessionCommand(String, Bundle)`. Используем
     * Bundle.EMPTY. Этот конструктор работает во ВСЕХ версиях Media3 1.x (в 1.4.0 он
     * не deprecated, в 1.5.0+ deprecated но функционален).
     */
    private val downloadCommand = SessionCommand(ACTION_DOWNLOAD, Bundle.EMPTY)

    /**
     * Builds the «Скачать» CommandButton that appears in the expanded media
     * notification (lock screen, Android Auto, Wear OS). The button dispatches
     * [downloadCommand] to [sessionCallback.onCustomCommand] when pressed.
     *
     * Icon: `stat_sys_download` (system-provided download icon — always available
     * across all Android versions / OEM ROMs, no app resource needed).
     */
    @Suppress("DEPRECATION") // Builder() & setIconResId deprecated in Media3 1.4+;
    // non-deprecated alternatives (Builder(ICON_UNDEFINED), setCustomIconResId)
    // are @UnstableApi which would just trade one warning for another.
    // We use a system icon (stat_sys_download) not in the predefined @Icon list,
    // and these methods still work in all Media3 1.x (not removed even in 1.8.0).
    private fun buildDownloadButton(enabled: Boolean = true): CommandButton = CommandButton.Builder()
        .setSessionCommand(downloadCommand)
        .setDisplayName("Скачать")
        .setEnabled(enabled)
        .setIconResId(android.R.drawable.stat_sys_download)
        .build()

    /**
     * MediaSession.Callback that:
     *  1. In [onConnect] — registers [downloadCommand] as an available session
     *     command and returns the connection result with available commands.
     *  2. In [onCustomCommand] — handles [ACTION_DOWNLOAD] by enqueuing the
     *     current track in TrackDownloadManager (non-silent → progress notification).
     *
     * Fix #138-buildfix: в Media3 1.4.0 НЕТ `ConnectionResult.Builder()` (добавлен в 1.5.0)
     * и НЕТ public конструктора `ConnectionResult(SessionCommands, PlayerCommands)`
     * (конструктор package-private с 7 параметрами). Единственный способ создать
     * accepted ConnectionResult с кастомными командами — статический factory method
     * `ConnectionResult.accept(SessionCommands, Player.Commands)`. Этот метод есть
     * во ВСЕХ версиях Media3 1.x.
     *
     * Custom layout НЕ устанавливается здесь — он уже установлен на сессии через
     * `MediaSession.Builder.setCustomLayout()` в [onCreate] и обновляется
     * динамически через [updateDownloadButton] → `MediaSession.setCustomLayout()`.
     * Контроллеры получают текущий custom layout сессии при подключении.
     */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(downloadCommand)
                .build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_DOWNLOAD) {
                AppLog.i("PlayerService", "Lock screen download button pressed (Fix #138)")
                val track = PlayerConnection.currentTrackForDownload()
                if (track == null) {
                    AppLog.w("PlayerService", "Download button: no current track")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
                // Already downloaded — no-op success (user can press repeatedly without re-downloading).
                if (TrackDownloadManager.isDownloaded(track.id)) {
                    AppLog.i("PlayerService", "Track #${track.id} already downloaded — skipping")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                // Already in progress — no-op success.
                if (TrackDownloadManager.getDownloadState(track.id)?.isInProgress == true) {
                    AppLog.i("PlayerService", "Track #${track.id} already downloading — skipping")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                // Enqueue (non-silent → foreground progress notification).
                return try {
                    TrackDownloadManager.enqueueDownload(track, silent = false)
                    AppLog.i(
                        "PlayerService",
                        "Download enqueued for track #${track.id} (${track.artist} — ${track.title})"
                    )
                    // Button state will be refreshed by the subscriber when
                    // TrackDownloadManager.downloads emits the new QUEUED state.
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (e: Exception) {
                    AppLog.e("PlayerService", "Download enqueue failed for track #${track.id}", e)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.i("PlayerService", "onCreate")
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Fix #50 → #51-buildfix: audioSessionId для эквалайзера.
        // В media3 1.8.0 ExoPlayer.Builder НЕ имеет setAudioSessionId(int) —
        // этот метод был в старом ExoPlayer 2.x (com.google.android.exoplayer2).
        // В media3 audioSessionId выделяется автоматически при создании плеера
        // и СТАБИЛЕН на весь lifecycle (не меняется при смене трека — только
        // при реинициализации audio sink, чего не происходит в нормальном flow).
        // Поэтому: строим плеер без setAudioSessionId, после build() берём
        // player.audioSessionId и attach EQ один раз. Это решает исходную
        // проблему Fix #50 (EQ не сбрасывается при смене трека) без
        // несуществующего метода на Builder.
        val playerBuilder = ExoPlayer.Builder(this)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)

        // Fix #62: подключаем OkHttpDataSource с VK User-Agent.
        // Пытаемся получить OkHttpClient из SovaApp; если контекст ещё не
        // инициализирован (краш-репорт на ранних устройствах) — fallback
        // на стандартный DefaultHttpDataSource с UA "VKAndroidApp/...".
        val app = applicationContext as? SovaApp
        if (app != null) {
            try {
                val okHttp = app.httpClient
                // #64: OkHttpDataSource.Factory в media3 1.8.0 НЕ имеет
                // setAllowCrossProtocolRedirects (этот метод только на
                // DefaultHttpDataSource.Factory). http→https редиректы OkHttp
                // обрабатывает нативно — SovaApp.httpClient уже сконфигурирован
                // с .followRedirects(true).followSslRedirects(true) (SovaApp.kt:107-108),
                // так что отдельный флаг на datasource не нужен.
                val httpFactory = OkHttpDataSource.Factory(okHttp)
                    .setUserAgent(re.pinok.util.VkUserAgent.get(app))

                // Fix #76: CacheDataSource убран. Зашифрованные HLS-сегменты
                // в SimpleCache бесполезны — ключ AES-128 истекает быстрее,
                // чем пользователь перепрослушивает трек.
                // Авто-кеширование теперь в PlayerConnection.onPlaybackStateChanged:
                // при STATE_READY HLS-трек скачивается + расшифровывается +
                // склеивается в один .ts через TrackDownloadManager (в фоне).
                val dataSourceFactory: androidx.media3.datasource.DataSource.Factory =
                    DefaultDataSource.Factory(this, httpFactory)

                val mediaSourceFactory = DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
                playerBuilder.setMediaSourceFactory(mediaSourceFactory)
                AppLog.i("PlayerService", "OkHttpDataSource attached (VK UA, no stream cache — Fix #76)")
            } catch (e: Exception) {
                AppLog.e("PlayerService", "OkHttpDataSource setup failed, fallback to default", e)
            }
        }

        val player = playerBuilder.build()

        // Fix #96: EQ attach через Player.Listener.onAudioSessionIdChanged.
        // В media3 1.8.0 `player.audioSessionId` сразу после build() возвращает
        // C.AUDIO_SESSION_ID_UNSET (= 0) — audio sink ещё не выделен и реальный
        // sessionId появится только при первом воспроизведении. Раньше мы звали
        // attachOnce(0) в onCreate → skip → эквалайзер НИКОГДА не подключался
        // (в логе: "attachOnce: sessionId == 0 — skip" + "Equalizer attached once
        // to sessionId=0" — ложное сообщение об успехе).
        //
        // onAudioSessionIdChanged вызывается когда audio sink выделяет реальный
        // sessionId (после первого STATE_READY). attachOnce() идемпотентен —
        // повторные вызовы с тем же sessionId пропускаются, так что подписка
        // безопасна даже если коллбэк сработает несколько раз.
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0) {
                    EqualizerHelper.attachOnce(audioSessionId)
                    AppLog.i("PlayerService", "Equalizer attached to sessionId=$audioSessionId (onAudioSessionIdChanged)")
                }
            }
        })

        // Best-effort: вдруг audioSessionId уже выделен сразу после build()
        // (некоторые устройства/версии media3 так делают). Если нет —
        // onAudioSessionIdChanged выше сработает при первом воспроизведении.
        val playerSessionId = player.audioSessionId
        if (playerSessionId != 0) {
            EqualizerHelper.attachOnce(playerSessionId)
            AppLog.i("PlayerService", "Equalizer attached immediately to sessionId=$playerSessionId")
        } else {
            AppLog.d("PlayerService", "audioSessionId=0 right after build() — waiting for onAudioSessionIdChanged (Fix #96)")
        }

        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        // Fix #138: MediaSession with custom Callback (handles «Скачать» command)
        // and initial CustomLayout (download button shown on lock screen / Android Auto / Wear OS).
        // The onConnect() callback re-asserts both per-controller (some controllers
        // ignore the builder-level custom layout and only read what onConnect returns).
        val builder = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(buildDownloadButton(enabled = true)))
        if (sessionActivity != null) {
            builder.setSessionActivity(sessionActivity)
        }
        mediaSession = builder.build()

        // Subscribe to player state + download state to dynamically enable/disable
        // the download button on the lock screen while a download is in progress.
        startDownloadStateSubscriber()

        // Fix #287: подписка на смену audio output route. Без этого эквалайзер
        // перестаёт действовать после подключения Bluetooth A2DP (эффект отвязывается
        // от нового output, хотя объект жив). reattach() — no-op пока EQ не привязан.
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, audioRouteHandler)
    }

    /**
     * Fix #138: subscribes to PlayerConnection.playerState (for track changes)
     * and TrackDownloadManager.downloads (for download state changes). On each
     * emission calls [updateDownloadButton] which (if state changed) pushes a
     * new custom layout to MediaSession via [MediaSession.setCustomLayout].
     *
     * Two coroutines because combining two StateFlows via `combine` would only
     * emit when BOTH change; we want to react to either independently. The
     * [updateDownloadButton] function is idempotent (no-op if state unchanged).
     */
    private fun startDownloadStateSubscriber() {
        serviceScope.launch {
            PlayerConnection.playerState.collect { state ->
                val tid = state.currentTrack?.id
                if (tid != currentTrackId) {
                    currentTrackId = tid
                    updateDownloadButton()
                }
            }
        }
        serviceScope.launch {
            TrackDownloadManager.downloads.collect { _ ->
                updateDownloadButton()
            }
        }
    }

    /**
     * Recomputes the enabled state of the «Скачать» button based on the current
     * track + current download state, and pushes the updated custom layout to
     * [mediaSession] if the state changed.
     *
     * Button is DISABLED while the current track is downloading (status QUEUED
     * or DOWNLOADING) — prevents the user from spamming the button and starting
     * multiple concurrent downloads of the same track. Re-enabled when the
     * download completes, fails, or is removed. If the track is already
     * downloaded, the button stays ENABLED — [sessionCallback.onCustomCommand]
     * no-ops gracefully on a second press (returns RESULT_SUCCESS without
     * re-downloading).
     */
    private fun updateDownloadButton() {
        val tid = currentTrackId ?: return
        val state: DownloadState? = TrackDownloadManager.getDownloadState(tid)
        val isInProgress = state?.isInProgress == true
        val newEnabled = !isInProgress
        if (newEnabled != currentDownloadButtonEnabled) {
            currentDownloadButtonEnabled = newEnabled
            val button = buildDownloadButton(enabled = newEnabled)
            mediaSession?.setCustomLayout(listOf(button))
            AppLog.d(
                "PlayerService",
                "Download button updated: enabled=$newEnabled (trackId=$tid, status=${state?.status}, inProgress=$isInProgress)"
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Проверяем mediaSession == null отдельно (safe-call chain не ловит
        // случай когда сессия уже освобождена — null == false, 0 == false).
        // musicBackgroundPlay: если выключено — всегда стопаемся при уходе.
        //
        // #29 (build fix): Flow.first() — suspend функция, onTaskRemoved не suspend.
        // Оборачиваем в runBlocking (короткая операция — DataStore из in-memory cache).
        val bgPlay = try {
            kotlinx.coroutines.runBlocking {
                re.pinok.SovaApp.get().prefs.data.first().musicBackgroundPlay
            }
        } catch (_: Exception) { true }
        if (mediaSession == null || !bgPlay || player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        AppLog.i("PlayerService", "onDestroy")
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioRouteHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        EqualizerHelper.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /** Custom action for the «Скачать» button on lock screen / media notification. */
        const val ACTION_DOWNLOAD = "re.pinok.action.DOWNLOAD_TRACK"
        /** Fix #287: debounce reattach эквалайзера при смене audio route (мс). */
        private const val EQ_REATTACH_DEBOUNCE_MS = 600L
        /** Fix #342: увеличенный debounce для BT A2DP — codec negotiation ~1с. */
        private const val EQ_REATTACH_DEBOUNCE_BT_MS = 1200L
    }
}
