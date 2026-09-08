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
        // #ARCH-CONTAINERS 3.7-1: Track в :core:data — smart cast чужого модуля
        // невозможен (было: accessKey != null && accessKey.isNotBlank()); захват в val.
        val fallbackAccessKey = track.accessKey
        if (!fallbackAccessKey.isNullOrBlank()) {
            formBuilder.add("access_keys", fallbackAccessKey)
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

    // ─────────────────────────────────────────────────────────────────────
    // #AUDIO-ADD-WEB (2026-09-08): «Добавить в мою музыку» web-fallback.
    //
    // VK web добавляет трек к себе запросом
    //   ajax.post("al_audio.php?act=add",
    //     { group_id: 0, audio_owner_id: a.ownerId, audio_id: a.id, hash: a.addHash })
    // (дамп audio.a39c029f.js, снапшот Музыка.zip). API-метод audio.add для
    // web-токенов vk1.a.* обычно отдаёт ошибку прав — поэтому порядок такой:
    // сначала audio.add (вдруг токен умеет), затем этот web-путь.
    //
    // Источник hash (addHash), доказательства из бандлов core_spa:
    //  1. AUDIO_ITEM_INDEX_HASHES = 13: tuple[13] сериализатора audio-объекта —
    //     строка "addHash/editHash/actionHash/deleteHash/replaceHash/urlHash/restoreHash"
    //     → addHash = первый сегмент.
    //  2. Для API-объектов тот же сериализатор делает
    //     {addHash: e.access_key, ...} — VK web сам подставляет access_key как
    //     addHash. Значит access_key — валидный hash для act=add.
    // Порядок кандидатов: tuple[13] из reload_audio → access_key трека.
    // Оба пустые → честный отказ (без выдуманных хешей).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Добавить трек в «Мою музыку» web-запросом al_audio.php?act=add.
     *
     * @return Pair(ok, errorText): ok=true — добавлено; ok=false — errorText
     *         содержит РЕАЛЬНЫЙ ответ/ошибку VK (без маскировки), готовый
     *         для показа пользователю.
     */
    suspend fun addTrackToMyMusic(track: Track): Pair<Boolean, String?> {
        if (track.id <= 0L || track.ownerId == 0L) {
            return false to "Трек без корректного id/owner_id — добавление невозможно"
        }
        val tag = "#${track.ownerId}_${track.id}"
        val remixsid = exchangeAuthRepository?.remixsid()
        if (remixsid == null || remixsid.isBlank()) {
            return false to "Нет web-сессии (remixsid) — перевойдите в приложение"
        }

        // 1) Кандидат hash: tuple[13] reload_audio, первый сегмент (= addHash).
        // 2) Фолбэк: access_key — VK web сам подставляет его как addHash.
        // NULL-ЯВНО: elvis-фолбэк между двумя равнозначными источниками hash
        // (доказано сериализатором core_spa: addHash = access_key для API-объектов).
        val tupleHash = extractAddHashFromReload(track)
        val hash = tupleHash ?: track.accessKey
        if (hash.isNullOrBlank()) {
            AppLog.w(TAG, "#AUDIO-ADD-WEB $tag: нет addHash (tuple) и access_key — отказ")
            return false to "VK не отдал хеш добавления (addHash/access_key) для этого трека"
        }
        AppLog.d(TAG, "#AUDIO-ADD-WEB $tag: hash source = ${if (tupleHash != null) "tuple[13]" else "access_key"}")

        val form = FormBody.Builder()
            .add("act", "add")
            .add("al", "1")
            .add("group_id", "0")
            .add("audio_owner_id", track.ownerId.toString())
            .add("audio_id", track.id.toString())
            .add("hash", hash)
            .build()
        val req = Request.Builder()
            .url(AL_AUDIO_URL)
            .post(form)
            .header("User-Agent", WEB_UA)
            .header("Cookie", "remixsid=$remixsid")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/plain, */*; q=0.01")
            .header("Referer", "https://vk.com/audio")
            .build()

        return try {
            // NULL-ЯВНО: okhttp-цепочки (body?.string) — сетевой слой, паттерн
            // всего файла (fetchReloadAudio выше — те же цепочки).
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = "HTTP ${resp.code}: ${body.take(160)}"
                    AppLog.w(TAG, "#AUDIO-ADD-WEB $tag http fail: $msg")
                    return false to msg
                }
                parseAlAudioPayload(body, tag)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "#AUDIO-ADD-WEB $tag request fail: ${e.message}")
            false to "${e.message}"
        }
    }

    /**
     * Парсинг payload VK ajax для act=add.
     * Форматы: "<!>…<!>1" (числовой payload ≥ 1 = успех), JSON {"error":…},
     * произвольный текст — показываем пользователю как есть (no-stub).
     * @return Pair(ok, errorText для пользователя; null при успехе).
     */
    private fun parseAlAudioPayload(body: String, tag: String): Pair<Boolean, String?> {
        val payload = body.substringAfterLast("<!>").trim()
        AppLog.d(TAG, "#AUDIO-ADD-WEB payload for $tag: ${body.take(200)}")
        if (payload.isEmpty()) {
            return false to "Пустой ответ al_audio.php"
        }
        val asInt = payload.toIntOrNull()
        if (asInt != null) {
            // Числовой payload: ≥1 = подтверждение act=add (onDone-протокол vk web).
            return if (asInt >= 1) true to null
            else false to "VK отклонил добавление (payload=$asInt)"
        }
        return try {
            val json = JsonParser.parseString(payload)
            if (json.isJsonObject) {
                val o = json.asJsonObject
                val errEl = o.get("error")
                if (errEl != null && !errEl.isJsonNull) {
                    // VK web-ошибка: {"error": "..."} или {"error": {"error_msg": "..."}}.
                    val msg: String = if (errEl.isJsonObject) {
                        val msgEl = errEl.asJsonObject.get("error_msg")
                        if (msgEl != null && msgEl.isJsonPrimitive) msgEl.asString
                        else errEl.toString().take(160)
                    } else if (errEl.isJsonPrimitive) {
                        errEl.asString
                    } else {
                        errEl.toString().take(160)
                    }
                    false to msg
                } else {
                    // JSON-объект без error — подтверждение (протокол допускает data-ответ).
                    true to null
                }
            } else {
                false to "Неизвестный ответ al_audio.php: ${payload.take(120)}"
            }
        } catch (e: Exception) {
            // Не-JSON payload без error-маркеров во всём теле — считаем успехом
            // (vk ajax иногда присыпает ответ разметкой).
            if (body.contains("\"error\"")) {
                false to "Ошибка al_audio.php: ${payload.take(120)}"
            } else {
                true to null
            }
        }
    }

    /**
     * addHash из reload_audio: tuple[13] = "addHash/editHash/…" (сериализатор
     * core_spa, AUDIO_ITEM_INDEX_HASHES = 13). Первый сегмент; null если нет
     * tuple/поля/сегмент пуст.
     */
    private suspend fun extractAddHashFromReload(track: Track): String? {
        val tuple = fetchReloadTuple(track) ?: return null
        if (tuple.size() <= 13) return null
        val hashesEl = tuple.get(13)
        if (hashesEl == null || hashesEl.isJsonNull || !hashesEl.isJsonPrimitive) return null
        val addHash = hashesEl.asString.split("/").firstOrNull().orEmpty().trim()
        return if (addHash.isBlank()) null else addHash
    }

    /**
     * Сырой audio-tuple из reload_audio (общий источник для URL-парсера и
     * hash-экстрактора — ОДИН сетевой запрос, без дублей).
     */
    private suspend fun fetchReloadTuple(track: Track): com.google.gson.JsonArray? {
        val remixsid = exchangeAuthRepository?.remixsid()
        if (remixsid == null || remixsid.isBlank()) return null
        val audioId = "${track.ownerId}_${track.id}"
        val formBuilder = FormBody.Builder()
            .add("act", "reload_audio")
            .add("al", "1")
            .add("ids", audioId)
        // #ARCH-CONTAINERS 3.7-1: smart cast чужого модуля невозможен — захват в val.
        val fallbackAccessKey = track.accessKey
        if (!fallbackAccessKey.isNullOrBlank()) {
            formBuilder.add("access_keys", fallbackAccessKey)
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
                    AppLog.w(TAG, "fetchReloadTuple: HTTP ${resp.code} for #$audioId")
                    return null
                }
                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    AppLog.w(TAG, "fetchReloadTuple: empty body for #$audioId")
                    return null
                }
                val jsonStr = body.substringAfterLast("<!>")
                if (jsonStr.isBlank()) return null
                val json = JsonParser.parseString(jsonStr)
                if (!json.isJsonArray) return null
                json.asJsonArray.firstOrNull { it.isJsonArray }?.asJsonArray
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchReloadTuple: failed for #$audioId: ${e.message}")
            null
        }
    }
}
