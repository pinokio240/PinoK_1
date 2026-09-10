// File: ui/screens/community/CommunityAdminScreens.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.UserProfile
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════
// W35-b (волна 35): экраны администрирования сообщества.
//
// Источники:
//  - docs/админ.сообществ.снапшоты.сверка.md (Task 34-b, снапшоты «Группа_админ»,
//    14 страниц vk.ru): блок «Управление» = 11 пунктов (§4.1), порог показа
//    admin_level >= 1 (§5.1), настройка = ЧТЕНИЕ groups.getById + ЗАПИСЬ единым
//    groups.edit (§2 P1.5 — getSettings/setSettings вебом не вызываются),
//    статистика — wire-эталона нет (§2 P1.6) → реализация по официальным докам
//    stats.get / stats.getPostReach, руководители — API-wire нет (§2 P1.4) →
//    официальный groups.getMembers(filter=managers) + groups.editManager.
//  - docs/план.волна-35.админ-сообщества.2026-09-10.md §2 (A4), §3 (B1-B3).
//
// ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
//  1) Пункты блока «Управление» без реализованной маршрутизации показываются
//     DISABLED с подписью «в волне 36» (не выдуманных маршрутов нет).
//  2) Смена screen_name (короткого адреса) через groups.edit может требовать
//     дополнительных прав у веб-токена — при ошибке честный тост с текстом API.
//  3) stats.get может вернуть пусто (нет прав/нет данных) — честный error-стейт.
//  4) Отклонить заявку на вступление официальным API НЕЛЬЗЯ (метода нет,
//     web-легаси) — реализовано только «Одобрить» (сверка §3/план B3).
// ═══════════════════════════════════════════════════════════════════════════

/** W35-b: человекочитаемая роль руководителя. */
private fun adminRoleLabel(role: String?, isOwner: Boolean): String = when {
    isOwner || role == "creator" -> "Хозяин"
    role == "administrator" -> "Администратор"
    role == "editor" -> "Редактор"
    role == "moderator" -> "Модератор"
    else -> "Участник"
}

// ═══════════════════════════════════════════════════════════════════════════
// Блок «Управление» в CommunityScreen (вкладка «Стена», над TabRow).
// ═══════════════════════════════════════════════════════════════════════════

/**
 * W35-b: карточка «Управление» — админский блок на экране сообщества
 * (референс: правое меню из 11 пунктов, сверка §4.1). Рендерится ТОЛЬКО при
 * [groupInfo] != null и [VKApiClient.GroupInfo.isManager].
 */
