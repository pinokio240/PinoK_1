package re.pinok.ui.screens.superapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class Service(
    val title: String,
    val icon: ImageVector,
    val status: ServiceStatus = ServiceStatus.COMING_SOON,
)

private enum class ServiceStatus { READY, COMING_SOON, BLOCKED }

private val services = listOf(
    Service("Сообщества",   Icons.Default.Group),
    Service("Друзья",       Icons.Default.People),
    Service("Закладки",     Icons.Default.Bookmark),
    Service("Документы",    Icons.Default.Description),
    Service("Фотографии",   Icons.Default.PhotoLibrary),
    Service("Видеозаписи",  Icons.Default.VideoLibrary),
    Service("Платежи",      Icons.Default.Payment, status = ServiceStatus.BLOCKED),
    Service("Игры",         Icons.Default.SportsEsports, status = ServiceStatus.BLOCKED),
    Service("VK Apps",      Icons.Default.Apps, status = ServiceStatus.BLOCKED),
    Service("Стикеры",      Icons.Default.Favorite, status = ServiceStatus.BLOCKED),
)

@Composable
fun ServicesScreen() {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(services) { svc ->
            ServiceCard(svc) {
                val msg = when (svc.status) {
                    ServiceStatus.COMING_SOON -> "«${svc.title}» — раздел в разработке"
                    ServiceStatus.BLOCKED -> "«${svc.title}» — недоступно в неофициальном клиенте"
                    ServiceStatus.READY -> "Открываю «${svc.title}»…"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun ServiceCard(svc: Service, onClick: () -> Unit) {
    val isBlocked = svc.status == ServiceStatus.BLOCKED
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                svc.icon,
                contentDescription = svc.title,
                modifier = Modifier.size(32.dp),
                tint = if (isBlocked)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.primary,
            )
            Text(
                svc.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = if (isBlocked)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
