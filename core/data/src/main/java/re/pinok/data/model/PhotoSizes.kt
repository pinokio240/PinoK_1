// File: data/model/PhotoSizes.kt
package re.pinok.data.model

/**
 * Единый шаблон разрешения размера фото (максимальный по площади).
 *
 * Раньше `sizes.maxByOrNull { it.width * it.height }` был размазан по 9 местам:
 * `Models.kt` (thumbUrl/largestUrl/largestSize), `ProfileScreen`, `FeedScreen`,
 * `CommunityScreen`, `PostDetailScreen`, `ChatDetailScreen`, `StoryViewerScreen`.
 * Теперь все вызовы идут через этот helper — единая точка, где площадь считается
 * как Long (защита от переполнения Int на очень больших изображениях).
 */
object PhotoSizes {

    /**
     * Максимальный размер по площади (width * height как Long).
     * Null если [sizes] пуст или null.
     */
    fun <T> best(sizes: List<T>?, width: (T) -> Int, height: (T) -> Int): T? =
        sizes?.maxByOrNull { width(it).toLong() * height(it).toLong() }

    /** Максимальный размер фото-вложения ([Attachment.Photo.Size]). */
    fun best(sizes: List<Attachment.Photo.Size>?): Attachment.Photo.Size? =
        best(sizes, Attachment.Photo.Size::width, Attachment.Photo.Size::height)

    /** URL максимального размера фото-вложения. */
    fun bestUrl(sizes: List<Attachment.Photo.Size>?): String? = best(sizes)?.url

    /** Максимальный размер истории ([Story.StoryPhoto.Size]). */
    fun bestStory(sizes: List<Story.StoryPhoto.Size>?): Story.StoryPhoto.Size? =
        best(sizes, Story.StoryPhoto.Size::width, Story.StoryPhoto.Size::height)

    /** URL максимального размера истории. */
    fun bestStoryUrl(sizes: List<Story.StoryPhoto.Size>?): String? = bestStory(sizes)?.url
}