@Composable
fun CommunityAdminBlock(
    groupInfo: VKApiClient.GroupInfo?,
    onSettingsClick: (Long) -> Unit,
    onStatsClick: (Long) -> Unit,
    onPeopleClick: (groupId: Long, tab: String) -> Unit,
) {
    val gi = groupInfo ?: return
    if (!gi.isManager) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Управление",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Роль текущего пользователя — честный контекст прав.
                Text(
                    text = adminRoleLabel(null, false).let {
                        when {
                            gi.adminLevel >= 3 -> "Администратор"
                            gi.adminLevel == 2 -> "Редактор"
                            gi.adminLevel == 1 -> "Модератор"
                            else -> it
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ── Реализованные пункты (волна 35) ──
            AdminBlockRow(
                icon = Icons.Filled.Settings,
                title = "Настройки сообщества",
                subtitle = "Название, описание, сайт, адрес",
                onClick = { onSettingsClick(gi.id) },
            )
            AdminBlockRow(
                icon = Icons.Filled.BarChart,
                title = "Статистика",
                subtitle = "Посетители, охват, активность (30 дней)",
                onClick = { onStatsClick(gi.id) },
            )
            AdminBlockRow(
                icon = Icons.Filled.ManageAccounts,
                title = "Руководители",
                subtitle = "Роли и полномочия",
                onClick = { onPeopleClick(gi.id, "managers") },
            )
            AdminBlockRow(
                icon = Icons.Filled.PersonAdd,
                title = "Заявки",
                subtitle = "Одобрение вступления",
                onClick = { onPeopleClick(gi.id, "requests") },
            )
            AdminBlockRow(
                icon = Icons.Filled.Block,
                title = "Чёрный список",
                subtitle = "Забаненные участники",
                onClick = { onPeopleClick(gi.id, "banned") },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // ── Честные disabled-пункты (волна 36, план §4 C-серия) ──
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Сообщения", subtitle = "В волне 36", onClick = {}, enabled = false)
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Разделы", subtitle = "В волне 36", onClick = {}, enabled = false)
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Бизнес-инструменты", subtitle = "В волне 36", onClick = {}, enabled = false)
        }
    }
}

/** W35-b: строка пункта блока «Управление». */
@Composable
private fun AdminBlockRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Экран «Настройки сообщества» (B1).
// ═══════════════════════════════════════════════════════════════════════════

/**
 * W35-b: базовые настройки сообщества. Чтение — groups.getById (site/description/
 * screen_name/name), запись — ЕДИНЫЙ groups.edit (сверка §2 P1.5). Редактируемые
 * поля: Название, Описание, Сайт, Короткий адрес.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<VKApiClient.GroupInfo?>(null) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var screenName by remember { mutableStateOf("") }
    // Оригиналы для diff — шлём только изменённые параметры (честный groups.edit).
    var origTitle by remember { mutableStateOf("") }
    var origDescription by remember { mutableStateOf("") }
    var origWebsite by remember { mutableStateOf("") }
    var origScreenName by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val groups = app.apiClient.groupsGetById(listOf(groupId))
                val g = groups.firstOrNull()
                if (g == null) {
                    error = app.apiClient.lastApiError?.takeIf { it.isNotBlank() }
                        ?: "Сообщество не найдено"
                } else {
                    info = g
                    title = g.name; origTitle = g.name
                    description = g.description ?: ""; origDescription = g.description ?: ""
                    website = g.site ?: ""; origWebsite = g.site ?: ""
                    screenName = g.screenName ?: ""; origScreenName = g.screenName ?: ""
                }
            } catch (e: Exception) {
                AppLog.e("AdminSettings", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки сообщества") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
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

            else -> {
                val gi = info
                if (gi == null || !gi.isManager) {
                    // Честный гейт: экран доступен только руководителям.
                    ErrorView(
                        message = "Нет прав руководителя сообщества",
                        onRetry = null,
                        modifier = Modifier.padding(pad),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pad)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Изменения сохраняются одним запросом groups.edit (как в вебе)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Название") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Описание") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Сайт") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = screenName,
                            onValueChange = { screenName = it },
                            label = { Text("Короткий адрес (screen_name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                // NULL-ЯВНО: диф только изменённых непустых полей.
                                val params = mutableMapOf<String, String>()
                                if (title.isNotBlank() && title != origTitle) params["title"] = title
                                if (description != origDescription) params["description"] = description
                                if (website != origWebsite) params["website"] = website
                                if (screenName.isNotBlank() && screenName != origScreenName) {
                                    params["screen_name"] = screenName
                                }
                                if (params.isEmpty()) {
                                    Toast.makeText(context, "Нет изменений", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    saving = true
                                    try {
                                        val ok = app.apiClient.groupsEdit(groupId, params)
                                        if (ok) {
                                            origTitle = title
                                            origDescription = description
                                            origWebsite = website
                                            origScreenName = screenName
                                            Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = app.apiClient.lastApiError
                                            Toast.makeText(
                                                context,
                                                if (err.isNullOrBlank()) "Не удалось сохранить" else "Ошибка: $err",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        AppLog.e("AdminSettings", "groupsEdit failed", e)
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Сохранить")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Экран «Статистика» (B3).
// ═══════════════════════════════════════════════════════════════════════════

/**
 * W35-b: статистика сообщества за 30 дней (stats.get) + охват последних
 * 10 постов (stats.getPostReach). Wire-эталона нет (сверка §2 P1.6) —
 * формат по официальным докам VK API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStatsScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var points by remember { mutableStateOf<List<VKApiClient.StatsPoint>>(emptyList()) }
    var reach by remember { mutableStateOf<List<VKApiClient.PostReach>>(emptyList()) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val stats = app.apiClient.statsGet(groupId, days = 30)
                // Охват последних 10 постов стены сообщества (owner-style -id).
                val wall = app.apiClient.wallGet(ownerId = -groupId, count = 10)
                val postIds = wall.map { it.id }
                val postReach = if (postIds.isNotEmpty()) {
                    app.apiClient.statsGetPostReach(ownerId = -groupId, postIds = postIds)
                } else {
                    emptyList()
                }
                if (stats.isEmpty()) {
                    error = "Статистика пуста: нет данных или нет прав на это сообщество"
                } else {
                    points = stats
                    reach = postReach
                }
            } catch (e: Exception) {
                AppLog.e("AdminStats", "load failed", e)
                error = e.message ?: "Ошибка загрузки статистики"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
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

            else -> {
                // Агрегаты за период (считаем клиентски — API отдаёт по дням).
                val totalViews = points.sumOf { it.views }
                val totalVisitors = points.sumOf { it.visitors }
                val totalLikes = points.sumOf { it.likes }
                val totalReplies = points.sumOf { it.replies }
                val totalSubs = points.sumOf { it.subscribes }
                val totalUnsubs = points.sumOf { it.unsubscribes }
                val avgReach = if (points.isNotEmpty()) points.sumOf { it.reach } / points.size else 0

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "За 30 дней",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatSummaryCard("Просмотры", totalViews)
                    StatSummaryCard("Посетители", totalVisitors)
                    StatSummaryCard("Средний охват в день", avgReach)
                    StatSummaryCard("Реакции (лайки)", totalLikes)
                    StatSummaryCard("Комментарии", totalReplies)
                    StatSummaryCard("Подписки / отписки", totalSubs, totalUnsubs)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Охват последних записей",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (reach.isEmpty()) {
                        Text(
                            text = "Нет данных охвата по последним записям",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        reach.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Запись ${r.postId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "охват ${r.reach} · подписчики ${r.reachSubscribers} · лайки ${r.likes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Источник: stats.get / stats.getPostReach (доки VK API; wire-эталона нет — сверка §2 P1.6)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** W35-b: сводная карточка статистики. */
@Composable
private fun StatSummaryCard(label: String, value: Int, secondary: Int? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (secondary != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "/ $secondary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Экран «Люди» — Руководители / Заявки / Чёрный список (B2/B3).
// ═══════════════════════════════════════════════════════════════════════════

private const val TAB_MANAGERS = 0
private const val TAB_REQUESTS = 1
private const val TAB_BANNED = 2

/**
 * W35-b: управление людьми сообщества — 3 вкладки:
 *  - Руководители: groups.getMembers(filter=managers), смена роли/снятие — groups.editManager;
 *  - Заявки: groups.getRequests + groups.approveRequest (отклонение API-методом недоступно);
 *  - Чёрный список: groups.getBanned + groups.unbanUser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPeopleScreen(
    groupId: Long,
    initialTab: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedTab = remember {
        mutableIntStateOf(
            when (initialTab) {
                "requests" -> TAB_REQUESTS
                "banned" -> TAB_BANNED
                else -> TAB_MANAGERS
            }
        )
    }

    // ── Состояния вкладок (разные модели → отдельные стейты) ──
    var managers by remember { mutableStateOf<List<VKApiClient.GroupManager>>(emptyList()) }
    var managersLoading by remember { mutableStateOf(false) }
    var managersError by remember { mutableStateOf<String?>(null) }

    var requests by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var requestsLoading by remember { mutableStateOf(false) }
    var requestsError by remember { mutableStateOf<String?>(null) }

    var banned by remember { mutableStateOf<List<VKApiClient.BannedUser>>(emptyList()) }
    var bannedLoading by remember { mutableStateOf(false) }
    var bannedError by remember { mutableStateOf<String?>(null) }

    var busyUserId by remember { mutableStateOf<Long?>(null) }
    // Диалог смены роли: userId или null.
    var roleDialogFor by remember { mutableStateOf<VKApiClient.GroupManager?>(null) }

    fun loadManagers() {
        scope.launch {
            managersLoading = true
            managersError = null
            try {
                val list = app.apiClient.groupsGetManagers(groupId)
                if (list.isEmpty()) {
                    managersError = app.apiClient.lastApiError?.takeIf { it.isNotBlank() }
                        ?.ifBlank { null } ?: "Список руководителей пуст (нет данных или нет прав)"
                } else {
                    managers = list
                }
            } catch (e: Exception) {
                AppLog.e("AdminPeople", "loadManagers failed", e)
                managersError = e.message ?: "Ошибка загрузки"
            } finally {
                managersLoading = false
            }
        }
    }

    fun loadRequests() {
        scope.launch {
            requestsLoading = true
            requestsError = null
            try {
                val list = app.apiClient.groupsGetRequests(groupId)
                requestsError = null
                requests = list
            } catch (e: Exception) {
                AppLog.e("AdminPeople", "loadRequests failed", e)
                requestsError = e.message ?: "Ошибка загрузки"
            } finally {
                requestsLoading = false
            }
        }
    }

    fun loadBanned() {
        scope.launch {
            bannedLoading = true
            bannedError = null
            try {
                val list = app.apiClient.groupsGetBanned(groupId)
                banned = list
            } catch (e: Exception) {
                AppLog.e("AdminPeople", "loadBanned failed", e)
                bannedError = e.message ?: "Ошибка загрузки"
            } finally {
                bannedLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab.intValue) {
        when (selectedTab.intValue) {
            TAB_MANAGERS -> if (managers.isEmpty() && managersError == null) loadManagers()
            TAB_REQUESTS -> if (requests.isEmpty() && requestsError == null) loadRequests()
            TAB_BANNED -> if (banned.isEmpty() && bannedError == null) loadBanned()
        }
    }

    /** Смена роли / снятие полномочий. */
    fun applyRole(target: VKApiClient.GroupManager, role: String?) {
        scope.launch {
            busyUserId = target.userId
            try {
                val ok = app.apiClient.groupsEditManager(groupId, target.userId, role)
                if (ok) {
                    Toast.makeText(
                        context,
                        if (role == null) "Полномочия сняты" else "Роль обновлена",
                        Toast.LENGTH_SHORT,
                    ).show()
                    managers = emptyList()
                    managersError = null
                    loadManagers()
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось изменить роль" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminPeople", "editManager failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busyUserId = null
                roleDialogFor = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Люди") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = selectedTab.intValue) {
                Tab(
                    selected = selectedTab.intValue == TAB_MANAGERS,
                    onClick = { selectedTab.intValue = TAB_MANAGERS },
                    text = { Text("Руководители") },
                )
                Tab(
                    selected = selectedTab.intValue == TAB_REQUESTS,
                    onClick = { selectedTab.intValue = TAB_REQUESTS },
                    text = { Text("Заявки") },
                )
                Tab(
                    selected = selectedTab.intValue == TAB_BANNED,
                    onClick = { selectedTab.intValue = TAB_BANNED },
                    text = { Text("Чёрный список") },
                )
            }

            when (selectedTab.intValue) {
                TAB_MANAGERS -> {
                    when {
                        managersLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        managersError != null -> ErrorView(
                            message = managersError,
                            onRetry = { loadManagers() },
                        )

                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(managers, key = { "mgr_${it.userId}" }) { mgr ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUserClick(mgr.userId) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = mgr.photo100,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mgr.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = adminRoleLabel(mgr.role, mgr.isOwner),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (!mgr.isOwner) {
                                        TextButton(onClick = { roleDialogFor = mgr }) {
                                            Text("Роль")
                                        }
                                    }
                                    if (busyUserId == mgr.userId) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                TAB_REQUESTS -> {
                    when {
                        requestsLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        requestsError != null -> ErrorView(
                            message = requestsError,
                            onRetry = { loadRequests() },
                        )

                        requests.isEmpty() -> ErrorView(
                            message = "Новых заявок нет",
                            onRetry = { loadRequests() },
                        )

                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(requests, key = { "req_${it.id}" }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUserClick(user.id) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = user.photo100,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.fullName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        user.status?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                busyUserId = user.id
                                                try {
                                                    val ok = app.apiClient.groupsApproveRequest(groupId, user.id)
                                                    if (ok) {
                                                        Toast.makeText(context, "Заявка одобрена", Toast.LENGTH_SHORT).show()
                                                        requests = requests.filterNot { it.id == user.id }
                                                    } else {
                                                        val err = app.apiClient.lastApiError
                                                        Toast.makeText(
                                                            context,
                                                            if (err.isNullOrBlank()) "Не удалось одобрить" else "Ошибка: $err",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("AdminPeople", "approveRequest failed", e)
                                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    busyUserId = null
                                                }
                                            }
                                        },
                                        enabled = busyUserId != user.id,
                                    ) {
                                        Text("Одобрить")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                TAB_BANNED -> {
                    when {
                        bannedLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        bannedError != null -> ErrorView(
                            message = bannedError,
                            onRetry = { loadBanned() },
                        )

                        banned.isEmpty() -> ErrorView(
                            message = "Чёрный список пуст",
                            onRetry = { loadBanned() },
                        )

                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(banned, key = { "ban_${it.userId}" }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUserClick(entry.userId) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = entry.photo100,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (entry.endDate > 0L) {
                                                val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                                "до ${fmt.format(Date(entry.endDate * 1000L))}"
                                            } else {
                                                "навсегда"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                busyUserId = entry.userId
                                                try {
                                                    val ok = app.apiClient.groupsUnbanUser(groupId, entry.userId)
                                                    if (ok) {
                                                        Toast.makeText(context, "Разбанен", Toast.LENGTH_SHORT).show()
                                                        banned = banned.filterNot { it.userId == entry.userId }
                                                    } else {
                                                        val err = app.apiClient.lastApiError
                                                        Toast.makeText(
                                                            context,
                                                            if (err.isNullOrBlank()) "Не удалось разбанить" else "Ошибка: $err",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("AdminPeople", "unbanUser failed", e)
                                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    busyUserId = null
                                                }
                                            }
                                        },
                                        enabled = busyUserId != entry.userId,
                                    ) {
                                        Text("Разбанить")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог смены роли руководителя (groups.editManager).
    val dialogTarget = roleDialogFor
    if (dialogTarget != null) {
        AlertDialog(
            onDismissRequest = { roleDialogFor = null },
            title = {
                Text("Роль: ${dialogTarget.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column {
                    Text(
                        text = "Текущая роль: ${adminRoleLabel(dialogTarget.role, dialogTarget.isOwner)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { applyRole(dialogTarget, "moderator") },
                        enabled = dialogTarget.role != "moderator",
                    ) { Text("Модератор") }
                    TextButton(
                        onClick = { applyRole(dialogTarget, "editor") },
                        enabled = dialogTarget.role != "editor",
                    ) { Text("Редактор") }
                    TextButton(
                        onClick = { applyRole(dialogTarget, "administrator") },
                        enabled = dialogTarget.role != "administrator",
                    ) { Text("Администратор") }
                    HorizontalDivider()
                    TextButton(
                        onClick = { applyRole(dialogTarget, null) },
                    ) { Text("Снять полномочия") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { roleDialogFor = null }) { Text("Отмена") }
            },
        )
    }
}
