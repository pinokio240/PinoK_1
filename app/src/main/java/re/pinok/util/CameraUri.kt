package re.pinok.util

import androidx.compose.runtime.saveable.Saver
import android.net.Uri
import java.text.DecimalFormat

/**
 * Волна 3 (2026-11-15): общий хелпер для снимка с камеры через FileProvider.
 *
 * Раньше был продублирован 4 раза (ChatDetailScreen/FeedScreen/PostDetailScreen/
 * BoardTopicScreen) — теперь единая точка. FileProvider authority «<pkg>.fileprovider»
 * уже заявлен в manifest; cache-path разрешён.
 */
fun createCameraImageUri(ctx: android.content.Context): Uri? {
    return try {
        val photoFile = java.io.File(ctx.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            photoFile,
        )
    } catch (e: Exception) {
        AppLog.e("CameraUri", "createCameraImageUri failed", e)
        null
    }
}

/**
 * Saver для Uri — rememberSaveable переживает process death во время съёмки
 * (камера может убить процесс приложения; без Saver снимок теряется).
 * Раньше дублировался (UriSaver/CameraUriSaver в 4 экранах).
 */
val UriSaver: Saver<Uri?, String> = Saver(
    save = { it?.toString() ?: "" },
    restore = { saved -> if (saved.isBlank()) null else Uri.parse(saved) },
)

/**
 * Волна 3 (2026-11-15): форматтер размера файла «Б / КБ / МБ».
 * Раньше был продублирован 4 раза (FeedScreen.formatDocSize,
 * ChatDetailScreen.formatFileSize, PostDetailScreen.formatFileSize,
 * AttachmentPickerSheet.formatDocSizeShort) с чуть разной точностью.
 * Объединяем: [decimals]=false даёт целые КБ (как в Feed/AttachmentPicker),
 * [decimals]=true — один знак после запятой (как в Chat/PostDetail).
 */
fun formatFileSize(size: Long, decimals: Boolean = false): String {
    if (size < 1024) return "$size Б"
    if (size < 1024 * 1024) {
        return if (decimals) {
            DecimalFormat("#.#").format(size / 1024.0) + " КБ"
        } else {
            "${size / 1024} КБ"
        }
    }
    return if (decimals) {
        DecimalFormat("#.#").format(size / (1024.0 * 1024.0)) + " МБ"
    } else {
        String.format("%.1f МБ", size / 1024.0 / 1024.0)
    }
}

/**
 * Волна 3 (2026-11-15): копирование текста в системный буфер обмена с Toast.
 * Раньше дублировалось ~15 раз (ClipboardManager + ClipData + Toast + AppLog).
 * @param okMessage текст Toast при успехе (по умолчанию «Скопировано»).
 */
fun copyTextToClipboard(
    ctx: android.content.Context,
    text: String,
    okMessage: String = "Скопировано",
    logTag: String? = null,
) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
    if (cm != null) {
        cm.setPrimaryClip(android.content.ClipData.newPlainText("VK", text))
        android.widget.Toast.makeText(ctx, okMessage, android.widget.Toast.LENGTH_SHORT).show()
        if (logTag != null) AppLog.i(logTag, "copied to clipboard")
    } else {
        android.widget.Toast.makeText(ctx, "Не удалось получить буфер обмена", android.widget.Toast.LENGTH_SHORT).show()
    }
}