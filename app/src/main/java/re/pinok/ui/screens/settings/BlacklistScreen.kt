package re.pinok.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════
// §PROFILE-P4: Экран «Чёрный список» (этап П-4, инвентарь §1.6 / §5 п.5)
//   Перенесено из NotificationSettingsScreen (секция «Заблокированные») и
//   расширено до DOM-матрицы act=blacklist:
//     - список:          account.getBanned (offset/count, fields photo_100…)
//     - фильтр:          bl-filter (локальная фильтрация по имени)
//     - «Удалить из списка»: bl-remove-btn → account.unban + подтверждение
//     - «Добавить в чёрный список»: bl-add-btn → модалка «Ссылка на страницу
//       или сообщество» (числовой id / id123 / club123 / vk.com/name / короткое
//       имя → utils.resolveScreenName) + подсказка-превью (users.get /
//       groups.getById) → account.ban{owner_id}.
//   Тост «X был удалён из чёрного списка» — по §1.6.
// ═══════════════════════════════════════════════════════════

/** Размер страницы account.getBanned. */
private const val BLACKLIST_PAGE_SIZE = 50

/** Цель добавления в чёрный список после разбора строки ввода. */
private sealed class BlacklistTarget {
    /** Готовый owner_id (VK-семантика: >0 — пользователь, <0 — сообщество). */
    data class Direct(val ownerId: Long) : BlacklistTarget()

    /** Короткое имя — требует utils.resolveScreenName. */
    data class ScreenName(val name: String) : BlacklistTarget()
}

/** Найденная (для превью) страница перед баном. */
private data class BlacklistPreview(
    val ownerId: Long,
    val name: String,
    val photo: String?,
    val isGroup: Boolean,
)

/**
 * Разбор строки «ссылка или короткое имя» (bl-add-search §1.6):
 *  - "12345" / "-12345"  → Direct(owner_id) как есть;
 *  - "id123"             → Direct(123) (пользователь);
 *  - "club123"/"public123" → Direct(-123) (сообщество);
 *  - "vk.com/name", "m.vk.com/name", "vk.ru/name", "/name", "name"
 *    → ScreenName("name") (id123/club123 в ссылке тоже распознаются).
 */
private fun parseBlacklistInput(raw: String): BlacklistTarget? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    val lowered = text.lowercase()
    if (lowered.startsWith("http://") || lowered.startsWith("https://")) {
        text = text.substringAfter("://")
    }
    // Срезаем мобильный поддомен и хост поэтапно, пересчитывая lowercase
    // после каждого шага (иначе "m.vk.com/name" после снятия "m." не матчится
    // со старым lowercase и хост остаётся в тексте).
    var t = text.lowercase()
    if (t.startsWith("m.")) {
        text = text.substring(2)
        t = text.lowercase()
    }
    when {
        t.startsWith("vk.com/") -> text = text.substring(7)
        t.startsWith("vk.ru/") -> text = text.substring(6)
    }
    text = text
        .removePrefix("/")
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore('/')
        .trim()
    if (text.isEmpty()) return null
    // Числовой id (возможно отрицательный) — owner_id как есть.
    val asLong = text.toLongOrNull()
    if (asLong != null && asLong != 0L) return BlacklistTarget.Direct(asLong)
    // id123 → пользователь.
    val idMatch = Regex("^id(\\d+)$", RegexOption.IGNORE_CASE).find(text)
    if (idMatch != null) {
        val id = idMatch.groupValues[1].toLongOrNull()
        if (id != null && id != 0L) return BlacklistTarget.Direct(id)
    }
    // club123 / public123 → сообщество.
    val clubMatch = Regex("^(?:club|public)(\\d+)$", RegexOption.IGNORE_CASE).find(text)
    if (clubMatch != null) {
        val id = clubMatch.groupValues[1].toLongOrNull()
        if (id != null && id != 0L) return BlacklistTarget.Direct(-id)
    }
    // Короткое имя (screen_name VK: латиница/цифры/_/.).
    if (Regex("^[A-Za-z0-9_.]+$").matches(text)) {
        return BlacklistTarget.ScreenName(text.lowercase())
    }
    return null
}

