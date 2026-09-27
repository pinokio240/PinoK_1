// File: ui/screens/community/SoftDeleteStrip.kt
// #ADMIN-SOFT-DELETE: стрип восстановления после мягкого удаления поста.
// VK-web: «Запись удалена» + кнопка «Восстановить».
// Таймер ведёт вызывающий экран; компонент — только визуальный.
package re.pinok.ui.screens.community

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SoftDeleteStrip(
    secondsLeft: Int,
    onRestore: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Запись удалена ($secondsLeft)",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRestore) {
                Text("Восстановить")
            }
        }
    }
}
