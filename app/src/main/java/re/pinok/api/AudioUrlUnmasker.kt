package re.pinok.api

import re.pinok.util.AppLog

/**
 * #AUDIO-UNMASK (2026-08-01, P0 #1 из VK_IMPORT_API.MD §42.3):
 *
 * VK возвращает audio URL в виде `audio_api_unavailable?extra=...#...` — обфусцированный.
 * Это происходит для части треков (geo-restricted, copyright-protected, или просто
 * устаревший access_key). Без расшифровки URL — трек не воспроизводится и не скачивается.
 *
 * Функция — точный порт `audioUnmaskSource` (R-функция модуля 9141) из расширения
 * VK Music Saver v2.10.1 (`js/8669.vms.js:1080-1124`).
 *
 * ## Алгоритм (из реверса VKNext):
 *
 * 1. Если URL НЕ содержит `audio_api_unavailable` — вернуть как есть (уже расшифрован).
 * 2. Split по `?extra=` → берём часть после.
 * 3. Split по `#` → `[0]` = encoded ops chain, `[1]` = encoded data.
 * 4. base64-decode обе части через CUSTOM alphabet:
 *    `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/=`
 *    (внимание: `O` на позиции 41, `.` заменён на `O` — нестандартный base64).
 * 5. data split по `\t` (0x09) → массив ops.
 * 6. Каждый op split по `\v` (0x0B) → `[0]` = op name, `[1:]` = args.
 * 7. Применить ops в ОБРАТНОМ порядке (LIFO), начиная с decoded opsEncoded как seed.
 * 8. Если результат начинается с `http` — это финальный URL.
 *
 * ## 5 операций (из JS объекта `t`):
 *
 * - `v(str)` — reverse строки.
 * - `r(str, n)` — caesar-shift по custom alphabet на `n` (bi-directional, doubled alphabet).
 * - `s(str, seed)` — BigInt-seeded permutation: генерит массив перестановок,
 *   потом splice-rotate.
 * - `i(str, seed)` — `s(str, seed XOR vk.id)` (использует ID залогиненного юзера).
 * - `x(str, key)` — per-char XOR с charCode первого символа key.
 *
 * ## `vk.id` — это userId (числовой ID залогиненного пользователя).
 *
 * В браузере `vk.id` хранится в глобальном объекте `window.vk`.
 * В нашем Android приложении — `ExchangeAuthRepository.userId()`.
 *
 * ⚠️ БЕЗ `vk.id` op `i` нерасшифровываем. Если userId неизвестен — возвращаем
 * исходный URL (трек не проиграет, но это лучше чем мусор).
 *
 * ## Тестирование
 *
 * Пример из лога 2026-08-01 19:49:04: track #456249594, URL был обфусцирован.
 * После unmask должен стать `https://ps.vk4.me/c.../audio/.../index.m3u8?siren=1`.
 *
 * Reference: `/home/z/my-project/upload/vknext_extracted/pretty/8669.js:1080-1124`
 * (beautified copy оригинального JS).
 */
object AudioUrlUnmasker {

    private const val TAG = "AudioUrlUnmasker"

    /**
     * Custom base64 alphabet из JS: `"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/="`.
     *
     * ⚠️ Отличия от стандартного base64 (RFC 4648):
     * - Стандартный:   `ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz0123456789+/`
     * - VK custom:     `abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/=`
     *
     * То есть:
     * - lower-case FIRST (в стандартном — upper-case first)
     * - `0` на позиции 30 (в стандартном — `0` на позиции 52)
     * - `O` (буква) на позиции 41 (в стандартном там `p`)
     * - `=` (padding) включён в alphabet (в стандартном — не part of alphabet, только padding)
     */
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/="

