package re.pinok.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import re.pinok.util.AppLog

/**
 * W30-3 #VIDEO-BG-PLAYER: шина между видео-экраном (VideoPlayerScreen) и
 * [VideoPlaybackService] — фоновое воспроизведение видео БЕЗ картинки
 * (только аудиодорожка) с медиа-уведомлением и кнопками прокрутки «±N сек»
 * на lock-screen (шаг N — SovaPrefs.videoSeekStepSec, настройка 5/10/15).
 *
 * Паттерн: audio PlayerService (Fix #138) владеет своим ExoPlayer'ом, но у видео
 * плеер живёт в Activity (VideoPlayerScreen создаёт/релизит инстанс сам) —
 * поэтому сервис берёт ССЫЛКУ на тот же инстанс через эту шину (один процесс).
 *
 * Контракт вызовов из VideoPlayerScreen (docs/W30-VIDEO-IM-PARITY-PLAN.md §2.4):
 *  - onPlayerReady(context, player, title) — после создания/настройки ExoPlayer;
 *  - onBackgrounded(context) — Activity ушла в фон (onStop): если видео играет,
 *    поднимаем foreground-сервис (media3 рисует MediaStyle-уведомление);
 *  - onForegrounded() — Activity вернулась (onStart): foreground-уведомление
 *    больше не нужно, сервис гасим (не трогая воспроизведение);
 *  - onPlayerReleased() — плеер уничтожен: сбрасываем ссылку, гасим сервис.
 *
 * NULL-ЯВНО: поля nullable, ВСЕ обращения через явные if-проверки локальных val.
 */
object VideoPlaybackBus {

    /** Ссылка на video-ExoPlayer (владелец — VideoPlayerScreen). */
    @Volatile
    internal var playerRef: ExoPlayer? = null

    /** Заголовок для медиа-уведомления (название видео). */
    @Volatile
    internal var mediaTitle: String = "Видео"

    /** Application context (НЕ activity) — для интентов сервиса. */
    @Volatile
    private var appContext: Context? = null

    /** Сервис сейчас поднят (foreground) — чтобы не стартовать повторно. */
    @Volatile
    private var serviceRunning: Boolean = false

    fun onPlayerReady(context: Context, player: ExoPlayer, title: String) {
        appContext = context.applicationContext
        playerRef = player
        mediaTitle = if (title.isBlank()) "Видео" else title
        AppLog.d("VideoPlaybackBus", "onPlayerReady: $title")
    }

    fun onBackgrounded(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val player = playerRef
        // Уведомление имеет смысл только когда есть ЧТО слушать (играющее видео).
        if (player != null && player.isPlaying && !serviceRunning) {
            val intent = Intent(ctx, VideoPlaybackService::class.java)
            try {
                ContextCompat.startForegroundService(ctx, intent)
                serviceRunning = true
                AppLog.i("VideoPlaybackBus", "onBackgrounded: video continues in background (title=$mediaTitle)")
            } catch (e: Exception) {
                // startForegroundService может кинуть на некоторых ROM'ах при
                // запретах фоновых сервисов — честно логируем, не роняем UI.
                serviceRunning = false
                AppLog.w("VideoPlaybackBus", "startForegroundService failed: ${e.message}")
            }
        }
    }

    fun onForegrounded() {
        val ctx = appContext
        if (serviceRunning && ctx != null) {
            ctx.stopService(Intent(ctx, VideoPlaybackService::class.java))
            serviceRunning = false
            AppLog.d("VideoPlaybackBus", "onForegrounded: background service stopped")
        }
    }

    fun onPlayerReleased() {
        val ctx = appContext
        if (serviceRunning && ctx != null) {
            ctx.stopService(Intent(ctx, VideoPlaybackService::class.java))
        }
        serviceRunning = false
        playerRef = null
        AppLog.d("VideoPlaybackBus", "onPlayerReleased: bus cleared")
    }

    /** Вызывается сервисом при собственном уничтожении (system kill и т.п.). */
    internal fun notifyServiceStopped() {
        serviceRunning = false
    }
}
