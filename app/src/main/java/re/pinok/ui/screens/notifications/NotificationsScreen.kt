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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
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
import re.pinok.realtime.VkUrlDeepLinker
import re.pinok.api.VKApiClient
import re.pinok.realtime.VkNotificationsNotifier
import re.pinok.ui.components.ErrorView
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.ui.theme.UiScale
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
//   • #NOTIF-FILTER-ACTION (Fix #354): redesign-парсер (parseRedesignNotificationItem)
//     больше НЕ сводит тип к типу ОБЪЕКТА (было: post→new_posts, photo→photo —
//     «Костя оценил фотографию» уходил в «Фото», поэтому «Реакции/Репосты/
//     Подписки/Упоминания» на web-токенах матчили 0 items — «фильтр почти не
//     работает», репорт юзера 2026-09-08). Теперь категория берётся из
//     dots_menu.name (категория настроек VK, доказана ЧАСТЬ 34:
//     {type:"open_setting", name:"new_posts"}), фолбэк — глагол действия в
//     item.text: «оценил»→like, «прокомментировал»→comment, «ответил»→
//     reply_comment, «упомянул»→mention, «поделился»→copy, «подписал»→follow,
//     «в друзья/заявк»→friend_accepted, «подарил»→gift, «пригласил»→
//     group_invites, «день рождения»→birthday_reminder
//     (VKApiClient.resolveRedesignNotificationType). Контентные типы без
//     action-глагола (photo/video/clip/…) сохранены — фильтры «Фото/Видео/
//     Клипы» работают как раньше; matching-ветки when ниже НЕ менялись:
//     startsWith("like")/("comment")/("mention")/"copy"/"follow"+
//     startsWith("friend") уже покрывают новые значения.
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
//     type message*, mail НЕ производят (rg-проверка), но неизвестный
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

