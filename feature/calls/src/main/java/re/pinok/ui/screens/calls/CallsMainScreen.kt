package re.pinok.ui.screens.calls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-05): Этап А3 плана «звонки.перенос.план.md» — сайдбар
 * раздела расширен с 3 до 8 живых пунктов (матрица §1.2): Главная · Позвонить
 * друзьям · Активные · Запланированные · История · Пропущенные · Записи
 * звонков · Расшифровки звонков. Каждый таб — реальный fetch через фасад/
 * репозиторий (А2) и честный loading/error/empty (А4).
 *
 * «Настройка пунктов меню» (⚙ внизу сайдбара, §1.1): диалог видимости и
 * порядка пунктов (чекбокс + вверх/вниз), persist в SovaPrefs
 * (calls_sidebar_cfg, CSV «TAB:1,TAB:0» в порядке отображения), восстановление
 * при старте (LaunchedEffect + prefs.callsSidebarCfg.first()). Защиты:
 * нельзя скрыть последний видимый пункт; табы, добавленные в enum после
 * сохранения конфига, появляются видимыми в конце; «все скрыты» → дефолт.
 */
enum class CallsTab(val label: String) {
    HOME("Главная"),
    CALL_FRIENDS("Позвонить друзьям"),
    ACTIVE("Активные"),
    SCHEDULED("Запланированные"),
    HISTORY("История"),
    MISSED("Пропущенные"),
    RECORDINGS("Записи звонков"),
    TRANSCRIPTS("Расшифровки звонков"),
}

/** Пункт конфигурации сайдбара: таб + видимость (порядок = позиция в списке). */
data class CallsSidebarItem(val tab: CallsTab, val visible: Boolean)

private fun defaultSidebarConfig(): List<CallsSidebarItem> =
    CallsTab.entries.map { CallsSidebarItem(it, true) }

private fun serializeSidebarConfig(config: List<CallsSidebarItem>): String =
    config.joinToString(",") { it.tab.name + ":" + (if (it.visible) "1" else "0") }

/** Парсинг SovaPrefs.callsSidebarCfg с мержем новых табов и защитой «все скрыты». */
private fun parseSidebarConfig(raw: String): List<CallsSidebarItem> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return defaultSidebarConfig()
    val parsed = ArrayList<CallsSidebarItem>()
    val seen = HashSet<CallsTab>()
    for (token in trimmed.split(",")) {
        val parts = token.split(":")
        if (parts.size != 2) continue
        var tab: CallsTab? = null
        for (candidate in CallsTab.entries) {
            if (candidate.name == parts[0]) tab = candidate
        }
        if (tab == null) continue
        if (seen.contains(tab)) continue
        seen.add(tab)
        parsed.add(CallsSidebarItem(tab, parts[1] == "1"))
    }
    // Табы, добавленные в enum после сохранения конфига, — в конец, видимыми.
    for (tab in CallsTab.entries) {
        if (!seen.contains(tab)) parsed.add(CallsSidebarItem(tab, true))
    }
    val anyVisible = parsed.any { it.visible }
    if (!anyVisible) return defaultSidebarConfig()
    return parsed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsMainScreen(
    onBack: () -> Unit,
    onNavigateToCall: (Long) -> Unit,
) {
    val deps = LocalCallsDeps.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(CallsTab.HISTORY) }
    var sidebarExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 180.dp else 0.dp,
        label = "sidebarWidth",
    )
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showMenuSettings by remember { mutableStateOf(false) }
    var sidebarConfig by remember { mutableStateOf(defaultSidebarConfig()) }

    // Восстановление конфигурации пунктов меню при старте (SovaPrefs, Этап А3).
    LaunchedEffect(Unit) {
        val raw = deps.prefs.callsSidebarCfg.first()
        sidebarConfig = parseSidebarConfig(raw)
        AppLog.i("CallsMain", "sidebar config restored: ${serializeSidebarConfig(sidebarConfig)}")
    }

    val visibleTabs = sidebarConfig.filter { it.visible }.map { it.tab }
    // Выбранный таб скрыли в настройках → первый видимый.
    var effectiveTab = selectedTab
    if (!visibleTabs.contains(effectiveTab) && visibleTabs.isNotEmpty()) {
        effectiveTab = visibleTabs.first()
    }

    AppLog.i("CallsMain", "selectedTab=${effectiveTab.label} sidebarExpanded=$sidebarExpanded")

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
                onSchedule = {
                    // #CALLS-SNAP (2026-09-05): «Запланировать» — прежний диалог
                    // «будет доступна позже» (запрещённая заглушка) заменён
                    // переходом в ЖИВОЙ таб «Запланированные» (реальный список
                    // messages.getScheduledCalls, Этап А2/А3). Модалка создания
                    // messages.editCall — Этап Г2 плана, заменит этот обработчик.
                    AppLog.i("CallsHeader", "Запланировать → таб Запланированные (модалка editCall — Этап Г2)")
                    selectedTab = CallsTab.SCHEDULED
                },
                onJoin = { showJoinDialog = true },
            )
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    ContentArea(
                        selectedTab = effectiveTab,
                        onNavigateToCall = onNavigateToCall,
                        onCreateCall = { showCreateDialog = true },
                        onJoin = { showJoinDialog = true },
                        onOpenTab = { selectedTab = it },
                    )
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
                        tabs = visibleTabs,
                        selectedTab = effectiveTab,
                        onTabSelected = { selectedTab = it },
                        onOpenMenuSettings = { showMenuSettings = true },
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
    // ⚙ «Настройка пунктов меню» (Этап А3): видимость/порядок + persist.
    if (showMenuSettings) {
        CallsMenuSettingsDialog(
            config = sidebarConfig,
            onApply = { newConfig ->
                showMenuSettings = false
                sidebarConfig = newConfig
                val serialized = serializeSidebarConfig(newConfig)
                scope.launch {
                    deps.prefs.setCallsSidebarCfg(serialized)
                    AppLog.i("CallsMain", "sidebar config saved: $serialized")
                }
            },
            onDismiss = { showMenuSettings = false },
        )
    }
}

