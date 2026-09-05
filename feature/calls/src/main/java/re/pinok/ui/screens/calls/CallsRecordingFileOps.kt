package re.pinok.ui.screens.calls

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-05): Этап В1 плана «звонки.перенос.план.md» (§4-В/§1.4)
 * — реальные системные действия карточки записи и файла расшифровки:
 *  - «Скачать» — системный DownloadManager (context.getSystemService), файл
 *    кладётся в общую папку Downloads с уведомлением о завершении;
 *  - «Скопировать ссылку» — ClipboardManager (context.getSystemService);
 *  - «Открыть» — ACTION_VIEW во внешнем браузере/плеере.
 *
 * DownloadManager сам выполняет сетевую загрузку в системном процессе:
 * enqueue() только регистрирует задачу и не блокирует main-поток
 * (#ANR-MAIN-IO). Ограничение (честно, не заглушка): системный загрузчик
 * не передаёт куки/авторизационные заголовки PinoK — если CDN записи
 * потребует их, загрузка завершится ошибкой в шторке системы.
 *
 * #NULL-EXPLICIT: без non-null assertion, safe-call и elvis операторов.
 */

/** Санитизация названия записи в имя файла для Downloads. */
internal fun recordingFileName(title: String, url: String): String {
    val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    var ext = ""
    val q = url.indexOf('?')
    val path = if (q >= 0) url.substring(0, q) else url
    val dot = path.lastIndexOf('.')
    val slash = path.lastIndexOf('/')
    if (dot > slash && dot >= 0) {
        val candidate = path.substring(dot)
        if (candidate.length <= 5) ext = candidate
    }
    if (safeTitle.isEmpty()) return "call_recording" + ext
    return safeTitle + ext
}

/**
 * Скачать файл по URL в системную папку Downloads (реальный DownloadManager).
 * Возвращает true, если задача зарегистрирована.
 */
internal fun downloadToDownloads(context: Context, url: String, title: String, tag: String): Boolean {
    return try {
        val service = context.getSystemService(Context.DOWNLOAD_SERVICE)
        if (service is DownloadManager) {
            val fileName = recordingFileName(title, url)
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(fileName)
            request.setDescription("PinoK · Звонки")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            request.setAllowedOverMetered(true)
            service.enqueue(request)
            AppLog.i(tag, "download queued: $fileName")
            Toast.makeText(context, "Скачивание начато: $fileName", Toast.LENGTH_SHORT).show()
            true
        } else {
            AppLog.e(tag, "DownloadManager service unavailable")
            Toast.makeText(context, "Системный загрузчик недоступен", Toast.LENGTH_SHORT).show()
            false
        }
    } catch (e: Exception) {
        val m = e.message
        if (m == null) {
            AppLog.e(tag, "download failed", e)
        } else {
            AppLog.e(tag, "download failed: $m", e)
        }
        Toast.makeText(context, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Скопировать текст (ссылку) в системный буфер обмена. */
internal fun copyToClipboard(context: Context, text: String, successMsg: String, tag: String) {
    try {
        val service = context.getSystemService(Context.CLIPBOARD_SERVICE)
        if (service is ClipboardManager) {
            service.setPrimaryClip(ClipData.newPlainText("PinoK", text))
            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            AppLog.i(tag, "link copied to clipboard")
        } else {
            AppLog.e(tag, "ClipboardManager service unavailable")
        }
    } catch (e: Exception) {
        val m = e.message
        if (m == null) {
            AppLog.e(tag, "copy failed", e)
        } else {
            AppLog.e(tag, "copy failed: $m", e)
        }
        Toast.makeText(context, "Не удалось скопировать ссылку", Toast.LENGTH_SHORT).show()
    }
}

/** Открыть URL во внешнем обработчике (браузер/плеер VK Видео). */
internal fun openUrlExternally(context: Context, url: String, tag: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        AppLog.i(tag, "open externally: $url")
    } catch (e: Exception) {
        val m = e.message
        if (m == null) {
            AppLog.e(tag, "open failed", e)
        } else {
            AppLog.e(tag, "open failed: $m", e)
        }
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
