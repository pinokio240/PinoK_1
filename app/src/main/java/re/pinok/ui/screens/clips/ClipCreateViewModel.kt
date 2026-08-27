package re.pinok.ui.screens.clips

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.video.VideoCapture
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ClipCreateViewModel"

/** Стадии UI экрана создания клипа. */
enum class ClipCreateStage {
    Camera, Review, Publish, Done
}

/** Стадии процесса публикации (для прогресс-бара). */
enum class PublishStage {
    Idle, Prepare, Uploading, Processing, Finished, Failed
}

data class ClipCreateUiState(
    val stage: ClipCreateStage = ClipCreateStage.Camera,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isRecording: Boolean = false,
    val recordingStartedAt: Long = 0L,
    val recordingSeconds: Int = 0,
    val recordedUri: Uri? = null,
    val description: String = "",
    val musicTitle: String? = null,
    val cameraError: String? = null,
    val publishStage: PublishStage = PublishStage.Idle,
    val publishError: String? = null,
    val publishTicket: VKApiClient.VideoUploadTicket? = null,
)

/**
 * §37.12 Phase 5: ViewModel для ClipCreateScreen.
 *
 * Хранит:
 *  - текущий stage (Camera / Review / Publish / Done)
 *  - настройки камеры (lensFacing)
 *  - состояние записи (isRecording, длительность, выходной Uri)
 *  - текстовые поля (description, musicTitle)
 *  - прогресс публикации (video.save → upload → processing)
 *
 * CameraX VideoCapture use-case передаётся из Composable в [startRecording]/
 * [stopRecording] — ViewModel не держит lifecycle-cameraProvider, только
 * текущий Recording ref (для stop).
 */
