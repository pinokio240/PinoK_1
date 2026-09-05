package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.contracts.CallStarter
import re.pinok.contracts.ContainerRegistry
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog

private const val MISSED_TAG = "CallsMissedSection"

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А4 + Этап Б4 плана «звонки.перенос.план.md»
 * — секция «Пропущенные»: callsGetHistory{filter:"missed"} из репозитория,
 * кластеры со счётчиками «Имя (N)» (REV-UI §3.2: наблюдались (10)/(9)),
 * красная стрелка направления у каждой строки (входящие), redial-кнопки и
 * меню «Действия» строки — общий CallsClusterRow (кластерный режим).
 *
 * Отличия от «Истории» по вебу (REV-UI §3.2): заголовок «Пропущенные»,
 * кнопки «Ещё» в шапке НЕТ (персональная очистка — только из «Истории»;
 * групповая очистка доступна из меню групповой строки). Пагинация —
 * бесконечным скроллом (scroll-to-end → loadMore), как в вебе.
 */
@Composable
fun CallsMissedSection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.missed.collectAsState()
    val scope = rememberCoroutineScope()
    val callStarter = remember { ContainerRegistry.find<CallStarter>().firstOrNull() }

    LaunchedEffect(Unit) {
        AppLog.i(MISSED_TAG, "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.MISSED, force = false)
    }

    val clusters = remember(state.items) { clusterHistoryEntries(state.items) }
    val listState = rememberLazyListState()

    // Этап Б1/Б4: scroll-to-end → дозагрузка пропущенных.
    LaunchedEffect(clusters.size, state.hasMore, state.loadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (info != null) info.index else -1
        }.collect { lastVisible ->
            if (lastVisible >= 0 &&
                clusters.isNotEmpty() &&
                lastVisible >= clusters.size - LOAD_MORE_AHEAD &&
                state.hasMore &&
                !state.loadingMore
            ) {
                AppLog.i(MISSED_TAG, "scroll-to-end → loadMore(MISSED)")
                repo.loadMore(CallsSectionKey.MISSED)
            }
        }
    }

    // Диалог подтверждения групповой очистки из меню строки (персональной
    // «Ещё»-кнопки у пропущенных нет — REV-UI §3.2).
    var confirmClear by remember { mutableStateOf(false) }
    var clearGroupId by remember { mutableLongStateOf(0L) }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет пропущенных звонков",
        onRetry = { repo.refresh(CallsSectionKey.MISSED, force = true) },
        modifier = Modifier.testTag("missed_section"),
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            CallsSectionHeader(
                title = "Пропущенные",
                headerTestTag = "calls_missed_calls_header",
                showMoreMenu = false,
                onClearHistory = {},
            )
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
            ) {
                items(clusters, key = { it.key }) { cluster ->
                    CallsClusterRow(
                        entry = cluster.last,
                        cluster = cluster,
                        onClick = { onNavigateToCall(cluster.peerId) },
                        onAudioCall = { peerId -> startRedial(callStarter, peerId, video = false) },
                        onVideoCall = { peerId -> startRedial(callStarter, peerId, video = true) },
                        onRemove = { target ->
                            scope.launch {
                                repo.removeFromHistory(target.recordIds, target.groupId)
                            }
                        },
                        onClear = { target ->
                            clearGroupId = target.groupId
                            confirmClear = true
                        },
                        modifier = Modifier.testTag("missed_call_item"),
                    )
                }
                if (state.loadingMore) {
                    item(key = "missed_load_more_footer") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                                .testTag("calls_missed_load_more"),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmClearHistoryDialog(
            isGroup = clearGroupId > 0L,
            onConfirm = {
                confirmClear = false
                scope.launch { repo.clearCallHistory(clearGroupId) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}
