package re.pinok.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import re.pinok.ui.anim.LocalAnimScale
import re.pinok.ui.anim.tweenScaled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.contracts.CallStarter
import re.pinok.contracts.ContainerRegistry
import re.pinok.contracts.NavEntry
import re.pinok.data.model.Video
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.screens.bookmarks.BookmarksScreen
import re.pinok.ui.screens.community.BoardTopicScreen
import re.pinok.ui.screens.community.CommunityScreen
import re.pinok.ui.screens.documents.DocumentsScreen
import re.pinok.ui.screens.feed.FeedScreen
import re.pinok.ui.screens.feed.PostDetailScreen
import re.pinok.ui.screens.feed.StoryViewerScreen
import re.pinok.ui.screens.friends.FriendsScreen
import re.pinok.ui.screens.groups.GroupsScreen
import re.pinok.ui.screens.im.ChatDetailScreen
import re.pinok.ui.screens.im.ChatInfoScreen
import re.pinok.ui.screens.im.FoldersSettingsScreen
import re.pinok.ui.screens.im.MessagesScreen
import re.pinok.ui.screens.browser.InternalBrowserScreen
import re.pinok.ui.screens.music.MusicScreen
import re.pinok.ui.screens.music.AudioPlayerScreen
import re.pinok.ui.screens.music.AudioQueueScreen
import re.pinok.ui.screens.music.EqualizerScreen
import re.pinok.ui.screens.music.MusicPlaylistsScreen
import re.pinok.ui.screens.music.PlaylistDetailScreen
import re.pinok.ui.screens.music.MusicAlbumsScreen
import re.pinok.ui.screens.music.MusicArtistsScreen
import re.pinok.ui.screens.music.ArtistDetailScreen
import re.pinok.ui.screens.music.CatalogSectionScreen
import re.pinok.ui.screens.notifications.NotificationsScreen
import re.pinok.ui.screens.notifications.NotificationSettingsScreen
import re.pinok.ui.screens.offline.ClipOfflinePlayerScreen
import re.pinok.ui.screens.offline.OfflineAudioPlayerScreen
import re.pinok.ui.screens.offline.OfflineManagerScreen
import re.pinok.ui.screens.offline.StoryOfflinePlayerScreen
import re.pinok.ui.screens.photos.PhotosScreen
import re.pinok.ui.screens.profile.ProfileScreen
import re.pinok.ui.screens.profile.UserProfileScreen
import re.pinok.ui.screens.search.SearchScreen
import re.pinok.ui.screens.settings.AboutScreen
import re.pinok.ui.screens.settings.LogScreen
import re.pinok.ui.screens.settings.SettingsScreen
import re.pinok.ui.screens.superapp.ServicesScreen
import re.pinok.ui.screens.video.VideoScreen
import re.pinok.ui.screens.videoplayer.VideoPlatformRouter
import re.pinok.ui.components.CaptchaDialog
import re.pinok.ui.components.NetworkSwitchPopup
import re.pinok.ui.components.OfflineBanner
import re.pinok.ui.components.GlobalMiniPlayer
import re.pinok.util.AppLog

// ── Fix #337: редактор панелей — JSON-парсинг порядка/скрытых пунктов ──
// Хранится в SovaPrefs как JSON-массив route-строк. Здесь — лёгкий парсер
// без зависимостей (org.json.JSONArray доступен в Android SDK).
private fun parseRoutesJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Нормализует order под canonical-список: убирает неизвестные route,
 * добавляет недостающие (новые пункты после обновления) в конец canonical.
 * Гарантирует, что в результате есть ВСЕ пункты из [canonical] ровно по разу.
 */
private fun normalizeRouteOrder(order: List<String>, canonical: List<Screen>): List<Screen> {
    val byRoute = canonical.associateBy { it.route }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<Screen>()
    for (r in order) {
        val scr = byRoute[r]
        if (scr != null && r !in seen) {
            result.add(scr)
            seen.add(r)
        }
    }
    for (scr in canonical) {
        if (scr.route !in seen) {
            result.add(scr)
            seen.add(scr.route)
        }
    }
    return result
}

// ═══ #ARCH-CONTAINERS (Этап 1.4): host-маппинги контейнерных capability ═══

/**
 * route-строка NavEntry → destination хоста. Контейнер не знает Screen-типов
 * (:contracts без androidx/compose) — маппинг держит хост. Неизвестный route →
 * null: пункт не рендерится (graceful), хост пишет предупреждение в лог.
 * Новые контейнеры (1.5+) добавляют сюда одну строку — либо регистрируют
 * NavHost-destination и расширяют маппинг вместе с ним.
 */
private fun hostDestinationForRoute(route: String): Screen? = when (route) {
    Screen.CallsHistory.route -> Screen.CallsHistory // "calls_history" — destination уже в NavHost ниже
    // #ARCH-CONTAINERS (Этап 1.5-а): «Фото» — контейнер :feature:photos;
    // destination (Screen.Photos → PhotosScreen) остаётся в NavHost хоста.
    Screen.Photos.route -> Screen.Photos // "photos"
    else -> null
}

/**
 * iconKey → реальная иконка (контракты без compose, иконку мапит хост —
 * см. KDoc NavEntry). "calls" → та же иконка, что была у ядерного пункта
 * «Звонки» (Icons.Filled.Call). Неизвестный ключ → нейтральная иконка
 * (расширение) — НЕ падаем.
 */
private fun hostIconForKey(iconKey: String): ImageVector = when (iconKey) {
    "calls" -> Icons.Filled.Call
    // #ARCH-CONTAINERS (Этап 1.5-а): та же иконка, что была у ядерного пункта
    // «Фото» (Screen.Photos: Icons.Outlined.Image).
    "photos" -> Icons.Outlined.Image
    else -> Icons.Outlined.Extension
}

/**
 * #ARCH-CONTAINERS (Этап 1.4): мета исходящего звонка — имя/фото собеседника
 * из места вызова (шапка диалога, карточка друга). Контракт CallStarter
 * (startCall(peerId, video)) title/photo не передаёт — их знает только
 * host-сайт вызова. Порядок: onClick-сайт кладёт мета ЗДЕСЬ (синхронно, до
 * startCall → pendingOutgoingCallPeerId), LaunchedEffect(pendingOutgoingCall)
 * ниже читает при навигации на Screen.Call и очищает. Пусто (звонок не из UI,
 * напр. redial из истории) → CallScreen подтянет профиль сам (usersGetByIds).
 */
private object OutgoingCallMeta {
    @Volatile var title: String = ""
    @Volatile var photo: String? = null

    /** Заложить мета из host-сайта кнопки звонка (перед startCall). */
    fun stash(t: String, p: String?) {
        title = t
        photo = p
    }

    /** Забрать и очистить (вызывает LaunchedEffect при навигации на экран звонка). */
    fun consume(): Pair<String, String?> {
        val result = title to photo
        title = ""
        photo = null
        return result
    }
}

/**
 * #BOTTOM-SCROLL: кастомная кнопка для скроллируемой нижней панели (>5
 * кнопок). НЕ использует [NavigationBarItem] — т.к. в M3 BOM 2025.06
 * NavigationBarItem корректно резолвится только внутри [NavigationBar]
 * content-scope. Для скролл-Row делаем свою кнопку: Column(Icon+Text) с
 * selected-state (цвет) и unread-badge для Сообщений.
 */
