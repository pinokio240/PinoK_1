// File: media/StoryVideoDownloadService.kt
package re.pinok.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import re.pinok.R
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog

/**
 * StoryVideoDownloadService — foreground-сервис для уведомлений о загрузке
 * ВИДЕО-историй (stories).
 *
 * Mirror [VideoDownloadService], но:
 *  - отдельный NOTIFICATION_ID (2002 — не конфликтует с 2001 video)
 *  - отдельный канал `story_video_downloads`
 *  - отдельные строковые ресурсы
 *
 * Сам сервис НЕ выполняет загрузку — это делает [StoryVideoDownloadManager].
 * Сервис нужен только для foreground-notification на Android 8+ (иначе система
 * убивает фоновый процесс загрузки).
 *
 * `silent=true` в [StoryVideoDownloadManager.enqueueDownload] пропускает запуск
 * сервиса (auto-cache-on-play не должно показывать уведомление).
 */
class StoryVideoDownloadService : Service() {

    companion object {
        private const val TAG = "StoryVideoDlService"
        private const val NOTIFICATION_ID = 2002
        private const val NOTIFICATION_CHANNEL_ID = "story_video_downloads"

        fun start(context: Context) {
            val intent = Intent(context, StoryVideoDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StoryVideoDownloadService::class.java))
        }
    }

    override fun onCreate() {
        AppLog.i(TAG, "onCreate()")
        ensureChannel()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.i(TAG, "onStartCommand")
        // Fix #233 (P1-9): try/catch вокруг startForeground — на Android 12+ может
        // бросить ForegroundServiceStartNotAllowedException при bg-start (Fix #141
        // применили только к MusicDownloadService). Без catch сервис крашится.
        try {
            startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        } catch (e: Exception) {
            AppLog.e(TAG, "startForeground() failed: ${e.javaClass.simpleName}: ${e.message}", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.i(TAG, "onDestroy")
        super.onDestroy()
    }

    fun updateNotification(activeCount: Int, progressPercent: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(activeCount, progressPercent))
    }

    private fun buildNotification(activeCount: Int, progressPercent: Int): Notification {
        val title = if (activeCount == 0) {
            getString(R.string.story_video_download_notification_complete_title)
        } else {
            getString(R.string.story_video_download_notification_active_title, activeCount)
        }

        val contentIntent = Intent(this, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(getString(R.string.story_video_download_notification_text, progressPercent))
            .setProgress(100, progressPercent, progressPercent == 0 && activeCount > 0)
            .setOngoing(activeCount > 0)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.story_video_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.story_video_download_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
