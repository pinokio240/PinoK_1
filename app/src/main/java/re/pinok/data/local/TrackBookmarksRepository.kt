package re.pinok.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import re.pinok.data.model.Track
import re.pinok.util.AppLog

/**
 * Волна 40 #BOOKMARKS-TRACKS: локальные закладки треков.
 *
 * У fave.* НЕТ аудио-раздела (#FAVE-AUDIO 2026-08-03): VK не позволяет
 * положить трек в серверные закладки — «закладка трека» в VK-смысле равна
 * «Моя музыка» (audio.add). Пользователь (обратная связь 2026-09-12) ожидает
 * РАЗДЕЛЬНЫЕ сущности: «Моя музыка» — аудиобиблиотека, «Закладки → Треки» —
 * персональные закладки. Поэтому трек-закладки хранятся ЛОКАЛЬНО:
 * JSON-массив [Track] в SovaPrefs (ключ track_bookmarks_data, паттерн
 * msgFoldersData/FoldersRepository; Gson-раундтрип безопасен — все поля
 * Track размечены @SerializedName, прецедент TrackDownloadManager).
 *
 * UI: AudioMoreMenu «В закладки»/«Удалить из закладок» (toggle-семантика,
 * MusicScreen + AudioPlayerScreen), BookmarksScreen раздел «Треки»
 * (воспроизведение очередью + удаление).
 *
 * Потокобезопасность: Mutex на мутациях; StateFlow — источник истины для UI;
 * первоначальная загрузка асинхронная (init-корутина, Dispatchers.IO).
 * Репозиторий живёт на уровне приложения (SovaApp.onCreate, как
 * FoldersRepository/PinnedConversationsRepository).
 */
class TrackBookmarksRepository(private val prefs: SovaPrefs) {

    private val gson = Gson()
    private val type = object : TypeToken<List<Track>>() {}.type
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var loaded = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())

    /** Локально заложенные треки (новейшие первыми — как у VK «Моя музыка»). */
    val tracks: StateFlow<List<Track>> = _tracks

    init {
        scope.launch { ensureLoaded() }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val raw = prefs.trackBookmarksData.first()
        val list: List<Track> = if (raw.isBlank()) {
            emptyList()
        } else {
            try {
                gson.fromJson<List<Track>>(raw, type) ?: emptyList()
            } catch (e: Exception) {
                // Битый JSON не крашим UI — честный пустой список + крошка в лог.
                AppLog.w("TrackBookmarksRepository", "Failed to parse track_bookmarks_data: ${e.message}")
                emptyList()
            }
        }
        _tracks.value = list
        loaded = true
    }

    /** Трек уже в закладках? (best-effort по текущему стейту). */
    fun isBookmarked(track: Track): Boolean {
        return _tracks.value.any { it.id == track.id && it.ownerId == track.ownerId }
    }

    /**
     * Toggle закладки. true = теперь заложен, false = теперь НЕ заложен.
     * При ошибке записи исключение уходит вызывающему коду — UI показывает
     * честный сбой вместо молчаливого успеха.
     */
    suspend fun toggle(track: Track): Boolean {
        mutex.withLock {
            ensureLoaded()
            val current = _tracks.value
            val exists = current.any { it.id == track.id && it.ownerId == track.ownerId }
            val next = if (exists) {
                current.filterNot { it.id == track.id && it.ownerId == track.ownerId }
            } else {
                listOf(track) + current
            }
            persist(next)
            _tracks.value = next
            return !exists
        }
    }

    /** Удалить трек из закладок. true — удалён, false — трека в закладках не было. */
    suspend fun remove(track: Track): Boolean {
        mutex.withLock {
            ensureLoaded()
            val current = _tracks.value
            val next = current.filterNot { it.id == track.id && it.ownerId == track.ownerId }
            if (next.size == current.size) return false
            persist(next)
            _tracks.value = next
            return true
        }
    }

    private suspend fun persist(list: List<Track>) {
        prefs.setTrackBookmarksData(gson.toJson(list))
        AppLog.d("TrackBookmarksRepository", "Saved ${list.size} track bookmarks")
    }
}
