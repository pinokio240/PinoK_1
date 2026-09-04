package re.pinok.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * S7-2: Переиспользуемый компонент отображения ошибки.
 *
 * Используется на всех экранах для единообразного показа ошибок
 * с иконкой, текстом и кнопкой «Повторить».
 *
 * @param message Текст ошибки (если null, показывается defaultMessage)
 * @param onRetry Коллбэк при нажатии «Повторить» (если null, кнопка не показывается)
 * @param isOffline Если true, показывается иконка Wi-Fi off вместо error outline
 * @param defaultMessage Сообщение по умолчанию (когда message == null)
 */
@Composable
fun ErrorView(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    isOffline: Boolean = false,
    defaultMessage: String = "Произошла ошибка",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Outlined.WifiOff else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message ?: defaultMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("Повторить")
                }
            }
        }
    }
}

/**
 * S7-2: Компактный error view для встраивания в контент (не full-screen).
 * Используется когда нужно показать ошибку внутри карточки или секции.
 */
@Composable
fun ErrorViewCompact(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * S7-2: Empty state view — показывается когда список пуст.
 */
@Composable
fun EmptyStateView(
    message: String = "Нет данных",
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}