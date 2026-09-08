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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.realtime.VkNotificationsNotifier
import re.pinok.ui.components.ErrorView
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.util.AppLog
import re.pinok.util.toRelativeTime

// ═══════════════════════════════════════════════════════════
// #NOTIF-FEED-FILTER (19-B): Фильтры по типам уведомлений.
//
// Фильтр = ПАТТЕРН ЛЕНТЫ (ТЗ 18-δ, пересмотрен юзером 2026-09-08).
// В VK web «Уведомления» — секция ЛЕНТЫ (vk.com/feed?section=notifications,
// SPA com_web_spa_notifications — лента.снапшоты.парсинг.полный.md:123),
// поэтому выбор категории воспроизводит ленточный паттерн выбора разделов
// (FeedScreen #FEED-FILTER: компактный триггер → список разделов с чекмарком
// на активной). Контейнер — bottom-sheet с заголовком «Фильтр» и чекмарком
// на активной (прямо указано в ТЗ; FeedScreen при этом открывает DropdownMenu
// из FeedFilterBar — список+чекмарк перенесён 1:1, см. NotificationFilterSheet).
// Прежний самодельный dropdown-паттерн (FilterList → FlowRow-чипы в subBar
// TopAppBar) удалён.
//
// Состав списка = «ядро VK web + категории реальных типов getRedesign».
// Ядро VK web-уведомлений (секция notifications ленты, ТЗ):
//   Все / Комментарии / Упоминания / Реакции(лайки) / Репосты / Подписки(друзья).
// Реальные type, приходящие в NotificationItem (сверено по коду парсеров VKA):
//   • redesign, web-токены (parseRedesignNotificationItem, VKApiClient:14731):
//     new_posts (entity post/wall/"") , photo, video, clip, comment, topic,
//     market, story, app, podcast; НЕизвестный entity.type проходит как есть
//     (VKApiClient:14743 — например gift);
//   • legacy, не-web токены (parseNotificationItem, VKApiClient:7291 +
//     buildNotificationText VKApiClient:7495): like_*, comment, reply_comment,
//     mention*, copy, wall, follow, friend_accepted, friend_requested, gift,
//     birthday_reminder.
//
// Честный след по каждому пункту ПРЕЖНЕГО списка (ничего не удалено
// молча):
//   • Все/Комментарии/Упоминания/Лайки/Репосты/Подписки — ядро, сохранены;
//     «Лайки» переименованы в «Реакции» (нейминг ленты: FeedFilter.LIKES =
//     «Реакции»; матчинг like_* не менялся);
//   • «Ответы» (reply_comment) — СЛИТ в «Комментарии»: отдельного фильтра
//     ответов в ядре VK web нет; матч «Комментариев» расширен до
//     startsWith("comment") + reply_comment (теперь честно покрывает и
//     legacy comment_*-варианты, если VK их вернёт);
//   • «Друзья» (friend*) — СЛИТ в «Подписки (друзья)» (ядро VK web:
//     follow + friend_accepted/friend_requested одним пунктом);
//   • «Стена» (wall) — УДАЛЕН: строгое подмножество «Новых постов» (тот
//     матчил wall/post/new_posts и до этого); в redesign wall→new_posts
//     (VKApiClient:14732), собственного класса типов пункт не имел;
//   • «Мероприятия» (event*) — УДАЛЕН: ни один парсер VKA не производит
//     type event* (rg по VKApiClient — 0 вхождений), категория была мёртвой;
//   • Сохранены архивные категории, отображающие РЕАЛЬНЫЕ типы:
//     Новые посты (ядро redesign), Фото/Видео/Клипы/Истории/Магазин/Игры
//     (redesign: photo/video/clip/story/market/app), Подарки (gift: legacy +
//     redesign pass-through), Дни рождения (birthday_reminder: legacy),
//     Приглашения (матч расширен на "invite_group" — тип обрабатывается
//     VkNotificationsNotifier.titleForType/buildActionVerb, прежний матч
//     group_invites/group_invite его не ловил);
//   • Сообщения/Групповые чаты — сохранены по ТЗ (класс «диалогов»,
//     класс A сортировки 19-B.2); честная оговорка: парсеры VKA сегодня
//     type message*/mail НЕ производят (rg-проверка), но неизвестный
//     entity.type проходит redesign-парсер как есть (VKApiClient:14743),
//     поэтому матч оставлен живым; типы topic/podcast фильтра не имеют
//     и видны в «Все».
// ═══════════════════════════════════════════════════════════

