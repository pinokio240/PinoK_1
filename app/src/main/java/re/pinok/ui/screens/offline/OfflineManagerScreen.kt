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
import androidx.compose.runtime.LaunchedEffect
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

    // #ANR-MAIN-IO (2026-09-04): все файловые операции экрана — getLocalFile
    // (stat + magic-byte валидация с ОТКРЫТИЕМ файла), lastModified/length,
    // чтение .meta sidecar — выполняются на Dispatchers.IO, а не в композиции.
    // Раньше: getTotalDownloadedBytes() вызывался без remember (на каждой
    // рекомпозиции!), + remember-блоки байтов и allItems-сканы вкладок — суммарно
    // ~2500+ файловых операций на main при открытии экрана с полной библиотекой
    // → Davey 10349ms + ANR-tombstone (лог 2026-09-04 20:56:19–29).
    //
    // Состояние сканов: null = сканирование идёт (вкладка показывает спиннер,
    // футер показывает «…»); List = готово. При пересканировании старый список
    // остаётся видимым до атомарной замены — мигания списков нет.
    var audioItems by remember { mutableStateOf<List<AudioOfflineItem>?>(null) }
    var videoItems by remember { mutableStateOf<List<VideoOfflineItem>?>(null) }
    var storyItems by remember { mutableStateOf<List<StoryOfflineItem>?>(null) }
    var clipItems by remember { mutableStateOf<List<ClipOfflineItem>?>(null) }

    // Ключи пересканирования: FNV-1a по набору загрузок (см. audioScanKeyOf/
    // mediaScanKeyOf ниже). Прогресс-тики активных скачиваний НЕ меняют набор
    // COMPLETED → ключ стабилен → пересканирования нет; завершение скачивания /
    // удаление меняют набор → ключ меняется → пересканирование.
    val audioScanKey = remember(completedAudio) { audioScanKeyOf(completedAudio) }
    val videoScanKey = remember(completedVideo) { mediaScanKeyOf(completedVideo) }
    val storyScanKey = remember(completedStories) { mediaScanKeyOf(completedStories) }
    val clipScanKey = remember(completedClips) { mediaScanKeyOf(completedClips) }

    LaunchedEffect(audioScanKey) {
        val snapshot = completedAudio
        audioItems = withContext(Dispatchers.IO) {
            snapshot.map { ds ->
                val f = TrackDownloadManager.getLocalFile(ds.trackId)
                var lm = 0L
                var sz = 0L
                if (f != null) {
                    lm = f.lastModified()
                    sz = f.length()
                }
                AudioOfflineItem(state = ds, file = f, lastModified = lm, sizeBytes = sz)
            }
        }
    }
    LaunchedEffect(videoScanKey) {
        val snapshot = completedVideo
        videoItems = withContext(Dispatchers.IO) {
            snapshot.map { ds ->
                val f = VideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId)
                var lm = 0L
                var sz = 0L
                if (f != null) {
                    lm = f.lastModified()
                    sz = f.length()
                }
                VideoOfflineItem(state = ds, file = f, lastModified = lm, sizeBytes = sz)
            }
        }
    }
    LaunchedEffect(storyScanKey) {
        val snapshot = completedStories
        storyItems = withContext(Dispatchers.IO) {
            snapshot.map { ds ->
                val key = StoryVideoDownloadManager.storyKey(ds.ownerId, ds.trackId.toInt())
                val meta = StoryVideoDownloadManager.getStoryMeta(key)
                val f = StoryVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId.toInt())
                var lm = 0L
                var sz = 0L
                if (f != null) {
                    lm = f.lastModified()
                    sz = f.length()
                }
                var dateKey = lm
                if (meta != null && meta.downloadedAt > 0L) {
                    dateKey = meta.downloadedAt
                }
                StoryOfflineItem(
                    state = ds, meta = meta, file = f,
                    lastModified = lm, sizeBytes = sz, dateKey = dateKey,
                )
            }
        }
    }
    LaunchedEffect(clipScanKey) {
        val snapshot = completedClips
        clipItems = withContext(Dispatchers.IO) {
            snapshot.map { ds ->
                val meta = ClipVideoDownloadManager.getClipMeta(ds.ownerId, ds.trackId)
                val f = ClipVideoDownloadManager.getLocalFile(ds.ownerId, ds.trackId)
                var lm = 0L
                var sz = 0L
                if (f != null) {
                    lm = f.lastModified()
                    sz = f.length()
                }
                var dateKey = lm
                if (meta != null && meta.downloadedAt > 0L) {
                    dateKey = meta.downloadedAt
                }
                ClipOfflineItem(
                    state = ds, meta = meta, file = f,
                    lastModified = lm, sizeBytes = sz, dateKey = dateKey,
                )
            }
        }
    }

    // Футер: байты считаются из уже просканированных списков (чисто in-memory).
    // #NULL-EXPLICIT: захваты nullable-делегатов в локальные val.
    val loadedAudio = audioItems
    val loadedVideo = videoItems
    val loadedStory = storyItems
    val loadedClip = clipItems
    val audioBytes = if (loadedAudio != null) loadedAudio.sumOf { it.sizeBytes } else 0L
    val videoBytes = if (loadedVideo != null) loadedVideo.sumOf { it.sizeBytes } else 0L
    val storyBytes = if (loadedStory != null) loadedStory.sumOf { it.sizeBytes } else 0L
    val clipBytes = if (loadedClip != null) loadedClip.sumOf { it.sizeBytes } else 0L
    val totalBytes = audioBytes + videoBytes + storyBytes + clipBytes
    val bytesScanPending = loadedAudio == null || loadedVideo == null ||
        loadedStory == null || loadedClip == null

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
            val savedPath = snap.musicDownloadPath
            if (savedPath.isNotBlank()) {
                // #ANR-MAIN-IO: checkPathMismatch делает дисковый I/O
                // (exists/canWrite/probeWritable — запись probe-файла) — не на main.
                withContext(Dispatchers.IO) {
                    TrackDownloadManager.checkPathMismatch(savedPath)
                }
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
        // #NULL-EXPLICIT: захват var-делегата lightScanResult в локальный val —
        // smart-cast делегированного свойства невозможен; проверка и
        // использование — одна и та же val, поведение прежнее.
        val lightScanSnapshot = lightScanResult
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
        } else if (lightScanSnapshot != null) {
            val result = lightScanSnapshot
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
                    items = audioItems,
                    query = audioQuery,
                    onQueryChange = { audioQuery = it },
                    sort = audioSort,
                    onSortChange = { audioSort = it },
                    onOpenPlayer = onOpenPlayer,
                )
                1 -> VideoOfflineTab(
                    items = videoItems,
                    query = videoQuery,
                    onQueryChange = { videoQuery = it },
                    sort = videoSort,
                    onSortChange = { videoSort = it },
                    onPlayVideo = onPlayVideo,
                )
                // Fix #100: таб «Истории» — story video кэш.
                2 -> StoryOfflineTab(
                    items = storyItems,
                    query = storyQuery,
                    onQueryChange = { storyQuery = it },
                    sort = storySort,
                    onSortChange = { storySort = it },
                    onPlayStory = onPlayStory,
                )
                // §37.12 #330: таб «Клипы» — clip video кэш (ClipVideoDownloadManager).
                3 -> ClipOfflineTab(
                    items = clipItems,
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
                    text = if (bytesScanPending) "…" else formatBytes(totalBytes),
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

/**
 * Элемент офлайн-аудио с файлом для сортировки по date/size.
 * #ANR-MAIN-IO: lastModified/sizeBytes предвычислены при сканировании на
 * Dispatchers.IO (см. OfflineManagerScreen) — сортировка и отрисовка НЕ делают
 * файловых операций (раньше sortedByDescending { file.lastModified() } давал
 * O(n·log n) stat-системных вызовов на main).
 */
private data class AudioOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val file: File?,
    val lastModified: Long,
    val sizeBytes: Long,
)

