package re.pinok.ui.screens.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.BuildConfig
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Attachment
import re.pinok.data.model.Comment
import re.pinok.data.model.Post
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.PlayerConnection
import re.pinok.ui.navigation.PostHolder
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.PlaylistAttachmentCard
import re.pinok.ui.components.SkeletonFeedList
import re.pinok.ui.components.ErrorView
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.ui.navigation.FeedDataHolder
import re.pinok.ui.navigation.FeedScrollHolder
import re.pinok.ui.navigation.ScrollPosition
import re.pinok.util.AppLog
import re.pinok.util.toCountString
import re.pinok.util.toDurationString
import re.pinok.util.toRelativeTime
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
// FRIENDS → friends.getRecommendations. SEARCH → newsfeed.search.
private enum class FeedFilter(val label: String, val apiFilters: String?, val recommended: Boolean) {
    ALL("Все новости", "post,photo,video", false),
    RECOMMENDED("Рекомендации", null, true),
    LIKES("Реакции", null, false),
    PHOTOS("Фото", "post,photo,video", false),
    FRIENDS("Друзья", null, false),
    SEARCH("Поиск", null, false),
}

// #FEED-REACTIONS: подтабы для раздела «Реакции» (соответствует VK likes.getList type).
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
    // #CALLS: кнопка «Позвонить» на карточке друга.
    // #ARCH-CONTAINERS (Этап 1.4): nullable — хост передаёт колбэк ТОЛЬКО если
    // в реестре есть CallStarter (контейнер звонков). null → кнопка НЕ рендерится
    // (условие композиции, graceful-деградация без контейнера).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // Мониторинг сети для показа кнопки «Офлайн» при ошибке загрузки.
    val isOffline by app.networkObserver.isOnlineFlow.collectAsState(initial = !app.networkObserver.isOnline())

    // Настройки фильтрации ленты — реагируем на изменения в реальном времени.
    val feedPrefs by app.prefs.data.collectAsState(
        initial = re.pinok.data.local.SovaPrefs.Snapshot(
            newsAdsBlocked = true,
            newsRepostsHidden = false,
            newsPromoHidden = true,
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
            // Fix #228: масштаб стикер-фото (default 0% = исходный размер).
            stickerPhotoScale = 0,
            privacyOfflineMode = false,
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
            // P2.5: multi-select mode (opt-in — default OFF).
            msgMultiSelect = false,
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
            // Fix #237: showLogFab добавлен в Snapshot — FeedScreen тоже должен
            // передавать initial-значение, иначе компилятор падает:
            // «No value passed for parameter 'showLogFab'».
            // (Тот же класс бага что Fix #100 / #110 / #189 — Snapshot расширился.)
            // Default = BuildConfig.DEBUG (виден в debug, скрыт в release) —
            // соответствует default в SovaPrefs.
            showLogFab = BuildConfig.DEBUG,
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
            // #MSG-FAVORITES-TOGGLE: показывать «Избранное» в чатах (default true).
            msgShowFavorites = false,
            // #NET-SWITCH-POPUP (2026-08-04): netSwitchPopupEnabled добавлен в
            // Snapshot — FeedScreen тоже передаёт initial (default false).
            // (Тот же класс бага что Fix #100/#110/#189/#237/#302/#337 — Snapshot расширился.)
            // Default изменён на false (2026-08-04): popup скрыт по умолчанию.
            netSwitchPopupEnabled = false,
            lastRoute = "feed",
            // Fix #302 (Task 2-b): notifyCacheJson — пустая строка (= нет кэша),
            // реальные значения подгрузятся из SovaPrefs при первом же collect.
            notifyCacheJson = "",
            // §1-NOTIF-ARCHIVE: emailNotifyFreq — частота email-уведомлений
            // (0=всегда, 1=не чаще раза в день, 2=никогда). FeedScreen не использует
            // это поле, но Snapshot расширился — нужно передать initial-значение,
            // иначе компилятор падает: «No value passed for parameter 'emailNotifyFreq'».
            // (Тот же класс бага что Fix #100 / #110 / #189 / #237 / #302 — Snapshot расширился.)
            emailNotifyFreq = 0,
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
            callsVideoTx = true,
            callsVideoSwDecode = false,
            audioQuality = re.pinok.data.local.AudioQuality.Q192,
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
    var feedFilterName by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }
    val feedFilter = runCatching { FeedFilter.valueOf(feedFilterName) }.getOrDefault(FeedFilter.ALL)

    // #FEED-FILTER-SEARCH: поисковый запрос для вкладки «Поиск».
    var feedSearchQuery by remember { mutableStateOf("") }

    // #FEED-FILTER-FRIENDS: список рекомендованных друзей (вкладка «Друзья»).
    var recommendedFriends by remember { mutableStateOf<List<re.pinok.data.model.Friend>>(emptyList()) }
    var friendsLoading by remember { mutableStateOf(false) }

    // #FEED-REACTIONS: подтаб внутри «Реакций» (Все/Посты/Комментарии/Клипы/Видео).
    var likesFilterName by rememberSaveable { mutableStateOf(LikesFilter.ALL.name) }
    val likesFilter = runCatching { LikesFilter.valueOf(likesFilterName) }.getOrDefault(LikesFilter.ALL)
    var likesItems by remember { mutableStateOf<List<Post>>(emptyList()) }
    var likesLoading by remember { mutableStateOf(false) }
    var likesTotalCount by remember { mutableIntStateOf(0) }

    // Загрузка друзей-рекомендаций при переключении на вкладку «Друзья».
    LaunchedEffect(feedFilterName) {
        if (feedFilterName == FeedFilter.FRIENDS.name && recommendedFriends.isEmpty() && !friendsLoading) {
            friendsLoading = true
            try {
                val list = app.apiClient.friendsGetRecommendations(50)
                recommendedFriends = list
                AppLog.i("FeedScreen", "Loaded ${list.size} recommended friends")
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "friendsGetRecommendations error", e)
            } finally {
                friendsLoading = false
            }
        }
    }

    // Загрузка реакций (likes.getList) при переключении на вкладку «Реакции».
    LaunchedEffect(feedFilterName, likesFilterName) {
        if (feedFilterName == FeedFilter.LIKES.name && !likesLoading) {
            likesLoading = true
            try {
                val (totalCount, items) = app.apiClient.likesGetList(
                    type = likesFilter.apiType,
                    count = 30,
                )
                likesTotalCount = totalCount
                // Преобразуем items в посты через wall.getById
                if (items.isNotEmpty()) {
                    val postIds = items.mapNotNull { it ->
                        val ownerId = it.get("owner_id")?.takeIf { !it.isJsonNull }?.asLong ?: return@mapNotNull null
                        val itemId = it.get("item_id")?.takeIf { !it.isJsonNull }?.asLong ?: return@mapNotNull null
                        ownerId to itemId
                    }
                    if (postIds.isNotEmpty()) {
                        val result = app.apiClient.wallGetById(postIds)
                        likesItems = result.posts
                        AppLog.i("FeedScreen", "likesGetList: $totalCount total, ${likesItems.size} posts loaded")
                    } else {
                        likesItems = emptyList()
                    }
                } else {
                    likesItems = emptyList()
                }
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "likesGetList error", e)
            } finally {
                likesLoading = false
            }
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
                return VKApiClient.NewsfeedResult(emptyList(), emptyMap(), emptyMap(), null)
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
                if (allPosts.isEmpty()) {
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
                if (nextFrom == null || newPosts.isEmpty()) endReached = true
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
            if (allPosts.isEmpty()) {
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
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshFeed() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item(key = "stories_row") {
                Column {
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
                    // #FEED-REACTIONS: подтабы для раздела «Реакции».
                    if (feedFilter == FeedFilter.LIKES) {
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
            // #FEED-REACTIONS: список реакций (likes.getList).
            if (feedFilter == FeedFilter.LIKES) {
                if (likesLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (likesItems.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Нет реакций", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(likesItems, key = { "like_${it.ownerId}_${it.id}" }) { post ->
                        PostCard(
                            post = post,
                            profiles = emptyMap(),
                            groups = emptyMap(),
                            likesState = likesState,
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
            // #FEED-FILTER-FRIENDS: список рекомендованных друзей.
            if (feedFilter == FeedFilter.FRIENDS) {
                if (friendsLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (recommendedFriends.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Нет рекомендаций друзей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(recommendedFriends, key = { "fr_${it.id}" }) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserClickSavePos(friend.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val photo = friend.photo100 ?: friend.photo200
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(friend.firstName.take(1), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(friend.fullName, style = MaterialTheme.typography.bodyLarge)
                                if (friend.online == 1) {
                                    Text("онлайн", color = Color(0xFF4CAF50), fontSize = 13.sp)
                                }
                            }
                            // #CALLS: кнопка звонка другу (data-testid="friends_call_button").
                            // #ARCH-CONTAINERS (Этап 1.4): рисуем только при живом
                            // CallStarter (onCallClick != null).
                            if (onCallClick != null) {
                                IconButton(
                                    onClick = { onCallClick(friend.id, friend.fullName, friend.photo100 ?: friend.photo200) },
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Позвонить",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            // Кнопка «Добавить».
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val res = app.apiClient.friendsAdd(friend.id)
                                    if (res > 0) {
                                        android.widget.Toast.makeText(
                                            app.applicationContext,
                                            "Заявка отправлена",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }) {
                                Text("Добавить")
                            }
                        }
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
    }  // closes Box (PullToRefreshBox wrapper)

    // Bottom sheet комментариев — #43: показываем список + добавляем новый.
    val commenting = commentingPost.value
    if (commenting != null) {
        CommentsBottomSheet(
            post = commenting,
            onDismiss = { commentingPost.value = null },
            onSubmitComment = { message, attachment ->
                scope.launch {
                    val id = app.apiClient.wallCreateComment(
                        commenting.ownerId, commenting.id, message,
                        attachments = attachment,
                    )
                    if (id > 0) {
                        AppLog.i("FeedScreen", "Comment added: id=$id")
                    } else {
                        AppLog.w("FeedScreen", "Comment failed for post ${commenting.id}")
                    }
                }
            },
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
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = currentFilter.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Разделы ленты",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                FeedFilter.values().forEach { f ->
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
) {
    val ctx = LocalContext.current
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
    val timeStr = post.date.toRelativeTime()
    val photoAttachments = post.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
    val videoAttachments = post.attachments?.filter { it.type == "video" && it.video != null }.orEmpty()
    val audioAttachments = post.attachments?.filter { it.type == "audio" && it.audio != null }.orEmpty()

    // Локальное состояние лайка для этого поста.
    val likeKey = "${post.ownerId}_${post.id}"
    val likeState = likesState[likeKey]
    val isLiked = likeState?.first ?: (post.likes?.userLikes == 1)
    val likeCount = likeState?.second ?: post.likes?.count ?: 0
    // SOVA_2_lenta: состояние меню
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Метка «Реклама» для рекламных постов.
            if (post.isAd) {
                Text(
                    text = "Реклама",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }
            // Метка для рекомендованных постов.
            if (post.postType in listOf("suggest", "suggested")) {
                Text(
                    text = "Рекомендуемое",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = if (post.isAd) 0.dp else 8.dp),
                )
            }
            // ─── VKUI: vkit-OaLCik vkuiFlex__host vkuiFlex__alignCenter ───
            // post-header: avatar(link) + name(link) + spacer + context-menu(44x44)
            // VKUI: NO time in header — time ONLY in footer as post_date_block_preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // VKUI: vkuiAvatar__host 36x36, clickable link to author profile
                // vkuiImageBase__imgObjectFitCover
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onAuthorClick(post) },
                ) {
                    if (authorPhoto != null) {
                        AsyncImage(
                            model = authorPhoto,
                            contentDescription = authorName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = authorName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // VKUI: vkit-rQKpzS vkuiFlex__directionColumn, gap 0px
                Column(modifier = Modifier.weight(1f)) {
                    // VKUI: vkuiSubhead__densityCompact vkuiTypography__weight2 vkuiTypography__accent
                    // vkuiLink__withUnderline — ALL names are accent-colored clickable links
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onAuthorClick(post) },
                        )
                        // Бейдж верификации (без дополнительного padding)
                        if (post.fromId < 0) {
                            val g = groups[-post.fromId]
                            if (g != null && g.verified == 1) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = "Верифицировано",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    if (signerName != null) {
                        Text(
                            text = signerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // VKUI: flex-grow spacer (pushes menu to right)
                // VKUI: vkuiIconButton__densityCompact 44x44, more_horizontal_24
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Действия",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (post.isFavoriteBool) "Убрать из закладок" else "В закладки") },
                            leadingIcon = { Icon(if (post.isFavoriteBool) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                            onClick = { showMenu = false; onToggleBookmark(post) },
                        )
                        if (post.canEditBool) {
                            DropdownMenuItem(
                                text = { Text("Редактировать") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { showMenu = false; onEditPost(post) },
                            )
                        }
                        if (post.canPinBool) {
                            DropdownMenuItem(
                                text = { Text(if (post.isPinnedBool) "Открепить" else "Закрепить") },
                                leadingIcon = { Icon(if (post.isPinnedBool) Icons.Outlined.VisibilityOff else Icons.Outlined.PushPin, null) },
                                onClick = { showMenu = false; onTogglePin(post) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Скрыть из ленты") },
                            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                            onClick = { showMenu = false; onHideFromFeed(post) },
                        )
                        DropdownMenuItem(
                            text = { Text("Не показывать от автора") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { showMenu = false; onBanSource(post) },
                        )
                        if (post.canDeleteBool) {
                            DropdownMenuItem(
                                text = { Text("Удалить пост", color = Color(0xFFE53935)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFE53935)) },
                                onClick = { showMenu = false; onDeletePost(post) },
                            )
                        }
                    }
                }
            }
            // Индикатор закреплённого поста.
            if (post.isPinnedBool) {
                Text(
                    text = "Закреплённый пост",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                )
            }
            // SOVA_2_lenta: индикатор копирайта-источника.
            // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast чужого модуля
            // невозможен; захват в локальный val.
            val copyrightName = post.copyright?.name
            if (copyrightName != null) {
                Text(
                    text = copyrightName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // SOVA_2_lenta: индикатор Donut-заглушки.
            if (post.isDonut && post.donut?.placeholder != null) {
                Text(text = post.donut.placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (post.text.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                val textLines = post.text.lines().size
                val needsExpand = textLines > 6
                // Fix #204: парсим VK inline-ссылки [#alias|display|url] + обычные URL.
                val fullText = re.pinok.util.linkifyVkText(
                    text = post.text,
                    linkColor = MaterialTheme.colorScheme.primary,
                    onUrlClick = { url -> re.pinok.util.openUrlExternal(ctx, url) },
                )
                val collapsedText = if (needsExpand) {
                    re.pinok.util.linkifyVkText(
                        text = post.text.lines().take(6).joinToString("\n"),
                        linkColor = MaterialTheme.colorScheme.primary,
                        onUrlClick = { url -> re.pinok.util.openUrlExternal(ctx, url) },
                    )
                } else fullText
                Text(
                    text = if (needsExpand && !expanded) collapsedText else fullText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClick(post) }
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                // VKUI: vkit-b0dQw7 vkuiLink__withUnderline vkuiTypography__weight3 vkuiTypography__accent
                // "Показать ещё" — underline link style
                if (needsExpand && !expanded) {
                    Text(
                        text = "Показать ещё",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                            .clickable { expanded = true },
                    )
                }
            }
            if (photoAttachments.isNotEmpty()) {
                PhotoGrid(
                    photos = photoAttachments.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                )
            }
            videoAttachments.forEach { attach ->
                val v = attach.video
                if (v != null) {
                    VideoThumbnail(video = v, onClick = onVideoClick)
                }
            }
            // Аудио вложения в посте.
            // #30: используем общий AudioAttachmentList из ui/components.
            if (audioAttachments.isNotEmpty()) {
                AudioAttachmentList(tracks = audioAttachments.mapNotNull { it.audio })
            }
            // #30 (playlists): audio_playlist вложения.
            val playlistAttachments = post.attachments?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }.orEmpty()
            playlistAttachments.forEach { att -> att.audioPlaylist?.let { PlaylistAttachmentCard(playlist = it) } }
            // Link/snippet attachments (article cards like VK's snippet-attachment)
            // Fix #49-4: также рендерим page-вложения (VK wiki как alias-ссылки).
            val linkAttachments = post.attachments
                ?.filter { (it.type == "link" || it.type == "page") && it.link != null }
                .orEmpty()
            linkAttachments.forEach { attach ->
                val link = attach.link
                if (link != null) {
                    LinkCard(link = link, onClick = {
                        // Fix #51-A: alias (link/page) не открывался из-за URL без scheme.
                        // VK часто отдаёт короткие ссылки вида "vk.cc/abc" или "vk.com/foo"
                        // без протокола → Uri.parse создаёт URI без scheme → startActivity
                        // падает с ActivityNotFoundException. Также page.view_url может быть
                        // пустым. Нормализуем URL, валидируем, проверяем resolveActivity.
                        val rawUrl = link.url.orEmpty().trim()
                        if (rawUrl.isEmpty()) {
                            AppLog.w("FeedScreen", "LinkCard: url is empty, cannot open")
                            android.widget.Toast.makeText(
                                ctx,
                                "Ссылка недоступна",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@LinkCard
                        }
                        // Добавляем https:// если нет scheme (http://, https://, intent:, market:, ...).
                        val normalizedUrl = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
                        val uri = try {
                            android.net.Uri.parse(normalizedUrl)
                        } catch (e: Exception) {
                            AppLog.w("FeedScreen", "LinkCard: invalid url=$rawUrl", e)
                            android.widget.Toast.makeText(
                                ctx,
                                "Некорректная ссылка",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@LinkCard
                        }
                        if (uri.scheme == null || uri.host == null) {
                            AppLog.w("FeedScreen", "LinkCard: no scheme/host in url=$normalizedUrl")
                            android.widget.Toast.makeText(
                                ctx,
                                "Некорректная ссылка",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@LinkCard
                        }
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            uri,
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Проверяем что есть приложение способное обработать ACTION_VIEW для этого URI.
                        if (intent.resolveActivity(ctx.packageManager) == null) {
                            AppLog.w("FeedScreen", "LinkCard: no app to handle url=$normalizedUrl")
                            android.widget.Toast.makeText(
                                ctx,
                                "Нет приложения для открытия ссылки",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@LinkCard
                        }
                        try {
                            ctx.startActivity(intent)
                            AppLog.i("FeedScreen", "LinkCard: opened url=$normalizedUrl")
                        } catch (e: Exception) {
                            AppLog.w("FeedScreen", "Failed to open link: $normalizedUrl", e)
                            android.widget.Toast.makeText(
                                ctx,
                                "Не удалось открыть ссылку",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    })
                }
            }
            // Sprint 4: опросы.
            val pollAtt = post.attachments?.firstOrNull { it.type == "poll" && it.poll != null }
            if (pollAtt != null) {
                val poll = pollAtt.poll
                if (poll != null) {
                    val pollVoteScope = rememberCoroutineScope()
                    val pollVoteApp = re.pinok.SovaApp.get()
                    PollCard(poll = poll, onVote = { answerIds ->
                        pollVoteScope.launch {
                            pollVoteApp.apiClient.pollsAddVote(poll.id, poll.ownerId, answerIds)
                        }
                    })
                }
            }
            // Документ-вложения.
            val docAttachments = post.attachments?.filter { it.type == "doc" && it.doc != null }.orEmpty()
            if (docAttachments.isNotEmpty()) {
                docAttachments.forEach { attach ->
                    val doc = attach.doc
                    if (doc != null) {
                        DocAttachmentCard(doc = doc, onOpen = {
                            // Fix #51-A: та же нормализация URL что и для LinkCard.
                            val rawUrl = doc.url.orEmpty().trim()
                            if (rawUrl.isEmpty()) {
                                android.widget.Toast.makeText(
                                    ctx, "Ссылка недоступна", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@DocAttachmentCard
                            }
                            val normalizedUrl = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
                            val uri = android.net.Uri.parse(normalizedUrl)
                            if (uri.scheme == null || uri.host == null) {
                                android.widget.Toast.makeText(
                                    ctx, "Некорректная ссылка", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@DocAttachmentCard
                            }
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW, uri,
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                ctx.startActivity(intent)
                                AppLog.i("FeedScreen", "DocCard: opened url=$normalizedUrl")
                            } catch (e: Exception) {
                                AppLog.w("FeedScreen", "Failed to open doc: $normalizedUrl", e)
                                android.widget.Toast.makeText(
                                    ctx,
                                    "Не удалось открыть документ",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    }
                }
            }
            // Репост — copy_history.
            if (!post.copyHistory.isNullOrEmpty()) {
                val original = post.copyHistory.first()
                // VKUI: repost header — 24dp avatar, repost_outline_16 icon, 8px gap
                // padding: 4px 8px 0px
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                        val origName: String
                        val origPhoto: String?
                        if (original.fromId > 0) {
                            val p = profiles[original.fromId]
                            origName = if (p != null) "${p.firstName} ${p.lastName}" else "id${original.fromId}"
                            origPhoto = if (p != null) p.photo100 else null
                        } else {
                            val g = groups[-original.fromId]
                            origName = if (g != null) g.name else "Сообщество"
                            origPhoto = if (g != null) g.photo100 else null
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // VKUI: repost_outline_16 icon
                            Icon(
                                Icons.Outlined.Repeat,
                                contentDescription = "Репост",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            // VKUI: vkuiAvatar__host 24x24 (nested)
                            if (origPhoto != null) {
                                AsyncImage(
                                    model = origPhoto,
                                    contentDescription = origName,
                                    modifier = Modifier.size(24.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = origName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // VKUI: h4, vkuiSubhead__densityCompact vkuiTypography__weight2 vkuiTypography__accent
                            Text(
                                text = origName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAuthorClick(original) },
                            )
                        }
                        if (original.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = re.pinok.util.linkifyVkText(
                                    text = original.text,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    onUrlClick = { url -> re.pinok.util.openUrlExternal(ctx, url) },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Фото из оригинального поста.
                        val origPhotos = original.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
                        if (origPhotos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val origUrls = origPhotos.mapNotNull { it.photo?.largestUrl }
                            if (origUrls.isNotEmpty()) {
                                AsyncImage(
                                    model = origUrls.first(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            // ─── VKUI: post footer — vkuiFlex__justifySpaceBetween, 4px vertical padding ───
            // Actions: 8px row gap, 16px column gap. Date: post_date_block_preview (right, accent, underline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                var showReactions by remember { mutableStateOf(false) }
                Box {
                    ActionIcon(
                        icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        count = likeCount,
                        tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onLikeToggle(post) },
                        onLongClick = { showReactions = true },
                    )
                    if (showReactions) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-48).dp)
                                .zIndex(10f),
                        ) {
                            ReactionPicker(
                                onDismiss = { showReactions = false },
                                onSelect = { reaction ->
                                    onReaction(post, reaction)
                                },
                            )
                        }
                    }
                }
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = post.comments?.count ?: 0,
                    onClick = { onCommentClick(post) },
                )
                ActionIcon(
                    icon = Icons.Outlined.Repeat,
                    count = post.reposts?.count ?: 0,
                    onClick = { onRepostClick(post) },
                )
                // Время поста — обычный текст, без ссылки
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Attachment.Photo>,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val photosWithUrl = photos.mapNotNull { photo ->
        val size = PhotoSizes.best(photo.sizes)
        val url = size?.url ?: return@mapNotNull null
        val ratio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 1f
        Triple(photo, url, ratio)
    }
    if (photosWithUrl.isEmpty()) return
    val allUrls = photosWithUrl.map { it.second }

    // 1-2 фото — карусель с счётчиком N/M (как в ВК).
    if (photosWithUrl.size <= 2) {
        val pagerState = rememberPagerState(pageCount = { photosWithUrl.size })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val (_, url, ratio) = photosWithUrl[page]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.5f, 2f))
                        .clickable { onPhotoClick(allUrls, page) },
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            // Счётчик "N/M" в правом верхнем углу (если > 1 фото).
            if (photosWithUrl.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${photosWithUrl.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    } else {
        // 3+ фото — сетка (FlowRow).
        val colCount = if (photosWithUrl.size <= 4) 2 else 3
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = colCount,
        ) {
            photosWithUrl.forEachIndexed { index, (_, url, ratio) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.5f, 2f))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick(allUrls, index) },
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: Video, onClick: (Video) -> Unit) {
    val thumbUrl = video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                if (thumbUrl != null) {
                    AsyncImage(model = thumbUrl, contentDescription = video.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                if (video.duration > 0) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                            style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            // #VIDEO-TITLE-COMMENTS: название + счётчики (просмотры/комментарии),
            // если доступны — как в веб-VK под превью видео-вложения.
            if (video.title.isNotBlank() || video.views > 0 || video.commentsCount > 0) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (video.title.isNotBlank()) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (video.views > 0 || video.commentsCount > 0) {
                        val meta = buildList {
                            if (video.views > 0) add("${video.views.toCountString()} просмотров")
                            if (video.commentsCount > 0) add("${video.commentsCount.toCountString()} комментариев")
                        }.joinToString(" • ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(count.toCountString(), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
private fun LinkCard(link: Attachment.Link, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            if (link.photo?.largestUrl != null) {
                AsyncImage(
                    model = link.photo.largestUrl,
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

@Composable
private fun DocAttachmentCard(doc: Attachment.Doc, onOpen: () -> Unit = {}) {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    post: Post,
    onDismiss: () -> Unit,
    onSubmitComment: (String, String?) -> Unit,
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
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachmentString by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    // Расширенный пикер (Музыка/Видео) — общий с чатом и комментариями к посту.
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableStateOf(0) } // 0=Музыка, 1=Видео

    // Лаунчеры для вложений.
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val file = File(ctx.cacheDir, "comment_photo_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                val attachment = app.apiClient.uploadDocForComment(file)
                if (attachment != null) {
                    attachmentString = attachment
                    attachedFileName = "Фото"
                } else {
                    AppLog.w("CommentsBottomSheet", "uploadDocForComment returned null")
                }
                file.delete()
            } catch (e: Exception) {
                AppLog.e("CommentsBottomSheet", "upload photo error", e)
            } finally {
                uploading = false
            }
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

    // Загружаем комментарии при открытии sheet.
    LaunchedEffect(post.id) {
        scope.launch {
            loading = true
            try {
                val result = app.apiClient.wallGetComments(post.ownerId, post.id, count = 50)
                comments = result.comments
                profiles = result.profiles
            } catch (e: Exception) {
                AppLog.e("FeedScreen", "Failed to load comments", e)
            } finally {
                loading = false
            }
        }
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
                            CommentRow(
                                comment = comment,
                                author = profiles[comment.fromId],
                                postOwnerId = post.ownerId,
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

            // Поле ввода нового комментария с вложениями
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        onVideo = {
                            attachmentPickerTab = 1
                            showAttachmentPicker = true
                        },
                        onAudio = {
                            attachmentPickerTab = 0
                            showAttachmentPicker = true
                        },
                        onFile = {
                            fileLauncher.launch(arrayOf("*/*"))
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
                        val optimistic = Comment(
                            id = -System.currentTimeMillis(),
                            fromId = 0,
                            date = System.currentTimeMillis() / 1000,
                            text = if (msg.isBlank()) "📎 $attachedFileName" else msg,
                        )
                        localComments = localComments + optimistic
                        val textToSend = msg
                        val attachmentToSend = attachmentString
                        val hadAttachment = attachedFileName != null
                        inputText = ""
                        attachedFileName = null
                        attachmentString = null
                        scope.launch {
                            sending = true
                            onSubmitComment(textToSend, attachmentToSend)
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

    // Единый пикер «Музыка/Видео» из библиотеки VK — открывается при выборе
    // соответствующего пункта в UnifiedAttachMenu. Видео/аудио прикрепляются
    // к комментарию как "video{ownerId}_{id}" / "audio{ownerId}_{id}".
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
            onPickAudio = { track ->
                val att = if (track.accessKey != null) {
                    "audio${track.ownerId}_${track.id}_${track.accessKey}"
                } else {
                    "audio${track.ownerId}_${track.id}"
                }
                attachmentString = att
                attachedFileName = "Музыка: ${track.title}"
                showAttachmentPicker = false
            },
            onPickVideo = { video ->
                val att = if (video.accessKey != null) {
                    "video${video.ownerId}_${video.id}_${video.accessKey}"
                } else {
                    "video${video.ownerId}_${video.id}"
                }
                attachmentString = att
                attachedFileName = "Видео: ${video.title.ifBlank { "видео" }}"
                showAttachmentPicker = false
            },
        )
    }
}

@Composable
private fun CommentRow(comment: Comment, author: UserProfile?, postOwnerId: Long = 0L) {
    val name = author?.let { "${it.firstName} ${it.lastName}" } ?: "id${comment.fromId}"
    val photo = author?.photo100
    // Sprint 2, P1-2 (#89): локальное состояние лайка комментария.
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var isLiked by remember(comment.id) { mutableStateOf(comment.isLiked) }
    var likeCount by remember(comment.id) { mutableStateOf(comment.likesCount) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.date.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodySmall,
            )
            // Sprint 2, P1-2 (#89): кликабельная кнопка лайка комментария.
            if (postOwnerId != 0L && comment.id > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val newLiked = !isLiked
                        isLiked = newLiked
                        likeCount = (likeCount + (if (newLiked) 1 else -1)).coerceAtLeast(0)
                        scope.launch {
                            val newCount = if (newLiked) {
                                app.apiClient.likesAdd("comment", postOwnerId, comment.id)
                            } else {
                                app.apiClient.likesDelete("comment", postOwnerId, comment.id)
                            }
                            if (newCount >= 0) {
                                likeCount = newCount
                            } else {
                                isLiked = !newLiked
                                likeCount = (likeCount + (if (newLiked) -1 else 1)).coerceAtLeast(0)
                            }
                        }
                    }.padding(vertical = 2.dp),
                ) {
                    Icon(
                        if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                    )
                    if (likeCount > 0) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = likeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else if (likeCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = likeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}


/**
 * Sprint 4: Карточка опроса в ленте.
 */
@Composable
private fun PollCard(
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

