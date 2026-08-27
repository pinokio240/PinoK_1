// File: media/Mp4TagWriter.kt
package re.pinok.media

import re.pinok.data.model.Track
import re.pinok.util.AppLog
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * §42.12 P1 #3: запись MP4 metadata тегов (Apple iTunes format).
 *
 * MP4-контейнер (.m4a) хранит metadata в атоме `moov/udta/meta/ilst`. Каждый
 * тег — это дочерний атом с 4-байтным именем (fourcc) и структурой `data`:
 *
 *   [size:4][name:4='©nam'][size:4][name:4='data'][flags:4=1][reserved:4=0][payload:N]
 *
 * Apple fourcc-имена тегов (всегда 4 байта, могут начинаться с © = 0xA9):
 *   ©nam — Track Title
 *   ©ART — Artist
 *   ©alb — Album
 *   ©day — Year
 *   trkn — Track Number (binary: 2-byte 0, 2-byte track, 2-byte total, 2-byte 0)
 *   disk — Disk Number (аналогично trkn)
 *   covr — Cover Art (тип 0x0D для JPEG, 0x0E для PNG — флаги вместо 1)
 *   ©lyr — Lyrics
 *   cmt  — Comment
 *   ©too — Encoder ("PinoK v2.0.0")
 *
 * Структура .m4a до вставки тегов:
 *   ftyp | moov(mvhd, trak, ...) | mdat
 *
 * После вставки:
 *   ftyp | moov(mvhd, udta(meta(hdlr, ilst(©nam, ©ART, ...))), trak, ...) | mdat
 *
 * Вставка: открываем RandomAccessFile, ищем конец moov, вставляем udta-атом
 * с meta+ilst внутри. Размер moov и всех родительских атомов пересчитывается.
 *
 * Альтернатива: Android `MediaMetadataEditor` (API 31+) — но он только для
 * MediaStore-записи, не для произвольного файла. Media3 не имеет public API
 * для записи MP4 tags. Поэтому ручная сборка — единственный путь.
 *
 * Безопасность:
 *  — Все строки кодируем в UTF-8 (MP4 стандарт).
 *  — Размер атомов — big-endian 32-bit (кроме mvhd/hdlr — они 32-bit).
 *  — Если .m4a не имеет moov (битый) — отказываемся, не ломаем файл.
 *  — Cover image скачивается отдельным OkHttp-запросом (не блокирует UI).
 */
object Mp4TagWriter {

    private const val TAG = "Mp4TagWriter"

    /** Минимальная длина строки чтобы писать её в тег (фильтр мусора). */
    private const val MIN_TAG_LEN = 1