    /**
     * Расшифровать VK audio URL.
     *
     * @param url исходный URL (может быть уже расшифрован — тогда вернётся как есть)
     * @param userId ID залогинённого пользователя (для op `i`). 0 = не известно.
     * @return расшифрованный HTTPS URL, или исходный url если не удалось расшифровать
     *         (никогда не возвращает null если url != null).
     */
    fun unmask(url: String?, userId: Long): String? {
        if (url.isNullOrBlank()) return url
        // Если URL не содержит маркер обфускации — он уже расшифрован, вернуть как есть.
        if (!url.contains("audio_api_unavailable")) return url

        try {
            val extraPart = url.substringAfter("?extra=", "")
            if (extraPart.isEmpty()) {
                AppLog.w(TAG, "unmask: contains 'audio_api_unavailable' but no '?extra=' — return as-is")
                return url
            }

            // Split по '#' — ровно 2 части: [0]=opsEncoded, [1]=dataEncoded.
            // В JS: e.split("?extra=")[1].split("#")
            val hashParts = extraPart.split("#")
            if (hashParts.size < 2) {
                AppLog.w(TAG, "unmask: no '#' separator after ?extra= — return as-is")
                return url
            }

            val opsEncoded = hashParts[0]
            val dataEncoded = hashParts[1]

            // base64-decode обе части.
            // В JS: r = n(r[0]), i = "" === r[1] ? "" : n(r[1])
            val decodedOpsRaw: String? = base64Decode(opsEncoded)
            if (decodedOpsRaw == null) {
                AppLog.w(TAG, "unmask: base64 decode opsEncoded failed (len=${opsEncoded.length}) — return as-is")
                return url
            }
            val decodedOps: String = decodedOpsRaw
            val decodedData: String = if (dataEncoded.isEmpty()) {
                ""
            } else {
                val dataRaw: String? = base64Decode(dataEncoded)
                if (dataRaw == null) {
                    AppLog.w(TAG, "unmask: base64 decode dataEncoded failed (len=${dataEncoded.length}) — return as-is")
                    return url
                }
                dataRaw
            }

            // JS: if ("string" != typeof i || !r) return e;
            // i — это string (у нас всегда), r (decodedOps) должен быть непустым.
            if (decodedOps.isEmpty()) {
                AppLog.w(TAG, "unmask: decodedOps is empty — return as-is")
                return url
            }

            // JS: i = i ? i.split(String.fromCharCode(9)) : []
            // \t = 0x09 (String.fromCharCode(9))
            val opList: List<String> = if (decodedData.isEmpty()) emptyList() else decodedData.split('\t')

            // JS цикл: for (var o, a, s = (i = ...).length; s--;) { ... }
            // Идём с конца (s-- = post-decrement, проверка s!=0, потом использование).
            // На каждой итерации: a = opStr.split(\v), splice(0,1,r) — заменяем opName
            // на текущий result, вызываем t[opName](result, ...args).
            var result: String = decodedOps

            for (opStr in opList.asReversed()) {
                // \v = 0x0B (String.fromCharCode(11))
                val a: List<String> = opStr.split('\u000B')
                if (a.isEmpty()) {
                    AppLog.w(TAG, "unmask: empty op (split by \\v) — return current result")
                    return url
                }
                val opName = a[0]
                val args = a.drop(1)

                val fn = OPS[opName]
                if (fn == null) {
                    AppLog.w(TAG, "unmask: unknown op '$opName' — return as-is (known: ${OPS.keys})")
                    return url
                }

                // JS: t[o].apply(null, a) — вызов с args[0]=result (после splice),
                // args[1..]=аргументы из data.
                result = try {
                    fn(result, args, userId)
                } catch (e: Exception) {
                    AppLog.w(TAG, "unmask: op '$opName' threw ${e.javaClass.simpleName}: ${e.message} — return as-is")
                    return url
                }

                // #LOGCAT-NOISE-FIX: per-op intermediate log is VERBOSE (was DEBUG).
                // Fires per op per URL (~250 lines/audio page). Now suppressed in
                // release logcat by default — available via verboseToLogcat toggle
                // or in-app LogViewer (buffer keeps everything).
                if (result.length < 200) {
                    AppLog.v(TAG, "unmask: op '$opName' → result prefix='${result.take(60)}...' (len=${result.length})")
                }
            }

            // JS: if (r && "http" === r.substr(0, 4)) return r
            // #LOGCAT-NOISE-FIX: SUCCESS log is VERBOSE (was INFO). Fires per URL.
            return if (result.startsWith("http")) {
                AppLog.v(TAG, "unmask: SUCCESS — decoded URL prefix='${result.take(80)}...'")
                result
            } else {
                AppLog.w(TAG, "unmask: final result doesn't start with 'http' (got prefix='${result.take(40)}') — return as-is")
                url
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "unmask: unexpected ${e.javaClass.simpleName}: ${e.message}", e)
            return url
        }
    }

