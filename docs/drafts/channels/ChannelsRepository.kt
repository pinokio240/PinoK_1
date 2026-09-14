package re.pinok.feature.channels.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import re.pinok.util.AppLog

data class ChannelInfo(
    val channelId: Long,
    val parentId: Long,
    val title: String,
    val membersCount: Int,
    val photoBaseUrl: String,
    val isMember: Boolean,
    val unreadCount: Int,
    val readUpToCmid: Long,
    val notificationEnabled: Boolean,
    val isDon: Boolean,
    val lastCmid: Long,
    val lastTimeEpoch: Long
)

data class ChannelPost(
    val channelId: Long,
    val cmid: Long,
    val authorId: Long,
    val timeEpoch: Long,
    val text: String,
    val reactionsCount: Int,
    val commentsCount: Int,
    val viewsCount: Int,
    val isPinned: Boolean
)

class ChannelsRepository(
    private val http: OkHttpClient,
    private val tokenProvider: () -> String,
    private val clientId: String = "6287487",
    private val apiVersion: String = "5.285"
) {
    private val baseUrl: String = "https://web.api.vk.ru/method/"

    private suspend fun call(method: String, params: Map<String, String>): JSONObject? {
        val token = tokenProvider()
        if (token.isEmpty()) {
            AppLog.e("ChannelsRepo", "call $method: no web-token"); return null
        }
        val url = baseUrl + method + "?v=" + apiVersion + "&client_id=" + clientId
        val form = FormBody.Builder()
        for ((k, v) in params) { form.add(k, v) }
        form.add("access_token", token)
        val req = Request.Builder().url(url).post(form.build()).build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(req).execute().use { resp ->
                    val body = resp.body.string(); if (body.isEmpty()) null else JSONObject(body)
                }
            } catch (e: Throwable) {
                AppLog.e("ChannelsRepo", "call $method: " + e.message); null
            }
        }
    }

    suspend fun loadChannels(filter: String, count: Int): List<ChannelInfo> {
        val r = call("channels.get", mapOf(
            "count" to count.toString(),
            "filter" to filter,
            "extended" to "1"
        )) ?: return emptyList()
        return parseChannelList(r)
    }

    suspend fun loadChannel(channelId: Long): ChannelInfo? {
        val r = call("channels.getById", mapOf(
            "channel_ids" to channelId.toString(),
            "extended" to "1"
        )) ?: return null
        val items = r.optJSONObject("response")?.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        return parseChannel(items.getJSONObject(0).optJSONObject("channel"))
    }

    suspend fun loadPosts(channelId: Long, startCmid: Long, count: Int): List<ChannelPost> {
        val r = call("channels.getHistory", mapOf(
            "channel_id" to channelId.toString(),
            "start_cmid" to startCmid.toString(),
            "count" to count.toString(),
            "offset" to "-1",
            "extended" to "1"
        )) ?: return emptyList()
        return parsePostList(r, false)
    }

    suspend fun loadPinned(channelId: Long): List<ChannelPost> {
        val r = call("channels.getPinnedMessages", mapOf(
            "channel_id" to channelId.toString(),
            "extended" to "1"
        )) ?: return emptyList()
        return parsePostList(r, true)
    }

    private fun parseChannelList(r: JSONObject): List<ChannelInfo> {
        val items = r.optJSONObject("response")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ChannelInfo>(items.length())
        for (i in 0 until items.length()) {
            val ch = items.getJSONObject(i).optJSONObject("channel")
            if (ch != null) out.add(parseChannel(ch))
        }
        return out
    }

    private fun parseChannel(ch: JSONObject?): ChannelInfo {
        if (ch == null) return ChannelInfo(0L, 0L, "", 0, "", false, 0, 0L, false, false, 0L, 0L)
        val ud = ch.optJSONObject("user_data")
        val rs = if (ud == null) null else ud.optJSONObject("read_state")
        val ns = if (ud == null) null else ud.optJSONObject("notification_settings")
        val lm = ch.optJSONObject("last_message")
        return ChannelInfo(
            ch.optLong("channel_id"),
            ch.optLong("parent_id"),
            ch.optString("title"),
            ch.optInt("members_count"),
            ch.optString("photo_base"),
            ud != null && ud.optBoolean("is_member"),
            if (rs == null) 0 else rs.optInt("unread_count"),
            if (rs == null) 0L else rs.optLong("read_up_to_cmid"),
            ns != null && ns.optBoolean("is_enabled"),
            ud != null && ud.optBoolean("is_don"),
            if (lm == null) 0L else lm.optLong("cmid"),
            if (lm == null) 0L else lm.optLong("time")
        )
    }

    private fun parsePostList(r: JSONObject, pinned: Boolean): List<ChannelPost> {
        val items = r.optJSONObject("response")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ChannelPost>(items.length())
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val cp = it.optJSONObject("cm_payload")
            val cnt = if (cp == null) null else cp.optJSONObject("counters")
            val react = if (cnt == null) null else cnt.optJSONObject("reactions")
            val comm = if (cnt == null) null else cnt.optJSONObject("comments")
            val views = if (cnt == null) null else cnt.optJSONObject("views")
            out.add(ChannelPost(
                it.optLong("channel_id"),
                it.optLong("cmid"),
                it.optLong("author_id"),
                it.optLong("time"),
                it.optString("text"),
                if (react == null) 0 else react.optInt("count"),
                if (comm == null) 0 else comm.optInt("count"),
                if (views == null) 0 else views.optInt("count"),
                pinned
            ))
        }
        return out
    }
}
