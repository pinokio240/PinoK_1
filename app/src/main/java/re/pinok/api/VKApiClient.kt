package re.pinok.api

import android.content.Context
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import re.pinok.SovaApp
import re.pinok.feature.calls.CallsApi
import re.pinok.auth.exchange.ExchangeAuthRepository
import re.pinok.data.local.SovaPrefs
import re.pinok.data.local.TokenStorage
import re.pinok.data.model.Attachment
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.Chat
import re.pinok.data.model.ChatFolder
import re.pinok.data.model.Comment
import re.pinok.data.model.Message
import re.pinok.data.model.Post
import re.pinok.data.model.CatalogPlaylist
import re.pinok.data.model.CatalogViewType
import re.pinok.data.model.Track
import re.pinok.data.model.TrackArtist
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.data.model.VideoPlatform
import re.pinok.data.model.Album
import re.pinok.data.model.Bookmark
import re.pinok.data.model.QueueCredential
import re.pinok.data.model.DocFile
import re.pinok.data.model.Friend
import re.pinok.data.model.GiftItem
import re.pinok.data.model.Group
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.SearchHint
import re.pinok.mods.messages.MessageMods
import re.pinok.mods.network.NetworkMods
import re.pinok.mods.privacy.PrivacyMods
import re.pinok.util.AppLog
import re.pinok.util.NetworkObserver
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * #NULL-SAFE-HELPER (2026-08-02): smart-cast вместо цепочки safe-call-операций.
 *
 * Coding style PinoK (#NULL-EXPLICIT): неявные null-операторы запрещены
 * (safe-call, non-null assertion, elvis) — используем локальный `val` + `if (x != null)`.
 *
 * `SovaApp.getOrNull()` возвращает `SovaApp?` (nullable). `networkObserver` — `lateinit`
 * (non-null тип, но доступ до init бросает `UninitializedPropertyAccessException`).
 *
 * БЫЛО (нарушает coding style — две safe-call-операции в одной цепочке):
 * ```
 * val recentlySwitched = try {
 *     re.pinok.SovaApp.getOrNull()?.networkObserver?.isRecentlySwitched(30_000L) == true
 * } catch (_: Exception) { false }
 * ```
 *
 * СТАЛО (smart-cast через локальный val, без неявных null-операторов):
 * ```
 * val recentlySwitched = isNetworkRecentlySwitched(30_000L)
 * ```
 *
 * try/catch покрывает `UninitializedPropertyAccessException` (lateinit не инициализирован
 * на ранних стадиях boot) и любые runtime ошибки NetworkObserver.
 */
private fun isNetworkRecentlySwitched(windowMs: Long): Boolean {
    return try {
        val app = SovaApp.getOrNull()
        if (app != null) app.networkObserver.isRecentlySwitched(windowMs) else false
    } catch (_: Exception) {
        false
    }
}

// Task 20: реализует CallsApi (фасад :feature:calls) — рантайм тот же объект,
// сигнатуры без правок (дефолты аргументов легальны поверх интерфейса без дефолтов).
class VKApiClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val tokenStorage: TokenStorage,
    private val prefs: SovaPrefs,
    private val exchangeAuthRepository: ExchangeAuthRepository? = null,
    networkObserver: NetworkObserver? = null,
) : CallsApi {

    private val networkObserver = networkObserver ?: NetworkObserver(context)
    private val networkMods = NetworkMods()
    private val privacyMods = PrivacyMods()
    private val messageMods = MessageMods()

    // #CALLS-FIX: okcdn uid (584520805550) из auth.anonymLogin response —
    // НЕ VK user_id (171093180). Нужен для userId в WS URL сигналинга.
    @Volatile
    private var lastAnonymUid: Long = 0L
    override fun lastAnonymUid(): Long = lastAnonymUid

    private val randomIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    /** Fix #222: офлайн-кеш картинок стикеров.
     *  Когда стикер отображается в пикере (AsyncImage грузит PNG), мы параллельно
     *  скачиваем и сохраняем его в cacheDir/stickers/{stickerId}.png. При отправке
     *  стикера как картинки (messagesSendStickerAsImage) — берём из кеша, без
     *  повторной загрузки. Так стикер можно отправить даже офлайн (если он был
     *  открыт ранее). */
    private val stickerCacheDir = java.io.File(context.cacheDir, "stickers").apply {
        if (!exists()) mkdirs()
    }
    private fun stickerCacheFile(stickerId: Int): java.io.File =
        java.io.File(stickerCacheDir, "$stickerId.png")

    /**
     * Fix #225: определить MIME-тип и расширение по magic bytes изображения.
     * VK стикеры могут быть JPEG (sun1-XX.userapi.com/...jpg) или PNG.
     * При неверном Content-Type в multipart upload VK возвращает photo="".
     *
     * Поддерживаемые форматы: PNG, JPEG, GIF, WebP. Fallback — JPEG (最常见的).
     * @return Pair(mimeType, fileExtensionWithoutDot)
     */
    private fun detectImageMimeAndExt(bytes: ByteArray): Pair<String, String> {
        if (bytes.size < 12) return "image/jpeg" to "jpg"
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png" to "png"
        }
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()) {
            return "image/jpeg" to "jpg"
        }
        // GIF: 47 49 46 38 (GIF8)
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) {
            return "image/gif" to "gif"
        }
        // WebP: RIFF....WEBP
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) {
            return "image/webp" to "webp"
        }
        return "image/jpeg" to "jpg"
    }

    /**
     * Sprint 1, P0-3 (#76): максимум попыток решения captcha на один запрос.
     * После исчерпания — error 14 возвращается как null (как раньше).
     */
    private val MAX_CAPTCHA_RETRIES = 3

    suspend fun isOffline(): Boolean {
        val snap = prefs.data.first()
        return networkMods.isOfflineForced(snap) || !networkObserver.isOnline()
    }

    fun token(): String? = tokenStorage.load()?.accessToken

    suspend fun usersGet(userId: Long? = null): UserProfile? {
        // #30i (profile fix): убрана filterUsersFields — она могла ломать
        // загрузку профиля если DataStore ещё не инициализирован или
        // prefs.data.first() зависал. privacyHideLastSeen теперь работает
        // только через accountSetOnline() no-op (не отправляем ping).
        val args = mutableMapOf(
            "fields" to "photo_100,photo_200,online,last_seen,status,verified,counters",
        )
        if (userId != null) args["user_ids"] = userId.toString()
        val json = call("users.get", args) ?: return null
        return try {
            val arr = json.getAsJsonArray("response") ?: return null
            if (arr.isEmpty) return null
            val obj = arr[0].asJsonObject
            val countersObj = obj.getAsJsonObject("counters")
            UserProfile(
                id = obj.get("id")?.asLong ?: return null,
                firstName = obj.get("first_name")?.asString ?: "",
                lastName = obj.get("last_name")?.asString ?: "",
                photo100 = obj.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = obj.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                online = obj.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString,
                verified = obj.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                followersCount = countersObj?.get("followers")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                counters = countersObj?.let { c ->
                    UserProfile.Counters(
                        friends = c.get("friends")?.takeIf { !it.isJsonNull }?.asInt,
                        followers = c.get("followers")?.takeIf { !it.isJsonNull }?.asInt,
                        photos = c.get("photos")?.takeIf { !it.isJsonNull }?.asInt,
                        videos = c.get("videos")?.takeIf { !it.isJsonNull }?.asInt,
                        audios = c.get("audios")?.takeIf { !it.isJsonNull }?.asInt,
                        gifts = c.get("gifts")?.takeIf { !it.isJsonNull }?.asInt,
                    )
                },
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGet parse error", e)
            null
        }
    }

    // Fix #48: общий набор fields для newsfeed.* (из дампа Лента.html — реальный
    // VK web запрос newsfeed.getFeed). Без fields profiles[] приходят без
    // photo_100/photo_200 → аватарки постов не отображаются. Плюс fields для групп
    // (member_status, can_message, has_unseen_stories).
    private val NEWSFEED_FIELDS = "photo_100,photo_200,photo_base,sex,friend_status," +
        "first_name_gen,last_name_gen,screen_name,verified,image_status," +
        "has_unseen_stories,is_government_organization,trust_mark,is_verified," +
        "social_button_type,url,is_member,can_write_private_message,can_message," +
        "member_status,video_lives_data"

    data class NewsfeedResult(
        val posts: List<Post>,
        val profiles: Map<Long, UserProfile>,
        val groups: Map<Long, GroupInfo>,
        val nextFrom: String? = null,
    )

    data class GroupInfo(
        val id: Long,
        val name: String,
        val screenName: String? = null,
        val photo100: String? = null,
        val photo200: String? = null,
        val isClosed: Int = 0,
        val isMember: Int = 0,
        val verified: Int = 0,
        val membersCount: Int = 0,
        val description: String? = null,
        val status: String? = null,
        val type: String? = null,
    )

    suspend fun newsfeedGet(count: Int = 30, startFrom: String? = null, filters: String = "post,photo,video"): NewsfeedResult {
        if (isOffline()) {
            AppLog.w("VKApiClient", "newsfeedGet: offline mode — skipping API call")
            return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
        }
        // Fix #48: добавлен `fields` (из дампа Лента.html — реальный VK web запрос
        // newsfeed.getFeed передаёт эти поля). Без fields profiles[] приходят без
        // photo_100/photo_200 → аватарки постов не отображаются. Также добавлены
        // fields для групп (member_status, can_message, has_unseen_stories).
        val args = mutableMapOf(
            "filters" to filters,
            "count" to count.toString(),
            "extended" to "1",
            "fields" to NEWSFEED_FIELDS,
        )
        if (startFrom != null) args["start_from"] = startFrom
        val json = call("newsfeed.get", args) ?: return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
        return parseNewsfeedResponse(json)
    }

    /**
     * #FEED-FILTER: «Рекомендации» — newsfeed.getRecommended.
     * Формат ответа совпадает с newsfeed.get (items/profiles/groups/next_from).
     */
    suspend fun newsfeedGetRecommended(count: Int = 30, startFrom: String? = null): NewsfeedResult {
        if (isOffline()) return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
        val args = mutableMapOf(
            "count" to count.toString(),
            "extended" to "1",
            "fields" to NEWSFEED_FIELDS,
        )
        if (startFrom != null) args["start_from"] = startFrom
        val json = call("newsfeed.getRecommended", args) ?: return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
        return parseNewsfeedResponse(json)
    }

    /**
     * Общий парсер ответа newsfeed.* (items + profiles + groups + next_from).
     * Используется newsfeedGet и newsfeedGetRecommended.
     */
    private suspend fun parseNewsfeedResponse(json: JsonObject): NewsfeedResult {
        return try {
            val resp = json.getAsJsonObject("response") ?: return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
            val items = resp.getAsJsonArray("items") ?: return NewsfeedResult(emptyList(), emptyMap(), emptyMap())
            val nextFrom = resp.get("next_from")?.takeIf { !it.isJsonNull }?.asString

            val profilesArr = resp.getAsJsonArray("profiles")
            val profiles = mutableMapOf<Long, UserProfile>()
            if (profilesArr != null) {
                for (el in profilesArr) {
                    if (!el.isJsonObject) continue
                    val o = el.asJsonObject
                    val uid = o.get("id")?.asLong ?: continue
                    profiles[uid] = parseUserProfileMini(o)
                }
            }

            val groupsArr = resp.getAsJsonArray("groups")
            val groups = mutableMapOf<Long, GroupInfo>()
            if (groupsArr != null) {
                for (el in groupsArr) {
                    if (!el.isJsonObject) continue
                    val o = el.asJsonObject
                    val gid = o.get("id")?.asLong ?: continue
                    groups[gid] = GroupInfo(
                        id = gid,
                        name = o.get("name")?.asString ?: "",
                        screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
                        photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                        photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                        isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                        verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    )
                }
            }
            AppLog.d("VKApiClient", "newsfeedGet: parsed ${profiles.size} profiles, ${groups.size} groups from API (groupsArr=${groupsArr?.size() ?: "null"})")
            // DIAG: логируем КАЖДУЮ группу и её ключ
            for ((gid, gInfo) in groups) {
                AppLog.d("VKApiClient", "  GROUP PARSED: id=$gid key=$gid name='${gInfo.name}' photo100='${gInfo.photo100}'")
            }

            // VK newsfeed.get возвращает items разных типов (post, photo, video, audio,
            // friends, digest, promo, ads_easy_promote и т.д.). Парсим все поддерживаемые.
            // Дедупликация по (ownerId, id) для предотвращения коллизий в LazyColumn.
            val seenKeys = HashSet<Pair<Long, Long>>()
            val posts = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val itemType = o.get("type")?.takeIf { !it.isJsonNull }?.asString
                // Fix #49-3-bonus: skip standalone audio items — newsfeed.get с filter=audio
                // возвращает standalone audio recommendations (audio objects без id/owner_id/text),
                // а НЕ посты с audio-вложениями. Посты с audio приходят как type=post с
                // attachments[audio]. Старый фильтр ("post,photo,video,audio") передавал audio
                // → VK возвращал мусорные audio-promo items → parsePostMini падал на них.
                if (itemType != null && itemType !in listOf("post", "photo", "video")) {
                    AppLog.d("VKApiClient", "newsfeedGet: skip item type=$itemType")
                    return@mapNotNull null
                }
                val postId = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val postOwnerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                if (postId <= 0L || postOwnerId == 0L) {
                    AppLog.d("VKApiClient", "newsfeedGet: skip stub postId=$postId owner=$postOwnerId")
                    return@mapNotNull null
                }
                if (!seenKeys.add(postOwnerId to postId)) {
                    AppLog.d("VKApiClient", "newsfeedGet: skip duplicate post owner=$postOwnerId id=$postId")
                    return@mapNotNull null
                }
                // SOVA_2_lenta: используем parsePostMini для парсинга ВСЕХ полей
                // (включая 14 новых: isFavorite, canEdit, etc.)
                val parsed = parsePostMini(o)
                // DIAG: логируем fromId каждого поста
                AppLog.d("VKApiClient", "  POST PARSED: ownerId=$postOwnerId id=$postId fromId=${parsed.fromId} -> groupKey=${-parsed.fromId}")
                parsed
            }
            // Fix #67: lazy-fetch метаданных для групп, которых нет в groups[].
            // VK с web-token'ом часто НЕ возвращает полный groups[] (или возвращает
            // не для всех постов) → в FeedScreen все такие посты получали fallback
            // "Сообщество". VK web-клиент делает ровно то же —
            // apiWithPrefetch("groups.getById", {group_ids: ...}).
            val missingGroupIds = posts
                .asSequence()
                .map { it.fromId }
                .filter { it < 0 }  // группы — отрицательные fromId
                .map { -it }         // groups[] хранит положительные ID
                .filter { it !in groups.keys }
                .distinct()
                .toList()
            // FIX: также собираем group ID из copy_history (репостов) и вложений wall.
            val repostGroupIds = posts
                .asSequence()
                .flatMap { post ->
                    (post.copyHistory ?: emptyList()).asSequence()
                        .plus(post.attachments?.filter { it.type == "wall" }?.mapNotNull { it.wall } ?: emptyList())
                }
                .map { it.fromId }
                .filter { it < 0 }
                .map { -it }
                .filter { it !in groups.keys && it !in missingGroupIds }
                .distinct()
                .toList()
            val allMissing = (missingGroupIds + repostGroupIds).distinct()
            AppLog.d("VKApiClient", "newsfeedGet: ${posts.size} posts, ${missingGroupIds.size} missing groups from posts, ${repostGroupIds.size} from reposts. allMissing=$allMissing")
            if (allMissing.isNotEmpty()) {
                AppLog.d("VKApiClient",
                    "newsfeedGet: lazy-fetch ${allMissing.size} missing groups via groups.getById: $allMissing")
                try {
                    val fetched = groupsGetById(allMissing)
                    AppLog.d("VKApiClient", "newsfeedGet: groups.getById returned ${fetched.size} groups")
                    for (g in fetched) {
                        groups[g.id] = g
                    }
                } catch (e: Exception) {
                    AppLog.e("VKApiClient", "newsfeedGet: groups.getById failed", e)
                }
            }
            // DIAG: финальная сводка — для каждого поста проверяем наличие группы
            for (p in posts) {
                if (p.fromId < 0) {
                    val gKey = -p.fromId
                    val found = groups.containsKey(gKey)
                    AppLog.d("VKApiClient", "  FEED_CHECK: post ownerId=${p.ownerId} id=${p.id} fromId=${p.fromId} groupKey=$gKey found=$found")
                }
            }
            AppLog.i("VKApiClient", "newsfeedGet: DONE — ${posts.size} posts, ${profiles.size} profiles, ${groups.size} groups (keys=${groups.keys}), nextFrom=$nextFrom")
            // Профили (from_id > 0) обычно приходят в profiles[] полностью —
            // лента в основном состоит из постов групп. Если для отдельных постов
            // профиль не пришёл, FeedScreen покажет "id${fromId}" — приемлемый fallback.
            NewsfeedResult(posts, profiles, groups, nextFrom)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "newsfeed parse error", e)
            NewsfeedResult(emptyList(), emptyMap(), emptyMap())
        }
    }

    /**
     * #32: Получить количество непрочитанных диалогов.
     * VK: messages.getConversations с count=0 возвращает response.unread_count.
     */
    suspend fun messagesGetUnreadCount(): Int {
        if (isOffline()) return 0
        val args = mapOf(
            "count" to "0",
            "extended" to "1",
        )
        val json = call("messages.getConversations", args) ?: return 0
        return try {
            json.getAsJsonObject("response")
                ?.get("unread_count")?.asInt ?: 0
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "messagesGetUnreadCount error: ${e.message}")
            0
        }
    }

    suspend fun messagesGetConversations(count: Int = 20, offset: Int = 0, filter: String? = null): List<Chat> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,last_seen",
        )
        // §44 #MSG-REQUESTS (2026-08-03): filter parameter для message requests.
        // VK кладёт сообщения от не-друзей в отдельную папку «Запросы», которая
        // НЕ возвращается default getConversations (filter=all исключает requests).
        // filter=message_request — возвращает только запросы от не-друзей.
        // filter=all (default) — все диалоги КРОМЕ requests.
        if (filter != null) args["filter"] = filter
        val json = call("messages.getConversations", args) ?: return emptyList()
        val parsedChats: List<Chat> = try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val items = resp.getAsJsonArray("items") ?: return emptyList()
            val maps = parsePeerMaps(resp)
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseConversationItem(el.asJsonObject, maps)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetConversations parse error", e)
            return emptyList()
        }

        // Fix #128: добиваем недостающие имена/аватарки. VK с extended=1 НЕ
        // гарантирует что profiles[]/groups[] содержат ВСЕ пиров из items[].
        // Удалённые/заблокированные пользователи и некоторые сообщества могут
        // отсутствовать → в списке диалогов вместо имени светилось «Диалог».
        // Делаем один batch-запрос users.get + один groups.getById для пиров,
        // у которых title всё ещё пустой/«Диалог» или photo == null.
        return resolveMissingPeerInfo(parsedChats)
    }

    /**
     * #MODERN-SYNC: карты имён/аватарок/онлайна пиров из profiles[]/groups[].
     * Общие для messages.getConversations и messages.getItems (extended-ответы).
     */
    private data class PeerMaps(
        val profilesMap: Map<Long, String>,
        val profilesNames: Map<Long, Pair<String, String>>,
        val profilesOnline: Map<Long, Boolean>,
        val groupsMap: Map<Long, String>,
        val groupsNames: Map<Long, String>,
    )

    private fun parsePeerMaps(resp: com.google.gson.JsonObject): PeerMaps {
        val profilesMap = mutableMapOf<Long, String>()
        val profilesNames = mutableMapOf<Long, Pair<String, String>>()
        val profilesOnline = mutableMapOf<Long, Boolean>()
        resp.getAsJsonArray("profiles")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val uid = o.get("id")?.asLong ?: return@forEach
            val photoEl = o.get("photo_100")?.takeIf { !it.isJsonNull }
            if (photoEl != null) profilesMap[uid] = photoEl.asString
            profilesNames[uid] = Pair(
                o.get("first_name")?.asString ?: "",
                o.get("last_name")?.asString ?: "",
            )
            val onlineEl = o.get("online")?.takeIf { !it.isJsonNull }
            if (onlineEl != null) profilesOnline[uid] = onlineEl.asInt == 1
        }
        val groupsMap = mutableMapOf<Long, String>()
        val groupsNames = mutableMapOf<Long, String>()
        resp.getAsJsonArray("groups")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val gid = o.get("id")?.asLong ?: return@forEach
            val photoEl = o.get("photo_100")?.takeIf { !it.isJsonNull }
            if (photoEl != null) groupsMap[gid] = photoEl.asString
            groupsNames[gid] = o.get("name")?.asString ?: ""
        }
        return PeerMaps(profilesMap, profilesNames, profilesOnline, groupsMap, groupsNames)
    }

    /**
     * #MODERN-SYNC: парсинг одного conversation-элемента (conversation + last_message).
     * Общий для messages.getConversations и messages.getItems.
     */
    private fun parseConversationItem(o: com.google.gson.JsonObject, m: PeerMaps): Chat? {
        val conversation = o.getAsJsonObject("conversation") ?: return null
        val peer = conversation.getAsJsonObject("peer") ?: return null
        val peerId = peer.get("id")?.asLong ?: 0L
        val peerType = peer.get("type")?.asString ?: "user"

        // #72: title берётся из peer.title (для чатов type="chat"),
        // из profilesNames (для пользователей), из groupsNames (для групп).
        // Fix #128: peer.title для type="user"/type="group" может прийти
        // как пустая строка "" — .isNullOrBlank() это ловит (раньше только
        // null проверяли → пустой title не триггерил lookup).
        // Fix #271: для type="chat" дополнительно fallback на
        // chat_settings.title (VK дублирует туда название, но в редких
        // случаях peer.title пустой, а chat_settings.title — есть).
        val chatSettings = conversation.getAsJsonObject("chat_settings")
        val peerTitleRaw = peer.get("title")?.takeIf { !it.isJsonNull }?.asString
        val peerTitle = when {
            !peerTitleRaw.isNullOrBlank() -> peerTitleRaw
            peerType == "chat" -> parseChatSettingsTitle(chatSettings)
            peerType == "user" && peerId > 0 ->
                m.profilesNames[peerId]?.let { "${it.first} ${it.second}".trim() }
            peerType == "group" || peerId < 0 -> m.groupsNames[-peerId]
            else -> null
        } ?: "Диалог"
        // #72: photo берётся из peer.photo (для чатов), profilesMap (users), groupsMap (groups).
        // Fix #271: для type="chat" аватар лежит в conversation.chat_settings.photo
        // (объект photo_50/100/200), а НЕ в peer.photo (там null для чатов).
        val peerPhotoRaw = peer.get("photo")?.takeIf { !it.isJsonNull }?.asString
        val peerPhoto = when {
            !peerPhotoRaw.isNullOrBlank() -> peerPhotoRaw
            peerType == "chat" -> parseChatSettingsPhoto(chatSettings)
            peerId > 0 -> m.profilesMap[peerId]
            peerId < 0 -> m.groupsMap[-peerId]
            else -> null
        }

        val lastMsgObj = o.getAsJsonObject("last_message")
        val lastMessage = if (lastMsgObj != null) {
            Message(
                id = lastMsgObj.get("id")?.asLong ?: 0L,
                peerId = lastMsgObj.get("peer_id")?.asLong ?: peerId,
                fromId = lastMsgObj.get("from_id")?.asLong ?: 0L,
                date = lastMsgObj.get("date")?.asLong ?: 0L,
                text = lastMsgObj.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                out = lastMsgObj.get("out")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                readState = lastMsgObj.get("read_state")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                deleted = lastMsgObj.get("deleted")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                // Fix #203: cmid нужен для reply. last_message тоже несёт cmid.
                conversationMessageId = lastMsgObj.get("conversation_message_id")
                    ?.takeIf { !it.isJsonNull }?.asLong,
                // Fix #284: attachments для preview последнего сообщения.
                attachments = parseAttachments(lastMsgObj),
            )
        } else null
        // Fix #284: in_read/out_read — на уровне conversation (не peer).
        val inReadVal = conversation.get("in_read")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val outReadVal = conversation.get("out_read")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        // #72: unread_count из conversation.unread_count.
        val unreadCount = conversation.get("unread_count")?.takeIf { !it.isJsonNull }?.asInt
            ?: o.get("unread_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        // P3.4: can_write — флаг возможности писать (для определения каналов).
        val canWrite = conversation.get("can_write")?.let { cw ->
            if (cw.isJsonObject) {
                Chat.CanWrite(
                    allowed = cw.asJsonObject.get("allowed")
                        ?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                    reason = cw.asJsonObject.get("reason")
                        ?.takeIf { !it.isJsonNull }?.asInt,
                )
            } else null
        }
        // Fix #122: push_settings — для mute-индикатора.
        val pushSettings = conversation.get("push_settings")?.let { ps ->
            if (ps.isJsonObject) parsePushSettings(ps.asJsonObject) else null
        }
        // Fix #274: sort_id — для закрепления диалогов.
        val sortId = conversation.get("sort_id")?.let { sid ->
            if (sid.isJsonObject) {
                val sjo = sid.asJsonObject
                Chat.SortId(
                    majorId = sjo.get("major_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    minorId = sjo.get("minor_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                )
            } else null
        }
        // Fix #274: important — дублирующее boolean поле для закрепления.
        val important = conversation.get("important")?.takeIf { !it.isJsonNull }?.asBoolean

        return Chat(
            peer = Chat.Peer(
                id = peerId,
                type = peerType,
                localId = peer.get("local_id")?.asLong ?: 0L,
                title = peerTitle,
                photo = peerPhoto,
                online = if (peerType == "user" && peerId > 0) m.profilesOnline[peerId] else null,
            ),
            lastMessage = lastMessage,
            inRead = inReadVal,
            outRead = outReadVal,
            unreadCount = unreadCount,
            canWrite = canWrite,
            pushSettings = pushSettings,
            sortId = sortId,
            important = important,
        )
    }

    /**
     * #MODERN-SYNC: парсинг канала (community_channel) из messages.getItems.
     *
     * Структура (§35.1.2, подтверждено снапшотом):
     * {channel:{channel_id (отрицательный), title, photo_base, sort_id{...},
     *   user_data:{notification_settings:{is_enabled}, admin_level,
     *   read_state:{unread_count}}}, last_message:{cmid, author_id, time, text}}
     *
     * Канал = сообщество-broadcast с отрицательным peer.id. В Chat-модели это
     * `peer.type="group" && peer.id < 0 && canWrite.allowed=false` (isChannel).
     */
    private fun parseChannelItem(o: com.google.gson.JsonObject): Chat? {
        val ch = o.getAsJsonObject("channel") ?: return null
        val channelId = ch.get("channel_id")?.asLong ?: 0L
        if (channelId >= 0L) return null
        val title = ch.get("title")?.takeIf { !it.isJsonNull }?.asString
            ?: "Канал"
        val photo = ch.get("photo_base")?.takeIf { !it.isJsonNull }?.asString

        // user_data.read_state.unread_count — непрочитанные посты канала.
        val userData = ch.getAsJsonObject("user_data")
        val unreadCount = userData?.getAsJsonObject("read_state")
            ?.get("unread_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        // notification_settings.is_enabled == false → канал заглушён (mute).
        // #CHANNEL-MUTE-FIX: для каналов is_enabled=false — ДЕФОЛТ (уведомления
        // о постах выключены по умолчанию), а не ручной mute. Раньше это давало
        // 🔕-иконку у КАЖДОГО канала — неверно. PushSettings оставляем null.
        val adminLevel = userData?.get("admin_level")?.takeIf { !it.isJsonNull }?.asInt ?: 0

        // sort_id — закрепление (major_id > 0).
        val sortId = ch.getAsJsonObject("sort_id")?.let { sid ->
            Chat.SortId(
                majorId = sid.get("major_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                minorId = sid.get("minor_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            )
        }

        // last_message — формат канала: cmid вместо id, time вместо date, author_id вместо from_id.
        val lastMsgObj = o.getAsJsonObject("last_message")
        val lastMessage = if (lastMsgObj != null) {
            val cmid = lastMsgObj.get("cmid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            Message(
                id = cmid,
                peerId = channelId,
                fromId = lastMsgObj.get("author_id")?.takeIf { !it.isJsonNull }?.asLong ?: channelId,
                date = lastMsgObj.get("time")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                text = lastMsgObj.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                conversationMessageId = cmid.takeIf { it > 0L },
            )
        } else null

        return Chat(
            peer = Chat.Peer(
                id = channelId,
                type = "group",
                localId = 0L,
                title = title,
                photo = photo,
                online = null,
            ),
            lastMessage = lastMessage,
            inRead = 0L,
            outRead = 0L,
            unreadCount = unreadCount,
            canWrite = Chat.CanWrite(allowed = adminLevel > 0, reason = 18),
            // #CHANNEL-MUTE-FIX: каналы не помечаются muted по is_enabled=false
            // (это дефолт для каналов). pushSettings=null → mute-иконки нет.
            pushSettings = null,
            sortId = sortId,
            important = null,
        )
    }

    /**
     * §44 #MSG-REQUESTS: загружает сообщения от не-друзей (папка «Запросы»).
     *
     * VK маршрутизирует входящие сообщения от пользователей, не являющихся
     * друзьями, в отдельную папку «Запросы» (message requests). Эта папка
     * НЕ возвращается обычным messages.getConversations (filter=all её
     * исключает). Пользователь: «сообщение не от друзей не отображаются».
     *
     * Этот метод вызывает messages.getConversations с filter=message_request,
     * который возвращает ТОЛЬКО запросы от не-друзей. В UI они мерджатся в
     * общий список с визуальной меткой «Запрос» (или отдельной вкладкой).
     *
     * @return список запросов (может быть пустым если запросов нет)
     */
    suspend fun messagesGetConversationRequests(count: Int = 50, offset: Int = 0): List<Chat> {
        AppLog.d("VKApiClient", "messagesGetConversationRequests: fetching message_request folder " +
            "(count=$count, offset=$offset)")
        return messagesGetConversations(count = count, offset = offset, filter = "message_request")
    }

    /**
     * Fix #128: Резолв недостающих имён/аватарок для списка диалогов.
     *
     * VK API messages.getConversations с extended=1 возвращает profiles[] и
     * groups[], но НЕ для всех пиров: удалённые/заблокированные пользователи
     * и некоторые сообщества могут отсутствовать. В итоге в списке диалогов
     * такие чаты отображались как «Диалог» без аватарки.
     *
     * Логика:
     *  1. Собираем peerId где title пустой/«Диалог» ИЛИ photo == null.
     *  2. Положительные peerId → users.get (batch, до 1000 за раз).
     *  3. Отрицательные peerId → groups.getById (batch).
     *  4. Обновляем Chat.peer.title и Chat.peer.photo.
     *
     * @param chats список диалогов после первичного парсинга
     * @return обновлённый список (тот же порядок, новые title/photo где нашли)
     */
    private suspend fun resolveMissingPeerInfo(chats: List<Chat>): List<Chat> {
        if (chats.isEmpty()) return chats

        // Собираем пиров с неполными данными.
        data class MissingKey(val peerId: Long, val isGroup: Boolean)
        val missing = mutableListOf<MissingKey>()
        chats.forEach { chat ->
            val title = chat.peer.title
            val photo = chat.peer.photo
            val titleMissing = title.isNullOrBlank() || title == "Диалог"
            val photoMissing = photo.isNullOrBlank()
            if (titleMissing || photoMissing) {
                val pid = chat.peer.id
                // Групповой чат (type="chat", peerId >= 2_000_000_000) —
                // title и photo уже извлечены из chat_settings в парсере
                // (Fix #271: parseChatSettingsTitle + parseChatSettingsPhoto).
                // Если их нет и там — значит чат действительно без названия/аватара,
                // users.get/groups.getById тут не помогут (это не user/group пир).
                if (pid >= 2_000_000_000L) return@forEach
                missing.add(MissingKey(pid, isGroup = pid < 0))
            }
        }
        if (missing.isEmpty()) return chats

        val userIds = missing.filterNot { it.isGroup }.map { it.peerId }.distinct()
        val groupIds = missing.filter { it.isGroup }.map { -it.peerId }.distinct()
        // Fix #133: детальное логирование для диагностики «диалоги без имени».
        // Покажем сколько пиров missing, какие именно, и что вернул users/groups.
        AppLog.i("VKApiClient",
            "resolveMissingPeerInfo: missing=${missing.size} users=${userIds.size} groups=${groupIds.size} " +
                "userIds=${userIds.take(10)} groupIds=${groupIds.take(10)}")

        val usersMap: Map<Long, UserProfile> = if (userIds.isNotEmpty()) {
            try {
                val m = usersGetByIds(userIds)
                AppLog.i("VKApiClient",
                    "resolveMissingPeerInfo: users.get returned ${m.size}/${userIds.size} " +
                        "(missing users: ${userIds.filter { it !in m }.take(10)})")
                m
            } catch (e: Exception) {
                AppLog.w("VKApiClient", "resolveMissingPeerInfo: users.get failed: ${e.message}")
                emptyMap()
            }
        } else emptyMap()

        val groupsMap: Map<Long, GroupInfo> = if (groupIds.isNotEmpty()) {
            try {
                val list = groupsGetById(groupIds, fields = "photo_100,photo_200,name,screen_name,type")
                val m = list.associateBy { it.id }
                AppLog.i("VKApiClient",
                    "resolveMissingPeerInfo: groups.getById returned ${m.size}/${groupIds.size} " +
                        "(missing groups: ${groupIds.filter { it !in m }.take(10)})")
                m
            } catch (e: Exception) {
                AppLog.w("VKApiClient", "resolveMissingPeerInfo: groups.getById failed: ${e.message}")
                emptyMap()
            }
        } else emptyMap()

        // Fix #135: VKScript execute fallback variables — populated only when
        // both direct calls (users.get + groups.getById) returned empty.
        var fallbackUsersMap: Map<Long, UserProfile> = emptyMap()
        var fallbackGroupsMap: Map<Long, GroupInfo> = emptyMap()

        if (usersMap.isEmpty() && groupsMap.isEmpty()) {
            AppLog.w("VKApiClient",
                "resolveMissingPeerInfo: BOTH users.get and groups.getById returned empty " +
                    "(lastApiErrorCode=$lastApiErrorCode) — dialogs will show «Диалог» fallback")
            // Fix #135: BEFORE giving up, try VKScript execute fallback.
            // VKScript использует другой rate-limit bucket и часто работает
            // когда прямые users.get / groups.getById падают (network glitch,
            // 1117 token refresh race, rate-limit). Один round-trip вместо двух.
            val userIdsStr = userIds.joinToString(",")
            val groupIdsStr = groupIds.joinToString(",")
            val script = VKScript.build {
                if (userIds.isNotEmpty()) {
                    line("var users = API.users.get({user_ids: \"$userIdsStr\", fields: \"photo_100,photo_200,first_name,last_name,screen_name\"});")
                }
                if (groupIds.isNotEmpty()) {
                    line("var groups = API.groups.getById({group_ids: \"$groupIdsStr\", fields: \"photo_100,photo_200,name,screen_name,type\"});")
                }
                line("return { users: users, groups: groups };")
            }
            try {
                val resp = execute(script)
                if (resp == null) {
                    AppLog.w("VKApiClient",
                        "resolveMissingPeerInfo: VKScript fallback returned null (lastApiErrorCode=$lastApiErrorCode)")
                } else {
                    // response.users / response.groups могут быть JsonArray (если
                    // соответствующий userIds/groupIds был non-empty) либо отсутствовать/
                    // null (если был empty или API.*.get упал внутри script —
                    // partial success; execute_errors уже залогированы в execute()).
                    fallbackUsersMap = parseUsersJsonArray(resp.get("users"))
                    fallbackGroupsMap = parseGroupsJsonArray(resp.get("groups"))
                    AppLog.i("VKApiClient",
                        "resolveMissingPeerInfo: VKScript fallback returned users=${fallbackUsersMap.size}/$userIdsStr groups=${fallbackGroupsMap.size}/$groupIdsStr")
                    if (fallbackUsersMap.isEmpty() && fallbackGroupsMap.isEmpty()) {
                        AppLog.w("VKApiClient",
                            "resolveMissingPeerInfo: VKScript fallback also empty — giving up")
                    }
                }
            } catch (e: Exception) {
                AppLog.w("VKApiClient",
                    "resolveMissingPeerInfo: VKScript fallback failed: ${e.message}")
            }
            if (fallbackUsersMap.isEmpty() && fallbackGroupsMap.isEmpty()) {
                return chats
            }
        }

        // Fix #135: merge fallback results — если usersMap/groupsMap пустые,
        // используем fallback'и (либо заполненные, либо пустые — оба случая OK).
        val mergedUsersMap = if (usersMap.isEmpty()) fallbackUsersMap else usersMap
        val mergedGroupsMap = if (groupsMap.isEmpty()) fallbackGroupsMap else groupsMap

        // Обновляем только те чаты, где реально нашли имя/аватар.
        return chats.map { chat ->
            val pid = chat.peer.id
            val currentTitle = chat.peer.title
            val currentPhoto = chat.peer.photo
            val titleMissing = currentTitle.isNullOrBlank() || currentTitle == "Диалог"
            val photoMissing = currentPhoto.isNullOrBlank()
            if (!titleMissing && !photoMissing) return@map chat

            if (pid > 0 && pid < 2_000_000_000L) {
                // Пользователь.
                val u = mergedUsersMap[pid] ?: return@map chat
                val newTitle = if (titleMissing) {
                    val full = "${u.firstName} ${u.lastName}".trim()
                    full.ifBlank { currentTitle ?: "Диалог" }
                } else currentTitle
                val newPhoto = if (photoMissing) (u.photo100 ?: u.photo200) else currentPhoto
                chat.copy(peer = chat.peer.copy(title = newTitle, photo = newPhoto))
            } else if (pid < 0) {
                // Группа/канал.
                val g = mergedGroupsMap[-pid] ?: return@map chat
                val newTitle = if (titleMissing) g.name.ifBlank { currentTitle ?: "Диалог" } else currentTitle
                val newPhoto = if (photoMissing) (g.photo100 ?: g.photo200) else currentPhoto
                chat.copy(peer = chat.peer.copy(title = newTitle, photo = newPhoto))
            } else {
                chat
            }
        }
    }

    /**
     * Fix #135: helper для парсинга массива users из VKScript execute ответа.
     *
     * Используется в [resolveMissingPeerInfo] как fallback когда прямой
     * [usersGetByIds] возвращает пусто. Логика парсинга идентична [usersGetByIds]
     * (строки ~4179-4202), но работает напрямую с [JsonElement] — без извлечения
     * `response[]` (метод [execute] уже это сделал) и без вызова `call()`.
     *
     * @param el JSON-элемент: ожидается [JsonArray] (когда `var users = API.users.get(...)`
     *           присутствовал в script и users.get succeeded), либо `null` / [JsonNull]
     *           если ветка не была включена (userIds был empty) или users.get упал
     *           внутри script (partial success — execute_errors уже залогированы).
     * @return [Map]`<uid, UserProfile>`, никогда не null (emptyMap если нет данных).
     */
    private fun parseUsersJsonArray(el: JsonElement?): Map<Long, UserProfile> {
        if (el == null || el.isJsonNull || !el.isJsonArray) return emptyMap()
        val arr = el.asJsonArray
        val result = mutableMapOf<Long, UserProfile>()
        arr.forEach { e ->
            if (!e.isJsonObject) return@forEach
            val obj = e.asJsonObject
            val uid = obj.get("id")?.asLong ?: return@forEach
            // VK помечает удалённых/заблокированных пользователей как
            // "deactivated": "deleted" / "banned". Для таких всё равно берём
            // first_name/last_name (VK отдаёт "DELETED" / "Имя недоступно").
            val first = obj.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val last = obj.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
            result[uid] = UserProfile(
                id = uid,
                firstName = first,
                lastName = last,
                photo100 = obj.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = obj.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                online = obj.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                verified = obj.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            )
        }
        return result
    }

    /**
     * Fix #135: helper для парсинга массива groups из VKScript execute ответа.
     *
     * Используется в [resolveMissingPeerInfo] как fallback когда прямой
     * [groupsGetById] возвращает пусто. Логика парсинга идентична [groupsGetById]
     * (строки ~4893-4911), но работает напрямую с [JsonElement] — БЕЗ multi-format
     * handling (array / object-with-items / object-with-groups / single-object):
     * VKScript всегда возвращает array, не объект с обёрткой.
     *
     * @param el JSON-элемент: ожидается [JsonArray], либо `null` / [JsonNull].
     * @return [Map]`<gid, GroupInfo>`, никогда не null (emptyMap если нет данных).
     */
    private fun parseGroupsJsonArray(el: JsonElement?): Map<Long, GroupInfo> {
        if (el == null || el.isJsonNull || !el.isJsonArray) return emptyMap()
        val arr = el.asJsonArray
        val result = mutableMapOf<Long, GroupInfo>()
        arr.forEach { e ->
            if (!e.isJsonObject) return@forEach
            val o = e.asJsonObject
            val gid = o.get("id")?.asLong ?: return@forEach
            result[gid] = GroupInfo(
                id = gid,
                name = o.get("name")?.asString ?: "",
                screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
                photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                isMember = o.get("is_member")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                membersCount = o.get("members_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                status = o.get("status")?.takeIf { !it.isJsonNull }?.asString,
                type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
        return result
    }

    // Sprint 3 #14: Управление чатами.

    /** messages.createChat — создать групповой чат. Возвращает peer_id (2000000000+chat_id). */
    suspend fun messagesCreateChat(userIds: List<Long>, title: String): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "user_ids" to userIds.joinToString(",") { it.toString() },
            "title" to title,
        )
        val json = call("messages.createChat", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.get("chat_id")?.asLong?.let { 2000000000L + it } ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesCreateChat error", e)
            -1L
        }
    }

    /**
     * messages.editChat — переименовать чат + изменить описание.
     * chatId — локальный (без 2000000000).
     *
     * Fix #267 (Plan §36.12 P0-CHAT-2): расширение — добавлен optional description.
     * VK API: title (required), description (optional, до 255 символов).
     */
    suspend fun messagesEditChat(
        chatId: Long,
        title: String? = null,
        description: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf("chat_id" to chatId.toString())
        if (!title.isNullOrBlank()) args["title"] = title
        if (!description.isNullOrBlank()) args["description"] = description
        val json = call("messages.editChat", args) ?: return false
        return json.getAsJsonObject("response")?.get("success")?.asInt == 1
    }

    /**
     * Fix #267 (Plan §36.12 P0-CHAT-2): messages.setChatPhoto — установить фото чата.
     *
     * Трёхшаговый процесс (как в VK web):
     *  1. photos.getChatUploadServer → upload URL
     *  2. POST file (multipart) → response {file: "<token>"}
     *  3. messages.setChatPhoto({chat_id, file: <token>}) → response {message_id: ...}
     *
     * @param chatId локальный ID чата (без 2000000000)
     * @param fileBytes байты фото (JPEG/PNG, до 5MB)
     * @return true если фото установлено
     */
    suspend fun messagesSetChatPhoto(chatId: Long, fileBytes: ByteArray): Boolean {
        if (isOffline()) return false
        return try {
            withContext(Dispatchers.IO) {
                // Шаг 1: получить upload URL
                val serverJson = call("photos.getChatUploadServer", mapOf(
                    "chat_id" to chatId.toString(),
                )) ?: return@withContext false
                val uploadUrl = serverJson.getAsJsonObject("response")
                    ?.get("upload_url")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@withContext false

                // Шаг 2: POST file multipart → получаем file token
                val mediaType = "image/jpeg".toMediaType()
                val requestBody = fileBytes.toRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo", "photo.jpg", requestBody)
                    .build()
                val req = Request.Builder().url(uploadUrl).post(multipart).build()
                val fileToken = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.e("VKApiClient", "messagesSetChatPhoto upload HTTP ${resp.code}")
                        return@withContext false
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JsonParser.parseString(body).asJsonObject
                    json.get("file")?.takeIf { !it.isJsonNull }?.asString
                } ?: return@withContext false

                // Шаг 3: messages.setChatPhoto с file token
                val resultJson = call("messages.setChatPhoto", mapOf(
                    "chat_id" to chatId.toString(),
                    "file" to fileToken,
                )) ?: return@withContext false
                resultJson.has("response")
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesSetChatPhoto error", e)
            false
        }
    }

    /**
     * Fix #267 (Plan §36.12 P0-CHAT-3): messages.getChatInviteLink — ссылка-приглашение.
     *
     * Использует messages.getChat (chat_id) → chat_settings.invite_link.
     * Альтернатива: messages.getConversationsById → conversation.chat_settings.invite_link.
     *
     * @param peerId полный peer_id (2000000000 + chatId)
     * @return invite link URL или null (если нет прав / не group chat)
     */
    suspend fun messagesGetChatInviteLink(peerId: Long): String? {
        if (isOffline()) return null
        val chatId = peerId - 2_000_000_000L
        if (chatId <= 0) return null
        val json = call("messages.getChat", mapOf(
            "chat_id" to chatId.toString(),
            "fields" to "photo",
        )) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            resp.get("invite_link")?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "messagesGetChatInviteLink error: ${e.message}")
            null
        }
    }

    /**
     * Fix #272: messages.getChat — полная информация о чате (title + photo + members_count).
     *
     * VK API для type="chat" иногда НЕ отдаёт photo в messages.getConversationsById
     * (баг VK, особенно для чатов без аватара или с истекшим URL). Этот метод —
     * fallback: messages.getChat с fields=photo возвращает title + photo_50/100/200
     * прямо в корневом объекте ответа (без chat_settings обёртки).
     *
     * @param peerId полный peer_id (2000000000 + chatId)
     * @return Triple<title, photoUrl, membersCount> или null (не group chat / нет прав)
     */
    suspend fun messagesGetChat(peerId: Long): Triple<String?, String?, Int?>? {
        if (isOffline()) return null
        val chatId = peerId - 2_000_000_000L
        if (chatId <= 0) return null
        val json = call("messages.getChat", mapOf(
            "chat_id" to chatId.toString(),
            "fields" to "photo",
        )) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val title = resp.get("title")?.takeIf { !it.isJsonNull }?.asString
            // messages.getChat отдаёт photo как ОБЪЕКТ (photo_50/100/200), как и
            // chat_settings.photo в getConversationsById.
            val photo = resp.getAsJsonObject("photo")?.let { ph ->
                ph.get("photo_200")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                    ?: ph.get("photo_100")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                    ?: ph.get("photo_50")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            }
            val count = resp.get("members_count")?.takeIf { !it.isJsonNull }?.asInt
            Triple(title, photo, count)
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "messagesGetChat error: ${e.message}")
            null
        }
    }

    /**
     * Fix #267 (Plan §36.12 P1-CHAT-3): messages.editChat с owner_id —
     * передать права создателя чата другому участнику.
     *
     * VK API (недокументированный): messages.editChat({chat_id, owner_id: newOwnerId}).
     * Требует ACL can_change_owner == true И feature flag vkm_convo_owner_right_transfer.
     *
     * @param chatId локальный ID чата (без 2000000000)
     * @param newOwnerId user_id нового владельца (должен быть участником чата)
     * @return true если права переданы
     */
    suspend fun messagesTransferChatOwnership(chatId: Long, newOwnerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.editChat", mapOf(
            "chat_id" to chatId.toString(),
            "owner_id" to newOwnerId.toString(),
        )) ?: return false
        return json.getAsJsonObject("response")?.get("success")?.asInt == 1
    }

    /** messages.addChatUser — добавить пользователя в чат. */
    suspend fun messagesAddChatUser(chatId: Long, userId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.addChatUser", mapOf(
            "chat_id" to chatId.toString(),
            "user_id" to userId.toString(),
        )) ?: return false
        return true // VK возвращает 1 в response при успехе
    }

    /** messages.removeChatUser — исключить пользователя из чата (member_id = userId, или пустой = текущий пользователь — выход). */
    suspend fun messagesRemoveChatUser(chatId: Long, memberId: Long? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf("chat_id" to chatId.toString())
        if (memberId != null) args["member_id"] = memberId.toString()
        val json = call("messages.removeChatUser", args) ?: return false
        return true
    }

    /** messages.getConversationMembers — участники чата. */
    data class ChatMember(
        val memberId: Long,
        val userId: Long,
        val firstName: String,
        val lastName: String,
        val photo100: String?,
        val isOwner: Boolean,
        val isAdmin: Boolean,
        val invitedBy: Long,
    )

    suspend fun messagesGetConversationMembers(peerId: Long): List<ChatMember> {
        if (isOffline()) return emptyList()
        val args = mapOf(
            "peer_id" to peerId.toString(),
            // Fix #271: запрашиваем first_name,last_name,photo_100 для пользователей
            // (VK отдаёт их прямо в items[] при fields=). Для community-участников
            // (member_id < 0) эти поля отсутствуют — их берём из response.groups[].
            "fields" to "photo_100,first_name,last_name",
        )
        val json = call("messages.getConversationMembers", args) ?: return emptyList()
        return try {
            val respObj = json.getAsJsonObject("response") ?: return emptyList()
            val items = respObj.getAsJsonArray("items") ?: return emptyList()

            // Fix #271: парсим profiles[] (пользователи) и groups[] (сообщества)
            // из ответа. VK возвращает items[] с member_id/is_owner/is_admin, но
            // для community-участников (member_id < 0) first_name/last_name/photo_100
            // ОТСУТСТВУЮТ в самом item — они лежат в response.groups[]. Раньше
            // эти участники показывались как «id-181198905» без аватарки.
            val profilesMap = mutableMapOf<Long, Triple<String, String, String?>>()
            respObj.getAsJsonArray("profiles")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = o.get("id")?.asLong ?: return@forEach
                val first = o.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val last = o.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val photo = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString
                profilesMap[uid] = Triple(first, last, photo)
            }
            val groupsMap = mutableMapOf<Long, Pair<String, String?>>()
            respObj.getAsJsonArray("groups")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val gid = o.get("id")?.asLong ?: return@forEach
                val name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val photo = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString
                groupsMap[gid] = Pair(name, photo)
            }

            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val memberId = o.get("member_id")?.asLong
                    ?: o.get("user_id")?.asLong
                    ?: return@mapNotNull null
                val userId = o.get("user_id")?.asLong
                    ?: o.get("member_id")?.asLong
                    ?: memberId
                // Fix #271: JsonNull-safe парсинг first_name/last_name/photo_100.
                // Раньше `o.get("first_name")?.asString` падал на JsonNull
                // (UnsupportedOperationException) → весь items.mapNotNull падал в
                // catch → возвращался emptyList() → участники вообще не показывались.
                var firstName = o.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                var lastName = o.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                var photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString

                // Fix #271: резолв community-участников (member_id < 0) из groups[].
                // Для них first_name/last_name пустые → берём name из groupsMap.
                // Также добиваем пользователей, если поля не пришли в item (редко).
                if (memberId < 0) {
                    val gid = -memberId
                    groupsMap[gid]?.let { (name, photo) ->
                        if (firstName.isBlank()) firstName = name
                        if (lastName.isBlank()) lastName = ""  // у групп нет фамилии
                        if (photo100.isNullOrBlank()) photo100 = photo
                    }
                } else if (userId > 0 && (firstName.isBlank() || photo100.isNullOrBlank())) {
                    profilesMap[userId]?.let { (first, last, photo) ->
                        if (firstName.isBlank()) firstName = first
                        if (lastName.isBlank()) lastName = last
                        if (photo100.isNullOrBlank()) photo100 = photo
                    }
                }

                ChatMember(
                    memberId = memberId,
                    userId = userId,
                    firstName = firstName,
                    lastName = lastName,
                    photo100 = photo100,
                    isOwner = o.get("is_owner")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    isAdmin = o.get("is_admin")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    invitedBy = o.get("invited_by")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetConversationMembers error", e)
            emptyList()
        }
    }

    /** #74: результат messagesGetHistory — сообщения + профили отправителей. */
    data class HistoryResult(
        val messages: List<Message>,
        val profiles: Map<Long, UserProfile>,
    )

    suspend fun messagesGetHistory(
        peerId: Long,
        count: Int = 30,
        offset: Int = 0,
    ): List<Message> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "count" to count.toString(),
            "rev" to "0",
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,first_name,last_name,name",
        )
        if (offset > 0) args["offset"] = offset.toString()
        val json = call("messages.getHistory", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            val raw = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Message(
                    id = o.get("id")?.asLong ?: 0L,
                    peerId = o.get("peer_id")?.asLong ?: peerId,
                    fromId = o.get("from_id")?.asLong ?: 0L,
                    date = o.get("date")?.asLong ?: 0L,
                    text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    out = o.get("out")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    readState = o.get("read_state")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    deleted = o.get("deleted")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    edited = o.get("edited")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    originalText = o.get("original_text")?.takeIf { !it.isJsonNull }?.asString,
                    // Fix #203: conversation_message_id обязателен для reply (Fix #202).
                    // Без него messages.send с cmid=null → ранний return → «ответ не работает».
                    conversationMessageId = o.get("conversation_message_id")
                        ?.takeIf { !it.isJsonNull }?.asLong,
                    // Fix #99: парсим вложения (wall, photo, doc и т.д.).
                    attachments = parseAttachments(o),
                    // #67: reply_message + fwd_messages + action
                    replyMessage = o.getAsJsonObject("reply_message")?.let { parseMessage(it) },
                    fwdMessages = o.getAsJsonArray("fwd_messages")?.mapNotNull { fm ->
                        if (!fm.isJsonObject) null else parseMessage(fm.asJsonObject)
                    }?.takeIf { it.isNotEmpty() }.also { fwdList ->
                        // Fix #295 (round 2): диагностический лог — помогает
                        // отличить «fwd_messages не пришёл от VK» от «пришёл, но
                        // не отрендерился». Логируем только когда есть fwd — не
                        // спамим для обычных текстовых сообщений.
                        if (fwdList != null) {
                            AppLog.d("VKApiClient", "messagesGetHistory: msg id=${o.get("id")} has ${fwdList.size} fwd_messages" +
                                fwdList.joinToString("") { " [id=${it.id} from=${it.fromId} text=${it.text.take(30).replace("\n"," ")} atts=${it.attachments?.size ?: 0}]" })
                        }
                    },
                    // Fix #146: action может быть строкой ("chat_create") или
                    // объектом ({"type":"chat_pin_message",...}). Старый код звал
                    // .asString на объекте → UnsupportedOperationException → весь
                    // history-парсинг падал в catch → пустая история чата в UI.
                    action = o.get("action")?.let { el ->
                        when {
                            el.isJsonNull -> null
                            el.isJsonPrimitive -> el.asString
                            el.isJsonObject -> el.asJsonObject
                                .get("type")?.takeIf { !it.isJsonNull }?.asString
                            else -> null
                        }
                    },
                    actionText = o.get("action_text")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
            // MessageMods: undelete/unedit применяются к результату API-вызова.
            val snap = prefs.data.first()
            val modified = messageMods.apply(raw, snap)
            modified
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetHistory parse error", e)
            emptyList()
        }
    }

    /** #74: messagesGetHistory с профилями отправителей (для аватарок в чате). */
    suspend fun messagesGetHistoryWithProfiles(
        peerId: Long,
        count: Int = 30,
        offset: Int = 0,
    ): HistoryResult {
        if (isOffline()) return HistoryResult(emptyList(), emptyMap())
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "count" to count.toString(),
            "rev" to "0",
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,first_name,last_name,name",
        )
        if (offset > 0) args["offset"] = offset.toString()
        val json = call("messages.getHistory", args) ?: return HistoryResult(emptyList(), emptyMap())
        return try {
            val resp = json.getAsJsonObject("response") ?: return HistoryResult(emptyList(), emptyMap())
            val items = resp.getAsJsonArray("items") ?: return HistoryResult(emptyList(), emptyMap())
            // #74: парсим profiles[] и groups[] для аватарок
            val profiles = mutableMapOf<Long, UserProfile>()
            resp.getAsJsonArray("profiles")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = o.get("id")?.asLong ?: return@forEach
                profiles[uid] = UserProfile(
                    id = uid,
                    firstName = o.get("first_name")?.asString ?: "",
                    lastName = o.get("last_name")?.asString ?: "",
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    online = o.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
            resp.getAsJsonArray("groups")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val gid = o.get("id")?.asLong ?: return@forEach
                // Группы — отрицательный ID, маппим как UserProfile для UI
                profiles[-gid] = UserProfile(
                    id = -gid,
                    firstName = o.get("name")?.asString ?: "",
                    lastName = "",
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
            val raw = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Message(
                    id = o.get("id")?.asLong ?: 0L,
                    peerId = o.get("peer_id")?.asLong ?: peerId,
                    fromId = o.get("from_id")?.asLong ?: 0L,
                    date = o.get("date")?.asLong ?: 0L,
                    text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    out = o.get("out")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    readState = o.get("read_state")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    deleted = o.get("deleted")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    edited = o.get("edited")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    originalText = o.get("original_text")?.takeIf { !it.isJsonNull }?.asString,
                    // Fix #203: conversation_message_id обязателен для reply (Fix #202).
                    conversationMessageId = o.get("conversation_message_id")
                        ?.takeIf { !it.isJsonNull }?.asLong,
                    attachments = parseAttachments(o),
                    replyMessage = o.getAsJsonObject("reply_message")?.let { parseMessage(it) },
                    fwdMessages = o.getAsJsonArray("fwd_messages")?.mapNotNull { fm ->
                        if (!fm.isJsonObject) null else parseMessage(fm.asJsonObject)
                    }?.takeIf { it.isNotEmpty() }.also { fwdList ->
                        // Fix #295 (round 2): диагностический лог.
                        if (fwdList != null) {
                            AppLog.d("VKApiClient", "messagesGetHistoryWithProfiles: msg id=${o.get("id")} has ${fwdList.size} fwd_messages" +
                                fwdList.joinToString("") { " [id=${it.id} from=${it.fromId} text=${it.text.take(30).replace("\n"," ")} atts=${it.attachments?.size ?: 0}]" })
                        }
                    },
                    // Fix #146: action может быть строкой ("chat_create") или
                    // объектом ({"type":"chat_pin_message",...}). Старый код звал
                    // .asString на объекте → UnsupportedOperationException → весь
                    // history-парсинг падал в catch → пустая история чата в UI.
                    action = o.get("action")?.let { el ->
                        when {
                            el.isJsonNull -> null
                            el.isJsonPrimitive -> el.asString
                            el.isJsonObject -> el.asJsonObject
                                .get("type")?.takeIf { !it.isJsonNull }?.asString
                            else -> null
                        }
                    },
                    actionText = o.get("action_text")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
            val snap = prefs.data.first()
            val modified = messageMods.apply(raw, snap)
            HistoryResult(modified, profiles)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetHistoryWithProfiles error", e)
            HistoryResult(emptyList(), emptyMap())
        }
    }

    /**
     * DNR (Do Not Read) — если включён, не вызываем messages.markAsRead.
     * Возвращает true если вызов был подавлен (UI может показать «не прочитано»).
     *
     * @param force если true — игнорируем DNR (явное действие пользователя
     *   «отметить прочитанным» из меню диалога).
     */
    suspend fun messagesMarkAsRead(peerId: Long, upToMessageId: Long, force: Boolean = false): Boolean {
        val snap = prefs.data.first()
        if (!force && messageMods.shouldSuppressRead(snap)) {
            AppLog.d("VKApiClient", "messagesMarkAsRead: suppressed by DNR (peer=$peerId)")
            return false
        }
        val args = mapOf(
            "peer_id" to peerId.toString(),
            "start_message_id" to upToMessageId.toString(),
        )
        val json = call("messages.markAsRead", args)
        return json != null
    }

    /**
     * DNT (Do Not Type) — если включён, не вызываем messages.setActivity.
     */
    suspend fun messagesSetTyping(peerId: Long): Boolean {
        val snap = prefs.data.first()
        if (messageMods.shouldSuppressTyping(snap)) {
            AppLog.d("VKApiClient", "messagesSetTyping: suppressed by DNT (peer=$peerId)")
            return false
        }
        val args = mapOf(
            "peer_id" to peerId.toString(),
            "type" to "typing",
        )
        val json = call("messages.setActivity", args)
        return json != null
    }

    // ─── #58: недостающие messages.* методы (из архива мессенджера) ─────

    /** messages.markAsAnswered — отметить как отвеченное. */
    suspend fun messagesMarkAsAnswered(peerId: Long, messageIds: List<Long>, answered: Boolean = true): Boolean {
        if (isOffline()) return false
        val args = mapOf(
            "peer_id" to peerId.toString(),
            "message_ids" to messageIds.joinToString(","),
            "answered" to if (answered) "1" else "0",
        )
        val json = call("messages.markAsAnswered", args) ?: return false
        return json.has("response")
    }

    /** messages.deleteConversation — удалить диалог (очистить историю). */
    suspend fun messagesDeleteConversation(peerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.deleteConversation", mapOf(
            "peer_id" to peerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** messages.restore — восстановить удалённое сообщение. */
    suspend fun messagesRestore(messageId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.restore", mapOf(
            "message_id" to messageId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** messages.getConversationsById — инфо о конкретных диалогах. */
    suspend fun messagesGetConversationsById(peerIds: List<Long>): List<Chat> {
        if (isOffline() || peerIds.isEmpty()) return emptyList()
        val json = call("messages.getConversationsById", mapOf(
            "peer_ids" to peerIds.joinToString(","),
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,last_seen",
        )) ?: return emptyList()
        val parsedChats: List<Chat> = try {
            val respObj = json.getAsJsonObject("response") ?: return emptyList()
            val items = respObj.getAsJsonArray("items") ?: return emptyList()
            // Fix #128: парсим profiles[] и groups[] из ответа — раньше вообще
            // не использовались, хотя extended=1 их отдаёт. Из-за этого для
            // type="user" и type="group" пиров title всегда был «Диалог»
            // (peer.title для них пустой, нужен lookup по profiles/groups).
            val profilesNames = mutableMapOf<Long, Pair<String, String>>()
            val profilesPhotos = mutableMapOf<Long, String>()
            respObj.getAsJsonArray("profiles")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = o.get("id")?.asLong ?: return@forEach
                profilesNames[uid] = Pair(
                    o.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    o.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                )
                o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString?.let {
                    profilesPhotos[uid] = it
                }
            }
            val groupsNames = mutableMapOf<Long, String>()
            val groupsPhotos = mutableMapOf<Long, String>()
            respObj.getAsJsonArray("groups")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val gid = o.get("id")?.asLong ?: return@forEach
                groupsNames[gid] = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString?.let {
                    groupsPhotos[gid] = it
                }
            }
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val conversation = o.getAsJsonObject("conversation") ?: o
                val peer = conversation.getAsJsonObject("peer") ?: return@mapNotNull null
                val peerId = peer.get("id")?.asLong ?: return@mapNotNull null
                val peerType = peer.get("type")?.asString ?: "user"
                // P0.3: pinned_message — это Message object на верхнем уровне conversation.
                // Парсим через Gson, null если поля нет или оно null.
                val pinnedMsg = conversation.get("pinned_message")?.let { pm ->
                    if (pm.isJsonNull) null
                    else try { com.google.gson.Gson().fromJson(pm, Message::class.java) } catch (_: Exception) { null }
                }
                // P3.2 + Fix #122: push_settings — для определения mute state чата.
                // Используем единый parsePushSettings для захвата ВСЕХ полей
                // (disabled_until, disabled_forever, no_sound, disabled_mentions,
                // disabled_mass_mentions) — раньше no_sound терялся.
                val pushSettings = conversation.get("push_settings")?.let { ps ->
                    if (ps.isJsonObject) parsePushSettings(ps.asJsonObject) else null
                }
                // P3.4: can_write — флаг возможности писать (для определения каналов).
                val canWrite = conversation.get("can_write")?.let { cw ->
                    if (cw.isJsonObject) {
                        Chat.CanWrite(
                            allowed = cw.asJsonObject.get("allowed")
                                ?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                            reason = cw.asJsonObject.get("reason")
                                ?.takeIf { !it.isJsonNull }?.asInt,
                        )
                    } else null
                }
                // Fix #267 (Plan §36.12 P2-CHAT-1): ACL + permissions из chat_settings.
                // Только для group chats (type="chat", peer_id >= 2_000_000_000).
                // Для 1-1 диалогов и каналов chat_settings отсутствует → acl=null.
                val chatSettings = conversation.getAsJsonObject("chat_settings")
                val acl = chatSettings?.getAsJsonObject("acl")?.let { parseChatAcl(it) }
                val permissions = chatSettings?.getAsJsonObject("permissions")?.let { parseChatPermissions(it) }
                // Fix #269: description из chat_settings — для pre-fill в ChangeDescriptionDialog.
                val chatDescription = chatSettings
                    ?.get("description")?.takeIf { !it.isJsonNull }?.asString
                // Fix #128: lookup имени/аватарки по profiles/groups (как в
                // messagesGetConversations). Раньше здесь был тупой fallback
                // на «Диалог» для всех type="user"/type="group" пиров.
                // Fix #271: для type="chat" дополнительно fallback на
                // chat_settings.title (chatSettings уже извлечён выше для ACL).
                val peerTitleRaw = peer.get("title")?.takeIf { !it.isJsonNull }?.asString
                val peerTitle = when {
                    !peerTitleRaw.isNullOrBlank() -> peerTitleRaw
                    peerType == "chat" -> parseChatSettingsTitle(chatSettings)
                    peerType == "user" && peerId > 0 ->
                        profilesNames[peerId]?.let { "${it.first} ${it.second}".trim() }
                    peerType == "group" || peerId < 0 -> groupsNames[-peerId]
                    else -> null
                }
                // Fix #271: ГЛАВНЫЙ ФИКС аватарки группового чата. Для type="chat"
                // аватар лежит в conversation.chat_settings.photo (ОБЪЕКТ), а НЕ в
                // peer.photo. Раньше для peerId >= 2_000_000_000 photo всегда был
                // null → шапка ChatInfoScreen показывала иконку Group вместо фото.
                val peerPhoto = peer.get("photo")?.takeIf { !it.isJsonNull }?.asString
                    ?: when {
                        peerType == "chat" -> parseChatSettingsPhoto(chatSettings)
                        peerId > 0 && peerId < 2_000_000_000L -> profilesPhotos[peerId]
                        peerId < 0 -> groupsPhotos[-peerId]
                        else -> null
                    }
                // Fix #274: sort_id + important — для закрепления диалога.
                val sortId = conversation.get("sort_id")?.let { sid ->
                    if (sid.isJsonObject) {
                        val sjo = sid.asJsonObject
                        Chat.SortId(
                            majorId = sjo.get("major_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                            minorId = sjo.get("minor_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                        )
                    } else null
                }
                val important = conversation.get("important")?.takeIf { !it.isJsonNull }?.asBoolean
                // Fix #284: in_read/out_read здесь тоже нужны — getConversationsById
                // используется для refresh списка диалогов и для mute-lookup в
                // MessageNotifier. Без out_read read-checkmarks ломаются после refresh.
                val inReadById = conversation.get("in_read")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val outReadById = conversation.get("out_read")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                Chat(
                    peer = Chat.Peer(
                        id = peerId,
                        type = peerType,
                        localId = peer.get("local_id")?.asLong ?: 0L,
                        title = peerTitle ?: "Диалог",
                        photo = peerPhoto,
                    ),
                    lastMessage = null,
                    inRead = inReadById,
                    outRead = outReadById,
                    unreadCount = conversation.get("unread_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    pinnedMessage = pinnedMsg,
                    pushSettings = pushSettings,
                    canWrite = canWrite,
                    acl = acl,
                    permissions = permissions,
                    description = chatDescription,
                    sortId = sortId,
                    important = important,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetConversationsById parse error", e)
            return emptyList()
        }
        // Fix #128: добиваем недостающие имена/аватарки (как в messagesGetConversations).
        return resolveMissingPeerInfo(parsedChats)
    }

    /** messages.search — поиск по сообщениям. */
    suspend fun messagesSearch(query: String, peerId: Long? = null, count: Int = 20): List<MessageSearchResult> {
        if (isOffline() || query.isBlank()) return emptyList()
        val args = mutableMapOf(
            "q" to query.trim(),
            "count" to count.toString(),
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,last_seen",
        )
        if (peerId != null) args["peer_id"] = peerId.toString()
        val json = call("messages.search", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val items = resp.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                MessageSearchResult(
                    messageId = o.get("id")?.asLong ?: 0L,
                    peerId = o.get("peer_id")?.asLong ?: 0L,
                    fromId = o.get("from_id")?.asLong ?: 0L,
                    text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    date = o.get("date")?.asLong ?: 0L,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesSearch parse error", e)
            emptyList()
        }
    }

    /**
     * P3.3: messages.getChatFolders — список папок диалогов пользователя.
     *
     * ⚠️ Метод недокументирован в публичном VK API (5.282). Может вернуть error 3
     * (unknown method) или error 15 (access denied). В этом случае возвращаем emptyList —
     * UI использует клиентские папки из SovaPrefs.msgFoldersData.
     *
     * Ожидаемая структура response (если метод работает):
     * ```json
     * { "response": { "count": 1, "items": [
     *   { "id": 7, "title": "Каналы", "peer_ids": [2000000001, -123456] }
     * ] } }
     * ```
     * Поля парсим мягко (?: / takeIf) — структура может отличаться.
     */
    suspend fun messagesGetChatFolders(): List<ChatFolder> {
        if (isOffline()) return emptyList()
        val json = call("messages.getChatFolders", mapOf(
            "extended" to "1",
        )) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val items = resp.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val id = o.get("id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: o.get("folder_id")?.takeIf { !it.isJsonNull }?.asLong
                    ?: return@mapNotNull null
                val title = o.get("title")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@mapNotNull null
                // peer_ids может быть массивом чисел или массивом объектов {peer_id: ...}.
                val peerIds = o.get("peer_ids")?.let { pp ->
                    if (pp.isJsonArray) {
                        pp.asJsonArray.mapNotNull { p ->
                            when {
                                p.isJsonPrimitive -> p.asLong
                                p.isJsonObject -> p.asJsonObject.get("peer_id")
                                    ?.takeIf { !it.isJsonNull }?.asLong
                                else -> null
                            }
                        }.toSet()
                    } else emptySet()
                } ?: emptySet()
                ChatFolder(id = id, title = title, peerIds = peerIds)
            }
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "messagesGetChatFolders failed (likely undocumented): ${e.message}")
            emptyList()
        }
    }

    /** messages.getHistoryAttachments — медиа-вложения диалога. */
    suspend fun messagesGetHistoryAttachments(peerId: Long, mediaType: String = "photo", count: Int = 50): List<HistoryAttachment> {
        if (isOffline()) return emptyList()
        val json = call("messages.getHistoryAttachments", mapOf(
            "peer_id" to peerId.toString(),
            "media_type" to mediaType,
            "count" to count.toString(),
        )) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val items = resp.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val attachment = o.getAsJsonObject("attachment") ?: return@mapNotNull null
                val type = attachment.get("type")?.asString ?: return@mapNotNull null
                HistoryAttachment(
                    messageId = o.get("message_id")?.asLong ?: 0L,
                    type = type,
                    url = when (type) {
                        "photo" -> attachment.getAsJsonObject("photo")?.getAsJsonArray("sizes")?.maxByOrNull {
                            (it.asJsonObject.get("width")?.asInt ?: 0) * (it.asJsonObject.get("height")?.asInt ?: 0)
                        }?.asJsonObject?.get("url")?.asString
                        "video" -> attachment.getAsJsonObject("video")?.get("photo_320")?.asString
                        "audio" -> attachment.getAsJsonObject("audio")?.get("url")?.asString
                        "doc" -> attachment.getAsJsonObject("doc")?.get("url")?.asString
                        else -> null
                    },
                    title = when (type) {
                        "photo" -> "Фото"
                        "video" -> attachment.getAsJsonObject("video")?.get("title")?.asString ?: "Видео"
                        "audio" -> attachment.getAsJsonObject("audio")?.let {
                            "${it.get("artist")?.asString ?: ""} — ${it.get("title")?.asString ?: ""}"
                        } ?: "Аудио"
                        "doc" -> attachment.getAsJsonObject("doc")?.get("title")?.asString ?: "Документ"
                        else -> type
                    },
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetHistoryAttachments parse error", e)
            emptyList()
        }
    }

    /** messages.getLastActivity — последняя активность (online + last_seen).
     *  Fix #114: VK API требует `user_id`, НЕ `peer_id`. Раньше отправляли
     *  peer_id → err=100 "user_id is undefined". */
    suspend fun messagesGetLastActivity(peerId: Long): LastActivity? {
        if (isOffline()) return null
        val json = call("messages.getLastActivity", mapOf(
            "user_id" to peerId.toString(),
        )) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            LastActivity(
                online = resp.get("online")?.asInt ?: 0,
                lastSeen = resp.get("time")?.asLong ?: 0L,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetLastActivity parse error", e)
            null
        }
    }

    /** messages.pin — закрепить сообщение. */
    suspend fun messagesPin(peerId: Long, messageId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.pin", mapOf(
            "peer_id" to peerId.toString(),
            "message_id" to messageId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** messages.unpin — открепить сообщение. */
    suspend fun messagesUnpin(peerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.unpin", mapOf(
            "peer_id" to peerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /**
     * Fix #274: messages.markAsImportantConversation — закрепить/открепить диалог
     * в списке (поднять наверх, в блок «Закреплённые»).
     *
     * VK API параметры:
     *  - peer_id (required) — ID диалога
     *  - important (0/1, required) — 1 = закрепить, 0 = открепить
     *
     * После успешного вызова VK инкрементирует conversation.sort_id.major_id
     * (для закрепления) или обнуляет его (для открепления). LongPoll event 20
     * (CONVO_MAJOR_ID_CHANGED) прилетает всем активным сессиям с новым majorId.
     *
     * Возвращает true если VK ответил {"response": 1}. Caller сам обновляет
     * локальный sortId (нет стандартизированного ответа с новым sort_id).
     */
    suspend fun messagesMarkAsImportantConversation(peerId: Long, important: Boolean): Boolean {
        if (isOffline()) return false
        val json = call("messages.markAsImportantConversation", mapOf(
            "peer_id" to peerId.toString(),
            "important" to if (important) "1" else "0",
        )) ?: run {
            AppLog.w("VKApiClient", "markAsImportantConversation failed (null) peer=$peerId important=$important")
            return false
        }
        // Response: {"response": 1} (или boolean true у web-токена)
        val resp = json.get("response")?.takeIf { it.isJsonPrimitive }
        val ok = resp != null && try {
            when {
                resp.isJsonPrimitive && resp.asJsonPrimitive.isBoolean -> resp.asBoolean
                else -> resp.asInt == 1
            }
        } catch (_: Exception) { false }
        if (!ok) {
            val errCode = json.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("error_code")?.takeIf { !it.isJsonNull }?.asInt
            AppLog.w("VKApiClient",
                "markAsImportantConversation failed (err=$errCode) peer=$peerId important=$important")
        } else {
            AppLog.i("VKApiClient",
                "markAsImportantConversation ok: peer=$peerId important=$important")
        }
        return ok
    }

    /**
     * Fix #274: messages.markAsUnreadConversation — пометить диалог непрочитанным
     * (метка «новое», как в нативном VK → жирный шрифт без бейджа числа).
     *
     * VK API параметры:
     *  - peer_id (required) — ID диалога
     *  - unread (0/1, required) — 1 = пометить непрочитанным, 0 = прочитанным
     *
     * Используется в long-press меню списка диалогов (как в нативном VK).
     */
    suspend fun messagesMarkAsUnreadConversation(peerId: Long, unread: Boolean): Boolean {
        if (isOffline()) return false
        val json = call("messages.markAsUnreadConversation", mapOf(
            "peer_id" to peerId.toString(),
            "unread" to if (unread) "1" else "0",
        )) ?: run {
            AppLog.w("VKApiClient", "markAsUnreadConversation failed (null) peer=$peerId unread=$unread")
            return false
        }
        val resp = json.get("response")?.takeIf { it.isJsonPrimitive }
        val ok = resp != null && try {
            when {
                resp.isJsonPrimitive && resp.asJsonPrimitive.isBoolean -> resp.asBoolean
                else -> resp.asInt == 1
            }
        } catch (_: Exception) { false }
        if (!ok) {
            val errCode = json.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("error_code")?.takeIf { !it.isJsonNull }?.asInt
            AppLog.w("VKApiClient",
                "markAsUnreadConversation failed (err=$errCode) peer=$peerId unread=$unread")
        }
        return ok
    }

    /**
     * P3.2 + Fix #122 + Fix #273: messages.setConversationPushSettings —
     * mute/unmute чата с надёжным fallback на account.setSilenceMode.
     *
     * VK API параметры (классический api.vk.com):
     *  - peer_id (required) — ID диалога
     *  - disabled (0/1) — выключить/включить push-уведомления
     *  - disabled_until (-1/0/timestamp) — -1 = навсегда, 0 = отменить, ts = до
     *  - disabled_forever (0/1) — Fix #273: web/m.vk.com flow использует
     *    именно этот параметр (см. VK_IMPORT_API.MD §35.1, §18.2). Шлём
     *    ВСЕ три (disabled + disabled_until + disabled_forever) для
     *    максимальной совместимости — лишние параметры VK игнорирует.
     *  - sound (int) — значение звука (0 = без звука)
     *
     * Поведение:
     *  - mute (disabled=true): disabled=1 + disabled_until=-1 + disabled_forever=1.
     *  - unmute (disabled=false): disabled=0 + disabled_until=0 + disabled_forever=0.
     *
     * Ответ VK может быть трёх видов:
     *  1. {"response": {"peer_id": ..., "push_settings": {...}}} — парсим ps.
     *  2. {"response": 1} — успех без push_settings (часто для group chats).
     *     Fix #273: ранее возвращали null → caller откатывал toggle.
     *     Теперь синтезируем PushSettings из запрошенного state.
     *  3. null / error → fallback на account.setSilenceMode (см. ниже).
     *
     * Fix #273: для групповых чатов messages.setConversationPushSettings
     * иногда падает с error 15 (access denied) для не-админов. VK_IMPORT_API.MD
     * §18.2 рекомендует account.setSilenceMode как универсальный mute-метод.
     * Если primary-метод не сработал — пробуем fallback, и при успехе
     * возвращаем синтезированные PushSettings (caller не видит разницы).
     */
    suspend fun messagesSetConversationPushSettings(
        peerId: Long, disabled: Boolean, sound: Int? = null,
    ): Chat.PushSettings? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "disabled" to if (disabled) "1" else "0",
            // Fix #122: disabled_until=-1 для permanent mute, 0 для unmute.
            "disabled_until" to if (disabled) "-1" else "0",
            // Fix #273: web/m.vk.com flow (VK_IMPORT_API.MD §35.1) использует
            // disabled_forever. Шлём вместе с disabled для совместимости.
            "disabled_forever" to if (disabled) "1" else "0",
        )
        sound?.let { args["sound"] = it.toString() }
        val json = call("messages.setConversationPushSettings", args)

        // Case 1: ответ содержит push_settings — парсим и возвращаем.
        if (json != null) {
            val respElem = json.get("response")
            // Response может быть объектом {"peer_id":..,"push_settings":{..}}
            // ИЛИ примитивом 1 (просто успех).
            if (respElem != null && respElem.isJsonObject) {
                val resp = respElem.asJsonObject
                val ps = resp.get("push_settings")?.takeIf { it.isJsonObject }?.asJsonObject
                if (ps != null) {
                    val parsed = parsePushSettings(ps)
                    AppLog.i("VKApiClient",
                        "setConversationPushSettings ok (primary, push_settings): " +
                            "peer=$peerId disabled=$disabled newSettings=$parsed")
                    return parsed
                }
            }
            // Case 2: {"response": 1} — успех без push_settings.
            // Синтезируем PushSettings из запрошенного state (Fix #273).
            if (respElem != null && respElem.isJsonPrimitive) {
                val ok = try { respElem.asInt } catch (_: Exception) { 0 }
                if (ok == 1) {
                    val synthetic = Chat.PushSettings(
                        disabledForever = disabled,
                        disabledUntil = if (disabled) -1L else 0L,
                        sound = sound,
                        noSound = null,
                        disabledMentions = null,
                        disabledMassMentions = null,
                    )
                    AppLog.i("VKApiClient",
                        "setConversationPushSettings ok (primary, response=1 → synthetic): " +
                            "peer=$peerId disabled=$disabled synthetic=$synthetic")
                    return synthetic
                }
            }
            // Если есть явный error — логируем и идём в fallback.
            val errCode = json.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("error_code")?.takeIf { !it.isJsonNull }?.asInt
            AppLog.w("VKApiClient",
                "setConversationPushSettings primary failed (err=$errCode) for peer=$peerId disabled=$disabled — trying account.setSilenceMode fallback")
        } else {
            AppLog.w("VKApiClient",
                "setConversationPushSettings primary null for peer=$peerId disabled=$disabled — trying account.setSilenceMode fallback")
        }

        // Case 3: primary не сработал → fallback на account.setSilenceMode.
        // VK_IMPORT_API.MD §18.2 рекомендует именно этот метод для mute/unmute.
        // Работает надёжнее для group chats (где primary иногда даёт err 15).
        val fallbackOk = accountSetSilentMode(peerId, disabled)
        if (fallbackOk) {
            val synthetic = Chat.PushSettings(
                disabledForever = disabled,
                disabledUntil = if (disabled) -1L else 0L,
                sound = sound,
                noSound = null,
                disabledMentions = null,
                disabledMassMentions = null,
            )
            AppLog.i("VKApiClient",
                "account.setSilenceMode fallback ok: peer=$peerId disabled=$disabled → synthetic=$synthetic")
            return synthetic
        }

        AppLog.w("VKApiClient",
            "Both setConversationPushSettings and setSilenceMode failed for peer=$peerId disabled=$disabled")
        return null
    }

    /**
     * Fix #273: account.setSilenceMode — универсальный mute/unmute диалога.
     *
     * VK_IMPORT_API.MD §18.2 (строка 4223) рекомендует именно этот метод:
     *   «20. Mute/unmute диалога — account.setSilentMode»
     *
     * VK API параметры:
     *  - peer_id (int, required) — ID диалога (user id ИЛИ 2_000_000_000 + chat_id)
     *  - time (int, required) — -1 = навсегда, 0 = unmute, N = на N секунд
     *
     * VK сам извлекает chat_id = peer_id - 2_000_000_000 для групповых чатов,
     * поэтому отдельный chat_id параметр не нужен (modern API).
     *
     * Возвращает true если VK ответил {"response": 1}.
     *
     * Используется как:
     *  1. Fallback внутри messagesSetConversationPushSettings (когда primary
     *     падает с err 15 для group chats).
     *  2. Public API для прямого вызова из UI при необходимости.
     */
    suspend fun accountSetSilentMode(peerId: Long, muted: Boolean): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            // -1 = навсегда (mute), 0 = unmute. VK API spec.
            "time" to if (muted) "-1" else "0",
        )
        val json = call("account.setSilenceMode", args) ?: run {
            AppLog.w("VKApiClient", "account.setSilenceMode failed (null response) for peer=$peerId muted=$muted")
            return false
        }
        // Response: {"response": 1}
        val resp = json.get("response")?.takeIf { it.isJsonPrimitive }
        val ok = resp != null && try { resp.asInt == 1 } catch (_: Exception) { false }
        if (!ok) {
            val errCode = json.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("error_code")?.takeIf { !it.isJsonNull }?.asInt
            AppLog.w("VKApiClient",
                "account.setSilenceMode failed (no response=1, err=$errCode) for peer=$peerId muted=$muted")
        }
        return ok
    }

    /** Fix #122: парсинг push_settings из произвольного JsonObject. */
    private fun parsePushSettings(ps: com.google.gson.JsonObject): Chat.PushSettings {
        return Chat.PushSettings(
            disabledUntil = ps.get("disabled_until")?.takeIf { !it.isJsonNull }?.asLong,
            disabledForever = ps.get("disabled_forever")?.takeIf { !it.isJsonNull }?.asBoolean,
            sound = ps.get("sound")?.takeIf { !it.isJsonNull }?.asInt,
            noSound = ps.get("no_sound")?.takeIf { !it.isJsonNull }?.asBoolean,
            disabledMentions = ps.get("disabled_mentions")?.takeIf { !it.isJsonNull }?.asBoolean,
            disabledMassMentions = ps.get("disabled_mass_mentions")?.takeIf { !it.isJsonNull }?.asBoolean,
        )
    }

    /**
     * Fix #267 (Plan §36.12 P2-CHAT-1): Парсер ACL из chat_settings.acl объекта.
     * 14 базовых can_* полей + 3 optional admin-only. Все поля с защитой от JsonNull.
     */
    private fun parseChatAcl(acl: com.google.gson.JsonObject): re.pinok.data.model.ChatAcl {
        fun b(key: String): Boolean =
            acl.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        fun bn(key: String): Boolean? =
            acl.get(key)?.takeIf { !it.isJsonNull }?.asBoolean
        return re.pinok.data.model.ChatAcl(
            canChangeInfo = b("can_change_info"),
            canChangeInviteLink = b("can_change_invite_link"),
            canChangePin = b("can_change_pin"),
            canInvite = b("can_invite"),
            canPromoteUsers = b("can_promote_users"),
            canSeeInviteLink = b("can_see_invite_link"),
            canModerate = b("can_moderate"),
            canCopyChat = b("can_copy_chat"),
            canCall = b("can_call"),
            canUseMassMentions = b("can_use_mass_mentions"),
            canChangeStyle = b("can_change_style"),
            canSendReactions = b("can_send_reactions"),
            canForwardMessages = b("can_forward_messages"),
            canChangeOwner = b("can_change_owner"),
            canChangeStickersPopupAutoplay = bn("can_change_stickers_popup_autoplay"),
            canDisableForwardMessages = bn("can_disable_forward_messages"),
            canDisableServiceMessages = bn("can_disable_service_messages"),
        )
    }

    /**
     * Fix #267 (Plan §36.12 P2-CHAT-1): Парсер permissions из chat_settings.permissions.
     * Значения: "all" | "owner" | "owner_and_admins" (null если поле отсутствует).
     */
    private fun parseChatPermissions(p: com.google.gson.JsonObject): re.pinok.data.model.ChatPermissions {
        fun s(key: String): String? =
            p.get(key)?.takeIf { !it.isJsonNull }?.asString
        return re.pinok.data.model.ChatPermissions(
            invite = s("invite"),
            changeInfo = s("change_info"),
            changePin = s("change_pin"),
            useMassMentions = s("use_mass_mentions"),
            seeInviteLink = s("see_invite_link"),
            call = s("call"),
            changeAdmins = s("change_admins"),
            changeStyle = s("change_style"),
        )
    }

    /**
     * Fix #271: Парсер аватарки группового чата из chat_settings.photo.
     *
     * VK API для type="chat" (peer_id >= 2_000_000_000) отдаёт аватар НЕ в
     * `peer.photo` (там null/пусто), а в `conversation.chat_settings.photo` —
     * это ОБЪЕКТ с полями photo_50 / photo_100 / photo_200 / photo_base.
     * Раньше парсеры читали только `peer.photo` → для всех групповых чатов
     * аватарка была null → в списке диалогов и в ChatInfoScreen показывалась
     * пустая заглушка с иконкой Group. Это и есть «пустые места» на скриншоте.
     *
     * Возвращаем photo_200 (лучшее качество), fallback на photo_100 → photo_50.
     * null если chat_settings отсутствует, photo отсутствует, или все поля пустые.
     *
     * Источник: VK_IMPORT_API.MD §35.1.3 — структура conversation.chat_settings.photo.
     */
    private fun parseChatSettingsPhoto(chatSettings: com.google.gson.JsonObject?): String? {
        val photo = chatSettings?.getAsJsonObject("photo") ?: return null
        // photo_base — базовый URL без cs= (для произвольного размера), но
        // не всегда возвращается. Предпочитаем готовые photo_200/photo_100.
        return photo.get("photo_200")?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
            ?: photo.get("photo_100")?.takeIf { !it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            ?: photo.get("photo_50")?.takeIf { !it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
    }

    /**
     * Fix #271: Парсер названия группового чата из chat_settings.title.
     *
     * VK API для type="chat" обычно дублирует title и в `peer.title`, и в
     * `chat_settings.title`. Но в редких случаях (чат без названия, API
     * рассинхрон) peer.title может быть пустым, а chat_settings.title — есть.
     * Этот fallback нужен для полноты.
     */
    private fun parseChatSettingsTitle(chatSettings: com.google.gson.JsonObject?): String? {
        return chatSettings?.get("title")?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
    }

    /** P3.1: account.ban — заблокировать пользователя (owner_id). */
    suspend fun accountBan(ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("account.ban", mapOf("owner_id" to ownerId.toString())) ?: return false
        return json.has("response")
    }

    /** P3.1: messages.markAsSpam — пожаловаться на спам (message_ids, optional peer_id). */
    suspend fun messagesMarkAsSpam(messageIds: List<Long>, peerId: Long? = null): Boolean {
        if (isOffline() || messageIds.isEmpty()) return false
        val args = mutableMapOf("message_ids" to messageIds.joinToString(","))
        peerId?.let { args["peer_id"] = it.toString() }
        val json = call("messages.markAsSpam", args) ?: return false
        return json.has("response")
    }

    // #58: data classes для новых messages методов
    data class MessageSearchResult(
        val messageId: Long,
        val peerId: Long,
        val fromId: Long,
        val text: String,
        val date: Long,
    )

    data class HistoryAttachment(
        val messageId: Long,
        val type: String,
        val url: String?,
        val title: String,
    )

    data class LastActivity(
        val online: Int,
        val lastSeen: Long,
    )

    /**
     * Список аудиозаписей пользователя.
     *
     * Fix #62: добавлен параметр [offset] для пагинации (бесконечная лента).
     * VK audio.get поддерживает offset нативно — отдаёт count треков начиная
     * с offset. UI (MusicScreen) при достижении конца списка вызывает
     * audioGet(offset = tracks.size, count = 50) и добавляет новые треки.
     *
     * Для веб-токенов (vk1.a.*) audio.get выдаёт error 3 — fallback на
     * audio.getCatalog. getCatalog — это discovery-лента (не библиотека
     * пользователя), пагинация курсором. Для первого вызова offset=0
     * отдаём весь каталог; для последующих (offset>0) возвращаем пустой
     * список (курсорная пагинация getCatalog сложна и не реализована —
     * веб-токены получают достаточно треков одним вызовом).
     *
     */
    /**
     * audio.get с возвратом общего количества треков (response.count).
     * Возвращает Pair(totalCount, tracks). totalCount = -1 если неизвестно
     * (fallback на audio.getCatalog — нет поля count).
     */
    suspend fun audioGetWithCount(count: Int = 50, offset: Int = 0, ownerId: Long? = null): Pair<Int, List<Track>> {
        if (isOffline()) return Pair(0, emptyList())
        val snap = prefs.data.first()
        val args = mutableMapOf("count" to count.coerceIn(1, 100).toString())
        if (offset > 0) args["offset"] = offset.toString()
        // #30j (community tabs): owner_id для музыки сообщества (отрицательный)
        if (ownerId != null) args["owner_id"] = ownerId.toString()
        // musicHighQuality: VK отдаёт 320kbps MP3 вместо 128kbps OGG.
        if (snap.musicHighQuality) args["quality"] = "hq"
        val json = call("audio.get", args)
        if (json != null) {
            val errorObj = json.getAsJsonObject("error")
            if (errorObj == null) {
                // Успех — парсим треки + извлекаем response.count.
                val parsed = parseAudioResponseWithCount(json)
                return parsed
            } else {
                val errorCode = errorObj.get("error_code")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                AppLog.w("VKApiClient", "audio.get error $errorCode (offset=$offset) — пробуем audio.getCatalog как fallback")
                // Сохраняем ошибку для UI (если fallback тоже упадёт — покажется оригинал).
                lastApiErrorCode = errorCode
                lastApiError = errorObj.get("error_msg")?.takeIf { !it.isJsonNull }?.asString
            }
        }
        // Для сообществ fallback на getCatalog не имеет смысла — возвращаем пусто.
        if (ownerId != null) {
            return Pair(0, emptyList())
        }
        // Fix #56: fallback на audio.getCatalog — этот метод работает с веб-токенами
        // (vk1.a.*) без sig. Возвращает catalog блоков с плейлистами и треками.
        // Используется самим VK веб-клиентом для страницы "Музыка → Главная".
        // Fix #62: getCatalog не поддерживает offset — для offset>0 возвращаем
        // пустой список (веб-токены получают всю ленту первым вызовом).
        if (offset > 0) {
            AppLog.d("VKApiClient", "audio.getCatalog: offset=$offset > 0, пагинация не поддерживается, возвращаем []")
            return Pair(-1, emptyList())
        }
        val fallbackTracks = audioGetCatalogFallback(count)
        // getCatalog не даёт count — возвращаем -1 как маркер «неизвестно».
        // UI будет определять hasMore по size < pageSize.
        return Pair(-1, fallbackTracks)
    }

    /**
     * audio.get — backward-compatible обёртка (без count).
     */
    suspend fun audioGet(count: Int = 50, offset: Int = 0, ownerId: Long? = null): List<Track> {
        return audioGetWithCount(count, offset, ownerId).second
    }

    /**
     * Парсит успешный ответ audio.get — response.count + массив items.
     */
    private fun parseAudioResponseWithCount(json: JsonObject): Pair<Int, List<Track>> {
        return try {
            val resp = json.getAsJsonObject("response") ?: return Pair(0, emptyList())
            val totalCount = resp.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val items = resp.getAsJsonArray("items") ?: return Pair(totalCount, emptyList())
            val tracks = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Track(
                    id = o.get("id")?.asLong ?: 0L,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    artist = o.get("artist")?.asString ?: "",
                    title = o.get("title")?.asString ?: "",
                    duration = o.get("duration")?.asInt ?: 0,
                    url = extractAudioUrl(o),  // #AUDIO-UNMASK
                    albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
                    albumThumb = extractAlbumThumb(o),
                    accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                    lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong,
                    subtitle = o.get("subtitle")?.takeIf { !it.isJsonNull }?.asString,
                    genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
                )
            }
            Pair(totalCount, tracks)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audio parse error", e)
            Pair(0, emptyList())
        }
    }

    /**
     * Парсит успешный ответ audio.get (или audio.getById) — массив items в response.
     */
    private fun parseAudioResponse(json: JsonObject): List<Track>? {
        return parseAudioResponseWithCount(json).second.takeIf { it.isNotEmpty() }
    }

    /**
     * Извлекает URL обложки альбома из audio-объекта.
     * VK возвращает два варианта:
     *  1. album_thumb (прямая строка-URL) — старый формат
     *  2. album.thumb.photo_270 / photo_135 / photo_68 / photo_34 — новый формат
     *     (album.thumb — это объект с массивом размеров фото)
     */

    /**
     * #AUDIO-UNMASK (2026-08-01, P0 #1 из VK_IMPORT_API.MD §42.3):
     *
     * Парсит `url` из JsonObject и применяет [AudioUrlUnmasker.unmask] с userId
     * текущего пользователя. VK возвращает обфусцированные URL (`audio_api_unavailable`)
     * для части треков — без расшифровки трек не проиграет и не скачается.
     *
     * Используется во ВСЕХ местах парсинга audio URL (audio.get / getById / search /
     * getRecommendations / getPlaylistById / catalog fallback).
     *
     * @return расшифрованный HTTPS URL, или null если поля `url` нет / оно null.
     */
    private fun extractAudioUrl(o: JsonObject): String? {
        val raw = o.get("url")?.takeIf { !it.isJsonNull }?.asString
        if (raw.isNullOrBlank()) return null
        val userId = exchangeAuthRepository?.userId() ?: 0L
        return AudioUrlUnmasker.unmask(raw, userId)
    }

    private fun extractAlbumThumb(o: JsonObject): String? {
        // Прямая строка URL в album_thumb
        val direct = o.get("album_thumb")?.takeIf { !it.isJsonNull }?.asString
        if (!direct.isNullOrBlank()) return direct
        // Объект album.thumb.photo_XXX
        val thumbObj = o.getAsJsonObject("album")?.getAsJsonObject("thumb") ?: return null
        // Берём самый большой размер
        listOf("photo_270", "photo_300", "photo_135", "photo_68", "photo_34")
            .forEach { key ->
                val url = thumbObj.get(key)?.takeIf { !it.isJsonNull }?.asString
                if (!url.isNullOrBlank()) return url
            }
        return null
    }

    // ─── Sprint 5: Музыка v2 — API-методы ────────────────────────────

    /**
     * Поиск музыки. VK: audio.search
     *
     * Fix #266: добавлен fallback на catalog.getAudioSearch для веб-токенов.
     * Веб-токены (vk1.a.*) часто не имеют доступа к audio.search (error 3/15/5)
     * — так же, как и к audio.get. В этом случае делаем fallback на
     * catalog.getAudioSearch, который работает с веб-токенами и возвращает
     * блоки с треками/артистами/плейлистами.
     *
     * Возвращаем Pair<count, tracks>. Фильтруем треки без url (нельзя играть).
     * Дедуплицируем по (ownerId, id) — catalog может вернуть дубли.
     */
    suspend fun audioSearch(
        query: String,
        count: Int = 30,
        offset: Int = 0,
    ): Pair<Int, List<Track>> {
        if (isOffline() || query.isBlank()) return 0 to emptyList()
        // Fix #147: quality=hq для максимального качества (320kbps MP3 / HQ AAC).
        // Раньше отправлялось только в audioGetWithCount — поиск возвращал 128kbps.
        val snap = prefs.data.first()
        val args = mutableMapOf(
            "q" to query,
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        if (snap.musicHighQuality) args["quality"] = "hq"
        val json = call("audio.search", args)
        if (json != null) {
            val errorObj = json.getAsJsonObject("error")
            if (errorObj == null) {
                val (total, tracks) = parseAudioResponseWithCount(json)
                // Фильтруем треки без URL (нельзя проиграть) — если их слишком много,
                // это значит, что метод вернул «обрезанный» ответ (подписка/регион) —
                // всё равно покажем что есть. Но если ВООБЩЕ нет проигрываемых треков,
                // попробуем fallback.
                val playable = tracks.filter { !it.url.isNullOrBlank() }
                if (playable.isNotEmpty() || tracks.isNotEmpty()) {
                    return total to playable
                }
            } else {
                val errorCode = errorObj.get("error_code")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val errorMsg = errorObj.get("error_msg")?.takeIf { !it.isJsonNull }?.asString
                AppLog.w("VKApiClient",
                    "audio.search error $errorCode ($errorMsg) — пробуем catalog.getAudioSearch как fallback")
            }
        }
        // Fallback: catalog.getAudioSearch — работает с веб-токенами.
        // Возвращает секцию с блоками; извлекаем все треки из всех блоков.
        return audioSearchCatalogFallback(query, count, offset)
    }

    /**
     * Fix #266: Fallback-поиск через catalog.getAudioSearch.
     * Извлекает треки из всех блоков секции, дедуплицирует по (ownerId, id).
     * Поддерживает пагинацию через next_from (если offset>0 и есть next_from).
     */
    private suspend fun audioSearchCatalogFallback(
        query: String,
        count: Int,
        offset: Int,
    ): Pair<Int, List<Track>> {
        return try {
            // Для offset>0 нужно знать next_from от предыдущей страницы —
            // catalog.getAudioSearch использует cursor-based пагинацию, не offset.
            // На первом запросе (offset=0) next_from приходит в response.next_from.
            // Для простоты: при offset>0 возвращаем пусто (пользователь обычно
            // не скроллит глубоко в поиске). Если понадобится — добавим кэш next_from.
            if (offset > 0) {
                AppLog.d("VKApiClient",
                    "audioSearchCatalogFallback: offset=$offset — cursor-пагинация не поддерживается")
                return 0 to emptyList()
            }
            val section = catalogGetAudioSearchExtended(query, startFrom = null)
                ?: return 0 to emptyList()
            val seenKeys = HashSet<Pair<Long, Long>>()
            val result = mutableListOf<Track>()
            for (block in section.blocks) {
                for (item in block.items) {
                    if (item is re.pinok.data.model.AudioCatalogItem.TrackItem) {
                        val t = item.track
                        if (t.id <= 0L || t.ownerId == 0L) continue
                        if (!seenKeys.add(t.ownerId to t.id)) continue
                        // Пропускаем треки без URL — не сможем играть.
                        // (catalog может вернуть треки без URL для неподписчиков)
                        if (t.url.isNullOrBlank()) continue
                        result.add(t)
                        if (result.size >= count) return result.size to result
                    }
                }
            }
            AppLog.i("VKApiClient",
                "audioSearchCatalogFallback: query='$query' → ${result.size} tracks")
            result.size to result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioSearchCatalogFallback error", e)
            0 to emptyList()
        }
    }

    /**
     * Fix #266: Расширенный поиск музыки — возвращает треки + артистов + плейлисты.
     * Сначала пробует audio.search / audio.searchArtists / audio.searchPlaylists
     * (более точные результаты), при ошибке падает на catalog.getAudioSearch,
     * который возвращает все типы одним запросом (веб-токены).
     *
     * Используется в MusicScreen для отображения секций в результатах поиска.
     */
    suspend fun audioSearchWithSections(
        query: String,
        count: Int = 30,
    ): re.pinok.data.model.AudioSearchResult {
        if (isOffline() || query.isBlank()) {
            return re.pinok.data.model.AudioSearchResult()
        }
        val tracks = mutableListOf<Track>()
        val artists = mutableListOf<re.pinok.data.model.AudioArtist>()
        val playlists = mutableListOf<AudioPlaylist>()

        // 1) Пробуем catalog.getAudioSearch — работает с любым токеном и
        //    возвращает все типы одним запросом. Это даёт «богатый» UI
        //    (артисты сверху, плейлисты, треки) как в нативном VK Music.
        try {
            val section = catalogGetAudioSearchExtended(query, startFrom = null)
            if (section != null) {
                val seenTrackKeys = HashSet<Pair<Long, Long>>()
                val seenArtistIds = HashSet<Long>()
                val seenPlaylistKeys = HashSet<Pair<Long, Long>>()
                for (block in section.blocks) {
                    for (item in block.items) {
                        when (item) {
                            is re.pinok.data.model.AudioCatalogItem.TrackItem -> {
                                val t = item.track
                                if (t.id <= 0L || t.ownerId == 0L) continue
                                if (!seenTrackKeys.add(t.ownerId to t.id)) continue
                                // Сохраняем ВСЕ треки (даже без URL) — UI может
                                // показать «доступно по подписке» для треков без url.
                                tracks.add(t)
                            }
                            is re.pinok.data.model.AudioCatalogItem.ArtistItem -> {
                                val a = item.artist
                                if (a.id <= 0L || !seenArtistIds.add(a.id)) continue
                                artists.add(a)
                            }
                            is re.pinok.data.model.AudioCatalogItem.PlaylistItem -> {
                                val p = item.playlist
                                if (p.id <= 0L || !seenPlaylistKeys.add(p.ownerId to p.id)) continue
                                playlists.add(p)
                            }
                            is re.pinok.data.model.AudioCatalogItem.RadioItem -> Unit
                        }
                        if (tracks.size >= count) break
                    }
                    if (tracks.size >= count) break
                }
                AppLog.i("VKApiClient",
                    "audioSearchWithSections(catalog): query='$query' → " +
                        "${tracks.size} tracks, ${artists.size} artists, ${playlists.size} playlists")
                // Если catalog дал хотя бы треки — возвращаем его.
                // Если ничего не дал — пробуем классический audio.search ниже.
                if (tracks.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()) {
                    return re.pinok.data.model.AudioSearchResult(tracks, artists, playlists)
                }
            }
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "audioSearchWithSections: catalog.getAudioSearch failed: ${e.message}")
        }

        // 2) Fallback на классические методы (для direct-auth токенов).
        try {
            val (_, t) = audioSearch(query, count, 0)
            tracks.addAll(t)
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "audioSearchWithSections: audio.search failed: ${e.message}")
        }
        try {
            artists.addAll(audioSearchArtists(query, count = 10))
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "audioSearchWithSections: audio.searchArtists failed: ${e.message}")
        }
        try {
            playlists.addAll(audioSearchPlaylists(query, count = 10))
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "audioSearchWithSections: audio.searchPlaylists failed: ${e.message}")
        }
        return re.pinok.data.model.AudioSearchResult(tracks, artists, playlists)
    }

    /** #68: audio.searchPlaylists — поиск плейлистов. */
    suspend fun audioSearchPlaylists(query: String, count: Int = 20): List<AudioPlaylist> {
        if (isOffline() || query.isBlank()) return emptyList()
        val json = call("audio.searchPlaylists", mapOf(
            "q" to query,
            "count" to count.toString(),
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            // Fix #270: используем универсальный parseAudioPlaylist — он правильно
            // парсит photo как JsonObject ({photo_1200, photo_600, ...}), а НЕ как
            // строку. Раньше тут было `o.get("photo")?.asString` → краш
            // UnsupportedOperationException: JsonObject (см. лог 11:08:41).
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseAudioPlaylist(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioSearchPlaylists error", e)
            emptyList()
        }
    }

    /** Получить текст песни. VK: audio.getLyrics */
    suspend fun audioGetLyrics(lyricsId: Long): String? {
        if (isOffline() || lyricsId == 0L) return null
        val json = call("audio.getLyrics", mapOf("lyrics_id" to lyricsId.toString()))
        return try {
            val resp = json?.getAsJsonObject("response") ?: return null
            resp.get("text")?.asString
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "audioGetLyrics error: ${e.message}")
            null
        }
    }

    /**
     * Получить URL трека если track.url == null.
     * VK: audio.getById — возвращает полный объект трека с url.
     */
    suspend fun audioGetById(track: Track): Track? {
        if (isOffline()) return null
        val audioId = "${track.ownerId}_${track.id}"
        val args = mutableMapOf("audios" to audioId)
        if (track.accessKey != null) args["access_key"] = track.accessKey
        // Fix #147: quality=hq для максимального качества. audio.getById
        // используется как refresh path когда track.url==null (устарел URL) —
        // без quality=hq получим 128kbps вместо 320kbps.
        val snap = prefs.data.first()
        if (snap.musicHighQuality) args["quality"] = "hq"
        val json = call("audio.getById", args) ?: return null
        val apiResult = try {
            val resp = json.getAsJsonArray("response") ?: return null
            val o = resp.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            Track(
                id = o.get("id")?.asLong ?: track.id,
                ownerId = o.get("owner_id")?.asLong ?: track.ownerId,
                artist = o.get("artist")?.asString ?: track.artist,
                title = o.get("title")?.asString ?: track.title,
                duration = o.get("duration")?.asInt ?: track.duration,
                url = extractAudioUrl(o),  // #AUDIO-UNMASK
                albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong ?: track.albumId,
                albumThumb = extractAlbumThumb(o).let { it ?: track.albumThumb },
                accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString ?: track.accessKey,
                lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong ?: track.lyricsId,
                subtitle = o.get("subtitle")?.takeIf { !it.isJsonNull }?.asString ?: track.subtitle,
                genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt ?: track.genreId,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetById error", e)
            null
        }

        // §42.12 P1 #4: al_audio.php web-fallback.
        // Если API вернул трек без url (или null) — пробуем web-endpoint
        // через remixsid. VKNext использует этот путь как последний шанс.
        if (apiResult != null && !apiResult.url.isNullOrBlank()) {
            return apiResult
        }
        AppLog.i("VKApiClient", "audioGetById: API returned no url for #$audioId — trying al_audio.php fallback")
        val fallback = AlAudioFallback(httpClient, exchangeAuthRepository).fetchReloadAudio(track)
        if (fallback != null && !fallback.url.isNullOrBlank()) {
            AppLog.i("VKApiClient", "audioGetById: al_audio.php fallback OK for #$audioId")
            return fallback
        }
        return apiResult
    }

    /** Получить плейлисты пользователя. VK: audio.getPlaylists */
    suspend fun audioGetPlaylists(
        ownerId: Long = 0, // 0 = текущий пользователь
        count: Int = 30,
        offset: Int = 0,
    ): Pair<Int, List<re.pinok.data.model.AudioPlaylist>> {
        if (isOffline()) return 0 to emptyList()
        // #MUSIC-PORT-FIX: audio.getPlaylists требует owner_id обязательно
        // (иначе err=100 "owner_id is undefined"). Для ownerId=0 берём текущего юзера.
        val effectiveOwnerId = if (ownerId != 0L) ownerId else (exchangeAuthRepository?.userId() ?: 0L)
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        if (effectiveOwnerId != 0L) args["owner_id"] = effectiveOwnerId.toString()
        val json = call("audio.getPlaylists", args)
        return try {
            val resp = json?.getAsJsonObject("response") ?: return 0 to emptyList()
            val totalCount = resp.get("count")?.asInt ?: 0
            val items = resp.getAsJsonArray("items") ?: return totalCount to emptyList()
            val playlists = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                // #MUSIC-PORT-FIX: photo — JsonObject (не строка), парсим через
                // parseAudioPlaylist (иначе UnsupportedOperationException: JsonObject).
                parseAudioPlaylist(el.asJsonObject)
            }
            totalCount to playlists
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetPlaylists parse error", e)
            0 to emptyList()
        }
    }

    /** Получить треки из плейлиста. VK: audio.get с album_id */
    suspend fun audioGetPlaylistTracks(
        playlistId: Long,
        ownerId: Long = 0,
        accessKey: String? = null,
        count: Int = 50,
        offset: Int = 0,
    ): Pair<Int, List<Track>> {
        if (isOffline()) return 0 to emptyList()
        val args = mutableMapOf(
            "album_id" to playlistId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        if (ownerId != 0L) args["owner_id"] = ownerId.toString()
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        // Fix #147: quality=hq для максимального качества в треках плейлиста.
        if (prefs.data.first().musicHighQuality) args["quality"] = "hq"
        val json = call("audio.get", args)
        return json?.let { parseAudioResponseWithCount(it) } ?: (0 to emptyList())
    }

    /** Рекомендации музыки. VK: audio.getRecommendations */
    suspend fun audioGetRecommendations(
        count: Int = 30,
        offset: Int = 0,
    ): Pair<Int, List<Track>> {
        if (isOffline()) return 0 to emptyList()
        // Fix #147: quality=hq для максимального качества в рекомендациях.
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        if (prefs.data.first().musicHighQuality) args["quality"] = "hq"
        val json = call("audio.getRecommendations", args)
        return json?.let { parseAudioResponseWithCount(it) } ?: (0 to emptyList())
    }

    /**
     * §42.12 P2 #6: получить все ID треков из источника (плейлист/запись/страница).
     *
     * VK: `audio.getAudioIdsBySource` — возвращает массив audio_id для пагинации.
     * Используется VKNext для batch-скачивания плейлистов: сначала получаем все
     * ID, потом батчами по 100 через `audio.getById` получаем полные объекты.
     *
     * @param sourceType  "playlist" | "wall" | "page"
     * @param ownerId     владелец источника
     * @param sourceId    ID источника (playlist_id / post_id / page_id)
     * @param accessKey   access_key для приватных плейлистов (опц.)
     * @return список пар (ownerId, trackId) или пустой список при ошибке.
     */
    suspend fun audioGetAudioIdsBySource(
        sourceType: String,
        ownerId: Long,
        sourceId: Long,
        accessKey: String? = null,
    ): List<Pair<Long, Long>> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "type" to sourceType,
            "owner_id" to ownerId.toString(),
            "id" to sourceId.toString(),
        )
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        val json = call("audio.getAudioIdsBySource", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonArray("response") ?: return emptyList()
            val result = ArrayList<Pair<Long, Long>>(resp.size())
            for (el in resp) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                val oid = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: continue
                val tid = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: continue
                result.add(oid to tid)
            }
            AppLog.i("VKApiClient", "audioGetAudioIdsBySource: $sourceType/$ownerId/$sourceId → ${result.size} ids")
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetAudioIdsBySource error", e)
            emptyList()
        }
    }

    /**
     * §42.12 P2 #6 helper: пакетно получить полные объекты Track по списку ID.
     *
     * `audio.getById` принимает до 300 audio_id за раз. Мы бьём на батчи по 100
     * (безопасный лимит, VK иногда режет). Каждый батч — отдельный API call.
     *
     * @param ids список пар (ownerId, trackId)
     * @param onProgress опциональный callback (fetched, total) для UI-прогресса.
     * @return список Track (с url если audioGetById отдал его).
     */
    suspend fun audioGetByIdBatch(
        ids: List<Pair<Long, Long>>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val result = ArrayList<Track>(ids.size)
        val batchSize = 100
        var fetched = 0
        for (batchStart in ids.indices step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, ids.size)
            val batch = ids.subList(batchStart, batchEnd)
            // audio.getById принимает "ownerId_trackId,ownerId_trackId,..."
            val audiosParam = batch.joinToString(",") { (oid, tid) -> "${oid}_${tid}" }
            val args = mutableMapOf("audios" to audiosParam)
            // Fix #147: quality=hq для максимального качества в batch-запросах
            // (используется для скачивания плейлистов).
            if (prefs.data.first().musicHighQuality) args["quality"] = "hq"
            val json = call("audio.getById", args)
            if (json != null) {
                try {
                    val resp = json.getAsJsonArray("response")
                    if (resp != null) {
                        for (el in resp) {
                            if (!el.isJsonObject) continue
                            val o = el.asJsonObject
                            val track = Track(
                                id = o.get("id")?.asLong ?: continue,
                                ownerId = o.get("owner_id")?.asLong ?: continue,
                                artist = o.get("artist")?.asString ?: "",
                                title = o.get("title")?.asString ?: "",
                                duration = o.get("duration")?.asInt ?: 0,
                                url = extractAudioUrl(o),
                                albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
                                albumThumb = extractAlbumThumb(o),
                                accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                                lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong,
                                subtitle = o.get("subtitle")?.takeIf { !it.isJsonNull }?.asString,
                                genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
                            )
                            result.add(track)
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e("VKApiClient", "audioGetByIdBatch parse error (batch $batchStart)", e)
                }
            }
            fetched = batchEnd
            onProgress?.invoke(fetched, ids.size)
        }
        AppLog.i("VKApiClient", "audioGetByIdBatch: ${result.size}/${ids.size} tracks fetched")
        return result
    }

    // ─── P0: Музыкальный каталог ───────────────────────────────────

    /**
     * Section-id для музыкального каталога (catalog.getSection).
     *
     * #MUSIC-CATALOG-SECTION (2026-08-17/18): `catalog.getAudio` недоступен нашему
     * web-токену (err=8/err=3), а `catalog.getSection` РАБОТАЕТ на api.vk.com.
     * Section-id получены из server-rendered разметки снапшотов m.vk.ru
     * (data-section-id) и подтверждены live-запросом на устройстве.
     *
     * explore — ПОЛНЫЙ id из «Обзор_мобайл.html» (AudioSection__explore):
     * 9 блоков — Сегодня в плеере / Выбор редакции / Новинки / Оставаться в
     * тренде / Новые альбомы / Новые имена / Летнее настроение / Самые ожидаемые
     * новинки / Новинки по жанрам.
     */
    private val CATALOG_SECTION_IDS = mapOf(
        "general" to "PUldVA8FR0RzSVNUUlEFAzQKBVQZFlJEfFpFVA0WUVdxWllPBgVTVjs",
        "my" to "PUldVA8FR0RzSVNUWE1JSmRSS0wEGEleZFFYQQQEUlV3U1kL",
        "explore" to "PUldVA8FR0RzSVNUUEwbCikZDFQZFlJEfFpFVA0WUVdxWllPBgVTVjs",
        // #MUSIC-UPDATES: «Обновления» (following_updates) — новые треки от
        // артистов, на которых подписан. id из «музыка_Обновления.html».
        "updates" to "PUldVA8FR0RzSVNUU1sHCikcABhSax4WIgodE0YWR0R_SVNHGRZTRHxaXkcFDVhXflsU",
    )

    /**
     * catalogGetAudio — музыкальный каталог (Главная / Моя музыка / Обзор).
     *
     * #MUSIC-CATALOG-SECTION: реализовано через `catalog.getSection` + section-id
     * (api.vk.com), т.к. `catalog.getAudio` недоступен web-токену. Ответ
     * `response.section.blocks[]` + параллельные массивы audios/playlists/
     * recommended_playlists резолвится в блоки [re.pinok.data.model.CatalogBlock].
     */
    suspend fun catalogGetAudio(
        section: String = "general",
        blockId: String? = null,
        count: Int = 10,
        startFrom: String? = null,
    ): List<re.pinok.data.model.CatalogBlock> {
        if (isOffline()) return emptyList()
        val sectionId = CATALOG_SECTION_IDS[section]
            ?: CATALOG_SECTION_IDS["general"]
            ?: return emptyList()
        return catalogGetSectionById(sectionId, startFrom)
    }

    /**
     * #MUSIC-CATALOG-SHOW-ALL: catalog.getSection по произвольному section_id
     * (используется для «Показать все» — section_id из actions header-блока).
     */
    suspend fun catalogGetSectionById(
        sectionId: String,
        startFrom: String? = null,
    ): List<re.pinok.data.model.CatalogBlock> {
        if (isOffline() || sectionId.isBlank()) return emptyList()
        val args = mutableMapOf(
            "section_id" to sectionId,
            "need_blocks" to "1",
        )
        if (startFrom != null) args["start_from"] = startFrom

        val json = call("catalog.getSection", args)
        return try {
            val resp = json?.getAsJsonObject("response") ?: return emptyList()
            parseCatalogSectionBlocks(resp)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "catalog.getSection parse error", e)
            emptyList()
        }
    }


    /**
     * #MUSIC-CATALOG-SECTION: парсит ответ catalog.getSection (api.vk.com).
     *
     * Формат:
     *   response.section = { id, title, breadcrumbs, blocks: [...] }
     *   response.audios[] / playlists[] / recommended_playlists[] — параллельные массивы.
     *   blocks[].data_type + audios_ids[] / playlists_ids[] — rawId-строки
     *   "ownerId_id", ссылающиеся на параллельные массивы.
     *
     * Поддерживает также формат catalog.getAudioSearch:
     *   response.catalog = { default_section, sections: [{ id, title, blocks: [...] }] }.
     */
    private fun parseCatalogSectionBlocks(resp: JsonObject): List<re.pinok.data.model.CatalogBlock> {
        val result = mutableListOf<re.pinok.data.model.CatalogBlock>()

        val audiosById = HashMap<String, Track>()
        resp.getAsJsonArray("audios")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val t = parseTrackFromJson(o) ?: return@forEach
            audiosById["${t.ownerId}_${t.id}"] = t
        }
        val playlistsById = HashMap<String, re.pinok.data.model.AudioPlaylist>()
        for (key in listOf("playlists", "albums")) {
            resp.getAsJsonArray(key)?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val p = parseAudioPlaylist(el.asJsonObject) ?: return@forEach
                playlistsById["${p.ownerId}_${p.id}"] = p
            }
        }
        // #MUSIC-CATALOG-RECOMMENDED: recommended_playlists — ОТДЕЛЬНЫЙ массив
        // (без title, но с percentage/cover/color). НЕ смешиваем с playlists —
        // иначе title-less объекты затирали бы записи с реальным title.
        val recommendedById = HashMap<String, JsonObject>()
        resp.getAsJsonArray("recommended_playlists")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val ownerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
            val id = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: return@forEach
            recommendedById["${ownerId}_${id}"] = o
        }

        // Формат catalog.getSection: response.section.blocks.
        val sectionObj = resp.getAsJsonObject("section")
        val blocksArr = sectionObj?.getAsJsonArray("blocks")
            ?: resp.getAsJsonObject("catalog")?.getAsJsonArray("sections")?.firstOrNull()?.asJsonObject?.getAsJsonArray("blocks")
            ?: return result

        // #MUSIC-CATALOG-SHOW-ALL: section_id «Показать все» лежит в actions
        // header-блока (layout=header/header_extended), а контент-блок идёт
        // следующим. Запоминаем pending и прикрепляем к следующему контент-блоку.
        var pendingShowAllId: String? = null
        for (be in blocksArr) {
            if (!be.isJsonObject) continue
            val o = be.asJsonObject
            val layoutName = o.getAsJsonObject("layout")?.get("name")?.takeIf { !it.isJsonNull }?.asString
            // header-блок: извлекаем show-all section_id, сам блок не рендерим.
            if (layoutName == "header" || layoutName == "header_extended") {
                val actions = o.getAsJsonArray("actions")
                val openSection = actions?.firstOrNull { el ->
                    el.isJsonObject && el.asJsonObject.getAsJsonObject("action")
                        ?.get("type")?.takeIf { !it.isJsonNull }?.asString == "open_section"
                }?.asJsonObject
                pendingShowAllId = openSection?.get("section_id")?.takeIf { !it.isJsonNull }?.asString
                    ?: pendingShowAllId
                continue
            }
            if (layoutName == "separator") {
                result.add(re.pinok.data.model.CatalogBlock(
                    viewType = re.pinok.data.model.CatalogViewType.SEPARATOR,
                    title = null,
                ))
                continue
            }
            val block = parseCatalogWebBlock(o, audiosById, playlistsById, recommendedById, pendingShowAllId)
            if (block != null) {
                result.add(block)
                pendingShowAllId = null
            }
        }
        return result
    }

    /** #MUSIC-CATALOG-SECTION: парсит один блок каталога. */
    private fun parseCatalogWebBlock(
        o: JsonObject,
        audiosById: Map<String, Track>,
        playlistsById: Map<String, re.pinok.data.model.AudioPlaylist>,
        recommendedById: Map<String, JsonObject>,
        showAllId: String? = null,
    ): re.pinok.data.model.CatalogBlock? {
        val dataType = o.get("data_type")?.takeIf { !it.isJsonNull }?.asString
        val layout = o.getAsJsonObject("layout")
        val layoutName = layout?.get("name")?.takeIf { !it.isJsonNull }?.asString
        val blockId = o.get("id")?.takeIf { !it.isJsonNull }?.asString
        val title = o.get("title")?.takeIf { !it.isJsonNull }?.asString
            ?: layout?.get("title")?.takeIf { !it.isJsonNull }?.asString

        val tracks = mutableListOf<Track>()
        val playlists = mutableListOf<re.pinok.data.model.CatalogPlaylist>()

        when (dataType) {
            "music_audios" -> {
                o.getAsJsonArray("audios_ids")?.forEach { idEl ->
                    val rawId = idEl.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                    audiosById[rawId]?.let { tracks.add(it) }
                }
                o.getAsJsonArray("audios")?.forEach { el ->
                    if (!el.isJsonObject) return@forEach
                    parseTrackFromJson(el.asJsonObject)?.let { tracks.add(it) }
                }
            }
            "music_playlists", "music_recommended_playlists" -> {
                o.getAsJsonArray("playlists_ids")?.forEach { idEl ->
                    val rawId = idEl.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                    val base = playlistsById[rawId]
                    if (dataType == "music_recommended_playlists") {
                        // #MUSIC-CATALOG-RECOMMENDED: title из playlists,
                        // matchPercent/cover из recommended_playlists.
                        val rec = recommendedById[rawId]
                        val pct = rec?.get("percentage")?.takeIf { !it.isJsonNull }?.asDouble
                        val recCover = rec?.get("cover")?.takeIf { !it.isJsonNull }?.asString
                        playlists.add(
                            re.pinok.data.model.CatalogPlaylist(
                                id = base?.id ?: rawId.substringAfter('_').toLongOrNull() ?: 0L,
                                ownerId = base?.ownerId ?: rawId.substringBefore('_').toLongOrNull() ?: 0L,
                                title = base?.title ?: "",
                                coverUrl = recCover ?: base?.coverUrl,
                                count = base?.count ?: 0,
                                accessKey = base?.accessKey,
                                blockId = blockId,
                                matchPercent = pct?.let { (it * 100).toInt() },
                            )
                        )
                    } else {
                        base?.let { playlists.add(toCatalogPlaylist(it)) }
                    }
                }
                o.getAsJsonArray("playlists")?.forEach { el ->
                    if (!el.isJsonObject) return@forEach
                    parseAudioPlaylist(el.asJsonObject)?.let { playlists.add(toCatalogPlaylist(it)) }
                }
            }
        }

        val viewType = when {
            dataType == "music_audios" -> re.pinok.data.model.CatalogViewType.TRIPLE_STACKED_SLIDER
            dataType == "music_playlists" && layoutName == "recomms_slider" ->
                re.pinok.data.model.CatalogViewType.RECOMMS_SLIDER
            dataType == "music_playlists" -> re.pinok.data.model.CatalogViewType.LARGE_SLIDER
            dataType == "music_recommended_playlists" -> re.pinok.data.model.CatalogViewType.LARGE_SLIDER
            layoutName == "header_extended" -> re.pinok.data.model.CatalogViewType.HEADER_EXTENDED
            layoutName == "header" -> re.pinok.data.model.CatalogViewType.HEADER
            layoutName == "separator" -> re.pinok.data.model.CatalogViewType.SEPARATOR
            else -> re.pinok.data.model.CatalogViewType.UNKNOWN
        }

        if (viewType == re.pinok.data.model.CatalogViewType.UNKNOWN) return null
        if ((viewType == re.pinok.data.model.CatalogViewType.HEADER ||
                    viewType == re.pinok.data.model.CatalogViewType.HEADER_EXTENDED) && title == null) return null
        if (tracks.isEmpty() && playlists.isEmpty() &&
            viewType != re.pinok.data.model.CatalogViewType.HEADER &&
            viewType != re.pinok.data.model.CatalogViewType.HEADER_EXTENDED &&
            viewType != re.pinok.data.model.CatalogViewType.SEPARATOR) return null

        return re.pinok.data.model.CatalogBlock(
            viewType = viewType,
            title = title,
            blockId = blockId,
            showAllId = showAllId ?: blockId,
            tracks = tracks,
            playlists = playlists,
            subtitle = null,
        )
    }

    /** #MUSIC-CATALOG-WEB-GATEWAY: AudioPlaylist → CatalogPlaylist. */
    private fun toCatalogPlaylist(p: re.pinok.data.model.AudioPlaylist): re.pinok.data.model.CatalogPlaylist =
        re.pinok.data.model.CatalogPlaylist(
            id = p.id,
            ownerId = p.ownerId,
            title = p.title,
            subtitle = p.description,
            coverUrl = p.coverUrl,
            count = p.count,
            plays = p.plays,
            accessKey = p.accessKey,
        )

    /**
     * Парсит массив блоков из ответа catalog.getAudio.
     *
     * Формат ответа:
     *   response.items[] — массив блоков
     *   Каждый блок имеет: type (view_type), title, id (block_id),
     *   и вложенные data.items[] — треки или плейлисты.
     */
    private fun parseCatalogBlocks(resp: JsonObject): List<re.pinok.data.model.CatalogBlock> {
        val items = resp.getAsJsonArray("items") ?: return emptyList()
        return items.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val viewTypeRaw = o.get("view_type")?.takeIf { !it.isJsonNull }?.asString
            val viewType = re.pinok.data.model.CatalogViewType.fromRaw(viewTypeRaw)

            // Пропускаем рекламные блоки и промо-подписки
            val title = o.get("title")?.takeIf { !it.isJsonNull }?.asString
            if (isAdOrSubscriptionBlock(title, viewType)) return@mapNotNull null

            val blockId = o.get("id")?.takeIf { !it.isJsonNull }?.asString
            val subtitle = o.get("subtitle")?.takeIf { !it.isJsonNull }?.asString

            // Вложенные элементы (треки или плейлисты)
            val dataObj = o.getAsJsonObject("data")
            val nestedItems = dataObj?.getAsJsonArray("items")

            val tracks = mutableListOf<Track>()
            val playlists = mutableListOf<re.pinok.data.model.CatalogPlaylist>()

            if (nestedItems != null) {
                for (ni in nestedItems) {
                    if (!ni.isJsonObject) continue
                    val nio = ni.asJsonObject
                    // Плейлисты имеют type="audio_playlist" или поле playlist
                    val itemType = nio.get("type")?.takeIf { !it.isJsonNull }?.asString
                    if (itemType == "audio_playlist" || nio.has("playlist")) {
                        playlists.add(parseCatalogPlaylist(nio))
                    } else {
                        val t = parseTrackFromCatalogItem(nio)
                        if (t != null) tracks.add(t)
                    }
                }
            }

            re.pinok.data.model.CatalogBlock(
                viewType = viewType,
                title = title,
                blockId = blockId,
                showAllId = blockId,
                tracks = tracks,
                playlists = playlists,
                subtitle = subtitle,
            )
        }
    }

    /** Парсит трек из элемента каталога (format может отличаться от audio.get). */
    private fun parseTrackFromCatalogItem(o: JsonObject): Track? {
        // Формат 1: прямые поля (audio.get style)
        val id = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val ownerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        if (id <= 0L || ownerId == 0L) return null

        return Track(
            id = id,
            ownerId = ownerId,
            artist = o.get("artist")?.takeIf { !it.isJsonNull }?.asString ?: "",
            title = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            duration = o.get("duration")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            url = extractAudioUrl(o),  // #AUDIO-UNMASK
            albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
            albumThumb = extractCatalogAlbumThumb(o),
            accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
            lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong,
            isExplicit = o.get("is_explicit")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            mainArtists = parseMainArtists(o),
            subtitle = o.get("subtitle")?.takeIf { !it.isJsonNull }?.asString,
            genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
        )
    }

    /** Извлекает обложку из разных форматов каталога. */
    private fun extractCatalogAlbumThumb(o: JsonObject): String? {
        // Прямая строка
        val direct = o.get("album_thumb")?.takeIf { !it.isJsonNull }?.asString
        if (!direct.isNullOrBlank()) return direct
        // Объект album.thumb
        val thumbObj = o.getAsJsonObject("album")?.getAsJsonObject("thumb") ?: return null
        for (key in listOf("photo_600", "photo_300", "photo_270", "photo_135", "photo_68", "photo_34")) {
            val url = thumbObj.get(key)?.takeIf { !it.isJsonNull }?.asString
            if (!url.isNullOrBlank()) return url
        }
        // Массив thumb (новый формат): cover_url или photos[]
        val coverUrl = o.get("cover_url")?.takeIf { !it.isJsonNull }?.asString
        if (!coverUrl.isNullOrBlank()) return coverUrl
        return null
    }

    /** Парсит main_artists массив. */
    private fun parseMainArtists(o: JsonObject): List<TrackArtist>? {
        val arr = o.getAsJsonArray("main_artists") ?: return null
        if (arr.isEmpty) return null
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val a = el.asJsonObject
            TrackArtist(
                id = a.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                name = a.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                domain = a.get("domain")?.takeIf { !it.isJsonNull }?.asString,
            )
        }.takeIf { it.isNotEmpty() }
    }

    /** Парсит плейлист из элемента каталога. */
    private fun parseCatalogPlaylist(o: JsonObject): re.pinok.data.model.CatalogPlaylist {
        val playlist = o.getAsJsonObject("playlist") ?: o
        return re.pinok.data.model.CatalogPlaylist(
            id = playlist.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            ownerId = playlist.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            title = playlist.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
            subtitle = playlist.get("subtitle")?.takeIf { !it.isJsonNull }?.asString,
            description = playlist.get("description")?.takeIf { !it.isJsonNull }?.asString,
            coverUrl = extractPlaylistCover(playlist),
            count = playlist.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            plays = playlist.get("plays")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            accessKey = playlist.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
            blockId = o.get("id")?.takeIf { !it.isJsonNull }?.asString,
            // match_percent для блока «Слушайте друг друга»
            matchPercent = o.get("match_percent")?.takeIf { !it.isJsonNull }?.asInt,
        )
    }

    /** Извлекает обложку плейлиста (разные форматы VK). */
    private fun extractPlaylistCover(o: JsonObject): String? {
        for (key in listOf("photo_600", "photo_300", "photo_270", "photo_200", "photo_135", "photo_100")) {
            val url = o.get(key)?.takeIf { !it.isJsonNull }?.asString
            if (!url.isNullOrBlank()) return url
        }
        val photos = o.getAsJsonArray("thumbs")
        if (photos != null && photos.size() > 0) {
            // Берём последний (самый большой)
            for (i in (photos.size() - 1) downTo 0) {
                val url = photos.get(i)?.takeIf { !it.isJsonNull }?.asString
                if (!url.isNullOrBlank()) return url
            }
        }
        return o.get("photo")?.takeIf { !it.isJsonNull }?.asString
    }

    /** Фильтрация рекламных/подписочных блоков. */
    private fun isAdOrSubscriptionBlock(title: String?, viewType: CatalogViewType): Boolean {
        if (viewType == CatalogViewType.SEPARATOR || viewType == CatalogViewType.HEADER) return false
        val t = (title ?: "").lowercase()
        return t.contains("подписк") ||
            t.contains("_vk Music pass") ||
            t.contains("premium") ||
            t.contains("пробн") ||
            t.contains("0 ₽") ||
            t.contains("реклам") ||
            viewType == CatalogViewType.UNKNOWN
    }

    /**
     * audio.getPlaylistById — открыть плейлист с треками.
     * Возвращает пару (плейлист, список треков).
     */
    suspend fun audioGetPlaylistById(
        playlistId: Long,
        ownerId: Long = 0,
        accessKey: String? = null,
        count: Int = 50,
        offset: Int = 0,
    ): Pair<re.pinok.data.model.AudioPlaylist?, List<Track>> {
        if (isOffline()) return null to emptyList()
        // #MUSIC-PORT-FIX: audio.getPlaylistById требует owner_id и playlist_id
        // (иначе err=100). Для ownerId=0 берём текущего юзера.
        val effectiveOwnerId = if (ownerId != 0L) ownerId else (exchangeAuthRepository?.userId() ?: 0L)
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        if (effectiveOwnerId != 0L) args["owner_id"] = effectiveOwnerId.toString()
        args["playlist_id"] = playlistId.toString()
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        // #FIX-A-HQ (2026-08-03): quality=hq везде — максимальное качество аудио
        // (320kbps MP3 / HQ AAC). Применяется ко ВСЕМ audio.get-family методам.
        if (prefs.data.first().musicHighQuality) args["quality"] = "hq"

        val json = call("audio.getPlaylistById", args)
        // #PLAYLIST-COMMUNITY: для плейлистов сообществ (VK Музыка, owner_id<0)
        // response — это САМ объект плейлиста (id/owner_id/title/count/photo),
        // БЕЗ поля "audios" (play_button=false). Треки берём отдельно через
        // audio.get(album_id). Для своих плейлистов — response.playlist + audios.
        return try {
            val resp = json?.getAsJsonObject("response") ?: return null to emptyList()
            // Плейлист: либо resp.playlist{...}, либо resp сам = плейлист.
            val pl = resp.getAsJsonObject("playlist")
            val playlistObj = if (pl != null) pl else {
                // resp сам — плейлист (формат сообщества).
                if (resp.get("id") != null && resp.get("owner_id") != null) resp else null
            }
            val playlist = if (playlistObj != null) {
                parseAudioPlaylist(playlistObj) ?: re.pinok.data.model.AudioPlaylist(
                    id = playlistId,
                    ownerId = effectiveOwnerId,
                    title = "",
                )
            } else null

            // Треки (#MUSIC-PORT-FIX: поле может быть "audios" или "audio").
            val audioArr = resp.getAsJsonArray("audios") ?: resp.getAsJsonArray("audio")
            if (audioArr != null) {
                val tracks = audioArr.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val o = el.asJsonObject
                    Track(
                        id = o.get("id")?.asLong ?: 0L,
                        ownerId = o.get("owner_id")?.asLong ?: 0L,
                        artist = o.get("artist")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        title = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        duration = o.get("duration")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                        url = extractAudioUrl(o),  // #AUDIO-UNMASK
                        albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
                        albumThumb = extractAlbumThumb(o),
                        accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                        lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong,
                    )
                }
                playlist to tracks
            } else {
                // #PLAYLIST-COMMUNITY: audios нет в ответе — грузим audio.get(album_id).
                val (_, tracks) = audioGetPlaylistTracks(
                    playlistId = playlistId,
                    ownerId = effectiveOwnerId,
                    accessKey = accessKey,
                    count = count,
                )
                playlist to tracks
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audio.getPlaylistById parse error", e)
            null to emptyList()
        }
    }

    // ─── Sprint 6: Поиск + закладки — API-методы ───────────────────

    /** Поиск по новостям/постам. VK: newsfeed.search */
    suspend fun newsfeedSearch(
        query: String,
        count: Int = 20,
        offset: Int = 0,
    ): Pair<Int, List<Post>> {
        if (isOffline() || query.isBlank()) return 0 to emptyList()
        val json = call("newsfeed.search", mapOf(
            "q" to query,
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
        ))
        return try {
            val resp = json?.getAsJsonObject("response") ?: return 0 to emptyList()
            val totalCount = resp.get("total_count")?.asInt ?: 0
            val items = resp.getAsJsonArray("items") ?: return totalCount to emptyList()
            val posts = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parsePostMini(el.asJsonObject)
            }
            totalCount to posts
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "newsfeedSearch parse error", e)
            0 to emptyList()
        }
    }

    // ========================================================================
    //  SOVA_2_lenta: Модерация ленты
    // ========================================================================

    /**
     * Скрыть элемент из ленты. VK: newsfeed.ignoreItem.
     *
     * @param type Тип: "wall", "photo", "video", "topic", "note"
     * @param ownerId ID владельца
     * @param itemId ID элемента
     * @return true при успехе
     */
    suspend fun newsfeedIgnoreItem(type: String, ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        val json = call("newsfeed.ignoreItem", mapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),
        ))
        return json?.has("response") == true
    }

    /**
     * Вернуть скрытый элемент в ленту. VK: newsfeed.unignoreItem.
     */
    suspend fun newsfeedUnignoreItem(type: String, ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        val json = call("newsfeed.unignoreItem", mapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),
        ))
        return json?.has("response") == true
    }

    /**
     * Получить список скрытых источников ленты. VK: newsfeed.getBanned.
     *
     * @return Triple: (список групп, список пользователей, список ссылок-источников)
     *         каждая запись содержит {id, name, type, ...}
     */
    suspend fun newsfeedGetBanned(): NewsfeedBannedResult {
        if (isOffline()) return NewsfeedBannedResult(emptyList(), emptyList(), emptyList())
        val json = call("newsfeed.getBanned", mapOf("extended" to "1"))
        return try {
            val resp = json?.getAsJsonObject("response") ?: return NewsfeedBannedResult(emptyList(), emptyList(), emptyList())
            val groups = resp.getAsJsonArray("groups")?.mapNotNull {
                if (!it.isJsonObject) null else it.asJsonObject
            } ?: emptyList()
            val profiles = resp.getAsJsonArray("profiles")?.mapNotNull {
                if (!it.isJsonObject) null else it.asJsonObject
            } ?: emptyList()
            val items = resp.getAsJsonArray("items")?.mapNotNull {
                if (!it.isJsonObject) null else it.asJsonObject
            } ?: emptyList()
            NewsfeedBannedResult(groups = groups, profiles = profiles, items = items)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "newsfeedGetBanned parse error", e)
            NewsfeedBannedResult(emptyList(), emptyList(), emptyList())
        }
    }

    data class NewsfeedBannedResult(
        val groups: List<JsonObject>,
        val profiles: List<JsonObject>,
        val items: List<JsonObject>,
    )

    /**
     * Добавить источник в чёрный список ленты. VK: newsfeed.addBan.
     *
     * @param userIds ID пользователей (для бана юзеров)
     * @param groupIds ID групп (для бана групп)
     */
    suspend fun newsfeedAddBan(userIds: List<Long>? = null, groupIds: List<Long>? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        userIds?.takeIf { it.isNotEmpty() }?.let { args["user_ids"] = it.joinToString(",") }
        groupIds?.takeIf { it.isNotEmpty() }?.let { args["group_ids"] = it.joinToString(",") }
        if (args.isEmpty()) return false
        val json = call("newsfeed.addBan", args)
        return json?.has("response") == true
    }

    /**
     * Убрать источник из чёрного списка ленты. VK: newsfeed.unban.
     *
     * @param userIds ID пользователей (для разбана юзеров)
     * @param groupIds ID групп (для разбана групп)
     */
    suspend fun newsfeedUnban(userIds: List<Long>? = null, groupIds: List<Long>? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        userIds?.takeIf { it.isNotEmpty() }?.let { args["user_ids"] = it.joinToString(",") }
        groupIds?.takeIf { it.isNotEmpty() }?.let { args["group_ids"] = it.joinToString(",") }
        if (args.isEmpty()) return false
        val json = call("newsfeed.unban", args)
        return json?.has("response") == true
    }

    // ========================================================================
    //  SOVA_2_lenta: Подписки в ленте
    // ========================================================================

    /**
     * Отписаться от рекомендованного источника в ленте. VK: newsfeed.unsubscribe.
     *
     * @param type "wall" для стен
     * @param ownerId ID группы или пользователя
     * @param itemId ID элемента (поста)
     */
    suspend fun newsfeedUnsubscribe(type: String = "wall", ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        val json = call("newsfeed.unsubscribe", mapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),
        ))
        return json?.has("response") == true
    }

    /**
     * Подписаться на рекомендованный источник. VK: newsfeed.subscribe.
     *
     * @param type "wall" для стен
     * @param ownerId ID группы или пользователя
     * @param itemId ID элемента (поста)
     */
    suspend fun newsfeedSubscribe(type: String = "wall", ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        val json = call("newsfeed.subscribe", mapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),
        ))
        return json?.has("response") == true
    }

    // ========================================================================
    //  SOVA_2_lenta: Управление постами
    // ========================================================================

    /**
     * Редактировать пост. VK: wall.edit.
     *
     * @param ownerId ID владельца стены
     * @param postId ID поста
     * @param message Новый текст
     * @param attachments Вложения (строка: "photo1_2,video3_4")
     * @param friendsOnly Только для друзей
     * @return true при успехе
     */
    suspend fun wallEdit(
        ownerId: Long,
        postId: Long,
        message: String,
        attachments: String? = null,
        friendsOnly: Boolean = false,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
            "message" to message,
        )
        if (!attachments.isNullOrBlank()) args["attachments"] = attachments
        if (friendsOnly) args["friends_only"] = "1"
        val json = call("wall.edit", args)
        return try {
            json?.getAsJsonObject("response")?.get("post_id")?.asLong != null
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallEdit parse error", e)
            false
        }
    }

    /**
     * Закрепить пост. VK: wall.pin.
     *
     * @return true при успехе
     */
    suspend fun wallPin(ownerId: Long, postId: Long): Boolean {
        if (isOffline()) return false
        val json = call("wall.pin", mapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
        ))
        return try {
            json?.getAsJsonObject("response")?.let { it.get("success")?.asInt == 1 || it.get("post_id")?.asLong != null } == true
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallPin parse error", e)
            false
        }
    }

    /**
     * Открепить пост. VK: wall.unpin.
     */
    suspend fun wallUnpin(ownerId: Long, postId: Long): Boolean {
        if (isOffline()) return false
        val json = call("wall.unpin", mapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
        ))
        return json?.has("response") == true
    }

    /**
     * Восстановить удалённый пост. VK: wall.restore.
     */
    suspend fun wallRestore(ownerId: Long, postId: Long): Boolean {
        if (isOffline()) return false
        val json = call("wall.restore", mapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
        ))
        return try {
            json?.getAsJsonObject("response")?.get("post_id")?.asLong != null
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallRestore parse error", e)
            false
        }
    }

    // ========================================================================
    //  SOVA_2_lenta: Управление комментариями
    // ========================================================================

    /**
     * Удалить комментарий. VK: wall.deleteComment.
     */
    suspend fun wallDeleteComment(ownerId: Long, commentId: Long): Boolean {
        if (isOffline()) return false
        val json = call("wall.deleteComment", mapOf(
            "owner_id" to ownerId.toString(),
            "comment_id" to commentId.toString(),
        ))
        return json?.has("response") == true
    }

    /**
     * Редактировать комментарий. VK: wall.editComment.
     */
    suspend fun wallEditComment(ownerId: Long, commentId: Long, message: String, attachments: String? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "comment_id" to commentId.toString(),
            "message" to message,
        )
        if (!attachments.isNullOrBlank()) args["attachments"] = attachments
        val json = call("wall.editComment", args)
        return json?.has("response") == true
    }

    /**
     * Восстановить удалённый комментарий. VK: wall.restoreComment.
     */
    suspend fun wallRestoreComment(ownerId: Long, commentId: Long): Boolean {
        if (isOffline()) return false
        val json = call("wall.restoreComment", mapOf(
            "owner_id" to ownerId.toString(),
            "comment_id" to commentId.toString(),
        ))
        return json?.has("response") == true
    }

    // ─── #30h: недостающие методы VK API ───────────────────────────────

    /** wall.getById — получить конкретные посты по owner_id_post_id. */
    suspend fun wallGetById(posts: List<Pair<Long, Long>>): WallByIdResult {
        if (isOffline() || posts.isEmpty()) return WallByIdResult(emptyList(), emptyMap())
        val postsStr = posts.joinToString(",") { "${it.first}_${it.second}" }
        val json = call("wall.getById", mapOf(
            "posts" to postsStr,
            "extended" to "1",
        )) ?: return WallByIdResult(emptyList(), emptyMap())
        return try {
            // Fix #233 (Q&A Bug B): с extended=1 VK возвращает ОБЪЕКТ
            // {count, items, profiles, groups}, а не прямой массив. Ранее
            // getAsJsonArray("response") возвращал null → emptyList всегда.
            // PostDetailScreen поэтому не мог дозагрузить пост из Ответов.
            val resp = json.getAsJsonObject("response")
                ?: return WallByIdResult(emptyList(), emptyMap())
            val items = resp.getAsJsonArray("items")
                ?: return WallByIdResult(emptyList(), emptyMap())
            val parsedPosts = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parsePostMini(el.asJsonObject)
            }
            val groups = parseGroupsJsonArray(resp.get("groups"))
            WallByIdResult(parsedPosts, groups)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallGetById parse error", e)
            WallByIdResult(emptyList(), emptyMap())
        }
    }

    /** Результат wall.getById: посты + группы (для отображения имени сообщества). */
    data class WallByIdResult(
        val posts: List<re.pinok.data.model.Post>,
        val groups: Map<Long, GroupInfo>,
    )

    /** wall.likeComment — лайк комментария (type=comment). */
    suspend fun wallLikeComment(ownerId: Long, commentId: Long): Boolean {
        if (isOffline()) return false
        val json = call("likes.add", mapOf(
            "type" to "comment",
            "owner_id" to ownerId.toString(),
            "item_id" to commentId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.add — добавить трек к себе. */
    suspend fun audioAdd(audioId: Long, ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.add", mapOf(
            "audio_id" to audioId.toString(),
            "owner_id" to ownerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.delete — удалить трек из своих. */
    suspend fun audioDelete(audioId: Long, ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.delete", mapOf(
            "audio_id" to audioId.toString(),
            "owner_id" to ownerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** video.add — добавить видео к себе. */
    suspend fun videoAdd(videoId: Long, ownerId: Long, accessKey: String? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "video_id" to videoId.toString(),
            "owner_id" to ownerId.toString(),
        )
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        val json = call("video.add", args) ?: return false
        return json.has("response")
    }

    /** video.delete — удалить видео. */
    suspend fun videoDelete(videoId: Long, ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("video.delete", mapOf(
            "video_id" to videoId.toString(),
            "owner_id" to ownerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** video.getComments — комментарии к видео. */
    suspend fun videoGetComments(ownerId: Long, videoId: Long, count: Int = 20): List<re.pinok.data.model.Comment> {
        if (isOffline()) return emptyList()
        val json = call("video.getComments", mapOf(
            "owner_id" to ownerId.toString(),
            "video_id" to videoId.toString(),
            "count" to count.toString(),
            "extended" to "1",
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { parseComment(it.asJsonObject) }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetComments parse error", e)
            emptyList()
        }
    }

    /** photos.getById — получить URL фото по owner_id_photo_id. */
    suspend fun photosGetById(photos: List<Pair<Long, Long>>): List<re.pinok.data.model.PhotoStandalone> {
        if (isOffline() || photos.isEmpty()) return emptyList()
        val photosStr = photos.joinToString(",") { "${it.first}_${it.second}" }
        val json = call("photos.getById", mapOf("photos" to photosStr, "extended" to "1")) ?: return emptyList()
        return try {
            val arr = json.getAsJsonArray("response") ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.PhotoStandalone(
                    id = o.get("id")?.asLong ?: 0L,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    sizes = emptyList(),
                    largestUrl = o.getAsJsonArray("sizes")?.maxByOrNull {
                        (it.asJsonObject.get("width")?.asInt ?: 0) * (it.asJsonObject.get("height")?.asInt ?: 0)
                    }?.asJsonObject?.get("url")?.asString,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosGetById parse error", e)
            emptyList()
        }
    }

    /** photos.createComment — комментарий к фото. */
    suspend fun photosCreateComment(ownerId: Long, photoId: Long, message: String): Boolean {
        if (isOffline()) return false
        val json = call("photos.createComment", mapOf(
            "owner_id" to ownerId.toString(),
            "photo_id" to photoId.toString(),
            "message" to message,
        )) ?: return false
        return json.has("response")
    }

    /**
     * §48 #VIDEO-BOARD-COMMENT: video.createComment — комментарий к видео.
     *
     * VK API: `video.createComment` с параметрами owner_id, video_id, message.
     * Поддерживает reply_to_comment для threaded replies (как wall.createComment).
     *
     * Возвращает id нового комментария или -1 при ошибке (аналогично
     * [wallCreateComment]). Используется [re.pinok.realtime.NotificationActionReceiver]
     * для RemoteInput-ответа из шторки (§46 #REMOTE-INPUT) когда уведомление
     * о комментарии к видео.
     *
     * @param ownerId владелец видео (отрицательный для групп).
     * @param videoId ID видео.
     * @param message текст комментария.
     * @param replyToComment id комментария для ответа (threaded reply), 0 = новый.
     */
    suspend fun videoCreateComment(
        ownerId: Long,
        videoId: Long,
        message: String,
        replyToComment: Long? = null,
    ): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "video_id" to videoId.toString(),
            "message" to message,
        )
        if (replyToComment != null && replyToComment > 0L) {
            args["reply_to_comment"] = replyToComment.toString()
        }
        val json = call("video.createComment", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.get("comment_id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoCreateComment parse error", e)
            -1L
        }
    }

    /**
     * §48 #VIDEO-BOARD-COMMENT: board.createComment — комментарий в обсуждении.
     *
     * VK API: `board.createComment` с параметрами group_id, topic_id, message.
     * В discussions (обсуждениях групп) комментарии не имеют owner_id/item_id
     * в обычном смысле — вместо этого group_id (положительный, без минуса) и
     * topic_id.
     *
     * В PinoK навигации topic открывается через OpenPost(ownerId, itemId, commentId)
     * где ownerId = -group_id (отрицательный), itemId = topic_id. Поэтому здесь
     * принимаем ownerId (может быть отрицательным = группа) и преобразуем.
     *
     * @param ownerId владелец топика (отрицательный для групп, как в VK API).
     *                Преобразуется в положительный group_id для API.
     * @param topicId ID топика в группе.
     * @param message текст комментария.
     */
    suspend fun boardCreateComment(
        ownerId: Long,
        topicId: Long,
        message: String,
    ): Boolean {
        if (isOffline()) return false
        // group_id должен быть положительным (без минуса).
        val groupId = if (ownerId < 0) -ownerId else ownerId
        val json = call("board.createComment", mapOf(
            "group_id" to groupId.toString(),
            "topic_id" to topicId.toString(),
            "message" to message,
        )) ?: return false
        return json.has("response")
    }

    /** photos.like — лайк фото. */
    suspend fun photosLike(ownerId: Long, photoId: Long): Boolean {
        if (isOffline()) return false
        val json = call("likes.add", mapOf(
            "type" to "photo",
            "owner_id" to ownerId.toString(),
            "item_id" to photoId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** groups.getMembers — участники сообщества. */
    suspend fun groupsGetMembers(groupId: Long, count: Int = 50, offset: Int = 0): List<UserProfile> {
        if (isOffline()) return emptyList()
        val json = call("groups.getMembers", mapOf(
            "group_id" to groupId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "fields" to "photo_100,photo_200,online,last_seen,status,verified",
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseUserProfileMini(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "groupsGetMembers parse error", e)
            emptyList()
        }
    }

    /** board.getTopics — темы обсуждений сообщества. */
    suspend fun boardGetTopics(groupId: Long, count: Int = 30, offset: Int = 0): List<re.pinok.data.model.BoardTopic> {
        if (isOffline()) return emptyList()
        val json = call("board.getTopics", mapOf(
            "group_id" to groupId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            "preview_length" to "100",
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.BoardTopic(
                    id = o.get("id")?.asLong ?: 0L,
                    title = o.get("title")?.asString ?: "",
                    created = o.get("created")?.asLong ?: 0L,
                    creatorId = o.get("created_by")?.asLong ?: 0L,
                    comments = o.get("comments")?.asInt ?: 0,
                    isClosed = o.get("is_closed")?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "boardGetTopics parse error", e)
            emptyList()
        }
    }

    /** board.getComments — комментарии темы обсуждения. */
    suspend fun boardGetComments(groupId: Long, topicId: Long, count: Int = 30, offset: Int = 0): List<re.pinok.data.model.BoardComment> {
        if (isOffline()) return emptyList()
        val json = call("board.getComments", mapOf(
            "group_id" to groupId.toString(),
            "topic_id" to topicId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.BoardComment(
                    id = o.get("id")?.asLong ?: 0L,
                    text = o.get("text")?.asString ?: "",
                    created = o.get("date")?.asLong ?: 0L,
                    creatorId = o.get("from_id")?.asLong ?: 0L,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "boardGetComments parse error", e)
            emptyList()
        }
    }

    /** Получить теги закладок. VK: fave.getTags (getTagList НЕ существует — err=3) */
    suspend fun faveGetTagList(): List<re.pinok.data.model.FaveTag> {
        if (isOffline()) return emptyList()
        val json = call("fave.getTags", emptyMap())
        return try {
            val items = json?.getAsJsonObject("response")?.getAsJsonArray("items")
            items?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.FaveTag(
                    id = o.get("id")?.asLong ?: 0L,
                    name = o.get("name")?.asString ?: "",
                )
            } ?: emptyList()
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "faveGetTagList error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fix #56: fallback для web-токенов (vk1.a.*) — audio.get выдаёт error 3
     * "Unknown method passed", но audio.getCatalog работает.
     *
     * VK web audio-страница вызывает audio.getCatalog?extended=1&need_blocks=1,
     * ответ содержит blocks[] с разными типами (audios_playlist, audios_recaps,
     * audios_recoms, etc). Внутри каждого блока есть audios[] — массив треков.
     * Мы агрегируем все треки из всех блоков и возвращаем как плоский список.
     */
    private suspend fun audioGetCatalogFallback(count: Int): List<Track> {
        val args = mapOf(
            "extended" to "1",
            "need_blocks" to "1",
        )
        val json = call("audio.getCatalog", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val blocks = resp.getAsJsonArray("blocks") ?: return emptyList()
            val result = mutableListOf<Track>()
            // Fix #58: дедупликация по Pair(ownerId, id), НЕ через packedKey.
            // Раньше было `ownerId * 1_000_000_000L + trackId` — это даёт ложные
            // коллизии при trackId >= 10^9 (VK audio ids достигают 10^9+):
            //   (owner=1, id=1_000_000_001) и (owner=2, id=1) → одинаковый packedKey
            //   → один трек терялся. Pair<Long,Long> безопасен и семантически точен
            //   (совпадает с подходом Fix #53 в newsfeedGet).
            val seenKeys = HashSet<Pair<Long, Long>>()
            for (blockEl in blocks) {
                if (!blockEl.isJsonObject) continue
                val block = blockEl.asJsonObject
                val audiosArr = block.getAsJsonArray("audios") ?: continue
                for (audioEl in audiosArr) {
                    if (!audioEl.isJsonObject) continue
                    val o = audioEl.asJsonObject
                    val trackId = o.get("id")?.asLong ?: continue
                    val trackOwnerId = o.get("owner_id")?.asLong ?: continue
                    if (!seenKeys.add(trackOwnerId to trackId)) continue
                    val url = extractAudioUrl(o)  // #AUDIO-UNMASK
                    if (url.isNullOrBlank()) continue  // пропускаем треки без URL
                    result.add(
                        Track(
                            id = trackId,
                            ownerId = trackOwnerId,
                            artist = o.get("artist")?.asString ?: "",
                            title = o.get("title")?.asString ?: "",
                            duration = o.get("duration")?.asInt ?: 0,
                            url = url,
                            albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
                            albumThumb = extractAlbumThumb(o),
                            accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                        )
                    )
                    if (result.size >= count) return result
                }
            }
            AppLog.i("VKApiClient", "audio.getCatalog fallback: ${result.size} треков")
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audio.getCatalog parse error", e)
            emptyList()
        }
    }

    // ─── Audio P1/P2 (Fix #84, music.zip dump 2026-07-15) ────────────────────
    // Расширенные audio API методы, дополняющие #80. Не конфликтуют с #80 —
    // у методов с перекрытыми именами добавлен суффикс Extended и другой возвращаемый тип.

    // [Удалено аудитом #90] audioGetPlaylistByIdExtended — мёртвый код, нигде не вызывался.

    /**
     * audio.createPlaylist — создать плейлист у себя.
     * @return ID созданного плейлиста или -1 при ошибке.
     */
    suspend fun audioCreatePlaylist(
        ownerId: Long,
        title: String,
        description: String? = null,
    ): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "title" to title,
        )
        if (!description.isNullOrBlank()) args["description"] = description
        val json = call("audio.createPlaylist", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonObject("playlist")
                ?.get("id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioCreatePlaylist error", e)
            -1L
        }
    }

    /** audio.editPlaylist — редактировать метаданные плейлиста. */
    suspend fun audioEditPlaylist(
        ownerId: Long,
        playlistId: Long,
        title: String,
        description: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
            "title" to title,
        )
        if (!description.isNullOrBlank()) args["description"] = description
        val json = call("audio.editPlaylist", args) ?: return false
        return json.has("response")
    }

    /** audio.deletePlaylist — удалить плейлист. */
    suspend fun audioDeletePlaylist(ownerId: Long, playlistId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.deletePlaylist", mapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /**
     * audio.addToPlaylist — добавить треки в плейлист.
     * @param audioIds список "owner_id_audio_id" строк.
     * @return ID добавленных записей или пустой список.
     */
    suspend fun audioAddToPlaylist(
        ownerId: Long,
        playlistId: Long,
        audioIds: List<String>,
    ): List<Long> {
        if (isOffline() || audioIds.isEmpty()) return emptyList()
        val json = call("audio.addToPlaylist", mapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
            "audio_ids" to audioIds.joinToString(","),
        )) ?: return emptyList()
        return try {
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("audio_ids")
                ?: return emptyList()
            arr.mapNotNull { it.asJsonObject?.get("audio_id")?.asLong }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioAddToPlaylist error", e)
            emptyList()
        }
    }

    /** audio.removeFromPlaylist — удалить треки из плейлиста. */
    suspend fun audioRemoveFromPlaylist(
        ownerId: Long,
        playlistId: Long,
        audioIds: List<String>,
    ): Boolean {
        if (isOffline() || audioIds.isEmpty()) return false
        val json = call("audio.removeFromPlaylist", mapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
            "audio_ids" to audioIds.joinToString(","),
        )) ?: return false
        return json.has("response")
    }

    /**
     * audio.getIdsBySource — получить ID треков источника (плейлиста/альбома).
     * Используется для подгрузки треков плейлиста по chunks.
     */
    suspend fun audioGetIdsBySource(
        source: String = "playlist",
        entityId: String,
        ref: String? = null,
    ): List<String> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "source" to source,
            "entity_id" to entityId,
        )
        if (!ref.isNullOrBlank()) args["ref"] = ref
        val json = call("audio.getIdsBySource", args) ?: return emptyList()
        return try {
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("ids")
                ?: json.getAsJsonArray("response")
                ?: return emptyList()
            arr.mapNotNull { el ->
                when {
                    el.isJsonPrimitive -> el.asString
                    el.isJsonObject -> {
                        val o = el.asJsonObject
                        "${o.get("owner_id")?.asString ?: return@mapNotNull null}_${o.get("id")?.asString ?: return@mapNotNull null}"
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetIdsBySource error", e)
            emptyList()
        }
    }

    /** audio.followPlaylist — подписаться на обновления плейлиста (бесплатно). */
    suspend fun audioFollowPlaylist(ownerId: Long, playlistId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.followPlaylist", mapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.savePlaylistAsCopy — скопировать плейлист себе. */
    suspend fun audioSavePlaylistAsCopy(ownerId: Long, playlistId: Long): Long {
        if (isOffline()) return -1L
        val json = call("audio.savePlaylistAsCopy", mapOf(
            "owner_id" to ownerId.toString(),
            "playlist_id" to playlistId.toString(),
        )) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonObject("playlist")
                ?.get("id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioSavePlaylistAsCopy error", e)
            -1L
        }
    }

    /** audio.restore — восстановить удалённый трек. */
    suspend fun audioRestore(audioId: Long, ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.restore", mapOf(
            "audio_id" to audioId.toString(),
            "owner_id" to ownerId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.reorder — переместить трек в списке (drag-and-drop). */
    suspend fun audioReorder(
        audioId: Long,
        ownerId: Long,
        before: String? = null,
        after: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "audio_id" to audioId.toString(),
            "owner_id" to ownerId.toString(),
        )
        if (!before.isNullOrBlank()) args["before"] = before
        if (!after.isNullOrBlank()) args["after"] = after
        val json = call("audio.reorder", args) ?: return false
        return json.has("response")
    }

    /** audio.addDislike — поставить «Не нравится». */
    suspend fun audioAddDislike(audioIds: List<String>): Boolean {
        if (isOffline() || audioIds.isEmpty()) return false
        val json = call("audio.addDislike", mapOf(
            "audio_ids" to audioIds.joinToString(","),
        )) ?: return false
        return json.has("response")
    }

    /** audio.removeDislike — снять «Не нравится». */
    suspend fun audioRemoveDislike(audioIds: List<String>): Boolean {
        if (isOffline() || audioIds.isEmpty()) return false
        val json = call("audio.removeDislike", mapOf(
            "audio_ids" to audioIds.joinToString(","),
        )) ?: return false
        return json.has("response")
    }

    /** audio.searchArtists — поиск артистов. */
    suspend fun audioSearchArtists(query: String, count: Int = 20): List<re.pinok.data.model.AudioArtist> {
        if (isOffline() || query.isBlank()) return emptyList()
        // #MUSIC-PORT-FIX: audio.searchArtists → err=3 для web-токена.
        // catalog.getAudioSearch отдаёт артистов в response.links[] (content_type=artist).
        val json = call("catalog.getAudioSearch", mapOf(
            "query" to query,
            "need_blocks" to "1",
        )) ?: return emptyList()
        val resp = json.getAsJsonObject("response") ?: return emptyList()
        val result = mutableListOf<re.pinok.data.model.AudioArtist>()
        resp.getAsJsonArray("links")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val o = el.asJsonObject
            val meta = o.getAsJsonObject("meta")
            if (meta?.get("content_type")?.takeIf { !it.isJsonNull }?.asString != "artist") return@forEach
            val name = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            val url = o.get("url")?.takeIf { !it.isJsonNull }?.asString
            val slug = url?.substringAfterLast('/', "")?.takeIf { it.isNotBlank() }
            val images = o.getAsJsonArray("image")
            val photo = images?.lastOrNull()?.asJsonObject?.get("url")?.takeIf { !it.isJsonNull }?.asString
                ?: images?.firstOrNull()?.asJsonObject?.get("url")?.takeIf { !it.isJsonNull }?.asString
            result.add(re.pinok.data.model.AudioArtist(
                id = 0L,
                name = name,
                domain = slug,
                photo = photo,
                photo200 = photo,
                followers = 0,
            ))
            if (result.size >= count) return result
        }
        return result
    }

    /** audio.searchAlbums — поиск альбомов. */
    suspend fun audioSearchAlbums(query: String, count: Int = 20): List<re.pinok.data.model.AudioPlaylist> {
        if (isOffline() || query.isBlank()) return emptyList()
        // #MUSIC-PORT-FIX: audio.searchAlbums → err=3 для web-токена.
        // Альбомы отдаются в response.albums[] и response.playlists[] (type=album).
        val json = call("catalog.getAudioSearch", mapOf(
            "query" to query,
            "need_blocks" to "1",
        )) ?: return emptyList()
        val resp = json.getAsJsonObject("response") ?: return emptyList()
        val result = mutableListOf<re.pinok.data.model.AudioPlaylist>()
        val seen = HashSet<Pair<Long, Long>>()
        for (key in listOf("albums", "playlists")) {
            resp.getAsJsonArray(key)?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val p = parseAudioPlaylist(el.asJsonObject) ?: return@forEach
                if (p.id > 0L && seen.add(p.ownerId to p.id)) {
                    result.add(p)
                    if (result.size >= count) return result
                }
            }
        }
        return result
    }

    /** audio.getAudiosByArtist — топ-треки артиста (slug + имя). */
    suspend fun audioGetAudiosByArtist(
        slug: String,
        name: String,
        count: Int = 50,
    ): List<Track> {
        if (isOffline() || name.isBlank()) return emptyList()
        // #MUSIC-PORT-FIX: для web-токена недоступны audio.getAudiosByArtist /
        // catalog.getAudioArtist / catalog.getAudio / audio.getCatalog (все → err=3).
        // Рабочий путь — catalog.getAudioSearch(query=<имя>): треки артиста приходят
        // в response.audios[], фильтруем по main_artists (имя/domain) и artist-строке.
        val json = call("catalog.getAudioSearch", mapOf(
            "query" to name,
            "need_blocks" to "1",
        )) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val result = mutableListOf<Track>()
            val seen = HashSet<Pair<Long, Long>>()
            val nameL = name.lowercase()
            val slugL = slug.trimStart('_').lowercase()
            resp.getAsJsonArray("audios")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val t = parseTrackFromJson(o) ?: return@forEach
                if (t.id <= 0L || t.ownerId == 0L || !seen.add(t.ownerId to t.id)) return@forEach
                val artistStr = t.artist.lowercase()
                val nameMatch = nameL.isNotBlank() && artistStr.contains(nameL)
                val slugMatch = slugL.isNotBlank() && artistStr.contains(slugL)
                val mainArtistsMatch = o.getAsJsonArray("main_artists")?.any { ma ->
                    if (!ma.isJsonObject) return@any false
                    val mao = ma.asJsonObject
                    val maName = mao.get("name")?.takeIf { !it.isJsonNull }?.asString?.lowercase()
                    val maDomain = mao.get("domain")?.takeIf { !it.isJsonNull }?.asString?.lowercase()
                    (maName != null && maName.contains(nameL)) ||
                        (slugL.isNotBlank() && maDomain != null && (maDomain == slugL || maDomain == "_$slugL"))
                } ?: false
                if (nameMatch || slugMatch || mainArtistsMatch) {
                    result.add(t)
                    if (result.size >= count) return result
                }
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetAudiosByArtist error", e)
            emptyList()
        }
    }

    /** audio.getArtistsById — артисты по IDs. */
    suspend fun audioGetArtistsById(artistIds: List<Long>): List<re.pinok.data.model.AudioArtist> {
        if (isOffline() || artistIds.isEmpty()) return emptyList()
        val json = call("audio.getArtistsById", mapOf(
            "artist_ids" to artistIds.joinToString(","),
        )) ?: return emptyList()
        return try {
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?: json.getAsJsonArray("response")
                ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseAudioArtist(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetArtistsById error", e)
            emptyList()
        }
    }

    /** audio.getRelatedArtistsById — похожие артисты. */
    suspend fun audioGetRelatedArtists(artistId: Long): List<re.pinok.data.model.AudioArtist> {
        if (isOffline()) return emptyList()
        val json = call("audio.getRelatedArtistsById", mapOf(
            "artist_id" to artistId.toString(),
        )) ?: return emptyList()
        return try {
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?: json.getAsJsonArray("response")
                ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseAudioArtist(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetRelatedArtists error", e)
            emptyList()
        }
    }

    /** audio.followArtist — подписаться на артиста (бесплатно). */
    suspend fun audioFollowArtist(artistId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.followArtist", mapOf(
            "artist_id" to artistId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.unfollowArtist — отписаться от артиста. */
    suspend fun audioUnfollowArtist(artistId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.unfollowArtist", mapOf(
            "artist_id" to artistId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.followRadioStation — подписаться на радиостанцию (бесплатно). */
    suspend fun audioFollowRadioStation(stationId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.followRadioStation", mapOf(
            "station_id" to stationId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.unfollowRadioStation — отписаться от радиостанции. */
    suspend fun audioUnfollowRadioStation(stationId: Long): Boolean {
        if (isOffline()) return false
        val json = call("audio.unfollowRadioStation", mapOf(
            "station_id" to stationId.toString(),
        )) ?: return false
        return json.has("response")
    }

    /** audio.radioGetById — получить радиостанцию. */
    suspend fun audioRadioGetById(stationId: Long): re.pinok.data.model.AudioRadioStation? {
        if (isOffline()) return null
        val json = call("audio.radioGetById", mapOf(
            "station_id" to stationId.toString(),
        )) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            // Smart-cast не срабатывает на сложном if-else с элвисом — берём non-null явно.
            val o: JsonObject = when {
                resp.has("items") -> resp.getAsJsonArray("items")?.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
                resp.has("id") -> resp
                else -> return null
            }
            re.pinok.data.model.AudioRadioStation(
                id = o.get("id")?.asLong ?: 0L,
                title = o.get("title")?.asString ?: "",
                coverUrl = o.get("cover_url")?.takeIf { !it.isJsonNull }?.asString,
                genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
                isFollowed = o.get("is_followed")?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioRadioGetById error", e)
            null
        }
    }

    /** audio.getSnippets — сниппеты треков (для вложений). */
    suspend fun audioGetSnippets(audioIds: List<String>): List<Track> {
        if (isOffline() || audioIds.isEmpty()) return emptyList()
        val args = mutableMapOf(
            "audio_ids" to audioIds.joinToString(","),
        )
        // #FIX-A-HQ: quality=hq везде.
        if (prefs.data.first().musicHighQuality) args["quality"] = "hq"
        val json = call("audio.getSnippets", args) ?: return emptyList()
        return try {
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?: json.getAsJsonArray("response")
                ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseTrackFromJson(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetSnippets error", e)
            emptyList()
        }
    }

    /** audio.getSearchSuggestions — подсказки поиска (для autocomplete). */
    suspend fun audioGetSearchSuggestions(query: String): List<String> {
        if (isOffline() || query.isBlank()) return emptyList()
        val json = call("audio.getSearchSuggestions", mapOf(
            "q" to query,
        )) ?: return emptyList()
        return try {
            val arr = json.getAsJsonArray("response") ?: return emptyList()
            arr.mapNotNull { el ->
                when {
                    el.isJsonPrimitive -> el.asString
                    el.isJsonObject -> el.asJsonObject.get("title")?.asString
                        ?: el.asJsonObject.get("name")?.asString
                    else -> null
                }
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "audioGetSearchSuggestions error", e)
            emptyList()
        }
    }

    // ─── Catalog API (Fix #79) ───────────────────────────────────────────────

    /**
     * catalog.getAudio — расширенная версия (Fix #84).
     * Дополняет catalogGetAudio (#80) параметром `prefetch=true` и возвращает
     * полную секцию с mixed типами (треки + плейлисты + артисты + радио в одном блоке).
     * Используется для catalog.getAudioSearch / catalog.getAudioArtist responses,
     * где структура отличается от простого catalog.getAudio.
     * @param section "general" | "my" | "explore" (по умолчанию "general").
     */
    suspend fun catalogGetAudioExtended(
        section: String = "general",
        prefetch: Boolean = true,
    ): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "section" to section,
        )
        if (prefetch) args["prefetch"] = "1"
        val json = call("catalog.getAudio", args) ?: return null
        return parseCatalogSectionExtended(json, section)
    }

    /** catalog.getSection — получить секцию по section_id (пагинация). */
    suspend fun catalogGetSectionExtended(
        sectionId: String,
        startFrom: String? = null,
    ): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "section_id" to sectionId,
        )
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        val json = call("catalog.getSection", args) ?: return null
        return parseCatalogSectionExtended(json, "")
    }

    /** catalog.getBlockItems — элементы блока (пагинация для «Показать все»). */
    suspend fun catalogGetBlockItemsExtended(
        blockId: String,
        startFrom: String? = null,
    ): re.pinok.data.model.AudioCatalogBlock? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "block_id" to blockId,
        )
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        val json = call("catalog.getBlockItems", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            parseCatalogBlockExtended(resp, "block")
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "catalogGetBlockItems error", e)
            null
        }
    }

    /** catalog.getAudioArtist — страница артиста в каталоге (slug или numeric-id строкой). */
    suspend fun catalogGetAudioArtistExtended(
        artistId: String,
    ): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline() || artistId.isBlank()) return null
        val json = call("catalog.getAudioArtist", mapOf(
            "artist_id" to artistId,
            // #MUSIC-PORT-FIX: need_blocks=1 — иначе вернутся только названия секций.
            "need_blocks" to "1",
        )) ?: return null
        return parseCatalogSectionExtended(json, "artist")
    }

    /** catalog.getAudioSearch — поиск в каталоге аудио (с табами). */
    suspend fun catalogGetAudioSearchExtended(
        query: String,
        startFrom: String? = null,
    ): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline() || query.isBlank()) return null
        // #MUSIC-PORT-FIX: параметр называется `query` (не `q`), и нужен
        // need_blocks=1 — иначе VK вернёт только названия секций без блоков.
        val args = mutableMapOf(
            "query" to query,
            "need_blocks" to "1",
        )
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        val json = call("catalog.getAudioSearch", args) ?: return null
        return parseCatalogSectionExtended(json, "search")
    }

    /** catalog.getSearchAll — глобальный поиск. */
    suspend fun catalogGetSearchAllExtended(query: String): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline() || query.isBlank()) return null
        val json = call("catalog.getSearchAll", mapOf("q" to query)) ?: return null
        return parseCatalogSectionExtended(json, "search_all")
    }

    /** catalog.getSearchTop — топ-результаты поиска. */
    suspend fun catalogGetSearchTopExtended(query: String): re.pinok.data.model.AudioCatalogSection? {
        if (isOffline() || query.isBlank()) return null
        val json = call("catalog.getSearchTop", mapOf("q" to query)) ?: return null
        return parseCatalogSectionExtended(json, "search_top")
    }

    /** catalog.hideBlock — скрыть блок (dislike). */
    suspend fun catalogHideBlockExtended(blockId: String): Boolean {
        if (isOffline()) return false
        val json = call("catalog.hideBlock", mapOf(
            "block_id" to blockId,
        )) ?: return false
        return json.has("response")
    }

    // ─── Парсеры для audio/catalog (Fix #79) ─────────────────────────────────

    /** Универсальный парсер Track из JsonObject. */
    private fun parseTrackFromJson(o: JsonObject): Track? {
        return try {
            val trackId = o.get("id")?.asLong ?: return null
            val trackOwnerId = o.get("owner_id")?.asLong ?: return null
            Track(
                id = trackId,
                ownerId = trackOwnerId,
                artist = o.get("artist")?.takeIf { !it.isJsonNull }?.asString ?: "",
                title = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                duration = o.get("duration")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                url = extractAudioUrl(o),  // #AUDIO-UNMASK
                albumId = o.get("album_id")?.takeIf { !it.isJsonNull }?.asLong,
                albumThumb = extractAlbumThumb(o),
                accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                lyricsId = o.get("lyrics_id")?.takeIf { !it.isJsonNull }?.asLong,
                mainArtists = o.getAsJsonArray("main_artists")?.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val a = el.asJsonObject
                    re.pinok.data.model.TrackArtist(
                        id = a.get("id")?.asLong ?: 0L,
                        name = a.get("name")?.asString ?: "",
                        domain = a.get("domain")?.takeIf { !it.isJsonNull }?.asString,
                        photo = a.get("photo")?.takeIf { !it.isJsonNull }?.asString,
                    )
                },
                isExplicit = o.get("is_explicit")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                isHq = o.get("is_hq")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "parseTrackFromJson failed: ${e.message}")
            null
        }
    }

    /** Универсальный парсер AudioPlaylist из JsonObject. */
    private fun parseAudioPlaylist(o: JsonObject): re.pinok.data.model.AudioPlaylist? {
        return try {
            val id = o.get("id")?.asLong ?: return null
            val ownerId = o.get("owner_id")?.asLong ?: 0L
            val photo = o.getAsJsonObject("photo")
            re.pinok.data.model.AudioPlaylist(
                id = id,
                ownerId = ownerId,
                title = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                photo = photo?.get("photo_1200")?.takeIf { !it.isJsonNull }?.asString
                    ?: photo?.get("photo_600")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = photo?.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                photo300 = photo?.get("photo_300")?.takeIf { !it.isJsonNull }?.asString,
                photo600 = photo?.get("photo_600")?.takeIf { !it.isJsonNull }?.asString,
                count = o.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                genreId = o.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
                type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
                accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                followers = o.get("followers")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                plays = o.get("plays")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            )
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "parseAudioPlaylist failed: ${e.message}")
            null
        }
    }

    /** Универсальный парсер AudioArtist из JsonObject. */
    private fun parseAudioArtist(o: JsonObject): re.pinok.data.model.AudioArtist? {
        return try {
            val id = o.get("id")?.asLong ?: return null
            re.pinok.data.model.AudioArtist(
                id = id,
                name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                domain = o.get("domain")?.takeIf { !it.isJsonNull }?.asString,
                photo = o.get("photo")?.takeIf { !it.isJsonNull }?.asString,
                photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                followers = o.get("followers")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                genres = o.getAsJsonArray("genres")?.mapNotNull {
                    it.takeIf { !it.isJsonNull }?.asString
                },
                isFollowed = o.get("is_followed")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "parseAudioArtist failed: ${e.message}")
            null
        }
    }

    /** Парсер каталожной секции (catalog.getAudio / getSection / getAudioSearch). */
    private fun parseCatalogSectionExtended(
        json: JsonObject,
        section: String,
    ): re.pinok.data.model.AudioCatalogSection? {
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val sectionId = resp.get("section_id")?.takeIf { !it.isJsonNull }?.asString
                ?: resp.get("catalog_section_id")?.takeIf { !it.isJsonNull }?.asString
                ?: ""
            val nextFrom = resp.get("next_from")?.takeIf { !it.isJsonNull }?.asString
            val blocksArr = resp.getAsJsonArray("blocks")
                ?: resp.getAsJsonArray("items")
                ?: return re.pinok.data.model.AudioCatalogSection(sectionId, section, emptyList(), nextFrom)
            val blocks = blocksArr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseCatalogBlockExtended(el.asJsonObject, "")
            }
            re.pinok.data.model.AudioCatalogSection(sectionId, section, blocks, nextFrom)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "parseCatalogSection error", e)
            null
        }
    }

    /** Парсер каталожного блока. title берётся из "title" или "header". */
    private fun parseCatalogBlockExtended(o: JsonObject, defaultTitle: String): re.pinok.data.model.AudioCatalogBlock? {
        return try {
            val id = o.get("id")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("block_id")?.takeIf { !it.isJsonNull }?.asString
                ?: ""
            val title = o.get("title")?.takeIf { !it.isJsonNull }?.asString
                ?: o.getAsJsonObject("header")?.get("title")?.takeIf { !it.isJsonNull }?.asString
                ?: defaultTitle
            val nextFrom = o.get("next_from")?.takeIf { !it.isJsonNull }?.asString
            val blockType = o.get("type")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("block_type")?.takeIf { !it.isJsonNull }?.asString
            val items = mutableListOf<re.pinok.data.model.AudioCatalogItem>()
            o.getAsJsonArray("audios")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                parseTrackFromJson(el.asJsonObject)?.let {
                    items.add(re.pinok.data.model.AudioCatalogItem.TrackItem(it))
                }
            }
            o.getAsJsonArray("playlists")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                parseAudioPlaylist(el.asJsonObject)?.let {
                    items.add(re.pinok.data.model.AudioCatalogItem.PlaylistItem(it))
                }
            }
            o.getAsJsonArray("artists")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                parseAudioArtist(el.asJsonObject)?.let {
                    items.add(re.pinok.data.model.AudioCatalogItem.ArtistItem(it))
                }
            }
            o.getAsJsonArray("radios")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val r = el.asJsonObject
                items.add(re.pinok.data.model.AudioCatalogItem.RadioItem(
                    re.pinok.data.model.AudioRadioStation(
                        id = r.get("id")?.asLong ?: 0L,
                        title = r.get("title")?.asString ?: "",
                        coverUrl = r.get("cover_url")?.takeIf { !it.isJsonNull }?.asString,
                        genreId = r.get("genre_id")?.takeIf { !it.isJsonNull }?.asInt,
                        isFollowed = r.get("is_followed")?.asBoolean ?: false,
                    )
                ))
            }
            o.getAsJsonArray("items")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val item = el.asJsonObject
                when (item.get("type")?.takeIf { !it.isJsonNull }?.asString ?: item.get("kind")?.asString) {
                    "audio", "music_track" -> parseTrackFromJson(item)?.let {
                        items.add(re.pinok.data.model.AudioCatalogItem.TrackItem(it))
                    }
                    "playlist", "music_album_playlist", "music_generated_playlist" -> parseAudioPlaylist(item)?.let {
                        items.add(re.pinok.data.model.AudioCatalogItem.PlaylistItem(it))
                    }
                    "artist", "music_artist" -> parseAudioArtist(item)?.let {
                        items.add(re.pinok.data.model.AudioCatalogItem.ArtistItem(it))
                    }
                    "radio" -> {
                        items.add(re.pinok.data.model.AudioCatalogItem.RadioItem(
                            re.pinok.data.model.AudioRadioStation(
                                id = item.get("id")?.asLong ?: 0L,
                                title = item.get("title")?.asString ?: "",
                            )
                        ))
                    }
                }
            }
            re.pinok.data.model.AudioCatalogBlock(id, title, items, nextFrom, blockType)
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "parseCatalogBlock failed: ${e.message}")
            null
        }
    }

    /**
     * Лайк на пост. VK: likes.add — type=post, owner_id, item_id.
     * Возвращает обновлённое количество лайков или -1 при ошибке.
     *
     * §37.12 #326: access_key передаётся как ОТДЕЛЬНЫЙ параметр (НЕ appended
     * к item_id). Ранее код делал item_id="videoId_accessKey" — это НЕправильно,
     * VK API ожидает item_id=videoId (число) + access_key=... (отдельный параметр).
     * Appended-формат вызывал err=100 "object not found" для приватных clips.
     *
     * @param accessKey ключ доступа (для приватных видео/клипов), nullable.
     */
    suspend fun likesAdd(
        type: String,
        ownerId: Long,
        itemId: Long,
        reactionId: Int? = null,
        accessKey: String? = null,
        trackCode: String? = null,
    ): Int {
        val args = mutableMapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),  // §37.12 #326: bare numeric ID, NOT "id_accessKey"
        )
        if (reactionId != null) args["reaction_id"] = reactionId.toString()
        // §37.12 #326: access_key как отдельный параметр (VK API поддерживает это).
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        val json = call("likes.add", args) ?: run {
            AppLog.w("VKApiClient", "likes.add null (errCode=$lastApiErrorCode) for type=$type ownerId=$ownerId itemId=$itemId" +
                " accessKey=${if (accessKey != null) "yes" else "no"} trackCode=${if (trackCode != null) "yes" else "no"}")
            return -1
        }
        return try {
            val likes = getObj(json, "response")?.get("likes")?.takeIf { it.isJsonPrimitive }?.asInt
            if (likes == null) {
                AppLog.w("VKApiClient", "likes.add no 'likes' field in response | errCode=$lastApiErrorCode | json=${json.toString().take(300)}")
                -1
            } else likes
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "likesAdd parse error", e)
            -1
        }
    }

    /**
     * Снять лайк. VK: likes.delete — type=post, owner_id, item_id.
     *
     * §37.12 #326: access_key как отдельный параметр (см. [likesAdd]).
     */
    suspend fun likesDelete(
        type: String,
        ownerId: Long,
        itemId: Long,
        accessKey: String? = null,
        trackCode: String? = null,
    ): Int {
        val args = mutableMapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),  // §37.12 #326: bare numeric ID
        )
        if (!accessKey.isNullOrBlank()) args["access_key"] = accessKey
        val json = call("likes.delete", args) ?: run {
            AppLog.w("VKApiClient", "likes.delete null (errCode=$lastApiErrorCode) for type=$type ownerId=$ownerId itemId=$itemId")
            return -1
        }
        return try {
            val likes = getObj(json, "response")?.get("likes")?.takeIf { it.isJsonPrimitive }?.asInt
            if (likes == null) {
                AppLog.w("VKApiClient", "likes.delete no 'likes' field | errCode=$lastApiErrorCode | json=${json.toString().take(300)}")
                -1
            } else likes
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "likesDelete parse error", e)
            -1
        }
    }

    /**
     * Sprint 2, P1-2 (#89): Проверить, лайкнул ли текущий пользователь объект.
     * VK: likes.isLiked — type, owner_id, item_id.
     *
     * Возвращает:
     *  - `true`  — пользователь лайкнул.
     *  - `false` — не лайкнул.
     *  - `null`  — ошибка API (неверный token, сеть, и т.д.).
     *
     * Используется для инициализации UI-состояния лайка на комментариях,
     * где VK не возвращает `user_likes` в wall.getComments ответе (в отличие
     * от постов, где `likes.user_likes` есть всегда).
     */
    suspend fun likesIsLiked(type: String, ownerId: Long, itemId: Long): Boolean? {
        if (isOffline()) return null
        val args = mapOf(
            "type" to type,
            "owner_id" to ownerId.toString(),
            "item_id" to itemId.toString(),
        )
        val json = call("likes.isLiked", args) ?: return null
        return try {
            // response = { liked: 1/0, copied: 1/0 }
            val resp = json.getAsJsonObject("response") ?: return null
            resp.get("liked")?.takeIf { !it.isJsonNull }?.asInt == 1
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "likesIsLiked parse error", e)
            null
        }
    }

    /**
     * Получить список лайкнутых объектов (реакции). VK: likes.getList
     *
     * §FEED-REACTIONS: VK web использует отдельный API-метод для раздела
     * «Реакции» — /feed?section=likes&filter=wall|wall_reply|video|clips.
     * URL-параметр filter КАРТИРУЕТСЯ в type API-параметр likes.getList:
     *   wall → type=post, wall_reply → type=comment,
     *   video → type=video, clips → type=video.
     * API-параметр filter=likes — только лайкнутые (не копии).
     *
     * @param type Тип: "post" (посты), "comment" (комментарии),
     *             "video" (видео/клипы).
     * @param count Количество (макс. 100).
     * @param offset Смещение для пагинации.
     * @return Pair(totalCount, items) где items — сырые JsonObject'ы.
     */
    suspend fun likesGetList(
        type: String = "post",
        count: Int = 30,
        offset: Int = 0,
    ): Pair<Int, List<JsonObject>> {
        if (isOffline()) return 0 to emptyList()
        val json = call("likes.getList", mapOf(
            "type" to type,
            "filter" to "likes",
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            "fields" to "photo_50,photo_100,verified,screen_name",
        ))
        return try {
            val resp = json?.getAsJsonObject("response")
            val totalCount = resp?.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val items = resp?.getAsJsonArray("items") ?: return totalCount to emptyList()
            val list = items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
            totalCount to list
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "likesGetList parse error", e)
            0 to emptyList()
        }
    }

    /**
     * Добавить комментарий к посту. VK: wall.createComment — owner_id, post_id, message.
     * Возвращает id нового комментария или -1 при ошибке.
     *
     * Fix #209: reply_to_comment — id комментария, на который отвечаем (threaded replies).
     * VK API: при ответе на другой комментарий передаём reply_to_comment=<id>,
     * также VK автоматически подставит reply_to_user (можно указать явно, но не обязательно).
     */
    suspend fun wallCreateComment(
        ownerId: Long,
        postId: Long,
        message: String,
        attachments: String? = null,
        replyToComment: Long? = null,
    ): Long {
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
            "message" to message,
        )
        if (!attachments.isNullOrBlank()) {
            args["attachments"] = attachments
        }
        // Fix #209: ответ на комментарий (threaded reply).
        if (replyToComment != null && replyToComment > 0L) {
            args["reply_to_comment"] = replyToComment.toString()
        }
        val json = call("wall.createComment", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.get("comment_id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallCreateComment parse error", e)
            -1L
        }
    }

    // ========================================================================
    //  #43: Полноценная функциональность соцсети — отправка сообщений,
    //       посты на стену, комментарии, заявки в друзья, LongPoll.
    // ========================================================================

    /**
     * Отправить сообщение в диалог. VK: messages.send — peer_id, message, random_id.
     *
     * Возвращает id нового сообщения или -1 при ошибке.
     * `random_id` — уникальный идентификатор для дедупликации (VK требует).
     * Если [randomId] не задан, генерируется из текущего времени.
     */
    suspend fun messagesSend(peerId: Long, message: String, randomId: Long = 0L, attachment: String = "", replyCmid: Long? = null): Long {
        if (isOffline()) return -1L
        val rid = if (randomId != 0L) randomId else randomIdCounter.incrementAndGet()
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "message" to message,
            "random_id" to rid.toString(),
        )
        if (attachment.isNotBlank()) {
            args["attachment"] = attachment
        }
        // Fix #203c: VK API 5.221+ ПОЛНОСТЬЮ deprecated параметр `reply_to` —
        // API отдаёт error 100 "reply_to is deprecated from version 5.221" на
        // любой call с этим параметром (лог 2026-07-23 01:11:08.943).
        // Предыдущие попытки:
        //   - Fix #202: `args["cmid"]` → VK игнорирует неизвестный параметр,
        //     текст уходит БЕЗ ответа (молча).
        //   - Fix #203b: `args["reply_to"]=cmid` → VK отклоняет с error 100.
        // Правильный механизм 2026 (VK API 5.221+): параметр `forward` с JSON:
        //   forward = {"peer_id":<peerId>,"conversation_message_ids":[<cmid>],"is_reply":true}
        // `is_reply:true` отличает ответ (reply) от пересылки (forward).
        // Источник: VK API docs + лог сервера (error 100 с указанием deprecated).
        if (replyCmid != null && replyCmid > 0) {
            val forwardJson = JsonObject().apply {
                addProperty("peer_id", peerId)
                add("conversation_message_ids", JsonArray().apply { add(replyCmid) })
                addProperty("is_reply", true)
            }.toString()
            args["forward"] = forwardJson
        }
        val json = call("messages.send", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesSend parse error", e)
            -1L
        }
    }

    /**
     * Поставить/снять реакцию на сообщение. VK: messages.react.
     *
     * @param peerId     ID диалога.
     * @param messageId  ID сообщения.
     * @param reactionId ID реакции (1=👍, 2=❤️, 3=😂, 4=😭, 5=😡, 6=🎉, 7=🔥, 8=😮).
     *                   Передать 0, чтобы снять свою реакцию.
     * @return true если успешно.
     */
    suspend fun messagesReact(
        peerId: Long,
        messageId: Long,
        reactionId: Int,
    ): Boolean {
        if (isOffline()) return false
        // Audit #40: VK API 5.180+ требует `cmid` (conversation message id),
        // не `message_id`. Для legacy-версий VK принимает оба, но 5.269 — только cmid.
        val args = mapOf(
            "peer_id" to peerId.toString(),
            "cmid" to messageId.toString(),
            "reaction_id" to reactionId.toString(),
        )
        val json = call("messages.react", args) ?: return false
        return json.has("response")
    }

    /**
     * Удалить сообщение. VK: messages.delete.
     * @param messageId ID сообщения (для ЛС). Для групповых — через peerId + messageId.
     * @param spam      Помечаем как спам (1/0).
     * @param deleteForAll Удалить для всех (1/0).
     * @return true если сервер вернул 1.
     */
    suspend fun messagesDelete(
        messageId: Long,
        spam: Boolean = false,
        deleteForAll: Boolean = false,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "message_ids" to messageId.toString(),
        )
        if (spam) args["spam"] = "1"
        if (deleteForAll) args["delete_for_all"] = "1"
        val json = call("messages.delete", args) ?: return false
        // Fix #145/#150: VK WEB gateway (web.api.vk.ru) возвращает response в
        // РАЗНЫХ форматах в зависимости от параметров и типа диалога. Замеченные:
        //  - {"response": {"<msgId>": 1}}            — ЛС, без delete_for_all
        //  - {"response": [1]} / [1, 1, ...]         — при delete_for_all=1 (старый API)
        //  - {"response": 1}                          — массовое удаление
        //  - {"response": {"<msgId>": 0}}            — нет прав (напр. delete_for_all
        //    для чужого сообщения в ЛС, или прошло >24ч) → сервер НЕ удалил
        //  - {"response": {"<msgId>": {"code":15,"description":"..."}}} — WEB gateway
        //    иногда оборачивает per-message ошибку в объект (callInternal пропускает,
        //    т.к. верхнего "error" нет → ответ формально "успешный").
        // Логируем тело ответа всегда (обрезано до 500 символов) для диагностики.
        val bodyStr = try { json.toString() } catch (_: Exception) { "<unstringifiable>" }
        AppLog.d("VKApiClient",
            "messagesDelete: msgId=$messageId deleteForAll=$deleteForAll " +
                "responseBytes=${bodyStr.length} body=${bodyStr.take(500)}")
        return try {
            val resp = json.get("response") ?: return false
            val ok = parseDeleteResponse(resp, messageId)
            AppLog.i("VKApiClient",
                "messagesDelete: msgId=$messageId parsedOk=$ok " +
                    "respType=${if (resp.isJsonObject) "object" else if (resp.isJsonArray) "array" else if (resp.isJsonPrimitive) "primitive" else "other"}")
            ok
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesDelete parse error body=$bodyStr", e)
            false
        }
    }

    /**
     * Fix #207: Удаление сообщения по conversation_message_id (cmid).
     *
     * VK API 5.221+ — параметр `message_ids` УСТАРЕЛ для чатов. Современный способ:
     *   conversation_message_ids=<cmid>&peer_id=<peerId>
     * Без peer_id вызов с conversation_message_ids отдаёт error.
     *
     * В ChatDetailScreen мы имеем Message.conversationMessageId (cmid) — это
     * правильный идентификатор для удаления. Старый messagesDelete (по message_id)
     * уходит «в никуда» на современных чатах → UI фильтрует локально, но сервер
     * не удаляет → после перезахода в диалог сообщение снова видно («висит»).
     *
     * Fix #207b: VK API error 15 "Access denied: message can not be deleted
     * (self message)" — в ЛС нельзя удалить СВОЁ сообщение с delete_for_all=1
     * (только для себя, delete_for_all=0). При этой ошибке автоматически ретраим
     * с delete_for_all=0.
     *
     * Формат ответа cmid-based delete (отличается от старого message_ids!):
     *   Успех:  {"response":[{"peer_id":..,"conversation_message_id":<cmid>}]}
     *   Провал: {"response":[{"peer_id":..,"conversation_message_id":<cmid>,
     *                          "error":{"code":15,"description":"..."}}]}
     *
     * @return true если сервер подтвердил удаление.
     */
    suspend fun messagesDeleteByCmid(
        peerId: Long,
        cmid: Long,
        spam: Boolean = false,
        deleteForAll: Boolean = false,
    ): Boolean {
        if (isOffline()) return false
        val ok = messagesDeleteByCmidInternal(peerId, cmid, spam, deleteForAll)
        // Fix #207b: если deleteForAll=true и VK отдал error 15 "self message"
        // (ЛС, своё сообщение — нельзя удалить для всех) — ретраим без delete_for_all.
        if (!ok && deleteForAll) {
            AppLog.i("VKApiClient",
                "messagesDeleteByCmid: retry without delete_for_all (error 15 self message fallback) " +
                    "peerId=$peerId cmid=$cmid")
            return messagesDeleteByCmidInternal(peerId, cmid, spam, deleteForAll = false)
        }
        return ok
    }

    private suspend fun messagesDeleteByCmidInternal(
        peerId: Long,
        cmid: Long,
        spam: Boolean,
        deleteForAll: Boolean,
    ): Boolean {
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "conversation_message_ids" to cmid.toString(),
        )
        if (spam) args["spam"] = "1"
        if (deleteForAll) args["delete_for_all"] = "1"
        val json = call("messages.delete", args) ?: return false
        val bodyStr = try { json.toString() } catch (_: Exception) { "<unstringifiable>" }
        AppLog.d("VKApiClient",
            "messagesDeleteByCmid: peerId=$peerId cmid=$cmid deleteForAll=$deleteForAll " +
                "responseBytes=${bodyStr.length} body=${bodyStr.take(500)}")
        return try {
            val resp = json.get("response") ?: return false
            val ok = parseCmidDeleteResponse(resp, cmid)
            AppLog.i("VKApiClient",
                "messagesDeleteByCmid: peerId=$peerId cmid=$cmid deleteForAll=$deleteForAll " +
                    "parsedOk=$ok respType=${if (resp.isJsonObject) "object" else if (resp.isJsonArray) "array" else if (resp.isJsonPrimitive) "primitive" else "other"}")
            ok
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesDeleteByCmid parse error body=$bodyStr", e)
            false
        }
    }

    /**
     * Fix #207b: Парсер ответа cmid-based messages.delete.
     *
     * Формат (array of objects, НЕ array of primitives как в старом message_ids):
     *   [{"peer_id":..,"conversation_message_id":<cmid>}]                       → успех
     *   [{"peer_id":..,"conversation_message_id":<cmid>,"error":{"code":15,...}}]→ провал
     *
     * Также поддерживает старые форматы (primitive/object) на случай если VK
     * вернёт их для backwards-compat.
     */
    private fun parseCmidDeleteResponse(resp: com.google.gson.JsonElement, cmid: Long): Boolean {
        return when {
            // Primitive 1/true (старый формат массового удаления)
            resp.isJsonPrimitive -> {
                val p = resp.asJsonPrimitive
                when {
                    p.isBoolean -> p.asBoolean
                    p.isNumber -> p.asInt == 1
                    p.isString -> p.asString == "1" || p.asString.equals("true", ignoreCase = true)
                    else -> false
                }
            }
            // Array of objects (cmid-based формат 2026)
            resp.isJsonArray -> {
                resp.asJsonArray.any { el ->
                    when {
                        // старый формат: [1, 1, 1]
                        el.isJsonPrimitive -> {
                            val p = el.asJsonPrimitive
                            (p.isNumber && p.asInt == 1) || (p.isBoolean && p.asBoolean)
                        }
                        // новый формат: {"peer_id":..,"conversation_message_id":<cmid>[,"error":{...}]}
                        el.isJsonObject -> {
                            val o = el.asJsonObject
                            val elemCmid = o.get("conversation_message_id")?.takeIf { it.isJsonPrimitive }?.asLong
                            // Если есть поле error — провал (даже если cmid совпал)
                            val err = o.get("error")
                            if (err != null && err.isJsonObject) {
                                val code = err.asJsonObject.get("code")?.takeIf { it.isJsonPrimitive }?.asInt
                                AppLog.w("VKApiClient",
                                    "parseCmidDeleteResponse: cmid=$elemCmid error code=$code " +
                                        "desc=${err.asJsonObject.get("description")?.asString}")
                                false
                            } else {
                                // Нет поля error → успех (cmid опционально проверяем)
                                elemCmid == null || elemCmid == cmid
                            }
                        }
                        else -> false
                    }
                }
            }
            resp.isJsonObject -> {
                // Stub-обработка для backwards-compat (если VK вернёт object)
                parseDeleteResponse(resp, cmid)
            }
            else -> false
        }
    }

    /**
     * Fix #150: robust парсинг ответа messages.delete.
     * Возвращает true если сервер подтвердил удаление.
     *
     * Поддерживаемые структуры response:
     *  - object {"<msgId>": 1|true|"1"}                        → успех
     *  - object {"<msgId>": 0|false|"0"}                        → провал (нет прав)
     *  - object {"<msgId>": {"code":0,"ok":true,"success":1}}  → успех (WEB gateway)
     *  - object {"<msgId>": {"code":15,"description":"..."}}    → провал (per-msg error)
     *  - array [1] / [1, 1]                                     → успех
     *  - primitive 1 / true                                     → успех
     */
    private fun parseDeleteResponse(resp: com.google.gson.JsonElement, messageId: Long): Boolean {
        return when {
            resp.isJsonPrimitive -> {
                val p = resp.asJsonPrimitive
                when {
                    p.isBoolean -> p.asBoolean
                    p.isNumber -> p.asInt == 1
                    p.isString -> p.asString == "1" || p.asString.equals("true", ignoreCase = true)
                    else -> false
                }
            }
            resp.isJsonArray -> {
                resp.asJsonArray.any { el ->
                    el.isJsonPrimitive && (
                        (el.asJsonPrimitive.isNumber && el.asInt == 1) ||
                            (el.asJsonPrimitive.isBoolean && el.asBoolean)
                    )
                }
            }
            resp.isJsonObject -> {
                val obj = resp.asJsonObject
                // Вариант 1: {"<msgId>": <result>}
                val perMsg = obj.get(messageId.toString())
                if (perMsg != null) {
                    return when {
                        perMsg.isJsonPrimitive -> {
                            val p = perMsg.asJsonPrimitive
                            when {
                                p.isBoolean -> p.asBoolean
                                p.isNumber -> p.asInt == 1
                                p.isString -> p.asString == "1" || p.asString.equals("true", ignoreCase = true)
                                else -> false
                            }
                        }
                        perMsg.isJsonObject -> {
                            // {"code":0,"ok":true} или {"code":15,"description":"..."}
                            val code = perMsg.asJsonObject.get("code")?.takeIf { it.isJsonPrimitive }?.asInt
                            val ok = perMsg.asJsonObject.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean
                            val success = perMsg.asJsonObject.get("success")?.takeIf { it.isJsonPrimitive }?.asInt
                            when {
                                // явный code ошибки (ненулевой) → провал
                                code != null && code != 0 -> false
                                // явный признак успеха → true
                                ok == true -> true
                                success != null && success == 1 -> true
                                // code==0 → успех
                                code != null && code == 0 -> true
                                // нет ни code, ни ok, ни success — считаем успехом
                                // (callInternal уже отфильтровал верхний "error")
                                else -> true
                            }
                        }
                        perMsg.isJsonNull -> false
                        else -> false
                    }
                }
                // Вариант 2: response — объект БЕЗ ключа messageId (напр. {"count":1}).
                // callInternal уже отфильтровал "error", значит операция формально
                // успешна. Считаем успехом, лог уже выше покажет структуру.
                true
            }
            else -> false
        }
    }

    /**
     * Отредактировать сообщение. VK: messages.edit.
     * @param peerId    ID диалога.
     * @param messageId ID сообщения.
     * @param message   Новый текст.
     * @param keepForwardMessages Сохранить пересылаемые сообщения.
     * @return true если успешно.
     */
    suspend fun messagesEdit(
        peerId: Long,
        messageId: Long,
        message: String,
        keepForwardMessages: Boolean = true,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "message_id" to messageId.toString(),
            "message" to message,
        )
        if (keepForwardMessages) args["keep_forward_messages"] = "1"
        val json = call("messages.edit", args) ?: return false
        return json.has("response")
    }

    /**
     * Переслать сообщения (включая файлы/вложения) из одного диалога в другой.
     * VK API 5.221+: `messages.send` с параметром `forward` (JSON).
     *
     * @param targetPeerId  Куда пересылаем (peer_id получателя).
     * @param sourcePeerId  Откуда пересылаем (peer_id исходного диалога) —
     *                      нужен для `forward` JSON {peer_id, conversation_message_ids}.
     * @param cmids         conversation_message_id пересылаемых сообщений
     *                      (приоритетный путь, VK API 5.221+).
     * @param message       Комментарий к пересылке (опционально).
     * @return ID нового сообщения или -1.
     *
     * Fix #295 («не получается файлы пересылать из диалога в диалог»):
     * ранее метод использовал параметр `forward_messages` со списком legacy
     * `message_id`. В VK API 5.221+ этот параметр (как и `reply_to`)
     * фактически нерабочий — `messages.send` с `forward_messages` молча
     * теряет вложения (файлы/фото/голосовые) или отдаёт error 100 на
     * сообщениях, идентифицированных только по cmid. Рабочий механизм 2026
     * — параметр `forward` с JSON (тот же, что и для reply, см. Fix #203c):
     *   forward = {"peer_id":<sourcePeerId>,"conversation_message_ids":[<cmid>,…]}
     * (без `is_reply` → это пересылка, а не ответ). Вложения (включая файлы)
     * переносятся сервером VK по ссылке на исходное сообщение.
     */
    suspend fun messagesForward(
        targetPeerId: Long,
        sourcePeerId: Long,
        cmids: List<Long>,
        message: String = "",
    ): Long {
        if (isOffline()) return -1L
        if (cmids.isEmpty()) {
            AppLog.w("VKApiClient", "messagesForward: empty cmids — cannot forward (VK API 5.221+ requires conversation_message_ids)")
            return -1L
        }
        val args = mutableMapOf(
            "peer_id" to targetPeerId.toString(),
            "random_id" to randomIdCounter.incrementAndGet().toString(),
        )
        if (message.isNotBlank()) args["message"] = message

        // Параметр `forward` с JSON — единственный рабочий механизм пересылки
        // в VK API 5.221+. conversation_message_ids указывают на сообщения в
        // исходном диалоге (sourcePeerId), сервер VK сам переносит их
        // (включая вложения/файлы) в targetPeerId.
        val forwardJson = JsonObject().apply {
            addProperty("peer_id", sourcePeerId)
            add("conversation_message_ids", JsonArray().apply {
                cmids.forEach { add(it) }
            })
        }.toString()
        args["forward"] = forwardJson
        AppLog.i("VKApiClient", "messagesForward: target=$targetPeerId source=$sourcePeerId cmids=$cmids forward=$forwardJson")

        val json = call("messages.send", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesForward parse error", e)
            -1L
        }
    }

    /**
     * Опубликовать пост на стене. VK: wall.post — owner_id, message, friends_only.
     *
     * Если [ownerId] равен 0 или не задан — пост уходит на стену текущего
     * пользователя (VK требует пустой/отсутствующий owner_id для этого).
     * Возвращает id нового поста или -1 при ошибке.
     */
    suspend fun wallPost(
        message: String,
        ownerId: Long? = null,
        friendsOnly: Boolean = false,
        publishDate: Long? = null,
    ): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "message" to message,
        )
        if (ownerId != null && ownerId != 0L) args["owner_id"] = ownerId.toString()
        if (friendsOnly) args["friends_only"] = "1"
        // SOVA_2_lenta: отложенный постинг — wall.post с publish_date (unix timestamp)
        if (publishDate != null && publishDate > 0) args["publish_date"] = publishDate.toString()
        val json = call("wall.post", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallPost parse error", e)
            -1L
        }
    }

    /**
     * Удалить пост со стены. VK: wall.delete — owner_id, post_id.
     * Возвращает true при успехе.
     */
    suspend fun wallDelete(ownerId: Long, postId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
        )
        val json = call("wall.delete", args) ?: return false
        return try {
            json.getAsJsonObject("response")?.get("post_id")?.asLong != null
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallDelete parse error", e)
            false
        }
    }

    /**
     * Sprint 2, P1-3 (#90): Репост записи на стену текущего пользователя.
     * VK: wall.repost — object, message.
     *
     * @param object_ Идентификатор объекта в формате `wall{owner_id}_{post_id}`
     *                (например, `wall-12345_678` для поста группы или `wall12345_678`
     *                для поста пользователя).
     * @param message Комментарий к репосту (необязательно). Появится как текст
     *                нового поста-репоста на стене пользователя.
     * @return `Pair<postId, repostsCount>` — id нового поста и обновлённое
     *         количество репостов оригинала. `Pair(-1L, -1)` при ошибке.
     */
    suspend fun wallRepost(object_: String, message: String = ""): Pair<Long, Int> {
        if (isOffline()) return -1L to -1
        val args = mutableMapOf(
            "object" to object_,
        )
        if (message.isNotBlank()) args["message"] = message
        val json = call("wall.repost", args) ?: return -1L to -1
        return try {
            val resp = json.getAsJsonObject("response") ?: return -1L to -1
            val postId = resp.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("id")?.asLong ?: -1L
            val repostsCount = resp.get("reposts_count")?.takeIf { !it.isJsonNull }?.asInt ?: -1
            postId to repostsCount
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallRepost parse error", e)
            -1L to -1
        }
    }

    // ========================================================================
    //  Share / Enhanced Repost: fave.add, messages.send с wall-attachment
    // ========================================================================

    /**
     * Добавить в закладки (fave.add).
     * Audit #40: VK API fave.add принимает разные параметры в зависимости от type:
     * - type=user → user_id
     * - type=group → group_id
     * - type=post/photo/video/article → item_id (формат "ownerId_objectId")
     * - type=link → link_id
     * Раньше отправляли owner_id+id — VK не распознавал, операция всегда падала.
     */
    suspend fun faveAdd(type: String = "post", ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        // #FAVE-WEB-TOKEN: у web-токена (vk1.a.*) `fave.add` возвращает error 3
        // "Unknown method passed" — VK разнёс закладки по отдельным методам:
        // fave.addPost / fave.addVideo / fave.addLink / fave.addPage. Старый
        // универсальный fave.add с параметром type у web-токена не работает.
        val args = mutableMapOf<String, String>()
        val method = when (type) {
            "user", "group", "page" -> {
                if (type == "group") args["group_id"] = itemId.toString()
                else args["user_id"] = itemId.toString()
                "fave.addPage"
            }
            "video" -> {
                args["owner_id"] = ownerId.toString()
                args["id"] = itemId.toString()
                "fave.addVideo"
            }
            "link" -> {
                args["link"] = "${ownerId}_$itemId"
                "fave.addLink"
            }
            else -> {
                args["owner_id"] = ownerId.toString()
                args["id"] = itemId.toString()
                "fave.addPost"
            }
        }
        val json = call(method, args) ?: return false
        return json.has("response") && json.getAsJsonPrimitive("response").isNumber
    }

    /** Удалить из закладок. См. faveAdd — web-токен требует fave.remove* методы. */
    suspend fun faveRemove(type: String = "post", ownerId: Long, itemId: Long): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        val method = when (type) {
            "user", "group", "page" -> {
                if (type == "group") args["group_id"] = itemId.toString()
                else args["user_id"] = itemId.toString()
                "fave.removePage"
            }
            "video" -> {
                args["owner_id"] = ownerId.toString()
                args["id"] = itemId.toString()
                "fave.removeVideo"
            }
            "link" -> {
                args["link_id"] = itemId.toString()
                "fave.removeLink"
            }
            else -> {
                args["owner_id"] = ownerId.toString()
                args["id"] = itemId.toString()
                "fave.removePost"
            }
        }
        val json = call(method, args) ?: return false
        return json.has("response") && json.getAsJsonPrimitive("response").isNumber
    }

    /** Отправить пост в диалог как пересылку (wall attachment). */
    suspend fun sendPostToChat(peerId: Long, ownerId: Long, postId: Long, message: String = ""): Long {
        if (isOffline()) return -1L
        val attachment = "wall${ownerId}_$postId"
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "attachment" to attachment,
            "random_id" to randomIdCounter.incrementAndGet().toString(),
        )
        if (message.isNotBlank()) args["message"] = message
        val json = call("messages.send", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
        } catch (_: Exception) { -1L }
    }

    /**
     * Репост на стену сообщества (wall.post с copy_history).
     * ownerId < 0 для групп.
     */
    suspend fun repostToGroup(
        groupId: Long,
        sourceOwnerId: Long,
        sourcePostId: Long,
        message: String = "",
    ): Long {
        if (isOffline()) return -1L
        val copyHistory = "wall${sourceOwnerId}_${sourcePostId}"
        val args = mutableMapOf(
            "owner_id" to (-groupId).toString(),
            "attachments" to copyHistory,
            "random_id" to randomIdCounter.incrementAndGet().toString(),
        )
        if (message.isNotBlank()) args["message"] = message
        val json = call("wall.post", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonObject("post")?.get("id")?.asLong ?: -1L
        } catch (_: Exception) { -1L }
    }

    // ========================================================================
    //  Sprint 2, P1-4 (#91): Загрузка фото в постах.
    //  Flow: photos.getWallUploadServer → multipart POST → photos.saveWallPhoto
    //        → wall.post(attachments="photo{ownerId}_{photoId}").
    // ========================================================================

    /**
     * Получить upload URL для загрузки фото на стену.
     * VK: photos.getWallUploadServer — group_id (optional).
     * Возвращает `upload_url` или null при ошибке.
     */
    suspend fun photosGetWallUploadServer(groupId: Long? = null): String? {
        if (isOffline()) return null
        val args = mutableMapOf<String, String>()
        if (groupId != null && groupId != 0L) args["group_id"] = groupId.toString()
        val json = call("photos.getWallUploadServer", args) ?: return null
        return try {
            json.getAsJsonObject("response")?.get("upload_url")?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosGetWallUploadServer parse error", e)
            null
        }
    }

    /**
     * Получить URL для загрузки фото в личное сообщение.
     * В отличие от getWallUploadServer, принимает peer_id.
     */
    suspend fun photosGetMessagesUploadServer(peerId: Long): String? {
        if (isOffline()) return null
        val args = mapOf("peer_id" to peerId.toString())
        val json = call("photos.getMessagesUploadServer", args) ?: return null
        return try {
            json.getAsJsonObject("response")?.get("upload_url")?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosGetMessagesUploadServer parse error", e)
            null
        }
    }

    /**
     * Сохранить фото после загрузки через getMessagesUploadServer.
     * Возвращает attachment строку "photo{ownerId}_{id}_{accessKey}".
     */
    suspend fun photosSaveMessagePhoto(
        server: Int,
        photo: String,
        hash: String,
    ): String? {
        if (isOffline()) return null
        val args = mapOf(
            "server" to server.toString(),
            "photo" to photo,
            "hash" to hash,
        )
        val json = call("photos.saveMessagesPhoto", args) ?: return null
        return try {
            val arr = json.getAsJsonArray("response")
            val obj = arr?.get(0)?.asJsonObject ?: return null
            val ownerId = obj.get("owner_id")?.asLong ?: return null
            val id = obj.get("id")?.asLong ?: return null
            val accessKey = obj.get("access_key")?.asString ?: ""
            "photo${ownerId}_${id}" + if (accessKey.isNotEmpty()) "_$accessKey" else ""
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosSaveMessagePhoto parse error", e)
            null
        }
    }

    /**
     * Полный pipeline отправки фото в сообщение:
     * 1. photos.getMessagesUploadServer(peer_id)
     * 2. POST multipart upload
     * 3. photos.save(server, photo, hash)
     * 4. messages.send(peer_id, attachment=photo{owner_id}_{id}_{access_key})
     *
     * Возвращает true если отправлено успешно.
     */
    suspend fun uploadAndSendPhoto(peerId: Long, uri: Uri): Boolean {
        val uploadUrl = photosGetMessagesUploadServer(peerId) ?: run {
            AppLog.e("VKApiClient", "uploadAndSendPhoto: failed to get upload server")
            return false
        }
        val uploaded = photosUploadWallPhoto(uploadUrl, uri) ?: run {
            AppLog.e("VKApiClient", "uploadAndSendPhoto: failed to upload")
            return false
        }
        val attachment = photosSaveMessagePhoto(uploaded.server, uploaded.photo, uploaded.hash) ?: run {
            AppLog.e("VKApiClient", "uploadAndSendPhoto: failed to save photo")
            return false
        }
        AppLog.i("VKApiClient", "uploadAndSendPhoto: attachment=$attachment, sending to peer=$peerId")
        val result = messagesSend(peerId = peerId, message = "", attachment = attachment)
        return result > 0
    }

    /**
     * Fix #233 (photo-send): перегрузка для отправки фото из java.io.File с
     * поддержкой подписи. Раньше ChatDetailScreen.doSend() для файлов, выбранных
     * через меню "Файл" (включая изображения!), безусловно вызывал docs-путь
     * `uploadDocForMessage` → фото приходило как ДОКУМЕНТ (или VK отклонял
     * загрузку → "Не удалось загрузить файл"). Теперь UI может отличить
     * isImage и вызвать этот photos-путь с подписью.
     *
     * VK photos.getMessagesUploadServer требует peer_id (в отличие от docs,
     * где peer_id не нужен). Поэтому метод принимает peerId.
     *
     * Возвращает attachment-строку "photo{ownerId}_{id}" при успехе, null при ошибке.
     * (Возвращает String, не Boolean, чтобы UI могло передать её в sendWithAttachment
     * вместе с текстом-подписью — единый паттерн с uploadDocForMessage.)
     */
    suspend fun uploadPhotoForMessage(peerId: Long, file: java.io.File, mime: String?): String? {
        val uploadUrl = photosGetMessagesUploadServer(peerId) ?: run {
            AppLog.e("VKApiClient", "uploadPhotoForMessage: failed to get upload server for peer=$peerId")
            return null
        }
        val uploaded = photosUploadWallPhoto(uploadUrl, file, mime) ?: run {
            AppLog.e("VKApiClient", "uploadPhotoForMessage: failed to upload")
            return null
        }
        val attachment = photosSaveMessagePhoto(uploaded.server, uploaded.photo, uploaded.hash) ?: run {
            AppLog.e("VKApiClient", "uploadPhotoForMessage: failed to save photo")
            return null
        }
        AppLog.i("VKApiClient", "uploadPhotoForMessage: attachment=$attachment")
        return attachment
    }

    /**
     * Fix #154/#159: загрузить произвольный файл как VK-документ и отправить
     * в чат. Снимает искусственное ограничение на длину текста в messages.send.
     *
     * Контекст проблемы: VK `messages.send` имеет лимит ~4096 символов на поле
     * `message` (API error 914 "Message is too long"). Лог-файлы PinoK и
     * длинные тексты упирались в этот лимит. Но VK поддерживает attachment
     * типа `doc{ownerId}_{id}` — документ до 200 МБ, без ограничения на длину.
     *
     * Этот helper оркестрирует весь flow:
     *  1. Копирует content:// URI → временный File в cacheDir (upload-сервер
     *     принимает multipart только из файла, не из потока).
     *  2. `docs.getMessagesUploadServer(type="doc")` → upload_url
     *     (через существующий [uploadDocForMessage]).
     *  3. multipart POST файла → `{file: "..."}`
     *  4. `docs.save(file, title)` → `{ownerId, id, accessKey}`
     *  5. `messages.send(peer_id, attachment="doc{ownerId}_{id}", message=caption)`
     *  6. Удаляет временный файл.
     *
     * Аналогично [uploadAndSendPhoto], но для документов. Используется из
     * системного share-sheet (ShareToChatSheet) и может быть вызван с любым
     * MIME-типом: text/plain, application/json, application/octet-stream, и т.д.
     *
     * @param peerId   ID диалога
     * @param uri      content:// или file:// URI файла
     * @param mimeType MIME-тип (если null — угадывается по имени файла)
     * @param message  Текст-подпись к документу (опционально, обрезается до
     *                 4000 символов — VK messages.send лимит ~4096)
     * @return true если сообщение с документом отправлено
     */
    suspend fun uploadAndSendDoc(peerId: Long, uri: Uri, mimeType: String? = null, message: String = ""): Boolean {
        // Шаг 1: копируем Uri → временный File. VK upload-сервер требует файл.
        val tempFile = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val inputStream = resolver.openInputStream(uri) ?: run {
                    AppLog.e("VKApiClient", "uploadAndSendDoc: cannot open input stream for $uri")
                    return@withContext null
                }
                // Имя файла: lastPathSegment у content:// часто URL-encoded
                // (например "primary%3A_logs%2Fpinok_logs_123.txt"). Декодируем.
                val rawName = uri.lastPathSegment ?: "file"
                val fileName = try {
                    java.net.URLDecoder.decode(rawName, "UTF-8")
                } catch (_: Exception) {
                    rawName
                }.takeLast(80) // защита от слишком длинных имён
                val file = java.io.File(context.cacheDir, "share_${System.currentTimeMillis()}_$fileName")
                inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                AppLog.i("VKApiClient", "uploadAndSendDoc: copied ${file.length()} bytes → ${file.name}")
                file
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "uploadAndSendDoc: copy uri→file error", e)
                null
            }
        } ?: return false

        try {
            // Шаги 2-4: docs.getMessagesUploadServer → upload → docs.save.
            val attachment = uploadDocForMessage(tempFile, mimeType) ?: run {
                AppLog.e("VKApiClient", "uploadAndSendDoc: uploadDocForMessage failed for ${tempFile.name} (mime=$mimeType)")
                return false
            }
            // Шаг 5: messages.send с attachment. Текст-подпись обрезаем под лимит.
            AppLog.i("VKApiClient", "uploadAndSendDoc: attachment=$attachment, sending to peer=$peerId")
            val caption = if (message.length > 4000) message.take(4000) else message
            val result = messagesSend(peerId = peerId, message = caption, attachment = attachment)
            return result > 0
        } finally {
            // Шаг 6: чистим временный файл независимо от результата.
            tempFile.delete()
        }
    }

    /**
     * Загрузить фото (multipart POST) на upload_url, полученный от
     * `photosGetWallUploadServer`. VK возвращает `{server, photo, hash}`.
     *
     * Читает файл из [uri] через ContentResolver, определяет MIME-тип.
     * Возвращает `UploadedPhoto(server, photo, hash)` или null при ошибке.
     */
    suspend fun photosUploadWallPhoto(uploadUrl: String, uri: Uri): UploadedPhoto? {
        if (isOffline()) return null
        return withContext(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        AppLog.e("VKApiClient", "photosUploadWallPhoto: cannot open input stream for $uri")
                        return@withContext null
                    }
                val mediaType = mimeType.toMediaType()
                val requestBody = bytes.toRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo", "photo.jpg", requestBody)
                    .build()
                val req = Request.Builder().url(uploadUrl).post(multipart).build()
                httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.e("VKApiClient", "photosUploadWallPhoto HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject
                val server = json.get("server")?.takeIf { !it.isJsonNull }?.asInt ?: -1
                // VK возвращает photo как JSON-encoded строку (с экранированными кавычками).
                // photos.saveWallPhoto принимает её как есть.
                val photo = json.get("photo")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val hash = json.get("hash")?.takeIf { !it.isJsonNull }?.asString ?: ""
                if (photo.isEmpty() || hash.isEmpty()) {
                    AppLog.e("VKApiClient", "photosUploadWallPhoto: empty photo/hash in response: $body")
                    return@withContext null
                }
                UploadedPhoto(server = server, photo = photo, hash = hash)
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "photosUploadWallPhoto failed", e)
                null
            }
        }
    }

    /**
     * Fix #233 (photo-send): перегрузка [photosUploadWallPhoto] для java.io.File.
     * Используется из [uploadPhotoForMessage] когда UI уже скопировало Uri в
     * временный File (ChatDetailScreen pendingFile). MIME-тип передаётся явно,
     * расширение filename выводится из него (photo.jpg/png/gif/webp) — ранее
     * Uri-версия хардкодила "photo.jpg" для всех форматов.
     *
     * Fix #235 (retry): VK upload-сервер (impf.ru) периодически отдаёт HTTP 504
     * (gateway timeout) или `{"photo":"","hash":"..."}` (пустой photo) под
     * нагрузкой — это ТРАНЗИТНЫЕ сбои, не ошибка клиента. Раньше один сбой =
     * фото терялось (в логе: «отправка не всегда срабатывает»). Теперь
     * ретраимся до 3 попыток с backoff 500ms / 1000ms. 4xx (кроме 408/429) —
     * не ретраим (клиентская ошибка, повтор не поможет).
     */
    suspend fun photosUploadWallPhoto(uploadUrl: String, file: java.io.File, mime: String?): UploadedPhoto? {
        if (isOffline()) return null
        return withContext(Dispatchers.IO) {
            val mimeType = mime ?: "image/jpeg"
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "photosUploadWallPhoto(file): cannot read ${file.name}", e)
                return@withContext null
            }
            if (bytes.isEmpty()) {
                AppLog.e("VKApiClient", "photosUploadWallPhoto(file): empty file ${file.name}")
                return@withContext null
            }
            val mediaType = mimeType.toMediaType()
            val requestBody = bytes.toRequestBody(mediaType)
            val ext = when (mimeType.lowercase()) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/bmp" -> "bmp"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", "photo.$ext", requestBody)
                .build()
            // Fix #235: до 3 попыток. backoff: 500ms, 1000ms.
            val maxAttempts = 3
            var lastError: String = "unknown"
            for (attempt in 1..maxAttempts) {
                try {
                    val req = Request.Builder().url(uploadUrl).post(multipart).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastError = "HTTP ${resp.code}"
                            // 4xx (кроме 408 Request Timeout / 429 Too Many Requests) —
                            // клиентская ошибка, ретрай бессмысленен.
                            val clientError = resp.code in 400..499 && resp.code != 408 && resp.code != 429
                            if (clientError) {
                                AppLog.e("VKApiClient", "photosUploadWallPhoto(file) HTTP ${resp.code} — client error, no retry")
                                return@withContext null
                            }
                            AppLog.w("VKApiClient", "photosUploadWallPhoto(file) HTTP ${resp.code} (attempt $attempt/$maxAttempts) — will retry")
                            return@use
                        }
                        val body = resp.body?.string().orEmpty()
                        val json = JsonParser.parseString(body).asJsonObject
                        val server = json.get("server")?.takeIf { !it.isJsonNull }?.asInt ?: -1
                        val photo = json.get("photo")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val hash = json.get("hash")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        if (photo.isEmpty() || hash.isEmpty()) {
                            // VK отдаёт пустой photo под нагрузкой — транзитный сбой,
                            // ретрай имеет смысл (обычно 2-я попытка проходит).
                            lastError = "empty photo/hash: $body"
                            AppLog.w("VKApiClient", "photosUploadWallPhoto(file): empty photo/hash (attempt $attempt/$maxAttempts) — will retry")
                            return@use
                        }
                        if (attempt > 1) {
                            AppLog.i("VKApiClient", "photosUploadWallPhoto(file) succeeded on attempt $attempt/$maxAttempts")
                        }
                        return@withContext UploadedPhoto(server = server, photo = photo, hash = hash)
                    }
                } catch (e: java.io.IOException) {
                    lastError = "IOException: ${e.message}"
                    AppLog.w("VKApiClient", "photosUploadWallPhoto(file) IOException (attempt $attempt/$maxAttempts): ${e.message} — will retry")
                } catch (e: Exception) {
                    AppLog.e("VKApiClient", "photosUploadWallPhoto(file) failed (attempt $attempt)", e)
                    lastError = "Exception: ${e.message}"
                }
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(if (attempt == 1) 500L else 1000L)
                }
            }
            AppLog.e("VKApiClient", "photosUploadWallPhoto(file) exhausted $maxAttempts retries — $lastError")
            null
        }
    }

    /**
     * Сохранить загруженное фото на стену (после multipart upload).
     * VK: photos.saveWallPhoto — server, photo, hash, owner_id (optional for user wall).
     * Возвращает `Pair<photoId, ownerId>` или `Pair(-1L, -1L)` при ошибке.
     */
    suspend fun photosSaveWallPhoto(
        server: Int,
        photo: String,
        hash: String,
        ownerId: Long? = null,
    ): Pair<Long, Long> {
        if (isOffline()) return -1L to -1L
        val args = mutableMapOf(
            "server" to server.toString(),
            "photo" to photo,
            "hash" to hash,
        )
        if (ownerId != null && ownerId != 0L) args["owner_id"] = ownerId.toString()
        val json = call("photos.saveWallPhoto", args) ?: return -1L to -1L
        return try {
            val resp = json.getAsJsonArray("response") ?: return -1L to -1L
            val first = resp.firstOrNull()?.asJsonObject ?: return -1L to -1L
            val pid = first.get("id")?.asLong ?: -1L
            val oid = first.get("owner_id")?.asLong ?: -1L
            pid to oid
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosSaveWallPhoto parse error", e)
            -1L to -1L
        }
    }

    /**
     * Опубликовать пост с вложениями.
     * VK: wall.post — message, attachments, owner_id (optional), friends_only.
     *
     * @param attachments Строка в формате VK: `photo{ownerId}_{photoId}` (через запятую для нескольких).
     * @return id нового поста или -1 при ошибке.
     */
    suspend fun wallPostWithAttachments(
        message: String,
        attachments: String,
        ownerId: Long? = null,
        friendsOnly: Boolean = false,
        publishDate: Long? = null,
    ): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "message" to message,
            "attachments" to attachments,
        )
        if (ownerId != null && ownerId != 0L) args["owner_id"] = ownerId.toString()
        if (friendsOnly) args["friends_only"] = "1"
        if (publishDate != null && publishDate > 0) args["publish_date"] = publishDate.toString()
        val json = call("wall.post", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("id")?.asLong ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallPostWithAttachments parse error", e)
            -1L
        }
    }

    /**
     * Sprint 2, P1-4 (#91): High-level helper — загрузить фото и опубликовать пост с ним.
     *
     * Оркестрирует весь flow:
     * 1. `photos.getWallUploadServer` → upload_url
     * 2. multipart POST файла → (server, photo, hash)
     * 3. `photos.saveWallPhoto` → (photoId, ownerId)
     * 4. `wall.post(attachments="photo{ownerId}_{photoId}")` → postId
     *
     * @param message   Текст поста (может быть пустым если есть фото).
     * @param photoUri  Uri фото (content:// из photo picker или file://).
     * @param friendsOnly Только для друзей.
     * @return id нового поста или -1 при ошибке на любом этапе.
     */
    suspend fun uploadPhotoAndPost(
        message: String,
        photoUri: Uri,
        friendsOnly: Boolean = false,
    ): Long {
        AppLog.i("VKApiClient", "uploadPhotoAndPost: starting upload flow")
        // 1. Получаем upload_url.
        val uploadUrl = photosGetWallUploadServer()
        if (uploadUrl.isNullOrBlank()) {
            AppLog.w("VKApiClient", "uploadPhotoAndPost: getWallUploadServer failed")
            return -1L
        }
        // 2. Загружаем файл.
        val uploaded = photosUploadWallPhoto(uploadUrl, photoUri)
        if (uploaded == null) {
            AppLog.w("VKApiClient", "uploadPhotoAndPost: upload failed")
            return -1L
        }
        // 3. Сохраняем фото.
        val (photoId, photoOwnerId) = photosSaveWallPhoto(
            server = uploaded.server,
            photo = uploaded.photo,
            hash = uploaded.hash,
        )
        if (photoId <= 0 || photoOwnerId <= 0) {
            AppLog.w("VKApiClient", "uploadPhotoAndPost: saveWallPhoto failed")
            return -1L
        }
        // 4. Публикуем пост с attachments.
        val attachments = "photo${photoOwnerId}_$photoId"
        val postId = wallPostWithAttachments(message, attachments, friendsOnly = friendsOnly)
        AppLog.i("VKApiClient", "uploadPhotoAndPost: done, postId=$postId, photo=$attachments")
        return postId
    }

    /** Результат multipart-загрузки фото (шаг 2 из 3). */
    data class UploadedPhoto(
        val server: Int,
        val photo: String,  // JSON-encoded строка от VK
        val hash: String,
    )

    /**
     * Получить комментарии к посту. VK: wall.getComments — owner_id, post_id, count.
     *
     * Fix #234: теперь парсит ВСЕ поля через общий parseComment():
     *  - reply_to_user / reply_to_comment (раньше терялись → исчезал контекст
     *    «→ Имя» после reload)
     *  - attachments (фото/видео/аудио в комментариях)
     *  - parents_stack (цепочка предков)
     *  - thread.items (превью ветки, запрашивается через thread_items_count=10)
     *
     * @param sort "asc" (сначала старые) или "desc" (сначала новые). VK API 5.243.
     * @param threadItemsCount сколько превью ответов вернуть под каждым
     *   комментарием (0 = не запрашивать thread совсем).
     * @param commentId §37.12 #328: если не null — VK вернёт только ответы на этот
     *   комментарий (а не корневые комментарии поста). Используется для lazy-load
     *   полной ветки ответов при развёртывании «↓ N ответов» в PostDetailScreen.
     */
    suspend fun wallGetComments(
        ownerId: Long,
        postId: Long,
        count: Int = 30,
        offset: Int = 0,
        sort: String = "asc",
        threadItemsCount: Int = 10,
        commentId: Long? = null,
    ): CommentsResult {
        if (isOffline()) return CommentsResult(emptyList(), emptyMap())
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            "fields" to "photo_100,online,verified",
            "sort" to sort,
            // Fix #234: просим VK вернуть превью ветки ответов под каждым
            // комментарием. Без этого параметра поле thread не приходит совсем,
            // и UI не может показать «N ответов» под комментарием.
            "thread_items_count" to threadItemsCount.toString(),
        )
        // §37.12 #328: comment_id — fetch ответов на конкретный комментарий.
        if (commentId != null) args["comment_id"] = commentId.toString()
        val json = call("wall.getComments", args)
            ?: return CommentsResult(emptyList(), emptyMap())
        return try {
            val resp = json.getAsJsonObject("response") ?: return CommentsResult(emptyList(), emptyMap())
            val items = resp.getAsJsonArray("items") ?: return CommentsResult(emptyList(), emptyMap())
            val profilesArr = resp.getAsJsonArray("profiles")
            val profiles = mutableMapOf<Long, UserProfile>()
            if (profilesArr != null) {
                for (el in profilesArr) {
                    if (!el.isJsonObject) continue
                    val o = el.asJsonObject
                    val uid = o.get("id")?.asLong ?: continue
                    profiles[uid] = parseUserProfileMini(o)
                }
            }
            // Fix #234: используем общий parseComment (парсит reply_*, attachments,
            // parents_stack, thread). Раньше был inline-парсер, который терял
            // ВСЕ поля кроме id/from_id/date/text/likes — это причина бага
            // «исчезающий → Имя после reload».
            val comments = items.mapNotNull { el ->
                if (!el.isJsonObject) null else parseComment(el.asJsonObject)
            }
            CommentsResult(comments, profiles)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallGetComments parse error", e)
            CommentsResult(emptyList(), emptyMap())
        }
    }

    /** Результат запроса комментариев: список + профили авторов. */
    data class CommentsResult(
        val comments: List<Comment>,
        val profiles: Map<Long, UserProfile>,
    )

    /**
     * Получить заявки в друзья. VK: friends.getRequests — count, offset, extended=1.
     * Возвращает список пользователей, которые хотят добавить вас в друзья.
     */
    suspend fun friendsGetRequests(count: Int = 50, offset: Int = 0): List<Friend> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            "fields" to "photo_100,photo_200,online,last_seen,sex,city,verified",
        )
        val json = call("friends.getRequests", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            // extended=1 возвращает {count, items:[{...user...}]}
            // без extended — {count, items:[uid,uid,...]}. Мы запросили extended.
            val items = resp.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Friend(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    firstName = o.get("first_name")?.asString ?: "",
                    lastName = o.get("last_name")?.asString ?: "",
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    online = o.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    lastSeen = o.get("last_seen")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                        UserProfile.LastSeen(
                            time = it.get("time")?.asLong ?: 0L,
                            platform = it.get("platform")?.takeIf { x -> !x.isJsonNull }?.asInt,
                        )
                    },
                    sex = o.get("sex")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    city = o.get("city")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                        UserProfile.City(it.get("title")?.asString ?: "")
                    },
                    verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "friendsGetRequests parse error", e)
            emptyList()
        }
    }

    /**
     * P4.4: `execute` — отправляет VKScript на единый endpoint VK API.
     *
     * VK `execute` позволяет объединить до 25 методов в один HTTP round-trip.
     * Скрипт пишется на VKScript (JS-подобный синтаксис):
     * ```
     * var msg = API.messages.getById({message_ids: 123});
     * var user = API.users.get({user_ids: msg.items[0].user_id});
     * return { message: msg, user: user };
     * ```
     *
     * ⚠️ Ограничения (см. VK_IMPORT_API.MD §23.6 `executeUnsupportedMethods`):
     *  - `photos.save*` (включая `saveMessagesPhoto`)
     *  - `docs.save`
     *  - `audio.save`
     *  - `messages.setChatPhoto`
     *  - `stories.save`, `polls.savePhoto`
     * Эти методы НЕ могут быть batch'ены — только отдельными HTTP-вызовами.
     *
     * Реальная польза: batch для GET+SEND последовательностей (например,
     * получить диалог + профили участников одним запросом вместо 2-3).
     *
     * Возвращает `response` объект (без обёртки) или null при ошибке.
     * Если VK вернул `execute_errors` — они логируются, но метод не падает
     * (частичная успешность возможна в execute).
     */
    suspend fun execute(script: String): JsonObject? {
        if (script.isBlank()) {
            AppLog.execute(stage = "script-empty")
            return null
        }
        // Логируем запрос: длина + первые 80 символов (privacy — полный скрипт не логируем,
        // может содержать peer_id, message text и т.д.).
        AppLog.execute(
            stage = "request",
            scriptLength = script.length,
            scriptPreview = script,
        )
        val args = mapOf("code" to script)
        val startMs = System.nanoTime()
        val json = call("execute", args, skipOffline = true)
        val durationMs = (System.nanoTime() - startMs) / 1_000_000
        if (json == null) {
            AppLog.execute(stage = "response-err", scriptLength = script.length,
                durationMs = durationMs, error = RuntimeException("call returned null"))
            return null
        }
        // Логируем execute_errors (частичный fail) — но возвращаем response,
        // потому что часть вызовов в script могла успеть.
        val execErrors = json.getAsJsonArray("execute_errors")
        val execErrorsCount = execErrors?.size() ?: 0
        if (execErrorsCount > 0) {
            AppLog.execute(stage = "response-partial", scriptLength = script.length,
                executeErrorsCount = execErrorsCount, durationMs = durationMs)
            // Дополнительно: логируем первые 3 execute_errors для debugging
            val limit = minOf(3, execErrorsCount)
            for (i in 0 until limit) {
                val err = execErrors[i]?.toString()?.take(200) ?: continue
                AppLog.w("VKApiClient", "execute_errors[$i]: $err")
            }
        } else {
            val bodySize = json.toString().length
            AppLog.execute(stage = "response-ok", scriptLength = script.length,
                durationMs = durationMs, bodySize = bodySize)
        }
        return try {
            json.getAsJsonObject("response")
                ?: json // если response нет — вернём весь json (для error handling caller'ом)
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "execute: response is not an object: ${json.toString().take(200)}")
            json
        }
    }

    /**
     * P4.4: пример batch'а — получить несколько диалогов с профилями одним execute.
     *
     * Вместо N отдельных вызовов `messages.getConversationsById` + `users.get` +
     * `groups.get` — один execute, возвращающий всё сразу. Экономия: 2 round-trip'а
     * на каждый диалог (при количестве участников > 1).
     *
     * VKScript:
     * ```
     * var convs = API.messages.getConversationsById({peer_ids: "1,2,3", extended: 1});
     * return convs;  // уже включает profiles + groups (extended=1)
     * ```
     *
     * ⚠️ [peerIds] лимит: 100 peer за один вызов (VK API limit).
     *
     * @return JsonObject ответа `messages.getConversationsById` (с items, profiles, groups),
     *         или null при ошибке.
     */
    suspend fun executeGetConversationsBatch(peerIds: List<Long>): JsonObject? {
        if (peerIds.isEmpty()) return null
        val peerIdsStr = peerIds.joinToString(",")
        // VKScript: один вызов getConversationsById с extended=1 возвращает
        // items + profiles + groups — это уже batch на уровне API.
        val script = """
            var convs = API.messages.getConversationsById({peer_ids: "$peerIdsStr", extended: 1});
            return convs;
        """.trimIndent()
        return execute(script)
    }

    /**
     * Получить LongPoll-сервер для realtime-обновлений сообщений.
     * VK: messages.getLongPollServer — need_pts=1, lp_version=<3|14>.
     *
     * P4.1: [lpVersion] по умолчанию 3 (как раньше). Если caller передаёт 14 —
     * VK вернёт расширенный формат ответа (attachments в NewMessage, дополнительные
     * поля). Парсер [re.pinok.realtime.LongPollClient.handleEvent] совместим с обоими
     * версиями обратно — v14 добавляет поля в конец массивов, базовые индексы те же.
     *
     * Audit #40: lp_version=3 (раньше был 2) — должен совпадать с version=3,
     * который LongPollClient отправляет в URL опроса. Несовпадение версий
     * приводило к failed=4 (version outdated) и постоянному переподключению.
     * ExchangeAuthApi.prefetch использует lp_version=4 — но это для префетча,
     * не для активного поллинга.
     *
     * Возвращает [LongPollServer] или null при ошибке.
     */
    suspend fun messagesGetLongPollServer(lpVersion: Int = 3): LongPollServer? {
        // Fix #99: НЕ проверяем isOffline() — LongPoll должен всегда пытаться
        // переподключиться, иначе при кратковременной потере сети
        // приложение «теряет» соединение навсегда.
        val args = mapOf(
            "need_pts" to "1",
            "lp_version" to lpVersion.toString(),
        )
        val json = call("messages.getLongPollServer", args, skipOffline = true) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            LongPollServer(
                server = resp.get("server")?.asString ?: "",
                key = resp.get("key")?.asString ?: "",
                ts = resp.get("ts")?.asLong ?: 0L,
                pts = resp.get("pts")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetLongPollServer parse error", e)
            null
        }
    }

    /** Параметры LongPoll-сервера для polling-обновлений сообщений. */
    data class LongPollServer(
        val server: String,
        val key: String,
        val ts: Long,
        val pts: Long,
    )

    /**
     * §52.5 Sprint A (P0): Modern Sync API — messages.getDiff (lp_version=21).
     *
     * Возвращает LongPoll-credentials, папки и счётчики одним запросом — заменяет
     * связку getConversations + getChatFolders + getCounters + getLongPollServer.
     * Ответ (VK_IMPORT_API.MD §35.1.1):
     *   {server_version, credentials{key,ts,server_lp}, counters{...},
     *    folders{count,items[]}, invalidate_all}
     *
     * server_lp = "api.vk.ru/ruim<user_id>" — НЕ lp.vk.com. LongPoll-опрос идёт по
     * `https://{server_lp}?act=a_check&key=...&ts=...&version=21&wait=25&mode=1226`
     * (те же коды событий 4/5/6/7/8/9/61/62/80, что и v3/v14 — §35.3.2).
     *
     * Возвращает [MessagesDiff] или null при ошибке (сеть, error code, parse).
     */
    suspend fun messagesGetDiff(): MessagesDiff? {
        val args = mapOf(
            "lp_version" to "21",
            "conversations_limit" to "0",
            "extended_filters" to "credentials,server_version,profiles,contacts,groups,messages,counters,folders,folders_with_peers",
            "group_id" to "0",
            "counter_filters" to "all",
            "supported_types" to "channels,business,personal,unread,managed_groups",
            "fields" to "photo_100,photo_200,online,last_seen,screen_name",
        )
        val json = call("messages.getDiff", args, skipOffline = true) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: run {
                AppLog.w("VKApiClient", "messagesGetDiff: no 'response' field | json=${json.toString().take(300)}")
                return null
            }
            val cred = resp.getAsJsonObject("credentials")
            val counters = resp.getAsJsonObject("counters")
            val folders = resp.getAsJsonObject("folders")?.getAsJsonArray("items")
                ?.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val o = el.asJsonObject
                    MessagesDiff.Folder(
                        id = o.get("id")?.asInt ?: 0,
                        name = o.get("name")?.asString ?: "",
                        type = o.get("type")?.asString ?: "",
                        flags = o.get("flags")?.asInt ?: 0,
                    )
                } ?: emptyList()
            val diff = MessagesDiff(
                key = cred?.get("key")?.asString ?: "",
                ts = cred?.get("ts")?.asLong ?: 0L,
                serverLp = cred?.get("server_lp")?.asString ?: "",
                serverVersion = resp.get("server_version")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                invalidateAll = resp.get("invalidate_all")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                countersMessages = counters?.get("messages")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                countersUnreadUnmuted = counters?.get("messages_unread_unmuted")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                folders = folders,
            )
            AppLog.i("VKApiClient", "messagesGetDiff ok: key=${if (diff.key.isNotBlank()) "yes(${diff.key.take(8)}…)" else "NO"} " +
                "ts=${diff.ts} server_lp=${diff.serverLp.take(40)} server_version=${diff.serverVersion} " +
                "invalidate_all=${diff.invalidateAll} counters=${diff.countersMessages}/${diff.countersUnreadUnmuted} " +
                "folders=${diff.folders.size}")
            diff
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetDiff parse error", e)
            null
        }
    }

    /** Результат messages.getDiff — Modern Sync API (lp_version=21). */
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

    /**
     * §52.5 Sprint A (P0): Modern Sync API — messages.getItems (пагинация диалогов).
     *
     * Курсорный список диалогов: совмещает диалоги и каналы в одном запросе.
     * Ответ (VK_IMPORT_API.MD §35.1.2):
     *   {conversations:{items:[{conversation, last_message}]}, total_count,
     *    channels:{items:[]}, profiles, groups}
     *
     * Курсор пагинации — start_from="conversations_X,channels_X_Y" (возвращается
     * в поле next_from ответа).
     *
     * @param startFrom курсор из предыдущего ответа (null = первая страница)
     * @param targetCount сколько диалогов запросить
     */
    suspend fun messagesGetItems(
        startFrom: String? = null,
        targetCount: Int = 20,
    ): MessagesItems? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "filter" to "all",
            // §35.1.2: start_from обязателен — первая страница "conversations_0,channels_0_0".
            "start_from" to (startFrom ?: "conversations_0,channels_0_0"),
            "extended" to "1",
            // target_count максимум 100 (VK возвращает err=100 иначе).
            "target_count" to targetCount.coerceIn(1, 100).toString(),
            "group_id" to "0",
            "fields" to "photo_100,photo_200,online,last_seen,screen_name",
        )
        val json = call("messages.getItems", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val maps = parsePeerMaps(resp)
            val itemsArr = resp.getAsJsonObject("conversations")?.getAsJsonArray("items")
            val chats = itemsArr?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseConversationItem(el.asJsonObject, maps)
            } ?: emptyList()
            // #MODERN-SYNC: каналы — отдельная структура channels.items[] (channel + last_message).
            // Мерджим в общий список (UI сам сортирует по lastMessage.date DESC).
            val channelsArr = resp.getAsJsonObject("channels")?.getAsJsonArray("items")
            val channelChats = channelsArr?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseChannelItem(el.asJsonObject)
            } ?: emptyList()
            val allChats = (chats + channelChats)
                .distinctBy { it.peer.id }
            // #MODERN-SYNC-CURSOR (2026-08-18): курсор разгадан live-пробами.
            //   conversations_{cmid} — cmid (conversation_message_id) последнего
            //     (самого старого) conversation в странице.
            //   channels_{minor_id} — sort_id.minor_id последнего канала.
            // Пробы: channels_6 → 0; channels_1786525083 → 4 (остаток);
            //   conversations_191716 → 16 chats. Формат подтверждён.
            val nextFrom = buildString {
                val lastConvCmid = chats.lastOrNull()?.lastMessage?.conversationMessageId ?: 0L
                append("conversations_").append(lastConvCmid)
                val lastChannelMinor = channelChats.lastOrNull()?.sortId?.minorId ?: 0L
                append(",channels_").append(lastChannelMinor)
            }
            // total_count лежит ВНУТРИ conversations (не на top-level).
            val totalCount = resp.getAsJsonObject("conversations")
                ?.get("total_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            AppLog.i("VKApiClient", "messagesGetItems ok: chats=${chats.size} channels=${channelChats.size} total=$totalCount next=$nextFrom")
            MessagesItems(allChats, chats.size, channelChats.size, totalCount, nextFrom)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetItems parse error", e)
            null
        }
    }

    /**
     * #MODERN-SYNC-CURSOR (2026-08-18): собрать ВСЕ каналы через messages.getItems.
     *
     * legacy getConversations возвращает только часть каналов (2 из 10 в live-тесте),
     * а getItems отдаёт их все курсорной пагинацией channels_{minor_id}.
     * Итерируем, пока channelsCount > 0.
     */
    suspend fun messagesGetAllChannels(maxPages: Int = 20): List<Chat> {
        if (isOffline()) return emptyList()
        val result = ArrayList<Chat>()
        var cursor: String? = null
        var pages = 0
        while (pages < maxPages) {
            val page = messagesGetItems(startFrom = cursor, targetCount = 100) ?: break
            if (page.chats.isEmpty()) break
            result.addAll(page.chats.filter { it.isChannel })
            if (page.channelsCount == 0) break
            cursor = page.nextFrom
            pages++
        }
        val distinct = result.distinctBy { it.peer.id }
        AppLog.i("VKApiClient", "messagesGetAllChannels: ${distinct.size} channels in $pages pages")
        return distinct
    }

    /** Результат messages.getItems — пагинированный список диалогов. */
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

    /**
     * §52.5 Sprint A (P0): Modern Sync API — messages.getConfig (конфиг v17).
     *
     * Возвращает серверную конфигурацию мессенджера. Нужен для полной
     * Modern Sync триады (getDiff + getItems + getConfig). Парсим минимально —
     * version + сырой config-объект (для будущих настроек UI).
     */
    suspend fun messagesGetConfig(): MessagesConfig? {
        if (isOffline()) return null
        val json = call("messages.getConfig", emptyMap()) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val cfg = MessagesConfig(
                version = resp.get("version")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            )
            AppLog.i("VKApiClient", "messagesGetConfig ok: version=${cfg.version}")
            cfg
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetConfig parse error", e)
            null
        }
    }

    /** Результат messages.getConfig — конфиг мессенджера (v17). */
    data class MessagesConfig(
        val version: Int,
    )

    /**
     * P4.2: messages.getLongPollHistory — получить пропущенные события
     * между двумя сессиями LongPoll (или после failed=1 history outdated).
     *
     * VK API: `messages.getLongPollHistory` с параметрами:
     *  - `ts`: последний известный ts (из [LongPollServer.ts])
     *  - `pts`: последний известный pts (из [LongPollServer.pts])
     *  - `preview_length`: 0 (без обрезки текста)
     *  - `fields`: профильные поля для profiles/groups (например "photo_100,online")
     *  - `events_limit`: 1000 (макс событий в ответе)
     *  - `msgs_limit`: 200 (макс сообщений в ответе)
     *  - `lp_version`: 3 (должно совпадать с [messagesGetLongPollServer])
     *
     * Формат ответа:
     * ```
     * { "response": {
     *     "history": [[<event_code>, ...], ...],  // те же события что и в LP updates[]
     *     "messages": { count, items },
     *     "conversations": [...],
     *     "profiles": [...],
     *     "groups": [...],
     *     "new_pts": <Long>  // обновлённый pts для следующего backfill
     * }}
     * ```
     *
     * [LongPollHistory.history] — это сырые JsonArray события (как в LP `updates[]`),
     * которые [re.pinok.realtime.LongPollClient.handleEvent] умеет парсить без изменений.
     *
     * Возвращает null при ошибке API (network, error code, parse).
     */
    suspend fun messagesGetLongPollHistory(
        pts: Long,
        ts: Long,
        fields: String = "photo_100,online,screen_name",
        eventsLimit: Int = 1000,
        msgsLimit: Int = 200,
    ): LongPollHistory? {
        if (pts <= 0L || ts <= 0L) {
            AppLog.w("VKApiClient", "getLongPollHistory: invalid pts=$pts or ts=$ts")
            return null
        }
        val args = mapOf(
            "ts" to ts.toString(),
            "pts" to pts.toString(),
            // FIX #354: preview_length deprecated с VK API 5.217.
            // Лог показывал: "API error 100: preview_length is deprecated
            // from version 5.217 (method=messages.getLongPollHistory)".
            // Приложение использует VK API 5.269 → параметр убран.
            // Раньше "0" означал "без обрезки текста" — без параметра VK
            // возвращает полный текст по умолчанию, поведение сохранено.
            "fields" to fields,
            "events_limit" to eventsLimit.toString(),
            "msgs_limit" to msgsLimit.toString(),
            "lp_version" to "3",
        )
        val startMs = System.nanoTime()
        val json = call("messages.getLongPollHistory", args, skipOffline = true)
        val durationMs = (System.nanoTime() - startMs) / 1_000_000
        if (json == null) {
            AppLog.backfill(stage = "fetch-null", savedPts = pts, durationMs = durationMs)
            return null
        }
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val historyArr = resp.getAsJsonArray("history")
            val history = mutableListOf<JsonArray>()
            if (historyArr != null) {
                for (el in historyArr) {
                    if (el.isJsonArray) history.add(el.asJsonArray)
                }
            }
            val newPts = resp.get("new_pts")?.takeIf { !it.isJsonNull }?.asLong ?: pts
            val messagesCount = resp.getAsJsonObject("messages")?.get("count")?.takeIf { !it.isJsonNull }?.asInt
                ?: history.size
            val conversationsCount = resp.getAsJsonArray("conversations")?.size() ?: 0
            val bodySize = resp.toString().length
            AppLog.backfill(stage = "fetch-ok", savedPts = pts, currentPts = newPts,
                eventsCount = history.size, messagesCount = messagesCount,
                conversationsCount = conversationsCount, durationMs = durationMs)
            AppLog.d("VKApiClient", "getLongPollHistory response bodySize=$bodySize bytes")
            LongPollHistory(
                history = history,
                newPts = newPts,
                newTs = ts, // VK не возвращает new_ts; для backfill ts остаётся прежним
                messagesCount = messagesCount,
                conversationsCount = conversationsCount,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetLongPollHistory parse error", e)
            AppLog.backfill(stage = "fetch-parse-error", savedPts = pts, error = e)
            null
        }
    }

    /**
     * P4.2: результат messages.getLongPollHistory — пропущенные между сессиями события.
     *
     * [history] — сырые события (тот же формат что и в LongPoll `updates[]`),
     * обрабатываются [re.pinok.realtime.LongPollClient.handleEvent] без изменений.
     * [newPts] — обновлённый pts для следующего backfill (сохранить в SovaPrefs.lpLastPts).
     */
    data class LongPollHistory(
        val history: List<JsonArray>,
        val newPts: Long,
        val newTs: Long,
        val messagesCount: Int,
        val conversationsCount: Int,
    )

    /**
     * Базовый профиль пользователя (короткий набор полей).
     * Audit #40: Делегирует к расширенной версии usersGetFullExtended (70+ полей).
     * Раньше здесь была отдельная укороченная реализация — но UI (UserProfileScreen)
     * нужен был расширенный профиль, а Kotlin-разрешение перегрузок вызывало
     * базовую (10 полей) вместо расширенной (70+ полей).
     */
    suspend fun usersGetFull(userId: Long): UserProfile? = usersGetFullExtended(userId)

    /**
     * Fix #128: Batch users.get — получить профили нескольких пользователей одним
     * запросом. Используется для резолва имён/аватарок в списке диалогов, когда
     * messages.getConversations не вернул профиль в profiles[] (удалённые/заблокиро-
     * ванные пользователи).
     *
     * Возвращает Map<userId, UserProfile>.
     */
    override suspend fun usersGetByIds(userIds: List<Long>): Map<Long, UserProfile> {
        if (isOffline() || userIds.isEmpty()) return emptyMap()
        val args = mutableMapOf(
            "user_ids" to userIds.joinToString(",") { it.toString() },
            "fields" to "photo_100,photo_200,online,last_seen,verified",
        )
        val json = call("users.get", args) ?: return emptyMap()
        return try {
            val arr = json.getAsJsonArray("response") ?: return emptyMap()
            val result = mutableMapOf<Long, UserProfile>()
            arr.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val obj = el.asJsonObject
                val uid = obj.get("id")?.asLong ?: return@forEach
                // VK помечает удалённые/заблокированные пользователей как
                // "deactivated": "deleted" или "banned". Для таких всё равно
                // берём first_name/last_name (VK отдаёт "DELETED" / "Имя
                // недоступно"), чтобы в списке диалогов было хоть что-то вместо
                // «Диалог».
                val first = obj.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val last = obj.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                result[uid] = UserProfile(
                    id = uid,
                    firstName = first,
                    lastName = last,
                    photo100 = obj.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = obj.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    online = obj.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    verified = obj.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGetByIds parse error", e)
            emptyMap()
        }
    }

    /**
     * Получить уведомления. VK: notifications.get — count, offset.
     * Возвращает пару: список уведомлений + next_from для пагинации.
     */
    suspend fun notificationsGet(
        count: Int = 30,
        offset: Int = 0,
        startFrom: String? = null,
    ): Pair<List<NotificationItem>, String?> {
        if (isOffline()) return emptyList<NotificationItem>() to null
        // Fix #248: для web-токенов (vk1.a.*) VK всегда отдаёт error 3
        // (Unknown method) на notifications.get — метод требует scope
        // notifications, которого у веб-токенов нет. Сразу идём в
        // notifications.getRedesign — он доступен для тех же токенов и
        // отдаёт тот же формат данных. Экономит ~150мс на каждом вызове
        // (первичная загрузка + pull-to-refresh + пагинация) и убирает
        // спам error-логов «API error 3: Unknown method».
        if (VkSigner.isWebToken(token())) {
            return notificationsGetRedesign(count = count, startFrom = startFrom)
        }
        val args = mutableMapOf(
            "count" to count.toString(),
            "extended" to "1",  // profiles + groups
        )
        if (offset > 0) args["offset"] = offset.toString()
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        // Для НЕ-web-токенов (Kate/Direct Auth) notifications.get может
        // работать — но если вдруг вернётся error 3, fallback на
        // getRedesign (см. выше). lastApiErrorCode проверяем ПОСЛЕ call()
        // — call() сам выставит его в error-path.
        val json = call("notifications.get", args)
        if (json == null) {
            if (lastApiErrorCode == 3) {
                AppLog.w("VKApiClient", "notifications.get: error 3 (Unknown method) — fallback to notifications.getRedesign")
                return notificationsGetRedesign(count = count, startFrom = startFrom)
            }
            return emptyList<NotificationItem>() to null
        }
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList<NotificationItem>() to null
            val nextFrom = resp.get("next_from")?.asString
            val items = resp.getAsJsonArray("items") ?: return emptyList<NotificationItem>() to nextFrom

            // Парсим profiles и groups в lookup-карты
            val profilesMap = mutableMapOf<Long, NotificationProfile>()
            resp.getAsJsonArray("profiles")?.let { arr ->
                for (el in arr) {
                    if (!el.isJsonObject) continue
                    val p = el.asJsonObject
                    val id = p.get("id")?.asLong ?: continue
                    profilesMap[id] = NotificationProfile(
                        id = id,
                        name = (p.get("first_name")?.asString ?: "") + " " +
                               (p.get("last_name")?.asString ?: ""),
                        photo100 = p.get("photo_100")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        photo200 = p.get("photo_200")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        isGroup = false,
                    )
                }
            }
            resp.getAsJsonArray("groups")?.let { arr ->
                for (el in arr) {
                    if (!el.isJsonObject) continue
                    val g = el.asJsonObject
                    val id = -(g.get("id")?.asLong ?: continue)  // groups have negative IDs
                    profilesMap[id] = NotificationProfile(
                        id = id,
                        name = g.get("name")?.asString ?: "",
                        photo100 = g.get("photo_100")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        photo200 = g.get("photo_200")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        isGroup = true,
                    )
                }
            }

            val parsed = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseNotificationItem(el.asJsonObject, profilesMap)
            }
            parsed to nextFrom
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "notificationsGet parse error", e)
            emptyList<NotificationItem>() to null
        }
    }

    /** Отметить все уведомления как прочитанные. */
    suspend fun notificationsMarkAsRead(): Boolean {
        if (isOffline()) return false
        return try {
            val json = call("notifications.markAsRead", emptyMap())
            val resp = json?.getAsJsonObject("response")
            resp?.get("state")?.asInt == 1
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "notificationsMarkAsRead failed: ${e.message}")
            false
        }
    }

    /** #75: notifications.markAsViewed — отметить уведомление просмотренным. */
    suspend fun notificationsMarkAsViewed(): Boolean {
        if (isOffline()) return false
        val json = call("notifications.markAsViewed", emptyMap()) ?: return false
        return json.has("response")
    }

    /** Парсинг одного элемента уведомления из JSON. */
    private fun parseNotificationItem(
        o: JsonObject,
        profilesMap: Map<Long, NotificationProfile>,
    ): NotificationItem? {
        val type = o.get("type")?.asString ?: return null
        val date = o.get("date")?.asLong ?: 0L

        // --- feedback: кто совершил действие ---
        val feedbackIds = mutableListOf<Long>()
        val fbEl = o.get("feedback")
        if (fbEl != null) {
            if (fbEl.isJsonObject) {
                fbEl.asJsonObject.get("from_id")?.asLong?.let { feedbackIds.add(it) }
            } else if (fbEl.isJsonArray) {
                for (f in fbEl.asJsonArray) {
                    if (f.isJsonObject) {
                        f.asJsonObject.get("from_id")?.asLong?.let { feedbackIds.add(it) }
                    }
                }
            }
        }

        // --- parent: объект к которому относится уведомление ---
        val parentEl = o.get("parent")
        var parentType = ""
        var parentOwnerId = 0L
        var parentItemId = 0L
        var parentText = ""
        var parentPhotoUrl: String? = null
        var parentVideoThumb: String? = null
        // §42.4 #PUSH-DEEPLINK: см. комментарий ниже (parentType=="comment").
        var parentCommentId = 0L
        var parentUrl: String? = null
        // #29: полный список вложений для горизонтального скролла (§14.2)
        val attachments = mutableListOf<NotificationAttachment>()

        if (parentEl != null && parentEl.isJsonObject) {
            val p = parentEl.asJsonObject
            parentType = p.get("type")?.asString ?: ""
            parentOwnerId = p.get("owner_id")?.asLong ?: 0L
            // Fix #233 (Q&A Bug A): fallback на p.get("id") для случая когда
            // parent — сам пост (фильтр "Комментарии": VK возвращает parent с
            // type="post" и id=post_id, БЕЗ поля post_id). Ранее parentItemId=0
            // → в NotificationsScreen onPostClick пропускался → tap падал на
            // onUserClick(feedbackIds.first()) = профиль комментатора, а не пост.
            // Теперь id поста корректно извлекается.
            parentItemId = p.get("post_id")?.asLong
                ?: p.get("photo_id")?.asLong
                ?: p.get("video_id")?.asLong
                ?: p.get("topic_id")?.asLong
                ?: p.get("id")?.asLong
                ?: 0L
            parentText = p.get("text")?.takeIf { !it.isJsonNull }?.asString ?: ""
            // §42.4 #PUSH-DEEPLINK: для parentType=="comment" сохраняем
            // собственный id комментария (p.id) отдельно — parentItemId при
            // этом = post_id (пост, на котором оставлен комментарий). Это нужно
            // чтобы тап на уведомление «ответ на комментарий» скроллил к
            // конкретному комментарию, а не просто открывал пост.
            if (parentType.equals("comment", ignoreCase = true)) {
                parentCommentId = p.get("id")?.asLong ?: 0L
            }

            // Собираем ВСЕ вложения для LazyRow (раньше — только первое фото/видео)
            p.getAsJsonArray("attachments")?.let { atts ->
                for (att in atts) {
                    if (!att.isJsonObject) continue
                    val a = att.asJsonObject
                    val attType = a.get("type")?.asString ?: continue
                    when (attType) {
                        "photo" -> {
                            a.getAsJsonObject("photo")?.let { photo ->
                                val url = extractBestPhotoUrl(photo)
                                if (parentPhotoUrl == null && url != null) parentPhotoUrl = url
                                attachments.add(NotificationAttachment(
                                    type = "photo",
                                    thumbUrl = url,
                                    ownerId = photo.get("owner_id")?.asLong ?: parentOwnerId,
                                    itemId = photo.get("id")?.asLong ?: 0L,
                                    accessKey = photo.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                                ))
                            }
                        }
                        "video" -> {
                            a.getAsJsonObject("video")?.let { video ->
                                val thumb = video.get("photo_130")?.asString
                                    ?: video.get("photo_800")?.asString
                                if (parentVideoThumb == null && thumb != null) parentVideoThumb = thumb
                                attachments.add(NotificationAttachment(
                                    type = "video",
                                    thumbUrl = thumb,
                                    ownerId = video.get("owner_id")?.asLong ?: parentOwnerId,
                                    itemId = video.get("id")?.asLong ?: 0L,
                                    accessKey = video.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                                ))
                            }
                        }
                        "clip" -> {
                            a.getAsJsonObject("clip")?.let { clip ->
                                attachments.add(NotificationAttachment(
                                    type = "clip",
                                    thumbUrl = clip.get("photo_130")?.asString
                                        ?: clip.get("photo_800")?.asString,
                                    ownerId = clip.get("owner_id")?.asLong ?: parentOwnerId,
                                    itemId = clip.get("id")?.asLong ?: 0L,
                                    accessKey = clip.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                                ))
                            }
                        }
                        "gift" -> {
                            a.getAsJsonObject("gift")?.let { gift ->
                                attachments.add(NotificationAttachment(
                                    type = "gift",
                                    thumbUrl = gift.get("thumb_256")?.asString
                                        ?: gift.get("thumb_96")?.asString,
                                    ownerId = parentOwnerId,
                                    itemId = gift.get("id")?.asLong ?: 0L,
                                    accessKey = null,
                                ))
                            }
                        }
                    }
                }
            }
        }

        // #29: кнопки действий (notification-actions, §14.2)
        // Тип уведомления определяет какие действия доступны.
        val actions = mutableListOf<NotificationAction>()
        val targetUid = feedbackIds.firstOrNull() ?: 0L
        when (type) {
            "gift" -> {
                // «Подарить в ответ» — открывает диалог выбора подарка
                actions.add(NotificationAction(
                    label = "Подарить в ответ",
                    style = NotificationAction.ActionStyle.SECONDARY,
                    actionType = NotificationAction.ActionType.GIFT_REPLY,
                    targetUserId = targetUid,
                ))
            }
            "reply_comment", "comment", "mention", "mention_comments" -> {
                // «Ответить» — открывает ввод текста
                actions.add(NotificationAction(
                    label = "Ответить",
                    style = NotificationAction.ActionStyle.TERTIARY,
                    actionType = NotificationAction.ActionType.REPLY,
                    targetUserId = targetUid,
                ))
            }
            "follow", "friend_accepted", "friend_requested" -> {
                // Открыть профиль — есть в long-press menu, но дублируем кнопкой
                actions.add(NotificationAction(
                    label = "Открыть профиль",
                    style = NotificationAction.ActionStyle.TERTIARY,
                    actionType = NotificationAction.ActionType.OPEN_USER,
                    targetUserId = targetUid,
                ))
            }
        }

        // --- reply: ответ на комментарий (для reply_comment) ---
        val replyEl = o.get("reply")
        var replyText = ""
        var replyFromId = 0L
        var replyDate = 0L
        if (replyEl != null && replyEl.isJsonObject) {
            val r = replyEl.asJsonObject
            replyText = r.get("text")?.asString ?: ""
            replyFromId = r.get("from_id")?.asLong ?: 0L
            replyDate = r.get("date")?.asLong ?: 0L
        }

        // Резолвим профили из feedback
        val feedbackProfiles = feedbackIds.mapNotNull { profilesMap[it] }

        return NotificationItem(
            type = type,
            date = date,
            feedbackProfiles = feedbackProfiles,
            feedbackIds = feedbackIds,
            parentType = parentType,
            parentOwnerId = parentOwnerId,
            parentItemId = parentItemId,
            parentText = parentText,
            parentPhotoUrl = parentPhotoUrl,
            parentVideoThumb = parentVideoThumb,
            attachments = attachments,
            actions = actions,
            replyText = replyText,
            replyFromId = replyFromId,
            replyDate = replyDate,
            profilesMap = profilesMap,
            parentCommentId = parentCommentId,
            parentUrl = parentUrl,
            // legacy
            text = buildNotificationText(type, feedbackProfiles, parentText, replyText),
            parentId = parentItemId,
            parentOwnerIdLegacy = parentOwnerId,
        )
    }

    /** Извлечь лучший URL фото (приоритет: 600 > 130 > 75). */
    private fun extractBestPhotoUrl(photo: JsonObject): String? {
        return photo.get("photo_600")?.takeIf { !it.isJsonNull }?.asString
            ?: photo.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
            ?: photo.get("photo_75")?.takeIf { !it.isJsonNull }?.asString
    }

    /** Построить человекочитаемый текст уведомления. */
    private fun buildNotificationText(
        type: String,
        feedbackProfiles: List<NotificationProfile>,
        parentText: String,
        replyText: String,
    ): String {
        val names = feedbackProfiles.take(3).joinToString(", ") { it.name }
        val extra = if (feedbackProfiles.size > 3) " и ещё ${feedbackProfiles.size - 3}" else ""
        return when {
            type.startsWith("like_") -> {
                val what = when {
                    type.contains("post") -> "вашу запись"
                    type.contains("comment") -> "ваш комментарий"
                    type.contains("photo") -> "вашу фотографию"
                    type.contains("video") -> "ваше видео"
                    type.contains("topic") -> "вашу тему"
                    else -> "вашу запись"
                }
                "$names${extra} оценили$what"
            }
            type == "follow" -> "$names${if (extra.isEmpty()) "" else " и ещё кто-то"} подписались на вас"
            type == "friend_accepted" -> "$names принял(а) вашу заявку в друзья"
            type == "friend_requested" -> "$names хочет добавить вас в друзья"
            type == "comment" -> "$names прокомментировал(и) вашу запись"
            type == "reply_comment" -> "$names ответил(и) на ваш комментарий: ${replyText.take(80)}"
            type == "copy" -> "$names скопировал(и) вашу запись на свою стену"
            type == "wall" -> "$names написал(и) на вашей стене: ${parentText.take(80)}"
            type.startsWith("mention") -> "$names упомянул(и) вас"
            type == "birthday_reminder" -> "Сегодня день рождения у $names"
            else -> "Уведомление от $names"
        }
    }

    /** Профиль пользователя/группы для уведомлений (лёгкая модель). */
    data class NotificationProfile(
        val id: Long,
        val name: String,
        val photo100: String,
        val photo200: String,
        val isGroup: Boolean,
    )

    /** Одна запись уведомления (расширенная модель). */
    data class NotificationItem(
        val type: String,                        // like_post, like_comment, follow, mention, reply_comment, copy, wall, gift, ...
        val date: Long,
        val feedbackProfiles: List<NotificationProfile>,  // кто совершил действие
        val feedbackIds: List<Long>,              // ID действующих лиц
        val parentType: String,                   // post, photo, video, comment, topic
        val parentOwnerId: Long,
        val parentItemId: Long,                   // post_id, photo_id, video_id
        val parentText: String,                   // текст родительского поста
        val parentPhotoUrl: String?,              // превью фото из attachments (legacy, первое)
        val parentVideoThumb: String?,            // превью видео (legacy, первое)
        /**
         * #29 (закрытие хвостов): полный список вложений уведомления.
         *
         * Согласно VK_IMPORT_API.MD §14.2 (notification-attachments), уведомление
         * может содержать 0-N вложений: фото, видео, клипы. Раньше парсер брал
         * только первое фото и первое видео — теперь храним ВСЕ.
         *
         * UI использует это для LazyRow горизонтального скролла (§14.6 mapping).
         */
        val attachments: List<NotificationAttachment>,
        /**
         * #29: кнопки действий для уведомления (например, для gift — «Подарить в ответ»).
         * Согласно VK_IMPORT_API.MD §14.2 (notification-actions). Обычно 0-2 кнопки.
         */
        val actions: List<NotificationAction>,
        val replyText: String,                    // текст ответа (reply_comment)
        val replyFromId: Long,                    // кто ответил
        val replyDate: Long,                      // дата ответа
        val profilesMap: Map<Long, NotificationProfile>,  // все профили из ответа
        // §42.4 #PUSH-DEEPLINK: ID самого комментария (когда parent — comment).
        // parentItemId при этом = post_id (пост, содержащий комментарий), а
        // parentCommentId = id комментария, к которому надо скроллить при тапе
        // на уведомление «ответ на комментарий».
        val parentCommentId: Long = 0L,
        // §42.4 #PUSH-DEEPLINK: канонический VK URL из redesign action.entity.url
        // (например «https://vk.com/wall-123_456?reply=789»). Используется
        // VkUrlDeepLinker как наиболее надёжный источник для построения deep-link:
        // URL однозначно кодирует тип/owner/item/comment, в отличие от
        // type+post_id, который для комментариев неоднозначен.
        val parentUrl: String? = null,
        // legacy-поля для обратной совместимости
        val text: String,
        val parentId: Long,
        val parentOwnerIdLegacy: Long,
        /**
         * Fix #254: сырой id из redesign-формата (строка base64-подобная).
         * Для legacy-формата (notifications.get) — пустая строка.
         * Используется в uniqueKey для дедупликации — в redesign-формате
         * у каждого item есть уникальный id, даже если date+type+owner+item
         * совпадают (например, два уведомления о лайках одного поста).
         */
        val rawId: String = "",
    ) {
        /**
         * Уникальный ключ для key={} и distinctBy.
         *
         * Fix #255: РАНЬШЕ использовался rawId.take(40) — это БАГ!
         * redesign id — это base64-подобная строка (60-100+ символов),
         * у которой первые ~40 символов — ОБЩИЙ префикс (кодирует
         * timestamp/партицию/курсор). take(40) обрезал id до этого
         * общего префикса, и ВСЕ 23 уведомления получали одинаковый
         * uniqueKey → distinctBy оставлял только 1.
         *
         * Теперь используем ПОЛНЫЙ rawId. Для legacy-формата (rawId="")
         * fallback включает feedbackIds и hashCode от text — чтобы
         * разные уведомления с одинаковой date+type+owner+item
         * (например, 2 лайка одного поста) не схлопывались.
         */
        val uniqueKey: String = if (rawId.isNotBlank()) {
            "redesign_$rawId"
        } else {
            val fbKey = feedbackIds.joinToString("-")
            val textHash = text.hashCode()
            "${date}_${type}_${parentOwnerId}_${parentItemId}_${fbKey}_${textHash}"
        }
    }

    /**
     * #29: вложение уведомления — компактная модель для горизонтального скролла.
     *
     * VK_IMPORT_API.MD §14.2: `notification-attachment-image` — миниатюра вложения,
     * кликабельная. Поддерживаемые типы: photo, video, clip, gift.
     */
    data class NotificationAttachment(
        val type: String,           // "photo" | "video" | "clip" | "gift"
        val thumbUrl: String?,      // URL миниатюры
        val ownerId: Long,          // для навигации (открыть пост/фото/видео)
        val itemId: Long,
        val accessKey: String?,     // для приватных фото/видео
    )

    /**
     * #29: кнопка действия в уведомлении.
     *
     * VK_IMPORT_API.MD §14.2: `notification-actions` — FlowRow с кнопками.
     * Известные сценарии:
     *  - gift: "Подарить в ответ" (modeSecondary) → открывает диалог выбора подарка
     *  - reply_comment / mention: "Ответить" (modeTertiary) → открывает ввод текста
     */
    data class NotificationAction(
        val label: String,          // "Подарить в ответ" | "Ответить" | ...
        val style: ActionStyle,     // SECONDARY (кнопка с заливкой) | TERTIARY (текстовая)
        val actionType: ActionType, // GIFT_REPLY | REPLY | OPEN | ...
        val targetUserId: Long,     // кому адресовано действие (для подарка/ответа)
    ) {
        enum class ActionStyle { SECONDARY, TERTIARY }
        enum class ActionType { GIFT_REPLY, REPLY, OPEN_POST, OPEN_USER }
    }

    /**
     * Получить конкретное видео по owner_id + video_id + access_key.
     *
     * VK возвращает полные файлы (mp4_*, hls, dash) только через video.get,
     * но НЕ всегда в attachments ленты (newsfeed.get). Этот метод —
     * fallback для VideoPlayerScreen: если files==null или URL истёк,
     * запросить свежий видео-объект с рабочими прямыми ссылками.
     *
     * @param ownerId   владелец видео
     * @param videoId   ID видео
     * @param accessKey access_key из attachment (необязателен)
     * @return Video с заполненным files, или null если видео недоступно.
     */
    suspend fun videoGetById(
        ownerId: Long,
        videoId: Long,
        accessKey: String? = null,
    ): Video? {
        if (isOffline()) return null
        val videosParam = buildString {
            append("${ownerId}_$videoId")
            if (!accessKey.isNullOrBlank()) append("_$accessKey")
        }
        val args = mapOf(
            "videos" to videosParam,
            // #VIDEO-FRAME-FIX: extended=1 — иначе VK не возвращает image[] (превью)
            // и files (прямые URL). С extended=0 фрейм пустой, а URL нет → WebView fallback.
            "extended" to "1",
        )
        val json = call("video.get", args) ?: return null
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items")
            if (items == null || items.size() == 0) {
                AppLog.w("VKApiClient", "videoGetById: пустой ответ для $videosParam")
                return null
            }
            val o = items.get(0).asJsonObject
            val thumbs = parseVideoThumbs(o)
            val v = Video(
                id = o.get("id")?.asLong ?: 0L,
                ownerId = o.get("owner_id")?.asLong ?: 0L,
                title = o.get("title")?.asString ?: "",
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                duration = o.get("duration")?.asInt ?: 0,
                date = o.get("date")?.asLong ?: 0L,
                views = o.get("views")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                player = o.get("player")?.takeIf { !it.isJsonNull }?.asString,
                files = parseVideoFiles(o),
                accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                image = thumbs,
                likes = parseLikes(o.getAsJsonObject("likes")),
            )
            // OK-IMPL-1 (Stage 1): определяем типизированную платформу видео
            // (VK/OK/YOUTUBE/EXTERNAL_IFRAME/UNKNOWN) и externalId для OK/YouTube.
            v.withDetectedPlatform()
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetById parse error", e)
            null
        }
    }

    /**
     * video.get — получить список видео пользователя/сообщества.
     * Возвращает Video с заполненными files (прямые URL).
     */
    suspend fun videoGet(
        ownerId: Long? = null,
        count: Int = 30,
        offset: Int = 0,
        albumId: Long? = null,
    ): List<Video> {
        if (isOffline()) return emptyList()
        // #VIDEO-FRAME-FIX: extended=1 — иначе нет image[] (превью) и files (URL),
        // у OK-crosspost видео фрейм не рисуется и играть нечем.
        val args = mutableMapOf("count" to count.toString(), "extended" to "1")
        if (ownerId != null) args["owner_id"] = ownerId.toString()
        // #VIDEO-PORT: album_id — видео конкретного альбома.
        if (albumId != null) args["album_id"] = albumId.toString()
        if (offset > 0) args["offset"] = offset.toString()
        val json = call("video.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val thumbs = parseVideoThumbs(o)
                // Fix #54/#69: парсим files map через общий helper.
                // Ранее files всегда был null, и VideoPlayerScreen падал в
                // "video.files?.let {...} ?: video.player" → использовал HTML player URL
                // → ExoPlayer не мог воспроизвести HTML → "Видео недоступно".
                // Теперь files наполняется реально: mp4_1080, mp4_720, ..., hls, dash.
                val filesMap = parseVideoFiles(o)
                // player — это HTML-страница плеера VK (НЕ прямой видеофайл).
                // ExoPlayer НЕ может воспроизвести HTML. Сохраняем для fallback
                // (открыть в браузере), но приоритет в VideoPlayerScreen — files.
                val playerUrl = o.get("player")?.takeIf { !it.isJsonNull }?.asString
                val v = Video(
                    id = o.get("id")?.asLong ?: 0L,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    title = o.get("title")?.asString ?: "",
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    duration = o.get("duration")?.asInt ?: 0,
                    date = o.get("date")?.asLong ?: 0L,
                    views = o.get("views")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    player = playerUrl,
                    files = filesMap,
                    accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                    image = thumbs,
                    // Sprint 2, P1-2 (#89): парсим likes для video.get ответа.
                    likes = parseLikes(o.getAsJsonObject("likes")),
                )
                // OK-IMPL-1 (Stage 1): определяем платформу + externalId (OK/YouTube).
                v.withDetectedPlatform()
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGet parse error", e)
            emptyList()
        }
    }

    // ========================================================================
    //  #35: Полный функционал соцсети ВК — расширенные API-методы
    //  Добавлено: friends.get, groups.get, photos.getAlbums, photos.get,
    //  search.getHints, fave.get, docs.get, wall.get, users.getFollowers,
    //  users.search, groups.join, groups.leave, friends.add, friends.delete.
    //  Все методы работают через единый call() → sig-логика общая.
    // ========================================================================

    /**
     * #VIDEO-SEARCH: video.search — поиск видео по запросу.
     * VK: video.search { q, count, offset, extended=1, sort }.
     * Работает с web-токеном (vk1.a.*).
     */
    suspend fun videoSearch(
        query: String,
        count: Int = 30,
        offset: Int = 0,
    ): List<Video> {
        if (isOffline() || query.isBlank()) return emptyList()
        val args = mutableMapOf(
            "q" to query,
            "count" to count.toString(),
            "extended" to "1",
        )
        if (offset > 0) args["offset"] = offset.toString()
        val json = call("video.search", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val thumbs = parseVideoThumbs(o)
                val filesMap = parseVideoFiles(o)
                val playerUrl = o.get("player")?.takeIf { !it.isJsonNull }?.asString
                val v = Video(
                    id = o.get("id")?.asLong ?: 0L,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    title = o.get("title")?.asString ?: "",
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    duration = o.get("duration")?.asInt ?: 0,
                    date = o.get("date")?.asLong ?: 0L,
                    views = o.get("views")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    player = playerUrl,
                    files = filesMap,
                    accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                    image = thumbs,
                    likes = parseLikes(o.getAsJsonObject("likes")),
                )
                v.withDetectedPlatform()
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoSearch parse error", e)
            emptyList()
        }
    }

    /**
     * #VIDEO-PORT: video.getAlbums — альбомы видео пользователя/сообщества.
     * VK: video.getAlbums { owner_id, count, offset, extended=1 }.
     */
    suspend fun videoGetAlbums(
        ownerId: Long? = null,
        count: Int = 30,
        offset: Int = 0,
    ): Pair<Int, List<re.pinok.data.model.VideoAlbum>> {
        if (isOffline()) return 0 to emptyList()
        val args = mutableMapOf("count" to count.toString(), "extended" to "1")
        if (ownerId != null) args["owner_id"] = ownerId.toString()
        if (offset > 0) args["offset"] = offset.toString()
        val json = call("video.getAlbums", args) ?: return 0 to emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return 0 to emptyList()
            val total = resp.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val items = resp.getAsJsonArray("items") ?: return total to emptyList()
            val albums = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.VideoAlbum(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    ownerId = o.get("owner_id")?.asLong ?: return@mapNotNull null,
                    title = o.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    count = o.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    plays = o.get("plays")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    updatedTime = o.get("updated_time")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    photo320 = o.get("photo_320")?.takeIf { !it.isJsonNull }?.asString,
                    photo160 = o.get("photo_160")?.takeIf { !it.isJsonNull }?.asString,
                    image = o.getAsJsonArray("image")?.mapNotNull { im ->
                        if (!im.isJsonObject) return@mapNotNull null
                        val io = im.asJsonObject
                        re.pinok.data.model.Video.Thumb(
                            url = io.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null,
                            width = io.get("width")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                            height = io.get("height")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                        )
                    },
                )
            }
            total to albums
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetAlbums parse error", e)
            0 to emptyList()
        }
    }

    /**
     * #VIDEO-PORT: video.getCatalog — разделы видео-каталога.
     * Возвращает response.sections[] («Для вас»/«Тренды»/«Детям»/«Телеканалы»).
     */
    suspend fun videoGetCatalogSections(): List<re.pinok.data.model.VideoCatalogSection> {
        if (isOffline()) return emptyList()
        val json = call("video.getCatalog", mapOf("extended" to "1")) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val sections = resp.getAsJsonArray("sections") ?: return emptyList()
            sections.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                re.pinok.data.model.VideoCatalogSection(
                    id = o.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null,
                    name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    url = o.get("url")?.takeIf { !it.isJsonNull }?.asString,
                    isSelected = o.get("is_selected")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetCatalogSections parse error", e)
            emptyList()
        }
    }

    /**
     * #VIDEO-PORT: video.getVideoDiscover — discovery-лента видео раздела каталога.
     * VK: video.getVideoDiscover { section_id / section_name, extended=1 }.
     * Для web-токена → err=15. Fallback: catalog.getSection(section_id) → videos.
     */
    suspend fun videoGetDiscover(
        sectionId: String? = null,
        sectionName: String? = null,
        count: Int = 30,
        startFrom: String? = null,
    ): List<Video> {
        if (isOffline()) return emptyList()
        // Path 1: catalog.getSection по section_id (тот же паттерн, что в музыке).
        if (!sectionId.isNullOrBlank()) {
            val sectionVideos = catalogGetSectionVideos(sectionId)
            if (sectionVideos.isNotEmpty()) return sectionVideos
        }
        // Path 2: video.getVideoDiscover (для direct-токенов).
        val args = mutableMapOf("count" to count.toString(), "extended" to "1")
        if (!sectionName.isNullOrBlank()) args["section"] = sectionName
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        val json = call("video.getVideoDiscover", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val items = resp.getAsJsonArray("items")
                ?: resp.getAsJsonArray("videos")
                ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                parseVideoFromJson(o)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetDiscover parse error", e)
            emptyList()
        }
    }

    /**
     * #VIDEO-PORT: catalog.getSection по видео section_id → видео раздела.
     * Ответ: response.section.blocks[] с data_type="videos" + videos_ids[],
     * параллельный response.videos[].
     */
    private suspend fun catalogGetSectionVideos(sectionId: String): List<Video> {
        val json = call("catalog.getSection", mapOf(
            "section_id" to sectionId,
            "need_blocks" to "1",
        )) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val videosById = HashMap<String, Video>()
            resp.getAsJsonArray("videos")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val v = parseVideoFromJson(o) ?: return@forEach
                videosById["${v.ownerId}_${v.id}"] = v
            }
            val result = mutableListOf<Video>()
            resp.getAsJsonObject("section")?.getAsJsonArray("blocks")?.forEach { be ->
                if (!be.isJsonObject) return@forEach
                be.asJsonObject.getAsJsonArray("videos_ids")?.forEach { idEl ->
                    val rawId = idEl.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                    videosById[rawId]?.let { result.add(it) }
                }
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "catalogGetSectionVideos parse error", e)
            emptyList()
        }
    }

    /** #VIDEO-PORT: парсит Video из JsonObject (общий для catalog/discover). */
    private fun parseVideoFromJson(o: com.google.gson.JsonObject): re.pinok.data.model.Video? {
        val filesMap = parseVideoFiles(o)
        return re.pinok.data.model.Video(
            id = o.get("id")?.asLong ?: return null,
            ownerId = o.get("owner_id")?.asLong ?: return null,
            title = o.get("title")?.asString ?: "",
            duration = o.get("duration")?.asInt ?: 0,
            date = o.get("date")?.asLong ?: 0L,
            views = o.get("views")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            player = o.get("player")?.takeIf { !it.isJsonNull }?.asString,
            files = filesMap,
            accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
            image = parseVideoThumbs(o),
        ).withDetectedPlatform()
    }

    /** friends.get — список друзей пользователя.
     *  @param userId ID пользователя (null = текущий)
     *  @param count  лимит (макс 1000 за запрос)
     *  @param offset сдвиг для пагинации
     *  @param onlineOnly только онлайн-друзья (0/1)
     */
    suspend fun friendsGet(
        userId: Long? = null,
        count: Int = 50,
        offset: Int = 0,
        onlineOnly: Boolean = false,
    ): List<Friend> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "fields" to "photo_100,photo_200,online,last_seen,status,bdate,sex,city,verified,online_app,online_mobile",
            "order" to "hints",
        )
        if (userId != null) args["user_id"] = userId.toString()
        if (onlineOnly) args["online"] = "1"
        val json = call("friends.get", args) ?: return emptyList()
        return parseFriendsList(json)
    }

    /**
     * #FRIENDS-RECOMMEND: friends.getRecommendations — рекомендованные друзья.
     * Работает с web-токеном (vk1.a.*). Формат ответа совпадает с friends.get
     * (items[]). По плану §54.5 P1-1.
     */
    suspend fun friendsGetRecommendations(count: Int = 50): List<Friend> {
        if (isOffline()) return emptyList()
        val args = mapOf(
            "count" to count.toString(),
            "fields" to "photo_100,photo_200,online,last_seen,status,bdate,sex,city,verified",
            "order" to "hints",
        )
        val json = call("friends.getRecommendations", args) ?: return emptyList()
        return parseFriendsList(json)
    }

    private fun parseFriendsList(json: JsonObject): List<Friend> = try {
        val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
        items.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            Friend(
                id = o.get("id")?.asLong ?: return@mapNotNull null,
                firstName = o.get("first_name")?.asString ?: "",
                lastName = o.get("last_name")?.asString ?: "",
                photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                online = o.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                lastSeen = o.get("last_seen")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    UserProfile.LastSeen(
                        time = it.get("time")?.asLong ?: 0L,
                        platform = it.get("platform")?.takeIf { x -> !x.isJsonNull }?.asInt,
                    )
                },
                status = o.get("status")?.takeIf { !it.isJsonNull }?.asString,
                bdate = o.get("bdate")?.takeIf { !it.isJsonNull }?.asString,
                sex = o.get("sex")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                city = o.get("city")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    UserProfile.City(it.get("title")?.asString ?: "")
                },
                verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                onlineApp = o.get("online_app")?.takeIf { !it.isJsonNull }?.asInt,
                onlineMobile = o.get("online_mobile")?.takeIf { !it.isJsonNull }?.asInt,
            )
        }
    } catch (e: Exception) {
        AppLog.e("VKApiClient", "parseFriendsList error", e)
        emptyList()
    }

    /** groups.get — список сообществ пользователя.
     *  @param userId ID пользователя (null = текущий)
     *  @param count  лимит (макс 1000)
     *  @param offset сдвиг
     */
    suspend fun groupsGet(
        userId: Long? = null,
        count: Int = 50,
        offset: Int = 0,
        // Fix #144: filter parameter — groups.get поддерживает filter=admin/editor/moder
        // для получения сообществ где пользователь админ/редактор/модератор.
        // Используется в ForwardDialog/CreatePostDialog для «сообщества где я админ».
        //   filter=admin   — только группы где user owner (admin_level=3)
        //   filter=editor  — editor+admin (admin_level>=2)
        //   filter=moder   — moder+editor+admin (admin_level>=1)
        //   filter=groups  — только группы (не публичные страницы/события)
        //   filter=publics — только публичные страницы/события
        // null = без фильтра (default VK API behavior).
        filter: String? = null,
    ): List<Group> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            // Fix #144: добавлен admin_level в fields (нужен для ForwardDialog).
            "fields" to "members_count,description,status,verified,is_member,can_post,activity,site,type,admin_level",
        )
        if (userId != null) args["user_id"] = userId.toString()
        if (filter != null) args["filter"] = filter
        val json = call("groups.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Group(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    name = o.get("name")?.asString ?: "",
                    screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
                    isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    membersCount = o.get("members_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    status = o.get("status")?.takeIf { !it.isJsonNull }?.asString,
                    verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    isMember = o.get("is_member")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    canPost = o.get("can_post")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    activity = o.get("activity")?.takeIf { !it.isJsonNull }?.asString,
                    site = o.get("site")?.takeIf { !it.isJsonNull }?.asString,
                    // Fix #144: admin_level — парсим из ответа (0 если поле отсутствует,
                    // например при groups.get без filter=admin_editor).
                    adminLevel = o.get("admin_level")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "groupsGet parse error", e)
            emptyList()
        }
    }

    /**
     * Fix #67: groups.getById — lazy-fetch метаданных сообществ по списку ID.
     *
     * Нужен когда newsfeed.get НЕ вернул groups[] для некоторых постов
     * (или вернул не для всех). VK web-клиент делает то же самое —
     * см. `apiWithPrefetch("groups.getById", {group_ids: ..., fields: ...})`
     * в `99865.dc6f95d1369838ed.js` reference-дампа.
     *
     * @param groupIds положительные ID сообществ (без знака, как в groups[].id)
     * @param fields   дополнительные поля (по умолчанию photo_100, description, ...)
     */
    suspend fun groupsGetById(
        groupIds: List<Long>,
        fields: String = "photo_100,photo_200,description,members_count,verified,activity,status,screen_name,is_member,type",
    ): List<GroupInfo> {
        if (isOffline() || groupIds.isEmpty()) return emptyList()
        // #30i (groups fix): всегда используем group_ids (plural) — это работает
        // с vk1.a.* web-токенами. group_id (singular) отдаёт другой формат ответа.
        // Обрабатываем все возможные форматы ответа.
        val args = mutableMapOf(
            "group_ids" to groupIds.joinToString(",") { it.toString() },
            "fields" to fields,
        )
        AppLog.d("VKApiClient", "groupsGetById: args=$args")
        val json = call("groups.getById", args) ?: return emptyList()
        return try {
            // VK API groups.getById возвращает РАЗНЫЕ форматы:
            //  1. {"response": [{...}, {...}]}  — массив (старый стандартный)
            //  2. {"response": {"count":N, "items":[...]}}  — объект с items
            //  3. {"response": {"groups":[...]}}  — VK API 5.282+ (новый формат с fields)
            //  4. {"response": {"id":..., "name":...}}  — одиночный объект
            //  5. {"response": 1}  — просто число (success)
            val resp = json.get("response")
            val arr: com.google.gson.JsonArray? = when {
                resp == null || resp.isJsonNull -> null
                resp.isJsonArray -> resp.asJsonArray
                resp.isJsonObject -> {
                    val obj = resp.asJsonObject
                    when {
                        obj.has("groups") -> obj.getAsJsonArray("groups")
                        obj.has("items") -> obj.getAsJsonArray("items")
                        obj.has("id") -> com.google.gson.JsonArray().apply { add(obj) }
                        else -> null
                    }
                }
                else -> null
            }
            if (arr == null) {
                AppLog.w("VKApiClient", "groupsGetById: unexpected response: ${json.toString().take(200)}")
                return emptyList()
            }
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val gid = o.get("id")?.asLong ?: return@mapNotNull null
                GroupInfo(
                    id = gid,
                    name = o.get("name")?.asString ?: "",
                    screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    isMember = o.get("is_member")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    membersCount = o.get("members_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    status = o.get("status")?.takeIf { !it.isJsonNull }?.asString,
                    type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "groupsGetById parse error", e)
            emptyList()
        }
    }

    /** photos.getAlbums — список фотоальбомов пользователя.
     *  @param ownerId ID владельца (отрицательный для групп, null = текущий) */
    suspend fun photosGetAlbums(ownerId: Long? = null): List<Album> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf("need_covers" to "1", "need_system" to "1")
        if (ownerId != null) args["owner_id"] = ownerId.toString()
        val json = call("photos.getAlbums", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Album(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    title = o.get("title")?.asString ?: "",
                    size = o.get("size")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    thumbId = o.get("thumb_id")?.takeIf { !it.isJsonNull }?.asLong,
                    thumbSrc = o.get("thumb_src")?.takeIf { !it.isJsonNull }?.asString,
                    created = o.get("created")?.takeIf { !it.isJsonNull }?.asLong ?: 0,
                    updated = o.get("updated")?.takeIf { !it.isJsonNull }?.asLong ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosGetAlbums parse error", e)
            emptyList()
        }
    }

    /** photos.get — фотографии из альбома.
     *  @param ownerId  ID владельца альбома
     *  @param albumId  ID альбома (или "wall", "profile", "saved") */
    suspend fun photosGet(
        ownerId: Long,
        albumId: String = "profile",
        count: Int = 50,
        offset: Int = 0,
    ): List<PhotoItem> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "album_id" to albumId,
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
        )
        val json = call("photos.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el -> parsePhotoItem(el) }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "photosGet parse error", e)
            emptyList()
        }
    }

    /** search.getHints — подсказки поиска (люди, группы, приложения). */
    suspend fun searchGetHints(query: String, count: Int = 20): List<SearchHint> {
        if (isOffline() || query.isBlank()) return emptyList()
        val args = mutableMapOf(
            "q" to query.trim(),
            "limit" to count.toString(),
            "search_global" to "1",
        )
        val json = call("search.getHints", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val type = o.get("type")?.asString ?: return@mapNotNull null
                val desc = o.get("description")?.takeIf { !it.isJsonNull }?.asString
                val section = o.get("section")?.takeIf { !it.isJsonNull }?.asString
                var user: UserProfile? = null
                var group: Group? = null
                var appId: Long? = null
                if (type == "profile") {
                    val p = o.getAsJsonObject("profile")
                    if (p != null) user = parseUserProfileMini(p)
                } else if (type == "group") {
                    val g = o.getAsJsonObject("group")
                    if (g != null) group = parseGroupMini(g)
                } else if (type == "app") {
                    // Fix #233 (P1-8): парсим app.id — без него все app-hints
                    // имеют одинаковый LazyColumn key "app_null" → crash.
                    val a = o.getAsJsonObject("app")
                    appId = a?.get("id")?.takeIf { !it.isJsonNull }?.asLong
                }
                SearchHint(type = type, section = section, description = desc, global = 1, user = user, group = group, appId = appId)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "searchGetHints parse error", e)
            emptyList()
        }
    }

    /** users.search — поиск людей по имени/фамилии. */
    suspend fun usersSearch(query: String, count: Int = 20, offset: Int = 0): List<UserProfile> {
        if (isOffline() || query.isBlank()) return emptyList()
        val args = mutableMapOf(
            "q" to query.trim(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "fields" to "photo_100,photo_200,online,last_seen,status,verified,city,bdate,sex",
        )
        val json = call("users.search", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                if (o.get("id") == null) return@mapNotNull null
                parseUserProfileMini(o)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersSearch parse error", e)
            emptyList()
        }
    }

    /** groups.search — поиск сообществ. */
    suspend fun groupsSearch(query: String, count: Int = 20, offset: Int = 0): List<Group> {
        if (isOffline() || query.isBlank()) return emptyList()
        val args = mutableMapOf(
            "q" to query.trim(),
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        val json = call("groups.search", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                Group(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    name = o.get("name")?.asString ?: "",
                    screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
                    isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    membersCount = o.get("members_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    isMember = o.get("is_member")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "groupsSearch parse error", e)
            emptyList()
        }
    }

    /** fave.get — закладки (люди, группы, посты, фото, видео, ссылки). */
    suspend fun faveGet(count: Int = 30, offset: Int = 0, tagId: Long? = null): List<Bookmark> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
        )
        tagId?.let { args["tag_id"] = it.toString() }
        val json = call("fave.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                val type = o.get("type")?.asString ?: return@mapNotNull null
                val seen = o.get("seen")?.takeIf { !it.isJsonNull }?.let {
                    if (it.isJsonPrimitive) {
                        val p = it.asJsonPrimitive
                        if (p.isBoolean) p.asBoolean else p.asInt != 0
                    } else false
                }
                val addedDate = o.get("added_date")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                var user: UserProfile? = null
                var group: Group? = null
                var post: Post? = null
                var photo: PhotoItem? = null
                var video: Video? = null
                var link: Attachment.Link? = null
                val entity = o.getAsJsonObject(type) ?: o
                when (type) {
                    "user", "profile" -> user = parseUserProfileMini(entity)
                    "group" -> group = parseGroupMini(entity)
                    "post" -> post = parsePostMini(entity)
                    "photo" -> photo = parsePhotoItem(entity)
                    "video" -> video = parseVideoMini(entity)
                    "link" -> {
                        link = Attachment.Link(
                            url = entity.get("url")?.asString ?: "",
                            title = entity.get("title")?.takeIf { !it.isJsonNull }?.asString,
                            description = entity.get("description")?.takeIf { !it.isJsonNull }?.asString,
                        )
                    }
                }
                Bookmark(type = type, seen = seen ?: false, addedDate = addedDate,
                    user = user, group = group, post = post, photo = photo, video = video, link = link)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "faveGet parse error", e)
            emptyList()
        }
    }

    /** docs.get — документы пользователя. */
    suspend fun docsGet(count: Int = 50, offset: Int = 0): List<DocFile> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
        )
        val json = call("docs.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                DocFile(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    ownerId = o.get("owner_id")?.asLong ?: 0L,
                    title = o.get("title")?.asString ?: "",
                    ext = o.get("ext")?.asString ?: "",
                    size = o.get("size")?.asLong ?: 0L,
                    url = o.get("url")?.asString ?: "",
                    date = o.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: 0,
                    type = o.get("type")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "docsGet parse error", e)
            emptyList()
        }
    }

    /** docs.getMessagesUploadServer — для загрузки голосовых и документов в сообщения. */
    suspend fun docsGetMessagesUploadServer(type: String = "audio_message"): String? {
        if (isOffline()) return null
        val args = mapOf("type" to type)
        val json = call("docs.getMessagesUploadServer", args) ?: return null
        return try {
            val url = json.getAsJsonObject("response")?.get("upload_url")
                ?.takeIf { !it.isJsonNull }?.asString
            // Fix #154: логируем upload_url для диагностики 405 от kittenx.
            // По хосту видно какой upload-сервер VK вернул (kittenx / pu.vk.com / etc).
            AppLog.i("VKApiClient", "docsGetMessagesUploadServer(type=$type) → ${url?.take(120)}…")
            url
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "docsGetMessagesUploadServer error", e)
            null
        }
    }

    /**
     * Загрузить файл (multipart POST) на upload_url для голосового сообщения.
     * VK требует OGG/opus для voice messages.
     * Возвращает {file, ...} строку для docs.save.
     */
    suspend fun docsUploadVoice(uploadUrl: String, file: java.io.File): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name,
                        file.asRequestBody("audio/ogg".toMediaType()))
                    .build()
                // Fix #154: те же браузерные заголовки что и для docs — kittenx
                // сервер требует Origin/Referer, иначе 405.
                val req = Request.Builder()
                    .url(uploadUrl)
                    .header("Origin", VKEndpoints.WEB_ORIGIN)
                    .header("Referer", VKEndpoints.WEB_REFERER)
                    .header("User-Agent", VKEndpoints.WEB_BROWSER_UA)
                    .post(requestBody)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                val json = JsonParser.parseString(body).asJsonObject
                mapOf(
                    "file" to (json.get("file")?.asString ?: return@withContext null),
                )
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "docsUploadVoice error", e)
                null
            }
        }
    }

    /**
     * docs.save — сохранить загруженный документ. Возвращает данные для attachment.
     *
     * VK `docs.save` возвращает один из вариантов (один объект, НЕ items-массив):
     *   {"response":{"type":"audio_message","audio_message":{id,owner_id,access_key,duration,...}}}
     *   {"response":{"type":"doc","doc":{id,owner_id,access_key,...}}}
     *   {"response":{"type":"graffiti","graffiti":{...}}}
     *
     * FIX (голосовые не отправлялись): старая версия делала
     * `getAsJsonObject("response")?.getAsJsonObject("type")` — но поле `type`
     * это JSON-СТРОКА ("audio_message"), а не объект. Gson бросал
     * ClassCastException (либо возвращал null), затем fallback на сам `response`
     * тоже не содержал `owner_id`/`id` на верхнем уровне → docsSave ВСЕГДА
     * возвращал null для audio_message → `sendVoiceMessage` молча падал на
     * шаге 3 (до messages.send), голосовое просто не уходило.
     *
     * Теперь явно читаем строку `type` и берём вложенный объект по ключу.
     */
    suspend fun docsSave(
        file: String,
        title: String = "voice.ogg",
    ): Triple<Long, Long, String>? { // (ownerId, docId, accessKey)
        val args = mapOf(
            "file" to file,
            "title" to title,
        )
        val json = call("docs.save", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val typeEl = resp.get("type")
            val type = if (typeEl != null && typeEl.isJsonPrimitive) typeEl.asString else null
            val inner = when (type) {
                "audio_message" -> resp.getAsJsonObject("audio_message")
                "doc" -> resp.getAsJsonObject("doc")
                "graffiti" -> resp.getAsJsonObject("graffiti")
                else -> resp.getAsJsonArray("items")?.firstOrNull()?.asJsonObject ?: resp
            } ?: return null
            val ownerId = inner.get("owner_id")?.asLong ?: return null
            val id = inner.get("id")?.asLong ?: return null
            val accessKey = inner.get("access_key")?.takeIf { !it.isJsonNull }?.asString ?: ""
            AppLog.i("VKApiClient", "docsSave ok: type=$type doc=${ownerId}_$id key=${accessKey.take(4)}…")
            Triple(ownerId, id, accessKey)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "docsSave error", e)
            null
        }
    }

    /**
     * Загрузить произвольный документ (фото/файл) для отправки в сообщения.
     * docs.getMessagesUploadServer(type="doc") → multipart upload → docs.save.
     * Возвращает "doc{ownerId}_{id}_{accessKey}" или null.
     */
    suspend fun uploadDocForMessage(file: java.io.File, mimeType: String? = null): String? {
        if (isOffline()) return null
        // Fix #236 (file-format validation): VK docs upload-сервер отклоняет
        // определённые расширения — mp3/wav/flac (audio — нужен audio-pipeline),
        // mp4/avi/mov (video — нужен video-pipeline), apk/exe/msi/jar
        // (executables — заблокированы по безопасности). Раньше такие файлы
        // «молча» проваливали upload → пользователь видел «Не удалось отправить
        // файл» без объяснения причины. Теперь проверяем расширение ДО upload
        // и логируем понятную причину. См. VK_IMPORT_API.MD §30.
        val ext = file.extension.lowercase()
        if (ext.isNotEmpty() && ext !in VK_DOC_ALLOWED_EXTENSIONS) {
            val reason = when {
                ext in VK_AUDIO_EXTENSIONS -> "аудио-файл — используйте отправку музыки"
                ext in VK_VIDEO_EXTENSIONS -> "видео-файл — используйте отправку видео"
                ext in VK_EXECUTABLE_EXTENSIONS -> "исполняемый файл — заблокирован VK"
                else -> "формат .$ext не поддерживается VK docs"
            }
            AppLog.w("VKApiClient", "uploadDocForMessage: отклонён ${file.name} — $reason")
            return null
        }
        val uploadUrl = docsGetMessagesUploadServer(type = "doc") ?: return null
        val actualMime = mimeType ?: guessMimeType(file.name)
        val uploadResult = withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name,
                        file.asRequestBody(actualMime.toMediaType()))
                    .build()
                // Fix #154: kittenx (web-upload сервер) отклоняет POST без браузерных
                // заголовков — HTTP 405. Добавляем Origin/Referer/Chrome-UA как vk.com.
                val req = Request.Builder()
                    .url(uploadUrl)
                    .header("Origin", VKEndpoints.WEB_ORIGIN)
                    .header("Referer", VKEndpoints.WEB_REFERER)
                    .header("User-Agent", VKEndpoints.WEB_BROWSER_UA)
                    .post(requestBody)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                // FIX: VK upload-сервер может вернуть HTML (ошибка, капча) вместо JSON.
                if (!body.trimStart().startsWith("{")) {
                    AppLog.w("VKApiClient", "uploadDocForMessage: non-JSON response (${resp.code}): ${body.take(200)}")
                    return@withContext null
                }
                val json = try {
                    JsonParser.parseString(body).asJsonObject
                } catch (e: com.google.gson.JsonSyntaxException) {
                    AppLog.w("VKApiClient", "uploadDocForMessage: malformed JSON: ${body.take(200)}")
                    return@withContext null
                }
                mapOf(
                    "file" to (json.get("file")?.asString
                        ?: return@withContext null),  // Fix #232: fail-fast — пустой file → null, не ""
                    "size" to (json.get("size")?.asString ?: ""),
                )
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "uploadDocForMessage upload error", e)
                null
            }
        } ?: return null

        val saved = docsSave(uploadResult["file"] ?: "", title = file.name) ?: return null
        val (ownerId, docId, accessKey) = saved
        return if (accessKey.isNotBlank()) "doc${ownerId}_${docId}_$accessKey"
               else "doc${ownerId}_${docId}"
    }

    /** Отправить сообщение с attachment (doc, photo, wall, etc.). */
    suspend fun sendWithAttachment(peerId: Long, attachment: String, message: String = ""): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "attachment" to attachment,
            "random_id" to randomIdCounter.incrementAndGet().toString(),
        )
        if (message.isNotBlank()) args["message"] = message
        val json = call("messages.send", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
        } catch (_: Exception) { -1L }
    }

    /**
     * Fix #234 (multi-photo preview): загрузить ФОТО для комментария к посту.
     *
     * VK для комментариев к постам использует WALL-photo pipeline (НЕ messages,
     * как в чатах, и НЕ doc, как для произвольных файлов):
     *   1. photos.getWallUploadServer() → upload_url
     *   2. multipart POST файла → {server, photo, hash}
     *   3. photos.saveWallPhoto(server, photo, hash) → Pair<photoId, ownerId>
     *   4. attachment = "photo{ownerId}_{photoId}"
     *
     * Раньше [uploadDocForComment] использовался и для фото тоже — фото приходило
     * как ДОКУМЕНТ (без миниатюры, надо было кликать чтобы открыть). Теперь UI
     * явно выбирает фото-путь для картинок, doc-путь — для остальных файлов.
     *
     * Возвращает "photo{ownerId}_{id}" или null при ошибке.
     */
    suspend fun uploadPhotoForComment(file: java.io.File, mimeType: String?): String? {
        if (isOffline()) return null
        val uploadUrl = photosGetWallUploadServer(groupId = null) ?: run {
            AppLog.e("VKApiClient", "uploadPhotoForComment: failed to get wall upload server")
            return null
        }
        val uploaded = photosUploadWallPhoto(uploadUrl, file, mimeType) ?: run {
            AppLog.e("VKApiClient", "uploadPhotoForComment: failed to upload")
            return null
        }
        val (photoId, photoOwnerId) = photosSaveWallPhoto(
            server = uploaded.server,
            photo = uploaded.photo,
            hash = uploaded.hash,
        )
        if (photoId <= 0L || photoOwnerId <= 0L) {
            AppLog.e("VKApiClient", "uploadPhotoForComment: photosSaveWallPhoto failed")
            return null
        }
        val attachment = "photo${photoOwnerId}_${photoId}"
        AppLog.i("VKApiClient", "uploadPhotoForComment: attachment=$attachment")
        return attachment
    }

    /**
     * Загрузить документ для комментария к посту.
     * docs.getWallUploadServer → multipart upload → docs.save.
     * Возвращает "doc{ownerId}_{id}_{accessKey}" или null.
     */
    suspend fun uploadDocForComment(file: java.io.File, mimeType: String? = null): String? {
        if (isOffline()) return null
        // docs.getWallUploadServer — для документов на стенах/в комментариях.
        val args = mapOf("type" to "doc")
        val json = call("docs.getWallUploadServer", args) ?: return null
        val uploadUrl = try {
            json.getAsJsonObject("response")?.get("upload_url")
                ?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "docs.getWallUploadServer error", e)
            null
        } ?: return null

        val actualMime = mimeType ?: guessMimeType(file.name)
        val uploadResult = withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name,
                        file.asRequestBody(actualMime.toMediaType()))
                    .build()
                // Fix #154: те же браузерные заголовки для wall-comment doc upload.
                val req = Request.Builder()
                    .url(uploadUrl)
                    .header("Origin", VKEndpoints.WEB_ORIGIN)
                    .header("Referer", VKEndpoints.WEB_REFERER)
                    .header("User-Agent", VKEndpoints.WEB_BROWSER_UA)
                    .post(requestBody)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                val parsed = JsonParser.parseString(body).asJsonObject
                mapOf(
                    "file" to (parsed.get("file")?.asString
                        ?: return@withContext null),  // Fix #232: fail-fast — пустой file → null, не ""
                    "size" to (parsed.get("size")?.asString ?: ""),
                )
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "uploadDocForComment upload error", e)
                null
            }
        } ?: return null

        val saved = docsSave(uploadResult["file"] ?: "", title = file.name) ?: return null
        val (ownerId, docId, accessKey) = saved
        return if (accessKey.isNotBlank()) "doc${ownerId}_${docId}_$accessKey"
               else "doc${ownerId}_${docId}"
    }

    /** Упрощённый MIME-type гесс по расширению файла. */
    private fun guessMimeType(filename: String): String = when {
        filename.endsWith(".jpg", true) || filename.endsWith(".jpeg", true) -> "image/jpeg"
        filename.endsWith(".png", true) -> "image/png"
        filename.endsWith(".gif", true) -> "image/gif"
        filename.endsWith(".webp", true) -> "image/webp"
        filename.endsWith(".mp4", true) -> "video/mp4"
        filename.endsWith(".mp3", true) -> "audio/mpeg"
        filename.endsWith(".pdf", true) -> "application/pdf"
        filename.endsWith(".doc", true) -> "application/msword"
        filename.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    /**
     * Полный pipeline отправки голосового сообщения:
     * getMessagesUploadServer(type=audio_message) → multipart upload →
     * docs.save → messages.send с `doc{ownerId}_{id}_{accessKey}` attachment.
     *
     * Каждый шаг логируется отдельно — раньше всё падало молча на шаге 3
     * (см. [docsSave]), и в логах было не видно, какой этап сломался.
     *
     * @return id отправленного сообщения или -1 при ошибке.
     */
    suspend fun sendVoiceMessage(peerId: Long, audioFile: java.io.File): Long {
        if (isOffline()) return -1L
        AppLog.i("VKApiClient", "sendVoiceMessage: peer=$peerId file=${audioFile.name} (${audioFile.length()} B)")

        // 1. Получить upload URL.
        val uploadUrl = docsGetMessagesUploadServer("audio_message") ?: run {
            AppLog.e("VKApiClient", "sendVoiceMessage ✗ step1 getMessagesUploadServer failed")
            return -1L
        }
        // 2. Загрузить файл.
        val uploadResult = docsUploadVoice(uploadUrl, audioFile) ?: run {
            AppLog.e("VKApiClient", "sendVoiceMessage ✗ step2 upload failed → $uploadUrl")
            return -1L
        }
        val fileToken = uploadResult["file"]
        if (fileToken.isNullOrBlank()) {
            AppLog.e("VKApiClient", "sendVoiceMessage ✗ step2 upload returned empty file token")
            return -1L
        }
        // 3. Сохранить документ.
        val (ownerId, docId, accessKey) = docsSave(fileToken, "voice.ogg") ?: run {
            AppLog.e("VKApiClient", "sendVoiceMessage ✗ step3 docs.save failed")
            return -1L
        }
        // 4. Отправить сообщение с attachment.
        val attachment = "doc${ownerId}_${docId}" + if (accessKey.isNotBlank()) "_$accessKey" else ""
        AppLog.i("VKApiClient", "sendVoiceMessage step4 → messages.send attachment=$attachment")
        val args = mutableMapOf(
            "peer_id" to peerId.toString(),
            "attachment" to attachment,
            "random_id" to randomIdCounter.incrementAndGet().toString(),
        )
        val json = call("messages.send", args) ?: run {
            AppLog.e("VKApiClient", "sendVoiceMessage ✗ step4 messages.send returned null")
            return -1L
        }
        return try {
            val mid = json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
            AppLog.i("VKApiClient", "sendVoiceMessage ✓ sent messageId=$mid")
            mid
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "sendVoiceMessage parse error", e)
            -1L
        }
    }

    // Sprint 3 #13: Стикеры.

    /** store.getProducts — доступные наборы стикеров (содержит сами стикеры).
     *
     * Fix #210 (стикер-панель не работает): VK API требует параметр `filters`
     * (error 100 "filters is undefined" без него). `filters=purchased` возвращает
     * только купленные/доступные юзеру паки — именно то, что нужно для панели.
     * VK web (research RESEARCH-STICKERS-1) вызывает {type:"stickers"} без filters,
     * но web использует внутренний API; публичный VK API требует filters явно. */
    suspend fun storeGetStickerPacks(): List<re.pinok.data.model.StickerPack> {
        if (isOffline()) return emptyList()
        val json = call("store.getProducts", mapOf(
            "type" to "stickers",
            "filters" to "purchased",
            "extended" to "1",
            "count" to "100",
        )) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items")
            // Fix #221: НЕ фильтруем деактивированные паки — помечаем в модели.
            // Раньше (Fix #220) фильтровали active=0, но юзер попросил отправлять
            // такие стикеры как картинку. Теперь пикер показывает ВСЕ купленные паки,
            // а sendSticker решает: active=true → messagesSendSticker,
            // active=false → messagesSendStickerAsImage (скачивает картинку стикера
            // и шлёт как photo attachment).
            val total = items?.size() ?: 0
            var inactiveCount = 0
            val result = items?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val purchased = safeInt(obj.get("purchased"), 1) == 1
                val active = safeInt(obj.get("active"), 1) == 1
                if (!active) inactiveCount++
                re.pinok.data.model.StickerPack(
                    id = obj.get("id")?.asInt ?: return@mapNotNull null,
                    title = obj.get("title")?.asString ?: "",
                    stickers = obj.getAsJsonArray("stickers")?.mapNotNull { s ->
                        if (!s.isJsonObject) return@mapNotNull null
                        val so = s.asJsonObject
                        re.pinok.data.model.StickerItem(
                            stickerId = so.get("sticker_id")?.asInt ?: return@mapNotNull null,
                            productId = obj.get("id")?.asInt ?: 0,
                            images = so.getAsJsonArray("images")?.mapNotNull { img ->
                                if (!img.isJsonObject) null else {
                                    val io = img.asJsonObject
                                    re.pinok.data.model.StickerImage(
                                        url = io.get("url")?.asString ?: return@mapNotNull null,
                                        width = io.get("width")?.asInt ?: 64,
                                        height = io.get("height")?.asInt ?: 64,
                                    )
                                }
                            },
                            imagesWithBackground = so.getAsJsonArray("images_with_background")?.mapNotNull { img ->
                                if (!img.isJsonObject) null else {
                                    val io = img.asJsonObject
                                    re.pinok.data.model.StickerImage(
                                        url = io.get("url")?.asString ?: return@mapNotNull null,
                                        width = io.get("width")?.asInt ?: 64,
                                        height = io.get("height")?.asInt ?: 64,
                                    )
                                }
                            },
                            // Fix #229: анимированные стикеры (animated WebP / GIF / Lottie JSON).
                            animationUrl = so.get("animation_url")?.takeIf { !it.isJsonNull }?.asString,
                        )
                    },
                    purchased = purchased,
                    active = active,
                )
            } ?: emptyList()
            if (inactiveCount > 0) {
                AppLog.i("VKApiClient", "storeGetStickerPacks: $inactiveCount inactive (will be sent as image) of $total packs")
            }
            // Fix #229: логируем сколько анимированных стикеров нашли.
            val animCount = result.sumOf { pack -> pack.stickers?.count { it.animationUrl != null } ?: 0 }
            if (animCount > 0) {
                AppLog.i("VKApiClient", "storeGetStickerPacks: $animCount animated stickers found across ${result.size} packs")
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "storeGetStickerPacks error", e)
            emptyList()
        }
    }

    /**
     * Fix #221: получить каталог стикер-паков (включая НЕ купленные).
     * VK API store.getProducts с filters=featured возвращает рекомендуемые/популярные
     * паки. Помечаем purchased=false для тех, которых нет у юзера.
     *
     * Используется, чтобы показать платные стикеры в пикере с затемнением + 🔒.
     *
     * @return список паков, где purchased=false означает "не куплен" (показать с lock).
     */
    suspend fun storeGetStickerCatalog(): List<re.pinok.data.model.StickerPack> {
        if (isOffline()) return emptyList()
        // #STORE-FEATURED-FIX (2026-08-18): `filters=featured` с web-токеном
        // возвращает error 100, а без `filters` — error 100 "filters is undefined".
        // VK store.getProducts требует filters всегда. Показывать непокупные стикеры
        // через этот эндпоинт не работает — возвращаем empty как fallback.
        // Покупные стикеры уже загружены в storeGetStickerPacks (filters=purchased).
        return emptyList()
    }

    /** messages.send со стикером.
     *
     * Fix #210 (стикер-панель не работает): VK web отправляет стикер через
     * `attachment="sticker<id>"`, а НЕ через параметр `sticker_id`
     * (research RESEARCH-STICKERS-1: "sticker_id НЕ найден в архиве как параметр
     * messages.send для чата"). Используем attachment как primary формат.
     * Fallback на `sticker_id` оставлен на случай, если для конкретного
     * аккаунта/версии API attachment не сработает (VK API docs считают оба
     * валидными). call() возвращает null при API error → fallback сработает. */
    /**
     * Fix #223: отправка стикера с автоматическим перехватом err=100 "not available".
     *
     * VK помечает стикер-паки active=1, но при этом отклоняет отправку конкретных
     * стикеров с err=100 "this sticker is not available" (копирайт/жалобы/удаление
     * пака). Флаг active из store.getProducts НЕНАДЁЖЕН — см. лог Fix #221 где
     * стикер 106131 из active=1 пака вернул err=100.
     *
     * Поэтому вместо предсказания по флагу active перехватываем по фактической
     * ошибке: если обе попытки (attachment=sticker<id> и sticker_id) провалились
     * с err=100 "not available" — автоматически отправляем стикер как картинку
     * (download PNG → photos upload → photo attachment). Для юзера в чате разницы
     * нет: стикер и фото выглядят одинаково.
     *
     * @param fallbackImageUrl URL PNG стикера (StickerItem.displayUrl, 256px).
     *        null → фолбэкка нет (только sticker-отправка).
     * @return message_id (>0 — успех, -1 — ошибка).
     */
    suspend fun messagesSendSticker(peerId: Long, stickerId: Int, fallbackImageUrl: String? = null): Long {
        if (isOffline()) return -1L
        val randomId = randomIdCounter.incrementAndGet().toString()
        // Primary: attachment="sticker<id>" (формат VK web, подтверждён research'ом).
        var json = call("messages.send", mapOf(
            "peer_id" to peerId.toString(),
            "attachment" to "sticker$stickerId",
            "random_id" to randomId,
        ))

        // Fix #226: захватываем ошибку ПЕРВОЙ попытки ДО второго call() —
        // иначе второй call() перезапишет lastApiErrorCode/lastApiError и мы
        // потеряем информацию о том, что первая попытка провалилась из-за
        // "sticker not available".
        val firstErrCode = lastApiErrorCode
        val firstErrMsg = lastApiError ?: ""

        if (json == null) {
            // Fix #226: если первая попытка уже вернула "sticker not available" —
            // перехватываем НЕМЕДЛЕННО, без второго call(). Это экономит один
            // заведомо неудачный API-запрос и исключает риск, что вторая попытка
            // вернёт другой error code и перезапишет lastApiErrorCode.
            if (isStickerNotAvailableError(firstErrCode, firstErrMsg) && !fallbackImageUrl.isNullOrBlank()) {
                AppLog.i("VKApiClient", "messagesSendSticker: sticker $stickerId not available (1st attempt: err=$firstErrCode: $firstErrMsg) — intercepting immediately, sending as image")
                return messagesSendStickerAsImage(peerId, stickerId, fallbackImageUrl)
            }
            // Fallback: sticker_id параметр (VK API docs).
            AppLog.w("VKApiClient", "messagesSendSticker: attachment=sticker<$stickerId> returned null (1st: err=$firstErrCode: $firstErrMsg), falling back to sticker_id param")
            json = call("messages.send", mapOf(
                "peer_id" to peerId.toString(),
                "sticker_id" to stickerId.toString(),
                "random_id" to randomIdCounter.incrementAndGet().toString(),
            )) ?: run {
                // Fix #223/#226: обе попытки провалились. Проверяем ОБЕ ошибки —
                // если ХОТЯ БЫ ОДНА попытка вернула "sticker not available",
                // перехватываем и отправляем как картинку.
                val secondErrCode = lastApiErrorCode
                val secondErrMsg = lastApiError ?: ""
                val notAvailable1 = isStickerNotAvailableError(firstErrCode, firstErrMsg)
                val notAvailable2 = isStickerNotAvailableError(secondErrCode, secondErrMsg)
                if ((notAvailable1 || notAvailable2) && !fallbackImageUrl.isNullOrBlank()) {
                    AppLog.i("VKApiClient", "messagesSendSticker: sticker $stickerId not available — intercepting after 2nd attempt, sending as image (1st: err=$firstErrCode, 2nd: err=$secondErrCode)")
                    return messagesSendStickerAsImage(peerId, stickerId, fallbackImageUrl)
                }
                AppLog.w("VKApiClient", "messagesSendSticker: both attempts failed (1st: err=$firstErrCode: $firstErrMsg, 2nd: err=$secondErrCode: $secondErrMsg), image fallback=${if (fallbackImageUrl.isNullOrBlank()) "N/A" else "skipped (not not-available)"}")
                return -1L
            }
        }
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("message_id")?.asLong
                ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesSendSticker error", e)
            -1L
        }
    }

    /**
     * Fix #226: определяет, означает ли API-ошибка что стикер недоступен
     * (заблокирован/удалён/копирайт). Используется для перехвата в
     * [messagesSendSticker] → автоматическая отправка как картинка.
     *
     * VK возвращает err=100 "This sticker is not available" для заблокированных
     * стикер-паков (копирайт/жалобы/удаление). Флаг `active` пака ненадёжен —
     * VK помечает active=1, но при отправке возвращает err=100.
     *
     * Сопоставляем (case-insensitive):
     *  - err=100: "not available", "недоступен", "недоступн", "sticker", "стикер"
     *    (err=100 для messages.send со стикером почти всегда = стикер-проблема,
     *    т.к. peer_id и random_id валидны)
     *  - err=15: "sticker" / "стикер" в сообщении (access denied для стикера)
     *  - err=10: "sticker" / "стикер" в сообщении (internal error, редко)
     *
     * Английский AND русский — lang=ru в API-вызове локализует данные, но
     * error_msg может приходить на любом из языков в зависимости от VK-сервера.
     */
    private fun isStickerNotAvailableError(errCode: Int, errMsg: String): Boolean {
        if (errMsg.isBlank()) return false
        val msg = errMsg.lowercase()
        val hasStickerWord = msg.contains("sticker") || msg.contains("стикер")
        val hasNotAvailable =
            msg.contains("not available") ||
            msg.contains("недоступен") ||
            msg.contains("недоступн") ||
            msg.contains("не доступен")
        return when (errCode) {
            100 -> hasNotAvailable || hasStickerWord
            15, 10 -> hasStickerWord
            else -> false
        }
    }

    /**
     * Fix #222: предзагрузить картинку стикера в офлайн-кеш.
     * Вызывается когда стикер отображается в пикере (AsyncImage грузит PNG через Coil,
     * мы параллельно кешируем свой экземпляр для отправки). Если стикер уже в кеше —
     * ничего не делает (быстрая проверка exists()). Если URL пустой или загрузка
     * не удалась — тихо пропускаем (не блокируем UI пикера).
     *
     * @return true если стикер есть в кеше после вызова (был или только что скачан)
     */
    suspend fun preloadStickerToCache(stickerId: Int, imageUrl: String?): Boolean {
        if (imageUrl.isNullOrBlank()) return false
        val cacheFile = stickerCacheFile(stickerId)
        if (cacheFile.exists() && cacheFile.length() > 0) return true
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(imageUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.w("VKApiClient", "preloadStickerToCache: HTTP ${resp.code} for stickerId=$stickerId")
                        return@withContext false
                    }
                    val bytes = resp.body?.bytes() ?: return@withContext false
                    cacheFile.writeBytes(bytes)
                    AppLog.d("VKApiClient", "preloadStickerToCache: cached stickerId=$stickerId (${bytes.size} bytes)")
                    true
                }
            } catch (e: Exception) {
                AppLog.w("VKApiClient", "preloadStickerToCache: failed for stickerId=$stickerId: ${e.message}")
                false
            }
        }
    }

    /**
     * Fix #221/#222: отправить недоступный стикер (active=0, "this sticker is not available")
     * как обычную картинку. VK деактивирует стикер-паки (копирайт/жалобы), но сами
     * PNG-изображения стикеров остаются доступны по тому же URL.
     *
     * Fix #222: использует офлайн-кеш (cacheDir/stickers/{stickerId}.png). Если стикер
     * был открыт ранее (preloadStickerToCache) — берём из кеша без сетевой загрузки.
     * Если кеш miss — скачиваем по URL и кешируем на будущее.
     *
     * Flow:
     *  1. Получить bytes: из кеша (если есть) ИЛИ скачать по URL + закешировать
     *  2. photos.getMessagesUploadServer(peer_id)
     *  3. POST multipart upload → server/photo/hash
     *  4. photos.saveMessagesPhoto → photo{ownerId}_{id}
     *  5. messages.send(attachment=photo{ownerId}_{id})
     *
     * @param peerId ID диалога
     * @param stickerId ID стикера (для кеша)
     * @param imageUrl URL картинки стикера (StickerItem.displayUrl — 256px PNG)
     * @return message_id (>0 — успех, -1 — ошибка)
     */
    suspend fun messagesSendStickerAsImage(peerId: Long, stickerId: Int, imageUrl: String): Long {
        if (isOffline()) return -1L
        if (imageUrl.isBlank()) {
            AppLog.w("VKApiClient", "messagesSendStickerAsImage: empty imageUrl")
            return -1L
        }
        return withContext(Dispatchers.IO) {
            try {
                // 1. Получить bytes стикера: из кеша или скачать + закешировать.
                val cacheFile = stickerCacheFile(stickerId)
                val bytes = if (cacheFile.exists() && cacheFile.length() > 0) {
                    AppLog.d("VKApiClient", "messagesSendStickerAsImage: cache hit for stickerId=$stickerId (${cacheFile.length()} bytes)")
                    cacheFile.readBytes()
                } else {
                    val downloaded = try {
                        val req = Request.Builder().url(imageUrl).get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                AppLog.w("VKApiClient", "messagesSendStickerAsImage: download HTTP ${resp.code} for $imageUrl")
                                return@withContext -1L
                            }
                            resp.body?.bytes() ?: return@withContext -1L
                        }
                    } catch (e: Exception) {
                        AppLog.e("VKApiClient", "messagesSendStickerAsImage: download failed for $imageUrl", e)
                        return@withContext -1L
                    }
                    // Закешировать на будущее.
                    try { cacheFile.writeBytes(downloaded) } catch (e: Exception) {
                        AppLog.w("VKApiClient", "messagesSendStickerAsImage: cache write failed: ${e.message}")
                    }
                    AppLog.d("VKApiClient", "messagesSendStickerAsImage: cache miss → downloaded ${downloaded.size} bytes")
                    downloaded
                }

                // 2. Получить upload URL для photos messages.
                val uploadUrl = photosGetMessagesUploadServer(peerId) ?: run {
                    AppLog.w("VKApiClient", "messagesSendStickerAsImage: getMessagesUploadServer failed")
                    return@withContext -1L
                }

                // 3. POST multipart upload.
                // Fix #225: определяем формат по magic bytes, а не хардкодим image/png.
                // VK стикеры могут быть JPEG (sun1-XX.userapi.com/...jpg) или PNG.
                // При неверном Content-Type VK возвращает photo="" → отправка фейлится.
                val (mimeType, ext) = detectImageMimeAndExt(bytes)
                AppLog.d("VKApiClient", "messagesSendStickerAsImage: detected format=$mimeType, ${bytes.size} bytes, first4=${bytes.take(4).map { "%02x".format(it) }}")
                val mediaType = mimeType.toMediaType()
                val requestBody = bytes.toRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo", "sticker.$ext", requestBody)
                    .build()
                val uploadReq = Request.Builder().url(uploadUrl).post(multipart).build()
                val uploaded = httpClient.newCall(uploadReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.w("VKApiClient", "messagesSendStickerAsImage: upload HTTP ${resp.code}")
                        return@withContext -1L
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JsonParser.parseString(body).asJsonObject
                    val server = json.get("server")?.takeIf { !it.isJsonNull }?.asInt ?: -1
                    val photo = json.get("photo")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val hash = json.get("hash")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    if (photo.isEmpty() || hash.isEmpty()) {
                        AppLog.w("VKApiClient", "messagesSendStickerAsImage: empty photo/hash: $body")
                        return@withContext -1L
                    }
                    Triple(server, photo, hash)
                }

                // 4. Сохранить фото.
                val attachment = photosSaveMessagePhoto(uploaded.first, uploaded.second, uploaded.third) ?: run {
                    AppLog.w("VKApiClient", "messagesSendStickerAsImage: saveMessagesPhoto failed")
                    return@withContext -1L
                }
                AppLog.i("VKApiClient", "messagesSendStickerAsImage: attachment=$attachment, sending to peer=$peerId")

                // 5. Отправить messages.send.
                val randomId = randomIdCounter.incrementAndGet().toString()
                val sendJson = call("messages.send", mapOf(
                    "peer_id" to peerId.toString(),
                    "attachment" to attachment,
                    "random_id" to randomId,
                )) ?: return@withContext -1L

                sendJson.getAsJsonObject("response")?.getAsJsonArray("items")
                    ?.firstOrNull()?.asJsonObject?.get("message_id")?.asLong
                    ?: sendJson.getAsJsonObject("response")?.get("message_id")?.asLong
                    ?: -1L
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "messagesSendStickerAsImage error", e)
                -1L
            }
        }
    }

    // ========================================================================
    //  P5.3: Отправка существующих вложений (audio/video/gift) в диалоги.
    //  VK messages.send принимает attachment строку вида:
    //    audio{ownerId}_{id}            — аудиозапись из библиотеки
    //    video{ownerId}_{id}_{accessKey} — видео из библиотеки
    //    gift{ownerId}_{id}              — подарок (только для платных подарков
    //                                       нужен голоса; gifts.send резолвит оплату)
    // ========================================================================

    /**
     * P5.3: Отправить аудиозапись в диалог.
     * Использует messages.send с attachment="audio{ownerId}_{id}".
     * @return message_id или -1L при ошибке.
     */
    suspend fun sendAudioToChat(peerId: Long, audioOwnerId: Long, audioId: Long,
                                accessKey: String? = null): Long {
        if (isOffline()) return -1L
        val attachment = "audio${audioOwnerId}_${audioId}" +
            (accessKey?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: "")
        return sendWithAttachment(peerId, attachment)
    }

    /**
     * P5.3: Отправить видео в диалог.
     * Использует messages.send с attachment="video{ownerId}_{id}_{accessKey}".
     * @return message_id или -1L при ошибке.
     */
    suspend fun sendVideoToChat(peerId: Long, video: Video): Long {
        if (isOffline()) return -1L
        val attachment = "video${video.ownerId}_${video.id}" +
            (video.accessKey?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: "")
        return sendWithAttachment(peerId, attachment)
    }

    /**
     * Fix #297: Загрузить видеофайл с телефона на сервер VK и отправить в диалог.
     *
     * Трёх-шаговый pipeline VK API:
     *  1. `video.save(name, is_private=1)` → `{upload_url, video_id, owner_id}`
     *  2. POST файла на `upload_url` (multipart, с прогресс-колбэком)
     *     → сервер подтверждает upload (video_id уже известен из шага 1)
     *  3. `messages.send(peer_id, attachment="video{owner_id}_{video_id}")`
     *
     * `is_private=1` обязательно для видео, отправляемых в личные сообщения —
     * иначе VK может отклонить как «публичное видео без прав».
     *
     * @param peerId       кому отправляем
     * @param file         локальный видеофайл (mp4/avi/mov/…)
     * @param displayName  имя для видео на VK (по умолчанию — имя файла)
     * @param onProgress   колбэк прогресса (bytesWritten, totalBytes, fraction 0..1)
     * @return message_id нового сообщения или -1L при ошибке
     */
    suspend fun uploadAndSendVideo(
        peerId: Long,
        file: java.io.File,
        displayName: String = file.name,
        onProgress: (bytesWritten: Long, totalBytes: Long, fraction: Float) -> Unit = { _, _, _ -> },
    ): Long {
        if (isOffline()) return -1L
        val name = displayName.substringBeforeLast('.')
        AppLog.i("VKApiClient", "uploadAndSendVideo: name=$name size=${file.length()}B peer=$peerId")

        // Шаг 1: video.save → upload_url + video_id + owner_id
        val saveArgs = mutableMapOf(
            "name" to name,
            "is_private" to "1", // обязательно для сообщений
        )
        val saveJson = call("video.save", saveArgs) ?: run {
            AppLog.w("VKApiClient", "uploadAndSendVideo: video.save returned null")
            return -1L
        }
        val resp = saveJson.getAsJsonObject("response") ?: run {
            AppLog.w("VKApiClient", "uploadAndSendVideo: video.save no response: $saveJson")
            return -1L
        }
        val uploadUrl = resp.get("upload_url")?.takeIf { !it.isJsonNull }?.asString ?: run {
            AppLog.w("VKApiClient", "uploadAndSendVideo: no upload_url in $resp")
            return -1L
        }
        val videoId = resp.get("video_id")?.takeIf { !it.isJsonNull }?.asLong ?: run {
            AppLog.w("VKApiClient", "uploadAndSendVideo: no video_id in $resp")
            return -1L
        }
        val ownerId = resp.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong
            ?: exchangeAuthRepository?.userId() ?: 0L
        AppLog.i("VKApiClient", "uploadAndSendVideo: video.save ok ownerId=$ownerId videoId=$videoId uploadUrl=${uploadUrl.take(80)}…")

        // Шаг 2: POST файла на upload_url с прогресс-колбэком
        val uploaded = withContext(Dispatchers.IO) {
            try {
                val mime = guessMimeType(file.name).ifBlank { "video/mp4" }
                val baseBody = file.asRequestBody(mime.toMediaType())
                val progressBody = ProgressRequestBody(baseBody, onProgress)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("video_file", file.name, progressBody)
                    .build()
                val req = Request.Builder()
                    .url(uploadUrl)
                    .header("Origin", VKEndpoints.WEB_ORIGIN)
                    .header("Referer", VKEndpoints.WEB_REFERER)
                    .header("User-Agent", VKEndpoints.WEB_BROWSER_UA)
                    .post(multipart)
                    .build()
                httpClient.newCall(req).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        AppLog.w("VKApiClient", "uploadAndSendVideo: upload HTTP ${response.code}: ${body?.take(300)}")
                        return@withContext false
                    }
                    // VK upload server обычно возвращает JSON {size:..} или пустой ответ
                    AppLog.i("VKApiClient", "uploadAndSendVideo: upload ok (${body?.length ?: 0} bytes response)")
                    true
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "uploadAndSendVideo: upload exception", e)
                false
            }
        }
        if (!uploaded) return -1L

        // Шаг 3: отправляем сообщение с attachment="video{ownerId}_{videoId}"
        val attachment = "video${ownerId}_$videoId"
        AppLog.i("VKApiClient", "uploadAndSendVideo: sending message attachment=$attachment")
        return sendWithAttachment(peerId, attachment)
    }

    /**
     * P5.3: Каталог подарков для выбора пользователем.
     * VK gifts.getCatalog возвращает список доступных подарков с sticker-превью.
     * @param userId ID пользователя, для которого запрашивается каталог (влияет на рекомендации).
     */
    suspend fun giftsGetCatalog(userId: Long? = null): List<GiftItem> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "extended" to "1",
        )
        if (userId != null) args["user_id"] = userId.toString()
        val json = call("gifts.getCatalog", args) ?: return emptyList()
        return try {
            // Структура ответа gifts.getCatalog: response — массив категорий,
            // каждая категория содержит items[].gift{id, sticker}
            val arr = json.getAsJsonObject("response")?.getAsJsonArray("items")
                ?: json.getAsJsonArray("response")
                ?: return emptyList()
            val result = mutableListOf<GiftItem>()
            for (catEl in arr) {
                if (!catEl.isJsonObject) continue
                val cat = catEl.asJsonObject
                val items = cat.getAsJsonArray("items") ?: continue
                for (itemEl in items) {
                    if (!itemEl.isJsonObject) continue
                    val item = itemEl.asJsonObject
                    val giftObj = item.getAsJsonObject("gift") ?: item
                    val giftId = giftObj.get("id")?.asLong ?: continue
                    val sticker = giftObj.getAsJsonObject("sticker")
                    val images = sticker?.getAsJsonArray("images")
                    val thumb256 = images?.firstOrNull {
                        it.asJsonObject?.get("width")?.asInt == 256
                    }?.asJsonObject?.get("url")?.asString
                        ?: images?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    result.add(GiftItem(
                        id = giftId,
                        thumbUrl = thumb256,
                        // gifts.getCatalog возвращает price в votes (голосах).
                        priceVotes = item.getAsJsonObject("price")?.get("votes")?.asInt,
                    ))
                }
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "giftsGetCatalog parse error", e)
            emptyList()
        }
    }

    /**
     * P5.3: Отправить подарок пользователю.
     * VK gifts.send(user_id, gift_id, guid, message?).
     * Платный подарок списывает голоса с баланса отправителя.
     * @return 1 при успехе, -1L при ошибке.
     */
    suspend fun giftsSend(userId: Long, giftId: Long, message: String = ""): Long {
        if (isOffline()) return -1L
        val args = mutableMapOf(
            "user_id" to userId.toString(),
            "gift_id" to giftId.toString(),
            "guid" to randomIdCounter.incrementAndGet().toString(),
        )
        if (message.isNotBlank()) args["message"] = message
        val json = call("gifts.send", args) ?: return -1L
        return try {
            json.getAsJsonObject("response")?.get("gift_id")?.asLong
                ?: json.getAsJsonObject("response")?.get("success")?.takeIf { it.asInt == 1 }?.let { 1L }
                ?: -1L
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "giftsSend error", e)
            -1L
        }
    }

    /** wall.get — посты со стены пользователя или группы.
     *  @param ownerId ID владельца (отрицательный для групп, null = текущий) */
    suspend fun wallGet(ownerId: Long? = null, count: Int = 30, offset: Int = 0): List<Post> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "filter" to "all",
            "extended" to "1",
        )
        if (ownerId != null) args["owner_id"] = ownerId.toString()
        val json = call("wall.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            // Fix #53: защитная дедупликация по (ownerId, id) — иногда VK возвращает
            // закреплённый пост и в начале списка, и в основной ленте одновременно.
            val seenKeys = HashSet<Pair<Long, Long>>()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val post = parsePostMini(el.asJsonObject)
                if (post.id <= 0L || post.ownerId == 0L) return@mapNotNull null
                if (!seenKeys.add(post.ownerId to post.id)) {
                    AppLog.d("VKApiClient", "wallGet: skip duplicate post owner=${post.ownerId} id=${post.id}")
                    return@mapNotNull null
                }
                post
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallGet parse error", e)
            emptyList()
        }
    }

    /** users.getFollowers — подписчики пользователя. */
    suspend fun usersGetFollowers(userId: Long? = null, count: Int = 50, offset: Int = 0): List<UserProfile> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "count" to count.toString(),
            "offset" to offset.toString(),
            "fields" to "photo_100,photo_200,online,last_seen,status,verified",
        )
        if (userId != null) args["user_id"] = userId.toString()
        val json = call("users.getFollowers", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseUserProfileMini(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGetFollowers parse error", e)
            emptyList()
        }
    }

    /**
     * groups.join — вступить в сообщество.
     *
     * §37.12 #324: идемпотентный — VK возвращает err=15 "Access denied: you are
     * already in this community" если пользователь уже участник. Трактуем это как
     * success (цель "быть участником" достигнута). Без этого toggle-subscribe на
     * clip-автора, на которого уже подписан, показывал бы "Не удалось" вместо OK.
     */
    suspend fun groupsJoin(groupId: Long): Boolean {
        val args = mapOf("group_id" to groupId.toString())
        val json = call("groups.join", args)
        if (json != null) {
            return try {
                // Fix #350: VK API groups.join возвращает либо {"response":{"success":1}}
                // (полный ответ), либо {"response":1} (короткий). Принимаем оба.
                val resp = json.get("response")
                if (resp != null && !resp.isJsonNull) {
                    if (resp.isJsonObject) {
                        resp.asJsonObject.get("success")?.asInt == 1
                    } else {
                        // Простой integer — success если != 0.
                        resp.asInt != 0
                    }
                } else false
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "groupsJoin parse error", e)
                false
            }
        }
        // §37.12 #324: call вернул null — проверяем lastApiErrorCode.
        // err=15 ("already in community") → идемпотентный success.
        // err=6 ("too many requests per second") → НЕ success (нужно retry).
        if (lastApiErrorCode == 15) {
            AppLog.d("VKApiClient", "groupsJoin($groupId): err=15 (already member) — treating as success (idempotent)")
            return true
        }
        return false
    }

    /** groups.leave — покинуть сообщество. */
    suspend fun groupsLeave(groupId: Long): Boolean {
        val args = mapOf("group_id" to groupId.toString())
        val json = call("groups.leave", args) ?: return false
        return try {
            // Fix #350: VK API groups.leave возвращает {"response":1} (примитив),
            // а НЕ {"response":{"success":1}}. Старый парсинг через
            // getAsJsonObject("response")?.get("success") всегда возвращал null → false,
            // из-за чего кнопка подписки в GroupsScreen/CommunityScreen не обновляла
            // состояние после отписки. Теперь принимаем оба формата.
            val resp = json.get("response")
            if (resp != null && !resp.isJsonNull) {
                if (resp.isJsonObject) {
                    resp.asJsonObject.get("success")?.asInt == 1
                } else {
                    // Простой integer — success если != 0.
                    resp.asInt != 0
                }
            } else false
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "groupsLeave parse error", e)
            false
        }
    }

    /** friends.add — добавить в друзья (или принять заявку). */
    suspend fun friendsAdd(userId: Long, text: String? = null): Int {
        val args = mutableMapOf("user_id" to userId.toString())
        if (text != null) args["text"] = text
        val json = call("friends.add", args) ?: return -1
        return try {
            json.getAsJsonObject("response")?.get("friend_status")?.asInt ?: -1
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "friendsAdd parse error", e)
            -1
        }
    }

    /** friends.delete — удалить из друзей. */
    suspend fun friendsDelete(userId: Long): Boolean {
        val args = mapOf("user_id" to userId.toString())
        val json = call("friends.delete", args) ?: return false
        return try {
            json.getAsJsonObject("response")?.get("success")?.asInt == 1
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "friendsDelete parse error", e)
            false
        }
    }

    // ------------------------------------------------------------------------
    //  Вспомогательные парсеры — вынесены из повторяющихся блоков выше.
    // ------------------------------------------------------------------------

    /** #67: парсинг Message из JSON (для reply_message, fwd_messages). */
    private fun parseMessage(o: JsonObject): Message {
        return Message(
            id = o.get("id")?.asLong ?: 0L,
            peerId = o.get("peer_id")?.asLong ?: 0L,
            fromId = o.get("from_id")?.asLong ?: 0L,
            date = o.get("date")?.asLong ?: 0L,
            text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: "",
            out = o.get("out")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            readState = o.get("read_state")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            deleted = o.get("deleted")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            edited = o.get("edited")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            originalText = o.get("original_text")?.takeIf { !it.isJsonNull }?.asString,
            // Fix #203: cmid для reply_message/fwd_messages (на случай reply на reply).
            conversationMessageId = o.get("conversation_message_id")
                ?.takeIf { !it.isJsonNull }?.asLong,
            attachments = parseAttachments(o),
            replyMessage = o.getAsJsonObject("reply_message")?.let { parseMessage(it) },
            fwdMessages = o.getAsJsonArray("fwd_messages")?.mapNotNull { fm ->
                if (!fm.isJsonObject) null else parseMessage(fm.asJsonObject)
            }?.takeIf { it.isNotEmpty() },
            action = o.get("action")?.takeIf { !it.isJsonNull }?.asString,
            actionText = o.get("action_text")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    /**
     * #30h / Fix #234: парсинг комментария (общий для wall.getComments,
     * video.getComments). Теперь парсит ВСЕ поля VK API 5.243:
     *  - id, from_id, date, text, likes
     *  - reply_to_user, reply_to_comment (контекст ответа)
     *  - attachments (фото/видео/аудио/документы в комментариях)
     *  - parents_stack (цепочка предков для глубоких тредов)
     *  - thread.items (превью ветки ответов, если запрошено через
     *    thread_items_count=N в wall.getComments)
     *
     * Рекурсивен: thread.items сам парсится через parseComment.
     */
    private fun parseComment(o: JsonObject): Comment {
        // Fix #237: ВСЕ числовые поля парсятся через safeInt/safeLong, т.к.
        // VK web-API (vk1.a.*) возвращает булевы поля (can_like, can_post,
        // show_reply_button, deleted) как JSON true/false. Раньше сырые
        // .asInt/.asLong падали с NumberFormatException: For input string:
        // "true" → wallGetComments возвращал пустой список → комментарии в
        // постах вообще не отображались.
        return Comment(
            id = safeLong(o.get("id")),
            fromId = safeLong(o.get("from_id")),
            date = safeLong(o.get("date")),
            text = safeString(o.get("text")) ?: "",
            likes = o.getAsJsonObject("likes")?.let { l ->
                Post.Likes(
                    count = safeInt(l.get("count")),
                    userLikes = safeInt(l.get("user_likes")),
                    canLike = safeInt(l.get("can_like"), 1),
                )
            },
            // Fix #237: reply_to_user / reply_to_comment — Long, но VK может
            // отдать 0 или вообще omit. safeLong возвращает 0L для null/false.
            // Для nullable-семантики оборачиваем: 0 → null (нет ответа).
            replyToUser = safeLong(o.get("reply_to_user")).takeIf { it != 0L },
            replyToComment = safeLong(o.get("reply_to_comment")).takeIf { it != 0L },
            // Fix #234: attachments в комментариях раньше терялись → фото/видео/аудио
            // в комментариях не отображались. Теперь парсятся тем же хелпером, что
            // и для сообщений/постов.
            attachments = parseAttachments(o),
            // Fix #234: цепочка ID предков (для глубоких ответов на ответ на ответ).
            parentsStack = o.getAsJsonArray("parents_stack")
                ?.takeIf { it.size() > 0 }
                ?.mapNotNull { el -> safeLong(el).takeIf { !el.isJsonNull && it != 0L } },
            // Fix #234: превью ветки ответов. VK отдаёт thread только при явном
            // thread_items_count>0 в запросе. thread.items парсятся рекурсивно.
            // Fix #237: can_post/show_reply_button теперь через safeBool (VK web
            // отдаёт их как true/false).
            thread = o.getAsJsonObject("thread")?.let { t ->
                Comment.CommentThread(
                    count = safeInt(t.get("count")),
                    items = t.getAsJsonArray("items")?.mapNotNull { el ->
                        if (!el.isJsonObject) null else parseComment(el.asJsonObject)
                    } ?: emptyList(),
                    canPost = safeBool(t.get("can_post")),
                    showReplyButton = safeBool(t.get("show_reply_button"), true),
                )
            },
        )
    }

    private fun parseUserProfileMini(o: JsonObject): UserProfile {
        val countersObj = o.getAsJsonObject("counters")
        return UserProfile(
            id = o.get("id")?.asLong ?: 0L,
            firstName = o.get("first_name")?.asString ?: "",
            lastName = o.get("last_name")?.asString ?: "",
            photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
            photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
            online = o.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            lastSeen = o.get("last_seen")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                UserProfile.LastSeen(
                    time = it.get("time")?.asLong ?: 0L,
                    platform = it.get("platform")?.takeIf { x -> !x.isJsonNull }?.asInt,
                )
            },
            status = o.get("status")?.takeIf { !it.isJsonNull }?.asString,
            bdate = o.get("bdate")?.takeIf { !it.isJsonNull }?.asString,
            city = o.get("city")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                UserProfile.City(it.get("title")?.asString ?: "")
            },
            verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            followersCount = countersObj?.get("followers")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
        )
    }

    private fun parseGroupMini(o: JsonObject): Group {
        return Group(
            id = o.get("id")?.asLong ?: 0L,
            name = o.get("name")?.asString ?: "",
            screenName = o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString,
            isClosed = o.get("is_closed")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            type = o.get("type")?.takeIf { !it.isJsonNull }?.asString,
            photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
            photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
            membersCount = o.get("members_count")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            verified = o.get("verified")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            isMember = o.get("is_member")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
        )
    }

    private fun parsePhotoItem(el: com.google.gson.JsonElement): PhotoItem? {
        if (!el.isJsonObject) return null
        val o = el.asJsonObject
        val sizes = o.getAsJsonArray("sizes")?.mapNotNull { s ->
            if (!s.isJsonObject) return@mapNotNull null
            val so = s.asJsonObject
            Attachment.Photo.Size(
                url = so.get("url")?.asString ?: "",
                width = so.get("width")?.asInt ?: 0,
                height = so.get("height")?.asInt ?: 0,
                type = so.get("type")?.asString ?: "",
            )
        }
        return PhotoItem(
            id = o.get("id")?.asLong ?: return null,
            ownerId = o.get("owner_id")?.asLong ?: 0L,
            albumId = o.get("album_id")?.asLong ?: 0L,
            date = o.get("date")?.asLong ?: 0L,
            text = o.get("text")?.takeIf { !it.isJsonNull }?.asString,
            sizes = sizes,
            accessKey = o.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    // Fix #70: общий helper для парсинга attachments[] поста.
    // Применяется в newsfeedGet (лента) и parsePostMini (wall.get для профиля/сообщества).
    // Поддерживаемые типы: photo, video, link. Остальные (audio, doc, poll, ...)
    // попадают в else-ветку — тип сохраняется, но внутренний объект не парсится
    // (UI рендерит их как «Вложение: <type>»).
    private fun parseAttachments(o: JsonObject): List<Attachment>? {
        val attachList = o.getAsJsonArray("attachments") ?: return null
        return parseAttachmentsArray(attachList)
    }

    /** Fix #99: парсинг массива вложений (для messages.getHistory). */
    private fun parseAttachmentsArray(attachList: JsonArray): List<Attachment>? {
        // Fix #47: VK web API (vk1.a.* token) возвращает richer format — некоторые
        // поля могут быть JsonObject вместо String/Int/Long. Все .asString/.asInt/.asLong
        // заменены на safeString/safeInt/safeLong (companion object) — не бросают
        // UnsupportedOperationException на JsonObject/JsonArray.
        //
        // Fix #49-3: per-item try/catch. РАНЬШЕ один битый attachment (неожиданная
        // форма JsonObject → UnsupportedOperationException) пробрасывался наверх,
        // ловился в newsfeedGet/wallGet → весь post.attachments становился null →
        // у поста пропадали все вложения (аудио/плейлисты и т.д.). Теперь один
        // attachment может упасть — он логируется и пропускается, остальные парсятся.
        val result = attachList.mapNotNull { a ->
            try {
                if (!a.isJsonObject) return@mapNotNull null
                val aObj = a.asJsonObject
                val type = safeString(aObj.get("type")) ?: return@mapNotNull null
                when (type) {
                    "photo" -> {
                        val p = aObj.getAsJsonObject("photo") ?: return@mapNotNull null
                        val photoSizes = p.getAsJsonArray("sizes")?.mapNotNull { s ->
                            if (!s.isJsonObject) return@mapNotNull null
                            val so = s.asJsonObject
                            Attachment.Photo.Size(
                                url = safeString(so.get("url")) ?: "",
                                width = safeInt(so.get("width")),
                                height = safeInt(so.get("height")),
                                type = safeString(so.get("type")) ?: "",
                            )
                        }
                        Attachment(
                            type = type,
                            photo = Attachment.Photo(
                                id = safeLong(p.get("id")),
                                ownerId = safeLong(p.get("owner_id")),
                                sizes = photoSizes ?: emptyList(),
                                text = safeString(p.get("text")),
                            ),
                        )
                    }
                    "video" -> {
                        val vEl = aObj.getAsJsonObject("video") ?: return@mapNotNull null
                        // #WALL-CLIPS: раньше парсили Video вручную и ТЕРЯЛИ клипы —
                        // клип в wall.get приходит как type="video" с type="clip"/is_clips=1
                        // и постером в covers[]/first_frames[] (а не image[]/photo_*).
                        // parseVideoFull читает всё: type, is_clips, covers→image,
                        // first_frames, engagement→likes/reposts/comments, files.
                        val parsedVideo = parseVideoFull(vEl)
                        // OK-IMPL-1 (Stage 1): определяем платформу + externalId
                        // для OK/YouTube видео в attachments ленты. Это ГЛАВНАЯ
                        // точка входа OK-видео в приложение — feed → VideoHolder.open.
                        Attachment(type = type, video = parsedVideo.withDetectedPlatform())
                    }
                    // #WALL-CLIPS: тип "short_video" (как в newsfeed.getFeed, §37.12)
                    // — нормализуем в "video" с is_clips, чтобы существующие рендеры
                    // (CommunityPostCard/FeedScreen) подхватили клип.
                    "short_video" -> {
                        val s = aObj.getAsJsonObject("short_video") ?: return@mapNotNull null
                        val parsed = parseVideoFull(s).withDetectedPlatform()
                        Attachment(type = "video", video = parsed)
                    }
                    "link" -> {
                        val l = aObj.getAsJsonObject("link") ?: return@mapNotNull null
                        Attachment(type = type, link = Attachment.Link(
                            url = safeString(l.get("url")) ?: "",
                            title = safeString(l.get("title")),
                            description = safeString(l.get("description")),
                        ))
                    }
                    // Fix #49-4: page (VK wiki-страницы). Часто используются как alias
                    // для ссылок на статьи. Рендерим как LinkCard (переиспользуем Link).
                    "page" -> {
                        val pg = aObj.getAsJsonObject("page") ?: return@mapNotNull null
                        val pageUrl = safeString(pg.get("view_url"))
                            ?: safeString(pg.get("url")) ?: ""
                        Attachment(type = type, link = Attachment.Link(
                            url = pageUrl,
                            title = safeString(pg.get("title")),
                            description = safeString(pg.get("description")),
                        ))
                    }
                    "wall" -> {
                        // Fix #99: wall-вложение в сообщениях (репост поста).
                        val w = aObj.getAsJsonObject("wall") ?: return@mapNotNull null
                        Attachment(type = type, wall = parsePostMini(w))
                    }
                    "poll" -> {
                        val p = aObj.getAsJsonObject("poll") ?: return@mapNotNull null
                        Attachment(type = type, poll = parsePoll(p))
                    }
                    "audio" -> {
                        val a = aObj.getAsJsonObject("audio") ?: return@mapNotNull null
                        Attachment(type = type, audio = Track(
                            id = safeLong(a.get("id")),
                            ownerId = safeLong(a.get("owner_id")),
                            artist = safeString(a.get("artist")) ?: "",
                            title = safeString(a.get("title")) ?: "",
                            duration = safeInt(a.get("duration")),
                            url = safeString(a.get("url")),
                            albumId = safeLong(a.get("album_id")).takeIf { it != 0L },
                            albumThumb = safeString(a.get("album_thumb")),
                            accessKey = safeString(a.get("access_key")),
                            lyricsId = safeLong(a.get("lyrics_id")).takeIf { it != 0L },
                        ))
                    }
                    // #30 (playlists): audio_playlist как вложение поста.
                    "audio_playlist" -> {
                        val p = aObj.getAsJsonObject("audio_playlist") ?: return@mapNotNull null
                        Attachment(type = type, audioPlaylist = re.pinok.data.model.AudioPlaylist(
                            id = safeLong(p.get("id")),
                            ownerId = safeLong(p.get("owner_id")),
                            title = safeString(p.get("title")) ?: "",
                            description = safeString(p.get("description")),
                            photo = safeString(p.get("photo")),
                            photo200 = safeString(p.get("photo_200")),
                            photo300 = safeString(p.get("photo_300")),
                            photo600 = safeString(p.get("photo_600")),
                            count = safeInt(p.get("count")),
                            accessKey = safeString(p.get("access_key")),
                            type = safeString(p.get("type")),
                        ))
                    }
                    "doc" -> {
                        val d = aObj.getAsJsonObject("doc") ?: return@mapNotNull null
                        // Fix #125: парсим audio_msg внутри doc (legacy формат голосовых).
                        // Раньше audio_msg НЕ парсился → Doc.isVoiceMessage всегда false →
                        // голосовые рендерились как обычный файл-карточка вместо плеера.
                        val audioMsg = d.getAsJsonObject("audio_msg")?.let { am ->
                            Attachment.Doc.AudioMsg(
                                duration = safeInt(am.get("duration")),
                                linkOgg = safeString(am.get("link_ogg")),
                                linkMp3 = safeString(am.get("link_mp3")),
                                waveform = am.getAsJsonArray("waveform")?.mapNotNull { w ->
                                    if (w.isJsonPrimitive) w.asInt else null
                                },
                            )
                        }
                        Attachment(type = type, doc = Attachment.Doc(
                            id = safeLong(d.get("id")),
                            ownerId = safeLong(d.get("owner_id")),
                            title = safeString(d.get("title")) ?: "",
                            size = safeLong(d.get("size")),
                            ext = safeString(d.get("ext")) ?: "",
                            url = safeString(d.get("url")) ?: "",
                            accessKey = safeString(d.get("access_key")),
                            audioMsg = audioMsg,
                        ))
                    }
                    // Fix #125: audio_message — стандартный формат голосовых сообщений VK.
                    // Приходит как type="audio_message" с полем audio_message.
                    // Раньше ветки НЕ было → fall-through в else → пустой Attachment →
                    // VoiceMessageBubble никогда не вызывался.
                    "audio_message" -> {
                        val am = aObj.getAsJsonObject("audio_message") ?: return@mapNotNull null
                        Attachment(
                            type = type,
                            audioMessage = Attachment.Doc.AudioMsg(
                                duration = safeInt(am.get("duration")),
                                linkOgg = safeString(am.get("link_ogg")),
                                linkMp3 = safeString(am.get("link_mp3")),
                                waveform = am.getAsJsonArray("waveform")?.mapNotNull { w ->
                                    if (w.isJsonPrimitive) w.asInt else null
                                },
                            ),
                        )
                    }
                    // Fix #219: парсинг type=sticker (входящие стикеры в messages.getHistory).
                    // РАНЬШЕ ветки НЕ было → fall-through в else → Attachment(type="sticker",
                    // sticker=null). Рендер в MessageBubble (ChatDetailScreen.kt:3434-3448)
                    // проверяет `it.sticker != null` → всегда false → стикер невидим в диалоге.
                    // Пикер работает, отправка работает (отдельный код), но приём — нет.
                    // Паттерн скопирован из storeGetStickerPacks (см. строки ~5958-5992).
                    "sticker" -> {
                        val s = aObj.getAsJsonObject("sticker") ?: return@mapNotNull null
                        Attachment(
                            type = type,
                            sticker = re.pinok.data.model.StickerAttachment(
                                stickerId = safeInt(s.get("sticker_id")),
                                productId = safeInt(s.get("product_id")),
                                images = s.getAsJsonArray("images")?.mapNotNull { img ->
                                    if (!img.isJsonObject) null else {
                                        val io = img.asJsonObject
                                        re.pinok.data.model.StickerImage(
                                            url = safeString(io.get("url")) ?: return@mapNotNull null,
                                            width = safeInt(io.get("width")).takeIf { it > 0 } ?: 64,
                                            height = safeInt(io.get("height")).takeIf { it > 0 } ?: 64,
                                        )
                                    }
                                },
                                imagesWithBackground = s.getAsJsonArray("images_with_background")?.mapNotNull { img ->
                                    if (!img.isJsonObject) null else {
                                        val io = img.asJsonObject
                                        re.pinok.data.model.StickerImage(
                                            url = safeString(io.get("url")) ?: return@mapNotNull null,
                                            width = safeInt(io.get("width")).takeIf { it > 0 } ?: 256,
                                            height = safeInt(io.get("height")).takeIf { it > 0 } ?: 256,
                                        )
                                    }
                                },
                                // Fix #229: анимированные стикеры во входящих сообщениях.
                                animationUrl = safeString(s.get("animation_url")),
                            ),
                        )
                    }
                    else -> Attachment(type = type)
                }
            } catch (e: Exception) {
                // Fix #49-3: один битый attachment НЕ должен валить весь post.
                AppLog.w("VKApiClient", "parseAttachments: skip bad attachment: ${e.message}")
                null
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parsePostMini(o: JsonObject): Post {
        // Fix #70: ранее здесь attachments и copy_history НЕ парсились →
        // все посты на стене профиля имели attachments=null → фото/видео/репосты
        // не отображались, посты выглядели пустыми (только текст + статистика).
        // Fix #47: все .asLong/.asInt/.asString заменены на safe-хелперы.
        return Post(
            id = safeLong(o.get("id")),
            ownerId = safeLong(o.get("owner_id")),
            fromId = safeLong(o.get("from_id")).takeIf { it != 0L }
                ?: safeLong(o.get("owner_id")), // VK web-token может не возвращать from_id
            signerId = safeLong(o.get("signer_id")).takeIf { it != 0L },
            date = safeLong(o.get("date")),
            text = safeString(o.get("text")) ?: "",
            attachments = parseAttachments(o),
            // copy_history парсим рекурсивно, глубина ограничена VK API (обычно 1).
            copyHistory = o.getAsJsonArray("copy_history")?.mapNotNull { ch ->
                if (!ch.isJsonObject) null else parsePostMini(ch.asJsonObject)
            }?.takeIf { it.isNotEmpty() },
            likes = o.getAsJsonObject("likes")?.let {
                Post.Likes(
                    count = safeInt(it.get("count")),
                    userLikes = safeInt(it.get("user_likes")),
                    canLike = safeInt(it.get("can_like"), 1),
                )
            },
            reposts = o.getAsJsonObject("reposts")?.let {
                Post.Reposts(
                    count = safeInt(it.get("count")),
                    userReposted = safeInt(it.get("user_reposted")),
                )
            },
            views = o.getAsJsonObject("views")?.let { Post.Views(safeInt(it.get("count"))) },
            comments = o.getAsJsonObject("comments")?.let {
                Post.Comments(
                    count = safeInt(it.get("count")),
                    canPost = safeInt(it.get("can_post"), 1),
                )
            },
            postType = safeString(o.get("post_type")),
            markedAsAds = safeInt(o.get("marked_as_ads")),
            isPinned = safeInt(o.get("is_pinned")),
            // --- SOVA_2_lenta: новые поля ---
            isFavorite = safeBool(o.get("is_favorite")),
            canEdit = safeBool(o.get("can_edit")),
            canDelete = safeBool(o.get("can_delete")),
            canPin = safeBool(o.get("can_pin")),
            edited = safeLong(o.get("edited")).takeIf { it != 0L },
            isArchived = safeBool(o.get("is_archived")),
            copyright = o.getAsJsonObject("copyright")?.let { c ->
                Post.Copyright(
                    id = safeInt(c.get("id")).takeIf { it != 0 },
                    link = safeString(c.get("link")),
                    name = safeString(c.get("name")),
                    type = safeString(c.get("type")),
                )
            },
            donut = o.getAsJsonObject("donut")?.let { d ->
                Post.Donut(
                    isDonut = safeBool(d.get("is_donut")),
                    paidDuration = safeInt(d.get("paid_duration")).takeIf { it != 0 },
                    placeholder = safeString(d.get("placeholder")),
                    canPublishFreeCopy = safeBool(d.get("can_publish_free_copy")),
                    editMode = safeString(d.get("edit_mode")),
                )
            },
            reactions = o.getAsJsonObject("reactions")?.let { r ->
                Post.Reactions(
                    count = safeInt(r.get("count")).takeIf { it != 0 },
                    userReacted = safeInt(r.get("user_reacted")),
                )
            },
            hash = safeString(o.get("hash")),
            friendsOnly = safeBool(o.get("friends_only")),
            createdBy = safeLong(o.get("created_by")).takeIf { it != 0L },
            postponedId = safeLong(o.get("postponed_id")).takeIf { it != 0L },
            accessKey = safeString(o.get("access_key")),
        )
    }

    private fun parseVideoMini(o: JsonObject): Video {
        return parseVideoFull(o)
    }

    /**
     * §37.12 Phase 1: расширенный парсер Video — включает все clips-поля.
     * Используется везде, где раньше звали parseVideoMini: clips ленты, video.get,
     * attachments в сообщениях и постах. Старые callers получают те же поля +
     * новые clips-specific (reposts, comments, music_info, is_clips, etc.).
     */
    private fun parseVideoFull(o: JsonObject): Video {
        // §37.12 #324: NEW format (short_video_full) использует engagement{} вместо
        // likes{}/views/comments/reposts, access{} вместо can_*, duration_seconds
        // вместо duration, publish_timestamp вместо date, covers[] вместо image[],
        // first_frames[] вместо first_frame[]. Нормализуем к LEGACY-полям Video.
        // §37.12 #325: ИСПОЛЬЗУЕМ getObj() вместо o.getAsJsonObject() — Gson бросает
        // ClassCastException если поле это JsonPrimitive (int/string), а не объект.
        // В NEW формате VK может вернуть comments: 5 (int) вместо {count:5} (obj).
        val engagement = getObj(o, "engagement")
        val access = getObj(o, "access")
        val likesObj = getObj(o, "likes") ?: engagement?.let { eng ->
            // Конвертируем engagement{like_count, ...} → likes{count, user_likes, can_like}
            // user_likes в NEW-формате не возвращается — выводим из is_liked если есть.
            val likeCount = safeInt(eng.get("like_count"))
            val userLikes = safeInt(o.get("is_liked"))
            if (likeCount == 0 && userLikes == 0) null
            else JsonObject().apply {
                addProperty("count", likeCount)
                addProperty("user_likes", userLikes)
                addProperty("can_like", 1)
            }
        }
        val reposts = getObj(o, "reposts")?.let {
            Video.Reposts(
                count = safeInt(it.get("count")),
                userReposted = safeInt(it.get("user_reposted")),
            )
        } ?: engagement?.let { eng ->
            val repostCount = safeInt(eng.get("repost_count"))
            if (repostCount == 0) null
            else Video.Reposts(count = repostCount, userReposted = safeInt(o.get("is_reposted")))
        }
        val comments = getObj(o, "comments")?.let {
            Video.Comments(
                count = safeInt(it.get("count")),
                canPost = safeInt(it.get("can_post"), 1),
            )
        } ?: engagement?.let { eng ->
            val commentCount = safeInt(eng.get("comment_count"))
            if (commentCount == 0) null
            else Video.Comments(count = commentCount, canPost = 1)
        }
        val musicInfo = getObj(o, "music_info")?.let { m ->
            Video.ClipMusic(
                id = safeLong(m.get("id")),
                ownerId = safeLong(m.get("owner_id")),
                artist = safeString(m.get("artist")) ?: "",
                title = safeString(m.get("title")) ?: "",
                duration = safeInt(m.get("duration")),
                url = safeString(m.get("url")),
                isExplicit = m.get("is_explicit")?.takeIf { !it.isJsonNull }?.asInt,
            )
        }
        // §37.12 #324: duration_seconds (NEW) → duration; publish_timestamp (NEW) → date
        val durationVal = if (o.has("duration_seconds")) safeInt(o.get("duration_seconds")) else safeInt(o.get("duration"))
        val dateVal = if (o.has("publish_timestamp")) safeLong(o.get("publish_timestamp")) else safeLong(o.get("date"))
        // §37.12 #324: engagement.view_count (NEW) → views
        val viewsVal = if (o.has("views")) safeInt(o.get("views"))
            else engagement?.let { safeInt(it.get("view_count")) } ?: 0
        // §37.12 #324: id может отсутствовать — fallback на clip_id (alias в NEW-формате)
        val idVal = safeLongNullable(o.get("id")) ?: safeLongNullable(o.get("clip_id")) ?: 0L
        // §37.12 #324: covers[] (NEW) → image[] (через конвертацию в Thumb)
        val imageThumbs = parseVideoThumbs(o) ?: getArr(o, "covers")?.let { coversArr ->
            coversArr.mapNotNull { c ->
                if (!c.isJsonObject) return@mapNotNull null
                val cObj = c.asJsonObject
                val url = safeString(cObj.get("url")) ?: return@mapNotNull null
                val width = if (cObj.has("width")) safeInt(cObj.get("width")) else 0
                val height = if (cObj.has("height")) safeInt(cObj.get("height")) else 0
                Video.Thumb(url = url, width = width, height = height)
            }.takeIf { it.isNotEmpty() }
        }
        // §37.12 #FIRST-FRAME: вертикальные кадры (first_frames[]) — постер клипа.
        // iv.okcdn.ru/getVideoPreview?...type=32/34/39/43 (135x240 … 1080x1920).
        // #WALL-CLIPS: wall.get использует first_frame (ЕДИНСТВЕННОЕ число),
        // short_video_full — first_frames (множественное). Читаем оба.
        val firstFrames = (getArr(o, "first_frames") ?: getArr(o, "first_frame"))
            ?.mapNotNull { f ->
                if (!f.isJsonObject) return@mapNotNull null
                val fObj = f.asJsonObject
                val url = safeString(fObj.get("url")) ?: return@mapNotNull null
                Video.Thumb(
                    url = url,
                    width = if (fObj.has("width")) safeInt(fObj.get("width")) else 0,
                    height = if (fObj.has("height")) safeInt(fObj.get("height")) else 0,
                )
            }?.takeIf { it.isNotEmpty() }
        // §37.12 #324: access{can_like, can_repost, ...} (NEW, bool) → can_* (int 0/1)
        // VK может вернуть bool или 0/1 — safeBool обработает оба варианта.
        val accessCanLike = access?.get("can_like")?.let { if (safeBool(it)) 1 else 0 }
        val accessCanRepost = access?.get("can_repost")?.let { if (safeBool(it)) 1 else 0 }
        val accessCanComment = access?.get("can_comment")?.let { if (safeBool(it)) 1 else 0 }
        val accessCanSubscribe = access?.get("can_subscribe")?.let { if (safeBool(it)) 1 else 0 }
        return Video(
            id = idVal,
            ownerId = safeLong(o.get("owner_id")),
            title = safeString(o.get("title")) ?: "",
            description = safeString(o.get("description")),
            duration = durationVal,
            date = dateVal,
            views = viewsVal,
            player = safeString(o.get("player")),
            files = parseVideoFiles(o),
            accessKey = safeString(o.get("access_key")),
            image = imageThumbs,
            firstFrames = firstFrames,
            // #WALL-CLIPS: размеры кадра — для детекции вертикальных клипов на стене.
            height = safeInt(o.get("height")),
            width = safeInt(o.get("width")),
            likes = parseLikes(likesObj),
            reposts = reposts,
            comments = comments,
            canLike = accessCanLike ?: safeIntNullable(o.get("can_like")),
            canComment = accessCanComment ?: safeIntNullable(o.get("can_comment")),
            canRepost = accessCanRepost ?: safeIntNullable(o.get("can_repost")),
            canSubscribe = accessCanSubscribe ?: safeIntNullable(o.get("can_subscribe")),
            canEdit = safeIntNullable(o.get("can_edit")),
            canDelete = safeIntNullable(o.get("can_delete")),
            canAdd = safeIntNullable(o.get("can_add")),
            canReport = safeIntNullable(o.get("can_report")),
            isFavorite = safeIntNullable(o.get("is_favorite")),
            isSubscribed = safeIntNullable(o.get("is_subscribed")),
            isPrivate = safeIntNullable(o.get("is_private")),
            isLimited = safeIntNullable(o.get("is_limited")),
            isPromoted = safeIntNullable(o.get("is_promoted")),
            isAd = safeIntNullable(o.get("is_ad")),
            isClips = safeIntNullable(o.get("is_clips")) ?: if (o.has("duration_seconds") || engagement != null) 1 else null,
            isLive = safeIntNullable(o.get("is_live")),
            isUpcoming = safeIntNullable(o.get("is_upcoming")),
            repeat = safeIntNullable(o.get("repeat")),
            mute = safeIntNullable(o.get("mute")),
            noSound = safeIntNullable(o.get("no_sound")),
            trackCode = safeString(o.get("track_code")),
            type = safeString(o.get("type")) ?: "video",
            platform = safeString(o.get("platform")),
            added = safeIntNullable(o.get("added")),
            completelyLoaded = safeIntNullable(o.get("completely_loaded")),
            musicInfo = musicInfo,
            // nearest_clips / next_clip / prev_clip парсятся отдельно при необходимости
            storyId = safeLongNullable(o.get("story_id")),
        ).withDetectedPlatform()
    }

    /**
     * Sprint 2, P1-2 (#89): Helper для парсинга объекта likes{count,user_likes,can_like}.
     * Возвращает null если likes-объект отсутствует (нет лайков на объекте).
     */
    private fun parseLikes(likesObj: JsonObject?): Post.Likes? {
        if (likesObj == null) return null
        val count = safeInt(likesObj.get("count"))
        val userLikes = safeInt(likesObj.get("user_likes"))
        val canLike = safeInt(likesObj.get("can_like"), 1)
        return Post.Likes(count = count, userLikes = userLikes, canLike = canLike)
    }

    // Fix #69: общий helper для парсинга files-объекта видео.
    // Применяется в videoGet, parseVideoMini, и в парсере video-аттачмента newsfeed.
    // VK возвращает ключи: mp4_144, mp4_240, mp4_360, mp4_480, mp4_720, mp4_1080,
    // mp4_1440, mp4_2160, hls, dash, dash_sep.
    // §37.12 #325: getObj вместо getAsJsonObject — безопасен против JsonPrimitive.
    private fun parseVideoFiles(o: JsonObject): Map<String, String>? {
        val filesObj = getObj(o, "files") ?: return null
        val map = mutableMapOf<String, String>()
        for ((key, value) in filesObj.entrySet()) {
            if (!value.isJsonPrimitive) continue
            val str = value.asString
            if (str.isNotBlank()) map[key] = str
        }
        return if (map.isEmpty()) null else map
    }

    // Fix #69: общий helper для парсинга image[] (превью видео).
    // §37.12 #325: getArr вместо getAsJsonArray — безопасен против JsonPrimitive.
    // safeString/safeInt вместо ?.asString/?.asInt — безопасны против не-primitive.
    // #VIDEO-FRAME-FIX: VK отдаёт превью ДВУМЯ способами — новый массив image[]
    // и legacy-поля photo_1280/800/640/320/130. Для OK-crossposted и старых видео
    // image[] пуст/отсутствует, а photo_* есть → фрейм не рисовался. Теперь
    // fallback на photo_* по приоритету (высокое → низкое).
    private fun parseVideoThumbs(o: JsonObject): List<Video.Thumb>? {
        val arr = getArr(o, "image")
        if (arr != null && arr.size() > 0) {
            return arr.mapNotNull { img ->
                if (!img.isJsonObject) return@mapNotNull null
                val io = img.asJsonObject
                val url = safeString(io.get("url")) ?: return@mapNotNull null
                Video.Thumb(
                    url = url,
                    width = safeInt(io.get("width")),
                    height = safeInt(io.get("height")),
                )
            }.takeIf { it.isNotEmpty() }
        }
        // Fallback: legacy-поля photo_* (размеры неизвестны → width/height 0).
        val legacyUrl = listOf("photo_1280", "photo_800", "photo_640", "photo_320", "photo_130")
            .firstNotNullOfOrNull { safeString(o.get(it)) }
        return legacyUrl?.let { listOf(Video.Thumb(url = it, width = 0, height = 0)) }
    }

    @Volatile
    override var lastApiError: String? = null
        private set

    @Volatile
    override var lastApiErrorCode: Int = 0
        private set

    // ── #38: Auto-offline после N последовательных сетевых неудач ──────────
    // Если VK API упал (IOException/timeout/DNS) но интернет на устройстве есть
    // (captive portal, VK IP-блок, сервер лежит) — после MAX_CONSECUTIVE_NET_ERRORS
    // ошибок в течение NET_ERROR_WINDOW_MS автоматически включаем privacyOfflineMode.
    // UI реактивно обновится (FeedScreen collectAsState) + все API-методы начнут
    // возвращать empty (isOffline()=true). Сброс счётчика — на первом успешном
    // ответе. Пользователь может выйти из авто-офлайна: drawer → «Офлайн» → кнопка
    // «Войти» в TopAppBar OfflineManagerScreen, либо просто повторный успех API
    // после восстановления сети (watcher в NetworkObserver).
    @Volatile private var consecutiveNetworkErrors: Int = 0
    @Volatile private var lastNetworkErrorTs: Long = 0L
    private val MAX_CONSECUTIVE_NET_ERRORS = 3
    private val NET_ERROR_WINDOW_MS = 60_000L

    /**
     * Fix #45: Сброс счётчика сетевых ошибок при восстановлении сети.
     *
     * Вызывается из [re.pinok.SovaApp] через `networkObserver.addOnNetworkLostListener`
     * точнее при onAvailable (см. SovaApp.kt — watcher подписан на isOnlineFlow).
     *
     * Без этого после WiFi→Mobile switch: счётчик может быть близок к
     * [MAX_CONSECUTIVE_NET_ERRORS] от tail-ошибок на мёртвом WiFi-интерфейсе,
     * и 1-2 ошибки на новом интерфейсе → ложный auto-offline.
     */
    fun resetNetworkErrorCounter() {
        if (consecutiveNetworkErrors > 0) {
            AppLog.i("VKApiClient", "Network restored — resetting consecutiveNetworkErrors ($consecutiveNetworkErrors → 0)")
            consecutiveNetworkErrors = 0
            lastNetworkErrorTs = 0L
        }
    }

    /**
     * Sprint 1, P0-3 (#76): обработчик VK Captcha (error 14).
     *
     * Если задан — [callInternal] при error 14 вызывает [CaptchaHandler.solve],
     * показывает UI-диалог, и при success — retry с captcha_sid+captcha_key.
     * Если null — error 14 возвращается как null (старое поведение).
     *
     * Устанавливается из [re.pinok.SovaApp] после инициализации.
     */
    @Volatile
    var captchaHandler: re.pinok.captcha.CaptchaHandler? = null

    /** Public wrapper for fire-and-forget calls (e.g. stories.view). */
    suspend fun callPublic(method: String, args: Map<String, String>) {
        call(method, args)
    }

    private suspend fun call(
        method: String,
        args: Map<String, String>,
        skipOffline: Boolean = false,
        silent: Boolean = false,
    ): JsonObject? {
        return callInternal(method, args, captchaAttempt = 0, skipOffline = skipOffline, silent = silent)
    }

    /**
     * Sprint 1, P0-3 (#76): внутренняя реализация call с счётчиком captcha-попыток.
     *
     * При error 14 (Captcha needed) — если [captchaHandler] задан и
     * `captchaAttempt < MAX_CAPTCHA_RETRIES` (3), вызываем `captchaHandler.solve()`,
     * показываем UI-диалог, и при success — рекурсивный retry с captcha_sid+captcha_key.
     * При cancel (solve вернул null) — return null (запрос отменён пользователем).
     *
     * §37.12 #322: [silent] — если true, E-level логи API-ошибок понижаются до D.
     * Используется для BFF-only методов (video.addViewingHistoryRecord), которые
     * стабильно возвращают error 100 через прямой токен, но не должны засорять
     * logcat каждый swipe в clips.
     */
    private suspend fun callInternal(
        method: String,
        args: Map<String, String>,
        captchaAttempt: Int,
        skipOffline: Boolean = false,
        silent: Boolean = false,
    ): JsonObject? {
        // S7-1: Rate limiter — max 3 requests per second to avoid Flood Control.
        rateLimitWait()

        // Fix #99: LongPoll сервер должен пытаться переподключиться даже
        // при кратковременной потере сети, иначе приложение «теряет» соединение навсегда.
        if (!skipOffline && isOffline()) {
            AppLog.d("VKApiClient", "call($method): offline, returning null")
            return null
        }

        // PrivacyMods: читаем snapshot один раз на вызов (безопасно, prefs — DataStore).
        val snap = prefs.data.first()

        var attempt = 0
        while (attempt < 2) {
            val tk = token()
                ?: run {
                    // #FORCE-REFRESH (2026-08-02): no token → force real refresh.
                    // #NULL-SAFE: smart-cast через локальный val (без ?.).
                    val er = exchangeAuthRepository
                    val refreshed: String? = if (er != null) er.ensureFreshToken(force = true) else null
                    if (refreshed.isNullOrBlank()) {
                        // Fix #175: grace period — НЕ триггерим AuthActivity если
                        // сеть недавно переключилась. token() мог вернуть null
                        // из-за того что предыдущий error 5/1117 (во время switch)
                        // уже очистил access_token. Но через 30 сек VK перестанет
                        // возвращать error 5/1117 и ensureFreshToken сможет обменять
                        // exchange_token на свежий access_token без полного re-login.
                        val recentlySwitched = isNetworkRecentlySwitched(30_000L)
                        if (recentlySwitched) {
                            AppLog.w("VKApiClient", "call($method): no token + refresh failed, но сеть недавно переключилась — НЕ notifyTokenInvalidated (Fix #175). Возвращаем null.")
                            return null
                        }
                        AppLog.w("VKApiClient", "call($method): no token, refresh failed")
                        // Fix #112: token() вернул null (токена нет в storage —
                        // cleared после error 5, или никогда не было) И refresh
                        // через exchange_token/web_token упал. Раньше здесь был
                        // молчаливый `return null` — MainActivity не узнавал, что
                        // токен мёртв, и пользователь видел вечный loading screen
                        // (особенно после долгого фона: process kill + restore →
                        // boot скипался из-за bootLocal, API вызовов не было →
                        // notifyTokenInvalidated не вызывался).
                        //
                        // Теперь: сообщаем MainActivity что токен невалиден →
                        // boot LaunchedEffect запустит AuthActivity (silent re-login
                        // через remixsid если есть, иначе полный вход).
                        try { SovaApp.get().notifyTokenInvalidated() } catch (_: Exception) {}
                        return null
                    }
                    refreshed
                }

            // =================================================================
            // #28: ВКЛЮЧАЕМ VkSigner для messages.* / audio.* / execute.
            //
            // КОРНЕВАЯ ПРИЧИНА error 15 (#26 был неверным решением):
            //   #22 переключил на OAuth WebView (response_type=token) — VK НЕ
            //   возвращает `secret` (user_secret) в этом flow. Без secret подписать
            //   запрос невозможно → messages.* / audio.* дают error 15.
            //   #26 на этом основании отключил sig совсем — неверно.
            //
            // #27 вернул Direct Auth (oauth.vk.com/access_token grant_type=password),
            // который возвращает `secret` (AuthResult.secret, поле `c` в декомпиляте).
            // #28: теперь используем этот user_secret для sig.
            //
            // ⚠️ app_secret (hHbZxrka2uZ6jB1inYsH) — НЕ для подписи! Он серверный.
            //    См. VkSigner.kt, декомпилят xsna.tzs.f() + AuthResult.c.
            // =================================================================

            // Telemetry endpoints suppression — дропаем stats.* и execute.*stat*
            // вызовы если privacyAntiTelemetry=true (audit #25: подключение PrivacyMods).
            if (privacyMods.shouldDropTelemetry(snap) &&
                (method.startsWith("stats.") || method.contains("stat", ignoreCase = true))) {
                AppLog.d("VKApiClient", "call($method): telemetry dropped by PrivacyMods")
                return null
            }

            // Собираем ВСЕ параметры запроса в LinkedHashMap — порядок важен для sig.
            // VK SDK (tzs.c()) формирует: user args → v → https → access_token → lang → device.
            val allParams = LinkedHashMap<String, String>(args.size + 5)
            args.forEach { (k, v) -> allParams[k] = v }
            allParams["v"] = VKEndpoints.API_VERSION
            allParams["https"] = "1"
            allParams["access_token"] = tk
            allParams["lang"] = "ru"
            // Device masking — подменяем device_model/os_version/build/manufacturer
            // на «Pixel 9 Pro» если privacyDeviceMask=true.
            // VK API принимает параметр `device` как JSON-string с полями устройства.
            if (privacyMods.shouldMaskDevice(snap)) {
                val fields = privacyMods.maskedDeviceFields()
                val deviceJson = com.google.gson.Gson().toJson(fields)
                allParams["device"] = deviceJson
            }

            // sig-подпись для messages.* / audio.* / execute — если нужно.
            //
            // #33: Web Token Exchange flow (client_id=6287487) возвращает vk1.a.XXX
            // токены, которые VK API trustит как официальному веб-клиенту.
            // Для них sig НЕ нужен — даже для SIGNED_METHODS (messages.*, audio.*).
            //
            // Логика:
            //   1. vk1.a.* токен (вебовый) → НИКОГДА не подписываем
            //   2. Обычный токен + метод требует sig + есть user_secret → подписываем
            //   3. Иначе → без sig (VK сам вернёт error 15 если метод требует подписи)
            val userSecret = tokenStorage.secret()
            val sig: String? = if (VkSigner.shouldSign(method, tk, userSecret)) {
                val secret = userSecret; if (secret == null) null
                else VkSigner.sign(method, allParams, secret)
            } else null

            val form = FormBody.Builder().apply {
                allParams.forEach { (k, v) -> add(k, v) }
                sig?.let { add("sig", it) }
            }.build()

            if (sig != null) {
                AppLog.d("VKApiClient", "call($method): signed with user_secret (sig=${sig.take(8)}…)")
            } else if (VkSigner.isWebToken(tk)) {
                AppLog.d("VKApiClient", "call($method): web token (vk1.a.*), no sig needed")
            }

            // ─────────────────────────────────────────────────────────────────
            // Task #Web-API: выбор шлюза по prefs.
            //   false (default) → api.vk.com (Android, sig-required для некоторых методов)
            //   true            → web.api.vk.ru (m.vk.ru mobile-web gateway)
            //
            // Web gateway принимает те же params + access_token, но не требует
            // X-VK-Android-Client header (см. SovaApp interceptor — для web.api.vk.ru
            // он автоматически опускается через isWebFlowHost). sig для vk1.a.*
            // web-токенов и так не нужен (VkSigner.isWebToken → no sig).
            //
            // Fix #124: Web gateway имеет allowlist методов и возвращает err=3
            // "Unknown method passed" для методов, которых нет в нём. Подтверждённые
            // несовместимые: messages.setConversationPushSettings (mute/unmute).
            // Для них форсируем api.vk.com независимо от pref — там метод работает.
            // ─────────────────────────────────────────────────────────────────
            val useWebGateway = snap.netUseWebApiGateway && !WEB_INCOMPATIBLE_METHODS.contains(method)
            if (snap.netUseWebApiGateway && WEB_INCOMPATIBLE_METHODS.contains(method)) {
                AppLog.d("VKApiClient", "call($method): forcing api.vk.com (incompatible with web gateway)")
            }
            val req = Request.Builder()
                .url(VKEndpoints.method(method, useWebGateway))
                .post(form)
                .build()
            if (useWebGateway) {
                AppLog.d("VKApiClient", "call($method): using WEB gateway web.api.vk.ru")
            }

            // #33: Детальная трассировка VK API запроса — метод + маскированные
            // параметры + статус подписи. Помогает диагностировать error 5/14/15
            // и сетевые сбои по экспортированным логам (AppLog.exportDetailed).
            AppLog.api(
                method = method,
                params = args,
                direction = AppLog.ApiDirection.REQUEST,
            )

            val startNs = System.nanoTime()
            val raw: JsonObject = try {
                withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<JsonObject> { cont ->
                        val callObj = httpClient.newCall(req)
                        cont.invokeOnCancellation { runCatching { callObj.cancel() } }
                        callObj.enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                if (cont.isActive) cont.resumeWithException(e)
                            }
                            override fun onResponse(call: Call, response: Response) {
                                try {
                                    val body = response.body?.string().orEmpty()
                                    if (!response.isSuccessful) {
                                        cont.resumeWithException(IOException("HTTP ${response.code}: ${body.take(200)}"))
                                        return
                                    }
                                    val parsed = JsonParser.parseString(body).asJsonObject
                                    cont.resume(parsed)
                                } catch (e: Exception) {
                                    if (cont.isActive) cont.resumeWithException(e)
                                }
                            }
                        })
                    }
                }
            } catch (netErr: Exception) {
                // Fix #114: CancellationException (включая LeftCompositionCancellationException)
                // — это нормальная отмена корутины (composition left, user navigated away).
                // НЕ логируем как NETWORK_FAIL и НЕ считаем как network error для auto-offline.
                // Раньше логировалось как "✗NET ... Canceled" → засоряло логи и могло
                // триггерить auto-offline после 3 отмен.
                if (netErr is kotlinx.coroutines.CancellationException) {
                    throw netErr
                }
                // #33: Логируем сетевой сбой с длителькой и пробрасываем исключение
                // наверх (прежнее поведение — обработка в вызывающем методе).
                val durationMs = (System.nanoTime() - startNs) / 1_000_000L
                AppLog.api(
                    method = method,
                    params = args,
                    direction = AppLog.ApiDirection.NETWORK_FAIL,
                    durationMs = durationMs,
                    error = netErr,
                )
                // #38: auto-offline после MAX_CONSECUTIVE_NET_ERRORS сетевых неудач
                // в течение NET_ERROR_WINDOW_MS. Только для IOException-подобных
                // ошибок (не для CancellationException — это нормальная отмена).
                if (netErr is java.io.IOException ||
                    netErr is java.net.UnknownHostException ||
                    netErr is java.net.SocketTimeoutException) {
                    val now = System.currentTimeMillis()
                    if (now - lastNetworkErrorTs > NET_ERROR_WINDOW_MS) {
                        consecutiveNetworkErrors = 0
                    }
                    consecutiveNetworkErrors++
                    lastNetworkErrorTs = now
                    AppLog.w(
                        "VKApiClient",
                        "Network error #$consecutiveNetworkErrors (window ${NET_ERROR_WINDOW_MS}ms): ${netErr.javaClass.simpleName}: ${netErr.message}",
                    )
                    if (consecutiveNetworkErrors >= MAX_CONSECUTIVE_NET_ERRORS) {
                        AppLog.w(
                            "VKApiClient",
                            "Auto-enabling offline mode after $consecutiveNetworkErrors consecutive network failures — switching to OfflineManager",
                        )
                        // callInternal — suspend fun, prefs.setPrivacyOfflineMode тоже suspend.
                        // runCatching глотает возможные исключения DataStore (маловероятно, но безопасно).
                        runCatching { prefs.setPrivacyOfflineMode(true) }
                        consecutiveNetworkErrors = 0
                    }
                }
                throw netErr
            }

            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val respSize = raw.toString().length

            // Безопасное чтение «error»: getAsJsonObject бросает ClassCastException,
            // если член существует, но не является объектом (патологический ответ VK).
            val err = raw.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
            if (err == null) {
                AppLog.api(
                    method = method,
                    params = args,
                    direction = AppLog.ApiDirection.RESPONSE_OK,
                    durationMs = durationMs,
                    bodySize = respSize,
                )
                // #38: успешный ответ VK — сбрасываем счётчик сетевых ошибок.
                // Если до этого были 1-2 неудачи (но не достигли порога), теперь
                // счётчик обнулён, и следующая серия начнётся сначала.
                if (consecutiveNetworkErrors > 0) {
                    consecutiveNetworkErrors = 0
                }
                return raw
            }

            val code = err.get("error_code")?.asInt ?: -1
            val msg = err.get("error_msg")?.asString ?: "unknown"
            // Fix #230: error_subcode 1130 = "access_token was given to another ip address".
            // Это НЕ временное явление (как обычный err=5 после switch сети) — VK
            // привязывает токен к IP навсегда. Grace period (Fix #175) здесь
            // бесполезен и вреден: 30 секунд null-ответов → пустая лента → краш UI.
            // При 1130 сразу чистим токен и запускаем AuthActivity.
            val subcode = err.get("error_subcode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val isIpMismatch = code == 5 && subcode == 1130

            // #33: Трассировка API-ошибки (дополняет детальный диагностический лог
            // ниже — здесь тайминг + размер + code, там — error_msg и анализ sig).
            // §37.12 #322: silent-методы (BFF-only) логируют ошибку на D, не E.
            if (silent) {
                AppLog.d("VKApiClient", "✗ $method err=$code (silent, BFF-only) ${durationMs}ms ${respSize}B")
            } else {
                AppLog.api(
                    method = method,
                    params = args,
                    direction = AppLog.ApiDirection.RESPONSE_ERR,
                    durationMs = durationMs,
                    apiCode = code,
                    bodySize = respSize,
                )
            }

            // Sprint 1, P0-3 (#76): Captcha needed (error 14).
            // VK возвращает captcha_sid + captcha_img. Показываем UI-диалог
            // через captchaHandler, при success — retry с captcha_sid+captcha_key.
            // MAX_CAPTCHA_RETRIES=3 — защита от бесконечной рекурсии при упорно
            // неверной captcha. При cancel (solve→null) — return null (отменено
            // пользователем, не ошибка).
            val handler = captchaHandler
            if (code == 14 && handler != null && captchaAttempt < MAX_CAPTCHA_RETRIES) {
                val sid = err.get("captcha_sid")?.takeIf { !it.isJsonNull }?.asString
                val img = err.get("captcha_img")?.takeIf { !it.isJsonNull }?.asString
                if (sid != null && img != null) {
                    AppLog.w("VKApiClient",
                        "Captcha required (sid=${sid.take(8)}…) on $method — " +
                            "solving via UI (attempt ${captchaAttempt + 1}/$MAX_CAPTCHA_RETRIES)")
                    val key = handler.solve(sid, img)
                    if (key != null) {
                        val newArgs = args.toMutableMap().apply {
                            put("captcha_sid", sid)
                            put("captcha_key", key)
                        }
                        return callInternal(method, newArgs, captchaAttempt + 1, skipOffline = skipOffline, silent = silent)
                    }
                    AppLog.w("VKApiClient", "Captcha cancelled by user on $method")
                    lastApiError = "$method: captcha cancelled"
                    lastApiErrorCode = 14
                    return null
                }
            }

            // Fix #96: обрабатываем и code=5 (token invalid) и code=1117 (token expired).
            // VK возвращает 1117 для web-токенов vk1.a.* когда они истекают по времени
            // (а не по инвалидации сессии). Раньше 1117 НЕ обрабатывался → все запросы
            // падали → авторизация «пропадала через ~час пользования» → белый экран.
            // Теперь: сначала пробуем silent refresh (exchange_token), при неудаче —
            // чистим токен и уведомляем MainActivity → AuthActivity → авто-вход через
            // сохранённый remixsid (если CookieManager его ещё содержит).
            //
            // Fix #175: GRACE PERIOD после switch сети. VK-сервер иногда возвращает
            // error 5/1117 сразу после смены IP (Wi-Fi → Mobile) из-за security-фичи
            // «подозрительная активность». Через 5-15 сек токен снова работает.
            // Если error 5/1117 пришла в течение 30 сек после switch'а — НЕ чистим
            // токен и НЕ триггерим AuthActivity, а ждём 5 сек и retry. Только если
            // ошибка persist'ит после grace period — реальная инвалидация.
            val isTokenExpiredOrInvalid = code == 5 || code == 1117
            val authRepo = exchangeAuthRepository
            if (isTokenExpiredOrInvalid && attempt == 0 && authRepo != null) {
                // Fix #175: grace period check.
                val recentlySwitched = isNetworkRecentlySwitched(30_000L)
                // #IP-MISMATCH-GRACE (2026-08-01): Fix #230 пропускал grace period
                // при IP mismatch (subcode 1130) — но при network switch (Wi-Fi↔Mobile)
                // VK ВСЕГДА меняет IP и временно возвращает 5/1130. Без grace period
                // это приводило к мгновенному AuthActivity SILENT при каждой смене сети.
                //
                // Лог до фикса (network switch mobile→wifi):
                //   12:37:48.432  onAvailable: 162 (Wi-Fi)
                //   12:37:50.935  getLongPollServer err=5/1130 (IP mismatch)
                //   12:37:50.936  Grace period ПРОПУЩЕН (Fix #230) → ensureFreshToken
                //   12:37:50.943  attempt 2 (7мс спустя — refresh не успел/упал)
                //   12:37:51.109  err=5/1130 снова
                //   12:37:51.150  notifyTokenInvalidated: tick 1
                //   12:37:51.336  AuthActivity SILENT mode ← лишний re-login!
                //
                // Фикс: grace period работает ДАЖЕ при IP mismatch, если
                // recentlySwitched=true. За 5 сек либо VK обновит IP binding,
                // либо ensureFreshToken (Path 1.5 silentRefreshViaRemixsid) получит
                // новый токен для нового IP.
                if (recentlySwitched) {
                    // #RELOGIN-FORCE (2026-08-02): EARLY check hasSilentMeans BEFORE delay.
                    // Web OAuth tokens (6287487) — permanently IP-bound. VK НЕ обновляет
                    // IP binding (logcat 19:32-19:33: 60+ сек стабильно err=5/1130).
                    // Если silent means НЕТ (no remixsid/exchange_token/trusted_hash —
                    // типично для external browser auth на Android 7+, где cookies
                    // изолированы) — grace period бесполезен: 5с delay + FORCE refresh
                    // (все paths failed) + retry old token = err=5 again → "no data"
                    // 30с → AuthActivity. Лучше СРАЗУ launch AuthActivity — user
                    // перезайти за ~20с вместо 50с+ (30с grace + 20с re-login).
                    // Для user С remixsid: grace period + FORCE refresh (Path 1.5).
                    //
                    // #FORCE-REFRESH защищает от ложных AuthActivity: если silent means
                    // есть (remixsid), Path 1.5 получит новый токен → AuthActivity НЕ
                    // запустится. #NO-SILENT-MEANS (§41.19) возвращал null → app зависал
                    // в "no data" → user не мог перезайти. #RELOGIN-FORCE это исправляет.
                    val hasSilentMeansEarly = try { authRepo.hasSilentReloginMeans() } catch (_: Exception) { true }
                    if (!hasSilentMeansEarly) {
                        // #IP-BINDING-RETRY (2026-08-15): нет silent means (внешний
                        // OAuth — remixsid не захвачен из-за изоляции cookies), но
                        // web-токен vk1.a.* НЕ привязан к IP навсегда: VK обновляет
                        // IP binding асинхронно после network switch (grace period
                        // §43/§175). Вместо мгновенного clearAccessToken + AuthActivity
                        // (#RELOGIN-FORCE) даём VK время обновить binding и делаем
                        // single retry со СТАРЫМ токеном. Если retry снова 5/1130 и
                        // grace истёк (>30с) — тогда полный re-login (нижний блок).
                        val graceDelayMs = if (method == "messages.getLongPollServer") 2_000L else 5_000L
                        AppLog.w("VKApiClient", "API error $code${if (isIpMismatch) "/1130 (IP mismatch)" else ""} on $method — network switched + NO silent means. VK обновляет IP binding асинхронно — ждём ${graceDelayMs}мс и делаем single retry со старым токеном (#IP-BINDING-RETRY).")
                        try { SovaApp.get().setNetworkSwitchState(re.pinok.util.NetworkSwitchState.Refreshing(attempt = 1)) } catch (_: Exception) {}
                        kotlinx.coroutines.delay(graceDelayMs)
                        attempt++
                        continue
                    }
                    // §43 #NET-SWITCH-DELAY: method-aware grace delay.
                    // messagesGetLongPollServer — lightweight метод, вызывается в tight
                    // loop LongPollClient'а. 5с grace delay × 3 цикла = 15с «зависания»
                    // только внутри callInternal. Сократили до 2с для этого метода —
                    // LongPollClient.loop() всё равно делает interruptibleDelay после
                    // null-fetch, так что общая пауза между retry ~7с (2с grace + 5с
                    // loop delay), достаточно для VK IP binding update.
                    // Для остальных методов (messages.send, wall.post и т.д.) оставляем
                    // 5с — они запускаются по user-action, 5с acceptable.
                    val graceDelayMs = if (method == "messages.getLongPollServer") 2_000L else 5_000L
                    AppLog.w("VKApiClient", "API error $code${if (isIpMismatch) "/1130 (IP mismatch)" else ""} on $method — но сеть недавно переключилась (default-network-switch < 30s). VK часто возвращает 5/1130 при смене IP. Ждём ${graceDelayMs}мс (method-aware), пробуем silent refresh, затем retry БЕЗ clearAccessToken.")
                    kotlinx.coroutines.delay(graceDelayMs)
                    // #NET-SWITCH-POPUP #MULTI-ATTEMPT-GRACE: до 3 попыток silent
                    // refresh с интервалом graceDelayMs/2 между ними. Каждая попытка
                    // обновляет NetworkSwitchState.Refreshing(attempt=N) — popup
                    // показывает «попытка N/3», пользователь видит прогресс вместо
                    // зависшего spinner'а.
                    //
                    // Обоснование: VK обновляет IP binding асинхронно, одна попытка
                    // через 5с может не успеть (сервер ещё кэширует старый binding).
                    // 3 попытки × 2.5с интервал = ~7.5с окно для VK. Если silent means
                    // есть (remixsid) — Path 1.5 silentRefreshViaRemixsid получит новый
                    // токен на 1-й/2-й/3-й попытке.
                    //
                    // #GRACE-NO-CLEAR (2026-08-02): если ВСЕ 3 попытки вернули null
                    // (нет remixsid/exchange_token/trusted_hash — типично для external
                    // browser auth, где cookies изолированы), НЕ продолжаем retry со
                    // старым мёртвым токеном — это гарантирует второй err=5 →
                    // clearAccessToken → AuthActivity full re-login при КАЖДОЙ смене
                    // сети. Вместо этого возвращаем null — вызывающий получит "нет
                    // данных", пользователь останется в приложении. VK за 30-60с
                    // обновит IP binding, следующий API-вызов пройдёт нормально.
                    //
                    // Это решает жалобу: "переключение ви-фи на мобильную сеть
                    // требует регистрацию при смене сети".
                    val maxGraceAttempts = 3
                    var refreshedDuringGrace: String? = null
                    for (graceAttempt in 1..maxGraceAttempts) {
                        try { SovaApp.get().setNetworkSwitchState(
                            re.pinok.util.NetworkSwitchState.Refreshing(attempt = graceAttempt)) } catch (_: Exception) {}
                        AppLog.i("VKApiClient", "grace period silent refresh attempt $graceAttempt/$maxGraceAttempts on $method")
                        runCatching {
                            // #FORCE-REFRESH (2026-08-02): err=5/1130 means VK rejected
                            // the token. hasValidAccessToken() lies (checks timestamp only,
                            // not IP binding). Force=true bypasses short-circuit and
                            // calls Path 1.5 (silentRefreshViaRemixsid) to get a NEW token
                            // for the NEW IP. Without force, returns the SAME old IP-bound
                            // token → retry fails → app stuck in "no data" loop.
                            refreshedDuringGrace = authRepo.ensureFreshToken(force = true)
                        }.onFailure { e ->
                            AppLog.w("VKApiClient", "grace attempt $graceAttempt silent refresh failed: ${e.message}")
                        }
                        if (refreshedDuringGrace != null) {
                            AppLog.i("VKApiClient", "grace period silent refresh OK on attempt $graceAttempt/$maxGraceAttempts — retry с новым токеном")
                            break
                        }
                        // Между попытками короткий delay (graceDelayMs/2) — даём VK
                        // время обновить IP binding. Не полный graceDelayMs, т.к.
                        // первичный delay уже был выше.
                        if (graceAttempt < maxGraceAttempts) {
                            kotlinx.coroutines.delay(graceDelayMs / 2L)
                        }
                    }
                    if (refreshedDuringGrace != null) {
                        // #NET-SWITCH-POPUP: новый токен получен → Idle (скрыть popup).
                        try { SovaApp.get().setNetworkSwitchState(re.pinok.util.NetworkSwitchState.Idle) } catch (_: Exception) {}
                        attempt++
                        continue
                    }
                    // #GRACE-NO-CLEAR: silent refresh не дал нового токена.
                    // ВАРИАНТ A: retry со старым (старый всё ещё в storage) —
                    //   VK мог уже обновить IP binding за 5с delay.
                    // ВАРИАНТ B: вернуть null без clearAccessToken.
                    //
                    // Делаем ОДНУ попытку retry со старым токеном (вариант A) —
                    // если VK успел обновить IP binding, retry пройдёт успешно.
                    // Если retry снова даст err=5 — следующий recentlySwitched=true
                    // (если <30с) снова grace, иначе — чистый clearAccessToken.
                    // Это БОЛЕЕ мягко чем было раньше (где retry был всегда, даже
                    // если ensureFreshToken ничего не дал и grace period ложный).
                    AppLog.i("VKApiClient", "silent refresh during grace period returned no new token — single retry with old token (VK may have updated IP binding during 5s delay)")
                    // #NET-SWITCH-POPUP: silent refresh не дал токен → Failed(canRetry=true).
                    // Если single-retry со старым токеном пройдёт — следующий успех не
                    // сбросит state явно, но popup auto-timeout очистит Switching/Refreshing.
                    // Если retry упадёт — следующий err=5 (не recentlySwitched) → AuthActivity.
                    val canRetryFlag = try { authRepo.hasSilentReloginMeans() } catch (_: Exception) { true }
                    try { SovaApp.get().setNetworkSwitchState(re.pinok.util.NetworkSwitchState.Failed(
                        "Не удалось обновить токен автоматически.", canRetry = canRetryFlag)) } catch (_: Exception) {}
                    attempt++
                    continue
                }
                if (isIpMismatch) {
                    AppLog.w("VKApiClient", "API error 5/1130 (IP mismatch) on $method — токен перманентно невалиден для этого IP (НЕ network switch). Идём к refresh.")
                } else {
                    AppLog.w("VKApiClient", "API error $code (token ${if (code == 1117) "expired" else "invalid"}) on $method — refreshing via exchange_token")
                }
                // #FORCE-REFRESH (2026-08-02): err=5/1130 — VK rejected token.
                // Force=true bypasses hasValidAccessToken() short-circuit, tries
                // Path 1.5 (silentRefreshViaRemixsid) for a new IP-bound token.
                val refreshed = authRepo.ensureFreshToken(force = true)
                if (refreshed != null) {
                    attempt++
                    continue
                }
                // #RELOGIN-FORCE (2026-08-02): если ensureFreshToken(force=true) вернул
                // null И в storage НЕТ remixsid/trusted_hash/exchange_token (типично для
                // external browser auth на Android 7+, где cookies изолированы) —
                // ЗАПУСКАЕМ AuthActivity (НЕ return null).
                //
                // #NO-SILENT-MEANS (§41.19) возвращал null → app зависал в "no data" →
                // user не мог перезайти. Logcat 19:32-19:33 доказал: VK НЕ обновляет
                // IP binding для web OAuth tokens (60+ сек стабильно err=5/1130). Ждать
                // 5 минут (extendedGrace) бессмысленно — token permanently rejected.
                //
                // #FORCE-REFRESH уже попробовал все silent paths (Path 0/1.5/2.5/3) —
                // все failed. Единственный путь — re-login через AuthActivity.
                // User перезайти → new token (IP-bound к новому IP) → app работает.
                //
                // #FORCE-REFRESH защищает от ложных AuthActivity: если silent means есть
                // (remixsid), Path 1.5 получит новый токен → код ниже НЕ выполнится.
                val hasSilentMeans = try {
                    authRepo.hasSilentReloginMeans()
                } catch (_: Exception) {
                    // Если не можем проверить — считаем что silent means есть
                    // (старое поведение, безопаснее для пользователя).
                    true
                }
                if (!hasSilentMeans) {
                    AppLog.e("VKApiClient", "Refresh failed (no silent means: no remixsid/exchange_token/trusted_hash) — web OAuth token permanently IP-rejected. Launching AuthActivity for re-login (#RELOGIN-FORCE).")
                    tokenStorage.clearAccessToken()
                    try { SovaApp.get().notifyTokenInvalidated() } catch (_: Exception) {}
                    return null
                }
                // P3 #IP-MISMATCH-RETRY: 5/1130 = VK привязал токен к другому IP.
                // При живых silent-средствах (remixsid/p/trusted_hash) даём VK ещё
                // один короткий шанс обновить IP binding — доп. retry через 2с.
                // Снижает каскад «единичный IP-mismatch → clearAccessToken →
                // AuthActivity SILENT → возможно FULL» на мобильной сети с частой
                // сменой вышек (Wi-Fi↔LTE, handover между сотами).
                if (isIpMismatch) {
                    kotlinx.coroutines.delay(2_000L)
                    val retryIpRefresh = authRepo.ensureFreshToken(force = true)
                    if (retryIpRefresh != null) {
                        AppLog.i("VKApiClient", "API error 5/1130 — retry silent refresh OK after 2s (#IP-MISMATCH-RETRY), retrying $method")
                        attempt++
                        continue
                    }
                    AppLog.w("VKApiClient", "API error 5/1130 — retry silent refresh also failed, falling through to clearAccessToken")
                }
                AppLog.e("VKApiClient", "Refresh failed, clearing access_token (keeping remixsid/sat for silent re-login — Fix #106)")
                // Fix #106: очищаем только access_token, НЕ трогая remixsid/sat_token/
                // exchange_token. Это позволяет AuthActivity (Fix #107) автоматически
                // переобменять remixsid на свежий web_token без ручного ввода пароля.
                tokenStorage.clearAccessToken()
                // Fix #50-A: уведомляем MainActivity о потере токена, иначе
                // приложение остаётся на главном экране с пустым токеном →
                // все API вызовы возвращают null → пустая лента → БЕЛЫЙ ЭКРАН.
                try { SovaApp.get().notifyTokenInvalidated() } catch (_: Exception) {}
                return null
            }

            // §37.12 #322: silent-флаг для BFF-only методов — лог на D, не E.
            if (silent) {
                AppLog.d("VKApiClient", "(silent) API error $code: $msg (method=$method)")
            } else {
                AppLog.e("VKApiClient", "API error $code: $msg (method=$method)")
            }
            lastApiError = "$method: $msg"
            lastApiErrorCode = code
            if (code == 5 || code == 1117) {
                // Fix #175: grace period — даже на 2-й попытке. Если switch сети
                // был недавно, НЕ чистим токен и НЕ запускаем AuthActivity.
                // Возвращаем null — вызывающий код увидит «нет данных» и пользователь
                // не увидит окно авторизации. Через 30 сек после switch'а grace period
                // истечёт, и реальная инвалидация токена обработается нормально.
                // #IP-MISMATCH-GRACE: grace period работает и при IP mismatch (1130)
                // при network switch — VK временно возвращает 5/1130 при смене IP.
                val recentlySwitched = isNetworkRecentlySwitched(30_000L)
                if (recentlySwitched) {
                    AppLog.w("VKApiClient", "API error $code${if (isIpMismatch) "/1130" else ""} on $method (attempt=$attempt) — сеть недавно переключилась, НЕ чистим токен и НЕ запускаем AuthActivity (Fix #175 grace period). Возвращаем null.")
                    return null
                }
                // #RELOGIN-FORCE (2026-08-02): no silent means + retry exhausted →
                // ЗАПУСКАЕМ AuthActivity (НЕ return null). #NO-SILENT-MEANS (§41.19)
                // возвращал null → "no data" forever → user не мог перезайти.
                // Теперь user видит AuthActivity и может перезайти.
                //
                // #FORCE-REFRESH в первом блоке (attempt==0) уже попробовал все silent
                // paths → если мы тут (attempt > 0, NOT recentlySwitched) — silent means
                // точно нет → re-login единственный путь. VK permanently rejects IP-bound
                // web OAuth tokens (logcat 19:32-19:33: 60+ сек err=5/1130 стабильно).
                //
                // #NULL-SAFE (2026-08-02): этот блок — ОТДЕЛЬНЫЙ от `attempt==0 && authRepo!=null`
                // (9078→9201), поэтому smart-cast на `authRepo` тут НЕ действует. authRepo
                // объявлен как `val authRepo = exchangeAuthRepository` (nullable).
                // Coding style PinoK (#NULL-EXPLICIT): неявные null-операторы запрещены —
                // локальный `val ar = authRepo` + `if (ar != null) ar.hasSilentReloginMeans() else true`.
                // Если authRepo==null, считаем что silent means есть (старое поведение,
                // безопаснее для пользователя).
                val hasSilentMeans = try {
                    val ar = authRepo
                    if (ar != null) ar.hasSilentReloginMeans() else true
                } catch (_: Exception) {
                    true
                }
                if (!hasSilentMeans) {
                    AppLog.e("VKApiClient", "API error $code on $method (attempt=$attempt) — no silent means, retry exhausted — launching AuthActivity for re-login (#RELOGIN-FORCE).")
                    tokenStorage.clearAccessToken()
                    try { SovaApp.get().notifyTokenInvalidated() } catch (_: Exception) {}
                    return null
                }
                // Fix #106: только access_token, сохраняем remixsid для silent re-login.
                tokenStorage.clearAccessToken()
                // Fix #50-A: тот же механизм — пользователь увидит AuthActivity
                // вместо пустой ленты.
                try { SovaApp.get().notifyTokenInvalidated() } catch (_: Exception) {}
            }
            // Error 15 (access denied) — sig issue. Помогаем диагностировать:
            //  • vk1.a.* web token (client_id=6287487) — error 15 невозможен,
            //    VK trustит веб-токену. Если получили — токен истёк/ревёкнут.
            //  • sig отсутствует → нужен Direct Auth (oauth.vk.com/access_token grant_type=password)
            //  • sig неверный → user_secret не совпадает (возможно токен получен через OAuth WebView)
            //  • sig верный но метод заблокирован → VK заблокировал метод для этого client_id
            if (code == 15 && VkSigner.requiresSig(method)) {
                val sigStatus = when {
                    VkSigner.isWebToken(tk) -> "web token (vk1.a.*) — token expired/revoked?"
                    sig == null -> "sig NOT sent (no user_secret — old OAuth token?)"
                    userSecret.isNullOrBlank() -> "no user_secret in storage"
                    else -> "sig sent (${sig.take(8)}…) but VK rejected — secret mismatch?"
                }
                AppLog.w("VKApiClient",
                    "error 15 on signed method $method: $sigStatus. " +
                    "Use Web Token flow (client_id=6287487) or Direct Auth (phone+password).")
            }
            return null
        }
        return null
    }

    // Sprint 4: Опросы.

    private fun parsePoll(o: JsonObject): re.pinok.data.model.Poll {
        val answers = o.getAsJsonArray("answers")?.mapNotNull { a ->
            if (!a.isJsonObject) return@mapNotNull null
            val ao = a.asJsonObject
            re.pinok.data.model.Poll.Answer(
                id = ao.get("id")?.asLong ?: 0L,
                text = ao.get("text")?.asString ?: "",
                votes = ao.get("votes")?.asInt ?: 0,
                rate = ao.get("rate")?.asDouble ?: 0.0,
            )
        } ?: emptyList()
        return re.pinok.data.model.Poll(
            id = o.get("id")?.asLong ?: 0L,
            ownerId = o.get("owner_id")?.asLong ?: 0L,
            question = o.get("question")?.asString ?: "",
            created = o.get("created")?.asLong ?: 0,
            votes = o.get("votes")?.asInt ?: 0,
            answerId = o.get("answer_id")?.asLong,
            answers = answers,
            anonymous = o.get("anonymous")?.asInt ?: 0,
            multiple = o.get("multiple")?.asInt ?: 0,
            closed = o.get("closed")?.asInt ?: 0,
            isBoard = o.get("is_board")?.asInt ?: 0,
        )
    }

    /** polls.addVote — проголосовать в опросе. */
    suspend fun pollsAddVote(pollId: Long, ownerId: Long, answerIds: List<Long>): Boolean {
        if (isOffline()) return false
        val json = call("polls.addVote", mapOf(
            "poll_id" to pollId.toString(),
            "owner_id" to ownerId.toString(),
            "answer_ids" to answerIds.joinToString(","),
        )) ?: return false
        return true
    }

    // ─── S7-1: Sliding-window rate limiter ───────────────────────────────

    companion object {
        private const val MAX_REQUESTS_PER_SECOND = 3
        private const val RATE_WINDOW_MS = 1000L

        // #CALLS: vchat API base + apiKey из calls SDK.
        // apiKey для VK web/MVK = CGMMEJLGDIHBABABA (подтверждён из vchat.clientStats
        // рабочего веб-звонка vk_web2). CIOPGQJGDIHBABABA — OK-версия (210 DISABLED).
        private val VCHAT_BASES = listOf(
            "https://calls.okcdn.ru",
            "https://api.mycdn.me",
            "https://calls-test.okcdn.ru",
        )
        private val VCHAT_API_KEYS = listOf(
            "CGMMEJLGDIHBABABA",
            "7793118",
            "android_web",
            "0",
        )
        // #CALLS: рабочие хост и ключ (подтверждены тестами — остальные дают
        // 101 PARAM_API_KEY). Для новых методов используем только эти.
        private const val VCHAT_BASE = "https://calls.okcdn.ru"
        private const val VCHAT_API_KEY = "CGMMEJLGDIHBABABA"

        // ═══ Fix #236: VK docs allowed/blocked extensions ═══
        // VK docs upload-сервер (docs.getMessagesUploadServer type="doc")
        // принимает ограниченный набор расширений. Источник: VK API docs
        // (dev.vk.com/ru/api/upload/document-in-profile) + logcat-наблюдения:
        // ✅ pdf, crt — прошли; ❌ mp3, apk — отклонены.
        //
        // Аудио (mp3/wav/flac/…) и видео (mp4/avi/…) должны идти через свои
        // pipeline-ы (audio.getUploadServer / video.save), а не через docs.
        // Executables (apk/exe/msi/jar/…) заблокированы VK по безопасности.
        //
        // Список ниже — whitelist для docs-pipeline. Если расширение не здесь
        // и не в audio/video/executable — даём пользователю понятную ошибку.

        /** Разрешённые расширения для docs.getMessagesUploadServer(type="doc"). */
        val VK_DOC_ALLOWED_EXTENSIONS: Set<String> = setOf(
            // Документы
            "txt", "rtf", "doc", "docx", "pdf", "fb2", "epub", "djvu", "djv",
            "html", "htm", "odt", "ods", "odp", "xls", "xlsx", "ppt", "pptx",
            "csv", "md", "tex", "wpd",
            // Архивы
            "zip", "rar", "7z", "gz", "tar", "bz2", "xz", "cab",
            // Изображения (через docs тоже можно)
            "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif", "webp", "svg", "psd",
            // Код/данные
            "json", "xml", "sql", "js", "css", "java", "kt", "py", "go", "rs",
            "c", "cpp", "h", "hpp", "cs", "rb", "php", "swift", "yml", "yaml",
            "toml", "ini", "cfg", "conf", "sh", "bat", "ps1",
            // Ключи/сертификаты
            "crt", "cer", "pem", "key", "der", "p12", "pfx",
            // Прочее
            "ics", "vcf", "m3u", "m3u8", "torrent", "srt", "ass",
        )

        /** Аудио-расширения — НЕ принимаются docs, нужен audio-pipeline. */
        val VK_AUDIO_EXTENSIONS: Set<String> = setOf(
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "aiff", "opus",
            "alac", "ape", "mka",
        )

        /** Видео-расширения — НЕ принимаются docs, нужен video-pipeline. */
        val VK_VIDEO_EXTENSIONS: Set<String> = setOf(
            "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "m4v", "3gp",
            "mpg", "mpeg", "ts", "vob",
        )

        /** Исполняемые/установочные — заблокированы VK по безопасности. */
        val VK_EXECUTABLE_EXTENSIONS: Set<String> = setOf(
            "apk", "exe", "msi", "dmg", "deb", "rpm", "jar", "war", "aar",
            "dll", "so", "dylib", "bin", "run", "app",
            "bat", "cmd", "com", "scr", "sh", "ps1", "vbs",
            "iso", "img", "vmdk", "vdi", "wim",
        )

        /**
         * Fix #124 + Fix #273: Методы, отсутствующие в allowlist'е WEB-шлюза
         * (web.api.vk.ru). VK Web gateway возвращает для них err=3
         * "Unknown method passed". Эти методы форсируются через api.vk.com
         * даже если netUseWebApiGateway=true.
         *
         * Подтверждённые:
         *  - messages.setConversationPushSettings (mute/unmute чатов).
         *    Источник: VK Web JS bundles не содержат этого метода.
         *  - account.setSilenceMode (Fix #273: fallback для mute/unmute).
         *    Добавлен превентивно — это старый метод, который может не быть
         *    в web-allowlist'е. Форсируем через api.vk.com, где он точно работает.
         *
         * Если всплывёт ещё err=3 на web-gateway — добавить метод сюда.
         */
        private val WEB_INCOMPATIBLE_METHODS: Set<String> = setOf(
            "messages.setConversationPushSettings",
            "account.setSilenceMode",
            // Fix #274: mute/pin/unread операции над диалогами — форсируем через
            // api.vk.com, т.к. web-шлюз может не знать эти методы.
            "messages.markAsImportantConversation",
            "messages.markAsUnreadConversation",
        )

        // ═══ Fix #47: Safe JSON extractors ═══
        // VK web API (vk1.a.* token) возвращает richer format чем стандартный API.
        // Некоторые поля, которые мы ожидаем как String/Int/Long, могут быть
        // JsonObject или JsonArray → getAsString/getAsInt бросает UnsupportedOperationException.
        // Эти хелперы проверяют isJsonPrimitive перед вызовом, возвращая null иначе.

        /** Безопасно извлекает String из JsonElement. Не бросает на JsonObject/Array/null. */
        fun safeString(e: JsonElement?): String? {
            if (e == null || e.isJsonNull) return null
            if (!e.isJsonPrimitive) return null
            return try { e.asString } catch (_: Exception) { null }
        }

        /**
         * §37.12 #325: Безопасно извлекает JsonObject из поля.
         *
         * Gson's `JsonObject.getAsJsonObject(name)` бросает ClassCastException если
         * поле существует, но это JsonPrimitive (число/строка/bool), а не JsonObject.
         * Это ломает весь парсер: один clip с `comments: 5` (int вместо {count:5})
         * валит shortVideo.getRecom → fallback на newsfeed → clips без files[] →
         * чёрный экран.
         *
         * Этот helper проверяет isJsonObject перед cast, возвращая null иначе.
         */
        fun getObj(o: JsonObject, name: String): JsonObject? {
            val el = o.get(name) ?: return null
            if (el.isJsonNull || !el.isJsonObject) return null
            return el.asJsonObject
        }

        /** §37.12 #325: Безопасно извлекает JsonArray из поля (аналогично getObj). */
        fun getArr(o: JsonObject, name: String): JsonArray? {
            val el = o.get(name) ?: return null
            if (el.isJsonNull || !el.isJsonArray) return null
            return el.asJsonArray
        }

        /** Безопасно извлекает Int из JsonElement.
         *  Fix #237: VK web-API (vk1.a.*) возвращает булевы поля (can_like,
         *  can_post, show_reply_button, deleted и т.д.) как JSON true/false,
         *  хотя классический API отдаёт 0/1. Раньше safeInt падал с
         *  NumberFormatException на "true" → ломал ВСЕ комментарии в постах
         *  (wall.getComments parse error). Теперь true→1, false→0. */
        fun safeInt(e: JsonElement?, default: Int = 0): Int {
            if (e == null || e.isJsonNull) return default
            if (!e.isJsonPrimitive) return default
            val p = e.asJsonPrimitive
            if (p.isBoolean) return if (p.asBoolean) 1 else 0
            return try { p.asInt } catch (_: Exception) {
                try { p.asString.toIntOrNull() ?: default } catch (_: Exception) { default }
            }
        }

        /** Безопасно извлекает Long из JsonElement.
         *  Fix #237: та же защита от JSON boolean, что в safeInt. */
        fun safeLong(e: JsonElement?, default: Long = 0L): Long {
            if (e == null || e.isJsonNull) return default
            if (!e.isJsonPrimitive) return default
            val p = e.asJsonPrimitive
            if (p.isBoolean) return if (p.asBoolean) 1L else 0L
            return try { p.asLong } catch (_: Exception) {
                try { p.asString.toLongOrNull() ?: default } catch (_: Exception) { default }
            }
        }

        /** Безопасно извлекает Boolean из JsonElement (VK возвращает 0/1 или true/false). */
        fun safeBool(e: JsonElement?, default: Boolean = false): Boolean {
            if (e == null || e.isJsonNull) return default
            if (!e.isJsonPrimitive) return default
            val p = e.asJsonPrimitive
            if (p.isBoolean) return p.asBoolean
            return try { p.asInt != 0 } catch (_: Exception) {
                try { p.asString.toBooleanStrictOrNull() ?: default } catch (_: Exception) { default }
            }
        }

        /**
         * Nullable-вариант safeInt: возвращает null если поля нет/isNull.
         * Fix #321: VK Clips возвращает is_favorite/is_subscribed/can_* как boolean
         * (true/false) вместо 0/1 — старый `?.asInt` падал с NumberFormatException,
         * ломая весь newsfeedGetClipsFeed → "Клипов пока нет".
         */
        fun safeIntNullable(e: JsonElement?): Int? {
            if (e == null || e.isJsonNull) return null
            if (!e.isJsonPrimitive) return null
            val p = e.asJsonPrimitive
            if (p.isBoolean) return if (p.asBoolean) 1 else 0
            return try { p.asInt } catch (_: Exception) {
                try { p.asString.toIntOrNull() } catch (_: Exception) { null }
            }
        }

        /** Nullable-вариант safeLong (Fix #321: та же защита от boolean). */
        fun safeLongNullable(e: JsonElement?): Long? {
            if (e == null || e.isJsonNull) return null
            if (!e.isJsonPrimitive) return null
            val p = e.asJsonPrimitive
            if (p.isBoolean) return if (p.asBoolean) 1L else 0L
            return try { p.asLong } catch (_: Exception) {
                try { p.asString.toLongOrNull() } catch (_: Exception) { null }
            }
        }
    }

    /** Thread-safe list of recent request timestamps (millis). */
    private val requestTimestamps = java.util.concurrent.CopyOnWriteArrayList<Long>()

    // ═══ Fix #47: Новые методы из реального дампа VK API v5.282 ═══
    // Источник: профиль.zip → apiPrefetchCache (см. VK_IMPORT_API.MD §16)

    /**
     * users.getContentTabs — табы контента профиля (v5.282).
     * Возвращает список табов: audios, videos, wall, photos, short_videos, narratives, archive_wall.
     * Используется мобильным VK для рендера вкладок на странице профиля.
     *
     * @param userId ID пользователя (или null для текущего).
     * @return List<ContentTab> или emptyList при ошибке.
     */
    suspend fun usersGetContentTabs(userId: Long? = null): List<ContentTab> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf("scrollable_tabs" to "1")
        if (userId != null) args["user_id"] = userId.toString()
        val json = call("users.getContentTabs", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()
            val tabsArr = resp.getAsJsonArray("tabs_settings") ?: return emptyList()
            tabsArr.mapNotNull { t ->
                if (!t.isJsonObject) return@mapNotNull null
                val o = t.asJsonObject
                val name = safeString(o.get("name")) ?: return@mapNotNull null
                val contentTypes = o.getAsJsonArray("content_types")?.mapNotNull { ct ->
                    safeString(ct)
                } ?: emptyList()
                ContentTab(
                    name = name,
                    toSectionButton = safeBool(o.get("to_section_button")),
                    canAddButton = safeBool(o.get("can_add_button")),
                    contentTypes = contentTypes,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGetContentTabs parse error", e)
            emptyList()
        }
    }

    /**
     * users.getWallTabs — фильтры стены профиля (v5.282).
     * Возвращает список фильтров: all, owner, archived — с count.
     *
     * @param userId ID пользователя (или null для текущего).
     * @return List<WallTab> или emptyList при ошибке.
     */
    suspend fun usersGetWallTabs(userId: Long? = null): List<WallTab> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf<String, String>()
        if (userId != null) args["user_id"] = userId.toString()
        val json = call("users.getWallTabs", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonArray("response") ?: return emptyList()
            resp.mapNotNull { t ->
                if (!t.isJsonObject) return@mapNotNull null
                val o = t.asJsonObject
                WallTab(
                    type = safeString(o.get("type")) ?: return@mapNotNull null,
                    title = safeString(o.get("title")) ?: "",
                    count = safeInt(o.get("count")),
                    isWallOwn = safeBool(o.get("is_wall_own")),
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGetWallTabs parse error", e)
            emptyList()
        }
    }

    /**
     * utils.resolveScreenName — resolve короткого имени → object_id + type (v5.282).
     *
     * @param screenName Короткое имя (например, "pluton_tut" или "durov").
     * @return Pair(objectId, type) где type ∈ {"user", "group", "application", "page"}.
     */
    suspend fun resolveScreenName(screenName: String): Pair<Long, String>? {
        if (isOffline()) return null
        val args = mapOf("screen_name" to screenName)
        val json = call("utils.resolveScreenName", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val objectId = safeLong(resp.get("object_id")).takeIf { it != 0L } ?: return null
            val type = safeString(resp.get("type")) ?: return null
            objectId to type
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "resolveScreenName parse error", e)
            null
        }
    }

    /**
     * account.getCounters — счётчики уведомлений (v5.282).
     * Возвращает messages, notifications, calls, channels и menu_*_badge.
     */
    suspend fun accountGetCounters(): Map<String, Any?> {
        if (isOffline()) return emptyMap()
        val json = call("account.getCounters", emptyMap()) ?: return emptyMap()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyMap()
            val result = mutableMapOf<String, Any?>()
            for ((key, value) in resp.entrySet()) {
                result[key] = when {
                    value.isJsonPrimitive -> safeString(value) ?: safeLong(value)
                    value.isJsonObject -> value.toString()
                    else -> null
                }
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "accountGetCounters parse error", e)
            emptyMap()
        }
    }

    // ─── #CALLS: звонки (queuev4 + WebRTC) ───────────────────────────

    /**
     * #CALLS: queue.subscribe — получить queue-credential для queuev4.vk.ru.
     *
     * Возвращает { key, ts, url } для long-poll канала. VK web хранит это в
     * localStorage queue_credential_calls_cache_<uid>_<app_id>.
     *
     * Реальный формат (расшифрован из m.vk.ru бандла QueueManager):
     *   queue_ids = "accountcounters_<uid>"  (имя БЕЗ подчёркивания внутри)
     *   response  = { queues: [{ key, timestamp }] }  (поле `timestamp`, не `ts`)
     *
     * Вызывается с SAT-токеном (sat_token из ExchangeTokenStorage) — web-токен
     * VK не принимает для queue.subscribe (err=100). Если SAT-токена нет —
     * пробуем обычный web-токен (может не сработать).
     *
     * @param queueIdSuffix имя очереди (по умолчанию accountcounters_<uid>).
     * @return QueueCredential или null при ошибке.
     */
    override suspend fun queueSubscribe(userId: Long, queueIdSuffix: String?): QueueCredential? {
        if (isOffline()) return null
        val uid = if (userId > 0L) userId else (exchangeAuthRepository?.userId() ?: 0L)
        val suffix = queueIdSuffix ?: "accountcounters_$uid"

        // 1) SAT-токен — прямой POST к api.vk.com (queue.subscribe требует SAT).
        val sat = exchangeAuthRepository?.satToken()
        if (!sat.isNullOrBlank()) {
            try {
                val form = FormBody.Builder()
                    .add("queue_ids", suffix)
                    .add("v", VKEndpoints.API_VERSION)
                    .add("https", "1")
                    .add("access_token", sat)
                    .add("lang", "ru")
                    .build()
                val req = Request.Builder()
                    .url("${VKEndpoints.API_HOST}/method/queue.subscribe")
                    .post(form)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                        val error = json?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                        if (error == null) {
                            val parsed = parseQueueCredential(json, uid)
                            if (parsed != null) {
                                AppLog.i("VKApiClient", "queue.subscribe ok via SAT (suffix=$suffix)")
                                return parsed
                            }
                        } else {
                            val code = error.get("error_code")?.asInt ?: -1
                            AppLog.w("VKApiClient", "queue.subscribe SAT error code=$code")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "queue.subscribe SAT request error", e)
            }
        }

        // 2) Fallback: web-токен через обычный call().
        val json = call("queue.subscribe", mapOf("queue_ids" to suffix)) ?: return null
        return try {
            parseQueueCredential(json.getAsJsonObject("response") ?: json, uid)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "queueSubscribe parse error", e)
            null
        }
    }

    /** Парсит QueueCredential из response { queues: [{key, timestamp}] }. */
    private fun parseQueueCredential(resp: JsonObject?, uid: Long): QueueCredential? {
        if (resp == null) return null
        val queues = resp.getAsJsonArray("queues") ?: return null
        val first = queues.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val key = safeString(first.get("key")) ?: return null
        // Поле `timestamp` (не `ts`) — см. мобильный бандл: { key, timestamp } = t.queues[0]
        val ts = first.get("timestamp")
            ?.takeIf { it.isJsonPrimitive }
            ?.asLong
            ?: safeLong(first.get("ts"))
        return QueueCredential(
            key = key,
            ts = ts,
            url = "https://queuev4.vk.ru/im1180",
            userId = uid,
        )
    }

    // ─── #CALLS: vchat API (calls.okcdn.ru) — звонки ────────────────

    /**
     * #CALLS: получить anonym-токен для звонков.
     *
     * VK Calls desktop (client_id=7793118) использует:
     *   GET https://oauth.vk.ru/get_anonym_token?device_id=<uuid>&client_id=7793118&client_secret=<...>
     *   → { token: "anonym.eyJ...", expired_at: <unix> }
     *
     * Токен нужен как auth_token в auth.anonymLogin и anonymToken в vchat-запросах.
     *
     * @return anonym token или null.
     */
    suspend fun getAnonymToken(): String? {
        if (isOffline()) return null
        return try {
            val deviceId = exchangeAuthRepository?.deviceId() ?: return null
            val url = "https://oauth.vk.ru/get_anonym_token?device_id=$deviceId&client_id=7793118"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                if (!resp.isSuccessful) return@use null
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                safeString(json.get("token"))
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "getAnonymToken error", e)
            null
        }
    }

    /**
     * #CALLS (2026-08-24): messages.getCallToken — источник $-токена ($Ksd...)
     * для auth.anonymLogin (version=3). Именно его вызывает m.vk.ru SPA при
     * логине и сохраняет в localStorage `calls_token_with_url_<uid>`.
     *
     * Метод требует авторизацию как у веб-приложения (cookies из CookieManager
     * + access_token). Вызывать СРАЗУ после WebView-логина (OAuthWebViewActivity
     * onTokenReceived), когда VK уже поставил session cookies в CookieManager.
     *
     * @param accessToken access_token (vk1.a.*)
     * @param cookieHeader полный Cookie-заголовок из CookieManager (remixsid, httoken...)
     * @return $-токен (например "$Ksd1qVP...") или null.
     */
    suspend fun getCallToken(accessToken: String, cookieHeader: String): String? {
        if (isOffline()) return null
        return try {
            val form = FormBody.Builder()
                .add("v", "5.275")
                .add("access_token", accessToken)
                .add("env", "production")
                .build()
            val req = Request.Builder()
                .url("https://api.vk.com/method/messages.getCallToken")
                .post(form)
                .header("Cookie", cookieHeader)
                .header("Origin", "https://m.vk.ru")
                .header("Referer", "https://m.vk.ru/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6943.137 Mobile Safari/537.36")
                // Полный набор браузерных заголовков, как у Chrome/m.vk.ru SPA.
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"133\", \"Google Chrome\";v=\"133\"")
                .header("Sec-Ch-Ua-Mobile", "?1")
                .header("Sec-Ch-Ua-Platform", "\"Android\"")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    AppLog.w("VKApiClient", "messages.getCallToken HTTP ${resp.code}: ${body.take(100)}")
                    return@use null
                }
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                if (json.has("error")) {
                    AppLog.w("VKApiClient", "messages.getCallToken error: ${json.get("error")}")
                    return@use null
                }
                val respObj = json.get("response")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                val token = respObj?.get("token")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: respObj?.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
                if (!token.isNullOrBlank()) {
                    AppLog.i("VKApiClient", "messages.getCallToken OK (len=${token.length})")
                } else {
                    AppLog.w("VKApiClient", "messages.getCallToken: нет token в ответе: ${body.take(150)}")
                }
                token
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "getCallToken error", e)
            null
        }
    }

    /**
     * #CALLS: auth.anonymLogin — получить session_key для звонков.
     *
     * Из calls SDK:
     *   auth.anonymLogin({
     *     session_data: { device_id, client_version, client_type:"SDK_JS",
     *                    auth_token: <callToken>, version: 3 },
     *     application_key: <apiKey>
     *   }) → { session_key, session_secret_key, uid }
     *
     * callToken — из oauth.vk.ru/get_anonym_token (client_id=7793118).
     * session_key используется в vchat-запросах как `session_key`.
     *
     * @return session_key или null.
     */
    suspend fun vchatAnonymLogin(callToken: String, apiKey: String, deviceId: String): String? {
        if (isOffline()) return null
        return try {
            // #CALLS-FIX (2026-08-24): рабочий вариант — auth.anonymLogin С auth_token
            // ($-токен) и version=3 даёт session_key правильного формата (-w-fl..., 156),
            // который принимает vchat.getConversationParams. БЕЗ auth_token (version=2)
            // сервер даёт ключ -w-vF... (134), который vchat НЕ принимает (100 must be specified).
            val form = FormBody.Builder()
                .add("method", "auth.anonymLogin")
                .add("format", "JSON")
                .add("application_key", apiKey)
                .add("session_data", JsonObject().apply {
                    addProperty("device_id", deviceId)
                    // #CALLS-FIX (2026-08-24): эталон Chrome desktop использует
                    // client_version=1.1 (у нас было 2.0.0 — сервер мог его
                    // отклонять как неизвестную версию).
                    addProperty("client_version", "1.1")
                    addProperty("client_type", "SDK_JS")
                    if (callToken.isNotBlank()) {
                        addProperty("auth_token", callToken)
                        addProperty("version", 3)
                    } else {
                        addProperty("version", 2)
                    }
                }.toString())
                .build()
            for (base in VCHAT_BASES) {
                val res = try {
                    val req = Request.Builder().url("$base/fb.do").post(form).build()
                    httpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            AppLog.w("VKApiClient", "auth.anonymLogin $base HTTP ${resp.code}: ${body.take(100)}")
                            return@use null
                        }
                        val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                        if (json?.has("error_code") == true) {
                            AppLog.w("VKApiClient", "auth.anonymLogin $base error: ${json}")
                            return@use null
                        }
                        json?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?: json
                    }
                } catch (e: Exception) {
                    AppLog.w("VKApiClient", "auth.anonymLogin $base failed: ${e.message}")
                    null
                }
                if (res != null) {
                    // #CALLS-FIX: okcdn uid (584520805550) — НЕ VK user_id (171093180).
                    // Нужен для userId в WS URL сигналинга (иначе invalid-token).
                    val okUid = safeString(res.get("uid"))?.toLongOrNull()
                    if (okUid != null && okUid > 0L) lastAnonymUid = okUid
                    return safeString(res.get("session_key"))
                }
            }
            null
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatAnonymLogin error", e)
            null
        }
    }

    /**
     * #CALLS: vchat.getConversationParams — параметры звонка (STUN/TURN/token/endpoint).
     *
     * Формат (из calls SDK Zs):
     *   POST https://calls.okcdn.ru/fb.do
     *   Content-Type: application/x-www-form-urlencoded
     *   body: method=vchat.getConversationParams&format=JSON&conversation_id=<id>
     *         &application_key=<apiKey>
     *         &session_key=<sessionKey>   (приоритет; из auth.anonymLogin)
     *         ИЛИ &access_token=<token>
     *
     * @return JsonObject response или null.
     */
    suspend fun vchatGetConversationParams(
        conversationId: String,
        sessionKey: String? = null,
        anonymToken: String? = null,
    ): JsonObject? {
        if (isOffline()) return null
        // Пробуем каждый хост и apiKey (error 101 = неверный application_key).
        for (base in VCHAT_BASES) {
            for (apiKey in VCHAT_API_KEYS) {
                val res = try {
                    val form = FormBody.Builder()
                        .add("method", "vchat.getConversationParams")
                        .add("format", "JSON")
                        .add("conversation_id", conversationId)
                        .add("application_key", apiKey)
                    if (!sessionKey.isNullOrBlank()) {
                        form.add("session_key", sessionKey)
                    } else if (!anonymToken.isNullOrBlank()) {
                        form.add("anonymToken", anonymToken)
                    } else {
                        val tk = token()
                        if (!tk.isNullOrBlank()) form.add("access_token", tk)
                    }
                    val req = Request.Builder()
                        .url("$base/fb.do")
                        .post(form.build())
                        .build()
                    // #CALLS-IN-FIX (2026-08-29): общий клиент ждёт readTimeout=45с
                    // (рассчитан на long-poll). Для vchat это слишком долго: при сбое
                    // резолв conversation params входящего звонка молча висел 45с+,
                    // сигналинг не поднимался, принять звонок было нельзя. Бужем
                    // КАЖДЫЙ вызов 10с — в норме vchat отвечает за <1с.
                    httpClient.newCall(req).apply {
                        timeout().timeout(10_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }.execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            AppLog.w("VKApiClient", "vchat $base key=$apiKey HTTP ${resp.code}: ${body.take(100)}")
                            return@use null
                        }
                        val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                        if (json?.has("error") == true || json?.has("error_code") == true) {
                            AppLog.w("VKApiClient", "vchat $base key=$apiKey error: ${json}")
                            return@use null
                        }
                        json?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: json
                    }
                } catch (e: Exception) {
                    AppLog.w("VKApiClient", "vchat $base key=$apiKey failed: ${e.message}")
                    null
                }
                if (res != null) return res
            }
        }
        AppLog.e("VKApiClient", "vchatGetConversationParams: все хосты/ключи недоступны")
        return null
    }

    /**
     * #CALLS (2026-08-24): vchat.system.getInfo — системный запрос перед
     * startConversation (эталон Chrome desktop vk.ru вызывает его первым
     * после auth.anonymLogin).
     *
     * POST https://calls.okcdn.ru/fb.do
     *   method=system.getInfo
     *   &format=JSON
     *   &application_key=CGMMEJLGDIHBABABA
     *   &session_key=<session_key>
     *
     * @return JsonObject response или null.
     */
    override suspend fun vchatSystemGetInfo(sessionKey: String): JsonObject? {
        if (isOffline()) return null
        if (sessionKey.isBlank()) return null
        return try {
            val form = FormBody.Builder()
                .add("method", "system.getInfo")
                .add("format", "JSON")
                .add("application_key", VCHAT_API_KEY)
                .add("session_key", sessionKey)
                .build()
            val req = Request.Builder()
                .url("$VCHAT_BASE/fb.do")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    AppLog.w("VKApiClient", "vchat.system.getInfo HTTP ${resp.code}: ${body.take(100)}")
                    return@use null
                }
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                if (json?.has("error") == true || json?.has("error_code") == true) {
                    AppLog.w("VKApiClient", "vchat.system.getInfo error: ${json}")
                    return@use null
                }
                json?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: json
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatSystemGetInfo error", e)
            null
        }
    }

    /**
     * #CALLS: vchat.createJoinLink — получить join-ссылку для приглашения в звонок.
     *
     * POST https://calls.okcdn.ru/fb.do
     *   method=vchat.createJoinLink
     *   conversationId=<call_id>
     *   application_key=CGMMEJLGDIHBABABA
     *   session_key=<session_key из _okcls_anonymLogin>
     *
     * @return join_link (base64url токен) или null.
     */
    override suspend fun vchatCreateJoinLink(conversationId: String, sessionKey: String): String? {
        if (isOffline()) return null
        return try {
            val form = FormBody.Builder()
                .add("method", "vchat.createJoinLink")
                .add("format", "JSON")
                .add("conversationId", conversationId)
                .add("application_key", VCHAT_API_KEY)
                .add("session_key", sessionKey)
                .build()
            val req = Request.Builder()
                .url("$VCHAT_BASE/fb.do")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                if (!resp.isSuccessful) {
                    AppLog.w("VKApiClient", "vchat.createJoinLink HTTP ${resp.code}: ${body.take(120)}")
                    return@use null
                }
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return@use null
                if (json.has("error") || json.has("error_code")) {
                    AppLog.w("VKApiClient", "vchat.createJoinLink error: $json")
                    return@use null
                }
                val respObj = json.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: json
                safeString(respObj.get("join_link"))
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatCreateJoinLink error", e)
            null
        }
    }

    /**
     * #CALLS (2026-08-26): vchat.joinConversation — присоединиться/принять звонок.
     * HTTP-эквивалент accept-call + offer/answer. Именно этот метод вызывает
     * Chrome desktop (vk.ru) при нажатии «Войти» (join), а не WebSocket.
     *
     * POST https://calls.okcdn.ru/fb.do
     *   method=vchat.joinConversation
     *   conversationId=<id>
     *   application_key=CGMMEJLGDIHBABABA
     *   session_key=<session_key>
     *   isVideo=false
     *   mediaSettings={"isAudioEnabled":true,...}
     *
     * @param conversationId conversation id
     * @param sessionKey session_key
     * @param isVideo флаг видео
     * @return JsonObject response или null
     */
    override suspend fun vchatJoinConversation(
        conversationId: String,
        sessionKey: String,
        isVideo: Boolean,
    ): JsonObject? {
        if (isOffline()) return null
        if (sessionKey.isBlank()) return null
        return try {
            val mediaSettings = JsonObject().apply {
                addProperty("isAudioEnabled", true)
                addProperty("isVideoEnabled", isVideo)
                addProperty("isScreenSharingEnabled", false)
                addProperty("isFastScreenSharingEnabled", false)
                addProperty("isAudioSharingEnabled", false)
                addProperty("isAnimojiEnabled", false)
                addProperty("isDataEnabled", false)
                addProperty("videoBitrateBps", 0)
                addProperty("audioBitrateBps", 0)
            }
            val form = FormBody.Builder()
                .add("method", "vchat.joinConversation")
                .add("format", "JSON")
                .add("conversationId", conversationId)
                .add("application_key", VCHAT_API_KEY)
                .add("session_key", sessionKey)
                .add("mediaSettings", mediaSettings.toString())
                .build()
            val req = Request.Builder()
                .url("$VCHAT_BASE/fb.do")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    AppLog.w("VKApiClient", "vchat.joinConversation HTTP ${resp.code}: ${body.take(100)}")
                    return@use null
                }
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                if (json?.has("error") == true || json?.has("error_code") == true) {
                    val msg = json.toString()
                    if (msg.contains("is blocked") || msg.contains("PERMISSION_DENIED")) {
                        // #CALLS-WAF (2026-08-31): антифрод VK ограничил метод для
                        // устройства/IP — «Method ... is blocked for <id> from IP <ip>».
                        // Это СЕРВЕРНОЕ ограничение (WAF), не баг клиента: при нём
                        // SERVER-топология (SFU) для PinoK недоступна, а DIRECT-звонки
                        // может не пускать тот же WAF (нет FULL_CONNECTION/registered-peer,
                        // 0 ответов ICE у пира). Диагностика: сменить сеть (Wi-Fi ↔
                        // мобильная, другой IP) и повторить; обычно отпускает само.
                        AppLog.e("VKApiClient", "#CALLS-WAF: сервер ограничил vchat.joinConversation (антифрод по IP/устройству): $msg")
                    } else {
                        AppLog.w("VKApiClient", "vchat.joinConversation error: $json")
                    }
                    return@use null
                }
                json?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: json
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatJoinConversation error", e)
            null
        }
    }

    /**
     * #CALLS (2026-08-26): vchat.hangupConversation — завершить звонок через HTTP.
     * Альтернатива WS-команде hangup. Используется Chrome desktop.
     *
     * POST https://calls.okcdn.ru/fb.do
     *   method=vchat.hangupConversation
     *   conversationId=<id>
     *   application_key=CGMMEJLGDIHBABABA
     *   session_key=<session_key>
     *   reason=hungup
     */
    override suspend fun vchatHangupConversation(
        conversationId: String,
        sessionKey: String,
        reason: String,
    ): Boolean {
        if (isOffline()) return false
        if (sessionKey.isBlank()) return false
        return try {
            val form = FormBody.Builder()
                .add("method", "vchat.hangupConversation")
                .add("format", "JSON")
                .add("conversationId", conversationId)
                .add("application_key", VCHAT_API_KEY)
                .add("session_key", sessionKey)
                .add("reason", reason)
                .build()
            val req = Request.Builder()
                .url("$VCHAT_BASE/fb.do")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    AppLog.w("VKApiClient", "vchat.hangupConversation HTTP ${resp.code}: ${body.take(100)}")
                    return@use false
                }
                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                if (json?.has("error") == true || json?.has("error_code") == true) {
                    AppLog.w("VKApiClient", "vchat.hangupConversation error: $json")
                    return@use false
                }
                true
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatHangupConversation error", e)
            false
        }
    }

    /**
     * #CALLS: vchat.startConversation — создать активную conversation
     * для ИСХОДЯЩЕГО звонка. Без неё сервер сразу закрывает WS: conversation-ended
     * (INITIALLY_CLOSED). Вызывается после messages.startCall.
     *
     * ФОРМАТ — ТОЧНЫЙ эталон из рабочего звонка Chrome desktop vk.ru (2026-08-24):
     *   conversationId=<UUID>
     *   &isVideo=false
     *   &protocolVersion=5
     *   &payload={"is_video":false,"with_join_link":false,"join_by_link":false,
     *             "community_user_id":0,"caller_app_id":6287487}
     *   &onlyAdminCanShareMovie=false
     *   &externalIds=<sopbesednik vk user_id>   (НЕ uids!)
     *   &method=vchat.startConversation
     *   &format=JSON
     *   &application_key=CGMMEJLGDIHBABABA
     *   &session_key=-w-fl...
     *
     * Отличия от прежней версии (вероятная причина INITIALLY_CLOSED):
     *   - externalIds ВМЕСТО uids (браузер передаёт externalIds!)
     *   - payload с caller_app_id (мы не передавали вообще)
     *   - onlyAdminCanShareMovie=false
     *   - НЕТ createJoinLink (браузер передаёт with_join_link:false в payload)
     *   - НЕТ capabilities
     *
     * @param conversationId call_id из messages.startCall
     * @param sessionKey session_key (правильный формат -w-fl...)
     * @param peerUid VK user_id собеседника (для externalIds)
     * @param callerAppId app_id звонка (эталон desktop = 6287487)
     * @return JsonObject response или null.
     */
    override suspend fun vchatStartConversation(
        conversationId: String,
        sessionKey: String?,
        peerUid: Long,
        callerAppId: Long,
    ): JsonObject? {
        if (isOffline()) return null
        if (sessionKey.isNullOrBlank()) {
            AppLog.w("VKApiClient", "vchatStartConversation: session_key пуст")
            return null
        }
        return try {
            val payload = JsonObject().apply {
                addProperty("is_video", false)
                addProperty("with_join_link", false)
                addProperty("join_by_link", false)
                addProperty("community_user_id", 0)
                addProperty("caller_app_id", callerAppId)
            }
            val form = FormBody.Builder()
                .add("method", "vchat.startConversation")
                .add("format", "JSON")
                .add("conversationId", conversationId)
                .add("application_key", VCHAT_API_KEY)
                .add("session_key", sessionKey)
                // #CALLS-FIX (2026-08-24): точный эталон Chrome desktop —
                // externalIds ВМЕСТО uids, payload, onlyAdminCanShareMovie.
                .add("isVideo", "false")
                .add("protocolVersion", "5")
                .add("payload", payload.toString())
                .add("onlyAdminCanShareMovie", "false")
                .add("externalIds", peerUid.toString())
                .build()
            for (base in VCHAT_BASES) {
                val res = try {
                    val req = Request.Builder().url("$base/fb.do").post(form).build()
                    httpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            AppLog.w("VKApiClient", "vchat.startConversation $base HTTP ${resp.code}: ${body.take(100)}")
                            return@use null
                        }
                        val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                        if (json?.has("error") == true || json?.has("error_code") == true) {
                            AppLog.w("VKApiClient", "vchat.startConversation $base error: ${json}")
                            return@use null
                        }
                        json?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: json
                    }
                } catch (e: Exception) {
                    AppLog.e("VKApiClient", "vchat.startConversation $base exception: ${e.message}", e)
                    if (e is java.io.IOException) {
                        AppLog.e("VKApiClient", "startConversation IOException cause=${e.cause?.message}")
                    }
                    null
                }
                if (res != null) {
                    AppLog.i("VKApiClient", "vchat.startConversation OK")
                    return res
                }
            }
            AppLog.e("VKApiClient", "vchat.startConversation: все хосты недоступны")
            null
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "vchatStartConversation error", e)
            null
        }
    }

    /**
     * #CALLS: messages.startCall — инициировать звонок собеседнику.
     *
     * VK API: messages.startCall { peer_id, voice? } → response { call_id }.
     * Дальше обе стороны лонг-поллят queuev4 и обмениваются WebRTC SDP/ICE.
     *
     * @return call_id (string) или null.
     */
    override suspend fun messagesStartCall(peerId: Long, video: Boolean): String? {
        if (isOffline()) return null
        val args = mutableMapOf("peer_id" to peerId.toString())
        if (!video) args["voice"] = "1"
        val json = call("messages.startCall", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            safeString(resp.get("call_id"))
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesStartCall parse error", e)
            null
        }
    }

    /**
     * #CALLS: messages.getCurrentCalls — текущие активные звонки.
     * VK API: messages.getCurrentCalls → response.items[] = [...]
     */
    override suspend fun messagesGetCurrentCalls(): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getCurrentCalls", emptyMap()) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetCurrentCalls parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getInboundCalls — история входящих звонков.
     * VK API: messages.getInboundCalls { count=30 } → response.items[] = [...]
     */
    // Task 22: override CallsApi (:feature:calls). Дефолт count=30 легален
    // поверх интерфейса без дефолта (переопределение не переобъявляет дефолт).
    override suspend fun messagesGetInboundCalls(count: Int): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getInboundCalls", mapOf("count" to count.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetInboundCalls parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS (2026-08-27): calls.getHistory — история звонков (как Chrome desktop).
     * Формат ответа: { response: { items: [{ peer_id, name, photo, direction, date, duration, ... }] } }
     */
    override suspend fun callsGetHistory(count: Int, offset: Int): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("calls.getHistory", mapOf("count" to count.toString(), "offset" to offset.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "callsGetHistory parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS (2026-08-27): calls.getMissedCalls — пропущенные звонки.
     */
    suspend fun callsGetMissedCalls(count: Int = 30): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("calls.getMissedCalls", mapOf("count" to count.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "callsGetMissedCalls parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getScheduledCalls — запланированные звонки.
     */
    suspend fun messagesGetScheduledCalls(count: Int = 30): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getScheduledCalls", mapOf("count" to count.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetScheduledCalls parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getCallParticipants — участники звонка.
     */
    suspend fun messagesGetCallParticipants(callId: String): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getCallParticipants", mapOf("call_id" to callId)) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetCallParticipants parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getCallPreview — превью звонка.
     */
    suspend fun messagesGetCallPreview(callId: String): JsonObject? {
        if (isOffline()) return null
        val json = call("messages.getCallPreview", mapOf("call_id" to callId)) ?: return null
        return try {
            json.getAsJsonObject("response")
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetCallPreview parse error", e)
            null
        }
    }

    /**
     * #CALLS: messages.getGroupsForCall — группы доступные для звонка.
     */
    suspend fun messagesGetGroupsForCall(): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getGroupsForCall", emptyMap()) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetGroupsForCall parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getCallRecordings — записи звонков.
     * VK API: messages.getCallRecordings { count=30 } → response.items[] = [...]
     */
    override suspend fun messagesGetCallRecordings(count: Int): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getCallRecordings", mapOf("count" to count.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetCallRecordings parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.getCallTranscriptions — расшифровки звонков.
     * VK API: messages.getCallTranscriptions { count=30 } → response.items[] = [...]
     */
    override suspend fun messagesGetCallTranscriptions(count: Int): List<JsonObject> {
        if (isOffline()) return emptyList()
        val json = call("messages.getCallTranscriptions", mapOf("count" to count.toString())) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            items.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesGetCallTranscriptions parse error", e)
            emptyList()
        }
    }

    /**
     * #CALLS: messages.editCall — редактировать звонок.
     * @return true при успехе.
     */
    suspend fun messagesEditCall(callId: String, name: String? = null, scheduledDate: Long? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf("call_id" to callId)
        name?.let { args["name"] = it }
        scheduledDate?.let { args["scheduled_date"] = it.toString() }
        val json = call("messages.editCall", args) ?: return false
        return json.get("response")?.takeIf { it.isJsonObject } != null
    }

    /**
     * #CALLS: messages.deleteScheduledCall — удалить запланированный звонок.
     */
    suspend fun messagesDeleteScheduledCall(callId: String): Boolean {
        if (isOffline()) return false
        val json = call("messages.deleteScheduledCall", mapOf("call_id" to callId)) ?: return false
        return json.get("response")?.takeIf { it.isJsonObject } != null
    }

    /**
     * #CALLS: messages.forceCallFinish — принудительно завершить звонок.
     */
    suspend fun messagesForceCallFinish(callId: String): Boolean {
        if (isOffline()) return false
        val json = call("messages.forceCallFinish", mapOf("call_id" to callId)) ?: return false
        return json.get("response")?.takeIf { it.isJsonObject } != null
    }

    /**
     * #CALLS: messages.vkRoomsJoinCall — присоединиться к звонку (vk rooms).
     */
    suspend fun messagesVkRoomsJoinCall(callId: String): String? {
        if (isOffline()) return null
        val json = call("messages.vkRoomsJoinCall", mapOf("call_id" to callId)) ?: return null
        return try {
            val resp = json.getAsJsonObject("response")
            safeString(resp?.get("join_url"))
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "messagesVkRoomsJoinCall parse error", e)
            null
        }
    }

    /**
     * Fix #267 (Plan §36.12 P1-CHAT-5): account.getToggles — feature flags.
     *
     * VK API method `account.getToggles` возвращает список фича-флагов
     * (A/B тесты, rollout flags). Используется UI для ACL-gating пунктов меню:
     * «Передать права создателя» (vkm_convo_owner_right_transfer),
     * «Удалить чат» (vkm_delete_chat), и т.д.
     *
     * @return Map<flagName, enabled>. Пустая map при ошибке/оффлайне.
     */
    suspend fun accountGetTogglesExternal(): Map<String, Boolean> {
        if (isOffline()) return emptyMap()
        val json = call("account.getToggles", emptyMap()) ?: return emptyMap()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyMap()
            // Формат ответа: response.items[] = [{name, enabled, value}, ...]
            // или response = [{...}] (без items обёртки). Обрабатываем оба варианта.
            val items = resp.getAsJsonArray("items")
                ?: json.getAsJsonArray("response")
                ?: return emptyMap()
            val result = mutableMapOf<String, Boolean>()
            items.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                // Fix #268: Elvis (?:) имеет БОЛЕЕ высокий приоритет чем == (equality),
                // поэтому старая запись `asBoolean ?: asString == "1" ?: false`
                // парсилась как `(asBoolean ?: asString) == ("1" ?: false)` —
                // `"1" ?: false` всегда давало "1", а весь enabled становился
                // сравнением с строкой "1": boolean `true` == "1" → false (БАГ!).
                // Теперь явные скобки: сначала boolean, иначе сравнение value=="1".
                val enabledBool = o.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean
                val enabled = enabledBool
                    ?: (o.get("value")?.takeIf { !it.isJsonNull }?.asString == "1")
                result[name] = enabled
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "accountGetToggles parse error", e)
            emptyMap()
        }
    }

    /** Таб контента профиля (users.getContentTabs). */
    data class ContentTab(
        val name: String,
        val toSectionButton: Boolean = false,
        val canAddButton: Boolean = false,
        val contentTypes: List<String> = emptyList(),
    )

    /** Фильтр стены (users.getWallTabs). */
    data class WallTab(
        val type: String,
        val title: String,
        val count: Int = 0,
        val isWallOwn: Boolean = false,
    )

    /**
     * stories.get — получение историй из ленты (v5.282, extended=1).
     *
     * **ВАЖНО (#48 fix):** Реальная структура ответа (из дампа Лента.html):
     * ```
     * response: {
     *   count: 83,
     *   items: [               ← STORY GROUPS (не отдельные истории!)
     *     {
     *       id: "b6145da6...",  ← hash string (ID группы)
     *       name: "Author Name",
     *       type: "stories",
     *       has_unseen: true,   ← флаг непросмотренности на уровне ГРУППЫ
     *       no_author_link: false,
     *       stories: [          ← массив отдельных историй
     *         { id: 456270284, owner_id: -74006511, date, type: "photo",
     *           photo: { sizes: [...] }, access_key: "story",
     *           expires_at, is_ads, can_*, clickable_stickers, replies, ... }
     *       ]
     *     }
     *   ],
     *   groups: [...],          ← метаданные групп (id, name, photo_100, ...)
     *   profiles: [...]         ← метаданные пользователей
     * }
     * ```
     *
     * Старый парсер (до #48) итерировал `items[]` как отдельные истории и
     * падал на `parseStory` (id — hash string, не число) → `storyGroups = emptyList()`
     * → истории не отображались.
     *
     * @param count Лимит возвращаемых групп.
     * @return List<StoryGroup> или emptyList при ошибке/офлайне.
     */
    suspend fun storiesGet(count: Int = 20): List<re.pinok.data.model.StoryGroup> {
        if (isOffline()) {
            AppLog.w("VKApiClient", "storiesGet: offline mode — skipping")
            return emptyList()
        }
        // Fix #48: добавлен `fields` — реальный VK-веб запрос передаёт его,
        // без него profiles[] приходят пустыми (нет photo_100 для авторов-юзеров).
        val args = mapOf(
            "extended" to "1",
            "fields" to "first_name,last_name,first_name_gen,last_name_gen," +
                "first_name_ins,last_name_ins,screen_name,name,is_member,is_closed," +
                "photo_50,photo_100,photo_200,friend_status,is_verified,verified,sex",
            "count" to count.toString(),
        )
        val json = call("stories.get", args) ?: return emptyList()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyList()

            // 1. Карта метаданных владельцев: ownerId → (photo100, name).
            //    profiles[] — для юзеров (owner_id > 0), groups[] — для сообществ (owner_id < 0).
            val ownerPhoto = mutableMapOf<Long, String>()
            val ownerName = mutableMapOf<Long, String>()
            resp.getAsJsonArray("profiles")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = safeLong(o.get("id")).takeIf { it != 0L } ?: return@forEach
                ownerPhoto[uid] = safeString(o.get("photo_100")) ?: ""
                val fn = safeString(o.get("first_name")) ?: ""
                val ln = safeString(o.get("last_name")) ?: ""
                ownerName[uid] = "$fn $ln".trim()
            }
            resp.getAsJsonArray("groups")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val gid = safeLong(o.get("id")).takeIf { it != 0L } ?: return@forEach
                // В stories owner_id для группы = -gid (отрицательное).
                ownerPhoto[-gid] = safeString(o.get("photo_100")) ?: ""
                ownerName[-gid] = safeString(o.get("name")) ?: ""
            }

            // 2. items[] — это STORY GROUPS (не отдельные истории!).
            //    Каждая группа: { id (hash), name, type, has_unseen, stories: [...] }.
            val itemsArr = resp.getAsJsonArray("items") ?: return emptyList()
            val result = mutableListOf<re.pinok.data.model.StoryGroup>()
            for (el in itemsArr) {
                if (!el.isJsonObject) continue
                val grp = el.asJsonObject
                val groupName = safeString(grp.get("name"))
                val hasUnseen = safeBool(grp.get("has_unseen"))
                val storiesArr = grp.getAsJsonArray("stories") ?: continue

                val stories = mutableListOf<re.pinok.data.model.Story>()
                var firstOwnerId = 0L
                for (sel in storiesArr) {
                    if (!sel.isJsonObject) continue
                    val story = parseStory(sel.asJsonObject) ?: continue
                    if (firstOwnerId == 0L) firstOwnerId = story.ownerId
                    stories.add(story)
                }
                if (stories.isEmpty()) continue

                // ownerId берём из первой истории (он одинаковый внутри группы).
                val ownerId = firstOwnerId
                // Имя: предпочитаем group-level name, иначе из profiles/groups.
                val name = groupName ?: ownerName[ownerId]
                val photo = ownerPhoto[ownerId]

                result.add(
                    re.pinok.data.model.StoryGroup(
                        ownerId = ownerId,
                        name = name,
                        photo100 = photo,
                        isSeen = !hasUnseen,
                        stories = stories,
                    )
                )
            }
            AppLog.d("VKApiClient", "storiesGet: parsed ${result.size} groups " +
                "(${resp.getAsJsonArray("profiles")?.size() ?: 0} profiles, " +
                "${resp.getAsJsonArray("groups")?.size() ?: 0} groups)")
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "storiesGet parse error", e)
            emptyList()
        }
    }

    /**
     * Парсит отдельную историю (элемент массива `items[].stories[]`).
     *
     * Fix #48: реальные поля истории (из дампа Лента.html):
     * - `id` (Int), `owner_id` (Long), `date` (Long), `type` ("photo"|"video"|"link")
     * - `access_key` (String, обычно "story"), `expires_at` (Long)
     * - `photo` (Object с `sizes[]`), `video` (Object), `link` (Object)
     * - `is_ads` (Bool), `no_sound` (Bool)
     * - `can_comment/can_like/can_reply/can_see/can_share/can_hide/can_ask*` (Int/Bool)
     * - `clickable_stickers` (Object), `reaction_set_id`, `replies`, `track_code`
     * - `preloading_enabled`
     *
     * **НЕ существует** в реальном ответе: `is_seen`, `is_expired`, `is_deleted`, `views`
     * (они были в старом парсере, но всегда = 0). `has_unseen` есть только на уровне группы.
     */
    private fun parseStory(o: com.google.gson.JsonObject): re.pinok.data.model.Story? {
        val id = safeInt(o.get("id")).takeIf { it != 0 } ?: return null
        val ownerId = safeLong(o.get("owner_id")).takeIf { it != 0L } ?: return null
        val type = safeString(o.get("type")) ?: "photo"

        // Photo (для type=photo).
        val photoEl = o.getAsJsonObject("photo")
        val photo: re.pinok.data.model.Story.StoryPhoto? = if (photoEl != null) {
            val sizes = photoEl.getAsJsonArray("sizes")?.mapNotNull { s ->
                if (!s.isJsonObject) return@mapNotNull null
                val so = s.asJsonObject
                re.pinok.data.model.Story.StoryPhoto.Size(
                    url = safeString(so.get("url")) ?: return@mapNotNull null,
                    width = safeInt(so.get("width")),
                    height = safeInt(so.get("height")),
                    type = safeString(so.get("type")) ?: "",
                )
            } ?: emptyList()
            re.pinok.data.model.Story.StoryPhoto(sizes = sizes, text = safeString(photoEl.get("text")))
        } else null

        // Video (для type=video).
        val videoEl = o.getAsJsonObject("video")
        val video: re.pinok.data.model.Story.StoryVideo? = if (videoEl != null) {
            val preview = videoEl.getAsJsonObject("photo")?.let { ph ->
                val sizes = ph.getAsJsonArray("sizes")?.mapNotNull { s ->
                    if (!s.isJsonObject) return@mapNotNull null
                    val so = s.asJsonObject
                    re.pinok.data.model.Story.StoryPhoto.Size(
                        url = safeString(so.get("url")) ?: return@mapNotNull null,
                        width = safeInt(so.get("width")),
                        height = safeInt(so.get("height")),
                        type = safeString(so.get("type")) ?: "",
                    )
                } ?: emptyList()
                re.pinok.data.model.Story.StoryPhoto(sizes = sizes)
            }
            // Fix #49-1: парсим video_files (VK web-tokens используют "video_files" —
            // это Map<String,String> с mp4_144/mp4_240/mp4_360/mp4_480/mp4_720/hls).
            // Также поддерживаем legacy-ключ "files" на всякий случай.
            val filesMap: Map<String, String>? = run {
                val vf = videoEl.getAsJsonObject("video_files")
                    ?: videoEl.getAsJsonObject("files")
                    ?: return@run null
                val out = linkedMapOf<String, String>()
                for ((k, v) in vf.entrySet()) {
                    val url = safeString(v) ?: continue
                    if (url.isNotBlank()) out[k] = url
                }
                if (out.isEmpty()) null else out
            }
            re.pinok.data.model.Story.StoryVideo(
                duration = safeInt(videoEl.get("duration")),
                preview = preview,
                files = filesMap,
                player = safeString(videoEl.get("player")),
            )
        } else null

        // Link (для type=link).
        val linkEl = o.getAsJsonObject("link")
        val link: re.pinok.data.model.Story.StoryLink? = if (linkEl != null) {
            re.pinok.data.model.Story.StoryLink(
                url = safeString(linkEl.get("url")) ?: "",
                text = safeString(linkEl.get("text")),
            )
        } else null

        // Replies (опционально).
        val repliesEl = o.getAsJsonObject("replies")
        val replies: re.pinok.data.model.Story.StoryReplies? = if (repliesEl != null) {
            re.pinok.data.model.Story.StoryReplies(
                count = safeInt(repliesEl.get("count")),
                canReply = safeInt(repliesEl.get("can_reply")),
            )
        } else null

        return re.pinok.data.model.Story(
            id = id,
            ownerId = ownerId,
            date = safeLong(o.get("date")),
            type = type,
            // Эти поля отсутствуют в реальном ответе v5.282 (см. KDoc выше) — оставляем 0.
            isExpired = 0,
            isSeen = 0,
            isDeleted = 0,
            accessKey = safeString(o.get("access_key")),
            photo = photo,
            video = video,
            link = link,
            views = 0,
            replies = replies,
        )
    }

    /**
     * VK: account.setOnline — пингует VK что мы онлайн, обновляет наш `last_seen`.
     *
     * При `privacyHideLastSeen=true` — NO-OP. VK не получит ping и не обновит наш
     * last_seen, мы остаёмся «был в сети давно» для других пользователей.
     *
     * VK Android SDK вызывает этот метод каждые ~5 минут в оригинальном приложении.
     * SOVA 2.0 НЕ вызывает его автоматически (по умолчанию offline-ping disabled),
     * но этот метод доступен для будущих функций (например, presence indicator).
     *
     * @return true если ping отправлен, false если пропущен (privacy/offline/error)
     */
    suspend fun accountSetOnline(): Boolean {
        if (isOffline()) return false
        val snap = prefs.data.first()
        if (privacyMods.shouldHideLastSeen(snap)) {
            AppLog.d("VKApiClient", "accountSetOnline: skipped (privacyHideLastSeen=true)")
            return false
        }
        val json = call("account.setOnline", mapOf("voip" to "0")) ?: return false
        val ok = json.get("response")?.takeIf { !it.isJsonNull }?.asInt == 1
        if (ok) AppLog.d("VKApiClient", "accountSetOnline: ping sent")
        return ok
    }

    /**
     * VK: wall.get с фильтром для стены пользователя.
     * Из исследования: users.getWallTabs возвращает 3 фильтра (all/owner/archived).
     * @param filter "all" — все, "owner" — свои, "suggests" — предложенные, "archived" — архив
     */
    suspend fun wallGetWithFilter(
        ownerId: Long,
        filter: String = "all",
        count: Int = 20,
        offset: Int = 0,
    ): List<Post> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "count" to count.toString(),
            "offset" to offset.toString(),
            "filter" to filter,
            "extended" to "0",
        )
        val json = call("wall.get", args) ?: return emptyList()
        return try {
            val items = json.getAsJsonObject("response")?.getAsJsonArray("items") ?: return emptyList()
            val seenKeys = HashSet<Pair<Long, Long>>()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val post = parsePostMini(el.asJsonObject)
                if (post.id <= 0L || post.ownerId == 0L) return@mapNotNull null
                if (!seenKeys.add(post.ownerId to post.id)) return@mapNotNull null
                post
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "wallGetWithFilter parse error", e)
            emptyList()
        }
    }

    /**
     * VK: users.get с полным набором полей (70+ полей из исследования).
     * Расширенная версия usersGet для ProfileScreen.
     * Audit #40: переименована из usersGetFull в usersGetFullExtended,
     * чтобы избежать конфликта перегрузок с базовой версией выше.
     */
    suspend fun usersGetFullExtended(userId: Long? = null): UserProfile? {
        if (isOffline()) return null
        // #30i (profile fix): убрана filterUsersFields — могла ломать загрузку.
        val args = mutableMapOf(
            "fields" to "age_mark,about,activities,activity,bdate,books,career,city,connections," +
                "counters,country,emoji_status,first_name_acc,first_name_dat,first_name_gen," +
                "first_name_ins,friend_status,games,gifts_tooltip,has_photo,home_town,interests," +
                "is_dead,is_favorite,is_hidden_from_feed,is_subscribed,last_seen,military," +
                "movies,music,occupation,online,online_info,personal,quotes,relation,relatives," +
                "schools,screen_name,social_button_type,domain,sex,site,status,trending," +
                "tv,universities,verified,video_live,wall_default,bdate_visibility,contacts," +
                "photo_max,photo_medium_rec,photo_rec,can_ask_question,can_subscribe_posts," +
                "cover,maiden_name,nickname,photo_200,photo_400,photo_avg_color,photo_id," +
                "photo_max_size,profile_buttons,service_description,is_followers_mode_on," +
                "followers_count,home_phone,mobile_phone,photo_50,photo_100,online,last_seen," +
                "status,verified,counters",
        )
        if (userId != null) args["user_ids"] = userId.toString()
        val json = call("users.get", args) ?: return null
        return try {
            val arr = json.getAsJsonArray("response") ?: return null
            if (arr.isEmpty) return null
            val obj = arr[0].asJsonObject
            // Fix #49-5: early null check. Если VK вернул {id: null} или
            // deleted-профиль → safeLong вернёт 0 → возвращаем null с логом.
            val idVal = safeLong(obj.get("id"))
            if (idVal == 0L) {
                AppLog.w("VKApiClient", "usersGetFullExtended: id is null/0 — profile may be deleted or unavailable. keys=${obj.keySet()}")
                return null
            }
            val countersObj = obj.getAsJsonObject("counters")
            UserProfile(
                id = idVal,
                firstName = safeString(obj.get("first_name")) ?: "",
                lastName = safeString(obj.get("last_name")) ?: "",
                photo100 = safeString(obj.get("photo_100")),
                photo200 = safeString(obj.get("photo_200")),
                photoMax = safeString(obj.get("photo_max")),
                online = safeInt(obj.get("online")),
                lastSeen = obj.getAsJsonObject("last_seen")?.let { ls ->
                    UserProfile.LastSeen(
                        time = safeLong(ls.get("time")),
                        platform = safeInt(ls.get("platform")).takeIf { it != 0 },
                    )
                },
                status = safeString(obj.get("status")),
                bdate = safeString(obj.get("bdate")),
                city = obj.getAsJsonObject("city")?.let { c ->
                    UserProfile.City(title = safeString(c.get("title")) ?: "")
                },
                country = obj.getAsJsonObject("country")?.let { c ->
                    UserProfile.Country(title = safeString(c.get("title")) ?: "")
                },
                verified = safeInt(obj.get("verified")),
                followersCount = safeInt(obj.get("followers_count")),
                friendsCount = safeInt(countersObj?.get("friends")),
                counters = countersObj?.let { c ->
                    UserProfile.Counters(
                        friends = safeInt(c.get("friends")).takeIf { it != 0 },
                        followers = safeInt(c.get("followers")).takeIf { it != 0 },
                        onlineFriends = safeInt(c.get("online_friends")).takeIf { it != 0 },
                        photos = safeInt(c.get("photos")).takeIf { it != 0 },
                        videos = safeInt(c.get("videos")).takeIf { it != 0 },
                        audios = safeInt(c.get("audios")).takeIf { it != 0 },
                        groups = safeInt(c.get("groups")).takeIf { it != 0 },
                        gifts = safeInt(c.get("gifts")).takeIf { it != 0 },
                    )
                },
                domain = safeString(obj.get("domain")),
                screenName = safeString(obj.get("screen_name")),
                sex = safeInt(obj.get("sex")),
                homeTown = safeString(obj.get("home_town")),
                mobilePhone = safeString(obj.get("mobile_phone")),
                homePhone = safeString(obj.get("home_phone")),
                site = safeString(obj.get("site")),
                canWritePrivateMessage = safeInt(obj.get("can_write_private_message")),
                canPost = safeInt(obj.get("can_post")),
                friendStatus = safeInt(obj.get("friend_status")),
                isClosed = safeInt(obj.get("is_closed")),
                isFavorite = safeInt(obj.get("is_favorite")),
                isSubscribed = safeInt(obj.get("is_subscribed")),
                hasPhoto = safeInt(obj.get("has_photo")),
                wallDefault = safeString(obj.get("wall_default")),
                photoAvgColor = safeString(obj.get("photo_avg_color")),
                cover = obj.getAsJsonObject("cover")?.let { coverObj ->
                    UserProfile.Cover(
                        enabled = safeInt(coverObj.get("enabled")) == 1,
                        images = coverObj.getAsJsonArray("images")?.mapNotNull {
                            safeString(it.asJsonObject.get("url"))
                        } ?: emptyList(),
                    )
                },
                about = safeString(obj.get("about")),
                activities = safeString(obj.get("activities")),
                interests = safeString(obj.get("interests")),
                music = safeString(obj.get("music")),
                movies = safeString(obj.get("movies")),
                books = safeString(obj.get("books")),
                games = safeString(obj.get("games")),
                nickname = safeString(obj.get("nickname")),
                maidenName = safeString(obj.get("maiden_name")),
                relation = safeInt(obj.get("relation")),
                personal = obj.getAsJsonObject("personal")?.let { p ->
                    UserProfile.Personal(
                        political = safeInt(p.get("political")),
                        religions = safeString(p.get("religions")),
                        inspiredBy = safeString(p.get("inspired_by")),
                        peopleMain = safeInt(p.get("people_main")),
                        lifeMain = safeInt(p.get("life_main")),
                        smoking = safeInt(p.get("smoking")),
                        alcohol = safeInt(p.get("alcohol")),
                    )
                },
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "usersGetFull parse error", e)
            null
        }
    }

    // ─── Друзья: getOnline ───

    /**
     * VK: friends.getOnline — друзья онлайн.
     * @param userId ID пользователя (если null — текущий пользователь).
     * @return Список UserProfile (только id, firstName, lastName, photo100, online).
     */
    override suspend fun friendsGetOnline(userId: Long?): List<UserProfile> {
        if (isOffline()) return emptyList()
        val args = mutableMapOf(
            "fields" to "photo_100,photo_200,online,last_seen",
            "order" to "random",
        )
        if (userId != null) args["user_id"] = userId.toString()
        val json = call("friends.getOnline", args) ?: return emptyList()
        return try {
            val arr = json.getAsJsonArray("response") ?: return emptyList()
            arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                UserProfile(
                    id = o.get("id")?.asLong ?: return@mapNotNull null,
                    firstName = o.get("first_name")?.asString ?: "",
                    lastName = o.get("last_name")?.asString ?: "",
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    online = o.get("online")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                )
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "friendsGetOnline error", e)
            emptyList()
        }
    }

    // ─── #68: Новые API методы из архивов Уведомления + Мессенджер_чат ──

    /**
     * notifications.getRedesign — redesigned notifications feed (из архива Уведомления).
     *
     * Fix #254: ПОЛНАЯ ПЕРЕПИСКА парсера. Предыдущая версия (Fix #251/#253)
     * предполагала, что getRedesign возвращает тот же формат, что и
     * notifications.get (items + profiles + groups + next_from). Это НЕ так.
     *
     * Реальный формат getRedesign (подтверждён логом 2026-07-26 20:08:18.495):
     * ```json
     * {
     *   "response": {
     *     "last_viewed": 1784996731,
     *     "notifications": [           ← МАССИВ напрямую (не объект с items!)
     *       {
     *         "id": "jPUkdD0VRNA8iSV…",  ← строка (base64-подобная)
     *         "date": 1785085293,
     *         "image": {"type":"single_owner","owner":{"type":"group","id":203839081}},
     *         "header": "ЧП Россия | Новости | События | Расследования |",
     *         "text": "опубликовало новый пост",
     *         "action": {"type":"entity_show","entity":{"type":"post","owner_id":-203839081,"post_id":72084,"url":"...","attachments_string":"Видео"}},
     *         "attachment": {"type":"entity_array","items":[{"type":"post","owner_id":-203839081,"post_id":72084,...}]},
     *         "dots_menu": [{"type":"open_setting",...},{"type":"unsubscribe",...},{"type":"hide_notification",...}]
     *       }, ...
     *     ],
     *     "next_from": "...",
     *     "users": [...],    ← НЕ "profiles"!
     *     "groups": [...],
     *     "posts": [...], "photos": [...], "videos": [...], "stories": [...],
     *     "apps": [...], "polls": [...], "market_items": [...], "podcast_episodes": [...]
     *   }
     * }
     * ```
     *
     * Ключевые отличия от notifications.get:
     *   1. items лежат в response.notifications (массив напрямую), НЕ response.items
     *   2. item.id — строка (base64-подобная), не число
     *   3. НЕТ полей type / feedback / parent / from_id
     *      Вместо них: header (имя владельца), text (действие),
     *      action.entity (type+owner_id+post_id), image.owner (аватар),
     *      attachment.items (вложения)
     *   4. profiles лежат в response.users (не response.profiles)
     *   5. Доп. сущности: posts, photos, videos, stories, apps, polls,
     *      market_items, podcast_episodes (как extended=1 в notifications.get,
     *      но разбиты по типам, а не в одном arrays)
     *
     * Fix #254 решает «уведомления так и не появились» (после #251/#253):
     * API возвращал 211KB с 30 items, но старый парсер искал response.items
     * (которого нет) и response.notifications.items (которого нет — там массив
     * напрямую). Теперь ищем response.notifications как массив и парсим новым
     * парсером parseRedesignNotificationItem.
     */
    suspend fun notificationsGetRedesign(count: Int = 30, startFrom: String? = null): Pair<List<NotificationItem>, String?> {
        if (isOffline()) return emptyList<NotificationItem>() to null
        val args = mutableMapOf(
            "count" to count.toString(),
            "extended" to "1",
        )
        if (!startFrom.isNullOrBlank()) args["start_from"] = startFrom
        val json = call("notifications.getRedesign", args) ?: return emptyList<NotificationItem>() to null

        return try {
            // Fix #254: defensive — response может быть НЕ объектом.
            val respEl = json.get("response")
            if (respEl == null || !respEl.isJsonObject) {
                AppLog.w("VKApiClient", "getRedesign: 'response' missing or not JsonObject. json (first 500 chars): ${json.toString().take(500)}")
                return emptyList<NotificationItem>() to null
            }
            val resp = respEl.asJsonObject

            // Fix #254: items лежат в response.notifications (массив напрямую).
            // НЕ response.items (как в notifications.get) и НЕ response.notifications.items
            // (как я предполагал в Fix #253). Проверяем оба варианта для надёжности —
            // вдруг для OAuth-токенов VK вернёт старый формат.
            val notificationsEl = resp.get("notifications")
            val legacyItemsEl = resp.getAsJsonArray("items")
            val items: JsonArray = when {
                notificationsEl != null && notificationsEl.isJsonArray -> {
                    AppLog.i("VKApiClient", "getRedesign: items source=response.notifications (redesign format), count=${notificationsEl.asJsonArray.size()}")
                    notificationsEl.asJsonArray
                }
                legacyItemsEl != null -> {
                    // Старый формат (notifications.get) — для OAuth-токенов.
                    AppLog.i("VKApiClient", "getRedesign: items source=response.items (legacy format), count=${legacyItemsEl.size()}")
                    legacyItemsEl
                }
                else -> {
                    AppLog.w("VKApiClient", "getRedesign: items NOT FOUND. resp keys = [${resp.entrySet().joinToString(",") { it.key }}], resp (first 800 chars): ${resp.toString().take(800)}")
                    return emptyList<NotificationItem>() to null
                }
            }
            if (items.size() == 0) {
                AppLog.i("VKApiClient", "getRedesign: items array is EMPTY (0 notifications)")
                val nextFrom0 = resp.get("next_from")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                return emptyList<NotificationItem>() to nextFrom0
            }

            // Fix #254: next_from. В redesign-формате называется так же (next_from),
            // но на всякий случай держим fallback на start_from.
            val nextFrom: String? =
                resp.get("next_from")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                    ?: resp.get("start_from")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

            // Fix #254: profiles лежат в response.users (не response.profiles).
            // Для redesign-формата. Для legacy-формата (response.items) — в response.profiles.
            // Парсим ОБА варианта для надёжности.
            val profilesMap = mutableMapOf<Long, NotificationProfile>()
            // redesign format: response.users
            resp.getAsJsonArray("users")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = o.get("id")?.asLong ?: return@forEach
                val firstName = o.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val lastName = o.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val name = "$firstName $lastName".trim()
                profilesMap[uid] = NotificationProfile(
                    id = uid,
                    name = if (name.isBlank()) (o.get("screen_name")?.takeIf { !it.isJsonNull }?.asString ?: "id$uid") else name,
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    isGroup = false,
                )
            }
            // legacy format: response.profiles
            resp.getAsJsonArray("profiles")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val uid = o.get("id")?.asLong ?: return@forEach
                val firstName = o.get("first_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val lastName = o.get("last_name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                profilesMap[uid] = NotificationProfile(
                    id = uid,
                    name = "$firstName $lastName".trim(),
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    isGroup = false,
                )
            }
            // groups (одинаково для обоих форматов)
            resp.getAsJsonArray("groups")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val gid = o.get("id")?.asLong ?: return@forEach
                profilesMap[-gid] = NotificationProfile(
                    id = gid,
                    name = o.get("name")?.asString ?: "",
                    photo100 = o.get("photo_100")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    photo200 = o.get("photo_200")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    isGroup = true,
                )
            }
            val groupsCount = profilesMap.values.count { it.isGroup }
            AppLog.i("VKApiClient", "getRedesign: users=${profilesMap.size - groupsCount}, groups=$groupsCount, items=${items.size()}, nextFrom=${nextFrom?.take(40) ?: "null"}")

            // Fix #333: превью вложений в redesign-формате лежат НЕ в attachment.items
            // (там только {type, owner_id, post_id, url, attachments_string}), а в
            // топ-level массивах response.photos / videos / clips / market_items.
            // Каждый объект содержит owner_id + id + photo_130/photo_320/thumb_photo.
            // Собираем карту "type:oId_id" → URL миниатюры, чтобы заполнить
            // NotificationAttachment.thumbUrl и parentPhotoUrl/parentVideoThumb.
            // Без этого превью уведомлений пустые (серые заглушки).
            val mediaThumbs = mutableMapOf<String, String>()
            fun putThumb(type: String, ownerId: Long, id: Long, url: String?) {
                if (url.isNullOrBlank()) return
                mediaThumbs["$type:${ownerId}_$id"] = url
            }
            // photos: photo_130 (compact) → fallback photo_604
            resp.getAsJsonArray("photos")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val oid = o.get("owner_id")?.asLong ?: return@forEach
                val pid = o.get("id")?.asLong ?: return@forEach
                val url = o.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_604")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_75")?.takeIf { !it.isJsonNull }?.asString
                putThumb("photo", oid, pid, url)
            }
            // videos: photo_320 → fallback photo_130
            resp.getAsJsonArray("videos")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val oid = o.get("owner_id")?.asLong ?: return@forEach
                val vid = o.get("id")?.asLong ?: return@forEach
                val url = o.get("photo_320")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_800")?.takeIf { !it.isJsonNull }?.asString
                putThumb("video", oid, vid, url)
            }
            // clips: photo_320 → fallback photo_130
            resp.getAsJsonArray("clips")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val oid = o.get("owner_id")?.asLong ?: return@forEach
                val cid = o.get("id")?.asLong ?: return@forEach
                val url = o.get("photo_320")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_800")?.takeIf { !it.isJsonNull }?.asString
                putThumb("clip", oid, cid, url)
            }
            // market_items: thumb_photo → fallback photo_130
            resp.getAsJsonArray("market_items")?.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val o = el.asJsonObject
                val oid = o.get("owner_id")?.asLong ?: return@forEach
                val mid = o.get("id")?.asLong ?: return@forEach
                val url = o.get("thumb_photo")?.takeIf { !it.isJsonNull }?.asString
                    ?: o.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
                putThumb("market", oid, mid, url)
            }
            if (mediaThumbs.isNotEmpty()) {
                AppLog.i("VKApiClient", "getRedesign: mediaThumbs=${mediaThumbs.size} (photos=${resp.getAsJsonArray("photos")?.size() ?: 0}, videos=${resp.getAsJsonArray("videos")?.size() ?: 0}, clips=${resp.getAsJsonArray("clips")?.size() ?: 0}, market=${resp.getAsJsonArray("market_items")?.size() ?: 0})")
            } else {
                // §43 #LOG-NOISE: downgrade WARN → DEBUG. mediaThumbs EMPTY —
                // ожидаемое поведение когда уведомления содержат только posts
                // (VK не отдаёт thumbnails для posts в notificationsGetRedesign).
                AppLog.d("VKApiClient", "getRedesign: mediaThumbs EMPTY — resp keys=[${resp.entrySet().joinToString(",") { it.key }}]")
            }

            // Fix #254: выбираем парсер по формату. Если items — это redesigned
            // (есть поле "header" или "action") → parseRedesignNotificationItem.
            // Иначе (legacy notifications.get) → parseNotificationItem.
            val isRedesignFormat = items.firstOrNull()?.isJsonObject == true &&
                (items.first().asJsonObject.has("header") || items.first().asJsonObject.has("action"))

            var parsedCount = 0
            var filteredCount = 0
            val notifs = items.mapNotNull { el ->
                if (!el.isJsonObject) {
                    filteredCount++
                    return@mapNotNull null
                }
                val parsed = if (isRedesignFormat) {
                    parseRedesignNotificationItem(el.asJsonObject, profilesMap, mediaThumbs)
                } else {
                    parseNotificationItem(el.asJsonObject, profilesMap)
                }
                if (parsed == null) filteredCount++ else parsedCount++
                parsed
            }
            AppLog.i("VKApiClient", "getRedesign: format=${if (isRedesignFormat) "redesign" else "legacy"}, parsed=$parsedCount, filtered_out=$filteredCount (total=${items.size()})")

            // Fix #255: диагностика uniqueKey — если distinct < parsed,
            // значит ключи схлопываются (баг take(40) или другая причина).
            if (notifs.size > 1) {
                val distinctKeys = notifs.map { it.uniqueKey }.toSet()
                if (distinctKeys.size < notifs.size) {
                    AppLog.w("VKApiClient", "getRedesign: UNIQUE KEY COLLISION! parsed=${notifs.size}, distinct=${distinctKeys.size}")
                    notifs.take(5).forEachIndexed { i, n ->
                        AppLog.w("VKApiClient", "  item[$i]: rawId='${n.rawId.take(80)}' (len=${n.rawId.length}), date=${n.date}, type='${n.type}', parentOwnerId=${n.parentOwnerId}, parentItemId=${n.parentItemId}, uniqueKey='${n.uniqueKey.take(80)}'")
                    }
                } else {
                    AppLog.i("VKApiClient", "getRedesign: uniqueKey OK — ${notifs.size} items, ${distinctKeys.size} distinct keys")
                    // Логируем первые 2 rawId для подтверждения что они разные
                    notifs.take(2).forEachIndexed { i, n ->
                        AppLog.i("VKApiClient", "  item[$i]: rawId(len=${n.rawId.length})='${n.rawId.take(80)}...'")
                    }
                }
            }

            if (notifs.isEmpty() && items.size() > 0) {
                val firstItem = items[0]
                if (firstItem.isJsonObject) {
                    val firstKeys = firstItem.asJsonObject.entrySet().joinToString(",") { it.key }
                    AppLog.w("VKApiClient", "getRedesign: ALL ${items.size()} items filtered out! First item keys = [$firstKeys]")
                    AppLog.w("VKApiClient", "getRedesign: first item JSON (first 800 chars): ${firstItem.toString().take(800)}")
                }
            }

            notifs to nextFrom
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "notificationsGetRedesign error", e)
            emptyList<NotificationItem>() to null
        }
    }

    /**
     * §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): получить список security alerts
     * (подозрительные входы — новое устройство, город, IP).
     *
     * VK ID account API method: `accountPersonal.getSecurityAlerts`.
     * Источник: анализ архива VK ID_веб.zip (см. VK_IMPORT_API.MD §49.2.1).
     *
     * Возвращает массив объектов с полями:
     *  - `device_id` — устройство (формат `87v-we10y1_...` или `android-<uuid>`)
     *  - `device_name` — отображаемое имя (server-templated)
     *  - `app_name` — приложение (PinoK / VK / VK ID / ...)
     *  - `ip` — IP-адрес входа
     *  - `location` — город/страна
     *  - `last_activity` — unix timestamp
     *  - `is_suspicious` — true если алгоритм VK счёл вход подозрительным
     *
     * @param hash — auth hash (logout_hash из web_token JSON). Если null —
     *   метод попробует без него (VK может принять просто access_token).
     * @return JsonArray alerts, или null при ошибке.
     */
    suspend fun accountGetSecurityAlerts(hash: String? = null): com.google.gson.JsonArray? {
        if (isOffline()) return null
        val args = mutableMapOf<String, String>()
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("accountPersonal.getSecurityAlerts", args) ?: return null
        return try {
            val respEl = json.get("response")
            when {
                respEl == null || respEl.isJsonNull -> {
                    AppLog.w("VKApiClient", "getSecurityAlerts: 'response' missing. json (first 300): ${json.toString().take(300)}")
                    null
                }
                respEl.isJsonArray -> respEl.asJsonArray
                respEl.isJsonObject -> {
                    // Может быть обёрнут в {items: [...]} или {alerts: [...]}
                    val obj = respEl.asJsonObject
                    obj.getAsJsonArray("items")
                        ?: obj.getAsJsonArray("alerts")
                        ?: com.google.gson.JsonArray().also {
                            AppLog.w("VKApiClient", "getSecurityAlerts: response is object but no items/alerts array. keys=[${obj.entrySet().joinToString(",") { it.key }}]")
                        }
                }
                else -> null
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "getSecurityAlerts error", e)
            null
        }
    }

    /**
     * §49.5.1 #SAFETY-NET-ALERTS: включить/выключить SafetyNet (alerts о
     * подозрительных входах). VK ID account API: `accountPersonal.setSafetyNetEnabled`.
     *
     * @param isEnabled true=включить alerts, false=выключить
     * @param hash auth hash (logout_hash)
     * @return true если запрос успешен (HTTP 200 + no error in response)
     */
    suspend fun accountSetSafetyNetEnabled(isEnabled: Boolean, hash: String? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "is_enabled" to if (isEnabled) "1" else "0",
        )
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("accountPersonal.setSafetyNetEnabled", args) ?: return false
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "setSafetyNetEnabled error: ${errObj.get("error_msg")?.asString}")
            return false
        }
        return true
    }

    // ══════════════════════════════════════════════════════════════════════
    //  §49.6 Sprint VK-ID-1: Управление сессиями/устройствами + CUA verification
    //
    //  API methods (namespace `accountPersonal.*` и `cua.*`):
    //   - getActivityHistoryDevices({hash}) → список активных сессий
    //   - resetSessions({hash, device_id, app_id?}) → завершить одну сессию
    //   - resetAllSessions({hash, app_id?}) → завершить все сессии
    //   - getSessionInfoForReset({login_hash, hash}) → pre-flight перед reset
    //   - cua.getValidationMethods({action, hash}) → каналы подтверждения
    //   - cua.sendPhoneCode/sendPushCode/sendEmailCode({hash}) → отправить код
    //   - cua.checkPhoneCode/checkPushCode/checkEmailCode({code, hash}) → проверить
    //
    //  Источник: анализ архива VK ID_веб.zip (VK_IMPORT_API.MD §49.2.2, §49.2.3).
    // ══════════════════════════════════════════════════════════════════════

    /**
     * §49.6 Sprint VK-ID-1.2: получить список активных сессий аккаунта.
     *
     * VK ID account API: `accountPersonal.getActivityHistoryDevices`.
     * Возвращает массив объектов с server-templated полями name/app/location.
     *
     * Текущее устройство (PinoK) определяется по совпадению deviceId с
     * `TokenStorage.deviceId` (если доступно) — его нельзя завершить.
     *
     * @param hash auth hash (logout_hash из web_token JSON).
     * @return список сессий, или null при ошибке/офлайне. Пустой список = нет сессий.
     */
    suspend fun accountGetActivityHistoryDevices(hash: String?): List<re.pinok.data.model.DeviceSession>? {
        if (isOffline()) return null
        val args = mutableMapOf<String, String>()
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("accountPersonal.getActivityHistoryDevices", args) ?: return null
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "getActivityHistoryDevices error: ${errObj.get("error_msg")?.asString}")
            return null
        }
        return try {
            val respEl = json.get("response")
            when {
                respEl == null || respEl.isJsonNull -> {
                    AppLog.w("VKApiClient", "getActivityHistoryDevices: 'response' missing")
                    null
                }
                respEl.isJsonArray -> parseDeviceSessions(respEl.asJsonArray)
                respEl.isJsonObject -> {
                    val obj = respEl.asJsonObject
                    val arr = obj.getAsJsonArray("items")
                        ?: obj.getAsJsonArray("devices")
                        ?: obj.getAsJsonArray("sessions")
                    if (arr != null) parseDeviceSessions(arr) else emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "getActivityHistoryDevices parse error", e)
            null
        }
    }

    /**
     * Парсер массива устройств из `accountPersonal.getActivityHistoryDevices`.
     * VK возвращает server-templated поля — маппим в [DeviceSession] с fallback.
     */
    private fun parseDeviceSessions(arr: com.google.gson.JsonArray): List<re.pinok.data.model.DeviceSession> {
        val currentDeviceId = runCatching { tokenStorage.deviceId() }.getOrNull()
        val out = ArrayList<re.pinok.data.model.DeviceSession>(arr.size())
        for (el in arr) {
            val o = el.asJsonObject ?: continue
            val deviceId = o.get("device_id")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("id")?.takeIf { !it.isJsonNull }?.asString
                ?: continue
            val name = o.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("device_name")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("title")?.takeIf { !it.isJsonNull }?.asString
                ?: "Устройство"
            val appName = o.get("app")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("app_name")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("platform")?.takeIf { !it.isJsonNull }?.asString
                ?: ""
            val ip = o.get("ip")?.takeIf { !it.isJsonNull }?.asString
            val location = o.get("location")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("geo")?.takeIf { !it.isJsonNull }?.asString
            val lastActivityTs = o.get("last_activity")?.takeIf { !it.isJsonNull }?.asLong
                ?: o.get("last_seen")?.takeIf { !it.isJsonNull }?.asLong
                ?: o.get("date")?.takeIf { !it.isJsonNull }?.asLong
                ?: 0L
            val isOnline = o.get("is_online")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: o.get("online")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: false
            val isCurrent = currentDeviceId != null && currentDeviceId == deviceId
            out.add(
                re.pinok.data.model.DeviceSession(
                    deviceId = deviceId,
                    name = name,
                    appName = appName,
                    ip = ip,
                    location = location,
                    lastActivityTs = lastActivityTs,
                    isOnline = isOnline,
                    isCurrent = isCurrent,
                    deviceType = re.pinok.data.model.classifyDeviceType(name, appName),
                )
            )
        }
        return out
    }

    /**
     * §49.6 Sprint VK-ID-1.3: завершить ОДНУ сессию (device-specific logout).
     *
     * VK ID account API: `accountPersonal.resetSessions`.
     *
     * @param deviceId идентификатор сессии (из [DeviceSession.deviceId]).
     * @param hash auth hash (logout_hash).
     * @param validationToken токен из [cuaCheckCode] (если VK требует verification
     *   для action=reset_sessions). null если verification не требуется.
     * @param appId фильтр по приложению (null = все приложения для этого device).
     * @param excludeDeviceId если нужно исключить текущее устройство из reset
     *   (для resetAllSessions — чтобы не выкинуть себя). null = не исключать.
     * @return true если VK подтвердил завершение.
     */
    suspend fun accountResetSessions(
        deviceId: String,
        hash: String?,
        validationToken: String? = null,
        appId: String? = null,
        excludeDeviceId: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf("device_id" to deviceId)
        if (!hash.isNullOrBlank()) args["hash"] = hash
        if (!validationToken.isNullOrBlank()) args["validation_token"] = validationToken
        if (!appId.isNullOrBlank()) args["app_id"] = appId
        if (!excludeDeviceId.isNullOrBlank()) args["exclude_device_id"] = excludeDeviceId
        val json = call("accountPersonal.resetSessions", args) ?: return false
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "resetSessions error: ${errObj.get("error_msg")?.asString}")
            return false
        }
        AppLog.i("VKApiClient", "resetSessions OK: device=$deviceId")
        return true
    }

    /**
     * §49.6 Sprint VK-ID-1.4: завершить ВСЕ сессии (logout everywhere).
     *
     * VK ID account API: `accountPersonal.resetAllSessions`.
     * После вызова VK инвалидирует все токены, кроме текущего (если exclude_device_id задан).
     *
     * @param hash auth hash (logout_hash).
     * @param validationToken токен из [cuaCheckCode] для action=reset_all_sessions.
     * @param appId фильтр по приложению (null = все).
     * @param excludeDeviceId текущее устройство — НЕ завершать (иначе выкинем себя).
     * @return true если VK подтвердил.
     */
    suspend fun accountResetAllSessions(
        hash: String?,
        validationToken: String? = null,
        appId: String? = null,
        excludeDeviceId: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        if (!hash.isNullOrBlank()) args["hash"] = hash
        if (!validationToken.isNullOrBlank()) args["validation_token"] = validationToken
        if (!appId.isNullOrBlank()) args["app_id"] = appId
        if (!excludeDeviceId.isNullOrBlank()) args["exclude_device_id"] = excludeDeviceId
        val json = call("accountPersonal.resetAllSessions", args) ?: return false
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "resetAllSessions error: ${errObj.get("error_msg")?.asString}")
            return false
        }
        AppLog.i("VKApiClient", "resetAllSessions OK (exclude=$excludeDeviceId)")
        return true
    }

    /**
     * §49.6 Sprint VK-ID-1: pre-flight запрос перед reset — показывает что будет закрыто.
     * VK ID account API: `accountPersonal.getSessionInfoForReset`.
     *
     * @param loginHash hash конкретной сессии (не logout_hash!).
     * @param hash auth hash (logout_hash).
     * @return JsonObject с полями {sessions_count, apps[], last_activity} или null.
     */
    suspend fun accountGetSessionInfoForReset(loginHash: String, hash: String?): JsonObject? {
        if (isOffline()) return null
        val args = mutableMapOf("login_hash" to loginHash)
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("accountPersonal.getSessionInfoForReset", args) ?: return null
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "getSessionInfoForReset error: ${errObj.get("error_msg")?.asString}")
            return null
        }
        return json.getAsJsonObject("response")
    }

    // ── CUA (Confirm User Action) verification framework ────────────────

    /**
     * §49.6 Sprint VK-ID-1.5: получить доступные каналы подтверждения для action.
     * VK ID API: `cua.getValidationMethods({action, hash})`.
     *
     * @param action одна из констант [re.pinok.data.model.CuaAction]
     *   (reset_sessions, reset_all_sessions, change_password, ...).
     * @param hash auth hash (logout_hash).
     * @return список методов, или null при ошибке.
     */
    suspend fun cuaGetValidationMethods(
        action: String,
        hash: String?,
    ): re.pinok.data.model.CuaValidationMethods? {
        if (isOffline()) return null
        val args = mutableMapOf("action" to action)
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("cua.getValidationMethods", args) ?: return null
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            AppLog.w("VKApiClient", "cua.getValidationMethods error: ${errObj.get("error_msg")?.asString}")
            return null
        }
        return try {
            val respEl = json.get("response")
            if (respEl == null || respEl.isJsonNull) {
                re.pinok.data.model.CuaValidationMethods(methods = emptyList())
            } else if (respEl.isJsonObject) {
                val obj = respEl.asJsonObject
                val arr = obj.getAsJsonArray("methods")
                    ?: obj.getAsJsonArray("items")
                    ?: com.google.gson.JsonArray()
                val methods = ArrayList<re.pinok.data.model.CuaValidationMethod>(arr.size())
                for (i in 0 until arr.size()) {
                    val m = arr[i].asJsonObject ?: continue
                    val type = m.get("type")?.asString
                        ?: m.get("method")?.asString
                        ?: continue
                    val parsed = parseCuaMethod(type)
                    if (parsed == null) continue
                    val mask = m.get("mask")?.asString
                        ?: m.get("masked")?.asString
                        ?: m.get("phone")?.asString
                        ?: m.get("email")?.asString
                        ?: ""
                    val primary = m.get("is_primary")?.takeIf { !it.isJsonNull }?.asBoolean ?: (methods.isEmpty())
                    methods.add(re.pinok.data.model.CuaValidationMethod(parsed, mask, primary))
                }
                val canSkip = obj.get("can_skip")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                val retry = obj.get("retry_delay")?.takeIf { !it.isJsonNull }?.asInt ?: 60
                re.pinok.data.model.CuaValidationMethods(methods, canSkip, retry)
            } else {
                re.pinok.data.model.CuaValidationMethods(methods = emptyList())
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "cua.getValidationMethods parse error", e)
            null
        }
    }

    private fun parseCuaMethod(type: String): re.pinok.data.model.CuaMethod? {
        return when (type.lowercase()) {
            "sms", "phone" -> re.pinok.data.model.CuaMethod.SMS
            "push" -> re.pinok.data.model.CuaMethod.PUSH
            "email" -> re.pinok.data.model.CuaMethod.EMAIL
            "phone_bind", "phonebind" -> re.pinok.data.model.CuaMethod.PHONE_BIND
            else -> null
        }
    }

    /**
     * §49.6 Sprint VK-ID-1.5: отправить код подтверждения через выбранный канал.
     *
     * @param method канал (SMS/PUSH/EMAIL/PHONE_BIND) — определяет имя API-метода.
     * @param hash auth hash (logout_hash).
     * @param extraParams доп. параметры (email/phone для sendEmailCode/sendPhoneCode —
     *   если VK требует их при отправке; обычно использует привязанные из профиля).
     * @return [re.pinok.data.model.CuaSendResult].
     */
    suspend fun cuaSendCode(
        method: re.pinok.data.model.CuaMethod,
        hash: String?,
        extraParams: Map<String, String> = emptyMap(),
    ): re.pinok.data.model.CuaSendResult {
        if (isOffline()) return re.pinok.data.model.CuaSendResult(false, error = "offline")
        val args = mutableMapOf<String, String>()
        if (!hash.isNullOrBlank()) args["hash"] = hash
        args.putAll(extraParams)
        val json = call(method.apiSendName, args) ?: return re.pinok.data.model.CuaSendResult(false, error = "network")
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            val msg = errObj.get("error_msg")?.asString ?: "unknown"
            AppLog.w("VKApiClient", "${method.apiSendName} error: $msg")
            return re.pinok.data.model.CuaSendResult(false, error = msg)
        }
        val respEl = json.get("response")
        val retry = respEl?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("retry_delay")?.takeIf { !it.isJsonNull }?.asInt ?: 60
        AppLog.i("VKApiClient", "${method.apiSendName} OK (retry in ${retry}s)")
        return re.pinok.data.model.CuaSendResult(true, retryDelaySec = retry)
    }

    /**
     * §49.6 Sprint VK-ID-1.5: проверить код подтверждения.
     *
     * @param code введённый пользователем код.
     * @param method канал (определяет имя API-метода).
     * @param hash auth hash.
     * @return [re.pinok.data.model.CuaCheckResult] — при success содержит
     *   validationToken (передаётся в resetSessions/changePassword/...).
     */
    suspend fun cuaCheckCode(
        code: String,
        method: re.pinok.data.model.CuaMethod,
        hash: String?,
    ): re.pinok.data.model.CuaCheckResult {
        if (isOffline()) return re.pinok.data.model.CuaCheckResult(false, error = "offline")
        val args = mutableMapOf("code" to code)
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call(method.apiCheckName, args) ?: return re.pinok.data.model.CuaCheckResult(false, error = "network")
        val errObj = json.getAsJsonObject("error")
        if (errObj != null) {
            val msg = errObj.get("error_msg")?.asString ?: "unknown"
            val codeNum = errObj.get("error_code")?.takeIf { !it.isJsonNull }?.asInt
            AppLog.w("VKApiClient", "${method.apiCheckName} error: code=$codeNum msg=$msg")
            return re.pinok.data.model.CuaCheckResult(false, error = msg)
        }
        val respEl = json.get("response")
        val token = respEl?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("validation_token")?.takeIf { !it.isJsonNull }?.asString
        val attempts = respEl?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("attempts_remaining")?.takeIf { !it.isJsonNull }?.asInt
        AppLog.i("VKApiClient", "${method.apiCheckName} OK — token=${if (token != null) "present" else "null"}")
        return re.pinok.data.model.CuaCheckResult(true, token, attempts)
    }

    /**
     * Fix #254: Парсер для redesigned-формата notifications.getRedesign.
     *
     * Redesigned item имеет СОВЕРШЕННО ДРУГУЮ структуру, чем notifications.get:
     *   - id: строка (base64-подобная), не число
     *   - date: timestamp
     *   - image: {type: "single_owner", owner: {type: "group"|"user", id: <id>}}
     *   - header: человекочитаемое имя владельца ("ЧП Россия | Новости | ...")
     *   - text: действие ("опубликовало новый пост", "прокомментировал", ...)
     *   - action: {type: "entity_show", entity: {type: "post"|"photo"|..., owner_id, post_id, url, attachments_string}}
     *   - attachment: {type: "entity_array", items: [...]}
     *   - dots_menu: [{type: "open_setting"|...}, {type: "unsubscribe", ...}, {type: "hide_notification", ...}]
     *
     * Маппинг на NotificationItem:
     *   - type: вычисляем из action.entity.type ("post"→"new_posts", "photo"→"photo", ...)
     *     Это нужно чтобы фильтры в NotificationsScreen работали.
     *   - feedbackProfiles: profilesMap[image.owner.id] (для groups id отрицательный)
     *   - feedbackIds: listOf(image.owner.id) (для groups — negative)
     *   - parentType: action.entity.type
     *   - parentOwnerId: action.entity.owner_id
     *   - parentItemId: action.entity.post_id / photo_id / video_id / id
     *   - parentText: action.entity.attachments_string или ""
     *   - attachments: из attachment.items (тип + owner_id + id)
     *   - text: "$header $text" (например "ЧП Россия | Новости | ... опубликовало новый пост")
     *   - uniqueKey: используется redesignId если parentItemId==0, иначе старый формат
     *
     * @param o элемент уведомления из response.notifications[]
     * @param profilesMap карта профилей (id → NotificationProfile), для groups id отрицательный
     * @param mediaThumbs Fix #333: карта "type:ownerId_id" → URL миниатюры, собранная
     *   из топ-level массивов response.photos/videos/clips/market_items. Без этого
     *   attachment.items не содержит photo_130, и превью остаются пустыми.
     * @return NotificationItem или null если item неразборчив
     */
    private fun parseRedesignNotificationItem(
        o: JsonObject,
        profilesMap: Map<Long, NotificationProfile>,
        mediaThumbs: Map<String, String>,
    ): NotificationItem? {
        // date — обязателен (иначе это не уведомление)
        val date = o.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: return null

        // id — строка в redesign-формате (base64-подобная). Используется для uniqueKey.
        val redesignId = o.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""

        // --- image.owner → feedbackProfiles (кто совершил действие) ---
        // image: {type: "single_owner", owner: {type: "group"|"user", id: <id>}}
        // image.type может быть "single_owner" (один аватар) или другие варианты
        // (много аватаров — "multiple_owners"?) — пока обрабатываем только single.
        val feedbackIds = mutableListOf<Long>()
        val imageEl = o.get("image")
        if (imageEl != null && imageEl.isJsonObject) {
            val ownerEl = imageEl.asJsonObject.get("owner")
            if (ownerEl != null && ownerEl.isJsonObject) {
                val owner = ownerEl.asJsonObject
                val ownerId = owner.get("id")?.asLong ?: 0L
                val ownerType = owner.get("type")?.asString ?: "user"
                if (ownerId != 0L) {
                    // Для groups в profilesMap ключ — отрицательный id.
                    // Для users — положительный.
                    val mapKey = if (ownerType == "group") -ownerId else ownerId
                    feedbackIds.add(mapKey)
                }
            }
        }
        val feedbackProfiles = feedbackIds.mapNotNull { profilesMap[it] }

        // --- action.entity → parent (объект уведомления) ---
        // action: {type: "entity_show", entity: {type, owner_id, post_id, url, attachments_string}}
        var parentType = ""
        var parentOwnerId = 0L
        var parentItemId = 0L
        var parentText = ""
        // §42.4 #PUSH-DEEPLINK: канонический VK URL (entity.url) и id комментария.
        // См. VkUrlDeepLinker — URL используется как primary source для deep-link.
        var parentUrl: String? = null
        var parentCommentId = 0L
        val attachments = mutableListOf<NotificationAttachment>()
        val actions = mutableListOf<NotificationAction>()

        val actionEl = o.get("action")
        if (actionEl != null && actionEl.isJsonObject) {
            val entityEl = actionEl.asJsonObject.get("entity")
            if (entityEl != null && entityEl.isJsonObject) {
                val entity = entityEl.asJsonObject
                parentType = entity.get("type")?.asString ?: ""
                parentOwnerId = entity.get("owner_id")?.asLong ?: 0L
                // post_id / photo_id / video_id / topic_id / id — разные имена для разных типов
                parentItemId = entity.get("post_id")?.asLong
                    ?: entity.get("photo_id")?.asLong
                    ?: entity.get("video_id")?.asLong
                    ?: entity.get("topic_id")?.asLong
                    ?: entity.get("id")?.asLong
                    ?: 0L
                parentText = entity.get("attachments_string")?.takeIf { !it.isJsonNull }?.asString ?: ""
                // §42.4 #PUSH-DEEPLINK: entity.url — канонический VK permalink
                // (например «https://vk.com/wall-123_456?reply=789»). Надёжнее
                // чем type+post_id, т.к. однозначно кодирует comment_id (?reply=).
                parentUrl = entity.get("url")?.takeIf { !it.isJsonNull }?.asString
                // §42.4 #PUSH-DEEPLINK: для comment-сущности сохраняем id
                // комментария — к нему скроллим в PostDetailScreen.
                if (parentType.equals("comment", ignoreCase = true)) {
                    parentCommentId = entity.get("id")?.asLong ?: 0L
                }
            }
            // action.type = "entity_show" — это тип ДЕЙСТВИЯ (что будет при клике),
            // не тип уведомления. Для actions кнопок не используем — в redesign
            // кнопки лежат в dots_menu.
        }

        // --- attachment.items → attachments (вложения) ---
        // attachment: {type: "entity_array", items: [{type, owner_id, post_id, url, attachments_string}, ...]}
        // Fix #333: в redesign-формате items НЕ содержат photo_130/photo_800 —
        // миниатюры лежат в топ-level response.photos/videos/clips/market_items.
        // Ищем thumbUrl в mediaThumbs по composite-ключу "type:ownerId_id".
        var firstPhotoThumb: String? = null
        var firstVideoThumb: String? = null
        val attachmentEl = o.get("attachment")
        if (attachmentEl != null && attachmentEl.isJsonObject) {
            val attItems = attachmentEl.asJsonObject.getAsJsonArray("items")
            if (attItems != null) {
                for (att in attItems) {
                    if (!att.isJsonObject) continue
                    val a = att.asJsonObject
                    val attType = a.get("type")?.asString ?: continue
                    // Маппим типы сущностей на типы вложений NotificationAttachment
                    val mappedType = when (attType) {
                        "photo" -> "photo"
                        "video" -> "video"
                        "clip" -> "clip"
                        "gift" -> "gift"
                        "post", "wall" -> "photo"  // пост — показываем как превью (если есть)
                        "market_item", "market" -> "market"
                        else -> "photo"
                    }
                    val attOwnerId = a.get("owner_id")?.asLong ?: parentOwnerId
                    val attItemId = a.get("post_id")?.asLong
                        ?: a.get("photo_id")?.asLong
                        ?: a.get("video_id")?.asLong
                        ?: a.get("id")?.asLong
                        ?: 0L
                    // Fix #333 + NOTIF-THUMB-FIX (#352): сначала пробуем mediaThumbs
                    // (правильный источник для redesign), потом — inline-поля photo_130/
                    // photo_800/photo_320 (VK иногда включает их inline для видео/клипов),
                    // потом — поле url (для некоторых типов это прямой URL превью).
                    // Если ВСЕ источники пусты — thumbUrl=null, UI покажет fallback-иконку.
                    val thumb = mediaThumbs["$mappedType:${attOwnerId}_$attItemId"]
                        ?: a.get("photo_130")?.takeIf { !it.isJsonNull }?.asString
                        ?: a.get("photo_800")?.takeIf { !it.isJsonNull }?.asString
                        ?: a.get("photo_320")?.takeIf { !it.isJsonNull }?.asString
                        ?: a.get("photo_75")?.takeIf { !it.isJsonNull }?.asString
                        ?: a.get("url")?.takeIf { !it.isJsonNull }?.asString?.let { u ->
                            // NOTIF-THUMB-FIX (#352): поле url обычно это permalink
                            // (vk.com/wall-123_456), но для некоторых типов (market_item,
                            // story) VK кладёт прямой URL превью. Проверяем что URL
                            // выглядит как image (ends with .jpg/.png/.webp/.jpeg).
                            val lower = u.lowercase()
                            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                                lower.endsWith(".png") || lower.endsWith(".webp")
                            ) u else null
                        }
                    // Fix #335 (revised, NOTIF-FIX-1): VK web DOES show thumbnails for post
                    // notifications — проверено по скриншоту: рядом с «опубликовало новый пост»
                    // идёт LazyRow с 3 anime-постерами. Более раннее предположение «VK web НЕ
                    // показывает миниатюры для post-уведомлений» было ОШИБОЧНЫМ.
                    // Раньше здесь стоял `if (thumb == null) continue` — он пропускал ВСЕ
                    // attachment-entries без thumbUrl. Но в redesign-формате для постов
                    // attachment.items часто содержит entry типа "photo" с owner_id+photo_id,
                    // и thumb НАХОДИТСЯ в mediaThumbs (если photo есть в response.photos[]).
                    // Пропуск приводил к пустому LazyRow → карточка выглядела «голой».
                    //
                    // Теперь: ВСЕГДА создаём NotificationAttachment (даже с thumbUrl=null —
                    // тогда UI AttachmentThumb покажет fallback-иконку по типу вложения,
                    // что лучше, чем пустое место). Превью parentPhotoUrl/parentVideoThumb
                    // тоже заполняем из mediaThumbs (ниже в fallback-блоке).
                    if (firstPhotoThumb == null && thumb != null && mappedType == "photo") {
                        firstPhotoThumb = thumb
                    }
                    if (firstVideoThumb == null && thumb != null && (mappedType == "video" || mappedType == "clip")) {
                        firstVideoThumb = thumb
                    }
                    // NOTIF-THUMB-FIX (#352): diagnostic log — если thumb null после всех
                    // fallback'ов, логируем доступные поля для диагностики. Это поможет
                    // понять, почему некоторые превью в уведомлениях пустые (серые
                    // плейсхолдеры с иконками на скриншоте пользователя).
                    if (thumb == null) {
                        // §43 #LOG-NOISE: downgrade WARN → DEBUG. VK API не включает
                        // mediaThumbs для post-уведомлений (только photos/videos arrays,
                        // которые пусты для posts). UI уже показывает fallback-иконку по
                        // типу вложения. Логирование на WARN засоряло logcat (40+ строк
                        // на каждый notificationsGetRedesign).
                        val availableKeys = a.entrySet().joinToString(",") { it.key }
                        AppLog.d("VKApiClient",
                            "getRedesign: thumb NULL for type=$attType mapped=$mappedType " +
                                "owner=$attOwnerId item=$attItemId keys=[$availableKeys] " +
                                "mediaThumbsSize=${mediaThumbs.size}"
                        )
                    }
                    attachments.add(NotificationAttachment(
                        type = mappedType,
                        thumbUrl = thumb,
                        ownerId = attOwnerId,
                        itemId = attItemId,
                        accessKey = a.get("access_key")?.takeIf { !it.isJsonNull }?.asString,
                    ))
                }
            }
        }
        // Fix #333: если в attachment.items не нашлось превью, пробуем parent-сущность
        // (action.entity) — это основной объект уведомления (пост/фото/видео).
        // Для post — thumb может быть в photos по owner_id+post_id (VK отдаёт фото поста).
        if (firstPhotoThumb == null && parentOwnerId != 0L && parentItemId != 0L) {
            firstPhotoThumb = mediaThumbs["photo:${parentOwnerId}_$parentItemId"]
                ?: mediaThumbs["video:${parentOwnerId}_$parentItemId"]
                ?: mediaThumbs["clip:${parentOwnerId}_$parentItemId"]
        }
        if (firstVideoThumb == null && parentOwnerId != 0L && parentItemId != 0L) {
            firstVideoThumb = mediaThumbs["video:${parentOwnerId}_$parentItemId"]
                ?: mediaThumbs["clip:${parentOwnerId}_$parentItemId"]
        }

        // --- header + text → display text ---
        // header: "ЧП Россия | Новости | События | Расследования |"
        // text: "опубликовало новый пост"
        // → text = "ЧП Россия | Новости | События | Расследования | опубликовало новый пост"
        val header = o.get("header")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val actionText = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val displayText = if (header.isBlank() && actionText.isBlank()) {
            "Уведомление"
        } else if (header.isBlank()) {
            actionText
        } else if (actionText.isBlank()) {
            header
        } else {
            "$header $actionText"
        }

        // --- type: вычисляем из parentType (action.entity.type) ---
        // Это нужно чтобы фильтры в NotificationsScreen работали.
        // Маппим типы сущностей на типы уведомлений:
        //   "post" → "new_posts" (фильтр "Новые посты")
        //   "photo" → "photo"
        //   "video" → "video"
        //   "clip" → "clip"
        //   "comment" → "comment"
        //   "topic" → "topic"
        //   "market_item" → "market"
        //   "story" → "story"
        //   "app" → "app"
        //   "podcast_episode" → "podcast"
        // Если parentType пустой — fallback на "new_posts" (типичный случай для redesign).
        val type = when (parentType) {
            "post", "wall" -> "new_posts"
            "photo" -> "photo"
            "video" -> "video"
            "clip" -> "clip"
            "comment" -> "comment"
            "topic" -> "topic"
            "market_item" -> "market"
            "story" -> "story"
            "app" -> "app"
            "podcast_episode" -> "podcast"
            "" -> "new_posts"  // неизвестный тип — считаем новым постом
            else -> parentType  // используем как есть
        }

        // --- dots_menu → actions (пока не используем, но парсим для будущего) ---
        // dots_menu: [{type: "open_setting", name: "new_posts"}, {type: "unsubscribe", query: "...", name: "new_posts"}, {type: "hide_notification", query: "..."}]
        // Это контекстное меню (настройки уведомлений, отписка, скрыть).
        // Кнопки действий (Ответить, Подарить в ответ) в redesign-формате отсутствуют —
        // их нет в redesigned items. Если в будущем понадобится — добавим.
        // Пока оставляем actions пустым.

        return NotificationItem(
            type = type,
            date = date,
            feedbackProfiles = feedbackProfiles,
            feedbackIds = feedbackIds,
            parentType = parentType,
            parentOwnerId = parentOwnerId,
            parentItemId = parentItemId,
            parentText = parentText,
            // Fix #333: заполняем превью из mediaThumbs (топ-level photos/videos/clips).
            parentPhotoUrl = firstPhotoThumb,
            parentVideoThumb = firstVideoThumb,
            attachments = attachments,
            actions = actions,
            replyText = "",
            replyFromId = 0L,
            replyDate = 0L,
            profilesMap = profilesMap,
            parentCommentId = parentCommentId,
            parentUrl = parentUrl,
            text = displayText,
            parentId = parentItemId,
            parentOwnerIdLegacy = parentOwnerId,
            rawId = redesignId,
        )
    }

    /** notifications.getUnreadCounters — счётчики непрочитанных по категориям. */
    suspend fun notificationsGetUnreadCounters(): Map<String, Int> {
        if (isOffline()) return emptyMap()
        val json = call("notifications.getUnreadCounters", emptyMap()) ?: return emptyMap()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyMap()
            val result = mutableMapOf<String, Int>()
            resp.entrySet().forEach { (key, value) ->
                if (value.isJsonPrimitive) {
                    result[key] = value.asInt
                }
            }
            result
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "notificationsGetUnreadCounters error", e)
            emptyMap()
        }
    }

    /** friends.getCounters — счётчики друзей (из архива Мессенджер_чат). */
    suspend fun friendsGetCounters(): Map<String, Int> {
        if (isOffline()) return emptyMap()
        val json = call("friends.getCounters", emptyMap()) ?: return emptyMap()
        return try {
            val resp = json.getAsJsonObject("response") ?: return emptyMap()
            val result = mutableMapOf<String, Int>()
            resp.entrySet().forEach { (key, value) ->
                if (value.isJsonPrimitive) result[key] = value.asInt
            }
            result
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "friendsGetCounters error: ${e.message}")
            emptyMap()
        }
    }

    /** docs.getTags — теги документов (из архива Мессенджер_чат). */
    suspend fun docsGetTags(): List<Pair<Long, String>> {
        if (isOffline()) return emptyList()
        val json = call("docs.getTags", emptyMap()) ?: return emptyList()
        return try {
            val items = json.getAsJsonArray("response") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                (o.get("id")?.asLong ?: 0L) to (o.get("name")?.asString ?: "")
            }
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "docsGetTags error: ${e.message}")
            emptyList()
        }
    }

    /** docs.getTypes — типы документов (из архива Мессенджер_чат). */
    suspend fun docsGetTypes(): List<Pair<Int, String>> {
        if (isOffline()) return emptyList()
        val json = call("docs.getTypes", emptyMap()) ?: return emptyList()
        return try {
            val items = json.getAsJsonArray("response") ?: return emptyList()
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                (o.get("id")?.asInt ?: 0) to (o.get("name")?.asString ?: "")
            }
        } catch (e: Exception) {
            AppLog.w("VKApiClient", "docsGetTypes error: ${e.message}")
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Notify-settings BFF (settingsGeneral.*)
    //   — used by the SPA at m.vk.ru/settings?act=notify
    //   Source: /home/z/notif/NOTIFICATION_ANALYSIS.md
    // ════════════════════════════════════════════════════════════════════

    /**
     * settingsGeneral.getNotifySettings — fetch the full notify-settings
     * document. Returns a generic sections/params tree.
     *
     * @param page BFF page selector. Default "notify" (the settings page).
     *             Other known values: "account", "privacy", "content".
     */
    suspend fun settingsGeneralGetNotifySettings(
        page: String = "notify",
    ): List<re.pinok.data.model.SettingsSection>? {
        if (isOffline()) return null
        val json = call("settingsGeneral.getNotifySettings", mapOf("page" to page))
            ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val sectionsArr = resp.getAsJsonArray("sections") ?: return null
            sectionsArr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseSettingsSection(el.asJsonObject)
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "settingsGeneralGetNotifySettings error", e)
            null
        }
    }

    /**
     * settingsGeneral.setNotifySettings — PATCH a single param.
     * Booleans must be sent as "true"/"false" strings.
     */
    suspend fun settingsGeneralSetNotifySettings(
        key: String,
        value: String,
    ): Boolean {
        if (isOffline()) return false
        val json = call("settingsGeneral.setNotifySettings",
            mapOf("key" to key, "value" to value)) ?: return false
        // VK API возвращает {"response": 1} (число, не объект). Раньше вызывали
        // getAsJsonObject("response") → ClassCastException (#300). Простая
        // проверка наличия поля — как в account.setObsceneFilter.
        return json.has("response")
    }

    /** Convenience wrapper: toggles a boolean param. */
    suspend fun settingsGeneralToggleNotify(
        key: String,
        enabled: Boolean,
    ): Boolean = settingsGeneralSetNotifySettings(key, if (enabled) "true" else "false")

    /** settingsGeneral.getAccountSettings — same shape, page="account". */
    suspend fun settingsGeneralGetAccountSettings(page: String = "account") =
        settingsGeneralGetNotifySettings(page)

    /** settingsGeneral.setAccountSettings — same PATCH shape. */
    suspend fun settingsGeneralSetAccountSettings(key: String, value: String) =
        settingsGeneralSetNotifySettings(key, value)

    /** settingsGeneral.getPrivacySettings — page="privacy". */
    suspend fun settingsGeneralGetPrivacySettings() = settingsGeneralGetNotifySettings("privacy")

    /** settingsGeneral.setPrivacySettings — same PATCH. */
    suspend fun settingsGeneralSetPrivacySettings(key: String, value: String) =
        settingsGeneralSetNotifySettings(key, value)

    /** settings.startChangeNotifyEmail — start the email-change flow. */
    suspend fun settingsStartChangeNotifyEmail(): com.google.gson.JsonObject? {
        if (isOffline()) return null
        return call("settings.startChangeNotifyEmail", emptyMap())
    }

    /** settings.performEmailBannerAction — bind/dismiss email banner. */
    suspend fun settingsPerformEmailBannerAction(action: String, hash: String? = null): Boolean {
        val args = mutableMapOf("action" to action)
        if (!hash.isNullOrBlank()) args["hash"] = hash
        val json = call("settings.performEmailBannerAction", args) ?: return false
        return json.getAsJsonObject("response")?.get("result")?.asInt == 1
    }

    private fun parseSettingsSection(o: com.google.gson.JsonObject): re.pinok.data.model.SettingsSection {
        val params = o.getAsJsonArray("params")?.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val p = el.asJsonObject
            re.pinok.data.model.SettingsParam(
                key = p.get("key")?.asString ?: "",
                type = p.get("type")?.asString ?: "toggle",
                title = p.get("title")?.takeIf { !it.isJsonNull }?.asString,
                description = p.get("description")?.takeIf { !it.isJsonNull }?.asString,
                isChecked = p.get("is_checked")?.takeIf { !it.isJsonNull }?.asBoolean,
                value = p.get("value")?.takeIf { !it.isJsonNull }?.asString,
                options = p.getAsJsonArray("options")?.mapNotNull { opt ->
                    if (!opt.isJsonObject) return@mapNotNull null
                    val op = opt.asJsonObject
                    re.pinok.data.model.SettingsParamOption(
                        value = op.get("value")?.asString ?: "",
                        label = op.get("label")?.asString ?: "",
                    )
                } ?: emptyList(),
            )
        } ?: emptyList()
        return re.pinok.data.model.SettingsSection(
            id = o.get("id")?.asString ?: "",
            title = o.get("title")?.takeIf { !it.isJsonNull }?.asString,
            description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
            params = params,
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Silent mode («Не беспокоить»)
    // ════════════════════════════════════════════════════════════════════

    /** account.getSilentModeStatus — current silent-mode state. */
    suspend fun accountGetSilentModeStatus(): re.pinok.data.model.SilentModeStatus? {
        if (isOffline()) return null
        val json = call("account.getSilentModeStatus", emptyMap()) ?: return null
        return try {
            val r = json.getAsJsonObject("response") ?: return null
            re.pinok.data.model.SilentModeStatus(
                silentUntil = r.get("silent_until")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                sound = r.get("sound")?.takeIf { !it.isJsonNull }?.asInt ?: 1,
                disabledUntil = r.get("disabled_until")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                disabledMentions = r.get("disabled_mentions")?.takeIf { !it.isJsonNull }?.asInt == 1,
                disabledMassMentions = r.get("disabled_mass_mentions")?.takeIf { !it.isJsonNull }?.asInt == 1,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "accountGetSilentModeStatus error", e)
            null
        }
    }

    /**
     * account.startSilentMode — turn on "Не беспокоить".
     * @param time duration in seconds. 0 (or -1) = forever. 900 = 15 min,
     *             3600 = 1 hour, 28800 = 8 hours.
     */
    suspend fun accountStartSilentMode(time: Long): Boolean {
        if (isOffline()) return false
        val json = call("account.startSilentMode", mapOf("time" to time.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    /** account.stopSilentMode — turn off "Не беспокоить". */
    suspend fun accountStopSilentMode(): Boolean {
        if (isOffline()) return false
        val json = call("account.stopSilentMode", emptyMap()) ?: return false
        return json.getAsJsonObject("response") != null
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Ban list (manage blocked users)
    // ════════════════════════════════════════════════════════════════════

    /**
     * account.getBanned — list of blocked users.
     * @param offset pagination offset
     * @param count page size (max 200)
     * @param fields extra user fields to fetch
     */
    suspend fun accountGetBanned(
        offset: Int = 0,
        count: Int = 50,
        fields: String = "photo_100,photo_200,first_name,last_name,ban_date",
    ): re.pinok.data.model.BannedUsersList? {
        if (isOffline()) return null
        val args = mapOf(
            "offset" to offset.toString(),
            "count" to count.toString(),
            "fields" to fields,
        )
        val json = call("account.getBanned", args) ?: return null
        return try {
            val r = json.getAsJsonObject("response") ?: return null
            val items = r.getAsJsonArray("items")?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val u = el.asJsonObject
                re.pinok.data.model.BannedUser(
                    id = u.get("id")?.asLong ?: 0L,
                    firstName = u.get("first_name")?.asString ?: "",
                    lastName = u.get("last_name")?.asString ?: "",
                    photo100 = u.get("photo_100")?.takeIf { !it.isJsonNull }?.asString,
                    photo200 = u.get("photo_200")?.takeIf { !it.isJsonNull }?.asString,
                    banDate = u.get("ban_date")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                )
            } ?: emptyList()
            re.pinok.data.model.BannedUsersList(
                count = r.get("count")?.asInt ?: items.size,
                items = items,
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "accountGetBanned error", e)
            null
        }
    }

    /** account.unban — unblock a user. */
    suspend fun accountUnban(ownerId: Long): Boolean {
        if (isOffline()) return false
        val json = call("account.unban", mapOf("owner_id" to ownerId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Community messages opt-in/out
    // ════════════════════════════════════════════════════════════════════

    /** messages.allowMessagesFromGroup — opt in to community push messages. */
    suspend fun messagesAllowFromGroup(groupId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.allowMessagesFromGroup",
            mapOf("group_id" to groupId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    /** messages.denyMessagesFromGroup — opt out of community push messages. */
    suspend fun messagesDenyFromGroup(groupId: Long): Boolean {
        if (isOffline()) return false
        val json = call("messages.denyMessagesFromGroup",
            mapOf("group_id" to groupId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Apps push notifications
    // ════════════════════════════════════════════════════════════════════

    /** apps.allowNotifications — allow a mini-app to send push. */
    suspend fun appsAllowNotifications(appId: Long): Boolean {
        if (isOffline()) return false
        val json = call("apps.allowNotifications",
            mapOf("app_id" to appId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    /** apps.denyNotifications — deny a mini-app push. */
    suspend fun appsDenyNotifications(appId: Long): Boolean {
        if (isOffline()) return false
        val json = call("apps.denyNotifications",
            mapOf("app_id" to appId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    /** apps.readAllNotifications — mark all app notifications as read. */
    suspend fun appsReadAllNotifications(appId: Long): Boolean {
        if (isOffline()) return false
        val json = call("apps.readAllNotifications",
            mapOf("app_id" to appId.toString())) ?: return false
        return json.getAsJsonObject("response") != null
    }

    // ════════════════════════════════════════════════════════════════════
    // §1-NOTIF-ANALYSIS: Obscene text filter (account.setObsceneFilter)
    // ════════════════════════════════════════════════════════════════════

    /** account.setObsceneFilter — toggle the obscene-word filter.
     *  VK API возвращает {"response": 1} (число, не объект). Раньше тут было
     *  json.getAsJsonObject("response") — это падало с ClassCastException:
     *  JsonPrimitive cannot be cast to JsonObject (#300). Используем has(),
     *  как в соседних set-методах (account.setSilentMode и т.д.). */
    suspend fun accountSetObsceneFilter(enabled: Boolean): Boolean {
        if (isOffline()) return false
        val json = call("account.setObsceneFilter",
            mapOf("value" to if (enabled) "1" else "0")) ?: return false
        return json.has("response")
    }

    // ════════════════════════════════════════════════════════════════════
    // §37.12 Phase 1: VK Clips API methods
    //clip = video с is_clips=1 (или duration<=60 + vertical). Все методы
    // возвращают JSON-safe значения: null/empty при ошибке, без FATAL.
    // ════════════════════════════════════════════════════════════════════

    /** Результат ленты clips: список + курсор пагинации + профили/группы. */
    data class ClipsFeedResult(
        val items: List<Video>,
        val nextFrom: String?,
        val profiles: Map<Long, UserProfile>,
        val groups: Map<Long, GroupInfo>,
    )

    /**
     * newsfeed.getFeed с section="clips" — LEGACY clips-лента.
     *
     * §37.12 #324: VK web НЕ использует этот метод для /clips — он использует
     * [shortVideoGetRecom]. Оставляем как fallback (когда shortVideo.getRecom
     * недоступен или вернул 0 clips). VK иногда отдаёт clips через newsfeed
     * в формате {type:"video", video:{...}} (legacy) или {type:"short_video",
     * short_video:{...}} (new) или {type:"post", attachments:[{type:"video",...}]}.
     *
     * @param section "clips" (также "clips_subscriptions", "clips_trends")
     * @param count 5..50 (рекомендуется 10 для vertical pager)
     * @param startFrom курсор из предыдущего ответа (next_from)
     */
    suspend fun newsfeedGetClipsFeed(
        section: String = "clips",
        count: Int = 10,
        startFrom: String? = null,
    ): ClipsFeedResult {
        if (isOffline()) return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        val args = mutableMapOf(
            "section" to section,
            "count" to count.toString(),
            "extended" to "1",
        )
        startFrom?.let { args["start_from"] = it }
        val json = call("newsfeed.getFeed", args) ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        return try {
            // §37.12 #325: getObj/getArr вместо getAsJsonObject/getAsJsonArray —
            // безопасны против ClassCastException если поле это JsonPrimitive.
            val resp = getObj(json, "response") ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
            val items = getArr(resp, "items") ?: emptyList()
            val clips = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                // §37.12 #324: расширены wrapper-варианты — VK отдаёт clips
                // в newsfeed.items в разных обёртках в зависимости от версии API.
                // §37.12 #325: getObj вместо o.getAsJsonObject — безопасен против ClassCastException.
                // §37.12 #325: try/catch на каждый item — один bad clip не должен валить всю страницу.
                try {
                    val videoObj = getObj(o, "video") ?: getObj(o, "clip")
                        ?: getObj(o, "short_video") ?: getObj(o, "item")
                    if (videoObj != null) {
                        parseVideoFull(videoObj)
                    } else {
                        // post+attachments: ищем первый video-attachment
                        val post = getObj(o, "post")
                        val attachments = post?.let { getArr(it, "attachments") }
                        val videoAttachment = attachments?.firstOrNull { att ->
                            att.isJsonObject &&
                                safeString(att.asJsonObject.get("type")) == "video" &&
                                att.asJsonObject.has("video")
                        }
                        val vObj = videoAttachment?.let { getObj(it.asJsonObject, "video") }
                        if (vObj != null) parseVideoFull(vObj) else null
                    }
                } catch (e: Exception) {
                    AppLog.w("VKApiClient", "newsfeedGetClipsFeed: skip bad item ${o.toString().take(200)}: ${e.message}")
                    null
                }
            }
            val nextFrom = safeString(resp.get("next_from"))
            val profiles = parseUsersJsonArray(getArr(resp, "profiles"))
            val groups = parseGroupsJsonArray(getArr(resp, "groups"))
            ClipsFeedResult(clips, nextFrom, profiles, groups)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "newsfeedGetClipsFeed parse error", e)
            ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        }
    }

    /**
     * §37.12 #324: shortVideo.getRecom — CANONICAL clips-лента (как VK web).
     *
     * Это метод, который VK web использует на /clips. В отличие от
     * newsfeed.getFeed(section=clips), shortVideo.getRecom возвращает clips
     * с INLINE files[] (CDN URLs mp4_720, hls) + access_key + short_video_auth_token,
     * что убирает необходимость lazy-fetch через video.get (который для clips
     * НЕ возвращает files[]).
     *
     * Поддерживает ДВА формата ответа:
     *  - LEGACY: {feed:{items:[{id, owner_id, files, likes, ...}]}, page_anchor}
     *  - NEW (short_video_full): {feed:{items:[{type:"short_video_full",
     *    item:{owner_id, id, files, engagement{...}, covers, access{...}}}]}}
     *
     * @param section "clips" | "subscriptions" | "trends" (VK web ref-значения)
     * @param count 5..50
     * @param pageAnchor курсор из предыдущего ответа (page_anchor, НЕ next_from!)
     */
    suspend fun shortVideoGetRecom(
        section: String = "clips",
        count: Int = 10,
        pageAnchor: String? = null,
    ): ClipsFeedResult {
        if (isOffline()) return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        val args = mutableMapOf(
            "ref" to section,
            "count" to count.toString(),
            // fields — те же что VK web передаёт (профили авторов)
            "fields" to "photo_50,photo_100,photo_200,photo_400_orig,first_name,last_name,first_name_gen,last_name_gen,first_name_acc,last_name_acc,sex,online,can_write_private_message,can_send_friend_request,can_access_closed,verified,trending,friend_status,is_subscribed,is_hidden_from_feed,blacklisted,blacklisted_by_me,deactivated",
        )
        pageAnchor?.let { args["page_anchor"] = it }
        val json = call("shortVideo.getRecom", args) ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        return try {
            // §37.12 #325: getObj/getArr вместо getAsJsonObject/getAsJsonArray —
            // безопасны против ClassCastException если поле это JsonPrimitive.
            val resp = getObj(json, "response") ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
            val feed = getObj(resp, "feed") ?: resp
            val items = getArr(feed, "items") ?: emptyList()
            val clips = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                // §37.12 #324: NEW format {type:"short_video_full", item:{...}}
                // §37.12 #325: getObj вместо o.getAsJsonObject — безопасен против ClassCastException.
                // §37.12 #325: try/catch на каждый item — один bad clip не должен валить всю страницу.
                try {
                    val typeStr = safeString(o.get("type"))
                    val videoObj = when {
                        typeStr == "short_video_full" || typeStr == "short_video_full_legacy" ->
                            getObj(o, "item") ?: o
                        else -> getObj(o, "video") ?: getObj(o, "clip")
                            ?: getObj(o, "short_video") ?: getObj(o, "item") ?: o
                    }
                    parseVideoFull(videoObj)
                } catch (e: Exception) {
                    AppLog.w("VKApiClient", "shortVideoGetRecom: skip bad item ${o.toString().take(200)}: ${e.message}")
                    null
                }
            }
            val nextFrom = safeString(feed.get("page_anchor")) ?: safeString(resp.get("page_anchor"))
            val profiles = parseUsersJsonArray(getArr(resp, "profiles"))
            val groups = parseGroupsJsonArray(getArr(resp, "groups"))
            ClipsFeedResult(clips, nextFrom, profiles, groups)
        } catch (e: Exception) {
            // §37.12 #325: логируем структуру первого item для диагностики
            try {
                val resp2 = getObj(json, "response")
                val feed2 = resp2?.let { getObj(it, "feed") } ?: resp2
                val items2 = feed2?.let { getArr(it, "items") }
                val firstItem = items2?.firstOrNull()
                AppLog.e("VKApiClient", "shortVideoGetRecom parse error: ${e.message} | firstItem=${firstItem?.toString()?.take(500)}", e)
            } catch (_: Exception) {
                AppLog.e("VKApiClient", "shortVideoGetRecom parse error", e)
            }
            ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        }
    }

    /**
     * §37.12 #326: shortVideo.get — fetch ОДНОГО клипа с files[] (CDN URLs).
     *
     * Это метод, который VK web использует на странице клипа (apiPrefetchCache
     * в сохранённой HTML-странице). Параметр: short_video_raw_ids = "owner_id_video_id"
     * (БЕЗ access_key). Ответ: {response:{feed:{items:[{type:"short_video_full",
     * item:{owner_id, id, files:{mp4_144, mp4_240, mp4_360, mp4_480, mp4_720,
     * mp4_1080, hls, ...}, engagement{...}, covers[], access{...}, duration_seconds}}}]}}
     *
     * В отличие от [videoGetClipById] (video.get, который НЕ возвращает files[]
     * для clips), shortVideo.get ВСЕГДА возвращает files[] с прямыми CDN URLs.
     *
     * Это КЛЮЧЕВОЙ метод для воспроизведения clips: когда [shortVideoGetRecom]
     * возвращает clips без files[] (или с устаревшими URL'ами), этот метод
     * подтягивает свежие CDN-ссылки для ExoPlayer.
     *
     * @param shortVideoRawIds "owner_id_video_id" (например "-229917482_456239261")
     * @return Video с заполненным files[] (bestPlayUrl != null) или null
     */
    suspend fun shortVideoGet(
        shortVideoRawIds: String,
    ): Video? {
        if (isOffline()) return null
        val args = mapOf(
            "short_video_raw_ids" to shortVideoRawIds,
            // fields — те же что VK web передаёт (профили/группы авторов)
            "fields" to "photo_50,photo_100,photo_200,photo_400_orig,first_name,last_name,first_name_gen,last_name_gen,first_name_acc,last_name_acc,sex,online,can_write_private_message,can_send_friend_request,can_access_closed,verified,trending,friend_status,is_subscribed,is_hidden_from_feed,blacklisted,blacklisted_by_me,deactivated",
        )
        val json = call("shortVideo.get", args) ?: run {
            AppLog.w("VKApiClient", "shortVideo.get: call returned null for $shortVideoRawIds (errCode=$lastApiErrorCode)")
            return null
        }
        return try {
            val resp = getObj(json, "response") ?: run {
                AppLog.w("VKApiClient", "shortVideo.get: no 'response' field for $shortVideoRawIds | json=${json.toString().take(300)}")
                return null
            }
            val feed = getObj(resp, "feed") ?: resp
            // §37.12 #326 fix: НЕ делаем `?: emptyList()` — это меняет тип на
            // Iterable<JsonElement>, у которого нет .size()/.isEmpty(). Держим
            // JsonArray? и проверяем null явно (как в остальных местах файла).
            val items = getArr(feed, "items")
            if (items == null || items.size() == 0) {
                AppLog.w("VKApiClient", "shortVideo.get: empty items for $shortVideoRawIds | resp=${resp.toString().take(300)}")
                return null
            }
            val firstItem = items.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val typeStr = safeString(firstItem.get("type"))
            // §37.12 #326: ответ имеет ту же структуру что shortVideo.getRecom:
            // {type:"short_video_full", item:{...}} или {type:"short_video_full_legacy", item:{...}}
            val videoObj = when {
                typeStr == "short_video_full" || typeStr == "short_video_full_legacy" ->
                    getObj(firstItem, "item") ?: firstItem
                else -> getObj(firstItem, "item") ?: getObj(firstItem, "video")
                    ?: getObj(firstItem, "clip") ?: getObj(firstItem, "short_video") ?: firstItem
            }
            val video = parseVideoFull(videoObj)
            AppLog.i("VKApiClient", "shortVideo.get ok: $shortVideoRawIds → id=${video.id} ownerId=${video.ownerId} " +
                "files=${video.files?.size ?: 0} bestPlayUrl=${if (video.bestPlayUrl != null) "yes" else "NO"} " +
                "likes=${video.likesCount} accessKey=${if (video.accessKey != null) "yes" else "no"}")
            video
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "shortVideo.get parse error for $shortVideoRawIds: ${e.message} | json=${json.toString().take(500)}", e)
            null
        }
    }

    /** Convenience overload: shortVideoGet(ownerId, videoId) → shortVideoGet("ownerId_videoId"). */
    suspend fun shortVideoGet(ownerId: Long, videoId: Long): Video? =
        shortVideoGet("${ownerId}_$videoId")

    /**
     * #GROUP-CLIPS: shortVideo.getOwnerVideos — клипы сообщества/пользователя.
     *
     * Это метод, которым VK web грузит вкладку «Клипы» (group_tab_short_videos)
     * на странице сообщества/профиля: `shortVideo.getOwnerVideos {owner_id, count}`.
     * Ответ — тот же формат что shortVideo.getRecom:
     * {response:{items:[{type:"short_video_full", item:{...files, covers, engagement...}}]}}
     *
     * @param ownerId владелец клипов (для сообщества передаём -groupId)
     * @param count  количество (web передаёт 9)
     */
    suspend fun shortVideoGetOwnerVideos(
        ownerId: Long,
        count: Int = 30,
    ): List<Video> {
        if (isOffline()) return emptyList()
        val args = mapOf(
            "owner_id" to ownerId.toString(),
            "count" to count.toString(),
        )
        val json = call("shortVideo.getOwnerVideos", args) ?: return emptyList()
        return try {
            val resp = getObj(json, "response") ?: return emptyList()
            val items = getArr(resp, "items")
            if (items == null || items.size() == 0) {
                AppLog.d("VKApiClient", "shortVideo.getOwnerVideos: no items for owner=$ownerId")
                return emptyList()
            }
            items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val o = el.asJsonObject
                try {
                    val typeStr = safeString(o.get("type"))
                    val videoObj = when {
                        typeStr == "short_video_full" || typeStr == "short_video_full_legacy" ->
                            getObj(o, "item") ?: o
                        else -> getObj(o, "video") ?: getObj(o, "clip")
                            ?: getObj(o, "short_video") ?: getObj(o, "item") ?: o
                    }
                    parseVideoFull(videoObj)
                } catch (e: Exception) {
                    AppLog.w("VKApiClient", "shortVideo.getOwnerVideos: skip bad item ${o.toString().take(200)}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "shortVideo.getOwnerVideos parse error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * video.get с расширенным ответом — для clip-detail (extended=1).
     * Возвращает clip со всеми полями (likes, reposts, comments, music_info,
     * can_*, is_*, nearest_clips через отдельный запрос).
     */
    suspend fun videoGetClipById(
        ownerId: Long,
        videoId: Long,
        accessKey: String? = null,
    ): Video? {
        if (isOffline()) return null
        val videosParam = buildString {
            append("${ownerId}_$videoId")
            if (!accessKey.isNullOrBlank()) append("_$accessKey")
        }
        val args = mapOf(
            "videos" to videosParam,
            "extended" to "1",
        )
        val json = call("video.get", args) ?: return null
        return try {
            // §37.12 #325: getObj/getArr вместо getAsJsonObject/getAsJsonArray —
            // безопасны против ClassCastException если поле это JsonPrimitive.
            val items = getObj(json, "response")?.let { getArr(it, "items") }
            if (items == null || items.size() == 0) {
                AppLog.w("VKApiClient", "videoGetClipById: пустой ответ для $videosParam")
                return null
            }
            val firstItem = items.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return null
            parseVideoFull(firstItem)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetClipById parse error", e)
            null
        }
    }

    /**
     * video.getPlayerConfig — прямые URL для ExoPlayer.
     * Возвращает JSON {url, hash, subtitles, ...} или null.
     * Используется когда files пустой (приватные/age-restricted клипы).
     */
    suspend fun videoGetPlayerConfig(
        ownerId: Long,
        videoId: Long,
        accessKey: String? = null,
    ): JsonObject? {
        if (isOffline()) return null
        val videosParam = buildString {
            append("${ownerId}_$videoId")
            if (!accessKey.isNullOrBlank()) append("_$accessKey")
        }
        val json = call("video.getPlayerConfig", mapOf("video" to videosParam)) ?: return null
        return try {
            json.getAsJsonObject("response")
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetPlayerConfig error", e)
            null
        }
    }

    /**
     * video.addViewingHistoryRecord — запись просмотра в историю.
     * @param durationWatched сколько секунд просмотрено (для recommendations)
     *
     * §37.12 #322: этот метод — BFF-only в VK web (недоступен через прямой
     * vk1.a.* токен, стабильно возвращает error 100). Поэтому вызываем в
     * silent-режиме: ошибки логируются на D, не E, чтобы не засорять logcat
     * каждый swipe в clips. Просмотр трекается best-effort.
     */
    suspend fun videoAddViewingHistoryRecord(
        ownerId: Long,
        videoId: Long,
        durationWatched: Int,
        accessKey: String? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "video_id" to videoId.toString(),
            "duration_watched" to durationWatched.toString(),
        )
        accessKey?.let { args["access_key"] = it }
        val json = call("video.addViewingHistoryRecord", args, silent = true) ?: return false
        return json.has("response")
    }

    /**
     * video.getLongPollServer — отдельный LP для live-clip-чата.
     * Возвращает {server, key, ts} для polling-петли live-сообщений.
     */
    suspend fun videoGetLongPollServer(
        ownerId: Long,
        videoId: Long,
    ): VideoLongPollServer? {
        if (isOffline()) return null
        val json = call("video.getLongPollServer",
            mapOf("owner_id" to ownerId.toString(), "video_id" to videoId.toString())) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            VideoLongPollServer(
                server = safeString(resp.get("server")) ?: "",
                key = safeString(resp.get("key")) ?: "",
                ts = safeString(resp.get("ts")) ?: "",
            )
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetLongPollServer error", e)
            null
        }
    }

    /** data class для video.getLongPollServer ответа (live-clip чат).
     *  Отдельная от [LongPollServer] (messages LP) — поля отличаются
     *  (String ts без pts), поэтому не переиспользуем messages-вариант. */
    data class VideoLongPollServer(
        val server: String,
        val key: String,
        val ts: String,
    )

    /**
     * video.getAds — реклама перед/после клипа.
     * Возвращает список рекламных вставок или null.
     */
    suspend fun videoGetAds(
        ownerId: Long,
        videoId: Long,
        accessKey: String? = null,
    ): JsonArray? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "owner_id" to ownerId.toString(),
            "video_id" to videoId.toString(),
        )
        accessKey?.let { args["access_key"] = it }
        val json = call("video.getAds", args) ?: return null
        return try {
            json.getAsJsonObject("response")?.getAsJsonArray("ads")
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoGetAds error", e)
            null
        }
    }

    /**
     * video.trackAdEvent — трекинг показа/клика рекламы.
     * @param eventType "show" / "click" / "skip" / "complete"
     */
    suspend fun videoTrackAdEvent(
        ownerId: Long,
        videoId: Long,
        adId: Long,
        eventType: String,
    ): Boolean {
        if (isOffline()) return false
        val json = call("video.trackAdEvent", mapOf(
            "owner_id" to ownerId.toString(),
            "video_id" to videoId.toString(),
            "ad_id" to adId.toString(),
            "event_type" to eventType,
        )) ?: return false
        return json.has("response")
    }

    /**
     * groups.edit с notifications=0/1 — toggle push-уведомлений о новых clips
     * от автора (вместо wall.subscribe для group-authors).
     */
    suspend fun groupsEditNotifications(
        groupId: Long,
        notifications: Boolean,
    ): Boolean {
        if (isOffline()) return false
        val json = call("groups.edit", mapOf(
            "group_id" to (-groupId).toString(),
            "notifications" to if (notifications) "1" else "0",
        )) ?: return false
        return json.has("response")
    }

    /**
     * fave.addPage — добавить clip-автора (user или group) в закладки.
     * Универсальный метод: если userId>0 — user, если groupId>0 — group.
     */
    suspend fun faveAddPage(userId: Long? = null, groupId: Long? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        userId?.let { args["user_id"] = it.toString() }
        groupId?.let { args["group_id"] = it.toString() }
        val json = call("fave.addPage", args) ?: return false
        return json.has("response")
    }

    /** Поиск clips по строке/хештегу. */
    suspend fun searchClips(
        query: String,
        count: Int = 20,
        offset: Int = 0,
    ): ClipsFeedResult {
        if (isOffline()) return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        val args = mutableMapOf(
            "q" to query,
            "count" to count.toString(),
            "offset" to offset.toString(),
            "extended" to "1",
            // search.getHints или newsfeed.getFeed с section=search_clips
            // Возвращаем как ClipsFeedResult.
        )
        val json = call("search.getClips", args) ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        return try {
            val resp = json.getAsJsonObject("response") ?: return ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
            val items = resp.getAsJsonArray("items") ?: emptyList()
            val clips = items.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                parseVideoFull(el.asJsonObject)
            }
            val profiles = parseUsersJsonArray(resp.getAsJsonArray("profiles"))
            val groups = parseGroupsJsonArray(resp.getAsJsonArray("groups"))
            val nextFrom = if (offset + clips.size < safeInt(resp.get("count"))) {
                (offset + clips.size).toString()
            } else null
            ClipsFeedResult(clips, nextFrom, profiles, groups)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "searchClips error", e)
            ClipsFeedResult(emptyList(), null, emptyMap(), emptyMap())
        }
    }

    // ── §37.12 Phase 5: Clip creation — upload pipeline ────────────────────
    //
    // VK video upload flow (см. VK_IMPORT_API.MD §17 / VK API docs):
    //  1. video.save — резервирует видео, возвращает {upload_url, video_id, owner_id}.
    //     Для clips передаём is_clips=1 + (опц.) album_id=-2 (clips-альбом группы).
    //  2. POST video_file (multipart) на upload_url → ответ {size, video_id}
    //     (сервер асинхронно начинает обработку: транскодинг, превью).
    //  3. После upload видео появляется в video.get через ~10-60 секунд
    //     (статус processing). UI должен polled-check status.
    //
    // ВАЖНО: VK ограничивает clips — формат mp4, длительность ≤ 60 сек,
    //  вертикальная ориентация (9:16). Сервер сам транскодит.

    /** Результат шага 1 (video.save): зарезервированный видео + upload_url. */
    data class VideoUploadTicket(
        val uploadUrl: String,
        val videoId: Long,
        val ownerId: Long,
    )

    /**
     * §37.12 Phase 5: video.save — резервирование видео перед upload.
     *
     * @param name заголовок клипа
     * @param description описание (опц., может содержать хештеги)
     * @param isClips true → короткое вертикальное видео (clip)
     * @param groupId >0 → загрузка в сообщество (для clips — обязательно,
     *  VK принимает clips только от групп; для user-clips через web).
     * @param wallpost 0 = не публиковать на стену (clip будет в clips-ленте)
     * @return [VideoUploadTicket] или null при ошибке
     */
    suspend fun videoSave(
        name: String,
        description: String? = null,
        isClips: Boolean = true,
        groupId: Long? = null,
        wallpost: Int = 0,
    ): VideoUploadTicket? {
        if (isOffline()) return null
        val args = mutableMapOf(
            "name" to name,
            "is_clips" to if (isClips) "1" else "0",
            "wallpost" to wallpost.toString(),
            "no_comments" to "0",
            "repeat" to "1", // clips — loop
            "privacy_view" to "all",
        )
        description?.takeIf { it.isNotBlank() }?.let { args["description"] = it }
        groupId?.takeIf { it > 0 }?.let { args["group_id"] = it.toString() }
        // album_id=-2 → специальный clips-альбом (как в VK web).
        if (isClips) args["album_id"] = "-2"

        val json = call("video.save", args) ?: return null
        return try {
            val resp = json.getAsJsonObject("response") ?: return null
            val uploadUrl = safeString(resp.get("upload_url")) ?: return null
            val videoId = resp.get("video_id")?.asLong ?: return null
            val ownerId = (groupId?.takeIf { it > 0 }?.let { -it } ?: 0L)
                .let { if (it != 0L) it else resp.get("owner_id")?.asLong ?: 0L }
            VideoUploadTicket(uploadUrl, videoId, ownerId)
        } catch (e: Exception) {
            AppLog.e("VKApiClient", "videoSave error", e)
            null
        }
    }

    /**
     * §37.12 Phase 5: upload видеофайла на upload_url (шаг 2).
     *
     * Читает файл по [uri] через ContentResolver, отправляет multipart-form
     * с полем "video_file". VK возвращает `{size, video_id}` — оба поля
     * могут отсутствовать при частичной ошибке, проверяем только HTTP 200.
     *
     * @return true если HTTP-загрузка прошла успешно (сервер принял файл).
     *  Серверная обработка (транскодинг) продолжается асинхронно.
     */
    suspend fun videoUploadFile(uploadUrl: String, uri: Uri): Boolean {
        if (isOffline()) return false
        return withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        AppLog.e("VKApiClient", "videoUploadFile: cannot open input stream for $uri")
                        return@withContext false
                    }
                // VK принимает видео как "video_file" в multipart/form-data.
                // Content-Type по умолчанию video/mp4 — OK для clips.
                val mediaType = "video/mp4".toMediaType()
                val requestBody = bytes.toRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("video_file", "clip.mp4", requestBody)
                    .build()
                val req = Request.Builder().url(uploadUrl).post(multipart).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLog.e("VKApiClient", "videoUploadFile HTTP ${resp.code}: ${resp.message}")
                        return@withContext false
                    }
                    val body = resp.body?.string().orEmpty()
                    AppLog.i("VKApiClient", "videoUploadFile ok: $body")
                    true
                }
            } catch (e: Exception) {
                AppLog.e("VKApiClient", "videoUploadFile error", e)
                false
            }
        }
    }

    /**
     * §37.12 Phase 5: удалить клип (если пользователь отменил загрузку
     *  или upload не удался — чистим зарезервированный video_id).
     */
    suspend fun videoDeleteClip(videoId: Long, ownerId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf(
            "video_id" to videoId.toString(),
            "owner_id" to ownerId.toString(),
            "target_id" to ownerId.toString(),
        )
        val json = call("video.delete", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.9 fave.removePage — убрать clip-автора (user или group) из закладок.
     * Парный к [faveAddPage].
     */
    suspend fun faveRemovePage(userId: Long? = null, groupId: Long? = null): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf<String, String>()
        userId?.let { args["user_id"] = it.toString() }
        groupId?.let { args["group_id"] = it.toString() }
        val json = call("fave.removePage", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.9 wall.subscribe — включить уведомления о новых клипах автора.
     * Работает и для user-clips, и для group-clips (owner_id с правильным знаком).
     */
    suspend fun wallSubscribe(ownerId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf("owner_id" to ownerId.toString())
        val json = call("wall.subscribe", args) ?: return false
        return json.has("response")
    }

    /** §37.9 wall.unsubscribe — выключить уведомления о новых клипах автора. */
    suspend fun wallUnsubscribe(ownerId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf("owner_id" to ownerId.toString())
        val json = call("wall.unsubscribe", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.9 newsfeed.banUser — скрыть автора из рекомендаций clips
     * (в отличие от account.ban, не блокирует полностью, а только убирает из feed).
     *
     * §37.12 #324: ранее вызывал несуществующий метод `newsfeed.ban` → err=3
     * "Unknown method passed". Корректное имя метода — `newsfeed.banUser` (для
     * пользователей) или `newsfeed.banOwner` (для групп). VK API принимает
     * user_ids как список для пользователей и group_ids для групп.
     */
    suspend fun newsfeedBanUser(ownerId: Long): Boolean {
        if (isOffline()) return false
        val args = if (ownerId > 0) {
            mapOf("user_ids" to ownerId.toString())
        } else {
            // Группа: передаём group_ids с положительным ID (без минуса).
            mapOf("group_ids" to (-ownerId).toString())
        }
        val json = call("newsfeed.banUser", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.9 users.subscribe — подписка на user-clip автора (для user-clips).
     * Публичного users.subscribe в open API нет, поэтому эмулируем через
     * friends.add (если не друг) — VK сам обрабатывает публичные подписки.
     */
    suspend fun usersSubscribe(userId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf("user_id" to userId.toString())
        val json = call("friends.add", args) ?: return false
        // response: 1 — заявка отправлена, 2 — заявка одобрена, 4 — повторная отправка
        val resp = json.get("response")
        return resp != null && resp.isJsonPrimitive && resp.asInt in setOf(1, 2, 4)
    }

    /** §37.9 users.unsubscribe — отписка от user-clip автора (через friends.delete). */
    suspend fun usersUnsubscribe(userId: Long): Boolean {
        if (isOffline()) return false
        val args = mapOf("user_id" to userId.toString())
        val json = call("friends.delete", args) ?: return false
        // response.success = 1 для исходящей подписки; для друга возвращает {success:1, friend_deleted:1, out_request_deleted:1}
        val resp = json.getAsJsonObject("response") ?: return false
        val success = resp.get("success")
        return (success != null && success.isJsonPrimitive && success.asInt == 1) ||
            resp.has("out_request_deleted")
    }

    /**
     * §37.9 video.edit — редактировать свой клип (название, описание, приватность).
     * Только если clip.canEdit == true.
     */
    suspend fun videoEdit(
        videoId: Long,
        ownerId: Long,
        name: String? = null,
        description: String? = null,
        isPrivate: Boolean? = null,
    ): Boolean {
        if (isOffline()) return false
        val args = mutableMapOf(
            "video_id" to videoId.toString(),
            "owner_id" to ownerId.toString(),
        )
        name?.let { args["name"] = it }
        description?.let { args["desc"] = it }
        isPrivate?.let { args["privacy_view"] = if (it) "1" else "0" }
        val json = call("video.edit", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.9 Пожаловаться на клип — execute/VKScript через API.video.report.
     * Прямой публичный метод reportVideo отсутствует в open VK API, но BFF VK web
     * дёргает internal API; для open-клиента используем execute-обёртку,
     * вызывающую accounts/reportVideo через внутренний эндпоинт (best-effort).
     */
    suspend fun reportVideo(ownerId: Long, videoId: Long, reason: Int = 0, comment: String? = null): Boolean {
        if (isOffline()) return false
        // execute: var r = API.video.report({owner_id:..,video_id:..,reason:..,comment:..}); return r;
        val commentJs = comment?.let { ",comment:\"${it.replace("\"", "\\\"")}\"" } ?: ""
        val code = "var r = API.video.report({owner_id:$ownerId, video_id:$videoId, reason:$reason$commentJs}); return r;"
        val args = mapOf(
            "code" to code,
        )
        val json = call("execute", args) ?: return false
        return json.has("response")
    }

    /**
     * §37.10 clips_dislike — BFF execute/storage.set, т.к. публичного clips_dislike нет.
     * Влияет на рекомендации (best-effort; зависит от access к BFF).
     */
    suspend fun clipsDislike(ownerId: Long, videoId: Long): Boolean {
        if (isOffline()) return false
        val key = "clips_dislike_${ownerId}_$videoId"
        val code = "var r = API.storage.set({key:\"$key\", value:\"1\"}); return r;"
        val json = call("execute", mapOf("code" to code)) ?: return false
        return json.has("response")
    }

    /** §37.10 убрать дизлайк (clips_remove_dislike) — storage.set value="0". */
    suspend fun clipsRemoveDislike(ownerId: Long, videoId: Long): Boolean {
        if (isOffline()) return false
        val key = "clips_dislike_${ownerId}_$videoId"
        val code = "var r = API.storage.set({key:\"$key\", value:\"0\"}); return r;"
        val json = call("execute", mapOf("code" to code)) ?: return false
        return json.has("response")
    }

    private suspend fun rateLimitWait() {
        while (true) {
            val now = System.currentTimeMillis()
            // Clean old timestamps
            requestTimestamps.removeAll { it < now - RATE_WINDOW_MS }
            if (requestTimestamps.size < MAX_REQUESTS_PER_SECOND) {
                requestTimestamps.add(now)
                return
            }
            // Wait until oldest timestamp exits the window
            val oldest = requestTimestamps.firstOrNull() ?: return
            val waitMs = (oldest + RATE_WINDOW_MS - now).coerceAtLeast(1L)
            delay(waitMs)
        }
    }
}