/**
 * Элемент офлайн-видео с файлом для сортировки по date/size.
 * #ANR-MAIN-IO: lastModified/sizeBytes предвычислены при сканировании (см. AudioOfflineItem).
 */
private data class VideoOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val file: File?,
    val lastModified: Long,
    val sizeBytes: Long,
)

/** #ANR-MAIN-IO: ключ пересканирования аудио — FNV-1a по trackId набора.
 *  Стабилен для неизменного набора COMPLETED (прогресс-тики не пересканируют),
 *  меняется при завершении скачивания / удалении загрузки. */
private fun audioScanKeyOf(states: List<re.pinok.data.model.DownloadState>): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    for (s in states) {
        h = (h xor s.trackId) * 1099511628211L // FNV prime
    }
    return h
}

/** #ANR-MAIN-IO: ключ пересканирования видео/историй/клипов — FNV-1a по (ownerId, trackId). */
private fun mediaScanKeyOf(states: List<re.pinok.data.model.DownloadState>): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    for (s in states) {
        h = (h xor s.ownerId) * 1099511628211L
        h = (h xor s.trackId) * 1099511628211L
    }
    return h
}

/** #ANR-MAIN-IO: общая заглушка «Сканирование кэша…» для вкладок офлайн-менеджера. */
@Composable
private fun OfflineScanInProgress(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Сканирование кэша…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioOfflineTab(
    /** #ANR-MAIN-IO: просканированные элементы (null = сканирование кэша идёт). */
    items: List<AudioOfflineItem>?,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    /** Fix #50: открывает [OfflineAudioPlayerScreen] при тапе. */
    onOpenPlayer: () -> Unit = {},
) {
    // #NULL-EXPLICIT: захват nullable-параметра в локальный val для смарт-каста.
    val loaded = items
    val allItems = if (loaded != null) loaded else emptyList()

    // #39 C5: filter + sort — ЧИСТО in-memory: lastModified/sizeBytes
    // предвычислены при сканировании на Dispatchers.IO (#ANR-MAIN-IO),
    // файловых операций на каждое нажатие клавиши больше нет.
    val visibleItems = remember(loaded, query, sort) {
        if (loaded == null) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) loaded else {
                loaded.filter { item ->
                    item.state.title.lowercase().contains(q) ||
                        item.state.artist.lowercase().contains(q) ||
                        item.state.displayText.lowercase().contains(q)
                }
            }
            when (sort) {
                OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.lastModified }
                OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.sizeBytes }
                OfflineSortOption.TITLE_AZ -> filtered.sortedBy { it.state.title.lowercase() }
                OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { it.state.artist.lowercase() }
            }
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

        if (loaded == null) {
            // #ANR-MAIN-IO: сканирование кэша идёт на Dispatchers.IO — честный loading.
            OfflineScanInProgress(modifier = Modifier.weight(1f))
        } else if (visibleItems.isEmpty()) {
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
                        // #ANR-MAIN-IO: размер из скана — без file.length() на recompose.
                        fileSize = item.sizeBytes,
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
    /** #ANR-MAIN-IO: просканированные элементы (null = сканирование кэша идёт). */
    items: List<VideoOfflineItem>?,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: OfflineSortOption,
    onSortChange: (OfflineSortOption) -> Unit,
    onPlayVideo: ((ownerId: Long, videoId: Long, title: String) -> Unit)?,
) {
    // #NULL-EXPLICIT: захват nullable-параметра в локальный val для смарт-каста.
    val loaded = items
    val allItems = if (loaded != null) loaded else emptyList()

    // #39 C5: filter + sort (видео — без ARTIST_AZ) — чисто in-memory (#ANR-MAIN-IO).
    val visibleItems = remember(loaded, query, sort) {
        if (loaded == null) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) loaded else {
                loaded.filter { it.state.title.lowercase().contains(q) }
            }
            when (sort) {
                OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.lastModified }
                OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.sizeBytes }
                OfflineSortOption.TITLE_AZ -> filtered.sortedBy { it.state.title.lowercase() }
                // Для видео ARTIST_AZ не имеет смысла — fallback на TITLE_AZ.
                OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { it.state.title.lowercase() }
            }
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

        if (loaded == null) {
            // #ANR-MAIN-IO: сканирование кэша идёт на Dispatchers.IO — честный loading.
            OfflineScanInProgress(modifier = Modifier.weight(1f))
        } else if (visibleItems.isEmpty()) {
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
                        // #ANR-MAIN-IO: размер из скана — без file.length() на recompose.
                        fileSize = item.sizeBytes,
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
    /** #ANR-MAIN-IO: просканированные элементы с .meta sidecar (null = сканирование идёт). */
    items: List<StoryOfflineItem>?,
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
    // #NULL-EXPLICIT: захват nullable-параметра в локальный val для смарт-каста.
    val loaded = items
    val allItems = if (loaded != null) loaded else emptyList()

    // #ANR-MAIN-IO: сортировка по предвычисленным dateKey/sizeBytes (чисто in-memory);
    // файловые операции (lastModified/length) и чтение .meta — только при скане на IO.
    val visibleItems = remember(loaded, query, sort) {
        if (loaded == null) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) loaded else {
                loaded.filter { (it.meta?.ownerName ?: it.state.title).lowercase().contains(q) }
            }
            when (sort) {
                OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.dateKey }
                OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.sizeBytes }
                OfflineSortOption.TITLE_AZ -> filtered.sortedBy { (it.meta?.ownerName ?: it.state.title).lowercase() }
                OfflineSortOption.ARTIST_AZ -> filtered.sortedBy { (it.meta?.ownerName ?: it.state.title).lowercase() }
            }
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

        if (loaded == null) {
            // #ANR-MAIN-IO: сканирование кэша идёт на Dispatchers.IO — честный loading.
            OfflineScanInProgress(modifier = Modifier.weight(1f))
        } else if (visibleItems.isEmpty()) {
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
                    // #NULL-EXPLICIT: захват для смарт-каста + отсутствие live-exists() на recompose.
                    val rowMeta = item.meta
                    val rowFile = item.file
                    StoryOfflineRow(
                        state = item.state,
                        meta = rowMeta,
                        // #ANR-MAIN-IO: размер из скана — без file.length() на recompose.
                        fileSize = item.sizeBytes,
                        // Fix #111: canPlay — файл, подтверждённый сканом (getLocalFile
                        // возвращает файл только если он существует и валиден).
                        // Живая exists()-проверка на каждый recompose убрана (#ANR-MAIN-IO).
                        canPlay = rowFile != null && onPlayStory != null,
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

/**
 * Fix #100: элемент офлайн-истории — DownloadState + .meta sidecar + файл.
 * #ANR-MAIN-IO: lastModified/sizeBytes/dateKey предвычислены при скане на IO;
 * dateKey = meta.downloadedAt (если >0), иначе file.lastModified — сортировка
 * DATE_NEW без файловых операций.
 */
private data class StoryOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val meta: StoryVideoDownloadManager.StoryVideoMeta?,
    val file: java.io.File?,
    val lastModified: Long,
    val sizeBytes: Long,
    val dateKey: Long,
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
 *
 * #ANR-MAIN-IO: lastModified/sizeBytes/dateKey предвычислены при скане на IO;
 * dateKey = meta.downloadedAt (если >0), иначе file.lastModified.
 */
private data class ClipOfflineItem(
    val state: re.pinok.data.model.DownloadState,
    val meta: ClipVideoDownloadManager.ClipVideoMeta?,
    val file: java.io.File?,
    val lastModified: Long,
    val sizeBytes: Long,
    val dateKey: Long,
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
    items: List<ClipOfflineItem>?,
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
    // #NULL-EXPLICIT: захват nullable-параметра в локальный val для смарт-каста.
    // #ANR-MAIN-IO: .meta sidecar + файл сканируются на Dispatchers.IO
    // (см. OfflineManagerScreen), вкладка получает готовые элементы.
    val loaded = items
    val allItems = if (loaded != null) loaded else emptyList()

    // #39 C5: filter + sort (clips — без ARTIST_AZ, эта опция скрыта — mirror video).
    // #ANR-MAIN-IO: сортировка по предвычисленным dateKey/sizeBytes (in-memory).
    val visibleItems = remember(loaded, query, sort) {
        if (loaded == null) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) loaded else {
                loaded.filter { item ->
                    val title = item.meta?.title?.lowercase().orEmpty()
                    val author = item.meta?.authorName?.lowercase().orEmpty()
                    val desc = item.meta?.description?.lowercase().orEmpty()
                    title.contains(q) || author.contains(q) || desc.contains(q) ||
                        item.state.title.lowercase().contains(q)
                }
            }
            when (sort) {
                OfflineSortOption.DATE_NEW -> filtered.sortedByDescending { it.dateKey }
                OfflineSortOption.SIZE_BIG -> filtered.sortedByDescending { it.sizeBytes }
                OfflineSortOption.TITLE_AZ -> filtered.sortedBy {
                    (it.meta?.title?.ifBlank { null } ?: it.state.title).lowercase()
                }
                // Для clips ARTIST_AZ не имеет смысла — fallback на TITLE_AZ (mirror video).
                OfflineSortOption.ARTIST_AZ -> filtered.sortedBy {
                    (it.meta?.title?.ifBlank { null } ?: it.state.title).lowercase()
                }
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

        if (loaded == null) {
            // #ANR-MAIN-IO: сканирование кэша идёт на Dispatchers.IO — честный loading.
            OfflineScanInProgress(modifier = Modifier.weight(1f))
        } else if (visibleItems.isEmpty()) {
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
                    // #NULL-EXPLICIT: захват для смарт-каста + отсутствие live-exists() на recompose.
                    val rowFile = item.file
                    ClipOfflineRow(
                        state = item.state,
                        meta = item.meta,
                        // #ANR-MAIN-IO: размер из скана — без file.length() на recompose.
                        fileSize = item.sizeBytes,
                        // §37.12 #330: canPlay — файл, подтверждённый сканом (getLocalFile
                        // возвращает файл только если он существует и валиден).
                        canPlay = rowFile != null && onPlayClip != null,
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
