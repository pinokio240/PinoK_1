package re.pinok.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.ui.navigation.PostHolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Post
import re.pinok.data.model.Video
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
// #POST-CAROUSEL-EVERYWHERE (22-B): общий компонент карусели фото поста.
import re.pinok.ui.components.PostPhotoGrid
// Fix #366: общий компонент карусели видео поста.
import re.pinok.ui.components.PostVideoCarousel
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.navigation.CommunityRestoreHolder
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime
import re.pinok.util.toCountString
import java.util.Locale
import android.widget.Toast

/**
 * Fix #67: Экран сообщества — header (ава + имя + описание) + стена (wall.get).
 *
 * Открывается тапом по header'у поста в ленте (FeedScreen).
 * Использует `groups.getById` для метаданных и `wall.get` с `owner_id = -groupId`
 * для постов. Парсинг постов — через общий `parsePostMini`, который после Fix #70
 * корректно парсит attachments и copy_history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    groupId: Long,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    // Шаг 4 (#32d): тап по теме обсуждения → BoardTopicScreen.
    onTopicClick: (groupId: Long, topicId: Long, title: String) -> Unit = { _, _, _ -> },
    // #OPVK-EXTRACT (Task 3-c): тап по счётчику подписчиков в шапке →
    // GroupMembersScreen (список участников, groups.getMembers).
    onMembersClick: (groupId: Long) -> Unit = {},
    // W35-b (волна 35): блок «Управление» — маршрутизация в админ-экраны
    // (CommunityAdminBlock рендерится при admin_level >= 1 — сверка §5.1).
    onAdminSettingsClick: (groupId: Long) -> Unit = {},
    onAdminStatsClick: (groupId: Long) -> Unit = {},
    onAdminPeopleClick: (groupId: Long, tab: String) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // #POST-CAROUSEL-EVERYWHERE (22-B): флаг карусели фото — один общий
    // SovaPrefs.feedCarouselEnabled (управляет лентой/сообществами/профилями/
    // пост-детейлом; настройка «Настройки → Лента → „Карусель фото в постах"»).
    // СКОУП-РЕШЕНИЕ: читается ОДИН раз на уровне экрана (здесь, где уже есть
    // app) и пробрасывается параметром в CommunityPostCard (два места рендера
    // фото — пост и репост-карточка — внутри неё), а не читается на каждый кадр.
    val prefsSnap by app.prefs.data.collectAsState(initial = null)
    // NULL-ЯВНО: Snapshot-initial до первого эмита; дефолт true = SovaPrefs default.
    val carouselEnabled: Boolean = prefsSnap?.feedCarouselEnabled ?: true
    var groupInfo by remember { mutableStateOf<VKApiClient.GroupInfo?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    // #POST-SIGNER (волна 39): имена профилей из wall.get extended=1 —
    // подпись автора под постом сообщества (запрос пользователя: имя автора
    // под постом не писалось в приложении, а на веб-странице ВК писалось).
    // Копится между страницами пагинации (wallGetExtended.profileNames).
    var signerNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Fix #85: pull-to-refresh + infinite scroll стены группы.
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // W36 #COMMUNITY-COMPOSER (C0 волны 35): композер постинга на стену
    // сообщества (референс web: group_publish_block, сверка §4.1).
    var showComposer by remember { mutableStateOf(false) }
    // S6-4: подписка на сообщество.
    var isMember by remember { mutableStateOf(false) }
    // Fix #350: блокируем кнопку подписки на время API-запроса.
    var subscribePending by remember { mutableStateOf(false) }
    var descExpanded by remember { mutableStateOf(false) }
    // S6-4: оптимистичное состояние лайков.
    val likesState = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    // #30j (community tabs): активная вкладка контента сообщества.
    var selectedTab by remember { mutableStateOf(0) }
    // #GROUP-CLIPS: «Клипы» — отдельная вкладка (shortVideo.getOwnerVideos).
    val tabs = listOf("Записи", "Фото", "Видео", "Клипы", "Музыка", "Обсуждения")

    // Шаг 1 (#32a): state для вкладки «Фото».
    // Ленивая загрузка при первом открытии вкладки; без пагинации (photosGet возвращает
    // до 50 за раз — достаточно для первого приближения, пагинацию добавим отдельным шагом).
    var photos by remember { mutableStateOf<List<re.pinok.data.model.PhotoItem>>(emptyList()) }
    var photosLoading by remember { mutableStateOf(false) }
    var photosError by remember { mutableStateOf<String?>(null) }
    var photosLoaded by remember { mutableStateOf(false) }

    // Шаг 2 (#32b): state для вкладки «Видео».
    // С пагинацией (видео «тяжелее», 30 за раз мало для активного сообщества).
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var videosLoading by remember { mutableStateOf(false) }
    var videosLoadingMore by remember { mutableStateOf(false) }
    var videosError by remember { mutableStateOf<String?>(null) }
    var videosLoaded by remember { mutableStateOf(false) }
    var videosEndReached by remember { mutableStateOf(false) }

    // Шаг 3 (#32c): state для вкладки «Музыка».
    // #AUDIO-PAGING-ALL (волна 38): была капнута на 50 (одна страница audioGet) —
    // до конца списка у сообществ с музыкой >50 треков было не доскроллить.
    // Теперь — серверная пагинация по offset (audioGetWithCount), автодогрузка
    // по скроллу до конца, стоп по неполной странице (VK total НЕ доверяем —
    // занижает, прецедент AudioLibraryPager).
    var tracks by remember { mutableStateOf<List<re.pinok.data.model.Track>>(emptyList()) }
    var tracksLoading by remember { mutableStateOf(false) }
    var tracksError by remember { mutableStateOf<String?>(null) }
    var tracksLoaded by remember { mutableStateOf(false) }
    var tracksServerOffset by remember { mutableStateOf(0) }
    var tracksHasMore by remember { mutableStateOf(false) }
    var tracksLoadingMore by remember { mutableStateOf(false) }

    // #GROUP-CLIPS: state для вкладки «Клипы» (shortVideo.getOwnerVideos).
    // Без пагинации (30 за раз достаточно для первого приближения).
    var clips by remember { mutableStateOf<List<Video>>(emptyList()) }
    var clipsLoading by remember { mutableStateOf(false) }
    var clipsError by remember { mutableStateOf<String?>(null) }
    var clipsLoaded by remember { mutableStateOf(false) }

    // Шаг 4 (#32d): state для вкладки «Обсуждения».
    // Без пагинации (30 тем за раз — обычно достаточно;TopicsTab простой список).
    var topics by remember { mutableStateOf<List<re.pinok.data.model.BoardTopic>>(emptyList()) }
    var topicsLoading by remember { mutableStateOf(false) }
    var topicsError by remember { mutableStateOf<String?>(null) }
    var topicsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val groups = app.apiClient.groupsGetById(listOf(groupId))
                groupInfo = groups.firstOrNull()
                isMember = groupInfo?.isMember == 1
                if (groupInfo == null) {
                    errorText = app.apiClient.lastApiError ?: "Сообщество не найдено (id=$groupId)"
                } else {
                    // wall.get с owner_id = -groupId (VK convention: группы — отрицательные).
                    // #POST-SIGNER: extended=1 — вместе с постами приходят профили
                    // (имена авторов подписи).
                    val wallPage = app.apiClient.wallGetExtended(ownerId = -groupId, count = 30)
                    posts = wallPage.posts
                        .filter { it.id > 0 && it.ownerId != 0L }
                        .distinctBy { "${it.ownerId}_${it.id}" }
                    signerNames = wallPage.profileNames
                    endReached = wallPage.posts.size < 30
                    AppLog.i("CommunityScreen", "Loaded group ${groupInfo?.name} + ${posts.size} posts")
                }
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "Failed to load community", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // #NAV-GROUP-VIDEO-RESTORE (Fix #344): сообщество загружено — пишем контекст
    // восстановления (id). Стирание — в SovaNavHost при уходе с экрана Community.
    LaunchedEffect(groupInfo) {
        if (groupInfo != null) {
            app.prefs.setLastCommunityId(groupId)
        }
    }
    // #NAV-GROUP-VIDEO-RESTORE (Fix #344): однократная прокрутка стены к позиции
    // до смерти процесса. Индекс сохраняется/восстанавливается в одних координатах
    // LazyColumn (хедер + табы + посты) — пространство индексов идентично.
    LaunchedEffect(posts) {
        val target = CommunityRestoreHolder.pendingScroll
        if (posts.isNotEmpty() && target >= 0 && selectedTab == 0) {
            CommunityRestoreHolder.pendingScroll = -1
            listState.scrollToItem(target)
        }
    }
    // #NAV-GROUP-VIDEO-RESTORE (Fix #344): позиция стены пишется на каждой
    // остановке скролла и после каждой загрузки постов (posts в ключах —
    // иначе после загрузки без скролла в prefs остался бы протухший индекс).
    // Process death dispose не вызывает, поэтому пишем постоянно, а не в onDispose.
    LaunchedEffect(listState.isScrollInProgress, selectedTab, posts) {
        if (selectedTab == 0 && !listState.isScrollInProgress && posts.isNotEmpty()) {
            app.prefs.setLastCommunityScroll(listState.firstVisibleItemIndex)
        }
    }

    // Fix #85: pull-to-refresh стены группы.
    fun refreshWall() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                // #POST-SIGNER: extended=1 (имена авторов подписи).
                val wallPage = app.apiClient.wallGetExtended(ownerId = -groupId, count = 30)
                posts = wallPage.posts
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                signerNames = wallPage.profileNames
                endReached = wallPage.posts.size < 30
                AppLog.i("CommunityScreen", "refreshed wall: ${wallPage.posts.size} posts")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "refreshWall failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #85: infinite scroll стены группы.
    fun loadMoreWall() {
        if (loadingMore || endReached || posts.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                // #POST-SIGNER: extended=1; имена профилей копим между страницами.
                val wallPage = app.apiClient.wallGetExtended(
                    ownerId = -groupId, count = 30, offset = posts.size)
                val wall = wallPage.posts
                signerNames = signerNames + wallPage.profileNames
                val newPosts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .filter { np -> posts.none { it.ownerId == np.ownerId && it.id == np.id } }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                if (newPosts.isEmpty()) {
                    endReached = true
                } else {
                    posts = (posts + newPosts).distinctBy { "${it.ownerId}_${it.id}" }
                    if (wall.size < 30) endReached = true
                }
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "loadMoreWall failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Шаг 1 (#32a): загрузка фото сообщества при первом переходе на вкладку «Фото».
    // photosLoaded предотвращает повторные запросы при переключении туда-обратно.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 1 || photosLoaded || photosLoading) return@LaunchedEffect
        scope.launch {
            photosLoading = true
            photosError = null
            try {
                // VK convention: owner_id группы — отрицательный.
                photos = app.apiClient.photosGet(ownerId = -groupId, albumId = "wall", count = 50)
                photosLoaded = true
                AppLog.i("CommunityScreen", "Loaded ${photos.size} photos for group $groupId")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "photosGet failed", e)
                photosError = "Ошибка: ${e.message}"
            } finally {
                photosLoading = false
            }
        }
    }

    // Шаг 2 (#32b): первая загрузка видео сообщества при переходе на вкладку 2.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 2 || videosLoaded || videosLoading) return@LaunchedEffect
        scope.launch {
            videosLoading = true
            videosError = null
            try {
                videos = app.apiClient.videoGet(ownerId = -groupId, count = 30, offset = 0)
                videosLoaded = true
                videosEndReached = videos.size < 30
                AppLog.i("CommunityScreen", "Loaded ${videos.size} videos for group $groupId")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "videoGet failed", e)
                videosError = "Ошибка: ${e.message}"
            } finally {
                videosLoading = false
            }
        }
    }

    // Шаг 2 (#32b): пагинация видео.
    fun loadMoreVideos() {
        if (videosLoadingMore || videosEndReached || videos.isEmpty()) return
        scope.launch {
            videosLoadingMore = true
            try {
                val more = app.apiClient.videoGet(ownerId = -groupId, count = 30, offset = videos.size)
                val newOnes = more.filter { nv -> videos.none { it.id == nv.id && it.ownerId == nv.ownerId } }
                if (newOnes.isEmpty()) {
                    videosEndReached = true
                } else {
                    videos = (videos + newOnes).distinctBy { "${it.ownerId}_${it.id}" }
                    if (more.size < 30) videosEndReached = true
                }
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "loadMoreVideos failed", e)
            } finally {
                videosLoadingMore = false
            }
        }
    }

    // Шаг 2 (#32b): триггер пагинации видео (только когда активна вкладка 2).
    LaunchedEffect(listState, videos.size, selectedTab) {
        if (selectedTab != 2) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMoreVideos() }
    }

    // #GROUP-CLIPS: загрузка клипов сообщества при переходе на вкладку 3.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 3 || clipsLoaded || clipsLoading) return@LaunchedEffect
        scope.launch {
            clipsLoading = true
            clipsError = null
            try {
                clips = app.apiClient.shortVideoGetOwnerVideos(ownerId = -groupId, count = 30)
                clipsLoaded = true
                AppLog.i("CommunityScreen", "Loaded ${clips.size} clips for group $groupId")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "shortVideoGetOwnerVideos failed", e)
                clipsError = "Ошибка: ${e.message}"
            } finally {
                clipsLoading = false
            }
        }
    }

    // Шаг 3 (#32c): загрузка музыки сообщества при переходе на вкладку 4.
    // #AUDIO-PAGING-ALL: первая страница (100 треков) — дальше догрузка по скроллу.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 4 || tracksLoaded || tracksLoading) return@LaunchedEffect
        scope.launch {
            tracksLoading = true
            tracksError = null
            try {
                val (total, page) = app.apiClient.audioGetWithCount(
                    count = 100, offset = 0, ownerId = -groupId)
                tracks = page.filter { it.id > 0L && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                tracksServerOffset = page.size
                // Полная страница → есть ещё данные; частичная → конец списка
                // (total не используем как стоп — VK занижает count).
                tracksHasMore = page.size >= 100
                tracksLoaded = true
                AppLog.i("CommunityScreen", "Loaded ${tracks.size}/${page.size} tracks for group $groupId (total=$total hasMore=$tracksHasMore)")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "audioGet failed", e)
                tracksError = "Ошибка: ${e.message}"
            } finally {
                tracksLoading = false
            }
        }
    }

    // #AUDIO-PAGING-ALL: догрузка следующей страницы музыки сообщества.
    fun loadMoreCommunityTracks() {
        if (tracksLoadingMore || tracksLoading || !tracksHasMore) return
        scope.launch {
            tracksLoadingMore = true
            try {
                val (_, page) = app.apiClient.audioGetWithCount(
                    count = 100, offset = tracksServerOffset, ownerId = -groupId)
                tracksServerOffset += page.size
                val fresh = page.filter { it.id > 0L && it.ownerId != 0L }
                    .filter { nv -> tracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                tracks = (tracks + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                tracksHasMore = page.size >= 100
                AppLog.i("CommunityScreen", "Community music loadMore: +${fresh.size} (offset=$tracksServerOffset)")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "community music loadMore failed", e)
            } finally {
                tracksLoadingMore = false
            }
        }
    }

    // #AUDIO-PAGING-ALL: триггер автодогрузки музыки (вкладка 4).
    LaunchedEffect(listState, selectedTab) {
        if (selectedTab != 4) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 3) total else -1
        }
            .distinctUntilChanged()
            .filter { it > 0 }
            .collect { loadMoreCommunityTracks() }
    }

    // Шаг 4 (#32d): загрузка тем обсуждений при переходе на вкладку 5.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 5 || topicsLoaded || topicsLoading) return@LaunchedEffect
        scope.launch {
            topicsLoading = true
            topicsError = null
            try {
                topics = app.apiClient.boardGetTopics(groupId = groupId, count = 30, offset = 0)
                topicsLoaded = true
                AppLog.i("CommunityScreen", "Loaded ${topics.size} topics for group $groupId")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "boardGetTopics failed", e)
                topicsError = "Ошибка: ${e.message}"
            } finally {
                topicsLoading = false
            }
        }
    }

    // Fix #85: триггер пагинации.
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

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val g = groupInfo
    if (g == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorText ?: "Сообщество не загружено",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = onBack) { Text("Назад") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    loading = true
                    errorText = null
                    scope.launch {
                        try {
                            val grps = app.apiClient.groupsGetById(listOf(groupId))
                            groupInfo = grps.firstOrNull()
                            isMember = groupInfo?.isMember == 1
                            if (groupInfo == null) {
                                errorText = app.apiClient.lastApiError ?: "Сообщество не найдено (id=$groupId)"
                            } else {
                                val wallPage = app.apiClient.wallGetExtended(ownerId = -groupId, count = 30)
                                posts = wallPage.posts
                                    .filter { it.id > 0 && it.ownerId != 0L }
                                    .distinctBy { "${it.ownerId}_${it.id}" }
                                signerNames = wallPage.profileNames
                                endReached = wallPage.posts.size < 30
                            }
                        } catch (e: Exception) {
                            AppLog.e("CommunityScreen", "Retry failed", e)
                            errorText = "Ошибка: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Повторить") }
            }
        }
        return
    }

    // Fix #43: statusBarsPadding — контент не уходит под системную панель.
    // CommunityScreen в hasOwnTopBar списке SovaNavHost → глобальный TopAppBar
    // не рисуется, insets нужно применять самому. Аналогично ProfileScreen.
    // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх»
    // (один общий listState на стену/видео/обсуждения — FAB общий для всех табов).
    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshWall() },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item {
            // Header — top row: back + avatar + name
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (g.photo100 != null || g.photo200 != null) {
                    AsyncImage(
                        model = g.photo200 ?: g.photo100,
                        contentDescription = g.name,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = g.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Name row with optional verified badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = g.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (g.verified == 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Подтверждённое сообщество",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF1976D2),
                            )
                        }
                    }
                    // Type badge
                    val typeLabel = when (g.type) {
                        "page" -> "Публичная страница"
                        "event" -> "Мероприятие"
                        else -> "Группа"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        if (g.isClosed == 1) {
                            Text(
                                text = " · Закрытое сообщество",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    // Subscriber count — #OPVK-EXTRACT (Task 3-c): кликабельная
                    // строка-вход «Участники»: тап по счётчику подписчиков
                    // (members_count из GroupInfo) открывает список участников
                    // (GroupMembersScreen, groups.getMembers). Шеврон — аффорданс;
                    // тач-таргет ≥ 44dp (defaultMinSize).
                    if (g.membersCount > 0) {
                        val countStr = formatMemberCount(g.membersCount)
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable { onMembersClick(groupId) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$countStr подписчиков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Участники",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Status text
            if (!g.status.isNullOrBlank()) {
                Text(
                    text = g.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Description
            if (!g.description.isNullOrBlank()) {
                val maxLines = if (descExpanded) Int.MAX_VALUE else 3
                // #30 (build fix): ClickableText deprecated в Compose 2025.06.00.
                // Заменён на Text + clickable modifier — проще и стабильнее.
                val descColor = MaterialTheme.colorScheme.onSurfaceVariant
                val linkColor = Color(0xFF1976D2)
                Text(
                    text = buildAnnotatedString {
                        append(g.description)
                        if (!descExpanded) {
                            withStyle(SpanStyle(color = linkColor)) {
                                append(" Показать ещё")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = descColor),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { if (!descExpanded) descExpanded = true },
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // S6-4: кнопка подписки / отписки.
        item {
            val buttonShape = RoundedCornerShape(12.dp)
            val context = LocalContext.current
            if (isMember) {
                OutlinedButton(
                    onClick = {
                        if (subscribePending) return@OutlinedButton
                        scope.launch {
                            subscribePending = true
                            try {
                                val ok = app.apiClient.groupsLeave(groupId)
                                if (ok) {
                                    isMember = false
                                    Toast.makeText(context, "Вы отписались от «${groupInfo?.name ?: "сообщества"}»", Toast.LENGTH_SHORT).show()
                                    // Fix #52-B: после отписки состав историй мог
                                    // измениться (stories.get включает истории от
                                    // подписанных сообществ) — markDirty триггерит
                                    // перезагрузку в StoriesRow при возврате на Feed.
                                    re.pinok.ui.navigation.StoriesHolder.markDirty()
                                } else {
                                    val err = app.apiClient.lastApiError
                                    Toast.makeText(
                                        context,
                                        if (err.isNullOrBlank()) "Не удалось отписаться" else "Ошибка: $err",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e("CommunityScreen", "groupsLeave failed", e)
                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                subscribePending = false
                            }
                        }
                    },
                    enabled = !subscribePending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = buttonShape,
                ) {
                    if (subscribePending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Отписаться")
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (subscribePending) return@Button
                        scope.launch {
                            subscribePending = true
                            try {
                                val ok = app.apiClient.groupsJoin(groupId)
                                if (ok) {
                                    isMember = true
                                    Toast.makeText(context, "Вы подписались на «${groupInfo?.name ?: "сообщество"}»", Toast.LENGTH_SHORT).show()
                                    // Fix #52-B: после подписки состав историй мог
                                    // измениться (новое сообщество может иметь активные
                                    // истории) — markDirty триггерит перезагрузку.
                                    re.pinok.ui.navigation.StoriesHolder.markDirty()
                                } else {
                                    val err = app.apiClient.lastApiError
                                    Toast.makeText(
                                        context,
                                        if (err.isNullOrBlank()) "Не удалось подписаться" else "Ошибка: $err",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e("CommunityScreen", "groupsJoin failed", e)
                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                subscribePending = false
                            }
                        }
                    },
                    enabled = !subscribePending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = buttonShape,
                ) {
                    if (subscribePending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Подписаться")
                    }
                }
            }
        }
        // W35-b (волна 35): карточка «Управление» — админ-блок над вкладками
        // (референс: правое меню 11 пунктов, сверка «Группа_админ» §4.1;
        // внутренний гейт isManager внутри блока — у обычного участника невидим).
        item {
            CommunityAdminBlock(
                groupInfo = groupInfo,
                onSettingsClick = onAdminSettingsClick,
                onStatsClick = onAdminStatsClick,
                onPeopleClick = onAdminPeopleClick,
            )
        }
        item {
            // #30j (community tabs): ScrollableTabRow с 5 вкладками.
            // Соответствует §9.8 VK_IMPORT_API.MD: group_tab_wall/photos/videos/audios/topics.
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }
        // #30j: условный рендеринг контента по выбранной вкладке.
        when (selectedTab) {
            0 -> {
                // Wall — записи сообщества (существующая реализация)
                // W36 #COMMUNITY-COMPOSER (C0 волны 35): строка композера над
                // записями (как в web — group_publish_block). Гейт can_post=1 —
                // право постить на стене (приходит в groups.getById из волны 35;
                // у обычного участника без права композер не рисуется).
                if (g.canPost == 1) {
                    item(key = "community_composer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showComposer = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Ава сообщества (место публикации); у руководителя
                            // дефолтный автор записи — само сообщество (сверка §3.5).
                            AsyncImage(
                                model = g.photo200 ?: g.photo100,
                                contentDescription = "Аватар сообщества",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Написать запись…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
            val postKey = "${post.ownerId}_${post.id}"
            CommunityPostCard(
                post = post,
                authorName = g.name,
                authorPhoto = g.photo200 ?: g.photo100,
                // #POST-SIGNER (волна 39): имя автора подписи из wall.get extended=1.
                signerName = post.signerId?.let { sid -> signerNames[sid] },
                onVideoClick = onVideoClick,
                // Fix #365: пробрасываем группы в PostHolder для имени сообщества
                // в PostDetailScreen (тот же паттерн, что в FeedScreen) — работает
                // и для репост-карточек (клик по карточке репоста открывает
                // оригинальный пост через этот же колбэк).
                onPostClick = { post ->
                    PostHolder.lastGroups = mapOf(groupId to g)
                    onPostClick(post)
                },
                onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                // 22-B: карусель фото (общий SovaPrefs-флаг, см. выше).
                carouselEnabled = carouselEnabled,
                onRepostClick = { repostPost.value = it },
                onLikeToggle = { ownerId, postId, currentlyLiked ->
                    val key = "${ownerId}_${postId}"
                    val base = post.likes?.count ?: 0
                    val prev = likesState[key] ?: (post.likes?.userLikes == 1) to base
                    // Оптимистичное обновление.
                    likesState[key] = (!prev.first) to (if (prev.first) prev.second - 1 else prev.second + 1)
                    scope.launch {
                        try {
                            val newCount = if (currentlyLiked) {
                                app.apiClient.likesDelete("post", ownerId, postId)
                            } else {
                                app.apiClient.likesAdd("post", ownerId, postId)
                            }
                            likesState[key] = (!currentlyLiked) to (if (newCount >= 0) newCount else likesState[key]?.second ?: prev.second)
                        } catch (e: Exception) {
                            AppLog.e("CommunityScreen", "like toggle failed for $key", e)
                            likesState[key] = prev
                        }
                    }
                },
                onCommentClick = { ownerId, postId ->
                    val cPost = posts.find { it.ownerId == ownerId && it.id == postId }
                    if (cPost != null) {
                        PostHolder.last = cPost
                        PostHolder.lastGroups = mapOf(groupId to g)
                        onPostClick(cPost)
                    }
                },
                likesOverride = likesState[postKey],
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        }
        // Fix #85: футер пагинации.
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
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
            } // when (selectedTab) 0 — Wall
            1 -> {
                // Шаг 1 (#32a): Фото сообщества — photosGet(ownerId=-groupId, albumId="wall").
                // Сетка 3 колонки (FlowRow), тап → PhotoViewer (уже подключён ниже).
                item {
                    CommunityPhotosTab(
                        photos = photos,
                        loading = photosLoading,
                        error = photosError,
                        onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    )
                }
            }
            2 -> {
                // Шаг 2 (#32b): Видео сообщества — videoGet(ownerId=-groupId).
                // Список VideoThumbnail (существующий composable), тап → onVideoClick.
                // Пагинация по скроллу (loadMoreVideos триггерится выше).
                if (videosLoading && videos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (videosError != null && videos.isEmpty()) {
                    item {
                        val errMsg = videosError
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errMsg ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else if (videos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "В сообществе нет видео",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Видео (${videos.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    items(videos, key = { "${it.ownerId}_${it.id}" }) { video ->
                        VideoThumbnail(video = video, onClick = onVideoClick)
                    }
                    // Футер пагинации видео.
                    item {
                        when {
                            videosLoadingMore -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            videosEndReached -> {
                                Text(
                                    text = "Это все видео",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            3 -> {
                // #GROUP-CLIPS: Клипы сообщества — shortVideo.getOwnerVideos.
                // Сетка/список клипов (вертикальные карточки), тап → onVideoClick.
                if (clipsLoading && clips.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (clipsError != null && clips.isEmpty()) {
                    item {
                        val errMsg = clipsError
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errMsg ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else if (clips.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "В сообществе нет клипов",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Клипы (${clips.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    items(clips, key = { "clip_${it.ownerId}_${it.id}" }) { clip ->
                        ClipThumbnail(video = clip, onClick = onVideoClick)
                    }
                }
            }
            4 -> {
                // Шаг 3 (#32c): Музыка сообщества — audioGet(ownerId=-groupId).
                // AudioAttachmentList сам управляет play через PlayerConnection.playTrackList.
                item {
                    when {
                        tracksLoading && tracks.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                        tracksError != null && tracks.isEmpty() -> {
                            val errMsg = tracksError
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = errMsg ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        tracks.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "В сообществе нет музыки",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Музыка (${tracks.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            // AudioAttachmentList — общий компонент (FeedScreen/ProfileScreen).
                            // play/pause через PlayerConnection.playTrackList(tracks, startIndex).
                            re.pinok.ui.components.AudioAttachmentList(tracks = tracks)
                        }
                    }
                }
                // #AUDIO-PAGING-ALL: футер догрузки музыки сообщества.
                if (tracksHasMore) {
                    item(key = "community_music_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            5 -> {
                // Шаг 4 (#32d): Обсуждения — boardGetTopics(groupId).
                // Список тем; тап → onTopicClick → BoardTopicScreen.
                if (topicsLoading && topics.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (topicsError != null && topics.isEmpty()) {
                    item {
                        val errMsg = topicsError
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errMsg ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else if (topics.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "В сообществе нет обсуждений",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Обсуждения (${topics.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    items(topics, key = { it.id }) { topic ->
                        BoardTopicRow(
                            topic = topic,
                            onClick = { onTopicClick(groupId, topic.id, topic.title) },
                        )
                    }
                }
            }
        } // when (selectedTab)
    }
    } // PullToRefreshBox (Fix #85)

    // Fix #389 #SCROLL-TOP-PARITY: единая FAB-стрелка «наверх» — работает на
    // всех табах сообщества (стена/видео/обсуждения — один общий LazyList).
    ScrollToTopFab(
        listState = listState,
        modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp),
    )
    } // closes Box (Fix #389 #SCROLL-TOP-PARITY)

    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val viewer = photoViewerState.value
    if (viewer != null) {
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
        )
    }

    // ShareSheet: расширенный диалог «Поделиться».
    // #SHARE-18B: onSuccess → refreshWall — после репоста на стену сообщества
    // (или публикации вложения) стена сразу показывает новый пост,
    // как в PostDetailScreen (doRefresh).
    val sharing = repostPost.value
    if (sharing != null) {
        ShareSheet(
            post = sharing,
            onDismiss = { repostPost.value = null },
            onSuccess = { refreshWall() },
        )
    }

    // W36 #COMMUNITY-COMPOSER: диалог создания записи на стене сообщества.
    // Публикация: wall.post owner_id=-groupId + from_group/signed (переключатель
    // автора внутри диалога; «сообщество» гейтится canPostAsGroup=isAuthor —
    // admin_level>=2, волна 35). После публикации — refreshWall() (паттерн
    // #SHARE-18B): новый пост виден сразу, как в вебе.
    if (showComposer) {
        val composerContext = LocalContext.current
        CreatePostDialog(
            onDismiss = { showComposer = false },
            onSubmit = { _, _ -> },
            targetGroupId = groupId,
            canPostAsGroup = g.isAuthor,
            onSubmitGroup = { message, fromGroup, signed ->
                scope.launch {
                    try {
                        val postId = app.apiClient.wallPost(
                            message,
                            ownerId = -groupId,
                            fromGroup = fromGroup,
                            signed = signed,
                        )
                        if (postId > 0) {
                            AppLog.i(
                                "CommunityScreen",
                                "posted to wall: id=$postId, groupId=$groupId, fromGroup=$fromGroup, signed=$signed",
                            )
                            Toast.makeText(composerContext, "Запись опубликована", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            refreshWall()
                        } else {
                            Toast.makeText(composerContext, "Не удалось опубликовать запись", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("CommunityScreen", "wallPost(group) failed", e)
                        Toast.makeText(composerContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSubmitGroupWithAttachments = { message, fromGroup, signed, attachments ->
                scope.launch {
                    try {
                        val postId = app.apiClient.wallPostWithAttachments(
                            message = message,
                            attachments = attachments.joinToString(","),
                            ownerId = -groupId,
                            fromGroup = fromGroup,
                            signed = signed,
                        )
                        if (postId > 0) {
                            AppLog.i(
                                "CommunityScreen",
                                "posted to wall with ${attachments.size} attachments: id=$postId, groupId=$groupId, fromGroup=$fromGroup",
                            )
                            Toast.makeText(composerContext, "Запись опубликована", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            refreshWall()
                        } else {
                            Toast.makeText(composerContext, "Не удалось опубликовать запись", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        AppLog.e("CommunityScreen", "wallPostWithAttachments(group) failed", e)
                        Toast.makeText(composerContext, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityPostCard(
    post: Post,
    authorName: String,
    authorPhoto: String?,
    // #POST-SIGNER (волна 39): имя автора подписи (wall.get extended=1 →
    // signer_id → profileNames). null — пост без подписи/имя не пришло.
    signerName: String? = null,
    onVideoClick: (Video) -> Unit,
    onPostClick: (Post) -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // Sprint 2, P1-3 (#90): тап по репосту → диалог.
    onRepostClick: (Post) -> Unit = {},
    // S6-4: лайк.
    onLikeToggle: (ownerId: Long, postId: Long, isLiked: Boolean) -> Unit = { _, _, _ -> },
    // S6-4: комментарий → детальный экран поста.
    onCommentClick: (ownerId: Long, postId: Long) -> Unit = { _, _ -> },
    // S6-4: переопределение лайков для оптимистичного UI.
    likesOverride: Pair<Boolean, Int>? = null,
    // 22-B: карусель фото (общий SovaPrefs.feedCarouselEnabled, читается
    // на уровне экрана). Дефолт true — легаси-вызовы совместимы (паттерн 19-A).
    carouselEnabled: Boolean = true,
) {
    val photoAttachments = post.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
    val videoAttachments = post.attachments?.filter { it.type == "video" && it.video != null }.orEmpty()
    // #30 (audio attachments): рендер audio-вложений на стене сообщества.
    val audioAttachments = post.attachments?.filter { it.type == "audio" && it.audio != null }.orEmpty()
    val timeStr = post.date.toAbsoluteTime()
    val (isLiked, likeCount) = likesOverride
        ?: ((post.likes?.userLikes == 1) to (post.likes?.count ?: 0))
    val commentCount = post.comments?.count ?: 0
    val repostCount = post.reposts?.count ?: 0
    val viewCount = post.views?.count ?: 0

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
                    // #POST-SIGNER: подпись автора — имя под записью, как в VK web
                    // (тот же паттерн, что в FeedPostCard ленты).
                    val signer = signerName
                    if (signer != null) {
                        Text(
                            text = signer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (post.text.isNotBlank()) {
                // #POST-TEXT-EXPAND (Fix #345): длинный текст разворачивается на
                // месте; «Показать ещё» — только когда текст реально не влезает
                // (hasVisualOverflow). Тап по свёрнутому тексту = развернуть,
                // по развёрнутому = открыть пост (как раньше).
                var textExpanded by remember { mutableStateOf(false) }
                var textOverflowed by remember { mutableStateOf(false) }
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable {
                            if (textExpanded) {
                                onPostClick(post)
                            } else {
                                textExpanded = true
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(start = 12.dp, bottom = 6.dp)
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
            // #WALL-CLIPS: клипы (isClip) рендерим вертикальной карточкой,
            // обычные видео — горизонтальной VideoThumbnail (16:9).
            // Fix #366: >1 видео и включена карусель — общий PostVideoCarousel
            // (тот же флаг, что у PostPhotoGrid выше); иначе прежний стопк.
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
                        if (v.isClip) {
                            ClipThumbnail(video = v, onClick = onVideoClick)
                        } else {
                            VideoThumbnail(video = v, onClick = onVideoClick)
                        }
                    }
                }
            }
            // #30 (audio attachments): рендерим audio-вложения (как в FeedScreen).
            if (audioAttachments.isNotEmpty()) {
                re.pinok.ui.components.AudioAttachmentList(tracks = audioAttachments.mapNotNull { it.audio })
            }
            // #30 (playlists): audio_playlist вложения.
            val playlistAttachments = post.attachments?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }.orEmpty()
            playlistAttachments.forEach { att -> att.audioPlaylist?.let { re.pinok.ui.components.PlaylistAttachmentCard(playlist = it) } }
            // #WALL-CLIPS-REPOST: клипы на стене сообщества часто приходят РЕПОСТАМИ
            // (copy_history) — при этом сам пост пустой (текст + attachments пусты).
            // Раньше copy_history не рендерился → пост показывался только с заголовком
            // группы. Теперь рендерим первый copy_history как вложенную карточку.
            post.copyHistory?.firstOrNull()?.let { repost ->
                val rPhotos = repost.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
                val rVideos = repost.attachments?.filter { it.type == "video" && it.video != null }.orEmpty()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // Fix #365: репост-карточка ЦЕЛИКОМ открывает оригинальный
                        // пост (onPostClick → PostHolder.last + PostDetail, роут
                        // Community в SovaNavHost). clickable ПОСЛЕ clip — риппл по
                        // скруглению; вложенные клики (фото/видео ниже) перехватываются
                        // раньше — просмотр открывается как раньше, а не пост.
                        .clickable { onPostClick(repost) }
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (repost.text.isNotBlank()) {
                            // Fix #365: текст репоста — паттерн Fix #345 (см. пост
                            // выше): «Показать ещё» при реальном переполнении
                            // (hasVisualOverflow); тап по свёрнутому тексту =
                            // развернуть, по развёрнутому (и по короткому) = открыть
                            // пост целиком.
                            var repostTextExpanded by remember { mutableStateOf(false) }
                            var repostTextOverflowed by remember { mutableStateOf(false) }
                            Text(
                                text = repost.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable {
                                    if (repostTextOverflowed && !repostTextExpanded) {
                                        repostTextExpanded = true
                                    } else {
                                        onPostClick(repost)
                                    }
                                },
                                onTextLayout = { result ->
                                    if (!repostTextExpanded && result.hasVisualOverflow) repostTextOverflowed = true
                                },
                                maxLines = if (repostTextExpanded) Int.MAX_VALUE else 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (repostTextOverflowed && !repostTextExpanded) {
                                Text(
                                    text = "Показать ещё",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF1976D2),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { repostTextExpanded = true },
                                )
                            }
                        }
                        if (rPhotos.isNotEmpty()) {
                            // 22-B: общий PostPhotoGrid (см. пост выше).
                            PostPhotoGrid(
                                photos = rPhotos.mapNotNull { it.photo },
                                onPhotoClick = onPhotoClick,
                                carouselEnabled = carouselEnabled,
                            )
                        }
                        // Fix #366: несколько видео в репосте и включённая карусель
                        // → общий PostVideoCarousel; иначе прежний стопк (клип/видео).
                        if (carouselEnabled && rVideos.size > 1) {
                            PostVideoCarousel(
                                videos = rVideos.mapNotNull { it.video },
                                carouselEnabled = carouselEnabled,
                                onVideoClick = onVideoClick,
                            )
                        } else {
                            rVideos.forEach { att ->
                                val v = att.video
                                if (v != null) {
                                    if (v.isClip) {
                                        ClipThumbnail(video = v, onClick = onVideoClick)
                                    } else {
                                        VideoThumbnail(video = v, onClick = onVideoClick)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(
                    icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    count = likeCount,
                    tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onLikeToggle(post.ownerId, post.id, isLiked) },
                )
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = commentCount,
                    onClick = { onCommentClick(post.ownerId, post.id) },
                )
                Spacer(Modifier.width(8.dp))
                // Sprint 2, P1-3 (#90): репост кликабелен.
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
            // #VIDEO-TITLE-COMMENTS: название + счётчики (просмотры/комментарии).
            if (video.title.isNotBlank() || video.views > 0 || video.commentsCount > 0) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (video.title.isNotBlank()) {
                        // #POST-TEXT-EXPAND (Fix #345): длинное название видео
                        // разворачивается тапом (перехватывает клик карточки;
                        // тап по превью открывает видео как раньше, а тап по
                        // развёрнутому названию — тоже открывает видео).
                        var titleExpanded by remember { mutableStateOf(false) }
                        var titleOverflowed by remember { mutableStateOf(false) }
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                if (titleExpanded) {
                                    onClick(video)
                                } else {
                                    titleExpanded = true
                                }
                            },
                            onTextLayout = { result ->
                                if (!titleExpanded && result.hasVisualOverflow) titleOverflowed = true
                            },
                            maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (titleOverflowed && !titleExpanded) {
                            Text(
                                text = "Показать ещё",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1976D2),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { titleExpanded = true },
                            )
                        }
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
private fun ClipThumbnail(video: Video, onClick: (Video) -> Unit) {
    // #GROUP-CLIPS: вертикальный постер (first_frames → clipPosterUrl), как в веб-VK.
    val thumbUrl = video.clipPosterUrl ?: video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
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
            // Название снизу (как в clips-сетке VK).
            if (video.title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toCountString(), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

/** Formats member count in VK style: "1,2 млн", "345 тыс", "42". */
private fun formatMemberCount(n: Int): String {
    if (n >= 1_000_000) {
        val v = n / 1_000_000.0
        val formatted = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
        return "$formatted млн"
    }
    if (n >= 1_000) {
        val v = n / 1_000.0
        val formatted = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
        return "$formatted тыс"
    }
    return n.toString()
}

