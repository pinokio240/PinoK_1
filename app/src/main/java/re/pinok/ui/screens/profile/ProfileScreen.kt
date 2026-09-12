package re.pinok.ui.screens.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
// П-8-REACT: ExperimentalFoundationApi — combinedClickable (long-press лайка
// → пикер реакций; страховочная аннотация, см. ActionIcon).
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
// П-8-REACT: long-press по лайку записи стены (named-аргументы — как в
// FeedScreen.ActionIcon :2391).
import androidx.compose.foundation.combinedClickable
// П-6b (#PROFILE-GAP-6b): 7 вкладок контента не помещаются на узких экранах —
// чип-ряд скроллится горизонтально.
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
// Fix #378: закреплённая кнопка выхода — поднимаем её над жестовой навигацией.
// П-8-REACT: пикер реакций всплывает НАД кнопкой лайка (паттерн FeedScreen :2198).
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
// П-7-AB: Flag — пункт «Пожаловаться» в «⋯»-меню записи; Groups — строка «Подписки».
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
// Fix #378: разделитель над закреплённой кнопкой выхода.
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
// Fix #371/#373: «Показать ещё» — underline link style (паттерн FeedScreen VKUI).
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// П-8-REACT: пикер реакций рисуется поверх соседей внутри карточки (zIndex).
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Bookmark
import re.pinok.data.model.Friend
import re.pinok.data.model.Post
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.PlayerConnection
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
// #POST-CAROUSEL-EVERYWHERE (22-B): общий компонент карусели фото поста.
import re.pinok.ui.components.PostPhotoGrid
// Fix #372/#373: карусель видео в постах/репостах профиля — общий компонент
// (тот же, что в ленте/сообществах, Fix #366).
import re.pinok.ui.components.PostVideoCarousel
import re.pinok.ui.components.PlaylistAttachmentCard
import re.pinok.ui.components.RepostDialog
import re.pinok.ui.components.ScrollToTopFab
// П-8-REACT: пикер реакций стены — переиспользование feed-компонента как есть
// (+ аддитивный selectedReactionId с дефолтом; FeedScreen не затронут).
// Fix #376: link/poll/doc вложения — карточки ленты, сделаны internal
// (FeedScreen). Порядок/параметры рендера 1:1 с PostCard ленты.
import re.pinok.ui.screens.feed.DocAttachmentCard
import re.pinok.ui.screens.feed.LinkCard
import re.pinok.ui.screens.feed.PollCard
import re.pinok.ui.screens.feed.ReactionEntry
import re.pinok.ui.screens.feed.ReactionPicker
import re.pinok.util.AppLog
// Fix #371: VK inline-ссылки [#alias|display|url] + URL в тексте поста кликабельны
// (тот же линкер, что в ленте FeedScreen:2280).
import re.pinok.util.linkifyVkText
import re.pinok.util.openUrlExternal
import re.pinok.util.toCountString
import re.pinok.util.toRelativeTime

// П-1 (#PROFILE-SNAP): вкладки контента профиля. Статический набор — web-набор
// users.getContentTabs известен (инвентарь §1.1.3: music,videos,photos,…),
// вызывать usersGetContentTabs для этого не обязательно.
private const val PROFILE_TAB_WALL = "wall"
private const val PROFILE_TAB_MUSIC = "music"
private const val PROFILE_TAB_VIDEO = "video"
private const val PROFILE_TAB_PHOTO = "photo"

// П-6b (#PROFILE-GAP-6b): остаток вкладок §3.2 снапшота — Клипы/Статьи/Закладки.
private const val PROFILE_TAB_CLIPS = "clips"
private const val PROFILE_TAB_ARTICLES = "articles"
private const val PROFILE_TAB_BOOKMARKS = "bookmarks"

private val PROFILE_CONTENT_TABS: List<Pair<String, String>> = listOf(
    PROFILE_TAB_WALL to "Стена",
    PROFILE_TAB_MUSIC to "Музыка",
    PROFILE_TAB_VIDEO to "Видео",
    PROFILE_TAB_PHOTO to "Фото",
    // П-6b (#PROFILE-GAP-6b): закрытие остатка плана (инвентарь §5 п.2 —
    // «секции правой колонки (…клипы/статьи)» и §3.2 Клипы/Статьи/Закладки).
    PROFILE_TAB_CLIPS to "Клипы",
    PROFILE_TAB_ARTICLES to "Статьи",
    PROFILE_TAB_BOOKMARKS to "Закладки",
)

// П-1: подвкладки стены — значения wall.get(filter) по канонике users.getWallTabs
// (all | owner | archived). «Архив» показывается только если users.getWallTabs
// вернул archived с count>0 (иначе серверная поддержка фильтра не гарантирована).
private const val WALL_FILTER_ALL = "all"
private const val WALL_FILTER_OWNER = "owner"
private const val WALL_FILTER_ARCHIVED = "archived"

// W33-c: страницы пагинации вкладок профиля («разделы целиком» — запрос
// юзера волны 33: раньше Музыка=10/Видео=9/Фото=12 жёстко обрезались).
private const val PROFILE_MUSIC_PAGE = 100
private const val PROFILE_VIDEO_PAGE = 20
private const val PROFILE_PHOTO_PAGE = 60
private const val PROFILE_GIFTS_PAGE = 10