    // ─── 5 операций (точный порт JS объекта `t`) ────────────────────────────

    /**
     * Карта операций: opName → (currentResult, args, userId) → newResult.
     *
     * JS: `t = { v: ..., r: ..., s: ..., i: ..., x: ... }`
     */
    private val OPS: Map<String, (String, List<String>, Long) -> String> = mapOf(
        "v" to { s, _, _ -> opV(s) },
        "r" to { s, args, _ -> opR(s, args) },
        "s" to { s, args, _ -> opS(s, args) },
        "i" to { s, args, uid -> opI(s, args, uid) },
        "x" to { s, args, _ -> opX(s, args) }
    )

    /**
     * `v: e => e.split("").reverse().join("")` — реверс строки.
     */
    private fun opV(s: String): String = s.reversed()

    /**
     * `r: (e, t) => { e = e.split(""); for (let n, o = r + r, a = e.length; a--;) ~(n = o.indexOf(e[a])) && (e[a] = o.substr(n - t, 1)); return e.join("") }`
     *
     * Caesar-shift по custom alphabet. `t` — аргумент из data (строка-число).
     *
     * - `o = ALPHABET + ALPHABET` (двойной, для возможности `n - t` быть отрицательным)
     * - для каждого символа `e[a]` (с конца):
     *   - найти позицию `n` в `o`
     *   - если найден (n != -1): заменить на `o[n - t]`
     *
     * `t` парсится как Int из строки. Если не число — JS даёт NaN, `(n - NaN) = NaN`,
     * `o.substr(NaN, 1) = ""` (пустая строка). У нас в Kotlin — fallback на 0.
     */
    private fun opR(s: String, args: List<String>): String {
        val firstArg: String? = args.firstOrNull()
        val shift: Int = if (firstArg != null) {
            val parsed: Int? = firstArg.toIntOrNull()
            if (parsed != null) parsed else 0
        } else 0
        val doubled = ALPHABET + ALPHABET
        val sb = StringBuilder(s.length)
        // JS идёт с конца (a--), но порядок не важен — каждый символ меняется независимо.
        for (c in s) {
            val idx = doubled.indexOf(c)
            if (idx >= 0) {
                // o.substr(n - t, 1) — один символ с позиции (n - t).
                // В doubled (length = 2 * ALPHABET.length = 130) это всегда валидный индекс
                // для shift в разумных пределах (|shift| < ALPHABET.length).
                val newIdx = idx - shift
                if (newIdx in doubled.indices) {
                    sb.append(doubled[newIdx])
                } else {
                    // За пределами doubled — в JS substr вернул бы "". Пропускаем символ.
                    sb.append(c)
                }
            } else {
                // Символ не из alphabet — оставляем как есть (в JS тоже не меняется).
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * `s: (e, t) => { ... permutation с BigInt-seeded shuffle ... }`
     *
     * 1. Генерим массив перестановок `n` длины `r = e.length`:
     *    ```
     *    t = Math.abs(t)
     *    for (e = r; e--;)  // e от r-1 до 0
     *        t = (r * (e + 1) ^ (t + e)) % r
     *        n[e] = t
     *    ```
     * 2. Применяем перестановку (reverse-rotate):
     *    ```
     *    for (o = 0; ++o < r;)  // o от 1 до r-1
     *        e[o] = e.splice(n[r - 1 - o], 1, e[o])[0]
     *    ```
     *    `splice(idx, 1, val)` — заменяет элемент на позиции `idx` на `val`,
     *    возвращает массив со старым элементом. `[0]` — старый элемент.
     *    Итого: tmp = e[idx]; e[idx] = e[o]; e[o] = tmp; (но splice мутирует массив).
     *
     * `t` парсится как Int из args[0].
     */
    private fun opS(s: String, args: List<String>): String {
        if (s.isEmpty()) return s
        val firstArg: String? = args.firstOrNull()
        val seedIn: Int = if (firstArg != null) {
            val parsed: Int? = firstArg.toIntOrNull()
            if (parsed != null) parsed else 0
        } else 0
        return permuteInternal(s, seedIn)
    }

    /**
     * `i: (e, r) => t.s(e, r ^ vk.id)` — то же что `s`, но seed XOR с userId.
     *
     * В JS: `r` это args[0] (строка-число), `vk.id` это число.
     * JS `r ^ vk.id` конвертирует оба в Int32, потом XOR.
     * У нас: args[0].toInt() xor userId.toInt() (toInt() для эмуляции Int32).
     */
    private fun opI(s: String, args: List<String>, userId: Long): String {
        if (s.isEmpty()) return s
        val firstArg: String? = args.firstOrNull()
        val argSeed: Int = if (firstArg != null) {
            val parsed: Int? = firstArg.toIntOrNull()
            if (parsed != null) parsed else 0
        } else 0
        // JS: r ^ vk.id — оба как Int32. userId Long → toInt() даёт Int32 (с потерей старших бит).
        val userId32: Int = userId.toInt()
        val effectiveSeed: Int = argSeed xor userId32
        return permuteInternal(s, effectiveSeed)
    }

    /**
     * `x: (e, t) => { const r = []; return t = t.charCodeAt(0), each(e.split(""), (e, n) => { r.push(String.fromCharCode(n.charCodeAt(0) ^ t)) }), r.join("") }`
     *
     * Per-char XOR с charCode первого символа `t` (args[0]).
     *
     * ⚠️ В JS `each` — это polyfill для forEach, в callback `(e, n)` где `e` — элемент,
     * `n` — тоже элемент (НЕ индекс!). В коде используется `n.charCodeAt(0)` — то есть
     * для каждого символа в строке `e` берём его charCode и XOR с `t`.
     *
     * Здесь `e` (из callback) игнорируется — это правильное поведение.
     */
    private fun opX(s: String, args: List<String>): String {
        val keyStrRaw: String? = args.firstOrNull()
        if (keyStrRaw == null || keyStrRaw.isEmpty()) return s
        val keyStr: String = keyStrRaw
        val key: Int = keyStr[0].code  // charCodeAt(0)
        val sb = StringBuilder(s.length)
        for (c in s) {
            // JS: String.fromCharCode(n.charCodeAt(0) ^ t)
            // n.charCodeAt(0) — код символа (0..65535 для UTF-16).
            // t — Int32 (от charCodeAt). XOR даёт Int32.
            // String.fromCharCode принимает Int и берёт младшие 16 бит.
            val xored: Int = c.code xor key
            sb.append(xored.toChar())
        }
        return sb.toString()
    }

    // ─── Вспомогательные: permute (общий для s и i) ─────────────────────────

    /**
     * Внутренняя реализация permutation для op `s` и `i`.
     *
     * @param s строка для перестановки
     * @param seedIn seed (для `i` уже XOR с userId)
     */
    private fun permuteInternal(s: String, seedIn: Int): String {
        val r: Int = s.length
        if (r == 0) return s

        // JS: t = Math.abs(t) — берём модуль.
        var t: Int = Math.abs(seedIn)

        // Генерация массива перестановок n длины r:
        // for (e = r; e--;)  → e от r-1 до 0
        //   t = (r * (e + 1) ^ (t + e)) % r
        //   n[e] = t
        //
        // ⚠️ JS побитовые операции конвертируют операнды в Int32 (signed).
        // `r * (e + 1)` — это Number multiplication, но потом `^` конвертит в Int32.
        // В Kotlin: `((r * (e + 1)) xor (t + e)) % r` — все Int, эквивалентно.
        val n = IntArray(r)
        for (e in r - 1 downTo 0) {
            val term: Int = (r * (e + 1)) xor (t + e)
            t = term % r
            n[e] = t
        }

        // Применение перестановки:
        // JS: e = e.split("") → массив символов
        //     for (o = 0; ++o < r;)  → o от 1 до r-1
        //         e[o] = e.splice(n[r - 1 - o], 1, e[o])[0]
        //
        // splice(idx, 1, val): удаляет 1 элемент на позиции idx, вставляет val.
        // Возвращает массив удалённых элементов (1 шт). [0] — старый элемент.
        //
        // Итого: tmp = e[idx]; e[idx] = e[o]; e[o] = tmp;
        // (но splice мутирует массив — порядок последующих операций зависит от этого).
        //
        // В Kotlin мутабельный список символов:
        val arr: MutableList<Char> = s.toMutableList()
        for (o in 1 until r) {
            val idx: Int = n[r - 1 - o]
            if (idx !in arr.indices) continue  // защита (в JS splice молча пропустит)
            // swap arr[idx] и arr[o]:
            val tmp: Char = arr[idx]
            arr[idx] = arr[o]
            arr[o] = tmp
        }
        return arr.joinToString("")
    }

    // ─── Custom base64 decoder ──────────────────────────────────────────────

    /**
     * Декодер base64 с custom alphabet (см. [ALPHABET]).
     *
     * Порт JS функции `n(e)`:
     * ```js
     * const n = e => {
     *   if (!e || e.length % 4 == 1) return !1;
     *   for (var t, n, o = 0, a = 0, s = ""; n = e.charAt(a++);)
     *     ~(n = r.indexOf(n)) && (
     *       t = o % 4 ? 64 * t + n : n,
     *       o++ % 4 && (s += String.fromCharCode(255 & t >> (-2 * o & 6)))
     *     );
     *   return s
     * }
     * ```
     *
     * Возвращает String Latin-1 (символы 0..255). Может содержать управляющие
     * символы (0x09, 0x0B) — это нормально, они нужны для split в [unmask].
     *
     * @return декодированная строка, или null если вход невалиден (length % 4 == 1)
     */
    private fun base64Decode(input: String): String? {
        if (input.isEmpty()) return ""
        // JS: if (e.length % 4 == 1) return !1 (false)
        if (input.length % 4 == 1) return null

        val sb = StringBuilder()
        var t: Int = 0  // аккумулятор
        var o: Int = 0  // счётчик обработанных валидных символов (0,1,2,3,0,1,...)

        for (ch in input) {
            val n: Int = ALPHABET.indexOf(ch)
            // JS: ~(n = r.indexOf(e.charAt(a++))) — если n == -1, ~(-1) = 0, falsy → skip
            if (n < 0) continue

            // JS: t = o % 4 ? 64 * t + n : n
            // Если o кратно 4 (o % 4 == 0) — начинаем новую группу, t = n.
            // Иначе — накапливаем: t = 64 * t + n.
            t = if (o % 4 != 0) 64 * t + n else n

            // JS: o++ % 4 && (s += String.fromCharCode(255 & t >> (-2 * o & 6)))
            // o++ — post-increment: возвращает OLD value, потом o становится OLD+1.
            // Проверка: old_o % 4 != 0 → добавить символ.
            // После инкремента o = NEW value. Сдвиг: -2 * NEW_o & 6.
            val oldO: Int = o
            o += 1

            if (oldO % 4 != 0) {
                // Вычислить сдвиг: -2 * o (new) & 6.
                // JS побитовые операции на Int32. -2 * o в Int32 = -(2 * o).
                // & 6 — оставляет только биты 0x2 и 0x4.
                //
                // Таблица (new_o после инкремента):
                //   oldO=0 → new_o=1 → shift = -2 & 6 = 6   (но не append, т.к. oldO%4==0)
                //   oldO=1 → new_o=2 → shift = -4 & 6 = 4
                //   oldO=2 → new_o=3 → shift = -6 & 6 = 2
                //   oldO=3 → new_o=4 → shift = -8 & 6 = 0
                val shift: Int = (-2 * o) and 6
                // JS: 255 & t >> shift  → берём младшие 8 бит от (t >> shift).
                // В Kotlin: t shr shift and 0xFF
                val byte: Int = (t shr shift) and 0xFF
                sb.append(byte.toChar())
            }
        }

        return sb.toString()
    }
}
