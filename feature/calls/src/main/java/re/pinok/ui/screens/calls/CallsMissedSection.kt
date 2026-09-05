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
 * #CALLS-SNAP (2026-09-05): Этап А2/А4 — секция «Пропущенные» читает
 * репозиторий раздела (CallsSectionRepository.missed: callsGetHistory
 * {filter:"missed"} + обогащение профилями); строки — общий вид
 * CallsClusterRow (бейдж/счётчики-кластеры — Этап Б4). Кэш: CONTENT не
 * перезапрашивается; «Повторить» реально перезапускает fetch.
 */
@Composable
fun CallsMissedSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.missed.collectAsState()

    LaunchedEffect(Unit) {
        AppLog.i("CallsMissedSection", "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.MISSED, force = false)
    }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет пропущенных звонков",
        onRetry = { repo.refresh(CallsSectionKey.MISSED, force = true) },
        modifier = Modifier.testTag("missed_section"),
    ) { entries ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        ) {
            items(entries, key = { it.callId }) { entry ->
                CallsClusterRow(
                    entry = entry,
                    onClick = { onNavigateToCall(entry.peerId) },
                    modifier = Modifier.testTag("missed_call_item"),
                )
            }
        }
    }
}
