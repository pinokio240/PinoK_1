package re.pinok.data.model

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

/**
 * DTOs used by the VK API client and UI.
 *
 * Mostly mirrors VK API 5.243 response shapes — only the fields the UI actually
 * uses are modelled. Optional fields are nullable.
 */

data class UserProfile(
    @SerializedName("id")              val id: Long,
    @SerializedName("first_name")      val firstName: String,
    @SerializedName("last_name")       val lastName: String,
    @SerializedName("photo_100")       val photo100: String? = null,
    @SerializedName("photo_200")       val photo200: String? = null,
    @SerializedName("photo_max")       val photoMax: String? = null,
    @SerializedName("online")          val online: Int = 0,
    @SerializedName("last_seen")       val lastSeen: LastSeen? = null,
    @SerializedName("status")          val status: String? = null,
    @SerializedName("bdate")           val bdate: String? = null,
    @SerializedName("city")            val city: City? = null,
    @SerializedName("country")         val country: Country? = null,
    @SerializedName("verified")        val verified: Int = 0,
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("friends_count")   val friendsCount: Int = 0,
    @SerializedName("counters")        val counters: Counters? = null,
    // --- Поля из исследования VK (usersGetFull, 70+ полей) ---
    @SerializedName("domain")          val domain: String? = null,
    @SerializedName("screen_name")     val screenName: String? = null,
    @SerializedName("sex")             val sex: Int = 0,
    @SerializedName("home_town")       val homeTown: String? = null,
    @SerializedName("mobile_phone")    val mobilePhone: String? = null,
    @SerializedName("home_phone")      val homePhone: String? = null,
    @SerializedName("site")            val site: String? = null,
    @SerializedName("friend_status")   val friendStatus: Int = 0,
    // Audit #40: VK API возвращает эти поля как Int (0/1), не Boolean.
    // Gson не конвертирует Int→Boolean автоматически. Все парсеры в VKApiClient
    // уже используют `?.asInt == 1` — но если будет добавлен Gson().fromJson(),
    // Boolean-тип сломается. Приводим к Int для консистентности с Group model.
    @SerializedName("can_write_private_message") val canWritePrivateMessage: Int = 0,
    @SerializedName("can_post")        val canPost: Int = 0,
    @SerializedName("is_closed")       val isClosed: Int = 0,
    @SerializedName("is_favorite")     val isFavorite: Int = 0,
    @SerializedName("is_subscribed")   val isSubscribed: Int = 0,
    @SerializedName("has_photo")       val hasPhoto: Int = 0,
    @SerializedName("wall_default")    val wallDefault: String? = null,
    @SerializedName("photo_avg_color") val photoAvgColor: String? = null,
    @SerializedName("about")           val about: String? = null,
    @SerializedName("activities")      val activities: String? = null,
    @SerializedName("interests")       val interests: String? = null,
    @SerializedName("music")           val music: String? = null,
    @SerializedName("movies")          val movies: String? = null,
    @SerializedName("books")           val books: String? = null,
    @SerializedName("games")           val games: String? = null,
    @SerializedName("nickname")        val nickname: String? = null,
    @SerializedName("maiden_name")     val maidenName: String? = null,
    @SerializedName("relation")        val relation: Int = 0,
    val cover: Cover? = null,
    val personal: Personal? = null,
) {
    val fullName: String get() = "$firstName $lastName"
    val isOnline: Boolean get() = online == 1

    data class LastSeen(
        @SerializedName("time")     val time: Long,
        @SerializedName("platform") val platform: Int? = null,
    )
    data class City(@SerializedName("title") val title: String)
    data class Country(@SerializedName("title") val title: String)
    data class Counters(
        @SerializedName("friends")        val friends: Int? = null,
        @SerializedName("followers")      val followers: Int? = null,
        @SerializedName("online_friends") val onlineFriends: Int? = null,
        @SerializedName("photos")         val photos: Int? = null,
        @SerializedName("videos")         val videos: Int? = null,
        @SerializedName("audios")         val audios: Int? = null,
        @SerializedName("groups")         val groups: Int? = null,
        @SerializedName("gifts")          val gifts: Int? = null,
    )
    /** Обложка профиля (из investigation: cover.enabled, cover.images[]) */
    data class Cover(
        val enabled: Boolean = false,
        val images: List<String> = emptyList(),
    )
    /** Личные данные (political, religions, etc.) */
    data class Personal(
        val political: Int = 0,
        val religions: String? = null,
        val inspiredBy: String? = null,
        val peopleMain: Int = 0,
        val lifeMain: Int = 0,
        val smoking: Int = 0,
        val alcohol: Int = 0,
    )
}

data class Post(
    @SerializedName("id")             val id: Long,
    @SerializedName("owner_id")       val ownerId: Long,
    @SerializedName("from_id")        val fromId: Long,
    @SerializedName("signer_id")      val signerId: Long? = null,
    @SerializedName("date")           val date: Long,
    @SerializedName("text")           val text: String,
    @SerializedName("attachments")    val attachments: List<Attachment>? = null,
    // Fix #70: copy_history — репосты. VK возвращает массив вложенных постов.
    // UI рендерит первый элемент как «Репост: <оригинал>».
    @SerializedName("copy_history")   val copyHistory: List<Post>? = null,
    @SerializedName("likes")          val likes: Likes? = null,
    @SerializedName("reposts")        val reposts: Reposts? = null,
    @SerializedName("views")          val views: Views? = null,
    @SerializedName("comments")       val comments: Comments? = null,
    @SerializedName("post_type")      val postType: String? = null,
    @SerializedName("marked_as_ads")  val markedAsAds: Int = 0,
    @SerializedName("is_pinned")      val isPinned: Int = 0,
    // --- SOVA_2_lenta: новые поля Post ---
    @SerializedName("is_favorite")    val isFavorite: Boolean? = null,
    @SerializedName("can_edit")       val canEdit: Boolean? = null,
    @SerializedName("can_delete")     val canDelete: Boolean? = null,
    @SerializedName("can_pin")        val canPin: Boolean? = null,
    @SerializedName("edited")         val edited: Long? = null,
    @SerializedName("is_archived")    val isArchived: Boolean? = null,
    @SerializedName("copyright")      val copyright: Copyright? = null,
    @SerializedName("donut")          val donut: Donut? = null,
    @SerializedName("reactions")      val reactions: Reactions? = null,
    @SerializedName("hash")           val hash: String? = null,
    @SerializedName("friends_only")   val friendsOnly: Boolean? = null,
    @SerializedName("created_by")     val createdBy: Long? = null,
    @SerializedName("postponed_id")   val postponedId: Long? = null,
    @SerializedName("access_key")     val accessKey: String? = null,
) {
    data class Likes(
        @SerializedName("count")        val count: Int,
        @SerializedName("user_likes")   val userLikes: Int = 0,
        @SerializedName("can_like")     val canLike: Int = 1,
    )
    data class Reposts(
        @SerializedName("count")          val count: Int,
        @SerializedName("user_reposted")  val userReposted: Int = 0,
    )
    data class Views(@SerializedName("count") val count: Int)
    data class Comments(
        @SerializedName("count")  val count: Int,
        @SerializedName("can_post") val canPost: Int = 1,
    )
    /** Копирайт-источник (ссылка, имя, тип). */
    data class Copyright(
        @SerializedName("id")    val id: Int? = null,
        @SerializedName("link")  val link: String? = null,
        @SerializedName("name")  val name: String? = null,
        @SerializedName("type")  val type: String? = null,
    )
    /** Donut — платный контент. */
    data class Donut(
        @SerializedName("is_donut")             val isDonut: Boolean? = null,
        @SerializedName("paid_duration")        val paidDuration: Int? = null,
        @SerializedName("placeholder")         val placeholder: String? = null,
        @SerializedName("can_publish_free_copy") val canPublishFreeCopy: Boolean? = null,
        @SerializedName("edit_mode")            val editMode: String? = null,
    )
    /** Реакции (эмодзи-реакции на пост). */
    data class Reactions(
        @SerializedName("count")       val count: Int? = null,
        @SerializedName("user_reacted") val userReacted: Int? = null,
    )

    val isAd: Boolean get() = markedAsAds == 1
    val isRepost: Boolean get() = ownerId != fromId
    val isPinnedBool: Boolean get() = isPinned == 1
    val isFavoriteBool: Boolean get() = isFavorite == true
    val canEditBool: Boolean get() = canEdit == true
    val canDeleteBool: Boolean get() = canDelete == true
    val canPinBool: Boolean get() = canPin == true
    val isEdited: Boolean get() = edited != null
    val isArchivedBool: Boolean get() = isArchived == true
    val isDonut: Boolean get() = donut?.isDonut == true
}

data class Attachment(
    @SerializedName("type")   val type: String,
    @SerializedName("photo")  val photo: Photo? = null,
    @SerializedName("video")  val video: Video? = null,
    @SerializedName("audio")  val audio: Track? = null,
    @SerializedName("link")   val link: Link? = null,
    @SerializedName("doc")    val doc: Doc? = null,
    // Fix #114: голосовые сообщения приходят как type="audio_message" с полем
    // audio_message (НЕ как doc с audio_msg). Без этого поля voice-сообщения
    // из messages.getHistory десериализуются в пустой Attachment и не рендерятся.
    @SerializedName("audio_message") val audioMessage: Doc.AudioMsg? = null,
    // Fix #99: wall-вложение в сообщениях (репост поста в ЛС).
    @SerializedName("wall")   val wall: Post? = null,
    // Sprint 3 #13: стикеры в сообщениях.
    @SerializedName("sticker") val sticker: StickerAttachment? = null,
    // Sprint 4: опросы в постах.
    @SerializedName("poll") val poll: Poll? = null,
    // #30 (playlists): audio_playlist как вложение поста.
    @SerializedName("audio_playlist") val audioPlaylist: AudioPlaylist? = null,
) {
    data class Photo(
        @SerializedName("id")       val id: Long,
        @SerializedName("owner_id") val ownerId: Long,
        @SerializedName("sizes")    val sizes: List<Size>? = null,
        @SerializedName("text")     val text: String? = null,
    ) {
        data class Size(
            @SerializedName("url")    val url: String,
            @SerializedName("width")  val width: Int,
            @SerializedName("height") val height: Int,
            @SerializedName("type")   val type: String,
        )
        val largestUrl: String? get() = PhotoSizes.bestUrl(sizes)

        /**
         * Fix #227: наибольший размер фото (по площади). Null если sizes пуст.
         * Используется для определения размеров при рендере стикер-фото.
         */
        val largestSize: Size? get() = PhotoSizes.best(sizes)

        /**
         * Fix #227: детект стикер-фото (отправленного как картинка через
         * messagesSendStickerAsImage). Стикеры квадратные и небольшие (≤512px),
         * обычные фото — прямоугольные и крупнее. Эвристика:
         *   - есть sizes
         *   - максимальный размер квадратный (|w-h| ≤ 16px)
         *   - максимальная сторона ≤ 512px
         * Используется в ChatDetailScreen для рендера в исходном размере
         * (не растягивая на всю ширину бабла как обычные фото).
         */
        val isStickerLike: Boolean get() {
            val s = largestSize ?: return false
            return abs(s.width - s.height) <= 16 && s.width <= 512 && s.height <= 512
        }
    }

    data class Link(
        @SerializedName("url")    val url: String,
        @SerializedName("title")  val title: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("photo")  val photo: Photo? = null,
    )

    data class Doc(
        @SerializedName("id")       val id: Long,
        @SerializedName("owner_id") val ownerId: Long,
        @SerializedName("title")    val title: String,
        @SerializedName("ext")      val ext: String,
        @SerializedName("url")      val url: String,
        @SerializedName("size")     val size: Long,
        @SerializedName("access_key") val accessKey: String? = null,
        @SerializedName("audio_msg")  val audioMsg: AudioMsg? = null,
    ) {
        data class AudioMsg(
            @SerializedName("duration")  val duration: Int = 0,
            @SerializedName("link_ogg")  val linkOgg: String? = null,
            @SerializedName("link_mp3")  val linkMp3: String? = null,
            @SerializedName("waveform")  val waveform: List<Int>? = null,
        )

        val isVoiceMessage: Boolean get() = audioMsg != null
    }
}

