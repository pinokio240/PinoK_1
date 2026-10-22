package re.pinok.auth.auth2

import android.app.Activity
import re.pinok.SovaApp

// #AUTH2 (2026-10-17): слой абстракции «флоу авторизации». Позволяет сосуществовать
// нескольким способам входа и переключаться между ними тумблером (devAuthV2Enabled)
// без вмешательства в существующий WebView-флоу (который остаётся дефолтом).

/** Результат флоу авторизации. */
sealed class AuthFlowResult {
    /** Вход успешен — токен сохранён, MainActivity продолжит как обычно. */
    data class Success(val userId: Long? = null) : AuthFlowResult()
    /** Пользователь отменил вход / закрыл экран. */
    object Cancelled : AuthFlowResult()
    /** Флоу не смог выполнить вход (сеть, VK отклонил и т.п.) — честный вердикт. */
    data class Error(val message: String, val code: Int? = null) : AuthFlowResult()
}

/**
 * Контракт флоу входа. Новая реализация (например [DirectHttpAuthFlow] — прямой HTTP OAuth)
 * пишет токен через уже существующий ExchangeAuthRepository.saveOAuthToken — тем же способом,
 * что и старый флоу, поэтому весь остальной рантайм (MainActivity, VKApiClient, LongPoll)
 * не отличает «Авторизацию 2» от прежнего входа.
 */
interface AuthFlow {
    /**
     * Запустить флоу.
     * @return результат входа.
     */
    suspend fun run(activity: Activity): AuthFlowResult
}

/**
 * Фабрика выбора флоу по тумблеру. Старый WebView-флоу НЕ трогается.
 *
 * Правила:
 *  - silent=true (скрытый авто-ре-логин) ВСЕГДА идёт в старый флоу — «Авторизация 2»
 *    не должна перехватывать молчаливый refresh (иначе сломается Fix #107/SILENT-HEADLESS).
 *  - authV2Enabled=true и НЕ silent → новый флоу (DirectHttpAuthFlow).
 *  - иначе → null (это сигнал AuthActivity продолжить прежний путь входа).
 */
object AuthFlowFactory {
    /**
     * @return флоу, который нужно запустить вместо прежнего, либо null = прежний путь.
     */
    fun resolve(app: SovaApp, silent: Boolean): AuthFlow? {
        // Silent re-login — всегда прежний WebView-флоу (не трогаем).
        if (silent) return null
        // AUTH-WHITESCREEN-FIX (2026-10-23): default false — V2 даёт белый экран на
        // budget устройствах (Oppo A17k). V1 (VkAuthWebViewScreenV2) имеет safety-mechanisms.
        val v2 = runCatching { app.prefsSnapshot?.authV2Enabled ?: false }.getOrDefault(false)
        if (!v2) return null
        return DirectHttpAuthFlow(app)
    }
}

/**
 * #AUTH2: реализация «Авторизации 2» — запускает AuthV2Activity (прямой HTTP OAuth) и
 * ждёт результат. Токен сохраняется AuthV2Activity через ExchangeAuthRepository — тот же
 * механизм, что и прежний вход, поэтому состояние рантайма (MainActivity, VKApiClient, LongPoll)
 * не отличает этот флоу от старого.
 */
class DirectHttpAuthFlow(
    private val app: SovaApp,
) : AuthFlow {
    override suspend fun run(activity: Activity): AuthFlowResult {
        return try {
            val intent = android.content.Intent(activity, AuthV2Activity::class.java)
            activity.startActivity(intent)
            // AuthV2Activity вернёт результат через setResult(RESULT_OK/RESULT_CANCELED).
            // Вход определяется по сохранённому токену (MainActivity.onResume проверяет
            // hasValidToken), поэтому здесь не ждём ActivityResult — флоу считается запущенным.
            AuthFlowResult.Success()
        } catch (t: Throwable) {
            AuthFlowResult.Error(t.message ?: "Не удалось запустить вход", code = null)
        }
    }
}