/**
 * Экран «Чёрный список»: список заблокированных + фильтр + добавление по
 * ссылке/короткому имени (account.ban) + удаление (account.unban).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<BannedUser>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var pendingUnban by remember { mutableStateOf<BannedUser?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    /** Страница account.getBanned с offset. */
    suspend fun fetchPage(offset: Int): BannedUsersList? =
        app.apiClient.accountGetBanned(offset = offset, count = BLACKLIST_PAGE_SIZE)

    fun applyPage(reset: Boolean, page: BannedUsersList) {
        total = page.count
        items = if (reset) {
            page.items
        } else {
            // Дедуп по id — защита от дублей при повторных ответах.
            (items + page.items).distinctBy { it.id }
        }
    }

    fun loadFirst() {
        scope.launch {
            loading = true
            error = null
            try {
                val page = fetchPage(0)
                if (page == null) {
                    items = emptyList()
                    total = 0
                    error = "Не удалось загрузить чёрный список. " +
                        "Проверьте подключение к сети."
                } else {
                    applyPage(reset = true, page = page)
                }
            } catch (e: Exception) {
                AppLog.e("BlacklistScreen", "loadFirst error", e)
                error = "Ошибка загрузки: ${e.message ?: "неизвестная"}"
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (loadingMore || items.size >= total) return
        scope.launch {
            loadingMore = true
            try {
                val page = fetchPage(items.size)
                if (page == null) {
                    Toast.makeText(
                        context,
                        "Не удалось загрузить следующую страницу: " +
                            (app.apiClient.lastApiError ?: "нет ответа сервера"),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    applyPage(reset = false, page = page)
                }
            } catch (e: Exception) {
                AppLog.e("BlacklistScreen", "loadMore error", e)
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFirst() }

    val hasMore = items.size < total
    val filtered = items.filter { user ->
        filter.isBlank() || user.fullName.lowercase().contains(filter.trim().lowercase())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чёрный список") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            // bl-add-btn (§1.6) — «Добавить в чёрный список».
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Icon(Icons.Default.Block, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить")
            }
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
                            onClick = { loadFirst() },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("Повторить")
                        }
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                val page = fetchPage(0)
                                if (page != null) applyPage(reset = true, page = page)
                            } catch (e: Exception) {
                                AppLog.e("BlacklistScreen", "refresh error", e)
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
                        // bl-filter (§1.6) — локальная фильтрация по имени.
                        item(key = "filter") {
                            OutlinedTextField(
                                value = filter,
                                onValueChange = { filter = it },
                                singleLine = true,
                                placeholder = { Text("Фильтр по имени") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .heightIn(min = 44.dp),
                            )
                        }
                        if (filtered.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (items.isEmpty()) {
                                        "Список заблокированных пуст"
                                    } else {
                                        "По фильтру ничего не найдено"
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            items(filtered, key = { "banned_${it.id}" }) { user ->
                                BannedListRow(
                                    user = user,
                                    onRemove = { pendingUnban = user },
                                )
                            }
                        }
                        if (hasMore) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp,
                                        )
                                    } else {
                                        TextButton(
                                            onClick = { loadMore() },
                                            modifier = Modifier.heightIn(min = 44.dp),
                                        ) {
                                            Text("Загрузить ещё (${total - items.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Подтверждение «Удалить из списка» (bl-remove-btn §1.6).
    pendingUnban?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUnban = null },
            title = { Text("Удалить из чёрного списка?") },
            text = { Text("«${target.fullName}» снова сможет писать вам и видеть ваш профиль.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUnban = null
                        scope.launch {
                            val ok = app.apiClient.accountUnban(target.id)
                            if (ok) {
                                items = items.filter { it.id != target.id }
                                total = (total - 1).coerceAtLeast(0)
                                Toast.makeText(
                                    context,
                                    "«${target.fullName}» удалён из чёрного списка",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Не удалось удалить: " +
                                        (app.apiClient.lastApiError ?: "нет ответа сервера"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingUnban = null },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    // Модалка «Добавление в чёрный список» (bl-add-btn / bl-add-search §1.6).
    if (showAddDialog) {
        AddToBlacklistDialog(
            app = app,
            onDismiss = { showAddDialog = false },
            onBanned = { name ->
                showAddDialog = false
                Toast.makeText(
                    context,
                    "«$name» добавлен в чёрный список",
                    Toast.LENGTH_SHORT,
                ).show()
                loadFirst()
            },
        )
    }
}

/** Строка заблокированного: аватар/имя/дата бана + «Удалить из списка». */
@Composable
private fun BannedListRow(user: BannedUser, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 48.dp),
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
        TextButton(
            onClick = onRemove,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Удалить из списка")
        }
    }
}

/**
 * Модалка добавления в чёрный список: ввод ссылки/короткого имени → разбор →
 * resolveScreenName → превью (users.get / groups.getById) → account.ban.
 */
@Composable
private fun AddToBlacklistDialog(
    app: SovaApp,
    onDismiss: () -> Unit,
    onBanned: (name: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BlacklistPreview?>(null) }
    var banInProgress by remember { mutableStateOf(false) }

    /** Разбор + резолв + превью найденной страницы. */
    fun resolveInput() {
        val target = parseBlacklistInput(input)
        if (target == null) {
            parseError = "Не удалось распознать ссылку или короткое имя"
            preview = null
            return
        }
        parseError = null
        preview = null
        scope.launch {
            resolving = true
            try {
                val ownerId: Long? = when (target) {
                    is BlacklistTarget.Direct -> target.ownerId
                    is BlacklistTarget.ScreenName -> {
                        val resolved = app.apiClient.resolveScreenName(target.name)
                        when {
                            resolved == null -> null
                            resolved.second == "user" -> resolved.first
                            // Сообщества/публичные страницы банятся отрицательным id.
                            resolved.second == "group" || resolved.second == "page" ->
                                -resolved.first
                            // application и прочие типы банить нельзя.
                            else -> null
                        }
                    }
                }
                if (ownerId == null || ownerId == 0L) {
                    parseError = "Страница не найдена. Проверьте ссылку или короткое имя."
                } else {
                    val isGroup = ownerId < 0
                    val name: String
                    val photo: String?
                    if (isGroup) {
                        val group = app.apiClient.groupsGetById(listOf(-ownerId)).firstOrNull()
                        name = group?.name ?: "сообщество -$ownerId"
                        photo = group?.photo100 ?: group?.photo200
                    } else {
                        val user = app.apiClient.usersGet(ownerId)
                        name = user?.fullName ?: "id$ownerId"
                        photo = user?.photo100 ?: user?.photo200
                    }
                    preview = BlacklistPreview(
                        ownerId = ownerId,
                        name = name,
                        photo = photo,
                        isGroup = isGroup,
                    )
                }
            } catch (e: Exception) {
                AppLog.e("BlacklistScreen", "resolveInput error", e)
                parseError = "Ошибка поиска: ${e.message ?: "неизвестная"}"
            } finally {
                resolving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!banInProgress) onDismiss() },
        title = { Text("Добавление в чёрный список") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        // Любая правка ввода сбрасывает найденное превью.
                        preview = null
                        parseError = null
                    },
                    singleLine = true,
                    enabled = !resolving && !banInProgress,
                    placeholder = { Text("Ссылка на страницу или сообщество") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Например: 12345, id123, vk.com/durov или durov",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    resolving -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Поиск страницы…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    parseError != null -> {
                        // #NULL-EXPLICIT: делегированное свойство не smart-cast —
                        // захват в локальный val.
                        val err = parseError
                        if (err != null) {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    preview != null -> {
                        val p = preview
                        if (p != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (p.photo != null) {
                                    AsyncImage(
                                        model = p.photo,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(p.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (p.isGroup) "Сообщество" else "Пользователь",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = preview != null && !banInProgress
            Button(
                onClick = {
                    val p = preview
                    if (p != null) {
                        banInProgress = true
                        scope.launch {
                            val ok = app.apiClient.accountBan(p.ownerId)
                            banInProgress = false
                            if (ok) {
                                onBanned(p.name)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Не удалось заблокировать: " +
                                        (app.apiClient.lastApiError ?: "нет ответа сервера"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
                enabled = ready,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Заблокировать")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !banInProgress,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Отмена")
            }
        },
    )
}
