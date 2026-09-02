package re.pinok.ui.screens.notifications

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.BannedUser
import re.pinok.data.model.BannedUsersList
import re.pinok.data.model.SettingsParam
import re.pinok.data.model.SettingsSection
import re.pinok.data.model.SilentModeStatus
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════
// §1-NOTIF-ANALYSIS: Экран «Настройки уведомлений»
//   Соответствует m.vk.ru/settings?act=notify
//   Источник: /home/z/notif/NOTIFICATION_ANALYSIS.md
// ═══════════════════════════════════════════════════════════

/**
 * Экран настроек уведомлений. Объединяет:
 *  - «Не беспокоить» (account.getSilentModeStatus / startSilentMode / stopSilentMode)
 *  - BFF-секции settingsGeneral.getNotifySettings → ParamRow (toggle/select/button/warning)
 *  - Заблокированные пользователи (account.getBanned / unban)
 *  - Фильтр нецензурной лексики (account.setObsceneFilter)
 *
 * Optimistic UI: переключатели двигаются мгновенно, откатываются при ошибке API + Toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var silentMode by remember { mutableStateOf<SilentModeStatus?>(null) }
    var sections by remember { mutableStateOf<List<SettingsSection>>(emptyList()) }
    var banned by remember { mutableStateOf<BannedUsersList?>(null) }
    var obsceneFilter by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        loading = true
        error = null
        try {
            val sm = app.apiClient.accountGetSilentModeStatus()
            val secs = app.apiClient.settingsGeneralGetNotifySettings()
            val bn = app.apiClient.accountGetBanned()
            silentMode = sm
            sections = secs ?: emptyList()
            banned = bn
            if (sm == null && secs == null && bn == null) {
                error = "Не удалось загрузить настройки. Проверьте подключение к сети."
            }
        } catch (e: Exception) {
            AppLog.e("NotificationSettings", "loadAll error", e)
            error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Сбросить") },
                            onClick = {
                                showOverflow = false
                                Toast.makeText(
                                    context,
                                    "Сброс настроек не поддерживается VK API",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        // #NULL-EXPLICIT: захват var-делегата error в локальный val — smart-cast
        // делегированного свойства невозможен; проверка и использование — одна
        // и та же val, поведение прежнее.
        val loadError = error
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            loadError,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { loadAll() } }) {
                            Text("Повторить")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // 1. Silent mode banner
                    item {
                        SilentModeCard(
                            status = silentMode,
                            onSnooze = { time ->
                                scope.launch {
                                    val ok = app.apiClient.accountStartSilentMode(time)
                                    if (ok) {
                                        silentMode = app.apiClient.accountGetSilentModeStatus()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Не удалось включить «Не беспокоить»",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onStop = {
                                scope.launch {
                                    val ok = app.apiClient.accountStopSilentMode()
                                    if (ok) {
                                        silentMode = app.apiClient.accountGetSilentModeStatus()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Не удалось выключить «Не беспокоить»",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        )
                    }

                    // 2. BFF sections from settingsGeneral.getNotifySettings
                    sections.forEach { section ->
                        if (!section.title.isNullOrBlank()) {
                            item(key = "header_${section.id}") {
                                SectionHeader(title = section.title)
                            }
                        }
                        if (!section.description.isNullOrBlank()) {
                            item(key = "desc_${section.id}") {
                                Text(
                                    section.description,
                                    modifier = Modifier.padding(
                                        start = 16.dp, end = 16.dp, bottom = 4.dp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        items(section.params, key = { "${section.id}_${it.key}" }) { param ->
                            ParamRow(
                                param = param,
                                onToggle = { p, newValue ->
                                    // Optimistic update
                                    val prev = sections
                                    sections = sections.map { s ->
                                        s.copy(params = s.params.map { pp ->
                                            if (pp.key == p.key) pp.copy(isChecked = newValue) else pp
                                        })
                                    }
                                    scope.launch {
                                        val ok = app.apiClient.settingsGeneralToggleNotify(p.key, newValue)
                                        if (!ok) {
                                            sections = prev
                                            Toast.makeText(
                                                context,
                                                "Не удалось изменить",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                onSelect = { p, value ->
                                    val prev = sections
                                    sections = sections.map { s ->
                                        s.copy(params = s.params.map { pp ->
                                            if (pp.key == p.key) pp.copy(value = value) else pp
                                        })
                                    }
                                    scope.launch {
                                        val ok = app.apiClient.settingsGeneralSetNotifySettings(p.key, value)
                                        if (!ok) {
                                            sections = prev
                                            Toast.makeText(
                                                context,
                                                "Не удалось изменить",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // 3. Banned users
                    item(key = "header_banned") {
                        SectionHeader(title = "Заблокированные", icon = Icons.Default.Block)
                    }
                    val bannedItems = banned?.items ?: emptyList()
                    if (bannedItems.isEmpty()) {
                        item(key = "banned_empty") {
                            Text(
                                "Список заблокированных пуст",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(bannedItems, key = { "banned_${it.id}" }) { user ->
                            BannedUserRow(
                                user = user,
                                onUnban = {
                                    scope.launch {
                                        val ok = app.apiClient.accountUnban(user.id)
                                        if (ok) {
                                            banned = banned?.let { bl ->
                                                bl.copy(items = bl.items.filter { it.id != user.id })
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Не удалось разблокировать",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // 4. Obscene filter
                    item(key = "header_content") {
                        SectionHeader(title = "Контент")
                    }
                    item(key = "obscene_filter") {
                        ObsceneFilterRow(
                            checked = obsceneFilter,
                            onToggle = { newValue ->
                                val prev = obsceneFilter
                                obsceneFilter = newValue
                                scope.launch {
                                    val ok = app.apiClient.accountSetObsceneFilter(newValue)
                                    if (!ok) {
                                        obsceneFilter = prev
                                        Toast.makeText(
                                            context,
                                            "Не удалось изменить",
                                            Toast.LENGTH_SHORT,
                                        ).show()
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

// ═══════════════════════════════════════════════════════════
// Sub-components
// ═══════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SilentModeCard(
    status: SilentModeStatus?,
    onSnooze: (Long) -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        if (status?.isActive == true) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Не беспокоить включено", fontWeight = FontWeight.SemiBold)
                }
                val untilText = if (status.isForever) {
                    "До ручного выключения"
                } else {
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    "До ${fmt.format(Date(status.silentUntil * 1000))}"
                }
                Text(
                    untilText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onStop) { Text("Выключить") }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Не беспокоить", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Временно отключить все уведомления",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SnoozeButton("15 мин", Modifier.weight(1f)) { onSnooze(900L) }
                    SnoozeButton("1 час", Modifier.weight(1f)) { onSnooze(3600L) }
                    SnoozeButton("8 часов", Modifier.weight(1f)) { onSnooze(28800L) }
                    SnoozeButton("Навсегда", Modifier.weight(1f)) { onSnooze(-1L) }
                }
            }
        }
    }
}

@Composable
private fun SnoozeButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(text, fontSize = 12.sp)
    }
}

/**
 * Рендерит один параметр настройки в зависимости от его `type`.
 * - "toggle" / "custom_toggle" → Switch row
 * - "select" / "radio" → row с dropdown из param.options
 * - "button" → TextButton row (action)
 * - "warning" → карточка с amber-фоном
 * - прочее → просто title
 */
