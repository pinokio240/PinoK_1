package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.updater.UpdateInfo
import re.pinok.updater.UpdaterManager
import re.pinok.updater.UpdaterUiState

/**
 * Волна 43 #UPDATER-BANNER (docs/UPDATER-PLAN.md §2, стратегия M5):
 * ненавязчивый баннер «доступна новая версия» поверх главного экрана.
 * Паттерн — OfflineBanner (тот же Box в SovaNavHost, один над другим в
 * Column, чтобы баннеры не спорили за одну позицию).
 *
 * ПОКАЗЫВАЕТСЯ только когда:
 *  - состояние UpdaterManager'а = Available (после проверки манифеста);
 *  - эту версию НЕ «пропустили» (SovaPrefs.update_skipped_code — кнопка
 *    «Пропустить» здесь же).
 *
 * Тап по баннеру → [onOpenUpdateTab] (навигация + одноразовый флаг
 * UpdateDeepLink, чтобы настройки открылись сразу на вкладке «Обновлений»).
 * Автопроверки баннер НЕ запускает — он только отображает результат
 * (ручная кнопка / проверка при запуске / проверка при входе во вкладку).
 *
 * NULL-ЯВНО: состояние и снапшот читаются до любых ранних выходов; при
 * неполных данных баннер просто не рисуется.
 */
@Composable
fun UpdateBanner(
    onOpenUpdateTab: () -> Unit,
) {
    val app = SovaApp.get()
    val state by UpdaterManager.state.collectAsState()
    val snapFlow = app.prefs.data.collectAsState(initial = app.prefsSnapshot)
    val scope = rememberCoroutineScope()

    val st = state
    val availableInfo: UpdateInfo? = when (st) {
        is UpdaterUiState.Available -> st.latest
        else -> null
    }
    val s = snapFlow.value
    if (availableInfo == null) return
    if (s == null) return
    // «Пропустить эту версию»: баннер её больше не показывает (вкладка покажет).
    if (availableInfo.versionCode == s.updateSkippedCode) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onOpenUpdateTab() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Доступна версия " + availableInfo.versionName.orEmpty() +
                    " (versionCode " + availableInfo.versionCode + ")",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val notes = availableInfo.notes.orEmpty()
            if (notes.isNotEmpty()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = {
            scope.launch { UpdaterManager.skipVersion(availableInfo.versionCode) }
        }) {
            Text(
                "Пропустить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
