package re.pinok.ui.screens.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Track
import re.pinok.media.ClipVideoDownloadManager
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.media.VideoDownloadManager
import re.pinok.media.StoryVideoDownloadManager
import re.pinok.util.AppLog
import java.io.File

private const val TAG = "OfflineManagerScreen"

/**
 * #39 C5: Опции сортировки офлайн-контента (по образцу Kate Mobile audio_cache).
 *
 * DATE_NEW — newest first (по file lastModified),
 * SIZE_BIG — largest first,
 * TITLE_AZ — по алфавиту (title),
 * ARTIST_AZ — по исполнителю (только аудио).
 */
enum class OfflineSortOption(val label: String) {
    DATE_NEW("Сначала новые"),
    SIZE_BIG("Сначала большие"),
    TITLE_AZ("По названию (А-Я)"),
    ARTIST_AZ("По исполнителю (А-Я)"),
}

/**
 * Экран офлайн-менеджера — показывает скачанные аудио и видео.
 * Доступен при отсутствии сети через кнопку в ErrorView или из drawer.
 *
 * #34: [onPlayVideo] — опциональный колбэк для воспроизведения скачанного
 * видео. Если задан — тап по строке видео открывает VideoPlayerScreen
 * (через VideoHolder). Если null (guest-режим без navController) —
 * воспроизведение недоступно, показывается только информация.
 *
 * #39 C5: поиск + сортировка по образцу Kate Mobile `audio_cache.xml`
 * (filter_box + clear). Фильтр работает по title/artist (аудио) или title
 * (видео), case-insensitive. Сортировка: DATE_NEW / SIZE_BIG / TITLE_AZ /
 * ARTIST_AZ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineManagerScreen(
    onBack: () -> Unit,
    onPlayVideo: ((ownerId: Long, videoId: Long, title: String) -> Unit)? = null,
    /**
     * Fix #111: Колбэк воспроизведения скачанной видео-истории из кэша.
     * Открывает StoryOfflinePlayerScreen с file:// URI (без сети).
     * Если null (guest-режим) — тап по строке истории игнорируется.
     */
    onPlayStory: ((ownerId: Long, storyId: Int) -> Unit)? = null,
    /**
     * §37.12 #330: Колбэк воспроизведения скачанного клипа из кэша.
     * Открывает ClipOfflinePlayerScreen с file:// URI (без сети, TikTok-стиль
     * 9:16 vertical). Если null (guest-режим) — тап по строке клипа игнорируется.
     */
    onPlayClip: ((ownerId: Long, videoId: Long) -> Unit)? = null,
    /**
     * Fix #50: Колбэк «Открыть офлайн-плеер». Открывает новый
     * [re.pinok.ui.screens.offline.OfflineAudioPlayerScreen] с минималистичным
     * UI (только прогресс, controls, очередь) и без сетевых запросов.
     * Default = пустой lambda — для guest-режима и обратной совместимости.
     */
    onOpenPlayer: () -> Unit = {},
) {
    val audioDownloads by TrackDownloadManager.downloads.collectAsState()
    val videoDownloads by VideoDownloadManager.downloads.collectAsState()
    // Fix #100: story video downloads (отдельный менеджер).
    val storyDownloads by StoryVideoDownloadManager.downloads.collectAsState()
    // §37.12 #330: clip video downloads (отдельный менеджер ClipVideoDownloadManager).
    val clipDownloads by ClipVideoDownloadManager.downloads.collectAsState()

    val completedAudio = remember(audioDownloads) {
        audioDownloads.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .toList()
    }
    val completedVideo = remember(videoDownloads) {
        videoDownloads.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .toList()
    }
    val completedStories = remember(storyDownloads) {
        storyDownloads.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .toList()
    }
    // §37.12 #330: только завершённые clip-загрузки (downloaded + .mp4 файл валиден).
    val completedClips = remember(clipDownloads) {
        clipDownloads.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .toList()
    }

    val audioCount = completedAudio.size
    val videoCount = completedVideo.size
    val storyCount = completedStories.size
    val clipCount = completedClips.size
    val audioBytes = TrackDownloadManager.getTotalDownloadedBytes()
    // #39 C5: считаем и видео-байты для футера (раньше только аудио).
    val videoBytes = remember(completedVideo) {
        completedVideo.sumOf { ds ->
            VideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId)?.length() ?: 0L
        }
    }
    // Fix #100: story video bytes для футера.
    val storyBytes = remember(completedStories) {
        completedStories.sumOf { ds ->
            // trackId хранит storyId (Int→Long). Парсим ключ для getLocalFile.
            StoryVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId.toInt())?.length() ?: 0L
        }
    }
    // §37.12 #330: clip bytes для футера. Для clips trackId хранит Long videoId.
    val clipBytes = remember(completedClips) {
        completedClips.sumOf { ds ->
            ClipVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId)?.length() ?: 0L
        }
    }
    val totalBytes = audioBytes + videoBytes + storyBytes + clipBytes

    var selectedTab by remember { mutableIntStateOf(0) }
    // #39 C5: state поиска/сортировки — отдельные для каждой вкладки.
    var audioQuery by remember { mutableStateOf("") }
    var videoQuery by remember { mutableStateOf("") }
    var storyQuery by remember { mutableStateOf("") }
    // §37.12 #330: query для вкладки Клипы.
    var clipQuery by remember { mutableStateOf("") }
    var audioSort by remember { mutableStateOf(OfflineSortOption.DATE_NEW) }
    var videoSort by remember { mutableStateOf(OfflineSortOption.DATE_NEW) }
    var storySort by remember { mutableStateOf(OfflineSortOption.DATE_NEW) }
    // §37.12 #330: сортировка для clips (по умолчанию DATE_NEW).
    var clipSort by remember { mutableStateOf(OfflineSortOption.DATE_NEW) }

    // Fix #167: state для диалогов проверки кэша.
    var scanMenuOpen by remember { mutableStateOf(false) }
    var lightScanResult by remember { mutableStateOf<Map<Long, TrackDownloadManager.CacheIntegrity>?>(null) }
    var lightScanRunning by remember { mutableStateOf(false) }
    var deepScanRunning by remember { mutableStateOf(false) }
    var deepScanProgress by remember { mutableStateOf("" to 0) } // (text, done count)
    val scope = rememberCoroutineScope()

    // Fix #147: при открытии экрана проверяем соответствие сохранённого пути
    // загрузки текущему downloadDir. Если SD card отмонтирована или SAF
    // permission revoked после перезапуска — покажем banner (см. ниже).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val snap = re.pinok.SovaApp.get().prefs.data.first()
            if (snap.musicDownloadPath.isNotBlank()) {
                TrackDownloadManager.checkPathMismatch(snap.musicDownloadPath)
            }
        } catch (e: Exception) {
            // prefs может быть не готов — не критично, при следующем open сработает.
        }
    }

    // Fix #100: добавлен третий таб «Истории».
    // §37.12 #330: добавлен четвёртый таб «Клипы».
    val tabTitles = listOf(
        "Аудио ($audioCount)",
        "Видео ($videoCount)",
        "Истории ($storyCount)",
        "Клипы ($clipCount)",
    )

    // Fix #160: убран windowInsetsPadding(navigationBars) — он дублировал
    // Scaffold bottomBar padding (GlobalMiniPlayer + Spacer с navigationBars).
    // Scaffold уже даёт content bottom padding = miniPlayer + navBar, а этот
    // inset добавлял ЕЩЁ один navBar → внизу экрана было пустое место (~48dp)
    // между футером «Всего: N аудио...» и мини-плеером. Другие hasOwnTopBar
    // экраны (CommunityScreen, SettingsScreen) тоже не добавляют этот inset.
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = { Text("Офлайн") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                // Fix #167: кнопка «Проверить кэш» — открывает меню с двумя опциями.
                IconButton(onClick = { scanMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = "Проверить кэш",
                    )
                }
                DropdownMenu(
                    expanded = scanMenuOpen,
                    onDismissRequest = { scanMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Лёгкая проверка (без сети)") },
                        onClick = {
                            scanMenuOpen = false
                            lightScanRunning = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    TrackDownloadManager.scanAllCachedLight()
                                }
                                lightScanResult = result
                                lightScanRunning = false
                            }
                        },
                        enabled = !lightScanRunning && !deepScanRunning,
                    )
                    DropdownMenuItem(
                        text = { Text("Глубокая проверка (с m3u8)") },
                        onClick = {
                            scanMenuOpen = false
                            deepScanRunning = true
                            scope.launch {
                                val cached = TrackDownloadManager.downloads.value.values
                                    .filter { it.isCompleted }
                                    .sortedBy { it.trackId }
                                var done = 0
                                for (state in cached) {
                                    deepScanProgress = ("${state.artist} — ${state.title}" to done)
                                    withContext(Dispatchers.IO) {
                                        TrackDownloadManager.deepScanTrack(state.trackId)
                                    }
                                    done++
                                    deepScanProgress = ("${state.artist} — ${state.title}" to done)
                                }
                                // После глубокого сканирования обновим и лёгкий результат
                                lightScanResult = withContext(Dispatchers.Default) {
                                    TrackDownloadManager.scanAllCachedLight()
                                }
                                deepScanRunning = false
                                deepScanProgress = ("" to 0)
                            }
                        },
                        enabled = !lightScanRunning && !deepScanRunning,
                    )
                }
                // #34: guest-режим — кнопка «Войти» в TopAppBar удалена.
                // Возврат на экран авторизации теперь через кнопку «Назад» (onBack).
            },
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )

        // Fix #147: banner предупреждения о несоответствии папки загрузки.
        // Показывается когда сохранённый в prefs musicDownloadPath не может
        // быть применён (SD card отмонтирована, SAF permission revoked, и т.д.)
        // и файлы пишутся во внутреннюю память вместо выбранной папки.
        val pathMismatch by TrackDownloadManager.pathMismatch.collectAsState()
        val mismatchInfo = pathMismatch
        if (mismatchInfo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Папка загрузки недоступна",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "${mismatchInfo.reason}\n" +
                            "Выбрано: ${mismatchInfo.savedPath}\n" +
                            "Файлы пишутся: внутренняя память",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        // Fix #167: баннер статуса проверки кэша.
        if (lightScanRunning || deepScanRunning) {
            val (text, done) = deepScanProgress
            val total = if (deepScanRunning) completedAudio.size else 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (deepScanRunning && text.isNotEmpty()) {
                        "Глубокая проверка: $done/$total — $text"
                    } else {
                        "Лёгкая проверка кэша…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (lightScanResult != null) {
            val result = lightScanResult!!
            val valid = result.count { it.value == TrackDownloadManager.CacheIntegrity.VALID }
            val corrupted = result.count { it.value == TrackDownloadManager.CacheIntegrity.CORRUPTED }
            val noHash = result.count { it.value == TrackDownloadManager.CacheIntegrity.NO_HASH }
            val notFound = result.count { it.value == TrackDownloadManager.CacheIntegrity.NOT_FOUND }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (corrupted > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("Проверено: ${result.size}")
                        if (valid > 0) append("  ✓$valid")
                        if (corrupted > 0) append("  ⚠$corrupted повреждено")
                        if (noHash > 0) append("  ?$noHash без хеша")
                        if (notFound > 0) append("  ✗$notFound нет файла")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (corrupted > 0) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (selectedTab) {
                0 -> AudioOfflineTab(
                    tracks = completedAudio,
                    query = audioQuery,
                    onQueryChange = { audioQuery = it },
                    sort = audioSort,
                    onSortChange = { audioSort = it },
                    onOpenPlayer = onOpenPlayer,
                )
                1 -> VideoOfflineTab(
                    videos = completedVideo,
                    query = videoQuery,
                    onQueryChange = { videoQuery = it },
                    sort = videoSort,
                    onSortChange = { videoSort = it },
                    onPlayVideo = onPlayVideo,
                )
                // Fix #100: таб «Истории» — story video кэш.
                2 -> StoryOfflineTab(
                    stories = completedStories,
                    query = storyQuery,
                    onQueryChange = { storyQuery = it },
                    sort = storySort,
                    onSortChange = { storySort = it },
                    onPlayStory = onPlayStory,
                )
                // §37.12 #330: таб «Клипы» — clip video кэш (ClipVideoDownloadManager).
                3 -> ClipOfflineTab(
                    clips = completedClips,
                    query = clipQuery,
                    onQueryChange = { clipQuery = it },
                    sort = clipSort,
                    onSortChange = { clipSort = it },
                    onPlayClip = onPlayClip,
                    onRemoveClip = { ownerId, videoId ->
                        AppLog.i(TAG, "Delete offline clip: owner=$ownerId videoId=$videoId")
                        ClipVideoDownloadManager.removeDownload(ownerId, videoId)
                    },
                )
            }
        }

        // Итого: количество + объём
        // §37.12 #330: добавлен clipCount в футер.
        if (audioCount > 0 || videoCount > 0 || storyCount > 0 || clipCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Всего: $audioCount аудио, $videoCount видео, $storyCount историй, $clipCount клипов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatBytes(totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * #39 C5: Панель поиска + сортировки (по образцу Kate Mobile filter_box + clear).
 *
 * OutlinedTextField с leading Search icon + trailing Clear (если query не пустой).
 * IconButton Sort открывает DropdownMenu с вариантами сортировки.
 */
@Composable
private fun SearchSortBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    sortOptions: List<OfflineSortOption>,
    totalCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Поиск ($totalCount)") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Очистить",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        var sortMenuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Сортировка",
                )
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/** Элемент офлайн-аудио с файлом для сортировки по date/size. */
private data class AudioOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val file: File?,
)

/** Элемент офлайн-видео с файлом для сортировки по date/size. */
private data class VideoOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val file: File?,
)

@Composable
private fun AudioOfflineTab(
    tracks: List<re.pinok.data.model.DownloadState>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    /** Fix #50: открывает [OfflineAudioPlayerScreen] при тапе. */
    onOpenPlayer: () -> Unit = {},
) {
    // Построить список items с файлами (для sort by date/size).
    val allItems = remember(tracks) {
        tracks.map { ds ->
            AudioOfflineItem(
                state = ds,
                file = TrackDownloadManager.getLocalFile(ds.trackId),
            )
        }
    }

    // #39 C5: filter + sort
    val visibleItems = remember(allItems, query, sort) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allItems else {
            allItems.filter { item ->
                item.state.title.lowercase().contains(q) ||
                    item.state.artist.lowercase().contains(q) ||
                    item.state.displayText.lowercase().contains(q)
            }
        }
        when (sort) {
            OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.file?.lastModified() ?: 0L }
            OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.file?.length() ?: 0L }
            OfflineSortOption.TITLE_AZ -> filtered.sortedBy { it.state.title.lowercase() }
            OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { it.state.artist.lowercase() }
        }
    }

    // Построить список Track для PlayerConnection (по visibleItems — играет только отфильтрованный список).
    val playableTracks = remember(visibleItems) {
        visibleItems.map { item ->
            Track(
                id = item.state.trackId,
                ownerId = item.state.ownerId,
                artist = item.state.artist,
                title = item.state.title,
                duration = 0,
                url = null, // URL не нужен — PlayerConnection проверит локальный файл
            )
        }
    }

    // #39 C5: единый Column — SearchSortBar + (empty Box | LazyColumn) с weight(1f).
    // Раньше Column и LazyColumn были siblings с fillMaxSize → LazyColumn перекрывал SearchSortBar.
    Column(modifier = Modifier.fillMaxSize()) {
        // Fix #50: кнопка «Открыть плеер» вверху вкладки — открывает минималистичный
        // офлайн-плеер с прогрессом, controls и очередью скачанных треков.
        // Показывается только когда есть хоть один скачанный трек.
        if (allItems.isNotEmpty()) {
            Button(
                onClick = {
                    AppLog.i(TAG, "Open offline audio player (${allItems.size} tracks)")
                    onOpenPlayer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Открыть плеер (${allItems.size})")
            }
        }
        SearchSortBar(
            query = query,
            onQueryChange = onQueryChange,
            sort = sort,
            onSortChange = onSortChange,
            sortOptions = OfflineSortOption.entries,
            totalCount = allItems.size,
        )

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (allItems.isEmpty()) "Нет загруженных аудио"
                               else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(visibleItems, key = { it.state.trackId }) { item ->
                    val downloadState = item.state
                    AudioOfflineRow(
                        state = downloadState,
                        fileSize = item.file?.length() ?: 0L,
                        onClick = {
                            val index = playableTracks.indexOfFirst { it.id == downloadState.trackId }
                            if (index >= 0) {
                                AppLog.i(TAG, "Play offline track: ${downloadState.displayText}")
                                PlayerConnection.playTrackList(playableTracks, index)
                            }
                        },
                        onDelete = {
                            AppLog.i(TAG, "Delete offline track: ${downloadState.trackId}")
                            TrackDownloadManager.removeDownload(downloadState.trackId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoOfflineTab(
    videos: List<re.pinok.data.model.DownloadState>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    onPlayVideo: ((ownerId: Long, videoId: Long, title: String) -> Unit)?,
) {
    val allItems = remember(videos) {
        videos.map { ds ->
            VideoOfflineItem(
                state = ds,
                file = VideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId),
            )
        }
    }

    // #39 C5: filter + sort (видео — без ARTIST_AZ, эта опция скрыта).
    val visibleItems = remember(allItems, query, sort) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allItems else {
            allItems.filter { it.state.title.lowercase().contains(q) }
        }
        when (sort) {
            OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.file?.lastModified() ?: 0L }
            OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.file?.length() ?: 0L }
            OfflineSortOption.TITLE_AZ -> filtered.sortedBy { it.state.title.lowercase() }
            // Для видео ARTIST_AZ не имеет смысла — fallback на TITLE_AZ.
            OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { it.state.title.lowercase() }
        }
    }

    // #39 C5: единый Column — SearchSortBar + (empty Box | LazyColumn) с weight(1f).
    Column(modifier = Modifier.fillMaxSize()) {
        SearchSortBar(
            query = query,
            onQueryChange = onQueryChange,
            sort = sort,
            onSortChange = onSortChange,
            // Видео не имеет исполнителя — убираем ARTIST_AZ из меню.
            sortOptions = OfflineSortOption.entries - OfflineSortOption.ARTIST_AZ,
            totalCount = allItems.size,
        )

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (allItems.isEmpty()) "Нет загруженных видео"
                               else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(visibleItems, key = { "${it.state.ownerId}_${it.state.trackId}" }) { item ->
                    val downloadState = item.state
                    VideoOfflineRow(
                        state = downloadState,
                        fileSize = item.file?.length() ?: 0L,
                        canPlay = onPlayVideo != null,
                        onClick = {
                            if (onPlayVideo != null) {
                                AppLog.i(TAG, "Play offline video: owner=${downloadState.ownerId} id=${downloadState.trackId}")
                                onPlayVideo(
                                    downloadState.ownerId,
                                    downloadState.trackId,
                                    downloadState.title.ifBlank { "Видео #${downloadState.trackId}" },
                                )
                            }
                        },
                        onDelete = {
                            AppLog.i(TAG, "Delete offline video: owner=${downloadState.ownerId} id=${downloadState.trackId}")
                            VideoDownloadManager.removeDownload(downloadState.ownerId, downloadState.trackId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioOfflineRow(
    state: re.pinok.data.model.DownloadState,
    fileSize: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = if (state.title.isNotBlank()) state.title else "Трек #${state.trackId}"
            val displayArtist = state.artist.ifBlank { "Неизвестный исполнитель" }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$displayArtist${if (fileSize > 0) " \u2022 ${formatBytes(fileSize)}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoOfflineRow(
    state: re.pinok.data.model.DownloadState,
    fileSize: Long,
    canPlay: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canPlay) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (canPlay) Icons.Filled.PlayArrow else Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // #39 C1: реальный title из .meta sidecar (refreshFromDisk загружает).
            // Fallback «Видео #ID» — только если .meta отсутствует/повреждён.
            val displayTitle = state.title.ifBlank { "Видео #${state.trackId}" }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (fileSize > 0) formatBytes(fileSize) else "owner: ${state.ownerId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[0]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

// ═══════════════════════════════════════════════════════════════════
// Fix #100: Story video offline tab — кэш видео-историй.
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StoryOfflineTab(
    stories: List<re.pinok.data.model.DownloadState>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    /**
     * Fix #111: Колбэк воспроизведения story из кэша.
     * null = guest-режим, тап по строке игнорируется (canPlay = false).
     */
    onPlayStory: ((ownerId: Long, storyId: Int) -> Unit)? = null,
) {
    // Загружаем .meta sidecar для каждого story — там ownerName, thumbUrl, expiresAt.
    val allItems = remember(stories) {
        stories.map { ds ->
            val key = StoryVideoDownloadManager.storyKey(ds.ownerId, ds.trackId.toInt())
            val meta = StoryVideoDownloadManager.getStoryMeta(key)
            StoryOfflineItem(
                state = ds,
                meta = meta,
                file = StoryVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId.toInt()),
            )
        }
    }

    val visibleItems = remember(allItems, query, sort) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allItems else {
            allItems.filter { (it.meta?.ownerName ?: it.state.title).lowercase().contains(q) }
        }
        when (sort) {
            OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.meta?.downloadedAt ?: it.file?.lastModified() ?: 0L }
            OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.file?.length() ?: 0L }
            OfflineSortOption.TITLE_AZ -> filtered.sortedBy { (it.meta?.ownerName ?: it.state.title).lowercase() }
            OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { (it.meta?.ownerName ?: it.state.title).lowercase() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchSortBar(
            query = query,
            onQueryChange = onQueryChange,
            sort = sort,
            onSortChange = onSortChange,
            sortOptions = OfflineSortOption.entries - OfflineSortOption.ARTIST_AZ,
            totalCount = allItems.size,
        )

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (allItems.isEmpty()) "Нет загруженных историй"
                               else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (allItems.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Истории кэшируются автоматически при просмотре",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(visibleItems, key = { "s_${it.state.ownerId}_${it.state.trackId}" }) { item ->
                    StoryOfflineRow(
                        state = item.state,
                        meta = item.meta,
                        fileSize = item.file?.length() ?: 0L,
                        // Fix #111: canPlay только если файл существует И onPlayStory задан.
                        // Если файл удалён/истёк — тап игнорируется (нельзя играть).
                        canPlay = item.file != null && item.file.exists() && onPlayStory != null,
                        onPlay = {
                            onPlayStory?.invoke(item.state.ownerId, item.state.trackId.toInt())
                        },
                        onDelete = {
                            AppLog.i(TAG, "Delete offline story: owner=${item.state.ownerId} storyId=${item.state.trackId}")
                            StoryVideoDownloadManager.removeDownload(item.state.ownerId, item.state.trackId.toInt())
                        },
                    )
                }
            }
        }
    }
}

private data class StoryOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val meta: StoryVideoDownloadManager.StoryVideoMeta?,
    val file: java.io.File?,
)

@Composable
private fun StoryOfflineRow(
    state: re.pinok.data.model.DownloadState,
    meta: StoryVideoDownloadManager.StoryVideoMeta?,
    fileSize: Long,
    /** Fix #111: можно ли воспроизвести (файл существует + onPlayStory задан). */
    canPlay: Boolean = false,
    onPlay: () -> Unit = {},
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fix #111: тап по строке открывает StoryOfflinePlayerScreen (как VideoOfflineRow).
            .then(if (canPlay) Modifier.clickable(onClick = onPlay) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Аватар автора (из meta.ownerPhoto100) или fallback-иконка.
        // Fix #111: если canPlay — overlay с PlayArrow поверх аватара (как VideoOfflineRow).
        val photoUrl = meta?.ownerPhoto100
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                coil3.compose.AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (canPlay) Icons.Filled.PlayArrow else Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Fix #111: полупрозрачный PlayArrow overlay поверх аватара если canPlay.
            if (canPlay && photoUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = meta?.ownerName?.ifBlank { null }
                ?: state.title.ifBlank { "История ${state.ownerId}" }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Подзаголовок: размер + badge истечения TTL.
            val subtitle = buildString {
                if (fileSize > 0) append(formatBytes(fileSize))
                meta?.expiresAt?.let { exp ->
                    val remainMs = exp - System.currentTimeMillis()
                    if (remainMs > 0) {
                        val remainH = remainMs / (60 * 60 * 1000)
                        if (fileSize > 0) append(" • ")
                        if (remainH > 0) append("истекает через ${remainH}ч")
                        else append("истекает скоро")
                    } else {
                        if (fileSize > 0) append(" • ")
                        append("истекла")
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §37.12 #330: Clip video offline tab — кэш коротких вертикальных видео.
// Структурно — копия StoryOfflineTab/StoryOfflineRow (Fix #100 / Fix #111),
// но данные берутся из ClipVideoDownloadManager (clip_downloads/, ключ "c_*").
// ═══════════════════════════════════════════════════════════════════

/**
 * §37.12 #330: Элемент офлайн-клипа — DownloadState + .meta sidecar + локальный файл.
 *
 * Для clips trackId в DownloadState хранит Long videoId (вместо Int storyId
 * у stories). OwnerId берётся из DownloadState.ownerId — он может быть
 * отрицательным для групповых клипов (например -229917482 → группа 229917482).
 */
private data class ClipOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val meta: ClipVideoDownloadManager.ClipVideoMeta?,
    val file: java.io.File?,
)

/**
 * §37.12 #330: Вкладка «Клипы» в OfflineManagerScreen.
 *
 * Показывает список скачанных clips из ClipVideoDownloadManager: миниатюра,
 * автор, заголовок, длительность, дата скачивания. Тап → onPlayClip
 * (открывает ClipOfflinePlayerScreen). Иконка-delete → onRemoveClip
 * (вызывает ClipVideoDownloadManager.removeDownload).
 *
 * Структура и UX — копия StoryOfflineTab:
 *  - SearchSortBar (без ARTIST_AZ, т.к. clips не имеют «исполнителя»).
 *  - Empty-state с подсказкой «Скачайте клип из раздела Клипы».
 *  - LazyColumn of ClipOfflineRow.
 */
@Composable
private fun ClipOfflineTab(
    clips: List<re.pinok.data.model.DownloadState>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    /**
     * §37.12 #330: Колбэк воспроизведения clip из кэша.
     * null = guest-режим, тап по строке игнорируется (canPlay = false).
     */
    onPlayClip: ((ownerId: Long, videoId: Long) -> Unit)? = null,
    /** Колбэк удаления clip (обычно вызывает ClipVideoDownloadManager.removeDownload). */
    onRemoveClip: (ownerId: Long, videoId: Long) -> Unit,
) {
    // Загружаем .meta sidecar + локальный файл для каждого clip.
    // В .meta хранятся: title, description, thumbUrl, duration, authorName,
    // authorAvatar, downloadedAt (для сортировки и отображения).
    val allItems = remember(clips) {
        clips.map { ds ->
            val meta = ClipVideoDownloadManager.getClipMeta(ds.ownerId, ds.trackId)
            val file = ClipVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId)
            ClipOfflineItem(state = ds, meta = meta, file = file)
        }
    }

    // #39 C5: filter + sort (clips — без ARTIST_AZ, эта опция скрыта — mirror video).
    val visibleItems = remember(allItems, query, sort) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allItems else {
            allItems.filter { item ->
                val title = item.meta?.title?.lowercase().orEmpty()
                val author = item.meta?.authorName?.lowercase().orEmpty()
                val desc = item.meta?.description?.lowercase().orEmpty()
                title.contains(q) || author.contains(q) || desc.contains(q) ||
                    item.state.title.lowercase().contains(q)
            }
        }
        when (sort) {
            OfflineSortOption.DATE_NEW -> filtered.sortedByDescending {
                it.meta?.downloadedAt?.takeIf { t -> t > 0 } ?: it.file?.lastModified() ?: 0L
            }
            OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.file?.length() ?: 0L }
            OfflineSortOption.TITLE_AZ -> filtered.sortedBy {
                (it.meta?.title?.ifBlank { null } ?: it.state.title).lowercase()
            }
            // Для clips ARTIST_AZ не имеет смысла — fallback на TITLE_AZ (mirror video).
            OfflineSortOption.ARTIST_AZ -> filtered.sortedBy {
                (it.meta?.title?.ifBlank { null } ?: it.state.title).lowercase()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchSortBar(
            query = query,
            onQueryChange = onQueryChange,
            sort = sort,
            onSortChange = onSortChange,
            // Clips не имеют исполнителя — убираем ARTIST_AZ из меню (mirror video).
            sortOptions = OfflineSortOption.entries - OfflineSortOption.ARTIST_AZ,
            totalCount = allItems.size,
        )

        if (visibleItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (allItems.isEmpty()) "Нет скачанных клипов"
                               else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (allItems.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Скачайте клип из раздела «Клипы» — он появится здесь",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(visibleItems, key = { "c_${it.state.ownerId}_${it.state.trackId}" }) { item ->
                    ClipOfflineRow(
                        state = item.state,
                        meta = item.meta,
                        fileSize = item.file?.length() ?: 0L,
                        // §37.12 #330: canPlay только если файл существует И onPlayClip задан.
                        // Если файл удалён пользователем/истёк TTL — тап игнорируется.
                        canPlay = item.file != null && item.file.exists() && onPlayClip != null,
                        onPlay = {
                            onPlayClip?.invoke(item.state.ownerId, item.state.trackId)
                        },
                        onDelete = {
                            onRemoveClip(item.state.ownerId, item.state.trackId)
                        },
                    )
                }
            }
        }
    }
}

/**
 * §37.12 #330: Строка офлайн-клипа — миниатюра + автор + заголовок + длительность.
 *
 * Структурно — копия StoryOfflineRow (Fix #111), но использует ClipVideoMeta
 * вместо StoryVideoMeta. Дополнительно показывает duration (M:SS) — у clips
 * длительность обычно 15-60s, важно для пользователя при скролле списка.
 *
 * Layout:
 * ```
 * [Thumbnail 64dp]  [Column: Row(avatar 24dp + authorName bold)
 *                            title / description
 *                            duration • date • size             ]  [Delete]
 * ```
 *
 * Thumbnail — из `meta.thumbUrl` (AsyncImage). Поверх thumbnail — полупрозрачный
 * PlayArrow overlay если canPlay (как в StoryOfflineRow).
 */
@Composable
private fun ClipOfflineRow(
    state: re.pinok.data.model.DownloadState,
    meta: ClipVideoDownloadManager.ClipVideoMeta?,
    fileSize: Long,
    /** §37.12 #330: можно ли воспроизвести (файл существует + onPlayClip задан). */
    canPlay: Boolean = false,
    onPlay: () -> Unit = {},
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // §37.12 #330: тап по строке открывает ClipOfflinePlayerScreen.
            .then(if (canPlay) Modifier.clickable(onClick = onPlay) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ─── Thumbnail 64dp (clip — вертикальное 9:16 видео, но в списке показываем квадрат) ───
        val thumbUrl = meta?.thumbUrl
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbUrl != null) {
                coil3.compose.AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (canPlay) Icons.Filled.PlayArrow else Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // §37.12 #330: полупрозрачный PlayArrow overlay поверх thumbnail если canPlay.
            if (canPlay && thumbUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            // §37.12 #330: duration badge в правом нижнем углу thumbnail (как у VK clip preview).
            val durationSec = meta?.duration ?: 0
            if (durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = formatClipDuration(durationSec),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // ─── Колонка: автор + заголовок + дата/размер ───
        Column(modifier = Modifier.weight(1f)) {
            // Первая строка: аватар автора (24dp, опционально) + имя автора bold.
            val authorName = meta?.authorName?.ifBlank { null }
                ?: state.artist.ifBlank { null }
                ?: "Клип ${state.ownerId}"
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatarUrl = meta?.authorAvatar
                if (avatarUrl != null) {
                    coil3.compose.AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = authorName,
                    // §37.12 #330: weight(1f) — ограничивает ширину, иначе длинное имя
                    // автора может вытолкнуть delete-иконку за пределы экрана.
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Заголовок clip'а (или description если title пустой).
            val displayTitle = meta?.title?.ifBlank { null }
                ?: state.title.ifBlank { null }
            if (displayTitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Подзаголовок: дата скачивания • размер.
            val subtitle = buildString {
                meta?.downloadedAt?.takeIf { it > 0 }?.let { ts ->
                    append(formatDownloadedAt(ts))
                }
                if (fileSize > 0) {
                    if (isNotEmpty()) append(" • ")
                    append(formatBytes(fileSize))
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * §37.12 #330: Форматирование длительности clip'а в M:SS (например 1:02 для 62s).
 * Mirror StoryOfflinePlayerScreen.formatMs, но принимает секунды (Int), а не миллисекунды.
 */
private fun formatClipDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}

/**
 * §37.12 #330: human-readable «N д/ч/мин назад» для даты скачивания clip'а.
 * Mirror StoryOfflinePlayerScreen.formatStoryOfflineDate.
 */
private fun formatDownloadedAt(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = (now - timestampMs) / 1000
    return when {
        diff < 60 -> "только что"
        diff < 3600 -> "${diff / 60} мин назад"
        diff < 86400 -> "${diff / 3600} ч назад"
        else -> "${diff / 86400} д назад"
    }
}
