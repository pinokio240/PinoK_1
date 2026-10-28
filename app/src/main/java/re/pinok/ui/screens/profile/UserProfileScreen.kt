// File: ui/screens/profile/UserProfileScreen.kt
package re.pinok.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
// PHOTO-SHARE-WIRE: Intent.ACTION_SEND для «Поделиться» из PhotoViewer.
import android.content.Intent
import android.widget.Toast
// PROFILE-ALBUMS: обложка альбома + кликабельная карточка.
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
// PROFILE-ALBUMS: clip-скругление углов обложки альбома.
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Call
// П-7-AB: Flag — пункт «Пожаловаться» в «⋯»-меню записи чужой стены.
import androidx.compose.material.icons.outlined.Flag
// PROFILE-ALBUMS: иконка-плейсхолдер обложки альбома (как CommunityAlbumRow).
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Album
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.Post
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.data.model.VideoAlbum
import re.pinok.media.PlayerConnection
import re.pinok.ui.components.PhotoViewer
// PHOTO-SHARE-WIRE: ShareToChatSheet для пункта «Отправить» в PhotoViewer.
import re.pinok.ui.components.ShareToChatSheet
import re.pinok.ui.components.RepostGroupCache
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.components.ShareSheet
import re.pinok.util.AppLog

/**
 * Sprint 1, P0-2 (#74): Экран чужого профиля.
 *
 * Открывается тапом по автору поста в ленте (если `fromId > 0`), по другу в
 * списке друзей, по собеседнику в чате. Показывает:
 *  — Шапку профиля (аватар, имя, статус, online-индикатор) — [ProfileHeader]
 *  — Счётчики (друзья, подписчики, фото, видео) — [CountersRow]
 *  — Действия: «Написать» (открывает ChatDetailScreen) и «Добавить в друзья»
 *    (вызывает `friends.add` с Toast-фидбеком)
 *  — Стену пользователя (`wall.get(ownerId=userId, count=20)`) — [WallPostCard]
 *
 * Источник данных: `users.get(user_ids=userId, fields=photo_100,photo_200,
 * photo_400,online,last_seen,status,verified,counters,bdate,city,country,
 * followers_count,common_count)` через [VKApiClient.usersGetFull].
 *
 * В отличие от [ProfileScreen] (свой профиль), здесь НЕТ кнопки «Выйти» и
 * настроек аккаунта — только просмотр + 2 действия.
 *
 * Переиспользует public composables из ProfileScreen.kt: [ProfileHeader],
 * [CountersRow], [WallPostCard] — чтобы не дублировать UI-логику.
 *
 * Этап П-2 (PROFILE-P2-3b): поверх «Написать»/«В друзья» добавлены:
 *  — «Подписаться»/«Отписаться» (usersSubscribe/usersUnsubscribe; начальное
 *    состояние из UserProfile.isSubscribed; для друга не показывается —
 *    не дублирует «В друзья»);
 *  — «Позвонить» в TopAppBar — nullable [onCallClick] от хоста (CallStarter-
 *    паттерн FriendsScreen: null → кнопка не рендерится);
 *  — «⋯»-меню TopAppBar: пожаловаться (users.report), скопировать ссылку
 *    (domain/screen_name), закладки (fave.addUser/removeUser, состояние из
 *    is_favorite), скрыть/вернуть в ленту (newsfeed.addBan/deleteBan —
 *    состояние локальное: is_hidden_from_feed модель не парсит);
 *  — секция «Подарки» над лентой (gifts.get, count=9).
 *
 *  П-7-AB (остаток правой колонки веба — профиль.этап-П5.решение.md §3):
 *  — чип «Подписчики» в [CountersRow] кликабелен → список подписчиков
 *    СОБЕСЕДНИКА (FollowersSubscriptionsScreen, mode=followers, userId=p.id);
 *  — строка «Подписки» ([SubscriptionsEntryRow]) → подписки собеседника
 *    (mode=subscriptions); счётчик — параллельный добор
 *    users.getSubscriptions (как gifts); чипа «Подписки» в CountersRow нет —
 *    поля subscriptions в модели UserProfile.Counters нет (модель править
 *    запрещено), строка — как в своём профиле;
 *  — ЧЕСТНОЕ ОТКЛОНЕНИЕ: чип «Друзья» на чужом профиле НЕ кликабелен —
 *    существующий Screen.Friends глобальный (без userId) и показывает СВОИХ
 *    друзей: переход вёл бы к подмене списка собеседника своим; экрана
 *    «друзья пользователя X» в репо нет;
 *  — «Пожаловаться» на записи чужой стены ([WallPostCard] showReport=!isSelf):
 *    wall.markAsSpam с AlertDialog-подтверждением; manage-пункты П-6а по-прежнему
 *    не активируются на чужой стене (showActions=false — меню только с жалобой).
 *
 *  #OPVK-EXTRACT (Task 3-c, идея OpenVK AboutProfileLayout — AGPL, только
 *  семантика «собирать label/value ТОЛЬКО из непустых значений», не код):
 *  секция «Информация» ([ProfileInfoSection]) над «Подарками» — карточка со
 *  строками «О себе»/«Деятельность»/«Интересы»/«Любимая музыка»/
 *  «Любимые фильмы»/«Любимые книги» ([profileInfoRows]). Никаких новых
 *  VKA-вызовов: поля уже приходят в usersGetFull→usersGetFullExtended
 *  (fields профиля) и парсятся в UserProfile. bdate/city НЕ дублируются —
 *  их уже рисует [ProfileHeader] («День рождения»/«Город»); строка
 *  «Любимые цитаты» НЕ рисуется — поле quotes запрашивается в fields, но в
 *  модель UserProfile не парсится (модель вне зоны правок) — no-stub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onMessageClick: (peerId: Long, title: String, photo: String?) -> Unit = { _, _, _ -> },
    onPostClick: (Post) -> Unit = {},
    // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
    // Этап П-2: «Позвонить» — #ARCH-CONTAINERS (Этап 1.4): хост передаёт
    // колбэк ТОЛЬКО если в реестре есть CallStarter (SovaNavHost.callClick);
    // null → кнопка НЕ рендерится (тот же контракт, что FriendsScreen/FeedScreen).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
    // П-7-AB: тапы правой колонки веба с userId собеседника: чип «Подписчики»
    // → список подписчиков; строка «Подписки» → подписки. «Друзья» не проводим
    // (глобальный Screen.Friends показывает СВОИХ друзей — отклонение в KDoc).
    onFollowersClick: (Long) -> Unit = {},
    onSubscriptionsClick: (Long) -> Unit = {},
    // #FRIEND-CONTENT-TABS: чип «Друзья» → список друзей собеседника
    // (Screen.FollowList MODE_FRIENDS; на своём профиле — Screen.Friends).
    onFriendsClick: (Long) -> Unit = {},
    // POSTCARD-REFACTOR-PLAN: тап по имени/аватару автора в шапке поста чужой
    // стены. Группа/канал (fromId<0) → сообщество; пользователь (fromId>0) →
    // его профиль (на чужой стене чужие посты-репосты от групп/других людей).
    // Дефолты {} — легаси-вызовы не меняют поведение.
    onGroupClick: (Long) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // #POST-CAROUSEL-EVERYWHERE (волна 22): ОДИН флаг карусели на все поверхности —
    // follow-up 22-B (UserProfileScreen был вне зоны агента: дефолт true без флага,
    // настройка «Карусель фото в постах» здесь бы не работала).
    val prefsSnap by app.prefs.data.collectAsState(initial = null)
    // NULL-ЯВНО: Snapshot-initial до первого эмита; дефолт true = SovaPrefs default.
    val carouselEnabled: Boolean = prefsSnap?.feedCarouselEnabled ?: true
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var friendActionInProgress by remember { mutableStateOf(false) }
    var isFriend by remember { mutableStateOf(false) }
    // Fix #86: pull-to-refresh + infinite scroll стены пользователя.
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // PHOTO-SHARE-WIRE: URL для ShareToChatSheet — открывается из пункта «Отправить»
    // в «⋮»-меню PhotoViewer. null → sheet закрыт.
    var shareToChatUrl by remember { mutableStateOf<String?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // Группы-источники РЕПОСТОВ чужой стены (copy_history[0] с ownerId<0) →
    // имя/аватар. Резолв лениво в LaunchedEffect каждого поста (см. items ниже):
    // на репост чужой стены можно нарваться с любых групп, кэш копит их по ходу.
    val repostGroups = remember { RepostGroupCache() }

    // Этап П-2 (PROFILE-P2-3b): состояние действий чужого профиля.
    var profileMenuExpanded by remember { mutableStateOf(false) }
    // Начальные значения выставляются из профиля (is_subscribed / is_favorite).
    var subscribed by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    // is_hidden_from_feed модель UserProfile не парсит — состояние локальное
    // (сессионное), сбрасывается при перезагрузке экрана.
    var hiddenFromFeed by remember { mutableStateOf(false) }
    var subscribeInProgress by remember { mutableStateOf(false) }
    var gifts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // П-7-AB: счётчик «Подписок» собеседника (users.getSubscriptions →
    // followSubscriptionsTotal, параллельный добор как gifts); не распознан →
    // строка «Подписки» без числа (счётчик не имитируется).
    var subscriptionsCount by remember { mutableStateOf<Int?>(null) }
    // П-7-AB: «Пожаловаться» на запись чужой стены (wall.markAsSpam).
    val reportingPost = remember { mutableStateOf<Post?>(null) }
    var reportInFlight by remember { mutableStateOf(false) }

    // #FRIEND-CONTENT-TABS (2026-10-17): контентные вкладки чужих разделов.
    // Паттерн — ProfileScreen (свой профиль) W33-c: «Стена/Музыка/Видео/Фото»,
    // чипы счётчиков переключают selectedContentTab, ленивая загрузка при
    // первом выборе вкладки, статусы loading/error/empty через Tab*Row.
    // Раздел «Друзья» НЕ вкладка — чип ведёт на отдельный экран
    // (Screen.FollowList, MODE_FRIENDS, см. FollowersSubscriptionsScreen).
    var selectedTab by remember { mutableStateOf(PROFILE_TAB_WALL) }
    var tabRetryTick by remember { mutableStateOf(0) }
    // Музыка (audio.getWithCount → total + tracks).
    var musicTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicError by remember { mutableStateOf<String?>(null) }
    var musicLoaded by remember { mutableStateOf(false) }
    // Видео (video.get).
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var videoLoading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var videoLoaded by remember { mutableStateOf(false) }
    // Фото (photos.getAll → URLs).
    var photoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoLoading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var photoLoaded by remember { mutableStateOf(false) }
    // PROFILE-ALBUMS: альбомы во вкладке «Фото» (photos.getAlbums) —
    // горизонтальный список карточек над «Все фото». openedAlbum != null →
    // пользователь тапнул по альбому, рендерим фото этого альбома (photos.get)
    // в сетке + кнопку «Назад к альбомам». Паттерн — ProfileScreen.kt:301.
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
    // photos.get при той же ссылке на openedAlbum (паттерн tabRetryTick).
    var albumPhotosRetryTick by remember { mutableStateOf(0) }
    // ALBUMS-IN-VIDEO: state для видеоальбомов внутри вкладки «Видео»
    // (паттерн PROFILE-ALBUMS для фотоальбомов → видеоальбомы). Грузится
    // video.getAlbums(ownerId=userId — ПОЛОЖИТЕЛЬНЫЙ для чужого профиля).
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

    // Свой профиль? Если да — не показываем «Добавить в друзья».
    val currentUserId = app.exchangeAuthRepository.userId()
    val isSelf = userId == currentUserId

    LaunchedEffect(userId) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val p = app.apiClient.usersGetFull(userId)
                profile = p
                if (p != null) {
                    // #FRIEND-COLOR: инициализируем isFriend из profile.friendStatus.
                    // VK friend_status: 0=не друг, 1=заявка отправлена мной,
                    // 2=входящая заявка, 3=друг (взаимная дружба).
                    // Кнопка "Удалить" показывается только если status==3.
                    // Раньше isFriend всегда=false при загрузке — кнопка показывала
                    // "В друзья" даже для действительных друзей.
                    isFriend = (p.friendStatus == 3)
                    // Этап П-2: начальные состояния подписки/закладок из users.get
                    // (is_subscribed / is_favorite парсятся в UserProfile с П-0).
                    subscribed = (p.isSubscribed == 1)
                    isBookmarked = (p.isFavorite == 1)
                    val wall = app.apiClient.wallGet(ownerId = userId, count = 20)
                    posts = wall
                        .filter { it.id > 0 && it.ownerId != 0L }
                        .distinctBy { "${it.ownerId}_${it.id}" }
                    endReached = wall.size < 20
                    AppLog.i("UserProfileScreen", "Loaded profile uid=$userId + ${wall.size} posts")
                } else {
                    errorText = app.apiClient.lastApiError ?: "Профиль не найден"
                }
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "Failed to load profile uid=$userId", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Этап П-2: подарки чужого профиля (gifts.get, count=9 — инвентарь
    // §1.1.6 «Подарки (N)»). Для своего профиля не грузим; ошибка/пусто →
    // секции просто нет (no-stub).
    LaunchedEffect(userId, isSelf) {
        if (isSelf) return@LaunchedEffect
        try {
            gifts = app.apiClient.giftsGet(userId, count = 9)
        } catch (e: Exception) {
            AppLog.w("UserProfileScreen", "giftsGet failed: ${e.message}")
        }
    }

    // П-7-AB: счётчик «Подписок» собеседника (для строки «Подписки» —
    // параллельный добор, как gifts). Для своего профиля не грузим — там
    // свой счётчик считает ProfileScreen. Ошибка → строка без числа.
    LaunchedEffect(userId, isSelf) {
        if (isSelf) return@LaunchedEffect
        try {
            subscriptionsCount = followSubscriptionsTotal(
                app.apiClient.usersGetSubscriptions(userId = userId, count = 1),
            )
        } catch (e: Exception) {
            AppLog.w("UserProfileScreen", "subscriptions count failed: ${e.message}")
        }
    }

    // #FRIEND-CONTENT-TABS: ленивая загрузка разделов чужого пользователя
    // при первом выборе вкладки (паттерн ProfileScreen W33-c). Каждая вкладка
    // грузится ОДИН раз (loaded-флаг); «Повторить» сбрасывает ошибку через
    // tabRetryTick. Недоступность раздела (error 15 / total=-1) → честная
    // ошибка с текстом lastApiErrorHuman, а не молчаливая пустота.
    LaunchedEffect(selectedTab, tabRetryTick) {
        if (isSelf) return@LaunchedEffect
        when (selectedTab) {
            PROFILE_TAB_MUSIC -> if (!musicLoaded && !musicLoading) {
                musicLoading = true
                musicError = null
                try {
                    val (total, page) = app.apiClient.audioGetWithCount(50, 0, userId)
                    if (total < 0 && page.isEmpty()) {
                        musicError = "Не удалось загрузить музыку: " +
                            (app.apiClient.lastApiErrorHuman() ?: "раздел недоступен")
                        AppLog.w("UserProfileScreen", "Music tab: silent fail for uid=$userId")
                    } else {
                        musicTracks = page.filter { it.id > 0L && !it.url.isNullOrBlank() }
                        musicLoaded = true
                    }
                } catch (e: Exception) {
                    musicError = "Ошибка: ${e.message}"
                } finally {
                    musicLoading = false
                }
            }
            PROFILE_TAB_VIDEO -> if (!videoLoaded && !videoLoading) {
                videoLoading = true
                videoError = null
                // ALBUMS-IN-VIDEO: параллельно с «всеми видео» грузим видеоальбомы
                // (video.getAlbums) — для горизонтального списка над «Все видео»
                // (паттерн PROFILE-ALBUMS для фото → видеоальбомы).
                if (!videoAlbumsLoaded && !videoAlbumsLoading) {
                    launch {
                        videoAlbumsLoading = true
                        videoAlbumsError = null
                        try {
                            val (total, list) = app.apiClient.videoGetAlbums(ownerId = userId, count = 100)
                            videoAlbums = list
                            videoAlbumsLoaded = true
                            AppLog.i("UserProfileScreen", "Video albums loaded: ${videoAlbums.size} for uid=$userId (total=$total)")
                        } catch (e: Exception) {
                            AppLog.e("UserProfileScreen", "Video albums load failed", e)
                            videoAlbumsError = "Ошибка: ${e.message}"
                        } finally {
                            videoAlbumsLoading = false
                        }
                    }
                }
                try {
                    videos = app.apiClient.videoGet(ownerId = userId, count = 30).filter { it.id > 0L }
                    videoLoaded = true
                } catch (e: Exception) {
                    videoError = "Ошибка: ${e.message}"
                } finally {
                    videoLoading = false
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
                            albums = app.apiClient.photosGetAlbums(ownerId = userId)
                            albumsLoaded = true
                            AppLog.i("UserProfileScreen", "Albums loaded: ${albums.size} for uid=$userId")
                        } catch (e: Exception) {
                            AppLog.e("UserProfileScreen", "Albums load failed", e)
                            albumsError = "Ошибка: ${e.message}"
                        } finally {
                            albumsLoading = false
                        }
                    }
                }
                try {
                    val photos = app.apiClient.photosGetAll(ownerId = userId, count = 30)
                    photoUrls = photos.mapNotNull { extractPhotoAllUrl(it) }
                    photoLoaded = true
                } catch (e: Exception) {
                    photoError = "Ошибка: ${e.message}"
                } finally {
                    photoLoading = false
                }
            }
        }
    }

    // PROFILE-ALBUMS: открыли альбом — грузим его фото (photos.get,
    // album_id = album.id, owner_id = userId). albumPhotosRetryTick — кнопка
    // «Повторить» в TabErrorRow (паттерн tabRetryTick). Паттерн — ProfileScreen.kt:658.
    LaunchedEffect(openedAlbum, albumPhotosRetryTick) {
        val album = openedAlbum ?: return@LaunchedEffect
        albumPhotosLoading = true
        albumPhotosError = null
        try {
            albumPhotos = app.apiClient.photosGet(
                ownerId = userId,
                albumId = album.id.toString(),
                count = 200,
                offset = 0,
            )
            AppLog.i("UserProfileScreen", "Album photos loaded: ${albumPhotos.size} for «${album.title}»")
        } catch (e: Exception) {
            AppLog.e("UserProfileScreen", "Album photos load failed", e)
            albumPhotosError = "Ошибка: ${e.message}"
        } finally {
            albumPhotosLoading = false
        }
    }

    // ALBUMS-IN-VIDEO: открыли видеоальбом — грузим его видео (video.get с
    // album_id, owner_id = userId). Паттерн — LaunchedEffect(openedAlbum) выше.
    LaunchedEffect(openedVideoAlbum) {
        val album = openedVideoAlbum ?: return@LaunchedEffect
        albumVideosLoading = true
        albumVideosError = null
        try {
            albumVideos = app.apiClient.videoGet(ownerId = userId, count = 50, albumId = album.id)
            AppLog.i("UserProfileScreen", "Album videos loaded: ${albumVideos.size} for «${album.title}»")
        } catch (e: Exception) {
            AppLog.e("UserProfileScreen", "Album videos load failed", e)
            albumVideosError = "Ошибка: ${e.message}"
        } finally {
            albumVideosLoading = false
        }
    }

    // Fix #86: pull-to-refresh стены + профиля.
    fun refreshWall() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                val fresh = app.apiClient.usersGetFull(userId)
                if (fresh != null) profile = fresh
                // Этап П-2: освежаем состояния подписки/закладок из свежего профиля.
                subscribed = (fresh?.isSubscribed == 1)
                isBookmarked = (fresh?.isFavorite == 1)
                val wall = app.apiClient.wallGet(ownerId = userId, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                endReached = wall.size < 20
                AppLog.i("UserProfileScreen", "refreshed: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "refreshWall failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #86: infinite scroll стены.
    fun loadMoreWall() {
        if (loadingMore || endReached || posts.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val wall = app.apiClient.wallGet(ownerId = userId, count = 20, offset = posts.size)
                val newPosts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .filter { np -> posts.none { it.ownerId == np.ownerId && it.id == np.id } }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                if (newPosts.isEmpty()) {
                    endReached = true
                } else {
                    posts = (posts + newPosts).distinctBy { "${it.ownerId}_${it.id}" }
                    if (wall.size < 20) endReached = true
                }
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "loadMoreWall failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #86: триггер пагинации.
    LaunchedEffect(listState, posts.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMoreWall() }
    }

    // П-7-AB: жалоба на запись чужой стены (wall.markAsSpam) после подтверждения
    // в AlertDialog. Успех → тост «Жалоба отправлена»; ошибка → тост lastApiError.
    // Во время полёта кнопки диалога disabled, повторный вызов игнорируется.
    fun reportWallPostConfirmed(target: Post) {
        if (reportInFlight) return
        reportInFlight = true
        scope.launch {
            val ok = try {
                app.apiClient.wallMarkAsSpam(ownerId = target.ownerId, postId = target.id)
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "wallMarkAsSpam failed", e)
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

    if (loading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Профиль", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val p = profile
    if (p == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Профиль", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorText ?: "Профиль не загружен",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        return
    }

    // #OPVK-EXTRACT: строки «Информация» — только непустые значения; пусто →
    // секция не рисуется вовсе (no-stub, семантика AboutProfileLayout OpenVK).
    val infoRows = profileInfoRows(p)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = p.fullName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Этап П-2: «Позвонить» — только при живом CallStarter
                    // (иконка как в FriendsScreen:457; null → не рендерится).
                    if (!isSelf && onCallClick != null) {
                        IconButton(
                            onClick = { onCallClick(p.id, p.fullName, p.photo200 ?: p.photo100) },
                        ) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Позвонить",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (!isSelf) {
                        Box {
                            IconButton(onClick = { profileMenuExpanded = true }) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё")
                            }
                            DropdownMenu(
                                expanded = profileMenuExpanded,
                                onDismissRequest = { profileMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Пожаловаться") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                // VKA usersReport KDoc: допустимые type —
                                                // porn|spam|fraud|insult|... («user» в
                                                // списке нет) — шлём spam как общий тип.
                                                val ok = app.apiClient.usersReport(p.id, "spam")
                                                Toast.makeText(
                                                    context,
                                                    if (ok) "Жалоба отправлена"
                                                    else "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "usersReport failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Скопировать ссылку") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        // Fix #193-паттерн: платформенный
                                        // ClipboardManager (LocalClipboardManager
                                        // deprecated в Compose 1.8+).
                                        val url = "https://vk.com/${p.domain ?: p.screenName ?: "id${p.id}"}"
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("vk_profile_link", url))
                                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Убрать из закладок" else "Добавить в закладки") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val ok =
                                                    if (isBookmarked) app.apiClient.faveRemoveUser(p.id)
                                                    else app.apiClient.faveAddUser(p.id)
                                                if (ok) {
                                                    isBookmarked = !isBookmarked
                                                    Toast.makeText(
                                                        context,
                                                        if (isBookmarked) "Добавлено в закладки" else "Убрано из закладок",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "fave user action failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (hiddenFromFeed) "Вернуть в ленту" else "Скрыть из ленты") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val ok =
                                                    if (hiddenFromFeed) app.apiClient.newsfeedDeleteBan(userIds = listOf(p.id))
                                                    else app.apiClient.newsfeedAddBan(userIds = listOf(p.id))
                                                if (ok) {
                                                    hiddenFromFeed = !hiddenFromFeed
                                                    Toast.makeText(
                                                        context,
                                                        if (hiddenFromFeed) "Записи скрыты из ленты" else "Записи вернутся в ленту",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "newsfeed ban action failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх»
        // над лентой стены чужого профиля (пагинация loadMoreWall).
        Box(modifier = Modifier.fillMaxSize()) {
        // #FRIEND-CONTENT-TABS: состояние плеера выносится из LazyColumn-scope
        // (collectAsState в LazyListScope-items — НЕ composable-контекст, ошибка
        // компиляции). Один observable на весь экран, как в ProfileScreen.
        val friendPlayerState = PlayerConnection.playerState.collectAsState().value
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshWall() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { ProfileHeader(profile = p) }
            item {
                CountersRow(
                    profile = p,
                    // П-7-AB: тап по чипу «Подписчики» → подписчики СОБЕСЕДНИКА.
                    onFollowersClick = { onFollowersClick(p.id) },
                    // #FRIEND-CONTENT-TABS: чипы счётчиков → переключение вкладки
                    // контента внутри профиля (паттерн ProfileScreen W33-c).
                    onPhotosClick = { selectedTab = PROFILE_TAB_PHOTO },
                    onVideosClick = { selectedTab = PROFILE_TAB_VIDEO },
                    onAudiosClick = { selectedTab = PROFILE_TAB_MUSIC },
                    // «Друзья» — чип ведёт на отдельный экран списка друзей
                    // пользователя X (на своём профиле — Screen.Friends; здесь —
                    // FollowersSubscriptionsScreen MODE_FRIENDS, см. ниже).
                    onFriendsClick = { onFriendsClick(p.id) },
                )
            }
            // П-7-AB: строка «Подписки» собеседника (чипа в CountersRow нет —
            // поля subscriptions в модели Counters нет; строка — как в своём
            // профиле, см. KDoc SubscriptionsEntryRow).
            item {
                SubscriptionsEntryRow(
                    count = subscriptionsCount,
                    onClick = { onSubscriptionsClick(p.id) },
                )
            }

            // Действия: «Написать» + «Добавить в друзья» (кроме себя).
            if (!isSelf) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onMessageClick(
                                    p.id,
                                    p.fullName,
                                    p.photo200 ?: p.photo100,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Написать")
                        }
                        OutlinedButton(
                            onClick = {
                                if (friendActionInProgress) return@OutlinedButton
                                scope.launch {
                                    friendActionInProgress = true
                                    try {
                                        if (isFriend) {
                                            val ok = app.apiClient.friendsDelete(p.id)
                                            isFriend = !ok
                                            Toast.makeText(
                                                context,
                                                if (ok) "Удалён из друзей" else "Не удалось удалить",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            val result = app.apiClient.friendsAdd(p.id)
                                            // friendsAdd возвращает: 1 — заявка отправлена/принята,
                                            // 2 — одобрена, 0 — ошибка.
                                            if (result > 0) {
                                                isFriend = (result == 2)
                                                Toast.makeText(
                                                    context,
                                                    if (result == 2) "Добавлен в друзья"
                                                    else "Заявка отправлена",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось добавить: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AppLog.e("UserProfileScreen", "friend action failed", e)
                                        Toast.makeText(
                                            context,
                                            "Ошибка: ${e.message}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
} finally {
                                        friendActionInProgress = false
                                    }
                                }
                            },
                            enabled = !friendActionInProgress,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isFriend) "Удалить" else "В друзья")
                        }
                        // Этап П-2: «Подписаться»/«Отписаться» — usersSubscribe/
                        // usersUnsubscribe (VKA П-0; web-семантика «подписаться на
                        // открытой странице»). Состояние из is_subscribed; для
                        // друга (friend_status==3) не показываем — не дублируем
                        // кнопку «В друзья»/«Удалить».
                        if (!isFriend) {
                            OutlinedButton(
                                onClick = {
                                    if (subscribeInProgress) return@OutlinedButton
                                    scope.launch {
                                        subscribeInProgress = true
                                        try {
                                            val ok =
                                                if (subscribed) app.apiClient.usersUnsubscribe(p.id)
                                                else app.apiClient.usersSubscribe(p.id)
                                            if (ok) {
                                                subscribed = !subscribed
                                                Toast.makeText(
                                                    context,
                                                    if (subscribed) "Вы подписались" else "Вы отписались",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            AppLog.e("UserProfileScreen", "subscribe action failed", e)
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            subscribeInProgress = false
                                        }
                                    }
                                },
                                enabled = !subscribeInProgress,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(
                                    if (subscribed) "Отписаться" else "Подписаться",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // #FRIEND-CONTENT-TABS: ряд чипов вкладок «Стена/Музыка/Видео/Фото»
            // (как свой профиль — ProfileChipsRow; активируется тапом по чипу
            // счётчика или по самому чипу). Контент вкладок ниже, между
            // чипами и секцией «Информация» (для WALL секции остаются как есть).
            item(key = "friend_content_tabs") {
                ProfileChipsRow(
                    options = FRIEND_CONTENT_TABS,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }

            // Контент выбранной вкладки (неактивные секции рендерятся при WALL).
            if (selectedTab == PROFILE_TAB_MUSIC) {
                val me = musicError
                if (musicLoading) {
                    item(key = "friend_music_progress") { TabProgressRow() }
                } else if (me != null) {
                    item(key = "friend_music_error") {
                        TabErrorRow(message = me, onRetry = { tabRetryTick++ })
                    }
                } else if (musicTracks.isEmpty()) {
                    item(key = "friend_music_empty") {
                        TabEmptyRow("У этого пользователя нет музыки")
                    }
                } else {
                    itemsIndexed(musicTracks, key = { idx, t ->
                        "friend_music_${idx}_${t.ownerId}_${t.id}"
                    }) { idx, track ->
                        val current = friendPlayerState.currentTrack
                        val isCurrent = current != null &&
                            track.id == current.id && track.ownerId == current.ownerId
                        ProfileTrackRow(
                            track = track,
                            isPlaying = isCurrent && friendPlayerState.isPlaying,
                            onClick = {
                                if (isCurrent) PlayerConnection.togglePlayPause()
                                else PlayerConnection.playTrackList(musicTracks, idx)
                            },
                        )
                    }
                }
            } else if (selectedTab == PROFILE_TAB_VIDEO) {
                // ALBUMS-IN-VIDEO: во вкладке «Видео» сначала показываем видеоальбомы
                // (video.getAlbums) — горизонтальный список карточек над «Все видео».
                // Тап по альбому → openedVideoAlbum (грузим видео через video.get с
                // album_id) и рендерим их в сетке + кнопку «Назад к альбомам».
                // Паттерн — PROFILE_TAB_PHOTO ниже + ProfileScreen PROFILE_TAB_VIDEO.
                val openedVAlbum = openedVideoAlbum
                if (openedVAlbum != null) {
                    // Открыли видеоальбом — видео из этого альбома + «Назад».
                    item(key = "friend_album_video_back") {
                        AlbumBackRow(title = openedVAlbum.title) {
                            openedVideoAlbum = null
                            albumVideos = emptyList()
                        }
                    }
                    if (albumVideosLoading && albumVideos.isEmpty()) {
                        item(key = "friend_album_video_progress") { TabProgressRow() }
                    } else if (albumVideosError != null && albumVideos.isEmpty()) {
                        item(key = "friend_album_video_error") {
                            TabErrorRow(message = albumVideosError ?: "", onRetry = { tabRetryTick++ })
                        }
                    } else if (albumVideos.isEmpty()) {
                        item(key = "friend_album_video_empty") {
                            TabEmptyRow("В альбоме нет видео")
                        }
                    } else {
                        val albumVideoRows = albumVideos.chunked(2)
                        itemsIndexed(
                            albumVideoRows,
                            key = { idx, _ -> "friend_album_video_row_$idx" },
                        ) { _, rowVideos ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowVideos.forEach { video ->
                                    ProfileVideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(2 - rowVideos.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    // Горизонтальный список видеоальбомов (если есть) над «Все видео».
                    if (videoAlbums.isNotEmpty()) {
                        item(key = "friend_video_albums_header") {
                            Text(
                                text = "Видеоальбомы (${videoAlbums.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        item(key = "friend_video_albums_row") {
                            ProfileVideoAlbumsRow(
                                albums = videoAlbums,
                                onAlbumClick = { openedVideoAlbum = it },
                            )
                        }
                    }
                    val ve = videoError
                    if (videoLoading) {
                        item(key = "friend_video_progress") { TabProgressRow() }
                    } else if (ve != null) {
                        item(key = "friend_video_error") {
                            TabErrorRow(message = ve, onRetry = { tabRetryTick++ })
                        }
                    } else if (videos.isEmpty()) {
                        item(key = "friend_video_empty") { TabEmptyRow("У этого пользователя нет видео") }
                    } else {
                        val rows = videos.chunked(2)
                        itemsIndexed(rows, key = { idx, _ -> "friend_video_row_$idx" }) { _, rowVideos ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowVideos.forEach { video ->
                                    ProfileVideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == PROFILE_TAB_PHOTO) {
                // PROFILE-ALBUMS: во вкладке «Фото» сначала показываем альбомы
                // (photos.getAlbums) — горизонтальный список карточек над
                // «Все фото». Тап по альбому → openedAlbum (грузим фото через
                // photos.get) и рендерим их в сетке + кнопку «Назад к альбомам».
                // Паттерн — ProfileScreen.kt:1731.
                val opened = openedAlbum
                if (opened != null) {
                    // PROFILE-ALBUMS: albumUrls вычисляем ВНЕ item{} (remember
                    // нельзя вызывать в LazyListScope — не @Composable).
                    val albumUrls = albumPhotos.mapNotNull { it.largestUrl }
                    // Открыли альбом — фото из этого альбома + «Назад».
                    item(key = "friend_album_back") {
                        AlbumBackRow(title = opened.title) { openedAlbum = null }
                    }
                    if (albumPhotosLoading && albumUrls.isEmpty()) {
                        item(key = "friend_album_photo_progress") { TabProgressRow() }
                    } else if (albumPhotosError != null && albumUrls.isEmpty()) {
                        item(key = "friend_album_photo_error") {
                            TabErrorRow(
                                message = albumPhotosError ?: "",
                                onRetry = { albumPhotosRetryTick++ },
                            )
                        }
                    } else if (albumUrls.isEmpty()) {
                        item(key = "friend_album_photo_empty") {
                            TabEmptyRow("В альбоме нет фотографий")
                        }
                    } else {
                        val albumRows = albumUrls.chunked(3)
                        itemsIndexed(
                            albumRows,
                            key = { idx, _ -> "friend_album_photo_row_$idx" },
                        ) { idx, rowUrls ->
                            PhotoGridRow(
                                urls = rowUrls,
                                startIndex = idx * 3,
                                fullList = albumUrls,
                                onPhotoClick = { urls, i -> photoViewerState.value = urls to i },
                            )
                        }
                    }
                } else {
                    // Горизонтальный список альбомов (если есть) над «Все фото».
                    if (albums.isNotEmpty()) {
                        item(key = "friend_albums_header") {
                            Text(
                                text = "Альбомы (${albums.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        item(key = "friend_albums_row") {
                            ProfileAlbumsRow(
                                albums = albums,
                                onAlbumClick = { openedAlbum = it },
                            )
                        }
                    }
                    // «Все фото» (photos.getAll) — заголовок + сетка (как раньше).
                    item(key = "friend_all_photos_header") {
                        Text(
                            text = "Все фото",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    val pe = photoError
                    if (photoLoading) {
                        item(key = "friend_photo_progress") { TabProgressRow() }
                    } else if (pe != null) {
                        item(key = "friend_photo_error") {
                            TabErrorRow(message = pe, onRetry = { tabRetryTick++ })
                        }
                    } else if (photoUrls.isEmpty()) {
                        item(key = "friend_photo_empty") { TabEmptyRow("У этого пользователя нет фотографий") }
                    } else {
                        val rows = photoUrls.chunked(3)
                        itemsIndexed(rows, key = { idx, _ -> "friend_photo_row_$idx" }) { idx, rowUrls ->
                            PhotoGridRow(
                                urls = rowUrls,
                                startIndex = idx * 3,
                                fullList = photoUrls,
                                onPhotoClick = { urls, i -> photoViewerState.value = urls to i },
                            )
                        }
                    }
                }
            }

            // #OPVK-EXTRACT: секция «Информация» (label/value из уже загруженного
            // UserProfile) — между действиями и «Подарками», как инфо-блок веба.
            if (selectedTab == PROFILE_TAB_WALL && infoRows.isNotEmpty()) {
                item(key = "profile_info") {
                    ProfileInfoSection(rows = infoRows)
                }
            }

            // Этап П-2: секция «Подарки» над лентой записей (gifts.get, count=9,
            // инвентарь §1.1.6). Рисуется только при непустом ответе — no-stub.
            if (selectedTab == PROFILE_TAB_WALL && gifts.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "Подарки (${p.counters?.gifts ?: gifts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(gifts) { g ->
                                val url = giftImageUrl(g)
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Подарок",
                                        modifier = Modifier.size(72.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Блок стены (заголовок, посты, пагинация) — только на вкладке «Стена».
            if (selectedTab == PROFILE_TAB_WALL) {
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
                // Группа-источник репоста (copy_history[0], ownerId<0): резолвим
                // её имя/аватар лениво через groups.getById (LaunchedEffect внутри
                // items-lambda — валидный composable-контекст; кэш не падает при
                // ошибке сети).
                val repostOwnerId = post.copyHistory?.firstOrNull()?.ownerId
                if (repostOwnerId != null && repostOwnerId < 0) {
                    LaunchedEffect(post.ownerId, post.id) { repostGroups.resolve(app.apiClient, post) }
                }
                WallPostCard(
                    post = post,
                    authorName = p.fullName,
                    authorPhoto = p.photo200 ?: p.photo100,
                    carouselEnabled = carouselEnabled,
                    onVideoClick = onVideoClick,
                    onPostClick = onPostClick,
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    onRepostClick = { repostPost.value = it },
                    // Шаг 5 (#32e): тап по комментарию → onCommentClick → PostDetailScreen.
                    onCommentClick = onCommentClick,
                    // POSTCARD-REFACTOR-PLAN: тап по имени/аватару автора поста —
                    // группа/канал → сообщество, пользователь → его профиль.
                    onGroupClick = onGroupClick,
                    onUserClick = onUserClick,
                    // Группа-источник РЕПОСТА: имя/аватар из кэша репост-групп
                    // (null до загрузки → «Запись клуба <id>», затем рекомпозиция
                    // подставит имя/фото). Клик по репост-шапке из группы ВЕДЁТ В
                    // СООБЩЕСТВО (Screen.Community.buildRoute).
                    repostGroupName = if (repostOwnerId != null && repostOwnerId < 0) repostGroups.nameOf(-repostOwnerId) else null,
                    repostGroupPhoto = if (repostOwnerId != null && repostOwnerId < 0) repostGroups.photoOf(-repostOwnerId) else null,
                    onRepostGroupClick = {
                        post.copyHistory?.firstOrNull()?.ownerId
                            ?.takeIf { it < 0 }
                            ?.let { onGroupClick(-it) }
                    },
                    // П-7-AB: «Пожаловаться» на записях ЧУЖОЙ стены (wall.markAsSpam).
                    // showActions по-прежнему false (П-6а: manage-пункты только на
                    // своём профиле) → меню открывается с одним «Пожаловаться».
                    showReport = !isSelf,
                    onReportSpam = { reportingPost.value = it },
                )
            }
            // Fix #86: футер пагинации.
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "На стене нет записей",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            } // if (selectedTab == PROFILE_TAB_WALL)
        }
        } // PullToRefreshBox (Fix #86)

        // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх» над стеной.
        ScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
        } // closes Box (Fix #389 #SCROLL-TOP-PARITY)

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

        // ShareSheet: расширенный диалог «Поделиться».
        val sharing = repostPost.value
        if (sharing != null) {
            ShareSheet(
                post = sharing,
                onDismiss = { repostPost.value = null },
            )
        }

        // П-7-AB: подтверждение «Пожаловаться» на запись (wall.markAsSpam).
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
}

/**
 * Этап П-2 (PROFILE-P2-3b): URL картинки подарка из item ответа `gifts.get`.
 * VK отдаёт два формата: legacy — gift_75/gift_96/gift_256 строками на уровне
 * item; новый — вложенный объект gift { thumb_75/thumb_96/thumb_256 }.
 * Поддержаны оба; null → item без картинки пропускается (no-stub).
 */
