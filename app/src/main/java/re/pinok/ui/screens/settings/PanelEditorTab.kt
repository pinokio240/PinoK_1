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
 * Все редактируемые пункты боковой панели (dynamic-пункты, без фикс. хвоста).
 * Порядок в этом списке = canonical-порядок для initial/reset.
 * #OFFLINE-DUPLICATE-FIX: OfflineManager убран — он в фикс. хвосте drawer.
 * #ARCH-CONTAINERS (Этап 1.4/1.5-а): CallsHistory и Photos убраны —
 * контейнерные пункты панели (NavEntry) редактором не редактируются
 * (список зеркалит sidebarEditableScreens в SovaNavHost).
 */
private val SIDEBAR_EDITABLE_SCREENS: List<Screen> = listOf(
    Screen.Friends, Screen.Groups, Screen.Search,
    Screen.Bookmarks, Screen.Documents, Screen.Clips,
    Screen.Services, Screen.Notifications, Screen.Logs,
    Screen.Equalizer,
)

/**
 * Все пункты нижней панели.
 * #SIDEBAR-BOTTOM-UNION (2026-08-01): раньше только 5 dock-кнопок. Теперь
 * добавлены все sidebar-пункты (включая OfflineManager) — пользователь может
 * поместить любую кнопку на нижнюю панель.
 * #BOTTOM-DEFAULT-4 (2026-08-01): по умолчанию visible только 4 (Профиль,
 * Сообщения, Музыка, Видео), остальные скрыты. Если включить >5 — панель
 * становится горизонтально прокручиваемой (см. #BOTTOM-SCROLL в SovaNavHost).
 * #ARCH-CONTAINERS (Этап 1.5-а): Photos здесь ОСТАВЛЕН — Dock/нижняя панель —
 * ядерная собственность хоста (Правило владения UI); ярлык «Фото» навигирует
 * на destination "photos" и работает независимо от контейнера.
 */
private val BOTTOMBAR_EDITABLE_SCREENS: List<Screen> = listOf(
    Screen.Profile, Screen.Messages, Screen.Music, Screen.Video,
    Screen.Feed,
    Screen.Friends, Screen.Groups, Screen.Photos, Screen.Search,
    Screen.Bookmarks, Screen.Documents, Screen.Clips,
    Screen.Services, Screen.Notifications, Screen.Logs,
    Screen.OfflineManager, Screen.Equalizer,
)

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
 * Нормализует order под canonical-список: убирает неизвестные route,
 * добавляет недостающие (новые пункты после обновления) в конец.
 * Гарантирует, что в order есть ВСЕ пункты из [canonical] ровно по разу.
 */
private fun normalizeOrder(order: List<String>, canonical: List<Screen>): List<String> {
    val canonicalRoutes = canonical.map { it.route }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    // Сохраняем порядок пользователя, фильтруя неизвестные/дубли.
    for (r in order) {
        if (r in canonicalRoutes && r !in seen) {
            result.add(r)
            seen.add(r)
        }
    }
    // Добавляем новые пункты (которых не было в сохранённом order).
    for (s in canonical) {
        if (s.route !in seen) {
            result.add(s.route)
            seen.add(s.route)
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
        mutableStateOf(normalizeOrder(parseRoutes(s.sidebarItemsOrder), SIDEBAR_EDITABLE_SCREENS))
    }
    var sidebarHidden by remember(s.sidebarItemsHidden) {
        mutableStateOf(parseRoutes(s.sidebarItemsHidden).toSet())
    }
    var bottomOrder by remember(s.bottomBarItemsOrder) {
        mutableStateOf(normalizeOrder(parseRoutes(s.bottomBarItemsOrder), BOTTOMBAR_EDITABLE_SCREENS))
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
                orderedRoutes = sidebarOrder,
                hiddenRoutes = sidebarHidden,
                screenByRoute = SIDEBAR_EDITABLE_SCREENS.associateBy { it.route },
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
                onToggleVisible = { route ->
                    sidebarHidden = if (route in sidebarHidden) {
                        sidebarHidden - route
                    } else {
                        sidebarHidden + route
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
                    // Профиль, Сообщения, Музыка, Видео).
                    val defaultOrder = listOf(
                        Screen.Profile.route, Screen.Messages.route,
                        Screen.Music.route, Screen.Video.route,
                    ) + BOTTOMBAR_EDITABLE_SCREENS.filter { it.route !in listOf(
                        Screen.Profile.route, Screen.Messages.route,
                        Screen.Music.route, Screen.Video.route,
                    ) }.map { it.route }
                    val defaultHidden = BOTTOMBAR_EDITABLE_SCREENS
                        .filter { it.route !in listOf(
                            Screen.Profile.route, Screen.Messages.route,
                            Screen.Music.route, Screen.Video.route,
                        ) }.map { it.route }.toSet()
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
                orderedRoutes = bottomOrder,
                hiddenRoutes = bottomHidden,
                screenByRoute = BOTTOMBAR_EDITABLE_SCREENS.associateBy { it.route },
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
                onToggleVisible = { route ->
                    bottomHidden = if (route in bottomHidden) {
                        bottomHidden - route
                    } else {
                        bottomHidden + route
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
    orderedRoutes: List<String>,
    hiddenRoutes: Set<String>,
    screenByRoute: Map<String, Screen>,
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
            orderedRoutes.forEachIndexed { idx, route ->
                val screen = screenByRoute[route] ?: return@forEachIndexed
                val visible = route !in hiddenRoutes
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
                    if (screen.icon != null) {
                        Icon(
                            screen.icon,
                            contentDescription = null,
                            tint = if (visible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        screen.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (visible) FontWeight.Medium else FontWeight.Normal,
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Visibility toggle
                    IconButton(
                        onClick = { onToggleVisible(route) },
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
                        enabled = idx < orderedRoutes.lastIndex,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            tint = if (idx < orderedRoutes.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
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
