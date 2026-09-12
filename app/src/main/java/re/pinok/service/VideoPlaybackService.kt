package re.pinok.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
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
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.util.AppLog

/**
 * W30-3 #VIDEO-BG-PLAYER: фоновое воспроизведение видео БЕЗ картинки —
 * MediaSessionService (media3) поверх video-ExoPlayer'а из [VideoPlaybackBus].
 *
 * Требование юзера: «на экране блокировки должен показываться свой проигрыватель,
 * как аудио, без видео, с функцией прокрутки в фоне видео на определенное
 * количество секунд как вперед, так и назад» + настройка шага 5с/10с/15с
 * (Настройки → Видео → «Фоновое воспроизведение», SovaPrefs.videoSeekStepSec).
 *
 * Реализация по паттерну audio PlayerService (Fix #138): media3 сам строит
 * MediaStyle-уведомление из сессии (PlayerService не рисует уведомление вручную),
 * кастомные кнопки прокрутки задаются через setCustomLayout — они отображаются
 * в уведомлении и на lock-screen (проверено Fix #138 кнопкой «Скачать»).
 *
 * Сервис НЕ владеет плеером: ссылку выдаёт VideoPlaybackBus, релизит экран.
 * PlayerRef null (экран закрыт) → stopSelf — честный no-op.
 */
class VideoPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Кэш шага прокрутки (сек) — обновляется collect'ом prefs.data. */
    @Volatile
    private var seekStepSec: Int = 10

    companion object {
        private const val ACTION_SEEK_BACK = "re.pinok.video.SEEK_BACK"
        private const val ACTION_SEEK_FORWARD = "re.pinok.video.SEEK_FORWARD"
        private val seekBackCommand = SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY)
        private val seekForwardCommand = SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY)

        // FIX #VIDEO-FG-TIME: канал/id служебной заглушки foreground (см.
        // promoteToForegroundImmediately).
        // #LOCKSCREEN-FIX (2026-09-11): ID УНИКАЛЬНЫЙ и общий для заглушки и
        // media3-нотификации видео — провайдер (см. onCreate,
        // setMediaNotificationProvider) строит MediaStyle-уведомление с ЭТИМ же
        // ID → startForeground заменяет заглушку 1:1 (startForeground/notify с
        // тем же ID обновляют нотификацию на месте, без дублей в шторке).
        // Отличать от 1001 (DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
        // media3 1.8.0) ОБЯЗАТЕЛЬНО: у аудио PlayerService провайдер дефолтный
        // (=1001) — оба сервиса живут в одном процессе, одинаковый ID означал
        // бы перезапись нотификаций друг друга при одновременном аудио+видео.
        private const val CHANNEL_ID_PLACEHOLDER = "pinok_video_fg_placeholder"
        private const val NOTIF_ID_PLACEHOLDER = 41102
    }

    // @OptIn: весь notification-provider API media3 — @UnstableApi (см. комментарий
    // у setMediaNotificationProvider ниже). Первый @OptIn в проекте — осознанно:
    // альтернатива (ручное MediaStyle-уведомление) требует легаси androidx.media
    // и собственных PendingIntent'ов — риск выше.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // FIX #VIDEO-FG-TIME (краш 2026-09-10 23:39:12.911 и 23:41:49.235, HOTWAV
        // Cyber 15, API 33): ContextCompat.startForegroundService из
        // VideoPlaybackBus.onBackgrounded создаёт СИСТЕМНОЕ обязательство вызвать
        // startForeground() в короткое окно — иначе FATAL
        // RemoteServiceException$ForegroundServiceDidNotStartInTimeException.
        // Обязательство должно быть погашено ДАЖЕ если сервис немедленно
        // останавливается (гонка из лога: экран уходит с композиции →
        // onPlayerReleased → stopService приходит РАНЬШЕ, чем media3 продвинет
        // нас в foreground — media3 продвигает только на isPlaying-переходе,
        // которого при релизнутом плеере уже не будет).
        // Решение: продвигаемся в foreground САМИ первым же действием onCreate —
        // тихая MIN-приоритетная заглушка; при живом воспроизведении media3
        // заменит её MediaStyle-уведомлением, при немедленном stopSelf — заглушка
        // исчезает вместе с сервисом. Все пути stopSelf ниже (playerRef==null,
        // build-fail, onTaskRemoved, внешний stopService) теперь легальны.
        promoteToForegroundImmediately()

        val player: ExoPlayer? = VideoPlaybackBus.playerRef
        if (player == null) {
            // Экран уже релизнул плеер (гонка при быстром выходе) — сервиса нет.
            AppLog.w("VideoPlaybackService", "onCreate: no player from bus — stopSelf")
            stopSelf()
            return
        }

        // Живой кэш шага прокрутки из настроек (правка применилась — кнопки и
        // следующая прокрутка используют новый шаг без пересоздания сервиса).
        val app = applicationContext as? SovaApp
        if (app != null) {
            serviceScope.launch {
                app.prefs.data.collect { snapshot ->
                    val newStep = snapshot.videoSeekStepSec
                    if (newStep != seekStepSec && newStep in setOf(5, 10, 15)) {
                        seekStepSec = newStep
                        // Перестраиваем layout, чтобы на кнопках был актуальный «±N сек».
                        val session = mediaSession
                        if (session != null) {
                            session.setCustomLayout(listOf(buildSeekButton(back = true), buildSeekButton(back = false)))
                        }
                        AppLog.i("VideoPlaybackService", "Seek step changed → $newStep sec (layout rebuilt)")
                    }
                }
            }
        } else {
            AppLog.w("VideoPlaybackService", "applicationContext is not SovaApp — step stays default")
        }

        // NULL-ЯВНО: getLaunchIntentForPackage может вернуть null — явная проверка.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        var sessionActivity: android.app.PendingIntent? = null
        if (launchIntent != null) {
            sessionActivity = android.app.PendingIntent.getActivity(
                this, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

        // Fix #VIDEO-SESSION-ID-COLLISION (кейс ciber.txt): media3 требует уникальный
        // session ID НА ПРОЦЕСС. Без setId() ID = "" у ОБОИХ сервисов (аудио
        // PlayerService:404 и этот) → когда в процессе жива аудио-сессия и стартует
        // видео-сервис (lock-screen при играющем видео), второй build() с тем же
        // пустым ID кидает IllegalStateException: "Session ID must be unique" →
        // FATAL EXCEPTION. Явный уникальный ID снимает коллизию (setId есть с media3 1.0).
        val builder = MediaSession.Builder(this, player)
            .setId("pinok-video-session")
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(buildSeekButton(back = true), buildSeekButton(back = false)))
        if (sessionActivity != null) {
            builder.setSessionActivity(sessionActivity)
        }
        // Defensive-гвард: build() может кинуть IllegalStateException (дубль session
        // ID — кейс ciber.txt) или IllegalArgumentException (невалидный аргумент).
        // Сервис опционален для UX — отказ build() НЕ должен ронять приложение:
        // честно логируем причину и гасим сервис (плеер НЕ релизим — им владеет
        // видео-экран, сервис держит только ссылку).
        // NULL-ЯВНО: build() либо отдаёт сессию, либо кидает — локальная non-null
        // val, поле остаётся nullable (onDestroy/гонки), без !!.
        val builtSession: MediaSession = try {
            builder.build()
        } catch (e: IllegalStateException) {
            AppLog.e("VideoPlaybackService", "onCreate: MediaSession build failed: ${e.message} — stopSelf", e)
            stopSelf()
            return
        } catch (e: IllegalArgumentException) {
            AppLog.e("VideoPlaybackService", "onCreate: MediaSession build failed: ${e.message} — stopSelf", e)
            stopSelf()
            return
        }
        mediaSession = builtSession
        // #LOCKSCREEN-FIX: явный провайдер с уникальным NOTIF_ID_PLACEHOLDER —
        // MediaStyle-нотификация видео постится с тем же ID, что и заглушка
        // (замена 1:1 без дублей в шторке) и НЕ конфликтует с аудио
        // PlayerService (его провайдер дефолтный — ID 1001; оба сервиса в одном
        // процессе, одинаковый ID = перезапись нотификаций друг друга).
        // Провайдер — тот же DefaultMediaNotificationProvider, что media3
        // использует по умолчанию (MediaStyle, канал, действия, bitmap-ар
        // — всё его поведение); отличается ТОЛЬКО ID. Весь provider-API
        // @UnstableApi (DefaultMediaNotificationProvider, MediaNotification.Provider,
        // setMediaNotificationProvider) — потому @OptIn на onCreate.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(applicationContext)
                .setNotificationId(NOTIF_ID_PLACEHOLDER)
                .build(),
        )
        // #LOCKSCREEN-FIX (жалоба 2026-09-11 «lock-screen плеер как у аудио — не работает»):
        // РЕГИСТРАЦИЯ сессии в сервисе. MediaSessionService узнаёт о сессии только
        // двумя путями: onGetSession() (вызывается при BIND MediaController'а или
        // MEDIA_BUTTON-интенте в onStartCommand) либо явный addSession(). Аудио
        // PlayerService работает именно через bind: PlayerConnection подключается
        // MediaController по SessionToken → onBind → onGetSession → addSession →
        // MediaNotificationManager создаёт внутренний notification-controller и
        // САМ постит MediaStyle-уведомление (lock-screen плеер). Видео же
        // стартует startForegroundService с «голым» интентом (VideoPlaybackBus,
        // без action и без bind) → onStartCommand не проходит ни одну ветку
        // (isMediaAction=isCustomAction=false) → сессия НИКОГДА не попадала в
        // сервис → media3 никогда не строил медиа-нотификацию: в шторке висела
        // только тихая MIN-заглушка #VIDEO-FG-TIME, на lock-screen плеера не
        // было вовсе. addSession() регистрирует сессию напрямую: внутренний
        // controller подключается, onConnected(shouldShowNotification=true при
        // непустом timeline) → onUpdateNotificationInternal → media3 сам
        // продвигается в foreground своей MediaStyle-нотификацией (play/pause +
        // кнопки ±N сек из custom layout) — она же заменяет заглушку 1:1
        // (общий ID 1001). Идемпотентно: повторный addSession той же сессии
        // игнорируется (guard old==null в исходнике media3).
        addSession(builtSession)
        AppLog.i("VideoPlaybackService", "onCreate: session ready + added to service (title=${VideoPlaybackBus.mediaTitle}, step=$seekStepSec)")

        // #VIDEO-BG-KEEP (2026-09-12): видео доиграло, пока приложение в фоне —
        // прекращаем сервис; осиротевший плеер релизится в onDestroy
        // (releaseOrphanedPlayer, guard по screenAttached — если экран уже
        // (пере)присоединился, плеер живёт дальше). Без этого после ENDED
        // уведомление/сессия висели до смерти процесса.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    AppLog.i("VideoPlaybackService", "STATE_ENDED — stopSelf (orphan release в onDestroy)")
                    stopSelf()
                }
            }
        })
    }

    /**
     * FIX #VIDEO-FG-TIME: немедленный startForeground с тихой заглушкой —
     * гашение системного обязательства из startForegroundService.
     * ServiceCompat.startForeground с явным типом mediaPlayback (обязателен на
     * API 34+; permission FOREGROUND_SERVICE_MEDIA_PLAYBACK есть в манифесте).
     * Всё в try/catch: сервис опционален для UX — отказ нотификации не должен
     * ронять приложение (класс defensive-гвардов onCreate).
     */
    private fun promoteToForegroundImmediately() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_PLACEHOLDER,
                        "Фоновое видео (служебное)",
                        NotificationManager.IMPORTANCE_MIN,
                    ).apply { setShowBadge(false) },
                )
            }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_PLACEHOLDER)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(VideoPlaybackBus.mediaTitle)
                .setContentText("Воспроизведение видео")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setShowWhen(false)
                .setOngoing(true)
                .build()
            ServiceCompat.startForeground(
                this,
                NOTIF_ID_PLACEHOLDER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            AppLog.d(
                "VideoPlaybackService",
                "startForeground: placeholder promoted (обязательство startForegroundService погашено)",
            )
        } catch (e: Exception) {
            AppLog.w("VideoPlaybackService", "startForeground placeholder failed: ${e.message}")
        }
    }

    /**
     * Кнопки «−N сек» / «+N сек» в custom layout (Fix #138-паттерн CommandButton).
     *
     * W36 #MEDIA3-DEPRECATION: CommandButton.Builder() и setIconResId(int)
     * deprecated с media3 1.5.0 — замена по официальному javadoc androidx/media
     * (CommandButton.java): иконка задаётся конструктором Builder(@Icon int),
     * «A separate resource id via setIconResId is no longer required unless for
     * ICON_UNDEFINED». Для шага 5/10/15 есть встроенные константы
     * ICON_SKIP_BACK_N / ICON_SKIP_FORWARD_N — рендерятся с цифрой («−10»/«+10»)
     * на lock-screen, вместо системных заглушек ic_media_rew/ff.
     * Ветки else — defensive (seekStepSec гвардится в {5,10,15} и настройкой,
     * и collect'ом prefs).
     */
    private fun buildSeekButton(back: Boolean): CommandButton {
        val icon = if (back) {
            when (seekStepSec) {
                5 -> CommandButton.ICON_SKIP_BACK_5
                10 -> CommandButton.ICON_SKIP_BACK_10
                15 -> CommandButton.ICON_SKIP_BACK_15
                else -> CommandButton.ICON_SKIP_BACK
            }
        } else {
            when (seekStepSec) {
                5 -> CommandButton.ICON_SKIP_FORWARD_5
                10 -> CommandButton.ICON_SKIP_FORWARD_10
                15 -> CommandButton.ICON_SKIP_FORWARD_15
                else -> CommandButton.ICON_SKIP_FORWARD
            }
        }
        return CommandButton.Builder(icon)
            .setSessionCommand(if (back) seekBackCommand else seekForwardCommand)
            .setDisplayName(if (back) "−${seekStepSec} сек" else "+${seekStepSec} сек")
            .build()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Fix #138-buildfix: в Media3 1.4.0 нет ConnectionResult.Builder() —
            // единственный путь: статический ConnectionResult.accept(commands, playerCommands).
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(seekBackCommand)
                .add(seekForwardCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val session = mediaSession
            if (session == null) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
            val player = session.player
            return when (customCommand.customAction) {
                ACTION_SEEK_BACK -> {
                    val target = (player.currentPosition - seekStepSec * 1000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    AppLog.d("VideoPlaybackService", "seek back ${seekStepSec}s → $target ms")
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SEEK_FORWARD -> {
                    val duration = player.duration
                    val rawTarget = player.currentPosition + seekStepSec * 1000L
                    // duration может быть C.TIME_UNSET (—1) у live-потоков — тогда не клампим.
                    val target = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
                    player.seekTo(target)
                    AppLog.d("VideoPlaybackService", "seek forward ${seekStepSec}s → $target ms")
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Паттерн PlayerService: свайп приложения из recents — если видео НЕ играет,
        // сервис не нужен. suspend collect'ов тут нет, поэтому без runBlocking.
        val player: ExoPlayer? = VideoPlaybackBus.playerRef
        val playing = player != null && player.isPlaying
        if (!playing) {
            AppLog.i("VideoPlaybackService", "onTaskRemoved: not playing → stopSelf")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        AppLog.i("VideoPlaybackService", "onDestroy")
        val session = mediaSession
        if (session != null) {
            session.release()
        }
        mediaSession = null
        serviceScope.cancel()
        // #VIDEO-BG-KEEP: если экран не присоединён, плеер осиротел (доиграл в
        // фоне / свайп задачи) — релизим здесь, иначе инстанс протечёт до смерти
        // процесса. При присоединённом экране guard вернёт false — плеер живёт.
        val orphanReleased = VideoPlaybackBus.releaseOrphanedPlayer()
        if (orphanReleased) {
            AppLog.i("VideoPlaybackService", "onDestroy: orphaned player released")
        }
        VideoPlaybackBus.notifyServiceStopped()
        super.onDestroy()
    }
}
