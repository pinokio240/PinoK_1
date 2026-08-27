package re.pinok.ui.screens.devices

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TabletMac
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.CuaAction
import re.pinok.data.model.DeviceSession
import re.pinok.data.model.DeviceType
import re.pinok.util.AppLog

/**
 * §49.6 Sprint VK-ID-1.2 — экран «Устройства и сессии».
 *
 * Показывает список активных сессий аккаунта VK (аналог
 * id.vk.ru/account → Устройства). Пользователь может:
 *  - завершить одну сессию (через [CuaVerifySheet] для action=reset_sessions)
 *  - завершить все сессии кроме текущей (action=reset_all_sessions)
 *
 * MVI: состояние экрана в sealed [DevicesUiState] (Loading/Error/Content).
 * Lifecycle: LaunchedEffect(Unit) грузит данные один раз при открытии;
 * pull-to-refresh обновляет список. CoroutineScope привязан к композиции.
 *
 * Источник: VK_IMPORT_API.MD §49.6 Sprint VK-ID-1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var uiState by remember { mutableStateOf<DevicesUiState>(DevicesUiState.Loading) }
    var isRefreshing by remember { mutableStateOf(false) }
    // Pending reset action — открывает CuaVerifySheet.
    var pendingReset by remember { mutableStateOf<PendingReset?>(null) }
    // Confirm dialog для "завершить все".
    var showConfirmResetAll by remember { mutableStateOf(false) }

    fun loadDevices() {
        scope.launch {
            uiState = DevicesUiState.Loading
            val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
            val devices = app.apiClient.accountGetActivityHistoryDevices(hash)
            uiState = if (devices == null) {
                DevicesUiState.Error("Не удалось загрузить список сессий. Проверьте подключение.")
            } else {
                DevicesUiState.Content(devices)
            }
        }
    }

    LaunchedEffect(Unit) { loadDevices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Устройства и сессии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        scope.launch {
                            val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                            val devices = app.apiClient.accountGetActivityHistoryDevices(hash)
                            isRefreshing = false
                            if (devices != null) {
                                uiState = DevicesUiState.Content(devices)
                            } else {
                                Toast.makeText(context, "Ошибка обновления", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                    val devices = app.apiClient.accountGetActivityHistoryDevices(hash)
                    isRefreshing = false
                    if (devices != null) {
                        uiState = DevicesUiState.Content(devices)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = uiState) {
                DevicesUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DevicesUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadDevices() }) {
                            Text("Повторить")
                        }
                    }
                }
                is DevicesUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Header card с описанием + кнопкой "Завершить все".
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Активные сессии: ${s.devices.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Если заметили unfamiliar сессию — завершите её. " +
                                            "VK попросит подтвердить действие кодом (SMS/Push/Email).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { showConfirmResetAll = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                        enabled = s.devices.size > 1,
                                    ) {
                                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text("Завершить все другие сессии")
                                    }
                                }
                            }
                        }

                        items(s.devices, key = { it.deviceId }) { device ->
                            DeviceItem(
                                device = device,
                                onTerminate = {
                                    // Сначала пробуем reset БЕЗ verification — если VK вернёт
                                    // error "verification required", открываем CuaVerifySheet.
                                    scope.launch {
                                        val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                                        val ok = app.apiClient.accountResetSessions(
                                            deviceId = device.deviceId,
                                            hash = hash,
                                        )
                                        if (ok) {
                                            Toast.makeText(context, "Сессия завершена", Toast.LENGTH_SHORT).show()
                                            // Обновляем список.
                                            isRefreshing = true
                                            val refreshed = app.apiClient.accountGetActivityHistoryDevices(hash)
                                            isRefreshing = false
                                            if (refreshed != null) {
                                                uiState = DevicesUiState.Content(refreshed)
                                            }
                                        } else {
                                            // VK требует verification — открываем sheet.
                                            AppLog.i("DevicesScreen", "resetSessions failed — opening CUA sheet")
                                            pendingReset = PendingReset.Single(device.deviceId, device.name)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // CUA verification sheet — открывается когда VK требует подтверждение.
    val pendingResetVal = pendingReset
    if (pendingResetVal != null) {
        val pr = pendingResetVal
        CuaVerifySheet(
            action = when (pr) {
                is PendingReset.Single -> CuaAction.RESET_SESSIONS
                is PendingReset.All -> CuaAction.RESET_ALL_SESSIONS
            },
            title = when (pr) {
                is PendingReset.Single -> "Завершить сессию\n${pr.deviceName}"
                is PendingReset.All -> "Завершить все другие сессии"
            },
            onDismiss = { pendingReset = null },
            onVerified = { token ->
                scope.launch {
                    val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                    val currentDeviceId = runCatching { app.tokenStorage.deviceId() }.getOrNull()
                    val ok = when (pr) {
                        is PendingReset.Single -> app.apiClient.accountResetSessions(
                            deviceId = pr.deviceId,
                            hash = hash,
                            validationToken = token,
                        )
                        is PendingReset.All -> app.apiClient.accountResetAllSessions(
                            hash = hash,
                            validationToken = token,
                            excludeDeviceId = currentDeviceId,
                        )
                    }
                    if (ok) {
                        Toast.makeText(
                            context,
                            if (pr is PendingReset.All) "Все другие сессии завершены" else "Сессия завершена",
                            Toast.LENGTH_SHORT,
                        ).show()
                        // Обновляем список.
                        val refreshed = app.apiClient.accountGetActivityHistoryDevices(hash)
                        if (refreshed != null) {
                            uiState = DevicesUiState.Content(refreshed)
                        }
                    } else {
                        Toast.makeText(context, "Не удалось завершить сессию", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    // Confirm dialog для "Завершить все".
    if (showConfirmResetAll) {
        AlertDialog(
            onDismissRequest = { showConfirmResetAll = false },
            title = { Text("Завершить все сессии?") },
            text = {
                Text(
                    "Все другие устройства будут выходы из аккаунта VK. " +
                        "Текущая сессия PinoK останется активной. " +
                        "VK может потребовать подтвердить действие кодом.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmResetAll = false
                    // Сначала пробуем без verification.
                    scope.launch {
                        val hash = runCatching { app.tokenStorage.logoutHash() }.getOrNull()
                        val currentDeviceId = runCatching { app.tokenStorage.deviceId() }.getOrNull()
                        val ok = app.apiClient.accountResetAllSessions(
                            hash = hash,
                            excludeDeviceId = currentDeviceId,
                        )
                        if (ok) {
                            Toast.makeText(context, "Все сессии завершены", Toast.LENGTH_SHORT).show()
                            val refreshed = app.apiClient.accountGetActivityHistoryDevices(hash)
                            if (refreshed != null) {
                                uiState = DevicesUiState.Content(refreshed)
                            }
                        } else {
                            pendingReset = PendingReset.All
                        }
                    }
                }) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetAll = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DeviceItem(
    device: DeviceSession,
    onTerminate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = deviceIcon(device.deviceType),
                contentDescription = null,
                tint = if (device.isCurrent) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (device.isCurrent) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "это устройство",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (device.appName.isNotBlank()) {
                    Text(
                        device.appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildString {
                    if (!device.location.isNullOrBlank()) append(device.location)
                    if (!device.ip.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(device.ip)
                    }
                    if (isNotEmpty()) append(" · ")
                    append(device.lastActivityLabel())
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!device.isCurrent) {
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onTerminate) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew,
                        contentDescription = "Завершить сессию",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.MOBILE -> Icons.Outlined.Smartphone
    DeviceType.TABLET -> Icons.Outlined.TabletMac
    DeviceType.DESKTOP -> Icons.Outlined.Computer
    DeviceType.UNKNOWN -> Icons.Default.DevicesOther
}

/** Type-safe MVI состояние экрана устройств. */
private sealed interface DevicesUiState {
    /** Загрузка списка сессий. */
    data object Loading : DevicesUiState
    /** Список загружен. */
    data class Content(val devices: List<DeviceSession>) : DevicesUiState
    /** Ошибка загрузки. */
    data class Error(val message: String) : DevicesUiState
}

/** Тип ожидающего reset действия (для CuaVerifySheet). */
private sealed interface PendingReset {
    /** Завершить одну сессию. */
    data class Single(val deviceId: String, val deviceName: String) : PendingReset
    /** Завершить все сессии кроме текущей. */
    data object All : PendingReset
}
