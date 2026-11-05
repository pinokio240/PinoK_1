package re.pinok.ui.screens.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.realtime.VkUrlDeepLinker
import re.pinok.api.VKApiClient
import re.pinok.realtime.VkNotificationsNotifier
import re.pinok.ui.components.ErrorView
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.ui.theme.UiScale
import re.pinok.util.AppLog
import re.pinok.util.linkifyVkText
import re.pinok.util.openUrlExternal

// P1.6 (DIAG-C2 §5): локальный тип-фильтр уведомлений (NOTIFICATION_FILTERS +
// NotificationFilter data class + NotificationFilterSheet bottom-sheet + activeFilter
// state + filterMatch-блок в filteredNotifications) УДАЛЁН как мёртвый код —
// UI-триггер кнопки-фильтра в TopAppBar был убран #NOTIFSHOW, фильтр всегда "all".
// Серверная категорийная фильтрация (NotificationCategoryBar + NOTIFICATION_CATEGORIES)
// остаётся рабочей — см. ниже #NOTIF-NFDCAT.

// ═══════════════════════════════════════════════════════════
// #NOTIF-NFDCAT (категорийный сайдбар): СЕРВЕРНЫЕ категории
// notifications.getRedesign. ТЗ из снапшотов VK web-уведомлений:
//   all(«Уведомления профиля»), communities(«Сообщества»),
//   feedback(«Обратная связь»), friends(«Друзья»), services(«Сервисы»),
//   communication(«Общение»), account(«Аккаунт»).
// Выбор категории → серверная перезагрузка через
// notificationsGetPage(category=serverKey).
// ═══════════════════════════════════════════════════════════

/** #NOTIF-NFDCAT: одна серверная категория getRedesign. */
private data class NotificationCategory(
    val serverKey: String,   // значение параметра category для метода
    val label: String,       // человекочитаемое имя из сайдбара VK web
    val icon: ImageVector,
)

private val NOTIFICATION_CATEGORIES = listOf(
    NotificationCategory("all",            "Уведомления профиля", Icons.Outlined.Notifications),
    NotificationCategory("communities",    "Сообщества",          Icons.Outlined.Group),
    NotificationCategory("feedback",       "Обратная связь",      Icons.Outlined.AlternateEmail),
    NotificationCategory("friends",        "Друзья",              Icons.Outlined.PersonAdd),
    NotificationCategory("services",       "Сервисы",             Icons.Outlined.Apps),
    NotificationCategory("communication",  "Общение",             Icons.Outlined.ChatBubbleOutline),
    NotificationCategory("account",        "Аккаунт",             Icons.Outlined.Email),
)

/**
 * #NOTIF-FEED-FILTER (19-B.2): стабильная двухклассовая сортировка списка
 * уведомлений — класс A (сообщения/диалоги: message*, mail/group_chats/chat,
 * см. [re.pinok.realtime.VkNotificationsNotifier.isMessageClassType])
 * располагается ВЫШЕ новостных (класс B: лайки/комментарии/посты/...).
 * Внутри классов исходный порядок VK сохраняется (sortedBy — стабильный),
 * поэтому пагинация (Fix #255/#253), поиск и undo-скрытие не затрагиваются:
 * сортировка меняет только ПОРЯДОК, не состав списка.
 *
 * Применяется в filteredNotifications (не при загрузке): единая точка для
 * первой страницы, pull-to-refresh и loadMore — каждая порция автоматически
 * пересортируется, а фильтр/поиск остаются независимыми шагами конвейера.
 */
private fun sortDialogsFirst(
    list: List<VKApiClient.NotificationItem>,
): List<VKApiClient.NotificationItem> {
    return list.sortedBy { item ->
        if (VkNotificationsNotifier.isMessageClassType(item.type)) 0 else 1
    }
}

// ═══════════════════════════════════════════════════════════
// Иконки для каждого типа уведомления (N2: 20+ типов)
// ═══════════════════════════════════════════════════════════

private data class TypeIcon(
    val icon: ImageVector,
    val tint: Color,
    val bgTint: Color,
)

@Composable
private fun getTypeIcon(type: String): TypeIcon {
    val likeColor = Color(0xFFE53935)
    val commentColor = MaterialTheme.colorScheme.primary
    val repostColor = Color(0xFF4CAF50)
    val mentionColor = Color(0xFFFF9800)
    val followColor = Color(0xFF2196F3)
    val friendColor = Color(0xFF9C27B0)
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant

    val pair = when {
        type.startsWith("like_post") || type == "like" ->
            Icons.Outlined.Favorite to likeColor
        type.startsWith("like_comment") ->
            Icons.Outlined.Favorite to Color(0xFFE91E63)
        type.startsWith("like_photo") ->
            Icons.Outlined.Image to likeColor
        type.startsWith("like_video") ->
            Icons.Outlined.VideoCameraBack to likeColor
        type.startsWith("like_topic") ->
            @Suppress("DEPRECATION")
            Icons.AutoMirrored.Outlined.Subject to likeColor
        type.startsWith("like") ->
            Icons.Outlined.Favorite to likeColor
        type == "comment" ->
            Icons.Outlined.ChatBubbleOutline to commentColor
        type == "reply_comment" ->
            Icons.AutoMirrored.Filled.Reply to commentColor
        type == "copy" || type == "repost" ->
            Icons.Outlined.Repeat to repostColor
        type == "mention" || type.startsWith("mention") ->
            Icons.Outlined.AlternateEmail to mentionColor
        type == "follow" ->
            Icons.Outlined.PersonAdd to followColor
        type == "friend_accepted" ->
            Icons.Outlined.Check to friendColor
        type == "friend_requested" ->
            Icons.Outlined.Group to friendColor
        type == "wall" ->
            @Suppress("DEPRECATION")
            Icons.AutoMirrored.Outlined.Subject to Color(0xFF607D8B)
        // Fix #254: типы из redesign-формата (notifications.getRedesign)
        type == "new_posts" || type == "post" ->
            Icons.Outlined.Dashboard to Color(0xFF607D8B)
        type == "photo" ->
            Icons.Outlined.Image to Color(0xFF795548)
        type == "video" ->
            Icons.Outlined.VideoCameraBack to Color(0xFFE91E63)
        type == "clip" ->
            Icons.Outlined.PlayCircle to Color(0xFFFF5722)
        type == "topic" ->
            @Suppress("DEPRECATION")
            Icons.AutoMirrored.Outlined.Subject to Color(0xFF9C27B0)
        type == "market" ->
            Icons.Outlined.ShoppingCart to Color(0xFFFF9800)
        type == "story" ->
            Icons.Outlined.AddAPhoto to Color(0xFF00BCD4)
        type == "app" ->
            Icons.Outlined.Apps to Color(0xFF4CAF50)
        type == "podcast" ->
            Icons.Outlined.MusicNote to Color(0xFF3F51B5)
        type == "birthday_reminder" ->
            Icons.Outlined.Cake to Color(0xFFFF5722)
        type == "app_request" ->
            Icons.Outlined.Notifications to Color(0xFF009688)
        // P1.8 (DIAG-C2 §2, §10 #14): 4 ранее fallback-типа получили собственные иконки.
        // Иконки выбраны из уже импортированных Material Icons Outlined (CalendarMonth
        // ранее был неиспользуемым импортом; MusicNote/Apps/ChatBubbleOutline уже
        // использовались для podcast/app/comment — здесь задействованы с ДРУГИМ tint,
        // чтобы тип был визуально различим).
        type == "audio" ->
            Icons.Outlined.MusicNote to Color(0xFF00BCD4)
        type == "event_invite" || type == "event" ->
            Icons.Outlined.CalendarMonth to Color(0xFF7C4DFF)
        type == "sdk_app_request" ->
            Icons.Outlined.Apps to Color(0xFFFF7043)
        type == "wall_reply" ->
            Icons.Outlined.ChatBubbleOutline to Color(0xFF607D8B)
        else ->
            Icons.Outlined.Notifications to defaultColor
    }
    return TypeIcon(
        icon = pair.first,
        tint = pair.second,
        bgTint = pair.second.copy(alpha = 0.12f),
    )
}

