package re.pinok.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import re.pinok.data.model.Track
import re.pinok.util.AppLog

/**
 * Общий helper для share трека (VK audio) через Android ACTION_SEND chooser.
 * Текст: "Title — Artist\nhttps://vk.com/audio{ownerId}_{id}".
 * Выносит логику из AudioPlayerScreen и MusicScreen, чтобы не дублировать.
 */
fun shareTrack(context: Context, track: Track) {
    val shareText = "${track.title} — ${track.artist}\nhttps://vk.com/audio${track.ownerId}_${track.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(intent, "Поделиться").apply {
        // FLAG_ACTIVITY_NEW_TASK нужен только для non-Activity context.
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        AppLog.w("TrackShare", "share: no app to handle intent", e)
        Toast.makeText(context, "Нет приложений для передачи", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        AppLog.e("TrackShare", "share failed", e)
        Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}