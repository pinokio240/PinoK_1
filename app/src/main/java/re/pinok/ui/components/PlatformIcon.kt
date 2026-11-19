package re.pinok.ui.components

import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import re.pinok.data.model.UserProfile

/**
 * #PLATFORM-ICON (2026-11-17): глобальная иконка устройства пользователя
 * рядом с именем профиля. Источник — [UserProfile.lastSeen.platform] (VK API
 * `last_seen.platform`: 1=mobile web, 2=iPhone, 3=iPad, 4=Android, 5=WinPhone,
 * 6=Windows, 7=web full).
 *
 * ## Видимость на всех темах
 * - **online**: [MaterialTheme.colorScheme.primary] (solid, full opacity) —
 *   ярко-зелёный/акцентный, виден на light/dark.
 * - **offline**: [MaterialTheme.colorScheme.onSurface] (solid, full opacity) —
 *   высокий контраст к surface на обеих темах (НЕ onSurfaceVariant с alpha —
 *   alpha 0.6 была невидна на dark темах).
 * - Размер 14dp (был 12dp — слишком мелко).
 *
 * ## Глобальность
 * Используется везде где показывается имя профиля: сообщения в чате, forwarded,
 * комментарии, списки. Принимает [platform] и [isOnline] — вызывающая сторона
 * передаёт `profile?.lastSeen?.platform` и `profile?.online == 1`.
 *
 * @param platform код платформы из last_seen; null = иконка не рисуется
 * @param isOnline true если profile.online == 1
 */
@Composable
fun PlatformDeviceIcon(
    platform: Int?,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    if (platform == null) return
    val icon = platformIconVector(platform)
    val tint: Color = if (isOnline) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Icon(
        imageVector = icon,
        contentDescription = platformLabel(platform),
        tint = tint,
        modifier = modifier.size(14.dp),
    )
}

/** VK platform code → Material icon. */
private fun platformIconVector(platform: Int): ImageVector = when (platform) {
    4 -> Icons.Outlined.PhoneAndroid      // Android
    2 -> Icons.Outlined.PhoneIphone        // iPhone
    3 -> Icons.Outlined.TabletMac          // iPad
    6, 7 -> Icons.Outlined.Computer        // Windows / web (desktop)
    else -> Icons.Outlined.DevicesOther    // mobile web (1), WinPhone (5), unknown
}

/** VK platform code → human-readable label (для contentDescription). */
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

/**
 * Convenience: extract platform + online from [UserProfile] (nullable-safe).
 * Возвращает null для platform если lastSeen отсутствует — [PlatformDeviceIcon]
 * тогда не рисуется.
 */
fun UserProfile.platformIconData(): Pair<Int?, Boolean>? {
    val p = lastSeen?.platform ?: return null
    return p to (online == 1)
}
