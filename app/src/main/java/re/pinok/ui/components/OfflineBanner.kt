// File: ui/components/OfflineBanner.kt
package re.pinok.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import re.pinok.SovaApp
import re.pinok.util.NetworkSwitchState

/**
 * #NETWORK-RESILIENCE (2026-08-04): ненавязчивый persistent баннер offline-режима.
 *
 * В отличие от [NetworkSwitchPopup] (модальный AlertDialog, default=disabled в
 * настройках), этот баннер:
 *  - **Всегда включён** — нет тумблера в настройках. Это informational status,
 *    не прерывание. Пользователь должен видеть что он offline, чтобы понимать
 *    почему данные не обновляются.
 *  - **Не блокирует UI** — persistent полоска внизу экрана поверх bottomBar.
 *    Пользователь может продолжать скроллить, открывать экраны, читать кэш.
 *  - **Auto-dismiss** когда сеть появляется (слушает [NetworkObserver.isOnlineFlow]).
 *
 * UX-сценарии:
 *
 *  1. **Полная потеря сети** (`isOnline == false`):
 *     «Нет подключения. Данные могут быть устаревшими.» + иконка WifiOff.
 *     Тап → переход в OfflineManager (если есть закэшированный контент).
 *
 *  2. **Смена сети без потери** (`networkSwitchState == Switching`):
 *     «Переключение сети…» + спиннер. Тап → ничего (ждём стабилизации).
 *     Этот state кратковременный (8с auto-timeout в NetworkSwitchPopup),
 *     баннер просто информирует что происходит.
 *
 *  3. **Refresh token после switch** (`networkSwitchState == Refreshing`):
 *     «Обновляем сессию…» + спиннер. Тап → ничего.
 *
 *  4. **Refresh failed** (`networkSwitchState == Failed`):
 *     «Не удалось обновить сессию» + кнопка-подсказка «Тап для повтора».
 *     Тап → `SovaApp.retryNetworkSwitchRefresh()`.
 *
 *  5. **Online** (`isOnline == true && state == Idle`):
 *     Баннер скрыт (AnimatedVisibility fade-out).
 *
 * Важно: баннер НЕ заменяет [NetworkSwitchPopup]. Popup остаётся для
 * пользователей, которые явно включили модальное прерывание в настройках.
 * Banner — для всех остальных, кто хочет тихой работы.
 *
 * Размещение: в [re.pinok.ui.navigation.SovaNavHost] рядом с [NetworkSwitchPopup]
 * и [CaptchaDialog], как overlay поверх всего app (в Box после Scaffold).
 *
 * @param onOpenOfflineManager навигация на Screen.OfflineManager (тап по баннеру
 *     в Offline state — пользователь хочет посмотреть закэшированный контент).
 * @param onRetry тап по баннеру в Failed state — повторить silent refresh.
 *     Default: `SovaApp.get().retryNetworkSwitchRefresh()`.
 */
@Composable
fun OfflineBanner(
    onOpenOfflineManager: () -> Unit,
    onRetry: () -> Unit = { SovaApp.get().retryNetworkSwitchRefresh() },
) {
    val app = SovaApp.get()
    val isOnline by app.networkObserver.isOnlineFlow.collectAsState()
    val switchState by app.networkSwitchState.collectAsState()

    // Определяем что показать (или скрыть).
    // Приоритет: полная потеря сети > Failed refresh > Switching/Refreshing > скрыт.
    val content: BannerContent? = when {
        !isOnline -> BannerContent.Offline
        switchState is NetworkSwitchState.Failed -> BannerContent.Failed(
            canRetry = (switchState as NetworkSwitchState.Failed).canRetry,
        )
        switchState is NetworkSwitchState.Offline -> BannerContent.Offline
        switchState is NetworkSwitchState.Switching -> BannerContent.Switching
        switchState is NetworkSwitchState.Refreshing -> BannerContent.Refreshing(
            attempt = (switchState as NetworkSwitchState.Refreshing).attempt,
        )
        else -> null  // Idle + online — баннер скрыт
    }

    AnimatedVisibility(
        visible = content != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        if (content == null) return@AnimatedVisibility

        val (icon, message, backgroundColor, contentColor, onTap) = when (content) {
            BannerContent.Offline -> BannerVisuals(
                icon = Icons.Outlined.WifiOff,
                message = "Нет подключения. Данные могут быть устаревшими.",
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onTap = onOpenOfflineManager,
            )
            BannerContent.Switching -> BannerVisuals(
                icon = Icons.Outlined.CloudOff,
                message = "Переключение сети…",
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onTap = null,  // тап ничего не делает — ждём стабилизации
            )
            is BannerContent.Refreshing -> BannerVisuals(
                icon = Icons.Outlined.CloudOff,
                message = "Обновляем сессию (попытка ${content.attempt})…",
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onTap = null,
            )
            is BannerContent.Failed -> BannerVisuals(
                icon = Icons.Outlined.CloudOff,
                message = if (content.canRetry) {
                    "Не удалось обновить сессию. Тап для повтора."
                } else {
                    "Сессия истекла. Требуется вход."
                },
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onTap = if (content.canRetry) onRetry else null,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable(enabled = onTap != null) { onTap?.invoke() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Internal state model
// ─────────────────────────────────────────────────────────────────────────

private sealed class BannerContent {
    /** Полная потеря сети (`isOnline == false`). */
    object Offline : BannerContent()

    /** Идёт смена default route (Wi-Fi↔Mobile) без потери связи. */
    object Switching : BannerContent()

    /** Silent refresh токена после switch (err=5/1117 grace period). */
    data class Refreshing(val attempt: Int) : BannerContent()

    /** Silent refresh не удался — токен протух, сеть есть но VK не пускает. */
    data class Failed(val canRetry: Boolean) : BannerContent()
}

private data class BannerVisuals(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String,
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val onTap: (() -> Unit)?,
)
