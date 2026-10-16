package re.pinok.ui.screens.im

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

// #CHAT-APPEARANCE (2026-10-16): профиль внешнего вида диалога (чата/канала),
// независимый от глобального шрифта и темы. Значения null = не переопределять
// (использовать системный цвет/размер). Предоставляется в ChatDetailScreen.
data class ChatAppearance(
    val textColor: Color? = null,
    val fontSizeSp: Float? = null,
    val bgColor: Color? = null,
)

// Дефолт = всё null (переопределение выключено -> системные значения).
val LocalChatAppearance = staticCompositionLocalOf { ChatAppearance() }