    /**
     * Записать metadata в .m4a файл.
     *
     * @param m4aFile  целевой .m4a (уже создан MediaMuxer/SirenTranscoder).
     * @param track    трек (берём artist, title, albumId, albumThumb, genreId, subtitle).
     * @param lyrics   текст песни (если есть, для ©lyr). null = не писать.
     * @param comment  промо-комментарий (если есть, для cmt). null = не писать.
     * @param coverUrl URL обложки для covr-атома (null = не писать обложку).
     * @return true если теги записаны успешно, false при ошибке.
     */
    suspend fun writeTags(
        m4aFile: File,
        track: Track,
        lyrics: String? = null,
        comment: String? = null,
        coverUrl: String? = null,
    ): Boolean {
        if (!m4aFile.exists() || m4aFile.length() < 10_000L) {
            AppLog.w(TAG, "writeTags: ${m4aFile.name} not found or too small — skip")
            return false
        }

        // #VK-MUSIC-SAVER-PORT: обложка (covr) — скачиваем из albumThumb если передано.
        val coverBytes = downloadCover(coverUrl)

        // Собираем ilst-атом со всеми тегами.
        val ilstBytes = buildIlstAtom(track, lyrics, comment, coverBytes)
        if (ilstBytes.isEmpty()) {
            AppLog.w(TAG, "writeTags: ilst empty (no tags to write) — skip")
            return false
        }

        // Вставляем udta→meta→ilst в moov.
        return try {
            insertUdtaIntoMoov(m4aFile, ilstBytes)
        } catch (e: Exception) {
            AppLog.e(TAG, "writeTags: failed to insert udta: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Сборка ilst-атома
    // ════════════════════════════════════════════════════════════════

    /**
     * Собрать ilst-атом со всеми тегами из [track].
     * Возвращает пустой массив если нет ни одного тега.
     */
    private fun buildIlstAtom(
        track: Track,
        lyrics: String?,
        comment: String?,
        coverBytes: ByteArray?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // ©nam — title (+subtitle в скобках, как в веб-VK)
        val titleWithSubtitle = track.title +
            (track.subtitle?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        if (titleWithSubtitle.isNotBlank()) {
            out.write(makeTagAtom("©nam".toFourCc(), titleWithSubtitle.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // ©ART — artist
        if (track.artist.length >= MIN_TAG_LEN) {
            out.write(makeTagAtom("©ART".toFourCc(), track.artist.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // ©alb — album (albumId как строка, fallback пусто)
        val albumStr = track.albumId?.takeIf { it != 0L }?.let { "album_$it" }
        if (albumStr != null) {
            out.write(makeTagAtom("©alb".toFourCc(), albumStr.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // #VK-MUSIC-SAVER-PORT: ©gen — жанр из genre_id (свободная строка).
        val genreName = genreName(track.genreId)
        if (genreName != null) {
            out.write(makeTagAtom("©gen".toFourCc(), genreName.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // #VK-MUSIC-SAVER-PORT: covr — обложка (JPEG/PNG).
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val type = when {
                coverBytes.size >= 3 && coverBytes[0] == 0xFF.toByte() && coverBytes[1] == 0xD8.toByte() -> 0x0D // JPEG
                coverBytes.size >= 4 && coverBytes[0] == 0x89.toByte() && coverBytes[1] == 0x50.toByte() -> 0x0E // PNG
                else -> 0 // binary (unknown) — некоторые плееры всё равно покажут
            }
            out.write(makeTagAtom("covr".toFourCc(), coverBytes, dataType = type))
        }
        // ©lyr — lyrics
        if (lyrics != null && lyrics.length >= MIN_TAG_LEN) {
            out.write(makeTagAtom("©lyr".toFourCc(), lyrics.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // cmt — comment (промо)
        if (comment != null && comment.length >= MIN_TAG_LEN) {
            out.write(makeTagAtom("cmt".toFourCc(), comment.toByteArray(Charsets.UTF_8), dataType = 1))
        }
        // ©too — encoder
        out.write(makeTagAtom("©too".toFourCc(), "PinoK ${re.pinok.BuildConfig.VERSION_NAME}".toByteArray(Charsets.UTF_8), dataType = 1))

        val ilstPayload = out.toByteArray()
        if (ilstPayload.isEmpty()) return ByteArray(0)

        // Оборачиваем в ilst-атом: [size:4]['ilst':4][payload]
        return boxAtom("ilst", ilstPayload)
    }

    // ════════════════════════════════════════════════════════════════
    //  Обложка + жанр (#VK-MUSIC-SAVER-PORT)
    // ════════════════════════════════════════════════════════════════

    /** OkHttp-клиент для загрузки обложки (не SovaApp.httpClient — он с interceptor'ами). */
    private val coverClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Максимальный размер обложки — 1MB (больше не нужно для APIC). */
    private const val MAX_COVER_BYTES = 1_048_576

    /**
     * Скачать обложку альбома по URL. Возвращает байты или null (сеть/ошибка).
     * Лучше не иметь обложки, чем упасть на скачивании тегов.
     */
    private suspend fun downloadCover(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return try {
            val req = okhttp3.Request.Builder().url(url).get().build()
            coverClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.size > MAX_COVER_BYTES) null else bytes
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "downloadCover failed: ${e.message}")
            null
        }
    }

    /**
     * #VK-MUSIC-SAVER-PORT: таблица жанров VK (genre_id → строка) — из VKnext.
     */
    private fun genreName(genreId: Int?): String? = when (genreId) {
        1 -> "Rock"
        2 -> "Pop"
        3 -> "Rap & Hip-Hop"
        4 -> "Easy Listening"
        5 -> "Dance & House"
        6 -> "Instrumental"
        7 -> "Metal"
        8 -> "Dubstep"
        10 -> "Drum & Bass"
        11 -> "Trance"
        12 -> "Chanson"
        13 -> "Ethnic"
        14 -> "Acoustic & Vocal"
        15 -> "Reggae"
        16 -> "Classical"
        17 -> "Indie Pop"
        18 -> "Other"
        19 -> "Speech"
        21 -> "Alternative"
        22 -> "Electropop & Disco"
        1001 -> "Jazz & Blues"
        else -> null
    }

    /**
     * Создать один тег-атом внутри ilst:
     *   [size:4][fourcc:4]
     *     [size:4]['data':4][flags:4=type][reserved:4=0][payload:N]
     *
     * @param fourcc   4-байтное имя (например "©nam")
     * @param payload  байты значения (строка в UTF-8 или binary для covr)
     * @param dataType 1 = UTF-8 string, 0x0D = JPEG, 0x0E = PNG, 0 = binary
     */
    private fun makeTagAtom(fourcc: ByteArray, payload: ByteArray, dataType: Int): ByteArray {
        // data-атом: [size:4]['data':4][flags:4][reserved:4=0][payload]
        val dataSize = 8 + 4 + 4 + payload.size // header(8) + flags(4) + reserved(4) + payload
        val dataAtom = ByteArrayOutputStream()
        DataOutputStream(dataAtom).use { dos ->
            dos.writeInt(dataSize)
            dos.write("data".toByteArray(Charsets.US_ASCII))
            dos.writeInt(dataType) // flags: type indicator (1=utf8, 0x0D=jpeg)
            dos.writeInt(0) // reserved (4 bytes of zero)
            dos.write(payload)
        }
        val dataBytes = dataAtom.toByteArray()

        // Тег-атом: [size:4][fourcc:4][data-atom]
        val tagPayload = dataBytes
        val tagAtom = ByteArrayOutputStream()
        DataOutputStream(tagAtom).use { dos ->
            dos.writeInt(8 + tagPayload.size)
            dos.write(fourcc)
            dos.write(tagPayload)
        }
        return tagAtom.toByteArray()
    }

    /** Обернуть payload в box-атом: [size:4][name:4][payload]. */
    private fun boxAtom(name: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeInt(8 + payload.size)
            dos.write(name.toByteArray(Charsets.US_ASCII))
            dos.write(payload)
        }
        return out.toByteArray()
    }

    // ════════════════════════════════════════════════════════════════
    //  Вставка udta в moov
    // ════════════════════════════════════════════════════════════════

    /**
     * Вставить udta-атом (содержащий meta+ilst) внутрь moov-атома .m4a.
     *
     * Стратегия:
     *  1. Парсим top-level boxes, находим moov (запоминаем offset + size).
     *  2. Если внутри moov уже есть udta — заменяем (старый удаляем).
     *  3. Собираем новый udta: [size]['udta'][meta[hdlr, ilst]].
     *  4. Читаем весь moov, модифицируем, пересчитываем размер moov.
     *  5. Перезаписываем файл: ftyp + новый moov + всё остальное.
     *
     * Это безопасно — если что-то пойдёт не так, оригинал не трогаем
     * (работаем через temp-файл, потом atomic rename).
     */
    private fun insertUdtaIntoMoov(m4aFile: File, ilstBytes: ByteArray): Boolean {
        // Собираем udta-атом: udta(meta(hdlr, ilst))
        val hdlrBytes = buildHdlrAtom()
        val metaPayload = ByteArrayOutputStream()
        metaPayload.write(hdlrBytes)
        metaPayload.write(ilstBytes)
        val metaAtom = boxAtom("meta", metaPayload.toByteArray())
        val udtaAtom = boxAtom("udta", metaAtom)

        // Читаем весь файл в память (треки обычно 3-10 MB — ОК).
        // Для больших файлов лучше стримингово, но это P1, не блокер.
        val originalBytes = m4aFile.readBytes()
        val originalSize = originalBytes.size

        // Парсим top-level boxes, ищем moov.
        var offset = 0
        var moovOffset = -1
        var moovSize = 0
        val segments = mutableListOf<Pair<Int, ByteArray>>() // (offset, bytes) остальных atoms

        while (offset + 8 <= originalSize) {
            val size = readUInt32BE(originalBytes, offset)
            if (size < 8 || offset + size > originalSize) break
            val name = String(originalBytes, offset + 4, 4, Charsets.US_ASCII)
            if (name == "moov") {
                moovOffset = offset
                moovSize = size
            } else {
                segments.add(offset to originalBytes.copyOfRange(offset, offset + size))
            }
            offset += size
        }

        if (moovOffset < 0) {
            AppLog.e(TAG, "insertUdtaIntoMoov: no moov box found in ${m4aFile.name}")
            return false
        }

        // Читаем moov, ищем udta внутри (если есть — удаляем).
        val moovBytes = originalBytes.copyOfRange(moovOffset, moovOffset + moovSize)
        val moovContent = ByteArrayOutputStream()
        var moovInner = 8 // пропускаем size+name (8 байт)
        while (moovInner + 8 <= moovBytes.size) {
            val childSize = readUInt32BE(moovBytes, moovInner)
            if (childSize < 8 || moovInner + childSize > moovBytes.size) break
            val childName = String(moovBytes, moovInner + 4, 4, Charsets.US_ASCII)
            if (childName != "udta") {
                moovContent.write(moovBytes, moovInner, childSize)
            }
            moovInner += childSize
        }

        // Добавляем новый udta в конец moov.
        moovContent.write(udtaAtom)
        val newMoovContent = moovContent.toByteArray()
        val newMoovSize = 8 + newMoovContent.size

        // Собираем новый moov: [size]['moov'][content]
        val newMoov = ByteArrayOutputStream()
        DataOutputStream(newMoov).use { dos ->
            dos.writeInt(newMoovSize)
            dos.write("moov".toByteArray(Charsets.US_ASCII))
            dos.write(newMoovContent)
        }
        val newMoovBytes = newMoov.toByteArray()

        // Собираем новый файл: ftyp + moov + mdat + остальное.
        // Порядок важен: ftyp первым, moov вторым (для faststart), mdat потом.
        val outFile = File(m4aFile.parentFile, "${m4aFile.name}.tags.tmp")
        try {
            RandomAccessFile(outFile, "rw").use { raf ->
                // Сначала пишем ftyp (и всё что было ДО moov).
                for ((segOffset, segBytes) in segments) {
                    if (segOffset < moovOffset) {
                        raf.write(segBytes)
                    }
                }
                // Новый moov.
                raf.write(newMoovBytes)
                // Всё что было ПОСЛЕ moov.
                for ((segOffset, segBytes) in segments) {
                    if (segOffset > moovOffset) {
                        raf.write(segBytes)
                    }
                }
            }

            // Atomic rename.
            if (!outFile.renameTo(m4aFile)) {
                outFile.copyTo(m4aFile, overwrite = true)
                outFile.delete()
            }
            AppLog.i(TAG, "insertUdtaIntoMoov: ${m4aFile.name} tagged " +
                "(moov ${moovSize}→${newMoovBytes.size} bytes, +${udtaAtom.size} udta)")
            return true
        } catch (e: Exception) {
            AppLog.e(TAG, "insertUdtaIntoMoov: write failed: ${e.message}")
            outFile.delete()
            return false
        }
    }

    /**
     * Собрать hdlr-атом (обязательный внутри meta в Apple-формате).
     * hdlr = handler reference atom, declares that meta is metadata.
     */
    private fun buildHdlrAtom(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeInt(33) // size: 8 + 25 = 33
            dos.write("hdlr".toByteArray(Charsets.US_ASCII))
            dos.writeInt(0) // version + flags
            dos.writeInt(0) // pre-defined (mhlr, often 0 for iTunes)
            dos.write("mdir".toByteArray(Charsets.US_ASCII)) // handler type
            dos.write("appl".toByteArray(Charsets.US_ASCII)) // manufacturer
            dos.writeInt(0) // component flags
            dos.writeInt(0) // component flags mask
            dos.write(0) // name (empty pascal string)
        }
        return out.toByteArray()
    }

    /** Прочитать 4-байтное big-endian uint из byte array по offset. */
    private fun readUInt32BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    /** Конвертировать 4-символьную строку в 4 байта (fourcc). */
    private fun String.toFourCc(): ByteArray {
        val bytes = ByteArray(4)
        val src = this.toByteArray(Charsets.UTF_8)
        System.arraycopy(src, 0, bytes, 0, minOf(4, src.size))
        return bytes
    }
}