// ═══════════════════════════════════════════════════════════
// NOTIF-FIX-1 (Task 4): Секционные заголовки «НОВЫЕ» / «РАНЬШЕ»
// VK web разбивает список уведомлений на 2 секции: свежие (<24h)
// и просмотренные/старые (>=24h). Заголовок — мелкий капс-текст
// на tinted-полосе. Используем обычный `item { }` в LazyColumn
// (НЕ stickyHeader — он ExperimentalFoundationApi и иногда глючит
// со SwipeToDismissBox).
// ═══════════════════════════════════════════════════════════

private sealed interface NotificationListEntry {
    /** Секционный заголовок «НОВЫЕ» или «РАНЬШЕ». */
    data class Header(val text: String, val isNew: Boolean) : NotificationListEntry
    /** Карточка уведомления (обёрнута в SwipeToDismissBox). */
    data class Card(val item: VKApiClient.NotificationItem) : NotificationListEntry
}

/**
 * Мелкий капс-заголовок секции уведомлений.
 *
 * NOTIF-FIX-1 (Task 4): для «НОВЫЕ» используется primary-цвет (привлечение
 * внимания к свежим), для «РАНЬШЕ» — outline (нейтрально). Полоса с лёгким
 * surfaceVariant-tint чтобы визуально отделить секции.
 */
@Composable
private fun SectionHeader(text: String, isNew: Boolean) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isNew) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ═══════════════════════════════════════════════════════════
// #NOTIF-NFDCAT: категорийный сайдбар + переключатель Новые/Просмотренные
// ═══════════════════════════════════════════════════════════

/**
 * #NOTIF-NFDCAT: горизонтальный категорийный сайдбар серверных категорий
 * notifications.getRedesign (VK web-логика: выбор категории в сайдбаре
 * перезагружает список СЕРВЕРНО через category=..., а не фильтрует локально).
 * Визуальный паттерн — ленточный (активная категория с жирным текстом/подчёркиванием).
 * Рендерится в ScreenTopBar.subBar (persistent под TopAppBar).
 */
