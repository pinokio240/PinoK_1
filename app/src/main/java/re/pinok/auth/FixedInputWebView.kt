package re.pinok.auth

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.webkit.WebView

/**
 * WebView с исправленным InputConnection для VK ID React-страниц.
 *
 * **Проблема:** VK ID (id.vk.com/auth) использует React с controlled inputs.
 * Android IME (Gboard, Samsung) шлёт composition events (`setComposingText`),
 * которые React обрабатывает неправильно → курсор сбрасывается в позицию 0
 * → каждый символ печатается в начало → ТЕКСТ ЗЕРКАЛИТСЯ (Pluton240 → 042notulP).
 *
 * **Решение (Fix #75, тройной слой):**
 *
 * **Слой 1 — InputConnection (этот файл):** Перехватываем [InputConnection] и
 * правильно преобразуем `setComposingText` в `deleteSurroundingText` + `commitText`.
 * Предыдущая реализация (#74) просто делала `setComposingText → commitText`,
 * что вызывало ДУБЛИРОВАНИЕ: при вводе "Pl" получалось "PPl" ( composing-текст
 * не удалялся перед вставкой нового). Теперь трекаем длину composing-текста
 * и удаляем старый перед вставкой нового.
 *
 * **Слой 2 — JS cursor fix (VK_2FA_CURSOR_FIX_JS в AuthActivity):**
 * На страницах id.vk.com инжектится JS, который после каждого `input`-события
 * через `setTimeout(0)` + `setTimeout(16)` + `setTimeout(50)` устанавливает
 * `selectionStart = selectionEnd = value.length`. Это перебивает React'овский
 * сброс курсора в позицию 0, потому что setTimeout выполняется ПОСЛЕ
 * React-обработчика (capture phase + microtask scheduling).
 *
 * **Слой 3 — Нативный 2FA оверлей (AuthActivity):**
 * Если слои 1 и 2 не помогли, при обнаружении id.vk.com показывается
 * нативный Compose `OutlinedTextField` поверх WebView. Пользователь вводит
 * код в нативное поле, код инжектится в WebView input через JS и форма
 * сабмитится. Полностью обходит WebView input.
 *
 * Также корректно передаём `deleteSurroundingText` (backspace) и
 * `sendKeyEvent` (Enter, D-pad).
 */
class FixedInputWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val superConn = super.onCreateInputConnection(outAttrs) ?: return null
        return SovaInputConnection(superConn)
    }
}

/**
 * Обёртка над InputConnection с правильной обработкой composing-текста.
 *
 * **Отличие от #74:** Раньше `setComposingText("Pl", 2)` просто транслировалось
 * в `commitText("Pl", 2)`, но предыдущий composing-текст ("P") оставался
 * в поле → результат "PPl" вместо "Pl".
 *
 * Теперь:
 * 1. Трекаем длину предыдущего composing-текста (`composingLength`).
 * 2. При новом `setComposingText`: сначала `deleteSurroundingText(composingLength, 0)`
 *    (удаляем старый composing), потом `commitText(newText, cursor)`.
 * 3. `finishComposingText` → no-op (уже закоммитили) + сброс `composingLength`.
 * 4. `setComposingRegion` → блокируем (не даём IME устанавливать composing region).
 *
 * Пример потока для ввода "Plu":
 * ```
 * setComposingText("P", 1)  → deleteSurroundingText(0,0) + commitText("P",1) → "P"
 * setComposingText("Pl", 2) → deleteSurroundingText(1,0) + commitText("Pl",2) → "Pl"
 * setComposingText("Plu",3) → deleteSurroundingText(2,0) + commitText("Plu",3) → "Plu"
 * finishComposingText()      → composingLength = 0 → "Plu" (final)
 * ```
 */
private class SovaInputConnection(
    target: InputConnection,
) : InputConnectionWrapper(target, true) {

    /** Длина предыдущего composing-текста, которую нужно удалить
     *  перед вставкой нового. */
    private var composingLength = 0

    /**
     * IME вызывает при каждом изменении composing-текста.
     *
     * Шаг 1: Удаляем предыдущий composing-текст (если есть).
     *   `deleteSurroundingText(n, 0)` удаляет n символов ПЕРЕД курсором.
     *   После `commitText("P", 1)` курсор стоит после "P" (позиция 1).
     *   `deleteSurroundingText(1, 0)` удаляет 1 символ перед курсором → "P" удалено.
     *
     * Шаг 2: Коммитим новый текст как финальный (не composing).
     *
     * Шаг 3: Запоминаем длину нового текста для следующего вызова.
     */
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        // Шаг 1: удалить предыдущий composing-текст
        if (composingLength > 0) {
            super.deleteSurroundingText(composingLength, 0)
        }
        // Шаг 2: вставить новый текст (commit, не composing)
        val len = text?.length ?: 0
        composingLength = len
        return super.commitText(text, newCursorPosition)
    }

    /**
     * IME вызывает когда заканчивает composing (Enter, выбор из suggestions).
     * Текст уже закоммичен в setComposingText, поэтому:
     * - Сбрасываем composingLength
     * - НЕ вызываем super.finishComposingText() — это вызовет двойной commit
     */
    override fun finishComposingText(): Boolean {
        composingLength = 0
        return true
    }

    /**
     * Блокируем установку composing region — IME иногда пытается
     * выделить часть текста как composing. Мы этого не хотим.
     */
    override fun setComposingRegion(start: Int, end: Int): Boolean {
        // Ничего не делаем — не даём IME управлять composing region
        return true
    }

    /**
     * При ручном удалении (backspace) — сбрасываем composingLength,
     * потому что backspace обрабатывается через deleteSurroundingText(1, 0),
     * а не через setComposingText.
     */
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        // Если пользователь нажал backspace во время composing —
        // composing region больше не актуальна
        if (beforeLength > 0) {
            composingLength = 0
        }
        return super.deleteSurroundingText(beforeLength, afterLength)
    }
}