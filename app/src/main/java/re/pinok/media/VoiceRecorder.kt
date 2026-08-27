package re.pinok.media

import android.media.MediaRecorder
import android.os.Build
import re.pinok.util.AppLog
import java.io.File

/**
 * Singleton voice recorder that produces audio files suitable for VK API upload.
 *
 * On API 29+ (Q) the output is OGG/Opus — exactly what VK expects (`audio/ogg`).
 * On older APIs where MediaRecorder lacks Opus support, falls back to AAC/M4A
 * and keeps the `.ogg` extension — VK's backend handles the transcoding.
 *
 * All public mutating methods are `@Synchronized` for thread safety.
 */
object VoiceRecorder {

    private const val TAG = "VoiceRecorder"

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    @Synchronized
    fun startRecording(outputFile: File) {
        if (recorder != null) {
            AppLog.w(TAG, "Already recording — ignoring startRecording()")
            return
        }

        try {
            val instance = createRecorder()

            if (Build.VERSION.SDK_INT >= 29) {
                // OGG / Opus — preferred by VK
                instance.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setOutputFile(outputFile.absolutePath)
                    setAudioSamplingRate(48000)
                    setAudioEncodingBitRate(32000)
                }
                AppLog.i(TAG, "Starting OGG/Opus recording → ${outputFile.absolutePath}")
            } else {
                // AAC / M4A fallback — VK accepts and re-encodes
                instance.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outputFile.absolutePath)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(64000)
                }
                AppLog.i(
                    TAG,
                    "Starting AAC/M4A fallback recording (API ${Build.VERSION.SDK_INT}) " +
                        "→ ${outputFile.absolutePath}"
                )
            }

            instance.prepare()
            instance.start()

            recorder = instance
            currentFile = outputFile
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to start recording", e)
            releaseRecorder()
            throw e
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        val rec = recorder ?: run {
            AppLog.w(TAG, "stopRecording() called but not recording")
            return null
        }

        return try {
            rec.stop()
            AppLog.i(TAG, "Recording stopped → ${currentFile?.absolutePath}")
            currentFile
        } catch (e: Exception) {
            AppLog.e(TAG, "Error stopping recorder", e)
            null
        } finally {
            releaseRecorder()
        }
    }

    @Synchronized
    fun isRecording(): Boolean = recorder != null

    @Synchronized
    fun getAmplitude(): Int {
        return try {
            val rec = recorder ?: return 0
            rec.maxAmplitude
        } catch (e: Exception) {
            AppLog.w(TAG, "Error reading amplitude", e)
            0
        }
    }

    @Synchronized
    fun cancelRecording() {
        val rec = recorder
        if (rec == null) {
            AppLog.w(TAG, "cancelRecording() called but not recording")
            return
        }

        try {
            rec.stop()
        } catch (e: Exception) {
            AppLog.w(TAG, "Error stopping recorder during cancel", e)
        } finally {
            releaseRecorder()
            currentFile?.let { file ->
                if (file.exists() && !file.delete()) {
                    AppLog.w(TAG, "Could not delete cancelled recording: ${file.absolutePath}")
                } else {
                    AppLog.i(TAG, "Cancelled recording deleted: ${file.absolutePath}")
                }
            }
            currentFile = null
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun createRecorder(): MediaRecorder {
        @Suppress("DEPRECATION")
        return MediaRecorder()
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            AppLog.w(TAG, "Error releasing MediaRecorder", e)
        }
        recorder = null
    }
}