private fun giftImageUrl(item: JsonObject): String? {
    for (key in listOf("gift_256", "gift_96", "gift_75")) {
        val v = item.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        if (!v.isNullOrBlank()) return v
    }
    val gift = item.getAsJsonObject("gift") ?: return null
    for (key in listOf("thumb_256", "thumb_96", "thumb_75")) {
        val v = gift.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        if (!v.isNullOrBlank()) return v
    }
    return null
}

/**
 * #OPVK-EXTRACT (Task 3-c): строки секции «Информация» из полей UserProfile,
 * которые УЖЕ приходят в загрузочном флоу экрана (usersGetFull →
 * usersGetFullExtended, fields about/activities/interests/music/movies/books;
 * парсинг VKA:13457-13463) — НИКАКИХ новых VKA-вызовов. Только непустые
 * значения (семантика AboutProfileLayout OpenVK: item добавляется при
 * непустой строке — идея, не код; AGPL-копирование запрещено).
 *
 * ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
 *  — «Любимые цитаты» (quotes) не рисуется: поле запрашивается в fields
 *    usersGetFullExtended, но в модель UserProfile НЕ парсится (модель —
 *    core/data, вне зоны правок), имитировать значение нечем;
 *  — bdate/city НЕ дублируются: их уже показывает [ProfileHeader]
 *    («День рождения: …» / «Город: …», ProfileScreen.kt:1526-1541);
 *  — sex/tv/games/relation/career/education и personal.* приходят не для всех
 *    страниц/токенов (UserProfile.sex — 0-дефолт без «не задано»-маркера),
 *    в заданный список строк не входят — не рисуются.
 */
