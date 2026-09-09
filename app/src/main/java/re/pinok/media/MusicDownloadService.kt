// File: media/MusicDownloadService.kt
package re.pinok.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import re.pinok.R
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog

/**
 * MusicDownloadService — foreground-сервис для уведомлений о загрузке музыки.
 *
 * Ранее наследовался от Media3 DownloadService (который был удалён в 1.8.0).
 * Теперь это простой [Service], который показывает уведомление пока
 * [TrackDownloadManager] имеет активные загрузки.
 */
class MusicDownloadService : Service() {

    /**
     * #DL-WAKELOCK (Fix #382): локи удержания CPU/Wi-Fi на время активных загрузок.
     *
     * Зачем: foreground-сервис сам по себе НЕ гарантирует CPU и Wi-Fi в Doze —
     * система может усыплять устройство между сетевыми пакетами (stall) и
     * резать Wi-Fi до low-latency-минимума, из-за чего HLS-загрузки виснут или
     * падают по таймауту. PARTIAL_WAKE_LOCK держит CPU, WifiLock
     * (WIFI_MODE_FULL_HIGH_PERF) держит Wi-Fi в высокопроизводительном режиме,
     * пока сервис жив (а жив он ровно пока есть активные загрузки —
     * см. maybeStopForegroundService в TrackDownloadManager).
     *
     * setReferenceCounted(false): один acquire/release на жизненный цикл сервиса
     * — onCreate/onDestroy симметричны, счётчик не нужен.
     */
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        private const val TAG = "MusicDownloadService"
        // Fix #152: NOTIFICATION_ID изменён с 1001 на 2001.
        // Media3 PlayerService (MediaSessionService) тоже использует notification
        // id=1001 (подтверждено logcat: onNotificationPosted id=1001 для ОБОИХ —
        // channel=music_downloads и channel=default_channel_id/category=transport/
        // groupKey=media3_group_key). Когда MusicDownloadService постит свою
        // нотификацию (прогресс загрузки) на id=1001, она ПЕРЕЗАПИСЫВАЕТ
        // MediaSession-нотификацию плеера → контролы плеера (play/pause/next/prev)
        // на lock screen исчезают, заменяясь прогресс-баром загрузки.
        // Разные notification ID = оба видны одновременно: плеер с контролами +
        // отдельная нотификация загрузки.
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "music_downloads"

