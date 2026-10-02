package re.pinok.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import re.pinok.SovaApp
import re.pinok.data.local.SovaPrefs
import re.pinok.ui.navigation.PanelItem
import re.pinok.ui.navigation.PanelItems
import re.pinok.ui.navigation.Screen

// ══════════════════════════════════════════════════════════════════════
//  Fix #337: «Редактор панелей» — настройка боковой и нижней панелей.
//
//  Пользователь может:
//   - Включать/выключать кнопки в обеих панелях.
//   - Менять порядок кнопок стрелками ↑/↓.
//
//  Ограничения для боковой панели (по требованию пользователя):
//   - «Выйти из приложения» — всегда внизу, не редактируется.
//   - «Настройки» — всегда над «Выйти», не редактируется.
//   - «Офлайн» — всегда над «Настройки», не редактируется.
//  Эти 3 кнопки НЕ входят в sidebarItemsOrder/Hidden — они рендерятся
//  отдельно в SovaNavHost (фиксированный «хвост» drawer'а).
//
//  Нижняя панель — редактируется полностью (все 5 кнопок dockScreens).
// ══════════════════════════════════════════════════════════════════════

/**
 * Все редактируемые пункты боковой и нижней панелей.
 * #PANELEDIT (2026-10-02): единый канонический набор — PanelItems.all
 * (см. ui/navigation/PanelItems.kt). Раньше были два разнесённых Screen-списка
 * (SIDEBAR_EDITABLE_SCREENS / BOTTOMBAR_EDITABLE_SCREENS); теперь обе панели
 * используют ОДИН список из 14 пунктов по требованию пользователя, а фикси-
 * рованный хвост drawer (Офлайн/Настройки/Выйти) по-прежнему не редактируется.
 */
private val CANONICAL_PANEL_ITEMS: List<PanelItem> = PanelItems.all

/**
 * Фиксированный «хвост» боковой панели (сверху-вниз): Офлайн → Настройки → Выйти.
 * Не редактируется пользователем. Показывается в редакторе как info-блок.
 */
private val SIDEBAR_FIXED_TAIL: List<Pair<String, ImageVector?>> = listOf(
    Screen.OfflineManager.title to Screen.OfflineManager.icon,
    Screen.Settings.title to Screen.Settings.icon,
    "Выйти из приложения" to null,
)

// ── JSON helpers ────────────────────────────────────────────────────────

