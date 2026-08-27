// File: media/SirenTranscoder.kt
package re.pinok.media

import android.media.MediaExtractor
import android.media.MediaFormat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import re.pinok.util.AppLog
import java.io.File

/**
 * §42.12 P0 #2: Siren→AAC транскодер через ffmpeg-kit.
 *
 * VK отдаёт часть треков в проприетарном кодеке Siren (модификация ITU-T G.722.1,
 * 16kHz mono, 24-32 kbps). Сегменты HLS имеют magic byte `0x25` (не `0x47` как
 * MPEG-TS). ExoPlayer/MediaExtractor НЕ умеют декодировать Siren офлайн —
 * приходилось стримить через HLS онлайн (Wi-Fi бейдж в UI).
 *
 * Этот класс использует нативный ffmpeg (libavcodec умеет Siren через декодер
 * `g7221` / `siren`) для транскодинга .ts → .m4a (AAC 128kbps 44.1kHz stereo).
 * Результат — стандартный MP4, играющий офлайн во всех плеерах.
 *
 * Зависимость: `com.github.VineshChauhan24:ffmpeg-kit-android-16kb-lts-full-gpl:1.0.7`
 * (community fork — arthenica закрыла ffmpeg-kit в 2024 и удалила бинарники с
 * Maven Central). 16KB page-aligned, Full-GPL, ffmpeg 6.0. Пакет
 * com.arthenica.ffmpegkit сохранён — этот код работает без правок.
 * Размер: ~30-40 MB native libs. GPL из-за libfdk-aac + x264 + др.
 *
 * API-нюанс fork'а: в отличие от оригинального arthenica ffmpeg-kit, здесь
 * НЕТ `FFmpegKitConfig.setTimeout()`. Таймаут реализован вручную через
 * `executeWithArgumentsAsync` + `withTimeoutOrNull` + `FFmpegKit.cancel(sessionId)`
 * (см. [transcodeToM4a]).
 *
 * Поток:
 *  1. [transcodeToM4a] — точка входа. Принимает input .ts (raw siren concat)
 *     и output .m4a. Возвращает true при успехе.
 *  2. Парсит команду через FFmpegKitConfig.parseArguments, запускает асинхронно
 *     через executeWithArgumentsAsync с CompletableDeferred для ожидания.
 *  3. withTimeoutOrNull(FFMPEG_TIMEOUT_MS) — если за 2 минуты ffmpeg не завершился,
 *     вызываем FFmpegKit.cancel(sessionId) и возвращаем false.
 *  4. В callback'е проверяем ReturnCode.isSuccess + размер output ≥ 10KB
 *     + validateTranscodedM4a (MediaExtractor проверяет sr/ch/dur).
 *
 * Безопасность:
 *  — Все пути — File (не строки с user input), экранирование не нужно.
 *  — parseArguments сам корректно разбирает кавычки, отдельная ручная сборка
 *    строки больше не нужна — передаём массив аргументов напрямую.
 *  — Timeout: 2 минуты максимум на трек, потом cancel + delete output.
 *  — Логи: enableLogCallback → AppLog.d (verbose, только debug-build).
 *
 * Откат #SIREN-FIX: после внедрения этого транскодера siren-треки больше НЕ
 * кэшируются как codec=siren с Wi-Fi бейджем. Они транскодируются в aac и
 * играют офлайн. Старые siren-кэши (если есть у пользователя) остаются как .ts —
 * при первом воспроизведении пользователь может удалить их и скачать заново.
 */
object SirenTranscoder {

    private const val TAG = "SirenTranscoder"

    /** Минимальный размер выходного .m4a — защита от пустых/битых транскодов. */
    private const val MIN_OUTPUT_BYTES = 10_000L

    /** ffmpeg timeout в миллисекундах (2 минуты на трек). */
    private const val FFMPEG_TIMEOUT_MS = 120_000L