/**
 * OK-IMPL-1 (Stage 1): типизированная платформа видео.
 *
 * Определяется в [Video.detectPlatform] на основе формы `files`/`player`:
 *  - VK                — есть `files` (mp4_*, hls, dash от VK API).
 *  - OK                — `player` указывает на `ok.ru/videoembed/<id>` или `ok.ru/video/<id>`.
 *  - YOUTUBE           — `player` указывает на `youtube.com/embed/<id>` или `youtu.be/<id>`.
 *  - EXTERNAL_IFRAME   — `player` ведёт на другой iframe-домен (Rutube/Vimeo/...).
 *  - UNKNOWN           — нет ни files, ни player URL (видео недоступно или требует video.get).
 *
 * См. OK_VIDEO_PLAN.md Этап 1, OK_PLAYER_REVERSE.md §"Cross-platform / external video support".
 */
enum class VideoPlatform { VK, OK, YOUTUBE, INSTAGRAM, EXTERNAL_IFRAME, UNKNOWN }

data class Video(
    @SerializedName("id")          val id: Long,
    @SerializedName("owner_id")    val ownerId: Long,
    @SerializedName("title")       val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("duration")    val duration: Int,
    @SerializedName("date")        val date: Long,
    @SerializedName("views")       val views: Int = 0,
    @SerializedName("image")       val image: List<Thumb>? = null,
    // §37.12 #FIRST-FRAME: вертикальные кадры клипа (1080x1920 и ниже) —
    // используются веб-плеером как постер (VideoPoster__poster). Лучше covers[],
    // которые горизонтальные (16:9) и для вертикального клипа дают неверный crop.
    @SerializedName("first_frames") val firstFrames: List<Thumb>? = null,
    // #WALL-CLIPS: высота/ширина кадра. У wall-клипов height=1920 > width — по ним
    // определяем вертикальность (isClip), т.к. в wall.get нет is_clips/type="clip".
    @SerializedName("height")      val height: Int = 0,
    @SerializedName("width")       val width: Int = 0,
    @SerializedName("player")      val player: String? = null,
    @SerializedName("files")       val files: Map<String, String>? = null,
    // Fix #69: access_key нужен для повторного запроса video.get?videos=owner_id_video_id_access_key,
    // если первичный files оказался пустым или устарел (URL'ы короткоживущие, ~часы).
    @SerializedName("access_key")  val accessKey: String? = null,
    // Sprint 2, P1-2 (#89): лайки на видео. VK возвращает likes{count,user_likes}
    // при video.get и в attachments ленты (extended=1).
    @SerializedName("likes")       val likes: Post.Likes? = null,
    // §37.12 Phase 1: Clips fields — добавлены для clip-плеера.
    @SerializedName("reposts")     val reposts: Reposts? = null,
    @SerializedName("comments")    val comments: Comments? = null,
    @SerializedName("can_like")        val canLike: Int? = null,
    @SerializedName("can_comment")     val canComment: Int? = null,
    @SerializedName("can_repost")      val canRepost: Int? = null,
    @SerializedName("can_subscribe")   val canSubscribe: Int? = null,
    @SerializedName("can_edit")        val canEdit: Int? = null,
    @SerializedName("can_delete")      val canDelete: Int? = null,
    @SerializedName("can_add")         val canAdd: Int? = null,
    @SerializedName("can_report")      val canReport: Int? = null,
    @SerializedName("is_favorite")     val isFavorite: Int? = null,
    @SerializedName("is_subscribed")   val isSubscribed: Int? = null,
    @SerializedName("is_private")      val isPrivate: Int? = null,
    @SerializedName("is_limited")      val isLimited: Int? = null,
    @SerializedName("is_promoted")     val isPromoted: Int? = null,
    @SerializedName("is_ad")           val isAd: Int? = null,
    // is_clips=1 → это clip (короткое вертикальное видео). Также duration<=60.
    @SerializedName("is_clips")        val isClips: Int? = null,
    @SerializedName("is_live")         val isLive: Int? = null,
    @SerializedName("is_upcoming")     val isUpcoming: Int? = null,
    @SerializedName("repeat")          val repeat: Int? = null,
    @SerializedName("mute")            val mute: Int? = null,
    @SerializedName("no_sound")        val noSound: Int? = null,
    @SerializedName("track_code")      val trackCode: String? = null,
    @SerializedName("type")            val type: String? = null,
    @SerializedName("platform")        val platform: String? = null,
    @SerializedName("added")           val added: Int? = null,
    @SerializedName("completely_loaded") val completelyLoaded: Int? = null,
    // Музыка клипа (audio-трек, играющий в фоне).
    @SerializedName("music_info")      val musicInfo: ClipMusic? = null,
    // Соседние клипы в ленте (для vertical pager).
    @SerializedName("nearest_clips")   val nearestClips: List<Video>? = null,
    @SerializedName("next_clip")       val nextClip: Video? = null,
    @SerializedName("prev_clip")       val prevClip: Video? = null,
    // Связанная story (если клип создан из story).
    @SerializedName("story_id")        val storyId: Long? = null,
    // Оригинал (если это репост).
    @SerializedName("original")        val original: Video? = null,
    // ── OK-IMPL-1 (Stage 1): типизированная платформа видео для VideoPlatformRouter.
    // НЕ имеет @SerializedName: это локально-вычисляемое поле (см. [detectPlatform]).
    // Существующий `platform: String?` выше — это СЫРОЙ VK API platform-тег
    // ("android"/"iphone"/"web"/...), его НЕ трогаем. videoPlatform вычисляется
    // в VKApiClient.videoGetById/videoGet через detectPlatform() на основе формы
    // `files` и URL в `player`.
    val videoPlatform: VideoPlatform = VideoPlatform.UNKNOWN,
    // OK movieId (например "16108201904696") или YouTube videoId — извлекается
    // из `player` URL регуляркой в detectPlatform(). null для VK-native видео.
    val externalId: String? = null,
) {
    data class Thumb(
        @SerializedName("url")    val url: String,
        @SerializedName("width")  val width: Int,
        @SerializedName("height") val height: Int,
    )

    /** Репосты клипа (для счётчика share). */
    data class Reposts(
        @SerializedName("count")         val count: Int = 0,
        @SerializedName("user_reposted") val userReposted: Int = 0,
    )

    /** Комментарии клипа (для счётчика и can_post). */
    data class Comments(
        @SerializedName("count")    val count: Int = 0,
        @SerializedName("can_post") val canPost: Int = 0,
    )

    /** Музыка клипа (audio-трек из music_info). */
    data class ClipMusic(
        @SerializedName("id")          val id: Long = 0,
        @SerializedName("owner_id")    val ownerId: Long = 0,
        @SerializedName("artist")      val artist: String = "",
        @SerializedName("title")       val title: String = "",
        @SerializedName("duration")    val duration: Int = 0,
        @SerializedName("url")         val url: String? = null,
        @SerializedName("is_explicit") val isExplicit: Int? = null,
    )

    val thumbUrl: String? get() = image?.maxByOrNull { it.width * it.height }?.url
    // §37.12 #FIRST-FRAME: вертикальный постер клипа (max по площади из first_frames).
    // Fallback на thumbUrl (covers) если first_frames отсутствуют (старые ответы).
    val clipPosterUrl: String? get() =
        firstFrames?.maxByOrNull { it.width * it.height }?.url ?: thumbUrl
    val likesCount: Int get() = likes?.count ?: 0
    val isLiked: Boolean get() = likes?.userLikes == 1
    // §37.12: clips-удобные геттеры.
    // #WALL-CLIPS: wall.get НЕ отдаёт is_clips/type="clip" — клип определяется
    // по вертикальности кадра (height > width) и короткой длительности.
    val isClip: Boolean get() = isClips == 1 ||
        (duration in 1..60 && (type == "clip" || (height > 0 && width > 0 && height > width)))
    val isLiveClip: Boolean get() = isLive == 1
    val isMuted: Boolean get() = mute == 1 || noSound == 1
    val repostsCount: Int get() = reposts?.count ?: 0
    val commentsCount: Int get() = comments?.count ?: 0
    val isSubscribedToAuthor: Boolean get() = isSubscribed == 1
    val isFavorited: Boolean get() = isFavorite == 1
    val canReportClip: Boolean get() = canReport == 1
    val canLikeClip: Boolean get() = canLike != 0
    val canCommentClip: Boolean get() = canComment != 0
    val canShareClip: Boolean get() = canRepost != 0
    // §37.9: canEdit/canDelete для контекстного меню «Редактировать»/«Удалить».
    val canEditClip: Boolean get() = canEdit == 1
    val canDeleteClip: Boolean get() = canDelete == 1
    /** Лучший URL для прямого воспроизведения: mp4_720 → mp4_480 → mp4_360 → mp4_240 → hls → player. */
    val bestPlayUrl: String? get() {
        files?.let { f ->
            return f["mp4_1080"] ?: f["mp4_720"] ?: f["mp4_480"] ?: f["mp4_360"] ?: f["mp4_240"] ?: f["hls"] ?: f["mp4_orig"]
        }
        return player
    }

    /**
     * Fix #334: URL клипа с учётом предпочтительного качества пользователя.
     *
     * [preferredQuality] — значение из SovaPrefs.videoPreferredQuality:
     *  - "auto" / пусто → делегирует в [bestPlayUrl] (максимальное доступное).
     *  - "1080" / "720" / ... → ищет точное совпадение, потом ближайшее ≤ preferred,
     *    потом fallback на [bestPlayUrl] (если все доступные выше preferred — берём
     *    минимальное из них, чтобы не гонять 4K когда пользователь просит 240p).
     *
     * Порядок качества (high→low): mp4_2160, mp4_1440, mp4_1080, mp4_720,
     * mp4_480, mp4_360, mp4_240, mp4_144. Клипы обычно имеют только 720/480/360/240.
     */
    fun playUrlForQuality(preferredQuality: String): String? {
        if (preferredQuality.isBlank() || preferredQuality == "auto") return bestPlayUrl
        val f = files ?: return player
        // Точное совпадение
        f["mp4_$preferredQuality"]?.let { return it }
        // Ближайшее ≤ preferred (перебор от высокого к низкому)
        val order = VideoQuality.KEYS
        val prefInt = preferredQuality.toIntOrNull() ?: return bestPlayUrl
        // Первое (максимальное) качество ≤ preferred, которое есть в files
        for (key in order) {
            val q = key.substringAfter("_").toIntOrNull() ?: continue
            if (q <= prefInt && f[key] != null) return f[key]
        }
        // Все доступные выше preferred → берём минимальное доступное из order
        for (i in order.indices.reversed()) {
            f[order[i]]?.let { return it }
        }
        return bestPlayUrl
    }

    /**
     * OK-IMPL-1 (Stage 1) + FEED-FIX-2 (#347) + Fix #142 (2026-08-03):
     * определяет типизированную платформу видео.
     *
     * Алгоритм (см. OK_VIDEO_PLAN.md §1):
     *  1. `player` содержит `ok.ru/videoembed/` или `ok.ru/video/` →
     *     [VideoPlatform.OK], movieId извлекается регуляркой
     *     `ok\.ru/(?:videoembed|video)/(\d+)`.
     *  2. `player` содержит `youtube.com/embed/` или `youtu.be/` →
     *     [VideoPlatform.YOUTUBE], videoId извлекается.
     *  3. Fix #142: `player` содержит `instagram.com/reel/` или `/p/` или
     *     `instagr.am/...` → [VideoPlatform.INSTAGRAM], shortcode извлекается.
     *  4. `player` содержит другой iframe-домен (есть scheme + path) →
     *     [VideoPlatform.EXTERNAL_IFRAME].
     *  5. `files != null && files.isNotEmpty()` → [VideoPlatform.VK] (VK API
     *     всегда отдаёт прямые mp4/hls/dash URL для VK-native видео).
     *  6. Иначе → [VideoPlatform.UNKNOWN] (видео недоступно или требует video.get).
     *
     * FEED-FIX-2 (#347): порядок проверок изменён — `player` URL проверяется
     * ПЕРЕД `files`. Раньше `files` проверялся первым, и OK-crossposted видео
     * (у которых VK возвращает `files` с embed-URL как placeholder) ошибочно
     * определялись как VK. ExoPlayer потом пытался играть HTML-страницу
     * ok.ru/videoembed/... → `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`.
     * Теперь player URL приоритетнее: если это ok.ru/youtube/instagram/external
     * iframe — платформа соответствующая, независимо от того что VK положил в files.
     *
     * Fix #142: Instagram добавлен ОТДЕЛЬНО от EXTERNAL_IFRAME, потому что:
     *  - Instagram блокирует iframe-встраивание (X-Frame-Options: SAMEORIGIN).
     *    OkWebViewPlayer generic iframe → пустой экран.
     *  - Нужен специальный embed URL: `https://www.instagram.com/reel/<id>/embed`
     *    (Instagram имеет публичный embed endpoint, который НЕ блокирует iframe).
     *  - Также можно извлечь прямой mp4 через `?__a=1&__d=dis` JSON endpoint
     *    (но это ломается каждые несколько месяцев — Instagram меняет схему).
     *    Поэтому используем /embed/ как стабильный fallback.
     *
     * Возвращает саму [VideoPlatform]; ID платформы (movieId/videoId/shortcode)
     * доступен через [extractExternalId].
     *
     * Потокобезопасно: чистая функция без состояния.
     */
    fun detectPlatform(): VideoPlatform {
        // FEED-FIX-2 (#347): player URL проверяется ПЕРВЫМ. OK-crossposted
        // видео имеют player=ok.ru/videoembed/... И files (VK кладёт embed URL
        // как placeholder). Если files проверять первым — такие видео ошибочно
        // уйдут в VK path → ExoPlayer падает на HTML-странице.
        val p = player
        if (p != null) {
            val lower = p.lowercase()
            when {
                OK_MOVIE_ID_REGEX.containsMatchIn(lower) -> return VideoPlatform.OK
                YOUTUBE_ID_REGEX.containsMatchIn(lower) -> return VideoPlatform.YOUTUBE
                // Fix #142: Instagram — до generic EXTERNAL_IFRAME, иначе
                // instagram.com/reel/... уйдёт в EXTERNAL_IFRAME и embed не сработает.
                INSTAGRAM_ID_REGEX.containsMatchIn(lower) -> return VideoPlatform.INSTAGRAM
                p.startsWith("http://") || p.startsWith("https://") -> {
                    // #OK-NATIVE-FIX: OK-crosspost теперь имеет player=vk.ru/video_ext.php
                    // (НЕ ok.ru/videoembed). Если VK при этом отдал РЕАЛЬНЫЕ прямые URL
                    // в files (mp4_*/hls/dash с OK CDN) — ExoPlayer может играть нативно
                    // (фрейм + плеер + PiP). Иначе (files пуст/embed-плейсхолдер) —
                    // EXTERNAL_IFRAME (WebView), как раньше.
                    return if (hasPlayableFiles()) VideoPlatform.VK else VideoPlatform.EXTERNAL_IFRAME
                }
            }
        }
        if (!files.isNullOrEmpty()) return VideoPlatform.VK
        return VideoPlatform.UNKNOWN
    }

    /**
     * #OK-NATIVE-FIX: есть ли в files реальные playable ключи (mp4-качества,
     * hls и dash), а не embed-плейсхолдер. ExoPlayer умеет их играть нативно.
     */
    fun hasPlayableFiles(): Boolean {
        val f = files ?: return false
        return f.keys.any { key ->
            key.startsWith("mp4_") ||
                key == "hls" || key == "hls_ondemand" ||
                key == "dash" || key == "dash_sep" || key == "dash_webm" ||
                key == "hls_fmp4"
        }
    }

    /**
     * OK-IMPL-1 (Stage 1) + Fix #142: извлекает movieId (OK), videoId (YouTube)
     * или shortcode (Instagram) из `player` URL. null для VK/EXTERNAL_IFRAME/UNKNOWN.
     *
     * OK regex: `ok\.ru/(?:videoembed|video)/(\d+)` — MovieId — это длинное
     * число (например 16108201904696). VK cross-postит OK-видео именно так.
     *
     * YouTube: `youtube.com/embed/<id>` или `youtu.be/<id>` — id состоит из
     * [A-Za-z0-9_-]{11} (фиксированная длина 11 символов).
     *
     * Instagram (Fix #142): `instagram.com/reel/<shortcode>/` или `/p/<shortcode>/`
     * или `instagr.am/reel/<shortcode>/`. Shortcode — base64url-подобная строка.
     */
    fun extractExternalId(): String? {
        val p = player
        if (p == null) return null
        val lower = p.lowercase()
        // NULLSAFE-1: replaced ?.let { return ... } with explicit null check + local val
        val okMatch = OK_MOVIE_ID_REGEX.find(lower)
        if (okMatch != null) return okMatch.groupValues[1]
        val ytMatch = YOUTUBE_ID_REGEX.find(lower)
        if (ytMatch != null) return ytMatch.groupValues[1]
        // Fix #142: Instagram shortcode.
        val igMatch = INSTAGRAM_ID_REGEX.find(lower)
        if (igMatch != null) return igMatch.groupValues[1]
        return null
    }

    /**
     * OK-IMPL-1 (Stage 1): возвращает копию Video с заполненными [videoPlatform]
     * и [externalId] на основе [detectPlatform]/[extractExternalId].
     *
     * Если платформа уже определена (не UNKNOWN) — возвращает `this` без копирования.
     * Если платформа UNKNOWN и externalId==null — тоже возвращает `this` без копирования
     * (нет смысла плодить copy() для VK-native видео, у которых videoPlatform
     * уже выставлен через default UNKNOWN, и для UNKNOWN fallback остаётся UNKNOWN).
     *
     * Безопасна для повторного вызова — idempotent.
     */
    fun withDetectedPlatform(): Video {
        if (videoPlatform != VideoPlatform.UNKNOWN || externalId != null) return this
        val p = detectPlatform()
        val id = extractExternalId()
        if (p == VideoPlatform.UNKNOWN && id == null) return this
        return copy(videoPlatform = p, externalId = id)
    }

    companion object {
        // Регулярки детекции платформы. kotlin.text.Regex — потокобезопасный.
        // ok.ru/videoembed/16108201904696 или ok.ru/video/16108201904696.
        internal val OK_MOVIE_ID_REGEX = Regex("""ok\.ru/(?:videoembed|video)/(\d+)""")
        // youtube.com/embed/dQw4w9WgXcQ или youtu.be/dQw4w9WgXcQ.
        internal val YOUTUBE_ID_REGEX = Regex("""(?:youtube\.com/embed/|youtu\.be/)([A-Za-z0-9_-]{11})""")
        // Fix #142 (2026-08-03): Instagram reel/post URL.
        // instagram.com/reel/CxYz1234567/ или instagram.com/p/CxYz1234567/.
        // Shortcode: [A-Za-z0-9_-] (11 chars типично, но может быть длиннее).
        // Также ловим instagr.am (короткий домен).
        internal val INSTAGRAM_ID_REGEX = Regex("""(?:instagram\.com|instagr\.am)/(?:reel|p|tv)/([A-Za-z0-9_-]+)""")
    }
}

