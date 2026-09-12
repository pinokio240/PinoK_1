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
 *  - onPlayerReady(context, player, title, videoKey) — после создания/настройки ExoPlayer;
 *  - onBackgrounded(context) — Activity ушла в фон (onStop): если видео играет,
 *    поднимаем foreground-сервис (media3 рисует MediaStyle-уведомление);
 *  - onForegrounded() — Activity вернулась (onStart): foreground-уведомление
 *    больше не нужно, сервис гасим (не трогая воспроизведение);
 *  - onPlayerReleased() — плеер уничтожен: сбрасываем ссылку, гасим сервис.
 *
 * #VIDEO-BG-KEEP (2026-09-12, жалоба «фоновое видео не работает»): на ROM'ах с
 * агрессивной политикой активити («Не сохранять действия» и т.п.) система
 * УНИЧТОЖАЕТ MainActivity сразу после onStop — композиция видео-экрана
 * умирает, и прежний контракт «экран всегда релизит плеер» убивал
 * воспроизведение через ~200 мс после ухода в фон (logcat 2026-09-12 20:04:43:
 * onBackgrounded → VideoPlaybackService onCreate → dispose композиции →
 * onPlayerReleased → ExoPlayer Release → сервис onDestroy). Теперь у шины
 * ЖИЗНЕННЫЙ ЦИКЛ ВЛАДЕНИЯ:
 *  - экран (пере)присоединился: markScreenAttached + onPlayerReady;
 *  - dispose при активном приложении (back-навигация/смена экрана):
 *    onPlayerReleased — как прежде, полный teardown;
 *  - dispose при 0 активити (система убила активити в фоне):
 *    onScreenDetachedInBackground — плеер ЖИВЁТ и играет дальше, сервис держит
 *    MediaSession/уведомление; при возврате экран переиспользует инстанс
 *    через livePlayerFor(key видео) — бесшовно, без рестарта;
 *  - сервис при STATE_ENDED в фоне / свайпе задачи: stopSelf, а onDestroy
 *    релизит осиротевший плеер (releaseOrphanedPlayer, guard по screenAttached).
 *
 * NULL-ЯВНО: поля nullable, ВСЕ обращения через явные if-проверки локальных val.
 */
object VideoPlaybackBus {

    /** Ссылка на video-ExoPlayer (владение — жизненный цикл шины, см. класс-док). */
    @Volatile
    internal var playerRef: ExoPlayer? = null

    /** Заголовок для медиа-уведомления (название видео). */
    @Volatile
    internal var mediaTitle: String = "Видео"

    /**
     * #VIDEO-BG-KEEP: ключ видео (тот же, что PlaybackPositionStore.videoKey) —
     * идентификация инстанса при переиспользовании после пересоздания активити.
     */
    @Volatile
    internal var videoKey: String = ""

    /** #VIDEO-BG-KEEP: жива ли сейчас композиция видео-экрана (экран-владелец). */
    @Volatile
    internal var screenAttached: Boolean = false

    /** Application context (НЕ activity) — для интентов сервиса. */
    @Volatile
    private var appContext: Context? = null

    /** Сервис сейчас поднят (foreground) — чтобы не стартовать повторно. */
    @Volatile
    private var serviceRunning: Boolean = false

    fun onPlayerReady(context: Context, player: ExoPlayer, title: String, videoKey: String) {
        appContext = context.applicationContext
        // #VIDEO-BG-KEEP: подмена инстанса при живом старом (осиротевший фоновый
        // плеер + пользователь открыл ДРУГОЕ видео) — гасим сервис со старым
        // уведомлением и релизим старый плеер (паттерн onPlayerReleased:
        // stopService → release; media3-сессия переживает release плеера).
        val previous = playerRef
        if (previous != null && previous !== player) {
            val ctx = appContext
            if (serviceRunning && ctx != null) {
                ctx.stopService(Intent(ctx, VideoPlaybackService::class.java))
                serviceRunning = false
                AppLog.d("VideoPlaybackBus", "onPlayerReady: previous video service stopped")
            }
            try {
                previous.release()
                AppLog.i("VideoPlaybackBus", "onPlayerReady: previous player released")
            } catch (e: Exception) {
                AppLog.w("VideoPlaybackBus", "onPlayerReady: previous release failed: ${e.message}")
            }
        }
        playerRef = player
        mediaTitle = if (title.isBlank()) "Видео" else title
        this.videoKey = videoKey
        screenAttached = true
        AppLog.d("VideoPlaybackBus", "onPlayerReady: $title (key=$videoKey)")
    }

