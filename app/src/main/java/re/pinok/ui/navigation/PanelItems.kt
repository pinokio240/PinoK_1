package re.pinok.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector

// ══════════════════════════════════════════════════════════════════════
//  Fix #PANELEDIT: «Редактор панелей» — единый канонический набор пунктов
//  боковой и нижней панелей по требованию пользователя.
//
//  Список (порядок = канонический):
//    Уведомления, Мессенджер, Сообщества, Фотографии, Друзья, Видео, Клипы,
//    Музыка, Закладки, Файлы, Поиск, Реакции, Сервисы, Логи.
//
//  Главное отличие от Screen.route-подхода (который не мог выразить):
//    - дубли одного экрана («Мессенджер» → Screen.Messages);
//    - «Сообщества» (Screen.Groups) рядом с «Мессенджером»;
//    - специальные действия («Реакции» → Лента с фильтром LIKES).
//  Поэтому каждый пункт имеет СТАБИЛЬНЫЙ key (не Screen.route), который и
//  хранится в SovaPrefs (sidebar_items_order/hidden, bottombar_items_order/hidden).
// ══════════════════════════════════════════════════════════════════════

/**
 * Что делает пункт панели при тапе.
 * [Route] — обычная навигация на top-level экран.
 * [FeedFilter] — специальный пункт «Реакции»: открыть Ленту на разделе LIKES.
 */
sealed class PanelAction {
    data class Route(val screen: Screen) : PanelAction()

    /**
     * Открыть Ленту (Screen.Feed) и переключить её на указанный раздел.
     * [filterName] — это FeedFilter.name ("LIKES"), задаётся строкой, т.к.
     * enum FeedFilter приватный (в FeedScreen.kt) и не доступен из навигации.
     */
    data class FeedFilter(val filterName: String) : PanelAction()
}

/**
 * Один редактируемый пункт панели навигации.
 * @param key стабильный идентификатор для SovaPrefs (не Screen.route —
 *            допускает дубли/соседние экраны и спец-действие «реакции»).
 * @param title подпись в панели/редакторе.
 * @param icon иконка (null → пункт рисуется без иконки).
 * @param action поведение при тапе.
 */
data class PanelItem(
    val key: String,
    val title: String,
    val icon: ImageVector?,
    val action: PanelAction,
) {
    /** Конечный destination-route для подсветки выбранного состояния. */
    val destinationRoute: String
        get() = when (val a = action) {
            is PanelAction.Route -> a.screen.route
            is PanelAction.FeedFilter -> Screen.Feed.route
        }
}

/**
 * Канонические списки пунктов панелей. Порядок в этих списках = канонический
 * (для initial/сброса). Оба списка читаются динамически из SovaPrefs
 * (задом `left_panel_items` / `bottom_panel_items` через order+hidden JSON).
 *
 * Боковая и нижняя панели используют ОДИН набор пунктов (требование
 * пользователя: нижняя панель показывает «тот же список»).
 */
object PanelItems {

    /** Канонический порядок + полный набор пунктов панелей. */
    val all: List<PanelItem> = listOf(
        PanelItem("notifications", "Уведомления", Screen.Notifications.icon, PanelAction.Route(Screen.Notifications)),
        PanelItem("messenger", "Мессенджер", Icons.AutoMirrored.Filled.Chat, PanelAction.Route(Screen.Messages)),
        PanelItem("groups", "Сообщества", Screen.Groups.icon, PanelAction.Route(Screen.Groups)),
        PanelItem("photos", "Фотографии", Screen.Photos.icon, PanelAction.Route(Screen.Photos)),
        PanelItem("friends", "Друзья", Screen.Friends.icon, PanelAction.Route(Screen.Friends)),
        PanelItem("video", "Видео", Screen.Video.icon, PanelAction.Route(Screen.Video)),
        PanelItem("clips", "Клипы", Screen.Clips.icon, PanelAction.Route(Screen.Clips)),
        PanelItem("music", "Музыка", Screen.Music.icon, PanelAction.Route(Screen.Music)),
        PanelItem("bookmarks", "Закладки", Screen.Bookmarks.icon, PanelAction.Route(Screen.Bookmarks)),
        PanelItem("files", "Файлы", Screen.Documents.icon, PanelAction.Route(Screen.Documents)),
        PanelItem("search", "Поиск", Screen.Search.icon, PanelAction.Route(Screen.Search)),
        PanelItem("reactions", "Реакции", Icons.Filled.ThumbUp, PanelAction.FeedFilter("LIKES")),
        PanelItem("services", "Сервисы", Screen.Services.icon, PanelAction.Route(Screen.Services)),
        PanelItem("logs", "Логи", Screen.Logs.icon, PanelAction.Route(Screen.Logs)),
    )

    /** По key → PanelItem (для маппинга из SovaPrefs). */
    val byKey: Map<String, PanelItem> = all.associateBy { it.key }

    /** Те же keys в каноническом порядке. */
    val allKeys: List<String> = all.map { it.key }
}

/**
 * #PANELEDIT: запрос на открытие Ленты в конкретном разделе (для спец-пункта
 * «Реакции» → FeedFilter.LIKES). Маршрут Ленты не параметризован (Screen.Feed
 * "feed" — main-роут, параметризация ломает lastRoute/currentRoute restore,
 * см. #FEED-MENU-NOTIF в SovaNavHost), поэтому seed передаётся через in-memory
 * holder (паттерн PostHolder/StoryHolder этого же пакета).
 *
 * FeedScreen читает желаемый раздел в LaunchedEffect(FeedOpenRequest.version):
 *  - на первой композиции применяет как initial filterName;
 *  - если Лента уже на экране — version++ заставляет перечитать (второй тап
 *    по «Реакции» переключает прямо на месте, без пересоздания screen).
 */
object FeedOpenRequest {
    /** Желаемое имя раздела ленты (FeedFilter.name) или null = без запроса. */
    @Volatile private var filterName: String? = null

    /** Монотонно растёт при каждом запросе — ключ LaunchedEffect-перечитывания. */
    @Volatile var version: Int = 0
        private set

    /** Запросить открытие Ленты на разделе [filter]. */
    fun request(filter: String) {
        filterName = filter
        version++
    }

    /** Забрать и сбросить текущий запрос (вызывается FeedScreen). */
    fun consume(): String? {
        val f = filterName
        filterName = null
        return f
    }
}