        fun start(context: Context) {
            val intent = Intent(context, MusicDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MusicDownloadService::class.java))
        }

        /**
         * Fix #139: Static update — можно вызывать из TrackDownloadManager
         * без ссылки на instance сервиса. Постит/обновляет foreground-нотификацию
         * с текущим прогрессом.
         *
         * Если activeCount == 0 → нотификация покажет «Загрузка завершена»
         * кратко, после чего будет отменена в maybeStopForegroundService()
         * при остановке сервиса.
         * Если activeCount > 0 → «Загрузка музыки: N» с progress-bar.
         *
         * Без этого метода (Audit #40) нотификация навсегда оставалась в
         * стартовом состоянии 0%/indeterminate — пользователь не видел
         * признаков скачивания.
         */
        fun updateProgress(context: Context, activeCount: Int, progressPercent: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            // Idempotent channel creation — safety net если сервис ещё не запущен
            // (startForegroundService асинхронен, updateProgress может прийти раньше onCreate).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.music_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.music_download_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            try {
                nm.notify(NOTIFICATION_ID, buildNotificationStatic(context, activeCount, progressPercent))
            } catch (e: Exception) {
                // На Android 13+ без POST_NOTIFICATIONS nm.notify молча падает.
                // Логируем — не роняем скачивание.
                AppLog.w(TAG, "updateProgress: nm.notify failed: ${e.message}")
            }
        }

        /**
         * Fix #139: Static-билдер нотификации — usable из companion.
         * Логика идентична бывшему instance buildNotification, но использует
         * context.getString вместо getString (instance method).
         */
        private fun buildNotificationStatic(context: Context, activeCount: Int, progressPercent: Int): Notification {
            val title = if (activeCount == 0) {
                context.getString(R.string.music_download_notification_complete_title)
            } else {
                context.getString(R.string.music_download_notification_active_title, activeCount)
            }

            val contentIntent = Intent(context, MainActivity::class.java).let { intent ->
                PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.music_download_notification_text, progressPercent))
                .setProgress(100, progressPercent, progressPercent == 0 && activeCount > 0)
                .setOngoing(activeCount > 0)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    /**
     * Fix #141 (ForegroundServiceDidNotStartInTimeException crash):
     *
     * Crash scenario (log 2026-07-20 14:45:35):
     *   1. User listens to music, auto-cache runs in background.
     *   2. User clicks a track → playTrackList(1266 tracks, start=32).
     *   3. playTrackList calls TrackDownloadManager.enqueueDownload(firstTrack, silent=true)
     *      for the first track in the list (safeIndex > 0 → cache first).
     *   4. enqueueDownload → startForegroundService() → context.startForegroundService(intent)
     *      → schedules MusicDownloadService start on main thread.
     *   5. PARALLEL on main thread: audio.get network call blocks main thread for 7+ seconds
     *      (log: "audio.get ... 7357ms" + "Skipped 428 frames!").
     *   6. MusicDownloadService.onCreate() / onStartCommand() can't run because main thread
     *      is blocked → startForeground() never called within 5 sec ANR window.
     *   7. Android throws ForegroundServiceDidNotStartInTimeException → FATAL crash.
     *
     * Fix: call startForeground() IMMEDIATELY in onCreate() before super.onCreate(),
     * using a minimal pre-built notification. This guarantees the foreground state is
     * announced as soon as the service object is created — even if onStartCommand is
     * delayed by main-thread congestion. The onStartCommand() call to startForeground()
     * is kept as a redundant safety net (it's a no-op if already in foreground state).
     *
     * Note: Android framework guarantees onCreate() runs on main thread BEFORE
     * onStartCommand(), and onCreate() is the FIRST callback where the Service
     * instance exists. Calling startForeground() here is the earliest possible point.
     *
     * The pre-built notification is cached in companion so it doesn't allocate on
     * every service start — built once lazily.
     */
    override fun onCreate() {
        AppLog.i(TAG, "onCreate()")
        ensureChannel()
        // CRITICAL: startForeground() FIRST, before super.onCreate().
        // If super.onCreate() does any work that throws, we still need the foreground
        // state to be committed so the system doesn't kill us.
        try {
            startForeground(NOTIFICATION_ID, buildNotification(0, 0))
            AppLog.i(TAG, "startForeground() called in onCreate() (Fix #141)")
        } catch (e: Exception) {
            // On Android 12+ startForeground can throw ForegroundServiceStartNotAllowedException
            // if the service was started from background. We catch and log — the download
            // itself continues via TrackDownloadManager's coroutine scope (not bound to
            // the service lifecycle). The notification just won't show.
            AppLog.e(TAG, "startForeground() failed in onCreate(): ${e.javaClass.simpleName}: ${e.message}", e)
        }
        super.onCreate()
        // #DL-WAKELOCK (Fix #382): держим CPU + Wi-Fi пока сервис живёт (а живёт он
        // ровно пока есть активные загрузки — см. maybeStopForegroundService).
        // Каждый acquire в try/catch: сбой локов (нет разрешения, экзотическая
        // прошивка) НЕ должен ломать запуск сервиса — загрузки продолжатся, просто
        // с меньшей устойчивостью в Doze.
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PinoK:MusicDownload")
                lock.setReferenceCounted(false)
                lock.acquire()
                wakeLock = lock
                AppLog.i(TAG, "DL-WAKELOCK: PARTIAL_WAKE_LOCK acquired")
            } else {
                AppLog.w(TAG, "DL-WAKELOCK: PowerManager недоступен — wakelock пропущен")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "DL-WAKELOCK: wakelock acquire failed: ${e.message}")
        }
        try {
            val wm = getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                // WIFI_MODE_FULL_HIGH_PERF: помечен deprecated начиная с API 34,
                // но по-прежнему является рабочим режимом удержания Wi-Fi в
                // high-perf — подавляем только предупреждение компилятора.
                @Suppress("DEPRECATION")
                val lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PinoK:MusicDownloadWifi")
                lock.setReferenceCounted(false)
                lock.acquire()
                wifiLock = lock
                AppLog.i(TAG, "DL-WAKELOCK: WifiLock acquired (FULL_HIGH_PERF)")
            } else {
                AppLog.w(TAG, "DL-WAKELOCK: WifiManager недоступен — wifiLock пропущен")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "DL-WAKELOCK: wifiLock acquire failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.i(TAG, "onStartCommand")
        // Redundant safety net: if onCreate() already called startForeground(), this is
        // a no-op (Android just updates the notification). If for some reason onCreate's
        // startForeground failed (e.g. exception caught above), this is a second chance.
        try {
            startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        } catch (e: Exception) {
            AppLog.e(TAG, "startForeground() failed in onStartCommand(): ${e.javaClass.simpleName}: ${e.message}", e)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.i(TAG, "onDestroy")
        // #DL-WAKELOCK (Fix #382): корректно релизим локи ПЕРЕД снятием foreground.
        // release() только если лок реально held — иначе Android кидает
        // RuntimeException("WakeLock under-locked"). С null-проверками и try/catch:
        // onDestroy не должен ронять процесс даже на экзотике.
        val heldWakeLock = wakeLock
        if (heldWakeLock != null) {
            try {
                if (heldWakeLock.isHeld) heldWakeLock.release()
                AppLog.i(TAG, "DL-WAKELOCK: PARTIAL_WAKE_LOCK released")
            } catch (e: Exception) {
                AppLog.w(TAG, "DL-WAKELOCK: wakelock release failed: ${e.message}")
            }
            wakeLock = null
        }
        val heldWifiLock = wifiLock
        if (heldWifiLock != null) {
            try {
                if (heldWifiLock.isHeld) heldWifiLock.release()
                AppLog.i(TAG, "DL-WAKELOCK: WifiLock released")
            } catch (e: Exception) {
                AppLog.w(TAG, "DL-WAKELOCK: wifiLock release failed: ${e.message}")
            }
            wifiLock = null
        }
        // Fix #143: явно отменяем notification и выходим из foreground-режима.
        // Раньше onDestroy только вызывал super.onDestroy() — notification с
        // «Загрузка завершена / 100%» оставалась висеть forever, потому что
        // stopService() убивает сервис, но НЕ отменяет его notification.
        // stopForeground(STOP_FOREGROUND_REMOVE) явно снимает foreground-флаг
        // и удаляет notification. Дублируем nm.cancel() для надёжности на
        // старых Android (<24) где STOP_FOREGROUND_REMOVE не работает.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "stopForeground failed: ${e.message}")
        }
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            AppLog.w(TAG, "nm.cancel failed: ${e.message}")
        }
        super.onDestroy()
    }

    /**
     * Обновить уведомление (instance — делегирует в static updateProgress).
     *
     * Fix #139: теперь обновление прогресса доступно ИЗ TrackDownloadManager
     * через статический `MusicDownloadService.updateProgress(context, ...)`.
     * Audit #40 (TODO Sprint 5) — закрыт: updateState() в TrackDownloadManager
     * теперь дёргает статический updateProgress → нотификация обновляется
     * на каждом прогресс-тике (HLS-сегмент / direct-MP3 chunk).
     */
    fun updateNotification(activeCount: Int, progressPercent: Int) {
        updateProgress(this, activeCount, progressPercent)
    }

    private fun buildNotification(activeCount: Int, progressPercent: Int): Notification {
        // Fix #139: delegate в static-билдер — единая логика для instance/companion.
        return buildNotificationStatic(this, activeCount, progressPercent)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // audit High #6: unsafe cast → безопасный as? с ранним возвратом
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.music_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.music_download_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}