// ═══════════════════════════════════════════════════════════
// #NOTIF-NFDCAT (категорийный сайдбар): СЕРВЕРНЫЕ категории
// notifications.getRedesign. ТЗ из снапшотов VK web-уведомлений:
//   all(«Уведомления профиля»), communities(«Сообщества»),
//   feedback(«Обратная связь»), friends(«Друзья»), services(«Сервисы»),
//   communication(«Общение»), account(«Аккаунт»).
// Выбор категории → серверная перезагрузка через
// notificationsGetPage(category=serverKey). Локальные типы-фильтры
// (NOTIFICATION_FILTERS, bottom-sheet «Фильтр») — ДОП. уровень поверх.
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
    var activeFilter by remember { mutableStateOf("all") }
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

    // Скрытые уведомления (для undo)
    val hiddenKeys = remember { mutableStateListOf<String>() }

    // Фильтрованный список.
    // #NOTIF-FEED-FILTER (19-B.2): после фильтрации применяется стабильная
    // двухклассовая сортировка sortDialogsFirst — диалоги (класс A) выше
    // новостных (класс B), внутри классов порядок VK. Поиск/undo/пагинация
    // не затронуты (сортировка меняет только порядок).
    val filteredNotifications = remember(notifications, searchQuery, activeFilter, hiddenKeys, lastViewed, showViewed) {
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
                        // VKA сегодня message*, mail не производят, но неизвестный
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
                keyMatch && viewMatch && filterMatch && searchMatch
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
        if (loadingMore || endReached || notifications.isEmpty()) return
        // Fix #255: если nextFrom null — больше нет страниц (двойная проверка)
        if (nextFrom.isNullOrBlank()) {
            endReached = true
            return
        }
        scope.launch {
            loadingMore = true
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
        // best-effort серверный вызов (endpoint не подтверждён; результат не критичен).
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
            }
        }
    }

    fun unsubscribeWithUndo(item: VKApiClient.NotificationItem) {
        val key = item.uniqueKey
        hiddenKeys.add(key)
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
    // #NOTIFSHOW: showFilters и кнопка фильтра-типов удалены (вход в
    // bottom-sheet был только у иконки-триггера) — ключа showFilters больше нет.
    // NOTIF-FIX-1 (Task 5): добавлен unreadCount в ключ — иначе бейдж не
    // перерисуется при изменении счётчика (configure() вызовется один раз
    // с unreadCount=0 и останется таким навсегда).
    DisposableEffect(showSearch, unreadCount, serverCategory, showViewed) {
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
                }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Кнопка фильтров (N10) убрана (#NOTIFSHOW): фильтр по типам закрыт;
                // шестерёнка настроек ниже остаётся.
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
                            activeFilter = "all"  // смена серверной категории сбрасывает локальный тип-фильтр
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
        // #NOTIFSHOW: bottom-sheet «Фильтр» по типам удалён вместе с кнопкой-триггером
        // (единственный вход был у иконки в TopBar). activeFilter остаётся "all".
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
                                    onDeepLink = onDeepLink,
                                    onActionReply = onActionReply,
                                    onActionGiftReply = onActionGiftReply,
                                    // #NOTIF-NFDCAT: dots_menu действия через helper'ы
                                    // локал dismiss + undo + best-effort API (см. ниже).
                                    onHideNotification = { hideNotificationWithUndo(it) },
                                    onUnsubscribeNotification = { unsubscribeWithUndo(it) },
                                    onOpenNotificationSettings = onOpenNotificationSettings,
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
    onDeepLink: ((VkUrlDeepLinker.DeepLinkAction) -> Unit)? = null,
    // #29: callbacks для notification-actions (§14.2)
    onActionReply: ((targetUserId: Long) -> Unit)? = null,
    onActionGiftReply: ((targetUserId: Long) -> Unit)? = null,
    // #NOTIF-NFDCAT: действия «⋮» (dots_menu)
    onHideNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onUnsubscribeNotification: ((VKApiClient.NotificationItem) -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
) {
    val typeIcon = getTypeIcon(item.type)
    var showContextMenu by remember { mutableStateOf(false) }

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
        val handledByDeepLink =
            action !is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenNotifications
        if (handledByDeepLink && onDeepLink != null) {
            onDeepLink(action)
        } else if (onUserClick != null && item.feedbackIds.isNotEmpty()) {
            onUserClick(item.feedbackIds.first())
        } else if (onPostClick != null && item.parentOwnerId != 0L && item.parentItemId != 0L) {
            onPostClick(item.parentOwnerId, item.parentItemId)
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
            .padding(horizontal = UiScale.scaled(12.dp), vertical = UiScale.scaled(10.dp)),
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
            if (item.bubbleText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = item.bubbleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
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

            // #NOTIF-MULTILK-NAMES: компактная сводка имён для случая «несколько
            // человек поставили лайк/реакцию» («Имя1, Имя2 и ещё N»). Показываем
            // когда profiles > 1 — тогда выше рисуется ряд аватаров, а здесь
            // короткая подпись, КТО именно отреагировал (требование фичи
            // #FEEDBACK-MULTILIKE: карточка должна показывать кто лайкнул).
            // До 2 имён из списка feedbackProfiles (порядок как вернул VK),
            // при большем числе — «и ещё N».
            if (item.feedbackProfiles.size > 1) {
                val names = buildString {
                    item.feedbackProfiles.take(2).forEachIndexed { i, p ->
                        if (i > 0) append(", ")
                        append(if (p.name.isNotBlank()) p.name else "id${p.id}")
                    }
                    val rest = item.feedbackProfiles.size - 2
                    if (rest > 0) append(" и ещё $rest")
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = names,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ─── Медиа-превью (N4) — compact, только когда attachments ≤ 1 ───
        // Когда attachments > 1, они рендерятся в LazyRow выше (в правой колонке
        // не хватает места). Compact-превью — для одного фото/видео.
        if (item.attachments.size <= 1) {
            val thumbUrl = item.parentPhotoUrl ?: item.parentVideoThumb
            // NOTIF-VIDEO-THUMB: если для одиночного видео-вложения превью не пришло
            // (getRedesign не отдаёт URL видео-превью), не оставляем карточку «голой» —
            // рисуем компактный плейсхолдер с видео-иконкой (как в AttachmentThumb).
            val singleType = item.attachments.firstOrNull()?.type
            val isVideoFallback = thumbUrl == null &&
                (singleType == "video" || singleType == "clip")
            if (thumbUrl != null || isVideoFallback) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = "Превью",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.VideoCameraBack,
                            contentDescription = "Видео",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    // Play-иконка для видео
                    if (thumbUrl != null && (item.parentVideoThumb != null || singleType == "clip")) {
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
                // ─── #NOTIF-NFDCAT: dots_menu действия (VK web «⋮» меню) ───
                // hide_notification → «Убрать из списка», unsubscribe → «Не уведомлять»,
                // open_setting → «Настроить». hide/unsubscribe: локальный dismiss + undo
                // (UX-приоритет) + best-effort API-вызов (см. NotificationsScreen helper'ы).
                // open_setting: переход на экран настроек уведомлений.
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

