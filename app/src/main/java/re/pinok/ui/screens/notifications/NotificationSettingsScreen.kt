package re.pinok.ui.screens.notifications

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications

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
import re.pinok.data.model.Group
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
 *  - Уведомления сообществ (этап П-4, §1.5): groups.get(filter=editor) +
 *    groupsEditNotifications — тумблер УРОВНЯ группы (событийных тумблеров §1.5
 *    в API нет — см. KDoc секции)
 *  - Фильтр нецензурной лексики (account.setObsceneFilter)
 *
 * §PROFILE-P4: секция «Заблокированные» (account.getBanned/unban) ПЕРЕНЕСЕНА в
 * самостоятельный экран BlacklistScreen (ui/screens/settings) — строка-вход
 * осталась на вкладке «Уведомления» SettingsScreen.
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
    // §PROFILE-P4 (§1.5): сообщества, где я админ/редактор — тумблер уровня группы.
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    // Локальное состояние тумблера по group.id. Начальное значение API НЕ отдаёт
    // (геттера «уведомления сообщества» в VK API нет) — считаем включёнными
    // (VK-дефолт «Отображать в ленте уведомлений» = вкл), честно указано в описании секции.
    var groupNotifyState by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var groupBusy by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // #SETTINGS-FIX (P2-6, аудит настроек ⚠️4): начальное состояние не читается — геттера
    // account.getObsceneFilter в VK API/бандлах НЕТ (профиль.снапшоты.инвентарь.md:341 —
    // оба MISS). Фактическое состояние сервера до первого переключения неизвестно;
    // показывается честная подпись (ObsceneFilterRow). Запись работает (VKA:14867).
    var obsceneFilter by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        loading = true
        error = null
        try {
            val sm = app.apiClient.accountGetSilentModeStatus()
            val secs = app.apiClient.settingsGeneralGetNotifySettings()
            val grps = app.apiClient.groupsGet(filter = "editor")
            silentMode = sm
            sections = secs ?: emptyList()
            groups = grps
            if (sm == null && secs == null) {
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
                // #SETTINGS-FIX (P3-7, аудит настроек): псевдо-пункт меню «Сбросить» удалён —
                // он показывал честный тост «не поддерживается VK API», т.е. был пустой
                // кнопкой (лучше нет пункта, чем мёртвый). Все данные этого экрана —
                // серверные (BFF/settingsGeneral, silent mode, группы, obscene-фильтр):
                // локальных push-ключей здесь нет, честная семантика «сброса» отсутствует.
                actions = {},
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
                        // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast чужого
                        // модуля невозможен; захват в локальный val (работает и через item{}).
                        val sectionTitle = section.title
                        if (!sectionTitle.isNullOrBlank()) {
                            item(key = "header_${section.id}") {
                                SectionHeader(title = sectionTitle)
                            }
                        }
                        val sectionDescription = section.description
                        if (!sectionDescription.isNullOrBlank()) {
                            item(key = "desc_${section.id}") {
                                Text(
                                    sectionDescription,
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

                    // 3. Уведомления сообществ (§PROFILE-P4, §1.5)
                    item(key = "header_groups") {
                        SectionHeader(title = "Уведомления сообществ", icon = Icons.Default.Group)
                    }
                    item(key = "groups_desc") {
                        Text(
                            "Сообщества, где вы администратор или редактор. " +
                                "Тумблер — «Отображать в ленте уведомлений» для всего " +
                                "сообщества. Отдельные события (комментарии, упоминания, " +
                                "предложенные записи и др.) в VK API недоступны. " +
                                "Положение тумблера до первого переключения не читается " +
                                "из API — показан VK-дефолт «вкл».",
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, bottom = 4.dp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (groups.isEmpty()) {
                        item(key = "groups_empty") {
                            Text(
                                "Нет сообществ, где вы администратор или редактор",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(groups, key = { "group_${it.id}" }) { group ->
                            GroupNotifyRow(
                                group = group,
                                checked = groupNotifyState[group.id] ?: true,
                                busy = group.id in groupBusy,
                                onToggle = { g, newValue ->
                                    val prevMap = groupNotifyState
                                    groupNotifyState = groupNotifyState +
                                        (g.id to newValue)
                                    groupBusy = groupBusy + g.id
                                    scope.launch {
                                        val ok = try {
                                            // Сигнатура groupsEditNotifications(groupId, …)
                                            // внутри инвертирует знак (wire group_id =
                                            // -groupId), поэтому передаётся owner-style
                                            // отрицательный id → на провод положительный
                                            // group_id по спецификации VK API groups.edit.
                                            app.apiClient.groupsEditNotifications(
                                                -g.id,
                                                newValue,
                                            )
                                        } catch (e: Exception) {
                                            AppLog.e(
                                                "NotificationSettings",
                                                "groupsEditNotifications(${g.id}) failed",
                                                e,
                                            )
                                            false
                                        }
                                        groupBusy = groupBusy - g.id
                                        if (!ok) {
                                            groupNotifyState = prevMap
                                            Toast.makeText(
                                                context,
                                                "Не удалось изменить: " +
                                                    (app.apiClient.lastApiError
                                                        ?: "нет ответа сервера"),
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
            // #SETTINGS-FIX (P3-8, аудит настроек ⚠️5): select/radio БЕЗ options —
            // рендер значения БЕЗ шеврона/кликабельности/dropdown (пустой dropdown
            // выглядел как баг). legacy-исключения «…кроме N друзей» в BFF не приходят
            // (al_settings.php, no-stub §5.6) — честный просмотр значения.
            val canEdit = param.options.isNotEmpty()
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (canEdit) {
                                Modifier.clickable { dropdownExpanded = true }
                            } else {
                                Modifier // read-only: options нет — dropdown не открываем
                            },
                        ),
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
                    if (canEdit) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать")
                    }
                }
                if (canEdit) {
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

/**
 * §PROFILE-P4 (§1.5): строка сообщества с тумблером уровня группы.
 *
 * ЧЕСТНОЕ ОТКЛОНЕНИЕ от §1.5 (no-stub): 16 событийных тумблеров подстраницы
 * (Комментарии, Предложенные записи, Упоминания, Реакции, Поделились,
 * Новые подписчики, Истории, Непрочитанные сообщения, Статистика,
 * Завершённые опросы, Советы по продвижению, Модерация товаров, Соавторство,
 * Отзывы о сообществе, Отметки сообщества, Комментарии к товарам) и
 * «Удалить источник» живут на legacy al_settings.php (group_notify_*) —
 * wire-путей в снапшоте НЕТ, BFF-ключей в бандлах НЕ обнаружено.
 * Единственный живой путь — groups.edit{group_id, notifications} — покрывает
 * только вкл/выкл уровня группы (в вебе этот флаг называется «Отображать
 * в ленте уведомлений»). Поэтому: один тумблер на группу, события не имитируются.
 */
@Composable
private fun GroupNotifyRow(
    group: Group,
    checked: Boolean,
    busy: Boolean,
    onToggle: (Group, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = group.photo100 ?: group.photo200
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
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name)
            if (group.screenName != null) {
                Text(
                    group.screenName ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Switch(checked = checked, onCheckedChange = { onToggle(group, it) })
        }
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
            // #SETTINGS-FIX (P2-6, аудит настроек ⚠️4): ЧЕСТНОЕ ОТОБРАЖЕНИЕ СТАТУСА.
            // Геттера account.getObsceneFilter в VK API/бандлах нет — до первого
            // переключения положение тумблера НЕ отражает сервер (после переключения —
            // оптимистично корректно, откат при ошибке).
            Text(
                "Состояние сервера не читается (геттера в VK API нет): точное положение " +
                    "тумблера видно после первого переключения",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