@Composable
fun ProfileScreen(
    // W31-b: параметр onLogout УДАЛЁН вместе со всем logout-UI экрана —
    // выход из аккаунта теперь ТОЛЬКО в боковом drawer (SovaNavHost, Fix #369).
    onVideoClick: (Video) -> Unit = {},
    // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
    // П-3 (#PROFILE-SNAP): кнопка «Редактировать профиль» → EditProfileScreen
    // (маршрут profile_edit: account.saveProfileInfo + аватар/обложка).
    // Параметр с дефолтом — существующие вызовы (SovaNavHost) обновлены,
    // прочие вызовы не обязательны.
    onEditProfileClick: () -> Unit = {},
    // П-6b (#PROFILE-GAP-6b): тап по карточке «Возможно, вы знакомы» / юзер-закладке
    // → чужой профиль (Screen.UserProfile, паттерн FriendsScreen.onUserClick).
    onUserClick: (Long) -> Unit = {},
    // П-6b: тап по пост-закладке → PostDetailScreen (паттерн BookmarksScreen.
    // onPostClick: PostHolder.last + navigate). Отдельный колбэк — onCommentClick
    // занят постами стены (см. вызов WallPostCard).
    onPostClick: (Post) -> Unit = {},
    // П-7-AB: тапы правой колонки веба (списки): чипы «Друзья»/«Подписчики»
    // CountersRow и строка «Подписки» → FollowersSubscriptionsScreen /
    // Screen.Friends. onFollowersClick/onSubscriptionsClick получают id
    // владельца списка (p.id своего профиля).
    onFriendsClick: () -> Unit = {},
    onFollowersClick: (Long) -> Unit = {},
    onSubscriptionsClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // #POST-CAROUSEL-EVERYWHERE (22-B): флаг карусели фото — один общий
    // SovaPrefs.feedCarouselEnabled (управляет лентой/сообществами/профилями/
    // пост-детейлом; настройка «Настройки → Лента → „Карусель фото в постах"»).
    // СКОУП-РЕШЕНИЕ: читается ОДИН раз на уровне экрана (здесь, где уже есть
    // app) и пробрасывается параметром в WallPostCard → RepostBlock (два
    // места рендера фото — пост и репост-карточка), а не читается на каждый кадр.
    val prefsSnap by app.prefs.data.collectAsState(initial = null)
    // NULL-ЯВНО: Snapshot-initial до первого эмита; дефолт true = SovaPrefs default.
    val carouselEnabled: Boolean = prefsSnap?.feedCarouselEnabled ?: true
    // Fix #360 #PROFILE-SUGGEST-TOGGLE: блок «Возможно, вы знакомы» показывается
    // ТОЛЬКО при включённой настройке (Настройки → Приватность → «Профиль»).
    val showFriendSuggestions: Boolean = prefsSnap?.profileFriendSuggestions ?: false  // NULL-ЯВНО (prefs-паттерн carouselEnabled выше)
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // User request 2026-07-12: кнопка «Создать пост» перенесена из ленты в профиль.
    val creatingPost = remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> selectedPhotoUri = uri }
    // Флаг: нужно перезагрузить стену после создания нового поста.
    var reloadWallTrigger by remember { mutableStateOf(0) }

    // ══ П-6a (#PROFILE-GAP-6a): «Действия» поста стены + лайк (остаток скоупа П-1, §5 п.2) ══
    // Optimistic-состояния лайков (паттерн FeedScreen.likesState): key "ownerId_id"
    // → (isLiked, count). Пустая карта = показываем серверные post.likes
    // (стартовое состояние — user_likes из wall.get, поле есть в модели Post).
    val likeStates = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    // ══ П-8-REACT: «моя реакция» на запись стены (long-press по лайку → пикер,
    // паттерн FeedScreen :2186-2210). key "ownerId_id" → reaction_id моей текущей
    // реакции; 0 = реакции нет/неизвестна. При ОТСУТСТВИИ ключа — серверная
    // post.reactions.user_reacted (модель Post.Reactions, парсер VKApiClient
    // :9868; сама модель — core/data, 0 diff по правилам задачи).
    val reactionStates = remember { mutableStateMapOf<String, Int>() }
    // Ключи записей с лайком «в полёте» (повторный клик игнорируется — disabled).
    val likeInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // Ключи записей с действием «в полёте» (pin/unpin/delete/edit) — меню disabled.
    val postActionInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // Запись, ожидающая подтверждения удаления (AlertDialog ниже).
    val deletingPost = remember { mutableStateOf<Post?>(null) }
    // Запись, ожидающая правки текста (AlertDialog ниже).
    val editingPost = remember { mutableStateOf<Post?>(null) }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

    // ── П-1 (#PROFILE-SNAP): свой профиль — статус, подвкладки стены, вкладки контента ──
    // Активная подвкладка стены (wall.get filter).
    var wallFilter by remember { mutableStateOf(WALL_FILTER_ALL) }
    // Индикатор перезагрузки ленты (смена фильтра).
    var wallLoading by remember { mutableStateOf(false) }
    // #PROFILE-WALL-PAGING (волна 39): пагинация ленты профиля (жалоба
    // пользователя: «пагинация страницы профиля бесконечная — не доходит
    // до самого начала»). Раньше грузились только первые 20 записей
    // (wallGet count=20) без догрузки — до старых записей было не доскроллить.
    // serverOffset — по RAW-странице (урок волны 31 #AUDIO-PAGING);
    // hasMore — full-page-паттерн (VK response.count у wall.get
    // фильтрованному «all» не доверяем — прецедент заниженного total).
    var wallServerOffset by remember { mutableStateOf(0) }
    var wallLoadingMore by remember { mutableStateOf(false) }
    var wallHasMore by remember { mutableStateOf(false) }
    // «Архив» доступен, если users.getWallTabs вернул archived с count>0.
    var hasArchiveWallTab by remember { mutableStateOf(false) }
    // Подарки (gifts.get — сырые JsonObject, парсинг в GiftsSection).
    var gifts by remember { mutableStateOf<List<JsonObject>?>(null) }
    // Активная вкладка контента.
    var selectedContentTab by remember { mutableStateOf(PROFILE_TAB_WALL) }
    // Счётчик ретраев вкладок контента (кнопка «Повторить»).
    var contentRetryTick by remember { mutableStateOf(0) }
    // Музыка (audio.get) — ленивая загрузка при первом выборе вкладки.
    var musicLoaded by remember { mutableStateOf(false) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicError by remember { mutableStateOf<String?>(null) }
    var musicTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // Видео (video.get).
    var videoLoaded by remember { mutableStateOf(false) }
    var videoLoading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    // Фото (photos.getAll).
    var photoLoaded by remember { mutableStateOf(false) }
    var photoLoading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // ══ П-6b (#PROFILE-GAP-6b): остаток плана — вкладки Клипы/Статьи/Закладки ══
    // Клипы (shortVideo.getOwnerVideos) — Video с вертикальным постером.
    var clipsLoaded by remember { mutableStateOf(false) }
    var clipsLoading by remember { mutableStateOf(false) }
    var clipsError by remember { mutableStateOf<String?>(null) }
    var clips by remember { mutableStateOf<List<Video>>(emptyList()) }
    // Статьи (articles.getOwnerPublished) — сырые JsonObject (парсинг в секции).
    var articlesLoaded by remember { mutableStateOf(false) }
    var articlesLoading by remember { mutableStateOf(false) }
    var articlesError by remember { mutableStateOf<String?>(null) }
    var articles by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // Закладки (fave.get) — свои закладки, с подгрузкой «Загрузить ещё».
    var bookmarksLoaded by remember { mutableStateOf(false) }
    var bookmarksLoading by remember { mutableStateOf(false) }
    var bookmarksError by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var bookmarksHasMore by remember { mutableStateOf(false) }
    var bookmarksLoadingMore by remember { mutableStateOf(false) }

    // W33-c: пагинация вкладок Музыка/Видео/Фото/Подарки. serverOffset —
    // по RAW-странице (ДО фильтров), а не по отфильтрованному списку
    // (урок волны 31 #AUDIO-PAGING: offset от отфильтрованного списка
    // = сдвиг окна пагинации и ранний «стоп»).
    var musicServerOffset by remember { mutableStateOf(0) }
    var musicTotal by remember { mutableStateOf(0) }
    var musicLoadingMore by remember { mutableStateOf(false) }
    var musicHasMore by remember { mutableStateOf(false) }
    var videoServerOffset by remember { mutableStateOf(0) }
    var videoLoadingMore by remember { mutableStateOf(false) }
    var videoHasMore by remember { mutableStateOf(false) }
    var photoServerOffset by remember { mutableStateOf(0) }
    var photoLoadingMore by remember { mutableStateOf(false) }
    var photoHasMore by remember { mutableStateOf(false) }
    var giftsServerOffset by remember { mutableStateOf(0) }
    var giftsLoadingMore by remember { mutableStateOf(false) }
    var giftsHasMore by remember { mutableStateOf(false) }
    // «Возможно, вы знакомы» (friends.getRecommendations): null = ещё грузится /
    // ошибка → секция не рисуется (паттерн подарков П-1).
    var friendSuggestions by remember { mutableStateOf<List<Friend>?>(null) }
    // friend_status из friends.add (1 = добавлен, 2 = заявка отправлена).
    val suggestionSent = remember { mutableStateMapOf<Long, Int>() }
    val suggestionInFlight = remember { mutableStateMapOf<Long, Boolean>() }
    // П-7-AB: счётчик «Подписок» (users.getSubscriptions → followSubscriptionsTotal,
    // параллельный добор как подарки). Не распознан/ошибка → строка «Подписки»
    // без числа (навигационная ссылка, счётчик не имитируется).
    var subscriptionsCount by remember { mutableStateOf<Int?>(null) }
    // П-7-AB: «Пожаловаться» на запись (wall.markAsSpam) — VK-семантика:
    // доступна на ЧУЖИХ записях (ownerId != id текущего пользователя — здесь
    // p.id своего профиля; решается в items() по посту: showReport).
    val reportingPost = remember { mutableStateOf<Post?>(null) }
    var reportInFlight by remember { mutableStateOf(false) }
    // Диалог правки статуса (status.set).
    var showStatusDialog by remember { mutableStateOf(false) }
    // W31-b: стейт showLogoutConfirm удалён вместе с logout-UI — подтверждение
    // выхода осталось только в drawer (SovaNavHost, Fix #369).
    var statusSaving by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            errorText = null
            try {
                // П-1 (#PROFILE-SNAP): usersGetFullExtended вместо usersGet —
                // тянет field=cover (обложка профиля) и веб-набор полей
                // (PROFILE-P0-2); всё, что рисует экран (status/counters/
                // verified/bdate/city), парсится так же.
                val prof = app.apiClient.usersGetFullExtended(null)
                profile = prof
                if (prof != null) {
                    // П-1: доступность «Архива» (users.getWallTabs) и подарки
                    // (gifts.get) — параллельные доборы, стену не задерживают.
                    scope.launch {
                        val wallTabs = app.apiClient.usersGetWallTabs(prof.id)
                        hasArchiveWallTab = wallTabs.any { it.type == "archived" && it.count > 0 }
                    }
                    scope.launch {
                        try {
                            val giftPage = app.apiClient.giftsGet(prof.id, count = PROFILE_GIFTS_PAGE)
                            gifts = giftPage
                            giftsServerOffset = giftPage.size
                            giftsHasMore = giftPage.size >= PROFILE_GIFTS_PAGE
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "Gifts load failed", e)
                        }
                    }
                    // П-6b (#PROFILE-GAP-6b): «Возможно, вы знакомы» — параллельный
                    // добор (паттерн подарков П-1). Пусто/ошибка → секция не рисуется.
                    // Fix #360 #PROFILE-SUGGEST-TOGGLE: загрузка ТОЛЬКО когда блок
                    // включён в настройках (по умолчанию выключен) — лишний
                    // friends.getRecommendations при выключенном блоке не делаем.
                    if (showFriendSuggestions) {
                        scope.launch {
                            try {
                                friendSuggestions = app.apiClient.friendsGetRecommendations(count = 10)
                            } catch (e: Exception) {
                                AppLog.e("ProfileScreen", "Friend suggestions load failed", e)
                            }
                        }
                    }
                    // П-7-AB: счётчик «Подписок» правой колонки (users.getSubscriptions
                    // count=1 → total из response). Параллельный добор (паттерн
                    // подарков); не распознан → строка «Подписки» без числа.
                    scope.launch {
                        try {
                            subscriptionsCount = followSubscriptionsTotal(
                                app.apiClient.usersGetSubscriptions(userId = prof.id, count = 1),
                            )
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "Subscriptions count load failed", e)
                        }
                    }
                    val wall = app.apiClient.wallGet(ownerId = prof.id, count = 20)
                    // Fix #53: защитная дедупликация на уровне UI.
                    posts = wall
                        .filter { it.id > 0 && it.ownerId != 0L }
                        .distinctBy { "${it.ownerId}_${it.id}" }
                    // #PROFILE-WALL-PAGING: курсор следующей страницы + full-page-стоп.
                    wallServerOffset = wall.size
                    wallHasMore = wall.size >= 20
                    AppLog.i("ProfileScreen", "Loaded profile + ${wall.size} wall posts")
                } else {
                    errorText = app.apiClient.lastApiError ?: "Не удалось загрузить профиль"
                }
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Failed to load profile", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Перезагрузка стены после создания нового поста (reloadWallTrigger меняется).
    LaunchedEffect(reloadWallTrigger) {
        if (reloadWallTrigger == 0) return@LaunchedEffect // пропускаем первичную загрузку
        scope.launch {
            val p = profile ?: return@launch
            try {
                // П-1: перезагрузка уважает активную подвкладку стены (wall.get filter).
                val wall = app.apiClient.wallGetWithFilter(ownerId = p.id, filter = wallFilter, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                // #PROFILE-WALL-PAGING: перезагрузка сбрасывает курсор пагинации.
                wallServerOffset = wall.size
                wallHasMore = wall.size >= 20
                AppLog.i("ProfileScreen", "Reloaded wall after new post: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Reload wall failed", e)
            }
        }
    }

    // П-1 (#PROFILE-SNAP): подвкладки стены. Смена фильтра перезагружает ленту
    // через wall.get(filter=all|owner|archived). Первое значение пропускаем —
    // начальная загрузка идёт в LaunchedEffect(Unit) выше.
    var wallFilterStarted by remember { mutableStateOf(false) }
    LaunchedEffect(wallFilter) {
        if (!wallFilterStarted) {
            wallFilterStarted = true
            return@LaunchedEffect
        }
        scope.launch {
            val prof = profile ?: return@launch
            wallLoading = true
            try {
                val wall = app.apiClient.wallGetWithFilter(ownerId = prof.id, filter = wallFilter, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                // #PROFILE-WALL-PAGING: смена фильтра — новый список, курсор с нуля.
                wallServerOffset = wall.size
                wallHasMore = wall.size >= 20
                AppLog.i("ProfileScreen", "Wall filter=$wallFilter loaded: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Wall filter load failed", e)
            } finally {
                wallLoading = false
            }
        }
    }

    // П-1: вкладки контента грузятся ЛЕНИВО при первом выборе
    // (ключ-флаг loaded + LaunchedEffect по активной вкладке).
    LaunchedEffect(selectedContentTab, contentRetryTick) {
        val ownerId = profile?.id ?: return@LaunchedEffect
        when (selectedContentTab) {
            PROFILE_TAB_MUSIC -> if (!musicLoaded && !musicLoading) {
                musicLoading = true
                musicError = null
                try {
                    // W33-c: полная пагинация (audioGetWithCount → response.total);
                    // serverOffset = RAW-страница (до фильтра playability).
                    val (total, page) =
                        app.apiClient.audioGetWithCount(PROFILE_MUSIC_PAGE, 0, ownerId)
                    musicTotal = total
                    musicServerOffset = page.size
                    musicTracks = page.filter { it.id > 0L && !it.url.isNullOrBlank() }
                    // #AUDIO-PAGING-ALL (волна 38): было musicServerOffset < total —
                    // VK занижает count у аудио (прецедент AudioLibraryPager: total
                    // НЕ годится как стоп-условие) → «Показать ещё» пропадал раньше
                    // времени, до конца списка было не доскроллить. Теперь честный
                    // паттерн полной страницы: полная RAW-страница → догружаем дальше.
                    musicHasMore = page.size >= PROFILE_MUSIC_PAGE
                    musicLoaded = true
                    AppLog.i("ProfileScreen", "Music tab loaded: ${musicTracks.size} tracks (total=$total)")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Music tab load failed", e)
                    musicError = "Ошибка: ${e.message}"
                } finally {
                    musicLoading = false
                }
            }
            PROFILE_TAB_VIDEO -> if (!videoLoaded && !videoLoading) {
                videoLoading = true
                videoError = null
                try {
                    // W33-c: полная пагинация (total у video.get недоступен —
                    // hasMore по заполненности RAW-страницы, паттерн закладок).
                    val page = app.apiClient.videoGet(ownerId = ownerId, count = PROFILE_VIDEO_PAGE)
                    videoServerOffset = page.size
                    videos = page.filter { it.id > 0L }
                    videoHasMore = page.size >= PROFILE_VIDEO_PAGE
                    videoLoaded = true
                    AppLog.i("ProfileScreen", "Video tab loaded: ${videos.size} videos")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Video tab load failed", e)
                    videoError = "Ошибка: ${e.message}"
                } finally {
                    videoLoading = false
                }
            }
            PROFILE_TAB_PHOTO -> if (!photoLoaded && !photoLoading) {
                photoLoading = true
                photoError = null
                try {
                    photos = app.apiClient.photosGetAll(
                        ownerId = ownerId, count = PROFILE_PHOTO_PAGE, offset = 0)
                    photoServerOffset = photos.size
                    photoHasMore = photos.size >= PROFILE_PHOTO_PAGE
                    photoLoaded = true
                    AppLog.i("ProfileScreen", "Photo tab loaded: ${photos.size} photos")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Photo tab load failed", e)
                    photoError = "Ошибка: ${e.message}"
                } finally {
                    photoLoading = false
                }
            }
            // П-6b (#PROFILE-GAP-6b): вкладка «Клипы» — shortVideo.getOwnerVideos.
            PROFILE_TAB_CLIPS -> if (!clipsLoaded && !clipsLoading) {
                clipsLoading = true
                clipsError = null
                try {
                    clips = app.apiClient.shortVideoGetOwnerVideos(ownerId = ownerId, count = 30)
                        .filter { it.id > 0L }
                    clipsLoaded = true
                    AppLog.i("ProfileScreen", "Clips tab loaded: ${clips.size} clips")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Clips tab load failed", e)
                    clipsError = "Ошибка: ${e.message}"
                } finally {
                    clipsLoading = false
                }
            }
            // П-6b: вкладка «Статьи» — articles.getOwnerPublished (сырые JsonObject;
            // count=20 — вкладка-список, веб-сайдбар запрашивает 3 — см. KDoc секции).
            PROFILE_TAB_ARTICLES -> if (!articlesLoaded && !articlesLoading) {
                articlesLoading = true
                articlesError = null
                try {
                    articles = app.apiClient.articlesGetOwnerPublished(ownerId = ownerId, count = 20)
                    articlesLoaded = true
                    AppLog.i("ProfileScreen", "Articles tab loaded: ${articles.size} items")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Articles tab load failed", e)
                    articlesError = "Ошибка: ${e.message}"
                } finally {
                    articlesLoading = false
                }
            }
            // П-6b: вкладка «Закладки» — fave.get (свои закладки; первая страница,
            // подгрузка — loadMoreBookmarksPage).
            PROFILE_TAB_BOOKMARKS -> if (!bookmarksLoaded && !bookmarksLoading) {
                bookmarksLoading = true
                bookmarksError = null
                try {
                    val page = app.apiClient.faveGet(count = BOOKMARKS_PAGE_SIZE)
                    bookmarks = page
                    bookmarksHasMore = page.size >= BOOKMARKS_PAGE_SIZE
                    bookmarksLoaded = true
                    AppLog.i("ProfileScreen", "Bookmarks tab loaded: ${page.size} items")
                    // #BOOKMARKS-FIX (2026-09-12): faveGet возвращает emptyList и при
                    // ошибке API (не бросает) — раньше вкладка молча показывала «пусто».
                    // Теперь честная русская причина (как в BookmarksScreen панели).
                    if (page.isEmpty()) {
                        val human = app.apiClient.lastApiErrorHuman()
                        if (human != null) bookmarksError = "Закладки не загрузились: $human"
                    }
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Bookmarks tab load failed", e)
                    bookmarksError = "Ошибка: ${e.message}"
                } finally {
                    bookmarksLoading = false
                }
            }
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val p = profile
    if (p == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorText ?: "Профиль не загружен",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            // W31-b: аварийная кнопка «Выйти» удалена — выход из аккаунта
            // ТОЛЬКО в боковом drawer (Fix #369). Из ошибки навигация доступна
            // штатно (drawer/нижняя навигация).
        }
        return
    }

    // ══ П-6a (#PROFILE-GAP-6a): обработчики «Действий» поста стены и лайка ══
    val context = LocalContext.current

    // Лайк/анлайк записи стены: optimistic (isLiked + счётчик ±1) → likes.add /
    // likes.delete (паттерн FeedScreen.onLikeToggle; обе функции возвращают Int —
    // обновлённое количество лайков, -1 при ошибке). При ошибке — откат +
    // Toast lastApiError. Повторный клик во время полёта игнорируется.
    fun toggleWallPostLike(clicked: Post) {
        val key = "${clicked.ownerId}_${clicked.id}"
        if (likeInFlight.containsKey(key)) return
        val current = likeStates[key]
            ?: ((clicked.likes?.userLikes == 1) to (clicked.likes?.count ?: 0))
        val newLiked = !current.first
        likeStates[key] = newLiked to (current.second + (if (newLiked) 1 else -1)).coerceAtLeast(0)
        likeInFlight[key] = true
        scope.launch {
            val newCount = try {
                if (newLiked) {
                    app.apiClient.likesAdd("post", clicked.ownerId, clicked.id)
                } else {
                    app.apiClient.likesDelete("post", clicked.ownerId, clicked.id)
                }
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "likes.add/delete failed", e)
                -1
            }
            likeInFlight.remove(key)
            if (newCount >= 0) {
                // VK подтвердил — фиксируем точное значение счётчика.
                likeStates[key] = newLiked to newCount
                // П-8-REACT: обычный клик (likes.add БЕЗ reaction_id) или снятие
                // лайка — «моя реакция» обнуляется: после снятия её нет; после
                // простого лайка reaction_id неизвестен → будущий long-press
                // пойдёт по цепочке likes.delete + likes.add (см.
                // applyWallPostReaction, случай C — идемпотентно корректен).
                reactionStates[key] = 0
            } else {
                likeStates[key] = current
                Toast.makeText(
                    context,
                    app.apiClient.lastApiError ?: "Не удалось оценить запись",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // ══ П-8-REACT: выбор реакции long-press'ом по лайку записи стены ══
    // (пикер — re/pinok/ui/screens/feed/ReactionPicker.kt, переиспользован как
    // есть + аддитивный параметр selectedReactionId; образец — FeedScreen.
    // onReaction :1437-1453, НО умнее: FeedScreen не различает «уже лайкнуто»
    // и всегда зовёт likes.add(+1 optimistic) — здесь 3 случая).
    //
    // ЧЕСТНО О «МОЕЙ РЕАКЦИИ»: модель Post несёт reactions.user_reacted
    // (Post.Reactions, парсер VKApiClient :9868) — reaction_id моей текущей
    // реакции или 0/absent. Отсюда:
    //  - reaction_id ИЗВЕСТЕН → «та же реакция» выключает лайк (likes.delete,
    //    случай B), «другая» — последовательно likes.delete + likes.add(новая)
    //    (VK не умеет смену реакции одним likes.add; FeedScreen так не делает —
    //    у него ветки «уже лайкнуто» нет вообще), случай C;
    //  - reaction_id НЕИЗВЕСТЕН (поле reactions не пришло — старые версии API /
    //    стены без reactions) → любая реакция на уже лайкнутую запись идёт по
    //    цепочке delete+add (она идемпотентно корректна и для «той же» реакции:
    //    снять-поставить ту же = то же состояние); пикер — без предвыделения.
    //    Модель править запрещено (core/data, 0 diff) — честное ограничение.
    //
    // ИЗВЕСТНОЕ КОСМЕТИЧЕСКОЕ РАСХОЖДЕНИЕ (преждесуществующее, унаследовано от
    // FeedScreen): маппинг emoji→reaction_id в ReactionPicker.VK_REACTIONS не
    // совпадает с семантикой KDoc VKApiClient.messagesReact :5327 (1=👍, 2=❤️,
    // 3=😂, 4=😭, 5=😡, 6=🎉, 7=🔥, 8=😮): пикер отдаёт 3=🔥, 5=😮, 6=😭, 7=😡,
    // 8=🙏. В API уходит только Int (истина — id); править ReactionPicker
    // запрещено (приоритет 0 правок задачи).
    fun applyWallPostReaction(clicked: Post, reaction: ReactionEntry) {
        val key = "${clicked.ownerId}_${clicked.id}"
        if (likeInFlight.containsKey(key)) return
        val current = likeStates[key]
            ?: ((clicked.likes?.userLikes == 1) to (clicked.likes?.count ?: 0))
        val prevReaction = reactionStates[key]
            ?: clicked.reactions?.userReacted?.takeIf { it > 0 }
            ?: 0
        if (!current.first) {
            // (A) НЕ лайкнуто: optimistic (true, count+1) → likes.add(reactionId).
            likeStates[key] = true to (current.second + 1)
            reactionStates[key] = reaction.reactionId
            likeInFlight[key] = true
            scope.launch {
                val newCount = try {
                    app.apiClient.likesAdd("post", clicked.ownerId, clicked.id, reactionId = reaction.reactionId)
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "likes.add(reaction) failed", e)
                    -1
                }
                likeInFlight.remove(key)
                if (newCount >= 0) {
                    likeStates[key] = true to newCount
                } else {
                    likeStates[key] = current
                    reactionStates[key] = prevReaction
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось поставить реакцию",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else if (prevReaction == reaction.reactionId) {
            // (B) УЖЕ лайкнуто ТЕМ ЖЕ reaction_id: выключить — optimistic
            // (false, count-1) → likes.delete.
            likeStates[key] = false to (current.second - 1).coerceAtLeast(0)
            reactionStates[key] = 0
            likeInFlight[key] = true
            scope.launch {
                val newCount = try {
                    app.apiClient.likesDelete("post", clicked.ownerId, clicked.id)
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "likes.delete(reaction) failed", e)
                    -1
                }
                likeInFlight.remove(key)
                if (newCount >= 0) {
                    likeStates[key] = false to newCount
                } else {
                    likeStates[key] = current
                    reactionStates[key] = prevReaction
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось снять реакцию",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            // (C) УЖЕ лайкнуто ДРУГОЙ (или НЕИЗВЕСТНОЙ — prevReaction==0, честное
            // ограничение выше) реакцией: VK не умеет смену одним likes.add →
            // ПОСЛЕДОВАТЕЛЬНО likes.delete + likes.add(новая). Счётчик оптимистично
            // НЕ трогаем (delete+add = ±0). Откат всей цепочки при ошибке любого
            // шага; при сбое add после успешного delete — компенсация: вернуть
            // прежнюю реакцию likes.add(prevReaction) (best-effort, только если
            // она была известна; иначе сервер остаётся «не лайкнуто», UI откатится
            // к прежнему виду — честная деградация, состояние восстановится при
            // следующей перезагрузке стены).
            reactionStates[key] = reaction.reactionId
            likeInFlight[key] = true
            scope.launch {
                val delCount = try {
                    app.apiClient.likesDelete("post", clicked.ownerId, clicked.id)
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "likes.delete(switch) failed", e)
                    -1
                }
                if (delCount < 0) {
                    likeInFlight.remove(key)
                    reactionStates[key] = prevReaction
                    Toast.makeText(
                        context,
                        app.apiClient.lastApiError ?: "Не удалось сменить реакцию",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val addCount = try {
                    app.apiClient.likesAdd("post", clicked.ownerId, clicked.id, reactionId = reaction.reactionId)
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "likes.add(switch) failed", e)
                    -1
                }
                likeInFlight.remove(key)
                if (addCount >= 0) {
                    likeStates[key] = true to addCount
                } else {
                    // Текст ошибки читаем ДО компенсации (иначе lastApiError
                    // перетрётся ответом/ошибкой restore-вызова).
                    val errMsg = app.apiClient.lastApiError ?: "Не удалось сменить реакцию"
                    if (prevReaction > 0) {
                        try {
                            app.apiClient.likesAdd("post", clicked.ownerId, clicked.id, reactionId = prevReaction)
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "likes.add(restore) failed", e)
                        }
                    }
                    likeStates[key] = current
                    reactionStates[key] = prevReaction
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Закрепить/открепить (wall.pin / wall.unpin): optimistic — флаг is_pinned
    // и подъём закреплённой записи вверх списка (как в VK); при ошибке — откат
    // всего списка + Toast lastApiError.
    fun toggleWallPostPin(clicked: Post) {
        val key = "${clicked.ownerId}_${clicked.id}"
        if (postActionInFlight.containsKey(key)) return
        val previous = posts
        val newPinned = !clicked.isPinnedBool
        posts = previous
            .map {
                if (it.ownerId == clicked.ownerId && it.id == clicked.id) {
                    it.copy(isPinned = if (newPinned) 1 else 0)
                } else {
                    it
                }
            }
            .sortedByDescending { it.isPinned }
        postActionInFlight[key] = true
        scope.launch {
            val ok = try {
                if (newPinned) {
                    app.apiClient.wallPin(clicked.ownerId, clicked.id)
                } else {
                    app.apiClient.wallUnpin(clicked.ownerId, clicked.id)
                }
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "wall.pin/unpin failed", e)
                false
            }
            postActionInFlight.remove(key)
            if (ok) {
                Toast.makeText(
                    context,
                    if (newPinned) "Запись закреплена" else "Запись откреплена",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                posts = previous
                Toast.makeText(
                    context,
                    app.apiClient.lastApiError
                        ?: if (newPinned) "Не удалось закрепить запись" else "Не удалось открепить запись",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Удаление записи (wall.delete) после подтверждения в AlertDialog: optimistic —
    // запись исчезает из списка сразу, при ошибке — возврат + Toast lastApiError.
    fun deleteWallPostConfirmed(target: Post) {
        val key = "${target.ownerId}_${target.id}"
        if (postActionInFlight.containsKey(key)) return
        deletingPost.value = null
        val previous = posts
        posts = previous.filter { it.ownerId != target.ownerId || it.id != target.id }
        postActionInFlight[key] = true
        scope.launch {
            val ok = try {
                app.apiClient.wallDelete(target.ownerId, target.id)
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "wallDelete failed", e)
                false
            }
            postActionInFlight.remove(key)
            if (ok) {
                Toast.makeText(context, "Запись удалена", Toast.LENGTH_SHORT).show()
            } else {
                posts = previous
                Toast.makeText(
                    context,
                    app.apiClient.lastApiError ?: "Не удалось удалить запись",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Редактирование текста записи (wall.edit) — только для текстовых постов
    // (покрытие сигнатуры достаточное: message + friends_only; см. KDoc
    // WallPostCard.editCovered). Текст применяется ПОСЛЕ ответа сервера
    // (wall.edit → response.post_id); при ошибке диалог остаётся открыт
    // с текстом lastApiError.
    fun editWallPostConfirmed(target: Post, newText: String) {
        val key = "${target.ownerId}_${target.id}"
        if (postActionInFlight.containsKey(key)) return
        postActionInFlight[key] = true
        editSaving = true
        scope.launch {
            val ok = try {
                app.apiClient.wallEdit(
                    ownerId = target.ownerId,
                    postId = target.id,
                    message = newText,
                    // wall.edit сбрасывает непереданные поля — friends_only передаём как есть.
                    friendsOnly = target.friendsOnly == true,
                )
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "wallEdit failed", e)
                false
            }
            postActionInFlight.remove(key)
            editSaving = false
            if (ok) {
                posts = posts.map {
                    if (it.ownerId == target.ownerId && it.id == target.id) it.copy(text = newText) else it
                }
                editingPost.value = null
                Toast.makeText(context, "Запись изменена", Toast.LENGTH_SHORT).show()
            } else {
                editError = app.apiClient.lastApiError ?: "Не удалось изменить запись"
            }
        }
    }

    // П-7-AB: жалоба на запись (wall.markAsSpam) после подтверждения в
    // AlertDialog. Успех → тост «Жалоба отправлена»; ошибка → тост lastApiError.
    // Во время полёта кнопки диалога disabled, повторный вызов игнорируется.
    fun reportWallPostConfirmed(target: Post) {
        if (reportInFlight) return
        reportInFlight = true
        scope.launch {
            val ok = try {
                app.apiClient.wallMarkAsSpam(ownerId = target.ownerId, postId = target.id)
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "wallMarkAsSpam failed", e)
                false
            }
            reportInFlight = false
            reportingPost.value = null
            Toast.makeText(
                context,
                if (ok) "Жалоба отправлена"
                else (app.apiClient.lastApiError ?: "Не удалось отправить жалобу"),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // ══ П-6b (#PROFILE-GAP-6b): «Возможно, вы знакомы» — добавление в друзья ══
    // friends.add(user_id): Int → friend_status (1 = стали друзьями, 2 = заявка
    // отправлена, -1/ошибка). Кнопка после успеха становится disabled:
    // «Заявка отправлена» (или «Добавлен» при мгновенном одобрении).
    // Повторный клик в полёте/после успеха игнорируется.
    fun addSuggestion(friend: Friend) {
        if (suggestionInFlight.containsKey(friend.id)) return
        if (suggestionSent.containsKey(friend.id)) return
        suggestionInFlight[friend.id] = true
        scope.launch {
            val status = try {
                app.apiClient.friendsAdd(friend.id)
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "friendsAdd failed", e)
                -1
            }
            suggestionInFlight.remove(friend.id)
            if (status > 0) {
                suggestionSent[friend.id] = status
                Toast.makeText(
                    context,
                    if (status == 1) "Добавлен в друзья" else "Заявка отправлена",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    app.apiClient.lastApiError ?: "Не удалось отправить заявку",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // П-6b: открытие закладки — dispatch по типу Bookmark (только типы с живой
    // навигацией; список «что открывается» — KDoc BookmarksTabSection).
    fun openBookmark(bookmark: Bookmark) {
        when (bookmark.type) {
            "post" -> bookmark.post?.let { onPostClick(it) }
            "video" -> bookmark.video?.let { onVideoClick(it) }
            "photo" -> {
                val url = bookmark.photo?.largestUrl
                if (url != null) photoViewerState.value = listOf(url) to 0
            }
            "link" -> bookmark.link?.url?.takeIf { it.isNotBlank() }?.let { openUrlExternal(context, it) }
            "user" -> bookmark.user?.let { onUserClick(it.id) }
        }
    }

    // П-6b: подгрузка закладок (fave.get offset) — «Загрузить ещё».
    fun loadMoreBookmarksPage() {
        if (bookmarksLoadingMore || bookmarksLoading || !bookmarksHasMore) return
        scope.launch {
            bookmarksLoadingMore = true
            try {
                val next = app.apiClient.faveGet(count = BOOKMARKS_PAGE_SIZE, offset = bookmarks.size)
                bookmarks = (bookmarks + next).distinctBy { b ->
                    Triple(
                        b.type,
                        b.post?.id ?: b.video?.id ?: b.photo?.id ?: b.user?.id ?: b.group?.id ?: 0L,
                        b.addedDate,
                    )
                }
                bookmarksHasMore = next.size >= BOOKMARKS_PAGE_SIZE
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Bookmarks load-more failed", e)
                Toast.makeText(
                    context,
                    app.apiClient.lastApiError ?: "Не удалось загрузить закладки",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                bookmarksLoadingMore = false
            }
        }
    }

    // W33-c: дозагрузка страниц вкладок (Музыка/Видео/Фото/Подарки).
    // offset — серверный (по RAW-страницам, см. стейт-блок выше); hasMore
    // у video/photo/gifts — по заполненности RAW-страницы (паттерн закладок),
    // у music — по response.total (audioGetWithCount).
    fun loadMoreMusicPage() {
        if (musicLoadingMore || musicLoading || !musicHasMore) return
        // NULL-ЯВНО: явная проверка вместо элвиса (правило новых строк).
        val prof = profile
        if (prof == null) return
        scope.launch {
            musicLoadingMore = true
            try {
                val (total, page) = app.apiClient.audioGetWithCount(
                    PROFILE_MUSIC_PAGE, musicServerOffset, prof.id)
                musicTotal = total
                musicServerOffset += page.size
                musicTracks = musicTracks + page.filter { it.id > 0L && !it.url.isNullOrBlank() }
                // #AUDIO-PAGING-ALL: full-page-паттерн вместо стопа по заниженному
                // VK total (см. комментарий в инициализации вкладки выше).
                musicHasMore = page.size >= PROFILE_MUSIC_PAGE
                AppLog.i("ProfileScreen",
                    "Music loadMore: +${page.size} (offset=$musicServerOffset total=$total)")
            } catch (e: Exception) {
                AppLog.w("ProfileScreen", "Music loadMore failed: ${e.message}")
            } finally {
                musicLoadingMore = false
            }
        }
    }

    fun loadMoreVideoPage() {
        if (videoLoadingMore || videoLoading || !videoHasMore) return
        // NULL-ЯВНО: явная проверка вместо элвиса.
        val prof = profile
        if (prof == null) return
        scope.launch {
            videoLoadingMore = true
            try {
                val page = app.apiClient.videoGet(
                    ownerId = prof.id, count = PROFILE_VIDEO_PAGE, offset = videoServerOffset)
                videoServerOffset += page.size
                videos = videos + page.filter { it.id > 0L }
                videoHasMore = page.size >= PROFILE_VIDEO_PAGE
            } catch (e: Exception) {
                AppLog.w("ProfileScreen", "Video loadMore failed: ${e.message}")
            } finally {
                videoLoadingMore = false
            }
        }
    }

    fun loadMorePhotoPage() {
        if (photoLoadingMore || photoLoading || !photoHasMore) return
        // NULL-ЯВНО: явная проверка вместо элвиса.
        val prof = profile
        if (prof == null) return
        scope.launch {
            photoLoadingMore = true
            try {
                val page = app.apiClient.photosGetAll(
                    ownerId = prof.id, count = PROFILE_PHOTO_PAGE, offset = photoServerOffset)
                photoServerOffset += page.size
                photos = photos + page
                photoHasMore = page.size >= PROFILE_PHOTO_PAGE
            } catch (e: Exception) {
                AppLog.w("ProfileScreen", "Photo loadMore failed: ${e.message}")
            } finally {
                photoLoadingMore = false
            }
        }
    }

    fun loadMoreGiftsPage() {
        if (giftsLoadingMore || !giftsHasMore) return
        // NULL-ЯВНО: явные проверки вместо элвисов.
        val prof = profile
        if (prof == null) return
        scope.launch {
            giftsLoadingMore = true
            try {
                val page = app.apiClient.giftsGet(
                    prof.id, count = PROFILE_GIFTS_PAGE, offset = giftsServerOffset)
                giftsServerOffset += page.size
                val existingGifts = gifts
                gifts = if (existingGifts != null) existingGifts + page else page
                giftsHasMore = page.size >= PROFILE_GIFTS_PAGE
            } catch (e: Exception) {
                AppLog.w("ProfileScreen", "Gifts loadMore failed: ${e.message}")
            } finally {
                giftsLoadingMore = false
            }
        }
    }

    // #PROFILE-WALL-PAGING (волна 39): догрузка ленты профиля. Паттерн
    // loadMoreMusicPage: server-offset по RAW-странице, стоп по неполной
    // странице, дедуп на границе окон (закреплённый пост повторяется на
    // первой странице каждого окна — класс Fix #53).
    fun loadMoreWallPage() {
        if (wallLoadingMore || wallLoading || !wallHasMore) return
        // NULL-ЯВНО: явная проверка вместо элвиса.
        val prof = profile
        if (prof == null) return
        scope.launch {
            wallLoadingMore = true
            try {
                val page = app.apiClient.wallGetWithFilter(
                    ownerId = prof.id, filter = wallFilter, count = 20, offset = wallServerOffset)
                wallServerOffset += page.size
                wallHasMore = page.size >= 20
                val existingKeys = posts.map { "${it.ownerId}_${it.id}" }.toHashSet()
                val fresh = page
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                    .filter { "${it.ownerId}_${it.id}" !in existingKeys }
                posts = posts + fresh
                AppLog.i("ProfileScreen",
                    "Wall loadMore: +${fresh.size} (raw=${page.size}, offset=$wallServerOffset, hasMore=$wallHasMore)")
            } catch (e: Exception) {
                AppLog.w("ProfileScreen", "Wall loadMore failed: ${e.message}")
            } finally {
                wallLoadingMore = false
            }
        }
    }

    // Fix #389 #SCROLL-TOP-PARITY: состояние главного списка (стена/закладки/статьи)
    // — выведено наружу, тот же экземпляр используется FAB «наверх» ниже.
    // W34-FIX (2026-09-10): объявление перенесено ВЫШЕ LaunchedEffect'ов скролла
    // по счётчикам — Kotlin резолвит локальные val по порядку объявления, ссылки
    // mainListState в LaunchedEffect ниже падали с «Unresolved reference»
    // (×5: строки 1106/1107/1121/1122 лога сборки).
    val mainListState = rememberLazyListState()
    // W33-c: переход по счётчикам (Фото/Видео/Аудио/Подарки) — после смены
    // вкладки скроллим LazyColumn к началу её контента. Индекс 5 = первый
    // item контента (перед ним ProfileHeader / «Редактировать» / счётчики /
    // «Подписки» / ряд чипов-вкладок). Ждём, пока item'ы вкладки соберутся
    // в layout (первичная загрузка ленивая).
    var contentScrollTick by remember { mutableStateOf(0) }
    LaunchedEffect(selectedContentTab, contentScrollTick) {
        if (contentScrollTick == 0) return@LaunchedEffect
        while (mainListState.layoutInfo.totalItemsCount < 6) delay(50)
        mainListState.animateScrollToItem(5)
    }
    // Подарки живут на вкладке Стена ПЕРЕД лентой (#PROFILE-WALL-PAGING,
    // волна 39 — по жалобе «почему подарки снизу»). Скролл: сначала к item
    // после фиксированной шапки (создать пост/фильтр/подарки попадают во
    // viewport), затем точная подгонка по ключу "profile_gifts". Подарки
    // грузятся параллельным добором при открытии профиля — ждём их (таймаут
    // 5с от вечного цикла при сбое gifts.get; тогда просто скролл к ленте).
    var giftsScrollTick by remember { mutableStateOf(0) }
    LaunchedEffect(giftsScrollTick) {
        if (giftsScrollTick == 0) return@LaunchedEffect
        var waitedMs = 0
        while (gifts == null && waitedMs < 5000) {
            delay(100)
            waitedMs += 100
        }
        while (mainListState.layoutInfo.totalItemsCount < 6) delay(50)
        // 7 item'ов над подарками: хедер, «Редактировать», счётчики, «Подписки»,
        // чипы вкладок, «Создать пост», фильтры стены (без секции «Возможно,
        // вы знакомы» — она опциональна и по умолчанию выключена).
        // coerce — у коротких профилей item'ов может быть меньше 8.
        val headerTarget = 7.coerceAtMost(mainListState.layoutInfo.totalItemsCount - 1)
        mainListState.animateScrollToItem(headerTarget)
        val giftsIndex = mainListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "profile_gifts" }?.index
        // NULL-ЯВНО: явная проверка вместо элвиса (index может быть null —
        // item ещё не в layout при догрузке подарков; первичный скролл уже
        // привёл пользователя в зону подарков).
        if (giftsIndex != null) {
            mainListState.animateScrollToItem(giftsIndex)
        }
    }

    // #AUDIO-PAGING-ALL (волна 38): автодогрузка музыки профиля — доскроллили
    // до конца списка → грузим следующую страницу без нажатия «Показать ещё»
    // (кнопка остаётся как ручной fallback). Поток эмитит total при активном
    // триггере: после аппенда total растёт → условие перепроверяется.
    LaunchedEffect(mainListState, selectedContentTab) {
        if (selectedContentTab != PROFILE_TAB_MUSIC) return@LaunchedEffect
        snapshotFlow {
            val info = mainListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 4) total else -1
        }
            .distinctUntilChanged()
            .filter { it > 0 }
            .collect { loadMoreMusicPage() }
    }

    // #PROFILE-WALL-PAGING (волна 39): автодогрузка ленты профиля — доскроллили
    // до конца записей → следующая страница без нажатия «Показать ещё» (кнопка
    // остаётся fallback). Поток эмитит total при активном триггере: после
    // аппенда total растёт → условие перепроверяется само.
    LaunchedEffect(mainListState, selectedContentTab) {
        if (selectedContentTab != PROFILE_TAB_WALL) return@LaunchedEffect
        snapshotFlow {
            val info = mainListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 4) total else -1
        }
            .distinctUntilChanged()
            .filter { it > 0 }
            .collect { loadMoreWallPage() }
    }

    // Fix #43: statusBarsPadding — контент не уходит под системную панель.
    // ProfileScreen в hasOwnTopBar списке SovaNavHost, но своего Scaffold нет
    // (глобальный TopAppBar не рисуется) → insets применяем сами.
    // W31-b: обёртка Column→Box(weight(1f)) сохранена — она нужна оверлею FAB
    // (ScrollToTopFab живёт в BoxScope и align-ится к низу weight-зоны);
    // закреплённая строка «Выйти из аккаунта» (Fix #378) удалена — выход
    // из аккаунта теперь ТОЛЬКО в боковом drawer (Fix #369).
    // W34-FIX: mainListState объявлен выше — до LaunchedEffect'ов скролла.
    // W33-c: состояние плеера для списка «Музыка» и URL-сетка «Фото»
    // собираются на уровне тела экрана: внутри LazyListScope-веток
    // composable-вызовы (collectAsState/remember) запрещены.
    val musicPlayerState = PlayerConnection.playerState.collectAsState().value
    val photoGridUrls = remember(photos) { photos.mapNotNull { extractPhotoAllUrl(it) } }
    Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f)) {
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding(), state = mainListState) {
        // П-1: тап по статусу → диалог правки (status.set).
        item {
            ProfileHeader(
                profile = p,
                onStatusClick = {
                    statusError = null
                    showStatusDialog = true
                },
            )
        }
        // П-3 (#PROFILE-SNAP): вход в редактор профиля (EditProfileScreen —
        // account.saveProfileInfo + смена аватара/обложки). Паттерн кнопки-строки —
        // как соседняя «Создать пост» (Icons.Filled.Edit уже в файле).
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEditProfileClick() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Редактировать профиль",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            CountersRow(
                profile = p,
                // П-7-AB: чипы кликабельны — «Друзья» → глобальный Screen.Friends
                // (маршрут без userId; на своём профиле корректно), «Подписчики» →
                // список подписчиков p.id (FollowersSubscriptionsScreen).
                onFriendsClick = onFriendsClick,
                onFollowersClick = { onFollowersClick(p.id) },
                // W33-c: счётчики → свои разделы: смена контентной вкладки +
                // скролл к её началу (контент вкладки — item №5 общего списка).
                onPhotosClick = {
                    selectedContentTab = PROFILE_TAB_PHOTO
                    contentScrollTick++
                },
                onVideosClick = {
                    selectedContentTab = PROFILE_TAB_VIDEO
                    contentScrollTick++
                },
                onAudiosClick = {
                    selectedContentTab = PROFILE_TAB_MUSIC
                    contentScrollTick++
                },
                onGiftsClick = {
                    selectedContentTab = PROFILE_TAB_WALL
                    giftsScrollTick++
                },
            )
        }
        // П-7-AB: строка «Подписки» — доступ к экрану подписок БЕЗ чипа в
        // CountersRow (поля subscriptions в модели Counters нет, модель править
        // запрещено — см. KDoc SubscriptionsEntryRow).
        item {
            SubscriptionsEntryRow(
                count = subscriptionsCount,
                onClick = { onSubscriptionsClick(p.id) },
            )
        }
        // П-1 (#PROFILE-SNAP): вкладки контента «Стена / Музыка / Видео / Фото» —
        // шапка и счётчики сохраняются, меняется только контент ниже.
        item {
            ProfileChipsRow(
                options = PROFILE_CONTENT_TABS,
                selected = selectedContentTab,
                onSelect = { selectedContentTab = it },
            )
        }
        if (selectedContentTab == PROFILE_TAB_WALL) {
            // User request 2026-07-12: кнопка «Создать пост» в профиле
            // (перенесена из ленты). VK API: wall.post — owner_id = текущий пользователь.
            // См. VK_IMPORT_API.MD §1.1.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { creatingPost.value = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Создать пост",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            // П-1 (#PROFILE-SNAP): подвкладки стены «Все / Свои / Архив»
            // (wall.get filter=all|owner|archived, каноника users.getWallTabs).
            item {
                WallFilterRow(
                    selected = wallFilter,
                    showArchive = hasArchiveWallTab,
                    onSelect = { wallFilter = it },
                )
            }
            // П-6b (#PROFILE-GAP-6b): «Возможно, вы знакомы» (friends.getRecommendations)
            // — на вкладке Стена сверху, как в VK web. Пусто/ошибка — не рисуем.
            // Fix #360 #PROFILE-SUGGEST-TOGGLE: рендер только при включённой
            // настройке (Настройки → Приватность → «Профиль», default ВЫКЛ).
            val suggestions = if (showFriendSuggestions) friendSuggestions else null
            if (!suggestions.isNullOrEmpty()) {
                item {
                    FriendSuggestionsSection(
                        suggestions = suggestions,
                        sentStatuses = suggestionSent,
                        inFlight = suggestionInFlight,
                        onAdd = { addSuggestion(it) },
                        onOpen = onUserClick,
                    )
                }
            }
            // #PROFILE-WALL-PAGING (волна 39): подарки ПЕРЕД лентой (запрос
            // пользователя «почему подарки снизу» — раздел виден сразу, без
            // прокрутки всей стены, как в VK web). Ключ "profile_gifts" — для
            // точного скролла по счётчику «Подарки» (giftsScrollTick ниже).
            // П-1: подарки профиля (gifts.get) — ряд открыток.
            // NULL-ЯВНО: явная проверка вместо элвиса (строка перенесена W33-c).
            val pCounters = p.counters
            // W34-FIX: pCounters.gifts — сам по себе Int? (модель Counters),
            // а GiftsSection принимает Int → внутренний элвис, иначе
            // «Argument type mismatch: actual type is 'Int?'» (строка 1369 лога).
            val giftTotalCount: Int = if (pCounters != null) (pCounters.gifts ?: 0) else 0
            val giftsList = gifts
            if (!giftsList.isNullOrEmpty()) {
                item(key = "profile_gifts") {
                    GiftsSection(
                        gifts = giftsList,
                        totalCount = giftTotalCount,
                        hasMore = giftsHasMore,
                        loadingMore = giftsLoadingMore,
                        onShowMore = { loadMoreGiftsPage() },
                    )
                }
            }
            if (wallLoading) {
                item { TabProgressRow() }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Записи (${posts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            items(posts, key = { "${it.ownerId}_${it.id}" }) { post ->
                // П-6a: ключ поста для optimistic-карт лайка/действий.
                val postKey = "${post.ownerId}_${post.id}"
                WallPostCard(
                    post = post,
                    authorName = p.fullName,
                    authorPhoto = p.photo200 ?: p.photo100,
                    onVideoClick = onVideoClick,
                    // Fix #374: тап по посту/репосту → PostDetailScreen (экран-параметр
                    // onPostClick :172; обёртка SovaNavHost: PostHolder.last + buildRoute
                    // уже работала для пост-закладок — теперь и для карточек стены,
                    // паритет с UserProfileScreen:770 и лентой).
                    onPostClick = onPostClick,
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    // 22-B: карусель фото (общий SovaPrefs-флаг, см. выше).
                    carouselEnabled = carouselEnabled,
                    onRepostClick = { repostPost.value = it },
                    // Шаг 5 (#32e): тап по комментарию → onCommentClick → PostDetailScreen.
                    onCommentClick = onCommentClick,
                    // П-6a (#PROFILE-GAP-6a): лайк записи + «Действия» поста стены
                    // (pin/unpin/delete/edit). Свой профиль → владелец стены = p.id.
                    likesState = likeStates,
                    // П-8-REACT: реакции long-press'ом (пикер + optimistic-карта).
                    reactionsState = reactionStates,
                    onReaction = { clicked, reaction -> applyWallPostReaction(clicked, reaction) },
                    likePending = likeInFlight.containsKey(postKey),
                    onLikeToggle = { toggleWallPostLike(it) },
                    showActions = true,
                    wallOwnerId = p.id,
                    actionPending = postActionInFlight.containsKey(postKey),
                    onPinToggle = { toggleWallPostPin(it) },
                    onDeleteRequest = { deletingPost.value = it },
                    onEditRequest = {
                        editError = null
                        editingPost.value = it
                    },
                    // П-7-AB: «Пожаловаться» — только на ЧУЖИХ записях (ownerId !=
                    // мой id; на своей стене owner_id всегда p.id → пункт не
                    // показывается, как в VK). Управление — через showReport.
                    showReport = post.ownerId != p.id,
                    onReportSpam = { reportingPost.value = it },
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            if (posts.isEmpty() && !wallLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (wallFilter) {
                                WALL_FILTER_OWNER -> "Своих записей на стене нет"
                                WALL_FILTER_ARCHIVED -> "В архиве нет записей"
                                else -> "На стене пока нет записей"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // #PROFILE-WALL-PAGING (волна 39): футер догрузки ленты — кнопка
            // остаётся ручным fallback автодогрузки по скроллу (паттерн вкладки
            // «Музыка», W33-c).
            if (wallHasMore) {
                item(key = "wall_more") {
                    TabShowMoreRow(loading = wallLoadingMore, onClick = { loadMoreWallPage() })
                }
            }
        } else if (selectedContentTab == PROFILE_TAB_MUSIC) {
            // W33-c: полный раздел «Музыка» — вертикальный список ВСЕХ треков
            // с пагинацией «Показать ещё» (раньше — горизонтальная полоса
            // из 10 карточек). Строки = отдельные item'ы LazyColumn —
            // виртуализация длинного списка (сотни/тысячи треков).
            val musicErr = musicError
            if (musicLoading) {
                item(key = "music_progress") { TabProgressRow() }
            } else if (musicErr != null) {
                item(key = "music_error") {
                    TabErrorRow(message = musicErr, onRetry = { contentRetryTick++ })
                }
            } else if (musicTracks.isEmpty()) {
                item(key = "music_empty") {
                    TabEmptyRow("В вашей музыке пока нет треков")
                }
            } else {
                itemsIndexed(
                    musicTracks,
                    // Ключ с индексом — crash-proof к дублям (прецедент Fix #281).
                    key = { idx, track -> "music_${idx}_${track.ownerId}_${track.id}" },
                ) { idx, track ->
                    val current = musicPlayerState.currentTrack
                    val isCurrent = current != null &&
                        track.id == current.id &&
                        track.ownerId == current.ownerId
                    ProfileTrackRow(
                        track = track,
                        isPlaying = isCurrent && musicPlayerState.isPlaying,
                        onClick = {
                            if (isCurrent) {
                                PlayerConnection.togglePlayPause()
                            } else {
                                PlayerConnection.playTrackList(musicTracks, idx)
                            }
                        },
                    )
                }
                if (musicHasMore) {
                    item(key = "music_more") {
                        TabShowMoreRow(loading = musicLoadingMore, onClick = { loadMoreMusicPage() })
                    }
                }
            }
        } else if (selectedContentTab == PROFILE_TAB_VIDEO) {
            // W33-c: полный раздел «Видео» — вертикальная сетка 2 колонки
            // с пагинацией (раньше — полоса из 9 карточек).
            val videoErr = videoError
            if (videoLoading) {
                item(key = "video_progress") { TabProgressRow() }
            } else if (videoErr != null) {
                item(key = "video_error") {
                    TabErrorRow(message = videoErr, onRetry = { contentRetryTick++ })
                }
            } else if (videos.isEmpty()) {
                item(key = "video_empty") {
                    TabEmptyRow("У вас пока нет видео")
                }
            } else {
                val videoRows = videos.chunked(2)
                itemsIndexed(
                    videoRows,
                    key = { rowIdx, _ -> "video_row_$rowIdx" },
                ) { _, rowVideos ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowVideos.forEach { video ->
                            ProfileVideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Добиваем последнюю строку пустой ячейкой до 2 колонок.
                        repeat(2 - rowVideos.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                if (videoHasMore) {
                    item(key = "video_more") {
                        TabShowMoreRow(loading = videoLoadingMore, onClick = { loadMoreVideoPage() })
                    }
                }
            }
        } else if (selectedContentTab == PROFILE_TAB_PHOTO) {
            // W33-c: полный раздел «Фото» — сетка 3 колонки с пагинацией
            // (раньше жёсткий кап 12 фото). Строки сетки = item'ы LazyColumn.
            val photoErr = photoError
            if (photoLoading) {
                item(key = "photo_progress") { TabProgressRow() }
            } else if (photoErr != null) {
                item(key = "photo_error") {
                    TabErrorRow(message = photoErr, onRetry = { contentRetryTick++ })
                }
            } else if (photoGridUrls.isEmpty()) {
                item(key = "photo_empty") {
                    TabEmptyRow("У вас пока нет фотографий")
                }
            } else {
                val photoRows = photoGridUrls.chunked(3)
                itemsIndexed(
                    photoRows,
                    key = { rowIdx, _ -> "photo_row_$rowIdx" },
                ) { rowIdx, rowUrls ->
                    PhotoGridRow(
                        urls = rowUrls,
                        startIndex = rowIdx * 3,
                        fullList = photoGridUrls,
                        onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    )
                }
                if (photoHasMore) {
                    item(key = "photo_more") {
                        TabShowMoreRow(loading = photoLoadingMore, onClick = { loadMorePhotoPage() })
                    }
                }
            }
        } else if (selectedContentTab == PROFILE_TAB_CLIPS) {
            // П-6b (#PROFILE-GAP-6b): вкладка «Клипы».
            item {
                ClipsTabSection(
                    clips = clips,
                    loading = clipsLoading,
                    error = clipsError,
                    onRetry = { contentRetryTick++ },
                    onClipClick = onVideoClick,
                )
            }
        } else if (selectedContentTab == PROFILE_TAB_ARTICLES) {
            // П-6b: вкладка «Статьи» — тап открывает статью в системном браузере.
            item {
                ArticlesTabSection(
                    articles = articles,
                    loading = articlesLoading,
                    error = articlesError,
                    onRetry = { contentRetryTick++ },
                    onOpenArticle = { url -> openUrlExternal(context, url) },
                )
            }
        } else if (selectedContentTab == PROFILE_TAB_BOOKMARKS) {
            // П-6b: вкладка «Закладки» (fave.get) — тап по доступным типам.
            item {
                BookmarksTabSection(
                    bookmarks = bookmarks,
                    loading = bookmarksLoading,
                    error = bookmarksError,
                    onRetry = { contentRetryTick++ },
                    hasMore = bookmarksHasMore,
                    loadingMore = bookmarksLoadingMore,
                    onLoadMore = { loadMoreBookmarksPage() },
                    onOpen = { openBookmark(it) },
                    // Волна 40 #BOOKMARKS-REMOVE-ALL: удаление через bookmarkRemove
                    // (жалоба тестера: «Нет возможности удалить из закладок»).
                    onRemove = { bm ->
                        scope.launch {
                            val (ok, err) = app.apiClient.bookmarkRemove(bm)
                            if (ok) {
                                bookmarks = bookmarks.filterNot { it == bm }
                                Toast.makeText(context, "Удалено из закладок", Toast.LENGTH_SHORT).show()
                            } else {
                                // NULL-ЯВНО: err nullable — явный if вместо ?:.
                                val shown = if (err != null) ": $err" else ""
                                Toast.makeText(context, "Не удалось удалить из закладок$shown", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        }
        // W31-b: бывший комментарий Fix #378 о переносе кнопки выхода в
        // закреплённую строку удалён вместе с самой строкой — выход из
        // аккаунта ТОЛЬКО в drawer (Fix #369).
    }

    // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх» — оверлей над
    // списком (Box-обёртка выше).
    ScrollToTopFab(
        listState = mainListState,
        modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp),
    )
    }

    // W31-b: закреплённая строка «Выйти из аккаунта» (Fix #378) и её
    // диалог-подтверждение удалены ЦЕЛИКОМ — logout остался только в
    // боковом drawer (SovaNavHost, Fix #369 + обёртка Fix #370).
    // Вес list-зоны не изменился: Box(weight(1f)) тянется на весь Column,
    // дыры вёрстки нет.
    // W33-a FIX (root-cause «~80 Unresolved reference»): в W31-b вместе с
    // logout-блоком удалилась ЗАКРЫВАЮЩАЯ СКОБКА Column (в диффе ушли
    // «} }» — if(showLogoutConfirm) И Column). Диалоги ниже провалились
    // внутрь Column, скобка в конце файла стала закрывать Column, а сам
    // ProfileScreen остался незакрытым до EOF → все хелперы ниже
    // (ProfileHeader/CountersRow/WallPostCard/ActionIcon/…) стали
    // локальными функциями и пропали из UserProfileScreen/ChatDetailScreen
    // (Syntax error EOF + Unresolved reference WallPostCard и т.д.).
    // Скобка Column восстановлена.
    }

    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val viewer = photoViewerState.value
    if (viewer != null) {
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
        )
    }

    // Sprint 2, P1-3 (#90): диалог репоста.
    val reposting = repostPost.value
    if (reposting != null) {
        RepostDialog(
            post = reposting,
            onDismiss = { repostPost.value = null },
        )
    }

    // User request 2026-07-12: диалог создания нового поста (перенесён из ленты).
    // VK API: wall.post(message, owner_id, friends_only) или
    // uploadPhotoAndPost для поста с фото. После успеха — перезагружаем стену.
    if (creatingPost.value) {
        CreatePostDialog(
            selectedPhotoUri = selectedPhotoUri,
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemovePhoto = { selectedPhotoUri = null },
            onDismiss = {
                creatingPost.value = false
                selectedPhotoUri = null
            },
            onSubmit = { message, friendsOnly ->
                val photoUri = selectedPhotoUri
                val ownerId = p.id
                scope.launch {
                    val id = if (photoUri != null) {
                        // Пост с фото — upload flow на стену текущего пользователя.
                        AppLog.i("ProfileScreen", "Creating post with photo on wall $ownerId")
                        app.apiClient.uploadPhotoAndPost(message, photoUri, friendsOnly = friendsOnly)
                    } else {
                        // Текстовый пост на свою стену.
                        app.apiClient.wallPost(message, ownerId = ownerId, friendsOnly = friendsOnly)
                    }
                    if (id > 0) {
                        AppLog.i("ProfileScreen", "Post created: id=$id")
                        // Перезагружаем стену, чтобы новый пост появился сверху.
                        reloadWallTrigger++
                    } else {
                        AppLog.w("ProfileScreen", "wallPost failed")
                    }
                }
                creatingPost.value = false
                selectedPhotoUri = null
            },
        )
    }

    // П-1 (#PROFILE-SNAP): диалог правки статуса (status.set). Пустой текст
    // снимает статус (VK: пустой text удаляет статус).
    if (showStatusDialog) {
        var statusDraft by remember { mutableStateOf(p.status ?: "") }
        AlertDialog(
            onDismissRequest = { if (!statusSaving) showStatusDialog = false },
            title = { Text("Изменить статус") },
            text = {
                Column {
                    OutlinedTextField(
                        value = statusDraft,
                        onValueChange = { statusDraft = it.take(140) },
                        placeholder = { Text("Что у вас нового?") },
                        maxLines = 3,
                        enabled = !statusSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val sErr = statusError
                    if (sErr != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = sErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            statusSaving = true
                            statusError = null
                            val newText = statusDraft.trim()
                            val ok = try {
                                app.apiClient.statusSet(newText)
                            } catch (e: Exception) {
                                AppLog.e("ProfileScreen", "statusSet failed", e)
                                false
                            }
                            statusSaving = false
                            if (ok) {
                                // Обновляем состояние экрана без перезагрузки профиля.
                                profile = p.copy(status = newText.takeIf { it.isNotEmpty() })
                                showStatusDialog = false
                                AppLog.i("ProfileScreen", "Status updated")
                            } else {
                                statusError = app.apiClient.lastApiError ?: "Не удалось сохранить статус"
                            }
                        }
                    },
                    enabled = !statusSaving,
                ) {
                    Text(if (statusSaving) "Сохранение…" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }, enabled = !statusSaving) {
                    Text("Отмена")
                }
            },
        )
    }

    // ══ П-6a (#PROFILE-GAP-6a): подтверждение удаления записи (wall.delete) ══
    val deleteTarget = deletingPost.value
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deletingPost.value = null },
            title = { Text("Удаление записи") },
            text = { Text("Запись будет удалена со стены. Действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { deleteWallPostConfirmed(deleteTarget) }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPost.value = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    // ══ П-7-AB: подтверждение «Пожаловаться» (wall.markAsSpam) ══
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

    // ══ П-6a (#PROFILE-GAP-6a): правка текста записи (wall.edit) ══
    val editTarget = editingPost.value
    if (editTarget != null) {
        var editDraft by remember(editTarget) { mutableStateOf(editTarget.text) }
        AlertDialog(
            onDismissRequest = { if (!editSaving) editingPost.value = null },
            title = { Text("Редактирование записи") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        placeholder = { Text("Текст записи") },
                        maxLines = 10,
                        enabled = !editSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val eErr = editError
                    if (eErr != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = eErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { editWallPostConfirmed(editTarget, editDraft.trim()) },
                    // wall.edit с пустым message у текстовой записи — серверная
                    // ошибка; пустой текст не отправляем (как в вебе VK).
                    enabled = !editSaving && editDraft.trim().isNotEmpty(),
                ) {
                    Text(if (editSaving) "Сохранение…" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPost.value = null }, enabled = !editSaving) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
fun ProfileHeader(
    profile: UserProfile,
    // П-1 (#PROFILE-SNAP): тап по статусу → диалог правки (status.set).
    // Nullable с дефолтом null: UserProfileScreen (чужой профиль) зовёт без
    // параметра — там правка статуса не показывается.
    onStatusClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // П-1: обложка профиля (users.get field=cover → usersGetFullExtended
        // парсит → UserProfile.cover.images; enabled=1 без images — не рисуем).
        val coverUrl = profile.cover?.takeIf { it.enabled }?.images?.lastOrNull()
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка профиля",
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
        }
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
            if (profile.photo200 != null) {
                AsyncImage(
                    model = profile.photo200,
                    contentDescription = profile.fullName,
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (profile.isOnline) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(3.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.fullName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (profile.verified == 1) {
                Spacer(Modifier.width(6.dp))
                Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            }
        }
        // П-1: статус кликабелен на своём профиле (правка через status.set);
        // пустой статус → подсказка «Установить статус» (как в вебе VK).
        val statusText = profile.status
        val statusClick = onStatusClick
        if (statusText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (statusClick != null) {
                    Modifier.clickable { statusClick() }
                } else {
                    Modifier
                },
            )
        } else if (statusClick != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Установить статус",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { statusClick() },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ID: ${profile.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        profile.bdate?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "День рождения: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profile.city?.title?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Город: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountersRow(
    profile: UserProfile,
    // П-7-AB: тапы по чипам. Nullable с дефолтом null — существующие вызовы
    // совместимы (UserProfileScreen зовёт без параметров); чип кликабелен
    // ТОЛЬКО при переданном колбэке (нет мёртвой нажимаемости). На чужом
    // профиле «Друзья» не передаётся: глобальный Screen.Friends показывает
    // СВОИХ друзей — честное отклонение (см. KDoc UserProfileScreen).
    onFriendsClick: (() -> Unit)? = null,
    onFollowersClick: (() -> Unit)? = null,
    // W33-c: счётчики-переходы в свои разделы (запрос юзера волны 33 —
    // «зелёные» чипы должны открывать разделы). Фото/Видео/Аудио —
    // переключение контентной вкладки профиля; Подарки — вкладка Стена
    // (ряд подарков под лентой) + скролл к ним.
    onPhotosClick: (() -> Unit)? = null,
    onVideosClick: (() -> Unit)? = null,
    onAudiosClick: (() -> Unit)? = null,
    onGiftsClick: (() -> Unit)? = null,
) {
    val counters = profile.counters
    val items = mutableListOf<Pair<String, Int>>()
    // Основные счётчики — из counters или верхнеуровневых полей.
    counters?.friends?.let { items.add("Друзья" to it) }
    profile.followersCount.takeIf { it > 0 }?.let { items.add("Подписчики" to it) }
        ?: counters?.followers?.let { items.add("Подписчики" to it) }
    counters?.photos?.let { items.add("Фото" to it) }
    counters?.videos?.let { items.add("Видео" to it) }
    counters?.audios?.let { items.add("Аудио" to it) }
    counters?.gifts?.takeIf { it > 0 }?.let { items.add("Подарки" to it) }
    if (items.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        items.forEach { (label, count) ->
            // П-7-AB: кликабельность чипа решается переданным колбэком. Чип
            // «Подписки» в ряд НЕ добавляется — поля subscriptions в модели
            // UserProfile.Counters нет (правки core-моделей запрещены); доступ
            // к экрану подписок даёт отдельная строка (SubscriptionsEntryRow).
            val chipClick: (() -> Unit)? = when (label) {
                "Друзья" -> onFriendsClick
                "Подписчики" -> onFollowersClick
                // W33-c: счётчики → свои разделы.
                "Фото" -> onPhotosClick
                "Видео" -> onVideosClick
                "Аудио" -> onAudiosClick
                "Подарки" -> onGiftsClick
                else -> null
            }
            Card(
                modifier = Modifier.weight(1f).padding(2.dp)
                    .clickable(enabled = chipClick != null) { chipClick?.invoke() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = count.toCountString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Карточка записи на стене профиля.
 *
 * П-6a (#PROFILE-GAP-6a, остаток скоупа П-1 — инвентарь §5 п.2):
 *  - ЛАЙК: ряд лайка кликабелен — optimistic (isLiked + счётчик ±1) →
 *    likes.add / likes.delete; optimistic-состояние живёт в вызывающем экране
 *    ([likesState], паттерн FeedScreen.likesState), откат и Toast lastApiError —
 *    тоже там. Стартовое состояние — user_likes из wall.get (поле есть в модели
 *    Post, парсится в parsePostMini).
 *  - «ДЕЙСТВИЯ» ([showActions]): «⋯»-меню с Закрепить/Открепить (wall.pin /
 *    wall.unpin, label по post.is_pinned), Редактировать (wall.edit — только
 *    для ТЕКСТОВЫХ записей, см. editCovered ниже) и Удалить (wall.delete —
 *    с AlertDialog-подтверждением на уровне экрана). Меню показывается для
 *    постов владельца стены ([wallOwnerId]) ИЛИ постов с серверными
 *    can_edit/can_delete (поля есть в модели Post) — правило §5 п.2; решение
 *    «показывать ли меню вовсе» — за вызывающим экраном (ProfileScreen — свой
 *    профиль; UserProfileScreen не передаёт showActions → меню скрыто).
 *  ЧЕСТНОЕ ОГРАНИЧЕНИЕ РЕДАКТИРОВАНИЯ (editCovered): wall.edit сбрасывает
 *  непереданные поля, а сигнатура VKApiClient.wallEdit принимает только
 *  message/attachments/friends_only. Вложения link/poll/audio_playlist/
 *  sticker и copy_history из модели Post невосстановимы в attachments-строку
 *  → для постов с ними пункт «Редактировать» НЕ показывается (иначе молчаливая
 *  потеря данных). Для текстовых записей покрытие достаточное: message +
 *  friends_only, остальное у такого поста отсутствует.
 *
 *  П-7-AB: «ПОЖАЛОВАТЬСЯ» ([showReport]/[onReportSpam]) — wall.markAsSpam,
 *  VK-семантика: доступен на ЧУЖИХ записях (ownerId != id текущего
 *  пользователя). Решение о показе — за вызывающим экраном: «свой id» карточка
 *  сама не вычисляет (WallPostCard переиспользуется на своём и чужом профиле) —
 *  ProfileScreen передаёт showReport = post.ownerId != p.id, UserProfileScreen —
 *  showReport = !isSelf (isSelf по exchangeAuthRepository.userId()). Если
 *  manage-пунктов нет (showActions=false, чужая стена), но showReport=true —
 *  меню откроется с ОДНИМ пунктом «Пожаловаться» (manage-пункты гейтерятся
 *  showActions — поведение П-6а на своём профиле не изменилось).
 *  Подтверждение (AlertDialog) и сам вызов — на уровне экрана.
 *
 *  П-8-REACT: РЕАКЦИИ ([reactionsState]/[onReaction]) — long-press по лайку →
 *  ReactionPicker (переиспользован из FeedScreen; + аддитивный параметр
 *  selectedReactionId с дефолтом — вызов FeedScreen.kt:2201 не задет).
 *  Пикер показывается ТОЛЬКО при переданном [onReaction] — UserProfileScreen
 *  лайки не прокидывает (П-6а), dead-пикер там недопустим. Клик — прежний
 *  простой лайк ([onLikeToggle]). Семантика выбора (добавить / снять «ту же» /
 *  сменить «другую» через likes.delete + likes.add, откат цепочки) — см. KDoc
 *  applyWallPostReaction в ProfileScreen. «Моя реакция» — [reactionsState]
 *  поверх серверной post.reactions.user_reacted (модель Post.Reactions, поле
 *  может отсутствовать → пикер без предвыделения и смена реакции идёт по
 *  цепочке delete+add — честное ограничение, модель core/data не правим).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallPostCard(
    post: Post,
    authorName: String,
    authorPhoto: String?,
    onVideoClick: (Video) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    // Sprint 2, P1-1 (#88): тап по фото → полноэкранный просмотр.
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // Sprint 2, P1-3 (#90): тап по репосту → диалог.
    onRepostClick: (Post) -> Unit = {},
    // Шаг 5 (#32e): тап по иконке комментария → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
    // П-6a (#PROFILE-GAP-6a): optimistic-состояния лайков экрана
    // (key "ownerId_id" → (isLiked, count)); пустая карта — серверные post.likes.
    likesState: Map<String, Pair<Boolean, Int>> = emptyMap(),
    // П-8-REACT: «моя реакция» на запись (key "ownerId_id" → reaction_id,
    // 0 = нет/неизвестна; фоллбэк внутри карточки — post.reactions.user_reacted).
    reactionsState: Map<String, Int> = emptyMap(),
    // П-8-REACT: long-press по лайку → пикер реакций. Nullable с дефолтом null:
    // прежние вызовы без параметра (UserProfileScreen:731, лайки не прокинуты)
    // long-press НЕ получают — dead-пикер недопустим.
    onReaction: ((Post, ReactionEntry) -> Unit)? = null,
    likePending: Boolean = false,
    onLikeToggle: (Post) -> Unit = {},
    // П-6a: «Действия» поста (pin/unpin/delete/edit) — см. KDoc выше.
    showActions: Boolean = false,
    wallOwnerId: Long = 0L,
    actionPending: Boolean = false,
    onPinToggle: (Post) -> Unit = {},
    onDeleteRequest: (Post) -> Unit = {},
    onEditRequest: (Post) -> Unit = {},
    // П-7-AB: «Пожаловаться» (wall.markAsSpam) — показ решает вызывающий экран
    // (VK-семантика: ЧУЖАЯ запись; см. KDoc выше). С дефолтом false: прежние
    // вызовы (без параметра) меню не расширяют.
    showReport: Boolean = false,
    onReportSpam: (Post) -> Unit = {},
    // 22-B: карусель фото (общий SovaPrefs.feedCarouselEnabled, читается
    // на уровне экрана ProfileScreen). Дефолт true — легаси-вызовы совместимы
    // (паттерн 19-A). ЧЕСТНОЕ ОГРАНИЧЕНИЕ: второй вызов WallPostCard —
    // UserProfileScreen:757 — ВНЕ зоны 22-B, параметр там не прокинут;
    // карусель на чужом профиле всегда включена (дефолт), независимо от
    // настройки. Проброс prefs-флага в UserProfileScreen — follow-up оркестратору.
    carouselEnabled: Boolean = true,
) {
    val photoAttachments = post.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
    // Fix #70: ранее video-вложения вообще не отображались на стене профиля.
    val videoAttachments = post.attachments?.filter { it.type == "video" && it.video != null }.orEmpty()
    // #30 (audio attachments): рендер audio-вложений на стене профиля.
    val audioAttachments = post.attachments?.filter { it.type == "audio" && it.audio != null }.orEmpty()
    val timeStr = post.date.toRelativeTime()
    // П-6a: optimistic-состояние лайка поверх серверных post.likes
    // (переопределение появляется после первого клика; до того — user_likes из wall.get).
    val likeOverride = likesState["${post.ownerId}_${post.id}"]
    val isLiked = likeOverride?.first ?: (post.likes?.userLikes == 1)
    val likeCount = likeOverride?.second ?: (post.likes?.count ?: 0)
    // П-8-REACT: «моя реакция» — optimistic-переопределение экрана поверх
    // серверной post.reactions.user_reacted (reaction_id или 0/absent; поле
    // отсутствует у старых API/стен без reactions → 0 = «неизвестно», пикер
    // без предвыделения — честное ограничение, модель core/data не правим).
    // Предвыделение в пикере осмысленно только при активном лайке.
    val myReactionId = reactionsState["${post.ownerId}_${post.id}"]
        ?: post.reactions?.userReacted?.takeIf { it > 0 }
        ?: 0
    val pickerSelection = if (isLiked) myReactionId else 0
    val commentCount = post.comments?.count ?: 0
    val repostCount = post.reposts?.count ?: 0
    val viewCount = post.views?.count ?: 0
    // П-6a: «⋯»-меню «Действий» — правило §5 п.2: посты владельца стены ИЛИ
    // серверные can_edit/can_delete (поля есть в модели Post/parsePostMini;
    // на своём профиле wallOwnerId == id текущего пользователя — покрывает и
    // фоллбэк «owner == userId текущего пользователя»).
    // П-7-AB: меню открывается и при showReport (чужая запись без manage-прав
    // — пункты manage гейтерятся showActions ниже, останется один пункт).
    val canManage = post.ownerId == wallOwnerId || post.canEditBool || post.canDeleteBool
    val menuVisible = (showActions && canManage) || showReport
    // П-6a: «Редактировать» — только текстовые записи (честное ограничение
    // wall.edit, см. KDoc выше): вложения/репост/копирайт/подпись — не редактируем.
    val editCovered = post.attachments.isNullOrEmpty() &&
        post.copyHistory == null &&
        post.copyright == null &&
        post.signerId == null &&
        (post.postType == null || post.postType == "post")
    // П-6a: состояние «⋯»-меню (паттерн FeedScreen.PostCard).
    var showMenu by remember { mutableStateOf(false) }
    // Fix #371/#376: контекст для linkify-кликов и открытия link/doc-вложений
    // (openUrlExternal / startActivity) — один захват на карточку.
    val ctx = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (authorPhoto != null) {
                    AsyncImage(
                        model = authorPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = authorName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                // П-6a (#PROFILE-GAP-6a): «⋯»-меню «Действий» поста (как в VK web).
                if (menuVisible) {
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                contentDescription = "Действия",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            // Закрепить/Открепить (wall.pin / wall.unpin) — label по post.is_pinned.
                            // П-7-AB: гейт showActions — manage-пункт не показывается в
                            // report-only меню (чужая стена, showActions=false).
                            if (showActions && (post.canPinBool || post.ownerId == wallOwnerId)) {
                                DropdownMenuItem(
                                    text = { Text(if (post.isPinnedBool) "Открепить" else "Закрепить") },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = { showMenu = false; onPinToggle(post) },
                                    enabled = !actionPending,
                                )
                            }
                            // Редактировать (wall.edit) — только текстовые записи (editCovered).
                            // П-7-AB: гейт showActions — как у «Закрепить» (report-only меню).
                            if (showActions && editCovered && (post.canEditBool || post.ownerId == wallOwnerId)) {
                                DropdownMenuItem(
                                    text = { Text("Редактировать") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { showMenu = false; onEditRequest(post) },
                                    enabled = !actionPending,
                                )
                            }
                            // Удалить (wall.delete) — подтверждение на уровне экрана.
                            // П-7-AB: гейт showActions && canManage — ранее Delete был
                            // безусловно виден внутри меню, но само меню открывалось только
                            // при showActions && canManage; теперь меню может открыться и
                            // по showReport — управляемые пункты прячем (поведение П-6а
                            // при showActions=true не меняется).
                            if (showActions && canManage) {
                                DropdownMenuItem(
                                    text = { Text("Удалить", color = Color(0xFFE53935)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                        )
                                    },
                                    onClick = { showMenu = false; onDeleteRequest(post) },
                                    enabled = !actionPending,
                                )
                            }
                            // П-7-AB: «Пожаловаться» (wall.markAsSpam) — ЧУЖАЯ запись;
                            // решение о показе — за вызывающим (showReport). Диалог
                            // подтверждения и вызов — на уровне экрана (onReportSpam).
                            if (showReport) {
                                DropdownMenuItem(
                                    text = { Text("Пожаловаться") },
                                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                                    onClick = { showMenu = false; onReportSpam(post) },
                                    enabled = !actionPending,
                                )
                            }
                        }
                    }
                }
            }
            // Fix #375 (#PROFILE-PINNED-LABEL): индикатор закреплённой записи —
            // паритет с лентой (FeedScreen: «Закреплённый пост» под шапкой).
            if (post.isPinnedBool) {
                Text(
                    text = "Закреплённый пост",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                )
            }
            // Fix #375 (паритет с лентой): индикатор копирайта-источника.
            // NULL-ЯВНО: модель в :core:data — захват ДО проверки (без ?.-операторов).
            val postCopyright = post.copyright
            if (postCopyright != null) {
                val copyrightName = postCopyright.name
                if (copyrightName != null) {
                    Text(
                        text = copyrightName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (post.text.isNotBlank()) {
                // Fix #371 (#PROFILE-POST-TEXT): паритет с лентой — паттерн Fix #345
                // (CommunityScreen) + linkifyVkText (FeedScreen:2280): длинный текст
                // разворачивается на месте, «Показать ещё» — только при реальном
                // переполнении (hasVisualOverflow); #alias/URL внутри текста кликабельны
                // (link-аннотации перехватывают клик раньше текста — потомок приоритетнее).
                // Тап по свёрнутому тексту = развернуть, по развёрнутому/короткому =
                // открыть пост (onPostClick → PostDetail). ctx — общий val карточки выше.
                var textExpanded by remember { mutableStateOf(false) }
                var textOverflowed by remember { mutableStateOf(false) }
                Text(
                    text = linkifyVkText(
                        text = post.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onUrlClick = { url -> openUrlExternal(ctx, url) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable {
                            if (textOverflowed && !textExpanded) {
                                textExpanded = true
                            } else {
                                onPostClick(post)
                            }
                        },
                    onTextLayout = { result ->
                        if (!textExpanded && result.hasVisualOverflow) textOverflowed = true
                    },
                    maxLines = if (textExpanded) Int.MAX_VALUE else 10,
                    overflow = TextOverflow.Ellipsis,
                )
                if (textOverflowed && !textExpanded) {
                    Text(
                        text = "Показать ещё",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clickable { textExpanded = true },
                    )
                }
            }
            if (photoAttachments.isNotEmpty()) {
                // 22-B: общий PostPhotoGrid — карусель при включённой настройке
                // и фото > 1, иначе прежний вид (1-2 пейджер, 3+ сетка).
                PostPhotoGrid(
                    photos = photoAttachments.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                    carouselEnabled = carouselEnabled,
                )
            }
            // Fix #372 (#PROFILE-VIDEO-CAROUSEL): >1 видео и включена карусель —
            // общий PostVideoCarousel (тот же флаг, что у PostPhotoGrid выше);
            // иначе прежний вертикальный стопк VideoThumbnail (одно видео).
            if (carouselEnabled && videoAttachments.size > 1) {
                PostVideoCarousel(
                    videos = videoAttachments.mapNotNull { it.video },
                    carouselEnabled = carouselEnabled,
                    onVideoClick = onVideoClick,
                )
            } else {
                videoAttachments.forEach { attach ->
                    val v = attach.video
                    if (v != null) {
                        VideoThumbnail(video = v, onClick = onVideoClick)
                    }
                }
            }
            // #30 (audio attachments): рендерим audio-вложения (как в FeedScreen).
            if (audioAttachments.isNotEmpty()) {
                AudioAttachmentList(tracks = audioAttachments.mapNotNull { it.audio })
            }
            // #30 (playlists): audio_playlist вложения.
            val playlistAttachments = post.attachments?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }.orEmpty()
            playlistAttachments.forEach { att -> att.audioPlaylist?.let { PlaylistAttachmentCard(playlist = it) } }
            // Fix #376 (#PROFILE-LINK-POLL-DOC): link/page/poll/doc вложения —
            // паритет с лентой (FeedScreen PostCard; LinkCard/PollCard/DocAttachmentCard
            // сделаны internal). Порядок и параметры 1:1 с FeedScreen:2352-2480.
            val linkAttachments = post.attachments.orEmpty()
                .filter { (it.type == "link" || it.type == "page") && it.link != null }
            linkAttachments.forEach { attach ->
                val link = attach.link
                if (link != null) {
                    LinkCard(link = link, onClick = {
                        // Fix #51-A: нормализация URL как в ленте — VK отдаёт короткие
                        // ссылки (vk.cc/abc) без схемы; валидация + resolveActivity.
                        val rawUrl = link.url.orEmpty().trim()
                        if (rawUrl.isEmpty()) {
                            AppLog.w("ProfileScreen", "LinkCard: url is empty, cannot open")
                            Toast.makeText(ctx, "Ссылка недоступна", Toast.LENGTH_SHORT).show()
                            return@LinkCard
                        }
                        val normalizedUrl = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
                        val uri = try {
                            android.net.Uri.parse(normalizedUrl)
                        } catch (e: Exception) {
                            AppLog.w("ProfileScreen", "LinkCard: invalid url=$rawUrl", e)
                            Toast.makeText(ctx, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
                            return@LinkCard
                        }
                        if (uri.scheme == null || uri.host == null) {
                            AppLog.w("ProfileScreen", "LinkCard: no scheme/host in url=$normalizedUrl")
                            Toast.makeText(ctx, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
                            return@LinkCard
                        }
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            uri,
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (intent.resolveActivity(ctx.packageManager) == null) {
                            AppLog.w("ProfileScreen", "LinkCard: no app to handle url=$normalizedUrl")
                            Toast.makeText(ctx, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show()
                            return@LinkCard
                        }
                        try {
                            ctx.startActivity(intent)
                            AppLog.i("ProfileScreen", "LinkCard: opened url=$normalizedUrl")
                        } catch (e: Exception) {
                            AppLog.w("ProfileScreen", "Failed to open link: $normalizedUrl", e)
                            Toast.makeText(ctx, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
            // Опросы (Sprint 4): голосование через polls.addVote (как в ленте).
            val pollAtt = post.attachments.orEmpty().firstOrNull { it.type == "poll" && it.poll != null }
            if (pollAtt != null) {
                val poll = pollAtt.poll
                if (poll != null) {
                    val pollVoteScope = rememberCoroutineScope()
                    val pollVoteApp = SovaApp.get()
                    PollCard(poll = poll, onVote = { answerIds ->
                        pollVoteScope.launch {
                            pollVoteApp.apiClient.pollsAddVote(poll.id, poll.ownerId, answerIds)
                        }
                    })
                }
            }
            // Документ-вложения (как в ленте).
            val docAttachments = post.attachments.orEmpty().filter { it.type == "doc" && it.doc != null }
            if (docAttachments.isNotEmpty()) {
                docAttachments.forEach { attach ->
                    val doc = attach.doc
                    if (doc != null) {
                        DocAttachmentCard(doc = doc, onOpen = {
                            // Fix #51-A: та же нормализация URL, что и для LinkCard выше.
                            val rawUrl = doc.url.orEmpty().trim()
                            if (rawUrl.isEmpty()) {
                                Toast.makeText(ctx, "Ссылка недоступна", Toast.LENGTH_SHORT).show()
                                return@DocAttachmentCard
                            }
                            val normalizedUrl = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
                            val uri = android.net.Uri.parse(normalizedUrl)
                            if (uri.scheme == null || uri.host == null) {
                                Toast.makeText(ctx, "Некорректная ссылка", Toast.LENGTH_SHORT).show()
                                return@DocAttachmentCard
                            }
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW, uri,
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                ctx.startActivity(intent)
                                AppLog.i("ProfileScreen", "DocCard: opened url=$normalizedUrl")
                            } catch (e: Exception) {
                                AppLog.w("ProfileScreen", "Failed to open doc: $normalizedUrl", e)
                                Toast.makeText(ctx, "Не удалось открыть документ", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
            // Fix #70: рендерим репост (copy_history) — первый элемент.
            post.copyHistory?.firstOrNull()?.let { repost ->
                RepostBlock(
                    repost = repost,
                    onPhotoClick = onPhotoClick,
                    onVideoClick = onVideoClick,
                    // Fix #373: тап по карточке репоста/развёрнутому тексту →
                    // PostDetail оригинала (тот же колбэк, что у тела поста).
                    onPostClick = onPostClick,
                    carouselEnabled = carouselEnabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // П-8-REACT: long-press по лайку → пикер реакций (паттерн
                // FeedScreen :2186-2210). Пикер показывается только когда
                // вызывающий экран прокинул onReaction (иначе dead-UX).
                var showReactions by remember { mutableStateOf(false) }
                Box {
                    ActionIcon(
                        icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        count = likeCount,
                        tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        // П-6a (#PROFILE-GAP-6a): кликабельный лайк записи стены
                        // (likes.add / likes.delete через onLikeToggle); во время полёта — disabled.
                        onClick = { onLikeToggle(post) },
                        // П-8-REACT: long-press → пикер реакций (только если экран
                        // прокинул onReaction; иначе null — поведение как раньше).
                        onLongClick = if (onReaction != null) {
                            { showReactions = true }
                        } else {
                            null
                        },
                        enabled = !likePending,
                    )
                    if (showReactions) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-48).dp)
                                .zIndex(10f),
                        ) {
                            ReactionPicker(
                                onDismiss = { showReactions = false },
                                // П-8-REACT: предвыделение «моей» реакции (0 = нет).
                                selectedReactionId = pickerSelection,
                                onSelect = { reaction -> onReaction?.invoke(post, reaction) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = commentCount,
                    // Шаг 5 (#32e): тап по комментарию → PostDetailScreen (через onCommentClick).
                    onClick = { onCommentClick(post) },
                )
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = Icons.Outlined.Repeat,
                    count = repostCount,
                    onClick = { onRepostClick(post) },
                )
                Spacer(Modifier.weight(1f))
                if (viewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(viewCount.toCountString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Fix #70: VideoThumbnail для video-вложений на стене (аналог FeedScreen.VideoThumbnail).
@Composable
fun VideoThumbnail(video: Video, onClick: (Video) -> Unit) {
    val thumbUrl = video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier.size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (video.duration > 0) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// Fix #70: RepostBlock — отображение copy_history[0] как вложенной карточки.
// Показывает заголовок «Запись <ownerId>» + текст + вложения оригинального поста
// (фото/видео/аудио/плейлисты — #PROFILE-REPOST-ATTACH).
// Sprint 2, P1-1 (#88): фото кликабельны → PhotoViewer.
@Composable
fun RepostBlock(
    repost: Post,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    onVideoClick: (Video) -> Unit = {},
    // Fix #373 (#PROFILE-REPOST-OPEN): тап по карточке репоста / по развёрнутому
    // или короткому тексту → открытие поста-оригинала (PostDetail через onPostClick
    // вызывающей карточки; колбэк пробрасывается из WallPostCard, дефолт {} —
    // как у соседей, прежние вызовы совместимы).
    onPostClick: (Post) -> Unit = {},
    // 22-B: карусель фото (общий SovaPrefs.feedCarouselEnabled, пробрасывается
    // из WallPostCard). Дефолт true — легаси-вызовы совместимы (паттерн 19-A).
    carouselEnabled: Boolean = true,
) {
    // Fix #373: контекст для linkify-кликов в тексте репоста (openUrlExternal).
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            // Fix #373: карточка репоста ЦЕЛИКОМ открывает оригинальный пост.
            // clickable ПОСЛЕ clip — риппл по скруглению; вложенные клики
            // (PostPhotoGrid/видео/аудио/ссылки ниже) перехватываются раньше —
            // открывается просмотр, а не пост (потомок кликабельнее родителя).
            .clickable { onPostClick(repost) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // Audit #40: для user-posts (ownerId>0) не показываем "клуба" —
                    // иначе получается двойной пробел "Запись  123". buildString аккуратнее.
                    text = buildString {
                        append("Запись")
                        if (repost.ownerId < 0) append(" клуба")
                        append(' ')
                        append(repost.ownerId)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (repost.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                // Fix #373 (#PROFILE-REPOST-EXPAND): текст репоста — паттерн Fix #345
                // (CommunityScreen:1337-1365 / FeedScreen:2571-2609) + linkify: «Показать
                // ещё» только при реальном переполнении (hasVisualOverflow); тап по
                // свёрнутому тексту = развернуть, по развёрнутому/короткому = открыть
                // пост целиком (onPostClick).
                var repostTextExpanded by remember { mutableStateOf(false) }
                var repostTextOverflowed by remember { mutableStateOf(false) }
                Text(
                    text = linkifyVkText(
                        text = repost.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onUrlClick = { url -> openUrlExternal(ctx, url) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (repostTextOverflowed && !repostTextExpanded) {
                            repostTextExpanded = true
                        } else {
                            onPostClick(repost)
                        }
                    },
                    onTextLayout = { result ->
                        if (!repostTextExpanded && result.hasVisualOverflow) repostTextOverflowed = true
                    },
                    maxLines = if (repostTextExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repostTextOverflowed && !repostTextExpanded) {
                    Text(
                        text = "Показать ещё",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clickable { repostTextExpanded = true },
                    )
                }
            }
            // Фото из репоста
            val repostPhotos = repost.attachments
                ?.filter { it.type == "photo" && it.photo != null }
                .orEmpty()
            if (repostPhotos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                // 22-B: общий PostPhotoGrid (см. WallPostCard выше).
                PostPhotoGrid(
                    photos = repostPhotos.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                    carouselEnabled = carouselEnabled,
                )
            }
            // #PROFILE-REPOST-ATTACH: видео в репосте (как в WallPostCard).
            val repostVideos = repost.attachments
                ?.filter { it.type == "video" && it.video != null }
                .orEmpty()
            // Fix #373: несколько видео в репосте и включённая карусель —
            // общий PostVideoCarousel (как в посте выше и в ленте, Fix #366);
            // иначе прежний вертикальный стопк VideoThumbnail (одно видео).
            if (carouselEnabled && repostVideos.size > 1) {
                Spacer(Modifier.height(6.dp))
                PostVideoCarousel(
                    videos = repostVideos.mapNotNull { it.video },
                    carouselEnabled = carouselEnabled,
                    onVideoClick = onVideoClick,
                )
            } else {
                repostVideos.forEach { att ->
                    val v = att.video
                    if (v != null) {
                        Spacer(Modifier.height(6.dp))
                        VideoThumbnail(video = v, onClick = onVideoClick)
                    }
                }
            }
            // #PROFILE-REPOST-ATTACH: аудио в репосте.
            val repostAudios = repost.attachments
                ?.filter { it.type == "audio" && it.audio != null }
                .orEmpty()
            if (repostAudios.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AudioAttachmentList(tracks = repostAudios.mapNotNull { it.audio })
            }
            // #PROFILE-REPOST-ATTACH: плейлисты в репосте.
            repost.attachments
                ?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }
                ?.forEach { att ->
                    att.audioPlaylist?.let {
                        Spacer(Modifier.height(6.dp))
                        PlaylistAttachmentCard(playlist = it)
                    }
                }
        }
    }
}

// П-8-REACT: ExperimentalFoundationApi — combinedClickable для long-press
// (в foundation 1.8 / BOM 2025.06 stable-перегрузка с named onClick/onLongClick
// opt-in не требует — аннотация страховочная, как @OptIn в FeedScreen:2231).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    // П-8-REACT: long-press (пикер реакций на лайке); null — прежнее поведение.
    // Прочие вызовы (комментарий/репост) параметр не передают. Named-аргументы
    // combinedClickable идентичны FeedScreen.ActionIcon (:2391);
    // onLongClick == null деградирует до простого клика.
    onLongClick: (() -> Unit)? = null,
    // П-6a: disabled-состояние — во время API-полёта clickable не вешается.
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(
                if (onClick != null && enabled) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toCountString(), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// П-1 (#PROFILE-SNAP): вкладки контента и секции своего профиля.
// ═══════════════════════════════════════════════════════════════════════════

/** Единый ряд чипов (вкладки контента + подвкладки стены). */
@Composable
private fun ProfileChipsRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // П-6b (#PROFILE-GAP-6b): 7 вкладок контента не помещаются на узких экранах —
    // ряд скроллится горизонтально (для 3 подвкладок стены поведение то же —
    // они помещаются, скролл просто не активен).
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Card(
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onSelect(value) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Подвкладки стены: «Все / Свои / Архив» («Архив» — только если доступен). */
@Composable
private fun WallFilterRow(selected: String, showArchive: Boolean, onSelect: (String) -> Unit) {
    val filters = buildList {
        add(WALL_FILTER_ALL to "Все")
        add(WALL_FILTER_OWNER to "Свои")
        if (showArchive) add(WALL_FILTER_ARCHIVED to "Архив")
    }
    ProfileChipsRow(options = filters, selected = selected, onSelect = onSelect)
}

@Composable
private fun TabProgressRow() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun TabErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun TabEmptyRow(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** W33-c: строка трека полного раздела «Музыка» (вертикальный список,
 *  виртуализируется общим LazyColumn профиля; тап — playTrackList всей
 *  загруженной очереди, семантика плейлиста волны 31 #AUDIO-QUEUE-PLAYLIST). */
@Composable
private fun ProfileTrackRow(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = track.albumThumb
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (track.duration > 0) {
            Text(
                text = "${track.duration / 60}:${"%02d".format(track.duration % 60)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** W33-c: строка «Показать ещё» пагинации вкладок профиля (честные
 *  состояния: спиннер при дозагрузке, кнопка — когда есть что грузить). */
@Composable
private fun TabShowMoreRow(loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onClick) { Text("Показать ещё") }
        }
    }
}

/** W33-c: карточка видео сетки раздела «Видео» (вертикальная сетка 2 колонки;
 *  ширина задаёт вызывающий через [modifier] — weight(1f) в строке-чанке). */
@Composable
private fun ProfileVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumbUrl = video.thumbUrl
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                if (video.duration > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Text(
                text = video.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** W33-c: строка сетки «Фото» (3 колонки) — вызывается как item общего
 *  LazyColumn (виртуализация полного раздела). Тап открывает PhotoViewer
 *  с ПОЛНЫМ списком URL и глобальным индексом ([startIndex] + колонка). */
@Composable
private fun PhotoGridRow(
    urls: List<String>,
    startIndex: Int,
    fullList: List<String>,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        urls.forEachIndexed { colIdx, url ->
            Box(
                modifier = Modifier.weight(1f).aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPhotoClick(fullList, startIndex + colIdx) },
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Добиваем последнюю строку пустыми ячейками до 3 колонок.
        repeat(3 - urls.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

/** URL лучшего размера из item photos.getAll (sizes[] — max по площади). */
private fun extractPhotoAllUrl(photo: JsonObject): String? {
    return try {
        var bestUrl: String? = null
        var bestArea = 0L
        val sizes = photo.getAsJsonArray("sizes")
        if (sizes != null) {
            for (el in sizes) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                val urlEl = o.get("url") ?: continue
                if (!urlEl.isJsonPrimitive) continue
                val wEl = o.get("width")
                val hEl = o.get("height")
                val w = if (wEl != null && wEl.isJsonPrimitive) wEl.asInt else 0
                val h = if (hEl != null && hEl.isJsonPrimitive) hEl.asInt else 0
                val area = w.toLong() * h.toLong()
                if (area > bestArea) {
                    bestArea = area
                    bestUrl = urlEl.asString
                }
            }
        }
        if (bestUrl != null) return bestUrl
        // Fallback: статические photo_* поля, если sizes пуст.
        listOf("photo_807", "photo_604", "photo_130").firstNotNullOfOrNull { key ->
            val el = photo.get(key)
            if (el != null && el.isJsonPrimitive) el.asString else null
        }
    } catch (e: Exception) {
        AppLog.e("ProfileScreen", "extractPhotoAllUrl parse error", e)
        null
    }
}

/** Секция «Подарки»: gifts.get → LazyRow открыток. */
/** П-1: раздел «Подарки» (gifts.get) — ряд открыток; W33-c: + пагинация
 *  «Показать ещё» (hasMore/loadingMore/onShowMore — дефолты не ломают
 *  существующие вызовы). */
@Composable
private fun GiftsSection(
    gifts: List<JsonObject>,
    totalCount: Int,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onShowMore: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = if (totalCount > 0) "Подарки ($totalCount)" else "Подарки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                gifts,
                key = { idx, _ -> "gift_$idx" },
            ) { _, giftItem ->
                val thumbUrl = extractGiftThumbUrl(giftItem)
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = "Подарок",
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        // W33-c: пагинация «Показать ещё» (дефолт hasMore=false — строки нет).
        if (hasMore) {
            TabShowMoreRow(loading = loadingMore, onClick = onShowMore)
        }
    }
}

/**
 * URL открытки из «сырого» item gifts.get. Реальная структура (снапшот
 * «Профиль», apiPrefetchCache): {id, from_id, message, date,
 * gift: {id, thumb_512, thumb_256, thumb_96, thumb_48, ...}, gift_hash} —
 * поля photo_270/photo_200 в ответе отсутствуют, берём thumb_*.
 */
private fun extractGiftThumbUrl(giftItem: JsonObject): String? {
    return try {
        val giftObj = giftItem.getAsJsonObject("gift") ?: giftItem
        listOf("thumb_512", "thumb_256", "thumb_96", "thumb_48").firstNotNullOfOrNull { key ->
            val el = giftObj.get(key)
            if (el != null && el.isJsonPrimitive && el.asString.isNotBlank()) el.asString else null
        }
    } catch (e: Exception) {
        null
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// П-6b (#PROFILE-GAP-6b): остаток плана профиля — вкладки Клипы/Статьи/Закладки
// (инвентарь §5 п.2 «секции правой колонки (…клипы/статьи)», §3.2) и блок
// «Возможно, вы знакомы» (§5 п.3, переиспользован friendsGetRecommendations).
// ═══════════════════════════════════════════════════════════════════════════

/** Размер страницы закладок (fave.get) и порог «Загрузить ещё». */
private const val BOOKMARKS_PAGE_SIZE = 30

/**
 * Секция «Возможно, вы знакомы» (§5 п.3 — friends.getRecommendations).
 * LazyRow карточек: аватар + имя + кнопка «+» (friends.add). Тап по карточке —
 * чужой профиль ([onOpen]; паттерн FriendsScreen.onUserClick → Screen.UserProfile).
 * После успешного friends.add кнопка становится disabled: «Заявка отправлена»
 * (friend_status=2) / «Добавлен» (friend_status=1 — мгновенное одобрение).
 * Секция рисуется только при непустом списке (вызывающий экран: пусто/ошибка
 * API → секция не рисуется — паттерн подарков П-1).
 */
@Composable
private fun FriendSuggestionsSection(
    suggestions: List<Friend>,
    sentStatuses: Map<Long, Int>,
    inFlight: Map<Long, Boolean>,
    onAdd: (Friend) -> Unit,
    onOpen: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = "Возможно, вы знакомы",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                suggestions,
                // Ключ с индексом — crash-proof к дублям (прецедент Fix #281).
                key = { idx, friend -> "suggest_${idx}_${friend.id}" },
            ) { _, friend ->
                ProfileSuggestionCard(
                    friend = friend,
                    sentStatus = sentStatuses[friend.id],
                    inFlight = inFlight[friend.id] == true,
                    onAdd = { onAdd(friend) },
                    onClick = { onOpen(friend.id) },
                )
            }
        }
    }
}

/** Карточка «Возможно, вы знакомы»: аватар + имя + кнопка «+». */
@Composable
private fun ProfileSuggestionCard(
    friend: Friend,
    sentStatus: Int?,
    inFlight: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val avatarUrl = friend.photo200 ?: friend.photo100
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = friend.fullName,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = friend.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = friend.fullName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            if (sentStatus != null) {
                // friends.add уже прошёл — кнопка замещается disabled-меткой.
                Text(
                    text = if (sentStatus == 1) "Добавлен" else "Заявка отправлена",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(
                    onClick = onAdd,
                    enabled = !inFlight,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Вкладка «Клипы» (§5 п.2): shortVideo.getOwnerVideos → LazyRow вертикальных
 * карточек-превью (паттерн бывшей VideoTabSection П-1, W33-c — сетка 2 колонки).
 *
 * ОТКРЫТИЕ КЛИПА (честная механика): выделенного маршрута «конкретный клип» в
 * приложении нет (Screen.Clips — параметless-фид ClipsFeedScreen), поэтому клип
 * открывается СУЩЕСТВУЮЩИМ механизмом [onClipClick] → VideoHolder.open →
 * VideoPlayerScreen — тот же живой путь, что у вкладки «Видео». Плеер сам
 * дорезолвит воспроизведение: videoGetById(ownerId, id, accessKey) → files/hls
 * (accessKey сохраняется в модели Video при парсинге) — просмотр живой,
 * не превью-заглушка.
 */
@Composable
private fun ClipsTabSection(
    clips: List<Video>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onClipClick: (Video) -> Unit,
) {
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        clips.isEmpty() -> TabEmptyRow("У вас пока нет клипов")
        else -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    clips,
                    key = { idx, clip -> "clip_${idx}_${clip.ownerId}_${clip.id}" },
                ) { _, clip ->
                    ProfileClipCard(clip = clip, onClick = { onClipClick(clip) })
                }
            }
        }
    }
}

/** Вертикальная карточка клипа: постер (first_frames → covers) + длительность. */
@Composable
private fun ProfileClipCard(clip: Video, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val posterUrl = clip.clipPosterUrl
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = clip.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (clip.duration > 0) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${clip.duration / 60}:${"%02d".format(clip.duration % 60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/**
 * Вкладка «Статьи» (§5 п.2): articles.getOwnerPublished → список карточек.
 * Модели статьи в core-моделях для этого ответа нет (метод возвращает сырые
 * JsonObject), поэтому title/url/обложка парсятся терпеливо здесь
 * ([extractArticleInfo], isJsonNull-гварды — как extractPhotoAllUrl П-1).
 *
 * Тап по статье → системный браузер ([onOpenArticle] → Linkify.openUrlExternal,
 * ACTION_VIEW): статья — веб-контент vk.com/@…, честное поведение.
 * Статья без URL рисуется некликабельной.
 *
 * ОТКЛОНЕНИЕ от веб-снапшота: бандл pageProfile запрашивает count:3 (виджет
 * сайдбара); здесь вкладка — полноценный список, запрашивается страница из 20.
 */
@Composable
private fun ArticlesTabSection(
    articles: List<JsonObject>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    val items = remember(articles) { articles.mapNotNull { extractArticleInfo(it) } }
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        items.isEmpty() -> TabEmptyRow("У вас пока нет статей")
        else -> Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // FIX-COMPILE: itemsIndexed — extension LazyListScope, в обычном Column
            // недоступна (статьи ≤20, ленивость не нужна) → forEach.
            items.forEach { item ->
                ProfileArticleRow(item = item, onOpen = onOpenArticle)
            }
        }
    }
}

/** Карточка статьи: обложка (если есть) + заголовок + дата/просмотры. */
@Composable
private fun ProfileArticleRow(item: ProfileArticleInfo, onOpen: (String) -> Unit) {
    val url = item.url
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (url != null) Modifier.clickable { onOpen(url) } else Modifier),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imageUrl = item.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "Статья" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    if (item.publishedDate > 0) add(item.publishedDate.toRelativeTime())
                    if (item.views > 0) add("${item.views.toCountString()} просмотров")
                }
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Вкладка «Закладки» (§5 п.2): fave.get (СВОИ закладки, extended=1) → список
 * строк (миниатюра/название/тип). Тап — только по типам с живой навигацией
 * ([bookmarkIsOpenable]):
 *  - post → PostDetailScreen (onPostClick: PostHolder.last + navigate, паттерн
 *    BookmarksScreen.onPostClick);
 *  - video → VideoHolder.open → VideoPlayerScreen;
 *  - photo → существующий PhotoViewer (самый большой sizes);
 *  - link → системный браузер (openUrlExternal — веб-контент);
 *  - user → чужой профиль (Screen.UserProfile через onUserClick).
 * Остальные типы (article, product, …) с волны 40 #BOOKMARKS-REMOVE-ALL:
 * Bookmark несёт минимум полей (objectId/objectOwnerId/objectTitle — заголовок
 * строки корректен), а ДОЛГОЕ НАЖАТИЕ по строке УДАЛЯЕТ закладку через
 * VKApiClient.bookmarkRemove (все типы: видео с access_key, ссылки по link_id,
 * статьи/товары через fave.removeArticle/removeProduct — жалоба тестера
 * 2026-09-12 «Нет возможности удалить из закладок»). Подгрузка —
 * «Загрузить ещё» (hasMore = последняя страница полная).
 */
@Composable
private fun BookmarksTabSection(
    bookmarks: List<Bookmark>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    // Волна 40 #BOOKMARKS-REMOVE-ALL: удаление закладки (long-press → диалог).
    onRemove: (Bookmark) -> Unit = {},
) {
    // Волна 40: цель удаления (подтверждение — AlertDialog ниже).
    var removeTarget by remember { mutableStateOf<Bookmark?>(null) }
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        bookmarks.isEmpty() -> TabEmptyRow("У вас пока нет закладок")
        else -> Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bookmarks.forEach { bookmark ->
                ProfileBookmarkRow(
                    bookmark = bookmark,
                    onClick = { onOpen(bookmark) },
                    onLongClick = { removeTarget = bookmark },
                )
            }
            if (hasMore) {
                TextButton(
                    onClick = onLoadMore,
                    enabled = !loadingMore,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (loadingMore) "Загрузка…" else "Загрузить ещё")
                }
            }
        }
    }
    // Волна 40 #BOOKMARKS-REMOVE-ALL: подтверждение удаления (паттерн BookmarksScreen).
    removeTarget?.let { bm ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Удалить из закладок?") },
            text = {
                Text(
                    text = bm.title.ifBlank { bm.type },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    onRemove(bm)
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/** Строка закладки: миниатюра (если есть) + название + метка типа.
 *  Волна 40 #BOOKMARKS-REMOVE-ALL: long-press = «Удалить из закладок».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileBookmarkRow(bookmark: Bookmark, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val openable = bookmarkIsOpenable(bookmark)
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                // Тап работает только по открытым типам (как раньше);
                // long-press всегда доступен — удаление.
                onClick = { if (openable) onClick() },
                onLongClick = onLongClick,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = bookmark.thumbUrl
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = bookmark.title,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bookmarkTypeLabel(bookmark.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Тип закладки → человекочитаемая метка. */
private fun bookmarkTypeLabel(type: String): String = when (type) {
    "user" -> "Пользователь"
    "group" -> "Сообщество"
    "post" -> "Запись"
    "photo" -> "Фотография"
    "video" -> "Видео"
    "link" -> "Ссылка"
    "article" -> "Статья"
    "product" -> "Товар"
    else -> type
}

/**
 * Есть ли у закладки живая навигация (см. KDoc BookmarksTabSection): только
 * типы с распарсенной VKA сущностью и доступным просмотром.
 */
private fun bookmarkIsOpenable(bookmark: Bookmark): Boolean = when (bookmark.type) {
    "post" -> bookmark.post != null
    "video" -> bookmark.video != null
    "photo" -> bookmark.photo?.largestUrl != null
    "link" -> !bookmark.link?.url.isNullOrBlank()
    "user" -> bookmark.user != null
    else -> false
}

/**
 * Поля статьи из «сырого» item articles.getOwnerPublished (patient-parsing):
 * url → ("url", "view_url"), обложка → ("preview_img", "cover_photo",
 * "photo_200", "photo"), дата → "published_date", просмотры → "views".
 * Возвращает null, если в item нет ни заголовка, ни URL (рисовать нечего).
 */
private fun extractArticleInfo(article: JsonObject): ProfileArticleInfo? {
    return try {
        val title = jsonPrimitiveStr(article, "title", "subtitle").orEmpty()
        val url = jsonPrimitiveStr(article, "url", "view_url")
        if (title.isBlank() && url == null) return null
        ProfileArticleInfo(
            title = title,
            url = url,
            imageUrl = jsonPrimitiveStr(article, "preview_img", "cover_photo", "photo_200", "photo"),
            publishedDate = article.get("published_date")
                ?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
            views = article.get("views")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
        )
    } catch (e: Exception) {
        AppLog.e("ProfileScreen", "extractArticleInfo parse error", e)
        null
    }
}

/** Первое непустое строковое поле из кандидатов-ключей (гварды null/primitive). */
private fun jsonPrimitiveStr(o: JsonObject, vararg keys: String): String? {
    for (key in keys) {
        val el = o.get(key) ?: continue
        if (el.isJsonPrimitive && el.asString.isNotBlank()) return el.asString
    }
    return null
}

/** Распарсенные поля статьи вкладки «Статьи» (см. [extractArticleInfo]). */
private data class ProfileArticleInfo(
    val title: String,
    val url: String?,
    val imageUrl: String?,
    val publishedDate: Long,
    val views: Int,
)

