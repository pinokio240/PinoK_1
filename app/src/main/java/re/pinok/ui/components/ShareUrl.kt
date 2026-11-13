package re.pinok.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import re.pinok.util.AppLog

/**
 * Волна 2 (2026-11-13): общий share-helper для ссылки/текста.
 *
 * Заменяет ~11 инлайн-блоков `Intent(ACTION_SEND){type=text/plain; putExtra(EXTRA_TEXT)}`
 * + createChooser в проекте (фото, темы, ссылки в посте, channel-link).
 * По образцу существующего [shareTrack] (TrackShare.kt).
 */
fun shareUrl(context: Context, text: String, chooserTitle: String = "Поделиться") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
        // FLAG_ACTIVITY_NEW_TASK нужен только для non-Activity context.
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        AppLog.w("ShareUrl", "share: no app to handle intent", e)
        Toast.makeText(context, "Нет приложений для передачи", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        AppLog.e("ShareUrl", "share failed", e)
        Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}