package re.pinok.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.ui.anim.LocalAnimScale
import re.pinok.ui.anim.tweenScaled
import re.pinok.ui.navigation.Screen
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════
// #FEED-MENU-NOTIF-7 (2026-10-02): по прямому требованию юзера меню ленты
// УРЕЗАНО до ТОЛЬКО 7 категорий уведомлений (#FEED-MENU-NOTIF) — Локальные
// фильтры ленты (Лента/Фотографии/Друзья/Поиск/Реакции, filterName) УДАЛЕНЫ,
// параметры currentFilterName/onSectionSelected под них убраны из сигнатуры.
// Всё, что рисуется в панели — 7 категорий уведомлений и закреплённая строка
// «Редактировать» (Скрытые источники, Screen.FeedHidden). Ниже — история.
//
// История: #FEED-MENU-VKWEB (Fix #353, волна 23): правое боковое меню ленты =
// «Список ленты» VK web (vk.ru/feed, data-testid="rightmenu",
// aria-label «Список ленты» — лента.снапшоты.парсинг.полный.md §1.0.2).
//
// Прежняя версия (19-A) повторяла НЕ то, что в VK web: «Друзья онлайн» /
// «Возможные друзья» / «Рекомендуемые сообщества» / «Мои закладки».
// По прямому требованию юзера («Зачем мне там закладки и прочая ерунда?»)
// и по снапшоту меню — это НАВИГАЦИЯ РАЗДЕЛОВ ленты (тогда):
//
//   testid                              VK web        PinoK (FeedFilter)
//   feed_right_menu_news                Лента         ALL (newsfeed.get)
//   feed_right_menu_sources_photos      Фотографии    PHOTOS (фильтр фото)
//   feed_right_menu_sources_friends     Друзья        FRIENDS (лента постов
//                                     |             друзей, IMP-FEED-1)
//   feed_right_menu_global_search       Поиск         SEARCH (newsfeed.search)
//   feed_right_menu_wall_likes          Реакции       LIKES (likes.getList,
//                                     |             5 подтабов как в VK web)
//   feed_right_menu_sources_action_edit Редактировать → Screen.FeedHidden
//                                       («Скрытые источники», менеджер
//                                       мьютов ленты — эквивалент web-акта
//                                       al_settings.php?act=a_edit_owners_list)
//
// #FEED-MENU-VKWEB-2 (2026-10-02): меню РАСШИРЕНО до навигации по разделам
// приложения (образец VK-навигации). Лента/Фотографии/Друзья/Поиск/Реакции —
// по-прежнему переключали раздел ТЕКУЩЕЙ ленты (feedFilterName + reloadFeed)
// через onSectionSelected. В #FEED-MENU-NOTIF-7 ВСЕ они удалены.
//
// #FEED-MENU-NOTIF (2026-10-01): навигационные разделы (Уведомления/Мессенджер/
// Сообщества/Видео/Клипы/Музыка/Сервисы/Закладки/Файлы — Screen.*) УБРАНЫ из
// меню. Вместо них — КАТЕГОРИИ УВЕДОМЛЕНИЙ (подписи из веб-сайдбара VK,
// notifications.getRedesign): Уведомления профиля/Сообщества/Обратная связь/
// Друзья/Сервисы/Общение/Аккаунт. Клик по категории открывает NotificationsScreen
// с этой категорией (onNavigateCategory → Screen.Notifications.buildRoute(category)).
// Бейджей в меню НЕТ (у категорий уведомлений простого глобального источника
// нет — счётчик живёт локально внутри NotificationsScreen).
//
// «Рекомендации» в меню НЕТ — как в VK web (она осталась в заголовочном
// dropdown FeedFilterBar: таб «Все новости / Рекомендации» sticky-хедера).
//
// УДАЛЕНО по требованию юзера (в VK web rightmenu отсутствует): «Друзья
// онлайн» (friendsGetOnline), «Возможные друзья» (friendsGetSuggestions),
// «Рекомендуемые сообщества» (groupsGetCatalog), «Мои закладки» (faveGet).
// Раздел «Друзья» = лента постов ДРУЗЕЙ (§1.2 снапшота), а не список
// «возможных друзей»; блок рекомендаций над лентой друзей также убран из
// FeedScreen (#FEED-MENU-VKWEB). VKA-методы не тронуты (API-поверхность).
//
// Fix #390 #NOTIFY-MODES: закреплённый блок «Режим уведомлений» ПЕРЕЕХАЛ
// из этой панели в ОСНОВНУЮ (глобальную) боковую панель навигации приложения
// (SovaNavHost.ModalNavigationDrawer) — режим один на всё приложение, доступ к
// нему нужен из любого раздела. Общие данные и компонент — NotifyModeItem в
// NotifyModeSelector.kt (NOTIFY_MODE_OPTIONS, notifyModeLabel, диалог выбора).
// Здесь панель ленты возвращена к чистой навигации разделов без pinned-блока.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Один пункт меню: человекочитаемый заголовок, иконка, data-testid VK web
 * (для трассировки в KDoc-таблице выше) и ровно ОДИН из двух типов действия:
 *
 *  - [notificationCategory] != null — пункт КАТЕГОРИИ уведомлений
 *    (#FEED-MENU-NOTIF-7, VK web getRedesign): выбор открывает NotificationsScreen
 *    с этой серверной категорией (Screen.Notifications path-аргумент).
 *  - [screen] != null — пункт НАВИГАЦИИ на другой раздел приложения
 *    (Screen.* drawer-паттерн, popUpTo+restoreState). Из списка в текущей
 *    версии не используется (меню — только категории уведомлений), поле
 *    оставлено для возможного будущего пункта меню.
 *  - Все == null — пункт недоступен и скрывается из списка
 *    ([FEED_MENU_ENTRIES] фильтрует null-действия).
 *
 * Поле [filterName] (локальные фильтры ленты Лента/Фотографии/Друзья/Поиск/
 * Реакции) УДАЛЕНО по требованию юзера (#FEED-MENU-NOTIF-7) — в меню остались
 * только категории уведомлений, фильтры ленты в правой панели больше не рисуются.
 */
private data class FeedMenuEntry(
    val vkTestId: String,
    val label: String,
    val icon: ImageVector,
    val notificationCategory: String? = null,
    val screen: Screen? = null,
)

// #FEED-MENU-NOTIF-7 (2026-10-02): по требованию юзера в меню ленты осталось
// ТОЛЬКО 7 КАТЕГОРИЙ УВЕДОМЛЕНИЙ (#FEED-MENU-NOTIF, 2026-10-01). Локальные
// фильтры ленты (Лента/Фотографии/Друзья/Поиск/Реакции, filterName) УДАЛЕНЫ —
// они переключали саму ленту, а не разделы, и юзеру они не нужны. Подписи
// категорий — точные из веб-сайдбара уведомлений VK (notifications.getRedesign):
// all(«Уведомления профиля»), communities(«Сообщества»), feedback(«Обратная
// связь»), friends(«Друзья»), services(«Сервисы»), communication(«Общение»),
// account(«Аккаунт»). Клик по категории → NotificationsScreen с этой категорией
// (onNavigateCategory → Screen.Notifications.buildRoute(category)).
private val FEED_MENU_ENTRIES = listOf(
    // ── Категории уведомлений (открывают NotificationsScreen с категорией) ──
    FeedMenuEntry("feed_right_menu_notif_all", "Уведомления профиля", Icons.Outlined.Notifications, notificationCategory = "all"),
    FeedMenuEntry("feed_right_menu_notif_communities", "Сообщества", Icons.Outlined.Groups, notificationCategory = "communities"),
    FeedMenuEntry("feed_right_menu_notif_feedback", "Обратная связь", Icons.Outlined.RateReview, notificationCategory = "feedback"),
    FeedMenuEntry("feed_right_menu_notif_friends", "Друзья", Icons.Outlined.Group, notificationCategory = "friends"),
    FeedMenuEntry("feed_right_menu_notif_services", "Сервисы", Icons.Outlined.Apps, notificationCategory = "services"),
    FeedMenuEntry("feed_right_menu_notif_communication", "Общение", Icons.Outlined.Forum, notificationCategory = "communication"),
    FeedMenuEntry("feed_right_menu_notif_account", "Аккаунт", Icons.Outlined.AccountBox, notificationCategory = "account"),
)

/** Все пункты меню с реализуемым действием (пункты без действия скрыты). */
private val FEED_MENU_VISIBLE = FEED_MENU_ENTRIES.filter { it.notificationCategory != null }

/**
 * #FEED-MENU-NOTIF-7: правое боковое меню ленты (оверлей поверх контента
 * FeedScreen: Scrim + панель справа). Всегда в композиции (visible-флаг) →
 * AnimatedVisibility проигрывает и вход, и выход.
 *
 * По требованию юзера меню содержит ТОЛЬКО 7 категорий уведомлений
 * (#FEED-MENU-NOTIF-7) — локальные фильтры ленты (Лента/Фотографии/Друзья/
 * Поиск/Реакции) и параметры currentFilterName/onSectionSelected под них
 * УДАЛЕНЫ: панель больше не переключает фильтр ленты.
 *
 * @param visible          открыта ли панель (state FeedScreen.showRightPanel)
 * @param onDismiss        закрыть панель (тап по Scrim / «×» / системный back)
 * @param onNavigateCategory(category) выбор КАТЕГОРИИ уведомлений (#FEED-MENU-NOTIF)
 *                         — FeedScreen закрывает панель и открывает
 *                         NotificationsScreen с этой категорией
 * @param onNavigate(screen) выбор пункта НАВИГАЦИИ (Screen.* drawer-паттерн).
 *                         В текущем списке меню не используется (меню — только
 *                         категории уведомлений), оставлен для совместимости
 *                         сигнатуры.
 * @param onOpenHiddenSources «Редактировать» → «Скрытые источники»
 *                         (Screen.FeedHidden, newsfeed.getBanned + unban)
 */
@Composable
fun FeedRightPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigateCategory: (String) -> Unit,
    onNavigate: (Screen) -> Unit,
    onOpenHiddenSources: () -> Unit,
) {
    // Fix #390 #NOTIFY-MODES: блок «Режим уведомлений» УБРАН из этой панели
    // (переехал в глобальный drawer — NotifyModeItem), поэтому internal-стейта
    // диалога здесь больше нет. Панель возвращена к чистой навигации разделов.

    // Системный back закрывает панель, а не покидает экран (прецедент 19-A).
    BackHandler(enabled = visible) { onDismiss() }

    // Fix #224: скорость анимаций — панель и scrim масштабируются настройкой
    // «Скорость анимации» (LocalAnimScale). 0 → мгновенно (snap).
    val animScale = LocalAnimScale.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Оверлей: Scrim + панель. Дети Box: сначала Scrim, потом панель —
        // панель выше по z, клики по ней не доходят до Scrim. ──
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tweenScaled(animScale, 300)),
            exit = fadeOut(tweenScaled(animScale, 300)),
        ) {
            // Scrim: тап вне панели закрывает её (индикация клика отключена —
            // ripple на полупрозрачной подложке выглядит как артефакт).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tweenScaled<androidx.compose.ui.unit.IntOffset>(animScale, 300)) { it },
            exit = slideOutHorizontally(tweenScaled<androidx.compose.ui.unit.IntOffset>(animScale, 300)) { it },
        ) {
            // Ширина/скругление — как в прежней версии 19-A (визуальная
            // преемственность, поменялось только СОДЕРЖИМОЕ меню).
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            ) {
                Column {
                    // Заголовок + закрытие «×» — как в прежней версии и в VK web
                    // (у web-меню нет заголовка, но мобильному оверлею нужна
                    // явная кнопка закрытия рядом с жестом back и тапом по Scrim).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Меню ленты",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть меню",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()

                    // Fix #390 #NOTIFY-MODES: блок «Режим уведомлений» УБРАН отсюда
                    // (переехал в глобальный drawer — NotifyModeItem). Разделы ленты и
                    // «Редактировать» остались в прокручиваемой Column, чтобы при
                    // переполнении контент листался в рамках панели.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // #FEED-MENU-NOTIF-7: только КАТЕГОРИИ УВЕДОМЛЕНИЙ
                        // (все пункты FEED_MENU_ENTRIES имеют notificationCategory).
                        // Клик по категории открывает NotificationsScreen с категорией
                        // через onNavigateCategory. Локальных фильтров ленты больше нет.
                        FEED_MENU_VISIBLE.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable {
                                        AppLog.i("FeedRightPanel", "notif category: ${entry.vkTestId} -> ${entry.notificationCategory}")
                                        onNavigateCategory(entry.notificationCategory ?: "all")
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    entry.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        HorizontalDivider()

                        // «Редактировать» (feed_right_menu_sources_action_edit) —
                        // менеджер лент VK web (списки/скрытые источники). Мобильный
                        // эквивалент — «Скрытые источники» (newsfeed.getBanned/unban,
                        // IMP-FEED-2); списки лент — честное отклонение (моб. API
                        // newsfeed.getLists не заведён, см. §3.4 снапшота).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    AppLog.i("FeedRightPanel", "edit -> hidden sources")
                                    onOpenHiddenSources()
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Редактировать",
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
