package re.pinok.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import re.pinok.api.VKApiClient
import re.pinok.data.model.Post

/**
 * Кэш групп-источников репостов для одного экрана (своя стена / чужая стена /
 * канал). Ключ — ПОЛОЖИТЕЛЬНЫЙ id группы (группа в copy_history[0] имеет
 * ownerId < 0; в кэше храним -ownerId). Значение — Pair(имя, аватар URL).
 *
 * Резолв идёт лениво: вызывающий экран в LaunchedEffect(post.ownerId, post.id)
 * зовёт [resolve] для поста с репостом из группы; результат кладётся в
 * Snapshot-state → рекомпозиция показывает имя/аватарку вместо «Запись клуба
 * <id>». Сеть может упасть — [resolve] никогда не бросает (работает через
 * runCatching), рендер не ломается.
 */
class RepostGroupCache {
    var groups by mutableStateOf<Map<Long, Pair<String, String?>>>(emptyMap())
        private set

    /**
     * Резолвит группу-источник репоста [post], если это репост из группы
     * (ownerId<0) и группа ещё не в кэше. Безопасен: любые ошибки сети/парсинга
     * молча игнорируются (кроме отмены корутины, которая пробрасывается).
     */
    suspend fun resolve(apiClient: VKApiClient, post: Post) {
        val repostOwnerId = post.copyHistory?.firstOrNull()?.ownerId ?: return
        if (repostOwnerId >= 0) return
        val groupId = -repostOwnerId
        if (groups.containsKey(groupId)) return
        val info = withContext(Dispatchers.IO) {
            try {
                apiClient.groupsGetById(listOf(groupId)).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return
        groups = groups + (groupId to (info.name to (info.photoBase ?: info.photo200 ?: info.photo100)))
    }

    /** Имя группы-источника репоста (или null, если ещё не загружено/не репост). */
    fun nameOf(groupId: Long): String? = groups[groupId]?.first

    /** Аватар группы-источника репоста (или null, если ещё не загружен/не репост). */
    fun photoOf(groupId: Long): String? = groups[groupId]?.second
}