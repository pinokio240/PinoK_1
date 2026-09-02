// File: media/PlaybackPositionStore.kt
package re.pinok.media

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import re.pinok.util.AppLog
import java.io.File

/**
 * #39 C2: Persistent playback position — позиция воспроизведения аудио/видео
 * переживает рестарт приложения.
 *
 * По образцу VK web `videoplayer_prefs.position.<uid>.<owner_video> = {date, pos}`.
 *
 * Хранение: JSON-файл `filesDir/playback_positions.json` с map
 * `<mediaId> → {posMs, date}`. mediaId для аудио = `trackId.toString()`,
 * для видео = [videoKey] (`"v_ownerId_videoId"`).
 *
 * Запись дебаунсится (не чаще раза в [SAVE_INTERVAL_MS]) чтобы не нагружать
 * диск при частых тиках прогресса плеера (500мс). Финальная позиция
 * принудительно сбрасывается через [flush] при pause/transition/dispose.
 *
 * Позиции < [MIN_SAVE_POS_MS] не сохраняются (трек только начался —
 * восстанавливать 1-2с бессмысленно). Позиции > 95% длительности очищаются
 * (трек дослушан — при следующем запуске стартуем с начала).
 */
object PlaybackPositionStore {

    private const val TAG = "PlaybackPositionStore"
    private const val FILE_NAME = "playback_positions.json"
    private const val SAVE_INTERVAL_MS = 5000L
    private const val MAX_ENTRIES = 1000
    private const val MIN_SAVE_POS_MS = 3000L

    @Volatile
    private var appContext: Context? = null

    /** Map: mediaId → positionMs. LinkedHashMap для LRU-подобного порядка. */
    @Volatile
    private var positions: MutableMap<String, Long> = LinkedHashMap()

    @Volatile
    private var lastSaveTs: Long = 0L

    @Volatile
    private var dirty: Boolean = false

    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            appContext = context.applicationContext
            loadFromDisk()
        }
    }

    private fun loadFromDisk() {
        try {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, FILE_NAME)
            if (!file.exists()) {
                AppLog.i(TAG, "No existing positions file — starting fresh")
                return
            }
            val json = file.readText()
            val obj = JsonParser.parseString(json).asJsonObject
            val map = LinkedHashMap<String, Long>()
            for ((key, value) in obj.entrySet()) {
                try {
                    val posObj = value.asJsonObject
                    val pos = posObj.get("posMs")?.asLong ?: continue
                    map[key] = pos
                } catch (_: Exception) { /* skip malformed entry */ }
            }
            positions = map
            AppLog.i(TAG, "Loaded ${map.size} playback positions from disk")
        } catch (e: Exception) {
            AppLog.w(TAG, "loadFromDisk failed: ${e.message}")
        }
    }

    private fun saveToDisk() {
        try {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, FILE_NAME)

            // LRU-обрезка: если записей слишком много — оставляем последние MAX_ENTRIES.
            if (positions.size > MAX_ENTRIES) {
                val toKeep = positions.entries.toList()
                    .takeLast(MAX_ENTRIES)
                    .associate { it.toPair() }
                positions = LinkedHashMap(toKeep)
            }

            val obj = JsonObject()
            val now = System.currentTimeMillis()
            for ((key, pos) in positions) {
                val entry = JsonObject()
                entry.addProperty("posMs", pos)
                entry.addProperty("date", now)
                obj.add(key, entry)
            }
            file.writeText(Gson().toJson(obj))
            lastSaveTs = now
            dirty = false
        } catch (e: Exception) {
            AppLog.w(TAG, "saveToDisk failed: ${e.message}")
        }
    }

    /**
     * Получить сохранённую позицию (мс) или 0 если нет.
     * Вызывается из UI-потока при восстановлении — быстро (in-memory read).
     */
    fun getPosition(mediaId: String): Long {
        return positions[mediaId] ?: 0L
    }

    /**
     * Сохранить позицию. Дебаунс: реальная запись на диск не чаще
     * [SAVE_INTERVAL_MS]. Безопасно вызывать на каждом тике прогресса (500мс).
     */
    fun savePosition(mediaId: String, posMs: Long) {
        if (posMs < MIN_SAVE_POS_MS) return
        positions[mediaId] = posMs
        dirty = true
        val now = System.currentTimeMillis()
        if (now - lastSaveTs > SAVE_INTERVAL_MS) {
            saveToDisk()
        }
    }

    /**
     * Принудительно сбросить на диск. Вызывать при pause/transition/dispose —
     * гарантирует, что последняя позиция не потеряется при убивании процесса.
     */
    fun flush() {
        if (dirty) saveToDisk()
    }

    /** Удалить позицию (при дослушивании трека до конца). */
    fun clearPosition(mediaId: String) {
        if (positions.remove(mediaId) != null) {
            dirty = true
            saveToDisk()
        }
    }

    /** Ключ для видео: "v_ownerId_videoId". */
    fun videoKey(ownerId: Long, videoId: Long): String = "v_${ownerId}_${videoId}"
}
