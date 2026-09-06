package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.feature.calls.CallsDependencies
import re.pinok.util.AppLog

/**
 * #CALLS-INCOMING-UI (Этап Е плана «звонки.перенос.план.md» §4-Е2; реверс §8.2):
 * свёрнутый баннер входящего звонка — компактный оверлей ПОВЕРХ контента любого
 * раздела (эквивалент web-виджета CollapsedCall/CallWidget: фото + имя + статус
 * calls_incoming_collapsed; кнопки). Показывается после «Свернуть» на полноэкранном
 * [IncomingCallScreen]; тап по телу баннера — развернуть обратно. Кнопки:
 * принять (зелёная) / отклонить (красная, hangup REJECTED — [performIncomingDecline]).
 *
 * Хост (SovaNavHost) скрывает баннер в звонковом разделе (route "call"/"calls_*"/
 * "settings_calls") и вешает на TopCenter с statusBarsPadding — системные бары
 * не перекрываются.
 */
@Composable
fun IncomingCallBanner(
    peerId: Long,
    title: String,
    photo: String?,
    payload: String?,
    deps: CallsDependencies,
    modifier: Modifier = Modifier,
    onAccept: () -> Unit,
    onDone: () -> Unit,
    onExpand: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1F2B),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) { onExpand() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val photoVal = photo
            if (photoVal != null) {
                AsyncImage(
                    model = photoVal,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2F3E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color(0xFF8A93A6),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "Входящий звонок" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Входящий звонок…",
                    color = Color(0xFF9AA3B5),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Принять (зелёная) — существующая навигация на CallScreen.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF43A047))
                    .clickable {
                        if (!busy) onAccept()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Принять",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Отклонить (красная) — hangup REJECTED (тот же путь, что на полном экране).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable {
                        if (busy) return@clickable
                        busy = true
                        scope.launch {
                            AppLog.i("IncomingCallBanner", "Отклонение входящего из баннера: REJECTED")
                            val ok = performIncomingDecline(deps, payload, "REJECTED")
                            if (ok) {
                                onDone()
                            } else {
                                // Честный провал: баннер остаётся, можно повторить/развернуть.
                                AppLog.w("IncomingCallBanner", "Отклонение НЕ подтверждено (REJECTED)")
                                busy = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CallEnd,
                    contentDescription = "Отклонить",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