/**
 * #VIDEO-PORT: альбом видео (video.getAlbums).
 */
data class VideoAlbum(
    @SerializedName("id")           val id: Long,
    @SerializedName("owner_id")     val ownerId: Long,
    @SerializedName("title")        val title: String = "",
    @SerializedName("count")        val count: Int = 0,
    @SerializedName("plays")        val plays: Int = 0,
    @SerializedName("updated_time") val updatedTime: Long = 0,
    // photo_320 / photo_160 — обложки альбома.
    @SerializedName("photo_320")    val photo320: String? = null,
    @SerializedName("photo_160")    val photo160: String? = null,
    // image[] — массив обложек (новый формат video.getAlbums extended=1).
    @SerializedName("image")        val image: List<Video.Thumb>? = null,
) {
    val coverUrl: String?
        get() = image?.maxByOrNull { it.width * it.height }?.url ?: photo320 ?: photo160
}

/**
 * #VIDEO-PORT: раздел видео-каталога (video.getCatalog → response.sections[]).
 */
data class VideoCatalogSection(
    @SerializedName("id")          val id: String,
    @SerializedName("name")        val name: String = "",
    @SerializedName("url")         val url: String? = null,
    @SerializedName("is_selected") val isSelected: Int = 0,
)

data class Track(
    @SerializedName("id")         val id: Long,
    @SerializedName("owner_id")   val ownerId: Long,
    @SerializedName("artist")     val artist: String,
    @SerializedName("title")      val title: String,
    @SerializedName("duration")   val duration: Int,
    @SerializedName("url")        val url: String? = null,
    @SerializedName("album_id")   val albumId: Long? = null,
    @SerializedName("album_thumb") val albumThumb: String? = null,
    @SerializedName("access_key") val accessKey: String? = null,
    @SerializedName("lyrics_id")  val lyricsId: Long? = null,
    @SerializedName("main_artists") val mainArtists: List<TrackArtist>? = null,
    @SerializedName("is_explicit") val isExplicit: Boolean = false,
    @SerializedName("is_hq")     val isHq: Boolean = false,
    // #VK-MUSIC-SAVER-PORT: subtitle (remix/feat подпись из веб-VK) + genre_id.
    @SerializedName("subtitle")   val subtitle: String? = null,
    @SerializedName("genre_id")   val genreId: Int? = null,
) {
    val fullTitle: String get() = "$artist — $title"
    val hasLyrics: Boolean get() = lyricsId != null && lyricsId != 0L
}

