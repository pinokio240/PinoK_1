package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А4 — секция «История» читает репозиторий
 * раздела (CallsSectionRepository.history: callsGetHistory{filter:"all"} +
 * обогащение профилями) вместо собственного remember{}-феча; строки — общий
 * вид CallsClusterRow. Кэш: CONTENT не перезапрашивается при повторном входе
 * на таб; «Повторить» (ошибка/empty) реально перезапускает fetch
 * (refresh force=true). Действия строки/redial-кнопки — Этап Б; клик по
 * строке — redial аудио через существующий CallStarter-путь хоста.
 */
@Composable
fun CallsHistorySection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.history.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsHistorySection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.HISTORY, force = false)
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет звонков",
        onRetry = { repo.refresh(CallsSectionKey.HISTORY, force = true) },
        modifier = Modifier.testTag("call_history_section"),
    ) { entries ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        ) {
            items(entries, key = { it.callId }) { entry ->
                CallsClusterRow(
                    entry = entry,
                    onClick = { onNavigateToCall(entry.peerId) },
                    modifier = Modifier.testTag("call_history_item"),
                )
            }
        }
    }
}
