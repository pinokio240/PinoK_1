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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
// П-7-AB: Flag — пункт «Пожаловаться» в «⋯»-меню записи.
import androidx.compose.material.icons.outlined.Flag
// PROFILE-ALBUMS: иконка-плейсхолдер обложки альбома (как CommunityAlbumRow).
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
// Fix #378: разделитель над закреплённой кнопкой выхода.
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Album
import re.pinok.data.model.Bookmark
import re.pinok.data.model.Friend
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.Post
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.data.model.VideoAlbum
import re.pinok.media.PlayerConnection
// PHOTO-SHARE-WIRE: share intent + ShareToChatSheet для PhotoViewer.
import android.content.Intent
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.ShareToChatSheet
import re.pinok.ui.components.RepostDialog
import re.pinok.ui.components.ScrollToTopFab
// POSTCARD-REFACTOR-PLAN (этап 3c): WallPostCard — обёртка-делегат единого
// PostCard; пункты её «⋯»-меню — через PostMenuItem.
import re.pinok.ui.components.PostCard
import re.pinok.ui.components.PostMenuItem
import re.pinok.ui.components.RepostGroupCache
// VIDEO-ALBUMS-CRUD: общие UI-компоненты для управления альбомами/видео.
import re.pinok.ui.components.VideoContextMenuDropdown
import re.pinok.ui.components.AlbumContextMenuDropdown
import re.pinok.ui.components.CreateVideoAlbumCard
import re.pinok.ui.components.CreateVideoAlbumDialog
import re.pinok.ui.components.RenameVideoAlbumDialog
import re.pinok.ui.components.DeleteVideoAlbumConfirmDialog
import re.pinok.ui.components.AddVideoToAlbumDialog
import re.pinok.ui.components.DeleteVideoConfirmDialog
// П-8-REACT: пикер реакций стены — переиспользование feed-компонента как есть
// (+ аддитивный selectedReactionId с дефолтом; FeedScreen не затронут).
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
internal const val PROFILE_TAB_WALL = "wall"
internal const val PROFILE_TAB_MUSIC = "music"
internal const val PROFILE_TAB_VIDEO = "video"
internal const val PROFILE_TAB_PHOTO = "photo"
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
    // POSTCARD-REFACTOR-PLAN: тап по имени группы/канала в шапке поста стены →
    // сообщество (Screen.Community.buildRoute по положительному id). Своя стена
    // может содержать посты сообществ (republished/repost) — onGroupClick ведёт
    // в них. Дефолт {} — легаси-вызовы не меняют поведение.
    onGroupClick: (Long) -> Unit = {},
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
    // PHOTO-SHARE-WIRE: URL для ShareToChatSheet — открывается из пункта «Отправить»
    // в «⋮»-меню PhotoViewer. null → sheet закрыт.
    var shareToChatUrl by remember { mutableStateOf<String?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // Группы-источники РЕПОСТОВ стены (copy_history[0] с ownerId<0) → имя/аватар.
    // Резолв через groups.getById лениво в LaunchedEffect каждого поста (см. items
    // ниже); кладётся в Snapshot-state → рекомпозиция подставляет имя/фото в шапку
    // репоста вместо «Запись клуба <id>».
    val repostGroups = remember { RepostGroupCache() }
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
    // PROFILE-ALBUMS: альбомы во вкладке «Фото» (photos.getAlbums) —
    // горизонтальный список карточек над «Все фото». openedAlbum != null →
    // пользователь тапнул по альбому, рендерим фото этого альбома (photos.get)
    // в сетке + кнопку «Назад к альбомам». Паттерн — CommunityScreen:223 (tab 7).
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    var albumsLoaded by remember { mutableStateOf(false) }
    var openedAlbum by remember { mutableStateOf<Album?>(null) }
    var albumPhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var albumPhotosLoading by remember { mutableStateOf(false) }
    var albumPhotosError by remember { mutableStateOf<String?>(null) }
    // PROFILE-ALBUMS: счётчик ретраев фото альбома (кнопка «Повторить» в
    // TabErrorRow) — пробрасывается в ключ LaunchedEffect, чтобы перезапустить
    // photos.get при той же ссылке на openedAlbum (паттерн contentRetryTick).
    var albumPhotosRetryTick by remember { mutableStateOf(0) }
    // ALBUMS-IN-VIDEO: state для видеоальбомов внутри вкладки «Видео»
    // (паттерн PROFILE-ALBUMS для фотоальбомов → видеоальбомы). Грузится
    // video.getAlbums(ownerId=p.id — ПОЛОЖИТЕЛЬНЫЙ для своего профиля).
    // openedVideoAlbum != null → рендерим видео этого альбома (video.get с
    // album_id) в сетке + кнопку «← Видеоальбомы» (AlbumBackRow).
    var videoAlbums by remember { mutableStateOf<List<VideoAlbum>>(emptyList()) }
    var videoAlbumsLoading by remember { mutableStateOf(false) }
    var videoAlbumsError by remember { mutableStateOf<String?>(null) }
    var videoAlbumsLoaded by remember { mutableStateOf(false) }
    var openedVideoAlbum by remember { mutableStateOf<VideoAlbum?>(null) }
    var albumVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var albumVideosLoading by remember { mutableStateOf(false) }
    var albumVideosError by remember { mutableStateOf<String?>(null) }
    // ALBUMS-IN-VIDEO: счётчик ретраев видео альбома (кнопка «Повторить» в
    // TabErrorRow) — пробрасывается в ключ LaunchedEffect(openedVideoAlbum),
    // чтобы перезапустить video.get(albumId) при той же ссылке на альбом
    // (паттерн albumPhotosRetryTick для фотоальбомов выше).
    var albumVideosRetryTick by remember { mutableStateOf(0) }
    // REVIEWER-2: тик перезагрузки списка альбомов после CRUD (create/rename/
    // delete). LaunchedEffect(selectedContentTab, contentRetryTick) НЕ перезапускается
    // при смене videoAlbumsLoaded, поэтому без этого тика список после CRUD
    // оставался бы пустым/устаревшим до ручного переключения вкладки.
    var videoAlbumsRefreshTick by remember { mutableStateOf(0) }

    // VIDEO-ALBUMS-CRUD: state для диалогов и контекстных меню. Свой профиль →
    // можно создавать/переименовывать/удалять альбомы и управлять видео в них.
    // canManageAlbums=true для своего профиля (isSelf всегда true в ProfileScreen).
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var renameAlbumTarget by remember { mutableStateOf<VideoAlbum?>(null) }
    var deleteAlbumTarget by remember { mutableStateOf<VideoAlbum?>(null) }
    var showAlbumMenuFor by remember { mutableStateOf<VideoAlbum?>(null) }
    var showVideoMenuFor by remember { mutableStateOf<Video?>(null) }
    var showAlbumVideoMenuFor by remember { mutableStateOf<Video?>(null) }
    var addToAlbumTarget by remember { mutableStateOf<Video?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<Video?>(null) }
    val videoCrudContext = LocalContext.current
    // Свой профиль — currentUserId = profile.id (== app.getVkUid() после загрузки).
    val currentUserId = profile?.id ?: 0L

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
        launch {
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
                    launch {
                        val wallTabs = app.apiClient.usersGetWallTabs(prof.id)
                        hasArchiveWallTab = wallTabs.any { it.type == "archived" && it.count > 0 }
                    }
                    launch {
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
                        launch {
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
                    launch {
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
        launch {
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
        launch {
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
    LaunchedEffect(selectedContentTab, contentRetryTick, videoAlbumsRefreshTick) {
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
                    // #AUDIO-PAGING-HOLE (волна 41): total=-1 = сбой вызова (гейт/
                    // токен/капча/API) — это НЕ «пустая музыка»: честная ошибка
                    // с «Повторить» вместо молчаливого пустого состояния.
                    if (total < 0 && page.isEmpty()) {
                        musicError = if (app.networkObserver.isOffline()) {
                            "Нет сети. Подключитесь к интернету и повторите."
                        } else {
                            "Не удалось загрузить музыку: ${app.apiClient.lastApiErrorHuman() ?: "неизвестная причина"}"
                        }
                        AppLog.w("ProfileScreen", "Music tab: silent fail (total=-1, offline=${app.networkObserver.isOffline()})")
                    } else {
                        // total=-1 при непустой странице (catalog-fallback) — счётчик не трогаем.
                        if (total > 0) musicTotal = total
                        musicServerOffset = page.size
                        musicTracks = page.filter { it.id > 0L && !it.url.isNullOrBlank() }
                        // #AUDIO-PAGING-HOLE (волна 41): НЕпустая страница (даже
                        // КОРОТКАЯ) ≠ конец списка — VK режет листинг на «битой»
                        // записи (прецедент стопа 149/3239: страница offset=100
                        // вернула 49 raw → «полная страница»=false → стоп).
                        // Конец = пустая страница (в loadMore — после прыжков через дыры).
                        musicHasMore = page.isNotEmpty()
                        musicLoaded = true
                        AppLog.i("ProfileScreen", "Music tab loaded: ${musicTracks.size} tracks (total=$total)")
                    }
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Music tab load failed", e)
                    musicError = "Ошибка: ${e.message}"
                } finally {
                    musicLoading = false
                }
            }
            PROFILE_TAB_VIDEO -> {
                // REVIEWER-2: видеоальбомы грузим НЕЗАВИСИМО от «всех видео» —
                // CRUD (videoAlbumsRefreshTick++) может инвалидировать список альбомов,
                // не затрагивая videoLoaded. Раньше этот блок был внутри
                // `if (!videoLoaded && !videoLoading)` и блокировался после первой
                // загрузки видео — список альбомов не обновлялся после CRUD.
                if (!videoAlbumsLoaded && !videoAlbumsLoading) {
                    launch {
                        videoAlbumsLoading = true
                        videoAlbumsError = null
                        try {
                            val (total, list) = app.apiClient.videoGetAlbums(ownerId = ownerId, count = 100)
                            videoAlbums = list
                            videoAlbumsLoaded = true
                            AppLog.i("ProfileScreen", "Video albums loaded: ${videoAlbums.size} for uid=$ownerId (total=$total)")
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "Video albums load failed", e)
                            videoAlbumsError = "Ошибка: ${e.message}"
                        } finally {
                            videoAlbumsLoading = false
                        }
                    }
                }
                if (!videoLoaded && !videoLoading) {
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
            }
            PROFILE_TAB_PHOTO -> if (!photoLoaded && !photoLoading) {
                photoLoading = true
                photoError = null
                // PROFILE-ALBUMS: параллельно с «всеми фото» грузим альбомы
                // (photos.getAlbums) — для горизонтального списка над «Все фото».
                if (!albumsLoaded && !albumsLoading) {
                    launch {
                        albumsLoading = true
                        albumsError = null
                        try {
                            albums = app.apiClient.photosGetAlbums(ownerId = ownerId)
                            albumsLoaded = true
                            AppLog.i("ProfileScreen", "Albums loaded: ${albums.size} for uid=$ownerId")
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "Albums load failed", e)
                            albumsError = "Ошибка: ${e.message}"
                        } finally {
                            albumsLoading = false
                        }
                    }
                }
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
                        .items.filter { it.id > 0L }
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

    // PROFILE-ALBUMS: открыли альбом — грузим его фото (photos.get,
    // album_id = album.id, owner_id = owner профиля). Сброс при тапе «Назад»:
    // openedAlbum = null (гейт в начале; старые albumPhotos очищать не нужно —
    // они не рисуются при null). albumPhotosRetryTick — кнопка «Повторить» в
    // TabErrorRow (паттерн contentRetryTick). Паттерн — CommunityScreen:1715.
    LaunchedEffect(openedAlbum, albumPhotosRetryTick) {
        val album = openedAlbum ?: return@LaunchedEffect
        val prof = profile ?: return@LaunchedEffect
        albumPhotosLoading = true
        albumPhotosError = null
        try {
            albumPhotos = app.apiClient.photosGet(
                ownerId = prof.id,
                albumId = album.id.toString(),
                count = 200,
                offset = 0,
            )
            AppLog.i("ProfileScreen", "Album photos loaded: ${albumPhotos.size} for «${album.title}»")
        } catch (e: Exception) {
            AppLog.e("ProfileScreen", "Album photos load failed", e)
            albumPhotosError = "Ошибка: ${e.message}"
        } finally {
            albumPhotosLoading = false
        }
    }

    // ALBUMS-IN-VIDEO: открыли видеоальбом — грузим его видео (video.get с
    // album_id). Паттерн — LaunchedEffect(openedAlbum) для фотоальбомов выше.
    // albumVideosRetryTick — кнопка «Повторить» в TabErrorRow перезапускает
    // этот эффект (паттерн albumPhotosRetryTick). Сброс при тапе «Назад»:
    // openedVideoAlbum = null (гейт в начале).
    LaunchedEffect(openedVideoAlbum, albumVideosRetryTick) {
        val album = openedVideoAlbum ?: return@LaunchedEffect
        val prof = profile ?: return@LaunchedEffect
        albumVideosLoading = true
        albumVideosError = null
        try {
            albumVideos = app.apiClient.videoGet(ownerId = prof.id, count = 50, albumId = album.id)
            AppLog.i("ProfileScreen", "Album videos loaded: ${albumVideos.size} for «${album.title}»")
        } catch (e: Exception) {
            AppLog.e("ProfileScreen", "Album videos load failed", e)
            albumVideosError = "Ошибка: ${e.message}"
        } finally {
            albumVideosLoading = false
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
            // P0.44-audit B2: "page" — fave.get(item_type=page) MIX users+groups.
            // user (id>0) → onUserClick, group (id<0, после нормализации +) → onGroupClick.
            "user", "page" -> {
                bookmark.user?.let { onUserClick(it.id) }
                    ?: bookmark.group?.let { onGroupClick(it.id) }
            }
            "group" -> bookmark.group?.let { onGroupClick(it.id) }
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
    // у music (#AUDIO-PAGING-HOLE, волна 41) — по непустой RAW-странице
    // (короткая страница ≠ конец: VK режет листинг на «битой» записи).
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
                // #AUDIO-PAGING-HOLE: total=-1 (сбой) — счётчик не затираем.
                if (total > 0) musicTotal = total
                musicServerOffset += page.size
                val fresh = page.filter { it.id > 0L && !it.url.isNullOrBlank() }
                    .filter { nv -> musicTracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                musicTracks = musicTracks + fresh
                // #AUDIO-PAGING-HOLE (волна 41): НЕпустая страница (даже короткая)
                // ≠ конец списка (прецедент стопа 149/3239). Сбоя (total=-1) —
                // hasMore сохраняется: следующий скролл/кнопка повторят попытку.
                musicHasMore = page.isNotEmpty() || total < 0
                AppLog.i("ProfileScreen",
                    "Music loadMore: +${fresh.size} (offset=$musicServerOffset total=$total)")
                // Дыры в листинге: пустая страница при offset < total — прыжки +1
                // через «битую» запись (до 5 попыток inline, без разгона пейджера).
                var hops = 0
                while (!musicHasMore && hops < 5 && musicTotal > 0 && musicServerOffset < musicTotal) {
                    hops++
                    val hopOffset = musicServerOffset + 1
                    val (hopTotal, hopPage) = app.apiClient.audioGetWithCount(
                        PROFILE_MUSIC_PAGE, hopOffset, prof.id)
                    if (hopTotal > 0) musicTotal = hopTotal
                    musicServerOffset = hopOffset + hopPage.size
                    val hopFresh = hopPage.filter { it.id > 0L && !it.url.isNullOrBlank() }
                        .filter { nv -> musicTracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                    if (hopPage.isNotEmpty()) musicTracks = musicTracks + hopFresh
                    musicHasMore = hopPage.isNotEmpty() || hopTotal < 0
                    AppLog.i("ProfileScreen", "Music hole-hop $hops: offset=$hopOffset got=${hopPage.size} (total=$musicTotal)")
                }
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
                // Группа-источник репоста (copy_history[0], ownerId<0): резолвим
                // её имя/аватар через groups.getById лениво. LaunchedEffect можно
                // вызывать внутри items-lambda (это composable-контекст). Кэш
                // защищает от повторов и от падения сети (runCatching внутри).
                val repostOwnerId = post.copyHistory?.firstOrNull()?.ownerId
                if (repostOwnerId != null && repostOwnerId < 0) {
                    LaunchedEffect(post.ownerId, post.id) { repostGroups.resolve(app.apiClient, post) }
                }
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
                    // POSTCARD-REFACTOR-PLAN: тап по имени/аватару автора поста —
                    // группа/канал → сообщество, пользователь → чужой профиль.
                    onGroupClick = onGroupClick,
                    onUserClick = onUserClick,
                    // Группа-источник РЕПОСТА: имя/аватар из кэша репост-групп
                    // (null до загрузки → заголовок «Запись клуба <id>», затем
                    // рекомпозиция подставит имя/фото). Клик по репост-шапке
                    // ВЕДЁТ В СООБЩЕСТВО этой группы.
                    repostGroupName = if (repostOwnerId != null && repostOwnerId < 0) repostGroups.nameOf(-repostOwnerId) else null,
                    repostGroupPhoto = if (repostOwnerId != null && repostOwnerId < 0) repostGroups.photoOf(-repostOwnerId) else null,
                    onRepostGroupClick = {
                        post.copyHistory?.firstOrNull()?.ownerId
                            ?.takeIf { it < 0 }
                            ?.let { onGroupClick(-it) }
                    },
                    // П-6a (#PROFILE-GAP-6a): лайк записи + «Действия» поста стены
                    // (pin/unpin/delete/edit). Свой профиль → владелец стены = p.id.
                    likesState = likeStates,
                    // П-8-REACT: реакции long-press'ом (пикер + optimistic-карта).
                    reactionsState = reactionStates,
                    onReaction = { clicked, reaction -> applyWallPostReaction(clicked, reaction) },
                    likePending = likeInFlight.containsKey(postKey),
                    onLikeToggle = { toggleWallPostLike(it) },
                    // #PROFILE-WALL-BOOKMARK: закладка поста стены профиля.
                    // Тот же паттерн, что в ленте (FeedScreen): faveAdd/faveRemove
                    // по "post" (ownerId, id). Пункт добавлен в «⋯»-меню.
                    onToggleBookmark = { p ->
                        scope.launch {
                            val ok = if (p.isFavoriteBool) {
                                app.apiClient.faveRemove("post", p.ownerId, p.id)
                            } else {
                                app.apiClient.faveAdd("post", p.ownerId, p.id)
                            }
                            AppLog.d("ProfileScreen", "wall post bookmark toggle: ok=$ok")
                        }
                    },
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
            // ALBUMS-IN-VIDEO: во вкладке «Видео» сначала показываем видеоальбомы
            // (video.getAlbums) — горизонтальный список карточек над «Все видео».
            // Тап по альбому → openedVideoAlbum (грузим видео через video.get с
            // album_id) и рендерим их в сетке + кнопку «Назад к альбомам».
            // Паттерн — PROFILE-ALBUMS (selectedContentTab == PROFILE_TAB_PHOTO).
            val openedVAlbum = openedVideoAlbum
            if (openedVAlbum != null) {
                // Открыли видеоальбом — видео из этого альбома + «Назад».
                item(key = "album_video_back") {
                    AlbumBackRow(title = openedVAlbum.title) {
                        openedVideoAlbum = null
                        albumVideos = emptyList()
                    }
                }
                if (albumVideosLoading && albumVideos.isEmpty()) {
                    item(key = "album_video_progress") { TabProgressRow() }
                } else if (albumVideosError != null && albumVideos.isEmpty()) {
                    item(key = "album_video_error") {
                        TabErrorRow(message = albumVideosError ?: "", onRetry = { albumVideosRetryTick++ })
                    }
                } else if (albumVideos.isEmpty()) {
                    item(key = "album_video_empty") {
                        TabEmptyRow("В альбоме нет видео")
                    }
                } else {
                    val albumVideoRows = albumVideos.chunked(2)
                    itemsIndexed(
                        albumVideoRows,
                        key = { rowIdx, _ -> "album_video_row_$rowIdx" },
                    ) { _, rowVideos ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowVideos.forEach { video ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ProfileVideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        modifier = Modifier.fillMaxWidth(),
                                        onLongClick = { showAlbumVideoMenuFor = video },
                                    )
                                    // VIDEO-ALBUMS-CRUD (B4): long-press на видео
                                    // внутри открытого альбома. Свой профиль →
                                    // canManageAlbums=true (все 3 пункта).
                                    VideoContextMenuDropdown(
                                        target = video,
                                        state = showAlbumVideoMenuFor,
                                        onDismiss = { showAlbumVideoMenuFor = null },
                                        showRemoveFromAlbum = true,
                                        showDeleteVideo = video.ownerId == currentUserId,
                                        showAddToSelf = video.ownerId != currentUserId,
                                        onRemoveFromAlbum = {
                                            showAlbumVideoMenuFor = null
                                            scope.launch {
                                                val album = openedVideoAlbum
                                                if (album == null) {
                                                    Toast.makeText(videoCrudContext, "Альбом не открыт", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                try {
                                                    val ok = app.apiClient.videoRemoveFromAlbum(
                                                        albumId = album.id,
                                                        video = video,
                                                        ownerId = currentUserId,
                                                    )
                                                    if (ok) {
                                                        albumVideos = albumVideos.filterNot { it.id == video.id && it.ownerId == video.ownerId }
                                                        Toast.makeText(videoCrudContext, "Убрано из альбома", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(videoCrudContext, "Не удалось убрать из альбома", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("ProfileScreen", "videoRemoveFromAlbum failed", e)
                                                    Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onDeleteVideo = {
                                            showAlbumVideoMenuFor = null
                                            deleteVideoTarget = video
                                        },
                                        onAddToSelf = {
                                            showAlbumVideoMenuFor = null
                                            scope.launch {
                                                try {
                                                    val ok = app.apiClient.videoAdd(
                                                        videoId = video.id,
                                                        ownerId = video.ownerId,
                                                        accessKey = video.accessKey,
                                                    )
                                                    val msg = if (ok) "Добавлено вам" else "Не удалось добавить"
                                                    Toast.makeText(videoCrudContext, msg, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    AppLog.e("ProfileScreen", "videoAdd (self) failed", e)
                                                    Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            repeat(2 - rowVideos.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                // Горизонтальный список видеоальбомов (если есть) над «Все видео».
                // VIDEO-ALBUMS-CRUD: даже если альбомов пока нет — рисуем заголовок
                // + «+ Создать альбом», чтобы пользователь мог создать первый.
                item(key = "profile_video_albums_header") {
                    Text(
                        text = "Видеоальбомы (${videoAlbums.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (videoAlbums.isNotEmpty()) {
                    item(key = "profile_video_albums_row") {
                        ProfileVideoAlbumsRow(
                            albums = videoAlbums,
                            onAlbumClick = { openedVideoAlbum = it },
                            onAlbumLongClick = { showAlbumMenuFor = it },
                            albumMenuState = showAlbumMenuFor,
                            onDismissAlbumMenu = { showAlbumMenuFor = null },
                            onRenameAlbum = { album ->
                                showAlbumMenuFor = null
                                renameAlbumTarget = album
                            },
                            onDeleteAlbum = { album ->
                                showAlbumMenuFor = null
                                deleteAlbumTarget = album
                            },
                        )
                    }
                }
                // VIDEO-ALBUMS-CRUD (B1): карточка «+ Создать альбом» под рядом
                // (свой профиль — всегда можно, даже если альбомов ещё нет).
                item(key = "profile_create_video_album_card") {
                    CreateVideoAlbumCard(
                        onClick = { showCreateAlbumDialog = true },
                        widthDp = 120.dp,
                    )
                }
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
                                Box(modifier = Modifier.weight(1f)) {
                                    ProfileVideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        modifier = Modifier.fillMaxWidth(),
                                        onLongClick = { showVideoMenuFor = video },
                                    )
                                    // VIDEO-ALBUMS-CRUD (B3): long-press на видео в
                                    // «Все видео». Свой профиль → оба пункта.
                                    VideoContextMenuDropdown(
                                        target = video,
                                        state = showVideoMenuFor,
                                        onDismiss = { showVideoMenuFor = null },
                                        showAddToAlbum = true,
                                        showAddToSelf = video.ownerId != currentUserId,
                                        onAddToAlbum = {
                                            showVideoMenuFor = null
                                            addToAlbumTarget = video
                                        },
                                        onAddToSelf = {
                                            showVideoMenuFor = null
                                            scope.launch {
                                                try {
                                                    val ok = app.apiClient.videoAdd(
                                                        videoId = video.id,
                                                        ownerId = video.ownerId,
                                                        accessKey = video.accessKey,
                                                    )
                                                    val msg = if (ok) "Добавлено вам" else "Не удалось добавить"
                                                    Toast.makeText(videoCrudContext, msg, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    AppLog.e("ProfileScreen", "videoAdd (self) failed", e)
                                                    Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    )
                                }
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
            }
        } else if (selectedContentTab == PROFILE_TAB_PHOTO) {
            // PROFILE-ALBUMS: во вкладке «Фото» сначала показываем альбомы
            // (photos.getAlbums) — горизонтальный список карточек над «Все фото».
            // Тап по альбому → openedAlbum (грузим фото через photos.get) и
            // рендерим их в сетке + кнопку «Назад к альбомам». Тап на «Все фото»
            // не нужен — сетка photosGetAll остаётся видимой ниже альбомов.
            // Паттерн — CommunityScreen.kt:1649 (вкладка «Альбомы»).
            val opened = openedAlbum
            if (opened != null) {
                // PROFILE-ALBUMS: albumUrls вычисляем ВНЕ item{} (remember
                // нельзя вызывать в LazyListScope — не @Composable).
                val albumUrls = albumPhotos.mapNotNull { it.largestUrl }
                // Открыли альбом — фото из этого альбома + «Назад».
                item(key = "album_back") {
                    AlbumBackRow(title = opened.title) { openedAlbum = null }
                }
                if (albumPhotosLoading && albumUrls.isEmpty()) {
                    item(key = "album_photo_progress") { TabProgressRow() }
                } else if (albumPhotosError != null && albumUrls.isEmpty()) {
                    item(key = "album_photo_error") {
                        TabErrorRow(
                            message = albumPhotosError ?: "",
                            onRetry = { albumPhotosRetryTick++ },
                        )
                    }
                } else if (albumUrls.isEmpty()) {
                    item(key = "album_photo_empty") {
                        TabEmptyRow("В альбоме нет фотографий")
                    }
                } else {
                    val albumRows = albumUrls.chunked(3)
                    itemsIndexed(
                        albumRows,
                        key = { rowIdx, _ -> "album_photo_row_$rowIdx" },
                    ) { rowIdx, rowUrls ->
                        PhotoGridRow(
                            urls = rowUrls,
                            startIndex = rowIdx * 3,
                            fullList = albumUrls,
                            onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                        )
                    }
                }
            } else {
                // Горизонтальный список альбомов (если есть) над «Все фото».
                //albumsError и albumsLoading показываем только если фото ещё не
                //пришли — иначе альбомы просто «накладываются» поверх фотогрид.
                if (albums.isNotEmpty()) {
                    item(key = "profile_albums_header") {
                        Text(
                            text = "Альбомы (${albums.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    item(key = "profile_albums_row") {
                        ProfileAlbumsRow(
                            albums = albums,
                            onAlbumClick = { openedAlbum = it },
                        )
                    }
                }
                // «Все фото» (photos.getAll) — заголовок + сетка (как раньше).
                item(key = "all_photos_header") {
                    Text(
                        text = "Все фото",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
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
        // PHOTO-SHARE-WIRE: wired onShare/onSendToChat (раньше были default {}).
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
            onShare = { url ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(send, "Поделиться"))
            },
            onSendToChat = { url -> shareToChatUrl = url },
        )
    }

    // PHOTO-SHARE-WIRE: ShareToChatSheet для «Отправить» из PhotoViewer.
    shareToChatUrl?.let { url ->
        ShareToChatSheet(
            sharedText = url,
            sharedStreamUri = null,
            sharedMimeType = null,
            onDismiss = { shareToChatUrl = null },
            onSuccess = { shareToChatUrl = null },
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

    // ══ VIDEO-ALBUMS-CRUD: диалоги и пикеры (B1/B2/B3/B4) ══
    // Свой профиль → canManageAlbums=true (создаём/переименовываем/удаляем
    // свои альбомы). Owner_id = profile.id (ПОЛОЖИТЕЛЬНЫЙ, без group_id).
    if (showCreateAlbumDialog) {
        CreateVideoAlbumDialog(
            onDismiss = { showCreateAlbumDialog = false },
            onConfirm = { title, privacy ->
                val ownerId = profile?.id
                showCreateAlbumDialog = false
                if (ownerId == null) {
                    Toast.makeText(videoCrudContext, "Профиль ещё загружается", Toast.LENGTH_SHORT).show()
                    return@onConfirm
                }
                scope.launch {
                    try {
                        val albumId = app.apiClient.videoCreateAlbum(
                            title = title,
                            privacy = privacy,
                            ownerId = ownerId,
                        )
                        if (albumId != null && albumId > 0) {
                            Toast.makeText(videoCrudContext, "Альбом создан", Toast.LENGTH_SHORT).show()
                            // REVIEWER-2: инвалидируем флаг + тик перезагрузки
                            // (не очищаем videoAlbums — новый альбом добавится после
                            // перезагрузки с сервера, без flicker «пусто»).
                            videoAlbumsLoaded = false
                            videoAlbumsRefreshTick++
                        } else {
                            val err = app.apiClient.lastApiError ?: "Не удалось создать альбом"
                            Toast.makeText(videoCrudContext, err, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("ProfileScreen", "videoCreateAlbum failed", e)
                        Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    renameAlbumTarget?.let { album ->
        RenameVideoAlbumDialog(
            initialTitle = album.title,
            onDismiss = { renameAlbumTarget = null },
            onConfirm = { newTitle ->
                val target = album
                val ownerId = profile?.id
                renameAlbumTarget = null
                if (ownerId == null) {
                    Toast.makeText(videoCrudContext, "Профиль ещё загружается", Toast.LENGTH_SHORT).show()
                    return@onConfirm
                }
                scope.launch {
                    try {
                        val ok = app.apiClient.videoEditAlbum(
                            albumId = target.id,
                            title = newTitle,
                            ownerId = ownerId,
                        )
                        if (ok) {
                            Toast.makeText(videoCrudContext, "Переименовано", Toast.LENGTH_SHORT).show()
                            videoAlbums = videoAlbums.map {
                                if (it.id == target.id && it.ownerId == target.ownerId) it.copy(title = newTitle)
                                else it
                            }
                            if (openedVideoAlbum?.id == target.id) {
                                openedVideoAlbum = openedVideoAlbum?.copy(title = newTitle)
                            }
                            // REVIEWER-2: оптимистичный .map{} выше остаётся видимым,
                            // перезагрузка с сервера идёт фоном через videoAlbumsRefreshTick.
                            videoAlbumsLoaded = false
                            videoAlbumsRefreshTick++
                        } else {
                            val err = app.apiClient.lastApiError ?: "Не удалось переименовать"
                            Toast.makeText(videoCrudContext, err, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("ProfileScreen", "videoEditAlbum failed", e)
                        Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    deleteAlbumTarget?.let { album ->
        DeleteVideoAlbumConfirmDialog(
            albumTitle = album.title.ifBlank { "Без названия" },
            onDismiss = { deleteAlbumTarget = null },
            onConfirm = {
                val target = album
                val ownerId = profile?.id
                deleteAlbumTarget = null
                if (ownerId == null) {
                    Toast.makeText(videoCrudContext, "Профиль ещё загружается", Toast.LENGTH_SHORT).show()
                    return@onConfirm
                }
                scope.launch {
                    try {
                        val ok = app.apiClient.videoDeleteAlbum(
                            albumId = target.id,
                            ownerId = ownerId,
                        )
                        if (ok) {
                            Toast.makeText(videoCrudContext, "Альбом удалён", Toast.LENGTH_SHORT).show()
                            if (openedVideoAlbum?.id == target.id) {
                                openedVideoAlbum = null
                                albumVideos = emptyList()
                            }
                            // REVIEWER-2: оптимистичное удаление из списка + тик
                            // перезагрузки (вместо emptyList(), которое очищало бы
                            // весь ряд до перезагрузки).
                            videoAlbums = videoAlbums.filterNot { it.id == target.id && it.ownerId == target.ownerId }
                            videoAlbumsLoaded = false
                            videoAlbumsRefreshTick++
                        } else {
                            val err = app.apiClient.lastApiError ?: "Не удалось удалить альбом"
                            Toast.makeText(videoCrudContext, err, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("ProfileScreen", "videoDeleteAlbum failed", e)
                        Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    addToAlbumTarget?.let { video ->
        AddVideoToAlbumDialog(
            albums = videoAlbums,
            onDismiss = { addToAlbumTarget = null },
            onPick = { album ->
                val target = video
                val ownerId = profile?.id
                addToAlbumTarget = null
                if (ownerId == null) {
                    Toast.makeText(videoCrudContext, "Профиль ещё загружается", Toast.LENGTH_SHORT).show()
                    return@onPick
                }
                scope.launch {
                    try {
                        val ok = app.apiClient.videoAddToAlbum(
                            albumId = album.id,
                            video = target,
                            ownerId = ownerId,
                        )
                        val msg = if (ok) "Добавлено в альбом «${album.title}»" else "Не удалось добавить"
                        Toast.makeText(videoCrudContext, msg, Toast.LENGTH_SHORT).show()
                        if (openedVideoAlbum?.id == album.id) {
                            albumVideos = albumVideos + target
                        }
                    } catch (e: Exception) {
                        AppLog.e("ProfileScreen", "videoAddToAlbum failed", e)
                        Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCreateAlbum = {
                addToAlbumTarget = null
                showCreateAlbumDialog = true
            },
        )
    }
    deleteVideoTarget?.let { video ->
        DeleteVideoConfirmDialog(
            videoTitle = video.title.ifBlank { "Без названия" },
            onDismiss = { deleteVideoTarget = null },
            onConfirm = {
                val target = video
                deleteVideoTarget = null
                scope.launch {
                    try {
                        val ok = app.apiClient.videoDelete(
                            videoId = target.id,
                            ownerId = target.ownerId,
                        )
                        if (ok) {
                            Toast.makeText(videoCrudContext, "Видео удалено", Toast.LENGTH_SHORT).show()
                            albumVideos = albumVideos.filterNot { it.id == target.id && it.ownerId == target.ownerId }
                            videos = videos.filterNot { it.id == target.id && it.ownerId == target.ownerId }
                        } else {
                            val err = app.apiClient.lastApiError ?: "Не удалось удалить видео"
                            Toast.makeText(videoCrudContext, err, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("ProfileScreen", "videoDelete failed", e)
                        Toast.makeText(videoCrudContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
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
    // POSTCARD-REFACTOR-PLAN: тап по имени/аватару автора поста. Клик по
    // группе/каналу (post.fromId<0) ведёт в сообщество (groupId — ПОЛОЖИТЕЛЬНЫЙ
    // id, как в Screen.Community.buildRoute); по пользователю (fromId>0) —
    // в чужой профиль (userId положительный, Screen.UserProfile.buildRoute);
    // автора нет (0) — в пост. Дефолты {} — легаси-вызовы не меняют поведение.
    onGroupClick: (groupId: Long) -> Unit = {},
    onUserClick: (userId: Long) -> Unit = {},
    // Группа-источник РЕПОСТА (copy_history[0] с ownerId<0). Резолв типа
    // «группа + аватар + клик в группу» делает вызывающий экран (у PostCard/
    // RepostBlock нет карты групп). Дефолты null/{} — старый вид заголовка
    // репоста («Запись клуба <id>»), легаси-вызовы совместимы.
    repostGroupName: String? = null,
    repostGroupPhoto: String? = null,
    onRepostGroupClick: (() -> Unit)? = null,
    // POSTCARD-REFACTOR-PLAN: дополнительные пункты «⋯»-меню (для канальных
    // постов — «Поделиться»/«Действия с сообщением»). Добавляются к
    // postMenuItems (после них). Дефолт emptyList — легаси-вызовы (Profile/
    // UserProfile) меню не расширяют.
    extraMenuItems: List<PostMenuItem> = emptyList(),
    // POSTCARD-REFACTOR-PLAN / #PROFILE-WALL-BOOKMARK: закладка поста стены.
    // Пункт «В закладки / Убрать из закладок» добавляется в «⋯»-меню ВСЕГДА
    // (не зависит от showActions/manage-прав), чтобы любой пост на стене
    // профиля можно было добавить в закладки. Дефолт {} — легаси-вызовы.
    onToggleBookmark: (Post) -> Unit = {},
) {
    // POSTCARD-REFACTOR-PLAN (этап 3c): WallPostCard — ОБЁРТКА-ДЕЛЕГАТ единого
    // PostCard (ui/components/PostCard.kt). Внешняя сигнатура сохранена 1:1 —
    // все 5 мест вызова (ProfileScreen/UserProfileScreen/ChatDetailScreen) не
    // меняются. До вызова PostCard собираем производные состояния: authorIsGroup,
    // оптимистичный лайк (likesOverride) и реакцию (reactionOverride), права-гейты
    // ⋮-меню и настройки отрисовки (по паритету со старым inline-кодом).

    // П-6a: пост от группы (< 0) — рисуем аватар/имя группы.
    val isGroupPost = post.fromId < 0
    // П-6a: права управляемой записи + видимость «⋯»-меню (посты владельца
    // стены ИЛИ серверные can_edit/can_delete; меню открывается и при showReport).
    val canManage = post.ownerId == wallOwnerId || post.canEditBool || post.canDeleteBool
    val menuVisible = (showActions && canManage) || showReport
    // П-6a: «Редактировать» — только текстовые записи (вложения/репост/
    // копирайт/подпись не покрыты wall.edit).
    val editCovered = post.attachments.isNullOrEmpty() &&
        post.copyHistory == null &&
        post.copyright == null &&
        post.signerId == null &&
        (post.postType == null || post.postType == "post")
    // П-6a/П-7-AB: гейты и порядок пунктов ⋮-меню — 1:1 со старым inline-меню.
    // PostCard сам рисует DropdownMenu из menuItems (не вложенный диалог).
    // #PROFILE-WALL-BOOKMARK: пункт «В закладки / Убрать из закладок» добавляется
    // ВСЕГДА (первым), независимо от showActions/manage-прав — любой пост стены
    // можно заложить (как в ленте FeedScreen).
    val postMenuItems = buildList {
        // Закладка поста — всегда (переключатель по post.isFavoriteBool).
        add(PostMenuItem(
            label = if (post.isFavoriteBool) "Убрать из закладок" else "В закладки",
            icon = if (post.isFavoriteBool) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            onClick = { onToggleBookmark(post) },
        ))
        if (menuVisible) {
            if (showActions && (post.canPinBool || post.ownerId == wallOwnerId)) {
                add(PostMenuItem(
                    label = if (post.isPinnedBool) "Открепить" else "Закрепить",
                    icon = Icons.Outlined.PushPin,
                    onClick = { onPinToggle(post) },
                ))
            }
            if (showActions && editCovered && (post.canEditBool || post.ownerId == wallOwnerId)) {
                add(PostMenuItem(
                    label = "Редактировать",
                    icon = Icons.Filled.Edit,
                    onClick = { onEditRequest(post) },
                ))
            }
            if (showActions && canManage) {
                add(PostMenuItem(
                    label = "Удалить",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    onClick = { onDeleteRequest(post) },
                ))
            }
            if (showReport) {
                add(PostMenuItem(
                    label = "Пожаловаться",
                    icon = Icons.Outlined.Flag,
                    onClick = { onReportSpam(post) },
                ))
            }
        }
    }
    // П-6a/П-8-REACT: оптимистичные состояния экрана поверх серверных post.*.
    val optimisticLike = likesState["${post.ownerId}_${post.id}"]
    val optimisticReaction = reactionsState["${post.ownerId}_${post.id}"]
    // Просмотры: скрываем нули (паритет со старым ViewBlock и Community).
    val showViewsWrapped = (post.views?.count ?: 0) > 0

    PostCard(
        post = post,
        authorName = authorName,
        authorPhoto = authorPhoto,
        authorIsGroup = isGroupPost,
        signerName = null, // ProfileScreen не имеет extended profiles map для signerId lookup
        showFullDate = false,
        // Закладок в WallPostCard не было — иконку и пункт меню не добавляем.
        isBookmarked = null,
        onToggleBookmark = null,
        verified = false,
        // Тап по имени/аватару автора: группа/канал (fromId<0) → сообщество
        // (Screen.Community.buildRoute по положительному id), пользователь
        // (fromId>0) → чужой профиль (Screen.UserProfile.buildRoute), иначе
        // (0 — автора нет) → открыть пост.
        onAuthorClick = {
            if (post.fromId < 0) {
                onGroupClick(-post.fromId)
            } else if (post.fromId > 0) {
                onUserClick(post.fromId)
            } else {
                onPostClick(post)
            }
        },
        // AUTHOR-STRIP: полоска «Перейти к посту» — клик открывает стену, где
        // ЛЕЖИТ пост (по post.ownerId, НЕ fromId). user → UserProfileScreen,
        // group → CommunityScreen. onUserClick/onGroupClick уже проброшены в
        // WallPostCard вызывающими экранами (Profile/UserProfile/ChatDetail),
        // поэтому ВСЕ 5 callsites WallPostCard получают полоску автоматически.
        onPostContextClick = {
            if (post.ownerId > 0) {
                onUserClick(post.ownerId)
            } else {
                onGroupClick(-post.ownerId)
            }
        },
        onPostClick = onPostClick,
        onVideoClick = onVideoClick,
        onPhotoClick = onPhotoClick,
        onLikeToggle = onLikeToggle,
        onCommentClick = onCommentClick,
        onRepostClick = onRepostClick,
        onReaction = onReaction,
        likesOverride = optimisticLike,
        reactionOverride = optimisticReaction,
        // POSTCARD-REFACTOR-PLAN: к базовым пунктам («⋯»-меню профиля) добавляем
        // extraMenuItems (канальные «Поделиться»/«Действия с сообщением»).
        // Конкатенация корректна и при пустом postMenuItems.
        menuItems = postMenuItems + extraMenuItems,
        // Закреп показываем (был в профиле), «Реклама» — нет (в профиле не было).
        showPinnedBadge = true,
        showAdBadge = false,
        showSubscribeButton = false,
        textMaxLines = 10,
        carouselEnabled = carouselEnabled,
        showViews = showViewsWrapped,
        myUserId = wallOwnerId,
        // Группа-источник репоста → PostCard → RepostBlock (заголовок+клик).
        repostGroupName = repostGroupName,
        repostGroupPhoto = repostGroupPhoto,
        onRepostGroupClick = onRepostGroupClick,
    )
}

// POSTCARD-REFACTOR-PLAN (этап 3c): WallPostCard теперь — обёртка-делегат
// единого PostCard (ui/components/PostCard.kt); вложения/репост/счётчики
// рендерит общий компонент.

// ═══════════════════════════════════════════════════════════════════════════
// П-1 (#PROFILE-SNAP): вкладки контента и секции своего профиля.
// ═══════════════════════════════════════════════════════════════════════════

/** Единый ряд чипов (вкладки контента + подвкладки стены). */
@Composable
internal fun ProfileChipsRow(
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
internal fun TabProgressRow() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}

@Composable
internal fun TabErrorRow(message: String, onRetry: () -> Unit) {
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
internal fun TabEmptyRow(message: String) {
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
internal fun ProfileTrackRow(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
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
internal fun TabShowMoreRow(loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
 *  ширина задаёт вызывающий через [modifier] — weight(1f) в строке-чанке).
 *  VIDEO-ALBUMS-CRUD: [onLongClick] — long-press → контекстное меню
 *  (добавить в альбом / убрать / удалить). null = обычный клик. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
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
internal fun PhotoGridRow(
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
internal fun extractPhotoAllUrl(photo: JsonObject): String? {
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
    // P0.45-TRACER B8: "page" — fave.get(item_type=page) MIX users+groups.
    "page" -> "Страница"
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
    // P0.45-TRACER B7: "page" — MIX users+groups, открывается если есть user ИЛИ group.
    "user", "page" -> bookmark.user != null || bookmark.group != null
    "group" -> bookmark.group != null
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

// ════════════════════════════════════════════════════════════════════════════
// PROFILE-ALBUMS: альбомы во вкладке «Фото» (photos.getAlbums → photos.get).
// Паттерн — CommunityScreen.kt:2313 (CommunityAlbumRow), но здесь альбомы
// рендерятся ГОРИЗОНТАЛЬНЫМ списком карточек над «Все фото», а тап ведёт
// НЕ прямо в PhotoViewer, а в подсекцию сетки фото альбома (см. openedAlbum).
// ════════════════════════════════════════════════════════════════════════════

/**
 * PROFILE-ALBUMS: горизонтальный список альбомов во вкладке «Фото» профиля.
 * LazyRow карточек 120dp (обложка 120×120 + название + «N фото»). Тап по
 * карточке → [onAlbumClick] (открывает подсекцию фото альбома через
 * [openedAlbum]). Пустые альбомы (size==0) тоже рисуются — VK может отдавать
 * системные альбомы «Сохранённые»/«С стены»/«Документы» без счётчика.
 *
 * internal — используется и в [UserProfileScreen] (чужой профиль, та же вкладка «Фото»).
 *
 * @param albums      Список альбомов (photos.getAlbums)
 * @param onAlbumClick Тап по карточке альбома — открыть фото альбома
 */
@Composable
internal fun ProfileAlbumsRow(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(albums, key = { "profile_album_${it.ownerId}_${it.id}" }) { album ->
            ProfileAlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

/**
 * ALBUMS-IN-VIDEO: горизонтальный список видеоальбомов во вкладке «Видео»
 * (свой/чужой профиль). Паттерн — [ProfileAlbumsRow] для фотоальбомов;
 * зеркало на уровне ProfileScreen (используется и в UserProfileScreen).
 *
 * internal — переиспользуется в [UserProfileScreen] (чужой профиль, та же
 * вкладка «Видео»).
 *
 * @param albums       Список видеоальбомов (video.getAlbums)
 * @param onAlbumClick Тап по карточке → открыть видео альбома
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileVideoAlbumsRow(
    albums: List<VideoAlbum>,
    onAlbumClick: (VideoAlbum) -> Unit,
    // VIDEO-ALBUMS-CRUD: long-press → контекстное меню (переименовать/удалить).
    onAlbumLongClick: ((VideoAlbum) -> Unit)? = null,
    // Текущий активный альбом (null = меню скрыто) — рендерим dropdown внутри
    // LazyRow, anchored к карточке.
    albumMenuState: VideoAlbum? = null,
    onDismissAlbumMenu: () -> Unit = {},
    onRenameAlbum: ((VideoAlbum) -> Unit)? = null,
    onDeleteAlbum: ((VideoAlbum) -> Unit)? = null,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(albums, key = { "pvalbum_${it.ownerId}_${it.id}" }) { album ->
            Box {
                ProfileVideoAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    onLongClick = onAlbumLongClick?.let { cb -> { cb(album) } },
                )
                if (onAlbumLongClick != null && onRenameAlbum != null && onDeleteAlbum != null) {
                    AlbumContextMenuDropdown(
                        target = album,
                        state = albumMenuState,
                        onDismiss = onDismissAlbumMenu,
                        onRename = { onRenameAlbum(album) },
                        onDelete = { onDeleteAlbum(album) },
                    )
                }
            }
        }
    }
}

/**
 * ALBUMS-IN-VIDEO: карточка видеоальбома в горизонтальной LazyRow. Card-стиль —
 * обложка 1:1 (coverUrl из video.getAlbums extended=1: image[] maxBy area,
 * fallback photo_320 / photo_160) или серый placeholder с иконкой VideoLibrary;
 * под ней название и счётчик видео. Паттерн — [ProfileAlbumCard] (фотоальбом).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileVideoAlbumCard(
    album: VideoAlbum,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.width(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val cover = album.coverUrl
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = album.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = album.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.count} видео",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * PROFILE-ALBUMS: карточка альбома в горизонтальной LazyRow. Card-стиль —
 * обложка 120×120 (thumbSrc или серый placeholder с иконкой Image), под ней
 * название и счётчик фото. Паттерн — CommunityAlbumRow (CommunityScreen.kt:2313),
 * но вертикальная компоновка для горизонтального скролла.
 */
@Composable
internal fun ProfileAlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val coverUrl = album.thumbSrc
            if (coverUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = album.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.size} фото",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}

/**
 * PROFILE-ALBUMS: строка «← Назад к альбомам» над сеткой фото открытого
 * альбома. Тап → [onBack] (сбрасывает [openedAlbum] в null, возвращаясь к
 * обзору альбомов + «Все фото»). internal — используется и в [UserProfileScreen].
 */
@Composable
internal fun AlbumBackRow(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBack() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад к альбомам",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