/** Артист трека (из main_artists массива). */
data class TrackArtist(
    @SerializedName("id")       val id: Long = 0,
    @SerializedName("name")     val name: String = "",
    @SerializedName("domain")   val domain: String? = null,
    @SerializedName("photo")    val photo: String? = null,
)

/** Плейлист из audio.getPlaylists. */
data class AudioPlaylist(
    @SerializedName("id")            val id: Long = 0,
    @SerializedName("owner_id")      val ownerId: Long = 0,
    @SerializedName("title")         val title: String = "",
    @SerializedName("description")   val description: String? = null,
    @SerializedName("photo")         val photo: String? = null,
    @SerializedName("photo_200")     val photo200: String? = null,
    @SerializedName("photo_300")     val photo300: String? = null,
    @SerializedName("photo_600")     val photo600: String? = null,
    @SerializedName("count")         val count: Int = 0,
    @SerializedName("genre_id")      val genreId: Int? = null,
    @SerializedName("type")          val type: String? = null,
    @SerializedName("access_key")    val accessKey: String? = null,
    @SerializedName("followers")     val followers: Int = 0,
    @SerializedName("plays")         val plays: Int = 0,
) {
    val coverUrl: String? get() = photo600 ?: photo300 ?: photo200 ?: photo
}

data class Chat(
    @SerializedName("peer")    val peer: Peer,
    @SerializedName("last_message") val lastMessage: Message? = null,
    @SerializedName("in_read") val inRead: Long = 0,
    @SerializedName("out_read") val outRead: Long = 0,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("push_settings") val pushSettings: PushSettings? = null,
    // Fix #274: sort_id — сортировка диалога в списке. VK API отдаёт объект
    // {major_id, minor_id} в conversation.sort_id. major_id > 0 = закреплённый
    // диалог (вверху списка). Чем больше major_id, тем выше в закрепе.
    // minor_id = timestamp последнего сообщения (для сортировки внутри группы).
    // Источник: messages.getConversations, messages.getConversationsById.
    // См. VK_IMPORT_API.MD §35.1.3 — "sort_id": {"major_id": 16, "minor_id": 253391}.
    @SerializedName("sort_id") val sortId: SortId? = null,
    // Fix #274: important — признак «закреплённого» (important) диалога.
    // VK API отдаёт boolean поле в conversation.important. true = закреплён.
    // Дублирует информацию из sort_id.major_id > 0, но VK использует оба поля.
    @SerializedName("important") val important: Boolean? = null,
    // P0.3: pinned message (для group chats). VK отдаёт это поле в
    // messages.getConversationsById и messages.getConversations (extended=1).
    @SerializedName("pinned_message") val pinnedMessage: Message? = null,
    // P0.3: conversation_message_id для текущего сообщения в чате
    // (используется messages.pin/unpin с cmid вместо message_id).
    @SerializedName("current_conversation_message_id") val currentCmid: Long? = null,
    // P3.4: can_write — флаг возможности писать в диалог. Для каналов
    // (broadcast-сообщества) allowed=false, если пользователь не админ.
    // Источник: messages.getConversations / getConversationsById → conversation.can_write.
    @SerializedName("can_write") val canWrite: CanWrite? = null,
    // Fix #267 (Plan §36.12 P2-CHAT-1): ACL — права текущего пользователя в чате.
    // Источник: conversation.chat_settings.acl (только для group chats, VK API 5.x).
    // null для 1-1 диалогов и каналов (там ACL неприменим).
    @SerializedName("acl") val acl: ChatAcl? = null,
    // Fix #267 (Plan §36.12 P2-CHAT-1): permissions — кто может делать действия
    // ("all" | "owner" | "owner_and_admins"). Источник: chat_settings.permissions.
    @SerializedName("permissions") val permissions: ChatPermissions? = null,
    // Fix #269: description — описание group chat (chat_settings.description).
    // Источник: messages.getConversationsById → conversation.chat_settings.description.
    // null для 1-1 диалогов и чатов без описания. Используется в ChangeDescriptionDialog
    // для pre-fill (раньше был хардкод "" → пользователь не видел текущее описание).
    @SerializedName("description") val description: String? = null,
) {
    data class Peer(
        @SerializedName("id")    val id: Long,
        @SerializedName("type")  val type: String,
        @SerializedName("local_id") val localId: Long,
        @SerializedName("title") val title: String? = null,
        @SerializedName("photo") val photo: String? = null,
        // Fix #283: online-статус пользователя (только для type="user").
        // true = сейчас онлайн, false = офлайн, null = неизвестно (группы/
        // чаты/каналы, или статус не получен). Источник: messages.getConversations
        // → profiles[].online (extended=1). Без @SerializedName — поле НЕ
        // десериализуется из peer-объекта (там его нет), выставляется вручную
        // при парсинге profiles[] в VKApiClient.messagesGetConversations.
        val online: Boolean? = null,
    )
    data class PushSettings(
        @SerializedName("disabled_until") val disabledUntil: Long? = null,
        @SerializedName("disabled_forever") val disabledForever: Boolean? = null,
        @SerializedName("sound") val sound: Int? = null,
        // Fix #122: VK web возвращает также no_sound / disabled_mentions /
        // disabled_mass_mentions. no_sound=true означает «без звука» — тоже
        // считаем заглушённым для UI-индикатора (как в нативном VK).
        @SerializedName("no_sound") val noSound: Boolean? = null,
        @SerializedName("disabled_mentions") val disabledMentions: Boolean? = null,
        @SerializedName("disabled_mass_mentions") val disabledMassMentions: Boolean? = null,
    ) {
        /**
         * Fix #122 + Fix #273: единый «заглушён ли диалог» — true если push
         * полностью выключен (disabled_forever, ИЛИ disabled_until в будущем,
         * ИЛИ disabled_until == -1 что в VK API означает «навсегда»).
         *
         * Fix #273: ранее disabled_until == -1 НЕ считался заглушённым, потому
         * что проверка `disabledUntil > nowSec` давала false (-1 < nowSec).
         * Но VK API (см. VK_IMPORT_API.MD §35.1, §18.2) использует
         * disabled_until = -1 как «навсегда» → это заглушённое состояние.
         * Без этой правки toggle уведомлений мог «откатываться» даже после
         * успешного API-ответа, потому что сервер возвращал
         * {disabled_until: -1} без disabled_forever, isMuted() → false,
         * UI ставил muted=false вместо true.
         */
        fun isMuted(nowSec: Long = System.currentTimeMillis() / 1000): Boolean {
            if (disabledForever == true) return true
            // Fix #273: VK API использует disabled_until == -1 как «навсегда».
            if (disabledUntil == -1L) return true
            if (disabledUntil != null && disabledUntil > nowSec) return true
            return false
        }
    }

    /**
     * Fix #274: SortId — позиция диалога в списке (закрепление).
     *
     * VK API отдаёт в conversation.sort_id объект {major_id, minor_id}:
     *  - major_id > 0 → диалог закреплён («important»). Чем больше major_id,
     *    тем выше он в блоке закреплённых. VK инкрементирует major_id при
     *    каждом новом закреплении (16, 17, 18…), так что последний
     *    закреплённый диалог оказывается наверху.
     *  - major_id == 0 → обычный диалог, сортировка по minor_id
     *    (= timestamp последнего сообщения, DESC).
     *  - minor_id — для сортировки внутри группы (закреплённые между собой
     *    по major_id DESC, обычные между собой по minor_id DESC).
     *
     * См. VK_IMPORT_API.MD §35.1.3:
     *   "sort_id": {"major_id": 16, "minor_id": 253391}
     *
     * LongPoll event 20 (CONVO_MAJOR_ID_CHANGED) прилетает при закреплении/
     * откреплении — payload {peerId, majorId}.
     */
    data class SortId(
        @SerializedName("major_id") val majorId: Long = 0L,
        @SerializedName("minor_id") val minorId: Long = 0L,
    ) {
        /** true если диалог закреплён (major_id > 0 или important == true). */
        fun isPinned(): Boolean = majorId > 0L
    }

    /**
     * P3.4: can_write из VK API conversation object.
     * allowed=false → пользователь не может писать (канал / заблокирован / покинул).
     * reason: 18=канал (broadcast), другие коды — см. VK API docs.
     */
    data class CanWrite(
        @SerializedName("allowed") val allowed: Boolean = true,
        @SerializedName("reason") val reason: Int? = null,
    )

    /**
     * P3.4: является ли диалог каналом (broadcast-сообщество).
     * Канал = группа (peerId < 0) где пользователь не может писать.
     * Для каналов скрывается composer, показывается footer «Вы подписаны».
     */
    val isChannel: Boolean get() = peer.id < 0 && canWrite?.allowed == false
}

/**
 * Fix #267 (Plan §36.12 P2-CHAT-1): ACL — права текущего пользователя в group chat.
 *
 * Источник: messages.getConversationsById → conversation.chat_settings.acl
 * (VK API version 5.x, только для type="chat" пиров с peer_id >= 2_000_000_000).
 *
 * 14 базовых прав (can_*) + 3 admin-only (optional, null для обычных участников).
 * Используется в ChatInfoScreen для ACL-gating пунктов меню
 * (Изменить название, Закрепить, Передать права, и т.д.).
 *
 * @see <a href="https://vk.com/dev/objects/chat_settings">VK API: chat_settings</a>
 */
