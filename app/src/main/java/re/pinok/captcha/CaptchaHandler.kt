// File: captcha/CaptchaHandler.kt
package re.pinok.captcha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import re.pinok.util.AppLog

/**
 * Sprint 1, P0-3 (#76): Прозрачная обработка VK Captcha (error 14).
 *
 * VK API требует captcha при подозрительной активности (массовые запросы,
 * повторяющиеся действия, новый IP). Ответ error 14 содержит:
 *  - `captcha_sid` — идентификатор сессии капчи
 *  - `captcha_img` — URL картинки капчи (4-5 символов)
 *
 * Архитектура:
 *  1. [VKApiClient.call] при error 14 вызывает [CaptchaHandler.solve] с sid+img.
 *  2. [UiCaptchaHandler] устанавливает [_challenge] (StateFlow) → UI (CaptchaDialog)
 *     подписан и показывает диалог с картинкой + полем ввода.
 *  3. Пользователь вводит key → [submit(key)] → `CompletableDeferred.complete(key)`
 *     → `solve()` возвращает key в VKApiClient → retry call с captcha_sid+captcha_key.
 *  4. Если пользователь отменяет → [cancel()] → `solve()` возвращает null →
 *     VKApiClient отменяет запрос (return null).
 *
 * Это прозрачно для callers VKApiClient — они просто вызывают API, captcha
 * обрабатывается автоматически с UI-диалогом.
 *
 * Защита от бесконечной рекурсии: если VK снова вернёт error 14 с тем же sid
 * (неверная captcha) — UI покажет НОВУЮ картинку (VK генерирует новый sid при
 * каждой error 14). VKApiClient ограничивает retries через `captchaRetries`
 * счётчик (default 3) — после исчерпания возвращает null.
 */
interface CaptchaHandler {
    /**
     * Показать captcha UI и дождаться ввода пользователя.
     *
     * @param sid — captcha_sid из error 14.
     * @param img — URL картинки captcha (captcha_img из error 14).
     * @return введённый key, или null если пользователь отменил.
     */
    suspend fun solve(sid: String, img: String): String?
}

/**
 * Текущий запрос captcha (или null если нет активной).
 * UI подписывается на [UiCaptchaHandler.challenge] и показывает диалог когда
 * challenge != null.
 */
data class CaptchaChallenge(
    val sid: String,
    val img: String,
    private val deferred: CompletableDeferred<String?>,
) {
    /** Ввести ответ пользователя. Completes [deferred] с [key]. */
    fun submit(key: String) {
        try { deferred.complete(key) } catch (_: IllegalStateException) {}
    }

    /** Отменить. Completes [deferred] с null. */
    fun cancel() {
        try { deferred.complete(null) } catch (_: IllegalStateException) {}
    }
}

/**
 * UI-driven реализация [CaptchaHandler]. Показывает диалог через [challenge]
 * StateFlow, на который подписан [re.pinok.ui.components.CaptchaDialog].
 *
 * Поток:
 *  - `solve(sid, img)` создаёт `CaptchaChallenge` с `CompletableDeferred`,
 *    устанавливает в `_challenge`, и `await()`-ит deferred.
 *  - UI вызывает `challenge.submit(key)` или `challenge.cancel()`.
 *  - `solve` возвращает key или null.
 *
 * Thread-safe: `_challenge` это `MutableStateFlow`, `submit/cancel` через
 * `CompletableDeferred` (thread-safe). Если UI не успел подписаться —
 * StateFlow хранит последнее значение, UI покажет диалог при подписке.
 */
class UiCaptchaHandler : CaptchaHandler {
    private val _challenge = MutableStateFlow<CaptchaChallenge?>(null)
    val challenge: StateFlow<CaptchaChallenge?> = _challenge.asStateFlow()

    override suspend fun solve(sid: String, img: String): String? {
        // If a captcha is already being solved, fail fast
        if (_challenge.value != null) return null
        AppLog.i(TAG, "solve: showing captcha dialog (sid=${sid.take(8)}…)")
        val deferred = CompletableDeferred<String?>()
        val challenge = CaptchaChallenge(sid = sid, img = img, deferred = deferred)
        _challenge.value = challenge
        return try {
            deferred.await()
        } finally {
            // Очищаем challenge только если он всё ещё наш (не перетёрт новым).
            if (_challenge.value === challenge) {
                _challenge.value = null
            }
        }
    }

    companion object {
        private const val TAG = "UiCaptchaHandler"
    }
}
