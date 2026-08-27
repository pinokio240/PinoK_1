package re.pinok.auth

/**
 * Phone number formatter for the Russian/CIS phone format used by VK.
 *
 * Normalises user input into the canonical VK submission format `+<digits>`
 * (e.g. `+79991234567`) while exposing a pretty display form
 * `+7 (999) 123-45-67` for the text field.
 *
 * Usage:
 * ```kotlin
 * var raw by rememberSaveable { mutableStateOf("") }
 * OutlinedTextField(
 *     value = PhoneFormatter.format(raw),
 *     onValueChange = { raw = PhoneFormatter.parse(it) },
 *     ...
 * )
 * // On submit:
 * val phoneForApi = PhoneFormatter.toApiForm(raw)  // "+79991234567"
 * ```
 *
 * Rules:
 *  - Leading `8` is rewritten to `7` (Russian trunk prefix).
 *  - Leading `+` is stripped from raw, kept in display.
 *  - Country code defaults to 7 if the user typed 10 digits without one.
 *  - Non-digit characters in input are filtered out before parsing.
 *  - Output is clamped to 11 digits (country code + 10 digits).
 */
object PhoneFormatter {

    private const val MAX_DIGITS = 11

    /** Pretty form for display: `+7 (999) 123-45-67`. */
    fun format(rawDigits: String): String {
        val digits = sanitize(rawDigits)
        if (digits.isEmpty()) return ""
        return when (digits.length) {
            1    -> "+$digits"
            2    -> "+${digits[0]} (${digits[1]}"
            3    -> "+${digits[0]} (${digits.substring(1)}"
            4, 5, 6 -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4)}"
            7    -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits[6]}"
            8    -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 8)}"
            9    -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits[8]}"
            10   -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9, 10)}"
            else -> "+${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9, 11)}"
        }
    }

    /**
     * Parses a display string (or raw digits) back into the canonical raw
     * digits form. Replaces leading `8` with `7`. Returns up to 11 digits.
     */
    fun parse(input: String): String {
        var digits = sanitize(input)
        if (digits.isEmpty()) return ""
        // Replace Russian trunk prefix 8 with country code 7.
        // Работает для любой длины ≥ 10 (пользователь может ещё не
        // допечатал номер, но 8 уже нужно заменить на 7).
        if (digits.length >= 10 && digits.startsWith('8')) {
            digits = "7" + digits.substring(1)
        }
        // If user typed 10 digits without country code, prepend 7.
        if (digits.length == 10 && !digits.startsWith('7')) {
            digits = "7$digits"
        }
        return digits.take(MAX_DIGITS)
    }

    /** Returns `+<digits>` form for VK API submission, or null if invalid. */
    fun toApiForm(rawDigits: String): String? {
        val digits = parse(rawDigits)
        if (digits.length < 11) return null
        return "+$digits"
    }

    /** True if the raw digits form a complete phone (11 digits, starts with 7). */
    fun isComplete(rawDigits: String): Boolean {
        val digits = parse(rawDigits)
        return digits.length == MAX_DIGITS && digits.startsWith('7')
    }

    /** Strips everything except digits from the input. */
    private fun sanitize(input: String): String = input.filter { it.isDigit() }
}

/**
 * 2FA code formatter — adds a space separator every 3 digits for readability
 * (`123456` -> `123 456`). Used by [ValidationCodeForm].
 *
 * Codes from VK are typically 6 digits (SMS) or 4-6 chars (push approval code).
 * We allow up to 8 chars total and strip non-alphanumerics before formatting.
 */
object CodeFormatter {

    private const val GROUP_SIZE = 3
    private const val MAX_CHARS = 8

    /** Pretty form with space-separated groups: `123 456`. */
    fun format(raw: String): String {
        val clean = sanitize(raw).take(MAX_CHARS)
        if (clean.isEmpty()) return ""
        return clean.chunked(GROUP_SIZE).joinToString(" ")
    }

    /** Strips spaces / non-alphanumerics — returns the raw code for API submission. */
    fun parse(input: String): String =
        sanitize(input).take(MAX_CHARS)

    private fun sanitize(input: String): String =
        input.filter { it.isLetterOrDigit() }
}