data class ChatAcl(
    @SerializedName("can_change_info")            val canChangeInfo: Boolean = false,
    @SerializedName("can_change_invite_link")     val canChangeInviteLink: Boolean = false,
    @SerializedName("can_change_pin")             val canChangePin: Boolean = false,
    @SerializedName("can_invite")                 val canInvite: Boolean = false,
    @SerializedName("can_promote_users")          val canPromoteUsers: Boolean = false,
    @SerializedName("can_see_invite_link")        val canSeeInviteLink: Boolean = false,
    @SerializedName("can_moderate")               val canModerate: Boolean = false,
    @SerializedName("can_copy_chat")              val canCopyChat: Boolean = false,
    @SerializedName("can_call")                   val canCall: Boolean = false,
    @SerializedName("can_use_mass_mentions")      val canUseMassMentions: Boolean = false,
    @SerializedName("can_change_style")           val canChangeStyle: Boolean = false,
    @SerializedName("can_send_reactions")         val canSendReactions: Boolean = false,
    @SerializedName("can_forward_messages")       val canForwardMessages: Boolean = false,
    @SerializedName("can_change_owner")           val canChangeOwner: Boolean = false,
    // admin-only fields (optional — присутствуют только у owner/admin):
    @SerializedName("can_change_stickers_popup_autoplay") val canChangeStickersPopupAutoplay: Boolean? = null,
    @SerializedName("can_disable_forward_messages") val canDisableForwardMessages: Boolean? = null,
    @SerializedName("can_disable_service_messages") val canDisableServiceMessages: Boolean? = null,
)

/**
 * Fix #267 (Plan §36.12 P2-CHAT-1): Permissions — кто может делать действия в чате.
 *
 * Источник: conversation.chat_settings.permissions.
 * Значения: "all" | "owner" | "owner_and_admins".
 * В отличие от ACL (что МОЖЕТ текущий пользователь), permissions — это НАСТРОЙКИ
 * чата (кто вообще может делать действие, независимо от конкретного пользователя).
 */
data class ChatPermissions(
    @SerializedName("invite")            val invite: String? = null,
    @SerializedName("change_info")       val changeInfo: String? = null,
    @SerializedName("change_pin")        val changePin: String? = null,
    @SerializedName("use_mass_mentions") val useMassMentions: String? = null,
    @SerializedName("see_invite_link")   val seeInviteLink: String? = null,
    @SerializedName("call")              val call: String? = null,
    @SerializedName("change_admins")     val changeAdmins: String? = null,
    @SerializedName("change_style")      val changeStyle: String? = null,
)

/**
 * P3.3: Папка диалогов — пользовательская группировка чатов.
 *
 * VK API (messages.getChatFolders) возвращает folder_id + title + список peer_ids.
 * Метод недокументирован и может быть недоступен → папки хранятся также клиентски
 * в SovaPrefs (JSON). UI работает с локальной копией; API — best-effort синхронизация.
 *
 * Аналог m.vk.ru: экран «Папки с чатами» (tid="me_folder_settings_item_*").
 * Вкладки в MessagesScreen: «Все» + папки + «Непрочитанные» + gear.
 *
 * @param id folder_id (с сервера) или локальный (System.currentTimeMillis()).
 * @param title Название папки («Каналы», «Работа», etc.).
 * @param peerIds Множество peer_id чатов, входящих в папку.
 * @param iconEmoji Опциональная иконка-эмодзи (для будущего).
 */
data class ChatFolder(
    val id: Long,
    val title: String,
    val peerIds: Set<Long> = emptySet(),
    val iconEmoji: String? = null,
)

data class Message(
    @SerializedName("id")        val id: Long,
    @SerializedName("peer_id")   val peerId: Long,
    @SerializedName("from_id")   val fromId: Long,
    @SerializedName("date")      val date: Long,
    @SerializedName("text")      val text: String,
    @SerializedName("out")       val out: Int = 0,
    @SerializedName("read_state") val readState: Int = 0,
    @SerializedName("deleted")   val deleted: Int = 0,
    @SerializedName("edited")    val edited: Int = 0,
    @SerializedName("edit_time") val editTime: Long? = null,
    @SerializedName("original_text") val originalText: String? = null,
    @SerializedName("attachments") val attachments: List<Attachment>? = null,
    @SerializedName("reactions")  val reactions: MessageReaction? = null,
    // #60: reply + forwarded messages
    @SerializedName("reply_message") val replyMessage: Message? = null,
    @SerializedName("fwd_messages") val fwdMessages: List<Message>? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("action_text") val actionText: String? = null,
    @SerializedName("conversation_message_id") val conversationMessageId: Long? = null,
    @SerializedName("keyboard") val keyboard: Any? = null,
) {
    val isOut: Boolean get() = out == 1
    val isRead: Boolean get() = readState == 1
    val isDeleted: Boolean get() = deleted == 1
    val isEdited: Boolean get() = edited == 1
    val isAction: Boolean get() = action != null
    val hasReply: Boolean get() = replyMessage != null
    val hasForwarded: Boolean get() = !fwdMessages.isNullOrEmpty()
}

/**
 * Реакции на сообщение (messages.react, VK API 5.243).
 * Парсится из поля "reactions" в объекте сообщения.
 */
data class MessageReaction(
    @SerializedName("count")          val count: Int = 0,
    @SerializedName("user_reaction")  val userReaction: Int? = null,
    @SerializedName("recent_reactions") val recentReactions: List<RecentReaction>? = null,
) {
    val hasUserReaction: Boolean get() = userReaction != null && userReaction != 0
}

data class RecentReaction(
    @SerializedName("user_id")     val userId: Long,
    @SerializedName("reaction_id") val reactionId: Int,
)

// ============================================================================
//  #43: Доп. сущности для полнофункциональной соцсети — комментарии к постам
//  и заявки в друзья. Comment зеркалирует wall.getComments ответ VK API 5.243.
// ============================================================================

/**
 * Комментарий к посту/фото/видео ВК (wall.getComments).
 */
data class Comment(
    @SerializedName("id")        val id: Long,
    @SerializedName("from_id")   val fromId: Long,
    @SerializedName("date")      val date: Long,
    @SerializedName("text")      val text: String,
    @SerializedName("likes")     val likes: Post.Likes? = null,
    @SerializedName("reply_to_user") val replyToUser: Long? = null,
    @SerializedName("reply_to_comment") val replyToComment: Long? = null,
    @SerializedName("attachments") val attachments: List<Attachment>? = null,
    // Fix #234: цепочка ID всех предков в ветке ответов (для глубоких тредов).
    // VK отдаёт это поле только при thread_items_count>0, иначе null.
    @SerializedName("parents_stack") val parentsStack: List<Long>? = null,
    // Fix #234: превью ветки ответов (thread.items) — запрашивается явным
    // thread_items_count=N в wall.getComments. null = VK не вернул превью.
    @SerializedName("thread")    val thread: CommentThread? = null,
) {
    val likesCount: Int get() = likes?.count ?: 0
    val isLiked: Boolean get() = likes?.userLikes == 1

    /** Fix #234: короткая сводка ветки ответов под этим комментарием. */
    data class CommentThread(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("items") val items: List<Comment> = emptyList(),
        @SerializedName("can_post") val canPost: Boolean = false,
        @SerializedName("show_reply_button") val showReplyButton: Boolean = true,
    )
}

/**
 * #30h: Фото как самостоятельная сущность (photos.getById, photos.getAll).
 * Используется для просмотра альбомов и отдельных фото.
 */
data class PhotoStandalone(
    @SerializedName("id")        val id: Long,
    @SerializedName("owner_id")  val ownerId: Long,
    @SerializedName("album_id")  val albumId: Long = 0L,
    @SerializedName("text")      val text: String = "",
    @SerializedName("sizes")     val sizes: List<Attachment.Photo.Size> = emptyList(),
    @SerializedName("largestUrl") val largestUrl: String? = null,
) {
    val bestUrl: String? get() = largestUrl ?: sizes.maxByOrNull { it.width * it.height }?.url
}

/**
 * #30h: Тема обсуждения сообщества (board.getTopics).
 */
data class BoardTopic(
    @SerializedName("id")         val id: Long,
    @SerializedName("title")      val title: String,
    @SerializedName("created")    val created: Long,
    @SerializedName("created_by") val creatorId: Long,
    @SerializedName("comments")   val comments: Int,
    @SerializedName("is_closed")  val isClosed: Int = 0,
)

/**
 * #30h: Комментарий в теме обсуждения (board.getComments).
 */
data class BoardComment(
    @SerializedName("id")      val id: Long,
    @SerializedName("text")    val text: String,
    @SerializedName("date")    val created: Long,
    @SerializedName("from_id") val creatorId: Long,
)