@Composable
private fun NotificationCategoryBar(
    categories: List<NotificationCategory>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories, key = { it.serverKey }) { cat ->
            val isSelected = selected == cat.serverKey
            // Чип-категория (аналог FeedFilterBar выбранного раздела).
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                    .clickable { onSelect(cat.serverKey) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    cat.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = cat.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * #NOTIF-NFDCAT: переключатель «Новые»/«Просмотренные».
 * Использует last_viewed (поле ответа getRedesign): «Новые» показывает только
 * уведомления моложе last_viewed, «Просмотренные» — все (или старше). Простая
 * UI-реализация: два текстовых сегмента, активный подсвечен. Фильтр применяется
 * в [filteredNotifications] через viewMatch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewViewedToggle(
    showViewed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Показать:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Сегмент «Новые»
        ToggleSegmentItem(
            text = "Новые",
            selected = !showViewed,
            onClick = { if (showViewed) onToggle() },
        )
        // Сегмент «Просмотренные»
        ToggleSegmentItem(
            text = "Просмотренные",
            selected = showViewed,
            onClick = { if (!showViewed) onToggle() },
        )
    }
}

/** #NOTIF-NFDCAT: один сегмент переключателя Новые/Просмотренные. */
@Composable
private fun ToggleSegmentItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ═══════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onUserClick: ((userId: Long) -> Unit)? = null,
    // #NOTIF-DEEPLINK (2026-10-02): переход по клику на уведомление к целевому
    // объекту (пост/комментарий/ответ/лайк/фото/видео/юзер) через существующий
    // движок VkUrlDeepLinker. Клик по карточке идёт сюда (deepLinkFor), а не в
    // примитивные onPostClick/onUserClick (остаются для превью/вложений).
    onDeepLink: ((VkUrlDeepLinker.DeepLinkAction) -> Unit)? = null,
    // #29: callbacks для notification-actions (§14.2)
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
    // #NOTIF-NFDCAT: кнопка «Настройки» в шапке → экран настроек уведомлений.
    onOpenNotificationSettings: (() -> Unit)? = null,
    // #FEED-MENU-NOTIF (2026-10-01): начальная серверная категория getRedesign
    // (all/communities/feedback/friends/services/communication/account), с которой
    // экран открывается из меню ленты (srv category → Screen.Notifications path-арг).
    // default "all" — «Уведомления профиля».
    initialCategory: String = "all",
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val pageSize = 30

    var notifications by remember { mutableStateOf<List<VKApiClient.NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var nextFrom by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    // NOTIF-FIX-1 (Task 5): счётчик непрочитанных уведомлений для бейджа в TopBar.
    // VK web показывает красный круг с числом рядом с заголовком «Уведомления».
    var unreadCount by remember { mutableStateOf(0) }
    // #NOTIF-NFDCAT: категорийный сайдбар + Новые/Просмотренные.
    val CATEGORY_ALL = "all"
    // #FEED-MENU-NOTIF: начальная категория — из аргумента маршрута (меню ленты),
    // иначе "all". remember (не rememberSaveable): при возврате на экран смена
    // категории в сайдбаре сбрасывается к категории маршрута-входа.
    var serverCategory by remember { mutableStateOf(initialCategory.ifBlank { CATEGORY_ALL }) }
    // last_viewed из последнего ответа getRedesign (переключатель Новые/Просмотренные).
    var lastViewed by remember { mutableStateOf<Long?>(null) }
    // По умолчанию = «Новые»: false = только «Новые» (моложе last_viewed).
    // true = показываем все/просмотренные. Переключатель работает
    // (юзер может переключить на «Просмотренные»).
    var showViewed by remember { mutableStateOf(false) }

    // FIX-C: PhotoViewer для фото-вложений уведомлений. Раньше тап по фото
    // открывал пост (onPostClick), теперь — native PhotoViewer (pinch-zoom + swipe).
    // photoViewerUrls != null → диалог показан; photoViewerIndex — стартовая позиция.
    var photoViewerUrls by remember { mutableStateOf<List<String>?>(null) }
    var photoViewerIndex by remember { mutableStateOf(0) }

    // Скрытые уведомления (для undo)
    val hiddenKeys = remember { mutableStateListOf<String>() }

    // Фильтрованный список.
    // #NOTIF-FEED-FILTER (19-B.2): после фильтрации применяется стабильная
    // двухклассовая сортировка sortDialogsFirst — диалоги (класс A) выше
    // новостных (класс B), внутри классов порядок VK. Поиск/undo/пагинация
    // не затронуты (сортировка меняет только порядок).
    val filteredNotifications = remember(notifications, searchQuery, hiddenKeys, lastViewed, showViewed) {
        sortDialogsFirst(
            notifications.filter { item ->
                val keyMatch = item.uniqueKey !in hiddenKeys
                // #NOTIF-NFDCAT: «Новые»/«Просмотренные» — фильтр по view-state.
                // Показываем «Новые» (моложе last_viewed) если showViewed=false.
                // Если last_viewed ещё не известен (null) — без фильтра (все видны).
                // Локальная val для locally smart cast (lastViewed — delegated property).
                val lastViewedValue = lastViewed
                val viewMatch = if (showViewed || lastViewedValue == null || lastViewedValue == 0L) {
                    true
                } else {
                    item.date >= lastViewedValue
                }
                val searchMatch = searchQuery.isBlank() ||
                    item.text.contains(searchQuery, ignoreCase = true) ||
                    item.parentText.contains(searchQuery, ignoreCase = true) ||
                    item.feedbackProfiles.any { it.name.contains(searchQuery, ignoreCase = true) }
                keyMatch && viewMatch && searchMatch
            },
        )
    }

    // Загрузка первой страницы.
    // Fix #248: убрали anti-pattern `LaunchedEffect(Unit) { scope.launch { ... } }`
    // — он запускал корутину в rememberCoroutineScope, которая переживает
    // LaunchedEffect и при уходе экрана кидала ForgottenCoroutineScopeException
    // внутрь catch(Exception), что показывало юзеру «Не удалось загрузить:
    // rememberCoroutineScope left the composition». Теперь корутина живёт
    // в scope самого LaunchedEffect — он отменяет её чисто при уходе.
    // #NOTIF-NFDCAT: ключ = serverCategory — смена серверной категории
    // (сайдбар) полностью перезагружает список через notificationsGetPage.
    LaunchedEffect(serverCategory) {
        loading = true
        endReached = false
        errorText = null
        // Сброс пагинации/скрытых при смене категории.
        hiddenKeys.clear()
        lastViewed = null
        try {
            val page = app.apiClient.notificationsGetPage(
                count = pageSize,
                category = if (serverCategory == CATEGORY_ALL) null else serverCategory,
            )
            val list = page.items
            val nf = page.nextFrom
            // #NOTIF-NFDCAT: сохраняем last_viewed для переключателя Новые/Просмотренные.
            val lv = page.lastViewed
            if (lv != null && lv > 0L) lastViewed = lv
            // Fix #253: логируем сколько items вернул API и сколько осталось
            // после distinctBy — если list.size > 0 но notifications.size == 0,
            // значит все дубликаты по uniqueKey и юзер видит пусто.
            val distinctCount = list.distinctBy { it.uniqueKey }.size
            AppLog.i("NotificationsScreen", "Loaded: api=${list.size}, distinct=$distinctCount, nextFrom=${nf?.take(40) ?: "null"}, errCode=${app.apiClient.lastApiErrorCode}")
            notifications = list.distinctBy { it.uniqueKey }
            nextFrom = nf
            // Fix #255: РАНЬШЕ было `if (list.size < pageSize) endReached = true`.
            // Это БАГ пагинации! VK getRedesign часто возвращает МЕНЬШЕ items,
            // чем запрошено (count=30, а вернулось 23), но next_from при этом
            // установлен — значит есть ещё страницы. Теперь используем
            // nextFrom == null как авторитетный сигнал конца списка.
            endReached = (nf == null)
            if (list.isEmpty()) {
                val errCode = app.apiClient.lastApiErrorCode
                errorText = when (errCode) {
                    // Fix #237: VK отключил notifications.get для новых
                    // web-токенов (vk1.a.*) — error 3 Unknown method.
                    // notificationsGet() внутри делает fallback на
                    // getRedesign, но если и он упал — показываем
                    // внятное сообщение, а не пустой экран.
                    3 -> "VK отключил метод уведомлений для этого токена (error 3). " +
                         "Нужен токен со scope=notifications, либо используйте раздел «Ответы/Диалоги»."
                    15 -> "Доступ к уведомлениям ограничен VK (error 15)."
                    5 -> "Токен недействителен. Авторизуйтесь заново."
                    0 -> null
                    else -> app.apiClient.lastApiError
                }
                AppLog.w("NotificationsScreen", "Loaded EMPTY list. errCode=$errCode, errorText=$errorText, lastApiError=${app.apiClient.lastApiError}")
            }
        } catch (e: CancellationException) {
            // Fix #248: корректная отмена (пользователь ушёл со экрана)
            // — НЕ показываем как ошибку, просто пробрасываем дальше.
            throw e
        } catch (e: Exception) {
            AppLog.e("NotificationsScreen", "Failed to load notifications", e)
            errorText = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
        }

        // NOTIF-FIX-1 (Task 5): параллельно с загрузкой ленты подтягиваем счётчики
        // непрочитанных. Делаем ВНЕ основного try/catch — если counters упадёт,
        // лента всё равно должна отрисоваться (без бейджа). Суммируем все значения
        // из map (VK возвращает {mentions: N, comments: M, ...} — общее число
        // непрочитанных = сумма). Скрываем бейдж при ошибке (unreadCount = 0).
        try {
            val counters = app.apiClient.notificationsGetUnreadCounters()
            val total = counters.values.sum()
            AppLog.i("NotificationsScreen", "Unread counters: $counters → total=$total")
            unreadCount = total
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("NotificationsScreen", "Failed to load unread counters: ${e.message}")
            unreadCount = 0
        }

        // §42 #PUSH-NOTIFICATIONS: пользователь открыл вкладку Уведомления —
        // отменяем все активные system notifications (лайки/комментарии/etc.)
        // и помечаем просмотренными server-side (VK перестаёт считать непрочитанными).
        // notificationsMarkAsViewed был dead code (defined но never called) —
        // теперь активируем.
        try {
            re.pinok.realtime.VkNotificationsNotifier.cancelAll(app)
            val viewed = app.apiClient.notificationsMarkAsViewed()
            AppLog.i("NotificationsScreen", "notificationsMarkAsViewed=$viewed, cancelled active VK notifications")
        } catch (e: Exception) {
            AppLog.w("NotificationsScreen", "markAsViewed/cancelAll failed: ${e.message}")
        }
    }

    // Pull-to-refresh
    fun refreshNotifications() {
        scope.launch {
            isRefreshing = true
            try {
                // #NOTIF-NFDCAT: refresh учитывает текущую серверную категорию.
                val page = app.apiClient.notificationsGetPage(
                    count = pageSize,
                    category = if (serverCategory == CATEGORY_ALL) null else serverCategory,
                )
                val list = page.items
                val nf = page.nextFrom
                notifications = list.distinctBy { it.uniqueKey }
                nextFrom = nf
                // Fix #255: endReached по nextFrom, не по размеру страницы
                endReached = (nf == null)
                errorText = null
                hiddenKeys.clear()
                val lv = page.lastViewed
                if (lv != null && lv > 0L) lastViewed = lv
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("NotificationsScreen", "refresh failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Пагинация
    fun loadMoreNotifications() {
        // #NOTIF-PAGINATION-DOUBLE-LOAD (2026-10-18): фикс двойного вызова.
        // Раньше loadingMore=true ставился ВНУТРИ scope.launch (асинхронно после
        // launch) — между вызовом loadMoreNotifications и фактической установкой
        // флага snapshotFlow (на filteredNotifications.size) успевал дёрнуть
        // loadMore ещё раз с тем же nextFrom → один cursor запрашивался дважды →
        // дубли, список плохо рос. Теперь флаг ставится СИНХРОННО до launch,
        // guard ниже мгновенно блокирует повторный вызов.
        if (loadingMore || endReached || notifications.isEmpty()) return
        // Fix #255: если nextFrom null — больше нет страниц (двойная проверка)
        if (nextFrom.isNullOrBlank()) {
            endReached = true
            return
        }
        loadingMore = true
        scope.launch {
            try {
                // #NOTIF-NFDCAT: loadMore сохраняет серверную категорию.
                val page = app.apiClient.notificationsGetPage(
                    count = pageSize,
                    startFrom = nextFrom,
                    category = if (serverCategory == CATEGORY_ALL) null else serverCategory,
                )
                val pageList = page.items
                val nf = page.nextFrom
                AppLog.i("NotificationsScreen", "loadMore: page=${pageList.size}, nextFrom=${nf?.take(40) ?: "null"}, existing=${notifications.size}")
                if (pageList.isNotEmpty()) {
                    // Fix #255: дедупликация по ПОЛНОМУ uniqueKey (теперь использует
                    // полный rawId, а не take(40)). Существующие + новые.
                    val existingKeys = notifications.map { it.uniqueKey }.toMutableSet()
                    val newItems = pageList.filter { it.uniqueKey !in existingKeys }
                    notifications = notifications + newItems
                }
                nextFrom = nf
                // Fix #255: endReached по nextFrom, не по размеру страницы.
                // VK может вернуть < pageSize items, но next_from будет установлен.
                if (nf == null) endReached = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("NotificationsScreen", "loadMore failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // #NOTIF-NFDCAT: dots_menu действия (hide / unsubscribe) — LOKAльный
    // dismiss + undo (UX-приоритет) + best-effort API-вызов.
    //
    // Точный web-endpoint hide/unsubscribe в архиве НЕ зафиксирован — API-вызовы
    // notificationsHideItem/notificationsUnsubscribe (VKApiClient) сделаны best-effort
    // с TODO: реальный endpoint уточнить по web-bundle. Локальный эффект (убрать
    // из списка + «Отменить») гарантирован всегда, независимо от результата API.
    // ═══════════════════════════════════════════════════════════════

    fun hideNotificationWithUndo(item: VKApiClient.NotificationItem) {
        val key = item.uniqueKey
        hiddenKeys.add(key)
        // VK-WEB-CARD: server-side hide через notifications.hide(query=...).
        val hideAction = item.dotsMenu.firstOrNull { it.isHide }
        scope.launch {
            if (hideAction != null) {
                app.apiClient.notificationsHideItem(hideAction.name, hideAction.query)
            }
            val result = snackbarHostState.showSnackbar(
                "Уведомление убрано",
                "Отменить",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                hiddenKeys.remove(key)
                // VK-WEB-CARD: server-side rollback через notifications.restore(query=...).
                if (hideAction != null) {
                    app.apiClient.notificationsRestore(hideAction.query)
                }
            }
        }
    }

    fun unsubscribeWithUndo(item: VKApiClient.NotificationItem) {
        val key = item.uniqueKey
        hiddenKeys.add(key)
        // VK-WEB-CARD: server-side unsubscribe через notifications.unsubscribe(query=...).
        val unsubAction = item.dotsMenu.firstOrNull { it.isUnsubscribe }
        scope.launch {
            if (unsubAction != null) {
                app.apiClient.notificationsUnsubscribe(unsubAction.name, unsubAction.query)
            }
            val result = snackbarHostState.showSnackbar(
                "Уведомления этого типа отключены",
                "Отменить",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                hiddenKeys.remove(key)
                // VK-WEB-CARD: server-side rollback через notifications.subscribe(query=...).
                if (unsubAction != null) {
                    app.apiClient.notificationsSubscribe(unsubAction.query)
                }
            }
        }
    }

    // Бесконечная пагинация
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= filteredNotifications.size - 3 && filteredNotifications.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreNotifications() }
    }

    // Fix #256: регистрируем actions + subBar в ГЛОБАЛЬНОМ TopAppBar (SovaNavHost).
    // Раньше тут был собственный Scaffold.topBar с заголовком «Уведомления» + поиск +
    // фильтр — он дублировал глобальный TopAppBar (hamburger + «Уведомления»).
    // Теперь глобальный TopAppBar один; мы только добавляем в него actions.
    // Fix #260: showSearch в ключе DisposableEffect — иначе configure()
    // вызывается один раз с showSearch=false → titleOverride=null навсегда;
    // TextField поиска не появляется при тапе на иконку.
    // P1.6 (DIAG-C2 §5): локальный тип-фильтр (activeFilter + #NOTIF-FEED-FILTER
    // bottom-sheet) удалён как мёртвый код — UI-триггер кнопки-фильтра был
    // убран #NOTIFSHOW, фильтр всегда "all". Серверная категория остаётся.
    // NOTIF-FIX-1 (Task 5): добавлен unreadCount в ключ — иначе бейдж не
    // перерисуется при изменении счётчика (configure() вызовется один раз
    // с unreadCount=0 и останется таким навсегда).
    DisposableEffect(showSearch, unreadCount, serverCategory, showViewed) {
        val token = ScreenTopBar.configure(
            // Actions: mark-all-read, search, settings
            actions = {
                // Кнопка "Прочитать все" (N6)
                // P1.4 (DIAG-C2 §4): после успешного notificationsMarkAsRead — локально
                // обнуляем unreadCount (оптимистичное обновление бейджа в заголовке
                // «Уведомления» — titleOverride Row с красным кругом) и отменяем активные
                // VK system notifications (аналог входа на экран — LaunchedEffect(serverCategory)
                // строка 853). Без этого бейдж оставался до следующего перезахода на экран.
                IconButton(
                    onClick = {
                        scope.launch {
                            val ok = app.apiClient.notificationsMarkAsRead()
                            if (ok) {
                                // Оптимистично зануляем локальный счётчик непрочитанных
                                // уведомлений — бейдж в titleOverride перерисуется мгновенно
                                // (unreadCount есть в ключах DisposableEffect, строка 1012).
                                unreadCount = 0
                                // Отменяем активные системные уведомления VK (setNumber-бейджи
                                // в шторке). Best-effort: неудача cancelAll не откатывает
                                // серверный markAsRead и не блокирует snackbar.
                                try {
                                    re.pinok.realtime.VkNotificationsNotifier.cancelAll(app)
                                } catch (e: Exception) {
                                    AppLog.w("NotificationsScreen", "cancelAll after mark-as-read failed: ${e.message}")
                                }
                                snackbarHostState.showSnackbar(
                                    "Все уведомления прочитаны",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Outlined.Visibility,
                        contentDescription = "Прочитать все",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Кнопка поиска
                IconButton(onClick = {
                    showSearch = !showSearch
                }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Кнопка фильтров (N10) убрана (#NOTIFSHOW): фильтр по типам закрыт.
                // P1.6 (DIAG-C2 §5): мёртвый код NOTIFICATION_FILTERS +
                // NotificationFilterSheet + activeFilter удалён. Шестерёнка настроек ниже.
                // #NOTIF-NFDCAT: кнопка «Настройки» (шестерёнка) в шапке уведомлений.
                // Переход к экрану настроек уведомлений (NotificationSettingsScreen).
                // Если callback не передан (например, экран вне навигационного графа) — скрываем.
                if (onOpenNotificationSettings != null) {
                    IconButton(
                        onClick = { onOpenNotificationSettings() },
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Настройки уведомлений",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            // titleOverride: НЕ-null ВСЕГДА (NOTIF-FIX-1, Task 5).
            // Раньше при showSearch=false передавался null → SovaNavHost рисовал
            // обычный `Text(currentTitle)` без бейджа. Теперь мы сами рисуем
            // Row { Text("Уведомления") + Spacer + Badge(unreadCount) }.
            // При showSearch=true — TextField как и раньше (поиск важнее бейджа).
            titleOverride = {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                    )
                } else {
                    // Row с заголовком + бейджем непрочитанных.
                    // VK web: «Уведомления» + красный круг с числом (99+ при >99).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Уведомления")
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            },
            // ─── #NOTIF-NFDCAT: категорийный сайдбар + переключатель Новые/Просмотренные ───
            // Рендерим как subBar под глобальным TopAppBar (persistent, не скроллится вместе
            // со списком — как FeedFilterBar в ленте). Выбор категории serverCategory
            // перезагружает список через LaunchedEffect(serverCategory) → notificationsGetPage.
            subBar = {
                NotificationCategoryBar(
                    categories = NOTIFICATION_CATEGORIES,
                    selected = serverCategory,
                    onSelect = { key ->
                        if (key != serverCategory) {
                            serverCategory = key
                        }
                    },
                )
                NewViewedToggle(
                    showViewed = showViewed,
                    onToggle = { showViewed = !showViewed },
                )
                // #NOTIF-NFDCAT: бейджи по категориям (п.7). notificationsGetUnreadCounters
                // возвращает {mentions/…} — не маппятся напрямую на категории сайдбара
                // (all/communities/…). TODO #NOTIF-NFDCAT: точный маппинг бейджей
                // getUnreadCounters → getRedesign категории не подтверждён — не рисуем.
            },
        )
        onDispose { ScreenTopBar.clear(token) }
    }

    Box(
        // P0.39-IME-UNIFY: windowInsetsPadding(ime.union(navbar)) — самоуправление.
        // IME закрыт → padding = navbar. IME открыт → padding = ime (включает navbar).
        // Раньше .imePadding() давал двойной подъём с NavHost.imePadding (теперь убран).
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        // SnackbarHost overlay (раньше был в Scaffold)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // P1.6 (DIAG-C2 §5): bottom-sheet «Фильтр» по типам удалён вместе с
        // кнопкой-триггером (#NOTIFSHOW). Мёртвый код activeFilter +
        // NotificationFilterSheet + NOTIFICATION_FILTERS удалён.
        // ─── Loading: Skeleton (N5) ───
        if (loading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(8) {
                    NotificationSkeletonCard()
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
                return@Box
            }

            // ─── Empty state ───
            if (filteredNotifications.isEmpty() && !isRefreshing) {
                ErrorView(
                    message = when {
                        notifications.isEmpty() -> errorText ?: "Нет новых уведомлений"
                        hiddenKeys.isNotEmpty() -> "Все уведомления скрыты"
                        searchQuery.isNotBlank() -> "Ничего не найдено по запросу «$searchQuery»"
                        else -> "Нет новых уведомлений"
                    },
                    onRetry = { refreshNotifications() },
                )
                return@Box
            }

            // ─── Main list ───
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshNotifications() },
                modifier = Modifier.fillMaxSize(),
            ) {
                // NOTIF-FIX-1 (Task 4): разбиваем отфильтрованный список на 2 секции —
                // «НОВЫЕ» (моложе 24 часов) и «РАНЬШЕ» (старше). VK web делает то же
                // самое на странице уведомлений. Сборка выполняется на каждой
                // рекомпозиции (filter O(n), n обычно < 200 — дёшево). Если список
                // пустой — обе секции пустые, заголовки не рисуются (см. условия ниже).
                val nowSec = System.currentTimeMillis() / 1000
                val newItems = filteredNotifications.filter { nowSec - it.date < 86400 }
                val oldItems = filteredNotifications.filter { nowSec - it.date >= 86400 }
                val listEntries = buildList {
                    if (newItems.isNotEmpty()) {
                        add(NotificationListEntry.Header("НОВЫЕ", isNew = true))
                        newItems.forEach { add(NotificationListEntry.Card(it)) }
                    }
                    if (oldItems.isNotEmpty()) {
                        add(NotificationListEntry.Header("РАНЬШЕ", isNew = false))
                        oldItems.forEach { add(NotificationListEntry.Card(it)) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // Fix #334: bottom contentPadding — небольшой отступ, чтобы
                    // последний footer-элемент («Загрузить ещё» / «Это все уведомления»)
                    // не прилипал к нижнему краю.
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(
                        listEntries,
                        key = { entry ->
                            when (entry) {
                                is NotificationListEntry.Header -> "header_${entry.text}"
                                is NotificationListEntry.Card -> "card_${entry.item.uniqueKey}"
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is NotificationListEntry.Header -> {
                                SectionHeader(text = entry.text, isNew = entry.isNew)
                            }
                            is NotificationListEntry.Card -> {
                                val item = entry.item
                                NotificationCardSwipeable(
                                    item = item,
                                    onDismiss = { dismissedItem ->
                                        val key = dismissedItem.uniqueKey
                                        hiddenKeys.add(key)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                "Уведомление скрыто",
                                                "Отменить",
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                hiddenKeys.remove(key)
                                            }
                                        }
                                    },
                                    onPostClick = onPostClick,
                                    onUserClick = onUserClick,
                                    onDeepLink = onDeepLink,
                                    onActionReply = onActionReply,
                                    onActionGiftReply = onActionGiftReply,
                                    // #NOTIF-NFDCAT: dots_menu действия через helper'ы
                                    // локал dismiss + undo + best-effort API (см. ниже).
                                    onHideNotification = { hideNotificationWithUndo(it) },
                                    onUnsubscribeNotification = { unsubscribeWithUndo(it) },
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    // FIX-C: фото-вложения открывают native PhotoViewer.
                                    onPhotoClick = { urls, idx ->
                                        photoViewerUrls = urls
                                        photoViewerIndex = idx
                                    },
                                    // VK-WEB-CARD: per-card NEW-бейдж.
                                    lastViewed = lastViewed,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                    // Футер пагинации
                    item {
                        when {
                            loadingMore -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            endReached && filteredNotifications.isNotEmpty() -> {
                                Text(
                                    text = "Это все уведомления",
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            // Fix #255: ручная кнопка «Загрузить ещё» как fallback.
                            // Показываем когда есть nextFrom, но скролл-триггер ещё не сработал
                            // (например, на экране мало items и юзер не скроллит).
                            !nextFrom.isNullOrBlank() && filteredNotifications.isNotEmpty() -> {
                                TextButton(
                                    onClick = { loadMoreNotifications() },
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                ) {
                                    Text("Загрузить ещё", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх» поверх
            // списка (после PullToRefreshBox — оверлей в Box-обёртке экрана).
            // На skeleton/empty-state не рисуется (выше ранние return@Box).
            ScrollToTopFab(
                listState = listState,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )
        }

        // FIX-C: PhotoViewer overlay для фото-вложений уведомлений.
        // photoViewerUrls != null → диалог показан поверх всего экрана (Dialog).
        // Список URL фото-вложений собирается в NotificationCard при тапе по фото.
        val pvUrls = photoViewerUrls
        if (pvUrls != null) {
            PhotoViewer(
                photos = pvUrls,
                initial = photoViewerIndex,
                onDismiss = { photoViewerUrls = null },
            )
        }
}

// ═══════════════════════════════════════════════════════════
// SwipeToDismiss обёртка (N8: скрыть + undo)
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCardSwipeable(
    item: VKApiClient.NotificationItem,
    onDismiss: (VKApiClient.NotificationItem) -> Unit,
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onUserClick: ((userId: Long) -> Unit)? = null,
    onDeepLink: ((VkUrlDeepLinker.DeepLinkAction) -> Unit)? = null,
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
    // #NOTIF-NFDCAT: действия «⋮» (dots_menu) — локальный dismiss + undo
    // (hide/unsubscribe). Передаются из NotificationsScreen (см. helper'ы).
    onHideNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onUnsubscribeNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    // FIX-C: тап по фото-вложению → PhotoViewer (полноэкранный просмотр).
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // VK-WEB-CARD: last_viewed для per-card NEW-бейджа.
    lastViewed: Long? = null,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDismiss(item)
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Скрыть",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        NotificationCard(
            item = item,
            onPostClick = onPostClick,
            onUserClick = onUserClick,
            onDeepLink = onDeepLink,
            onActionReply = onActionReply,
            onActionGiftReply = onActionGiftReply,
            onHideNotification = onHideNotification,
            onUnsubscribeNotification = onUnsubscribeNotification,
            onOpenNotificationSettings = onOpenNotificationSettings,
            // FIX-C: фото-вложения открывают native PhotoViewer.
            onPhotoClick = onPhotoClick,
            // VK-WEB-CARD: per-card NEW-бейдж.
            lastViewed = lastViewed,
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Notification Card (N2: иконки, N3: аватары, N4: превью, N7: действия)
// ═══════════════════════════════════════════════════════════

/** #NOTIF-FULL-DATE (2026-11-05): полная дата+время «дд.ММ.гггг ЧЧ:мм»
 * (например «05.11.2026 21:05») вместо относительного времени.
 * [tsSec] — Unix timestamp в СЕКУНДАХ (item.date, то же, что toRelativeTime:
 * System.currentTimeMillis()/1000 - this → Date(this*1000)). */
private fun formatFullDateTime(tsSec: Long): String {
    return try {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(tsSec * 1000L))
    } catch (_: Exception) {
        ""
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NotificationCard(
    item: VKApiClient.NotificationItem,
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onUserClick: ((userId: Long) -> Unit)? = null,
    onDeepLink: ((VkUrlDeepLinker.DeepLinkAction) -> Unit)? = null,
    // #29: callbacks для notification-actions (§14.2)
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
    // #NOTIF-NFDCAT: действия «⋮» (dots_menu)
    onHideNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onUnsubscribeNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    // FIX-C: тап по фото-вложению → PhotoViewer (полноэкранный просмотр).
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // VK-WEB-CARD: last_viewed из getRedesign ответа — для per-card NEW-бейджа.
    // Если item.date >= lastViewed — карточка «новая» (синяя точка рядом со временем).
    lastViewed: Long? = null,
) {
    val typeIcon = getTypeIcon(item.type)
    var showContextMenu by remember { mutableStateOf(false) }

    // #NOTIF-LINKIFY (2026-11-01): VK-ссылки/упоминания в текстах уведомлений
    // кликабельны → openUrlExternal → INTERNAL-LINK-ROUTING (нативные экраны).
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary

    // VK-WEB-CARD: header/text — ОТДЕЛЬНЫЕ поля от VK redesign API (по реальному HAR).
    // VK web: data-testid="notification-header" = имя, data-testid="notification-text" = глагол.
    // Прежний код отрезал prefix вручную (хрупко) — теперь парсер хранит их раздельно.
    // Fallback: если header пуст (legacy/парсинг сломан) — из feedbackProfiles.
    val profiles = item.feedbackProfiles
    val singleProfile = if (profiles.size == 1) profiles.first() else null
    val header: String = item.header.ifBlank {
        singleProfile?.name ?: ""
    }
    val displayText: String = item.text

    // #NOTIF-DEEPLINK (2026-10-02): клик по карточке → существующий движок
    // VkUrlDeepLinker.deepLinkFor(item) строит точный target (пост с commentId,
    // фото, видео, юзер, сообщество, уведомления). Раньше клик открывал пост
    // без commentId (для ответа на комментарий не скроллил), а лайк на комментарий
    // вёл на пост без параметров. Теперь всё аккуратное через DeepLinkAction.
    // Если deepLink дал OpenNotifications (нет объекта) и тип — follow/подписка —
    // оставляем прежний onUserClick фолбэк (открываем профиль отправителя).
    val navigate: () -> Unit = {
        val action = try {
            VkUrlDeepLinker.deepLinkFor(item)
        } catch (e: Exception) {
            re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenNotifications
        }
        // DIAG: логируем что построил deepLinker — для отладки переходов.
        AppLog.i("NotifNav", "type=${item.type} parentType=${item.parentType} " +
            "owner=${item.parentOwnerId} item=${item.parentItemId} " +
            "comment=${item.parentCommentId} url=${item.parentUrl} " +
            "→ action=$action")
        val handledByDeepLink =
            action !is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenNotifications
        if (handledByDeepLink && onDeepLink != null) {
            onDeepLink(action)
        } else if (onPostClick != null && item.parentOwnerId != 0L && item.parentItemId != 0L) {
            // VK-WEB-CARD: fallback на пост (НЕ на профиль — профиль открывается
            // только кликом по имени/header). Если parentOwnerId/ItemId=0 —
            // ничего не делаем (лучше чем переход в профиль по клику на карточку).
            onPostClick(item.parentOwnerId, item.parentItemId)
        }
        // Если deepLink не сработал и нет parent post — НЕ fallback на onUserClick.
        // Пользователь ждал переход к комментарию, а не в профиль отправителя.
        // Профиль открывается отдельным кликом по header (имени автора).
    }

    // #NOTIF-SNAP-CARD (2026-11-05): клип-карточка (like_comment/reply на short_video).
    // VK web присылает parentType="short_video" (чаще) или "clip". Для неё:
    //  • НЕ показываем parentText-сниппет (у клипа его нет — ролик, не пост);
    //  • превью рендерим ВЕРТИКАЛЬНЫМ 72×120 (см. AttachmentThumb).
    val isClip = item.parentType.equals("short_video", ignoreCase = true) ||
        item.parentType.equals("clip", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fix #256: ОБЯЗАТЕЛЬНЫЙ фон surface — без него прозрачная карточка
            // показывает SwipeToDismissBox.backgroundContent (errorContainer = красный).
            // Юзер видел все уведомления «красными».
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = navigate,
                onLongClick = { showContextMenu = true },
            )
            .padding(horizontal = UiScale.scaled(12.dp), vertical = UiScale.scaled(10.dp)),
        verticalAlignment = Alignment.Top,
    ) {
        // ─── Аватар / Иконка типа (VK-WEB-CARD: 48dp как в VK web) ───
        // Логика как на VK web:
        //  • 1 профиль-сообщество → аватар группы 48dp + малый бейдж типа (20dp) в углу
        //  • 1 профиль-пользователь → аватар пользователя 48dp + малый бейдж типа (20dp) в углу
        //  • несколько профилей или нет профилей → обычная иконка типа 48dp (старое поведение),
        //    аватары пользователей рендерятся как stacked-row внизу карточки
        // VK-WEB-CARD: кликабельные ссылки в карточке:
        //  1. Аватар → профиль отправителя (как в VK web — data-testid="notification-avatar")
        //  2. Имя автора (header) → профиль отправителя
        //  3. Текст/вложения → к комментарию/посту/вложению (navigate)
        // #NOTIF-CLICK-PARTS (2026-11-05): клик по имени/аватару отправителя.
        // VK API конвенция: id профиля-пользователя положительный, группа —
        // ОТРИЦАТЕЛЬНАЯ (-ownerId). Пользователь → профиль (onUserClick),
        // группа → сообщество через onDeepLink(OpenCommunity) (колбэка
        // onOpenCommunity в сигнатуре нет — переиспользуем onDeepLink).
        val firstFeedbackId: Long = item.feedbackIds.firstOrNull() ?: 0L
        val clickProfile: () -> Unit = {
            if (item.feedbackIds.isNotEmpty()) {
                val fid = firstFeedbackId
                if (fid < 0) {
                    if (onDeepLink != null) {
                        onDeepLink(re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenCommunity(-fid))
                    }
                } else if (onUserClick != null) {
                    onUserClick(fid)
                }
            }
        }
        val avatarUrl: String? = if (singleProfile != null) {
            val p200 = singleProfile.photo200
            val p100 = singleProfile.photo100
            when {
                p200.isNotBlank() -> p200
                p100.isNotBlank() -> p100
                else -> null
            }
        } else {
            null
        }
        // VK-WEB-CARD: аватар 40dp (как в VK web — не 48dp).
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (avatarUrl != null) MaterialTheme.colorScheme.surfaceVariant else typeIcon.bgTint)
                .clickable(onClick = clickProfile),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null && singleProfile != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = singleProfile.name,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    typeIcon.icon,
                    contentDescription = item.type,
                    tint = typeIcon.tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Малый бейдж типа в правом нижнем углу (поверх аватара).
            if (avatarUrl != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 22.dp, y = 22.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        typeIcon.icon,
                        contentDescription = null,
                        tint = typeIcon.tint,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // ─── Контент ───
        Column(modifier = Modifier.weight(1f)) {
            // #NOTIF-UNIFORM-CARD (2026-11-05): СТРОКА 1 в одну линию —
            // [имя (weight 1f, клик → профиль)] + [NEW-бейдж][время][⋮-меню].
            // Раньше имя было отдельным Text ниже, а время+⋮ — строкой выше;
            // по единому паттерну карточки теперь всё на одной строке:
            // имя слева (рядом с аватаром), время и ⋮-контекст-меню справа.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // #NOTIF-FULL-DATE (2026-11-05): слева в Строке 1 — вертикальный
                // блок «имя сверху, под ним дата+время» (weight 1f); справа —
                // NEW-бейдж (если есть) + ⋮-меню (время убрано из правой части).
                Column(modifier = Modifier.weight(1f)) {
                    if (header.isNotBlank()) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // #NOTIF-CLICK-PARTS: имя автора кликабельно → профиль.
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = clickProfile),
                        )
                    }
                    // #NOTIF-FULL-DATE (2026-11-05): полная дата под именем.
                    // item.date — Unix timestamp в секундах → *1000 для Date.
                    Text(
                        text = formatFullDateTime(item.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                // VK-WEB-CARD: per-card NEW-бейдж (data-testid="notification-viewed-badge"
                // в VK web — синяя точка для непрочитанных). item.date >= lastViewed = новое.
                val isNew = if (lastViewed != null && lastViewed > 0L) {
                    item.date >= lastViewed
                } else {
                    false
                }
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Box {
                    IconButton(
                        onClick = { showContextMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Меню",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // ─── Контекстное меню (N9) ───
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Открыть профиль") },
                            onClick = {
                                showContextMenu = false
                                if (onUserClick != null && item.feedbackIds.isNotEmpty()) {
                                    onUserClick(item.feedbackIds.first())
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Visibility, null, Modifier.size(20.dp)) },
                        )
                        if (item.parentOwnerId != 0L) {
                            DropdownMenuItem(
                                text = { Text("Открыть запись") },
                                onClick = {
                                    showContextMenu = false
                                    if (onPostClick != null && item.parentItemId != 0L) {
                                        onPostClick(item.parentOwnerId, item.parentItemId)
                                    }
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Subject, null, Modifier.size(20.dp)) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Скопировать текст") },
                            onClick = {
                                showContextMenu = false
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                val textToCopy = item.text.ifBlank { item.parentText }
                                if (clipboard != null && textToCopy.isNotBlank()) {
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VK уведомление", textToCopy))
                                    android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(20.dp)) },
                        )
                        // ─── #NOTIF-NFDCAT: dots_menu действия (VK web «⋮» меню) ───
                        if (onHideNotification != null && item.dotsMenu.any { it.isHide }) {
                            DropdownMenuItem(
                                text = { Text("Убрать из списка") },
                                onClick = {
                                    showContextMenu = false
                                    onHideNotification(item)
                                },
                                leadingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(20.dp)) },
                            )
                        }
                        if (onUnsubscribeNotification != null && item.dotsMenu.any { it.isUnsubscribe }) {
                            DropdownMenuItem(
                                text = { Text("Не уведомлять") },
                                onClick = {
                                    showContextMenu = false
                                    onUnsubscribeNotification(item)
                                },
                                leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null, Modifier.size(20.dp)) },
                            )
                        }
                        if (onOpenNotificationSettings != null && item.dotsMenu.any { it.isOpenSetting }) {
                            DropdownMenuItem(
                                text = { Text("Настроить") },
                                onClick = {
                                    showContextMenu = false
                                    onOpenNotificationSettings()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
            }

            // #NOTIF-UNIFORM-CARD (2026-11-05): небольшой отступ между строкой
            // заголовка и текстом уведомления (имя больше не несёт vertical padding).
            Spacer(modifier = Modifier.height(2.dp))

            // VK-WEB-CARD: текст уведомления (глагол без имени, как в VK web).
            if (displayText.isNotBlank()) {
                Text(
                    text = linkifyVkText(
                        displayText,
                        linkColor,
                        onUrlClick = { url -> openUrlExternal(context, url) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // #NOTIF-UNIFORM-CARD (2026-11-05): вложения/миниатюры ПОДНЯТЫ из низа
            // карточки сюда (перед сниппетом поста) — единый паттерн: [строка1] →
            // глагол → миниатюры → сниппет поста → текст комментария.
            // ─── #NOTIF-NFDCAT: attachment (entity_array / bubble / static_image) ───
            // • entity_array → attachments_string («Видео/Фото/4 фото/3 вложения»);
            // • bubble → текст комментария-пузыря (main_text);
            // • static_image → превью-картинка (для одноэлементного — уже в правой
            //   колонке compact-превью через attachments; здесь только заголовок есть,
            //   если attachments_string задан).
            if (item.attachmentType.equals("entity_array", ignoreCase = true) &&
                item.attachmentsString.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.attachmentsString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // #NOTIF-SNAP-CARD (2026-11-05): порядок блоков приведён к снапшоту VK web:
            // header → text → parentText (сниппет/подпись вложений) → attachments → reply/bubble.
            // VK web рендерит вложения (LazyRow/превью) ПОСЛЕ post-text, а не до него
            // (раньше LazyRow стоял над сниппетом — несоответствие снапшоту).

            // Превью текста родительского поста.
            // NOTIF-FIX-1 (Task 3): убрано `&& item.type != "wall"` — раньше превью
            // текста СКРЫВАЛОСЬ для wall/new_posts уведомлений, и карточка выглядела
            // пустой (только «опубликовало новый пост», без контекста ЧТО опубликовано).
            // По скриншоту VK web — превью текста показывается для ВСЕХ типов, включая
            // новые посты (аниме-название, детали эпизода и т.д.). maxLines увеличен
            // с 2 до 3 — VK web показывает до 3 строк превью.
            // #NOTIF-SNAP-CARD (2026-11-05): для клип-карточек (short_video/clip) сниппет
            // НЕ показываем — у короткого ролика нет post-text в снапшоте VK web
            // (тело карточки: вертикальное превью + main_text в бабле, БЕЗ parentText).
            if (item.parentText.isNotBlank() && !isClip) {
                Spacer(Modifier.height(4.dp))
                // #NOTIF-CLICK-PARTS (2026-11-05): превью поста (parentText) — явный
                // кликабельный блок → переход НА САМ ПОСТ (без скролла к комментарию,
                // commentId=0L). Раньше работал только неявно через клик по всей карточке.
                Text(
                    text = linkifyVkText(
                        item.parentText,
                        linkColor,
                        onUrlClick = { url -> openUrlExternal(context, url) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onDeepLink != null && item.parentOwnerId != 0L && item.parentItemId != 0L)
                        Modifier.clickable {
                            onDeepLink(re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenPost(item.parentOwnerId, item.parentItemId, 0L))
                        }
                    else
                        Modifier,
                )
            }

            // #NOTIF-SNAP-CARD (2026-11-05): горизонтальный скролл ВСЕХ вложений с
            // bubble overlay — ПОД parentText (порядок как в снапшоте VK web:
            // header → text → post-text → attachments). Для клип-карточек превью
            // рендерится вертикальным 72×120 (передаём isClip в AttachmentThumb).
            if (item.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(item.attachments) { att ->
                        AttachmentThumb(
                            attachment = att,
                            bubbleText = item.bubbleText,
                            isClip = isClip,
                            onClick = {
                                // VK-WEB-CARD (HAR-SNAP-FIX): для bubble-вложения (like_comment)
                                // клик по превью открывает ТОТ ЖЕ target что и клик по карточке
                                // (clip с commentId через deepLink), а НЕ onPostClick —
                                // onPostClick ожидает wall-post (ownerId, postId), а для bubble
                                // att.itemId это video_id/short_video_id, не post_id.
                                // Без этой проверки клик уводил в профиль или несуществующий пост.
                                if (item.attachmentType.equals("bubble", ignoreCase = true)) {
                                    navigate()
                                } else if (att.type == "video" || att.type == "clip") {
                                    // #NOTIF-UNIFORM-CARD (2026-11-05): клип/видео-превью →
                                    // navigate() (глубокий переход → OpenVideo), а НЕ onPostClick:
                                    // клип не wall-пост, onPostClick уводил в несуществующий пост.
                                    navigate()
                                }
                                // FIX-C: Фото — открываем native PhotoViewer (pinch-zoom + swipe),
                                // а не пост (см. аудит AUDIT-REPLIES-UI, находка 7).
                                // Список URL всех фото-вложений + индекс тапнутого → PhotoViewer.
                                else if (att.type == "photo" && !att.thumbUrl.isNullOrBlank()) {
                                    val photoUrls = item.attachments
                                        .filter { it.type == "photo" }
                                        .mapNotNull { it.thumbUrl }
                                    val clickedIdx = photoUrls.indexOf(att.thumbUrl)
                                    onPhotoClick(
                                        photoUrls,
                                        if (clickedIdx >= 0) clickedIdx else 0,
                                    )
                                }
                                // Прочие типы — fallback на пост/профиль (как раньше)
                                else if (onPostClick != null && att.ownerId != 0L && att.itemId != 0L) {
                                    onPostClick(att.ownerId, att.itemId)
                                }
                            },
                        )
                    }
                }
            }

            // Превью ответа (reply_comment)
            if (item.replyText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { navigate() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        item.profilesMap[item.replyFromId]?.let { replyProfile ->
                            Text(
                                text = replyProfile.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // #NOTIF-CLICK-PARTS (2026-11-05): имя автора ответа —
                                // кликабельно (те же правила знака, что в clickProfile):
                                // id>0 + onUserClick → профиль; id<0 + onDeepLink → сообщество.
                                modifier = when {
                                    item.replyFromId > 0 && onUserClick != null ->
                                        Modifier.clickable { onUserClick(item.replyFromId) }
                                    item.replyFromId < 0 && onDeepLink != null ->
                                        Modifier.clickable {
                                            onDeepLink(re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenCommunity(-item.replyFromId))
                                        }
                                    else -> Modifier
                                },
                            )
                        }
                        Text(
                            text = linkifyVkText(
                                item.replyText,
                                linkColor,
                                onUrlClick = { url -> openUrlExternal(context, url) },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // VK-WEB-CARD: bubbleText — отдельным блоком когда НЕТ attachments
            // (overlay только для превью). Если attachments есть — bubbleText рендерится
            // как overlay на превью (см. AttachmentThumb ниже).
            if (item.bubbleText.isNotBlank() && item.attachments.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { navigate() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = linkifyVkText(
                            item.bubbleText,
                            linkColor,
                            onUrlClick = { url -> openUrlExternal(context, url) },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // #NOTIF-NFDCAT: пред-кнопки (buttons.primary/secondary) — рендер ПОД
            // текстом уведомления (как VK web). Клик дефолтно открывает parent/
            // профиль/ЛС/подарки по actionType. TODO #NOTIF-NFDCAT: url пред-кнопки
            // (preButtonUrls[idx]) НЕ реализован как внешний открыватель — модель
            // кнопки не несёт надёжного web-открывателя; при появлении точного
            // маппинга (анкета → ссылка, ЛС → peerId) подключить url.
            if (item.preButtons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.preButtons.forEach { action ->
                        val needPost = item.parentOwnerId != 0L && item.parentItemId != 0L
                        val needUser = item.feedbackIds.isNotEmpty()
                        val onClick: () -> Unit = {
                            when (action.actionType) {
                                VKApiClient.NotificationAction.ActionType.GIFT_REPLY ->
                                    onActionGiftReply?.invoke(item.feedbackIds.firstOrNull() ?: 0L)
                                VKApiClient.NotificationAction.ActionType.REPLY ->
                                    if (needPost) onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                                    else onActionReply?.invoke(item.feedbackIds.firstOrNull() ?: 0L)
                                VKApiClient.NotificationAction.ActionType.OPEN_USER ->
                                    if (needUser) onUserClick?.invoke(item.feedbackIds.first())
                                VKApiClient.NotificationAction.ActionType.OPEN_POST ->
                                    if (needPost) onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                            }
                        }
                        when (action.style) {
                            VKApiClient.NotificationAction.ActionStyle.SECONDARY -> {
                                Button(
                                    onClick = onClick,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 12.dp, vertical = 4.dp,
                                    ),
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            VKApiClient.NotificationAction.ActionStyle.TERTIARY -> {
                                TextButton(
                                    onClick = onClick,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp, vertical = 0.dp,
                                    ),
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // #29: notification-actions — FlowRow кнопок (§14.2, §14.6)
            if (item.actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.actions.forEach { action ->
                        when (action.style) {
                            VKApiClient.NotificationAction.ActionStyle.SECONDARY -> {
                                Button(
                                    onClick = {
                                        when (action.actionType) {
                                            VKApiClient.NotificationAction.ActionType.GIFT_REPLY -> {
                                                onActionGiftReply?.invoke(action.targetUserId)
                                            }
                                            VKApiClient.NotificationAction.ActionType.REPLY -> {
                                                // Fix #233 (Q&A Bug C): «Ответить» на reply_comment должно
                                                // открывать пост (где был комментарий), а не чат с
                                                // комментатором. Чат — fallback только если parent неизвестен.
                                                if (item.parentOwnerId != 0L && item.parentItemId != 0L) {
                                                    onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                                                } else {
                                                    onActionReply?.invoke(action.targetUserId)
                                                }
                                            }
                                            VKApiClient.NotificationAction.ActionType.OPEN_USER -> {
                                                onUserClick?.invoke(action.targetUserId)
                                            }
                                            VKApiClient.NotificationAction.ActionType.OPEN_POST -> {
                                                if (item.parentOwnerId != 0L && item.parentItemId != 0L) {
                                                    onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 12.dp, vertical = 4.dp,
                                    ),
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            VKApiClient.NotificationAction.ActionStyle.TERTIARY -> {
                                TextButton(
                                    onClick = {
                                        when (action.actionType) {
                                            VKApiClient.NotificationAction.ActionType.GIFT_REPLY -> {
                                                onActionGiftReply?.invoke(action.targetUserId)
                                            }
                                            VKApiClient.NotificationAction.ActionType.REPLY -> {
                                                // Fix #233 (Q&A Bug C): то же что SECONDARY — открыть пост,
                                                // не чат с комментатором.
                                                if (item.parentOwnerId != 0L && item.parentItemId != 0L) {
                                                    onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                                                } else {
                                                    onActionReply?.invoke(action.targetUserId)
                                                }
                                            }
                                            VKApiClient.NotificationAction.ActionType.OPEN_USER -> {
                                                onUserClick?.invoke(action.targetUserId)
                                            }
                                            VKApiClient.NotificationAction.ActionType.OPEN_POST -> {
                                                if (item.parentOwnerId != 0L && item.parentItemId != 0L) {
                                                    onPostClick?.invoke(item.parentOwnerId, item.parentItemId)
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp, vertical = 0.dp,
                                    ),
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // VK-WEB-CARD: нижняя строка — только stacked аватары для multi-like.
            // Время уже вверху (строка заголовка), multiLike имена в header для single.
            // Для multi (feedbackProfiles.size > 1) — stacked avatars внизу.
            if (item.feedbackProfiles.size > 1) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val displayProfiles = item.feedbackProfiles.take(5)
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        displayProfiles.forEachIndexed { index, profile ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (index * 12).dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (profile.photo100.isNotBlank()) {
                                    AsyncImage(
                                        model = profile.photo100,
                                        contentDescription = profile.name,
                                        modifier = Modifier.size(20.dp).clip(CircleShape),
                                    )
                                } else {
                                    Text(
                                        text = profile.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (item.feedbackProfiles.size > 5) {
                            Text(
                                text = "+${item.feedbackProfiles.size - 5}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.offset(x = (5 * 12).dp),
                            )
                        }
                    }
                    // VK-WEB-CARD: compact names summary для multi-like.
                    val names = buildString {
                        item.feedbackProfiles.take(2).forEachIndexed { i, p ->
                            if (i > 0) append(", ")
                            append(if (p.name.isNotBlank()) p.name else "id${p.id}")
                        }
                        val rest = item.feedbackProfiles.size - 2
                        if (rest > 0) append(" и ещё $rest")
                    }
                    Text(
                        text = names,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // VK-WEB-CARD: compact-превью и отдельная кнопка ⋮ в правой колонке УБРАНЫ.
        // Все вложения рендерятся в LazyRow внутри Column (с bubble overlay).
        // Меню ⋮ перенесено в верхнюю строку заголовка (справа от времени).
    }
}

// ═══════════════════════════════════════════════════════════
// #29: AttachmentThumb — миниатюра вложения для LazyRow (§14.6)
// ═══════════════════════════════════════════════════════════

@Composable
private fun AttachmentThumb(
    attachment: VKApiClient.NotificationAttachment,
    onClick: () -> Unit,
    // VK-WEB-CARD: bubble-text overlay поверх превью (как в VK web — текст комментария
    // на превью клипа/видео). Пустая строка — overlay не рендерится.
    bubbleText: String = "",
    // #NOTIF-SNAP-CARD (2026-11-05): клип-карточка (like_comment/reply на short_video).
    // Если true — превью рендерим ВЕРТИКАЛЬНЫМ 72×120 (как в снапшоте VK web), иначе
    // квадратным 72×72. Для клипа bubbleText = main_text (заголовок ролика) — оверлей
    // остаётся поверх вертикального превью.
    isClip: Boolean = false,
) {
    // Размер превью: клип → вертикальный 72×120; остальное → квадрат 72×72.
    val thumbWidthDp = 72.dp
    val thumbHeightDp = if (isClip) 120.dp else 72.dp
    Box(
        modifier = Modifier
            .width(thumbWidthDp)
            .height(thumbHeightDp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!attachment.thumbUrl.isNullOrBlank()) {
            AsyncImage(
                model = attachment.thumbUrl,
                contentDescription = attachment.type,
                modifier = Modifier
                    .width(thumbWidthDp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            // Fallback-иконка по типу вложения
            val icon = when (attachment.type) {
                "video", "clip" -> Icons.Outlined.VideoCameraBack
                "gift" -> Icons.Outlined.Favorite
                "photo" -> Icons.Outlined.Image
                else -> Icons.Outlined.Image
            }
            Icon(
                icon,
                contentDescription = attachment.type,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        // Play-иконка для видео/клипов
        if (attachment.type == "video" || attachment.type == "clip") {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // VK-WEB-CARD: bubble-text overlay — текст комментария поверх превью (внизу).
        // Полупрозрачный gradient-фон + 2 строки текста (как в VK web).
        if (bubbleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = bubbleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Бейдж типа в углу
        if (attachment.type == "gift") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Favorite,
                    contentDescription = "Подарок",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Skeleton Card (N5: shimmer загрузка)
// ═══════════════════════════════════════════════════════════

@Composable
private fun NotificationSkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = shimmerAlpha)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Иконка placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(shimmerColor) },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Текст placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(shimmerColor) },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(shimmerColor) },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(shimmerColor) },
            )
        }
    }
}