    /**
     * #VIDEO-BG-KEEP: живой плеер с тем же видео — для переиспользования
     * экраном после пересоздания активити. null — нет плеера или это другое
     * видео (тогда экран создаёт свой инстанс как прежде).
     */
    fun livePlayerFor(key: String): ExoPlayer? {
        val player = playerRef
        if (player == null) return null
        if (videoKey != key) return null
        return player
    }

    /**
     * #VIDEO-BG-KEEP: экран (пере)присоединился — синхронный вызов из
     * DisposableEffect, раньше любых жизненных событий. Защищает
     * [releaseOrphanedPlayer] от гонок при возврате приложения.
     */
    fun markScreenAttached() {
        screenAttached = true
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

    /**
     * #VIDEO-BG-TOGGLE (волна 39): немедленное применение выключенного тумблера
     * «Фоновое воспроизведение» (Настройки → Видео). Вызывается из SettingsScreen
     * при переводе тумблера в OFF, пока видео играет в фоне: гасим сервис
     * (уведомление/lock-screen плеер исчезают) и ставим плеер на паузу —
     * семантика «фоновое воспроизведение отключено». Плеер НЕ релизим: при
     * возврате в приложение экран переиспользует инстанс через [livePlayerFor].
     */
    fun disableBackgroundNow() {
        val ctx = appContext
        if (serviceRunning && ctx != null) {
            ctx.stopService(Intent(ctx, VideoPlaybackService::class.java))
        }
        serviceRunning = false
        val player = playerRef
        if (player != null) {
            try {
                player.pause()
                AppLog.i("VideoPlaybackBus", "#VIDEO-BG-TOGGLE: background playback disabled — player paused, service stopped")
            } catch (e: Exception) {
                AppLog.w("VideoPlaybackBus", "disableBackgroundNow: pause failed: ${e.message}")
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
        screenAttached = false
        AppLog.d("VideoPlaybackBus", "onPlayerReleased: bus cleared")
    }

    /**
     * #VIDEO-BG-KEEP: экран уничтожен, пока приложение в фоне (система убила
     * активити — «Не сохранять действия»/агрессивный ROM; logcat 2026-09-12).
     * Плеер НЕ релизим: звук продолжается, сервис держит MediaSession и
     * уведомление. При возврате экран переиспользует инстанс через
     * [livePlayerFor] — воспроизведение не прерывается.
     */
    fun onScreenDetachedInBackground() {
        screenAttached = false
        AppLog.i(
            "VideoPlaybackBus",
            "onScreenDetachedInBackground: player keeps playing (title=$mediaTitle, key=$videoKey)",
        )
    }

    /**
     * #VIDEO-BG-KEEP: релиз осиротевшего плеера — сервис завершается
     * (STATE_ENDED в фоне, свайп задачи из recents) и экран не присоединён.
     * Возвращает true, если плеер был освобождён (для лога сервиса).
     */
    internal fun releaseOrphanedPlayer(): Boolean {
        if (screenAttached) return false
        val player = playerRef
        if (player == null) return false
        playerRef = null
        try {
            player.release()
            AppLog.i("VideoPlaybackBus", "releaseOrphanedPlayer: released (key=$videoKey)")
        } catch (e: Exception) {
            AppLog.w("VideoPlaybackBus", "releaseOrphanedPlayer: release failed: ${e.message}")
        }
        return true
    }

    /** Вызывается сервисом при собственном уничтожении (system kill и т.п.). */
    internal fun notifyServiceStopped() {
        serviceRunning = false
    }
}
