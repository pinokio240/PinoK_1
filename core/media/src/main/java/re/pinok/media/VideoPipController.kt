// File: media/VideoPipController.kt
package re.pinok.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import re.pinok.util.AppLog

/**
 * Статический мост между VideoPlayerScreen / OkWebViewPlayer (Compose) и
 * MainActivity (Activity).
 *
 * VideoPlayerScreen / OkWebViewPlayer вызывает [requestPip] / [setPipEnabled] —
 * MainActivity вызывает [enterPipIfEnabled] из onUserLeaveHint().
 *
 * Fix #142 (2026-08-03): PiP теперь работает и для OkWebViewPlayer (YouTube,
 * Instagram, external iframe). РАНЬШЕ только VideoPlayerScreen (нативный
 * ExoPlayer) регистрировал PiP. Теперь оба плеера регистрируются через
 * VideoPipController. togglePlayPause callback у WebView-плеера дёргает
 * JS `video.play()/pause()` вместо ExoPlayer.pause()/play().
 *
 * Также Fix #142 добавил `android:supportsPictureInPicture="true"` в манифест
 * (AndroidManifest.xml). БЕЗ этого атрибута на MIUI/OneUI/EMUI
 * enterPictureInPictureMode() молча fail. На AOSP работает и без флага.
 *
 * ## «Разрешение для поверхностного отображения»:
 *
 * PiP НЕ требует SYSTEM_ALERT_WINDOW permission на Android 8+ (API 26+).
 * Это отдельный системный механизм (PictureInPictureParams), не overlay window.
 * Система сама управляет PiP-окном — приложение только запрашивает переход.
 *
 * На Android < 8 (API < 26) PiP не поддерживается вообще — [requestPip] и
 * [enterPipIfEnabled] silently no-op (проверка Build.VERSION.SDK_INT).
 *
 * На Android 12+ (API 31+) можно использовать `setAutoEnterEnabled(true)` —
 * система автоматически входит в PiP при сворачивании activity, без явного
 * вызова enterPictureInPictureMode в onUserLeaveHint. Но для совместимости
 * со старыми устройствами оставлен ручной путь.
 */
object VideoPipController {

    private const val TAG = "VideoPipController"

    @Volatile
    private var pipEnabled = false

    @Volatile
    private var isPlaying = false

    /** VideoPlayerScreen / OkWebViewPlayer регистрирует callback для play/pause в PiP-окне. */
    @Volatile
    private var togglePlayPause: (() -> Unit)? = null

    /** Защита от спама PiP ошибками — не пытаться повторно, если последняя попытка была неудачной. */
    @Volatile
    private var pipAvailable = true

    fun setPipEnabled(enabled: Boolean) {
        pipEnabled = enabled
        AppLog.d(TAG, "setPipEnabled → $enabled")
    }

    fun setIsPlaying(playing: Boolean) {
        isPlaying = playing
    }

    fun setTogglePlayPause(action: (() -> Unit)?) {
        togglePlayPause = action
    }

