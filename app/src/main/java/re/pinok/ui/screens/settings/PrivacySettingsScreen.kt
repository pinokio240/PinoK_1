package re.pinok.ui.screens.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.SettingsParam
import re.pinok.data.model.SettingsSection
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════
// §PROFILE-P4: Экран «VK-приватность» (этап П-4, инвентарь §4.1 п.10 / §5 п.5)
//   Серверные настройки приватности через BFF:
//     - чтение:  settingsGeneral.getPrivacySettings(page="privacy")
//                → те же секции ParamRow (toggle/select/button/warning),
//                  что и в NotificationSettingsScreen (локальные копии —
//                  NotificationSettingsScreen не тронут);
//     - тумблер: settingsGeneral.toggleNotify(key, v) (bool → "true"/"false");
//     - select:  settingsGeneral.setPrivacySettings(key, value).
//   UI-паттерн «Изменения сохранены» — settings_basic_changes_saved из §1.4
//   (m.vk.ru/settings?act=notify). Legacy al_settings.php web-пути (исключения
//   «…кроме N друзей», модалка выбора друзей) в BFF не приходят — если секция
//   не отдала options, рендерится select без опций (честно, no-stub).
// ═══════════════════════════════════════════════════════════

/** Через сколько мс скрывать индикатор «Изменения сохранены». */
private const val PRIVACY_SAVED_INDICATOR_MS = 2000L