@Composable
private fun BottomNavScrollButton(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navIcon = screen.icon
    val unreadCount = if (screen.route == Screen.Messages.route) {
        re.pinok.realtime.UnreadMessagesCounter.unreadCount.collectAsState().value
    } else {
        0
    }
    val tint = if (selected) MaterialTheme.colorScheme.onSurface
               else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (navIcon != null) {
            if (screen.route == Screen.Messages.route && unreadCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                    },
                ) {
                    Icon(navIcon, contentDescription = screen.title, tint = tint)
                }
            } else {
                Icon(navIcon, contentDescription = screen.title, tint = tint)
            }
        }
        Text(
            text = screen.title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SovaNavHost(
    nav: NavHostController = rememberNavController(),
    onLogout: () -> Unit,
    onExitApp: () -> Unit = {},
    initialRoute: String = Screen.Feed.route,
    pendingOpenChatPeerId: Long? = null,
    pendingOpenChatTitle: String? = null,
    onOpenChatConsumed: () -> Unit = {},
    pendingDeepLink: re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val app = remember { SovaApp.get(nav.context) }

    // #ARCH-CONTAINERS (Этап 1.4): хост строит панель/кнопки звонка из реестра
    // контейнеров. Реестр не реактивен — UI перечитывает его при следующем
    // построении composition (контракт ContainerRegistry): сняли контейнер →
    // пункты/кнопки исчезли, ядро живо (graceful-деградация).
    //  - containerNavEntries — пункты боковой панели (после ядерных, по order);
    //  - callStarter — запуск звонка; null → кнопки звонка НЕ рендерятся.
    //  - callClick — готовый колбэк для ядерных экранов (лента/друзья/чат):
    //    кладёт title/photo в OutgoingCallMeta (контракт CallStarter их не
    //    передаёт) и стартует звонок. Явный тип → лямбда приводится к Unit.
    val containerNavEntries = remember {
        ContainerRegistry.find<NavEntry>().sortedBy { it.order }
    }
    val callStarter = remember { ContainerRegistry.find<CallStarter>().firstOrNull() }
    val callClick: ((peerId: Long, title: String, photo: String?) -> Unit)? =
        callStarter?.let { starter ->
            { pid: Long, title: String, photo: String? ->
                OutgoingCallMeta.stash(title, photo)
                starter.startCall(pid, video = false)
            }
        }

    // Fix #208: навигация на ChatDetailScreen когда пользователь тапнул по
    // push-уведомлению. MainActivity.handleOpenChatIntent устанавливает
    // pendingOpenChatPeerId, здесь мы подхватываем и навигируем. После
    // навигации сбрасываем state через onOpenChatConsumed (иначе при каждомом
    // recompose мы бы пытались навигировать снова).
    LaunchedEffect(pendingOpenChatPeerId) {
        val peerId = pendingOpenChatPeerId
        if (peerId != null && peerId > 0) {
            val title = pendingOpenChatTitle ?: ""
            AppLog.i("SovaNavHost", "OPEN_CHAT: navigating to chat peerId=$peerId title='$title'")
            nav.navigate(Screen.ChatDetail.buildRoute(peerId, title, null)) {
                // Не добавляем дубликаты если уже в этом чате.
                launchSingleTop = true
            }
            onOpenChatConsumed()
        }
    }

    // #CALLS: навигация на CallScreen при входящем звонке (тап по уведомлению).
    // SovaApp.startCallNotifier сохраняет pendingIncomingCallPayload; здесь
    // подхватываем и открываем CallScreen(incoming=true, payload).
    LaunchedEffect(app.pendingIncomingCallPayload) {
        val payload = app.pendingIncomingCallPayload
        if (!payload.isNullOrBlank()) {
            val peerId = app.pendingIncomingCallPeerId
            val title = app.pendingIncomingCallTitle.ifBlank { "Входящий звонок" }
            val photo = app.pendingIncomingCallPhoto
            AppLog.i("SovaNavHost", "INCOMING_CALL: navigating to CallScreen payload.len=${payload.length}")
            nav.navigate(Screen.Call.buildRoute(peerId, title, photo, incoming = true, payload = payload)) {
                launchSingleTop = true
            }
            app.consumeIncomingCall()
        }
    }

    // #ARCH-CONTAINERS: исходящий звонок через контейнер :feature:calls
    // (CallStarter.startCall → pendingOutgoingCall*; тот же паттерн, что
    // incoming выше). title/photo берём из OutgoingCallMeta — их заложил
    // host-сайт кнопки (контракт CallStarter их не передаёт); пусто →
    // CallScreen подтянет профиль сам (usersGetByIds).
    LaunchedEffect(app.pendingOutgoingCallPeerId) {
        val peerId = app.pendingOutgoingCallPeerId
        if (peerId > 0L) {
            val (metaTitle, metaPhoto) = OutgoingCallMeta.consume()
            AppLog.i("SovaNavHost", "OUTGOING_CALL: navigating to CallScreen peerId=$peerId (CallStarter)")
            nav.navigate(Screen.Call.buildRoute(peerId, metaTitle, metaPhoto, incoming = false)) {
                launchSingleTop = true
            }
            app.consumeOutgoingCall()
        }
    }

    // §42 #PUSH-NOTIFICATIONS: навигация при тапе на VK-уведомление
    // (лайк/комментарий/репост/ответ/подписка/упоминание/подарок/запись на стене).
    // MainActivity.handleDeepLinkIntent устанавливает pendingDeepLink,
    // здесь подхватываем и навигируем на нужный Screen. После навигации
    // сбрасываем state через onDeepLinkConsumed.
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        try {
            when (link) {
                is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenPost -> {
                    // §42.4 #PUSH-DEEPLINK: если есть commentId — пробрасываем его
                    // в PostDetailScreen через holder, чтобы экран проскроллил к
                    // комментарию после загрузки (ответ на комментарий / новый
                    // комментарий на посте).
                    if (link.commentId != 0L) {
                        PostDetailTarget.commentId = link.commentId
                    }
                    AppLog.i("SovaNavHost", "DEEP_LINK: navigating to PostDetail owner=${link.ownerId} post=${link.postId} commentId=${link.commentId}")
                    nav.navigate(Screen.PostDetail.buildRoute(link.ownerId, link.postId)) {
                        launchSingleTop = true
                    }
                }
                is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenVideo -> {
                    // §42.4 #PUSH-DEEPLINK: VideoPlayer удалён из NavHost (#90,
                    // теперь overlay). Раньше nav.navigate(Screen.VideoPlayer...)
                    // вело на несуществующий маршрут → тап по видео-уведомлению
                    // ничего не открывал. Теперь открываем overlay-плеер через
                    // VideoHolder.open(Video(...)) — VideoPlatformRouter сам
                    // подтянет CDN URL и метаданные по owner_id+video_id.
                    AppLog.i("SovaNavHost", "DEEP_LINK: opening VideoPlayer overlay owner=${link.ownerId} video=${link.videoId}")
                    VideoHolder.open(Video(
                        id = link.videoId,
                        ownerId = link.ownerId,
                        title = "",
                        description = null,
                        duration = 0,
                        date = 0,
                    ))
                }
                is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenUser -> {
                    AppLog.i("SovaNavHost", "DEEP_LINK: navigating to UserProfile user=${link.userId}")
                    nav.navigate(Screen.UserProfile.buildRoute(link.userId)) {
                        launchSingleTop = true
                    }
                }
                is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenCommunity -> {
                    AppLog.i("SovaNavHost", "DEEP_LINK: navigating to Community group=${link.groupId}")
                    nav.navigate(Screen.Community.buildRoute(link.groupId)) {
                        launchSingleTop = true
                    }
                }
                is re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenPhoto -> {
                    // §42.4 #PUSH-DEEPLINK: открываем нативный PhotoViewer (pinch-zoom,
                    // swipe) через PhotoHolder overlay — как в ленте/профиле/чате.
                    // Раньше открывалось в InternalBrowser (WebView, vk.com/photo) —
                    // медленно и не нативно. Если photoUrl пуст (редкий случай —
                    // уведомление без превью) — fallback на InternalBrowser.
                    val url = link.photoUrl
                    if (!url.isNullOrBlank()) {
                        AppLog.i("SovaNavHost", "DEEP_LINK: opening PhotoViewer overlay owner=${link.ownerId} photo=${link.photoId}")
                        PhotoHolder.open(listOf(url), 0)
                    } else {
                        val webUrl = re.pinok.realtime.VkUrlDeepLinker.webUrlFor(link)
                        AppLog.i("SovaNavHost", "DEEP_LINK: no photoUrl, fallback to InternalBrowser url=$webUrl")
                        nav.navigate(Screen.InternalBrowser.buildRoute(webUrl)) {
                            launchSingleTop = true
                        }
                    }
                }
                re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenNotifications -> {
                    // Открываем вкладку Уведомления. В текущей навигации это
                    // NotificationsScreen — проверим, есть ли route.
                    AppLog.i("SovaNavHost", "DEEP_LINK: navigating to Notifications")
                    nav.navigate("notifications") {
                        launchSingleTop = true
                    }
                }
                re.pinok.realtime.VkUrlDeepLinker.DeepLinkAction.OpenDevices -> {
                    // §49.6 Sprint VK-ID-1.6: deep-link из security-alert notification.
                    AppLog.i("SovaNavHost", "DEEP_LINK: navigating to Devices")
                    nav.navigate("devices") {
                        launchSingleTop = true
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w("SovaNavHost", "DEEP_LINK navigation failed: ${e.message}")
        }
        onDeepLinkConsumed()
    }

    // Fix #132: Восстановление chat_detail после process death во время камеры.
    //
    // Симптом: пользователь в чате → тап на камеру → система убивает процесс
    // (low memory) → пользователь делает фото и возвращается → приложение
    // холодно стартует → NavController ненадёжно восстанавливает back stack
    // (chat_detail на мгновение compostится, фото отправляется через
    // rememberSaveable UriSaver, но затем currentRoute сбрасывается на Feed
    // из-за NavHost(startDestination = snap.lastRoute = "feed")). Пользователь
    // оказывается на ленте, фото при этом уходит.
    //
    // Фикс: перед запуском камеры явно сохраняем peerId/title/photo чата в
    // rememberSaveable. При первом composition SovaNavHost, если там что-то
    // есть и мы не на chat_detail — навигируемся обратно. После навигации
    // очищаем, чтобы следующий запуск приложения не возвращал в старый чат.
    var cameraReturnPeerId by rememberSaveable { mutableLongStateOf(0L) }
    var cameraReturnTitle by rememberSaveable { mutableStateOf("") }
    var cameraReturnPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (cameraReturnPeerId != 0L) {
            val current = nav.currentDestination?.route
            val alreadyOnChat = current != null && current.startsWith("chat_detail")
            AppLog.i("SovaNavHost",
                "camera return: peer=${cameraReturnPeerId} currentRoute=$current alreadyOnChat=$alreadyOnChat")
            // Очищаем saved state в любом случае — либо навигируемся, либо уже там.
            val pid = cameraReturnPeerId
            val title = cameraReturnTitle
            val photo = cameraReturnPhoto
            cameraReturnPeerId = 0L
            cameraReturnTitle = ""
            cameraReturnPhoto = null
            if (!alreadyOnChat) {
                val route = Screen.ChatDetail.buildRoute(pid, title, photo)
                try {
                    nav.navigate(route)
                } catch (e: Exception) {
                    AppLog.w("SovaNavHost", "camera return navigate failed: ${e.message}")
                }
            }
        }
    }

    // #90: Overlay для VideoPlayer — видео открывается поверх текущего экрана,
    // без nav.navigate(). Feed (и любой другой экран) остаётся живым в
    // Composition → LazyListState не теряется → позиция скролла сохраняется.
    val overlayVideo by VideoHolder.active.collectAsState()
    // §42.4 #PUSH-DEEPLINK: overlay для PhotoViewer — фото из push-уведомления
    // (like_photo / comment_photo) открывается нативно поверх текущего экрана,
    // как в ленте/профиле/чате. PhotoViewer — Dialog с pinch-zoom и swipe.
    val overlayPhoto by PhotoHolder.active.collectAsState()

    // Fix #224: глобальный масштаб скорости анимаций (0f..1f).
    // 0f → анимации выключены (snap/мгновенно), 1f — норма. Берётся из prefs
    // (interfaceAnimSpeed 0..100 → /100f). Предоставляется через LocalAnimScale
    // всем экранам внутри NavHost + используется в transition-ламбдах NavHost.
    val prefsSnap by app.prefs.data.collectAsState(initial = null)
    val animScale = (prefsSnap?.interfaceAnimSpeed ?: 100).coerceIn(0, 100) / 100f

    // Основные экраны (dock + drawer) — только их сохраняем как lastRoute.
    // Детальные экраны (VideoPlayer, PostDetail, ChatDetail и т.д.) НЕ сохраняем —
    // они контекстные и зависят от переданных данных.
    //
    // Fix #114: Screen.Logs убран из mainRoutes. Logs — утилитный экран
    // отладки, не «домашний». Если сохранять его как lastRoute, при
    // перезапуске приложения Logs становится startDestination NavHost →
    // nav.popBackStack() из Logs возвращает false → кнопка «закрыть»
    // не работала. Теперь lastRoute всегда остаётся «настоящим» экраном.
    val mainRoutes = remember {
        listOf(
            Screen.Feed.route, Screen.Messages.route, Screen.Music.route,
            Screen.Video.route, Screen.Profile.route, Screen.Friends.route,
            Screen.Groups.route, Screen.Photos.route, Screen.Search.route,
            Screen.Bookmarks.route, Screen.Documents.route, Screen.Services.route,
            Screen.Notifications.route, Screen.Settings.route, Screen.Clips.route,
        )
    }
    // Fix #226: множество роутов основных разделов для O(1)-проверки в
    // transition-ламбдах NavHost. Используется чтобы отличить переключение
    // таб-в-таб (мгновенно, без анимации) от push/pop детальных экранов (fade).
    val tabRouteSet = remember { mainRoutes.toHashSet() }

    val backStack by nav.currentBackStackEntryAsState()
    val rawRoute = backStack?.destination?.route
    // Fix #226: никогда не падаем в "feed" при транзиентном null backStack.
    //
    // Раньше `?: Screen.Feed.route` на холодном старте (NavHost ещё не успел
    // навигировать на startDestination → currentBackStackEntry == null)
    // давал currentRoute="feed" на 1-2 кадра. Последствия:
    //   (1) LaunchedEffect(currentRoute) писал lastRoute="feed" в prefs → при
    //       следующем запуске startDestination="feed" → back из любого таба
    //       перекидывал в ленту («пытается в ленту перекинуть», «не всегда» —
    //       только если процесс убили до самоисправления).
    //   (2) NavigationBarItem(selected = currentRoute == screen.route) на этот
    //       кадр подсвечивал Ленту → визуальное «дёрганье» дока.
    //   (3) currentTitle на этот кадр = «Лента».
    //
    // Теперь: запоминаем последний валидный роут в remember-стейте, а до
    // первого валидного роута используем initialRoute (реальный lastRoute из
    // prefs). currentRoute больше никогда не становится "feed" фиктивно.
    var lastKnownRoute by remember { mutableStateOf<String?>(null) }
    if (rawRoute != null) lastKnownRoute = rawRoute
    val currentRoute = lastKnownRoute ?: initialRoute

    // Сохраняем текущий основной роут при каждой навигации.
    // Fix #226: ключ — rawRoute (а не currentRoute), и пишем ТОЛЬКО когда
    // rawRoute != null. Транзиентный null на холодном старте больше не портит
    // lastRoute значением "feed".
    LaunchedEffect(rawRoute) {
        if (rawRoute != null && rawRoute in mainRoutes) {
            app.prefs.setLastRoute(rawRoute)
        }
    }

    // Fix #52-B: истории обновляются при возврате на Feed из детальных экранов.
    // Отслеживаем переход НА Feed route — если предыдущий route был детальным
    // экраном (Community, UserProfile, PostDetail, VideoPlayer, StoryViewer),
    // значит пользователь вернулся из контекста где мог изменить состав
    // историй (subscribe/unsubscribe группы, просмотр истории = mark seen).
    // StoriesHolder.markDirty() инкрементирует dirtyKey → StoriesRow
    // перезагружает истории через LaunchedEffect(dirtyKey).
    var prevRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        val prev = prevRoute
        if (currentRoute == Screen.Feed.route && prev != null && prev != Screen.Feed.route) {
            // Возврат на Feed из другого экрана — истории могли измениться.
            StoriesHolder.markDirty()
            AppLog.d("SovaNavHost", "Returned to Feed from $prev → stories marked dirty")
        }
        // #POST-DETAIL-SCROLL: при возврате на PostDetail из Community/другого
        // экрана — StoriesHolder.markDirty() используется как триггер для
        // восстановления позиции скролла в PostDetailScreen (по аналогии с Feed).
        if (currentRoute.startsWith(Screen.PostDetail.route.substringBefore("{")) &&
            prev != null && !prev.startsWith(Screen.PostDetail.route.substringBefore("{"))
        ) {
            StoriesHolder.markDirty()
            AppLog.d("SovaNavHost", "Returned to PostDetail from $prev → scroll restore triggered")
        }
        prevRoute = currentRoute
    }

    // Локальный список вместо Screen.dock/drawer — исключает любые
    // потенциальные проблемы с инициализацией companion object через R8.
    val dockScreens: List<Screen> = listOf(
        Screen.Feed, Screen.Messages, Screen.Music, Screen.Video, Screen.Profile,
    )
    val drawerScreens: List<Screen> = listOf(
        Screen.Friends, Screen.Groups, Screen.Search,
        // #ARCH-CONTAINERS (Этап 1.5-а): Screen.Photos убран из ядерного
        // хардкода — пункт «Фото» приходит из реестра (NavEntry контейнера
        // :feature:photos, route "photos", см. containerNavEntries в
        // drawerContent). Без контейнера пункта нет, остальное живо
        // (NavHost-destination photos — остался ниже).
        Screen.Bookmarks, Screen.Documents,
        // #ARCH-CONTAINERS (Этап 1.4): Screen.CallsHistory убран из ядерного
        // хардкода — пункт «Звонки» приходит из реестра (NavEntry контейнера
        // :feature:calls, route "calls_history", см. containerNavEntries в
        // drawerContent). Без контейнера пункта нет, остальное живо.
        Screen.Clips,
        Screen.Services, Screen.Notifications, Screen.Settings, Screen.Logs,
        // #38: кнопка офлайн-менеджера в боковой панели — быстрый доступ к
        // скачанным аудио/видео без переключения в guest-режим. Маршрут
        // Screen.OfflineManager уже зарегистрирован ниже в NavHost.
        Screen.OfflineManager,
        // Этап 2 (#Equalizer): кнопка эквалайзера в боковой панели —
        // открывает полноэкранный EqualizerScreen (5 вкладок: пресеты,
        // полосы, bass+virt, reverb, loudness). Упрощённый EQ остаётся
        // в аудиоплеере (BottomSheet).
        Screen.Equalizer,
    )

    // ── Fix #337: редактор панелей ──────────────────────────────────────
    // Canonical-списки редактируемых пунктов (без фикс. хвоста для sidebar).
    // Drawer: dynamic-пункты (Офлайн/Настройки/Выйти — фикс. хвост, не тут).
    val sidebarEditableScreens: List<Screen> = remember {
        // #OFFLINE-DUPLICATE-FIX (2026-08-01): OfflineManager убран из
        //   sidebarEditableScreens — он рендерится в фикс. хвосте drawer
        //   вместе с Settings. Раньше был дубль: и в скролл-списке, и в хвосте.
        // #ARCH-CONTAINERS (Этап 1.4/1.5-а): Screen.CallsHistory и Screen.Photos
        //   убраны — контейнерные пункты панели (NavEntry) НЕ редактируются
        //   панель-редактором (их нет без контейнера; порядок задаёт
        //   capability.order). Сохранённые в prefs order-строки "calls_history"/
        //   "photos" отбрасываются normalizeRouteOrder как неизвестные.
listOf(
            Screen.Friends, Screen.Groups, Screen.Search,
            Screen.Bookmarks, Screen.Documents, Screen.Clips,
            Screen.Services, Screen.Notifications, Screen.Logs,
            Screen.Equalizer,
        )
    }
    // #SIDEBAR-BOTTOM-UNION (2026-08-01): пользователь просил, чтобы кнопки
    //   боковой панели были доступны и для нижней. Раньше в редакторе нижней
    //   панели было только 5 dock-кнопок. Теперь добавлены все sidebar-пункты
    //   (включая OfflineManager) — пользователь может поставить любую кнопку
    //   на нижнюю панель (Логи, Офлайн, Поиск и т.д.).
    val bottomBarEditableScreens: List<Screen> = remember {
        // #ARCH-CONTAINERS (Этап 1.5-а): Screen.Photos здесь ОСТАВЛЕН — Dock/
        //   нижняя панель — ядерная собственность хоста (Правило владения UI);
        //   «Фото» на нижней панели — кнопка-ярлык на destination "photos"
        //   (работает независимо от контейнера, destination в NavHost хоста).
        dockScreens + listOf(
            Screen.Friends, Screen.Groups, Screen.Photos, Screen.Search,
            Screen.Bookmarks, Screen.Documents, Screen.Clips,
            Screen.Services, Screen.Notifications, Screen.Logs,
            Screen.OfflineManager, Screen.Equalizer,
        )
    }

    // Парсим order/hidden из prefsSnap (null-safe для холодного старта).
    val sidebarOrderRoutes: List<String> = remember(prefsSnap?.sidebarItemsOrder) {
        parseRoutesJson(prefsSnap?.sidebarItemsOrder)
    }
    val sidebarHiddenRoutes: Set<String> = remember(prefsSnap?.sidebarItemsHidden) {
        parseRoutesJson(prefsSnap?.sidebarItemsHidden).toSet()
    }
    val bottomOrderRoutes: List<String> = remember(prefsSnap?.bottomBarItemsOrder) {
        parseRoutesJson(prefsSnap?.bottomBarItemsOrder)
    }
    val bottomHiddenRoutes: Set<String> = remember(prefsSnap?.bottomBarItemsHidden) {
        parseRoutesJson(prefsSnap?.bottomBarItemsHidden).toSet()
    }
    // Нормализованные списки Screen для рендера: порядок из prefs, скрытые убраны.
    val visibleSidebarScreens: List<Screen> = remember(sidebarOrderRoutes, sidebarEditableScreens, sidebarHiddenRoutes) {
        normalizeRouteOrder(sidebarOrderRoutes, sidebarEditableScreens)
            .filter { it.route !in sidebarHiddenRoutes }
    }
    val visibleBottomScreens: List<Screen> = remember(bottomOrderRoutes, bottomBarEditableScreens, bottomHiddenRoutes) {
        normalizeRouteOrder(bottomOrderRoutes, bottomBarEditableScreens)
            .filter { it.route !in bottomHiddenRoutes }
    }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // #247: отдельный диалог выхода из приложения (не из аккаунта).
    // Авторизация при этом сохраняется — при следующем запуске пользователь
    // сразу попадёт в ленту без повторного логина.
    var showExitAppDialog by remember { mutableStateOf(false) }
    val allScreens: List<Screen> = dockScreens + drawerScreens + listOf(Screen.About)
    // #ARCH-CONTAINERS (Этап 1.4/1.5-а): заголовок может прийти и из контейнерного
    // NavEntry (routes "calls_history"/"photos" больше не в ядерном списке — см.
    // drawerScreens). Неизвестный роут — как раньше, "PinoK".
    val currentTitle = allScreens.firstOrNull { it.route == currentRoute }?.title
        ?: containerNavEntries
            .firstOrNull { hostDestinationForRoute(it.route)?.route == currentRoute }?.title
        ?: "PinoK"

    // Экраны со своим TopAppBar — скрываем глобальный заголовок и навбар.
    // #30 (nav fix): добавлены Community, AudioPlayer, AudioQueue — у них есть
    // собственный TopAppBar с back button, но они НЕ были в списке, из-за чего
    // глобальный TopAppBar + bottom nav + mini-player показывались поверх.
    val hasOwnTopBar = listOf(
        // #90: VideoPlayer убран — теперь overlay, не маршрут NavHost.
        Screen.UserProfile.route,
        Screen.PostDetail.route, Screen.ChatDetail.route,
        Screen.Logs.route, Screen.StoryViewer.route,
        Screen.OfflineManager.route,
        // Fix #111: у офлайн-плеера историй собственный TopAppBar + back button.
        Screen.StoryOfflinePlayer.route,
        // §37.12 #330: у офлайн-плеера клипов собственный fullscreen UI + back button.
        Screen.ClipOfflinePlayer.route,
        // Fix #50: у офлайн-плеера собственный TopAppBar + back button.
        Screen.OfflineAudioPlayer.route,
        // Этап 2 (#Equalizer): у EqualizerScreen собственный TopAppBar
        // (← Эквалайзер + master switch).
        Screen.Equalizer.route,
        Screen.Community.route, Screen.AudioPlayer.route, Screen.AudioQueue.route,
        // Шаг 4 (#32d): BoardTopicScreen имеет собственный TopAppBar с back button.
        Screen.BoardTopic.route,
        // Fix #272: у ChatInfoScreen собственный TopAppBar («Информация» + back).
        // Раньше маршрут НЕ был в списке → глобальный ScreenTopBar НЕ скрывался,
        // но локальный TopAppBar рисовал свой → появлялись ДВА AppBar'а
        // (пустой глобальный сверху + «Информация» снизу) с пустотой между ними.
        // На скриншоте пользователя это выглядело как «стрелка назад + PinoK»
        // (глобальный, остался от CommunityScreen) И ниже «стрелка назад + Информация»
        // (локальный ChatInfoScreen) — две панели с пустым зазором.
        Screen.ChatInfo.route,
        // Fix #272: у FoldersSettingsScreen тоже собственный TopAppBar —
        // та же проблема двойного AppBar'а при открытии из MessagesScreen.
        Screen.FoldersSettings.route,
        // Fix #144: у InternalBrowserScreen собственный TopAppBar (← URL ↻ ⋮).
        // Жалоба: «при просмотре в диалоге файла образуется две верхние панели —
        // должна остаться только одна (с URL), верхняя с надписью PinoK пропадать».
        // Причина: маршрут НЕ был в hasOwnTopBar → глобальный ScreenTopBar
        // (← PinoK) оставался видимым поверх локального TopAppBar браузера.
        // Та же проблема что и у ChatInfo (Fix #272) и FoldersSettings.
        Screen.InternalBrowser.route,
        // Fix #NOTIF-SETTINGS-DUAL-BAR (2026-08-04): у NotificationSettingsScreen
        // собственный Scaffold+TopAppBar («← Уведомления ⋮»). Маршрут НЕ был в
        // hasOwnTopBar → глобальный ScreenTopBar (← PinoK) оставался видимым
        // поверх локального → две панели (скриншот Screenshot_20260804_212704).
        // Та же проблема что и у ChatInfo/FoldersSettings/InternalBrowser.
        Screen.NotificationSettings.route,
        // #MUSIC-PORT: у музыкальных экранов собственный LibraryTopBar
        // (← Название). Маршруты не были в списке → глобальный ScreenTopBar
        // (← PinoK) рисовался поверх локального → две панели.
        Screen.MusicPlaylists.route,
        Screen.PlaylistDetail.route,
        Screen.MusicAlbums.route,
        Screen.MusicArtists.route,
        Screen.ArtistDetail.route,
        Screen.CatalogSection.route,
    ).any { currentRoute.startsWith(it.substringBefore("{")) }

    // §37.12 #327: экраны, которые хотят скрыть ТОЛЬКО глобальный TopAppBar,
    // но ОСТАВИТЬ bottom NavigationBar. В отличие от hasOwnTopBar (который
    // скрывает оба), hidesGlobalTopBarOnly скрывает только верхнюю панель.
    // Clips: full-screen vertical pager — TopAppBar с заголовком «Клипы»
    // занимает место поверх видео, но нижняя навигация нужна для перехода
    // на другие вкладки. Overlay клипов (←, 🔊, +) поднимается наверх.
    val hidesGlobalTopBarOnly = currentRoute == Screen.Clips.route
    val showGlobalTopBar = !hasOwnTopBar && !hidesGlobalTopBarOnly

    // Fix #256: сбрасываем ScreenTopBar при смене маршрута. Экраны конфигурируют
    // TopBar через DisposableEffect, но если экран не имеет DisposableEffect (или
    // если hasOwnTopBar=true и глобальный TopAppBar скрыт), старая конфигурация
    // может остаться. Этот LaunchedEffect — safety net.
    LaunchedEffect(currentRoute, hasOwnTopBar) {
        if (hasOwnTopBar || hidesGlobalTopBarOnly) {
            // На экранах со своим TopAppBar ИЛИ скрывающих глобальный — очищаем.
            ScreenTopBar.clear()
        }
    }

    // Hide-on-scroll bottom NavigationBar (#299):
    // При скролле контента вниз (delta.y < 0) прячем нижнее меню, при скролте
    // вверх — показываем. Реализовано через NestedScrollConnection, который
    // получает дельты от LazyColumn/scrollable внутри экранов. Порог +8px
    // отсекает джиттер от пальца. Гистерезис: чтобы переключить состояние,
    // нужно накопить >24px в одном направлении — иначе бар мигает на каждом
    // маленьком движении. MiniPlayer НЕ прячется (это не «меню»).
    // #FEED-FAB-SYNC: сохраняем сам State-объект, чтобы пробросить его в дочерние
    // экраны через LocalBottomBarVisible (CompositionLocalProvider ниже).
    // FeedScreen читает .value → FAB «наверх» появляется синхронно со скрытием
    // панели, а не ждёт порога 200px (fix «слепой зоны» 24–200px).
    val bottomBarVisibleState = remember { androidx.compose.runtime.mutableStateOf(true) }
    var bottomBarVisible by bottomBarVisibleState
    val scrollDeltaAccumulator = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val hideOnScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val dy = available.y
                if (dy == 0f) return androidx.compose.ui.geometry.Offset.Zero
                val prev = scrollDeltaAccumulator.floatValue
                // Накапливаем, ограничивая чтобы накопитель не «залипал».
                val next = (prev + dy).coerceIn(-160f, 160f)
                scrollDeltaAccumulator.floatValue = next
                if (!bottomBarVisible && next > 24f) {
                    // скроллим вверх → показать
                    bottomBarVisible = true
                    scrollDeltaAccumulator.floatValue = 0f
                } else if (bottomBarVisible && next < -24f) {
                    // скроллим вниз → спрятать
                    bottomBarVisible = false
                    scrollDeltaAccumulator.floatValue = 0f
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    // При смене таба — сбрасываем в видимое состояние (новый экран не должен
    // наследовать «свёрнутый» бар с предыдущего).
    LaunchedEffect(currentRoute) {
        bottomBarVisible = true
        scrollDeltaAccumulator.floatValue = 0f
    }

    // Fix #258: динамическая ширина drawer — по самому длинному пункту меню.
    // Раньше было 280dp * fontScale (240..420dp) — пользователь видел слишком
    // широкий drawer. Теперь измеряем самый длинный заголовок через
    // TextMeasurer и добавляем отступы (иконка + padding + запас).
    // Самые длинные пункты: «Выйти из приложения» (19), «Уведомления» (11),
    // «Сообщества» (10), «Документы» (9).
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val drawerTextStyle = MaterialTheme.typography.labelLarge
    val longestDrawerText = remember(allScreens, containerNavEntries) {
        // allScreens + контейнерные NavEntry + «Выйти из приложения» (отдельная кнопка в drawer).
        val titles = allScreens.map { it.title } + containerNavEntries.map { it.title } + "Выйти из приложения"
        titles.maxByOrNull { it.length } ?: "PinoK"
    }
    val drawerWidth = remember(textMeasurer, longestDrawerText, density.fontScale) {
        val measured = textMeasurer.measure(
            text = longestDrawerText,
            style = drawerTextStyle,
        )
        // measured.size.width — в пикселях. Конвертируем в Dp через density.
        val contentWidthDp = with(density) { measured.size.width.toDp() }
        // Ширина текста + иконка (24dp) + gap (12dp) + горизонтальные
        // padding'и drawer-пункта (16+16=32dp) + запас (16dp).
        (contentWidthDp + 24.dp + 12.dp + 32.dp + 16.dp).coerceIn(200.dp, 320.dp)
    }

    // Sprint 1, P0-3 (#76): CaptchaDialog поверх всего — captcha может
    // потребоваться в любом запросе, независимо от активного экрана.
    Box {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // #247: ModalDrawerSheet с адаптивной шириной под fontScale.
        // Без modifier.width() drawer использует дефолт 360dp — слишком
        // узко для крупного шрифта, слишком широко для мелкого.
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth)
                    // Fix #337: drawer на всю высоту — header сверху, fixed-tail
                    // снизу, middle скроллится если пунктов много.
                    .fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    // #247: заголовок с кнопкой сворачивания drawer.
                    // Кнопка MenuOpen (AutoMirrored — RTL-совместимая) вызывает
                    // drawerState.close() — альтернатива свайпу влево.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "PinoK",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuOpen,
                                contentDescription = "Свернуть меню",
                            )
                        }
                    }
                    // Fix #337: dynamic-пункты в скроллящемся Column.
                    // Если пунктов много (или крупный шрифт) — скролл работает.
                    // visibleSidebarScreens уже отфильтрован от hidden и
                    // упорядочен по prefs пользователя.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        visibleSidebarScreens.forEach { screen ->
                            // §37.12 Phase 7: badge с кол-вом новых clips на пункте «Клипы».
                            val clipsBadge = if (screen.route == Screen.Clips.route) {
                                re.pinok.realtime.ClipsCounter.count.collectAsState().value
                            } else 0
                            NavigationDrawerItem(
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    // #30 (nav fix): popUpTo + saveState — как в dock items.
                                    if (screen.route == Screen.Clips.route && clipsBadge > 0) {
                                        // Сбрасываем счётчик при открытии clips-экрана.
                                        re.pinok.realtime.ClipsCounter.reset()
                                    }
                                    nav.navigate(screen.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                icon = {
                                    if (screen.icon != null) {
                                        if (clipsBadge > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text(if (clipsBadge > 99) "99+" else clipsBadge.toString()) }
                                                },
                                            ) {
                                                Icon(screen.icon, contentDescription = null)
                                            }
                                        } else {
                                            Icon(screen.icon, contentDescription = null)
                                        }
                                    }
                                },
                            )
                        }
                        // #ARCH-CONTAINERS (Этап 1.4): контейнерные пункты панели —
                        // из реестра (NavEntry), ПОСЛЕ ядерных (внутри группы —
                        // по order, см. containerNavEntries). Нет контейнера →
                        // пунктов нет (graceful); неизвестный хосту route →
                        // предупреждение в лог и пропуск (не падаем).
                        containerNavEntries.forEach { entry ->
                            val dest = hostDestinationForRoute(entry.route)
                            if (dest == null) {
                                LaunchedEffect(entry.route) {
                                    AppLog.w(
                                        "SovaNavHost",
                                        "CONTAINERS: NavEntry route '${entry.route}' не зарегистрирован в хосте — пункт «${entry.title}» скрыт",
                                    )
                                }
                            } else {
                                NavigationDrawerItem(
                                    label = { Text(entry.title) },
                                    selected = currentRoute == dest.route,
                                    onClick = {
                                        // Те же опции, что у ядерных пунктов (popUpTo+saveState).
                                        nav.navigate(dest.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = {
                                        Icon(hostIconForKey(entry.iconKey), contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                    // Fix #337: фиксированный хвост drawer — всегда в этом порядке,
                    // не редактируется пользователем: Офлайн → Настройки → Выйти.
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text(Screen.OfflineManager.title) },
                        selected = currentRoute == Screen.OfflineManager.route,
                        onClick = {
                            nav.navigate(Screen.OfflineManager.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(Screen.OfflineManager.icon ?: Icons.Default.CloudOff, contentDescription = null)
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text(Screen.Settings.title) },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            nav.navigate(Screen.Settings.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(Screen.Settings.icon ?: Icons.Default.Settings, contentDescription = null)
                        },
                    )
                    // #247: «Выйти из приложения» — закрывает приложение целиком,
                    // сохраняя авторизацию. При следующем запуске пользователь
                    // сразу попадёт в ленту. Полный logout из аккаунта —
                    // в ProfileScreen (отдельная кнопка «Выйти из аккаунта»).
                    NavigationDrawerItem(
                        label = { Text("Выйти из приложения") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showExitAppDialog = true
                        },
                        icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            // Hide-on-scroll (#299): connection ловит дельты скролла от
            // LazyColumn/verticalScroll внутри экранов и переключает
            // bottomBarVisible. Навешиваем на сам Scaffold — nested scroll
            // всплывает от контента (NavHost → экраны → списки).
            modifier = Modifier.nestedScroll(hideOnScrollConnection),
            // #49: Убираем insets только сверху и по горизонтали —
            // системные панели (status bar, nav bar) и клавиатура (IME)
            // должны учитываться Compose-ayout'ом через imePadding/WindowInsets.
            // Нули: WindowInsets(0, 0, 0, 0) убивает IME insets →
            // клавиатура «съедает» контент вместо его сдвига.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Скрываем глобальный TopAppBar на экранах со своим заголовком
                // (hasOwnTopBar) ИЛИ на экранах, которые явно скрывают только topBar
                // (hidesGlobalTopBarOnly, например Clips — full-screen pager).
                // §37.12 #327: bottom NavigationBar при этом ОСТАЁТСЯ для hidesGlobalTopBarOnly.
                if (showGlobalTopBar) {
                    Column {
                        TopAppBar(
                            // Fix #256: экраны могут переопределить title (например,
                            // TextField для поиска) или navigationIcon (back button).
                            title = ScreenTopBar.titleOverride ?: { Text(currentTitle) },
                            navigationIcon = {
                                ScreenTopBar.navigationIconOverride?.invoke()
                                    ?: run {
                                        // Fix #261: на Ленте — hamburger (открытие drawer).
                                        // На всех остальных экранах — кнопка «Назад»
                                        // (popBackStack). Drawer всё ещё доступен через
                                        // свайп от левого края (ModalNavigationDrawer).
                                        if (currentRoute == Screen.Feed.route) {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                Icon(Icons.Default.Menu, contentDescription = "Меню")
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                if (!nav.popBackStack()) {
                                                    // Fallback: если назад некуда — на Ленту.
                                                    nav.navigate(Screen.Feed.route) {
                                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Назад",
                                                )
                                            }
                                        }
                                    }
                            },
                            // Fix #256: экраны регистрируют свои actions (search, filter,
                            // mark-all-read) через ScreenTopBar.configure().
                            actions = {
                                ScreenTopBar.actions?.invoke()
                            },
                            // #49: Явный отступ под status bar (время/сеть/батарея).
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        )
                        // Fix #256: subBar — контент под TopAppBar (filter chips, tabs).
                        ScreenTopBar.subBar?.invoke()
                    }
                }
            },
            bottomBar = {
                // #57: GlobalMiniPlayer + NavigationBar в Column.
                // Мини-плеер показываем ВСЕГДА (кроме AudioPlayer/VideoPlayer) —
                // даже на экранах с hasOwnTopBar (OfflineManager, Community, и т.д.).
                // NavigationBar — только на основных экранах (не hasOwnTopBar).
                // Раньше мини-плеер был overlay в Box → перекрывал NavigationBar.
                val showMiniPlayer = currentRoute != Screen.AudioPlayer.route &&
                    currentRoute != Screen.VideoPlayer.route &&
                    overlayVideo == null
                // Fix #334: navigationBarsPadding на САМОМ outer Column bottomBar-слота,
                // а НЕ на NavigationBar внутри AnimatedVisibility. Раньше inset был на
                // NavigationBar (стр.601) — при hide-on-scroll (#299) AnimatedVisibility
                // схлопывал NavigationBar к 0 высоты, и inset схлопывался вместе с ним →
                // contentPadding.bottom Scaffold'а = 0 → контент уходил под системную
                // nav bar (footer «Загрузить ещё», FAB «наверх», последние Switch'и в
                // настройках). Перенос inset на Column гарантирует, что слот ВСЕГДА
                // резервирует место под nav bar, независимо от visible/hidden NavigationBar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    if (showMiniPlayer) {
                        GlobalMiniPlayer(
                            onOpenPlayer = { nav.navigate(Screen.AudioPlayer.route) },
                        )
                    }
                    if (!hasOwnTopBar && visibleBottomScreens.isNotEmpty()) {
                        // Fix #337: если все кнопки нижней панели скрыты —
                        // NavigationBar не показываем (рамка осталась бы).
                        // Hide-on-scroll (#299): AnimatedVisibility с shrinkVertically
                        // схлопывает NavigationBar к нижнему краю. Scaffold измеряет
                        // высоту bottomBar слота — при collapse высота=0 → content
                        // padding bottom автоматически становится 0, контент
                        // разворачивается на всю высоту. MiniPlayer остаётся видимым.
                        // Fix #334: inset перенесён на outer Column — здесь НЕ дублируем.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = bottomBarVisible,
                            enter = androidx.compose.animation.expandVertically(
                                expandFrom = Alignment.Bottom,
                                animationSpec = androidx.compose.animation.core.tween(220),
                            ),
                            exit = androidx.compose.animation.shrinkVertically(
                                shrinkTowards = Alignment.Bottom,
                                animationSpec = androidx.compose.animation.core.tween(200),
                            ),
                        ) {
                            // #BOTTOM-SCROLL (2026-08-01): если на нижней панели
                            // больше 5 кнопок (пользователь включил доп. пункты
                            // через «Редактор панелей») — панель становится
                            // горизонтально прокручиваемой. Каждая кнопка получает
                            // фиксированную ширину 80.dp (как стандартный
                            // NavigationBarItem при 5 элементах), Row скроллится.
                            // При ≤5 кнопок — обычный NavigationBar (кнопки
                            // распределяются по всей ширине через weight).
                            if (visibleBottomScreens.size <= 5) {
                                // Fix #COMPILE: NavigationBarItem вызывается INLINE
                                // внутри NavigationBar{ } content-scope (как в старом
                                // коде до #BOTTOM-SCROLL). В M3 BOM 2025.06 вызов
                                // NavigationBarItem из отдельной @Composable fun
                                // давал 'Unresolved reference' — внутри NavigationBar{}
                                // scope резолвится корректно.
                                NavigationBar {
                                    visibleBottomScreens.forEach { screen ->
                                        val navIcon = screen.icon
                                        val unreadCount = if (screen.route == Screen.Messages.route) {
                                            re.pinok.realtime.UnreadMessagesCounter.unreadCount.collectAsState().value
                                        } else {
                                            0
                                        }
                                        NavigationBarItem(
                                            selected = currentRoute == screen.route,
                                            onClick = {
                                                nav.navigate(screen.route) {
                                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                if (navIcon != null) {
                                                    if (screen.route == Screen.Messages.route && unreadCount > 0) {
                                                        BadgedBox(
                                                            badge = {
                                                                Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                                                            },
                                                        ) {
                                                            Icon(navIcon, contentDescription = screen.title)
                                                        }
                                                    } else {
                                                        Icon(navIcon, contentDescription = screen.title)
                                                    }
                                                }
                                            },
                                            label = { Text(screen.title) },
                                        )
                                    }
                                }
                            } else {
                                val bottomScrollState = rememberScrollState()
                                Surface(
                                    color = NavigationBarDefaults.containerColor,
                                    contentColor = androidx.compose.material3.contentColorFor(
                                        NavigationBarDefaults.containerColor
                                    ),
                                    tonalElevation = NavigationBarDefaults.Elevation,
                                    shadowElevation = 3.dp,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(bottomScrollState)
                                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                            .height(80.dp)
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        visibleBottomScreens.forEach { screen ->
                                            BottomNavScrollButton(
                                                screen = screen,
                                                selected = currentRoute == screen.route,
                                                onClick = {
                                                    nav.navigate(screen.route) {
                                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                modifier = Modifier.width(80.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fix #334: ранее тут был Spacer(windowInsetsPadding(navigationBars))
                        // для экранов с hasOwnTopBar. Теперь inset на outer Column (стр.588),
                        // Spacer избыточен — Column уже резервирует место под nav bar.
                    }
                }
            },
        ) { padding ->
            // #FEED-FAB-SYNC: пробрасываем bottomBarVisibleState в дочерние экраны,
            // чтобы FeedScreen показывал FAB «наверх» синхронно со скрытием панели.
            CompositionLocalProvider(
                LocalAnimScale provides animScale,
                LocalBottomBarVisible provides bottomBarVisibleState,
            ) {
            NavHost(
                navController = nav,
                startDestination = initialRoute,
                modifier = Modifier.padding(padding).imePadding(),
                // Fix #224: масштабируемые fade-переходы между экранами.
                // animScale=0 → tweenScaled возвращает snap() → мгновенный переход.
                // animScale=1 → нормальный fade 220ms (enter) / 180ms (exit).
                //
                // Fix #226: переключение между табами дока/дровера — БЕЗ анимации.
                // Раньше каждый тап по нижнему доку запускал fadeIn/fadeOut →
                // видимое «дёрганье»/мигание при смене таба. Табы — равноправные
                // разделы, а не drill-down навигация, поэтому смена таба должна
                // быть мгновенной. Fade остаётся только для push/pop детальных
                // экранов (ChatDetail, PostDetail, Community, UserProfile и т.д.).
                enterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    if (from in tabRouteSet && to in tabRouteSet) EnterTransition.None
                    else fadeIn(tweenScaled<Float>(animScale, 220))
                },
                exitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    if (from in tabRouteSet && to in tabRouteSet) ExitTransition.None
                    else fadeOut(tweenScaled<Float>(animScale, 180))
                },
                popEnterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    if (from in tabRouteSet && to in tabRouteSet) EnterTransition.None
                    else fadeIn(tweenScaled<Float>(animScale, 220))
                },
                popExitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    if (from in tabRouteSet && to in tabRouteSet) ExitTransition.None
                    else fadeOut(tweenScaled<Float>(animScale, 180))
                },
            ) {
                composable(Screen.Feed.route) {
                    FeedScreen(
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        // Fix #67: навигация на экран сообщества из ленты.
                        onGroupClick = { groupId ->
                            nav.navigate(Screen.Community.buildRoute(groupId))
                        },
                        // Fix #71: навигация на детальный экран поста.
                        onPostClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                        // Sprint 1, P0-2 (#74): навигация на экран чужого профиля.
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        // Stories → StoryViewerScreen.
                        onStoryViewerClick = {
                            nav.navigate(Screen.StoryViewer.route)
                        },
                        // Офлайн-менеджер: при отсутствии сети.
                        onOpenOfflineManager = {
                            nav.navigate(Screen.OfflineManager.route)
                        },
                        // #ARCH-CONTAINERS (Этап 1.4): запуск звонка — только через
                        // реестр (CallStarter). callClick == null → кнопка звонка
                        // в ленте НЕ рендерится (условие композиции).
                        onCallClick = callClick,
                    )
                }
                composable(Screen.Messages.route) {
                    MessagesScreen(
                        onChatClick = { chat ->
                            nav.navigate(
                                Screen.ChatDetail.buildRoute(chat.peer.id, chat.peer.title ?: "Диалог", chat.peer.photo)
                            )
                        },
                        onFoldersSettings = {
                            nav.navigate(Screen.FoldersSettings.route)
                        },
                    )
                }
                composable(Screen.Music.route) {
                    MusicScreen(
                        onOpenPlayer = { nav.navigate(Screen.AudioPlayer.route) },
                        onOpenQueue = { nav.navigate(Screen.AudioQueue.route) },
                        // #MUSIC-PORT: навигация на экраны музыкальной библиотеки.
                        onOpenPlaylists = { nav.navigate(Screen.MusicPlaylists.route) },
                        onOpenAlbums = { nav.navigate(Screen.MusicAlbums.route) },
                        onOpenArtists = { nav.navigate(Screen.MusicArtists.route) },
                        onOpenPlaylist = { ownerId, playlistId, accessKey ->
                            nav.navigate(Screen.PlaylistDetail.buildRoute(ownerId, playlistId, accessKey))
                        },
                        onOpenAlbum = { ownerId, albumId, accessKey ->
                            nav.navigate(Screen.PlaylistDetail.buildRoute(ownerId, albumId, accessKey))
                        },
                        onOpenArtist = { slug, name ->
                            nav.navigate(Screen.ArtistDetail.buildRoute(slug, name))
                        },
                        onShowAll = { sectionId, title ->
                            nav.navigate(Screen.CatalogSection.buildRoute(sectionId, title))
                        },
                    )
                }
                // #MUSIC-PORT: экраны музыкальной библиотеки.
                composable(Screen.MusicPlaylists.route) {
                    MusicPlaylistsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPlaylist = { ownerId, playlistId, accessKey ->
                            nav.navigate(Screen.PlaylistDetail.buildRoute(ownerId, playlistId, accessKey))
                        },
                    )
                }
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(
                        navArgument(Screen.PlaylistDetail.ARG_OWNER_ID) { type = NavType.LongType },
                        navArgument(Screen.PlaylistDetail.ARG_PLAYLIST_ID) { type = NavType.LongType },
                        navArgument(Screen.PlaylistDetail.ARG_ACCESS_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val ownerId = entry.arguments?.getLong(Screen.PlaylistDetail.ARG_OWNER_ID) ?: 0L
                    val playlistId = entry.arguments?.getLong(Screen.PlaylistDetail.ARG_PLAYLIST_ID) ?: 0L
                    val accessKey = entry.arguments?.getString(Screen.PlaylistDetail.ARG_ACCESS_KEY)
                        ?.takeIf { it.isNotBlank() }
                    PlaylistDetailScreen(
                        ownerId = ownerId,
                        playlistId = playlistId,
                        accessKey = accessKey,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Screen.MusicAlbums.route) {
                    MusicAlbumsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenAlbum = { ownerId, albumId, accessKey ->
                            nav.navigate(Screen.PlaylistDetail.buildRoute(ownerId, albumId, accessKey))
                        },
                    )
                }
                composable(Screen.MusicArtists.route) {
                    MusicArtistsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenArtist = { slug, name -> nav.navigate(Screen.ArtistDetail.buildRoute(slug, name)) },
                    )
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        navArgument(Screen.ArtistDetail.ARG_SLUG) { type = NavType.StringType },
                        navArgument(Screen.ArtistDetail.ARG_NAME) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val slug = entry.arguments?.getString(Screen.ArtistDetail.ARG_SLUG) ?: ""
                    val name = entry.arguments?.getString(Screen.ArtistDetail.ARG_NAME) ?: ""
                    ArtistDetailScreen(
                        slug = slug,
                        name = name,
                        onBack = { nav.popBackStack() },
                    )
                }
                // #MUSIC-CATALOG-SHOW-ALL: полный список блока каталога.
                composable(
                    route = Screen.CatalogSection.route,
                    arguments = listOf(
                        navArgument(Screen.CatalogSection.ARG_SECTION_ID) { type = NavType.StringType },
                        navArgument(Screen.CatalogSection.ARG_TITLE) {
                            type = NavType.StringType
                            defaultValue = "Показать все"
                        },
                    ),
                ) { entry ->
                    val sectionId = entry.arguments?.getString(Screen.CatalogSection.ARG_SECTION_ID) ?: ""
                    val title = entry.arguments?.getString(Screen.CatalogSection.ARG_TITLE) ?: "Показать все"
                    CatalogSectionScreen(
                        sectionId = sectionId,
                        title = title,
                        onBack = { nav.popBackStack() },
                        onOpenPlaylist = { ownerId, playlistId, accessKey ->
                            nav.navigate(Screen.PlaylistDetail.buildRoute(ownerId, playlistId, accessKey))
                        },
                    )
                }
                composable(Screen.AudioPlayer.route) {
                    AudioPlayerScreen(
                        onBack = { nav.popBackStack() },
                        onOpenQueue = { nav.navigate(Screen.AudioQueue.route) },
                        onOpenFullEqualizer = { nav.navigate(Screen.Equalizer.route) },
                    )
                }
                composable(Screen.AudioQueue.route) {
                    AudioQueueScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Screen.Video.route) {
                    VideoScreen(
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                    )
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onLogout = onLogout,
                        // Fix #70: навигация на видеоплеер из постов на стене профиля.
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
                        onCommentClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                    )
                }
                composable(Screen.Friends.route)       {
                    FriendsScreen(
                        // Sprint 1, P0-2 (#74): тап на друга → экран профиля.
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        // #CALLS: кнопка звонка на карточке друга.
                        // #ARCH-CONTAINERS (Этап 1.4): через CallStarter; null →
                        // кнопка не рендерится (см. FriendsScreen).
                        onCallClick = callClick,
                    )
                }
                composable(Screen.Groups.route) {
                    GroupsScreen(
                        onGroupClick = { groupId ->
                            nav.navigate(Screen.Community.buildRoute(groupId))
                        },
                    )
                }
                composable(Screen.Photos.route)        { PhotosScreen() }
                composable(Screen.Search.route)        {
                    SearchScreen(
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        onGroupClick = { groupId ->
                            nav.navigate(Screen.Community.buildRoute(groupId))
                        },
                    )
                }
                composable(Screen.Bookmarks.route)     {
                    BookmarksScreen(
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        onGroupClick = { groupId ->
                            nav.navigate(Screen.Community.buildRoute(groupId))
                        },
                        onPostClick = { ownerId, postId ->
                            PostHolder.last = re.pinok.data.model.Post(
                                ownerId = ownerId, fromId = ownerId, id = postId, date = 0, text = "",
                            )
                            nav.navigate(Screen.PostDetail.buildRoute(ownerId, postId))
                        },
                        onVideoClick = { ownerId, videoId ->
                            // FIX: VideoHolder.last не устанавливался — VideoPlayerScreen не получал Video
                            VideoHolder.open(Video(
                                id = videoId, ownerId = ownerId, title = "", duration = 0, date = 0,
                            ))
                        },
                    )
                }
                composable(Screen.Documents.route)     { DocumentsScreen() }
                // #CALLS: история звонков. #ARCH-CONTAINERS (Этап 1.4): пункт
                // drawer «Звонки» теперь из реестра — без контейнера этот экран
                // недостижим; сам NavHost-destination остаётся у хоста.
composable(Screen.CallsHistory.route) {
                    re.pinok.ui.screens.calls.CallsMainScreen(
                        onBack = { nav.popBackStack() },
                        onNavigateToCall = { peerId ->
                            // #ARCH-CONTAINERS (Этап 1.4): redial — тоже через
                            // CallStarter. Стартера нет → только лог: экран в этом
                            // состоянии недостижим (нет drawer-пункта). title/photo
                            // здесь нет (секция отдаёт peerId) → CallScreen подтянет
                            // имя/аватар сам (usersGetByIds, OutgoingCallMeta пуст).
                            val starter = callStarter
                            if (starter != null) {
                                starter.startCall(peerId, video = false)
                            } else {
                                AppLog.w("SovaNavHost", "CONTAINERS: CallStarter недоступен — redial из истории звонков пропущен (peerId=$peerId)")
                            }
                        },
                    )
                }
                composable(Screen.Clips.route) {
                    // §37.12 Phase 3 + 4 + 6: Clips feed (vertical pager, full-screen)
                    // + interactions (share / comments / more-actions sheets).
                    val vm: re.pinok.ui.screens.clips.ClipsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = re.pinok.ui.screens.clips.clipsViewModelFactory(),
                    )
                    // Локальный state для выбранного clip + типа sheet'а.
                    var shareClip by remember { androidx.compose.runtime.mutableStateOf<re.pinok.data.model.Video?>(null) }
                    var commentClip by remember { androidx.compose.runtime.mutableStateOf<re.pinok.data.model.Video?>(null) }
                    var moreClip by remember { androidx.compose.runtime.mutableStateOf<re.pinok.data.model.Video?>(null) }
                    val app = remember { SovaApp.get(nav.context) }
                    val clipScope = rememberCoroutineScope()

                    re.pinok.ui.screens.clips.ClipsFeedScreen(
                        vm = vm,
                        onAuthorClick = { ownerId ->
                            if (ownerId > 0) {
                                nav.navigate(Screen.UserProfile.buildRoute(ownerId))
                            } else if (ownerId < 0) {
                                nav.navigate(Screen.Community.buildRoute(-ownerId))
                            }
                        },
                        onBack = {
                            if (!nav.popBackStack()) {
                                nav.navigate(Screen.Feed.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onShareClip = { clip -> shareClip = clip },
                        onCommentClip = { clip -> commentClip = clip },
                        onMoreActions = { clip -> moreClip = clip },
                        onHashtagClick = { tag ->
                            // §37.10: тап по хештегу → поиск clips по этому тегу.
                            vm.search(tag)
                        },
                        onCreateClip = { nav.navigate(Screen.ClipCreate.route) },
                    )

                    // Phase 4: sheets поверх клипов.
                    shareClip?.let { clip ->
                        re.pinok.ui.screens.clips.ClipShareSheet(
                            clip = clip,
                            onDismiss = { shareClip = null },
                            onShareToChat = { peerId, peerName ->
                                clipScope.launch {
                                    try {
                                        // §37.12 #322: для приватных клипов нужен access_key в attachment.
                                        val attachment = buildString {
                                            append("video").append(clip.ownerId).append("_").append(clip.id)
                                            if (!clip.accessKey.isNullOrBlank()) append("_").append(clip.accessKey)
                                        }
                                        val mid = app.apiClient.messagesSend(peerId, "", attachment = attachment)
                                        if (mid > 0) {
                                            android.widget.Toast.makeText(
                                                nav.context, "Отправлено в «$peerName»", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(
                                                nav.context, "Не удалось отправить", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            nav.context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onShareToWall = {
                                clipScope.launch {
                                    try {
                                        // §37.12 #322: для приватных клипов нужен access_key в attachment.
                                        val attachment = buildString {
                                            append("video").append(clip.ownerId).append("_").append(clip.id)
                                            if (!clip.accessKey.isNullOrBlank()) append("_").append(clip.accessKey)
                                        }
                                        val postId = app.apiClient.wallPostWithAttachments(
                                            attachments = attachment,
                                            message = "",
                                        )
                                        android.widget.Toast.makeText(
                                            nav.context,
                                            if (postId > 0) "Опубликовано на стене" else "Не удалось опубликовать",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            nav.context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onCopyLink = {
                                val link = "https://vk.com/clip${clip.ownerId}_${clip.id}"
                                val cm = nav.context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("VK Clip", link))
                                android.widget.Toast.makeText(
                                    nav.context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }

                    commentClip?.let { clip ->
                        re.pinok.ui.screens.clips.ClipCommentsSheet(
                            clip = clip,
                            onDismiss = { commentClip = null },
                        )
                    }

                    moreClip?.let { clip ->
                        re.pinok.ui.screens.clips.ClipMoreActionsSheet(
                            clip = clip,
                            onDismiss = { moreClip = null },
                            onSubscribe = {
                                val wasSubscribed = clip.isSubscribedToAuthor
                                vm.toggleSubscribe(clip) { ok ->
                                    android.widget.Toast.makeText(
                                        nav.context,
                                        when {
                                            !ok && !wasSubscribed -> "Не удалось подписаться"
                                            !ok && wasSubscribed -> "Не удалось отписаться"
                                            wasSubscribed -> "Вы отписались"
                                            else -> "Вы подписались"
                                        },
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onUnsubscribe = {
                                val wasSubscribed = clip.isSubscribedToAuthor
                                vm.toggleSubscribe(clip) { ok ->
                                    android.widget.Toast.makeText(
                                        nav.context,
                                        when {
                                            !ok && wasSubscribed -> "Не удалось отписаться"
                                            !ok && !wasSubscribed -> "Не удалось подписаться"
                                            wasSubscribed -> "Вы отписались"
                                            else -> "Вы подписались"
                                        },
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onFavorite = {
                                val wasFav = clip.isFavorited
                                vm.toggleFavorite(clip) { ok ->
                                    android.widget.Toast.makeText(
                                        nav.context,
                                        when {
                                            !ok && !wasFav -> "Не удалось добавить в закладки"
                                            !ok && wasFav -> "Не удалось убрать из закладок"
                                            wasFav -> "Убрано из закладок"
                                            else -> "Добавлено в закладки"
                                        },
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onReport = {
                                vm.reportClip(clip)
                                android.widget.Toast.makeText(
                                    nav.context, "Жалоба отправлена", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onBanAuthor = {
                                clipScope.launch {
                                    val ok = app.apiClient.accountBan(clip.ownerId)
                                    android.widget.Toast.makeText(
                                        nav.context,
                                        if (ok) "Автор заблокирован" else "Не удалось заблокировать",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                    if (ok) vm.refresh()
                                }
                            },
                            onCopyLink = {
                                val link = "https://vk.com/clip${clip.ownerId}_${clip.id}"
                                val cm = nav.context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("VK Clip", link))
                                android.widget.Toast.makeText(
                                    nav.context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onToggleNotifications = {
                                vm.toggleNotifications(clip)
                                android.widget.Toast.makeText(
                                    nav.context,
                                    if (clip.isSubscribedToAuthor) "Уведомления выключены" else "Уведомления включены",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onHideAuthor = {
                                vm.hideAuthor(clip)
                                android.widget.Toast.makeText(
                                    nav.context, "Автор скрыт из ленты", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onEditClip = {
                                // §37.9: упрощённый flow — Toast, т.к. полноценный editor-screen за рамками #320.
                                android.widget.Toast.makeText(
                                    nav.context, "Редактирование скоро будет доступно", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDeleteClip = {
                                vm.deleteClip(clip)
                                android.widget.Toast.makeText(
                                    nav.context, "Клип удалён", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                }
                composable(Screen.ClipCreate.route) {
                    // §37.12 Phase 5: запись и публикация нового клипа.
                    re.pinok.ui.screens.clips.ClipCreateScreen(
                        onBack = {
                            if (!nav.popBackStack()) {
                                nav.navigate(Screen.Clips.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onPublished = { ownerId, videoId ->
                            // После успешной публикации — назад к ленте clips.
                            android.widget.Toast.makeText(
                                nav.context,
                                "Клип опубликован",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            nav.popBackStack(Screen.Clips.route, inclusive = false)
                        },
                    )
                }
                composable(Screen.Services.route)      { ServicesScreen() }
                composable(Screen.Notifications.route) { NotificationsScreen(
                        onPostClick = { ownerId, postId ->
                            PostHolder.last = re.pinok.data.model.Post(
                                ownerId = ownerId, fromId = ownerId, id = postId, date = 0, text = "",
                            )
                            nav.navigate(Screen.PostDetail.buildRoute(ownerId, postId))
                        },
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        // Audit #40: wired onActionReply — открывает чат с пользователем.
                        // onActionGiftReply остаётся null — экран подарков не реализован.
                        onActionReply = { targetUserId ->
                            nav.navigate(Screen.ChatDetail.buildRoute(targetUserId, "", null))
                        },
                    ) }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onOpenNotificationSettings = {
                            nav.navigate(Screen.NotificationSettings.route)
                        },
                        onOpenDevices = {
                            nav.navigate(Screen.Devices.route)
                        },
                    )
                }
                composable(Screen.NotificationSettings.route) {
                    NotificationSettingsScreen(onBack = { nav.popBackStack() })
                }
                // §49.6 Sprint VK-ID-1.2: Управление сессиями/устройствами аккаунта.
                composable(Screen.Devices.route) {
                    re.pinok.ui.screens.devices.DevicesScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                // #CALLS: экран звонка (входящий/исходящий/разговор).
                composable(
                    route = Screen.Call.route,
                    arguments = listOf(
                        navArgument(Screen.Call.ARG_PEER_ID) { type = NavType.LongType },
                        navArgument(Screen.Call.ARG_TITLE) { type = NavType.StringType; defaultValue = "Звонок" },
                        navArgument(Screen.Call.ARG_PHOTO) { type = NavType.StringType; defaultValue = "" },
                        navArgument(Screen.Call.ARG_INCOMING) { type = NavType.BoolType; defaultValue = false },
                        navArgument(Screen.Call.ARG_PAYLOAD) { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val peerId = entry.arguments?.getLong(Screen.Call.ARG_PEER_ID) ?: 0L
                    val title = entry.arguments?.getString(Screen.Call.ARG_TITLE) ?: "Звонок"
                    val photo = entry.arguments?.getString(Screen.Call.ARG_PHOTO)?.takeIf { it.isNotBlank() }
                    val incoming = entry.arguments?.getBoolean(Screen.Call.ARG_INCOMING) ?: false
                    val payload = entry.arguments?.getString(Screen.Call.ARG_PAYLOAD)?.takeIf { it.isNotBlank() }
                    re.pinok.ui.screens.calls.CallScreen(
                        peerId = peerId,
                        title = title,
                        photo = photo,
                        incoming = incoming,
                        onNavigateBack = { nav.popBackStack() },
                        incomingPayload = payload,
                    )
}
                composable(Screen.CallsWebView.route) {
                    re.pinok.ui.screens.calls.CallsWebViewScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Screen.About.route) { AboutScreen() }
                composable(Screen.Logs.route)          {
                    // Fix #114: «Закрыть» вместо «назад». popBackStack() может
                    // вернуть false, если Logs оказался startDestination
                    // (lastRoute="logs" мог быть сохранён до фикса #114).
                    // В этом случае явно навигируем на Feed — всегда работает.
                    LogScreen(onClose = {
                        if (!nav.popBackStack()) {
                            nav.navigate(Screen.Feed.route) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    })
                }
                composable(Screen.StoryViewer.route) { StoryViewerScreen(onBack = { nav.popBackStack() }) }
                composable(Screen.OfflineManager.route) {
                    OfflineManagerScreen(
                        onBack = { nav.popBackStack() },
                        // Fix #50: кнопка «Открыть плеер» в AudioOfflineTab ведёт
                        // на новый минималистичный офлайн-плеер (без сетевых запросов).
                        onOpenPlayer = { nav.navigate(Screen.OfflineAudioPlayer.route) },
                        onPlayVideo = { ownerId, videoId, title ->
                            // #34: Воспроизведение скачанного видео из офлайн-менеджера.
                            // Создаём минимальный Video объект — VideoPlayerScreen сам проверит
                            // VideoDownloadManager.getLocalFile() и проиграет локальный файл.
                            VideoHolder.open(Video(
                                id = videoId,
                                ownerId = ownerId,
                                title = title,
                                description = null,
                                duration = 0,
                                date = 0,
                            ))
                        },
                        // Fix #111: тап по story row в OfflineManager → StoryOfflinePlayerScreen.
                        // file:// URI, без сети, без CDN URL refresh.
                        onPlayStory = { ownerId, storyId ->
                            nav.navigate(Screen.StoryOfflinePlayer.buildRoute(ownerId, storyId))
                        },
                        // §37.12 #330: тап по clip row в OfflineManager → ClipOfflinePlayerScreen.
                        // file:// URI, без сети, TikTok-стиль 9:16 vertical (RESIZE_MODE_ZOOM).
                        onPlayClip = { ownerId, videoId ->
                            nav.navigate(Screen.ClipOfflinePlayer.buildRoute(ownerId, videoId))
                        },
                    )
                }
                // Fix #50: собственный офлайн аудиоплеер — открывается из
                // OfflineManagerScreen через onOpenPlayer. Читает скачанные треки
                // из TrackDownloadManager и текущий стейт из PlayerConnection.
                composable(Screen.OfflineAudioPlayer.route) {
                    OfflineAudioPlayerScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                // Этап 2 (#Equalizer): полноэкранный эквалайзер — 5 вкладок.
                // Открывается из кнопки в боковом drawer. Упрощённый EQ
                // остаётся в аудиоплеере (BottomSheet с пресетами + switch).
                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                // Fix #111: офлайн-просмотр скачанной видео-истории.
                // Читает локальный .mp4 через StoryVideoDownloadManager.getLocalFile()
                // и meta через getStoryMeta(). Воспроизведение через ExoPlayer с
                // file:// URI — полностью офлайн, без сети.
                composable(
                    route = Screen.StoryOfflinePlayer.route,
                    arguments = listOf(
                        navArgument(Screen.StoryOfflinePlayer.ARG_OWNER_ID) { type = NavType.LongType },
                        navArgument(Screen.StoryOfflinePlayer.ARG_STORY_ID) { type = NavType.IntType },
                    ),
                ) { entry ->
                    val ownerId = entry.arguments?.getLong(Screen.StoryOfflinePlayer.ARG_OWNER_ID) ?: 0L
                    val storyId = entry.arguments?.getInt(Screen.StoryOfflinePlayer.ARG_STORY_ID) ?: 0
                    StoryOfflinePlayerScreen(
                        ownerId = ownerId,
                        storyId = storyId,
                        onBack = { nav.popBackStack() },
                    )
                }
                // §37.12 #330: офлайн-просмотр скачанного клипа.
                // Читает локальный .mp4 через ClipVideoDownloadManager.getLocalFile()
                // и meta через getClipMeta(). Воспроизведение через ExoPlayer с
                // file:// URI, RESIZE_MODE_ZOOM (TikTok-стиль 9:16 vertical) — полностью
                // офлайн, без сети. Открывается из OfflineManager → «Клипы» tab.
                composable(
                    route = Screen.ClipOfflinePlayer.route,
                    arguments = listOf(
                        navArgument(Screen.ClipOfflinePlayer.ARG_OWNER_ID) { type = NavType.LongType },
                        navArgument(Screen.ClipOfflinePlayer.ARG_VIDEO_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val ownerId = entry.arguments?.getLong(Screen.ClipOfflinePlayer.ARG_OWNER_ID) ?: 0L
                    val videoId = entry.arguments?.getLong(Screen.ClipOfflinePlayer.ARG_VIDEO_ID) ?: 0L
                    ClipOfflinePlayerScreen(
                        ownerId = ownerId,
                        videoId = videoId,
                        onBack = { nav.popBackStack() },
                    )
                }
                // #90: VideoPlayer удалён из NavHost — теперь рендерится как overlay.
                // См. overlayVideo ниже после Scaffold.
                // Fix #67: экран сообщества.
                composable(
                    route = Screen.Community.route,
                    arguments = listOf(
                        navArgument(Screen.Community.ARG_GROUP_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val groupId = entry.arguments?.getLong(Screen.Community.ARG_GROUP_ID) ?: 0L
                    CommunityScreen(
                        groupId = groupId,
                        onBack = { nav.popBackStack() },
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        onPostClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        // Шаг 4 (#32d): тап по теме обсуждения → BoardTopicScreen.
                        onTopicClick = { gId, topicId, topicTitle ->
                            nav.navigate(Screen.BoardTopic.buildRoute(gId, topicId, topicTitle))
                        },
                    )
                }
                // Шаг 4 (#32d): экран темы обсуждения сообщества.
                composable(
                    route = Screen.BoardTopic.route,
                    arguments = listOf(
                        navArgument(Screen.BoardTopic.ARG_GROUP_ID) { type = NavType.LongType },
                        navArgument(Screen.BoardTopic.ARG_TOPIC_ID) { type = NavType.LongType },
                        navArgument(Screen.BoardTopic.ARG_TITLE) {
                            type = NavType.StringType
                            defaultValue = "Обсуждение"
                        },
                    ),
                ) { entry ->
                    val gId = entry.arguments?.getLong(Screen.BoardTopic.ARG_GROUP_ID) ?: 0L
                    val tId = entry.arguments?.getLong(Screen.BoardTopic.ARG_TOPIC_ID) ?: 0L
                    val tTitle = entry.arguments?.getString(Screen.BoardTopic.ARG_TITLE) ?: "Обсуждение"
                    BoardTopicScreen(
                        groupId = gId,
                        topicId = tId,
                        title = tTitle,
                        onBack = { nav.popBackStack() },
                    )
                }
                // Sprint 1, P0-2 (#74): экран чужого профиля.
                composable(
                    route = Screen.UserProfile.route,
                    arguments = listOf(
                        navArgument(Screen.UserProfile.ARG_USER_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val userId = entry.arguments?.getLong(Screen.UserProfile.ARG_USER_ID) ?: 0L
                    UserProfileScreen(
                        userId = userId,
                        onBack = { nav.popBackStack() },
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        onMessageClick = { peerId, title, photo ->
                            nav.navigate(Screen.ChatDetail.buildRoute(peerId, title, photo))
                        },
                        onPostClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                        // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
                        onCommentClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                    )
                }
                // Fix #71: экран детального просмотра поста.
                composable(
                    route = Screen.PostDetail.route,
                    arguments = listOf(
                        navArgument(Screen.PostDetail.ARG_OWNER_ID) { type = NavType.LongType },
                        navArgument(Screen.PostDetail.ARG_POST_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val ownerId = entry.arguments?.getLong(Screen.PostDetail.ARG_OWNER_ID) ?: -1L
                    val postId = entry.arguments?.getLong(Screen.PostDetail.ARG_POST_ID) ?: -1L
                    PostDetailScreen(
                        ownerId = ownerId,
                        postId = postId,
                        onBack = { nav.popBackStack() },
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        onGroupClick = { groupId ->
                            nav.navigate(Screen.Community.buildRoute(groupId))
                        },
                    )
                }
                composable(
                    route = Screen.ChatDetail.route,
                    arguments = listOf(
                        navArgument(Screen.ChatDetail.ARG_PEER_ID) { type = NavType.LongType },
                        navArgument(Screen.ChatDetail.ARG_TITLE) {
                            type = NavType.StringType
                            defaultValue = "Диалог"
                        },
                        navArgument(Screen.ChatDetail.ARG_PHOTO) {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        },
                    ),
                ) { entry ->
                    val peerId = entry.arguments?.getLong(Screen.ChatDetail.ARG_PEER_ID) ?: 0L
                    val title = entry.arguments?.getString(Screen.ChatDetail.ARG_TITLE) ?: "Диалог"
                    val photoRaw = entry.arguments?.getString(Screen.ChatDetail.ARG_PHOTO)
                    val photo = photoRaw?.takeIf { it.isNotBlank() }
                    ChatDetailScreen(
                        peerId = peerId,
                        peerTitle = try { java.net.URLDecoder.decode(title, "UTF-8") } catch (_: Exception) { title },
                        peerPhoto = photo?.let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } },
                        onBack = { nav.popBackStack() },
                        // Sprint 1, P0-2 (#74): тап по header чата → экран профиля.
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                        // P2.4: тап по wall-вложению в сообщении → PostDetailScreen.
                        onPostClick = { post ->
                            PostHolder.last = post
                            nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id))
                        },
                        // P2.1: тап по video-вложению в сообщении → VideoPlayer overlay.
                        onVideoClick = { video ->
                            VideoHolder.open(video)
                        },
                        // P2.2: тап по audio-вложению → запустить в PlayerConnection.
                        onAudioClick = { track ->
                            re.pinok.media.PlayerConnection.playTrackList(listOf(track), 0)
                        },
                        // P2.3: голосование в опросе → polls.addVote + reload history.
                        onPollVote = { poll, answerIds ->
                            scope.launch {
                                try {
                                    val ok = app.apiClient.pollsAddVote(poll.id, poll.ownerId, answerIds)
                                    if (ok) AppLog.i("SovaNavHost", "Poll vote ok: poll=${poll.id}")
                                    else AppLog.w("SovaNavHost", "Poll vote failed: poll=${poll.id}")
                                } catch (e: Exception) {
                                    AppLog.w("SovaNavHost", "Poll vote error: ${e.message}")
                                }
                            }
                        },
                        // P3.1: тап по «Информация о чате» → открыть ChatInfoScreen.
                        onInfoClick = { pid ->
                            nav.navigate(Screen.ChatInfo.buildRoute(pid))
                        },
                        // P5.1: открыть URL во внутреннем браузере (WebView).
                        // Внешний браузер обрабатывается внутри ChatDetailScreen.
                        onOpenUrlInternal = { url ->
                            nav.navigate(Screen.InternalBrowser.buildRoute(url))
                        },
                        // Fix #132: перед запуском камеры сохраняем параметры чата
                        // в rememberSaveable, чтобы при process death вернуться в чат.
                        onCameraLaunch = { pid, t, p ->
                            cameraReturnPeerId = pid
                            cameraReturnTitle = t
                            cameraReturnPhoto = p
                        },
                        // Fix #132: камера отработала — очищаем saved state.
                        onCameraReturnConsumed = {
                            cameraReturnPeerId = 0L
                            cameraReturnTitle = ""
                            cameraReturnPhoto = null
                        },
                        // #CALLS: кнопка «Позвонить» в шапке диалога → CallScreen.
                        // #ARCH-CONTAINERS (Этап 1.4): через CallStarter; null →
                        // кнопка не рендерится (см. ChatDetailScreen). title/photo
                        // чата — в OutgoingCallMeta (контракт их не передаёт).
                        onCallClick = callClick,
                    )
                }
                // P3.1: ChatInfoScreen — информация о чате (members / shared media / actions).
                composable(
                    route = Screen.ChatInfo.route,
                    arguments = listOf(
                        navArgument(Screen.ChatInfo.ARG_PEER_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    val peerId = entry.arguments?.getLong(Screen.ChatInfo.ARG_PEER_ID) ?: 0L
                    ChatInfoScreen(
                        peerId = peerId,
                        onBack = { nav.popBackStack() },
                        onUserClick = { userId ->
                            nav.navigate(Screen.UserProfile.buildRoute(userId))
                        },
                    )
                }
                // P3.3: FoldersSettingsScreen — управление папками диалогов.
                composable(
                    route = Screen.FoldersSettings.route,
                ) {
                    FoldersSettingsScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
                // P5.1: InternalBrowser — встроенный браузер (WebView) для ссылок из чата.
                composable(
                    route = Screen.InternalBrowser.route,
                    arguments = listOf(
                        navArgument(Screen.InternalBrowser.ARG_URL) { type = NavType.StringType },
                    ),
                ) { entry ->
                    val rawUrl = entry.arguments?.getString(Screen.InternalBrowser.ARG_URL).orEmpty()
                    InternalBrowserScreen(
                        url = rawUrl,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            }
        }

    }
    // #57: GlobalMiniPlayer перенесён обратно в bottomBar Scaffold (в Column
    // с NavigationBar). Overlay в Box убран — он перекрывал NavigationBar.
    // Sprint 1, P0-3 (#76): captcha dialog — overlay поверх всего app.
    CaptchaDialog()
    // #NET-SWITCH-POPUP (2026-08-03): popup при переключении сети.
    // Overlay поверх всего app (рядом с CaptchaDialog). Подписывается на
    // SovaApp.networkSwitchState + SovaPrefs.netSwitchPopupEnabled.
    // Навигация на Screen.OfflineManager (offline-менеджер) из кнопки popup.
    NetworkSwitchPopup(
        onOpenOfflineManager = { nav.navigate(Screen.OfflineManager.route) },
    )
    // #NETWORK-RESILIENCE (2026-08-04): OfflineBanner — ненавязчивый persistent
    // баннер внизу экрана. В отличие от NetworkSwitchPopup (модальный, default=off),
    // этот баннер ВСЕГДА включён — это informational status, не прерывание.
    // Слушает NetworkObserver.isOnlineFlow + networkSwitchState. Auto-dismiss
    // при появлении сети. Тап → OfflineManager (offline state) / retry (failed).
    // Размещён ПЕРЕД overlayVideo/overlayPhoto — full-screen video/photo должны
    // перекрывать баннер (когда видео на весь экран, offline-статус не нужен).
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 80.dp)  // поверх NavigationBar; если mini player виден — баннер чуть выше
            .padding(horizontal = 12.dp),
    ) {
        OfflineBanner(
            onOpenOfflineManager = { nav.navigate(Screen.OfflineManager.route) },
        )
    }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            },
        )
    }
    // #247: диалог выхода из приложения (сохраняя авторизацию).
    // onExitApp в MainActivity вызывает finishAffinity() — Android
    // закрывает все Activity в стеке. Процесс завершается, при следующем
    // запуске SovaApp.onCreate() находит сохранённый access_token в
    // tokenStorage → пользователь сразу попадает в ленту.
    if (showExitAppDialog) {
        AlertDialog(
            onDismissRequest = { showExitAppDialog = false },
            title = { Text("Выйти из приложения?") },
            text = {
                Text(
                    "Приложение будет закрыто. Авторизация сохранится — " +
                        "при следующем запуске вы сразу попадёте в ленту.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitAppDialog = false
                    onExitApp()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitAppDialog = false }) { Text("Отмена") }
            },
        )
    }
    // #90: VideoPlayer overlay — видео рендерится поверх всего UI.
    // Feed (и любой экран) остаётся живым в Composition → скролл не сбрасывается.
    overlayVideo?.let { video ->
        BackHandler(enabled = true) {
            VideoHolder.close()
        }
        VideoPlatformRouter(
            video = video,
            onBack = { VideoHolder.close() },
        )
    }
    // §42.4 #PUSH-DEEPLINK: PhotoViewer overlay — фото из push-уведомления
    // (like_photo/comment_photo) открывается нативно. PhotoViewer — Dialog,
    // рендерится поверх всего приложения с pinch-zoom + swipe между фото.
    overlayPhoto?.let { (photos, initial) ->
        BackHandler(enabled = true) {
            PhotoHolder.close()
        }
        PhotoViewer(
            photos = photos,
            initial = initial,
            onDismiss = { PhotoHolder.close() },
        )
    }
    } // Box
}

/**
 * Держатель видео для overlay-плеера (#90).
 * Видео открывается поверх текущего экрана без nav.navigate() —
 * текущий экран остаётся живым в Composition, скролл не теряется.
 */
object VideoHolder {
    @Volatile
    var last: Video? = null

    private val _active = MutableStateFlow<Video?>(null)
    val active = _active.asStateFlow()

    fun open(video: Video) {
        last = video
        _active.value = video
    }

    fun close() {
        _active.value = null
    }
}

/**
 * Fix #71: In-memory держатель последнего выбранного поста.
 * Используется, чтобы пробросить объект [re.pinok.data.model.Post]
 * на [PostDetailScreen] без сложной сериализации через arguments.
 */
object PostHolder {
    @Volatile
    var last: re.pinok.data.model.Post? = null
    // Fix #99: группы для отображения имени сообщества в PostDetailScreen.
    @Volatile
    var lastGroups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>? = null
}

/**
 * §42.4 #PUSH-DEEPLINK: in-memory держатель для полноэкранного PhotoViewer.
 *
 * По образцу [VideoHolder]: push-уведомление (like_photo/comment_photo) не
 * может передать список URL через NavHost-arguments (фото не имеет собственного
 * маршрута — PhotosScreen parameterless). Поэтому SovaNavHost при тапе на
 * photo-уведомление вызывает [PhotoHolder.open] с URL превью, а overlay-блок
 * рендерит [PhotoViewer] (Dialog с pinch-zoom + swipe) поверх всего UI.
 *
 * Текущий экран (Feed/Notifications/...) остаётся живым в Composition.
 */
object PhotoHolder {
    private val _active = MutableStateFlow<Pair<List<String>, Int>?>(null)
    val active = _active.asStateFlow()

    fun open(photos: List<String>, initial: Int = 0) {
        if (photos.isEmpty()) return
        _active.value = photos to initial
    }

    fun close() {
        _active.value = null
    }
}

/**
 * §42.4 #PUSH-DEEPLINK: target commentId для скролла в PostDetailScreen.
 *
 * Когда push-уведомление — «ответ на комментарий» / «новый комментарий»,
 * [VkUrlDeepLinker] строит OpenPost(ownerId, postId, commentId). SovaNavHost
 * перед nav.navigate(PostDetail) кладёт commentId сюда, а PostDetailScreen
 * в LaunchedEffect читает и сбрасывает (null) после скролла.
 *
 * Holder-паттерн (а не route-параметр) выбран чтобы не менять маршрут
 * Screen.PostDetail = "post_detail/{ownerId}/{postId}", который используется
 * в десятках мест навигации. Дополнительно переживает process death при
 * холодном старте из push (PendingIntent → onCreate).
 */
object PostDetailTarget {
    @Volatile
    var commentId: Long? = null
}

/** Fix #99: сохранение позиции скролла ленты при навигации. */
data class ScrollPosition(val index: Int, val offset: Int)

object FeedScrollHolder {
    @Volatile var position = ScrollPosition(0, 0)
}

/**
 * #POST-DETAIL-SCROLL: сохранение позиции скролла PostDetailScreen (комментарии)
 * при навигации к видео/сообществу и возврате. По образцу [FeedScrollHolder].
 *
 * PostDetailScreen использует LazyColumn для поста + комментариев.
 * При тапе на видео в комментарии → VideoHolder.open (overlay, composition
 * сохраняется) → позиция должна сохраниться. Но при тапе на сообщество →
 * nav.navigate(Community) → PostDetail LEAVES composition → позиция теряется.
 *
 * Этот holder + rememberSaveable(saver = LazyListState.Saver) решают проблему.
 */
object PostDetailScrollHolder {
    @Volatile var position = ScrollPosition(0, 0)
}

/**
 * Fix #100: In-memory кэш данных ленты — переживает навигацию к VideoPlayer
 * и обратно. Без этого `LaunchedEffect(Unit)` в FeedScreen перезапускается
 * при возврате (Compose Navigation отменяет корутину composition при уходе
 * с экрана), сбрасывает `loading=true` и `allPosts=emptyList()` → LazyColumn
 * не отрисовывается → позиция скролла теряется, лента прыгает в начало.
 *
 * По образцу [VideoHolder] / [PostHolder] — простой singleton-холдер.
 * Pull-to-refresh и создание поста очищают кэш через [clear].
 */
object FeedDataHolder {
    @Volatile var allPosts: List<re.pinok.data.model.Post>? = null
    @Volatile var profiles: Map<Long, re.pinok.data.model.UserProfile>? = null
    @Volatile var groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>? = null
    @Volatile var nextFrom: String? = null
    @Volatile var endReached: Boolean = false
    @Volatile var lastUpdated: Long = 0L

    /** Сохранить снимок состояния ленты (после каждой загрузки/пагинации). */
    fun snapshot(
        allPosts: List<re.pinok.data.model.Post>,
        profiles: Map<Long, re.pinok.data.model.UserProfile>,
        groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>,
        nextFrom: String?,
        endReached: Boolean,
    ) {
        this.allPosts = allPosts
        this.profiles = profiles
        this.groups = groups
        this.nextFrom = nextFrom
        this.endReached = endReached
        this.lastUpdated = System.currentTimeMillis()
    }

    /** Сбросить кэш (pull-to-refresh, создание поста, logout). */
    fun clear() {
        allPosts = null
        profiles = null
        groups = null
        nextFrom = null
        endReached = false
        lastUpdated = 0L
        // Позицию скролла тоже сбрасываем — после reload индексы могут не совпадать.
        FeedScrollHolder.position = ScrollPosition(0, 0)
    }
}

/**
 * Fix #52-B: In-memory кэш историй + dirtyKey триггер обновления.
 *
 * Проблема: StoriesRow.kt использовал `LaunchedEffect(Unit)` — грузил истории
 * ОДИН раз при первом composition. Когда пользователь уходил в CommunityScreen
 * (подписался/отписался от группы) и возвращался в ленту — StoriesRow НЕ
 * перекомпозировался (Feed в backstack, SavedStateHandle сохраняет state),
 * истории НЕ обновлялись. VK stories.get возвращает истории от подписанных
 * сообществ → после subscribe/unsubscribe состав историй меняется, но UI
 * показывал устаревший список.
 *
 * Решение (по образцу FeedDataHolder):
 * - `storyGroups`: кэш списка StoryGroup — переживает навигацию.
 * - `dirtyKey`: инкрементируется при любом событии которое должно триггерить
 *   обновление историй: (1) возврат на Feed route из другого экрана,
 *   (2) subscribe/unsubscribe в CommunityScreen, (3) создание/удаление истории.
 *   StoriesRow собирает dirtyKey в LaunchedEffect и перезагружает при изменении.
 * - `markDirty()`: публичный метод для явного триггера (вызывается из
 *   CommunityScreen после groupsJoin/groupsLeave).
 * - `clear()`: сброс кэша (logout, pull-to-refresh ленты).
 */
object StoriesHolder {
    @Volatile var storyGroups: List<re.pinok.data.model.StoryGroup>? = null
    @Volatile var lastUpdated: Long = 0L

    /**
     * Fix #88: заменено @Volatile var dirtyKey на MutableStateFlow.
     *
     * @Volatile гарантирует видимость между потоками, но НЕ триггерит
     * recomposition в Compose. Чтение `val restoreKey = StoriesHolder.dirtyKey`
     * в FeedScreen было «снимком значения на момент composition» — когда
     * SovaNavHost вызывал markDirty() при возврате с VideoPlayer, dirtyKey
     * менялся с 1 на 2, но Compose об этом не знал → LaunchedEffect(restoreKey)
     * не перезапускался → позиция скролла ленты не восстанавливалась.
     *
     * Теперь это StateFlow — collectAsState() в FeedScreen и StoriesRow
     * подписывается на изменения и триггерит recomposition.
     */
    private val _dirtyKey = MutableStateFlow(0)
    val dirtyKey: kotlinx.coroutines.flow.StateFlow<Int> = _dirtyKey.asStateFlow()

    /** Сохранить снимок историй (после каждой загрузки). */
    fun snapshot(groups: List<re.pinok.data.model.StoryGroup>) {
        storyGroups = groups
        lastUpdated = System.currentTimeMillis()
    }

    /**
     * Отметить истории как устаревшие — следующий composition StoriesRow
     * перезагрузит их. Вызывать при: subscribe/unsubscribe группы,
     * создании/удалении истории, возврате на Feed из детального экрана.
     */
    fun markDirty() {
        _dirtyKey.value++
    }

    /** Сбросить кэш (logout, pull-to-refresh ленты). */
    fun clear() {
        storyGroups = null
        lastUpdated = 0L
        _dirtyKey.value++  // триггерим перезагрузку
    }
}
