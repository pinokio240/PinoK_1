package re.pinok.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import re.pinok.util.AppLog

/**
 * Fix #276: PinnedConversationsRepository — локальное хранилище закреплённых
 * диалогов (peer_id список в порядке закрепления; 0-й элемент — верхний).
 *
 * WHY LOCAL: VK API `messages.markAsImportantConversation` требует
 * special-scope user token (право `messages`, выдаётся в исключительных
 * случаях по запросу на devsupport@corp.vk.com) ИЛИ community token.
 * Наш web-token (`vk1.a.*` из web-сессии) не подходит → API возвращает
 * err=8 "method available only for group messages" (см. лог Fix #274/#276).
 *
 * Поэтому закрепление хранится ЛОКАЛЬНО в [SovaPrefs.pinnedConvsData]
 * (source of truth для UI). API-вызов делается best-effort в фоне —
 * если когда-нибудь VK разрешит нашему токену, сервер тоже подхватит.
 *
 * Потокобезопасность: все операции через DataStore (атомарные put).
 *
 * Структура JSON: `[2000000062, 152094335, -123456]` (массив Long).
 */
class PinnedConversationsRepository(private val prefs: SovaPrefs) {

    private val gson = Gson()
    private val type = object : TypeToken<List<Long>>() {}.type

    /**
     * Загрузить список закреплённых peer_id (в порядке закрепления).
     * Пустая строка → пустой список. При ошибке парсинга — пустой список.
     */
    suspend fun load(): List<Long> {
        val raw = prefs.data.first().pinnedConvsData
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<Long>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            AppLog.w("PinnedConvsRepo", "Failed to parse pinnedConvsData: ${e.message}")
            emptyList()
        }
    }

    /** Сохранить полный список закреплённых peer_id (атомарно). */
    suspend fun save(peerIds: List<Long>) {
        val json = gson.toJson(peerIds)
        prefs.setPinnedConvsData(json)
        AppLog.d("PinnedConvsRepo", "Saved ${peerIds.size} pinned: $peerIds")
    }

    /**
     * Закрепить диалог. Добавляет peer_id в НАЧАЛО списка (чтобы чат встал
     * наверх закреплённого блока — как в нативном VK).
     * Если уже закреплён — сначала убирает из старой позиции, потом в начало.
     */
    suspend fun pin(peerId: Long): List<Long> {
        val current = load().toMutableList()
        current.remove(peerId)
        val updated = listOf(peerId) + current
        save(updated)
        return updated
    }

    /** Открепить диалог. Убирает peer_id из списка. No-op если не был закреплён. */
    suspend fun unpin(peerId: Long): List<Long> {
        val current = load().toMutableList()
        current.remove(peerId)
        save(current)
        return current
    }

    /**
     * Переставить закреплённый диалог с [fromIndex] на [toIndex] (drag&drop).
     * Индексы — в рамках списка закреплённых. Возвращает новый порядок.
     * No-op если индексы невалидны.
     */
    suspend fun reorder(fromIndex: Int, toIndex: Int): List<Long> {
        val current = load().toMutableList()
        if (fromIndex !in current.indices) return current
        if (toIndex !in current.indices) return current
        if (fromIndex == toIndex) return current
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        save(current)
        return current
    }

    /**
     * Сеттер списка целиком (для drag&drop когда UI уже посчитал новый порядок).
     * Просто персистит переданный список.
     */
    suspend fun setOrder(peerIds: List<Long>) {
        save(peerIds)
    }

    /** Проверить, закреплён ли диалог (без загрузки всего списка в UI). */
    suspend fun isPinned(peerId: Long): Boolean = load().contains(peerId)
}
