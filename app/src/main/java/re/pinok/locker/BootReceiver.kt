package re.pinok.locker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import re.pinok.SovaApp
import re.pinok.realtime.LongPollKeepAliveService
import re.pinok.util.AppLog

/**
 * Re-arms the locker after device boot or after the user unlocks the device
 * post-reboot. Mirrors the original SOVA V RE behaviour where the app would
 * prompt for PIN as soon as the launcher was reachable.
 *
 * Fix #340: также запускает [LongPollKeepAliveService] после загрузки, если
 * пользователь был залогинен (есть валидный token ИЛИ remixsid для silent
 * re-login). Без этого после перезагрузки устройства LongPoll не поднимался,
 * пока пользователь сам не откроет приложение → push-уведомления не приходили.
 *
 * `MY_PACKAGE_REPLACED` добавлен в intent-filter — сервис перезапускается также
 * после обновления приложения (иначе после app update push молчат до первого
 * ручного запуска).
 *
 * На Android 12+ (API 31+) `startForegroundService` из BOOT_COMPLETED
 * receiver разрешён — broadcast от системы exempt от background-start
 * restrictions.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AppLog.i("BootReceiver", "Boot/pkg-replaced received (${intent.action}), locker armed")
                // Fix #340: поднимаем keep-alive сервис если есть сохранённая сессия.
                // SovaApp может быть ещё не инициализирован при BOOT_COMPLETED —
                // getOrNull вернёт null, в этом случае сервис всё равно стартует
                // (onStartCommand внутри проверит токен через SovaApp.get).
                try {
                    val app = SovaApp.getOrNull()
                    val hasToken = app?.tokenStorage?.hasValidToken() == true
                    val hasRemixsid = !app?.exchangeAuthRepository?.remixsid().isNullOrBlank()
                    if (hasToken || hasRemixsid) {
                        AppLog.i("BootReceiver", "Session present (token=$hasToken, remixsid=$hasRemixsid) — starting keep-alive service")
                        LongPollKeepAliveService.start(context)
                    } else {
                        AppLog.i("BootReceiver", "No session — keep-alive service not started")
                    }
                } catch (e: Exception) {
                    AppLog.w("BootReceiver", "keep-alive start failed: ${e.message}")
                }
            }
        }
    }
}
