// File: ui/screens/music/MusicScreen.kt
package re.pinok.ui.screens.music

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.data.model.AudioArtist
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.AudioSearchResult
import re.pinok.data.model.DownloadState
import re.pinok.util.toDurationString
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Track
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.util.AppLog
import kotlin.math.abs

/**
 * #TRACKS-CACHE (2026-08-01): Process-wide кэш списка «Мои треки».
 *
 * Раньше tracks/totalCount жили в `remember { mutableStateOf(...) }` внутри
 * composable-функции MusicScreen. При уходе с экрана (navigate в другой таб /
 * открытие плеера) composable покидает composition → remember очищается →
 * при возврате tracks сбрасывалось в emptyList() и заново грузилась 1-я страница
 * (50 треков). Пользователь видел: «533 / 3233» → ушёл → вернулся → «50 / 3233»
 * → фоновая подгрузка тянет обратно. Выглядело как «число снова обновляется».
 *
 * Теперь: tracks/totalCount инициализируются из этого синглтона, а после каждой
 * загрузки (первичная + loadMore) обновляют кэш. При возврате на экран список
 * показывается мгновенно из кэша (без reset-флэша), а фоновый reload проверяет
 * актуальность. TTL 5 минут — через 5 мин бездействия кэш считается устаревшим
 * и первичная загрузка идёт в сеть (на случай если библиотека изменилась).
 */
private object MusicTracksCache {
    @Volatile var tracks: List<Track> = emptyList()
    @Volatile var totalCount: Int = -1
    @Volatile var timestampMs: Long = 0L
    private const val TTL_MS = 5 * 60 * 1000L

    fun isFresh(): Boolean =
        timestampMs > 0L && (System.currentTimeMillis() - timestampMs) < TTL_MS

    fun snapshot(): Pair<List<Track>, Int> = Pair(tracks, totalCount)

    fun update(tracks: List<Track>, total: Int) {
        this.tracks = tracks
        this.totalCount = total
        this.timestampMs = System.currentTimeMillis()
    }
}

