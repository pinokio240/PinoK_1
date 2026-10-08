package re.pinok.ui.theme

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier

/**
 * Единый IME-подъём для поверхностей, смежных с клавиатурой.
 *
 * Единственный штатный источник ime-подъёма в приложении — внешний
 * `NavHost.imePadding()` (SovaNavHost.kt). Он поднимает ВЕСЬ NavHost-контент,
 * но НЕ достигает поверхностей вне его zone-действия: отдельные окна
 * (AlertDialog) и оверлеи (правая панель поиска). Именно там поля ввода
 * оказываются перекрыты клавиатурой, а между ними и клавиатурой остаётся
 * «полоса» фона.
 *
 * Этот хелпер — ЕДИНАЯ точка ime-подъёма для таких поверхностей. Все места
 * с поисковыми полями вне NavHost используют его вместо разрозненных
 * inline-паттернов. Внутри NavHost-контента применять не нужно: там подъём
 * уже даёт внешний NavHost.imePadding (избыточный вложенный imePadding по
 * модели consumed-insets композером игнорируется как no-op).
 */
fun Modifier.appImePadding(): Modifier = imePadding()