private fun parseRoutes(json: String): List<String> {
    return try {
        val arr = JSONArray(if (json.isBlank()) "[]" else json)
        buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun List<String>.toJson(): String {
    val arr = JSONArray()
    forEach { arr.put(it) }
    return arr.toString()
}

/**
 * Нормализует order под canonical-список: убирает неизвестные keys,
 * добавляет недостающие (новые пункты после обновления) в конец.
 * Гарантирует, что в order есть ВСЕ пункты из [canonical] ровно по разу.
 */
private fun normalizeOrder(order: List<String>, canonical: List<PanelItem>): List<String> {
    val canonicalKeys = canonical.map { it.key }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    // Сохраняем порядок пользователя, фильтруя неизвестные/дубли.
    for (k in order) {
        if (k in canonicalKeys && k !in seen) {
            result.add(k)
            seen.add(k)
        }
    }
    // Добавляем новые пункты (которых не было в сохранённом order).
    for (item in canonical) {
        if (item.key !in seen) {
            result.add(item.key)
            seen.add(item.key)
        }
    }
    return result
}

// ── Tab composable ──────────────────────────────────────────────────────

@Composable
fun PanelEditorTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    // Локальный редактируемый state — коммитим в prefs при каждом изменении.
    var sidebarOrder by remember(s.sidebarItemsOrder) {
        mutableStateOf(normalizeOrder(parseRoutes(s.sidebarItemsOrder), CANONICAL_PANEL_ITEMS))
    }
    var sidebarHidden by remember(s.sidebarItemsHidden) {
        mutableStateOf(parseRoutes(s.sidebarItemsHidden).toSet())
    }
    var bottomOrder by remember(s.bottomBarItemsOrder) {
        mutableStateOf(normalizeOrder(parseRoutes(s.bottomBarItemsOrder), CANONICAL_PANEL_ITEMS))
    }
    var bottomHidden by remember(s.bottomBarItemsHidden) {
        mutableStateOf(parseRoutes(s.bottomBarItemsHidden).toSet())
    }

    fun commitSidebar() {
        scope.launch {
            app.prefs.setSidebarItemsOrder(sidebarOrder.toJson())
            app.prefs.setSidebarItemsHidden(sidebarHidden.toList().toJson())
        }
    }
    fun commitBottom() {
        scope.launch {
            app.prefs.setBottomBarItemsOrder(bottomOrder.toJson())
            app.prefs.setBottomBarItemsHidden(bottomHidden.toList().toJson())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Боковая панель") }
        item {
            Text(
                "Перетаскивайте кнопки стрелками ↑↓ и включайте/выключайте их. " +
                    "Кнопки «Офлайн», «Настройки» и «Выйти из приложения» закреплены " +
                    "внизу панели и не редактируются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item {
            ReorderableListCard(
                title = "Кнопки панели",
                orderedKeys = sidebarOrder,
                hiddenKeys = sidebarHidden,
                itemByKey = PanelItems.byKey,
                onMoveUp = { idx ->
                    if (idx > 0) {
                        sidebarOrder = sidebarOrder.toMutableList().apply {
                            add(idx - 1, removeAt(idx))
                        }
                        commitSidebar()
                    }
                },
                onMoveDown = { idx ->
                    if (idx < sidebarOrder.lastIndex) {
                        sidebarOrder = sidebarOrder.toMutableList().apply {
                            add(idx + 1, removeAt(idx))
                        }
                        commitSidebar()
                    }
                },
                onToggleVisible = { key ->
                    sidebarHidden = if (key in sidebarHidden) {
                        sidebarHidden - key
                    } else {
                        sidebarHidden + key
                    }
                    commitSidebar()
                },
            )
        }
        item { FixedTailCard() }

        item { SectionHeader("Нижняя панель") }
        item {
            Text(
                "Редактируется полностью: порядок, видимость. " +
                    "Если скрыть все кнопки — панель скроется целиком. " +
                    "При больше 5 видимых кнопок панель прокручивается вбок.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    // #BOTTOM-DEFAULT-4: сброс к дефолту (4 кнопки:
                    // Уведомления, Мессенджер, Сообщества, Фотографии).
                    val defaultVisible = listOf(
                        "notifications", "messenger", "groups", "photos",
                    )
                    val defaultOrder = defaultVisible +
                        CANONICAL_PANEL_ITEMS.filter { it.key !in defaultVisible }.map { it.key }
                    val defaultHidden = CANONICAL_PANEL_ITEMS
                        .filter { it.key !in defaultVisible }.map { it.key }.toSet()
                    bottomOrder = defaultOrder
                    bottomHidden = defaultHidden
                    commitBottom()
                }) {
                    Text("Сбросить по умолчанию")
                }
            }
        }
        item {
            ReorderableListCard(
                title = "Кнопки панели",
                orderedKeys = bottomOrder,
                hiddenKeys = bottomHidden,
                itemByKey = PanelItems.byKey,
                onMoveUp = { idx ->
                    if (idx > 0) {
                        bottomOrder = bottomOrder.toMutableList().apply {
                            add(idx - 1, removeAt(idx))
                        }
                        commitBottom()
                    }
                },
                onMoveDown = { idx ->
                    if (idx < bottomOrder.lastIndex) {
                        bottomOrder = bottomOrder.toMutableList().apply {
                            add(idx + 1, removeAt(idx))
                        }
                        commitBottom()
                    }
                },
                onToggleVisible = { key ->
                    bottomHidden = if (key in bottomHidden) {
                        bottomHidden - key
                    } else {
                        bottomHidden + key
                    }
                    commitBottom()
                },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Subcomponents ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ReorderableListCard(
    title: String,
    orderedKeys: List<String>,
    hiddenKeys: Set<String>,
    itemByKey: Map<String, PanelItem>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onToggleVisible: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            orderedKeys.forEachIndexed { idx, key ->
                val item = itemByKey[key] ?: return@forEachIndexed
                val visible = key !in hiddenKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (visible) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (item.icon != null) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (visible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (visible) FontWeight.Medium else FontWeight.Normal,
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Visibility toggle
                    IconButton(
                        onClick = { onToggleVisible(key) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (visible) "Скрыть" else "Показать",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Move up
                    IconButton(
                        onClick = { onMoveUp(idx) },
                        enabled = idx > 0,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Вверх",
                            tint = if (idx > 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                    // Move down
                    IconButton(
                        onClick = { onMoveDown(idx) },
                        enabled = idx < orderedKeys.lastIndex,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            tint = if (idx < orderedKeys.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedTailCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Закреплённые кнопки (не редактируются)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Всегда в этом порядке внизу боковой панели.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SIDEBAR_FIXED_TAIL.forEach { (title, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        // Выход из приложения — иконка PowerSettingsNew из SovaNavHost.
                        Icon(
                            Icons.Outlined.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