/**
 * #NOTIF-FEED-FILTER: один пункт bottom-sheet «Фильтр».
 * @param type ключ фильтра (= значение activeFilter; "all" — без фильтра)
 * @param label человекочитаемый заголовок (VK web-нейминг)
 * @param icon иконка пункта
 */
private data class NotificationFilter(
    val type: String,
    val label: String,
    val icon: ImageVector,
)

private val NOTIFICATION_FILTERS = listOf(
    // ── Ядро VK web (секция notifications ленты) ──
    NotificationFilter("all", "Все", Icons.Outlined.Notifications),
    NotificationFilter("comment", "Комментарии", Icons.Outlined.ChatBubbleOutline),
    NotificationFilter("mention", "Упоминания", Icons.Outlined.AlternateEmail),
    NotificationFilter("like", "Реакции", Icons.Outlined.FavoriteBorder),
    NotificationFilter("copy", "Репосты", Icons.Outlined.ContentCopy),
    NotificationFilter("follow", "Подписки (друзья)", Icons.Outlined.PersonAdd),
    // ── Категории реальных типов getRedesign / legacy ──
    NotificationFilter("new_posts", "Новые посты", Icons.Outlined.Dashboard),
    NotificationFilter("photos", "Фото", Icons.Outlined.PhotoLibrary),
    NotificationFilter("videos", "Видео", Icons.Outlined.PlayCircle),
    NotificationFilter("clips", "Клипы", Icons.Outlined.VideoCameraBack),
    NotificationFilter("stories", "Истории", Icons.Outlined.AddAPhoto),
    NotificationFilter("gifts", "Подарки", Icons.Outlined.CardGiftcard),
    NotificationFilter("birthday", "Дни рождения", Icons.Outlined.Cake),
    NotificationFilter("messages", "Сообщения", Icons.Outlined.Email),
    NotificationFilter("group_chats", "Групповые чаты", Icons.Outlined.Group),
    NotificationFilter("group_invites", "Приглашения", Icons.Outlined.GroupAdd),
    NotificationFilter("market", "Магазин", Icons.Outlined.ShoppingCart),
    NotificationFilter("apps_requests", "Игры", Icons.Outlined.Apps),
)

/**
 * #NOTIF-FEED-FILTER (19-B.2): стабильная двухклассовая сортировка списка
 * уведомлений — класс A (сообщения/диалоги: message*/mail/group_chats/chat,
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
// #NOTIF-FEED-FILTER (19-B): bottom-sheet «Фильтр»
// ═══════════════════════════════════════════════════════════

