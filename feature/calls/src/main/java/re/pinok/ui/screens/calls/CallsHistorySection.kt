package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.contracts.CallStarter
import re.pinok.contracts.ContainerRegistry
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog

private const val HISTORY_TAG = "CallsHistorySection"

/**
 * Сколько строк до конца списка считаем «пора грузить дальше»
 * (scroll-to-end триггер пагинации Этапа Б1). Общая с «Пропущенными».
 */
internal const val LOAD_MORE_AHEAD = 3

/**
 * #CALLS-SNAP (2026-09-05): Этап А2/А4 + Этап Б плана «звонки.перенос.план.md»
 * — секция «История» читает репозиторий раздела (CallsSectionRepository.history:
 * callsGetHistory{filter:"all"} + обогащение профилями); строки —
 * CallsClusterRow в кластерном режиме.
 *
 * Этап Б1 (пагинация): scroll-to-end LazyColumn → repo.loadMore(HISTORY) —
 * append следующей страницы без дублей, футер-спиннер на loadingMore.
 * Этап Б2 (redial): кнопки «Аудиозвонок X»/«Видеозвонок X» — CallStarter из
 * реестра контейнеров (тот же механизм, что SovaNavHost), video=true доходит
 * до хоста (SovaApp.pendingOutgoingCallVideo).
 * Этап Б3 (действия): «Ещё» шапки → «Очистить историю» (calls.clearHistory,
 * с подтверждающим диалогом — необратимо); меню строки «Действия» →
 * «Убрать из списка» (deleteHistoryRecords / deleteGroupHistoryRecords) и
 * «Очистить историю» (групповые — clearGroupHistory по group_id); после
 * успеха репозиторий перечитывает HISTORY+MISSED форсом.
 */
@Composable
fun CallsHistorySection(onNavigateToCall: (Long) -> Unit) {
    val repo = LocalCallsSectionRepository.current
    val state by repo.history.collectAsState()
    val scope = rememberCoroutineScope()
    // CallStarter — capability самого контейнера звонков (реестр, §1.3 канона);
    // тот же рантайм-объект, что использует SovaNavHost для onNavigateToCall.
    val callStarter = remember { ContainerRegistry.find<CallStarter>().firstOrNull() }

    LaunchedEffect(Unit) {
        AppLog.i(HISTORY_TAG, "ensure loaded (кэш: CONTENT не перезапрашивается)")
        repo.refresh(CallsSectionKey.HISTORY, force = false)
    }

    val clusters = remember(state.items) { clusterHistoryEntries(state.items) }
    val listState = rememberLazyListState()

    // Этап Б1: scroll-to-end → дозагрузка. Ключи эффекта — на изменение
    // размера списка/флагов пагинации снапшот-поток перезапускается.
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
                AppLog.i(HISTORY_TAG, "scroll-to-end → loadMore(HISTORY)")
                repo.loadMore(CallsSectionKey.HISTORY)
            }
        }
    }

    // Диалог подтверждения «Очистить историю» (необратимо, Этап Б3).
    var confirmClear by remember { mutableStateOf(false) }
    var clearGroupId by remember { mutableLongStateOf(0L) }

    CallsSectionScaffold(
        state = state,
        emptyText = "Нет звонков",
        onRetry = { repo.refresh(CallsSectionKey.HISTORY, force = true) },
        modifier = Modifier.testTag("call_history_section"),
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            CallsSectionHeader(
                title = "История звонков",
                headerTestTag = "calls_history_list_header",
                showMoreMenu = true,
                onClearHistory = {
                    clearGroupId = 0L
                    confirmClear = true
                },
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
                        modifier = Modifier.testTag("call_history_item"),
                    )
                }
                if (state.loadingMore) {
                    item(key = "history_load_more_footer") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                                .testTag("calls_history_load_more"),
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

/**
 * Redial (Этап Б2) через CallStarter хоста: тот же механизм, что у всех
 * кнопок звонка ядра (SovaNavHost callClick / onNavigateToCall). Отдельно от
 * onNavigateToCall потому, что тот колбэк жёстко шлёт video=false (SovaNavHost,
 * вне скоупа этапа) — видео-redial обязан нести video=true до хоста.
 */
internal fun startRedial(starter: CallStarter?, peerId: Long, video: Boolean) {
    if (peerId <= 0L) {
        AppLog.w(HISTORY_TAG, "redial: peerId неизвестен — звонок не начат (video=$video)")
        return
    }
    val s = starter
    if (s == null) {
        AppLog.w(HISTORY_TAG, "redial: CallStarter недоступен (peerId=$peerId, video=$video)")
        return
    }
    val ok = s.startCall(peerId, video)
    AppLog.i(HISTORY_TAG, "redial peerId=$peerId video=$video -> $ok")
}

/**
 * Шапка секции (REV-UI §2.2/§3.1): заголовок «История звонков» + «Ещё»
 * (data-testid веба calls_history_list_menu) с пунктом «Очистить историю»
 * (calls.clearHistory — план §1.3). У «Пропущенных» меню «Ещё» в вебе нет
 * (REV-UI §3.2) — showMoreMenu=false.
 */
@Composable
internal fun CallsSectionHeader(
    title: String,
    headerTestTag: String,
    showMoreMenu: Boolean,
    onClearHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).testTag(headerTestTag),
        )
        if (showMoreMenu) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("calls_history_list_menu"),
                ) {
                    Text("Ещё")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Очистить историю") },
                        onClick = {
                            menuOpen = false
                            onClearHistory()
                        },
                        modifier = Modifier.testTag("calls_history_menu_clear"),
                    )
                }
            }
        }
    }
}

/**
 * Подтверждение «Очистить историю» (необратимо) — в стиле диалогов проекта
 * (AlertDialog, как «Настройка пунктов меню»/«Создать звонок»).
 */
@Composable
internal fun ConfirmClearHistoryDialog(
    isGroup: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isGroup) "Очистить историю групповых звонков?"
                else "Очистить историю звонков?"
            )
        },
        text = {
            Text("История будет удалена безвозвратно. Это действие необратимо.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("calls_clear_history_confirm"),
            ) {
                Text("Очистить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