class ClipCreateViewModel(
    private val app: SovaApp,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClipCreateUiState())
    val uiState: StateFlow<ClipCreateUiState> = _uiState.asStateFlow()

    /** Текущая запись CameraX (для stopRecording). */
    private var activeRecording: Recording? = null

    /** Флаг отмены — чтобы callback не перевёл в Review при cancel. */
    private var canceled: Boolean = false

    /** Папка для записей (в cacheDir — FileProvider настроен). */
    private val clipsDir: File by lazy {
        File(app.cacheDir, "clips").apply { mkdirs() }
    }

    // ── Camera ───────────────────────────────────────────────────────────

    fun hasCameraPermission(): Boolean {
        val cam = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    fun flipCamera() {
        _uiState.update { s ->
            val newLens = if (s.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
            s.copy(lensFacing = newLens)
        }
    }

    // ── Recording ─────────────────────────────────────────────────────────

    /**
     * Начать запись клипа.
     *
     * @param context для MainExecutor
     * @param videoCapture VideoCapture<Recorder> use-case из Composable
     */
    fun startRecording(context: Context, videoCapture: VideoCapture<Recorder>) {
        if (_uiState.value.isRecording) return
        try {
            // Создаём выходной файл в cacheDir/clips/.
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(clipsDir, "clip_$ts.mp4")
            // Чистим старые клипы (>1 часа), чтобы cacheDir не разрастался.
            clipsDir.listFiles()?.forEach { f ->
                if (System.currentTimeMillis() - f.lastModified() > 3_600_000L) f.delete()
            }

            val outputOptions = FileOutputOptions.Builder(outFile).build()
            canceled = false

            // CameraX 1.4+: start() принимает Consumer<VideoRecordEvent>.
            // События: Status (промежуточные, игнорируем) + Finalize (финал).
            activeRecording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(
                    ContextCompat.getMainExecutor(context),
                    Consumer { event -> handleRecordEvent(event, outFile) },
                )

            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingStartedAt = System.currentTimeMillis(),
                    recordingSeconds = 0,
                    cameraError = null,
                )
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "startRecording error", e)
            _uiState.update { it.copy(cameraError = "Не удалось начать запись: ${e.message}") }
        }
    }

    /**
     * Обработчик событий записи CameraX.
     *
     * CameraX 1.4+ присылает поток VideoRecordEvent:
     *  - Status    — промежуточные обновления (recordingStats), игнорируем
     *  - Finalize  — финальный результат с outputResults.outputUri + cause
     *
     * Только на Finalize завершаем запись и переходим дальше.
     *
     * NB: проверка ошибки через cause:Throwable? (null = успех, non-null = ошибка),
     * а не через errorType/ERROR_NONE — эти поля/константы переименовывались между
     * минорными версиями CameraX 1.4.x. cause остаётся стабильным контрактом.
     */
    private fun handleRecordEvent(event: VideoRecordEvent, outFile: File) {
        // Игнорируем промежуточные Status-события.
        if (event !is VideoRecordEvent.Finalize) return

        val wasCanceled = canceled
        activeRecording = null
        if (wasCanceled) {
            try { outFile.delete() } catch (_: Exception) {}
            return
        }
        // cause == null означает успешное завершение записи.
        val cause = event.cause
        val errorMsg: String? = if (cause != null) {
            cause.message?.takeIf { it.isNotBlank() }?.let { "Ошибка записи: $it" }
                ?: "Ошибка записи (${cause.javaClass.simpleName})"
        } else null
        if (errorMsg != null) {
            try { outFile.delete() } catch (_: Exception) {}
            _uiState.update {
                it.copy(
                    isRecording = false,
                    cameraError = errorMsg,
                )
            }
            return
        }
        // Успех — outputUri из OutputResults, fallback на FileProvider.
        // outputUri всегда non-null (контракт CameraX), но может быть Uri.EMPTY.
        val uri = try {
            val outUri = event.outputResults.outputUri
            if (outUri != Uri.EMPTY) outUri
            else FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            AppLog.e(TAG, "FileProvider error", e)
            Uri.fromFile(outFile)
        }
        _uiState.update {
            it.copy(
                isRecording = false,
                recordedUri = uri,
                stage = ClipCreateStage.Review,
            )
        }
    }

    fun stopRecording(context: Context) {
        if (!_uiState.value.isRecording) return
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            AppLog.e(TAG, "stopRecording error", e)
            activeRecording = null
            // Принудительно завершаем запись в UI.
            _uiState.update { it.copy(isRecording = false) }
        }
    }

    fun cancelRecording() {
        canceled = true
        try { activeRecording?.stop() } catch (_: Exception) {}
        activeRecording = null
        _uiState.value.recordedUri?.let { uri ->
            try {
                if (uri.scheme == "file") File(uri.path ?: "").delete()
            } catch (_: Exception) {}
        }
        _uiState.update { ClipCreateUiState(lensFacing = it.lensFacing) }
    }

    fun discardRecording() {
        // Удалить записанный файл и вернуться к камере.
        _uiState.value.recordedUri?.let { uri ->
            try {
                if (uri.scheme == "file") File(uri.path ?: "").delete()
            } catch (_: Exception) {}
        }
        _uiState.update { ClipCreateUiState(lensFacing = it.lensFacing) }
    }

    fun resetToCamera() {
        _uiState.update { ClipCreateUiState(lensFacing = it.lensFacing) }
    }

    fun updateRecordingSeconds(sec: Int) {
        _uiState.update { it.copy(recordingSeconds = sec) }
    }

    // ── Review fields ─────────────────────────────────────────────────────

    fun updateDescription(text: String) {
        _uiState.update { it.copy(description = text) }
    }

    // ── Publish pipeline ──────────────────────────────────────────────────

    /**
     * Запустить пайплайн публикации:
     *  1) video.save (is_clips=1) → upload_url + video_id
     *  2) POST video_file на upload_url
     *  3) ждать обработки (пауза 8 сек — VK транскодит асинхронно)
     *  4) Done
     *
     * На любой ошибке — stage=Failed с сообщением, видео удаляется через video.delete.
     */
    fun startPublish(context: Context) {
        val uri = _uiState.value.recordedUri ?: run {
            _uiState.update { it.copy(publishError = "Нет записанного видео") }
            return
        }
        _uiState.update {
            it.copy(
                stage = ClipCreateStage.Publish,
                publishStage = PublishStage.Prepare,
                publishError = null,
            )
        }
        viewModelScope.launch {
            val api = app.apiClient
            // Шаг 1: video.save.
            val ticket = withContext(Dispatchers.IO) {
                api.videoSave(
                    name = "Clip ${SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())}",
                    description = _uiState.value.description.takeIf { it.isNotBlank() },
                    isClips = true,
                    groupId = null, // TODO: выбор группы для сообществ-clips
                )
            }
            if (ticket == null) {
                publishFail("Не удалось подготовить видео (video.save)")
                return@launch
            }
            _uiState.update {
                it.copy(
                    publishTicket = ticket,
                    publishStage = PublishStage.Uploading,
                )
            }

            // Шаг 2: upload file.
            val uploaded = withContext(Dispatchers.IO) {
                api.videoUploadFile(ticket.uploadUrl, uri)
            }
            if (!uploaded) {
                // Чистим зарезервированный video.
                withContext(Dispatchers.IO) { api.videoDeleteClip(ticket.videoId, ticket.ownerId) }
                publishFail("Не удалось загрузить видео. Проверьте соединение.")
                return@launch
            }

            // Шаг 3: обработка на сервере (пауза — реальный status-poll через
            // video.get/image_processing, но VK обычно готово за ~8 сек).
            _uiState.update { it.copy(publishStage = PublishStage.Processing) }
            delay(8000)

            // Шаг 4: публикация готова.
            _uiState.update {
                it.copy(
                    publishStage = PublishStage.Finished,
                    stage = ClipCreateStage.Done,
                )
            }
        }
    }

    private fun publishFail(message: String) {
        _uiState.update {
            it.copy(
                publishStage = PublishStage.Failed,
                publishError = message,
            )
        }
    }
}
