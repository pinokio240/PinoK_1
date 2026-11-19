package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.TabletMac
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import re.pinok.data.model.UserProfile

/**
 * #PLATFORM-ICON (2026-11-17): глобальная иконка устройства пользователя
 * рядом с именем профиля.
 *
 * ## Поведение (3 случая)
 * 1. **platform известен** (last_seen.platform != null) → иконка устройства
 *    (Android/iPhone/iPad/Computer). Цвет: primary если online, onSurface если оффлайн.
 * 2. **platform неизвестен, но online** → зелёная точка 8dp (fallback — пользователь
 *    видит что фича работает, VK просто не отдал platform для этого токена/приватности).
 * 3. **platform неизвестен, оффлайн** → ничего (null last_seen у оффлайн-юзера бесполезен).
 *
 * ## Видимость на всех темах
 * - online device icon: [MaterialTheme.colorScheme.primary] (solid, full opacity).
 * - offline device icon: [MaterialTheme.colorScheme.onSurface] (solid, full opacity).
 * - online dot: [Color.Green] (solid — видна на light/dark, не зависит от темы).
 * - Размер device icon 14dp, online dot 8dp.
 *
 * ## Почему может не быть device-иконки (только точка)
 * VK web-токен (vk1.a.*) НЕ всегда отдаёт `last_seen.platform` — зависит от
 * приватности юзера и scope токена. `online_info.is_online` возвращается надёжно,
 * поэтому зелёная точка для online-юзеров рисуется ВСЕГДА (даже без platform).
 * Device-иконка (Android/iPhone/Computer) — только когда VK вернул platform.
 *
 * @param profile UserProfile (nullable — null = ничего не рисуем)
 */
@Composable
fun PlatformDeviceIcon(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
) {
    if (profile == null) return
    val platform = profile.lastSeen?.platform
    val isOnline = profile.online == 1

    if (platform != null) {
        // Случай 1: device-иконка.
        val tint = if (isOnline) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = platformIconVector(platform),
            contentDescription = platformLabel(platform),
            tint = tint,
            modifier = modifier.size(14.dp),
        )
    } else if (isOnline) {
        // Случай 2: fallback — зелёная точка для online-юзера без platform.
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.Green),
        )
    }
    // Случай 3: оффлайн без platform → ничего.
}

/** VK platform code → Material icon. */
private fun platformIconVector(platform: Int): ImageVector = when (platform) {
    4 -> Icons.Outlined.PhoneAndroid      // Android
    2 -> Icons.Outlined.PhoneIphone        // iPhone
    3 -> Icons.Outlined.TabletMac          // iPad
    6, 7 -> Icons.Outlined.Computer        // Windows / web (desktop)
    else -> Icons.Outlined.DevicesOther    // mobile web (1), WinPhone (5), unknown
}

/** VK platform code → human-readable label. */
private fun platformLabel(platform: Int): String = when (platform) {
    4 -> "Android"
    2 -> "iPhone"
    3 -> "iPad"
    6 -> "Windows"
    7 -> "Веб"
    1 -> "Мобильная версия"
    5 -> "Windows Phone"
    else -> "Устройство"
}