@Suppress("unused")
data class EqualizerPreset(
    val name: String,
    val bands: List<Float>,  // gains per band in dB (0.5 шаг), 9 полос (60Hz..14kHz)
) {
    companion object {
        // #VK-MUSIC-PLAYER-PORT: 18 пресетов VKnext vmp (10 полос 31Hz..16kHz),
        // маппинг на 9 полос PinoK (60Hz..14kHz): отбрасываем самую низкую (31Hz),
        // остальные 9 соответствуют по порядку. Значения — в dB (полу-шаг).
        val DEFAULT = EqualizerPreset("По умолчанию", List(9) { 0f })
        val CLASSICAL = EqualizerPreset("Классика", listOf(-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -3.5f, -3.5f, -3.5f, -4.5f))
        val CLUB = EqualizerPreset("Клуб", listOf(-0.5f, 4f, 2.5f, 2.5f, 2.5f, 1.5f, -0.5f, -0.5f, -0.5f))
        val DANCE = EqualizerPreset("Танцевальная", listOf(3.5f, 1f, -0.5f, -0.5f, -2.5f, -3.5f, -3.5f, -0.5f, -0.5f))
        val BASS_BOOST = EqualizerPreset("Басы", listOf(4.5f, 4.5f, 2.5f, 0.5f, -2f, -4f, -5f, -5.5f, -5.5f))
        val BASS_AND_TREBLE = EqualizerPreset("Басы и высокие", listOf(2.5f, -0.5f, -3.5f, -2f, 0.5f, 4f, 5.5f, 6f, 6f))
        val TREBLE = EqualizerPreset("Высокие", listOf(-4.5f, -4.5f, -2f, 1f, 5.5f, 8f, 8f, 8f, 8f))
        val SPEAKERS = EqualizerPreset("Колонки", listOf(5.5f, 2.5f, -1.5f, -1f, 0.5f, 2f, 4.5f, 6f, 7f))
        val LARGE_HALL = EqualizerPreset("Большой зал", listOf(5f, 2.5f, 2.5f, -0.5f, -2f, -2f, -2f, -0.5f, -0.5f))
        val CONCERT = EqualizerPreset("Концерт", listOf(-0.5f, 2f, 2.5f, 2.5f, 2.5f, 2f, 1f, 1f, 1f))
        val PARTY = EqualizerPreset("Вечеринка", listOf(3.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 3.5f, 3.5f))
        val POP = EqualizerPreset("Поп", listOf(2f, 3.5f, 4f, 2.5f, -0.5f, -1f, -1f, -0.5f, -0.5f))
        val REGGAE = EqualizerPreset("Регги", listOf(-0.5f, -0.5f, -2.5f, -0.5f, 3f, 3f, -0.5f, -0.5f, -0.5f))
        val ROCK = EqualizerPreset("Рок", listOf(2f, -2.5f, -4f, -1.5f, 2f, 4f, 5.5f, 5.5f, 5.5f))
        val SKA = EqualizerPreset("Ска", listOf(-2f, -2f, -0.5f, 2f, 2.5f, 4f, 4.5f, 5.5f, 4.5f))
        val SOFT = EqualizerPreset("Мягкая", listOf(0.5f, -0.5f, -1f, -0.5f, 2f, 4f, 4.5f, 5.5f, 6f))
        val SOFT_ROCK = EqualizerPreset("Софт-рок", listOf(2f, 1f, -0.5f, -2f, -2.5f, -1.5f, -0.5f, 1f, 4f))
        val TECHNO = EqualizerPreset("Техно", listOf(2.5f, -0.5f, -2.5f, -2f, -0.5f, 4f, 4.5f, 4.5f, 4f))

        val ALL = listOf(
            DEFAULT, CLASSICAL, CLUB, DANCE, BASS_BOOST, BASS_AND_TREBLE, TREBLE,
            SPEAKERS, LARGE_HALL, CONCERT, PARTY, POP, REGGAE, ROCK, SKA, SOFT,
            SOFT_ROCK, TECHNO,
        )
    }
}

/** App log entry — for the in-app log viewer. */
@Suppress("unused")
data class LogEntry(
    val timestamp: Long,
    val level: Level,
    val tag: String,
    val message: String,
) {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }
}

/** Media player state used by the player UI. */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    /** Fix #59: сообщение об ошибке воспроизведения (null = нет ошибки).
     *  Сбрасывается при успешном старте нового трека. */
    val error: String? = null,
    /** Fix #62: режим перемешивания (SHUFFLE) и повторения (REPEAT_OFF/ONE/ALL). */
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = REPEAT_MODE_OFF,
    /** Скорость воспроизведения (0.25x – 3.0x). */
    val speed: Float = 1.0f,
) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
        /** Repeat one track 2 times total, then advance to next. */
        const val REPEAT_MODE_TWO = 3
    }
}

/** Статус скачивания (треки и видео). */
enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, REMOVING }

/**
 * Категория причины сбоя загрузки (#OFFLINE-STATUS-1).
 *
 * Разделяет «трек умер» (URL протух/удалён) от сетевых сбоев и проблем кодека —
 * чтобы UI мог показывать дохлые треки отдельно и пользователь понимал: повтор
 * имеет смысл только для NETWORK/DISK, а DEAD_URL — навсегда (пока VK не вернёт URL).
 *
 * - DEAD_URL: HTTP 403/404/410/451, "expired", "unavailable" — URL невалиден.
 * - NETWORK: HTTP 5xx, timeout, unknown host, SSL — повтор может помочь.
 * - CODEC: Siren/MediaCodec/MediaMuxer — нужен транскодер (P0 #2).
 * - DISK: IOException при записи — нет места / нет прав.
 * - UNKNOWN: всё прочее.
 */
enum class FailReason { DEAD_URL, NETWORK, CODEC, DISK, UNKNOWN }

/**
 * Полное состояние скачивания одного трека.
 *
 * @param trackId   ID трека (Long, т.к. VK API использует long).
 * @param status    Текущий статус.
 * @param progress  Прогресс в процентах (0..100). -1 = недоступно.
 * @param reason    Причина сбоя (короткое сообщение), только если status == FAILED.
 * @param failReason Категория сбоя (#OFFLINE-STATUS-1). null если не FAILED.
 * @param codec     Аудио-кодек кэша: "aac" (m4a), "mpegts" (.ts 0x47), "siren" (.ts non-0x47).
 *                  null если не COMPLETED. Siren-кэш валиден, но офлайн не играется —
 *                  стримится онлайн до внедрения транскодера (P0 #2).
 * @param deadSinceMs Timestamp (System.currentTimeMillis) когда трек был помечен
 *                  DEAD_URL (#DEAD-RECHECK). null если не dead. Используется для
 *                  авто-recheck: треки dead >1ч перепроверяются через audioGetById
 *                  (URL мог стать доступен — VK пере-выдал ссылку).
 */
data class DownloadState(
    val trackId: Long,
    val status: DownloadStatus,
    val progress: Int = 0,
    val reason: String? = null,
    val failReason: FailReason? = null,
    val codec: String? = null,
    val title: String = "",
    val artist: String = "",
    val ownerId: Long = 0,
    val deadSinceMs: Long? = null,
) {
    val isCompleted: Boolean get() = status == DownloadStatus.COMPLETED
    val isInProgress: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
    /** Трек «умер» — URL невалиден навсегда (не сетевой сбой). */
    val isDead: Boolean get() = status == DownloadStatus.FAILED && failReason == FailReason.DEAD_URL
    /** Siren-кэш: файл есть, но офлайн не играется (стримится онлайн). */
    val isSirenCache: Boolean get() = status == DownloadStatus.COMPLETED && codec == "siren"
    val displayText: String get() = "$artist — $title".trim(' ', '—')
}

// ============================================================================
//  Расширенные сущности ВК — полный функционал соцсети (#35)
//  Добавлено: Friend, Group (полная), Album, PhotoStandalone, Bookmark, Poll,
//  Story, Article, DocFile, SearchHint. Каждая модель зеркалирует VK API 5.243
//  и содержит только поля, реально используемые UI.
// ============================================================================

/**
 * Расширенная модель друга/пользователя в списке friends.get.
 * Содержит доп. поля по сравнению с [UserProfile]: bdate, sex, relation, career.
 */
data class Friend(
    @SerializedName("id")              val id: Long,
    @SerializedName("first_name")      val firstName: String,
    @SerializedName("last_name")       val lastName: String,
    @SerializedName("photo_100")       val photo100: String? = null,
    @SerializedName("photo_200")       val photo200: String? = null,
    @SerializedName("online")          val online: Int = 0,
    @SerializedName("last_seen")       val lastSeen: UserProfile.LastSeen? = null,
    @SerializedName("status")          val status: String? = null,
    @SerializedName("bdate")           val bdate: String? = null,
    @SerializedName("sex")             val sex: Int = 0,           // 1=жен, 2=муж
    @SerializedName("city")            val city: UserProfile.City? = null,
    @SerializedName("verified")        val verified: Int = 0,
    @SerializedName("online_app")      val onlineApp: Int? = null,
    @SerializedName("online_mobile")   val onlineMobile: Int? = null,
) {
    val fullName: String get() = "$firstName $lastName"
    val isOnline: Boolean get() = online == 1
    val sexLabel: String get() = when (sex) { 1 -> "жен"; 2 -> "муж"; else -> "" }
}

/**
 * Полная модель сообщества ВК. [VKApiClient.GroupInfo] — упрощённая версия
 * только для ленты (id+name+photo). Эта — для полноценного экрана сообществ.
 */
data class Group(
    @SerializedName("id")              val id: Long,
    @SerializedName("name")            val name: String,
    @SerializedName("screen_name")     val screenName: String? = null,
    @SerializedName("is_closed")       val isClosed: Int = 0,
    @SerializedName("type")            val type: String? = null,   // group, page, event
    @SerializedName("photo_100")       val photo100: String? = null,
    @SerializedName("photo_200")       val photo200: String? = null,
    @SerializedName("members_count")   val membersCount: Int = 0,
    @SerializedName("description")     val description: String? = null,
    @SerializedName("status")          val status: String? = null,
    @SerializedName("verified")        val verified: Int = 0,
    @SerializedName("is_member")       val isMember: Int = 0,
    @SerializedName("can_post")        val canPost: Int = 0,
    @SerializedName("can_see_all_posts") val canSeeAllPosts: Int = 1,
    @SerializedName("activity")        val activity: String? = null,
    @SerializedName("site")            val site: String? = null,
    // Fix #144: admin_level — уровень доступа пользователя в сообществе.
    //   0 = не админ (по умолчанию для groups.get без filter),
    //   1 = moderator,
    //   2 = editor,
    //   3 = administrator.
    // Используется в ForwardDialog/CreatePostDialog для фильтра «сообщества
    // где я админ» (groups.get?filter=admin_editor возвращает только те,
    // где admin_level >= 2).
    @SerializedName("admin_level")     val adminLevel: Int = 0,
) {
    val isMemberBool: Boolean get() = isMember == 1
    // Fix #144: true если пользователь имеет права editor/admin (can_post в чужую стену).
    val isAdmin: Boolean get() = adminLevel >= 2
    val typeLabel: String get() = when (type) {
        "page" -> "Публичная страница"
        "event" -> "Мероприятие"
        else -> "Группа"
    }
}

/**
 * Фотоальбом ВК.
 */
data class Album(
    @SerializedName("id")              val id: Long,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("title")           val title: String,
    @SerializedName("size")            val size: Int = 0,
    @SerializedName("description")     val description: String? = null,
    @SerializedName("thumb_id")        val thumbId: Long? = null,
    @SerializedName("thumb_src")       val thumbSrc: String? = null,
    @SerializedName("created")         val created: Long = 0,
    @SerializedName("updated")         val updated: Long = 0,
    @SerializedName("privacy_view")    val privacyView: Int? = null,
)

/**
 * Отдельная фотография ВК (из photos.get / photos.getAll).
 * Отличается от [Attachment.Photo] тем, что это полноценная сущность со
 * всеми полями: дата, лайки, комментарии, альбом.
 */
data class PhotoItem(
    @SerializedName("id")              val id: Long,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("album_id")        val albumId: Long,
    @SerializedName("date")            val date: Long,
    @SerializedName("text")            val text: String? = null,
    @SerializedName("sizes")           val sizes: List<Attachment.Photo.Size>? = null,
    @SerializedName("likes")           val likes: Post.Likes? = null,
    @SerializedName("comments")        val comments: Post.Comments? = null,
    @SerializedName("reposts")         val reposts: Post.Reposts? = null,
    @SerializedName("access_key")      val accessKey: String? = null,
) {
    val largestUrl: String? get() = sizes?.maxByOrNull { it.width * it.height }?.url
    val mediumUrl: String? get() = sizes?.filter { it.width >= 300 && it.width <= 600 }
        ?.minByOrNull { it.width }?.url ?: largestUrl
}

