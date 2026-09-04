package re.pinok.data.model

/**
 * §49.6 Sprint VK-ID-1 (2026-08-04) — модели для управления сессиями/устройствами
 * и CUA (Confirm User Action) verification framework.
 *
 * Источник: анализ архива VK ID_веб.zip (см. VK_IMPORT_API.MD §49.2.2, §49.2.3).
 *
 * API методы:
 *  - `accountPersonal.getActivityHistoryDevices({hash})` → [DeviceSession] list
 *  - `accountPersonal.resetSessions({hash, device_id, app_id, exclude_device_id?})`
 *  - `accountPersonal.resetAllSessions({hash, app_id})`
 *  - `cua.getValidationMethods({action, hash})` → [CuaValidationMethods]
 *  - `cua.sendPhoneCode/sendEmailCode/sendPushCode({hash})` → [CuaSendResult]
 *  - `cua.checkPhoneCode/checkEmailCode/checkPushCode({code, hash})` → [CuaCheckResult]
 */

// ══════════════════════════════════════════════════════════════════════
//  DeviceSession — одна активная сессия аккаунта VK
// ══════════════════════════════════════════════════════════════════════

/**
 * Одна строка в списке «Устройства и сессии» (аналог m.vk.ru → Настройки → Устройства).
 *
 * VK возвращает server-templated поля `name`/`app`/`location` — мы парсим их в
 * человекочитаемые строки, не строим UI из raw JSON.
 *
 * @param deviceId внутренний идентификатор сессии (передаётся в `resetSessions`).
 *   Формат: `87v-we10y1_...` или `android-<uuid>` — НЕ то же самое что deviceId
 *   в LongPoll/push (см. §49.5.5 — deviceId путаница).
 * @param name отображаемое имя устройства ("iPhone 14 Pro", "Chrome на Windows").
 * @param appName имя приложения ("PinoK", "VK", "VK ID", ...).
 * @param ip IP-адрес последнего входа.
 * @param location город/страна последнего входа ("Москва, Россия").
 * @param lastActivityTs unix timestamp последней активности (секунды).
 * @param isOnlinetrue если сессия активна прямо сейчас.
 * @param isCurrent true если это текущая сессия PinoK (НЕЛЬЗЯ завершить).
 * @param deviceType грубая классификация (mobile/desktop/tablet/unknown) —
 *   для иконки в списке. Вычисляется из `name`/`app` эвристикой.
 */
data class DeviceSession(
    val deviceId: String,
    val name: String,
    val appName: String,
    val ip: String?,
    val location: String?,
    val lastActivityTs: Long,
    val isOnline: Boolean,
    val isCurrent: Boolean,
    val deviceType: DeviceType,
) {
    /** Человекочитаемое "был в сети N мин назад" / "сейчас в сети". */
    fun lastActivityLabel(nowSec: Long = System.currentTimeMillis() / 1000): String {
        if (isOnline) return "сейчас в сети"
        val diff = nowSec - lastActivityTs
        return when {
            diff < 60 -> "только что"
            diff < 3600 -> "${diff / 60} мин назад"
            diff < 86_400 -> "${diff / 3600} ч назад"
            diff < 2_592_000 -> "${diff / 86_400} дн назад"
            else -> "${diff / 2_592_000} мес назад"
        }
    }
}

enum class DeviceType { MOBILE, DESKTOP, TABLET, UNKNOWN }

/**
 * Классифицирует устройство по строке имени/приложения.
 * Эвристика — VK не отдаёт явный тип, только server-templated name.
 */
fun classifyDeviceType(name: String, appName: String): DeviceType {
    val n = name.lowercase()
    val a = appName.lowercase()
    if (n.contains("ipad") || n.contains("tablet") || n.contains("планшет")) return DeviceType.TABLET
    if (n.contains("iphone") || n.contains("android") || n.contains("mobile") ||
        a.contains("vk ") || a.contains("pinok") || a.contains("vkid") || a.contains("mobile")
    ) return DeviceType.MOBILE
    if (n.contains("windows") || n.contains("mac") || n.contains("linux") ||
        n.contains("chrome") || n.contains("firefox") || n.contains("safari") ||
        n.contains("browser") || n.contains("браузер")
    ) return DeviceType.DESKTOP
    return DeviceType.UNKNOWN
}