    /**
     * Запросить переход в PiP-режим (явный, по тапу на кнопку PiP в плеере).
     *
     * Проверяет:
     *  1. pipEnabled — плеер зарегистрировал PiP (DisposableEffect).
     *  2. pipAvailable — предыдущая попытка не упала с ошибкой.
     *  3. Build.VERSION.SDK_INT >= O (API 26) — PiP доступен.
     *  4. Fix #142: activity.isInPictureInPictureMode — не повторять если уже в PiP.
     *
     * ВАЖНО: на Android 8+ enterPictureInPictureMode требует чтобы activity
     * была visible и не в multi-window. На некоторых оболочках (MIUI) также
     * требует `android:supportsPictureInPicture="true"` в манифесте
     * (Fix #142 добавил этот флаг).
     *
     * Fix #PIP-VIDEO-ONLY (2026-08-04): явный запрос PiP (тап по кнопке)
     * разрешён БЕЗ проверки isPlaying — пользователь явно попросил. Но
     * [enterPipIfEnabled] (auto-enter при сворачивании) теперь проверяет
     * isPlaying, чтобы не показывать чёрный PiP при сворачивании неработающего
     * плеера.
     */
    fun requestPip(activity: Activity) {
        if (!pipEnabled || !pipAvailable) {
            AppLog.d(TAG, "requestPip: skipped (enabled=$pipEnabled available=$pipAvailable)")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AppLog.w(TAG, "requestPip: PiP requires API 26+ (current=${Build.VERSION.SDK_INT})")
            return
        }
        // Fix #142: не повторять если уже в PiP (иначе IllegalStateException).
        if (activity.isInPictureInPictureMode) {
            AppLog.d(TAG, "requestPip: already in PiP — skip")
            return
        }
        try {
            val params = buildParams(activity)
            activity.enterPictureInPictureMode(params)
            AppLog.i(TAG, "enterPictureInPictureMode (manual)")
        } catch (e: Exception) {
            pipAvailable = false
            AppLog.e(TAG, "PiP failed — отключаем повторные попытки. " +
                "Проверьте android:supportsPictureInPicture=\"true\" в AndroidManifest.xml. " +
                "Error: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Вызывать из onUserLeaveHint() Activity (auto-PiP при сворачивании).
     *
     * Fix #PIP-VIDEO-ONLY (2026-08-04): PiP активируется ТОЛЬКО когда видео
     * реально playing. Раньше PiP показывался при любом сворачивании, если
     * пользователь был на экране видеоплеера — даже если видео на паузе или
     * ещё не загружено. Это приводило к чёрному PiP-окну.
     *
     * Теперь:
     *  - pipEnabled = true (плеер зарегистрирован) И
     *  - isPlaying = true (видео реально воспроизводится)
     *  → auto-enter PiP.
     *
     * Если видео на паузе — сворачивание просто скрывает приложение (normal
     * behavior), без PiP. Пользователь может явно запросить PiP кнопкой в
     * плеере ([requestPip]) — тогда isPlaying не проверяется.
     */
    fun enterPipIfEnabled(activity: Activity) {
        if (!pipEnabled || !pipAvailable) return
        // Fix #PIP-VIDEO-ONLY: только если видео playing — иначе чёрный PiP.
        if (!isPlaying) {
            AppLog.d(TAG, "enterPipIfEnabled: skipped — video not playing (PiP shows only video)")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (activity.isInPictureInPictureMode) return
        try {
            val params = buildParams(activity)
            activity.enterPictureInPictureMode(params)
            AppLog.i(TAG, "auto-enter PiP from onUserLeaveHint (video is playing)")
        } catch (e: Exception) {
            pipAvailable = false
            AppLog.e(TAG, "Auto-PiP failed — отключаем повторные попытки. " +
                "Error: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    fun exitPip(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (activity.isInPictureInPictureMode) {
                activity.moveTaskToBack(false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(activity: Activity): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        // Fix #142: setAutoEnterEnabled (API 31+) — система сама входит в PiP
        // при onUserLeaveHint, без явного enterPictureInPictureMode.
        // Безопасен на API < 31 (no-op через Compat).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                builder.setAutoEnterEnabled(true)
            } catch (e: Exception) {
                AppLog.w(TAG, "setAutoEnterEnabled failed: ${e.message}")
            }
        }
        // Прямой вызов setPictureInPictureParams — дублирует builder-флаг.
        // Fix #142: убрал (избыточно + может падать на некоторых оболочках).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = mutableListOf<RemoteAction>()
            // Play/Pause кнопка в PiP окне
            val pauseIcon = Icon.createWithResource(activity, android.R.drawable.ic_media_pause)
            val playIcon = Icon.createWithResource(activity, android.R.drawable.ic_media_play)
            val currentToggle = togglePlayPause
            if (currentToggle != null) {
                actions.add(
                    RemoteAction(
                        if (isPlaying) pauseIcon else playIcon,
                        if (isPlaying) "Пауза" else "Играть",
                        if (isPlaying) "Пауза" else "Играть",
                        android.app.PendingIntent.getBroadcast(
                            activity, 0,
                            Intent("re.pinok.VIDEO_PIP_TOGGLE").setPackage(activity.packageName),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            else
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                )
            }
            if (actions.isNotEmpty()) {
                builder.setActions(actions)
            }
        }
        return builder.build()
    }

    /** Вызвать из BroadcastReceiver для обработки PiP actions. */
    fun handleBroadcastAction(action: String) {
        if (action == "re.pinok.VIDEO_PIP_TOGGLE") {
            val fn = togglePlayPause
            if (fn != null) {
                fn()
                isPlaying = !isPlaying
            }
        }
    }
}