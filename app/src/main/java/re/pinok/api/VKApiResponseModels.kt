package re.pinok.api

import com.google.gson.JsonArray
import re.pinok.data.model.Chat
import re.pinok.data.model.Comment
import re.pinok.data.model.Message
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video

/**
 * O2 #PERF-STRUCT (2026-09-16): вынос 16 data-классов-моделей из VKApiClient.kt.
 *
 * В исходном VKApiClient.kt эти модели были вложены в класс и раздували файл (~115 строк).
 * Перенесены как top-level data-классы — VKApiClient продолжает ссылаться на них
 * без изменений (резолвятся из того же пакета re.pinok.api).
 *
 * Импорт re.pinok.api.VKApiClient.GroupInfo нужен в WallByIdResult/ClipsFeedResult
 * (GroupInfo остаётся вложенным в VKApiClient).
 */

data class HistoryResult(
    val messages: List<Message>,
    val profiles: Map<Long, UserProfile>,
    val failure: String? = null,
)

data class WallByIdResult(
    val posts: List<re.pinok.data.model.Post>,
    val groups: Map<Long, VKApiClient.GroupInfo>,
)

data class UploadedPhoto(
    val server: Int,
    val photo: String,  // JSON-encoded строка от VK
    val hash: String,
)

data class CommentsResult(
    val comments: List<Comment>,
    val profiles: Map<Long, UserProfile>,
)

data class ChannelsHistoryResult(
    val messages: List<Message>,
    val profiles: Map<Long, UserProfile>,
    /** Минимальный cmid страницы — start_cmid для следующей (более старой) страницы. */
    val oldestCmid: Long? = null,
    /** Новейший cmid канала из getById.last_message (null при пагинации «старее»). */
    val newestCmid: Long? = null,
    /** Честная причина пустоты (VK API err / нет сети / парсинг) — null при успехе. */
    val failure: String? = null,
)

data class MessagesDiff(
    val key: String,
    val ts: Long,
    val serverLp: String,
    val serverVersion: Long,
    val invalidateAll: Boolean,
    val countersMessages: Int,
    val countersUnreadUnmuted: Int,
    val folders: List<Folder>,
) {
    data class Folder(
        val id: Int,
        val name: String,
        val type: String,
        val flags: Int,
    )

    /** Готовы ли credentials к LongPoll-опросу. */
    val hasCredentials: Boolean get() = key.isNotBlank() && ts > 0L && serverLp.isNotBlank()
}

data class MessagesItems(
    val chats: List<Chat>,
    /** Сколько диалогов (conversations) в этой странице — для курсора conversations_{N}. */
    val conversationsCount: Int,
    /** Сколько каналов в этой странице — для курсора channels_{N}. */
    val channelsCount: Int,
    val totalCount: Int,
    /** Курсор следующей страницы ("conversations_{cmid},channels_{minor_id}"). */
    val nextFrom: String = "",
)

data class MessagesConfig(
    val version: Int,
)

data class LongPollHistory(
    val history: List<JsonArray>,
    val newPts: Long,
    val newTs: Long,
    val messagesCount: Int,
    val conversationsCount: Int,
)

data class NotificationProfile(
    val id: Long,
    val name: String,
    val photo100: String,
    val photo200: String,
    val isGroup: Boolean,
)

data class CreatedPoll(
    val ownerId: Long,
    val id: Long,
)

data class ContentTab(
    val name: String,
    val toSectionButton: Boolean = false,
    val canAddButton: Boolean = false,
    val contentTypes: List<String> = emptyList(),
)

data class WallTab(
    val type: String,
    val title: String,
    val count: Int = 0,
    val isWallOwn: Boolean = false,
)

data class WallGetExtendedResult(
    val posts: List<Post>,
    /** id профиля → «Имя Фамилия» (только users из ответа). */
    val profileNames: Map<Long, String>,
)

data class ClipsFeedResult(
    val items: List<Video>,
    val nextFrom: String?,
    val profiles: Map<Long, UserProfile>,
    val groups: Map<Long, VKApiClient.GroupInfo>,
)

data class VideoLongPollServer(
    val server: String,
    val key: String,
    val ts: String,
)
