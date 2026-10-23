package re.pinok.ui.screens.feed

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Attachment
import re.pinok.data.model.Comment
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.PlayerConnection
import re.pinok.ui.navigation.PostHolder
import re.pinok.ui.navigation.Screen
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.AttachmentPickerTab
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.SkeletonFeedList
import re.pinok.ui.components.ErrorView
import re.pinok.ui.components.FeedRightPanel
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.ui.components.PostCard as UnifiedPostCard
import re.pinok.ui.components.PostMenuItem
import re.pinok.ui.components.VideoThumbnail
import re.pinok.ui.components.buildVkAttachment
import re.pinok.ui.navigation.FeedDataHolder
import re.pinok.ui.navigation.FeedScrollHolder
import re.pinok.ui.navigation.FeedPanelState
import re.pinok.ui.navigation.ScreenTopBar
import re.pinok.ui.navigation.ScrollPosition
import re.pinok.ui.theme.UiScale
import re.pinok.util.AppLog
import re.pinok.util.toCountString
import re.pinok.util.toDurationString
import java.io.File
import kotlin.math.roundToInt

/**
 * #FEED-FILTER: разделы ленты из VK web rightmenu — панель
 * `position: sticky; top: calc(var(--page-block-offset) + var(--header-height))`
 * на vk.ru/feed («Все новости / Рекомендации / Видео / Фото / Записи»).
 * ALL→newsfeed.get(filters=post,photo,video), RECOMMENDED→newsfeed.getRecommended,
 * остальные — newsfeed.get(filters=<тип>).
 */
// #FEED-FILTER: разделы из левой панели VK web (vk.ru/feed?section=*).
// ALL → newsfeed.get. RECOMMENDED → newsfeed.getRecommended.
// LIKES → likes.getList (5 подтабов: Все/Посты/Комментарии/Клипы/Видео).
// PHOTOS → client-side фильтр поверх ALL.
//   IMP-FEED-1 отклонение (п.6): server-side newsfeed.get(filters="photo")
//   НЕ подключён — с этим фильтром VK возвращает items type="photo"
//   (псевдо-посты без id/from_id поста), которые parseNewsfeedResponse
//   отбрасывает stub-гвардом (postId<=0) → таб стал бы пустым. Правка
//   потребовала бы менять VKA-парсер (в IMP-FEED-1 заморожен — единственный
//   допустимый дифф там: newsfeedGet sourceIds). Оставлен client-side фильтр.
// FRIENDS → лента постов друзей: newsfeed.get(source_ids из friends.get)
//   (IMP-FEED-1, снапшот §1.2: section=friends = посты друзей, НЕ «возможные
//   друзья»; блок рекомендаций над лентой УДАЛЕН — #FEED-MENU-VKWEB Fix #353,
//   в VK web раздел «Друзья» — чистая лента постов друзей).
// SEARCH → newsfeed.search.
// FEED-MENU-RESTORE: enum сделан public — FeedRightPanel использует для меню ленты
enum class FeedFilter(val label: String, val apiFilters: String?, val recommended: Boolean) {
    ALL("Все новости", "post,photo,video", false),
    RECOMMENDED("Рекомендации", null, true),
    LIKES("Реакции", null, false),
    PHOTOS("Фото", "post,photo,video", false),
    FRIENDS("Друзья", null, false),
    SEARCH("Поиск", null, false),
}

// #FEED-REACTIONS: подтабы для раздела «Реакции» (соответствует VK likes.getList type).
// IMP-FEED-1: WALL_REPLY («Комментарии») рендерит CommentCard из сырых items
// likes.getList(type="comment"); CLIPS/VIDEO оба type="video" — моб. API
// их неразличает (снапшот §3.3/§5.7, честное ограничение).
private enum class LikesFilter(val label: String, val apiType: String) {
    ALL("Все", "post"),
    WALL("Посты", "post"),
    WALL_REPLY("Комментарии", "comment"),
    CLIPS("Клипы", "video"),
    VIDEO("Видео", "video"),
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onVideoClick: (Video) -> Unit = {},
    // Fix #67: навигация на экран сообщества по тапу на header поста.
    onGroupClick: (Long) -> Unit = {},
    // Fix #71: навигация на детальный экран поста по тапу на текст/тело поста.
    onPostClick: (Post) -> Unit = {},
    // Sprint 1, P0-2 (#74): навигация на экран чужого профиля по тапу на header поста.
    onUserClick: (Long) -> Unit = {},
    // Stories: навигация на StoryViewerScreen.
    onStoryViewerClick: () -> Unit = {},
    // Офлайн-менеджер: кнопка «Офлайн» при отсутствии сети.
    onOpenOfflineManager: () -> Unit = {},
    // #FEED-MENU-VKWEB (Fix #353): «Редактировать» из правого меню ленты →
    // «Скрытые источники» (Screen.FeedHidden, newsfeed.getBanned + unban,
    // IMP-FEED-2). Эквивалент web-акта al_settings.php?act=a_edit_owners_list.
    onOpenHiddenSources: () -> Unit = {},
    // #FEED-MENU-VKWEB-2: навигация из правого меню ленты на другие разделы
    // (Уведомления/Мессенджер/Сообщества/Видео/Клипы/Музыка/Сервисы/Закладки/
    // Файлы). Хост (SovaNavHost) передаёт drawer-паттерн navigate на Screen.*.
    onFeedPanelNavigate: (Screen) -> Unit = {},
    // #FEED-MENU-NOTIF (2026-10-01): выбор КАТЕГОРИИ уведомлений в правом меню
    // ленты → NotificationsScreen с этой категорией (Screen.Notifications
    // buildRoute(category)). Хост (SovaNavHost) навигирует по этому маршруту.
    onFeedPanelOpenNotifications: (String) -> Unit = {},
    // #CALLS: кнопка «Позвонить» на карточке друга.
    // #ARCH-CONTAINERS (Этап 1.4): nullable — хост передаёт колбэк ТОЛЬКО если
    // в реестре есть CallStarter (контейнер звонков). null → кнопка НЕ рендерится
    // (условие композиции, graceful-деградация без контейнера).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
    // #PANEL-SEPARATE-REACTIONS (2026-10-18): автономный режим «только реакции».
    // Маршрут feed_likes открывает тот же FeedScreen, но рендерит ИСКЛЮЧИТЕЛЬНО
    // раздел LIKES (табы + контент реакций) БЕЗ обычной ленты, stories и их
    // фоновой загрузки — полное разделение «Ленты» и «Реакций» на уровне панели.
    // Обычный маршрут feed передаёт false.
    likesOnly: Boolean = false,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // Мониторинг сети для показа кнопки «Офлайн» при ошибке загрузки.
    val isOffline by app.networkObserver.isOnlineFlow.collectAsState(initial = !app.networkObserver.isOnline())

    // NEWS-TIMER: пока лента видима на экране, фоновый опрос новостей (CommunityPostsPoller)
    // пропускается — лента сама тянет свежие посты, нет смысла дублировать запросы newsfeed.get.
    androidx.compose.runtime.DisposableEffect(Unit) {
        re.pinok.FeedVisibility.isVisible = true
        onDispose {
            re.pinok.FeedVisibility.isVisible = false
        }
    }

