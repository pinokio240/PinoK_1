// File: ui/screens/community/CommunityAdminScreens.kt
package re.pinok.ui.screens.community

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Friend
import re.pinok.data.model.Post
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
    onLinksClick: (Long) -> Unit,
    onInvitesClick: (Long) -> Unit,
    onQueueClick: (Long) -> Unit,
    onAddressesClick: (Long) -> Unit,
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

            AdminBlockRow(
                icon = Icons.Filled.Settings,
                title = "Ссылки сообщества",
                subtitle = "Добавить, изменить, удалить",
                onClick = { onLinksClick(gi.id) },
            )

            // ── W38 (C2/C5/C4) ──
            AdminBlockRow(
                icon = Icons.Filled.GroupAdd,
                title = "Приглашения",
                subtitle = "Пригласить друзей, отозвать",
                onClick = { onInvitesClick(gi.id) },
            )
            AdminBlockRow(
                icon = Icons.Filled.Schedule,
                title = "Отложенные и предложения",
                subtitle = "Записи, ожидающие публикации",
                onClick = { onQueueClick(gi.id) },
            )
            AdminBlockRow(
                icon = Icons.Filled.Place,
                title = "Адреса",
                subtitle = "Точки сообщества на карте",
                onClick = { onAddressesClick(gi.id) },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // ── Честные disabled-пункты (план §4 C-серия: C3 кнопка действия,
            //    C6 чаты сообщества — НЕ реализованы, без выдуманных маршрутов) ──
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Сообщения", subtitle = "Пока не реализовано", onClick = {}, enabled = false)
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Разделы", subtitle = "Пока не реализовано", onClick = {}, enabled = false)
            AdminBlockRow(icon = Icons.Filled.Settings, title = "Бизнес-инструменты", subtitle = "Пока не реализовано", onClick = {}, enabled = false)
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
    // W38 (C7): обложка сообщества — поток как EditProfileScreen.changeCover
    // (v2-форма: upload_v2 → multipart "file" → saveOwnerCoverPhoto response_json),
    // но с group_id. Удаление — photos.removeOwnerCoverPhoto(group_id).
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var coverBusy by remember { mutableStateOf(false) }

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
                    coverUrl = g.coverUrl
                }
            } catch (e: Exception) {
                AppLog.e("AdminSettings", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    /** W38 (C7): загрузка обложки из галереи (v2-форма веба, group_id). */
    fun changeCover(uri: Uri) {
        if (coverBusy) return
        coverBusy = true
        scope.launch {
            try {
                val uploadUrl = app.apiClient.photosGetOwnerCoverPhotoUploadServer(
                    groupId = groupId,
                    uploadV2 = true,
                )
                if (uploadUrl.isNullOrBlank()) {
                    Toast.makeText(context, "Не удалось получить адрес загрузки обложки", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val raw = uploadGroupCoverMultipart(context, uploadUrl, uri)
                if (raw == null) {
                    Toast.makeText(context, "Не удалось загрузить обложку", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val ok = app.apiClient.photosSaveOwnerCoverPhoto(
                    responseJson = raw,
                    groupId = groupId,
                    uploadV2 = true,
                )
                if (ok) {
                    Toast.makeText(context, "Обложка обновлена", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось сохранить обложку",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminSettings", "changeCover failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                coverBusy = false
            }
        }
    }

    /** W38 (C7): удалить обложку (photos.removeOwnerCoverPhoto, group_id). */
    fun removeGroupCover() {
        if (coverBusy) return
        coverBusy = true
        scope.launch {
            try {
                val ok = app.apiClient.photosRemoveOwnerCoverPhoto(groupId = groupId)
                if (ok) {
                    Toast.makeText(context, "Обложка удалена", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось удалить обложку",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminSettings", "removeGroupCover failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                coverBusy = false
            }
        }
    }

    // W38 (C7): пикер галереи (GetContent("image/*") — паттерн EditProfileScreen П-3).
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) changeCover(uri)
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

                        // W38 (C7): обложка сообщества.
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Обложка",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val currentCover = coverUrl
                        if (currentCover != null) {
                            AsyncImage(
                                model = currentCover,
                                contentDescription = "Обложка сообщества",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Text(
                                text = "Обложка не установлена",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row {
                            Button(
                                onClick = { coverPicker.launch("image/*") },
                                enabled = !coverBusy,
                            ) {
                                if (coverBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Загрузить из галереи")
                                }
                            }
                            if (currentCover != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { removeGroupCover() }, enabled = !coverBusy) {
                                    Text("Удалить")
                                }
                            }
                        }

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

// W37 (C1): экран ссылок сообщества.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLinksScreen(groupId: Long, onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var links by remember { mutableStateOf<List<VKApiClient.GroupLink>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var addUrl by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<VKApiClient.GroupLink?>(null) }
    var editName by remember { mutableStateOf("") }
    fun load() {
        scope.launch {
            loading = true; error = null
            try { links = app.apiClient.groupsGetLinks(groupId) }
            catch (e: Exception) { AppLog.e("AdminLinks", "load", e); error = e.message ?: "Ошибка" }
            finally { loading = false }
        }
    }
    LaunchedEffect(groupId) { load() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ссылки сообщества") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = { TextButton(onClick = { addUrl = ""; showAdd = true }, enabled = !busy) { Text("Добавить") } },
        )
    }) { pad ->
        when {
            loading -> Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> ErrorView(message = error, onRetry = { load() }, modifier = Modifier.padding(pad))
            links.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Ссылок пока нет") }
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(links) { link ->
                    Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp), RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (link.name.isNotBlank()) { Text(link.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                                Text(link.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { editTarget = link; editName = link.name }, enabled = !busy) { Text("Изменить") }
                            TextButton(onClick = {
                                scope.launch {
                                    busy = true
                                    val ok = app.apiClient.groupsDeleteLink(groupId, link.id)
                                    busy = false
                                    if (ok) { Toast.makeText(context, "Ссылка удалена", Toast.LENGTH_SHORT).show(); load() }
                                    else { Toast.makeText(context, app.apiClient.lastApiError ?: "Не удалось удалить", Toast.LENGTH_LONG).show() }
                                }
                            }, enabled = !busy) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAdd = false },
            title = { Text("Новая ссылка") },
            text = { OutlinedTextField(addUrl, { addUrl = it }, label = { Text("URL (https://...)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val url = addUrl.trim()
                    if (url.isBlank()) return@TextButton
                    scope.launch {
                        busy = true
                        val ok = app.apiClient.groupsAddLink(groupId, url)
                        busy = false
                        if (ok) { showAdd = false; Toast.makeText(context, "Ссылка добавлена", Toast.LENGTH_SHORT).show(); load() }
                        else { Toast.makeText(context, app.apiClient.lastApiError ?: "Не удалось добавить", Toast.LENGTH_LONG).show() }
                    }
                }, enabled = !busy && addUrl.isNotBlank()) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }, enabled = !busy) { Text("Отмена") } },
        )
    }
    val et = editTarget
    if (et != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) editTarget = null },
            title = { Text("Изменить подпись") },
            text = { OutlinedTextField(editName, { editName = it }, label = { Text("Текст ссылки") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        val ok = app.apiClient.groupsEditLink(groupId, et.id, editName.trim())
                        busy = false
                        if (ok) { editTarget = null; Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show(); load() }
                        else { Toast.makeText(context, app.apiClient.lastApiError ?: "Не удалось сохранить", Toast.LENGTH_LONG).show() }
                    }
                }, enabled = !busy) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }, enabled = !busy) { Text("Отмена") } },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// W38 (C7): multipart-загрузка обложки сообщества.
// ═══════════════════════════════════════════════════════════════════════════

/**
 * W38 (C7): multipart-загрузка файла обложки на upload_url. Cover-сервер
 * принимает файл в поле "file", транспорт отдельный от photosUploadWallPhoto
 * (поле "photo"). Копия приватного uploadCoverMultipart из EditProfileScreen
 * (там не экспортируется) — VKA не трогаем. Возвращает СЫРОЙ JSON-ответ
 * сервера (идёт в response_json v2-формы photos.saveOwnerCoverPhoto) или null.
 */
private suspend fun uploadGroupCoverMultipart(
    context: Context,
    uploadUrl: String,
    uri: Uri,
): String? {
    return withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val requestBody = bytes.toRequestBody(mime.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "cover.jpg", requestBody)
                .build()
            val request = Request.Builder().url(uploadUrl).post(multipart).build()
            // Свежий OkHttpClient: upload-серверу (pu.vk.com) авторизация не нужна.
            OkHttpClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.e("AdminSettings", "cover upload HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    AppLog.e("AdminSettings", "cover upload: empty body")
                    return@use null
                }
                body
            }
        } catch (e: Exception) {
            AppLog.e("AdminSettings", "cover upload failed", e)
            null
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// W38 (C2): экран «Приглашения» — пригласить друзей / приглашённые.
// ═══════════════════════════════════════════════════════════════════════════

private const val INVITES_TAB_FRIENDS = 0
private const val INVITES_TAB_INVITED = 1

/**
 * W38 (C2, план §4): приглашения в сообщество.
 *  - Вкладка «Пригласить»: friends.get (order=hints, 200) + groups.invite;
 *  - Вкладка «Приглашённые»: groups.getInvitedUsers + groups.recallInvitation.
 * ЧЕСТНО: пригласить можно только друга (офиц. API); чужим — API-ошибка
 * честным тостом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInvitesScreen(
    groupId: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedTab = remember { mutableIntStateOf(INVITES_TAB_FRIENDS) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var friendsLoading by remember { mutableStateOf(false) }
    var friendsError by remember { mutableStateOf<String?>(null) }
    var invited by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var invitedLoading by remember { mutableStateOf(false) }
    var invitedError by remember { mutableStateOf<String?>(null) }
    var busyUserId by remember { mutableStateOf<Long?>(null) }
    // Локальный стейт «уже приглашено» (после успешного groups.invite).
    var invitedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var filter by remember { mutableStateOf("") }

    fun loadFriends() {
        scope.launch {
            friendsLoading = true
            friendsError = null
            try {
                friends = app.apiClient.friendsGet(count = 200)
            } catch (e: Exception) {
                AppLog.e("AdminInvites", "loadFriends failed", e)
                friendsError = e.message ?: "Ошибка загрузки"
            } finally {
                friendsLoading = false
            }
        }
    }

    fun loadInvited() {
        scope.launch {
            invitedLoading = true
            invitedError = null
            try {
                invited = app.apiClient.groupsGetInvitedUsers(groupId)
            } catch (e: Exception) {
                AppLog.e("AdminInvites", "loadInvited failed", e)
                invitedError = e.message ?: "Ошибка загрузки"
            } finally {
                invitedLoading = false
            }
        }
    }

    /** W38 (C2): groups.invite — пригласить друга. */
    fun invite(user: Friend) {
        scope.launch {
            busyUserId = user.id
            try {
                val ok = app.apiClient.groupsInvite(groupId, user.id)
                if (ok) {
                    Toast.makeText(context, "Приглашение отправлено", Toast.LENGTH_SHORT).show()
                    invitedIds = invitedIds + user.id
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось пригласить" else "Ошибка: $err",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminInvites", "invite failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busyUserId = null
            }
        }
    }

    /** W38 (C2): groups.recallInvitation — отозвать приглашение. */
    fun recall(user: UserProfile) {
        scope.launch {
            busyUserId = user.id
            try {
                val ok = app.apiClient.groupsRecallInvitation(groupId, user.id)
                if (ok) {
                    Toast.makeText(context, "Приглашение отозвано", Toast.LENGTH_SHORT).show()
                    invited = invited.filterNot { it.id == user.id }
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось отозвать" else "Ошибка: $err",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminInvites", "recall failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busyUserId = null
            }
        }
    }

    LaunchedEffect(selectedTab.intValue) {
        when (selectedTab.intValue) {
            INVITES_TAB_FRIENDS -> if (friends.isEmpty() && friendsError == null) loadFriends()
            INVITES_TAB_INVITED -> if (invited.isEmpty() && invitedError == null) loadInvited()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приглашения") },
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
                    selected = selectedTab.intValue == INVITES_TAB_FRIENDS,
                    onClick = { selectedTab.intValue = INVITES_TAB_FRIENDS },
                    text = { Text("Пригласить") },
                )
                Tab(
                    selected = selectedTab.intValue == INVITES_TAB_INVITED,
                    onClick = { selectedTab.intValue = INVITES_TAB_INVITED },
                    text = { Text("Приглашённые") },
                )
            }

            when (selectedTab.intValue) {
                INVITES_TAB_FRIENDS -> {
                    when {
                        friendsLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        friendsError != null -> ErrorView(
                            message = friendsError,
                            onRetry = { loadFriends() },
                        )

                        else -> {
                            val filteredFriends = friends.filter { friend ->
                                filter.isBlank() ||
                                    friend.fullName.lowercase().contains(filter.trim().lowercase())
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "filter") {
                                    OutlinedTextField(
                                        value = filter,
                                        onValueChange = { filter = it },
                                        singleLine = true,
                                        placeholder = { Text("Фильтр по имени") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                                if (filteredFriends.isEmpty()) {
                                    item(key = "empty_friends") {
                                        Text(
                                            text = "Подходящих друзей нет",
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    items(filteredFriends, key = { it.id }) { friend ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onUserClick(friend.id) }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AsyncImage(
                                                model = friend.photo100,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape),
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = friend.fullName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (invitedIds.contains(friend.id)) {
                                                Text(
                                                    text = "Приглашено",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                TextButton(
                                                    onClick = { invite(friend) },
                                                    enabled = busyUserId != friend.id,
                                                ) {
                                                    Text("Пригласить")
                                                }
                                            }
                                            if (busyUserId == friend.id) {
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
                    }
                }

                INVITES_TAB_INVITED -> {
                    when {
                        invitedLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        invitedError != null -> ErrorView(
                            message = invitedError,
                            onRetry = { loadInvited() },
                        )

                        invited.isEmpty() -> ErrorView(
                            message = "Приглашений нет",
                            onRetry = { loadInvited() },
                        )

                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(invited, key = { it.id }) { user ->
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
                                    }
                                    TextButton(
                                        onClick = { recall(user) },
                                        enabled = busyUserId != user.id,
                                    ) {
                                        Text("Отозвать")
                                    }
                                    if (busyUserId == user.id) {
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
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// W38 (C5): экран «Отложенные и предложения» — wall.get filter=postponed/suggests.
// ═══════════════════════════════════════════════════════════════════════════

private const val QUEUE_TAB_POSTPONED = 0
private const val QUEUE_TAB_SUGGESTS = 1

/**
 * W38 (C5, план §4): записи, ожидающие публикации.
 *  - «Отложенные»: wall.get filter=postponed (дата = дата будущей публикации);
 *  - «Предложения»: wall.get filter=suggests.
 * Действие: удаление (wall.delete — офиц. API). ЧЕСТНО: опубликовать
 * предложение / изменить дату отложенной официальным API НЕЛЬЗЯ — не делаем.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWallQueueScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedTab = remember { mutableIntStateOf(QUEUE_TAB_POSTPONED) }
    var postponed by remember { mutableStateOf<List<Post>>(emptyList()) }
    var suggests by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyPostId by remember { mutableStateOf<Long?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                postponed = app.apiClient.wallGetWithFilter(
                    ownerId = -groupId, filter = "postponed", count = 50,
                )
                suggests = app.apiClient.wallGetWithFilter(
                    ownerId = -groupId, filter = "suggests", count = 50,
                )
            } catch (e: Exception) {
                AppLog.e("AdminWallQueue", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    /** W38 (C5): удалить запись (wall.delete — офиц. API). */
    fun removePost(post: Post, tab: Int) {
        scope.launch {
            busyPostId = post.id
            try {
                val ok = app.apiClient.wallDelete(ownerId = -groupId, postId = post.id)
                if (ok) {
                    Toast.makeText(context, "Запись удалена", Toast.LENGTH_SHORT).show()
                    if (tab == QUEUE_TAB_POSTPONED) {
                        postponed = postponed.filterNot { it.id == post.id }
                    } else {
                        suggests = suggests.filterNot { it.id == post.id }
                    }
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось удалить" else "Ошибка: $err",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminWallQueue", "removePost failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busyPostId = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отложенные и предложения") },
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
                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                    TabRow(selectedTabIndex = selectedTab.intValue) {
                        Tab(
                            selected = selectedTab.intValue == QUEUE_TAB_POSTPONED,
                            onClick = { selectedTab.intValue = QUEUE_TAB_POSTPONED },
                            text = { Text("Отложенные (${postponed.size})") },
                        )
                        Tab(
                            selected = selectedTab.intValue == QUEUE_TAB_SUGGESTS,
                            onClick = { selectedTab.intValue = QUEUE_TAB_SUGGESTS },
                            text = { Text("Предложения (${suggests.size})") },
                        )
                    }

                    val current = if (selectedTab.intValue == QUEUE_TAB_POSTPONED) postponed else suggests
                    if (current.isEmpty()) {
                        ErrorView(
                            message = if (selectedTab.intValue == QUEUE_TAB_POSTPONED) {
                                "Отложенных записей нет"
                            } else {
                                "Предложенных записей нет"
                            },
                            onRetry = { load() },
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(current, key = { it.id }) { post ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fmt.format(Date(post.date * 1000L)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = post.text.ifBlank { "Без текста" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(
                                        onClick = { removePost(post, selectedTab.intValue) },
                                        enabled = busyPostId != post.id,
                                    ) {
                                        Text("Удалить")
                                    }
                                    if (busyPostId == post.id) {
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
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// W38 (C4): экран «Адреса» — groups.getAddresses/addAddress/editAddress/deleteAddress.
// ═══════════════════════════════════════════════════════════════════════════

/** W38 (C4): человекочитаемая метка work_info_status (доки VK API). */
private fun addressStatusLabel(status: String?): String = when (status) {
    "always_open" -> "Открыто всегда"
    "temporarily_closed" -> "Временно закрыто"
    "timetable" -> "По расписанию"
    else -> "Нет информации"
}

/**
 * W38 (C4, план §4): адреса сообщества — список/добавление/правка/удаление.
 * Офиц. API (5.85+). Расписание (timetable JSON) сознательно не редактируется
 * — честная строка статуса; полный редактор расписания — следующая волна.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddressesScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addresses by remember { mutableStateOf<List<VKApiClient.GroupAddress>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    // Диалог добавления.
    var showAdd by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    var addAddress by remember { mutableStateOf("") }
    var addPhone by remember { mutableStateOf("") }
    // Диалог правки.
    var editTarget by remember { mutableStateOf<VKApiClient.GroupAddress?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editAddress by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                addresses = app.apiClient.groupsGetAddresses(groupId)
            } catch (e: Exception) {
                AppLog.e("AdminAddresses", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    fun submitAdd() {
        if (addTitle.isBlank() || addAddress.isBlank()) return
        scope.launch {
            busy = true
            try {
                val ok = app.apiClient.groupsAddAddress(
                    groupId = groupId,
                    title = addTitle.trim(),
                    address = addAddress.trim(),
                    phone = addPhone.trim().takeIf { it.isNotBlank() },
                )
                if (ok) {
                    showAdd = false
                    Toast.makeText(context, "Адрес добавлен", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось добавить адрес",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminAddresses", "submitAdd failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun submitEdit() {
        val target = editTarget ?: return
        if (editTitle.isBlank() || editAddress.isBlank()) return
        scope.launch {
            busy = true
            try {
                val ok = app.apiClient.groupsEditAddress(
                    groupId = groupId,
                    addressId = target.id,
                    title = editTitle.trim(),
                    address = editAddress.trim(),
                    phone = editPhone.trim().takeIf { it.isNotBlank() },
                )
                if (ok) {
                    editTarget = null
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось сохранить",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminAddresses", "submitEdit failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun remove(target: VKApiClient.GroupAddress) {
        scope.launch {
            busy = true
            try {
                val ok = app.apiClient.groupsDeleteAddress(groupId, target.id)
                if (ok) {
                    Toast.makeText(context, "Адрес удалён", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось удалить адрес",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminAddresses", "remove failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Адреса") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { addTitle = ""; addAddress = ""; addPhone = ""; showAdd = true }, enabled = !busy) {
                        Text("Добавить")
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

            addresses.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { Text("Адресов пока нет") }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
                items(addresses, key = { it.id }) { addr ->
                    Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp), RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (addr.title.isNotBlank()) {
                                    Text(addr.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                                if (addr.address.isNotBlank()) {
                                    Text(addr.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = addressStatusLabel(addr.workInfoStatus) +
                                        (addr.phone?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    editTarget = addr
                                    editTitle = addr.title
                                    editAddress = addr.address
                                    editPhone = addr.phone ?: ""
                                },
                                enabled = !busy,
                            ) { Text("Изменить") }
                            TextButton(onClick = { remove(addr) }, enabled = !busy) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAdd = false },
            title = { Text("Новый адрес") },
            text = {
                Column {
                    OutlinedTextField(addTitle, { addTitle = it }, label = { Text("Название (например, «Главный офис»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(addAddress, { addAddress = it }, label = { Text("Адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(addPhone, { addPhone = it }, label = { Text("Телефон (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitAdd() },
                    enabled = !busy && addTitle.isNotBlank() && addAddress.isNotBlank(),
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }, enabled = !busy) { Text("Отмена") } },
        )
    }

    val editing = editTarget
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) editTarget = null },
            title = { Text("Изменить адрес") },
            text = {
                Column {
                    OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(editAddress, { editAddress = it }, label = { Text("Адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(editPhone, { editPhone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitEdit() },
                    enabled = !busy && editTitle.isNotBlank() && editAddress.isNotBlank(),
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }, enabled = !busy) { Text("Отмена") } },
        )
    }
}
