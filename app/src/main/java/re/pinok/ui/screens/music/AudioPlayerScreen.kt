// File: ui/screens/music/AudioPlayerScreen.kt
package re.pinok.ui.screens.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.AudioPlaylist
import re.pinok.data.model.EqualizerPreset
import re.pinok.data.model.PlayerState
import re.pinok.data.model.Track
import re.pinok.media.EqualizerHelper
import re.pinok.media.PlayerConnection
import re.pinok.media.TrackDownloadManager
import re.pinok.util.AppLog
import re.pinok.util.toDurationString
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Fix #62: Полноэкранный аудиоплеер.
 *
 * Моделирован по скриншоту SOVA reference (Screenshot_20260628_185029.png):
 *  — Крупная обложка трека в центре (с gradient-плейсхолдером если нет albumThumb)
 *  — Прогресс-бар с таймингами (текущее / оставшееся)
 *  — Название трека (белым) + артист (синим, кликабельный → поиск артиста)
 *  — Контролы: prev / play-pause / next + download + 3-dots
 *  — Второй ряд: shuffle / queue / repeat
 *
 * Открывается тапом по мини-плееру в MusicScreen.
 * System back → onBack (popBackStack).
 */
@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFullEqualizer: () -> Unit = {},
) {
    BackHandler { onBack() }

    val playerState by PlayerConnection.playerState.collectAsState()
    val downloads by TrackDownloadManager.downloads.collectAsState()

    // Тёмный фон с лёгким gradient от обложки (как в SOVA)
    val vkBlack = Color(0xFF0F0F10)
    val vkCard = Color(0xFF1C1C1E)
    val vkTextPrimary = Color(0xFFFFFFFF)
    val vkTextSecondary = Color(0xFFA8A8AA)
    val vkAccent = Color(0xFF3D8BFF)

    val track = playerState.currentTrack
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    // Fix #249: Activity-context для startActivity(chooser) — иначе
    // Application-context (SovaApp.get()) кидает AndroidRuntimeException
    // «Calling startActivity() from outside of an Activity context
    // requires the FLAG_ACTIVITY_NEW_TASK flag».
    val localContext = LocalContext.current

    // ─── Lyrics state ──────────────────────────────────────────────
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }

    // ─── Equalizer state ──────────────────────────────────────────
    var showEqualizer by remember { mutableStateOf(false) }

    // ─── Fix #258: Add-to-playlist dialog state ───────────────────
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AudioPlaylist>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ─── Fix #258: Create-new-playlist dialog ─────────────────────
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    // Fix #161: убран verticalScroll из главного Column.
    // В скроллящемся Column Modifier.weight() игнорируется (получает 0 высоты),
    // поэтому Spacer(weight(1f)) не работал → контент сжимался вверху, а
    // fillMaxSize() оставлял ~1/4 экрана пустым внизу. Без scroll weight
    // корректно распределяет свободное место: обложка+заголовок вверху,
    // прогресс+контролы внизу.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vkBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ─── Top bar: back + title + more ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = vkTextPrimary,
                )
            }
            Text(
                text = "Сейчас играет",
                color = vkTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            var showTopMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showTopMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Ещё",
                        tint = vkTextPrimary,
                    )
                }
                DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Текст песни", color = vkTextPrimary) },
                        onClick = {
                            showTopMenu = false
                            showLyrics = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист", color = vkTextPrimary) },
                        onClick = {
                            showTopMenu = false
                            // Fix #258: открываем диалог выбора плейлиста.
                            showPlaylistDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("В закладки", color = vkTextPrimary) },
                        onClick = {
                            showTopMenu = false
                            val t = track
                            if (t == null) return@DropdownMenuItem
                            // #FAVE-AUDIO (2026-08-03): fave.add(type="audio").
                            scope.launch {
                                var ok = false
                                try {
                                    ok = app.apiClient.faveAdd("audio", t.ownerId, t.id)
                                } catch (e: Exception) {
                                    AppLog.e("AudioPlayerScreen", "faveAdd audio error", e)
                                }
                                snackbarHostState.showSnackbar(
                                    if (ok) "Добавлено в закладки" else "Не удалось добавить в закладки"
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться", color = vkTextPrimary) },
                        onClick = {
                            showTopMenu = false
                            val t = track
                            if (t == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Нет трека для передачи")
                                }
                                return@DropdownMenuItem
                            }
                            // Fix #258: defensively share — показываем snackbar
                            // при ошибке вместо тихого логирования (прежний код
                            // мог крашить на некоторых устройствах).
                            scope.launch {
                                try {
                                    val shareText = "${t.title} — ${t.artist}\nhttps://vk.com/audio${t.ownerId}_${t.id}"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    }
                                    val chooser = android.content.Intent.createChooser(intent, "Поделиться").apply {
                                        // FLAG_ACTIVITY_NEW_TASK нужен только для non-Activity context.
                                        if (localContext !is android.app.Activity) {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    }
                                    localContext.startActivity(chooser)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    AppLog.w("AudioPlayerScreen", "share: no app to handle intent", e)
                                    snackbarHostState.showSnackbar("Нет приложений для передачи")
                                } catch (e: Exception) {
                                    AppLog.e("AudioPlayerScreen", "share failed", e)
                                    // Fallback на Application context.
                                    try {
                                        val shareText = "${t.title} — ${t.artist}\nhttps://vk.com/audio${t.ownerId}_${t.id}"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        app.startActivity(android.content.Intent.createChooser(intent, "Поделиться"))
                                    } catch (e2: Exception) {
                                        AppLog.e("AudioPlayerScreen", "share fallback failed", e2)
                                        snackbarHostState.showSnackbar("Не удалось поделиться: ${e2.message}")
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        if (track == null) {
            // Нет трека — показываем заглушку
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = vkTextSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ничего не играет", color = vkTextSecondary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Выберите трек в разделе «Музыка»", color = vkTextSecondary, fontSize = 13.sp)
                }
            }
            return@Column
        }

        // ─── Крупная обложка ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.hsl((abs(track.artist.hashCode()) % 360).toFloat(), 0.6f, 0.4f),
                            Color.hsl(((abs(track.artist.hashCode() * 31)) % 360).toFloat(), 0.5f, 0.25f),
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
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        // ─── Название + артист ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = vkTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = vkAccent,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Иконка текста песни — только если трек имеет lyrics
            if (track.hasLyrics) {
                IconButton(onClick = {
                    lyricsText = null
                    showLyrics = true
                }) {
                    Text("ТТ", color = vkTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ─── Прогресс-бар с таймингами ─────────────────────────────────
        val effectiveDuration = if (playerState.durationMs > 0) playerState.durationMs else track.duration * 1000L
        var sliderDragging by remember { mutableStateOf(false) }
        var sliderPos by remember { mutableStateOf(playerState.positionMs.toFloat()) }

        LaunchedEffect(playerState.positionMs) {
            if (!sliderDragging) sliderPos = playerState.positionMs.toFloat()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Slider(
                value = if (sliderDragging) sliderPos else playerState.positionMs.toFloat(),
                onValueChange = {
                    sliderDragging = true
                    sliderPos = it
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    PlayerConnection.seekTo(sliderPos.toLong())
                },
                valueRange = 0f..effectiveDuration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    activeTrackColor = vkAccent,
                    inactiveTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                    thumbColor = vkAccent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = playerState.positionMs.toDurationString(),
                    color = vkTextSecondary,
                    fontSize = 11.sp,
                )
                // Оставшееся время (с минусом, как в SOVA)
                val remaining = (effectiveDuration - playerState.positionMs).coerceAtLeast(0L)
                Text(
                    text = "-" + remaining.toDurationString(),
                    color = vkTextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Основные контролы: prev / play-pause / next ──────────────
        // Fix #163: Play должен быть строго по центру экрана.
        // Раньше в ряду было 4 кнопки (Download/Prev/Play/Next) при
        // SpaceEvenly → Play оказывался на 60% ширины, а не 50%.
        // Download перенесён во второй ряд. Теперь 3 кнопки при SpaceEvenly:
        // позиции 25% / 50% / 75% → Play ровно по центру.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Prev
            IconButton(onClick = { PlayerConnection.prev() }) {
                Icon(
                    Icons.Filled.SkipPrevious, "Предыдущий",
                    tint = vkTextPrimary, modifier = Modifier.size(40.dp),
                )
            }
            // Play/Pause (центральная, крупная)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(vkAccent)
                    .clickable { PlayerConnection.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Пауза" else "Играть",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            // Next
            IconButton(onClick = { PlayerConnection.next() }) {
                Icon(
                    Icons.Filled.SkipNext, "Следующий",
                    tint = vkTextPrimary, modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ─── Второй ряд: download / shuffle / speed / queue / equalizer / repeat ──
        // Fix #163: Download перенесён сюда из главного ряда (чтобы Play был
        // по центру). 6 кнопок при SpaceEvenly, padding уменьшен до 12dp.
        val dl = downloads[track.id]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Download (перенесён из главного ряда)
            IconButton(onClick = {
                if (dl != null && (dl.isCompleted || dl.isInProgress)) {
                    TrackDownloadManager.removeDownload(track.id)
                    return@IconButton
                }
                if (track.url != null) {
                    TrackDownloadManager.enqueueDownload(track)
                    return@IconButton
                }
                scope.launch {
                    AppLog.i("AudioPlayer", "download: track.url is null, trying audioGetById for ${track.id}")
                    val resolved = app.apiClient.audioGetById(track)
                    if (resolved != null && resolved.url != null) {
                        TrackDownloadManager.enqueueDownload(resolved)
                    } else {
                        AppLog.w("AudioPlayer", "download: audioGetById returned no URL for ${track.id}")
                    }
                }
            }) {
                when {
                    dl == null ->
                        Icon(Icons.Filled.Download, "Скачать", tint = vkTextSecondary, modifier = Modifier.size(24.dp))
                    // #OFFLINE-STATUS-1: дохлый трек (DEAD_URL) — MusicOff, красный.
                    dl.isDead ->
                        Icon(Icons.Filled.MusicOff, "Недоступен (URL истёк)", tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    dl.status == re.pinok.data.model.DownloadStatus.FAILED ->
                        Icon(Icons.Filled.Download, "Повторить", tint = vkTextSecondary, modifier = Modifier.size(24.dp))
                    dl.isInProgress -> CircularProgressIndicator(
                        progress = { if (dl.progress >= 0) dl.progress / 100f else 0f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = vkAccent,
                        trackColor = Color.Transparent,
                    )
                    // #OFFLINE-STATUS-1: siren-кэш — DownloadDone + wifi-бейдж.
                    dl.isSirenCache -> Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DownloadDone, "Скачано (онлайн-кеш, siren)", tint = vkAccent, modifier = Modifier.size(24.dp))
                        Icon(Icons.Filled.Wifi, null, tint = Color(0xFF22C55E), modifier = Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp).size(11.dp))
                    }
                    dl.isCompleted -> Icon(Icons.Filled.DownloadDone, "Скачано", tint = vkAccent, modifier = Modifier.size(24.dp))
                    else -> Icon(Icons.Filled.Download, "Скачивание", tint = vkTextSecondary, modifier = Modifier.size(24.dp))
                }
            }
            // Shuffle
            IconButton(onClick = {
                PlayerConnection.setShuffleModeEnabled(!playerState.shuffleModeEnabled)
            }) {
                Icon(
                    Icons.Filled.Shuffle, "Перемешать",
                    tint = if (playerState.shuffleModeEnabled) vkAccent else vkTextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Speed
            var showSpeedMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showSpeedMenu = true }) {
                    Text(
                        text = "${"%.2f".format(playerState.speed)}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (playerState.speed != 1.0f) vkAccent else vkTextSecondary,
                    )
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { spd ->
                        DropdownMenuItem(
                            text = { Text("${"%.2f".format(spd)}x") },
                            onClick = {
                                PlayerConnection.setPlaybackSpeed(spd)
                                showSpeedMenu = false
                            },
                        )
                    }
                }
            }
            // Queue
            IconButton(onClick = onOpenQueue) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic, "Очередь",
                    tint = vkTextSecondary, modifier = Modifier.size(24.dp),
                )
            }
            // Equalizer
            IconButton(onClick = { showEqualizer = true }) {
                Icon(
                    Icons.Filled.Equalizer, "Эквалайзер",
                    tint = vkTextSecondary, modifier = Modifier.size(24.dp),
                )
            }
            // Repeat (4 состояния: OFF / ALL / ONE / TWO)
            IconButton(onClick = { PlayerConnection.cycleRepeatMode() }) {
                val repeatIcon = when (playerState.repeatMode) {
                    PlayerState.REPEAT_MODE_ONE, PlayerState.REPEAT_MODE_TWO -> Icons.Filled.RepeatOne
                    else -> Icons.Filled.Repeat
                }
                val repeatTint = if (playerState.repeatMode != PlayerState.REPEAT_MODE_OFF) vkAccent else vkTextSecondary
                Box(contentAlignment = Alignment.Center) {
                    Icon(repeatIcon, "Повтор", tint = repeatTint, modifier = Modifier.size(24.dp))
                    // Бейдж "2" для REPEAT_MODE_TWO поверх иконки RepeatOne.
                    if (playerState.repeatMode == PlayerState.REPEAT_MODE_TWO) {
                        Text(
                            text = "2",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(vkAccent, shape = RoundedCornerShape(50))
                                .padding(horizontal = 3.dp)
                                .offset(x = 6.dp, y = (-4).dp),
                        )
                    }
                }
            }
        }

        // Fix #161: небольшой нижний отступ для визуального баланса.
        // navigationBarsPadding на Column уже учитывает gesture/nav bar.
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ─── Lyrics Bottom Sheet ────────────────────────────────────────
    if (showLyrics) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = sheetState,
            containerColor = vkCard,
            dragHandle = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Drag handle pill
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(vkTextSecondary.copy(alpha = 0.4f)),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Title row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Текст песни",
                            color = vkTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showLyrics = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = vkTextSecondary,
                            )
                        }
                    }
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    lyricsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = vkAccent)
                        }
                    }
                    lyricsText == null -> {
                        Text(
                            text = "Текст песни недоступен",
                            color = vkTextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                    }
                    else -> {
                        val lt = lyricsText
                        if (lt != null) {
                        Text(
                            text = lt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = vkTextPrimary,
                            lineHeight = 28.sp,
                        )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Fetch lyrics when sheet opens
        LaunchedEffect(showLyrics) {
            val currentTrack = track
            if (showLyrics && currentTrack != null && currentTrack.lyricsId != null) {
                lyricsLoading = true
                lyricsText = app.apiClient.audioGetLyrics(currentTrack.lyricsId)
                lyricsLoading = false
            }
        }
    }

    // ─── Equalizer Bottom Sheet (упрощённый) ──────────────────────
    // Этап 2 (#Equalizer): здесь оставляем компактную панель — пресеты +
    // master switch + quick bass/virt. Полная настройка всех 6 эффектов
    // (9 полос, reverb, loudness) — в полноэкранном EqualizerScreen,
    // кнопка «Открыть полный эквалайзер» внизу панели.
    if (showEqualizer) {
        val eqSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var eqEnabled by remember { mutableStateOf(false) }
        var eqPresetName by remember { mutableStateOf<String?>(null) }

        // Quick-эффекты (только если включены в feature-флагах)
        val featureFlags = remember { re.pinok.media.EqualizerFeatureFlags.snapshot() }
        var bassOn by remember { mutableStateOf(false) }
        var bassStr by remember { mutableStateOf(0) }
        var virtOn by remember { mutableStateOf(false) }
        var virtStr by remember { mutableStateOf(0) }

        LaunchedEffect(showEqualizer) {
            if (showEqualizer) {
                eqEnabled = EqualizerHelper.isEnabled() || EqualizerHelper.isSavedEnabled()
                eqPresetName = EqualizerHelper.currentPresetName ?: EqualizerHelper.getSavedPresetName()
                val engine = EqualizerHelper.engine()
                if (engine != null) {
                    bassOn = engine.isBassBoostEnabled() || engine.isBassBoostSavedEnabled()
                    bassStr = engine.getBassBoostStrength()
                    virtOn = engine.isVirtualizerEnabled() || engine.isVirtualizerSavedEnabled()
                    virtStr = engine.getVirtualizerStrength()
                }
            }
        }

        val displayPresetName = eqPresetName
            ?: "Пользовательский"

        ModalBottomSheet(
            onDismissRequest = { showEqualizer = false },
            sheetState = eqSheetState,
            containerColor = vkCard,
            dragHandle = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(vkTextSecondary.copy(alpha = 0.4f)),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Эквалайзер",
                            color = vkTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = displayPresetName,
                            color = vkAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        IconButton(onClick = { showEqualizer = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = vkTextSecondary,
                            )
                        }
                    }
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                // ─── Вкл / Выкл ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Вкл / Выкл",
                        color = vkTextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { enabled ->
                            eqEnabled = enabled
                            PlayerConnection.setEqualizerEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = vkAccent,
                            checkedTrackColor = vkAccent,
                            uncheckedThumbColor = vkTextSecondary,
                            uncheckedTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ─── Пресеты ───────────────────────────────────────
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqualizerPreset.ALL.forEach { preset ->
                        val isActive = eqPresetName == preset.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isActive) vkAccent
                                    else vkTextSecondary.copy(alpha = 0.15f)
                                )
                                .clickable {
                                    PlayerConnection.setEqualizerPreset(preset)
                                    eqPresetName = preset.name
                                },
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isActive) Color.White else vkTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Quick: BassBoost (если включён в feature-флагах) ────
                if (featureFlags.bassEnabled) {
                    val engine = EqualizerHelper.engine()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Басы",
                            color = vkTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = bassOn,
                            onCheckedChange = { on ->
                                bassOn = on
                                engine?.setBassBoostEnabled(on)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = vkAccent,
                                checkedTrackColor = vkAccent,
                                uncheckedThumbColor = vkTextSecondary,
                                uncheckedTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                            ),
                        )
                    }
                    Slider(
                        value = bassStr.toFloat(),
                        onValueChange = { v ->
                            bassStr = v.toInt()
                            engine?.setBassBoostStrength(v.toInt())
                        },
                        enabled = bassOn,
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = vkAccent,
                            activeTrackColor = vkAccent,
                            inactiveTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ─── Quick: Virtualizer (если включён в feature-флагах) ───
                if (featureFlags.virtualizerEnabled) {
                    val engine = EqualizerHelper.engine()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Объём",
                            color = vkTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = virtOn,
                            onCheckedChange = { on ->
                                virtOn = on
                                engine?.setVirtualizerEnabled(on)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = vkAccent,
                                checkedTrackColor = vkAccent,
                                uncheckedThumbColor = vkTextSecondary,
                                uncheckedTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                            ),
                        )
                    }
                    Slider(
                        value = virtStr.toFloat(),
                        onValueChange = { v ->
                            virtStr = v.toInt()
                            engine?.setVirtualizerStrength(v.toInt())
                        },
                        enabled = virtOn,
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = vkAccent,
                            activeTrackColor = vkAccent,
                            inactiveTrackColor = vkTextSecondary.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ─── Кнопка «Открыть полный эквалайзер» ──────────────
                // Ведёт на полноэкранный EqualizerScreen (9 полос, reverb,
                // loudness, тонкая настройка).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(vkAccent.copy(alpha = 0.15f))
                        .clickable {
                            showEqualizer = false
                            onOpenFullEqualizer()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = vkAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Открыть полный эквалайзер",
                        color = vkAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("→", color = vkAccent, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ─── Fix #258: SnackbarHost для уведомлений ─────────────────────
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    )

    // ─── Fix #258: Диалог «Добавить в плейлист» ─────────────────────
    if (showPlaylistDialog) {
        val myId = app.exchangeAuthRepository.userId()
        // Загружаем плейлисты при открытии диалога.
        LaunchedEffect(showPlaylistDialog) {
            playlistsLoading = true
            try {
                val (_, result) = app.apiClient.audioGetPlaylists(count = 50)
                playlists = result
            } catch (e: Exception) {
                AppLog.e("AudioPlayerScreen", "load playlists failed", e)
                playlists = emptyList()
            } finally {
                playlistsLoading = false
            }
        }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            containerColor = vkCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Добавить в плейлист",
                        color = vkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        showPlaylistDialog = false
                        newPlaylistTitle = ""
                        showCreatePlaylistDialog = true
                    }) {
                        Text("Новый", color = vkAccent, fontSize = 14.sp)
                    }
                }
            },
            text = {
                val t = track
                if (t == null) {
                    Text("Нет трека", color = vkTextSecondary, fontSize = 14.sp)
                } else if (playlistsLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = vkAccent)
                    }
                } else if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Нет плейлистов. Создайте новый.",
                            color = vkTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (addingToPlaylist) return@clickable
                                        addingToPlaylist = true
                                        scope.launch {
                                            try {
                                                val audioIds = listOf("${t.ownerId}_${t.id}")
                                                val result = app.apiClient.audioAddToPlaylist(
                                                    ownerId = myId,
                                                    playlistId = playlist.id,
                                                    audioIds = audioIds,
                                                )
                                                if (result.isNotEmpty()) {
                                                    snackbarHostState.showSnackbar(
                                                        "Добавлено в «${playlist.title}»",
                                                    )
                                                } else {
                                                    val err = app.apiClient.lastApiError
                                                    snackbarHostState.showSnackbar(
                                                        "Не удалось: ${err ?: "трек уже в плейлисте?"}",
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("AudioPlayerScreen", "addToPlaylist failed", e)
                                                snackbarHostState.showSnackbar("Ошибка: ${e.message}")
                                            } finally {
                                                addingToPlaylist = false
                                                showPlaylistDialog = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Обложка плейлиста
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(vkAccent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val coverUrl = playlist.coverUrl
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
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        color = vkTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (playlist.count > 0) {
                                        Text(
                                            text = "${playlist.count} треков",
                                            color = vkTextSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                if (addingToPlaylist) {
                                    CircularProgressIndicator(
                                        color = vkAccent,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Закрыть", color = vkAccent)
                }
            },
        )
    }

    // ─── Fix #258: Диалог «Создать плейлист» ────────────────────────
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistTitle = ""
            },
            containerColor = vkCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Новый плейлист",
                    color = vkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    placeholder = { Text("Название", color = vkTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = vkTextPrimary,
                        fontSize = 15.sp,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = newPlaylistTitle.trim()
                        if (title.isEmpty()) return@TextButton
                        val t = track
                        scope.launch {
                            try {
                                val myId = app.exchangeAuthRepository.userId()
                                val newId = app.apiClient.audioCreatePlaylist(
                                    ownerId = myId,
                                    title = title,
                                    description = "",
                                )
                                if (newId > 0 && t != null) {
                                    val audioIds = listOf("${t.ownerId}_${t.id}")
                                    app.apiClient.audioAddToPlaylist(
                                        ownerId = myId,
                                        playlistId = newId,
                                        audioIds = audioIds,
                                    )
                                    snackbarHostState.showSnackbar("Создан «$title», трек добавлен")
                                } else {
                                    val err = app.apiClient.lastApiError
                                    snackbarHostState.showSnackbar("Не удалось создать: ${err ?: "ошибка"}")
                                }
                            } catch (e: Exception) {
                                AppLog.e("AudioPlayerScreen", "createPlaylist failed", e)
                                snackbarHostState.showSnackbar("Ошибка: ${e.message}")
                            } finally {
                                showCreatePlaylistDialog = false
                                newPlaylistTitle = ""
                            }
                        }
                    },
                ) {
                    Text("Создать", color = vkAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    newPlaylistTitle = ""
                }) {
                    Text("Отмена", color = vkTextSecondary)
                }
            },
        )
    }
}