    // Настройки фильтрации ленты — реагируем на изменения в реальном времени.
    val feedPrefs by app.prefs.data.collectAsState(
        initial = re.pinok.data.local.SovaPrefs.Snapshot(
            newsAdsBlocked = true,
            newsRepostsHidden = false,
            newsPromoHidden = true,
            newsCollapsePostText = false,
            // NEWS-TIMER/AUTO-OFF: интервал фонового опроса и тумблер отключения автообновления.
            newsPollIntervalSec = 180,
            newsAutoRefreshOff = false,
            themeDark = true,
            themeAccentIndex = 0,
            themeDynamic = false,
            // #MONET-HYBRID: themeMonetHybrid добавлен в Snapshot — FeedScreen
            // тоже должен передавать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'themeMonetHybrid'».
            // (Тот же класс бага что Fix #100 / #110 / #189 — Snapshot расширился.)
            // Default = true (соответствует default в SovaPrefs).
            // Реальное значение подгрузится из SovaPrefs при первом collect.
            themeMonetHybrid = true,
            fontScale = 100,
            // Fix #224: скорость анимаций (default 100% = норма).
            interfaceAnimSpeed = 100,
            // BOTTOMBAR-FIX: фиксация нижней панели (default true = не скрывается скроллом).
            uiFixBottomBar = true,
            // HOME-SCREEN: настройка «Главный экран» (default выключена, дом = Лента).
            uiHomeEnabled = false,
            uiHomeRoute = "",
            // Fix #228: масштаб стикер-фото (default 0% = исходный размер).
            stickerPhotoScale = 0,
            privacyOfflineMode = false,
            // Fix #360 #PROFILE-SUGGEST-TOGGLE: Snapshot расширился — dummy-snapshot
            // обязан передать новое поле (класс бага Fix #276/#356). Default false
            // синхрон с SovaPrefs (секция «Возможно, вы знакомы» скрыта по умолчанию).
            profileFriendSuggestions = false,
            // Fix #DEFAULTS-OFF (2026-08-04): default=true → false (синхрон с SovaPrefs).
            privacyDeviceMask = false,
            privacyAntiTelemetry = true,
            // Fix #HIDE-LAST-SEEN-DEAD (2026-08-04): default=true → false (синхрон с SovaPrefs).
            // Настройка НЕ РАБОТАЕТ (accountSetOnline не вызывается).
            privacyHideLastSeen = false,
            msgDnr = true,
            msgDnt = true,
            msgUndelete = true,
            msgUnedit = true,
            // P0.1: typing indicator default ON (safe, doesn't break anything).
            msgTypingIndicator = true,
            // P0.3: pinned message bar default ON (safe — только для group chats).
            msgPinBar = true,
            // P1.3: message grouping default ON (safe — только визуальное объединение).
            msgGrouping = true,
            // P1.1: date separators + unread divider + scroll-to-bottom FAB (default ON).
            msgDateSeparators = true,
            msgUnreadDivider = true,
            msgScrollFab = true,
            // P1.2: reply via swipe (default ON).
            msgSwipeReply = true,
            // P2.6: read receipts ✓/✓✓ (default ON).
            msgReadReceipts = true,
            // P1.4: search + tabs в MessagesScreen (default ON).
            msgSearch = true,
            // #MSG-TAB-TOGGLES (2026-10-16): вкладки «Чаты»/«Каналы» в MessagesScreen — ON по умолчанию.
            msgShowChatsTab = true,
            msgShowChannelsTab = true,
            // P0.29: multi-select mode — default ON (включено по умолчанию).
            msgMultiSelect = true,
            // P3.5: multi-file upload — до 10 фото за раз (default ON).
            msgMultiFile = true,
            // P3.6: dual send/mic button — state machine (opt-in — default OFF).
            msgDualButton = false,
            // P3.2: mute/unmute chat — toggle уведомлений (default ON).
            msgMute = true,
            // P3.1: ChatInfo screen — отдельный экран информации о чате (default ON).
            msgChatInfo = true,
            // P3.4: channel mode — отдельный UX для каналов (default ON).
            msgChannelMode = true,
            // P3.3: folders system (default false — opt-in, экспериментально).
            msgFolders = false,
            msgFoldersData = "",
            // Fix #276: pinnedConvsData — локальные закреплённые диалоги (JSON).
            // FeedScreen не использует это поле, но Snapshot расширился в Fix #276,
            // поэтому нужно передать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'pinnedConvsData'».
            // (Тот же класс бага что Fix #100 / #110 / #189 — Snapshot расширился.)
            pinnedConvsData = "",
            // Fix #356 #MSG-ARCHIVE: archivedConvsData — локальные архивные диалоги
            // (JSON). FeedScreen не использует это поле, но Snapshot расширился —
            // передаём initial-значение (тот же класс бага, что Fix #276 выше).
            archivedConvsData = "",
            // Fix #392 #IM-LOCAL-PIN: imPinnedDialogs — peer_id локально закреплённых
            // диалогов (List<Long>, уже распарсен в SovaPrefs). FeedScreen не использует
            // это поле, но Snapshot расширился — передаём initial-значение (тот же
            // класс бага, что Fix #276/#356 выше: «No value passed for parameter»).
            imPinnedDialogs = emptyList(),
            // P3.7: bubble-less дизайн (default false — opt-in, экспериментально).
            msgBubbleless = false,
            // Sprint 5 (P4.1–P4.4): новые поля Snapshot — должны передаваться
            // здесь тоже, иначе компилятор падает: «No value passed for parameter
            // 'msgLpBackfill'» и т.д. (Fix #100 / #110 — тот же класс бага).
            // Все opt-in (default false) — кроме lpLastTs/lpLastPts (0L = нет состояния).
            msgLpBackfill = false,
            msgLpV14 = false,
            // §52.5 Sprint A (P0): Modern Sync API (default false — opt-in).
            msgModernSync = false,
            msgExecuteBatch = false,
            msgWsChannels = false,
            lpLastTs = 0L,
            lpLastPts = 0L,
            // P5.1: открытие ссылок из чата во внутреннем браузере (default false).
            openLinksInInternalBrowser = false,
            cacheSizeMb = 0L,
            cacheCustomPath = "",
            musicDownloadPath = "/Music/PinoK/",
            videoDownloadPath = "",
            musicHighQuality = true,
            musicBackgroundPlay = true,
            // #OFFLINE-TAB: audioFormat добавлен в Snapshot — FeedScreen тоже
            // должен передавать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'audioFormat'».
            // (Тот же класс бага что Fix #100/#110/#189/#237/#302/#337/#monet-hybrid.)
            // Default M4A — MediaMuxer работает из коробки, MP3 opt-in.
            audioFormat = re.pinok.data.local.AudioFormat.M4A,
            // §42.12 P1 #3 / P2 #8 / P2 #9 / P3 #11: новые поля Snapshot
            // для metadata tags / Genius lyrics / промо / метод конвертации.
            // FeedScreen не использует их, но Snapshot расширился — нужны defaults.
            writeId3Tags = true,
            writeGeniusLyrics = false,
            writePromoComment = false,
            audioConvertMethod = "siren_transcoder",
            numTracksInPlaylist = true,
            // §334: videoPreferredQuality — предпочтительное качество видео для
            // плеера и клипов. FeedScreen не использует это поле, но Snapshot
            // расширился — нужно передать initial-значение, иначе компилятор падает.
            videoPreferredQuality = "auto",
            // Fix #100 (этап 5): stories prefs — добавлены в Snapshot,
            // но FeedScreen не обновил initial-значение. Без этого компилятор
            // падает: «No value passed for parameter 'autoCacheStories'».
            // Fix #AUTOCACHE-STORIES-OFF (2026-08-04): default=false (см. SovaPrefs).
            autoCacheStories = false,
            storyCacheLimitMb = 200,
            // #STORY-TOGGLE: storiesShown добавлен в Snapshot — FeedScreen
            // тоже должен передавать initial-значение, иначе компилятор
            // падает: «No value passed for parameter 'storiesShown'».
            storiesShown = true,
            // Fix #110: autoCacheAudio добавлен в Snapshot — FeedScreen
            // тоже должен передавать initial-значение, иначе компилятор
            // падает: «No value passed for parameter 'autoCacheAudio'».
            // Fix #AUTOCACHE-AUDIO-OFF (2026-08-05): default=false (см. SovaPrefs).
            autoCacheAudio = false,
            netSslPinning = false,
            netAwayBypass = true,
            netAdBlock = true,
            // Task #Web-API: web.api.vk.ru toggle — default OFF (api.vk.com).
            netUseWebApiGateway = false,
            // P1.1c #URL-LOG: default OFF — 0 шума. Toggle в NETWORK-вкладке SettingsScreen.
            netLogApiUrls = false,
            netProxyEnabled = false,
            netProxyHost = "",
            netProxyPort = 8080,
            lockerEnabled = false,
            lockerPinHash = "",
            lockerBiometric = false,
            // Fix #DEFAULTS-OFF (2026-08-04): default=true → false (синхрон с SovaPrefs).
            lockerOnBackground = false,
            // Fix #189: настраиваемые VK домены (.com/.ru) + web client_id.
            // Snapshot получил 7 новых auth-полей — FeedScreen должен передавать
            // initial-значения, иначе компилятор падает:
            // «No value passed for parameter 'authOauthHost'» и т.д.
            // (Тот же класс бага что Fix #100 / #110 — Snapshot расширился.)
            authOauthHost = "oauth.vk.com",
            authIdHost = "id.vk.com",
            authLoginHost = "login.vk.com",
            authMobileWebHost = "m.vk.ru",
            authApiHost = "api.vk.com",
            authWebClientId = "6287487",
            authForceRevoke = false,
            // #SETTINGS-DOMAINS (2026-10-14): Snapshot расширился — FeedScreen
            // обязан передать initial-значения новых доменов (тот же класс бага
            // Fix #100/#110/#189: «No value passed for parameter»).
            authWebApiHost = "web.api.vk.ru",
            authUserapiHost = "userapi.com",
            authMediaCdnHost = "vkuserphoto.ru",
            authOkCdnHost = "okcdn.ru",
            authMyCdnHost = "mycdn.me",
            authQueuev4Host = "queuev4.vk.ru",
            authEhHost = "eh.vk.ru",
            authVkDomain = "vk.ru",
            // #SETTINGS-DEVTAB (2026-10-14): глобальный kill-switch звонков (default false) —
            // FeedScreen должен передать initial-значение, иначе Snapshot не соберётся.
            callsEnabled = false,
            // #AUTH2: «Авторизация 2» — альтернативный флоу входа (default on, как в SovaPrefs).
            authV2Enabled = true,
            // Fix #237: showLogFab добавлен в Snapshot — FeedScreen тоже должен
            // передавать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'showLogFab'».
            // (Тот же класс бага что Fix #100 / #110 / #189 — Snapshot расширился.)
            // #LOG-FAB-DEFAULT-OFF (14.09): default = false (значок скрыт во всех
            // сборках) — соответствует default в SovaPrefs.
            showLogFab = false,
            // #LOG-CATEGORIES (2026-08-04): logCategoriesDisabled добавлен в
            // Snapshot — FeedScreen тоже должен передавать initial-значение,
            // иначе компилятор падает:
            // «No value passed for parameter 'logCategoriesDisabled'».
            // (Тот же класс бага что Fix #100 / #110 / #189 / #237 — Snapshot расширился.)
            // #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): default =
            // NON_CRITICAL_CATEGORY_NAMES — включены только AUTH+SYSTEM+NETWORK.
            // Реальное значение подгрузится из SovaPrefs при первом collect.
            logCategoriesDisabled = re.pinok.util.AppLog.NON_CRITICAL_CATEGORY_NAMES,
            // #238: показ FAB «подняться в верх ленты» (default true).
            // FeedScreen — единственный потребитель этой настройки.
            feedShowScrollFab = true,
            // #FEED-FILTER-TOGGLE: показывать панель разделов ленты (default true).
            feedShowFilter = false,
            // #FEED-CAROUSEL (19-A): карусель фото в ленте (default true — как в SovaPrefs).
            // Snapshot расширился — FeedScreen обязан передать initial-значение
            // (тот же класс бага что Fix #100/#110/#189/#237/#302/#337).
            feedCarouselEnabled = true,
            // #MSG-FAVORITES-TOGGLE: показывать «Избранное» в чатах (default true).
            msgShowFavorites = false,
            // #NET-SWITCH-POPUP (2026-08-04): netSwitchPopupEnabled добавлен в
            // Snapshot — FeedScreen тоже передаёт initial.
            // (Тот же класс бага что Fix #100/#110/#189/#237/#302/#337 — Snapshot расширился.)
            // #NET-POPUP-DEFAULT-ON (14.09): default = true (popup виден по умолчанию).
            netSwitchPopupEnabled = true,
            lastRoute = "feed",
            // Fix #302 (Task 2-b): notifyCacheJson — пустая строка (= нет кэша),
            // реальные значения подгрузятся из SovaPrefs при первом же collect.
            notifyCacheJson = "",
            // P3.1: emailNotifyFreq удалён из Snapshot — DEAD STORE (UI не читал,
            // EmailNotifyCard переведён на серверный BFF page=email).
            // Fix #337: редактор панелей — порядок/видимость кнопок. FeedScreen
            // не использует эти поля, но Snapshot расширился — нужно передать
            // initial-значения (тот же класс бага что Fix #100/#110/#189/#302).
            // Реальные значения подгрузятся из SovaPrefs при первом collect.
            sidebarItemsOrder = re.pinok.data.local.SovaPrefs.SIDEBAR_DEFAULT_ORDER,
            sidebarItemsHidden = "[]",
            bottomBarItemsOrder = re.pinok.data.local.SovaPrefs.BOTTOMBAR_DEFAULT_ORDER,
            bottomBarItemsHidden = "[]",
            // FEED-FIX-4 (#349): externalVideosEnabled — Default true.
            // Пользователь явно запросил «тумблер включён по умолчанию».
            // VK-видео играет нативно (ExoPlayer) всегда, флаг на VK НЕ влияет.
            // При true: OK-видео → нативный OkVideoRepository (ExoPlayer, без
            // рекламы, без WebView — не влияет на авторизацию); YouTube/external
            // → OkWebViewPlayer (WebView с блокировкой рекламы).
            // Реальное значение подгрузится из SovaPrefs при первом collect.
            // (Тот же класс бага что Fix #100/#110/...)
            externalVideosEnabled = true,
            // #VIDEO-AUTOPLAY: автовоспроизведение видео при открытии (default true).
            // FeedScreen не использует это поле, но Snapshot расширился — нужно
            // передать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'videoAutoplay'».
            // (Тот же класс бага что Fix #100/#110/#189/#237/#302/#337/#monet-hybrid.)
            videoAutoplay = true,
            // Fix #VIDEO-BLOCK-ADS: блокировка рекламных видео (default true).
            // FeedScreen не использует это поле, но Snapshot расширился — нужно
            // передать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'videoBlockAds'».
            // Реальное значение подгрузится из SovaPrefs при первом collect.
            videoBlockAds = true,
            // §42 #PUSH-NOTIFICATIONS: 12 новых полей в Snapshot для локальных
            // push-уведомлений VK-событий (лайки/комментарии/репосты/ответы/
            // подписки/упоминания/подарки/стена). FeedScreen не использует эти
            // поля напрямую — они нужны только чтобы initial-Snapshot компилировался.
            // Реальные значения подгрузятся из SovaPrefs при первом же collect
            // (data flow заменит initial). Defaults совпадают с SovaPrefs.snapshot().
            // (Тот же класс бага что Fix #100/#110/#189/#237/#302/#337/#monet-hybrid.)
            pushEnabled = true,
            pushLikes = true,
            pushComments = true,
            pushReplies = true,
            pushFollows = true,
            pushMentions = true,
            pushReposts = true,
            pushWall = true,
            pushGifts = true,
            pushOther = true,
            pushPollingIntervalSec = 120,
            pushLastSeenKeys = "",
            // §42.2 #PUSH-ENHANCED: 16 расширенных полей. Defaults совпадают
            // с SovaPrefs.snapshot(). FeedScreen не использует их напрямую —
            // нужны только для компиляции initial-Snapshot (collectAsState
            // заменит реальными значениями при первом collect).
            pushAutoDismissMs = 0L,
            pushPreviewMode = "full",
            pushPreviewLength = 80,
            pushQuietHoursEnabled = false,
            pushQuietHoursStart = 1320,
            pushQuietHoursEnd = 480,
            pushShowAvatar = true,
            pushShowBigPicture = true,
            pushSoundEnabled = true,
            pushVibrationEnabled = true,
            pushLedColor = 0,
            pushGroupingMode = "category",
            pushGroupThreshold = 3,
            pushActionButtons = true,
            // §46 #REMOTE-INPUT: default true для preview/dummy snapshot.
            pushReplyButton = true,
            pushPerUserMuted = "",
            pushShowDelayMs = 0L,
            // §42.3 #PUSH-SOURCE-FILTER: source-filter defaults.
            pushFromCommunities = true,
            pushFromUsers = true,
            // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): default true.
            pushSafetyNetAlerts = true,
            safetyNetPollIntervalMin = 10,
            callsQueueKey = "",
            callsQueueTs = 0L,
            callsSessionKey = "",
            callsSessionUid = 0L,
            callsCallToken = "",
            // #CALLS-VIDEO-RX (Этап 1): callsVideoRx добавлен в Snapshot — FeedScreen
            // тоже должен передавать initial-значение (тот же класс бага, что
            // Fix #100 / #110 / #189 / #monet-hybrid). Default true — как в SovaPrefs.
            callsVideoRx = true,
            // #CALLS-SYMMETRIC / #CALLS-SWDECODE (01.09): initial-значения — как в SovaPrefs.
            callsVideoTx = false,
            callsVideoSwDecode = false,
            // #MUSIC2-TOGGLE: music2Enabled добавлен в Snapshot — FeedScreen тоже
            // обязан передавать initial-значение (тот же класс бага, что callsVideoRx).
            // Default false — как в SovaPrefs (контейнер «Музыка 2» выключен).
            music2Enabled = false,
            // #CALLS-AEC-TOGGLE: callsEchoCancel добавлен в Snapshot БЕЗ дефолта —
            // initial-конструкция обязана передавать его (тот же класс бага, что
            // Fix #100 / #110 / #189 / #monet-hybrid). Default true — как в SovaPrefs.
            callsEchoCancel = true,
            // #UISCALE: uiScale добавлен в Snapshot — initial-конструкция
            // обязана передавать его (тот же класс бага, что Fix #100/#110/#189).
            // Default 0.85f — как в SovaPrefs.
            uiScale = re.pinok.data.local.SovaPrefs.UI_SCALE_DEFAULT,
            audioQuality = re.pinok.data.local.AudioQuality.Q192,
            // P0.18: storiesSwipeHints добавлен в Snapshot — initial-конструкция
            // обязана передавать его (default true, как в SovaPrefs).
            storiesSwipeHints = true,
            // #VIDEO-QUALITY-MOBILE-LIMIT: mobileVideoQualityLimit добавлен в
            // Snapshot — initial-конструкция обязана передавать его (тот же
            // класс бага, что Fix #100 / #110 / #189 / #monet-hybrid).
            // FeedScreen не использует это поле напрямую — реальное значение
            // подгрузится из SovaPrefs при первом collect. Default "480".
            mobileVideoQualityLimit = "480",
        )
    )

    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    // Все загруженные посты (до клиентской фильтрации).
    // Используем separate state чтобы фильтр не терял посты при переключении настроек.
    // Fix #100: инициализируем из FeedDataHolder — переживает навигацию к VideoPlayer.
    var allPosts by remember { mutableStateOf(FeedDataHolder.allPosts ?: emptyList()) }
    var profiles by remember { mutableStateOf(FeedDataHolder.profiles ?: emptyMap()) }
    var groups by remember { mutableStateOf(FeedDataHolder.groups ?: emptyMap()) }
    // Skeleton показывается только при самом первом запуске (кэш пуст).
    // При возврате из VideoPlayer — кэш есть, loading=false, LazyColumn сразу отрисовывается.
    var loading by remember { mutableStateOf(FeedDataHolder.allPosts == null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Защита от флуда: один одновременный запрос ленты
    var feedJobRunning by remember { mutableStateOf(false) }
    // Sprint 1, P0-4 (#77): пагинация ленты через newsfeed.get start_from/next_from.
    var nextFrom by remember { mutableStateOf(FeedDataHolder.nextFrom) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(FeedDataHolder.endReached) }  // nextFrom==null && уже загружали

    // #FEED-FILTER: активный раздел ленты (VK rightmenu). Храним имя enum-а в
    // rememberSaveable — переживает навигацию (VideoPlayer/PostDetail) и поворот.
    // #PANEL-SEPARATE-REACTIONS: в автономном режиме (маршрут feed_likes) фиксируем
    // раздел LIKES — экран показывает ТОЛЬКО реакции, без переключателя и обычной
    // ленты. В обычном режиме (маршрут feed) — раздел ALL (раздел LIKES из ленты
    // убран; см. FeedFilterBar ниже).
    var feedFilterName by rememberSaveable {
        mutableStateOf(if (likesOnly) FeedFilter.LIKES.name else FeedFilter.ALL.name)
    }
    val feedFilter = runCatching { FeedFilter.valueOf(feedFilterName) }.getOrDefault(FeedFilter.ALL)

    // #FEED-FILTER-SEARCH: поисковый запрос для вкладки «Поиск».
    var feedSearchQuery by remember { mutableStateOf("") }

    // #FEED-REACTIONS: подтаб внутри «Реакций» (Все/Посты/Комментарии/Клипы/Видео).
    var likesFilterName by rememberSaveable { mutableStateOf(LikesFilter.ALL.name) }
    val likesFilter = runCatching { LikesFilter.valueOf(likesFilterName) }.getOrDefault(LikesFilter.ALL)
    var likesItems by remember { mutableStateOf<List<Post>>(emptyList()) }
    var likesLoading by remember { mutableStateOf(false) }
    var likesTotalCount by remember { mutableIntStateOf(0) }
    // IMP-FEED-1: группы из wallGetById для списка «Реакций» (кнопка
    // подписки в PostCard — состояние из GroupInfo.isMember, если VK его отдал).
    var likesGroups by remember { mutableStateOf<Map<Long, VKApiClient.GroupInfo>>(emptyMap()) }

    // #FEED-LIKES-COMMENTS (IMP-FEED-1, п.3): лайкнутые комментарии
    // (likes.getList type="comment") + профили авторов (usersGetByIds).
    var likedComments by remember { mutableStateOf<List<LikedComment>>(emptyList()) }
    var likedCommentAuthors by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }

    // #FEED-LIKES-PHOTOS (IMP-FEED-1, п.4): лайкнутые фото (likes.getList
    // type="photo"). Пагинация: offset += страница; hasMore = страница полная.
    var likedPhotos by remember { mutableStateOf<List<LikedPhoto>>(emptyList()) }
    var likedPhotosTotal by remember { mutableIntStateOf(0) }
    var likedPhotosOffset by remember { mutableIntStateOf(0) }
    var likedPhotosHasMore by remember { mutableStateOf(false) }
    var likedPhotosLoadingMore by remember { mutableStateOf(false) }

    // Fix #364 #FEED-REACTIONS-VIDEO: лайкнутые видео/клипы (подтабы «Клипы»/
    // «Видео»). likes.getList(type="video") отдаёт только owner_id/item_id —
    // полные объекты (превью/длительность/название) догружаются
    // video.get(videos=…) → сюда. Сбрасывается при каждой загрузке вкладки.
    var likesVideos by remember { mutableStateOf<List<Video>>(emptyList()) }

    // §FEED-REACTIONS-NEWFEED: пагинация ленты реакций (newsfeed.getLikesFeed).
    // Курсор из response.next_from (likesNextFrom); hasMore = есть следующая
    // страница. Основной загрузчик (loadReactions) сбрасывает их на первой
    // странице, loadMoreReactions() дочитывает через start_from=likesNextFrom.
    var likesNextFrom by remember { mutableStateOf("") }
    var likesHasMore by remember { mutableStateOf(false) }
    var likesLoadingMore by remember { mutableStateOf(false) }

    // #FEED-SUBSCRIBE (IMP-FEED-1, п.2): оптимистичное состояние подписки
    // на авторов постов (key = fromId). Значение: true → «Отписаться»,
    // false → «Подписаться». ОТСУТСТВУЕТ в мапе = состояние неизвестно →
    // PostCard рисует дефолт «Подписаться» (честно: VK не отдаёт состояние
    // подписки на юзера в newsfeed.get; для групп is_member запрошен в
    // fields, но parseNewsfeedResponse его в GroupInfo не кладёт — только
    // fallback-путь parseGroupsJsonArray).
    val subscribeState = remember { mutableStateMapOf<String, Boolean>() }
    // «Пожаловаться» / подписка работают только с ЧУЖИХ записей — нужен
    // свой id (UserProfileScreen-паттерн: exchangeAuthRepository.userId()).
    val myUserId = remember { app.exchangeAuthRepository.userId() }

    // ══ П-7-AB (IMP-FEED-1, п.1): «Пожаловаться» на пост ленты ══
    // Паттерн 1:1 из ProfileScreen.reportWallPostConfirmed (wall.markAsSpam):
    // state-цель + in-flight флаг + AlertDialog подтверждения (низ экрана).
    val reportingPost = remember { mutableStateOf<Post?>(null) }
    var reportInFlight by remember { mutableStateOf(false) }
    fun reportWallPostConfirmed(target: Post) {
        if (reportInFlight) return
        reportInFlight = true
        scope.launch {
            val ok = try {
                app.apiClient.wallMarkAsSpam(ownerId = target.ownerId, postId = target.id)
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "wallMarkAsSpam failed", e)
                false
            }
            reportInFlight = false
            reportingPost.value = null
            android.widget.Toast.makeText(
                app.applicationContext,
                if (ok) "Жалоба отправлена"
                else (app.apiClient.lastApiError ?: "Не удалось отправить жалобу"),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // #FEED-SUBSCRIBE (IMP-FEED-1, п.2): подписка на автора поста.
    // Юзер (fromId>0) → newsfeed.subscribe/unsubscribe(type="wall", ownerId=fromId,
    // itemId=fromId); сообщество (fromId<0) → groups.join/leave(-fromId).
    // Оптимистично: состояние в [subscribeState], ошибка → откат + тост lastApiError.
    fun toggleSubscribe(post: Post) {
        val key = post.fromId.toString()
        if (post.fromId == 0L || post.fromId == myUserId) return
        val current = subscribeState[key]
            ?: if (post.fromId < 0) (likesGroups[-post.fromId] ?: groups[-post.fromId])?.let { it.isMember == 1 } else null
        val target = !(current ?: false)
        subscribeState[key] = target // оптимистично
        scope.launch {
            val ok = if (target) {
                if (post.fromId < 0) app.apiClient.groupsJoin(-post.fromId)
                else app.apiClient.newsfeedSubscribe(type = "wall", ownerId = post.fromId, itemId = post.fromId)
            } else {
                if (post.fromId < 0) app.apiClient.groupsLeave(-post.fromId)
                else app.apiClient.newsfeedUnsubscribe(type = "wall", ownerId = post.fromId, itemId = post.fromId)
            }
            if (!ok) {
                // Откат: absent-состояние дефолтит в false («Подписаться») —
                // ровно то, что пользователь видел до тапа.
                subscribeState[key] = current ?: false
                android.widget.Toast.makeText(
                    app.applicationContext,
                    app.apiClient.lastApiError ?: "Не удалось изменить подписку",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // #FEED-LIKES-PHOTOS (IMP-FEED-1, п.4): загрузка страницы лайкнутых фото.
    // reset=true — первая страница (offset 0); иначе offset += последняя страница.
    fun loadLikedPhotos(reset: Boolean) {
        if (likedPhotosLoadingMore) return
        val page = 30
        val offset = if (reset) 0 else likedPhotosOffset
        scope.launch {
            likedPhotosLoadingMore = true
            try {
                val (total, items) = app.apiClient.likesGetList(type = "photo", count = page, offset = offset)
                val parsed = items.mapNotNull { parseLikedPhoto(it) }
                likedPhotosTotal = total
                likedPhotosOffset = offset + items.size
                likedPhotos = if (reset) parsed
                else (likedPhotos + parsed).distinctBy { "${it.ownerId}_${it.photoId}" }
                // hasMore = страница была ПОЛНОЙ (items.size) И не дошли до total (Pair.first).
                likedPhotosHasMore = items.size >= page && likedPhotos.size < total
                AppLog.i("FeedScreen", "likesGetList(photo): $total total, +${parsed.size} parsed (offset=$offset)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "likesGetList(photo) failed", e)
            } finally {
                likedPhotosLoadingMore = false
            }
        }
    }

    // #FEED-REACTIONS-REFRESH (2026-10-02): счётчик-перезапуск загрузки реакций.
    // Проблема: refreshFeed()/reloadFeed() идут через fetchFeedPage (пусто для
    // LIKES) и НЕ перезапускали отдельный загрузчик реакций (LaunchedEffect был
    // завязан только на feedFilterName/likesFilterName) → pull-to-refresh и кнопка
    // «Обновить» в разделе «Реакции» ничего не делали. Инкремент этого ключа
    // перезапускает LaunchedEffect(feedFilterName, likesFilterName, reactionsReloadKey)
    // → loadReactions() выполняется заново без смены раздела/подтаба.
    // #FEED-REACTIONS-COUNTS: empty-состояния используют likesTotalCount, чтобы
    // отличать «лайкнутых нет вовсе» (count==0) от «нагрузка есть, но разрешить
    // объекты не удалось» (count>0, но items пустые).
    var reactionsReloadKey by remember { mutableIntStateOf(0) }

    // Загрузка реакций (newsfeed.getLikesFeed). Выделена из LaunchedEffect в
    // отдельную suspend-функцию с собственным likesLoading-гардом, чтобы её
    // можно было перезапускать при pull-to-refresh (reactionsReloadKey++), не
    // полагаясь на смену раздела/подтаба.
    // IMP-FEED-1: подтаб «Комментарии» (WALL_REPLY) → type="comment" возвращает
    // ПОЛНЫЕ объекты комментариев (id/from_id/text/date/likes.count) — рендерим
    // CommentCard, а не wallGetById-посты.
    // §FEED-REACTIONS-NEWFEED: источник данных переключён с likes.getList
    // (список ЮЗЕРОВ конкретного поста) на newsfeed.getLikesFeed (лента реакций).
    // likesFeedGet возвращает (count, nextFrom, сырые items) — по подтабу
    // выбираем like_types и маршрутизируем items в likesItems/likesVideos/
    // likedComments. Курсор likesNextFrom + likesHasMore для пагинации.
    // Ядро загрузки одной страницы реакций. reset-режим (startFrom="") очищает
    // списки; append-режим добавляет следующую страницу и дедуплицирует.
    // По подтабу выбирается like_types: «Все» — все типы (посты кладём в
    // likesItems, остальные типы рендер «Все» не показывает); «Посты»/«Комменты»/
    // «Клипы»/«Видео» — соответствующий тип.
    suspend fun loadReactionsCore(startFrom: String, append: Boolean) {
        val likeTypes = when (likesFilter) {
            LikesFilter.ALL -> "post,comment,video,clip,market"
            LikesFilter.WALL -> "post"
            LikesFilter.WALL_REPLY -> "comment"
            LikesFilter.CLIPS -> "clip"
            LikesFilter.VIDEO -> "video"
        }
        val (totalCount, nextFrom, items) = app.apiClient.likesFeedGet(
            count = 30,
            startFrom = startFrom,
            likeTypes = likeTypes,
        )
        likesTotalCount = totalCount
        likesNextFrom = nextFrom
        if (!append) {
            likesHasMore = false
        }

        when {
            // Подтаб «Комментарии»: сырые items (или вложенный блок comment)
            // → LikedComment patient-парсинг. Авторы — отдельным usersGetByIds.
            likesFilter == LikesFilter.WALL_REPLY -> {
                if (!append) {
                    likedComments = emptyList()
                    likedCommentAuthors = emptyMap()
                }
                val parsed = items.mapNotNull { likesFeedCommentObject(it)?.let { c -> parseLikedComment(c) } }
                likedComments = if (append) (likedComments + parsed).distinctBy { "${it.ownerId}_${it.commentId}" }
                else parsed
                // Авторов догружаем только для НОВЫХ комментариев страницы
                // (append-режим не перезапрашивает уже загруженных).
                val newFromIds = parsed.map { it.fromId }.filter { it > 0 }.distinct()
                val missingAuthors = newFromIds.filterNot { likedCommentAuthors.containsKey(it) }
                if (missingAuthors.isNotEmpty()) {
                    likedCommentAuthors = likedCommentAuthors + app.apiClient.usersGetByIds(missingAuthors)
                }
                likesHasMore = nextFrom.isNotBlank() && parsed.isNotEmpty()
                AppLog.i("FeedScreen", "likesFeedGet(comment): $totalCount total, nextFrom=$nextFrom, ${likedComments.size} parsed")
            }
            // Подтабы «Клипы»/«Видео»: owner_id+item_id → video.get(videos=…)
            // → полные Video (превью/длительность). Моб. API клипы/видео не
            // различает — обе вкладки честно работают через videoFeed.
            likesFilter == LikesFilter.CLIPS || likesFilter == LikesFilter.VIDEO -> {
                if (!append) {
                    // Сброс всех состояний подтабов: ветка постов ниже НЕ гейтится
                    // по likesFilter (else-цепочка рендера) — не убрав likesItems,
                    // получили бы посты прошлой вкладки на «Клипах».
                    likesItems = emptyList()
                    likesGroups = emptyMap()
                    likedComments = emptyList()
                    likedCommentAuthors = emptyMap()
                    likesVideos = emptyList()
                }
                // NULL-ЯВНО: элементы likesFeed могут не иметь owner_id/item_id
                // (или JsonNull) — likesFeedItemTarget возвращает null для битых.
                val newVideoIds = items.mapNotNull { likesFeedItemTarget(it) }
                    .map { (ownerId, itemId) -> "${ownerId}_${itemId}" }
                    .distinct()
                // video.get возвращает только ДОСТУПНЫЕ видео (удалённые/приватные
                // VK молча пропускает) — результат может быть меньше newVideoIds.
                val newVideos = if (newVideoIds.isEmpty()) emptyList()
                else app.apiClient.videoGet(newVideoIds)
                likesVideos = if (append) (likesVideos + newVideos).distinctBy { "${it.ownerId}_${it.id}" }
                else newVideos
                likesHasMore = nextFrom.isNotBlank() && newVideoIds.isNotEmpty()
                AppLog.i("FeedScreen", "likesFeedGet(video): $totalCount total, nextFrom=$nextFrom, ${likesVideos.size} videos loaded")
            }
            // Подтабы «Все»/«Посты»: из items берём только post-типы и догружаем
            // через wall.getById; «Все» запрашивает все like_types, но рендер
            // раздела «Все» показывает только посты (остальные типы пропускаем).
            else -> {
                if (!append) {
                    likesItems = emptyList()
                    likesGroups = emptyMap()
                    likedComments = emptyList()
                    likedCommentAuthors = emptyMap()
                    likesVideos = emptyList()
                }
                val postItems = items.filter { it.get("type")?.takeIf { !it.isJsonNull }?.asString == "post" }
                val postIds = postItems.mapNotNull { likesFeedItemTarget(it) }
                if (postIds.isNotEmpty()) {
                    val result = app.apiClient.wallGetById(postIds)
                    likesItems = if (append) (likesItems + result.posts).distinctBy { "${it.ownerId}_${it.id}" }
                    else result.posts
                    likesGroups = if (append) likesGroups + result.groups else result.groups
                    likesHasMore = nextFrom.isNotBlank() && postIds.isNotEmpty()
                    AppLog.i("FeedScreen", "likesFeedGet: $totalCount total, nextFrom=$nextFrom, ${likesItems.size} posts loaded")
                } else {
                    if (!append) {
                        likesItems = emptyList()
                        likesGroups = emptyMap()
                    }
                    likesHasMore = false
                }
            }
        }
    }

    // §FEED-REACTIONS-NEWFEED: первая страница реакций (сброс, start_from="").
    suspend fun loadReactions() {
        if (likesLoading) return
        likesLoading = true
        try {
            loadReactionsCore(startFrom = "", append = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("FeedScreen", "loadReactions error", e)
        } finally {
            likesLoading = false
        }
    }

    // §FEED-REACTIONS-NEWFEED: пагинация реакций — дочитать следующую страницу
    // через start_from=likesNextFrom (курсор response.next_from). Триггер —
    // loadMoreReactionsLauncher (скролл к концу раздела, аналог loadMore ленты).
    fun loadMoreReactions() {
        if (likesLoading || likesLoadingMore || !likesHasMore || likesNextFrom.isBlank()) return
        scope.launch {
            likesLoadingMore = true
            try {
                loadReactionsCore(startFrom = likesNextFrom, append = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "loadMoreReactions error", e)
            } finally {
                likesLoadingMore = false
            }
        }
    }

    // Запуск загрузки реакций при входе в раздел «Реакции», смене подтаба ИЛИ
    // перезапуске через reactionsReloadKey++ (refresh/reload).
    LaunchedEffect(feedFilterName, likesFilterName, reactionsReloadKey) {
        if (feedFilterName == FeedFilter.LIKES.name) {
            loadReactions()
        }
    }

    // #FEED-LIKES-PHOTOS (IMP-FEED-1): первая страница лайкнутых фото —
    // только на подтабе «Все» (снапшот §1.5: feed_likes_tabs_photo — НЕ таб,
    // а блок «Фотографии | Показать все» внутри вкладки «Все»).
    // reactionsReloadKey в ключах: pull-to-refresh (refreshFeed) инкрементирует
    // ключ → блок «Понравившиеся фото» тоже перезагружается с первой страницы
    // (раньше guard likedPhotos.isEmpty() блокировал повторную загрузку уже
    // заполненного блока). LikesFilterChangeReset guard остаётся от параллельных
    // запусков.
    LaunchedEffect(feedFilterName, likesFilterName, reactionsReloadKey) {
        if (feedFilterName == FeedFilter.LIKES.name &&
            likesFilterName == LikesFilter.ALL.name &&
            !likedPhotosLoadingMore
        ) {
            loadLikedPhotos(reset = true)
        }
    }

    // Загрузка одной страницы ленты по текущему разделу.
    // Читает feedFilterName напрямую (не через derived feedFilter) — так при
    // смене раздела в onSelect (feedFilterName = f.name; reloadFeed()) запрос
    // уходит уже с НОВЫМ фильтром, не дожидаясь рекомпозиции.
    suspend fun fetchFeedPage(count: Int, startFrom: String?): VKApiClient.NewsfeedResult {
        val f = runCatching { FeedFilter.valueOf(feedFilterName) }.getOrDefault(FeedFilter.ALL)
        when {
            f.recommended -> return app.apiClient.newsfeedGetRecommended(count, startFrom)
            f == FeedFilter.LIKES -> {
                return VKApiClient.NewsfeedResult(emptyList(), emptyMap(), emptyMap(), null)
            }
            f == FeedFilter.FRIENDS -> {
                // #FEED-FRIENDS-FEED (IMP-FEED-1, п.5): «Друзья» = лента постов
                // друзей (снапшот §1.2: vk.ru/feed?section=friends — посты
                // друзей, НЕ «возможные друзья»). Моб. аналог: newsfeed.get с
                // source_ids из friends.get (аддитивный параметр newsfeedGet).
                // ЛИМИТ VK API: source_ids ≤ 100 id — берём первых 100 друзей
                // (friends.get c count=1000, сортировка order=hints как везде);
                // при >100 друзей лента покрывает первых 100 — задокументированное
                // ограничение (выше требует execute-батчей, вне скоупа IMP-FEED-1).
                // Ошибка/пусто friendsGet → пустой NewsfeedResult: caller
                // (reloadFeed/refreshFeed) покажет честное состояние
                // «Ошибка API: …»/«Лента пуста» через lastApiError-гейт.
                val friendIds = try {
                    app.apiClient.friendsGet(count = 1000).map { it.id }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("FeedScreen", "friendsGet for friends feed failed", e)
                    emptyList()
                }
                if (friendIds.isEmpty()) {
                    return VKApiClient.NewsfeedResult(emptyList(), emptyMap(), emptyMap(), null)
                }
                return app.apiClient.newsfeedGet(
                    count = count,
                    startFrom = startFrom,
                    filters = "post,photo,video",
                    sourceIds = friendIds.distinct().take(100),
                )
            }
            f == FeedFilter.SEARCH -> {
                if (feedSearchQuery.isBlank()) return VKApiClient.NewsfeedResult(emptyList(), emptyMap(), emptyMap(), null)
                val (_, posts) = app.apiClient.newsfeedSearch(feedSearchQuery, count)
                return VKApiClient.NewsfeedResult(posts, emptyMap(), emptyMap(), null)
            }
        }
        return app.apiClient.newsfeedGet(count, startFrom, f.apiFilters ?: "post,photo,video")
    }

    // Состояния лайков и комментариев (mutable state map по post key).
    // Храним локально — оптимистичное обновление UI.
    val likesState = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }  // key → (isLiked, count)
    val commentingPost = remember { mutableStateOf<Post?>(null) }  // диалог комментариев
    // Fix #51-B: rememberSaveable(saver = LazyListState.Saver) — официальная
    // рекомендация Android Developers (https://developer.android.com/develop/ui/compose/state-saving).
    // Переживает: (1) навигацию (NavBackStackEntry SavedStateHandle сохраняет
    // state когда Feed покидает composition при переходе на VideoPlayer),
    // (2) process death (SavedStateHandle персистится в Bundle),
    // (3) config changes (rotation). Раньше rememberLazyListState() терял
    // позицию при возврате из VideoPlayer — пользователь видел ленту с начала.
    //
    // feedReloadKey: инкрементируется при reload/refresh → rememberSaveable
    // пересоздаёт LazyListState с (0,0). Без этого после reload список posts
    // другой, а rememberSaveable восстановил бы СТАРЫЙ index → позиция указала
    // бы на другой пост или вышла за границы списка.
    var feedReloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberSaveable(feedReloadKey, saver = LazyListState.Saver) { LazyListState() }
    // Флаг: scroll restore выполнен — только после этого начинаем сохранять позицию.
    // Без этого snapshotFlow запишет (0,0) при первой отрисовке LazyColumn
    // и перетрёт сохранённую позицию до того, как scrollRestore эффект успеет её прочитать.
    var scrollRestored by remember { mutableStateOf(false) }

    // Fix #100: явно фиксируем позицию скролла ПЕРЕД уходом с экрана (на видео/пост/профиль/группу).
    // snapshotFlow в LaunchedEffect ниже асинхронный — между последним скроллом и кликом
    // может не успеть записать актуальную позицию. Эта обёртка гарантирует сохранение.
    //
    // Fix #114: скипаем sticky header (StoriesRow, index 0) — он ВСЕГДА в
    // visibleItemsInfo на offset 0, поэтому firstOrNull() возвращал (0,0)
    // независимо от реальной позиции скролла. Из-за этого при возврате из
    // StoryViewer/VideoPlayer лента всегда скидывалась в начало.
    //
    // #FEED-FILTER-REMOVED (2026-08-01): source-фильтр (Все/Друзья/Группы)
    // убран по запросу пользователя. В LazyColumn снова только один sticky
    // header (StoriesRow, index 0). Берём первый видимый item с index > 0.
    fun saveScrollPosition() {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index > 0 }
        FeedScrollHolder.position = if (first != null) {
            ScrollPosition(first.index, first.offset)
        } else {
            // Постов не видно (лента пустая/грузится) — сохраняем «верх».
            ScrollPosition(0, 0)
        }
        AppLog.d("FeedScreen", "saveScrollPosition: pos=(${FeedScrollHolder.position.index},${FeedScrollHolder.position.offset})")
    }
    val onVideoClickSavePos: (Video) -> Unit = { v -> saveScrollPosition(); onVideoClick(v) }
    val onPostClickSavePos: (Post) -> Unit = { p -> saveScrollPosition(); onPostClick(p) }
    val onGroupClickSavePos: (Long) -> Unit = { g -> saveScrollPosition(); onGroupClick(g) }
    val onUserClickSavePos: (Long) -> Unit = { u -> saveScrollPosition(); onUserClick(u) }
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    // photoViewerState = Pair<urls, initialIndex>.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Sprint 2, P1-3 → ShareSheet: расширенный диалог «Поделиться».
    val sharePost = remember { mutableStateOf<Post?>(null) }
    // P0.46-unify-comments: шаринг комментария (wall_reply{ownerId}_{cid}).
    // В FeedScreen используется в подтабе «Реакции» → «Комментарии».
    // Comment не содержит ownerId стены — храним отдельно (для likes.getList
    // type=comment ownerId = owner стены, к которой привязан комментарий).
    val sharingComment = remember { mutableStateOf<Comment?>(null) }
    val sharingCommentOwnerId = remember { mutableStateOf(0L) }
    // P0.46-unify-comments: id текущего юзера (для isOwnComment в CommentItem).
    val currentUserId = remember { app.tokenStorage.load()?.userId ?: 0L }
    // #FEED-RIGHTPANEL (19-A): правое боковое меню ленты открыто.
    // Вместо локального remember — глобальный FeedPanelState.visible (см.
    // ScreenTopBar.kt): кнопка ⋮ живёт в глобальном TopAppBar (другая композиция),
    // и closure кнопки должен писать в тот же state, что читает панель, независимо
    // от инстанса FeedScreen. Иначе при пересоздании экрана клик «теряется в никуда».

    // Fix #363 #FEED-MENU-TOPBAR-2: кнопка «меню ленты» на ГЛОБАЛЬНОЙ верхней
    // панели. SovaNavHost рендерит TopAppBar вне зоны FeedScreen и подставляет
    // ScreenTopBar.actions в слот actions (прецедент Fix #256/#260/#262:
    // NotificationsScreen/FriendsScreen/GroupsScreen/VideoScreen). Раньше кнопка
    // была в закреплённой строке над лентой (Fix #359) — она отъедала высоту
    // контента; теперь лента занимает весь экран (см. Column ниже).
    //
    // Токен-паттерн: configure() возвращает токен-владельца, clear(token) в
    // onDispose сносит конфигурацию ТОЛЬКО если это всё ещё наша конфигурация —
    // при навигации Feed → X: X.configure() → Feed.onDispose.clear(tokenFeed)
    // = no-op, конфиг X не затирается.
    //
    // Ключ DisposableEffect(Unit): лямбда actions только ПИШЕТ FeedPanelState.visible
    // (глобальный state — захват не зависит от инстанса экрана), изменчивых
    // читаемых значений нет — переконфигурация при рекомпозиции не нужна (в
    // отличие от NotificationsScreen, где ключ = showSearch/showFilters/unreadCount).
    //
    // #FEED-MENU-TOPBAR-2 (Fix #366): надпись «меню ленты» рядом со значком ⋮.
    // Подпись рисуется ВНУТРИ actions-слота (ScreenTopBar.actions): гейт по
    // «активен ли экран ленты» обеспечивается автоматически — actions
    // конфигурируются только FeedScreen'ом (при уходе clear(). сбрасывает).
    // При ограниченном месте (узкий экран/сплит-экран, screenWidthDp < 320) —
    // показываем только иконку.
    DisposableEffect(Unit) {
        val token = ScreenTopBar.configure(
            actions = {
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (screenWidthDp >= 320) {
                        Text(
                            text = "меню ленты",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = {
                        AppLog.i("FeedScreen", "⋮ menu clicked → FeedPanelState.visible = true")
                        FeedPanelState.visible = true
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Меню ленты",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        onDispose { ScreenTopBar.clear(token) }
    }

    // Клиентская фильтрация ленты по настройкам.
    // VK API частично фильтрует рекламу (тип=ads пропускается в VKApiClient),
    // но marked_as_ads посты всё равно приходят. Репосты и промо тоже не
    // фильтруются сервером — только клиентом.
    //
    // #30g (feed fix): РАНЬШЕ posts обновлялся только в LaunchedEffect(feedPrefs).
    // Когда reloadFeed обновлял allPosts — posts НЕ пересчитывался (LaunchedEffect
    // не срабатывал т.к. feedPrefs не менялся) → лента показывалась пустой.
    // Теперь: posts = filteredList вычисляется КАЖДЫЙ раз когда allPosts ИЛИ
    // feedPrefs меняются.
    // #FEED-FILTER-REMOVED (2026-08-01): source-фильтр (Все/Друзья/Группы)
    // убран по запросу пользователя. Раньше было два эффекта
    // (allPosts→baseFilteredPosts→posts±sourceFilter), теперь — один: posts
    // = allPosts после ad/repost/promo фильтрации, без source-разделения.
    LaunchedEffect(allPosts, feedPrefs, feedFilterName) {
        // #FEED-FILTER-FIX: PHOTOS → посты с фото-вложениями (клиентский фильтр).
        // LIKES → теперь через likes.getList API (выше), не клиентская фильтрация.
        posts = allPosts.filter { post ->
            if (feedPrefs.newsAdsBlocked && post.isAd) return@filter false
            if (feedPrefs.newsRepostsHidden && !post.copyHistory.isNullOrEmpty()) return@filter false
            if (feedPrefs.newsPromoHidden && (post.postType == "promo" || post.postType == "ad_promo")) return@filter false
            when (feedFilterName) {
                FeedFilter.PHOTOS.name -> post.attachments?.any { it.type == "photo" } == true
                else -> true
            }
        }
    }

    // Функция перезагрузки ленты — используется после создания поста и при pull-to-refresh.
    fun reloadFeed() {
        if (feedJobRunning) return
        feedJobRunning = true
        // Fix #100: сбрасываем кэш + позицию — после reload индексы могут не совпадать.
        FeedDataHolder.clear()
        scrollRestored = false
        // Fix #51-B: инкрементируем feedReloadKey → rememberSaveable пересоздаёт
        // LazyListState с (0,0). Также сбрасываем FeedScrollHolder (backup механизм).
        feedReloadKey++
        FeedScrollHolder.position = ScrollPosition(0, 0)
        // Fix #52-B: сбрасываем кэш историй при reload/refresh — dirtyKey++
        // триггерит перезагрузку в StoriesRow (LaunchedEffect(dirtyKey)).
        re.pinok.ui.navigation.StoriesHolder.clear()
        // #FEED-REACTIONS-REFRESH: см. refreshFeed — в разделе «Реакции» reload
        // тоже должен перезапускать отдельный загрузчик реакций (не только
        // fetchFeedPage, который для LIKES возвращает пусто). Перезапуск даже
        // когда смена раздела уже дёрнула LaunchedEffect — безвреден: у
        // loadReactions собственный likesLoading-гард от параллельных запусков.
        if (feedFilterName == FeedFilter.LIKES.name) {
            reactionsReloadKey++
        }
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val result = fetchFeedPage(30, null)
                // Fix #53: защитная дедупликация на уровне UI — даже если API вернёт
                // дубликаты, LazyColumn не упадёт с "Key X was already used".
                allPosts = result.posts
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                profiles = result.profiles
                groups = result.groups
                // DIAG: логируем мапу групп и проверяем каждый пост
                AppLog.d("FeedScreen", "reloadFeed: groups map keys=${groups.keys}, size=${groups.size}")
                allPosts.forEach { post ->
                    if (post.fromId < 0) {
                        val gKey = -post.fromId
                        val g = groups[gKey]
                        AppLog.d("FeedScreen", "  POST_LOOKUP: fromId=${post.fromId} groupKey=$gKey found=${g != null} name=${g?.name ?: "NULL"}")
                    }
                }
                // Sprint 1, P0-4 (#77): курсор пагинации.
                nextFrom = result.nextFrom
                if (nextFrom == null) endReached = true
                allPosts.forEach { post ->
                    val key = "${post.ownerId}_${post.id}"
                    post.likes?.let { l ->
                        likesState[key] = (l.userLikes == 1) to l.count
                    }
                }
                // Fix #100: синхронизируем кэш для следующего возврата из VideoPlayer.
                FeedDataHolder.snapshot(allPosts, profiles, groups, nextFrom, endReached)
                // Fix #358 #FEED-SECTION-EMPTY-GATE: разделы Реакции (LIKES) и Поиск
                // (SEARCH) УМЫШЛЕННО возвращают пустой NewsfeedResult из fetchFeedPage
                // (Реакции грузятся отдельным LaunchedEffect через likes.getList,
                // Поиск ждёт запрос по полю ввода). Ставить сюда «Лента пуста» =
                // убивать гейтом (errorText != null && posts.isEmpty()) весь UI
                // раздела — поле поиска и подтабы Реакций становились недостижимы
                // («в меню ленты поиск и реакции не работает ни то не другое»).
                // Пусто в этих разделах — НОРМАЛЬНОЕ состояние; свои ошибки их
                // loader'ы показывают сами (likesLoading/apiErrorMessage).
                if (allPosts.isEmpty() &&
                    feedFilterName != FeedFilter.LIKES.name &&
                    feedFilterName != FeedFilter.SEARCH.name
                ) {
                    val err = app.apiClient.lastApiError
                    errorText = if (err != null) "Ошибка API: $err" else "Лента пуста"
                }
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "reloadFeed failed", e)
            } finally {
                loading = false
                feedJobRunning = false
            }
        }
    }

    // Fix #377 #DOZE-RESUME-RELOAD (H3 из разведки 26-1-d): после глубокого сна
    // экраны кроме музыки не перезагружаются на resume. Лента «застревает» на
    // ошибке: LaunchedEffect(Unit) первичной загрузки НЕ перезапускается
    // (композиция пережила сон), а cache-guard (FeedDataHolder.allPosts != null)
    // пропускает повторную загрузку. Сеть к моменту возврата уже поднялась,
    // но никто не перечитывает ленту — помогает только свернуть/развернуть app.
    //
    // Паттерн 1:1 MusicScreen (Fix #367: авто-перезагрузка при возврате сети),
    // расширенный foreground-тиком из SovaApp.foregroundTicks (инкремент в
    // ProcessLifecycleOwner ON_RESUME — немедленно, без debounce).
    // Условия перезагрузки: экран в ошибочном состоянии (errorText != null),
    // постов нет, сеть реально есть (isOffline=false — значение читается из
    // состояния в момент emission), авто-офлайн (#38) не активен — иначе
    // reloadFeed молча вернёт пусто и ошибка перезапишется впустую.
    // reloadFeed сам защищён feedJobRunning-гардом от параллельных запусков.
    LaunchedEffect(Unit) {
        // (1) Возврат в foreground после сна — немедленная перезагрузка.
        launch {
            app.foregroundTicks.collect { tick ->
                if (tick <= 0) return@collect
                val hasError = errorText != null
                if (hasError && posts.isEmpty() && !isOffline && !feedPrefs.privacyOfflineMode) {
                    AppLog.i("FeedScreen", "foregroundTick=$tick — лента с ошибкой и без постов, авто-reload (#DOZE-RESUME-RELOAD)")
                    reloadFeed()
                }
            }
        }
        // (2) Восстановление сети, пока экран открыт с ошибкой — как в MusicScreen.
        var wasOnline = app.networkObserver.isOnline()
        app.networkObserver.isOnlineFlow.collect { online ->
            if (online && !wasOnline && errorText != null && posts.isEmpty() && !feedPrefs.privacyOfflineMode) {
                AppLog.i("FeedScreen", "сеть восстановилась при открытой ошибке — авто-reload (#DOZE-RESUME-RELOAD)")
                reloadFeed()
            }
            wasOnline = online
        }
    }

    // Sprint 1, P0-4 (#77): pull-to-refresh — перезагрузка ленты с spinner'ом.
    fun refreshFeed() {
        if (feedJobRunning) return
        feedJobRunning = true
        // Fix #100: pull-to-refresh сбрасывает кэш + позицию.
        FeedDataHolder.clear()
        scrollRestored = false
        // Fix #51-B: инкрементируем feedReloadKey → rememberSaveable пересоздаёт
        // LazyListState с (0,0). Также сбрасываем FeedScrollHolder (backup механизм).
        feedReloadKey++
        FeedScrollHolder.position = ScrollPosition(0, 0)
        // Fix #52-B: сбрасываем кэш историй при reload/refresh — dirtyKey++
        // триггерит перезагрузку в StoriesRow (LaunchedEffect(dirtyKey)).
        re.pinok.ui.navigation.StoriesHolder.clear()
        // #FEED-REACTIONS-REFRESH: в разделе «Реакции» fetchFeedPage возвращает
        // пусто (раздел грузит отдельным загрузчиком loadReactions). Инкремент
        // reactionsReloadKey перезапускает LaunchedEffect(feedFilterName,
        // likesFilterName, reactionsReloadKey) → реакций/фото перезагружаются
        // при pull-to-refresh, иначе обновление ничего не делало (сам класс
        // бага, что Fix #358: раздел «застревал» на старых данных).
        if (feedFilterName == FeedFilter.LIKES.name) {
            reactionsReloadKey++
        }
        scope.launch {
            isRefreshing = true
            try {
                val result = fetchFeedPage(30, null)
                allPosts = result.posts
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                profiles = result.profiles
                groups = result.groups
                nextFrom = result.nextFrom
                endReached = (nextFrom == null)
                allPosts.forEach { post ->
                    val key = "${post.ownerId}_${post.id}"
                    post.likes?.let { l ->
                        likesState[key] = (l.userLikes == 1) to l.count
                    }
                }
                // Fix #100: синхронизируем кэш.
                FeedDataHolder.snapshot(allPosts, profiles, groups, nextFrom, endReached)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("FeedScreen", "refreshFeed failed: ${e.message}")
            } finally {
                isRefreshing = false
                feedJobRunning = false
            }
        }
    }

    // Sprint 1, P0-4 (#77): пагинация — загрузка следующей страницы.
    fun loadMore() {
        if (loadingMore || endReached || nextFrom == null) return
        scope.launch {
            loadingMore = true
            try {
                val result = fetchFeedPage(30, nextFrom)
                val newPosts = result.posts
                    .filter { it.id > 0 && it.ownerId != 0L }
                    // Дедупликация против уже загруженных.
                    .filter { np -> allPosts.none { it.ownerId == np.ownerId && it.id == np.id } }
                if (newPosts.isNotEmpty()) {
                    allPosts = (allPosts + newPosts).distinctBy { "${it.ownerId}_${it.id}" }
                    // Мёржим profiles/groups — новые могут добавить неизвестных авторов.
                    profiles = profiles + result.profiles
                    groups = groups + result.groups
                    newPosts.forEach { post ->
                        val key = "${post.ownerId}_${post.id}"
                        post.likes?.let { l ->
                            likesState[key] = (l.userLikes == 1) to l.count
                        }
                    }
                }
                nextFrom = result.nextFrom
                // Конец списка задаёт ТОЛЬКО серверный курсор nextFrom (== null).
                // НЕ ставим конец по newPosts.isEmpty(): пустая страница после
                // дедуп-фильтра ещё не значит, что постов больше нет — nextFrom
                // указывает на следующую страницу, по которой догрузим дальше.
                if (nextFrom == null) endReached = true
                // Fix #100: синхронизируем кэш после пагинации.
                FeedDataHolder.snapshot(allPosts, profiles, groups, nextFrom, endReached)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // scope отменён — нормально при пересоздании Activity
            } catch (e: Exception) {
                AppLog.w("FeedScreen", "loadMore failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // #PANEL-SEPARATE-REACTIONS: в автономном режиме (feed_likes) обычная лента
        // не грузится вообще — раздел реакций тянет свой LaunchedEffect(feedFilterName,
        // likesFilterName, reactionsReloadKey). Ранний выход исключает фоновый fetchFeedPage.
        if (likesOnly) return@LaunchedEffect
        // Fix #100: первичная загрузка только если кэша НЕТ.
        // При возврате из VideoPlayer FeedDataHolder уже содержит allPosts —
        // LazyColumn сразу отрисуется с восстановленной позицией, без skeleton.
        // LaunchedEffect(Unit) перезапускается каждый раз при возвращении composition
        // в активное состояние (Compose Navigation отменяет корутину при уходе с экрана),
        // поэтому без этой проверки лента будет перезагружаться каждый возврат.
        if (FeedDataHolder.allPosts != null) {
            AppLog.d("FeedScreen", "LaunchedEffect(Unit): cache exists — skip load")
            return@LaunchedEffect
        }
        // FIX: используем корутину LaunchedEffect напрямую вместо scope.launch,
        // чтобы избежать ForgottenCoroutineScopeException при пересоздании Activity
        // (например, после авторизации).
        loading = true
        endReached = false
        errorText = null
        try {
            val result = fetchFeedPage(30, null)
            // Fix #53: защитная дедупликация на уровне UI.
            allPosts = result.posts
                .filter { it.id > 0 && it.ownerId != 0L }
                .distinctBy { "${it.ownerId}_${it.id}" }
            profiles = result.profiles
            groups = result.groups
            // Sprint 1, P0-4 (#77): курсор пагинации.
            nextFrom = result.nextFrom
            if (nextFrom == null) endReached = true
            // Инициализируем состояние лайков из данных постов.
            allPosts.forEach { post ->
                val key = "${post.ownerId}_${post.id}"
                post.likes?.let { l ->
                    likesState[key] = (l.userLikes == 1) to l.count
                }
            }
            // Fix #358 #FEED-SECTION-EMPTY-GATE: тот же гейт, что в reloadFeed —
            // первичная загрузка тоже может стартовать в разделе LIKES/SEARCH
            // (feedFilterName — rememberSaveable, переживает process death).
            if (allPosts.isEmpty() &&
                feedFilterName != FeedFilter.LIKES.name &&
                feedFilterName != FeedFilter.SEARCH.name
            ) {
                val err = app.apiClient.lastApiError
                errorText = if (err != null) "Ошибка API: $err" else "Лента пуста"
            }
            // Fix #100: сохраняем снимок в кэш.
            FeedDataHolder.snapshot(allPosts, profiles, groups, nextFrom, endReached)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #252: корректная отмена (пользователь ушёл со экрана)
            throw e
        } catch (e: Exception) {
            AppLog.e("FeedScreen", "Failed to load feed", e)
            errorText = "Не удалось загрузить ленту: ${e.message}"
        } finally {
            loading = false
        }
    }

    // Sprint 1, P0-4 (#77): бесконечная пагинация — детектим скролл к концу.
    // Если последний видимый item в пределах 3 позиций от конца — loadMore().
    // ВНИМАНИЕ: posts.size НЕ в ключе LaunchedEffect! Иначе при каждом loadMore()
    // эффект перезапускается, snapshotFlow излучает true заново → бесконечный цикл
    // запросов → VK Flood control (error 9). snapshotFlow сам отслеживает
    // изменения posts.size через Compose snapshot reads.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= posts.size - 3 && posts.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMore() }
    }

    // §FEED-REACTIONS-NEWFEED: пагинация раздела «Реакции» — детектим скролл
    // к концу списка текущего подтаба (аналог loadMore для основной ленты) и
    // дочитываем следующую страницу через start_from=likesNextFrom.
    // На других разделах — no-op (feedFilter != LIKES → predicate false).
    LaunchedEffect(listState) {
        snapshotFlow {
            if (feedFilter != FeedFilter.LIKES) return@snapshotFlow false
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Считаем только item-ы текущего подтаба (без header/tabs/фото-блока).
            val totalLikesItems = when {
                likesFilter == LikesFilter.WALL_REPLY -> likedComments.size
                likesFilter == LikesFilter.CLIPS || likesFilter == LikesFilter.VIDEO -> likesVideos.size
                else -> likesItems.size
            }
            lastVisible >= totalLikesItems - 3 && totalLikesItems > 0
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreReactions() }
    }

    // Fix #99: сохраняем позицию скролла ленты при каждом изменении.
    // ВАЖНО: сохраняем только после scrollRestored — иначе при первой отрисовке
    // LazyColumn (items только что появились) snapshotFlow излучит (0,0) и
    // перетрёт сохранённую позицию до scroll restore.
    //
    // Fix #49 (audit): если posts пустой (видимых items нет) — НЕ пишем (0,0)
    // в FeedScrollHolder. Иначе сценарий: posts ещё не загрузился → snapshotFlow
    // излучает (0,0) → scrollRestored=true (от первого LaunchedEffect) → перетирает
    // сохранённую позицию. Теперь эмитим null если visibleItemsInfo пустой.
    //
    // Fix #114: скипаем sticky header (index 0 = StoriesRow) — он всегда на
    // offset 0 и иначе позиция всегда сохранялась бы как (0,0). См. подробнее
    // в saveScrollPosition() выше.
    //
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull { it.index > 0 }
            if (first != null) first.index to first.offset else null
        }
        .distinctUntilChanged()
        .collect { pair ->
            if (pair != null && scrollRestored) {
                FeedScrollHolder.position = ScrollPosition(pair.first, pair.second)
            }
        }
    }

    // #FEED-SCROLL-RESTORE-FIX: перепроектированный механизм восстановления
    // позиции скролла ленты после возврата из PostDetail / VideoPlayer /
    // Community / UserProfile / StoryViewer.
    //
    // ПРОБЛЕМА (предыдущий фикс через restoreKey не сработал):
    //   restoreKey = StoriesHolder.dirtyKey.collectAsState(). markDirty()
    //   вызывается в SovaNavHost.LaunchedEffect(currentRoute) при возврате на
    //   Feed. Но timing хрупкий: FeedScreen может recompose и прочитать
    //   restoreKey ДО того как StateFlow-update от markDirty() propagate →
    //   LaunchedEffect(restoreKey) видит «тот же» key → НЕ перезапускается →
    //   позиция НЕ восстанавливается. Пользователь видит ленту с начала.
    //
    // РЕШЕНИЕ: триггер восстановления — posts.isNotEmpty() (false→true).
    //   posts начинается как emptyList() (remember), затем LaunchedEffect
    //   (allPosts, feedPrefs) → posts. Этот переход false→true
    //   надёжный и НЕ зависит от markDirty timing.
    //
    // rememberSaveable(saver = LazyListState.Saver) остаётся первичным
    // механизмом (восстанавливает LazyListState напрямую из SavedStateHandle).
    // Этот effect — backup на случай если rememberSaveable не сработал
    // (LazyColumn пустой при re-entry → state clamped to (0,0)) или если
    // список posts изменился и индексы сместились.
    //
    // feedReloadKey в ключах — после reload/refresh эффект перезапускается,
    // но FeedScrollHolder.position сброшен в (0,0) → нет scrollToItem →
    // просто scrollRestored=true.

    // #FEED-SCROLL-POST-DETAIL: диагностический лог входа в Feed.
    // Логируем состояние restore-механизма при каждом (re-)composition —
    // это позволяет по logcat точно понять, что произошло при возврате из
    // PostDetail: был ли кэш, была ли восстановлена LazyListState, какое
    // значение в FeedScrollHolder.
    LaunchedEffect(Unit) {
        val saved = FeedScrollHolder.position
        val cacheSize = FeedDataHolder.allPosts?.size ?: -1
        AppLog.i("FeedScreen", "ENTERED: FeedScrollHolder=(${saved.index},${saved.offset}), " +
            "cache=${if (cacheSize >= 0) "$cacheSize posts" else "null"}, " +
            "listState.firstVisible=${listState.firstVisibleItemIndex}/" +
            "${listState.firstVisibleItemScrollOffset}")
    }

    // Backup-save: сохраняем позицию при уходе с Feed (DisposableEffect).
    // Покрывает пути НЕ через onPostClickSavePos (system Back, навигация
    // через drawer, и т.д.). snapshotFlow выше — основной save (continuously).
    //
    // #FEED-SCROLL-POST-DETAIL: DEFENSIVE — не перетираем глубокую сохранённую
    // позицию более мелкой (транзиентный артефакт анимации nav-transition).
    // Сценарий бага: пользователь на index=5 → тап на пост →
    // saveScrollPosition() пишет (5, offset) → nav fade-out анимация →
    // onDispose вызывается когда LazyColumn уже «сворачивается» →
    // layoutInfo.visibleItemsInfo может содержать index=1 (артефакт) →
    // старый код перетирал (5, offset) на (1, ...) → при возврате restore
    // скроллил на 1 вместо 5. Теперь: сохраняем только если новый index
    // >= сохранённому (не регрессируем вглубь).
    DisposableEffect(Unit) {
        onDispose {
            // Fix #114: it.index > 0 (пропускаем StoriesRow sticky header).
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index > 0 }
            val current = FeedScrollHolder.position
            if (first != null) {
                if (current.index == 0 || first.index >= current.index) {
                    FeedScrollHolder.position = ScrollPosition(first.index, first.offset)
                    AppLog.d("FeedScreen", "onDispose save: pos=(${first.index},${first.offset})")
                } else {
                    AppLog.d("FeedScreen", "onDispose skip: first=${first.index} < saved=${current.index} — keeping saved (animation artifact?)")
                }
            } else {
                AppLog.d("FeedScreen", "onDispose: no visible post (index>0) — keeping saved=(${current.index},${current.offset})")
            }
        }
    }

    // Restore: срабатывает когда posts становятся непустыми (false→true).
    // scrollRestored — guard чтобы восстановить ОДИН раз за сессию.
    //
    // #FEED-SCROLL-POST-DETAIL: перепроектированный restore с verify+retry.
    // Проблема: старая версия звала scrollToItem ОДИН раз с delay(50). Если
    // LazyColumn не успел measure items (posts только что стали непустыми),
    // scrollToItem тихо скроллил на 0 или не на тот index. Теперь:
    //   (1) withFrameNanos — ждём ОДИН layout pass (надёжнее delay).
    //   (2) scrollToItem — основной restore.
    //   (3) verify — проверяем listState.firstVisibleItemIndex == saved.index.
    //   (4) retry — если verify не сошёлся, повторяем с delay(150).
    // Логируем КАЖДЫЙ шаг — по logcat будет видна реальная причина если
    // восстановление не сработает.
    LaunchedEffect(posts.isNotEmpty(), feedReloadKey) {
        if (!posts.isNotEmpty()) return@LaunchedEffect
        if (scrollRestored) return@LaunchedEffect
        val saved = FeedScrollHolder.position
        val beforeIdx = listState.firstVisibleItemIndex
        AppLog.i("FeedScreen", "Restore effect FIRED: posts=${posts.size}, saved=(${saved.index},${saved.offset}), " +
            "listState.before=${beforeIdx}, feedReloadKey=$feedReloadKey")
        if (saved.index > 0) {
            // (1) Ждём один layout pass — LazyColumn должен measure items.
            withFrameNanos { }
            // Дополнительная delay на случай если withFrameNanos сработал до
            // того как Compose опубликовал layout для LazyColumn.
            kotlinx.coroutines.delay(30L)
            // (2) Основной restore.
            //
            // #FEED-SCROLL-OFFSET-SIGN (2026-08-01): visibleItemsInfo.offset и
            // scrollToItem(scrollOffset) имеют ПРОТИВОПОЛОЖНЫЕ знаки:
            //   visibleItemsInfo.offset = -100 → item на 100px ВЫШЕ viewport top
            //   scrollToItem(i, 100)           → item на 100px ВЫШЕ viewport top
            //   scrollToItem(i, -100)          → item на 100px НИЖЕ viewport top
            // Раньше код звал scrollToItem(saved.index, saved.offset) — т.е.
            // scrollToItem(18, -1365) → скроллил НАЗАД на 1365px от item 18 →
            // приземлялся на item 16 (на 2 поста выше). Лог:
            //   After scrollToItem(1): firstVisible=16 (expected=18)
            //   After scrollToItem(2): firstVisible=16  ← retry тоже мимо
            // Фикс: negate offset → scrollToItem(saved.index, -saved.offset).
            val scrollOffset = -saved.offset
            runCatching {
                listState.scrollToItem(saved.index, scrollOffset)
            }.onFailure { e ->
                AppLog.w("FeedScreen", "scrollToItem(1) failed: ${e.message}")
            }
            // (3) Verify — действительно ли мы на нужном index?
            var afterIdx = listState.firstVisibleItemIndex
            AppLog.i("FeedScreen", "After scrollToItem(1): firstVisible=$afterIdx (expected=${saved.index}, " +
                "scrollOffset=$scrollOffset)")
            // (4) Retry если verify не сошёлся. Бывает при большом списке +
            // сложном layout (видео-превью, большие фото) — первый scrollToItem
            // может приземлиться на соседний index из-за измерений.
            if (afterIdx != saved.index) {
                AppLog.w("FeedScreen", "Restore MISMATCH: expected=${saved.index} actual=$afterIdx — retrying with delay(150)")
                kotlinx.coroutines.delay(150L)
                runCatching {
                    listState.scrollToItem(saved.index, scrollOffset)
                }.onFailure { e ->
                    AppLog.w("FeedScreen", "scrollToItem(2) failed: ${e.message}")
                }
                afterIdx = listState.firstVisibleItemIndex
                AppLog.i("FeedScreen", "After scrollToItem(2): firstVisible=$afterIdx")
            }
            AppLog.i("FeedScreen", "Scroll RESTORED: index=${saved.index} offset=${saved.offset} " +
                "(scrollOffset=$scrollOffset, actual=$afterIdx, feedReloadKey=$feedReloadKey)")
        } else {
            AppLog.d("FeedScreen", "Scroll restore skip: saved index=0 (top), feedReloadKey=$feedReloadKey")
        }
        scrollRestored = true
    }

    // ─── #FEED-MENU-VKWEB (Fix #353) → Fix #368: правое боковое меню ленты ───
    // Размещён ДО loading/error-ранних return'ов — панель меню открывается из
    // любого состояния экрана (триггер в глобальном TopBar). Раньше вызов стоял
    // после return'ов: на скелетоне/ошибке FeedPanelState.visible=true записывался
    // «в пустоту» (панель вне композиции) и «сама выскакивала» после загрузки.
    // Прецедент: NotificationsScreen (bottom-sheet «Фильтр» до return'ов).
    //
    // zIndex(1f): этот вызов стоит ВЫШЕ контента по коду (иначе ранние return'
    // его пропускают), а сиблинги в Compose рисуются в порядке композиции —
    // без zIndex Scrim/панель оказались бы ПОД карточками ленты. zIndex
    // поднимает оверлей над всеми сиблингами (лента/FAB — zIndex 0).
    //
    // Навигация РАЗДЕЛОВ ленты («Список ленты» VK web, rightmenu §1.0.2
    // снапшота): Лента/Фотографии/Друзья/Поиск/Реакции переключают
    // feedFilterName с перезагрузкой (см. onSelect у FeedFilterBar),
    // навигационные разделы (Уведомления/Мессенджер/...) уводят на Screen.*
    // через onFeedPanelNavigate, «Редактировать» уводит на «Скрытые
    // источники» (Screen.FeedHidden).
    Box(modifier = Modifier.zIndex(1f)) {
        FeedRightPanel(
            visible = FeedPanelState.visible,
            currentFilter = feedFilter,
            onSelectFilter = { f ->
                AppLog.i("FeedScreen", "FeedRightPanel filter → ${f.name}, FeedPanelState.visible = false")
                FeedPanelState.visible = false
                feedFilterName = f.name
                reloadFeed()
            },
            onDismiss = {
                AppLog.i("FeedScreen", "FeedRightPanel dismiss → FeedPanelState.visible = false")
                FeedPanelState.visible = false
            },
            onOpenHiddenSources = {
                AppLog.i("FeedScreen", "FeedRightPanel hidden sources → FeedPanelState.visible = false")
                FeedPanelState.visible = false
                onOpenHiddenSources()
            },
        )
    }

    if (loading) {
        SkeletonFeedList(count = 5)
        return
    }

    if (errorText != null && posts.isEmpty()) {
        // #38: показываем кнопку «Офлайн контент» не только при отсутствии интернета
        // (networkObserver.isOnlineFlow), но и при auto-offline (privacyOfflineMode=true),
        // который включается VKApiClient.callInternal после 3 сетевых неудач подряд.
        val offlineRequested = isOffline || feedPrefs.privacyOfflineMode
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ErrorView(
                message = errorText,
                onRetry = { reloadFeed() },
                isOffline = isOffline,
            )
            if (offlineRequested) {
                OutlinedButton(
                    onClick = onOpenOfflineManager,
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Офлайн контент")
                }
            }
        }
        return
    }

    // #238: scroll-to-top FAB — показывается при прокрутке вниз, тап →
    // плавная анимация к началу ленты. Гейтится настройкой feedShowScrollFab
    // (SettingsScreen → Интерфейс → «Кнопка наверх в ленте»).
    //
    // #FEED-FAB-SYNC: FAB показывается ТАКЖЕ когда нижнее меню скрыто
    // (bottomBarVisible == false), независимо от величины скролла. Раньше пороги
    // были рассинхронизированы: меню пряталось при ~24px скролла (accumulator
    // < -24f в SovaNavHost), а FAB появлялся только при >200px или index>0 —
    // получалась «слепая зона» 24–200px, где меню уже спрятано, а кнопки ещё
    // нет → пользователь видел «кнопка пропала при скрытии меню». Теперь FAB
    // появляется ровно в момент скрытия панели.
    val bottomBarVisibleState = re.pinok.ui.navigation.LocalBottomBarVisible.current
    val showScrollToTopFab by remember {
        derivedStateOf {
            feedPrefs.feedShowScrollFab && posts.isNotEmpty() &&
                (!bottomBarVisibleState.value ||
                    listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 200)
        }
    }

    // Sprint 1, P0-4 (#77): PullToRefreshBox — pull-to-refresh ленты.
    // ExperimentalMaterial3Api — PullToRefreshBox стабилен в material3 1.4+,
    // но аннотация пока остаётся.
    // #238: обёрнут в Box чтобы наложить scroll-to-top FAB (PullToRefreshBox
    // сам по себе не принимает overlay-контент).
    //
    // Fix #359 → Fix #363 #FEED-MENU-TOPBAR-2: закреплённая строка с кнопкой
    // «меню ленты» УДАЛЕНА — кнопка переехала на глобальный TopAppBar через
    // ScreenTopBar.configure (DisposableEffect выше, токен-паттерн Fix #256/#262).
    // Раньше она пришла сюда из первого item LazyColumn (Fix #359), теперь
    // финальное место — слот actions глобального TopAppBar (SovaNavHost).
    // Column сохранён как обёртка скролл-зоны (Box weight 1f + FAB-оверлей).
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshFeed() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            // #PANEL-SEPARATE-REACTIONS: подтабы раздела «Реакции» вынесены в
            // собственный item (а не в stories_header), чтобы рендериться и в
            // автономном режиме (feed_likes), когда stories/заголовок ленты скрыты.
            if (likesOnly || feedFilter == FeedFilter.LIKES) {
                item(key = "reactions_tabs") {
                    ScrollableTabRow(
                        selectedTabIndex = LikesFilter.entries.indexOf(likesFilter),
                        edgePadding = 12.dp,
                        modifier = Modifier.fillMaxWidth(),
                        divider = {},
                    ) {
                        LikesFilter.entries.forEachIndexed { index, lf ->
                            Tab(
                                selected = likesFilter == lf,
                                onClick = { likesFilterName = lf.name },
                                text = { Text(lf.label, maxLines = 1) },
                            )
                        }
                    }
                }
            }
            item(key = "stories_row") {
                // #PANEL-SEPARATE-REACTIONS: в автономном режиме (feed_likes) header
                // с переключателем разделов/поиском/историями НЕ рендерится — вверху
                // сразу подтабы реакций. Обычная лента, stories и их загрузка на фоне
                // этого маршрута полностью отключены.
                if (!likesOnly) {
                Column {
                    // Fix #359 → Fix #363: кнопка «меню ленты» ПЕРЕНЕСЕНА из этого
                    // item сначала в закреплённую панель (#359), затем на глобальный
                    // TopAppBar (#363, ScreenTopBar.configure) — над списком её больше нет.
                    // #FEED-FILTER-TOGGLE: панель разделов скрывается настройкой.
                    if (feedPrefs.feedShowFilter) {
                        FeedFilterBar(
                            currentFilter = feedFilter,
                            onSelect = { f ->
                                if (f != feedFilter) {
                                    feedFilterName = f.name
                                    reloadFeed()
                                }
                            },
                        )
                    }
                    // #FEED-FILTER-SEARCH: поле поиска для вкладки «Поиск».
                    if (feedFilter == FeedFilter.SEARCH) {
                        OutlinedTextField(
                            value = feedSearchQuery,
                            onValueChange = { feedSearchQuery = it },
                            placeholder = { Text("Поиск по новостям…") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { reloadFeed() },
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "Поиск",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                Row {
                                    if (feedSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            feedSearchQuery = ""
                                            reloadFeed()
                                        }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                                        }
                                    }
                                    IconButton(onClick = { reloadFeed() }) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = "Искать",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                    // #STORY-TOGGLE: раздел «Истории» скрывается настройкой
                    // (storiesShown == false). StoriesRow сам тянет данные через
                    // LaunchedEffect → storiesGet — при скрытии ряда не вызываем
                    // его вовсе (гейт на уровне FeedScreen), чтобы не дёргать API.
                    if (feedPrefs.storiesShown) {
                        StoriesRow(
                            onStoryClick = { groups, index ->
                                // Fix #113: сохраняем позицию скролла перед переходом
                                // в StoryViewer — иначе при возврате лента скидывалась
                                // в начало (onVideoClick/onPostClick это уже делали,
                                // stories были пропущены).
                                saveScrollPosition()
                                StoryHolder.groups = groups
                                StoryHolder.startGroupIndex = index
                                onStoryViewerClick()
                            },
                        )
                    }
                }
                } // if (!likesOnly)
            }
            // #FEED-REACTIONS: список реакций (likes.getList).
            if (feedFilter == FeedFilter.LIKES) {
                // #FEED-LIKES-PHOTOS (IMP-FEED-1, п.4): блок «Понравившиеся фото» —
                // только на подтабе «Все» (снапшот §1.5). Рендер — LikedPhotosSection
                // (Chunked-rows сетка 3×квадрат, LazyVerticalGrid внутри LazyColumn нельзя).
                if (likesFilter == LikesFilter.ALL && likedPhotos.isNotEmpty()) {
                    item(key = "likes_photos_block") {
                        LikedPhotosSection(
                            photos = likedPhotos,
                            total = likedPhotosTotal,
                            hasMore = likedPhotosHasMore,
                            loadingMore = likedPhotosLoadingMore,
                            onLoadMore = { loadLikedPhotos(reset = false) },
                            onPhotoClick = { urls, idx ->
                                saveScrollPosition()
                                photoViewerState.value = urls to idx
                            },
                        )
                    }
                }
                if (likesLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (likesFilter == LikesFilter.WALL_REPLY) {
                    // #FEED-LIKES-COMMENTS (IMP-FEED-1, п.3): подтаб «Комментарии».
                    // Карточки лайкнутых комментариев (CommentCard). «Пожаловаться»
                    // на коммент НЕ добавлен — моб. wire отсутствует (reports.php
                    // legacy web; wall.markAsSpam работает только с постами),
                    // отклонение задокументировано в KDoc CommentCard.
                    // #FEED-REACTIONS-COUNTS: различаем «лайкнутых комментариев нет вовсе»
                    // (likesTotalCount==0) от «объекты есть, но получить/разрешить
                    // не удалось» (count>0, а likedComments пусто).
                    if (likedComments.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (likesTotalCount == 0) "Нет лайкнутых комментариев"
                                    else "Не удалось загрузить комментарии",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(likedComments, key = { "like_comment_${it.ownerId}_${it.commentId}" }) { comment ->
                            // P0.46-unify-comments: общий CommentItem из PostDetailScreen.
                            // Раньше был локальный CommentCard (LikedComment → Comment,
                            // аватар+имя+текст+лайк). CommentItem даёт то же + вложения,
                            // long-press меню (Ответить/Поделиться) и ветку ответов.
                            // Likes.getList(type="comment") уже возвращает liked=true,
                            // поэтому Post.Likes(userLikes = 1).
                            val author = likedCommentAuthors[comment.fromId]
                            val profilesMap = remember(author) {
                                if (author != null) mapOf(comment.fromId to author) else emptyMap()
                            }
                            val asComment = remember(comment) {
                                Comment(
                                    id = comment.commentId,
                                    fromId = comment.fromId,
                                    date = comment.date,
                                    text = comment.text,
                                    likes = Post.Likes(
                                        count = comment.likesCount,
                                        userLikes = 1,
                                        canLike = 0,
                                    ),
                                )
                            }
                            CommentItem(
                                comment = asComment,
                                profiles = profilesMap,
                                postOwnerId = comment.ownerId,
                                currentUserId = currentUserId,
                                onShareComment = { c ->
                                    sharingComment.value = c
                                    sharingCommentOwnerId.value = comment.ownerId
                                },
                            )
                        }
                    }
                } else if (likesFilter == LikesFilter.CLIPS || likesFilter == LikesFilter.VIDEO) {
                    // Fix #364 #FEED-REACTIONS-VIDEO: лайкнутые видео/клипы.
                    // Рендер — VideoThumbnail (тот же компонент, что у видео-вложений
                    // постов), onClick → onVideoClickSavePos → VideoHolder.open
                    // (overlay-плеер, позиция ленты сохраняется перед уходом — Fix #100).
                    // Пустая вкладка → честное состояние: «лайкнутых видео нет» (count==0)
                    // VS «загрузка есть, но VK не вернул доступные видео»
                    // (count>0, likesVideos пусто — удалённые/приватные).
                    if (likesVideos.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (likesTotalCount == 0) "Нет лайкнутых видео"
                                    else "Не удалось загрузить видео",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(likesVideos, key = { "like_video_${it.ownerId}_${it.id}" }) { video ->
                            VideoThumbnail(video = video, onClick = onVideoClickSavePos)
                        }
                    }
                } else if (likesItems.isEmpty()) {
                    // #FEED-REACTIONS-COUNTS: «вы никого не лайкали» (count==0) VS
                    // «лайки есть, но wallGetById не вернул целостные посты
                    // (удалённые/приватные/недоступные)».
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (likesTotalCount == 0) "Вы ещё никого не лайкали"
                                else "Не удалось загрузить реакции",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(likesItems, key = { "like_${it.ownerId}_${it.id}" }) { post ->
                        PostCard(
                            post = post,
                            profiles = emptyMap(),
                            groups = likesGroups,
                            likesState = likesState,
                            myUserId = myUserId,
                            subscriptionState = subscribeState,
                            // #FEED-CAROUSEL (19-A): флаг из prefs экрана.
                            carouselEnabled = feedPrefs.feedCarouselEnabled,
                            // NEWS-COLLAPSE: сворачивание текста поста (преф экрана).
                            collapsePostText = feedPrefs.newsCollapsePostText,
                            onSubscribe = { toggleSubscribe(it) },
                            onReportSpam = { reportingPost.value = it },
                            onLikeToggle = { clickedPost ->
                                val key = "${clickedPost.ownerId}_${clickedPost.id}"
                                val current = likesState[key] ?: (false to 0)
                                val newLiked = !current.first
                                likesState[key] = newLiked to (current.second + (if (newLiked) 1 else -1)).coerceAtLeast(0)
                                scope.launch {
                                    val newCount = if (newLiked) {
                                        app.apiClient.likesAdd("post", clickedPost.ownerId, clickedPost.id)
                                    } else {
                                        app.apiClient.likesDelete("post", clickedPost.ownerId, clickedPost.id)
                                    }
                                    if (newCount >= 0) {
                                        likesState[key] = newLiked to newCount
                                    } else {
                                        likesState[key] = current
                                    }
                                }
                            },
                            onCommentClick = { commentingPost.value = it },
                            onVideoClick = onVideoClickSavePos,
                            onAuthorClick = { p ->
                                if (p.fromId < 0) onGroupClickSavePos(-p.fromId)
                                else onUserClickSavePos(p.fromId)
                            },
                            // AUTHOR-STRIP: клик на полоску → стена, где ЛЕЖИТ пост
                            // (ownerId, не fromId): user → профиль, group → сообщество.
                            onPostContextClick = { p ->
                                if (p.ownerId > 0) onUserClickSavePos(p.ownerId)
                                else onGroupClickSavePos(-p.ownerId)
                            },
                            onPostClick = onPostClickSavePos,
                            onPhotoClick = { urls, index ->
                                saveScrollPosition()
                                photoViewerState.value = urls to index
                            },
                            onRepostClick = { sharePost.value = it },
                        )
                    }
                }
            }
            // Кнопка «Создать пост» убрана из ленты (user request 2026-07-12):
            // как в оригинальном VK, на ленте остаются только истории (StoriesRow),
            // а кнопка создания поста перенесена в профиль (ProfileScreen.kt).
            // См. VK_IMPORT_API.MD §15.3 — stories-block заменяет inline-create-button.
            items(posts, key = { "${it.ownerId}_${it.id}" }) { post ->
                PostCard(
                    post = post,
                    profiles = profiles,
                    groups = groups,
                    likesState = likesState,
                    // IMP-FEED-1: подписка (п.2) + «Пожаловаться» (п.1).
                    myUserId = myUserId,
                    subscriptionState = subscribeState,
                    // #FEED-CAROUSEL (19-A): флаг из prefs экрана.
                    carouselEnabled = feedPrefs.feedCarouselEnabled,
                    // NEWS-COLLAPSE: сворачивание текста поста (преф экрана).
                    collapsePostText = feedPrefs.newsCollapsePostText,
                    onSubscribe = { toggleSubscribe(it) },
                    onReportSpam = { reportingPost.value = it },
                    onLikeToggle = { clickedPost ->
                        val key = "${clickedPost.ownerId}_${clickedPost.id}"
                        val current = likesState[key] ?: (false to 0)
                        val newLiked = !current.first
                        // Оптимистично обновляем UI.
                        likesState[key] = newLiked to (current.second + (if (newLiked) 1 else -1)).coerceAtLeast(0)
                        // В фоне вызываем VK API.
                        scope.launch {
                            val newCount = if (newLiked) {
                                app.apiClient.likesAdd("post", clickedPost.ownerId, clickedPost.id)
                            } else {
                                app.apiClient.likesDelete("post", clickedPost.ownerId, clickedPost.id)
                            }
                            if (newCount >= 0) {
                                // VK подтвердил — обновляем точным значением.
                                likesState[key] = newLiked to newCount
                            } else {
                                // Ошибка — откатываем.
                                likesState[key] = current
                                AppLog.w("FeedScreen", "like toggle failed for $key")
                            }
                        }
                    },
                    onCommentClick = { cPost -> commentingPost.value = cPost },
                    onVideoClick = onVideoClickSavePos,
                    // Fix #67: передаём callback для клика по header'у → экран группы.
                    // Sprint 1, P0-2 (#74): для fromId > 0 → экран чужого профиля.
                    onAuthorClick = { clickedPost ->
                        if (clickedPost.fromId < 0) {
                            // Группа — открываем CommunityScreen с положительным ID.
                            onGroupClickSavePos(-clickedPost.fromId)
                        } else if (clickedPost.fromId > 0) {
                            // Пользователь — открываем UserProfileScreen.
                            onUserClickSavePos(clickedPost.fromId)
                        }
                    },
                    // AUTHOR-STRIP: клик на полоску → стена, где ЛЕЖИТ пост (ownerId).
                    // fromId = автор (кто написал), ownerId = стена (где лежит) —
                    // это могут быть разные сущности (юзер пишет на стене группы).
                    onPostContextClick = { p ->
                        if (p.ownerId > 0) onUserClickSavePos(p.ownerId)
                        else onGroupClickSavePos(-p.ownerId)
                    },
                    // Fix #71: тап по тексту поста → детальный экран.
                    // Fix: пробрасываем группы в PostHolder для имени сообщества в PostDetailScreen.
                    onPostClick = { post ->
                        PostHolder.lastGroups = groups
                        onPostClickSavePos(post)
                    },
                    // Sprint 2, P1-1 (#88): тап по фото → полноэкранный просмотр.
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    // Sprint 2, P1-3 (#90): тап по репосту → диалог.
                    onRepostClick = { sharePost.value = it },
                    // SOVA_2_lenta: контекстное меню поста.
                    onToggleBookmark = { p ->
                        scope.launch {
                            val ok = if (p.isFavoriteBool) {
                                app.apiClient.faveRemove("post", p.ownerId, p.id)
                            } else {
                                app.apiClient.faveAdd("post", p.ownerId, p.id)
                            }
                            AppLog.d("FeedScreen", "bookmark toggle: ok=$ok")
                        }
                    },
                    onDeletePost = { p ->
                        scope.launch {
                            val ok = app.apiClient.wallDelete(p.ownerId, p.id)
                            if (ok) { posts = posts.filter { it.ownerId != p.ownerId || it.id != p.id } }
                            AppLog.d("FeedScreen", "delete post: ok=$ok")
                        }
                    },
                    onTogglePin = { p ->
                        scope.launch {
                            val ok = if (p.isPinnedBool) {
                                app.apiClient.wallUnpin(p.ownerId, p.id)
                            } else {
                                app.apiClient.wallPin(p.ownerId, p.id)
                            }
                            AppLog.d("FeedScreen", "pin toggle: ok=$ok")
                        }
                    },
                    onEditPost = { /* TODO: экран редактирования поста */ },
                    onHideFromFeed = { p ->
                        scope.launch {
                            val ok = app.apiClient.newsfeedIgnoreItem("wall", p.ownerId, p.id)
                            if (ok) { posts = posts.filter { it.ownerId != p.ownerId || it.id != p.id } }
                            AppLog.d("FeedScreen", "hide from feed: ok=$ok")
                        }
                    },
                    onBanSource = { p ->
                        scope.launch {
                            val ok = if (p.fromId < 0) {
                                app.apiClient.newsfeedAddBan(groupIds = listOf(-p.fromId))
                            } else {
                                app.apiClient.newsfeedAddBan(userIds = listOf(p.fromId))
                            }
                            if (ok) { posts = posts.filter { it.fromId != p.fromId } }
                            AppLog.d("FeedScreen", "ban source: ok=$ok")
                        }
                    },
                    onReaction = { p, reaction ->
                        val key = "${p.ownerId}_${p.id}"
                        // Оптимистично обновляем счётчик.
                        val current = likesState[key] ?: (false to 0)
                        likesState[key] = true to (current.second + 1)
                        scope.launch {
                            val newCount = app.apiClient.likesAdd(
                                "post", p.ownerId, p.id,
                                reactionId = reaction.reactionId,
                            )
                            if (newCount >= 0) {
                                likesState[key] = true to newCount
                            } else {
                                likesState[key] = current
                            }
                        }
                    },
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
            // Sprint 1, P0-4 (#77): футер пагинации — spinner при загрузке или
            // «Это все записи» когда next_from==null.
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
                    endReached && posts.isNotEmpty() -> {
                        Text(
                            text = "Это все записи",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        }  // closes PullToRefreshBox

        // #238: scroll-to-top FAB — появляется при прокрутке вниз.
        // Тап → плавная анимация к началу ленты (item 0).
        // #FEED-FAB-SYNC: сразу возвращаем нижнее меню (bottomBarVisible=true),
        // не дожидаясь пока animateScrollToItem нагенерирует достаточно scroll
        // delta для onPreScroll (animation может выдать delta < 24f при коротком
        // скролле → меню осталось бы скрытым после возврата наверх).
        if (showScrollToTopFab) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // #FEED-REFRESH-FAB: обновление ленты рядом с кнопкой «наверх».
                FloatingActionButton(
                    onClick = {
                        bottomBarVisibleState.value = true
                        refreshFeed()
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить ленту")
                }
                FloatingActionButton(
                    onClick = {
                        bottomBarVisibleState.value = true
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
                }
            }
        }
        }  // closes Box (weight 1f — скролл-зона ленты)
    }  // closes Column (Fix #363: закреплённая строка удалена, кнопка — на TopAppBar)

    // Fix #368: FeedRightPanel ПЕРЕНЕСЁН выше — до loading/error-ранних
    // return'ов (перед `if (loading)`), т.к. return пропускал панель из
    // композиции: на скелетоне/ошибке меню не открывалось. См. вызов выше.

    // Bottom sheet комментариев — #43.
    // Волна 22: шит САМОДОСТАТОЧЕН — отправку wallCreateComment (включая
    // reply_to_comment, Fix #209) и перезагрузку wallGetComments он делает сам
    // (внутри есть SovaApp и scope), поэтому колбэк onSubmitComment удалён.
    // Навигационные колбэки — реальные механизмы этого экрана: видео →
    // onVideoClickSavePos (сохранение скролл-позиции перед уходом, Fix #100),
    // фото → photoViewerState (полноэкранный PhotoViewer ниже).
    val commenting = commentingPost.value
    if (commenting != null) {
        CommentsBottomSheet(
            post = commenting,
            onDismiss = { commentingPost.value = null },
            onVideoClick = onVideoClickSavePos,
            onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
        )
    }

    // Sprint 2, P1-1 (#88): полноэкранный просмотрщик фото.
    val viewer = photoViewerState.value
    if (viewer != null) {
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
        )
    }

    // ShareSheet: расширенный диалог «Поделиться» (диалоги / сообщества / стена / избранное).
    val sharing = sharePost.value
    if (sharing != null) {
        ShareSheet(
            post = sharing,
            onDismiss = { sharePost.value = null },
        )
    }

    // P0.46-unify-comments: ShareSheet для лайкнутого комментария из подтаба «Реакции».
    // Comment не имеет ownerId — но LikesFilter.WALL_REPLY хранит ownerId стены
    // в самом LikedComment (likes.getList type=comment возвращает owner_id стены).
    val sComment = sharingComment.value
    if (sComment != null) {
        ShareSheet(
            attachments = listOf("wall_reply${sharingCommentOwnerId.value}_${sComment.id}"),
            onDismiss = { sharingComment.value = null },
            onSuccess = { sharingComment.value = null },
        )
    }

    // ══ П-7-AB (IMP-FEED-1, п.1): подтверждение «Пожаловаться на запись?» ══
    // Паттерн 1:1 из ProfileScreen (reportTarget + reportInFlight: кнопки
    // disabled в полёте, повторный вызов игнорируется; успех → тост
    // «Жалоба отправлена», ошибка → тост lastApiError).
    val reportTarget = reportingPost.value
    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { if (!reportInFlight) reportingPost.value = null },
            title = { Text("Пожаловаться на запись?") },
            text = { Text("Запись будет отправлена на проверку администрации VK.") },
            confirmButton = {
                TextButton(
                    onClick = { reportWallPostConfirmed(reportTarget) },
                    enabled = !reportInFlight,
                ) {
                    Text(
                        if (reportInFlight) "Отправка…" else "Пожаловаться",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { reportingPost.value = null }, enabled = !reportInFlight) {
                    Text("Отмена")
                }
            },
        )
    }
}

// #FEED-FILTER: кнопка-переключатель раздела ленты (аналог VK rightmenu
// на vk.ru/feed). Открывает DropdownMenu с разделами «Все новости / Рекомендации /
// Видео / Фото / Записи». Компактная — в sticky-хэдере над историями.
@Composable
private fun FeedFilterBar(
    currentFilter: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiScale.scaled(12.dp), vertical = UiScale.scaled(2.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.height(UiScale.scaled(36.dp)),
            ) {
                Text(
                    text = currentFilter.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Разделы ленты",
                    modifier = Modifier.size(UiScale.scaled(18.dp)),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // #PANEL-SEPARATE-REACTIONS: «Реакции» (LIKES) убраны из переключателя
                // ленты — теперь это отдельный маршрут feed_likes, открываемый из панели.
                FeedFilter.values().filter { it != FeedFilter.LIKES }.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.label) },
                        leadingIcon = if (f == currentFilter) {
                            { Icon(Icons.Outlined.Check, null) }
                        } else null,
                        onClick = {
                            expanded = false
                            if (f != currentFilter) onSelect(f)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(
    post: Post,
    profiles: Map<Long, UserProfile>,
    groups: Map<Long, VKApiClient.GroupInfo>,
    likesState: Map<String, Pair<Boolean, Int>>,
    onLikeToggle: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onVideoClick: (Video) -> Unit,
    // Fix #67: тап по header'у (аватар/имя) → переход в сообщество.
    onAuthorClick: (Post) -> Unit = {},
    // Fix #71: тап по тексту/телу поста → детальный экран поста.
    onPostClick: (Post) -> Unit = {},
    // Sprint 2, P1-1 (#88): тап по фото → полноэкранный просмотр. (photos, initialIndex)
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // #FEED-CAROUSEL (19-A): карусель фото в пост-карточке (настройка
    // feedCarouselEnabled из SovaPrefs). Читается ОДИН раз на уровне экрана
    // (существующий collectAsState feedPrefs) и пробрасывается вниз — новых
    // чтений prefs на каждый кадр нет.
    carouselEnabled: Boolean = true,
    // NEWS-COLLAPSE: сворачивание текста поста в ленте до 10 строк с кнопкой
    // «Показать всё» (преф newsCollapsePostText). Влияет ТОЛЬКО на отображение.
    collapsePostText: Boolean = false,
    // Sprint 2, P1-3 (#90): тап по кнопке репоста → диалог подтверждения.
    onRepostClick: (Post) -> Unit = {},
    // SOVA_2_lenta: контекстное меню поста.
    onToggleBookmark: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onTogglePin: (Post) -> Unit = {},
    onEditPost: (Post) -> Unit = {},
    onHideFromFeed: (Post) -> Unit = {},
    onBanSource: (Post) -> Unit = {},
    // Реакции: (post, reactionEntry) — пользователь выбрал эмодзи-реакцию.
    onReaction: (Post, ReactionEntry) -> Unit = { _, _ -> },
    // ══ IMP-FEED-1 ══
    // Свой id (для гейтов «Пожаловаться»/«Подписаться» — только на чужих записях).
    myUserId: Long = 0L,
    // #FEED-SUBSCRIBE: состояние подписки на автора (key = fromId).
    // absent = неизвестно → рисуем дефолт «Подписаться» (VK не отдаёт состояние
    // подписки на юзера в newsfeed.get — честный дефолт, см. задачу IMP-FEED-1).
    subscriptionState: Map<String, Boolean> = emptyMap(),
    onSubscribe: (Post) -> Unit = {},
    // П-7-AB: «Пожаловаться» (wall.markAsSpam) — диалог и вызов на уровне экрана.
    onReportSpam: (Post) -> Unit = {},
    // AUTHOR-STRIP: клик на полоску «Перейти к посту» → стена, где ЛЕЖИТ пост
    // (по post.ownerId). Это ДРУГОЙ сценарий, чем onAuthorClick (fromId → профиль
    // автора): ownerId может отличаться от fromId (пользователь пишет на стене группы).
    onPostContextClick: (Post) -> Unit = {},
) {
    // Пост-источник: группа (fromId < 0) или пользователь (fromId > 0).
    val authorIsGroup = post.fromId < 0
    // Резолв автора (имя/фото) и подписи (signer) — ветвление 1:1 со старым
    // inline-телом (fromId > 0 → пользователь, иначе → группа).
    val authorName: String
    val authorPhoto: String?
    val signerName: String?
    if (post.fromId > 0) {
        val p = profiles[post.fromId]
        authorName = if (p != null) "${p.firstName} ${p.lastName}" else "id${post.fromId}"
        authorPhoto = if (p != null) p.photo100 else null
        signerName = null
    } else {
        val g = groups[-post.fromId]
        authorName = if (g != null) g.name else "Сообщество"
        authorPhoto = if (g != null) g.photo100 else null
        signerName = if (post.signerId != null) {
            val sp = profiles[post.signerId]
            if (sp != null) "${sp.firstName} ${sp.lastName}" else null
        } else {
            null
        }
    }
    // Подтверждённость группы (verified-бейдж) — для authorIsGroup и g.verified == 1.
    val verified = authorIsGroup && (groups[-post.fromId]?.verified == 1)

    // ─── Оптимистичный лайк (ключ «ownerId_id» из likesState) ───
    val likeKey = "${post.ownerId}_${post.id}"
    val likesOverride = likesState[likeKey]

    // ─── Кнопка подписки (как в старом inline-хедере) ───
    // Скрыта на своих постах (fromId == myUserId) и у постов без автора.
    val showSubscribeButton = post.fromId != 0L && post.fromId != myUserId
    // Состояние: override из subscriptionState; absent → дефолт «Подписаться»
    // (для сообществ — из GroupInfo.isMember, если VK отдал is_member).
    val subscribed: Boolean? = subscriptionState[post.fromId.toString()]
        ?: if (authorIsGroup) groups[-post.fromId]?.let { it.isMember == 1 } else null

    // ─── Меню ⋮: пункты 1:1 со старым inline-DropdownMenu ───
    // Пост-гейты (права поста) — зона вызывающего экрана; порядок/иконки повторены.
    val menuItems = buildList {
        // Закладка — ВСЕГДА (переключатель по isFavoriteBool).
        add(PostMenuItem(
            label = if (post.isFavoriteBool) "Убрать из закладок" else "В закладки",
            icon = if (post.isFavoriteBool) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            onClick = { onToggleBookmark(post) },
        ))
        if (post.canEditBool) {
            add(PostMenuItem(
                label = "Редактировать",
                icon = Icons.Outlined.Edit,
                onClick = { onEditPost(post) },
            ))
        }
        if (post.canPinBool) {
            add(PostMenuItem(
                label = if (post.isPinnedBool) "Открепить" else "Закрепить",
                icon = if (post.isPinnedBool) Icons.Outlined.VisibilityOff else Icons.Outlined.PushPin,
                onClick = { onTogglePin(post) },
            ))
        }
        add(PostMenuItem(
            label = "Скрыть из ленты",
            icon = Icons.Outlined.VisibilityOff,
            onClick = { onHideFromFeed(post) },
        ))
        add(PostMenuItem(
            label = "Не показывать от автора",
            icon = Icons.Outlined.ContentCopy,
            onClick = { onBanSource(post) },
        ))
        // П-7-AB: «Пожаловаться» — только на чужих записях (ownerId != myUserId).
        if (post.ownerId != myUserId) {
            add(PostMenuItem(
                label = "Пожаловаться",
                icon = Icons.Outlined.Flag,
                onClick = { onReportSpam(post) },
            ))
        }
        if (post.canDeleteBool) {
            add(PostMenuItem(
                label = "Удалить пост",
                icon = Icons.Outlined.Delete,
                destructive = true,
                onClick = { onDeletePost(post) },
            ))
        }
    }

    // ─── Делегирование единому PostCard (ui/components/PostCard.kt) ───
    // Алиас UnifiedPostCard — потому что единый компонент называется так же, как
    // эта обёртка; без алиаса вызов был бы рекурсией на саму себя.
    // Все колбэки/состояние пробрасываются 1:1 из сигнатуры обёртки.
    UnifiedPostCard(
        post = post,
        authorName = authorName,
        authorPhoto = authorPhoto,
        authorIsGroup = authorIsGroup,
        signerName = signerName,
        showFullDate = false,          // лента — относительное время
        isBookmarked = null,           // закладка показывается только в ⋮-меню
        onToggleBookmark = null,
        verified = verified,
        onAuthorClick = { onAuthorClick(post) },
        onPostClick = onPostClick,
        onVideoClick = onVideoClick,
        onPhotoClick = onPhotoClick,
        onLikeToggle = onLikeToggle,
        onCommentClick = onCommentClick,
        onRepostClick = onRepostClick,
        onReaction = onReaction,
        likesOverride = likesOverride,
        reactionOverride = null,       // отдельной reactionsState-карты в ленте нет
        menuItems = menuItems,
        showPinnedBadge = true,        // лента показывает «Закреплённый пост»
        showAdBadge = true,            // лента показывает «Реклама»
        showSubscribeButton = showSubscribeButton,
        subscribed = subscribed,
        onSubscribe = onSubscribe,
        // NEWS-COLLAPSE: при включённом сворачивании текст обрезается до 6 строк
        // (нужен запас, чтобы кнопка «Показать всё» помещалась), иначе текст
        // полностью. Int.MAX_VALUE → needsExpand=false → без кнопки, без обрезки.
        textMaxLines = if (collapsePostText) 6 else Int.MAX_VALUE,
        carouselEnabled = carouselEnabled,
        showViews = true,              // лента показывает просмотры
        myUserId = myUserId,
        // AUTHOR-STRIP: проброс клика полоски «Перейти к посту» в единый PostCard.
        onPostContextClick = { onPostContextClick(post) },
    )
}

// ═══════════════════════════════════════════════════════════════════════
// IMP-FEED-1: раздел «Реакции» — лайкнутые фото (п.4) и комментарии (п.3).
// ═══════════════════════════════════════════════════════════════════════

/**
 * Сырое лайкнутое фото из likes.getList(type="photo") (IMP-FEED-1, п.4).
 * url — максимальный width из sizes[] (parseLikedPhoto).
 */
private data class LikedPhoto(
    val ownerId: Long,
    val photoId: Long,
    val url: String,
)

/**
 * Сырой лайкнутый комментарий из likes.getList(type="comment") (IMP-FEED-1, п.3).
 * VK возвращает items как ПОЛНЫЕ объекты комментариев (id/from_id/text/date/
 * likes.count) — парсинг patient-стиль (parseLikedComment).
 */
private data class LikedComment(
    val ownerId: Long,
    val commentId: Long,
    val fromId: Long,
    val text: String,
    val date: Long,
    val likesCount: Int,
)

/**
 * Patient-парсинг фото-объекта likes.getList(type="photo"): owner_id/id/
 * sizes[] → url максимального width. Битые элементы (нет id/owner_id/sizes/
 * url) пропускаются (null → mapNotNull отфильтрует).
 */
private fun parseLikedPhoto(o: JsonObject): LikedPhoto? {
    return try {
        val ownerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val photoId = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val sizes = o.get("sizes")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        var bestUrl: String? = null
        var bestWidth = -1
        sizes.forEach { s ->
            if (!s.isJsonObject) return@forEach
            val so = s.asJsonObject
            val w = so.get("width")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val url = so.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            if (w > bestWidth) {
                bestWidth = w
                bestUrl = url
            }
        }
        LikedPhoto(ownerId = ownerId, photoId = photoId, url = bestUrl ?: return null)
    } catch (e: Exception) {
        AppLog.w("FeedScreen", "parseLikedPhoto failed: ${e.message}")
        null
    }
}

/**
 * §FEED-REACTIONS-NEWFEED: из сырого item newsfeed.getLikesFeed извлекает
 * (ownerId, itemId) для догрузки полного объекта (wallGetById / videoGet).
 * Приоритет: вложенный блок (post/video/comment/clip) → верхнеуровневые
 * owner_id/item_id (или id). Битые элементы (нет id/owner_id) → null
 * (mapNotNull отфильтрует).
 */
private fun likesFeedItemTarget(o: JsonObject): Pair<Long, Long>? {
    return try {
        val nested = o.get("post")
            ?: o.get("video")
            ?: o.get("comment")
            ?: o.get("clip")
        val ownerEl = if (nested != null && nested.isJsonObject) {
            nested.asJsonObject.get("owner_id") ?: o.get("owner_id")
        } else {
            o.get("owner_id")
        }
        // item_id — как в likes.getList; у вложенного блока обычно `id`.
        val itemEl = if (nested != null && nested.isJsonObject) {
            nested.asJsonObject.get("id") ?: o.get("item_id") ?: o.get("id")
        } else {
            o.get("item_id") ?: o.get("id")
        }
        val owner = ownerEl?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val item = itemEl?.takeIf { !it.isJsonNull }?.asLong ?: return null
        owner to item
    } catch (e: Exception) {
        AppLog.w("FeedScreen", "likesFeedItemTarget failed: ${e.message}")
        null
    }
}

/**
 * §FEED-REACTIONS-NEWFEED: из item newsfeed.getLikesFeed (type="comment")
 * возвращает объект комментария для parseLikedComment: вложенный блок
 * `comment`, если он есть, иначе сам item.
 */
private fun likesFeedCommentObject(o: JsonObject): JsonObject? {
    val nested = o.get("comment")
    return if (nested != null && nested.isJsonObject) nested.asJsonObject else o
}

/**
 * Patient-парсинг комментария likes.getList(type="comment"): id/from_id/text/
 * date/likes.count. Битые элементы (нет id/from_id) пропускаются.
 */
private fun parseLikedComment(o: JsonObject): LikedComment? {
    return try {
        val commentId = o.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val fromId = o.get("from_id")?.takeIf { !it.isJsonNull }?.asLong ?: return null
        val ownerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val text = o.get("text")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val date = o.get("date")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val likesCount = o.get("likes")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        LikedComment(
            ownerId = ownerId,
            commentId = commentId,
            fromId = fromId,
            text = text,
            date = date,
            likesCount = likesCount,
        )
    } catch (e: Exception) {
        AppLog.w("FeedScreen", "parseLikedComment failed: ${e.message}")
        null
    }
}

/**
 * #FEED-LIKES-PHOTOS (IMP-FEED-1, п.4): сетка «Понравившиеся фото» в разделе
 * «Реакции», подтаб «Все» (снапшот §1.5: likes-feed-photo0..19, 96×96;
 * §3.3 — UI-сетки раньше не было).
 *
 * Данные: likes.getList(type="photo"), items — сырые фото-объекты
 * ([parseLikedPhoto]: sizes[] → url максимального width, битые пропускаются).
 *
 * Сетка: 3 колонки КВАДРАТНЫХ ячеек. LazyVerticalGrid нельзя внутри LazyColumn
 * (вложенный скролл) — используем Chunked-rows по 3 ячейки (Row + weight(1f) +
 * aspectRatio(1f)); неполный ряд добивается Spacer'ами (weight-выравнивание).
 *
 * Тап по фото → существующий photoViewerState FeedScreen (largestUrl — url
 * максимального width; передаётся список ВСЕХ загруженных url + index).
 *
 * Пагинация «Загрузить ещё»: offset += страница (30, advanced по сырому
 * items.size, чтобы битые элементы не съедали курсор); hasMore = страница
 * была полной (items.size >= PAGE) И loaded < total (Pair.first).
 */
@Composable
private fun LikedPhotosSection(
    photos: List<LikedPhoto>,
    total: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Понравившиеся фото",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (total > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        photos.chunked(3).forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEachIndexed { colIdx, photo ->
                    val flatIndex = rowIdx * 3 + colIdx
                    AsyncImage(
                        model = photo.url,
                        contentDescription = "Фотография",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPhotoClick(photos.map { it.url }, flatIndex) },
                        contentScale = ContentScale.Crop,
                    )
                }
                // Добивка неполного ряда — квадратные ячейки сохраняют ширину 1/3.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (hasMore) {
            TextButton(onClick = onLoadMore, enabled = !loadingMore) {
                Text(
                    text = if (total > 0) "Загрузить ещё (${photos.size}/${total})" else "Загрузить ещё",
                )
            }
        }
    }
}

// P0.46-unify-comments: локальный CommentCard удалён — используется общий
// re.pinok.ui.screens.feed.CommentItem из PostDetailScreen (с лайком,
// вложениями, long-press меню и шарингом wall_reply).


// POSTCARD-REFACTOR-PLAN (этап 1): VideoThumbnail и ActionIcon вынесены в общие
// re.pinok.ui.components (см. импорты выше). Лента использует статичное название
// (titleExpandable=false — дефолт общего компонента).

// Fix #376: internal — карточка link/page-вложений переиспользуется ProfileScreen
// (паритет постов Профиля с Лентой; подпись/поведение не менялись).
@Composable
internal fun LinkCard(link: Attachment.Link, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            // #ARCH-CONTAINERS 3.7-1: photo/largestUrl в :core:data — захват ДО проверки.
            val linkPhotoUrl = link.photo?.largestUrl
            if (linkPhotoUrl != null) {
                AsyncImage(
                    model = linkPhotoUrl,
                    contentDescription = link.title,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // #ARCH-CONTAINERS 3.7-1: захват в локальный val (модели в :core:data)
                val linkTitle = link.title
                if (linkTitle != null) {
                    Text(
                        text = linkTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val linkDescription = link.description
                if (linkDescription != null) {
                    Text(
                        text = linkDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Source URL
                Text(
                    text = extractDomain(link.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun extractDomain(url: String): String {
    // Fix #49-4: null-safe host extraction. java.net.URI(url).host возвращает null
    // для URL без схемы (например 'vk.cc/abc' или 'vk.com/foo'). Без этого
    // host.startsWith("www.") кидал NPE → catch возвращал url (OK fallback),
    // но это ломало отображение домена в LinkCard.
    return try {
        val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val host = java.net.URI(withScheme).host ?: return url
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (_: Exception) { url }
}

private fun formatDocSize(size: Long): String = when {
    size < 1024 -> "${size} Б"
    size < 1024 * 1024 -> "${size / 1024} КБ"
    else -> String.format("%.1f МБ", size / 1024.0 / 1024.0)
}

/**
 * #ATTACH-UNIFY (P0.2): URI для снимка камеры через FileProvider (cache-path
 * заявлен в manifest, authority «<pkg>.fileprovider» — паттерн 1:1 из
 * ChatDetailScreen.createCameraImageUri). null — FileProvider не настроен.
 */
private fun createCameraImageUri(ctx: android.content.Context): Uri? {
    return try {
        val photoFile = File(ctx.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            photoFile,
        )
    } catch (e: Exception) {
        AppLog.e("CommentsBottomSheet", "createCameraImageUri failed", e)
        null
    }
}

/**
 * #ATTACH-UNIFY: Saver для Uri — rememberSaveable переживает process death
 * во время съёмки (паттерн ChatDetailScreen Fix #126: камера может убить
 * процесс, и без Saver снимок терялся).
 */
private val CameraUriSaver: Saver<Uri?, String> = Saver(
    save = { it?.toString() ?: "" },
    restore = { saved -> if (saved.isBlank()) null else Uri.parse(saved) },
)

// Fix #376: internal — карточка doc-вложений переиспользуется ProfileScreen
// (паритет постов Профиля с Лентой; подпись/поведение не менялись).
@Composable
internal fun DocAttachmentCard(doc: Attachment.Doc, onOpen: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} · ${formatDocSize(doc.size)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// #30: AudioAttachmentList перенесён в ui/components/AudioAttachmentList.kt
// — используется в FeedScreen, ProfileScreen, UserProfileScreen, CommunityScreen.


/**
 * Волна 22 (#COMMENT-REPLY-FEED): шит комментариев поста ИЗ ЛЕНТЫ — самодостаточный:
 * сам загружает wallGetComments, сам отправляет wallCreateComment (включая
 * reply_to_comment — ответ на комментарий, паттерн 1:1 из PostDetailScreen Fix #209),
 * сам перезагружает список после успешной отправки. Раньше отправка шла через
 * колбэк onSubmitComment наверх (wallCreateComment БЕЗ replyToComment, результат
 * игнорировался, список никогда не обновлялся с сервера) — колбэк УДАЛЁН.
 *
 * - Reply: кнопка «Ответить» в CommentRow → превью цели над композером →
 *   reply_to_comment при отправке; оптимистичный комментарий несёт
 *   replyToUser/replyToComment → «Ответ X» виден сразу.
 * - Перезагрузка после отправки: серверный список wallGetComments — источник
 *   истины. Решение (честно): локальные оптимистичные чистятся ТОЛЬКО при
 *   успешной перезагрузке (loadComments() == true); если она упала (сеть
 *   мигнула сразу после успешной отправки) — оптимистичные ОСТАЮТСЯ, чтобы
 *   отправленный комментарий не исчез из UI до следующего открытия шита.
 * - Содержимое комментариев (#COMMENT-ATTACH-EVERYWHERE): CommentRow рендерит
 *   comment.attachments через общий CommentAttachmentsView (фото/видео/аудио/
 *   doc/voice/link/стикер по CDN-паттерну VK web).
 * - onVideoClick/onPhotoClick — реальные навигационные колбэки FeedScreen
 *   (см. место вызова); по умолчанию пустые — контекст без навигации рендерит
 *   вложения без перехода (честный no-op, не заглушка-обещание).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    post: Post,
    onDismiss: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var profiles by remember { mutableStateOf(emptyMap<Long, UserProfile>()) }
    var loading by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var localComments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    // Волна 22: комментарий, на который отвечаем (reply-режим); null = обычный комментарий.
    var replyingToComment by remember { mutableStateOf<Comment?>(null) }
    // P0.46-unify-comments: шаринг комментария (wall_reply{post.ownerId}_{cid})
    // и id текущего юзера (для isOwnComment в CommentItem).
    var sharingComment by remember { mutableStateOf<Comment?>(null) }
    val currentUserId = remember { app.tokenStorage.load()?.userId ?: 0L }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachmentString by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    // Расширенный пикер (Музыка/Видео/Фото/Документы) — общий с чатом и комментариями к посту.
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableStateOf(AttachmentPickerTab.Music) }

    // Лаунчеры для вложений.
    // #ATTACH-UNIFY (P0.1): фото комментария грузится через ФОТО-пайплайн
    // (uploadPhotoForComment = photos.getWallUploadServer → upload → saveWallPhoto,
    // attach "photo{owner}_{id}"), а НЕ через docs — раньше фото уходило как
    // doc{} (расхождение с VK web и с PostDetailScreen, который делает верно).
    fun uploadCommentPhoto(uri: Uri) {
        scope.launch {
            uploading = true
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val file = File(ctx.cacheDir, "comment_photo_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                val attachment = app.apiClient.uploadPhotoForComment(file, "image/*")
                if (attachment != null) {
                    attachmentString = attachment
                    attachedFileName = "Фото"
                } else {
                    AppLog.w("CommentsBottomSheet", "uploadPhotoForComment returned null")
                }
                file.delete()
            } catch (e: Exception) {
                AppLog.e("CommentsBottomSheet", "upload photo error", e)
            } finally {
                uploading = false
            }
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        uploadCommentPhoto(uri)
    }
    // #ATTACH-UNIFY (P0.2): камера в комментариях ленты — TakePicture + FileProvider
    // (паттерн 1:1 из ChatDetailScreen:1204, был dead-пункт onCamera={}). Снимок
    // идёт по тому же пути, что фото из галереи → attach "photo{...}".
    var cameraImageUri by rememberSaveable(stateSaver = CameraUriSaver) { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraImageUri
        cameraImageUri = null
        if (!ok || uri == null) return@rememberLauncherForActivityResult
        uploadCommentPhoto(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(ctx)
            if (uri != null) {
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            } else {
                AppLog.w("CommentsBottomSheet", "createCameraImageUri returned null")
            }
        } else {
            AppLog.w("CommentsBottomSheet", "CAMERA permission denied")
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val fileName = java.net.URLDecoder.decode(uri.lastPathSegment ?: "file", "UTF-8")
                val file = File(ctx.cacheDir, "comment_${System.currentTimeMillis()}_$fileName")
                file.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                val attachment = app.apiClient.uploadDocForComment(file)
                if (attachment != null) {
                    attachmentString = attachment
                    attachedFileName = fileName
                } else {
                    AppLog.w("CommentsBottomSheet", "uploadDocForComment returned null for file")
                }
                file.delete()
            } catch (e: Exception) {
                AppLog.e("CommentsBottomSheet", "upload file error", e)
            } finally {
                uploading = false
            }
        }
    }

    // Загружаем комментарии: при открытии sheet и после успешной отправки
    // (волна 22 — серверный список становится источником истины). Раньше
    // загрузка была одноразовой (внутри LaunchedEffect) и после отправки
    // список никогда не обновлялся с сервера.
    // suspend + Boolean-результат: отправке нужно ЗНАТЬ, пришёл ли серверный
    // список, чтобы решить, чистить ли оптимистичные (см. KDoc шита).
    suspend fun loadComments(): Boolean {
        loading = true
        try {
            val result = app.apiClient.wallGetComments(post.ownerId, post.id, count = 50)
            comments = result.comments
            profiles = result.profiles
            return true
        } catch (e: Exception) {
            AppLog.e("FeedScreen", "Failed to load comments", e)
            return false
        } finally {
            loading = false
        }
    }
    LaunchedEffect(post.id) {
        loadComments()
    }

    // P0.46-unify-comments: ShareSheet для комментария из CommentsBottomSheet.
    // Рендерится ПЕРЕД ModalBottomSheet — поверх всего UI, как диалог «Поделиться».
    val sComment = sharingComment
    if (sComment != null) {
        ShareSheet(
            attachments = listOf("wall_reply${post.ownerId}_${sComment.id}"),
            onDismiss = { sharingComment = null },
            onSuccess = { sharingComment = null },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Заголовок
            Text(
                text = "Комментарии · ${post.comments?.count ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Список комментариев
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val allComments = (comments + localComments).distinctBy { it.id }
                if (allComments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Нет комментариев. Будьте первым!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(allComments, key = { it.id }) { comment ->
                            // P0.46-unify-comments: общий CommentItem из PostDetailScreen.
                            // Раньше был локальный CommentRow (аватар+имя+текст+лайк+
                            // «Ответить»). CommentItem даёт то же + вложения, long-press
                            // меню (Ответить/Поделиться/Редактировать/Удалить) и ветку
                            // ответов (thread.items → ReplyItem).
                            CommentItem(
                                comment = comment,
                                profiles = profiles,
                                postOwnerId = post.ownerId,
                                onReply = { replyingToComment = comment },
                                onVideoClick = onVideoClick,
                                onPhotoClick = onPhotoClick,
                                currentUserId = currentUserId,
                                onShareComment = { c -> sharingComment = c },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Превью прикреплённого файла.
            if (attachedFileName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachedFileName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { attachedFileName = null; attachmentString = null }, modifier = Modifier.size(24.dp)) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            // Волна 22: превью комментария, на который отвечаем (reply-режим).
            // Паттерн 1:1 из PostDetailScreen Fix #209: аватар автора + «Ответ {имя}»
            // + первые 60 символов текста + ✕ для отмены. Клик по превью — НЕ действие
            // (отменяет только ✕), как в VK web.
            val replyTarget = replyingToComment
            if (replyTarget != null) {
                val replyAuthor = profiles[replyTarget.fromId]
                // Честный фолбэк: VK скрывает профиль удалённого/закрытого юзера —
                // без профиля показываем нейтральное имя (заголовок «Ответ пользователю»).
                val replyAuthorName: String = if (replyAuthor != null) {
                    replyAuthor.fullName
                } else {
                    "пользователю"
                }
                // Явная развязка вместо элвиса (NULL-EXPLICIT).
                val replyAuthorAvatar: String? = if (replyAuthor != null) replyAuthor.photo100 else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Аватар автора цели: из profiles; без профиля/фото — кружок
                    // с первой буквой (или «?», если имени нет).
                    if (replyAuthorAvatar != null) {
                        AsyncImage(
                            model = replyAuthorAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (replyAuthor != null) replyAuthorName.take(1).uppercase() else "?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ответ $replyAuthorName",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            // До 60 символов; пустой текст (комментарий-вложение) → «(вложение)».
                            text = replyTarget.text.take(60).ifBlank { "(вложение)" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { replyingToComment = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            // Поле ввода нового комментария с вложениями
            // P0.39-IME-UNIFY: ModalBottomSheet не наследует внешние insets,
            // поэтому windowInsetsPadding(ime.union(navbar)) — самоуправление.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Кнопка вложения.
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !sending && !uploading,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AttachFile, contentDescription = "Прикрепить")
                        }
                    }
                    // Единое меню «Прикрепить» — тот же компонент, что в чате
                    // и в комментариях к посту. Подарки недоступны в комментариях.
                    UnifiedAttachMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        onPhoto = {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        // #ATTACH-UNIFY (P0.2): камера честно прокинута (был dead-пункт).
                        onCamera = {
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(ctx, permission) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                val uri = createCameraImageUri(ctx)
                                if (uri != null) {
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            } else {
                                cameraPermissionLauncher.launch(permission)
                            }
                        },
                        // #ATTACH-UNIFY (P1.5): фото/файл «Из VK» — табы пикера.
                        showPhotoFromVk = true,
                        onPhotoFromVk = {
                            attachmentPickerTab = AttachmentPickerTab.Photos
                            showAttachmentPicker = true
                        },
                        onVideo = {
                            attachmentPickerTab = AttachmentPickerTab.Video
                            showAttachmentPicker = true
                        },
                        onAudio = {
                            attachmentPickerTab = AttachmentPickerTab.Music
                            showAttachmentPicker = true
                        },
                        onFile = {
                            fileLauncher.launch(arrayOf("*/*"))
                        },
                        showFileFromVk = true,
                        onFileFromVk = {
                            attachmentPickerTab = AttachmentPickerTab.Docs
                            showAttachmentPicker = true
                        },
                        showGift = false,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ваш комментарий…") },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        val msg = inputText.trim()
                        val hasContent = msg.isNotBlank() || attachedFileName != null
                        if (!hasContent || sending || uploading) return@TextButton
                        // Волна 22: фиксируем reply-цель ДО очистки состояния
                        // (паттерн 1:1 из PostDetailScreen Fix #209). Явная развязка
                        // вместо элвиса: id цели > 0 → отвечаем на неё; иначе
                        // (обычный комментарий) reply-параметры не передаются вовсе.
                        val replyTarget = replyingToComment
                        val replyToCommentId: Long? = if (replyTarget != null && replyTarget.id > 0) replyTarget.id else null
                        val optimisticId = -System.currentTimeMillis()
                        val optimistic = Comment(
                            id = optimisticId,
                            fromId = 0,
                            date = System.currentTimeMillis() / 1000,
                            text = if (msg.isBlank()) "📎 $attachedFileName" else msg,
                            // Волна 22: reply-контекст в оптимистичном комментарии —
                            // «Ответ {имя}» виден сразу, до перезагрузки с сервера.
                            replyToUser = if (replyTarget != null) replyTarget.fromId else null,
                            replyToComment = replyToCommentId,
                        )
                        localComments = localComments + optimistic
                        val textToSend = msg
                        val attachmentToSend = attachmentString
                        // Прячем UI сразу — оптимистичный UX (как в PostDetailScreen).
                        inputText = ""
                        attachedFileName = null
                        attachmentString = null
                        replyingToComment = null
                        scope.launch {
                            sending = true
                            // Волна 22: отправка В САМОМ шите (раньше — колбэк onSubmitComment
                            // наверх без replyToComment, результат игнорировался, список не
                            // обновлялся). reply_to_comment поддержан VKA с Fix #209.
                            val id = app.apiClient.wallCreateComment(
                                post.ownerId, post.id, textToSend,
                                attachments = attachmentToSend,
                                replyToComment = replyToCommentId,
                            )
                            if (id > 0) {
                                AppLog.i("CommentsBottomSheet", "Comment added: id=$id")
                                // Серверный список — источник истины: перезагружаем с сервера.
                                // Решение (честно, см. KDoc шита): оптимистичные чистим
                                // ТОЛЬКО при успешной перезагрузке; если сеть упала сразу
                                // после успешной отправки — комментарий остаётся видимым
                                // локально до следующего открытия шита, а не исчезает.
                                val reloaded = loadComments()
                                if (reloaded) {
                                    localComments = emptyList()
                                }
                            } else {
                                // Провал отправки: откатываем оптимистичный (по id) и
                                // показываем честную причину (lastApiError VKA, сбрасывается
                                // в начале каждого вызова — #STALE-ERR-FIX).
                                localComments = localComments.filter { it.id != optimisticId }
                                AppLog.w("CommentsBottomSheet", "Comment failed for post ${post.id}")
                                val apiErr = app.apiClient.lastApiError
                                if (apiErr != null && apiErr.isNotBlank()) {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        "Не удалось отправить комментарий: $apiErr",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        "Не удалось отправить комментарий",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            sending = false
                        }
                    },
                    enabled = (inputText.isNotBlank() || attachedFileName != null) && !sending && !uploading,
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Отправить")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Единый пикер библиотеки VK — открывается при выборе соответствующего
    // пункта в UnifiedAttachMenu. Видео/аудио прикрепляются к комментарию как
    // "video{ownerId}_{id}" / "audio{ownerId}_{id}" (сборка через buildVkAttachment).
    // #ATTACH-UNIFY (P1.4): + табы «Фото»/«Документы» (attach без upload);
    // таб «Подарки» скрыт — gifts.send в комментариях недоступен (был dead-таб).
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
            showGiftTab = false,
            showPhotoTab = true,
            showDocsTab = true,
            onPickAudio = { track ->
                val att = buildVkAttachment("audio", track.ownerId, track.id, track.accessKey)
                attachmentString = att
                attachedFileName = "Музыка: ${track.title}"
                showAttachmentPicker = false
            },
            onPickVideo = { video ->
                val att = buildVkAttachment("video", video.ownerId, video.id, video.accessKey)
                attachmentString = att
                attachedFileName = "Видео: ${video.title.ifBlank { "видео" }}"
                showAttachmentPicker = false
            },
            onPickPhotoAttachment = { att, _ ->  // _ = thumb (#COMPOSER-ATTACH-PREVIEW): превью тут не нужно, только attachment-строка
                attachmentString = att
                attachedFileName = "Фото из VK"
                showAttachmentPicker = false
            },
            onPickDocAttachment = { att, title ->
                attachmentString = att
                attachedFileName = "Документ: $title"
                showAttachmentPicker = false
            },
        )
    }
}

// P0.46-unify-comments: локальный CommentRow удалён — используется общий
// re.pinok.ui.screens.feed.CommentItem из PostDetailScreen (с лайком,
// вложениями, long-press меню, веткой ответов и шарингом wall_reply).



/**
 * Sprint 4: Карточка опроса в ленте.
 * Fix #376: internal — переиспользуется ProfileScreen (паритет постов Профиля
 * с Лентой; подпись/поведение не менялись, голосование — через колбэк onVote).
 */
@Composable
internal fun PollCard(
    poll: re.pinok.data.model.Poll,
    onVote: (List<Long>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var voted by remember { mutableStateOf(poll.isVoted) }
    val totalVotes = poll.votes.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        // Question.
        Text(
            text = poll.question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Options.
        poll.answers.forEach { answer ->
            val isSelected = selectedIds.contains(answer.id) || (voted && poll.answerId == answer.id)
            val pct = if (voted) (answer.votes.toFloat() / totalVotes * 100).roundToInt() else 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .then(
                        if (!voted) Modifier.clickable {
                            if (poll.multiple == 1) {
                                selectedIds = if (isSelected) selectedIds - answer.id else selectedIds + answer.id
                            } else {
                                selectedIds = listOf(answer.id)
                            }
                        } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (poll.multiple == 1 && !voted) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = answer.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (voted) {
                        Text(
                            text = "${pct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (voted) {
                    // Progress bar.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    )
                }
            }
        }

        // Vote button / info.
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!voted && selectedIds.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = {
                    onVote(selectedIds)
                    voted = true
                }) {
                    Text("Голосовать")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = poll.votes.toCountString() + " голосов",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (poll.isAnonymous) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("Анонимно", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

