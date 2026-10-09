// File: media/ClipPlayer.kt
package re.pinok.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.data.model.Video
import re.pinok.util.AppLog

/**
 * Изолированный проигрыватель клипов — полный перенос web-поведения VK.
 *
 * В отличие от старой логики [re.pinok.ui.screens.clips.ClipsFeedScreen]
 * (mp4-first, MIME по URL, без учёта ключа), здесь:
 *  - выбор источника по WEB_PRIORITY ([ClipSourceSelector.pick], DASH-first);
 *  - MIME по КЛЮЧУ files (dash→MPD, hls→M3U8), а не по URL;
 *  - кастомный DataSource [OkCdnQueryRangeDataSource] для okcdn `&bytes=`;
 *  - ABR по web-правилам ([buildTrackSelector]);
 *  - failover_host при IO-ошибках (1 попытка), затем следующий формат;
 *  - stall-guard: 3 сталла → понижение maxVideoBitrate.
 *
 * Владелец (ClipPlayerItem) вызывает [prepare] один раз на clip и управляет через
 * [play]/[pause]/[setVolume]. Плеер живёт всё время показа клипа и освобождается
 * в [release].
 *
 * NULL-политика: без `?.`/`?:`/`!!`, только явные guard и локальные val.
 */
class ClipPlayer(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val vkUserAgent: String,
    private val callbacks: ClipPlayerCallbacks,
) {

    interface ClipPlayerCallbacks {
        fun onFirstFrame()
        fun onError(error: Throwable)
        fun onTrackChange()
        fun onDurationChanged()
        fun onPositionChanged()
    }

    /** Один кандидат на воспроизведение из WEB_PRIORITY. */
    private class Source(val key: String, val url: String, val mime: String)

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    // Локальное состояние выбора источника (для failover).
    private var filesMap: Map<String, String> = emptyMap()
    private var sources: List<Source> = emptyList()
    private var sourceIndex: Int = -1
    private var failoverUsed: Boolean = false
    private var maxBitrate: Int = DEFAULT_MAX_BITRATE

    // Stall-guard.
    private var stallCount: Int = 0
    private var lastStallStartMs: Long = 0L

    // Таймаут на первый кадр (web sourceOpenTimeout). Если за SOURCE_TIMEOUT_MS
    // первый кадр не появился — считаем источник зависшим и переключаемся дальше.
    private var firstFrameJob: Job? = null
    private val uiScope = CoroutineScope(Dispatchers.Main.immediate)

    // Внешнее управление.
    private var playWhenReadyOverride: Boolean = true
    private var currentVolume: Float = 1f

    @Volatile
    private var released: Boolean = false

    private companion object {
        private const val TAG = "ClipPlayer"
        private const val FAILOVER_KEY: String = "failover_host"
        private const val DEFAULT_MAX_BITRATE: Int = 8_000_000
        private const val STALL_LOWER_FACTOR: Int = 2
        private const val MIN_STALL_MS: Long = 500L
        private const val MAX_STALL_THRESHOLD: Int = 3
        private const val MIN_VIDEO_BITRATE: Int = 500_000
        // Таймаут ожидания первого кадра (web sourceOpenTimeout: 3000). Диапазон 2-4с,
        // берём 3000мс.
        private const val SOURCE_TIMEOUT_MS: Long = 3000L
    }

    /**
     * Главный вход: строит список кандидатов из [clip.files] и начинает
     * воспроизведение с лучшего. Повторный вызов пересбрасывает состояние.
     */
    fun prepare(clip: Video) {
        if (released) return
        val files = clip.files
        val av1 = ClipSourceSelector.hasAv1Decoder(context)
        val chosen = ClipSourceSelector.pick(files, av1)
        val keysLabel = if (files == null) "null" else files.keys.toString()
        AppLog.i(TAG, "prepare: clip=${clip.ownerId}_${clip.id} filesKeys=$keysLabel av1=$av1 chosen=${chosen?.first ?: "null"}")
        if (chosen == null) {
            AppLog.w(TAG, "prepare: нет источников для clip ${clip.ownerId}_${clip.id}")
            callbacks.onError(IllegalStateException("No playable source for clip"))
            return
        }
        val list = buildCandidateList(files, av1)
        if (list.isEmpty()) {
            callbacks.onError(IllegalStateException("No playable source for clip"))
            return
        }
        if (files != null) {
            filesMap = files
        }
        sources = list
        sourceIndex = list.indexOfFirst { it.key == chosen.first }.coerceAtLeast(0)
        failoverUsed = false
        stallCount = 0
        lastStallStartMs = 0L
        ensurePlayer()
        if (released) return
        startCurrentSource()
    }

    private fun buildCandidateList(files: Map<String, String>?, av1: Boolean): List<Source> {
        if (files == null) return emptyList()
        if (files.isEmpty()) return emptyList()
        val out = ArrayList<Source>(ClipSourceSelector.WEB_PRIORITY.size)
        val priority = ClipSourceSelector.WEB_PRIORITY
        for (key in priority) {
            val url = files[key]
            if (url == null) continue
            if (url.isBlank()) continue
            if (key == "dash_webm_av1" && !av1) continue
            val mime = ClipSourceSelector.pickMime(key)
            if (mime == null) {
                out.add(Source(key, url, ""))
            } else {
                out.add(Source(key, url, mime))
            }
        }
        return out
    }

    /** Гарантирует существование ExoPlayer (создаётся один раз). */
    private fun ensurePlayer() {
        val existing = player
        if (existing != null) return
        val selector = buildTrackSelector()
        trackSelector = selector
        val exo = try {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(buildMediaSourceFactory())
                .setLoadControl(VideoPlayerConfig.defaultLoadControl())
                .setTrackSelector(selector)
                .build()
        } catch (e: Exception) {
            AppLog.e(TAG, "ExoPlayer build failed", e)
            callbacks.onError(e)
            return
        }
        exo.volume = currentVolume
        exo.playWhenReady = playWhenReadyOverride
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        exo.addListener(ClipPlayerListener())
        player = exo
    }

    /** ABR по web-правилам: 3s buffer re-eval, 15s re-eval, bandwidthFraction 0.9. */
    private fun buildTrackSelector(): DefaultTrackSelector {
        val selectionFactory = AdaptiveTrackSelection.Factory(
            10000, // minDurationForQualityIncreaseMs
            20000, // maxDurationForQualityDecreaseMs
            3000,  // minTimeBetweenBufferReevaluationMs
            0.9f,  // bandwidthFraction
        )
        return DefaultTrackSelector(context, selectionFactory)
    }

    /** OkHttp DataSource + okcdn `&bytes=` обёртка + DefaultDataSource. */
    private fun buildMediaSourceFactory(): DefaultMediaSourceFactory {
        // web-сессия клипов (HAR) шлёт Referer и Origin ОБА как https://m.vk.ru.
        val refererProps = mapOf("Referer" to "https://m.vk.ru/", "Origin" to "https://m.vk.ru")
        val httpFactory: DataSource.Factory = try {
            OkHttpDataSource.Factory(httpClient)
                .setUserAgent(vkUserAgent)
                .setDefaultRequestProperties(refererProps)
        } catch (e: Exception) {
            AppLog.w(TAG, "OkHttpDataSource setup failed, fallback to DefaultHttpDataSource: ${e.message}")
            DefaultHttpDataSource.Factory()
                .setUserAgent(vkUserAgent)
                .setDefaultRequestProperties(refererProps)
        }
        val okcdnFactory = DataSource.Factory {
            OkCdnQueryRangeDataSource(httpFactory.createDataSource())
        }
        val dsFactory = DefaultDataSource.Factory(context, okcdnFactory)
        return DefaultMediaSourceFactory(dsFactory)
    }

    /** Начинает/перезапускает воспроизведение текущего [sourceIndex]. */
    private fun startCurrentSource() {
        val exo = player
        if (exo == null) return
        val source = sources.getOrNull(sourceIndex)
        if (source == null) {
            callbacks.onError(IllegalStateException("No playable source left"))
            return
        }
        val url = resolveSourceUrl(source)
        val builder = MediaItem.Builder().setUri(url)
        val mime = source.mime
        if (mime.isNotEmpty()) {
            builder.setMimeType(mime)
        }
        try {
            exo.stop()
            exo.setMediaItem(builder.build())
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.playWhenReady = playWhenReadyOverride
            exo.prepare()
            val mimeLabel = if (mime.isEmpty()) "progressive" else mime
            val host = try { android.net.Uri.parse(url).host } catch (e: Exception) { "?" }
            AppLog.i(TAG, "startCurrentSource: idx=$sourceIndex key=${source.key} mime=$mimeLabel host=$host failover=$failoverUsed url=${url.take(120)}")
            scheduleSourceTimeout()
        } catch (e: Exception) {
            AppLog.e(TAG, "startCurrentSource prepare error", e)
            callbacks.onError(e)
        }
    }

    /**
     * Таймаут на первый кадр: если клип не показал кадр за SOURCE_TIMEOUT_MS —
     * считаем источник зависшим и переходим на следующий формат (или failover).
     * Сбрасывается в [ClipPlayerListener.onRenderedFirstFrame].
     */
    private fun scheduleSourceTimeout() {
        cancelSourceTimeout()
        val job = uiScope.launch {
            delay(SOURCE_TIMEOUT_MS)
            AppLog.w(TAG, "source timeout (${SOURCE_TIMEOUT_MS}ms) — первый кадр не появился, switch")
            handleSourceTimeout()
        }
        firstFrameJob = job
    }

    private fun cancelSourceTimeout() {
        val job = firstFrameJob
        if (job != null) {
            job.cancel()
            firstFrameJob = null
        }
    }

    /** Таймаут источника: failover (1 раз) → следующий формат из WEB_PRIORITY. */
    private fun handleSourceTimeout() {
        if (released) return
        // 1) failover_host на том же источнике (один раз).
        if (!failoverUsed) {
            failoverUsed = true
            AppLog.i(TAG, "timeout-failover: retry source idx=$sourceIndex with failover_host")
            startCurrentSource()
            return
        }
        // 2) следующий формат.
        failoverUsed = false
        val nextIdx = sourceIndex + 1
        if (nextIdx < sources.size) {
            sourceIndex = nextIdx
            AppLog.i(TAG, "timeout: switch to next format idx=$nextIdx (${sources[nextIdx].key})")
            startCurrentSource()
        } else {
            AppLog.w(TAG, "timeout: исчерпаны все источники")
            callbacks.onError(IllegalStateException("All sources timed out"))
        }
    }

    /** URL источника: с failover_host если активирован failover, иначе оригинал. */
    private fun resolveSourceUrl(source: Source): String {
        if (!failoverUsed) return source.url
        val fh = filesMap[FAILOVER_KEY]
        if (fh == null) return source.url
        if (fh.isBlank()) return source.url
        val swapped = ClipSourceSelector.withFailoverHost(source.url, fh)
        if (swapped == null) return source.url
        return swapped
    }

    /** Сырой ExoPlayer для привязки к PlayerView (null до успешного создания). */
    fun exoPlayer(): ExoPlayer? = player

    fun play() {
        if (released) return
        playWhenReadyOverride = true
        val p = player
        if (p == null) return
        if (!p.isPlaying) p.play()
    }

    fun pause() {
        if (released) return
        playWhenReadyOverride = false
        val p = player
        if (p == null) return
        if (p.isPlaying) p.pause()
    }

    /** true, если клип объявлен текущим (для playWhenReady vs pause в UI). */
    fun isCurrent(): Boolean = playWhenReadyOverride

    fun setVolume(volume: Float) {
        currentVolume = volume
        val p = player
        if (p != null) p.volume = volume
    }

    /** Текущая позиция в миллисекундах (для viewSegments). */
    fun currentPositionMs(): Long {
        val p = player
        if (p == null) return 0L
        val pos = p.currentPosition
        return pos.coerceAtLeast(0L)
    }

    /** Длительность в миллисекундах (0 пока неизвестна). */
    fun durationMs(): Long {
        val p = player
        if (p == null) return 0L
        val d = p.duration
        if (d == C.TIME_UNSET) return 0L
        return d.coerceAtLeast(0L)
    }

    fun release() {
        if (released) return
        released = true
        cancelSourceTimeout()
        try {
            val p = player
            if (p != null) p.release()
        } catch (e: Exception) {
            AppLog.w(TAG, "release error: ${e.message}")
        }
        player = null
        trackSelector = null
        sources = emptyList()
        filesMap = emptyMap()
        sourceIndex = -1
    }

    /** Player.Listener: onFirstFrame, сталлы и failover по IO-ошибкам. */
    private inner class ClipPlayerListener : Player.Listener {

        override fun onRenderedFirstFrame() {
            cancelSourceTimeout()
            callbacks.onFirstFrame()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                callbacks.onPositionChanged()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            callbacks.onPositionChanged()
        }

        override fun onTracksChanged(trackGroups: Tracks) {
            callbacks.onTrackChange()
            callbacks.onDurationChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                handleStall()
            } else if (playbackState == Player.STATE_READY) {
                stallCount = 0
                lastStallStartMs = 0L
            }
            callbacks.onPositionChanged()
        }

        private fun handleStall() {
            if (released) return
            val now = System.currentTimeMillis()
            if (lastStallStartMs == 0L) {
                // Начало первой паузы буферизации.
                lastStallStartMs = now
                return
            }
            val elapsed = now - lastStallStartMs
            lastStallStartMs = now
            if (elapsed < MIN_STALL_MS) return
            stallCount += 1
            if (stallCount >= MAX_STALL_THRESHOLD) {
                stallCount = 0
                lowerMaxVideoBitrate()
                lastStallStartMs = 0L
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.e(TAG, "onPlayerError: code=${error.errorCode} (${error.errorCodeName}) msg=${error.message} cause=${error.cause?.message}", error)
            if (released) return
            if (isIoError(error.errorCode)) {
                handleIoError()
            } else {
                callbacks.onError(error)
            }
        }

        private fun isIoError(code: Int): Boolean {
            if (code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return true
            if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) return true
            if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return true
            if (code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return true
            return false
        }

        private fun handleIoError() {
            // 1) Пробуем failover_host на ТОМ ЖЕ источнике (один раз).
            if (!failoverUsed) {
                failoverUsed = true
                AppLog.i(TAG, "failover: retry source idx=$sourceIndex with failover_host")
                startCurrentSource()
                return
            }
            // 2) Иначе — следующий формат из WEB_PRIORITY.
            failoverUsed = false
            val nextIdx = sourceIndex + 1
            if (nextIdx < sources.size) {
                sourceIndex = nextIdx
                AppLog.i(TAG, "failover: switch to next format idx=$nextIdx (${sources[nextIdx].key})")
                startCurrentSource()
            } else {
                AppLog.w(TAG, "failover: исчерпаны все источники")
                callbacks.onError(IllegalStateException("All sources failed"))
            }
        }

        private fun lowerMaxVideoBitrate() {
            val nextBps = (maxBitrate / STALL_LOWER_FACTOR).coerceAtLeast(MIN_VIDEO_BITRATE)
            if (nextBps >= maxBitrate) return
            maxBitrate = nextBps
            val selector = trackSelector
            if (selector == null) return
            try {
                val params = selector.parameters.buildUpon()
                    .setMaxVideoBitrate(nextBps)
                    .build()
                selector.setParameters(params)
                AppLog.i(TAG, "stall-guard: lowered maxVideoBitrate to $nextBps")
            } catch (e: Exception) {
                AppLog.w(TAG, "stall-guard: setParameters failed: ${e.message}")
            }
        }
    }
}