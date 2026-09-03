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
