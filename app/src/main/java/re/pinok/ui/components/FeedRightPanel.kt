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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
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
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════
// #FEED-MENU-VKWEB (Fix #353, волна 23): правое боковое меню ленты =
// «Список ленты» VK web (vk.ru/feed, data-testid="rightmenu",
// aria-label «Список ленты» — лента.снапшоты.парсинг.полный.md §1.0.2).
//
// Прежняя версия (19-A) повторяла НЕ то, что в VK web: «Друзья онлайн» /
// «Возможные друзья» / «Рекомендуемые сообщества» / «Мои закладки».
// По прямому требованию юзера («Зачем мне там закладки и прочая ерунда?»)
// и по снапшоту меню — это НАВИГАЦИЯ РАЗДЕЛОВ ленты:
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
// Выбор раздела НЕ навигирует на другой экран — переключает раздел ТЕКУЩЕЙ
// ленты (feedFilterName + reloadFeed на стороне FeedScreen), как в VK web.
// «Рекомендации» в меню НЕТ — как в VK web (она осталась в заголовочном
// dropdown FeedFilterBar: таб «Все новости / Рекомендации» sticky-хедера).
//
// УДАЛЕНО по требованию юзера (в VK web rightmenu отсутствует): «Друзья
// онлайн» (friendsGetOnline), «Возможные друзья» (friendsGetSuggestions),
// «Рекомендуемые сообщества» (groupsGetCatalog), «Мои закладки» (faveGet).
// Раздел «Друзья» = лента постов ДРУЗЕЙ (§1.2 снапшота), а не список
// «возможных друзей»; блок рекомендаций над лентой друзей также убран из
// FeedScreen (#FEED-MENU-VKWEB). VKA-методы не тронуты (API-поверхность).
// ═══════════════════════════════════════════════════════════════════════

/**
 * Один пункт меню: имя раздела (значение [re.pinok.ui.screens.feed.FeedFilter].name,
 * передаётся строкой — enum приватен в FeedScreen), человекочитаемый заголовок,
 * иконка и data-testid VK web (для трассировки в KDoc-таблице выше).
 */
private data class FeedMenuEntry(
    val vkTestId: String,
    val filterName: String,
    val label: String,
    val icon: ImageVector,
)

// Порядок и подписи — 1:1 с VK web rightmenu (§1.0.2 снапшота).
private val FEED_MENU_ENTRIES = listOf(
    FeedMenuEntry("feed_right_menu_news", "ALL", "Лента", Icons.Outlined.Newspaper),
    FeedMenuEntry("feed_right_menu_sources_photos", "PHOTOS", "Фотографии", Icons.Outlined.PhotoLibrary),
    FeedMenuEntry("feed_right_menu_sources_friends", "FRIENDS", "Друзья", Icons.Outlined.Group),
    FeedMenuEntry("feed_right_menu_global_search", "SEARCH", "Поиск", Icons.Outlined.Search),
    FeedMenuEntry("feed_right_menu_wall_likes", "LIKES", "Реакции", Icons.Outlined.FavoriteBorder),
)

/**
 * #FEED-MENU-VKWEB: правое боковое меню ленты (оверлей поверх контента
 * FeedScreen: Scrim + панель справа). Всегда в композиции (visible-флаг) →
 * AnimatedVisibility проигрывает и вход, и выход.
 *
 * @param visible          открыта ли панель (state FeedScreen.showRightPanel)
 * @param currentFilterName FeedFilter.name активного раздела ленты — на нём
 *                         рисуется чекмарк (в VK web активный пункт подсвечен)
 * @param onDismiss        закрыть панель (тап по Scrim / «×» / системный back)
 * @param onSectionSelected(feedFilterName) выбор раздела — FeedScreen закрывает
 *                         панель, выставляет feedFilterName и перезагружает ленту
 * @param onOpenHiddenSources «Редактировать» → «Скрытые источники»
 *                         (Screen.FeedHidden, newsfeed.getBanned + unban)
 */
@Composable
fun FeedRightPanel(
    visible: Boolean,
    currentFilterName: String,
    onDismiss: () -> Unit,
    onSectionSelected: (String) -> Unit,
    onOpenHiddenSources: () -> Unit,
) {
    // Системный back закрывает панель, а не покидает экран (прецедент 19-A).
    BackHandler(enabled = visible) { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Оверлей: Scrim + панель. Дети Box: сначала Scrim, потом панель —
        // панель выше по z, клики по ней не доходят до Scrim. ──
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
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
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
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

                    // Разделы ленты (5 пунктов VK web). Выбор переключает раздел
                    // текущей ленты (см. KDoc класса) — навигации на другие экраны нет.
                    FEED_MENU_ENTRIES.forEach { entry ->
                        val selected = entry.filterName == currentFilterName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    AppLog.i("FeedRightPanel", "section select: ${entry.vkTestId} -> ${entry.filterName}")
                                    onSectionSelected(entry.filterName)
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontSize = 16.sp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
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
