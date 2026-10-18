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
 * [Route] — навигация на экран.
 */
sealed class PanelAction {
    data class Route(val screen: Screen) : PanelAction()
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
        // PANEL-EDIT-FEED: «Лента» как полноправный пункт канона (был только в dockScreens/
        // mainRoutes для маршрутизации, но отсутствовал в PanelItems.all → не появлялся в
        // «Редакторе панелей»). Добавлен первым, как главный экран (Screen.Feed, route "feed").
        // По умолчанию скрыт (см. BOTTOMBAR_DEFAULT_HIDDEN/SIDEBAR), поэтому дефолтная панель
        // не меняется — пользователь сам добавляет его в редакторе при желании.
        PanelItem("feed", "Лента", Screen.Feed.icon, PanelAction.Route(Screen.Feed)),
        PanelItem("notifications", "Уведомления", Screen.Notifications.icon, PanelAction.Route(Screen.Notifications)),
        PanelItem("messenger", "Мессенджер", Icons.AutoMirrored.Filled.Chat, PanelAction.Route(Screen.Messages)),
        PanelItem("groups", "Сообщества", Screen.Groups.icon, PanelAction.Route(Screen.Groups)),
        PanelItem("photos", "Фотографии", Screen.Photos.icon, PanelAction.Route(Screen.Photos)),
        PanelItem("friends", "Друзья", Screen.Friends.icon, PanelAction.Route(Screen.Friends)),
        PanelItem("video", "Видео", Screen.Video.icon, PanelAction.Route(Screen.Video)),
        // POSTCARD-REFACTOR-PLAN / #PANELEDIT-FIX: «Профиль» выпал из панелей при
        // переходе на динамический редактор (PanelItems.all не содержал пункта —
        // нижняя/боковая панели строились из него, а Screen.Profile остался только
        // в dockScreens/mainRoutes для маршрутизации). Возвращаем полноправным
        // пунктом канона: иконка Screen.Profile.icon (Icons.Default.Person).
        PanelItem("profile", "Профиль", Screen.Profile.icon, PanelAction.Route(Screen.Profile)),
        PanelItem("clips", "Клипы", Screen.Clips.icon, PanelAction.Route(Screen.Clips)),
        PanelItem("music", "Музыка", Screen.Music.icon, PanelAction.Route(Screen.Music)),
        // P0.32: Музыка 2 — новый контейнер (pull-to-refresh, визуализатор, 5 вкладок).
        PanelItem("music2", "Музыка 2", Screen.Music2.icon, PanelAction.Route(Screen.Music2)),
        PanelItem("bookmarks", "Закладки", Screen.Bookmarks.icon, PanelAction.Route(Screen.Bookmarks)),
        PanelItem("files", "Файлы", Screen.Documents.icon, PanelAction.Route(Screen.Documents)),
        PanelItem("search", "Поиск", Screen.Search.icon, PanelAction.Route(Screen.Search)),
        PanelItem("reactions", "Реакции", Icons.Filled.ThumbUp, PanelAction.Route(Screen.FeedLikes)),
        PanelItem("services", "Сервисы", Screen.Services.icon, PanelAction.Route(Screen.Services)),
        PanelItem("logs", "Логи", Screen.Logs.icon, PanelAction.Route(Screen.Logs)),
    )

    /** По key → PanelItem (для маппинга из SovaPrefs). */
    val byKey: Map<String, PanelItem> = all.associateBy { it.key }

    /** Те же keys в каноническом порядке. */
    val allKeys: List<String> = all.map { it.key }
}