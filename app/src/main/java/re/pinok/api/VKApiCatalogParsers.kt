package re.pinok.api

import com.google.gson.JsonObject
import re.pinok.data.model.CatalogViewType
import re.pinok.data.model.TrackArtist

/**
 * O2 #PERF-STRUCT (2026-09-16) шаг 2.3: вынос листовых catalog-парсеров из VKApiClient.kt.
 *
 * Вариант B (безопасный): только чистые функции, НЕ зависящие от приватных полей
 * VKApiClient (httpClient, tokenStorage, prefs, exchangeAuthRepository, call() и т.д.).
 * Поэтому top-level в пакете re.pinok.api — вызовы из VKApiClient.kt продолжают
 * работать без правок (Kotlin резолвит top-level functions в том же package).
 *
 * НЕ вынесены (зависят от extractAudioUrl -> exchangeAuthRepository):
 *   parseTrackFromCatalogItem, parseTrackFromJson, parseCatalogWebBlock,
 *   parseCatalogSectionBlocks, parseCatalogBlocks — остаются в VKApiClient.kt.
 */

/** #MUSIC-CATALOG-WEB-GATEWAY: AudioPlaylist -> CatalogPlaylist. */
internal fun toCatalogPlaylist(
    p: re.pinok.data.model.AudioPlaylist,
): re.pinok.data.model.CatalogPlaylist =
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

/** Парсит main_artists массив. */
internal fun parseMainArtists(o: JsonObject): List<TrackArtist>? {
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
internal fun parseCatalogPlaylist(o: JsonObject): re.pinok.data.model.CatalogPlaylist {
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

/** Фильтрация рекламных/подписочных блоков. */
internal fun isAdOrSubscriptionBlock(title: String?, viewType: CatalogViewType): Boolean {
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
