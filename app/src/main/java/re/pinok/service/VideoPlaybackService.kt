package re.pinok.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
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
    }

    override fun onCreate() {
        super.onCreate()
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
        try {
            mediaSession = builder.build()
        } catch (e: IllegalStateException) {
            AppLog.e("VideoPlaybackService", "onCreate: MediaSession build failed: ${e.message} — stopSelf", e)
            stopSelf()
            return
        } catch (e: IllegalArgumentException) {
            AppLog.e("VideoPlaybackService", "onCreate: MediaSession build failed: ${e.message} — stopSelf", e)
            stopSelf()
            return
        }
        AppLog.i("VideoPlaybackService", "onCreate: session ready (title=${VideoPlaybackBus.mediaTitle}, step=$seekStepSec)")
    }

    /**
     * Кнопки «−N сек» / «+N сек» в custom layout (Fix #138-паттерн CommandButton).
     * Иконки — системные android.R.drawable.ic_media_rew / ic_media_ff (гарантированно
     * существуют на всех уровнях API; на lock-screen рендерятся системой).
     */
    private fun buildSeekButton(back: Boolean): CommandButton = CommandButton.Builder()
        .setSessionCommand(if (back) seekBackCommand else seekForwardCommand)
        .setDisplayName(if (back) "−${seekStepSec} сек" else "+${seekStepSec} сек")
        .setIconResId(if (back) android.R.drawable.ic_media_rew else android.R.drawable.ic_media_ff)
        .build()

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
        VideoPlaybackBus.notifyServiceStopped()
        super.onDestroy()
    }
}