// ══════════════════════════════════════════════════════════════════════
//  CUA — Confirm User Action verification framework
// ══════════════════════════════════════════════════════════════════════

/**
 * Тип канала подтверждения CUA.
 *
 * VK ID web SDK поддерживает 4 канала (§49.2.3):
 *  - SMS на привязанный телефон
 *  - PUSH на официальное VK app (если установлен)
 *  - EMAIL на notify-email
 *  - PHONE_BIND — код для смены телефона (отдельный flow)
 */
enum class CuaMethod {
    SMS,
    PUSH,
    EMAIL,
    PHONE_BIND,
    ;

    val apiSendName: String
        get() = when (this) {
            SMS -> "cua.sendPhoneCode"
            PUSH -> "cua.sendPushCode"
            EMAIL -> "cua.sendEmailCode"
            PHONE_BIND -> "cua.sendPhoneBindCode"
        }

    val apiCheckName: String
        get() = when (this) {
            SMS -> "cua.checkPhoneCode"
            PUSH -> "cua.checkPushCode"
            EMAIL -> "cua.checkEmailCode"
            PHONE_BIND -> "cua.checkPhoneBindCode"
        }

    val displayName: String
        get() = when (this) {
            SMS -> "SMS"
            PUSH -> "Push-уведомление"
            EMAIL -> "Email"
            PHONE_BIND -> "SMS (смена телефона)"
        }
}

/**
 * Один доступный метод подтверждения для данного action.
 *
 * @param method тип канала.
 * @param mask замаскированный адрес ("+7 ••• 94", "p•••24@bk.ru") —
 *   VK возвращает его для отображения юзеру.
 * @param isPrimarytrue если VK рекомендует этот метод (первый в списке).
 */
data class CuaValidationMethod(
    val method: CuaMethod,
    val mask: String,
    val isPrimary: Boolean = false,
)

/**
 * Ответ `cua.getValidationMethods({action, hash})`.
 *
 * @param methods доступные каналы подтверждения (может быть пустым если
 *   VK не требует verification для данного action — тогда reset можно
 *   выполнять сразу).
 * @param canSkiptrue если verification опциональна (action неопасный).
 * @param retryDelaySec задержка перед повторной отправкой кода (для resend UI).
 */
data class CuaValidationMethods(
    val methods: List<CuaValidationMethod>,
    val canSkip: Boolean = false,
    val retryDelaySec: Int = 60,
)

/**
 * Ответ `cua.sendXxxCode({hash})` — код отправлен.
 *
 * @param successtrue если код отправлен.
 * @param retryDelaySec через сколько сек можно запросить код повторно.
 * @param error код ошибки VK (если success=false), человекочитаемое.
 */
data class CuaSendResult(
    val success: Boolean,
    val retryDelaySec: Int = 60,
    val error: String? = null,
)

/**
 * Ответ `cua.checkXxxCode({code, hash})` — код проверен.
 *
 * @param successtrue если код верный.
 * @param validationToken токен подтверждения — передаётся в опасный action
 *   (resetSessions, changePassword, ...). VK валидирует его серверно.
 *   Если VK не требует token для данного action — поле null (success=true достаточно).
 * @param error код ошибки VK (неверный код, истёк и т.п.).
 * @param attemptsRemaining сколько попыток ввода осталось (VK ограничивает).
 */
data class CuaCheckResult(
    val success: Boolean,
    val validationToken: String? = null,
    val attemptsRemaining: Int? = null,
    val error: String? = null,
)

/**
 * Action-константы для `cua.getValidationMethods({action})`.
 *
 * Источник: account.bundle.js — найдены exact strings.
 */
object CuaAction {
    /** Завершить ОДНУ сессию (device-specific logout). */
    const val RESET_SESSIONS = "reset_sessions"
    /** Завершить ВСЕ сессии (logout everywhere). */
    const val RESET_ALL_SESSIONS = "reset_all_sessions"
    const val CHANGE_PASSWORD = "change_password"
    const val CHANGE_EMAIL = "change_email"
    const val CHANGE_PHONE = "change_phone"
    const val DISABLE_OTP = "disable_otp"
}
