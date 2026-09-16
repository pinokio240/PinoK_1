package re.pinok.api

import com.google.gson.JsonObject
import re.pinok.util.AppLog
import re.pinok.data.model.Track
import re.pinok.data.model.AudioSearchResult
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.AudioArtist

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

// ─── Шаг 2.2 (2026-09-16): audio playlist / artist / search finalizer ───

/**
 * Fix #281 (crash): финальная дедупликация результатов поиска.
 *
 * Артисты, спарсенные из links[] catalog.getAudioSearch, приходят с id=0 у
 * ВСЕХ (VK не отдаёт числовой id в links). Без дедупа два таких артиста в
 * результате давали два item с key="artist_0" в LazyRow →
 * IllegalArgumentException «Key was already used» → процесс падал (пользователь
 * видел «приложение закрылось при поиске»).
 *
 * Идентичность артиста: id>0 → по числовому id; id=0 → по имени (lowercase).
 * Плейлисты/треки — по (ownerId, id). Треки обрезаются до maxTracks.
 */
internal fun finalizeAudioSearchResult(
    tracks: MutableList<Track>,
    artists: MutableList<re.pinok.data.model.AudioArtist>,
    playlists: MutableList<AudioPlaylist>,
    maxTracks: Int,
    nextFrom: String? = null,
): re.pinok.data.model.AudioSearchResult {
    val uniqueTracks = tracks.distinctBy { "${it.ownerId}_${it.id}" }.take(maxTracks)
    val uniqueArtists = artists.distinctBy { a ->
        if (a.id > 0L) "id_${a.id}" else "name_${a.name.lowercase()}"
    }
    val uniquePlaylists = playlists.distinctBy { "${it.ownerId}_${it.id}" }
    return re.pinok.data.model.AudioSearchResult(
        uniqueTracks, uniqueArtists, uniquePlaylists, nextFrom,
    )
}

/** Универсальный парсер AudioPlaylist из JsonObject. */
internal fun parseAudioPlaylist(o: JsonObject): re.pinok.data.model.AudioPlaylist? {
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
internal fun parseAudioArtist(o: JsonObject): re.pinok.data.model.AudioArtist? {
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

