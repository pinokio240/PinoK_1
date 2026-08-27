package re.pinok.ui.screens.clips

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.video.VideoCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
// Fix #140: navigationBarsPadding — чтобы нижний оверлей не перекрывался
// navigation bar в edge-to-edge.
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import re.pinok.SovaApp
import re.pinok.util.AppLog
import java.util.Locale

private const val TAG = "ClipCreateScreen"

/** Минимальная и максимальная длительность клипа в секундах (VK ограничение). */
private const val CLIP_MIN_SEC = 5
private const val CLIP_MAX_SEC = 60

/**
 * §37.12 Phase 5: Clip creation — экран записи и публикации клипа.
 *
 * Состояния экрана:
 *  1) Camera   — превью камеры + кнопка записи
 *  2) Review   — превью записанного видео + поля (описание, музыка) + "Опубликовать"
 *  3) Publish  — прогресс-бар по стадиям (video.save → upload → обработка → готово)
 *  4) Done     — финальный экран с кнопкой "Готово"
 *
 * CameraX use cases: Preview + VideoCapture<Recorder> (Quality.HD).
 * Запись ведётся в cacheDir/clips/ (FileOutputOptions) → Uri через FileProvider.
 *
 * @param onBack закрыть экран
 * @param onPublished(ownerId, videoId) — после успешной публикации
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipCreateScreen(
    onBack: () -> Unit,
    onPublished: (ownerId: Long, videoId: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val vm: ClipCreateViewModel = viewModel(factory = clipCreateViewModelFactory(app))

    val uiState by vm.uiState.collectAsState()

    // Permission launcher — запрашивает CAMERA + RECORD_AUDIO вместе.
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val allGranted = result.values.all { it }
        if (!allGranted) {
            AppLog.w(TAG, "Camera/mic permission denied: $result")
        }
    }

    LaunchedEffect(Unit) {
        if (!vm.hasCameraPermission()) {
            permLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            ))
        }
    }

    when (uiState.stage) {
        ClipCreateStage.Camera -> CameraStage(
            vm = vm,
            onBack = onBack,
            permLauncher = permLauncher,
        )
        ClipCreateStage.Review -> ReviewStage(vm = vm, onBack = onBack)
        ClipCreateStage.Publish -> PublishStage(
            vm = vm,
            onPublished = onPublished,
            onBack = onBack,
        )
        ClipCreateStage.Done -> DoneStage(vm = vm, onBack = onBack)
    }
}

// ════════════════════════════════════════════════════════════════════════
// Stage 1: Camera — превью + запись
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraStage(
    vm: ClipCreateViewModel,
    onBack: () -> Unit,
    permLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by vm.uiState.collectAsState()

    if (!vm.hasCameraPermission()) {
        PermissionRequestView(
            onGrant = {
                permLauncher.launch(arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                ))
            },
            onBack = onBack,
        )
        return
    }

    // CameraX preview view.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // VideoCapture use case с Recorder (CameraX 1.4+).
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    // Bind use cases к lifecycle при смене lens.
    LaunchedEffect(uiState.lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(uiState.lensFacing)
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    videoCapture,
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Camera bind error", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Unbind камеры при выходе из экрана.
    DisposableEffect(Unit) {
        onDispose {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try { future.get().unbindAll() } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {}
        }
    }

    // Timer для отображения длительности записи.
    LaunchedEffect(uiState.isRecording, uiState.recordingStartedAt) {
        if (uiState.isRecording && uiState.recordingStartedAt > 0) {
            while (true) {
                val elapsed = (System.currentTimeMillis() - uiState.recordingStartedAt) / 1000
                vm.updateRecordingSeconds(elapsed.toInt())
                if (elapsed >= CLIP_MAX_SEC) {
                    vm.stopRecording(context)
                    break
                }
                delay(250)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        // Top bar: close + lens-switch.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    vm.cancelRecording()
                    onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }
            IconButton(
                onClick = { vm.flipCamera() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Сменить камеру", tint = Color.White)
            }
        }

        // Bottom controls: record button + helper text.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isRecording) {
                Text(
                    text = formatSeconds(uiState.recordingSeconds),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = "Нажмите кнопку, чтобы записать клип\n(5–60 сек)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            RecordButton(
                isRecording = uiState.isRecording,
                progress = if (uiState.isRecording) {
                    uiState.recordingSeconds.toFloat() / CLIP_MAX_SEC
                } else 0f,
                canStop = uiState.recordingSeconds >= CLIP_MIN_SEC,
                onStart = { vm.startRecording(context, videoCapture) },
                onStop = { vm.stopRecording(context) },
            )
        }

        // Camera error overlay.
        uiState.cameraError?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
            ) {
                Text(err, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    progress: Float,
    canStop: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clickable {
                if (isRecording) {
                    if (canStop) onStop()
                } else {
                    onStart()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = Color.Red,
                strokeWidth = 4.dp,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(if (isRecording) 32.dp else 56.dp)
                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                .background(Color.Red),
        )
    }
}

@Composable
private fun PermissionRequestView(
    onGrant: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Для записи клипа нужен доступ к камере и микрофону",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onGrant) {
                    Text("Разрешить", color = Color.White)
                }
                TextButton(onClick = onBack) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Stage 2: Review — превью + поля + опубликовать
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStage(
    vm: ClipCreateViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    val uri = uiState.recordedUri ?: run {
        LaunchedEffect(Unit) { vm.resetToCamera() }
        return
    }

    // Превью через ExoPlayer (loop).
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(uri) {
        onDispose { try { player.release() } catch (_: Exception) {} }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { it.player = player },
        )

        IconButton(
            onClick = {
                try { player.release() } catch (_: Exception) {}
                vm.discardRecording()
            },
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Fix #140: navigationBarsPadding — чтобы нижний блок (описание +
                // кнопки) не перекрывался navigation bar в edge-to-edge.
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = vm::updateDescription,
                placeholder = { Text("Описание клипа", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))

            // Music picker (stub — TODO: добавить audio search dialog).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { /* TODO §37.12: music picker */ }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = uiState.musicTitle ?: "Добавить музыку",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.startPublish(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Опубликовать", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Stage 3: Publish — прогресс по стадиям
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun PublishStage(
    vm: ClipCreateViewModel,
    onPublished: (Long, Long) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by vm.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            val stages = listOf(
                "Подготовка…",
                "Загрузка видео…",
                "Обработка на сервере…",
                "Публикация…",
            )
            val currentStageIndex = when (uiState.publishStage) {
                PublishStage.Prepare -> 0
                PublishStage.Uploading -> 1
                PublishStage.Processing -> 2
                PublishStage.Finished -> 3
                else -> 0
            }
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stages.getOrNull(currentStageIndex) ?: "",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            uiState.publishError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = err,
                    color = Color.Red.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { vm.resetToCamera() }) {
                    Text("Повторить", color = Color.White)
                }
            }
        }
    }

    // Реакция на переход к Done — вызываем onPublished (для навигации).
    LaunchedEffect(uiState.stage) {
        if (uiState.stage == ClipCreateStage.Done) {
            val ticket = uiState.publishTicket
            if (ticket != null) {
                onPublished(ticket.ownerId, ticket.videoId)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Stage 4: Done
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun DoneStage(
    vm: ClipCreateViewModel,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Клип опубликован!",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    vm.resetToCamera()
                    onBack()
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Готово")
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Helpers
// ════════════════════════════════════════════════════════════════════════

private fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) String.format(Locale.getDefault(), "%d:%02d", m, s)
    else String.format(Locale.getDefault(), "0:%02d", s)
}

private fun clipCreateViewModelFactory(app: SovaApp) = viewModelFactory {
    initializer { ClipCreateViewModel(app) }
}
