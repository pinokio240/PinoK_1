// File: ui/components/NetworkSwitchPopup.kt
package re.pinok.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import re.pinok.SovaApp
import re.pinok.util.AppLog
import re.pinok.util.NetworkSwitchState

/**
 * #NET-SWITCH-POPUP (2026-08-03): всплывающее окно при переключении сети.
 *
 * #MULTI-ATTEMPT-PROGRESS: Refreshing state показывает «попытка N из MAX_GRACE_ATTEMPTS».
 * MAX_GRACE_ATTEMPTS=3 соответствует maxGraceAttempts в VKApiClient.kt grace-period
 * handler (см. блок «#MULTI-ATTEMPT-GRACE»). Если popup показывает attempt > MAX —
 * это ручной retry (кнопка «Повторить» в Failed state → SovaApp.retryNetworkSwitchRefresh).
 *
 * Поведение (по запросу пользователя):
 *  - Пока идёт смена (Switching / Refreshing): спиннер + кнопки «Отмена» и «Закрыть».
 *  - Если смена не удалась (Failed): сообщение + кнопки «Повторить» и «Офлайн-менеджер».
 *  - Полная потеря сети (Offline): «Нет сети» + кнопка «Офлайн-менеджер».
 *  - Переключение между сетями максимально скрыто от пользователя, кроме этого окна.
 *  - Окно выключается в Настройках -> Интерфейс -> «Окно переключения сети»
 *    (SovaPrefs.netSwitchPopupEnabled). При выключении логика переключения и
 *    silent refresh продолжают работать в фоне — функционал не теряется.
 *
 * Кнопки:
 *  - «Отмена» (Switching/Refreshing): setNetworkSwitchState(Idle) — пользователь
 *    отказался ждать. Background refresh в VKApiClient может продолжаться и позже
 *    перевести в Failed (тогда popup появится снова с «Повторить»).
 *  - «Закрыть» (Switching/Refreshing): скрыть popup локально (dismissed = state).
 *    Background работа продолжается. Если state сменится на другой (Failed/Offline) —
 *    popup появится снова.
 *  - «Повторить» (Failed): SovaApp.retryNetworkSwitchRefresh() — запускает silent
 *    ensureFreshToken(force=true) в appScope, переводит в Refreshing.
 *  - «Офлайн-менеджер» (Failed/Offline): onOpenOfflineManager() + setNetworkSwitchState(Idle).
 *
 * Auto-timeout: если Switching длится >8с без перехода в Refreshing/Failed
 * (VK не вернул err=5 — сеть стабилизировалась без IP-конфликта) -> auto-Idle.
 */
