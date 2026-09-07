package re.pinok.data.model

import com.google.gson.annotations.SerializedName

/**
 * Task 22 (2026-09-03): UserProfile перенесён из app/.../data/model/Models.kt
 * в :core:data (git mv секции, пакет re.pinok.data.model сохранён).
 *
 * Причина: CallsDependencies/CallsApi (фасады :feature:calls, Task 20/21)
 * используют UserProfile в сигнатурах usersGetByIds/friendsGetOnline, а класс
 * жил в :app — цикл :app -> :feature:calls -> :app запрещён Gradle (лог
 * 2026-09-03: 55 ошибок, все каскад от Unresolved UserProfile).
 *
 * Класс самодостаточен: только @SerializedName (gson) + вложенные data-классы
 * (LastSeen/City/Country/Counters/Cover/Personal) — без ссылок на :app.
 * Модели-потребители в :app (Models.kt: UserFull.user, VkAccount.user и т.д.)
 * продолжают видеть UserProfile без правок: тот же пакет, разрешение
 * same-package через classpath (прецедент: PermissionManager -> AppLog).
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
    // --- #PROFILE-SNAP (PROFILE-P0-2, 2026-09-05): веб-набор полей профиля
    // (инвентарь §2.0/§4.1, Приложение А; fields usersGetFullExtended
    // расширен волной П-0, парсинг подключён этим этапом). Все поля честные
    // nullable: VK отдаёт их не для всех токенов/страниц (закрытый профиль,
    // чужой, групповой токен). Флаги в ответе приходят и 0/1, и true/false —
    // парсинг через safeIntNullable (терпит оба, прецедент Fix #321).
    @SerializedName("mutual")       val mutual: Mutual? = null,
    @SerializedName("owner_state")  val ownerState: Int? = null,
    @SerializedName("stories_archive_count") val storiesArchiveCount: Int? = null,
    @SerializedName("image_status") val imageStatus: String? = null,
    @SerializedName("deactivated")  val deactivated: String? = null,
    @SerializedName("blacklisted")  val blacklisted: Int? = null,
    @SerializedName("blacklisted_by_me") val blacklistedByMe: Int? = null,
    @SerializedName("no_index")     val noIndex: Int? = null,
    @SerializedName("lists")        val friendLists: List<Int>? = null,
    @SerializedName("can_invite_to_chats") val canInviteToChats: Int? = null,
    @SerializedName("can_see_wishes") val canSeeWishes: Int? = null,
    @SerializedName("can_ban")      val canBan: Int? = null,
    @SerializedName("can_see_gifts") val canSeeGifts: Int? = null,
    @SerializedName("can_call")     val canCall: Int? = null,
    @SerializedName("can_send_friend_request") val canSendFriendRequest: Int? = null,
    @SerializedName("can_see_all_posts") val canSeeAllPosts: Int? = null,
    @SerializedName("can_subscribe_stories") val canSubscribeStories: Int? = null,
    @SerializedName("is_subscribed_stories") val isSubscribedStories: Int? = null,
    @SerializedName("is_sber_verified") val isSberVerified: Int? = null,
    @SerializedName("is_tinkoff_verified") val isTinkoffVerified: Int? = null,
    @SerializedName("is_esia_verified") val isEsiaVerified: Int? = null,
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
        // #PROFILE-SNAP (PROFILE-P0-2): кластер clips* — реально возвращаемые
        // поля counters из снапшота (инвентарь §1.1.2: 18 полей counters,
        // среди них clips/clips_followers/clips_views/clips_likes).
        @SerializedName("clips")           val clips: Int? = null,
        @SerializedName("clips_followers") val clipsFollowers: Int? = null,
        @SerializedName("clips_views")     val clipsViews: Int? = null,
        @SerializedName("clips_likes")     val clipsLikes: Int? = null,
    )
    /** #PROFILE-SNAP (PROFILE-P0-2): mutual в users.get приходит объектом
     *  {count:N} (веб-запрашивает field mutual, инвентарь §2.0/§4.1). */
    data class Mutual(@SerializedName("count") val count: Int = 0)
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