/**
 * Экран «Музыка» — нативный интерфейс VK Music (моделирован по SOVA V RE).
 *
 * Fix #62 → P0: переработан на 3 вкладки по реальному каталогу VK.
 *  — 3 вкладки: Моя музыка / Главная / Обзор
 *  — «Главная» и «Обзор» загружают catalog.getAudio
 *  — «Моя музыка»: меню (Недавнее/Плейлисты/Альбомы/Артисты/Скачанная музыка) +
 *    «Мои треки» (count, «Перемешать все», «Списки») + список треков
 *  — Бесконечная лента: при достижении конца списка подгружается следующая
 *    страница через audio.get(offset=tracks.size). Футер «Загрузка…» внизу.
 *  — Мини-плеер внизу тапабелен → открывает полноэкранный плеер.
 *
 * Воспроизведение: PlayerConnection → PlayerService (Media3).
 * Скачивание: TrackDownloadManager → MusicDownloadService.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun MusicScreen(
    onOpenPlayer: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    // #MUSIC-PORT: навигация на экраны музыкальной библиотеки.
    onOpenPlaylists: () -> Unit = {},
    onOpenAlbums: () -> Unit = {},
    onOpenArtists: () -> Unit = {},
    onOpenPlaylist: (ownerId: Long, playlistId: Long, accessKey: String?) -> Unit = { _, _, _ -> },
    onOpenAlbum: (ownerId: Long, albumId: Long, accessKey: String?) -> Unit = { _, _, _ -> },
    onOpenArtist: (slug: String, name: String) -> Unit = { _, _ -> },
    onShowAll: (sectionId: String, title: String) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // #TRACKS-CACHE: инициализируем из синглтона, чтобы при возврате на экран
    // список не сбрасывался в emptyList (fix «число снова обновляется»).
    val cached = MusicTracksCache.snapshot()
    var tracks by remember { mutableStateOf(cached.first) }
    var loading by remember { mutableStateOf(cached.first.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }

    // Fix #86: AudioMoreMenu + LyricsSheet (из ui/components/AudioMoreMenu.kt).
    // Состояние меню трека и открытой лирики — общее для всех VKTrackRow на экране.
    var moreMenuTrack by remember { mutableStateOf<Track?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var lyricsSheetTrackId by remember { mutableStateOf<Long?>(null) }
    var apiErrorMessage by remember { mutableStateOf<String?>(null) }
    // #TRACKS-CACHE: hasMore=true по умолчанию (как в оригинале). Если кэш свежий
    // и все треки уже загружены — loadMoreTracksSuspend сам вернёт false и остановится.
    var hasMore by remember { mutableStateOf(true) }
    var totalCount by remember { mutableStateOf(cached.second) } // #TRACKS-CACHE: init from cache
    var selectedTab by remember { mutableStateOf(0) } // 0=Моя музыка, 1=Главная, 2=Обзор

    // ─── Поиск (S5-1) ────────────────────────────────────────────────
    // Fix #266: расширенный поиск через audioSearchWithSections.
    // Возвращает треки + артистов + плейлисты одним запросом (catalog.getAudioSearch)
    // — работает с веб-токенами, которые не имеют доступа к audio.search.
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<AudioSearchResult?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    // Fix #269: поиск «опущен» во вкладку «Моя музыка» — поле всегда видно
    // вверху вкладки (inline OutlinedTextField в Column). Иконку поиска
    // из TopAppBar убрали — она была незаметна и пользователь жаловался
    // «поле поиска не вызывается». Теперь поиск активен = (tab==1 && query не пуст).
    // Локальная val без геттера: т.к. selectedTab/searchQuery — mutableStateOf,
    // чтение их здесь триггерит рекомпозицию, и searchActive пересчитывается.
    val searchActive = selectedTab == 0 && searchQuery.isNotBlank()

    // Fix #268: FocusRequester для автофокуса при переходе на вкладку «Моя музыка»
    // с непустым запросом (или при первом показе). Без этого поле не получает
    // фокус автоматически → клавиатура не открывается.
    val searchFocusRequester = remember { FocusRequester() }

    // Дебаунс 500мс для поискового запроса
    LaunchedEffect(searchQuery) {
        snapshotFlow { searchQuery }
            .debounce(500)
            .collect { query ->
                if (query.isBlank()) {
                    searchResult = null
                    return@collect
                }
                searchLoading = true
                try {
                    // Запуск на IO — catalog.getAudioSearch возвращает большой JSON
                    // (200-500KB с блоками tracks+artists+playlists).
                    val result = withContext(Dispatchers.IO) {
                        app.apiClient.audioSearchWithSections(query, count = 50)
                    }
                    searchResult = result
                } catch (e: Exception) {
                    AppLog.e("MusicScreen", "Search error", e)
                    searchResult = AudioSearchResult()
                } finally {
                    searchLoading = false
                }
            }
    }

    val playerState by PlayerConnection.playerState.collectAsState()
    val downloads by TrackDownloadManager.downloads.collectAsState()

    val pageSize = 50

    // ─── Первичная загрузка ────────────────────────────────────────────
    // Fix #252: убрали anti-pattern `LaunchedEffect(Unit) { scope.launch { ... } }`
    // — корутина в rememberCoroutineScope переживает LaunchedEffect и при уходе
    // экрана кидает ForgottenCoroutineScopeException. Теперь корутина живёт в
    // scope самого LaunchedEffect.
    LaunchedEffect(Unit) {
        // #TRACKS-CACHE: если кэш свежий (<5 мин) — не перегружаем первую
        // страницу, список уже показан из синглтона. Фоновая подгрузка ниже
        // (loadMore) доберёт остальные страницы если нужно. Это убирает
        // «число снова обновляется» при перезаходе на экран.
        if (MusicTracksCache.isFresh() && tracks.isNotEmpty()) {
            AppLog.i("MusicScreen", "Cache fresh (${tracks.size} tracks, total=$totalCount) — skip first-page reload")
            loading = false
            return@LaunchedEffect
        }
        loading = true
        apiErrorMessage = null
        hasMore = true
        try {
            val (total, raw) = app.apiClient.audioGetWithCount(count = pageSize, offset = 0)
            val firstPage = raw
                .filter { it.id > 0L && it.ownerId != 0L && !it.url.isNullOrBlank() }
                .distinctBy { "${it.ownerId}_${it.id}" }
            tracks = firstPage
            totalCount = total
            // hasMore: если totalCount известен — по нему; иначе по размеру страницы.
            hasMore = if (total > 0) firstPage.size < total else firstPage.size >= pageSize
            // #TRACKS-CACHE: сохраняем в синглтон для следующего входа.
            MusicTracksCache.update(firstPage, total)
            AppLog.i("MusicScreen", "First page: ${firstPage.size} tracks, total=$total, hasMore=$hasMore")
            if (firstPage.isEmpty()) {
                val errCode = app.apiClient.lastApiErrorCode
                val errStr = app.apiClient.lastApiError
                apiErrorMessage = when (errCode) {
                    3 -> "VK audio API недоступен для этого типа авторизации.\n\n" +
                        "Попробуйте выйти и войти через «Войти через VK (веб)» — " +
                        "это даст web-токен с доступом к audio.getCatalog."
                    15 -> "Доступ к аудио запрещён VK (error 15)."
                    else -> if (errStr != null) "Ошибка: $errStr" else "Нет музыки"
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #252: корректная отмена (пользователь ушёл со экрана)
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicScreen", "Failed to load tracks", e)
            apiErrorMessage = "Ошибка загрузки: ${e.message}"
        } finally {
            loading = false
        }
    }

    // ─── Функция подгрузки следующей страницы (suspend, для вызова из while-цикла) ──
    suspend fun loadMoreTracksSuspend(): Boolean {
        if (loadingMore || !hasMore || loading) return false
        loadingMore = true
        try {
            val currentSize = tracks.size
            // Fix #141: network call + JSON parsing (163KB+ for 50 tracks) MUST run on IO,
            // not Main. Previously this suspend fun ran on Main dispatcher (rememberCoroutineScope
            // returns Main) which blocked UI for 7+ seconds during audio.get — and indirectly
            // caused ForegroundServiceDidNotStartInTimeException because MusicDownloadService
            // couldn't call startForeground() in time (main thread blocked).
            // The state mutation (tracks = ...) still happens on Main via .copyOnWrite.
            val (total, raw) = withContext(Dispatchers.IO) {
                app.apiClient.audioGetWithCount(count = pageSize, offset = currentSize)
            }
            val next = raw
                .filter { it.id > 0L && it.ownerId != 0L && !it.url.isNullOrBlank() }
            if (next.isEmpty()) {
                hasMore = false
            } else {
                // Обновляем totalCount если API его вернул
                if (total > 0) totalCount = total
                val existingKeys = tracks.map { "${it.ownerId}_${it.id}" }.toHashSet()
                val fresh = next.filter { "${it.ownerId}_${it.id}" !in existingKeys }
                if (fresh.isNotEmpty()) {
                    tracks = tracks + fresh
                }
                // hasMore: по totalCount если известен, иначе по размеру страницы
                hasMore = if (total > 0) tracks.size < total else next.size >= pageSize
                // #TRACKS-CACHE: обновляем синглтон после каждой подгрузки —
                // при следующем входе на экран список будет актуальным.
                MusicTracksCache.update(tracks, totalCount)
                AppLog.d("MusicScreen", "Loaded page at offset=$currentSize: ${fresh.size} new, total=${tracks.size}/$total, hasMore=$hasMore")
            }
        } catch (e: Exception) {
            AppLog.e("MusicScreen", "loadMoreTracks failed", e)
        } finally {
            loadingMore = false
        }
        return hasMore
    }

    // ─── Функция подгрузки для вызова из snapshotFlow (fire-and-forget) ──
    fun loadMoreTracks() {
        scope.launch { loadMoreTracksSuspend() }
    }

    // ─── Тёмная тема как в нативном ВК Music ───────────────────────────
    val vkBlack = Color(0xFF0F0F10)
    val vkCard = Color(0xFF1C1C1E)
    val vkSurface = Color(0xFF242426)
    val vkTextPrimary = Color(0xFFFFFFFF)
    val vkTextSecondary = Color(0xFFA8A8AA)
    val vkAccent = Color(0xFF3D8BFF)

    val listState = rememberLazyListState()

    // ─── Фоновая подгрузка всех треков (только на вкладке «Моя музыка») ──
    // После первичной загрузки продолжает подгружать страницы в фоне,
    // пока hasMore=true. Пользователь может сразу слушать — не нужно
    // скроллить до конца чтобы подгрузились следующие треки.
    // tracks.size НЕ в ключе — snapshotFlow сам отслеживает изменения
    // через Compose snapshot reads. Иначе эффект перезапускается при
    // каждом добавлении треков → дублирование запросов.
    LaunchedEffect(listState, selectedTab) {
        if (selectedTab != 0) return@LaunchedEffect

        // 1) Скролл-пагинация: подгружаем при приближении к концу списка.
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            lastVisible >= total - 5 && total > 0
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (!loadingMore && hasMore && !loading) loadMoreTracks()
            }

        // 2) Фоновая предзагрузка: после первичной загрузки автоматически
        // подтягиваем следующие страницы, пока пользователь слушает.
        // Запускается параллельно со скролл-пагинацией.
    }

    // Фоновая предзагрузка: после первичной загрузки автоматически
    // подтягиваем ОГРАНИЧЕННОЕ количество страниц фоном.
    //
    // Fix #173: РАНЬШЕ цикл грузил ВСЕ 3233 трека (по ~10-13 за запрос) каждые
    // 0.7-1с = ~340 KB/s непрерывного трафика. При переключении WiFi→Mobile:
    // десятки in-flight audio.get отменялись → consecutiveNetworkErrors++ →
    // auto-offline / cascade failures. Теперь:
    //   1) НЕ больше MAX_PRELOAD_PAGES дополнительных страниц (~500 треков
    //      достаточно для большинства пользователей; остальное подгрузится
    //      по скроллу).
    //   2) Пауза 1500мс между страницами (вместо 300мс) — не мешает другим
    //      API-запросам (messages.getLongPollHistory, player HLS segment
    //      fetches и т.д.).
    //   3) Стоп если приложение офлайн или уже загрузило лимит.
    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) return@LaunchedEffect
        // Ждём завершения первичной загрузки
        while (loading) delay(100)
        if (!hasMore) return@LaunchedEffect
        delay(1200) // пауза чтобы не спамить API сразу после первичной загрузки
        // Fix #173: ограничиваем количество фоновых страниц.
        val maxPreloadPages = 10 // ~500 треков поверх первичной страницы (50)
        var pagesLoaded = 0
        while (hasMore && pagesLoaded < maxPreloadPages) {
            // Стоп если сеть пропала — нет смысла спамить отменёнными запросами.
            if (!app.networkObserver.isOnline()) {
                AppLog.i("MusicScreen", "Background preload: offline — stopping (loaded ${tracks.size} tracks)")
                break
            }
            val more = loadMoreTracksSuspend()
            if (!more) break
            pagesLoaded++
            delay(1500) // пауза между страницами чтобы не триггерить rate limit и не мешать другим API
        }
        AppLog.i("MusicScreen", "Background preload complete: ${tracks.size} tracks loaded (pages=$pagesLoaded, total=$totalCount, stopped=${if (pagesLoaded >= maxPreloadPages) "limit" else "end"})")
    }

    // Fix #269: поиск «опущен» во вкладку «Моя музыка» — поле всегда видно
    // вверху вкладки как inline OutlinedTextField (см. MusicMyTracksTab).
    // Иконку поиска из TopAppBar убрали — она была незаметна, и пользователь
    // жаловался «поле поиска не вызывается». Теперь TopAppBar остаётся чистым
    // (только заголовок «Музыка»), а поиск живет внутри вкладки.
    // Сбрасываем поиск при уходе с экрана.
    DisposableEffect(Unit) {
        onDispose {
            searchQuery = ""
            searchResult = null
        }
    }

    // ─── FAB «наверх» — как в ленте, появляется при скролле вниз ────
    val showScrollToTopFab by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(vkBlack)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vkBlack),
    ) {
        // ─── Шапка: только вкладки ────────────────────────────────────
        MusicTabsBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            textColor = vkTextPrimary,
            secondaryColor = vkTextSecondary,
        )

        // ─── Inline поле поиска (Fix #269) ────────────────────────────
        // Поле всегда видно вверху вкладки «Моя музыка». При вводе текста
        // searchActive становится true → ниже показываются результаты поиска
        // вместо основного контента вкладки. Поле остаётся видимым всегда,
        // чтобы пользователь мог изменить запрос.
        if (selectedTab == 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isEmpty()) searchResult = null
                },
                placeholder = {
                    Text(
                        "Поиск музыки…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = vkTextSecondary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = vkTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResult = null
                        }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Очистить",
                                modifier = Modifier.size(18.dp),
                                tint = vkTextSecondary,
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = vkTextSecondary.copy(alpha = 0.2f),
                    focusedBorderColor = vkAccent,
                    unfocusedContainerColor = vkCard.copy(alpha = 0.5f),
                    focusedContainerColor = vkCard,
                    cursorColor = vkAccent,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = vkTextPrimary),
            )
        }

        // ─── Поиск: результаты вместо контента вкладок (S5-1) ────────
        // Fix #266: расширенный UI с секциями Артисты / Плейлисты / Треки.
        // Если catalog.getAudioSearch вернул артистов — показываем их в горизонтальном
        // слайдере сверху, плейлисты — ниже, затем треки списком.
        if (searchActive) {
            val resultTracks = searchResult?.tracks ?: emptyList()
            val resultArtists = searchResult?.artists ?: emptyList()
            val resultPlaylists = searchResult?.playlists ?: emptyList()
            val hasAny = resultTracks.isNotEmpty() ||
                resultArtists.isNotEmpty() ||
                resultPlaylists.isNotEmpty()

            if (searchLoading && !hasAny) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = vkAccent)
                }
            } else if (searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Введите запрос для поиска", color = vkTextSecondary, fontSize = 14.sp)
                }
            } else if (!hasAny) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ничего не найдено", color = vkTextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("По запросу «$searchQuery»", color = vkTextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (playerState.currentTrack != null) 100.dp else 16.dp),
                ) {
                    // ─── Секция «Артисты» (горизонтальный слайдер) ───
                    if (resultArtists.isNotEmpty()) {
                        item(key = "search_section_artists") {
                            SearchSectionHeader(
                                title = "Артисты",
                                count = resultArtists.size,
                                textColor = vkTextPrimary,
                                secondaryColor = vkTextSecondary,
                            )
                        }
                        item(key = "search_artists_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(resultArtists, key = { "artist_${it.id}" }) { artist ->
                                    SearchArtistCard(
                                        artist = artist,
                                        cardColor = vkCard,
                                        textColor = vkTextPrimary,
                                        secondaryColor = vkTextSecondary,
                                    )
                                }
                            }
                        }
                        item(key = "search_artists_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = vkTextSecondary.copy(alpha = 0.15f),
                            )
                        }
                    }

                    // ─── Секция «Плейлисты» (горизонтальный слайдер) ───
                    if (resultPlaylists.isNotEmpty()) {
                        item(key = "search_section_playlists") {
                            SearchSectionHeader(
                                title = "Плейлисты",
                                count = resultPlaylists.size,
                                textColor = vkTextPrimary,
                                secondaryColor = vkTextSecondary,
                            )
                        }
                        item(key = "search_playlists_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(resultPlaylists, key = { "pl_${it.ownerId}_${it.id}" }) { pl ->
                                    SearchPlaylistCard(
                                        playlist = pl,
                                        cardColor = vkCard,
                                        textColor = vkTextPrimary,
                                        secondaryColor = vkTextSecondary,
                                    )
                                }
                            }
                        }
                        item(key = "search_playlists_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = vkTextSecondary.copy(alpha = 0.15f),
                            )
                        }
                    }

                    // ─── Секция «Треки» (вертикальный список) ───
                    if (resultTracks.isNotEmpty()) {
                        item(key = "search_section_tracks") {
                            SearchSectionHeader(
                                title = "Треки",
                                count = resultTracks.size,
                                textColor = vkTextPrimary,
                                secondaryColor = vkTextSecondary,
                            )
                        }
                    }
                    items(resultTracks, key = { "track_${it.ownerId}_${it.id}" }) { track ->
                        val current = playerState.currentTrack
                        val isCurrent = current != null &&
                            track.id == current.id &&
                            track.ownerId == current.ownerId
                        val isPlaying = isCurrent && playerState.isPlaying
                        val dl = downloads[track.id]
                        VKTrackRow(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            downloadState = dl,
                            cardColor = vkCard,
                            accentColor = vkAccent,
                            textColor = vkTextPrimary,
                            secondaryColor = vkTextSecondary,
                            onPlayClick = {
                                val isCur = track.id == playerState.currentTrack?.id &&
                                    track.ownerId == playerState.currentTrack?.ownerId
                                if (isCur) {
                                    PlayerConnection.togglePlayPause()
                                } else {
                                    PlayerConnection.playTrackList(resultTracks, resultTracks.indexOf(track))
                                }
                            },
                            onDownloadClick = {
                                val dl = downloads[track.id]
                                if (dl?.isCompleted == true || dl?.isInProgress == true) {
                                    TrackDownloadManager.removeDownload(track.id)
                                } else {
                                    TrackDownloadManager.enqueueDownload(track)
                                }
                            },
                            onMoreClick = {
                                moreMenuTrack = track
                                moreMenuExpanded = true
                            },
                        )
                    }
                    if (searchLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = vkAccent,
                                )
                            }
                        }
                    }
                }
            }
        } else {

        // #PLAYLIST-OPEN: открыть плейлист каталога — грузим треки через
        // audio.getPlaylistById и запускаем воспроизведение с первого.
        fun openPlaylistAndPlay(pl: re.pinok.data.model.CatalogPlaylist) {
            scope.launch {
                try {
                    val (_, plTracks) = app.apiClient.audioGetPlaylistById(
                        playlistId = pl.id,
                        ownerId = pl.ownerId,
                        accessKey = pl.accessKey,
                    )
                    val filtered = plTracks.filter { it.id > 0L && !it.url.isNullOrBlank() }
                    if (filtered.isNotEmpty()) {
                        PlayerConnection.playTrackList(filtered, 0)
                        android.widget.Toast.makeText(
                            app.applicationContext,
                            "Плейлист «${pl.title}» — ${filtered.size} треков",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            app.applicationContext,
                            "Плейлист пуст или недоступен",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                } catch (e: Exception) {
                    AppLog.e("MusicScreen", "openPlaylistAndPlay failed: ${e.message}")
                }
            }
        }

        // ─── Контент по выбранной вкладке ──────────────────────────────
        when (selectedTab) {
            0 -> MusicMyTracksTab(
                tracks = tracks,
                totalCount = totalCount,
                loading = loading,
                loadingMore = loadingMore,
                hasMore = hasMore,
                listState = listState,
                playerState = playerState,
                downloads = downloads,
                cardColor = vkCard,
                textColor = vkTextPrimary,
                secondaryColor = vkTextSecondary,
                accentColor = vkAccent,
                apiErrorMessage = apiErrorMessage,
                onOpenPlaylists = onOpenPlaylists,
                onOpenAlbums = onOpenAlbums,
                onOpenArtists = onOpenArtists,
                onShuffleAll = { PlayerConnection.shuffleAll(tracks) },
                onPlayTrack = { track ->
                    val isCurrent = track.id == playerState.currentTrack?.id &&
                        track.ownerId == playerState.currentTrack?.ownerId
                    if (isCurrent) {
                        PlayerConnection.togglePlayPause()
                    } else {
                        PlayerConnection.playTrackList(tracks, tracks.indexOf(track))
                    }
                },
                onDownloadToggle = { track ->
                    val dl = downloads[track.id]
                    if (dl?.isCompleted == true || dl?.isInProgress == true) {
                        TrackDownloadManager.removeDownload(track.id)
                    } else {
                        TrackDownloadManager.enqueueDownload(track)
                    }
                },
                onMoreClick = { track ->
                    moreMenuTrack = track
                    moreMenuExpanded = true
                },
            )
            1 -> MusicHomeTab(
                tracks = tracks,
                loading = loading,
                playerState = playerState,
                downloads = downloads,
                cardColor = vkCard,
                textColor = vkTextPrimary,
                secondaryColor = vkTextSecondary,
                accentColor = vkAccent,
                apiErrorMessage = apiErrorMessage,
                onPlayTrack = { idx ->
                    if (tracks.isNotEmpty()) {
                        PlayerConnection.playTrackList(tracks, idx.coerceIn(0, tracks.lastIndex))
                    }
                },
                onToggleTrack = { track ->
                    val isCurrent = track.id == playerState.currentTrack?.id &&
                        track.ownerId == playerState.currentTrack?.ownerId
                    if (isCurrent) {
                        PlayerConnection.togglePlayPause()
                    } else {
                        PlayerConnection.playTrackList(tracks, tracks.indexOf(track))
                    }
                },
                onDownloadToggle = { track ->
                    val dl = downloads[track.id]
                    if (dl?.isCompleted == true || dl?.isInProgress == true) {
                        TrackDownloadManager.removeDownload(track.id)
                    } else {
                        TrackDownloadManager.enqueueDownload(track)
                    }
                },
                onPlaylistClick = { pl -> openPlaylistAndPlay(pl) },
                onShowAll = onShowAll,
            )
            2 -> DiscoverTab(
                textColor = vkTextPrimary,
                secondaryColor = vkTextSecondary,
                accentColor = vkAccent,
                cardColor = vkCard,
                playerState = playerState,
                downloads = downloads,
                onPlaylistClick = { pl -> openPlaylistAndPlay(pl) },
                onShowAll = onShowAll,
            )
            // #MUSIC-UPDATES: «Обновления» — catalog.getSection(updates),
            // section-id из «музыка_Обновления.html». Рендер как DiscoverTab.
            3 -> DiscoverTab(
                textColor = vkTextPrimary,
                secondaryColor = vkTextSecondary,
                accentColor = vkAccent,
                cardColor = vkCard,
                playerState = playerState,
                downloads = downloads,
                onPlaylistClick = { pl -> openPlaylistAndPlay(pl) },
                onShowAll = onShowAll,
                section = "updates",
            )
            // #MUSIC-RADIO: «Радио» — список радиостанций.
            // Section-id радио пока не найден в снапшотах → заглушка.
            // Инфраструктура готова: AudioRadioStation, audio.radioGetById,
            // audio.followRadioStation. Нужен section-id из живого m.vk.ru
            // после JS: m.vk.ru/audios<id>?section=radiostations.
            4 -> RadioStubTab(
                textColor = vkTextPrimary,
                secondaryColor = vkTextSecondary,
                accentColor = vkAccent,
                cardColor = vkCard,
            )
        }
        } // end else (!searchActive)

        // ─── Баннер ошибки воспроизведения (Fix #59) ───
        val errorMessage = playerState.error
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB3261E).copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { PlayerConnection.next() }) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Следующий",
                        tint = Color.White,
                    )
                }
            }
        }

        // Fix #71: мини-плеер удалён из MusicScreen — теперь он глобальный
        // (рендерится в SovaNavHost над NavigationBar на всех экранах).
        // На экране Музыки он тоже виден через SovaNavHost.

        // Fix #86: AudioMoreMenu — контекстное меню трека.
        // Состояние moreMenuTrack/moreMenuExpanded меняется из VKTrackRow.onMoreClick.
        // DropdownMenu рендерится как overlay — достаточно один Box с якорем.
        moreMenuTrack?.let { track ->
            Box(modifier = Modifier.fillMaxWidth()) {
                re.pinok.ui.components.AudioMoreMenu(
                    track = track,
                    expanded = moreMenuExpanded,
                    onDismiss = {
                        moreMenuExpanded = false
                        moreMenuTrack = null
                    },
                    isOwn = track.ownerId == app.exchangeAuthRepository.userId(),
                    onAdd = {
                        scope.launch {
                            try {
                                app.apiClient.audioAdd(track.id, track.ownerId)
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "audioAdd error", e)
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            try {
                                app.apiClient.audioDelete(track.id, track.ownerId)
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "audioDelete error", e)
                            }
                        }
                    },
                    onRestore = {
                        scope.launch {
                            try {
                                app.apiClient.audioRestore(track.id, track.ownerId)
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "audioRestore error", e)
                            }
                        }
                    },
                    onShare = {
                        // #SHARE-AUDIO (2026-08-03): реализован Android ACTION_SEND
                        // chooser с текстом "Title — Artist\nhttps://vk.com/audio...":
                        val t = track
                        scope.launch {
                            try {
                                val shareText = "${t.title} — ${t.artist}\nhttps://vk.com/audio${t.ownerId}_${t.id}"
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val ctx = app.applicationContext
                                ctx.startActivity(
                                    android.content.Intent.createChooser(intent, "Поделиться")
                                        .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                )
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "share audio failed", e)
                            }
                        }
                    },
                    // #FAVE-AUDIO (2026-08-03): "В закладки" — fave.add(type="audio").
                    onBookmark = {
                        val t = track
                        scope.launch {
                            var ok = false
                            try {
                                ok = app.apiClient.faveAdd("audio", t.ownerId, t.id)
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "faveAdd audio error", e)
                            }
                            // Toast-фидбек пользователю (зелёный = добавлено).
                            try {
                                val msg = if (ok) "Добавлено в закладки" else "Не удалось добавить в закладки"
                                android.widget.Toast.makeText(app.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                    },
                    onCopyLink = {
                        // Формируем прямую ссылку и кладём в ClipboardManager.
                        val link = "https://vk.com/audio${track.ownerId}_${track.id}"
                        try {
                            val ctx = app.applicationContext
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("VK audio", link))
                            AppLog.i("MusicScreen", "Audio link copied: $link")
                        } catch (e: Exception) {
                            AppLog.e("MusicScreen", "copyLink error", e)
                        }
                    },
                    onShowLyrics = {
                        track.lyricsId?.let { lyricsSheetTrackId = it }
                    },
                    onShowRecommendations = {
                        scope.launch {
                            try {
                                // Возвращает Pair<totalCount, tracks> — берём только треки.
                                val (_, recs) = app.apiClient.audioGetRecommendations(count = 30)
                                if (recs.isNotEmpty()) {
                                    PlayerConnection.playTrackList(recs, 0)
                                }
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "getRecommendations error", e)
                            }
                        }
                    },
                    onDislike = {
                        scope.launch {
                            try {
                                app.apiClient.audioAddDislike(
                                    listOf("${track.ownerId}_${track.id}")
                                )
                            } catch (e: Exception) {
                                AppLog.e("MusicScreen", "addDislike error", e)
                            }
                        }
                    },
                )
            }
        }

        // Fix #86: LyricsSheet — открыт когда lyricsSheetTrackId != null.
        lyricsSheetTrackId?.let { lid ->
            re.pinok.ui.components.LyricsSheet(
                lyricsId = lid,
                onDismiss = { lyricsSheetTrackId = null },
            )
        }
    } // end Column

        // ─── FAB «наверх» ────────────────────────────────────────────
        if (showScrollToTopFab && selectedTab == 0 && !searchActive) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = vkSurface,
                contentColor = vkTextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
            }
        }
    } // end outer Box
}

// ─── Header с 5 вкладками ────────────────────────────────────────────────────

@Composable
private fun MusicTabsBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    textColor: Color,
    secondaryColor: Color,
) {
    val tabs = listOf("Моя музыка", "Главная", "Обзор", "Обновления", "Радио")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F10))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Fix #258: inline поиск убран — теперь в TopAppBar.
        // Горизонтальный скроллируемый ряд вкладок
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(tabs.size) { idx ->
                MusicTab(
                    name = tabs[idx],
                    isActive = selectedTab == idx,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    onClick = { onTabSelected(idx) },
                )
            }
        }
    }
}

@Composable
private fun MusicTab(
    name: String,
    isActive: Boolean,
    textColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = name,
            color = if (isActive) textColor else secondaryColor,
            fontSize = 15.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 24.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) Color(0xFF3D8BFF) else Color.Transparent),
        )
    }
}

// ─── Вкладка «Моя музыка»: меню + мои треки с пагинацией ─────────────────────

@Composable
private fun MusicMyTracksTab(
    tracks: List<Track>,
    totalCount: Int,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    playerState: re.pinok.data.model.PlayerState,
    downloads: Map<Long, DownloadState>,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    apiErrorMessage: String?,
    onOpenPlaylists: () -> Unit = {},
    onOpenAlbums: () -> Unit = {},
    onOpenArtists: () -> Unit = {},
    onShuffleAll: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onDownloadToggle: (Track) -> Unit,
    onMoreClick: (Track) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val currentTrack = playerState.currentTrack
    val hasCurrentInList = currentTrack != null &&
        tracks.any { it.id == currentTrack.id && it.ownerId == currentTrack.ownerId }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (playerState.currentTrack != null) 100.dp else 16.dp),
    ) {
        // ─── Меню разделов (Недавнее / Плейлисты / Альбомы / ...) ───
        item {
            MyMusicMenuList(
                textColor = textColor,
                secondaryColor = secondaryColor,
                cardColor = cardColor,
                onOpenPlaylists = onOpenPlaylists,
                onOpenAlbums = onOpenAlbums,
                onOpenArtists = onOpenArtists,
            )
        }

        // ─── Заголовок «Мои треки» + счётчик + «Перемешать все» ───
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Мои треки",
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (totalCount > 0) "${tracks.size} / $totalCount" else "${tracks.size}",
                    color = secondaryColor,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                // Кнопка «К треку» — прокрутка к текущему играющему треку
                if (hasCurrentInList) {
                    val target = currentTrack // captured val
                    Row(
                        modifier = Modifier
                            .clickable {
                                val idx = tracks.indexOfFirst {
                                    it.id == target.id && it.ownerId == target.ownerId
                                }
                                if (idx >= 0) {
                                    // LazyColumn items: 0=меню, 1=заголовок, 2...=треки
                                    val targetIndex = 2 + idx
                                    scope.launch { listState.animateScrollToItem(targetIndex) }
                                }
                            }
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "К треку",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                // Перемешать все
                Row(
                    modifier = Modifier.clickable(onClick = onShuffleAll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Перемешать все",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // ─── Состояния ───
        if (loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        }

        if (apiErrorMessage != null && tracks.isEmpty() && !loading) {
            item {
                Text(
                    text = apiErrorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                )
            }
        }

        // ─── Список треков ───
        items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
            val current = playerState.currentTrack
            val isCurrent = current != null &&
                track.id == current.id &&
                track.ownerId == current.ownerId
            val isPlaying = isCurrent && playerState.isPlaying
            val dl = downloads[track.id]
            VKTrackRow(
                track = track,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                downloadState = dl,
                cardColor = cardColor,
                accentColor = accentColor,
                textColor = textColor,
                secondaryColor = secondaryColor,
                onPlayClick = { onPlayTrack(track) },
                onDownloadClick = { onDownloadToggle(track) },
                onMoreClick = { onMoreClick(track) },
            )
        }

        // ─── Футер: «Загрузка…» / «Больше нет» / пусто ───
        if (loadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = accentColor,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Загрузка…", color = secondaryColor, fontSize = 13.sp)
                    }
                }
            }
        } else if (!hasMore && tracks.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Это все треки", color = secondaryColor, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─── Меню «Моя музыка»: Недавнее / Плейлисты / Альбомы / ... ─────────────────

@Composable
private fun MyMusicMenuList(
    textColor: Color,
    secondaryColor: Color,
    cardColor: Color,
    onOpenPlaylists: () -> Unit = {},
    onOpenAlbums: () -> Unit = {},
    onOpenArtists: () -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var showPlaylists by remember { mutableStateOf(false) }
    var showDownloaded by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AudioPlaylist>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }

    // Загрузка плейлистов при открытии диалога
    LaunchedEffect(showPlaylists) {
        if (showPlaylists) {
            playlistsLoading = true
            try {
                val (_, result) = app.apiClient.audioGetPlaylists(count = 30)
                playlists = result
            } catch (e: Exception) {
                AppLog.e("MyMusicMenuList", "Failed to load playlists", e)
                playlists = emptyList()
            } finally {
                playlistsLoading = false
            }
        }
    }

    // Диалог плейлистов
    if (showPlaylists) {
        AlertDialog(
            onDismissRequest = { showPlaylists = false },
            containerColor = cardColor,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Плейлисты",
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (playlistsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3D8BFF))
                    }
                } else if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Нет плейлистов", color = secondaryColor, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            try {
                                                val (_, tracks) = app.apiClient.audioGetPlaylistTracks(
                                                    playlistId = playlist.id,
                                                    ownerId = playlist.ownerId,
                                                    accessKey = playlist.accessKey,
                                                )
                                                val filtered = tracks
                                                    .filter { it.id > 0L && it.ownerId != 0L && !it.url.isNullOrBlank() }
                                                if (filtered.isNotEmpty()) {
                                                    PlayerConnection.playTrackList(filtered, 0)
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("PlaylistsDialog", "Failed to load playlist tracks", e)
                                            }
                                            showPlaylists = false
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Обложка плейлиста
                                val coverUrl = playlist.coverUrl
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF3D8BFF),
                                                    Color(0xFF1A5FCC),
                                                ),
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                // Название + инфо
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        color = textColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row {
                                        if (playlist.count > 0) {
                                            Text(
                                                text = "${playlist.count} треков",
                                                color = secondaryColor,
                                                fontSize = 12.sp,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        if (playlist.followers > 0) {
                                            Text(
                                                text = "${playlist.followers} подписчиков",
                                                color = secondaryColor,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                                // Кнопка play
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3D8BFF))
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val (_, tracks) = app.apiClient.audioGetPlaylistTracks(
                                                        playlistId = playlist.id,
                                                        ownerId = playlist.ownerId,
                                                        accessKey = playlist.accessKey,
                                                    )
                                                    val filtered = tracks
                                                        .filter { it.id > 0L && it.ownerId != 0L && !it.url.isNullOrBlank() }
                                                    if (filtered.isNotEmpty()) {
                                                        PlayerConnection.playTrackList(filtered, 0)
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("PlaylistsDialog", "Failed to play playlist", e)
                                                }
                                                showPlaylists = false
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Играть",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // #VK-MUSIC-SAVER-PORT: кнопка «скачать плейлист»
                                // (папка + обложка + tracklist.txt + все треки).
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3D8BFF).copy(alpha = 0.15f))
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val (_, tracks) = app.apiClient.audioGetPlaylistTracks(
                                                        playlistId = playlist.id,
                                                        ownerId = playlist.ownerId,
                                                        accessKey = playlist.accessKey,
                                                    )
                                                    val filtered = tracks
                                                        .filter { it.id > 0L && it.ownerId != 0L && !it.url.isNullOrBlank() }
                                                    if (filtered.isNotEmpty()) {
                                                        TrackDownloadManager.enqueuePlaylistDownload(
                                                            playlistTitle = playlist.title,
                                                            coverUrl = playlist.coverUrl,
                                                            tracks = filtered,
                                                        )
                                                        android.widget.Toast.makeText(
                                                            app.applicationContext,
                                                            "Скачивание плейлиста: ${filtered.size} треков",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    AppLog.e("PlaylistsDialog", "Failed to download playlist", e)
                                                }
                                                showPlaylists = false
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Скачать плейлист",
                                        tint = Color(0xFF3D8BFF),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylists = false }) {
                    Text("Закрыть", color = Color(0xFF3D8BFF))
                }
            },
        )
    }

    // ─── Диалог «Скачанная музыка» ──────────────────────────────
    if (showDownloaded) {
        val downloadedStates by TrackDownloadManager.downloads.collectAsState()
        val downloadedTracks = downloadedStates.values
            .filter { it.isCompleted }
            .sortedByDescending { it.trackId }
        val totalBytes = TrackDownloadManager.getTotalDownloadedBytes()
        val sizeStr = when {
            totalBytes < 1024 -> "$totalBytes Б"
            totalBytes < 1024 * 1024 -> "${"%.1f".format(totalBytes / 1024.0)} КБ"
            totalBytes < 1024 * 1024 * 1024 -> "${"%.1f".format(totalBytes / (1024.0 * 1024))} МБ"
            else -> "${"%.2f".format(totalBytes / (1024.0 * 1024 * 1024))} ГБ"
        }

        AlertDialog(
            onDismissRequest = { showDownloaded = false },
            containerColor = cardColor,
            shape = RoundedCornerShape(16.dp),
            title = {
                Column {
                    Text("Скачанная музыка", color = textColor, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${downloadedTracks.size} треков • $sizeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                    )
                }
            },
            text = {
                if (downloadedTracks.isEmpty()) {
                    Text("Нет скачанных треков", color = secondaryColor)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        items(
                            items = downloadedTracks,
                            key = { it.trackId },
                        ) { state ->
                            // Audit #40: используем collectAsState() вместо .value —
                            // иначе UI не рекомпозится при смене текущего трека.
                            val playerState by PlayerConnection.playerState.collectAsState()
                            val isCurrentTrack = playerState.currentTrack?.id == state.trackId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentTrack) Color(0xFF3D8BFF).copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable {
                                        // Fix #162: ранее трек искался в playerState.queue.
                                        // Если трека там не было (плеер пуст / играл другой
                                        // плейлист) — matchingTrack == null и playTrackById
                                        // НЕ вызывался → тап по скачанному треку ничего не
                                        // делал. Теперь строим Track-объекты из DownloadState
                                        // и запускаем playTrackList. toMediaItem берёт
                                        // локальный файл через getLocalFile(id) — url=null
                                        // не проблема (Fix #55).
                                        val downloadedTrackList = downloadedTracks.map { ds ->
                                            Track(
                                                id = ds.trackId,
                                                ownerId = ds.ownerId,
                                                artist = ds.artist,
                                                title = ds.title,
                                                duration = 0,
                                            )
                                        }
                                        val idx = downloadedTrackList.indexOfFirst { it.id == state.trackId }
                                        if (idx >= 0) {
                                            AppLog.i("MusicScreen", "Play downloaded: #${state.trackId} idx=$idx of ${downloadedTrackList.size}")
                                            PlayerConnection.playTrackList(downloadedTrackList, idx)
                                        } else {
                                            AppLog.w("MusicScreen", "Play downloaded: track #${state.trackId} not found in downloaded list")
                                        }
                                        showDownloaded = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.title.ifEmpty { "Трек #${state.trackId}" },
                                        color = if (isCurrentTrack) Color(0xFF3D8BFF) else textColor,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = state.artist.ifEmpty { "—" },
                                        color = secondaryColor,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = {
                                    TrackDownloadManager.removeDownload(state.trackId)
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (downloadedTracks.isNotEmpty()) {
                    TextButton(onClick = {
                        downloadedTracks.forEach { TrackDownloadManager.removeDownload(it.trackId) }
                        showDownloaded = false
                    }) {
                        Text("Удалить все", color = Color(0xFFE53935))
                    }
                }
                TextButton(onClick = { showDownloaded = false }) {
                    Text("Закрыть", color = Color(0xFF3D8BFF))
                }
            },
        )
    }

    val menuItems = listOf(
        Triple("Недавнее", Icons.Outlined.AccessTime, Color(0xFF3D8BFF)),
        Triple("Плейлисты", Icons.Filled.MusicNote, Color(0xFF3D8BFF)),
        Triple("Альбомы", Icons.Outlined.Album, Color(0xFF3D8BFF)),
        Triple("Артисты и кураторы", Icons.Outlined.Person, Color(0xFF3D8BFF)),
        Triple("Скачанная музыка", Icons.Filled.DownloadDone, Color(0xFF3D8BFF)),
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        menuItems.forEach { (title, icon, tint) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        // #MUSIC-PORT: навигация на экраны библиотеки вместо диалогов.
                        when (title) {
                            "Плейлисты" -> onOpenPlaylists()
                            "Альбомы" -> onOpenAlbums()
                            "Артисты и кураторы" -> onOpenArtists()
                            "Скачанная музыка" -> showDownloaded = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("›", color = secondaryColor, fontSize = 18.sp)
            }
        }
    }
}

// ─── Вкладка «Главная»: каталог VK (catalog.getAudio section=general) ─

@Composable
private fun MusicHomeTab(
    tracks: List<Track>,
    loading: Boolean,
    playerState: re.pinok.data.model.PlayerState,
    downloads: Map<Long, DownloadState>,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    apiErrorMessage: String?,
    onPlayTrack: (Int) -> Unit,
    onToggleTrack: (Track) -> Unit,
    onDownloadToggle: (Track) -> Unit,
    onPlaylistClick: (re.pinok.data.model.CatalogPlaylist) -> Unit = {},
    onShowAll: (sectionId: String, title: String) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var catalogBlocks by remember { mutableStateOf<List<re.pinok.data.model.CatalogBlock>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }

    // Загрузка каталога
    LaunchedEffect(Unit) {
        catalogLoading = true
        try {
            val blocks = app.apiClient.catalogGetAudio(section = "general", count = 10)
            catalogBlocks = blocks
            AppLog.i("MusicHomeTab", "Loaded ${blocks.size} catalog blocks")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #252: корректная отмена (пользователь ушёл со экрана)
            throw e
        } catch (e: Exception) {
            AppLog.e("MusicHomeTab", "catalog.getAudio error", e)
        } finally {
            catalogLoading = false
        }
    }

    val homeListState = rememberLazyListState()
    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (playerState.currentTrack != null) 100.dp else 16.dp),
    ) {
        if (catalogLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = accentColor) }
            }
        } else if (catalogBlocks.isEmpty() && tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, null, tint = secondaryColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Каталог пуст", color = textColor, fontSize = 16.sp)
                    }
                }
            }
        } else {
            // Рендерим блоки каталога.
            // #MUSIC-CATALOG-NO-DUP-HEADERS: HEADER/HEADER_EXTENDED блоки НЕ
            // рендерим отдельно — их title уже дублируется в SectionHeader
            // слайдера (контент-блок несёт тот же title). Отдельный рендер давал
            // двойной заголовок («МОИ ТРЕКИ» ×2).
            catalogBlocks.forEach { block ->
                when (block.viewType) {
                    re.pinok.data.model.CatalogViewType.HEADER,
                    re.pinok.data.model.CatalogViewType.HEADER_EXTENDED -> Unit
                    re.pinok.data.model.CatalogViewType.SEPARATOR -> {
                        item { HorizontalDivider(color = secondaryColor.copy(alpha = 0.1f), thickness = 1.dp) }
                    }
                    re.pinok.data.model.CatalogViewType.TRIPLE_STACKED_SLIDER -> {
                        // Горизонтальная карусель треков
                        if (block.tracks.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                TrackSliderRow(
                                    tracks = block.tracks,
                                    playerState = playerState,
                                    downloads = downloads,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlayTrack = { idx -> onPlayTrack(idx) },
                                    onToggleTrack = onToggleTrack,
                                    onDownloadToggle = onDownloadToggle,
                                )
                            }
                        }
                    }
                    // #MUSIC-CATALOG-RECOMMS: «Собрано алгоритмами» — это слайдер
                    // ПЛЕЙЛИСТОВ (recomms_slider), а не треков. Рендерим как плейлисты.
                    re.pinok.data.model.CatalogViewType.RECOMMS_SLIDER -> {
                        if (block.playlists.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                PlaylistSliderRow(
                                    playlists = block.playlists,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlaylistClick = onPlaylistClick,
                                )
                            }
                        }
                    }
                    re.pinok.data.model.CatalogViewType.LARGE_SLIDER -> {
                        // Горизонтальная карусель плейлистов
                        if (block.playlists.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                PlaylistSliderRow(
                                    playlists = block.playlists,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlaylistClick = onPlaylistClick,
                                )
                            }
                        }
                    }
                    else -> { /* UNKNOWN, LIST — skip for now on Home tab */ }
                }
            }

            // Фоллбэк: если каталог пуст — показываем «Мои треки» как раньше
            if (catalogBlocks.isEmpty() && tracks.isNotEmpty()) {
                item { SectionHeader("МОИ ТРЕКИ", textColor, secondaryColor) }
                items(tracks.take(6), key = { it.id }) { track ->
                    val isCurrent = playerState.currentTrack?.id == track.id
                    val isPlaying = isCurrent && playerState.isPlaying
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(tracks.indexOf(track)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = if (isPlaying) accentColor else textColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title.orEmpty(),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist.orEmpty(),
                                color = secondaryColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// #MUSIC-RADIO: заглушка вкладки «Радио».
// Section-id радиовкладки пока не найден в снапшотах. Инфраструктура готова:
// AudioRadioStation (Models.kt:1642), audio.radioGetById/followRadioStation.
// Нужен живой section-id из m.vk.ru/audios<id>?section=radiostations после JS.
@Composable
private fun RadioStubTab(
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    cardColor: Color,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = secondaryColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Радио", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Раздел в разработке",
                color = secondaryColor,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Откройте m.vk.ru/feed в браузере чтобы получить section-id",
                color = secondaryColor.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

// ─── Вкладка «Обзор» (P0): каталог explore (catalog.getAudio section=explore) ─

@Composable
private fun DiscoverTab(
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    cardColor: Color,
    playerState: re.pinok.data.model.PlayerState,
    downloads: Map<Long, DownloadState>,
    onPlaylistClick: (re.pinok.data.model.CatalogPlaylist) -> Unit = {},
    onShowAll: (sectionId: String, title: String) -> Unit = { _, _ -> },
    section: String = "explore",
) {
    val app = SovaApp.get()
    var catalogBlocks by remember { mutableStateOf<List<re.pinok.data.model.CatalogBlock>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }

    LaunchedEffect(section) {
        catalogLoading = true
        try {
            val blocks = app.apiClient.catalogGetAudio(section = section, count = 10)
            catalogBlocks = blocks
            AppLog.i("DiscoverTab", "Loaded ${blocks.size} blocks (section=$section)")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #252: корректная отмена (пользователь ушёл со экрана)
            throw e
        } catch (e: Exception) {
            AppLog.e("DiscoverTab", "catalog.getAudio($section) error", e)
        } finally {
            catalogLoading = false
        }
    }

    val discoverListState = rememberLazyListState()
    LazyColumn(
        state = discoverListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (playerState.currentTrack != null) 100.dp else 16.dp),
    ) {
        if (catalogLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = accentColor) }
            }
        } else if (catalogBlocks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, null, tint = secondaryColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Нет рекомендаций", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Попробуйте обновить позже", color = secondaryColor, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // #MUSIC-CATALOG-NO-DUP-HEADERS: HEADER/HEADER_EXTENDED не рендерим
            // отдельно — title дублируется в SectionHeader слайдера.
            catalogBlocks.forEach { block ->
                when (block.viewType) {
                    re.pinok.data.model.CatalogViewType.HEADER,
                    re.pinok.data.model.CatalogViewType.HEADER_EXTENDED -> Unit
                    re.pinok.data.model.CatalogViewType.SEPARATOR -> {
                        item { HorizontalDivider(color = secondaryColor.copy(alpha = 0.1f), thickness = 1.dp) }
                    }
                    re.pinok.data.model.CatalogViewType.TRIPLE_STACKED_SLIDER -> {
                        if (block.tracks.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                TrackSliderRow(
                                    tracks = block.tracks,
                                    playerState = playerState,
                                    downloads = downloads,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlayTrack = { idx ->
                                        PlayerConnection.playTrackList(block.tracks, idx)
                                    },
                                    onToggleTrack = {},
                                    onDownloadToggle = {},
                                )
                            }
                        }
                    }
                    re.pinok.data.model.CatalogViewType.RECOMMS_SLIDER -> {
                        if (block.playlists.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                PlaylistSliderRow(
                                    playlists = block.playlists,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlaylistClick = onPlaylistClick,
                                )
                            }
                        }
                    }
                    re.pinok.data.model.CatalogViewType.LARGE_SLIDER -> {
                        if (block.playlists.isNotEmpty()) {
                            item {
                                // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast
                                // свойства чужого модуля невозможен; захват в локальный val.
                                val blockTitle = block.title
                                if (blockTitle != null) {
                                    SectionHeader(
                                        blockTitle.uppercase(), textColor, secondaryColor,
                                        onShowAll = block.showAllId?.let { sid ->
                                            { onShowAll(sid, blockTitle) }
                                        },
                                    )
                                }
                            }
                            item {
                                PlaylistSliderRow(
                                    playlists = block.playlists,
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                    accentColor = accentColor,
                                    onPlaylistClick = onPlaylistClick,
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// ─── Section Header ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    textColor: Color,
    secondaryColor: Color,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onShowAll != null) {
            Text(
                text = "Показать все",
                color = secondaryColor,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ─── Algorithm Cards Row (СОБРАНО АЛГОРИТМАМИ) ─────────────────────────────

@Composable
private fun AlgorithmCardsRow(
    tracks: List<Track>,
    onPlayAll: (Int) -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AlgorithmCard(
                title = "Для вас",
                subtitle = "обновлён сегодня",
                gradient = listOf(Color(0xFF2E5BFF), Color(0xFF1A3DCC)),
                playStartIndex = 0,
                onPlay = onPlayAll,
                textColor = textColor,
                secondaryColor = secondaryColor,
            )
        }
        item {
            AlgorithmCard(
                title = "Открытия",
                subtitle = "Новое для вас",
                gradient = listOf(Color(0xFFE91E63), Color(0xFFAD1457)),
                playStartIndex = (tracks.size / 3).coerceAtLeast(0),
                onPlay = onPlayAll,
                textColor = textColor,
                secondaryColor = secondaryColor,
            )
        }
        item {
            AlgorithmCard(
                title = "Новинки",
                subtitle = "обновлён в субботу",
                gradient = listOf(Color(0xFF00BFA5), Color(0xFF00897B)),
                playStartIndex = (tracks.size * 2 / 3).coerceAtLeast(0),
                onPlay = onPlayAll,
                textColor = textColor,
                secondaryColor = secondaryColor,
            )
        }
    }
}

@Composable
private fun AlgorithmCard(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    playStartIndex: Int,
    onPlay: (Int) -> Unit,
    textColor: Color,
    secondaryColor: Color,
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush = Brush.verticalGradient(gradient))
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                color = textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = secondaryColor,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(textColor)
                        .clickable { onPlay(playStartIndex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Играть",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// ─── VK-style Track Row ────────────────────────────────────────────────────

@Composable
private fun VKTrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    downloadState: DownloadState?,
    cardColor: Color,
    accentColor: Color,
    textColor: Color,
    secondaryColor: Color,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoreClick: () -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlayClick)
                .background(if (isCurrent) cardColor.copy(alpha = 0.5f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Обложка трека
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.hsl((abs(track.artist.hashCode()) % 360).toFloat(), 0.55f, 0.45f),
                                Color.hsl(((abs(track.artist.hashCode() * 31)) % 360).toFloat(), 0.45f, 0.55f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val albumThumb = track.albumThumb
                if (!albumThumb.isNullOrBlank()) {
                    AsyncImage(
                        model = albumThumb,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Название + артист
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrent) accentColor else textColor,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    color = secondaryColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Длительность
            Text(
                text = track.duration.toDurationString(),
                color = secondaryColor,
                fontSize = 12.sp,
            )

            // Кнопка скачивания (4 состояния)
            VKDownloadButton(state = downloadState, onClick = onDownloadClick, accentColor = accentColor, secondaryColor = secondaryColor)

            // Троеточие-меню — открывает AudioMoreMenu (Fix #86).
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Ещё",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Прогресс-бар скачивания под строкой
        if (downloadState != null && downloadState.isInProgress) {
            LinearProgressIndicator(
                progress = {
                    if (downloadState.progress >= 0) downloadState.progress / 100f else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 16.dp),
                color = accentColor,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun VKDownloadButton(
    state: DownloadState?,
    onClick: () -> Unit,
    accentColor: Color,
    secondaryColor: Color,
) {
    when {
        state == null -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Скачать",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // #OFFLINE-STATUS-1: дохлый трек (DEAD_URL — URL протух/удалён).
        // Отдельная иконка MusicOff, чтобы пользователь ВИДЕЛ что трек «скончался»,
        // а не просто «ошибка загрузки». Тап — повтор (вдруг VK вернул URL).
        state.isDead -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.MusicOff,
                    contentDescription = "Недоступен (URL истёк) — тап для повтора",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        state.status == DownloadStatus.FAILED -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Повторить",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Fix #266: QUEUED (в очереди) — показываем часики + badge с позицией.
        // Отличается от DOWNLOADING (active) — там крутится progress-circle.
        state.status == DownloadStatus.QUEUED -> {
            // Позиция в очереди (1-based). 0 = уже активен (но статус QUEUED —
            // маловероятно, но защитно).
            val queuePos = re.pinok.media.TrackDownloadManager.getQueuePosition(state.trackId)
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,  // часики — "ожидание"
                    contentDescription = if (queuePos > 0) "В очереди: $queuePos" else "В очереди",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
                if (queuePos > 1) {
                    // Badge с номером позиции в очереди
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .background(accentColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 3.dp, vertical = 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = queuePos.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        state.status == DownloadStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { if (state.progress >= 0) state.progress / 100f else 0f },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accentColor,
                    trackColor = Color.Transparent,
                )
            }
        }
        // #OFFLINE-STATUS-1: siren-кэш (COMPLETED, codec=siren) — файл есть,
        // но офлайн не играется (стримится онлайн через HLS). DownloadDone +
        // маленький wifi-бейдж, чтобы пользователь видел: кеш есть, онлайн-only.
        state.isSirenCache -> {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "Скачано (онлайн-кеш, siren) — тап чтобы удалить",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(10.dp),
                )
            }
        }
        state.isCompleted -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "Скачано (тап чтобы удалить)",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        else -> {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Скачивание",
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// Fix #71: VKMiniPlayerBar удалён — теперь используется GlobalMiniPlayer

// ─── Горизонтальная карусель треков (TrackSlider) ──────────────────

@Composable
private fun TrackSliderRow(
    tracks: List<Track>,
    playerState: re.pinok.data.model.PlayerState,
    downloads: Map<Long, DownloadState>,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onPlayTrack: (Int) -> Unit,
    onToggleTrack: (Track) -> Unit,
    onDownloadToggle: (Track) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
            val isCurrent = playerState.currentTrack?.let {
                it.id == track.id && it.ownerId == track.ownerId
            } ?: false

            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onToggleTrack(track) },
            ) {
                // Обложка с оверлеем play
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (track.albumThumb != null) {
                        AsyncImage(
                            model = track.albumThumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote, null,
                            tint = secondaryColor,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    // Оверлей play
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent && playerState.isPlaying) Color.Black.copy(alpha = 0.6f) else accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isCurrent && playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    // Explicit badge
                    if (track.isExplicit) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text("E", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Название
                Text(
                    text = track.title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Артист
                Text(
                    text = track.artist,
                    color = secondaryColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Горизонтальная карусель плейлистов (PlaylistSlider) ─────────────

@Composable
private fun PlaylistSliderRow(
    playlists: List<re.pinok.data.model.CatalogPlaylist>,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onPlaylistClick: (re.pinok.data.model.CatalogPlaylist) -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playlists, key = { "${it.ownerId}_${it.id}" }) { pl ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    // #PLAYLIST-OPEN: открыть плейлист (audio.getPlaylistById → play).
                    .clickable { onPlaylistClick(pl) },
            ) {
                // Обложка
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pl.coverUrl != null) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote, null,
                            tint = accentColor,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Название
                Text(
                    text = pl.title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Совпадение вкусов или кол-во треков
                val subtitle = when {
                    pl.matchPercent != null -> "${pl.matchPercent}% совпадение"
                    pl.count > 0 -> "${pl.count} треков"
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = secondaryColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Fix #266: Компоненты для секций поиска (Артисты / Плейлисты / Треки)
// ════════════════════════════════════════════════════════════════════

/**
 * Заголовок секции в результатах поиска («Артисты», «Плейлисты», «Треки»).
 * Слева — название, справа — счётчик.
 */
@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    textColor: Color,
    secondaryColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            color = secondaryColor,
            fontSize = 13.sp,
        )
    }
}

/**
 * Карточка артиста для горизонтального слайдера в результатах поиска.
 * Круглая аватарка + имя + жанр (если есть).
 */
@Composable
private fun SearchArtistCard(
    artist: AudioArtist,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(cardColor.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            val photo = artist.coverUrl
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = artist.name,
                    tint = secondaryColor,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!artist.genres.isNullOrEmpty()) {
            Text(
                text = artist.genres.first(),
                color = secondaryColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Карточка плейлиста для горизонтального слайдера в результатах поиска.
 * Квадратная обложка + название + кол-во треков.
 */
@Composable
private fun SearchPlaylistCard(
    playlist: AudioPlaylist,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(cardColor.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            val cover = playlist.photo300 ?: playlist.photo200 ?: playlist.photo600 ?: playlist.photo
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Outlined.Album,
                    contentDescription = playlist.title,
                    tint = secondaryColor,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // #ARCH-CONTAINERS 3.7-1: модели в :core:data — smart cast чужого модуля
        // невозможен; без захвата subtitle выводился бы String? и isNotBlank() падал.
        val playlistDescription = playlist.description
        val subtitle = when {
            !playlistDescription.isNullOrBlank() -> playlistDescription
            playlist.count > 0 -> "${playlist.count} треков"
            else -> ""
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = secondaryColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
