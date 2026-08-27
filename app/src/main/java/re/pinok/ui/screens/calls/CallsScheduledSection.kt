package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun CallsScheduledSection(onNavigateToCall: (Long) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().testTag("scheduled_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Нет запланированных звонков",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}