@Composable
fun NetworkSwitchPopup(
    onOpenOfflineManager: () -> Unit,
) {
    val app = SovaApp.get()
    val state by app.networkSwitchState.collectAsState()
    // prefsSnapshot — синхронный O(1) кеш (seeded в SovaApp.onCreate, всегда non-null
    // после старта). Popup рекомпосится при смене networkSwitchState (StateFlow) —
    // тогда перечитывает и netSwitchPopupEnabled. Тумблер меняется в Настройках
    // (popup в этот момент скрыт) — к моменту следующего switch'а будет свежее значение.
    val enabled = app.prefsSnapshot?.netSwitchPopupEnabled ?: false

    // Локально «закрытое» состояние — пользователь нажал «Закрыть».
    // Popup скрыт пока state не сменится на ДРУГОЕ значение.
    var dismissed by remember { mutableStateOf<NetworkSwitchState?>(null) }

    // Auto-timeout для Switching: 8с без перехода -> Idle (сеть стабилизировалась).
    val switchingState = state
    LaunchedEffect(switchingState) {
        if (switchingState is NetworkSwitchState.Switching) {
            kotlinx.coroutines.delay(8_000L)
            // Если за 8с state не изменился (всё ещё тот же Switching с тем же sinceMs) —
            // сеть стабилизировалась без VK-ошибки -> Idle.
            val current = app.networkSwitchState.value
            if (current is NetworkSwitchState.Switching && current.sinceMs == switchingState.sinceMs) {
                AppLog.i("NetworkSwitchPopup", "Switching auto-timeout (8s, no VK error) -> Idle")
                app.setNetworkSwitchState(NetworkSwitchState.Idle)
            }
        }
    }

    // Сброс dismissed когда state возвращается в Idle (без потери композиции).
    // LaunchedEffect избегает mutating-state-during-composition.
    if (state == NetworkSwitchState.Idle && dismissed != null) {
        LaunchedEffect(Unit) { dismissed = null }
    }

    // #MULTI-ATTEMPT-PROGRESS: countdown для Switching auto-timeout (8с -> Idle).
    // Показывает пользователю сколько секунд осталось до авто-закрытия popup'а
    // если VK не вернёт err=5 (сеть стабилизировалась без IP-конфликта).
    // LaunchedEffect keyed на sinceMs — сбрасывается каждый раз когда state
    // меняется на новый Switching (sinceMs другой). Должен быть ВЫШЕ ранних
    // return'ов — Compose требует unconditional hooks (conditional LaunchedEffect
    // ломает композицию при enabled=false / Idle / dismissed).
    var switchCountdown by remember { mutableStateOf(8) }
    val switchingForCountdown = state as? NetworkSwitchState.Switching
    LaunchedEffect(switchingForCountdown?.sinceMs) {
        if (switchingForCountdown != null) {
            switchCountdown = 8
            while (switchCountdown > 0) {
                kotlinx.coroutines.delay(1000L)
                switchCountdown--
            }
        }
    }

    // Не показываем если: popup выключен в настройках, state == Idle, или
    // пользователь нажал «Закрыть» для текущего state (dismissed == state).
    if (!enabled) return
    if (state == NetworkSwitchState.Idle) return
    if (dismissed == state) return

    // Локальные привязки для smart-cast в when (state — val из by, не smart-cast'ится).
    val s = state
    val title: String
    val message: String
    val isBusy: Boolean
    when (s) {
        is NetworkSwitchState.Switching -> {
            title = "Переключение сети"
            message = s.reason + ". Обновляем подключение… (${switchCountdown}с)"
            isBusy = true
        }
        is NetworkSwitchState.Refreshing -> {
            title = "Обновление сессии"
            // #MULTI-ATTEMPT-PROGRESS: «попытка N из MAX_GRACE_ATTEMPTS».
            // Соответствует maxGraceAttempts=3 в VKApiClient grace-period handler.
            // Если attempt > MAX — это ручной retry (кнопка «Повторить»), показываем
            // просто «повторная попытка N».
            val maxAttempts = 3
            message = if (s.attempt <= maxAttempts) {
                "Сеть сменилась — обновляем токен (попытка ${s.attempt} из $maxAttempts)…"
            } else {
                "Сеть сменилась — повторная попытка обновления токена (#${s.attempt})…"
            }
            isBusy = true
        }
        is NetworkSwitchState.Failed -> {
            title = "Не удалось переключиться"
            message = s.reason
            isBusy = false
        }
        NetworkSwitchState.Offline -> {
            title = "Нет сети"
            message = "Сетевое подключение потеряно. Доступен офлайн-контент."
            isBusy = false
        }
        NetworkSwitchState.Idle -> return
    }

    AlertDialog(
        onDismissRequest = {
            // Свайп/тап вне диалога = «Закрыть» (скрыть, background продолжает).
            dismissed = state
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val icon = when (s) {
                        is NetworkSwitchState.Failed -> Icons.Outlined.CloudOff
                        NetworkSwitchState.Offline -> Icons.Outlined.WifiOff
                        else -> Icons.Outlined.CloudOff
                    }
                    val tint = if (s is NetworkSwitchState.Failed)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            // Правая кнопка — основное действие.
            when (s) {
                is NetworkSwitchState.Switching, is NetworkSwitchState.Refreshing -> {
                    TextButton(onClick = { dismissed = state }) { Text("Закрыть") }
                }
                is NetworkSwitchState.Failed -> {
                    if (s.canRetry) {
                        TextButton(onClick = {
                            dismissed = null
                            app.retryNetworkSwitchRefresh()
                        }) { Text("Повторить") }
                    } else {
                        // canRetry=false -> AuthActivity. Кнопка «Войти».
                        TextButton(onClick = {
                            app.setNetworkSwitchState(NetworkSwitchState.Idle)
                            dismissed = null
                            try { app.notifyTokenInvalidated() } catch (_: Exception) {}
                        }) { Text("Войти") }
                    }
                }
                NetworkSwitchState.Offline -> {
                    TextButton(onClick = {
                        app.setNetworkSwitchState(NetworkSwitchState.Idle)
                        dismissed = null
                        onOpenOfflineManager()
                    }) { Text("Офлайн-менеджер") }
                }
                NetworkSwitchState.Idle -> {}
            }
        },
        dismissButton = {
            // Левая кнопка — вторичное действие.
            when (s) {
                is NetworkSwitchState.Switching, is NetworkSwitchState.Refreshing -> {
                    TextButton(onClick = {
                        app.setNetworkSwitchState(NetworkSwitchState.Idle)
                        dismissed = null
                    }) { Text("Отмена") }
                }
                is NetworkSwitchState.Failed -> {
                    TextButton(onClick = {
                        app.setNetworkSwitchState(NetworkSwitchState.Idle)
                        dismissed = null
                        onOpenOfflineManager()
                    }) { Text("Офлайн-менеджер") }
                }
                NetworkSwitchState.Offline -> {
                    TextButton(onClick = { dismissed = state }) { Text("Закрыть") }
                }
                NetworkSwitchState.Idle -> {}
            }
        },
    )
}