/**
 * Закладка ВК (fave.get). Может быть любого типа: пользователь, сообщество,
 * пост, фото, видео, статья, товар.
 */
data class FaveTag(
    @SerializedName("id")   val id: Long,
    @SerializedName("name") val name: String,
)

data class Bookmark(
    @SerializedName("type")            val type: String,   // user, group, post, photo, video, article, product, link
    @SerializedName("tags")            val tags: List<FaveTag>? = null,
    @SerializedName("seen")            val seen: Boolean? = null,
    @SerializedName("added_date")      val addedDate: Long = 0,
    // Сущность — может быть любого типа. Парсится в VKApiClient в зависимости от type.
    val user: UserProfile? = null,
    val group: Group? = null,
    val post: Post? = null,
    val photo: PhotoItem? = null,
    val video: Video? = null,
    val link: Attachment.Link? = null,
) {
    val title: String get() = when (type) {
        "user" -> user?.fullName ?: ""
        "group" -> group?.name ?: ""
        "post" -> post?.text?.take(60) ?: ""
        "photo" -> photo?.text?.take(60) ?: "Фото"
        "video" -> video?.title ?: ""
        "link" -> link?.title ?: ""
        else -> type
    }
    val thumbUrl: String? get() = when (type) {
        "user" -> user?.photo100
        "group" -> group?.photo100
        "photo" -> photo?.mediumUrl
        "video" -> video?.thumbUrl
        "link" -> link?.photo?.largestUrl
        else -> null
    }
}

/**
 * Опрос ВК (polls.getById).
 */
data class Poll(
    @SerializedName("id")              val id: Long,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("question")        val question: String,
    @SerializedName("created")         val created: Long = 0,
    @SerializedName("votes")           val votes: Int = 0,
    @SerializedName("answer_id")       val answerId: Long? = null,    // выбранный пользователем вариант
    @SerializedName("answers")         val answers: List<Answer> = emptyList(),
    @SerializedName("anonymous")       val anonymous: Int = 0,
    @SerializedName("multiple")        val multiple: Int = 0,
    @SerializedName("closed")          val closed: Int = 0,
    @SerializedName("is_board")        val isBoard: Int = 0,
) {
    data class Answer(
        @SerializedName("id")          val id: Long,
        @SerializedName("text")        val text: String,
        @SerializedName("votes")       val votes: Int = 0,
        @SerializedName("rate")        val rate: Double = 0.0,
    )
    val isVoted: Boolean get() = answerId != null
    val isAnonymous: Boolean get() = anonymous == 1
}

/**
 * Статья ВК (articles.get). Длинные публикации в ВК.
 */
data class Article(
    @SerializedName("id")              val id: Long,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("title")           val title: String,
    @SerializedName("subtitle")        val subtitle: String? = null,
    @SerializedName("content")         val content: String? = null,
    @SerializedName("url")             val url: String? = null,
    @SerializedName("view_url")        val viewUrl: String? = null,
    @SerializedName("owner_name")      val ownerName: String? = null,
    @SerializedName("owner_photo")     val ownerPhoto: String? = null,
    @SerializedName("published_date")  val publishedDate: Long = 0,
    @SerializedName("views")           val views: Int = 0,
    @SerializedName("shares")          val shares: Int = 0,
    @SerializedName("is_favorite")     val isFavorite: Boolean = false,
    @SerializedName("cover_photo")     val coverPhoto: String? = null,
)

/**
 * Документ ВК (docs.get). Расширен по сравнению с [Attachment.Doc]:
 * добавлены date, type, previews.
 */
data class DocFile(
    @SerializedName("id")              val id: Long,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("title")           val title: String,
    @SerializedName("ext")             val ext: String,
    @SerializedName("size")            val size: Long,
    @SerializedName("url")             val url: String,
    @SerializedName("date")            val date: Long = 0,
    @SerializedName("type")            val type: Int = 0,    // 1..8: text, arch, gif, image, audio, video, ebook, unknown
    @SerializedName("preview")         val preview: Preview? = null,
    @SerializedName("access_key")      val accessKey: String? = null,
) {
    data class Preview(
        @SerializedName("photo")       val photo: PhotoItem? = null,
        @SerializedName("video")       val video: Video? = null,
        @SerializedName("gif")         val gif: String? = null,
    )
    val typeLabel: String get() = when (type) {
        1 -> "Текст"
        2 -> "Архив"
        3 -> "GIF"
        4 -> "Изображение"
        5 -> "Аудио"
        6 -> "Видео"
        7 -> "Книга"
        else -> "Файл"
    }
    val isGif: Boolean get() = ext.equals("gif", ignoreCase = true) || type == 3
    val isImage: Boolean get() = type == 4 || ext.lowercase() in listOf("jpg", "jpeg", "png", "webp")
    val sizeLabel: String get() = when {
        size < 1024 -> "${size} Б"
        size < 1024 * 1024 -> "${size / 1024} КБ"
        else -> String.format("%.1f МБ", size / 1024.0 / 1024.0)
    }
}

/**
 * Подсказка поиска ВК (search.getHints).
 */
data class SearchHint(
    @SerializedName("type")            val type: String,   // profile, group, app
    @SerializedName("section")         val section: String? = null,
    @SerializedName("description")     val description: String? = null,
    @SerializedName("global")          val global: Int = 0,
    // Сущность — парсится в зависимости от type
    val user: UserProfile? = null,
    val group: Group? = null,
    // Fix #233 (P1-8): app id для type="app" ( VK search.getHints возвращает
    // объект app, но ранее он не парсился → все app-hints имели одинаковый
    // LazyColumn key "app_null" → crash "Key N was already used").
    val appId: Long? = null,
) {
    val title: String get() = when (type) {
        "profile" -> user?.fullName ?: ""
        "group" -> group?.name ?: ""
        else -> description ?: type
    }
    val thumbUrl: String? get() = when (type) {
        "profile" -> user?.photo100
        "group" -> group?.photo100
        else -> null
    }
}

// Sprint 3 #13: Стикеры.
data class StickerAttachment(
    @SerializedName("sticker_id") val stickerId: Int,
    @SerializedName("product_id") val productId: Int = 0,
    @SerializedName("images") val images: List<StickerImage>? = null,
    @SerializedName("images_with_background") val imagesWithBackground: List<StickerImage>? = null,
    /** Fix #229: URL анимированной версии (animated WebP / GIF). null для статичных. */
    @SerializedName("animation_url") val animationUrl: String? = null,
) {
    /** URL стикера 256px (предпочтительный размер для чата). */
    val displayUrl: String? get() = imagesWithBackground
        ?.firstOrNull { it.width >= 256 }?.url
        ?: images?.maxByOrNull { it.width }?.url

    /** Fix #229: URL для отображения с анимацией. Lottie (.json/.tgs) пропускаем. */
    val animatedDisplayUrl: String? get() {
        val u = animationUrl ?: return null
        val lower = u.substringBefore('?').substringAfterLast('/').lowercase()
        if (lower.endsWith(".json") || lower.endsWith(".tgs")) return null
        return u
    }

    /** Fix #229: лучший URL для рендера — анимированный если есть, иначе статичный. */
    val renderUrl: String? get() = animatedDisplayUrl ?: displayUrl
}

data class StickerImage(
    @SerializedName("url")    val url: String,
    @SerializedName("width")  val width: Int,
    @SerializedName("height") val height: Int,
)

data class StickerPack(
    @SerializedName("id")         val id: Int,
    @SerializedName("title")      val title: String,
    @SerializedName("user_id")    val userId: Long? = null,
    @SerializedName("stickers")   val stickers: List<StickerItem>? = null,
    @SerializedName("icon")      val icon: StickerImage? = null,
    /** Fix #221: куплен ли пак юзером. false → стикер нельзя отправить как стикер,
     *  но можно показать с затемнением и предложить отправить как картинку. */
    val purchased: Boolean = true,
    /** Fix #221: активен ли пак. VK может деактивировать пак (копирайт/жалобы),
     *  при этом purchased=true. active=false → messages.send вернёт err=100
     *  "this sticker is not available". В этом случае отправляем как картинку. */
    val active: Boolean = true,
)

data class StickerItem(
    @SerializedName("sticker_id") val stickerId: Int,
    @SerializedName("product_id") val productId: Int = 0,
    @SerializedName("images") val images: List<StickerImage>? = null,
    @SerializedName("images_with_background") val imagesWithBackground: List<StickerImage>? = null,
    /** Fix #229: URL анимированной версии стикера (animated WebP / GIF, реже Lottie JSON).
     *  null для статичных стикеров. VK отдаёт это поле только у анимированных паков. */
    @SerializedName("animation_url") val animationUrl: String? = null,
) {
    val displayUrl: String? get() = imagesWithBackground
        ?.firstOrNull { it.width >= 128 }?.url
        ?: images?.maxByOrNull { it.width }?.url

    /** Fix #225: URL для отправки как картинку (photos upload).
     *  Prefer images (transparent PNG) — стикер в чате будет без фона.
     *  Берём 256px+ (достаточное качество для фото-вложения).
     *  Fallback на displayUrl (imagesWithBackground) если images пуст. */
    val sendImageUrl: String? get() = images
        ?.firstOrNull { it.width >= 256 }?.url
        ?: images?.maxByOrNull { it.width }?.url
        ?: displayUrl

    /** Fix #229: URL для ОТОБРАЖЕНИЯ с анимацией.
     *  Возвращаем animation_url только если он декодируется Coil-ом
     *  (GIF / animated WebP). Lottie-анимации (.json / .tgs) Coil не умеет —
     *  для них возвращаем null, рендер падает на статичный displayUrl.
     *  Extension-less URL (типичный случай для VK) пропускаем к Coil —
     *  декодеры определяют формат по magic bytes. */
    val animatedDisplayUrl: String? get() {
        val u = animationUrl ?: return null
        val lower = u.substringBefore('?').substringAfterLast('/').lowercase()
        if (lower.endsWith(".json") || lower.endsWith(".tgs")) return null
        return u
    }

    /** Fix #229: признак анимированного стикера (для бейджа ▶ в пикере). */
    val isAnimated: Boolean get() = animatedDisplayUrl != null
}

/**
 * P5.3: Подарок из каталога VK gifts.getCatalog.
 * @param id        ID подарка (передаётся в gifts.send)
 * @param thumbUrl  URL sticker-превью подарка (256px preferred)
 * @param priceVotes цена в голосах (null = бесплатный)
 */
