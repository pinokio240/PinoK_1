package re.pinok.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import re.pinok.util.AppLog

/**
 * Fix #356 #MSG-ARCHIVE: ArchivedConversationsRepository — локальное хранилище
 * архивных диалогов (peer_id список).
 *
 * WHY LOCAL: `messages.archiveConversation`/`messages.unarchiveConversation`
 * для нашего web-token (`vk1.a.*`) вероятен err=8/15 (недостаточно прав —
 * метод задокументирован, но права web-сессии ограничены; снапшот
 * мессенджер.снапшоты.разбор.md §7.3). Прецедент — закрепление
 * (PinnedConversationsRepository, Fix #274/#276): ЛОКАЛЬНОЕ хранилище —
 * source of truth для UI, API-вызов делается best-effort в фоне — если
 * когда-нибудь VK разрешит нашему токену, сервер тоже подхватит.
 *
 * Потокобезопасность: все операции через DataStore (атомарные put).
 *
 * Структура JSON: `[2000000062, 152094335, -123456]` (массив Long).
 */
class ArchivedConversationsRepository(private val prefs: SovaPrefs) {

    private val gson = Gson()
    private val type = object : TypeToken<List<Long>>() {}.type

    /**
     * Загрузить список архивных peer_id.
     * Пустая строка → пустой список. При ошибке парсинга — пустой список.
     */
    suspend fun load(): List<Long> {
        val raw = prefs.data.first().archivedConvsData
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<Long>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            AppLog.w("ArchivedConvsRepo", "Failed to parse archivedConvsData: ${e.message}")
            emptyList()
        }
    }

    /** Сохранить полный список архивных peer_id (атомарно). */
    suspend fun save(peerIds: List<Long>) {
        val json = gson.toJson(peerIds)
        prefs.setArchivedConvsData(json)
        AppLog.d("ArchivedConvsRepo", "Saved ${peerIds.size} archived: $peerIds")
    }

    /** Архивировать диалог: добавляет peer_id в начало списка (no-op если уже там — dedup). */
    suspend fun archive(peerId: Long): List<Long> {
        val current = load().toMutableList()
        current.remove(peerId) // dedup — не плодим дубли при повторном архиве
        current.add(0, peerId)
        save(current)
        return current
    }

    /** Разархивировать диалог. Убирает peer_id из списка. No-op если не был там. */
    suspend fun unarchive(peerId: Long): List<Long> {
        val current = load().toMutableList()
        current.remove(peerId)
        save(current)
        return current
    }

    /** Проверить, в архиве ли диалог (без загрузки всего списка в UI). */
    suspend fun isArchived(peerId: Long): Boolean = load().contains(peerId)
}
