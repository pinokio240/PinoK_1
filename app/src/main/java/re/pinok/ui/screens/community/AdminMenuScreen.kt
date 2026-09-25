// File: ui/screens/community/AdminMenuScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════════
// ADMIN-MENU (#ADMIN-MENU): «Меню» сообщества — админ-блок (web-only owners.*).
// READ  — owners.getMenu {owner_id=-gid, is_hidden=0|1} → {can_add, items[],
//         is_hidden} (HAR §2). Запрашиваем ДВА списка (видимые и скрытые) и
//         объединяем — иначе скрытые пункты не увидеть.
// WRITE — owners.addMenuItem {owner_id, title, url, crop_data}; скрыть/показать
//         ВСЁ меню — owners.hideMenu / owners.showMenu (формат HAR, response:1).
//
// ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub, как требует проект):
//  1) Переименование/смена URL пункта — метод owners.editMenuItem в HAR НЕ снят
//     → не реализовано; у пунктов с can_edit_title/can_edit_url подсказка.
//  2) Удаление пункта — owners.deleteMenuItem в HAR и в коде проекта НЕ найден
//     → кнопку удаления НЕ рисуем даже при settings.can_delete («в веб-версии»).
//  3) Точечное скрытие ОТДЕЛЬНОГО пункта — метод не снят; toggle рисуем только
//     при settings.can_edit_hidden и в disabled-состоянии с пояснением (не
//     притворяемся, что можем писать).
// ═══════════════════════════════════════════════════════════════════════════

/** Человекочитаемый тип пункта меню (owners.getMenu type). */
private fun menuTypeLabel(type: String): String = when (type) {
    "photo" -> "Фото"
    "app" -> "Приложение"
    "link" -> "Ссылка"
    else -> type
}

/**
 * Гейты пункта меню. settings может не прийти (partial-объект) — тогда честный
 * дефолт: без прав на редактирование и видимый пункт.
 */
private fun menuItemSettings(item: VKApiClient.OwnerMenuItem): VKApiClient.OwnerMenuItemSettings {
    val st = item.settings
    if (st != null) return st
    return VKApiClient.OwnerMenuItemSettings(
        appId = null,
        canDelete = false,
        canEditApp = false,
        canEditHidden = false,
        canEditTitle = false,
        canEditUrl = false,
        hidden = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMenuScreen(groupId: Long, onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf<List<VKApiClient.OwnerMenuItem>>(emptyList()) }
    var hidden by remember { mutableStateOf<List<VKApiClient.OwnerMenuItem>>(emptyList()) }
    var menuHidden by remember { mutableStateOf(false) }
    var canAdd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val vis = app.apiClient.ownersGetMenu(groupId, isHidden = false)
                val hid = app.apiClient.ownersGetMenu(groupId, isHidden = true)
                val base = vis
                val baseHidden = hid
                if (base == null && baseHidden == null) {
                    val err = app.apiClient.lastApiError
                    error = if (err.isNullOrBlank()) "Не удалось загрузить меню" else err
                } else {
                    visible = if (base != null) base.items else emptyList()
                    hidden = if (baseHidden != null) baseHidden.items else emptyList()
                    canAdd = if (base != null) base.canAdd else if (baseHidden != null) baseHidden.canAdd else false
                    menuHidden = if (base != null) base.isHidden else if (baseHidden != null) baseHidden.isHidden else false
                }
            } catch (e: Exception) {
                AppLog.e("AdminMenu", "load failed", e)
                // NULL-ЯВНО: однострочный фолбэк для отображаемого текста ошибки,
                // бывает исключение без message; логи подробнее в AppLog.
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    fun addItem() {
        val title = addTitle.trim()
        if (title.isBlank()) return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val item = app.apiClient.ownersAddMenuItem(groupId, title, addUrl.trim())
                if (item == null) {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось добавить пункт" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    showAdd = false
                    Toast.makeText(context, "Пункт добавлен", Toast.LENGTH_SHORT).show()
                    load()
                }
            } catch (e: Exception) {
                AppLog.e("AdminMenu", "addItem failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun toggleWholeMenu() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val hideNow = menuHidden
                val ok = if (hideNow) {
                    app.apiClient.ownersShowMenu(groupId)
                } else {
                    app.apiClient.ownersHideMenu(groupId)
                }
                if (ok) {
                    Toast.makeText(
                        context,
                        if (hideNow) "Меню показано" else "Меню скрыто",
                        Toast.LENGTH_SHORT,
                    ).show()
                    load()
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось изменить видимость меню" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminMenu", "toggleWholeMenu failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Меню") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            addTitle = ""
                            addUrl = ""
                            showAdd = true
                        },
                        enabled = !busy && canAdd,
                    ) { Text("Добавить") }
                },
            )
        },
    ) { pad ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null -> ErrorView(
                message = error,
                onRetry = { load() },
                modifier = Modifier.padding(pad),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
                // ── Блок «Скрыть/Показать» всё меню (owners.hideMenu/showMenu) ──
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (menuHidden) "Меню скрыто" else "Меню показывается",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Видимость меню для участников",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = { toggleWholeMenu() }, enabled = !busy) {
                                Text(if (menuHidden) "Показать меню" else "Скрыть меню")
                            }
                        }
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

                val allItems = visible + hidden
                if (allItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Пунктов меню нет",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(allItems, key = { it.id }) { mi ->
                        val st = menuItemSettings(mi)
                        MenuItemRow(
                            item = mi,
                            isHiddenNow = st.hidden,
                            canEditHidden = st.canEditHidden,
                            canDelete = st.canDelete,
                            canEditTitle = st.canEditTitle,
                            canEditUrl = st.canEditUrl,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                item {
                    Text(
                        "Переименование, удаление и скрытие отдельных пунктов — только в веб-версии " +
                            "админ-панели сообщества. Здесь можно добавить пункт и скрыть/показать всё меню.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddMenuItemDialog(
            title = addTitle,
            url = addUrl,
            busy = busy,
            onTitleChange = { addTitle = it },
            onUrlChange = { addUrl = it },
            onDismiss = { if (!busy) showAdd = false },
            onAdd = { addItem() },
        )
    }
}

/** Строка пункта меню: название, тип, URL, бейдж «Скрыт», гейты-подсказки. */
@Composable
private fun MenuItemRow(
    item: VKApiClient.OwnerMenuItem,
    isHiddenNow: Boolean,
    canEditHidden: Boolean,
    canDelete: Boolean,
    canEditTitle: Boolean,
    canEditUrl: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isHiddenNow) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Скрыт",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val typeText = menuTypeLabel(item.type)
            Text(
                if (item.url.isNotBlank()) "$typeText · ${item.url}" else typeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (canDelete || canEditHidden || canEditTitle || canEditUrl) {
                Text(
                    "Правка и удаление пункта — только в веб-версии",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canEditHidden) {
            Spacer(Modifier.width(8.dp))
            // Switch DISABLED: метод записи видимости отдельного пункта
            // (owners.editMenuItem / hideMenuItem) в HAR и коде не найден —
            // не притворяемся, что пишем. Гейт can_edit_hidden показывает,
            // что видимость пункта вообще редактируема (но только из веба).
            Switch(checked = !isHiddenNow, onCheckedChange = null, enabled = false)
        }
    }
}

/** Диалог добавления пункта меню (название + URL). */
@Composable
private fun AddMenuItemDialog(
    title: String,
    url: String,
    busy: Boolean,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый пункт") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("URL (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAdd,
                enabled = !busy && title.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
        },
    )
}