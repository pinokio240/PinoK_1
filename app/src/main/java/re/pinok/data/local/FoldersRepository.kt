package re.pinok.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import re.pinok.data.model.ChatFolder
import re.pinok.util.AppLog

/**
 * P3.3: FoldersRepository — клиентское хранилище папок диалогов.
 *
 * VK API `messages.getChatFolders` недокументирован и может не работать →
 * папки хранятся локально в [SovaPrefs.msgFoldersData] как JSON-массив.
 * Это source of truth для UI (MessagesScreen tabs + FoldersSettingsScreen).
 *
 * Сериализация через Gson. Структура JSON:
 * ```json
 * [{"id":1700000000000,"title":"Работа","peerIds":[123,456],"iconEmoji":null}]
 * ```
 *
 * Потокобезопасность: load-modify-save секции (addFolder/editFolder/deleteFolder)
 * защищены Mutex (P3.2 fix — был TOCTOU: concurrent pin(X)+pin(Y) терял один peerId);
 * saveFolders/loadFolders атомарны сами по себе (DataStore put + flow.first).
 * ID папок = System.currentTimeMillis() (локально-уникальный).
 */
class FoldersRepository(private val prefs: SovaPrefs) {

    private val gson = Gson()
    private val type = object : TypeToken<List<ChatFolder>>() {}.type

    // P3.2: сериализует load→modify→save секции, чтобы параллельные
    // addFolder/editFolder/deleteFolder не затирали изменения друг друга.
    private val mutex = Mutex()

    /**
     * Загрузить папки из SovaPrefs. Пустая строка → пустой список.
     * При ошибке десериализации — возвращаем пустой список (не крашим UI).
     */
    suspend fun loadFolders(): List<ChatFolder> {
        val raw = prefs.data.first().msgFoldersData
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<ChatFolder>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            AppLog.w("FoldersRepository", "Failed to parse msgFoldersData: ${e.message}")
            emptyList()
        }
    }

    /** Сохранить список папок в SovaPrefs (атомарно). */
    suspend fun saveFolders(folders: List<ChatFolder>) {
        val json = gson.toJson(folders)
        prefs.setMsgFoldersData(json)
        AppLog.d("FoldersRepository", "Saved ${folders.size} folders")
    }

    /** Добавить новую папку. ID = текущее время (локально-уникальный). */
    suspend fun addFolder(title: String, peerIds: Set<Long>, iconEmoji: String? = null): ChatFolder = mutex.withLock {
        val current = loadFolders()
        val folder = ChatFolder(
            id = System.currentTimeMillis(),
            title = title.trim(),
            peerIds = peerIds,
            iconEmoji = iconEmoji,
        )
        saveFolders(current + folder)
        folder
    }

    /** Редактировать существующую папку (по id). Если не найдена — no-op. */
    suspend fun editFolder(id: Long, title: String, peerIds: Set<Long>, iconEmoji: String? = null) = mutex.withLock {
        val current = loadFolders()
        val updated = current.map { f ->
            if (f.id == id) f.copy(title = title.trim(), peerIds = peerIds, iconEmoji = iconEmoji)
            else f
        }
        saveFolders(updated)
    }

    /** Удалить папку по id. */
    suspend fun deleteFolder(id: Long) = mutex.withLock {
        val current = loadFolders()
        saveFolders(current.filter { it.id != id })
    }
}
