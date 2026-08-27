package re.pinok.util

/**
 * #NET-SWITCH-POPUP (2026-08-03): reactive state of network switching.
 *
 * Пользователь просил всплывающее окно при смене сети:
 *  - пока идёт смена — кнопки «Отмена» и «Закрыть»;
 *  - если смена не удалась — кнопки «Повторить» и «Офлайн-менеджер»;
 *  - переключение между сетями должно быть максимально скрыто от пользователя,
 *    кроме этого окна;
 *  - окно должно выключаться в настройках интерфейса без потери функционала
 *    приложения (тумблер [SovaPrefs.netSwitchPopupEnabled], default = false).
 *    (2026-08-04: default изменён с true → false по просьбе пользователя.)
 *
 * Производители состояния:
 *  - [NetworkObserver] — физическая смена default route (Wi-Fi↔Mobile) / IP change
 *    / полная потеря сети → [Switching] / [Offline].
 *  - [re.pinok.api.VKApiClient] — err=5/1117 grace-period handler → [Refreshing]
 *    (silent ensureFreshToken) / [Failed] (refresh не дал токен).
 *
 * Потребитель:
 *  - [re.pinok.ui.components.NetworkSwitchPopup] — Composable-overlay поверх
 *    всего app (рядом с CaptchaDialog в SovaNavHost). Подписывается на
 *    [SovaApp.networkSwitchState] + [SovaPrefs.netSwitchPopupEnabled].
 *
 * ВАЖНО: смена сети и обновление токена продолжают работать в фоне независимо
 * от того, показано окно или нет. Тумблер управляет ТОЛЬКО видимостью UI.
 */
sealed class NetworkSwitchState {

    /** Сеть стабильна, переключение не активно. Popup скрыт. */
    object Idle : NetworkSwitchState()

    /**
     * Идёт физическая смена default network (Wi-Fi↔Mobile) или IP change.
     * Popup показывает спиннер + кнопки «Отмена» и «Закрыть».
     *
     * @param sinceMs timestamp начала смены (для auto-timeout → Failed).
     * @param reason человеко-читаемая причина ("Wi-Fi → Mobile", "IP change", …).
     */
    data class Switching(val sinceMs: Long, val reason: String) : NetworkSwitchState()

    /**
     * Сеть сменилась, VK вернул err=5/1117 — идёт silent refresh токена
     * (ensureFreshToken Path 1.5/2.5/3). Popup показывает спиннер + кнопки
     * «Отмена» и «Закрыть».
     *
     * @param attempt номер попытки refresh (1, 2, …).
     */
    data class Refreshing(val attempt: Int) : NetworkSwitchState()

    /**
     * Silent refresh не дал нового токена (нет remixsid / exchange_token /
     * trusted_hash — типично для external browser auth). Сеть переключилась,
     * но VK пока не пускает. Popup показывает ошибку + кнопки «Повторить»
     * и «Офлайн-менеджер».
     *
     * @param reason причина неудачи ("silent refresh вернул null", …).
     * @param canRetry true если есть смысл повторить (hasSilentReloginMeans).
     */
    data class Failed(val reason: String, val canRetry: Boolean) : NetworkSwitchState()

    /**
     * Полная потеря сети (нет default route). Popup показывает «Нет сети»
     * + кнопку «Офлайн-менеджер». Кнопки «Повторить» нет — сеть либо
     * восстановится сама (→ Idle), либо нет (пользователь идёт в офлайн).
     */
    object Offline : NetworkSwitchState()
}
