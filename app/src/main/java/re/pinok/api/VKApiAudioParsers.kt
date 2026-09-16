package re.pinok.api

import com.google.gson.JsonObject

/**
 * O2 #PERF-STRUCT (2026-09-16) шаг 2.1: вынос "листовых" audio-парсеров из VKApiClient.kt.
 *
 * Эти функции зависят только от JsonObject — не используют приватные поля VKApiClient
 * (httpClient, tokenStorage, prefs, call() и т.д.), поэтому могут быть top-level
 * в том же пакете re.pinok.api. Вызовы из VKApiClient.kt продолжают работать
 * без правок (Kotlin резолвит top-level functions в том же package).
 */

internal fun extractAlbumThumb(o: JsonObject): String? {
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

/** Извлекает обложку из разных форматов каталога. */
internal fun extractCatalogAlbumThumb(o: JsonObject): String? {
    // Прямая строка
    val direct = o.get("album_thumb")?.takeIf { !it.isJsonNull }?.asString
    if (!direct.isNullOrBlank()) return direct
    // Объект album.thumb
    val thumbObj = o.getAsJsonObject("album")?.getAsJsonObject("thumb") ?: return null
    for (key in listOf("photo_600", "photo_300", "photo_270", "photo_135", "photo_68", "photo_34")) {
        val url = thumbObj.get(key)?.takeIf { !it.isJsonNull }?.asString
        if (!url.isNullOrBlank()) return url
    }
    // Обложка thumb (новый формат): cover_url или photos[]
    val coverUrl = o.get("cover_url")?.takeIf { !it.isJsonNull }?.asString
    if (!coverUrl.isNullOrBlank()) return coverUrl
    return null
}

/** Извлекает обложку плейлиста (разные форматы VK). */
internal fun extractPlaylistCover(o: JsonObject): String? {
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
