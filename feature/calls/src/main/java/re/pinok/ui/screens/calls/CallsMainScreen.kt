package re.pinok.ui.screens.calls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import re.pinok.util.AppLog

enum class CallsTab(val label: String) {
    CALL_FRIENDS("Позвонить друзьям"),
    HISTORY("История"),
    MISSED("Пропущенные"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsMainScreen(
    onBack: () -> Unit,
    onNavigateToCall: (Long) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(CallsTab.HISTORY) }
    var sidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 180.dp else 0.dp,
        label = "sidebarWidth",
    )
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    AppLog.i("CallsMain", "selectedTab=${selectedTab.label} sidebarExpanded=$sidebarExpanded")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Звонки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CallsHeader(
                onCreateCall = { showCreateDialog = true },
                onSchedule = { showScheduleDialog = true },
                onJoin = { showJoinDialog = true },
            )
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    ContentArea(selectedTab = selectedTab, onNavigateToCall = onNavigateToCall)
                }
                // Кнопка-флажок для сворачивания/разворачивания боковой панели
                IconButton(
                    onClick = { sidebarExpanded = !sidebarExpanded },
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight()
                        .testTag("sidebar_toggle"),
                ) {
                    Icon(
                        imageVector = if (sidebarExpanded) Icons.AutoMirrored.Filled.ArrowForward
                            else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (sidebarExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Боковая панель (сворачивается/разворачивается)
                if (sidebarExpanded) {
                    CallsRightSidebar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier.width(180.dp).fillMaxHeight(),
                    )
                }
            }
        }
    }
    // Диалоги для кнопок шапки
    if (showCreateDialog) {
        CreateCallDialog(
            onDismiss = { showCreateDialog = false },
            onCall = { peerId ->
                showCreateDialog = false
                onNavigateToCall(peerId)
            }
        )
    }
    if (showJoinDialog) {
        JoinCallDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { link ->
                showJoinDialog = false
                AppLog.i("CallsMain", "join by link: $link")
            }
        )
    }
    if (showScheduleDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Запланировать звонок") },
            text = { Text("Функция планирования звонков будет доступна позже") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showScheduleDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun CreateCallDialog(onDismiss: () -> Unit, onCall: (Long) -> Unit) {
    var peerIdText by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать звонок") },
        text = {
            Column {
                Text("Введите ID пользователя:", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextField(
                    value = peerIdText,
                    onValueChange = { peerIdText = it },
                    placeholder = { Text("152094335") },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("create_call_input"),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val id = peerIdText.toLongOrNull()
                    if (id != null) onCall(id)
                },
                enabled = peerIdText.toLongOrNull() != null,
            ) { Text("Позвонить") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun JoinCallDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var linkText by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключиться к звонку") },
        text = {
            Column {
                Text("Введите ссылку-приглашение:", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    placeholder = { Text("https://vk.ru/call/join/...") },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("join_call_input"),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onJoin(linkText) },
                enabled = linkText.isNotBlank(),
            ) { Text("Подключиться") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun CallsHeader(
    onCreateCall: () -> Unit = {},
    onSchedule: () -> Unit = {},
    onJoin: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderButton(label = "Создать звонок", onClick = onCreateCall)
        HeaderButton(label = "Запланировать", onClick = onSchedule)
        HeaderButton(label = "Подключиться", onClick = onJoin)
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit = {}) {
    TextButton(
        onClick = {
            AppLog.i("CallsHeader", "clicked: $label")
            onClick()
        },
        modifier = Modifier.testTag("header_$label"),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ContentArea(
    selectedTab: CallsTab,
    onNavigateToCall: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (selectedTab) {
            CallsTab.CALL_FRIENDS -> CallsFriendsSection(onNavigateToCall = onNavigateToCall)
            CallsTab.HISTORY -> CallsHistorySection(onNavigateToCall = onNavigateToCall)
            CallsTab.MISSED -> CallsMissedSection(onNavigateToCall = onNavigateToCall)
        }
    }
}

@Composable
private fun EmptyPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CallsRightSidebar(
    selectedTab: CallsTab,
    onTabSelected: (CallsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            CallsTab.entries.forEachIndexed { index, tab ->
                item(key = tab.name) {
                    SidebarTabItem(
                        tab = tab,
                        isSelected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
                if (index == 1 || index == 3 || index == 5) {
                    item(key = "sep_$index") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarTabItem(
    tab: CallsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sidebar_tab_${tab.name}")
            .clickable(onClick = onClick)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tab.label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}