/**
 * Шаг 4 (#32d): строка темы обсуждения в списке вкладки «Обсуждения».
 *
 * Card с заголовком темы, счётчиком комментариев, датой создания и иконкой-стрелкой.
 * Закрытые темы помечаются badge "Закрыта".
 *
 * @param topic   Тема (BoardTopic)
 * @param onClick Callback тапа → onTopicClick(groupId, topicId, title)
 */
@Composable
private fun BoardTopicRow(
    topic: re.pinok.data.model.BoardTopic,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${topic.comments} комм.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (topic.created > 0) {
                        Text(
                            text = "• " + topic.created.toAbsoluteTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (topic.isClosed == 1) {
                        Text(
                            text = "• Закрыта",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Шаг 1 (#32a): вкладка «Фото» сообщества.
 *
 * Сетка 3 колонки (FlowRow) из PhotoItem.largestUrl. Тап → PhotoViewer
 * через onPhotoClick(listOf(urls), index).
 *
 * Без пагинации: photosGet возвращает до 50 за раз. Если фото > 50, добавим
 * пагинацию отдельным шагом (как loadMoreWall).
 *
 * @param photos      Список PhotoItem (из photosGet)
 * @param loading     Состояние загрузки
 * @param error       Текст ошибки (null = OK)
 * @param onPhotoClick Callback (urls, index) → открытие PhotoViewer
 */
@Composable
private fun CommunityPhotosTab(
    photos: List<re.pinok.data.model.PhotoItem>,
    loading: Boolean,
    error: String?,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    when {
        loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        error != null -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        photos.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "В сообществе нет фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        else -> {
            // Фильтруем только фото с валидным URL; largestUrl берёт самый большой size.
            val withUrl = photos.mapNotNull { p ->
                val url = p.largestUrl ?: return@mapNotNull null
                p to url
            }
            val allUrls = withUrl.map { it.second }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 3,
            ) {
                withUrl.forEachIndexed { index, (_, url) ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPhotoClick(allUrls, index) },
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
