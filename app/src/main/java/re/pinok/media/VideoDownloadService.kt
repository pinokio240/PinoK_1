// File: media/VideoDownloadService.kt
package re.pinok.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import re.pinok.R
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog

/**
 * VideoDownloadService — foreground-сервис для уведомлений о загрузке видео.
 *
 * Ранее наследовался от Media3 DownloadService (который был удалён в 1.8.0).
 * Теперь это простой [Service], который показывает уведомление пока
 * [VideoDownloadManager] имеет активные загрузки.
 */
class VideoDownloadService : Service() {

    companion object {
        private const val TAG = "VideoDownloadService"
        // Fix #233 (P1-9): ранее 2001 — коллизия с MusicDownloadService (тоже 2001).
        // Уведомления перетирались: запуск Music-загрузки убивал Video-нотификацию
        // и наоборот. Теперь Music=2001, Story=2002, Video=2003 — все уникальны.
        private const val NOTIFICATION_ID = 2003
        private const val NOTIFICATION_CHANNEL_ID = "video_downloads"

        fun start(context: Context) {
            val intent = Intent(context, VideoDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VideoDownloadService::class.java))
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
        // бросить ForegroundServiceStartNotAllowedException (Fix #141 применили
        // только к MusicDownloadService). Без catch сервис крашится при bg-start.
        try {
            // P4.1: ServiceCompat.startForeground с указанием типа — на API 34+
            // type обязателен для сервисов с foregroundServiceType в манифесте
            // (тут dataSync). helper ниже диспатчит корректно по API.
            startForegroundCompat(NOTIFICATION_ID, buildNotification(0, 0))
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

    /**
     * Обновить уведомление.
     * Audit #40: метод НЕ вызывается из VideoDownloadManager — прогресс-уведомление
     * не обновляется (остаётся в стартовом состоянии 0%/indeterminate).
     * Для активации нужно: в VideoDownloadManager.downloadFile
     * вызывать `VideoDownloadService.start(this)` + `serviceInstance.updateNotification(...)`.
     * Пока это TODO (Sprint 5).
     */
    fun updateNotification(activeCount: Int, progressPercent: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(activeCount, progressPercent))
    }

    private fun buildNotification(activeCount: Int, progressPercent: Int): Notification {
        val title = if (activeCount == 0) {
            getString(R.string.video_download_notification_complete_title)
        } else {
            getString(R.string.video_download_notification_active_title, activeCount)
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
            .setContentText(getString(R.string.video_download_notification_text, progressPercent))
            .setProgress(100, progressPercent, progressPercent == 0 && activeCount > 0)
            .setOngoing(activeCount > 0)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // audit High #6: unsafe cast → безопасный as? с ранним возвратом
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.video_download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.video_download_channel_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    // P4.1: ServiceCompat.startForeground с указанием foregroundServiceType.
    //
    // Зачем: 2-arg startForeground(id, notification) deprecated на Android 14+
    // (API 34+) для сервисов с foregroundServiceType в манифесте. У нас
    // android:foregroundServiceType="dataSync" (см. AndroidManifest.xml) —
    // на API 34+ нужно явно передать type в 3-arg startForeground, иначе
    // поведение не определено (manifest-тип работает как fallback, но
    // гугл предупреждает о возможных изменениях).
    //
    // Как: ServiceCompat.startForeground сам диспатчит по API:
    //   - API 34+: вызывает Service.startForeground(id, notif, type) (новый 3-arg).
    //   - API 29-33: вызывает Service.startForeground(id, notif, type) (старый 3-arg).
    //   - API < 29 (minSdk=24): вызывает Service.startForeground(id, notif) (2-arg,
    //     type полностью игнорируется — foregroundServiceType в манифесте
    //     учитывается системой автоматически).
    //
    // На API < 34 передаём type=0 — это эквивалентно 2-arg вызову (system берёт
    // тип из манифеста). На API 34+ передаём FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    // т.к. он обязателен и должен совпадать с одним из типов манифеста (dataSync).
    //
    // minSdk=24, поэтому НЕ обёрнуто в if (SDK_INT >= Q) — ServiceCompat сам
    // выбирает нужный dispatch (см. внутр. Api29Impl/Api34Impl в androidx.core).
    private fun startForegroundCompat(id: Int, notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, id, notification, type)
    }
}