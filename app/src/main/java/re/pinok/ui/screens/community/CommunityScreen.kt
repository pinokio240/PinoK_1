package re.pinok.ui.screens.community

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
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
import re.pinok.data.model.BoardTopic
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.Post
import re.pinok.data.model.Video
import re.pinok.data.model.Album
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.PhotoViewerPhotoItems
// POSTCARD-REFACTOR-PLAN (этап 2): вертикальная карточка клипа вынесена
// в общий ui/components/ClipThumbnail.kt.
import re.pinok.ui.components.ClipThumbnail
import re.pinok.ui.components.PagingFooter
import re.pinok.ui.components.PagingTrigger
import re.pinok.ui.components.PostCard
import re.pinok.ui.components.PostMenuItem
import re.pinok.ui.components.ScrollToTopFab
import re.pinok.ui.components.ServerOffset
import re.pinok.ui.components.ServerNextFrom
import re.pinok.ui.components.rememberPagedListState
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.components.ShareToChatSheet
import re.pinok.ui.components.VideoThumbnail
import re.pinok.ui.navigation.CommunityRestoreHolder
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime
import java.util.Locale
import android.widget.Toast
import android.content.Intent

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
    // P0.18 (Task 30): начальная вкладка при открытии CommunityScreen из stories
    // overlay (Фото автора → tab=1, Видео автора → tab=2). Default 0 (Записи).
    initialTab: Int = 0,
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
    // W38 (C2/C5/C4): приглашения / отложенные+предложения / адреса.
    // W39 (C9): журнал действий сообщества (web-only al-эндпоинт, HAR §12.2).
    // W41 (C3): кнопка действия сообщества.
    // W41 (C6): чаты сообщества + раздел «Сообщения».
    onMessagesClick: (groupId: Long) -> Unit = {},
    // C7 (#ADMIN-SECTIONS): разделы сообщества.
    // C8 (#ADMIN-COMMENTS): комментарии сообщества (фильтры + лента).
    onAdminCommentsClick: (groupId: Long) -> Unit = {},
    // ADMIN-EVENTS: «События» сообщества (notifications.getRedesign).
    onAdminEventsClick: (groupId: Long) -> Unit = {},
    // ADMIN-MENU-STRIKES: «Меню» (owners.*) и «Страйки» (strikeSystem.*).
    onAdminStrikesClick: (groupId: Long) -> Unit = {},
    // #ADMIN-WIRE-FIX: недостающие 10 колбэков из SovaNavHost → CommunityAdminBlock.
    onAdminPeopleClick: (groupId: Long, tab: String) -> Unit = { _, _ -> },
    onAdminLinksClick: (groupId: Long) -> Unit = {},
    onAdminInvitesClick: (groupId: Long) -> Unit = {},
    onAdminQueueClick: (groupId: Long) -> Unit = {},
    onAdminAddressesClick: (groupId: Long) -> Unit = {},
    onAdminEventLogClick: (groupId: Long) -> Unit = {},
    onCtaClick: (groupId: Long) -> Unit = {},
    onChatsClick: (groupId: Long) -> Unit = {},
    onSectionsClick: (groupId: Long) -> Unit = {},
    onAdminMenuClick: (groupId: Long) -> Unit = {},
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
    // #POST-SIGNER (волна 39): имена профилей из wall.get extended=1 —
    // подпись автора под постом сообщества (запрос пользователя: имя автора
    // под постом не писалось в приложении, а на веб-странице ВК писалось).
    // Копится между страницами пагинации (wallState fetcher мёржит profileNames).
    var signerNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // COMMUNITY-SEARCH: поиск постов на стене сообщества (wall.search).
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Post>>(emptyList()) }
    var searchSignerNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchOffset by remember { mutableStateOf(0) }
    var searchEndReached by remember { mutableStateOf(false) }
    // Fix #85: pull-to-refresh стены группы.
    var isRefreshing by remember { mutableStateOf(false) }

    // ФАЗА 0/1 (единый паттерн пагинации, эталон FeedScreen): стена/видео/файлы
    // переведены на PagedListState. Курсор — ServerOffset (offset += размер страницы),
    // конец списка = серверный курсор пришёл null. Собственные *LoadingMore/
    // *EndReached-флаги и старые snapshotFlow-триггеры удалены.
    // P0.47-REVERT: wall.get (offset-based) — единственный надёжный способ получить
    // записи сообщества. groups.getContentForTabs(tab="wall") через web-шлюз ВК
    // возвращает ПУСТОЙ ответ (24B) — метод НЕ поддерживает вкладку wall.
    // Доказано логом (2026-10-21): tabs=photos → 24B (пусто), tabs=short_videos → 497KB.
    // Только «Клипы» (short_videos) работают через getContentForTabs.
    val wallState = rememberPagedListState<Post>(
        fetcher = { cursor ->
            val offset = (cursor as? ServerOffset)?.offset ?: 0
            val wp = app.apiClient.wallGetExtended(ownerId = -groupId, count = 30, offset = offset)
            signerNames = signerNames + wp.profileNames
            val page = wp.posts.filter { it.id > 0 && it.ownerId != 0L }
            // Конец: rawCount==0 (пустой ответ) ИЛИ offset+rawCount >= totalCount.
            val next = if (wp.rawCount == 0 || (wp.totalCount > 0 && offset + wp.rawCount >= wp.totalCount))
                null else ServerOffset(offset + wp.rawCount)
            page to next
        },
        keyOf = { p -> "${p.ownerId}_${p.id}" },
        rememberKey = groupId,
    )
    val listState = rememberLazyListState()
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // PHOTO-MENU (PHOTO-MENU-ALBUMS): URL для ShareToChatSheet — открывается из
    // пункта «Отправить» в «⋮»-меню PhotoViewer. null → sheet закрыт.
    var shareToChatUrl by remember { mutableStateOf<String?>(null) }
    // #COMMUNITY-ALBUMS (PHOTO-MENU-ALBUMS Задача 2): state для вкладки «Альбомы».
    // Грузится photos.getAlbums(ownerId=-groupId) при переходе на вкладку 7.
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    var albumsLoaded by remember { mutableStateOf(false) }
    // Какой альбом сейчас открывается (загрузка его фото → PhotoViewer).
    // null = никакой. Хранит сам Album — для Toast-сообщений.
    var albumOpening by remember { mutableStateOf<Album?>(null) }
    // ALBUMS-IN-PHOTO: openedAlbum — открытый альбом (фото показываются вместо «Все фото»).
    var openedAlbum by remember { mutableStateOf<Album?>(null) }
    var albumPhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var albumPhotosLoading by remember { mutableStateOf(false) }
    var albumPhotosError by remember { mutableStateOf<String?>(null) }
    var albumPhotosRetryTick by remember { mutableStateOf(0) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // #ADMIN-POST-ACTIONS-EDIT: пост в режиме редактирования.
    var editingPost by remember { mutableStateOf<Post?>(null) }
    // #ADMIN-SOFT-DELETE: отложенное удаление поста (стрип + таймер 10с).
    var softDeletedPost by remember { mutableStateOf<Post?>(null) }
    var softDeleteSeconds by remember { mutableStateOf(0) }
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
    var selectedTab by remember { mutableStateOf(initialTab) }
    // P0.8 (Task 16): динамические вкладки на основе GroupSections.
    // GroupSections НЕ приходит в GroupInfo (поле отсутствует — VK API отдаёт
    // его только в groups.getSettings, который требует админ-прав). Грузим
    // отдельно; для не-админов → null → fallback на полный набор + «Файлы».
    // Логический индекс (0..6) ПОСТОЯНЕН — когда админ выключил секцию
    // (например photos=0), соответствующая пара исключается из списка, но
    // остальные индексы сохраняются → `when (selectedTab) 0/1/2/…` не нужно
    // перенумеровывать, существующие LaunchedEffect'ы по selectedTab==N не
    // ломаются (если вкладка не показана, по ней никогда не кликают).
    var groupSections by remember { mutableStateOf<VKApiClient.GroupSections?>(null) }
    var sectionsLoaded by remember { mutableStateOf(false) }

    // Шаг 1 (#32a): state для вкладки «Фото» — единый паттерн пагинации.
    // photosGet(ownerId=-groupId, albumId="wall", count=50); конец когда page.size < 50.
    val photosState = rememberPagedListState<PhotoItem>(
        fetcher = { cursor ->
            // P0.47-REVERT: photos.get(album="wall") — getContentForTabs(tab="photos") → 24B (пусто).
            val offset = (cursor as? ServerOffset)?.offset ?: 0
            val (list, totalCount) = app.apiClient.photosGetWithCount(ownerId = -groupId, albumId = "wall", count = 50, offset = offset)
            val next = if (list.isEmpty() || (totalCount > 0 && offset + list.size >= totalCount))
                null else ServerOffset(offset + list.size)
            list to next
        },
        keyOf = { p -> "photo_${p.ownerId}_${p.id}" },
        rememberKey = groupId,
    )

    // Шаг 2 (#32b): state для вкладки «Видео».
    // С пагинацией (видео «тяжелее», 30 за раз мало для активного сообщества).
    var videosLoaded by remember { mutableStateOf(false) }
    val videosState = rememberPagedListState(
        fetcher = { cursor ->
            // P0.47-REVERT: video.get — getContentForTabs(tab="videos") → пусто.
            val offset = (cursor as? ServerOffset)?.offset ?: 0
            val (list, totalCount) = app.apiClient.videoGetWithCount(ownerId = -groupId, count = 30, offset = offset)
            val next = if (list.isEmpty() || (totalCount > 0 && offset + list.size >= totalCount))
                null else ServerOffset(offset + list.size)
            list to next
        },
        keyOf = { v -> "${v.ownerId}_${v.id}" },
        rememberKey = groupId,
    )

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

    // #GROUP-CLIPS: state для вкладки «Клипы».
    // P0.47: cursor-based пагинация через groups.getContentForTabs (как VK web).
    // start_from → next_from, count=45. Конец: next_from == null.
    val clipsState = rememberPagedListState<Video>(
        fetcher = { cursor ->
            val startFrom = (cursor as? ServerNextFrom)?.value
            val (clips, nextFrom) = app.apiClient.groupsGetClipsForTabs(
                groupId = groupId,
                count = 45,
                startFrom = startFrom,
            )
            clips to (nextFrom?.let { ServerNextFrom(it) })
        },
        keyOf = { v -> "clip_${v.ownerId}_${v.id}" },
        rememberKey = groupId,
    )

    // Шаг 4 (#32d): state для вкладки «Обсуждения».
    // Единый паттерн пагинации: boardGetTopics(groupId, count=30), конец когда page.size < 30.
    val topicsState = rememberPagedListState<BoardTopic>(
        fetcher = { cursor ->
            // P0.47-REVERT: board.getTopics — getContentForTabs(tab="topics") → пусто.
            val offset = (cursor as? ServerOffset)?.offset ?: 0
            val (list, totalCount) = app.apiClient.boardGetTopicsWithCount(groupId = groupId, count = 30, offset = offset)
            val next = if (list.isEmpty() || (totalCount > 0 && offset + list.size >= totalCount))
                null else ServerOffset(offset + list.size)
            list to next
        },
        keyOf = { t -> "topic_${groupId}_${t.id}" },
        rememberKey = groupId,
    )

    // P0.8 (Task 16): state для вкладки «Файлы» (docs.get с ownerId=-groupId).
    // С пагинацией (как у видео): 30 за раз, догрузка по скроллу до конца.
    // docs.get работает для любого сообщества (в отличие от groups.getSettings,
    // который требует админ-прав) → вкладка «Файлы» показывается ВСЕГДА.
    var docsLoaded by remember { mutableStateOf(false) }
    val docsState = rememberPagedListState(
        fetcher = { cursor ->
            // P0.47-REVERT: docs.get — getContentForTabs(tab="docs") → пусто.
            val offset = (cursor as? ServerOffset)?.offset ?: 0
            val (list, totalCount) = app.apiClient.docsGetWithCount(count = 30, offset = offset, ownerId = -groupId)
            val next = if (list.isEmpty() || (totalCount > 0 && offset + list.size >= totalCount))
                null else ServerOffset(offset + list.size)
            list to next
        },
        keyOf = { d -> "doc_${d.ownerId}_${d.id}" },
        rememberKey = groupId,
    )

    // COMMUNITY-SEARCH: debounce 500ms → wall.search.
    // #DEBOUNCE-STABLE-v2: устранена ПРИЧИНА warning'а @ExperimentalCoroutinesApi.
    // Эволюция: .debounce(500) (@FlowPreview) → transformLatest+delay
    // (@ExperimentalCoroutinesApi) → ТЕПЕРЬ чистый LaunchedEffect+delay
    // (оба стабильные, никаких opt-in).
    //
    // Как работает debounce без Flow API: LaunchedEffect(searchQuery)
    // АВТОМАТИЧЕСКИ отменяет (cancel) предыдущий блок при смене key. Поэтому
    // быстрый ввод «абв» → 3 запуска, каждый отменяет предыдущий до истечения
    // delay(500). Только когда пользователь перестаёт печатать на 500мс,
    // delay завершается и выполняется wall.search. Семантика 1:1 с debounce.
    //
    // Дополнительно: snapshotFlow не нужен — LaunchedEffect(searchQuery)
    // сам ре-запускается только при изменении searchQuery (Compose
    // key-based re-launch эквивалентен distinctUntilChanged).
    LaunchedEffect(searchQuery) {
        val q = searchQuery
        if (q.isBlank()) {
            searchResults = emptyList()
            searchSignerNames = emptyMap()
            searchOffset = 0
            searchEndReached = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(500)
        searchLoading = true
        searchOffset = 0
        searchEndReached = false
        try {
            val result = app.apiClient.wallSearch(
                ownerId = -groupId, query = q, count = 20, offset = 0,
            )
            searchResults = result.posts
            searchSignerNames = result.profileNames
            signerNames = signerNames + result.profileNames
            searchOffset = result.posts.size
            if (result.posts.isEmpty()) searchEndReached = true
            AppLog.i("CommunityScreen", "wallSearch: ${result.posts.size} posts for «$q»")
        } catch (e: Exception) {
            AppLog.e("CommunityScreen", "wallSearch error", e)
        } finally {
            searchLoading = false
        }
    }

    // COMMUNITY-SEARCH: подгрузка следующих результатов поиска.
    fun loadMoreSearch() {
        if (searchLoading || searchEndReached || searchResults.isEmpty()) return
        scope.launch {
            searchLoading = true
            try {
                val rawOffset = searchOffset
                val result = app.apiClient.wallSearch(
                    ownerId = -groupId, query = searchQuery, count = 20, offset = rawOffset,
                )
                if (result.posts.isNotEmpty()) {
                    searchResults = (searchResults + result.posts).distinctBy { "${it.ownerId}_${it.id}" }
                    signerNames = signerNames + result.profileNames
                    searchSignerNames = searchSignerNames + result.profileNames
                }
                searchOffset = rawOffset + result.posts.size
                if (result.posts.isEmpty()) searchEndReached = true
            } catch (e: Exception) {
                AppLog.w("CommunityScreen", "loadMoreSearch failed: ${e.message}")
            } finally {
                searchLoading = false
            }
        }
    }

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
                    // #POST-SIGNER: extended=1 — имена авторов подписи мержит fetcher wallState.
                    wallState.loadFirst()
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
    // P0.8 (Task 16): подгрузка GroupSections (groups.getSettings) для
    // динамических вкладок. Метод требует админ-прав — для не-менеджеров
    // возвращает null → fallback на полный набор вкладок (+ «Файлы»).
    // Гейтинг по groupInfo.isManager — чтобы не делать обречённый запрос
    // для обычного участника. sectionsLoaded флаг защищает от повторов.
    LaunchedEffect(groupId, groupInfo) {
        if (sectionsLoaded) return@LaunchedEffect
        val g0 = groupInfo
        if (g0 == null) return@LaunchedEffect
        if (!g0.isManager) {
            // Не-админ → группы.getSettings вернёт access-denied; не делаем
            // лишний сетевой запрос. Fallback: tabs без фильтрации.
            sectionsLoaded = true
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val s = app.apiClient.groupsGetSettings(groupId)
                groupSections = s
                AppLog.i("CommunityScreen", "GroupSections loaded: " +
                    "wall=${s?.wall} photos=${s?.photos} video=${s?.video} " +
                    "audio=${s?.audio} docs=${s?.docs} topics=${s?.topics}")
            } catch (e: Exception) {
                AppLog.w("CommunityScreen", "GroupSections load failed: ${e.message}")
            } finally {
                sectionsLoaded = true
            }
        }
    }
    // P0.8 (Task 16): вычисление динамического списка вкладок.
    // Pair(логическийИндекс, заголовок): индекс 0=Записи, 1=Фото, 2=Видео,
    // 3=Клипы, 4=Музыка, 5=Обсуждения, 6=Файлы. Когда GroupSections явно
    // выключает секцию (значение 0 — disabled), исключаем пару из списка;
    // остальные индексы сохраняются. «Клипы» и «Файлы» — всегда (в VK API
    // нет групповой настройки clips, а docs.get работает для любого сообщества).
    val tabs: List<Pair<Int, String>> = remember(groupSections, sectionsLoaded) {
        val s = groupSections
        val has = s != null
        buildList {
            // «Записи» (wall): 0=выкл, 1=открытая, 2=ограниченная, 3=закрытая.
            // Всегда показываем вкладку: даже выключенная стена у сообщества
            // без записей — это базовая информация (пустой state внутри).
            // Явная non-null локальная переменная (sn) — smart-cast на var из
            // remember через lambda-захват не работает, делаем явную копию.
            if (!has) {
                add(0 to "Записи")
                add(1 to "Фото")
                add(2 to "Видео")
                add(3 to "Клипы")
                add(4 to "Музыка")
                add(5 to "Обсуждения")
            } else {
                val sn = s
                if (sn.wall != 0) add(0 to "Записи")
                if (sn.photos > 0) add(1 to "Фото")
                if (sn.video > 0) add(2 to "Видео")
                add(3 to "Клипы")
                if (sn.audio > 0) add(4 to "Музыка")
                if (sn.topics > 0) add(5 to "Обсуждения")
            }
            // «Файлы» (docs) — всегда: docs.get работает для любого сообщества
            // (не требует админ-прав), даже если админ выключил секцию docs=0.
            add(6 to "Файлы")
            // ALBUMS-IN-PHOTO: альбомы убраны из отдельной вкладки — теперь
            // показываются ВНУТРИ вкладки «Фото» (паттерн из ProfileScreen).
        }
    }
    // P0.8 (Task 16): если текущий selectedTab больше не в списке вкладок
    // (админ выключил секцию в другой сессии) — переключаемся на первую.
    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty() && tabs.none { it.first == selectedTab }) {
            selectedTab = tabs.first().first
        }
    }
    // #NAV-GROUP-VIDEO-RESTORE (Fix #344): однократная прокрутка стены к позиции
    // до смерти процесса. Индекс сохраняется/восстанавливается в одних координатах
    // LazyColumn (хедер + табы + посты) — пространство индексов идентично.
    LaunchedEffect(wallState.items) {
        val target = CommunityRestoreHolder.pendingScroll
        if (wallState.items.isNotEmpty() && target >= 0 && selectedTab == 0) {
            CommunityRestoreHolder.pendingScroll = -1
            listState.scrollToItem(target)
        }
    }
    // #NAV-GROUP-VIDEO-RESTORE (Fix #344): позиция стены пишется на каждой
    // остановке скролла и после каждой загрузки постов (wallState.items в ключах —
    // иначе после загрузки без скролла в prefs остался бы протухший индекс).
    // Process death dispose не вызывает, поэтому пишем постоянно, а не в onDispose.
    LaunchedEffect(listState.isScrollInProgress, selectedTab, wallState.items) {
        if (selectedTab == 0 && !listState.isScrollInProgress && wallState.items.isNotEmpty()) {
            app.prefs.setLastCommunityScroll(listState.firstVisibleItemIndex)
        }
    }

    // Fix #85: pull-to-refresh стены группы.
    fun refreshWall() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                // #POST-SIGNER: extended=1 — имена авторов подписи мержит fetcher wallState.
                wallState.loadFirst()
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "refreshWall failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Шаг 1 (#32a): первая загрузка фото при переходе на вкладку 1.
    // Фаза 0/1: PagedListState + показан список пуст → грузим первую страницу.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab == 1 && !photosState.loading && photosState.items.isEmpty() && photosState.cursor == null) {
            photosState.loadFirst()
        }
    }

    // ALBUMS-IN-PHOTO: загрузка фото альбома при openedAlbum (как в ProfileScreen).
    LaunchedEffect(openedAlbum, albumPhotosRetryTick) {
        val album = openedAlbum ?: return@LaunchedEffect
        albumPhotosLoading = true
        albumPhotosError = null
        try {
            albumPhotos = app.apiClient.photosGet(
                ownerId = -groupId,
                albumId = album.id.toString(),
                count = 200,
                offset = 0,
            )
            AppLog.i("CommunityScreen", "Album photos loaded: ${albumPhotos.size} for «${album.title}»")
        } catch (e: Exception) {
            AppLog.e("CommunityScreen", "Album photos load failed", e)
            albumPhotosError = "Ошибка: ${e.message}"
        } finally {
            albumPhotosLoading = false
        }
    }

    // Шаг 1 (#32a): канонический триггер пагинации фото, гейт по вкладке 1.
    PagingTrigger(listState = listState, state = photosState, enabled = selectedTab == 1)

    // Шаг 2 (#32b): первая загрузка видео сообщества при переходе на вкладку 2.
    // videosLoaded — грузим только один раз (переключение туда-обратно не догружает).
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 2 || videosLoaded) return@LaunchedEffect
        videosLoaded = true
        videosState.loadFirst()
    }

    // Шаг 2 (#32b): канонический триггер пагинации видео, гейт по вкладке 2.
    PagingTrigger(listState = listState, state = videosState, enabled = selectedTab == 2)

    // #GROUP-CLIPS: первая загрузка клипов сообщества при переходе на вкладку 3.
    // Фаза 0/1: PagedListState + показан список пуст → грузим первую страницу.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab == 3 && !clipsState.loading && clipsState.items.isEmpty() && clipsState.cursor == null) {
            clipsState.loadFirst()
        }
    }

    // #GROUP-CLIPS: канонический триггер пагинации клипов, гейт по вкладке 3.
    PagingTrigger(listState = listState, state = clipsState, enabled = selectedTab == 3)

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
                // #AUDIO-PAGING-HOLE (волна 41): НЕпустая страница (даже короткая)
                // ≠ конец списка — VK режет листинг на «битой» записи (прецедент
                // стопа 149/3239). Сбой (total=-1) — hasMore сохраняется для повтора.
                tracksHasMore = page.isNotEmpty() || total < 0
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
                val (total, page) = app.apiClient.audioGetWithCount(
                    count = 100, offset = tracksServerOffset, ownerId = -groupId)
                tracksServerOffset += page.size
                val fresh = page.filter { it.id > 0L && it.ownerId != 0L }
                    .filter { nv -> tracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                tracks = (tracks + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец;
                // сбой (total=-1) — сохраняем hasMore для повтора по скроллу.
                tracksHasMore = page.isNotEmpty() || total < 0
                AppLog.i("CommunityScreen", "Community music loadMore: +${fresh.size} (offset=$tracksServerOffset total=$total)")
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

    // Шаг 4 (#32d): первая загрузка тем обсуждений при переходе на вкладку 5.
    // Фаза 0/1: PagedListState + показан список пуст → грузим первую страницу.
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab == 5 && !topicsState.loading && topicsState.items.isEmpty() && topicsState.cursor == null) {
            topicsState.loadFirst()
        }
    }

    // Шаг 4 (#32d): канонический триггер пагинации тем, гейт по вкладке 5.
    PagingTrigger(listState = listState, state = topicsState, enabled = selectedTab == 5)

    // P0.8 (Task 16): загрузка документов сообщества при переходе на вкладку 6.
    // docs.get с ownerId=-groupId (VK convention для групп — отрицательный id).
    // docsLoaded — грузим только один раз (переключение туда-обратно не догружает).
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 6 || docsLoaded) return@LaunchedEffect
        docsLoaded = true
        docsState.loadFirst()
    }

    // P0.8 (Task 16): канонический триггер пагинации документов, гейт по вкладке 6.
    PagingTrigger(listState = listState, state = docsState, enabled = selectedTab == 6)

    // #COMMUNITY-ALBUMS (PHOTO-MENU-ALBUMS Задача 2): загрузка альбомов сообщества
    // при переходе на вкладку 7. photos.getAlbums(ownerId=-groupId, need_covers=1,
    // need_system=1) — возвращает список альбомов с обложками и счётчиком фото.
    // albumsLoaded — грузим один раз (как docs/videos); обновление — pull-to-refresh
    // через refreshWall() (общий триггер для стены; альбомы догрузятся при возврате).
    LaunchedEffect(selectedTab, groupId) {
        if (selectedTab != 1 || albumsLoaded) return@LaunchedEffect
        scope.launch {
            albumsLoading = true
            albumsError = null
            try {
                val list = app.apiClient.photosGetAlbums(ownerId = -groupId)
                albums = list
                AppLog.i("CommunityScreen", "Loaded ${albums.size} albums for group $groupId")
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "photosGetAlbums failed", e)
                albumsError = "Ошибка: ${e.message}"
            } finally {
                albumsLoading = false
                albumsLoaded = true
            }
        }
    }

    // Fix #85: канонический триггер пагинации стены, гейт по вкладке 0.
    PagingTrigger(listState = listState, state = wallState, enabled = selectedTab == 0)

    // COMMUNITY-SEARCH-PAGINATION: триггер догрузки результатов поиска.
    // wallState использует PagedListState + PagingTrigger, но поиск — отдельный
    // список searchResults: List<Post> + loadMoreSearch(), поэтому стандартный
    // PagingTrigger не подходит (требует PagedListState). Здесь ручной
    // эквивалент: snapshotFlow по layoutInfo → lastVisible >= total-3 →
    // loadMoreSearch(). Паттерн 1:1 с PagingTrigger (PagedListState.kt:199).
    // Гейты: selectedTab==0, searchQuery.isNotBlank() (активный поиск),
    // searchResults непуст, не loadMoreSearch-внутренние гейты (loading/end/
    // empty — проверяются внутри loadMoreSearch).
    LaunchedEffect(listState, selectedTab, searchQuery) {
        snapshotFlow {
            if (selectedTab != 0 || searchQuery.isBlank()) return@snapshotFlow false
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 3 && searchResults.isNotEmpty()
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMoreSearch() }
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
                                wallState.loadFirst()
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

    // #COMMUNITY-POST-DRY: общие обработчики для карточек постов стены И поиска.
    // Раньше блок «⋮»-меню (~75 строк) и onLikeToggle/onCommentClick дублировались
    // между обычной стеной и результатами поиска — поиск при этом ссылался на
    // несуществующие имена (onLikeToggle/onCommentClick/buildMenuItems/
    // onPostContextClick) и не компилировался. Здесь три локальные функции,
    // захватывающие общий scope (g, app, scope, likesState, editingPost,
    // softDeletedPost/softDeleteSeconds, refreshWall, onPostClick) — оба места
    // рендера поста (стена + поиск) используют их одинаково. Поведение стены
    // НЕ изменено: логика 1:1 с прежним inline-кодом (verified: optimistic like
    // по post.likes, scope.launch likesAdd/Delete, wallState.find заменён на
    // сам post — эквивалентно, т.к. PostCard вызывает колбэк с тем же post).
    fun buildCommunityPostMenuItems(post: Post): List<PostMenuItem> = buildList {
        // Закладка поста — всегда (для всех).
        add(
            PostMenuItem(
                label = if (post.isFavoriteBool) "Убрать из закладок" else "В закладки",
                icon = if (post.isFavoriteBool) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                onClick = {
                    scope.launch {
                        val ok = if (post.isFavoriteBool) {
                            app.apiClient.faveRemove("post", post.ownerId, post.id)
                        } else {
                            app.apiClient.faveAdd("post", post.ownerId, post.id)
                        }
                        AppLog.d("CommunityScreen", "bookmark toggle: ok=$ok")
                    }
                },
            )
        )
        if (g.isManager) {
            add(
                PostMenuItem(
                    label = "Редактировать",
                    icon = Icons.Outlined.Edit,
                    onClick = { editingPost = post; showComposer = true },
                )
            )
            add(
                PostMenuItem(
                    label = "Удалить",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    // #ADMIN-SOFT-DELETE: мягкое удаление со стрипом восстановления.
                    onClick = {
                        softDeletedPost = post
                        softDeleteSeconds = 10
                    },
                )
            )
            val commentsClosed = (post.comments?.canPost ?: 1) == 0
            add(
                PostMenuItem(
                    label = if (commentsClosed) "Открыть комментарии" else "Закрыть комментарии",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = {
                        scope.launch {
                            val ok = if (commentsClosed) {
                                app.apiClient.wallOpenComments(groupId, post.id)
                            } else {
                                app.apiClient.wallCloseComments(groupId, post.id)
                            }
                            AppLog.d("CommunityScreen", "comments toggle: ok=$ok")
                            if (ok) refreshWall()
                        }
                    },
                )
            )
            val pinned = post.isPinned == 1
            add(
                PostMenuItem(
                    label = if (pinned) "Убрать из «Главное»" else "Закрепить в «Главное»",
                    icon = if (pinned) Icons.Outlined.VisibilityOff else Icons.Outlined.PushPin,
                    onClick = {
                        scope.launch {
                            val ok = if (pinned) {
                                app.apiClient.ownersRemoveFromMainTab(groupId, post.id)
                            } else {
                                app.apiClient.ownersPinToMainTab(groupId, post.id)
                            }
                            AppLog.d("CommunityScreen", "pin toggle: ok=$ok")
                            if (ok) refreshWall()
                        }
                    },
                )
            )
        }
    }

    fun handleCommunityPostLike(post: Post, currentlyLiked: Boolean) {
        val key = "${post.ownerId}_${post.id}"
        val base = post.likes?.count ?: 0
        val prev = likesState[key] ?: (post.likes?.userLikes == 1) to base
        // Оптимистичное обновление.
        likesState[key] = (!prev.first) to (if (prev.first) prev.second - 1 else prev.second + 1)
        scope.launch {
            try {
                val newCount = if (currentlyLiked) {
                    app.apiClient.likesDelete("post", post.ownerId, post.id)
                } else {
                    app.apiClient.likesAdd("post", post.ownerId, post.id)
                }
                likesState[key] = (!currentlyLiked) to (if (newCount >= 0) newCount else likesState[key]?.second ?: prev.second)
            } catch (e: Exception) {
                AppLog.e("CommunityScreen", "like toggle failed for $key", e)
                likesState[key] = prev
            }
        }
    }

    fun handleCommunityPostComment(post: Post) {
        PostHolder.last = post
        // Fix #365: пробрасываем группы в PostHolder для имени сообщества
        // в PostDetailScreen (тот же паттерн, что в FeedScreen).
        PostHolder.lastGroups = mapOf(groupId to g)
        onPostClick(post)
    }

    // Fix #43: statusBarsPadding — контент не уходит под системную панель.
    // CommunityScreen в hasOwnTopBar списке SovaNavHost → глобальный TopAppBar
    // не рисуется, insets нужно применять самому. Аналогично ProfileScreen.
    // Fix #389 #SCROLL-TOP-PARITY: Box-обёртка — оверлей для FAB «наверх»
    // (один общий listState на стену/видео/обсуждения — FAB общий для всех табов).
    // #ADMIN-SOFT-DELETE: soft-delete tick — 1 секунда за шаг; на 0 — реальный wall.delete.
    LaunchedEffect(softDeletedPost, softDeleteSeconds) {
        val sd = softDeletedPost
        if (sd != null) {
            if (softDeleteSeconds <= 0) {
                scope.launch { app.apiClient.wallDelete(-groupId, sd.id) }
                softDeletedPost = null
                refreshWall()
            } else {
                kotlinx.coroutines.delay(1000)
                softDeleteSeconds -= 1
            }
        }
    }

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
                onLinksClick = onAdminLinksClick,
                onInvitesClick = onAdminInvitesClick,
                onQueueClick = onAdminQueueClick,
                onAddressesClick = onAdminAddressesClick,
                onEventLogClick = onAdminEventLogClick,
                onCtaClick = onCtaClick,
                onChatsClick = onChatsClick,
                onSectionsClick = onSectionsClick,
                onMenuClick = onAdminMenuClick,
                onMessagesClick = onMessagesClick,
                onCommentsClick = onAdminCommentsClick,
                onEventsClick = onAdminEventsClick,
                onStrikesClick = onAdminStrikesClick,
            )
        }
        item {
            // #30j (community tabs): ScrollableTabRow с динамическими вкладками.
            // Соответствует §9.8 VK_IMPORT_API.MD: group_tab_wall/photos/videos/audios/topics.
            // P0.8 (Task 16): tabs — List<Pair<Int, String>> (логический индекс → заголовок);
            // selectedTabIndex ищем через indexOfFirst, т.к. selectedTab хранит
            // логический индекс (0..6), а не позицию в списке (м.б. меньше 7,
            // если админ выключил часть секций).
            val selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            ) {
                tabs.forEach { (idx, title) ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
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
                            text = "Записи (${wallState.items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
        // #ADMIN-SOFT-DELETE: стрип восстановления над списком.
        item {
            if (softDeletedPost != null) {
                SoftDeleteStrip(
                    secondsLeft = softDeleteSeconds,
                    onRestore = { softDeletedPost = null },
                )
            }
        }
        // COMMUNITY-SEARCH: единое поисковое поле — ВСЕГДА рендерится с ОДНИМ
        // стабильным ключом "search_input". Раньше было два item с разными
        // ключами ("search_input" для expanded и "search_input_collapsed" для
        // пустого запроса) → при вводе первой буквы ключ менялся → Compose
        // уничтожал старый TextField и создавал новый → фокус терялся →
        // клавиатура закрывалась. Теперь поле одно, trailingIcon (кнопка «×»)
        // показывается условно внутри — фокус сохраняется при любом вводе.
        item(key = "search_input") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Поиск по записям…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
            )
        }
        // COMMUNITY-SEARCH: если есть поисковый запрос — показываем результаты
        // поиска вместо обычной стены.
        if (searchQuery.isNotBlank()) {
            if (searchLoading && searchResults.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (searchResults.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(searchResults.filter { it.id != softDeletedPost?.id }, key = { "search_${it.ownerId}_${it.id}" }) { post ->
                    val postKey = "${post.ownerId}_${post.id}"
                    CommunityPostCard(
                        post = post,
                        authorName = g.name,
                        authorPhoto = g.photo200 ?: g.photo100,
                        // SHOW-AUTHOR-ALL: «от [signer name]» — для всех постов с signerId.
                        signerName = post.signerId?.let { sid -> signerNames[sid]?.let { name -> "от $name" } },
                        // #COMMUNITY-POST-DRY: общие обработчики стены/поиска (см. defs выше).
                        menuItems = buildCommunityPostMenuItems(post),
                        onVideoClick = onVideoClick,
                        // Fix #365: пробрасываем группы в PostHolder для имени сообщества
                        // в PostDetailScreen (тот же паттерн, что в FeedScreen/wall-секции).
                        onPostClick = { p ->
                            PostHolder.lastGroups = mapOf(groupId to g)
                            onPostClick(p)
                        },
                        onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                        // 22-B: карусель фото (общий SovaPrefs-флаг).
                        carouselEnabled = carouselEnabled,
                        onRepostClick = { repostPost.value = it },
                        onLikeToggle = { _, _, currentlyLiked -> handleCommunityPostLike(post, currentlyLiked) },
                        onCommentClick = { _, _ -> handleCommunityPostComment(post) },
                        likesOverride = likesState[postKey],
                    )
                    // Разделитель — для визуальной консистентности с обычной стеной.
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                }
                // COMMUNITY-SEARCH-PAGINATION: футер пагинации результатов поиска.
                // Раньше loadMoreSearch() БЫЛ определён, но НИКТО его не вызывал —
                // поиск показывал только первую страницу (20 постов), остальные
                // «не прогружены». Теперь триггер ниже (searchPaginationTrigger)
                // вызывает loadMoreSearch() при скролле к концу. Футер показывает
                // статус догрузки / «Это все результаты».
                item(key = "search_paging_footer") {
                    when {
                        searchLoading -> {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        searchEndReached -> {
                            Text(
                                "Это все результаты",
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
        // Обычная стена (без поиска)
        items(wallState.items.filter { it.id != softDeletedPost?.id }, key = { "${it.ownerId}_${it.id}" }) { post ->
            val postKey = "${post.ownerId}_${post.id}"
            CommunityPostCard(
                post = post,
                authorName = g.name,
                authorPhoto = g.photo200 ?: g.photo100,
                // SHOW-AUTHOR-ALL: «от [signer name]» — для всех постов с signerId.
                signerName = post.signerId?.let { sid -> signerNames[sid]?.let { name -> "от $name" } },
                // #COMMUNITY-POST-DRY: общие обработчики стены/поиска (см. defs выше).
                // Поведение стены НЕ изменено: логика 1:1 с прежним inline-кодом.
                menuItems = buildCommunityPostMenuItems(post),
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
                // #COMMUNITY-POST-DRY: optimistic like + scope.launch
                // likesAdd/Delete вынесены в handleCommunityPostLike (общий для
                // стены и поиска). ownerId/postId из колбэка == post.ownerId/id.
                onLikeToggle = { _, _, currentlyLiked -> handleCommunityPostLike(post, currentlyLiked) },
                // #COMMUNITY-POST-DRY: PostHolder.last/lastGroups + onPostClick
                // вынесены в handleCommunityPostComment. wallState.items.find
                // заменён на сам post — эквивалентно (PostCard зовёт колбэк с тем
                // же post, что и items(...){ post -> }).
                onCommentClick = { _, _ -> handleCommunityPostComment(post) },
                likesOverride = likesState[postKey],
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        }
        // Фазa 0/1: общий футер пагинации стены.
        item(key = "wall_paging_footer") {
            PagingFooter(state = wallState, emptyText = "Это все записи")
        }
        if (wallState.items.isEmpty()) {
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
        } // else (обычная стена без поиска)
            } // when (selectedTab) 0 — Wall
            1 -> {
                // ALBUMS-IN-PHOTO: альбомы показываются ВНУТРИ вкладки «Фото»
                // (паттерн из ProfileScreen). Сначала горизонтальный список альбомов,
                // затем «Все фото» (photosGet wall album). Тап по альбому → открывает
                // фото из альбома через photos.get → PhotoViewer.
                // Альбомы грузятся через LaunchedEffect(selectedTab==1) выше.
                if (albums.isNotEmpty()) {
                    item(key = "community_albums_row") {
                        val ctx = LocalContext.current
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(albums, key = { "album_${it.ownerId}_${it.id}" }) { album ->
                                CommunityAlbumRow(
                                    album = album,
                                    isLoading = albumOpening?.id == album.id &&
                                        albumOpening?.ownerId == album.ownerId,
                                    onClick = {
                                        // ALBUMS-IN-PHOTO: открываем альбом как в профиле —
                                        // фото показываются в сетке вместо «Все фото» + кнопка «Назад».
                                        openedAlbum = album
                                    },
                                )
                            }
                        }
                    }
                }
                // ALBUMS-IN-PHOTO: если openedAlbum != null — показываем фото альбома
                // + кнопку «← Назад к альбомам» (как в ProfileScreen).
                val opened = openedAlbum
                if (opened != null) {
                    val albumUrls = albumPhotos.mapNotNull { it.largestUrl }
                    item(key = "album_back") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openedAlbum = null }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад к альбомам",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = opened.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (albumPhotosLoading && albumUrls.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (albumPhotosError != null && albumUrls.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(albumPhotosError ?: "", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else if (albumUrls.isNotEmpty()) {
                        val albumPhotoRows = albumPhotos.mapNotNull { p -> p.smallUrl?.let { url -> p to url } }.chunked(3)
                        itemsIndexed(albumPhotoRows, key = { idx, _ -> "album_photo_row_$idx" }) { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                rowItems.forEachIndexed { colIndex, (_, url) ->
                                    val globalIndex = rowIndex * 3 + colIndex
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { photoViewerState.value = albumUrls to globalIndex },
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
                                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                // Шаг 1 (#32a): Фото сообщества — photosGet(ownerId=-groupId, albumId="wall").
                // Сетка 3 колонки, тап → PhotoViewer (уже подключён ниже).
                // COMPACT-GRID-FIX: каждая строка — отдельный item() в LazyColumn,
                // чтобы PagingTrigger видел индексы и триггерил пагинацию.
                if (photosState.loading && photosState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                    }
                } else if (photosState.error != null && photosState.items.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(photosState.error ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (photosState.items.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("В сообществе нет фото", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    // PHOTO-QUALITY: превью в сетке — smallUrl (130px, type 'm') —
                    // быстро грузится даже на медленной сети. 3 колонки = маленькие превью,
                    // 130px достаточно. allUrls (для PhotoViewer) — largestUrl (оригинал).
                    val withUrl = photosState.items.mapNotNull { p -> p.smallUrl?.let { url -> p to url } }
                    val allUrls = photosState.items.mapNotNull { it.largestUrl }
                    val photoRows = withUrl.chunked(3)
                    itemsIndexed(photoRows, key = { idx, _ -> "photo_row_$idx" }) { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowItems.forEachIndexed { colIndex, (_, url) ->
                                val globalIndex = rowIndex * 3 + colIndex
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { photoViewerState.value = allUrls to globalIndex },
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
                            repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                    // Фаза 0/1: общий футер пагинации фото.
                    item(key = "photos_paging_footer") {
                        PagingFooter(state = photosState, emptyText = "Это все фото")
                    }
                }
                } // else (openedAlbum == null — показываем «Все фото»)
            }
            2 -> {
                // Шаг 2 (#32b): Видео сообщества — videoGet(ownerId=-groupId).
                // Список VideoThumbnail (существующий composable), тап → onVideoClick.
                // Пагинация — канонический PagingTrigger (гейт selectedTab == 2 выше).
                if (videosState.loading && videosState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (videosState.error != null && videosState.items.isEmpty()) {
                    item {
                        val errMsg = videosState.error
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
                } else if (videosState.items.isEmpty()) {
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
                                text = "Видео (${videosState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    // COMPACT-VIDEO-GRID: видео в сетке 2 колонки. Каждая строка —
                    // отдельный item() в LazyColumn, чтобы PagingTrigger видел индексы
                    // и триггерил пагинацию при скролле.
                    val videoRows = videosState.items.chunked(2)
                    itemsIndexed(videoRows, key = { idx, _ -> "video_row_$idx" }) { _, rowVideos ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowVideos.forEach { video ->
                                Box(modifier = Modifier.weight(1f)) {
                                    VideoThumbnail(
                                        video = video,
                                        onClick = onVideoClick,
                                        compact = true,
                                    )
                                }
                            }
                            repeat(2 - rowVideos.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    // Фаза 0/1: общий футер пагинации видео.
                    item(key = "videos_paging_footer") {
                        PagingFooter(state = videosState, emptyText = "Это все видео")
                    }
                }
            }
            3 -> {
                // #GROUP-CLIPS: Клипы сообщества — shortVideo.getOwnerVideos.
                // Сетка/список клипов (вертикальные карточки), тап → onVideoClick.
                // Пагинация — канонический PagingTrigger (гейт selectedTab == 3 выше).
                if (clipsState.loading && clipsState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (clipsState.error != null && clipsState.items.isEmpty()) {
                    item {
                        val errMsg = clipsState.error
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
                } else if (clipsState.items.isEmpty()) {
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
                                text = "Клипы (${clipsState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    // COMPACT-CLIP-GRID: клипы в сетке 2 колонки. Каждая строка —
                    // отдельный item() в LazyColumn, чтобы PagingTrigger работал.
                    val clipRows = clipsState.items.chunked(2)
                    itemsIndexed(clipRows, key = { idx, _ -> "clip_row_$idx" }) { _, rowClips ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowClips.forEach { clip ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ClipThumbnail(
                                        video = clip,
                                        onClick = onVideoClick,
                                        compact = true,
                                    )
                                }
                            }
                            repeat(2 - rowClips.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    // Фаза 0/1: общий футер пагинации клипов.
                    item(key = "clips_paging_footer") {
                        PagingFooter(state = clipsState, emptyText = "Это все клипы")
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
                // Пагинация — канонический PagingTrigger (гейт selectedTab == 5 выше).
                if (topicsState.loading && topicsState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (topicsState.error != null && topicsState.items.isEmpty()) {
                    item {
                        val errMsg = topicsState.error
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
                } else if (topicsState.items.isEmpty()) {
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
                                text = "Обсуждения (${topicsState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    items(topicsState.items, key = { "topic_${groupId}_${it.id}" }) { topic ->
                        BoardTopicRow(
                            topic = topic,
                            onClick = { onTopicClick(groupId, topic.id, topic.title) },
                        )
                    }
                    // Фаза 0/1: общий футер пагинации тем.
                    item(key = "topics_paging_footer") {
                        PagingFooter(state = topicsState, emptyText = "Тем больше нет")
                    }
                }
            }
            6 -> {
                // P0.8 (Task 16): Файлы сообщества — docs.get(ownerId=-groupId).
                // Список документов (иконка по типу / название / размер / дата),
                // тап → DownloadManager (как ChannelFilesDialog в ChatDetailScreen).
                // Пагинация — канонический PagingTrigger (гейт selectedTab == 6 выше).
                if (docsState.loading && docsState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (docsState.error != null && docsState.items.isEmpty()) {
                    item {
                        val errMsg = docsState.error
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
                } else if (docsState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "В сообществе нет файлов",
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
                                text = "Файлы (${docsState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    items(docsState.items, key = { "doc_${it.ownerId}_${it.id}" }) { doc ->
                        CommunityDocRow(doc = doc)
                    }
                    // Фаза 0/1: общий футер пагинации документов.
                    item(key = "docs_paging_footer") {
                        PagingFooter(state = docsState, emptyText = "Это все файлы")
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
    // #ADMIN-POST-ACTIONS-FLAT: диалог-меню PostActionsMenu удалён — его место
    // заняло плоское «⋮»-меню PostCard (см. формирование menuItems на уровне
    // экрана выше). Файл PostActionsMenu.kt остаётся в проекте, но больше не
    // вызывается.
    val viewer = photoViewerState.value
    if (viewer != null) {
        // PHOTO-QUALITY: используем PhotoViewer (List<String>) — работает для ВСЕХ
        // источников фото: посты (PostPhotoGrid allUrls), вкладка «Фото» (smallUrl),
        // альбомы (albumPhotos largestUrl). PhotoViewerPhotoItems требовал List<PhotoItem>
        // и не находил фото из постов (они в post.attachments, не в photosState.items).
        val shareContext = LocalContext.current
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
            onShare = { url ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                shareContext.startActivity(Intent.createChooser(send, "Поделиться"))
            },
            onSendToChat = { url -> shareToChatUrl = url },
        )
    }

    // PHOTO-MENU (PHOTO-MENU-ALBUMS): ShareToChatSheet для «Отправить» из PhotoViewer.
    // sharedText=url (без файла) — user выбирает диалог, messages.send шлёт текстом.
    val chatShareUrl = shareToChatUrl
    if (chatShareUrl != null) {
        ShareToChatSheet(
            sharedText = chatShareUrl,
            sharedStreamUri = null,
            sharedMimeType = null,
            onDismiss = { shareToChatUrl = null },
            onSuccess = { shareToChatUrl = null },
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
            onDismiss = { showComposer = false; editingPost = null },
            onSubmit = { _, _ -> },
            initialText = editingPost?.text ?: "",
            submitLabel = if (editingPost != null) "Сохранить" else "Опубликовать",
            targetGroupId = groupId,
            canPostAsGroup = g.isAuthor,
            onSubmitGroup = { message, fromGroup, signed ->
                scope.launch {
                    try {
                        val postId = if (editingPost != null) {
                            val ep = editingPost!!
                            if (app.apiClient.wallEdit(-groupId, ep.id, message)) ep.id else 0L
                        } else {
                            app.apiClient.wallPost(
                                message,
                                ownerId = -groupId,
                                fromGroup = fromGroup,
                                signed = signed,
                            )
                        }
                        if (postId > 0) {
                            AppLog.i(
                                "CommunityScreen",
                                "posted to wall: id=$postId, groupId=$groupId, fromGroup=$fromGroup, signed=$signed",
                            )
                            Toast.makeText(composerContext, "Запись опубликована", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            editingPost = null
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
                        val attachStr = attachments.joinToString(",")
                        val postId = if (editingPost != null) {
                            val ep = editingPost!!
                            if (app.apiClient.wallEdit(-groupId, ep.id, message, attachStr)) ep.id else 0L
                        } else {
                            app.apiClient.wallPostWithAttachments(
                                message = message,
                                attachments = attachStr,
                                ownerId = -groupId,
                                fromGroup = fromGroup,
                                signed = signed,
                            )
                        }
                        if (postId > 0) {
                            AppLog.i(
                                "CommunityScreen",
                                "posted to wall with ${attachments.size} attachments: id=$postId, groupId=$groupId, fromGroup=$fromGroup",
                            )
                            Toast.makeText(composerContext, "Запись опубликована", Toast.LENGTH_SHORT).show()
                            showComposer = false
                            editingPost = null
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
    // #ADMIN-POST-ACTIONS-FLAT: плоские пункты «⋮»-меню формируются на уровне
    // экрана (см. вызов ниже) и пробрасываются в общий PostCard. Дефолт пустой —
    // для вызовов без меню.
    menuItems: List<PostMenuItem> = emptyList(),
) {
    // POSTCARD-REFACTOR-PLAN (этап 3b): тело CommunityPostCard делегируется
    // единому общему PostCard (ui/components/PostCard.kt). Обёртка сохранена,
    // чтобы не менять вызов на строке ~1147 и работу screen-level state.
    // likesOverride (оптимистичное состояние лайка) PostCard сам читает из
    // post.likes → применяем его через копию поста с переопределённым likes.
    val displayPost = if (likesOverride != null) {
        val (overrideLiked, overrideCount) = likesOverride
        post.copy(
            likes = Post.Likes(
                count = overrideCount,
                userLikes = if (overrideLiked) 1 else 0,
                canLike = post.likes?.canLike ?: 1,
            )
        )
    } else post

    PostCard(
        post = displayPost,
        authorName = authorName,
        authorPhoto = authorPhoto,
        authorIsGroup = post.fromId < 0,
        signerName = signerName,
        // Community показывал toAbsoluteTime в карточке → сохраняем полный формат
        // (PostCard рендерит время в футере, а не в шапке — косметическое отличие).
        showFullDate = true,
        // закладки на карточке в Community нет.
        isBookmarked = null,
        onToggleBookmark = null,
        // доступ к g.verified в обёртке нет — verified-бейдж не показываем.
        verified = false,
        // тап по аватару/имени автора = открыть пост (как тап по тексту раньше).
        onAuthorClick = { onPostClick(post) },
        onPostClick = onPostClick,
        onVideoClick = onVideoClick,
        onPhotoClick = onPhotoClick,
        // like: адаптация (ownerId, postId, isLiked) → (Post). Третьим аргументом
        // передаём текущее состояние из того же post.likes, что PostCard рисует
        // (с учётом likesOverride через displayPost).
        onLikeToggle = { p -> onLikeToggle(p.ownerId, p.id, p.likes?.userLikes == 1) },
        // comment: адаптация (ownerId, postId) → (Post).
        onCommentClick = { p -> onCommentClick(p.ownerId, p.id) },
        onRepostClick = onRepostClick,
        onReaction = null,
        menuItems = menuItems,
        // в Community не было меток «Закреплённый пост»/«Реклама» — сохраняем поведение.
        showPinnedBadge = false,
        showAdBadge = false,
        // кнопки подписки в Community нет.
        showSubscribeButton = false,
        subscribed = null,
        onSubscribe = {},
        carouselEnabled = carouselEnabled,
        // Community скрывал 0-просмотры → показываем views, только если они есть.
        showViews = (post.views?.count ?: 0) > 0,
        // Community разворачивал текст после 10 строк (было maxLines=10 до «Показать ещё»).
        textMaxLines = 10,
    )
}

// POSTCARD-REFACTOR-PLAN (этап 1): VideoThumbnail и ActionIcon вынесены в общие
// re.pinok.ui.components (см. импорты выше). Сообщество использует разворачиваемое
// название (#POST-TEXT-EXPAND) — на стороне вызова titleExpandable=true.
// POSTCARD-REFACTOR-PLAN (этап 2): ClipThumbnail вынесен в общий
// re.pinok.ui.components.ClipThumbnail (логика 1:1, см. импорт выше).

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
 * Пагинация — единый паттерн (PagedListState + PagingTrigger + PagingFooter,
 * гейт selectedTab == 1): photosGet(ownerId=-groupId, albumId="wall", count=50).
 *
 * @param photos      Список PhotoItem (из photosState.items)
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
            // COMPACT-GRID-FIX: используем chunked rows вместо FlowRow+weight.
            // FlowRow с weight(1f) ломался при загрузке — все элементы делили ширину
            // поровну на всех строках, сетка «улетучивалась».
            // chunked(3) — фиксированные 3 колонки, Row с weight внутри каждой строки.
            val rows = withUrl.chunked(3)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                rows.forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rowItems.forEachIndexed { colIndex, (_, url) ->
                            val globalIndex = rowIndex * 3 + colIndex
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(allUrls, globalIndex) },
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
                        // Добиваем пустые ячейки в последней строке
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

// P0.8 (Task 16): строка документа во вкладке «Файлы» сообщества.
// Иконка по типу (image/gif/pdf/video/text) + название + ext/размер/тип/дата +
// кнопка «скачать» (DownloadManager, как ChannelFilesDialog в ChatDetailScreen).
// Тап по строке тоже запускает загрузку. Файлы с ext=gif/image показываются
// иконкой Image (preview не отрисован — отдельный viewer не заведён, только
// скачивание — паттерн ChannelFileRow).
@Composable
private fun CommunityDocRow(doc: re.pinok.data.model.DocFile) {
    val context = LocalContext.current

    fun downloadDoc() {
        val url = doc.url
        if (url.isBlank()) {
            Toast.makeText(context, "Ссылка на файл недоступна — обновите список", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val safeTitle = doc.title.ifBlank { "vk_doc_${doc.id}" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeTitle.${doc.ext}"
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Документы VK")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "VK/$fileName")
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            if (dm == null) {
                Toast.makeText(context, "Загрузка недоступна на этом устройстве", Toast.LENGTH_SHORT).show()
                return
            }
            dm.enqueue(request)
            Toast.makeText(context, "Загрузка началась: $fileName", Toast.LENGTH_SHORT).show()
            AppLog.i("CommunityScreen", "Doc download enqueued: $fileName (${doc.sizeLabel})")
        } catch (e: Exception) {
            AppLog.e("CommunityScreen", "Doc download failed", e)
            Toast.makeText(context, "Не удалось скачать: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { downloadDoc() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    doc.isImage -> Icons.Outlined.Image
                    doc.isGif -> Icons.Outlined.Image
                    doc.ext.lowercase() == "pdf" -> Icons.Outlined.PictureAsPdf
                    doc.type == 6 -> Icons.Outlined.Movie
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // EXT • размер • тип • дата (как в ChannelFilesDialog, + дата).
            val dateStr = if (doc.date > 0) " • " + doc.date.toAbsoluteTime() else ""
            Text(
                text = "${doc.ext.uppercase()} • ${doc.sizeLabel} • ${doc.typeLabel}$dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = { downloadDoc() }) {
            Icon(Icons.Outlined.Download, contentDescription = "Скачать")
        }
    }
}

/**
 * #COMMUNITY-ALBUMS (PHOTO-MENU-ALBUMS Задача 2): строка альбома во вкладке «Альбомы».
 *
 * Card с обложкой (thumbSrc), названием (title), счётчиком фото (size) и описанием.
 * Тап → photos.get(albumId) → открывает PhotoViewer с URL'ами фото альбома.
 *
 * @param album     Альбом (Album)
 * @param isLoading Идёт загрузка фото этого альбома → заменяем trailing-иконку
 *                  на прогресс-вертушку (защита от двойного тапа).
 * @param onClick   Callback тапа — грузит фото и открывает PhotoViewer (реализация
 *                  на уровне экрана, где доступны scope/app/groupId/photoViewerState).
 */
@Composable
private fun CommunityAlbumRow(
    album: Album,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    // ALBUM-CARD-VERTICAL: вертикальная Card — обложка сверху, подпись снизу
    // (паттерн VK web / профиль — альбомы в виде вертикальных карточек).
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            // Обложка альбома (16:9, RoundedCornerShape top).
            val coverUrl = album.thumbSrc
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Outlined.Image,
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
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // Подпись снизу.
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = album.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${album.size} фото",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
