// File: ui/screens/community/AdminStrikesScreen.kt
package re.pinok.ui.screens.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════
// ADMIN-MENU (#ADMIN-STRIKES): «Страйки» сообщества — web-only strikeSystem.*.
// READ  — strikeSystem.getStrikesList {group_id, tab=active|appealed,
//         date_from=<unix>, date_to=<unix>} → {"count":0,"data":[]} (HAR §2).
//         Данных сейчас НЕТ — честный empty-state, не заглушка.
//       — strikeSystem.getInfo {group_id, tab} (в HAR вызывается в batch);
//         формат ответа не зафиксирован → на экране не рисуется, метод в ядре
//         возвращает сырой response или null (см. VKApiClient.strikeSystemGetInfo).
// ЧЕСТНОЕ ОГРАНИЧЕНИЕ: API «Подать апелляцию» (appeal-метод) в коде/HAR НЕ
// найден — кнопку НЕ рисуем; вместо неё строка «Обжалование доступно в
// веб-версии vk.ru/strikes/-<groupId>» внизу экрана (без несуществующих вызовов).
// ═══════════════════════════════════════════════════════════════════════════

/** Значение tab для active-вкладки (как в HAR). */
private const val STRIKE_TAB_ACTIVE = "active"
/** Значение tab для appealed-вкладки (как в HAR). */
private const val STRIKE_TAB_APPEALED = "appealed"

/** Unix-секунды → «dd.MM.yyyy HH:mm»; при ошибке формата — сырое число. */
private fun formatStrikeDate(unix: Long): String {
    return try {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(unix * 1000L))
    } catch (e: Exception) {
        unix.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStrikesScreen(groupId: Long, onBack: () -> Unit) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(STRIKE_TAB_ACTIVE) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<VKApiClient.StrikesPage>(VKApiClient.StrikesPage(0, emptyList())) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val p = app.apiClient.strikeSystemGetStrikesList(groupId, tab)
                page = p
                // Пустой ответ {"count":0,"data":[]} — штатное состояние (HAR),
                // НЕ ошибка. Ошибкой считаем только реальный lastApiError.
                val err = app.apiClient.lastApiError
                if (p.count == 0L && p.data.isEmpty() && !err.isNullOrBlank()) {
                    error = err
                }
            } catch (e: Exception) {
                AppLog.e("AdminStrikes", "load failed", e)
                // NULL-ЯВНО: однострочный фолбэк для отображаемого текста ошибки,
                // бывает исключение без message; логи подробнее в AppLog.
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId, tab) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Страйки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            val tabIndex = if (tab == STRIKE_TAB_ACTIVE) 0 else 1
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tab = STRIKE_TAB_ACTIVE },
                    text = { Text("Активные") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tab = STRIKE_TAB_APPEALED },
                    text = { Text("Обжалованные") },
                )
            }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                error != null -> ErrorView(
                    message = error,
                    onRetry = { load() },
                    modifier = Modifier.fillMaxSize(),
                )

                page.data.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Страйков нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(page.data, key = { it.id }) { strike ->
                        StrikeRow(strike)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Обжалование доступно в веб-версии vk.ru/strikes/-$groupId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/**
 * Строка страйка. Формат элемента не подтверждён (данных в HAR нет) — рисуем
 * ровно то, что удалось распарсить: title/reason/date, иначе честный фолбэк.
 */
@Composable
private fun StrikeRow(strike: VKApiClient.StrikeItem) {
    var title = strike.title
    if (title == null || title.isBlank()) {
        title = "Страйк #${strike.id}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val reason = strike.reason
            if (!reason.isNullOrBlank()) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val date = strike.date
            if (date != null) {
                Text(
                    formatStrikeDate(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}