private fun profileInfoRows(p: UserProfile): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    p.about?.takeIf { it.isNotBlank() }?.let { rows.add("О себе" to it) }
    p.activities?.takeIf { it.isNotBlank() }?.let { rows.add("Деятельность" to it) }
    p.interests?.takeIf { it.isNotBlank() }?.let { rows.add("Интересы" to it) }
    p.music?.takeIf { it.isNotBlank() }?.let { rows.add("Любимая музыка" to it) }
    p.movies?.takeIf { it.isNotBlank() }?.let { rows.add("Любимые фильмы" to it) }
    p.books?.takeIf { it.isNotBlank() }?.let { rows.add("Любимые книги" to it) }
    return rows
}

/**
 * #OPVK-EXTRACT (Task 3-c): карточка «Информация» чужого профиля — заголовок
 * в стиле секций экрана («Подарки»/«Записи»: titleMedium) + Card (паттерн
 * [SubscriptionsEntryRow]: elevation 0) со строками label/value. Рисуется
 * только при наличии хотя бы одной непустой строки (вызывает только с
 * непустым [rows] — см. вызов в UserProfileScreen).
 */
@Composable
private fun ProfileInfoSection(rows: List<Pair<String, String>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Информация",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Card(elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                rows.forEachIndexed { index, row ->
                    val (label, value) = row
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// #FRIEND-CONTENT-TABS: вкладки контента чужого профиля. Только Стена/Музыка/
// Видео/Фото — Клипы/Статьи/Закладки (для своего профиля, ProfileScreen) для
// чужого пользователя обычно недоступны или не нужны (no-stub).
private val FRIEND_CONTENT_TABS: List<Pair<String, String>> = listOf(
    PROFILE_TAB_WALL to "Стена",
    PROFILE_TAB_MUSIC to "Музыка",
    PROFILE_TAB_VIDEO to "Видео",
    PROFILE_TAB_PHOTO to "Фото",
)
