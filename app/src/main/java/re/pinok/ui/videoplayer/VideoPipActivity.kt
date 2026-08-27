// File: ui/videoplayer/VideoPipActivity.kt
package re.pinok.ui.videoplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import re.pinok.media.PlaybackPositionStore
import re.pinok.media.PlayerConnection
import re.pinok.util.AppLog
import re.pinok.util.VkUserAgent

/**
 * #PIP-VIDEO-ONLY: отдельная активность, которая уходит в Picture-in-Picture.
 *
 * Раньше PiP вызывался на MainActivity (`VideoPipController.requestPip`) —
 * из-за этого в PiP-окно сворачивалось ВСЁ приложение (навигация, чаты, лента),
 * а пользователь не мог продолжать листать контент. Теперь PiP-окно содержит
 * ТОЛЬКО видео: MainActivity остаётся полноэкранной и browsable, а поверх неё
 * плавает PiP-окно этой активности.
 *
 * Поток: кнопка PiP в VideoPlayerScreen → стартует VideoPipActivity с текущим
 * URL + позицией → активность сразу уходит в PiP → пользователь листает приложение.
 * Тап по PiP-окну разворачивает её в полноэкранный плеер (тот же ExoPlayer).
 */
class VideoPipActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VideoPipActivity"
        private const val ACTION_TOGGLE = "re.pinok.VIDEO_PIP_TOGGLE"
        private const val ACTION_CLOSE = "re.pinok.VIDEO_PIP_CLOSE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_OWNER_ID = "extra_owner_id"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_PLAYBACK_RATE = "extra_playback_rate"

        fun intent(
            context: Context,
            url: String,
            title: String,
            positionMs: Long,
            ownerId: Long,
            videoId: Long,
            playbackRate: Float = 1f,
        ): Intent = Intent(context, VideoPipActivity::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_POSITION, positionMs)
            .putExtra(EXTRA_OWNER_ID, ownerId)
            .putExtra(EXTRA_VIDEO_ID, videoId)
            .putExtra(EXTRA_PLAYBACK_RATE, playbackRate)

        // #PIP-PAUSE-ON-NEW-VIDEO: держим ссылку на плеер активного PiP-окна,
        // чтобы при открытии нового видео приостановить уже плавающее.
        @Volatile
        private var activePlayer: ExoPlayer? = null

        // #PIP-AUDIO-PAUSE: активен ли PiP сейчас (для координации с VideoPlayerScreen,
        // чтобы он не возобновил аудио, пока PiP играет).
        @Volatile
        var isActive = false
            private set

        // #PIP-AUDIO-PAUSE: нужно ли возобновить аудио при закрытии PiP.
        @Volatile
        var resumeAudioOnClose = false

        /**
         * Поставить активный PiP-плеер на паузу. Вызывается из VideoPlayerScreen
         * (и из onCreate самой активности) при открытии нового видео.
         */
        fun pauseActivePip() {
            try {
                activePlayer?.pause()
                AppLog.d(TAG, "pauseActivePip: paused active PiP player")
            } catch (e: Exception) {
                AppLog.w(TAG, "pauseActivePip failed: ${e.message}")
            }
        }

        private fun registerActivePlayer(player: ExoPlayer?) {
            activePlayer = player
        }
    }

    private var player: ExoPlayer? = null
    private var posKey: String? = null
    private var title: String = ""
    private var pipEnteredOnce = false
    private val isInPipState = mutableStateOf(false)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE -> togglePlayPause()
                ACTION_CLOSE -> {
                    savePosition()
                    player?.release()
                    player = null
                    finishAndRemoveTask()
                }
            }
        }
    }

    // #PIP-ON-NEW-INTENT: singleTask launchMode — если пользователь открыл
    // новое видео через PiP пока старая активность ещё в стеке, startActivity
    // вызовет onNewIntent (а не onCreate). Без этого ExoPlayer продолжает
    // играть старый клип вместо нового видео.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLog.i(TAG, "onNewIntent — switching to new video")
        savePosition()
        val url = intent.getStringExtra(EXTRA_URL)
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val ownerId = intent.getLongExtra(EXTRA_OWNER_ID, 0L)
        val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
        val playbackRate = intent.getFloatExtra(EXTRA_PLAYBACK_RATE, 1f).coerceIn(0.25f, 2f)
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        if (url.isNullOrBlank()) return
        posKey = PlaybackPositionStore.videoKey(ownerId, videoId)
        player?.release()
        player = buildPlayer(url, position, playbackRate)
        registerActivePlayer(player)
        // #PIP-AUDIO-PAUSE: новое видео → аудио на паузе.
        resumeAudioOnClose = PlayerConnection.pauseIfPlaying()
        isActive = true
        setContent {
            val inPip by isInPipState
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = { pv -> pv.useController = !inPip },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!inPip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            savePosition()
                            player?.release()
                            player = null
                            finishAndRemoveTask()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        pipEnteredOnce = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate")

        val url = intent.getStringExtra(EXTRA_URL)
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val ownerId = intent.getLongExtra(EXTRA_OWNER_ID, 0L)
        val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
        val playbackRate = intent.getFloatExtra(EXTRA_PLAYBACK_RATE, 1f).coerceIn(0.25f, 2f)
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        if (url.isNullOrBlank()) {
            AppLog.w(TAG, "empty URL — finishing")
            finish()
            return
        }
        posKey = PlaybackPositionStore.videoKey(ownerId, videoId)

        // #PIP-AUDIO-PAUSE: пока PiP играет — аудио на паузе. Если аудио ещё играло
        // (краевой случай, когда PiP стартует без предварительной паузы из видеоплеера),
        // запоминаем и возобновим при закрытии.
        resumeAudioOnClose = PlayerConnection.pauseIfPlaying()
        isActive = true

        // #PIP-PAUSE-ON-NEW-VIDEO: перед созданием нового плеера приостанавливаем
        // предыдущий PiP (если пользователь запустил второй PiP поверх первого).
        pauseActivePip()

        player = buildPlayer(url, position, playbackRate)
        registerActivePlayer(player)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_TOGGLE)
                addAction(ACTION_CLOSE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }
        }

        val p = player
        setContent {
            val inPip by isInPipState
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = p
                            useController = true
                        }
                    },
                    update = { pv -> pv.useController = !inPip },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!inPip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            savePosition()
                            p?.release()
                            finishAndRemoveTask()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    private fun buildPlayer(url: String, position: Long, playbackRate: Float = 1f): ExoPlayer {
        val ua = VkUserAgent.get(application)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setDefaultRequestProperties(mapOf("Referer" to "https://m.vk.com/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
        if (url.contains("m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exo.setMediaItem(mediaItemBuilder.build())
        exo.prepare()
        exo.seekTo(position)
        // #PIP-INHERIT-SETTINGS: скорость воспроизведения наследуется от видеоплеера.
        exo.setPlaybackSpeed(playbackRate)
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                refreshPipParams()
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            }
        })
        return exo
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun savePosition() {
        val key = posKey ?: return
        val p = player ?: return
        try {
            if (p.playbackState == Player.STATE_READY) {
                val pos = p.currentPosition
                val dur = p.duration
                if (dur > 0 && pos >= dur * 0.95) {
                    PlaybackPositionStore.clearPosition(key)
                } else if (pos > 3000L) {
                    PlaybackPositionStore.savePosition(key, pos)
                }
            }
            PlaybackPositionStore.flush()
        } catch (e: Exception) {
            AppLog.w(TAG, "savePosition failed: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!pipEnteredOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipEnteredOnce = true
            Handler(Looper.getMainLooper()).postDelayed({
                enterPip()
            }, 200L)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) {
            enterPip()
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isInPictureInPictureMode) return
        try {
            enterPictureInPictureMode(buildPipParams())
            AppLog.i(TAG, "enterPictureInPictureMode")
        } catch (e: Exception) {
            AppLog.e(TAG, "enterPip failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun refreshPipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isInPictureInPictureMode) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (e: Exception) {
            AppLog.w(TAG, "refreshPipParams failed: ${e.message}")
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                builder.setAutoEnterEnabled(true)
            } catch (e: Exception) {
                AppLog.w(TAG, "setAutoEnterEnabled failed: ${e.message}")
            }
        }
        val playing = player?.isPlaying == true
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val actions = mutableListOf<RemoteAction>()
        actions.add(
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                ),
                if (playing) "Пауза" else "Играть",
                if (playing) "Пауза" else "Играть",
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_TOGGLE).setPackage(packageName),
                    flags,
                ),
            )
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Закрыть",
                "Закрыть",
                PendingIntent.getBroadcast(
                    this,
                    1,
                    Intent(ACTION_CLOSE).setPackage(packageName),
                    flags,
                ),
            )
        )
        builder.setActions(actions)
        return builder.build()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
        AppLog.i(TAG, "PiP mode changed: $isInPictureInPictureMode")
        if (!isInPictureInPictureMode) {
            refreshPipParams()
        }
    }

    override fun onStop() {
        savePosition()
        super.onStop()
    }

    override fun onDestroy() {
        savePosition()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                unregisterReceiver(pipReceiver)
            } catch (e: Exception) {
                AppLog.w(TAG, "unregisterReceiver: ${e.message}")
            }
        }
        player?.release()
        player = null
        registerActivePlayer(null)
        // #PIP-AUDIO-PAUSE: возобновляем аудио при закрытии PiP, если оно было
        // приостановлено (непосредственно PiP'ом или видеоплеером, который ушёл
        // с экрана пока PiP играл).
        if (resumeAudioOnClose) {
            PlayerConnection.resumeIfWasPlaying()
            resumeAudioOnClose = false
        }
        isActive = false
        super.onDestroy()
        AppLog.i(TAG, "onDestroy")
    }
}