/**
 * Экран VK-приватности (BFF settingsGeneral.*). Оптимистичный UI:
 * переключатель/значение двигаются мгновенно, при ошибке API — откат + Toast
 * с lastApiError.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<SettingsSection>>(emptyList()) }

    // Индикатор сохранения (settings_basic_changes_saved): ключи в полёте +
    // флаг «изменения сохранены» с авто-скрытием.
    var savingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSaved by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        error = null
        val secs = app.apiClient.settingsGeneralGetPrivacySettings()
        sections = secs ?: emptyList()
        if (secs == null) {
            error = "Не удалось загрузить настройки приватности. " +
                "Проверьте подключение к сети."
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            loadAll()
        } catch (e: Exception) {
            AppLog.e("PrivacySettings", "loadAll error", e)
            error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
        } finally {
            loading = false
        }
    }

    // Авто-скрытие «Изменения сохранены».
    LaunchedEffect(showSaved) {
        if (showSaved) {
            delay(PRIVACY_SAVED_INDICATOR_MS)
            showSaved = false
        }
    }

    /**
     * Оптимистичное применение значения параметра с откатом при ошибке.
     * @param rollback откат стейта к прежнему значению
     * @param setter сетевой вызов (toggle или set)
     */
    fun applyParam(
        param: SettingsParam,
        rollback: () -> Unit,
        setter: suspend () -> Boolean,
    ) {
        savingKeys = savingKeys + param.key
        showSaved = false
        scope.launch {
            val ok = try {
                setter()
            } catch (e: Exception) {
                AppLog.e("PrivacySettings", "applyParam(${param.key}) failed", e)
                false
            }
            savingKeys = savingKeys - param.key
            if (ok) {
                showSaved = true
            } else {
                rollback()
                Toast.makeText(
                    context,
                    "Не удалось изменить: " +
                        (app.apiClient.lastApiError ?: "нет ответа сервера"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VK-приватность") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            refreshing = true
                            try {
                                loadAll()
                            } catch (e: Exception) {
                                AppLog.e("PrivacySettings", "refresh error", e)
                                error = "Ошибка обновления: ${e.message ?: "неизвестная"}"
                            } finally {
                                refreshing = false
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
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
                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    try {
                                        loadAll()
                                    } catch (e: Exception) {
                                        AppLog.e("PrivacySettings", "retry error", e)
                                        error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Повторить")
                        }
                    }
                }
            }
            sections.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Настройки приватности пусты",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                loadAll()
                            } catch (e: Exception) {
                                AppLog.e("PrivacySettings", "pull-refresh error", e)
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        // Индикатор «Сохранение… / Изменения сохранены».
                        item(key = "save_indicator") {
                            PrivacySaveIndicator(
                                saving = savingKeys.isNotEmpty(),
                                saved = showSaved,
                            )
                        }
                        sections.forEach { section ->
                            // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                            // чужого модуля невозможен; захват в локальный val.
                            val sectionTitle = section.title
                            if (!sectionTitle.isNullOrBlank()) {
                                item(key = "header_${section.id}") {
                                    PrivacySectionHeader(title = sectionTitle)
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
                                PrivacyParamRow(
                                    param = param,
                                    saving = param.key in savingKeys,
                                    onToggle = { p, newValue ->
                                        val prev = sections
                                        sections = sections.map { s ->
                                            s.copy(params = s.params.map { pp ->
                                                if (pp.key == p.key) {
                                                    pp.copy(isChecked = newValue)
                                                } else {
                                                    pp
                                                }
                                            })
                                        }
                                        applyParam(
                                            param = p,
                                            rollback = { sections = prev },
                                            setter = {
                                                app.apiClient.settingsGeneralToggleNotify(
                                                    p.key,
                                                    newValue,
                                                )
                                            },
                                        )
                                    },
                                    onSelect = { p, value ->
                                        val prev = sections
                                        sections = sections.map { s ->
                                            s.copy(params = s.params.map { pp ->
                                                if (pp.key == p.key) {
                                                    pp.copy(value = value)
                                                } else {
                                                    pp
                                                }
                                            })
                                        }
                                        applyParam(
                                            param = p,
                                            rollback = { sections = prev },
                                            setter = {
                                                app.apiClient.settingsGeneralSetPrivacySettings(
                                                    p.key,
                                                    value,
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Индикатор «Сохранение…» / «Изменения сохранены» (settings_basic_changes_saved). */
@Composable
private fun PrivacySaveIndicator(saving: Boolean, saved: Boolean) {
    if (!saving && !saved) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Text(
                "Сохранение…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            Text(
                "Изменения сохранены",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PrivacySectionHeader(title: String, icon: ImageVector? = null) {
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

/**
 * Рендер одного параметра privacy-ответа BFF (локальная копия ParamRow из
 * NotificationSettingsScreen — тот же файл-паттерн, но с setPrivacySettings
 * для select и индикатором сохранения):
 * - "toggle" / "custom_toggle" → Switch row (settingsGeneral.toggleNotify)
 * - "select" / "radio" → dropdown (settingsGeneral.setPrivacySettings)
 * - "button" → TextButton row (действие недоступно — честный Toast)
 * - "warning" → карточка
 * - прочее → просто title
 */
@Composable
private fun PrivacyParamRow(
    param: SettingsParam,
    saving: Boolean,
    onToggle: (SettingsParam, Boolean) -> Unit,
    onSelect: (SettingsParam, String) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    when (param.type) {
        "toggle", "custom_toggle" -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .defaultMinSize(minHeight = 44.dp),
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
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = param.isChecked == true,
                        onCheckedChange = { onToggle(param, it) },
                    )
                }
            }
        }
        "select", "radio" -> {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .defaultMinSize(minHeight = 44.dp)
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
                        val selectedLabel = param.options
                            .firstOrNull { it.value == param.value }?.label
                            ?: param.value ?: "—"
                        Text(
                            selectedLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать")
                    }
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    // Опции приходит только из BFF-ответа; если секция их не
                    // отдала — dropdown пуст (no-stub: ничего не придумываем).
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
                TextButton(
                    onClick = {
                        // Per-key action-методов для privacy-кнопок в BFF-бандлах
                        // нет — честный отказ, не имитация.
                        Toast.makeText(
                            context,
                            "Действие «${param.title ?: param.key}» недоступно",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
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
