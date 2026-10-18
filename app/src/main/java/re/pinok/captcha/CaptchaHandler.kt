// File: captcha/CaptchaHandler.kt
package re.pinok.captcha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import re.pinok.util.AppLog

/**
 * Sprint 1, P0-3 (#76) + #CAPTCHA-V2 (2026-10-17): обработка VK Captcha (error 14).
 *
 * У VK две схемы капчи:
 *  1. КЛАССИЧЕСКАЯ (устаревшая, но в fallback): `captcha_sid` + `captcha_img` —
 *     пользователь видит картинку и вводит текст → `captcha_key`. Обрабатывается
 *     через [CaptchaHandler.solve] (метод ниже).
 *  2. НОВАЯ VK ID: error 14 возвращает `redirect_uri` (URL сессии капчи,
 *     https://id.vk.com/not_robot_captcha?...session_token=...). Пользователь
 *     проходит капчу в WebView, VK редиректит на blank.html#success_token=...,
 *     полученный `success_token` подставляется в повторный запрос (вместо captcha_key).
 *     Обрабатывается через [CaptchaHandler.solveRedirect] (см. ниже).
 *
 * Архитектура:
 *  1. [VKApiClient.call] при error 14 выбирает схему по наличию redirect_uri vs captcha_img.
 *  2. [UiCaptchaHandler] ставит [_challenge] (StateFlow) → UI (CaptchaDialog) показывает диалог:
 *     - classic: картинка + поле ввода;
 *     - v2: WebView с redirect_uri, после прохождения перехватывается success_token.
 *  3. Ввод → [submit]/[submitSuccessToken] → CompletableDeferred → solve() возвращает
 *     key/success_token в VKApiClient → retry.
 *  4. Отмена → [cancel()] → null → запрос отменяется.
 */
interface CaptchaHandler {
    /**
     * Классическая схема: показать картинку и получить введённый текст.
     * @return введённый key, или null если пользователь отменил.
     */
    suspend fun solve(sid: String, img: String): String?

    /**
     * Новая VK ID схема: открыть WebView по redirect_uri, дождаться прохождения капчи,
     * вернуть success_token (или null при отмене/ошибке).
     * Дефолт: null (не поддерживает новую схему — классический fallback).
     */
    suspend fun solveRedirect(redirectUri: String): String? = null
}

/**
 * Текущий запрос captcha (или null если нет активной).
 * Может представлять classic (sid+img) или v2 (redirectUri) капчу.
 */
data class CaptchaChallenge(
    val sid: String?,
    val img: String?,
    val redirectUri: String?,
    private val deferred: CompletableDeferred<String?> = CompletableDeferred(),
) {
    /** Истина, если это новый VK ID сценарий (WebView + success_token). */
    val isV2: Boolean get() = redirectUri != null

    /** Ввести классический ответ (captcha_key). */
    fun submit(key: String) {
        try { deferred.complete(key) } catch (_: IllegalStateException) {}
    }

    /** Ввести success_token (новая VK ID схема). */
    fun submitSuccessToken(token: String) {
        try { deferred.complete(token) } catch (_: IllegalStateException) {}
    }

    /** Отменить. Completes deferred с null. */
    fun cancel() {
        try { deferred.complete(null) } catch (_: IllegalStateException) {}
    }

    /** Дождаться результата (key / success_token / null при отмене). */
    suspend fun await(): String? = deferred.await()
}

/**
 * UI-driven реализация [CaptchaHandler]. Показывает диалог через [challenge]
 * StateFlow, на который подписан [re.pinok.ui.components.CaptchaDialog].
 */
class UiCaptchaHandler : CaptchaHandler {
    private val _challenge = MutableStateFlow<CaptchaChallenge?>(null)
    val challenge: StateFlow<CaptchaChallenge?> = _challenge.asStateFlow()

    override suspend fun solve(sid: String, img: String): String? {
        // If a captcha is already being solved, fail fast
        if (_challenge.value != null) return null
        AppLog.i(TAG, "solve: showing CLASSIC captcha dialog (sid=${sid.take(8)}…)")
        return awaitChallenge(CaptchaChallenge(sid = sid, img = img, redirectUri = null))
    }

    override suspend fun solveRedirect(redirectUri: String): String? {
        if (_challenge.value != null) return null
        AppLog.i(TAG, "solve: showing VK ID captcha dialog (redirect_uri=${redirectUri.take(80)}…)")
        return awaitChallenge(CaptchaChallenge(sid = null, img = null, redirectUri = redirectUri))
    }

    // Complexation: ожидание результата через CompletableDeferred внутри CaptchaChallenge.
    private suspend fun awaitChallenge(challenge: CaptchaChallenge): String? {
        _challenge.value = challenge
        return try {
            challenge.await()
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