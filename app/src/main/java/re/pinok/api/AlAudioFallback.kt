// File: api/AlAudioFallback.kt
package re.pinok.api

import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.Request
import re.pinok.data.model.Track
import re.pinok.util.AppLog

/**
 * §42.12 P1 #4: web-fallback для получения URL трека через al_audio.php.
 *
 * Когда `audio.getById` (API method) вернул null или невалидный URL
 * (audio_api_unavailable без возможности расшифровки), VKNext использует
 * web-fallback: POST на `https://vk.com/al_audio.php` с `act=reload_audio`
 * и `audio_id=OWNER_ID_TRACK_ID`. Это web-endpoint, требует remixsid cookie
 * (НЕ access_token!). Возвращает JSON с массивом audio-данных, включая url.
 *
 * Этот метод использовался в десктопном VK-вебе для ленивой подгрузки URL
 * при прокрутке списка аудио. Мобильный API не имеет аналога — только web.
 *
 * Поток:
 *  1. [fetchReloadAudio] — точка входа. Принимает Track, возвращает Track? с url.
 *  2. Собирает form body: act=reload_audio, aid=OWNER_TRACK, ids=OWNER_TRACK.
 *  3. POST https://vk.com/al_audio.php с Cookie: remixsid=...
 *  4. Парсит ответ: <!>JSON или <!><!>JSON (VK ajax-формат).
 *  5. Извлекает url, прогоняет через AudioUrlUnmasker (расшифровка).
 *
 * Безопасность:
 *  — Cookie: только remixsid (НЕ передаём access_token в web!).
 *  — User-Agent: десктопный Chrome (vk.com отдаёт упрощённый HTML мобильным UA).
 *  — Timeout: 10s (web slower than API).
 *  — Если remixsid пуст → сразу return null, не делаем запрос.
 */
class AlAudioFallback(
    private val httpClient: okhttp3.OkHttpClient,
    private val exchangeAuthRepository: re.pinok.auth.exchange.ExchangeAuthRepository?,
) {

    companion object {
        private const val TAG = "AlAudioFallback"
        private const val AL_AUDIO_URL = "https://vk.com/al_audio.php"
        private const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Получить URL трека через web al_audio.php reload_audio.
     *
     * @param track трек с id, ownerId, (опц.) accessKey. url может быть null.
     * @return обновлённый Track с url если удалось, иначе null.
     */
    suspend fun fetchReloadAudio(track: Track): Track? {
        val remixsid = exchangeAuthRepository?.remixsid()
        if (remixsid == null || remixsid.isBlank()) {
            AppLog.d(TAG, "fetchReloadAudio: no remixsid — skip (need web login)")
            return null
        }

        val audioId = "${track.ownerId}_${track.id}"
        val formBuilder = FormBody.Builder()
            .add("act", "reload_audio")
            .add("al", "1")
            .add("ids", audioId)
        if (track.accessKey != null && track.accessKey.isNotBlank()) {
            formBuilder.add("access_keys", track.accessKey)
        }

        val req = Request.Builder()
            .url(AL_AUDIO_URL)
            .post(formBuilder.build())
            .header("User-Agent", WEB_UA)
            .header("Cookie", "remixsid=$remixsid")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/plain, */*; q=0.01")
            .header("Referer", "https://vk.com/audio")
            .build()

        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.w(TAG, "fetchReloadAudio: HTTP ${resp.code} for #$audioId")
                    return null
                }
                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    AppLog.w(TAG, "fetchReloadAudio: empty body for #$audioId")
                    return null
                }
                parseReloadAudioResponse(body, track)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchReloadAudio: failed for #$audioId: ${e.message}")
            null
        }
    }

    /**
     * Парсинг ответа al_audio.php.
     *
     * VK ajax-формат: `<!>JSON` или `<!><!>JSON` (префикс с разделителями).
     * Внутри JSON: массив `[[OWNER_ID, TRACK_ID, URL, ARTIST, TITLE, DURATION, ...]]`.
     * URL в ответе обфусцирован — нужен AudioUrlUnmasker.unmask.
     */
    private fun parseReloadAudioResponse(body: String, track: Track): Track? {
        // Убираем префикс <!>...<!> перед JSON.
        val jsonStr = body.substringAfterLast("<!>")
        if (jsonStr.isBlank()) {
            AppLog.w(TAG, "parseReloadAudioResponse: no JSON after <!> prefix")
            return null
        }

        return try {
            val json = JsonParser.parseString(jsonStr)
            // Ответ может быть массивом массивов или объектом с payload.
            val arr = if (json.isJsonArray) json.asJsonArray else return null
            // Ищем первый элемент, который является массивом аудио-данных.
            // Структура VK: [[audio_tuple], user_info, ...] — первый элемент.
            val audioTuple = arr.firstOrNull { it.isJsonArray }?.asJsonArray
            if (audioTuple == null || audioTuple.size() < 3) {
                AppLog.w(TAG, "parseReloadAudioResponse: no audio tuple in response")
                return null
            }

            // VK audio tuple format (legacy web):
            // [0] = owner_id, [1] = track_id, [2] = url (обфусцированный!),
            // [3] = artist, [4] = title, [5] = duration, ...
            val urlRaw = audioTuple.get(2)?.takeIf { !it.isJsonNull }?.asString
            if (urlRaw.isNullOrBlank() || urlRaw.contains("audio_api_unavailable")) {
                // URL обфусцирован или недоступен — пробуем расшифровать.
                val unmasked = if (urlRaw != null) {
                    try {
                        re.pinok.api.AudioUrlUnmasker.unmask(urlRaw, exchangeAuthRepository?.userId() ?: 0L)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "parseReloadAudioResponse: unmask failed: ${e.message}")
                        null
                    }
                } else null
                if (unmasked.isNullOrBlank()) {
                    AppLog.w(TAG, "parseReloadAudioResponse: no valid url for #${track.ownerId}_${track.id}")
                    return null
                }
                return track.copy(url = unmasked)
            }

            // URL уже валидный (редко для web endpoint, но бывает).
            track.copy(url = urlRaw)
        } catch (e: Exception) {
            AppLog.w(TAG, "parseReloadAudioResponse: JSON parse error: ${e.message}")
            null
        }
    }
}
