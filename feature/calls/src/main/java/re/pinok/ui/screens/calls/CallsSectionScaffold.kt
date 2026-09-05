package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import re.pinok.feature.calls.CallsSectionState
import re.pinok.feature.calls.CallsSectionStatus

/**
 * #CALLS-SNAP (2026-09-05): Этап А4 плана «звонки.перенос.план.md» — общий
 * паттерн loading/error/empty для секций раздела «Звонки».
 *
 * ИЗВЕСТНЫЙ БАГ УСТРАНЁН: раньше «Повторить» менял только локальные флаги
 * (load(){loading=true}) — LaunchedEffect(Unit) не перезапускался и данные
 * НЕ перезагружались. Здесь onRetry обязан вызывать реальный fetch (репозиторий:
 * refresh(key, force=true)) — состояние приходит StateFlow'ом, кнопка всегда
 * перезапускает загрузку. Empty-state тоже даёт «Повторить» (честный
 * empty-state с возможностью перепроверить — требование Этапа А).
 */
@Composable
fun <T> CallsSectionScaffold(
    state: CallsSectionState<T>,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (items: List<T>) -> Unit,
) {
    when (state.status) {
        CallsSectionStatus.LOADING -> {
            Box(modifier.fillMaxSize().testTag("calls_section_loading"), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        CallsSectionStatus.ERROR -> {
            Box(modifier.fillMaxSize().testTag("calls_section_error"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ошибка загрузки",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val msg = state.errorMessage
                    if (msg != null && msg.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // РАБОЧИЙ «Повторить»: onRetry = repo.refresh(key, force=true)
                    Button(onClick = onRetry, modifier = Modifier.testTag("calls_section_retry")) {
                        Text("Повторить")
                    }
                }
            }
        }
        CallsSectionStatus.EMPTY -> {
            Box(modifier.fillMaxSize().testTag("calls_section_empty"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("calls_section_empty_retry")) {
                        Text("Повторить")
                    }
                }
            }
        }
        CallsSectionStatus.CONTENT -> content(state.items)
    }
}