/**
 * #NOTIF-FEED-FILTER (19-B): выбор категории фильтра уведомлений — ленточный
 * визуальный паттерн (аналог FeedScreen #FEED-FILTER, FeedFilterBar): список
 * разделов [NOTIFICATION_FILTERS] с чекмарком на активной. Контейнер —
 * ModalBottomSheet с заголовком «Фильтр» (прямо указано в ТЗ; FeedScreen
 * открывает DropdownMenu — список+чекмарк воспроизведён 1:1, контейнер —
 * bottom-sheet по ТЗ). Прежний самодельный паттерн (FilterList → FlowRow-чипы
 * в subBar) удалён.
 *
 * Поведение как у ленточного DropdownMenu: выбор пункта закрывает список;
 * повторный тап на активную категорию просто закрывает (фильтр не сбрасывается).
 *
 * @param currentFilter текущее значение activeFilter
 * @param onSelect вызван при выборе категории (передаёт type пункта)
 * @param onDismiss закрытие шита (тап мимо / back / после выбора)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationFilterSheet(
    currentFilter: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Заголовок «Фильтр» — по ТЗ (паттерн AttachmentPickerSheet «Прикрепить»).
            Text(
                text = "Фильтр",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                items(NOTIFICATION_FILTERS, key = { it.type }) { f ->
                    val selected = currentFilter == f.type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(f.type)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            f.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = f.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        // Чекмарк на активной — как в ленточном FeedFilterBar
                        // (DropdownMenuItem leadingIcon=Check на текущем разделе).
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Выбрано",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onUserClick: ((userId: Long) -> Unit)? = null,
    // #29: callbacks для notification-actions (§14.2)
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
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
    var activeFilter by remember { mutableStateOf("all") }
    var showFilters by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // NOTIF-FIX-1 (Task 5): счётчик непрочитанных уведомлений для бейджа в TopBar.
    // VK web показывает красный круг с числом рядом с заголовком «Уведомления».
    var unreadCount by remember { mutableStateOf(0) }

    // Скрытые уведомления (для undo)
    val hiddenKeys = remember { mutableStateListOf<String>() }

    // Фильтрованный список.
    // #NOTIF-FEED-FILTER (19-B.2): после фильтрации применяется стабильная
    // двухклассовая сортировка sortDialogsFirst — диалоги (класс A) выше
    // новостных (класс B), внутри классов порядок VK. Поиск/undo/пагинация
    // не затронуты (сортировка меняет только порядок).
    val filteredNotifications = remember(notifications, searchQuery, activeFilter, hiddenKeys) {
        sortDialogsFirst(
            notifications.filter { item ->
                val keyMatch = item.uniqueKey !in hiddenKeys
                val filterMatch = activeFilter == "all" ||
                    when (activeFilter) {
                        // ── Ядро VK web ──
                        // #NOTIF-FEED-FILTER: «Комментарии» включает ответы
                        // (reply_comment слит по ядру VK web) и legacy comment_*.
                        "comment" -> item.type.startsWith("comment") || item.type == "reply_comment"
                        "mention" -> item.type.startsWith("mention")
                        "like" -> item.type.startsWith("like")
                        "copy" -> item.type == "copy" || item.type.startsWith("copy")
                        // «Подписки (друзья)»: follow + friend_* (слит по ядру).
                        "follow" -> item.type == "follow" || item.type.startsWith("friend")
                        // ── Категории реальных типов getRedesign/legacy ──
                        "new_posts" -> item.type == "wall" || item.type == "post" || item.type == "new_posts"
                        "birthday" -> item.type == "birthday_reminder"
                        "gifts" -> item.type == "gift"
                        // #NOTIF-FEED-FILTER: класс A сортировки 19-B.2. Парсеры
                        // VKA сегодня message*/mail не производят, но неизвестный
                        // entity.type проходит redesign как есть (VKApiClient:14743).
                        "messages" -> item.type.startsWith("message") || item.type == "mail"
                        "group_chats" -> item.type.startsWith("group") || item.type == "chat"
                        // #NOTIF-FEED-FILTER: + "invite_group" — тип обрабатывается
                        // VkNotificationsNotifier (titleForType), прежний матч
                        // group_invites/group_invite его не покрывал.
                        "group_invites" -> item.type == "group_invites" || item.type == "group_invite" || item.type == "invite_group"
                        "market" -> item.type == "market"
                        "clips" -> item.type.startsWith("clip")
                        "stories" -> item.type.startsWith("story")
                        "photos" -> item.type.startsWith("photo")
                        "videos" -> item.type.startsWith("video")
                        "apps_requests" -> item.type.startsWith("app")
                        else -> false
                    }
                val searchMatch = searchQuery.isBlank() ||
                    item.text.contains(searchQuery, ignoreCase = true) ||
                    item.parentText.contains(searchQuery, ignoreCase = true) ||
                    item.feedbackProfiles.any { it.name.contains(searchQuery, ignoreCase = true) }
                keyMatch && filterMatch && searchMatch
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
    LaunchedEffect(Unit) {
        loading = true
        endReached = false
        errorText = null
        try {
            val (list, nf) = app.apiClient.notificationsGet(count = pageSize)
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
                val (list, nf) = app.apiClient.notificationsGet(count = pageSize)
                notifications = list.distinctBy { it.uniqueKey }
                nextFrom = nf
                // Fix #255: endReached по nextFrom, не по размеру страницы
                endReached = (nf == null)
                errorText = null
                hiddenKeys.clear()
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
        if (loadingMore || endReached || notifications.isEmpty()) return
        // Fix #255: если nextFrom null — больше нет страниц (двойная проверка)
        if (nextFrom.isNullOrBlank()) {
            endReached = true
            return
        }
        scope.launch {
            loadingMore = true
            try {
                val (page, nf) = app.apiClient.notificationsGet(
                    count = pageSize,
                    startFrom = nextFrom,
                )
                AppLog.i("NotificationsScreen", "loadMore: page=${page.size}, nextFrom=${nf?.take(40) ?: "null"}, existing=${notifications.size}")
                if (page.isNotEmpty()) {
                    // Fix #255: дедупликация по ПОЛНОМУ uniqueKey (теперь использует
                    // полный rawId, а не take(40)). Существующие + новые.
                    val existingKeys = notifications.map { it.uniqueKey }.toMutableSet()
                    val newItems = page.filter { it.uniqueKey !in existingKeys }
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
    // Fix #260: showSearch/showFilters в ключе DisposableEffect — иначе
    // configure() вызывается один раз с showSearch=false, showFilters=false
    // → titleOverride=null навсегда; TextField поиска и подсветка кнопки
    // фильтра не появляются при тапе на иконки.
    // #NOTIF-FEED-FILTER (19-B): subBar с чипами больше не существует
    // (фильтр = bottom-sheet, рендерится ниже независимо от TopBar),
    // но ключ showFilters всё ещё нужен — от него зависит tint иконки-триггера.
    // NOTIF-FIX-1 (Task 5): добавлен unreadCount в ключ — иначе бейдж не
    // перерисуется при изменении счётчика (configure() вызовется один раз
    // с unreadCount=0 и останется таким навсегда).
    DisposableEffect(showSearch, showFilters, unreadCount) {
        val token = ScreenTopBar.configure(
            // Actions: mark-all-read, search toggle, filter toggle
            actions = {
                // Кнопка "Прочитать все" (N6)
                IconButton(
                    onClick = {
                        scope.launch {
                            val ok = app.apiClient.notificationsMarkAsRead()
                            if (ok) {
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
                    if (showSearch) showFilters = false
                }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Кнопка фильтров (N10)
                IconToggleButton(
                    checked = showFilters,
                    onCheckedChange = { showFilters = it; if (it) showSearch = false },
                ) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Фильтры",
                        tint = if (showFilters) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            // #NOTIF-FEED-FILTER (19-B): subBar с FlowRow-чипами УДАЛЁН — фильтр
            // переведён на ленточный паттерн (bottom-sheet «Фильтр» со списком
            // категорий + чекмарк, см. NotificationFilterSheet ниже). subBar
            // больше не передаётся (дефолт null в ScreenTopBar.configure).
        )
        onDispose { ScreenTopBar.clear(token) }
    }

    Box(
        // Fix #334: navigationBarsPadding + imePadding на outer Box — чтобы ВСЁ
        // содержимое (список, snackbar, loading-skeleton, empty-state) было выше
        // системной nav bar, а при открытии поиска — выше клавиатуры. Раньше footer
        // «Загрузить ещё» уходил под nav bar (screenshot Screenshot_20260729_214310).
        modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
    ) {
        // SnackbarHost overlay (раньше был в Scaffold)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // ─── #NOTIF-FEED-FILTER (19-B): bottom-sheet «Фильтр» (ленточный паттерн) ───
        // Размещён ДО loading/empty-ранних return'ов — чтобы шит открывался из
        // любого состояния экрана (триггер-иконка живёт в глобальном TopBar и
        // доступна и на skeleton, и на empty-state).
        if (showFilters) {
            NotificationFilterSheet(
                currentFilter = activeFilter,
                onSelect = { selected -> activeFilter = selected },
                onDismiss = { showFilters = false },
            )
        }
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
                        activeFilter != "all" -> "Нет уведомлений этого типа"
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
                                    onActionReply = onActionReply,
                                    onActionGiftReply = onActionGiftReply,
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
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
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
            onActionReply = onActionReply,
            onActionGiftReply = onActionGiftReply,
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Notification Card (N2: иконки, N3: аватары, N4: превью, N7: действия)
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NotificationCard(
    item: VKApiClient.NotificationItem,
    onPostClick: ((ownerId: Long, postId: Long) -> Unit)? = null,
    onUserClick: ((userId: Long) -> Unit)? = null,
    // #29: callbacks для notification-actions (§14.2)
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
) {
    val typeIcon = getTypeIcon(item.type)
    var showContextMenu by remember { mutableStateOf(false) }

    // Deep-link navigation on click (S6-5)
    val navigate: () -> Unit = {
        if (onUserClick != null && item.feedbackIds.isNotEmpty() &&
            item.type in listOf("follow", "friend_accepted", "friend_requested")) {
            onUserClick(item.feedbackIds.first())
        } else if (onPostClick != null && item.parentOwnerId != 0L && item.parentItemId != 0L) {
            onPostClick(item.parentOwnerId, item.parentItemId)
        } else if (onUserClick != null && item.feedbackIds.isNotEmpty()) {
            onUserClick(item.feedbackIds.first())
        }
    }

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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ─── Аватар / Иконка типа (NOTIF-FIX-1, Task 2) ───
        // Логика как на VK web:
        //  • 1 профиль-сообщество → аватар группы 40dp + малый бейдж типа (16dp) в углу
        //  • 1 профиль-пользователь → аватар пользователя 40dp + малый бейдж типа (16dp) в углу
        //  • несколько профилей или нет профилей → обычная иконка типа 40dp (старое поведение),
        //    аватары пользователей рендерятся как stacked-row внизу карточки
        // NULLSAFE-1: заменили `item.feedbackProfiles.takeIf { it.size == 1 }?.first()` и
        // `singleProfile?.let { ... }` на явные null-check'и + локальные val.
        val profiles = item.feedbackProfiles
        val singleProfile = if (profiles.size == 1) profiles.first() else null
        val avatarUrl: String? = if (singleProfile != null) {
            val p200 = singleProfile.photo200
            val p100 = singleProfile.photo100
            when {
                p200.isNotBlank() -> p200
                p100.isNotBlank() -> p100
                else -> null
            }
        } else null
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (avatarUrl != null) MaterialTheme.colorScheme.surfaceVariant else typeIcon.bgTint),
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
            // VK web показывает аналогичный значок: иконка действия (лайк/коммент/пост)
            // поверх аватара отправителя, чтобы было видно ЧТО сделал пользователь.
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
            // Текст уведомления
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Превью текста родительского поста.
            // NOTIF-FIX-1 (Task 3): убрано `&& item.type != "wall"` — раньше превью
            // текста СКРЫВАЛОСЬ для wall/new_posts уведомлений, и карточка выглядела
            // пустой (только «опубликовало новый пост», без контекста ЧТО опубликовано).
            // По скриншоту VK web — превью текста показывается для ВСЕХ типов, включая
            // новые посты (аниме-название, детали эпизода и т.д.). maxLines увеличен
            // с 2 до 3 — VK web показывает до 3 строк превью.
            if (item.parentText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.parentText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Превью ответа (reply_comment)
            if (item.replyText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                            )
                        }
                        Text(
                            text = item.replyText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // #29: notification-attachments — горизонтальный скролл всех вложений (§14.2, §14.6)
            // Показываем LazyRow только когда вложений > 1 (одно вложение рендерится
            // как compact-превью в правой колонке — см. thumbUrl ниже).
            if (item.attachments.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(item.attachments) { att ->
                        AttachmentThumb(
                            attachment = att,
                            onClick = {
                                if (att.type == "video" || att.type == "clip") {
                                    // Видео/клип — открываем через onPostClick с owner/id
                                    if (onPostClick != null && att.ownerId != 0L && att.itemId != 0L) {
                                        onPostClick(att.ownerId, att.itemId)
                                    }
                                }
                                // Фото — пока просто открываем профиль/пост
                                else if (onPostClick != null && att.ownerId != 0L && att.itemId != 0L) {
                                    onPostClick(att.ownerId, att.itemId)
                                }
                            },
                        )
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

            // Нижняя строка: время + аватары
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Время
                Text(
                    text = item.date.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                // Аватары (N3) — stacked. NOTIF-FIX-1 (Task 2): скрываем row когда
                // feedbackProfile всего один — в этом случае его аватар уже отрисован
                // как 40dp primary visual в левом блоке, и повторять его 20dp копию
                // в нижней строке бессмысленно (визуальный шум). Stacked-row нужен
                // только когда несколько человек поставили лайк/коммент.
                if (item.feedbackProfiles.size > 1) {
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
                }
            }
        }

        // ─── Медиа-превью (N4) — compact, только когда attachments ≤ 1 ───
        // Когда attachments > 1, они рендерятся в LazyRow выше (в правой колонке
        // не хватает места). Compact-превью — для одного фото/видео.
        if (item.attachments.size <= 1) {
            val thumbUrl = item.parentPhotoUrl ?: item.parentVideoThumb
            if (thumbUrl != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = "Превью",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    // Play-иконка для видео
                    if (item.parentVideoThumb != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PlayCircle,
                                contentDescription = "Видео",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // #29: notification-menu — отдельная кнопка ⋮ (§14.2)
        // Раньше меню открывалось только long-press. Теперь есть видимая кнопка.
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
            val context = LocalContext.current
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
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// #29: AttachmentThumb — миниатюра вложения для LazyRow (§14.6)
// ═══════════════════════════════════════════════════════════

@Composable
private fun AttachmentThumb(
    attachment: VKApiClient.NotificationAttachment,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!attachment.thumbUrl.isNullOrBlank()) {
            AsyncImage(
                model = attachment.thumbUrl,
                contentDescription = attachment.type,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
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