/**
 * ⚙ «Настройка пунктов меню»: чекбокс — видимость пункта, стрелки — порядок.
 * Последний видимый пункт скрыть нельзя (чекбокс игнорирует попытку).
 */
@Composable
private fun CallsMenuSettingsDialog(
    config: List<CallsSidebarItem>,
    onApply: (List<CallsSidebarItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка пунктов меню") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                itemsIndexed(draft) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.visible,
                            onCheckedChange = { checked ->
                                val visibleCount = draft.count { it.visible }
                                if (checked || visibleCount > 1) {
                                    draft = draft.map { current ->
                                        if (current.tab == item.tab) current.copy(visible = checked) else current
                                    }
                                }
                            },
                            modifier = Modifier.testTag("menu_cfg_check_${item.tab.name}"),
                        )
                        Text(
                            item.tab.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val mutable = draft.toMutableList()
                                val moved = mutable.removeAt(index)
                                mutable.add(index - 1, moved)
                                draft = mutable
                            },
                            modifier = Modifier.size(32.dp).testTag("menu_cfg_up_${item.tab.name}"),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Выше")
                        }
                        IconButton(
                            enabled = index < draft.size - 1,
                            onClick = {
                                val mutable = draft.toMutableList()
                                val moved = mutable.removeAt(index)
                                mutable.add(index + 1, moved)
                                draft = mutable
                            },
                            modifier = Modifier.size(32.dp).testTag("menu_cfg_down_${item.tab.name}"),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ниже")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }, modifier = Modifier.testTag("menu_cfg_save")) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun CreateCallDialog(onDismiss: () -> Unit, onCall: (Long) -> Unit) {
    var peerIdText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать звонок") },
        text = {
            Column {
                Text("Введите ID пользователя:", style = MaterialTheme.typography.bodyMedium)
                TextField(
                    value = peerIdText,
                    onValueChange = { peerIdText = it },
                    placeholder = { Text("152094335") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_call_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = peerIdText.toLongOrNull()
                    if (id != null) onCall(id)
                },
                enabled = peerIdText.toLongOrNull() != null,
            ) { Text("Позвонить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun JoinCallDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var linkText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключиться к звонку") },
        text = {
            Column {
                Text("Введите ссылку-приглашение:", style = MaterialTheme.typography.bodyMedium)
                TextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    placeholder = { Text("https://vk.ru/call/join/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_call_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(linkText) },
                enabled = linkText.isNotBlank(),
            ) { Text("Подключиться") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
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
    onCreateCall: () -> Unit,
    onJoin: () -> Unit,
    onOpenTab: (CallsTab) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (selectedTab) {
            CallsTab.HOME -> CallsHomeSection(
                onNavigateToCall = onNavigateToCall,
                onCreateCall = onCreateCall,
                onJoin = onJoin,
                onOpenFriends = { onOpenTab(CallsTab.CALL_FRIENDS) },
            )
            CallsTab.CALL_FRIENDS -> CallsFriendsSection(onNavigateToCall = onNavigateToCall)
            CallsTab.ACTIVE -> CallsActiveSection(onNavigateToCall = onNavigateToCall)
            CallsTab.SCHEDULED -> CallsScheduledSection(onNavigateToCall = onNavigateToCall)
            CallsTab.HISTORY -> CallsHistorySection(onNavigateToCall = onNavigateToCall)
            CallsTab.MISSED -> CallsMissedSection(onNavigateToCall = onNavigateToCall)
            CallsTab.RECORDINGS -> CallsRecordingsSection(onNavigateToCall = onNavigateToCall)
            CallsTab.TRANSCRIPTS -> CallsTranscriptsSection(onNavigateToCall = onNavigateToCall)
        }
    }
}

/**
 * Сайдбар: видимые пункты (конфиг А3) + разделители по прежним позициям
 * (после 1-го/3-го/5-го) + внизу ⚙ «Настройка пунктов меню».
 */
@Composable
fun CallsRightSidebar(
    tabs: List<CallsTab>,
    selectedTab: CallsTab,
    onTabSelected: (CallsTab) -> Unit,
    onOpenMenuSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            tabs.forEachIndexed { index, tab ->
                item(key = tab.name) {
                    SidebarTabItem(
                        tab = tab,
                        isSelected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
                if ((index == 1 || index == 3 || index == 5) && index < tabs.size - 1) {
                    item(key = "sep_$index") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMenuSettings)
                .testTag("sidebar_menu_settings")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Настройка пунктов меню",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Настройка пунктов меню",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