@Composable
private fun ParamRow(
    param: SettingsParam,
    onToggle: (SettingsParam, Boolean) -> Unit,
    onSelect: (SettingsParam, String) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    when (param.type) {
        "toggle", "custom_toggle" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    param.title?.let { Text(it) }
                    param.description?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Switch(
                    checked = param.isChecked == true,
                    onCheckedChange = { onToggle(param, it) },
                )
            }
        }
        "select", "radio" -> {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dropdownExpanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        param.title?.let { Text(it) }
                        param.description?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        val selectedLabel = param.options.firstOrNull { it.value == param.value }?.label
                            ?: param.value ?: "—"
                        Text(
                            selectedLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать")
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    param.options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = {
                                dropdownExpanded = false
                                onSelect(param, opt.value)
                            },
                        )
                    }
                }
            }
        }
        "button" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                param.description?.let {
                    Text(
                        it,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = {
                    // Per-key actions (e.g. change_notify_email) — не реализованы в этом экране.
                    Toast.makeText(
                        context,
                        "Действие «${param.title ?: param.key}» недоступно",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Text(param.title ?: "Действие")
                }
            }
        }
        "warning" -> {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFC107).copy(alpha = 0.15f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    param.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                    param.description?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        else -> {
            param.title?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun BannedUserRow(user: BannedUser, onUnban: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = user.photo100 ?: user.photo200
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.fullName)
            if (user.banDate > 0) {
                val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                Text(
                    "Заблокирован ${fmt.format(Date(user.banDate * 1000))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        TextButton(onClick = onUnban) { Text("Разблокировать") }
    }
}

@Composable
private fun ObsceneFilterRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Фильтр нецензурной лексики")
            Text(
                "Скрывать сообщения с нецензурными словами",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
