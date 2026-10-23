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
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Recommend
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.ui.anim.LocalAnimScale
import re.pinok.ui.anim.tweenScaled
import re.pinok.ui.screens.feed.FeedFilter
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════
// FEED-MENU-RESTORE (2026-10-23): меню ленты ВОЗВРАЩЕНО к фильтрам ленты
// (Лента/Рекомендации/Фото/Друзья/Поиск/Реакции). Категории уведомлений
// (#FEED-MENU-NOTIF-7) УБРАНЫ — уведомления доступны через отдельный экран
// Notifications (drawer пункт + badge).
//
// Клик по фильтру → onSelectFilter(FeedFilter) → FeedScreen меняет feedFilterName
// + reloadFeed(). «Редактировать» → Screen.FeedHidden (Скрытые источники).
//
// История:
// - #FEED-MENU-NOTIF-7 (2026-10-02): меню урезано до 7 категорий уведомлений
// - #FEED-MENU-VKWEB (Fix #353): меню = «Список ленты» VK web (rightmenu)
// - 19-A: Друзья онлайн / Возможные друзья / Рекомендуемые сообщества / Закладки
// ═══════════════════════════════════════════════════════════════════════

/**
 * Один пункт меню: человекочитаемый заголовок, иконка, data-testid VK web
 * (для трассировки) и [filter] — фильтр ленты (FeedFilter).
 */
private data class FeedMenuEntry(
    val vkTestId: String,
    val label: String,
    val icon: ImageVector,
    val filter: FeedFilter,
)

/** Пункты меню ленты — фильтры (1:1 с VK web rightmenu). */
private val FEED_MENU_ENTRIES = listOf(
    FeedMenuEntry("feed_right_menu_news", "Все новости", Icons.Outlined.Article, FeedFilter.ALL),
    FeedMenuEntry("feed_right_menu_recommended", "Рекомендации", Icons.Outlined.Recommend, FeedFilter.RECOMMENDED),
    FeedMenuEntry("feed_right_menu_sources_photos", "Фото", Icons.Outlined.Image, FeedFilter.PHOTOS),
    FeedMenuEntry("feed_right_menu_sources_friends", "Друзья", Icons.Outlined.Group, FeedFilter.FRIENDS),
    FeedMenuEntry("feed_right_menu_global_search", "Поиск", Icons.Outlined.Search, FeedFilter.SEARCH),
    FeedMenuEntry("feed_right_menu_wall_likes", "Реакции", Icons.Outlined.ThumbUp, FeedFilter.LIKES),
)

/**
 * FEED-MENU-RESTORE: правое боковое меню ленты (оверлей поверх контента
 * FeedScreen: Scrim + панель справа). Всегда в композиции (visible-флаг) →
 * AnimatedVisibility проигрывает и вход, и выход.
 *
 * Меню содержит фильтры ленты (Лента/Рекомендации/Фото/Друзья/Поиск/Реакции).
 * Клик по фильтру → [onSelectFilter] → FeedScreen меняет feedFilterName + reloadFeed.
 *
 * @param visible          открыта ли панель (state FeedPanelState.visible)
 * @param currentFilter    текущий активный фильтр (для подсветки/галочки)
 * @param onSelectFilter   выбор фильтра → переключение ленты
 * @param onDismiss        закрыть панель (тап по Scrim / «×» / системный back)
 * @param onOpenHiddenSources «Редактировать» → «Скрытые источники»
 *                         (Screen.FeedHidden, newsfeed.getBanned + unban)
 */
@Composable
fun FeedRightPanel(
    visible: Boolean,
    currentFilter: FeedFilter,
    onSelectFilter: (FeedFilter) -> Unit,
    onDismiss: () -> Unit,
    onOpenHiddenSources: () -> Unit,
) {
    // Системный back закрывает панель, а не покидает экран (прецедент 19-A).
    BackHandler(enabled = visible) { onDismiss() }

    // Fix #224: скорость анимаций — панель и scrim масштабируются настройкой
    // «Скорость анимации» (LocalAnimScale). 0 → мгновенно (snap).
    val animScale = LocalAnimScale.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Оверлей: Scrim + панель. ──
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tweenScaled(animScale, 300)),
            exit = fadeOut(tweenScaled(animScale, 300)),
        ) {
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
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            ) {
                Column {
                    // Заголовок + закрытие «×»
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // FEED-MENU-RESTORE: фильтры ленты (Лента/Рекомендации/Фото/Друзья/Поиск/Реакции)
                        FEED_MENU_ENTRIES.forEach { entry ->
                            val isCurrent = entry.filter == currentFilter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable {
                                        AppLog.i("FeedRightPanel", "filter: ${entry.vkTestId} -> ${entry.filter.name}")
                                        onSelectFilter(entry.filter)
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    entry.icon,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 16.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) FontWeight.Medium
                                    else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                // Галочка для активного фильтра
                                if (isCurrent) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // «Редактировать» → «Скрытые источники» (Screen.FeedHidden)
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