data class GiftItem(
    val id: Long,
    val thumbUrl: String?,
    val priceVotes: Int?,
) {
    val isFree: Boolean get() = priceVotes == null || priceVotes == 0
    val priceText: String get() = if (isFree) "Бесплатно" else "$priceVotes голосов"
}

/**
 * История ВК (stories.get).
 */
data class Story(
    @SerializedName("id")              val id: Int,
    @SerializedName("owner_id")        val ownerId: Long,
    @SerializedName("date")            val date: Long = 0,
    @SerializedName("type")            val type: String = "photo",
    @SerializedName("is_expired")      val isExpired: Int = 0,
    @SerializedName("is_seen")         val isSeen: Int = 0,
    @SerializedName("is_deleted")      val isDeleted: Int = 0,
    @SerializedName("access_key")      val accessKey: String? = null,
    @SerializedName("photo")           val photo: StoryPhoto? = null,
    @SerializedName("video")           val video: StoryVideo? = null,
    @SerializedName("link")            val link: StoryLink? = null,
    @SerializedName("views")           val views: Int = 0,
    @SerializedName("replies")         val replies: StoryReplies? = null,
) {
    val isSeenBool: Boolean get() = isSeen == 1
    val thumbUrl: String? get() = PhotoSizes.bestStoryUrl(photo?.sizes)
        ?: PhotoSizes.bestStoryUrl(video?.preview?.sizes)

    data class StoryPhoto(
        @SerializedName("sizes")    val sizes: List<Size>? = null,
        @SerializedName("text")     val text: String? = null,
    ) {
        data class Size(
            @SerializedName("url")    val url: String,
            @SerializedName("width")  val width: Int,
            @SerializedName("height") val height: Int,
            @SerializedName("type")   val type: String,
        )
    }

    data class StoryVideo(
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("preview")  val preview: StoryPhoto? = null,
        // VK stories: video files map (mp4_144/mp4_240/mp4_360/mp4_480/mp4_720/hls).
        // Реальный VK API ключ — "video_files", но Gson мапит по SerializedName.
        @SerializedName("files")    val files: Map<String, String>? = null,
        // Fallback URL (VK player URL, иногда HTML — используется только если files пуст).
        @SerializedName("player")   val player: String? = null,
    )

    data class StoryLink(
        @SerializedName("url")   val url: String = "",
        @SerializedName("text")  val text: String? = null,
    )

    data class StoryReplies(
        @SerializedName("count")      val count: Int = 0,
        @SerializedName("can_reply")  val canReply: Int = 0,
    )
}

/** Группировка историй по владельцу (stories.get возвращает groups[] + items[]). */
data class StoryGroup(
    val ownerId: Long,
    val name: String? = null,
    val photo100: String? = null,
    val isSeen: Boolean = false,
    val stories: List<Story> = emptyList(),
) {
    val lastStoryThumb: String? get() = stories.lastOrNull { !it.isSeenBool }?.thumbUrl
        ?: stories.lastOrNull()?.thumbUrl
}

// ═══════════════════════════════════════════════════════════════════
//  Музыкальный каталог (catalog.getAudio)
// ═══════════════════════════════════════════════════════════════════

/** Тип визуального блока каталога (из data-view-type в HTML). */
enum class CatalogViewType(val raw: String) {
    HEADER("header"),
    HEADER_EXTENDED("header_extended"),
    SEPARATOR("separator"),
    TRIPLE_STACKED_SLIDER("triple_stacked_slider"),
    LARGE_SLIDER("large_slider"),
    RECOMMS_SLIDER("recomms_slider"),
    LIST("list"),
    UNKNOWN("unknown");

    companion object {
        fun fromRaw(raw: String?): CatalogViewType =
            entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}

/** Один блок музыкального каталога (из catalog.getAudio response). */
data class CatalogBlock(
    val viewType: CatalogViewType,
    val title: String? = null,
    val blockId: String? = null,
    /** ID для «Показать все» / подгрузки. */
    val showAllId: String? = null,
    /** Треки внутри блока (slider types). */
    val tracks: List<Track> = emptyList(),
    /** Плейлисты внутри блока (large_slider). */
    val playlists: List<CatalogPlaylist> = emptyList(),
    /** Редакторская пометка (подпись под заголовком). */
    val subtitle: String? = null,
)

/** Плейлист из каталога (расширенный, по сравнению с AudioPlaylist). */
data class CatalogPlaylist(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val count: Int = 0,
    val plays: Int = 0,
    val accessKey: String? = null,
    val blockId: String? = null,
    /** Совпадение вкусов (для блока «Слушайте друг друга»). */
    val matchPercent: Int? = null,
)

/** Артист трека (из main_artists). */
data class ArtistRef(
    @SerializedName("id")   val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("domain") val domain: String? = null,
)

// ─── #84: Расширенные audio/catalog API (Fix #84, music.zip dump 2026-07-15) ──
// Эти классы ДОПОЛНЯТ архитектуру #80 (CatalogViewType/CatalogBlock/CatalogPlaylist/ArtistRef).
// Используются методами audioGetPlaylistById, audioCreatePlaylist, audioSearchArtists,
// catalogGetAudio, catalogGetSection, catalogGetBlockItems и т.д. в VKApiClient.kt.

/** Полный артист (audio.getArtistsById, catalog.getAudioArtist). */
data class AudioArtist(
    @SerializedName("id")            val id: Long,
    @SerializedName("name")          val name: String,
    @SerializedName("domain")        val domain: String? = null,
    @SerializedName("photo")         val photo: String? = null,
    @SerializedName("photo_100")     val photo100: String? = null,
    @SerializedName("photo_200")     val photo200: String? = null,
    @SerializedName("followers")     val followers: Int = 0,
    @SerializedName("genres")        val genres: List<String>? = null,
    @SerializedName("is_followed")   val isFollowed: Boolean = false,
) {
    val coverUrl: String? get() = photo200 ?: photo100 ?: photo
}

/** Радиостанция (audio.radioGetById). */
data class AudioRadioStation(
    @SerializedName("id")            val id: Long,
    @SerializedName("title")         val title: String,
    @SerializedName("cover_url")     val coverUrl: String? = null,
    @SerializedName("genre_id")      val genreId: Int? = null,
    @SerializedName("is_followed")   val isFollowed: Boolean = false,
)

/**
 * Полный каталожный блок для расширенного API (Fix #84).
 * Отличается от CatalogBlock (#80) тем, что хранит смешанные элементы через CatalogItem,
 * а не раздельные tracks[]/playlists[] — это позволяет обрабатывать ответы с mixed типами
 * от catalog.getSection / catalog.getBlockItems / catalog.getAudioSearch.
 */
data class AudioCatalogBlock(
    val id: String,
    val title: String,
    val items: List<AudioCatalogItem> = emptyList(),
    val nextFrom: String? = null,
    val blockType: String? = null,
)

/** Элемент каталожного блока — трек ИЛИ плейлист ИЛИ артист ИЛИ радио (взаимоисключающе). */
sealed class AudioCatalogItem {
    data class TrackItem(val track: Track) : AudioCatalogItem()
    data class PlaylistItem(val playlist: AudioPlaylist) : AudioCatalogItem()
    data class ArtistItem(val artist: AudioArtist) : AudioCatalogItem()
    data class RadioItem(val station: AudioRadioStation) : AudioCatalogItem()
}

/**
 * Результат catalog.getAudio / catalog.getSection / catalog.getAudioSearch.
 * Содержит список блоков (AudioCatalogBlock) и cursor для пагинации.
 */
data class AudioCatalogSection(
    val sectionId: String,
    val section: String,
    val blocks: List<AudioCatalogBlock> = emptyList(),
    val nextFrom: String? = null,
)

/** Результат audio.getPlaylistById — плейлист + треки + owner info. */
data class PlaylistDetails(
    val playlist: AudioPlaylist,
    val tracks: List<Track> = emptyList(),
    val ownerName: String? = null,
    val ownerPhoto: String? = null,
)

/** Результат audio.searchArtists (расширенный поиск с табами). */
data class AudioSearchResult(
    val tracks: List<Track> = emptyList(),
    val artists: List<AudioArtist> = emptyList(),
    val playlists: List<AudioPlaylist> = emptyList(),
)

/** Dislike статус трека (audio.addDislike/removeDislike). */
data class AudioDislikeStatus(
    val trackId: Long,
    val ownerId: Long,
    val isDisliked: Boolean,
)

// ════════════════════════════════════════════════════════════════════
// §1-NOTIF-ANALYSIS: Notify-settings BFF schema (settingsGeneral.*)
// Source: /home/z/notif/NOTIFICATION_ANALYSIS.md
// VK mobile web (m.vk.ru/settings?act=notify) использует BFF-неймспейс
// settingsGeneral.* вместо классического account.getPushSettings.
// Ответ — generic sections/params дерево; клиент рендерит по полю `type`.
// ════════════════════════════════════════════════════════════════════

/** Секция настроек уведомлений (группа параметров с заголовком). */
data class SettingsSection(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val params: List<SettingsParam> = emptyList(),
)

/**
 * Параметр настройки. type определяет UI-элемент:
 * - "toggle" / "custom_toggle" — Switch
 * - "select" / "radio" — Dropdown / RadioButton
 * - "input" — TextField
 * - "button" — Button (action)
 * - "warning" — информационная карточка
 * - "group" — вложенная группа
 */
data class SettingsParam(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val isChecked: Boolean? = null,
    val value: String? = null,
    val options: List<SettingsParamOption> = emptyList(),
)

/** Опция для select/radio параметров. */
data class SettingsParamOption(
    val value: String,
    val label: String,
)

// ════════════════════════════════════════════════════════════════════
// §1-NOTIF-ANALYSIS: Silent-mode status (account.getSilentModeStatus)
// ════════════════════════════════════════════════════════════════════

/** Текущее состояние «Не беспокоить». */
data class SilentModeStatus(
    /** Unix timestamp when silent mode ends. 0 = inactive. */
    val silentUntil: Long,
    /** 1 = sound enabled globally, 0 = sound disabled. */
    val sound: Int,
    /** -1 = forever, 0 = unmuted, <ts> = until timestamp. */
    val disabledUntil: Long,
    val disabledMentions: Boolean,
    val disabledMassMentions: Boolean,
) {
    val isActive: Boolean get() = silentUntil > System.currentTimeMillis() / 1000
    val isForever: Boolean get() = disabledUntil == -1L
}

// ════════════════════════════════════════════════════════════════════
// §1-NOTIF-ANALYSIS: Banned users list (account.getBanned)
// ════════════════════════════════════════════════════════════════════

/** Результат account.getBanned — список заблокированных пользователей. */
data class BannedUsersList(
    val count: Int,
    val items: List<BannedUser>,
)

/** Один заблокированный пользователь. */
data class BannedUser(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val photo100: String?,
    val photo200: String?,
    val banDate: Long,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}
