// File: api/VKScript.kt
package re.pinok.api

/**
 * P4.4: VKScript builder — безопасная генерация VKScript для `execute` endpoint.
 *
 * VKScript — это JS-подобный язык VK API (см. VK_IMPORT_API.MD §23.8):
 *  - Поддерживает `var`, `if`, `for`, `while`, арифметику, логику.
 *  - Вызывает VK API через `API.<method>(<args>)`.
 *  - До 25 методов на один `execute`.
 *  - `return` возвращает результат (любой JSON-совместимый тип).
 *
 * ## Пример
 * ```kotlin
 * val script = VKScript.build {
 *     line("var msg = API.messages.getById({message_ids: $msgId});")
 *     line("var user = API.users.get({user_ids: msg.items[0].user_id});")
 *     line("return { message: msg, user: user };")
 * }
 * val resp = apiClient.execute(script)
 * ```
 *
 * ## Ограничения (см. VK_IMPORT_API.MD §23.6 executeUnsupportedMethods)
 * Через execute НЕЛЬЗЯ вызывать:
 *  - `photos.save*` (включая `saveMessagesPhoto`)
 *  - `docs.save`, `audio.save`
 *  - `messages.setChatPhoto`
 *  - `stories.save`, `polls.savePhoto`
 *
 * Для неподдерживаемых методов — используйте прямой вызов `apiClient.photosSaveMessagePhoto()`
 * и т.д. [VKScript.unsupportedMethods] хранит список для runtime-проверки.
 */
object VKScript {

    /**
     * Методы, которые VK НЕ позволяет вызывать через `execute` (см. §23.6).
     * Используется [checkSupported] для ранней валидации скрипта.
     */
    val unsupportedMethods = setOf(
        "photos.save",
        "photos.saveWallPhoto",
        "photos.saveOwnerPhoto",
        "photos.saveMessagesPhoto",
        "messages.setChatPhoto",
        "photos.saveMarketPhoto",
        "photos.saveMarketAlbumPhoto",
        "audio.save",
        "docs.save",
        "photos.saveOwnerCoverPhoto",
        "stories.save",
        "polls.savePhoto",
    )

    /**
     * Минимальный builder — собирает скрипт из строк с отступами.
     * Не делает синтаксическую проверку (VKScript — не Kotlin, парсить его здесь избыточно),
     * но обеспечивает консистентное форматирование и удобный API.
     */
    class Builder {
        private val sb = StringBuilder()

        /** Добавляет строку скрипта (без перевода строки в конце — добавляется автоматически). */
        fun line(s: String): Builder {
            sb.append(s).append('\n')
            return this
        }

        /** Добавляет пустую строку (для читаемости). */
        fun blank(): Builder {
            sb.append('\n')
            return this
        }

        /** Возвращает итоговый VKScript. */
        fun build(): String = sb.toString().trimEnd()
    }

    /** Собирает скрипт через DSL-лямбду. */
    fun build(block: Builder.() -> Unit): String = Builder().apply(block).build()

    /**
     * Экранирует строку для встраивания в VKScript как JS-строковый литерал.
     * Заменяет: `\` → `\\`, `"` → `\"`, переводы строк → `\n`.
     *
     * Пример: `escapeStr("hello \"world\"")` → `hello \"world\"`
     * Использование в скрипте: `var s = "${escapeStr(userInput)}";`
     */
    fun escapeStr(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * Проверяет, что скрипт не содержит прямых вызовов неподдерживаемых методов.
     * Возвращает список найденных нарушений (пустой список = OK).
     *
     * ⚠️ Простая строковая проверка — может дать false positive (например, в комментарии
     * или строковом литерале). Для production лучше использовать regex, но для sanity-check
     * при разработке этого достаточно.
     */
    fun checkSupported(script: String): List<String> {
        val violations = mutableListOf<String>()
        for (method in unsupportedMethods) {
            // Ищем "API.method(" или "method(" в скрипте
            if (script.contains("API.$method(") || script.contains(".$method(")) {
                violations.add(method)
            }
        }
        return violations
    }
}
