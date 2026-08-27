// File: media/GeniusLyricsFetcher.kt
package re.pinok.media

import okhttp3.Request
import re.pinok.util.AppLog

/**
 * §42.12 P2 #8: получение текстов песен с Genius.com.
 *
 * Genius API требует access_token даже для public search. VKNext использует
 * client-id flow (зашитый токен в extension). Мы делаем проще: web-scraping
 * genius.com/search?q=artist+title → парсим HTML → берём первый результат →
 * грузим страницу песни → извлекаем текст из div.lyrics.
 *
 * Это НЕ требует API ключа, но хрупкое (если Genius поменяет вёрстку — сломается).
 * В случае ошибки возвращаем null (тег ©lyr просто не пишется, трек сохраняется).
 *
 * User-Agent: подменяем на Chrome desktop (Genius отдаёт simplified HTML мобильным).
 *
 * Безопасность:
 *  — Только HTTPS (genius.com редиректит на HTTPS).
 *  — Timeout 10s на каждый запрос (search + lyrics page = 2 запроса).
 *  — Лимит длины текста: 32KB (защита от мусорных ответов).
 *  — HTML-entities раскодируем (≥<&#39; и т.д.).
 */
object GeniusLyricsFetcher {

    private const val TAG = "GeniusLyricsFetcher"
    private const val SEARCH_URL = "https://genius.com/search?q="
    private const val MAX_LYRICS_LEN = 32_000

    private val httpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Получить текст песни для (artist, title).
     *
     * @return текст песни (многострочный) или null если не найдено/ошибка.
     */
    suspend fun fetchLyrics(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank()) return null

        // Очищаем title от лишнего: "(Official Video)", "[Remix]", feat. и т.д.
        val cleanTitle = cleanTrackTitle(title)
        val query = "$artist $cleanTitle".trim()

        return try {
            val songUrl = searchFirstSongUrl(query)
            if (songUrl == null) {
                AppLog.d(TAG, "fetchLyrics: no search results for '$query'")
                return null
            }
            val lyrics = fetchLyricsFromPage(songUrl)
            if (lyrics != null && lyrics.length > MAX_LYRICS_LEN) {
                lyrics.substring(0, MAX_LYRICS_LEN)
            } else {
                lyrics
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchLyrics: failed for '$query': ${e.message}")
            null
        }
    }

    /** Поиск первой песни на Genius, возврат URL её страницы. */
    private suspend fun searchFirstSongUrl(query: String): String? {
        val url = SEARCH_URL + java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetchHtml(url) ?: return null

        // Ищем в HTML ссылку на песню: <a href="https://genius.com/Artist-song-lyrics">
        // Берём первую. Регекс уязвим к изменениям вёрстки, но Genius стабильный.
        val regex = Regex("href=\"(https://genius\\.com/[A-Za-z0-9\\-]+(?:-lyrics)?)\"")
        val match = regex.find(html)
        return match?.groupValues?.getOrNull(1)
    }

    /** Загрузить страницу песни и извлечь текст из div с классом lyrics. */
    private suspend fun fetchLyricsFromPage(songUrl: String): String? {
        val html = fetchHtml(songUrl) ?: return null

        // Genius использует <div data-lyrics-container="..."> для текста.
        // Альтернатива: JSON-LD в <script type="application/ld+json"> с recodingMusic.
        // Берём data-lyrics-container — самый стабильный селектор.
        val containerRegex = Regex(
            "data-lyrics-container=\"[^\"]*\"[^>]*>([\\s\\S]*?)</div>",
            RegexOption.IGNORE_CASE,
        )
        val matches = containerRegex.findAll(html).toList()
        if (matches.isEmpty()) {
            AppLog.d(TAG, "fetchLyricsFromPage: no lyrics container in $songUrl")
            return null
        }

        // Склеиваем все контейнеры (Genius разбивает текст на несколько div
        // для verse/chorus/bridge).
        val sb = StringBuilder()
        for (m in matches) {
            val raw = m.groupValues[1]
            val cleaned = cleanHtmlToText(raw)
            if (cleaned.isNotBlank()) {
                sb.append(cleaned).append("\n\n")
            }
        }
        val result = sb.toString().trim()
        return if (result.isBlank()) null else result
    }

    /** Скачать HTML по URL с desktop User-Agent. */
    private fun fetchHtml(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchHtml failed: ${e.message}")
            null
        }
    }

    /** Очистить title от мусора: "(Official Video)", "[Remix]", "feat. ...". */
    private fun cleanTrackTitle(title: String): String {
        var t = title
        // Убираем содержимое круглых/квадратных скобок если они в конце.
        t = t.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        t = t.replace(Regex("\\s*\\[[^]]*\\]\\s*$"), "")
        // Убираем "feat. ..." в конце.
        t = t.replace(Regex("(?i)\\s*feat\\..*$"), "")
        return t.trim()
    }

    /** Конвертировать HTML-фрагмент в plain text (раскодировать entities, убрать теги). */
    private fun cleanHtmlToText(html: String): String {
        var s = html
        // <br> → newline
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        // <p>, </p> → newline
        s = s.replace(Regex("(?i)</?p[^>]*>"), "\n")
        // Убираем все остальные теги.
        s = s.replace(Regex("<[^>]+>"), "")
        // HTML entities.
        s = s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        // Сжимаем множественные пустые строки.
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }
}
