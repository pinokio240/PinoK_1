package re.pinok.ui.screens.clips

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.ui.components.VideoMessageShapes
import re.pinok.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "VideoMessageCreateScreen"

/** Максимальная длительность видео-сообщения (video_messages_config.max_duration_sec). */
private const val VM_MAX_SEC = 180

/** Стадии экрана записи «кружка». */
private enum class VmStage { IDLE, RECORDING, REVIEW, SENDING }

private data class VmUiState(
    val stage: VmStage = VmStage.IDLE,
    val recordedFile: File? = null,
    val camError: String? = null,
)

/**
 * #VM-3 волна 3: экран записи видео-сообщения («кружка»).
 *
 * CameraX: фронтальная камера, аудио включено, квадратный кадр. «Кружок» всегда
 * имеет форму КРУГА (форма №1, VideoMessageShapes.SHAPE_ID_BASE) — выбор других
 * форм убран по решению продукта. После стопа — превью и «Отправить», который
 * вызывает app.apiClient.sendVideoMessage(peerId, file, onProgress) и закрывает
 * экран при успехе.
 *
 * UI построен по образцу ClipCreateScreen: полноэкранный чёрный экран,
 * кнопка «Отмена» сверху, таймер, кнопка записи/стоп снизу.
 *
 * @param peerId куда отправлять кружок.
 * @param onBack закрыть экран (вернуться в чат).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMessageCreateScreen(
    peerId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf(VmUiState()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    var sendProgress by remember { mutableFloatStateOf(0f) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    // Флаг «отправка идёт» — блокирует повторный клик «Отправить» (защита от дублей).
    var sending by remember { mutableStateOf(false) }
    // Текущая CameraX-запись и видео-capture — живут на уровне экрана, чтобы
    // таймер и обработчик Finalize могли до них добраться.
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoCaptureRef by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (!result.values.all { it }) {
            AppLog.w(TAG, "Camera/mic permission denied: $result")
        }
    }

    fun hasCameraPermission(): Boolean {
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission()) {
            permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // Обработчик финального события записи (Finalize).
    fun handleRecordEvent(event: VideoRecordEvent, outFile: File) {
        if (event !is VideoRecordEvent.Finalize) return
        activeRecording = null
        isRecording = false
        val cause = event.cause
        if (cause != null) {
            try { outFile.delete() } catch (_: Exception) {}
            uiState = uiState.copy(camError = cause.message?.takeIf { it.isNotBlank() }
                ?: "Ошибка записи (${cause.javaClass.simpleName})")
            return
        }
        recordedFile = outFile
        uiState = uiState.copy(stage = VmStage.REVIEW, recordedFile = outFile)
    }

    fun doStopRecording() {
        if (!isRecording) return
        try { activeRecording?.stop() } catch (e: Exception) {
            AppLog.e(TAG, "stop error", e)
            isRecording = false
        }
    }

    fun doStartRecording(capture: VideoCapture<Recorder>) {
        if (isRecording) return
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(context.cacheDir, "vm_$ts.mp4")
            val outputOptions = FileOutputOptions.Builder(outFile).build()
            val rec = capture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(
                    ContextCompat.getMainExecutor(context),
                    androidx.core.util.Consumer { ev -> handleRecordEvent(ev, outFile) },
                )
            activeRecording = rec
            videoCaptureRef = capture
            recordStartAt = System.currentTimeMillis()
            elapsed = 0
            isRecording = true
            uiState = uiState.copy(stage = VmStage.RECORDING, camError = null)
        } catch (e: Exception) {
            AppLog.e(TAG, "start error", e)
            uiState = uiState.copy(camError = "Не удалось начать запись: ${e.message}")
        }
    }

    fun doReset() {
        recordedFile?.let { try { it.delete() } catch (_: Exception) {} }
        recordedFile = null
        activeRecording = null
        videoCaptureRef = null
        isRecording = false
        uiState = VmUiState()
    }

    fun doSend() {
        val file = recordedFile ?: return
        if (sending) {
            AppLog.w(TAG, "doSend: отправка уже идёт, игнорирую повторный клик")
            return
        }
        sending = true
        sendProgress = 0f
        uiState = uiState.copy(stage = VmStage.SENDING, camError = null)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    app.apiClient.sendVideoMessage(peerId, file) { fr ->
                        // Прогресс загрузки сообщается локально в UI.
                        sendProgress = fr.coerceIn(0f, 1f)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "send error", e)
                    -1L
                }
            }
            sending = false
            if (result > 0L) {
                AppLog.i(TAG, "doSend: отправлено msgId=$result")
                onBack()
            } else {
                // Файл НЕ теряем — остаёмся на ревью, юзер может повторить «Отправить».
                AppLog.w(TAG, "doSend: отправка не удалась (result=$result), остаюсь на ревью")
                android.widget.Toast.makeText(
                    context, "Не удалось отправить кружок: загрузка видео не прошла",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                uiState = uiState.copy(stage = VmStage.REVIEW, camError = "Ошибка отправки: загрузка видео не прошла")
            }
        }
    }

    // Таймер длительности записи + автостоп на 180 сек.
    LaunchedEffect(isRecording, recordStartAt) {
        if (isRecording && recordStartAt > 0L) {
            while (true) {
                val sec = ((System.currentTimeMillis() - recordStartAt) / 1000).toInt()
                elapsed = sec
                if (sec >= VM_MAX_SEC) {
                    doStopRecording()
                    break
                }
                delay(250)
            }
        }
    }

    when (uiState.stage) {
        VmStage.IDLE, VmStage.RECORDING -> CameraRecorderStage(
            context = context,
            isRecording = uiState.stage == VmStage.RECORDING,
            elapsed = elapsed,
            cameraError = uiState.camError,
            permissionGranted = hasCameraPermission(),
            onPermissionRequest = {
                permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
            onStartRecord = { capture -> doStartRecording(capture) },
            onStopRecord = { doStopRecording() },
            onCancel = {
                if (isRecording) doStopRecording()
                doReset()
                onBack()
            },
        )
        VmStage.REVIEW -> ReviewSendStage(
            context = context,
            file = recordedFile,
            onBackToCamera = { doReset() },
            onSend = { doSend() },
            onCancel = { doReset(); onBack() },
        )
        VmStage.SENDING -> SendingStage(
            progress = sendProgress,
            onBack = { doReset(); onBack() },
        )
    }

    // Unbind камеры при выходе с экрана.
    DisposableEffect(Unit) {
        onDispose {
            try { activeRecording?.stop() } catch (_: Exception) {}
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try { future.get().unbindAll() } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {}
        }
    }
}

/**
 * IDLE/RECORDING: превью камеры (квадратный кадр) + пикер форм (до записи) +
 * кнопки записи/стоп и «Отмена».
 */