    init {
        // Включаем verbose-логи ffmpeg только в debug-сборке.
        // В release — тихо (логи ffmpeg очень шумные, засоряют logcat).
        if (re.pinok.BuildConfig.DEBUG) {
            FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_WARNING)
        } else {
            FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_QUIET)
        }
        // ВНИМАНИЕ: FFmpegKitConfig.setTimeout() в этом fork'е отсутствует
        // (arthenica оригинал имел, VineshChauhan24 форк — нет).
        // Таймаут реализован вручную в transcodeToM4a через
        // withTimeoutOrNull + FFmpegKit.cancel(sessionId).
    }

    /**
     * Транскодировать .ts (raw siren segments) → .m4a (AAC).
     *
     * @param inputTs  входной .ts файл (склеенные siren-сегменты, magic 0x25).
     * @param outputM4a выходной .m4a файл (будет перезаписан).
     * @return true если транскод успешен и output ≥ [MIN_OUTPUT_BYTES]
 *         и прошёл validateTranscodedM4a.
     *         false при ошибке ffmpeg, таймауте, слишком маленьком output
     *         или некорректных аудио-параметрах.
     */
    suspend fun transcodeToM4a(inputTs: File, outputM4a: File): Boolean {
        if (!inputTs.exists()) {
            AppLog.e(TAG, "transcodeToM4a: input not found: ${inputTs.absolutePath}")
            return false
        }
        val inputSize = inputTs.length()
        if (inputSize < MIN_OUTPUT_BYTES) {
            AppLog.e(TAG, "transcodeToM4a: input too small (${inputSize}B) — not a real siren stream")
            return false
        }

        // Удаляем старый output если был.
        outputM4a.delete()

        // Аргументы ffmpeg:
        //   -y              — overwrite output без вопроса
        //   -f mpegts       — force MPEG-TS demuxer (склеенный файл содержит
        //                     микс TS-сегментов и raw Siren; без -f mpegts
        //                     ffmpeg может выбрать неверный формат)
        //   -fflags +genpts — генерировать PTS для потоков без таймстемпов
        //                     (важно при миксе TS и raw Siren — потеря синхронизации)
        //   -ignore_unknown — пропустить неизвестные потоки (raw Siren данные
        //                     внутри TS-контейнера могут быть определены как
        //                     неизвестный stream)
        //   -i input.ts     — входной файл
        //   -vn             — нет видео (аудио-only, на всякий случай)
        //   -c:a aac        — кодек AAC (libfdk_aac недоступен в audio-build,
        //                     используем нативный aac encoder)
        //   -b:a 128k       — битрейт 128 kbps (VK quality)
        //   -ar 44100       — sample rate 44.1 kHz (resample из 16kHz siren)
        //   -ac 2           — stereo (upmix из mono siren — для совместимости
        //                     с BT A2DP и эквалайзером, которые ожидают stereo)
        //   -movflags +faststart — moov atom в начале (fast streaming start)
        //   output.m4a
        //
        // Fix #SIREN-PLAYBACK: добавлены -f mpegts, -fflags +genpts,
        // -ignore_unknown. Без них ffmpeg терял синхронизацию на
        // миксе TS/Siren сегментов → M4A с 88200Hz и чередующимся
        // mono/stereo → ExoPlayer rapidly cycling AudioTracks.
        //
        // Передаём массивом аргументов напрямую (не строкой с кавычками) —
        // FFmpegKitConfig.parseArguments нам НЕ нужен, executeWithArgumentsAsync
        // принимает String[] и сам корректно передаст пути с пробелами.
        val arguments = arrayOf(
            "-y",
            "-f", "mpegts",
            "-fflags", "+genpts",
            "-ignore_unknown",
            "-i", inputTs.absolutePath,
            "-vn",
            "-c:a", "aac",
            "-b:a", "128k",
            "-ar", "44100",
            "-ac", "2",
            "-movflags", "+faststart",
            outputM4a.absolutePath
        )

        AppLog.i(TAG, "transcodeToM4a: starting ffmpeg (${inputSize / 1024} KB input)")

        // CompletableDeferred для ожидания завершения async-callback'а.
        // Завершается ровно один раз — true при успехе, false при ошибке.
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()

        // Запускаем асинхронно. Метод возвращает сразу — мы получаем session,
        // callback сработает в native-потоке ffmpeg-kit по завершении.
        val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(
            arguments
        ) { completed ->
            onSessionCompleted(completed, outputM4a, inputSize, completion)
        }

        // Ждём завершения с таймаутом. Если за FFMPEG_TIMEOUT_MS ffmpeg не успел —
        // отменяем сессию вручную (native поток убивается через FFmpegKit.cancel).
        val result: Boolean? = kotlinx.coroutines.withTimeoutOrNull(FFMPEG_TIMEOUT_MS) {
            completion.await()
        }

        if (result != null) {
            // Нормальное завершение (успех или ошибка ffmpeg — ответ в result).
            return result
        }

        // Таймаут истёк — ffmpeg всё ещё работает. Отменяем.
        AppLog.e(
            TAG,
            "transcodeToM4a: TIMEOUT after ${FFMPEG_TIMEOUT_MS}ms — cancelling session ${session.sessionId}"
        )
        FFmpegKit.cancel(session.sessionId)

        // Даём cancel-у гонку завершиться — короткое ожидание, чтобы callback
        // успел почистить state. 500мс должно хватить (native сигнал доставляется быстро).
        kotlinx.coroutines.withTimeoutOrNull(2_000L) { completion.await() }

        outputM4a.delete()
        return false
    }

    /**
     * Callback завершения ffmpeg-сессии. Вызывается native потоком ffmpeg-kit.
     * Проверяет returnCode, state, размер output и аудио-параметры (MediaExtractor),
     * завершает [completion].
     */
    private fun onSessionCompleted(
        session: FFmpegSession,
        outputFile: File,
        inputSize: Long,
        completion: kotlinx.coroutines.CompletableDeferred<Boolean>
    ) {
        val state = session.state
        val returnCode = session.returnCode

        // Сначала проверим state — если сессию отменили (cancel), state=FAILED.
        if (state == SessionState.FAILED) {
            val logs = session.allLogsAsString
            val safeLogs: String = if (logs != null) logs else ""
            val logPreview: String = if (safeLogs.length > 500) {
                safeLogs.substring(safeLogs.length - 500)
            } else {
                safeLogs
            }
            AppLog.e(TAG, "transcodeToM4a: session FAILED (state). Last logs:\n$logPreview")
            outputFile.delete()
            completion.complete(false)
            return
        }

        if (!ReturnCode.isSuccess(returnCode)) {
            val logs = session.allLogsAsString
            val safeLogs: String = if (logs != null) logs else ""
            val logPreview: String = if (safeLogs.length > 500) {
                safeLogs.substring(safeLogs.length - 500)
            } else {
                safeLogs
            }
            AppLog.e(
                TAG,
                "transcodeToM4a: ffmpeg returnCode=${returnCode.value} (not success). Last logs:\n$logPreview"
            )
            outputFile.delete()
            completion.complete(false)
            return
        }

        val outSize = outputFile.length()
        if (outSize < MIN_OUTPUT_BYTES) {
            AppLog.e(
                TAG,
                "transcodeToM4a: output too small (${outSize}B, expected ≥$MIN_OUTPUT_BYTES) — transcode produced empty file"
            )
            outputFile.delete()
            completion.complete(false)
            return
        }

        // Fix #SIREN-PLAYBACK: валидация аудио-параметров транскодированного файла.
        // ffmpeg может «успешно» создать M4A с битым аудио-контентом
        // (неправильный sample-rate, чередующийся channel-count) — структурно
        // файл валиден (ftyp+moov+mdat), но ExoPlayer производит:
        //   - AudioTrack на 88200 Hz вместо 44100
        //   - Rapid AudioTrack cycling (create→stop→create каждый 30-2000мс)
        //   - Позиция продвигается в 20x быстрее реального времени
        // Проверяем через MediaExtractor: sample-rate == 44100, channels == 2,
        // duration > 0. Если что-то не совпадает — считаем транскод проваленным.
        if (!validateTranscodedM4a(outputFile)) {
            AppLog.e(TAG, "transcodeToM4a: output validation FAILED — file has incorrect audio parameters, deleting")
            outputFile.delete()
            completion.complete(false)
            return
        }

        AppLog.i(
            TAG,
            "transcodeToM4a: SUCCESS — ${outSize / 1024} KB output (${inputSize / 1024} KB input, ratio=${String.format(
                "%.2f",
                outSize.toFloat() / inputSize
            )})"
        )
        completion.complete(true)
    }

    /**
     * Fix #SIREN-PLAYBACK: валидация транскодированного M4A файла.
     *
     * Открывает файл через MediaExtractor и проверяет:
     *   - Sample rate == 44100 Hz (допуск ±100 Hz для целочисленного округления)
     *   - Channel count == 2 (stereo)
     *   - Duration > 0 (файл содержит реальный аудио-контент)
     *
     * Если любой параметр не совпадает — файл содержит битый аудио-контент
     * (типично для некорректного Siren→AAC транскодинга) и не должен
     * использоваться для офлайн-воспроизведения.
     */
    private fun validateTranscodedM4a(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var validated = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    val sampleRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { -1 }
                    val channels = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { -1 }
                    val durationUs = try { format.getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { -1L }
                    val durationSec = durationUs / 1_000_000
                    // AAC LC encoder округляет 44100 → 44100 (точно).
                    // Допуск ±100 Hz для защиты от округления на других энкодерах.
                    val srOk = sampleRate in 44000..44200
                    val chOk = channels == 2
                    val durOk = durationUs > 0
                    if (srOk && chOk && durOk) {
                        AppLog.i(TAG, "validateTranscodedM4a: OK — sr=${sampleRate}Hz ch=$channels dur=${durationSec}s (${file.length() / 1024}KB)")
                        validated = true
                    } else {
                        AppLog.e(TAG, "validateTranscodedM4a: FAIL — sr=${sampleRate}Hz${if (!srOk) " (expected 44100)" else ""} ch=$channels${if (!chOk) " (expected 2)" else ""} dur=${durationUs}us${if (!durOk) " (expected >0)" else ""}")
                    }
                    break // Проверяем только первый audio track
                }
            }
            if (!validated && extractor.trackCount == 0) {
                AppLog.e(TAG, "validateTranscodedM4a: FAIL — no tracks in M4A")
            }
            validated
        } catch (e: Exception) {
            AppLog.e(TAG, "validateTranscodedM4a: exception: ${e.message}")
            false
        } finally {
            extractor.release()
        }
    }

    /**
     * #AUDIO-MP3: Транскодировать вход (m4a/ts) → .mp3 через ffmpeg-kit.
     * VK Music Saver: libmp3lame-кодирование + ID3v2-теги.
     * #MERGE-213115F: union — libmp3lame из снапшота (правильное декодирование
     * AAC/Siren вместо битого -c:a copy) + метаданные title/artist/album/quality
     * из ветки звонков.
     */
    suspend fun transcodeToMp3(
        inputM4a: File,
        outputMp3: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        quality: String? = null,
    ): Boolean {
        if (!inputM4a.exists()) {
            AppLog.e(TAG, "transcodeToMp3: input not found: ${inputM4a.absolutePath}")
            return false
        }
        val inputSize = inputM4a.length()
        if (inputSize < MIN_OUTPUT_BYTES) {
            AppLog.e(TAG, "transcodeToMp3: input too small (${inputSize}B)")
            return false
        }
        outputMp3.delete()
        val args = mutableListOf(
            "-y", "-i", inputM4a.absolutePath, "-vn",
            "-c:a", "libmp3lame",
            "-id3v2_version", "3"
        )
        // PinoK style: без elvis (?:) — явный if.
        if (quality != null) { args.add("-b:a"); args.add(quality) } else { args.add("-b:a"); args.add("192k") }
        if (title != null) { args.add("-metadata"); args.add("title=$title") }
        if (artist != null) { args.add("-metadata"); args.add("artist=$artist") }
        if (album != null) { args.add("-metadata"); args.add("album=$album") }
        args.add(outputMp3.absolutePath)
        val arguments = args.toTypedArray()
        AppLog.i(TAG, "transcodeToMp3: starting ffmpeg (${inputSize / 1024} KB input)")
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val session = FFmpegKit.executeWithArgumentsAsync(arguments) { completed ->
            onSessionCompleted(completed, outputMp3, inputSize, completion)
        }
        val result: Boolean? = kotlinx.coroutines.withTimeoutOrNull(FFMPEG_TIMEOUT_MS) {
            completion.await()
        }
        if (result != null) return result
        AppLog.e(TAG, "transcodeToMp3: TIMEOUT — cancelling session ${session.sessionId}")
        FFmpegKit.cancel(session.sessionId)
        kotlinx.coroutines.withTimeoutOrNull(2_000L) { completion.await() }
        outputMp3.delete()
        return false
    }

    /**
     * Проверить, поддерживает ли установленный ffmpeg-kit декодер Siren/G.722.1.
     */
    fun checkSirenDecoderAvailable(): Boolean {
        val session = FFmpegKit.execute("-decoders")
        if (!ReturnCode.isSuccess(session.returnCode)) {
            AppLog.w(
                TAG,
                "checkSirenDecoderAvailable: cannot list decoders (returnCode=${session.returnCode.value})"
            )
            return false
        }
        val output = session.allLogsAsString
        val hasG7221 = output != null && output.contains("g7221")
        val hasSiren = output != null && output.contains("siren")
        val available = hasG7221 || hasSiren
        AppLog.i(TAG, "checkSirenDecoderAvailable: g7221=$hasG7221 siren=$hasSiren → available=$available")
        return available
    }
}
