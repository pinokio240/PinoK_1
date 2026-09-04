package re.pinok.mods.messages

import re.pinok.data.local.SovaPrefs
import re.pinok.data.model.Message
import re.pinok.util.AppLog

/**
 * Message-level mods (DNR/DNT, undelete, unedit).
 *
 * SOVA V RE применял эти моды через JNI hooks в VK message parser.
 * SOVA_2.0 применяет их на уровне Kotlin — после получения сообщений из API.
 *
 * Методы логируют каждое применение мода для отладки.
 */
class MessageMods {

    private val tag = "MessageMods"

    /**
     * Применяет undelete/unedit моды к списку сообщений.
     * Возвращает новый список (immutable).
     */
    fun apply(messages: List<Message>, snapshot: SovaPrefs.Snapshot): List<Message> {
        var result = messages
        var undeleteCount = 0
        var uneditCount = 0

        if (snapshot.msgUndelete) {
            result = result.map { m ->
                if (m.isDeleted) {
                    undeleteCount++
                    m.copy(deleted = 0)
                } else m
            }
            AppLog.d(tag, "undelete applied: restored $undeleteCount messages")
        }

        if (snapshot.msgUnedit) {
            result = result.map { m ->
                // #ARCH-CONTAINERS 3.7-1: originalText в :core:data — захват ДО проверки.
                val originalText = m.originalText
                if (m.isEdited && !originalText.isNullOrBlank()) {
                    uneditCount++
                    m.copy(text = originalText, edited = 0)
                } else m
            }
            AppLog.d(tag, "unedit applied: restored $uneditCount messages")
        }

        return result
    }

    /**
     * DNR (Do Not Read) — подавлять отметку "прочитано".
     * Логируется при каждом вызове для трейсинга.
     */
    fun shouldSuppressRead(snapshot: SovaPrefs.Snapshot): Boolean {
        val suppress = snapshot.msgDnr
        if (suppress) AppLog.d(tag, "DNR active — read receipt suppressed")
        return suppress
    }

    /**
     * DNT (Do Not Type) — подавлять индикатор "печатает".
     */
    fun shouldSuppressTyping(snapshot: SovaPrefs.Snapshot): Boolean {
        val suppress = snapshot.msgDnt
        if (suppress) AppLog.d(tag, "DNT active — typing indicator suppressed")
        return suppress
    }
}