@Composable
private fun CameraRecorderStage(
    context: Context,
    isRecording: Boolean,
    elapsed: Int,
    cameraError: String?,
    permissionGranted: Boolean,
    onPermissionRequest: () -> Unit,
    onStartRecord: (VideoCapture<Recorder>) -> Unit,
    onStopRecord: () -> Unit,
    onCancel: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelectorBuilderFront(),
                    preview,
                    videoCapture,
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "bind error", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    if (!permissionGranted) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Для кружка нужны камера и микрофон",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(24.dp),
                )
                Row {
                    TextButton(onClick = onPermissionRequest) { Text("Разрешить", color = Color.White) }
                    TextButton(onClick = onCancel) { Text("Отмена", color = Color.White.copy(alpha = 0.7f)) }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Квадратный кадр камеры по центру верхней части экрана.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center,
        ) {
            // Живое превью камеры, вырезаемое выбранной формой (реальный клип,
            // а не только контур).
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val shape = VideoMessageShapes.shapePathByShapeId(VideoMessageShapes.SHAPE_ID_BASE, this.size)
                        if (shape != null) {
                            clipPath(shape) { this@drawWithContent.drawContent() }
                        } else {
                            drawContent()
                        }
                    },
                factory = { previewView },
            )
            // Границы кадра.
            Box(
                Modifier
                    .matchParentSize()
                    .padding(16.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            )
            // Контур круга (форма №1) поверх заклипованного превью — ориентир маски.
            ShapeOutlineOverlay(shapeId = VideoMessageShapes.SHAPE_ID_BASE, enabled = !isRecording)
        }

        // Верхняя панель: «Отмена».
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Отмена", tint = Color.White)
            }
        }

        // Нижняя панель: таймер, пикер форм (только до записи), кнопка.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isRecording) {
                Text(
                    formatSec(elapsed),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    "Запишите кружок\n(до $VM_MAX_SEC сек)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            RecordPillButton(
                isRecording = isRecording,
                progress = elapsed.toFloat() / VM_MAX_SEC,
                onStart = { onStartRecord(videoCapture) },
                onStop = onStopRecord,
            )
        }

        // Ошибка камеры.
        cameraError?.let { err ->
            Box(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(16.dp),
            ) {
                Text(err, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

private fun CameraSelectorBuilderFront() =
    androidx.camera.core.CameraSelector.Builder()
        .requireLensFacing(androidx.camera.core.CameraSelector.LENS_FACING_FRONT).build()

/** Оверлей-контур выбранной формы поверх квадратного кадра (ориентир маски). */
@Composable
private fun ShapeOutlineOverlay(shapeId: Int, enabled: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        VideoMessageShapes.shapePathByShapeId(shapeId, size)?.let { p ->
            drawPath(
                path = p,
                color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f),
                style = Stroke(width = 4f),
            )
        }
    }
}

/** Горизонтальный пикер форм убран — «кружок» всегда форма №1 (круг). */

/** Круглая кнопка записи/стоп внизу экрана. */
@Composable
private fun RecordPillButton(
    isRecording: Boolean,
    progress: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (isRecording) 36.dp else 58.dp)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(Color.Red)
                .clickable { if (isRecording) onStop() else onStart() },
        )
        if (isRecording) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(76.dp),
                color = Color.White.copy(alpha = 0.9f),
                trackColor = Color.Transparent,
                strokeWidth = 3.dp,
            )
        }
    }
}

/** REVIEW: превью записанного файла + кнопки «Отмена»/«Отправить». */
@Composable
private fun ReviewSendStage(
    context: Context,
    file: File?,
    onBackToCamera: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val player = if (file != null) {
        remember(file) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
        }
    } else null

    DisposableEffect(file) {
        onDispose { try { player?.release() } catch (_: Exception) {} }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (file != null) {
            // Превью в том же квадратном кадре, вырезанном кругом (форма №1).
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.TopCenter),
            ) {
                AndroidView(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            val shape = VideoMessageShapes.shapePathByShapeId(VideoMessageShapes.SHAPE_ID_BASE, this.size)
                            if (shape != null) {
                                clipPath(shape) { this@drawWithContent.drawContent() }
                            } else {
                                drawContent()
                            }
                        },
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                )
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Отправить кружок", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** SENDING: прогресс отправки. */
@Composable
private fun SendingStage(
    progress: Float,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(16.dp))
            Text("Отправка кружка…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) { Text("Отмена", color = Color.White) }
        }
    }
}

private fun formatSec(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) String.format(Locale.getDefault(), "%d:%02d", m, s)
    else String.format(Locale.getDefault(), "0:%02d", s)
}