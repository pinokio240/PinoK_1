package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import re.pinok.util.AppLog

private const val PLAYER_TAG = "CallsRecordingPlayer"

/**
 * #CALLS-SNAP (2026-09-05): Этап В1 плана «звонки.перенос.план.md» (§4-В,
 * матрица §1.4 «Плеер записи») — нативный полноэкранный плеер записи звонка.
 *
 * Web-эквивалент (срез §4) — встроенный vkvideo-плеер; нативный эквивалент по
 * плану §3.6 («функционал не урезается») — собственный плеер. Реализован
 * Media3 ExoPlayer + PlayerView (зависимость добавлена в :feature:calls,
 * build.gradle.kts; каталожные алиасы androidx-media3-* из корневого toml).
 * Один движок для аудио и видео: PlayerView с системным контроллером
 * (play/pause/seek/позиция/время — реальные контролы медиа-плеера).
 *
 * Открывается полноэкранным Dialog ВНУТРИ секции (CallsMainScreen/SovaNavHost
 * недоступны Этапу В — маршруты не расширяются). Потоки: сетевое чтение и
 * декодирование — внутренние потоки ExoPlayer, UI-поток не блокируется
 * (#ANR-MAIN-IO). Ресурс освобождается в onDispose (release).
 *
 * Ограничение (честно): URL из ответа messages.getCallRecordings может
 * оказаться страницей vkvideo, а не медиа-потоком — тогда ExoPlayer отдаст
 * ошибку декодирования, она показывается поверх плеера (нет имитации
 * воспроизведения). #NULL-EXPLICIT: без non-null assertion, safe-call и
 * elvis операторов.
 */
@Composable
internal fun CallsRecordingPlayerDialog(
    title: String,
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val exo = remember(url) {
        ExoPlayer.Builder(context).build()
    }
    // .value-стиль (без by-делегата): захват MutableState из object-выражения
    // слушателя — без вопросов к capture local delegate
    val errorMessage = remember(url) { mutableStateOf<String?>(null) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val m = error.message
                if (m == null) {
                    errorMessage.value = "Ошибка воспроизведения записи"
                } else {
                    errorMessage.value = "Ошибка воспроизведения: $m"
                }
                AppLog.e(PLAYER_TAG, "playback error: $m", error)
            }
        }
        exo.addListener(listener)
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        exo.setHandleAudioBecomingNoisy(true)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
        onDispose {
            exo.removeListener(listener)
            exo.release()
            AppLog.i(PLAYER_TAG, "player released")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("recording_player_close"),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Закрыть плеер",
                            tint = Color.White,
                        )
                    }
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exo
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize().testTag("recording_player_view"),
                    )
                    val err = errorMessage.value
                    if (err != null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                err,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
